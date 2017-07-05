/*
 * Copyright (c) 2000, 2001, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test Selector with ServerSocketChannels
 * @library ..
 */

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.channels.spi.SelectorProvider;
import java.util.*;


public class BasicAccept {

    public static int TEST_PORT = 40170;

    static void server() throws Exception {
        Selector acceptSelector = Selector.open();
        ServerSocketChannel ssc = ServerSocketChannel.open();
        ssc.configureBlocking(false);
        InetAddress lh = InetAddress.getLocalHost();
        InetSocketAddress isa
            = new InetSocketAddress(lh, SelectorTest.TEST_PORT);
        ssc.socket().bind(isa);
        SelectionKey acceptKey
            = ssc.register(acceptSelector, SelectionKey.OP_ACCEPT);
        for (;;) {
            if (acceptSelector.select() == 0)
                continue;
            Set readyKeys = acceptSelector.selectedKeys();
            Iterator i = readyKeys.iterator();
            while (i.hasNext()) {
                SelectionKey sk = (SelectionKey)i.next();
                i.remove();
                ServerSocketChannel nextReady
                    = (ServerSocketChannel)sk.channel();
                SocketChannel sc = nextReady.accept();
                ByteBuffer bb = ByteBuffer.wrap(new byte[] { 42 });
                sc.write(bb);
            }
        }
    }

    private static class Server extends TestThread {
        Server() {
            super("Server", System.err);
        }
        void go() throws Exception {
            server();
        }
    }

    static void client() throws Exception {
        // Get a connection from the server
        InetAddress lh = InetAddress.getLocalHost();
        InetSocketAddress isa
            = new InetSocketAddress(lh, SelectorTest.TEST_PORT);
        int connectFailures = 0;
        boolean result = false;
        SocketChannel sc = SocketChannel.open();
        for (;;) {
            try {
                result = sc.connect(isa);
                break;
            } catch (java.net.ConnectException e) {
                connectFailures++;
                if (connectFailures > 30)
                    throw new RuntimeException("Cannot connect");
                Thread.currentThread().sleep(100);
                sc = SocketChannel.open();
            }
        }
        if (result) {
            System.err.println("Connected");
        } else {
            // Only happens when server and client are on separate machines
            System.err.println("Connection pending...");
            connectFailures = 0;
            while (!result) {
                try {
                    result = sc.finishConnect();
                    if (!result)
                        System.err.println("Not finished");
                    Thread.sleep(50);
                } catch (java.net.ConnectException e) {
                    Thread.sleep(100);
                    connectFailures++;
                    if (connectFailures > 30)
                        throw new RuntimeException("Cannot finish connecting");
                }
            }
            System.err.println("Finished connecting");
        }

        ByteBuffer bb = ByteBuffer.allocateDirect(1024);
        if (sc.read(bb) < 0)
            throw new RuntimeException("Failed to read from server");
        if (bb.get(0) != 42)
            throw new RuntimeException("Read wrong byte from server");
        System.err.println("Read from server");
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            Server server = new Server();
            server.start();
            client();
        } else if (args[0].equals("client")) {
            client();
        } else if (args[0].equals("server")) {
            server();
        }
    }

}
