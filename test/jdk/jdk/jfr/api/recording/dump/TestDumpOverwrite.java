/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.api.recording.dump;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.SimpleEventHelper;
import jdk.test.lib.jfr.Events;

/**
 * @test
 * @summary Test that multiple dumps to the same file by ongoing recordings do not mangle data.
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm jdk.jfr.api.recording.dump.TestDumpOverwrite
 */
public class TestDumpOverwrite {
    private static Path DUMP_PATH = Paths.get(".", "rec_TestDumpOverwrite.jfr");
    public static void main(String[] args) throws Exception {
        Recording recording1 = new Recording();
        Recording recording2 = new Recording();
        SimpleEventHelper.enable(recording1, true);
        SimpleEventHelper.enable(recording2, true);
        recording1.setDestination(DUMP_PATH);
        recording2.setDestination(DUMP_PATH);

        int actualId = 0;
        recording1.start();
        SimpleEventHelper.createEvent(actualId++);
        recording2.start();
        SimpleEventHelper.createEvent(actualId++);
        // This is results in the initial write to the dump destination
        recording2.stop();
        SimpleEventHelper.createEvent(actualId++);
        recording2.close();
        SimpleEventHelper.createEvent(actualId++);
        // This should first wipe the data previously written by recording2.
        recording1.stop();
        recording1.close();

        Asserts.assertTrue(Files.exists(DUMP_PATH), "Recording file does not exist: " + DUMP_PATH);

        // Verify events are read in order without duplicates (otherwise chunks may be out of order).
        // If the dump file is not being overwritten correctly, we will see event ids: 1, 0, 1, 2, 3.
        int expectedId = 0;
        for (RecordedEvent event : RecordingFile.readAllEvents(DUMP_PATH)) {
            Events.assertField(event, "id").equal(expectedId++);
        }
        Asserts.assertTrue(expectedId == actualId, "incorrect number of events found");
    }
}
