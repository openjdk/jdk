/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.foreign;

import jdk.internal.invoke.MhUtil;
import jdk.internal.misc.TerminatingThreadLocal;
import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

public final class CaptureStateUtil2 {

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final long SIZE = Linker.Option.captureStateLayout().byteSize();

    private static final TerminatingThreadLocal<SegmentStack> TL_STACK = new TerminatingThreadLocal<>() {
        @Override
        protected void threadTerminated(SegmentStack stack) {
            stack.close();
        }
    };

    private static final MethodHandle NON_NEGATIVE_INT_MH =
            MhUtil.findStatic(LOOKUP, "nonNegative",
                    MethodType.methodType(boolean.class, int.class));

    private static final MethodHandle SUCCESS_INT_MH =
            MhUtil.findStatic(LOOKUP, "success",
                    MethodType.methodType(int.class, int.class, MemorySegment.class));

    private static final MethodHandle ERROR_INT_MH =
            MhUtil.findStatic(LOOKUP, "error",
                    MethodType.methodType(int.class, MethodHandle.class, int.class, MemorySegment.class));

    private static final MethodHandle NON_NEGATIVE_LONG_MH =
            MhUtil.findStatic(LOOKUP, "nonNegative",
                    MethodType.methodType(boolean.class, long.class));

    private static final MethodHandle SUCCESS_LONG_MH =
            MhUtil.findStatic(LOOKUP, "success",
                    MethodType.methodType(long.class, long.class, MemorySegment.class));

    private static final MethodHandle ERROR_LONG_MH =
            MhUtil.findStatic(LOOKUP, "error",
                    MethodType.methodType(long.class, MethodHandle.class, long.class, MemorySegment.class));

    private static final MethodHandle ACQUIRE_STACK_MH =
            MhUtil.findStatic(LOOKUP, "acquireStack",
                    MethodType.methodType(SegmentStack.class));

    private static final MethodHandle ACQUIRE_SEGMENT_MH =
            MhUtil.findVirtual(LOOKUP, SegmentStack.class, "acquire",
                    MethodType.methodType(MemorySegment.class));

    private static final MethodHandle RELEASE_SEGMENT_MH =
            MhUtil.findVirtual(LOOKUP, SegmentStack.class, "release",
                    MethodType.methodType(void.class, MemorySegment.class));


    // (int.class | long.class) ->
    //   ({"GetLastError" | "WSAGetLastError"} | "errno") ->
    //     MethodHandle
    private static final Map<Class<?>, Map<String, MethodHandle>> CAPTURE_STATE_EXTRACTORS;

    static {

        final StructLayout stateLayout = Linker.Option.captureStateLayout();
        final Map<Class<?>, Map<String, MethodHandle>> classMap = new HashMap<>();
        for (var clazz : new Class<?>[]{int.class, long.class}) {
            Map<String, MethodHandle> handles = stateLayout
                    .memberLayouts().stream()
                    .collect(Collectors.toUnmodifiableMap(
                            member -> member.name().orElseThrow(),
                            member -> {
                                VarHandle vh = stateLayout.varHandle(MemoryLayout.PathElement.groupElement(member.name().orElseThrow()));
                                // (MemorySegment, long)int
                                MethodHandle mh = vh.toMethodHandle(VarHandle.AccessMode.GET);
                                // (MemorySegment)int
                                return MethodHandles.insertArguments(mh, 1, 0L);
                            }
                    ));
            classMap.put(clazz, handles);
        }
        CAPTURE_STATE_EXTRACTORS = Map.copyOf(classMap);
    }

    private CaptureStateUtil2() {
    }

