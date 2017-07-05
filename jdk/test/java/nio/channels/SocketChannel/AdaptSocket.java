/*
 * Copyright (c) 2001, 2002, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Unit test for socket-channel adaptors
 * @library ..
 */

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;


public class AdaptSocket {

    static java.io.PrintStream out = System.out;

    static final int ECHO_PORT = 7;
    static final int DAYTIME_PORT = 13;
    static final String REMOTE_HOST = TestUtil.HOST;
    static final String VERY_REMOTE_HOST = TestUtil.FAR_HOST;

    static void test(String hn, int timeout, boolean shouldTimeout)
        throws Exception
    {
        out.println();

        InetSocketAddress isa
            = new InetSocketAddress(InetAddress.getByName(hn),
                                    DAYTIME_PORT);
        SocketChannel sc = SocketChannel.open();
        Socket so = sc.socket();
        out.println("opened: " + so);
        out.println("        " + sc);

        //out.println("opts:   " + sc.options());
        so.setTcpNoDelay(true);
        //so.setTrafficClass(SocketOpts.IP.TOS_THROUGHPUT);
        so.setKeepAlive(true);
        so.setSoLinger(true, 42);
        so.setOOBInline(true);
        so.setReceiveBufferSize(512);
        so.setSendBufferSize(512);
        //out.println("        " + sc.options());

        if (timeout == 0)
            so.connect(isa);
        else {
            try {
                so.connect(isa, timeout);
            } catch (SocketTimeoutException x) {
                if (shouldTimeout) {
                    out.println("Connection timed out, as expected");
                    return;
                } else {
                    throw x;
                }
            }
        }
        out.println("connected: " + so);
        out.println("           " + sc);
        byte[] bb = new byte[100];
        int n = so.getInputStream().read(bb);
        String s = new String(bb, 0, n - 2, "US-ASCII");
        out.println(isa + " says: \"" + s + "\"");
        so.shutdownInput();
        out.println("ishut: " + sc);
        so.shutdownOutput();
        out.println("oshut: " + sc);
        so.close();
        out.println("closed: " + so);
        out.println("        " + sc);
    }

    static String dataString = "foo\r\n";

    static void testRead(Socket so, boolean shouldTimeout)
        throws Exception
    {
        String data = "foo\r\n";
        so.getOutputStream().write(dataString.getBytes("US-ASCII"));
        InputStream is = so.getInputStream();
        try {
            byte[] b = new byte[100];
            int n = is.read(b);
            if (n != 5)
                throw new Exception("Incorrect number of bytes read: " + n);
            if (!dataString.equals(new String(b, 0, n, "US-ASCII")))
                throw new Exception("Incorrect data read: " + n);
        } catch (SocketTimeoutException x) {
            if (shouldTimeout) {
                out.println("Read timed out, as expected");
                return;
            }
            throw x;
        }
    }

    static void testRead(String hn, int timeout, boolean shouldTimeout)
        throws Exception
    {
        out.println();

        InetSocketAddress isa
            = new InetSocketAddress(InetAddress.getByName(hn), ECHO_PORT);
        SocketChannel sc = SocketChannel.open();
        sc.connect(isa);
        Socket so = sc.socket();
        out.println("connected: " + so);
        out.println("           " + sc);

        if (timeout > 0)
            so.setSoTimeout(timeout);
        out.println("timeout: " + so.getSoTimeout());

        testRead(so, shouldTimeout);
        if (!TestUtil.onME())
            for (int i = 0; i < 4; i++)
                testRead(so, shouldTimeout);

        sc.close();
    }

    public static void main(String[] args) throws Exception {

        test(REMOTE_HOST, 0, false);
        test(REMOTE_HOST, 1000, false);
        test(VERY_REMOTE_HOST, 10, true);

        testRead(REMOTE_HOST, 0, false);
        testRead(REMOTE_HOST, 8000, false);
        testRead(VERY_REMOTE_HOST, 10, true);

    }

}
