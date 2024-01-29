/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @requires vm.flavor != "zero"
 * @modules java.base/jdk.internal.vm.annotation java.base/jdk.internal.misc
 * @key randomness
 * @run testng/othervm TestHandshake
 * @run testng/othervm -Xint TestHandshake
 * @run testng/othervm -XX:TieredStopAtLevel=1 TestHandshake
 * @run testng/othervm -XX:-TieredCompilation TestHandshake
 */

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static org.testng.Assert.*;

public class TestHandshake {

    static final int ITERATIONS = 5;
    static final int SEGMENT_SIZE = 1_000_000;
    static final int MAX_DELAY_MILLIS = 500;
    static final int MAX_EXECUTOR_WAIT_SECONDS = 20;
    static final int MAX_THREAD_SPIN_WAIT_MILLIS = 200;

    static final int NUM_ACCESSORS = Math.min(10, Runtime.getRuntime().availableProcessors());

    static final AtomicLong start = new AtomicLong();
    static final AtomicBoolean started = new AtomicBoolean();

    @Test(dataProvider = "accessors")
    public void testHandshake(String testName, AccessorFactory accessorFactory) throws InterruptedException {
        for (int it = 0 ; it < ITERATIONS ; it++) {
            Arena arena = Arena.ofShared();
            MemorySegment segment = arena.allocate(SEGMENT_SIZE, 1);
            System.out.println("ITERATION " + it);
            ExecutorService accessExecutor = Executors.newCachedThreadPool();
            start.set(System.currentTimeMillis());
            started.set(false);
            for (int i = 0; i < NUM_ACCESSORS ; i++) {
                accessExecutor.execute(accessorFactory.make(i, segment, arena));
            }
            int delay = ThreadLocalRandom.current().nextInt(MAX_DELAY_MILLIS);
            System.out.println("Starting handshaker with delay set to " + delay + " millis");
            Thread.sleep(delay);
            accessExecutor.execute(new Handshaker(arena));
            accessExecutor.shutdown();
            assertTrue(accessExecutor.awaitTermination(MAX_EXECUTOR_WAIT_SECONDS, TimeUnit.SECONDS));
            assertTrue(!segment.scope().isAlive());
        }
    }

    static abstract class AbstractSegmentAccessor implements Runnable {
        final MemorySegment segment;
        final int id;
        final AtomicBoolean failed = new AtomicBoolean();

        AbstractSegmentAccessor(int id, MemorySegment segment) {
            this.id = id;
            this.segment = segment;
        }

        @Override
        public final void run() {
            start("\"Accessor #\" + id");
            while (segment.scope().isAlive()) {
                try {
                    doAccess();
                } catch (IllegalStateException ex) {
                    if (!failed.get()) {
                        // ignore - this means segment was alive, but was closed while we were accessing it
                        // next isAlive test should fail
                        assertFalse(segment.scope().isAlive());
                        failed.set(true);
                    } else {
                        // rethrow!
                        throw ex;
                    }
                }
            }
            long delay = System.currentTimeMillis() - start.get();
            System.out.println("Accessor #" + id + " terminated - elapsed (ms): " + delay);
        }

        abstract void doAccess();

        private void backoff() {
            try {
                Thread.sleep(ThreadLocalRandom.current().nextInt(MAX_THREAD_SPIN_WAIT_MILLIS));
            } catch (InterruptedException ex) {
                throw new AssertionError(ex);
            }
        }
    }

    static void start(String name) {
        if (started.compareAndSet(false, true)) {
            long delay = System.currentTimeMillis() - start.get();
            System.out.println("Started first thread: " + name + " ; elapsed (ms): " + delay);
        }
    }

    static abstract class AbstractBufferAccessor extends AbstractSegmentAccessor {
        final ByteBuffer bb;

        AbstractBufferAccessor(int id, MemorySegment segment, Arena _unused) {
            super(id, segment);
            this.bb = segment.asByteBuffer();
        }
    }

    static class SegmentAccessor extends AbstractSegmentAccessor {

        SegmentAccessor(int id, MemorySegment segment, Arena _unused) {
            super(id, segment);
        }

        @Override
        void doAccess() {
            int sum = 0;
            for (int i = 0; i < segment.byteSize(); i++) {
                sum += segment.get(JAVA_BYTE, i);
            }
        }
    }

