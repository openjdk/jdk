/*
 * Copyright (c) 2022 SAP SE. All rights reserved.
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.CyclicBarrier;

/*
 * @test
 * @summary Test AlwaysPreTouchThreadStacks
 * @requires os.family != "aix"
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver TestAlwaysPreTouchStacks
 */

public class TestAlwaysPreTouchStacks {

    // We will create a bunch of large-stacked threads to make a significant imprint on combined thread stack size
    final static int MB = 1024*1024;
    static int memoryCeilingMB = 128;
    static int threadStackSizeMB = 8;
    static int numThreads = memoryCeilingMB / threadStackSizeMB;
    static CyclicBarrier gate = new CyclicBarrier(numThreads + 1);

    static private final Thread createTestThread(int num) {
        Thread t = new Thread(null,
                () -> {
                    System.out.println("Alive: " + num);
                    try {
                        // report aliveness, then sleep until VM death
                        gate.await();
                        for (;;) {
                            Thread.sleep(1000);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                },
                "TestThread-" + num, threadStackSizeMB * MB);
        t.setDaemon(true);
        return t;
    }

    public static void main(String[] args) throws Exception {

        if (args.length == 1 && args[0].equals("test")) {

            ArrayList<Thread> threads = new ArrayList<>();

            // Add a bunch of large-stacked threads to make a significant imprint on combined thread stack size
            for (int i = 0; i < numThreads; i++) {
                threads.add(createTestThread(i));
            }

            // Start test threads.
            threads.forEach(Thread::start);

            gate.await();

            // Stop VM. VM will run PrintNMTStatistics before exiting, and the still-running daemon threads
            // should show up with fully - or almost fully - committed thread stacks.

        } else {
            ArrayList<String> vmArgs = new ArrayList<>();
            Collections.addAll(vmArgs,
                    "-XX:+UnlockDiagnosticVMOptions",
                    "-Xmx100M",
                    "-XX:+AlwaysPreTouchStacks",
                    "-XX:NativeMemoryTracking=summary", "-XX:+PrintNMTStatistics");
            if (System.getProperty("os.name").contains("Linux")) {
                vmArgs.add("-XX:-UseMadvPopulateWrite");
            }
            Collections.addAll(vmArgs, "TestAlwaysPreTouchStacks", "test");
            ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(vmArgs);
            OutputAnalyzer output = new OutputAnalyzer(pb.start());
            output.reportDiagnosticSummary();

            output.shouldHaveExitValue(0);

            for (int i = 0; i < numThreads; i++) {
                output.shouldContain("Alive: " + i);
            }

            // We want to see, in the final NMT printout, a committed thread stack size very close to reserved
            // stack size. Like this:
            // -                    Thread (reserved=10332400KB, committed=10284360KB)
            //                      (thread #10021)
            //                      (stack: reserved=10301560KB, committed=10253520KB)   <<<<
            //
            // ... without -XX:+AlwaysPreTouchStacks, the committed/reserved ratio for thread stacks should be
            // a lot lower, e.g.:
            // -                    Thread (reserved=10332400KB, committed=331828KB)
            //                      (thread #10021)
            //                      (stack: reserved=10301560KB, committed=300988KB)  <<<

            output.shouldMatch("- *Thread.*reserved.*committed");
            Pattern pat = Pattern.compile(".*stack: reserved=(\\d+), committed=(\\d+).*");
            boolean foundLine = false;
            for (String line : output.asLines()) {
                Matcher m = pat.matcher(line);
                if (m.matches()) {
                    long reserved = Long.parseLong(m.group(1));
                    long committed = Long.parseLong(m.group(2));
                    System.out.println(">>>>> " + line + ": " + reserved + " - " + committed);
                    // This is a bit fuzzy: even with PreTouch we don't commit the full range of what NMT counts
                    // as thread stack. But without pre-touching, the thread stacks would be committed to about 1/5th
                    // of their reserved size. Requiring them to be committed for over 3/4th shows that pretouch is
                    // really working.
                    if ((double)committed < ((double)reserved * 0.75)) {
                        throw new RuntimeException("Expected a higher ratio between stack committed and reserved.");
                    }
                    // Added sanity tests: we expect our test threads to be still alive when NMT prints its final
                    // report, so their stacks should dominate the NMT-reported total stack size.
                    long max_reserved = memoryCeilingMB * 3 * MB;
                    long min_reserved = memoryCeilingMB * MB;
                    if (reserved >= max_reserved || reserved < min_reserved) {
                        throw new RuntimeException("Total reserved stack sizes outside of our expectations (" + reserved +
                                                   ", expected " + min_reserved + ".." + max_reserved + ")");
                    }
                    foundLine = true;
                    break;
                }
            }
            if (!foundLine) {
                throw new RuntimeException("Did not find expected NMT output");
            }
        }

    }

}
