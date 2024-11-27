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
import java.lang.foreign.StructLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;

import static org.junit.jupiter.api.Assertions.*;

final class TestErrnoUtil {

    private static final StructLayout CAPTURE_STATE_LAYOUT = Linker.Option.captureStateLayout();
    private static final VarHandle ERRNO_HANDLE =
            CAPTURE_STATE_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("errno"));

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final MethodHandle DUMMY_HANDLE;

    static {
        try {
            DUMMY_HANDLE = LOOKUP.findStatic(TestErrnoUtil.class, "dummy", MethodType.methodType(int.class, MemorySegment.class, int.class, int.class));
        } catch (ReflectiveOperationException e) {
            throw new InternalError(e);
        }
    }

    @Test
    void successful() throws Throwable {
        MethodHandle adapted = ErrnoUtil.adaptSystemCall(DUMMY_HANDLE);
        int r = (int) adapted.invoke(1, 0);
        assertEquals(1, r);
    }

    private static final int EACCES = 13; /* Permission denied */

    @Test
    void error() throws Throwable {
        MethodHandle adapted = ErrnoUtil.adaptSystemCall(DUMMY_HANDLE);

        int r = (int) adapted.invoke(-1, EACCES);
        assertEquals(-EACCES, r);
    }

    // Dummy method that is just using the provided parameters
    private static int dummy(MemorySegment segment, int result, int errno) {
        if (result < 0) {
            ERRNO_HANDLE.set(segment, 0, errno);
        }
        return result;
    }

}
