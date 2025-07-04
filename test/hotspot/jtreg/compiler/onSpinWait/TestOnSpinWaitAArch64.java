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

    private static String retInst = "ret";
    private static String neededAddInst = "addsp,sp,#0x20";
    private static String neededLdpInst = "ldpx29,x30,[sp,#16]";

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

        if (analyzer.contains("[MachCode]")) {
            spinWaitInst = getSpinWaitInstHex(spinWaitInst);
            retInst = "c0035fd6";
            neededAddInst = "ff830091";
            neededLdpInst = "fd7b41a9";
        }
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

    private static ArrayList<String> getInstrs(OutputAnalyzer output) {
        Iterator<String> iter = output.asLines().listIterator();

        String line = null;
        ArrayList<String> instrs = new ArrayList<String>();
        while (iter.hasNext()) {
            line = iter.next().trim();
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

            for (String instr : line.split("\\|")) {
                if (instr.startsWith(retInst)) {
                    return instrs;
                }
                instrs.add(instr);
            }
        }
        return instrs;
    }

    // The expected output of PrintAssembly for example for a spin wait with three NOPs
    // if JVM finds the hsdis library the output is like:
    //
    // 0x0000000111dfa588:   b.ne    0x0000000111dfa5c4
    // 0x0000000111dfa58c:   nop
    // 0x0000000111dfa590:   nop
    // 0x0000000111dfa594:   nop
    // 0x0000000111dfa598:   ldp    x29, x30, [sp, #16]
    // 0x0000000111dfa59c:   add    sp, sp, #0x20
    // 0x0000000111dfa5a0:   ldr    x8, [x28, #40]              ;   {poll_return}
    // 0x0000000111dfa5a4:   cmp    sp, x8
    // 0x0000000111dfa5a8:   b.hi    0x0000000111dfa5b0  // b.pmore
    // 0x0000000111dfa5ac:   ret
    //
    // We work as follows:
    // 1. Check whether printed instructions are disassembled ("[Disassembly]") or in hex form ("[MachCode]").
    // 2. Look for RET instruction and collect all seen instructions.
    // 3. In reverse order, search for 'add    sp, sp, #0x20' and 'ldp    x29, x30, [sp, #16]'.
    // 4. Count spin wait instructions.
    private static void checkOutput(OutputAnalyzer output, String spinWaitInst, int spinWaitInstCount) {
        ArrayList<String> instrs = getInstrs(output);

        // From the end of the list, look for the following instructions:
        //   ldp     x29, x30, [sp, #16]
        //   add     sp, sp, #0x20
        // or their hex form if a disassembler is not available:
        //   fd7b41a9
        //   ff830091
        ListIterator<String> instrReverseIter = instrs.listIterator(instrs.size());
        while (instrReverseIter.hasPrevious()) {
          String s = instrReverseIter.previous();
          instrReverseIter.next();
            if (instrReverseIter.previous().startsWith(neededAddInst)) {
                break;
            }
        }

        int foundInstCount = 0;
        if (instrReverseIter.hasPrevious() && instrReverseIter.previous().startsWith(neededLdpInst)) {
            while (instrReverseIter.hasPrevious()) {
                if (!instrReverseIter.previous().startsWith(spinWaitInst)) {
                    break;
                }
                ++foundInstCount;
            }
        }

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
