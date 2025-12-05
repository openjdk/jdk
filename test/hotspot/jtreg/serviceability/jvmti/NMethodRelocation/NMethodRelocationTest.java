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
 * This test verifies that COMPILED_METHOD_LOAD and COMPILED_METHOD_UNLOAD
 * events are correctly sent for relocated nmethods. In some cases, these
 * events may not be received if the VM exits. For this reason, the test will
 * issue a warning but still pass when the events are not observed.
 * However, the test will fail if the method fails to compile or if the
 * nmethod fails to relocate.

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

import static compiler.whitebox.CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION;

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

        OutputAnalyzer oa = new OutputAnalyzer(pb.start());
        String output = oa.getOutput();
        if (oa.getExitValue() != 0) {
            System.err.println(oa.getOutput());
            throw new RuntimeException("Non-zero exit code returned from the test");
        }
        Asserts.assertTrue(oa.getExitValue() == 0);

        Pattern pattern = Pattern.compile("<COMPILED_METHOD_LOAD>:   name: compiledMethod, code: (0x[0-9a-f]{16})");
        Matcher matcher = pattern.matcher(output);

        if (!matcher.find()) {
            System.err.println(oa.getOutput());
            System.err.println("WARNING: Unable to find first COMPILED_METHOD_LOAD event");
            return;
        }
        String fromAddr = matcher.group(1);

        if (!matcher.find()) {
            System.err.println(oa.getOutput());
            System.err.println("WARNING: Unable to find second COMPILED_METHOD_LOAD event");
            return;
        }
        String toAddr = matcher.group(1);

        // Confirm the nmethod was actually relocated
        Asserts.assertTrue(fromAddr != toAddr);

        // Check for UNLOAD events but only warn if missing
        if (!output.contains("<COMPILED_METHOD_UNLOAD>:   name: compiledMethod, code: " + fromAddr)) {
            System.err.println(oa.getOutput());
            System.err.println("WARNING: COMPILED_METHOD_UNLOAD event not found for address: " + fromAddr);
        }

        if (!output.contains("<COMPILED_METHOD_UNLOAD>:   name: compiledMethod, code: " + toAddr)) {
            System.err.println(oa.getOutput());
            System.err.println("WARNING: COMPILED_METHOD_UNLOAD event not found for address: " + toAddr);
        }
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

    /**
     * Returns value of VM option.
     *
     * @param name option's name
     * @return value of option or {@code null}, if option doesn't exist
     * @throws NullPointerException if name is null
     */
    protected static String getVMOption(String name) {
        Objects.requireNonNull(name);
        return Objects.toString(WHITE_BOX.getVMFlag(name), null);
    }

    /**
     * Returns value of VM option or default value.
     *
     * @param name         option's name
     * @param defaultValue default value
     * @return value of option or {@code defaultValue}, if option doesn't exist
     * @throws NullPointerException if name is null
     * @see #getVMOption(String)
     */
    protected static String getVMOption(String name, String defaultValue) {
        String result = getVMOption(name);
        return result == null ? defaultValue : result;
    }

    public static void main(String argv[]) throws Exception {
        run();
    }

    public static void run() throws Exception {
        Executable method = DoWork.class.getDeclaredMethod("compiledMethod");
        WHITE_BOX.testSetDontInlineMethod(method, true);

        WHITE_BOX.enqueueMethodForCompilation(method, COMP_LEVEL_FULL_OPTIMIZATION);

        if(!WHITE_BOX.isMethodCompiled(method)) {
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

        WHITE_BOX.fullGC();
        WHITE_BOX.fullGC();

        System.out.flush();
    }

    public static long compiledMethod() {
        return 0;
    }
}
