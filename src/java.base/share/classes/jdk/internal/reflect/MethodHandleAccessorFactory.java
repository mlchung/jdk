/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jdk.internal.reflect;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import jdk.internal.access.JavaLangInvokeAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.loader.ClassLoaders;
import jdk.internal.misc.Unsafe;
import jdk.internal.misc.VM;

import static java.lang.invoke.MethodType.genericMethodType;
import static java.lang.invoke.MethodType.methodType;
import static jdk.internal.reflect.MethodHandleAccessorFactory.LazyStaticHolder.*;

final class MethodHandleAccessorFactory {
    /**
     * Creates a MethodAccessor for the given reflected method.
     *
     * If the given method is called before the java.lang.invoke initialization
     * or the given method is a native method, it will use the native VM reflection
     * support.
     *
     * If the given method is a caller-sensitive method and the corresponding
     * caller-sensitive adapter with the caller class parameter is present,
     * it will use the method handle of the caller-sensitive adapter.
     *
     * Otherwise, it will use the direct method handle of the given method.
     *
     * @see CallerSensitive
     * @see CallerSensitiveAdapter
     */
    static MethodAccessorImpl newMethodAccessor(Method method, boolean callerSensitive) {
        if (useNativeAccessor(method)) {
            return DirectMethodHandleAccessor.nativeAccessor(method, callerSensitive);
        }

        // ExceptionInInitializerError may be thrown during class initialization
        // Ensure class initialized outside the invocation of method handle
        // so that EIIE is propagated (not wrapped with ITE)
        ensureClassInitialized(method.getDeclaringClass());

        try {
            if (callerSensitive) {
                var dmh = findCallerSensitiveAdapter(method);
                if (dmh != null) {
                    return DirectMethodHandleAccessor.callerSensitiveAdapter(method, dmh);
                }
            }
            var dmh = getDirectMethod(method, callerSensitive);
            return DirectMethodHandleAccessor.methodAccessor(method, dmh);
        } catch (IllegalAccessException e) {
            throw new InternalError(e);
        }
    }

    /**
     * Creates a ConstructorAccessor for the given reflected constructor.
     *
     * If a given constructor is called before the java.lang.invoke initialization,
     * it will use the native VM reflection support.
     *
     * Otherwise, it will use the direct method handle of the given constructor.
     */
    static ConstructorAccessorImpl newConstructorAccessor(Constructor<?> ctor) {
        if (useNativeAccessor(ctor)) {
            return DirectConstructorHandleAccessor.nativeAccessor(ctor);
        }

        // ExceptionInInitializerError may be thrown during class initialization
        // Ensure class initialized outside the invocation of method handle
        // so that EIIE is propagated (not wrapped with ITE)
        ensureClassInitialized(ctor.getDeclaringClass());

        try {
            MethodHandle mh = JLIA.unreflectConstructor(ctor);
            int paramCount = mh.type().parameterCount();
            MethodHandle target = mh.asFixedArity();
            target = MethodHandles.catchException(target, Throwable.class, wrapHandle(target));
            MethodType mtype = genericMethodType(0, true);
            target = target.asSpreader(Object[].class, paramCount)
                           .asType(mtype);
            return DirectConstructorHandleAccessor.constructorAccessor(ctor, target);
        } catch (IllegalAccessException e) {
            throw new InternalError(e);
        }
    }

