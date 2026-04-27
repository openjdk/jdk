/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static java.awt.color.ColorSpace.TYPE_3CLR;
import static java.awt.color.ColorSpace.TYPE_GRAY;
import static java.awt.color.ColorSpace.TYPE_RGB;
import static java.awt.color.ColorSpace.TYPE_XYZ;
import static java.awt.color.ICC_Profile.CLASS_ABSTRACT;
import static java.awt.color.ICC_Profile.CLASS_COLORSPACECONVERSION;
import static java.awt.color.ICC_Profile.CLASS_DISPLAY;

/**
 * @test
 * @bug 8256321 8359380
 * @summary Verifies built-in profile properties are the same before and after
 *          activation and in copies of built-in profiles
 */
public final class CheckDefaultProperties {

    public static void main(String[] args) {
        ICC_Profile srgb = ICC_Profile.getInstance(ColorSpace.CS_sRGB);
        ICC_Profile gray = ICC_Profile.getInstance(ColorSpace.CS_GRAY);
        ICC_Profile xyz = ICC_Profile.getInstance(ColorSpace.CS_CIEXYZ);
        ICC_Profile lrgb = ICC_Profile.getInstance(ColorSpace.CS_LINEAR_RGB);
        ICC_Profile pycc = ICC_Profile.getInstance(ColorSpace.CS_PYCC);

        // checks default values before built-in profiles are activated
        test(srgb, gray, xyz, lrgb, pycc);

        // activates built-in profiles and creates copies
        ICC_Profile srgbCopy = ICC_Profile.getInstance(srgb.getData());
        ICC_Profile grayCopy = ICC_Profile.getInstance(gray.getData());
        ICC_Profile xyzCopy = ICC_Profile.getInstance(xyz.getData());
        ICC_Profile lrgbCopy = ICC_Profile.getInstance(lrgb.getData());
        ICC_Profile pyccCopy = ICC_Profile.getInstance(pycc.getData());

        // checks default values after profile activation
        test(srgb, gray, xyz, lrgb, pycc);

        // checks default values in copies of the built-in profiles
        test(srgbCopy, grayCopy, xyzCopy, lrgbCopy, pyccCopy);
    }

    private static void test(ICC_Profile srgb, ICC_Profile gray,
                             ICC_Profile xyz, ICC_Profile lrgb,
                             ICC_Profile pycc) {
        test(srgb, TYPE_RGB, 3, CLASS_DISPLAY);
        test(gray, TYPE_GRAY, 1, CLASS_DISPLAY);
        test(xyz, TYPE_XYZ, 3, CLASS_ABSTRACT);
        test(lrgb, TYPE_RGB, 3, CLASS_DISPLAY);
        test(pycc, TYPE_3CLR, 3, CLASS_COLORSPACECONVERSION);
    }

    private static void test(ICC_Profile profile, int type, int num, int pcls) {
        int profileClass = profile.getProfileClass();
        int colorSpaceType = profile.getColorSpaceType();
        int numComponents = profile.getNumComponents();
        if (profileClass != pcls) {
            throw new RuntimeException("Wrong profile class: " + profileClass);
        }
        if (colorSpaceType != type) {
            throw new RuntimeException("Wrong profile type: " + colorSpaceType);
        }
        if (numComponents != num) {
            throw new RuntimeException("Wrong profile comps: " + numComponents);
        }
    }
}
