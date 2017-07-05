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
 * @bug 4737146 4750573
 * @summary Test if isConnectable returns true after connected
 * @library ..
 */

import java.net.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.channels.spi.SelectorProvider;
import java.util.*;

public class IsConnectable {

    static final int DAYTIME_PORT = 13;
    static final String DAYTIME_HOST = TestUtil.HOST;

    static void test() throws Exception {
        InetSocketAddress isa
            = new InetSocketAddress(InetAddress.getByName(DAYTIME_HOST),
                                    DAYTIME_PORT);
        SocketChannel sc = SocketChannel.open();
        sc.configureBlocking(false);
        sc.connect(isa);

        Selector selector = SelectorProvider.provider().openSelector();
        try {
            SelectionKey key = sc.register(selector, SelectionKey.OP_CONNECT);
            int keysAdded = selector.select();
            if (keysAdded > 0) {
                boolean result = sc.finishConnect();
                if (result) {
                    keysAdded = selector.select(5000);
                    // 4750573: keysAdded should not be incremented when op is dropped
                    // from a key already in the selected key set
                    if (keysAdded > 0)
                        throw new Exception("Test failed: 4750573 detected");
                    Set<SelectionKey> sel = selector.selectedKeys();
                    Iterator<SelectionKey> i = sel.iterator();
                    SelectionKey sk = i.next();
                    // 4737146: isConnectable should be false while connected
                    if (sk.isConnectable())
                        throw new Exception("Test failed: 4737146 detected");
                }
            } else {
                throw new Exception("Select failed");
            }
        } finally {
            sc.close();
            selector.close();
        }
    }

    public static void main(String[] args) throws Exception {
        test();
    }

}
