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
            "-XX:+TraceBytecodes",
            // Make sure that work() is not compiled. If there is no compiler,
            // the flag is still accepted.
            "-XX:CompileCommand=exclude,CoherentBytecodeTraceTest.work",
            CoherentBytecodeTraceTest.class.getName(), "worker"
        );
        OutputAnalyzer oa = new OutputAnalyzer(pb.start()).shouldHaveExitValue(0);
        analyze(oa.stdoutAsLines());
    }

    private static void schedule() throws InterruptedException {
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
        for (Thread thread : threads) {
            thread.join();
        }
    }

    // The analysis works by finding the invokeinterface bytecode when calling
    // Strategy.foo. The trace should look something like the following:
    // invokeinterface 116 <CoherentBytecodeTraceTest$Strategy.foo(I)V>
    // The strategy is to find CoherentBytecodeTraceTest$Strategy.foo's index
    // and then ensure the constant pool ref and opcode before are correct.
    // This requires going through the file line-by-line.
    private static void analyze(List<String> lines) {
        IO.println("Analyzing " + lines.size() + " lines");
        boolean foundAtLeastOne = false;
        // Reverse regex for: 'invokeinterface \d+ '. This is needed to look
        // back from the interface name to ensure that the thing that
        // preceeds it is an invokeinterface with a constant pool reference.
        // Use 'XXXX' to denote where we want to put \d+ or else it will get
        // reversed and lose its semantics.
        String searchRegex = reverseString("invokeinterface XXXX ")
                                 .replace("XXXX", "\\d+");
        Pattern reverseFirstPart = Pattern.compile(searchRegex);
        for (String line : lines) {
            int fooCallIndex = line.indexOf(THE_METHOD);
            if (fooCallIndex == -1) {
                continue;
            }
            String untilFooCall = line.substring(0, fooCallIndex);
            String beginningReverse = reverseString(untilFooCall);
            // Use a Scanner to do a match for "invokeinterface XXXX "
            // immediately before the constant pool reference.
            Scanner scanner = new Scanner(beginningReverse);
            // Scanner#hasNext would use the next token given by whitespace,
            // but whitespace is part of the pattern. Use horizon instead, if
            // this is null, then there is no match.
            if (scanner.findWithinHorizon(reverseFirstPart, 0) == null) {
                IO.println("Using regex: " + reverseFirstPart);
                IO.println("Regex rejected: " + beginningReverse);
                throw new RuntimeException(
                    "torn bytecode trace: " + line
                );
            }
            foundAtLeastOne = true;
        }
        // If there are no invokeinterface calls then something went wrong
        // and the test probably needs to be updated.
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

    private static String reverseString(String input) {
        return new StringBuilder(input).reverse().toString();
    }

    private record Outer(Object inner) implements Strategy {
        @Override
        public void foo(int i) {
            if (i % 1000 == 0) {
                IO.println("foo" + i);
            }
        }
    }

    public interface Strategy {
        void foo(int i);
    }
}
