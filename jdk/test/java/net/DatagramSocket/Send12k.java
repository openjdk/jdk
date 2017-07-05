/*
 * Copyright (c) 2001, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4094894
 * @summary On W95/W98 it's not possible to send a datagram >12k
 *          via the loopback address.
 */

import java.net.*;
import java.io.*;

public class Send12k {

    static final int SEND_SIZE = 16 * 1024;

    public static void main(String args[]) throws Exception {

        DatagramSocket s1 = new DatagramSocket();
        DatagramSocket s2 = new DatagramSocket();

        byte b1[] = new byte[ SEND_SIZE ];
        DatagramPacket p1 = new DatagramPacket(b1, 0, b1.length,
                                               InetAddress.getLocalHost(),
                                               s2.getLocalPort());
        boolean sendOkay = true;
        try {
            s1.send(p1);
        } catch (SocketException e) {
            /*
             * Prior to merlin a send of > 12k to loopback address
             * would fail silently.
             */
            sendOkay = false;
        }

        if (sendOkay) {
            byte b2[] = new byte[ SEND_SIZE * 2];
            DatagramPacket p2 = new DatagramPacket( b2, SEND_SIZE * 2 );
            s2.setSoTimeout(2000);

            try {
                s2.receive(p1);
            } catch (InterruptedIOException ioe) {
                throw new Exception("Datagram not received within timeout");
            }

            if (p1.getLength() != SEND_SIZE) {
                throw new Exception("Received datagram incorrect size");
            }
        }

    }

}
