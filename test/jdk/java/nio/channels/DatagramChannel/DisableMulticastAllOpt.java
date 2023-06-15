/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8241800
 * @requires (os.family == "linux")
 * @library /test/lib
 * @build jdk.test.lib.NetworkConfiguration
 *        jdk.test.lib.Platform
 * @run main/othervm DisableMulticastAllOpt
 * @summary Disable IPV6_MULTICAST_ALL to prevent interference from all multicast groups
 */

import java.io.InputStream;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import jdk.test.lib.NetworkConfiguration;

public class DisableMulticastAllOpt {

    // Check if the kernel is 4.20 or greater

    static boolean is_4_20_orGreater() {
        try {
            Process p = new ProcessBuilder("uname", "-r").start();
            InputStream is = p.getInputStream();
            byte[] output = is.readAllBytes();
            is.close();
            String verstring = new String(output, StandardCharsets.UTF_8);
            System.out.println("Uname -r: " + verstring);
            String[] vernumbers = verstring.split("\\.");
            if (vernumbers.length == 0)
                return false;
            int first;
            if ((first = Integer.parseInt(vernumbers[0])) >= 5)
                return true;
            if (first < 4)
                return false;
            if (vernumbers.length < 2)
                return false;
            if (Integer.parseInt(vernumbers[1]) >= 20)
                return true;
            else
                return false;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static InetAddress getInetAddress(String name) {
        try {
            return InetAddress.getByName(name);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    private static final InetAddress all = getInetAddress("::0");

    private static final NetworkInterface nif;

    static {
        try {
            nif = NetworkConfiguration.probe()
                .ip6MulticastInterfaces()
                .findFirst()
                .orElse(null);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    // Join this group
    private static final InetAddress mc1 = getInetAddress("FF12::100");

    // Send to this group without joining
    private static final InetAddress mc2 = getInetAddress("FF12::101");

    static int getPortFromChannel(DatagramChannel channel) throws IOException {
        InetSocketAddress addr = (InetSocketAddress) channel.getLocalAddress();
        return addr.getPort();
    }

    static DatagramChannel getChannel(int port) throws IOException {
        DatagramChannel chan = DatagramChannel.open(StandardProtocolFamily.INET6);
        chan.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        chan.bind(new InetSocketAddress(all, port));
        return chan;
    }

   /**
    * Creates one sending channel and two receiving channels which join
    * two different multicast groups on the same port.
    * A packet is then sent to each destination
    *
    * We then attempt to read two packets off the first channel
    * The first packet should always be received and without this change
    * the second packet will also be received, which should not happen
    * going forward on a 4.20+ kernel
    */
    public static void main(String[] args) throws Exception {
        if (nif == null) {
            System.out.println("Suitable multicast interface not available");
            return;
        }
        if (!is_4_20_orGreater()) {
            System.out.println("Kernel < 4.20. Not running test");
            /* Just check that attempting to create a socket
             * does not throw an exception. The setsockopt()
             * should fail silently
             */
            var ch1 = getChannel(0);
            ch1.close();
            return;
        }
        System.out.println("Kernel >= 4.20. Running test");
        System.out.println("Using interface: " + nif.getName());
        var ch1 = getChannel(0);
        int port = getPortFromChannel(ch1);
        var ch2 = getChannel(port);
        InetSocketAddress dest1 = new InetSocketAddress(mc1, port);
        InetSocketAddress dest2 = new InetSocketAddress(mc2, port);
        ch1.join(mc1, nif);
        ch2.join(mc2, nif);
        var sender = getChannel(0);
        sender.setOption(StandardSocketOptions.IP_MULTICAST_IF, nif);
        ByteBuffer txbuf = ByteBuffer.wrap("Hello world".getBytes(StandardCharsets.US_ASCII));
        ByteBuffer rxbuf = ByteBuffer.allocate(64);
        sender.send(txbuf, dest1);
        var addr = ch1.receive(rxbuf);
        System.out.printf("First read from %s\n", addr.toString());
        rxbuf.flip();
        System.out.printf("First read received %d bytes\n", rxbuf.remaining());
        txbuf = ByteBuffer.wrap("Goodbye world".getBytes(StandardCharsets.US_ASCII));
        rxbuf.clear();
        sender.send(txbuf, dest2);
        Selector selector = Selector.open();
        ch1.configureBlocking(false);
        ch1.register(selector, SelectionKey.OP_READ);
        int ret = selector.select(2000);
        if (ret == 0) {
            System.out.println("No packet received. Test succeeded");
        } else {
            throw new RuntimeException("Packet received. Test failed");
        }
        ch1.close();
        ch2.close();
        sender.close();
    }
}
