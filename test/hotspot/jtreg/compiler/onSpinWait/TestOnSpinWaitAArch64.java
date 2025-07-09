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

/**
 * @test TestOnSpinWaitAArch64
 * @summary Checks that java.lang.Thread.onSpinWait is intrinsified with instructions specified with '-XX:OnSpinWaitInst' and '-XX:OnSpinWaitInstCount'
 * @bug 8186670
 * @library /test/lib
 *
 * @requires vm.flagless
 * @requires os.arch=="aarch64"
 * @requires vm.debug
 *
 * @run driver compiler.onSpinWait.TestOnSpinWaitAArch64 c2 nop 7
 * @run driver compiler.onSpinWait.TestOnSpinWaitAArch64 c2 isb 3
 * @run driver compiler.onSpinWait.TestOnSpinWaitAArch64 c2 yield 1
 * @run driver compiler.onSpinWait.TestOnSpinWaitAArch64 c2 sb 1
 * @run driver compiler.onSpinWait.TestOnSpinWaitAArch64 c1 nop 7
 * @run driver compiler.onSpinWait.TestOnSpinWaitAArch64 c1 isb 3
 * @run driver compiler.onSpinWait.TestOnSpinWaitAArch64 c1 yield 1
 * @run driver compiler.onSpinWait.TestOnSpinWaitAArch64 c1 sb 1
 */

package compiler.onSpinWait;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.ListIterator;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestOnSpinWaitAArch64 {

    public static void main(String[] args) throws Exception {
        String compiler = args[0];
        String spinWaitInst = args[1];
        String spinWaitInstCount = args[2];
        ArrayList<String> command = new ArrayList<String>();
        command.add("-XX:+IgnoreUnrecognizedVMOptions");
        command.add("-showversion");
        command.add("-XX:-BackgroundCompilation");
        command.add("-XX:+UnlockDiagnosticVMOptions");
        if (compiler.equals("c2")) {
            command.add("-XX:-TieredCompilation");
        } else if (compiler.equals("c1")) {
            command.add("-XX:+TieredCompilation");
            command.add("-XX:TieredStopAtLevel=1");
        } else {
            throw new RuntimeException("Unknown compiler: " + compiler);
        }
        command.add("-Xbatch");
        command.add("-XX:OnSpinWaitInst=" + spinWaitInst);
        command.add("-XX:OnSpinWaitInstCount=" + spinWaitInstCount);
        command.add("-XX:CompileCommand=compileonly," + Launcher.class.getName() + "::" + "test");
        command.add("-XX:CompileCommand=print," + Launcher.class.getName() + "::" + "test");
        command.add(Launcher.class.getName());

        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(command);

        OutputAnalyzer analyzer = new OutputAnalyzer(pb.start());

        if ("sb".equals(spinWaitInst) && analyzer.contains("CPU does not support SB")) {
            System.out.println("Skipping the test. The current CPU does not support SB instruction.");
            return;
        }

        analyzer.shouldHaveExitValue(0);

        System.out.println(analyzer.getOutput());

        checkOutput(analyzer, spinWaitInst, Integer.parseInt(spinWaitInstCount));
    }

    private static String getSpinWaitInstHex(String spinWaitInst) {
      if ("nop".equals(spinWaitInst)) {
          return "1f2003d5";
      } else if ("isb".equals(spinWaitInst)) {
          return "df3f03d5";
      } else if ("yield".equals(spinWaitInst)) {
          return "3f2003d5";
      } else if ("sb".equals(spinWaitInst)) {
          return "ff3003d5";
      } else {
          throw new RuntimeException("Unknown spin wait instruction: " + spinWaitInst);
      }
    }

    private static int countInstructions(Iterator<String> iter, String instr) {
        String line = null;
        int foundInstCount = 0;
        while (iter.hasNext()) {
            line = iter.next().trim();
            if (line.startsWith(";; }")) {
                break;
            }

            if (!line.startsWith("0x")) {
                continue;
            }
            int pos = line.indexOf(':');
            if (pos == -1 || pos == line.length() - 1) {
                continue;
            }

            line = line.substring(pos + 1).replaceAll("\\s", "");
            if (line.startsWith(";")) {
                continue;
            }

            for (String s : line.split("\\|")) {
                if (!s.startsWith(instr)) {
                    return foundInstCount;
                }
                foundInstCount++;
            }
        }
        return foundInstCount;
    }

    // The expected output for a spin wait with three NOPs
    // if the hsdis library is available:
    //
    // ;; spin_wait {
    // 0x0000000111dfa58c:   nop
    // 0x0000000111dfa590:   nop
    // 0x0000000111dfa594:   nop
    // ;; }
    //
    // We work as follows:
    // 1. Check whether printed instructions are disassembled ("[Disassembly]").
    // 2. Look for the block comment ';; spin_wait {'.
    // 3. Count spin wait instructions.
    private static void checkOutput(OutputAnalyzer output, String spinWaitInst, int spinWaitInstCount) {
        Iterator<String> iter = output.asLines().listIterator();
        String line = null;
        boolean isDisassembled = false;
        while (iter.hasNext()) {
            line = iter.next();
            if (line.contains("[Disassembly]")) {
                isDisassembled = true;
                break;
            }

            if (line.contains("[MachCode]")) {
                break;
            }
        }

        boolean foundSpinWaitBlock = false;
        while (iter.hasNext()) {
            line = iter.next();
            if (line.contains(";; spin_wait {")) {
                foundSpinWaitBlock = true;
                break;
            }
        }

        if (!foundSpinWaitBlock) {
            throw new RuntimeException("Block comment ';; spin_wait {' not found");
        }

        final String expectedInstInOutput = isDisassembled ? spinWaitInst : getSpinWaitInstHex(spinWaitInst);
        final int foundInstCount = countInstructions(iter, expectedInstInOutput);

        if (foundInstCount != spinWaitInstCount) {
            throw new RuntimeException("Expect " + spinWaitInstCount + " " + spinWaitInst + " instructions. Found: " + foundInstCount);
        }
    }

    static class Launcher {
        public static void main(final String[] args) throws Exception {
            int end = 20_000;

            for (int i=0; i < end; i++) {
                test();
            }
        }
        static void test() {
            java.lang.Thread.onSpinWait();
        }
    }
}
