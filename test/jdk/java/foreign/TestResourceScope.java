/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @modules java.base/jdk.internal.ref
 *          jdk.incubator.foreign/jdk.incubator.foreign
 * @run testng/othervm TestResourceScope
 */

import java.lang.ref.Cleaner;

import jdk.incubator.foreign.ResourceScope;
import jdk.internal.ref.CleanerFactory;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.IntStream;

public class TestResourceScope {

    final static int N_THREADS = 100;

    @Test(dataProvider = "cleaners")
    public void testConfined(Supplier<Cleaner> cleanerSupplier) {
        AtomicInteger acc = new AtomicInteger();
        Cleaner cleaner = cleanerSupplier.get();
        ResourceScope scope = cleaner != null ?
                ResourceScope.newConfinedScope(cleaner) :
                ResourceScope.newConfinedScope();
        for (int i = 0 ; i < N_THREADS ; i++) {
            int delta = i;
            scope.addCloseAction(() -> acc.addAndGet(delta));
        }
        assertEquals(acc.get(), 0);

        if (cleaner == null) {
            scope.close();
            assertEquals(acc.get(), IntStream.range(0, N_THREADS).sum());
        } else {
            scope = null;
            int expected = IntStream.range(0, N_THREADS).sum();
            while (acc.get() != expected) {
                kickGC();
            }
        }
    }

    @Test(dataProvider = "cleaners")
    public void testSharedSingleThread(Supplier<Cleaner> cleanerSupplier) {
        AtomicInteger acc = new AtomicInteger();
        Cleaner cleaner = cleanerSupplier.get();
        ResourceScope scope = cleaner != null ?
                ResourceScope.newSharedScope(cleaner) :
                ResourceScope.newSharedScope();
        for (int i = 0 ; i < N_THREADS ; i++) {
            int delta = i;
            scope.addCloseAction(() -> acc.addAndGet(delta));
        }
        assertEquals(acc.get(), 0);

        if (cleaner == null) {
            scope.close();
            assertEquals(acc.get(), IntStream.range(0, N_THREADS).sum());
        } else {
            scope = null;
            int expected = IntStream.range(0, N_THREADS).sum();
            while (acc.get() != expected) {
                kickGC();
            }
        }
    }

