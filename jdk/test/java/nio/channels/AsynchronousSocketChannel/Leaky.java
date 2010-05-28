/*
 * Copyright (c) 2008, 2009, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4607272
 * @summary Unit test for AsynchronousSocketChannel
 * @run main/othervm -XX:+DisableExplicitGC -mx64m Leaky
 */

import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.net.*;
import java.util.concurrent.Future;

/**
 * Heap buffers must be substituted with direct buffers when doing I/O. This
 * test creates a scenario on Windows that challenges the per-thread buffer
 * cache and quickly leads to an OutOfMemoryError if temporary buffers are
 * not returned to the native heap.
 */

public class Leaky {

    static final int K = 1024;

    static class Connection {
        private final AsynchronousSocketChannel client;
        private final SocketChannel peer;
        private final ByteBuffer dst;
        private Future<Integer> readResult;

        Connection() throws Exception {
            ServerSocketChannel ssc =
                ServerSocketChannel.open().bind(new InetSocketAddress(0));
            InetAddress lh = InetAddress.getLocalHost();
            int port = ((InetSocketAddress)(ssc.getLocalAddress())).getPort();
            SocketAddress remote = new InetSocketAddress(lh, port);
            client = AsynchronousSocketChannel.open();
            client.connect(remote).get();
            peer = ssc.accept();
            ssc.close();
            dst = ByteBuffer.allocate(K*K);
        }

        void startRead() {
            dst.clear();
            readResult = client.read(dst);
        }

        void write() throws Exception {
            peer.write(ByteBuffer.wrap("X".getBytes()));
        }

        void finishRead() throws Exception {
            readResult.get();
        }
    }

    public static void main(String[] args) throws Exception {

        final int CONNECTION_COUNT = 10;
        Connection[] connections = new Connection[CONNECTION_COUNT];
        for (int i=0; i<CONNECTION_COUNT; i++) {
            connections[i] = new Connection();
        }

        for (int i=0; i<1024; i++) {
            // initiate reads
            for (Connection conn: connections) {
                conn.startRead();
            }

            // write data so that the read can complete
            for (Connection conn: connections) {
                conn.write();
            }

            // complete read
            for (Connection conn: connections) {
                conn.finishRead();
            }
        }
    }
}
