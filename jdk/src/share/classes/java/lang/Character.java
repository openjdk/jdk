/*
 * Copyright (c) 2002, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import java.util.Locale;

/**
 * The {@code Character} class wraps a value of the primitive
 * type {@code char} in an object. An object of type
 * {@code Character} contains a single field whose type is
 * {@code char}.
 * <p>
 * In addition, this class provides several methods for determining
 * a character's category (lowercase letter, digit, etc.) and for converting
 * characters from uppercase to lowercase and vice versa.
 * <p>
 * Character information is based on the Unicode Standard, version 6.2.0.
 * <p>
 * The methods and data of class {@code Character} are defined by
 * the information in the <i>UnicodeData</i> file that is part of the
 * Unicode Character Database maintained by the Unicode
 * Consortium. This file specifies various properties including name
 * and general category for every defined Unicode code point or
 * character range.
 * <p>
 * The file and its description are available from the Unicode Consortium at:
 * <ul>
 * <li><a href="http://www.unicode.org">http://www.unicode.org</a>
 * </ul>
 *
 * <h3><a name="unicode">Unicode Character Representations</a></h3>
 *
 * <p>The {@code char} data type (and therefore the value that a
 * {@code Character} object encapsulates) are based on the
 * original Unicode specification, which defined characters as
 * fixed-width 16-bit entities. The Unicode Standard has since been
 * changed to allow for characters whose representation requires more
 * than 16 bits.  The range of legal <em>code point</em>s is now
 * U+0000 to U+10FFFF, known as <em>Unicode scalar value</em>.
 * (Refer to the <a
 * href="http://www.unicode.org/reports/tr27/#notation"><i>
 * definition</i></a> of the U+<i>n</i> notation in the Unicode
 * Standard.)
 *
 * <p><a name="BMP">The set of characters from U+0000 to U+FFFF</a> is
 * sometimes referred to as the <em>Basic Multilingual Plane (BMP)</em>.
 * <a name="supplementary">Characters</a> whose code points are greater
 * than U+FFFF are called <em>supplementary character</em>s.  The Java
 * platform uses the UTF-16 representation in {@code char} arrays and
 * in the {@code String} and {@code StringBuffer} classes. In
 * this representation, supplementary characters are represented as a pair
 * of {@code char} values, the first from the <em>high-surrogates</em>
 * range, (&#92;uD800-&#92;uDBFF), the second from the
 * <em>low-surrogates</em> range (&#92;uDC00-&#92;uDFFF).
 *
 * <p>A {@code char} value, therefore, represents Basic
 * Multilingual Plane (BMP) code points, including the surrogate
 * code points, or code units of the UTF-16 encoding. An
 * {@code int} value represents all Unicode code points,
 * including supplementary code points. The lower (least significant)
 * 21 bits of {@code int} are used to represent Unicode code
 * points and the upper (most significant) 11 bits must be zero.
 * Unless otherwise specified, the behavior with respect to
 * supplementary characters and surrogate {@code char} values is
 * as follows:
 *
 * <ul>
 * <li>The methods that only accept a {@code char} value cannot support
 * supplementary characters. They treat {@code char} values from the
 * surrogate ranges as undefined characters. For example,
 * {@code Character.isLetter('\u005CuD840')} returns {@code false}, even though
 * this specific value if followed by any low-surrogate value in a string
 * would represent a letter.
 *
 * <li>The methods that accept an {@code int} value support all
 * Unicode characters, including supplementary characters. For
 * example, {@code Character.isLetter(0x2F81A)} returns
 * {@code true} because the code point value represents a letter
 * (a CJK ideograph).
 * </ul>
 *
 * <p>In the Java SE API documentation, <em>Unicode code point</em> is
 * used for character values in the range between U+0000 and U+10FFFF,
 * and <em>Unicode code unit</em> is used for 16-bit
 * {@code char} values that are code units of the <em>UTF-16</em>
 * encoding. For more information on Unicode terminology, refer to the
 * <a href="http://www.unicode.org/glossary/">Unicode Glossary</a>.
 *
 * @author  Lee Boynton
 * @author  Guy Steele
 * @author  Akira Tanaka
 * @author  Martin Buchholz
 * @author  Ulf Zibis
 * @since   1.0
 */
public final
class Character implements java.io.Serializable, Comparable<Character> {
    /**
     * The minimum radix available for conversion to and from strings.
     * The constant value of this field is the smallest value permitted
     * for the radix argument in radix-conversion methods such as the
     * {@code digit} method, the {@code forDigit} method, and the
     * {@code toString} method of class {@code Integer}.
     *
     * @see     Character#digit(char, int)
     * @see     Character#forDigit(int, int)
     * @see     Integer#toString(int, int)
     * @see     Integer#valueOf(String)
     */
    public static final int MIN_RADIX = 2;

    /**
     * The maximum radix available for conversion to and from strings.
     * The constant value of this field is the largest value permitted
     * for the radix argument in radix-conversion methods such as the
     * {@code digit} method, the {@code forDigit} method, and the
     * {@code toString} method of class {@code Integer}.
     *
     * @see     Character#digit(char, int)
     * @see     Character#forDigit(int, int)
     * @see     Integer#toString(int, int)
     * @see     Integer#valueOf(String)
     */
    public static final int MAX_RADIX = 36;

    /**
     * The constant value of this field is the smallest value of type
     * {@code char}, {@code '\u005Cu0000'}.
     *
     * @since   1.0.2
     */
    public static final char MIN_VALUE = '\u0000';

    /**
     * The constant value of this field is the largest value of type
     * {@code char}, {@code '\u005CuFFFF'}.
     *
     * @since   1.0.2
     */
    public static final char MAX_VALUE = '\uFFFF';

    /**
     * The {@code Class} instance representing the primitive type
     * {@code char}.
     *
     * @since   1.1
     */
    @SuppressWarnings("unchecked")
    public static final Class<Character> TYPE = (Class<Character>) Class.getPrimitiveClass("char");

    /*
     * Normative general types
     */

    /*
     * General character types
     */

    /**
     * General category "Cn" in the Unicode specification.
     * @since   1.1
     */
    public static final byte UNASSIGNED = 0;

    /**
     * General category "Lu" in the Unicode specification.
     * @since   1.1
     */
    public static final byte UPPERCASE_LETTER = 1;

    /**
     * General category "Ll" in the Unicode specification.
     * @since   1.1
     */
    public static final byte LOWERCASE_LETTER = 2;

    /**
     * General category "Lt" in the Unicode specification.
     * @since   1.1
     */
    public static final byte TITLECASE_LETTER = 3;

    /**
     * General category "Lm" in the Unicode specification.
     * @since   1.1
     */
    public static final byte MODIFIER_LETTER = 4;

    /**
     * General category "Lo" in the Unicode specification.
     * @since   1.1
     */
    public static final byte OTHER_LETTER = 5;

    /**
     * General category "Mn" in the Unicode specification.
     * @since   1.1
     */
    public static final byte NON_SPACING_MARK = 6;

    /**
     * General category "Me" in the Unicode specification.
     * @since   1.1
     */
    public static final byte ENCLOSING_MARK = 7;

    /**
     * General category "Mc" in the Unicode specification.
     * @since   1.1
     */
    public static final byte COMBINING_SPACING_MARK = 8;

    /**
     * General category "Nd" in the Unicode specification.
     * @since   1.1
     */
    public static final byte DECIMAL_DIGIT_NUMBER        = 9;

    /**
     * General category "Nl" in the Unicode specification.
     * @since   1.1
     */
    public static final byte LETTER_NUMBER = 10;

    /**
     * General category "No" in the Unicode specification.
     * @since   1.1
     */
    public static final byte OTHER_NUMBER = 11;

    /**
     * General category "Zs" in the Unicode specification.
     * @since   1.1
     */
    public static final byte SPACE_SEPARATOR = 12;

    /**
     * General category "Zl" in the Unicode specification.
     * @since   1.1
     */
    public static final byte LINE_SEPARATOR = 13;

    /**
     * General category "Zp" in the Unicode specification.
     * @since   1.1
     */
    public static final byte PARAGRAPH_SEPARATOR = 14;

    /**
     * General category "Cc" in the Unicode specification.
     * @since   1.1
     */
    public static final byte CONTROL = 15;

    /**
     * General category "Cf" in the Unicode specification.
     * @since   1.1
     */
    public static final byte FORMAT = 16;

    /**
     * General category "Co" in the Unicode specification.
     * @since   1.1
     */
    public static final byte PRIVATE_USE = 18;

    /**
     * General category "Cs" in the Unicode specification.
     * @since   1.1
     */
    public static final byte SURROGATE = 19;

    /**
     * General category "Pd" in the Unicode specification.
     * @since   1.1
     */
    public static final byte DASH_PUNCTUATION = 20;

    /**
     * General category "Ps" in the Unicode specification.
     * @since   1.1
     */
    public static final byte START_PUNCTUATION = 21;

    /**
     * General category "Pe" in the Unicode specification.
     * @since   1.1
     */
    public static final byte END_PUNCTUATION = 22;

    /**
     * General category "Pc" in the Unicode specification.
     * @since   1.1
     */
    public static final byte CONNECTOR_PUNCTUATION = 23;

    /**
     * General category "Po" in the Unicode specification.
     * @since   1.1
     */
    public static final byte OTHER_PUNCTUATION = 24;

    /**
     * General category "Sm" in the Unicode specification.
     * @since   1.1
     */
    public static final byte MATH_SYMBOL = 25;

    /**
     * General category "Sc" in the Unicode specification.
     * @since   1.1
     */
    public static final byte CURRENCY_SYMBOL = 26;

    /**
     * General category "Sk" in the Unicode specification.
     * @since   1.1
     */
    public static final byte MODIFIER_SYMBOL = 27;

    /**
     * General category "So" in the Unicode specification.
     * @since   1.1
     */
    public static final byte OTHER_SYMBOL = 28;

    /**
     * General category "Pi" in the Unicode specification.
     * @since   1.4
     */
    public static final byte INITIAL_QUOTE_PUNCTUATION = 29;

    /**
     * General category "Pf" in the Unicode specification.
     * @since   1.4
     */
    public static final byte FINAL_QUOTE_PUNCTUATION = 30;

    /**
     * Error flag. Use int (code point) to avoid confusion with U+FFFF.
     */
    static final int ERROR = 0xFFFFFFFF;


    /**
     * Undefined bidirectional character type. Undefined {@code char}
     * values have undefined directionality in the Unicode specification.
     * @since 1.4
     */
    public static final byte DIRECTIONALITY_UNDEFINED = -1;

    /**
     * Strong bidirectional character type "L" in the Unicode specification.
     * @since 1.4
     */
    public static final byte DIRECTIONALITY_LEFT_TO_RIGHT = 0;

    /**
     * Strong bidirectional character type "R" in the Unicode specification.
     * @since 1.4
     */
    public static final byte DIRECTIONALITY_RIGHT_TO_LEFT = 1;

    /**
    * Strong bidirectional character type "AL" in the Unicode specification.
     * @since 1.4
     */
    public static final byte DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC = 2;

    /**
     * Weak bidirectional character type "EN" in the Unicode specification.
     * @since 1.4
     */
    public static final byte DIRECTIONALITY_EUROPEAN_NUMBER = 3;

    /**
     * Weak bidirectional character type "ES" in the Unicode specification.
     * @since 1.4
     */
    public static final byte DIRECTIONALITY_EUROPEAN_NUMBER_SEPARATOR = 4;

    /**
     * Weak bidirectional character type "ET" in the Unicode specification.
     * @since 1.4
     */
    public static final byte DIRECTIONALITY_EUROPEAN_NUMBER_TERMINATOR = 5;

    /**
     * Weak bidirectional character type "AN" in the Unicode specification.
     * @since 1.4
     */
    public static final byte DIRECTIONALITY_ARABIC_NUMBER = 6;

    /**
     * Weak bidirectional character type "CS" in the Unicode specification.
     * @since 1.4
     */
    public static final byte DIRECTIONALITY_COMMON_NUMBER_SEPARATOR = 7;

    /**
     * Weak bidirectional character type "NSM" in the Unicode specification.
     * @since 1.4
     */
    public static final byte DIRECTIONALITY_NONSPACING_MARK = 8;

    /**
     * Weak bidirectional character type "BN" in the Unicode specification.
     * @since 1.4
     */
    public static final byte DIRECTIONALITY_BOUNDARY_NEUTRAL = 9;

    /**
     * Neutral bidirectional character type "B" in the Unicode specification.
     * @since 1.4
     */
    public static final byte DIRECTIONALITY_PARAGRAPH_SEPARATOR = 10;

    /**
     * Neutral bidirectional character type "S" in the Unicode specification.
     * @since 1.4
     */
    public static final byte DIRECTIONALITY_SEGMENT_SEPARATOR = 11;

    /**
     * Neutral bidirectional character type "WS" in the Unicode specification.
     * @since 1.4
     */
    public static final byte DIRECTIONALITY_WHITESPACE = 12;

    /**
     * Neutral bidirectional character type "ON" in the Unicode specification.
     * @since 1.4
     */
    public static final byte DIRECTIONALITY_OTHER_NEUTRALS = 13;

    /**
     * Strong bidirectional character type "LRE" in the Unicode specification.
     * @since 1.4
     */
    public static final byte DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING = 14;

    /**
     * Strong bidirectional character type "LRO" in the Unicode specification.
     * @since 1.4
     */
    public static final byte DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE = 15;

    /**
     * Strong bidirectional character type "RLE" in the Unicode specification.
     * @since 1.4
     */
    public static final byte DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING = 16;

    /**
     * Strong bidirectional character type "RLO" in the Unicode specification.
     * @since 1.4
     */
    public static final byte DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE = 17;

    /**
     * Weak bidirectional character type "PDF" in the Unicode specification.
     * @since 1.4
     */
    public static final byte DIRECTIONALITY_POP_DIRECTIONAL_FORMAT = 18;

    /**
     * The minimum value of a
     * <a href="http://www.unicode.org/glossary/#high_surrogate_code_unit">
     * Unicode high-surrogate code unit</a>
     * in the UTF-16 encoding, constant {@code '\u005CuD800'}.
     * A high-surrogate is also known as a <i>leading-surrogate</i>.
     *
     * @since 1.5
     */
    public static final char MIN_HIGH_SURROGATE = '\uD800';

    /**
     * The maximum value of a
     * <a href="http://www.unicode.org/glossary/#high_surrogate_code_unit">
     * Unicode high-surrogate code unit</a>
     * in the UTF-16 encoding, constant {@code '\u005CuDBFF'}.
     * A high-surrogate is also known as a <i>leading-surrogate</i>.
     *
     * @since 1.5
     */
    public static final char MAX_HIGH_SURROGATE = '\uDBFF';

    /**
     * The minimum value of a
     * <a href="http://www.unicode.org/glossary/#low_surrogate_code_unit">
     * Unicode low-surrogate code unit</a>
     * in the UTF-16 encoding, constant {@code '\u005CuDC00'}.
     * A low-surrogate is also known as a <i>trailing-surrogate</i>.
     *
     * @since 1.5
     */
    public static final char MIN_LOW_SURROGATE  = '\uDC00';

    /**
     * The maximum value of a
     * <a href="http://www.unicode.org/glossary/#low_surrogate_code_unit">
     * Unicode low-surrogate code unit</a>
     * in the UTF-16 encoding, constant {@code '\u005CuDFFF'}.
     * A low-surrogate is also known as a <i>trailing-surrogate</i>.
     *
     * @since 1.5
     */
    public static final char MAX_LOW_SURROGATE  = '\uDFFF';

    /**
     * The minimum value of a Unicode surrogate code unit in the
     * UTF-16 encoding, constant {@code '\u005CuD800'}.
     *
     * @since 1.5
     */
    public static final char MIN_SURROGATE = MIN_HIGH_SURROGATE;

    /**
     * The maximum value of a Unicode surrogate code unit in the
     * UTF-16 encoding, constant {@code '\u005CuDFFF'}.
     *
     * @since 1.5
     */
    public static final char MAX_SURROGATE = MAX_LOW_SURROGATE;

    /**
     * The minimum value of a
     * <a href="http://www.unicode.org/glossary/#supplementary_code_point">
     * Unicode supplementary code point</a>, constant {@code U+10000}.
     *
     * @since 1.5
     */
    public static final int MIN_SUPPLEMENTARY_CODE_POINT = 0x010000;

    /**
     * The minimum value of a
     * <a href="http://www.unicode.org/glossary/#code_point">
     * Unicode code point</a>, constant {@code U+0000}.
     *
     * @since 1.5
     */
    public static final int MIN_CODE_POINT = 0x000000;

    /**
     * The maximum value of a
     * <a href="http://www.unicode.org/glossary/#code_point">
     * Unicode code point</a>, constant {@code U+10FFFF}.
     *
     * @since 1.5
     */
    public static final int MAX_CODE_POINT = 0X10FFFF;


    /**
     * Instances of this class represent particular subsets of the Unicode
     * character set.  The only family of subsets defined in the
     * {@code Character} class is {@link Character.UnicodeBlock}.
     * Other portions of the Java API may define other subsets for their
     * own purposes.
     *
     * @since 1.2
     */
    public static class Subset  {

        private String name;

        /**
         * Constructs a new {@code Subset} instance.
         *
         * @param  name  The name of this subset
         * @exception NullPointerException if name is {@code null}
         */
        protected Subset(String name) {
            if (name == null) {
                throw new NullPointerException("name");
            }
            this.name = name;
        }

        /**
         * Compares two {@code Subset} objects for equality.
         * This method returns {@code true} if and only if
         * {@code this} and the argument refer to the same
         * object; since this method is {@code final}, this
         * guarantee holds for all subclasses.
         */
        public final boolean equals(Object obj) {
            return (this == obj);
        }

        /**
         * Returns the standard hash code as defined by the
         * {@link Object#hashCode} method.  This method
         * is {@code final} in order to ensure that the
         * {@code equals} and {@code hashCode} methods will
         * be consistent in all subclasses.
         */
        public final int hashCode() {
            return super.hashCode();
        }

        /**
         * Returns the name of this subset.
         */
        public final String toString() {
            return name;
        }
    }

    // See http://www.unicode.org/Public/UNIDATA/Blocks.txt
    // for the latest specification of Unicode Blocks.

    /**
     * A family of character subsets representing the character blocks in the
     * Unicode specification. Character blocks generally define characters
     * used for a specific script or purpose. A character is contained by
     * at most one Unicode block.
     *
     * @since 1.2
     */
    public static final class UnicodeBlock extends Subset {

        private static Map<String, UnicodeBlock> map = new HashMap<>(256);

        /**
         * Creates a UnicodeBlock with the given identifier name.
         * This name must be the same as the block identifier.
         */
        private UnicodeBlock(String idName) {
            super(idName);
            map.put(idName, this);
        }

        /**
         * Creates a UnicodeBlock with the given identifier name and
         * alias name.
         */
        private UnicodeBlock(String idName, String alias) {
            this(idName);
            map.put(alias, this);
        }

        /**
         * Creates a UnicodeBlock with the given identifier name and
         * alias names.
         */
        private UnicodeBlock(String idName, String... aliases) {
            this(idName);
            for (String alias : aliases)
                map.put(alias, this);
        }

        /**
         * Constant for the "Basic Latin" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock  BASIC_LATIN =
            new UnicodeBlock("BASIC_LATIN",
                             "BASIC LATIN",
                             "BASICLATIN");

        /**
         * Constant for the "Latin-1 Supplement" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock LATIN_1_SUPPLEMENT =
            new UnicodeBlock("LATIN_1_SUPPLEMENT",
                             "LATIN-1 SUPPLEMENT",
                             "LATIN-1SUPPLEMENT");

        /**
         * Constant for the "Latin Extended-A" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock LATIN_EXTENDED_A =
            new UnicodeBlock("LATIN_EXTENDED_A",
                             "LATIN EXTENDED-A",
                             "LATINEXTENDED-A");

        /**
         * Constant for the "Latin Extended-B" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock LATIN_EXTENDED_B =
            new UnicodeBlock("LATIN_EXTENDED_B",
                             "LATIN EXTENDED-B",
                             "LATINEXTENDED-B");

        /**
         * Constant for the "IPA Extensions" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock IPA_EXTENSIONS =
            new UnicodeBlock("IPA_EXTENSIONS",
                             "IPA EXTENSIONS",
                             "IPAEXTENSIONS");

        /**
         * Constant for the "Spacing Modifier Letters" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock SPACING_MODIFIER_LETTERS =
            new UnicodeBlock("SPACING_MODIFIER_LETTERS",
                             "SPACING MODIFIER LETTERS",
                             "SPACINGMODIFIERLETTERS");

        /**
         * Constant for the "Combining Diacritical Marks" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock COMBINING_DIACRITICAL_MARKS =
            new UnicodeBlock("COMBINING_DIACRITICAL_MARKS",
                             "COMBINING DIACRITICAL MARKS",
                             "COMBININGDIACRITICALMARKS");

        /**
         * Constant for the "Greek and Coptic" Unicode character block.
         * <p>
         * This block was previously known as the "Greek" block.
         *
         * @since 1.2
         */
        public static final UnicodeBlock GREEK =
            new UnicodeBlock("GREEK",
                             "GREEK AND COPTIC",
                             "GREEKANDCOPTIC");

        /**
         * Constant for the "Cyrillic" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock CYRILLIC =
            new UnicodeBlock("CYRILLIC");

        /**
         * Constant for the "Armenian" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock ARMENIAN =
            new UnicodeBlock("ARMENIAN");

        /**
         * Constant for the "Hebrew" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock HEBREW =
            new UnicodeBlock("HEBREW");

        /**
         * Constant for the "Arabic" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock ARABIC =
            new UnicodeBlock("ARABIC");

        /**
         * Constant for the "Devanagari" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock DEVANAGARI =
            new UnicodeBlock("DEVANAGARI");

        /**
         * Constant for the "Bengali" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock BENGALI =
            new UnicodeBlock("BENGALI");

        /**
         * Constant for the "Gurmukhi" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock GURMUKHI =
            new UnicodeBlock("GURMUKHI");

        /**
         * Constant for the "Gujarati" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock GUJARATI =
            new UnicodeBlock("GUJARATI");

        /**
         * Constant for the "Oriya" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock ORIYA =
            new UnicodeBlock("ORIYA");

        /**
         * Constant for the "Tamil" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock TAMIL =
            new UnicodeBlock("TAMIL");

        /**
         * Constant for the "Telugu" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock TELUGU =
            new UnicodeBlock("TELUGU");

        /**
         * Constant for the "Kannada" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock KANNADA =
            new UnicodeBlock("KANNADA");

        /**
         * Constant for the "Malayalam" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock MALAYALAM =
            new UnicodeBlock("MALAYALAM");

        /**
         * Constant for the "Thai" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock THAI =
            new UnicodeBlock("THAI");

        /**
         * Constant for the "Lao" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock LAO =
            new UnicodeBlock("LAO");

        /**
         * Constant for the "Tibetan" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock TIBETAN =
            new UnicodeBlock("TIBETAN");

        /**
         * Constant for the "Georgian" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock GEORGIAN =
            new UnicodeBlock("GEORGIAN");

        /**
         * Constant for the "Hangul Jamo" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock HANGUL_JAMO =
            new UnicodeBlock("HANGUL_JAMO",
                             "HANGUL JAMO",
                             "HANGULJAMO");

        /**
         * Constant for the "Latin Extended Additional" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock LATIN_EXTENDED_ADDITIONAL =
            new UnicodeBlock("LATIN_EXTENDED_ADDITIONAL",
                             "LATIN EXTENDED ADDITIONAL",
                             "LATINEXTENDEDADDITIONAL");

        /**
         * Constant for the "Greek Extended" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock GREEK_EXTENDED =
            new UnicodeBlock("GREEK_EXTENDED",
                             "GREEK EXTENDED",
                             "GREEKEXTENDED");

        /**
         * Constant for the "General Punctuation" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock GENERAL_PUNCTUATION =
            new UnicodeBlock("GENERAL_PUNCTUATION",
                             "GENERAL PUNCTUATION",
                             "GENERALPUNCTUATION");

        /**
         * Constant for the "Superscripts and Subscripts" Unicode character
         * block.
         * @since 1.2
         */
        public static final UnicodeBlock SUPERSCRIPTS_AND_SUBSCRIPTS =
            new UnicodeBlock("SUPERSCRIPTS_AND_SUBSCRIPTS",
                             "SUPERSCRIPTS AND SUBSCRIPTS",
                             "SUPERSCRIPTSANDSUBSCRIPTS");

        /**
         * Constant for the "Currency Symbols" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock CURRENCY_SYMBOLS =
            new UnicodeBlock("CURRENCY_SYMBOLS",
                             "CURRENCY SYMBOLS",
                             "CURRENCYSYMBOLS");

        /**
         * Constant for the "Combining Diacritical Marks for Symbols" Unicode
         * character block.
         * <p>
         * This block was previously known as "Combining Marks for Symbols".
         * @since 1.2
         */
        public static final UnicodeBlock COMBINING_MARKS_FOR_SYMBOLS =
            new UnicodeBlock("COMBINING_MARKS_FOR_SYMBOLS",
                             "COMBINING DIACRITICAL MARKS FOR SYMBOLS",
                             "COMBININGDIACRITICALMARKSFORSYMBOLS",
                             "COMBINING MARKS FOR SYMBOLS",
                             "COMBININGMARKSFORSYMBOLS");

        /**
         * Constant for the "Letterlike Symbols" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock LETTERLIKE_SYMBOLS =
            new UnicodeBlock("LETTERLIKE_SYMBOLS",
                             "LETTERLIKE SYMBOLS",
                             "LETTERLIKESYMBOLS");

        /**
         * Constant for the "Number Forms" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock NUMBER_FORMS =
            new UnicodeBlock("NUMBER_FORMS",
                             "NUMBER FORMS",
                             "NUMBERFORMS");

        /**
         * Constant for the "Arrows" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock ARROWS =
            new UnicodeBlock("ARROWS");

        /**
         * Constant for the "Mathematical Operators" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock MATHEMATICAL_OPERATORS =
            new UnicodeBlock("MATHEMATICAL_OPERATORS",
                             "MATHEMATICAL OPERATORS",
                             "MATHEMATICALOPERATORS");

        /**
         * Constant for the "Miscellaneous Technical" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock MISCELLANEOUS_TECHNICAL =
            new UnicodeBlock("MISCELLANEOUS_TECHNICAL",
                             "MISCELLANEOUS TECHNICAL",
                             "MISCELLANEOUSTECHNICAL");

        /**
         * Constant for the "Control Pictures" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock CONTROL_PICTURES =
            new UnicodeBlock("CONTROL_PICTURES",
                             "CONTROL PICTURES",
                             "CONTROLPICTURES");

        /**
         * Constant for the "Optical Character Recognition" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock OPTICAL_CHARACTER_RECOGNITION =
            new UnicodeBlock("OPTICAL_CHARACTER_RECOGNITION",
                             "OPTICAL CHARACTER RECOGNITION",
                             "OPTICALCHARACTERRECOGNITION");

        /**
         * Constant for the "Enclosed Alphanumerics" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock ENCLOSED_ALPHANUMERICS =
            new UnicodeBlock("ENCLOSED_ALPHANUMERICS",
                             "ENCLOSED ALPHANUMERICS",
                             "ENCLOSEDALPHANUMERICS");

        /**
         * Constant for the "Box Drawing" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock BOX_DRAWING =
            new UnicodeBlock("BOX_DRAWING",
                             "BOX DRAWING",
                             "BOXDRAWING");

        /**
         * Constant for the "Block Elements" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock BLOCK_ELEMENTS =
            new UnicodeBlock("BLOCK_ELEMENTS",
                             "BLOCK ELEMENTS",
                             "BLOCKELEMENTS");

        /**
         * Constant for the "Geometric Shapes" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock GEOMETRIC_SHAPES =
            new UnicodeBlock("GEOMETRIC_SHAPES",
                             "GEOMETRIC SHAPES",
                             "GEOMETRICSHAPES");

        /**
         * Constant for the "Miscellaneous Symbols" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock MISCELLANEOUS_SYMBOLS =
            new UnicodeBlock("MISCELLANEOUS_SYMBOLS",
                             "MISCELLANEOUS SYMBOLS",
                             "MISCELLANEOUSSYMBOLS");

        /**
         * Constant for the "Dingbats" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock DINGBATS =
            new UnicodeBlock("DINGBATS");

        /**
         * Constant for the "CJK Symbols and Punctuation" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock CJK_SYMBOLS_AND_PUNCTUATION =
            new UnicodeBlock("CJK_SYMBOLS_AND_PUNCTUATION",
                             "CJK SYMBOLS AND PUNCTUATION",
                             "CJKSYMBOLSANDPUNCTUATION");

        /**
         * Constant for the "Hiragana" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock HIRAGANA =
            new UnicodeBlock("HIRAGANA");

        /**
         * Constant for the "Katakana" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock KATAKANA =
            new UnicodeBlock("KATAKANA");

        /**
         * Constant for the "Bopomofo" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock BOPOMOFO =
            new UnicodeBlock("BOPOMOFO");

        /**
         * Constant for the "Hangul Compatibility Jamo" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock HANGUL_COMPATIBILITY_JAMO =
            new UnicodeBlock("HANGUL_COMPATIBILITY_JAMO",
                             "HANGUL COMPATIBILITY JAMO",
                             "HANGULCOMPATIBILITYJAMO");

        /**
         * Constant for the "Kanbun" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock KANBUN =
            new UnicodeBlock("KANBUN");

        /**
         * Constant for the "Enclosed CJK Letters and Months" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock ENCLOSED_CJK_LETTERS_AND_MONTHS =
            new UnicodeBlock("ENCLOSED_CJK_LETTERS_AND_MONTHS",
                             "ENCLOSED CJK LETTERS AND MONTHS",
                             "ENCLOSEDCJKLETTERSANDMONTHS");

        /**
         * Constant for the "CJK Compatibility" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock CJK_COMPATIBILITY =
            new UnicodeBlock("CJK_COMPATIBILITY",
                             "CJK COMPATIBILITY",
                             "CJKCOMPATIBILITY");

        /**
         * Constant for the "CJK Unified Ideographs" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock CJK_UNIFIED_IDEOGRAPHS =
            new UnicodeBlock("CJK_UNIFIED_IDEOGRAPHS",
                             "CJK UNIFIED IDEOGRAPHS",
                             "CJKUNIFIEDIDEOGRAPHS");

        /**
         * Constant for the "Hangul Syllables" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock HANGUL_SYLLABLES =
            new UnicodeBlock("HANGUL_SYLLABLES",
                             "HANGUL SYLLABLES",
                             "HANGULSYLLABLES");

        /**
         * Constant for the "Private Use Area" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock PRIVATE_USE_AREA =
            new UnicodeBlock("PRIVATE_USE_AREA",
                             "PRIVATE USE AREA",
                             "PRIVATEUSEAREA");

        /**
         * Constant for the "CJK Compatibility Ideographs" Unicode character
         * block.
         * @since 1.2
         */
        public static final UnicodeBlock CJK_COMPATIBILITY_IDEOGRAPHS =
            new UnicodeBlock("CJK_COMPATIBILITY_IDEOGRAPHS",
                             "CJK COMPATIBILITY IDEOGRAPHS",
                             "CJKCOMPATIBILITYIDEOGRAPHS");

