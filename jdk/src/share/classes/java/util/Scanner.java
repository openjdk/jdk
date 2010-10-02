/*
 * Copyright (c) 2003, 2009, Oracle and/or its affiliates. All rights reserved.
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

package java.util;

import java.nio.file.FileRef;
import java.util.regex.*;
import java.io.*;
import java.math.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.text.*;
import java.util.Locale;
import sun.misc.LRUCache;

/**
 * A simple text scanner which can parse primitive types and strings using
 * regular expressions.
 *
 * <p>A <code>Scanner</code> breaks its input into tokens using a
 * delimiter pattern, which by default matches whitespace. The resulting
 * tokens may then be converted into values of different types using the
 * various <tt>next</tt> methods.
 *
 * <p>For example, this code allows a user to read a number from
 * <tt>System.in</tt>:
 * <blockquote><pre>
 *     Scanner sc = new Scanner(System.in);
 *     int i = sc.nextInt();
 * </pre></blockquote>
 *
 * <p>As another example, this code allows <code>long</code> types to be
 * assigned from entries in a file <code>myNumbers</code>:
 * <blockquote><pre>
 *      Scanner sc = new Scanner(new File("myNumbers"));
 *      while (sc.hasNextLong()) {
 *          long aLong = sc.nextLong();
 *      }</pre></blockquote>
 *
 * <p>The scanner can also use delimiters other than whitespace. This
 * example reads several items in from a string:
 *<blockquote><pre>
 *     String input = "1 fish 2 fish red fish blue fish";
 *     Scanner s = new Scanner(input).useDelimiter("\\s*fish\\s*");
 *     System.out.println(s.nextInt());
 *     System.out.println(s.nextInt());
 *     System.out.println(s.next());
 *     System.out.println(s.next());
 *     s.close(); </pre></blockquote>
 * <p>
 * prints the following output:
 * <blockquote><pre>
 *     1
 *     2
 *     red
 *     blue </pre></blockquote>
 *
 * <p>The same output can be generated with this code, which uses a regular
 * expression to parse all four tokens at once:
 *<blockquote><pre>
 *     String input = "1 fish 2 fish red fish blue fish";
 *     Scanner s = new Scanner(input);
 *     s.findInLine("(\\d+) fish (\\d+) fish (\\w+) fish (\\w+)");
 *     MatchResult result = s.match();
 *     for (int i=1; i<=result.groupCount(); i++)
 *         System.out.println(result.group(i));
 *     s.close(); </pre></blockquote>
 *
 * <p>The <a name="default-delimiter">default whitespace delimiter</a> used
 * by a scanner is as recognized by {@link java.lang.Character}.{@link
 * java.lang.Character#isWhitespace(char) isWhitespace}. The {@link #reset}
 * method will reset the value of the scanner's delimiter to the default
 * whitespace delimiter regardless of whether it was previously changed.
 *
 * <p>A scanning operation may block waiting for input.
 *
 * <p>The {@link #next} and {@link #hasNext} methods and their
 * primitive-type companion methods (such as {@link #nextInt} and
 * {@link #hasNextInt}) first skip any input that matches the delimiter
 * pattern, and then attempt to return the next token. Both <tt>hasNext</tt>
 * and <tt>next</tt> methods may block waiting for further input.  Whether a
 * <tt>hasNext</tt> method blocks has no connection to whether or not its
 * associated <tt>next</tt> method will block.
 *
 * <p> The {@link #findInLine}, {@link #findWithinHorizon}, and {@link #skip}
 * methods operate independently of the delimiter pattern. These methods will
 * attempt to match the specified pattern with no regard to delimiters in the
 * input and thus can be used in special circumstances where delimiters are
 * not relevant. These methods may block waiting for more input.
 *
 * <p>When a scanner throws an {@link InputMismatchException}, the scanner
 * will not pass the token that caused the exception, so that it may be
 * retrieved or skipped via some other method.
 *
 * <p>Depending upon the type of delimiting pattern, empty tokens may be
 * returned. For example, the pattern <tt>"\\s+"</tt> will return no empty
 * tokens since it matches multiple instances of the delimiter. The delimiting
 * pattern <tt>"\\s"</tt> could return empty tokens since it only passes one
 * space at a time.
 *
 * <p> A scanner can read text from any object which implements the {@link
 * java.lang.Readable} interface.  If an invocation of the underlying
 * readable's {@link java.lang.Readable#read} method throws an {@link
 * java.io.IOException} then the scanner assumes that the end of the input
 * has been reached.  The most recent <tt>IOException</tt> thrown by the
 * underlying readable can be retrieved via the {@link #ioException} method.
 *
 * <p>When a <code>Scanner</code> is closed, it will close its input source
 * if the source implements the {@link java.io.Closeable} interface.
 *
 * <p>A <code>Scanner</code> is not safe for multithreaded use without
 * external synchronization.
 *
 * <p>Unless otherwise mentioned, passing a <code>null</code> parameter into
 * any method of a <code>Scanner</code> will cause a
 * <code>NullPointerException</code> to be thrown.
 *
 * <p>A scanner will default to interpreting numbers as decimal unless a
 * different radix has been set by using the {@link #useRadix} method. The
 * {@link #reset} method will reset the value of the scanner's radix to
 * <code>10</code> regardless of whether it was previously changed.
 *
 * <a name="localized-numbers">
 * <h4> Localized numbers </h4>
 *
 * <p> An instance of this class is capable of scanning numbers in the standard
 * formats as well as in the formats of the scanner's locale. A scanner's
 * <a name="initial-locale">initial locale </a>is the value returned by the {@link
 * java.util.Locale#getDefault} method; it may be changed via the {@link
 * #useLocale} method. The {@link #reset} method will reset the value of the
 * scanner's locale to the initial locale regardless of whether it was
 * previously changed.
 *
 * <p>The localized formats are defined in terms of the following parameters,
 * which for a particular locale are taken from that locale's {@link
 * java.text.DecimalFormat DecimalFormat} object, <tt>df</tt>, and its and
 * {@link java.text.DecimalFormatSymbols DecimalFormatSymbols} object,
 * <tt>dfs</tt>.
 *
 * <blockquote><table>
 * <tr><td valign="top"><i>LocalGroupSeparator&nbsp;&nbsp;</i></td>
 *     <td valign="top">The character used to separate thousands groups,
 *                      <i>i.e.,</i>&nbsp;<tt>dfs.</tt>{@link
 *                      java.text.DecimalFormatSymbols#getGroupingSeparator
 *                      getGroupingSeparator()}</td></tr>
 * <tr><td valign="top"><i>LocalDecimalSeparator&nbsp;&nbsp;</i></td>
 *     <td valign="top">The character used for the decimal point,
 *                      <i>i.e.,</i>&nbsp;<tt>dfs.</tt>{@link
 *                      java.text.DecimalFormatSymbols#getDecimalSeparator
 *                      getDecimalSeparator()}</td></tr>
 * <tr><td valign="top"><i>LocalPositivePrefix&nbsp;&nbsp;</i></td>
 *     <td valign="top">The string that appears before a positive number (may
 *                      be empty), <i>i.e.,</i>&nbsp;<tt>df.</tt>{@link
 *                      java.text.DecimalFormat#getPositivePrefix
 *                      getPositivePrefix()}</td></tr>
 * <tr><td valign="top"><i>LocalPositiveSuffix&nbsp;&nbsp;</i></td>
 *     <td valign="top">The string that appears after a positive number (may be
 *                      empty), <i>i.e.,</i>&nbsp;<tt>df.</tt>{@link
 *                      java.text.DecimalFormat#getPositiveSuffix
 *                      getPositiveSuffix()}</td></tr>
 * <tr><td valign="top"><i>LocalNegativePrefix&nbsp;&nbsp;</i></td>
 *     <td valign="top">The string that appears before a negative number (may
 *                      be empty), <i>i.e.,</i>&nbsp;<tt>df.</tt>{@link
 *                      java.text.DecimalFormat#getNegativePrefix
 *                      getNegativePrefix()}</td></tr>
 * <tr><td valign="top"><i>LocalNegativeSuffix&nbsp;&nbsp;</i></td>
 *     <td valign="top">The string that appears after a negative number (may be
 *                      empty), <i>i.e.,</i>&nbsp;<tt>df.</tt>{@link
 *                      java.text.DecimalFormat#getNegativeSuffix
 *                      getNegativeSuffix()}</td></tr>
 * <tr><td valign="top"><i>LocalNaN&nbsp;&nbsp;</i></td>
 *     <td valign="top">The string that represents not-a-number for
 *                      floating-point values,
 *                      <i>i.e.,</i>&nbsp;<tt>dfs.</tt>{@link
 *                      java.text.DecimalFormatSymbols#getNaN
 *                      getNaN()}</td></tr>
 * <tr><td valign="top"><i>LocalInfinity&nbsp;&nbsp;</i></td>
 *     <td valign="top">The string that represents infinity for floating-point
 *                      values, <i>i.e.,</i>&nbsp;<tt>dfs.</tt>{@link
 *                      java.text.DecimalFormatSymbols#getInfinity
 *                      getInfinity()}</td></tr>
 * </table></blockquote>
 *
 * <a name="number-syntax">
 * <h4> Number syntax </h4>
 *
 * <p> The strings that can be parsed as numbers by an instance of this class
 * are specified in terms of the following regular-expression grammar, where
 * Rmax is the highest digit in the radix being used (for example, Rmax is 9
 * in base 10).
 *
 * <p>
 * <table cellspacing=0 cellpadding=0 align=center>
 *
 *   <tr><td valign=top align=right><i>NonASCIIDigit</i>&nbsp;&nbsp;::</td>
 *       <td valign=top>= A non-ASCII character c for which
 *            {@link java.lang.Character#isDigit Character.isDigit}<tt>(c)</tt>
 *                        returns&nbsp;true</td></tr>
 *
 *   <tr><td>&nbsp;</td></tr>
 *
 *   <tr><td align=right><i>Non0Digit</i>&nbsp;&nbsp;::</td>
 *   <td><tt>= [1-</tt><i>Rmax</i><tt>] | </tt><i>NonASCIIDigit</i></td></tr>
 *
 *   <tr><td>&nbsp;</td></tr>
 *
 *   <tr><td align=right><i>Digit</i>&nbsp;&nbsp;::</td>
 *   <td><tt>= [0-</tt><i>Rmax</i><tt>] | </tt><i>NonASCIIDigit</i></td></tr>
 *
 *   <tr><td>&nbsp;</td></tr>
 *
 *   <tr><td valign=top align=right><i>GroupedNumeral</i>&nbsp;&nbsp;::</td>
 *       <td valign=top>
 *         <table cellpadding=0 cellspacing=0>
 *           <tr><td><tt>= (&nbsp;</tt></td>
 *               <td><i>Non0Digit</i><tt>
 *                   </tt><i>Digit</i><tt>?
 *                   </tt><i>Digit</i><tt>?</tt></td></tr>
 *           <tr><td></td>
 *               <td><tt>(&nbsp;</tt><i>LocalGroupSeparator</i><tt>
 *                         </tt><i>Digit</i><tt>
 *                         </tt><i>Digit</i><tt>
 *                         </tt><i>Digit</i><tt> )+ )</tt></td></tr>
 *         </table></td></tr>
 *
 *   <tr><td>&nbsp;</td></tr>
 *
 *   <tr><td align=right><i>Numeral</i>&nbsp;&nbsp;::</td>
 *       <td><tt>= ( ( </tt><i>Digit</i><tt>+ )
 *               | </tt><i>GroupedNumeral</i><tt> )</tt></td></tr>
 *
 *   <tr><td>&nbsp;</td></tr>
 *
 *   <tr><td valign=top align=right>
 *         <a name="Integer-regex"><i>Integer</i>&nbsp;&nbsp;::</td>
 *       <td valign=top><tt>= ( [-+]? ( </tt><i>Numeral</i><tt>
 *                               ) )</tt></td></tr>
 *   <tr><td></td>
 *       <td><tt>| </tt><i>LocalPositivePrefix</i><tt> </tt><i>Numeral</i><tt>
 *                      </tt><i>LocalPositiveSuffix</i></td></tr>
 *   <tr><td></td>
 *       <td><tt>| </tt><i>LocalNegativePrefix</i><tt> </tt><i>Numeral</i><tt>
 *                 </tt><i>LocalNegativeSuffix</i></td></tr>
 *
 *   <tr><td>&nbsp;</td></tr>
 *
 *   <tr><td align=right><i>DecimalNumeral</i>&nbsp;&nbsp;::</td>
 *       <td><tt>= </tt><i>Numeral</i></td></tr>
 *   <tr><td></td>
 *       <td><tt>| </tt><i>Numeral</i><tt>
 *                 </tt><i>LocalDecimalSeparator</i><tt>
 *                 </tt><i>Digit</i><tt>*</tt></td></tr>
 *   <tr><td></td>
 *       <td><tt>| </tt><i>LocalDecimalSeparator</i><tt>
 *                 </tt><i>Digit</i><tt>+</tt></td></tr>
 *
 *   <tr><td>&nbsp;</td></tr>
 *
 *   <tr><td align=right><i>Exponent</i>&nbsp;&nbsp;::</td>
 *       <td><tt>= ( [eE] [+-]? </tt><i>Digit</i><tt>+ )</tt></td></tr>
 *
 *   <tr><td>&nbsp;</td></tr>
 *
 *   <tr><td align=right>
 *         <a name="Decimal-regex"><i>Decimal</i>&nbsp;&nbsp;::</td>
 *       <td><tt>= ( [-+]? </tt><i>DecimalNumeral</i><tt>
 *                         </tt><i>Exponent</i><tt>? )</tt></td></tr>
 *   <tr><td></td>
 *       <td><tt>| </tt><i>LocalPositivePrefix</i><tt>
 *                 </tt><i>DecimalNumeral</i><tt>
 *                 </tt><i>LocalPositiveSuffix</i>
 *                 </tt><i>Exponent</i><tt>?</td></tr>
 *   <tr><td></td>
 *       <td><tt>| </tt><i>LocalNegativePrefix</i><tt>
 *                 </tt><i>DecimalNumeral</i><tt>
 *                 </tt><i>LocalNegativeSuffix</i>
 *                 </tt><i>Exponent</i><tt>?</td></tr>
 *
 *   <tr><td>&nbsp;</td></tr>
 *
 *   <tr><td align=right><i>HexFloat</i>&nbsp;&nbsp;::</td>
 *       <td><tt>= [-+]? 0[xX][0-9a-fA-F]*\.[0-9a-fA-F]+
 *                 ([pP][-+]?[0-9]+)?</tt></td></tr>
 *
 *   <tr><td>&nbsp;</td></tr>
 *
 *   <tr><td align=right><i>NonNumber</i>&nbsp;&nbsp;::</td>
 *       <td valign=top><tt>= NaN
 *                          | </tt><i>LocalNan</i><tt>
 *                          | Infinity
 *                          | </tt><i>LocalInfinity</i></td></tr>
 *
 *   <tr><td>&nbsp;</td></tr>
 *
 *   <tr><td align=right><i>SignedNonNumber</i>&nbsp;&nbsp;::</td>
 *       <td><tt>= ( [-+]? </tt><i>NonNumber</i><tt> )</tt></td></tr>
 *   <tr><td></td>
 *       <td><tt>| </tt><i>LocalPositivePrefix</i><tt>
 *                 </tt><i>NonNumber</i><tt>
 *                 </tt><i>LocalPositiveSuffix</i></td></tr>
 *   <tr><td></td>
 *       <td><tt>| </tt><i>LocalNegativePrefix</i><tt>
 *                 </tt><i>NonNumber</i><tt>
 *                 </tt><i>LocalNegativeSuffix</i></td></tr>
 *
 *   <tr><td>&nbsp;</td></tr>
 *
 *   <tr><td valign=top align=right>
 *         <a name="Float-regex"><i>Float</i>&nbsp;&nbsp;::</td>
 *       <td valign=top><tt>= </tt><i>Decimal</i><tt></td></tr>
 *       <tr><td></td>
 *           <td><tt>| </tt><i>HexFloat</i><tt></td></tr>
 *       <tr><td></td>
 *           <td><tt>| </tt><i>SignedNonNumber</i><tt></td></tr>
 *
 * </table>
 * </center>
 *
 * <p> Whitespace is not significant in the above regular expressions.
 *
 * @since   1.5
 */
