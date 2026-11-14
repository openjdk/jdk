/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, Red Hat Inc.
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
 * @test id=ENABLED
 * @bug 8303215 8312182 8397204
 * @summary On THP=always systems, we prevent THPs from forming within thread stacks
 * @library /test/lib
 * @requires vm.flagless
 * @requires os.family == "linux"
 * @requires vm.debug
 * @requires os.arch=="amd64" | os.arch=="x86_64" | os.arch=="aarch64"
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver THPsInThreadStackPreventionTest PATCH-ENABLED
 */

/*
 * @test id=DISABLED
 * @bug 8303215 8312182 8397204
 * @summary On THP=always systems, we prevent THPs from forming within thread stacks (negative test)
 * @library /test/lib
 * @requires vm.flagless
 * @requires os.family == "linux"
 * @requires vm.debug
 * @requires os.arch=="amd64" | os.arch=="x86_64" | os.arch=="aarch64"
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run main/manual THPsInThreadStackPreventionTest  PATCH-DISABLED
 */

import jdk.test.lib.Platform;
import jdk.test.lib.os.linux.HugePageConfiguration;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jtreg.SkippedException;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

public class THPsInThreadStackPreventionTest {

    // We test the mitigation for "huge rss for THP=always" introduced with JDK-8312182 and JDK-8302015:
    //
    // We start a program that spawns a ton of threads with a stack size close to THP page size. The threads
    // are idle and should not build up a lot of stack. The threads are started with an artificial delay
    // between thread start and stack guardpage creation, which exacerbates the RSS bloat (for explanation
    // please see 8312182).
    //
    // We then observe RSS of that program. We expect it to stay below a reasonable maximum. The unpatched
    // version should show an RSS of ~2 GB (paying for the fully paged in thread stacks). The fixed variant should
    // cost only ~200-400 MB.

    static final int numThreads = 1000;
    static final long threadStackSizeMB = 2; // must be 2M
    static final long heapSizeMB = 64;
    static final long basicRSSOverheadMB = heapSizeMB + 150;
    // A successful completion of this test would show not more than X KB per thread stack.
    static final long acceptableRSSPerThreadStack = 128 * 1024;
    static final long acceptableRSSForAllThreadStacks = numThreads * acceptableRSSPerThreadStack;
    static final long acceptableRSSLimitMB = (acceptableRSSForAllThreadStacks / (1024 * 1024)) + basicRSSOverheadMB;

    private static class TestMain {

        static class Sleeper extends Thread {
            CyclicBarrier barrier;
            public Sleeper(CyclicBarrier barrier) {
                this.barrier = barrier;
            }
            @Override
            public void run() {
                try {
                    barrier.await(); // wait for all siblings
                    barrier.await(); // wait main thread to print status
                } catch (InterruptedException | BrokenBarrierException e) {
                    e.printStackTrace();
                }
            }
        }

        public static void main(String[] args) throws BrokenBarrierException, InterruptedException {

            // Fire up 1000 threads with 2M stack size each.
            Sleeper[] threads = new Sleeper[numThreads];
            CyclicBarrier barrier = new CyclicBarrier(numThreads + 1);

            for (int i = 0; i < numThreads; i++) {
                threads[i] = new Sleeper(barrier);
                threads[i].start();
            }

            // Wait for all threads to come up
            barrier.await();

            // print status
            String file = "/proc/self/status";
            try (FileReader fr = new FileReader(file);
                 BufferedReader reader = new BufferedReader(fr)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            } catch (IOException | NumberFormatException e) { /* ignored */ }

            // Signal threads to stop
            barrier.await();

        }
    }

    static class ProcSelfStatus {

        public long rssMB;
        public long swapMB;
        public int numLifeThreads;

        // Parse output from /proc/self/status
        public static ProcSelfStatus parse(OutputAnalyzer o) {
            ProcSelfStatus status = new ProcSelfStatus();
            String s = o.firstMatch("Threads:\\s*(\\d+)", 1);
            Objects.requireNonNull(s);
            status.numLifeThreads = Integer.parseInt(s);
            s = o.firstMatch("VmRSS:\\s*(\\d+) kB", 1);
            Objects.requireNonNull(s);
            status.rssMB = Long.parseLong(s) / 1024;
            s = o.firstMatch("VmSwap:\\s*(\\d+) kB", 1);
            Objects.requireNonNull(s);
            status.swapMB = Long.parseLong(s) / 1024;
            return status;
        }
    }

