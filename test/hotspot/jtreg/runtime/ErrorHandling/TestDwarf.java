/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @test id=checkDecoder
 * @bug 8242181
 * @library / /test/lib
 * @summary Test DWARF parser with various crashes if debug symbols are available. If the libjvm debug symbols are not
 *          in the same directory as the libjvm.so file, in a subdirectory called .debug, or in the path specified
 *          by the environment variable _JVM_DWARF_PATH, then no verification of the hs_err_file is done for libjvm.so.
 * @requires vm.debug == true & vm.flagless & vm.compMode != "Xint" & os.family == "linux" & !vm.graal.enabled & vm.gc.G1
 * @modules java.base/jdk.internal.misc
 * @run main/native/othervm -Xbootclasspath/a:. -XX:-CreateCoredumpOnCrash -DcheckDecoder=true TestDwarf
 */

/*
 * @test id=dontCheckDecoder
 * @library / /test/lib
 * @requires vm.debug == true & vm.flagless & vm.compMode != "Xint" & os.family == "linux" & !vm.graal.enabled & vm.gc.G1
 * @modules java.base/jdk.internal.misc
 * @run main/native/othervm -Xbootclasspath/a:. -XX:-CreateCoredumpOnCrash -DcheckDecoder=false TestDwarf
 */

