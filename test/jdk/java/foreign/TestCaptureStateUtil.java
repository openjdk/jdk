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

/*
 * @test
 * @summary Test CaptureStateUtil
 * @modules java.base/jdk.internal.foreign
 * @run junit TestCaptureStateUtil
 */

import jdk.internal.foreign.CaptureStateUtil;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;

import static org.junit.jupiter.api.Assertions.*;

final class TestCaptureStateUtil {

    private static final String ERRNO_NAME = "errno";

    private static final VarHandle ERRNO_HANDLE = Linker.Option.captureStateLayout()
            .varHandle(MemoryLayout.PathElement.groupElement(ERRNO_NAME));

    private static final MethodHandle INT_DUMMY_HANDLE;
    private static final MethodHandle LONG_DUMMY_HANDLE;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            INT_DUMMY_HANDLE = lookup
                    .findStatic(TestCaptureStateUtil.class, "dummy",
                            MethodType.methodType(int.class, MemorySegment.class, int.class, int.class));
            LONG_DUMMY_HANDLE = lookup
                    .findStatic(TestCaptureStateUtil.class, "dummy",
                            MethodType.methodType(long.class, MemorySegment.class, long.class, int.class));
        } catch (ReflectiveOperationException e) {
            throw new InternalError(e);
        }
    }

    private static final MethodHandle ADAPTED_INT = CaptureStateUtil
            .adaptSystemCall(INT_DUMMY_HANDLE, ERRNO_NAME);
    private static final MethodHandle ADAPTED_LONG = CaptureStateUtil
            .adaptSystemCall(LONG_DUMMY_HANDLE, ERRNO_NAME);

    @Test
    void successfulInt() throws Throwable {
        int r = (int) ADAPTED_INT.invokeExact(1, 0);
        assertEquals(1, r);
    }

    private static final int EACCES = 13; /* Permission denied */

    @Test
    void errorInt() throws Throwable {
        int r = (int) ADAPTED_INT.invokeExact(-1, EACCES);
        assertEquals(-EACCES, r);
    }

    @Test
    void successfulLong() throws Throwable {
        long r = (long) ADAPTED_LONG.invokeExact(1L, 0);
        assertEquals(1, r);
    }

    @Test
    void errorLong() throws Throwable {
        long r = (long) ADAPTED_LONG.invokeExact(-1L, EACCES);
        assertEquals(-EACCES, r);
    }

    @Test
    void successfulIntPerHandle() throws Throwable {
        MethodHandle handle = CaptureStateUtil
                .adaptSystemCall(INT_DUMMY_HANDLE, ERRNO_NAME);
        int r = (int) handle.invokeExact(1, 0);
        assertEquals(1, r);
    }

    @Test
    void invariants() throws Throwable {
        MethodHandle noSegment = MethodHandles.lookup()
                .findStatic(TestCaptureStateUtil.class, "wrongType",
                        MethodType.methodType(long.class, long.class, int.class));

        var noSegEx = assertThrows(IllegalArgumentException.class, () -> CaptureStateUtil.adaptSystemCall(noSegment, ERRNO_NAME));
        assertTrue(noSegEx.getMessage().contains("does not have a MemorySegment as the first parameter"));

        MethodHandle noArgMH = MethodHandles.empty(MethodType.methodType(int.class));
        var emptyEx = assertThrows(IllegalArgumentException.class, () -> CaptureStateUtil.adaptSystemCall(noArgMH, ERRNO_NAME));
        assertTrue(emptyEx.getMessage().contains("does not have a MemorySegment as the first parameter"));

        MethodHandle wrongReturnType = MethodHandles.lookup()
                .findStatic(TestCaptureStateUtil.class, "wrongType",
                        MethodType.methodType(short.class, MemorySegment.class, long.class, int.class));

        var wrongRetEx = assertThrows(IllegalArgumentException.class, () -> CaptureStateUtil.adaptSystemCall(wrongReturnType, ERRNO_NAME));
        assertTrue(wrongRetEx.getMessage().contains("does not return an int or a long"));

        var wrongCaptureName = assertThrows(IllegalArgumentException.class, () -> CaptureStateUtil.adaptSystemCall(LONG_DUMMY_HANDLE, "foo"));
        assertEquals("Input not allowed: SegmentExtractorKey[returnType=long, stateName=foo]", wrongCaptureName.getMessage());

        assertThrows(NullPointerException.class, () -> CaptureStateUtil.adaptSystemCall(null, ERRNO_NAME));
        assertThrows(IllegalArgumentException.class, () -> CaptureStateUtil.adaptSystemCall(noSegment, null));
    }

    // Dummy method that is just returning the provided parameters
    private static int dummy(MemorySegment segment, int result, int errno) {
        ERRNO_HANDLE.set(segment, 0, errno);
        return result;
    }

    // Dummy method that is just returning the provided parameters
    private static long dummy(MemorySegment segment, long result, int errno) {
        ERRNO_HANDLE.set(segment, 0, errno);
        return result;
    }

    private static long wrongType(long result, int errno) {
        return 0;
    }

    private static short wrongType(MemorySegment segment, long result, int errno) {
        return 0;
    }

}
