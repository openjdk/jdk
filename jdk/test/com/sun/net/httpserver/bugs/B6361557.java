/*
 * Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6361557
 * @summary  Lightweight HTTP server quickly runs out of file descriptors on Linux
 */

import com.sun.net.httpserver.*;

import java.util.*;
import java.util.concurrent.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.net.*;

/**
 * The test simply opens 1,000 separate connections
 * and invokes one http request on each. The client does
 * not close any sockets until after they are closed
 * by the server. This verifies the basic ability
 * of the server to manage a reasonable number of connections
 */
public class B6361557 {

    public static boolean error = false;
    static final int NUM = 1000;

    static class Handler implements HttpHandler {
        int invocation = 1;
        public void handle (HttpExchange t)
            throws IOException
        {
            InputStream is = t.getRequestBody();
            Headers map = t.getRequestHeaders();
            Headers rmap = t.getResponseHeaders();
            while (is.read () != -1) ;
            is.close();
            t.sendResponseHeaders (200, -1);
            t.close();
        }
    }

    public static void main (String[] args) throws Exception {
        Handler handler = new Handler();
        InetSocketAddress addr = new InetSocketAddress (0);
        HttpServer server = HttpServer.create (addr, 0);
        HttpContext ctx = server.createContext ("/test", handler);

        ExecutorService executor = Executors.newCachedThreadPool();
        server.setExecutor (executor);
        server.start ();

        ByteBuffer buf = ByteBuffer.allocate (4096);
        InetSocketAddress destaddr = new InetSocketAddress (
                "127.0.0.1", server.getAddress().getPort()
        );
        System.out.println ("destaddr " + destaddr);

        Selector selector = Selector.open ();
        int i = 0;
        while (true) {
            i ++;
            int selres = selector.select (1);
            Set<SelectionKey> selkeys = selector.selectedKeys();
            for (SelectionKey key : selkeys) {
                if (key.isReadable()) {
                    SocketChannel chan = (SocketChannel)key.channel();
                    buf.clear();
                    try {
                        int x = chan.read (buf);
                        if (x == -1) {
                            chan.close();
                        }
                    } catch (IOException e) {}
                }
            }
            if (i< NUM) {
                SocketChannel schan = SocketChannel.open (destaddr);
                String cmd = "GET /test/foo.html HTTP/1.1\r\nContent-length: 0\r\n\r\n";
                buf.rewind ();
                buf.put (cmd.getBytes());
                buf.flip();
                int c = 0;
                while (buf.remaining() > 0) {
                    c += schan.write (buf);
                }
                schan.configureBlocking (false);
                schan.register (selector, SelectionKey.OP_READ, null);
            } else {
                System.out.println ("Finished clients");
                server.stop (1);
                executor.shutdown ();
                return;
            }
        }
    }
}
