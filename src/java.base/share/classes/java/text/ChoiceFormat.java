/*
 * Copyright (c) 1996, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * (C) Copyright Taligent, Inc. 1996, 1997 - All Rights Reserved
 * (C) Copyright IBM Corp. 1996 - 1998 - All Rights Reserved
 *
 *   The original version of this source code and documentation is copyrighted
 * and owned by Taligent, Inc., a wholly-owned subsidiary of IBM. These
 * materials are provided under terms of a License Agreement between Taligent
 * and Sun. This technology is protected by multiple US and International
 * patents. This notice and attribution to Taligent may not be removed.
 *   Taligent is a registered trademark of Taligent, Inc.
 *
 */

package java.text;

import java.io.InvalidObjectException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Arrays;

/**
 * {@code ChoiceFormat} is a concrete subclass of {@code NumberFormat} that
 * allows you to attach a format to a range of numbers.
 * It is generally used in a {@link MessageFormat} for handling plurals.
 * The choice is specified with an ascending list of doubles, where each item
 * specifies a half-open interval up to the next item:
 * <blockquote>
 * <pre>
 * X matches j if and only if limit[j] &le; X &lt; limit[j+1]
 * </pre>
 * </blockquote>
 * If there is no match, then either the first or last index is used, depending
 * on whether the number (X) is too low or too high.  If the limit array is not
 * in ascending order, the results of formatting will be incorrect.  ChoiceFormat
 * also accepts <code>&#92;u221E</code> as equivalent to infinity(INF).
 *
 * <p>
 * <strong>Note:</strong>
 * {@code ChoiceFormat} differs from the other {@code Format}
 * classes in that you create a {@code ChoiceFormat} object with a
 * constructor (not with a {@code getInstance} style factory
 * method). The factory methods aren't necessary because {@code ChoiceFormat}
 * doesn't require any complex setup for a given locale. In fact,
 * {@code ChoiceFormat} doesn't implement any locale specific behavior.
 *
 * <p>
 * A {@code ChoiceFormat} can be constructed using either an array of formats
 * and an array of limits or a string pattern. When constructing with
 * format and limit arrays, the length of these arrays must be the same.
 *
 * For example,
 * <ul>
 * <li>
 *     <em>limits</em> = {1,2,3,4,5,6,7}<br>
 *     <em>formats</em> = {"Sun","Mon","Tue","Wed","Thur","Fri","Sat"}
 * <li>
 *     <em>limits</em> = {0, 1, ChoiceFormat.nextDouble(1)}<br>
 *     <em>formats</em> = {"no files", "one file", "many files"}<br>
 *     ({@code nextDouble} can be used to get the next higher double, to
 *     make the half-open interval.)
 * </ul>
 *
 * <p>
 * Below is an example of constructing a ChoiceFormat with arrays to format
 * and parse values:
 * <blockquote>
 * {@snippet lang=java :
 * double[] limits = {1,2,3,4,5,6,7};
 * String[] dayOfWeekNames = {"Sun","Mon","Tue","Wed","Thur","Fri","Sat"};
 * ChoiceFormat form = new ChoiceFormat(limits, dayOfWeekNames);
 * ParsePosition status = new ParsePosition(0);
 * for (double i = 0.0; i <= 8.0; ++i) {
 *     status.setIndex(0);
 *     System.out.println(i + " -> " + form.format(i) + " -> "
 *                              + form.parse(form.format(i),status));
 * }
 * }
 * </blockquote>
 * For more sophisticated patterns, {@code ChoiceFormat} can be used with
 * {@link MessageFormat} to produce accurate forms for singular and plural:
 * <blockquote>
 * {@snippet lang=java :
 * double[] filelimits = {0,1,2};
 * String[] filepart = {"are no files","is one file","are {2} files"};
 * ChoiceFormat fileform = new ChoiceFormat(filelimits, filepart);
 * Format[] testFormats = {fileform, null, NumberFormat.getInstance()};
 * MessageFormat pattform = new MessageFormat("There {0} on {1}");
 * pattform.setFormats(testFormats);
 * Object[] testArgs = {null, "ADisk", null};
 * for (int i = 0; i < 4; ++i) {
 *     testArgs[0] = Integer.valueOf(i);
 *     testArgs[2] = testArgs[0];
 *     System.out.println(pattform.format(testArgs));
 * }
 * }
 * </blockquote>
 * Would output the following:
 * <blockquote>
 * <pre>{@code
 * There are no files on ADisk
 * There is one file on ADisk
 * There are 2 files on ADisk
 * There are 3 files on ADisk
 * }</pre>
 * </blockquote>
 *
 * <h2><a id="patterns">Patterns</a></h2>
 * A {@code ChoiceFormat} pattern has the following syntax:
 * <blockquote>
 * <dl>
 * <dt><i>Pattern:</i>
 * <dd>SubPattern *("|" SubPattern)
 * <dd><i>Note: Each additional SubPattern must have a Limit greater than the previous SubPattern's Limit</i>
 * </dl>
 *
 * <dl>
 * <dt><i>SubPattern:</i>
 * <dd>Limit Relation Format
 * </dl>
 *
 * <dl>
 * <dt><i>Limit:</i>
 * <dd>Number / "&infin;" / "-&infin;"
 * </dl>
 *
 * <dl>
 * <dt><i>Number:</i>
 * <dd>["-"] *(Digit) 1*(Decimal / Digit) *(Digit) [Exponent]
 * </dl>
 *
 * <dl>
 * <dt><i>Decimal:</i>
 * <dd>1*(Digit ".") / 1*("." Digit)
 * </dl>
 *
 * <dl>
 * <dt><i>Digit:</i>
 * <dd>0 - 9
 * </dl>
 *
 * <dl>
 * <dt><i>Exponent:</i>
 * <dd>*(Digit) Digit ExponentSymbol Digit *(Digit)
 * </dl>
 *
 * <dl>
 * <dt><i>ExponentSymbol:</i>
 * <dd>"e" / "E"
 * </dl>
 *
 * <dl>
 * <dt><i>Relation:</i>
 * <dd>"#" / "&lt;" / "&le;"
 * </dl>
 *
 * <dl>
 * <dt><i>Format:</i>
 * <dd>Any characters except the <i>Relation</i> symbols
 * </dl>
 *
 * </blockquote>
 *
 * <i>Note:The relation &le; is not equivalent to &lt;&equals;</i>
 *
 * <p>If a <i>Relation</i> symbol is to be used within a <i>Format</i> pattern,
 * it must be single quoted. For example,
 * {@code new ChoiceFormat("1# '#'1 ").format(1)} returns {@code " #1 "}.
 * Use two single quotes in a row to produce a literal single quote. For example,
 * {@code new ChoiceFormat("1# ''one'' ").format(1)} returns {@code " 'one' "}.
 *
 * <p>Below is an example of constructing a ChoiceFormat with a pattern:
 * <blockquote>
 * {@snippet lang=java :
 * ChoiceFormat fmt = new ChoiceFormat(
 *      "-1#is negative| 0#is zero or fraction | 1#is one |1.0<is 1+ |2#is two |2<is more than 2.");
 *
 * System.out.println(fmt.format(Double.NEGATIVE_INFINITY)); // outputs "is negative"
 * System.out.println(fmt.format(-1.0)); // outputs "is negative"
 * System.out.println(fmt.format(0)); // outputs "is zero or fraction"
 * System.out.println(fmt.format(0.9)); // outputs "is zero or fraction"
 * System.out.println(fmt.format(1)); // outputs "is one"
 * System.out.println(fmt.format(1.5)); // outputs "is 1+"
 * System.out.println(fmt.format(2)); // outputs "is two"
 * System.out.println(fmt.format(2.1)); // outputs "is more than 2."
 * System.out.println(fmt.format(Double.NaN)); // outputs "is negative"
 * System.out.println(fmt.format(Double.POSITIVE_INFINITY)); // outputs "is more than 2."
 * }
 * </blockquote>
 *
 * <h2><a id="synchronization">Synchronization</a></h2>
 *
 * <p>
 * Choice formats are not synchronized.
 * It is recommended to create separate format instances for each thread.
 * If multiple threads access a format concurrently, it must be synchronized
 * externally.
 *
 *
 * @see          DecimalFormat
 * @see          MessageFormat
 * @author       Mark Davis
 * @since 1.1
 */
