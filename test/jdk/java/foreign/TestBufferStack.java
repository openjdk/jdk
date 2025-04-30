/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @modules java.base/jdk.internal.foreign
 * @build NativeTestHelper TestBufferStack
 * @run junit/othervm --enable-native-access=ALL-UNNAMED TestBufferStack
 */

import jdk.internal.foreign.BufferStack;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.MemoryLayout.structLayout;
import static java.lang.foreign.ValueLayout.*;
import static org.junit.jupiter.api.Assertions.*;

public class TestBufferStack extends NativeTestHelper {

    @Test
    public void testScopedAllocation() {
        int stackSize = 128;
        BufferStack stack = BufferStack.of(stackSize);
        MemorySegment stackSegment;
        try (Arena frame1 = stack.pushFrame(3 * JAVA_INT.byteSize(), JAVA_INT.byteAlignment())) {
            // Segments have expected sizes and are accessible and allocated consecutively in the same scope.
            MemorySegment segment11 = frame1.allocate(JAVA_INT);
            assertEquals(frame1.scope(), segment11.scope());
            assertEquals(JAVA_INT.byteSize(), segment11.byteSize());
            segment11.set(JAVA_INT, 0, 1);
            stackSegment = segment11.reinterpret(stackSize);

            MemorySegment segment12 = frame1.allocate(JAVA_INT);
            assertEquals(segment11.address() + JAVA_INT.byteSize(), segment12.address());
            assertEquals(JAVA_INT.byteSize(), segment12.byteSize());
            assertEquals(frame1.scope(), segment12.scope());
            segment12.set(JAVA_INT, 0, 1);

            MemorySegment segment2;
            try (Arena frame2 = stack.pushFrame(JAVA_LONG.byteSize(), JAVA_LONG.byteAlignment())) {
                assertNotEquals(frame1.scope(), frame2.scope());
                // same here, but a new scope.
                segment2 = frame2.allocate(JAVA_LONG);
                assertEquals( segment12.address() + /*segment12 size + frame 1 spare + alignment constraint*/ 3 * JAVA_INT.byteSize(), segment2.address());
                assertEquals(JAVA_LONG.byteSize(), segment2.byteSize());
                assertEquals(frame2.scope(), segment2.scope());
                segment2.set(JAVA_LONG, 0, 1);

                // Frames must be closed in stack order.
                assertThrows(IllegalStateException.class, frame1::close);
            }
            // Scope is closed here, inner segments throw.
            assertThrows(IllegalStateException.class, () -> segment2.get(JAVA_INT, 0));
            // A new stack frame allocates at the same location (but different scope) as the previous did.
            try (Arena frame3 = stack.pushFrame(2 * JAVA_INT.byteSize(), JAVA_INT.byteAlignment())) {
                MemorySegment segment3 = frame3.allocate(JAVA_INT);
                assertEquals(frame3.scope(), segment3.scope());
                assertEquals(segment12.address() + 2 * JAVA_INT.byteSize(), segment3.address());
            }

            // Fallback arena behaves like regular stack frame.
            MemorySegment outOfStack;
            try (Arena hugeFrame = stack.pushFrame(1024, 4)) {
                outOfStack = hugeFrame.allocate(4);
                assertEquals(hugeFrame.scope(), outOfStack.scope());
                assertTrue(outOfStack.asOverlappingSlice(stackSegment).isEmpty());
            }
            assertThrows(IllegalStateException.class, () -> outOfStack.get(JAVA_INT, 0));

            // Outer segments are still accessible.
            segment11.get(JAVA_INT, 0);
            segment12.get(JAVA_INT, 0);
        }
    }

    static {
        System.loadLibrary("TestBufferStack");
    }

    private static final MemoryLayout HVAPoint3D = structLayout(NativeTestHelper.C_DOUBLE, C_DOUBLE, C_DOUBLE);
    private static final MemorySegment UPCALL_MH = upcallStub(TestBufferStack.class, "recurse", FunctionDescriptor.of(HVAPoint3D, C_INT));
    private static final MethodHandle DOWNCALL_MH = downcallHandle("recurse", FunctionDescriptor.of(HVAPoint3D, C_INT, ADDRESS));

    public static MemorySegment recurse(int depth) {
        try {
            return (MemorySegment) DOWNCALL_MH.invokeExact((SegmentAllocator) Arena.ofAuto(), depth, UPCALL_MH);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testDeepStack() throws Throwable {
        // Each downcall and upcall require 48 bytes of stack.
        // After five allocations we start falling back.
        MemorySegment point = recurse(10);
        assertEquals( 12.0, point.getAtIndex(C_DOUBLE, 0));
        assertEquals(11.0, point.getAtIndex(C_DOUBLE, 1));
        assertEquals( 10.0, point.getAtIndex(C_DOUBLE, 2));
    }
}
