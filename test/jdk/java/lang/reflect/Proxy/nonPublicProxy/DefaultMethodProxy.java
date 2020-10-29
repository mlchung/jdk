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

import java.lang.reflect.InaccessibleInvocationHandlerException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import p.ProxyMaker;

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
    public interface Baz {
        default String baz() { return "baz"; }
    }

    @Test
    public static void publicInterface() throws Exception {
        // create a proxy instance of a public proxy interface should succeed
        Object proxy = Proxy.newProxyInstance(DefaultMethodProxy.class.getClassLoader(),
                                              new Class<?>[] { I.class },
                                              IHF);
        new DefaultMethodProxy(proxy).testDefaultMethod("I");

        // can get the invocation handler
        assertTrue(Proxy.getInvocationHandler(proxy) != null);
    }

    @DataProvider(name = "inaccessibleIntfcs")
    private static Object[][] inaccessibleIntfcs() throws ClassNotFoundException {
        Class<?> fooClass = Class.forName("p.Foo");
        Class<?> barClass = Class.forName("p.Bar");
        return new Object[][]{
                new Object[]{new Class<?>[]{ fooClass }},
                new Object[]{new Class<?>[]{ barClass }},
                // throwing an accessible interface to the mix does not change things
                new Object[]{new Class<?>[]{ fooClass, Baz.class }},
                new Object[]{new Class<?>[]{ barClass, Baz.class }},
        };
    }

    @Test(dataProvider = "inaccessibleIntfcs")
    public static void hasPackageAccess(Class<?>[] intfs) throws ReflectiveOperationException {
        Object proxy = ProxyMaker.makeProxy(IHF, intfs);
        // verify access to the proxy's invocation handler
        assertTrue(ProxyMaker.getInvocationHandler(proxy) != null);

        new DefaultMethodProxy(proxy).testDefaultMethod("foo", "bar");

        try {
            // this class has no access to the proxy's invocation handler
            Proxy.getInvocationHandler(proxy);
            assertTrue(false, "InaccessibleInvocationHandlerException not thrown");
        } catch (InaccessibleInvocationHandlerException e) {}
    }

    @Test(dataProvider = "inaccessibleIntfcs", expectedExceptions = InaccessibleInvocationHandlerException.class)
    public static void noPackageAccess(Class<?>[] intfs) {
        // proxy maker with no access to the non-public interface in package p
        Proxy.newProxyInstance(ProxyMaker.class.getClassLoader(), intfs, IHF);
    }

    final Object proxy;
    DefaultMethodProxy(Object proxy) {
        this.proxy = proxy;
    }

    /*
     * Verify if a default method "m" can be invoked successfully
     */
    void testDefaultMethod(String... expected) throws ReflectiveOperationException {
        Method m = proxy.getClass().getDeclaredMethod("m");
        m.setAccessible(true);
        String name = (String)m.invoke(proxy);
        if (Stream.of(expected).noneMatch(name::equals)) {
            throw new RuntimeException("return value: " + name + " expected one of: " + Arrays.toString(expected));
        }
    }

    // invocation handler factory that creates handlers that forward invocations to super default methods
    private static final Function<InvocationHandler, InvocationHandler> IHF =
        superHandler -> (proxy, method, params) -> {
                System.out.format("Proxy for %s: invoking %s%n",
                                  Arrays.stream(proxy.getClass().getInterfaces())
                                        .map(Class::getName)
                                        .collect(Collectors.joining(", ")), method.getName());
                if (method.isDefault()) {
                    return superHandler.invoke(proxy, method, params);
                }
                throw new UnsupportedOperationException(method.toString());
        };

}
