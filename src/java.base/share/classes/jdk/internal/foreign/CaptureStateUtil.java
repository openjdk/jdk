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
import jdk.internal.lang.stable.InternalStableFactories;
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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * An internal utility class that can be used to adapt system-call-styled method handles
 * for efficient and easy use.
 */
public final class CaptureStateUtil {

    private static final StructLayout CAPTURE_LAYOUT = Linker.Option.captureStateLayout();
    private static final BufferStack POOL = BufferStack.of(CAPTURE_LAYOUT);

    // Do not use a lambda in order to allow early use in the init sequence
    private static final Function<BasicKey, MethodHandle> UNDERLYING_MAKE_BASIC_HANDLE = new Function<>() {
        @Override
        public MethodHandle apply(BasicKey basicKey) {
            return makeBasicHandle(basicKey);
        }
    };

    // The `BASIC_HANDLE_CACHE` contains the common "basic handles" that can be reused for
    // all adapted method handles. Keeping as much as possible reusable reduces the number
    // of combinators needed to form an adapted method handle.
    // The function is lazily computed.
    //
    private static final Function<BasicKey, MethodHandle> BASIC_HANDLE_CACHE;

    static {
        final Set<BasicKey> inputs = new HashSet<>();
        // The Cartesian product : (int.class, long.class) x ("errno", ...)
        // Do not use Streams in order to enable "early" use in the init sequence.
        for (Class<?> c : new Class<?>[]{int.class, long.class}) {
            for (MemoryLayout layout : CAPTURE_LAYOUT.memberLayouts()) {
                inputs.add(new BasicKey(c, layout.name().orElseThrow()));
            }
        }
        BASIC_HANDLE_CACHE = InternalStableFactories.function(inputs, UNDERLYING_MAKE_BASIC_HANDLE);
    }

    // A key that holds both the `returnType` and the `stateName` needed to look up a
    // specific "basic handle" in the `BASIC_HANDLE_CACHE`.
    //   returnType in {int.class | long.class}
    //   stateName can be anything non-null but should be in {"GetLastError" | "WSAGetLastError" | "errno"}
    private record BasicKey(Class<?> returnType, String stateName) {

        BasicKey(MethodHandle target, String stateName) {
            this(returnType(target), Objects.requireNonNull(stateName));
        }

        static Class<?> returnType(MethodHandle target) {
            // Implicit null check
            final MethodType type = target.type();
            final Class<?> returnType = type.returnType();

            if (!(returnType.equals(int.class) || returnType.equals(long.class))) {
                throw illegalArgDoesNot(target, "return an int or a long");
            }
            if (type.parameterCount() == 0 || type.parameterType(0) != MemorySegment.class) {
                throw illegalArgDoesNot(target, "have a MemorySegment as the first parameter");
            }
            return returnType;
        }

        private static IllegalArgumentException illegalArgDoesNot(MethodHandle target, String info) {
            return new IllegalArgumentException("The provided target " + target
                    + " does not " + info);
        }

