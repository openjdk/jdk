/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7124282
 * @key headful
 * @requires (os.family == "mac")
 * @summary Checks whether Table & List's focus ring color is more prominent
 * in comparison to original focus ring (represented by 'CellFocus.color'
 * property in Aqua LAF UIDefaults) for MacOS accent colors.
 * @run main CellFocusRingTest
 */

import java.awt.Color;
import javax.swing.plaf.BorderUIResource.LineBorderUIResource;
import javax.swing.UIManager;

public class CellFocusRingTest {

    private static StringBuffer errorLog = new StringBuffer();

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel("com.apple.laf.AquaLookAndFeel");
        } catch (Exception e) {
            throw new RuntimeException("Unsupported Look&Feel Class");
        }

        System.out.println("Test for Table");
        testFocusRingColor("Table");
        System.out.println("--------------------------------");

        System.out.println("Test for List");
        testFocusRingColor("List");
        System.out.println("--------------------------------");

        if (errorLog.isEmpty()) {
            System.out.println("Test passed !!");
        }
        else {
            throw new RuntimeException("Following cases failed.\n"+ errorLog);
        }
    }

    private static void testFocusRingColor(String prefix) {
        float[] bckRGB = new float[3];
        float[] oldCellRingRGB = new float[3];
        float[] newCellRingRGB = new float[3];

        Color selectionBck = null;
        Color originalRingColor = null;
        Color newRingColor = null;

        // saturation threshold for grayish colors
        float satGrayScale = 0.10f;

        if (UIManager.getDefaults().get(prefix + ".selectionBackground") != null
                && UIManager.getDefaults().get(prefix + ".selectionBackground")
                instanceof Color) {
            selectionBck = (Color) UIManager.getDefaults()
                    .get(prefix + ".selectionBackground");
        }

        if (UIManager.getDefaults().get("CellFocus.color") != null
                && UIManager.getDefaults().get("CellFocus.color")
                instanceof Color) {
            originalRingColor = (Color) UIManager.getDefaults()
                    .get("CellFocus.color");
        }

        if (UIManager.getDefaults()
                .get(prefix + ".focusCellHighlightBorder") != null &&
                UIManager.getDefaults().get(prefix + ".focusCellHighlightBorder")
                        instanceof LineBorderUIResource cellFocusBorderObj) {
            newRingColor = cellFocusBorderObj.getLineColor();
        }

        if (selectionBck == null || originalRingColor == null ||
                newRingColor == null) {
            errorLog.append(prefix + ": One or more color values are null.\n");
        }
        System.out.println(UIManager.getLookAndFeel().toString());
        System.out.println("Selection Background Color: "
                + selectionBck.toString());

        System.out.println("Original FocusRing Color: "
                + originalRingColor.toString());

        System.out.println("Brighter FocusRing Color: "
                + newRingColor.toString());

        int redValue = originalRingColor.getRed();
        int greenValue = originalRingColor.getGreen();
        int blueValue = originalRingColor.getBlue();

        float[] hsbValues = new float[3];
        Color.RGBtoHSB(redValue, greenValue, blueValue, hsbValues);

        // Edge case - Original Focus ring color: WHITE/BLACK/GRAY
        if (((hsbValues[0] == 0 && hsbValues[1] == 0)
                || hsbValues[1] <= satGrayScale) &&
                newRingColor.equals(Color.LIGHT_GRAY)) {
            System.out.println("Original Focus ring color:" +
                    "WHITE/BLACK/GRAYISH, Cell Focus Ring Color: LIGHT GRAY");
            return;
        }
        selectionBck.getRGBColorComponents(bckRGB);
        originalRingColor.getRGBColorComponents(oldCellRingRGB);
        newRingColor.getRGBColorComponents(newCellRingRGB);

        float originalRGBDiff = calculateRGBDiff(oldCellRingRGB, bckRGB);
        float brighterRGBDiff = calculateRGBDiff(newCellRingRGB, bckRGB);

        System.out.println("Original RGB Diff: "+ originalRGBDiff);
        System.out.println("Brighter RGB Diff: "+ brighterRGBDiff);

        if (brighterRGBDiff <= originalRGBDiff) {
            errorLog.append(prefix + ": Cell Focus Ring Not Visible.\n");
        }
    }

    /* calculates the difference between individual RGB components of 2 colors
       and returns the total difference. A higher RGB difference is preferred
       for a prominent cell highlighter */
    private static float calculateRGBDiff(float[] focusRingRGB, float[] bckRGB) {
        float totalRGBDiff = 0;
        for (int i=0; i< focusRingRGB.length; i++) {
            totalRGBDiff += Math.abs(focusRingRGB[i] - bckRGB[i]);
        }
        return totalRGBDiff;
    }
}