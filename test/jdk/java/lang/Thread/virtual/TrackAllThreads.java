/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @library /test/lib
 * @run junit/othervm -Djdk.trackAllThreads=true TrackAllThreads
 */

import java.lang.ref.WeakReference;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import jdk.test.lib.util.ForceGC;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TrackAllThreads {

    /**
     * Test that a virtual created directly with the Thread API, then parks, is not GC'ed.
     */
    @Test
    void testRootContainer() {
        Thread thread = Thread.ofVirtual().start(LockSupport::park);
        var ref = new WeakReference<>(thread);
        thread = null;
        ForceGC.waitFor(() -> ref.refersTo(null), 2000);
        thread = ref.get();
        if (thread == null) {
            fail("Thread has been GC'ed");
        } else {
            LockSupport.unpark(thread);
        }
    }

    /**
     * Test that a ThreadPoolExecutor using a virtual thread factory can be GC'ed after
     * the last virtual thread terminates.
     */
    @Test
    void testSharedContainer1() {
        var queue = new LinkedTransferQueue<Runnable>();
        ThreadFactory factory = Thread.ofVirtual().factory();
        var executor = new ThreadPoolExecutor(0, 1, 1, TimeUnit.SECONDS, queue, factory);
        executor.submit(() -> { });
        var ref = new WeakReference<>(executor);
        executor = null;
        boolean cleared = ForceGC.waitFor(() -> ref.refersTo(null), Long.MAX_VALUE);
        assertTrue(cleared);
    }

    /**
     * Test that a ThreadPoolExecutor using a virtual thread factory is not GC'ed when
     * a task (running in a virtual thread) has parked.
     */
    @Disabled
    @Test
    void testSharedContainer2() throws Exception {
        var queue = new LinkedTransferQueue<Runnable>();
        ThreadFactory factory = Thread.ofVirtual().factory();
        var executor = new ThreadPoolExecutor(0, 1, 1, TimeUnit.SECONDS, queue, factory);
        executor.execute(LockSupport::park);
        var ref = new WeakReference<>(executor);
        executor = null;
        ForceGC.waitFor(() -> ref.refersTo(null), 2000);
        executor = ref.get();
        if (executor == null) {
            fail("Executor has been GC'ed");
        } else {
            executor.shutdownNow();
            executor.close();
        }
    }

    /**
     * Test that a ThreadPerTaskExecutor using a virtual thread factory can be GC'ed after
     * the last virtual thread terminates.
     */
    @Test
    void testThreadPerTaskExecutor1() {
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        executor.submit(() -> { });
        var ref = new WeakReference<>(executor);
        executor = null;
        boolean cleared = ForceGC.waitFor(() -> ref.refersTo(null), Long.MAX_VALUE);
        assertTrue(cleared);
    }

    /**
     * Test that a ThreadPerTaskExecutor using a virtual thread factory is not GC'ed when
     * a task (running in a virtual thread) has parked.
     */
    @Disabled
    @Test
    void testThreadPerTaskExecutor2() throws Exception {
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        executor.execute(LockSupport::park);
        var ref = new WeakReference<>(executor);
        executor = null;
        ForceGC.waitFor(() -> ref.refersTo(null), 2000);
        executor = ref.get();
        if (executor == null) {
            fail("Executor has been GC'ed");
        } else {
            executor.shutdownNow();
            executor.close();
        }
    }
}
