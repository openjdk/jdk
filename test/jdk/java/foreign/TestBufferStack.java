/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * @run testng TestBufferStack
 */

import jdk.internal.foreign.abi.BufferStack;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.time.Duration;
import java.util.Arrays;
import java.util.stream.IntStream;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.time.temporal.ChronoUnit.SECONDS;

public class TestBufferStack {
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
                    while (true) {
                        try (Arena arena = stack.pushFrame(JAVA_LONG.byteSize(), JAVA_LONG.byteAlignment())) {
                            // Try to assert no two vThreads get allocated the same stack space.
                            MemorySegment segment = arena.allocate(JAVA_LONG);
                            JAVA_LONG.varHandle().setVolatile(segment, 0L, threadId);
                            Assert.assertEquals(threadId, (long) JAVA_LONG.varHandle().getVolatile(segment, 0L));
                        }
                    }
                })).toArray(Thread[]::new);
        Thread.sleep(Duration.of(10, SECONDS));
        Arrays.stream(vThreads).forEach(
                thread -> Assert.assertTrue(thread.isAlive()));
    }
}
