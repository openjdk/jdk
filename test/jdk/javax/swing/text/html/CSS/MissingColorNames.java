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
import java.util.HexFormat;

import javax.swing.text.html.StyleSheet;

/*
 * @test
 * @bug 8292276
 * @summary Missing Color Names in CSS and CSS 4 and 8 digits hex coded Color
 * @run main MissingColorNames
 */
public class MissingColorNames {

    // The CSS 'color' property accepts <name-color> listed in the CSS Color Module Level 4.
    // - Many of they keywords are not supported.
    // The CSS 'color' property accepts <hex-color>. The syntax is a a hash character, "#",
    // followed by 3, 4, 6, or 8 hexadecimal digits.
    // - Syntax with 4 and 8 hexadecimal digits are not supported.
    // Test fails if stringToColor doesn't return the expected value.
    public static void main(String[] args) {
        StringBuilder result = new StringBuilder("Failed.");
        boolean passed = true;
        StyleSheet styleSheet = new StyleSheet();

        for (String[] rgb : listNAMEetRGBA) {
            Object obj = styleSheet.stringToColor(rgb[0]);
            if (rgb[1] == null && obj != null) {
                passed = false;
                result.append("\n          ["+ rgb[0] + " should return null] ");
            }
            if (rgb[1] != null && obj == null) {
                passed = false;
                result.append("\n          ["+ rgb[0] + " is not supported] ");
            }
            if (rgb[1] != null && obj instanceof Color col && col.getRGB() != HexFormat.fromHexDigits(rgb[1])) {
                passed = false;
                result.append("\n       -> [ "+ rgb[0] + " wrong RGB code ] ");
            }
        }
        if (!passed) {
            throw new RuntimeException(result.toString());
        }
    }