        /**
         * Constant for the "Alphabetic Presentation Forms" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock ALPHABETIC_PRESENTATION_FORMS =
            new UnicodeBlock("ALPHABETIC_PRESENTATION_FORMS",
                             "ALPHABETIC PRESENTATION FORMS",
                             "ALPHABETICPRESENTATIONFORMS");

        /**
         * Constant for the "Arabic Presentation Forms-A" Unicode character
         * block.
         * @since 1.2
         */
        public static final UnicodeBlock ARABIC_PRESENTATION_FORMS_A =
            new UnicodeBlock("ARABIC_PRESENTATION_FORMS_A",
                             "ARABIC PRESENTATION FORMS-A",
                             "ARABICPRESENTATIONFORMS-A");

        /**
         * Constant for the "Combining Half Marks" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock COMBINING_HALF_MARKS =
            new UnicodeBlock("COMBINING_HALF_MARKS",
                             "COMBINING HALF MARKS",
                             "COMBININGHALFMARKS");

        /**
         * Constant for the "CJK Compatibility Forms" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock CJK_COMPATIBILITY_FORMS =
            new UnicodeBlock("CJK_COMPATIBILITY_FORMS",
                             "CJK COMPATIBILITY FORMS",
                             "CJKCOMPATIBILITYFORMS");

        /**
         * Constant for the "Small Form Variants" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock SMALL_FORM_VARIANTS =
            new UnicodeBlock("SMALL_FORM_VARIANTS",
                             "SMALL FORM VARIANTS",
                             "SMALLFORMVARIANTS");

        /**
         * Constant for the "Arabic Presentation Forms-B" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock ARABIC_PRESENTATION_FORMS_B =
            new UnicodeBlock("ARABIC_PRESENTATION_FORMS_B",
                             "ARABIC PRESENTATION FORMS-B",
                             "ARABICPRESENTATIONFORMS-B");

        /**
         * Constant for the "Halfwidth and Fullwidth Forms" Unicode character
         * block.
         * @since 1.2
         */
        public static final UnicodeBlock HALFWIDTH_AND_FULLWIDTH_FORMS =
            new UnicodeBlock("HALFWIDTH_AND_FULLWIDTH_FORMS",
                             "HALFWIDTH AND FULLWIDTH FORMS",
                             "HALFWIDTHANDFULLWIDTHFORMS");

        /**
         * Constant for the "Specials" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock SPECIALS =
            new UnicodeBlock("SPECIALS");

        /**
         * @deprecated As of J2SE 5, use {@link #HIGH_SURROGATES},
         *             {@link #HIGH_PRIVATE_USE_SURROGATES}, and
         *             {@link #LOW_SURROGATES}. These new constants match
         *             the block definitions of the Unicode Standard.
         *             The {@link #of(char)} and {@link #of(int)} methods
         *             return the new constants, not SURROGATES_AREA.
         */
        @Deprecated
        public static final UnicodeBlock SURROGATES_AREA =
            new UnicodeBlock("SURROGATES_AREA");

        /**
         * Constant for the "Syriac" Unicode character block.
         * @since 1.4
         */
        public static final UnicodeBlock SYRIAC =
            new UnicodeBlock("SYRIAC");

        /**
         * Constant for the "Thaana" Unicode character block.
         * @since 1.4
         */
        public static final UnicodeBlock THAANA =
            new UnicodeBlock("THAANA");

        /**
         * Constant for the "Sinhala" Unicode character block.
         * @since 1.4
         */
        public static final UnicodeBlock SINHALA =
            new UnicodeBlock("SINHALA");

        /**
         * Constant for the "Myanmar" Unicode character block.
         * @since 1.4
         */
        public static final UnicodeBlock MYANMAR =
            new UnicodeBlock("MYANMAR");

        /**
         * Constant for the "Ethiopic" Unicode character block.
         * @since 1.4
         */
        public static final UnicodeBlock ETHIOPIC =
            new UnicodeBlock("ETHIOPIC");

        /**
         * Constant for the "Cherokee" Unicode character block.
         * @since 1.4
         */
        public static final UnicodeBlock CHEROKEE =
            new UnicodeBlock("CHEROKEE");

        /**
         * Constant for the "Unified Canadian Aboriginal Syllabics" Unicode character block.
         * @since 1.4
         */
        public static final UnicodeBlock UNIFIED_CANADIAN_ABORIGINAL_SYLLABICS =
            new UnicodeBlock("UNIFIED_CANADIAN_ABORIGINAL_SYLLABICS",
                             "UNIFIED CANADIAN ABORIGINAL SYLLABICS",
                             "UNIFIEDCANADIANABORIGINALSYLLABICS");

        /**
         * Constant for the "Ogham" Unicode character block.
         * @since 1.4
         */
        public static final UnicodeBlock OGHAM =
            new UnicodeBlock("OGHAM");

        /**
         * Constant for the "Runic" Unicode character block.
         * @since 1.4
         */
        public static final UnicodeBlock RUNIC =
            new UnicodeBlock("RUNIC");

        /**
         * Constant for the "Khmer" Unicode character block.
         * @since 1.4
         */
        public static final UnicodeBlock KHMER =
            new UnicodeBlock("KHMER");

        /**
         * Constant for the "Mongolian" Unicode character block.
         * @since 1.4
         */
        public static final UnicodeBlock MONGOLIAN =
            new UnicodeBlock("MONGOLIAN");

        /**
         * Constant for the "Braille Patterns" Unicode character block.
         * @since 1.4
         */
        public static final UnicodeBlock BRAILLE_PATTERNS =
            new UnicodeBlock("BRAILLE_PATTERNS",
                             "BRAILLE PATTERNS",
                             "BRAILLEPATTERNS");

        /**
         * Constant for the "CJK Radicals Supplement" Unicode character block.
         * @since 1.4
         */
        public static final UnicodeBlock CJK_RADICALS_SUPPLEMENT =
            new UnicodeBlock("CJK_RADICALS_SUPPLEMENT",
                             "CJK RADICALS SUPPLEMENT",
                             "CJKRADICALSSUPPLEMENT");

        /**
         * Constant for the "Kangxi Radicals" Unicode character block.
         * @since 1.4
         */
        public static final UnicodeBlock KANGXI_RADICALS =
            new UnicodeBlock("KANGXI_RADICALS",
                             "KANGXI RADICALS",
                             "KANGXIRADICALS");

        /**
         * Constant for the "Ideographic Description Characters" Unicode character block.
         * @since 1.4
         */
        public static final UnicodeBlock IDEOGRAPHIC_DESCRIPTION_CHARACTERS =
            new UnicodeBlock("IDEOGRAPHIC_DESCRIPTION_CHARACTERS",
                             "IDEOGRAPHIC DESCRIPTION CHARACTERS",
                             "IDEOGRAPHICDESCRIPTIONCHARACTERS");

        /**
         * Constant for the "Bopomofo Extended" Unicode character block.
         * @since 1.4
         */
        public static final UnicodeBlock BOPOMOFO_EXTENDED =
            new UnicodeBlock("BOPOMOFO_EXTENDED",
                             "BOPOMOFO EXTENDED",
                             "BOPOMOFOEXTENDED");

        /**
         * Constant for the "CJK Unified Ideographs Extension A" Unicode character block.
         * @since 1.4
         */
        public static final UnicodeBlock CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A =
            new UnicodeBlock("CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A",
                             "CJK UNIFIED IDEOGRAPHS EXTENSION A",
                             "CJKUNIFIEDIDEOGRAPHSEXTENSIONA");

        /**
         * Constant for the "Yi Syllables" Unicode character block.
         * @since 1.4
         */
        public static final UnicodeBlock YI_SYLLABLES =
            new UnicodeBlock("YI_SYLLABLES",
                             "YI SYLLABLES",
                             "YISYLLABLES");

        /**
         * Constant for the "Yi Radicals" Unicode character block.
         * @since 1.4
         */
        public static final UnicodeBlock YI_RADICALS =
            new UnicodeBlock("YI_RADICALS",
                             "YI RADICALS",
                             "YIRADICALS");

        /**
         * Constant for the "Cyrillic Supplementary" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock CYRILLIC_SUPPLEMENTARY =
            new UnicodeBlock("CYRILLIC_SUPPLEMENTARY",
                             "CYRILLIC SUPPLEMENTARY",
                             "CYRILLICSUPPLEMENTARY",
                             "CYRILLIC SUPPLEMENT",
                             "CYRILLICSUPPLEMENT");

        /**
         * Constant for the "Tagalog" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock TAGALOG =
            new UnicodeBlock("TAGALOG");

        /**
         * Constant for the "Hanunoo" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock HANUNOO =
            new UnicodeBlock("HANUNOO");

        /**
         * Constant for the "Buhid" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock BUHID =
            new UnicodeBlock("BUHID");

        /**
         * Constant for the "Tagbanwa" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock TAGBANWA =
            new UnicodeBlock("TAGBANWA");

        /**
         * Constant for the "Limbu" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock LIMBU =
            new UnicodeBlock("LIMBU");

        /**
         * Constant for the "Tai Le" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock TAI_LE =
            new UnicodeBlock("TAI_LE",
                             "TAI LE",
                             "TAILE");

        /**
         * Constant for the "Khmer Symbols" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock KHMER_SYMBOLS =
            new UnicodeBlock("KHMER_SYMBOLS",
                             "KHMER SYMBOLS",
                             "KHMERSYMBOLS");

        /**
         * Constant for the "Phonetic Extensions" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock PHONETIC_EXTENSIONS =
            new UnicodeBlock("PHONETIC_EXTENSIONS",
                             "PHONETIC EXTENSIONS",
                             "PHONETICEXTENSIONS");

        /**
         * Constant for the "Miscellaneous Mathematical Symbols-A" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock MISCELLANEOUS_MATHEMATICAL_SYMBOLS_A =
            new UnicodeBlock("MISCELLANEOUS_MATHEMATICAL_SYMBOLS_A",
                             "MISCELLANEOUS MATHEMATICAL SYMBOLS-A",
                             "MISCELLANEOUSMATHEMATICALSYMBOLS-A");

        /**
         * Constant for the "Supplemental Arrows-A" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock SUPPLEMENTAL_ARROWS_A =
            new UnicodeBlock("SUPPLEMENTAL_ARROWS_A",
                             "SUPPLEMENTAL ARROWS-A",
                             "SUPPLEMENTALARROWS-A");

        /**
         * Constant for the "Supplemental Arrows-B" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock SUPPLEMENTAL_ARROWS_B =
            new UnicodeBlock("SUPPLEMENTAL_ARROWS_B",
                             "SUPPLEMENTAL ARROWS-B",
                             "SUPPLEMENTALARROWS-B");

        /**
         * Constant for the "Miscellaneous Mathematical Symbols-B" Unicode
         * character block.
         * @since 1.5
         */
        public static final UnicodeBlock MISCELLANEOUS_MATHEMATICAL_SYMBOLS_B =
            new UnicodeBlock("MISCELLANEOUS_MATHEMATICAL_SYMBOLS_B",
                             "MISCELLANEOUS MATHEMATICAL SYMBOLS-B",
                             "MISCELLANEOUSMATHEMATICALSYMBOLS-B");

        /**
         * Constant for the "Supplemental Mathematical Operators" Unicode
         * character block.
         * @since 1.5
         */
        public static final UnicodeBlock SUPPLEMENTAL_MATHEMATICAL_OPERATORS =
            new UnicodeBlock("SUPPLEMENTAL_MATHEMATICAL_OPERATORS",
                             "SUPPLEMENTAL MATHEMATICAL OPERATORS",
                             "SUPPLEMENTALMATHEMATICALOPERATORS");

        /**
         * Constant for the "Miscellaneous Symbols and Arrows" Unicode character
         * block.
         * @since 1.5
         */
        public static final UnicodeBlock MISCELLANEOUS_SYMBOLS_AND_ARROWS =
            new UnicodeBlock("MISCELLANEOUS_SYMBOLS_AND_ARROWS",
                             "MISCELLANEOUS SYMBOLS AND ARROWS",
                             "MISCELLANEOUSSYMBOLSANDARROWS");

        /**
         * Constant for the "Katakana Phonetic Extensions" Unicode character
         * block.
         * @since 1.5
         */
        public static final UnicodeBlock KATAKANA_PHONETIC_EXTENSIONS =
            new UnicodeBlock("KATAKANA_PHONETIC_EXTENSIONS",
                             "KATAKANA PHONETIC EXTENSIONS",
                             "KATAKANAPHONETICEXTENSIONS");

        /**
         * Constant for the "Yijing Hexagram Symbols" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock YIJING_HEXAGRAM_SYMBOLS =
            new UnicodeBlock("YIJING_HEXAGRAM_SYMBOLS",
                             "YIJING HEXAGRAM SYMBOLS",
                             "YIJINGHEXAGRAMSYMBOLS");

        /**
         * Constant for the "Variation Selectors" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock VARIATION_SELECTORS =
            new UnicodeBlock("VARIATION_SELECTORS",
                             "VARIATION SELECTORS",
                             "VARIATIONSELECTORS");

        /**
         * Constant for the "Linear B Syllabary" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock LINEAR_B_SYLLABARY =
            new UnicodeBlock("LINEAR_B_SYLLABARY",
                             "LINEAR B SYLLABARY",
                             "LINEARBSYLLABARY");

        /**
         * Constant for the "Linear B Ideograms" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock LINEAR_B_IDEOGRAMS =
            new UnicodeBlock("LINEAR_B_IDEOGRAMS",
                             "LINEAR B IDEOGRAMS",
                             "LINEARBIDEOGRAMS");

        /**
         * Constant for the "Aegean Numbers" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock AEGEAN_NUMBERS =
            new UnicodeBlock("AEGEAN_NUMBERS",
                             "AEGEAN NUMBERS",
                             "AEGEANNUMBERS");

        /**
         * Constant for the "Old Italic" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock OLD_ITALIC =
            new UnicodeBlock("OLD_ITALIC",
                             "OLD ITALIC",
                             "OLDITALIC");

        /**
         * Constant for the "Gothic" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock GOTHIC =
            new UnicodeBlock("GOTHIC");

        /**
         * Constant for the "Ugaritic" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock UGARITIC =
            new UnicodeBlock("UGARITIC");

        /**
         * Constant for the "Deseret" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock DESERET =
            new UnicodeBlock("DESERET");

        /**
         * Constant for the "Shavian" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock SHAVIAN =
            new UnicodeBlock("SHAVIAN");

        /**
         * Constant for the "Osmanya" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock OSMANYA =
            new UnicodeBlock("OSMANYA");

        /**
         * Constant for the "Cypriot Syllabary" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock CYPRIOT_SYLLABARY =
            new UnicodeBlock("CYPRIOT_SYLLABARY",
                             "CYPRIOT SYLLABARY",
                             "CYPRIOTSYLLABARY");

        /**
         * Constant for the "Byzantine Musical Symbols" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock BYZANTINE_MUSICAL_SYMBOLS =
            new UnicodeBlock("BYZANTINE_MUSICAL_SYMBOLS",
                             "BYZANTINE MUSICAL SYMBOLS",
                             "BYZANTINEMUSICALSYMBOLS");

        /**
         * Constant for the "Musical Symbols" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock MUSICAL_SYMBOLS =
            new UnicodeBlock("MUSICAL_SYMBOLS",
                             "MUSICAL SYMBOLS",
                             "MUSICALSYMBOLS");

        /**
         * Constant for the "Tai Xuan Jing Symbols" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock TAI_XUAN_JING_SYMBOLS =
            new UnicodeBlock("TAI_XUAN_JING_SYMBOLS",
                             "TAI XUAN JING SYMBOLS",
                             "TAIXUANJINGSYMBOLS");

        /**
         * Constant for the "Mathematical Alphanumeric Symbols" Unicode
         * character block.
         * @since 1.5
         */
        public static final UnicodeBlock MATHEMATICAL_ALPHANUMERIC_SYMBOLS =
            new UnicodeBlock("MATHEMATICAL_ALPHANUMERIC_SYMBOLS",
                             "MATHEMATICAL ALPHANUMERIC SYMBOLS",
                             "MATHEMATICALALPHANUMERICSYMBOLS");

        /**
         * Constant for the "CJK Unified Ideographs Extension B" Unicode
         * character block.
         * @since 1.5
         */
        public static final UnicodeBlock CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B =
            new UnicodeBlock("CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B",
                             "CJK UNIFIED IDEOGRAPHS EXTENSION B",
                             "CJKUNIFIEDIDEOGRAPHSEXTENSIONB");

        /**
         * Constant for the "CJK Compatibility Ideographs Supplement" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT =
            new UnicodeBlock("CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT",
                             "CJK COMPATIBILITY IDEOGRAPHS SUPPLEMENT",
                             "CJKCOMPATIBILITYIDEOGRAPHSSUPPLEMENT");

        /**
         * Constant for the "Tags" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock TAGS =
            new UnicodeBlock("TAGS");

        /**
         * Constant for the "Variation Selectors Supplement" Unicode character
         * block.
         * @since 1.5
         */
        public static final UnicodeBlock VARIATION_SELECTORS_SUPPLEMENT =
            new UnicodeBlock("VARIATION_SELECTORS_SUPPLEMENT",
                             "VARIATION SELECTORS SUPPLEMENT",
                             "VARIATIONSELECTORSSUPPLEMENT");

        /**
         * Constant for the "Supplementary Private Use Area-A" Unicode character
         * block.
         * @since 1.5
         */
        public static final UnicodeBlock SUPPLEMENTARY_PRIVATE_USE_AREA_A =
            new UnicodeBlock("SUPPLEMENTARY_PRIVATE_USE_AREA_A",
                             "SUPPLEMENTARY PRIVATE USE AREA-A",
                             "SUPPLEMENTARYPRIVATEUSEAREA-A");

        /**
         * Constant for the "Supplementary Private Use Area-B" Unicode character
         * block.
         * @since 1.5
         */
        public static final UnicodeBlock SUPPLEMENTARY_PRIVATE_USE_AREA_B =
            new UnicodeBlock("SUPPLEMENTARY_PRIVATE_USE_AREA_B",
                             "SUPPLEMENTARY PRIVATE USE AREA-B",
                             "SUPPLEMENTARYPRIVATEUSEAREA-B");

        /**
         * Constant for the "High Surrogates" Unicode character block.
         * This block represents codepoint values in the high surrogate
         * range: U+D800 through U+DB7F
         *
         * @since 1.5
         */
        public static final UnicodeBlock HIGH_SURROGATES =
            new UnicodeBlock("HIGH_SURROGATES",
                             "HIGH SURROGATES",
                             "HIGHSURROGATES");

        /**
         * Constant for the "High Private Use Surrogates" Unicode character
         * block.
         * This block represents codepoint values in the private use high
         * surrogate range: U+DB80 through U+DBFF
         *
         * @since 1.5
         */
        public static final UnicodeBlock HIGH_PRIVATE_USE_SURROGATES =
            new UnicodeBlock("HIGH_PRIVATE_USE_SURROGATES",
                             "HIGH PRIVATE USE SURROGATES",
                             "HIGHPRIVATEUSESURROGATES");

        /**
         * Constant for the "Low Surrogates" Unicode character block.
         * This block represents codepoint values in the low surrogate
         * range: U+DC00 through U+DFFF
         *
         * @since 1.5
         */
        public static final UnicodeBlock LOW_SURROGATES =
            new UnicodeBlock("LOW_SURROGATES",
                             "LOW SURROGATES",
                             "LOWSURROGATES");

        /**
         * Constant for the "Arabic Supplement" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock ARABIC_SUPPLEMENT =
            new UnicodeBlock("ARABIC_SUPPLEMENT",
                             "ARABIC SUPPLEMENT",
                             "ARABICSUPPLEMENT");

        /**
         * Constant for the "NKo" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock NKO =
            new UnicodeBlock("NKO");

        /**
         * Constant for the "Samaritan" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock SAMARITAN =
            new UnicodeBlock("SAMARITAN");

        /**
         * Constant for the "Mandaic" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock MANDAIC =
            new UnicodeBlock("MANDAIC");

        /**
         * Constant for the "Ethiopic Supplement" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock ETHIOPIC_SUPPLEMENT =
            new UnicodeBlock("ETHIOPIC_SUPPLEMENT",
                             "ETHIOPIC SUPPLEMENT",
                             "ETHIOPICSUPPLEMENT");

        /**
         * Constant for the "Unified Canadian Aboriginal Syllabics Extended"
         * Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock UNIFIED_CANADIAN_ABORIGINAL_SYLLABICS_EXTENDED =
            new UnicodeBlock("UNIFIED_CANADIAN_ABORIGINAL_SYLLABICS_EXTENDED",
                             "UNIFIED CANADIAN ABORIGINAL SYLLABICS EXTENDED",
                             "UNIFIEDCANADIANABORIGINALSYLLABICSEXTENDED");

        /**
         * Constant for the "New Tai Lue" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock NEW_TAI_LUE =
            new UnicodeBlock("NEW_TAI_LUE",
                             "NEW TAI LUE",
                             "NEWTAILUE");

        /**
         * Constant for the "Buginese" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock BUGINESE =
            new UnicodeBlock("BUGINESE");

        /**
         * Constant for the "Tai Tham" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock TAI_THAM =
            new UnicodeBlock("TAI_THAM",
                             "TAI THAM",
                             "TAITHAM");

        /**
         * Constant for the "Balinese" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock BALINESE =
            new UnicodeBlock("BALINESE");

        /**
         * Constant for the "Sundanese" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock SUNDANESE =
            new UnicodeBlock("SUNDANESE");

        /**
         * Constant for the "Batak" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock BATAK =
            new UnicodeBlock("BATAK");

        /**
         * Constant for the "Lepcha" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock LEPCHA =
            new UnicodeBlock("LEPCHA");

        /**
         * Constant for the "Ol Chiki" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock OL_CHIKI =
            new UnicodeBlock("OL_CHIKI",
                             "OL CHIKI",
                             "OLCHIKI");

        /**
         * Constant for the "Vedic Extensions" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock VEDIC_EXTENSIONS =
            new UnicodeBlock("VEDIC_EXTENSIONS",
                             "VEDIC EXTENSIONS",
                             "VEDICEXTENSIONS");

        /**
         * Constant for the "Phonetic Extensions Supplement" Unicode character
         * block.
         * @since 1.7
         */
        public static final UnicodeBlock PHONETIC_EXTENSIONS_SUPPLEMENT =
            new UnicodeBlock("PHONETIC_EXTENSIONS_SUPPLEMENT",
                             "PHONETIC EXTENSIONS SUPPLEMENT",
                             "PHONETICEXTENSIONSSUPPLEMENT");

        /**
         * Constant for the "Combining Diacritical Marks Supplement" Unicode
         * character block.
         * @since 1.7
         */
        public static final UnicodeBlock COMBINING_DIACRITICAL_MARKS_SUPPLEMENT =
            new UnicodeBlock("COMBINING_DIACRITICAL_MARKS_SUPPLEMENT",
                             "COMBINING DIACRITICAL MARKS SUPPLEMENT",
                             "COMBININGDIACRITICALMARKSSUPPLEMENT");

        /**
         * Constant for the "Glagolitic" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock GLAGOLITIC =
            new UnicodeBlock("GLAGOLITIC");

        /**
         * Constant for the "Latin Extended-C" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock LATIN_EXTENDED_C =
            new UnicodeBlock("LATIN_EXTENDED_C",
                             "LATIN EXTENDED-C",
                             "LATINEXTENDED-C");

        /**
         * Constant for the "Coptic" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock COPTIC =
            new UnicodeBlock("COPTIC");

        /**
         * Constant for the "Georgian Supplement" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock GEORGIAN_SUPPLEMENT =
            new UnicodeBlock("GEORGIAN_SUPPLEMENT",
                             "GEORGIAN SUPPLEMENT",
                             "GEORGIANSUPPLEMENT");

        /**
         * Constant for the "Tifinagh" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock TIFINAGH =
            new UnicodeBlock("TIFINAGH");

        /**
         * Constant for the "Ethiopic Extended" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock ETHIOPIC_EXTENDED =
            new UnicodeBlock("ETHIOPIC_EXTENDED",
                             "ETHIOPIC EXTENDED",
                             "ETHIOPICEXTENDED");

        /**
         * Constant for the "Cyrillic Extended-A" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock CYRILLIC_EXTENDED_A =
            new UnicodeBlock("CYRILLIC_EXTENDED_A",
                             "CYRILLIC EXTENDED-A",
                             "CYRILLICEXTENDED-A");

        /**
         * Constant for the "Supplemental Punctuation" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock SUPPLEMENTAL_PUNCTUATION =
            new UnicodeBlock("SUPPLEMENTAL_PUNCTUATION",
                             "SUPPLEMENTAL PUNCTUATION",
                             "SUPPLEMENTALPUNCTUATION");

        /**
         * Constant for the "CJK Strokes" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock CJK_STROKES =
            new UnicodeBlock("CJK_STROKES",
                             "CJK STROKES",
                             "CJKSTROKES");

        /**
         * Constant for the "Lisu" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock LISU =
            new UnicodeBlock("LISU");

        /**
         * Constant for the "Vai" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock VAI =
            new UnicodeBlock("VAI");

        /**
         * Constant for the "Cyrillic Extended-B" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock CYRILLIC_EXTENDED_B =
            new UnicodeBlock("CYRILLIC_EXTENDED_B",
                             "CYRILLIC EXTENDED-B",
                             "CYRILLICEXTENDED-B");

        /**
         * Constant for the "Bamum" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock BAMUM =
            new UnicodeBlock("BAMUM");

        /**
         * Constant for the "Modifier Tone Letters" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock MODIFIER_TONE_LETTERS =
            new UnicodeBlock("MODIFIER_TONE_LETTERS",
                             "MODIFIER TONE LETTERS",
                             "MODIFIERTONELETTERS");

        /**
         * Constant for the "Latin Extended-D" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock LATIN_EXTENDED_D =
            new UnicodeBlock("LATIN_EXTENDED_D",
                             "LATIN EXTENDED-D",
                             "LATINEXTENDED-D");

        /**
         * Constant for the "Syloti Nagri" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock SYLOTI_NAGRI =
            new UnicodeBlock("SYLOTI_NAGRI",
                             "SYLOTI NAGRI",
                             "SYLOTINAGRI");

        /**
         * Constant for the "Common Indic Number Forms" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock COMMON_INDIC_NUMBER_FORMS =
            new UnicodeBlock("COMMON_INDIC_NUMBER_FORMS",
                             "COMMON INDIC NUMBER FORMS",
                             "COMMONINDICNUMBERFORMS");

        /**
         * Constant for the "Phags-pa" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock PHAGS_PA =
            new UnicodeBlock("PHAGS_PA",
                             "PHAGS-PA");

        /**
         * Constant for the "Saurashtra" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock SAURASHTRA =
            new UnicodeBlock("SAURASHTRA");

        /**
         * Constant for the "Devanagari Extended" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock DEVANAGARI_EXTENDED =
            new UnicodeBlock("DEVANAGARI_EXTENDED",
                             "DEVANAGARI EXTENDED",
                             "DEVANAGARIEXTENDED");

        /**
         * Constant for the "Kayah Li" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock KAYAH_LI =
            new UnicodeBlock("KAYAH_LI",
                             "KAYAH LI",
                             "KAYAHLI");

        /**
         * Constant for the "Rejang" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock REJANG =
            new UnicodeBlock("REJANG");

        /**
         * Constant for the "Hangul Jamo Extended-A" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock HANGUL_JAMO_EXTENDED_A =
            new UnicodeBlock("HANGUL_JAMO_EXTENDED_A",
                             "HANGUL JAMO EXTENDED-A",
                             "HANGULJAMOEXTENDED-A");

        /**
         * Constant for the "Javanese" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock JAVANESE =
            new UnicodeBlock("JAVANESE");

        /**
         * Constant for the "Cham" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock CHAM =
            new UnicodeBlock("CHAM");

        /**
         * Constant for the "Myanmar Extended-A" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock MYANMAR_EXTENDED_A =
            new UnicodeBlock("MYANMAR_EXTENDED_A",
                             "MYANMAR EXTENDED-A",
                             "MYANMAREXTENDED-A");

        /**
         * Constant for the "Tai Viet" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock TAI_VIET =
            new UnicodeBlock("TAI_VIET",
                             "TAI VIET",
                             "TAIVIET");

        /**
         * Constant for the "Ethiopic Extended-A" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock ETHIOPIC_EXTENDED_A =
            new UnicodeBlock("ETHIOPIC_EXTENDED_A",
                             "ETHIOPIC EXTENDED-A",
                             "ETHIOPICEXTENDED-A");

        /**
         * Constant for the "Meetei Mayek" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock MEETEI_MAYEK =
            new UnicodeBlock("MEETEI_MAYEK",
                             "MEETEI MAYEK",
                             "MEETEIMAYEK");

        /**
         * Constant for the "Hangul Jamo Extended-B" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock HANGUL_JAMO_EXTENDED_B =
            new UnicodeBlock("HANGUL_JAMO_EXTENDED_B",
                             "HANGUL JAMO EXTENDED-B",
                             "HANGULJAMOEXTENDED-B");

        /**
         * Constant for the "Vertical Forms" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock VERTICAL_FORMS =
            new UnicodeBlock("VERTICAL_FORMS",
                             "VERTICAL FORMS",
                             "VERTICALFORMS");

        /**
         * Constant for the "Ancient Greek Numbers" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock ANCIENT_GREEK_NUMBERS =
            new UnicodeBlock("ANCIENT_GREEK_NUMBERS",
                             "ANCIENT GREEK NUMBERS",
                             "ANCIENTGREEKNUMBERS");

        /**
         * Constant for the "Ancient Symbols" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock ANCIENT_SYMBOLS =
            new UnicodeBlock("ANCIENT_SYMBOLS",
                             "ANCIENT SYMBOLS",
                             "ANCIENTSYMBOLS");

        /**
         * Constant for the "Phaistos Disc" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock PHAISTOS_DISC =
            new UnicodeBlock("PHAISTOS_DISC",
                             "PHAISTOS DISC",
                             "PHAISTOSDISC");

        /**
         * Constant for the "Lycian" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock LYCIAN =
            new UnicodeBlock("LYCIAN");

        /**
         * Constant for the "Carian" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock CARIAN =
            new UnicodeBlock("CARIAN");

        /**
         * Constant for the "Old Persian" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock OLD_PERSIAN =
            new UnicodeBlock("OLD_PERSIAN",
                             "OLD PERSIAN",
                             "OLDPERSIAN");

        /**
         * Constant for the "Imperial Aramaic" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock IMPERIAL_ARAMAIC =
            new UnicodeBlock("IMPERIAL_ARAMAIC",
                             "IMPERIAL ARAMAIC",
                             "IMPERIALARAMAIC");

        /**
         * Constant for the "Phoenician" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock PHOENICIAN =
            new UnicodeBlock("PHOENICIAN");

        /**
         * Constant for the "Lydian" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock LYDIAN =
            new UnicodeBlock("LYDIAN");

        /**
         * Constant for the "Kharoshthi" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock KHAROSHTHI =
            new UnicodeBlock("KHAROSHTHI");

        /**
         * Constant for the "Old South Arabian" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock OLD_SOUTH_ARABIAN =
            new UnicodeBlock("OLD_SOUTH_ARABIAN",
                             "OLD SOUTH ARABIAN",
                             "OLDSOUTHARABIAN");

        /**
         * Constant for the "Avestan" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock AVESTAN =
            new UnicodeBlock("AVESTAN");

        /**
         * Constant for the "Inscriptional Parthian" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock INSCRIPTIONAL_PARTHIAN =
            new UnicodeBlock("INSCRIPTIONAL_PARTHIAN",
                             "INSCRIPTIONAL PARTHIAN",
                             "INSCRIPTIONALPARTHIAN");

        /**
         * Constant for the "Inscriptional Pahlavi" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock INSCRIPTIONAL_PAHLAVI =
            new UnicodeBlock("INSCRIPTIONAL_PAHLAVI",
                             "INSCRIPTIONAL PAHLAVI",
                             "INSCRIPTIONALPAHLAVI");

        /**
         * Constant for the "Old Turkic" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock OLD_TURKIC =
            new UnicodeBlock("OLD_TURKIC",
                             "OLD TURKIC",
                             "OLDTURKIC");

        /**
         * Constant for the "Rumi Numeral Symbols" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock RUMI_NUMERAL_SYMBOLS =
            new UnicodeBlock("RUMI_NUMERAL_SYMBOLS",
                             "RUMI NUMERAL SYMBOLS",
                             "RUMINUMERALSYMBOLS");

        /**
         * Constant for the "Brahmi" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock BRAHMI =
            new UnicodeBlock("BRAHMI");

        /**
         * Constant for the "Kaithi" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock KAITHI =
            new UnicodeBlock("KAITHI");

        /**
         * Constant for the "Cuneiform" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock CUNEIFORM =
            new UnicodeBlock("CUNEIFORM");

        /**
         * Constant for the "Cuneiform Numbers and Punctuation" Unicode
         * character block.
         * @since 1.7
         */
        public static final UnicodeBlock CUNEIFORM_NUMBERS_AND_PUNCTUATION =
            new UnicodeBlock("CUNEIFORM_NUMBERS_AND_PUNCTUATION",
                             "CUNEIFORM NUMBERS AND PUNCTUATION",
                             "CUNEIFORMNUMBERSANDPUNCTUATION");

