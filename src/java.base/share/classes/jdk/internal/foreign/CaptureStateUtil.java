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
import jdk.internal.vm.annotation.ForceInline;

import java.lang.foreign.Arena;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class CaptureStateUtil {

    private static final StructLayout CAPTURE_LAYOUT = Linker.Option.captureStateLayout();
    private static final CarrierLocalArenaPools POOL = CarrierLocalArenaPools.create(CAPTURE_LAYOUT);

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

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

    private static final MethodHandle ACQUIRE_ARENA_MH =
            MhUtil.findStatic(LOOKUP, "acquireArena",
                    MethodType.methodType(Arena.class));

    private static final MethodHandle ALLOCATE_MH =
            MhUtil.findStatic(LOOKUP, "allocate",
                    MethodType.methodType(MemorySegment.class, Arena.class));

    private static final MethodHandle ARENA_CLOSE_MH =
            MhUtil.findVirtual(LOOKUP, Arena.class, "close",
                    MethodType.methodType(void.class));

    // The `BASIC_HANDLE_CACHE` contains the common "basic handles" that can be reused for
    // all adapted method handles. Keeping as much as possible reusable reduces the number
    // of combinators needed to form an adapted method handle.
    // The sub maps are lazily computed.
    //
    // The first-level key of the Map tells which return type the handle has
    // (`int` or `long`). The second-level key of the Map tells what is the name of
    // the captured state ("errno" (all platforms), "GetLastError", or "WSAGetLastError"
    // (the two latest only on Windows):
    //
    // (int.class | long.class) ->
    //   ({"GetLastError" | "WSAGetLastError"} | "errno") ->
    //     MethodHandle
    private static final Map<Class<?>, Map<String, MethodHandle>> BASIC_HANDLE_CACHE = Map.of(
            int.class, new ConcurrentHashMap<>(),
            long.class, new ConcurrentHashMap<>());

    private CaptureStateUtil() {}

    /**
     * {@return a new MethodHandle that adapts the provided {@code target} so that it
     * directly returns the same value as the {@code target} if it is non-negative,
     * otherwise returns the negated captured state defined by the provided
     * {@code stateName}}
     * <p>
     * This method is suitable for adapting system-call method handles(e.g.
     * {@code open()}, {@code read()}, and {@code close()}). Clients can check the return
     * value as shown in this example:
     * {@snippet lang = java:
     *       // (MemorySegment capture, MemorySegment pathname, int flags)int
     *       static final MethodHandle CAPTURING_OPEN = ...
     *
     *      // (MemorySegment pathname, int flags)int
     *      static final MethodHandle OPEN = CaptureStateUtil
     *             .adaptSystemCall(CAPTURING_OPEN, "errno");
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
     *} For a {@code target} method handle that takes a {@code MemorySegment} and two
     * {@code int} parameters and returns an {@code int} value, the method returns a new
     * method handle that is doing the equivalent of:
     * <p>
     * {@snippet lang = java:
     *         private static final MemoryLayout CAPTURE_LAYOUT =
     *                 Linker.Option.captureStateLayout();
     *         private static final CarrierLocalArenaPools POOL =
     *                 CarrierLocalArenaPools.create(CAPTURE_LAYOUT);
     *
     *         public int invoke(MethodHandle target,
     *                           String stateName,
     *                           int a, int b) {
     *             try (var arena = POOL.take()) {
     *                 final MemorySegment segment = arena.allocate(CAPTURE_LAYOUT);
     *                 final int result = (int) handle.invoke(segment, a, b);
     *                 if (result >= 0) {
     *                     return result;
     *                 }
     *                 return -(int) CAPTURE_LAYOUT
     *                     .varHandle(MemoryLayout.PathElement.groupElement(stateName))
     *                         .get(segment, 0);
     *             }
     *         }
     *}
     * except it is more performant. In the above {@code stateName} is the name of the
     * captured state (e.g. {@code errno}). The static {@code CAPTURE_LAYOUT} is shared
     * across all target method handles adapted by this method.
     *
     * @param target    method handle that returns an {@code int} or a {@code long} and
     *                  has a capturing state MemorySegment as its first parameter
     * @param stateName the name of the capturing state member layout (i.e. "errno",
     *                  "GetLastError", or "WSAGetLastError")
     * @throws IllegalArgumentException if the provided {@code target}'s return type is
     *                                  not {@code int} or {@code long}
     * @throws IllegalArgumentException if the provided {@code target}'s first parameter
     *                                  type is not {@linkplain MemorySegment}
     * @throws IllegalArgumentException if the provided {@code stateName} is unknown on
     *                                  the current platform
     */
    public static MethodHandle adaptSystemCall(MethodHandle target,
                                               String stateName) {
        // Implicit null check
        final Class<?> returnType = target.type().returnType();
        Objects.requireNonNull(stateName);

        if (!(returnType.equals(int.class) || returnType.equals(long.class))) {
            throw illegalArgDoesNot(target, "return an int or a long");
        }
        if (target.type().parameterCount() == 0 || target.type().parameterType(0) != MemorySegment.class) {
            throw illegalArgDoesNot(target, "have a MemorySegment as the first parameter");
        }

        // ((int | long), MemorySegment)(int | long)
        MethodHandle inner = BASIC_HANDLE_CACHE
                .get(returnType)
                .computeIfAbsent(stateName, new Function<>() {
                    @Override
                    public MethodHandle apply(String name) {
                        return basicHandleFor(returnType, name);
                    }
                });
        if (inner == null) {
            throw new IllegalArgumentException("Unknown state name: " + stateName +
                    ". Known on this platform: " + Linker.Option.captureStateLayout());
        }

        // Make `target` specific adaptations

        // Pre-pend all the parameters from the `target` MH.
        // (C0=MemorySegment, C1-Cn, MemorySegment)(int|long)
        inner = MethodHandles.collectArguments(inner, 0, target);

        int[] perm = new int[target.type().parameterCount() + 1];
        for (int i = 0; i < target.type().parameterCount(); i++) {
            perm[i] = i;
        }
        perm[target.type().parameterCount()] = 0;
        // Deduplicate the first and last coordinate and only use the first.
        // (C0=MemorySegment, C1-Cn)(int|long)
        inner = MethodHandles.permuteArguments(inner, target.type(), perm);

        // Use an `Arena` for the first argument instead and extract a segment from it.
        // (C0=Arena, C1-Cn)(int|long)
        inner = MethodHandles.collectArguments(inner, 0, ALLOCATE_MH);

        // Add an identity function for the result of the cleanup action.
        // ((int|long))(int|long)
        MethodHandle cleanup = MethodHandles.identity(returnType);
        // Add a dummy `Throwable` argument for the cleanup action.
        // This means, anything thrown will just be propagated.
        // (Throwable, (int|long))(int|long)
        cleanup = MethodHandles.dropArguments(cleanup, 0, Throwable.class);
        // Add the first parameter of the `inner` method handle to the cleanup
        // action and invoke `Arena::close` when it is run.
        // Cleanup does not have to have all parameters. It can have zero or more.
        // (Throwable, (int|long), Arena)(int|long)
        cleanup = MethodHandles.collectArguments(cleanup, 2, ARENA_CLOSE_MH);

        // Combine the `inner` and `cleanup` action in a try/finally block.
        // (Arena, C1-Cn)(int|long)
        MethodHandle tryFinally = MethodHandles.tryFinally(inner, cleanup);

        // Acquire the arena from the global pool.
        // Finally, we arrive at the intended method handle:
        // (C1-Cn)(int|long)
        return MethodHandles.collectArguments(tryFinally, 0, ACQUIRE_ARENA_MH);
    }

    private static MethodHandle basicHandleFor(Class<?> returnType,
                                               String capturedStateName) {
        final VarHandle vh = CAPTURE_LAYOUT.varHandle(
                MemoryLayout.PathElement.groupElement(capturedStateName));
        // This MH is used to extract the named captured state
        // from the capturing `MemorySegment`.
        // (MemorySegment, long)int
        MethodHandle intExtractor = vh.toMethodHandle(VarHandle.AccessMode.GET);
        // As the MH is already adapted to use the appropriate
        // offset, we just insert `0L` for the offset.
        // (MemorySegment)int
        intExtractor = MethodHandles.insertArguments(intExtractor, 1, 0L);

        // If X is the `returnType` (either `int` or `long`) then
        // the code below is equivalent to:
        //
        // X handle(X returnValue, MemorySegment segment)
        //     if (returnValue >= 0) {
        //         // Ignore the segment
        //         return returnValue;
        //     } else {
        //         // ignore the returnValue
        //         return -(X)intExtractor.invokeExact(segment);
        //     }
        // }
        if (returnType.equals(int.class)) {
            // (int, MemorySegment)int
            return MethodHandles.guardWithTest(
                    NON_NEGATIVE_INT_MH,
                    SUCCESS_INT_MH,
                    ERROR_INT_MH.bindTo(intExtractor));
        } else {
            // (long, MemorySegment)long
            return MethodHandles.guardWithTest(
                    NON_NEGATIVE_LONG_MH,
                    SUCCESS_LONG_MH,
                    ERROR_LONG_MH.bindTo(intExtractor));
        }
    }

    private static IllegalArgumentException illegalArgDoesNot(MethodHandle target, String info) {
        return new IllegalArgumentException("The provided target " + target
                + " does not " + info);
    }

    // The methods below are reflective used via static MethodHandles

    @ForceInline
    private static Arena acquireArena() {
        return POOL.take();
    }

    @ForceInline
    private static MemorySegment allocate(Arena arena) {
        // We do not need to zero out the segment.
        return ((NoInitSegmentAllocator) arena)
                .allocateNoInit(CAPTURE_LAYOUT.byteSize(), CAPTURE_LAYOUT.byteAlignment());
    }

    @ForceInline
    private static boolean nonNegative(int value) {
        return value >= 0;
    }

    @ForceInline
    private static int success(int value,
                               MemorySegment segment) {
        return value;
    }

    @ForceInline
    private static int error(MethodHandle errorHandle,
                             int value,
                             MemorySegment segment) throws Throwable {
        return -(int) errorHandle.invokeExact(segment);
    }

    @ForceInline
    private static boolean nonNegative(long value) {
        return value >= 0L;
    }

    @ForceInline
    private static long success(long value,
                                MemorySegment segment) {
        return value;
    }

    @ForceInline
    private static long error(MethodHandle errorHandle,
                              long value,
                              MemorySegment segment) throws Throwable {
        return -(int) errorHandle.invokeExact(segment);
    }

}