public class ChoiceFormat extends NumberFormat {

    // Proclaim serial compatibility with 1.1 FCS
    @java.io.Serial
    private static final long serialVersionUID = 1795184449645032964L;

    /**
     * Apply the given pattern to this ChoiceFormat object. The syntax
     * for the ChoiceFormat pattern can be seen in the {@linkplain ##patterns
     * Patterns} section. Unlike {@link #setChoices(double[], String[])} this
     * method will throw an {@code IllegalArgumentException} if the {@code
     * limits} are not in ascending order.
     * @param newPattern a pattern string
     * @throws    NullPointerException if {@code newPattern}
     *            is {@code null}
     * @throws    IllegalArgumentException if {@code newPattern}
     *            violates the pattern syntax
     * @see #ChoiceFormat(String)
     */
    public void applyPattern(String newPattern) {
        applyPatternImpl(newPattern);
    }

    /**
     * Implementation of applying a pattern to this ChoiceFormat.
     * This method processes a String pattern in accordance with the ChoiceFormat
     * pattern syntax and populates the internal {@code limits} and {@code formats}
     * array variables. See the {@linkplain ##patterns} section for
     * further understanding of certain special characters: "#", "<", "\u2264", "|".
     */
    private void applyPatternImpl(String newPattern) {
        StringBuilder[] segments = new StringBuilder[2];
        for (int i = 0; i < segments.length; ++i) {
            segments[i] = new StringBuilder();
        }
        double[] newChoiceLimits = new double[30];
        String[] newChoiceFormats = new String[30];
        int count = 0;
        int part = 0;
        double startValue = 0;
        double oldStartValue = Double.NaN;
        boolean inQuote = false;
        for (int i = 0; i < newPattern.length(); ++i) {
            char ch = newPattern.charAt(i);
            if (ch=='\'') {
                // Check for "''" indicating a literal quote
                if ((i+1)<newPattern.length() && newPattern.charAt(i+1)==ch) {
                    segments[part].append(ch);
                    ++i;
                } else {
                    inQuote = !inQuote;
                }
            } else if (inQuote) {
                segments[part].append(ch);
            } else if (ch == '<' || ch == '#' || ch == '\u2264') {
                if (segments[0].length() == 0) {
                    throw new IllegalArgumentException("Each interval must"
                            + " contain a number before a format");
                }

                String tempBuffer = segments[0].toString();
                if (tempBuffer.equals("\u221E")) {
                    startValue = Double.POSITIVE_INFINITY;
                } else if (tempBuffer.equals("-\u221E")) {
                    startValue = Double.NEGATIVE_INFINITY;
                } else {
                    startValue = Double.parseDouble(tempBuffer);
                }

                if (ch == '<' && startValue != Double.POSITIVE_INFINITY &&
                        startValue != Double.NEGATIVE_INFINITY) {
                    startValue = nextDouble(startValue);
                }
                if (startValue <= oldStartValue) {
                    throw new IllegalArgumentException("Incorrect order of"
                            + " intervals, must be in ascending order");
                }
                segments[0].setLength(0);
                part = 1;
            } else if (ch == '|') {
                if (count == newChoiceLimits.length) {
                    newChoiceLimits = doubleArraySize(newChoiceLimits);
                    newChoiceFormats = doubleArraySize(newChoiceFormats);
                }
                newChoiceLimits[count] = startValue;
                newChoiceFormats[count] = segments[1].toString();
                ++count;
                oldStartValue = startValue;
                segments[1].setLength(0);
                part = 0;
            } else {
                segments[part].append(ch);
            }
        }
        // clean up last one
        if (part == 1) {
            if (count == newChoiceLimits.length) {
                newChoiceLimits = doubleArraySize(newChoiceLimits);
                newChoiceFormats = doubleArraySize(newChoiceFormats);
            }
            newChoiceLimits[count] = startValue;
            newChoiceFormats[count] = segments[1].toString();
            ++count;
        }
        choiceLimits = new double[count];
        System.arraycopy(newChoiceLimits, 0, choiceLimits, 0, count);
        choiceFormats = new String[count];
        System.arraycopy(newChoiceFormats, 0, choiceFormats, 0, count);
    }

