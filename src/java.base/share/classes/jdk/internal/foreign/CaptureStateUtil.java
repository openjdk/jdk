/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class CaptureStateUtil {

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    private static final TerminatingThreadLocal<MemorySegment> TL = new TerminatingThreadLocal<>() {
        @Override
        protected void threadTerminated(MemorySegment value) {
            free(value);
        }
    };

    private static final MethodHandle ACQUIRE_MH =
            MhUtil.findStatic(LOOKUP, "acquireCaptureStateSegment",
                    MethodType.methodType(MemorySegment.class));

    private static final MethodHandle INT_RETURN_FILTER_MH =
            MhUtil.findStatic(LOOKUP, "returnFilter",
                    MethodType.methodType(int.class, MethodHandle.class, int.class));

    private static final MethodHandle LONG_RETURN_FILTER_MH =
            MhUtil.findStatic(LOOKUP, "returnFilter",
                    MethodType.methodType(long.class, MethodHandle.class, long.class));

    // (int.class | long.class) ->
    //   ({"GetLastError" | "WSAGetLastError"} | "errno") ->
    //     MethodHandle
    private static final Map<Class<?>, Map<String, MethodHandle>> RETURN_FILTERS;

    static {
        Map<Class<?>, Map<String, MethodHandle>> classMap = new HashMap<>();
        for (var clazz : new Class<?>[]{int.class, long.class}) {
            Map<String, MethodHandle> handles = Linker.Option.captureStateLayout()
                    .memberLayouts().stream()
                    .collect(Collectors.toUnmodifiableMap(
                            l -> l.name().orElseThrow(),
                            l -> {
                                MethodHandle mh = getAsIntHandle(l);
                                MethodHandle returnFilter = clazz.equals(int.class)
                                        ? INT_RETURN_FILTER_MH
                                        : LONG_RETURN_FILTER_MH;
                                // (int)int
                                return returnFilter.bindTo(mh);
                            }
                    ));
            classMap.put(clazz, handles);
        }
        RETURN_FILTERS = Map.copyOf(classMap);
    }

    private CaptureStateUtil() {
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
     *      static final MethodHandle OPEN = CaptureStateUtil.adaptSystemCall(CAPTURING_OPEN);
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
     *
     * @param target method handle that returns an {@code int} or a {@code long} and has
     *              an errno MemorySegment as its first parameter
     * @throws IllegalArgumentException if the provided {@code target}'s return type is
     *                                  not {@code int} or {@code long}
     * @throws IllegalArgumentException if the provided {@code target}'s first parameter
     *                                  type is not {@linkplain MemorySegment}
     */
    public static MethodHandle adaptSystemCall(MethodHandle target, String stateName) {
        Objects.requireNonNull(stateName);
        // Implicit null check
        final Class<?> returnType = target.type().returnType();
        if (!(returnType.equals(int.class) || returnType.equals(long.class))) {
            throw illegalArgNot(target, "return an int or a long");
        }
        if (target.type().parameterType(0) != MemorySegment.class) {
            throw illegalArgNot(target, "have a MemorySegment as the first parameter");
        }

        // (MemorySegment, C*)(int | long) -> (C*)(int | long)
        target = MethodHandles.collectArguments(target, 0, ACQUIRE_MH);

        // (int | long)(int | long)
        MethodHandle returnFilter = Objects.requireNonNull(RETURN_FILTERS
                .get(returnType)
                .get(stateName), "no such state name: " + stateName);

        // (C*)(int | long) -> (C*)(int | long)
        return MethodHandles.filterReturnValue(target, returnFilter);
    }

    // Used reflectively via ACQUIRE_MH
    private static MemorySegment acquireCaptureStateSegment() {
        MemorySegment segment = TL.get();
        if (segment == null) {
            TL.set(segment = malloc());
        }
        return segment;
    }

    // Used reflectively via INT_RETURN_FILTER_MH
    private static int returnFilter(MethodHandle errorHandle, int result) {
        if (result >= 0) {
            return result;
        }
        try {
            return -(int) errorHandle.invoke();
        } catch (Throwable t) {
            throw new InternalError(t);
        }
    }

    // Used reflectively via LONG_RETURN_FILTER_MH
    private static long returnFilter(MethodHandle errorHandle, long result) {
        if (result >= 0) {
            return result;
        }
        try {
            return -(int) errorHandle.invoke();
        } catch (Throwable t) {
            throw new InternalError(t);
        }
    }

    @SuppressWarnings("restricted")
    private static MemorySegment malloc() {
        final long size = Linker.Option.captureStateLayout().byteSize();
        final long address = UNSAFE.allocateMemory(size);
        return MemorySegment.ofAddress(address).reinterpret(size);
    }

    private static void free(MemorySegment segment) {
        UNSAFE.freeMemory(segment.address());
    }

    private static IllegalArgumentException illegalArgNot(MethodHandle target, String info) {
        return new IllegalArgumentException("The provided target " + target
                + " does not " + info);
    }

    private static MethodHandle getAsIntHandle(MemoryLayout layout) {
        MethodHandle handle = MhUtil.findStatic(LOOKUP, "getStateAsInt", MethodType.methodType(int.class, VarHandle.class));
        return handle.bindTo(layout.varHandle());
    }

    // Used reflectively by `getAsIntHandle(MemoryLayout layout)`
    private static int getStateAsInt(VarHandle handle) {
        return (int) handle.get(acquireCaptureStateSegment(), 0);
    }

}
