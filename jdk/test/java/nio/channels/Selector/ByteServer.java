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

/**
 *
 * Utility class for tests. A simple server, which waits for a connection,
 * writes out n bytes and waits.
 * @author kladko
 */

import java.net.Socket;
import java.net.ServerSocket;

public class ByteServer {

    public static final int PORT = 31415;
    public static final String LOCALHOST = "localhost";
    private int bytecount;
    private Socket  socket;
    private ServerSocket  serversocket;
    private Thread serverthread;
    volatile Exception savedException;

    public ByteServer(int bytecount) throws Exception{
        this.bytecount = bytecount;
        serversocket = new ServerSocket(PORT);
    }

    public void start() {
        serverthread = new Thread() {
            public void run() {
                try {
                    socket = serversocket.accept();
                    socket.getOutputStream().write(new byte[bytecount]);
                    socket.getOutputStream().flush();
                } catch (Exception e) {
                    System.err.println("Exception in ByteServer: " + e);
                    System.exit(1);
                }
            }
        };
        serverthread.start();
    }

    public void exit() throws Exception {
        serverthread.join();
        socket.close();
        serversocket.close();
    }
}
