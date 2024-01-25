/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Stress test timed park when pinned
 * @requires vm.debug != true
 * @library /test/lib
 * @run main/othervm --enable-native-access=ALL-UNNAMED PinALot 500000
 */

/*
 * @test
 * @requires vm.debug == true
 * @library /test/lib
 * @run main/othervm/timeout=300 --enable-native-access=ALL-UNNAMED PinALot 200000
 */

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import jdk.test.lib.thread.VThreadPinner;

public class PinALot {

    public static void main(String[] args) throws Exception {
        int iterations = 1_000_000;
        if (args.length > 0) {
            iterations = Integer.parseInt(args[0]);
        }
        final int ITERATIONS = iterations;

        AtomicInteger count = new AtomicInteger();

        Thread thread = Thread.ofVirtual().start(() -> {
            VThreadPinner.runPinned(() -> {
                while (count.incrementAndGet() < ITERATIONS) {
                    LockSupport.parkNanos(1);
                }
            });
        });

        boolean terminated;
        do {
            terminated = thread.join(Duration.ofSeconds(1));
            System.out.println(Instant.now() + " => " + count.get());
        } while (!terminated);

        int countValue = count.get();
        if (countValue != ITERATIONS) {
            throw new RuntimeException("count = " + countValue);
        }
    }
}
