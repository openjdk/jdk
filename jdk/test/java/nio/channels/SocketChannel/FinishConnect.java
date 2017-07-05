/*
 * Copyright (c) 2001, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test SocketChannel.finishConnect
 * @library ..
 */

import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.channels.spi.SelectorProvider;
import java.nio.charset.*;
import java.util.*;


public class FinishConnect {

    static final int DAYTIME_PORT = 13;
    static final String DAYTIME_HOST = TestUtil.HOST;

    public static void main(String[] args) throws Exception {
        test1(true, true);
        test1(true, false);
        test1(false, true);
        test1(false, false);
        test2();
    }

    static void test1(boolean select, boolean setBlocking) throws Exception {
        InetSocketAddress isa
            = new InetSocketAddress(InetAddress.getByName(DAYTIME_HOST),
                                    DAYTIME_PORT);
        SocketChannel sc = SocketChannel.open();
        sc.configureBlocking(false);
        boolean connected = sc.connect(isa);
        int attempts = 0;

        try {
            sc.connect(isa);
            throw new RuntimeException("Allowed another connect call");
        } catch (IllegalStateException ise) {
            // Correct behavior
        }

        if (setBlocking)
            sc.configureBlocking(true);

        if (!connected && select && !setBlocking) {
            Selector selector = SelectorProvider.provider().openSelector();
            sc.register(selector, SelectionKey.OP_CONNECT);
            while (!connected) {
                int keysAdded = selector.select(100);
                if (keysAdded > 0) {
                    Set readyKeys = selector.selectedKeys();
                    Iterator i = readyKeys.iterator();
                    while (i.hasNext()) {
                        SelectionKey sk = (SelectionKey)i.next();
                        SocketChannel nextReady =
                            (SocketChannel)sk.channel();
                        connected = sc.finishConnect();
                    }
                }
            }
            selector.close();
        }

        while (!connected) {
            if (attempts++ > 30)
                throw new RuntimeException("Failed to connect");
            Thread.sleep(100);
            connected = sc.finishConnect();
        }

        ByteBuffer bb = ByteBuffer.allocateDirect(100);
        int bytesRead = 0;
        int totalRead = 0;
        while (totalRead < 20) {
            bytesRead = sc.read(bb);
            if (bytesRead > 0)
                totalRead += bytesRead;
            if (bytesRead < 0)
                throw new RuntimeException("Message shorter than expected");
        }
        bb.position(bb.position() - 2);         // Drop CRLF
        bb.flip();
        CharBuffer cb = Charset.forName("US-ASCII").newDecoder().decode(bb);
        System.err.println(isa + " says: \"" + cb + "\"");
        sc.close();
    }

    static void test2() throws Exception {
        InetSocketAddress isa
            = new InetSocketAddress(InetAddress.getByName(DAYTIME_HOST),
                                    DAYTIME_PORT);
        boolean done = false;
        int globalAttempts = 0;
        while (!done) {
            if (globalAttempts++ > 50)
                throw new RuntimeException("Failed to connect");
            SocketChannel sc = SocketChannel.open();
            sc.configureBlocking(false);
            boolean connected = sc.connect(isa);
            int localAttempts = 0;
            while (!connected) {
                if (localAttempts++ > 500)
                    throw new RuntimeException("Failed to connect");
                connected = sc.finishConnect();
                if (connected) {
                    done = true;
                    break;
                }
                Thread.sleep(10);
            }
            sc.close();
        }
    }

}
