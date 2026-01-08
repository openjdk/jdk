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
 * @requires vm.jvmti &
 *           vm.gc != "Epsilon" &
 *           vm.flavor == "server" &
 *           !vm.emulatedClient &
 *           (vm.opt.TieredStopAtLevel == null | vm.opt.TieredStopAtLevel == 4)
 * @library /test/lib /test/hotspot/jtreg
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/native -agentlib:NMethodRelocationTest
 *                          --enable-native-access=ALL-UNNAMED
 *                          -Xbootclasspath/a:.
 *                          -Xbatch
 *                          -XX:+UnlockDiagnosticVMOptions
 *                          -XX:+WhiteBoxAPI
 *                          -XX:+SegmentedCodeCache
 *                          -XX:-TieredCompilation
 *                          -XX:+UnlockExperimentalVMOptions
 *                          -XX:+NMethodRelocation
 *                          NMethodRelocationTest
 */

import java.lang.reflect.Executable;

import jdk.test.lib.Asserts;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.whitebox.WhiteBox;
import jdk.test.whitebox.code.BlobType;
import jdk.test.whitebox.code.NMethod;

import static compiler.whitebox.CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION;

public class NMethodRelocationTest {

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

    protected static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();

    native static boolean shouldExit();

    public static void main(String[] argv) throws Exception {
        Executable method = NMethodRelocationTest.class.getDeclaredMethod("compiledMethod");
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

        while (!shouldExit()) {
            WHITE_BOX.fullGC();
        }
    }

    public static long compiledMethod() {
        return 0;
    }
}
