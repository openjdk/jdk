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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Map;

public class BuiltInProfileCheck {
    private static final int HEADER_TAG = ICC_Profile.icSigHead;
    private static final int INDEX = ICC_Profile.icHdrDeviceClass;
    private static final String EXCEPTION_MSG = "Built-in profile cannot be modified";
    /**
     * {@link #prepareTestProfile(String, boolean, int)}
     * stores the profile to test in testProfile.
     */
    private static ICC_Profile testProfile;

    private static final Map<Integer, String> colorSpace = Map.of(
            ColorSpace.CS_sRGB, "CS_sRGB",
            ColorSpace.CS_PYCC, "CS_PYCC",
            ColorSpace.CS_GRAY, "CS_GRAY",
            ColorSpace.CS_CIEXYZ, "CS_CIEXYZ",
            ColorSpace.CS_LINEAR_RGB, "CS_LINEAR_RGB"
    );

    public static void main(String[] args) throws Exception {
        System.out.println("CASE 1: Testing BuiltIn Profile");
        for (int cs : colorSpace.keySet()) {
            prepareTestProfile("Default", true, cs);
            testProfile(true, cs);
        }
        System.out.println("Passed\n");

        System.out.println("CASE 2: Testing Custom Profile");
        prepareTestProfile("Default", false, ColorSpace.CS_sRGB);
        testProfile(false, ColorSpace.CS_sRGB);
        System.out.println("Passed\n");

        System.out.println("CASE 3: Testing Built-In Profile"
                            + " Serialization & Deserialization");
        for (int cs : colorSpace.keySet()) {
            prepareTestProfile("Serialize", true, cs);
            testProfile(true, cs);
        }
        System.out.println("Passed\n");

        System.out.println("CASE 4: Testing Custom Profile"
                            + " Serialization & Deserialization");
        prepareTestProfile("Serialize", false, ColorSpace.CS_sRGB);
        testProfile(false, ColorSpace.CS_sRGB);
        System.out.println("Passed\n");

        System.out.println("CASE 5: Test reading Built-In profile from .icc file");
        prepareTestProfile("ReadFromFile", true, ColorSpace.CS_sRGB);
        testProfile(true, ColorSpace.CS_sRGB);
        System.out.println("Passed\n");

        System.out.println("CASE 6: Test reading Custom profile from .icc file");
        prepareTestProfile("ReadFromFile", false, ColorSpace.CS_sRGB);
        testProfile(false, ColorSpace.CS_sRGB);
        System.out.println("Passed\n");
    }

    private static void prepareTestProfile(String testCase,
                                           boolean isBuiltIn, int cs) {
        ICC_Profile builtInProfile = ICC_Profile.getInstance(cs);
        // if isBuiltIn=true use builtInProfile else create a copy
        testProfile = isBuiltIn
                      ? builtInProfile
                      : ICC_Profile.getInstance(builtInProfile.getData());

        switch (testCase) {
            case "Default" -> {
                // empty case block
                // no further processing of testProfile required for default case
            }
            case "Serialize" -> {
                try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                     ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                    oos.writeObject(testProfile);

                    byte[] array = baos.toByteArray();
                    try (ObjectInputStream ois =
                                 new ObjectInputStream(new ByteArrayInputStream(array))) {
                        testProfile = (ICC_Profile) ois.readObject();
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Test Failed ! Serial-Deserialization"
                                               + " case failed", e);
                }
            }
            case "ReadFromFile" -> {
                // .icc files serialized on older JDK version
                String filename = isBuiltIn ? "builtIn.icc" : "custom.icc";
                String testDir = System.getProperty("test.src")
                                 + System.getProperty("file.separator");
                filename = testDir + filename;

                try (FileInputStream fileIn = new FileInputStream(filename);
                     ObjectInputStream ois = new ObjectInputStream(fileIn)) {
                    testProfile = (ICC_Profile) ois.readObject();
                } catch (Exception e) {
                    throw new RuntimeException("Test Failed ! Unable to fetch"
                                               + " .icc files", e);
                }
            }
        }
    }

    private static void testProfile(boolean isBuiltIn, int cs) {
        byte[] headerData = testProfile.getData(HEADER_TAG);
        // Set profile class to valid icSigInputClass = 0x73636E72
        headerData[INDEX] = 0x73;
        headerData[INDEX + 1] = 0x63;
        headerData[INDEX + 2] = 0x6E;
        headerData[INDEX + 3] = 0x72;

        if (isBuiltIn) {
            System.out.println("Testing: " + colorSpace.get(cs));
            try {
                // Try updating a built-in profile, IAE is expected
                testProfile.setData(HEADER_TAG, headerData);
                throw new RuntimeException("Test Failed! IAE NOT thrown for profile "
                                           + colorSpace.get(cs));
            } catch (IllegalArgumentException iae) {
                if (!iae.getMessage().equals(EXCEPTION_MSG)) {
                    throw new RuntimeException("Test Failed! IAE with exception msg \""
                                               + EXCEPTION_MSG + "\" NOT thrown for profile "
                                               + colorSpace.get(cs));
                }
            }
        } else {
            // Modifying custom profile should NOT throw IAE
            testProfile.setData(HEADER_TAG, headerData);
        }
    }
}
