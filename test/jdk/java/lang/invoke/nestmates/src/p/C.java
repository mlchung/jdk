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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;

public class C extends ThreadLocal {
    public static Lookup lookup() {
        return MethodHandles.lookup();
    }

    public String name() {
        return "C";
    }

    public static void test() {
        accessD();
        accessE();
    }

    private static void accessD() {
        D d = new D();
        System.out.println("invoking D.name() = " + d.name());
        if (!d.name().equals("D")) {
            throw new AssertionError("unexpected " + d.name());
        }
    }

    private static void accessE() {
        E e = new E();
        System.out.println("invoking E.name() = " + e.name());
        if (!e.name().equals("E")) {
            throw new AssertionError("unexpected " + e.name());
        }
    }

    public static void main(String... args) {
        test();
    }
}
