/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 8287580
 * @summary Check CancelledKeyException not thrown during channel registration
 * @run main KeyCancelled
 */

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CountDownLatch;

public class KeyCancelled {
    private static final int ITERATIONS = 4;

    public static void main(String[] args) throws Exception {
        for (int iteration = 1; iteration <= 4; iteration++) {
            System.out.printf("Iteration %d%n", iteration);
            test();
        }
    }

    private static void test() throws IOException {
        Thread t;
        try (Selector s = Selector.open()) {
            // Use latch to avoid a ClosedSelectorException
            final CountDownLatch latch = new CountDownLatch(1);
            t = new Thread(() -> {
                for (int i = 0; i < 100_000; i++) {
                    s.keys().forEach(SelectionKey::cancel);
                }
                latch.countDown();
            });
            t.start();

            for (int i = 0; i < 10_000; i++) {
                try (SocketChannel c = s.provider().openSocketChannel()) {
                    c.configureBlocking(false);
                    // Sometimes this throws CancelledKeyException, because
                    // the key is cancelled by the other thread part-way through
                    // the register call.
                    c.register(s, SelectionKey.OP_READ);
                    // c.isRegistered() is false here after the exceptional case
                }
            }

            try {
                latch.await();
            } catch (InterruptedException ignored) {
            }
        }
        try {
            t.join();
        } catch (InterruptedException ignored) {
        }
    }
}