    /**
     * Creates a FieldAccessor for the given reflected field.
     *
     * Limitation: Field access via core reflection is only supported after
     * java.lang.invoke completes initialization.
     * java.lang.invoke initialization starts soon after System::initPhase1
     * and method handles are ready for use when initPhase2 begins.
     * During early VM startup (initPhase1), fields can be accessed directly
     * from the VM or through JNI.
     */
    static FieldAccessorImpl newFieldAccessor(Field field, boolean isReadOnly) {
        if (!VM.isJavaLangInvokeInited()) {
            throw new InternalError(field.getDeclaringClass().getName() + "::" + field.getName() +
                    " cannot be accessed reflectively before java.lang.invoke is initialized");
        }

        // ExceptionInInitializerError may be thrown during class initialization
        // Ensure class initialized outside the invocation of method handle
        // so that EIIE is propagated (not wrapped with ITE)
        ensureClassInitialized(field.getDeclaringClass());

        try {
            // the declaring class of the field has been initialized
            var getter = JLIA.unreflectField(field, false);
            var setter = isReadOnly ? null : JLIA.unreflectField(field, true);
            Class<?> type = field.getType();
            if (type == Boolean.TYPE) {
                return MethodHandleBooleanFieldAccessorImpl.fieldAccessor(field, getter, setter, isReadOnly);
            } else if (type == Byte.TYPE) {
                return MethodHandleByteFieldAccessorImpl.fieldAccessor(field, getter, setter, isReadOnly);
            } else if (type == Short.TYPE) {
                return MethodHandleShortFieldAccessorImpl.fieldAccessor(field, getter, setter, isReadOnly);
            } else if (type == Character.TYPE) {
                return MethodHandleCharacterFieldAccessorImpl.fieldAccessor(field, getter, setter, isReadOnly);
            } else if (type == Integer.TYPE) {
                return MethodHandleIntegerFieldAccessorImpl.fieldAccessor(field, getter, setter, isReadOnly);
            } else if (type == Long.TYPE) {
                return MethodHandleLongFieldAccessorImpl.fieldAccessor(field, getter, setter, isReadOnly);
            } else if (type == Float.TYPE) {
                return MethodHandleFloatFieldAccessorImpl.fieldAccessor(field, getter, setter, isReadOnly);
            } else if (type == Double.TYPE) {
                return MethodHandleDoubleFieldAccessorImpl.fieldAccessor(field, getter, setter, isReadOnly);
            } else {
                return MethodHandleObjectFieldAccessorImpl.fieldAccessor(field, getter, setter, isReadOnly);
            }
        } catch (IllegalAccessException e) {
            throw new InternalError(e);
        }
    }

    private static MethodHandle getDirectMethod(Method method, boolean callerSensitive) throws IllegalAccessException {
        var mtype = methodType(method.getReturnType(), method.getParameterTypes());
        var isStatic = Modifier.isStatic(method.getModifiers());
        var dmh = isStatic ? JLIA.findStatic(method.getDeclaringClass(), method.getName(), mtype)
                                        : JLIA.findVirtual(method.getDeclaringClass(), method.getName(), mtype);
        if (callerSensitive) {
            // the reflectiveInvoker for caller-sensitive method expects the same signature
            // as Method::invoke i.e. (Object, Object[])Object
            return makeTarget(dmh, isStatic, false);
        }
        return makeTarget(dmh, isStatic, false);
    }

    /**
     * Finds the method handle of a caller-sensitive adapter for the given
     * caller-sensitive method.  It has the same name as the given method
     * with a trailing caller class parameter.
     *
     * @see CallerSensitiveAdapter
     */
    private static MethodHandle findCallerSensitiveAdapter(Method method) throws IllegalAccessException {
        String name = method.getName();
        boolean isStatic = Modifier.isStatic(method.getModifiers());
        // append a Class parameter
        MethodType mtype = methodType(method.getReturnType(), method.getParameterTypes())
                                .appendParameterTypes(Class.class);
        MethodHandle dmh = isStatic ? JLIA.findStatic(method.getDeclaringClass(), name, mtype)
                                    : JLIA.findVirtual(method.getDeclaringClass(), name, mtype);
        return dmh != null ? makeTarget(dmh, isStatic, true) : null;
    }

    /**
     * Transforms the given dmh into a target method handle with the method type
     * {@code (Object, Object[])Object} or {@code (Object, Class, Object[])Object}
     */
    static MethodHandle makeTarget(MethodHandle dmh, boolean isStatic, boolean hasCallerParameter) {
        MethodType mtype = hasCallerParameter
                                ? methodType(Object.class, Object.class, Object[].class, Class.class)
                                : genericMethodType(1, true);

        MethodHandle target = dmh.asFixedArity();
        target = MethodHandles.catchException(target, Throwable.class, wrapHandle(target));
        if (isStatic) {
            // add leading 'this' parameter to static method which is then ignored
            target = MethodHandles.dropArguments(target, 0, Object.class);
        }
        // number of formal arguments
        int paramCount = dmh.type().parameterCount() - (isStatic ? 0 : 1) - (hasCallerParameter ? 1 : 0);
        target = target.asSpreader(1, Object[].class, paramCount);
        return target.asType(mtype);
    }

