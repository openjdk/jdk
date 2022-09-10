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
 * @bug 8292276
 * @summary Missing Color Names in CSS
 * @run main JDK8292276MissingColorNamesInCSS
 * @author Guy Abossolo Foh - ScientificWare
 */

import java.awt.Color;

import javax.swing.text.AttributeSet;
import javax.swing.text.html.StyleSheet;
import java.util.Locale;

import static javax.swing.text.html.CSS.Attribute.COLOR;
import static javax.swing.text.html.CSS.Attribute.BORDER_LEFT_COLOR;
import static javax.swing.text.html.CSS.Attribute.BORDER_RIGHT_COLOR;

public class JDK8292276MissingColorNamesInCSS {

    // The CSS 'color' property accepts <name-color>, 'transparent' keyword <rgb()>, <rgba> values.
    // - 'cyan' is the missing <name-color> keyword that originates the PR JDK8292276 :
    //   Missing Color Names In CSS.
    //   'cyan' keyword, as 131 <name-color> keywords also defined in CSS Color Module
    //   Level 4, are not referenced in CSS.java.
    // - sRGB colors defined by rgb and rgba functions must be case insensitive.
    // - 'transparent' keyword is the missing.
    //
    // This test fails,
    // - if stringToColor(null) returns null
    // - if getAttribute :
    //   - doesn't return cyan value.
    //     - When a <color-name> keyword is missing, getAttribute returns a black Color Object.
    //   - returns null when <rgb()> and <rgba()> values are not treated as being all lowercase.
    //     - When <rgb()> and <rgba()> values contains at least an uppercase character, getAttribute returns null.
    //   - returns null when using 'transparent' keyword.
    public static void main(String[] args) {
        StringBuilder result = new StringBuilder("Failed.");
        boolean passed = true;
        StyleSheet styleSheet = new StyleSheet();
        AttributeSet attributeSet;
        Object color;
        Object bdleftcolor;
        Object bdrightcolor;
        attributeSet = styleSheet.getDeclaration("""
            color: cyan;
            border-left-color : Rgb(250 210 120);
            border-right-color: transparent;
            """);
        color = attributeSet.getAttribute(COLOR);
        bdleftcolor = attributeSet.getAttribute(BORDER_LEFT_COLOR);
        bdrightcolor = attributeSet.getAttribute(BORDER_RIGHT_COLOR);

        if(styleSheet.stringToColor(null) != null){
            passed = false;
            result.append(" [stringToColor(null) must return null]");
        }
        if (!color.toString().toLowerCase(Locale.ROOT).equals("cyan")) {
            passed = false;
            result.append(" [<name-color> keyword(s) missing]");
        }
        if (bdrightcolor == null) {
            passed = false;
            result.append(" ['transparent' keyword missing]");
        }
        if (bdleftcolor == null) {
            passed = false;
            result.append(" [<rgb> or <rgba> values not case insensitive]");
        }
        if (!passed) {
            throw new RuntimeException(result.toString());
        }
    }
}
