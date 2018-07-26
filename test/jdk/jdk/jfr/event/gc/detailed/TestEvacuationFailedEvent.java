/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.event.gc.detailed;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.Events;

/**
 * @test
 * @key jfr
 * @requires vm.hasJFR
 *
 * @library /test/lib /test/jdk
 * @requires vm.gc == "G1" | vm.gc == null
 *
 * @run main jdk.jfr.event.gc.detailed.TestEvacuationFailedEvent
 */
public class TestEvacuationFailedEvent {

    private final static String EVENT_SETTINGS_FILE = System.getProperty("test.src", ".") + File.separator + "evacuationfailed-testsettings.jfc";
    private final static String JFR_FILE = "TestEvacuationFailedEvent.jfr";
    private final static int BYTES_TO_ALLOCATE = 1024 * 512;

    public static void main(String[] args) throws Exception {
        String[] vmFlags = {"-XX:+UnlockExperimentalVMOptions", "-XX:-UseFastUnorderedTimeStamps",
            "-Xmx64m", "-Xmn60m", "-XX:-UseDynamicNumberOfGCThreads", "-XX:ParallelGCThreads=3",
            "-XX:MaxTenuringThreshold=0", "-Xlog:gc*=debug", "-XX:+UseG1GC"};

        if (!ExecuteOOMApp.execute(EVENT_SETTINGS_FILE, JFR_FILE, vmFlags, BYTES_TO_ALLOCATE)) {
            System.out.println("OOM happened in the other thread(not test thread). Skip test.");
            // Skip test, process terminates due to the OOME error in the different thread
            return;
        }

        List<RecordedEvent> events = RecordingFile.readAllEvents(Paths.get(JFR_FILE));

        Events.hasEvents(events);
        for (RecordedEvent event : events) {
            long objectCount = Events.assertField(event, "evacuationFailed.objectCount").atLeast(1L).getValue();
            long smallestSize = Events.assertField(event, "evacuationFailed.smallestSize").atLeast(1L).getValue();
            long firstSize = Events.assertField(event, "evacuationFailed.firstSize").atLeast(smallestSize).getValue();
            long totalSize = Events.assertField(event, "evacuationFailed.totalSize").atLeast(firstSize).getValue();
            Asserts.assertLessThanOrEqual(smallestSize * objectCount, totalSize, "smallestSize * objectCount <= totalSize");
        }
    }
}
