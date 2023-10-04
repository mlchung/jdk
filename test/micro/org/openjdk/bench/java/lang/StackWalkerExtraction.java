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
package org.openjdk.bench.java.lang;

import java.lang.StackWalker.StackFrame;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Benchmarks for java.lang.StackWalker
 */
@State(value=Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 3)
public class StackWalkerExtraction {
    // private static final StackWalker WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static Object call(int depth, Callable<?> result) throws Exception {
        if (depth == 0) {
            return result.call();
        } else {
            return call(depth - 1, result);
        }
    }

    @Param({"10", "30", "100"})
    int depth;

    @Benchmark
    public Object snapshot_StackFrame() throws Exception {
        StackWalker sw = StackWalker.getInstance(Set.of(StackWalker.Option.RETAIN_CLASS_REFERENCE));
        return call(100, () -> sw.walk(new RawWalker(depth, 0)));
    }

    @Benchmark
    public Object snapshot_BackTrace() throws Exception {
        StackWalker sw = StackWalker.getInstance(Set.of(StackWalker.Option.RETAIN_CLASS_REFERENCE), depth);
        return call(100, () -> sw.walk(new RawWalker(depth, 0)));
    }

    // @Benchmark
    public Object snapshot_toList() throws Exception {
        StackWalker sw = StackWalker.getInstance(Set.of(StackWalker.Option.RETAIN_CLASS_REFERENCE), depth);
        return call(100, () -> sw.toList(depth));
    }

    // @Benchmark
    public Object snapshot() throws Exception {
        StackWalker sw = StackWalker.getInstance(Set.of(StackWalker.Option.RETAIN_CLASS_REFERENCE), depth);
        return call(100, () -> sw.snapshot());
    }

    @Benchmark
    public Object snapshot_StackTraceElements() throws Exception {
        StackWalker sw = StackWalker.getInstance(Set.of(StackWalker.Option.RETAIN_CLASS_REFERENCE), depth);
        @SuppressWarnings("unchecked")
        List<StackFrame> frames = (List<StackFrame>)call(100, () -> sw.toList(depth));
        return frames.stream().map(StackFrame::toStackTraceElement).toList();
    }

    @Benchmark
    public Object snapshot_StackFrame_StackTraceElements() throws Exception {
        StackWalker sw = StackWalker.getInstance(Set.of(StackWalker.Option.RETAIN_CLASS_REFERENCE), depth);
        return call(100, () -> sw.walk(new TransformingWalker(depth, 0)));
    }

    @Benchmark
    public Object throwable_StackTraceElements() throws Exception {
        return call(100, () -> (new ThrowableWalker(depth, 0)).walk());
    }

    @Benchmark
    public Object throwable() throws Exception {
        return call(100, () -> new Throwable());
    }

    private class RawWalker implements Function<Stream<StackFrame>, StackFrame[]> {

        private final int stackDepth, startFrame;

        RawWalker(int stackDepth, int startFrame) {
            this.stackDepth = stackDepth;
            this.startFrame = startFrame;
        }

        @Override
        public StackFrame[] apply(Stream<StackFrame> stackFrameStream) {
            return stackFrameStream.skip(startFrame).limit(stackDepth).toArray(StackFrame[]::new);
        }
    }

    private class ThrowableWalker {

        private final int stackDepth, startFrame;

        ThrowableWalker(int stackDepth, int startFrame) {
            this.stackDepth = stackDepth;
            this.startFrame = startFrame;
        }

        public StackTraceElement[] walk() {
            Throwable x = new Throwable();
            StackTraceElement[] elements = new StackTraceElement[stackDepth];
            StackTraceElement[] stackTrace = x.getStackTrace();
            for (int i=0, j=startFrame; i < elements.length && j < stackTrace.length; i++, j++) {
                elements[i] = stackTrace[j];
            }
            return elements;
        }
    }


    private class TransformingWalker implements Function<Stream<StackFrame>, StackTraceElement[]> {

        private final int stackDepth, startFrame;

        TransformingWalker(int stackDepth, int startFrame) {
            this.stackDepth = stackDepth;
            this.startFrame = startFrame;
        }

        @Override
        public StackTraceElement[] apply(Stream<StackFrame> stackFrameStream) {
            return stackFrameStream.skip(startFrame).limit(stackDepth)
                    .map(StackFrame::toStackTraceElement).toArray(StackTraceElement[]::new);
        }
    }

    public static void main(String... args) throws Exception {
        System.console().readLine();
        StackWalkerExtraction swe = new StackWalkerExtraction();
        if (args[0].equals("snapshot")) {
            swe.snapshot_StackFrame();
        }
    }

}