public final class Scanner implements Iterator<String>, Closeable {

    // Internal buffer used to hold input
    private CharBuffer buf;

    // Size of internal character buffer
    private static final int BUFFER_SIZE = 1024; // change to 1024;

    // The index into the buffer currently held by the Scanner
    private int position;

    // Internal matcher used for finding delimiters
    private Matcher matcher;

    // Pattern used to delimit tokens
    private Pattern delimPattern;

    // Pattern found in last hasNext operation
    private Pattern hasNextPattern;

    // Position after last hasNext operation
    private int hasNextPosition;

    // Result after last hasNext operation
    private String hasNextResult;

    // The input source
    private Readable source;

    // Boolean is true if source is done
    private boolean sourceClosed = false;

    // Boolean indicating more input is required
    private boolean needInput = false;

    // Boolean indicating if a delim has been skipped this operation
    private boolean skipped = false;

    // A store of a position that the scanner may fall back to
    private int savedScannerPosition = -1;

    // A cache of the last primitive type scanned
    private Object typeCache = null;

    // Boolean indicating if a match result is available
    private boolean matchValid = false;

    // Boolean indicating if this scanner has been closed
    private boolean closed = false;

    // The current radix used by this scanner
    private int radix = 10;

    // The default radix for this scanner
    private int defaultRadix = 10;

    // The locale used by this scanner
    private Locale locale = null;

    // A cache of the last few recently used Patterns
    private LRUCache<String,Pattern> patternCache =
    new LRUCache<String,Pattern>(7) {
        protected Pattern create(String s) {
            return Pattern.compile(s);
        }
        protected boolean hasName(Pattern p, String s) {
            return p.pattern().equals(s);
        }
    };

    // A holder of the last IOException encountered
    private IOException lastException;

    // A pattern for java whitespace
    private static Pattern WHITESPACE_PATTERN = Pattern.compile(
                                                "\\p{javaWhitespace}+");

    // A pattern for any token
    private static Pattern FIND_ANY_PATTERN = Pattern.compile("(?s).*");

    // A pattern for non-ASCII digits
    private static Pattern NON_ASCII_DIGIT = Pattern.compile(
        "[\\p{javaDigit}&&[^0-9]]");

    // Fields and methods to support scanning primitive types

    /**
     * Locale dependent values used to scan numbers
     */
    private String groupSeparator = "\\,";
    private String decimalSeparator = "\\.";
    private String nanString = "NaN";
    private String infinityString = "Infinity";
    private String positivePrefix = "";
    private String negativePrefix = "\\-";
    private String positiveSuffix = "";
    private String negativeSuffix = "";

    /**
     * Fields and an accessor method to match booleans
     */
    private static volatile Pattern boolPattern;
    private static final String BOOLEAN_PATTERN = "true|false";
    private static Pattern boolPattern() {
        Pattern bp = boolPattern;
        if (bp == null)
            boolPattern = bp = Pattern.compile(BOOLEAN_PATTERN,
                                          Pattern.CASE_INSENSITIVE);
        return bp;
    }

    /**
     * Fields and methods to match bytes, shorts, ints, and longs
     */
    private Pattern integerPattern;
    private String digits = "0123456789abcdefghijklmnopqrstuvwxyz";
    private String non0Digit = "[\\p{javaDigit}&&[^0]]";
    private int SIMPLE_GROUP_INDEX = 5;
    private String buildIntegerPatternString() {
        String radixDigits = digits.substring(0, radix);
        // \\p{javaDigit} is not guaranteed to be appropriate
        // here but what can we do? The final authority will be
        // whatever parse method is invoked, so ultimately the
        // Scanner will do the right thing
        String digit = "((?i)["+radixDigits+"]|\\p{javaDigit})";
        String groupedNumeral = "("+non0Digit+digit+"?"+digit+"?("+
                                groupSeparator+digit+digit+digit+")+)";
        // digit++ is the possessive form which is necessary for reducing
        // backtracking that would otherwise cause unacceptable performance
        String numeral = "(("+ digit+"++)|"+groupedNumeral+")";
        String javaStyleInteger = "([-+]?(" + numeral + "))";
        String negativeInteger = negativePrefix + numeral + negativeSuffix;
        String positiveInteger = positivePrefix + numeral + positiveSuffix;
        return "("+ javaStyleInteger + ")|(" +
            positiveInteger + ")|(" +
            negativeInteger + ")";
    }
    private Pattern integerPattern() {
        if (integerPattern == null) {
            integerPattern = patternCache.forName(buildIntegerPatternString());
        }
        return integerPattern;
    }

    /**
     * Fields and an accessor method to match line separators
     */
    private static volatile Pattern separatorPattern;
    private static volatile Pattern linePattern;
    private static final String LINE_SEPARATOR_PATTERN =
                                           "\r\n|[\n\r\u2028\u2029\u0085]";
    private static final String LINE_PATTERN = ".*("+LINE_SEPARATOR_PATTERN+")|.+$";

    private static Pattern separatorPattern() {
        Pattern sp = separatorPattern;
        if (sp == null)
            separatorPattern = sp = Pattern.compile(LINE_SEPARATOR_PATTERN);
        return sp;
    }

    private static Pattern linePattern() {
        Pattern lp = linePattern;
        if (lp == null)
            linePattern = lp = Pattern.compile(LINE_PATTERN);
        return lp;
    }

