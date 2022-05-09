/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @requires vm.debug != true
 * @compile --enable-preview -source ${jdk.version} PinALot.java
 * @run main/othervm --enable-preview PinALot
 * @summary Stress test timed park when pinned
 */

/**
 * @test
 * @requires vm.debug == true
 * @compile --enable-preview -source ${jdk.version} PinALot.java
 * @run main/othervm/timeout=300 --enable-preview PinALot 200000
 */

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

public class PinALot {

    static final Object lock = new Object();

    public static void main(String[] args) throws Exception {
        int iterations = 1_000_000;
        if (args.length > 0) {
            iterations = Integer.parseInt(args[0]);
        }
        final int ITERATIONS = iterations;

        AtomicInteger count = new AtomicInteger();

        Thread thread = Thread.ofVirtual().start(() -> {
            synchronized (lock) {
                while (count.incrementAndGet() < ITERATIONS) {
                    LockSupport.parkNanos(1);
                }
            }
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
