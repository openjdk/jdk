/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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

package java.text;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.IntStream;
import sun.util.locale.provider.LocaleProviderAdapter;

/**
 * {@code ListFormat} formats or parses a list of strings in a locale-sensitive way.
 * Use {@code ListFormat} to construct a list of strings displayed for end users.
 * For example, displaying a list of 3 weekdays, e.g. "Monday", "Wednesday", "Friday"
 * as "Monday, Wednesday, and Friday" in an inclusive list type. This class provides
 * the functionality defined in Unicode Consortium's LDML specification for
 * <a href="https://www.unicode.org/reports/tr35/tr35-general.html#ListPatterns">
 * List Patterns</a>.
 * <p>
 * Three formatting types are provided: {@link Type#STANDARD STANDARD}, {@link Type#OR OR},
 * and {@link Type#UNIT UNIT}, which determines the punctuation
 * between the strings and the connecting words if any. Also, three formatting styles for each
 * type are provided: {@link Style#FULL FULL}, {@link Style#SHORT SHORT}, and
 * {@link Style#NARROW NARROW}, suitable for how the strings are abbreviated (or not).
 * The following snippet is an example of formatting
 * the list of Strings {@code "Foo", "Bar", "Baz"} in US English with
 * {@code STANDARD} type and {@code FULL} style:
 * {@snippet lang=java :
 * ListFormat.getInstance(Locale.US, ListFormat.Type.STANDARD, ListFormat.Style.FULL)
 *     .format(List.of("Foo", "Bar", "Baz"))
 * }
 * This will produce the concatenated list string, "Foo, Bar, and Baz" as seen in
 * the following:
 * <table class="striped">
 * <caption style="display:none">Formatting examples</caption>
 * <thead>
 * <tr><th scope="col"></th>
 *     <th scope="col">FULL</th>
 *     <th scope="col">SHORT</th>
 *     <th scope="col">NARROW</th></tr>
 * </thead>
 * <tbody>
 * <tr><th scope="row" style="text-align:left">STANDARD</th>
 *     <td>Foo, Bar, and Baz</td>
 *     <td>Foo, Bar, &amp; Baz</td>
 *     <td>Foo, Bar, Baz</td>
 * <tr><th scope="row" style="text-align:left">OR</th>
 *     <td>Foo, Bar, or Baz</td>
 *     <td>Foo, Bar, or Baz</td>
 *     <td>Foo, Bar, or Baz</td>
 * <tr><th scope="row" style="text-align:left">UNIT</th>
 *     <td>Foo, Bar, Baz</td>
 *     <td>Foo, Bar, Baz</td>
 *     <td>Foo Bar Baz</td>
 * </tbody>
 * </table>
 * Note: these examples are from CLDR, there could be different results from other locale providers.
 * <p>
 * Alternatively, Locale, Type, and/or Style independent instances
 * can be created with {@link #getInstance(String[])}. The String array to the
 * method specifies the delimiting patterns for the start/middle/end portion of
 * the formatted string, as well as optional specialized patterns for two or three
 * elements. Refer to the method description for more detail.
 * <p>
 * On parsing, if some ambiguity is found in the input string, such as delimiting
 * sequences in the input string, the result, when formatted with the same formatting, does not
 * re-produce the input string. For example, a two element String list
 * "a, b,", "c" will be formatted as "a, b, and c", but may be parsed as three elements
 * "a", "b", "c".
 *
 * @implSpec This class is immutable and thread-safe
 *
 * @spec https://www.unicode.org/reports/tr35 Unicode Locale Data Markup Language (LDML)
 * @since 22
 */
public final class ListFormat extends Format {

    @Serial
    private static final long serialVersionUID = 5272525550078071946L;

    private static final int START = 0;
    private static final int MIDDLE = 1;
    private static final int END = 2;
    private static final int TWO = 3;
    private static final int THREE = 4;
    private static final int PATTERN_ARRAY_LENGTH = THREE + 1;
    private static final int PLACEHOLDER_LENGTH = 3; // i.e., "{i}".length()

    /**
     * The locale to use for formatting list patterns.
     * @serial
     */
    private final Locale locale;

    /**
     * The array of five pattern Strings. Each element corresponds to the Unicode LDML's
     * `listPatternsPart` type, i.e, start/middle/end/two/three.
     * @serial
     */
    private final String[] patterns;