        /**
         * Constant for the "Egyptian Hieroglyphs" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock EGYPTIAN_HIEROGLYPHS =
            new UnicodeBlock("EGYPTIAN_HIEROGLYPHS",
                             "EGYPTIAN HIEROGLYPHS",
                             "EGYPTIANHIEROGLYPHS");

        /**
         * Constant for the "Bamum Supplement" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock BAMUM_SUPPLEMENT =
            new UnicodeBlock("BAMUM_SUPPLEMENT",
                             "BAMUM SUPPLEMENT",
                             "BAMUMSUPPLEMENT");

        /**
         * Constant for the "Kana Supplement" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock KANA_SUPPLEMENT =
            new UnicodeBlock("KANA_SUPPLEMENT",
                             "KANA SUPPLEMENT",
                             "KANASUPPLEMENT");

        /**
         * Constant for the "Ancient Greek Musical Notation" Unicode character
         * block.
         * @since 1.7
         */
        public static final UnicodeBlock ANCIENT_GREEK_MUSICAL_NOTATION =
            new UnicodeBlock("ANCIENT_GREEK_MUSICAL_NOTATION",
                             "ANCIENT GREEK MUSICAL NOTATION",
                             "ANCIENTGREEKMUSICALNOTATION");

        /**
         * Constant for the "Counting Rod Numerals" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock COUNTING_ROD_NUMERALS =
            new UnicodeBlock("COUNTING_ROD_NUMERALS",
                             "COUNTING ROD NUMERALS",
                             "COUNTINGRODNUMERALS");

        /**
         * Constant for the "Mahjong Tiles" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock MAHJONG_TILES =
            new UnicodeBlock("MAHJONG_TILES",
                             "MAHJONG TILES",
                             "MAHJONGTILES");

        /**
         * Constant for the "Domino Tiles" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock DOMINO_TILES =
            new UnicodeBlock("DOMINO_TILES",
                             "DOMINO TILES",
                             "DOMINOTILES");

        /**
         * Constant for the "Playing Cards" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock PLAYING_CARDS =
            new UnicodeBlock("PLAYING_CARDS",
                             "PLAYING CARDS",
                             "PLAYINGCARDS");

        /**
         * Constant for the "Enclosed Alphanumeric Supplement" Unicode character
         * block.
         * @since 1.7
         */
        public static final UnicodeBlock ENCLOSED_ALPHANUMERIC_SUPPLEMENT =
            new UnicodeBlock("ENCLOSED_ALPHANUMERIC_SUPPLEMENT",
                             "ENCLOSED ALPHANUMERIC SUPPLEMENT",
                             "ENCLOSEDALPHANUMERICSUPPLEMENT");

        /**
         * Constant for the "Enclosed Ideographic Supplement" Unicode character
         * block.
         * @since 1.7
         */
        public static final UnicodeBlock ENCLOSED_IDEOGRAPHIC_SUPPLEMENT =
            new UnicodeBlock("ENCLOSED_IDEOGRAPHIC_SUPPLEMENT",
                             "ENCLOSED IDEOGRAPHIC SUPPLEMENT",
                             "ENCLOSEDIDEOGRAPHICSUPPLEMENT");

        /**
         * Constant for the "Miscellaneous Symbols And Pictographs" Unicode
         * character block.
         * @since 1.7
         */
        public static final UnicodeBlock MISCELLANEOUS_SYMBOLS_AND_PICTOGRAPHS =
            new UnicodeBlock("MISCELLANEOUS_SYMBOLS_AND_PICTOGRAPHS",
                             "MISCELLANEOUS SYMBOLS AND PICTOGRAPHS",
                             "MISCELLANEOUSSYMBOLSANDPICTOGRAPHS");

        /**
         * Constant for the "Emoticons" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock EMOTICONS =
            new UnicodeBlock("EMOTICONS");

        /**
         * Constant for the "Transport And Map Symbols" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock TRANSPORT_AND_MAP_SYMBOLS =
            new UnicodeBlock("TRANSPORT_AND_MAP_SYMBOLS",
                             "TRANSPORT AND MAP SYMBOLS",
                             "TRANSPORTANDMAPSYMBOLS");

        /**
         * Constant for the "Alchemical Symbols" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock ALCHEMICAL_SYMBOLS =
            new UnicodeBlock("ALCHEMICAL_SYMBOLS",
                             "ALCHEMICAL SYMBOLS",
                             "ALCHEMICALSYMBOLS");

        /**
         * Constant for the "CJK Unified Ideographs Extension C" Unicode
         * character block.
         * @since 1.7
         */
        public static final UnicodeBlock CJK_UNIFIED_IDEOGRAPHS_EXTENSION_C =
            new UnicodeBlock("CJK_UNIFIED_IDEOGRAPHS_EXTENSION_C",
                             "CJK UNIFIED IDEOGRAPHS EXTENSION C",
                             "CJKUNIFIEDIDEOGRAPHSEXTENSIONC");

        /**
         * Constant for the "CJK Unified Ideographs Extension D" Unicode
         * character block.
         * @since 1.7
         */
        public static final UnicodeBlock CJK_UNIFIED_IDEOGRAPHS_EXTENSION_D =
            new UnicodeBlock("CJK_UNIFIED_IDEOGRAPHS_EXTENSION_D",
                             "CJK UNIFIED IDEOGRAPHS EXTENSION D",
                             "CJKUNIFIEDIDEOGRAPHSEXTENSIOND");

        /**
         * Constant for the "Arabic Extended-A" Unicode character block.
         * @since 1.8
         */
        public static final UnicodeBlock ARABIC_EXTENDED_A =
            new UnicodeBlock("ARABIC_EXTENDED_A",
                             "ARABIC EXTENDED-A",
                             "ARABICEXTENDED-A");

        /**
         * Constant for the "Sundanese Supplement" Unicode character block.
         * @since 1.8
         */
        public static final UnicodeBlock SUNDANESE_SUPPLEMENT =
            new UnicodeBlock("SUNDANESE_SUPPLEMENT",
                             "SUNDANESE SUPPLEMENT",
                             "SUNDANESESUPPLEMENT");

        /**
         * Constant for the "Meetei Mayek Extensions" Unicode character block.
         * @since 1.8
         */
        public static final UnicodeBlock MEETEI_MAYEK_EXTENSIONS =
            new UnicodeBlock("MEETEI_MAYEK_EXTENSIONS",
                             "MEETEI MAYEK EXTENSIONS",
                             "MEETEIMAYEKEXTENSIONS");

        /**
         * Constant for the "Meroitic Hieroglyphs" Unicode character block.
         * @since 1.8
         */
        public static final UnicodeBlock MEROITIC_HIEROGLYPHS =
            new UnicodeBlock("MEROITIC_HIEROGLYPHS",
                             "MEROITIC HIEROGLYPHS",
                             "MEROITICHIEROGLYPHS");

        /**
         * Constant for the "Meroitic Cursive" Unicode character block.
         * @since 1.8
         */
        public static final UnicodeBlock MEROITIC_CURSIVE =
            new UnicodeBlock("MEROITIC_CURSIVE",
                             "MEROITIC CURSIVE",
                             "MEROITICCURSIVE");

        /**
         * Constant for the "Sora Sompeng" Unicode character block.
         * @since 1.8
         */
        public static final UnicodeBlock SORA_SOMPENG =
            new UnicodeBlock("SORA_SOMPENG",
                             "SORA SOMPENG",
                             "SORASOMPENG");

        /**
         * Constant for the "Chakma" Unicode character block.
         * @since 1.8
         */
        public static final UnicodeBlock CHAKMA =
            new UnicodeBlock("CHAKMA");

        /**
         * Constant for the "Sharada" Unicode character block.
         * @since 1.8
         */
        public static final UnicodeBlock SHARADA =
            new UnicodeBlock("SHARADA");

        /**
         * Constant for the "Takri" Unicode character block.
         * @since 1.8
         */
        public static final UnicodeBlock TAKRI =
            new UnicodeBlock("TAKRI");

        /**
         * Constant for the "Miao" Unicode character block.
         * @since 1.8
         */
        public static final UnicodeBlock MIAO =
            new UnicodeBlock("MIAO");

        /**
         * Constant for the "Arabic Mathematical Alphabetic Symbols" Unicode
         * character block.
         * @since 1.8
         */
        public static final UnicodeBlock ARABIC_MATHEMATICAL_ALPHABETIC_SYMBOLS =
            new UnicodeBlock("ARABIC_MATHEMATICAL_ALPHABETIC_SYMBOLS",
                             "ARABIC MATHEMATICAL ALPHABETIC SYMBOLS",
                             "ARABICMATHEMATICALALPHABETICSYMBOLS");

        private static final int blockStarts[] = {
            0x0000,   // 0000..007F; Basic Latin
            0x0080,   // 0080..00FF; Latin-1 Supplement
            0x0100,   // 0100..017F; Latin Extended-A
            0x0180,   // 0180..024F; Latin Extended-B
            0x0250,   // 0250..02AF; IPA Extensions
            0x02B0,   // 02B0..02FF; Spacing Modifier Letters
            0x0300,   // 0300..036F; Combining Diacritical Marks
            0x0370,   // 0370..03FF; Greek and Coptic
            0x0400,   // 0400..04FF; Cyrillic
            0x0500,   // 0500..052F; Cyrillic Supplement
            0x0530,   // 0530..058F; Armenian
            0x0590,   // 0590..05FF; Hebrew
            0x0600,   // 0600..06FF; Arabic
            0x0700,   // 0700..074F; Syriac
            0x0750,   // 0750..077F; Arabic Supplement
            0x0780,   // 0780..07BF; Thaana
            0x07C0,   // 07C0..07FF; NKo
            0x0800,   // 0800..083F; Samaritan
            0x0840,   // 0840..085F; Mandaic
            0x0860,   //             unassigned
            0x08A0,   // 08A0..08FF; Arabic Extended-A
            0x0900,   // 0900..097F; Devanagari
            0x0980,   // 0980..09FF; Bengali
            0x0A00,   // 0A00..0A7F; Gurmukhi
            0x0A80,   // 0A80..0AFF; Gujarati
            0x0B00,   // 0B00..0B7F; Oriya
            0x0B80,   // 0B80..0BFF; Tamil
            0x0C00,   // 0C00..0C7F; Telugu
            0x0C80,   // 0C80..0CFF; Kannada
            0x0D00,   // 0D00..0D7F; Malayalam
            0x0D80,   // 0D80..0DFF; Sinhala
            0x0E00,   // 0E00..0E7F; Thai
            0x0E80,   // 0E80..0EFF; Lao
            0x0F00,   // 0F00..0FFF; Tibetan
            0x1000,   // 1000..109F; Myanmar
            0x10A0,   // 10A0..10FF; Georgian
            0x1100,   // 1100..11FF; Hangul Jamo
            0x1200,   // 1200..137F; Ethiopic
            0x1380,   // 1380..139F; Ethiopic Supplement
            0x13A0,   // 13A0..13FF; Cherokee
            0x1400,   // 1400..167F; Unified Canadian Aboriginal Syllabics
            0x1680,   // 1680..169F; Ogham
            0x16A0,   // 16A0..16FF; Runic
            0x1700,   // 1700..171F; Tagalog
            0x1720,   // 1720..173F; Hanunoo
            0x1740,   // 1740..175F; Buhid
            0x1760,   // 1760..177F; Tagbanwa
            0x1780,   // 1780..17FF; Khmer
            0x1800,   // 1800..18AF; Mongolian
            0x18B0,   // 18B0..18FF; Unified Canadian Aboriginal Syllabics Extended
            0x1900,   // 1900..194F; Limbu
            0x1950,   // 1950..197F; Tai Le
            0x1980,   // 1980..19DF; New Tai Lue
            0x19E0,   // 19E0..19FF; Khmer Symbols
            0x1A00,   // 1A00..1A1F; Buginese
            0x1A20,   // 1A20..1AAF; Tai Tham
            0x1AB0,   //             unassigned
            0x1B00,   // 1B00..1B7F; Balinese
            0x1B80,   // 1B80..1BBF; Sundanese
            0x1BC0,   // 1BC0..1BFF; Batak
            0x1C00,   // 1C00..1C4F; Lepcha
            0x1C50,   // 1C50..1C7F; Ol Chiki
            0x1C80,   //             unassigned
            0x1CC0,   // 1CC0..1CCF; Sundanese Supplement
            0x1CD0,   // 1CD0..1CFF; Vedic Extensions
            0x1D00,   // 1D00..1D7F; Phonetic Extensions
            0x1D80,   // 1D80..1DBF; Phonetic Extensions Supplement
            0x1DC0,   // 1DC0..1DFF; Combining Diacritical Marks Supplement
            0x1E00,   // 1E00..1EFF; Latin Extended Additional
            0x1F00,   // 1F00..1FFF; Greek Extended
            0x2000,   // 2000..206F; General Punctuation
            0x2070,   // 2070..209F; Superscripts and Subscripts
            0x20A0,   // 20A0..20CF; Currency Symbols
            0x20D0,   // 20D0..20FF; Combining Diacritical Marks for Symbols
            0x2100,   // 2100..214F; Letterlike Symbols
            0x2150,   // 2150..218F; Number Forms
            0x2190,   // 2190..21FF; Arrows
            0x2200,   // 2200..22FF; Mathematical Operators
            0x2300,   // 2300..23FF; Miscellaneous Technical
            0x2400,   // 2400..243F; Control Pictures
            0x2440,   // 2440..245F; Optical Character Recognition
            0x2460,   // 2460..24FF; Enclosed Alphanumerics
            0x2500,   // 2500..257F; Box Drawing
            0x2580,   // 2580..259F; Block Elements
            0x25A0,   // 25A0..25FF; Geometric Shapes
            0x2600,   // 2600..26FF; Miscellaneous Symbols
            0x2700,   // 2700..27BF; Dingbats
            0x27C0,   // 27C0..27EF; Miscellaneous Mathematical Symbols-A
            0x27F0,   // 27F0..27FF; Supplemental Arrows-A
            0x2800,   // 2800..28FF; Braille Patterns
            0x2900,   // 2900..297F; Supplemental Arrows-B
            0x2980,   // 2980..29FF; Miscellaneous Mathematical Symbols-B
            0x2A00,   // 2A00..2AFF; Supplemental Mathematical Operators
            0x2B00,   // 2B00..2BFF; Miscellaneous Symbols and Arrows
            0x2C00,   // 2C00..2C5F; Glagolitic
            0x2C60,   // 2C60..2C7F; Latin Extended-C
            0x2C80,   // 2C80..2CFF; Coptic
            0x2D00,   // 2D00..2D2F; Georgian Supplement
            0x2D30,   // 2D30..2D7F; Tifinagh
            0x2D80,   // 2D80..2DDF; Ethiopic Extended
            0x2DE0,   // 2DE0..2DFF; Cyrillic Extended-A
            0x2E00,   // 2E00..2E7F; Supplemental Punctuation
            0x2E80,   // 2E80..2EFF; CJK Radicals Supplement
            0x2F00,   // 2F00..2FDF; Kangxi Radicals
            0x2FE0,   //             unassigned
            0x2FF0,   // 2FF0..2FFF; Ideographic Description Characters
            0x3000,   // 3000..303F; CJK Symbols and Punctuation
            0x3040,   // 3040..309F; Hiragana
            0x30A0,   // 30A0..30FF; Katakana
            0x3100,   // 3100..312F; Bopomofo
            0x3130,   // 3130..318F; Hangul Compatibility Jamo
            0x3190,   // 3190..319F; Kanbun
            0x31A0,   // 31A0..31BF; Bopomofo Extended
            0x31C0,   // 31C0..31EF; CJK Strokes
            0x31F0,   // 31F0..31FF; Katakana Phonetic Extensions
            0x3200,   // 3200..32FF; Enclosed CJK Letters and Months
            0x3300,   // 3300..33FF; CJK Compatibility
            0x3400,   // 3400..4DBF; CJK Unified Ideographs Extension A
            0x4DC0,   // 4DC0..4DFF; Yijing Hexagram Symbols
            0x4E00,   // 4E00..9FFF; CJK Unified Ideographs
            0xA000,   // A000..A48F; Yi Syllables
            0xA490,   // A490..A4CF; Yi Radicals
            0xA4D0,   // A4D0..A4FF; Lisu
            0xA500,   // A500..A63F; Vai
            0xA640,   // A640..A69F; Cyrillic Extended-B
            0xA6A0,   // A6A0..A6FF; Bamum
            0xA700,   // A700..A71F; Modifier Tone Letters
            0xA720,   // A720..A7FF; Latin Extended-D
            0xA800,   // A800..A82F; Syloti Nagri
            0xA830,   // A830..A83F; Common Indic Number Forms
            0xA840,   // A840..A87F; Phags-pa
            0xA880,   // A880..A8DF; Saurashtra
            0xA8E0,   // A8E0..A8FF; Devanagari Extended
            0xA900,   // A900..A92F; Kayah Li
            0xA930,   // A930..A95F; Rejang
            0xA960,   // A960..A97F; Hangul Jamo Extended-A
            0xA980,   // A980..A9DF; Javanese
            0xA9E0,   //             unassigned
            0xAA00,   // AA00..AA5F; Cham
            0xAA60,   // AA60..AA7F; Myanmar Extended-A
            0xAA80,   // AA80..AADF; Tai Viet
            0xAAE0,   // AAE0..AAFF; Meetei Mayek Extensions
            0xAB00,   // AB00..AB2F; Ethiopic Extended-A
            0xAB30,   //             unassigned
            0xABC0,   // ABC0..ABFF; Meetei Mayek
            0xAC00,   // AC00..D7AF; Hangul Syllables
            0xD7B0,   // D7B0..D7FF; Hangul Jamo Extended-B
            0xD800,   // D800..DB7F; High Surrogates
            0xDB80,   // DB80..DBFF; High Private Use Surrogates
            0xDC00,   // DC00..DFFF; Low Surrogates
            0xE000,   // E000..F8FF; Private Use Area
            0xF900,   // F900..FAFF; CJK Compatibility Ideographs
            0xFB00,   // FB00..FB4F; Alphabetic Presentation Forms
            0xFB50,   // FB50..FDFF; Arabic Presentation Forms-A
            0xFE00,   // FE00..FE0F; Variation Selectors
            0xFE10,   // FE10..FE1F; Vertical Forms
            0xFE20,   // FE20..FE2F; Combining Half Marks
            0xFE30,   // FE30..FE4F; CJK Compatibility Forms
            0xFE50,   // FE50..FE6F; Small Form Variants
            0xFE70,   // FE70..FEFF; Arabic Presentation Forms-B
            0xFF00,   // FF00..FFEF; Halfwidth and Fullwidth Forms
            0xFFF0,   // FFF0..FFFF; Specials
            0x10000,  // 10000..1007F; Linear B Syllabary
            0x10080,  // 10080..100FF; Linear B Ideograms
            0x10100,  // 10100..1013F; Aegean Numbers
            0x10140,  // 10140..1018F; Ancient Greek Numbers
            0x10190,  // 10190..101CF; Ancient Symbols
            0x101D0,  // 101D0..101FF; Phaistos Disc
            0x10200,  //               unassigned
            0x10280,  // 10280..1029F; Lycian
            0x102A0,  // 102A0..102DF; Carian
            0x102E0,  //               unassigned
            0x10300,  // 10300..1032F; Old Italic
            0x10330,  // 10330..1034F; Gothic
            0x10350,  //               unassigned
            0x10380,  // 10380..1039F; Ugaritic
            0x103A0,  // 103A0..103DF; Old Persian
            0x103E0,  //               unassigned
            0x10400,  // 10400..1044F; Deseret
            0x10450,  // 10450..1047F; Shavian
            0x10480,  // 10480..104AF; Osmanya
            0x104B0,  //               unassigned
            0x10800,  // 10800..1083F; Cypriot Syllabary
            0x10840,  // 10840..1085F; Imperial Aramaic
            0x10860,  //               unassigned
            0x10900,  // 10900..1091F; Phoenician
            0x10920,  // 10920..1093F; Lydian
            0x10940,  //               unassigned
            0x10980,  // 10980..1099F; Meroitic Hieroglyphs
            0x109A0,  // 109A0..109FF; Meroitic Cursive
            0x10A00,  // 10A00..10A5F; Kharoshthi
            0x10A60,  // 10A60..10A7F; Old South Arabian
            0x10A80,  //               unassigned
            0x10B00,  // 10B00..10B3F; Avestan
            0x10B40,  // 10B40..10B5F; Inscriptional Parthian
            0x10B60,  // 10B60..10B7F; Inscriptional Pahlavi
            0x10B80,  //               unassigned
            0x10C00,  // 10C00..10C4F; Old Turkic
            0x10C50,  //               unassigned
            0x10E60,  // 10E60..10E7F; Rumi Numeral Symbols
            0x10E80,  //               unassigned
            0x11000,  // 11000..1107F; Brahmi
            0x11080,  // 11080..110CF; Kaithi
            0x110D0,  // 110D0..110FF; Sora Sompeng
            0x11100,  // 11100..1114F; Chakma
            0x11150,  //               unassigned
            0x11180,  // 11180..111DF; Sharada
            0x111E0,  //               unassigned
            0x11680,  // 11680..116CF; Takri
            0x116D0,  //               unassigned
            0x12000,  // 12000..123FF; Cuneiform
            0x12400,  // 12400..1247F; Cuneiform Numbers and Punctuation
            0x12480,  //               unassigned
            0x13000,  // 13000..1342F; Egyptian Hieroglyphs
            0x13430,  //               unassigned
            0x16800,  // 16800..16A3F; Bamum Supplement
            0x16A40,  //               unassigned
            0x16F00,  // 16F00..16F9F; Miao
            0x16FA0,  //               unassigned
            0x1B000,  // 1B000..1B0FF; Kana Supplement
            0x1B100,  //               unassigned
            0x1D000,  // 1D000..1D0FF; Byzantine Musical Symbols
            0x1D100,  // 1D100..1D1FF; Musical Symbols
            0x1D200,  // 1D200..1D24F; Ancient Greek Musical Notation
            0x1D250,  //               unassigned
            0x1D300,  // 1D300..1D35F; Tai Xuan Jing Symbols
            0x1D360,  // 1D360..1D37F; Counting Rod Numerals
            0x1D380,  //               unassigned
            0x1D400,  // 1D400..1D7FF; Mathematical Alphanumeric Symbols
            0x1D800,  //               unassigned
            0x1EE00,  // 1EE00..1EEFF; Arabic Mathematical Alphabetic Symbols
            0x1EF00,  //               unassigned
            0x1F000,  // 1F000..1F02F; Mahjong Tiles
            0x1F030,  // 1F030..1F09F; Domino Tiles
            0x1F0A0,  // 1F0A0..1F0FF; Playing Cards
            0x1F100,  // 1F100..1F1FF; Enclosed Alphanumeric Supplement
            0x1F200,  // 1F200..1F2FF; Enclosed Ideographic Supplement
            0x1F300,  // 1F300..1F5FF; Miscellaneous Symbols And Pictographs
            0x1F600,  // 1F600..1F64F; Emoticons
            0x1F650,  //               unassigned
            0x1F680,  // 1F680..1F6FF; Transport And Map Symbols
            0x1F700,  // 1F700..1F77F; Alchemical Symbols
            0x1F780,  //               unassigned
            0x20000,  // 20000..2A6DF; CJK Unified Ideographs Extension B
            0x2A6E0,  //               unassigned
            0x2A700,  // 2A700..2B73F; CJK Unified Ideographs Extension C
            0x2B740,  // 2B740..2B81F; CJK Unified Ideographs Extension D
            0x2B820,  //               unassigned
            0x2F800,  // 2F800..2FA1F; CJK Compatibility Ideographs Supplement
            0x2FA20,  //               unassigned
            0xE0000,  // E0000..E007F; Tags
            0xE0080,  //               unassigned
            0xE0100,  // E0100..E01EF; Variation Selectors Supplement
            0xE01F0,  //               unassigned
            0xF0000,  // F0000..FFFFF; Supplementary Private Use Area-A
            0x100000  // 100000..10FFFF; Supplementary Private Use Area-B
        };

        private static final UnicodeBlock[] blocks = {
            BASIC_LATIN,
            LATIN_1_SUPPLEMENT,
            LATIN_EXTENDED_A,
            LATIN_EXTENDED_B,
            IPA_EXTENSIONS,
            SPACING_MODIFIER_LETTERS,
            COMBINING_DIACRITICAL_MARKS,
            GREEK,
            CYRILLIC,
            CYRILLIC_SUPPLEMENTARY,
            ARMENIAN,
            HEBREW,
            ARABIC,
            SYRIAC,
            ARABIC_SUPPLEMENT,
            THAANA,
            NKO,
            SAMARITAN,
            MANDAIC,
            null,
            ARABIC_EXTENDED_A,
            DEVANAGARI,
            BENGALI,
            GURMUKHI,
            GUJARATI,
            ORIYA,
            TAMIL,
            TELUGU,
            KANNADA,
            MALAYALAM,
            SINHALA,
            THAI,
            LAO,
            TIBETAN,
            MYANMAR,
            GEORGIAN,
            HANGUL_JAMO,
            ETHIOPIC,
            ETHIOPIC_SUPPLEMENT,
            CHEROKEE,
            UNIFIED_CANADIAN_ABORIGINAL_SYLLABICS,
            OGHAM,
            RUNIC,
            TAGALOG,
            HANUNOO,
            BUHID,
            TAGBANWA,
            KHMER,
            MONGOLIAN,
            UNIFIED_CANADIAN_ABORIGINAL_SYLLABICS_EXTENDED,
            LIMBU,
            TAI_LE,
            NEW_TAI_LUE,
            KHMER_SYMBOLS,
            BUGINESE,
            TAI_THAM,
            null,
            BALINESE,
            SUNDANESE,
            BATAK,
            LEPCHA,
            OL_CHIKI,
            null,
            SUNDANESE_SUPPLEMENT,
            VEDIC_EXTENSIONS,
            PHONETIC_EXTENSIONS,
            PHONETIC_EXTENSIONS_SUPPLEMENT,
            COMBINING_DIACRITICAL_MARKS_SUPPLEMENT,
            LATIN_EXTENDED_ADDITIONAL,
            GREEK_EXTENDED,
            GENERAL_PUNCTUATION,
            SUPERSCRIPTS_AND_SUBSCRIPTS,
            CURRENCY_SYMBOLS,
            COMBINING_MARKS_FOR_SYMBOLS,
            LETTERLIKE_SYMBOLS,
            NUMBER_FORMS,
            ARROWS,
            MATHEMATICAL_OPERATORS,
            MISCELLANEOUS_TECHNICAL,
            CONTROL_PICTURES,
            OPTICAL_CHARACTER_RECOGNITION,
            ENCLOSED_ALPHANUMERICS,
            BOX_DRAWING,
            BLOCK_ELEMENTS,
            GEOMETRIC_SHAPES,
            MISCELLANEOUS_SYMBOLS,
            DINGBATS,
            MISCELLANEOUS_MATHEMATICAL_SYMBOLS_A,
            SUPPLEMENTAL_ARROWS_A,
            BRAILLE_PATTERNS,
            SUPPLEMENTAL_ARROWS_B,
            MISCELLANEOUS_MATHEMATICAL_SYMBOLS_B,
            SUPPLEMENTAL_MATHEMATICAL_OPERATORS,
            MISCELLANEOUS_SYMBOLS_AND_ARROWS,
            GLAGOLITIC,
            LATIN_EXTENDED_C,
            COPTIC,
            GEORGIAN_SUPPLEMENT,
            TIFINAGH,
            ETHIOPIC_EXTENDED,
            CYRILLIC_EXTENDED_A,
            SUPPLEMENTAL_PUNCTUATION,
            CJK_RADICALS_SUPPLEMENT,
            KANGXI_RADICALS,
            null,
            IDEOGRAPHIC_DESCRIPTION_CHARACTERS,
            CJK_SYMBOLS_AND_PUNCTUATION,
            HIRAGANA,
            KATAKANA,
            BOPOMOFO,
            HANGUL_COMPATIBILITY_JAMO,
            KANBUN,
            BOPOMOFO_EXTENDED,
            CJK_STROKES,
            KATAKANA_PHONETIC_EXTENSIONS,
            ENCLOSED_CJK_LETTERS_AND_MONTHS,
            CJK_COMPATIBILITY,
            CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A,
            YIJING_HEXAGRAM_SYMBOLS,
            CJK_UNIFIED_IDEOGRAPHS,
            YI_SYLLABLES,
            YI_RADICALS,
            LISU,
            VAI,
            CYRILLIC_EXTENDED_B,
            BAMUM,
            MODIFIER_TONE_LETTERS,
            LATIN_EXTENDED_D,
            SYLOTI_NAGRI,
            COMMON_INDIC_NUMBER_FORMS,
            PHAGS_PA,
            SAURASHTRA,
            DEVANAGARI_EXTENDED,
            KAYAH_LI,
            REJANG,
            HANGUL_JAMO_EXTENDED_A,
            JAVANESE,
            null,
            CHAM,
            MYANMAR_EXTENDED_A,
            TAI_VIET,
            MEETEI_MAYEK_EXTENSIONS,
            ETHIOPIC_EXTENDED_A,
            null,
            MEETEI_MAYEK,
            HANGUL_SYLLABLES,
            HANGUL_JAMO_EXTENDED_B,
            HIGH_SURROGATES,
            HIGH_PRIVATE_USE_SURROGATES,
            LOW_SURROGATES,
            PRIVATE_USE_AREA,
            CJK_COMPATIBILITY_IDEOGRAPHS,
            ALPHABETIC_PRESENTATION_FORMS,
            ARABIC_PRESENTATION_FORMS_A,
            VARIATION_SELECTORS,
            VERTICAL_FORMS,
            COMBINING_HALF_MARKS,
            CJK_COMPATIBILITY_FORMS,
            SMALL_FORM_VARIANTS,
            ARABIC_PRESENTATION_FORMS_B,
            HALFWIDTH_AND_FULLWIDTH_FORMS,
            SPECIALS,
            LINEAR_B_SYLLABARY,
            LINEAR_B_IDEOGRAMS,
            AEGEAN_NUMBERS,
            ANCIENT_GREEK_NUMBERS,
            ANCIENT_SYMBOLS,
            PHAISTOS_DISC,
            null,
            LYCIAN,
            CARIAN,
            null,
            OLD_ITALIC,
            GOTHIC,
            null,
            UGARITIC,
            OLD_PERSIAN,
            null,
            DESERET,
            SHAVIAN,
            OSMANYA,
            null,
            CYPRIOT_SYLLABARY,
            IMPERIAL_ARAMAIC,
            null,
            PHOENICIAN,
            LYDIAN,
            null,
            MEROITIC_HIEROGLYPHS,
            MEROITIC_CURSIVE,
            KHAROSHTHI,
            OLD_SOUTH_ARABIAN,
            null,
            AVESTAN,
            INSCRIPTIONAL_PARTHIAN,
            INSCRIPTIONAL_PAHLAVI,
            null,
            OLD_TURKIC,
            null,
            RUMI_NUMERAL_SYMBOLS,
            null,
            BRAHMI,
            KAITHI,
            SORA_SOMPENG,
            CHAKMA,
            null,
            SHARADA,
            null,
            TAKRI,
            null,
            CUNEIFORM,
            CUNEIFORM_NUMBERS_AND_PUNCTUATION,
            null,
            EGYPTIAN_HIEROGLYPHS,
            null,
            BAMUM_SUPPLEMENT,
            null,
            MIAO,
            null,
            KANA_SUPPLEMENT,
            null,
            BYZANTINE_MUSICAL_SYMBOLS,
            MUSICAL_SYMBOLS,
            ANCIENT_GREEK_MUSICAL_NOTATION,
            null,
            TAI_XUAN_JING_SYMBOLS,
            COUNTING_ROD_NUMERALS,
            null,
            MATHEMATICAL_ALPHANUMERIC_SYMBOLS,
            null,
            ARABIC_MATHEMATICAL_ALPHABETIC_SYMBOLS,
            null,
            MAHJONG_TILES,
            DOMINO_TILES,
            PLAYING_CARDS,
            ENCLOSED_ALPHANUMERIC_SUPPLEMENT,
            ENCLOSED_IDEOGRAPHIC_SUPPLEMENT,
            MISCELLANEOUS_SYMBOLS_AND_PICTOGRAPHS,
            EMOTICONS,
            null,
            TRANSPORT_AND_MAP_SYMBOLS,
            ALCHEMICAL_SYMBOLS,
            null,
            CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B,
            null,
            CJK_UNIFIED_IDEOGRAPHS_EXTENSION_C,
            CJK_UNIFIED_IDEOGRAPHS_EXTENSION_D,
            null,
            CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT,
            null,
            TAGS,
            null,
            VARIATION_SELECTORS_SUPPLEMENT,
            null,
            SUPPLEMENTARY_PRIVATE_USE_AREA_A,
            SUPPLEMENTARY_PRIVATE_USE_AREA_B
        };


