/*
 * Copyright (c) 1997, 1998, Oracle and/or its affiliates. All rights reserved.
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
 *
 * @bug 4091803
 *
 * @summary this tests that the constructor of DatagramPacket rejects
 * bogus arguments properly.
 *
 * @author Benjamin Renaud
 */
import java.io.*;
import java.net.*;
import java.util.*;

public class Constructor {

    public static void main(String[] args) throws Exception {
        testNullPacket();
        testNegativeBufferLength();
        testPacketLengthTooLarge();
        testNegativePortValue();
        testPortValueTooLarge();
        testSimpleConstructor();
        testFullConstructor();
        System.err.println("all passed!");
    }

    static void testNullPacket() throws Exception {
        boolean error = true;
        try {
            new DatagramPacket(null, 100);
        } catch (NullPointerException e) {
            /* correct exception */
            error = false;
        }
        if (error) {
            throw new RuntimeException("test 1 failed.");
        }
    }

    static void testNegativeBufferLength() throws Exception {
        boolean error = true;
        byte[] buf = new byte[128];
        try {
            /* length lesser than buffer length */
            new DatagramPacket(buf, -128);
        } catch (IllegalArgumentException e) {
            /* correct exception */
            error = false;
        }
        if (error) {
            throw new RuntimeException("test 2 failed.");
        }
    }

    static void testPacketLengthTooLarge() throws Exception {
        boolean error = true;
        byte[] buf = new byte[128];
        try {
            /* length greater than buffer length */
            new DatagramPacket(buf, 256);
        } catch (IllegalArgumentException e) {
            /* correct exception */
            error = false;
        }
        if (error) {
            throw new RuntimeException("test 3 failed.");
        }
    }

    static void testNegativePortValue() throws Exception {
        boolean error = true;
        byte[] buf = new byte[128];
        InetAddress host = InetAddress.getLocalHost();
        try {
            /* negative port */
            new DatagramPacket(buf, 100, host, -1);
        } catch (IllegalArgumentException e) {
            /* correct exception */
            error = false;
        }
        if (error) {
            throw new RuntimeException("test 5 failed.");
        }
    }

    static void testPortValueTooLarge() throws Exception {
        boolean error = true;
        byte[] buf = new byte[256];
        InetAddress address = InetAddress.getLocalHost();
        try {
            /* invalid port value */
            new DatagramPacket(buf, 256, address, Integer.MAX_VALUE);
        } catch (IllegalArgumentException e) {
            /* correct exception */
            error = false;
        }
        if (error) {
            throw new RuntimeException("test 6 failed.");
        }
    }

    static void testSimpleConstructor() {
        byte[] buf = new byte[128];
        int offset = 10;
        int length = 50;
        DatagramPacket packet = new DatagramPacket(buf, offset, length);
        if (packet.getData() != buf || packet.getOffset() != offset ||
               packet.getLength() != length) {
            throw new RuntimeException("simple constructor failed");
        }
    }

    static void testFullConstructor() throws Exception {
        byte[] buf = new byte[128];
        int offset = 10;
        int length = 50;
        InetAddress address = InetAddress.getLocalHost();
        int port = 8080;
        DatagramPacket packet = new DatagramPacket(buf, offset, length,
                                                   address, port);
        if (packet.getData() != buf || packet.getOffset() != offset ||
            packet.getLength() != length ||
            packet.getAddress() != address ||
            packet.getPort() != port) {
            throw new RuntimeException("full constructor failed");
        }
    }
}
