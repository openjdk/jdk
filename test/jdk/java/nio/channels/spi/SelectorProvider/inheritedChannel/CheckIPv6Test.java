/*
 * Copyright (c) 2003, 2020, Oracle and/or its affiliates. All rights reserved.
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

/*
 *
 *
 * Used in conjunction to EchoService to test System.inheritedChannel().
 *
 * The first test is the TCP echo test. A service is launched with a TCP
 * socket and a TCP message is sent to the service. The test checks that
 * the message is correctly echoed.
 *
 * The second test is a UDP echo test. A service is launched with a UDP
 * socket and a UDP packet is sent to the service. The test checks that
 * the packet is correctly echoed.
 *
 */

import jdk.test.lib.Utils;

import java.io.IOException;
import java.net.DatagramPacket;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Random;

public class CheckIPv6Test {

    private static int failures = 0;

    private static String SERVICE = "CheckIPv6Service";

    public static void main(String args[]) throws IOException {

        if (!CheckIPv6Service.isIPv6Available()) {
            System.out.println("IPv6 not available. Test skipped.");
            return;
        }

        try {
            EchoTest.TCPEchoTest(SERVICE);
            System.out.println("IPv6 test passed.");
        } catch (Exception x) {
            System.err.println(x);
            failures++;
        }

        if (failures > 0) {
            throw new RuntimeException("Test failed - see log for details");
        }
    }

}
