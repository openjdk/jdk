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

import jdk.incubator.foreign.*;
import org.testng.annotations.*;

import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Spliterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.*;

public class TestSharedAccess {

    static final VarHandle intHandle = MemoryLayouts.JAVA_INT.varHandle(int.class);

    @Test
    public void testConfined() throws Throwable {
        Thread owner = Thread.currentThread();
        MemorySegment s = MemorySegment.allocateNative(4);
        AtomicReference<MemorySegment> confined = new AtomicReference<>(s);
        setInt(s, 42);
        assertEquals(getInt(s), 42);
        List<Thread> threads = new ArrayList<>();
        for (int i = 0 ; i < 1000 ; i++) {
            threads.add(new Thread(() -> {
                assertEquals(getInt(confined.get()), 42);
                confined.set(confined.get().handoff(owner));
            }));
        }
        threads.forEach(t -> {
            confined.set(confined.get().handoff(t));
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
        try (MemorySegment s = MemorySegment.allocateNative(layout).share()) {
            for (int i = 0 ; i < layout.elementCount().getAsLong() ; i++) {
                setInt(s.asSlice(i * 4), 42);
            }
            List<Thread> threads = new ArrayList<>();
            List<Spliterator<MemorySegment>> spliterators = new ArrayList<>();
            spliterators.add(s.spliterator(layout));
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
                        assertEquals(getInt(local), 42);
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
            setInt(s, 42);
            assertEquals(getInt(s), 42);
            List<Thread> threads = new ArrayList<>();
            MemorySegment sharedSegment = s.address().asSegmentRestricted(s.byteSize()).share();
            for (int i = 0 ; i < 1000 ; i++) {
                threads.add(new Thread(() -> {
                    assertEquals(getInt(sharedSegment), 42);
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

    @Test
    public void testHandoffToSelf() {
        MemorySegment s1 = MemorySegment.ofArray(new int[4]);
        MemorySegment s2 = s1.handoff(Thread.currentThread());
        assertFalse(s1.isAlive());
        assertTrue(s2.isAlive());
    }

    @Test
    public void testShareTwice() {
        MemorySegment s1 = MemorySegment.ofArray(new int[4]).share();
        MemorySegment s2 = s1.share();
        assertFalse(s1.isAlive());
        assertTrue(s2.isAlive());
    }

    @Test(expectedExceptions=UnsupportedOperationException.class)
    public void testBadHandoffNoAccess() {
        MemorySegment.ofArray(new int[4])
            .withAccessModes(MemorySegment.CLOSE).handoff(new Thread());
    }

    @Test(expectedExceptions=UnsupportedOperationException.class)
    public void testBadShareNoAccess() {
        MemorySegment.ofArray(new int[4])
                .withAccessModes(MemorySegment.CLOSE).share();
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

                    setInt(s2.asSlice(4), -42);
                    fail();
                } catch (IllegalStateException ex) {
                    assertTrue(ex.getMessage().contains("owning thread"));
                }
            });

            a.await();
            setInt(s1.asSlice(4), 42);
        }

        b.countDown();
        r.get();
    }

    static int getInt(MemorySegment base) {
        return (int)intHandle.getVolatile(base);
    }

    static void setInt(MemorySegment base, int value) {
        intHandle.setVolatile(base, value);
    }
}
