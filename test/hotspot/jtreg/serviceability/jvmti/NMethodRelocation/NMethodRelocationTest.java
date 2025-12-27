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

/*
 * @test
 * @bug 8316694
 * @summary Verify that nmethod relocation posts the correct JVMTI events
 * @requires vm.jvmti
 * @requires vm.gc == "null" | vm.gc == "Serial"
 * @requires vm.flavor == "server" & (vm.opt.TieredStopAtLevel == null | vm.opt.TieredStopAtLevel == 4)
 * @requires !vm.emulatedClient
 * @library /test/lib /test/hotspot/jtreg
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/native NMethodRelocationTest
 */

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Executable;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.test.lib.Asserts;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.whitebox.WhiteBox;
import jdk.test.whitebox.code.BlobType;
import jdk.test.whitebox.code.NMethod;

import static compiler.whitebox.CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION;

public class NMethodRelocationTest {
    public static void main(String[] args) throws Exception {
        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
                "-agentlib:NMethodRelocationTest",
                "--enable-native-access=ALL-UNNAMED",
                "-Xbootclasspath/a:.",
                "-Xbatch",
                "-XX:+UseSerialGC",
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:+WhiteBoxAPI",
                "-XX:+SegmentedCodeCache",
                "-XX:-TieredCompilation",
                "-XX:+UnlockExperimentalVMOptions",
                "-XX:+NMethodRelocation",
                "DoWork");

        Process process = pb.start();

        // Read output as stream
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {

            Pattern loadPattern = Pattern.compile(
                    "<COMPILED_METHOD_LOAD>:   name: compiledMethod, code: (0x[0-9a-f]{16})");
            Pattern unloadPattern = Pattern.compile(
                    "<COMPILED_METHOD_UNLOAD>:   name: compiledMethod, code: (0x[0-9a-f]{16})");

            String firstLoadAddress = null;
            String secondLoadAddress = null;

            String firstUnloadAddress = null;
            String secondUnloadAddress = null;

            String line;
            while ((line = reader.readLine()) != null) {
                // Check for load
                Matcher loadMatcher = loadPattern.matcher(line);
                if (loadMatcher.find()) {
                    // Verify the load events come before the unloads
                    Asserts.assertNull(firstUnloadAddress);
                    Asserts.assertNull(secondUnloadAddress);

                    String address = loadMatcher.group(1);

                    if (firstLoadAddress == null) {
                        System.out.println("Received first COMPILED_METHOD_LOAD event. Address: " + address);
                        firstLoadAddress = address;
                    } else if (secondLoadAddress == null) {
                        System.out.println("Received second COMPILED_METHOD_LOAD event. Address: " + address);
                        secondLoadAddress = address;
                    } else {
                        throw new RuntimeException("Received too many COMPILED_METHOD_LOAD events");
                    }
                }

                // Check for unload
                Matcher unloadMatcher = unloadPattern.matcher(line);
                if (unloadMatcher.find()) {
                    // Verify the unload events come after the loads
                    Asserts.assertNotNull(firstLoadAddress);
                    Asserts.assertNotNull(secondLoadAddress);

                    String address = unloadMatcher.group(1);

                    if (firstUnloadAddress == null) {
                        System.out.println("Received first COMPILED_METHOD_UNLOAD event. Address: " + address);
                        firstUnloadAddress = address;
                    } else if (secondUnloadAddress == null) {
                        System.out.println("Received second COMPILED_METHOD_UNLOAD event. Address: " + address);
                        secondUnloadAddress = address;

                        // We should have all events after receiving second unload
                        break;
                    }
                }
            }

            Asserts.assertNotNull(firstLoadAddress);
            Asserts.assertNotNull(secondLoadAddress);

            Asserts.assertNotNull(firstUnloadAddress);
            Asserts.assertNotNull(secondUnloadAddress);

            Asserts.assertEquals(firstLoadAddress, firstUnloadAddress);
            Asserts.assertEquals(secondLoadAddress, secondUnloadAddress);
        }

        process.destroy();
    }
}

class DoWork {

    protected static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();

    /** Load native library if required. */
    static {
        try {
            System.loadLibrary("NMethodRelocationTest");
        } catch (UnsatisfiedLinkError ule) {
            System.err.println("Could not load NMethodRelocationTest library");
            System.err.println("java.library.path: "
                + System.getProperty("java.library.path"));
            throw ule;
        }
    }

    public static void main(String argv[]) throws Exception {
        Executable method = DoWork.class.getDeclaredMethod("compiledMethod");
        WHITE_BOX.testSetDontInlineMethod(method, true);

        WHITE_BOX.enqueueMethodForCompilation(method, COMP_LEVEL_FULL_OPTIMIZATION);

        if (!WHITE_BOX.isMethodCompiled(method)) {
            throw new AssertionError("Method not compiled");
        }

        NMethod originalNMethod = NMethod.get(method, false);
        if (originalNMethod == null) {
            throw new AssertionError("Could not find original nmethod");
        }

        WHITE_BOX.relocateNMethodFromMethod(method, BlobType.MethodNonProfiled.id);

        NMethod relocatedNMethod = NMethod.get(method, false);
        if (relocatedNMethod == null) {
            throw new AssertionError("Could not find relocated nmethod");
        }

        if (originalNMethod.address == relocatedNMethod.address) {
            throw new AssertionError("Relocated nmethod same as original");
        }

        WHITE_BOX.deoptimizeAll();

        while (true) {
            WHITE_BOX.fullGC();
            System.out.flush();
        }
    }

    public static long compiledMethod() {
        return 0;
    }
}
