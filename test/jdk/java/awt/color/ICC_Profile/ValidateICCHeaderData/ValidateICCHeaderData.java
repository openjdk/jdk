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
 * @bug 8347377 8358057
 * @summary To verify if ICC_Profile's setData() and getInstance() methods
 *          validate header data and throw IAE for invalid values.
 * @run main ValidateICCHeaderData
 */

import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;

public class ValidateICCHeaderData {
    private static ICC_Profile profile;

    private static final boolean DEBUG = false;
    private static final int VALID_HEADER_SIZE = 128;
    private static final int HEADER_TAG = ICC_Profile.icSigHead;
    private static final int PROFILE_CLASS_START_INDEX = ICC_Profile.icHdrDeviceClass;
    private static final int COLOR_SPACE_START_INDEX = ICC_Profile.icHdrColorSpace;
    private static final int RENDER_INTENT_START_INDEX = ICC_Profile.icHdrRenderingIntent;
    private static final int PCS_START_INDEX = ICC_Profile.icHdrPcs;

    private static final int[] VALID_PROFILE_CLASS = new int[] {
            ICC_Profile.icSigInputClass, ICC_Profile.icSigDisplayClass,
            ICC_Profile.icSigOutputClass, ICC_Profile.icSigLinkClass,
            ICC_Profile.icSigAbstractClass, ICC_Profile.icSigColorSpaceClass,
            ICC_Profile.icSigNamedColorClass
    };

    private static final int[] VALID_COLOR_SPACE = new int[] {
            ICC_Profile.icSigXYZData, ICC_Profile.icSigLabData,
            ICC_Profile.icSigLuvData, ICC_Profile.icSigYCbCrData,
            ICC_Profile.icSigYxyData, ICC_Profile.icSigRgbData,
            ICC_Profile.icSigGrayData, ICC_Profile.icSigHsvData,
            ICC_Profile.icSigHlsData, ICC_Profile.icSigCmykData,
            ICC_Profile.icSigSpace2CLR, ICC_Profile.icSigSpace3CLR,
            ICC_Profile.icSigSpace4CLR, ICC_Profile.icSigSpace5CLR,
            ICC_Profile.icSigSpace6CLR, ICC_Profile.icSigSpace7CLR,
            ICC_Profile.icSigSpace8CLR, ICC_Profile.icSigSpace9CLR,
            ICC_Profile.icSigSpaceACLR, ICC_Profile.icSigSpaceBCLR,
            ICC_Profile.icSigSpaceCCLR, ICC_Profile.icSigSpaceDCLR,
            ICC_Profile.icSigSpaceECLR, ICC_Profile.icSigSpaceFCLR,
            ICC_Profile.icSigCmyData
    };

    private static final int[] VALID_RENDER_INTENT = new int[] {
            ICC_Profile.icPerceptual, ICC_Profile.icMediaRelativeColorimetric,
            ICC_Profile.icSaturation, ICC_Profile.icAbsoluteColorimetric
    };

    private static void createCopyOfBuiltInProfile() {
        ICC_Profile builtInProfile = ICC_Profile.getInstance(ColorSpace.CS_sRGB);
        //copy of SRGB BuiltIn Profile that can be modified
        //using ICC_Profile.setData()
        profile = ICC_Profile.getInstance(builtInProfile.getData());
    }

