// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 1996-2016, International Business Machines Corporation and
 * others. All Rights Reserved.
 *******************************************************************************
 */
package jdk.internal.icu.text;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.SortedSet;
import java.util.TreeSet;

import jdk.internal.icu.impl.BMPSet;
import jdk.internal.icu.impl.CharacterPropertiesImpl;
import jdk.internal.icu.impl.PatternProps;
import jdk.internal.icu.impl.RuleCharacterIterator;
import jdk.internal.icu.impl.SortedSetRelation;
import jdk.internal.icu.impl.StringRange;
import jdk.internal.icu.impl.UCaseProps;
import jdk.internal.icu.impl.UPropertyAliases;
import jdk.internal.icu.impl.UnicodeSetStringSpan;
import jdk.internal.icu.impl.Utility;
import jdk.internal.icu.lang.CharSequences;
import jdk.internal.icu.lang.CharacterProperties;
import jdk.internal.icu.lang.UCharacter;
import jdk.internal.icu.lang.UProperty;
import jdk.internal.icu.lang.UScript;
import jdk.internal.icu.util.Freezable;
import jdk.internal.icu.util.OutputInt;
import jdk.internal.icu.util.ULocale;
import jdk.internal.icu.util.VersionInfo;

/**
 * A mutable set of Unicode characters and multicharacter strings.
 * Objects of this class represent <em>character classes</em> used
 * in regular expressions. A character specifies a subset of Unicode
 * code points.  Legal code points are U+0000 to U+10FFFF, inclusive.
 *
 * Note: method freeze() will not only make the set immutable, but
 * also makes important methods much higher performance:
 * contains(c), containsNone(...), span(...), spanBack(...) etc.
 * After the object is frozen, any subsequent call that wants to change
 * the object will throw UnsupportedOperationException.
 *
 * <p>The UnicodeSet class is not designed to be subclassed.
 *
 * <p><code>UnicodeSet</code> supports two APIs. The first is the
 * <em>operand</em> API that allows the caller to modify the value of
 * a <code>UnicodeSet</code> object. It conforms to Java 2's
 * <code>java.util.Set</code> interface, although
 * <code>UnicodeSet</code> does not actually implement that
 * interface. All methods of <code>Set</code> are supported, with the
 * modification that they take a character range or single character
 * instead of an <code>Object</code>, and they take a
 * <code>UnicodeSet</code> instead of a <code>Collection</code>.  The
 * operand API may be thought of in terms of boolean logic: a boolean
 * OR is implemented by <code>add</code>, a boolean AND is implemented
 * by <code>retain</code>, a boolean XOR is implemented by
 * <code>complement</code> taking an argument, and a boolean NOT is
 * implemented by <code>complement</code> with no argument.  In terms
 * of traditional set theory function names, <code>add</code> is a
 * union, <code>retain</code> is an intersection, <code>remove</code>
 * is an asymmetric difference, and <code>complement</code> with no
 * argument is a set complement with respect to the superset range
 * <code>MIN_VALUE-MAX_VALUE</code>
 *
 * <p>The second API is the
 * <code>applyPattern()</code>/<code>toPattern()</code> API from the
 * <code>java.text.Format</code>-derived classes.  Unlike the
 * methods that add characters, add categories, and control the logic
 * of the set, the method <code>applyPattern()</code> sets all
 * attributes of a <code>UnicodeSet</code> at once, based on a
 * string pattern.
 *
 * <p><b>Pattern syntax</b></p>
 *
 * Patterns are accepted by the constructors and the
 * <code>applyPattern()</code> methods and returned by the
 * <code>toPattern()</code> method.  These patterns follow a syntax
 * similar to that employed by version 8 regular expression character
 * classes.  Here are some simple examples:
 *
 * <blockquote>
 *   <table>
 *     <tr style="vertical-align: top">
 *       <td style="white-space: nowrap; vertical-align: top; horizontal-align: left;"><code>[]</code></td>
 *       <td style="vertical-align: top;">No characters</td>
 *     </tr><tr style="vertical-align: top">
 *       <td style="white-space: nowrap; vertical-align: top; horizontal-align: left;"><code>[a]</code></td>
 *       <td style="vertical-align: top;">The character 'a'</td>
 *     </tr><tr style="vertical-align: top">
 *       <td style="white-space: nowrap; vertical-align: top; horizontal-align: left;"><code>[ae]</code></td>
 *       <td style="vertical-align: top;">The characters 'a' and 'e'</td>
 *     </tr>
 *     <tr>
 *       <td style="white-space: nowrap; vertical-align: top; horizontal-align: left;"><code>[a-e]</code></td>
 *       <td style="vertical-align: top;">The characters 'a' through 'e' inclusive, in Unicode code
 *       point order</td>
 *     </tr>
 *     <tr>
 *       <td style="white-space: nowrap; vertical-align: top; horizontal-align: left;"><code>[\\u4E01]</code></td>
 *       <td style="vertical-align: top;">The character U+4E01</td>
 *     </tr>
 *     <tr>
 *       <td style="white-space: nowrap; vertical-align: top; horizontal-align: left;"><code>[a{ab}{ac}]</code></td>
 *       <td style="vertical-align: top;">The character 'a' and the multicharacter strings &quot;ab&quot; and
 *       &quot;ac&quot;</td>
 *     </tr>
 *     <tr>
 *       <td style="white-space: nowrap; vertical-align: top; horizontal-align: left;"><code>[\p{Lu}]</code></td>
 *       <td style="vertical-align: top;">All characters in the general category Uppercase Letter</td>
 *     </tr>
 *   </table>
 * </blockquote>
 *
 * Any character may be preceded by a backslash in order to remove any special
 * meaning.  White space characters, as defined by the Unicode Pattern_White_Space property, are
 * ignored, unless they are escaped.
 *
 * <p>Property patterns specify a set of characters having a certain
 * property as defined by the Unicode standard.  Both the POSIX-like
 * "[:Lu:]" and the Perl-like syntax "\p{Lu}" are recognized.  For a
 * complete list of supported property patterns, see the User's Guide
 * for UnicodeSet at
 * <a href="https://unicode-org.github.io/icu/userguide/strings/unicodeset">
 * https://unicode-org.github.io/icu/userguide/strings/unicodeset</a>.
 * Actual determination of property data is defined by the underlying
 * Unicode database as implemented by UCharacter.
 *
 * <p>Patterns specify individual characters, ranges of characters, and
 * Unicode property sets.  When elements are concatenated, they
 * specify their union.  To complement a set, place a '^' immediately
 * after the opening '['.  Property patterns are inverted by modifying
 * their delimiters; "[:^foo]" and "\P{foo}".  In any other location,
 * '^' has no special meaning.
 *
 * <p>Since ICU 70, "[^...]", "[:^foo]", "\P{foo}", and "[:binaryProperty=No:]"
 * perform a "code point complement" (all code points minus the original set),
 * removing all multicharacter strings,
 * equivalent to .{@link #complement()}.{@link #removeAllStrings()} .
 * The {@link #complement()} API function continues to perform a
 * symmetric difference with all code points and thus retains all multicharacter strings.
 *
 * <p>Ranges are indicated by placing two a '-' between two
 * characters, as in "a-z".  This specifies the range of all
 * characters from the left to the right, in Unicode order.  If the
 * left character is greater than or equal to the
 * right character it is a syntax error.  If a '-' occurs as the first
 * character after the opening '[' or '[^', or if it occurs as the
 * last character before the closing ']', then it is taken as a
 * literal.  Thus "[a\\-b]", "[-ab]", and "[ab-]" all indicate the same
 * set of three characters, 'a', 'b', and '-'.
 *
 * <p>Sets may be intersected using the '&amp;' operator or the asymmetric
 * set difference may be taken using the '-' operator, for example,
 * "[[:L:]&amp;[\\u0000-\\u0FFF]]" indicates the set of all Unicode letters
 * with values less than 4096.  Operators ('&amp;' and '|') have equal
 * precedence and bind left-to-right.  Thus
 * "[[:L:]-[a-z]-[\\u0100-\\u01FF]]" is equivalent to
 * "[[[:L:]-[a-z]]-[\\u0100-\\u01FF]]".  This only really matters for
 * difference; intersection is commutative.
 *
 * <table>
 * <tr style="vertical-align: top;"><td style="white-space: nowrap;"><code>[a]</code><td>The set containing 'a'
 * <tr style="vertical-align: top;"><td style="white-space: nowrap;"><code>[a-z]</code><td>The set containing 'a'
 * through 'z' and all letters in between, in Unicode order
 * <tr style="vertical-align: top;"><td style="white-space: nowrap;"><code>[^a-z]</code><td>The set containing
 * all characters but 'a' through 'z',
 * that is, U+0000 through 'a'-1 and 'z'+1 through U+10FFFF
 * <tr style="vertical-align: top;"><td style="white-space: nowrap;"><code>[[<em>pat1</em>][<em>pat2</em>]]</code>
 * <td>The union of sets specified by <em>pat1</em> and <em>pat2</em>
 * <tr style="vertical-align: top;"><td style="white-space: nowrap;"><code>[[<em>pat1</em>]&amp;[<em>pat2</em>]]</code>
 * <td>The intersection of sets specified by <em>pat1</em> and <em>pat2</em>
 * <tr style="vertical-align: top;"><td style="white-space: nowrap;"><code>[[<em>pat1</em>]-[<em>pat2</em>]]</code>
 * <td>The asymmetric difference of sets specified by <em>pat1</em> and
 * <em>pat2</em>
 * <tr style="vertical-align: top;"><td style="white-space: nowrap;"><code>[:Lu:] or \p{Lu}</code>
 * <td>The set of characters having the specified
 * Unicode property; in
 * this case, Unicode uppercase letters
 * <tr style="vertical-align: top;"><td style="white-space: nowrap;"><code>[:^Lu:] or \P{Lu}</code>
 * <td>The set of characters <em>not</em> having the given
 * Unicode property
 * </table>
 *
 * <p><b>Formal syntax</b></p>
 *
 * <blockquote>
 *   <table>
 *     <tr style="vertical-align: top">
 *       <td style="white-space: nowrap; vertical-align: top;" align="right"><code>pattern :=&nbsp; </code></td>
 *       <td style="vertical-align: top;"><code>('[' '^'? item* ']') |
 *       property</code></td>
 *     </tr>
 *     <tr style="vertical-align: top">
 *       <td style="white-space: nowrap; vertical-align: top;" align="right"><code>item :=&nbsp; </code></td>
 *       <td style="vertical-align: top;"><code>char | (char '-' char) | pattern-expr<br>
 *       </code></td>
 *     </tr>
 *     <tr style="vertical-align: top">
 *       <td style="white-space: nowrap; vertical-align: top;" align="right"><code>pattern-expr :=&nbsp; </code></td>
 *       <td style="vertical-align: top;"><code>pattern | pattern-expr pattern |
 *       pattern-expr op pattern<br>
 *       </code></td>
 *     </tr>
 *     <tr style="vertical-align: top">
 *       <td style="white-space: nowrap; vertical-align: top;" align="right"><code>op :=&nbsp; </code></td>
 *       <td style="vertical-align: top;"><code>'&amp;' | '-'<br>
 *       </code></td>
 *     </tr>
 *     <tr style="vertical-align: top">
 *       <td style="white-space: nowrap; vertical-align: top;" align="right"><code>special :=&nbsp; </code></td>
 *       <td style="vertical-align: top;"><code>'[' | ']' | '-'<br>
 *       </code></td>
 *     </tr>
 *     <tr style="vertical-align: top">
 *       <td style="white-space: nowrap; vertical-align: top;" align="right"><code>char :=&nbsp; </code></td>
 *       <td style="vertical-align: top;"><em>any character that is not</em><code> special<br>
 *       | ('\\' </code><em>any character</em><code>)<br>
 *       | ('&#92;u' hex hex hex hex)<br>
 *       </code></td>
 *     </tr>
 *     <tr style="vertical-align: top">
 *       <td style="white-space: nowrap; vertical-align: top;" align="right"><code>hex :=&nbsp; </code></td>
 *       <td style="vertical-align: top;"><code>'0' | '1' | '2' | '3' | '4' | '5' | '6' | '7' | '8' | '9' |<br>
 *       &nbsp;&nbsp;&nbsp;&nbsp;'A' | 'B' | 'C' | 'D' | 'E' | 'F' | 'a' | 'b' | 'c' | 'd' | 'e' | 'f'</code></td>
 *     </tr>
 *     <tr>
 *       <td style="white-space: nowrap; vertical-align: top;" align="right"><code>property :=&nbsp; </code></td>
 *       <td style="vertical-align: top;"><em>a Unicode property set pattern</em></td>
 *     </tr>
 *   </table>
 *   <br>
 *   <table border="1">
 *     <tr>
 *       <td>Legend: <table>
 *         <tr>
 *           <td style="white-space: nowrap; vertical-align: top;"><code>a := b</code></td>
 *           <td style="width: 20; vertical-align: top;">&nbsp; </td>
 *           <td style="vertical-align: top;"><code>a</code> may be replaced by <code>b</code> </td>
 *         </tr>
 *         <tr>
 *           <td style="white-space: nowrap; vertical-align: top;"><code>a?</code></td>
 *           <td style="vertical-align: top;"></td>
 *           <td style="vertical-align: top;">zero or one instance of <code>a</code><br>
 *           </td>
 *         </tr>
 *         <tr>
 *           <td style="white-space: nowrap; vertical-align: top;"><code>a*</code></td>
 *           <td style="vertical-align: top;"></td>
 *           <td style="vertical-align: top;">one or more instances of <code>a</code><br>
 *           </td>
 *         </tr>
 *         <tr>
 *           <td style="white-space: nowrap; vertical-align: top;"><code>a | b</code></td>
 *           <td style="vertical-align: top;"></td>
 *           <td style="vertical-align: top;">either <code>a</code> or <code>b</code><br>
 *           </td>
 *         </tr>
 *         <tr>
 *           <td style="white-space: nowrap; vertical-align: top;"><code>'a'</code></td>
 *           <td style="vertical-align: top;"></td>
 *           <td style="vertical-align: top;">the literal string between the quotes </td>
 *         </tr>
 *       </table>
 *       </td>
 *     </tr>
 *   </table>
 * </blockquote>
 * <p>To iterate over contents of UnicodeSet, the following are available:
 * <ul><li>{@link #ranges()} to iterate through the ranges</li>
 * <li>{@link #strings()} to iterate through the strings</li>
 * <li>{@link #iterator()} to iterate through the entire contents in a single loop.
 * That method is, however, not particularly efficient, since it "boxes" each code point into a String.
 * </ul>
 * All of the above can be used in <b>for</b> loops.
 * The {@link jdk.internal.icu.text.UnicodeSetIterator UnicodeSetIterator} can also be used, but not in <b>for</b> loops.
 * <p>To replace, count elements, or delete spans, see {@link jdk.internal.icu.text.UnicodeSetSpanner UnicodeSetSpanner}.
 *
 * @author Alan Liu
 * @stable ICU 2.0
 * @see UnicodeSetIterator
 * @see UnicodeSetSpanner
 */
@SuppressWarnings("deprecation")
public class UnicodeSet extends UnicodeFilter implements Iterable<String>, Comparable<UnicodeSet>, Freezable<UnicodeSet> {
    private static final SortedSet<String> EMPTY_STRINGS =
            Collections.unmodifiableSortedSet(new TreeSet<String>());

    /**
     * Constant for the empty set.
     * @stable ICU 4.8
     */
    public static final UnicodeSet EMPTY = new UnicodeSet().freeze();
    /**
     * Constant for the set of all code points. (Since UnicodeSets can include strings, does not include everything that a UnicodeSet can.)
     * @stable ICU 4.8
     */
    public static final UnicodeSet ALL_CODE_POINTS = new UnicodeSet(0, 0x10FFFF).freeze();

    private static XSymbolTable XSYMBOL_TABLE = null; // for overriding the the function processing

    private static final int LOW = 0x000000; // LOW <= all valid values. ZERO for codepoints
    private static final int HIGH = 0x110000; // HIGH > all valid values. 10000 for code units.
    // 110000 for codepoints

    /**
     * Enough for sets with few ranges.
     * For example, White_Space has 10 ranges, list length 21.
     */
    private static final int INITIAL_CAPACITY = 25;

    /** Max list [0, 1, 2, ..., max code point, HIGH] */
    private static final int MAX_LENGTH = HIGH + 1;

    /**
     * Minimum value that can be stored in a UnicodeSet.
     * @stable ICU 2.0
     */
    public static final int MIN_VALUE = LOW;

    /**
     * Maximum value that can be stored in a UnicodeSet.
     * @stable ICU 2.0
     */
    public static final int MAX_VALUE = HIGH - 1;

    private int len;      // length used; list may be longer to minimize reallocs
    private int[] list;   // MUST be terminated with HIGH
    private int[] rangeList; // internal buffer
    private int[] buffer; // internal buffer

    // is not private so that UnicodeSetIterator can get access
    SortedSet<String> strings = EMPTY_STRINGS;

    /**
     * The pattern representation of this set.  This may not be the
     * most economical pattern.  It is the pattern supplied to
     * applyPattern(), with variables substituted and whitespace
     * removed.  For sets constructed without applyPattern(), or
     * modified using the non-pattern API, this string will be null,
     * indicating that toPattern() must generate a pattern
     * representation from the inversion list.
     */
    private String pat = null;

    // Special property set IDs
    private static final String ANY_ID   = "ANY";   // [\u0000-\U0010FFFF]
    private static final String ASCII_ID = "ASCII"; // [\u0000-\u007F]
    private static final String ASSIGNED = "Assigned"; // [:^Cn:]

    private volatile BMPSet bmpSet; // The set is frozen if bmpSet or stringSpan is not null.
    private volatile UnicodeSetStringSpan stringSpan;
    //----------------------------------------------------------------
    // Public API
    //----------------------------------------------------------------

    /**
     * Constructs an empty set.
     * @stable ICU 2.0
     */
    public UnicodeSet() {
        list = new int[INITIAL_CAPACITY];
        list[0] = HIGH;
        len = 1;
    }

    /**
     * Constructs a copy of an existing set.
     * @stable ICU 2.0
     */
    public UnicodeSet(UnicodeSet other) {
        set(other);
    }

    /**
     * Constructs a set containing the given range. If <code>end &gt;
     * start</code> then an empty set is created.
     *
     * @param start first character, inclusive, of range
     * @param end last character, inclusive, of range
     * @stable ICU 2.0
     */
    public UnicodeSet(int start, int end) {
        this();
        add(start, end);
    }

    /**
     * Quickly constructs a set from a set of ranges &lt;s0, e0, s1, e1, s2, e2, ..., sn, en&gt;.
     * There must be an even number of integers, and they must be all greater than zero,
     * all less than or equal to Character.MAX_CODE_POINT.
     * In each pair (..., si, ei, ...) it must be true that si &lt;= ei
     * Between adjacent pairs (...ei, sj...), it must be true that ei+1 &lt; sj
     * @param pairs pairs of character representing ranges
     * @stable ICU 4.4
     */
    public UnicodeSet(int... pairs) {
        if ((pairs.length & 1) != 0) {
            throw new IllegalArgumentException("Must have even number of integers");
        }
        list = new int[pairs.length + 1]; // don't allocate extra space, because it is likely that this is a fixed set.
        len = list.length;
        int last = -1; // used to ensure that the results are monotonically increasing.
        int i = 0;
        while (i < pairs.length) {
            int start = pairs[i];
            if (last >= start) {
                throw new IllegalArgumentException("Must be monotonically increasing.");
            }
            list[i++] = start;
            int limit = pairs[i] + 1;
            if (start >= limit) {
                throw new IllegalArgumentException("Must be monotonically increasing.");
            }
            list[i++] = last = limit;
        }
        list[i] = HIGH; // terminate
    }

    /**
     * Constructs a set from the given pattern.  See the class description
     * for the syntax of the pattern language.  Whitespace is ignored.
     * @param pattern a string specifying what characters are in the set
     * @exception java.lang.IllegalArgumentException if the pattern contains
     * a syntax error.
     * @stable ICU 2.0
     */
    public UnicodeSet(String pattern) {
        this();
        applyPattern(pattern, null, null, IGNORE_SPACE);
    }

    /**
     * Constructs a set from the given pattern.  See the class description
     * for the syntax of the pattern language.
     * @param pattern a string specifying what characters are in the set
     * @param ignoreWhitespace if true, ignore Unicode Pattern_White_Space characters
     * @exception java.lang.IllegalArgumentException if the pattern contains
     * a syntax error.
     * @stable ICU 2.0
     */
    public UnicodeSet(String pattern, boolean ignoreWhitespace) {
        this();
        applyPattern(pattern, null, null, ignoreWhitespace ? IGNORE_SPACE : 0);
    }

    /**
     * Constructs a set from the given pattern.  See the class description
     * for the syntax of the pattern language.
     * @param pattern a string specifying what characters are in the set
     * @param options a bitmask indicating which options to apply.
     * Valid options are {@link #IGNORE_SPACE} and
     * at most one of {@link #CASE_INSENSITIVE}, {@link #ADD_CASE_MAPPINGS},
     * {@link #SIMPLE_CASE_INSENSITIVE}. These case options are mutually exclusive.
     * @exception java.lang.IllegalArgumentException if the pattern contains
     * a syntax error.
     * @stable ICU 3.8
     */
    public UnicodeSet(String pattern, int options) {
        this();
        applyPattern(pattern, null, null, options);
    }

