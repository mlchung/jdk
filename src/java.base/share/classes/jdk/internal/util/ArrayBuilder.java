package jdk.internal.util;

import jdk.internal.misc.Unsafe;

import java.util.Objects;

/**
 * Builder for constructing a frozen array
 * @param <E> element type
 */
public class ArrayBuilder<E> {
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    private final E[] array;
    private Thread owner;

    @SuppressWarnings("unchecked")
    public ArrayBuilder(Class<?> arrayClass, int length) {
        this.array = (E[])UNSAFE.makeLarvalArray(arrayClass, length);
    }

    public ArrayBuilder<E> set(int index, E element) {
        array[index] = element;
        return this;
    }

    public E get(int index) {
        return array[index];
    }

    public ArrayBuilder<E> copy(E[] original) {
        System.arraycopy(original, 0, array, 0, original.length);
        return this;
    }

    @SuppressWarnings("unchecked")
    public E[] build() {
        checkState();
        E[] a = (E[])Objects.requireNonNull(UNSAFE.freezeLarvalArray(array));
        close();
        return a;
    }

    private void checkState() {
        if (owner != Thread.currentThread())
            throw new IllegalStateException(owner == null ? "closed builder" : "wrong thread");
    }
    private void close() {
        owner = null;
    }
}
