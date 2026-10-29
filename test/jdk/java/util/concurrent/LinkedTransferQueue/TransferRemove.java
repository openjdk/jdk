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
 * @bug 8395712
 * @summary Removing a transferred element must wake its producer
 * @library /test/lib
 * @run main/othervm/timeout=60 -XX:ActiveProcessorCount=1 TransferRemove
 */

/*
 * @test id=no-lse
 * @bug 8395712
 * @summary Removing a transferred element must wake its producer without LSE
 * @requires os.arch == "aarch64"
 * @library /test/lib
 * @run main/othervm/timeout=60 -XX:-UseLSE -XX:ActiveProcessorCount=1 TransferRemove
 */

import java.util.ArrayList;
import java.util.concurrent.LinkedTransferQueue;

public class TransferRemove {
    private static final int ITERATIONS = 1_000_000;
    private static final int PAIRS = 4;

    public static void main(String[] args) throws Exception {
        var threads = new ArrayList<Thread>(PAIRS * 2);
        var queues = new ArrayList<LinkedTransferQueue<Object>>(PAIRS);
        for(int pair = 0; pair < PAIRS; pair++) {
            var queue = new LinkedTransferQueue<Object>();
            Object token = new Object();
            var transferer = Thread.ofPlatform().daemon().start(() -> {
                try {
                    for (int i = 0; i < ITERATIONS; i++)
                        queue.transfer(token);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            });
            var remover = Thread.ofPlatform().daemon().start(() -> {
                for (int i = 0; i < ITERATIONS; i++) {
                    while(!queue.remove(token)) {
                        Thread.onSpinWait();
                    }
                }
            });

            threads.add(transferer);
            threads.add(remover);
            queues.add(queue);
        }

        for (var t : threads) {
            t.join();
        }

        for (var q : queues) {
            if (!q.isEmpty())
                throw new AssertionError("Queue not empty after removals");
        }
    }
}
