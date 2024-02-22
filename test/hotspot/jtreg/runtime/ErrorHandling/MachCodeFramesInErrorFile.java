/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @requires vm.flagless
 * @requires vm.compiler2.enabled
 * @summary Test that abstract machine code is dumped for the top frames in a hs-err log
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.compiler
 *          java.management
 *          jdk.internal.jvmstat/sun.jvmstat.monitor
 * @run driver MachCodeFramesInErrorFile
 */

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.Asserts;

import jdk.internal.misc.Unsafe;

public class MachCodeFramesInErrorFile {

    private static class Crasher {
        // Make Crasher.unsafe a compile-time constant so that
        // C2 intrinsifies calls to Unsafe intrinsics.
        private static final Unsafe unsafe = Unsafe.getUnsafe();

        public static void main(String[] args) throws Exception {
            if (args[0].equals("crashInJava")) {
                // This test relies on Unsafe.putLong(Object, long, long) being intrinsified
                if (!Stream.of(Unsafe.class.getDeclaredMethod("putLong", Object.class, long.class, long.class).getAnnotations()).
                    anyMatch(a -> a.annotationType().getName().equals("jdk.internal.vm.annotation.IntrinsicCandidate"))) {
                    throw new RuntimeException("Unsafe.putLong(Object, long, long) is not an intrinsic");
                }
                crashInJava1(10);
            } else {
                assert args[0].equals("crashInVM");
                crashInNative1(10);
            }
        }

        static void crashInJava1(long address) {
            System.out.println("in crashInJava1");
            crashInJava2(address);
        }
        static void crashInJava2(long address) {
            System.out.println("in crashInJava2");
            crashInJava3(address);
        }
        static void crashInJava3(long address) {
            unsafe.putLong(null, address, 42);
            System.out.println("wrote value to 0x" + Long.toHexString(address));
        }

        static void crashInNative1(long address) {
            System.out.println("in crashInNative1");
            crashInNative2(address);
        }
        static void crashInNative2(long address) {
            System.out.println("in crashInNative2");
            crashInNative3(address);
        }
        static void crashInNative3(long address) {
            System.out.println("read value " + unsafe.getLong(address) + " from 0x" + Long.toHexString(address));
        }
    }

    public static void main(String[] args) throws Exception {
        run(true);
        run(false);
    }

    /**
     * Runs Crasher in Xcomp mode. The inner
     * most method crashes the VM with Unsafe. The resulting hs-err log is
     * expected to have a min number of MachCode sections.
     */
    private static void run(boolean crashInJava) throws Exception {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
            "-Xmx64m", "--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED",
            "-XX:-CreateCoredumpOnCrash",
            "-Xcomp",
            "-XX:-TieredCompilation",
            "-XX:CompileCommand=compileonly,MachCodeFramesInErrorFile$Crasher.crashIn*",
            "-XX:CompileCommand=dontinline,MachCodeFramesInErrorFile$Crasher.crashIn*",
            "-XX:CompileCommand=dontinline,*/Unsafe.getLong", // ensures VM call when crashInJava == false
            Crasher.class.getName(),
            crashInJava ? "crashInJava" : "crashInVM");
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
        if (System.getenv("DEBUG") != null) {
            System.err.println(hsErr);
        }
        Set<String> frames = new HashSet<>();
        extractFrames(hsErr, frames, true);
        if (!crashInJava) {
            // A crash in native will have Java frames in the hs-err log
            // as there is a Java frame anchor on the stack.
            extractFrames(hsErr, frames, false);
        }
        int compiledJavaFrames = (int) frames.stream().filter(f -> f.startsWith("J ")).count();

        Matcher matcherDisasm = Pattern.compile("\\[Disassembly\\].*\\[/Disassembly\\]", Pattern.DOTALL).matcher(hsErr);
        if (matcherDisasm.find()) {
            // Real disassembly is present, no MachCode is expected.
            return;
        }

        Matcher matcher = Pattern.compile("\\[MachCode\\]\\s*\\[Verified Entry Point\\]\\s*  # \\{method\\} \\{[^}]*\\} '([^']+)' '([^']+)' in '([^']+)'", Pattern.DOTALL).matcher(hsErr);
        List<String> machCodeHeaders = matcher.results().map(mr -> String.format("'%s' '%s' in '%s'", mr.group(1), mr.group(2), mr.group(3))).collect(Collectors.toList());
        int minExpectedMachCodeSections = Math.max(1, compiledJavaFrames);
        if (machCodeHeaders.size() < minExpectedMachCodeSections) {
            Asserts.fail(machCodeHeaders.size() + " < " + minExpectedMachCodeSections);
        }
    }

    /**
     * Extracts the lines in {@code hsErr} below the line starting with
     * "Native frames:" or "Java frames:" up to the next blank line
     * and adds them to {@code frames}.
     */
    private static void extractFrames(String hsErr, Set<String> frames, boolean nativeStack) {
        String marker = (nativeStack ? "Native" : "Java") + " frames: ";
        boolean seenMarker = false;
        for (String line : hsErr.split(System.lineSeparator())) {
            if (line.startsWith(marker)) {
                seenMarker = true;
            } else if (seenMarker) {
                if (line.trim().isEmpty()) {
                    return;
                }
                frames.add(line);
            }
        }
        throw new RuntimeException("\"" + marker + "\" line missing in hs_err_pid file:\n" + hsErr);
    }
}
