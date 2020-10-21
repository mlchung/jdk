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

package jdk.test;

import java.lang.reflect.NewInvocationHandler;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Tests invocation of default methods in exported types and inaccessible types
 * in a named module
 */
public class DefaultMethods {
    private final static Module TEST_MODULE = DefaultMethods.class.getModule();
    private final static NewInvocationHandler IH = (invoker, proxy, method, params) -> {
        return invoker.invoke(proxy, method, params);
    };

    public static void main(String... args) throws Exception {
        // exported types from m1
        testDefaultMethod(new Class<?>[] { p.one.I.class, p.two.A.class}, 1);
        // qualified-exported type from m2
        testDefaultMethod(new Class<?>[] { p.two.internal.C.class, p.two.A.class }, 2);
        // module-private type from test module
        testDefaultMethod(new Class<?>[] { jdk.test.internal.R.class }, 10);
        // non-public interface in the same runtime package
        testDefaultMethod(new Class<?>[] { Class.forName("jdk.test.NP") }, 100);

        // inaccessible type - not exported to test module
        inaccessibleDefaultMethod(Class.forName("p.three.internal.Q"));
        // non-public interface in the same runtime package
        inaccessibleDefaultMethod(Class.forName("jdk.test.internal.NP"));
    }

    static void testDefaultMethod(Class<?>[] intfs, int expected) throws Exception {
        Object proxy = Proxy.newProxyInstance(TEST_MODULE.getClassLoader(), intfs, IH);
        if (!proxy.getClass().getModule().isNamed()) {
            throw new RuntimeException(proxy.getClass() + " expected to be in a named module");
        }
        Method m = intfs[0].getMethod("m");
        int result = (int)m.invoke(proxy);
        if (result != expected) {
            throw new RuntimeException("return value: " + result + " expected: " + expected);
        }
    }

    /*
     * Proxy::newProxyInstance fails as the caller has no access on the proxy interfaces
     */
    static void inaccessibleDefaultMethod(Class<?> intf) throws Exception {
        try {
            Proxy.newProxyInstance(TEST_MODULE.getClassLoader(), new Class<?>[]{intf}, IH);
            throw new RuntimeException("IAE not thrown");
        } catch (IllegalCallerException e) {
        }
    }
}
