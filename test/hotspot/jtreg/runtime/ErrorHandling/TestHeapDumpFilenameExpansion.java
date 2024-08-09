/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, Red Hat, Inc.
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


/*
 * @test TestHeapDumpFilenameExpansion
 * @bug 8204681
 * @summary Test heap dump filename arguments are expanded i.e. %p to pid and %t to timestamp.
 * @library /test/lib
 * @requires vm.flagless
 * @run driver/timeout=240 TestHeapDumpFilenameExpansion run
 */

import jdk.test.lib.Platform;
import jdk.test.lib.hprof.HprofParser;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

import java.io.File;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestHeapDumpFilenameExpansion {

    private static final String PID_FILENAME = "file%p.hprof";
    private static final String TIMESTAMP_FILENAME = "file%t.hprof";
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            try {
                Object[] oa = new Object[Integer.MAX_VALUE];
                for(int i = 0; i < oa.length; i++) {
                    oa[i] = new Object[Integer.MAX_VALUE];
                }
            } catch (OutOfMemoryError err) {
                return;
            }
        }

        testPidSubstitution(PID_FILENAME);
        testTimestampSusbtitution(TIMESTAMP_FILENAME);
    }

    private static void testPidSubstitution(String filename) throws Exception {
        Process process = startProcess(filename);
        long pid = process.pid();
        String heapdumpFilename = "file%d.hprof".formatted(pid);

        OutputAnalyzer output = new OutputAnalyzer(process);
        output.stdoutShouldNotBeEmpty();
        output.shouldContain("Dumping heap to " + heapdumpFilename);
        File dump = new File(heapdumpFilename);
        if (dump.exists() && dump.isFile()) {
            HprofParser.parse(new File(heapdumpFilename));
            dump.delete();
        } else {
            throw new Exception("Pid was not expanded for filename %s".formatted(filename));
        }
    }

    private static void testTimestampSusbtitution(String filename) throws Exception {
        Process process = startProcess(filename);
        OutputAnalyzer output = new OutputAnalyzer(process);
        output.stdoutShouldNotBeEmpty();

        boolean found = false;
        Pattern pattern = Pattern.compile("file\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}");
        File[] files = new File(".").listFiles();
        if(files != null) {
            for (File file : files) {
                Matcher matcher = pattern.matcher(file.getName());
                if (matcher.find()) {
                    found = true;
                    HprofParser.parse(file);
                    Files.delete(file.toPath());
                }
            }
        }

        if (!found) {
            throw new Exception("Timestamp was not expanded for filename %s".formatted(filename));
        }
    }

    private static Process startProcess(String filename) throws Exception {
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder("-XX:+HeapDumpOnOutOfMemoryError",
                "-XX:HeapDumpPath=" + filename,
                "-XX:MaxMetaspaceSize=16m",
                "-Xmx128m",
                Platform.isDebugBuild() ? "-XX:-VerifyDependencies" : "-Dx",
                TestHeapDumpFilenameExpansion.class.getName());
        return pb.start();
    }
}
