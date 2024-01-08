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

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/*
 * @test id=defaults
 * @bug 8319048
 * @summary Test the MonitorUnlinkBatch options
 * @library /test/lib
 * @run driver MonitorUnlinkBatchTest defaults
 */

/*
 * @test id=legal
 * @library /test/lib
 * @run driver MonitorUnlinkBatchTest legal
 */

/*
 * @test id=illegal
 * @library /test/lib
 * @run driver MonitorUnlinkBatchTest illegal
 */

/*
 * @test id=aggressive
 * @library /test/lib
 * @run driver MonitorUnlinkBatchTest aggressive
 */

/*
 * @test id=lazy
 * @library /test/lib
 * @run driver MonitorUnlinkBatchTest lazy
 */


public class MonitorUnlinkBatchTest {

    public static class Test {
        // Inflate a lot of monitors, so that threshold heuristics definitely fires
        private static final int MONITORS = 10_000;

        // Use a handful of threads to inflate the monitors, to eat the cost of
        // wait(1) calls. This can be larger than available parallelism, since threads
        // would be time-waiting.
        private static final int THREADS = 16;

        private static Thread[] threads;
        private static Object[] monitors;

        public static void main(String... args) throws Exception {
            monitors = new Object[MONITORS];
            threads = new Thread[THREADS];

            for (int t = 0; t < THREADS; t++) {
                int monStart = t * MONITORS / THREADS;
                int monEnd = (t + 1) * MONITORS / THREADS;
                threads[t] = new Thread(() -> {
                    for (int m = monStart; m < monEnd; m++) {
                        Object o = new Object();
                        synchronized (o) {
                            try {
                                o.wait(1);
                            } catch (InterruptedException e) {
                            }
                        }
                        monitors[m] = o;
                    }
                });
                threads[t].start();
            }

            for (Thread t : threads) {
                t.join();
            }

            try {
                Thread.sleep(10_000);
            } catch (InterruptedException ie) {
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            throw new IllegalArgumentException("Expect the test label");
        }

        String test = args[0];
        switch (test) {
            case "defaults":
                test("");
                break;

            case "legal":
                // Legal, even if not useful settings
                test("",
                     "-XX:MonitorDeflationMax=100000",
                     "-XX:MonitorUnlinkBatch=100001"
                     );
                break;

            case "illegal":
                // Quick tests that should fail on JVM flags verification.
                test("outside the allowed range",
                     "-XX:MonitorUnlinkBatch=-1"
                );
                test("outside the allowed range",
                     "-XX:MonitorUnlinkBatch=0"
                );
                break;

            case "aggressive":
                // The smallest batch possible.
                test("",
                     "-XX:MonitorUnlinkBatch=1"
                );
                break;

            case "lazy":
                // The largest batch possible.
                test("",
                     "-XX:MonitorDeflationMax=1000000",
                     "-XX:MonitorUnlinkBatch=1000000"
                );
                break;

            default:
                throw new IllegalArgumentException("Unknown test: " + test);
        }
    }

    public static void test(String msg, String... args) throws Exception {
        List<String> opts = new ArrayList<>();
        opts.add("-Xmx128M");
        opts.add("-XX:+UnlockDiagnosticVMOptions");
        opts.add("-XX:GuaranteedAsyncDeflationInterval=100");
        opts.addAll(Arrays.asList(args));
        opts.add("MonitorUnlinkBatchTest$Test");

        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(opts);
        OutputAnalyzer oa = new OutputAnalyzer(pb.start());
        if (msg.isEmpty()) {
            oa.shouldHaveExitValue(0);
        } else {
            oa.shouldNotHaveExitValue(0);
            oa.shouldContain(msg);
        }
    }

}
