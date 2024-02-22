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
 * @test id=platform
 * @bug 8323782
 * @summary Stress test Thread.interrupt on a target Thread doing a selection operation
 * @run main LotsOfInterrupts 200000
 */

/*
 * @test id=virtual
 * @run main/othervm -DthreadFactory=virtual LotsOfInterrupts 200000
 */

import java.nio.channels.Selector;
import java.time.Instant;
import java.util.concurrent.Phaser;
import java.util.concurrent.ThreadFactory;

public class LotsOfInterrupts {

    public static void main(String[] args) throws Exception {
        int iterations;
        if (args.length > 0) {
            iterations = Integer.parseInt(args[0]);
        } else {
            iterations = 500_000;
        }

        ThreadFactory factory;
        String value = System.getProperty("threadFactory");
        if ("virtual".equals(value)) {
            factory = Thread.ofVirtual().factory();
        } else {
            factory = Thread.ofPlatform().factory();
        }

        var phaser = new Phaser(2);

        Thread thread = factory.newThread(() -> {
            try (Selector sel = Selector.open()) {
                for (int i = 0; i < iterations; i++) {
                    phaser.arriveAndAwaitAdvance();
                    sel.select();

                    // clear interrupt status and consume wakeup
                    Thread.interrupted();
                    sel.selectNow();
                }
            } catch (Throwable ex) {
                ex.printStackTrace();
            }
        });
        thread.start();

        long lastTimestamp = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            phaser.arriveAndAwaitAdvance();
            thread.interrupt();

            long currentTime = System.currentTimeMillis();
            if ((currentTime - lastTimestamp) > 500) {
                System.out.format("%s %d iterations remaining ...%n", Instant.now(), (iterations - i));
                lastTimestamp = currentTime;
            }
        }

        thread.join();
    }
}
