/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test Unsafe primitives for frozen arrays
 * @modules java.base/jdk.internal.util
 * @run testng/othervm -Xint FrozenArrayTest
 */

import java.lang.reflect.Array;
import java.util.List;
import java.util.Set;

import jdk.internal.util.FrozenArrays;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class FrozenArrayTest {

    @DataProvider(name = "arrays")
    private Object[][] arrays() {
        return new Object[][] {
                new Object[] { String[].class, new String[] {"foo", "bar", "goo"} },
                new Object[] { Object[].class, new Object[] { List.of("a"), Set.of("b")} },
                new Object[] { int[][].class, new int[][] {new int[] {1,2}, new int[] {2, 3, 4}, new int[] {}} },
        };
    }

    @Test
    public void testIntArray() {
        int[] values = new int[] { 1, 2, 3, 4, 5};
        int[] result = (int[])FrozenArrays.freeze(values);
        assertTrue(FrozenArrays.isFrozen(result));
        assertEquals(result, values);
    }

    @Test
    public void testBooleanArray() {
        boolean[] values = new boolean[] { true, false};
        Object result = FrozenArrays.freeze(values);
        assertTrue(FrozenArrays.isFrozen(result));
        System.out.println("result: " + printArray(result) + " values: " + printArray(values));
        assertEquals(result, values);
    }

    @Test
    public void testShortArray() {
        short[] values = new short[] { 1, 2, 3};
        Object result = FrozenArrays.freeze(values);
        assertTrue(FrozenArrays.isFrozen(result));
        assertEquals(result, values);
    }

    @Test
    public void testLongArray() {
        long[] values = new long[] { 1L, 2L, 3L};
        Object result = FrozenArrays.freeze(values);
        assertTrue(FrozenArrays.isFrozen(result));
        assertEquals(result, values);
    }

    @Test
    public void testMultiDimensionArray() {
        int[][] values = new int[][] { new int[] {1,2}, new int[] {2, 3, 4}, new int[] {}};
        int[][] result = (int[][])FrozenArrays.freeze(values);
        assertTrue(FrozenArrays.isFrozen(result));
        assertEquals(result, values);
        assertFalse(FrozenArrays.isFrozen(result[0]));
    }

    @Test(dataProvider="arrays")
    public void test(Class<?> arrayType, Object[] values) {
        if (!arrayType.isArray() || values.getClass() != arrayType) {
            throw new IllegalArgumentException(arrayType.getName() + " " + values);
        }
        Object result = FrozenArrays.freeze(values);
        assertTrue(FrozenArrays.isFrozen(result));
        assertEquals(result, values);
    }

    @Test(expectedExceptions = {ArrayStoreException.class})
    public void writeToFrozenArray() {
        Integer[] values = new Integer[] { 1, 2, 3};
        Integer[] array = (Integer[])FrozenArrays.freeze(values);
        assertTrue(FrozenArrays.isFrozen(array));
        assertEquals(array, values);

        values[0] = 10;
        assertTrue(array[0] == 1);
        array[0] = values[0];
    }

    @Test(expectedExceptions = {ArrayStoreException.class})
    public void writeToFrozenIntArray() {
        int[] values = new int[] { 1, 2, 3};
        int[] array = (int[])FrozenArrays.freeze(values);
        assertTrue(FrozenArrays.isFrozen(array));
        assertEquals(array, values);

        values[0] = 10;
        assertTrue(array[0] == 1);
        array[0] = values[0];
    }

    private String printArray(Object array) {
        StringBuilder sb = new StringBuilder();
        sb.append(array.getClass().toString());
        sb.append("[");
        int len = Array.getLength(array);
        for (int i=0; i < len; i++) {
            sb.append(i + ": ").append(Array.get(array, i)).append(" ");
        }
        sb.append("]");
        return sb.toString();
    }
}