        /**
         * Returns the object representing the Unicode block containing the
         * given character, or {@code null} if the character is not a
         * member of a defined block.
         *
         * <p><b>Note:</b> This method cannot handle
         * <a href="Character.html#supplementary"> supplementary
         * characters</a>.  To support all Unicode characters, including
         * supplementary characters, use the {@link #of(int)} method.
         *
         * @param   c  The character in question
         * @return  The {@code UnicodeBlock} instance representing the
         *          Unicode block of which this character is a member, or
         *          {@code null} if the character is not a member of any
         *          Unicode block
         */
        public static UnicodeBlock of(char c) {
            return of((int)c);
        }

        /**
         * Returns the object representing the Unicode block
         * containing the given character (Unicode code point), or
         * {@code null} if the character is not a member of a
         * defined block.
         *
         * @param   codePoint the character (Unicode code point) in question.
         * @return  The {@code UnicodeBlock} instance representing the
         *          Unicode block of which this character is a member, or
         *          {@code null} if the character is not a member of any
         *          Unicode block
         * @exception IllegalArgumentException if the specified
         * {@code codePoint} is an invalid Unicode code point.
         * @see Character#isValidCodePoint(int)
         * @since   1.5
         */
        public static UnicodeBlock of(int codePoint) {
            if (!isValidCodePoint(codePoint)) {
                throw new IllegalArgumentException();
            }

            int top, bottom, current;
            bottom = 0;
            top = blockStarts.length;
            current = top/2;

            // invariant: top > current >= bottom && codePoint >= unicodeBlockStarts[bottom]
            while (top - bottom > 1) {
                if (codePoint >= blockStarts[current]) {
                    bottom = current;
                } else {
                    top = current;
                }
                current = (top + bottom) / 2;
            }
            return blocks[current];
        }

        /**
         * Returns the UnicodeBlock with the given name. Block
         * names are determined by The Unicode Standard. The file
         * Blocks-&lt;version&gt;.txt defines blocks for a particular
         * version of the standard. The {@link Character} class specifies
         * the version of the standard that it supports.
         * <p>
         * This method accepts block names in the following forms:
         * <ol>
         * <li> Canonical block names as defined by the Unicode Standard.
         * For example, the standard defines a "Basic Latin" block. Therefore, this
         * method accepts "Basic Latin" as a valid block name. The documentation of
         * each UnicodeBlock provides the canonical name.
         * <li>Canonical block names with all spaces removed. For example, "BasicLatin"
         * is a valid block name for the "Basic Latin" block.
         * <li>The text representation of each constant UnicodeBlock identifier.
         * For example, this method will return the {@link #BASIC_LATIN} block if
         * provided with the "BASIC_LATIN" name. This form replaces all spaces and
         * hyphens in the canonical name with underscores.
         * </ol>
         * Finally, character case is ignored for all of the valid block name forms.
         * For example, "BASIC_LATIN" and "basic_latin" are both valid block names.
         * The en_US locale's case mapping rules are used to provide case-insensitive
         * string comparisons for block name validation.
         * <p>
         * If the Unicode Standard changes block names, both the previous and
         * current names will be accepted.
         *
         * @param blockName A {@code UnicodeBlock} name.
         * @return The {@code UnicodeBlock} instance identified
         *         by {@code blockName}
         * @throws IllegalArgumentException if {@code blockName} is an
         *         invalid name
         * @throws NullPointerException if {@code blockName} is null
         * @since 1.5
         */
        public static final UnicodeBlock forName(String blockName) {
            UnicodeBlock block = map.get(blockName.toUpperCase(Locale.US));
            if (block == null) {
                throw new IllegalArgumentException();
            }
            return block;
        }
    }


    /**
     * A family of character subsets representing the character scripts
     * defined in the <a href="http://www.unicode.org/reports/tr24/">
     * <i>Unicode Standard Annex #24: Script Names</i></a>. Every Unicode
     * character is assigned to a single Unicode script, either a specific
     * script, such as {@link Character.UnicodeScript#LATIN Latin}, or
     * one of the following three special values,
     * {@link Character.UnicodeScript#INHERITED Inherited},
     * {@link Character.UnicodeScript#COMMON Common} or
     * {@link Character.UnicodeScript#UNKNOWN Unknown}.
     *
     * @since 1.7
     */
    public static enum UnicodeScript {
        /**
         * Unicode script "Common".
         */
        COMMON,

        /**
         * Unicode script "Latin".
         */
        LATIN,

        /**
         * Unicode script "Greek".
         */
        GREEK,

        /**
         * Unicode script "Cyrillic".
         */
        CYRILLIC,

        /**
         * Unicode script "Armenian".
         */
        ARMENIAN,

        /**
         * Unicode script "Hebrew".
         */
        HEBREW,

        /**
         * Unicode script "Arabic".
         */
        ARABIC,

        /**
         * Unicode script "Syriac".
         */
        SYRIAC,

        /**
         * Unicode script "Thaana".
         */
        THAANA,

        /**
         * Unicode script "Devanagari".
         */
        DEVANAGARI,

        /**
         * Unicode script "Bengali".
         */
        BENGALI,

        /**
         * Unicode script "Gurmukhi".
         */
        GURMUKHI,

        /**
         * Unicode script "Gujarati".
         */
        GUJARATI,

        /**
         * Unicode script "Oriya".
         */
        ORIYA,

        /**
         * Unicode script "Tamil".
         */
        TAMIL,

        /**
         * Unicode script "Telugu".
         */
        TELUGU,

        /**
         * Unicode script "Kannada".
         */
        KANNADA,

        /**
         * Unicode script "Malayalam".
         */
        MALAYALAM,

        /**
         * Unicode script "Sinhala".
         */
        SINHALA,

        /**
         * Unicode script "Thai".
         */
        THAI,

        /**
         * Unicode script "Lao".
         */
        LAO,

        /**
         * Unicode script "Tibetan".
         */
        TIBETAN,

        /**
         * Unicode script "Myanmar".
         */
        MYANMAR,

        /**
         * Unicode script "Georgian".
         */
        GEORGIAN,

        /**
         * Unicode script "Hangul".
         */
        HANGUL,

        /**
         * Unicode script "Ethiopic".
         */
        ETHIOPIC,

        /**
         * Unicode script "Cherokee".
         */
        CHEROKEE,

        /**
         * Unicode script "Canadian_Aboriginal".
         */
        CANADIAN_ABORIGINAL,

        /**
         * Unicode script "Ogham".
         */
        OGHAM,

        /**
         * Unicode script "Runic".
         */
        RUNIC,

        /**
         * Unicode script "Khmer".
         */
        KHMER,

        /**
         * Unicode script "Mongolian".
         */
        MONGOLIAN,

        /**
         * Unicode script "Hiragana".
         */
        HIRAGANA,

        /**
         * Unicode script "Katakana".
         */
        KATAKANA,

        /**
         * Unicode script "Bopomofo".
         */
        BOPOMOFO,

        /**
         * Unicode script "Han".
         */
        HAN,

        /**
         * Unicode script "Yi".
         */
        YI,

        /**
         * Unicode script "Old_Italic".
         */
        OLD_ITALIC,

        /**
         * Unicode script "Gothic".
         */
        GOTHIC,

        /**
         * Unicode script "Deseret".
         */
        DESERET,

        /**
         * Unicode script "Inherited".
         */
        INHERITED,

        /**
         * Unicode script "Tagalog".
         */
        TAGALOG,

        /**
         * Unicode script "Hanunoo".
         */
        HANUNOO,

        /**
         * Unicode script "Buhid".
         */
        BUHID,

        /**
         * Unicode script "Tagbanwa".
         */
        TAGBANWA,

        /**
         * Unicode script "Limbu".
         */
        LIMBU,

        /**
         * Unicode script "Tai_Le".
         */
        TAI_LE,

        /**
         * Unicode script "Linear_B".
         */
        LINEAR_B,

        /**
         * Unicode script "Ugaritic".
         */
        UGARITIC,

        /**
         * Unicode script "Shavian".
         */
        SHAVIAN,

        /**
         * Unicode script "Osmanya".
         */
        OSMANYA,

        /**
         * Unicode script "Cypriot".
         */
        CYPRIOT,

        /**
         * Unicode script "Braille".
         */
        BRAILLE,

        /**
         * Unicode script "Buginese".
         */
        BUGINESE,

        /**
         * Unicode script "Coptic".
         */
        COPTIC,

        /**
         * Unicode script "New_Tai_Lue".
         */
        NEW_TAI_LUE,

        /**
         * Unicode script "Glagolitic".
         */
        GLAGOLITIC,

        /**
         * Unicode script "Tifinagh".
         */
        TIFINAGH,

        /**
         * Unicode script "Syloti_Nagri".
         */
        SYLOTI_NAGRI,

        /**
         * Unicode script "Old_Persian".
         */
        OLD_PERSIAN,

        /**
         * Unicode script "Kharoshthi".
         */
        KHAROSHTHI,

        /**
         * Unicode script "Balinese".
         */
        BALINESE,

        /**
         * Unicode script "Cuneiform".
         */
        CUNEIFORM,

        /**
         * Unicode script "Phoenician".
         */
        PHOENICIAN,

        /**
         * Unicode script "Phags_Pa".
         */
        PHAGS_PA,

        /**
         * Unicode script "Nko".
         */
        NKO,

        /**
         * Unicode script "Sundanese".
         */
        SUNDANESE,

        /**
         * Unicode script "Batak".
         */
        BATAK,

        /**
         * Unicode script "Lepcha".
         */
        LEPCHA,

        /**
         * Unicode script "Ol_Chiki".
         */
        OL_CHIKI,

        /**
         * Unicode script "Vai".
         */
        VAI,

        /**
         * Unicode script "Saurashtra".
         */
        SAURASHTRA,

        /**
         * Unicode script "Kayah_Li".
         */
        KAYAH_LI,

        /**
         * Unicode script "Rejang".
         */
        REJANG,

        /**
         * Unicode script "Lycian".
         */
        LYCIAN,

        /**
         * Unicode script "Carian".
         */
        CARIAN,

        /**
         * Unicode script "Lydian".
         */
        LYDIAN,

        /**
         * Unicode script "Cham".
         */
        CHAM,

        /**
         * Unicode script "Tai_Tham".
         */
        TAI_THAM,

        /**
         * Unicode script "Tai_Viet".
         */
        TAI_VIET,

        /**
         * Unicode script "Avestan".
         */
        AVESTAN,

        /**
         * Unicode script "Egyptian_Hieroglyphs".
         */
        EGYPTIAN_HIEROGLYPHS,

        /**
         * Unicode script "Samaritan".
         */
        SAMARITAN,

        /**
         * Unicode script "Mandaic".
         */
        MANDAIC,

        /**
         * Unicode script "Lisu".
         */
        LISU,

        /**
         * Unicode script "Bamum".
         */
        BAMUM,

        /**
         * Unicode script "Javanese".
         */
        JAVANESE,

        /**
         * Unicode script "Meetei_Mayek".
         */
        MEETEI_MAYEK,

        /**
         * Unicode script "Imperial_Aramaic".
         */
        IMPERIAL_ARAMAIC,

        /**
         * Unicode script "Old_South_Arabian".
         */
        OLD_SOUTH_ARABIAN,

        /**
         * Unicode script "Inscriptional_Parthian".
         */
        INSCRIPTIONAL_PARTHIAN,

        /**
         * Unicode script "Inscriptional_Pahlavi".
         */
        INSCRIPTIONAL_PAHLAVI,

        /**
         * Unicode script "Old_Turkic".
         */
        OLD_TURKIC,

        /**
         * Unicode script "Brahmi".
         */
        BRAHMI,

        /**
         * Unicode script "Kaithi".
         */
        KAITHI,

        /**
         * Unicode script "Meroitic Hieroglyphs".
         */
        MEROITIC_HIEROGLYPHS,

        /**
         * Unicode script "Meroitic Cursive".
         */
        MEROITIC_CURSIVE,

        /**
         * Unicode script "Sora Sompeng".
         */
        SORA_SOMPENG,

        /**
         * Unicode script "Chakma".
         */
        CHAKMA,

        /**
         * Unicode script "Sharada".
         */
        SHARADA,

        /**
         * Unicode script "Takri".
         */
        TAKRI,

        /**
         * Unicode script "Miao".
         */
        MIAO,

        /**
         * Unicode script "Unknown".
         */
        UNKNOWN;

        private static final int[] scriptStarts = {
            0x0000,   // 0000..0040; COMMON
            0x0041,   // 0041..005A; LATIN
            0x005B,   // 005B..0060; COMMON
            0x0061,   // 0061..007A; LATIN
            0x007B,   // 007B..00A9; COMMON
            0x00AA,   // 00AA..00AA; LATIN
            0x00AB,   // 00AB..00B9; COMMON
            0x00BA,   // 00BA..00BA; LATIN
            0x00BB,   // 00BB..00BF; COMMON
            0x00C0,   // 00C0..00D6; LATIN
            0x00D7,   // 00D7..00D7; COMMON
            0x00D8,   // 00D8..00F6; LATIN
            0x00F7,   // 00F7..00F7; COMMON
            0x00F8,   // 00F8..02B8; LATIN
            0x02B9,   // 02B9..02DF; COMMON
            0x02E0,   // 02E0..02E4; LATIN
            0x02E5,   // 02E5..02E9; COMMON
            0x02EA,   // 02EA..02EB; BOPOMOFO
            0x02EC,   // 02EC..02FF; COMMON
            0x0300,   // 0300..036F; INHERITED
            0x0370,   // 0370..0373; GREEK
            0x0374,   // 0374..0374; COMMON
            0x0375,   // 0375..037D; GREEK
            0x037E,   // 037E..0383; COMMON
            0x0384,   // 0384..0384; GREEK
            0x0385,   // 0385..0385; COMMON
            0x0386,   // 0386..0386; GREEK
            0x0387,   // 0387..0387; COMMON
            0x0388,   // 0388..03E1; GREEK
            0x03E2,   // 03E2..03EF; COPTIC
            0x03F0,   // 03F0..03FF; GREEK
            0x0400,   // 0400..0484; CYRILLIC
            0x0485,   // 0485..0486; INHERITED
            0x0487,   // 0487..0530; CYRILLIC
            0x0531,   // 0531..0588; ARMENIAN
            0x0589,   // 0589..0589; COMMON
            0x058A,   // 058A..0590; ARMENIAN
            0x0591,   // 0591..05FF; HEBREW
            0x0600,   // 0600..060B; ARABIC
            0x060C,   // 060C..060C; COMMON
            0x060D,   // 060D..061A; ARABIC
            0x061B,   // 061B..061D; COMMON
            0x061E,   // 061E..061E; ARABIC
            0x061F,   // 061F..061F; COMMON
            0x0620,   // 0620..063F; ARABIC
            0x0640,   // 0640..0640; COMMON
            0x0641,   // 0641..064A; ARABIC
            0x064B,   // 064B..0655; INHERITED
            0x0656,   // 0656..065F; ARABIC
            0x0660,   // 0660..0669; COMMON
            0x066A,   // 066A..066F; ARABIC
            0x0670,   // 0670..0670; INHERITED
            0x0671,   // 0671..06DC; ARABIC
            0x06DD,   // 06DD..06DD; COMMON
            0x06DE,   // 06DE..06FF; ARABIC
            0x0700,   // 0700..074F; SYRIAC
            0x0750,   // 0750..077F; ARABIC
            0x0780,   // 0780..07BF; THAANA
            0x07C0,   // 07C0..07FF; NKO
            0x0800,   // 0800..083F; SAMARITAN
            0x0840,   // 0840..089F; MANDAIC
            0x08A0,   // 08A0..08FF; ARABIC
            0x0900,   // 0900..0950; DEVANAGARI
            0x0951,   // 0951..0952; INHERITED
            0x0953,   // 0953..0963; DEVANAGARI
            0x0964,   // 0964..0965; COMMON
            0x0966,   // 0966..0980; DEVANAGARI
            0x0981,   // 0981..0A00; BENGALI
            0x0A01,   // 0A01..0A80; GURMUKHI
            0x0A81,   // 0A81..0B00; GUJARATI
            0x0B01,   // 0B01..0B81; ORIYA
            0x0B82,   // 0B82..0C00; TAMIL
            0x0C01,   // 0C01..0C81; TELUGU
            0x0C82,   // 0C82..0CF0; KANNADA
            0x0D02,   // 0D02..0D81; MALAYALAM
            0x0D82,   // 0D82..0E00; SINHALA
            0x0E01,   // 0E01..0E3E; THAI
            0x0E3F,   // 0E3F..0E3F; COMMON
            0x0E40,   // 0E40..0E80; THAI
            0x0E81,   // 0E81..0EFF; LAO
            0x0F00,   // 0F00..0FD4; TIBETAN
            0x0FD5,   // 0FD5..0FD8; COMMON
            0x0FD9,   // 0FD9..0FFF; TIBETAN
            0x1000,   // 1000..109F; MYANMAR
            0x10A0,   // 10A0..10FA; GEORGIAN
            0x10FB,   // 10FB..10FB; COMMON
            0x10FC,   // 10FC..10FF; GEORGIAN
            0x1100,   // 1100..11FF; HANGUL
            0x1200,   // 1200..139F; ETHIOPIC
            0x13A0,   // 13A0..13FF; CHEROKEE
            0x1400,   // 1400..167F; CANADIAN_ABORIGINAL
            0x1680,   // 1680..169F; OGHAM
            0x16A0,   // 16A0..16EA; RUNIC
            0x16EB,   // 16EB..16ED; COMMON
            0x16EE,   // 16EE..16FF; RUNIC
            0x1700,   // 1700..171F; TAGALOG
            0x1720,   // 1720..1734; HANUNOO
            0x1735,   // 1735..173F; COMMON
            0x1740,   // 1740..175F; BUHID
            0x1760,   // 1760..177F; TAGBANWA
            0x1780,   // 1780..17FF; KHMER
            0x1800,   // 1800..1801; MONGOLIAN
            0x1802,   // 1802..1803; COMMON
            0x1804,   // 1804..1804; MONGOLIAN
            0x1805,   // 1805..1805; COMMON
            0x1806,   // 1806..18AF; MONGOLIAN
            0x18B0,   // 18B0..18FF; CANADIAN_ABORIGINAL
            0x1900,   // 1900..194F; LIMBU
            0x1950,   // 1950..197F; TAI_LE
            0x1980,   // 1980..19DF; NEW_TAI_LUE
            0x19E0,   // 19E0..19FF; KHMER
            0x1A00,   // 1A00..1A1F; BUGINESE
            0x1A20,   // 1A20..1AFF; TAI_THAM
            0x1B00,   // 1B00..1B7F; BALINESE
            0x1B80,   // 1B80..1BBF; SUNDANESE
            0x1BC0,   // 1BC0..1BFF; BATAK
            0x1C00,   // 1C00..1C4F; LEPCHA
            0x1C50,   // 1C50..1CBF; OL_CHIKI
            0x1CC0,   // 1CC0..1CCF; SUNDANESE
            0x1CD0,   // 1CD0..1CD2; INHERITED
            0x1CD3,   // 1CD3..1CD3; COMMON
            0x1CD4,   // 1CD4..1CE0; INHERITED
            0x1CE1,   // 1CE1..1CE1; COMMON
            0x1CE2,   // 1CE2..1CE8; INHERITED
            0x1CE9,   // 1CE9..1CEC; COMMON
            0x1CED,   // 1CED..1CED; INHERITED
            0x1CEE,   // 1CEE..1CF3; COMMON
            0x1CF4,   // 1CF4..1CF4; INHERITED
            0x1CF5,   // 1CF5..1CFF; COMMON
            0x1D00,   // 1D00..1D25; LATIN
            0x1D26,   // 1D26..1D2A; GREEK
            0x1D2B,   // 1D2B..1D2B; CYRILLIC
            0x1D2C,   // 1D2C..1D5C; LATIN
            0x1D5D,   // 1D5D..1D61; GREEK
            0x1D62,   // 1D62..1D65; LATIN
            0x1D66,   // 1D66..1D6A; GREEK
            0x1D6B,   // 1D6B..1D77; LATIN
            0x1D78,   // 1D78..1D78; CYRILLIC
            0x1D79,   // 1D79..1DBE; LATIN
            0x1DBF,   // 1DBF..1DBF; GREEK
            0x1DC0,   // 1DC0..1DFF; INHERITED
            0x1E00,   // 1E00..1EFF; LATIN
            0x1F00,   // 1F00..1FFF; GREEK
            0x2000,   // 2000..200B; COMMON
            0x200C,   // 200C..200D; INHERITED
            0x200E,   // 200E..2070; COMMON
            0x2071,   // 2071..2073; LATIN
            0x2074,   // 2074..207E; COMMON
            0x207F,   // 207F..207F; LATIN
            0x2080,   // 2080..208F; COMMON
            0x2090,   // 2090..209F; LATIN
            0x20A0,   // 20A0..20CF; COMMON
            0x20D0,   // 20D0..20FF; INHERITED
            0x2100,   // 2100..2125; COMMON
            0x2126,   // 2126..2126; GREEK
            0x2127,   // 2127..2129; COMMON
            0x212A,   // 212A..212B; LATIN
            0x212C,   // 212C..2131; COMMON
            0x2132,   // 2132..2132; LATIN
            0x2133,   // 2133..214D; COMMON
            0x214E,   // 214E..214E; LATIN
            0x214F,   // 214F..215F; COMMON
            0x2160,   // 2160..2188; LATIN
            0x2189,   // 2189..27FF; COMMON
            0x2800,   // 2800..28FF; BRAILLE
            0x2900,   // 2900..2BFF; COMMON
            0x2C00,   // 2C00..2C5F; GLAGOLITIC
            0x2C60,   // 2C60..2C7F; LATIN
            0x2C80,   // 2C80..2CFF; COPTIC
            0x2D00,   // 2D00..2D2F; GEORGIAN
            0x2D30,   // 2D30..2D7F; TIFINAGH
            0x2D80,   // 2D80..2DDF; ETHIOPIC
            0x2DE0,   // 2DE0..2DFF; CYRILLIC
            0x2E00,   // 2E00..2E7F; COMMON
            0x2E80,   // 2E80..2FEF; HAN
            0x2FF0,   // 2FF0..3004; COMMON
            0x3005,   // 3005..3005; HAN
            0x3006,   // 3006..3006; COMMON
            0x3007,   // 3007..3007; HAN
            0x3008,   // 3008..3020; COMMON
            0x3021,   // 3021..3029; HAN
            0x302A,   // 302A..302D; INHERITED
            0x302E,   // 302E..302F; HANGUL
            0x3030,   // 3030..3037; COMMON
            0x3038,   // 3038..303B; HAN
            0x303C,   // 303C..3040; COMMON
            0x3041,   // 3041..3098; HIRAGANA
            0x3099,   // 3099..309A; INHERITED
            0x309B,   // 309B..309C; COMMON
            0x309D,   // 309D..309F; HIRAGANA
            0x30A0,   // 30A0..30A0; COMMON
            0x30A1,   // 30A1..30FA; KATAKANA
            0x30FB,   // 30FB..30FC; COMMON
            0x30FD,   // 30FD..3104; KATAKANA
            0x3105,   // 3105..3130; BOPOMOFO
            0x3131,   // 3131..318F; HANGUL
            0x3190,   // 3190..319F; COMMON
            0x31A0,   // 31A0..31BF; BOPOMOFO
            0x31C0,   // 31C0..31EF; COMMON
            0x31F0,   // 31F0..31FF; KATAKANA
            0x3200,   // 3200..321F; HANGUL
            0x3220,   // 3220..325F; COMMON
            0x3260,   // 3260..327E; HANGUL
            0x327F,   // 327F..32CF; COMMON
            0x32D0,   // 32D0..3357; KATAKANA
            0x3358,   // 3358..33FF; COMMON
            0x3400,   // 3400..4DBF; HAN
            0x4DC0,   // 4DC0..4DFF; COMMON
            0x4E00,   // 4E00..9FFF; HAN
            0xA000,   // A000..A4CF; YI
            0xA4D0,   // A4D0..A4FF; LISU
            0xA500,   // A500..A63F; VAI
            0xA640,   // A640..A69F; CYRILLIC
            0xA6A0,   // A6A0..A6FF; BAMUM
            0xA700,   // A700..A721; COMMON
            0xA722,   // A722..A787; LATIN
            0xA788,   // A788..A78A; COMMON
            0xA78B,   // A78B..A7FF; LATIN
            0xA800,   // A800..A82F; SYLOTI_NAGRI
            0xA830,   // A830..A83F; COMMON
            0xA840,   // A840..A87F; PHAGS_PA
            0xA880,   // A880..A8DF; SAURASHTRA
            0xA8E0,   // A8E0..A8FF; DEVANAGARI
            0xA900,   // A900..A92F; KAYAH_LI
            0xA930,   // A930..A95F; REJANG
            0xA960,   // A960..A97F; HANGUL
            0xA980,   // A980..A9FF; JAVANESE
            0xAA00,   // AA00..AA5F; CHAM
            0xAA60,   // AA60..AA7F; MYANMAR
            0xAA80,   // AA80..AADF; TAI_VIET
            0xAAE0,   // AAE0..AB00; MEETEI_MAYEK
            0xAB01,   // AB01..ABBF; ETHIOPIC
            0xABC0,   // ABC0..ABFF; MEETEI_MAYEK
            0xAC00,   // AC00..D7FB; HANGUL
            0xD7FC,   // D7FC..F8FF; UNKNOWN
            0xF900,   // F900..FAFF; HAN
            0xFB00,   // FB00..FB12; LATIN
            0xFB13,   // FB13..FB1C; ARMENIAN
            0xFB1D,   // FB1D..FB4F; HEBREW
            0xFB50,   // FB50..FD3D; ARABIC
            0xFD3E,   // FD3E..FD4F; COMMON
            0xFD50,   // FD50..FDFC; ARABIC
            0xFDFD,   // FDFD..FDFF; COMMON
            0xFE00,   // FE00..FE0F; INHERITED
            0xFE10,   // FE10..FE1F; COMMON
            0xFE20,   // FE20..FE2F; INHERITED
            0xFE30,   // FE30..FE6F; COMMON
            0xFE70,   // FE70..FEFE; ARABIC
            0xFEFF,   // FEFF..FF20; COMMON
            0xFF21,   // FF21..FF3A; LATIN
            0xFF3B,   // FF3B..FF40; COMMON
            0xFF41,   // FF41..FF5A; LATIN
            0xFF5B,   // FF5B..FF65; COMMON
            0xFF66,   // FF66..FF6F; KATAKANA
            0xFF70,   // FF70..FF70; COMMON
            0xFF71,   // FF71..FF9D; KATAKANA
            0xFF9E,   // FF9E..FF9F; COMMON
            0xFFA0,   // FFA0..FFDF; HANGUL
            0xFFE0,   // FFE0..FFFF; COMMON
            0x10000,  // 10000..100FF; LINEAR_B
            0x10100,  // 10100..1013F; COMMON
            0x10140,  // 10140..1018F; GREEK
            0x10190,  // 10190..101FC; COMMON
            0x101FD,  // 101FD..1027F; INHERITED
            0x10280,  // 10280..1029F; LYCIAN
            0x102A0,  // 102A0..102FF; CARIAN
            0x10300,  // 10300..1032F; OLD_ITALIC
            0x10330,  // 10330..1037F; GOTHIC
            0x10380,  // 10380..1039F; UGARITIC
            0x103A0,  // 103A0..103FF; OLD_PERSIAN
            0x10400,  // 10400..1044F; DESERET
            0x10450,  // 10450..1047F; SHAVIAN
            0x10480,  // 10480..107FF; OSMANYA
            0x10800,  // 10800..1083F; CYPRIOT
            0x10840,  // 10840..108FF; IMPERIAL_ARAMAIC
            0x10900,  // 10900..1091F; PHOENICIAN
            0x10920,  // 10920..1097F; LYDIAN
            0x10980,  // 10980..1099F; MEROITIC_HIEROGLYPHS
            0x109A0,  // 109A0..109FF; MEROITIC_CURSIVE
            0x10A00,  // 10A00..10A5F; KHAROSHTHI
            0x10A60,  // 10A60..10AFF; OLD_SOUTH_ARABIAN
            0x10B00,  // 10B00..10B3F; AVESTAN
            0x10B40,  // 10B40..10B5F; INSCRIPTIONAL_PARTHIAN
            0x10B60,  // 10B60..10BFF; INSCRIPTIONAL_PAHLAVI
            0x10C00,  // 10C00..10E5F; OLD_TURKIC
            0x10E60,  // 10E60..10FFF; ARABIC
            0x11000,  // 11000..1107F; BRAHMI
            0x11080,  // 11080..110CF; KAITHI
            0x110D0,  // 110D0..110FF; SORA_SOMPENG
            0x11100,  // 11100..1117F; CHAKMA
            0x11180,  // 11180..1167F; SHARADA
            0x11680,  // 11680..116CF; TAKRI
            0x12000,  // 12000..12FFF; CUNEIFORM
            0x13000,  // 13000..167FF; EGYPTIAN_HIEROGLYPHS
            0x16800,  // 16800..16A38; BAMUM
            0x16F00,  // 16F00..16F9F; MIAO
            0x1B000,  // 1B000..1B000; KATAKANA
            0x1B001,  // 1B001..1CFFF; HIRAGANA
            0x1D000,  // 1D000..1D166; COMMON
            0x1D167,  // 1D167..1D169; INHERITED
            0x1D16A,  // 1D16A..1D17A; COMMON
            0x1D17B,  // 1D17B..1D182; INHERITED
            0x1D183,  // 1D183..1D184; COMMON
            0x1D185,  // 1D185..1D18B; INHERITED
            0x1D18C,  // 1D18C..1D1A9; COMMON
            0x1D1AA,  // 1D1AA..1D1AD; INHERITED
            0x1D1AE,  // 1D1AE..1D1FF; COMMON
            0x1D200,  // 1D200..1D2FF; GREEK
            0x1D300,  // 1D300..1EDFF; COMMON
            0x1EE00,  // 1EE00..1EFFF; ARABIC
            0x1F000,  // 1F000..1F1FF; COMMON
            0x1F200,  // 1F200..1F200; HIRAGANA
            0x1F201,  // 1F210..1FFFF; COMMON
            0x20000,  // 20000..E0000; HAN
            0xE0001,  // E0001..E00FF; COMMON
            0xE0100,  // E0100..E01EF; INHERITED
            0xE01F0   // E01F0..10FFFF; UNKNOWN

        };