    /**
     * Constructs a set from the given pattern.  See the class description
     * for the syntax of the pattern language.
     * @param pattern a string specifying what characters are in the set
     * @param pos on input, the position in pattern at which to start parsing.
     * On output, the position after the last character parsed.
     * @param symbols a symbol table mapping variables to char[] arrays
     * and chars to UnicodeSets
     * @exception java.lang.IllegalArgumentException if the pattern
     * contains a syntax error.
     * @stable ICU 2.0
     */
    public UnicodeSet(String pattern, ParsePosition pos, SymbolTable symbols) {
        this();
        applyPattern(pattern, pos, symbols, IGNORE_SPACE);
    }

    /**
     * Constructs a set from the given pattern.  See the class description
     * for the syntax of the pattern language.
     * @param pattern a string specifying what characters are in the set
     * @param pos on input, the position in pattern at which to start parsing.
     * On output, the position after the last character parsed.
     * @param symbols a symbol table mapping variables to char[] arrays
     * and chars to UnicodeSets
     * @param options a bitmask indicating which options to apply.
     * Valid options are {@link #IGNORE_SPACE} and
     * at most one of {@link #CASE_INSENSITIVE}, {@link #ADD_CASE_MAPPINGS},
     * {@link #SIMPLE_CASE_INSENSITIVE}. These case options are mutually exclusive.
     * @exception java.lang.IllegalArgumentException if the pattern
     * contains a syntax error.
     * @stable ICU 3.2
     */
    public UnicodeSet(String pattern, ParsePosition pos, SymbolTable symbols, int options) {
        this();
        applyPattern(pattern, pos, symbols, options);
    }


    /**
     * Return a new set that is equivalent to this one.
     * @stable ICU 2.0
     */
    @Override
    public Object clone() {
        if (isFrozen()) {
            return this;
        }
        return new UnicodeSet(this);
    }

    /**
     * Make this object represent the range <code>start - end</code>.
     * If <code>start &gt; end</code> then this object is set to an empty range.
     *
     * @param start first character in the set, inclusive
     * @param end last character in the set, inclusive
     * @stable ICU 2.0
     */
    public UnicodeSet set(int start, int end) {
        checkFrozen();
        clear();
        complement(start, end);
        return this;
    }

    /**
     * Make this object represent the same set as <code>other</code>.
     * @param other a <code>UnicodeSet</code> whose value will be
     * copied to this object
     * @stable ICU 2.0
     */
    public UnicodeSet set(UnicodeSet other) {
        checkFrozen();
        list = Arrays.copyOf(other.list, other.len);
        len = other.len;
        pat = other.pat;
        if (other.hasStrings()) {
            strings = new TreeSet<>(other.strings);
        } else {
            strings = EMPTY_STRINGS;
        }
        return this;
    }

    /**
     * Modifies this set to represent the set specified by the given pattern.
     * See the class description for the syntax of the pattern language.
     * Whitespace is ignored.
     * @param pattern a string specifying what characters are in the set
     * @exception java.lang.IllegalArgumentException if the pattern
     * contains a syntax error.
     * @stable ICU 2.0
     */
    public final UnicodeSet applyPattern(String pattern) {
        checkFrozen();
        return applyPattern(pattern, null, null, IGNORE_SPACE);
    }

    /**
     * Modifies this set to represent the set specified by the given pattern,
     * optionally ignoring whitespace.
     * See the class description for the syntax of the pattern language.
     * @param pattern a string specifying what characters are in the set
     * @param ignoreWhitespace if true then Unicode Pattern_White_Space characters are ignored
     * @exception java.lang.IllegalArgumentException if the pattern
     * contains a syntax error.
     * @stable ICU 2.0
     */
    public UnicodeSet applyPattern(String pattern, boolean ignoreWhitespace) {
        checkFrozen();
        return applyPattern(pattern, null, null, ignoreWhitespace ? IGNORE_SPACE : 0);
    }

    /**
     * Modifies this set to represent the set specified by the given pattern,
     * optionally ignoring whitespace.
     * See the class description for the syntax of the pattern language.
     * @param pattern a string specifying what characters are in the set
     * @param options a bitmask indicating which options to apply.
     * Valid options are {@link #IGNORE_SPACE} and
     * at most one of {@link #CASE_INSENSITIVE}, {@link #ADD_CASE_MAPPINGS},
     * {@link #SIMPLE_CASE_INSENSITIVE}. These case options are mutually exclusive.
     * @exception java.lang.IllegalArgumentException if the pattern
     * contains a syntax error.
     * @stable ICU 3.8
     */
    public UnicodeSet applyPattern(String pattern, int options) {
        checkFrozen();
        return applyPattern(pattern, null, null, options);
    }

    /**
     * Return true if the given position, in the given pattern, appears
     * to be the start of a UnicodeSet pattern.
     * @stable ICU 2.0
     */
    public static boolean resemblesPattern(String pattern, int pos) {
        return ((pos+1) < pattern.length() &&
                pattern.charAt(pos) == '[') ||
                resemblesPropertyPattern(pattern, pos);
    }

