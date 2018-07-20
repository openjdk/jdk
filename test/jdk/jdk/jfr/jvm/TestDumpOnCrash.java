/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jfr.jvm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import jdk.internal.misc.Unsafe;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import jdk.test.lib.Asserts;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

/**
 * @test
 * @key jfr
 * @summary Verifies that data associated with a running recording can be evacuated to an hs_err_pidXXX.jfr when the VM crashes
 * @requires vm.hasJFR
 *
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 *          jdk.jfr
 *
 * @run main/othervm --add-exports=java.base/jdk.internal.misc=ALL-UNNAMED jdk.jfr.jvm.TestDumpOnCrash
 */
public class TestDumpOnCrash {

    private static final CharSequence LOG_FILE_EXTENSION = ".log";
    private static final CharSequence JFR_FILE_EXTENSION = ".jfr";

    static class Crasher {
        public static void main(String[] args) {
            Unsafe.getUnsafe().putInt(0L, 0);
        }
    }

    public static void main(String[] args) throws Exception {
        processOutput(runProcess());
    }

    private static OutputAnalyzer runProcess() throws Exception {
        return new OutputAnalyzer(
            ProcessTools.createJavaProcessBuilder(true,
                "-Xmx64m",
                "-Xint",
                "-XX:-TransmitErrorReport",
                "-XX:-CreateCoredumpOnCrash",
                "--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED",
                "-XX:StartFlightRecording=dumponexit=true",
                Crasher.class.getName()).start());
    }

    private static void processOutput(OutputAnalyzer output) throws Exception {
        output.shouldContain("CreateCoredumpOnCrash turned off, no core file dumped");

        final Path jfrEmergencyFilePath = getHsErrJfrPath(output);
        Asserts.assertTrue(Files.exists(jfrEmergencyFilePath), "No emergency jfr recording file " + jfrEmergencyFilePath + " exists");
        Asserts.assertNotEquals(Files.size(jfrEmergencyFilePath), 0L, "File length 0. Should at least be some bytes");
        System.out.printf("File size=%d%n", Files.size(jfrEmergencyFilePath));

        List<RecordedEvent> events = RecordingFile.readAllEvents(jfrEmergencyFilePath);
        Asserts.assertFalse(events.isEmpty(), "No event found");
        System.out.printf("Found event %s%n", events.get(0).getEventType().getName());
    }

    private static Path getHsErrJfrPath(OutputAnalyzer output) throws Exception {
        // extract to find hs-err_pid log file location
        final String hs_err_pid_log_file = output.firstMatch("# *(\\S*hs_err_pid\\d+\\.log)", 1);
        if (hs_err_pid_log_file == null) {
            throw new RuntimeException("Did not find hs_err_pid.log file in output.\n");
        }
        // the dumped out jfr file should have the same name and location but with a .jfr extension
        final String hs_err_pid_jfr_file = hs_err_pid_log_file.replace(LOG_FILE_EXTENSION, JFR_FILE_EXTENSION);
        return Paths.get(hs_err_pid_jfr_file);
    }
}
