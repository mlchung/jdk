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

import java.lang.reflect.*;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/*
 * @test
 * @bug 8159746
 * @summary Test invoking a default method in a non-public proxy interface
 * @build p.Foo p.Bar p.ProxyMaker
 * @run testng DefaultMethodProxy
 */
public class DefaultMethodProxy {
    public interface I {
        default String m() { return "I"; }
    }

    @Test
    public static void publicInterface() throws Exception {
        // create a proxy instance of a public proxy interface should succeed
        Proxy proxy = (Proxy)Proxy.newProxyInstance(DefaultMethodProxy.class.getClassLoader(),
                                              new Class<?>[] { I.class }, IH);

        testDefaultMethod(proxy, "I");
        // can get the invocation handler
        assertTrue(Proxy.getInvocationHandler(proxy) == IH);
    }

    @DataProvider(name = "nonPublicIntfs")
    private static Object[][] nonPublicIntfs() throws ClassNotFoundException {
        Class<?> fooClass = Class.forName("p.Foo");
        Class<?> barClass = Class.forName("p.Bar");
        return new Object[][]{
                new Object[]{new Class<?>[]{ fooClass }, "foo"},
                new Object[]{new Class<?>[]{ barClass }, "bar"},
                new Object[]{new Class<?>[]{ barClass, fooClass}, "bar"},
        };
    }

    @Test(dataProvider = "nonPublicIntfs")
    public static void testNonPublicIntfs(Class<?>[] intfs, String expected) throws Exception {
        // create a proxy instance of a non-public proxy interface
        // via p.ProxyMaker which has the access to the proxy interface
        Proxy proxy = p.ProxyMaker.create(IH, intfs);
        // also access to the proxy
        assertTrue(p.ProxyMaker.getInvocationHandler(proxy) == IH);

        // invoke the default method
        testDefaultMethod(proxy, expected);

        try {
            // this class has no access to the proxy
            Proxy.getInvocationHandler(proxy);
            assertTrue(false);
        } catch (InaccessibleObjectException e) {}
    }

    @Test(dataProvider = "nonPublicIntfs", expectedExceptions = { InaccessibleObjectException.class})
    public static void noAccess(Class<?>[] intfs, String unused) throws Exception {
        // this class has no access to non-public interface in another package
        Proxy.newProxyInstance(DefaultMethodProxy.class.getClassLoader(), intfs, IH);
    }

    /*
     * Verify if a default method "m" can be invoked successfully
     */
    static void testDefaultMethod(Proxy proxy, String expected) throws ReflectiveOperationException {
        Method m = proxy.getClass().getDeclaredMethod("m");
        m.setAccessible(true);
        String name = (String)m.invoke(proxy);
        if (!expected.equals(name)) {
            throw new RuntimeException("return value: " + name + " expected: " + expected);
        }
    }

    // invocation handler with access to the non-public interface in package p
    private static final DelegatingInvocationHandler IH = new DelegatingInvocationHandler() {
        @Override
        public Object invoke(Object proxy, Method method, Object[] params) throws Throwable {
            System.out.format("Proxy for %s: invoking %s%n",
                    Arrays.stream(proxy.getClass().getInterfaces())
                            .map(Class::getName)
                            .collect(Collectors.joining(", ")), method.getName());
            if (method.isDefault()) {
                return invokeDefault(proxy, method, params);
            }
            throw new UnsupportedOperationException(method.toString());
        }
    };
}
