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

import java.awt.image.ColorModel;

/**
 * @test
 * @bug 4677581
 * @summary checks when the ColorModel#getComponentSize() throws AIOBE
 */
public final class GetComponentSizeAIOBE {

    public static void main(String[] args) {
        ColorModel cm = ColorModel.getRGBdefault();
        for (int i = 0; i < cm.getNumComponents(); ++i) {
            cm.getComponentSize(i);
        }

        testAIOBE(cm, Integer.MIN_VALUE);
        testAIOBE(cm, -1);
        testAIOBE(cm, cm.getNumComponents());
        testAIOBE(cm, cm.getNumComponents() + 1);
        testAIOBE(cm, Integer.MAX_VALUE);
    }

    private static void testAIOBE(ColorModel cm, int componentIdx) {
        try {
            cm.getComponentSize(componentIdx);
            throw new RuntimeException("AIOBE is not thrown");
        } catch (ArrayIndexOutOfBoundsException ignore) {
            // expected
        }
    }
}
