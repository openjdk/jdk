/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.event.io;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.test.lib.Utils;
import jdk.test.lib.jfr.Events;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

/**
 * @test
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib /test/jdk
 * @run main/othervm jdk.jfr.event.io.TestAsynchronousFileChannelEvents
 */
public class TestAsynchronousFileChannelEvents {

    public static void main(String[] args) throws Throwable {
        File tmp = Utils.createTempFile("TestAsynchronousFileChannelEvents", ".tmp").toFile();
        String s = "unremarkable data";
        ByteBuffer data = ByteBuffer.allocate(s.length());
        data.put(s.getBytes());

        try (Recording recording = new Recording();
             AsynchronousFileChannel ch = AsynchronousFileChannel.open(tmp.toPath(), READ, WRITE)) {

            List<IOEvent> expectedEvents = new ArrayList<>();
            recording.enable(IOEvent.EVENT_FILE_FORCE).withThreshold(Duration.ofMillis(0));
            recording.start();

            data.flip();
            ch.write(data, 0).get();

            // test force(boolean)
            ch.force(true);
            expectedEvents.add(IOEvent.createFileForceEvent(tmp));

            recording.stop();
            List<RecordedEvent> events = Events.fromRecording(recording);
            IOHelper.verifyEqualsInOrder(events, expectedEvents);
        }
    }
}
