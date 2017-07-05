/*
 * Copyright 2002-2009 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package java.lang;
import java.util.Map;
import java.util.HashMap;
import java.util.Locale;

/**
 * The <code>Character</code> class wraps a value of the primitive
 * type <code>char</code> in an object. An object of type
 * <code>Character</code> contains a single field whose type is
 * <code>char</code>.
 * <p>
 * In addition, this class provides several methods for determining
 * a character's category (lowercase letter, digit, etc.) and for converting
 * characters from uppercase to lowercase and vice versa.
 * <p>
 * Character information is based on the Unicode Standard, version 4.0.
 * <p>
 * The methods and data of class <code>Character</code> are defined by
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
 * <h4><a name="unicode">Unicode Character Representations</a></h4>
 *
 * <p>The <code>char</code> data type (and therefore the value that a
 * <code>Character</code> object encapsulates) are based on the
 * original Unicode specification, which defined characters as
 * fixed-width 16-bit entities. The Unicode standard has since been
 * changed to allow for characters whose representation requires more
 * than 16 bits.  The range of legal <em>code point</em>s is now
 * U+0000 to U+10FFFF, known as <em>Unicode scalar value</em>.
 * (Refer to the <a
 * href="http://www.unicode.org/reports/tr27/#notation"><i>
 * definition</i></a> of the U+<i>n</i> notation in the Unicode
 * standard.)
 *
 * <p>The set of characters from U+0000 to U+FFFF is sometimes
 * referred to as the <em>Basic Multilingual Plane (BMP)</em>. <a
 * name="supplementary">Characters</a> whose code points are greater
 * than U+FFFF are called <em>supplementary character</em>s.  The Java
 * 2 platform uses the UTF-16 representation in <code>char</code>
 * arrays and in the <code>String</code> and <code>StringBuffer</code>
 * classes. In this representation, supplementary characters are
 * represented as a pair of <code>char</code> values, the first from
 * the <em>high-surrogates</em> range, (&#92;uD800-&#92;uDBFF), the
 * second from the <em>low-surrogates</em> range
 * (&#92;uDC00-&#92;uDFFF).
 *
 * <p>A <code>char</code> value, therefore, represents Basic
 * Multilingual Plane (BMP) code points, including the surrogate
 * code points, or code units of the UTF-16 encoding. An
 * <code>int</code> value represents all Unicode code points,
 * including supplementary code points. The lower (least significant)
 * 21 bits of <code>int</code> are used to represent Unicode code
 * points and the upper (most significant) 11 bits must be zero.
 * Unless otherwise specified, the behavior with respect to
 * supplementary characters and surrogate <code>char</code> values is
 * as follows:
 *
 * <ul>
 * <li>The methods that only accept a <code>char</code> value cannot support
 * supplementary characters. They treat <code>char</code> values from the
 * surrogate ranges as undefined characters. For example,
 * <code>Character.isLetter('&#92;uD840')</code> returns <code>false</code>, even though
 * this specific value if followed by any low-surrogate value in a string
 * would represent a letter.
 *
 * <li>The methods that accept an <code>int</code> value support all
 * Unicode characters, including supplementary characters. For
 * example, <code>Character.isLetter(0x2F81A)</code> returns
 * <code>true</code> because the code point value represents a letter
 * (a CJK ideograph).
 * </ul>
 *
 * <p>In the Java SE API documentation, <em>Unicode code point</em> is
 * used for character values in the range between U+0000 and U+10FFFF,
 * and <em>Unicode code unit</em> is used for 16-bit
 * <code>char</code> values that are code units of the <em>UTF-16</em>
 * encoding. For more information on Unicode terminology, refer to the
 * <a href="http://www.unicode.org/glossary/">Unicode Glossary</a>.
 *
 * @author  Lee Boynton
 * @author  Guy Steele
 * @author  Akira Tanaka
 * @since   1.0
 */
public final
class Character extends Object implements java.io.Serializable, Comparable<Character> {
    /**
     * The minimum radix available for conversion to and from strings.
     * The constant value of this field is the smallest value permitted
     * for the radix argument in radix-conversion methods such as the
     * <code>digit</code> method, the <code>forDigit</code>
     * method, and the <code>toString</code> method of class
     * <code>Integer</code>.
     *
     * @see     java.lang.Character#digit(char, int)
     * @see     java.lang.Character#forDigit(int, int)
     * @see     java.lang.Integer#toString(int, int)
     * @see     java.lang.Integer#valueOf(java.lang.String)
     */
    public static final int MIN_RADIX = 2;

    /**
     * The maximum radix available for conversion to and from strings.
     * The constant value of this field is the largest value permitted
     * for the radix argument in radix-conversion methods such as the
     * <code>digit</code> method, the <code>forDigit</code>
     * method, and the <code>toString</code> method of class
     * <code>Integer</code>.
     *
     * @see     java.lang.Character#digit(char, int)
     * @see     java.lang.Character#forDigit(int, int)
     * @see     java.lang.Integer#toString(int, int)
     * @see     java.lang.Integer#valueOf(java.lang.String)
     */
    public static final int MAX_RADIX = 36;

    /**
     * The constant value of this field is the smallest value of type
     * <code>char</code>, <code>'&#92;u0000'</code>.
     *
     * @since   1.0.2
     */
    public static final char   MIN_VALUE = '\u0000';

    /**
     * The constant value of this field is the largest value of type
     * <code>char</code>, <code>'&#92;uFFFF'</code>.
     *
     * @since   1.0.2
     */
    public static final char   MAX_VALUE = '\uffff';

    /**
     * The <code>Class</code> instance representing the primitive type
     * <code>char</code>.
     *
     * @since   1.1
     */
    public static final Class<Character> TYPE = Class.getPrimitiveClass("char");

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
    public static final byte
        UNASSIGNED                  = 0;

   /**
    * General category "Lu" in the Unicode specification.
    * @since   1.1
    */
    public static final byte
        UPPERCASE_LETTER            = 1;

   /**
    * General category "Ll" in the Unicode specification.
    * @since   1.1
    */
    public static final byte
        LOWERCASE_LETTER            = 2;

   /**
    * General category "Lt" in the Unicode specification.
    * @since   1.1
    */
    public static final byte
        TITLECASE_LETTER            = 3;

   /**
    * General category "Lm" in the Unicode specification.
    * @since   1.1
    */
    public static final byte
        MODIFIER_LETTER             = 4;

   /**
    * General category "Lo" in the Unicode specification.
    * @since   1.1
    */
    public static final byte
        OTHER_LETTER                = 5;

   /**
    * General category "Mn" in the Unicode specification.
    * @since   1.1
    */
    public static final byte
        NON_SPACING_MARK            = 6;

   /**
    * General category "Me" in the Unicode specification.
    * @since   1.1
    */
    public static final byte
        ENCLOSING_MARK              = 7;

   /**
    * General category "Mc" in the Unicode specification.
    * @since   1.1
    */
    public static final byte
        COMBINING_SPACING_MARK      = 8;

   /**
    * General category "Nd" in the Unicode specification.
    * @since   1.1
    */
    public static final byte
        DECIMAL_DIGIT_NUMBER        = 9;

   /**
    * General category "Nl" in the Unicode specification.
    * @since   1.1
    */
    public static final byte
        LETTER_NUMBER               = 10;

   /**
    * General category "No" in the Unicode specification.
    * @since   1.1
    */
    public static final byte
        OTHER_NUMBER                = 11;

   /**
    * General category "Zs" in the Unicode specification.
    * @since   1.1
    */
    public static final byte
        SPACE_SEPARATOR             = 12;

   /**
    * General category "Zl" in the Unicode specification.
    * @since   1.1
    */
    public static final byte
        LINE_SEPARATOR              = 13;

   /**
    * General category "Zp" in the Unicode specification.
    * @since   1.1
    */
    public static final byte
        PARAGRAPH_SEPARATOR         = 14;

   /**
    * General category "Cc" in the Unicode specification.
    * @since   1.1
    */
    public static final byte
        CONTROL                     = 15;

   /**
    * General category "Cf" in the Unicode specification.
    * @since   1.1
    */
    public static final byte
        FORMAT                      = 16;

   /**
    * General category "Co" in the Unicode specification.
    * @since   1.1
    */
    public static final byte
        PRIVATE_USE                 = 18;

   /**
    * General category "Cs" in the Unicode specification.
    * @since   1.1
    */
    public static final byte
        SURROGATE                   = 19;

   /**
    * General category "Pd" in the Unicode specification.
    * @since   1.1
    */
    public static final byte
        DASH_PUNCTUATION            = 20;

   /**
    * General category "Ps" in the Unicode specification.
    * @since   1.1
    */
    public static final byte
        START_PUNCTUATION           = 21;

   /**
    * General category "Pe" in the Unicode specification.
    * @since   1.1
    */
    public static final byte
        END_PUNCTUATION             = 22;

   /**
    * General category "Pc" in the Unicode specification.
    * @since   1.1
    */
    public static final byte
        CONNECTOR_PUNCTUATION       = 23;

   /**
    * General category "Po" in the Unicode specification.
    * @since   1.1
    */
    public static final byte
        OTHER_PUNCTUATION           = 24;

   /**
    * General category "Sm" in the Unicode specification.
    * @since   1.1
    */
    public static final byte
        MATH_SYMBOL                 = 25;

   /**
    * General category "Sc" in the Unicode specification.
    * @since   1.1
    */
    public static final byte
        CURRENCY_SYMBOL             = 26;

   /**
    * General category "Sk" in the Unicode specification.
    * @since   1.1
    */
    public static final byte
        MODIFIER_SYMBOL             = 27;

   /**
    * General category "So" in the Unicode specification.
    * @since   1.1
    */
    public static final byte
        OTHER_SYMBOL                = 28;

   /**
    * General category "Pi" in the Unicode specification.
    * @since   1.4
    */
    public static final byte
        INITIAL_QUOTE_PUNCTUATION   = 29;

   /**
    * General category "Pf" in the Unicode specification.
    * @since   1.4
    */
    public static final byte
        FINAL_QUOTE_PUNCTUATION     = 30;

    /**
     * Error flag. Use int (code point) to avoid confusion with U+FFFF.
     */
     static final int ERROR = 0xFFFFFFFF;


    /**
     * Undefined bidirectional character type. Undefined <code>char</code>
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
     * The minimum value of a Unicode high-surrogate code unit in the
     * UTF-16 encoding. A high-surrogate is also known as a
     * <i>leading-surrogate</i>.
     *
     * @since 1.5
     */
    public static final char MIN_HIGH_SURROGATE = '\uD800';

    /**
     * The maximum value of a Unicode high-surrogate code unit in the
     * UTF-16 encoding. A high-surrogate is also known as a
     * <i>leading-surrogate</i>.
     *
     * @since 1.5
     */
    public static final char MAX_HIGH_SURROGATE = '\uDBFF';

    /**
     * The minimum value of a Unicode low-surrogate code unit in the
     * UTF-16 encoding. A low-surrogate is also known as a
     * <i>trailing-surrogate</i>.
     *
     * @since 1.5
     */
    public static final char MIN_LOW_SURROGATE  = '\uDC00';

    /**
     * The maximum value of a Unicode low-surrogate code unit in the
     * UTF-16 encoding. A low-surrogate is also known as a
     * <i>trailing-surrogate</i>.
     *
     * @since 1.5
     */
    public static final char MAX_LOW_SURROGATE  = '\uDFFF';

    /**
     * The minimum value of a Unicode surrogate code unit in the UTF-16 encoding.
     *
     * @since 1.5
     */
    public static final char MIN_SURROGATE = MIN_HIGH_SURROGATE;

    /**
     * The maximum value of a Unicode surrogate code unit in the UTF-16 encoding.
     *
     * @since 1.5
     */
    public static final char MAX_SURROGATE = MAX_LOW_SURROGATE;

    /**
     * The minimum value of a supplementary code point.
     *
     * @since 1.5
     */
    public static final int MIN_SUPPLEMENTARY_CODE_POINT = 0x010000;

    /**
     * The minimum value of a Unicode code point.
     *
     * @since 1.5
     */
    public static final int MIN_CODE_POINT = 0x000000;

    /**
     * The maximum value of a Unicode code point.
     *
     * @since 1.5
     */
    public static final int MAX_CODE_POINT = 0x10ffff;


    /**
     * Instances of this class represent particular subsets of the Unicode
     * character set.  The only family of subsets defined in the
     * <code>Character</code> class is <code>{@link Character.UnicodeBlock
     * UnicodeBlock}</code>.  Other portions of the Java API may define other
     * subsets for their own purposes.
     *
     * @since 1.2
     */
    public static class Subset  {

        private String name;

        /**
         * Constructs a new <code>Subset</code> instance.
         *
         * @exception NullPointerException if name is <code>null</code>
         * @param  name  The name of this subset
         */
        protected Subset(String name) {
            if (name == null) {
                throw new NullPointerException("name");
            }
            this.name = name;
        }

        /**
         * Compares two <code>Subset</code> objects for equality.
         * This method returns <code>true</code> if and only if
         * <code>this</code> and the argument refer to the same
         * object; since this method is <code>final</code>, this
         * guarantee holds for all subclasses.
         */
        public final boolean equals(Object obj) {
            return (this == obj);
        }

        /**
         * Returns the standard hash code as defined by the
         * <code>{@link Object#hashCode}</code> method.  This method
         * is <code>final</code> in order to ensure that the
         * <code>equals</code> and <code>hashCode</code> methods will
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

    /**
     * A family of character subsets representing the character blocks in the
     * Unicode specification. Character blocks generally define characters
     * used for a specific script or purpose. A character is contained by
     * at most one Unicode block.
     *
     * @since 1.2
     */
    public static final class UnicodeBlock extends Subset {

        private static Map map = new HashMap();

        /**
         * Create a UnicodeBlock with the given identifier name.
         * This name must be the same as the block identifier.
         */
        private UnicodeBlock(String idName) {
            super(idName);
            map.put(idName.toUpperCase(Locale.US), this);
        }

        /**
         * Create a UnicodeBlock with the given identifier name and
         * alias name.
         */
        private UnicodeBlock(String idName, String alias) {
            this(idName);
            map.put(alias.toUpperCase(Locale.US), this);
        }

        /**
         * Create a UnicodeBlock with the given identifier name and
         * alias names.
         */
        private UnicodeBlock(String idName, String[] aliasName) {
            this(idName);
            if (aliasName != null) {
                for(int x=0; x<aliasName.length; ++x) {
                    map.put(aliasName[x].toUpperCase(Locale.US), this);
                }
            }
        }

        /**
         * Constant for the "Basic Latin" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock  BASIC_LATIN =
            new UnicodeBlock("BASIC_LATIN", new String[] {"Basic Latin", "BasicLatin" });

        /**
         * Constant for the "Latin-1 Supplement" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock LATIN_1_SUPPLEMENT =
            new UnicodeBlock("LATIN_1_SUPPLEMENT", new String[]{ "Latin-1 Supplement", "Latin-1Supplement"});

        /**
         * Constant for the "Latin Extended-A" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock LATIN_EXTENDED_A =
            new UnicodeBlock("LATIN_EXTENDED_A", new String[]{ "Latin Extended-A", "LatinExtended-A"});

        /**
         * Constant for the "Latin Extended-B" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock LATIN_EXTENDED_B =
            new UnicodeBlock("LATIN_EXTENDED_B", new String[] {"Latin Extended-B", "LatinExtended-B"});

        /**
         * Constant for the "IPA Extensions" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock IPA_EXTENSIONS =
            new UnicodeBlock("IPA_EXTENSIONS", new String[] {"IPA Extensions", "IPAExtensions"});

        /**
         * Constant for the "Spacing Modifier Letters" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock SPACING_MODIFIER_LETTERS =
            new UnicodeBlock("SPACING_MODIFIER_LETTERS", new String[] { "Spacing Modifier Letters",
                                                                        "SpacingModifierLetters"});

        /**
         * Constant for the "Combining Diacritical Marks" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock COMBINING_DIACRITICAL_MARKS =
            new UnicodeBlock("COMBINING_DIACRITICAL_MARKS", new String[] {"Combining Diacritical Marks",
                                                                          "CombiningDiacriticalMarks" });

        /**
         * Constant for the "Greek and Coptic" Unicode character block.
         * <p>
         * This block was previously known as the "Greek" block.
         *
         * @since 1.2
         */
        public static final UnicodeBlock GREEK
            = new UnicodeBlock("GREEK", new String[] {"Greek and Coptic", "GreekandCoptic"});

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
            new UnicodeBlock("HANGUL_JAMO", new String[] {"Hangul Jamo", "HangulJamo"});

