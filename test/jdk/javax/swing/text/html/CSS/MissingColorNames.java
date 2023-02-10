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

import java.awt.Color;
import java.util.Locale;

import javax.swing.text.AttributeSet;
import javax.swing.text.html.StyleSheet;

import static javax.swing.text.html.CSS.Attribute.COLOR;
import static javax.swing.text.html.CSS.Attribute.BORDER_LEFT_COLOR;
import static javax.swing.text.html.CSS.Attribute.BORDER_RIGHT_COLOR;

/*
 * @test
 * @bug 8292276
 * @summary Missing Color Names in CSS
 * @run main MissingColorNames
 */
public class MissingColorNames {

    public static void main(String[] args) {
        StringBuilder result = new StringBuilder("Failed.");
        boolean passed = true;
        StyleSheet styleSheet = new StyleSheet();
        AttributeSet attributeSet = styleSheet.getDeclaration("""
            color: cyan;
            border-left-color : Rgb(250 210 120);
            border-right-color: transparent;
            """);
        Object color = attributeSet.getAttribute(COLOR);
        Object leftColor = attributeSet.getAttribute(BORDER_LEFT_COLOR);
        Object rightColor = attributeSet.getAttribute(BORDER_RIGHT_COLOR);

        if (styleSheet.stringToColor(null) != null) {
            passed = false;
            result.append(" [stringToColor(null) must return null]");
        }
        if (!color.toString().equals("cyan")) {
            passed = false;
            result.append(" [<name-color> keyword(s) missing]");
        }
        if (rightColor == null) {
            passed = false;
            result.append(" ['transparent' keyword missing]");
        }
        if (leftColor == null) {
            passed = false;
            result.append(" [<rgb()> or <rgba()> values not case insensitive]");
        }
        if (!passed) {
            throw new RuntimeException(result.toString());
        }
    }
}