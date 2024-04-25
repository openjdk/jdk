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

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * @test
 * @summary Tests that recursive locking doesn't cause excessive native memory usage
 * @library /test/lib
 * @run driver TestRecursiveMonitorChurn
 */
public class TestRecursiveMonitorChurn {
    static class Monitor {
        public static volatile int i, j;
        synchronized void doSomething() {
            i++;
            doSomethingElse();
        }
        synchronized void doSomethingElse() {
            j++;
        }
    }

    public static volatile Monitor monitor;
    public static void main(String[] args) throws IOException {
        if (args.length == 1 && args[0].equals("test")) {
            // The actual test, in a forked JVM.
            for (int i = 0; i < 100000; i++) {
                monitor = new Monitor();
                monitor.doSomething();
            }
            System.out.println("i + j = " + (Monitor.i + Monitor.j));
        } else {
            ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
                    "-XX:+UnlockDiagnosticVMOptions",
                    "-Xmx100M", "-XX:AsyncDeflationInterval=0", "-XX:GuaranteedAsyncDeflationInterval=0",
                    "-XX:NativeMemoryTracking=summary", "-XX:+PrintNMTStatistics",
                    "TestRecursiveMonitorChurn",
                    "test");
            OutputAnalyzer output = new OutputAnalyzer(pb.start());
            output.reportDiagnosticSummary();

            output.shouldHaveExitValue(0);

            // We want to see, in the final NMT printout, a committed object monitor size that is reasonably low.
            // Like this:
            // -           Object Monitors (reserved=208, committed=208)
            //                             (malloc=208 #1) (at peak)
            //
            // Without recursive locking support, this would look more like this:
            // -           Object Monitors (reserved=20800624, committed=20800624)
            //                             (malloc=20800624 #100003) (at peak)

            Pattern pat = Pattern.compile("- *Object Monitors.*reserved=(\\d+), committed=(\\d+).*");
            for (String line : output.asLines()) {
                Matcher m = pat.matcher(line);
                if (m.matches()) {
                    long reserved = Long.parseLong(m.group(1));
                    long committed = Long.parseLong(m.group(2));
                    System.out.println(">>>>> " + line + ": " + reserved + " - " + committed);
                    if (committed > 1000) {
                        throw new RuntimeException("Allocated too many monitors");
                    }
                    return;
                }
            }
            throw new RuntimeException("Did not find expected NMT output");
        }
    }
}