    public static void main(String[] args) throws Exception {
        createCopyOfBuiltInProfile();

        System.out.println("CASE 1: Testing VALID Profile Classes ...");
        testValidHeaderData(VALID_PROFILE_CLASS, PROFILE_CLASS_START_INDEX, 4);
        System.out.println("CASE 1: Passed \n");

        // PCS field validation for Profile class != DEVICE_LINK
        System.out.println("CASE 2: Testing VALID PCS Type"
                           + " for Profile class != DEVICE_LINK ...");
        testValidHeaderData(new int[] {ICC_Profile.icSigXYZData, ICC_Profile.icSigLabData},
                PCS_START_INDEX, 4);
        System.out.println("CASE 2: Passed \n");

        System.out.println("CASE 3: Testing INVALID PCS Type"
                           + " for Profile class != DEVICE_LINK ...");
        testInvalidHeaderData(ICC_Profile.icSigCmykData, PCS_START_INDEX, 4);
        System.out.println("CASE 3: Passed \n");

        System.out.println("CASE 4: Testing DEVICE LINK PROFILE CLASS ...");
        testValidHeaderData(new int[] {ICC_Profile.icSigLinkClass},
                PROFILE_CLASS_START_INDEX, 4);
        //to check if instantiating BufferedImage with
        //ICC_Profile device class = CLASS_DEVICELINK does not throw IAE.
        BufferedImage img = new BufferedImage(100, 100,
                                              BufferedImage.TYPE_3BYTE_BGR);
        System.out.println("CASE 4: Passed \n");

        // PCS field validation for Profile class == DEVICE_LINK
        System.out.println("CASE 5: Testing VALID PCS Type"
                           + " for Profile class == DEVICE_LINK ...");
        testValidHeaderData(VALID_COLOR_SPACE, PCS_START_INDEX, 4);
        System.out.println("CASE 5: Passed \n");

        System.out.println("CASE 6: Testing INVALID PCS Type"
                           + " for Profile class == DEVICE_LINK ...");
        //original icSigLabData = 0x4C616220
        int invalidSigLabData = 0x4C616221;
        testInvalidHeaderData(invalidSigLabData, PCS_START_INDEX, 4);
        System.out.println("CASE 6: Passed \n");

        System.out.println("CASE 7: Testing VALID Color Spaces ...");
        testValidHeaderData(VALID_COLOR_SPACE, COLOR_SPACE_START_INDEX, 4);
        System.out.println("CASE 7: Passed \n");

        System.out.println("CASE 8: Testing VALID Rendering Intent ...");
        testValidHeaderData(VALID_RENDER_INTENT, RENDER_INTENT_START_INDEX, 4);
        System.out.println("CASE 8: Passed \n");

        System.out.println("CASE 9: Testing INVALID Profile Class ...");
        //original icSigInputClass = 0x73636E72
        int invalidSigInputClass = 0x73636E70;
        testInvalidHeaderData(invalidSigInputClass, PROFILE_CLASS_START_INDEX, 4);
        System.out.println("CASE 9: Passed \n");

        System.out.println("CASE 10: Testing INVALID Color Space ...");
        //original icSigXYZData = 0x58595A20
        int invalidSigXYZData = 0x58595A21;
        testInvalidHeaderData(invalidSigXYZData, COLOR_SPACE_START_INDEX, 4);
        System.out.println("CASE 10: Passed \n");

        System.out.println("CASE 11: Testing INVALID Rendering Intent ...");
        testInvalidIntent();
        System.out.println("CASE 11: Passed \n");

        System.out.println("CASE 12: Testing INVALID Header Size ...");
        testInvalidHeaderSize();
        System.out.println("CASE 12: Passed \n");

        System.out.println("CASE 13: Testing ICC_Profile.getInstance(..)"
                           + " with VALID profile data ...");
        testProfileCreation(true);
        System.out.println("CASE 13: Passed \n");

        System.out.println("CASE 14: Testing ICC_Profile.getInstance(..)"
                           + " with INVALID profile data ...");
        testProfileCreation(false);
        System.out.println("CASE 14: Passed \n");

        System.out.println("CASE 15: Testing Deserialization of ICC_Profile ...");
        testDeserialization();
        System.out.println("CASE 15: Passed \n");

        System.out.println("Successfully completed testing all 15 cases. Test Passed !!");
    }

    private static void testValidHeaderData(int[] validData, int startIndex,
                                            int fieldLength) {
        for (int value : validData) {
            setTag(value, startIndex, fieldLength);
        }
    }

