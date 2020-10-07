/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @modules jdk.incubator.foreign java.base/jdk.internal.vm.annotation java.base/jdk.internal.misc
 * @key randomness
 * @run testng/othervm TestHandshake
 * @run testng/othervm -Xint TestHandshake
 * @run testng/othervm -XX:TieredStopAtLevel=1 TestHandshake
 * @run testng/othervm -XX:-TieredCompilation TestHandshake
 */

import jdk.incubator.foreign.MemoryAccess;
import jdk.incubator.foreign.MemorySegment;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class TestHandshake {

    static final int ITERATIONS = 5;
    static final int SEGMENT_SIZE = 1_000_000;
    static final int MAX_DELAY_MILLIS = 500;
    static final int MAX_EXECUTOR_WAIT_SECONDS = 10;
    static final int MAX_THREAD_SPIN_WAIT_MILLIS = 200;

    @Test(dataProvider = "accessors")
    public void testHandshake(Function<MemorySegment, Runnable> accessorFactory) throws InterruptedException {
        for (int it = 0 ; it < ITERATIONS ; it++) {
            MemorySegment segment = MemorySegment.allocateNative(SEGMENT_SIZE).share();
            System.err.println("ITERATION " + it);
            ExecutorService accessExecutor = Executors.newCachedThreadPool();
            for (int i = 0; i < Runtime.getRuntime().availableProcessors() ; i++) {
                accessExecutor.execute(accessorFactory.apply(segment));
            }
            Thread.sleep(ThreadLocalRandom.current().nextInt(MAX_DELAY_MILLIS));
            accessExecutor.execute(new Handshaker(segment));
            accessExecutor.shutdown();
            assertTrue(accessExecutor.awaitTermination(MAX_EXECUTOR_WAIT_SECONDS, TimeUnit.SECONDS));
            assertTrue(!segment.isAlive());
        }
    }

    static abstract class AbstractSegmentAccessor implements Runnable {
        final MemorySegment segment;

        AbstractSegmentAccessor(MemorySegment segment) {
            this.segment = segment;
        }

        @Override
        public final void run() {
            outer: while (segment.isAlive()) {
                try {
                    doAccess();
                } catch (IllegalStateException ex) {
                    backoff();
                    continue outer;
                }
            }
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

    static abstract class AbstractBufferAccessor extends AbstractSegmentAccessor {
        final ByteBuffer bb;

        AbstractBufferAccessor(MemorySegment segment) {
            super(segment);
            this.bb = segment.asByteBuffer();
        }
    }

    static class SegmentAccessor extends AbstractSegmentAccessor {

        SegmentAccessor(MemorySegment segment) {
            super(segment);
        }

        @Override
        void doAccess() {
            int sum = 0;
            for (int i = 0; i < segment.byteSize(); i++) {
                sum += MemoryAccess.getByteAtIndex(segment, i);
            }
        }
    }

    static class SegmentCopyAccessor extends AbstractSegmentAccessor {

        MemorySegment first, second;


        SegmentCopyAccessor(MemorySegment segment) {
            super(segment);
            long split = segment.byteSize() / 2;
            first = segment.asSlice(0, split);
            second = segment.asSlice(split);
        }

        @Override
        public void doAccess() {
            first.copyFrom(second);
        }
    }

    static class SegmentFillAccessor extends AbstractSegmentAccessor {

        SegmentFillAccessor(MemorySegment segment) {
            super(segment);
        }

        @Override
        public void doAccess() {
            segment.fill((byte) ThreadLocalRandom.current().nextInt(10));
        }
    }

    static class SegmentMismatchAccessor extends AbstractSegmentAccessor {

        final MemorySegment copy;

        SegmentMismatchAccessor(MemorySegment segment) {
            super(segment);
            this.copy = MemorySegment.allocateNative(SEGMENT_SIZE).share();
            copy.copyFrom(segment);
            MemoryAccess.setByteAtIndex(copy, ThreadLocalRandom.current().nextInt(SEGMENT_SIZE), (byte)42);
        }

        @Override
        public void doAccess() {
            segment.mismatch(copy);
        }
    }

    static class BufferAccessor extends AbstractBufferAccessor {

        BufferAccessor(MemorySegment segment) {
            super(segment);
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

        public BufferHandleAccessor(MemorySegment segment) {
            super(segment);
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

        final MemorySegment segment;

        Handshaker(MemorySegment segment) {
            this.segment = segment;
        }

        @Override
        public void run() {
            long prev = System.currentTimeMillis();
            while (true) {
                try {
                    segment.close();
                    break;
                } catch (IllegalStateException ex) {
                    Thread.onSpinWait();
                }
            }
            long delay = System.currentTimeMillis() - prev;
            System.out.println("Segment closed - delay (ms): " + delay);
        }
    }

    @DataProvider
    static Object[][] accessors() {
        return new Object[][] {
                { (Function<MemorySegment, Runnable>)SegmentAccessor::new },
                { (Function<MemorySegment, Runnable>)SegmentCopyAccessor::new },
                { (Function<MemorySegment, Runnable>)SegmentMismatchAccessor::new },
                { (Function<MemorySegment, Runnable>)SegmentFillAccessor::new },
                { (Function<MemorySegment, Runnable>)BufferAccessor::new },
                { (Function<MemorySegment, Runnable>)BufferHandleAccessor::new }
        };
    }
}