    /**
     * {@return a new MethodHandle that adapts the provided {@code target} so that it
     *          directly returns the same value as the {@code target} if it is
     *          non-negative, otherwise returns the negated errno}
     * <p>
     * This method is suitable for adapting system-call method handles(e.g.
     * {@code open()}, {@code read()}, and {@code close()}). Clients can check the return
     * value as shown in this example:
     * {@snippet lang = java:
     *       // (MemorySegment capture, MemorySegment pathname, int flags)int
     *       static final MethodHandle CAPTURING_OPEN = ...
     *
     *      // (MemorySegment pathname, int flags)int
     *      static final MethodHandle OPEN = CaptureStateUtil.adaptSystemCall(CAPTURING_OPEN, "errno");
     *
     *      try {
     *         int fh = (int)OPEN.invoke(pathName, flags);
     *         if (fh < 0) {
     *             throw new IOException("Error opening file: errno = " + (-fh));
     *         }
     *         processFile(fh);
     *      } catch (Throwable t) {
     *           throw new RuntimeException(t);
     *      }
     *
     *}
     * For a method handle that takes a MemorySegment and two int parameters, the method
     * combinators are doing the equivalent of:
     *
     * {@snippet lang=java:
     *         public int invoke(int a, int b) {
     *             final SegmentStack segmentStack = acquireStack();
     *             final MemorySegment segment = segmentStack.acquire();
     *             try {
     *                 final int result = (int) handle.invoke(segment, a, b);
     *                 if (result >= 0) {
     *                     return result;
     *                 }
     *                 return -(int) errorHandle.get(segment);
     *             } finally {
     *                 segmentStack.release(segment);
     *             }
     *         }
     * }
     *
     *
     *
     * @param target    method handle that returns an {@code int} or a {@code long} and has
     *                  a capturing state MemorySegment as its first parameter
     * @param stateName the name of the capturing state member layout
     *                  (i.e. "errno","GetLastError", or "WSAGetLastError")
     * @throws IllegalArgumentException if the provided {@code target}'s return type is
     *                                  not {@code int} or {@code long}
     * @throws IllegalArgumentException if the provided {@code target}'s first parameter
     *                                  type is not {@linkplain MemorySegment}
     */
    public static MethodHandle adaptSystemCall(MethodHandle target, String stateName) {
        // Implicit null check
        final Class<?> returnType = target.type().returnType();
        Objects.requireNonNull(stateName);

        if (!(returnType.equals(int.class) || returnType.equals(long.class))) {
            throw illegalArgDoesNot(target, "return an int or a long");
        }
        if (target.type().parameterType(0) != MemorySegment.class) {
            throw illegalArgDoesNot(target, "have a MemorySegment as the first parameter");
        }

        // ((int | long), MemorySegment)(int | long)
        final MethodHandle captureStateExtractor = CAPTURE_STATE_EXTRACTORS
                .get(returnType)
                .get(stateName);
        if (captureStateExtractor == null) {
            throw new IllegalArgumentException("Unknown state name: " + stateName);
        }

        final boolean isInt = (returnType == int.class);

        // Todo: Cache the error handles
        // ((int|long), MemorySegment)(int|long)
        MethodHandle inner = MethodHandles.guardWithTest(
                isInt ? NON_NEGATIVE_INT_MH : NON_NEGATIVE_LONG_MH,
                isInt ? SUCCESS_INT_MH : SUCCESS_LONG_MH,
                (isInt ? ERROR_INT_MH : ERROR_LONG_MH).bindTo(captureStateExtractor));

        // (C0=MemorySegment, C1-Cn, MemorySegment)(int|long)
        inner = MethodHandles.collectArguments(inner, 0, target);

        int[] perm = new int[target.type().parameterCount() + 1];
        for (int i = 0; i < target.type().parameterCount(); i++) {
            perm[i] = i;
        }
        perm[target.type().parameterCount()] = 0;
        // Deduplicate the first and last coordinate and only use the first
        // (C0=MemorySegment, C1-Cn)(int|long)
        inner = MethodHandles.permuteArguments(inner, target.type(), perm);

        // (SegmentStack, C0=MemorySegment, C1-Cn)(int|long)
        inner = MethodHandles.dropArguments(inner, 0, SegmentStack.class);

        // ((int|long))(int|long)
        MethodHandle cleanup = MethodHandles.identity(returnType);
        // (Throwable, (int|long))(int|long)
        cleanup = MethodHandles.dropArguments(cleanup, 0, Throwable.class);
        // (Throwable, (int|long), SegmentStack, C0=MemorySegment)(int|long)
        cleanup = MethodHandles.collectArguments(cleanup, 2, RELEASE_SEGMENT_MH);
        // (Throwable, (int|long), SegmentStack, C0=MemorySegment, C1-Cn)(int|long)
        cleanup = MethodHandles.dropArguments(cleanup, 4, inner.type().parameterList().subList(2, inner.type().parameterCount()));
        //cleanup = MethodHandles.filterArguments()

        // (SegmentStack, C0=MemorySegment, C1-Cn)(int|long)
        MethodHandle tryFinally = MethodHandles.tryFinally(inner, cleanup);

        // (SegmentStack, SegmentStack, C1-Cn)(int|long)
        MethodHandle result = MethodHandles.filterArguments(tryFinally, 1, ACQUIRE_SEGMENT_MH);

        final MethodType newType = result.type().dropParameterTypes(0, 1);
        perm = new int[result.type().parameterCount()];
        perm[0] = 0;
        for (int i = 1; i < result.type().parameterCount(); i++) {
            perm[i] = i - 1;
        }
        // Deduplicate the first and second coordinate and only use the first
        // (SegmentStack, C1-Cn)(int|long)
        result = MethodHandles.permuteArguments(result, newType, perm);

        // Finally we arrive at (C1-Cn)(int|long)
        result = MethodHandles.collectArguments(result, 0, ACQUIRE_STACK_MH);

        return result;
    }