    private transient String startBefore;
    private transient String startBetween;
    private transient String middleBetween;
    private transient String endBetween;
    private transient String endAfter;

    private ListFormat(Locale l, String[] patterns) {
        locale = l;
        this.patterns = patterns;
        init();
    }

    private void init() {
        // check for null pattern elements
        for (String elem : patterns) {
            if (elem == null) {
                throw new IllegalArgumentException("patterns array contains one or more null elements");
            }
        }

        // Get pattern strings. Pattern conditions from LDML are:
        // - it contains the placeholders {0}, {1}, and {2} ("3"-pattern only) in order
        // - "start" and "middle" patterns end with the {1} placeholder
        // - "middle" and "end" patterns begin with the {0} placeholder
        var pattern = patterns[START];
        var placeholderPositions = findPlaceholders(pattern);
        if (placeholderPositions != null &&
                placeholderPositions[1] + PLACEHOLDER_LENGTH == pattern.length()) {
            startBefore = pattern.substring(0, placeholderPositions[0]);
            startBetween = pattern.substring(placeholderPositions[0] + PLACEHOLDER_LENGTH,
                    placeholderPositions[1]);
        } else {
            throw new IllegalArgumentException("start pattern is incorrect: " + pattern);
        }

        pattern = patterns[MIDDLE];
        placeholderPositions = findPlaceholders(pattern);
        if (placeholderPositions != null &&
                placeholderPositions[0] == 0 &&
                placeholderPositions[1] + PLACEHOLDER_LENGTH == pattern.length()) {
            middleBetween = pattern.substring(placeholderPositions[0] + PLACEHOLDER_LENGTH,
                    placeholderPositions[1]);
        } else {
            throw new IllegalArgumentException("middle pattern is incorrect: " + pattern);
        }

        pattern = patterns[END];
        placeholderPositions = findPlaceholders(pattern);
        if (placeholderPositions != null && placeholderPositions[0] == 0) {
            endBetween = pattern.substring(placeholderPositions[0] + PLACEHOLDER_LENGTH,
                    placeholderPositions[1]);
            endAfter = pattern.substring(placeholderPositions[1] + PLACEHOLDER_LENGTH);
        } else {
            throw new IllegalArgumentException("end pattern is incorrect: " + pattern);
        }

        // Validate two/three patterns, if given. Otherwise, generate them
        pattern = patterns[TWO];
        if (!pattern.isEmpty()) {
            if (findPlaceholders(pattern) == null) {
                throw new IllegalArgumentException("pattern for two is incorrect: " + pattern);
            }
        } else {
            patterns[TWO] = startBefore + "{0}" + endBetween + "{1}" + endAfter;
        }
        pattern = patterns[THREE];
        if (!pattern.isEmpty()) {
            placeholderPositions = findPlaceholders(pattern);
            if (placeholderPositions == null || placeholderPositions[2] == -1) {
                throw new IllegalArgumentException("pattern for three is incorrect: " + pattern);
            }
        } else {
            patterns[THREE] = startBefore + "{0}" + startBetween + "{1}" + endBetween + "{2}" + endAfter;
        }
    }

    /**
     * {@return the available locales that support ListFormat}
     */
    public static Locale[] getAvailableLocales() {
        // Same as a typical format class
        return DateFormat.getAvailableLocales();
    }

    /**
     * {@return the ListFormat object for the default
     * {@link Locale.Category#FORMAT FORMAT Locale}, {@link Type#STANDARD STANDARD} type,
     * and {@link Style#FULL FULL} style}
     */
    public static ListFormat getInstance() {
        return getInstance(Locale.getDefault(Locale.Category.FORMAT), Type.STANDARD, Style.FULL);
    }

    /**
     * {@return the ListFormat object for the specified {@link Locale}, {@link Type Type},
     * and {@link Style Style}}
     * @param locale {@code Locale} to be used, not null
     * @param type type of the ListFormat. One of {@code STANDARD}, {@code OR},
     *             or {@code UNIT}, not null
     * @param style style of the ListFormat. One of {@code FULL}, {@code SHORT},
     *              or {@code NARROW}, not null
     * @throws NullPointerException if any of the arguments are null
     */
    public static ListFormat getInstance(Locale locale, Type type, Style style) {
        Objects.requireNonNull(locale);
        Objects.requireNonNull(type);
        Objects.requireNonNull(style);
        return new ListFormat(locale, LocaleProviderAdapter.forType(LocaleProviderAdapter.Type.CLDR)
                .getLocaleResources(locale)
                .getListPatterns(type, style));
    }

