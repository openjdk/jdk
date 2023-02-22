/*
 * Copyright (c) 2006, 2023, Oracle and/or its affiliates. All rights reserved.
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

package java.lang;

abstract class CharacterData {
    abstract int getProperties(int ch);
    abstract int getType(int ch);
    abstract boolean isDigit(int ch);
    abstract boolean isLowerCase(int ch);
    abstract boolean isUpperCase(int ch);
    abstract boolean isWhitespace(int ch);
    abstract boolean isMirrored(int ch);
    abstract boolean isJavaIdentifierStart(int ch);
    abstract boolean isJavaIdentifierPart(int ch);
    abstract boolean isUnicodeIdentifierStart(int ch);
    abstract boolean isUnicodeIdentifierPart(int ch);
    abstract boolean isIdentifierIgnorable(int ch);
    abstract int toLowerCase(int ch);
    abstract int toUpperCase(int ch);
    abstract int toTitleCase(int ch);
    abstract int digit(int ch, int radix);
    abstract int getNumericValue(int ch);
    abstract byte getDirectionality(int ch);

    //need to implement for JSR204
    int toUpperCaseEx(int ch) {
        return toUpperCase(ch);
    }

    char[] toUpperCaseCharArray(int ch) {
        return null;
    }

    boolean isOtherAlphabetic(int ch) {
        return false;
    }

    boolean isIdeographic(int ch) {
        return false;
    }

    // Character <= 0xff (basic latin) is handled by internal fast-path
    // to avoid initializing large tables.
    // Note: performance of this "fast-path" code may be sub-optimal
    // in negative cases for some accessors due to complicated ranges.
    // Should revisit after optimization of table initialization.

    static final CharacterData of(int ch) {
        if (ch >>> 8 == 0) {     // fast-path
            return CharacterDataLatin1.instance;
        } else {
            return switch (ch >>> 16) {  //plane 00-16
                case 0 -> CharacterData00.instance;
                case 1 -> CharacterData01.instance;
                case 2 -> CharacterData02.instance;
                case 3 -> CharacterData03.instance;
                case 14 -> CharacterData0E.instance;
                case 15, 16 -> CharacterDataPrivateUse.instance; // Both cases Private Use
                default -> CharacterDataUndefined.instance;
            };
        }
    }

    /**
     * There are a few Unicode code points which case folds into the latin1
     * range. This method returns that latin 1 lowercase fold,
     * or -1 if the code point does not fold into latin1.
     *
     * This method is equivalent to the following code:
     *
     * {@snippet :
     *   int folded = Character.toLowerCase(Character.toUpperCase(c));
     *   return folded <= 0XFF ? folded : -1;
     * }
     *
     * For performance reasons, the implementation compares to a set of known
     * code points instead. These code points were found using an exhaustive
     * search over all non-latin1 code points:
     *
     * {@snippet :
     *   for (int c = 256; c <= 0x3FFFF; c++) {
     *       int folded = Character.toLowerCase(Character.toUpperCase(c));
     *       if (folded <= 0XFF) {
     *           System.out.printf("0x%x folds to 0x%x%n", c, folded);
     *       }
     *   }
     * }
     *
     * To catch regressions caused by future changes in Unicode, an exhaustive
     * test verifies that the constants in this method is always
     * up to date. (See EqualsIgnoreCase.guardUnicodeFoldingToLatin1)
     */
    static int latin1CaseFold(int c) {
        return switch (c) {
            // Capital I with dot above: i
            case 0x130  -> 'i';
            // Small dotless i: i
            case 0x131  -> 'i';
            // Capital Y with diaeresis: Small y with Diaeresis
            case 0x178  -> 0xFF;
            // Small long s: Small s
            case 0x17f  -> 's';
            // Capital sharp S: Small sharp s
            case 0x1e9e -> 0xDF;
            // Kelvin sign: k
            case 0x212a -> 'k';
            // Angstrom sign: Small a with overring
            case 0x212b -> 0xE5;
            // c does not fold into latin1
            default     -> -1;
        };
    }
}
