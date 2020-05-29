/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *   Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

/*
 * @test
 * @run testng/othervm -Dforeign.restricted=permit TestSharedAccess
 */

import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.MemoryLayout;
import jdk.incubator.foreign.MemoryLayouts;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.SequenceLayout;
import org.testng.annotations.Test;

import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Spliterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class TestSharedAccess {

    static final VarHandle intHandle = MemoryLayouts.JAVA_INT.varHandle(int.class);

    @Test
    public void testConfined() throws Throwable {
        Thread owner = Thread.currentThread();
        MemorySegment s = MemorySegment.allocateNative(4);
        AtomicReference<MemorySegment> confined = new AtomicReference<>(s);
        setInt(s.baseAddress(), 42);
        assertEquals(getInt(s.baseAddress()), 42);
        List<Thread> threads = new ArrayList<>();
        for (int i = 0 ; i < 1000 ; i++) {
            threads.add(new Thread(() -> {
                assertEquals(getInt(confined.get().baseAddress()), 42);
                confined.set(confined.get().withOwnerThread(owner));
            }));
        }
        threads.forEach(t -> {
            confined.set(confined.get().withOwnerThread(t));
            t.start();
            try {
                t.join();
            } catch (Throwable e) {
                throw new IllegalStateException(e);
            }
        });
        confined.get().close();
    }

    @Test
    public void testShared() throws Throwable {
        SequenceLayout layout = MemoryLayout.ofSequence(1024, MemoryLayouts.JAVA_INT);
        try (MemorySegment s = MemorySegment.allocateNative(layout)) {
            for (int i = 0 ; i < layout.elementCount().getAsLong() ; i++) {
                setInt(s.baseAddress().addOffset(i * 4), 42);
            }
            List<Thread> threads = new ArrayList<>();
            List<Spliterator<MemorySegment>> spliterators = new ArrayList<>();
            spliterators.add(MemorySegment.spliterator(s, layout));
            while (true) {
                boolean progress = false;
                List<Spliterator<MemorySegment>> newSpliterators = new ArrayList<>();
                for (Spliterator<MemorySegment> spliterator : spliterators) {
                    Spliterator<MemorySegment> sub = spliterator.trySplit();
                    if (sub != null) {
                        progress = true;
                        newSpliterators.add(sub);
                    }
                }
                spliterators.addAll(newSpliterators);
                if (!progress) break;
            }

            AtomicInteger accessCount = new AtomicInteger();
            for (Spliterator<MemorySegment> spliterator : spliterators) {
                threads.add(new Thread(() -> {
                    spliterator.tryAdvance(local -> {
                        assertEquals(getInt(local.baseAddress()), 42);
                        accessCount.incrementAndGet();
                    });
                }));
            }
            threads.forEach(Thread::start);
            threads.forEach(t -> {
                try {
                    t.join();
                } catch (Throwable e) {
                    throw new IllegalStateException(e);
                }
            });
            assertEquals(accessCount.get(), 1024);
        }
    }

    @Test
    public void testSharedUnsafe() throws Throwable {
        try (MemorySegment s = MemorySegment.allocateNative(4)) {
            setInt(s.baseAddress(), 42);
            assertEquals(getInt(s.baseAddress()), 42);
            List<Thread> threads = new ArrayList<>();
            MemorySegment sharedSegment = MemorySegment.ofNativeRestricted(
                    s.baseAddress(), s.byteSize(), null, null, null);
            for (int i = 0 ; i < 1000 ; i++) {
                threads.add(new Thread(() -> {
                    assertEquals(getInt(sharedSegment.baseAddress()), 42);
                }));
            }
            threads.forEach(Thread::start);
            threads.forEach(t -> {
                try {
                    t.join();
                } catch (Throwable e) {
                    throw new IllegalStateException(e);
                }
            });
        }
    }

    @Test(expectedExceptions=IllegalStateException.class)
    public void testBadCloseWithPendingAcquire() {
        withAcquired(MemorySegment::close);
    }

    @Test(expectedExceptions=IllegalStateException.class)
    public void testBadCloseWithPendingAcquireBuffer() {
        withAcquired(segment -> {
            segment = MemorySegment.ofByteBuffer(segment.asByteBuffer()); // original segment is lost
            segment.close(); // this should still fail
        });
    }

    @Test(expectedExceptions=IllegalStateException.class)
    public void testBadHandoffWithPendingAcquire() {
        withAcquired(segment -> segment.withOwnerThread(new Thread()));
    }

    @Test(expectedExceptions=IllegalStateException.class)
    public void testBadHandoffWithPendingAcquireBuffer() {
        withAcquired(segment -> {
            segment = MemorySegment.ofByteBuffer(segment.asByteBuffer()); // original segment is lost
            segment.withOwnerThread(new Thread()); // this should still fail
        });
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void testBadHandoffSameThread() {
        MemorySegment.ofArray(new int[4]).withOwnerThread(Thread.currentThread());
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void testBadHandoffNullThread() {
        MemorySegment.ofArray(new int[4]).withOwnerThread(null);
    }

    private void withAcquired(Consumer<MemorySegment> acquiredAction) {
        CountDownLatch holder = new CountDownLatch(1);
        MemorySegment segment = MemorySegment.allocateNative(16);
        Spliterator<MemorySegment> spliterator = MemorySegment.spliterator(segment,
                MemoryLayout.ofSequence(16, MemoryLayouts.JAVA_BYTE));
        CountDownLatch acquired = new CountDownLatch(1);
        Runnable r = () -> spliterator.tryAdvance(s -> {
            try {
                acquired.countDown();
                holder.await();
            } catch (InterruptedException ex) {
                throw new AssertionError(ex);
            }
        });
        new Thread(r).start();
        try {
            acquired.await();
            acquiredAction.accept(segment);
        } catch (InterruptedException ex) {
            throw new AssertionError(ex);
        } finally {
            holder.countDown();
        }
    }

    @Test
    public void testOutsideConfinementThread() throws Throwable {
        CountDownLatch a = new CountDownLatch(1);
        CountDownLatch b = new CountDownLatch(1);
        CompletableFuture<?> r;
        try (MemorySegment s1 = MemorySegment.allocateNative(MemoryLayout.ofSequence(2, MemoryLayouts.JAVA_INT))) {
            r = CompletableFuture.runAsync(() -> {
                try {
                    ByteBuffer bb = s1.asByteBuffer();

                    MemorySegment s2 = MemorySegment.ofByteBuffer(bb);
                    a.countDown();

                    try {
                        b.await();
                    } catch (InterruptedException e) {
                    }

                    MemoryAddress base = s2.baseAddress();
                    setInt(base.addOffset(4), -42);
                    fail();
                } catch (IllegalStateException ex) {
                    assertTrue(ex.getMessage().contains("owning thread"));
                }
            });

            a.await();
            MemoryAddress base = s1.baseAddress();
            setInt(base.addOffset(4), 42);
        }

        b.countDown();
        r.get();
    }

    static int getInt(MemoryAddress address) {
        return (int)intHandle.getVolatile(address);
    }

    static void setInt(MemoryAddress address, int value) {
        intHandle.setVolatile(address, value);
    }
}
