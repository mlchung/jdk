/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import java.io.IOException;
import java.lang.reflect.InvocationHandler2;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.stream.Stream;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 * @test
 * @bug 8159746
 * @run testng DefaultMethods
 * @summary Basic tests for Proxy::invokeSuper default method
 */

public class DefaultMethods {
    public interface I1 {
        default int m() {
            return 10;
        }
    }

    public interface I2 {
        default int m() {
            return 20;
        }

        private void privateMethod() {
            throw new Error("should not reach here");
        }
    }

    // I3::m inherits from I2:m
    public interface I3 extends I2 {
        default int m3(String... s) {
            return Arrays.stream(s).mapToInt(String::length).sum();
        }
    }

    public interface I4 extends I1, I2 {
        default int m() {
            return 40;
        }

        default int mix(int a, String b) {
            return 0;
        }
    }

    public interface I12 extends I1, I2 {
        @Override
        int m();

        default int sum(int a, int b) {
            return a + b;
        }

        default Object[] concat(Object first, Object... rest) {
            Object[] result = new Object[1 + rest.length];
            result[0] = first;
            System.arraycopy(rest, 0, result, 1, rest.length);
            return result;
        }
    }

    public interface IX {
        default void doThrow(Throwable exception) throws Throwable {
            throw exception;
        }
    }

