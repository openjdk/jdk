/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.event.sampling;

import java.io.File;
import java.nio.file.Paths;
import java.time.Duration;

import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

/*
 * @test
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 * @run main/native jdk.jfr.event.sampling.TestNative
 */
public class TestNative {

    public final static String EVENT_SETTINGS_FILE = System.getProperty("test.src", ".") + File.separator + "sampling.jfc";
    public final static String JFR_DUMP = "samples.jfr";
    public final static String EXCEPTION = "No native samples found";
    public final static String NATIVE_EVENT = EventNames.NativeMethodSample;
    public static Recording recording;

    public static native void longTime();

    public static void main(String[] args) throws Exception {
        String lib = System.getProperty("test.nativepath");
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(true, "-Djava.library.path=" + lib, "jdk.jfr.event.sampling.TestNative$Test");

        OutputAnalyzer output = ProcessTools.executeProcess(pb);
        output.shouldHaveExitValue(0);
        output.stdoutShouldNotContain("No native samples found");
    }

    static class Test {
        public static void main(String[] args) throws Exception {
            System.loadLibrary("TestNative");
            FlightRecorder.getFlightRecorder();
            recording = new Recording();
            recording.setToDisk(true);
            recording.setDestination(Paths.get(JFR_DUMP));
            recording.enable(NATIVE_EVENT).withPeriod(Duration.ofMillis(10));
            recording.start();

            longTime();

            recording.stop();
            recording.close();

            try (RecordingFile rf = new RecordingFile(Paths.get(JFR_DUMP))) {
                while (rf.hasMoreEvents()) {
                    RecordedEvent re = rf.readEvent();
                    if (re.getEventType().getName().equals(NATIVE_EVENT)) {
                        return;
                    }
                }
            }

            throw new Exception("No native samples found");
        }
    }
}
