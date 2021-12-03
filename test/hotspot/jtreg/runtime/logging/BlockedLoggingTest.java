/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 * @test
 * @bug 8267517
 * @summary Test the JVM process with async unified logging won't be frozen
 * when stdout is blocked.
 *
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 * @run driver BlockedLoggingTest
 */

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.lang.RuntimeException;
import java.util.Arrays;
import java.util.List;

import jdk.test.lib.Asserts;
import jdk.test.lib.process.ProcessTools;

public class BlockedLoggingTest {
    static String BANNER = "User-defined Java Program has started.";

    public static class UserDefinedJavaProgram {
        public static void main(String[] args) {
            System.out.println(BANNER);
            System.out.flush();

            System.gc(); // trigger some gc activities.
            new Thread().start(); // launch a new thread.

            // if the control reaches here, we have demonstrated that the current process isn't
            // blocked by StdinBlocker because of -Xlog:async.
            //
            // the reason we throw a RuntimeException because the normal exit of JVM still needs
            // to call AsyncLogWriter::flush(), stdout is still blocked. AbortVMOnException will
            // abort JVM and avoid the final flushing.
            throw new RuntimeException("we succeed if we each here.");
        }
    }

    // StdinBlocker echoes whatever it sees from stdin until it encounters BANNER.
    // it will hang and leave stdin alone.
    public static class StdinBlocker {
        public static void main(String[] args) throws IOException {
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
            String line = in.readLine();
            while (line != null) {
                // block stdin once we have seen the banner.
                if (line.contains(BANNER)) {
                    while (true);
                }
                line = in.readLine();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        // process 0 pipes its stdout to process 1 as stdin.
        // java -Xlog:all=info -Xlog:async UserDefinedJavaProgram | java StdinBlocker
        ProcessBuilder[] builders = {
            ProcessTools.createJavaProcessBuilder("-XX:+UnlockDiagnosticVMOptions", "-XX:AbortVMOnException=java.lang.RuntimeException",
            // VMError::report_and_die() doesn't honor DisplayVMOutputToStderr, therefore we have to suppress it to avoid starvation
            "-XX:+DisplayVMOutputToStderr", "-XX:+SuppressFatalErrorMessage", "-XX:-UsePerfData",
             "-Xlog:all=debug", "-Xlog:async", UserDefinedJavaProgram.class.getName()),
            ProcessTools.createJavaProcessBuilder(StdinBlocker.class.getName())
        };

        List<Process> processes = ProcessBuilder.startPipeline(Arrays.asList(builders));
        // if process 0 should abort from Exceptions::debug_check_abort()
        int exitcode = processes.get(0).waitFor();
        Asserts.assertEQ(exitcode, Integer.valueOf(134));
        // terminate StdinBlocker by force
        processes.get(1).destroyForcibly();
    }
}