    /**
     * {@return the ListFormat object for the specified patterns}
     * <p>
     * This factory returns an instance based on the customized patterns array,
     * instead of letting the runtime provide appropriate patterns for the {@code Locale},
     * {@code Type}, or {@code Style}.
     * <p>
     * The patterns array should contain five String patterns, each corresponding to the Unicode LDML's
     * {@code listPatternPart}, i.e., "start", "middle", "end", two element, and three element patterns
     * in this order. Each pattern contains "{0}" and "{1}" (and "{2}" for the three element pattern)
     * placeholders that are substituted with the passed input strings on formatting.
     * If the length of the patterns array is not 5, an {@code IllegalArgumentException}
     * is thrown.
     * <p>
     * Each pattern string is first parsed as follows. Literals in parentheses, such as
     * "start_before", are optional:
     * <blockquote><pre>
     * start := (start_before){0}start_between{1}
     * middle := {0}middle_between{1}
     * end := {0}end_between{1}(end_after)
     * two := (two_before){0}two_between{1}(two_after)
     * three := (three_before){0}three_between1{1}three_between2{2}(three_after)
     * </pre></blockquote>
     * If two or three pattern string is empty, it falls back to
     * {@code "(start_before){0}end_between{1}(end_after)"},
     * {@code "(start_before){0}start_between{1}end_between{2}(end_after)"} respectively.
     * If parsing of any pattern string for start, middle, end, two, or three fails,
     * it throws an {@code IllegalArgumentException}.
     * <p>
     * On formatting, the input string list with {@code n} elements substitutes above
     * placeholders based on the number of elements:
     * <blockquote><pre>
     * n = 1: {0}
     * n = 2: parsed pattern for "two"
     * n = 3: parsed pattern for "three"
     * n > 3: (start_before){0}start_between{1}middle_between{2} ... middle_between{m}end_between{n}(end_after)
     * </pre></blockquote>
     * As an example, the following table shows a pattern array which is equivalent to
     * {@code STANDARD} type, {@code FULL} style in US English:
     * <table class="striped">
     * <caption style="display:none">Standard/Full Patterns in US English</caption>
     * <thead>
     * <tr><th scope="col">Pattern Kind</th>
     *     <th scope="col">Pattern String</th></tr>
     * </thead>
     * <tbody>
     * <tr><th scope="row" style="text-align:left">start</th>
     *     <td>"{0}, {1}"</td>
     * <tr><th scope="row" style="text-align:left">middle</th>
     *     <td>"{0}, {1}"</td>
     * <tr><th scope="row" style="text-align:left">end</th>
     *     <td>"{0}, and {1}"</td>
     * <tr><th scope="row" style="text-align:left">two</th>
     *     <td>"{0} and {1}"</td>
     * <tr><th scope="row" style="text-align:left">three</th>
     *     <td>""</td>
     * </tbody>
     * </table>
     * Here are the resulting formatted strings with the above pattern array.
     * <table class="striped">
     * <caption style="display:none">Formatting examples</caption>
     * <thead>
     * <tr><th scope="col">Input String List</th>
     *     <th scope="col">Formatted String</th></tr>
     * </thead>
     * <tbody>
     * <tr><th scope="row" style="text-align:left">"Foo", "Bar", "Baz", "Qux"</th>
     *     <td>"Foo, Bar, Baz, and Qux"</td>
     * <tr><th scope="row" style="text-align:left">"Foo", "Bar", "Baz"</th>
     *     <td>"Foo, Bar, and Baz"</td>
     * <tr><th scope="row" style="text-align:left">"Foo", "Bar"</th>
     *     <td>"Foo and Bar"</td>
     * <tr><th scope="row" style="text-align:left">"Foo"</th>
     *     <td>"Foo"</td>
     * </tbody>
     * </table>
     *
     * @param patterns array of patterns, not null
     * @throws IllegalArgumentException if the length {@code patterns} array is not 5, or
     *          any of {@code start}, {@code middle}, {@code end}, {@code two}, or
     *          {@code three} patterns cannot be parsed.
     * @throws NullPointerException if {@code patterns} is null.
     */
    public static ListFormat getInstance(String[] patterns) {
        Objects.requireNonNull(patterns);
        if (patterns.length != PATTERN_ARRAY_LENGTH) {
            throw new IllegalArgumentException("Pattern array length should be " + PATTERN_ARRAY_LENGTH);
        }
        return new ListFormat(Locale.ROOT, Arrays.copyOf(patterns, PATTERN_ARRAY_LENGTH));
    }

