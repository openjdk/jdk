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
 * @summary Test the JVM process with unified logging with -Xlog:async will not be
 * frozen even when stdout is blocked.
 *
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 * @run driver BlockedLoggingTest
 */

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.AbstractQueue;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

import jdk.test.lib.Asserts;
import jdk.test.lib.process.ProcessTools;

public class BlockedLoggingTest {
    static String BANNER = "User-defined Java Program has started.";
    static int ThreadNum = 1;

    public static class UserDefinedJavaProgram {
        public static void main(String[] args) {
            System.out.println(BANNER);
            System.out.flush();

            Thread[] threads = new Thread[ThreadNum];
            // The size of pipe buffer is indeterminate. It is presumably 64k on many Linux distros.
            // We just churn many gc-related logs in ChurnThread.Duration seconds.
            for(int i = 0; i < ThreadNum; ++i) {
                threads[i] = new ChurnThread();
                threads[i].start();
            }

            try {
                for (int i = 0; i < ThreadNum; ++i) {
                    threads[i].join();
                }
            } catch (InterruptedException ie) {
                // ignore
            }

            // If the control reaches here, we have demonstrated that the current process isn't
            // blocked by StdinBlocker because of -Xlog:async.
            //
            // The reason we throw a RuntimeException because the normal exit of JVM still needs
            // to call AsyncLogWriter::flush(), stdout is still blocked. AbortVMOnException will
            // abort JVM and avoid the final flushing.
            throw new RuntimeException("we succeed if we each here.");
        }
    }

    static class ChurnThread extends Thread {
        static long Duration = 3; // seconds;  Program will exit after Duration of seconds.
        static int ReferenceSize = 1024 * 10;  // each reference object size;
        static int CountDownSize = 1000 * 100;
        static int EachRemoveSize = 1000 * 50; // remove # of elements each time.

        long timeZero = System.currentTimeMillis();

        public ChurnThread() {}

        public void run() {
            AbstractQueue<String> q = new ArrayBlockingQueue<String>(CountDownSize);
            char[] srcArray = new char[ReferenceSize];
            String emptystr = new String(srcArray);

            while (true) {
                // Simulate object use to force promotion into OldGen and then GC
                if (q.size() >= CountDownSize) {
                    for (int j = 0; j < EachRemoveSize; j++) {
                        q.remove();
                    }

                    // every 1000 removal is counted as 1 unit.
                    long curTime = System.currentTimeMillis();
                    long totalTime = curTime - timeZero;

                    if (Duration != -1 && totalTime > Duration * 1000) {
                        return;
                    }
                }

                srcArray = new char[ReferenceSize];
                emptystr = new String(srcArray);
                String str = emptystr.replace('\0', 'a');
                q.add(str);
            }
        }
    }

    // StdinBlocker echoes whatever it sees from stdin until it encounters BANNER.
    // It will hang and leave stdin alone.
    public static class StdinBlocker {
        public static void main(String[] args) throws IOException {
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
            String line = in.readLine();

            while (line != null) {
                // block stdin once we have seen the banner.
                if (line.contains(BANNER)) {
                    while (true) {
                        try {
                            Thread.sleep(Long.MAX_VALUE);
                        } catch (InterruptedException ie) {/* skip on purpose */}
                    }
                }
                line = in.readLine();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        // The simplest test is to use tty with software flow control. AsyncUL should not suspend JVM
        // with XOFF(Ctrl^s) to stdout. We can not assume tty is in use in the testing environments. It is also
        // not portable. Therefore, the test uses pipe to simulate the suspending stdout.
        ProcessBuilder[] builders = {
            // Process 0 has to carefully avoid any output to stdout except Unified Logging.
            // We expect to demonstrate that process 0 with -Xlog:async can still terminate even though its stdout
            // is blocked.
            ProcessTools.createJavaProcessBuilder("-XX:+UnlockDiagnosticVMOptions", "-XX:AbortVMOnException=java.lang.RuntimeException",
            "-XX:+DisplayVMOutputToStderr", "-XX:+SuppressFatalErrorMessage", "-XX:-UsePerfData", "-Xlog:all=debug",
            "-Xlog:async", // should hang without this!
            UserDefinedJavaProgram.class.getName()),
            ProcessTools.createJavaProcessBuilder(StdinBlocker.class.getName())
        };

        List<Process> processes = ProcessBuilder.startPipeline(Arrays.asList(builders));
        // Process 0 should abort from Exceptions::debug_check_abort()
        int exitcode = processes.get(0).waitFor();
        // Exitcode may be 1 or 134.
        Asserts.assertNE(exitcode, Integer.valueOf(0));
        // Terminate StdinBlocker by force
        processes.get(1).destroyForcibly();
    }
}
