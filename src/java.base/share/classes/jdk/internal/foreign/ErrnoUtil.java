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
import java.lang.foreign.StructLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;

public final class ErrnoUtil {

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    private static final MethodHandles.Lookup ARGUS = MethodHandles.lookup();
    private static final TerminatingThreadLocal<MemorySegment> TL = new TerminatingThreadLocal<>(){
        @Override
        protected void threadTerminated(MemorySegment value) {
            free(value);
        }
    };

    private static final StructLayout CAPTURE_STATE_LAYOUT = Linker.Option.captureStateLayout();
        private static final VarHandle ERRNO_HANDLE =
            CAPTURE_STATE_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("errno"));

    private static final MethodHandle ACQUIRE_MH =
            MhUtil.findStatic(ARGUS, "acquireCaptureStateSegment",
                    MethodType.methodType(MemorySegment.class));
    private static final MethodHandle RETURN_FILTER_MH =
            MhUtil.findStatic(ARGUS, "returnFilter",
                    MethodType.methodType(int.class, int.class));

    private ErrnoUtil() {}

    /**
     * {@return a new MethodHandle that returns the returned int value if the
     *          returned value is non-negative, or else the negative errno}
     * <p>
     * As such, this method is suitable for adapting method handles for system calls
     * (e.g. {@code open()}, {@code read()}, and {@code close()}).
     *
     * @param target that returns an {@code int} and has an errno MemorySegment as
     *               the first parameter
     * @throws IllegalArgumentException if the provided {@code target}'s return type
     *         is not {@code int}
     * @throws IllegalArgumentException if the provided {@code target}'s first parameter
     *         type is not {@linkplain MemorySegment}
     */
    public static MethodHandle adaptSystemCall(MethodHandle target) {
        if (target.type().returnType() != int.class) {
            throw new IllegalArgumentException("The provided target " + target
                    + " does not return an int");
        }
        if (target.type().parameterType(0) != MemorySegment.class) {
            throw new IllegalArgumentException("The provided target " + target
                    + " does not have a MemorySegment as the first parameter");
        }
        // (MemorySegment, C*)int -> (C*)int
        target = MethodHandles.collectArguments(target, 0, ACQUIRE_MH);
        // (C*)int -> (C*)int
        target = MethodHandles.filterReturnValue(target, RETURN_FILTER_MH);
        return target;
    }

    // Used reflectively via ACQUIRE_MH
    private static MemorySegment acquireCaptureStateSegment() {
        MemorySegment segment = TL.get();
        if (segment == null) {
            TL.set(segment = malloc());
        }
        return segment;
    }

    // Used reflectively via RETURN_FILTER_MH
    private static int returnFilter(int result) {
        return result >= 0
                ? result
                : -(int) ERRNO_HANDLE.get(acquireCaptureStateSegment(), 0L);
    }

    @SuppressWarnings("restricted")
    private static MemorySegment malloc() {
        long address = UNSAFE.allocateMemory(CAPTURE_STATE_LAYOUT.byteSize());
        return MemorySegment.ofAddress(address).reinterpret(CAPTURE_STATE_LAYOUT.byteSize());
    }

    private static void free(MemorySegment segment) {
        UNSAFE.freeMemory(segment.address());
    }

}
