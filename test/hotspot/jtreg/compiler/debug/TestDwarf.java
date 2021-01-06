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

/**
 * @test
 * @bug 8242181
 * @library / /test/lib
 * @summary Test DWARF parser with -XX:CICrashAt.
 * @requires vm.debug == true & vm.compMode != "Xint" & os.family == "linux"
 * @modules java.base/jdk.internal.misc
 * @build sun.hotspot.WhiteBox
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/native/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *      compiler.debug.TestDwarf
 */

package compiler.debug;

import jdk.test.lib.Asserts;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import sun.misc.Unsafe;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestDwarf {
    static {
        System.loadLibrary("TestDwarf");
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 0) {
            switch (args[0]) {
                case "unsafeAccess":
                    crashUnsafeAccess();
                    Asserts.fail("Should crash in crashUnsafeAccess()");
                    break;
                case "outOfMemory":
                    crashOutOfMemory();
                    Asserts.fail("Should crash in crashOutOfMemory()");
                    break;
                case "abortVMOnException":
                    crashAbortVmOnException();
                    Asserts.fail("Should crash in crashAbortVmOnException()");
                    break;
                case "nativeDivByZero":
                    crashNativeDivByZero();
                    Asserts.fail("Should crash in crashNativeDivByZero()");
                    break;
                case "nativeDereferenceNull":
                    crashNativeDereferenceNull();
                    Asserts.fail("Should crash in crashNativeDereferenceNull()");
                    break;
            }
        } else {
            test();
        }
    }

    // Crash the VM in different ways in order to verify that DWARF parsing is able to print the source information
    // in the hs_err_files for each VM and C stack frame.
    private static void test() throws Exception {
        runAndCheck(new Flags("-Xcomp", "-XX:CICrashAt=1", "--version"));
        runAndCheck(new Flags(TestDwarf.class.getCanonicalName(), "unsafeAccess"));
        runAndCheck(new Flags("-Xmx10m", "-XX:+CrashOnOutOfMemoryError", TestDwarf.class.getCanonicalName(), "outOfMemory"));
        runAndCheck(new Flags("-XX:AbortVMOnException=java.lang.RuntimeException", TestDwarf.class.getCanonicalName(), "abortVMOnException"));
        runAndCheck(new Flags("-Xmx100M", "-XX:ErrorHandlerTest=15", "-XX:TestCrashInErrorHandler=14", "--version"));
        runAndCheck(new Flags("-XX:+CrashGCForDumpingJavaThread", "--version"));
        runAndCheck(new Flags(TestDwarf.class.getCanonicalName(), "nativeDivByZero"),
                    new DwarfConstraint(0, "Java_compiler_debug_TestDwarf_crashNativeDivByZero", "libTestDwarf.c", 33));
        runAndCheck(new Flags(TestDwarf.class.getCanonicalName(), "nativeDereferenceNull"),
                    new DwarfConstraint(0, "dereference_null", "libTestDwarfHelper.h", 29),
                    new DwarfConstraint(1, "some_method", "libTestDwarf.c", 37),
                    new DwarfConstraint(2, "Java_compiler_debug_TestDwarf_crashNativeDereferenceNull", "libTestDwarf.c", 41));
    }

    private static void runAndCheck(Flags flags, DwarfConstraint... constraints) throws Exception {
        OutputAnalyzer crashOut;
        crashOut = ProcessTools.executeProcess(ProcessTools.createTestJvm(flags.getFlags()));
        String crashOutputString = crashOut.getOutput();
        Asserts.assertNotEquals(crashOut.getExitValue(), 0, "Crash JVM exits gracefully");
        Pattern pattern = Pattern.compile("hs_err_pid[0-9]*.log");
        Matcher matcher = pattern.matcher(crashOutputString);
        System.out.println(crashOutputString);
        if (matcher.find()) {
            String hsErrFileName = matcher.group();
            System.out.println("hs_err_file: " + hsErrFileName);
            File hs_err_file = new File(hsErrFileName);
            BufferedReader reader = new BufferedReader(new FileReader(hs_err_file));
            String line;
            boolean foundNativeFrames = false;
            int matches = 0;
            int frameIdx = 0;
            // Check all stack entries after the line starting with "Native frames" in the hs_err_file until an empty line
            // is found which denotes the end of the stack frames.
            while ((line = reader.readLine()) != null) {
                if (foundNativeFrames) {
                    if (line.isEmpty()) {
                        // Done with the entire stack.
                        break;
                    } else if (line.startsWith("C") || line.startsWith("V")) { // Could be VM or native C frame
                        matches++;
                        // File names are non-empty and may contain English letters, underscores or dots ([a-zA-Z_.]+).
                        // Line numbers have at least one digit and start with non-zero ([1-9][0-9]*).
                        pattern = Pattern.compile("[CV][\\s\\t]+\\[[a-zA-Z_.]+.so\\+0x.+][\\s\\t]+.*\\+0x.+[\\s\\t]+\\([a-zA-Z_.]+\\.[a-z]+:[1-9][0-9]*\\)");
                        matcher = pattern.matcher(line);
                        Asserts.assertTrue(matcher.find(), "Could not find filename or line number in \"" + line + "\"");

                        // Check additional DWARF constraints
                        if (constraints != null) {
                            int finalFrameIdx = frameIdx;
                            String finalLine = line;
                            Arrays.stream(constraints).forEach(c -> c.checkConstraint(finalFrameIdx, finalLine));
                        }
                    }
                    frameIdx++;
                } else if (line.startsWith("Native frames")) {
                    // Stack starts after this line.
                    foundNativeFrames = true;
                }
            }
            Asserts.assertGreaterThan(matches, 0, "Could not find any stack frames");
        } else {
            throw new RuntimeException("Could not find an hs_err_file");
        }
    }

    // Crash with SIGSEGV.
    private static void crashUnsafeAccess() throws Exception {
        Field f = Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        Unsafe unsafe = (Unsafe)f.get(null);
        unsafe.putAddress(0, 0); // Crash
    }

    // Crash with Internal Error: Java heap space.
    private static void crashOutOfMemory() {
        Object[] o = null;

        // Loop endlessly and consume memory until we run out. Will crash due -XX:+CrashOnOutOfMemoryError.
        while (true) {
            o = new Object[] {o};
        }
    }

    // Crash with Internal Error: Saw java.lang.RuntimeException, aborting.
    // Crash happens due to an exception raised in combination with -XX:AbortVMOnException.
    private static void crashAbortVmOnException() {
        throw new RuntimeException();
    }

    private static native void crashNativeDivByZero();
    private static native void crashNativeDereferenceNull();
}

class Flags {
    private final List<String> listOfOptions = new ArrayList<>();

    Flags(String... flags) {
        listOfOptions.add("-Xlog:dwarf=info"); // Always add debug flag
        listOfOptions.addAll(Arrays.asList(flags));
    }

    public List<String> getFlags() {
        return listOfOptions;
    }

}
class DwarfConstraint {
    private final int frameIdx;
    private final String methodName;
    private final String dwarfInfo;

    DwarfConstraint(int frameIdx, String methodName, String fileName, int lineNo) {
        this.frameIdx = frameIdx;
        this.methodName = methodName;
        this.dwarfInfo = "(" + fileName + ":" + lineNo + ")";
    }

    public void checkConstraint(int currentFrameIdx, String line) {
        if (frameIdx == currentFrameIdx) {
            Asserts.assertTrue(line.contains(methodName), "Could not find method name " + methodName + " in \"" + line + "\"");
            Asserts.assertTrue(line.contains(dwarfInfo) , "Could not find DWARF info " + dwarfInfo + " in \"" + line + "\"");
        }
    }
}
