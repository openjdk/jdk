/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8272586
 * @requires vm.compiler2.enabled
 * @summary Test that abstract machine code is dumped for the top frames in a hs-err log
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.compiler
 *          java.management
 *          jdk.internal.jvmstat/sun.jvmstat.monitor
 * @run driver MachCodeFramesInErrorFile
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.Asserts;

import jdk.internal.misc.Unsafe;

public class MachCodeFramesInErrorFile {
    private static class Crasher {
        // Need to make unsafe a compile-time constant so that
        // C2 intrinsifies the call to Unsafe.getLong in method3.
        private static final Unsafe unsafe = Unsafe.getUnsafe();
        public static void main(String[] args) {
            method1(10);
        }

        static void method1(long address) {
            System.out.println("in method1");
            method2(address);
        }
        static void method2(long address) {
            System.out.println("in method2");
            method3(address);
        }
        static void method3(long address) {
            System.out.println("in method3");
            // Keep chasing pointers until we crash
            while (true) {
                address = unsafe.getLong(address);
            }
        }
    }

    /**
     * Runs Crasher and tries to force compile the methods in Crasher. The inner
     * most method crashes the VM with Unsafe. The resulting hs-err log is
     * expected to have a min number of MachCode sections.
     */
    public static void main(String[] args) throws Exception {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
            "-Xmx64m", "--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED",
            "-XX:-CreateCoredumpOnCrash",
            "-Xcomp",
            "-XX:-TieredCompilation", // ensure C2 compiles Crasher.method3
            "-XX:CompileCommand=compileonly,MachCodeFramesInErrorFile$Crasher.m*",
            "-XX:CompileCommand=dontinline,MachCodeFramesInErrorFile$Crasher.m*",
            Crasher.class.getName());
        OutputAnalyzer output = new OutputAnalyzer(pb.start());

        // Extract hs_err_pid file.
        String hs_err_file = output.firstMatch("# *(\\S*hs_err_pid\\d+\\.log)", 1);
        if (hs_err_file == null) {
            throw new RuntimeException("Did not find hs_err_pid file in output.\n" +
                                       "stderr:\n" + output.getStderr() + "\n" +
                                       "stdout:\n" + output.getStdout());
        }
        Path hsErrPath = Paths.get(hs_err_file);
        if (!Files.exists(hsErrPath)) {
            throw new RuntimeException("hs_err_pid file missing at " + hsErrPath + ".\n");
        }
        String hsErr = Files.readString(hsErrPath);
        if (!crashedInCrasherMethod(hsErr)) {
            return;
        }
        List<String> nativeFrames = extractNativeFrames(hsErr);
        int compiledJavaFrames = (int) nativeFrames.stream().filter(f -> f.startsWith("J ")).count();

        Matcher matcherDisasm = Pattern.compile("\\[Disassembly\\].*\\[/Disassembly\\]", Pattern.DOTALL).matcher(hsErr);
        if (matcherDisasm.find()) {
            // Real disassembly is present, no MachCode is expected.
            return;
        }

        Matcher matcher = Pattern.compile("\\[MachCode\\]\\s*\\[Verified Entry Point\\]\\s*  # \\{method\\} \\{[^}]*\\} '([^']+)' '([^']+)' in '([^']+)'", Pattern.DOTALL).matcher(hsErr);
        List<String> machCodeHeaders = matcher.results().map(mr -> String.format("'%s' '%s' in '%s'", mr.group(1), mr.group(2), mr.group(3))).collect(Collectors.toList());
        String message = "Mach code headers: " + machCodeHeaders +
                         "\n\nExtracted MachCode:\n" + extractMachCode(hsErr) +
                         "\n\nExtracted native frames:\n" + String.join("\n", nativeFrames);
        int minExpectedMachCodeSections = Math.max(1, compiledJavaFrames);
        Asserts.assertTrue(machCodeHeaders.size() >= minExpectedMachCodeSections, message);
    }

    /**
     * Checks whether the crashing frame is in {@code Crasher.method3}.
     */
    private static boolean crashedInCrasherMethod(String hsErr) {
        boolean checkProblematicFrame = false;
        for (String line : hsErr.split(System.lineSeparator())) {
            if (line.startsWith("# Problematic frame:")) {
                checkProblematicFrame = true;
            } else if (checkProblematicFrame) {
                String crasherMethod = Crasher.class.getSimpleName() + ".method3";
                if (!line.contains(crasherMethod)) {
                    // There's any number of things that can subvert the attempt
                    // to crash in the expected method.
                    System.out.println("Crashed in method other than " + crasherMethod + "\n\n" + line + "\n\nSkipping rest of test.");
                    return false;
                }
                return true;
            }
        }
        throw new RuntimeException("\"# Problematic frame:\" line missing in hs_err_pid file:\n" + hsErr);
    }

    /**
     * Gets the lines in {@code hsErr} below the line starting with "Native frames:" up to the next blank line.
     */
    private static List<String> extractNativeFrames(String hsErr) {
        List<String> res = new ArrayList<>();
        boolean inNativeFrames = false;
        for (String line : hsErr.split(System.lineSeparator())) {
            if (line.startsWith("Native frames: ")) {
                inNativeFrames = true;
            } else if (inNativeFrames) {
                if (line.trim().isEmpty()) {
                    return res;
                }
                res.add(line);
            }
        }
        throw new RuntimeException("\"Native frames:\" line missing in hs_err_pid file:\n" + hsErr);
    }

    private static String extractMachCode(String hsErr) {
        int start = hsErr.indexOf("[MachCode]");
        if (start != -1) {
            int end = hsErr.lastIndexOf("[/MachCode]");
            if (end != -1) {
                return hsErr.substring(start, end + "[/MachCode]".length());
            }
            return hsErr.substring(start);
        }
        return null;
    }
}