    /**
     * {@return a pattern {@code string} that represents the {@code limits} and
     * {@code formats} of this ChoiceFormat object}
     *
     * The {@code string} returned is not guaranteed to be the same input
     * {@code string} passed to either {@link #applyPattern(String)} or
     * {@link #ChoiceFormat(String)}.
     *
     * @see #applyPattern(String)
     */
    public String toPattern() {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < choiceLimits.length; ++i) {
            if (i != 0) {
                result.append('|');
            }
            // choose based upon which has less precision
            // approximate that by choosing the closest one to an integer.
            // could do better, but it's not worth it.
            double less = previousDouble(choiceLimits[i]);
            double tryLessOrEqual = Math.abs(Math.IEEEremainder(choiceLimits[i], 1.0d));
            double tryLess = Math.abs(Math.IEEEremainder(less, 1.0d));

            if (tryLessOrEqual < tryLess) {
                result.append(choiceLimits[i]);
                result.append('#');
            } else {
                if (choiceLimits[i] == Double.POSITIVE_INFINITY) {
                    result.append("\u221E");
                } else if (choiceLimits[i] == Double.NEGATIVE_INFINITY) {
                    result.append("-\u221E");
                } else {
                    result.append(less);
                }
                result.append('<');
            }
            // Append choiceFormats[i], using quotes if there are special characters.
            // Single quotes themselves must be escaped in either case.
            String text = choiceFormats[i];
            boolean needQuote = text.indexOf('<') >= 0
                || text.indexOf('#') >= 0
                || text.indexOf('\u2264') >= 0
                || text.indexOf('|') >= 0;
            if (needQuote) result.append('\'');
            if (text.indexOf('\'') < 0) result.append(text);
            else {
                for (int j=0; j<text.length(); ++j) {
                    char c = text.charAt(j);
                    result.append(c);
                    if (c == '\'') result.append(c);
                }
            }
            if (needQuote) result.append('\'');
        }
        return result.toString();
    }

    /**
     * Constructs a ChoiceFormat with limits and corresponding formats
     * based on the pattern.
     * The syntax for the ChoiceFormat pattern can be seen in the {@linkplain
     * ##patterns Patterns} section.
     *
     * @param newPattern the new pattern string
     * @throws    NullPointerException if {@code newPattern} is
     *            {@code null}
     * @throws    IllegalArgumentException if {@code newPattern}
     *            violates the pattern syntax
     * @see #applyPattern
     */
    public ChoiceFormat(String newPattern)  {
        applyPatternImpl(newPattern);
    }

    /**
     * Constructs with the limits and the corresponding formats.
     *
     * @param limits limits in ascending order
     * @param formats corresponding format strings
     * @throws    NullPointerException if {@code limits} or {@code formats}
     *            is {@code null}
     * @throws    IllegalArgumentException if the length of {@code limits}
     *            and {@code formats} are not equal
     * @see #setChoices
     */
    public ChoiceFormat(double[] limits, String[] formats) {
        setChoicesImpl(limits, formats);
    }

    /**
     * Set the choices to be used in formatting.
     *
     * @param limits contains the top value that you want
     * parsed with that format, and should be in ascending sorted order. When
     * formatting X, the choice will be the i, where
     * limit[i] &le; X {@literal <} limit[i+1].
     * If the limit array is not in ascending order, the results of formatting
     * will be incorrect.
     * @param formats are the formats you want to use for each limit.
     * @throws    NullPointerException if {@code limits} or
     *            {@code formats} is {@code null}
     * @throws    IllegalArgumentException if the length of {@code limits}
     *            and {@code formats} are not equal
     */
    public void setChoices(double[] limits, String[] formats) {
        setChoicesImpl(limits, formats);
    }

    /**
     * Implementation of populating the {@code limits} and
     * {@code formats} of this ChoiceFormat. Defensive copies are made.
     */
    private void setChoicesImpl(double[] limits, String[] formats) {
        if (limits.length != formats.length) {
            throw new IllegalArgumentException(
                    "Input arrays must be of the same length.");
        }
        choiceLimits = Arrays.copyOf(limits, limits.length);
        choiceFormats = Arrays.copyOf(formats, formats.length);
    }

    /**
     * {@return the limits of this ChoiceFormat}
     */
    public double[] getLimits() {
        return Arrays.copyOf(choiceLimits, choiceLimits.length);
    }

    /**
     * {@return the formats of this ChoiceFormat}
     */
    public Object[] getFormats() {
        return Arrays.copyOf(choiceFormats, choiceFormats.length);
    }

    // Overrides

    /**
     * Specialization of format. This method really calls
     * {@code format(double, StringBuffer, FieldPosition)}
     * thus the range of longs that are supported is only equal to
     * the range that can be stored by double. This will never be
     * a practical limitation.
     */
    @Override
    public StringBuffer format(long number, StringBuffer toAppendTo,
                               FieldPosition status) {
        return format((double)number, toAppendTo, status);
    }

    /**
     * Returns pattern with formatted double.
     * @param number number to be formatted and substituted.
     * @param toAppendTo where text is appended.
     * @param status ignore no useful status is returned.
     * @throws    NullPointerException if {@code toAppendTo}
     *            is {@code null}
     */
    @Override
    public StringBuffer format(double number, StringBuffer toAppendTo,
                               FieldPosition status) {
        // find the number
        int i;
        for (i = 0; i < choiceLimits.length; ++i) {
            if (!(number >= choiceLimits[i])) {
                // same as number < choiceLimits, except catches NaN
                break;
            }
        }
        --i;
        if (i < 0) i = 0;
        // return either a formatted number, or a string
        return toAppendTo.append(choiceFormats[i]);
    }

    /**
     * Parses a Number from the input text.
     * @param text the source text.
     * @param status an input-output parameter.  On input, the
     * status.index field indicates the first character of the
     * source text that should be parsed.  On exit, if no error
     * occurred, status.index is set to the first unparsed character
     * in the source text.  On exit, if an error did occur,
     * status.index is unchanged and status.errorIndex is set to the
     * first index of the character that caused the parse to fail.
     * @return A Number representing the value of the number parsed.
     * @throws    NullPointerException if {@code status} is {@code null}
     *            or if {@code text} is {@code null} and the list of
     *            choice strings is not empty.
     */
    @Override
    public Number parse(String text, ParsePosition status) {
        // find the best number (defined as the one with the longest parse)
        int start = status.index;
        int furthest = start;
        double bestNumber = Double.NaN;
        double tempNumber = 0.0;
        for (int i = 0; i < choiceFormats.length; ++i) {
            String tempString = choiceFormats[i];
            if (text.regionMatches(start, tempString, 0, tempString.length())) {
                status.index = start + tempString.length();
                tempNumber = choiceLimits[i];
                if (status.index > furthest) {
                    furthest = status.index;
                    bestNumber = tempNumber;
                    if (furthest == text.length()) break;
                }
            }
        }
        status.index = furthest;
        if (status.index == start) {
            status.errorIndex = furthest;
        }
        return Double.valueOf(bestNumber);
    }

    /**
     * Finds the least double greater than {@code d}.
     * If {@code NaN}, returns same value.
     * <p>Used to make half-open intervals.
     *
     * @implNote This is equivalent to calling
     * {@link Math#nextUp(double) Math.nextUp(d)}
     *
     * @param d the reference value
     * @return the least double value greater than {@code d}
     * @see #previousDouble
     */
    public static final double nextDouble (double d) {
        return Math.nextUp(d);
    }

    /**
     * Finds the greatest double less than {@code d}.
     * If {@code NaN}, returns same value.
     *
     * @implNote This is equivalent to calling
     * {@link Math#nextDown(double) Math.nextDown(d)}
     *
     * @param d the reference value
     * @return the greatest double value less than {@code d}
     * @see #nextDouble
     */
    public static final double previousDouble (double d) {
        return Math.nextDown(d);
    }

    /**
     * Overrides Cloneable
     */
    @Override
    public Object clone()
    {
        ChoiceFormat other = (ChoiceFormat) super.clone();
        // for primitives or immutables, shallow clone is enough
        other.choiceLimits = choiceLimits.clone();
        other.choiceFormats = choiceFormats.clone();
        return other;
    }

    /**
     * {@return the hash code for this {@code ChoiceFormat}}
     *
     * @implSpec This method calculates the hash code value using the values returned by
     * {@link #getFormats()} and {@link #getLimits()}.
     * @see Object#hashCode()
     */
    @Override
    public int hashCode() {
        int result = choiceLimits.length;
        if (choiceFormats.length > 0) {
            // enough for reasonable distribution
            result ^= choiceFormats[choiceFormats.length-1].hashCode();
        }
        return result;
    }

    /**
     * Compares the specified object with this {@code ChoiceFormat} for equality.
     * Returns true if the object is also a {@code ChoiceFormat} and the
     * two formats would format any value the same.
     *
     * @implSpec This method performs an equality check with a notion of class
     * identity based on {@code getClass()}, rather than {@code instanceof}.
     * Therefore, in the equals methods in subclasses, no instance of this class
     * should compare as equal to an instance of a subclass.
     * @param  obj object to be compared for equality
     * @return {@code true} if the specified object is equal to this {@code ChoiceFormat}
     * @see Object#equals(Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)                      // quick check
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        ChoiceFormat other = (ChoiceFormat) obj;
        return (Arrays.equals(choiceLimits, other.choiceLimits)
             && Arrays.equals(choiceFormats, other.choiceFormats));
    }

    /**
     * After reading an object from the input stream, do a simple verification
     * to maintain class invariants.
     * @throws InvalidObjectException if the objects read from the stream is invalid.
     */
    @java.io.Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        if (choiceLimits.length != choiceFormats.length) {
            throw new InvalidObjectException(
                    "limits and format arrays of different length.");
        }
    }

    // ===============privates===========================

    /**
     * A list of lower bounds for the choices.  The formatter will return
     * {@code choiceFormats[i]} if the number being formatted is greater than or equal to
     * {@code choiceLimits[i]} and less than {@code choiceLimits[i+1]}.
     * @serial
     */
    private double[] choiceLimits;

    /**
     * A list of choice strings.  The formatter will return
     * {@code choiceFormats[i]} if the number being formatted is greater than or equal to
     * {@code choiceLimits[i]} and less than {@code choiceLimits[i+1]}.
     * @serial
     */
    private String[] choiceFormats;

    /**
     * Finds the least double greater than {@code d} (if {@code positive} is
     * {@code true}), or the greatest double less than {@code d} (if
     * {@code positive} is {@code false}).
     * If {@code NaN}, returns same value.
     *
     * @implNote This is equivalent to calling
     * {@code positive ? Math.nextUp(d) : Math.nextDown(d)}
     *
     * @param d        the reference value
     * @param positive {@code true} if the least double is desired;
     *                 {@code false} otherwise
     * @return the least or greater double value
     */
    public static double nextDouble (double d, boolean positive) {
        return positive ? Math.nextUp(d) : Math.nextDown(d);
    }

    private static double[] doubleArraySize(double[] array) {
        int oldSize = array.length;
        double[] newArray = new double[oldSize * 2];
        System.arraycopy(array, 0, newArray, 0, oldSize);
        return newArray;
    }

    private String[] doubleArraySize(String[] array) {
        int oldSize = array.length;
        String[] newArray = new String[oldSize * 2];
        System.arraycopy(array, 0, newArray, 0, oldSize);
        return newArray;
    }

}