        private static final UnicodeScript[] scripts = {
            COMMON,
            LATIN,
            COMMON,
            LATIN,
            COMMON,
            LATIN,
            COMMON,
            LATIN,
            COMMON,
            LATIN,
            COMMON,
            LATIN,
            COMMON,
            LATIN,
            COMMON,
            LATIN,
            COMMON,
            BOPOMOFO,
            COMMON,
            INHERITED,
            GREEK,
            COMMON,
            GREEK,
            COMMON,
            GREEK,
            COMMON,
            GREEK,
            COMMON,
            GREEK,
            COPTIC,
            GREEK,
            CYRILLIC,
            INHERITED,
            CYRILLIC,
            ARMENIAN,
            COMMON,
            ARMENIAN,
            HEBREW,
            ARABIC,
            COMMON,
            ARABIC,
            COMMON,
            ARABIC,
            COMMON,
            ARABIC,
            COMMON,
            ARABIC,
            INHERITED,
            ARABIC,
            COMMON,
            ARABIC,
            INHERITED,
            ARABIC,
            COMMON,
            ARABIC,
            SYRIAC,
            ARABIC,
            THAANA,
            NKO,
            SAMARITAN,
            MANDAIC,
            ARABIC,
            DEVANAGARI,
            INHERITED,
            DEVANAGARI,
            COMMON,
            DEVANAGARI,
            BENGALI,
            GURMUKHI,
            GUJARATI,
            ORIYA,
            TAMIL,
            TELUGU,
            KANNADA,
            MALAYALAM,
            SINHALA,
            THAI,
            COMMON,
            THAI,
            LAO,
            TIBETAN,
            COMMON,
            TIBETAN,
            MYANMAR,
            GEORGIAN,
            COMMON,
            GEORGIAN,
            HANGUL,
            ETHIOPIC,
            CHEROKEE,
            CANADIAN_ABORIGINAL,
            OGHAM,
            RUNIC,
            COMMON,
            RUNIC,
            TAGALOG,
            HANUNOO,
            COMMON,
            BUHID,
            TAGBANWA,
            KHMER,
            MONGOLIAN,
            COMMON,
            MONGOLIAN,
            COMMON,
            MONGOLIAN,
            CANADIAN_ABORIGINAL,
            LIMBU,
            TAI_LE,
            NEW_TAI_LUE,
            KHMER,
            BUGINESE,
            TAI_THAM,
            BALINESE,
            SUNDANESE,
            BATAK,
            LEPCHA,
            OL_CHIKI,
            SUNDANESE,
            INHERITED,
            COMMON,
            INHERITED,
            COMMON,
            INHERITED,
            COMMON,
            INHERITED,
            COMMON,
            INHERITED,
            COMMON,
            LATIN,
            GREEK,
            CYRILLIC,
            LATIN,
            GREEK,
            LATIN,
            GREEK,
            LATIN,
            CYRILLIC,
            LATIN,
            GREEK,
            INHERITED,
            LATIN,
            GREEK,
            COMMON,
            INHERITED,
            COMMON,
            LATIN,
            COMMON,
            LATIN,
            COMMON,
            LATIN,
            COMMON,
            INHERITED,
            COMMON,
            GREEK,
            COMMON,
            LATIN,
            COMMON,
            LATIN,
            COMMON,
            LATIN,
            COMMON,
            LATIN,
            COMMON,
            BRAILLE,
            COMMON,
            GLAGOLITIC,
            LATIN,
            COPTIC,
            GEORGIAN,
            TIFINAGH,
            ETHIOPIC,
            CYRILLIC,
            COMMON,
            HAN,
            COMMON,
            HAN,
            COMMON,
            HAN,
            COMMON,
            HAN,
            INHERITED,
            HANGUL,
            COMMON,
            HAN,
            COMMON,
            HIRAGANA,
            INHERITED,
            COMMON,
            HIRAGANA,
            COMMON,
            KATAKANA,
            COMMON,
            KATAKANA,
            BOPOMOFO,
            HANGUL,
            COMMON,
            BOPOMOFO,
            COMMON,
            KATAKANA,
            HANGUL,
            COMMON,
            HANGUL,
            COMMON,
            KATAKANA,
            COMMON,
            HAN,
            COMMON,
            HAN,
            YI,
            LISU,
            VAI,
            CYRILLIC,
            BAMUM,
            COMMON,
            LATIN,
            COMMON,
            LATIN,
            SYLOTI_NAGRI,
            COMMON,
            PHAGS_PA,
            SAURASHTRA,
            DEVANAGARI,
            KAYAH_LI,
            REJANG,
            HANGUL,
            JAVANESE,
            CHAM,
            MYANMAR,
            TAI_VIET,
            MEETEI_MAYEK,
            ETHIOPIC,
            MEETEI_MAYEK,
            HANGUL,
            UNKNOWN     ,
            HAN,
            LATIN,
            ARMENIAN,
            HEBREW,
            ARABIC,
            COMMON,
            ARABIC,
            COMMON,
            INHERITED,
            COMMON,
            INHERITED,
            COMMON,
            ARABIC,
            COMMON,
            LATIN,
            COMMON,
            LATIN,
            COMMON,
            KATAKANA,
            COMMON,
            KATAKANA,
            COMMON,
            HANGUL,
            COMMON,
            LINEAR_B,
            COMMON,
            GREEK,
            COMMON,
            INHERITED,
            LYCIAN,
            CARIAN,
            OLD_ITALIC,
            GOTHIC,
            UGARITIC,
            OLD_PERSIAN,
            DESERET,
            SHAVIAN,
            OSMANYA,
            CYPRIOT,
            IMPERIAL_ARAMAIC,
            PHOENICIAN,
            LYDIAN,
            MEROITIC_HIEROGLYPHS,
            MEROITIC_CURSIVE,
            KHAROSHTHI,
            OLD_SOUTH_ARABIAN,
            AVESTAN,
            INSCRIPTIONAL_PARTHIAN,
            INSCRIPTIONAL_PAHLAVI,
            OLD_TURKIC,
            ARABIC,
            BRAHMI,
            KAITHI,
            SORA_SOMPENG,
            CHAKMA,
            SHARADA,
            TAKRI,
            CUNEIFORM,
            EGYPTIAN_HIEROGLYPHS,
            BAMUM,
            MIAO,
            KATAKANA,
            HIRAGANA,
            COMMON,
            INHERITED,
            COMMON,
            INHERITED,
            COMMON,
            INHERITED,
            COMMON,
            INHERITED,
            COMMON,
            GREEK,
            COMMON,
            ARABIC,
            COMMON,
            HIRAGANA,
            COMMON,
            HAN,
            COMMON,
            INHERITED,
            UNKNOWN
        };

        private static HashMap<String, Character.UnicodeScript> aliases;
        static {
            aliases = new HashMap<>(128);
            aliases.put("ARAB", ARABIC);
            aliases.put("ARMI", IMPERIAL_ARAMAIC);
            aliases.put("ARMN", ARMENIAN);
            aliases.put("AVST", AVESTAN);
            aliases.put("BALI", BALINESE);
            aliases.put("BAMU", BAMUM);
            aliases.put("BATK", BATAK);
            aliases.put("BENG", BENGALI);
            aliases.put("BOPO", BOPOMOFO);
            aliases.put("BRAI", BRAILLE);
            aliases.put("BRAH", BRAHMI);
            aliases.put("BUGI", BUGINESE);
            aliases.put("BUHD", BUHID);
            aliases.put("CAKM", CHAKMA);
            aliases.put("CANS", CANADIAN_ABORIGINAL);
            aliases.put("CARI", CARIAN);
            aliases.put("CHAM", CHAM);
            aliases.put("CHER", CHEROKEE);
            aliases.put("COPT", COPTIC);
            aliases.put("CPRT", CYPRIOT);
            aliases.put("CYRL", CYRILLIC);
            aliases.put("DEVA", DEVANAGARI);
            aliases.put("DSRT", DESERET);
            aliases.put("EGYP", EGYPTIAN_HIEROGLYPHS);
            aliases.put("ETHI", ETHIOPIC);
            aliases.put("GEOR", GEORGIAN);
            aliases.put("GLAG", GLAGOLITIC);
            aliases.put("GOTH", GOTHIC);
            aliases.put("GREK", GREEK);
            aliases.put("GUJR", GUJARATI);
            aliases.put("GURU", GURMUKHI);
            aliases.put("HANG", HANGUL);
            aliases.put("HANI", HAN);
            aliases.put("HANO", HANUNOO);
            aliases.put("HEBR", HEBREW);
            aliases.put("HIRA", HIRAGANA);
            // it appears we don't have the KATAKANA_OR_HIRAGANA
            //aliases.put("HRKT", KATAKANA_OR_HIRAGANA);
            aliases.put("ITAL", OLD_ITALIC);
            aliases.put("JAVA", JAVANESE);
            aliases.put("KALI", KAYAH_LI);
            aliases.put("KANA", KATAKANA);
            aliases.put("KHAR", KHAROSHTHI);
            aliases.put("KHMR", KHMER);
            aliases.put("KNDA", KANNADA);
            aliases.put("KTHI", KAITHI);
            aliases.put("LANA", TAI_THAM);
            aliases.put("LAOO", LAO);
            aliases.put("LATN", LATIN);
            aliases.put("LEPC", LEPCHA);
            aliases.put("LIMB", LIMBU);
            aliases.put("LINB", LINEAR_B);
            aliases.put("LISU", LISU);
            aliases.put("LYCI", LYCIAN);
            aliases.put("LYDI", LYDIAN);
            aliases.put("MAND", MANDAIC);
            aliases.put("MERC", MEROITIC_CURSIVE);
            aliases.put("MERO", MEROITIC_HIEROGLYPHS);
            aliases.put("MLYM", MALAYALAM);
            aliases.put("MONG", MONGOLIAN);
            aliases.put("MTEI", MEETEI_MAYEK);
            aliases.put("MYMR", MYANMAR);
            aliases.put("NKOO", NKO);
            aliases.put("OGAM", OGHAM);
            aliases.put("OLCK", OL_CHIKI);
            aliases.put("ORKH", OLD_TURKIC);
            aliases.put("ORYA", ORIYA);
            aliases.put("OSMA", OSMANYA);
            aliases.put("PHAG", PHAGS_PA);
            aliases.put("PLRD", MIAO);
            aliases.put("PHLI", INSCRIPTIONAL_PAHLAVI);
            aliases.put("PHNX", PHOENICIAN);
            aliases.put("PRTI", INSCRIPTIONAL_PARTHIAN);
            aliases.put("RJNG", REJANG);
            aliases.put("RUNR", RUNIC);
            aliases.put("SAMR", SAMARITAN);
            aliases.put("SARB", OLD_SOUTH_ARABIAN);
            aliases.put("SAUR", SAURASHTRA);
            aliases.put("SHAW", SHAVIAN);
            aliases.put("SHRD", SHARADA);
            aliases.put("SINH", SINHALA);
            aliases.put("SORA", SORA_SOMPENG);
            aliases.put("SUND", SUNDANESE);
            aliases.put("SYLO", SYLOTI_NAGRI);
            aliases.put("SYRC", SYRIAC);
            aliases.put("TAGB", TAGBANWA);
            aliases.put("TALE", TAI_LE);
            aliases.put("TAKR", TAKRI);
            aliases.put("TALU", NEW_TAI_LUE);
            aliases.put("TAML", TAMIL);
            aliases.put("TAVT", TAI_VIET);
            aliases.put("TELU", TELUGU);
            aliases.put("TFNG", TIFINAGH);
            aliases.put("TGLG", TAGALOG);
            aliases.put("THAA", THAANA);
            aliases.put("THAI", THAI);
            aliases.put("TIBT", TIBETAN);
            aliases.put("UGAR", UGARITIC);
            aliases.put("VAII", VAI);
            aliases.put("XPEO", OLD_PERSIAN);
            aliases.put("XSUX", CUNEIFORM);
            aliases.put("YIII", YI);
            aliases.put("ZINH", INHERITED);
            aliases.put("ZYYY", COMMON);
            aliases.put("ZZZZ", UNKNOWN);
        }

        /**
         * Returns the enum constant representing the Unicode script of which
         * the given character (Unicode code point) is assigned to.
         *
         * @param   codePoint the character (Unicode code point) in question.
         * @return  The {@code UnicodeScript} constant representing the
         *          Unicode script of which this character is assigned to.
         *
         * @exception IllegalArgumentException if the specified
         * {@code codePoint} is an invalid Unicode code point.
         * @see Character#isValidCodePoint(int)
         *
         */
        public static UnicodeScript of(int codePoint) {
            if (!isValidCodePoint(codePoint))
                throw new IllegalArgumentException();
            int type = getType(codePoint);
            // leave SURROGATE and PRIVATE_USE for table lookup
            if (type == UNASSIGNED)
                return UNKNOWN;
            int index = Arrays.binarySearch(scriptStarts, codePoint);
            if (index < 0)
                index = -index - 2;
            return scripts[index];
        }

        /**
         * Returns the UnicodeScript constant with the given Unicode script
         * name or the script name alias. Script names and their aliases are
         * determined by The Unicode Standard. The files Scripts&lt;version&gt;.txt
         * and PropertyValueAliases&lt;version&gt;.txt define script names
         * and the script name aliases for a particular version of the
         * standard. The {@link Character} class specifies the version of
         * the standard that it supports.
         * <p>
         * Character case is ignored for all of the valid script names.
         * The en_US locale's case mapping rules are used to provide
         * case-insensitive string comparisons for script name validation.
         *
         * @param scriptName A {@code UnicodeScript} name.
         * @return The {@code UnicodeScript} constant identified
         *         by {@code scriptName}
         * @throws IllegalArgumentException if {@code scriptName} is an
         *         invalid name
         * @throws NullPointerException if {@code scriptName} is null
         */
        public static final UnicodeScript forName(String scriptName) {
            scriptName = scriptName.toUpperCase(Locale.ENGLISH);
                                 //.replace(' ', '_'));
            UnicodeScript sc = aliases.get(scriptName);
            if (sc != null)
                return sc;
            return valueOf(scriptName);
        }
    }

    /**
     * The value of the {@code Character}.
     *
     * @serial
     */
    private final char value;

    /** use serialVersionUID from JDK 1.0.2 for interoperability */
    private static final long serialVersionUID = 3786198910865385080L;

    /**
     * Constructs a newly allocated {@code Character} object that
     * represents the specified {@code char} value.
     *
     * @param  value   the value to be represented by the
     *                  {@code Character} object.
     */
    public Character(char value) {
        this.value = value;
    }

    private static class CharacterCache {
        private CharacterCache(){}

        static final Character cache[] = new Character[127 + 1];

        static {
            for (int i = 0; i < cache.length; i++)
                cache[i] = new Character((char)i);
        }
    }

    /**
     * Returns a <tt>Character</tt> instance representing the specified
     * <tt>char</tt> value.
     * If a new <tt>Character</tt> instance is not required, this method
     * should generally be used in preference to the constructor
     * {@link #Character(char)}, as this method is likely to yield
     * significantly better space and time performance by caching
     * frequently requested values.
     *
     * This method will always cache values in the range {@code
     * '\u005Cu0000'} to {@code '\u005Cu007F'}, inclusive, and may
     * cache other values outside of this range.
     *
     * @param  c a char value.
     * @return a <tt>Character</tt> instance representing <tt>c</tt>.
     * @since  1.5
     */
    public static Character valueOf(char c) {
        if (c <= 127) { // must cache
            return CharacterCache.cache[(int)c];
        }
        return new Character(c);
    }

    /**
     * Returns the value of this {@code Character} object.
     * @return  the primitive {@code char} value represented by
     *          this object.
     */
    public char charValue() {
        return value;
    }

    /**
     * Returns a hash code for this {@code Character}; equal to the result
     * of invoking {@code charValue()}.
     *
     * @return a hash code value for this {@code Character}
     */
    @Override
    public int hashCode() {
        return Character.hashCode(value);
    }

    /**
     * Returns a hash code for a {@code char} value; compatible with
     * {@code Character.hashCode()}.
     *
     * @since 1.8
     *
     * @param value The {@code char} for which to return a hash code.
     * @return a hash code value for a {@code char} value.
     */
    public static int hashCode(char value) {
        return (int)value;
    }

    /**
     * Compares this object against the specified object.
     * The result is {@code true} if and only if the argument is not
     * {@code null} and is a {@code Character} object that
     * represents the same {@code char} value as this object.
     *
     * @param   obj   the object to compare with.
     * @return  {@code true} if the objects are the same;
     *          {@code false} otherwise.
     */
    public boolean equals(Object obj) {
        if (obj instanceof Character) {
            return value == ((Character)obj).charValue();
        }
        return false;
    }

    /**
     * Returns a {@code String} object representing this
     * {@code Character}'s value.  The result is a string of
     * length 1 whose sole component is the primitive
     * {@code char} value represented by this
     * {@code Character} object.
     *
     * @return  a string representation of this object.
     */
    public String toString() {
        char buf[] = {value};
        return String.valueOf(buf);
    }

    /**
     * Returns a {@code String} object representing the
     * specified {@code char}.  The result is a string of length
     * 1 consisting solely of the specified {@code char}.
     *
     * @param c the {@code char} to be converted
     * @return the string representation of the specified {@code char}
     * @since 1.4
     */
    public static String toString(char c) {
        return String.valueOf(c);
    }

    /**
     * Determines whether the specified code point is a valid
     * <a href="http://www.unicode.org/glossary/#code_point">
     * Unicode code point value</a>.
     *
     * @param  codePoint the Unicode code point to be tested
     * @return {@code true} if the specified code point value is between
     *         {@link #MIN_CODE_POINT} and
     *         {@link #MAX_CODE_POINT} inclusive;
     *         {@code false} otherwise.
     * @since  1.5
     */
    public static boolean isValidCodePoint(int codePoint) {
        // Optimized form of:
        //     codePoint >= MIN_CODE_POINT && codePoint <= MAX_CODE_POINT
        int plane = codePoint >>> 16;
        return plane < ((MAX_CODE_POINT + 1) >>> 16);
    }

    /**
     * Determines whether the specified character (Unicode code point)
     * is in the <a href="#BMP">Basic Multilingual Plane (BMP)</a>.
     * Such code points can be represented using a single {@code char}.
     *
     * @param  codePoint the character (Unicode code point) to be tested
     * @return {@code true} if the specified code point is between
     *         {@link #MIN_VALUE} and {@link #MAX_VALUE} inclusive;
     *         {@code false} otherwise.
     * @since  1.7
     */
    public static boolean isBmpCodePoint(int codePoint) {
        return codePoint >>> 16 == 0;
        // Optimized form of:
        //     codePoint >= MIN_VALUE && codePoint <= MAX_VALUE
        // We consistently use logical shift (>>>) to facilitate
        // additional runtime optimizations.
    }

    /**
     * Determines whether the specified character (Unicode code point)
     * is in the <a href="#supplementary">supplementary character</a> range.
     *
     * @param  codePoint the character (Unicode code point) to be tested
     * @return {@code true} if the specified code point is between
     *         {@link #MIN_SUPPLEMENTARY_CODE_POINT} and
     *         {@link #MAX_CODE_POINT} inclusive;
     *         {@code false} otherwise.
     * @since  1.5
     */
    public static boolean isSupplementaryCodePoint(int codePoint) {
        return codePoint >= MIN_SUPPLEMENTARY_CODE_POINT
            && codePoint <  MAX_CODE_POINT + 1;
    }

    /**
     * Determines if the given {@code char} value is a
     * <a href="http://www.unicode.org/glossary/#high_surrogate_code_unit">
     * Unicode high-surrogate code unit</a>
     * (also known as <i>leading-surrogate code unit</i>).
     *
     * <p>Such values do not represent characters by themselves,
     * but are used in the representation of
     * <a href="#supplementary">supplementary characters</a>
     * in the UTF-16 encoding.
     *
     * @param  ch the {@code char} value to be tested.
     * @return {@code true} if the {@code char} value is between
     *         {@link #MIN_HIGH_SURROGATE} and
     *         {@link #MAX_HIGH_SURROGATE} inclusive;
     *         {@code false} otherwise.
     * @see    Character#isLowSurrogate(char)
     * @see    Character.UnicodeBlock#of(int)
     * @since  1.5
     */
    public static boolean isHighSurrogate(char ch) {
        // Help VM constant-fold; MAX_HIGH_SURROGATE + 1 == MIN_LOW_SURROGATE
        return ch >= MIN_HIGH_SURROGATE && ch < (MAX_HIGH_SURROGATE + 1);
    }

    /**
     * Determines if the given {@code char} value is a
     * <a href="http://www.unicode.org/glossary/#low_surrogate_code_unit">
     * Unicode low-surrogate code unit</a>
     * (also known as <i>trailing-surrogate code unit</i>).
     *
     * <p>Such values do not represent characters by themselves,
     * but are used in the representation of
     * <a href="#supplementary">supplementary characters</a>
     * in the UTF-16 encoding.
     *
     * @param  ch the {@code char} value to be tested.
     * @return {@code true} if the {@code char} value is between
     *         {@link #MIN_LOW_SURROGATE} and
     *         {@link #MAX_LOW_SURROGATE} inclusive;
     *         {@code false} otherwise.
     * @see    Character#isHighSurrogate(char)
     * @since  1.5
     */
    public static boolean isLowSurrogate(char ch) {
        return ch >= MIN_LOW_SURROGATE && ch < (MAX_LOW_SURROGATE + 1);
    }

    /**
     * Determines if the given {@code char} value is a Unicode
     * <i>surrogate code unit</i>.
     *
     * <p>Such values do not represent characters by themselves,
     * but are used in the representation of
     * <a href="#supplementary">supplementary characters</a>
     * in the UTF-16 encoding.
     *
     * <p>A char value is a surrogate code unit if and only if it is either
     * a {@linkplain #isLowSurrogate(char) low-surrogate code unit} or
     * a {@linkplain #isHighSurrogate(char) high-surrogate code unit}.
     *
     * @param  ch the {@code char} value to be tested.
     * @return {@code true} if the {@code char} value is between
     *         {@link #MIN_SURROGATE} and
     *         {@link #MAX_SURROGATE} inclusive;
     *         {@code false} otherwise.
     * @since  1.7
     */
    public static boolean isSurrogate(char ch) {
        return ch >= MIN_SURROGATE && ch < (MAX_SURROGATE + 1);
    }

    /**
     * Determines whether the specified pair of {@code char}
     * values is a valid
     * <a href="http://www.unicode.org/glossary/#surrogate_pair">
     * Unicode surrogate pair</a>.

     * <p>This method is equivalent to the expression:
     * <blockquote><pre>{@code
     * isHighSurrogate(high) && isLowSurrogate(low)
     * }</pre></blockquote>
     *
     * @param  high the high-surrogate code value to be tested
     * @param  low the low-surrogate code value to be tested
     * @return {@code true} if the specified high and
     * low-surrogate code values represent a valid surrogate pair;
     * {@code false} otherwise.
     * @since  1.5
     */
    public static boolean isSurrogatePair(char high, char low) {
        return isHighSurrogate(high) && isLowSurrogate(low);
    }

    /**
     * Determines the number of {@code char} values needed to
     * represent the specified character (Unicode code point). If the
     * specified character is equal to or greater than 0x10000, then
     * the method returns 2. Otherwise, the method returns 1.
     *
     * <p>This method doesn't validate the specified character to be a
     * valid Unicode code point. The caller must validate the
     * character value using {@link #isValidCodePoint(int) isValidCodePoint}
     * if necessary.
     *
     * @param   codePoint the character (Unicode code point) to be tested.
     * @return  2 if the character is a valid supplementary character; 1 otherwise.
     * @see     Character#isSupplementaryCodePoint(int)
     * @since   1.5
     */
    public static int charCount(int codePoint) {
        return codePoint >= MIN_SUPPLEMENTARY_CODE_POINT ? 2 : 1;
    }

    /**
     * Converts the specified surrogate pair to its supplementary code
     * point value. This method does not validate the specified
     * surrogate pair. The caller must validate it using {@link
     * #isSurrogatePair(char, char) isSurrogatePair} if necessary.
     *
     * @param  high the high-surrogate code unit
     * @param  low the low-surrogate code unit
     * @return the supplementary code point composed from the
     *         specified surrogate pair.
     * @since  1.5
     */
    public static int toCodePoint(char high, char low) {
        // Optimized form of:
        // return ((high - MIN_HIGH_SURROGATE) << 10)
        //         + (low - MIN_LOW_SURROGATE)
        //         + MIN_SUPPLEMENTARY_CODE_POINT;
        return ((high << 10) + low) + (MIN_SUPPLEMENTARY_CODE_POINT
                                       - (MIN_HIGH_SURROGATE << 10)
                                       - MIN_LOW_SURROGATE);
    }

    /**
     * Returns the code point at the given index of the
     * {@code CharSequence}. If the {@code char} value at
     * the given index in the {@code CharSequence} is in the
     * high-surrogate range, the following index is less than the
     * length of the {@code CharSequence}, and the
     * {@code char} value at the following index is in the
     * low-surrogate range, then the supplementary code point
     * corresponding to this surrogate pair is returned. Otherwise,
     * the {@code char} value at the given index is returned.
     *
     * @param seq a sequence of {@code char} values (Unicode code
     * units)
     * @param index the index to the {@code char} values (Unicode
     * code units) in {@code seq} to be converted
     * @return the Unicode code point at the given index
     * @exception NullPointerException if {@code seq} is null.
     * @exception IndexOutOfBoundsException if the value
     * {@code index} is negative or not less than
     * {@link CharSequence#length() seq.length()}.
     * @since  1.5
     */
    public static int codePointAt(CharSequence seq, int index) {
        char c1 = seq.charAt(index);
        if (isHighSurrogate(c1) && ++index < seq.length()) {
            char c2 = seq.charAt(index);
            if (isLowSurrogate(c2)) {
                return toCodePoint(c1, c2);
            }
        }
        return c1;
    }

    /**
     * Returns the code point at the given index of the
     * {@code char} array. If the {@code char} value at
     * the given index in the {@code char} array is in the
     * high-surrogate range, the following index is less than the
     * length of the {@code char} array, and the
     * {@code char} value at the following index is in the
     * low-surrogate range, then the supplementary code point
     * corresponding to this surrogate pair is returned. Otherwise,
     * the {@code char} value at the given index is returned.
     *
     * @param a the {@code char} array
     * @param index the index to the {@code char} values (Unicode
     * code units) in the {@code char} array to be converted
     * @return the Unicode code point at the given index
     * @exception NullPointerException if {@code a} is null.
     * @exception IndexOutOfBoundsException if the value
     * {@code index} is negative or not less than
     * the length of the {@code char} array.
     * @since  1.5
     */
    public static int codePointAt(char[] a, int index) {
        return codePointAtImpl(a, index, a.length);
    }

    /**
     * Returns the code point at the given index of the
     * {@code char} array, where only array elements with
     * {@code index} less than {@code limit} can be used. If
     * the {@code char} value at the given index in the
     * {@code char} array is in the high-surrogate range, the
     * following index is less than the {@code limit}, and the
     * {@code char} value at the following index is in the
     * low-surrogate range, then the supplementary code point
     * corresponding to this surrogate pair is returned. Otherwise,
     * the {@code char} value at the given index is returned.
     *
     * @param a the {@code char} array
     * @param index the index to the {@code char} values (Unicode
     * code units) in the {@code char} array to be converted
     * @param limit the index after the last array element that
     * can be used in the {@code char} array
     * @return the Unicode code point at the given index
     * @exception NullPointerException if {@code a} is null.
     * @exception IndexOutOfBoundsException if the {@code index}
     * argument is negative or not less than the {@code limit}
     * argument, or if the {@code limit} argument is negative or
     * greater than the length of the {@code char} array.
     * @since  1.5
     */
    public static int codePointAt(char[] a, int index, int limit) {
        if (index >= limit || limit < 0 || limit > a.length) {
            throw new IndexOutOfBoundsException();
        }
        return codePointAtImpl(a, index, limit);
    }

    // throws ArrayIndexOutOfBoundsException if index out of bounds
    static int codePointAtImpl(char[] a, int index, int limit) {
        char c1 = a[index];
        if (isHighSurrogate(c1) && ++index < limit) {
            char c2 = a[index];
            if (isLowSurrogate(c2)) {
                return toCodePoint(c1, c2);
            }
        }
        return c1;
    }

