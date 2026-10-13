/*
 * Copyright (c) 2013, 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.event.os;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingStream;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;

/**
 * @test
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm jdk.jfr.event.os.TestCPULoad
 */
public class TestCPULoad {
    private final static String EVENT_NAME = EventNames.CPULoad;

    public static void main(String... args) throws Exception {
        try (RecordingStream rs = new RecordingStream()) {
            BlockingQueue<RecordedEvent> cpuEvent = new ArrayBlockingQueue<>(1);
            rs.setReuse(false);
            rs.enable(EVENT_NAME).withPeriod(Duration.ofMillis(100));
            rs.onEvent(cpuEvent::offer);
            rs.startAsync();
            RecordedEvent event = cpuEvent.take();
            System.out.println(event);
            Events.assertField(event, "jvmUser").atLeast(0.0f).atMost(1.0f);
            Events.assertField(event, "jvmSystem").atLeast(0.0f).atMost(1.0f);
            Events.assertField(event, "machineTotal").atLeast(0.0f).atMost(1.0f);
        }
    }
}