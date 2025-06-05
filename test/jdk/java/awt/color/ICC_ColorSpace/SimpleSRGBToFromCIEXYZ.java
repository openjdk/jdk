/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Arrays;

/**
 * @test
 * @bug 4760025
 * @summary Verifies sRGB conversions to and from CIE XYZ
 */
public final class SimpleSRGBToFromCIEXYZ {

    public static void main(String[] args) {
        ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_sRGB);
        for (float g : new float[]{1.0f, 0.8f, 0.6f}) {
            float[] rgb = {0, g, 0};
            float[] xyz = cs.toCIEXYZ(rgb);
            float[] inv = cs.fromCIEXYZ(xyz);

            if (inv[0] != 0 || Math.abs(inv[1] - g) > 0.0001f || inv[2] != 0) {
                System.err.println("Expected color:\t" + Arrays.toString(rgb));
                System.err.println("XYZ color:\t\t" + Arrays.toString(xyz));
                System.err.println("Actual color:\t" + Arrays.toString(inv));
                throw new Error("Wrong color");
            }
        }
    }
}
