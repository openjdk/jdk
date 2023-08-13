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

import javax.swing.text.html.StyleSheet;

/*
 * @test
 * @bug 8292276
 * @summary Aligns the rgb() and rgba() function behaviours in CSS.java
 * @run main RGBandRGBAalignmentBehaviours
 */
public class RGBandRGBAalignmentBehaviours {

    // The CSS 'color' property accepts <name-color> listed in the CSS Color Module Level 4.
    // - Many of they keywords are not supported.
    //
    // Test fails if stringToColor doesn't return the expected value.
    public static void main(String[] args) {
        StringBuilder result = new StringBuilder("Failed.");
        boolean passed = true;
        StyleSheet styleSheet = new StyleSheet();

        for(String[] rgb : listRGBetRGBA) {
            Object color = styleSheet.stringToColor(rgb[0]);
            String cols = color instanceof Color col ? "\n    " + rgb[0] + " hex =" + Integer.toHexString(col.getRGB()) : null;

            if (color instanceof Color col && !rgb[1].equals(Integer.toHexString(col.getRGB()))) {
                passed = false;
                result.append("\n    Fails to parse : " + rgb[0] + " -> " + rgb[1] + " " + Integer.toHexString(col.getRGB()));
            }
        }

        if (!passed) {
            throw new RuntimeException(result.toString());
        }
    }