    /**
     * {@return the {@code Locale} of this ListFormat}
     *
     * The {@code locale} is defined by {@link #getInstance(Locale, Type, Style)} or
     * {@link #getInstance(String[])}.
     */
    public Locale getLocale() {
        return locale;
    }

    /**
     * {@return the patterns used in this ListFormat}
     *
     * The {@code patterns} are defined by {@link #getInstance(Locale, Type, Style)} or
     * {@link #getInstance(String[])}.
     */
    public String[] getPatterns() {
        return Arrays.copyOf(patterns, patterns.length);
    }

    /**
     * {@return the string that consists of the input strings, concatenated with the
     * patterns of this {@code ListFormat}}
     * @apiNote Formatting the string from an excessively long list may exceed memory
     *          or string sizes.
     * @param input The list of input strings to format. There should at least
     *              one String element in this list, otherwise an {@code IllegalArgumentException}
     *              is thrown.
     * @throws IllegalArgumentException if the length of {@code input} is zero.
     * @throws NullPointerException if {@code input} is null.
     */
    public String format(List<String> input) {
        Objects.requireNonNull(input);

        return format(input, StringBufFactory.of(),
                DontCareFieldPosition.INSTANCE).toString();
    }

    /**
     * Formats an object and appends the resulting text to a given string
     * buffer. The object should either be a List or an array of Objects.
     *
     * @apiNote Formatting the string from an excessively long list or array
     *          may exceed memory or string sizes.
     * @param obj    The object to format. Must be a List or an array
     *               of Object.
     * @param toAppendTo    where the text is to be appended
     * @param pos    Ignored. Not used in ListFormat. May be null
     * @return       the string buffer passed in as {@code toAppendTo},
     *               with formatted text appended
     * @throws    NullPointerException if {@code obj} or {@code toAppendTo} is null
     * @throws    IllegalArgumentException if {@code obj} is neither a {@code List}
     *               nor an array of {@code Object}s, or its length is zero.
     */
    @Override
    public StringBuffer format(Object obj, StringBuffer toAppendTo, FieldPosition pos) {
        Objects.requireNonNull(obj);
        Objects.requireNonNull(toAppendTo);

        return format(obj, StringBufFactory.of(toAppendTo)).asStringBuffer();
    }

    @Override
    StringBuf format(Object obj, StringBuf toAppendTo, FieldPosition pos) {
        Objects.requireNonNull(obj);
        Objects.requireNonNull(toAppendTo);

        return format(obj, toAppendTo);
    }

    private StringBuf format(Object obj, StringBuf toAppendTo) {
        if (obj instanceof Object[] objs) {
            return generateMessageFormat(objs).format(objs, toAppendTo, DontCareFieldPosition.INSTANCE);
        } else if (obj instanceof List<?> objs) {
            var a = objs.toArray(new Object[0]);
            return generateMessageFormat(a).format(a, toAppendTo, DontCareFieldPosition.INSTANCE);
        } else {
            throw new IllegalArgumentException("The object to format should be a List<Object> or an Object[]");
        }
    }

    /**
     * {@return the parsed list of strings from the {@code source} string}
     *
     * Note that {@link #format(List)} and this method
     * may not guarantee a round-trip, if the input strings contain ambiguous
     * delimiters. For example, a two element String list {@code "a, b,", "c"} will be
     * formatted as {@code "a, b, and c"}, but may be parsed as three elements
     * {@code "a", "b", "c"}.
     *
     * @param source the string to parse, not null.
     * @throws ParseException if parse failed
     * @throws NullPointerException if source is null
     */
    public List<String> parse(String source) throws ParseException {
        var pp = new ParsePosition(0);
        if (parseObject(source, pp) instanceof List<?> orig) {
            // parseObject() should've returned List<String>
            return orig.stream().map(o -> (String)o).toList();
        } else {
            throw new ParseException("Parse failed", pp.getErrorIndex());
        }
    }