    /**
     * Returns the code point preceding the given index of the
     * {@code CharSequence}. If the {@code char} value at
     * {@code (index - 1)} in the {@code CharSequence} is in
     * the low-surrogate range, {@code (index - 2)} is not
     * negative, and the {@code char} value at {@code (index - 2)}
     * in the {@code CharSequence} is in the
     * high-surrogate range, then the supplementary code point
     * corresponding to this surrogate pair is returned. Otherwise,
     * the {@code char} value at {@code (index - 1)} is
     * returned.
     *
     * @param seq the {@code CharSequence} instance
     * @param index the index following the code point that should be returned
     * @return the Unicode code point value before the given index.
     * @exception NullPointerException if {@code seq} is null.
     * @exception IndexOutOfBoundsException if the {@code index}
     * argument is less than 1 or greater than {@link
     * CharSequence#length() seq.length()}.
     * @since  1.5
     */
    public static int codePointBefore(CharSequence seq, int index) {
        char c2 = seq.charAt(--index);
        if (isLowSurrogate(c2) && index > 0) {
            char c1 = seq.charAt(--index);
            if (isHighSurrogate(c1)) {
                return toCodePoint(c1, c2);
            }
        }
        return c2;
    }

    /**
     * Returns the code point preceding the given index of the
     * {@code char} array. If the {@code char} value at
     * {@code (index - 1)} in the {@code char} array is in
     * the low-surrogate range, {@code (index - 2)} is not
     * negative, and the {@code char} value at {@code (index - 2)}
     * in the {@code char} array is in the
     * high-surrogate range, then the supplementary code point
     * corresponding to this surrogate pair is returned. Otherwise,
     * the {@code char} value at {@code (index - 1)} is
     * returned.
     *
     * @param a the {@code char} array
     * @param index the index following the code point that should be returned
     * @return the Unicode code point value before the given index.
     * @exception NullPointerException if {@code a} is null.
     * @exception IndexOutOfBoundsException if the {@code index}
     * argument is less than 1 or greater than the length of the
     * {@code char} array
     * @since  1.5
     */
    public static int codePointBefore(char[] a, int index) {
        return codePointBeforeImpl(a, index, 0);
    }

    /**
     * Returns the code point preceding the given index of the
     * {@code char} array, where only array elements with
     * {@code index} greater than or equal to {@code start}
     * can be used. If the {@code char} value at {@code (index - 1)}
     * in the {@code char} array is in the
     * low-surrogate range, {@code (index - 2)} is not less than
     * {@code start}, and the {@code char} value at
     * {@code (index - 2)} in the {@code char} array is in
     * the high-surrogate range, then the supplementary code point
     * corresponding to this surrogate pair is returned. Otherwise,
     * the {@code char} value at {@code (index - 1)} is
     * returned.
     *
     * @param a the {@code char} array
     * @param index the index following the code point that should be returned
     * @param start the index of the first array element in the
     * {@code char} array
     * @return the Unicode code point value before the given index.
     * @exception NullPointerException if {@code a} is null.
     * @exception IndexOutOfBoundsException if the {@code index}
     * argument is not greater than the {@code start} argument or
     * is greater than the length of the {@code char} array, or
     * if the {@code start} argument is negative or not less than
     * the length of the {@code char} array.
     * @since  1.5
     */
    public static int codePointBefore(char[] a, int index, int start) {
        if (index <= start || start < 0 || start >= a.length) {
            throw new IndexOutOfBoundsException();
        }
        return codePointBeforeImpl(a, index, start);
    }

    // throws ArrayIndexOutOfBoundsException if index-1 out of bounds
    static int codePointBeforeImpl(char[] a, int index, int start) {
        char c2 = a[--index];
        if (isLowSurrogate(c2) && index > start) {
            char c1 = a[--index];
            if (isHighSurrogate(c1)) {
                return toCodePoint(c1, c2);
            }
        }
        return c2;
    }

    /**
     * Returns the leading surrogate (a
     * <a href="http://www.unicode.org/glossary/#high_surrogate_code_unit">
     * high surrogate code unit</a>) of the
     * <a href="http://www.unicode.org/glossary/#surrogate_pair">
     * surrogate pair</a>
     * representing the specified supplementary character (Unicode
     * code point) in the UTF-16 encoding.  If the specified character
     * is not a
     * <a href="Character.html#supplementary">supplementary character</a>,
     * an unspecified {@code char} is returned.
     *
     * <p>If
     * {@link #isSupplementaryCodePoint isSupplementaryCodePoint(x)}
     * is {@code true}, then
     * {@link #isHighSurrogate isHighSurrogate}{@code (highSurrogate(x))} and
     * {@link #toCodePoint toCodePoint}{@code (highSurrogate(x), }{@link #lowSurrogate lowSurrogate}{@code (x)) == x}
     * are also always {@code true}.
     *
     * @param   codePoint a supplementary character (Unicode code point)
     * @return  the leading surrogate code unit used to represent the
     *          character in the UTF-16 encoding
     * @since   1.7
     */
    public static char highSurrogate(int codePoint) {
        return (char) ((codePoint >>> 10)
            + (MIN_HIGH_SURROGATE - (MIN_SUPPLEMENTARY_CODE_POINT >>> 10)));
    }

    /**
     * Returns the trailing surrogate (a
     * <a href="http://www.unicode.org/glossary/#low_surrogate_code_unit">
     * low surrogate code unit</a>) of the
     * <a href="http://www.unicode.org/glossary/#surrogate_pair">
     * surrogate pair</a>
     * representing the specified supplementary character (Unicode
     * code point) in the UTF-16 encoding.  If the specified character
     * is not a
     * <a href="Character.html#supplementary">supplementary character</a>,
     * an unspecified {@code char} is returned.
     *
     * <p>If
     * {@link #isSupplementaryCodePoint isSupplementaryCodePoint(x)}
     * is {@code true}, then
     * {@link #isLowSurrogate isLowSurrogate}{@code (lowSurrogate(x))} and
     * {@link #toCodePoint toCodePoint}{@code (}{@link #highSurrogate highSurrogate}{@code (x), lowSurrogate(x)) == x}
     * are also always {@code true}.
     *
     * @param   codePoint a supplementary character (Unicode code point)
     * @return  the trailing surrogate code unit used to represent the
     *          character in the UTF-16 encoding
     * @since   1.7
     */
    public static char lowSurrogate(int codePoint) {
        return (char) ((codePoint & 0x3ff) + MIN_LOW_SURROGATE);
    }

    /**
     * Converts the specified character (Unicode code point) to its
     * UTF-16 representation. If the specified code point is a BMP
     * (Basic Multilingual Plane or Plane 0) value, the same value is
     * stored in {@code dst[dstIndex]}, and 1 is returned. If the
     * specified code point is a supplementary character, its
     * surrogate values are stored in {@code dst[dstIndex]}
     * (high-surrogate) and {@code dst[dstIndex+1]}
     * (low-surrogate), and 2 is returned.
     *
     * @param  codePoint the character (Unicode code point) to be converted.
     * @param  dst an array of {@code char} in which the
     * {@code codePoint}'s UTF-16 value is stored.
     * @param dstIndex the start index into the {@code dst}
     * array where the converted value is stored.
     * @return 1 if the code point is a BMP code point, 2 if the
     * code point is a supplementary code point.
     * @exception IllegalArgumentException if the specified
     * {@code codePoint} is not a valid Unicode code point.
     * @exception NullPointerException if the specified {@code dst} is null.
     * @exception IndexOutOfBoundsException if {@code dstIndex}
     * is negative or not less than {@code dst.length}, or if
     * {@code dst} at {@code dstIndex} doesn't have enough
     * array element(s) to store the resulting {@code char}
     * value(s). (If {@code dstIndex} is equal to
     * {@code dst.length-1} and the specified
     * {@code codePoint} is a supplementary character, the
     * high-surrogate value is not stored in
     * {@code dst[dstIndex]}.)
     * @since  1.5
     */
    public static int toChars(int codePoint, char[] dst, int dstIndex) {
        if (isBmpCodePoint(codePoint)) {
            dst[dstIndex] = (char) codePoint;
            return 1;
        } else if (isValidCodePoint(codePoint)) {
            toSurrogates(codePoint, dst, dstIndex);
            return 2;
        } else {
            throw new IllegalArgumentException();
        }
    }

    /**
     * Converts the specified character (Unicode code point) to its
     * UTF-16 representation stored in a {@code char} array. If
     * the specified code point is a BMP (Basic Multilingual Plane or
     * Plane 0) value, the resulting {@code char} array has
     * the same value as {@code codePoint}. If the specified code
     * point is a supplementary code point, the resulting
     * {@code char} array has the corresponding surrogate pair.
     *
     * @param  codePoint a Unicode code point
     * @return a {@code char} array having
     *         {@code codePoint}'s UTF-16 representation.
     * @exception IllegalArgumentException if the specified
     * {@code codePoint} is not a valid Unicode code point.
     * @since  1.5
     */
    public static char[] toChars(int codePoint) {
        if (isBmpCodePoint(codePoint)) {
            return new char[] { (char) codePoint };
        } else if (isValidCodePoint(codePoint)) {
            char[] result = new char[2];
            toSurrogates(codePoint, result, 0);
            return result;
        } else {
            throw new IllegalArgumentException();
        }
    }

    static void toSurrogates(int codePoint, char[] dst, int index) {
        // We write elements "backwards" to guarantee all-or-nothing
        dst[index+1] = lowSurrogate(codePoint);
        dst[index] = highSurrogate(codePoint);
    }

    /**
     * Returns the number of Unicode code points in the text range of
     * the specified char sequence. The text range begins at the
     * specified {@code beginIndex} and extends to the
     * {@code char} at index {@code endIndex - 1}. Thus the
     * length (in {@code char}s) of the text range is
     * {@code endIndex-beginIndex}. Unpaired surrogates within
     * the text range count as one code point each.
     *
     * @param seq the char sequence
     * @param beginIndex the index to the first {@code char} of
     * the text range.
     * @param endIndex the index after the last {@code char} of
     * the text range.
     * @return the number of Unicode code points in the specified text
     * range
     * @exception NullPointerException if {@code seq} is null.
     * @exception IndexOutOfBoundsException if the
     * {@code beginIndex} is negative, or {@code endIndex}
     * is larger than the length of the given sequence, or
     * {@code beginIndex} is larger than {@code endIndex}.
     * @since  1.5
     */
    public static int codePointCount(CharSequence seq, int beginIndex, int endIndex) {
        int length = seq.length();
        if (beginIndex < 0 || endIndex > length || beginIndex > endIndex) {
            throw new IndexOutOfBoundsException();
        }
        int n = endIndex - beginIndex;
        for (int i = beginIndex; i < endIndex; ) {
            if (isHighSurrogate(seq.charAt(i++)) && i < endIndex &&
                isLowSurrogate(seq.charAt(i))) {
                n--;
                i++;
            }
        }
        return n;
    }

    /**
     * Returns the number of Unicode code points in a subarray of the
     * {@code char} array argument. The {@code offset}
     * argument is the index of the first {@code char} of the
     * subarray and the {@code count} argument specifies the
     * length of the subarray in {@code char}s. Unpaired
     * surrogates within the subarray count as one code point each.
     *
     * @param a the {@code char} array
     * @param offset the index of the first {@code char} in the
     * given {@code char} array
     * @param count the length of the subarray in {@code char}s
     * @return the number of Unicode code points in the specified subarray
     * @exception NullPointerException if {@code a} is null.
     * @exception IndexOutOfBoundsException if {@code offset} or
     * {@code count} is negative, or if {@code offset +
     * count} is larger than the length of the given array.
     * @since  1.5
     */
    public static int codePointCount(char[] a, int offset, int count) {
        if (count > a.length - offset || offset < 0 || count < 0) {
            throw new IndexOutOfBoundsException();
        }
        return codePointCountImpl(a, offset, count);
    }

    static int codePointCountImpl(char[] a, int offset, int count) {
        int endIndex = offset + count;
        int n = count;
        for (int i = offset; i < endIndex; ) {
            if (isHighSurrogate(a[i++]) && i < endIndex &&
                isLowSurrogate(a[i])) {
                n--;
                i++;
            }
        }
        return n;
    }

    /**
     * Returns the index within the given char sequence that is offset
     * from the given {@code index} by {@code codePointOffset}
     * code points. Unpaired surrogates within the text range given by
     * {@code index} and {@code codePointOffset} count as
     * one code point each.
     *
     * @param seq the char sequence
     * @param index the index to be offset
     * @param codePointOffset the offset in code points
     * @return the index within the char sequence
     * @exception NullPointerException if {@code seq} is null.
     * @exception IndexOutOfBoundsException if {@code index}
     *   is negative or larger then the length of the char sequence,
     *   or if {@code codePointOffset} is positive and the
     *   subsequence starting with {@code index} has fewer than
     *   {@code codePointOffset} code points, or if
     *   {@code codePointOffset} is negative and the subsequence
     *   before {@code index} has fewer than the absolute value
     *   of {@code codePointOffset} code points.
     * @since 1.5
     */
    public static int offsetByCodePoints(CharSequence seq, int index,
                                         int codePointOffset) {
        int length = seq.length();
        if (index < 0 || index > length) {
            throw new IndexOutOfBoundsException();
        }

        int x = index;
        if (codePointOffset >= 0) {
            int i;
            for (i = 0; x < length && i < codePointOffset; i++) {
                if (isHighSurrogate(seq.charAt(x++)) && x < length &&
                    isLowSurrogate(seq.charAt(x))) {
                    x++;
                }
            }
            if (i < codePointOffset) {
                throw new IndexOutOfBoundsException();
            }
        } else {
            int i;
            for (i = codePointOffset; x > 0 && i < 0; i++) {
                if (isLowSurrogate(seq.charAt(--x)) && x > 0 &&
                    isHighSurrogate(seq.charAt(x-1))) {
                    x--;
                }
            }
            if (i < 0) {
                throw new IndexOutOfBoundsException();
            }
        }
        return x;
    }

    /**
     * Returns the index within the given {@code char} subarray
     * that is offset from the given {@code index} by
     * {@code codePointOffset} code points. The
     * {@code start} and {@code count} arguments specify a
     * subarray of the {@code char} array. Unpaired surrogates
     * within the text range given by {@code index} and
     * {@code codePointOffset} count as one code point each.
     *
     * @param a the {@code char} array
     * @param start the index of the first {@code char} of the
     * subarray
     * @param count the length of the subarray in {@code char}s
     * @param index the index to be offset
     * @param codePointOffset the offset in code points
     * @return the index within the subarray
     * @exception NullPointerException if {@code a} is null.
     * @exception IndexOutOfBoundsException
     *   if {@code start} or {@code count} is negative,
     *   or if {@code start + count} is larger than the length of
     *   the given array,
     *   or if {@code index} is less than {@code start} or
     *   larger then {@code start + count},
     *   or if {@code codePointOffset} is positive and the text range
     *   starting with {@code index} and ending with {@code start + count - 1}
     *   has fewer than {@code codePointOffset} code
     *   points,
     *   or if {@code codePointOffset} is negative and the text range
     *   starting with {@code start} and ending with {@code index - 1}
     *   has fewer than the absolute value of
     *   {@code codePointOffset} code points.
     * @since 1.5
     */
    public static int offsetByCodePoints(char[] a, int start, int count,
                                         int index, int codePointOffset) {
        if (count > a.length-start || start < 0 || count < 0
            || index < start || index > start+count) {
            throw new IndexOutOfBoundsException();
        }
        return offsetByCodePointsImpl(a, start, count, index, codePointOffset);
    }

    static int offsetByCodePointsImpl(char[]a, int start, int count,
                                      int index, int codePointOffset) {
        int x = index;
        if (codePointOffset >= 0) {
            int limit = start + count;
            int i;
            for (i = 0; x < limit && i < codePointOffset; i++) {
                if (isHighSurrogate(a[x++]) && x < limit &&
                    isLowSurrogate(a[x])) {
                    x++;
                }
            }
            if (i < codePointOffset) {
                throw new IndexOutOfBoundsException();
            }
        } else {
            int i;
            for (i = codePointOffset; x > start && i < 0; i++) {
                if (isLowSurrogate(a[--x]) && x > start &&
                    isHighSurrogate(a[x-1])) {
                    x--;
                }
            }
            if (i < 0) {
                throw new IndexOutOfBoundsException();
            }
        }
        return x;
    }

    /**
     * Determines if the specified character is a lowercase character.
     * <p>
     * A character is lowercase if its general category type, provided
     * by {@code Character.getType(ch)}, is
     * {@code LOWERCASE_LETTER}, or it has contributory property
     * Other_Lowercase as defined by the Unicode Standard.
     * <p>
     * The following are examples of lowercase characters:
     * <blockquote><pre>
     * a b c d e f g h i j k l m n o p q r s t u v w x y z
     * '&#92;u00DF' '&#92;u00E0' '&#92;u00E1' '&#92;u00E2' '&#92;u00E3' '&#92;u00E4' '&#92;u00E5' '&#92;u00E6'
     * '&#92;u00E7' '&#92;u00E8' '&#92;u00E9' '&#92;u00EA' '&#92;u00EB' '&#92;u00EC' '&#92;u00ED' '&#92;u00EE'
     * '&#92;u00EF' '&#92;u00F0' '&#92;u00F1' '&#92;u00F2' '&#92;u00F3' '&#92;u00F4' '&#92;u00F5' '&#92;u00F6'
     * '&#92;u00F8' '&#92;u00F9' '&#92;u00FA' '&#92;u00FB' '&#92;u00FC' '&#92;u00FD' '&#92;u00FE' '&#92;u00FF'
     * </pre></blockquote>
     * <p> Many other Unicode characters are lowercase too.
     *
     * <p><b>Note:</b> This method cannot handle <a
     * href="#supplementary"> supplementary characters</a>. To support
     * all Unicode characters, including supplementary characters, use
     * the {@link #isLowerCase(int)} method.
     *
     * @param   ch   the character to be tested.
     * @return  {@code true} if the character is lowercase;
     *          {@code false} otherwise.
     * @see     Character#isLowerCase(char)
     * @see     Character#isTitleCase(char)
     * @see     Character#toLowerCase(char)
     * @see     Character#getType(char)
     */
    public static boolean isLowerCase(char ch) {
        return isLowerCase((int)ch);
    }

    /**
     * Determines if the specified character (Unicode code point) is a
     * lowercase character.
     * <p>
     * A character is lowercase if its general category type, provided
     * by {@link Character#getType getType(codePoint)}, is
     * {@code LOWERCASE_LETTER}, or it has contributory property
     * Other_Lowercase as defined by the Unicode Standard.
     * <p>
     * The following are examples of lowercase characters:
     * <blockquote><pre>
     * a b c d e f g h i j k l m n o p q r s t u v w x y z
     * '&#92;u00DF' '&#92;u00E0' '&#92;u00E1' '&#92;u00E2' '&#92;u00E3' '&#92;u00E4' '&#92;u00E5' '&#92;u00E6'
     * '&#92;u00E7' '&#92;u00E8' '&#92;u00E9' '&#92;u00EA' '&#92;u00EB' '&#92;u00EC' '&#92;u00ED' '&#92;u00EE'
     * '&#92;u00EF' '&#92;u00F0' '&#92;u00F1' '&#92;u00F2' '&#92;u00F3' '&#92;u00F4' '&#92;u00F5' '&#92;u00F6'
     * '&#92;u00F8' '&#92;u00F9' '&#92;u00FA' '&#92;u00FB' '&#92;u00FC' '&#92;u00FD' '&#92;u00FE' '&#92;u00FF'
     * </pre></blockquote>
     * <p> Many other Unicode characters are lowercase too.
     *
     * @param   codePoint the character (Unicode code point) to be tested.
     * @return  {@code true} if the character is lowercase;
     *          {@code false} otherwise.
     * @see     Character#isLowerCase(int)
     * @see     Character#isTitleCase(int)
     * @see     Character#toLowerCase(int)
     * @see     Character#getType(int)
     * @since   1.5
     */
    public static boolean isLowerCase(int codePoint) {
        return getType(codePoint) == Character.LOWERCASE_LETTER ||
               CharacterData.of(codePoint).isOtherLowercase(codePoint);
    }

    /**
     * Determines if the specified character is an uppercase character.
     * <p>
     * A character is uppercase if its general category type, provided by
     * {@code Character.getType(ch)}, is {@code UPPERCASE_LETTER}.
     * or it has contributory property Other_Uppercase as defined by the Unicode Standard.
     * <p>
     * The following are examples of uppercase characters:
     * <blockquote><pre>
     * A B C D E F G H I J K L M N O P Q R S T U V W X Y Z
     * '&#92;u00C0' '&#92;u00C1' '&#92;u00C2' '&#92;u00C3' '&#92;u00C4' '&#92;u00C5' '&#92;u00C6' '&#92;u00C7'
     * '&#92;u00C8' '&#92;u00C9' '&#92;u00CA' '&#92;u00CB' '&#92;u00CC' '&#92;u00CD' '&#92;u00CE' '&#92;u00CF'
     * '&#92;u00D0' '&#92;u00D1' '&#92;u00D2' '&#92;u00D3' '&#92;u00D4' '&#92;u00D5' '&#92;u00D6' '&#92;u00D8'
     * '&#92;u00D9' '&#92;u00DA' '&#92;u00DB' '&#92;u00DC' '&#92;u00DD' '&#92;u00DE'
     * </pre></blockquote>
     * <p> Many other Unicode characters are uppercase too.
     *
     * <p><b>Note:</b> This method cannot handle <a
     * href="#supplementary"> supplementary characters</a>. To support
     * all Unicode characters, including supplementary characters, use
     * the {@link #isUpperCase(int)} method.
     *
     * @param   ch   the character to be tested.
     * @return  {@code true} if the character is uppercase;
     *          {@code false} otherwise.
     * @see     Character#isLowerCase(char)
     * @see     Character#isTitleCase(char)
     * @see     Character#toUpperCase(char)
     * @see     Character#getType(char)
     * @since   1.0
     */
    public static boolean isUpperCase(char ch) {
        return isUpperCase((int)ch);
    }

    /**
     * Determines if the specified character (Unicode code point) is an uppercase character.
     * <p>
     * A character is uppercase if its general category type, provided by
     * {@link Character#getType(int) getType(codePoint)}, is {@code UPPERCASE_LETTER},
     * or it has contributory property Other_Uppercase as defined by the Unicode Standard.
     * <p>
     * The following are examples of uppercase characters:
     * <blockquote><pre>
     * A B C D E F G H I J K L M N O P Q R S T U V W X Y Z
     * '&#92;u00C0' '&#92;u00C1' '&#92;u00C2' '&#92;u00C3' '&#92;u00C4' '&#92;u00C5' '&#92;u00C6' '&#92;u00C7'
     * '&#92;u00C8' '&#92;u00C9' '&#92;u00CA' '&#92;u00CB' '&#92;u00CC' '&#92;u00CD' '&#92;u00CE' '&#92;u00CF'
     * '&#92;u00D0' '&#92;u00D1' '&#92;u00D2' '&#92;u00D3' '&#92;u00D4' '&#92;u00D5' '&#92;u00D6' '&#92;u00D8'
     * '&#92;u00D9' '&#92;u00DA' '&#92;u00DB' '&#92;u00DC' '&#92;u00DD' '&#92;u00DE'
     * </pre></blockquote>
     * <p> Many other Unicode characters are uppercase too.
     *
     * @param   codePoint the character (Unicode code point) to be tested.
     * @return  {@code true} if the character is uppercase;
     *          {@code false} otherwise.
     * @see     Character#isLowerCase(int)
     * @see     Character#isTitleCase(int)
     * @see     Character#toUpperCase(int)
     * @see     Character#getType(int)
     * @since   1.5
     */
    public static boolean isUpperCase(int codePoint) {
        return getType(codePoint) == Character.UPPERCASE_LETTER ||
               CharacterData.of(codePoint).isOtherUppercase(codePoint);
    }

    /**
     * Determines if the specified character is a titlecase character.
     * <p>
     * A character is a titlecase character if its general
     * category type, provided by {@code Character.getType(ch)},
     * is {@code TITLECASE_LETTER}.
     * <p>
     * Some characters look like pairs of Latin letters. For example, there
     * is an uppercase letter that looks like "LJ" and has a corresponding
     * lowercase letter that looks like "lj". A third form, which looks like "Lj",
     * is the appropriate form to use when rendering a word in lowercase
     * with initial capitals, as for a book title.
     * <p>
     * These are some of the Unicode characters for which this method returns
     * {@code true}:
     * <ul>
     * <li>{@code LATIN CAPITAL LETTER D WITH SMALL LETTER Z WITH CARON}
     * <li>{@code LATIN CAPITAL LETTER L WITH SMALL LETTER J}
     * <li>{@code LATIN CAPITAL LETTER N WITH SMALL LETTER J}
     * <li>{@code LATIN CAPITAL LETTER D WITH SMALL LETTER Z}
     * </ul>
     * <p> Many other Unicode characters are titlecase too.
     *
     * <p><b>Note:</b> This method cannot handle <a
     * href="#supplementary"> supplementary characters</a>. To support
     * all Unicode characters, including supplementary characters, use
     * the {@link #isTitleCase(int)} method.
     *
     * @param   ch   the character to be tested.
     * @return  {@code true} if the character is titlecase;
     *          {@code false} otherwise.
     * @see     Character#isLowerCase(char)
     * @see     Character#isUpperCase(char)
     * @see     Character#toTitleCase(char)
     * @see     Character#getType(char)
     * @since   1.0.2
     */
    public static boolean isTitleCase(char ch) {
        return isTitleCase((int)ch);
    }

    /**
     * Determines if the specified character (Unicode code point) is a titlecase character.
     * <p>
     * A character is a titlecase character if its general
     * category type, provided by {@link Character#getType(int) getType(codePoint)},
     * is {@code TITLECASE_LETTER}.
     * <p>
     * Some characters look like pairs of Latin letters. For example, there
     * is an uppercase letter that looks like "LJ" and has a corresponding
     * lowercase letter that looks like "lj". A third form, which looks like "Lj",
     * is the appropriate form to use when rendering a word in lowercase
     * with initial capitals, as for a book title.
     * <p>
     * These are some of the Unicode characters for which this method returns
     * {@code true}:
     * <ul>
     * <li>{@code LATIN CAPITAL LETTER D WITH SMALL LETTER Z WITH CARON}
     * <li>{@code LATIN CAPITAL LETTER L WITH SMALL LETTER J}
     * <li>{@code LATIN CAPITAL LETTER N WITH SMALL LETTER J}
     * <li>{@code LATIN CAPITAL LETTER D WITH SMALL LETTER Z}
     * </ul>
     * <p> Many other Unicode characters are titlecase too.
     *
     * @param   codePoint the character (Unicode code point) to be tested.
     * @return  {@code true} if the character is titlecase;
     *          {@code false} otherwise.
     * @see     Character#isLowerCase(int)
     * @see     Character#isUpperCase(int)
     * @see     Character#toTitleCase(int)
     * @see     Character#getType(int)
     * @since   1.5
     */
    public static boolean isTitleCase(int codePoint) {
        return getType(codePoint) == Character.TITLECASE_LETTER;
    }

    /**
     * Determines if the specified character is a digit.
     * <p>
     * A character is a digit if its general category type, provided
     * by {@code Character.getType(ch)}, is
     * {@code DECIMAL_DIGIT_NUMBER}.
     * <p>
     * Some Unicode character ranges that contain digits:
     * <ul>
     * <li>{@code '\u005Cu0030'} through {@code '\u005Cu0039'},
     *     ISO-LATIN-1 digits ({@code '0'} through {@code '9'})
     * <li>{@code '\u005Cu0660'} through {@code '\u005Cu0669'},
     *     Arabic-Indic digits
     * <li>{@code '\u005Cu06F0'} through {@code '\u005Cu06F9'},
     *     Extended Arabic-Indic digits
     * <li>{@code '\u005Cu0966'} through {@code '\u005Cu096F'},
     *     Devanagari digits
     * <li>{@code '\u005CuFF10'} through {@code '\u005CuFF19'},
     *     Fullwidth digits
     * </ul>
     *
     * Many other character ranges contain digits as well.
     *
     * <p><b>Note:</b> This method cannot handle <a
     * href="#supplementary"> supplementary characters</a>. To support
     * all Unicode characters, including supplementary characters, use
     * the {@link #isDigit(int)} method.
     *
     * @param   ch   the character to be tested.
     * @return  {@code true} if the character is a digit;
     *          {@code false} otherwise.
     * @see     Character#digit(char, int)
     * @see     Character#forDigit(int, int)
     * @see     Character#getType(char)
     */
    public static boolean isDigit(char ch) {
        return isDigit((int)ch);
    }

    /**
     * Determines if the specified character (Unicode code point) is a digit.
     * <p>
     * A character is a digit if its general category type, provided
     * by {@link Character#getType(int) getType(codePoint)}, is
     * {@code DECIMAL_DIGIT_NUMBER}.
     * <p>
     * Some Unicode character ranges that contain digits:
     * <ul>
     * <li>{@code '\u005Cu0030'} through {@code '\u005Cu0039'},
     *     ISO-LATIN-1 digits ({@code '0'} through {@code '9'})
     * <li>{@code '\u005Cu0660'} through {@code '\u005Cu0669'},
     *     Arabic-Indic digits
     * <li>{@code '\u005Cu06F0'} through {@code '\u005Cu06F9'},
     *     Extended Arabic-Indic digits
     * <li>{@code '\u005Cu0966'} through {@code '\u005Cu096F'},
     *     Devanagari digits
     * <li>{@code '\u005CuFF10'} through {@code '\u005CuFF19'},
     *     Fullwidth digits
     * </ul>
     *
     * Many other character ranges contain digits as well.
     *
     * @param   codePoint the character (Unicode code point) to be tested.
     * @return  {@code true} if the character is a digit;
     *          {@code false} otherwise.
     * @see     Character#forDigit(int, int)
     * @see     Character#getType(int)
     * @since   1.5
     */
    public static boolean isDigit(int codePoint) {
        return getType(codePoint) == Character.DECIMAL_DIGIT_NUMBER;
    }

    /**
     * Determines if a character is defined in Unicode.
     * <p>
     * A character is defined if at least one of the following is true:
     * <ul>
     * <li>It has an entry in the UnicodeData file.
     * <li>It has a value in a range defined by the UnicodeData file.
     * </ul>
     *
     * <p><b>Note:</b> This method cannot handle <a
     * href="#supplementary"> supplementary characters</a>. To support
     * all Unicode characters, including supplementary characters, use
     * the {@link #isDefined(int)} method.
     *
     * @param   ch   the character to be tested
     * @return  {@code true} if the character has a defined meaning
     *          in Unicode; {@code false} otherwise.
     * @see     Character#isDigit(char)
     * @see     Character#isLetter(char)
     * @see     Character#isLetterOrDigit(char)
     * @see     Character#isLowerCase(char)
     * @see     Character#isTitleCase(char)
     * @see     Character#isUpperCase(char)
     * @since   1.0.2
     */
    public static boolean isDefined(char ch) {
        return isDefined((int)ch);
    }