    static class SegmentCopyAccessor extends AbstractSegmentAccessor {

        MemorySegment first, second;


        SegmentCopyAccessor(int id, MemorySegment segment, Arena _unused) {
            super(id, segment);
            long split = segment.byteSize() / 2;
            first = segment.asSlice(0, split);
            second = segment.asSlice(split);
        }

        @Override
        public void doAccess() {
            first.copyFrom(second);
        }
    }

    static class SegmentSwappyCopyAccessor extends AbstractSegmentAccessor {

        MemorySegment first, second;
        ValueLayout sourceLayout, destLayout;
        long count;


        SegmentSwappyCopyAccessor(int id, MemorySegment segment, Arena _unused) {
            super(id, segment);
            long split = segment.byteSize() / 2;
            first = segment.asSlice(0, split);
            sourceLayout = JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN);
            second = segment.asSlice(split);
            destLayout = JAVA_INT.withOrder(ByteOrder.BIG_ENDIAN);
            count = Math.min(first.byteSize() / sourceLayout.byteSize(),
                second.byteSize() / destLayout.byteSize());
        }

        @Override
        public void doAccess() {
            MemorySegment.copy(first, sourceLayout, 0L, second, destLayout, 0L, count);
        }
    }

    static class SegmentFillAccessor extends AbstractSegmentAccessor {

        SegmentFillAccessor(int id, MemorySegment segment, Arena _unused) {
            super(id, segment);
        }

        @Override
        public void doAccess() {
            segment.fill((byte) ThreadLocalRandom.current().nextInt(10));
        }
    }

    static class SegmentMismatchAccessor extends AbstractSegmentAccessor {

        final MemorySegment copy;

        SegmentMismatchAccessor(int id, MemorySegment segment, Arena arena) {
            super(id, segment);
            this.copy = arena.allocate(SEGMENT_SIZE, 1);
            copy.copyFrom(segment);
            copy.set(JAVA_BYTE, ThreadLocalRandom.current().nextInt(SEGMENT_SIZE), (byte)42);
        }

        @Override
        public void doAccess() {
            segment.mismatch(copy);
        }
    }

    static class BufferAccessor extends AbstractBufferAccessor {

        BufferAccessor(int id, MemorySegment segment, Arena _unused) {
            super(id, segment, null);
        }

        @Override
        public void doAccess() {
            int sum = 0;
            for (int i = 0; i < bb.capacity(); i++) {
                sum += bb.get(i);
            }
        }
    }

    static class BufferHandleAccessor extends AbstractBufferAccessor {

        static VarHandle handle = MethodHandles.byteBufferViewVarHandle(short[].class, ByteOrder.nativeOrder());

        public BufferHandleAccessor(int id, MemorySegment segment, Arena _unused) {
            super(id, segment, null);
        }

        @Override
        public void doAccess() {
            int sum = 0;
            for (int i = 0; i < bb.capacity() / 2; i++) {
                sum += (short) handle.get(bb, i);
            }
        }
    };

    static class Handshaker implements Runnable {

        final Arena arena;

        Handshaker(Arena arena) {
            this.arena = arena;
        }

        @Override
        public void run() {
            start("Handshaker");
            arena.close(); // This should NOT throw
            long delay = System.currentTimeMillis() - start.get();
            System.out.println("Segment closed - elapsed (ms): " + delay);
        }
    }

    interface AccessorFactory {
        AbstractSegmentAccessor make(int id, MemorySegment segment, Arena arena);
    }

    @DataProvider
    static Object[][] accessors() {
        return new Object[][] {
                { "SegmentAccessor", (AccessorFactory)SegmentAccessor::new },
                { "SegmentCopyAccessor", (AccessorFactory)SegmentCopyAccessor::new },
                { "SegmentSwappyCopyAccessor", (AccessorFactory)SegmentSwappyCopyAccessor::new },
                { "SegmentMismatchAccessor", (AccessorFactory)SegmentMismatchAccessor::new },
                { "SegmentFillAccessor", (AccessorFactory)SegmentFillAccessor::new },
                { "BufferAccessor", (AccessorFactory)BufferAccessor::new },
                { "BufferHandleAccessor", (AccessorFactory)BufferHandleAccessor::new }
        };
    }
}