        /**
         * Constant for the "Latin Extended Additional" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock LATIN_EXTENDED_ADDITIONAL =
            new UnicodeBlock("LATIN_EXTENDED_ADDITIONAL", new String[] {"Latin Extended Additional",
                                                                        "LatinExtendedAdditional"});

        /**
         * Constant for the "Greek Extended" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock GREEK_EXTENDED =
            new UnicodeBlock("GREEK_EXTENDED", new String[] {"Greek Extended", "GreekExtended"});

        /**
         * Constant for the "General Punctuation" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock GENERAL_PUNCTUATION =
            new UnicodeBlock("GENERAL_PUNCTUATION", new String[] {"General Punctuation", "GeneralPunctuation"});

        /**
         * Constant for the "Superscripts and Subscripts" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock SUPERSCRIPTS_AND_SUBSCRIPTS =
            new UnicodeBlock("SUPERSCRIPTS_AND_SUBSCRIPTS", new String[] {"Superscripts and Subscripts",
                                                                          "SuperscriptsandSubscripts" });

        /**
         * Constant for the "Currency Symbols" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock CURRENCY_SYMBOLS =
            new UnicodeBlock("CURRENCY_SYMBOLS", new String[] { "Currency Symbols", "CurrencySymbols"});

        /**
         * Constant for the "Combining Diacritical Marks for Symbols" Unicode character block.
         * <p>
         * This block was previously known as "Combining Marks for Symbols".
         * @since 1.2
         */
        public static final UnicodeBlock COMBINING_MARKS_FOR_SYMBOLS =
            new UnicodeBlock("COMBINING_MARKS_FOR_SYMBOLS", new String[] {"Combining Diacritical Marks for Symbols",
                                                                                                                                                   "CombiningDiacriticalMarksforSymbols",
                                                                           "Combining Marks for Symbols",
                                                                           "CombiningMarksforSymbols" });

        /**
         * Constant for the "Letterlike Symbols" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock LETTERLIKE_SYMBOLS =
            new UnicodeBlock("LETTERLIKE_SYMBOLS", new String[] { "Letterlike Symbols", "LetterlikeSymbols"});

        /**
         * Constant for the "Number Forms" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock NUMBER_FORMS =
            new UnicodeBlock("NUMBER_FORMS", new String[] {"Number Forms", "NumberForms"});

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
            new UnicodeBlock("MATHEMATICAL_OPERATORS", new String[] {"Mathematical Operators",
                                                                     "MathematicalOperators"});

        /**
         * Constant for the "Miscellaneous Technical" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock MISCELLANEOUS_TECHNICAL =
            new UnicodeBlock("MISCELLANEOUS_TECHNICAL", new String[] {"Miscellaneous Technical",
                                                                      "MiscellaneousTechnical"});

        /**
         * Constant for the "Control Pictures" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock CONTROL_PICTURES =
            new UnicodeBlock("CONTROL_PICTURES", new String[] {"Control Pictures", "ControlPictures"});

        /**
         * Constant for the "Optical Character Recognition" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock OPTICAL_CHARACTER_RECOGNITION =
            new UnicodeBlock("OPTICAL_CHARACTER_RECOGNITION", new String[] {"Optical Character Recognition",
                                                                            "OpticalCharacterRecognition"});

        /**
         * Constant for the "Enclosed Alphanumerics" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock ENCLOSED_ALPHANUMERICS =
            new UnicodeBlock("ENCLOSED_ALPHANUMERICS", new String[] {"Enclosed Alphanumerics",
                                                                     "EnclosedAlphanumerics"});

        /**
         * Constant for the "Box Drawing" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock BOX_DRAWING =
            new UnicodeBlock("BOX_DRAWING", new String[] {"Box Drawing", "BoxDrawing"});

        /**
         * Constant for the "Block Elements" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock BLOCK_ELEMENTS =
            new UnicodeBlock("BLOCK_ELEMENTS", new String[] {"Block Elements", "BlockElements"});

        /**
         * Constant for the "Geometric Shapes" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock GEOMETRIC_SHAPES =
            new UnicodeBlock("GEOMETRIC_SHAPES", new String[] {"Geometric Shapes", "GeometricShapes"});

        /**
         * Constant for the "Miscellaneous Symbols" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock MISCELLANEOUS_SYMBOLS =
            new UnicodeBlock("MISCELLANEOUS_SYMBOLS", new String[] {"Miscellaneous Symbols",
                                                                    "MiscellaneousSymbols"});

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
            new UnicodeBlock("CJK_SYMBOLS_AND_PUNCTUATION", new String[] {"CJK Symbols and Punctuation",
                                                                          "CJKSymbolsandPunctuation"});

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
            new UnicodeBlock("HANGUL_COMPATIBILITY_JAMO", new String[] {"Hangul Compatibility Jamo",
                                                                        "HangulCompatibilityJamo"});

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
            new UnicodeBlock("ENCLOSED_CJK_LETTERS_AND_MONTHS", new String[] {"Enclosed CJK Letters and Months",
                                                                              "EnclosedCJKLettersandMonths"});

        /**
         * Constant for the "CJK Compatibility" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock CJK_COMPATIBILITY =
            new UnicodeBlock("CJK_COMPATIBILITY", new String[] {"CJK Compatibility", "CJKCompatibility"});

        /**
         * Constant for the "CJK Unified Ideographs" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock CJK_UNIFIED_IDEOGRAPHS =
            new UnicodeBlock("CJK_UNIFIED_IDEOGRAPHS", new String[] {"CJK Unified Ideographs",
                                                                     "CJKUnifiedIdeographs"});

        /**
         * Constant for the "Hangul Syllables" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock HANGUL_SYLLABLES =
            new UnicodeBlock("HANGUL_SYLLABLES", new String[] {"Hangul Syllables", "HangulSyllables"});

        /**
         * Constant for the "Private Use Area" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock PRIVATE_USE_AREA =
            new UnicodeBlock("PRIVATE_USE_AREA", new String[] {"Private Use Area", "PrivateUseArea"});

        /**
         * Constant for the "CJK Compatibility Ideographs" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock CJK_COMPATIBILITY_IDEOGRAPHS =
            new UnicodeBlock("CJK_COMPATIBILITY_IDEOGRAPHS",
                             new String[] {"CJK Compatibility Ideographs",
                                           "CJKCompatibilityIdeographs"});

        /**
         * Constant for the "Alphabetic Presentation Forms" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock ALPHABETIC_PRESENTATION_FORMS =
            new UnicodeBlock("ALPHABETIC_PRESENTATION_FORMS", new String[] {"Alphabetic Presentation Forms",
                                                                            "AlphabeticPresentationForms"});

        /**
         * Constant for the "Arabic Presentation Forms-A" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock ARABIC_PRESENTATION_FORMS_A =
            new UnicodeBlock("ARABIC_PRESENTATION_FORMS_A", new String[] {"Arabic Presentation Forms-A",
                                                                          "ArabicPresentationForms-A"});

        /**
         * Constant for the "Combining Half Marks" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock COMBINING_HALF_MARKS =
            new UnicodeBlock("COMBINING_HALF_MARKS", new String[] {"Combining Half Marks",
                                                                   "CombiningHalfMarks"});

        /**
         * Constant for the "CJK Compatibility Forms" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock CJK_COMPATIBILITY_FORMS =
            new UnicodeBlock("CJK_COMPATIBILITY_FORMS", new String[] {"CJK Compatibility Forms",
                                                                      "CJKCompatibilityForms"});

        /**
         * Constant for the "Small Form Variants" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock SMALL_FORM_VARIANTS =
            new UnicodeBlock("SMALL_FORM_VARIANTS", new String[] {"Small Form Variants",
                                                                  "SmallFormVariants"});

        /**
         * Constant for the "Arabic Presentation Forms-B" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock ARABIC_PRESENTATION_FORMS_B =
            new UnicodeBlock("ARABIC_PRESENTATION_FORMS_B", new String[] {"Arabic Presentation Forms-B",
                                                                          "ArabicPresentationForms-B"});

        /**
         * Constant for the "Halfwidth and Fullwidth Forms" Unicode character block.
         * @since 1.2
         */
        public static final UnicodeBlock HALFWIDTH_AND_FULLWIDTH_FORMS =
            new UnicodeBlock("HALFWIDTH_AND_FULLWIDTH_FORMS",
                             new String[] {"Halfwidth and Fullwidth Forms",
                                           "HalfwidthandFullwidthForms"});

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
                             new String[] {"Unified Canadian Aboriginal Syllabics",
                                           "UnifiedCanadianAboriginalSyllabics"});

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
            new UnicodeBlock("BRAILLE_PATTERNS", new String[] {"Braille Patterns",
                                                               "BraillePatterns"});

        /**
         * Constant for the "CJK Radicals Supplement" Unicode character block.
         * @since 1.4
         */
        public static final UnicodeBlock CJK_RADICALS_SUPPLEMENT =
             new UnicodeBlock("CJK_RADICALS_SUPPLEMENT", new String[] {"CJK Radicals Supplement",
                                                                       "CJKRadicalsSupplement"});

        /**
         * Constant for the "Kangxi Radicals" Unicode character block.
         * @since 1.4
         */
        public static final UnicodeBlock KANGXI_RADICALS =
            new UnicodeBlock("KANGXI_RADICALS", new String[] {"Kangxi Radicals", "KangxiRadicals"});

        /**
         * Constant for the "Ideographic Description Characters" Unicode character block.
         * @since 1.4
         */
        public static final UnicodeBlock IDEOGRAPHIC_DESCRIPTION_CHARACTERS =
            new UnicodeBlock("IDEOGRAPHIC_DESCRIPTION_CHARACTERS", new String[] {"Ideographic Description Characters",
                                                                                 "IdeographicDescriptionCharacters"});

        /**
         * Constant for the "Bopomofo Extended" Unicode character block.
         * @since 1.4
         */
        public static final UnicodeBlock BOPOMOFO_EXTENDED =
            new UnicodeBlock("BOPOMOFO_EXTENDED", new String[] {"Bopomofo Extended",
                                                                "BopomofoExtended"});

        /**
         * Constant for the "CJK Unified Ideographs Extension A" Unicode character block.
         * @since 1.4
         */
        public static final UnicodeBlock CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A =
            new UnicodeBlock("CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A", new String[] {"CJK Unified Ideographs Extension A",
                                                                                 "CJKUnifiedIdeographsExtensionA"});

        /**
         * Constant for the "Yi Syllables" Unicode character block.
         * @since 1.4
         */
        public static final UnicodeBlock YI_SYLLABLES =
            new UnicodeBlock("YI_SYLLABLES", new String[] {"Yi Syllables", "YiSyllables"});

        /**
         * Constant for the "Yi Radicals" Unicode character block.
         * @since 1.4
         */
        public static final UnicodeBlock YI_RADICALS =
            new UnicodeBlock("YI_RADICALS", new String[] {"Yi Radicals", "YiRadicals"});


        /**
         * Constant for the "Cyrillic Supplementary" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock CYRILLIC_SUPPLEMENTARY =
            new UnicodeBlock("CYRILLIC_SUPPLEMENTARY",
                             new String[] {"Cyrillic Supplementary",
                                           "CyrillicSupplementary",
                                           "Cyrillic Supplement",
                                           "CyrillicSupplement"});

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
            new UnicodeBlock("TAI_LE", new String[] {"Tai Le", "TaiLe"});

        /**
         * Constant for the "Khmer Symbols" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock KHMER_SYMBOLS =
            new UnicodeBlock("KHMER_SYMBOLS", new String[] {"Khmer Symbols", "KhmerSymbols"});

        /**
         * Constant for the "Phonetic Extensions" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock PHONETIC_EXTENSIONS =
            new UnicodeBlock("PHONETIC_EXTENSIONS", new String[] {"Phonetic Extensions", "PhoneticExtensions"});

        /**
         * Constant for the "Miscellaneous Mathematical Symbols-A" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock MISCELLANEOUS_MATHEMATICAL_SYMBOLS_A =
            new UnicodeBlock("MISCELLANEOUS_MATHEMATICAL_SYMBOLS_A",
                             new String[]{"Miscellaneous Mathematical Symbols-A",
                                          "MiscellaneousMathematicalSymbols-A"});

        /**
         * Constant for the "Supplemental Arrows-A" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock SUPPLEMENTAL_ARROWS_A =
            new UnicodeBlock("SUPPLEMENTAL_ARROWS_A", new String[] {"Supplemental Arrows-A",
                                                                    "SupplementalArrows-A"});

        /**
         * Constant for the "Supplemental Arrows-B" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock SUPPLEMENTAL_ARROWS_B =
            new UnicodeBlock("SUPPLEMENTAL_ARROWS_B", new String[] {"Supplemental Arrows-B",
                                                                    "SupplementalArrows-B"});

        /**
         * Constant for the "Miscellaneous Mathematical Symbols-B" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock MISCELLANEOUS_MATHEMATICAL_SYMBOLS_B
                = new UnicodeBlock("MISCELLANEOUS_MATHEMATICAL_SYMBOLS_B",
                                   new String[] {"Miscellaneous Mathematical Symbols-B",
                                                 "MiscellaneousMathematicalSymbols-B"});

        /**
         * Constant for the "Supplemental Mathematical Operators" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock SUPPLEMENTAL_MATHEMATICAL_OPERATORS =
            new UnicodeBlock("SUPPLEMENTAL_MATHEMATICAL_OPERATORS",
                             new String[]{"Supplemental Mathematical Operators",
                                          "SupplementalMathematicalOperators"} );

        /**
         * Constant for the "Miscellaneous Symbols and Arrows" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock MISCELLANEOUS_SYMBOLS_AND_ARROWS =
            new UnicodeBlock("MISCELLANEOUS_SYMBOLS_AND_ARROWS", new String[] {"Miscellaneous Symbols and Arrows",
                                                                               "MiscellaneousSymbolsandArrows"});

        /**
         * Constant for the "Katakana Phonetic Extensions" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock KATAKANA_PHONETIC_EXTENSIONS =
            new UnicodeBlock("KATAKANA_PHONETIC_EXTENSIONS", new String[] {"Katakana Phonetic Extensions",
                                                                           "KatakanaPhoneticExtensions"});

        /**
         * Constant for the "Yijing Hexagram Symbols" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock YIJING_HEXAGRAM_SYMBOLS =
            new UnicodeBlock("YIJING_HEXAGRAM_SYMBOLS", new String[] {"Yijing Hexagram Symbols",
                                                                      "YijingHexagramSymbols"});

        /**
         * Constant for the "Variation Selectors" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock VARIATION_SELECTORS =
            new UnicodeBlock("VARIATION_SELECTORS", new String[] {"Variation Selectors", "VariationSelectors"});

        /**
         * Constant for the "Linear B Syllabary" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock LINEAR_B_SYLLABARY =
            new UnicodeBlock("LINEAR_B_SYLLABARY", new String[] {"Linear B Syllabary", "LinearBSyllabary"});

        /**
         * Constant for the "Linear B Ideograms" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock LINEAR_B_IDEOGRAMS =
            new UnicodeBlock("LINEAR_B_IDEOGRAMS", new String[] {"Linear B Ideograms", "LinearBIdeograms"});

        /**
         * Constant for the "Aegean Numbers" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock AEGEAN_NUMBERS =
            new UnicodeBlock("AEGEAN_NUMBERS", new String[] {"Aegean Numbers", "AegeanNumbers"});

        /**
         * Constant for the "Old Italic" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock OLD_ITALIC =
            new UnicodeBlock("OLD_ITALIC", new String[] {"Old Italic", "OldItalic"});

        /**
         * Constant for the "Gothic" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock GOTHIC = new UnicodeBlock("GOTHIC");

        /**
         * Constant for the "Ugaritic" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock UGARITIC = new UnicodeBlock("UGARITIC");

        /**
         * Constant for the "Deseret" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock DESERET = new UnicodeBlock("DESERET");

        /**
         * Constant for the "Shavian" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock SHAVIAN = new UnicodeBlock("SHAVIAN");

        /**
         * Constant for the "Osmanya" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock OSMANYA = new UnicodeBlock("OSMANYA");

        /**
         * Constant for the "Cypriot Syllabary" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock CYPRIOT_SYLLABARY =
            new UnicodeBlock("CYPRIOT_SYLLABARY", new String[] {"Cypriot Syllabary", "CypriotSyllabary"});

        /**
         * Constant for the "Byzantine Musical Symbols" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock BYZANTINE_MUSICAL_SYMBOLS =
            new UnicodeBlock("BYZANTINE_MUSICAL_SYMBOLS", new String[] {"Byzantine Musical Symbols",
                                                                        "ByzantineMusicalSymbols"});

        /**
         * Constant for the "Musical Symbols" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock MUSICAL_SYMBOLS =
            new UnicodeBlock("MUSICAL_SYMBOLS", new String[] {"Musical Symbols", "MusicalSymbols"});

        /**
         * Constant for the "Tai Xuan Jing Symbols" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock TAI_XUAN_JING_SYMBOLS =
            new UnicodeBlock("TAI_XUAN_JING_SYMBOLS", new String[] {"Tai Xuan Jing Symbols",
                                                                     "TaiXuanJingSymbols"});

        /**
         * Constant for the "Mathematical Alphanumeric Symbols" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock MATHEMATICAL_ALPHANUMERIC_SYMBOLS =
            new UnicodeBlock("MATHEMATICAL_ALPHANUMERIC_SYMBOLS",
                             new String[] {"Mathematical Alphanumeric Symbols", "MathematicalAlphanumericSymbols"});

        /**
         * Constant for the "CJK Unified Ideographs Extension B" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B =
            new UnicodeBlock("CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B",
                             new String[] {"CJK Unified Ideographs Extension B", "CJKUnifiedIdeographsExtensionB"});

        /**
         * Constant for the "CJK Compatibility Ideographs Supplement" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT =
            new UnicodeBlock("CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT",
                             new String[]{"CJK Compatibility Ideographs Supplement",
                                          "CJKCompatibilityIdeographsSupplement"});

        /**
         * Constant for the "Tags" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock TAGS = new UnicodeBlock("TAGS");

        /**
         * Constant for the "Variation Selectors Supplement" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock VARIATION_SELECTORS_SUPPLEMENT =
            new UnicodeBlock("VARIATION_SELECTORS_SUPPLEMENT", new String[] {"Variation Selectors Supplement",
                                                                             "VariationSelectorsSupplement"});

        /**
         * Constant for the "Supplementary Private Use Area-A" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock SUPPLEMENTARY_PRIVATE_USE_AREA_A =
            new UnicodeBlock("SUPPLEMENTARY_PRIVATE_USE_AREA_A",
                             new String[] {"Supplementary Private Use Area-A",
                                           "SupplementaryPrivateUseArea-A"});

        /**
         * Constant for the "Supplementary Private Use Area-B" Unicode character block.
         * @since 1.5
         */
        public static final UnicodeBlock SUPPLEMENTARY_PRIVATE_USE_AREA_B =
            new UnicodeBlock("SUPPLEMENTARY_PRIVATE_USE_AREA_B",
                             new String[] {"Supplementary Private Use Area-B",
                                           "SupplementaryPrivateUseArea-B"});

