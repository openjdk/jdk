/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

        // #rgba should be interpreted as #rrggbbaa according CSS Color Level 4.
        // Then expecting 0xaaff1122 from Color.
        Color color = styleSheet.stringToColor("#f12a");
        result.append("  Test for #f12a");
        if (0xaaff1122 != color.getRGB()) {
            passed = false;
        }

        // In #rrggbbaa, last two digits should be interpreted as Alpha value according CSS Color Level 4.
        // Then expecting 0xaaff1122 from Color.
        color = styleSheet.stringToColor("#ff1122aa");
        result.append(" and Test for #ff1122aa");
        if (0xaaff1122 != color.getRGB()) {
            passed = false;
        }

        if (!passed) {
            result.insert(0, "Failed :");
            throw new RuntimeException(result.toString());
        }
    }
}
