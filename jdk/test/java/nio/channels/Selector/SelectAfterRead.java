/*
 * Copyright (c) 2002, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4639943
 * @summary  Checks that Windows behavior matches Solaris for
 *           various read/select combinations.
 * @author kladko
 */

import java.net.*;
import java.nio.*;
import java.nio.channels.*;

public class SelectAfterRead {

    final static int TIMEOUT = 1000;

    public static void main(String[] argv) throws Exception {
        // server: accept connection and write one byte
        ByteServer server = new ByteServer(1);
        server.start();
        InetSocketAddress isa = new InetSocketAddress(
                InetAddress.getByName(ByteServer.LOCALHOST), ByteServer.PORT);
        Selector sel = Selector.open();
        SocketChannel sc = SocketChannel.open();
        sc.connect(isa);
        sc.read(ByteBuffer.allocate(1));
        sc.configureBlocking(false);
        sc.register(sel, SelectionKey.OP_READ);
        // previously on Windows select would select channel here, although there was
        // nothing to read
        if (sel.selectNow() != 0)
            throw new Exception("Select returned nonzero value");
        sc.close();
        sel.close();
        server.exit();

        // Now we will test a two reads combination
        // server: accept connection and write two bytes
        server = new ByteServer(2);
        server.start();
        sc = SocketChannel.open();
        sc.connect(isa);
        sc.configureBlocking(false);
        sel = Selector.open();
        sc.register(sel, SelectionKey.OP_READ);
        if (sel.select(TIMEOUT) != 1)
            throw new Exception("One selected key expected");
        sel.selectedKeys().clear();
        // previously on Windows a channel would get selected only once
        if (sel.selectNow() != 1)
            throw new Exception("One selected key expected");
        // Previously on Windows two consequent reads would cause select()
        // to select a channel, although there was nothing remaining to
        // read in the channel
        if (sc.read(ByteBuffer.allocate(1)) != 1)
            throw new Exception("One byte expected");
        if (sc.read(ByteBuffer.allocate(1)) != 1)
            throw new Exception("One byte expected");
        if (sel.selectNow() != 0)
            throw new Exception("Select returned nonzero value");
        sc.close();
        sel.close();
        server.exit();
    }
}