    private static Method findDefaultMethod(Class<?> refc, Method m) {
        try {
            assertTrue(refc.isInterface());

            Method method = refc.getMethod(m.getName(), m.getParameterTypes());
            assertTrue(method.isDefault());
            return method;
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void test() throws IllegalAccessException {
        ClassLoader loader = DefaultMethods.class.getClassLoader();
        Object proxy = Proxy.newProxyInstance(loader, new Class<?>[] { I1.class, I2.class},
                (superInvoker, o, method, params) -> {
                    return superInvoker.invokeSuper(o, findDefaultMethod(I2.class, method), params);
                });
        I1 i1 = (I1) proxy;
        assertEquals(i1.m(), 20);
    }

    // a default method is declared in one of the proxy interfaces
    @DataProvider(name = "defaultMethods")
    private Object[][] defaultMethods() {
        return new Object[][]{
            new Object[]{new Class<?>[]{I1.class, I2.class}, true, 10},
            new Object[]{new Class<?>[]{I1.class, I3.class}, true, 10},
            new Object[]{new Class<?>[]{I1.class, I12.class}, true, 10},
            new Object[]{new Class<?>[]{I2.class, I12.class}, true, 20},
            new Object[]{new Class<?>[]{I4.class}, true, 40},
            new Object[]{new Class<?>[]{I4.class, I3.class}, true, 40},
            new Object[]{new Class<?>[]{I12.class}, false, -1},
            new Object[]{new Class<?>[]{I12.class, I1.class, I2.class}, false, -1}
        };
    }

    @Test(dataProvider = "defaultMethods")
    public void testDefaultMethod(Class<?>[] intfs, boolean isDefault, int expected) throws Exception {
        InvocationHandler2 ih = (superInvoker, proxy, method, params) -> {
            System.out.format("invoking %s with parameters: %s%n", method, Arrays.toString(params));
            switch (method.getName()) {
                case "m":
                    assertTrue(method.isDefault() == isDefault);
                    assertTrue(Arrays.stream(proxy.getClass().getInterfaces())
                                     .anyMatch(intf -> method.getDeclaringClass() == intf),
                               Arrays.toString(proxy.getClass().getInterfaces()));
                    if (method.isDefault()) {
                        return superInvoker.invokeSuper(proxy, method, params);
                    } else {
                        return -1;
                    }
                default:
                    throw new UnsupportedOperationException(method.toString());
            }
        };

        Object proxy = Proxy.newProxyInstance(DefaultMethods.class.getClassLoader(), intfs, ih);
        Method m = proxy.getClass().getMethod("m");
        int result = (int)m.invoke(proxy);
        assertEquals(result, expected);
    }

    // a default method may be declared in a proxy interface or
    // inherited from a superinterface of a proxy interface
    @DataProvider(name = "supers")
    private Object[][] supers() {
        return new Object[][]{
            // invoke "m" implemented in the first proxy interface
            // same as the method passed to InvocationHandler::invoke
            new Object[]{new Class<?>[]{I1.class}, I1.class, 10},
            new Object[]{new Class<?>[]{I2.class}, I2.class, 20},
            new Object[]{new Class<?>[]{I1.class, I2.class}, I1.class, 10},
            // "m" is implemented in I2, an indirect superinterface of I3
            new Object[]{new Class<?>[]{I3.class}, I3.class, 20},
            // "m" is implemented in I1, I2 and overridden in I4
            new Object[]{new Class<?>[]{I4.class}, I4.class, 40},
            // invoke "m" implemented in the second proxy interface
            // different from the method passed to InvocationHandler::invoke
            new Object[]{new Class<?>[]{I1.class, I2.class}, I2.class, 20},
            new Object[]{new Class<?>[]{I1.class, I3.class}, I3.class, 20},
            // I2::m is implemented in more than one proxy interface directly or indirectly
            // I3::m resolves to I2::m (indirect superinterface)
            // I2 is the superinterface of I4 and I4 overrides m
            // the proxy class can invoke I4::m and I2::m
            new Object[]{new Class<?>[]{I3.class, I4.class}, I3.class, 20},
            new Object[]{new Class<?>[]{I3.class, I4.class}, I4.class, 40},
            new Object[]{new Class<?>[]{I4.class, I3.class}, I3.class, 20},
            new Object[]{new Class<?>[]{I4.class, I3.class}, I4.class, 40}
        };
    }

    @Test(dataProvider = "supers")
    public void testSuper(Class<?>[] intfs, Class<?> proxyInterface, int expected) throws Exception {
        final InvocationHandler2 ih = (superInvoker, proxy, method, params) -> {
            switch (method.getName()) {
                case "m":
                    assertTrue(method.isDefault());
                    return superInvoker.invokeSuper(proxy, findDefaultMethod(proxyInterface, method), params);
                default:
                    throw new UnsupportedOperationException(method.toString());
            }
        };
        ClassLoader loader = proxyInterface.getClassLoader();
        Object proxy = Proxy.newProxyInstance(loader, intfs, ih);
        InvocationHandler2.SuperInvoker superInvoker = getSuperInvoker(proxy);
        if (proxyInterface == I1.class) {
            I1 i1 = (I1) proxy;
            assertEquals(i1.m(), expected);
        } else if (proxyInterface == I2.class) {
            I2 i2 = (I2) proxy;
            assertEquals(i2.m(), expected);
        } else if (proxyInterface == I3.class) {
            I3 i3 = (I3) proxy;
            assertEquals(i3.m(), expected);
        } else if (proxyInterface == I4.class) {
            I4 i4 = (I4) proxy;
            assertEquals(i4.m(), expected);
        } else {
            throw new UnsupportedOperationException(proxyInterface.toString());
        }
        // invoke via superInvoker directly
        assertEquals(superInvoker.invokeSuper(proxy, proxyInterface.getMethod("m")), expected);
    }

    // invoke I12 default methods with parameters and var args
    @Test
    public void testI12() throws Exception {
        final InvocationHandler2 ih = (superInvoker, proxy, method, params) -> {
            System.out.format("invoking %s with parameters: %s%n", method, Arrays.toString(params));
            switch (method.getName()) {
                case "sum":
                case "concat":
                    assertTrue(method.isDefault());
                    return superInvoker.invokeSuper(proxy, method, params);
                default:
                    throw new UnsupportedOperationException(method.toString());
            }
        };
        ClassLoader loader = DefaultMethods.class.getClassLoader();
        I12 i12 = (I12) Proxy.newProxyInstance(loader, new Class<?>[] { I12.class }, ih);
        InvocationHandler2.SuperInvoker superInvoker = getSuperInvoker(i12);
        assertEquals(i12.sum(1, 2), 3);
        assertEquals(i12.concat(1, 2, 3, 4), new Object[]{1, 2, 3, 4});
        Method m = I12.class.getMethod("concat", Object.class, Object[].class);
        assertTrue(m.isDefault());
        assertEquals(superInvoker.invokeSuper(i12, m, 100, new Object[] {"foo", true, "bar"}),
                     new Object[] {100, "foo", true, "bar"});
    }

    // test a no-arg default method with and without arguments passed in the invocation
    @Test
    public void testEmptyArgument() throws Exception {
        ClassLoader loader = DefaultMethods.class.getClassLoader();
        Object proxy = Proxy.newProxyInstance(loader, new Class<?>[]{I4.class}, HANDLER);
        InvocationHandler2.SuperInvoker superInvoker = getSuperInvoker(proxy);
        Method m1 = I4.class.getMethod("m");
        assertTrue(m1.getDeclaringClass() == I4.class);
        assertTrue(m1.isDefault());
        superInvoker.invokeSuper(proxy, m1);
        superInvoker.invokeSuper(proxy, m1, new Object[0]);

        Method m2 = I4.class.getMethod("mix", int.class, String.class);
        assertTrue(m1.getDeclaringClass() == I4.class);
        assertTrue(m1.isDefault());
        superInvoker.invokeSuper(proxy, m2, Integer.valueOf(100), "foo");
    }

    @Test
    public void testVarArgs() throws Exception {
        ClassLoader loader = DefaultMethods.class.getClassLoader();
        I3 proxy = (I3)Proxy.newProxyInstance(loader, new Class<?>[]{I3.class}, HANDLER);
        InvocationHandler2.SuperInvoker superInvoker = getSuperInvoker(proxy);
        Method m = I3.class.getMethod("m3", String[].class);
        assertTrue(m.isVarArgs() && m.isDefault());
        assertEquals(proxy.m3("a", "b", "cde"), 5);
        assertEquals(superInvoker.invokeSuper(proxy, m, (Object)new String[] {"a", "bc" }), 3);
    }

    /*
     * Invoke I12::m which is an abstract method
     */
    @Test(expectedExceptions = {IllegalArgumentException.class})
    public void invokeAbstractMethod() throws Exception {
        ClassLoader loader = DefaultMethods.class.getClassLoader();
        I12 proxy = (I12) Proxy.newProxyInstance(loader, new Class<?>[]{I12.class}, HANDLER);
        Method method = I12.class.getMethod("m");
        assertTrue(method.getDeclaringClass() == I12.class);
        assertFalse(method.isDefault());
        proxy.m();
    }

    /*
     * Invoke a non proxy (default) method with parameters
     */
    @Test(expectedExceptions = {IllegalArgumentException.class})
    public void invokeNonProxyMethod() throws Exception {
        ClassLoader loader = DefaultMethods.class.getClassLoader();
        I3 proxy = (I3) Proxy.newProxyInstance(loader, new Class<?>[]{I3.class}, HANDLER);
        InvocationHandler2.SuperInvoker superInvoker = getSuperInvoker(proxy);
        Method m = I4.class.getMethod("mix", int.class, String.class);
        assertTrue(m.isDefault());
        superInvoker.invokeSuper(proxy, m);
    }

    // negative cases
    @DataProvider(name = "negativeCases")
    private Object[][] negativeCases() {
        return new Object[][]{
            // I4::m overrides I1::m and I2::m
            new Object[] { new Class<?>[]{I4.class}, I1.class, "m" },
            new Object[] { new Class<?>[]{I4.class}, I2.class, "m" },
            // I12::m is not a default method
            new Object[] { new Class<?>[]{I12.class}, I12.class, "m" },
            // non-proxy default method
            new Object[] { new Class<?>[]{I3.class}, I1.class, "m" },
            // not a default method and not a proxy interface
            new Object[] { new Class<?>[]{I12.class}, DefaultMethods.class, "test" },
            new Object[] { new Class<?>[]{I12.class}, Runnable.class, "run" },
            // I2::privateMethod is a private method
            new Object[] { new Class<?>[]{I3.class}, I2.class, "privateMethod" }
        };
    }

    @Test(dataProvider = "negativeCases", expectedExceptions = {IllegalArgumentException.class})
    public void testNegativeCase(Class<?>[] interfaces, Class<?> defc, String name)
            throws Exception {
        ClassLoader loader = DefaultMethods.class.getClassLoader();
        Object proxy = Proxy.newProxyInstance(loader, interfaces, HANDLER);
        InvocationHandler2.SuperInvoker superInvoker = getSuperInvoker(proxy);
        try {
            Method method = defc.getDeclaredMethod(name);
            superInvoker.invokeSuper(proxy, method);
        } catch (Throwable e) {
            System.out.format("%s method %s::%s exception thrown: %s%n",
                              Arrays.toString(interfaces), defc.getName(), name, e.getMessage());
            throw e;
        }
    }

    @DataProvider(name = "illegalArguments")
    private Object[][] illegalArguments() {
        return new Object[][] {
            new Object[] {},
            new Object[] { 100 },
            new Object[] { 100, "foo", 100 },
            new Object[] { 100L, "foo" },
            new Object[] { "foo", 100},
            new Object[] { null, "foo" }
        };
    }

    @Test(dataProvider = "illegalArguments", expectedExceptions = {IllegalArgumentException.class})
    public void testIllegalArgument(Object... args) throws Exception {
        ClassLoader loader = DefaultMethods.class.getClassLoader();
        I4 proxy = (I4)Proxy.newProxyInstance(loader, new Class<?>[]{I4.class}, HANDLER);
        InvocationHandler2.SuperInvoker superInvoker = getSuperInvoker(proxy);
        Method m = I4.class.getMethod("mix", int.class, String.class);
        assertTrue(m.isDefault());
        if (args.length == 0) {
            // substitute empty args with null since @DataProvider doesn't allow null array
            args = null;
        }
        superInvoker.invokeSuper(proxy, m, args);
    }

    @DataProvider(name = "invocationTargetExceptions")
    private Object[][] invocationTargetExceptions() {
        return new Object[][] {
            new Object[] { new IOException() },
            new Object[] { new IllegalArgumentException() },
            new Object[] { new ClassCastException() },
            new Object[] { new NullPointerException() },
            new Object[] { new AssertionError() },
            new Object[] { new Throwable() }
        };
    }

    @Test(dataProvider = "invocationTargetExceptions")
    public void testInvocationTargetException(Throwable exception) throws Exception {
        ClassLoader loader = DefaultMethods.class.getClassLoader();
        IX proxy = (IX)Proxy.newProxyInstance(loader, new Class<?>[]{IX.class}, HANDLER);
        InvocationHandler2.SuperInvoker superInvoker = getSuperInvoker(proxy);
        Method m = IX.class.getMethod("doThrow", Throwable.class);
        try {
            superInvoker.invokeSuper(proxy, m, exception);
        } catch (InvocationTargetException e) {
            assertEquals(e.getCause(), exception);
        }
    }

    private static InvocationHandler2.SuperInvoker getSuperInvoker(Object proxy) {
        try {
            Class<?> proxyClass = proxy.getClass();
            Class<?>[] intfcs = proxyClass.getInterfaces();
            // take any method of any interface with a method
            Method m = Stream.of(intfcs)
                  .flatMap(intf -> Stream.of(intf.getMethods()))
                  .findAny().orElseThrow(() -> new IllegalArgumentException("proxy has no methods"));
            Object returnValue = getDefault(m.getReturnType());
            Class<?>[] ptypes = m.getParameterTypes();
            Object[] args = new Object[ptypes.length];
            for (int i = 0; i < ptypes.length; i++) {
                args[i] = getDefault(ptypes[i]);
            }
            InvokerCapturingHandler handler = new InvokerCapturingHandler(returnValue);
            // we assume caching works and we get the same proxy class
            Object captureProxy = Proxy.newProxyInstance(proxyClass.getClassLoader(), intfcs, handler);
            m.invoke(captureProxy, args);
            return handler.getSuperInvoker();
        } catch (Exception e) {
            throw new AssertionError("Can't get Proxy lookup", e);
        }
    }

    private static Object getDefault(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte)0;
        if (type == short.class) return (short)0;
        if (type == char.class) return (char)0;
        if (type == int.class) return (int)0;
        if (type == long.class) return (long)0;
        if (type == float.class) return (float)0;
        if (type == double.class) return (double)0;
        return null;
    }

    private static class InvokerCapturingHandler implements InvocationHandler2 {
        private SuperInvoker superInvoker;
        private final Object returnValue;

        InvokerCapturingHandler(Object returnValue) {
            this.returnValue = returnValue;
        }

        @Override
        public Object invoke(SuperInvoker superInvoker, Object proxy, Method method, Object[] args) throws Throwable {
            this.superInvoker = superInvoker;
            return returnValue;
        }

        SuperInvoker getSuperInvoker() {
            return superInvoker;
        }
    }

    private static final InvocationHandler2 HANDLER = (superInvoker, proxy, method, params) -> {
        System.out.format("invoking %s with parameters: %s%n", method, Arrays.toString(params));
        return superInvoker.invokeSuper(proxy, method, params);
    };
}
