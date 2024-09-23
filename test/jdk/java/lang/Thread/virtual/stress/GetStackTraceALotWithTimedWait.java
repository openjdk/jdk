/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Stress test Thread.getStackTrace on a virtual thread in timed-Object.wait
 * @requires vm.debug != true
 * @run main/othervm GetStackTraceALotWithTimedWait 100000
 */

/*
 * @test
 * @requires vm.debug == true
 * @run main/othervm GetStackTraceALotWithTimedWait 50000
 */

import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class GetStackTraceALotWithTimedWait {

    public static void main(String[] args) throws Exception {
        int iterations = args.length > 0 ? Integer.parseInt(args[0]) : 100_000;

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var done = new AtomicBoolean();
            var threads = new ArrayList<Thread>();

            // start threads that invoke Object.wait with a short timeout
            int nthreads = Runtime.getRuntime().availableProcessors();
            for (int i = 0; i < nthreads; i++) {
                var ref = new AtomicReference<Thread>();
                var lock = new Object();
                executor.submit(() -> {
                    ref.set(Thread.currentThread());
                    while (!done.get()) {
                        synchronized (lock) {
                            int delay = 1 + ThreadLocalRandom.current().nextInt(20);
                            lock.wait(delay);
                        }
                    }
                    return null;
                });
                Thread thread;
                while ((thread = ref.get()) == null) {
                    Thread.sleep(20);
                }
                threads.add(thread);
            }

            // hammer on Thread.getStackTrace
            try {
                for (int i = 1; i <= iterations; i++) {
                    if ((i % 1000) == 0) {
                        System.out.println(Instant.now() + " => " + i + " of " + iterations);
                    }
                    for (Thread thread : threads) {
                        thread.getStackTrace();
                    }
                }
            } finally {
                done.set(true);
            }
        }
    }
}