        /**
         * Constant for the "High Surrogates" Unicode character block.
         * This block represents codepoint values in the high surrogate
         * range: 0xD800 through 0xDB7F
         *
         * @since 1.5
         */
        public static final UnicodeBlock HIGH_SURROGATES =
            new UnicodeBlock("HIGH_SURROGATES", new String[] {"High Surrogates", "HighSurrogates"});

        /**
         * Constant for the "High Private Use Surrogates" Unicode character block.
         * This block represents codepoint values in the high surrogate
         * range: 0xDB80 through 0xDBFF
         *
         * @since 1.5
         */
        public static final UnicodeBlock HIGH_PRIVATE_USE_SURROGATES =
            new UnicodeBlock("HIGH_PRIVATE_USE_SURROGATES", new String[] { "High Private Use Surrogates",
                                                                           "HighPrivateUseSurrogates"});

        /**
         * Constant for the "Low Surrogates" Unicode character block.
         * This block represents codepoint values in the high surrogate
         * range: 0xDC00 through 0xDFFF
         *
         * @since 1.5
         */
        public static final UnicodeBlock LOW_SURROGATES =
            new UnicodeBlock("LOW_SURROGATES", new String[] {"Low Surrogates", "LowSurrogates"});

        /**
         * Constant for the "Arabic Supplement" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock ARABIC_SUPPLEMENT =
            new UnicodeBlock("ARABIC_SUPPLEMENT",
                             new String[] { "Arabic Supplement",
                                            "ArabicSupplement"});

        /**
         * Constant for the "NKo" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock NKO = new UnicodeBlock("NKO");

        /**
         * Constant for the "Ethiopic Supplement" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock ETHIOPIC_SUPPLEMENT =
            new UnicodeBlock("ETHIOPIC_SUPPLEMENT",
                             new String[] { "Ethiopic Supplement",
                                            "EthiopicSupplement"});

        /**
         * Constant for the "New Tai Lue" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock NEW_TAI_LUE =
            new UnicodeBlock("NEW_TAI_LUE",
                             new String[] { "New Tai Lue",
                                            "NewTaiLue"});

        /**
         * Constant for the "Buginese" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock BUGINESE =
            new UnicodeBlock("BUGINESE");

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
         * Constant for the "Lepcha" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock LEPCHA = new UnicodeBlock("LEPCHA");

        /**
         * Constant for the "Ol Chiki" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock OL_CHIKI =
            new UnicodeBlock("OL_CHIKI",
                             new String[] { "Ol Chiki",
                                            "OlChiki"});

        /**
         * Constant for the "Phonetic Extensions Supplement" Unicode character
         * block.
         * @since 1.7
         */
        public static final UnicodeBlock PHONETIC_EXTENSIONS_SUPPLEMENT =
            new UnicodeBlock("PHONETIC_EXTENSIONS_SUPPLEMENT",
                             new String[] { "Phonetic Extensions Supplement",
                                            "PhoneticExtensionsSupplement"});

        /**
         * Constant for the "Combining Diacritical Marks Supplement" Unicode
         * character block.
         * @since 1.7
         */
        public static final UnicodeBlock COMBINING_DIACRITICAL_MARKS_SUPPLEMENT =
            new UnicodeBlock("COMBINING_DIACRITICAL_MARKS_SUPPLEMENT",
                             new String[] { "Combining Diacritical Marks Supplement",
                                            "CombiningDiacriticalMarksSupplement"});

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
                             new String[] { "Latin Extended-C",
                                            "LatinExtended-C"});

        /**
         * Constant for the "Coptic" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock COPTIC = new UnicodeBlock("COPTIC");

        /**
         * Constant for the "Georgian Supplement" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock GEORGIAN_SUPPLEMENT =
            new UnicodeBlock("GEORGIAN_SUPPLEMENT",
                             new String[] { "Georgian Supplement",
                                            "GeorgianSupplement"});

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
                             new String[] { "Ethiopic Extended",
                                            "EthiopicExtended"});

        /**
         * Constant for the "Cyrillic Extended-A" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock CYRILLIC_EXTENDED_A =
            new UnicodeBlock("CYRILLIC_EXTENDED_A",
                             new String[] { "Cyrillic Extended-A",
                                            "CyrillicExtended-A"});

        /**
         * Constant for the "Supplemental Punctuation" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock SUPPLEMENTAL_PUNCTUATION =
            new UnicodeBlock("SUPPLEMENTAL_PUNCTUATION",
                             new String[] { "Supplemental Punctuation",
                                            "SupplementalPunctuation"});

        /**
         * Constant for the "CJK Strokes" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock CJK_STROKES =
            new UnicodeBlock("CJK_STROKES",
                             new String[] { "CJK Strokes",
                                            "CJKStrokes"});

        /**
         * Constant for the "Vai" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock VAI = new UnicodeBlock("VAI");

        /**
         * Constant for the "Cyrillic Extended-B" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock CYRILLIC_EXTENDED_B =
            new UnicodeBlock("CYRILLIC_EXTENDED_B",
                             new String[] { "Cyrillic Extended-B",
                                            "CyrillicExtended-B"});

        /**
         * Constant for the "Modifier Tone Letters" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock MODIFIER_TONE_LETTERS =
            new UnicodeBlock("MODIFIER_TONE_LETTERS",
                             new String[] { "Modifier Tone Letters",
                                            "ModifierToneLetters"});

        /**
         * Constant for the "Latin Extended-D" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock LATIN_EXTENDED_D =
            new UnicodeBlock("LATIN_EXTENDED_D",
                             new String[] { "Latin Extended-D",
                                            "LatinExtended-D"});

        /**
         * Constant for the "Syloti Nagri" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock SYLOTI_NAGRI =
            new UnicodeBlock("SYLOTI_NAGRI",
                             new String[] { "Syloti Nagri",
                                            "SylotiNagri"});

        /**
         * Constant for the "Phags-pa" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock PHAGS_PA =
            new UnicodeBlock("PHAGS_PA", new String[] { "Phags-pa"});

        /**
         * Constant for the "Saurashtra" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock SAURASHTRA =
            new UnicodeBlock("SAURASHTRA");

        /**
         * Constant for the "Kayah Li" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock KAYAH_LI =
            new UnicodeBlock("KAYAH_LI",
                             new String[] { "Kayah Li",
                                            "KayahLi"});

        /**
         * Constant for the "Rejang" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock REJANG = new UnicodeBlock("REJANG");

        /**
         * Constant for the "Cham" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock CHAM = new UnicodeBlock("CHAM");

        /**
         * Constant for the "Vertical Forms" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock VERTICAL_FORMS =
            new UnicodeBlock("VERTICAL_FORMS",
                             new String[] { "Vertical Forms",
                                            "VerticalForms"});

        /**
         * Constant for the "Ancient Greek Numbers" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock ANCIENT_GREEK_NUMBERS =
            new UnicodeBlock("ANCIENT_GREEK_NUMBERS",
                             new String[] { "Ancient Greek Numbers",
                                            "AncientGreekNumbers"});

        /**
         * Constant for the "Ancient Symbols" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock ANCIENT_SYMBOLS =
            new UnicodeBlock("ANCIENT_SYMBOLS",
                             new String[] { "Ancient Symbols",
                                            "AncientSymbols"});

        /**
         * Constant for the "Phaistos Disc" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock PHAISTOS_DISC =
            new UnicodeBlock("PHAISTOS_DISC",
                             new String[] { "Phaistos Disc",
                                            "PhaistosDisc"});

        /**
         * Constant for the "Lycian" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock LYCIAN = new UnicodeBlock("LYCIAN");

        /**
         * Constant for the "Carian" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock CARIAN = new UnicodeBlock("CARIAN");

        /**
         * Constant for the "Old Persian" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock OLD_PERSIAN =
            new UnicodeBlock("OLD_PERSIAN",
                             new String[] { "Old Persian",
                                            "OldPersian"});

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
        public static final UnicodeBlock LYDIAN = new UnicodeBlock("LYDIAN");

        /**
         * Constant for the "Kharoshthi" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock KHAROSHTHI =
            new UnicodeBlock("KHAROSHTHI");

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
                             new String[] { "Cuneiform Numbers and Punctuation",
                                            "CuneiformNumbersandPunctuation"});

        /**
         * Constant for the "Ancient Greek Musical Notation" Unicode character
         * block.
         * @since 1.7
         */
        public static final UnicodeBlock ANCIENT_GREEK_MUSICAL_NOTATION =
            new UnicodeBlock("ANCIENT_GREEK_MUSICAL_NOTATION",
                             new String[] { "Ancient Greek Musical Notation",
                                            "AncientGreekMusicalNotation"});

        /**
         * Constant for the "Counting Rod Numerals" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock COUNTING_ROD_NUMERALS =
            new UnicodeBlock("COUNTING_ROD_NUMERALS",
                             new String[] { "Counting Rod Numerals",
                                            "CountingRodNumerals"});

        /**
         * Constant for the "Mahjong Tiles" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock MAHJONG_TILES =
            new UnicodeBlock("MAHJONG_TILES",
                             new String[] { "Mahjong Tiles",
                                            "MahjongTiles"});

        /**
         * Constant for the "Domino Tiles" Unicode character block.
         * @since 1.7
         */
        public static final UnicodeBlock DOMINO_TILES =
            new UnicodeBlock("DOMINO_TILES",
                             new String[] { "Domino Tiles",
                                            "DominoTiles"});

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
            0x0700,   // 0700..074F; Syria
            0x0750,   // 0750..077F; Arabic Supplement
            0x0780,   // 0780..07BF; Thaana
            0x07C0,   // 07C0..07FF; NKo
            0x0800,   //             unassigned
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
            0x18B0,   //             unassigned
            0x1900,   // 1900..194F; Limbu
            0x1950,   // 1950..197F; Tai Le
            0x1980,   // 1980..19DF; New Tai Lue
            0x19E0,   // 19E0..19FF; Khmer Symbols
            0x1A00,   // 1A00..1A1F; Buginese
            0x1A20,   //             unassigned
            0x1B00,   // 1B00..1B7F; Balinese
            0x1B80,   // 1B80..1BBF; Sundanese
            0x1BC0,   //             unassigned
            0x1C00,   // 1C00..1C4F; Lepcha
            0x1C50,   // 1C50..1C7F; Ol Chiki
            0x1C80,   //             unassigned
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
            0x4E00,   // 4E00..9FFF; CJK Unified Ideograph
            0xA000,   // A000..A48F; Yi Syllables
            0xA490,   // A490..A4CF; Yi Radicals
            0xA4D0,   //             unassigned
            0xA500,   // A500..A63F; Vai
            0xA640,   // A640..A69F; Cyrillic Extended-B
            0xA6A0,   //             unassigned
            0xA700,   // A700..A71F; Modifier Tone Letters
            0xA720,   // A720..A7FF; Latin Extended-D
            0xA800,   // A800..A82F; Syloti Nagri
            0xA830,   //             unassigned
            0xA840,   // A840..A87F; Phags-pa
            0xA880,   // A880..A8DF; Saurashtra
            0xA8E0,   //             unassigned
            0xA900,   // A900..A92F; Kayah Li
            0xA930,   // A930..A95F; Rejang
            0xA960,   //             unassigned
            0xAA00,   // AA00..AA5F; Cham
            0xAA60,   //             unassigned
            0xAC00,   // AC00..D7AF; Hangul Syllables
            0xD7B0,   //             unassigned
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
            0x10400,  // 10400..1044F; Desere
            0x10450,  // 10450..1047F; Shavian
            0x10480,  // 10480..104AF; Osmanya
            0x104B0,  //               unassigned
            0x10800,  // 10800..1083F; Cypriot Syllabary
            0x10840,  //               unassigned
            0x10900,  // 10900..1091F; Phoenician
            0x10920,  // 10920..1093F; Lydian
            0x10940,  //               unassigned
            0x10A00,  // 10A00..10A5F; Kharoshthi
            0x10A60,  //               unassigned
            0x12000,  // 12000..123FF; Cuneiform
            0x12400,  // 12400..1247F; Cuneiform Numbers and Punctuation
            0x12480,  //               unassigned
            0x1D000,  // 1D000..1D0FF; Byzantine Musical Symbols
            0x1D100,  // 1D100..1D1FF; Musical Symbols
            0x1D200,  // 1D200..1D24F; Ancient Greek Musical Notation
            0x1D250,  //               unassigned
            0x1D300,  // 1D300..1D35F; Tai Xuan Jing Symbols
            0x1D360,  // 1D360..1D37F; Counting Rod Numerals
            0x1D380,  //               unassigned
            0x1D400,  // 1D400..1D7FF; Mathematical Alphanumeric Symbols
            0x1D800,  //               unassigned
            0x1F000,  // 1F000..1F02F; Mahjong Tiles
            0x1F030,  // 1F030..1F09F; Domino Tiles
            0x1F0A0,  //               unassigned
            0x20000,  // 20000..2A6DF; CJK Unified Ideographs Extension B
            0x2A6E0,  //               unassigned
            0x2F800,  // 2F800..2FA1F; CJK Compatibility Ideographs Supplement
            0x2FA20,  //               unassigned
            0xE0000,  // E0000..E007F; Tags
            0xE0080,  //               unassigned
            0xE0100,  // E0100..E01EF; Variation Selectors Supplement
            0xE01F0,  //               unassigned
            0xF0000,  // F0000..FFFFF; Supplementary Private Use Area-A
            0x100000, // 100000..10FFFF; Supplementary Private Use Area-B
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
            null,
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
            null,
            LIMBU,
            TAI_LE,
            NEW_TAI_LUE,
            KHMER_SYMBOLS,
            BUGINESE,
            null,
            BALINESE,
            SUNDANESE,
            null,
            LEPCHA,
            OL_CHIKI,
            null,
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
            null,
            VAI,
            CYRILLIC_EXTENDED_B,
            null,
            MODIFIER_TONE_LETTERS,
            LATIN_EXTENDED_D,
            SYLOTI_NAGRI,
            null,
            PHAGS_PA,
            SAURASHTRA,
            null,
            KAYAH_LI,
            REJANG,
            null,
            CHAM,
            null,
            HANGUL_SYLLABLES,
            null,
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
            null,
            PHOENICIAN,
            LYDIAN,
            null,
            KHAROSHTHI,
            null,
            CUNEIFORM,
            CUNEIFORM_NUMBERS_AND_PUNCTUATION,
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
            MAHJONG_TILES,
            DOMINO_TILES,
            null,
            CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B,
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
         * given character, or <code>null</code> if the character is not a
         * member of a defined block.
         *
                 * <p><b>Note:</b> This method cannot handle <a
                 * href="Character.html#supplementary"> supplementary
                 * characters</a>. To support all Unicode characters,
                 * including supplementary characters, use the {@link
                 * #of(int)} method.
         *
         * @param   c  The character in question
         * @return  The <code>UnicodeBlock</code> instance representing the
         *          Unicode block of which this character is a member, or
         *          <code>null</code> if the character is not a member of any
         *          Unicode block
         */
        public static UnicodeBlock of(char c) {
            return of((int)c);
        }


        /**
         * Returns the object representing the Unicode block
         * containing the given character (Unicode code point), or
         * <code>null</code> if the character is not a member of a
         * defined block.
         *
                 * @param   codePoint the character (Unicode code point) in question.
         * @return  The <code>UnicodeBlock</code> instance representing the
         *          Unicode block of which this character is a member, or
         *          <code>null</code> if the character is not a member of any
         *          Unicode block
                 * @exception IllegalArgumentException if the specified
                 * <code>codePoint</code> is an invalid Unicode code point.
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
         *  hyphens in the canonical name with underscores.
         * </ol>
         * Finally, character case is ignored for all of the valid block name forms.
         * For example, "BASIC_LATIN" and "basic_latin" are both valid block names.
         * The en_US locale's case mapping rules are used to provide case-insensitive
         * string comparisons for block name validation.
         * <p>
         * If the Unicode Standard changes block names, both the previous and
         * current names will be accepted.
         *
         * @param blockName A <code>UnicodeBlock</code> name.
         * @return The <code>UnicodeBlock</code> instance identified
         *         by <code>blockName</code>
         * @throws IllegalArgumentException if <code>blockName</code> is an
         *         invalid name
         * @throws NullPointerException if <code>blockName</code> is null
         * @since 1.5
         */
        public static final UnicodeBlock forName(String blockName) {
            UnicodeBlock block = (UnicodeBlock)map.get(blockName.toUpperCase(Locale.US));
            if (block == null) {
                throw new IllegalArgumentException();
            }
            return block;
        }
    }


