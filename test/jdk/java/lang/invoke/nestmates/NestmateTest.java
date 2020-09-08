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

/*
 * @test
 * @bug 8199386
 * @run testng/othervm NestmateTest
 * @summary Lookup::in teleports to a nestmate and produces a private lookup
 */

import java.lang.invoke.*;
import java.lang.invoke.MethodHandles.Lookup;

import org.testng.annotations.Test;
import static java.lang.invoke.MethodHandles.Lookup.*;
import static java.lang.invoke.MethodHandles.*;
import static java.lang.invoke.MethodType.*;
import static org.testng.Assert.*;

// See also http://mail.openjdk.java.net/pipermail/valhalla-spec-experts/2016-January/000071.html
public class NestmateTest {
    /*
     * 1) This lookup is not in the same nest of MyThreadLocal
     * 2) Teleport to a member of MyThreadLocal's nest will drop PRIVATE access
     */
    @Test
    public void test() throws Throwable {
        Class<?> c = MyThreadLocal.Invoker.class;

        Lookup L1 = lookup();
        assertFalse(L1.lookupClass().isNestmateOf(MyThreadLocal.class));
        assertTrue(MyThreadLocal.class.isNestmateOf(c));

        Lookup L2 = L1.in(c);
        assertTrue(L2.lookupClass() == c);
        assertTrue((L2.lookupModes() & PRIVATE) == 0);
    }

    /*
     *
     */
    @Test
    public void intraNestTeleport() throws Throwable {
        Class<?> host = MyThreadLocal.class;
        Class<?> member = MyThreadLocal.Invoker.class;
        Lookup L1 = MyThreadLocal.lookup();         // Lookup on MyThreadLocal
        Lookup L2 = L1.in(member);                  // teleport to a member
        assertTrue(L2.lookupClass().isNestmateOf(L1.lookupClass()));
        assertTrue(L2.hasFullPrivilegeAccess());          // full-power lookup

        // access to a private method of its nest host
        MethodHandle mh = L2.findStatic(host, "testNestmateAccess", methodType(void.class));
        mh.invokeExact();

        // L1 has access to a protected method in its superclass in a different package
        // because host (specialCaller) must be identical to the lookup class of Lookup object
        L1.findSpecial(ThreadLocal.class, "initialValue",
                       methodType(Object.class),
                       host);
        try {
            // L2 has no access to a protected method in its superclass in a different package
            // as L2's lookup class != host
            assertFalse(L2.lookupClass() == host);
            L2.findSpecial(ThreadLocal.class, "initialValue",
                           methodType(Object.class),
                           host);
            assertTrue(false);
        } catch (IllegalAccessException e) { }

        // cross-package teleport: has only PUBLIC access
        Lookup L3 = L2.in(ThreadLocal.class);
        assertTrue(L3.lookupModes() == PUBLIC);
    }

    /*
     * Lookup::in teleports to a nestmate and produces a full privilege Lookup.
     */
    @Test
    public void remoteProtectedMethod() throws Throwable {
        MyThreadLocal tl = new MyThreadLocal();
        // obtain the Lookup object on Invoker
        // then teleport to its nestmate to get a method handle on
        // the protected java.lang.ThreadLocal::initialValue method
        Lookup lookup = MyThreadLocal.Invoker.LOOKUP.in(MyThreadLocal.class);
        assertTrue(lookup.hasFullPrivilegeAccess());
        MethodHandle mh = lookup.findSpecial(ThreadLocal.class, "initialValue",
                                             methodType(Object.class),
                                             MyThreadLocal.class);
        Object o = mh.bindTo(tl).invokeExact();
        assertTrue(o == null);
    }

    /*
     * Dynamic invocation of super::initialValue method handle.
     */
    @Test
    public void staticRemoteProtectedMethod() throws Throwable {
        MyThreadLocal tl = new MyThreadLocal();
        MethodHandle mh = tl.remoteProtectedMethod();
        mh = mh.bindTo(tl);
        assertTrue(mh.invokeExact() == null);
    }
}
