/*
 * Copyright (c) 2026, Microsoft Corporation. All rights reserved.
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
 * @summary Check C1 and C2 PAC instructions and execution
 * @requires (os.arch == "aarch64") & (os.family == "linux" | os.family == "windows")
 * @requires vm.compiler1.enabled & vm.compiler2.enabled
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -Xbatch -XX:UseBranchProtection=pac-ret
 *                   runtime.aarch64.TestReturnAddressProtection
 */

package runtime.aarch64;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import jdk.test.lib.Asserts;
import jdk.test.lib.Platform;
import jdk.test.whitebox.WhiteBox;
import jdk.test.whitebox.code.NMethod;
import jtreg.SkippedException;

public class TestReturnAddressProtection {
    private static volatile int sink;
    private static final WhiteBox WB = WhiteBox.getWhiteBox();

    private static void test(int value) {
        sink = value;
    }

    private static void checkInstructions(byte[] code) {
        boolean foundSign = false;
        boolean foundAuth = false;

        // AArch64 HINT #imm7 is encoded as 0xd503_201f | (imm7 << 5).
        // PACIAZ and AUTIAZ use HINT #24 and #28 respectively, whereas PACIBZ
        // and AUTIBZ use HINT #26 and #30 respectively.
        int sign = 0xd503_201f | ((Platform.isWindows() ? 26 : 24) << 5);
        int auth = 0xd503_201f | ((Platform.isWindows() ? 30 : 28) << 5);

        ByteBuffer instructions = ByteBuffer.wrap(code).order(ByteOrder.LITTLE_ENDIAN);
        for (int idx = 0; idx < code.length / Integer.BYTES; idx += 1) {
            int instruction = instructions.getInt(idx * Integer.BYTES);
            foundSign |= instruction == sign;
            foundAuth |= instruction == auth;

            if (foundSign && foundAuth) {
                return;
            }
        }

        throw new AssertionError("Missing signing/authentication instructions");
    }

    private static void checkCompilationLevel(Method testMethod, int level) {
        System.out.println("Checking PAC at compilation level " + level);
        Asserts.assertTrue(WB.enqueueMethodForCompilation(testMethod, level));

        NMethod code = NMethod.get(testMethod, false);
        Asserts.assertNotNull(code, "Missing compiled test method");
        Asserts.assertEQ(code.comp_level, level, "Wrong compilation level");

        // Check whether the generated code includes PAC and AUT instructions.
        checkInstructions(code.insts);

        // Make sure that the signing and authentication steps don't fail.
        test(42);

        // Clear this compilation before checking the next compilation level.
        WB.deoptimizeMethod(testMethod);
    }

    public static void main(String[] args) throws Exception {
        if (!WB.isReturnAddressProtectionEnabled()) {
            throw new SkippedException("Return-address protection is disabled");
        }

        Class this_class = TestReturnAddressProtection.class;
        Method testMethod = this_class.getDeclaredMethod("test", int.class);
        WB.testSetDontInlineMethod(testMethod, true);

        checkCompilationLevel(testMethod, 1);
        checkCompilationLevel(testMethod, 4);
    }
}
