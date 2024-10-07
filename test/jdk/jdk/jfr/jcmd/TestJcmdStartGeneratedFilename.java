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

package jdk.jfr.jcmd;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;

import jdk.jfr.FlightRecorder;
import jdk.jfr.FlightRecorderListener;
import jdk.jfr.Recording;
import jdk.jfr.RecordingState;

import jdk.test.lib.process.OutputAnalyzer;

/**
 * @test
 * @summary Verify that a filename is generated
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib /test/jdk
 * @run main/othervm jdk.jfr.jcmd.TestJcmdStartGeneratedFilename
 */
public class TestJcmdStartGeneratedFilename {

    public static void main(String[] args) throws Exception {
        CountDownLatch recordingClosed = new CountDownLatch(1);
        FlightRecorder.addListener(new FlightRecorderListener() {
            public void recordingStateChanged(Recording recording) {
                if (recording.getState() == RecordingState.CLOSED) {
                    recordingClosed.countDown();
                }
            }
        });
        Path directory = Paths.get(".", "recordings");
        Files.createDirectories(directory);
        JcmdHelper.jcmd("JFR.start", "duration=1s", "filename=" + directory);
        recordingClosed.await();
        for (Path path : Files.list(directory).toList()) {
            String file = path.toString();
            System.out.println("Found file: " + file);
            if (file.endsWith(".jfr") && file.contains("hotspot-")) {
                return;
            }
        }
        throw new Exception("Expected dump file on the format hotspot-...jfr");
    }
}
