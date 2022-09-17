/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * @bug 8293776
 * @summary Adds CSS 4 and 8 digits hex coded Color
 * @run main Hex3468DigitsColor
 */

import java.awt.Color;
import javax.swing.text.html.StyleSheet;

public class Hex3468DigitsColor {

    public static void main(String[] args) {
        StringBuilder result = new StringBuilder();
        boolean passed = true;
        StyleSheet styleSheet = new StyleSheet();
        // #rgba Should be interpreted as #rrggbbaa according CSS Color Level 4.
        // Then expecting r=255 g=17 b=34 a=170
        Color color = styleSheet.stringToColor("#f12a");
        int red = color.getRed();
        int green = color.getGreen();
        int blue = color.getBlue();
        int alpha = color.getAlpha();
        result.append("  Test for #f00a");
        if (red != 255) {
            result.append(", expected r=255 but r=%s found".formatted(red));
            passed = false;
        }
        if (green != 17) {
            result.append(", expected g=17 but g=%s found".formatted(green));
            passed = false;
        }
        if (blue != 34) {
            result.append(", expected b=34 but b=%s found".formatted(blue));
            passed = false;
        }
        if (alpha != 170) {
            result.append(", expected a=170 but a=%s found".formatted(alpha));
            passed = false;
        }
        // In #rrggbbaa last two digits should be interpreted as Alpha value according CSS Color Level 4.
        // Then expecting r=255 g=17 b=34 a=170
        color = styleSheet.stringToColor("#ff1122aa");
        alpha = color.getAlpha();
        result.append("\n  Test for #ff1122aa");
        if (red != 255) {
            result.append(", expected r=255 but r=%s found".formatted(red));
            passed = false;
        }
        if (green != 17) {
            result.append(", expected g=17 but g=%s found".formatted(green));
            passed = false;
        }
        if (blue != 34) {
            result.append(", expected b=34 but b=%s found".formatted(blue));
            passed = false;
        }
        if (alpha != 170) {
            result.append(", expected a=170 but a=%s found".formatted(alpha));
            passed = false;
        }
        if (!passed) {
            result.insert(0, "Failed :");
            throw new RuntimeException(result.toString());
        }
    }
}