    static String [][] listNAMEetRGBA = {
        // The null argument
        // Subset of named color tests
        {null, null},
        // - This color doesn't belong to the CSS-COLOR-4 specification
        {"com.scientificware.color", null},
        // - The colors listed below belong to the CSS-COLOR-4 specification
        {"aliceblue", "fff0f8ff"},
        {"antiquewhite", "fffaebd7"},
        {"aqua", "ff00ffff"},
        {"aquamarine", "ff7fffd4"},
        {"azure", "fff0ffff"},
        {"beige", "fff5f5dc"},
        {"bisque", "ffffe4c4"},
        {"black", "ff000000"},
        {"blanchedalmond", "ffffebcd"},
        {"blue", "ff0000ff"},
        {"blueviolet", "ff8a2be2"},
        {"brown", "ffa52a2a"},
        {"burlywood", "ffdeb887"},
        {"cadetblue", "ff5f9ea0"},
        {"chartreuse", "ff7fff00"},
        {"chocolate", "ffd2691e"},
        {"coral", "ffff7f50"},
        {"cornflowerblue", "ff6495ed"},
        {"cornsilk", "fffff8dc"},
        {"crimson", "ffdc143c"},
        {"cyan", "ff00ffff"},
        {"darkblue", "ff00008b"},
        {"darkcyan", "ff008b8b"},
        {"darkgoldenrod", "ffb8860b"},
        {"darkgray", "ffa9a9a9"},
        {"darkgreen", "ff006400"},
        {"darkgrey", "ffa9a9a9"},
        {"darkkhaki", "ffbdb76b"},
        {"darkmagenta", "ff8b008b"},
        {"darkolivegreen", "ff556b2f"},
        {"darkorange", "ffff8c00"},
        {"darkorchid", "ff9932cc"},
        {"darkred", "ff8b0000"},
        {"darksalmon", "ffe9967a"},
        {"darkseagreen", "ff8fbc8f"},
        {"darkslateblue", "ff483d8b"},
        {"darkslategray", "ff2f4f4f"},
        {"darkslategrey", "ff2f4f4f"},
        {"darkturquoise", "ff00ced1"},
        {"darkviolet", "ff9400d3"},
        {"deeppink", "ffff1493"},
        {"deepskyblue", "ff00bfff"},
        {"dimgray", "ff696969"},
        {"dimgrey", "ff696969"},
        {"dodgerblue", "ff1e90ff"},
        {"firebrick", "ffb22222"},
        {"floralwhite", "fffffaf0"},
        {"forestgreen", "ff228b22"},
        {"fuchsia", "ffff00ff"},
        {"gainsboro", "ffdcdcdc"},
        {"ghostwhite", "fff8f8ff"},
        {"gold", "ffffd700"},
        {"goldenrod", "ffdaa520"},
        {"gray", "ff808080"},
        {"green", "ff008000"},
        {"greenyellow", "ffadff2f"},
        {"grey", "ff808080"},
        {"honeydew", "fff0fff0"},
        {"hotpink", "ffff69b4"},
        {"indianred", "ffcd5c5c"},
        {"indigo", "ff4b0082"},
        {"ivory", "fffffff0"},
        {"khaki", "fff0e68c"},
        {"lavender", "ffe6e6fa"},
        {"lavenderblush", "fffff0f5"},
        {"lawngreen", "ff7cfc00"},
        {"lemonchiffon", "fffffacd"},
        {"lightblue", "ffadd8e6"},
        {"lightcoral", "fff08080"},
        {"lightcyan", "ffe0ffff"},
        {"lightgoldenrodyellow", "fffafad2"},
        {"lightgray", "ffd3d3d3"},
        {"lightgreen", "ff90ee90"},
        {"lightgrey", "ffd3d3d3"},
        {"lightpink", "ffffb6c1"},
        {"lightsalmon", "ffffa07a"},
        {"lightseagreen", "ff20b2aa"},
        {"lightskyblue", "ff87cefa"},
        {"lightslategray", "ff778899"},
        {"lightslategrey", "ff778899"},
        {"lightsteelblue", "ffb0c4de"},
        {"lightyellow", "ffffffe0"},
        {"lime", "ff00ff00"},
        {"limegreen", "ff32cd32"},
        {"linen", "fffaf0e6"},
        {"magenta", "ffff00ff"},
        {"maroon", "ff800000"},
        {"mediumaquamarine", "ff66cdaa"},
        {"mediumblue", "ff0000cd"},
        {"mediumorchid", "ffba55d3"},
        {"mediumpurple", "ff9370db"},
        {"mediumseagreen", "ff3cb371"},
        {"mediumslateblue", "ff7b68ee"},
        {"mediumspringgreen", "ff00fa9a"},
        {"mediumturquoise", "ff48d1cc"},
        {"mediumvioletred", "ffc71585"},
        {"midnightblue", "ff191970"},
        {"mintcream", "fff5fffa"},
        {"mistyrose", "ffffe4e1"},
        {"moccasin", "ffffe4b5"},
        {"navajowhite", "ffffdead"},
        {"navy", "ff000080"},
        {"oldlace", "fffdf5e6"},
        {"olive", "ff808000"},
        {"olivedrab", "ff6b8e23"},
        {"orange", "ffffa500"},
        {"orangered", "ffff4500"},
        {"orchid", "ffda70d6"},
        {"palegoldenrod", "ffeee8aa"},
        {"palegreen", "ff98fb98"},
        {"paleturquoise", "ffafeeee"},
        {"palevioletred", "ffdb7093"},
        {"papayawhip", "ffffefd5"},
        {"peachpuff", "ffffdab9"},
        {"peru", "ffcd853f"},
        {"pink", "ffffc0cb"},
        {"plum", "ffdda0dd"},
        {"powderblue", "ffb0e0e6"},
        {"purple", "ff800080"},
        {"rebeccapurple", "ff663399"},
        {"red", "ffff0000"},
        {"rosybrown", "ffbc8f8f"},
        {"royalblue", "ff4169e1"},
        {"saddlebrown", "ff8b4513"},
        {"salmon", "fffa8072"},
        {"sandybrown", "fff4a460"},
        {"seagreen", "ff2e8b57"},
        {"seashell", "fffff5ee"},
        {"sienna", "ffa0522d"},
        {"silver", "ffc0c0c0"},
        {"skyblue", "ff87ceeb"},
        {"slateblue", "ff6a5acd"},
        {"slategray", "ff708090"},
        {"slategrey", "ff708090"},
        {"snow", "fffffafa"},
        {"springgreen", "ff00ff7f"},
        {"steelblue", "ff4682b4"},
        {"tan", "ffd2b48c"},
        {"teal", "ff008080"},
        {"thistle", "ffd8bfd8"},
        {"tomato", "ffff6347"},
        {"transparent", "00000000"},
        {"turquoise", "ff40e0d0"},
        {"violet", "ffee82ee"},
        {"wheat", "fff5deb3"},
        {"white", "ffffffff"},
        {"whitesmoke", "fff5f5f5"},
        {"yellow", "ffffff00"},
        {"yellowgreen", "ff9acd32"},
        // Subset of hexadecimal tests
        {"#", null},
        {"#f", null},
        {"#f0", null},
        {"#f0f", "ffff00ff"},
        // - #rgba should be interpreted as #rrggbbaa according CSS Color Level 4.
        // - Then expecting 0xaaff1122 from Color.
        {"#f12a", "aaff1122"},
        {"#f0f10", null},
        {"#f0f109", "fff0f109"},
        {"#f0f1092", null},
        // - In #rrggbbaa, last two digits should be interpreted as Alpha value according CSS Color Level 4.
        // - Then expecting 0xaaff1122 from Color.
        {"#ff1122aa", "aaff1122"},
        {"#f0f10928", "28f0f109"},
        {"f0f10928", "28f0f109"},
        {"#f0f109289", null},
        {"f0f109289", null},
        {"ppabcdef", null},
        {"b52k", null},
        {"#ppabcdef", null},
        {"#b52k", null},
        {"#ffffffff", "ffffffff"},
        {"ffffffff", "ffffffff"},
        {"#ffffff", "ffffffff"},
        {"ffffff", "ffffffff"}
    };
}