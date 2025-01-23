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
 * @modules java.base/jdk.internal.foreign.abi
 * @build NativeTestHelper TestBufferStack
 * @run testng/othervm --enable-native-access=ALL-UNNAMED TestBufferStack
 */

import jdk.internal.foreign.abi.BufferStack;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.invoke.MethodHandle;
import java.time.Duration;
import java.util.Arrays;
import java.util.stream.IntStream;

import static java.lang.foreign.MemoryLayout.structLayout;
import static java.lang.foreign.ValueLayout.*;
import static java.time.temporal.ChronoUnit.SECONDS;

public class TestBufferStack extends NativeTestHelper {
    @Test
    public void testScopedAllocation() {
        int stackSize = 128;
        BufferStack stack = new BufferStack(stackSize);
        MemorySegment stackSegment;
        try (Arena frame1 = stack.pushFrame(3 * JAVA_INT.byteSize(), JAVA_INT.byteAlignment())) {
            // Segments have expected sizes and are accessible and allocated consecutively in the same scope.
            MemorySegment segment11 = frame1.allocate(JAVA_INT);
            Assert.assertEquals(segment11.scope(), frame1.scope());
            Assert.assertEquals(segment11.byteSize(), JAVA_INT.byteSize());
            segment11.set(JAVA_INT, 0, 1);
            stackSegment = segment11.reinterpret(stackSize);

            MemorySegment segment12 = frame1.allocate(JAVA_INT);
            Assert.assertEquals(segment12.address(), segment11.address() + JAVA_INT.byteSize());
            Assert.assertEquals(segment12.byteSize(), JAVA_INT.byteSize());
            Assert.assertEquals(segment12.scope(), frame1.scope());
            segment12.set(JAVA_INT, 0, 1);

            MemorySegment segment2;
            try (Arena frame2 = stack.pushFrame(JAVA_LONG.byteSize(), JAVA_LONG.byteAlignment())) {
                Assert.assertNotEquals(frame2.scope(), frame1.scope());
                // same here, but a new scope.
                segment2 = frame2.allocate(JAVA_LONG);
                Assert.assertEquals(segment2.address(), segment12.address() + /*segment12 size + frame 1 spare + alignment constraint*/ 3 * JAVA_INT.byteSize());
                Assert.assertEquals(segment2.byteSize(), JAVA_LONG.byteSize());
                Assert.assertEquals(segment2.scope(), frame2.scope());
                segment2.set(JAVA_LONG, 0, 1);

                // Frames must be closed in stack order.
                Assert.assertThrows(IllegalStateException.class, frame1::close);
            }
            // Scope is closed here, inner segments throw.
            Assert.assertThrows(IllegalStateException.class, () -> segment2.get(JAVA_INT, 0));
            // A new stack frame allocates at the same location (but different scope) as the previous did.
            try (Arena frame3 = stack.pushFrame(2 * JAVA_INT.byteSize(), JAVA_INT.byteAlignment())) {
                MemorySegment segment3 = frame3.allocate(JAVA_INT);
                Assert.assertEquals(segment3.scope(), frame3.scope());
                Assert.assertEquals(segment3.address(), segment12.address() + 2 * JAVA_INT.byteSize());
            }

            // Fallback arena behaves like regular stack frame.
            MemorySegment outOfStack;
            try (Arena hugeFrame = stack.pushFrame(1024, 4)) {
                outOfStack = hugeFrame.allocate(4);
                Assert.assertEquals(outOfStack.scope(), hugeFrame.scope());
                Assert.assertTrue(outOfStack.asOverlappingSlice(stackSegment).isEmpty());
            }
            Assert.assertThrows(IllegalStateException.class, () -> outOfStack.get(JAVA_INT, 0));

            // Outer segments are still accessible.
            segment11.get(JAVA_INT, 0);
            segment12.get(JAVA_INT, 0);
        }
    }

    @Test
    public void stress() throws InterruptedException {
        BufferStack stack = new BufferStack(256);
        Thread[] vThreads = IntStream.range(0, 1024).mapToObj(_ ->
                Thread.ofVirtual().start(() -> {
                    long threadId = Thread.currentThread().threadId();
                    while (!Thread.interrupted()) {
                        for (int i = 0; i < 1_000_000; i++) {
                            try (Arena arena = stack.pushFrame(JAVA_LONG.byteSize(), JAVA_LONG.byteAlignment())) {
                                // Try to assert no two vThreads get allocated the same stack space.
                                MemorySegment segment = arena.allocate(JAVA_LONG);
                                JAVA_LONG.varHandle().setVolatile(segment, 0L, threadId);
                                Assert.assertEquals(threadId, (long) JAVA_LONG.varHandle().getVolatile(segment, 0L));
                            }
                        }
                        Thread.yield(); // make sure the driver thread gets a chance.
                    }
                })).toArray(Thread[]::new);
        Thread.sleep(Duration.of(10, SECONDS));
        Arrays.stream(vThreads).forEach(
                thread -> {
                    Assert.assertTrue(thread.isAlive());
                    thread.interrupt();
                });
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
        Assert.assertEquals(point.getAtIndex(C_DOUBLE, 0), 12.0);
        Assert.assertEquals(point.getAtIndex(C_DOUBLE, 1), 11.0);
        Assert.assertEquals(point.getAtIndex(C_DOUBLE, 2), 10.0);
    }
}
