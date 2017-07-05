/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ProtocolFamily;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import sun.management.jdp.JdpException;
import sun.management.jdp.JdpJmxPacket;
import sun.management.jdp.JdpPacketReader;

public class JdpClient {

    private static class PacketListener implements Runnable {

        private static final int BUFFER_LENGTH = 4096;
        private final DatagramChannel channel;
        private static int maxPacketCount = 1;
        private static int maxEmptyPacketCount = 10;

        private void get(Map<?,?> map, String key)
            throws JdpException {

            if (map.get(key) == null) {
                  throw new JdpException("Test failed, packet field " + key + " missed");
            }
        }

        private void checkFieldPresence(JdpJmxPacket p)
            throws IOException, JdpException {

            byte[] b = p.getPacketData();

            JdpPacketReader reader = new JdpPacketReader(b);
            Map<String,String> pMap = reader.getDiscoveryDataAsMap();

            get(pMap, JdpJmxPacket.UUID_KEY);
            get(pMap, JdpJmxPacket.MAIN_CLASS_KEY);
            get(pMap, JdpJmxPacket.JMX_SERVICE_URL_KEY);
            // get(pMap, JdpJmxPacket.INSTANCE_NAME_KEY);
            get(pMap, JdpJmxPacket.PROCESS_ID_KEY);
            get(pMap, JdpJmxPacket.BROADCAST_INTERVAL_KEY);
            get(pMap, JdpJmxPacket.RMI_HOSTNAME_KEY);
        }


        PacketListener(DatagramChannel channel) {
            this.channel = channel;
        }

        @java.lang.Override
        public void run() {
            try {
                Selector sel;
                sel = Selector.open();
                channel.configureBlocking(false);
                channel.register(sel, SelectionKey.OP_READ);
                ByteBuffer buf = ByteBuffer.allocate(1024);

                int count = 1;
                int emptyPacketsCount = 1;

                try {
                    while (true) {

                        // Use tcpdump -U -w - -s 1400 -c 2 -vv port 7095
                        // to verify that correct packet being sent
                        sel.selectedKeys().clear();
                        buf.rewind();

                        sel.select(10 * 1000);
                        channel.receive(buf);

                        if (buf.position() == 0 ){
                            if (JdpDoSomething.getVerbose()){
                                System.err.println("Empty packet received");
                            }
                            if (++emptyPacketsCount > maxEmptyPacketCount){
                                throw new RuntimeException("Test failed, maxEmptyPacketCount reached");
                            }

                            continue;
                        }

                        buf.flip();
                        byte[] dgramData = new byte[buf.remaining()];
                        buf.get(dgramData);
                        try {
                            JdpJmxPacket packet = new JdpJmxPacket(dgramData);
                            JdpDoSomething.printJdpPacket(packet);
                            checkFieldPresence(packet);
                            if(++count > maxPacketCount){
                                   break;
                            }
                        } catch (JdpException e) {
                            e.printStackTrace();
                            throw new RuntimeException("Test failed");
                        }

                    }

                    System.out.println("OK: Test passed");

                } finally {
                    sel.close();
                    channel.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException("Test failed");
            }
        }
    }

    public static void main(String[] args) {
        try {
            String discoveryPort = System.getProperty("com.sun.management.jdp.port");
            String discoveryAddress = System.getProperty("com.sun.management.jdp.address");
            if (discoveryAddress == null || discoveryPort == null) {
                System.out.println("Test failed. address and port must be specified");
                return;
            }

            int port = Integer.parseInt(discoveryPort);
            InetAddress address = InetAddress.getByName(discoveryAddress);


            ProtocolFamily family = (address instanceof Inet6Address)
                    ? StandardProtocolFamily.INET6 : StandardProtocolFamily.INET;

            DatagramChannel channel;

            channel = DatagramChannel.open(family);
            channel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
            channel.bind(new InetSocketAddress(port));

            Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface interf : Collections.list(nets)) {
                if (interf.supportsMulticast()) {
                    try {
                        channel.join(address, interf);
                    } catch (IOException e) {
                        // Skip not configured interfaces
                    }
                }
            }

            PacketListener listener = new PacketListener(channel);
            new Thread(listener, "Jdp Client").start();

        } catch (RuntimeException e){
            System.out.println("Test failed.");
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Test failed. unexpected error " + e);
        }
    }
}
