/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.reflect;

import jdk.internal.vm.annotation.Stable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.WrongMethodTypeException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

import static java.lang.invoke.MethodType.methodType;

/**
 * {@code SuperInvocationHandler} is an <i>invocation handler</i>
 * that supports the invocation of default methods of a proxy instance.
 *
 * @since 16
 */
final class SuperInvocationHandler implements InvocationHandler {
    private final ConcurrentHashMap<Method, MethodHandle> defaultMethods = new ConcurrentHashMap<>();
    private final MethodHandles.Lookup proxyLookup;

    /**
     * Creates a {@code SuperInvocationHandler}
     */
    SuperInvocationHandler(MethodHandles.Lookup lookup) {
        this.proxyLookup = lookup;
    }

    /**
     * Invokes the specified default method on the given {@code proxy} instance with
     * the given parameters.  The given {@code method} must be a default method
     * declared in a proxy interface of the {@code proxy}'s class or inherited
     * from its superinterface directly or indirectly.
     * <p>
     * This method behaves as if called from an {@code invokespecial} instruction
     * from the proxy class as the caller equivalent to the invocation of
     * {@code X.super.m(A* a)} where {@code X} is a proxy interface and
     * the call to {@code X.super::m(A*)} is resolved to the given {@code method}.
     *
     * @param proxy   the {@code Proxy} instance on which the default method to be invoked
     * @param method  the {@code Method} instance corresponding to a default method
     *                declared in a proxy interface of the proxy class or inherited
     *                from its superinterface directly or indirectly
     * @param args    the parameters used for the method invocation; can be {@code null}
     *                if the number of formal parameters required by the method is zero.
     * @return the value returned from the method invocation
     *
     * @throws IllegalArgumentException if any of the following conditions is {@code true}:
     *         <ul>
     *         <li>{@code proxy} is not an instance of the proxy class handled by
     *             the default-method invocation handler; or</li>
     *         <li>the given {@code method} is not a default method declared
     *             in a proxy interface of the proxy class and not inherited from
     *             any of its superinterfaces; or</li>
     *         <li>the given {@code method} is overridden directly or indirectly by
     *             the proxy interfaces and the method reference to the named
     *             method never resolves to the given {@code method}; or</li>
     *         <li>the length of the given {@code args} array does not match the
     *             number of parameters of the method to be invoked; or</li>
     *         <li>any of the {@code args} elements fails the unboxing
     *             conversion if the corresponding method parameter type is
     *             a primitive type; or if, after possible unboxing, any of the
     *             {@code args} elements cannot be assigned to the corresponding
     *             method parameter type.</li>
     *         </ul>
     * @throws NullPointerException if {@code proxy} or {@code method} is {@code null}
     *
     * @since 16
     * @jvms 5.4.3. Method Resolution
     */
    public final Object invoke(Object proxy, Method method, Object[] args) throws Throwable
    {
        Objects.requireNonNull(proxy);
        Objects.requireNonNull(method);

        // verify that the object is actually a proxy instance
        Class<?> proxyClass = proxy.getClass();
        if (proxyClass != proxyLookup.lookupClass()) {
            throw new IllegalArgumentException("'proxy' is not the proxy instance of "
                    + proxyLookup.lookupClass().getName());
        }
        if (!method.isDefault()) {
            throw new IllegalArgumentException("\"" + method + "\" is not a default method");
        }

        // lookup the cached method handle
        MethodHandle superMH = defaultMethods.get(method);
        if (superMH == null) {
            MethodType type = methodType(method.getReturnType(), method.getParameterTypes());
            Class<?> proxyInterface = findProxyInterfaceOrElseThrow(proxyClass, method);
            MethodHandle dmh;
            try {
                dmh = proxyLookup.findSpecial(proxyInterface, method.getName(), type, proxyClass)
                                 .withVarargs(false);
            } catch (IllegalAccessException | NoSuchMethodException e) {
                // should not reach here
                throw new InternalError(e);
            }
            // this check can be turned into assertion as it is guaranteed to succeed by the virtue of
            // looking up a default (instance) method declared or inherited by proxyInterface
            // while proxyClass implements (is a subtype of) proxyInterface ...
            assert ((BooleanSupplier) () -> {
                try {
                    // make sure that the method type matches
                    dmh.asType(type.insertParameterTypes(0, proxyClass));
                    return true;
                } catch (WrongMethodTypeException e) {
                    return false;
                }
            }).getAsBoolean() : "Wrong method type";
            // change return type to Object
            MethodHandle mh = dmh.asType(dmh.type().changeReturnType(Object.class));
            // wrap any exception thrown with InvocationException that is used to distinguish
            // CCE and NPE thrown by the default method body vs
            // CCE and NPE thrown due to the arguments incompatible with the method signature
            mh = MethodHandles.catchException(mh, Throwable.class, wrapWithInvocationExceptionMH());
            // spread array of arguments among parameters (skipping 1st parameter - target)
            mh = mh.asSpreader(1, Object[].class, type.parameterCount());
            // change target type to Object
            mh = mh.asType(MethodType.methodType(Object.class, Object.class, Object[].class));

            // push MH into cache
            MethodHandle cached = defaultMethods.putIfAbsent(method, mh);
            if (cached != null) {
                superMH = cached;
            } else {
                superMH = mh;
            }
        }

        // invoke the super method
        try {
            // the args array can be null if the number of formal parameters required by
            // the method is zero (consistent with Method::invoke)
            Object[] params = args != null ? args : EMPTY_ARGS;
            return superMH.invokeExact(proxy, params);
        } catch (ClassCastException | NullPointerException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        } catch (InvocationException e) {
            throw e.getCause();
        } catch (RuntimeException | Error e) {
            throw e;
        }
    }