    /**
     * Ensures the given class is initialized.  If this is called from <clinit>,
     * this method returns but defc's class initialization is not completed.
     */
    static void ensureClassInitialized(Class<?> defc) {
        if (UNSAFE.shouldBeInitialized(defc)) {
            UNSAFE.ensureClassInitialized(defc);
        }
    }

    /*
     * Returns true if NativeAccessor should be used.
     */
    private static boolean useNativeAccessor(Executable member) {
        if (!VM.isJavaLangInvokeInited())
            return true;

        if (Modifier.isNative(member.getModifiers()))
            return true;

        if (ReflectionFactory.useNativeAccessorOnly())  // for testing only
            return true;

        // MethodHandle::withVarargs on a member with varargs modifier bit set
        // verifies that the last parameter of the member must be an array type.
        // The JVMS does not require the last parameter descriptor of the method descriptor
        // is an array type if the ACC_VARARGS flag is set in the access_flags item.
        // Hence the reflection implementation does not check the last parameter type
        // if ACC_VARARGS flag is set.  Workaround this by invoking through
        // the native accessor.
        int paramCount = member.getParameterCount();
        if (member.isVarArgs() &&
                (paramCount == 0 || !(member.getParameterTypes()[paramCount-1].isArray()))) {
            return true;
        }
        // A method handle cannot be created if its type has an arity >= 255
        // as the method handle's invoke method consumes an extra argument
        // of the method handle itself. Fall back to use the native implementation.
        if (slotCount(member) >= MAX_JVM_ARITY) {
            return true;
        }
        return false;
    }

    private static final int MAX_JVM_ARITY = 255;  // this is mandated by the JVM spec.
    /*
     * Return number of slots of the given member.
     * - long/double args counts for two argument slots
     * - A non-static method consumes an extra argument for the object on which
     *   the method is called.
     * - A constructor consumes an extra argument for the object which is being constructed.
     */
    private static int slotCount(Executable member) {
        int slots = 0;
        Class<?>[] ptypes = member.getParameterTypes();
        for (Class<?> ptype : ptypes) {
            if (ptype == double.class || ptype == long.class) {
                slots++;
            }
        }
        return ptypes.length + slots +
                (Modifier.isStatic(member.getModifiers()) ? 0 : 1);
    }

    /*
     * Delay initializing these static fields until java.lang.invoke is fully initialized.
     */
    static class LazyStaticHolder {
        static final JavaLangInvokeAccess JLIA = SharedSecrets.getJavaLangInvokeAccess();
        static final MethodHandle WRAP;
        static {
            try {
                WRAP = MethodHandles.lookup().findStatic(MethodHandleAccessorFactory.class, "wrap",
                                                         methodType(Object.class, Throwable.class));
            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw new InternalError(e);
            }
        }
    }

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    private static Object wrap(Throwable e) throws InvocationTargetException {
        throw new InvocationTargetException(e);
    }

    private static WeakReference<MethodHandle> weakAsTypeCache = null;

    private static MethodHandle wrapHandle(MethodHandle target) {
        MethodType newType = target.type();
        boolean useWeakCache = useWeakCache(newType);
        if (useWeakCache) {
            WeakReference<MethodHandle> cache = weakAsTypeCache;
            if (cache != null) {
                MethodHandle atc = cache.get();
                if (atc != null && newType == atc.type()) {
                    return atc; // weak cache hit
                }
            }
        }

        MethodHandle mh = WRAP.asType(methodType(newType.returnType(), Throwable.class));
        // clear the soft asType cache if the target's method type is not defined
        // by any of the builtin loaders.
        //
        // System::gc does not clear soft references for G1 and some other collectors.
        // This would cause behavioral change if a class becomes softly reachable
        // but its soft reference would not get cleared and therefore the class
        // and its defining class loader will not get unloaded.
        if (useWeakCache) {
            JLIA.clearSoftAsTypeCache(WRAP);
            weakAsTypeCache = new WeakReference<>(mh);
        }
        return mh;
    }

    private static boolean useWeakCache(MethodType mtype) {
        for (Class<?> type : mtype.parameterArray()) {
            if (!isBuiltinLoader(type.getClassLoader())) {
                return true;
            }
        }
        if (!isBuiltinLoader(mtype.returnType().getClassLoader())) {
            return true;
        }
        return false;
    }

    private static boolean isBuiltinLoader(ClassLoader loader) {
        return loader == null ||
                loader == ClassLoaders.platformClassLoader() ||
                loader == ClassLoaders.appClassLoader();
    }
}
