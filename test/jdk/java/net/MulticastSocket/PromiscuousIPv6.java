/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
 *

/*
 * @test
 * @bug 8210493
 * @requires os.family == "linux"
 * @library /test/lib
 * @build jdk.test.lib.NetworkConfiguration
 *        PromiscuousIPv6
 * @run main PromiscuousIPv6
 */
import jdk.test.lib.NetworkConfiguration;
import jtreg.SkippedException;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;

import static java.lang.System.out;

/*
 * This test was created as a copy of the Promiscuous test and adapted for
 * IPv6 node-local and link-local multicast addresses on Linux.
 */
public class PromiscuousIPv6 {

    static final int TIMEOUT =  5 * 1000; // 5 secs
    static int id = 1000;

    static void receive(DatagramSocket mc, boolean datagramExpected, int id)
            throws IOException
    {
        byte[] ba = new byte[100];
        DatagramPacket p = new DatagramPacket(ba, ba.length);
        try {
            mc.receive(p);
            int recvId = Integer.parseInt(
                    new String(p.getData(), 0, p.getLength(), "UTF-8"));
            if (datagramExpected) {
                if (recvId != id)
                    throw new RuntimeException("Unexpected id, got " + recvId
                                                       + ", expected: " + id);
                out.printf("Received message as expected, %s\n", p.getAddress());
            } else {
                throw new RuntimeException("Unexpected message received, "
                                                   + p.getAddress());
            }
        } catch (SocketTimeoutException e) {
            if (datagramExpected)
                throw new RuntimeException("Expected message not received, "
                                                   + e.getMessage());
            else
                out.printf("Message not received, as expected\n");
        }
    }

    static void test(InetAddress group1, InetAddress group2)
            throws IOException
    {
        try (MulticastSocket mc1 = new MulticastSocket(new InetSocketAddress(group1, 0));
             MulticastSocket mc2 = new MulticastSocket(new InetSocketAddress(group2, mc1.getLocalPort()));
             DatagramSocket ds = new DatagramSocket()) {

            final int port = mc1.getLocalPort();
            out.printf("Using port: %d\n", port);

            mc1.setSoTimeout(TIMEOUT);
            mc2.setSoTimeout(TIMEOUT);
            int nextId = id;
            byte[] msg = Integer.toString(nextId).getBytes("UTF-8");
            DatagramPacket p = new DatagramPacket(msg, msg.length);
            p.setAddress(group1);
            p.setPort(port);

            mc1.joinGroup(group1);
            out.printf("mc1 joined the MC group: %s\n", group1);
            mc2.joinGroup(group2);
            out.printf("mc2 joined the MC group: %s\n", group2);

            out.printf("Sending datagram to: %s/%d\n", group1, port);
            ds.send(p);

            // the packet should be received by mc1 only
            receive(mc1, true, nextId);
            receive(mc2, false, 0);

            nextId = ++id;
            msg = Integer.toString(nextId).getBytes("UTF-8");
            p = new DatagramPacket(msg, msg.length);
            p.setAddress(group2);
            p.setPort(port);

            out.printf("Sending datagram to: %s/%d\n", group2, port);
            ds.send(p);

            // the packet should be received by mc2 only
            receive(mc2, true, nextId);
            receive(mc1, false, 0);

            mc1.leaveGroup(group1);
            mc2.leaveGroup(group2);
        }
    }

    public static void main(String args[]) throws IOException {
        String os = System.getProperty("os.name");

        if (!os.equals("Linux")) {
            throw new SkippedException("This test should be run only on Linux");
        } else {
            String osVersion = System.getProperty("os.version");
            String prefix = "3.10.0";
            if (osVersion.startsWith(prefix)) {
                throw new SkippedException(
                        String.format("The behavior under test is known NOT to work on '%s' kernels", prefix));
            }
        }

        NetworkConfiguration.printSystemConfiguration(System.out);

        if (NetworkConfiguration.probe()
                                .ip6MulticastInterfaces()
                                .findAny()
                                .isEmpty()) {
            throw new SkippedException(
                    "No IPv6 interfaces that support multicast found");
        }

        InetAddress interfaceLocal1 = InetAddress.getByName("ff11::2.3.4.5");
        InetAddress interfaceLocal2 = InetAddress.getByName("ff11::6.7.8.9");
        test(interfaceLocal1, interfaceLocal2);

        InetAddress linkLocal1 = InetAddress.getByName("ff12::2.3.4.5");
        InetAddress linkLocal2 = InetAddress.getByName("ff12::6.7.8.9");
        test(linkLocal1, linkLocal2);
    }
}
