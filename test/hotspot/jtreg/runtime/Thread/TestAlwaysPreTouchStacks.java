/*
 * Copyright (c) 2022 SAP SE. All rights reserved.
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * @test
 * @summary Test AlwaysPreTouchThreadStacks
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver TestAlwaysPreTouchStacks
 */

public class TestAlwaysPreTouchStacks extends Thread {

    static private final Thread createTestThread(int stackSize) {
        Thread t = new Thread(null,
                () -> System.out.println("Alive: " + stackSize),
                "Thread-" + stackSize, stackSize);
        return t;
    }

    public static void main(String[] args) throws InterruptedException, IOException {

        int[] stackSizes = {
          1024 * 256, 1024 * 512, 1024 * 1024 * 3
        };

        if (args.length == 1 && args[0].equals("test")) {

            ArrayList<Thread> threads = new ArrayList<>();

            for (int s: stackSizes) {
                threads.add(createTestThread(s));
            }

            threads.forEach(Thread::start);
            for (Thread t: threads) {
                t.join();
            }

        } else {

            ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
                    "-XX:+UnlockDiagnosticVMOptions",
                    "-Xmx100M",
                    "-XX:+AlwaysPreTouchStacks", "-Xlog:os+thread=trace",
                    "-XX:NativeMemoryTracking=summary", "-XX:+PrintNMTStatistics",
                    "TestAlwaysPreTouchStacks",
                    "test");
            OutputAnalyzer output = new OutputAnalyzer(pb.start());
            output.reportDiagnosticSummary();

            output.shouldHaveExitValue(0);

            for (int s: stackSizes) {
                output.shouldContain("Alive: " + Integer.toString(s));
            }

            output.shouldContain("Pretouching thread stack");

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
                    if (committed < (reserved / 2)) {
                        throw new RuntimeException("Expected a higher ratio between stack committed and reserved.");
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
