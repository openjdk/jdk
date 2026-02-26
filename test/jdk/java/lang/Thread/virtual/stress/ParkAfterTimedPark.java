/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @test id=parked
 * @bug 8369227
 * @summary Stress test untimed park after a timed park when a thread is unparked around the
 *     same time that the timeout expires.
 * @library /test/lib
 * @run main/othervm --enable-native-access=ALL-UNNAMED ParkAfterTimedPark 200 false
 */

/*
 * @test id=pinned
 * @summary Stress test untimed park, while pinned, and after a timed park when a thread is
 *     unparked around the same time that the timeout expires.
 * @library /test/lib
 * @run main/othervm --enable-native-access=ALL-UNNAMED ParkAfterTimedPark 200 true
 */

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import jdk.test.lib.thread.VThreadPinner;

public class ParkAfterTimedPark {
    public static void main(String[] args) throws Exception {
        int iterations = (args.length > 0) ? Integer.parseInt(args[0]) : 100;
        boolean pinned = (args.length > 1) ? Boolean.parseBoolean(args[1]) : false;

        for (int i = 1; i <= iterations; i++) {
            System.out.println(Instant.now() + " => " + i + " of " + iterations);
            for (int timeout = 1; timeout <= 10; timeout++) {
                test(timeout, pinned);
            }
        }
    }

    /**
     * Creates two virtual threads. The first does a timed-park for the given time,
     * then parks in CountDownLatch.await. A second virtual thread unparks the first
     * around the same time that the timeout for the first expires.
     */
    private static void test(int millis, boolean pinned) throws Exception {
        long nanos = TimeUnit.MILLISECONDS.toNanos(millis);

        var finish = new CountDownLatch(1);

        Thread thread1 = Thread.startVirtualThread(() -> {
            LockSupport.parkNanos(nanos);
            boolean done = false;
            while (!done) {
                try {
                    if (pinned) {
                        VThreadPinner.runPinned(() -> {
                            finish.await();
                        });
                    } else {
                        finish.await();
                    }
                    done = true;
                } catch (InterruptedException e) { }
            }
        });

        Thread thread2 = Thread.startVirtualThread(() -> {
            int delta = ThreadLocalRandom.current().nextInt(millis);
            boolean done = false;
            while (!done) {
                try {
                    Thread.sleep(millis - delta);
                    done = true;
                } catch (InterruptedException e) { }
            }
            LockSupport.unpark(thread1);
        });

        // wait for first thread to park before count down
        await(thread1, Thread.State.WAITING);
        finish.countDown();

        thread1.join();
        thread2.join();
    }

    /**
     * Waits for the given thread to reach a given state.
     */
    private static void await(Thread thread, Thread.State expectedState) throws Exception {
        Thread.State state = thread.getState();
        while (state != expectedState) {
             if (state == Thread.State.TERMINATED)
                 throw new RuntimeException("Thread has terminated");
            Thread.sleep(10);
            state = thread.getState();
        }
    }
}