    /**
     * Fields and methods to match floats and doubles
     */
    private Pattern floatPattern;
    private Pattern decimalPattern;
    private void buildFloatAndDecimalPattern() {
        // \\p{javaDigit} may not be perfect, see above
        String digit = "([0-9]|(\\p{javaDigit}))";
        String exponent = "([eE][+-]?"+digit+"+)?";
        String groupedNumeral = "("+non0Digit+digit+"?"+digit+"?("+
                                groupSeparator+digit+digit+digit+")+)";
        // Once again digit++ is used for performance, as above
        String numeral = "(("+digit+"++)|"+groupedNumeral+")";
        String decimalNumeral = "("+numeral+"|"+numeral +
            decimalSeparator + digit + "*+|"+ decimalSeparator +
            digit + "++)";
        String nonNumber = "(NaN|"+nanString+"|Infinity|"+
                               infinityString+")";
        String positiveFloat = "(" + positivePrefix + decimalNumeral +
                            positiveSuffix + exponent + ")";
        String negativeFloat = "(" + negativePrefix + decimalNumeral +
                            negativeSuffix + exponent + ")";
        String decimal = "(([-+]?" + decimalNumeral + exponent + ")|"+
            positiveFloat + "|" + negativeFloat + ")";
        String hexFloat =
            "[-+]?0[xX][0-9a-fA-F]*\\.[0-9a-fA-F]+([pP][-+]?[0-9]+)?";
        String positiveNonNumber = "(" + positivePrefix + nonNumber +
                            positiveSuffix + ")";
        String negativeNonNumber = "(" + negativePrefix + nonNumber +
                            negativeSuffix + ")";
        String signedNonNumber = "(([-+]?"+nonNumber+")|" +
                                 positiveNonNumber + "|" +
                                 negativeNonNumber + ")";
        floatPattern = Pattern.compile(decimal + "|" + hexFloat + "|" +
                                       signedNonNumber);
        decimalPattern = Pattern.compile(decimal);
    }
    private Pattern floatPattern() {
        if (floatPattern == null) {
            buildFloatAndDecimalPattern();
        }
        return floatPattern;
    }
    private Pattern decimalPattern() {
        if (decimalPattern == null) {
            buildFloatAndDecimalPattern();
        }
        return decimalPattern;
    }

    // Constructors

    /**
     * Constructs a <code>Scanner</code> that returns values scanned
     * from the specified source delimited by the specified pattern.
     *
     * @param  source A character source implementing the Readable interface
     * @param pattern A delimiting pattern
     * @return A scanner with the specified source and pattern
     */
    private Scanner(Readable source, Pattern pattern) {
        if (source == null)
            throw new NullPointerException("source");
        if (pattern == null)
            throw new NullPointerException("pattern");
        this.source = source;
        delimPattern = pattern;
        buf = CharBuffer.allocate(BUFFER_SIZE);
        buf.limit(0);
        matcher = delimPattern.matcher(buf);
        matcher.useTransparentBounds(true);
        matcher.useAnchoringBounds(false);
        useLocale(Locale.getDefault(Locale.Category.FORMAT));
    }

    /**
     * Constructs a new <code>Scanner</code> that produces values scanned
     * from the specified source.
     *
     * @param  source A character source implementing the {@link Readable}
     *         interface
     */
    public Scanner(Readable source) {
        this(source, WHITESPACE_PATTERN);
    }

    /**
     * Constructs a new <code>Scanner</code> that produces values scanned
     * from the specified input stream. Bytes from the stream are converted
     * into characters using the underlying platform's
     * {@linkplain java.nio.charset.Charset#defaultCharset() default charset}.
     *
     * @param  source An input stream to be scanned
     */
    public Scanner(InputStream source) {
        this(new InputStreamReader(source), WHITESPACE_PATTERN);
    }

    /**
     * Constructs a new <code>Scanner</code> that produces values scanned
     * from the specified input stream. Bytes from the stream are converted
     * into characters using the specified charset.
     *
     * @param  source An input stream to be scanned
     * @param charsetName The encoding type used to convert bytes from the
     *        stream into characters to be scanned
     * @throws IllegalArgumentException if the specified character set
     *         does not exist
     */
    public Scanner(InputStream source, String charsetName) {
        this(makeReadable(source, charsetName), WHITESPACE_PATTERN);
    }

    private static Readable makeReadable(InputStream source,
                                         String charsetName)
    {
        if (source == null)
            throw new NullPointerException("source");
        InputStreamReader isr = null;
        try {
            isr = new InputStreamReader(source, charsetName);
        } catch (UnsupportedEncodingException uee) {
            IllegalArgumentException iae = new IllegalArgumentException();
            iae.initCause(uee);
            throw iae;
        }
        return isr;
    }

    /**
     * Constructs a new <code>Scanner</code> that produces values scanned
     * from the specified file. Bytes from the file are converted into
     * characters using the underlying platform's
     * {@linkplain java.nio.charset.Charset#defaultCharset() default charset}.
     *
     * @param  source A file to be scanned
     * @throws FileNotFoundException if source is not found
     */
    public Scanner(File source)
        throws FileNotFoundException
    {
        this((ReadableByteChannel)(new FileInputStream(source).getChannel()));
    }

    /**
     * Constructs a new <code>Scanner</code> that produces values scanned
     * from the specified file. Bytes from the file are converted into
     * characters using the specified charset.
     *
     * @param  source A file to be scanned
     * @param charsetName The encoding type used to convert bytes from the file
     *        into characters to be scanned
     * @throws FileNotFoundException if source is not found
     * @throws IllegalArgumentException if the specified encoding is
     *         not found
     */
    public Scanner(File source, String charsetName)
        throws FileNotFoundException
    {
        this((ReadableByteChannel)(new FileInputStream(source).getChannel()),
             charsetName);
    }

    /**
     * Constructs a new <code>Scanner</code> that produces values scanned
     * from the specified file. Bytes from the file are converted into
     * characters using the underlying platform's
     * {@linkplain java.nio.charset.Charset#defaultCharset() default charset}.
     *
     * @param   source
     *          A file to be scanned
     * @throws  IOException
     *          if an I/O error occurs opening source
     *
     * @since   1.7
     */
    public Scanner(FileRef source)
        throws IOException
    {
        this(source.newInputStream());
    }

    /**
     * Constructs a new <code>Scanner</code> that produces values scanned
     * from the specified file. Bytes from the file are converted into
     * characters using the specified charset.
     *
     * @param   source
     *          A file to be scanned
     * @param   charsetName
     *          The encoding type used to convert bytes from the file
     *          into characters to be scanned
     * @throws  IOException
     *          if an I/O error occurs opening source
     * @throws  IllegalArgumentException
     *          if the specified encoding is not found
     * @since   1.7
     */
    public Scanner(FileRef source, String charsetName)
        throws IOException
    {
        this(source.newInputStream(), charsetName);
    }

    /**
     * Constructs a new <code>Scanner</code> that produces values scanned
     * from the specified string.
     *
     * @param  source A string to scan
     */
    public Scanner(String source) {
        this(new StringReader(source), WHITESPACE_PATTERN);
    }

    /**
     * Constructs a new <code>Scanner</code> that produces values scanned
     * from the specified channel. Bytes from the source are converted into
     * characters using the underlying platform's
     * {@linkplain java.nio.charset.Charset#defaultCharset() default charset}.
     *
     * @param  source A channel to scan
     */
    public Scanner(ReadableByteChannel source) {
        this(makeReadable(source), WHITESPACE_PATTERN);
    }

    private static Readable makeReadable(ReadableByteChannel source) {
        if (source == null)
            throw new NullPointerException("source");
        String defaultCharsetName =
            java.nio.charset.Charset.defaultCharset().name();
        return Channels.newReader(source,
                           java.nio.charset.Charset.defaultCharset().name());
    }

    /**
     * Constructs a new <code>Scanner</code> that produces values scanned
     * from the specified channel. Bytes from the source are converted into
     * characters using the specified charset.
     *
     * @param  source A channel to scan
     * @param charsetName The encoding type used to convert bytes from the
     *        channel into characters to be scanned
     * @throws IllegalArgumentException if the specified character set
     *         does not exist
     */
    public Scanner(ReadableByteChannel source, String charsetName) {
        this(makeReadable(source, charsetName), WHITESPACE_PATTERN);
    }

    private static Readable makeReadable(ReadableByteChannel source,
                                  String charsetName)
    {
        if (source == null)
            throw new NullPointerException("source");
        if (!Charset.isSupported(charsetName))
            throw new IllegalArgumentException(charsetName);
        return Channels.newReader(source, charsetName);
    }

    // Private primitives used to support scanning

    private void saveState() {
        savedScannerPosition = position;
    }

    private void revertState() {
        this.position = savedScannerPosition;
        savedScannerPosition = -1;
        skipped = false;
    }

    private boolean revertState(boolean b) {
        this.position = savedScannerPosition;
        savedScannerPosition = -1;
        skipped = false;
        return b;
    }

    private void cacheResult() {
        hasNextResult = matcher.group();
        hasNextPosition = matcher.end();
        hasNextPattern = matcher.pattern();
    }

    private void cacheResult(String result) {
        hasNextResult = result;
        hasNextPosition = matcher.end();
        hasNextPattern = matcher.pattern();
    }

    // Clears both regular cache and type cache
    private void clearCaches() {
        hasNextPattern = null;
        typeCache = null;
    }

    // Also clears both the regular cache and the type cache
    private String getCachedResult() {
        position = hasNextPosition;
        hasNextPattern = null;
        typeCache = null;
        return hasNextResult;
    }

    // Also clears both the regular cache and the type cache
    private void useTypeCache() {
        if (closed)
            throw new IllegalStateException("Scanner closed");
        position = hasNextPosition;
        hasNextPattern = null;
        typeCache = null;
    }

    // Tries to read more input. May block.
    private void readInput() {
        if (buf.limit() == buf.capacity())
            makeSpace();

        // Prepare to receive data
        int p = buf.position();
        buf.position(buf.limit());
        buf.limit(buf.capacity());

        int n = 0;
        try {
            n = source.read(buf);
        } catch (IOException ioe) {
            lastException = ioe;
            n = -1;
        }

        if (n == -1) {
            sourceClosed = true;
            needInput = false;
        }

        if (n > 0)
            needInput = false;

        // Restore current position and limit for reading
        buf.limit(buf.position());
        buf.position(p);
    }

    // After this method is called there will either be an exception
    // or else there will be space in the buffer
    private boolean makeSpace() {
        clearCaches();
        int offset = savedScannerPosition == -1 ?
            position : savedScannerPosition;
        buf.position(offset);
        // Gain space by compacting buffer
        if (offset > 0) {
            buf.compact();
            translateSavedIndexes(offset);
            position -= offset;
            buf.flip();
            return true;
        }
        // Gain space by growing buffer
        int newSize = buf.capacity() * 2;
        CharBuffer newBuf = CharBuffer.allocate(newSize);
        newBuf.put(buf);
        newBuf.flip();
        translateSavedIndexes(offset);
        position -= offset;
        buf = newBuf;
        matcher.reset(buf);
        return true;
    }

    // When a buffer compaction/reallocation occurs the saved indexes must
    // be modified appropriately
    private void translateSavedIndexes(int offset) {
        if (savedScannerPosition != -1)
            savedScannerPosition -= offset;
    }

    // If we are at the end of input then NoSuchElement;
    // If there is still input left then InputMismatch
    private void throwFor() {
        skipped = false;
        if ((sourceClosed) && (position == buf.limit()))
            throw new NoSuchElementException();
        else
            throw new InputMismatchException();
    }

