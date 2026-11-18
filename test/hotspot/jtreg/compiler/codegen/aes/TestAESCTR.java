/*
 * Copyright (c) 2026, Intel Corporation. All rights reserved.
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
 * @bug 8391494
 * @key stress randomness
 * @summary test AES/CTR intrinsics
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=600 -Xbatch -DcheckOutput=true -Dmode=CTR
 *      -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *      compiler.codegen.aes.TestAESCTR
 */
/**
 * @test
 * @bug 8391494
 * @key stress randomness
 * @requires os.simpleArch == "x64"
 * @summary test AES/CTR intrinsics
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=600 -Xbatch -DcheckOutput=true -Dmode=CTR
 *      -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *      -XX:UseAVX=2 -XX:+EnableX86ECoreOpts compiler.codegen.aes.TestAESCTR
 * @run main/othervm/timeout=600 -Xbatch -DcheckOutput=true -Dmode=CTR
 *      -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *      -XX:UseAVX=1 compiler.codegen.aes.TestAESCTR
 */

package compiler.codegen.aes;

import jdk.test.whitebox.WhiteBox;

public class TestAESCTR {
    public static void main(String[] args) {
        String[] keys = {"128", "192", "256"};
        int[] msgSizes = {
            1, 15, 16, 17, 63, 64, 65, 128, 129, 256, 639, 768, 801, 512*200+1
        };
        for (int msgSize:msgSizes) {
            for (String key:keys) {
                System.setProperty("keySize", key);
                System.setProperty("msgSize", ""+msgSize);

                // deopt so reference output is from Java AES implementation
                WhiteBox.getWhiteBox().deoptimizeAll();
                TestAESMain.main(args);

                if (msgSize == 1) {
                    continue;
                }

                // test the usedCtr path, force update() + doFinal()
                System.setProperty("lastChunkSize", "7");
                System.setProperty("encInputOffset", "1");
                WhiteBox.getWhiteBox().deoptimizeAll();
                TestAESMain.main(args);
                System.clearProperty("lastChunkSize");
                System.clearProperty("encInputOffset");
            }
        }
    }
}
