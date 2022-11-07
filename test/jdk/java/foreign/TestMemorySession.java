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

import java.lang.foreign.Arena;

import java.lang.foreign.MemorySession;

import jdk.internal.foreign.MemorySessionImpl;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.IntStream;

public class TestMemorySession {

    final static int N_THREADS = 100;

    @Test
    public void testConfined() {
        AtomicInteger acc = new AtomicInteger();
        Arena arena = Arena.openConfined();
        for (int i = 0 ; i < N_THREADS ; i++) {
            int delta = i;
            addCloseAction(arena.session(), () -> acc.addAndGet(delta));
        }
        assertEquals(acc.get(), 0);

        arena.close();
        assertEquals(acc.get(), IntStream.range(0, N_THREADS).sum());
    }

    @Test(dataProvider = "sharedSessions")
    public void testSharedSingleThread(SessionSupplier sessionSupplier) {
        AtomicInteger acc = new AtomicInteger();
        MemorySession session = sessionSupplier.get();
        for (int i = 0 ; i < N_THREADS ; i++) {
            int delta = i;
            addCloseAction(session, () -> acc.addAndGet(delta));
        }
        assertEquals(acc.get(), 0);

        if (!SessionSupplier.isImplicit(session)) {
            SessionSupplier.close(session);
            assertEquals(acc.get(), IntStream.range(0, N_THREADS).sum());
        } else {
            session = null;
            int expected = IntStream.range(0, N_THREADS).sum();
            while (acc.get() != expected) {
                kickGC();
            }
        }
    }

    @Test(dataProvider = "sharedSessions")
    public void testSharedMultiThread(SessionSupplier sessionSupplier) {
        AtomicInteger acc = new AtomicInteger();
        List<Thread> threads = new ArrayList<>();
        MemorySession session = sessionSupplier.get();
        AtomicReference<MemorySession> sessionRef = new AtomicReference<>(session);
        for (int i = 0 ; i < N_THREADS ; i++) {
            int delta = i;
            Thread thread = new Thread(() -> {
                try {
                    addCloseAction(sessionRef.get(), () -> {
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
        if (!SessionSupplier.isImplicit(session)) {
            while (true) {
                try {
                    SessionSupplier.close(session);
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

        if (!SessionSupplier.isImplicit(session)) {
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
        Arena arena = Arena.openConfined();
        List<Arena> handles = new ArrayList<>();
        for (int i = 0 ; i < N_THREADS ; i++) {
            Arena handle = Arena.openConfined();
            keepAlive(handle.session(), arena.session());
            handles.add(handle);
        }

        while (true) {
            try {
                arena.close();
                assertEquals(handles.size(), 0);
                break;
            } catch (IllegalStateException ex) {
                assertTrue(handles.size() > 0);
                Arena handle = handles.remove(0);
                handle.close();
            }
        }
    }

    @Test
    public void testLockSharedMultiThread() {
        Arena arena = Arena.openShared();
        AtomicInteger lockCount = new AtomicInteger();
        for (int i = 0 ; i < N_THREADS ; i++) {
            new Thread(() -> {
                try (Arena handle = Arena.openConfined()) {
                    keepAlive(handle.session(), arena.session());
                    lockCount.incrementAndGet();
                    waitSomeTime();
                    lockCount.decrementAndGet();
                } catch (IllegalStateException ex) {
                    // might be already closed - do nothing
                }
            }).start();
        }

        while (true) {
            try {
                arena.close();
                assertEquals(lockCount.get(), 0);
                break;
            } catch (IllegalStateException ex) {
                waitSomeTime();
            }
        }
    }

    @Test
    public void testCloseEmptyConfinedSession() {
        Arena.openConfined().close();
    }

    @Test
    public void testCloseEmptySharedSession() {
        Arena.openShared().close();
    }

    @Test
    public void testCloseConfinedLock() {
        Arena arena = Arena.openConfined();
        Arena handle = Arena.openConfined();
        keepAlive(handle.session(), arena.session());
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

    @Test(dataProvider = "allSessions")
    public void testSessionAcquires(SessionSupplier sessionSupplier) {
        MemorySession session = sessionSupplier.get();
        acquireRecursive(session, 5);
        if (!SessionSupplier.isImplicit(session))
            SessionSupplier.close(session);
    }

    private void acquireRecursive(MemorySession session, int acquireCount) {
        try (Arena arena = Arena.openConfined()) {
            keepAlive(arena.session(), session);
            if (acquireCount > 0) {
                // recursive acquire
                acquireRecursive(session, acquireCount - 1);
            }
            if (!SessionSupplier.isImplicit(session)) {
                assertThrows(IllegalStateException.class, () -> SessionSupplier.close(session));
            }
        }
    }

    @Test
    public void testConfinedSessionWithImplicitDependency() {
        Arena root = Arena.openConfined();
        // Create many implicit sessions which depend on 'root', and let them become unreachable.
        for (int i = 0; i < N_THREADS; i++) {
            keepAlive(MemorySession.implicit(), root.session());
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
                    try (Arena arena = Arena.openConfined()) {
                        keepAlive(arena.session(), root.session());
                        // dummy
                    }
                }
                // try again
            }
        }
    }

    @Test
    public void testConfinedSessionWithSharedDependency() {
        Arena root = Arena.openConfined();
        List<Thread> threads = new ArrayList<>();
        // Create many implicit sessions which depend on 'root', and let them become unreachable.
        for (int i = 0; i < N_THREADS; i++) {
            Arena arena = Arena.openShared(); // create session inside same thread!
            keepAlive(arena.session(), root.session());
            Thread t = new Thread(arena::close); // close from another thread!
            threads.add(t);
            t.start();
        }
        for (int i = 0 ; i < N_THREADS ; i++) { // add more races from current thread
            try (Arena arena = Arena.openConfined()) {
                keepAlive(arena.session(), root.session());
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
    static Object[][] drops() {
        return new Object[][] {
                { (Supplier<Arena>) Arena::openConfined},
                { (Supplier<Arena>) Arena::openShared},
        };
    }

    private void keepAlive(MemorySession child, MemorySession parent) {
        MemorySessionImpl parentImpl = MemorySessionImpl.toSessionImpl(parent);
        parentImpl.acquire0();
        addCloseAction(child, parentImpl::release0);
    }

    private void addCloseAction(MemorySession session, Runnable action) {
        MemorySessionImpl sessionImpl = MemorySessionImpl.toSessionImpl(session);
        sessionImpl.addCloseAction(action);
    }

    interface SessionSupplier extends Supplier<MemorySession> {

        static void close(MemorySession session) {
            ((MemorySessionImpl)session).close();
        }

        static boolean isImplicit(MemorySession session) {
            return !((MemorySessionImpl)session).isCloseable();
        }

        static SessionSupplier ofImplicit() {
            return MemorySession::implicit;
        }

        static SessionSupplier ofArena(Supplier<Arena> arenaSupplier) {
            return () -> arenaSupplier.get().session();
        }
    }

    @DataProvider(name = "sharedSessions")
    static Object[][] sharedSessions() {
        return new Object[][] {
                { SessionSupplier.ofArena(Arena::openShared) },
                { SessionSupplier.ofImplicit() },
        };
    }

    @DataProvider(name = "allSessions")
    static Object[][] allSessions() {
        return new Object[][] {
                { SessionSupplier.ofArena(Arena::openConfined) },
                { SessionSupplier.ofArena(Arena::openShared) },
                { SessionSupplier.ofImplicit() },
        };
    }
}