    /**
     * TODO: create Appendable version of UTF16.append(buf, c),
     * maybe in new class Appendables?
     * @throws IOException
     */
    private static void appendCodePoint(Appendable app, int c) {
        assert 0 <= c && c <= 0x10ffff;
        try {
            if (c <= 0xffff) {
                app.append((char) c);
            } else {
                app.append(UTF16.getLeadSurrogate(c)).append(UTF16.getTrailSurrogate(c));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * TODO: create class Appendables?
     * @throws IOException
     */
    private static void append(Appendable app, CharSequence s) {
        try {
            app.append(s);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Append the <code>toPattern()</code> representation of a
     * string to the given <code>Appendable</code>.
     */
    private static <T extends Appendable> T _appendToPat(T buf, String s, boolean escapeUnprintable) {
        int cp;
        for (int i = 0; i < s.length(); i += Character.charCount(cp)) {
            cp = s.codePointAt(i);
            _appendToPat(buf, cp, escapeUnprintable);
        }
        return buf;
    }

    /**
     * Append the <code>toPattern()</code> representation of a
     * character to the given <code>Appendable</code>.
     */
    private static <T extends Appendable> T _appendToPat(T buf, int c, boolean escapeUnprintable) {
        try {
            if (escapeUnprintable ? Utility.isUnprintable(c) : Utility.shouldAlwaysBeEscaped(c)) {
                // Use hex escape notation (<backslash>uxxxx or <backslash>Uxxxxxxxx) for anything
                // unprintable
                return Utility.escape(buf, c);
            }
            // Okay to let ':' pass through
            switch (c) {
            case '[': // SET_OPEN:
            case ']': // SET_CLOSE:
            case '-': // HYPHEN:
            case '^': // COMPLEMENT:
            case '&': // INTERSECTION:
            case '\\': //BACKSLASH:
            case '{':
            case '}':
            case '$':
            case ':':
                buf.append('\\');
                break;
            default:
                // Escape whitespace
                if (PatternProps.isWhiteSpace(c)) {
                    buf.append('\\');
                }
                break;
            }
            appendCodePoint(buf, c);
            return buf;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static <T extends Appendable> T _appendToPat(
            T result, int start, int end, boolean escapeUnprintable) {
        _appendToPat(result, start, escapeUnprintable);
        if (start != end) {
            if ((start+1) != end ||
                    // Avoid writing what looks like a lead+trail surrogate pair.
                    start == 0xdbff) {
                try {
                    result.append('-');
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
            _appendToPat(result, end, escapeUnprintable);
        }
        return result;
    }

    /**
     * Returns a string representation of this set.  If the result of
     * calling this function is passed to a UnicodeSet constructor, it
     * will produce another set that is equal to this one.
     * @stable ICU 2.0
     */
    @Override
    public String toPattern(boolean escapeUnprintable) {
        if (pat != null && !escapeUnprintable) {
            return pat;
        }
        StringBuilder result = new StringBuilder();
        return _toPattern(result, escapeUnprintable).toString();
    }

    /**
     * Append a string representation of this set to result.  This will be
     * a cleaned version of the string passed to applyPattern(), if there
     * is one.  Otherwise it will be generated.
     */
    private <T extends Appendable> T _toPattern(T result,
            boolean escapeUnprintable) {
        if (pat == null) {
            return appendNewPattern(result, escapeUnprintable, true);
        }
        try {
            if (!escapeUnprintable) {
                // TODO: The C++ version does not have this shortcut, and instead
                // always cleans up the pattern string,
                // which also escapes Utility.shouldAlwaysBeEscaped(c).
                // We should sync these implementations.
                result.append(pat);
                return result;
            }
            boolean oddNumberOfBackslashes = false;
            for (int i=0; i<pat.length(); ) {
                int c = pat.codePointAt(i);
                i += Character.charCount(c);
                if (Utility.isUnprintable(c)) {
                    // If the unprintable character is preceded by an odd
                    // number of backslashes, then it has been escaped
                    // and we omit the last backslash.
                    Utility.escape(result, c);
                    oddNumberOfBackslashes = false;
                } else if (!oddNumberOfBackslashes && c == '\\') {
                    // Temporarily withhold an odd-numbered backslash.
                    oddNumberOfBackslashes = true;
                } else {
                    if (oddNumberOfBackslashes) {
                        result.append('\\');
                    }
                    appendCodePoint(result, c);
                    oddNumberOfBackslashes = false;
                }
            }
            if (oddNumberOfBackslashes) {
                result.append('\\');
            }
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Generate and append a string representation of this set to result.
     * This does not use this.pat, the cleaned up copy of the string
     * passed to applyPattern().
     *
     * @param result the buffer into which to generate the pattern
     * @param escapeUnprintable escape unprintable characters if true
     * @stable ICU 2.0
     */
    public StringBuffer _generatePattern(StringBuffer result, boolean escapeUnprintable) {
        return _generatePattern(result, escapeUnprintable, true);
    }

    /**
     * Generate and append a string representation of this set to result.
     * This does not use this.pat, the cleaned up copy of the string
     * passed to applyPattern().
     *
     * @param result the buffer into which to generate the pattern
     * @param escapeUnprintable escape unprintable characters if true
     * @param includeStrings if false, doesn't include the strings.
     * @stable ICU 3.8
     */
    public StringBuffer _generatePattern(StringBuffer result,
            boolean escapeUnprintable, boolean includeStrings) {
        return appendNewPattern(result, escapeUnprintable, includeStrings);
    }

    // Implementation of public _generatePattern().
    // Allows other callers to use a StringBuilder while the existing API is stuck with StringBuffer.
    private <T extends Appendable> T appendNewPattern(
            T result, boolean escapeUnprintable, boolean includeStrings) {
        try {
            result.append('[');

            int i = 0;
            int limit = len & ~1;  // = 2 * getRangeCount()

            // If the set contains at least 2 intervals and includes both
            // MIN_VALUE and MAX_VALUE, then the inverse representation will
            // be more economical.
            //     if (getRangeCount() >= 2 &&
            //             getRangeStart(0) == MIN_VALUE &&
            //             getRangeEnd(last) == MAX_VALUE)
            // Invariant: list[len-1] == HIGH == MAX_VALUE + 1
            // If limit == len then len is even and the last range ends with MAX_VALUE.
            //
            // *But* do not write the inverse (complement) if there are strings.
            // Since ICU 70, the '^' performs a code point complement which removes all strings.
            if (len >= 4 && list[0] == 0 && limit == len && !hasStrings()) {
                // Emit the inverse
                result.append('^');
                // Offsetting the inversion list index by one lets us
                // iterate over the ranges of the set complement.
                i = 1;
                --limit;
            }

            // Emit the ranges as pairs.
            while (i < limit) {
                int start = list[i];  // getRangeStart()
                int end = list[i + 1] - 1;  // getRangeEnd() = range limit minus one
                if (!(0xd800 <= end && end <= 0xdbff)) {
                    _appendToPat(result, start, end, escapeUnprintable);
                    i += 2;
                } else {
                    // The range ends with a lead surrogate.
                    // Avoid writing what looks like a lead+trail surrogate pair.
                    // 1. Postpone ranges that start with a lead surrogate code point.
                    int firstLead = i;
                    while ((i += 2) < limit && list[i] <= 0xdbff) {}
                    int firstAfterLead = i;
                    // 2. Write following ranges that start with a trail surrogate code point.
                    while (i < limit && (start = list[i]) <= 0xdfff) {
                        _appendToPat(result, start, list[i + 1] - 1, escapeUnprintable);
                        i += 2;
                    }
                    // 3. Now write the postponed ranges.
                    for (int j = firstLead; j < firstAfterLead; j += 2) {
                        _appendToPat(result, list[j], list[j + 1] - 1, escapeUnprintable);
                    }
                }
            }

            if (includeStrings && hasStrings()) {
                for (String s : strings) {
                    result.append('{');
                    _appendToPat(result, s, escapeUnprintable);
                    result.append('}');
                }
            }
            result.append(']');
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Returns the number of elements in this set (its cardinality)
     * Note than the elements of a set may include both individual
     * codepoints and strings.
     *
     * @return the number of elements in this set (its cardinality).
     * @stable ICU 2.0
     */
    public int size() {
        int n = 0;
        int count = getRangeCount();
        for (int i = 0; i < count; ++i) {
            n += getRangeEnd(i) - getRangeStart(i) + 1;
        }
        return n + strings.size();
    }

    /**
     * Returns <tt>true</tt> if this set contains no elements.
     *
     * @return <tt>true</tt> if this set contains no elements.
     * @stable ICU 2.0
     */
    public boolean isEmpty() {
        return len == 1 && !hasStrings();
    }

    /**
     * @return true if this set contains multi-character strings or the empty string.
     * @stable ICU 70
     */
    public boolean hasStrings() {
        return !strings.isEmpty();
    }

    /**
     * Implementation of UnicodeMatcher API.  Returns <tt>true</tt> if
     * this set contains any character whose low byte is the given
     * value.  This is used by <tt>RuleBasedTransliterator</tt> for
     * indexing.
     * @stable ICU 2.0
     */
    @Override
    public boolean matchesIndexValue(int v) {
        /* The index value v, in the range [0,255], is contained in this set if
         * it is contained in any pair of this set.  Pairs either have the high
         * bytes equal, or unequal.  If the high bytes are equal, then we have
         * aaxx..aayy, where aa is the high byte.  Then v is contained if xx <=
         * v <= yy.  If the high bytes are unequal we have aaxx..bbyy, bb>aa.
         * Then v is contained if xx <= v || v <= yy.  (This is identical to the
         * time zone month containment logic.)
         */
        for (int i=0; i<getRangeCount(); ++i) {
            int low = getRangeStart(i);
            int high = getRangeEnd(i);
            if ((low & ~0xFF) == (high & ~0xFF)) {
                if ((low & 0xFF) <= v && v <= (high & 0xFF)) {
                    return true;
                }
            } else if ((low & 0xFF) <= v || v <= (high & 0xFF)) {
                return true;
            }
        }
        if (hasStrings()) {
            for (String s : strings) {
                if (s.isEmpty()) {
                    continue;  // skip the empty string
                }
                int c = UTF16.charAt(s, 0);
                if ((c & 0xFF) == v) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Implementation of UnicodeMatcher.matches().  Always matches the
     * longest possible multichar string.
     * @stable ICU 2.0
     */
    @Override
    public int matches(Replaceable text,
            int[] offset,
            int limit,
            boolean incremental) {

        if (offset[0] == limit) {
            if (contains(UnicodeMatcher.ETHER)) {
                return incremental ? U_PARTIAL_MATCH : U_MATCH;
            } else {
                return U_MISMATCH;
            }
        } else {
            if (hasStrings()) { // try strings first

                // might separate forward and backward loops later
                // for now they are combined

                // TODO Improve efficiency of this, at least in the forward
                // direction, if not in both.  In the forward direction we
                // can assume the strings are sorted.

                boolean forward = offset[0] < limit;

                // firstChar is the leftmost char to match in the
                // forward direction or the rightmost char to match in
                // the reverse direction.
                char firstChar = text.charAt(offset[0]);

                // If there are multiple strings that can match we
                // return the longest match.
                int highWaterLength = 0;

                for (String trial : strings) {
                    if (trial.isEmpty()) {
                        continue;  // skip the empty string
                    }

                    char c = trial.charAt(forward ? 0 : trial.length() - 1);

                    // Strings are sorted, so we can optimize in the
                    // forward direction.
                    if (forward && c > firstChar) break;
                    if (c != firstChar) continue;

                    int length = matchRest(text, offset[0], limit, trial);

                    if (incremental) {
                        int maxLen = forward ? limit-offset[0] : offset[0]-limit;
                        if (length == maxLen) {
                            // We have successfully matched but only up to limit.
                            return U_PARTIAL_MATCH;
                        }
                    }

                    if (length == trial.length()) {
                        // We have successfully matched the whole string.
                        if (length > highWaterLength) {
                            highWaterLength = length;
                        }
                        // In the forward direction we know strings
                        // are sorted so we can bail early.
                        if (forward && length < highWaterLength) {
                            break;
                        }
                        continue;
                    }
                }

                // We've checked all strings without a partial match.
                // If we have full matches, return the longest one.
                if (highWaterLength != 0) {
                    offset[0] += forward ? highWaterLength : -highWaterLength;
                    return U_MATCH;
                }
            }
            return super.matches(text, offset, limit, incremental);
        }
    }

    /**
     * Returns the longest match for s in text at the given position.
     * If limit > start then match forward from start+1 to limit
     * matching all characters except s.charAt(0).  If limit < start,
     * go backward starting from start-1 matching all characters
     * except s.charAt(s.length()-1).  This method assumes that the
     * first character, text.charAt(start), matches s, so it does not
     * check it.
     * @param text the text to match
     * @param start the first character to match.  In the forward
     * direction, text.charAt(start) is matched against s.charAt(0).
     * In the reverse direction, it is matched against
     * s.charAt(s.length()-1).
     * @param limit the limit offset for matching, either last+1 in
     * the forward direction, or last-1 in the reverse direction,
     * where last is the index of the last character to match.
     * @return If part of s matches up to the limit, return |limit -
     * start|.  If all of s matches before reaching the limit, return
     * s.length().  If there is a mismatch between s and text, return
     * 0
     */
    private static int matchRest (Replaceable text, int start, int limit, String s) {
        int maxLen;
        int slen = s.length();
        if (start < limit) {
            maxLen = limit - start;
            if (maxLen > slen) maxLen = slen;
            for (int i = 1; i < maxLen; ++i) {
                if (text.charAt(start + i) != s.charAt(i)) return 0;
            }
        } else {
            maxLen = start - limit;
            if (maxLen > slen) maxLen = slen;
            --slen; // <=> slen = s.length() - 1;
            for (int i = 1; i < maxLen; ++i) {
                if (text.charAt(start - i) != s.charAt(slen - i)) return 0;
            }
        }
        return maxLen;
    }

    /**
     * Tests whether the text matches at the offset. If so, returns the end of the longest substring that it matches. If not, returns -1.
     * @internal
     * @deprecated This API is ICU internal only.
     */
    @Deprecated
    public int matchesAt(CharSequence text, int offset) {
        int lastLen = -1;
        strings:
            if (hasStrings()) {
                char firstChar = text.charAt(offset);
                String trial = null;
                // find the first string starting with firstChar
                Iterator<String> it = strings.iterator();
                while (it.hasNext()) {
                    trial = it.next();
                    char firstStringChar = trial.charAt(0);
                    if (firstStringChar < firstChar) continue;
                    if (firstStringChar > firstChar) break strings;
                }

                // now keep checking string until we get the longest one
                for (;;) {
                    int tempLen = matchesAt(text, offset, trial);
                    if (lastLen > tempLen) break strings;
                    lastLen = tempLen;
                    if (!it.hasNext()) break;
                    trial = it.next();
                }
            }

        if (lastLen < 2) {
            int cp = UTF16.charAt(text, offset);
            if (contains(cp)) lastLen = UTF16.getCharCount(cp);
        }

        return offset+lastLen;
    }

    /**
     * Does one string contain another, starting at a specific offset?
     * @param text text to match
     * @param offsetInText offset within that text
     * @param substring substring to match at offset in text
     * @return -1 if match fails, otherwise other.length()
     */
    // Note: This method was moved from CollectionUtilities
    private static int matchesAt(CharSequence text, int offsetInText, CharSequence substring) {
        int len = substring.length();
        int textLength = text.length();
        if (textLength + offsetInText > len) {
            return -1;
        }
        int i = 0;
        for (int j = offsetInText; i < len; ++i, ++j) {
            char pc = substring.charAt(i);
            char tc = text.charAt(j);
            if (pc != tc) return -1;
        }
        return i;
    }

    /**
     * Implementation of UnicodeMatcher API.  Union the set of all
     * characters that may be matched by this object into the given
     * set.
     * @param toUnionTo the set into which to union the source characters
     * @stable ICU 2.2
     */
    @Override
    public void addMatchSetTo(UnicodeSet toUnionTo) {
        toUnionTo.addAll(this);
    }

    /**
     * Returns the index of the given character within this set, where
     * the set is ordered by ascending code point.  If the character
     * is not in this set, return -1.  The inverse of this method is
     * <code>charAt()</code>.
     * @return an index from 0..size()-1, or -1
     * @stable ICU 2.0
     */
    public int indexOf(int c) {
        if (c < MIN_VALUE || c > MAX_VALUE) {
            throw new IllegalArgumentException("Invalid code point U+" + Utility.hex(c, 6));
        }
        int i = 0;
        int n = 0;
        for (;;) {
            int start = list[i++];
            if (c < start) {
                return -1;
            }
            int limit = list[i++];
            if (c < limit) {
                return n + c - start;
            }
            n += limit - start;
        }
    }

    /**
     * Returns the character at the given index within this set, where
     * the set is ordered by ascending code point.  If the index is
     * out of range, return -1.  The inverse of this method is
     * <code>indexOf()</code>.
     * @param index an index from 0..size()-1
     * @return the character at the given index, or -1.
     * @stable ICU 2.0
     */
    public int charAt(int index) {
        if (index >= 0) {
            // len2 is the largest even integer <= len, that is, it is len
            // for even values and len-1 for odd values.  With odd values
            // the last entry is UNICODESET_HIGH.
            int len2 = len & ~1;
            for (int i=0; i < len2;) {
                int start = list[i++];
                int count = list[i++] - start;
                if (index < count) {
                    return start + index;
                }
                index -= count;
            }
        }
        return -1;
    }

    /**
     * Adds the specified range to this set if it is not already
     * present.  If this set already contains the specified range,
     * the call leaves this set unchanged.  If <code>start &gt; end</code>
     * then an empty range is added, leaving the set unchanged.
     *
     * @param start first character, inclusive, of range to be added
     * to this set.
     * @param end last character, inclusive, of range to be added
     * to this set.
     * @stable ICU 2.0
     */
    public UnicodeSet add(int start, int end) {
        checkFrozen();
        return add_unchecked(start, end);
    }

    /**
     * Adds all characters in range (uses preferred naming convention).
     * @param start The index of where to start on adding all characters.
     * @param end The index of where to end on adding all characters.
     * @return a reference to this object
     * @stable ICU 4.4
     */
    public UnicodeSet addAll(int start, int end) {
        checkFrozen();
        return add_unchecked(start, end);
    }

    // for internal use, after checkFrozen has been called
    private UnicodeSet add_unchecked(int start, int end) {
        if (start < MIN_VALUE || start > MAX_VALUE) {
            throw new IllegalArgumentException("Invalid code point U+" + Utility.hex(start, 6));
        }
        if (end < MIN_VALUE || end > MAX_VALUE) {
            throw new IllegalArgumentException("Invalid code point U+" + Utility.hex(end, 6));
        }
        if (start < end) {
            int limit = end + 1;
            // Fast path for adding a new range after the last one.
            // Odd list length: [..., lastStart, lastLimit, HIGH]
            if ((len & 1) != 0) {
                // If the list is empty, set lastLimit low enough to not be adjacent to 0.
                int lastLimit = len == 1 ? -2 : list[len - 2];
                if (lastLimit <= start) {
                    checkFrozen();
                    if (lastLimit == start) {
                        // Extend the last range.
                        list[len - 2] = limit;
                        if (limit == HIGH) {
                            --len;
                        }
                    } else {
                        list[len - 1] = start;
                        if (limit < HIGH) {
                            ensureCapacity(len + 2);
                            list[len++] = limit;
                            list[len++] = HIGH;
                        } else {  // limit == HIGH
                            ensureCapacity(len + 1);
                            list[len++] = HIGH;
                        }
                    }
                    pat = null;
                    return this;
                }
            }
            // This is slow. Could be much faster using findCodePoint(start)
            // and modifying the list, dealing with adjacent & overlapping ranges.
            add(range(start, end), 2, 0);
        } else if (start == end) {
            add(start);
        }
        return this;
    }

    //    /**
    //     * Format out the inversion list as a string, for debugging.  Uncomment when
    //     * needed.
    //     */
    //    public final String dump() {
    //        StringBuffer buf = new StringBuffer("[");
    //        for (int i=0; i<len; ++i) {
    //            if (i != 0) buf.append(", ");
    //            int c = list[i];
    //            //if (c <= 0x7F && c != '\n' && c != '\r' && c != '\t' && c != ' ') {
    //            //    buf.append((char) c);
    //            //} else {
    //                buf.append("U+").append(Utility.hex(c, (c<0x10000)?4:6));
    //            //}
    //        }
    //        buf.append("]");
    //        return buf.toString();
    //    }

    /**
     * Adds the specified character to this set if it is not already
     * present.  If this set already contains the specified character,
     * the call leaves this set unchanged.
     * @stable ICU 2.0
     */
    public final UnicodeSet add(int c) {
        checkFrozen();
        return add_unchecked(c);
    }

    // for internal use only, after checkFrozen has been called
    private final UnicodeSet add_unchecked(int c) {
        if (c < MIN_VALUE || c > MAX_VALUE) {
            throw new IllegalArgumentException("Invalid code point U+" + Utility.hex(c, 6));
        }

        // find smallest i such that c < list[i]
        // if odd, then it is IN the set
        // if even, then it is OUT of the set
        int i = findCodePoint(c);

        // already in set?
        if ((i & 1) != 0) return this;

        // HIGH is 0x110000
        // assert(list[len-1] == HIGH);

        // empty = [HIGH]
        // [start_0, limit_0, start_1, limit_1, HIGH]

        // [..., start_k-1, limit_k-1, start_k, limit_k, ..., HIGH]
        //                             ^
        //                             list[i]

        // i == 0 means c is before the first range
        // TODO: Is the "list[i]-1" a typo? Even if you pass MAX_VALUE into
        //      add_unchecked, the maximum value that "c" will be compared to
        //      is "MAX_VALUE-1" meaning that "if (c == MAX_VALUE)" will
        //      never be reached according to this logic.
        if (c == list[i]-1) {
            // c is before start of next range
            list[i] = c;
            // if we touched the HIGH mark, then add a new one
            if (c == MAX_VALUE) {
                ensureCapacity(len+1);
                list[len++] = HIGH;
            }
            if (i > 0 && c == list[i-1]) {
                // collapse adjacent ranges

                // [..., start_k-1, c, c, limit_k, ..., HIGH]
                //                     ^
                //                     list[i]
                System.arraycopy(list, i+1, list, i-1, len-i-1);
                len -= 2;
            }
        }

        else if (i > 0 && c == list[i-1]) {
            // c is after end of prior range
            list[i-1]++;
            // no need to check for collapse here
        }

        else {
            // At this point we know the new char is not adjacent to
            // any existing ranges, and it is not 10FFFF.


            // [..., start_k-1, limit_k-1, start_k, limit_k, ..., HIGH]
            //                             ^
            //                             list[i]

            // [..., start_k-1, limit_k-1, c, c+1, start_k, limit_k, ..., HIGH]
            //                             ^
            //                             list[i]

            // Don't use ensureCapacity() to save on copying.
            // NOTE: This has no measurable impact on performance,
            // but it might help in some usage patterns.
            if (len+2 > list.length) {
                int[] temp = new int[nextCapacity(len + 2)];
                if (i != 0) System.arraycopy(list, 0, temp, 0, i);
                System.arraycopy(list, i, temp, i+2, len-i);
                list = temp;
            } else {
                System.arraycopy(list, i, list, i+2, len-i);
            }

            list[i] = c;
            list[i+1] = c+1;
            len += 2;
        }

        pat = null;
        return this;
    }

    /**
     * Adds the specified multicharacter to this set if it is not already
     * present.  If this set already contains the multicharacter,
     * the call leaves this set unchanged.
     * Thus "ch" =&gt; {"ch"}
     *
     * @param s the source string
     * @return this object, for chaining
     * @stable ICU 2.0
     */
    public final UnicodeSet add(CharSequence s) {
        checkFrozen();
        int cp = getSingleCP(s);
        if (cp < 0) {
            String str = s.toString();
            if (!strings.contains(str)) {
                addString(str);
                pat = null;
            }
        } else {
            add_unchecked(cp, cp);
        }
        return this;
    }

    private void addString(CharSequence s) {
        if (strings == EMPTY_STRINGS) {
            strings = new TreeSet<>();
        }
        strings.add(s.toString());
    }

    /**
     * Utility for getting code point from single code point CharSequence.
     * See the public UTF16.getSingleCodePoint() (which returns -1 for null rather than throwing NPE).
     *
     * @return a code point IF the string consists of a single one.
     * otherwise returns -1.
     * @param s to test
     */
    private static int getSingleCP(CharSequence s) {
        if (s.length() == 1) return s.charAt(0);
        if (s.length() == 2) {
            int cp = Character.codePointAt(s, 0);
            if (cp > 0xFFFF) { // is surrogate pair
                return cp;
            }
        }
        return -1;
    }

    /**
     * Adds each of the characters in this string to the set. Thus "ch" =&gt; {"c", "h"}
     * If this set already any particular character, it has no effect on that character.
     * @param s the source string
     * @return this object, for chaining
     * @stable ICU 2.0
     */
    public final UnicodeSet addAll(CharSequence s) {
        checkFrozen();
        int cp;
        for (int i = 0; i < s.length(); i += UTF16.getCharCount(cp)) {
            cp = UTF16.charAt(s, i);
            add_unchecked(cp, cp);
        }
        return this;
    }

    /**
     * Retains EACH of the characters in this string. Note: "ch" == {"c", "h"}
     * If this set already any particular character, it has no effect on that character.
     * @param s the source string
     * @return this object, for chaining
     * @stable ICU 2.0
     */
    public final UnicodeSet retainAll(CharSequence s) {
        return retainAll(fromAll(s));
    }

    /**
     * Complement EACH of the characters in this string. Note: "ch" == {"c", "h"}
     * If this set already any particular character, it has no effect on that character.
     * @param s the source string
     * @return this object, for chaining
     * @stable ICU 2.0
     */
    public final UnicodeSet complementAll(CharSequence s) {
        return complementAll(fromAll(s));
    }

    /**
     * Remove EACH of the characters in this string. Note: "ch" == {"c", "h"}
     * If this set already any particular character, it has no effect on that character.
     * @param s the source string
     * @return this object, for chaining
     * @stable ICU 2.0
     */
    public final UnicodeSet removeAll(CharSequence s) {
        return removeAll(fromAll(s));
    }

    /**
     * Remove all strings from this UnicodeSet
     * @return this object, for chaining
     * @stable ICU 4.2
     */
    public final UnicodeSet removeAllStrings() {
        checkFrozen();
        if (hasStrings()) {
            strings.clear();
            pat = null;
        }
        return this;
    }

    /**
     * Makes a set from a multicharacter string. Thus "ch" =&gt; {"ch"}
     *
     * @param s the source string
     * @return a newly created set containing the given string
     * @stable ICU 2.0
     */
    public static UnicodeSet from(CharSequence s) {
        return new UnicodeSet().add(s);
    }


    /**
     * Makes a set from each of the characters in the string. Thus "ch" =&gt; {"c", "h"}
     * @param s the source string
     * @return a newly created set containing the given characters
     * @stable ICU 2.0
     */
    public static UnicodeSet fromAll(CharSequence s) {
        return new UnicodeSet().addAll(s);
    }


    /**
     * Retain only the elements in this set that are contained in the
     * specified range.  If <code>start &gt; end</code> then an empty range is
     * retained, leaving the set empty.
     *
     * @param start first character, inclusive, of range
     * @param end last character, inclusive, of range
     * @stable ICU 2.0
     */
    public UnicodeSet retain(int start, int end) {
        checkFrozen();
        if (start < MIN_VALUE || start > MAX_VALUE) {
            throw new IllegalArgumentException("Invalid code point U+" + Utility.hex(start, 6));
        }
        if (end < MIN_VALUE || end > MAX_VALUE) {
            throw new IllegalArgumentException("Invalid code point U+" + Utility.hex(end, 6));
        }
        if (start <= end) {
            retain(range(start, end), 2, 0);
        } else {
            clear();
        }
        return this;
    }

    /**
     * Retain the specified character from this set if it is present.
     * Upon return this set will be empty if it did not contain c, or
     * will only contain c if it did contain c.
     * @param c the character to be retained
     * @return this object, for chaining
     * @stable ICU 2.0
     */
    public final UnicodeSet retain(int c) {
        return retain(c, c);
    }

    /**
     * Retain the specified string in this set if it is present.
     * Upon return this set will be empty if it did not contain s, or
     * will only contain s if it did contain s.
     * @param cs the string to be retained
     * @return this object, for chaining
     * @stable ICU 2.0
     */
    public final UnicodeSet retain(CharSequence cs) {
        int cp = getSingleCP(cs);
        if (cp < 0) {
            checkFrozen();
            String s = cs.toString();
            boolean isIn = strings.contains(s);
            // Check for getRangeCount() first to avoid somewhat-expensive size()
            // when there are single code points.
            if (isIn && getRangeCount() == 0 && size() == 1) {
                return this;
            }
            clear();
            if (isIn) {
                addString(s);
            }
            pat = null;
        } else {
            retain(cp, cp);
        }
        return this;
    }

    /**
     * Removes the specified range from this set if it is present.
     * The set will not contain the specified range once the call
     * returns.  If <code>start &gt; end</code> then an empty range is
     * removed, leaving the set unchanged.
     *
     * @param start first character, inclusive, of range to be removed
     * from this set.
     * @param end last character, inclusive, of range to be removed
     * from this set.
     * @stable ICU 2.0
     */
    public UnicodeSet remove(int start, int end) {
        checkFrozen();
        if (start < MIN_VALUE || start > MAX_VALUE) {
            throw new IllegalArgumentException("Invalid code point U+" + Utility.hex(start, 6));
        }
        if (end < MIN_VALUE || end > MAX_VALUE) {
            throw new IllegalArgumentException("Invalid code point U+" + Utility.hex(end, 6));
        }
        if (start <= end) {
            retain(range(start, end), 2, 2);
        }
        return this;
    }

    /**
     * Removes the specified character from this set if it is present.
     * The set will not contain the specified character once the call
     * returns.
     * @param c the character to be removed
     * @return this object, for chaining
     * @stable ICU 2.0
     */
    public final UnicodeSet remove(int c) {
        return remove(c, c);
    }

    /**
     * Removes the specified string from this set if it is present.
     * The set will not contain the specified string once the call
     * returns.
     * @param s the string to be removed
     * @return this object, for chaining
     * @stable ICU 2.0
     */
    public final UnicodeSet remove(CharSequence s) {
        int cp = getSingleCP(s);
        if (cp < 0) {
            checkFrozen();
            String str = s.toString();
            if (strings.contains(str)) {
                strings.remove(str);
                pat = null;
            }
        } else {
            remove(cp, cp);
        }
        return this;
    }

    /**
     * Complements the specified range in this set.  Any character in
     * the range will be removed if it is in this set, or will be
     * added if it is not in this set.  If <code>start &gt; end</code>
     * then an empty range is complemented, leaving the set unchanged.
     *
     * @param start first character, inclusive, of range
     * @param end last character, inclusive, of range
     * @stable ICU 2.0
     */
    public UnicodeSet complement(int start, int end) {
        checkFrozen();
        if (start < MIN_VALUE || start > MAX_VALUE) {
            throw new IllegalArgumentException("Invalid code point U+" + Utility.hex(start, 6));
        }
        if (end < MIN_VALUE || end > MAX_VALUE) {
            throw new IllegalArgumentException("Invalid code point U+" + Utility.hex(end, 6));
        }
        if (start <= end) {
            xor(range(start, end), 2, 0);
        }
        pat = null;
        return this;
    }

    /**
     * Complements the specified character in this set.  The character
     * will be removed if it is in this set, or will be added if it is
     * not in this set.
     * @stable ICU 2.0
     */
    public final UnicodeSet complement(int c) {
        return complement(c, c);
    }

    /**
     * This is equivalent to
     * <code>complement(MIN_VALUE, MAX_VALUE)</code>.
     *
     * <p><strong>Note:</strong> This performs a symmetric difference with all code points
     * <em>and thus retains all multicharacter strings</em>.
     * In order to achieve a "code point complement" (all code points minus this set),
     * the easiest is to .{@link #complement()}.{@link #removeAllStrings()} .
     *
     * @stable ICU 2.0
     */
    public UnicodeSet complement() {
        checkFrozen();
        if (list[0] == LOW) {
            System.arraycopy(list, 1, list, 0, len-1);
            --len;
        } else {
            ensureCapacity(len+1);
            System.arraycopy(list, 0, list, 1, len);
            list[0] = LOW;
            ++len;
        }
        pat = null;
        return this;
    }

    /**
     * Complement the specified string in this set.
     * The set will not contain the specified string once the call
     * returns.
     *
     * @param s the string to complement
     * @return this object, for chaining
     * @stable ICU 2.0
     */
    public final UnicodeSet complement(CharSequence s) {
        checkFrozen();
        int cp = getSingleCP(s);
        if (cp < 0) {
            String s2 = s.toString();
            if (strings.contains(s2)) {
                strings.remove(s2);
            } else {
                addString(s2);
            }
            pat = null;
        } else {
            complement(cp, cp);
        }
        return this;
    }

    /**
     * Returns true if this set contains the given character.
     * @param c character to be checked for containment
     * @return true if the test condition is met
     * @stable ICU 2.0
     */
    @Override
    public boolean contains(int c) {
        if (c < MIN_VALUE || c > MAX_VALUE) {
            throw new IllegalArgumentException("Invalid code point U+" + Utility.hex(c, 6));
        }
        if (bmpSet != null) {
            return bmpSet.contains(c);
        }
        if (stringSpan != null) {
            return stringSpan.contains(c);
        }

        /*
        // Set i to the index of the start item greater than ch
        // We know we will terminate without length test!
        int i = -1;
        while (true) {
            if (c < list[++i]) break;
        }
         */

        int i = findCodePoint(c);

        return ((i & 1) != 0); // return true if odd
    }

    /**
     * Returns the smallest value i such that c < list[i].  Caller
     * must ensure that c is a legal value or this method will enter
     * an infinite loop.  This method performs a binary search.
     * @param c a character in the range MIN_VALUE..MAX_VALUE
     * inclusive
     * @return the smallest integer i in the range 0..len-1,
     * inclusive, such that c < list[i]
     */
    private final int findCodePoint(int c) {
        /* Examples:
                                           findCodePoint(c)
           set              list[]         c=0 1 3 4 7 8
           ===              ==============   ===========
           []               [110000]         0 0 0 0 0 0
           [\u0000-\u0003]  [0, 4, 110000]   1 1 1 2 2 2
           [\u0004-\u0007]  [4, 8, 110000]   0 0 0 1 1 2
           [:all:]          [0, 110000]      1 1 1 1 1 1
         */

        // Return the smallest i such that c < list[i].  Assume
        // list[len - 1] == HIGH and that c is legal (0..HIGH-1).
        if (c < list[0]) return 0;
        // High runner test.  c is often after the last range, so an
        // initial check for this condition pays off.
        if (len >= 2 && c >= list[len-2]) return len-1;
        int lo = 0;
        int hi = len - 1;
        // invariant: c >= list[lo]
        // invariant: c < list[hi]
        for (;;) {
            int i = (lo + hi) >>> 1;
        if (i == lo) return hi;
        if (c < list[i]) {
            hi = i;
        } else {
            lo = i;
        }
        }
    }

    //    //----------------------------------------------------------------
    //    // Unrolled binary search
    //    //----------------------------------------------------------------
    //
    //    private int validLen = -1; // validated value of len
    //    private int topOfLow;
    //    private int topOfHigh;
    //    private int power;
    //    private int deltaStart;
    //
    //    private void validate() {
    //        if (len <= 1) {
    //            throw new IllegalArgumentException("list.len==" + len + "; must be >1");
    //        }
    //
    //        // find greatest power of 2 less than or equal to len
    //        for (power = exp2.length-1; power > 0 && exp2[power] > len; power--) {}
    //
    //        // assert(exp2[power] <= len);
    //
    //        // determine the starting points
    //        topOfLow = exp2[power] - 1;
    //        topOfHigh = len - 1;
    //        deltaStart = exp2[power-1];
    //        validLen = len;
    //    }
    //
    //    private static final int exp2[] = {
    //        0x1, 0x2, 0x4, 0x8,
    //        0x10, 0x20, 0x40, 0x80,
    //        0x100, 0x200, 0x400, 0x800,
    //        0x1000, 0x2000, 0x4000, 0x8000,
    //        0x10000, 0x20000, 0x40000, 0x80000,
    //        0x100000, 0x200000, 0x400000, 0x800000,
    //        0x1000000, 0x2000000, 0x4000000, 0x8000000,
    //        0x10000000, 0x20000000 // , 0x40000000 // no unsigned int in Java
    //    };
    //
    //    /**
    //     * Unrolled lowest index GT.
    //     */
    //    private final int leastIndexGT(int searchValue) {
    //
    //        if (len != validLen) {
    //            if (len == 1) return 0;
    //            validate();
    //        }
    //        int temp;
    //
    //        // set up initial range to search. Each subrange is a power of two in length
    //        int high = searchValue < list[topOfLow] ? topOfLow : topOfHigh;
    //
    //        // Completely unrolled binary search, folhighing "Programming Pearls"
    //        // Each case deliberately falls through to the next
    //        // Logically, list[-1] < all_search_values && list[count] > all_search_values
    //        // although the values -1 and count are never actually touched.
    //
    //        // The bounds at each point are low & high,
    //        // where low == high - delta*2
    //        // so high - delta is the midpoint
    //
    //        // The invariant AFTER each line is that list[low] < searchValue <= list[high]
    //
    //        switch (power) {
    //        //case 31: if (searchValue < list[temp = high-0x40000000]) high = temp; // no unsigned int in Java
    //        case 30: if (searchValue < list[temp = high-0x20000000]) high = temp;
    //        case 29: if (searchValue < list[temp = high-0x10000000]) high = temp;
    //
    //        case 28: if (searchValue < list[temp = high- 0x8000000]) high = temp;
    //        case 27: if (searchValue < list[temp = high- 0x4000000]) high = temp;
    //        case 26: if (searchValue < list[temp = high- 0x2000000]) high = temp;
    //        case 25: if (searchValue < list[temp = high- 0x1000000]) high = temp;
    //
    //        case 24: if (searchValue < list[temp = high-  0x800000]) high = temp;
    //        case 23: if (searchValue < list[temp = high-  0x400000]) high = temp;
    //        case 22: if (searchValue < list[temp = high-  0x200000]) high = temp;
    //        case 21: if (searchValue < list[temp = high-  0x100000]) high = temp;
    //
    //        case 20: if (searchValue < list[temp = high-   0x80000]) high = temp;
    //        case 19: if (searchValue < list[temp = high-   0x40000]) high = temp;
    //        case 18: if (searchValue < list[temp = high-   0x20000]) high = temp;
    //        case 17: if (searchValue < list[temp = high-   0x10000]) high = temp;
    //
    //        case 16: if (searchValue < list[temp = high-    0x8000]) high = temp;
    //        case 15: if (searchValue < list[temp = high-    0x4000]) high = temp;
    //        case 14: if (searchValue < list[temp = high-    0x2000]) high = temp;
    //        case 13: if (searchValue < list[temp = high-    0x1000]) high = temp;
    //
    //        case 12: if (searchValue < list[temp = high-     0x800]) high = temp;
    //        case 11: if (searchValue < list[temp = high-     0x400]) high = temp;
    //        case 10: if (searchValue < list[temp = high-     0x200]) high = temp;
    //        case  9: if (searchValue < list[temp = high-     0x100]) high = temp;
    //
    //        case  8: if (searchValue < list[temp = high-      0x80]) high = temp;
    //        case  7: if (searchValue < list[temp = high-      0x40]) high = temp;
    //        case  6: if (searchValue < list[temp = high-      0x20]) high = temp;
    //        case  5: if (searchValue < list[temp = high-      0x10]) high = temp;
    //
    //        case  4: if (searchValue < list[temp = high-       0x8]) high = temp;
    //        case  3: if (searchValue < list[temp = high-       0x4]) high = temp;
    //        case  2: if (searchValue < list[temp = high-       0x2]) high = temp;
    //        case  1: if (searchValue < list[temp = high-       0x1]) high = temp;
    //        }
    //
    //        return high;
    //    }
    //
    //    // For debugging only
    //    public int len() {
    //        return len;
    //    }
    //
    //    //----------------------------------------------------------------
    //    //----------------------------------------------------------------

    /**
     * Returns true if this set contains every character
     * of the given range.
     * @param start first character, inclusive, of the range
     * @param end last character, inclusive, of the range
     * @return true if the test condition is met
     * @stable ICU 2.0
     */
    public boolean contains(int start, int end) {
        if (start < MIN_VALUE || start > MAX_VALUE) {
            throw new IllegalArgumentException("Invalid code point U+" + Utility.hex(start, 6));
        }
        if (end < MIN_VALUE || end > MAX_VALUE) {
            throw new IllegalArgumentException("Invalid code point U+" + Utility.hex(end, 6));
        }
        //int i = -1;
        //while (true) {
        //    if (start < list[++i]) break;
        //}
        int i = findCodePoint(start);
        return ((i & 1) != 0 && end < list[i]);
    }

    /**
     * Returns <tt>true</tt> if this set contains the given
     * multicharacter string.
     * @param s string to be checked for containment
     * @return <tt>true</tt> if this set contains the specified string
     * @stable ICU 2.0
     */
    public final boolean contains(CharSequence s) {

        int cp = getSingleCP(s);
        if (cp < 0) {
            return strings.contains(s.toString());
        } else {
            return contains(cp);
        }
    }

    /**
     * Returns true if this set contains all the characters and strings
     * of the given set.
     * @param b set to be checked for containment
     * @return true if the test condition is met
     * @stable ICU 2.0
     */
    public boolean containsAll(UnicodeSet b) {
        // The specified set is a subset if all of its pairs are contained in
        // this set. This implementation accesses the lists directly for speed.
        // TODO: this could be faster if size() were cached. But that would affect building speed
        // so it needs investigation.
        int[] listB = b.list;
        boolean needA = true;
        boolean needB = true;
        int aPtr = 0;
        int bPtr = 0;
        int aLen = len - 1;
        int bLen = b.len - 1;
        int startA = 0, startB = 0, limitA = 0, limitB = 0;
        while (true) {
            // double iterations are such a pain...
            if (needA) {
                if (aPtr >= aLen) {
                    // ran out of A. If B is also exhausted, then break;
                    if (needB && bPtr >= bLen) {
                        break;
                    }
                    return false;
                }
                startA = list[aPtr++];
                limitA = list[aPtr++];
            }
            if (needB) {
                if (bPtr >= bLen) {
                    // ran out of B. Since we got this far, we have an A and we are ok so far
                    break;
                }
                startB = listB[bPtr++];
                limitB = listB[bPtr++];
            }
            // if B doesn't overlap and is greater than A, get new A
            if (startB >= limitA) {
                needA = true;
                needB = false;
                continue;
            }
            // if B is wholy contained in A, then get a new B
            if (startB >= startA && limitB <= limitA) {
                needA = false;
                needB = true;
                continue;
            }
            // all other combinations mean we fail
            return false;
        }

        if (!strings.containsAll(b.strings)) return false;
        return true;
    }

    //    /**
    //     * Returns true if this set contains all the characters and strings
    //     * of the given set.
    //     * @param c set to be checked for containment
    //     * @return true if the test condition is met
    //     * @stable ICU 2.0
    //     */
    //    public boolean containsAllOld(UnicodeSet c) {
    //        // The specified set is a subset if all of its pairs are contained in
    //        // this set.  It's possible to code this more efficiently in terms of
    //        // direct manipulation of the inversion lists if the need arises.
    //        int n = c.getRangeCount();
    //        for (int i=0; i<n; ++i) {
    //            if (!contains(c.getRangeStart(i), c.getRangeEnd(i))) {
    //                return false;
    //            }
    //        }
    //        if (!strings.containsAll(c.strings)) return false;
    //        return true;
    //    }

    /**
     * Returns true if there is a partition of the string such that this set contains each of the partitioned strings.
     * For example, for the Unicode set [a{bc}{cd}]<br>
     * containsAll is true for each of: "a", "bc", ""cdbca"<br>
     * containsAll is false for each of: "acb", "bcda", "bcx"<br>
     * @param s string containing characters to be checked for containment
     * @return true if the test condition is met
     * @stable ICU 2.0
     */
    public boolean containsAll(String s) {
        int cp;
        for (int i = 0; i < s.length(); i += UTF16.getCharCount(cp)) {
            cp = UTF16.charAt(s, i);
            if (!contains(cp))  {
                if (!hasStrings()) {
                    return false;
                }
                return containsAll(s, 0);
            }
        }
        return true;
    }

    /**
     * Recursive routine called if we fail to find a match in containsAll, and there are strings
     * @param s source string
     * @param i point to match to the end on
     * @return true if ok
     */
    private boolean containsAll(String s, int i) {
        if (i >= s.length()) {
            return true;
        }
        int  cp= UTF16.charAt(s, i);
        if (contains(cp) && containsAll(s, i+UTF16.getCharCount(cp))) {
            return true;
        }
        for (String setStr : strings) {
            if (!setStr.isEmpty() &&  // skip the empty string
                    s.startsWith(setStr, i) &&  containsAll(s, i+setStr.length())) {
                return true;
            }
        }
        return false;

    }

    /**
     * Get the Regex equivalent for this UnicodeSet
     * @return regex pattern equivalent to this UnicodeSet
     * @internal
     * @deprecated This API is ICU internal only.
     */
    @Deprecated
    public String getRegexEquivalent() {
        if (!hasStrings()) {
            return toString();
        }
        StringBuilder result = new StringBuilder("(?:");
        appendNewPattern(result, true, false);
        for (String s : strings) {
            result.append('|');
            _appendToPat(result, s, true);
        }
        return result.append(")").toString();
    }

    /**
     * Returns true if this set contains none of the characters
     * of the given range.
     * @param start first character, inclusive, of the range
     * @param end last character, inclusive, of the range
     * @return true if the test condition is met
     * @stable ICU 2.0
     */
    public boolean containsNone(int start, int end) {
        if (start < MIN_VALUE || start > MAX_VALUE) {
            throw new IllegalArgumentException("Invalid code point U+" + Utility.hex(start, 6));
        }
        if (end < MIN_VALUE || end > MAX_VALUE) {
            throw new IllegalArgumentException("Invalid code point U+" + Utility.hex(end, 6));
        }
        int i = -1;
        while (true) {
            if (start < list[++i]) break;
        }
        return ((i & 1) == 0 && end < list[i]);
    }

    /**
     * Returns true if none of the characters or strings in this UnicodeSet appears in the string.
     * For example, for the Unicode set [a{bc}{cd}]<br>
     * containsNone is true for: "xy", "cb"<br>
     * containsNone is false for: "a", "bc", "bcd"<br>
     * @param b set to be checked for containment
     * @return true if the test condition is met
     * @stable ICU 2.0
     */
    public boolean containsNone(UnicodeSet b) {
        // The specified set is a subset if some of its pairs overlap with some of this set's pairs.
        // This implementation accesses the lists directly for speed.
        int[] listB = b.list;
        boolean needA = true;
        boolean needB = true;
        int aPtr = 0;
        int bPtr = 0;
        int aLen = len - 1;
        int bLen = b.len - 1;
        int startA = 0, startB = 0, limitA = 0, limitB = 0;
        while (true) {
            // double iterations are such a pain...
            if (needA) {
                if (aPtr >= aLen) {
                    // ran out of A: break so we test strings
                    break;
                }
                startA = list[aPtr++];
                limitA = list[aPtr++];
            }
            if (needB) {
                if (bPtr >= bLen) {
                    // ran out of B: break so we test strings
                    break;
                }
                startB = listB[bPtr++];
                limitB = listB[bPtr++];
            }
            // if B is higher than any part of A, get new A
            if (startB >= limitA) {
                needA = true;
                needB = false;
                continue;
            }
            // if A is higher than any part of B, get new B
            if (startA >= limitB) {
                needA = false;
                needB = true;
                continue;
            }
            // all other combinations mean we fail
            return false;
        }

        if (!SortedSetRelation.hasRelation(strings, SortedSetRelation.DISJOINT, b.strings)) return false;
        return true;
    }

    //    /**
    //     * Returns true if none of the characters or strings in this UnicodeSet appears in the string.
    //     * For example, for the Unicode set [a{bc}{cd}]<br>
    //     * containsNone is true for: "xy", "cb"<br>
    //     * containsNone is false for: "a", "bc", "bcd"<br>
    //     * @param c set to be checked for containment
    //     * @return true if the test condition is met
    //     * @stable ICU 2.0
    //     */
    //    public boolean containsNoneOld(UnicodeSet c) {
    //        // The specified set is a subset if all of its pairs are contained in
    //        // this set.  It's possible to code this more efficiently in terms of
    //        // direct manipulation of the inversion lists if the need arises.
    //        int n = c.getRangeCount();
    //        for (int i=0; i<n; ++i) {
    //            if (!containsNone(c.getRangeStart(i), c.getRangeEnd(i))) {
    //                return false;
    //            }
    //        }
    //        if (!SortedSetRelation.hasRelation(strings, SortedSetRelation.DISJOINT, c.strings)) return false;
    //        return true;
    //    }

    /**
     * Returns true if this set contains none of the characters
     * of the given string.
     * @param s string containing characters to be checked for containment
     * @return true if the test condition is met
     * @stable ICU 2.0
     */
    public boolean containsNone(CharSequence s) {
        return span(s, SpanCondition.NOT_CONTAINED) == s.length();
    }

    /**
     * Returns true if this set contains one or more of the characters
     * in the given range.
     * @param start first character, inclusive, of the range
     * @param end last character, inclusive, of the range
     * @return true if the condition is met
     * @stable ICU 2.0
     */
    public final boolean containsSome(int start, int end) {
        return !containsNone(start, end);
    }

    /**
     * Returns true if this set contains one or more of the characters
     * and strings of the given set.
     * @param s set to be checked for containment
     * @return true if the condition is met
     * @stable ICU 2.0
     */
    public final boolean containsSome(UnicodeSet s) {
        return !containsNone(s);
    }

    /**
     * Returns true if this set contains one or more of the characters
     * of the given string.
     * @param s string containing characters to be checked for containment
     * @return true if the condition is met
     * @stable ICU 2.0
     */
    public final boolean containsSome(CharSequence s) {
        return !containsNone(s);
    }


    /**
     * Adds all of the elements in the specified set to this set if
     * they're not already present.  This operation effectively
     * modifies this set so that its value is the <i>union</i> of the two
     * sets.  The behavior of this operation is unspecified if the specified
     * collection is modified while the operation is in progress.
     *
     * @param c set whose elements are to be added to this set.
     * @stable ICU 2.0
     */
    public UnicodeSet addAll(UnicodeSet c) {
        checkFrozen();
        add(c.list, c.len, 0);
        if (c.hasStrings()) {
            if (strings == EMPTY_STRINGS) {
                strings = new TreeSet<>(c.strings);
            } else {
                strings.addAll(c.strings);
            }
        }
        return this;
    }

    /**
     * Retains only the elements in this set that are contained in the
     * specified set.  In other words, removes from this set all of
     * its elements that are not contained in the specified set.  This
     * operation effectively modifies this set so that its value is
     * the <i>intersection</i> of the two sets.
     *
     * @param c set that defines which elements this set will retain.
     * @stable ICU 2.0
     */
    public UnicodeSet retainAll(UnicodeSet c) {
        checkFrozen();
        retain(c.list, c.len, 0);
        if (hasStrings()) {
            if (!c.hasStrings()) {
                strings.clear();
            } else {
                strings.retainAll(c.strings);
            }
        }
        return this;
    }

    /**
     * Removes from this set all of its elements that are contained in the
     * specified set.  This operation effectively modifies this
     * set so that its value is the <i>asymmetric set difference</i> of
     * the two sets.
     *
     * @param c set that defines which elements will be removed from
     *          this set.
     * @stable ICU 2.0
     */
    public UnicodeSet removeAll(UnicodeSet c) {
        checkFrozen();
        retain(c.list, c.len, 2);
        if (hasStrings() && c.hasStrings()) {
            strings.removeAll(c.strings);
        }
        return this;
    }

    /**
     * Complements in this set all elements contained in the specified
     * set.  Any character in the other set will be removed if it is
     * in this set, or will be added if it is not in this set.
     *
     * @param c set that defines which elements will be complemented from
     *          this set.
     * @stable ICU 2.0
     */
    public UnicodeSet complementAll(UnicodeSet c) {
        checkFrozen();
        xor(c.list, c.len, 0);
        if (c.hasStrings()) {
            if (strings == EMPTY_STRINGS) {
                strings = new TreeSet<>(c.strings);
            } else {
                SortedSetRelation.doOperation(strings, SortedSetRelation.COMPLEMENTALL, c.strings);
            }
        }
        return this;
    }

    /**
     * Removes all of the elements from this set.  This set will be
     * empty after this call returns.
     * @stable ICU 2.0
     */
    public UnicodeSet clear() {
        checkFrozen();
        list[0] = HIGH;
        len = 1;
        pat = null;
        if (hasStrings()) {
            strings.clear();
        }
        return this;
    }

    /**
     * Iteration method that returns the number of ranges contained in
     * this set.
     * @see #getRangeStart
     * @see #getRangeEnd
     * @stable ICU 2.0
     */
    public int getRangeCount() {
        return len/2;
    }

    /**
     * Iteration method that returns the first character in the
     * specified range of this set.
     * @exception ArrayIndexOutOfBoundsException if index is outside
     * the range <code>0..getRangeCount()-1</code>
     * @see #getRangeCount
     * @see #getRangeEnd
     * @stable ICU 2.0
     */
    public int getRangeStart(int index) {
        return list[index*2];
    }

    /**
     * Iteration method that returns the last character in the
     * specified range of this set.
     * @exception ArrayIndexOutOfBoundsException if index is outside
     * the range <code>0..getRangeCount()-1</code>
     * @see #getRangeStart
     * @see #getRangeEnd
     * @stable ICU 2.0
     */
    public int getRangeEnd(int index) {
        return (list[index*2 + 1] - 1);
    }

    /**
     * Reallocate this objects internal structures to take up the least
     * possible space, without changing this object's value.
     * @stable ICU 2.0
     */
    public UnicodeSet compact() {
        checkFrozen();
        if ((len + 7) < list.length) {
            // If we have more than a little unused capacity, shrink it to len.
            list = Arrays.copyOf(list, len);
        }
        rangeList = null;
        buffer = null;
        if (strings != EMPTY_STRINGS && strings.isEmpty()) {
            strings = EMPTY_STRINGS;
        }
        return this;
    }

    /**
     * Compares the specified object with this set for equality.  Returns
     * <tt>true</tt> if the specified object is also a set, the two sets
     * have the same size, and every member of the specified set is
     * contained in this set (or equivalently, every member of this set is
     * contained in the specified set).
     *
     * @param o Object to be compared for equality with this set.
     * @return <tt>true</tt> if the specified Object is equal to this set.
     * @stable ICU 2.0
     */
    @Override
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }
        if (this == o) {
            return true;
        }
        try {
            UnicodeSet that = (UnicodeSet) o;
            if (len != that.len) return false;
            for (int i = 0; i < len; ++i) {
                if (list[i] != that.list[i]) return false;
            }
            if (!strings.equals(that.strings)) return false;
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    /**
     * Returns the hash code value for this set.
     *
     * @return the hash code value for this set.
     * @see java.lang.Object#hashCode()
     * @stable ICU 2.0
     */
    @Override
    public int hashCode() {
        int result = len;
        for (int i = 0; i < len; ++i) {
            result *= 1000003;
            result += list[i];
        }
        return result;
    }

    /**
     * Return a programmer-readable string representation of this object.
     * @stable ICU 2.0
     */
    @Override
    public String toString() {
        return toPattern(true);
    }

    //----------------------------------------------------------------
    // Implementation: Pattern parsing
    //----------------------------------------------------------------

    /**
     * Parses the given pattern, starting at the given position.  The character
     * at pattern.charAt(pos.getIndex()) must be '[', or the parse fails.
     * Parsing continues until the corresponding closing ']'.  If a syntax error
     * is encountered between the opening and closing brace, the parse fails.
     * Upon return from a successful parse, the ParsePosition is updated to
     * point to the character following the closing ']', and an inversion
     * list for the parsed pattern is returned.  This method
     * calls itself recursively to parse embedded subpatterns.
     *
     * @param pattern the string containing the pattern to be parsed.  The
     * portion of the string from pos.getIndex(), which must be a '[', to the
     * corresponding closing ']', is parsed.
     * @param pos upon entry, the position at which to being parsing.  The
     * character at pattern.charAt(pos.getIndex()) must be a '['.  Upon return
     * from a successful parse, pos.getIndex() is either the character after the
     * closing ']' of the parsed pattern, or pattern.length() if the closing ']'
     * is the last character of the pattern string.
     * @return an inversion list for the parsed substring
     * of <code>pattern</code>
     * @exception java.lang.IllegalArgumentException if the parse fails.
     * @internal
     * @deprecated This API is ICU internal only.
     */
    @Deprecated
    public UnicodeSet applyPattern(String pattern,
            ParsePosition pos,
            SymbolTable symbols,
            int options) {

        // Need to build the pattern in a temporary string because
        // _applyPattern calls add() etc., which set pat to empty.
        boolean parsePositionWasNull = pos == null;
        if (parsePositionWasNull) {
            pos = new ParsePosition(0);
        }

        StringBuilder rebuiltPat = new StringBuilder();
        RuleCharacterIterator chars =
                new RuleCharacterIterator(pattern, symbols, pos);
        applyPattern(chars, symbols, rebuiltPat, options, 0);
        if (chars.inVariable()) {
            syntaxError(chars, "Extra chars in variable value");
        }
        pat = rebuiltPat.toString();
        if (parsePositionWasNull) {
            int i = pos.getIndex();

            // Skip over trailing whitespace
            if ((options & IGNORE_SPACE) != 0) {
                i = PatternProps.skipWhiteSpace(pattern, i);
            }

            if (i != pattern.length()) {
                throw new IllegalArgumentException("Parse of \"" + pattern +
                        "\" failed at " + i);
            }
        }
        return this;
    }

    // Add constants to make the applyPattern() code easier to follow.

    private static final int LAST0_START = 0,
            LAST1_RANGE = 1,
            LAST2_SET = 2;

    private static final int MODE0_NONE = 0,
            MODE1_INBRACKET = 1,
            MODE2_OUTBRACKET = 2;

    private static final int SETMODE0_NONE = 0,
            SETMODE1_UNICODESET = 1,
            SETMODE2_PROPERTYPAT = 2,
            SETMODE3_PREPARSED = 3;

    private static final int MAX_DEPTH = 100;

    /**
     * Parse the pattern from the given RuleCharacterIterator.  The
     * iterator is advanced over the parsed pattern.
     * @param chars iterator over the pattern characters.  Upon return
     * it will be advanced to the first character after the parsed
     * pattern, or the end of the iteration if all characters are
     * parsed.
     * @param symbols symbol table to use to parse and dereference
     * variables, or null if none.
     * @param rebuiltPat the pattern that was parsed, rebuilt or
     * copied from the input pattern, as appropriate.
     * @param options a bit mask.
     * Valid options are {@link #IGNORE_SPACE} and
     * at most one of {@link #CASE_INSENSITIVE}, {@link #ADD_CASE_MAPPINGS},
     * {@link #SIMPLE_CASE_INSENSITIVE}. These case options are mutually exclusive.
     */
    private void applyPattern(RuleCharacterIterator chars, SymbolTable symbols,
            Appendable rebuiltPat, int options, int depth) {
        if (depth > MAX_DEPTH) {
            syntaxError(chars, "Pattern nested too deeply");
        }

        // Syntax characters: [ ] ^ - & { }

        // Recognized special forms for chars, sets: c-c s-s s&s

        int opts = RuleCharacterIterator.PARSE_VARIABLES |
                RuleCharacterIterator.PARSE_ESCAPES;
        if ((options & IGNORE_SPACE) != 0) {
            opts |= RuleCharacterIterator.SKIP_WHITESPACE;
        }

        StringBuilder patBuf = new StringBuilder(), buf = null;
        boolean usePat = false;
        UnicodeSet scratch = null;
        RuleCharacterIterator.Position backup = null;

        // mode: 0=before [, 1=between [...], 2=after ]
        // lastItem: 0=none, 1=char, 2=set
        int lastItem = LAST0_START, lastChar = 0, mode = MODE0_NONE;
        char op = 0;

        boolean invert = false;

        clear();
        String lastString = null;

        while (mode != MODE2_OUTBRACKET && !chars.atEnd()) {
            //Eclipse stated the following is "dead code"
            /*
            if (false) {
                // Debugging assertion
                if (!((lastItem == 0 && op == 0) ||
                        (lastItem == 1 && (op == 0 || op == '-')) ||
                        (lastItem == 2 && (op == 0 || op == '-' || op == '&')))) {
                    throw new IllegalArgumentException();
                }
            }*/

            int c = 0;
            boolean literal = false;
            UnicodeSet nested = null;

            // -------- Check for property pattern

            // setMode: 0=none, 1=unicodeset, 2=propertypat, 3=preparsed
            int setMode = SETMODE0_NONE;
            if (resemblesPropertyPattern(chars, opts)) {
                setMode = SETMODE2_PROPERTYPAT;
            }

            // -------- Parse '[' of opening delimiter OR nested set.
            // If there is a nested set, use `setMode' to define how
            // the set should be parsed.  If the '[' is part of the
            // opening delimiter for this pattern, parse special
            // strings "[", "[^", "[-", and "[^-".  Check for stand-in
            // characters representing a nested set in the symbol
            // table.

            else {
                // Prepare to backup if necessary
                backup = chars.getPos(backup);
                c = chars.next(opts);
                literal = chars.isEscaped();

                if (c == '[' && !literal) {
                    if (mode == MODE1_INBRACKET) {
                        chars.setPos(backup); // backup
                        setMode = SETMODE1_UNICODESET;
                    } else {
                        // Handle opening '[' delimiter
                        mode = MODE1_INBRACKET;
                        patBuf.append('[');
                        backup = chars.getPos(backup); // prepare to backup
                        c = chars.next(opts);
                        literal = chars.isEscaped();
                        if (c == '^' && !literal) {
                            invert = true;
                            patBuf.append('^');
                            backup = chars.getPos(backup); // prepare to backup
                            c = chars.next(opts);
                            literal = chars.isEscaped();
                        }
                        // Fall through to handle special leading '-';
                        // otherwise restart loop for nested [], \p{}, etc.
                        if (c == '-') {
                            literal = true;
                            // Fall through to handle literal '-' below
                        } else {
                            chars.setPos(backup); // backup
                            continue;
                        }
                    }
                } else if (symbols != null) {
                    UnicodeMatcher m = symbols.lookupMatcher(c); // may be null
                    if (m != null) {
                        try {
                            nested = (UnicodeSet) m;
                            setMode = SETMODE3_PREPARSED;
                        } catch (ClassCastException e) {
                            syntaxError(chars, "Syntax error");
                        }
                    }
                }
            }

            // -------- Handle a nested set.  This either is inline in
            // the pattern or represented by a stand-in that has
            // previously been parsed and was looked up in the symbol
            // table.

            if (setMode != SETMODE0_NONE) {
                if (lastItem == LAST1_RANGE) {
                    if (op != 0) {
                        syntaxError(chars, "Char expected after operator");
                    }
                    add_unchecked(lastChar, lastChar);
                    _appendToPat(patBuf, lastChar, false);
                    lastItem = LAST0_START;
                    op = 0;
                }

                if (op == '-' || op == '&') {
                    patBuf.append(op);
                }

                if (nested == null) {
                    if (scratch == null) scratch = new UnicodeSet();
                    nested = scratch;
                }
                switch (setMode) {
                case SETMODE1_UNICODESET:
                    nested.applyPattern(chars, symbols, patBuf, options, depth + 1);
                    break;
                case SETMODE2_PROPERTYPAT:
                    chars.skipIgnored(opts);
                    nested.applyPropertyPattern(chars, patBuf, symbols);
                    break;
                case SETMODE3_PREPARSED: // `nested' already parsed
                    nested._toPattern(patBuf, false);
                    break;
                }

                usePat = true;

                if (mode == MODE0_NONE) {
                    // Entire pattern is a category; leave parse loop
                    set(nested);
                    mode = MODE2_OUTBRACKET;
                    break;
                }

                switch (op) {
                case '-':
                    removeAll(nested);
                    break;
                case '&':
                    retainAll(nested);
                    break;
                case 0:
                    addAll(nested);
                    break;
                }

                op = 0;
                lastItem = LAST2_SET;

                continue;
            }

            if (mode == MODE0_NONE) {
                syntaxError(chars, "Missing '['");
            }

            // -------- Parse special (syntax) characters.  If the
            // current character is not special, or if it is escaped,
            // then fall through and handle it below.

            if (!literal) {
                switch (c) {
                case ']':
                    if (lastItem == LAST1_RANGE) {
                        add_unchecked(lastChar, lastChar);
                        _appendToPat(patBuf, lastChar, false);
                    }
                    // Treat final trailing '-' as a literal
                    if (op == '-') {
                        add_unchecked(op, op);
                        patBuf.append(op);
                    } else if (op == '&') {
                        syntaxError(chars, "Trailing '&'");
                    }
                    patBuf.append(']');
                    mode = MODE2_OUTBRACKET;
                    continue;
                case '-':
                    if (op == 0) {
                        if (lastItem != LAST0_START) {
                            op = (char) c;
                            continue;
                        } else if (lastString != null) {
                            op = (char) c;
                            continue;
                        } else {
                            // Treat final trailing '-' as a literal
                            add_unchecked(c, c);
                            c = chars.next(opts);
                            literal = chars.isEscaped();
                            if (c == ']' && !literal) {
                                patBuf.append("-]");
                                mode = MODE2_OUTBRACKET;
                                continue;
                            }
                        }
                    }
                    syntaxError(chars, "'-' not after char, string, or set");
                    break;
                case '&':
                    if (lastItem == LAST2_SET && op == 0) {
                        op = (char) c;
                        continue;
                    }
                    syntaxError(chars, "'&' not after set");
                    break;
                case '^':
                    syntaxError(chars, "'^' not after '['");
                    break;
                case '{':
                    if (op != 0 && op != '-') {
                        syntaxError(chars, "Missing operand after operator");
                    }
                    if (lastItem == LAST1_RANGE) {
                        add_unchecked(lastChar, lastChar);
                        _appendToPat(patBuf, lastChar, false);
                    }
                    lastItem = LAST0_START;
                    if (buf == null) {
                        buf = new StringBuilder();
                    } else {
                        buf.setLength(0);
                    }
                    boolean ok = false;
                    while (!chars.atEnd()) {
                        c = chars.next(opts);
                        literal = chars.isEscaped();
                        if (c == '}' && !literal) {
                            ok = true;
                            break;
                        }
                        appendCodePoint(buf, c);
                    }
                    if (!ok) {
                        syntaxError(chars, "Invalid multicharacter string");
                    }
                    // We have new string. Add it to set and continue;
                    // we don't need to drop through to the further
                    // processing
                    String curString = buf.toString();
                    if (op == '-') {
                        int lastSingle = CharSequences.getSingleCodePoint(lastString == null ? "" : lastString);
                        int curSingle = CharSequences.getSingleCodePoint(curString);
                        if (lastSingle != Integer.MAX_VALUE && curSingle != Integer.MAX_VALUE) {
                            add(lastSingle,curSingle);
                        } else {
                            if (strings == EMPTY_STRINGS) {
                                strings = new TreeSet<>();
                            }
                            try {
                                StringRange.expand(lastString, curString, true, strings);
                            } catch (Exception e) {
                                syntaxError(chars, e.getMessage());
                            }
                        }
                        lastString = null;
                        op = 0;
                    } else {
                        add(curString);
                        lastString = curString;
                    }
                    patBuf.append('{');
                    _appendToPat(patBuf, curString, false);
                    patBuf.append('}');
                    continue;
                case SymbolTable.SYMBOL_REF:
                    //         symbols  nosymbols
                    // [a-$]   error    error (ambiguous)
                    // [a$]    anchor   anchor
                    // [a-$x]  var "x"* literal '$'
                    // [a-$.]  error    literal '$'
                    // *We won't get here in the case of var "x"
                    backup = chars.getPos(backup);
                    c = chars.next(opts);
                    literal = chars.isEscaped();
                    boolean anchor = (c == ']' && !literal);
                    if (symbols == null && !anchor) {
                        c = SymbolTable.SYMBOL_REF;
                        chars.setPos(backup);
                        break; // literal '$'
                    }
                    if (anchor && op == 0) {
                        if (lastItem == LAST1_RANGE) {
                            add_unchecked(lastChar, lastChar);
                            _appendToPat(patBuf, lastChar, false);
                        }
                        add_unchecked(UnicodeMatcher.ETHER);
                        usePat = true;
                        patBuf.append(SymbolTable.SYMBOL_REF).append(']');
                        mode = MODE2_OUTBRACKET;
                        continue;
                    }
                    syntaxError(chars, "Unquoted '$'");
                    break;
                default:
                    break;
                }
            }

            // -------- Parse literal characters.  This includes both
            // escaped chars ("\u4E01") and non-syntax characters
            // ("a").

            switch (lastItem) {
            case LAST0_START:
                if (op == '-' && lastString != null) {
                    syntaxError(chars, "Invalid range");
                }
                lastItem = LAST1_RANGE;
                lastChar = c;
                lastString = null;
                break;
            case LAST1_RANGE:
                if (op == '-') {
                    if (lastString != null) {
                        syntaxError(chars, "Invalid range");
                    }
                    if (lastChar >= c) {
                        // Don't allow redundant (a-a) or empty (b-a) ranges;
                        // these are most likely typos.
                        syntaxError(chars, "Invalid range");
                    }
                    add_unchecked(lastChar, c);
                    _appendToPat(patBuf, lastChar, false);
                    patBuf.append(op);
                    _appendToPat(patBuf, c, false);
                    lastItem = LAST0_START;
                    op = 0;
                } else {
                    add_unchecked(lastChar, lastChar);
                    _appendToPat(patBuf, lastChar, false);
                    lastChar = c;
                }
                break;
            case LAST2_SET:
                if (op != 0) {
                    syntaxError(chars, "Set expected after operator");
                }
                lastChar = c;
                lastItem = LAST1_RANGE;
                break;
            }
        }

        if (mode != MODE2_OUTBRACKET) {
            syntaxError(chars, "Missing ']'");
        }

        chars.skipIgnored(opts);

        /**
         * Handle global flags (invert, case insensitivity).  If this
         * pattern should be compiled case-insensitive, then we need
         * to close over case BEFORE COMPLEMENTING.  This makes
         * patterns like /[^abc]/i work.
         */
        if ((options & CASE_MASK) != 0) {
            closeOver(options);
        }
        if (invert) {
            complement().removeAllStrings();  // code point complement
        }

        // Use the rebuilt pattern (pat) only if necessary.  Prefer the
        // generated pattern.
        if (usePat) {
            append(rebuiltPat, patBuf.toString());
        } else {
            appendNewPattern(rebuiltPat, false, true);
        }
    }

    private static void syntaxError(RuleCharacterIterator chars, String msg) {
        throw new IllegalArgumentException("Error: " + msg + " at \"" +
                Utility.escape(chars.toString()) +
                '"');
    }

    /**
     * Add the contents of the UnicodeSet (as strings) into a collection.
     * @param target collection to add into
     * @stable ICU 4.4
     */
    public <T extends Collection<String>> T addAllTo(T target) {
        return addAllTo(this, target);
    }


    /**
     * Add the contents of the UnicodeSet (as strings) into a collection.
     * @param target collection to add into
     * @stable ICU 4.4
     */
    public String[] addAllTo(String[] target) {
        return addAllTo(this, target);
    }

    /**
     * Add the contents of the UnicodeSet (as strings) into an array.
     * @stable ICU 4.4
     */
    public static String[] toArray(UnicodeSet set) {
        return addAllTo(set, new String[set.size()]);
    }

    /**
     * Add the contents of the collection (as strings) into this UnicodeSet.
     * The collection must not contain null.
     * @param source the collection to add
     * @return a reference to this object
     * @stable ICU 4.4
     */
    public UnicodeSet add(Iterable<?> source) {
        return addAll(source);
    }

    /**
     * Add a collection (as strings) into this UnicodeSet.
     * Uses standard naming convention.
     * @param source collection to add into
     * @return a reference to this object
     * @stable ICU 4.4
     */
    public UnicodeSet addAll(Iterable<?> source) {
        checkFrozen();
        for (Object o : source) {
            add(o.toString());
        }
        return this;
    }

    //----------------------------------------------------------------
    // Implementation: Utility methods
    //----------------------------------------------------------------

    private int nextCapacity(int minCapacity) {
        // Grow exponentially to reduce the frequency of allocations.
        if (minCapacity < INITIAL_CAPACITY) {
            return minCapacity + INITIAL_CAPACITY;
        } else if (minCapacity <= 2500) {
            return 5 * minCapacity;
        } else {
            int newCapacity = 2 * minCapacity;
            if (newCapacity > MAX_LENGTH) {
                newCapacity = MAX_LENGTH;
            }
            return newCapacity;
        }
    }

    private void ensureCapacity(int newLen) {
        if (newLen > MAX_LENGTH) {
            newLen = MAX_LENGTH;
        }
        if (newLen <= list.length) return;
        int newCapacity = nextCapacity(newLen);
        int[] temp = new int[newCapacity];
        // Copy only the actual contents.
        System.arraycopy(list, 0, temp, 0, len);
        list = temp;
    }

    private void ensureBufferCapacity(int newLen) {
        if (newLen > MAX_LENGTH) {
            newLen = MAX_LENGTH;
        }
        if (buffer != null && newLen <= buffer.length) return;
        int newCapacity = nextCapacity(newLen);
        buffer = new int[newCapacity];
        // The buffer has no contents to be copied.
        // It is always filled from scratch after this call.
    }

    /**
     * Assumes start <= end.
     */
    private int[] range(int start, int end) {
        if (rangeList == null) {
            rangeList = new int[] { start, end+1, HIGH };
        } else {
            rangeList[0] = start;
            rangeList[1] = end+1;
        }
        return rangeList;
    }

    //----------------------------------------------------------------
    // Implementation: Fundamental operations
    //----------------------------------------------------------------

    // polarity = 0, 3 is normal: x xor y
    // polarity = 1, 2: x xor ~y == x === y

    private UnicodeSet xor(int[] other, int otherLen, int polarity) {
        ensureBufferCapacity(len + otherLen);
        int i = 0, j = 0, k = 0;
        int a = list[i++];
        int b;
        // TODO: Based on the call hierarchy, polarity of 1 or 2 is never used
        //      so the following if statement will not be called.
        ///CLOVER:OFF
        if (polarity == 1 || polarity == 2) {
            b = LOW;
            if (other[j] == LOW) { // skip base if already LOW
                ++j;
                b = other[j];
            }
            ///CLOVER:ON
        } else {
            b = other[j++];
        }
        // simplest of all the routines
        // sort the values, discarding identicals!
        while (true) {
            if (a < b) {
                buffer[k++] = a;
                a = list[i++];
            } else if (b < a) {
                buffer[k++] = b;
                b = other[j++];
            } else if (a != HIGH) { // at this point, a == b
                // discard both values!
                a = list[i++];
                b = other[j++];
            } else { // DONE!
                buffer[k++] = HIGH;
                len = k;
                break;
            }
        }
        // swap list and buffer
        int[] temp = list;
        list = buffer;
        buffer = temp;
        pat = null;
        return this;
    }

    // polarity = 0 is normal: x union y
    // polarity = 2: x union ~y
    // polarity = 1: ~x union y
    // polarity = 3: ~x union ~y

    private UnicodeSet add(int[] other, int otherLen, int polarity) {
        ensureBufferCapacity(len + otherLen);
        int i = 0, j = 0, k = 0;
        int a = list[i++];
        int b = other[j++];
        // change from xor is that we have to check overlapping pairs
        // polarity bit 1 means a is second, bit 2 means b is.
        main:
            while (true) {
                switch (polarity) {
                case 0: // both first; take lower if unequal
                    if (a < b) { // take a
                        // Back up over overlapping ranges in buffer[]
                        if (k > 0 && a <= buffer[k-1]) {
                            // Pick latter end value in buffer[] vs. list[]
                            a = max(list[i], buffer[--k]);
                        } else {
                            // No overlap
                            buffer[k++] = a;
                            a = list[i];
                        }
                        i++; // Common if/else code factored out
                        polarity ^= 1;
                    } else if (b < a) { // take b
                        if (k > 0 && b <= buffer[k-1]) {
                            b = max(other[j], buffer[--k]);
                        } else {
                            buffer[k++] = b;
                            b = other[j];
                        }
                        j++;
                        polarity ^= 2;
                    } else { // a == b, take a, drop b
                        if (a == HIGH) break main;
                        // This is symmetrical; it doesn't matter if
                        // we backtrack with a or b. - liu
                        if (k > 0 && a <= buffer[k-1]) {
                            a = max(list[i], buffer[--k]);
                        } else {
                            // No overlap
                            buffer[k++] = a;
                            a = list[i];
                        }
                        i++;
                        polarity ^= 1;
                        b = other[j++]; polarity ^= 2;
                    }
                    break;
                case 3: // both second; take higher if unequal, and drop other
                    if (b <= a) { // take a
                        if (a == HIGH) break main;
                        buffer[k++] = a;
                    } else { // take b
                        if (b == HIGH) break main;
                        buffer[k++] = b;
                    }
                    a = list[i++]; polarity ^= 1;   // factored common code
                    b = other[j++]; polarity ^= 2;
                    break;
                case 1: // a second, b first; if b < a, overlap
                    if (a < b) { // no overlap, take a
                        buffer[k++] = a; a = list[i++]; polarity ^= 1;
                    } else if (b < a) { // OVERLAP, drop b
                        b = other[j++]; polarity ^= 2;
                    } else { // a == b, drop both!
                        if (a == HIGH) break main;
                        a = list[i++]; polarity ^= 1;
                        b = other[j++]; polarity ^= 2;
                    }
                    break;
                case 2: // a first, b second; if a < b, overlap
                    if (b < a) { // no overlap, take b
                        buffer[k++] = b; b = other[j++]; polarity ^= 2;
                    } else  if (a < b) { // OVERLAP, drop a
                        a = list[i++]; polarity ^= 1;
                    } else { // a == b, drop both!
                        if (a == HIGH) break main;
                        a = list[i++]; polarity ^= 1;
                        b = other[j++]; polarity ^= 2;
                    }
                    break;
                }
            }
        buffer[k++] = HIGH;    // terminate
        len = k;
        // swap list and buffer
        int[] temp = list;
        list = buffer;
        buffer = temp;
        pat = null;
        return this;
    }

    // polarity = 0 is normal: x intersect y
    // polarity = 2: x intersect ~y == set-minus
    // polarity = 1: ~x intersect y
    // polarity = 3: ~x intersect ~y

    private UnicodeSet retain(int[] other, int otherLen, int polarity) {
        ensureBufferCapacity(len + otherLen);
        int i = 0, j = 0, k = 0;
        int a = list[i++];
        int b = other[j++];
        // change from xor is that we have to check overlapping pairs
        // polarity bit 1 means a is second, bit 2 means b is.
        main:
            while (true) {
                switch (polarity) {
                case 0: // both first; drop the smaller
                    if (a < b) { // drop a
                        a = list[i++]; polarity ^= 1;
                    } else if (b < a) { // drop b
                        b = other[j++]; polarity ^= 2;
                    } else { // a == b, take one, drop other
                        if (a == HIGH) break main;
                        buffer[k++] = a; a = list[i++]; polarity ^= 1;
                        b = other[j++]; polarity ^= 2;
                    }
                    break;
                case 3: // both second; take lower if unequal
                    if (a < b) { // take a
                        buffer[k++] = a; a = list[i++]; polarity ^= 1;
                    } else if (b < a) { // take b
                        buffer[k++] = b; b = other[j++]; polarity ^= 2;
                    } else { // a == b, take one, drop other
                        if (a == HIGH) break main;
                        buffer[k++] = a; a = list[i++]; polarity ^= 1;
                        b = other[j++]; polarity ^= 2;
                    }
                    break;
                case 1: // a second, b first;
                    if (a < b) { // NO OVERLAP, drop a
                        a = list[i++]; polarity ^= 1;
                    } else if (b < a) { // OVERLAP, take b
                        buffer[k++] = b; b = other[j++]; polarity ^= 2;
                    } else { // a == b, drop both!
                        if (a == HIGH) break main;
                        a = list[i++]; polarity ^= 1;
                        b = other[j++]; polarity ^= 2;
                    }
                    break;
                case 2: // a first, b second; if a < b, overlap
                    if (b < a) { // no overlap, drop b
                        b = other[j++]; polarity ^= 2;
                    } else  if (a < b) { // OVERLAP, take a
                        buffer[k++] = a; a = list[i++]; polarity ^= 1;
                    } else { // a == b, drop both!
                        if (a == HIGH) break main;
                        a = list[i++]; polarity ^= 1;
                        b = other[j++]; polarity ^= 2;
                    }
                    break;
                }
            }
        buffer[k++] = HIGH;    // terminate
        len = k;
        // swap list and buffer
        int[] temp = list;
        list = buffer;
        buffer = temp;
        pat = null;
        return this;
    }

    private static final int max(int a, int b) {
        return (a > b) ? a : b;
    }

    //----------------------------------------------------------------
    // Generic filter-based scanning code
    //----------------------------------------------------------------

    private static interface Filter {
        boolean contains(int codePoint);
    }

    private static final class NumericValueFilter implements Filter {
        double value;
        NumericValueFilter(double value) { this.value = value; }
        @Override
        public boolean contains(int ch) {
            return UCharacter.getUnicodeNumericValue(ch) == value;
        }
    }

    private static final class GeneralCategoryMaskFilter implements Filter {
        int mask;
        GeneralCategoryMaskFilter(int mask) { this.mask = mask; }
        @Override
        public boolean contains(int ch) {
            return ((1 << UCharacter.getType(ch)) & mask) != 0;
        }
    }

    private static final class IntPropertyFilter implements Filter {
        int prop;
        int value;
        IntPropertyFilter(int prop, int value) {
            this.prop = prop;
            this.value = value;
        }
        @Override
        public boolean contains(int ch) {
            return UCharacter.getIntPropertyValue(ch, prop) == value;
        }
    }

    private static final class ScriptExtensionsFilter implements Filter {
        int script;
        ScriptExtensionsFilter(int script) { this.script = script; }
        @Override
        public boolean contains(int c) {
            return UScript.hasScript(c, script);
        }
    }

    // VersionInfo for unassigned characters
    private static final VersionInfo NO_VERSION = VersionInfo.getInstance(0, 0, 0, 0);

    private static final class VersionFilter implements Filter {
        VersionInfo version;
        VersionFilter(VersionInfo version) { this.version = version; }
        @Override
        public boolean contains(int ch) {
            VersionInfo v = UCharacter.getAge(ch);
            // Reference comparison ok; VersionInfo caches and reuses
            // unique objects.
            return !Utility.sameObjects(v, NO_VERSION) &&
                    v.compareTo(version) <= 0;
        }
    }

    /**
     * Generic filter-based scanning code for UCD property UnicodeSets.
     */
    private void applyFilter(Filter filter, UnicodeSet inclusions) {
        // Logically, walk through all Unicode characters, noting the start
        // and end of each range for which filter.contain(c) is
        // true.  Add each range to a set.
        //
        // To improve performance, use an inclusions set which
        // encodes information about character ranges that are known
        // to have identical properties.
        // inclusions contains the first characters of
        // same-value ranges for the given property.

        clear();

        int startHasProperty = -1;
        int limitRange = inclusions.getRangeCount();

        for (int j=0; j<limitRange; ++j) {
            // get current range
            int start = inclusions.getRangeStart(j);
            int end = inclusions.getRangeEnd(j);

            // for all the code points in the range, process
            for (int ch = start; ch <= end; ++ch) {
                // only add to the unicodeset on inflection points --
                // where the hasProperty value changes to false
                if (filter.contains(ch)) {
                    if (startHasProperty < 0) {
                        startHasProperty = ch;
                    }
                } else if (startHasProperty >= 0) {
                    add_unchecked(startHasProperty, ch-1);
                    startHasProperty = -1;
                }
            }
        }
        if (startHasProperty >= 0) {
            add_unchecked(startHasProperty, 0x10FFFF);
        }
    }

    /**
     * Remove leading and trailing Pattern_White_Space and compress
     * internal Pattern_White_Space to a single space character.
     */
    private static String mungeCharName(String source) {
        source = PatternProps.trimWhiteSpace(source);
        StringBuilder buf = null;
        for (int i=0; i<source.length(); ++i) {
            char ch = source.charAt(i);
            if (PatternProps.isWhiteSpace(ch)) {
                if (buf == null) {
                    buf = new StringBuilder().append(source, 0, i);
                } else if (buf.charAt(buf.length() - 1) == ' ') {
                    continue;
                }
                ch = ' '; // convert to ' '
            }
            if (buf != null) {
                buf.append(ch);
            }
        }
        return buf == null ? source : buf.toString();
    }

    //----------------------------------------------------------------
    // Property set API
    //----------------------------------------------------------------

    /**
     * Modifies this set to contain those code points which have the
     * given value for the given binary or enumerated property, as
     * returned by UCharacter.getIntPropertyValue.  Prior contents of
     * this set are lost.
     *
     * @param prop a property in the range
     * UProperty.BIN_START..UProperty.BIN_LIMIT-1 or
     * UProperty.INT_START..UProperty.INT_LIMIT-1 or.
     * UProperty.MASK_START..UProperty.MASK_LIMIT-1.
     *
     * @param value a value in the range
     * UCharacter.getIntPropertyMinValue(prop)..
     * UCharacter.getIntPropertyMaxValue(prop), with one exception.
     * If prop is UProperty.GENERAL_CATEGORY_MASK, then value should not be
     * a UCharacter.getType() result, but rather a mask value produced
     * by logically ORing (1 &lt;&lt; UCharacter.getType()) values together.
     * This allows grouped categories such as [:L:] to be represented.
     *
     * @return a reference to this set
     *
     * @stable ICU 2.4
     */
    public UnicodeSet applyIntPropertyValue(int prop, int value) {
        // All of the following include checkFrozen() before modifying this set.
        if (prop == UProperty.GENERAL_CATEGORY_MASK) {
            UnicodeSet inclusions = CharacterPropertiesImpl.getInclusionsForProperty(prop);
            applyFilter(new GeneralCategoryMaskFilter(value), inclusions);
        } else if (prop == UProperty.SCRIPT_EXTENSIONS) {
            UnicodeSet inclusions = CharacterPropertiesImpl.getInclusionsForProperty(prop);
            applyFilter(new ScriptExtensionsFilter(value), inclusions);
        } else if (0 <= prop && prop < UProperty.BINARY_LIMIT) {
            if (value == 0 || value == 1) {
                set(CharacterProperties.getBinaryPropertySet(prop));
                if (value == 0) {
                    complement().removeAllStrings();  // code point complement
                }
            } else {
                clear();
            }
        } else if (UProperty.INT_START <= prop && prop < UProperty.INT_LIMIT) {
            UnicodeSet inclusions = CharacterPropertiesImpl.getInclusionsForProperty(prop);
            applyFilter(new IntPropertyFilter(prop, value), inclusions);
        } else {
            throw new IllegalArgumentException("unsupported property " + prop);
        }
        return this;
    }



    /**
     * Modifies this set to contain those code points which have the
     * given value for the given property.  Prior contents of this
     * set are lost.
     *
     * @param propertyAlias a property alias, either short or long.
     * The name is matched loosely.  See PropertyAliases.txt for names
     * and a description of loose matching.  If the value string is
     * empty, then this string is interpreted as either a
     * General_Category value alias, a Script value alias, a binary
     * property alias, or a special ID.  Special IDs are matched
     * loosely and correspond to the following sets:
     *
     * "ANY" = [\\u0000-\\U0010FFFF],
     * "ASCII" = [\\u0000-\\u007F].
     *
     * @param valueAlias a value alias, either short or long.  The
     * name is matched loosely.  See PropertyValueAliases.txt for
     * names and a description of loose matching.  In addition to
     * aliases listed, numeric values and canonical combining classes
     * may be expressed numerically, e.g., ("nv", "0.5") or ("ccc",
     * "220").  The value string may also be empty.
     *
     * @return a reference to this set
     *
     * @stable ICU 2.4
     */
    public UnicodeSet applyPropertyAlias(String propertyAlias, String valueAlias) {
        return applyPropertyAlias(propertyAlias, valueAlias, null);
    }

    /**
     * Modifies this set to contain those code points which have the
     * given value for the given property.  Prior contents of this
     * set are lost.
     * @param propertyAlias A string of the property alias.
     * @param valueAlias A string of the value alias.
     * @param symbols if not null, then symbols are first called to see if a property
     * is available. If true, then everything else is skipped.
     * @return this set
     * @stable ICU 3.2
     */
    public UnicodeSet applyPropertyAlias(String propertyAlias,
            String valueAlias, SymbolTable symbols) {
        checkFrozen();
        int p;
        int v;
        boolean invert = false;

        if (symbols != null
                && (symbols instanceof XSymbolTable)
                && ((XSymbolTable)symbols).applyPropertyAlias(propertyAlias, valueAlias, this)) {
            return this;
        }

        if (XSYMBOL_TABLE != null) {
            if (XSYMBOL_TABLE.applyPropertyAlias(propertyAlias, valueAlias, this)) {
                return this;
            }
        }

        if (valueAlias.length() > 0) {
            p = UCharacter.getPropertyEnum(propertyAlias);

            // Treat gc as gcm
            if (p == UProperty.GENERAL_CATEGORY) {
                p = UProperty.GENERAL_CATEGORY_MASK;
            }

            if ((p >= UProperty.BINARY_START && p < UProperty.BINARY_LIMIT) ||
                    (p >= UProperty.INT_START && p < UProperty.INT_LIMIT) ||
                    (p >= UProperty.MASK_START && p < UProperty.MASK_LIMIT)) {
                try {
                    v = UCharacter.getPropertyValueEnum(p, valueAlias);
                } catch (IllegalArgumentException e) {
                    // Handle numeric CCC
                    if (p == UProperty.CANONICAL_COMBINING_CLASS ||
                            p == UProperty.LEAD_CANONICAL_COMBINING_CLASS ||
                            p == UProperty.TRAIL_CANONICAL_COMBINING_CLASS) {
                        v = Integer.parseInt(PatternProps.trimWhiteSpace(valueAlias));
                        // Anything between 0 and 255 is valid even if unused.
                        if (v < 0 || v > 255) throw e;
                    } else {
                        throw e;
                    }
                }
            }

            else {
                switch (p) {
                case UProperty.NUMERIC_VALUE:
                {
                    double value = Double.parseDouble(PatternProps.trimWhiteSpace(valueAlias));
                    applyFilter(new NumericValueFilter(value),
                            CharacterPropertiesImpl.getInclusionsForProperty(p));
                    return this;
                }
                case UProperty.NAME:
                {
                    // Must munge name, since
                    // UCharacter.charFromName() does not do
                    // 'loose' matching.
                    String buf = mungeCharName(valueAlias);
                    int ch = UCharacter.getCharFromExtendedName(buf);
                    if (ch == -1) {
                        throw new IllegalArgumentException("Invalid character name");
                    }
                    clear();
                    add_unchecked(ch);
                    return this;
                }
                case UProperty.UNICODE_1_NAME:
                    // ICU 49 deprecates the Unicode_1_Name property APIs.
                    throw new IllegalArgumentException("Unicode_1_Name (na1) not supported");
                case UProperty.AGE:
                {
                    // Must munge name, since
                    // VersionInfo.getInstance() does not do
                    // 'loose' matching.
                    VersionInfo version = VersionInfo.getInstance(mungeCharName(valueAlias));
                    applyFilter(new VersionFilter(version),
                            CharacterPropertiesImpl.getInclusionsForProperty(p));
                    return this;
                }
                case UProperty.SCRIPT_EXTENSIONS:
                    v = UCharacter.getPropertyValueEnum(UProperty.SCRIPT, valueAlias);
                    // fall through to calling applyIntPropertyValue()
                    break;
                default:
                    // p is a non-binary, non-enumerated property that we
                    // don't support (yet).
                    throw new IllegalArgumentException("Unsupported property");
                }
            }
        }

        else {
            // valueAlias is empty.  Interpret as General Category, Script,
            // Binary property, or ANY or ASCII.  Upon success, p and v will
            // be set.
            UPropertyAliases pnames = UPropertyAliases.INSTANCE;
            p = UProperty.GENERAL_CATEGORY_MASK;
            v = pnames.getPropertyValueEnum(p, propertyAlias);
            if (v == UProperty.UNDEFINED) {
                p = UProperty.SCRIPT;
                v = pnames.getPropertyValueEnum(p, propertyAlias);
                if (v == UProperty.UNDEFINED) {
                    p = pnames.getPropertyEnum(propertyAlias);
                    if (p == UProperty.UNDEFINED) {
                        p = -1;
                    }
                    if (p >= UProperty.BINARY_START && p < UProperty.BINARY_LIMIT) {
                        v = 1;
                    } else if (p == -1) {
                        if (0 == UPropertyAliases.compare(ANY_ID, propertyAlias)) {
                            set(MIN_VALUE, MAX_VALUE);
                            return this;
                        } else if (0 == UPropertyAliases.compare(ASCII_ID, propertyAlias)) {
                            set(0, 0x7F);
                            return this;
                        } else if (0 == UPropertyAliases.compare(ASSIGNED, propertyAlias)) {
                            // [:Assigned:]=[:^Cn:]
                            p = UProperty.GENERAL_CATEGORY_MASK;
                            v = (1<<UCharacter.UNASSIGNED);
                            invert = true;
                        } else {
                            // Property name was never matched.
                            throw new IllegalArgumentException("Invalid property alias: " + propertyAlias + "=" + valueAlias);
                        }
                    } else {
                        // Valid property name, but it isn't binary, so the value
                        // must be supplied.
                        throw new IllegalArgumentException("Missing property value");
                    }
                }
            }
        }

        applyIntPropertyValue(p, v);
        if(invert) {
            complement().removeAllStrings();  // code point complement
        }

        return this;
    }

    //----------------------------------------------------------------
    // Property set patterns
    //----------------------------------------------------------------

    /**
     * Return true if the given position, in the given pattern, appears
     * to be the start of a property set pattern.
     */
    private static boolean resemblesPropertyPattern(String pattern, int pos) {
        // Patterns are at least 5 characters long
        if ((pos+5) > pattern.length()) {
            return false;
        }

        // Look for an opening [:, [:^, \p, or \P
        return pattern.regionMatches(pos, "[:", 0, 2) ||
                pattern.regionMatches(true, pos, "\\p", 0, 2) ||
                pattern.regionMatches(pos, "\\N", 0, 2);
    }

    /**
     * Return true if the given iterator appears to point at a
     * property pattern.  Regardless of the result, return with the
     * iterator unchanged.
     * @param chars iterator over the pattern characters.  Upon return
     * it will be unchanged.
     * @param iterOpts RuleCharacterIterator options
     */
    private static boolean resemblesPropertyPattern(RuleCharacterIterator chars,
            int iterOpts) {
        boolean result = false;
        iterOpts &= ~RuleCharacterIterator.PARSE_ESCAPES;
        RuleCharacterIterator.Position pos = chars.getPos(null);
        int c = chars.next(iterOpts);
        if (c == '[' || c == '\\') {
            int d = chars.next(iterOpts & ~RuleCharacterIterator.SKIP_WHITESPACE);
            result = (c == '[') ? (d == ':') :
                (d == 'N' || d == 'p' || d == 'P');
        }
        chars.setPos(pos);
        return result;
    }

    /**
     * Parse the given property pattern at the given parse position.
     * @param symbols TODO
     */
    private UnicodeSet applyPropertyPattern(String pattern, ParsePosition ppos, SymbolTable symbols) {
        int pos = ppos.getIndex();

        // On entry, ppos should point to one of the following locations:

        // Minimum length is 5 characters, e.g. \p{L}
        if ((pos+5) > pattern.length()) {
            return null;
        }

        boolean posix = false; // true for [:pat:], false for \p{pat} \P{pat} \N{pat}
        boolean isName = false; // true for \N{pat}, o/w false
        boolean invert = false;

        // Look for an opening [:, [:^, \p, or \P
        if (pattern.regionMatches(pos, "[:", 0, 2)) {
            posix = true;
            pos = PatternProps.skipWhiteSpace(pattern, (pos+2));
            if (pos < pattern.length() && pattern.charAt(pos) == '^') {
                ++pos;
                invert = true;
            }
        } else if (pattern.regionMatches(true, pos, "\\p", 0, 2) ||
                pattern.regionMatches(pos, "\\N", 0, 2)) {
            char c = pattern.charAt(pos+1);
            invert = (c == 'P');
            isName = (c == 'N');
            pos = PatternProps.skipWhiteSpace(pattern, (pos+2));
            if (pos == pattern.length() || pattern.charAt(pos++) != '{') {
                // Syntax error; "\p" or "\P" not followed by "{"
                return null;
            }
        } else {
            // Open delimiter not seen
            return null;
        }

        // Look for the matching close delimiter, either :] or }
        int close = pattern.indexOf(posix ? ":]" : "}", pos);
        if (close < 0) {
            // Syntax error; close delimiter missing
            return null;
        }

        // Look for an '=' sign.  If this is present, we will parse a
        // medium \p{gc=Cf} or long \p{GeneralCategory=Format}
        // pattern.
        int equals = pattern.indexOf('=', pos);
        String propName, valueName;
        if (equals >= 0 && equals < close && !isName) {
            // Equals seen; parse medium/long pattern
            propName = pattern.substring(pos, equals);
            valueName = pattern.substring(equals+1, close);
        }

        else {
            // Handle case where no '=' is seen, and \N{}
            propName = pattern.substring(pos, close);
            valueName = "";

            // Handle \N{name}
            if (isName) {
                // This is a little inefficient since it means we have to
                // parse "na" back to UProperty.NAME even though we already
                // know it's UProperty.NAME.  If we refactor the API to
                // support args of (int, String) then we can remove
                // "na" and make this a little more efficient.
                valueName = propName;
                propName = "na";
            }
        }

        applyPropertyAlias(propName, valueName, symbols);

        if (invert) {
            complement().removeAllStrings();  // code point complement
        }

        // Move to the limit position after the close delimiter
        ppos.setIndex(close + (posix ? 2 : 1));

        return this;
    }

    /**
     * Parse a property pattern.
     * @param chars iterator over the pattern characters.  Upon return
     * it will be advanced to the first character after the parsed
     * pattern, or the end of the iteration if all characters are
     * parsed.
     * @param rebuiltPat the pattern that was parsed, rebuilt or
     * copied from the input pattern, as appropriate.
     * @param symbols TODO
     */
    private void applyPropertyPattern(RuleCharacterIterator chars,
            Appendable rebuiltPat, SymbolTable symbols) {
        String patStr = chars.getCurrentBuffer();
        int start = chars.getCurrentBufferPos();
        ParsePosition pos = new ParsePosition(start);
        applyPropertyPattern(patStr, pos, symbols);
        int length = pos.getIndex() - start;
        if (length == 0) {
            syntaxError(chars, "Invalid property pattern");
        }
        chars.jumpahead(length);
        append(rebuiltPat, patStr.substring(start, pos.getIndex()));
    }

    //----------------------------------------------------------------
    // Case folding API
    //----------------------------------------------------------------

    /**
     * Bitmask for constructor and applyPattern() indicating that
     * white space should be ignored.  If set, ignore Unicode Pattern_White_Space characters,
     * unless they are quoted or escaped.  This may be ORed together
     * with other selectors.
     * @stable ICU 3.8
     */
    public static final int IGNORE_SPACE = 1;

    /**
     * Alias for {@link #CASE_INSENSITIVE}.
     *
     * @deprecated ICU 73 Use {@link #CASE_INSENSITIVE} instead.
     */
    @Deprecated
    public static final int CASE = 2;

    /**
     * Enable case insensitive matching.  E.g., "[ab]" with this flag
     * will match 'a', 'A', 'b', and 'B'.  "[^ab]" with this flag will
     * match all except 'a', 'A', 'b', and 'B'. This performs a full
     * closure over case mappings, e.g. '\u017f' (U+017F long s) for 's'.
     *
     * <p>This value is an options bit set value for some
     * constructors, applyPattern(), and closeOver().
     * It can be ORed together with other, unrelated options.
     *
     * <p>The resulting set is a superset of the input for the code points but
     * not for the strings.
     * It performs a case mapping closure of the code points and adds
     * full case folding strings for the code points, and reduces strings of
     * the original set to their full case folding equivalents.
     *
     * <p>This is designed for case-insensitive matches, for example
     * in regular expressions. The full code point case closure allows checking of
     * an input character directly against the closure set.
     * Strings are matched by comparing the case-folded form from the closure
     * set with an incremental case folding of the string in question.
     *
     * <p>The closure set will also contain single code points if the original
     * set contained case-equivalent strings (like U+00DF for "ss" or "Ss" etc.).
     * This is not necessary (that is, redundant) for the above matching method
     * but results in the same closure sets regardless of whether the original
     * set contained the code point or a string.
     *
     * @stable ICU 3.4
     */
    public static final int CASE_INSENSITIVE = 2;

    /**
     * Adds all case mappings for each element in the set.
     * This adds the full lower-, title-, and uppercase mappings as well as the full case folding
     * of each existing element in the set.
     *
     * <p>This value is an options bit set value for some
     * constructors, applyPattern(), and closeOver().
     * It can be ORed together with other, unrelated options.
     *
     * <p>Unlike the "case insensitive" options, this does not perform a closure.
     * For example, it does not add '\u017f' (U+017F long s) for 's',
     * 'K' (U+212A Kelvin sign) for 'k', or replace set strings by their case-folded versions.
     *
     * @stable ICU 3.4
     */
    public static final int ADD_CASE_MAPPINGS = 4;

    /**
     * Enable case insensitive matching.
     * Same as {@link #CASE_INSENSITIVE} but using only Simple_Case_Folding (scf) mappings,
     * which map each code point to one code point,
     * not full Case_Folding (cf) mappings, which map some code points to multiple code points.
     *
     * <p>This is designed for case-insensitive matches, for example in certain
     * regular expression implementations where only Simple_Case_Folding mappings are used,
     * such as in ECMAScript (JavaScript) regular expressions.
     *
     * <p>This value is an options bit set value for some
     * constructors, applyPattern(), and closeOver().
     * It can be ORed together with other, unrelated options.
     *
     * @draft ICU 73
     */
    public static final int SIMPLE_CASE_INSENSITIVE = 6;

    private static final int CASE_MASK = CASE_INSENSITIVE | ADD_CASE_MAPPINGS;

    //  add the result of a full case mapping to the set
    //  use str as a temporary string to avoid constructing one
    private static final void addCaseMapping(UnicodeSet set, int result, StringBuilder full) {
        if(result >= 0) {
            if(result > UCaseProps.MAX_STRING_LENGTH) {
                // add a single-code point case mapping
                set.add(result);
            } else {
                // add a string case mapping from full with length result
                set.add(full.toString());
                full.setLength(0);
            }
        }
        // result < 0: the code point mapped to itself, no need to add it
        // see UCaseProps
    }

    /** For case closure on a large set, look only at code points with relevant properties. */
    UnicodeSet maybeOnlyCaseSensitive(UnicodeSet src) {
        if (src.size() < 30) {
            return src;
        }
        // Return the intersection of the src code points with Case_Sensitive ones.
        UnicodeSet sensitive = CharacterProperties.getBinaryPropertySet(UProperty.CASE_SENSITIVE);
        // Start by cloning the "smaller" set. Try not to copy the strings, if there are any in src.
        if (src.hasStrings() || src.getRangeCount() > sensitive.getRangeCount()) {
            return sensitive.cloneAsThawed().retainAll(src);
        } else {
            return ((UnicodeSet) src.clone()).retainAll(sensitive);
        }
    }

    // Per-character scf = Simple_Case_Folding of a string.
    // (Normally when we case-fold a string we use full case foldings.)
    private static final boolean scfString(CharSequence s, StringBuilder scf) {
        int length = s.length();
        // Loop while not needing modification.
        for (int i = 0; i < length;) {
            int c = Character.codePointAt(s, i);
            int scfChar = UCharacter.foldCase(c, UCharacter.FOLD_CASE_DEFAULT);
            if (scfChar != c) {
                // Copy the characters before c.
                scf.setLength(0);
                scf.append(s, 0, i);
                // Loop over the rest of the string and keep case-folding.
                for (;;) {
                    scf.appendCodePoint(scfChar);
                    i += Character.charCount(c);
                    if (i == length) {
                        return true;
                    }
                    c = Character.codePointAt(s, i);
                    scfChar = UCharacter.foldCase(c, UCharacter.FOLD_CASE_DEFAULT);
                }
            }
            i += Character.charCount(c);
        }
        return false;
    }

    /**
     * Close this set over the given attribute.  For the attribute
     * {@link #CASE_INSENSITIVE}, the result is to modify this set so that:
     *
     * <ol>
     * <li>For each character or string 'a' in this set, all strings
     * 'b' such that foldCase(a) == foldCase(b) are added to this set.
     * (For most 'a' that are single characters, 'b' will have
     * b.length() == 1.)
     *
     * <li>For each string 'e' in the resulting set, if e !=
     * foldCase(e), 'e' will be removed.
     * </ol>
     *
     * <p>Example: [aq\u00DF{Bc}{bC}{Fi}] =&gt; [aAqQ\u00DF\uFB01{ss}{bc}{fi}]
     *
     * <p>(Here foldCase(x) refers to the operation
     * UCharacter.foldCase(x, true), and a == b actually denotes
     * a.equals(b), not pointer comparison.)
     *
     * @param attribute bitmask for attributes to close over.
     * Valid options:
     * At most one of {@link #CASE_INSENSITIVE}, {@link #ADD_CASE_MAPPINGS},
     * {@link #SIMPLE_CASE_INSENSITIVE}. These case options are mutually exclusive.
     * Unrelated options bits are ignored.
     * @return a reference to this set.
     * @stable ICU 3.8
     */
    public UnicodeSet closeOver(int attribute) {
        checkFrozen();
        switch (attribute & CASE_MASK) {
        case 0:
            break;
        case CASE_INSENSITIVE:
            closeOverCaseInsensitive(/* simple= */ false);
            break;
        case ADD_CASE_MAPPINGS:
            closeOverAddCaseMappings();
            break;
        case SIMPLE_CASE_INSENSITIVE:
            closeOverCaseInsensitive(/* simple= */ true);
            break;
        default:
            // bad option (unreachable)
            break;
        }
        return this;
    }

    private void closeOverCaseInsensitive(boolean simple) {
        UCaseProps csp = UCaseProps.INSTANCE;
        // Start with input set to guarantee inclusion.
        UnicodeSet foldSet = new UnicodeSet(this);

        // Full case mappings closure:
        // Remove strings because the strings will actually be reduced (folded);
        // therefore, start with no strings and add only those needed.
        // Do this before processing code points, because they may add strings.
        if (!simple && foldSet.hasStrings()) {
            foldSet.strings.clear();
        }

        UnicodeSet codePoints = maybeOnlyCaseSensitive(this);

        // Iterate over the ranges of single code points. Nested loop for each code point.
        int n = codePoints.getRangeCount();
        for (int i=0; i<n; ++i) {
            int start = codePoints.getRangeStart(i);
            int end   = codePoints.getRangeEnd(i);

            if (simple) {
                for (int cp=start; cp<=end; ++cp) {
                    csp.addSimpleCaseClosure(cp, foldSet);
                }
            } else {
                for (int cp=start; cp<=end; ++cp) {
                    csp.addCaseClosure(cp, foldSet);
                }
            }
        }
        if (hasStrings()) {
            StringBuilder sb = simple ? new StringBuilder() : null;
            for (String s : strings) {
                if (simple) {
                    if (scfString(s, sb)) {
                        foldSet.remove(s).add(sb);
                    }
                } else {
                    String str = UCharacter.foldCase(s, 0);
                    if(!csp.addStringCaseClosure(str, foldSet)) {
                        foldSet.add(str); // does not map to code points: add the folded string itself
                    }
                }
            }
        }
        set(foldSet);
    }

    private void closeOverAddCaseMappings() {
        UCaseProps csp = UCaseProps.INSTANCE;
        // Start with input set to guarantee inclusion.
        UnicodeSet foldSet = new UnicodeSet(this);

        UnicodeSet codePoints = maybeOnlyCaseSensitive(this);

        // Iterate over the ranges of single code points. Nested loop for each code point.
        int n = codePoints.getRangeCount();
        int result;
        StringBuilder full = new StringBuilder();

        for (int i=0; i<n; ++i) {
            int start = codePoints.getRangeStart(i);
            int end   = codePoints.getRangeEnd(i);

            // add case mappings
            // (does not add long s for regular s, or Kelvin for k, for example)
            for (int cp=start; cp<=end; ++cp) {
                result = csp.toFullLower(cp, null, full, UCaseProps.LOC_ROOT);
                addCaseMapping(foldSet, result, full);

                result = csp.toFullTitle(cp, null, full, UCaseProps.LOC_ROOT);
                addCaseMapping(foldSet, result, full);

                result = csp.toFullUpper(cp, null, full, UCaseProps.LOC_ROOT);
                addCaseMapping(foldSet, result, full);

                result = csp.toFullFolding(cp, full, 0);
                addCaseMapping(foldSet, result, full);
            }
        }
        if (hasStrings()) {
            ULocale root = ULocale.ROOT;
            BreakIterator bi = BreakIterator.getWordInstance(root);
            for (String str : strings) {
                // TODO: call lower-level functions
                foldSet.add(UCharacter.toLowerCase(root, str));
                foldSet.add(UCharacter.toTitleCase(root, str, bi));
                foldSet.add(UCharacter.toUpperCase(root, str));
                foldSet.add(UCharacter.foldCase(str, 0));
            }
        }
        set(foldSet);
    }

    /**
     * Internal class for customizing UnicodeSet parsing of properties.
     * TODO: extend to allow customizing of codepoint ranges
     * @draft ICU3.8 (retain)
     * @author medavis
     */
    abstract public static class XSymbolTable implements SymbolTable {
        /**
         * Default constructor
         * @draft ICU3.8 (retain)
         */
        public XSymbolTable(){}
        /**
         * Supplies default implementation for SymbolTable (no action).
         * @draft ICU3.8 (retain)
         */
        @Override
        public UnicodeMatcher lookupMatcher(int i) {
            return null;
        }

        /**
         * Override the interpretation of the sequence [:propertyName=propertyValue:] (and its negated and Perl-style
         * variant). The propertyName and propertyValue may be existing Unicode aliases, or may not be.
         * <p>
         * This routine will be called whenever the parsing of a UnicodeSet pattern finds such a
         * propertyName+propertyValue combination.
         *
         * @param propertyName
         *            the name of the property
         * @param propertyValue
         *            the name of the property value
         * @param result UnicodeSet value to change
         *            a set to which the characters having the propertyName+propertyValue are to be added.
         * @return returns true if the propertyName+propertyValue combination is to be overridden, and the characters
         *         with that property have been added to the UnicodeSet, and returns false if the
         *         propertyName+propertyValue combination is not recognized (in which case result is unaltered).
         * @draft ICU3.8 (retain)
         */
        public boolean applyPropertyAlias(String propertyName, String propertyValue, UnicodeSet result) {
            return false;
        }
        /**
         * Supplies default implementation for SymbolTable (no action).
         * @draft ICU3.8 (retain)
         */
        @Override
        public char[] lookup(String s) {
            return null;
        }
        /**
         * Supplies default implementation for SymbolTable (no action).
         * @draft ICU3.8 (retain)
         */
        @Override
        public String parseReference(String text, ParsePosition pos, int limit) {
            return null;
        }
    }

    /**
     * Is this frozen, according to the Freezable interface?
     *
     * @return value
     * @stable ICU 3.8
     */
    @Override
    public boolean isFrozen() {
        return (bmpSet != null || stringSpan != null);
    }

    /**
     * Freeze this class, according to the Freezable interface.
     *
     * @return this
     * @stable ICU 4.4
     */
    @Override
    public UnicodeSet freeze() {
        if (!isFrozen()) {
            compact();

            // Optimize contains() and span() and similar functions.
            if (hasStrings()) {
                stringSpan = new UnicodeSetStringSpan(this, new ArrayList<>(strings), UnicodeSetStringSpan.ALL);
            }
            if (stringSpan == null || !stringSpan.needsStringSpanUTF16()) {
                // Optimize for code point spans.
                // There are no strings, or
                // all strings are irrelevant for span() etc. because
                // all of each string's code points are contained in this set.
                // However, fully contained strings are relevant for spanAndCount(),
                // so we create both objects.
                bmpSet = new BMPSet(list, len);
            }
        }
        return this;
    }

    /**
     * Span a string using this UnicodeSet.
     * <p>To replace, count elements, or delete spans, see {@link jdk.internal.icu.text.UnicodeSetSpanner UnicodeSetSpanner}.
     * @param s The string to be spanned
     * @param spanCondition The span condition
     * @return the length of the span
     * @stable ICU 4.4
     */
    public int span(CharSequence s, SpanCondition spanCondition) {
        return span(s, 0, spanCondition);
    }

    /**
     * Span a string using this UnicodeSet.
     *   If the start index is less than 0, span will start from 0.
     *   If the start index is greater than the string length, span returns the string length.
     * <p>To replace, count elements, or delete spans, see {@link jdk.internal.icu.text.UnicodeSetSpanner UnicodeSetSpanner}.
     * @param s The string to be spanned
     * @param start The start index that the span begins
     * @param spanCondition The span condition
     * @return the string index which ends the span (i.e. exclusive)
     * @stable ICU 4.4
     */
    public int span(CharSequence s, int start, SpanCondition spanCondition) {
        int end = s.length();
        if (start < 0) {
            start = 0;
        } else if (start >= end) {
            return end;
        }
        if (bmpSet != null) {
            // Frozen set without strings, or no string is relevant for span().
            return bmpSet.span(s, start, spanCondition, null);
        }
        if (stringSpan != null) {
            return stringSpan.span(s, start, spanCondition);
        } else if (hasStrings()) {
            int which = spanCondition == SpanCondition.NOT_CONTAINED ? UnicodeSetStringSpan.FWD_UTF16_NOT_CONTAINED
                    : UnicodeSetStringSpan.FWD_UTF16_CONTAINED;
            UnicodeSetStringSpan strSpan = new UnicodeSetStringSpan(this, new ArrayList<>(strings), which);
            if (strSpan.needsStringSpanUTF16()) {
                return strSpan.span(s, start, spanCondition);
            }
        }

        return spanCodePointsAndCount(s, start, spanCondition, null);
    }

    /**
     * Same as span() but also counts the smallest number of set elements on any path across the span.
     * <p>To replace, count elements, or delete spans, see {@link jdk.internal.icu.text.UnicodeSetSpanner UnicodeSetSpanner}.
     * @param outCount An output-only object (must not be null) for returning the count.
     * @return the limit (exclusive end) of the span
     * @internal
     * @deprecated This API is ICU internal only.
     */
    @Deprecated
    public int spanAndCount(CharSequence s, int start, SpanCondition spanCondition, OutputInt outCount) {
        if (outCount == null) {
            throw new IllegalArgumentException("outCount must not be null");
        }
        int end = s.length();
        if (start < 0) {
            start = 0;
        } else if (start >= end) {
            return end;
        }
        if (stringSpan != null) {
            // We might also have bmpSet != null,
            // but fully-contained strings are relevant for counting elements.
            return stringSpan.spanAndCount(s, start, spanCondition, outCount);
        } else if (bmpSet != null) {
            return bmpSet.span(s, start, spanCondition, outCount);
        } else if (hasStrings()) {
            int which = spanCondition == SpanCondition.NOT_CONTAINED ? UnicodeSetStringSpan.FWD_UTF16_NOT_CONTAINED
                    : UnicodeSetStringSpan.FWD_UTF16_CONTAINED;
            which |= UnicodeSetStringSpan.WITH_COUNT;
            UnicodeSetStringSpan strSpan = new UnicodeSetStringSpan(this, new ArrayList<>(strings), which);
            return strSpan.spanAndCount(s, start, spanCondition, outCount);
        }

        return spanCodePointsAndCount(s, start, spanCondition, outCount);
    }

    private int spanCodePointsAndCount(CharSequence s, int start,
            SpanCondition spanCondition, OutputInt outCount) {
        // Pin to 0/1 values.
        boolean spanContained = (spanCondition != SpanCondition.NOT_CONTAINED);

        int c;
        int next = start;
        int length = s.length();
        int count = 0;
        do {
            c = Character.codePointAt(s, next);
            if (spanContained != contains(c)) {
                break;
            }
            ++count;
            next += Character.charCount(c);
        } while (next < length);
        if (outCount != null) { outCount.value = count; }
        return next;
    }

    /**
     * Span a string backwards (from the end) using this UnicodeSet.
     * <p>To replace, count elements, or delete spans, see {@link jdk.internal.icu.text.UnicodeSetSpanner UnicodeSetSpanner}.
     * @param s The string to be spanned
     * @param spanCondition The span condition
     * @return The string index which starts the span (i.e. inclusive).
     * @stable ICU 4.4
     */
    public int spanBack(CharSequence s, SpanCondition spanCondition) {
        return spanBack(s, s.length(), spanCondition);
    }

    /**
     * Span a string backwards (from the fromIndex) using this UnicodeSet.
     * If the fromIndex is less than 0, spanBack will return 0.
     * If fromIndex is greater than the string length, spanBack will start from the string length.
     * <p>To replace, count elements, or delete spans, see {@link jdk.internal.icu.text.UnicodeSetSpanner UnicodeSetSpanner}.
     * @param s The string to be spanned
     * @param fromIndex The index of the char (exclusive) that the string should be spanned backwards
     * @param spanCondition The span condition
     * @return The string index which starts the span (i.e. inclusive).
     * @stable ICU 4.4
     */
    public int spanBack(CharSequence s, int fromIndex, SpanCondition spanCondition) {
        if (fromIndex <= 0) {
            return 0;
        }
        if (fromIndex > s.length()) {
            fromIndex = s.length();
        }
        if (bmpSet != null) {
            // Frozen set without strings, or no string is relevant for spanBack().
            return bmpSet.spanBack(s, fromIndex, spanCondition);
        }
        if (stringSpan != null) {
            return stringSpan.spanBack(s, fromIndex, spanCondition);
        } else if (hasStrings()) {
            int which = (spanCondition == SpanCondition.NOT_CONTAINED)
                    ? UnicodeSetStringSpan.BACK_UTF16_NOT_CONTAINED
                            : UnicodeSetStringSpan.BACK_UTF16_CONTAINED;
            UnicodeSetStringSpan strSpan = new UnicodeSetStringSpan(this, new ArrayList<>(strings), which);
            if (strSpan.needsStringSpanUTF16()) {
                return strSpan.spanBack(s, fromIndex, spanCondition);
            }
        }

        // Pin to 0/1 values.
        boolean spanContained = (spanCondition != SpanCondition.NOT_CONTAINED);

        int c;
        int prev = fromIndex;
        do {
            c = Character.codePointBefore(s, prev);
            if (spanContained != contains(c)) {
                break;
            }
            prev -= Character.charCount(c);
        } while (prev > 0);
        return prev;
    }

    /**
     * Clone a thawed version of this class, according to the Freezable interface.
     * @return the clone, not frozen
     * @stable ICU 4.4
     */
    @Override
    public UnicodeSet cloneAsThawed() {
        UnicodeSet result = new UnicodeSet(this);
        assert !result.isFrozen();
        return result;
    }

    // internal function
    private void checkFrozen() {
        if (isFrozen()) {
            throw new UnsupportedOperationException("Attempt to modify frozen object");
        }
    }

    // ************************
    // Additional methods for integration with Generics and Collections
    // ************************

    /**
     * A struct-like class used for iteration through ranges, for faster iteration than by String.
     * Read about the restrictions on usage in {@link UnicodeSet#ranges()}.
     *
     * @stable ICU 54
     */
    public static class EntryRange {
        /**
         * The starting code point of the range.
         *
         * @stable ICU 54
         */
        public int codepoint;
        /**
         * The ending code point of the range
         *
         * @stable ICU 54
         */
        public int codepointEnd;

        EntryRange() {
        }

        /**
         * {@inheritDoc}
         *
         * @stable ICU 54
         */
        @Override
        public String toString() {
            StringBuilder b = new StringBuilder();
            return (
                    codepoint == codepointEnd ? _appendToPat(b, codepoint, false)
                            : _appendToPat(_appendToPat(b, codepoint, false).append('-'), codepointEnd, false))
                            .toString();
        }
    }

    /**
     * Provide for faster iteration than by String. Returns an Iterable/Iterator over ranges of code points.
     * The UnicodeSet must not be altered during the iteration.
     * The EntryRange instance is the same each time; the contents are just reset.
     *
     * <p><b>Warning: </b>To iterate over the full contents, you have to also iterate over the strings.
     *
     * <p><b>Warning: </b>For speed, UnicodeSet iteration does not check for concurrent modification.
     * Do not alter the UnicodeSet while iterating.
     *
     * <pre>
     * // Sample code
     * for (EntryRange range : us1.ranges()) {
     *     // do something with code points between range.codepoint and range.codepointEnd;
     * }
     * for (String s : us1.strings()) {
     *     // do something with each string;
     * }
     * </pre>
     *
     * @stable ICU 54
     */
    public Iterable<EntryRange> ranges() {
        return new EntryRangeIterable();
    }

    private class EntryRangeIterable implements Iterable<EntryRange> {
        @Override
        public Iterator<EntryRange> iterator() {
            return new EntryRangeIterator();
        }
    }

    private class EntryRangeIterator implements Iterator<EntryRange> {
        int pos;
        EntryRange result = new EntryRange();

        @Override
        public boolean hasNext() {
            return pos < len-1;
        }
        @Override
        public EntryRange next() {
            if (pos < len-1) {
                result.codepoint = list[pos++];
                result.codepointEnd = list[pos++]-1;
            } else {
                throw new NoSuchElementException();
            }
            return result;
        }
        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }


    /**
     * Returns a string iterator. Uses the same order of iteration as {@link UnicodeSetIterator}.
     * <p><b>Warning: </b>For speed, UnicodeSet iteration does not check for concurrent modification.
     * Do not alter the UnicodeSet while iterating.
     * @see java.util.Set#iterator()
     * @stable ICU 4.4
     */
    @Override
    public Iterator<String> iterator() {
        return new UnicodeSetIterator2(this);
    }

    // Cover for string iteration.
    private static class UnicodeSetIterator2 implements Iterator<String> {
        // Invariants:
        // sourceList != null then sourceList[item] is a valid character
        // sourceList == null then delegates to stringIterator
        private int[] sourceList;
        private int len;
        private int item;
        private int current;
        private int limit;
        private SortedSet<String> sourceStrings;
        private Iterator<String> stringIterator;
        private char[] buffer;

        UnicodeSetIterator2(UnicodeSet source) {
            // set according to invariants
            len = source.len - 1;
            if (len > 0) {
                sourceStrings = source.strings;
                sourceList = source.list;
                current = sourceList[item++];
                limit = sourceList[item++];
            } else {
                stringIterator = source.strings.iterator();
                sourceList = null;
            }
        }

        /* (non-Javadoc)
         * @see java.util.Iterator#hasNext()
         */
        @Override
        public boolean hasNext() {
            return sourceList != null || stringIterator.hasNext();
        }

        /* (non-Javadoc)
         * @see java.util.Iterator#next()
         */
        @Override
        public String next() {
            if (sourceList == null) {
                return stringIterator.next();
            }
            int codepoint = current++;
            // we have the codepoint we need, but we may need to adjust the state
            if (current >= limit) {
                if (item >= len) {
                    stringIterator = sourceStrings.iterator();
                    sourceList = null;
                } else {
                    current = sourceList[item++];
                    limit = sourceList[item++];
                }
            }
            // Now return. Single code point is easy
            if (codepoint <= 0xFFFF) {
                return String.valueOf((char)codepoint);
            }
            // But Java lacks a valueOfCodePoint, so we handle ourselves for speed
            // allocate a buffer the first time, to make conversion faster.
            if (buffer == null) {
                buffer = new char[2];
            }
            // compute ourselves, to save tests and calls
            int offset = codepoint - Character.MIN_SUPPLEMENTARY_CODE_POINT;
            buffer[0] = (char)((offset >>> 10) + Character.MIN_HIGH_SURROGATE);
            buffer[1] = (char)((offset & 0x3ff) + Character.MIN_LOW_SURROGATE);
            return String.valueOf(buffer);
        }

        /* (non-Javadoc)
         * @see java.util.Iterator#remove()
         */
        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * @see #containsAll(jdk.internal.icu.text.UnicodeSet)
     * @stable ICU 4.4
     */
    public <T extends CharSequence> boolean containsAll(Iterable<T> collection) {
        for (T o : collection) {
            if (!contains(o)) {
                return false;
            }
        }
        return true;
    }

    /**
     * @see #containsNone(jdk.internal.icu.text.UnicodeSet)
     * @stable ICU 4.4
     */
    public <T extends CharSequence> boolean containsNone(Iterable<T> collection) {
        for (T o : collection) {
            if (contains(o)) {
                return false;
            }
        }
        return true;
    }

    /**
     * @see #containsAll(jdk.internal.icu.text.UnicodeSet)
     * @stable ICU 4.4
     */
    public final <T extends CharSequence> boolean containsSome(Iterable<T> collection) {
        return !containsNone(collection);
    }

    /**
     * @see #addAll(jdk.internal.icu.text.UnicodeSet)
     * @stable ICU 4.4
     */
    @SuppressWarnings("unchecked")  // See ticket #11395, this is safe.
    public <T extends CharSequence> UnicodeSet addAll(T... collection) {
        checkFrozen();
        for (T str : collection) {
            add(str);
        }
        return this;
    }


    /**
     * @see #removeAll(jdk.internal.icu.text.UnicodeSet)
     * @stable ICU 4.4
     */
    public <T extends CharSequence> UnicodeSet removeAll(Iterable<T> collection) {
        checkFrozen();
        for (T o : collection) {
            remove(o);
        }
        return this;
    }

    /**
     * @see #retainAll(jdk.internal.icu.text.UnicodeSet)
     * @stable ICU 4.4
     */
    public <T extends CharSequence> UnicodeSet retainAll(Iterable<T> collection) {
        checkFrozen();
        // TODO optimize
        UnicodeSet toRetain = new UnicodeSet();
        toRetain.addAll(collection);
        retainAll(toRetain);
        return this;
    }

    /**
     * Comparison style enums used by {@link UnicodeSet#compareTo(UnicodeSet, ComparisonStyle)}.
     * @stable ICU 4.4
     */
    public enum ComparisonStyle {
        /**
         * @stable ICU 4.4
         */
        SHORTER_FIRST,
        /**
         * @stable ICU 4.4
         */
        LEXICOGRAPHIC,
        /**
         * @stable ICU 4.4
         */
        LONGER_FIRST
    }

    /**
     * Compares UnicodeSets, where shorter come first, and otherwise lexicographically
     * (according to the comparison of the first characters that differ).
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     * @stable ICU 4.4
     */
    @Override
    public int compareTo(UnicodeSet o) {
        return compareTo(o, ComparisonStyle.SHORTER_FIRST);
    }
    /**
     * Compares UnicodeSets, in three different ways.
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     * @stable ICU 4.4
     */
    public int compareTo(UnicodeSet o, ComparisonStyle style) {
        if (style != ComparisonStyle.LEXICOGRAPHIC) {
            int diff = size() - o.size();
            if (diff != 0) {
                return (diff < 0) == (style == ComparisonStyle.SHORTER_FIRST) ? -1 : 1;
            }
        }
        int result;
        for (int i = 0; ; ++i) {
            if (0 != (result = list[i] - o.list[i])) {
                // if either list ran out, compare to the last string
                if (list[i] == HIGH) {
                    if (!hasStrings()) return 1;
                    String item = strings.first();
                    return compare(item, o.list[i]);
                }
                if (o.list[i] == HIGH) {
                    if (!o.hasStrings()) return -1;
                    String item = o.strings.first();
                    int compareResult = compare(item, list[i]);
                    return compareResult > 0 ? -1 : compareResult < 0 ? 1 : 0; // Reverse the order.
                }
                // otherwise return the result if even index, or the reversal if not
                return (i & 1) == 0 ? result : -result;
            }
            if (list[i] == HIGH) {
                break;
            }
        }
        return compare(strings, o.strings);
    }

    /**
     * @stable ICU 4.4
     */
    public int compareTo(Iterable<String> other) {
        return compare(this, other);
    }

    /**
     * Utility to compare a string to a code point.
     * Same results as turning the code point into a string (with the [ugly] new StringBuilder().appendCodePoint(codepoint).toString())
     * and comparing, but much faster (no object creation).
     * Actually, there is one difference; a null compares as less.
     * Note that this (=String) order is UTF-16 order -- <i>not</i> code point order.
     * @stable ICU 4.4
     */

    public static int compare(CharSequence string, int codePoint) {
        return CharSequences.compare(string, codePoint);
    }

    /**
     * Utility to compare a string to a code point.
     * Same results as turning the code point into a string and comparing, but much faster (no object creation).
     * Actually, there is one difference; a null compares as less.
     * Note that this (=String) order is UTF-16 order -- <i>not</i> code point order.
     * @stable ICU 4.4
     */
    public static int compare(int codePoint, CharSequence string) {
        return -CharSequences.compare(string, codePoint);
    }


    /**
     * Utility to compare two iterables. Warning: the ordering in iterables is important. For Collections that are ordered,
     * like Lists, that is expected. However, Sets in Java violate Leibniz's law when it comes to iteration.
     * That means that sets can't be compared directly with this method, unless they are TreeSets without
     * (or with the same) comparator. Unfortunately, it is impossible to reliably detect in Java whether subclass of
     * Collection satisfies the right criteria, so it is left to the user to avoid those circumstances.
     * @stable ICU 4.4
     */
    public static <T extends Comparable<T>> int compare(Iterable<T> collection1, Iterable<T> collection2) {
        return compare(collection1.iterator(), collection2.iterator());
    }

    /**
     * Utility to compare two iterators. Warning: the ordering in iterables is important. For Collections that are ordered,
     * like Lists, that is expected. However, Sets in Java violate Leibniz's law when it comes to iteration.
     * That means that sets can't be compared directly with this method, unless they are TreeSets without
     * (or with the same) comparator. Unfortunately, it is impossible to reliably detect in Java whether subclass of
     * Collection satisfies the right criteria, so it is left to the user to avoid those circumstances.
     * @internal
     * @deprecated This API is ICU internal only.
     */
    @Deprecated
    public static <T extends Comparable<T>> int compare(Iterator<T> first, Iterator<T> other) {
        while (true) {
            if (!first.hasNext()) {
                return other.hasNext() ? -1 : 0;
            } else if (!other.hasNext()) {
                return 1;
            }
            T item1 = first.next();
            T item2 = other.next();
            int result = item1.compareTo(item2);
            if (result != 0) {
                return result;
            }
        }
    }


    /**
     * Utility to compare two collections, optionally by size, and then lexicographically.
     * @stable ICU 4.4
     */
    public static <T extends Comparable<T>> int compare(Collection<T> collection1, Collection<T> collection2, ComparisonStyle style) {
        if (style != ComparisonStyle.LEXICOGRAPHIC) {
            int diff = collection1.size() - collection2.size();
            if (diff != 0) {
                return (diff < 0) == (style == ComparisonStyle.SHORTER_FIRST) ? -1 : 1;
            }
        }
        return compare(collection1, collection2);
    }

    /**
     * Utility for adding the contents of an iterable to a collection.
     * @stable ICU 4.4
     */
    public static <T, U extends Collection<T>> U addAllTo(Iterable<T> source, U target) {
        for (T item : source) {
            target.add(item);
        }
        return target;
    }

    /**
     * Utility for adding the contents of an iterable to a collection.
     * @stable ICU 4.4
     */
    public static <T> T[] addAllTo(Iterable<T> source, T[] target) {
        int i = 0;
        for (T item : source) {
            target[i++] = item;
        }
        return target;
    }

    /**
     * For iterating through the strings in the set. Example:
     * <pre>
     * for (String key : myUnicodeSet.strings()) {
     *   doSomethingWith(key);
     * }
     * </pre>
     * @stable ICU 4.4
     */
    public Collection<String> strings() {
        if (hasStrings()) {
            return Collections.unmodifiableSortedSet(strings);
        } else {
            return EMPTY_STRINGS;
        }
    }

    /**
     * Return the value of the first code point, if the string is exactly one code point. Otherwise return Integer.MAX_VALUE.
     * @internal
     * @deprecated This API is ICU internal only.
     */
    @Deprecated
    public static int getSingleCodePoint(CharSequence s) {
        return CharSequences.getSingleCodePoint(s);
    }

    /**
     * Simplify the ranges in a Unicode set by merging any ranges that are only separated by characters in the dontCare set.
     * For example, the ranges: \\u2E80-\\u2E99\\u2E9B-\\u2EF3\\u2F00-\\u2FD5\\u2FF0-\\u2FFB\\u3000-\\u303E change to \\u2E80-\\u303E
     * if the dontCare set includes unassigned characters (for a particular version of Unicode).
     * @param dontCare Set with the don't-care characters for spanning
     * @return the input set, modified
     * @internal
     * @deprecated This API is ICU internal only.
     */
    @Deprecated
    public UnicodeSet addBridges(UnicodeSet dontCare) {
        UnicodeSet notInInput = new UnicodeSet(this).complement().removeAllStrings();
        for (UnicodeSetIterator it = new UnicodeSetIterator(notInInput); it.nextRange();) {
            if (it.codepoint != 0 && it.codepointEnd != 0x10FFFF &&
                    dontCare.contains(it.codepoint, it.codepointEnd)) {
                add(it.codepoint,it.codepointEnd);
            }
        }
        return this;
    }

    /**
     * Find the first index at or after fromIndex where the UnicodeSet matches at that index.
     * If findNot is true, then reverse the sense of the match: find the first place where the UnicodeSet doesn't match.
     * If there is no match, length is returned.
     * @internal
     * @deprecated This API is ICU internal only. Use span instead.
     */
    @Deprecated
    public int findIn(CharSequence value, int fromIndex, boolean findNot) {
        //TODO add strings, optimize, using ICU4C algorithms
        int cp;
        for (; fromIndex < value.length(); fromIndex += UTF16.getCharCount(cp)) {
            cp = UTF16.charAt(value, fromIndex);
            if (contains(cp) != findNot) {
                break;
            }
        }
        return fromIndex;
    }

    /**
     * Find the last index before fromIndex where the UnicodeSet matches at that index.
     * If findNot is true, then reverse the sense of the match: find the last place where the UnicodeSet doesn't match.
     * If there is no match, -1 is returned.
     * BEFORE index is not in the UnicodeSet.
     * @internal
     * @deprecated This API is ICU internal only. Use spanBack instead.
     */
    @Deprecated
    public int findLastIn(CharSequence value, int fromIndex, boolean findNot) {
        //TODO add strings, optimize, using ICU4C algorithms
        int cp;
        fromIndex -= 1;
        for (; fromIndex >= 0; fromIndex -= UTF16.getCharCount(cp)) {
            cp = UTF16.charAt(value, fromIndex);
            if (contains(cp) != findNot) {
                break;
            }
        }
        return fromIndex < 0 ? -1 : fromIndex;
    }

    /**
     * Strips code points from source. If matches is true, script all that match <i>this</i>. If matches is false, then strip all that <i>don't</i> match.
     * @param source The source of the CharSequence to strip from.
     * @param matches A boolean to either strip all that matches or don't match with the current UnicodeSet object.
     * @return The string after it has been stripped.
     * @internal
     * @deprecated This API is ICU internal only. Use replaceFrom.
     */
    @Deprecated
    public String stripFrom(CharSequence source, boolean matches) {
        StringBuilder result = new StringBuilder();
        for (int pos = 0; pos < source.length();) {
            int inside = findIn(source, pos, !matches);
            result.append(source.subSequence(pos, inside));
            pos = findIn(source, inside, matches); // get next start
        }
        return result.toString();
    }

    /**
     * Argument values for whether span() and similar functions continue while the current character is contained vs.
     * not contained in the set.
     * <p>
     * The functionality is straightforward for sets with only single code points, without strings (which is the common
     * case):
     * <ul>
     * <li>CONTAINED and SIMPLE work the same.
     * <li>CONTAINED and SIMPLE are inverses of NOT_CONTAINED.
     * <li>span() and spanBack() partition any string the
     * same way when alternating between span(NOT_CONTAINED) and span(either "contained" condition).
     * <li>Using a
     * complemented (inverted) set and the opposite span conditions yields the same results.
     * </ul>
     * When a set contains multi-code point strings, then these statements may not be true, depending on the strings in
     * the set (for example, whether they overlap with each other) and the string that is processed. For a set with
     * strings:
     * <ul>
     * <li>The complement of the set contains the opposite set of code points, but the same set of strings.
     * Therefore, complementing both the set and the span conditions may yield different results.
     * <li>When starting spans
     * at different positions in a string (span(s, ...) vs. span(s+1, ...)) the ends of the spans may be different
     * because a set string may start before the later position.
     * <li>span(SIMPLE) may be shorter than
     * span(CONTAINED) because it will not recursively try all possible paths. For example, with a set which
     * contains the three strings "xy", "xya" and "ax", span("xyax", CONTAINED) will return 4 but span("xyax",
     * SIMPLE) will return 3. span(SIMPLE) will never be longer than span(CONTAINED).
     * <li>With either "contained" condition, span() and spanBack() may partition a string in different ways. For example,
     * with a set which contains the two strings "ab" and "ba", and when processing the string "aba", span() will yield
     * contained/not-contained boundaries of { 0, 2, 3 } while spanBack() will yield boundaries of { 0, 1, 3 }.
     * </ul>
     * Note: If it is important to get the same boundaries whether iterating forward or backward through a string, then
     * either only span() should be used and the boundaries cached for backward operation, or an ICU BreakIterator could
     * be used.
     * <p>
     * Note: Unpaired surrogates are treated like surrogate code points. Similarly, set strings match only on code point
     * boundaries, never in the middle of a surrogate pair.
     *
     * @stable ICU 4.4
     */
    public enum SpanCondition {
        /**
         * Continues a span() while there is no set element at the current position.
         * Increments by one code point at a time.
         * Stops before the first set element (character or string).
         * (For code points only, this is like while contains(current)==false).
         * <p>
         * When span() returns, the substring between where it started and the position it returned consists only of
         * characters that are not in the set, and none of its strings overlap with the span.
         *
         * @stable ICU 4.4
         */
        NOT_CONTAINED,

        /**
         * Spans the longest substring that is a concatenation of set elements (characters or strings).
         * (For characters only, this is like while contains(current)==true).
         * <p>
         * When span() returns, the substring between where it started and the position it returned consists only of set
         * elements (characters or strings) that are in the set.
         * <p>
         * If a set contains strings, then the span will be the longest substring for which there
         * exists at least one non-overlapping concatenation of set elements (characters or strings).
         * This is equivalent to a POSIX regular expression for <code>(OR of each set element)*</code>.
         * (Java/ICU/Perl regex stops at the first match of an OR.)
         *
         * @stable ICU 4.4
         */
        CONTAINED,

        /**
         * Continues a span() while there is a set element at the current position.
         * Increments by the longest matching element at each position.
         * (For characters only, this is like while contains(current)==true).
         * <p>
         * When span() returns, the substring between where it started and the position it returned consists only of set
         * elements (characters or strings) that are in the set.
         * <p>
         * If a set only contains single characters, then this is the same as CONTAINED.
         * <p>
         * If a set contains strings, then the span will be the longest substring with a match at each position with the
         * longest single set element (character or string).
         * <p>
         * Use this span condition together with other longest-match algorithms, such as ICU converters
         * (ucnv_getUnicodeSet()).
         *
         * @stable ICU 4.4
         */
        SIMPLE,

        /**
         * One more than the last span condition.
         *
         * @stable ICU 4.4
         */
        CONDITION_COUNT
    }

    /**
     * Get the default symbol table. Null means ordinary processing. For internal use only.
     * @return the symbol table
     * @internal
     * @deprecated This API is ICU internal only.
     */
    @Deprecated
    public static XSymbolTable getDefaultXSymbolTable() {
        return XSYMBOL_TABLE;
    }

    /**
     * Set the default symbol table. Null means ordinary processing. For internal use only. Will affect all subsequent parsing
     * of UnicodeSets.
     * <p>
     * WARNING: If this function is used with a UnicodeProperty, and the
     * Unassigned characters (gc=Cn) are different than in ICU, you MUST call
     * {@code UnicodeProperty.ResetCacheProperties} afterwards. If you then call {@code UnicodeSet.setDefaultXSymbolTable}
     * with null to clear the value, you MUST also call {@code UnicodeProperty.ResetCacheProperties}.
     *
     * @param xSymbolTable the new default symbol table.
     * @internal
     * @deprecated This API is ICU internal only.
     */
    @Deprecated
    public static void setDefaultXSymbolTable(XSymbolTable xSymbolTable) {
        // If the properties override inclusions, these have to be regenerated.
        // TODO: Check if the Unicode Tools or Unicode Utilities really need this.
        CharacterPropertiesImpl.clear();
        XSYMBOL_TABLE = xSymbolTable;
    }
}
//eof
