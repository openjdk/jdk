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

import static java.lang.foreign.ValueLayout.JAVA_INT;

public class TestBufferStack {
    @Test
    public void testScopedAllocation() {
        BufferStack stack = new BufferStack(256);
        try (Arena frame1 = stack.pushFrame(2 * JAVA_INT.byteSize(), JAVA_INT.byteAlignment())) {
            // Segments have expected sizes and are accessible and allocated consecutively in the same scope.
            MemorySegment segment11 = frame1.allocate(JAVA_INT);
            Assert.assertEquals(segment11.byteSize(), 4);
            segment11.set(JAVA_INT, 0, 1);

            MemorySegment segment12 = frame1.allocate(JAVA_INT);
            Assert.assertEquals(segment12.address(), segment11.address() + 4);
            Assert.assertEquals(segment12.byteSize(), 4);
            Assert.assertEquals(segment12.scope(), segment11.scope());
            segment12.set(JAVA_INT, 0, 1);

            MemorySegment segment21;
            try (Arena frame2 = stack.pushFrame(2 * JAVA_INT.byteSize(), JAVA_INT.byteAlignment())) {
                // same here, but a new scope.
                segment21 = frame2.allocate(JAVA_INT);
                Assert.assertEquals(segment21.address(), segment12.address() + 4);
                Assert.assertEquals(segment21.byteSize(), 4);
                Assert.assertNotEquals(segment21.scope(), segment12.scope());
                segment21.set(JAVA_INT, 0, 1);

                MemorySegment segment22 = frame2.allocate(JAVA_INT);
                Assert.assertEquals(segment22.address(), segment21.address() + 4);
                Assert.assertEquals(segment22.byteSize(), 4);
                Assert.assertEquals(segment22.scope(), segment21.scope());
                segment22.set(JAVA_INT, 0, 1);

                // Frames must be closed in stack order.
                Assert.assertThrows(IllegalStateException.class, frame1::close);
            }
            // Scope is closed here, inner segments throw.
            Assert.assertThrows(IllegalStateException.class, () -> segment21.get(JAVA_INT, 0));
            // A new stack frame allocates at the same location the previous did.
            try (Arena frame3 = stack.pushFrame(2 * JAVA_INT.byteSize(), JAVA_INT.byteAlignment())) {
                MemorySegment segment31 = frame3.allocate(JAVA_INT);
                Assert.assertEquals(segment21.address(), segment12.address() + 4);
            }

            // Outer segments are still accessible.
            segment11.get(JAVA_INT, 0);
            segment12.get(JAVA_INT, 0);
        }
    }
}