    /**
     * Parses text from a string to produce a list of strings.
     * <p>
     * The method attempts to parse text starting at the index given by
     * {@code parsePos}.
     * If parsing succeeds, then the index of {@code parsePos} is updated
     * to the index after the last character used (parsing does not necessarily
     * use all characters up to the end of the string), and the parsed
     * object is returned. The updated {@code parsePos} can be used to
     * indicate the starting point for the next call to parse additional text.
     * If an error occurs, then the index of {@code parsePos} is not
     * changed, the error index of {@code parsePos} is set to the index of
     * the character where the error occurred, and null is returned.
     * See the {@link #parse(String)} method for more information
     * on list parsing.
     *
     * @param source A string, part of which should be parsed.
     * @param parsePos A {@code ParsePosition} object with index and error
     *            index information as described above.
     * @return A list of string parsed from the {@code source}.
     *            In case of error, returns null.
     * @throws NullPointerException if {@code source} or {@code parsePos} is null.
     * @throws IndexOutOfBoundsException if the starting index given by
     *            {@code parsePos} is outside {@code source}.
     */
    @Override
    public Object parseObject(String source, ParsePosition parsePos) {
        Objects.requireNonNull(source);
        Objects.requireNonNull(parsePos);
        var startPattern = findPattern(source, parsePos.getIndex(), startBefore, startBetween);
        var endPattern = findPattern(source, parsePos.getIndex(), endBetween, endAfter);
        Object parsed = null;
        if (startPattern != null && endPattern != null) {
            // get endPattern to the last
            var ep = endPattern;
            while ((ep = findPattern(source, ep[1], endBetween, endAfter)) != null) {
                endPattern = ep;
            }

            var startEnd = startPattern[1];
            var endStart = endPattern[0];
            if (startEnd <= endStart) {
                var mid = source.substring(startEnd, endStart);
                var count = 3;
                var mbLength = middleBetween.length();
                if (mbLength > 0) {
                    var midIndex = 0;
                    while ((midIndex = mid.indexOf(middleBetween, midIndex)) >= 0) {
                        count++;
                        midIndex += mbLength;
                    }
                }
                parsed = new MessageFormat(createMessageFormatString(count), locale).parseObject(source, parsePos);
            }
        }

        if (parsed == null) {
            // now try exact number patterns
            parsed = new MessageFormat(patterns[TWO], locale).parseObject(source, parsePos);
            if (parsed == null) {
                parsed = new MessageFormat(patterns[THREE], locale).parseObject(source, parsePos);
            }
        }

        // return the entire source from parsePos if still no match
        if (parsed == null) {
            parsed = new String[]{source.substring(parsePos.getIndex())};
            parsePos.setIndex(source.length());
        }

        if (parsed instanceof Object[] objs) {
            parsePos.setErrorIndex(-1);
            return Arrays.asList(objs);
        } else {
            // MessageFormat.parseObject() failed
            return null;
        }
    }

    @Override
    public AttributedCharacterIterator formatToCharacterIterator(Object arguments) {
        Objects.requireNonNull(arguments);

        if (arguments instanceof List<?> objs) {
            var a = objs.toArray(new Object[0]);
            return generateMessageFormat(a).formatToCharacterIterator(a);
        } else if (arguments instanceof Object[] objs) {
            return generateMessageFormat(objs).formatToCharacterIterator(objs);
        } else {
            throw new IllegalArgumentException("The arguments should be a List<Object> or an Object[]");
        }
    }

    /**
     * Compares the specified object with this {@code ListFormat} for equality.
     * Returns {@code true} if the specified object is also a {@code ListFormat}, and
     * {@code locale} and {@code patterns}, returned from {@link #getLocale()}
     * and {@link #getPatterns()} respectively, are equal.
     * @param obj the object to be compared for equality.
     * @return {@code true} if the specified object is equal to this {@code ListFormat}
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj instanceof ListFormat other) {
            return locale.equals(other.locale) &&
                Arrays.equals(patterns, other.patterns);
        }

        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(locale, Arrays.hashCode(patterns));
    }

    /**
     * {@return a string identifying this {@code ListFormat}, for debugging}
     */
    @Override
    public String toString() {
        return
            """
            ListFormat [locale: "%s", start: "%s", middle: "%s", end: "%s", two: "%s", three: "%s"]
            """.formatted(locale.getDisplayName(), patterns[START], patterns[MIDDLE], patterns[END], patterns[TWO], patterns[THREE]);
    }

