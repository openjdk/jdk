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
 * @summary Checks that java.lang.Thread.onSpinWait is intrinsified with instructions specified in '-XX:UsePauseImpl'
 * @bug 8186670
 * @library /test/lib
 *
 * @requires vm.flagless
 * @requires os.arch=="aarch64"
 *
 * @run driver compiler.onSpinWait.TestOnSpinWaitImplAArch64 4 nop
 * @run driver compiler.onSpinWait.TestOnSpinWaitImplAArch64 3 isb
 * @run driver compiler.onSpinWait.TestOnSpinWaitImplAArch64 2 yield
 */

package compiler.onSpinWait;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.regex.Pattern;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestOnSpinWaitImplAArch64 {
    public static void main(String[] args) throws Exception {
        String pauseImplInst = args[1];
        String pauseImplInstCount = args[0];
        ArrayList<String> command = new ArrayList<String>();
        command.add("-XX:+IgnoreUnrecognizedVMOptions");
        command.add("-showversion");
        command.add("-XX:-BackgroundCompilation");
        command.add("-XX:+UnlockDiagnosticVMOptions");
        command.add("-XX:+PrintOptoAssembly");
        command.add("-XX:-TieredCompilation");
        command.add("-Xbatch");
        command.add("-XX:UsePauseImpl=" + pauseImplInstCount + pauseImplInst);
        command.add("-XX:CompileCommand=compileonly," + Launcher.class.getName() + "::" + "test");
        command.add(Launcher.class.getName());

        // Test C2 compiler
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(command);

        OutputAnalyzer analyzer = new OutputAnalyzer(pb.start());

        analyzer.shouldHaveExitValue(0);

        checkOutput(analyzer, pauseImplInst, Integer.parseInt(pauseImplInstCount));
    }

    private static void checkOutput(OutputAnalyzer output, String pauseImplInst, int pauseImplInstCount) {
        Iterator<String> iter = output.asLines().listIterator();

        String match = skipTo(iter, Pattern.quote("{method}"));
        if (match == null) {
            throw new RuntimeException("Missing compiler output for the method 'test'!\n\n" + output.getOutput());
        }

        match = skipTo(iter, Pattern.quote("- name:"));
        if (match == null) {
            throw new RuntimeException("Missing compiled method name!\n\n" + output.getOutput());
        }
        if (!match.contains("test")) {
            throw new RuntimeException("Wrong method " + match + "!\n  -- expecting 'test'\n\n" + output.getOutput());
        }

        match = skipTo(iter, Pattern.quote("! membar_onspinwait"));
        if (match == null) {
            throw new RuntimeException("Missing 'membar_onspinwait'!\n\n" + output.getOutput());
        }
        if (!match.contains(pauseImplInst)) {
            throw new RuntimeException("Wrong intruction " + match + "!\n  -- expecting " + pauseImplInst + "\n\n" + output.getOutput());
        }
        int foundInstCount = 1;
        while (foundInstCount < pauseImplInstCount) {
            if (!iter.hasNext()) {
                break;
            }
            String nextLine = iter.next();
            if (!nextLine.contains(pauseImplInst)) {
                break;
            }
            ++foundInstCount;
        }
        if (foundInstCount != pauseImplInstCount) {
            throw new RuntimeException("Wrong intruction " + pauseImplInst + " count " + foundInstCount + "!\n  -- expecting " + pauseImplInstCount + "\n\n" + output.getOutput());
        }
    }

    private static String skipTo(Iterator<String> iter, String substring) {
        while (iter.hasNext()) {
            String nextLine = iter.next();
            if (nextLine.matches(".*" + substring + ".*")) {
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
