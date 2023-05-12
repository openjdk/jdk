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

import java.awt.Color;
import java.awt.color.ColorSpace;

/**
 * @test
 * @bug 6528710
 * @summary Verifies sRGB-ColorSpace to sRGB-ColorSpace conversion quality
 */
public final class SimpleSRGBConversionQualityTest {

    public static void main(String[] args) {
        ColorSpace cspace = ColorSpace.getInstance(ColorSpace.CS_sRGB);
        float fvalue[] = {1.0f, 1.0f, 1.0f};

        Color c = new Color(cspace, fvalue, 1.0f);
        if (c.getRed() != 255 || c.getGreen() != 255 || c.getBlue() != 255) {
            throw new RuntimeException("Wrong color: " + c);
        }

        float frgbvalue[] = cspace.toRGB(fvalue);
        for (int i = 0; i < 3; ++i) {
            if (frgbvalue[i] != 1.0f) {
                System.err.println(fvalue[i] + " -> " + frgbvalue[i]);
                throw new RuntimeException("Wrong value");
            }
        }
    }
}
