/*
 * Copyright (c) 2026 SAP SE. All rights reserved.
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
 */

/**
 * @test TestOnSpinWaitPPC64
 * @summary Checks that java.lang.Thread.onSpinWait is intrinsified on PPC64
 *          and emits the SMT priority-low / priority-medium nop pair.
 * @library /test/lib
 *
 * @requires vm.flagless
 * @requires os.arch=="ppc64" | os.arch=="ppc64le"
 * @requires vm.debug
 *
 * @run driver compiler.onSpinWait.TestOnSpinWaitPPC64 c2
 * @run driver compiler.onSpinWait.TestOnSpinWaitPPC64 c1
 */

package compiler.onSpinWait;

import java.util.ArrayList;
import java.util.Iterator;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestOnSpinWaitPPC64 {

    public static void main(String[] args) throws Exception {
        String compiler = args[0];
        ArrayList<String> command = new ArrayList<String>();
        command.add("-XX:+IgnoreUnrecognizedVMOptions");
        command.add("-showversion");
        command.add("-XX:-BackgroundCompilation");
        command.add("-XX:+UnlockDiagnosticVMOptions");
        command.add("-XX:+PrintCompilation");
        command.add("-XX:+PrintInlining");
        if (compiler.equals("c2")) {
            command.add("-XX:-TieredCompilation");
        } else if (compiler.equals("c1")) {
            command.add("-XX:+TieredCompilation");
            command.add("-XX:TieredStopAtLevel=1");
        } else {
            throw new RuntimeException("Unknown compiler: " + compiler);
        }
        command.add("-Xbatch");
        command.add("-XX:CompileCommand=compileonly," + Launcher.class.getName() + "::test");
        command.add("-XX:CompileCommand=print," + Launcher.class.getName() + "::test");
        command.add(Launcher.class.getName());

        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(command);

        OutputAnalyzer analyzer = new OutputAnalyzer(pb.start());

        analyzer.shouldHaveExitValue(0);

        System.out.println(analyzer.getOutput());

        checkOutput(analyzer);
    }

    // Hex encoding of the SMT priority-hint instructions emitted by the
    // PPC `Thread.onSpinWait()` intrinsic.
    // X-forms used:
    // or 1,1,1 -> 0x7C21_0B78 (smt_prio_low)
    // or 2,2,2 -> 0x7C42_1378 (smt_prio_medium)
    private static String getSpinWaitInstructionHexLE(String name) {
        if ("smt_prio_low".equals(name))
            return "780b217c";
        if ("smt_prio_medium".equals(name))
            return "7813427c";
        throw new RuntimeException("Unknown spin wait instruction: " + name);
    }

    private static String getSpinWaitInstructionHexBE(String name) {
        if ("smt_prio_low".equals(name))
            return "7c210b78";
        if ("smt_prio_medium".equals(name))
            return "7c421378";
        throw new RuntimeException("Unknown spin wait instruction: " + name);
    }

    private static boolean lineContainsInstruction(String compact, String name) {
        return compact.contains(getSpinWaitInstructionHexLE(name)) ||
                compact.contains(getSpinWaitInstructionHexBE(name));
    }

    // The expected output for the spin wait body if the hsdis library is available:
    //
    // ;; spin_wait {
    // 0x...: or r1,r1,r1
    // 0x...: or r2,r2,r2
    // ;; }
    //
    // When hsdis is absent the disassembler dumps raw bytes which have to matched.
    private static void checkOutput(OutputAnalyzer output) {
        Iterator<String> iter = output.asLines().listIterator();

        // 1. Check whether printed instructions are disassembled.
        boolean isDisassembled = false;
        while (iter.hasNext()) {
            String line = iter.next();
            if (line.contains("[Disassembly]")) {
                isDisassembled = true;
                break;
            }
            if (line.contains("[MachCode]")) {
                break;
            }
        }

        // 2. Look for the spin_wait block comment.
        boolean foundHead = false;
        while (iter.hasNext()) {
            String line = iter.next().trim();
            if (line.contains(";; spin_wait {")) {
                foundHead = true;
                break;
            }
        }
        if (!foundHead) {
            throw new RuntimeException("spin_wait block comment not found");
        }

        // 3. Expect prio low and prio med instructions inside the block.
        boolean sawLow = false, sawMed = false;
        while (iter.hasNext()) {
            String line = iter.next().trim();
            if (line.startsWith(";; }")) {
                break;
            }
            if (isDisassembled) {
                // hsdis output: look for `or r1,r1,r1` and `or r2,r2,r2`.
                // Also the mnemonic mr rx,rx needs to be matched.
                String norm = line.replaceAll("\\s+", " ");
                if (norm.contains("or r1,r1,r1") || norm.contains("mr r1,r1"))
                    sawLow = true;
                if (norm.contains("or r2,r2,r2") || norm.contains("mr r2,r2"))
                    sawMed = true;
            } else {
                // without hsdis output: raw 4-byte instruction words.
                String compact = line.replaceAll("\\s", "").toLowerCase();
                if (lineContainsInstruction(compact, "smt_prio_low"))
                    sawLow = true;
                if (lineContainsInstruction(compact, "smt_prio_medium"))
                    sawMed = true;
            }
        }

        if (!sawLow) {
            throw new RuntimeException("Did not find smt_prio_low (or r1,r1,r1) inside spin_wait block");
        }
        if (!sawMed) {
            throw new RuntimeException("Did not find smt_prio_medium (or r2,r2,r2) inside spin_wait block");
        }
    }

    static class Launcher {
        public static void main(final String[] args) throws Exception {
            for (int i = 0; i < 20_000; i++) {
                test();
            }
        }

        static void test() {
            java.lang.Thread.onSpinWait();
        }
    }
}
