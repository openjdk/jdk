/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.net.Sdp;

import java.net.*;
import java.io.*;
import java.nio.channels.*;
import java.util.*;

/**
 * Exercise com.oracle.net.Sdp with each IP address plumbed to InfiniBand
 * interfaces listed in a given file.
 */

public class Sanity {
    public static void main(String[] args) throws Exception {
        // The file is a list of interfaces to test.
        Scanner s = new Scanner(new File(args[0]));
        try {
            while (s.hasNextLine()) {
                String link = s.nextLine();
                NetworkInterface ni = NetworkInterface.getByName(link);
                if (ni != null) {
                    Enumeration<InetAddress> addrs = ni.getInetAddresses();
                    while (addrs.hasMoreElements()) {
                        InetAddress addr = addrs.nextElement();
                        System.out.format("Testing %s: %s\n", link, addr.getHostAddress());
                        test(addr);
                    }
                }
            }
        } finally {
            s.close();
        }
    }

    static void test(InetAddress addr) throws Exception {
        // Test SocketChannel and ServerSocketChannel
        ServerSocketChannel ssc = Sdp.openServerSocketChannel();
        try {
            ssc.socket().bind(new InetSocketAddress(addr, 0));
            int port = ssc.socket().getLocalPort();

            // SocketChannel.connect (implicit bind)
            SocketChannel client = Sdp.openSocketChannel();
            try {
                client.connect(new InetSocketAddress(addr, port));
                SocketChannel peer = ssc.accept();
                try {
                    testConnection(Channels.newOutputStream(client),
                                   Channels.newInputStream(peer));
                } finally {
                    peer.close();
                }
            } finally {
                client.close();
            }

            // SocketChannel.connect (explicit bind)
            client = Sdp.openSocketChannel();
            try {
                client.socket().bind(new InetSocketAddress(addr, 0));
                client.connect(new InetSocketAddress(addr, port));
                ssc.accept().close();
            } finally {
                client.close();
            }
        } finally {
            ssc.close();
        }

        // Test Socket and ServerSocket
        ServerSocket ss = Sdp.openServerSocket();
        try {
            ss.bind(new InetSocketAddress(addr, 0));
            int port = ss.getLocalPort();

            // Socket.connect (implicit bind)
            Socket s = Sdp.openSocket();
            try {
                s.connect(new InetSocketAddress(addr, port));
                Socket peer = ss.accept();
                try {
                    testConnection(s.getOutputStream(), peer.getInputStream());
                } finally {
                    peer.close();
                }
            } finally {
                s.close();
            }

            // Socket.connect (explicit bind)
            s = Sdp.openSocket();
            try {
                s.bind(new InetSocketAddress(addr, 0));
                s.connect(new InetSocketAddress(addr, port));
                ss.accept().close();
            } finally {
                s.close();
            }
        } finally {
            ss.close();
        }
    }

    static void testConnection(OutputStream out, InputStream in)
        throws IOException
    {
        byte[] msg = "hello".getBytes();
        out.write(msg);

        byte[] ba = new byte[100];
        int nread = 0;
        while (nread < msg.length) {
            int n = in.read(ba);
            if (n < 0)
                throw new IOException("EOF not expected!");
            nread += n;
        }
    }
}
