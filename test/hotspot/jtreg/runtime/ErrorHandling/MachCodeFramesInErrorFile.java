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
import java.util.Set;
import java.util.HashSet;
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
        public static void main(String[] args) {
            method1(0);
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
            method4(address);
        }
        static void method4(long address) {
            System.out.println("in method4");
            method5(address);
        }
        static void method5(long address) {
            System.out.println("in method5");
            method6(address);
        }
        static void method6(long address) {
            System.out.println("in method6");
            method7(address);
        }
        static void method7(long address) {
            System.out.println("in method7");
            method8(address);
        }
        static void method8(long address) {
            System.out.println("in method8");
            method9(address);
        }
        static void method9(long address) {
            System.out.println("in method9");
            Unsafe.getUnsafe().getInt(address);
        }
    }

    /**
     * Runs Crasher and forces each method in Crasher to be compiled. The inner
     * most method (i.e. method9) crashes the VM by reading from 0. The resulting
     * hs-err log is expected to have a MachCode section for each method in Crasher.
     */
    public static void main(String[] args) throws Exception {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
            "-Xmx64m", "--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED",
            "-XX:-CreateCoredumpOnCrash",
            "-Xcomp",
            "-XX:CompileCommand=compileonly,MachCodeFramesInErrorFile$Crasher.*",
            "-XX:CompileCommand=dontinline,MachCodeFramesInErrorFile$Crasher.*",
            Crasher.class.getName());
        OutputAnalyzer output = new OutputAnalyzer(pb.start());

        // Extract hs_err_pid file.
        String hs_err_file = output.firstMatch("# *(\\S*hs_err_pid\\d+\\.log)", 1);
        if (hs_err_file == null) {
            throw new RuntimeException("Did not find hs_err_pid file in output.\n");
        }

        Path f = Paths.get(hs_err_file);
        if (!Files.exists(f)) {
            throw new RuntimeException("hs_err_pid file missing at " + f + ".\n");
        }
        String hsErr = Files.readString(Paths.get(hs_err_file));
        System.out.println(hsErr);
        Matcher matcher = Pattern.compile("\\[MachCode\\]\\s*\\[Verified Entry Point\\]\\s*  # \\{method\\} \\{[^}]*\\} '([^']+)' '([^']+)' in '([^']+)'", Pattern.DOTALL).matcher(hsErr);
        Set<String> expect = Stream.of(Crasher.class.getDeclaredMethods()).map(method -> method.getName()).collect(Collectors.toSet());
        Set<String> actual = new HashSet<>();
        matcher.results().forEach(mr -> {
            if (mr.group(3).equals(Crasher.class.getName())) {
                actual.add(mr.group(1));
            }
        });
        Asserts.assertEquals(expect, actual);
    }
}
