/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, SAP SE. All rights reserved.
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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.util.List;
import java.util.stream.Collectors;

import jdk.test.lib.NetworkConfiguration;
import jdk.test.lib.net.IPSupport;

/*
 * @test
 * @bug 8390212
 * @summary MulticastSocket over a dual-stack IPv6 delegate must be able to
 *          join an IPv4 multicast group on every platform where the JDK's
 *          capability flags advertise support (canIPv6SocketJoinIPv4Group
 *          OR canJoin6WithIPv4Group). The IllegalArgumentException("IPv6
 *          socket cannot join IPv4 multicast group") must be reserved for
 *          the case where neither path is viable.
 * @library /test/lib
 * @build jdk.test.lib.NetworkConfiguration
 *        jdk.test.lib.Platform
 * @run main/othervm JoinIPv4GroupOnIPv6Socket
 */
public class JoinIPv4GroupOnIPv6Socket {

    public static void main(String[] args) throws Exception {
        IPSupport.throwSkippedExceptionIfNonOperational();
        if (!IPSupport.hasIPv6()) {
            System.out.println("IPv6 not available; test not meaningful");
            return;
        }

        InetAddress group = InetAddress.getByName("224.0.0.251");
        SocketAddress groupSA = new InetSocketAddress(group, 0);

        NetworkConfiguration nc = NetworkConfiguration.probe();
        List<NetworkInterface> interfaces =
            nc.ip4MulticastInterfaces().collect(Collectors.toList());
        if (interfaces.isEmpty()) {
            System.out.println("No IPv4 multicast-capable interfaces; nothing to test");
            return;
        }

        int joined = 0;
        for (NetworkInterface nif : interfaces) {
            try (MulticastSocket s = new MulticastSocket()) {
                s.joinGroup(groupSA, nif);
                s.leaveGroup(groupSA, nif);
                System.out.println("OK: joined " + group + " on " + nif.getName());
                joined++;
            } catch (IllegalArgumentException iae) {
                throw new RuntimeException(
                    "Unexpected IllegalArgumentException on " + nif.getName()
                    + ": " + iae.getMessage(), iae);
            }
        }

        if (joined == 0) {
            throw new RuntimeException(
                "No interface accepted the join; expected at least one success");
        }
    }
}
