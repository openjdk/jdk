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
 * @run main CancelDuringRegister
 */

import java.nio.channels.*;

public class CancelDuringRegister {

    private static volatile boolean done;

    public static void main(String[] args) throws Exception {
        try (Selector sel = Selector.open()) {

            // create thread to cancel all keys in the selector's key set
            var thread = new Thread(() -> {
                while (!done) {
                    sel.keys().forEach(SelectionKey::cancel);
                }
            });
            thread.start();

            try (SocketChannel sc = SocketChannel.open()) {
                sc.configureBlocking(false);

                for (int i = 0; i <100_000; i++) {
                    // register
                    var key = sc.register(sel, SelectionKey.OP_READ);
                    sel.selectNow();

                    // cancel and flush
                    key.cancel();
                    do {
                        sel.selectNow();
                    } while (sel.keys().contains(key));
                }
            } finally {
                done = true;
            }

            thread.join();
        }
    }
}