import jdk.test.lib.Asserts;
import jdk.test.lib.Platform;
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

    static boolean checkDecoder = Boolean.getBoolean("checkDecoder");

    public static void main(String[] args) throws Exception {
        if (args.length != 0) {
            switch (args[0]) {
                case "unsafeAccess" -> {
                    crashUnsafeAccess();
                    Asserts.fail("Should crash in crashUnsafeAccess()");
                }
                case "outOfMemory" -> {
                    crashOutOfMemory();
                    Asserts.fail("Should crash in crashOutOfMemory()");
                }
                case "abortVMOnException" -> {
                    crashAbortVmOnException();
                    Asserts.fail("Should crash in crashAbortVmOnException()");
                }
                case "nativeDivByZero" -> {
                    crashNativeDivByZero();
                    Asserts.fail("Should crash in crashNativeDivByZero()");
                }
                case "nativeMultipleMethods" -> {
                    crashNativeMultipleMethods(1);
                    crashNativeMultipleMethods(2);
                    crashNativeMultipleMethods(3);
                    Asserts.fail("Should crash in crashNativeMultipleMethods()");
                    crashNativeMultipleMethods(4);
                }
                case "nativeDereferenceNull" -> {
                    crashNativeDereferenceNull();
                    Asserts.fail("Should crash in crashNativeDereferenceNull()");
                }
            }
        } else {
            try {
                test();
            } catch (UnsupportedDwarfVersionException e) {
                System.out.println("Skip test due to a DWARF section that is in an unsupported version by the parser.");
            }
        }
    }

    // Crash the VM in different ways in order to verify that DWARF parsing is able to print the source information
    // in the hs_err_files for each VM and C stack frame.
    private static void test() throws Exception {
        runAndCheck(new Flags("-Xcomp", "-XX:CICrashAt=1", "--version"));
        runAndCheck(new Flags("-Xmx100M", "-XX:ErrorHandlerTest=15", "-XX:TestCrashInErrorHandler=14", "--version"));
        runAndCheck(new Flags("-XX:+CrashGCForDumpingJavaThread", "--version"));
        runAndCheck(new Flags("-Xmx10m", "-XX:+CrashOnOutOfMemoryError", TestDwarf.class.getCanonicalName(), "outOfMemory"));
        // Use -XX:-TieredCompilation as C1 is currently not aborting the VM (JDK-8264899).
        runAndCheck(new Flags(TestDwarf.class.getCanonicalName(), "unsafeAccess"));
        runAndCheck(new Flags("-XX:-TieredCompilation", "-XX:+UnlockDiagnosticVMOptions", "-XX:AbortVMOnException=MyException",
                              TestDwarf.class.getCanonicalName(), "abortVMOnException"));
        if (Platform.isX64() || Platform.isX86()) {
            // Not all platforms raise SIGFPE but x86_32 and x86_64 do.
            runAndCheck(new Flags(TestDwarf.class.getCanonicalName(), "nativeDivByZero"),
                        new DwarfConstraint(0, "Java_TestDwarf_crashNativeDivByZero", "libTestDwarf.c", 59));
            runAndCheck(new Flags(TestDwarf.class.getCanonicalName(), "nativeMultipleMethods"),
                        new DwarfConstraint(0, "foo", "libTestDwarf.c", 42),
                        new DwarfConstraint(1, "Java_TestDwarf_crashNativeMultipleMethods", "libTestDwarf.c", 70));
        }
        runAndCheck(new Flags(TestDwarf.class.getCanonicalName(), "nativeDereferenceNull"),
                    new DwarfConstraint(0, "dereference_null", "libTestDwarfHelper.h", 44));
    }

    // The full pattern accepts lines like:
    //  V [libjvm.so+0x8f4ed8] report_fatal(VMErrorType, char const*, int, char const*, ...)+0x78 (debug.cpp:212)
    // but if the decoder is not available we only get
    //  V [libjvm.so+0x8f4ed8] (debug.cpp:212)
    private static final String FULL_PATTERN ="[CV][\\s\\t]+\\[([a-zA-Z0-9_.]+)\\+0x.+][\\s\\t]+.*\\+0x.+[\\s\\t]+\\([a-zA-Z0-9_.]+\\.[a-z]+:[1-9][0-9]*\\)";
    private static final String NO_DECODER_PATTERN ="[CV][\\s\\t]+\\[([a-zA-Z0-9_.]+)\\+0x.+].*\\([a-zA-Z0-9_.]+\\.[a-z]+:[1-9][0-9]*\\)";

    private static void runAndCheck(Flags flags, DwarfConstraint... constraints) throws Exception {
        OutputAnalyzer crashOut;
        crashOut = ProcessTools.executeProcess(ProcessTools.createTestJvm(flags.getFlags()));
        String crashOutputString = crashOut.getOutput();
        Asserts.assertNotEquals(crashOut.getExitValue(), 0, "Crash JVM should not exit gracefully");
        System.out.println(crashOutputString);

        File hs_err_file = HsErrFileUtils.openHsErrFileFromOutput(crashOut);
        try (FileReader fr = new FileReader(hs_err_file);
             BufferedReader reader = new BufferedReader(fr)) {
            String line;
            boolean foundNativeFrames = false;
            int matches = 0;
            int frameIdx = 0;

            Pattern pattern = Pattern.compile(checkDecoder ? FULL_PATTERN : NO_DECODER_PATTERN);

            // Check all stack entries after the line starting with "Native frames" in the hs_err_file until an empty line
            // is found which denotes the end of the stack frames.
            while ((line = reader.readLine()) != null) {
                if (foundNativeFrames) {
                    if (line.isEmpty()) {
                        // Done with the entire stack.
                        break;
                    } else if ((line.startsWith("C") || line.startsWith("V"))) {
                        // Could be VM or native C frame. There are usually no symbols available for libpthread.so.
                        matches++;
                        // File and library names are non-empty and may contain English letters, underscores, dots or numbers ([a-zA-Z0-9_.]+).
                        // Line numbers have at least one digit and start with non-zero ([1-9][0-9]*).
                        Matcher matcher = pattern.matcher(line);
                        if (!matcher.find()) {
                            checkMissingElement(crashOutputString, line);
                        }

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
        }
    }

    /**
     * After we failed to match the pattern, try to determine what element was missing.
     * There are some valid cases where we cannot find source information.
     */
    private static void checkMissingElement(String crashOutputString, String line) {
        // First check if we got the library name.
        Pattern pattern = Pattern.compile("[CV][\\s\\t]+\\[([a-zA-Z0-9_.-]+)\\+0x.+]");
        Matcher matcher = pattern.matcher(line);
        Asserts.assertTrue(matcher.find(), "Must find library name in \"" + line + "\"");

        // Check if there are symbols available for library. If not, then we cannot find any source information for this library.
        // This can happen if this test is run without any JDK debug symbols at all but also for some libraries like libpthread.so
        // which usually has no symbols available.
        String library = matcher.group(1);
        pattern = Pattern.compile("Failed to load DWARF file for library.*" + library + ".*or find DWARF sections directly inside it");
        matcher = pattern.matcher(crashOutputString);
        if (!matcher.find()) {
            // Symbols were fine so check if we expected decoder output and didn't find it.
            if (checkDecoder) {
                pattern = Pattern.compile(NO_DECODER_PATTERN);
                matcher = pattern.matcher(line);
                if (matcher.find()) {
                    Asserts.fail("Could not find decoded method signature in \"" + line + "\"");
                }
            }
            bailoutIfUnsupportedDwarfVersion(crashOutputString);
            Asserts.fail("Could not find filename or line number in \"" + line + "\"");
        }
        // We should always find symbols for libTestDwarf.so.
        Asserts.assertFalse(library.equals("libTestDwarf.so"), "Could not find filename or line number in \"" + line + "\" for libTestDwarf.so");
        System.out.println("Did not find symbols for " + library + ". If they are not in the same directory as " + library + " consider setting " +
                           "the environmental variable _JVM_DWARF_PATH to point to the debug symbols directory.");
    }

    /**
     * Some older GCC versions might emit DWARF sections in an old format that is not supported by the DWARF parser.
     * If this is the case, skip this entire test by throwing UnsupportedDwarfVersionException.
     */
    private static void bailoutIfUnsupportedDwarfVersion(String crashOutputString) {
        Pattern pattern = Pattern.compile(".debug_\\S+ in unsupported DWARF version \\d+");
        Matcher matcher = pattern.matcher(crashOutputString);
        if (matcher.find()) {
            throw new UnsupportedDwarfVersionException();
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

        // Loop endlessly and consume memory until we run out. Will crash due to -XX:+CrashOnOutOfMemoryError.
        while (true) {
            o = new Object[] {o};
        }
    }

    // Crash with Internal Error: Saw java.lang.RuntimeException, aborting.
    // Crash happens due to an exception raised in combination with -XX:AbortVMOnException.
    private static void crashAbortVmOnException() {
        throw new MyException();
    }

    private static native void crashNativeDivByZero();
    private static native void crashNativeDereferenceNull();
    private static native void crashNativeMultipleMethods(int x);
}

class UnsupportedDwarfVersionException extends RuntimeException { }

class MyException extends RuntimeException { }

class Flags {
    private final List<String> listOfOptions = new ArrayList<>();

    Flags(String... flags) {
        listOfOptions.add("-XX:TraceDwarfLevel=2"); // Always add debug flag
        listOfOptions.add("-XX:-CreateCoredumpOnCrash"); // Never create dumps
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