    @Test(dataProvider = "cleaners")
    public void testSharedMultiThread(Supplier<Cleaner> cleanerSupplier) {
        AtomicInteger acc = new AtomicInteger();
        Cleaner cleaner = cleanerSupplier.get();
        List<Thread> threads = new ArrayList<>();
        ResourceScope scope = cleaner != null ?
                ResourceScope.newSharedScope(cleaner) :
                ResourceScope.newSharedScope();
        AtomicReference<ResourceScope> scopeRef = new AtomicReference<>(scope);
        for (int i = 0 ; i < N_THREADS ; i++) {
            int delta = i;
            Thread thread = new Thread(() -> {
                try {
                    scopeRef.get().addCloseAction(() -> {
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

        // if no cleaner, close - not all segments might have been added to the scope!
        // if cleaner, don't unset the scope - after all, the scope is kept alive by threads
        if (cleaner == null) {
            while (true) {
                try {
                    scope.close();
                    break;
                } catch (IllegalStateException ise) {
                    // scope is acquired (by add) - wait some more
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
            scope = null;
            scopeRef.set(null);
            int expected = IntStream.range(0, N_THREADS).sum();
            while (acc.get() != expected) {
                kickGC();
            }
        }
    }

    @Test
    public void testLockSingleThread() {
        ResourceScope scope = ResourceScope.newConfinedScope();
        List<ResourceScope> handles = new ArrayList<>();
        for (int i = 0 ; i < N_THREADS ; i++) {
            ResourceScope handle = ResourceScope.newConfinedScope();
            handle.keepAlive(scope);
            handles.add(handle);
        }

        while (true) {
            try {
                scope.close();
                assertEquals(handles.size(), 0);
                break;
            } catch (IllegalStateException ex) {
                assertTrue(handles.size() > 0);
                ResourceScope handle = handles.remove(0);
                handle.close();
            }
        }
    }

    @Test
    public void testLockSharedMultiThread() {
        ResourceScope scope = ResourceScope.newSharedScope();
        AtomicInteger lockCount = new AtomicInteger();
        for (int i = 0 ; i < N_THREADS ; i++) {
            new Thread(() -> {
                try (ResourceScope handle = ResourceScope.newConfinedScope()) {
                    handle.keepAlive(scope);
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
                scope.close();
                assertEquals(lockCount.get(), 0);
                break;
            } catch (IllegalStateException ex) {
                waitSomeTime();
            }
        }
    }

    @Test
    public void testCloseEmptyConfinedScope() {
        ResourceScope.newConfinedScope().close();
    }

    @Test
    public void testCloseEmptySharedScope() {
        ResourceScope.newSharedScope().close();
    }

    @Test
    public void testCloseConfinedLock() {
        ResourceScope scope = ResourceScope.newConfinedScope();
        ResourceScope handle = ResourceScope.newConfinedScope();
        handle.keepAlive(scope);
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
            assertEquals(failure.get().getClass(), IllegalStateException.class);
        } catch (Throwable ex) {
            throw new AssertionError(ex);
        }
    }

    @Test(dataProvider = "scopes")
    public void testScopeHandles(Supplier<ResourceScope> scopeFactory) {
        ResourceScope scope = scopeFactory.get();
        acquireRecursive(scope, 5);
        if (scope != ResourceScope.globalScope()) {
            scope.close();
        }
    }

    @Test(dataProvider = "scopes", expectedExceptions = IllegalArgumentException.class)
    public void testAcquireSelf(Supplier<ResourceScope> scopeSupplier) {
        ResourceScope scope = scopeSupplier.get();
        scope.keepAlive(scope);
    }

    private void acquireRecursive(ResourceScope scope, int acquireCount) {
        try (ResourceScope handle = ResourceScope.newConfinedScope()) {
            handle.keepAlive(scope);
            if (acquireCount > 0) {
                // recursive acquire
                acquireRecursive(scope, acquireCount - 1);
            }
            if (scope != ResourceScope.globalScope()) {
                assertThrows(IllegalStateException.class, scope::close);
            }
        }
    }

    @Test
    public void testConfinedScopeWithImplicitDependency() {
        ResourceScope root = ResourceScope.newConfinedScope();
        // Create many implicit scopes which depend on 'root', and let them become unreachable.
        for (int i = 0; i < N_THREADS; i++) {
            ResourceScope.newConfinedScope(Cleaner.create()).keepAlive(root);
        }
        // Now let's keep trying to close 'root' until we succeed. This is trickier than it seems: cleanup action
        // might be called from another thread (the Cleaner thread), so that the confined scope lock count is updated racily.
        // If that happens, the loop below never terminates.
        while (true) {
            try {
                root.close();
                break; // success!
            } catch (IllegalStateException ex) {
                kickGC();
                for (int i = 0 ; i < N_THREADS ; i++) {  // add more races from current thread
                    try (ResourceScope scope = ResourceScope.newConfinedScope()) {
                        scope.keepAlive(root);
                        // dummy
                    }
                }
                // try again
            }
        }
    }

    @Test
    public void testConfinedScopeWithSharedDependency() {
        ResourceScope root = ResourceScope.newConfinedScope();
        List<Thread> threads = new ArrayList<>();
        // Create many implicit scopes which depend on 'root', and let them become unreachable.
        for (int i = 0; i < N_THREADS; i++) {
            ResourceScope scope = ResourceScope.newSharedScope(); // create scope inside same thread!
            scope.keepAlive(root);
            Thread t = new Thread(scope::close); // close from another thread!
            threads.add(t);
            t.start();
        }
        for (int i = 0 ; i < N_THREADS ; i++) { // add more races from current thread
            try (ResourceScope scope = ResourceScope.newConfinedScope()) {
                scope.keepAlive(root);
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
        // Now let's close 'root'. This is trickier than it seems: releases of the confined scope happen in different
        // threads, so that the confined scope lock count is updated racily. If that happens, the following close will blow up.
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
                { (Supplier<Cleaner>)() -> null },
                { (Supplier<Cleaner>)Cleaner::create },
                { (Supplier<Cleaner>)CleanerFactory::cleaner }
        };
    }

    @DataProvider
    static Object[][] scopes() {
        return new Object[][] {
                { (Supplier<ResourceScope>)ResourceScope::newConfinedScope },
                { (Supplier<ResourceScope>)ResourceScope::newSharedScope },
                { (Supplier<ResourceScope>)ResourceScope::newImplicitScope },
                { (Supplier<ResourceScope>)ResourceScope::globalScope }
        };
    }
}
