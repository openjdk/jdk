/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.jfr.event.runtime;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.test.lib.Asserts;
import jdk.test.lib.Platform;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.averagingLong;
import static java.util.stream.Collectors.groupingBy;

/**
 * @test
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib
 *
 * @run main/othervm jdk.jfr.event.runtime.TestNetworkUtilizationEvent
 */
public class TestNetworkUtilizationEvent {

    private static final long packetSendCount = 100;

    public static void main(String[] args) throws Throwable {
        testSimple();
    }

    static void testSimple() throws Throwable {

        Instant start = Instant.now();
        Recording recording = new Recording();
        recording.enable(EventNames.NetworkUtilization);
        recording.start();

        DatagramSocket socket = new DatagramSocket();
        String msg = "hello!";
        byte[] buf = msg.getBytes();

        // Send a few packets both to the loopback address as well to an external
        DatagramPacket packet = new DatagramPacket(buf, buf.length, InetAddress.getLoopbackAddress(), 12345);
        for (int i = 0; i < packetSendCount; ++i) {
            socket.send(packet);
        }
        packet = new DatagramPacket(buf, buf.length, InetAddress.getByName("10.0.0.0"), 12345);
        for (int i = 0; i < packetSendCount; ++i) {
            socket.send(packet);
        }

        // Now there should have been traffic on at least two different interfaces
        recording.stop();
        Duration runtime = Duration.between(start, Instant.now());
        List<RecordedEvent> events = Events.fromRecording(recording);

        // Calculate the average write rate for each interface
        Map<String, Double> writeRates = events.stream()
                .collect(groupingBy(e -> Events.assertField(e, "networkInterface").getValue(),
                         averagingLong(e -> Events.assertField(e, "writeRate").getValue())));

        // Our test packets should have generated at least this much traffic per second
        long expectedTraffic = (buf.length * packetSendCount) / Math.max(1, runtime.toSeconds());

        // Count the number of interfaces that have seen at least our test traffic
        long interfacesWithTraffic = writeRates.values().stream()
                .filter(d -> d >= expectedTraffic)
                .count();

        if (Platform.isWindows() || Platform.isSolaris()) {
            // Windows and Solaris do not track statistics for the loopback interface
            Asserts.assertGreaterThanOrEqual(writeRates.size(), 1);
            Asserts.assertGreaterThanOrEqual(interfacesWithTraffic, Long.valueOf(1));
        } else {
            Asserts.assertGreaterThanOrEqual(writeRates.size(), 2);
            Asserts.assertGreaterThanOrEqual(interfacesWithTraffic, Long.valueOf(2));
        }
    }
}
