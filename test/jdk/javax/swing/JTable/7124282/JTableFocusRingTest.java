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
 * @summary Checks whether the JTable's focus ring color's RGB color
 * diff with selectionBackground is greater in comparison to original
 * focus ring (represented by 'Table.cellFocusRing' property in Aqua LAF
 * UIDefaults).
 * @run main JTableFocusRingTest
 */

import java.awt.Color;
import java.util.Arrays;
import javax.swing.plaf.BorderUIResource.LineBorderUIResource;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

public class JTableFocusRingTest {

    public static void main(String[] args) throws Exception{

        try {
                UIManager.setLookAndFeel("com.apple.laf.AquaLookAndFeel");
        } catch (ClassNotFoundException | InstantiationException |
                IllegalAccessException | UnsupportedLookAndFeelException e) {
                throw new RuntimeException("Unsupported Look&Feel Class");
        }
        SwingUtilities.invokeAndWait(() -> {

            float[] bckRGB = new float[3];
            float[] oldCellRingRGB = new float[3];
            float[] newCellRingRGB = new float[3];

            Color selectionBck = null;
            Color originalRingColor = null;
            Color newRingColor = null;

            // saturation threshold for grayish colors
            float satGrayScale = 0.10f;

            if (UIManager.getDefaults().get("Table.selectionBackground") != null
                    && UIManager.getDefaults().get("Table.selectionBackground")
                    instanceof Color) {
                selectionBck = (Color) UIManager.getDefaults()
                        .get("Table.selectionBackground");
            }
            if (UIManager.getDefaults().get("Table.cellFocusRing") != null
                    && UIManager.getDefaults().get("Table.cellFocusRing")
                    instanceof Color) {
               originalRingColor = (Color) UIManager.getDefaults().get("Table.cellFocusRing");
            }

            if (UIManager.getDefaults()
                    .get("Table.focusCellHighlightBorder") != null &&
                    UIManager.getDefaults().get("Table.focusCellHighlightBorder")
                            instanceof LineBorderUIResource) {
                LineBorderUIResource cellFocusBorderObj = (LineBorderUIResource)
                        UIManager.getDefaults().get("Table.focusCellHighlightBorder");
                newRingColor = cellFocusBorderObj.getLineColor();
            }

            if (selectionBck == null || originalRingColor == null ||
                    newRingColor == null) {
                throw new RuntimeException("One or more color values are null");
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

            System.out.println("Original Focus Ring Hue, Saturation and" +
                    " Brightness: "+ Arrays.toString(hsbValues));

            // Edge case - Original Focus ring color: WHITE/BLACK/GRAY
            if (((hsbValues[0] == 0 && hsbValues[1] == 0)
                    || hsbValues[1] <= satGrayScale) &&
                    newRingColor.equals(Color.LIGHT_GRAY)) {
                System.out.println("Original Focus ring color:" +
                        "WHITE/BLACK/GRAYISH, Cell Focus Ring Color: LIGHT GRAY");
                System.out.println("Test case passed");
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
                throw new RuntimeException("Cell Focus Ring Not Visible");
            }
        });
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