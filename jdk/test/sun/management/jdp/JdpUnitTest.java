/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.net.InetAddress;
import java.util.UUID;

import sun.management.jdp.JdpController;
import sun.management.jdp.JdpPacket;
import sun.management.jdp.JdpJmxPacket;
import sun.management.jdp.JdpException;

public class JdpUnitTest {


    static byte[] russian_name = {(byte)0xd0,(byte)0xbf,(byte)0xd1,(byte)0x80,(byte)0xd0,(byte)0xbe,(byte)0xd0,(byte)0xb2,
                                  (byte)0xd0,(byte)0xb5,(byte)0xd1,(byte)0x80,(byte)0xd0,(byte)0xba,(byte)0xd0,(byte)0xb0,
                                  (byte)0x20,(byte)0xd1,(byte)0x81,(byte)0xd0,(byte)0xb2,(byte)0xd1,(byte)0x8f,(byte)0xd0,
                                  (byte)0xb7,(byte)0xd0,(byte)0xb8,(byte)0x0a};

    /**
     * This test tests that complete packet is build correctly
     */
    public static void PacketBuilderTest()
        throws IOException, JdpException {

        /* Complete packet test */
        {
            JdpJmxPacket p1 = new JdpJmxPacket(UUID.randomUUID(), "fake://unit-test");
            p1.setMainClass("FakeUnitTest");
            p1.setInstanceName( new String(russian_name,"UTF-8"));
            byte[] b = p1.getPacketData();

            JdpJmxPacket p2 = new JdpJmxPacket(b);
            JdpDoSomething.printJdpPacket(p1);
            JdpDoSomething.compaireJdpPacketEx(p1, p2);
        }

        /*Missed field packet test*/
        {
            JdpJmxPacket p1 = new JdpJmxPacket(UUID.randomUUID(), "fake://unit-test");
            p1.setMainClass("FakeUnitTest");
            p1.setInstanceName(null);
            byte[] b = p1.getPacketData();

            JdpJmxPacket p2 = new JdpJmxPacket(b);
            JdpDoSomething.printJdpPacket(p1);
            JdpDoSomething.compaireJdpPacketEx(p1, p2);
        }

         System.out.println("OK: Test passed");

    }

    public static void startFakeDiscoveryService()
            throws IOException, JdpException {

        String discoveryPort = System.getProperty("com.sun.management.jdp.port");
        String discoveryAddress = System.getProperty("com.sun.management.jdp.address");
        InetAddress address = InetAddress.getByName(discoveryAddress);
        int port = Integer.parseInt(discoveryPort);
        JdpController.startDiscoveryService(address, port, "FakeDiscovery", "fake://unit-test");
    }

    public static void main(String[] args) {
        try {
            PacketBuilderTest();
            startFakeDiscoveryService();
            JdpDoSomething.doSomething();

        } catch (Throwable e) {
            e.printStackTrace();
            System.out.println("Test failed. unexpected error " + e);
        }
    }
}
