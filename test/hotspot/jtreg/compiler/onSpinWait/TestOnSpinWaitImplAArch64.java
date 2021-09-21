/*
 * Copyright (c) 2021, Amazon.com Inc. or its affiliates. All rights reserved.
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
 * @test TestOnSpinWaitImplAArch64
 * @summary Checks that java.lang.Thread.onSpinWait is intrinsified with instructions specified in '-XX:OnSpinWaitImpl'
 * @bug 8186670
 * @library /test/lib
 *
 * @requires vm.flagless
 * @requires os.arch=="aarch64"
 *
 * @run driver compiler.onSpinWait.TestOnSpinWaitImplAArch64 c2 7 nop
 * @run driver compiler.onSpinWait.TestOnSpinWaitImplAArch64 c2 3 isb
 * @run driver compiler.onSpinWait.TestOnSpinWaitImplAArch64 c2 1 yield
 * @run driver compiler.onSpinWait.TestOnSpinWaitImplAArch64 c1 7 nop
 * @run driver compiler.onSpinWait.TestOnSpinWaitImplAArch64 c1 3 isb
 * @run driver compiler.onSpinWait.TestOnSpinWaitImplAArch64 c1 1 yield
 */

package compiler.onSpinWait;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.ListIterator;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestOnSpinWaitImplAArch64 {
    public static void main(String[] args) throws Exception {
        String compiler = args[0];
        String pauseImplInstCount = args[1];
        String pauseImplInst = args[2];
        ArrayList<String> command = new ArrayList<String>();
        command.add("-XX:+IgnoreUnrecognizedVMOptions");
        command.add("-showversion");
        command.add("-XX:-BackgroundCompilation");
        command.add("-XX:+UnlockDiagnosticVMOptions");
        command.add("-XX:+PrintAssembly");
        if (compiler.equals("c2")) {
            command.add("-XX:-TieredCompilation");
        } else if (compiler.equals("c1")) {
            command.add("-XX:+TieredCompilation");
            command.add("-XX:TieredStopAtLevel=1");
        } else {
            throw new RuntimeException("Unknown compiler: " + compiler);
        }
        command.add("-Xbatch");
        command.add("-XX:OnSpinWaitImpl=" + pauseImplInstCount + pauseImplInst);
        command.add("-XX:CompileCommand=compileonly," + Launcher.class.getName() + "::" + "test");
        command.add(Launcher.class.getName());

        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(command);

        OutputAnalyzer analyzer = new OutputAnalyzer(pb.start());

        analyzer.shouldHaveExitValue(0);

        System.out.println(analyzer.getOutput());

        checkOutput(analyzer, getPauseImplInstHex(pauseImplInst), Integer.parseInt(pauseImplInstCount));
    }

    private static String getPauseImplInstHex(String pauseImplInst) {
      if ("nop".equals(pauseImplInst)) {
          return "1f20 03d5";
      } else if ("isb".equals(pauseImplInst)) {
          return "df3f 03d5";
      } else if ("yield".equals(pauseImplInst)) {
          return "3f20 03d5";
      } else {
          throw new RuntimeException("Unknown pause implementation: " + pauseImplInst);
      }
    }

    private static void addInstrs(String line, ArrayList<String> instrs) {
        for (String instr : line.split("\\|")) {
            instrs.add(instr.trim());
        }
    }

    private static void checkOutput(OutputAnalyzer output, String pauseImplInstHex, int pauseImplInstCount) {
        Iterator<String> iter = output.asLines().listIterator();

        String match = skipTo(iter, "'test' '()V' in 'compiler/onSpinWait/TestOnSpinWaitImplAArch64$Launcher'");
        if (match == null) {
            throw new RuntimeException("Missing compiler output for the method compiler.onSpinWait.TestOnSpinWaitImplAArch64$Launcher::test");
        }

        ArrayList<String> instrs = new ArrayList<String>();
        while (iter.hasNext()) {
            String line = iter.next();
            if (line.contains("*invokestatic onSpinWait")) {
                break;
            }
            if (line.contains("0x") && !line.contains(";")) {
                addInstrs(line, instrs);
            }
        }

        if (!iter.hasNext() || !iter.next().contains("- compiler.onSpinWait.TestOnSpinWaitImplAArch64$Launcher::test@0") || !iter.hasNext()) {
            throw new RuntimeException("Missing compiler output for Thread.onSpinWait intrinsic");
        }

        String line = iter.next();
        if (!line.contains("0x") || line.contains(";")) {
            throw new RuntimeException("Expected hex instructions");
        }

        addInstrs(line, instrs);

        int foundInstCount = 0;

        ListIterator<String> instrReverseIter = instrs.listIterator(instrs.size());
        while (instrReverseIter.hasPrevious()) {
            if (instrReverseIter.previous().endsWith(pauseImplInstHex)) {
                foundInstCount = 1;
                break;
            }
        }

        while (instrReverseIter.hasPrevious()) {
            if (!instrReverseIter.previous().endsWith(pauseImplInstHex)) {
                break;
            }
            ++foundInstCount;
        }

        if (foundInstCount != pauseImplInstCount) {
            throw new RuntimeException("Wrong instruction " + pauseImplInstHex + " count " + foundInstCount + "!\n  -- expecting " + pauseImplInstCount);
        }
    }

    private static String skipTo(Iterator<String> iter, String substring) {
        while (iter.hasNext()) {
            String nextLine = iter.next();
            if (nextLine.contains(substring)) {
                return nextLine;
            }
        }
        return null;
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
