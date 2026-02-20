/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8370044
 * @library / /test/lib
 * @summary Ensures that individual bytecodes are not broken apart.
 * @requires vm.debug == true & vm.flagless
 * @run driver CoherentBytecodeTraceTest
 */

import module java.base;

import jdk.test.lib.Utils;
import jdk.test.lib.process.*;

public class CoherentBytecodeTraceTest {
    private static final boolean DEBUG = false;
    private static final int NUM_THREADS = 3;
    private static final String THE_METHOD = "<CoherentBytecodeTraceTest$Strategy.foo(I)V>";

    public static void main(String[] args)
            throws InterruptedException, IOException {
        if (args.length == 1 && "worker".equals(args[0])) {
            schedule();
            return;
        }
        // Create a VM process and trace its bytecodes.
        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
            // Trace the bytecodes.
            "-XX:+TraceBytecodes",
            // Make sure that work() is not compiled. If there is no compiler,
            // the flag is still accepted.
            "-XX:CompileCommand=exclude,CoherentBytecodeTraceTest.work",
            CoherentBytecodeTraceTest.class.getName(), "worker"
        );
        // Make sure that it contains the output needed.
        OutputAnalyzer oa = new OutputAnalyzer(pb.start()).shouldHaveExitValue(0);
        analyze(oa.stdoutAsLines());
    }

    // Schedules n threads to do the work each.
    private static void schedule() throws InterruptedException {
        // Start n worker threads that do the work.
        Thread[] threads =
            IntStream.range(0, NUM_THREADS)
                     .mapToObj(i -> {
                         Thread thread = new Thread(
                             CoherentBytecodeTraceTest::work
                         );
                         thread.start();
                         return thread;
                     })
                     .toArray(Thread[]::new);
        // Wait for work to be completed.
        for (Thread thread : threads) {
            thread.join();
        }
    }

    // The analysis works by finding the invokeinterface bytecode when calling
    // Strategy.foo The trace should look something like the following:
    // invokeinterface 116 <CoherentBytecodeTraceTest$Strategy.foo(I)V>
    // The stategy is to find CoherentBytecodeTraceTest$Strategy.foo's index
    // and then ensure the text before and after is correct.
    // This requires going through the file line-by-line.
    private static void analyze(List<String> lines) {
        if (DEBUG) {
            IO.println("Analyzing " + lines.size() + " lines");
        }
        boolean foundAtLeastOne = false;
        // Reverse regex for: 'invokeinterface \d+ '. This is needed to look
        // back from the interface name to ensure that the thing that
        // preceeds it is an invokeinterface with a constant pool reference.
        Pattern reverseFirstPart = Pattern.compile(" \\d+ ecafretniekovni");
        for (String line : lines) {
            int cbttsFoo = line.indexOf(THE_METHOD);
            if (cbttsFoo == -1) {
                continue;
            }
            String beginningReverse = new StringBuilder(line.substring(0, cbttsFoo))
                                                       .reverse()
                                                       .toString();
            // Use a Scanner to do a match for "invokeinterface XXXX "
            // immediately before the constant pool reference.
            Scanner scanner = new Scanner(beginningReverse);
            // Scanner#hasNext would use the next token given by whitespace,
            // but whitespace is part of the pattern. Use horizon instead, if
            // this is null, then there is no match.
            if (scanner.findWithinHorizon(reverseFirstPart, 0) == null) {
                if (DEBUG) {
                    IO.println("Regex rejected: " + beginningReverse);
                }
                throw new RuntimeException(
                    "torn bytecode trace: " + line
                );
            }
            foundAtLeastOne = true;
        }
        // Sanity.
        if (!foundAtLeastOne) {
            throw new RuntimeException(
                "sanity failure: no invokeinterface found for " + THE_METHOD
            );
        }
    }

    // Performs some random work.
    // The goal is to have this emit some bytecodes that contain other bytes.
    public static void work() {
        int x = 10;
        int y = 30;
        int sum = 123;
        for (int i = 0; i < 10_000; i++) {
            if (i == x) {
                int modulo = y % i;
                sum ^= modulo;
            } else {
                Strategy[] arr = new Strategy[] { new Outer(new Object()) };
                arr[0].foo(i);
                x = y - sum;
            }
        }
    }

    private static final record Outer(Object inner) implements Strategy {
        @Override
        public void foo(int i) {
            if (i % 1000 == 0) {
                IO.println("foo" + i);
            }
        }
    }

    public static interface Strategy {
        void foo(int i);
    }
}
