/*
 * Copyright (c) 2011, 2016, Oracle and/or its affiliates. All rights reserved.
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

package java.util.regex;

import java.util.HashMap;
import java.util.Locale;
import java.util.regex.Pattern.CharPredicate;
import java.util.regex.Pattern.BmpCharPredicate;

class CharPredicates {

    static final CharPredicate ALPHABETIC  = Character::isAlphabetic;

    // \p{gc=Decimal_Number}
    static final CharPredicate DIGIT       = Character::isDigit;

    static final CharPredicate LETTER      = Character::isLetter;

    static final CharPredicate IDEOGRAPHIC = Character::isIdeographic;

    static final CharPredicate LOWERCASE   = Character::isLowerCase;

    static final CharPredicate UPPERCASE   = Character::isUpperCase;

    static final CharPredicate TITLECASE   = Character::isTitleCase;

    // \p{Whitespace}
    static final CharPredicate WHITE_SPACE = ch ->
        ((((1 << Character.SPACE_SEPARATOR) |
           (1 << Character.LINE_SEPARATOR) |
           (1 << Character.PARAGRAPH_SEPARATOR)) >> Character.getType(ch)) & 1)
        != 0 || (ch >= 0x9 && ch <= 0xd) || (ch == 0x85);

    // \p{gc=Control}
    static final CharPredicate CONTROL     = ch ->
        Character.getType(ch) == Character.CONTROL;

    // \p{gc=Punctuation}
    static final CharPredicate PUNCTUATION = ch ->
        ((((1 << Character.CONNECTOR_PUNCTUATION) |
           (1 << Character.DASH_PUNCTUATION) |
           (1 << Character.START_PUNCTUATION) |
           (1 << Character.END_PUNCTUATION) |
           (1 << Character.OTHER_PUNCTUATION) |
           (1 << Character.INITIAL_QUOTE_PUNCTUATION) |
           (1 << Character.FINAL_QUOTE_PUNCTUATION)) >> Character.getType(ch)) & 1)
        != 0;

    // \p{gc=Decimal_Number}
    // \p{Hex_Digit}    -> PropList.txt: Hex_Digit
    static final CharPredicate HEX_DIGIT = DIGIT.union(
        ch -> (ch >= 0x0030 && ch <= 0x0039) ||
              (ch >= 0x0041 && ch <= 0x0046) ||
              (ch >= 0x0061 && ch <= 0x0066) ||
              (ch >= 0xFF10 && ch <= 0xFF19) ||
              (ch >= 0xFF21 && ch <= 0xFF26) ||
              (ch >= 0xFF41 && ch <= 0xFF46));

    static final CharPredicate ASSIGNED = ch ->
        Character.getType(ch) != Character.UNASSIGNED;

    // PropList.txt:Noncharacter_Code_Point
    static final CharPredicate NONCHARACTER_CODE_POINT = ch ->
        (ch & 0xfffe) == 0xfffe || (ch >= 0xfdd0 && ch <= 0xfdef);

    // \p{alpha}
    // \p{digit}
    static final CharPredicate ALNUM = ALPHABETIC.union(DIGIT);

    // \p{Whitespace} --
    // [\N{LF} \N{VT} \N{FF} \N{CR} \N{NEL}  -> 0xa, 0xb, 0xc, 0xd, 0x85
    //  \p{gc=Line_Separator}
    //  \p{gc=Paragraph_Separator}]
    static final CharPredicate BLANK = ch ->
        Character.getType(ch) == Character.SPACE_SEPARATOR ||
        ch == 0x9; // \N{HT}

    // [^
    //  \p{space}
    //  \p{gc=Control}
    //  \p{gc=Surrogate}
    //  \p{gc=Unassigned}]
    static final CharPredicate GRAPH = ch ->
        ((((1 << Character.SPACE_SEPARATOR) |
           (1 << Character.LINE_SEPARATOR) |
           (1 << Character.PARAGRAPH_SEPARATOR) |
           (1 << Character.CONTROL) |
           (1 << Character.SURROGATE) |
           (1 << Character.UNASSIGNED)) >> Character.getType(ch)) & 1)
        == 0;

    // \p{graph}
    // \p{blank}
    // -- \p{cntrl}
    static final CharPredicate PRINT = GRAPH.union(BLANK).and(CONTROL.negate());

    //  200C..200D    PropList.txt:Join_Control
    static final CharPredicate JOIN_CONTROL = ch -> ch == 0x200C || ch == 0x200D;

    //  \p{alpha}
    //  \p{gc=Mark}
    //  \p{digit}
    //  \p{gc=Connector_Punctuation}
    //  \p{Join_Control}    200C..200D
    static final CharPredicate WORD =
        ALPHABETIC.union(ch -> ((((1 << Character.NON_SPACING_MARK) |
                                  (1 << Character.ENCLOSING_MARK) |
                                  (1 << Character.COMBINING_SPACING_MARK) |
                                  (1 << Character.DECIMAL_DIGIT_NUMBER) |
                                  (1 << Character.CONNECTOR_PUNCTUATION))
                                 >> Character.getType(ch)) & 1) != 0,
                         JOIN_CONTROL);

    /////////////////////////////////////////////////////////////////////////////

    private static final HashMap<String, CharPredicate> posix = new HashMap<>(12);
    private static final HashMap<String, CharPredicate> uprops = new HashMap<>(18);

    private static void defPosix(String name, CharPredicate p) {
        posix.put(name, p);
    }
    private static void defUProp(String name, CharPredicate p) {
        uprops.put(name, p);
    }

    static {
        defPosix("ALPHA", ALPHABETIC);
        defPosix("LOWER", LOWERCASE);
        defPosix("UPPER", UPPERCASE);
        defPosix("SPACE", WHITE_SPACE);
        defPosix("PUNCT", PUNCTUATION);
        defPosix("XDIGIT",HEX_DIGIT);
        defPosix("ALNUM", ALNUM);
        defPosix("CNTRL", CONTROL);
        defPosix("DIGIT", DIGIT);
        defPosix("BLANK", BLANK);
        defPosix("GRAPH", GRAPH);
        defPosix("PRINT", PRINT);

        defUProp("ALPHABETIC", ALPHABETIC);
        defUProp("ASSIGNED", ASSIGNED);
        defUProp("CONTROL", CONTROL);
        defUProp("HEXDIGIT", HEX_DIGIT);
        defUProp("IDEOGRAPHIC", IDEOGRAPHIC);
        defUProp("JOINCONTROL", JOIN_CONTROL);
        defUProp("LETTER", LETTER);
        defUProp("LOWERCASE", LOWERCASE);
        defUProp("NONCHARACTERCODEPOINT", NONCHARACTER_CODE_POINT);
        defUProp("TITLECASE", TITLECASE);
        defUProp("PUNCTUATION", PUNCTUATION);
        defUProp("UPPERCASE", UPPERCASE);
        defUProp("WHITESPACE", WHITE_SPACE);
        defUProp("WORD", WORD);
        defUProp("WHITE_SPACE", WHITE_SPACE);
        defUProp("HEX_DIGIT", HEX_DIGIT);
        defUProp("NONCHARACTER_CODE_POINT", NONCHARACTER_CODE_POINT);
        defUProp("JOIN_CONTROL", JOIN_CONTROL);
    }

    public static CharPredicate forUnicodeProperty(String propName) {
        propName = propName.toUpperCase(Locale.ROOT);
        CharPredicate p = uprops.get(propName);
        if (p != null)
            return p;
        return posix.get(propName);
    }

    public static CharPredicate forPOSIXName(String propName) {
        return posix.get(propName.toUpperCase(Locale.ENGLISH));
    }

    /////////////////////////////////////////////////////////////////////////////

    /**
     * Returns a predicate matching all characters belong to a named
     * UnicodeScript.
     */
    static CharPredicate forUnicodeScript(String name) {
        final Character.UnicodeScript script;
        try {
            script = Character.UnicodeScript.forName(name);
            return ch -> script == Character.UnicodeScript.of(ch);
        } catch (IllegalArgumentException iae) {}
        return null;
    }

    /**
     * Returns a predicate matching all characters in a UnicodeBlock.
     */
    static CharPredicate forUnicodeBlock(String name) {
        final Character.UnicodeBlock block;
        try {
            block = Character.UnicodeBlock.forName(name);
            return ch -> block == Character.UnicodeBlock.of(ch);
        } catch (IllegalArgumentException iae) {}
         return null;
    }

    /////////////////////////////////////////////////////////////////////////////

    // unicode categories, aliases, properties, java methods ...

    private static final HashMap<String, CharPredicate> props = new HashMap<>(128);

    /**
     * Returns a predicate matching all characters in a named property.
     */
    static CharPredicate forProperty(String name) {
        return props.get(name);
    }

    private static void defProp(String name, CharPredicate p) {
        props.put(name, p);
    }

    private static void defCategory(String name, final int typeMask) {
        CharPredicate p = ch -> (typeMask & (1 << Character.getType(ch))) != 0;
        props.put(name, p);
    }

    private static void defRange(String name, final int lower, final int upper) {
        BmpCharPredicate p = ch -> lower <= ch && ch <= upper;
        props.put(name, p);
    }

    private static void defCtype(String name, final int ctype) {
        BmpCharPredicate p = ch -> ch < 128 && ASCII.isType(ch, ctype);
        // PrintPattern.pmap.put(p, name);
        props.put(name, p);
    }

    static {
        // Unicode character property aliases, defined in
        // http://www.unicode.org/Public/UNIDATA/PropertyValueAliases.txt
        defCategory("Cn", 1<<Character.UNASSIGNED);
        defCategory("Lu", 1<<Character.UPPERCASE_LETTER);
        defCategory("Ll", 1<<Character.LOWERCASE_LETTER);
        defCategory("Lt", 1<<Character.TITLECASE_LETTER);
        defCategory("Lm", 1<<Character.MODIFIER_LETTER);
        defCategory("Lo", 1<<Character.OTHER_LETTER);
        defCategory("Mn", 1<<Character.NON_SPACING_MARK);
        defCategory("Me", 1<<Character.ENCLOSING_MARK);
        defCategory("Mc", 1<<Character.COMBINING_SPACING_MARK);
        defCategory("Nd", 1<<Character.DECIMAL_DIGIT_NUMBER);
        defCategory("Nl", 1<<Character.LETTER_NUMBER);
        defCategory("No", 1<<Character.OTHER_NUMBER);
        defCategory("Zs", 1<<Character.SPACE_SEPARATOR);
        defCategory("Zl", 1<<Character.LINE_SEPARATOR);
        defCategory("Zp", 1<<Character.PARAGRAPH_SEPARATOR);
        defCategory("Cc", 1<<Character.CONTROL);
        defCategory("Cf", 1<<Character.FORMAT);
        defCategory("Co", 1<<Character.PRIVATE_USE);
        defCategory("Cs", 1<<Character.SURROGATE);
        defCategory("Pd", 1<<Character.DASH_PUNCTUATION);
        defCategory("Ps", 1<<Character.START_PUNCTUATION);
        defCategory("Pe", 1<<Character.END_PUNCTUATION);
        defCategory("Pc", 1<<Character.CONNECTOR_PUNCTUATION);
        defCategory("Po", 1<<Character.OTHER_PUNCTUATION);
        defCategory("Sm", 1<<Character.MATH_SYMBOL);
        defCategory("Sc", 1<<Character.CURRENCY_SYMBOL);
        defCategory("Sk", 1<<Character.MODIFIER_SYMBOL);
        defCategory("So", 1<<Character.OTHER_SYMBOL);
        defCategory("Pi", 1<<Character.INITIAL_QUOTE_PUNCTUATION);
        defCategory("Pf", 1<<Character.FINAL_QUOTE_PUNCTUATION);
        defCategory("L", ((1<<Character.UPPERCASE_LETTER) |
                          (1<<Character.LOWERCASE_LETTER) |
                          (1<<Character.TITLECASE_LETTER) |
                          (1<<Character.MODIFIER_LETTER)  |
                          (1<<Character.OTHER_LETTER)));
        defCategory("M", ((1<<Character.NON_SPACING_MARK) |
                          (1<<Character.ENCLOSING_MARK)   |
                          (1<<Character.COMBINING_SPACING_MARK)));
        defCategory("N", ((1<<Character.DECIMAL_DIGIT_NUMBER) |
                          (1<<Character.LETTER_NUMBER)        |
                          (1<<Character.OTHER_NUMBER)));
        defCategory("Z", ((1<<Character.SPACE_SEPARATOR) |
                          (1<<Character.LINE_SEPARATOR)  |
                          (1<<Character.PARAGRAPH_SEPARATOR)));
        defCategory("C", ((1<<Character.CONTROL)     |
                          (1<<Character.FORMAT)      |
                          (1<<Character.PRIVATE_USE) |
                          (1<<Character.SURROGATE)   |
                          (1<<Character.UNASSIGNED))); // Other
        defCategory("P", ((1<<Character.DASH_PUNCTUATION)      |
                          (1<<Character.START_PUNCTUATION)     |
                          (1<<Character.END_PUNCTUATION)       |
                          (1<<Character.CONNECTOR_PUNCTUATION) |
                          (1<<Character.OTHER_PUNCTUATION)     |
                          (1<<Character.INITIAL_QUOTE_PUNCTUATION) |
                          (1<<Character.FINAL_QUOTE_PUNCTUATION)));
        defCategory("S", ((1<<Character.MATH_SYMBOL)     |
                          (1<<Character.CURRENCY_SYMBOL) |
                          (1<<Character.MODIFIER_SYMBOL) |
                          (1<<Character.OTHER_SYMBOL)));
        defCategory("LC", ((1<<Character.UPPERCASE_LETTER) |
                           (1<<Character.LOWERCASE_LETTER) |
                           (1<<Character.TITLECASE_LETTER)));
        defCategory("LD", ((1<<Character.UPPERCASE_LETTER) |
                           (1<<Character.LOWERCASE_LETTER) |
                           (1<<Character.TITLECASE_LETTER) |
                           (1<<Character.MODIFIER_LETTER)  |
                           (1<<Character.OTHER_LETTER)     |
                           (1<<Character.DECIMAL_DIGIT_NUMBER)));
        defRange("L1", 0x00, 0xFF); // Latin-1
        props.put("all", ch -> true);

        // Posix regular expression character classes, defined in
        // http://www.unix.org/onlinepubs/009695399/basedefs/xbd_chap09.html
        defRange("ASCII", 0x00, 0x7F);   // ASCII
        defCtype("Alnum", ASCII.ALNUM);  // Alphanumeric characters
        defCtype("Alpha", ASCII.ALPHA);  // Alphabetic characters
        defCtype("Blank", ASCII.BLANK);  // Space and tab characters
        defCtype("Cntrl", ASCII.CNTRL);  // Control characters
        defRange("Digit", '0', '9');     // Numeric characters
        defCtype("Graph", ASCII.GRAPH);  // printable and visible
        defRange("Lower", 'a', 'z');     // Lower-case alphabetic
        defRange("Print", 0x20, 0x7E);   // Printable characters
        defCtype("Punct", ASCII.PUNCT);  // Punctuation characters
        defCtype("Space", ASCII.SPACE);  // Space characters
        defRange("Upper", 'A', 'Z');     // Upper-case alphabetic
        defCtype("XDigit",ASCII.XDIGIT); // hexadecimal digits

        // Java character properties, defined by methods in Character.java
        defProp("javaLowerCase", java.lang.Character::isLowerCase);
        defProp("javaUpperCase",  Character::isUpperCase);
        defProp("javaAlphabetic", java.lang.Character::isAlphabetic);
        defProp("javaIdeographic", java.lang.Character::isIdeographic);
        defProp("javaTitleCase", java.lang.Character::isTitleCase);
        defProp("javaDigit", java.lang.Character::isDigit);
        defProp("javaDefined", java.lang.Character::isDefined);
        defProp("javaLetter", java.lang.Character::isLetter);
        defProp("javaLetterOrDigit", java.lang.Character::isLetterOrDigit);
        defProp("javaJavaIdentifierStart", java.lang.Character::isJavaIdentifierStart);
        defProp("javaJavaIdentifierPart", java.lang.Character::isJavaIdentifierPart);
        defProp("javaUnicodeIdentifierStart", java.lang.Character::isUnicodeIdentifierStart);
        defProp("javaUnicodeIdentifierPart", java.lang.Character::isUnicodeIdentifierPart);
        defProp("javaIdentifierIgnorable", java.lang.Character::isIdentifierIgnorable);
        defProp("javaSpaceChar", java.lang.Character::isSpaceChar);
        defProp("javaWhitespace", java.lang.Character::isWhitespace);
        defProp("javaISOControl", java.lang.Character::isISOControl);
        defProp("javaMirrored", java.lang.Character::isMirrored);
    }

    /////////////////////////////////////////////////////////////////////////////

    /**
     * Posix ASCII variants, not in the lookup map
     */
    static final BmpCharPredicate ASCII_DIGIT = ch -> ch < 128 && ASCII.isDigit(ch);
    static final BmpCharPredicate ASCII_WORD  = ch -> ch < 128 && ASCII.isWord(ch);
    static final BmpCharPredicate ASCII_SPACE = ch -> ch < 128 && ASCII.isSpace(ch);

}