        @Override
        public int hashCode() {
            return returnType.hashCode() ^ stateName.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            // Don't use a record pattern to save on startup time
            return obj instanceof BasicKey that &&
                    returnType.equals(that.returnType) &&
                    stateName.equals(that.stateName);
        }
    }

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
     *         int fh = (int)OPEN.invokeExact(pathName, flags);
     *         if (fh < 0) {
     *             throw new IOException("Error opening file: errno = " + (-fh));
     *         }
     *         processFile(fh);
     *      } catch (Throwable t) {
     *           throw new RuntimeException(t);
     *      }
     *
     *}
     *
     * For a {@code target} method handle that takes a {@code MemorySegment} and two
     * {@code int} parameters and returns an {@code int} value, the method returns a new
     * method handle that is doing the equivalent of:
     * <p>
     * {@snippet lang = java:
     *         private static final MemoryLayout CAPTURE_LAYOUT =
     *                 Linker.Option.captureStateLayout();
     *         private static final BufferStack POOL =
     *                 BufferStack.of(CAPTURE_LAYOUT);
     *
     *         public int invoke(MethodHandle target,
     *                           String stateName,
     *                           int a, int b) {
     *             try (var arena = POOL.pushFrame(CAPTURE_LAYOUT)) {
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
        // Invariants checked in the BasicKey record
        final BasicKey basicKey = new BasicKey(target, stateName);

        // ((int | long), MemorySegment)(int | long)
        final MethodHandle basicHandle = BASIC_HANDLE_CACHE.apply(basicKey);

        // Make `target` specific adaptations of the basic handle

        // Pre-pend all the parameters from the `target` MH.
        // (C0=MemorySegment, C1-Cn, MemorySegment)(int|long)
        MethodHandle innerAdapted = MethodHandles.collectArguments(basicHandle, 0, target);

        final int[] perm = new int[target.type().parameterCount() + 1];
        for (int i = 0; i < target.type().parameterCount(); i++) {
            perm[i] = i;
        }
        // Last takes first
        perm[target.type().parameterCount()] = 0;
        // Deduplicate the first and last coordinate and only use the first one.
        // (C0=MemorySegment, C1-Cn)(int|long)
        innerAdapted = MethodHandles.permuteArguments(innerAdapted, target.type(), perm);

        // Use an `Arena` for the first argument instead and extract a segment from it.
        // (C0=Arena, C1-Cn)(int|long)
        innerAdapted = MethodHandles.collectArguments(innerAdapted, 0, HANDLES_CACHE.apply(ALLOCATE));

        // Add an identity function for the result of the cleanup action.
        // ((int|long))(int|long)
        MethodHandle cleanup = MethodHandles.identity(basicKey.returnType());
        // Add a dummy `Throwable` argument for the cleanup action.
        // This means, anything thrown will just be propagated.
        // (Throwable, (int|long))(int|long)
        cleanup = MethodHandles.dropArguments(cleanup, 0, Throwable.class);
        // Add the first `Arena` parameter of the `innerAdapted` method handle to the
        // cleanup action and invoke `Arena::close` when it is run. The `cleanup` handle
        // does not have to have all parameters. It can have zero or more.
        // (Throwable, (int|long), Arena)(int|long)
        cleanup = MethodHandles.collectArguments(cleanup, 2, HANDLES_CACHE.apply(ARENA_CLOSE));

        // Combine the `innerAdapted` and `cleanup` action into a try/finally block.
        // (Arena, C1-Cn)(int|long)
        final MethodHandle tryFinally = MethodHandles.tryFinally(innerAdapted, cleanup);

        // Acquire the arena from the global pool.
        // With this, we finally arrive at the intended method handle:
        // (C1-Cn)(int|long)
        return MethodHandles.collectArguments(tryFinally, 0, HANDLES_CACHE.apply(ACQUIRE_ARENA));
    }

    private static MethodHandle makeBasicHandle(BasicKey basicKey) {
        final VarHandle vh = CAPTURE_LAYOUT.varHandle(
                MemoryLayout.PathElement.groupElement(basicKey.stateName()));
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
        if (basicKey.returnType().equals(int.class)) {
            // (int, MemorySegment)int
            return MethodHandles.guardWithTest(
                    HANDLES_CACHE.apply(NON_NEGATIVE_INT),
                    HANDLES_CACHE.apply(SUCCESS_INT),
                    HANDLES_CACHE.apply(ERROR_INT).bindTo(intExtractor));
        } else {
            // (long, MemorySegment)long
            return MethodHandles.guardWithTest(
                    HANDLES_CACHE.apply(NON_NEGATIVE_LONG),
                    HANDLES_CACHE.apply(SUCCESS_LONG),
                    HANDLES_CACHE.apply(ERROR_LONG).bindTo(intExtractor));
        }
    }

    // The methods below are reflective used via static MethodHandles

    @ForceInline
    private static Arena acquireArena() {
        return POOL.pushFrame(CAPTURE_LAYOUT);
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

    // The method handles below are bound to static methods residing in this class

    private static final int
            NON_NEGATIVE_INT  = 0,
            SUCCESS_INT       = 1,
            ERROR_INT         = 2,
            NON_NEGATIVE_LONG = 3,
            SUCCESS_LONG      = 4,
            ERROR_LONG        = 5,
            ACQUIRE_ARENA     = 6,
            ALLOCATE          = 7,
            ARENA_CLOSE       = 8;

    // Do not use a lambda in order to allow early use in the init sequence
    private static final IntFunction<MethodHandle> UNDERLYING_MAKE_HANDLE = new IntFunction<MethodHandle>() {
        @Override
        public MethodHandle apply(int value) {
            return makeHandle(value);
        }
    };

    private static final IntFunction<MethodHandle> HANDLES_CACHE =
            InternalStableFactories.intFunction(ARENA_CLOSE + 1, UNDERLYING_MAKE_HANDLE);

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    // This is intentionally an old switch to improve on startup time.
    private static MethodHandle makeHandle(int index) {
        switch (index) {
            case NON_NEGATIVE_INT:
                return MhUtil.findStatic(LOOKUP, "nonNegative",
                        MethodType.methodType(boolean.class, int.class));
            case SUCCESS_INT:
                return MhUtil.findStatic(LOOKUP, "success",
                        MethodType.methodType(int.class, int.class, MemorySegment.class));
            case ERROR_INT:
                return MhUtil.findStatic(LOOKUP, "error",
                        MethodType.methodType(int.class, MethodHandle.class, int.class, MemorySegment.class));
            case NON_NEGATIVE_LONG:
                return MhUtil.findStatic(LOOKUP, "nonNegative",
                        MethodType.methodType(boolean.class, long.class));
            case SUCCESS_LONG:
                return MhUtil.findStatic(LOOKUP, "success",
                        MethodType.methodType(long.class, long.class, MemorySegment.class));
            case ERROR_LONG:
                return MhUtil.findStatic(LOOKUP, "error",
                        MethodType.methodType(long.class, MethodHandle.class, long.class, MemorySegment.class));
            case ACQUIRE_ARENA:
                return MhUtil.findStatic(LOOKUP, "acquireArena",
                        MethodType.methodType(Arena.class));
            case ALLOCATE:
                return MhUtil.findStatic(LOOKUP, "allocate",
                        MethodType.methodType(MemorySegment.class, Arena.class));
            case ARENA_CLOSE:
                return MhUtil.findVirtual(LOOKUP, Arena.class, "close",
                        MethodType.methodType(void.class));
        }
        throw new InternalError("Unknown index: " + index);
    }

}