    /**
     * Determines if a character (Unicode code point) is defined in Unicode.
     * <p>
     * A character is defined if at least one of the following is true:
     * <ul>
     * <li>It has an entry in the UnicodeData file.
     * <li>It has a value in a range defined by the UnicodeData file.
     * </ul>
     *
     * @param   codePoint the character (Unicode code point) to be tested.
     * @return  {@code true} if the character has a defined meaning
     *          in Unicode; {@code false} otherwise.
     * @see     Character#isDigit(int)
     * @see     Character#isLetter(int)
     * @see     Character#isLetterOrDigit(int)
     * @see     Character#isLowerCase(int)
     * @see     Character#isTitleCase(int)
     * @see     Character#isUpperCase(int)
     * @since   1.5
     */
    public static boolean isDefined(int codePoint) {
        return getType(codePoint) != Character.UNASSIGNED;
    }

    /**
     * Determines if the specified character is a letter.
     * <p>
     * A character is considered to be a letter if its general
     * category type, provided by {@code Character.getType(ch)},
     * is any of the following:
     * <ul>
     * <li> {@code UPPERCASE_LETTER}
     * <li> {@code LOWERCASE_LETTER}
     * <li> {@code TITLECASE_LETTER}
     * <li> {@code MODIFIER_LETTER}
     * <li> {@code OTHER_LETTER}
     * </ul>
     *
     * Not all letters have case. Many characters are
     * letters but are neither uppercase nor lowercase nor titlecase.
     *
     * <p><b>Note:</b> This method cannot handle <a
     * href="#supplementary"> supplementary characters</a>. To support
     * all Unicode characters, including supplementary characters, use
     * the {@link #isLetter(int)} method.
     *
     * @param   ch   the character to be tested.
     * @return  {@code true} if the character is a letter;
     *          {@code false} otherwise.
     * @see     Character#isDigit(char)
     * @see     Character#isJavaIdentifierStart(char)
     * @see     Character#isJavaLetter(char)
     * @see     Character#isJavaLetterOrDigit(char)
     * @see     Character#isLetterOrDigit(char)
     * @see     Character#isLowerCase(char)
     * @see     Character#isTitleCase(char)
     * @see     Character#isUnicodeIdentifierStart(char)
     * @see     Character#isUpperCase(char)
     */
    public static boolean isLetter(char ch) {
        return isLetter((int)ch);
    }

    /**
     * Determines if the specified character (Unicode code point) is a letter.
     * <p>
     * A character is considered to be a letter if its general
     * category type, provided by {@link Character#getType(int) getType(codePoint)},
     * is any of the following:
     * <ul>
     * <li> {@code UPPERCASE_LETTER}
     * <li> {@code LOWERCASE_LETTER}
     * <li> {@code TITLECASE_LETTER}
     * <li> {@code MODIFIER_LETTER}
     * <li> {@code OTHER_LETTER}
     * </ul>
     *
     * Not all letters have case. Many characters are
     * letters but are neither uppercase nor lowercase nor titlecase.
     *
     * @param   codePoint the character (Unicode code point) to be tested.
     * @return  {@code true} if the character is a letter;
     *          {@code false} otherwise.
     * @see     Character#isDigit(int)
     * @see     Character#isJavaIdentifierStart(int)
     * @see     Character#isLetterOrDigit(int)
     * @see     Character#isLowerCase(int)
     * @see     Character#isTitleCase(int)
     * @see     Character#isUnicodeIdentifierStart(int)
     * @see     Character#isUpperCase(int)
     * @since   1.5
     */
    public static boolean isLetter(int codePoint) {
        return ((((1 << Character.UPPERCASE_LETTER) |
            (1 << Character.LOWERCASE_LETTER) |
            (1 << Character.TITLECASE_LETTER) |
            (1 << Character.MODIFIER_LETTER) |
            (1 << Character.OTHER_LETTER)) >> getType(codePoint)) & 1)
            != 0;
    }

    /**
     * Determines if the specified character is a letter or digit.
     * <p>
     * A character is considered to be a letter or digit if either
     * {@code Character.isLetter(char ch)} or
     * {@code Character.isDigit(char ch)} returns
     * {@code true} for the character.
     *
     * <p><b>Note:</b> This method cannot handle <a
     * href="#supplementary"> supplementary characters</a>. To support
     * all Unicode characters, including supplementary characters, use
     * the {@link #isLetterOrDigit(int)} method.
     *
     * @param   ch   the character to be tested.
     * @return  {@code true} if the character is a letter or digit;
     *          {@code false} otherwise.
     * @see     Character#isDigit(char)
     * @see     Character#isJavaIdentifierPart(char)
     * @see     Character#isJavaLetter(char)
     * @see     Character#isJavaLetterOrDigit(char)
     * @see     Character#isLetter(char)
     * @see     Character#isUnicodeIdentifierPart(char)
     * @since   1.0.2
     */
    public static boolean isLetterOrDigit(char ch) {
        return isLetterOrDigit((int)ch);
    }

    /**
     * Determines if the specified character (Unicode code point) is a letter or digit.
     * <p>
     * A character is considered to be a letter or digit if either
     * {@link #isLetter(int) isLetter(codePoint)} or
     * {@link #isDigit(int) isDigit(codePoint)} returns
     * {@code true} for the character.
     *
     * @param   codePoint the character (Unicode code point) to be tested.
     * @return  {@code true} if the character is a letter or digit;
     *          {@code false} otherwise.
     * @see     Character#isDigit(int)
     * @see     Character#isJavaIdentifierPart(int)
     * @see     Character#isLetter(int)
     * @see     Character#isUnicodeIdentifierPart(int)
     * @since   1.5
     */
    public static boolean isLetterOrDigit(int codePoint) {
        return ((((1 << Character.UPPERCASE_LETTER) |
            (1 << Character.LOWERCASE_LETTER) |
            (1 << Character.TITLECASE_LETTER) |
            (1 << Character.MODIFIER_LETTER) |
            (1 << Character.OTHER_LETTER) |
            (1 << Character.DECIMAL_DIGIT_NUMBER)) >> getType(codePoint)) & 1)
            != 0;
    }

    /**
     * Determines if the specified character is permissible as the first
     * character in a Java identifier.
     * <p>
     * A character may start a Java identifier if and only if
     * one of the following is true:
     * <ul>
     * <li> {@link #isLetter(char) isLetter(ch)} returns {@code true}
     * <li> {@link #getType(char) getType(ch)} returns {@code LETTER_NUMBER}
     * <li> {@code ch} is a currency symbol (such as {@code '$'})
     * <li> {@code ch} is a connecting punctuation character (such as {@code '_'}).
     * </ul>
     *
     * @param   ch the character to be tested.
     * @return  {@code true} if the character may start a Java
     *          identifier; {@code false} otherwise.
     * @see     Character#isJavaLetterOrDigit(char)
     * @see     Character#isJavaIdentifierStart(char)
     * @see     Character#isJavaIdentifierPart(char)
     * @see     Character#isLetter(char)
     * @see     Character#isLetterOrDigit(char)
     * @see     Character#isUnicodeIdentifierStart(char)
     * @since   1.0.2
     * @deprecated Replaced by isJavaIdentifierStart(char).
     */
    @Deprecated
    public static boolean isJavaLetter(char ch) {
        return isJavaIdentifierStart(ch);
    }

    /**
     * Determines if the specified character may be part of a Java
     * identifier as other than the first character.
     * <p>
     * A character may be part of a Java identifier if and only if any
     * of the following are true:
     * <ul>
     * <li>  it is a letter
     * <li>  it is a currency symbol (such as {@code '$'})
     * <li>  it is a connecting punctuation character (such as {@code '_'})
     * <li>  it is a digit
     * <li>  it is a numeric letter (such as a Roman numeral character)
     * <li>  it is a combining mark
     * <li>  it is a non-spacing mark
     * <li> {@code isIdentifierIgnorable} returns
     * {@code true} for the character.
     * </ul>
     *
     * @param   ch the character to be tested.
     * @return  {@code true} if the character may be part of a
     *          Java identifier; {@code false} otherwise.
     * @see     Character#isJavaLetter(char)
     * @see     Character#isJavaIdentifierStart(char)
     * @see     Character#isJavaIdentifierPart(char)
     * @see     Character#isLetter(char)
     * @see     Character#isLetterOrDigit(char)
     * @see     Character#isUnicodeIdentifierPart(char)
     * @see     Character#isIdentifierIgnorable(char)
     * @since   1.0.2
     * @deprecated Replaced by isJavaIdentifierPart(char).
     */
    @Deprecated
    public static boolean isJavaLetterOrDigit(char ch) {
        return isJavaIdentifierPart(ch);
    }

    /**
     * Determines if the specified character (Unicode code point) is an alphabet.
     * <p>
     * A character is considered to be alphabetic if its general category type,
     * provided by {@link Character#getType(int) getType(codePoint)}, is any of
     * the following:
     * <ul>
     * <li> <code>UPPERCASE_LETTER</code>
     * <li> <code>LOWERCASE_LETTER</code>
     * <li> <code>TITLECASE_LETTER</code>
     * <li> <code>MODIFIER_LETTER</code>
     * <li> <code>OTHER_LETTER</code>
     * <li> <code>LETTER_NUMBER</code>
     * </ul>
     * or it has contributory property Other_Alphabetic as defined by the
     * Unicode Standard.
     *
     * @param   codePoint the character (Unicode code point) to be tested.
     * @return  <code>true</code> if the character is a Unicode alphabet
     *          character, <code>false</code> otherwise.
     * @since   1.7
     */
    public static boolean isAlphabetic(int codePoint) {
        return (((((1 << Character.UPPERCASE_LETTER) |
            (1 << Character.LOWERCASE_LETTER) |
            (1 << Character.TITLECASE_LETTER) |
            (1 << Character.MODIFIER_LETTER) |
            (1 << Character.OTHER_LETTER) |
            (1 << Character.LETTER_NUMBER)) >> getType(codePoint)) & 1) != 0) ||
            CharacterData.of(codePoint).isOtherAlphabetic(codePoint);
    }

    /**
     * Determines if the specified character (Unicode code point) is a CJKV
     * (Chinese, Japanese, Korean and Vietnamese) ideograph, as defined by
     * the Unicode Standard.
     *
     * @param   codePoint the character (Unicode code point) to be tested.
     * @return  <code>true</code> if the character is a Unicode ideograph
     *          character, <code>false</code> otherwise.
     * @since   1.7
     */
    public static boolean isIdeographic(int codePoint) {
        return CharacterData.of(codePoint).isIdeographic(codePoint);
    }

    /**
     * Determines if the specified character is
     * permissible as the first character in a Java identifier.
     * <p>
     * A character may start a Java identifier if and only if
     * one of the following conditions is true:
     * <ul>
     * <li> {@link #isLetter(char) isLetter(ch)} returns {@code true}
     * <li> {@link #getType(char) getType(ch)} returns {@code LETTER_NUMBER}
     * <li> {@code ch} is a currency symbol (such as {@code '$'})
     * <li> {@code ch} is a connecting punctuation character (such as {@code '_'}).
     * </ul>
     *
     * <p><b>Note:</b> This method cannot handle <a
     * href="#supplementary"> supplementary characters</a>. To support
     * all Unicode characters, including supplementary characters, use
     * the {@link #isJavaIdentifierStart(int)} method.
     *
     * @param   ch the character to be tested.
     * @return  {@code true} if the character may start a Java identifier;
     *          {@code false} otherwise.
     * @see     Character#isJavaIdentifierPart(char)
     * @see     Character#isLetter(char)
     * @see     Character#isUnicodeIdentifierStart(char)
     * @see     javax.lang.model.SourceVersion#isIdentifier(CharSequence)
     * @since   1.1
     */
    public static boolean isJavaIdentifierStart(char ch) {
        return isJavaIdentifierStart((int)ch);
    }

    /**
     * Determines if the character (Unicode code point) is
     * permissible as the first character in a Java identifier.
     * <p>
     * A character may start a Java identifier if and only if
     * one of the following conditions is true:
     * <ul>
     * <li> {@link #isLetter(int) isLetter(codePoint)}
     *      returns {@code true}
     * <li> {@link #getType(int) getType(codePoint)}
     *      returns {@code LETTER_NUMBER}
     * <li> the referenced character is a currency symbol (such as {@code '$'})
     * <li> the referenced character is a connecting punctuation character
     *      (such as {@code '_'}).
     * </ul>
     *
     * @param   codePoint the character (Unicode code point) to be tested.
     * @return  {@code true} if the character may start a Java identifier;
     *          {@code false} otherwise.
     * @see     Character#isJavaIdentifierPart(int)
     * @see     Character#isLetter(int)
     * @see     Character#isUnicodeIdentifierStart(int)
     * @see     javax.lang.model.SourceVersion#isIdentifier(CharSequence)
     * @since   1.5
     */
    public static boolean isJavaIdentifierStart(int codePoint) {
        return CharacterData.of(codePoint).isJavaIdentifierStart(codePoint);
    }

    /**
     * Determines if the specified character may be part of a Java
     * identifier as other than the first character.
     * <p>
     * A character may be part of a Java identifier if any of the following
     * are true:
     * <ul>
     * <li>  it is a letter
     * <li>  it is a currency symbol (such as {@code '$'})
     * <li>  it is a connecting punctuation character (such as {@code '_'})
     * <li>  it is a digit
     * <li>  it is a numeric letter (such as a Roman numeral character)
     * <li>  it is a combining mark
     * <li>  it is a non-spacing mark
     * <li> {@code isIdentifierIgnorable} returns
     * {@code true} for the character
     * </ul>
     *
     * <p><b>Note:</b> This method cannot handle <a
     * href="#supplementary"> supplementary characters</a>. To support
     * all Unicode characters, including supplementary characters, use
     * the {@link #isJavaIdentifierPart(int)} method.
     *
     * @param   ch      the character to be tested.
     * @return {@code true} if the character may be part of a
     *          Java identifier; {@code false} otherwise.
     * @see     Character#isIdentifierIgnorable(char)
     * @see     Character#isJavaIdentifierStart(char)
     * @see     Character#isLetterOrDigit(char)
     * @see     Character#isUnicodeIdentifierPart(char)
     * @see     javax.lang.model.SourceVersion#isIdentifier(CharSequence)
     * @since   1.1
     */
    public static boolean isJavaIdentifierPart(char ch) {
        return isJavaIdentifierPart((int)ch);
    }

    /**
     * Determines if the character (Unicode code point) may be part of a Java
     * identifier as other than the first character.
     * <p>
     * A character may be part of a Java identifier if any of the following
     * are true:
     * <ul>
     * <li>  it is a letter
     * <li>  it is a currency symbol (such as {@code '$'})
     * <li>  it is a connecting punctuation character (such as {@code '_'})
     * <li>  it is a digit
     * <li>  it is a numeric letter (such as a Roman numeral character)
     * <li>  it is a combining mark
     * <li>  it is a non-spacing mark
     * <li> {@link #isIdentifierIgnorable(int)
     * isIdentifierIgnorable(codePoint)} returns {@code true} for
     * the character
     * </ul>
     *
     * @param   codePoint the character (Unicode code point) to be tested.
     * @return {@code true} if the character may be part of a
     *          Java identifier; {@code false} otherwise.
     * @see     Character#isIdentifierIgnorable(int)
     * @see     Character#isJavaIdentifierStart(int)
     * @see     Character#isLetterOrDigit(int)
     * @see     Character#isUnicodeIdentifierPart(int)
     * @see     javax.lang.model.SourceVersion#isIdentifier(CharSequence)
     * @since   1.5
     */
    public static boolean isJavaIdentifierPart(int codePoint) {
        return CharacterData.of(codePoint).isJavaIdentifierPart(codePoint);
    }

    /**
     * Determines if the specified character is permissible as the
     * first character in a Unicode identifier.
     * <p>
     * A character may start a Unicode identifier if and only if
     * one of the following conditions is true:
     * <ul>
     * <li> {@link #isLetter(char) isLetter(ch)} returns {@code true}
     * <li> {@link #getType(char) getType(ch)} returns
     *      {@code LETTER_NUMBER}.
     * </ul>
     *
     * <p><b>Note:</b> This method cannot handle <a
     * href="#supplementary"> supplementary characters</a>. To support
     * all Unicode characters, including supplementary characters, use
     * the {@link #isUnicodeIdentifierStart(int)} method.
     *
     * @param   ch      the character to be tested.
     * @return  {@code true} if the character may start a Unicode
     *          identifier; {@code false} otherwise.
     * @see     Character#isJavaIdentifierStart(char)
     * @see     Character#isLetter(char)
     * @see     Character#isUnicodeIdentifierPart(char)
     * @since   1.1
     */
    public static boolean isUnicodeIdentifierStart(char ch) {
        return isUnicodeIdentifierStart((int)ch);
    }

    /**
     * Determines if the specified character (Unicode code point) is permissible as the
     * first character in a Unicode identifier.
     * <p>
     * A character may start a Unicode identifier if and only if
     * one of the following conditions is true:
     * <ul>
     * <li> {@link #isLetter(int) isLetter(codePoint)}
     *      returns {@code true}
     * <li> {@link #getType(int) getType(codePoint)}
     *      returns {@code LETTER_NUMBER}.
     * </ul>
     * @param   codePoint the character (Unicode code point) to be tested.
     * @return  {@code true} if the character may start a Unicode
     *          identifier; {@code false} otherwise.
     * @see     Character#isJavaIdentifierStart(int)
     * @see     Character#isLetter(int)
     * @see     Character#isUnicodeIdentifierPart(int)
     * @since   1.5
     */
    public static boolean isUnicodeIdentifierStart(int codePoint) {
        return CharacterData.of(codePoint).isUnicodeIdentifierStart(codePoint);
    }

    /**
     * Determines if the specified character may be part of a Unicode
     * identifier as other than the first character.
     * <p>
     * A character may be part of a Unicode identifier if and only if
     * one of the following statements is true:
     * <ul>
     * <li>  it is a letter
     * <li>  it is a connecting punctuation character (such as {@code '_'})
     * <li>  it is a digit
     * <li>  it is a numeric letter (such as a Roman numeral character)
     * <li>  it is a combining mark
     * <li>  it is a non-spacing mark
     * <li> {@code isIdentifierIgnorable} returns
     * {@code true} for this character.
     * </ul>
     *
     * <p><b>Note:</b> This method cannot handle <a
     * href="#supplementary"> supplementary characters</a>. To support
     * all Unicode characters, including supplementary characters, use
     * the {@link #isUnicodeIdentifierPart(int)} method.
     *
     * @param   ch      the character to be tested.
     * @return  {@code true} if the character may be part of a
     *          Unicode identifier; {@code false} otherwise.
     * @see     Character#isIdentifierIgnorable(char)
     * @see     Character#isJavaIdentifierPart(char)
     * @see     Character#isLetterOrDigit(char)
     * @see     Character#isUnicodeIdentifierStart(char)
     * @since   1.1
     */
    public static boolean isUnicodeIdentifierPart(char ch) {
        return isUnicodeIdentifierPart((int)ch);
    }

    /**
     * Determines if the specified character (Unicode code point) may be part of a Unicode
     * identifier as other than the first character.
     * <p>
     * A character may be part of a Unicode identifier if and only if
     * one of the following statements is true:
     * <ul>
     * <li>  it is a letter
     * <li>  it is a connecting punctuation character (such as {@code '_'})
     * <li>  it is a digit
     * <li>  it is a numeric letter (such as a Roman numeral character)
     * <li>  it is a combining mark
     * <li>  it is a non-spacing mark
     * <li> {@code isIdentifierIgnorable} returns
     * {@code true} for this character.
     * </ul>
     * @param   codePoint the character (Unicode code point) to be tested.
     * @return  {@code true} if the character may be part of a
     *          Unicode identifier; {@code false} otherwise.
     * @see     Character#isIdentifierIgnorable(int)
     * @see     Character#isJavaIdentifierPart(int)
     * @see     Character#isLetterOrDigit(int)
     * @see     Character#isUnicodeIdentifierStart(int)
     * @since   1.5
     */
    public static boolean isUnicodeIdentifierPart(int codePoint) {
        return CharacterData.of(codePoint).isUnicodeIdentifierPart(codePoint);
    }

    /**
     * Determines if the specified character should be regarded as
     * an ignorable character in a Java identifier or a Unicode identifier.
     * <p>
     * The following Unicode characters are ignorable in a Java identifier
     * or a Unicode identifier:
     * <ul>
     * <li>ISO control characters that are not whitespace
     * <ul>
     * <li>{@code '\u005Cu0000'} through {@code '\u005Cu0008'}
     * <li>{@code '\u005Cu000E'} through {@code '\u005Cu001B'}
     * <li>{@code '\u005Cu007F'} through {@code '\u005Cu009F'}
     * </ul>
     *
     * <li>all characters that have the {@code FORMAT} general
     * category value
     * </ul>
     *
     * <p><b>Note:</b> This method cannot handle <a
     * href="#supplementary"> supplementary characters</a>. To support
     * all Unicode characters, including supplementary characters, use
     * the {@link #isIdentifierIgnorable(int)} method.
     *
     * @param   ch      the character to be tested.
     * @return  {@code true} if the character is an ignorable control
     *          character that may be part of a Java or Unicode identifier;
     *           {@code false} otherwise.
     * @see     Character#isJavaIdentifierPart(char)
     * @see     Character#isUnicodeIdentifierPart(char)
     * @since   1.1
     */
    public static boolean isIdentifierIgnorable(char ch) {
        return isIdentifierIgnorable((int)ch);
    }

    /**
     * Determines if the specified character (Unicode code point) should be regarded as
     * an ignorable character in a Java identifier or a Unicode identifier.
     * <p>
     * The following Unicode characters are ignorable in a Java identifier
     * or a Unicode identifier:
     * <ul>
     * <li>ISO control characters that are not whitespace
     * <ul>
     * <li>{@code '\u005Cu0000'} through {@code '\u005Cu0008'}
     * <li>{@code '\u005Cu000E'} through {@code '\u005Cu001B'}
     * <li>{@code '\u005Cu007F'} through {@code '\u005Cu009F'}
     * </ul>
     *
     * <li>all characters that have the {@code FORMAT} general
     * category value
     * </ul>
     *
     * @param   codePoint the character (Unicode code point) to be tested.
     * @return  {@code true} if the character is an ignorable control
     *          character that may be part of a Java or Unicode identifier;
     *          {@code false} otherwise.
     * @see     Character#isJavaIdentifierPart(int)
     * @see     Character#isUnicodeIdentifierPart(int)
     * @since   1.5
     */
    public static boolean isIdentifierIgnorable(int codePoint) {
        return CharacterData.of(codePoint).isIdentifierIgnorable(codePoint);
    }

    /**
     * Converts the character argument to lowercase using case
     * mapping information from the UnicodeData file.
     * <p>
     * Note that
     * {@code Character.isLowerCase(Character.toLowerCase(ch))}
     * does not always return {@code true} for some ranges of
     * characters, particularly those that are symbols or ideographs.
     *
     * <p>In general, {@link String#toLowerCase()} should be used to map
     * characters to lowercase. {@code String} case mapping methods
     * have several benefits over {@code Character} case mapping methods.
     * {@code String} case mapping methods can perform locale-sensitive
     * mappings, context-sensitive mappings, and 1:M character mappings, whereas
     * the {@code Character} case mapping methods cannot.
     *
     * <p><b>Note:</b> This method cannot handle <a
     * href="#supplementary"> supplementary characters</a>. To support
     * all Unicode characters, including supplementary characters, use
     * the {@link #toLowerCase(int)} method.
     *
     * @param   ch   the character to be converted.
     * @return  the lowercase equivalent of the character, if any;
     *          otherwise, the character itself.
     * @see     Character#isLowerCase(char)
     * @see     String#toLowerCase()
     */
    public static char toLowerCase(char ch) {
        return (char)toLowerCase((int)ch);
    }

    /**
     * Converts the character (Unicode code point) argument to
     * lowercase using case mapping information from the UnicodeData
     * file.
     *
     * <p> Note that
     * {@code Character.isLowerCase(Character.toLowerCase(codePoint))}
     * does not always return {@code true} for some ranges of
     * characters, particularly those that are symbols or ideographs.
     *
     * <p>In general, {@link String#toLowerCase()} should be used to map
     * characters to lowercase. {@code String} case mapping methods
     * have several benefits over {@code Character} case mapping methods.
     * {@code String} case mapping methods can perform locale-sensitive
     * mappings, context-sensitive mappings, and 1:M character mappings, whereas
     * the {@code Character} case mapping methods cannot.
     *
     * @param   codePoint   the character (Unicode code point) to be converted.
     * @return  the lowercase equivalent of the character (Unicode code
     *          point), if any; otherwise, the character itself.
     * @see     Character#isLowerCase(int)
     * @see     String#toLowerCase()
     *
     * @since   1.5
     */
    public static int toLowerCase(int codePoint) {
        return CharacterData.of(codePoint).toLowerCase(codePoint);
    }

    /**
     * Converts the character argument to uppercase using case mapping
     * information from the UnicodeData file.
     * <p>
     * Note that
     * {@code Character.isUpperCase(Character.toUpperCase(ch))}
     * does not always return {@code true} for some ranges of
     * characters, particularly those that are symbols or ideographs.
     *
     * <p>In general, {@link String#toUpperCase()} should be used to map
     * characters to uppercase. {@code String} case mapping methods
     * have several benefits over {@code Character} case mapping methods.
     * {@code String} case mapping methods can perform locale-sensitive
     * mappings, context-sensitive mappings, and 1:M character mappings, whereas
     * the {@code Character} case mapping methods cannot.
     *
     * <p><b>Note:</b> This method cannot handle <a
     * href="#supplementary"> supplementary characters</a>. To support
     * all Unicode characters, including supplementary characters, use
     * the {@link #toUpperCase(int)} method.
     *
     * @param   ch   the character to be converted.
     * @return  the uppercase equivalent of the character, if any;
     *          otherwise, the character itself.
     * @see     Character#isUpperCase(char)
     * @see     String#toUpperCase()
     */
    public static char toUpperCase(char ch) {
        return (char)toUpperCase((int)ch);
    }

    /**
     * Converts the character (Unicode code point) argument to
     * uppercase using case mapping information from the UnicodeData
     * file.
     *
     * <p>Note that
     * {@code Character.isUpperCase(Character.toUpperCase(codePoint))}
     * does not always return {@code true} for some ranges of
     * characters, particularly those that are symbols or ideographs.
     *
     * <p>In general, {@link String#toUpperCase()} should be used to map
     * characters to uppercase. {@code String} case mapping methods
     * have several benefits over {@code Character} case mapping methods.
     * {@code String} case mapping methods can perform locale-sensitive
     * mappings, context-sensitive mappings, and 1:M character mappings, whereas
     * the {@code Character} case mapping methods cannot.
     *
     * @param   codePoint   the character (Unicode code point) to be converted.
     * @return  the uppercase equivalent of the character, if any;
     *          otherwise, the character itself.
     * @see     Character#isUpperCase(int)
     * @see     String#toUpperCase()
     *
     * @since   1.5
     */
    public static int toUpperCase(int codePoint) {
        return CharacterData.of(codePoint).toUpperCase(codePoint);
    }

    /**
     * Converts the character argument to titlecase using case mapping
     * information from the UnicodeData file. If a character has no
     * explicit titlecase mapping and is not itself a titlecase char
     * according to UnicodeData, then the uppercase mapping is
     * returned as an equivalent titlecase mapping. If the
     * {@code char} argument is already a titlecase
     * {@code char}, the same {@code char} value will be
     * returned.
     * <p>
     * Note that
     * {@code Character.isTitleCase(Character.toTitleCase(ch))}
     * does not always return {@code true} for some ranges of
     * characters.
     *
     * <p><b>Note:</b> This method cannot handle <a
     * href="#supplementary"> supplementary characters</a>. To support
     * all Unicode characters, including supplementary characters, use
     * the {@link #toTitleCase(int)} method.
     *
     * @param   ch   the character to be converted.
     * @return  the titlecase equivalent of the character, if any;
     *          otherwise, the character itself.
     * @see     Character#isTitleCase(char)
     * @see     Character#toLowerCase(char)
     * @see     Character#toUpperCase(char)
     * @since   1.0.2
     */
    public static char toTitleCase(char ch) {
        return (char)toTitleCase((int)ch);
    }

    /**
     * Converts the character (Unicode code point) argument to titlecase using case mapping
     * information from the UnicodeData file. If a character has no
     * explicit titlecase mapping and is not itself a titlecase char
     * according to UnicodeData, then the uppercase mapping is
     * returned as an equivalent titlecase mapping. If the
     * character argument is already a titlecase
     * character, the same character value will be
     * returned.
     *
     * <p>Note that
     * {@code Character.isTitleCase(Character.toTitleCase(codePoint))}
     * does not always return {@code true} for some ranges of
     * characters.
     *
     * @param   codePoint   the character (Unicode code point) to be converted.
     * @return  the titlecase equivalent of the character, if any;
     *          otherwise, the character itself.
     * @see     Character#isTitleCase(int)
     * @see     Character#toLowerCase(int)
     * @see     Character#toUpperCase(int)
     * @since   1.5
     */
    public static int toTitleCase(int codePoint) {
        return CharacterData.of(codePoint).toTitleCase(codePoint);
    }

    /**
     * Returns the numeric value of the character {@code ch} in the
     * specified radix.
     * <p>
     * If the radix is not in the range {@code MIN_RADIX} &le;
     * {@code radix} &le; {@code MAX_RADIX} or if the
     * value of {@code ch} is not a valid digit in the specified
     * radix, {@code -1} is returned. A character is a valid digit
     * if at least one of the following is true:
     * <ul>
     * <li>The method {@code isDigit} is {@code true} of the character
     *     and the Unicode decimal digit value of the character (or its
     *     single-character decomposition) is less than the specified radix.
     *     In this case the decimal digit value is returned.
     * <li>The character is one of the uppercase Latin letters
     *     {@code 'A'} through {@code 'Z'} and its code is less than
     *     {@code radix + 'A' - 10}.
     *     In this case, {@code ch - 'A' + 10}
     *     is returned.
     * <li>The character is one of the lowercase Latin letters
     *     {@code 'a'} through {@code 'z'} and its code is less than
     *     {@code radix + 'a' - 10}.
     *     In this case, {@code ch - 'a' + 10}
     *     is returned.
     * <li>The character is one of the fullwidth uppercase Latin letters A
     *     ({@code '\u005CuFF21'}) through Z ({@code '\u005CuFF3A'})
     *     and its code is less than
     *     {@code radix + '\u005CuFF21' - 10}.
     *     In this case, {@code ch - '\u005CuFF21' + 10}
     *     is returned.
     * <li>The character is one of the fullwidth lowercase Latin letters a
     *     ({@code '\u005CuFF41'}) through z ({@code '\u005CuFF5A'})
     *     and its code is less than
     *     {@code radix + '\u005CuFF41' - 10}.
     *     In this case, {@code ch - '\u005CuFF41' + 10}
     *     is returned.
     * </ul>
     *
     * <p><b>Note:</b> This method cannot handle <a
     * href="#supplementary"> supplementary characters</a>. To support
     * all Unicode characters, including supplementary characters, use
     * the {@link #digit(int, int)} method.
     *
     * @param   ch      the character to be converted.
     * @param   radix   the radix.
     * @return  the numeric value represented by the character in the
     *          specified radix.
     * @see     Character#forDigit(int, int)
     * @see     Character#isDigit(char)
     */
    public static int digit(char ch, int radix) {
        return digit((int)ch, radix);
    }

