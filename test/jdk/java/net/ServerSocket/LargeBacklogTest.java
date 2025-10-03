/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.ServerSocketChannel;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.fail;

/*
 * @test
 * @bug 8330940
 * @summary verify that java.net.ServerSocket and the server socket channels in java.nio.channels
 *          when configured with a backlog of >=200 on Windows, will allow for those many
 *          backlogged Socket connections
 * @requires os.family == "windows"
 * @run junit LargeBacklogTest
 */
class LargeBacklogTest {

    @Test
    void testServerSocket() throws Exception {
        final int backlog = 242;
        // Create a ServerSocket configured with the given backlog.
        // The ServerSocket never accept()s a connection so each connect() attempt
        // will be backlogged.
        try (var server = new ServerSocket(0, backlog, InetAddress.getLoopbackAddress())) {
            final int serverPort = server.getLocalPort();
            testBackloggedConnects(backlog, serverPort);
        }
    }

    @Test
    void testServerSocketChannel() throws Exception {
        final int backlog = 213;
        // Create a ServerSocketChannel configured with the given backlog.
        // The channel never accept()s a connection so each connect() attempt
        // will be backlogged.
        try (var serverChannel = ServerSocketChannel.open()) {
            serverChannel.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), backlog);
            final int serverPort = ((InetSocketAddress) serverChannel.getLocalAddress()).getPort();
            testBackloggedConnects(backlog, serverPort);
        }
    }

    @Test
    void testAsynchronousServerSocketChannel() throws Exception {
        final int backlog = 209;
        // Create a AsynchronousServerSocketChannel configured with the given backlog.
        // The channel never accept()s a connection so each connect() attempt
        // will be backlogged.
        try (var serverChannel = AsynchronousServerSocketChannel.open()) {
            serverChannel.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), backlog);
            final int serverPort = ((InetSocketAddress) serverChannel.getLocalAddress()).getPort();
            testBackloggedConnects(backlog, serverPort);
        }
    }

    private static void testBackloggedConnects(final int backlog, final int serverPort) {
        int numSuccessfulConnects = 0;
        System.err.println("attempting " + backlog + " connections to port " + serverPort);
        // attempt the Socket connections
        for (int i = 1; i <= backlog; i++) {
            try (final Socket sock = new Socket(InetAddress.getLoopbackAddress(), serverPort)) {
                numSuccessfulConnects++;
                System.err.println("connection " + i + " established " + sock);
            } catch (IOException ioe) {
                System.err.println("connection attempt " + i + " failed: " + ioe);
                // do not attempt any more connections
                break;
            }
        }
        System.err.println(numSuccessfulConnects + " connections successfully established");
        // ideally we expect the number of successful connections to be equal to the backlog value.
        // however in certain environments, it's possible that some other process attempts a
        // connection to the server's port. so we allow for a small number of connection attempts
        // to fail (due to exceeding the backlog)
        final int minimumExpectedSuccessfulConns = backlog - 5;
        if (numSuccessfulConnects < minimumExpectedSuccessfulConns) {
            fail("expected at least " + minimumExpectedSuccessfulConns
                    + " successful connections for a backlog of " + backlog + ", but only "
                    + numSuccessfulConnects + " were successful");
        }
    }
}
