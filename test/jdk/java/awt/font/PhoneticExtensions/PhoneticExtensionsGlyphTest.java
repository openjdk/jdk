/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8202696
 * @summary  Verifies if Phonetic extensions are getting displayed.
 */

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Locale;

public class PhoneticExtensionsGlyphTest {
    private static final String[] logicalFonts = {"dialog", "dialoginput", "serif", "sansserif", "monospaced"};

    private static final String phoneticExtnChars = "ᴀ ᴁ ᴂ ᴃ ᴄ ᴅ ᴆ ᴇ ᴈ ᴉ\n"
                                                   +"ᴊ ᴋ ᴌ ᴍ ᴎ ᴏ ᴐ ᴑ ᴒ ᴓ\n"
                                                   +"ᴔ ᴕ ᴖ ᴗ ᴘ ᴙ ᴚ ᴛ ᴜ ᴝ\n"
                                                   +"ᴞ ᴟ ᴠ ᴡ ᴢ ᴣ ᴤ ᴥ ᴦ ᴧ\n"
                                                   +"ᴨ ᴩ ᴪ ᴫ ᴬ ᴭ ᴮ ᴯ ᴰ ᴱ\n"
                                                   +"ᴲ ᴳ ᴴ ᴵ ᴶ ᴷ ᴸ ᴹ ᴺ ᴻ\n"
                                                   +"ᴼ ᴽ ᴾ ᴿ ᵀ ᵁ ᵂ ᵃ ᵄ ᵅ\n"
                                                   +"ᵆ ᵇ ᵈ ᵉ ᵊ ᵋ ᵌ ᵍ ᵎ ᵏ\n"
                                                   +"ᵐ ᵑ ᵒ ᵓ ᵔ ᵕ ᵖ ᵗ ᵘ ᵙ\n"
                                                   +"ᵚ ᵛ ᵜ ᵝ ᵞ ᵟ ᵠ ᵡ ᵢ ᵣ\n"
                                                   +"ᵤ ᵥ ᵦ ᵧ ᵨ ᵩ ᵪ ᵫ ᵬ ᵭ\n"
                                                   +"ᵮ ᵯ ᵰ ᵱ ᵲ ᵳ ᵴ ᵵ ᵶ ᵷ\n"
                                                   +"ᵸ ᵹ ᵺ ᵻ ᵼ ᵽ ᵾ ᵿ";

    public static void main(String[] args) throws Exception {
        if (!System.getProperty("os.name").startsWith("Win")) {
            return;
        }

        if(!canDisplayPhoneticChars()) {
            throw new RuntimeException("Phonetic extensions failed to display.");
        }
    }

    private static boolean isLogicalFont(Font f) {
        String fontName = f.getFamily().toLowerCase(Locale.ROOT);
        for (int i = 0; i < logicalFonts.length; i++) {
            if (logicalFonts[i].equals(fontName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean canDisplayPhoneticChars() {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        Font[] fonts = ge.getAllFonts();
        boolean ret = false;
        for (Font font : fonts) {
            if (isLogicalFont(font) && font.canDisplayUpTo(phoneticExtnChars) == -1) {
                ret = true;
                break;
            }
        }
        return ret;
    }
}