    private static void testInvalidHeaderData(int invalidData, int startIndex,
                                              int fieldLength) {
        try {
            setTag(invalidData, startIndex, fieldLength);
            throw new RuntimeException("Test Failed ! Expected IAE NOT thrown");
        } catch (IllegalArgumentException iae) {
            System.out.println("Expected IAE thrown: " + iae.getMessage());
        }
    }

    private static void testInvalidIntent() {
        //valid rendering intent values are 0-3
        int invalidRenderIntent = 5;
        try {
            setTag(invalidRenderIntent, RENDER_INTENT_START_INDEX, 4);
            throw new RuntimeException("Test Failed ! Expected IAE NOT thrown");
        } catch (IllegalArgumentException iae) {
            String message = iae.getMessage();
            System.out.println("Expected IAE thrown: " + message);
            if (!message.contains(": " + invalidRenderIntent)) {
                throw new RuntimeException("Test Failed ! Unexpected text");
            }
        }
    }

    private static void setTag(int value, int startIndex, int fieldLength) {
        byte[] byteArray;
        if (startIndex == RENDER_INTENT_START_INDEX) {
            byteArray = ByteBuffer.allocate(4).putInt(value).array();
        } else {
            BigInteger big = BigInteger.valueOf(value);
            byteArray = (big.toByteArray());
        }

        if (DEBUG) {
            System.out.print("Byte Array : ");
            for (int i = 0; i < byteArray.length; i++) {
                System.out.print(byteArray[i] + " ");
            }
            System.out.println("\n");
        }

        byte[] iccProfileHeaderData = profile.getData(HEADER_TAG);
        System.arraycopy(byteArray, 0, iccProfileHeaderData, startIndex, fieldLength);
        profile.setData(HEADER_TAG, iccProfileHeaderData);
    }

    private static void testProfileCreation(boolean validCase) {
        ICC_Profile builtInProfile = ICC_Profile.getInstance(ColorSpace.CS_GRAY);
        byte[] profileData = builtInProfile.getData();

        int validDeviceClass = ICC_Profile.icSigInputClass;
        BigInteger big = BigInteger.valueOf(validDeviceClass);
        //valid case set device class to 0x73636E72 (icSigInputClass)
        //invalid case set device class to 0x00000000
        byte[] field = validCase ? big.toByteArray()
                                 : ByteBuffer.allocate(4).putInt(0).array();
        System.arraycopy(field, 0, profileData, PROFILE_CLASS_START_INDEX, 4);

        try {
            ICC_Profile.getInstance(profileData);
            if (!validCase) {
                throw new RuntimeException("Test Failed ! Expected IAE NOT thrown");
            }
        } catch (IllegalArgumentException iae) {
            if (!validCase) {
                System.out.println("Expected IAE thrown: " + iae.getMessage());
            } else {
                throw new RuntimeException("Unexpected IAE thrown");
            }
        }
    }

    private static void testInvalidHeaderSize() {
        byte[] iccProfileHeaderData = profile.getData(HEADER_TAG);
        byte[] invalidHeaderSize = new byte[VALID_HEADER_SIZE - 1];
        System.arraycopy(iccProfileHeaderData, 0,
                invalidHeaderSize, 0, invalidHeaderSize.length);
        try {
            profile.setData(HEADER_TAG, invalidHeaderSize);
            throw new RuntimeException("Test Failed ! Expected IAE NOT thrown");
        } catch (IllegalArgumentException iae) {
            System.out.println("Expected IAE thrown: " + iae.getMessage());
        }
    }

    private static void testDeserialization() throws IOException {
        //invalidSRGB.icc is serialized on older version of JDK
        //Upon deserialization, the invalid profile is expected to throw IAE
        try {
            ICC_Profile.getInstance("./invalidSRGB.icc");
            throw new RuntimeException("Test Failed ! Expected IAE NOT thrown");
        } catch (IllegalArgumentException iae) {
            System.out.println("Expected IAE thrown: " + iae.getMessage());
        }
    }
}