    private MessageFormat generateMessageFormat(Object[] input) {
        var len = input.length;
        return switch (len) {
            case 0 -> throw new IllegalArgumentException("There should at least be one input string");
            case 1 -> new MessageFormat("{0}", locale);
            case 2, 3 -> new MessageFormat(patterns[len + 1], locale);
            default -> new MessageFormat(createMessageFormatString(len), locale);
        };
    }

    private String createMessageFormatString(int count) {
        var sb = new StringBuilder(256).append(patterns[START]);
        IntStream.range(2, count - 1).forEach(i -> sb.append(middleBetween).append("{").append(i).append("}"));
        sb.append(endBetween)
            .append("{").append(count - 1).append("}")
            .append(endAfter);
        return sb.toString();
    }

    @java.io.Serial
    private void readObject(ObjectInputStream stream)
            throws IOException, ClassNotFoundException {
        stream.defaultReadObject();
        try {
            init();
        } catch (IllegalArgumentException iae) {
            throw new IOException("Deserialization failed.", iae);
        }
    }

    /**
     * A ListFormat type - {@link #STANDARD STANDARD}, {@link #OR OR}, and
     * {@link #UNIT UNIT}.
     * <p>
     * {@code Type} is an enum which represents the type for formatting
     * a list within a given {@code ListFormat} instance. It determines
     * the punctuation and the connecting words in the formatted text.
     *
     * @since 22
     */
    public enum Type {

        /**
         * The {@code STANDARD} ListFormat type. This is the default
         * type, which concatenates elements in "and" enumeration.
         */
        STANDARD,

        /**
         * The {@code OR} ListFormat type. This type concatenates
         * elements in "or" enumeration.
         */
        OR,

        /**
         * The {@code UNIT} ListFormat type. This type concatenates
         * elements, useful for enumerating units.
         */
        UNIT
    }

    /**
     * A ListFormat style - {@link #FULL FULL}, {@link #SHORT SHORT},
     * and {@link #NARROW NARROW}.
     * <p>
     * {@code Style} is an enum which represents the style for formatting
     * a list within a given {@code ListFormat} instance.
     *
     * @since 22
     */
    public enum Style {

        /**
         * The {@code FULL} ListFormat style. This is the default style, which typically is the
         * full description of the text and punctuation that appear between the list elements.
         * Suitable for elements, such as "Monday", "Tuesday", "Wednesday", etc.
         */
        FULL,

        /**
         * The {@code SHORT} ListFormat style. This style is typically an abbreviation
         * of the text and punctuation that appear between the list elements.
         * Suitable for elements, such as "Mon", "Tue", "Wed", etc.
         */
        SHORT,

        /**
         * The {@code NARROW} ListFormat style. This style is typically the shortest description
         * of the text and punctuation that appear between the list elements.
         * Suitable for elements, such as "M", "T", "W", etc.
         */
        NARROW
    }

    /**
     * {@return the positions of the "{0}", "{1}", and "{2}" placeholders in the
     * given pattern string, or null if the pattern is invalid}
     *
     * The returned array contains -1 for "{2}" if that placeholder is absent.
     *
     * @param pattern pattern string to parse
     */
    private static int[] findPlaceholders(String pattern) {
        var positions = new int[3];
        for (int i = 0; i < positions.length; i++) {
            positions[i] = pattern.indexOf("{" + i + "}");
        }

        // Check the existence and order of the placeholders
        if (positions[0] == -1 ||
            positions[1] == -1 ||
            positions[0] + PLACEHOLDER_LENGTH > positions[1] ||
            positions[2] != -1 && positions[1] + PLACEHOLDER_LENGTH > positions[2]) {
            return null;
        }

        return positions;
    }

    /**
     * {@return the start and end positions of the first pattern found in
     * the given {@code source} starting at {@code pos}, or null if no such
     * pattern exists}
     *
     * The pattern must contain at least one character between the
     * {@code prefix} and {@code suffix} strings. The returned end position is
     * exclusive.
     *
     * @param source string to search
     * @param pos position at which to start the search
     * @param prefix starting string within the pattern
     * @param suffix ending string within the pattern
     */
    private static int[] findPattern(String source, int pos, String prefix, String suffix) {
        var prefixPos = source.indexOf(prefix, pos);
        var suffixPos = prefixPos != -1 ? source.indexOf(suffix, prefixPos + prefix.length() + 1) : -1;

        return prefixPos < suffixPos ?
            new int[] {prefixPos, suffixPos + suffix.length()} : null;
    }
}