    // Returns true if a complete token or partial token is in the buffer.
    // It is not necessary to find a complete token since a partial token
    // means that there will be another token with or without more input.
    private boolean hasTokenInBuffer() {
        matchValid = false;
        matcher.usePattern(delimPattern);
        matcher.region(position, buf.limit());

        // Skip delims first
        if (matcher.lookingAt())
            position = matcher.end();

        // If we are sitting at the end, no more tokens in buffer
        if (position == buf.limit())
            return false;

        return true;
    }

    /*
     * Returns a "complete token" that matches the specified pattern
     *
     * A token is complete if surrounded by delims; a partial token
     * is prefixed by delims but not postfixed by them
     *
     * The position is advanced to the end of that complete token
     *
     * Pattern == null means accept any token at all
     *
     * Triple return:
     * 1. valid string means it was found
     * 2. null with needInput=false means we won't ever find it
     * 3. null with needInput=true means try again after readInput
     */
    private String getCompleteTokenInBuffer(Pattern pattern) {
        matchValid = false;

        // Skip delims first
        matcher.usePattern(delimPattern);
        if (!skipped) { // Enforcing only one skip of leading delims
            matcher.region(position, buf.limit());
            if (matcher.lookingAt()) {
                // If more input could extend the delimiters then we must wait
                // for more input
                if (matcher.hitEnd() && !sourceClosed) {
                    needInput = true;
                    return null;
                }
                // The delims were whole and the matcher should skip them
                skipped = true;
                position = matcher.end();
            }
        }

        // If we are sitting at the end, no more tokens in buffer
        if (position == buf.limit()) {
            if (sourceClosed)
                return null;
            needInput = true;
            return null;
        }

        // Must look for next delims. Simply attempting to match the
        // pattern at this point may find a match but it might not be
        // the first longest match because of missing input, or it might
        // match a partial token instead of the whole thing.

        // Then look for next delims
        matcher.region(position, buf.limit());
        boolean foundNextDelim = matcher.find();
        if (foundNextDelim && (matcher.end() == position)) {
            // Zero length delimiter match; we should find the next one
            // using the automatic advance past a zero length match;
            // Otherwise we have just found the same one we just skipped
            foundNextDelim = matcher.find();
        }
        if (foundNextDelim) {
            // In the rare case that more input could cause the match
            // to be lost and there is more input coming we must wait
            // for more input. Note that hitting the end is okay as long
            // as the match cannot go away. It is the beginning of the
            // next delims we want to be sure about, we don't care if
            // they potentially extend further.
            if (matcher.requireEnd() && !sourceClosed) {
                needInput = true;
                return null;
            }
            int tokenEnd = matcher.start();
            // There is a complete token.
            if (pattern == null) {
                // Must continue with match to provide valid MatchResult
                pattern = FIND_ANY_PATTERN;
            }
            //  Attempt to match against the desired pattern
            matcher.usePattern(pattern);
            matcher.region(position, tokenEnd);
            if (matcher.matches()) {
                String s = matcher.group();
                position = matcher.end();
                return s;
            } else { // Complete token but it does not match
                return null;
            }
        }

        // If we can't find the next delims but no more input is coming,
        // then we can treat the remainder as a whole token
        if (sourceClosed) {
            if (pattern == null) {
                // Must continue with match to provide valid MatchResult
                pattern = FIND_ANY_PATTERN;
            }
            // Last token; Match the pattern here or throw
            matcher.usePattern(pattern);
            matcher.region(position, buf.limit());
            if (matcher.matches()) {
                String s = matcher.group();
                position = matcher.end();
                return s;
            }
            // Last piece does not match
            return null;
        }

        // There is a partial token in the buffer; must read more
        // to complete it
        needInput = true;
        return null;
    }

    // Finds the specified pattern in the buffer up to horizon.
    // Returns a match for the specified input pattern.
    private String findPatternInBuffer(Pattern pattern, int horizon) {
        matchValid = false;
        matcher.usePattern(pattern);
        int bufferLimit = buf.limit();
        int horizonLimit = -1;
        int searchLimit = bufferLimit;
        if (horizon > 0) {
            horizonLimit = position + horizon;
            if (horizonLimit < bufferLimit)
                searchLimit = horizonLimit;
        }
        matcher.region(position, searchLimit);
        if (matcher.find()) {
            if (matcher.hitEnd() && (!sourceClosed)) {
                // The match may be longer if didn't hit horizon or real end
                if (searchLimit != horizonLimit) {
                     // Hit an artificial end; try to extend the match
                    needInput = true;
                    return null;
                }
                // The match could go away depending on what is next
                if ((searchLimit == horizonLimit) && matcher.requireEnd()) {
                    // Rare case: we hit the end of input and it happens
                    // that it is at the horizon and the end of input is
                    // required for the match.
                    needInput = true;
                    return null;
                }
            }
            // Did not hit end, or hit real end, or hit horizon
            position = matcher.end();
            return matcher.group();
        }

        if (sourceClosed)
            return null;

        // If there is no specified horizon, or if we have not searched
        // to the specified horizon yet, get more input
        if ((horizon == 0) || (searchLimit != horizonLimit))
            needInput = true;
        return null;
    }

    // Returns a match for the specified input pattern anchored at
    // the current position
    private String matchPatternInBuffer(Pattern pattern) {
        matchValid = false;
        matcher.usePattern(pattern);
        matcher.region(position, buf.limit());
        if (matcher.lookingAt()) {
            if (matcher.hitEnd() && (!sourceClosed)) {
                // Get more input and try again
                needInput = true;
                return null;
            }
            position = matcher.end();
            return matcher.group();
        }

        if (sourceClosed)
            return null;

        // Read more to find pattern
        needInput = true;
        return null;
    }

    // Throws if the scanner is closed
    private void ensureOpen() {
        if (closed)
            throw new IllegalStateException("Scanner closed");
    }

    // Public methods

    /**
     * Closes this scanner.
     *
     * <p> If this scanner has not yet been closed then if its underlying
     * {@linkplain java.lang.Readable readable} also implements the {@link
     * java.io.Closeable} interface then the readable's <tt>close</tt> method
     * will be invoked.  If this scanner is already closed then invoking this
     * method will have no effect.
     *
     * <p>Attempting to perform search operations after a scanner has
     * been closed will result in an {@link IllegalStateException}.
     *
     */
    public void close() {
        if (closed)
            return;
        if (source instanceof Closeable) {
            try {
                ((Closeable)source).close();
            } catch (IOException ioe) {
                lastException = ioe;
            }
        }
        sourceClosed = true;
        source = null;
        closed = true;
    }

    /**
     * Returns the <code>IOException</code> last thrown by this
     * <code>Scanner</code>'s underlying <code>Readable</code>. This method
     * returns <code>null</code> if no such exception exists.
     *
     * @return the last exception thrown by this scanner's readable
     */
    public IOException ioException() {
        return lastException;
    }

    /**
     * Returns the <code>Pattern</code> this <code>Scanner</code> is currently
     * using to match delimiters.
     *
     * @return this scanner's delimiting pattern.
     */
    public Pattern delimiter() {
        return delimPattern;
    }

    /**
     * Sets this scanner's delimiting pattern to the specified pattern.
     *
     * @param pattern A delimiting pattern
     * @return this scanner
     */
    public Scanner useDelimiter(Pattern pattern) {
        delimPattern = pattern;
        return this;
    }

    /**
     * Sets this scanner's delimiting pattern to a pattern constructed from
     * the specified <code>String</code>.
     *
     * <p> An invocation of this method of the form
     * <tt>useDelimiter(pattern)</tt> behaves in exactly the same way as the
     * invocation <tt>useDelimiter(Pattern.compile(pattern))</tt>.
     *
     * <p> Invoking the {@link #reset} method will set the scanner's delimiter
     * to the <a href= "#default-delimiter">default</a>.
     *
     * @param pattern A string specifying a delimiting pattern
     * @return this scanner
     */
    public Scanner useDelimiter(String pattern) {
        delimPattern = patternCache.forName(pattern);
        return this;
    }

    /**
     * Returns this scanner's locale.
     *
     * <p>A scanner's locale affects many elements of its default
     * primitive matching regular expressions; see
     * <a href= "#localized-numbers">localized numbers</a> above.
     *
     * @return this scanner's locale
     */
    public Locale locale() {
        return this.locale;
    }

    /**
     * Sets this scanner's locale to the specified locale.
     *
     * <p>A scanner's locale affects many elements of its default
     * primitive matching regular expressions; see
     * <a href= "#localized-numbers">localized numbers</a> above.
     *
     * <p>Invoking the {@link #reset} method will set the scanner's locale to
     * the <a href= "#initial-locale">initial locale</a>.
     *
     * @param locale A string specifying the locale to use
     * @return this scanner
     */
    public Scanner useLocale(Locale locale) {
        if (locale.equals(this.locale))
            return this;

        this.locale = locale;
        DecimalFormat df =
            (DecimalFormat)NumberFormat.getNumberInstance(locale);
        DecimalFormatSymbols dfs = DecimalFormatSymbols.getInstance(locale);

        // These must be literalized to avoid collision with regex
        // metacharacters such as dot or parenthesis
        groupSeparator =   "\\" + dfs.getGroupingSeparator();
        decimalSeparator = "\\" + dfs.getDecimalSeparator();

        // Quoting the nonzero length locale-specific things
        // to avoid potential conflict with metacharacters
        nanString = "\\Q" + dfs.getNaN() + "\\E";
        infinityString = "\\Q" + dfs.getInfinity() + "\\E";
        positivePrefix = df.getPositivePrefix();
        if (positivePrefix.length() > 0)
            positivePrefix = "\\Q" + positivePrefix + "\\E";
        negativePrefix = df.getNegativePrefix();
        if (negativePrefix.length() > 0)
            negativePrefix = "\\Q" + negativePrefix + "\\E";
        positiveSuffix = df.getPositiveSuffix();
        if (positiveSuffix.length() > 0)
            positiveSuffix = "\\Q" + positiveSuffix + "\\E";
        negativeSuffix = df.getNegativeSuffix();
        if (negativeSuffix.length() > 0)
            negativeSuffix = "\\Q" + negativeSuffix + "\\E";

        // Force rebuilding and recompilation of locale dependent
        // primitive patterns
        integerPattern = null;
        floatPattern = null;

        return this;
    }

    /**
     * Returns this scanner's default radix.
     *
     * <p>A scanner's radix affects elements of its default
     * number matching regular expressions; see
     * <a href= "#localized-numbers">localized numbers</a> above.
     *
     * @return the default radix of this scanner
     */
    public int radix() {
        return this.defaultRadix;
    }

    /**
     * Sets this scanner's default radix to the specified radix.
     *
     * <p>A scanner's radix affects elements of its default
     * number matching regular expressions; see
     * <a href= "#localized-numbers">localized numbers</a> above.
     *
     * <p>If the radix is less than <code>Character.MIN_RADIX</code>
     * or greater than <code>Character.MAX_RADIX</code>, then an
     * <code>IllegalArgumentException</code> is thrown.
     *
     * <p>Invoking the {@link #reset} method will set the scanner's radix to
     * <code>10</code>.
     *
     * @param radix The radix to use when scanning numbers
     * @return this scanner
     * @throws IllegalArgumentException if radix is out of range
     */
    public Scanner useRadix(int radix) {
        if ((radix < Character.MIN_RADIX) || (radix > Character.MAX_RADIX))
            throw new IllegalArgumentException("radix:"+radix);

        if (this.defaultRadix == radix)
            return this;
        this.defaultRadix = radix;
        // Force rebuilding and recompilation of radix dependent patterns
        integerPattern = null;
        return this;
    }

