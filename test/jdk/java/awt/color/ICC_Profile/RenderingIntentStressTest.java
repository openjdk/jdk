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

import static java.awt.color.ICC_Profile.icAbsoluteColorimetric;
import static java.awt.color.ICC_Profile.icICCAbsoluteColorimetric;
import static java.awt.color.ICC_Profile.icMediaRelativeColorimetric;
import static java.awt.color.ICC_Profile.icPerceptual;
import static java.awt.color.ICC_Profile.icRelativeColorimetric;
import static java.awt.color.ICC_Profile.icSaturation;

/**
 * @test
 * @bug 8358057
 * @summary Stress test for ICC_Profile rendering intent parsing and validation
 */
public final class RenderingIntentStressTest {

    public static void main(String[] args) {
        ICC_Profile builtin = ICC_Profile.getInstance(ColorSpace.CS_sRGB);
        ICC_Profile profile = ICC_Profile.getInstance(builtin.getData());
        // some random combinations that should be ignored
        int[] upperBytes = {0x0000, 0xFFFF, 0xA5A5, 0x8000, 0x0001, 0x8080,
                            0x0101, 0xAA55, 0x550A, 0xFF00};
        for (int up : upperBytes) {
            for (int low = 0; low <= 0xFFFF; low++) {
                test(profile, up, low);
            }
        }
    }

    private static int getRenderingIntent(byte[] header) {
        // replicate the logic we have in jdk
        int index = ICC_Profile.icHdrRenderingIntent;
        return (header[index + 2] & 0xff) << 8 | header[index + 3] & 0xff;
    }

    private static void test(ICC_Profile profile, int up, int low) {
        byte[] header = profile.getData(ICC_Profile.icSigHead);
        // These bytes should be ignored
        header[ICC_Profile.icHdrRenderingIntent + 0] = (byte) (up >> 8 & 0xFF);
        header[ICC_Profile.icHdrRenderingIntent + 1] = (byte) (up & 0xFF);
        // This is the actual intent
        header[ICC_Profile.icHdrRenderingIntent + 2] = (byte) (low >> 8 & 0xFF);
        header[ICC_Profile.icHdrRenderingIntent + 3] = (byte) (low & 0xFF);

        boolean isValid = isValidIntent(low);
        try {
            profile.setData(ICC_Profile.icSigHead, header);
            if (!isValid) {
                throw new RuntimeException("IAE is expected");
            }
        } catch (IllegalArgumentException e) {
            if (isValid) {
                throw e;
            }
            return;
        }
        // verify that the intent is correctly stored in the profile by the CMM
        byte[] data = profile.getData(ICC_Profile.icSigHead);
        int actualIntent = getRenderingIntent(data);
        if (actualIntent != low) {
            System.out.println("Expected: " + low);
            System.out.println("Actual: " + actualIntent);
            throw new RuntimeException("Unexpected intent");
        }
    }

    private static boolean isValidIntent(int intent) {
        return intent == icPerceptual || intent == icRelativeColorimetric
                || intent == icMediaRelativeColorimetric
                || intent == icSaturation || intent == icAbsoluteColorimetric
                || intent == icICCAbsoluteColorimetric;
    }
}
