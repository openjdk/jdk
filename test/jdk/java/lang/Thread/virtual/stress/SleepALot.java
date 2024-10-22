/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Stress test Thread.sleep
 * @requires vm.debug != true & vm.continuations
 * @run main/othervm SleepALot 500000
 */

/*
 * @test
 * @requires vm.debug == true & vm.continuations
 * @run main/othervm/timeout=300 SleepALot 200000
 */

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

public class SleepALot {

    public static void main(String[] args) throws Exception {
        int iterations;
        if (args.length > 0) {
            iterations = Integer.parseInt(args[0]);
        } else {
            iterations = 1_000_000;
        }

        AtomicInteger count = new AtomicInteger();
        Thread thread = Thread.ofVirtual().start(() -> {
            while (count.incrementAndGet() < iterations) {
                try {
                    Thread.sleep(Duration.ofNanos(100));
                } catch (InterruptedException ignore) { }
            }
        });

        boolean terminated;
        do {
            terminated = thread.join(Duration.ofSeconds(1));
            System.out.println(Instant.now() + " => " + count.get() + " of " + iterations);
        } while (!terminated);

        int countValue = count.get();
        if (countValue != iterations) {
            throw new RuntimeException("Thread terminated, count=" + countValue);
        }
    }
}
