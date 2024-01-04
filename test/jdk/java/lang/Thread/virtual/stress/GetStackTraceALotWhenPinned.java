/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8322818
 * @summary Stress test Thread.getStackTrace on a virtual thread that is pinned
 * @requires vm.debug != true
 * @run main GetStackTraceALotWhenPinned 25000
 */

/*
 * @test
 * @requires vm.debug == true
 * @run main/timeout=300 GetStackTraceALotWhenPinned 10000
 */

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

public class GetStackTraceALotWhenPinned {

    public static void main(String[] args) throws Exception {
        var counter = new AtomicInteger(Integer.parseInt(args[0]));

        // Start a virtual thread that loops doing Thread.yield and parking while pinned.
        // This loop creates the conditions for the main thread to sample the stack trace
        // as it transitions from being unmounted to parking while pinned.
        var thread = Thread.startVirtualThread(() -> {
            boolean timed = false;
            while (counter.decrementAndGet() > 0) {
                Thread.yield();
                synchronized (GetStackTraceALotWhenPinned.class) {
                    if (timed) {
                        LockSupport.parkNanos(Long.MAX_VALUE);
                    } else {
                        LockSupport.park();
                    }
                }
                timed = !timed;
            }
        });

        long lastTimestamp = System.currentTimeMillis();
        while (thread.isAlive()) {
            thread.getStackTrace();
            LockSupport.unpark(thread);
            long currentTime = System.currentTimeMillis();
            if ((currentTime - lastTimestamp) > 500) {
                System.out.format("%s %d remaining ...%n", Instant.now(), counter.get());
                lastTimestamp = currentTime;
            }
        }
    }
}