    /**
     * The value of the <code>Character</code>.
     *
     * @serial
     */
    private final char value;

    /** use serialVersionUID from JDK 1.0.2 for interoperability */
    private static final long serialVersionUID = 3786198910865385080L;

    /**
     * Constructs a newly allocated <code>Character</code> object that
     * represents the specified <code>char</code> value.
     *
     * @param  value   the value to be represented by the
     *                  <code>Character</code> object.
     */
    public Character(char value) {
        this.value = value;
    }

    private static class CharacterCache {
        private CharacterCache(){}

        static final Character cache[] = new Character[127 + 1];

        static {
            for(int i = 0; i < cache.length; i++)
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
     * This method will always cache values in the range '&#92;u0000'
     * to '&#92;u007f'", inclusive, and may cache other values outside
     * of this range.
     *
     * @param  c a char value.
     * @return a <tt>Character</tt> instance representing <tt>c</tt>.
     * @since  1.5
     */
    public static Character valueOf(char c) {
        if(c <= 127) { // must cache
            return CharacterCache.cache[(int)c];
        }
        return new Character(c);
    }

    /**
     * Returns the value of this <code>Character</code> object.
     * @return  the primitive <code>char</code> value represented by
     *          this object.
     */
    public char charValue() {
        return value;
    }

    /**
     * Returns a hash code for this <code>Character</code>.
     * @return  a hash code value for this object.
     */
    public int hashCode() {
        return (int)value;
    }

    /**
     * Compares this object against the specified object.
     * The result is <code>true</code> if and only if the argument is not
     * <code>null</code> and is a <code>Character</code> object that
     * represents the same <code>char</code> value as this object.
     *
     * @param   obj   the object to compare with.
     * @return  <code>true</code> if the objects are the same;
     *          <code>false</code> otherwise.
     */
    public boolean equals(Object obj) {
        if (obj instanceof Character) {
            return value == ((Character)obj).charValue();
        }
        return false;
    }

    /**
     * Returns a <code>String</code> object representing this
     * <code>Character</code>'s value.  The result is a string of
     * length 1 whose sole component is the primitive
     * <code>char</code> value represented by this
     * <code>Character</code> object.
     *
     * @return  a string representation of this object.
     */
    public String toString() {
        char buf[] = {value};
        return String.valueOf(buf);
    }

    /**
     * Returns a <code>String</code> object representing the
     * specified <code>char</code>.  The result is a string of length
     * 1 consisting solely of the specified <code>char</code>.
     *
     * @param c the <code>char</code> to be converted
     * @return the string representation of the specified <code>char</code>
     * @since 1.4
     */
    public static String toString(char c) {
        return String.valueOf(c);
    }

    /**
     * Determines whether the specified code point is a valid Unicode
     * code point value in the range of <code>0x0000</code> to
     * <code>0x10FFFF</code> inclusive. This method is equivalent to
     * the expression:
     *
     * <blockquote><pre>
     * codePoint >= 0x0000 && codePoint <= 0x10FFFF
     * </pre></blockquote>
     *
     * @param  codePoint the Unicode code point to be tested
     * @return <code>true</code> if the specified code point value
     * is a valid code point value;
     * <code>false</code> otherwise.
     * @since  1.5
     */
    public static boolean isValidCodePoint(int codePoint) {
        return codePoint >= MIN_CODE_POINT && codePoint <= MAX_CODE_POINT;
    }

    /**
     * Determines whether the specified character (Unicode code point)
     * is in the supplementary character range. The method call is
     * equivalent to the expression:
     * <blockquote><pre>
     * codePoint >= 0x10000 && codePoint <= 0x10FFFF
     * </pre></blockquote>
     *
     * @param  codePoint the character (Unicode code point) to be tested
     * @return <code>true</code> if the specified character is in the Unicode
     *         supplementary character range; <code>false</code> otherwise.
     * @since  1.5
     */
    public static boolean isSupplementaryCodePoint(int codePoint) {
        return codePoint >= MIN_SUPPLEMENTARY_CODE_POINT
            && codePoint <= MAX_CODE_POINT;
    }

    /**
     * Determines if the given <code>char</code> value is a
     * high-surrogate code unit (also known as <i>leading-surrogate
     * code unit</i>). Such values do not represent characters by
     * themselves, but are used in the representation of <a
     * href="#supplementary">supplementary characters</a> in the
     * UTF-16 encoding.
     *
     * <p>This method returns <code>true</code> if and only if
     * <blockquote><pre>ch >= '&#92;uD800' && ch <= '&#92;uDBFF'
     * </pre></blockquote>
     * is <code>true</code>.
     *
     * @param   ch   the <code>char</code> value to be tested.
     * @return  <code>true</code> if the <code>char</code> value
     *          is between '&#92;uD800' and '&#92;uDBFF' inclusive;
     *          <code>false</code> otherwise.
     * @see     java.lang.Character#isLowSurrogate(char)
     * @see     Character.UnicodeBlock#of(int)
     * @since   1.5
     */
    public static boolean isHighSurrogate(char ch) {
        return ch >= MIN_HIGH_SURROGATE && ch <= MAX_HIGH_SURROGATE;
    }

    /**
     * Determines if the given <code>char</code> value is a
     * low-surrogate code unit (also known as <i>trailing-surrogate code
     * unit</i>). Such values do not represent characters by themselves,
     * but are used in the representation of <a
     * href="#supplementary">supplementary characters</a> in the UTF-16 encoding.
     *
     * <p> This method returns <code>true</code> if and only if
     * <blockquote><pre>ch >= '&#92;uDC00' && ch <= '&#92;uDFFF'
     * </pre></blockquote> is <code>true</code>.
     *
     * @param   ch   the <code>char</code> value to be tested.
     * @return  <code>true</code> if the <code>char</code> value
     *          is between '&#92;uDC00' and '&#92;uDFFF' inclusive;
     *          <code>false</code> otherwise.
     * @see java.lang.Character#isHighSurrogate(char)
     * @since   1.5
     */
    public static boolean isLowSurrogate(char ch) {
        return ch >= MIN_LOW_SURROGATE && ch <= MAX_LOW_SURROGATE;
    }

    /**
     * Determines whether the specified pair of <code>char</code>
     * values is a valid surrogate pair. This method is equivalent to
     * the expression:
     * <blockquote><pre>
     * isHighSurrogate(high) && isLowSurrogate(low)
     * </pre></blockquote>
     *
     * @param  high the high-surrogate code value to be tested
     * @param  low the low-surrogate code value to be tested
     * @return <code>true</code> if the specified high and
     * low-surrogate code values represent a valid surrogate pair;
     * <code>false</code> otherwise.
     * @since  1.5
     */
    public static boolean isSurrogatePair(char high, char low) {
        return isHighSurrogate(high) && isLowSurrogate(low);
    }

    /**
     * Determines the number of <code>char</code> values needed to
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
     * @see     #isSupplementaryCodePoint(int)
     * @since   1.5
     */
    public static int charCount(int codePoint) {
        return codePoint >= MIN_SUPPLEMENTARY_CODE_POINT? 2 : 1;
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
     * <code>CharSequence</code>. If the <code>char</code> value at
     * the given index in the <code>CharSequence</code> is in the
     * high-surrogate range, the following index is less than the
     * length of the <code>CharSequence</code>, and the
     * <code>char</code> value at the following index is in the
     * low-surrogate range, then the supplementary code point
     * corresponding to this surrogate pair is returned. Otherwise,
     * the <code>char</code> value at the given index is returned.
     *
     * @param seq a sequence of <code>char</code> values (Unicode code
     * units)
     * @param index the index to the <code>char</code> values (Unicode
     * code units) in <code>seq</code> to be converted
     * @return the Unicode code point at the given index
     * @exception NullPointerException if <code>seq</code> is null.
     * @exception IndexOutOfBoundsException if the value
     * <code>index</code> is negative or not less than
     * {@link CharSequence#length() seq.length()}.
     * @since  1.5
     */
    public static int codePointAt(CharSequence seq, int index) {
        char c1 = seq.charAt(index++);
        if (isHighSurrogate(c1)) {
            if (index < seq.length()) {
                char c2 = seq.charAt(index);
                if (isLowSurrogate(c2)) {
                    return toCodePoint(c1, c2);
                }
            }
        }
        return c1;
    }

    /**
     * Returns the code point at the given index of the
     * <code>char</code> array. If the <code>char</code> value at
     * the given index in the <code>char</code> array is in the
     * high-surrogate range, the following index is less than the
     * length of the <code>char</code> array, and the
     * <code>char</code> value at the following index is in the
     * low-surrogate range, then the supplementary code point
     * corresponding to this surrogate pair is returned. Otherwise,
     * the <code>char</code> value at the given index is returned.
     *
     * @param a the <code>char</code> array
     * @param index the index to the <code>char</code> values (Unicode
     * code units) in the <code>char</code> array to be converted
     * @return the Unicode code point at the given index
     * @exception NullPointerException if <code>a</code> is null.
     * @exception IndexOutOfBoundsException if the value
     * <code>index</code> is negative or not less than
     * the length of the <code>char</code> array.
     * @since  1.5
     */
    public static int codePointAt(char[] a, int index) {
        return codePointAtImpl(a, index, a.length);
    }

    /**
     * Returns the code point at the given index of the
     * <code>char</code> array, where only array elements with
     * <code>index</code> less than <code>limit</code> can be used. If
     * the <code>char</code> value at the given index in the
     * <code>char</code> array is in the high-surrogate range, the
     * following index is less than the <code>limit</code>, and the
     * <code>char</code> value at the following index is in the
     * low-surrogate range, then the supplementary code point
     * corresponding to this surrogate pair is returned. Otherwise,
     * the <code>char</code> value at the given index is returned.
     *
     * @param a the <code>char</code> array
     * @param index the index to the <code>char</code> values (Unicode
     * code units) in the <code>char</code> array to be converted
     * @param limit the index after the last array element that can be used in the
     * <code>char</code> array
     * @return the Unicode code point at the given index
     * @exception NullPointerException if <code>a</code> is null.
     * @exception IndexOutOfBoundsException if the <code>index</code>
     * argument is negative or not less than the <code>limit</code>
     * argument, or if the <code>limit</code> argument is negative or
     * greater than the length of the <code>char</code> array.
     * @since  1.5
     */
    public static int codePointAt(char[] a, int index, int limit) {
        if (index >= limit || limit < 0 || limit > a.length) {
            throw new IndexOutOfBoundsException();
        }
        return codePointAtImpl(a, index, limit);
    }

    static int codePointAtImpl(char[] a, int index, int limit) {
        char c1 = a[index++];
        if (isHighSurrogate(c1)) {
            if (index < limit) {
                char c2 = a[index];
                if (isLowSurrogate(c2)) {
                    return toCodePoint(c1, c2);
                }
            }
        }
        return c1;
    }

    /**
     * Returns the code point preceding the given index of the
     * <code>CharSequence</code>. If the <code>char</code> value at
     * <code>(index - 1)</code> in the <code>CharSequence</code> is in
     * the low-surrogate range, <code>(index - 2)</code> is not
     * negative, and the <code>char</code> value at <code>(index -
     * 2)</code> in the <code>CharSequence</code> is in the
     * high-surrogate range, then the supplementary code point
     * corresponding to this surrogate pair is returned. Otherwise,
     * the <code>char</code> value at <code>(index - 1)</code> is
     * returned.
     *
     * @param seq the <code>CharSequence</code> instance
     * @param index the index following the code point that should be returned
     * @return the Unicode code point value before the given index.
     * @exception NullPointerException if <code>seq</code> is null.
     * @exception IndexOutOfBoundsException if the <code>index</code>
     * argument is less than 1 or greater than {@link
     * CharSequence#length() seq.length()}.
     * @since  1.5
     */
    public static int codePointBefore(CharSequence seq, int index) {
        char c2 = seq.charAt(--index);
        if (isLowSurrogate(c2)) {
            if (index > 0) {
                char c1 = seq.charAt(--index);
                if (isHighSurrogate(c1)) {
                    return toCodePoint(c1, c2);
                }
            }
        }
        return c2;
    }

    /**
     * Returns the code point preceding the given index of the
     * <code>char</code> array. If the <code>char</code> value at
     * <code>(index - 1)</code> in the <code>char</code> array is in
     * the low-surrogate range, <code>(index - 2)</code> is not
     * negative, and the <code>char</code> value at <code>(index -
     * 2)</code> in the <code>char</code> array is in the
     * high-surrogate range, then the supplementary code point
     * corresponding to this surrogate pair is returned. Otherwise,
     * the <code>char</code> value at <code>(index - 1)</code> is
     * returned.
     *
     * @param a the <code>char</code> array
     * @param index the index following the code point that should be returned
     * @return the Unicode code point value before the given index.
     * @exception NullPointerException if <code>a</code> is null.
     * @exception IndexOutOfBoundsException if the <code>index</code>
     * argument is less than 1 or greater than the length of the
     * <code>char</code> array
     * @since  1.5
     */
    public static int codePointBefore(char[] a, int index) {
        return codePointBeforeImpl(a, index, 0);
    }

    /**
     * Returns the code point preceding the given index of the
     * <code>char</code> array, where only array elements with
     * <code>index</code> greater than or equal to <code>start</code>
     * can be used. If the <code>char</code> value at <code>(index -
     * 1)</code> in the <code>char</code> array is in the
     * low-surrogate range, <code>(index - 2)</code> is not less than
     * <code>start</code>, and the <code>char</code> value at
     * <code>(index - 2)</code> in the <code>char</code> array is in
     * the high-surrogate range, then the supplementary code point
     * corresponding to this surrogate pair is returned. Otherwise,
     * the <code>char</code> value at <code>(index - 1)</code> is
     * returned.
     *
     * @param a the <code>char</code> array
     * @param index the index following the code point that should be returned
     * @param start the index of the first array element in the
     * <code>char</code> array
     * @return the Unicode code point value before the given index.
     * @exception NullPointerException if <code>a</code> is null.
     * @exception IndexOutOfBoundsException if the <code>index</code>
     * argument is not greater than the <code>start</code> argument or
     * is greater than the length of the <code>char</code> array, or
     * if the <code>start</code> argument is negative or not less than
     * the length of the <code>char</code> array.
     * @since  1.5
     */
    public static int codePointBefore(char[] a, int index, int start) {
        if (index <= start || start < 0 || start >= a.length) {
            throw new IndexOutOfBoundsException();
        }
        return codePointBeforeImpl(a, index, start);
    }

    static int codePointBeforeImpl(char[] a, int index, int start) {
        char c2 = a[--index];
        if (isLowSurrogate(c2)) {
            if (index > start) {
                char c1 = a[--index];
                if (isHighSurrogate(c1)) {
                    return toCodePoint(c1, c2);
                }
            }
        }
        return c2;
    }

    /**
     * Converts the specified character (Unicode code point) to its
     * UTF-16 representation. If the specified code point is a BMP
     * (Basic Multilingual Plane or Plane 0) value, the same value is
     * stored in <code>dst[dstIndex]</code>, and 1 is returned. If the
     * specified code point is a supplementary character, its
     * surrogate values are stored in <code>dst[dstIndex]</code>
     * (high-surrogate) and <code>dst[dstIndex+1]</code>
     * (low-surrogate), and 2 is returned.
     *
     * @param  codePoint the character (Unicode code point) to be converted.
     * @param  dst an array of <code>char</code> in which the
     * <code>codePoint</code>'s UTF-16 value is stored.
     * @param dstIndex the start index into the <code>dst</code>
     * array where the converted value is stored.
     * @return 1 if the code point is a BMP code point, 2 if the
     * code point is a supplementary code point.
     * @exception IllegalArgumentException if the specified
     * <code>codePoint</code> is not a valid Unicode code point.
     * @exception NullPointerException if the specified <code>dst</code> is null.
     * @exception IndexOutOfBoundsException if <code>dstIndex</code>
     * is negative or not less than <code>dst.length</code>, or if
     * <code>dst</code> at <code>dstIndex</code> doesn't have enough
     * array element(s) to store the resulting <code>char</code>
     * value(s). (If <code>dstIndex</code> is equal to
     * <code>dst.length-1</code> and the specified
     * <code>codePoint</code> is a supplementary character, the
     * high-surrogate value is not stored in
     * <code>dst[dstIndex]</code>.)
     * @since  1.5
     */
    public static int toChars(int codePoint, char[] dst, int dstIndex) {
        if (codePoint < 0 || codePoint > MAX_CODE_POINT) {
            throw new IllegalArgumentException();
        }
        if (codePoint < MIN_SUPPLEMENTARY_CODE_POINT) {
            dst[dstIndex] = (char) codePoint;
            return 1;
        }
        toSurrogates(codePoint, dst, dstIndex);
        return 2;
    }

    /**
     * Converts the specified character (Unicode code point) to its
     * UTF-16 representation stored in a <code>char</code> array. If
     * the specified code point is a BMP (Basic Multilingual Plane or
     * Plane 0) value, the resulting <code>char</code> array has
     * the same value as <code>codePoint</code>. If the specified code
     * point is a supplementary code point, the resulting
     * <code>char</code> array has the corresponding surrogate pair.
     *
     * @param  codePoint a Unicode code point
     * @return a <code>char</code> array having
     *         <code>codePoint</code>'s UTF-16 representation.
     * @exception IllegalArgumentException if the specified
     * <code>codePoint</code> is not a valid Unicode code point.
     * @since  1.5
     */
    public static char[] toChars(int codePoint) {
        if (codePoint < 0 || codePoint > MAX_CODE_POINT) {
            throw new IllegalArgumentException();
        }
        if (codePoint < MIN_SUPPLEMENTARY_CODE_POINT) {
                return new char[] { (char) codePoint };
        }
        char[] result = new char[2];
        toSurrogates(codePoint, result, 0);
        return result;
    }

    static void toSurrogates(int codePoint, char[] dst, int index) {
        // We write elements "backwards" to guarantee all-or-nothing
        dst[index+1] = (char)((codePoint & 0x3ff) + MIN_LOW_SURROGATE);
        dst[index] = (char)((codePoint >>> 10)
            + (MIN_HIGH_SURROGATE - (MIN_SUPPLEMENTARY_CODE_POINT >>> 10)));
    }

    /**
     * Returns the number of Unicode code points in the text range of
     * the specified char sequence. The text range begins at the
     * specified <code>beginIndex</code> and extends to the
     * <code>char</code> at index <code>endIndex - 1</code>. Thus the
     * length (in <code>char</code>s) of the text range is
     * <code>endIndex-beginIndex</code>. Unpaired surrogates within
     * the text range count as one code point each.
     *
     * @param seq the char sequence
     * @param beginIndex the index to the first <code>char</code> of
     * the text range.
     * @param endIndex the index after the last <code>char</code> of
     * the text range.
     * @return the number of Unicode code points in the specified text
     * range
     * @exception NullPointerException if <code>seq</code> is null.
     * @exception IndexOutOfBoundsException if the
     * <code>beginIndex</code> is negative, or <code>endIndex</code>
     * is larger than the length of the given sequence, or
     * <code>beginIndex</code> is larger than <code>endIndex</code>.
     * @since  1.5
     */
    public static int codePointCount(CharSequence seq, int beginIndex, int endIndex) {
        int length = seq.length();
        if (beginIndex < 0 || endIndex > length || beginIndex > endIndex) {
            throw new IndexOutOfBoundsException();
        }
        int n = 0;
        for (int i = beginIndex; i < endIndex; ) {
            n++;
            if (isHighSurrogate(seq.charAt(i++))) {
                if (i < endIndex && isLowSurrogate(seq.charAt(i))) {
                    i++;
                }
            }
        }
        return n;
    }

    /**
     * Returns the number of Unicode code points in a subarray of the
     * <code>char</code> array argument. The <code>offset</code>
     * argument is the index of the first <code>char</code> of the
     * subarray and the <code>count</code> argument specifies the
     * length of the subarray in <code>char</code>s. Unpaired
     * surrogates within the subarray count as one code point each.
     *
     * @param a the <code>char</code> array
     * @param offset the index of the first <code>char</code> in the
     * given <code>char</code> array
     * @param count the length of the subarray in <code>char</code>s
     * @return the number of Unicode code points in the specified subarray
     * @exception NullPointerException if <code>a</code> is null.
     * @exception IndexOutOfBoundsException if <code>offset</code> or
     * <code>count</code> is negative, or if <code>offset +
     * count</code> is larger than the length of the given array.
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
        int n = 0;
        for (int i = offset; i < endIndex; ) {
            n++;
            if (isHighSurrogate(a[i++])) {
                if (i < endIndex && isLowSurrogate(a[i])) {
                    i++;
                }
            }
        }
        return n;
    }

    /**
     * Returns the index within the given char sequence that is offset
     * from the given <code>index</code> by <code>codePointOffset</code>
     * code points. Unpaired surrogates within the text range given by
     * <code>index</code> and <code>codePointOffset</code> count as
     * one code point each.
     *
     * @param seq the char sequence
     * @param index the index to be offset
     * @param codePointOffset the offset in code points
     * @return the index within the char sequence
     * @exception NullPointerException if <code>seq</code> is null.
     * @exception IndexOutOfBoundsException if <code>index</code>
     *   is negative or larger then the length of the char sequence,
     *   or if <code>codePointOffset</code> is positive and the
     *   subsequence starting with <code>index</code> has fewer than
     *   <code>codePointOffset</code> code points, or if
     *   <code>codePointOffset</code> is negative and the subsequence
     *   before <code>index</code> has fewer than the absolute value
     *   of <code>codePointOffset</code> code points.
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
                if (isHighSurrogate(seq.charAt(x++))) {
                    if (x < length && isLowSurrogate(seq.charAt(x))) {
                        x++;
                    }
                }
            }
            if (i < codePointOffset) {
                throw new IndexOutOfBoundsException();
            }
        } else {
            int i;
            for (i = codePointOffset; x > 0 && i < 0; i++) {
                if (isLowSurrogate(seq.charAt(--x))) {
                    if (x > 0 && isHighSurrogate(seq.charAt(x-1))) {
                        x--;
                    }
                }
            }
            if (i < 0) {
                throw new IndexOutOfBoundsException();
            }
        }
        return x;
    }

    /**
     * Returns the index within the given <code>char</code> subarray
     * that is offset from the given <code>index</code> by
     * <code>codePointOffset</code> code points. The
     * <code>start</code> and <code>count</code> arguments specify a
     * subarray of the <code>char</code> array. Unpaired surrogates
     * within the text range given by <code>index</code> and
     * <code>codePointOffset</code> count as one code point each.
     *
     * @param a the <code>char</code> array
     * @param start the index of the first <code>char</code> of the
     * subarray
     * @param count the length of the subarray in <code>char</code>s
     * @param index the index to be offset
     * @param codePointOffset the offset in code points
     * @return the index within the subarray
     * @exception NullPointerException if <code>a</code> is null.
     * @exception IndexOutOfBoundsException
     *   if <code>start</code> or <code>count</code> is negative,
     *   or if <code>start + count</code> is larger than the length of
     *   the given array,
     *   or if <code>index</code> is less than <code>start</code> or
     *   larger then <code>start + count</code>,
     *   or if <code>codePointOffset</code> is positive and the text range
     *   starting with <code>index</code> and ending with <code>start
     *   + count - 1</code> has fewer than <code>codePointOffset</code> code
     *   points,
     *   or if <code>codePointOffset</code> is negative and the text range
     *   starting with <code>start</code> and ending with <code>index
     *   - 1</code> has fewer than the absolute value of
     *   <code>codePointOffset</code> code points.
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
                if (isHighSurrogate(a[x++])) {
                    if (x < limit && isLowSurrogate(a[x])) {
                        x++;
                    }
                }
            }
            if (i < codePointOffset) {
                throw new IndexOutOfBoundsException();
            }
        } else {
            int i;
            for (i = codePointOffset; x > start && i < 0; i++) {
                if (isLowSurrogate(a[--x])) {
                    if (x > start && isHighSurrogate(a[x-1])) {
                        x--;
                    }
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
     * by <code>Character.getType(ch)</code>, is
     * <code>LOWERCASE_LETTER</code>.
     * <p>
     * The following are examples of lowercase characters:
     * <p><blockquote><pre>
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
     * @return  <code>true</code> if the character is lowercase;
     *          <code>false</code> otherwise.
     * @see     java.lang.Character#isLowerCase(char)
     * @see     java.lang.Character#isTitleCase(char)
     * @see     java.lang.Character#toLowerCase(char)
     * @see     java.lang.Character#getType(char)
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
     * <code>LOWERCASE_LETTER</code>.
     * <p>
     * The following are examples of lowercase characters:
     * <p><blockquote><pre>
     * a b c d e f g h i j k l m n o p q r s t u v w x y z
     * '&#92;u00DF' '&#92;u00E0' '&#92;u00E1' '&#92;u00E2' '&#92;u00E3' '&#92;u00E4' '&#92;u00E5' '&#92;u00E6'
     * '&#92;u00E7' '&#92;u00E8' '&#92;u00E9' '&#92;u00EA' '&#92;u00EB' '&#92;u00EC' '&#92;u00ED' '&#92;u00EE'
     * '&#92;u00EF' '&#92;u00F0' '&#92;u00F1' '&#92;u00F2' '&#92;u00F3' '&#92;u00F4' '&#92;u00F5' '&#92;u00F6'
     * '&#92;u00F8' '&#92;u00F9' '&#92;u00FA' '&#92;u00FB' '&#92;u00FC' '&#92;u00FD' '&#92;u00FE' '&#92;u00FF'
     * </pre></blockquote>
     * <p> Many other Unicode characters are lowercase too.
     *
     * @param   codePoint the character (Unicode code point) to be tested.
     * @return  <code>true</code> if the character is lowercase;
     *          <code>false</code> otherwise.
     * @see     java.lang.Character#isLowerCase(int)
     * @see     java.lang.Character#isTitleCase(int)
     * @see     java.lang.Character#toLowerCase(int)
     * @see     java.lang.Character#getType(int)
     * @since   1.5
     */
    public static boolean isLowerCase(int codePoint) {
        return getType(codePoint) == Character.LOWERCASE_LETTER;
    }

   /**
     * Determines if the specified character is an uppercase character.
     * <p>
     * A character is uppercase if its general category type, provided by
     * <code>Character.getType(ch)</code>, is <code>UPPERCASE_LETTER</code>.
     * <p>
     * The following are examples of uppercase characters:
     * <p><blockquote><pre>
     * A B C D E F G H I J K L M N O P Q R S T U V W X Y Z
     * '&#92;u00C0' '&#92;u00C1' '&#92;u00C2' '&#92;u00C3' '&#92;u00C4' '&#92;u00C5' '&#92;u00C6' '&#92;u00C7'
     * '&#92;u00C8' '&#92;u00C9' '&#92;u00CA' '&#92;u00CB' '&#92;u00CC' '&#92;u00CD' '&#92;u00CE' '&#92;u00CF'
     * '&#92;u00D0' '&#92;u00D1' '&#92;u00D2' '&#92;u00D3' '&#92;u00D4' '&#92;u00D5' '&#92;u00D6' '&#92;u00D8'
     * '&#92;u00D9' '&#92;u00DA' '&#92;u00DB' '&#92;u00DC' '&#92;u00DD' '&#92;u00DE'
     * </pre></blockquote>
     * <p> Many other Unicode characters are uppercase too.<p>
     *
     * <p><b>Note:</b> This method cannot handle <a
     * href="#supplementary"> supplementary characters</a>. To support
     * all Unicode characters, including supplementary characters, use
     * the {@link #isUpperCase(int)} method.
     *
     * @param   ch   the character to be tested.
     * @return  <code>true</code> if the character is uppercase;
     *          <code>false</code> otherwise.
     * @see     java.lang.Character#isLowerCase(char)
     * @see     java.lang.Character#isTitleCase(char)
     * @see     java.lang.Character#toUpperCase(char)
     * @see     java.lang.Character#getType(char)
     * @since   1.0
     */
    public static boolean isUpperCase(char ch) {
        return isUpperCase((int)ch);
    }

    /**
     * Determines if the specified character (Unicode code point) is an uppercase character.
     * <p>
     * A character is uppercase if its general category type, provided by
     * {@link Character#getType(int) getType(codePoint)}, is <code>UPPERCASE_LETTER</code>.
     * <p>
     * The following are examples of uppercase characters:
     * <p><blockquote><pre>
     * A B C D E F G H I J K L M N O P Q R S T U V W X Y Z
     * '&#92;u00C0' '&#92;u00C1' '&#92;u00C2' '&#92;u00C3' '&#92;u00C4' '&#92;u00C5' '&#92;u00C6' '&#92;u00C7'
     * '&#92;u00C8' '&#92;u00C9' '&#92;u00CA' '&#92;u00CB' '&#92;u00CC' '&#92;u00CD' '&#92;u00CE' '&#92;u00CF'
     * '&#92;u00D0' '&#92;u00D1' '&#92;u00D2' '&#92;u00D3' '&#92;u00D4' '&#92;u00D5' '&#92;u00D6' '&#92;u00D8'
     * '&#92;u00D9' '&#92;u00DA' '&#92;u00DB' '&#92;u00DC' '&#92;u00DD' '&#92;u00DE'
     * </pre></blockquote>
     * <p> Many other Unicode characters are uppercase too.<p>
     *
     * @param   codePoint the character (Unicode code point) to be tested.
     * @return  <code>true</code> if the character is uppercase;
     *          <code>false</code> otherwise.
     * @see     java.lang.Character#isLowerCase(int)
     * @see     java.lang.Character#isTitleCase(int)
     * @see     java.lang.Character#toUpperCase(int)
     * @see     java.lang.Character#getType(int)
     * @since   1.5
     */
    public static boolean isUpperCase(int codePoint) {
        return getType(codePoint) == Character.UPPERCASE_LETTER;
    }

    /**
     * Determines if the specified character is a titlecase character.
     * <p>
     * A character is a titlecase character if its general
     * category type, provided by <code>Character.getType(ch)</code>,
     * is <code>TITLECASE_LETTER</code>.
     * <p>
     * Some characters look like pairs of Latin letters. For example, there
     * is an uppercase letter that looks like "LJ" and has a corresponding
     * lowercase letter that looks like "lj". A third form, which looks like "Lj",
     * is the appropriate form to use when rendering a word in lowercase
     * with initial capitals, as for a book title.
     * <p>
     * These are some of the Unicode characters for which this method returns
     * <code>true</code>:
     * <ul>
     * <li><code>LATIN CAPITAL LETTER D WITH SMALL LETTER Z WITH CARON</code>
     * <li><code>LATIN CAPITAL LETTER L WITH SMALL LETTER J</code>
     * <li><code>LATIN CAPITAL LETTER N WITH SMALL LETTER J</code>
     * <li><code>LATIN CAPITAL LETTER D WITH SMALL LETTER Z</code>
     * </ul>
     * <p> Many other Unicode characters are titlecase too.<p>
     *
     * <p><b>Note:</b> This method cannot handle <a
     * href="#supplementary"> supplementary characters</a>. To support
     * all Unicode characters, including supplementary characters, use
     * the {@link #isTitleCase(int)} method.
     *
     * @param   ch   the character to be tested.
     * @return  <code>true</code> if the character is titlecase;
     *          <code>false</code> otherwise.
     * @see     java.lang.Character#isLowerCase(char)
     * @see     java.lang.Character#isUpperCase(char)
     * @see     java.lang.Character#toTitleCase(char)
     * @see     java.lang.Character#getType(char)
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
     * is <code>TITLECASE_LETTER</code>.
     * <p>
     * Some characters look like pairs of Latin letters. For example, there
     * is an uppercase letter that looks like "LJ" and has a corresponding
     * lowercase letter that looks like "lj". A third form, which looks like "Lj",
     * is the appropriate form to use when rendering a word in lowercase
     * with initial capitals, as for a book title.
     * <p>
     * These are some of the Unicode characters for which this method returns
     * <code>true</code>:
     * <ul>
     * <li><code>LATIN CAPITAL LETTER D WITH SMALL LETTER Z WITH CARON</code>
     * <li><code>LATIN CAPITAL LETTER L WITH SMALL LETTER J</code>
     * <li><code>LATIN CAPITAL LETTER N WITH SMALL LETTER J</code>
     * <li><code>LATIN CAPITAL LETTER D WITH SMALL LETTER Z</code>
     * </ul>
     * <p> Many other Unicode characters are titlecase too.<p>
     *
     * @param   codePoint the character (Unicode code point) to be tested.
     * @return  <code>true</code> if the character is titlecase;
     *          <code>false</code> otherwise.
     * @see     java.lang.Character#isLowerCase(int)
     * @see     java.lang.Character#isUpperCase(int)
     * @see     java.lang.Character#toTitleCase(int)
     * @see     java.lang.Character#getType(int)
     * @since   1.5
     */
    public static boolean isTitleCase(int codePoint) {
        return getType(codePoint) == Character.TITLECASE_LETTER;
    }

    /**
     * Determines if the specified character is a digit.
     * <p>
     * A character is a digit if its general category type, provided
     * by <code>Character.getType(ch)</code>, is
     * <code>DECIMAL_DIGIT_NUMBER</code>.
     * <p>
     * Some Unicode character ranges that contain digits:
     * <ul>
     * <li><code>'&#92;u0030'</code> through <code>'&#92;u0039'</code>,
     *     ISO-LATIN-1 digits (<code>'0'</code> through <code>'9'</code>)
     * <li><code>'&#92;u0660'</code> through <code>'&#92;u0669'</code>,
     *     Arabic-Indic digits
     * <li><code>'&#92;u06F0'</code> through <code>'&#92;u06F9'</code>,
     *     Extended Arabic-Indic digits
     * <li><code>'&#92;u0966'</code> through <code>'&#92;u096F'</code>,
     *     Devanagari digits
     * <li><code>'&#92;uFF10'</code> through <code>'&#92;uFF19'</code>,
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
     * @return  <code>true</code> if the character is a digit;
     *          <code>false</code> otherwise.
     * @see     java.lang.Character#digit(char, int)
     * @see     java.lang.Character#forDigit(int, int)
     * @see     java.lang.Character#getType(char)
     */
    public static boolean isDigit(char ch) {
        return isDigit((int)ch);
    }

    /**
     * Determines if the specified character (Unicode code point) is a digit.
     * <p>
     * A character is a digit if its general category type, provided
     * by {@link Character#getType(int) getType(codePoint)}, is
     * <code>DECIMAL_DIGIT_NUMBER</code>.
     * <p>
     * Some Unicode character ranges that contain digits:
     * <ul>
     * <li><code>'&#92;u0030'</code> through <code>'&#92;u0039'</code>,
     *     ISO-LATIN-1 digits (<code>'0'</code> through <code>'9'</code>)
     * <li><code>'&#92;u0660'</code> through <code>'&#92;u0669'</code>,
     *     Arabic-Indic digits
     * <li><code>'&#92;u06F0'</code> through <code>'&#92;u06F9'</code>,
     *     Extended Arabic-Indic digits
     * <li><code>'&#92;u0966'</code> through <code>'&#92;u096F'</code>,
     *     Devanagari digits
     * <li><code>'&#92;uFF10'</code> through <code>'&#92;uFF19'</code>,
     *     Fullwidth digits
     * </ul>
     *
     * Many other character ranges contain digits as well.
     *
     * @param   codePoint the character (Unicode code point) to be tested.
     * @return  <code>true</code> if the character is a digit;
     *          <code>false</code> otherwise.
     * @see     java.lang.Character#forDigit(int, int)
     * @see     java.lang.Character#getType(int)
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
     * @return  <code>true</code> if the character has a defined meaning
     *          in Unicode; <code>false</code> otherwise.
     * @see     java.lang.Character#isDigit(char)
     * @see     java.lang.Character#isLetter(char)
     * @see     java.lang.Character#isLetterOrDigit(char)
     * @see     java.lang.Character#isLowerCase(char)
     * @see     java.lang.Character#isTitleCase(char)
     * @see     java.lang.Character#isUpperCase(char)
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
     * @return  <code>true</code> if the character has a defined meaning
     *          in Unicode; <code>false</code> otherwise.
     * @see     java.lang.Character#isDigit(int)
     * @see     java.lang.Character#isLetter(int)
     * @see     java.lang.Character#isLetterOrDigit(int)
     * @see     java.lang.Character#isLowerCase(int)
     * @see     java.lang.Character#isTitleCase(int)
     * @see     java.lang.Character#isUpperCase(int)
     * @since   1.5
     */
    public static boolean isDefined(int codePoint) {
        return getType(codePoint) != Character.UNASSIGNED;
    }

    /**
     * Determines if the specified character is a letter.
     * <p>
     * A character is considered to be a letter if its general
     * category type, provided by <code>Character.getType(ch)</code>,
     * is any of the following:
     * <ul>
     * <li> <code>UPPERCASE_LETTER</code>
     * <li> <code>LOWERCASE_LETTER</code>
     * <li> <code>TITLECASE_LETTER</code>
     * <li> <code>MODIFIER_LETTER</code>
     * <li> <code>OTHER_LETTER</code>
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
     * @return  <code>true</code> if the character is a letter;
     *          <code>false</code> otherwise.
     * @see     java.lang.Character#isDigit(char)
     * @see     java.lang.Character#isJavaIdentifierStart(char)
     * @see     java.lang.Character#isJavaLetter(char)
     * @see     java.lang.Character#isJavaLetterOrDigit(char)
     * @see     java.lang.Character#isLetterOrDigit(char)
     * @see     java.lang.Character#isLowerCase(char)
     * @see     java.lang.Character#isTitleCase(char)
     * @see     java.lang.Character#isUnicodeIdentifierStart(char)
     * @see     java.lang.Character#isUpperCase(char)
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
     * <li> <code>UPPERCASE_LETTER</code>
     * <li> <code>LOWERCASE_LETTER</code>
     * <li> <code>TITLECASE_LETTER</code>
     * <li> <code>MODIFIER_LETTER</code>
     * <li> <code>OTHER_LETTER</code>
     * </ul>
     *
     * Not all letters have case. Many characters are
     * letters but are neither uppercase nor lowercase nor titlecase.
     *
     * @param   codePoint the character (Unicode code point) to be tested.
     * @return  <code>true</code> if the character is a letter;
     *          <code>false</code> otherwise.
     * @see     java.lang.Character#isDigit(int)
     * @see     java.lang.Character#isJavaIdentifierStart(int)
     * @see     java.lang.Character#isLetterOrDigit(int)
     * @see     java.lang.Character#isLowerCase(int)
     * @see     java.lang.Character#isTitleCase(int)
     * @see     java.lang.Character#isUnicodeIdentifierStart(int)
     * @see     java.lang.Character#isUpperCase(int)
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
     * <code>Character.isLetter(char ch)</code> or
     * <code>Character.isDigit(char ch)</code> returns
     * <code>true</code> for the character.
     *
     * <p><b>Note:</b> This method cannot handle <a
     * href="#supplementary"> supplementary characters</a>. To support
     * all Unicode characters, including supplementary characters, use
     * the {@link #isLetterOrDigit(int)} method.
     *
     * @param   ch   the character to be tested.
     * @return  <code>true</code> if the character is a letter or digit;
     *          <code>false</code> otherwise.
     * @see     java.lang.Character#isDigit(char)
     * @see     java.lang.Character#isJavaIdentifierPart(char)
     * @see     java.lang.Character#isJavaLetter(char)
     * @see     java.lang.Character#isJavaLetterOrDigit(char)
     * @see     java.lang.Character#isLetter(char)
     * @see     java.lang.Character#isUnicodeIdentifierPart(char)
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
     * <code>true</code> for the character.
     *
     * @param   codePoint the character (Unicode code point) to be tested.
     * @return  <code>true</code> if the character is a letter or digit;
     *          <code>false</code> otherwise.
     * @see     java.lang.Character#isDigit(int)
     * @see     java.lang.Character#isJavaIdentifierPart(int)
     * @see     java.lang.Character#isLetter(int)
     * @see     java.lang.Character#isUnicodeIdentifierPart(int)
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
     * <li> {@link #isLetter(char) isLetter(ch)} returns <code>true</code>
     * <li> {@link #getType(char) getType(ch)} returns <code>LETTER_NUMBER</code>
     * <li> ch is a currency symbol (such as "$")
     * <li> ch is a connecting punctuation character (such as "_").
     * </ul>
     *
     * @param   ch the character to be tested.
     * @return  <code>true</code> if the character may start a Java
     *          identifier; <code>false</code> otherwise.
     * @see     java.lang.Character#isJavaLetterOrDigit(char)
     * @see     java.lang.Character#isJavaIdentifierStart(char)
     * @see     java.lang.Character#isJavaIdentifierPart(char)
     * @see     java.lang.Character#isLetter(char)
     * @see     java.lang.Character#isLetterOrDigit(char)
     * @see     java.lang.Character#isUnicodeIdentifierStart(char)
     * @since   1.02
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
     * <li>  it is a currency symbol (such as <code>'$'</code>)
     * <li>  it is a connecting punctuation character (such as <code>'_'</code>)
     * <li>  it is a digit
     * <li>  it is a numeric letter (such as a Roman numeral character)
     * <li>  it is a combining mark
     * <li>  it is a non-spacing mark
     * <li> <code>isIdentifierIgnorable</code> returns
     * <code>true</code> for the character.
     * </ul>
     *
     * @param   ch the character to be tested.
     * @return  <code>true</code> if the character may be part of a
     *          Java identifier; <code>false</code> otherwise.
     * @see     java.lang.Character#isJavaLetter(char)
     * @see     java.lang.Character#isJavaIdentifierStart(char)
     * @see     java.lang.Character#isJavaIdentifierPart(char)
     * @see     java.lang.Character#isLetter(char)
     * @see     java.lang.Character#isLetterOrDigit(char)
     * @see     java.lang.Character#isUnicodeIdentifierPart(char)
     * @see     java.lang.Character#isIdentifierIgnorable(char)
     * @since   1.02
     * @deprecated Replaced by isJavaIdentifierPart(char).
     */
    @Deprecated
    public static boolean isJavaLetterOrDigit(char ch) {
        return isJavaIdentifierPart(ch);
    }

    /**
     * Determines if the specified character is
     * permissible as the first character in a Java identifier.
     * <p>
     * A character may start a Java identifier if and only if
     * one of the following conditions is true:
     * <ul>
     * <li> {@link #isLetter(char) isLetter(ch)} returns <code>true</code>
     * <li> {@link #getType(char) getType(ch)} returns <code>LETTER_NUMBER</code>
     * <li> ch is a currency symbol (such as "$")
     * <li> ch is a connecting punctuation character (such as "_").
     * </ul>
     *
     * <p><b>Note:</b> This method cannot handle <a
     * href="#supplementary"> supplementary characters</a>. To support
     * all Unicode characters, including supplementary characters, use
     * the {@link #isJavaIdentifierStart(int)} method.
     *
     * @param   ch the character to be tested.
     * @return  <code>true</code> if the character may start a Java identifier;
     *          <code>false</code> otherwise.
     * @see     java.lang.Character#isJavaIdentifierPart(char)
     * @see     java.lang.Character#isLetter(char)
     * @see     java.lang.Character#isUnicodeIdentifierStart(char)
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
     *      returns <code>true</code>
     * <li> {@link #getType(int) getType(codePoint)}
     *      returns <code>LETTER_NUMBER</code>
     * <li> the referenced character is a currency symbol (such as "$")
     * <li> the referenced character is a connecting punctuation character
     *      (such as "_").
     * </ul>
     *
     * @param   codePoint the character (Unicode code point) to be tested.
     * @return  <code>true</code> if the character may start a Java identifier;
     *          <code>false</code> otherwise.
     * @see     java.lang.Character#isJavaIdentifierPart(int)
     * @see     java.lang.Character#isLetter(int)
     * @see     java.lang.Character#isUnicodeIdentifierStart(int)
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
     * <li>  it is a currency symbol (such as <code>'$'</code>)
     * <li>  it is a connecting punctuation character (such as <code>'_'</code>)
     * <li>  it is a digit
     * <li>  it is a numeric letter (such as a Roman numeral character)
     * <li>  it is a combining mark
     * <li>  it is a non-spacing mark
     * <li> <code>isIdentifierIgnorable</code> returns
     * <code>true</code> for the character
     * </ul>
     *
     * <p><b>Note:</b> This method cannot handle <a
     * href="#supplementary"> supplementary characters</a>. To support
     * all Unicode characters, including supplementary characters, use
     * the {@link #isJavaIdentifierPart(int)} method.
     *
     * @param   ch      the character to be tested.
     * @return <code>true</code> if the character may be part of a
     *          Java identifier; <code>false</code> otherwise.
     * @see     java.lang.Character#isIdentifierIgnorable(char)
     * @see     java.lang.Character#isJavaIdentifierStart(char)
     * @see     java.lang.Character#isLetterOrDigit(char)
     * @see     java.lang.Character#isUnicodeIdentifierPart(char)
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
     * <li>  it is a currency symbol (such as <code>'$'</code>)
     * <li>  it is a connecting punctuation character (such as <code>'_'</code>)
     * <li>  it is a digit
     * <li>  it is a numeric letter (such as a Roman numeral character)
     * <li>  it is a combining mark
     * <li>  it is a non-spacing mark
     * <li> {@link #isIdentifierIgnorable(int)
     * isIdentifierIgnorable(codePoint)} returns <code>true</code> for
     * the character
     * </ul>
     *
     * @param   codePoint the character (Unicode code point) to be tested.
     * @return <code>true</code> if the character may be part of a
     *          Java identifier; <code>false</code> otherwise.
     * @see     java.lang.Character#isIdentifierIgnorable(int)
     * @see     java.lang.Character#isJavaIdentifierStart(int)
     * @see     java.lang.Character#isLetterOrDigit(int)
     * @see     java.lang.Character#isUnicodeIdentifierPart(int)
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
     * <li> {@link #isLetter(char) isLetter(ch)} returns <code>true</code>
     * <li> {@link #getType(char) getType(ch)} returns
     *      <code>LETTER_NUMBER</code>.
     * </ul>
     *
     * <p><b>Note:</b> This method cannot handle <a
     * href="#supplementary"> supplementary characters</a>. To support
     * all Unicode characters, including supplementary characters, use
     * the {@link #isUnicodeIdentifierStart(int)} method.
     *
     * @param   ch      the character to be tested.
     * @return  <code>true</code> if the character may start a Unicode
     *          identifier; <code>false</code> otherwise.
     * @see     java.lang.Character#isJavaIdentifierStart(char)
     * @see     java.lang.Character#isLetter(char)
     * @see     java.lang.Character#isUnicodeIdentifierPart(char)
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
     *      returns <code>true</code>
     * <li> {@link #getType(int) getType(codePoint)}
     *      returns <code>LETTER_NUMBER</code>.
     * </ul>
     * @param   codePoint the character (Unicode code point) to be tested.
     * @return  <code>true</code> if the character may start a Unicode
     *          identifier; <code>false</code> otherwise.
     * @see     java.lang.Character#isJavaIdentifierStart(int)
     * @see     java.lang.Character#isLetter(int)
     * @see     java.lang.Character#isUnicodeIdentifierPart(int)
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
     * <li>  it is a connecting punctuation character (such as <code>'_'</code>)
     * <li>  it is a digit
     * <li>  it is a numeric letter (such as a Roman numeral character)
     * <li>  it is a combining mark
     * <li>  it is a non-spacing mark
     * <li> <code>isIdentifierIgnorable</code> returns
     * <code>true</code> for this character.
     * </ul>
     *
     * <p><b>Note:</b> This method cannot handle <a
     * href="#supplementary"> supplementary characters</a>. To support
     * all Unicode characters, including supplementary characters, use
     * the {@link #isUnicodeIdentifierPart(int)} method.
     *
     * @param   ch      the character to be tested.
     * @return  <code>true</code> if the character may be part of a
     *          Unicode identifier; <code>false</code> otherwise.
     * @see     java.lang.Character#isIdentifierIgnorable(char)
     * @see     java.lang.Character#isJavaIdentifierPart(char)
     * @see     java.lang.Character#isLetterOrDigit(char)
     * @see     java.lang.Character#isUnicodeIdentifierStart(char)
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
     * <li>  it is a connecting punctuation character (such as <code>'_'</code>)
     * <li>  it is a digit
     * <li>  it is a numeric letter (such as a Roman numeral character)
     * <li>  it is a combining mark
     * <li>  it is a non-spacing mark
     * <li> <code>isIdentifierIgnorable</code> returns
     * <code>true</code> for this character.
     * </ul>
     * @param   codePoint the character (Unicode code point) to be tested.
     * @return  <code>true</code> if the character may be part of a
     *          Unicode identifier; <code>false</code> otherwise.
     * @see     java.lang.Character#isIdentifierIgnorable(int)
     * @see     java.lang.Character#isJavaIdentifierPart(int)
     * @see     java.lang.Character#isLetterOrDigit(int)
     * @see     java.lang.Character#isUnicodeIdentifierStart(int)
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
     * <li><code>'&#92;u0000'</code> through <code>'&#92;u0008'</code>
     * <li><code>'&#92;u000E'</code> through <code>'&#92;u001B'</code>
     * <li><code>'&#92;u007F'</code> through <code>'&#92;u009F'</code>
     * </ul>
     *
     * <li>all characters that have the <code>FORMAT</code> general
     * category value
     * </ul>
     *
     * <p><b>Note:</b> This method cannot handle <a
     * href="#supplementary"> supplementary characters</a>. To support
     * all Unicode characters, including supplementary characters, use
     * the {@link #isIdentifierIgnorable(int)} method.
     *
     * @param   ch      the character to be tested.
     * @return  <code>true</code> if the character is an ignorable control
     *          character that may be part of a Java or Unicode identifier;
     *           <code>false</code> otherwise.
     * @see     java.lang.Character#isJavaIdentifierPart(char)
     * @see     java.lang.Character#isUnicodeIdentifierPart(char)
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
     * <li><code>'&#92;u0000'</code> through <code>'&#92;u0008'</code>
     * <li><code>'&#92;u000E'</code> through <code>'&#92;u001B'</code>
     * <li><code>'&#92;u007F'</code> through <code>'&#92;u009F'</code>
     * </ul>
     *
     * <li>all characters that have the <code>FORMAT</code> general
     * category value
     * </ul>
     *
     * @param   codePoint the character (Unicode code point) to be tested.
     * @return  <code>true</code> if the character is an ignorable control
     *          character that may be part of a Java or Unicode identifier;
     *          <code>false</code> otherwise.
     * @see     java.lang.Character#isJavaIdentifierPart(int)
     * @see     java.lang.Character#isUnicodeIdentifierPart(int)
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
     * <code>Character.isLowerCase(Character.toLowerCase(ch))</code>
     * does not always return <code>true</code> for some ranges of
     * characters, particularly those that are symbols or ideographs.
     *
     * <p>In general, {@link java.lang.String#toLowerCase()} should be used to map
     * characters to lowercase. <code>String</code> case mapping methods
     * have several benefits over <code>Character</code> case mapping methods.
     * <code>String</code> case mapping methods can perform locale-sensitive
     * mappings, context-sensitive mappings, and 1:M character mappings, whereas
     * the <code>Character</code> case mapping methods cannot.
     *
     * <p><b>Note:</b> This method cannot handle <a
     * href="#supplementary"> supplementary characters</a>. To support
     * all Unicode characters, including supplementary characters, use
     * the {@link #toLowerCase(int)} method.
     *
     * @param   ch   the character to be converted.
     * @return  the lowercase equivalent of the character, if any;
     *          otherwise, the character itself.
     * @see     java.lang.Character#isLowerCase(char)
     * @see     java.lang.String#toLowerCase()
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
     * <code>Character.isLowerCase(Character.toLowerCase(codePoint))</code>
     * does not always return <code>true</code> for some ranges of
     * characters, particularly those that are symbols or ideographs.
     *
     * <p>In general, {@link java.lang.String#toLowerCase()} should be used to map
     * characters to lowercase. <code>String</code> case mapping methods
     * have several benefits over <code>Character</code> case mapping methods.
     * <code>String</code> case mapping methods can perform locale-sensitive
     * mappings, context-sensitive mappings, and 1:M character mappings, whereas
     * the <code>Character</code> case mapping methods cannot.
     *
     * @param   codePoint   the character (Unicode code point) to be converted.
     * @return  the lowercase equivalent of the character (Unicode code
     *          point), if any; otherwise, the character itself.
     * @see     java.lang.Character#isLowerCase(int)
     * @see     java.lang.String#toLowerCase()
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
     * <code>Character.isUpperCase(Character.toUpperCase(ch))</code>
     * does not always return <code>true</code> for some ranges of
     * characters, particularly those that are symbols or ideographs.
     *
     * <p>In general, {@link java.lang.String#toUpperCase()} should be used to map
     * characters to uppercase. <code>String</code> case mapping methods
     * have several benefits over <code>Character</code> case mapping methods.
     * <code>String</code> case mapping methods can perform locale-sensitive
     * mappings, context-sensitive mappings, and 1:M character mappings, whereas
     * the <code>Character</code> case mapping methods cannot.
     *
     * <p><b>Note:</b> This method cannot handle <a
     * href="#supplementary"> supplementary characters</a>. To support
     * all Unicode characters, including supplementary characters, use
     * the {@link #toUpperCase(int)} method.
     *
     * @param   ch   the character to be converted.
     * @return  the uppercase equivalent of the character, if any;
     *          otherwise, the character itself.
     * @see     java.lang.Character#isUpperCase(char)
     * @see     java.lang.String#toUpperCase()
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
     * <code>Character.isUpperCase(Character.toUpperCase(codePoint))</code>
     * does not always return <code>true</code> for some ranges of
     * characters, particularly those that are symbols or ideographs.
     *
     * <p>In general, {@link java.lang.String#toUpperCase()} should be used to map
     * characters to uppercase. <code>String</code> case mapping methods
     * have several benefits over <code>Character</code> case mapping methods.
     * <code>String</code> case mapping methods can perform locale-sensitive
     * mappings, context-sensitive mappings, and 1:M character mappings, whereas
     * the <code>Character</code> case mapping methods cannot.
     *
     * @param   codePoint   the character (Unicode code point) to be converted.
     * @return  the uppercase equivalent of the character, if any;
     *          otherwise, the character itself.
     * @see     java.lang.Character#isUpperCase(int)
     * @see     java.lang.String#toUpperCase()
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
     * <code>char</code> argument is already a titlecase
     * <code>char</code>, the same <code>char</code> value will be
     * returned.
     * <p>
     * Note that
     * <code>Character.isTitleCase(Character.toTitleCase(ch))</code>
     * does not always return <code>true</code> for some ranges of
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
     * @see     java.lang.Character#isTitleCase(char)
     * @see     java.lang.Character#toLowerCase(char)
     * @see     java.lang.Character#toUpperCase(char)
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
     * <code>Character.isTitleCase(Character.toTitleCase(codePoint))</code>
     * does not always return <code>true</code> for some ranges of
     * characters.
     *
     * @param   codePoint   the character (Unicode code point) to be converted.
     * @return  the titlecase equivalent of the character, if any;
     *          otherwise, the character itself.
     * @see     java.lang.Character#isTitleCase(int)
     * @see     java.lang.Character#toLowerCase(int)
     * @see     java.lang.Character#toUpperCase(int)
     * @since   1.5
     */
    public static int toTitleCase(int codePoint) {
        return CharacterData.of(codePoint).toTitleCase(codePoint);
    }

    /**
     * Returns the numeric value of the character <code>ch</code> in the
     * specified radix.
     * <p>
     * If the radix is not in the range <code>MIN_RADIX</code>&nbsp;&lt;=
     * <code>radix</code>&nbsp;&lt;= <code>MAX_RADIX</code> or if the
     * value of <code>ch</code> is not a valid digit in the specified
     * radix, <code>-1</code> is returned. A character is a valid digit
     * if at least one of the following is true:
     * <ul>
     * <li>The method <code>isDigit</code> is <code>true</code> of the character
     *     and the Unicode decimal digit value of the character (or its
     *     single-character decomposition) is less than the specified radix.
     *     In this case the decimal digit value is returned.
     * <li>The character is one of the uppercase Latin letters
     *     <code>'A'</code> through <code>'Z'</code> and its code is less than
     *     <code>radix&nbsp;+ 'A'&nbsp;-&nbsp;10</code>.
     *     In this case, <code>ch&nbsp;- 'A'&nbsp;+&nbsp;10</code>
     *     is returned.
     * <li>The character is one of the lowercase Latin letters
     *     <code>'a'</code> through <code>'z'</code> and its code is less than
     *     <code>radix&nbsp;+ 'a'&nbsp;-&nbsp;10</code>.
     *     In this case, <code>ch&nbsp;- 'a'&nbsp;+&nbsp;10</code>
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
     * @see     java.lang.Character#forDigit(int, int)
     * @see     java.lang.Character#isDigit(char)
     */
    public static int digit(char ch, int radix) {
        return digit((int)ch, radix);
    }

    /**
     * Returns the numeric value of the specified character (Unicode
     * code point) in the specified radix.
     *
     * <p>If the radix is not in the range <code>MIN_RADIX</code>&nbsp;&lt;=
     * <code>radix</code>&nbsp;&lt;= <code>MAX_RADIX</code> or if the
     * character is not a valid digit in the specified
     * radix, <code>-1</code> is returned. A character is a valid digit
     * if at least one of the following is true:
     * <ul>
     * <li>The method {@link #isDigit(int) isDigit(codePoint)} is <code>true</code> of the character
     *     and the Unicode decimal digit value of the character (or its
     *     single-character decomposition) is less than the specified radix.
     *     In this case the decimal digit value is returned.
     * <li>The character is one of the uppercase Latin letters
     *     <code>'A'</code> through <code>'Z'</code> and its code is less than
     *     <code>radix&nbsp;+ 'A'&nbsp;-&nbsp;10</code>.
     *     In this case, <code>ch&nbsp;- 'A'&nbsp;+&nbsp;10</code>
     *     is returned.
     * <li>The character is one of the lowercase Latin letters
     *     <code>'a'</code> through <code>'z'</code> and its code is less than
     *     <code>radix&nbsp;+ 'a'&nbsp;-&nbsp;10</code>.
     *     In this case, <code>ch&nbsp;- 'a'&nbsp;+&nbsp;10</code>
     *     is returned.
     * </ul>
     *
     * @param   codePoint the character (Unicode code point) to be converted.
     * @param   radix   the radix.
     * @return  the numeric value represented by the character in the
     *          specified radix.
     * @see     java.lang.Character#forDigit(int, int)
     * @see     java.lang.Character#isDigit(int)
     * @since   1.5
     */
    public static int digit(int codePoint, int radix) {
        return CharacterData.of(codePoint).digit(codePoint, radix);
    }

    /**
     * Returns the <code>int</code> value that the specified Unicode
     * character represents. For example, the character
     * <code>'&#92;u216C'</code> (the roman numeral fifty) will return
     * an int with a value of 50.
     * <p>
     * The letters A-Z in their uppercase (<code>'&#92;u0041'</code> through
     * <code>'&#92;u005A'</code>), lowercase
     * (<code>'&#92;u0061'</code> through <code>'&#92;u007A'</code>), and
     * full width variant (<code>'&#92;uFF21'</code> through
     * <code>'&#92;uFF3A'</code> and <code>'&#92;uFF41'</code> through
     * <code>'&#92;uFF5A'</code>) forms have numeric values from 10
     * through 35. This is independent of the Unicode specification,
     * which does not assign numeric values to these <code>char</code>
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
     * @return  the numeric value of the character, as a nonnegative <code>int</code>
     *           value; -2 if the character has a numeric value that is not a
     *          nonnegative integer; -1 if the character has no numeric value.
     * @see     java.lang.Character#forDigit(int, int)
     * @see     java.lang.Character#isDigit(char)
     * @since   1.1
     */
    public static int getNumericValue(char ch) {
        return getNumericValue((int)ch);
    }

    /**
     * Returns the <code>int</code> value that the specified
     * character (Unicode code point) represents. For example, the character
     * <code>'&#92;u216C'</code> (the Roman numeral fifty) will return
     * an <code>int</code> with a value of 50.
     * <p>
     * The letters A-Z in their uppercase (<code>'&#92;u0041'</code> through
     * <code>'&#92;u005A'</code>), lowercase
     * (<code>'&#92;u0061'</code> through <code>'&#92;u007A'</code>), and
     * full width variant (<code>'&#92;uFF21'</code> through
     * <code>'&#92;uFF3A'</code> and <code>'&#92;uFF41'</code> through
     * <code>'&#92;uFF5A'</code>) forms have numeric values from 10
     * through 35. This is independent of the Unicode specification,
     * which does not assign numeric values to these <code>char</code>
     * values.
     * <p>
     * If the character does not have a numeric value, then -1 is returned.
     * If the character has a numeric value that cannot be represented as a
     * nonnegative integer (for example, a fractional value), then -2
     * is returned.
     *
     * @param   codePoint the character (Unicode code point) to be converted.
     * @return  the numeric value of the character, as a nonnegative <code>int</code>
     *          value; -2 if the character has a numeric value that is not a
     *          nonnegative integer; -1 if the character has no numeric value.
     * @see     java.lang.Character#forDigit(int, int)
     * @see     java.lang.Character#isDigit(int)
     * @since   1.5
     */
    public static int getNumericValue(int codePoint) {
        return CharacterData.of(codePoint).getNumericValue(codePoint);
    }

    /**
     * Determines if the specified character is ISO-LATIN-1 white space.
     * This method returns <code>true</code> for the following five
     * characters only:
     * <table>
     * <tr><td><code>'\t'</code></td>            <td><code>'&#92;u0009'</code></td>
     *     <td><code>HORIZONTAL TABULATION</code></td></tr>
     * <tr><td><code>'\n'</code></td>            <td><code>'&#92;u000A'</code></td>
     *     <td><code>NEW LINE</code></td></tr>
     * <tr><td><code>'\f'</code></td>            <td><code>'&#92;u000C'</code></td>
     *     <td><code>FORM FEED</code></td></tr>
     * <tr><td><code>'\r'</code></td>            <td><code>'&#92;u000D'</code></td>
     *     <td><code>CARRIAGE RETURN</code></td></tr>
     * <tr><td><code>'&nbsp;'</code></td>  <td><code>'&#92;u0020'</code></td>
     *     <td><code>SPACE</code></td></tr>
     * </table>
     *
     * @param      ch   the character to be tested.
     * @return     <code>true</code> if the character is ISO-LATIN-1 white
     *             space; <code>false</code> otherwise.
     * @see        java.lang.Character#isSpaceChar(char)
     * @see        java.lang.Character#isWhitespace(char)
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
     * it is specified to be a space character by the Unicode standard. This
     * method returns true if the character's general category type is any of
     * the following:
     * <ul>
     * <li> <code>SPACE_SEPARATOR</code>
     * <li> <code>LINE_SEPARATOR</code>
     * <li> <code>PARAGRAPH_SEPARATOR</code>
     * </ul>
     *
     * <p><b>Note:</b> This method cannot handle <a
     * href="#supplementary"> supplementary characters</a>. To support
     * all Unicode characters, including supplementary characters, use
     * the {@link #isSpaceChar(int)} method.
     *
     * @param   ch      the character to be tested.
     * @return  <code>true</code> if the character is a space character;
     *          <code>false</code> otherwise.
     * @see     java.lang.Character#isWhitespace(char)
     * @since   1.1
     */
    public static boolean isSpaceChar(char ch) {
        return isSpaceChar((int)ch);
    }

    /**
     * Determines if the specified character (Unicode code point) is a
     * Unicode space character.  A character is considered to be a
     * space character if and only if it is specified to be a space
     * character by the Unicode standard. This method returns true if
     * the character's general category type is any of the following:
     *
     * <ul>
     * <li> {@link #SPACE_SEPARATOR}
     * <li> {@link #LINE_SEPARATOR}
     * <li> {@link #PARAGRAPH_SEPARATOR}
     * </ul>
     *
     * @param   codePoint the character (Unicode code point) to be tested.
     * @return  <code>true</code> if the character is a space character;
     *          <code>false</code> otherwise.
     * @see     java.lang.Character#isWhitespace(int)
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
     * <li> It is a Unicode space character (<code>SPACE_SEPARATOR</code>,
     *      <code>LINE_SEPARATOR</code>, or <code>PARAGRAPH_SEPARATOR</code>)
     *      but is not also a non-breaking space (<code>'&#92;u00A0'</code>,
     *      <code>'&#92;u2007'</code>, <code>'&#92;u202F'</code>).
     * <li> It is <code>'&#92;u0009'</code>, HORIZONTAL TABULATION.
     * <li> It is <code>'&#92;u000A'</code>, LINE FEED.
     * <li> It is <code>'&#92;u000B'</code>, VERTICAL TABULATION.
     * <li> It is <code>'&#92;u000C'</code>, FORM FEED.
     * <li> It is <code>'&#92;u000D'</code>, CARRIAGE RETURN.
     * <li> It is <code>'&#92;u001C'</code>, FILE SEPARATOR.
     * <li> It is <code>'&#92;u001D'</code>, GROUP SEPARATOR.
     * <li> It is <code>'&#92;u001E'</code>, RECORD SEPARATOR.
     * <li> It is <code>'&#92;u001F'</code>, UNIT SEPARATOR.
     * </ul>
     *
     * <p><b>Note:</b> This method cannot handle <a
     * href="#supplementary"> supplementary characters</a>. To support
     * all Unicode characters, including supplementary characters, use
     * the {@link #isWhitespace(int)} method.
     *
     * @param   ch the character to be tested.
     * @return  <code>true</code> if the character is a Java whitespace
     *          character; <code>false</code> otherwise.
     * @see     java.lang.Character#isSpaceChar(char)
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
     *      but is not also a non-breaking space (<code>'&#92;u00A0'</code>,
     *      <code>'&#92;u2007'</code>, <code>'&#92;u202F'</code>).
     * <li> It is <code>'&#92;u0009'</code>, HORIZONTAL TABULATION.
     * <li> It is <code>'&#92;u000A'</code>, LINE FEED.
     * <li> It is <code>'&#92;u000B'</code>, VERTICAL TABULATION.
     * <li> It is <code>'&#92;u000C'</code>, FORM FEED.
     * <li> It is <code>'&#92;u000D'</code>, CARRIAGE RETURN.
     * <li> It is <code>'&#92;u001C'</code>, FILE SEPARATOR.
     * <li> It is <code>'&#92;u001D'</code>, GROUP SEPARATOR.
     * <li> It is <code>'&#92;u001E'</code>, RECORD SEPARATOR.
     * <li> It is <code>'&#92;u001F'</code>, UNIT SEPARATOR.
     * </ul>
     * <p>
     *
     * @param   codePoint the character (Unicode code point) to be tested.
     * @return  <code>true</code> if the character is a Java whitespace
     *          character; <code>false</code> otherwise.
     * @see     java.lang.Character#isSpaceChar(int)
     * @since   1.5
     */
    public static boolean isWhitespace(int codePoint) {
        return CharacterData.of(codePoint).isWhitespace(codePoint);
    }

    /**
     * Determines if the specified character is an ISO control
     * character.  A character is considered to be an ISO control
     * character if its code is in the range <code>'&#92;u0000'</code>
     * through <code>'&#92;u001F'</code> or in the range
     * <code>'&#92;u007F'</code> through <code>'&#92;u009F'</code>.
     *
     * <p><b>Note:</b> This method cannot handle <a
     * href="#supplementary"> supplementary characters</a>. To support
     * all Unicode characters, including supplementary characters, use
     * the {@link #isISOControl(int)} method.
     *
     * @param   ch      the character to be tested.
     * @return  <code>true</code> if the character is an ISO control character;
     *          <code>false</code> otherwise.
     *
     * @see     java.lang.Character#isSpaceChar(char)
     * @see     java.lang.Character#isWhitespace(char)
     * @since   1.1
     */
    public static boolean isISOControl(char ch) {
        return isISOControl((int)ch);
    }

    /**
     * Determines if the referenced character (Unicode code point) is an ISO control
     * character.  A character is considered to be an ISO control
     * character if its code is in the range <code>'&#92;u0000'</code>
     * through <code>'&#92;u001F'</code> or in the range
     * <code>'&#92;u007F'</code> through <code>'&#92;u009F'</code>.
     *
     * @param   codePoint the character (Unicode code point) to be tested.
     * @return  <code>true</code> if the character is an ISO control character;
     *          <code>false</code> otherwise.
     * @see     java.lang.Character#isSpaceChar(int)
     * @see     java.lang.Character#isWhitespace(int)
     * @since   1.5
     */
    public static boolean isISOControl(int codePoint) {
        return (codePoint >= 0x0000 && codePoint <= 0x001F) ||
            (codePoint >= 0x007F && codePoint <= 0x009F);
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
     * @return  a value of type <code>int</code> representing the
     *          character's general category.
     * @see     java.lang.Character#COMBINING_SPACING_MARK
     * @see     java.lang.Character#CONNECTOR_PUNCTUATION
     * @see     java.lang.Character#CONTROL
     * @see     java.lang.Character#CURRENCY_SYMBOL
     * @see     java.lang.Character#DASH_PUNCTUATION
     * @see     java.lang.Character#DECIMAL_DIGIT_NUMBER
     * @see     java.lang.Character#ENCLOSING_MARK
     * @see     java.lang.Character#END_PUNCTUATION
     * @see     java.lang.Character#FINAL_QUOTE_PUNCTUATION
     * @see     java.lang.Character#FORMAT
     * @see     java.lang.Character#INITIAL_QUOTE_PUNCTUATION
     * @see     java.lang.Character#LETTER_NUMBER
     * @see     java.lang.Character#LINE_SEPARATOR
     * @see     java.lang.Character#LOWERCASE_LETTER
     * @see     java.lang.Character#MATH_SYMBOL
     * @see     java.lang.Character#MODIFIER_LETTER
     * @see     java.lang.Character#MODIFIER_SYMBOL
     * @see     java.lang.Character#NON_SPACING_MARK
     * @see     java.lang.Character#OTHER_LETTER
     * @see     java.lang.Character#OTHER_NUMBER
     * @see     java.lang.Character#OTHER_PUNCTUATION
     * @see     java.lang.Character#OTHER_SYMBOL
     * @see     java.lang.Character#PARAGRAPH_SEPARATOR
     * @see     java.lang.Character#PRIVATE_USE
     * @see     java.lang.Character#SPACE_SEPARATOR
     * @see     java.lang.Character#START_PUNCTUATION
     * @see     java.lang.Character#SURROGATE
     * @see     java.lang.Character#TITLECASE_LETTER
     * @see     java.lang.Character#UNASSIGNED
     * @see     java.lang.Character#UPPERCASE_LETTER
     * @since   1.1
     */
    public static int getType(char ch) {
        return getType((int)ch);
    }

    /**
     * Returns a value indicating a character's general category.
     *
     * @param   codePoint the character (Unicode code point) to be tested.
     * @return  a value of type <code>int</code> representing the
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
     * the specified radix. If the value of <code>radix</code> is not a
     * valid radix, or the value of <code>digit</code> is not a valid
     * digit in the specified radix, the null character
     * (<code>'&#92;u0000'</code>) is returned.
     * <p>
     * The <code>radix</code> argument is valid if it is greater than or
     * equal to <code>MIN_RADIX</code> and less than or equal to
     * <code>MAX_RADIX</code>. The <code>digit</code> argument is valid if
     * <code>0&nbsp;&lt;=digit&nbsp;&lt;&nbsp;radix</code>.
     * <p>
     * If the digit is less than 10, then
     * <code>'0'&nbsp;+ digit</code> is returned. Otherwise, the value
     * <code>'a'&nbsp;+ digit&nbsp;-&nbsp;10</code> is returned.
     *
     * @param   digit   the number to convert to a character.
     * @param   radix   the radix.
     * @return  the <code>char</code> representation of the specified digit
     *          in the specified radix.
     * @see     java.lang.Character#MIN_RADIX
     * @see     java.lang.Character#MAX_RADIX
     * @see     java.lang.Character#digit(char, int)
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
     * <code>char</code> values is <code>DIRECTIONALITY_UNDEFINED</code>.
     *
     * <p><b>Note:</b> This method cannot handle <a
     * href="#supplementary"> supplementary characters</a>. To support
     * all Unicode characters, including supplementary characters, use
     * the {@link #getDirectionality(int)} method.
     *
     * @param  ch <code>char</code> for which the directionality property
     *            is requested.
     * @return the directionality property of the <code>char</code> value.
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
     * right-to-left.  For example, <code>'&#92;u0028'</code> LEFT
     * PARENTHESIS is semantically defined to be an <i>opening
     * parenthesis</i>.  This will appear as a "(" in text that is
     * left-to-right but as a ")" in text that is right-to-left.
     *
     * <p><b>Note:</b> This method cannot handle <a
     * href="#supplementary"> supplementary characters</a>. To support
     * all Unicode characters, including supplementary characters, use
     * the {@link #isMirrored(int)} method.
     *
     * @param  ch <code>char</code> for which the mirrored property is requested
     * @return <code>true</code> if the char is mirrored, <code>false</code>
     *         if the <code>char</code> is not mirrored or is not defined.
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
     * <code>'&#92;u0028'</code> LEFT PARENTHESIS is semantically
     * defined to be an <i>opening parenthesis</i>.  This will appear
     * as a "(" in text that is left-to-right but as a ")" in text
     * that is right-to-left.
     *
     * @param   codePoint the character (Unicode code point) to be tested.
     * @return  <code>true</code> if the character is mirrored, <code>false</code>
     *          if the character is not mirrored or is not defined.
     * @since   1.5
     */
    public static boolean isMirrored(int codePoint) {
        return CharacterData.of(codePoint).isMirrored(codePoint);
    }

    /**
     * Compares two <code>Character</code> objects numerically.
     *
     * @param   anotherCharacter   the <code>Character</code> to be compared.

     * @return  the value <code>0</code> if the argument <code>Character</code>
     *          is equal to this <code>Character</code>; a value less than
     *          <code>0</code> if this <code>Character</code> is numerically less
     *          than the <code>Character</code> argument; and a value greater than
     *          <code>0</code> if this <code>Character</code> is numerically greater
     *          than the <code>Character</code> argument (unsigned comparison).
     *          Note that this is strictly a numerical comparison; it is not
     *          locale-dependent.
     * @since   1.2
     */
    public int compareTo(Character anotherCharacter) {
        return this.value - anotherCharacter.value;
    }

    /**
     * Converts the character (Unicode code point) argument to uppercase using
     * information from the UnicodeData file.
     * <p>
     *
     * @param   codePoint   the character (Unicode code point) to be converted.
     * @return  either the uppercase equivalent of the character, if
     *          any, or an error flag (<code>Character.ERROR</code>)
     *          that indicates that a 1:M <code>char</code> mapping exists.
     * @see     java.lang.Character#isLowerCase(char)
     * @see     java.lang.Character#isUpperCase(char)
     * @see     java.lang.Character#toLowerCase(char)
     * @see     java.lang.Character#toTitleCase(char)
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
     * mapping, then the <code>char</code> itself is returned in the
     * <code>char[]</code>.
     *
     * @param   codePoint   the character (Unicode code point) to be converted.
     * @return a <code>char[]</code> with the uppercased character.
     * @since 1.4
     */
    static char[] toUpperCaseCharArray(int codePoint) {
        // As of Unicode 4.0, 1:M uppercasings only happen in the BMP.
        assert isValidCodePoint(codePoint) &&
               !isSupplementaryCodePoint(codePoint);
        return CharacterData.of(codePoint).toUpperCaseCharArray(codePoint);
    }

    /**
     * The number of bits used to represent a <tt>char</tt> value in unsigned
     * binary form.
     *
     * @since 1.5
     */
    public static final int SIZE = 16;

    /**
     * Returns the value obtained by reversing the order of the bytes in the
     * specified <tt>char</tt> value.
     *
     * @return the value obtained by reversing (or, equivalently, swapping)
     *     the bytes in the specified <tt>char</tt> value.
     * @since 1.5
     */
    public static char reverseBytes(char ch) {
        return (char) (((ch & 0xFF00) >> 8) | (ch << 8));
    }
}
