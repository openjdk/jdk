/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4849277
 * @summary Test DatagramChannel send while connected
 * @author Mike McCloskey
 */

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;

public class ConnectedSend {

    public static void main(String[] args) throws Exception {
        test1();
        test2();
    }

    // Check if DatagramChannel.send while connected can include
    // address without throwing
    private static void test1() throws Exception {

        DatagramChannel sndChannel = DatagramChannel.open();
        sndChannel.socket().bind(null);
        InetSocketAddress sender = new InetSocketAddress(
            InetAddress.getLocalHost(),
            sndChannel.socket().getLocalPort());

        DatagramChannel rcvChannel = DatagramChannel.open();
        rcvChannel.socket().bind(null);
        InetSocketAddress receiver = new InetSocketAddress(
            InetAddress.getLocalHost(),
            rcvChannel.socket().getLocalPort());

        rcvChannel.connect(sender);
        sndChannel.connect(receiver);

        ByteBuffer bb = ByteBuffer.allocate(256);
        bb.put("hello".getBytes());
        bb.flip();
        int sent = sndChannel.send(bb, receiver);
        bb.clear();
        rcvChannel.receive(bb);
        bb.flip();
        CharBuffer cb = Charset.forName("US-ASCII").newDecoder().decode(bb);
        if (!cb.toString().startsWith("h"))
            throw new RuntimeException("Test failed");

        rcvChannel.close();
        sndChannel.close();
    }

    // Check if the datagramsocket adaptor can send with a packet
    // that has not been initialized with an address; the legacy
    // datagram socket will send in this case
    private static void test2() throws Exception {
        DatagramChannel sndChannel = DatagramChannel.open();
        sndChannel.socket().bind(null);
        InetSocketAddress sender = new InetSocketAddress(
            InetAddress.getLocalHost(),
            sndChannel.socket().getLocalPort());

        DatagramChannel rcvChannel = DatagramChannel.open();
        rcvChannel.socket().bind(null);
        InetSocketAddress receiver = new InetSocketAddress(
            InetAddress.getLocalHost(),
            rcvChannel.socket().getLocalPort());

        rcvChannel.connect(sender);
        sndChannel.connect(receiver);

        byte b[] = "hello".getBytes("UTF-8");
        DatagramPacket pkt = new DatagramPacket(b, b.length);
        sndChannel.socket().send(pkt);

        ByteBuffer bb = ByteBuffer.allocate(256);
        rcvChannel.receive(bb);
        bb.flip();
        CharBuffer cb = Charset.forName("US-ASCII").newDecoder().decode(bb);
        if (!cb.toString().startsWith("h"))
            throw new RuntimeException("Test failed");

        // Check that the pkt got set with the target address;
        // This is legacy behavior
        if (!pkt.getSocketAddress().equals(receiver))
            throw new RuntimeException("Test failed");

        rcvChannel.close();
        sndChannel.close();
    }
}
