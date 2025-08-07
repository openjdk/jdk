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

import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;

/**
 * @test
 * @bug 8358623
 * @summary Verifies ICC profile version of built-in color spaces
 */
public final class CheckVersions {

    public static void main(String[] args) {
        test(ColorSpace.CS_CIEXYZ, 2, 3, 0);
        test(ColorSpace.CS_GRAY, 2, 3, 0);
        test(ColorSpace.CS_LINEAR_RGB, 2, 3, 0);
        test(ColorSpace.CS_PYCC, 4, 0, 0);
        test(ColorSpace.CS_sRGB, 2, 3, 0);
    }

    private static void test(int cs, int expMajor, int expMinor, int expPatch) {
        ICC_Profile profile = ICC_Profile.getInstance(cs);

        int major = profile.getMajorVersion();
        int minorRaw = profile.getMinorVersion();
        int minor = (minorRaw >> 4) & 0x0F;
        int patch = minorRaw & 0x0F;

        if (major != expMajor || minor != expMinor || patch != expPatch) {
            System.err.println("Expected major: " + expMajor);
            System.err.println("Expected minor: " + expMinor);
            System.err.println("Expected patch: " + expPatch);

            System.err.println("Actual major: " + major);
            System.err.println("Actual minor: " + minor);
            System.err.println("Actual patch: " + patch);
            throw new RuntimeException("Test failed for ColorSpace: " + cs);
        }
    }
}
