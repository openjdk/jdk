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
 * @summary If an ExecutorService is unreferenced then the underlying ThreadPoolExecutor
 *     should be shutdown and terminate when all queued tasks have completed
 * @modules java.base/java.util.concurrent:+open
 * @run junit UnreferencedSTE
 */

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class UnreferencedSTE {

    /**
     * SingleThreadExecutor with no worker threads.
     */
    @Test
    void testNoWorker() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        ExecutorService delegate = getDelegate(executor);
        executor = null;
        gcAndAwaitTermination(delegate);
    }

    /**
     * SingleThreadExecutor with an idle worker thread.
     */
    @Test
    void testIdleWorker() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        // submit a task to get a worker to start
        executor.submit(() -> null).get();
        ExecutorService delegate = getDelegate(executor);
        executor = null;
        gcAndAwaitTermination(delegate);
    }

    /**
     * SingleThreadExecutor with an active worker and 0-2 queued tasks.
     */
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void testActiveWorker(int queuedTaskCount) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        int ntasks = 1 + queuedTaskCount;
        AtomicInteger completedTaskCount = new AtomicInteger();
        for (int i = 0; i < ntasks; i++) {
            executor.submit(() -> {
                Thread.sleep(Duration.ofSeconds(1));
                completedTaskCount.incrementAndGet();
                return null;
            });
        }
        ExecutorService delegate = getDelegate(executor);
        executor = null;
        gcAndAwaitTermination(delegate);
        assertEquals(ntasks, completedTaskCount.get());
    }

    /**
     * Returns the delegates for the given ExecutorService. The given ExectorService
     * must be a Executors$DelegatedExecutorService.
     */
    private ExecutorService getDelegate(ExecutorService executor) throws Exception {
        Field eField = Class.forName("java.util.concurrent.Executors$DelegatedExecutorService")
                .getDeclaredField("e");
        eField.setAccessible(true);
        return (ExecutorService) eField.get(executor);
    }

    /**
     * Invokes System.gc and and waits for the given ExecutorService to terminate.
     */
    private void gcAndAwaitTermination(ExecutorService executor) throws Exception {
        System.err.println(executor);
        boolean terminated = false;
        while (!terminated) {
            System.gc();
            terminated = executor.awaitTermination(100, TimeUnit.MILLISECONDS);
        }
    }
}

