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

/*
 * @test
 * @summary Test ErrnoUtil
 * @modules java.base/jdk.internal.foreign
 * @run junit TestErrnoUtil
 */

import jdk.internal.foreign.ErrnoUtil;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;

import static org.junit.jupiter.api.Assertions.*;

final class TestErrnoUtil {

    private static final VarHandle ERRNO_HANDLE = Linker.Option.captureStateLayout()
                    .varHandle(MemoryLayout.PathElement.groupElement("errno"));

    private static final MethodHandle INT_DUMMY_HANDLE;
    private static final MethodHandle LONG_DUMMY_HANDLE;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            INT_DUMMY_HANDLE = lookup
                    .findStatic(TestErrnoUtil.class, "dummy",
                            MethodType.methodType(int.class, MemorySegment.class, int.class, int.class));
            LONG_DUMMY_HANDLE = lookup
                    .findStatic(TestErrnoUtil.class, "dummy",
                            MethodType.methodType(long.class, MemorySegment.class, long.class, int.class));
        } catch (ReflectiveOperationException e) {
            throw new InternalError(e);
        }
    }

    @Test
    void successfulInt() throws Throwable {
        MethodHandle adapted = ErrnoUtil.adaptSystemCall(INT_DUMMY_HANDLE);
        int r = (int) adapted.invoke(1, 0);
        assertEquals(1, r);
    }

    private static final int EACCES = 13; /* Permission denied */

    @Test
    void errorInt() throws Throwable {
        MethodHandle adapted = ErrnoUtil.adaptSystemCall(INT_DUMMY_HANDLE);

        int r = (int) adapted.invoke(-1, EACCES);
        assertEquals(-EACCES, r);
    }

    @Test
    void successfulLong() throws Throwable {
        MethodHandle adapted = ErrnoUtil.adaptSystemCall(LONG_DUMMY_HANDLE);
        long r = (long) adapted.invoke(1, 0);
        assertEquals(1, r);
    }

    @Test
    void errorLong() throws Throwable {
        MethodHandle adapted = ErrnoUtil.adaptSystemCall(LONG_DUMMY_HANDLE);

        long r = (long) adapted.invoke(-1, EACCES);
        assertEquals(-EACCES, r);
    }

    @Test
    void invariants() throws Throwable {
        assertThrows(NullPointerException.class, () -> ErrnoUtil.adaptSystemCall(null));

        MethodHandle noSegment = MethodHandles.lookup()
                .findStatic(TestErrnoUtil.class, "wrongType",
                        MethodType.methodType(long.class, long.class, int.class));

        var noSegEx = assertThrows(IllegalArgumentException.class, () -> ErrnoUtil.adaptSystemCall(noSegment));
        assertTrue(noSegEx.getMessage().contains("does not have a MemorySegment as the first parameter"));

        MethodHandle wrongReturnType = MethodHandles.lookup()
                .findStatic(TestErrnoUtil.class, "wrongType",
                        MethodType.methodType(short.class, MemorySegment.class, long.class, int.class));

        var wrongRetEx = assertThrows(IllegalArgumentException.class, () -> ErrnoUtil.adaptSystemCall(wrongReturnType));
        assertTrue(wrongRetEx.getMessage().contains("does not return an int or a long"));

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