    /**
     * Returns the numeric value of the specified character (Unicode
     * code point) in the specified radix.
     *
     * <p>If the radix is not in the range {@code MIN_RADIX} &le;
     * {@code radix} &le; {@code MAX_RADIX} or if the
     * character is not a valid digit in the specified
     * radix, {@code -1} is returned. A character is a valid digit
     * if at least one of the following is true:
     * <ul>
     * <li>The method {@link #isDigit(int) isDigit(codePoint)} is {@code true} of the character
     *     and the Unicode decimal digit value of the character (or its
     *     single-character decomposition) is less than the specified radix.
     *     In this case the decimal digit value is returned.
     * <li>The character is one of the uppercase Latin letters
     *     {@code 'A'} through {@code 'Z'} and its code is less than
     *     {@code radix + 'A' - 10}.
     *     In this case, {@code codePoint - 'A' + 10}
     *     is returned.
     * <li>The character is one of the lowercase Latin letters
     *     {@code 'a'} through {@code 'z'} and its code is less than
     *     {@code radix + 'a' - 10}.
     *     In this case, {@code codePoint - 'a' + 10}
     *     is returned.
     * <li>The character is one of the fullwidth uppercase Latin letters A
     *     ({@code '\u005CuFF21'}) through Z ({@code '\u005CuFF3A'})
     *     and its code is less than
     *     {@code radix + '\u005CuFF21' - 10}.
     *     In this case,
     *     {@code codePoint - '\u005CuFF21' + 10}
     *     is returned.
     * <li>The character is one of the fullwidth lowercase Latin letters a
     *     ({@code '\u005CuFF41'}) through z ({@code '\u005CuFF5A'})
     *     and its code is less than
     *     {@code radix + '\u005CuFF41'- 10}.
     *     In this case,
     *     {@code codePoint - '\u005CuFF41' + 10}
     *     is returned.
     * </ul>
     *
     * @param   codePoint the character (Unicode code point) to be converted.
     * @param   radix   the radix.
     * @return  the numeric value represented by the character in the
     *          specified radix.
     * @see     Character#forDigit(int, int)
     * @see     Character#isDigit(int)
     * @since   1.5
     */
    public static int digit(int codePoint, int radix) {
        return CharacterData.of(codePoint).digit(codePoint, radix);
    }

    /**
     * Returns the {@code int} value that the specified Unicode
     * character represents. For example, the character
     * {@code '\u005Cu216C'} (the roman numeral fifty) will return
     * an int with a value of 50.
     * <p>
     * The letters A-Z in their uppercase ({@code '\u005Cu0041'} through
     * {@code '\u005Cu005A'}), lowercase
     * ({@code '\u005Cu0061'} through {@code '\u005Cu007A'}), and
     * full width variant ({@code '\u005CuFF21'} through
     * {@code '\u005CuFF3A'} and {@code '\u005CuFF41'} through
     * {@code '\u005CuFF5A'}) forms have numeric values from 10
     * through 35. This is independent of the Unicode specification,
     * which does not assign numeric values to these {@code char}
     * values.
     * <p>
     * If the character does not have a numeric value, then -1 is returned.
     * If the character has a numeric value that cannot be represented as a
     * nonnegative integer (for example, a fractional value), then -2
     * is returned.
     *
     * <p><b>Note:</b> This method cannot handle <a
     * href="#supplementary"> supplementary characters</a>. To support
     * all Unicode characters, including supplementary characters, use
     * the {@link #getNumericValue(int)} method.
     *
     * @param   ch      the character to be converted.
     * @return  the numeric value of the character, as a nonnegative {@code int}
     *           value; -2 if the character has a numeric value that is not a
     *          nonnegative integer; -1 if the character has no numeric value.
     * @see     Character#forDigit(int, int)
     * @see     Character#isDigit(char)
     * @since   1.1
     */
    public static int getNumericValue(char ch) {
        return getNumericValue((int)ch);
    }

    /**
     * Returns the {@code int} value that the specified
     * character (Unicode code point) represents. For example, the character
     * {@code '\u005Cu216C'} (the Roman numeral fifty) will return
     * an {@code int} with a value of 50.
     * <p>
     * The letters A-Z in their uppercase ({@code '\u005Cu0041'} through
     * {@code '\u005Cu005A'}), lowercase
     * ({@code '\u005Cu0061'} through {@code '\u005Cu007A'}), and
     * full width variant ({@code '\u005CuFF21'} through
     * {@code '\u005CuFF3A'} and {@code '\u005CuFF41'} through
     * {@code '\u005CuFF5A'}) forms have numeric values from 10
     * through 35. This is independent of the Unicode specification,
     * which does not assign numeric values to these {@code char}
     * values.
     * <p>
     * If the character does not have a numeric value, then -1 is returned.
     * If the character has a numeric value that cannot be represented as a
     * nonnegative integer (for example, a fractional value), then -2
     * is returned.
     *
     * @param   codePoint the character (Unicode code point) to be converted.
     * @return  the numeric value of the character, as a nonnegative {@code int}
     *          value; -2 if the character has a numeric value that is not a
     *          nonnegative integer; -1 if the character has no numeric value.
     * @see     Character#forDigit(int, int)
     * @see     Character#isDigit(int)
     * @since   1.5
     */
    public static int getNumericValue(int codePoint) {
        return CharacterData.of(codePoint).getNumericValue(codePoint);
    }

    /**
     * Determines if the specified character is ISO-LATIN-1 white space.
     * This method returns {@code true} for the following five
     * characters only:
     * <table summary="truechars">
     * <tr><td>{@code '\t'}</td>            <td>{@code U+0009}</td>
     *     <td>{@code HORIZONTAL TABULATION}</td></tr>
     * <tr><td>{@code '\n'}</td>            <td>{@code U+000A}</td>
     *     <td>{@code NEW LINE}</td></tr>
     * <tr><td>{@code '\f'}</td>            <td>{@code U+000C}</td>
     *     <td>{@code FORM FEED}</td></tr>
     * <tr><td>{@code '\r'}</td>            <td>{@code U+000D}</td>
     *     <td>{@code CARRIAGE RETURN}</td></tr>
     * <tr><td>{@code '&nbsp;'}</td>  <td>{@code U+0020}</td>
     *     <td>{@code SPACE}</td></tr>
     * </table>
     *
     * @param      ch   the character to be tested.
     * @return     {@code true} if the character is ISO-LATIN-1 white
     *             space; {@code false} otherwise.
     * @see        Character#isSpaceChar(char)
     * @see        Character#isWhitespace(char)
     * @deprecated Replaced by isWhitespace(char).
     */
    @Deprecated
    public static boolean isSpace(char ch) {
        return (ch <= 0x0020) &&
            (((((1L << 0x0009) |
            (1L << 0x000A) |
            (1L << 0x000C) |
            (1L << 0x000D) |
            (1L << 0x0020)) >> ch) & 1L) != 0);
    }


    /**
     * Determines if the specified character is a Unicode space character.
     * A character is considered to be a space character if and only if
     * it is specified to be a space character by the Unicode Standard. This
     * method returns true if the character's general category type is any of
     * the following:
     * <ul>
     * <li> {@code SPACE_SEPARATOR}
     * <li> {@code LINE_SEPARATOR}
     * <li> {@code PARAGRAPH_SEPARATOR}
     * </ul>
     *
     * <p><b>Note:</b> This method cannot handle <a
     * href="#supplementary"> supplementary characters</a>. To support
     * all Unicode characters, including supplementary characters, use
     * the {@link #isSpaceChar(int)} method.
     *
     * @param   ch      the character to be tested.
     * @return  {@code true} if the character is a space character;
     *          {@code false} otherwise.
     * @see     Character#isWhitespace(char)
     * @since   1.1
     */
    public static boolean isSpaceChar(char ch) {
        return isSpaceChar((int)ch);
    }

    /**
     * Determines if the specified character (Unicode code point) is a
     * Unicode space character.  A character is considered to be a
     * space character if and only if it is specified to be a space
     * character by the Unicode Standard. This method returns true if
     * the character's general category type is any of the following:
     *
     * <ul>
     * <li> {@link #SPACE_SEPARATOR}
     * <li> {@link #LINE_SEPARATOR}
     * <li> {@link #PARAGRAPH_SEPARATOR}
     * </ul>
     *
     * @param   codePoint the character (Unicode code point) to be tested.
     * @return  {@code true} if the character is a space character;
     *          {@code false} otherwise.
     * @see     Character#isWhitespace(int)
     * @since   1.5
     */
    public static boolean isSpaceChar(int codePoint) {
        return ((((1 << Character.SPACE_SEPARATOR) |
                  (1 << Character.LINE_SEPARATOR) |
                  (1 << Character.PARAGRAPH_SEPARATOR)) >> getType(codePoint)) & 1)
            != 0;
    }

    /**
     * Determines if the specified character is white space according to Java.
     * A character is a Java whitespace character if and only if it satisfies
     * one of the following criteria:
     * <ul>
     * <li> It is a Unicode space character ({@code SPACE_SEPARATOR},
     *      {@code LINE_SEPARATOR}, or {@code PARAGRAPH_SEPARATOR})
     *      but is not also a non-breaking space ({@code '\u005Cu00A0'},
     *      {@code '\u005Cu2007'}, {@code '\u005Cu202F'}).
     * <li> It is {@code '\u005Ct'}, U+0009 HORIZONTAL TABULATION.
     * <li> It is {@code '\u005Cn'}, U+000A LINE FEED.
     * <li> It is {@code '\u005Cu000B'}, U+000B VERTICAL TABULATION.
     * <li> It is {@code '\u005Cf'}, U+000C FORM FEED.
     * <li> It is {@code '\u005Cr'}, U+000D CARRIAGE RETURN.
     * <li> It is {@code '\u005Cu001C'}, U+001C FILE SEPARATOR.
     * <li> It is {@code '\u005Cu001D'}, U+001D GROUP SEPARATOR.
     * <li> It is {@code '\u005Cu001E'}, U+001E RECORD SEPARATOR.
     * <li> It is {@code '\u005Cu001F'}, U+001F UNIT SEPARATOR.
     * </ul>
     *
     * <p><b>Note:</b> This method cannot handle <a
     * href="#supplementary"> supplementary characters</a>. To support
     * all Unicode characters, including supplementary characters, use
     * the {@link #isWhitespace(int)} method.
     *
     * @param   ch the character to be tested.
     * @return  {@code true} if the character is a Java whitespace
     *          character; {@code false} otherwise.
     * @see     Character#isSpaceChar(char)
     * @since   1.1
     */
    public static boolean isWhitespace(char ch) {
        return isWhitespace((int)ch);
    }

    /**
     * Determines if the specified character (Unicode code point) is
     * white space according to Java.  A character is a Java
     * whitespace character if and only if it satisfies one of the
     * following criteria:
     * <ul>
     * <li> It is a Unicode space character ({@link #SPACE_SEPARATOR},
     *      {@link #LINE_SEPARATOR}, or {@link #PARAGRAPH_SEPARATOR})
     *      but is not also a non-breaking space ({@code '\u005Cu00A0'},
     *      {@code '\u005Cu2007'}, {@code '\u005Cu202F'}).
     * <li> It is {@code '\u005Ct'}, U+0009 HORIZONTAL TABULATION.
     * <li> It is {@code '\u005Cn'}, U+000A LINE FEED.
     * <li> It is {@code '\u005Cu000B'}, U+000B VERTICAL TABULATION.
     * <li> It is {@code '\u005Cf'}, U+000C FORM FEED.
     * <li> It is {@code '\u005Cr'}, U+000D CARRIAGE RETURN.
     * <li> It is {@code '\u005Cu001C'}, U+001C FILE SEPARATOR.
     * <li> It is {@code '\u005Cu001D'}, U+001D GROUP SEPARATOR.
     * <li> It is {@code '\u005Cu001E'}, U+001E RECORD SEPARATOR.
     * <li> It is {@code '\u005Cu001F'}, U+001F UNIT SEPARATOR.
     * </ul>
     *
     * @param   codePoint the character (Unicode code point) to be tested.
     * @return  {@code true} if the character is a Java whitespace
     *          character; {@code false} otherwise.
     * @see     Character#isSpaceChar(int)
     * @since   1.5
     */
    public static boolean isWhitespace(int codePoint) {
        return CharacterData.of(codePoint).isWhitespace(codePoint);
    }

    /**
     * Determines if the specified character is an ISO control
     * character.  A character is considered to be an ISO control
     * character if its code is in the range {@code '\u005Cu0000'}
     * through {@code '\u005Cu001F'} or in the range
     * {@code '\u005Cu007F'} through {@code '\u005Cu009F'}.
     *
     * <p><b>Note:</b> This method cannot handle <a
     * href="#supplementary"> supplementary characters</a>. To support
     * all Unicode characters, including supplementary characters, use
     * the {@link #isISOControl(int)} method.
     *
     * @param   ch      the character to be tested.
     * @return  {@code true} if the character is an ISO control character;
     *          {@code false} otherwise.
     *
     * @see     Character#isSpaceChar(char)
     * @see     Character#isWhitespace(char)
     * @since   1.1
     */
    public static boolean isISOControl(char ch) {
        return isISOControl((int)ch);
    }

    /**
     * Determines if the referenced character (Unicode code point) is an ISO control
     * character.  A character is considered to be an ISO control
     * character if its code is in the range {@code '\u005Cu0000'}
     * through {@code '\u005Cu001F'} or in the range
     * {@code '\u005Cu007F'} through {@code '\u005Cu009F'}.
     *
     * @param   codePoint the character (Unicode code point) to be tested.
     * @return  {@code true} if the character is an ISO control character;
     *          {@code false} otherwise.
     * @see     Character#isSpaceChar(int)
     * @see     Character#isWhitespace(int)
     * @since   1.5
     */
    public static boolean isISOControl(int codePoint) {
        // Optimized form of:
        //     (codePoint >= 0x00 && codePoint <= 0x1F) ||
        //     (codePoint >= 0x7F && codePoint <= 0x9F);
        return codePoint <= 0x9F &&
            (codePoint >= 0x7F || (codePoint >>> 5 == 0));
    }

    /**
     * Returns a value indicating a character's general category.
     *
     * <p><b>Note:</b> This method cannot handle <a
     * href="#supplementary"> supplementary characters</a>. To support
     * all Unicode characters, including supplementary characters, use
     * the {@link #getType(int)} method.
     *
     * @param   ch      the character to be tested.
     * @return  a value of type {@code int} representing the
     *          character's general category.
     * @see     Character#COMBINING_SPACING_MARK
     * @see     Character#CONNECTOR_PUNCTUATION
     * @see     Character#CONTROL
     * @see     Character#CURRENCY_SYMBOL
     * @see     Character#DASH_PUNCTUATION
     * @see     Character#DECIMAL_DIGIT_NUMBER
     * @see     Character#ENCLOSING_MARK
     * @see     Character#END_PUNCTUATION
     * @see     Character#FINAL_QUOTE_PUNCTUATION
     * @see     Character#FORMAT
     * @see     Character#INITIAL_QUOTE_PUNCTUATION
     * @see     Character#LETTER_NUMBER
     * @see     Character#LINE_SEPARATOR
     * @see     Character#LOWERCASE_LETTER
     * @see     Character#MATH_SYMBOL
     * @see     Character#MODIFIER_LETTER
     * @see     Character#MODIFIER_SYMBOL
     * @see     Character#NON_SPACING_MARK
     * @see     Character#OTHER_LETTER
     * @see     Character#OTHER_NUMBER
     * @see     Character#OTHER_PUNCTUATION
     * @see     Character#OTHER_SYMBOL
     * @see     Character#PARAGRAPH_SEPARATOR
     * @see     Character#PRIVATE_USE
     * @see     Character#SPACE_SEPARATOR
     * @see     Character#START_PUNCTUATION
     * @see     Character#SURROGATE
     * @see     Character#TITLECASE_LETTER
     * @see     Character#UNASSIGNED
     * @see     Character#UPPERCASE_LETTER
     * @since   1.1
     */
    public static int getType(char ch) {
        return getType((int)ch);
    }

    /**
     * Returns a value indicating a character's general category.
     *
     * @param   codePoint the character (Unicode code point) to be tested.
     * @return  a value of type {@code int} representing the
     *          character's general category.
     * @see     Character#COMBINING_SPACING_MARK COMBINING_SPACING_MARK
     * @see     Character#CONNECTOR_PUNCTUATION CONNECTOR_PUNCTUATION
     * @see     Character#CONTROL CONTROL
     * @see     Character#CURRENCY_SYMBOL CURRENCY_SYMBOL
     * @see     Character#DASH_PUNCTUATION DASH_PUNCTUATION
     * @see     Character#DECIMAL_DIGIT_NUMBER DECIMAL_DIGIT_NUMBER
     * @see     Character#ENCLOSING_MARK ENCLOSING_MARK
     * @see     Character#END_PUNCTUATION END_PUNCTUATION
     * @see     Character#FINAL_QUOTE_PUNCTUATION FINAL_QUOTE_PUNCTUATION
     * @see     Character#FORMAT FORMAT
     * @see     Character#INITIAL_QUOTE_PUNCTUATION INITIAL_QUOTE_PUNCTUATION
     * @see     Character#LETTER_NUMBER LETTER_NUMBER
     * @see     Character#LINE_SEPARATOR LINE_SEPARATOR
     * @see     Character#LOWERCASE_LETTER LOWERCASE_LETTER
     * @see     Character#MATH_SYMBOL MATH_SYMBOL
     * @see     Character#MODIFIER_LETTER MODIFIER_LETTER
     * @see     Character#MODIFIER_SYMBOL MODIFIER_SYMBOL
     * @see     Character#NON_SPACING_MARK NON_SPACING_MARK
     * @see     Character#OTHER_LETTER OTHER_LETTER
     * @see     Character#OTHER_NUMBER OTHER_NUMBER
     * @see     Character#OTHER_PUNCTUATION OTHER_PUNCTUATION
     * @see     Character#OTHER_SYMBOL OTHER_SYMBOL
     * @see     Character#PARAGRAPH_SEPARATOR PARAGRAPH_SEPARATOR
     * @see     Character#PRIVATE_USE PRIVATE_USE
     * @see     Character#SPACE_SEPARATOR SPACE_SEPARATOR
     * @see     Character#START_PUNCTUATION START_PUNCTUATION
     * @see     Character#SURROGATE SURROGATE
     * @see     Character#TITLECASE_LETTER TITLECASE_LETTER
     * @see     Character#UNASSIGNED UNASSIGNED
     * @see     Character#UPPERCASE_LETTER UPPERCASE_LETTER
     * @since   1.5
     */
    public static int getType(int codePoint) {
        return CharacterData.of(codePoint).getType(codePoint);
    }

    /**
     * Determines the character representation for a specific digit in
     * the specified radix. If the value of {@code radix} is not a
     * valid radix, or the value of {@code digit} is not a valid
     * digit in the specified radix, the null character
     * ({@code '\u005Cu0000'}) is returned.
     * <p>
     * The {@code radix} argument is valid if it is greater than or
     * equal to {@code MIN_RADIX} and less than or equal to
     * {@code MAX_RADIX}. The {@code digit} argument is valid if
     * {@code 0 <= digit < radix}.
     * <p>
     * If the digit is less than 10, then
     * {@code '0' + digit} is returned. Otherwise, the value
     * {@code 'a' + digit - 10} is returned.
     *
     * @param   digit   the number to convert to a character.
     * @param   radix   the radix.
     * @return  the {@code char} representation of the specified digit
     *          in the specified radix.
     * @see     Character#MIN_RADIX
     * @see     Character#MAX_RADIX
     * @see     Character#digit(char, int)
     */
    public static char forDigit(int digit, int radix) {
        if ((digit >= radix) || (digit < 0)) {
            return '\0';
        }
        if ((radix < Character.MIN_RADIX) || (radix > Character.MAX_RADIX)) {
            return '\0';
        }
        if (digit < 10) {
            return (char)('0' + digit);
        }
        return (char)('a' - 10 + digit);
    }

    /**
     * Returns the Unicode directionality property for the given
     * character.  Character directionality is used to calculate the
     * visual ordering of text. The directionality value of undefined
     * {@code char} values is {@code DIRECTIONALITY_UNDEFINED}.
     *
     * <p><b>Note:</b> This method cannot handle <a
     * href="#supplementary"> supplementary characters</a>. To support
     * all Unicode characters, including supplementary characters, use
     * the {@link #getDirectionality(int)} method.
     *
     * @param  ch {@code char} for which the directionality property
     *            is requested.
     * @return the directionality property of the {@code char} value.
     *
     * @see Character#DIRECTIONALITY_UNDEFINED
     * @see Character#DIRECTIONALITY_LEFT_TO_RIGHT
     * @see Character#DIRECTIONALITY_RIGHT_TO_LEFT
     * @see Character#DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
     * @see Character#DIRECTIONALITY_EUROPEAN_NUMBER
     * @see Character#DIRECTIONALITY_EUROPEAN_NUMBER_SEPARATOR
     * @see Character#DIRECTIONALITY_EUROPEAN_NUMBER_TERMINATOR
     * @see Character#DIRECTIONALITY_ARABIC_NUMBER
     * @see Character#DIRECTIONALITY_COMMON_NUMBER_SEPARATOR
     * @see Character#DIRECTIONALITY_NONSPACING_MARK
     * @see Character#DIRECTIONALITY_BOUNDARY_NEUTRAL
     * @see Character#DIRECTIONALITY_PARAGRAPH_SEPARATOR
     * @see Character#DIRECTIONALITY_SEGMENT_SEPARATOR
     * @see Character#DIRECTIONALITY_WHITESPACE
     * @see Character#DIRECTIONALITY_OTHER_NEUTRALS
     * @see Character#DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING
     * @see Character#DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE
     * @see Character#DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING
     * @see Character#DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE
     * @see Character#DIRECTIONALITY_POP_DIRECTIONAL_FORMAT
     * @since 1.4
     */
    public static byte getDirectionality(char ch) {
        return getDirectionality((int)ch);
    }

    /**
     * Returns the Unicode directionality property for the given
     * character (Unicode code point).  Character directionality is
     * used to calculate the visual ordering of text. The
     * directionality value of undefined character is {@link
     * #DIRECTIONALITY_UNDEFINED}.
     *
     * @param   codePoint the character (Unicode code point) for which
     *          the directionality property is requested.
     * @return the directionality property of the character.
     *
     * @see Character#DIRECTIONALITY_UNDEFINED DIRECTIONALITY_UNDEFINED
     * @see Character#DIRECTIONALITY_LEFT_TO_RIGHT DIRECTIONALITY_LEFT_TO_RIGHT
     * @see Character#DIRECTIONALITY_RIGHT_TO_LEFT DIRECTIONALITY_RIGHT_TO_LEFT
     * @see Character#DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
     * @see Character#DIRECTIONALITY_EUROPEAN_NUMBER DIRECTIONALITY_EUROPEAN_NUMBER
     * @see Character#DIRECTIONALITY_EUROPEAN_NUMBER_SEPARATOR DIRECTIONALITY_EUROPEAN_NUMBER_SEPARATOR
     * @see Character#DIRECTIONALITY_EUROPEAN_NUMBER_TERMINATOR DIRECTIONALITY_EUROPEAN_NUMBER_TERMINATOR
     * @see Character#DIRECTIONALITY_ARABIC_NUMBER DIRECTIONALITY_ARABIC_NUMBER
     * @see Character#DIRECTIONALITY_COMMON_NUMBER_SEPARATOR DIRECTIONALITY_COMMON_NUMBER_SEPARATOR
     * @see Character#DIRECTIONALITY_NONSPACING_MARK DIRECTIONALITY_NONSPACING_MARK
     * @see Character#DIRECTIONALITY_BOUNDARY_NEUTRAL DIRECTIONALITY_BOUNDARY_NEUTRAL
     * @see Character#DIRECTIONALITY_PARAGRAPH_SEPARATOR DIRECTIONALITY_PARAGRAPH_SEPARATOR
     * @see Character#DIRECTIONALITY_SEGMENT_SEPARATOR DIRECTIONALITY_SEGMENT_SEPARATOR
     * @see Character#DIRECTIONALITY_WHITESPACE DIRECTIONALITY_WHITESPACE
     * @see Character#DIRECTIONALITY_OTHER_NEUTRALS DIRECTIONALITY_OTHER_NEUTRALS
     * @see Character#DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING
     * @see Character#DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE
     * @see Character#DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING
     * @see Character#DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE
     * @see Character#DIRECTIONALITY_POP_DIRECTIONAL_FORMAT DIRECTIONALITY_POP_DIRECTIONAL_FORMAT
     * @since    1.5
     */
    public static byte getDirectionality(int codePoint) {
        return CharacterData.of(codePoint).getDirectionality(codePoint);
    }

    /**
     * Determines whether the character is mirrored according to the
     * Unicode specification.  Mirrored characters should have their
     * glyphs horizontally mirrored when displayed in text that is
     * right-to-left.  For example, {@code '\u005Cu0028'} LEFT
     * PARENTHESIS is semantically defined to be an <i>opening
     * parenthesis</i>.  This will appear as a "(" in text that is
     * left-to-right but as a ")" in text that is right-to-left.
     *
     * <p><b>Note:</b> This method cannot handle <a
     * href="#supplementary"> supplementary characters</a>. To support
     * all Unicode characters, including supplementary characters, use
     * the {@link #isMirrored(int)} method.
     *
     * @param  ch {@code char} for which the mirrored property is requested
     * @return {@code true} if the char is mirrored, {@code false}
     *         if the {@code char} is not mirrored or is not defined.
     * @since 1.4
     */
    public static boolean isMirrored(char ch) {
        return isMirrored((int)ch);
    }

    /**
     * Determines whether the specified character (Unicode code point)
     * is mirrored according to the Unicode specification.  Mirrored
     * characters should have their glyphs horizontally mirrored when
     * displayed in text that is right-to-left.  For example,
     * {@code '\u005Cu0028'} LEFT PARENTHESIS is semantically
     * defined to be an <i>opening parenthesis</i>.  This will appear
     * as a "(" in text that is left-to-right but as a ")" in text
     * that is right-to-left.
     *
     * @param   codePoint the character (Unicode code point) to be tested.
     * @return  {@code true} if the character is mirrored, {@code false}
     *          if the character is not mirrored or is not defined.
     * @since   1.5
     */
    public static boolean isMirrored(int codePoint) {
        return CharacterData.of(codePoint).isMirrored(codePoint);
    }

    /**
     * Compares two {@code Character} objects numerically.
     *
     * @param   anotherCharacter   the {@code Character} to be compared.

     * @return  the value {@code 0} if the argument {@code Character}
     *          is equal to this {@code Character}; a value less than
     *          {@code 0} if this {@code Character} is numerically less
     *          than the {@code Character} argument; and a value greater than
     *          {@code 0} if this {@code Character} is numerically greater
     *          than the {@code Character} argument (unsigned comparison).
     *          Note that this is strictly a numerical comparison; it is not
     *          locale-dependent.
     * @since   1.2
     */
    public int compareTo(Character anotherCharacter) {
        return compare(this.value, anotherCharacter.value);
    }

    /**
     * Compares two {@code char} values numerically.
     * The value returned is identical to what would be returned by:
     * <pre>
     *    Character.valueOf(x).compareTo(Character.valueOf(y))
     * </pre>
     *
     * @param  x the first {@code char} to compare
     * @param  y the second {@code char} to compare
     * @return the value {@code 0} if {@code x == y};
     *         a value less than {@code 0} if {@code x < y}; and
     *         a value greater than {@code 0} if {@code x > y}
     * @since 1.7
     */
    public static int compare(char x, char y) {
        return x - y;
    }

    /**
     * Converts the character (Unicode code point) argument to uppercase using
     * information from the UnicodeData file.
     *
     * @param   codePoint   the character (Unicode code point) to be converted.
     * @return  either the uppercase equivalent of the character, if
     *          any, or an error flag ({@code Character.ERROR})
     *          that indicates that a 1:M {@code char} mapping exists.
     * @see     Character#isLowerCase(char)
     * @see     Character#isUpperCase(char)
     * @see     Character#toLowerCase(char)
     * @see     Character#toTitleCase(char)
     * @since 1.4
     */
    static int toUpperCaseEx(int codePoint) {
        assert isValidCodePoint(codePoint);
        return CharacterData.of(codePoint).toUpperCaseEx(codePoint);
    }

    /**
     * Converts the character (Unicode code point) argument to uppercase using case
     * mapping information from the SpecialCasing file in the Unicode
     * specification. If a character has no explicit uppercase
     * mapping, then the {@code char} itself is returned in the
     * {@code char[]}.
     *
     * @param   codePoint   the character (Unicode code point) to be converted.
     * @return a {@code char[]} with the uppercased character.
     * @since 1.4
     */
    static char[] toUpperCaseCharArray(int codePoint) {
        // As of Unicode 6.0, 1:M uppercasings only happen in the BMP.
        assert isBmpCodePoint(codePoint);
        return CharacterData.of(codePoint).toUpperCaseCharArray(codePoint);
    }

    /**
     * The number of bits used to represent a <tt>char</tt> value in unsigned
     * binary form, constant {@code 16}.
     *
     * @since 1.5
     */
    public static final int SIZE = 16;

    /**
     * The number of bytes used to represent a {@code char} value in unsigned
     * binary form.
     *
     * @since 1.8
     */
    public static final int BYTES = SIZE / Byte.SIZE;

    /**
     * Returns the value obtained by reversing the order of the bytes in the
     * specified <tt>char</tt> value.
     *
     * @param ch The {@code char} of which to reverse the byte order.
     * @return the value obtained by reversing (or, equivalently, swapping)
     *     the bytes in the specified <tt>char</tt> value.
     * @since 1.5
     */
    public static char reverseBytes(char ch) {
        return (char) (((ch & 0xFF00) >> 8) | (ch << 8));
    }

    /**
     * Returns the Unicode name of the specified character
     * {@code codePoint}, or null if the code point is
     * {@link #UNASSIGNED unassigned}.
     * <p>
     * Note: if the specified character is not assigned a name by
     * the <i>UnicodeData</i> file (part of the Unicode Character
     * Database maintained by the Unicode Consortium), the returned
     * name is the same as the result of expression.
     *
     * <blockquote>{@code
     *     Character.UnicodeBlock.of(codePoint).toString().replace('_', ' ')
     *     + " "
     *     + Integer.toHexString(codePoint).toUpperCase(Locale.ENGLISH);
     *
     * }</blockquote>
     *
     * @param  codePoint the character (Unicode code point)
     *
     * @return the Unicode name of the specified character, or null if
     *         the code point is unassigned.
     *
     * @exception IllegalArgumentException if the specified
     *            {@code codePoint} is not a valid Unicode
     *            code point.
     *
     * @since 1.7
     */
    public static String getName(int codePoint) {
        if (!isValidCodePoint(codePoint)) {
            throw new IllegalArgumentException();
        }
        String name = CharacterName.get(codePoint);
        if (name != null)
            return name;
        if (getType(codePoint) == UNASSIGNED)
            return null;
        UnicodeBlock block = UnicodeBlock.of(codePoint);
        if (block != null)
            return block.toString().replace('_', ' ') + " "
                   + Integer.toHexString(codePoint).toUpperCase(Locale.ENGLISH);
        // should never come here
        return Integer.toHexString(codePoint).toUpperCase(Locale.ENGLISH);
    }
}