    // The next operation should occur in the specified radix but
    // the default is left untouched.
    private void setRadix(int radix) {
        if (this.radix != radix) {
            // Force rebuilding and recompilation of radix dependent patterns
            integerPattern = null;
            this.radix = radix;
        }
    }

    /**
     * Returns the match result of the last scanning operation performed
     * by this scanner. This method throws <code>IllegalStateException</code>
     * if no match has been performed, or if the last match was
     * not successful.
     *
     * <p>The various <code>next</code>methods of <code>Scanner</code>
     * make a match result available if they complete without throwing an
     * exception. For instance, after an invocation of the {@link #nextInt}
     * method that returned an int, this method returns a
     * <code>MatchResult</code> for the search of the
     * <a href="#Integer-regex"><i>Integer</i></a> regular expression
     * defined above. Similarly the {@link #findInLine},
     * {@link #findWithinHorizon}, and {@link #skip} methods will make a
     * match available if they succeed.
     *
     * @return a match result for the last match operation
     * @throws IllegalStateException  If no match result is available
     */
    public MatchResult match() {
        if (!matchValid)
            throw new IllegalStateException("No match result available");
        return matcher.toMatchResult();
    }

    /**
     * <p>Returns the string representation of this <code>Scanner</code>. The
     * string representation of a <code>Scanner</code> contains information
     * that may be useful for debugging. The exact format is unspecified.
     *
     * @return  The string representation of this scanner
     */
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("java.util.Scanner");
        sb.append("[delimiters=" + delimPattern + "]");
        sb.append("[position=" + position + "]");
        sb.append("[match valid=" + matchValid + "]");
        sb.append("[need input=" + needInput + "]");
        sb.append("[source closed=" + sourceClosed + "]");
        sb.append("[skipped=" + skipped + "]");
        sb.append("[group separator=" + groupSeparator + "]");
        sb.append("[decimal separator=" + decimalSeparator + "]");
        sb.append("[positive prefix=" + positivePrefix + "]");
        sb.append("[negative prefix=" + negativePrefix + "]");
        sb.append("[positive suffix=" + positiveSuffix + "]");
        sb.append("[negative suffix=" + negativeSuffix + "]");
        sb.append("[NaN string=" + nanString + "]");
        sb.append("[infinity string=" + infinityString + "]");
        return sb.toString();
    }

    /**
     * Returns true if this scanner has another token in its input.
     * This method may block while waiting for input to scan.
     * The scanner does not advance past any input.
     *
     * @return true if and only if this scanner has another token
     * @throws IllegalStateException if this scanner is closed
     * @see java.util.Iterator
     */
    public boolean hasNext() {
        ensureOpen();
        saveState();
        while (!sourceClosed) {
            if (hasTokenInBuffer())
                return revertState(true);
            readInput();
        }
        boolean result = hasTokenInBuffer();
        return revertState(result);
    }

    /**
     * Finds and returns the next complete token from this scanner.
     * A complete token is preceded and followed by input that matches
     * the delimiter pattern. This method may block while waiting for input
     * to scan, even if a previous invocation of {@link #hasNext} returned
     * <code>true</code>.
     *
     * @return the next token
     * @throws NoSuchElementException if no more tokens are available
     * @throws IllegalStateException if this scanner is closed
     * @see java.util.Iterator
     */
    public String next() {
        ensureOpen();
        clearCaches();

        while (true) {
            String token = getCompleteTokenInBuffer(null);
            if (token != null) {
                matchValid = true;
                skipped = false;
                return token;
            }
            if (needInput)
                readInput();
            else
                throwFor();
        }
    }

    /**
     * The remove operation is not supported by this implementation of
     * <code>Iterator</code>.
     *
     * @throws UnsupportedOperationException if this method is invoked.
     * @see java.util.Iterator
     */
    public void remove() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns true if the next token matches the pattern constructed from the
     * specified string. The scanner does not advance past any input.
     *
     * <p> An invocation of this method of the form <tt>hasNext(pattern)</tt>
     * behaves in exactly the same way as the invocation
     * <tt>hasNext(Pattern.compile(pattern))</tt>.
     *
     * @param pattern a string specifying the pattern to scan
     * @return true if and only if this scanner has another token matching
     *         the specified pattern
     * @throws IllegalStateException if this scanner is closed
     */
    public boolean hasNext(String pattern)  {
        return hasNext(patternCache.forName(pattern));
    }

    /**
     * Returns the next token if it matches the pattern constructed from the
     * specified string.  If the match is successful, the scanner advances
     * past the input that matched the pattern.
     *
     * <p> An invocation of this method of the form <tt>next(pattern)</tt>
     * behaves in exactly the same way as the invocation
     * <tt>next(Pattern.compile(pattern))</tt>.
     *
     * @param pattern a string specifying the pattern to scan
     * @return the next token
     * @throws NoSuchElementException if no such tokens are available
     * @throws IllegalStateException if this scanner is closed
     */
    public String next(String pattern)  {
        return next(patternCache.forName(pattern));
    }

    /**
     * Returns true if the next complete token matches the specified pattern.
     * A complete token is prefixed and postfixed by input that matches
     * the delimiter pattern. This method may block while waiting for input.
     * The scanner does not advance past any input.
     *
     * @param pattern the pattern to scan for
     * @return true if and only if this scanner has another token matching
     *         the specified pattern
     * @throws IllegalStateException if this scanner is closed
     */
    public boolean hasNext(Pattern pattern) {
        ensureOpen();
        if (pattern == null)
            throw new NullPointerException();
        hasNextPattern = null;
        saveState();

        while (true) {
            if (getCompleteTokenInBuffer(pattern) != null) {
                matchValid = true;
                cacheResult();
                return revertState(true);
            }
            if (needInput)
                readInput();
            else
                return revertState(false);
        }
    }

    /**
     * Returns the next token if it matches the specified pattern. This
     * method may block while waiting for input to scan, even if a previous
     * invocation of {@link #hasNext(Pattern)} returned <code>true</code>.
     * If the match is successful, the scanner advances past the input that
     * matched the pattern.
     *
     * @param pattern the pattern to scan for
     * @return the next token
     * @throws NoSuchElementException if no more tokens are available
     * @throws IllegalStateException if this scanner is closed
     */
    public String next(Pattern pattern) {
        ensureOpen();
        if (pattern == null)
            throw new NullPointerException();

        // Did we already find this pattern?
        if (hasNextPattern == pattern)
            return getCachedResult();
        clearCaches();

        // Search for the pattern
        while (true) {
            String token = getCompleteTokenInBuffer(pattern);
            if (token != null) {
                matchValid = true;
                skipped = false;
                return token;
            }
            if (needInput)
                readInput();
            else
                throwFor();
        }
    }

    /**
     * Returns true if there is another line in the input of this scanner.
     * This method may block while waiting for input. The scanner does not
     * advance past any input.
     *
     * @return true if and only if this scanner has another line of input
     * @throws IllegalStateException if this scanner is closed
     */
    public boolean hasNextLine() {
        saveState();

        String result = findWithinHorizon(linePattern(), 0);
        if (result != null) {
            MatchResult mr = this.match();
            String lineSep = mr.group(1);
            if (lineSep != null) {
                result = result.substring(0, result.length() -
                                          lineSep.length());
                cacheResult(result);

            } else {
                cacheResult();
            }
        }
        revertState();
        return (result != null);
    }

    /**
     * Advances this scanner past the current line and returns the input
     * that was skipped.
     *
     * This method returns the rest of the current line, excluding any line
     * separator at the end. The position is set to the beginning of the next
     * line.
     *
     * <p>Since this method continues to search through the input looking
     * for a line separator, it may buffer all of the input searching for
     * the line to skip if no line separators are present.
     *
     * @return the line that was skipped
     * @throws NoSuchElementException if no line was found
     * @throws IllegalStateException if this scanner is closed
     */
    public String nextLine() {
        if (hasNextPattern == linePattern())
            return getCachedResult();
        clearCaches();

        String result = findWithinHorizon(linePattern, 0);
        if (result == null)
            throw new NoSuchElementException("No line found");
        MatchResult mr = this.match();
        String lineSep = mr.group(1);
        if (lineSep != null)
            result = result.substring(0, result.length() - lineSep.length());
        if (result == null)
            throw new NoSuchElementException();
        else
            return result;
    }

    // Public methods that ignore delimiters

    /**
     * Attempts to find the next occurrence of a pattern constructed from the
     * specified string, ignoring delimiters.
     *
     * <p>An invocation of this method of the form <tt>findInLine(pattern)</tt>
     * behaves in exactly the same way as the invocation
     * <tt>findInLine(Pattern.compile(pattern))</tt>.
     *
     * @param pattern a string specifying the pattern to search for
     * @return the text that matched the specified pattern
     * @throws IllegalStateException if this scanner is closed
     */
    public String findInLine(String pattern) {
        return findInLine(patternCache.forName(pattern));
    }

    /**
     * Attempts to find the next occurrence of the specified pattern ignoring
     * delimiters. If the pattern is found before the next line separator, the
     * scanner advances past the input that matched and returns the string that
     * matched the pattern.
     * If no such pattern is detected in the input up to the next line
     * separator, then <code>null</code> is returned and the scanner's
     * position is unchanged. This method may block waiting for input that
     * matches the pattern.
     *
     * <p>Since this method continues to search through the input looking
     * for the specified pattern, it may buffer all of the input searching for
     * the desired token if no line separators are present.
     *
     * @param pattern the pattern to scan for
     * @return the text that matched the specified pattern
     * @throws IllegalStateException if this scanner is closed
     */
    public String findInLine(Pattern pattern) {
        ensureOpen();
        if (pattern == null)
            throw new NullPointerException();
        clearCaches();
        // Expand buffer to include the next newline or end of input
        int endPosition = 0;
        saveState();
        while (true) {
            String token = findPatternInBuffer(separatorPattern(), 0);
            if (token != null) {
                endPosition = matcher.start();
                break; // up to next newline
            }
            if (needInput) {
                readInput();
            } else {
                endPosition = buf.limit();
                break; // up to end of input
            }
        }
        revertState();
        int horizonForLine = endPosition - position;
        // If there is nothing between the current pos and the next
        // newline simply return null, invoking findWithinHorizon
        // with "horizon=0" will scan beyond the line bound.
        if (horizonForLine == 0)
            return null;
        // Search for the pattern
        return findWithinHorizon(pattern, horizonForLine);
    }

    /**
     * Attempts to find the next occurrence of a pattern constructed from the
     * specified string, ignoring delimiters.
     *
     * <p>An invocation of this method of the form
     * <tt>findWithinHorizon(pattern)</tt> behaves in exactly the same way as
     * the invocation
     * <tt>findWithinHorizon(Pattern.compile(pattern, horizon))</tt>.
     *
     * @param pattern a string specifying the pattern to search for
     * @return the text that matched the specified pattern
     * @throws IllegalStateException if this scanner is closed
     * @throws IllegalArgumentException if horizon is negative
     */
    public String findWithinHorizon(String pattern, int horizon) {
        return findWithinHorizon(patternCache.forName(pattern), horizon);
    }

    /**
     * Attempts to find the next occurrence of the specified pattern.
     *
     * <p>This method searches through the input up to the specified
     * search horizon, ignoring delimiters. If the pattern is found the
     * scanner advances past the input that matched and returns the string
     * that matched the pattern. If no such pattern is detected then the
     * null is returned and the scanner's position remains unchanged. This
     * method may block waiting for input that matches the pattern.
     *
     * <p>A scanner will never search more than <code>horizon</code> code
     * points beyond its current position. Note that a match may be clipped
     * by the horizon; that is, an arbitrary match result may have been
     * different if the horizon had been larger. The scanner treats the
     * horizon as a transparent, non-anchoring bound (see {@link
     * Matcher#useTransparentBounds} and {@link Matcher#useAnchoringBounds}).
     *
     * <p>If horizon is <code>0</code>, then the horizon is ignored and
     * this method continues to search through the input looking for the
     * specified pattern without bound. In this case it may buffer all of
     * the input searching for the pattern.
     *
     * <p>If horizon is negative, then an IllegalArgumentException is
     * thrown.
     *
     * @param pattern the pattern to scan for
     * @return the text that matched the specified pattern
     * @throws IllegalStateException if this scanner is closed
     * @throws IllegalArgumentException if horizon is negative
     */
    public String findWithinHorizon(Pattern pattern, int horizon) {
        ensureOpen();
        if (pattern == null)
            throw new NullPointerException();
        if (horizon < 0)
            throw new IllegalArgumentException("horizon < 0");
        clearCaches();

        // Search for the pattern
        while (true) {
            String token = findPatternInBuffer(pattern, horizon);
            if (token != null) {
                matchValid = true;
                return token;
            }
            if (needInput)
                readInput();
            else
                break; // up to end of input
        }
        return null;
    }

    /**
     * Skips input that matches the specified pattern, ignoring delimiters.
     * This method will skip input if an anchored match of the specified
     * pattern succeeds.
     *
     * <p>If a match to the specified pattern is not found at the
     * current position, then no input is skipped and a
     * <tt>NoSuchElementException</tt> is thrown.
     *
     * <p>Since this method seeks to match the specified pattern starting at
     * the scanner's current position, patterns that can match a lot of
     * input (".*", for example) may cause the scanner to buffer a large
     * amount of input.
     *
     * <p>Note that it is possible to skip something without risking a
     * <code>NoSuchElementException</code> by using a pattern that can
     * match nothing, e.g., <code>sc.skip("[ \t]*")</code>.
     *
     * @param pattern a string specifying the pattern to skip over
     * @return this scanner
     * @throws NoSuchElementException if the specified pattern is not found
     * @throws IllegalStateException if this scanner is closed
     */
    public Scanner skip(Pattern pattern) {
        ensureOpen();
        if (pattern == null)
            throw new NullPointerException();
        clearCaches();

        // Search for the pattern
        while (true) {
            String token = matchPatternInBuffer(pattern);
            if (token != null) {
                matchValid = true;
                position = matcher.end();
                return this;
            }
            if (needInput)
                readInput();
            else
                throw new NoSuchElementException();
        }
    }

    /**
     * Skips input that matches a pattern constructed from the specified
     * string.
     *
     * <p> An invocation of this method of the form <tt>skip(pattern)</tt>
     * behaves in exactly the same way as the invocation
     * <tt>skip(Pattern.compile(pattern))</tt>.
     *
     * @param pattern a string specifying the pattern to skip over
     * @return this scanner
     * @throws IllegalStateException if this scanner is closed
     */
    public Scanner skip(String pattern) {
        return skip(patternCache.forName(pattern));
    }

    // Convenience methods for scanning primitives

    /**
     * Returns true if the next token in this scanner's input can be
     * interpreted as a boolean value using a case insensitive pattern
     * created from the string "true|false".  The scanner does not
     * advance past the input that matched.
     *
     * @return true if and only if this scanner's next token is a valid
     *         boolean value
     * @throws IllegalStateException if this scanner is closed
     */
    public boolean hasNextBoolean()  {
        return hasNext(boolPattern());
    }

    /**
     * Scans the next token of the input into a boolean value and returns
     * that value. This method will throw <code>InputMismatchException</code>
     * if the next token cannot be translated into a valid boolean value.
     * If the match is successful, the scanner advances past the input that
     * matched.
     *
     * @return the boolean scanned from the input
     * @throws InputMismatchException if the next token is not a valid boolean
     * @throws NoSuchElementException if input is exhausted
     * @throws IllegalStateException if this scanner is closed
     */
    public boolean nextBoolean()  {
        clearCaches();
        return Boolean.parseBoolean(next(boolPattern()));
    }

    /**
     * Returns true if the next token in this scanner's input can be
     * interpreted as a byte value in the default radix using the
     * {@link #nextByte} method. The scanner does not advance past any input.
     *
     * @return true if and only if this scanner's next token is a valid
     *         byte value
     * @throws IllegalStateException if this scanner is closed
     */
    public boolean hasNextByte() {
        return hasNextByte(defaultRadix);
    }

    /**
     * Returns true if the next token in this scanner's input can be
     * interpreted as a byte value in the specified radix using the
     * {@link #nextByte} method. The scanner does not advance past any input.
     *
     * @param radix the radix used to interpret the token as a byte value
     * @return true if and only if this scanner's next token is a valid
     *         byte value
     * @throws IllegalStateException if this scanner is closed
     */
    public boolean hasNextByte(int radix) {
        setRadix(radix);
        boolean result = hasNext(integerPattern());
        if (result) { // Cache it
            try {
                String s = (matcher.group(SIMPLE_GROUP_INDEX) == null) ?
                    processIntegerToken(hasNextResult) :
                    hasNextResult;
                typeCache = Byte.parseByte(s, radix);
            } catch (NumberFormatException nfe) {
                result = false;
            }
        }
        return result;
    }

    /**
     * Scans the next token of the input as a <tt>byte</tt>.
     *
     * <p> An invocation of this method of the form
     * <tt>nextByte()</tt> behaves in exactly the same way as the
     * invocation <tt>nextByte(radix)</tt>, where <code>radix</code>
     * is the default radix of this scanner.
     *
     * @return the <tt>byte</tt> scanned from the input
     * @throws InputMismatchException
     *         if the next token does not match the <i>Integer</i>
     *         regular expression, or is out of range
     * @throws NoSuchElementException if input is exhausted
     * @throws IllegalStateException if this scanner is closed
     */
    public byte nextByte() {
         return nextByte(defaultRadix);
    }

    /**
     * Scans the next token of the input as a <tt>byte</tt>.
     * This method will throw <code>InputMismatchException</code>
     * if the next token cannot be translated into a valid byte value as
     * described below. If the translation is successful, the scanner advances
     * past the input that matched.
     *
     * <p> If the next token matches the <a
     * href="#Integer-regex"><i>Integer</i></a> regular expression defined
     * above then the token is converted into a <tt>byte</tt> value as if by
     * removing all locale specific prefixes, group separators, and locale
     * specific suffixes, then mapping non-ASCII digits into ASCII
     * digits via {@link Character#digit Character.digit}, prepending a
     * negative sign (-) if the locale specific negative prefixes and suffixes
     * were present, and passing the resulting string to
     * {@link Byte#parseByte(String, int) Byte.parseByte} with the
     * specified radix.
     *
     * @param radix the radix used to interpret the token as a byte value
     * @return the <tt>byte</tt> scanned from the input
     * @throws InputMismatchException
     *         if the next token does not match the <i>Integer</i>
     *         regular expression, or is out of range
     * @throws NoSuchElementException if input is exhausted
     * @throws IllegalStateException if this scanner is closed
     */
    public byte nextByte(int radix) {
        // Check cached result
        if ((typeCache != null) && (typeCache instanceof Byte)
            && this.radix == radix) {
            byte val = ((Byte)typeCache).byteValue();
            useTypeCache();
            return val;
        }
        setRadix(radix);
        clearCaches();
        // Search for next byte
        try {
            String s = next(integerPattern());
            if (matcher.group(SIMPLE_GROUP_INDEX) == null)
                s = processIntegerToken(s);
            return Byte.parseByte(s, radix);
        } catch (NumberFormatException nfe) {
            position = matcher.start(); // don't skip bad token
            throw new InputMismatchException(nfe.getMessage());
        }
    }

    /**
     * Returns true if the next token in this scanner's input can be
     * interpreted as a short value in the default radix using the
     * {@link #nextShort} method. The scanner does not advance past any input.
     *
     * @return true if and only if this scanner's next token is a valid
     *         short value in the default radix
     * @throws IllegalStateException if this scanner is closed
     */
    public boolean hasNextShort() {
        return hasNextShort(defaultRadix);
    }

    /**
     * Returns true if the next token in this scanner's input can be
     * interpreted as a short value in the specified radix using the
     * {@link #nextShort} method. The scanner does not advance past any input.
     *
     * @param radix the radix used to interpret the token as a short value
     * @return true if and only if this scanner's next token is a valid
     *         short value in the specified radix
     * @throws IllegalStateException if this scanner is closed
     */
    public boolean hasNextShort(int radix) {
        setRadix(radix);
        boolean result = hasNext(integerPattern());
        if (result) { // Cache it
            try {
                String s = (matcher.group(SIMPLE_GROUP_INDEX) == null) ?
                    processIntegerToken(hasNextResult) :
                    hasNextResult;
                typeCache = Short.parseShort(s, radix);
            } catch (NumberFormatException nfe) {
                result = false;
            }
        }
        return result;
    }

    /**
     * Scans the next token of the input as a <tt>short</tt>.
     *
     * <p> An invocation of this method of the form
     * <tt>nextShort()</tt> behaves in exactly the same way as the
     * invocation <tt>nextShort(radix)</tt>, where <code>radix</code>
     * is the default radix of this scanner.
     *
     * @return the <tt>short</tt> scanned from the input
     * @throws InputMismatchException
     *         if the next token does not match the <i>Integer</i>
     *         regular expression, or is out of range
     * @throws NoSuchElementException if input is exhausted
     * @throws IllegalStateException if this scanner is closed
     */
    public short nextShort() {
        return nextShort(defaultRadix);
    }

    /**
     * Scans the next token of the input as a <tt>short</tt>.
     * This method will throw <code>InputMismatchException</code>
     * if the next token cannot be translated into a valid short value as
     * described below. If the translation is successful, the scanner advances
     * past the input that matched.
     *
     * <p> If the next token matches the <a
     * href="#Integer-regex"><i>Integer</i></a> regular expression defined
     * above then the token is converted into a <tt>short</tt> value as if by
     * removing all locale specific prefixes, group separators, and locale
     * specific suffixes, then mapping non-ASCII digits into ASCII
     * digits via {@link Character#digit Character.digit}, prepending a
     * negative sign (-) if the locale specific negative prefixes and suffixes
     * were present, and passing the resulting string to
     * {@link Short#parseShort(String, int) Short.parseShort} with the
     * specified radix.
     *
     * @param radix the radix used to interpret the token as a short value
     * @return the <tt>short</tt> scanned from the input
     * @throws InputMismatchException
     *         if the next token does not match the <i>Integer</i>
     *         regular expression, or is out of range
     * @throws NoSuchElementException if input is exhausted
     * @throws IllegalStateException if this scanner is closed
     */
    public short nextShort(int radix) {
        // Check cached result
        if ((typeCache != null) && (typeCache instanceof Short)
            && this.radix == radix) {
            short val = ((Short)typeCache).shortValue();
            useTypeCache();
            return val;
        }
        setRadix(radix);
        clearCaches();
        // Search for next short
        try {
            String s = next(integerPattern());
            if (matcher.group(SIMPLE_GROUP_INDEX) == null)
                s = processIntegerToken(s);
            return Short.parseShort(s, radix);
        } catch (NumberFormatException nfe) {
            position = matcher.start(); // don't skip bad token
            throw new InputMismatchException(nfe.getMessage());
        }
    }

    /**
     * Returns true if the next token in this scanner's input can be
     * interpreted as an int value in the default radix using the
     * {@link #nextInt} method. The scanner does not advance past any input.
     *
     * @return true if and only if this scanner's next token is a valid
     *         int value
     * @throws IllegalStateException if this scanner is closed
     */
    public boolean hasNextInt() {
        return hasNextInt(defaultRadix);
    }

    /**
     * Returns true if the next token in this scanner's input can be
     * interpreted as an int value in the specified radix using the
     * {@link #nextInt} method. The scanner does not advance past any input.
     *
     * @param radix the radix used to interpret the token as an int value
     * @return true if and only if this scanner's next token is a valid
     *         int value
     * @throws IllegalStateException if this scanner is closed
     */
    public boolean hasNextInt(int radix) {
        setRadix(radix);
        boolean result = hasNext(integerPattern());
        if (result) { // Cache it
            try {
                String s = (matcher.group(SIMPLE_GROUP_INDEX) == null) ?
                    processIntegerToken(hasNextResult) :
                    hasNextResult;
                typeCache = Integer.parseInt(s, radix);
            } catch (NumberFormatException nfe) {
                result = false;
            }
        }
        return result;
    }

    /**
     * The integer token must be stripped of prefixes, group separators,
     * and suffixes, non ascii digits must be converted into ascii digits
     * before parse will accept it.
     */
    private String processIntegerToken(String token) {
        String result = token.replaceAll(""+groupSeparator, "");
        boolean isNegative = false;
        int preLen = negativePrefix.length();
        if ((preLen > 0) && result.startsWith(negativePrefix)) {
            isNegative = true;
            result = result.substring(preLen);
        }
        int sufLen = negativeSuffix.length();
        if ((sufLen > 0) && result.endsWith(negativeSuffix)) {
            isNegative = true;
            result = result.substring(result.length() - sufLen,
                                      result.length());
        }
        if (isNegative)
            result = "-" + result;
        return result;
    }

    /**
     * Scans the next token of the input as an <tt>int</tt>.
     *
     * <p> An invocation of this method of the form
     * <tt>nextInt()</tt> behaves in exactly the same way as the
     * invocation <tt>nextInt(radix)</tt>, where <code>radix</code>
     * is the default radix of this scanner.
     *
     * @return the <tt>int</tt> scanned from the input
     * @throws InputMismatchException
     *         if the next token does not match the <i>Integer</i>
     *         regular expression, or is out of range
     * @throws NoSuchElementException if input is exhausted
     * @throws IllegalStateException if this scanner is closed
     */
    public int nextInt() {
        return nextInt(defaultRadix);
    }

    /**
     * Scans the next token of the input as an <tt>int</tt>.
     * This method will throw <code>InputMismatchException</code>
     * if the next token cannot be translated into a valid int value as
     * described below. If the translation is successful, the scanner advances
     * past the input that matched.
     *
     * <p> If the next token matches the <a
     * href="#Integer-regex"><i>Integer</i></a> regular expression defined
     * above then the token is converted into an <tt>int</tt> value as if by
     * removing all locale specific prefixes, group separators, and locale
     * specific suffixes, then mapping non-ASCII digits into ASCII
     * digits via {@link Character#digit Character.digit}, prepending a
     * negative sign (-) if the locale specific negative prefixes and suffixes
     * were present, and passing the resulting string to
     * {@link Integer#parseInt(String, int) Integer.parseInt} with the
     * specified radix.
     *
     * @param radix the radix used to interpret the token as an int value
     * @return the <tt>int</tt> scanned from the input
     * @throws InputMismatchException
     *         if the next token does not match the <i>Integer</i>
     *         regular expression, or is out of range
     * @throws NoSuchElementException if input is exhausted
     * @throws IllegalStateException if this scanner is closed
     */
    public int nextInt(int radix) {
        // Check cached result
        if ((typeCache != null) && (typeCache instanceof Integer)
            && this.radix == radix) {
            int val = ((Integer)typeCache).intValue();
            useTypeCache();
            return val;
        }
        setRadix(radix);
        clearCaches();
        // Search for next int
        try {
            String s = next(integerPattern());
            if (matcher.group(SIMPLE_GROUP_INDEX) == null)
                s = processIntegerToken(s);
            return Integer.parseInt(s, radix);
        } catch (NumberFormatException nfe) {
            position = matcher.start(); // don't skip bad token
            throw new InputMismatchException(nfe.getMessage());
        }
    }

    /**
     * Returns true if the next token in this scanner's input can be
     * interpreted as a long value in the default radix using the
     * {@link #nextLong} method. The scanner does not advance past any input.
     *
     * @return true if and only if this scanner's next token is a valid
     *         long value
     * @throws IllegalStateException if this scanner is closed
     */
    public boolean hasNextLong() {
        return hasNextLong(defaultRadix);
    }

    /**
     * Returns true if the next token in this scanner's input can be
     * interpreted as a long value in the specified radix using the
     * {@link #nextLong} method. The scanner does not advance past any input.
     *
     * @param radix the radix used to interpret the token as a long value
     * @return true if and only if this scanner's next token is a valid
     *         long value
     * @throws IllegalStateException if this scanner is closed
     */
    public boolean hasNextLong(int radix) {
        setRadix(radix);
        boolean result = hasNext(integerPattern());
        if (result) { // Cache it
            try {
                String s = (matcher.group(SIMPLE_GROUP_INDEX) == null) ?
                    processIntegerToken(hasNextResult) :
                    hasNextResult;
                typeCache = Long.parseLong(s, radix);
            } catch (NumberFormatException nfe) {
                result = false;
            }
        }
        return result;
    }

    /**
     * Scans the next token of the input as a <tt>long</tt>.
     *
     * <p> An invocation of this method of the form
     * <tt>nextLong()</tt> behaves in exactly the same way as the
     * invocation <tt>nextLong(radix)</tt>, where <code>radix</code>
     * is the default radix of this scanner.
     *
     * @return the <tt>long</tt> scanned from the input
     * @throws InputMismatchException
     *         if the next token does not match the <i>Integer</i>
     *         regular expression, or is out of range
     * @throws NoSuchElementException if input is exhausted
     * @throws IllegalStateException if this scanner is closed
     */
    public long nextLong() {
        return nextLong(defaultRadix);
    }

    /**
     * Scans the next token of the input as a <tt>long</tt>.
     * This method will throw <code>InputMismatchException</code>
     * if the next token cannot be translated into a valid long value as
     * described below. If the translation is successful, the scanner advances
     * past the input that matched.
     *
     * <p> If the next token matches the <a
     * href="#Integer-regex"><i>Integer</i></a> regular expression defined
     * above then the token is converted into a <tt>long</tt> value as if by
     * removing all locale specific prefixes, group separators, and locale
     * specific suffixes, then mapping non-ASCII digits into ASCII
     * digits via {@link Character#digit Character.digit}, prepending a
     * negative sign (-) if the locale specific negative prefixes and suffixes
     * were present, and passing the resulting string to
     * {@link Long#parseLong(String, int) Long.parseLong} with the
     * specified radix.
     *
     * @param radix the radix used to interpret the token as an int value
     * @return the <tt>long</tt> scanned from the input
     * @throws InputMismatchException
     *         if the next token does not match the <i>Integer</i>
     *         regular expression, or is out of range
     * @throws NoSuchElementException if input is exhausted
     * @throws IllegalStateException if this scanner is closed
     */
    public long nextLong(int radix) {
        // Check cached result
        if ((typeCache != null) && (typeCache instanceof Long)
            && this.radix == radix) {
            long val = ((Long)typeCache).longValue();
            useTypeCache();
            return val;
        }
        setRadix(radix);
        clearCaches();
        try {
            String s = next(integerPattern());
            if (matcher.group(SIMPLE_GROUP_INDEX) == null)
                s = processIntegerToken(s);
            return Long.parseLong(s, radix);
        } catch (NumberFormatException nfe) {
            position = matcher.start(); // don't skip bad token
            throw new InputMismatchException(nfe.getMessage());
        }
    }

    /**
     * The float token must be stripped of prefixes, group separators,
     * and suffixes, non ascii digits must be converted into ascii digits
     * before parseFloat will accept it.
     *
     * If there are non-ascii digits in the token these digits must
     * be processed before the token is passed to parseFloat.
     */
    private String processFloatToken(String token) {
        String result = token.replaceAll(groupSeparator, "");
        if (!decimalSeparator.equals("\\."))
            result = result.replaceAll(decimalSeparator, ".");
        boolean isNegative = false;
        int preLen = negativePrefix.length();
        if ((preLen > 0) && result.startsWith(negativePrefix)) {
            isNegative = true;
            result = result.substring(preLen);
        }
        int sufLen = negativeSuffix.length();
        if ((sufLen > 0) && result.endsWith(negativeSuffix)) {
            isNegative = true;
            result = result.substring(result.length() - sufLen,
                                      result.length());
        }
        if (result.equals(nanString))
            result = "NaN";
        if (result.equals(infinityString))
            result = "Infinity";
        if (isNegative)
            result = "-" + result;

        // Translate non-ASCII digits
        Matcher m = NON_ASCII_DIGIT.matcher(result);
        if (m.find()) {
            StringBuilder inASCII = new StringBuilder();
            for (int i=0; i<result.length(); i++) {
                char nextChar = result.charAt(i);
                if (Character.isDigit(nextChar)) {
                    int d = Character.digit(nextChar, 10);
                    if (d != -1)
                        inASCII.append(d);
                    else
                        inASCII.append(nextChar);
                } else {
                    inASCII.append(nextChar);
                }
            }
            result = inASCII.toString();
        }

        return result;
    }

    /**
     * Returns true if the next token in this scanner's input can be
     * interpreted as a float value using the {@link #nextFloat}
     * method. The scanner does not advance past any input.
     *
     * @return true if and only if this scanner's next token is a valid
     *         float value
     * @throws IllegalStateException if this scanner is closed
     */
    public boolean hasNextFloat() {
        setRadix(10);
        boolean result = hasNext(floatPattern());
        if (result) { // Cache it
            try {
                String s = processFloatToken(hasNextResult);
                typeCache = Float.valueOf(Float.parseFloat(s));
            } catch (NumberFormatException nfe) {
                result = false;
            }
        }
        return result;
    }

    /**
     * Scans the next token of the input as a <tt>float</tt>.
     * This method will throw <code>InputMismatchException</code>
     * if the next token cannot be translated into a valid float value as
     * described below. If the translation is successful, the scanner advances
     * past the input that matched.
     *
     * <p> If the next token matches the <a
     * href="#Float-regex"><i>Float</i></a> regular expression defined above
     * then the token is converted into a <tt>float</tt> value as if by
     * removing all locale specific prefixes, group separators, and locale
     * specific suffixes, then mapping non-ASCII digits into ASCII
     * digits via {@link Character#digit Character.digit}, prepending a
     * negative sign (-) if the locale specific negative prefixes and suffixes
     * were present, and passing the resulting string to
     * {@link Float#parseFloat Float.parseFloat}. If the token matches
     * the localized NaN or infinity strings, then either "Nan" or "Infinity"
     * is passed to {@link Float#parseFloat(String) Float.parseFloat} as
     * appropriate.
     *
     * @return the <tt>float</tt> scanned from the input
     * @throws InputMismatchException
     *         if the next token does not match the <i>Float</i>
     *         regular expression, or is out of range
     * @throws NoSuchElementException if input is exhausted
     * @throws IllegalStateException if this scanner is closed
     */
    public float nextFloat() {
        // Check cached result
        if ((typeCache != null) && (typeCache instanceof Float)) {
            float val = ((Float)typeCache).floatValue();
            useTypeCache();
            return val;
        }
        setRadix(10);
        clearCaches();
        try {
            return Float.parseFloat(processFloatToken(next(floatPattern())));
        } catch (NumberFormatException nfe) {
            position = matcher.start(); // don't skip bad token
            throw new InputMismatchException(nfe.getMessage());
        }
    }

    /**
     * Returns true if the next token in this scanner's input can be
     * interpreted as a double value using the {@link #nextDouble}
     * method. The scanner does not advance past any input.
     *
     * @return true if and only if this scanner's next token is a valid
     *         double value
     * @throws IllegalStateException if this scanner is closed
     */
    public boolean hasNextDouble() {
        setRadix(10);
        boolean result = hasNext(floatPattern());
        if (result) { // Cache it
            try {
                String s = processFloatToken(hasNextResult);
                typeCache = Double.valueOf(Double.parseDouble(s));
            } catch (NumberFormatException nfe) {
                result = false;
            }
        }
        return result;
    }

    /**
     * Scans the next token of the input as a <tt>double</tt>.
     * This method will throw <code>InputMismatchException</code>
     * if the next token cannot be translated into a valid double value.
     * If the translation is successful, the scanner advances past the input
     * that matched.
     *
     * <p> If the next token matches the <a
     * href="#Float-regex"><i>Float</i></a> regular expression defined above
     * then the token is converted into a <tt>double</tt> value as if by
     * removing all locale specific prefixes, group separators, and locale
     * specific suffixes, then mapping non-ASCII digits into ASCII
     * digits via {@link Character#digit Character.digit}, prepending a
     * negative sign (-) if the locale specific negative prefixes and suffixes
     * were present, and passing the resulting string to
     * {@link Double#parseDouble Double.parseDouble}. If the token matches
     * the localized NaN or infinity strings, then either "Nan" or "Infinity"
     * is passed to {@link Double#parseDouble(String) Double.parseDouble} as
     * appropriate.
     *
     * @return the <tt>double</tt> scanned from the input
     * @throws InputMismatchException
     *         if the next token does not match the <i>Float</i>
     *         regular expression, or is out of range
     * @throws NoSuchElementException if the input is exhausted
     * @throws IllegalStateException if this scanner is closed
     */
    public double nextDouble() {
        // Check cached result
        if ((typeCache != null) && (typeCache instanceof Double)) {
            double val = ((Double)typeCache).doubleValue();
            useTypeCache();
            return val;
        }
        setRadix(10);
        clearCaches();
        // Search for next float
        try {
            return Double.parseDouble(processFloatToken(next(floatPattern())));
        } catch (NumberFormatException nfe) {
            position = matcher.start(); // don't skip bad token
            throw new InputMismatchException(nfe.getMessage());
        }
    }

    // Convenience methods for scanning multi precision numbers

    /**
     * Returns true if the next token in this scanner's input can be
     * interpreted as a <code>BigInteger</code> in the default radix using the
     * {@link #nextBigInteger} method. The scanner does not advance past any
     * input.
     *
     * @return true if and only if this scanner's next token is a valid
     *         <code>BigInteger</code>
     * @throws IllegalStateException if this scanner is closed
     */
    public boolean hasNextBigInteger() {
        return hasNextBigInteger(defaultRadix);
    }

    /**
     * Returns true if the next token in this scanner's input can be
     * interpreted as a <code>BigInteger</code> in the specified radix using
     * the {@link #nextBigInteger} method. The scanner does not advance past
     * any input.
     *
     * @param radix the radix used to interpret the token as an integer
     * @return true if and only if this scanner's next token is a valid
     *         <code>BigInteger</code>
     * @throws IllegalStateException if this scanner is closed
     */
    public boolean hasNextBigInteger(int radix) {
        setRadix(radix);
        boolean result = hasNext(integerPattern());
        if (result) { // Cache it
            try {
                String s = (matcher.group(SIMPLE_GROUP_INDEX) == null) ?
                    processIntegerToken(hasNextResult) :
                    hasNextResult;
                typeCache = new BigInteger(s, radix);
            } catch (NumberFormatException nfe) {
                result = false;
            }
        }
        return result;
    }

    /**
     * Scans the next token of the input as a {@link java.math.BigInteger
     * BigInteger}.
     *
     * <p> An invocation of this method of the form
     * <tt>nextBigInteger()</tt> behaves in exactly the same way as the
     * invocation <tt>nextBigInteger(radix)</tt>, where <code>radix</code>
     * is the default radix of this scanner.
     *
     * @return the <tt>BigInteger</tt> scanned from the input
     * @throws InputMismatchException
     *         if the next token does not match the <i>Integer</i>
     *         regular expression, or is out of range
     * @throws NoSuchElementException if the input is exhausted
     * @throws IllegalStateException if this scanner is closed
     */
    public BigInteger nextBigInteger() {
        return nextBigInteger(defaultRadix);
    }

    /**
     * Scans the next token of the input as a {@link java.math.BigInteger
     * BigInteger}.
     *
     * <p> If the next token matches the <a
     * href="#Integer-regex"><i>Integer</i></a> regular expression defined
     * above then the token is converted into a <tt>BigInteger</tt> value as if
     * by removing all group separators, mapping non-ASCII digits into ASCII
     * digits via the {@link Character#digit Character.digit}, and passing the
     * resulting string to the {@link
     * java.math.BigInteger#BigInteger(java.lang.String)
     * BigInteger(String, int)} constructor with the specified radix.
     *
     * @param radix the radix used to interpret the token
     * @return the <tt>BigInteger</tt> scanned from the input
     * @throws InputMismatchException
     *         if the next token does not match the <i>Integer</i>
     *         regular expression, or is out of range
     * @throws NoSuchElementException if the input is exhausted
     * @throws IllegalStateException if this scanner is closed
     */
    public BigInteger nextBigInteger(int radix) {
        // Check cached result
        if ((typeCache != null) && (typeCache instanceof BigInteger)
            && this.radix == radix) {
            BigInteger val = (BigInteger)typeCache;
            useTypeCache();
            return val;
        }
        setRadix(radix);
        clearCaches();
        // Search for next int
        try {
            String s = next(integerPattern());
            if (matcher.group(SIMPLE_GROUP_INDEX) == null)
                s = processIntegerToken(s);
            return new BigInteger(s, radix);
        } catch (NumberFormatException nfe) {
            position = matcher.start(); // don't skip bad token
            throw new InputMismatchException(nfe.getMessage());
        }
    }

    /**
     * Returns true if the next token in this scanner's input can be
     * interpreted as a <code>BigDecimal</code> using the
     * {@link #nextBigDecimal} method. The scanner does not advance past any
     * input.
     *
     * @return true if and only if this scanner's next token is a valid
     *         <code>BigDecimal</code>
     * @throws IllegalStateException if this scanner is closed
     */
    public boolean hasNextBigDecimal() {
        setRadix(10);
        boolean result = hasNext(decimalPattern());
        if (result) { // Cache it
            try {
                String s = processFloatToken(hasNextResult);
                typeCache = new BigDecimal(s);
            } catch (NumberFormatException nfe) {
                result = false;
            }
        }
        return result;
    }

    /**
     * Scans the next token of the input as a {@link java.math.BigDecimal
     * BigDecimal}.
     *
     * <p> If the next token matches the <a
     * href="#Decimal-regex"><i>Decimal</i></a> regular expression defined
     * above then the token is converted into a <tt>BigDecimal</tt> value as if
     * by removing all group separators, mapping non-ASCII digits into ASCII
     * digits via the {@link Character#digit Character.digit}, and passing the
     * resulting string to the {@link
     * java.math.BigDecimal#BigDecimal(java.lang.String) BigDecimal(String)}
     * constructor.
     *
     * @return the <tt>BigDecimal</tt> scanned from the input
     * @throws InputMismatchException
     *         if the next token does not match the <i>Decimal</i>
     *         regular expression, or is out of range
     * @throws NoSuchElementException if the input is exhausted
     * @throws IllegalStateException if this scanner is closed
     */
    public BigDecimal nextBigDecimal() {
        // Check cached result
        if ((typeCache != null) && (typeCache instanceof BigDecimal)) {
            BigDecimal val = (BigDecimal)typeCache;
            useTypeCache();
            return val;
        }
        setRadix(10);
        clearCaches();
        // Search for next float
        try {
            String s = processFloatToken(next(decimalPattern()));
            return new BigDecimal(s);
        } catch (NumberFormatException nfe) {
            position = matcher.start(); // don't skip bad token
            throw new InputMismatchException(nfe.getMessage());
        }
    }

    /**
     * Resets this scanner.
     *
     * <p> Resetting a scanner discards all of its explicit state
     * information which may have been changed by invocations of {@link
     * #useDelimiter}, {@link #useLocale}, or {@link #useRadix}.
     *
     * <p> An invocation of this method of the form
     * <tt>scanner.reset()</tt> behaves in exactly the same way as the
     * invocation
     *
     * <blockquote><pre>
     *   scanner.useDelimiter("\\p{javaWhitespace}+")
     *          .useLocale(Locale.getDefault())
     *          .useRadix(10);
     * </pre></blockquote>
     *
     * @return this scanner
     *
     * @since 1.6
     */
    public Scanner reset() {
        delimPattern = WHITESPACE_PATTERN;
        useLocale(Locale.getDefault(Locale.Category.FORMAT));
        useRadix(10);
        clearCaches();
        return this;
    }
}
