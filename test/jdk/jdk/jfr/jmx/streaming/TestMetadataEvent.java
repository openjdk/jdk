/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jfr.jmx.streaming;

import java.lang.management.ManagementFactory;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.function.Function;

import jdk.jfr.Configuration;
import jdk.jfr.EventType;
import jdk.jfr.FlightRecorder;
import jdk.jfr.consumer.MetadataEvent;
import jdk.management.jfr.RemoteRecordingStream;

/**
 * @test
 * @key jfr
 * @summary Sanity tests RemoteRecordingStream::onMetadata
 * @requires vm.hasJFR
 * @library /test/lib /test/jdk
 * @run main/othervm jdk.jfr.jmx.streaming.TestMetadataEvent
 */
public class TestMetadataEvent {

    public static void main(String... args) throws Exception {
        var conn = ManagementFactory.getPlatformMBeanServer();
        var q = new ArrayBlockingQueue<MetadataEvent>(1);
        try (RemoteRecordingStream e = new RemoteRecordingStream(conn)) {
            e.onMetadata(q::offer);
            e.startAsync();
            MetadataEvent event = q.take();
            assertEventTypes(FlightRecorder.getFlightRecorder().getEventTypes(), event.getEventTypes());
            assertConfigurations(Configuration.getConfigurations(), event.getConfigurations());
        }
    }

    private static void assertEventTypes(List<EventType> expected, List<EventType> eventTypes) throws Exception {
        assertListProperty(expected, eventTypes, EventType::getName);
    }

    private static void assertConfigurations(List<Configuration> expected, List<Configuration> configurations) throws Exception {
        assertListProperty(expected, configurations, Configuration::getName);
    }

    private static <T, R> void assertListProperty(List<T> expected, List<T> result, Function<T, R> mapper) throws Exception {
        var a1 = new HashSet<R>();
        a1.addAll(expected.stream().map(mapper).toList());
        var a2 = new HashSet<R>();
        a2.addAll(result.stream().map(mapper).toList());
        if (!a1.equals(a2)) {
            throw new Exception("Result not as expected!\nexpected = " + a1 + "\nresult= " + a2);
        }
    }
}
