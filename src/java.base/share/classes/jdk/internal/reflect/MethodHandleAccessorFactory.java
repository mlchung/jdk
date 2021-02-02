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

import jdk.internal.access.JavaLangInvokeAccess;
import jdk.internal.access.JavaLangReflectAccess;
import jdk.internal.access.SharedSecrets;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedAction;

import static java.lang.invoke.MethodType.methodType;

final class MethodHandleAccessorFactory {
    static MethodAccessor newMethodAccessor(Method m) {
        try {
            Method method = JLRA.copyMethod(m);
            // access check has already been performed before getting MethodAccess
            // suppress the access check when unreflecting the method into a DirectMethodHandle
            AccessController.doPrivileged(new PrivilegedAction<Method>() {
                @Override
                public Method run() {
                    method.setAccessible(true);
                    return method;
                }
            });
            MethodHandle target = directMethodHandle(method.getDeclaringClass(), method);
            return new DirectMethodAccessorImpl(method, target);
        } catch (IllegalAccessException e) {
            throw new InternalError(e);
        }
    }

    static ConstructorAccessor newConstructorAccessor(Constructor<?> ctor) {
        try {
            MethodHandle mh = JLIA.unreflectConstructor(ctor);
            int paramCount = mh.type().parameterCount();

            // invoke method with an exception handler that throws InvocationTargetException
            mh = MethodHandles.catchException(mh, Throwable.class,
                                              WRAP.asType(methodType(mh.type().returnType(), Throwable.class)));
            MethodHandle target = mh.asSpreader(Object[].class, paramCount)
                                    .asType(methodType(Object.class, Object[].class));
            return new DirectConstructorAccessorImpl(ctor, target);
        } catch (IllegalAccessException e) {
            throw new InternalError(e);
        }
    }

    static ConstructorAccessor newConstructorAccessorForSerialization(Constructor<?> ctor, Class<?> instantiatedClass) {
        try {
            MethodHandle mh = JLIA.unreflectConstructorForSerialization(ctor, instantiatedClass);
            int paramCount = mh.type().parameterCount();

            // invoke constructor with an exception handler
            mh = MethodHandles.catchException(mh, Throwable.class,
                                              WRAP.asType(methodType(mh.type().returnType(), Throwable.class)));
            MethodHandle target = mh.asSpreader(Object[].class, paramCount)
                                    .asType(methodType(Object.class, Object[].class));
            return new DirectConstructorAccessorImpl(ctor, target);
        } catch (IllegalAccessException e) {
            throw new InternalError(e);
        }
    }

    static FieldAccessor newFieldAccessor(Field field, boolean isReadOnly) {
        try {
            MethodHandle getter = JLIA.unreflectField(field, false);
            MethodHandle setter = isReadOnly ? null : JLIA.unreflectField(field, true);

            if (Modifier.isStatic(field.getModifiers())) {
                // static field
                getter = MethodHandles.dropArguments(getter, 0, Object.class)
                                      .asType(methodType(Object.class, Object.class));
                if (setter != null) {
                    setter = MethodHandles.dropArguments(setter, 0, Object.class)
                                          .asType(methodType(void.class, Object.class, Object.class));
                }
            }
            Class<?> type = field.getType();
            if (type == Boolean.TYPE) {
                return new MethodHandleBooleanFieldAccessorImpl(field, getter, setter);
            } else if (type == Byte.TYPE) {
                return new MethodHandleByteFieldAccessorImpl(field, getter, setter);
            } else if (type == Short.TYPE) {
                return new MethodHandleShortFieldAccessorImpl(field, getter, setter);
            } else if (type == Character.TYPE) {
                return new MethodHandleCharacterFieldAccessorImpl(field, getter, setter);
            } else if (type == Integer.TYPE) {
                return new MethodHandleIntegerFieldAccessorImpl(field, getter, setter);
            } else if (type == Long.TYPE) {
                return new MethodHandleLongFieldAccessorImpl(field, getter, setter);
            } else if (type == Float.TYPE) {
                return new MethodHandleFloatFieldAccessorImpl(field, getter, setter);
            } else if (type == Double.TYPE) {
                return new MethodHandleDoubleFieldAccessorImpl(field, getter, setter);
            } else {
                return new MethodHandleObjectFieldAccessorImpl(field, getter, setter);
            }
        } catch (IllegalAccessException e) {
            throw new InternalError(e);
        }
    }

    static MethodHandle directMethodHandle(Class<?> caller, Method method)
            throws IllegalAccessException
    {
        MethodHandle mh = JLIA.unreflectMethod(caller, method);
        int paramCount = mh.type().parameterCount();

        // invoke method with an exception handler that throws InvocationTargetException
        mh = MethodHandles.catchException(mh, Throwable.class,
                                          WRAP.asType(methodType(mh.type().returnType(), Throwable.class)));
        if (Modifier.isStatic(method.getModifiers())) {
            // static method
            MethodHandle spreader = mh.asSpreader(Object[].class, paramCount);
            spreader = MethodHandles.dropArguments(spreader, 0, Object.class);
            mh = spreader.asType(methodType(Object.class, Object.class, Object[].class));
        } else {
            // instance method
            MethodHandle spreader = mh.asSpreader(Object[].class, paramCount - 1);
            mh = spreader.asType(methodType(Object.class, Object.class, Object[].class));
        }
        return mh;
    }

    // make this package-private to workaround a bug in Reflection::getCallerClass
    // that skips this class and the lookup class is ReflectionFactory instead
    static Object wrap(Throwable e) throws InvocationTargetException {
        throw new InvocationTargetException(e);
    }

    private static final JavaLangInvokeAccess JLIA;
    private static final JavaLangReflectAccess JLRA;
    private static final MethodHandle WRAP;
    static {
        try {
            JLIA = SharedSecrets.getJavaLangInvokeAccess();
            JLRA = SharedSecrets.getJavaLangReflectAccess();
            WRAP = MethodHandles.lookup().findStatic(MethodHandleAccessorFactory.class, "wrap",
                                                     methodType(Object.class, Throwable.class));
        } catch (NoSuchMethodException|IllegalAccessException e) {
            throw new InternalError(e);
        }
    }

}
