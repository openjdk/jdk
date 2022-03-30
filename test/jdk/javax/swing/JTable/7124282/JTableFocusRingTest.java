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
 * @requires (os.family == "mac")
 * @summary Tests whether the JTable's cell focus ring is visible against the table's selection background color
 * @run main JTableFocusRingTest
 */

import java.awt.Color;
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
            float[] originalFocusRingRGB = new float[3];
            float[] brighterFocusRingRGB = new float[3];

            Color selectionBackground = null;
            Color originalFocusRingColor = null;
            Color brighterFocusRingColor = null;

            // focus ring color returned for Graphite accent color
            Color graphiteFocusRing = new Color(135,135,140);

            if (UIManager.getDefaults().get("Table.selectionBackground") != null
                    && UIManager.getDefaults().get("Table.selectionBackground") instanceof Color) {
                selectionBackground = (Color) UIManager.getDefaults()
                        .get("Table.selectionBackground");
            }
            if (UIManager.getDefaults().get("Focus.color") != null
                    && UIManager.getDefaults().get("Focus.color") instanceof Color) {
               originalFocusRingColor = (Color) UIManager.getDefaults().get("Focus.color");
            }

            if (UIManager.getDefaults().get("Table.focusCellHighlightBorder") != null
                    && UIManager.getDefaults().get("Table.focusCellHighlightBorder")
                    instanceof LineBorderUIResource) {
                LineBorderUIResource cellFocusBorderObj = (LineBorderUIResource)
                        UIManager.getDefaults().get("Table.focusCellHighlightBorder");
                brighterFocusRingColor = cellFocusBorderObj.getLineColor();
            }

            if (selectionBackground == null || originalFocusRingColor == null ||
                    brighterFocusRingColor == null) {
                throw new RuntimeException("One or more color values are null");
            }
            System.out.println(UIManager.getLookAndFeel().toString());
            System.out.println("Selection Background Color: "+ selectionBackground.toString());
            System.out.println("Original FocusRing Color: "+ originalFocusRingColor.toString());
            System.out.println("Brighter FocusRing Color: "+ brighterFocusRingColor.toString());

            int redValue = originalFocusRingColor.getRed();
            int greenValue = originalFocusRingColor.getGreen();
            int blueValue = originalFocusRingColor.getBlue();

            // Original Focus ring color: WHITE/BLACK/GRAY
            if ((redValue == greenValue && redValue == blueValue ||
                    originalFocusRingColor.equals(graphiteFocusRing)) &&
                    brighterFocusRingColor.equals(Color.LIGHT_GRAY)) {
                System.out.println("Condition-Background Color: WHITE/BLACK/GRAY, " +
                        "Focus Ring Color: LIGHT GRAY");
                System.out.println("Test case passed");
                return;
            }
            selectionBackground.getRGBColorComponents(bckRGB);
            originalFocusRingColor.getRGBColorComponents(originalFocusRingRGB);
            brighterFocusRingColor.getRGBColorComponents(brighterFocusRingRGB);

            float originalRGBDiff = calculateRGBDiff(originalFocusRingRGB, bckRGB);
            float brighterRGBDiff = calculateRGBDiff(brighterFocusRingRGB, bckRGB);

            System.out.println("Original RGB Diff: "+ originalRGBDiff);
            System.out.println("Brighter RGB Diff: "+ brighterRGBDiff);

            if (brighterRGBDiff <= originalRGBDiff) {
                throw new RuntimeException("Cell Focus Ring Not Visible");
            }
        });
    }

    /* calculates the difference between individual RGB components of 2 colors and
       returns the total difference */
    private static float calculateRGBDiff(float[] focusRingRGB, float[] bckRGB) {

        float totalRGBDiff = 0;
        for (int i=0; i< focusRingRGB.length; i++) {
            totalRGBDiff += Math.abs(focusRingRGB[i] - bckRGB[i]);
        }
        return totalRGBDiff;
    }
}