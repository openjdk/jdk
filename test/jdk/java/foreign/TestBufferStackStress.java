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
 * @build NativeTestHelper TestBufferStackStress
 * @run junit TestBufferStackStress
 */

import jdk.internal.foreign.BufferStack;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.time.Duration;
import java.util.Arrays;
import java.util.stream.IntStream;

import static java.lang.foreign.ValueLayout.*;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

public class TestBufferStackStress {

    @Test
    public void stress() throws InterruptedException {
        BufferStack stack = BufferStack.of(256, 1);
        Thread[] vThreads = IntStream.range(0, 1024).mapToObj(_ ->
                Thread.ofVirtual().start(() -> {
                    long threadId = Thread.currentThread().threadId();
                    while (!Thread.interrupted()) {
                        for (int i = 0; i < 1_000_000; i++) {
                            try (Arena arena = stack.pushFrame(JAVA_LONG.byteSize(), JAVA_LONG.byteAlignment())) {
                                // Try to assert no two vThreads get allocated the same stack space.
                                MemorySegment segment = arena.allocate(JAVA_LONG);
                                JAVA_LONG.varHandle().setVolatile(segment, 0L, threadId);
                                assertEquals(threadId, (long) JAVA_LONG.varHandle().getVolatile(segment, 0L));
                            }
                        }
                        Thread.yield(); // make sure the driver thread gets a chance.
                    }
                })).toArray(Thread[]::new);
        Thread.sleep(Duration.of(10, SECONDS));
        Arrays.stream(vThreads).forEach(
                thread -> {
                    assertTrue(thread.isAlive());
                    thread.interrupt();
                });
    }

}
