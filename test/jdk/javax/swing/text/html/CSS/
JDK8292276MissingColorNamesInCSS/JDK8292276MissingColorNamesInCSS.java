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

import javax.swing.text.AttributeSet;
import javax.swing.text.html.StyleSheet;

import static javax.swing.text.html.CSS.Attribute.COLOR;

public class JDK8292276MissingColorNamesInCSS {

    // Cyan is the missing color name that originates the PR JDK8292276 :
    // Missing Color NamesIn CSS.
    // Cyan name, as most color names Colors defined in CSS Color Module
    // Level 4, is not referenced in CSS.java.
    // This test fails, if getAttribute doesn't return a cyan Color Object.
    // When a color name is missing getAttribute returns a black Color Object. 
    public static void main(String[] args) {
        StyleSheet styleSheet = new StyleSheet();
        AttributeSet attributeSet = styleSheet.getDeclaration("color: cyan;");
        Object color = attributeSet.getAttribute(COLOR);
        if (!color.toString().equals("cyan")){
            throw new RuntimeException("Failed");
        }
    }
}
