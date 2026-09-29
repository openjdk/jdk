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
 * @bug 8336339
 * @summary Race registration and selection key updates with Selector.close
 * @run junit RaceUpdatesAndClose
 */

import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.RepeatedTest;

class RaceUpdatesAndClose {
    private static ExecutorService executor;

    @BeforeAll
    static void setup() throws Exception {
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterAll
    static void finish() {
        executor.shutdown();
    }

    /**
     * Race SelectableChannel.register and Selector.close.
     */
    @RepeatedTest(100)
    void raceRegisterAndClose() throws Exception {
        try (Selector sel = Selector.open();
             DatagramChannel dc = DatagramChannel.open()) {

            dc.configureBlocking(false);

            Phaser phaser = new Phaser(2);

            // register
            var task1 = executor.submit(() -> {
                phaser.arriveAndAwaitAdvance();
                try {
                    dc.register(sel, SelectionKey.OP_READ);
                } catch (ClosedSelectorException e) { }
                return null;
            });

            // close selector
            var task2 = executor.submit(() -> {
                phaser.arriveAndAwaitAdvance();
                sel.close();
                return null;
            });

            task1.get();
            task2.get();
        }
    }

    /**
     * Race SelectionKey.interestOps and Selector.close.
     */
    @RepeatedTest(100)
    void raceInterestOpsAndClose() throws Exception {
        try (Selector sel = Selector.open();
             DatagramChannel dc = DatagramChannel.open()) {

            dc.configureBlocking(false);
            SelectionKey key = dc.register(sel, SelectionKey.OP_READ);

            Phaser phaser = new Phaser(2);

            // interestOps
            var task1 = executor.submit(() -> {
                phaser.arriveAndAwaitAdvance();
                try {
                    key.interestOps(0);
                } catch (CancelledKeyException e) { }
            });

            // close selector
            var task2 = executor.submit(() -> {
                phaser.arriveAndAwaitAdvance();
                sel.close();
                return null;
            });

            task1.get();
            task2.get();
        }
    }
}
