/*
 * Copyright 2007 Sun Microsystems, Inc.  All Rights Reserved.
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

/*
 * @test
 * @bug 4742177
 * @summary Re-test IPv6 (and specifically MulticastSocket) with latest Linux & USAGI code
 */
import java.net.*;
import java.util.concurrent.*;
import java.util.*;


public class SetOutgoingIf {
    private static int PORT = 9001;
    private static String osname;

    static boolean isWindows() {
        if (osname == null)
            osname = System.getProperty("os.name");
        return osname.contains("Windows");
    }

    private static boolean hasIPv6() throws Exception {
        List<NetworkInterface> nics = Collections.list(
                                        NetworkInterface.getNetworkInterfaces());
        for (NetworkInterface nic : nics) {
            List<InetAddress> addrs = Collections.list(nic.getInetAddresses());
            for (InetAddress addr : addrs) {
                if (addr instanceof Inet6Address)
                    return true;
            }
        }

        return false;
    }

    public static void main(String[] args) throws Exception {
        if (isWindows()) {
            System.out.println("The test only run on non-Windows OS. Bye.");
            return;
        }

        if (!hasIPv6()) {
            System.out.println("No IPv6 available. Bye.");
            return;
        }

        // We need 2 or more network interfaces to run the test
        //
        List<NetworkInterface> nics = new ArrayList<NetworkInterface>();
        for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            // we should use only network interfaces with multicast support which are in "up" state
            if (!nic.isLoopback() && nic.supportsMulticast() && nic.isUp())
                nics.add(nic);
        }
        if (nics.size() <= 1) {
            System.out.println("Need 2 or more network interfaces to run. Bye.");
            return;
        }

        // We will send packets to one ipv4, one ipv4-mapped, and one ipv6
        // multicast group using each network interface :-
        //      224.1.1.1        --|
        //      ::ffff:224.1.1.2 -----> using network interface #1
        //      ff02::1:1        --|
        //      224.1.2.1        --|
        //      ::ffff:224.1.2.2 -----> using network interface #2
        //      ff02::1:2        --|
        // and so on.
        //
        List<InetAddress> groups = new ArrayList<InetAddress>();
        for (int i = 0; i < nics.size(); i++) {
            InetAddress groupv4 = InetAddress.getByName("224.1." + (i+1) + ".1");
            InetAddress groupv4mapped = InetAddress.getByName("::ffff:224.1." + (i+1) + ".2");
            InetAddress groupv6 = InetAddress.getByName("ff02::1:" + (i+1));
            groups.add(groupv4);
            groups.add(groupv4mapped);
            groups.add(groupv6);

            // use a separated thread to send to those 3 groups
            Thread sender = new Thread(new Sender(nics.get(i), groupv4, groupv4mapped, groupv6, PORT));
            sender.setDaemon(true); // we want sender to stop when main thread exits
            sender.start();
        }

        // try to receive on each group, then check if the packet comes
        // from the expected network interface
        //
        byte[] buf = new byte[1024];
        for (InetAddress group : groups) {
        MulticastSocket mcastsock = new MulticastSocket(PORT);
        mcastsock.setSoTimeout(5000);   // 5 second
            DatagramPacket packet = new DatagramPacket(buf, 0, buf.length);

            mcastsock.joinGroup(new InetSocketAddress(group, PORT), nics.get(groups.indexOf(group) / 3));

            try {
                mcastsock.receive(packet);
            } catch (Exception e) {
                // test failed if any exception
                throw new RuntimeException(e);
            }

            // now check which network interface this packet comes from
            NetworkInterface from = NetworkInterface.getByInetAddress(packet.getAddress());
            NetworkInterface shouldbe = nics.get(groups.indexOf(group) / 3);
            if (!from.equals(shouldbe)) {
                System.out.println("Packets on group "
                                    + group + " should come from "
                                    + shouldbe.getName() + ", but came from "
                                    + from.getName());
                //throw new RuntimeException("Test failed.");
            }

            mcastsock.leaveGroup(new InetSocketAddress(group, PORT), nics.get(groups.indexOf(group) / 3));
        }
    }
}

class Sender implements Runnable {
    private NetworkInterface nic;
    private InetAddress group1;
    private InetAddress group2;
    private InetAddress group3;
    private int port;

    public Sender(NetworkInterface nic,
                    InetAddress groupv4, InetAddress groupv4mapped, InetAddress groupv6,
                    int port) {
        this.nic = nic;
        group1 = groupv4;
        group2 = groupv4mapped;
        group3 = groupv6;
        this.port = port;
    }

    public void run() {
        try {
            MulticastSocket mcastsock = new MulticastSocket();
            mcastsock.setNetworkInterface(nic);

            byte[] buf = "hello world".getBytes();
            DatagramPacket packet1 = new DatagramPacket(buf, buf.length,
                                        new InetSocketAddress(group1, port));
            DatagramPacket packet2 = new DatagramPacket(buf, buf.length,
                                        new InetSocketAddress(group2, port));
            DatagramPacket packet3 = new DatagramPacket(buf, buf.length,
                                        new InetSocketAddress(group3, port));

            for (;;) {
                mcastsock.send(packet1);
                mcastsock.send(packet2);
                mcastsock.send(packet3);

                Thread.currentThread().sleep(1000);   // sleep 1 second
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
