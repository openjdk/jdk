/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8389671
 * @summary On Windows, a virtual thread invoking Selector.select will pin its carrier
 *     for the duration of the selection operation. Test that a spare thread is used to
 *     carry virtual threads that are started or continue while all carriers are pinned.
 * @requires test.thread.factory != "Virtual"
 * @modules java.base/java.lang:+open
 *          java.base/java.util.concurrent:+open
 * @library /test/lib
 * @run junit/othervm/timeout=600 ${test.main.class}
 */

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.channels.Selector;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;

import jdk.test.lib.thread.VThreadRunner;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PinnedBySelect {

    private static final int N_THREADS = Runtime.getRuntime().availableProcessors();

    private static long keepAliveTime;  // scheduler keep alive time in millis
    private static List<Thread> vthreads;

    /**
     * Start threads to block in timed and untimed selection operations. This
     * will pin all carriers on Windows.
     */
    @BeforeAll
    static void setup() throws Exception {
        keepAliveTime = schedulerKeepAlive();
        log("Scheduler keepAlive = %s ms", keepAliveTime);

        log("Start %d selector threads ...", N_THREADS);
        vthreads = new ArrayList<>();
        var latch = new CountDownLatch(N_THREADS);
        for (int i = 0; i < N_THREADS; i++) {
            boolean timed = (i % 2) == 0;
            Thread vthread = Thread.ofVirtual().start(() -> {
                try (var sel = Selector.open()) {
                    latch.countDown();
                    if (timed) {
                        sel.select(Duration.ofHours(1).toMillis());
                    } else {
                        sel.select();
                    }
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            });
            vthreads.add(vthread);
        }
        latch.await();
        log("All started.");
    }

    /**
     * Shutdown selector threads.
     */
    @AfterAll
    static void finish() throws Exception {
        log("Waiting for selector threads to terminate ...");
        for (Thread vthread : vthreads) {
            vthread.interrupt();
            vthread.join();
        }
        log("All terminated.");
    }

    /**
     * Noop test to check that a spare thread is used.
     */
    @Test
    void testNoop() throws Exception {
        VThreadRunner.run(() -> {
            // do nothing
        });
    }

    /**
     * Test that a spare thread is used to continue a virtual thread that sleeps
     * for longer than the scheduler's keep alive time
     */
    @Test
    void testSleep() throws Exception {
        long sleepTime = keepAliveTime + 1000;
        VThreadRunner.run(() -> {
            log("%s sleep %dms ...", Thread.currentThread(), sleepTime);
            Thread.sleep(Duration.ofMillis(sleepTime));
            log("%s sleep done", Thread.currentThread());
        });
    }

    /**
     * Returns the keep alive time for the ForkJoinPool instance used as the virtual
     * thread scheduler.
     */
    private static long schedulerKeepAlive() throws Exception  {
        Field schedulerField = Class.forName("java.lang.VirtualThread")
                .getDeclaredField("DEFAULT_SCHEDULER");
        schedulerField.setAccessible(true);
        var pool = (ForkJoinPool) schedulerField.get(null);

        Field keepAliveAlive = ForkJoinPool.class.getDeclaredField("keepAlive");
        keepAliveAlive.setAccessible(true);
        return keepAliveAlive.getLong(pool);
    }

    /**
     * Log the give message/args to standard error with a time stamp.
     */
    private static void log(String format, Object ... args) {
        Object[] newArgs = new Object[args.length + 1];
        newArgs[0] = Instant.now();
        System.arraycopy(args, 0, newArgs, 1, args.length);
        System.err.printf("%s " + format + "%n", newArgs);
    }
}
