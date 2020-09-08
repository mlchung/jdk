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

package p;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;

import static java.lang.invoke.MethodType.methodType;

/*
 * This test illustrates a language that allows E to access C's protected
 * members without synthesizing access bridges in C.class.
 *
 * For example, Java allows the nested classes to access
 * the protected members among them since they are the same package members
 * and the static compiler has to generate bridges.
 */
public class E {
    public String name() {
        return "E";
    }
    public static void test() throws Throwable {
        assertTrue(E.class.isNestmateOf(C.class));

        // E is a nestmate of C and E's lookup can find private member of C
        Lookup lookup = MethodHandles.lookup();
        MethodHandle mh1 = lookup.findStatic(C.class, "accessD", methodType(void.class));
        mh1.invokeExact();

        // Lookup::in teleports to a nest member
        // calling findSpecial: lookupClass must be identical to specialCaller i.e. C
        Lookup lookup2 = lookup.in(C.class);
        assertTrue(lookup2.hasPrivateAccess());
        MethodHandle mh2 = lookup2.findSpecial(ThreadLocal.class, "initialValue",
                                               methodType(Object.class),
                                               C.class);
        C c = new C();
        System.out.println("invoking protected ThreadLocal::initialValue");
        Object o = mh2.invokeExact(c);
    }

    static void assertTrue(boolean v) {
        if (!v) {
            throw new AssertionError("expected true but got " + v);
        }
    }
}