    static String[][] listRGBetRGBA = {
        // RGB subset
        {"rgb(12 24 200)", "ff0c18c8", "rgba(12 24 200 1.0)"},
        {"rgb(12 24 200%)", "ff0c18ff", "rgba(12 24 255 1.0)"},
        {"rgb(-1 24 200%)", "ff0018ff", "rgba(0 24 255 1.0)"},
        {"rgb(300 24 28)", "ffff181c", "rgba(255 24 28 1.0)"},
        {"rgb(12 24 200 / 82%)", "d10c18c8", "rgba(12 24 200 0.82)"},
        {"rgb(12 24 200 / 0.82)", "d10c18c8", "rgba(12 24 200 0.82)"},
        {"rgb(12 24 200 / -210)", "c18c8", "rgba(12 24 200 0.0)"},
        {"rgb(12, 24, 200)", "ff0c18c8", "rgba(12 24 200 1.0)"},
        {"rgb(12, 24, 200, 210)", "ff0c18c8", "rgba(12 24 200 1.0)"},
        {"rgb(12, 24, 200 , 210)", "ff0c18c8", "rgba(12 24 200 1.0)"},
        {"rgb(12 , 24 , 200 , 210)", "ff0c18c8", "rgba(12 24 200 1.0)"},
        {"rgb(   12  ,      24 ,   200 ,             210  )", "ff0c18c8", "rgba(12 24 200 1.0)"},
        {"rgb(12 ,24, 200 ,210)", "ff0c18c8", "rgba(12 24 200 1.0)"},
        {"rgb(12,24,200,210)", "ff0c18c8", "rgba(12 24 200 1.0)"},
        {"rgb(15% 60% 49%)", "ff26997d", "rgba(38 153 125 1.0)"},
        {"rgb(15% 60% 49% / 82%)", "d126997d", "rgba(38 153 125 0.82)"},
        {"rgb(15%, 60%, 49% / 82%)", "d126997d", "rgba(38 153 125 0.82)"},
        {"rgb(0.14  60% 52.3 / 0.98)", "fa009934", "rgba(0 153 52 0.98)"},
        {"rgb(none none none)", "ff000000", "rgba(0 0 0 1.0)"},
        {"rgb(none none none / none)", "0", "rgba(0 0 0 0.0)"},
        {"rgb(none none none/none)", "0", "rgba(0 0 0 0.0)"},
        {"rgb(none none 30)", "ff00001e", "rgba(0 0 30 1.0)"},
        {"rgb(none 20 none)", "ff001400", "rgba(0 20 0 1.0)"},
        {"rgb(10 none none)", "ff0a0000", "rgba(10 0 0 1.0)"},
        {"rgb(none none none)", "ff000000", "rgba(0 0 0 1.0)"},
        {"rgb(10 50 13% / 50%)", "800a3221", "rgba(10 50 33 0.5)"},
        {"rgb(10 50 13% // 50%)", "ff000000", "rgba(0 0 0 1.0)"},
        {"rgb(10 50,, 13% // 50%)", "ff000000", "rgba(0 0 0 1.0)"},
        {"rgb(10 50 ,, 13% // 50%)", "ff000000", "rgba(0 0 0 1.0)"},
        {"rgb(1.2e1 0.24e2 2e2)", "ff0c18c8", "rgba(12 24 200 1.0)"},
        {"rgb(1200e-2 2400e-2 200000e-3)", "ff0c18c8", "rgba(12 24 200 1.0)"},
        {"rgb(120560.64646464632469823160676064670646798706406464098706464097970906464067e-4 2400e-2 200000e-3)", "ff0c18c8", "rgba(12 24 200 1.0)"},
        // RGBA subset
        {"rgba(12 24 200)", "ff0c18c8", "rgba(12 24 200 1.0)"},
        {"rgba(12 24 200%)", "ff0c18ff", "rgba(12 24 255 1.0)"},
        {"rgba(-1 24 200%)", "ff0018ff", "rgba(0 24 255 1.0)"},
        {"rgba(300 24 28)", "ffff181c", "rgba(255 24 28 1.0)"},
        {"rgba(12 24 200 / 82%)", "d10c18c8", "rgba(12 24 200 0.82)"},
        {"rgba(12 24 200 / 0.82)", "d10c18c8", "rgba(12 24 200 0.82)"},
        {"rgba(12 24 200 / -210)", "c18c8", "rgba(12 24 200 0.0)"},
        {"rgba(12, 24, 200)", "ff0c18c8", "rgba(12 24 200 1.0)"},
        {"rgba(12, 24, 200, 210)", "ff0c18c8", "rgba(12 24 200 1.0)"},
        {"rgba(12, 24, 200 , 210)", "ff0c18c8", "rgba(12 24 200 1.0)"},
        {"rgba(12 , 24 , 200 , 210)", "ff0c18c8", "rgba(12 24 200 1.0)"},
        {"rgba(   12  ,      24 ,   200 ,             210  )", "ff0c18c8", "rgba(12 24 200 1.0)"},
        {"rgba(12 ,24, 200 ,210)", "ff0c18c8", "rgba(12 24 200 1.0)"},
        {"rgba(12,24,200,210)", "ff0c18c8", "rgba(12 24 200 1.0)"},
        {"rgba(15% 60% 49%)", "ff26997d", "rgba(38 153 125 1.0)"},
        {"rgba(15% 60% 49% / 82%)", "d126997d", "rgba(38 153 125 0.82)"},
        {"rgba(15%, 60%, 49% / 82%)", "d126997d", "rgba(38 153 125 0.82)"},
        {"rgba(0.14  60% 52.3 / 0.98)", "fa009934", "rgba(0 153 52 0.98)"},
        {"rgba(none none none)", "ff000000", "rgba(0 0 0 1.0)"},
        {"rgba(none none none / none)", "0", "rgba(0 0 0 0.0)"},
        {"rgba(none none none/none)", "0", "rgba(0 0 0 0.0)"},
        {"rgba(none none 30)", "ff00001e", "rgba(0 0 30 1.0)"},
        {"rgba(none 20 none)", "ff001400", "rgba(0 20 0 1.0)"},
        {"rgba(10 none none)", "ff0a0000", "rgba(10 0 0 1.0)"},
        {"rgba(none none none)", "ff000000", "rgba(0 0 0 1.0)"},
        {"rgba(10 50 13% / 50%)", "800a3221", "rgba(10 50 33 0.5)"},
        {"rgba(10 50 13% // 50%)", "ff000000", "rgba(0 0 0 1.0)"},
        {"rgba(10 50,, 13% // 50%)", "ff000000", "rgba(0 0 0 1.0)"},
        {"rgba(10 50 ,, 13% // 50%)", "ff000000", "rgba(0 0 0 1.0)"},
        {"rgba(1.2e1 0.24e2 2e2)", "ff0c18c8", "rgba(12 24 200 1.0)"},
        {"rgba(1200e-2 2400e-2 200000e-3)", "ff0c18c8", "rgba(12 24 200 1.0)"},
        {"rgba(120560.64646464632469823160676064670646798706406464098706464097970906464067e-4 2400e-2 200000e-3)", "ff0c18c8", "rgba(12 24 200 1.0)"}
    };
}