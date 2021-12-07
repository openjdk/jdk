/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8278339
 * @summary Test that ServerSocket::isClosed returns true after async close
 */

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class IsClosedAfterAsyncClose {

    private static final int ITERATIONS = 100;

    public static void main(String[] args) throws Exception {
        for (int i = 0; i < ITERATIONS; i++) {
            System.out.printf("Test %d...%n", i);

            // create listener bound to the loopback address
            ServerSocket listener = new ServerSocket();
            InetAddress loopback = InetAddress.getLoopbackAddress();
            listener.bind(new InetSocketAddress(loopback, 0));

            // task to close listener after a delay
            Runnable closeListener = () -> {
                try {
                    Thread.sleep(100);
                    listener.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            };

            // main thread blocks in accept. When listener is closed then accept
            // should wakeup with an IOException and isClosed should be true.
            try (listener) {
                Thread closer = new Thread(closeListener);
                closer.start();
                try {
                    while (true) {
                        Socket s = listener.accept();
                        // close spurious connection
                        s.close();
                    }
                } catch (IOException ioe) {
                    if (!listener.isClosed()) {
                        throw new RuntimeException("isClosed returned false!!");
                    }
                } finally {
                    closer.join();
                }
            }
        }
    }
}

