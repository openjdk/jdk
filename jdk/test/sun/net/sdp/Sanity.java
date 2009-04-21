/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

import java.net.*;
import java.io.*;
import java.nio.channels.*;
import java.util.Enumeration;

/**
 * Sanity check Socket/ServerSocket and each of the stream-oriented channels
 * on each IP address plumbed to the network adapters.
 */

public class Sanity {
    public static void main(String[] args) throws Exception {
        Enumeration<NetworkInterface> nifs = NetworkInterface.getNetworkInterfaces();
        while (nifs.hasMoreElements()) {
            NetworkInterface ni = nifs.nextElement();
            Enumeration<InetAddress> addrs = ni.getInetAddresses();
            while (addrs.hasMoreElements()) {
                InetAddress addr = addrs.nextElement();
                test(addr);
            }
        }
    }

    static void test(InetAddress addr) throws Exception {
        System.out.println(addr.getHostAddress());

        // ServerSocketChannel.bind
        ServerSocketChannel ssc = ServerSocketChannel.open();
        try {
            ssc.bind(new InetSocketAddress(addr, 0));
            int port = ((InetSocketAddress)(ssc.getLocalAddress())).getPort();

            // SocketChannel.connect (implicit bind)
            SocketChannel client = SocketChannel.open();
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
            client = SocketChannel.open();
            try {
                client.bind(new InetSocketAddress(addr, 0))
                  .connect(new InetSocketAddress(addr, port));
                ssc.accept().close();
            } finally {
                client.close();
            }
        } finally {
            ssc.close();
        }

        // AsynchronousServerSocketChannel.bind
        AsynchronousServerSocketChannel server =
            AsynchronousServerSocketChannel.open();
        try {
            server.bind(new InetSocketAddress(addr, 0));
            int port = ((InetSocketAddress)(server.getLocalAddress())).getPort();

            // AsynchronousSocketChannel.connect (implicit bind)
            AsynchronousSocketChannel client = AsynchronousSocketChannel.open();
            try {
                client.connect(new InetSocketAddress(addr, port)).get();
                AsynchronousSocketChannel peer = server.accept().get();
                try {
                    testConnection(Channels.newOutputStream(client),
                                   Channels.newInputStream(peer));
                } finally {
                    peer.close();
                }
            } finally {
                client.close();
            }

            // AsynchronousSocketChannel.connect (explicit bind)
            client = AsynchronousSocketChannel.open();
            try {
                client.bind(new InetSocketAddress(addr, 0))
                  .connect(new InetSocketAddress(addr, port)).get();
                server.accept().get().close();
            } finally {
                client.close();
            }
        } finally {
            server.close();
        }

        // ServerSocket.bind
        ServerSocket ss = new ServerSocket();
        try {
            ss.bind(new InetSocketAddress(addr, 0));
            int port = ss.getLocalPort();

            // Socket.connect (implicit bind)
            Socket s = new Socket();
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
            s = new Socket();
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
