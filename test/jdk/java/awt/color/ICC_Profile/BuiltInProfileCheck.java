/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8346465
 * @summary Tests if setData() throws IAE for BuiltIn profiles
 */

import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;

public class BuiltInProfileCheck {
    private static final int HEADER_TAG = ICC_Profile.icSigHead;
    private static final int PROFILE_CLASS_START_INDEX =
                                       ICC_Profile.icHdrDeviceClass;
    private static final String EXCEPTION_MSG = "Built-in profile cannot be modified";

    public static void main(String[] args) {
        System.out.println("CASE 1: Testing BuiltIn Profile");
        updateProfile(true);
        System.out.println("Passed\n");

        System.out.println("CASE 2: Testing Custom Profile");
        updateProfile(false);
        System.out.println("Passed\n");
    }

    private static void updateProfile(boolean isBuiltIn) {
        ICC_Profile builtInProfile = ICC_Profile.getInstance(ColorSpace.CS_sRGB);
        // Create a copy of the built-in profile
        ICC_Profile customProfile = ICC_Profile.getInstance(builtInProfile.getData());

        ICC_Profile iccProfile = isBuiltIn ? builtInProfile : customProfile;

        byte[] headerData = iccProfile.getData(HEADER_TAG);
        int index = PROFILE_CLASS_START_INDEX;
        // Set profile class to valid icSigInputClass = 0x73636E72
        headerData[index] = 0x73;
        headerData[index + 1] = 0x63;
        headerData[index + 2] = 0x6E;
        headerData[index + 3] = 0x72;

        if (isBuiltIn) {
            try {
                // Try updating a built-in profile, IAE is expected
                iccProfile.setData(HEADER_TAG, headerData);
                throw new RuntimeException("Test Failed! IAE NOT thrown.");
            } catch (IllegalArgumentException iae) {
                if (!iae.getMessage().equals(EXCEPTION_MSG)) {
                    throw new RuntimeException("Test Failed! IAE with exception msg \""
                                               + EXCEPTION_MSG + "\" NOT thrown.");
                }
            }
        } else {
            // Modifying custom profile should NOT throw IAE
            iccProfile.setData(HEADER_TAG, headerData);
        }
    }
}
