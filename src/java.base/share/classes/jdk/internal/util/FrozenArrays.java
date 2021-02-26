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
import jdk.internal.vm.annotation.IntrinsicCandidate;

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
     * Returns a frozen array containing the given elements.
     *
     * @param <T> the {@code array}'s element type
     * @param elements the elements to be contained in the array
     * @return a frozen array containing the specified elements
     */
    public static <T> T[] freeze(T[] elements) {
        Class<?> arrayClass = elements.getClass();
        int length = elements.length;
        @SuppressWarnings("unchecked")
        T[] result = (T[])UNSAFE.makeLarvalArray(arrayClass, length);
        System.arraycopy(elements, 0, result, 0, length);
        UNSAFE.freezeLarvalArray(result);
        return result;
    }

    /**
     * Copies the specified array, truncating or padding with nulls (if necessary)
     * so the copy has the specified length.  For all indices that are
     * valid in both the original array and the copy, the two arrays will
     * contain identical values.  For any indices that are valid in the
     * copy but not the original, the copy will contain {@code null}.
     * Such indices will exist if and only if the specified length
     * is greater than that of the original array.
     * The resulting array is a frozen array and
     * of exactly the same class as the original array.
     *
     * @param <T> the class of the objects in the array
     * @param original the array to be copied
     * @param newLength the length of the copy to be returned
     * @return a frozen copy of the original array, truncated or padded with nulls
     *     to obtain the specified length
     * @throws NegativeArraySizeException if {@code newLength} is negative
     * @throws NullPointerException if {@code original} is null
     */
    @SuppressWarnings("unchecked")
    public static <T> T[] freeze(T[] original, int newLength) {
        return freeze(original, newLength, (Class<? extends T[]>)original.getClass());
    }

    /**
     * Copies the specified array, truncating or padding with nulls (if necessary)
     * so the copy has the specified length.  For all indices that are
     * valid in both the original array and the copy, the two arrays will
     * contain identical values.  For any indices that are valid in the
     * copy but not the original, the copy will contain {@code null}.
     * Such indices will exist if and only if the specified length
     * is greater than that of the original array.
     * The resulting array is of the class {@code newType}.
     *
     * @param <U> the class of the objects in the original array
     * @param <T> the class of the objects in the returned array
     * @param original the array to be copied
     * @param newLength the length of the copy to be returned
     * @param newType the class of the copy to be returned
     * @return a copy of the original array, truncated or padded with nulls
     *     to obtain the specified length
     * @throws NegativeArraySizeException if {@code newLength} is negative
     * @throws NullPointerException if {@code original} is null
     * @throws ArrayStoreException if an element copied from
     *     {@code original} is not of a runtime type that can be stored in
     *     an array of class {@code newType}
     */
    @SuppressWarnings("unchecked")
    public static <T,U> T[] freeze(U[] original, int newLength, Class<? extends T[]> newType) {
        Objects.requireNonNull(original);
        Class<?> arrayClass = original.getClass();
        if (!arrayClass.isArray()) {
            throw new IllegalArgumentException("must be an array");
        }

        if (isFrozen(original) && newLength == original.length && arrayClass == newType) {
            return (T[]) original;
        }

        Object copy = UNSAFE.makeLarvalArray(newType, newLength);
        int numElements = original.length <= newLength ? original.length : newLength;
        System.arraycopy(original, 0, copy, 0, numElements);
        T[] result = (T[])UNSAFE.freezeLarvalArray(copy);
        return result;
    }

    /**
     * Copies the specified range of the specified array into a new array.
     * The initial index of the range ({@code from}) must lie between zero
     * and {@code original.length}, inclusive.  The value at
     * {@code original[from]} is placed into the initial element of the copy
     * (unless {@code from == original.length} or {@code from == to}).
     * Values from subsequent elements in the original array are placed into
     * subsequent elements in the copy.  The final index of the range
     * ({@code to}), which must be greater than or equal to {@code from},
     * may be greater than {@code original.length}, in which case
     * {@code null} is placed in all elements of the copy whose index is
     * greater than or equal to {@code original.length - from}.  The length
     * of the returned array will be {@code to - from}.
     * <p>
     * The resulting array is of exactly the same class as the original array.
     *
     * @param <T> the class of the objects in the array
     * @param original the array from which a range is to be copied
     * @param from the initial index of the range to be copied, inclusive
     * @param to the final index of the range to be copied, exclusive.
     *     (This index may lie outside the array.)
     * @return a new array containing the specified range from the original array,
     *     truncated or padded with nulls to obtain the required length
     * @throws ArrayIndexOutOfBoundsException if {@code from < 0}
     *     or {@code from > original.length}
     * @throws IllegalArgumentException if {@code from > to}
     * @throws NullPointerException if {@code original} is null
     */
    @SuppressWarnings("unchecked")
    public static <T> T[] freezeRange(T[] original, int from, int to) {
        return freezeRange(original, from, to, (Class<? extends T[]>) original.getClass());
    }

    /**
     * Copies the specified range of the specified array into a new array.
     * The initial index of the range ({@code from}) must lie between zero
     * and {@code original.length}, inclusive.  The value at
     * {@code original[from]} is placed into the initial element of the copy
     * (unless {@code from == original.length} or {@code from == to}).
     * Values from subsequent elements in the original array are placed into
     * subsequent elements in the copy.  The final index of the range
     * ({@code to}), which must be greater than or equal to {@code from},
     * may be greater than {@code original.length}, in which case
     * {@code null} is placed in all elements of the copy whose index is
     * greater than or equal to {@code original.length - from}.  The length
     * of the returned array will be {@code to - from}.
     * The resulting array is of the class {@code newType}.
     *
     * @param <U> the class of the objects in the original array
     * @param <T> the class of the objects in the returned array
     * @param original the array from which a range is to be copied
     * @param from the initial index of the range to be copied, inclusive
     * @param to the final index of the range to be copied, exclusive.
     *     (This index may lie outside the array.)
     * @param newType the class of the copy to be returned
     * @return a new array containing the specified range from the original array,
     *     truncated or padded with nulls to obtain the required length
     * @throws ArrayIndexOutOfBoundsException if {@code from < 0}
     *     or {@code from > original.length}
     * @throws IllegalArgumentException if {@code from > to}
     * @throws NullPointerException if {@code original} is null
     * @throws ArrayStoreException if an element copied from
     *     {@code original} is not of a runtime type that can be stored in
     *     an array of class {@code newType}.
     */
    public static <T,U> T[] freezeRange(U[] original, int from, int to, Class<? extends T[]> newType) {
        Objects.requireNonNull(original);
        if (!original.getClass().isArray()) {
            throw new IllegalArgumentException("must be an array");
        }

        int newLength = to - from;
        if (newLength < 0)
            throw new IllegalArgumentException(from + " > " + to);

        Object copy = UNSAFE.makeLarvalArray(newType, newLength);
        System.arraycopy(original, from, copy, 0,
                         Math.min(original.length - from, newLength));
        @SuppressWarnings("unchecked")
        T[] result = (T[])UNSAFE.freezeLarvalArray(copy);
        return result;
    }

    /**
     * Returns a frozen array containing the given elements.
     *
     * @param elements the elements to be contained in the array
     * @return a frozen array containing the specified elements
     */
    public static int[] freeze(int[] elements) {
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
    public static byte[] freeze(byte[] elements) {
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
    public static short[] freeze(short[] elements) {
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
    public static float[] freeze(float[] elements) {
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
    public static double[] freeze(double[] elements) {
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
    public static long[] freeze(long[] elements) {
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
    public static boolean[] freeze(boolean[] elements) {
        int length = elements.length;
        boolean[] result = (boolean[])UNSAFE.makeLarvalArray(boolean[].class, length);
        System.arraycopy(elements, 0, result, 0, length);
        UNSAFE.freezeLarvalArray(result);
        return result;
    }
}
