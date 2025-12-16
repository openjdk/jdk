/*
 * Copyright (c) 2026, NVIDIA CORPORATION & AFFILIATES. All rights reserved.
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
 * @bug 8374349
 * @summary Test PreferSVEMergingModeCPY behavior with different flag settings
 *
 * @requires os.arch == "aarch64"
 * @library /test/lib /
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller
 *             jdk.test.whitebox.WhiteBox
 *
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -XX:UseSVE=0
 *                   compiler.arguments.TestPreferSVEMergingModeCPY
 *
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -XX:UseSVE=1
 *                   compiler.arguments.TestPreferSVEMergingModeCPY
 *
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -XX:UseSVE=0 -XX:+PreferSVEMergingModeCPY
 *                   compiler.arguments.TestPreferSVEMergingModeCPY optionSpecified
 *
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -XX:UseSVE=0 -XX:-PreferSVEMergingModeCPY
 *                   compiler.arguments.TestPreferSVEMergingModeCPY optionSpecified
 *
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -XX:UseSVE=1 -XX:+PreferSVEMergingModeCPY
 *                   compiler.arguments.TestPreferSVEMergingModeCPY optionSpecified
 *
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -XX:UseSVE=1 -XX:-PreferSVEMergingModeCPY
 *                   compiler.arguments.TestPreferSVEMergingModeCPY optionSpecified
 */

package compiler.arguments;

import java.util.Arrays;
import java.util.List;

import jdk.test.lib.Asserts;
import jdk.test.whitebox.WhiteBox;
import jdk.test.whitebox.cpuinfo.CPUInfo;

public class TestPreferSVEMergingModeCPY {
    private static final WhiteBox WB = WhiteBox.getWhiteBox();

    public static void main(String[] args) {
        boolean optionSpecified = Arrays.asList(args).contains("optionSpecified");

        int sveLevel = WB.getUintVMFlag("UseSVE").intValue();
        Boolean optionValue = WB.getBooleanVMFlag("PreferSVEMergingModeCPY");
        Asserts.assertNotNull(optionValue);
        boolean isDefault = WB.isDefaultVMFlag("PreferSVEMergingModeCPY");

        List<String> cpuFeatures = CPUInfo.getFeatures();
        boolean isNeoverseV1orV2 = !cpuFeatures.isEmpty() && isNeoverseV1orV2(cpuFeatures.get(0));

        if (sveLevel == 0) {
            // UseSVE == 0
            if (!optionSpecified) {
                // Case 1: option not specified
                Asserts.assertFalse(optionValue.booleanValue(),
                        "PreferSVEMergingModeCPY should be false when UseSVE=0 (default)");
                Asserts.assertTrue(isDefault,
                        "PreferSVEMergingModeCPY should remain default when not specified");
            } else {
                // Case 3 & 4: option specified
                // Regardless of what user specified, VM forces it to false
                Asserts.assertFalse(optionValue.booleanValue(),
                        "PreferSVEMergingModeCPY must be disabled when UseSVE=0");
                Asserts.assertFalse(isDefault,
                        "PreferSVEMergingModeCPY should not be default when explicitly set by user");
            }
        } else {
            // UseSVE > 0
            if (!optionSpecified) {
                // Case 2: option not specified
                if (isNeoverseV1orV2) {
                    Asserts.assertTrue(optionValue.booleanValue(),
                            "PreferSVEMergingModeCPY should be true on Neoverse V1/V2 when UseSVE>0 (default)");
                } else {
                    Asserts.assertFalse(optionValue.booleanValue(),
                            "PreferSVEMergingModeCPY should be false on non-V1/V2 CPUs when UseSVE>0 (default)");
                }
                Asserts.assertTrue(isDefault,
                        "PreferSVEMergingModeCPY should remain default when not specified");
            } else {
                // Case 5 & 6: option specified
                // Option value should match what user specified
                Asserts.assertFalse(isDefault,
                        "PreferSVEMergingModeCPY should not be default when explicitly set by user");
            }
        }
    }

    private static boolean isNeoverseV1orV2(String cpuModel) {
        // Neoverse V1: CPU implementer 0x41 (ARM), variant 0x0, part 0xd40
        // Neoverse V2: CPU implementer 0x41 (ARM), variant 0x0, part 0xd4f
        return cpuModel.contains("0xd40") || cpuModel.contains("0xd4f");
    }
}
