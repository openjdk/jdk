/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @enablePreview
 * @modules java.base/jdk.internal.ref java.base/jdk.internal.foreign
 * @run testng/othervm TestMemorySession
 */

import java.lang.ref.Cleaner;

import java.lang.foreign.MemorySession;

import jdk.internal.foreign.MemorySessionImpl;
import jdk.internal.ref.CleanerFactory;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;

public class TestMemorySession {

    final static int N_THREADS = 100;

    @Test(dataProvider = "cleaners")
    public void testConfined(Supplier<Cleaner> cleanerSupplier, UnaryOperator<MemorySession> sessionFunc) {
        AtomicInteger acc = new AtomicInteger();
        Cleaner cleaner = cleanerSupplier.get();
        MemorySession session = cleaner != null ?
                MemorySession.openConfined(cleaner) :
                MemorySession.openConfined();
        session = sessionFunc.apply(session);
        for (int i = 0 ; i < N_THREADS ; i++) {
            int delta = i;
            session.addCloseAction(() -> acc.addAndGet(delta));
        }
        assertEquals(acc.get(), 0);

        if (cleaner == null) {
            session.close();
            assertEquals(acc.get(), IntStream.range(0, N_THREADS).sum());
        } else {
            session = null;
            int expected = IntStream.range(0, N_THREADS).sum();
            while (acc.get() != expected) {
                kickGC();
            }
        }
    }

    @Test(dataProvider = "cleaners")
    public void testSharedSingleThread(Supplier<Cleaner> cleanerSupplier, UnaryOperator<MemorySession> sessionFunc) {
        AtomicInteger acc = new AtomicInteger();
        Cleaner cleaner = cleanerSupplier.get();
        MemorySession session = cleaner != null ?
                MemorySession.openShared(cleaner) :
                MemorySession.openShared();
        session = sessionFunc.apply(session);
        for (int i = 0 ; i < N_THREADS ; i++) {
            int delta = i;
            session.addCloseAction(() -> acc.addAndGet(delta));
        }
        assertEquals(acc.get(), 0);

        if (cleaner == null) {
            session.close();
            assertEquals(acc.get(), IntStream.range(0, N_THREADS).sum());
        } else {
            session = null;
            int expected = IntStream.range(0, N_THREADS).sum();
            while (acc.get() != expected) {
                kickGC();
            }
        }
    }

    @Test(dataProvider = "cleaners")
    public void testSharedMultiThread(Supplier<Cleaner> cleanerSupplier, UnaryOperator<MemorySession> sessionFunc) {
        AtomicInteger acc = new AtomicInteger();
        Cleaner cleaner = cleanerSupplier.get();
        List<Thread> threads = new ArrayList<>();
        MemorySession session = cleaner != null ?
                MemorySession.openShared(cleaner) :
                MemorySession.openShared();
        session = sessionFunc.apply(session);
        AtomicReference<MemorySession> sessionRef = new AtomicReference<>(session);
        for (int i = 0 ; i < N_THREADS ; i++) {
            int delta = i;
            Thread thread = new Thread(() -> {
                try {
                    sessionRef.get().addCloseAction(() -> {
                        acc.addAndGet(delta);
                    });
                } catch (IllegalStateException ex) {
                    // already closed - we need to call cleanup manually
                    acc.addAndGet(delta);
                }
            });
            threads.add(thread);
        }
        assertEquals(acc.get(), 0);
        threads.forEach(Thread::start);

        // if no cleaner, close - not all segments might have been added to the session!
        // if cleaner, don't unset the session - after all, the session is kept alive by threads
        if (cleaner == null) {
            while (true) {
                try {
                    session.close();
                    break;
                } catch (IllegalStateException ise) {
                    // session is acquired (by add) - wait some more
                }
            }
        }

        threads.forEach(t -> {
            try {
                t.join();
            } catch (InterruptedException ex) {
                fail();
            }
        });

        if (cleaner == null) {
            assertEquals(acc.get(), IntStream.range(0, N_THREADS).sum());
        } else {
            session = null;
            sessionRef.set(null);
            int expected = IntStream.range(0, N_THREADS).sum();
            while (acc.get() != expected) {
                kickGC();
            }
        }
    }

    @Test
    public void testLockSingleThread() {
        MemorySession session = MemorySession.openConfined();
        List<MemorySession> handles = new ArrayList<>();
        for (int i = 0 ; i < N_THREADS ; i++) {
            MemorySession handle = MemorySession.openConfined();
            keepAlive(handle, session);
            handles.add(handle);
        }

        while (true) {
            try {
                session.close();
                assertEquals(handles.size(), 0);
                break;
            } catch (IllegalStateException ex) {
                assertTrue(handles.size() > 0);
                MemorySession handle = handles.remove(0);
                handle.close();
            }
        }
    }

    @Test
    public void testLockSharedMultiThread() {
        MemorySession session = MemorySession.openShared();
        AtomicInteger lockCount = new AtomicInteger();
        for (int i = 0 ; i < N_THREADS ; i++) {
            new Thread(() -> {
                try (MemorySession handle = MemorySession.openConfined()) {
                    keepAlive(handle, session);
                    lockCount.incrementAndGet();
                    waitSomeTime();
                    lockCount.decrementAndGet();
                    handle.close();
                } catch (IllegalStateException ex) {
                    // might be already closed - do nothing
                }
            }).start();
        }

        while (true) {
            try {
                session.close();
                assertEquals(lockCount.get(), 0);
                break;
            } catch (IllegalStateException ex) {
                waitSomeTime();
            }
        }
    }

