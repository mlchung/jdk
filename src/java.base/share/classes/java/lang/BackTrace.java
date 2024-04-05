/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package java.lang;

import java.lang.invoke.MethodType;
import java.lang.reflect.Modifier;

class BackTrace implements StackWalker.Backtrace {
    static final int CHUNK_SIZE = 32;

    private final short[] methods = new short[CHUNK_SIZE];
    private final int[] bcis = new int[CHUNK_SIZE];
    private final Class<?>[] classes = new Class<?>[CHUNK_SIZE];

    private boolean hasHiddenTopFrame;

    private BackTrace next;
    private int numFrames;  // filled by the VM

    // JVM injected fields
    // int[] or long[] names

    private final StackWalker walker;
    private Element[] elements;
    private StackTraceElement[] stackTraceElements;

    // invoked by the VM
    private static BackTrace newBackTrace() {
        return new BackTrace(null);
    }

    BackTrace(StackWalker walker) {
        this.walker = walker;
    }

    void ensureInitializedState() {
        if (walker == null || numFrames == -1)
            throw new IllegalStateException();
    }

    int chunkSize() {
        ensureInitializedState();
        return numFrames;
    }

    Class<?> classAt(int index) {
        return classes[index];
    }

    void setNext(BackTrace next) {
        this.next = next;
    }

    Element[] elements() {
        ensureInitializedState();

        Element[] elements = this.elements;
        if (elements == null) {
            synchronized (this) {
                elements = this.elements;
                if (elements == null) {
                    Element[] array = new Element[numFrames];
                    for (int i = 0; i < numFrames; i++) {
                        array[i] = new Element(walker, i, classes[i]);
                    }
                    this.elements = elements = array;
                }
            }
        }
        return elements;
    }

    private native void fillStackFrames(Class<?> cls, Object[] elements, int depth);

    class Element extends StackFrameInfo {
        final int index;
        Element(StackWalker walker, int index, Class<?> cls) {
            super(walker);
            this.index = index;
            this.classOrMemberName = cls;
        }

        Class<?> declaringClass() {
            return (Class<?>) this.classOrMemberName;
        }

        @Override
        public StackTraceElement toStackTraceElement() {
            initStackTraceElements();
            return stackTraceElements[index];
        }

        @Override
        public String getMethodName() {
            if (name == null) {
                initElements();
                assert name != null;
            }
            return name;
        }

        @Override
        public MethodType getMethodType() {
            ensureRetainClassRefEnabled();

            if (type == null) {
                initElements();
                assert type != null;
            }

            if (type instanceof MethodType mt) {
                return mt;
            }

            // type is not a MethodType yet.  Convert it thread-safely.
            synchronized (this) {
                if (type instanceof String sig) {
                    type = JLIA.getMethodType(sig, declaringClass().getClassLoader());
                }
            }
            return (MethodType)type;
        }

        @Override
        public boolean isNativeMethod() {
            // ensure initialized
            getMethodName();
            return Modifier.isNative(flags);
        }
    }

    private synchronized void initElements() {
        fillStackFrames(Element.class, elements, numFrames);
    }

    private void initStackTraceElements() {
        ensureInitializedState();

        StackTraceElement[] stes = stackTraceElements;
        if (stes == null) {
            synchronized (this) {
                stes = stackTraceElements;
                if (stes == null) {
                    StackTraceElement[] array = new StackTraceElement[CHUNK_SIZE];
                    for (int i = 0; i < numFrames; i++) {
                        array[i] = new StackTraceElement();
                    }
                    fillStackFrames(StackTraceElement.class, array, numFrames);
                    this.stackTraceElements = array;
                }
            }
        }
    }
}