    /**
     * Finds the first proxy interface that declares the given method
     * directly or indirectly.
     *
     * @throws IllegalArgumentException if not found
     */
    static Class<?> findProxyInterfaceOrElseThrow(Class<?> proxyClass, Method method) {
        Class<?> declaringClass = method.getDeclaringClass();
        if (!declaringClass.isInterface()) {
            throw new IllegalArgumentException("\"" + method +
                    "\" is not a method declared in the proxy class");
        }

        List<Class<?>> proxyInterfaces = Arrays.asList(proxyClass.getInterfaces());
        // the method's declaring class is a proxy interface
        if (proxyInterfaces.contains(declaringClass))
            return declaringClass;

        Deque<Class<?>> deque = new ArrayDeque<>();
        Set<Class<?>> visited = new HashSet<>();
        boolean indirectMethodRef = false;
        for (Class<?> intf : proxyInterfaces) {
            assert intf != declaringClass;
            visited.add(intf);
            deque.add(intf);

            Class<?> c;
            while ((c = deque.poll()) != null) {
                if (c == declaringClass) {
                    try {
                        // check if this method is the resolved method if referenced from
                        // this proxy interface (i.e. this method is not implemented
                        // by any other superinterface)
                        Method m = intf.getMethod(method.getName(), method.getParameterTypes());
                        if (m.getDeclaringClass() == declaringClass) {
                            return intf;
                        }
                        indirectMethodRef = true;
                    } catch (NoSuchMethodException e) {}

                    // skip traversing its superinterfaces
                    // another proxy interface may extend it and so
                    // the method's declaring class is left unvisited.
                    continue;
                }
                // visit all superinteraces of one proxy interface to find if
                // this proxy interface inherits the method directly or indirectly
                visited.add(c);
                for (Class<?> superIntf : c.getInterfaces()) {
                    if (!visited.contains(superIntf) && !deque.contains(superIntf)) {
                        if (superIntf == declaringClass) {
                            deque.addFirst(superIntf);
                        } else {
                            deque.add(superIntf);
                        }
                    }
                }
            }
        }

        throw new IllegalArgumentException("\"" + method + (indirectMethodRef
                ? "\" is overridden directly or indirectly by the proxy interfaces"
                : "\" is not a method declared in the proxy class"));
    }

    /**
     * Internal exception type to wrap the exception thrown by the default method
     * so that it can distinguish CCE and NPE thrown due to the arguments
     * incompatible with the method signature.
     */
    private static class InvocationException extends ReflectiveOperationException {
        @java.io.Serial
        private static final long serialVersionUID = 0L;
        InvocationException(Throwable cause) {
            super(cause);
        }
    }

    /**
     * Wraps given cause with InvocationException and throws it.
     *
     * @throws InvocationTargetException wrapping given cause
     */
    private static Object wrapWithInvocationException(Throwable cause) throws InvocationException {
        throw new InvocationException(cause);
    }

    @Stable
    private static MethodHandle wrapWithInvocationExceptionMH;

    private static MethodHandle wrapWithInvocationExceptionMH() {
        MethodHandle mh = wrapWithInvocationExceptionMH;
        if (mh == null) {
            try {
                wrapWithInvocationExceptionMH = mh = MethodHandles.lookup().findStatic(
                        SuperInvocationHandler.class,
                        "wrapWithInvocationException",
                        MethodType.methodType(Object.class, Throwable.class)
                );
            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw new InternalError(e);
            }
        }
        return mh;
    }

    private static final Object[] EMPTY_ARGS = new Object[0];
}
