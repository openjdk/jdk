/*
 * Copyright (c) 2004, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4729342
 * @summary Check for CancelledKeyException when key cancelled during select
 */

import java.nio.channels.*;
import java.io.IOException;
import java.net.*;

public class SelectAndCancel {
    static SelectionKey sk;

    /*
     * CancelledKeyException is the failure symptom of 4729342
     * NOTE: The failure is timing dependent and is not always
     * seen immediately when the bug is present.
     */
    public static void main(String[] args) throws Exception {
        final Selector selector = Selector.open();
        final ServerSocketChannel ssc =
            ServerSocketChannel.open().bind(new InetSocketAddress(0));
        final InetSocketAddress isa =
            new InetSocketAddress(InetAddress.getLocalHost(), ssc.socket().getLocalPort());

        // Create and start a selector in a separate thread.
        new Thread(new Runnable() {
                public void run() {
                    try {
                        ssc.configureBlocking(false);
                        sk = ssc.register(selector, SelectionKey.OP_ACCEPT);
                        selector.select();
                    } catch (IOException e) {
                        System.err.println("error in selecting thread");
                        e.printStackTrace();
                    }
                }
            }).start();

        // Wait for above thread to get to select() before we call close.
        Thread.sleep(3000);

        // Try to close. This should wakeup select.
        new Thread(new Runnable() {
                public void run() {
                    try {
                        SocketChannel sc = SocketChannel.open();
                        sc.connect(isa);
                        ssc.close();
                        sk.cancel();
                        sc.close();
                    } catch (IOException e) {
                        System.err.println("error in closing thread");
                        System.err.println(e);
                    }
                }
            }).start();

        // Wait for select() to be awakened, which should be done by close.
        Thread.sleep(3000);

        selector.wakeup();
        selector.close();
    }
}
