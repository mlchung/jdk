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
package jdk.internal.util;

import java.lang.reflect.Array;
import java.util.Objects;
import jdk.internal.misc.Unsafe;

public class FrozenArrays {
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    /**
     * Tests if the given array is frozen.
     * @param array an array
     * @return true if the specified array is a frozen array
     * @throws IllegalArgumentException the given array is not an array
     */
    public static boolean isFrozen(Object array) {
        Objects.requireNonNull(array);
        if (!array.getClass().isArray()) {
            throw new IllegalArgumentException("must be an array");
        }
        return UNSAFE.isFrozenArray(array);
    }

    /**
     * Returns a frozen array containing the given array.
     *
     * @param array
     * @return frozen array
     * @throws IllegalArgumentException the given array is not an array
     */
    public static Object freeze(Object array) {
        Objects.requireNonNull(array);
        if (!array.getClass().isArray()) {
            throw new IllegalArgumentException("must be an array");
        }

        if (isFrozen(array)) {
            return array;
        }
        int length = Array.getLength(array);
        Object result = UNSAFE.makeLarvalArray(array.getClass(), length);
        System.arraycopy(array, 0, result, 0, length);
        return UNSAFE.freezeLarvalArray(result);
    }

    /**
     * Copies the specified array, truncating or padding with nulls (if necessary)
     * so the copy has the specified length.  For all indices that are
     * valid in both the original array and the copy, the two arrays will
     * contain identical values.  For any indices that are valid in the
     * copy but not the original, the copy will contain {@code null}.
     * Such indices will exist if and only if the specified length
     * is greater than that of the original array.
     * The resulting array is of exactly the same class as the original array.
     *
     * @param <T> the class of the objects in the array
     * @param original the array to be copied
     * @param newLength the length of the copy to be returned
     * @return a copy of the original array, truncated or padded with nulls
     *     to obtain the specified length
     * @throws NegativeArraySizeException if {@code newLength} is negative
     * @throws NullPointerException if {@code original} is null
     */
    public static <T> T[] copyOf(T[] original, int newLength) {
        Objects.requireNonNull(original);
        if (!original.getClass().isArray()) {
            throw new IllegalArgumentException("must be an array");
        }

        Object array =  UNSAFE.makeLarvalArray(original.getClass(), newLength);
        int numElements = original.length <= newLength ? original.length : newLength;
        System.arraycopy(original, 0, array, 0, numElements);
        @SuppressWarnings("unchecked")
        T[] result = (T[])UNSAFE.freezeLarvalArray(array);
        return result;
    }

    /**
     * Builder for constructing a frozen array
     * @param <E> element type
     */
    public static class Builder<E> {
        final E[] array;

        @SuppressWarnings("unchecked")
        public Builder(Class<?> arrayClass, int length) {
            this.array = (E[])UNSAFE.makeLarvalArray(arrayClass, length);
        }

        public E[] larvalArray() {
            return array;
        }

        public Builder<E> set(int index, E element) {
            array[index] = element;
            return this;
        }

        @SuppressWarnings("unchecked")
        public E[] build() {
            return (E[]) UNSAFE.freezeLarvalArray(array);
        }
    }

    /**
     * Returns a frozen array containing the given elements.
     *
     * @param <E> the {@code array}'s element type
     * @param elements the elements to be contained in the array
     * @return a frozen array containing the specified elements
     */
    public static <E> E[] newArray(E[] elements) {
        Class<?> arrayType = elements.getClass();
        int length = elements.length;
        @SuppressWarnings("unchecked")
        E[] result = (E[])UNSAFE.makeLarvalArray(arrayType, length);
        System.arraycopy(elements, 0, result, 0, length);
        UNSAFE.freezeLarvalArray(result);
        return result;
    }

    /**
     * Returns a frozen array containing the given elements.
     *
     * @param elements the elements to be contained in the array
     * @return a frozen array containing the specified elements
     */
    public static int[] newArray(int[] elements) {
        int length = elements.length;
        int[] result = (int[])UNSAFE.makeLarvalArray(int[].class, length);
        System.arraycopy(elements, 0, result, 0, length);
        UNSAFE.freezeLarvalArray(result);
        return result;
    }

    /**
     * Returns a frozen array containing the given elements.
     *
     * @param elements the elements to be contained in the array
     * @return a frozen array containing the specified elements
     */
    public static byte[] newArray(byte[] elements) {
        int length = elements.length;
        byte[] result = (byte[])UNSAFE.makeLarvalArray(byte[].class, length);
        System.arraycopy(elements, 0, result, 0, length);
        UNSAFE.freezeLarvalArray(result);
        return result;
    }

    /**
     * Returns a frozen array containing the given elements.
     *
     * @param elements the elements to be contained in the array
     * @return a frozen array containing the specified elements
     */
    public static short[] newArray(short[] elements) {
        int length = elements.length;
        short[] result = (short[])UNSAFE.makeLarvalArray(short[].class, length);
        System.arraycopy(elements, 0, result, 0, length);
        UNSAFE.freezeLarvalArray(result);
        return result;
    }

    /**
     * Returns a frozen array containing the given elements.
     *
     * @param elements the elements to be contained in the array
     * @return a frozen array containing the specified elements
     */
    public static float[] newArray(float[] elements) {
        int length = elements.length;
        float[] result = (float[])UNSAFE.makeLarvalArray(float[].class, length);
        System.arraycopy(elements, 0, result, 0, length);
        UNSAFE.freezeLarvalArray(result);
        return result;
    }

    /**
     * Returns a frozen array containing the given elements.
     *
     * @param elements the elements to be contained in the array
     * @return a frozen array containing the specified elements
     */
    public static double[] newArray(double[] elements) {
        int length = elements.length;
        double[] result = (double[])UNSAFE.makeLarvalArray(double[].class, length);
        System.arraycopy(elements, 0, result, 0, length);
        UNSAFE.freezeLarvalArray(result);
        return result;
    }

    /**
     * Returns a frozen array containing the given elements.
     *
     * @param elements the elements to be contained in the array
     * @return a frozen array containing the specified elements
     */
    public static long[] newArray(long[] elements) {
        int length = elements.length;
        long[] result = (long[])UNSAFE.makeLarvalArray(long[].class, length);
        System.arraycopy(elements, 0, result, 0, length);
        UNSAFE.freezeLarvalArray(result);
        return result;
    }

    /**
     * Returns a frozen array containing the given elements.
     *
     * @param elements the elements to be contained in the array
     * @return a frozen array containing the specified elements
     */
    public static boolean[] newArray(boolean[] elements) {
        int length = elements.length;
        boolean[] result = (boolean[])UNSAFE.makeLarvalArray(boolean[].class, length);
        System.arraycopy(elements, 0, result, 0, length);
        UNSAFE.freezeLarvalArray(result);
        return result;
    }
}
