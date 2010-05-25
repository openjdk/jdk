/*
 * Copyright (c) 2001, 2007, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4511624
 * @summary Test Making lots of Selectors
 * @library ..
 * @run main/timeout=240 Connect
 */

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;
import java.nio.channels.spi.SelectorProvider;

public class Connect {

    static int success = 0;
    static int LIMIT = 500;

    public static void main(String[] args) throws Exception {
        scaleTest();
    }

    public static void scaleTest() throws Exception {
        InetAddress myAddress=InetAddress.getByName(TestUtil.HOST);
        InetSocketAddress isa = new InetSocketAddress(myAddress,13);

        for (int j=0; j<LIMIT; j++) {
            SocketChannel sc = SocketChannel.open();
            sc.configureBlocking(false);
            boolean result = sc.connect(isa);
            if (!result) {
                Selector RSelector = SelectorProvider.provider().openSelector();
                SelectionKey RKey = sc.register (RSelector, SelectionKey.OP_CONNECT);
                while (!result) {
                    int keysAdded = RSelector.select(100);
                    if (keysAdded > 0) {
                        Set readyKeys = RSelector.selectedKeys();
                        Iterator i = readyKeys.iterator();
                        while (i.hasNext()) {
                            SelectionKey sk = (SelectionKey)i.next();
                            SocketChannel nextReady = (SocketChannel)sk.channel();
                            result = nextReady.finishConnect();
                        }
                    }
                }
                RSelector.close();
            }
            read(sc);
        }
    }

    static void read(SocketChannel sc) throws Exception {
        ByteBuffer bb = ByteBuffer.allocateDirect(100);
        int n = 0;
        while (n == 0) // Note this is not a rigorous check for done reading
            n = sc.read(bb);
        //bb.position(bb.position() - 2);
        //bb.flip();
        //CharBuffer cb = Charset.forName("US-ASCII").newDecoder().decode(bb);
        //System.out.println("Received: \"" + cb + "\"");
        sc.close();
        success++;
    }
}