    // Used reflectively via ACQUIRE_MH
    @ForceInline
    private static SegmentStack acquireStack() {
        SegmentStack stack = TL_STACK.get();
        if (stack == null) {
            TL_STACK.set(stack = new SegmentStack());
        }
        return stack;
    }

    @SuppressWarnings("restricted")
    @ForceInline
    private static MemorySegment malloc() {
        final long address = UNSAFE.allocateMemory(SIZE);
        return MemorySegment.ofAddress(address).reinterpret(SIZE);
    }

    private static void free(MemorySegment segment) {
        UNSAFE.freeMemory(segment.address());
    }

    private static IllegalArgumentException illegalArgDoesNot(MethodHandle target, String info) {
        return new IllegalArgumentException("The provided target " + target
                + " does not " + info);
    }

    private static final class SegmentStack {

        @Stable
        private final Deque<MemorySegment> deque = new ConcurrentLinkedDeque<>();

        @ForceInline
        MemorySegment acquire() {
            final MemorySegment segment = deque.poll();
            return segment == null ? malloc() : segment;
        }

        void release(MemorySegment segment) {
            deque.push(segment);
        }

        void close(){
            for (MemorySegment segment:deque) {
                free(segment);
            }
        }
    }

    // Used reflectively
    @ForceInline
    private static boolean nonNegative(int value) {
        return value >= 0;
    }

    // Used reflectively
    @ForceInline
    private static int success(int value, MemorySegment segment) {
        return value;
    }

    // Used reflectively
    @ForceInline
    private static int error(MethodHandle errorHandle, int value, MemorySegment segment) throws Throwable {
        return -(int) errorHandle.invokeExact(segment);
    }

    // Used reflectively
    @ForceInline
    private static boolean nonNegative(long value) {
        return value >= 0L;
    }

    // Used reflectively
    @ForceInline
    private static long success(long value, MemorySegment segment) {
        return value;
    }

    // Used reflectively
    @ForceInline
    private static long error(MethodHandle errorHandle, long value, MemorySegment segment) throws Throwable {
        return -(int) errorHandle.invokeExact(segment);
    }

}