    @Test
    public void testCloseEmptyConfinedSession() {
        MemorySession.openConfined().close();
    }

    @Test
    public void testCloseEmptySharedSession() {
        MemorySession.openShared().close();
    }

    @Test
    public void testCloseConfinedLock() {
        MemorySession session = MemorySession.openConfined();
        MemorySession handle = MemorySession.openConfined();
        keepAlive(handle, session);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                handle.close();
            } catch (Throwable ex) {
                failure.set(ex);
            }
        });
        t.start();
        try {
            t.join();
            assertNotNull(failure.get());
            assertEquals(failure.get().getClass(), WrongThreadException.class);
        } catch (Throwable ex) {
            throw new AssertionError(ex);
        }
    }

    @Test(dataProvider = "sessions")
    public void testSessionAcquires(Supplier<MemorySession> sessionFactory) {
        MemorySession session = sessionFactory.get();
        acquireRecursive(session, 5);
        if (session.isCloseable()) {
            session.close();
        }
    }

    private void acquireRecursive(MemorySession session, int acquireCount) {
        try (MemorySession handle = MemorySession.openConfined()) {
            keepAlive(handle, session);
            if (acquireCount > 0) {
                // recursive acquire
                acquireRecursive(session, acquireCount - 1);
            }
            if (session.isCloseable()) {
                assertThrows(IllegalStateException.class, session::close);
            }
        }
    }

    @Test
    public void testConfinedSessionWithImplicitDependency() {
        MemorySession root = MemorySession.openConfined();
        // Create many implicit sessions which depend on 'root', and let them become unreachable.
        for (int i = 0; i < N_THREADS; i++) {
            keepAlive(MemorySession.openConfined(Cleaner.create()), root);
        }
        // Now let's keep trying to close 'root' until we succeed. This is trickier than it seems: cleanup action
        // might be called from another thread (the Cleaner thread), so that the confined session lock count is updated racily.
        // If that happens, the loop below never terminates.
        while (true) {
            try {
                root.close();
                break; // success!
            } catch (IllegalStateException ex) {
                kickGC();
                for (int i = 0 ; i < N_THREADS ; i++) {  // add more races from current thread
                    try (MemorySession session = MemorySession.openConfined()) {
                        keepAlive(session, root);
                        // dummy
                    }
                }
                // try again
            }
        }
    }

    @Test
    public void testConfinedSessionWithSharedDependency() {
        MemorySession root = MemorySession.openConfined();
        List<Thread> threads = new ArrayList<>();
        // Create many implicit sessions which depend on 'root', and let them become unreachable.
        for (int i = 0; i < N_THREADS; i++) {
            MemorySession session = MemorySession.openShared(); // create session inside same thread!
            keepAlive(session, root);
            Thread t = new Thread(session::close); // close from another thread!
            threads.add(t);
            t.start();
        }
        for (int i = 0 ; i < N_THREADS ; i++) { // add more races from current thread
            try (MemorySession session = MemorySession.openConfined()) {
                keepAlive(session, root);
                // dummy
            }
        }
        threads.forEach(t -> {
            try {
                t.join();
            } catch (InterruptedException ex) {
                // ok
            }
        });
        // Now let's close 'root'. This is trickier than it seems: releases of the confined session happen in different
        // threads, so that the confined session lock count is updated racily. If that happens, the following close will blow up.
        root.close();
    }

    private void waitSomeTime() {
        try {
            Thread.sleep(10);
        } catch (InterruptedException ex) {
            // ignore
        }
    }

    private void kickGC() {
        for (int i = 0 ; i < 100 ; i++) {
            byte[] b = new byte[100];
            System.gc();
            Thread.onSpinWait();
        }
    }

    @DataProvider
    static Object[][] cleaners() {
        return new Object[][] {
                { (Supplier<Cleaner>)() -> null, UnaryOperator.identity() },
                { (Supplier<Cleaner>)Cleaner::create, UnaryOperator.identity() },
                { (Supplier<Cleaner>)CleanerFactory::cleaner, UnaryOperator.identity() },
                { (Supplier<Cleaner>)Cleaner::create, (UnaryOperator<MemorySession>)MemorySession::asNonCloseable },
                { (Supplier<Cleaner>)CleanerFactory::cleaner, (UnaryOperator<MemorySession>)MemorySession::asNonCloseable }
        };
    }

    @DataProvider
    static Object[][] sessions() {
        return new Object[][] {
                { (Supplier<MemorySession>) MemorySession::openConfined},
                { (Supplier<MemorySession>) MemorySession::openShared},
                { (Supplier<MemorySession>) MemorySession::openImplicit},
                { (Supplier<MemorySession>) MemorySession::global},
                { (Supplier<MemorySession>) () -> MemorySession.openConfined().asNonCloseable() },
                { (Supplier<MemorySession>) () -> MemorySession.openShared().asNonCloseable() },
                { (Supplier<MemorySession>) () -> MemorySession.openImplicit().asNonCloseable() },
                { (Supplier<MemorySession>) () -> MemorySession.global().asNonCloseable()},
        };
    }

    private void keepAlive(MemorySession child, MemorySession parent) {
        MemorySessionImpl sessionImpl = MemorySessionImpl.toSessionImpl(parent);
        sessionImpl.acquire0();
        child.addCloseAction(sessionImpl::release0);
    }
}
