/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.Utils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/*
 * @test StackWalkNativeToJava
 * @bug 8316309
 * @summary Check that walking the stack works fine when going from C++ frame to Java frame.
 * @requires os.arch=="amd64" | os.arch=="x86_64" | os.arch=="aarch64"
 * @requires os.family != "windows"
 * @requires vm.flagless
 * @library /test/lib
 * @run driver StackWalkNativeToJava
 */

public class StackWalkNativeToJava {

    public static void main(String[] args) throws Exception {
        // Check stack walking works fine when sender of C++ frame
        // is a Java native method.
        testStackWalkNativeToJavaNative("-Xint");
        testStackWalkNativeToJavaNative("-Xcomp", "-XX:CompileCommand=dontinline,StackWalkNativeToJava$TestNativeToJavaNative::*");

        // Check stack walking works fine when sender of C++ frame
        // is a runtime stub or interpreted Java method (VM call from Java).
        testStackWalkNativeToJava("-Xint");
        testStackWalkNativeToJava("-Xcomp", "-XX:TieredStopAtLevel=3",
                                  "-XX:CompileCommand=dontinline,StackWalkNativeToJava$TestNativeToJava::*");
    }

    public static void testStackWalkNativeToJavaNative(String... extraFlags) throws Exception {
        List<String> commands = new ArrayList<>();
        commands.add("-Xbootclasspath/a:.");
        commands.add("-XX:-CreateCoredumpOnCrash");
        commands.add("-XX:+UnlockDiagnosticVMOptions");
        commands.add("-XX:AbortVMOnException=java.lang.IllegalMonitorStateException");
        commands.add("-XX:+ErrorFileToStdout");
        commands.addAll(Arrays.asList(extraFlags));
        commands.add("StackWalkNativeToJava$TestNativeToJavaNative");

        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(commands);
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldNotContain("java.lang.RuntimeException: Reached statement after obj.wait()");
        output.shouldNotContain("[error occurred during error reporting (printing native stack");
        String[] res = output.getOutput().split("StackWalkNativeToJava\\$TestNativeToJavaNative\\.callNativeMethod\\(\\)V");
        assertTrue(res.length - 1 == 2, res.length - 1);
        output.shouldNotHaveExitValue(0);
    }

    public static class TestNativeToJavaNative {
        public static void main(String[] args) throws Exception {
            TestNativeToJavaNative test = new TestNativeToJavaNative();
            test.callNativeMethod();
        }

        public void callNativeMethod() throws Exception {
            Object obj = new Object();
            // Trigger a fatal exit due to IllegalMonitorStateException during
            // a call to the VM from a Java native method.
            obj.wait();
            throw new RuntimeException("Reached statement after obj.wait()");
        }
    }

    public static void testStackWalkNativeToJava(String... extraFlags) throws Exception {
        List<String> commands = new ArrayList<>();
        commands.add("-Xbootclasspath/a:.");
        commands.add("-XX:-CreateCoredumpOnCrash");
        commands.add("-XX:+UnlockDiagnosticVMOptions");
        commands.add("-XX:DiagnoseSyncOnValueBasedClasses=1");
        commands.add("-XX:+ErrorFileToStdout");
        commands.addAll(Arrays.asList(extraFlags));
        commands.add("StackWalkNativeToJava$TestNativeToJava");

        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(commands);
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldNotContain("java.lang.RuntimeException: Reached statement after synchronized");
        output.shouldNotContain("[error occurred during error reporting (printing native stack");
        String[] res = output.getOutput().split("StackWalkNativeToJava\\$TestNativeToJava\\.callVMMethod\\(\\)V");
        assertTrue(res.length - 1 == 2, res.length - 1);
        output.shouldNotHaveExitValue(0);
    }

    public static class TestNativeToJava {
        static Integer counter = 0;

        public static void main(String[] args) throws Exception {
            TestNativeToJava test = new TestNativeToJava();
            test.callVMMethod();
        }

        public void callVMMethod() throws Exception {
            // Trigger a fatal exit for trying to synchronize on a value based class
            // during a call to the VM from a Java method.
            synchronized (counter) {
                counter++;
            }
            throw new RuntimeException("Reached statement after synchronized");
        }
    }

    private static void assertTrue(boolean condition, int count) {
        if (!condition) {
            throw new RuntimeException("Count error: count was " + count);
        }
    }
}