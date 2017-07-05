/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * Portions Copyright (c) 2012 IBM Corporation
 */

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

/*
 * @test
 * @bug 6429204
 * @summary SelectionKey.interestOps does not update interest set on Windows.
 * @author Frank Ding
 */
public class RacyDeregister {

    static boolean notified;
    static final Object selectorLock = new Object();
    static final Object notifyLock = new Object();
    /**
     * null: not terminated
     * true: passed
     * false: failed
     */
    static volatile Boolean succTermination = null;

    public static void main(String[] args) throws Exception {
        InetAddress addr = InetAddress.getByName(null);
        ServerSocketChannel sc = ServerSocketChannel.open();
        sc.socket().bind(new InetSocketAddress(addr, 0));

        SocketChannel.open(new InetSocketAddress(addr,
                sc.socket().getLocalPort()));

        SocketChannel accepted = sc.accept();
        accepted.configureBlocking(false);

        SocketChannel.open(new InetSocketAddress(addr,
                sc.socket().getLocalPort()));
        SocketChannel accepted2 = sc.accept();
        accepted2.configureBlocking(false);

        final Selector sel = Selector.open();
        SelectionKey key2 = accepted2.register(sel, SelectionKey.OP_READ);
        final SelectionKey[] key = new SelectionKey[]{
            accepted.register(sel, SelectionKey.OP_READ)};


        // thread that will be changing key[0].interestOps to OP_READ | OP_WRITE
        new Thread() {

            public void run() {
                try {
                    for (int k = 0; k < 15; k++) {
                        for (int i = 0; i < 10000; i++) {
                            synchronized (notifyLock) {
                                synchronized (selectorLock) {
                                    sel.wakeup();
                                    key[0].interestOps(SelectionKey.OP_READ
                                            | SelectionKey.OP_WRITE);
                                }
                                notified = false;
                                long beginTime = System.currentTimeMillis();
                                while (true) {
                                    notifyLock.wait(5000);
                                    if (notified) {
                                        break;
                                    }
                                    long endTime = System.currentTimeMillis();
                                    if (endTime - beginTime > 5000) {
                                        succTermination = false;
                                        // wake up main thread doing select()
                                        sel.wakeup();
                                        return;
                                    }
                                }
                            }
                        }
                    }
                    succTermination = true;
                    // wake up main thread doing select()
                    sel.wakeup();
                } catch (Exception e) {
                    System.out.println(e);
                    succTermination = true;
                    // wake up main thread doing select()
                    sel.wakeup();
                }
            }
        }.start();

        // main thread will be doing registering/deregistering with the sel
        while (true) {
            sel.select();
            if (Boolean.TRUE.equals(succTermination)) {
                System.out.println("Test passed");
                sel.close();
                sc.close();
                break;
            } else if (Boolean.FALSE.equals(succTermination)) {
                System.out.println("Failed to pass the test");
                sel.close();
                sc.close();
                throw new RuntimeException("Failed to pass the test");
            }
            synchronized (selectorLock) {
            }
            if (sel.selectedKeys().contains(key[0]) && key[0].isWritable()) {
                synchronized (notifyLock) {
                    notified = true;
                    notifyLock.notify();
                    key[0].cancel();
                    sel.selectNow();
                    key2 = accepted2.register(sel, SelectionKey.OP_READ);
                    key[0] = accepted.register(sel, SelectionKey.OP_READ);
                }
            }
            key2.cancel();
            sel.selectedKeys().clear();
        }
    }
}