    // glibc creates a new malloc arena when threads malloc concurrently and the existing arenas are
    // locked, up to 8 * online CPUs on 64-bit. This test starts 1000 threads at once, so startup
    // mallocs can open that many arenas. Since glibc 2.43, AArch64 malloc enables 2MB THP by
    // default and sizes a new arena heap to that page. Under THP=always each such heap is faulted
    // in as a 2MB page, so RSS includes this arena growth as well as the thread stacks. Cap the
    // arenas at 1 on AArch64 so that growth stays small and the RSS check still reflects the thread
    // stacks. Reference: https://sourceware.org/pipermail/glibc-cvs/2025q4/090171.html
    private static void limitGlibcMallocArenas(ProcessBuilder pb) {
        if (Platform.isAArch64()) {
            pb.environment().put("GLIBC_TUNABLES", "glibc.malloc.arena_max=1");
        }
    }

    public static void main(String[] args) throws Exception {

        HugePageConfiguration config = HugePageConfiguration.readFromOS();
        // This issue is bound to THP=always
        if (config.getThpMode() != HugePageConfiguration.THPMode.always) {
            throw new SkippedException("Test only makes sense in THP \"always\" mode");
        }

        String[] defaultArgs = {
            "-Xlog:pagesize",
            "-Xmx" + heapSizeMB + "m", "-Xms" + heapSizeMB + "m", "-XX:+AlwaysPreTouch", // stabilize RSS
            "-Xss" + threadStackSizeMB + "m",
            "-XX:-CreateCoredumpOnCrash",
            // Limits the number of JVM-internal threads, which depends on the available cores of the
            // machine. RSS+Swap could exceed acceptableRSSLimitMB when JVM creates many internal threads.
            "-XX:ActiveProcessorCount=2",
            // This will delay the child threads before they create guard pages, thereby greatly increasing the
            // chance of large VMA formation + hugepage coalescation; see JDK-8312182
            "-XX:+DelayThreadStartALot"
        };
        ArrayList<String> finalargs = new ArrayList<>(Arrays.asList(defaultArgs));

        switch (args[0]) {
            case "PATCH-ENABLED": {
                finalargs.add(TestMain.class.getName());
                ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(finalargs);
                limitGlibcMallocArenas(pb);

                OutputAnalyzer output = new OutputAnalyzer(pb.start());
                output.shouldHaveExitValue(0);

                ProcSelfStatus status = ProcSelfStatus.parse(output);
                if (status.numLifeThreads < numThreads) {
                    throw new RuntimeException("Number of live threads lower than expected: " + status.numLifeThreads + ", expected " + numThreads);
                } else {
                    System.out.println("Found " + status.numLifeThreads + " to be alive. Ok.");
                }

                long rssPlusSwapMB = status.swapMB + status.rssMB;

                if (rssPlusSwapMB > acceptableRSSLimitMB) {
                    throw new RuntimeException("RSS+Swap larger than expected: " + rssPlusSwapMB + "m, expected at most " + acceptableRSSLimitMB + "m");
                } else {
                    if (rssPlusSwapMB < heapSizeMB) { // we pretouch the java heap, so we expect to see at least that:
                        throw new RuntimeException("RSS+Swap suspiciously low: " + rssPlusSwapMB + "m, expected at least " + heapSizeMB + "m");
                    }
                    System.out.println("Okay: RSS+Swap=" + rssPlusSwapMB + ", within acceptable limit of " + acceptableRSSLimitMB);
                }
            }
            break;

            case "PATCH-DISABLED": {

                // Only execute manually! this will allocate ~2gb of memory!

                // explicitly disable the no-THP-workaround:
                finalargs.add("-XX:+UnlockDiagnosticVMOptions");
                finalargs.add("-XX:-THPStackMitigation");

                finalargs.add(TestMain.class.getName());
                ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(finalargs);
                limitGlibcMallocArenas(pb);

                OutputAnalyzer output = new OutputAnalyzer(pb.start());
                output.shouldHaveExitValue(0);

                // The JVM prints this only when THP mode is "always", the kernel does not map MAP_STACK to
                // VM_NOHUGEPAGE, and mitigation is off. Otherwise the high RSS this negative test expects cannot be
                // reproduced.
                if (!output.matches("\\[pagesize *\\] JVM will \\*not\\* prevent THPs in thread stacks\\. This may cause high RSS\\.")) {
                    throw new SkippedException("JVM did not report that THPs may form in thread stacks; "
                        + "the kernel already prevents them.");
                }

                // Parse output from self/status
                ProcSelfStatus status = ProcSelfStatus.parse(output);
                if (status.numLifeThreads < numThreads) {
                    throw new RuntimeException("Number of live threads lower than expected (" + status.numLifeThreads + ", expected " + numThreads +")");
                } else {
                    System.out.println("Found " + status.numLifeThreads + " to be alive. Ok.");
                }

                long rssPlusSwapMB = status.swapMB + status.rssMB;

                if (rssPlusSwapMB < acceptableRSSLimitMB) {
                    throw new RuntimeException("RSS+Swap lower than expected: " + rssPlusSwapMB + "m, expected more than " + acceptableRSSLimitMB + "m");
                }
                break;
            }

            default: throw new RuntimeException("Bad argument: " + args[0]);
        }
    }
}
