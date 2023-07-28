/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import sun.util.locale.provider.LocaleProviderAdapter;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * {@code ListFormat} provides a means to produce a list of concatenated
 * strings in a language-sensitive way. Use this to construct a list
 * of strings displayed for end users. For example, listing countries for US, UK,
 * and Japan generates "United States, United Kingdom, and Japan". This class
 * provides the functionality defined in Unicode Consortium's LDML specification for
 * <a href="https://www.unicode.org/reports/tr35/tr35-general.html#ListPatterns">
 * List Patterns</a>.
 * <p>
 * Three types of concatenation are provided; {@link Type#STANDARD STANDARD},
 * {@link Type#OR OR}, and {@link Type#UNIT UNIT}, also three styles for each
 * type are provided: {@link Style#FULL FULL}, {@link Style#SHORT SHORT}, and
 * {@link Style#NARROW NARROW}. The following snippet is an example of formatting
 * the list of Strings {@code ["Foo", "Bar", "Baz"]} in US English with
 * {@code STANDARD}, {@code FULL} (typical case):
 * {@snippet lang=java :
 * ListFormat.getInstance(Locale.US, ListFormat.Type.STANDARD, ListFormat.Style.FULL)
 *     .format(List.of("Foo", "Bar", "Baz"))
 * }
 * This will produce the concatenated list string, "Foo, Bar, and Baz" as seen in
 * the following table:
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
 * <p>
 * Alternatively, Locale, Type, and/or Style invariant patterns can be
 * specified with {@link #getInstance(String[])}. The String array to the
 * method specifies the delimiting patterns for the start/middle/end portion of
 * the formatted string, as well as optional specialized patterns for two or three
 * elements. Refer to the method description for more detail.
 *
 * @implSpec This class is immutable and thread-safe
 *
 * @since 22
 */
public class ListFormat extends Format {

    @Serial
    private static final long serialVersionUID = 5272525550078071946L;

    private static final int START = 0;
    private static final int MIDDLE = 1;
    private static final int END = 2;
    private static final int TWO = 3;
    private static final int THREE = 4;
    private static final int PATTERN_ARRAY_LENGTH = THREE + 1;

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

    private transient Pattern startPattern;
    private transient String middleBetween;
    private transient Pattern endPattern;

    private ListFormat(Locale l, String[] patterns) {
        locale = l;
        this.patterns = patterns;
        init();
    }

    private void init() {
        // get pattern strings
        var m = Pattern.compile("(?<startBefore>.*?)\\{0}(?<startBetween>.*?)\\{1}").matcher(patterns[START]);
        String startBefore;
        String startBetween;
        if (m.matches()) {
            startBefore = m.group("startBefore");
            startBetween = m.group("startBetween");
        } else {
            throw new IllegalArgumentException("start pattern is incorrect");
        }
        m = Pattern.compile("\\{0}(?<middleBetween>.*?)\\{1}").matcher(patterns[MIDDLE]);
        if (m.matches()) {
            middleBetween = m.group("middleBetween");
        } else {
            throw new IllegalArgumentException("middle pattern is incorrect");
        }
        m = Pattern.compile("\\{0}(?<endBetween>.*?)\\{1}(?<endAfter>.*?)").matcher(patterns[END]);
        String endBetween;
        String endAfter;
        if (m.matches()) {
            endBetween = m.group("endBetween");
            endAfter = m.group("endAfter");
        } else {
            throw new IllegalArgumentException("end pattern is incorrect");
        }

        // two/three patterns, if empty
        if (patterns[TWO] == null || patterns[TWO].isEmpty()) {
            patterns[TWO] = startBefore + "{0}" + endBetween + "{1}" + endAfter;
        }
        if (patterns[THREE] == null || patterns[THREE].isEmpty()) {
            patterns[THREE] = startBefore + "{0}" + startBetween + "{1}" + endBetween + "{2}" + endAfter;
        }
        startPattern = Pattern.compile(startBefore + "(.+?)" + startBetween);
        endPattern = Pattern.compile(endBetween + "(.+?)" + endAfter);
    }

    /**
     * {@return the available locales that support list formatting}
     */
    public static Locale[] getAvailableLocales() {
        // Same as a typical format class
        return DateFormat.getAvailableLocales();
    }

    /**
     * {@return the list format object for the default {@code Locale}, {@code STANDARD},
     * and {@code FULL}}
     */
    public static ListFormat getInstance() {
        return getInstance(Locale.getDefault(Locale.Category.FORMAT), Type.STANDARD, Style.FULL);
    }

    /**
     * {@return the list format object for the specified {@code Locale}, {@code Type},
     * and {@code Style}}
     * @param locale Locale to be used, not null
     * @param type type of the list format. One of STANDARD/OR/UNIT, not null
     * @param style style of the list format. One of FULL/SHORT/NARROW, not null
     * @throws NullPointerException if any of the arguments is null
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
     * {@return the list format object for the specified patterns array}
     * <p>
     * This factory produces an instance based on the customized patterns array,
     * instead of letting the runtime provide appropriate patterns for the Locale/Type/Style.
     * <p>
     * Patterns array should contain five Strings of patterns, each correspond to the Unicode LDML's
     * {@code listPatternPart}, i.e., "start", "middle", "end", "2", and "3" patterns
     * in this order. Each pattern contains "{0}" and "{1}" (and "{2}" for pattern "3")
     * placeholders that are substituted with the passed input strings on formatting.
     * If the length of the patterns array is less than 5, an {@code IllegalArgumentException}
     * is thrown.
     * <p>
     * Each pattern string is first parsed as follows. Patterns in parentheses are optional:
     * <blockquote><pre>
     * start := (start_before){0}start_between{1}
     * middle := {0}middle_between{1}
     * end := {0}end_between{1}(end_after)
     * two := (two_before){0}two_between{1}(two_after)
     * three := (three_before){0}three_between{1}three_between{2}(three_after)
     * </pre></blockquote>
     * If parsing of the pattern string fails, start/middle/end fall back to "{0}{1}",
     * and two/three fall back to empty strings.
     * Then the input string list with {@code n} elements substitutes these
     * placeholders:
     * <blockquote><pre>
     * (start_before){0}start_between{1}middle_between{2} ... middle_between{m}end_between{n}(end_after)
     * </pre></blockquote>
     * If the number of elements in the input string list is either 2 or 3, and the
     * pattern for those (either "two" or "three", respectively) is not empty, the
     * pattern is used as it is.
     * Following table shows patterns array which is equivalent to
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
     * <tr><th scope="row" style="text-align:left">2</th>
     *     <td>"{0} and {1}"</td>
     * <tr><th scope="row" style="text-align:left">3</th>
     *     <td>""</td>
     * </tbody>
     * </table>
     * Here are the resulting formatted strings with the above pattern array.
     * <table class="striped">
     * <caption style="display:none">Formatting examples</caption>
     * <thead>
     * <tr><th scope="col">Input String Array</th>
     *     <th scope="col">Formatted String</th></tr>
     * </thead>
     * <tbody>
     * <tr><th scope="row" style="text-align:left">["Foo", "Bar", "Baz", "Qux"]</th>
     *     <td>"Foo, Bar, Baz, and Qux"</td>
     * <tr><th scope="row" style="text-align:left">["Foo", "Bar", "Baz"]</th>
     *     <td>"Foo, Bar, and Baz"</td>
     * <tr><th scope="row" style="text-align:left">["Foo", "Bar"]</th>
     *     <td>"Foo and Bar"</td>
     * <tr><th scope="row" style="text-align:left">["Foo"]</th>
     *     <td>"Foo"</td>
     * </tbody>
     * </table>
     *
     * @param patterns array of patterns, not null
     * @throws IllegalArgumentException if the length {@code patterns} array is less than 5, or
     *          any of {@code start}, {@code middle}, {@code end} patterns cannot be parsed.
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
     * {@return the string that consists of the input strings, concatenated with the
     * patterns specified, or derived from {@code Locale}, {@code Type}, and {@code Style}}
     * @param input The list of input strings to format. There should at least
     *              one String element in this list, otherwise an {@code IllegalArgumentException}
     *              is thrown.
     * @throws IllegalArgumentException if the length of {@code input} is zero.
     * @throws NullPointerException if {@code input} is null.
     */
    public String format(List<String> input) {
        Objects.requireNonNull(input);
        return format(input, new StringBuffer(),
                DontCareFieldPosition.INSTANCE).toString();
    }

    /**
     * Formats an object and appends the resulting text to a given string
     * buffer. The object should either be a List or an array of Objects.
     * If the {@code pos} argument identifies a field used by the format,
     * then its indices are set to the beginning and end of the first such
     * field encountered.
     *
     * @param obj    The object to format. Must be a List or an array
     *               of Object.
     * @param toAppendTo    where the text is to be appended
     * @param pos    A {@code FieldPosition} identifying a field
     *               in the formatted text
     * @return       the string buffer passed in as {@code toAppendTo},
     *               with formatted text appended
     * @throws    NullPointerException if {@code toAppendTo} or
     *            {@code pos} is null
     * @throws    IllegalArgumentException if it cannot format the given
     *            object
     */
    @Override
    public StringBuffer format(Object obj, StringBuffer toAppendTo, FieldPosition pos) {
        Objects.requireNonNull(toAppendTo);
        Objects.requireNonNull(pos);
        if (obj instanceof Object[] objs) {
            return generateMessageFormat(objs).format(objs, toAppendTo, pos);
        } else if (obj instanceof List<?> objs) {
            var a = objs.toArray(new Object[0]);
            return generateMessageFormat(a).format(a, toAppendTo, pos);
        } else {
            throw new IllegalArgumentException("The object to format should be an Object list");
        }
    }

    /**
     * {@return the parsed list of Strings from the {@code source} String}
     *
     * Note that {@link #format(List)} and this method
     * may not guarantee a round-trip, if the input strings contain ambiguous
     * delimiters. For example, a String array {@code ["a, b,", "c"]} will be
     * formatted as {@code "a, b, and c"}, but may be parsed as
     * {@code ["a", "b", "c"]}.
     *
     * @param source the String to parse, not null.
     * @throws ParseException if parse failed
     * @throws NullPointerException if source is null
     */
    public List<String> parse(String source) throws ParseException {
        var pp = new ParsePosition(0);
        if (parseObject(source, pp) instanceof List<?> orig) {
            return orig.stream().map(Object::toString).toList();
        } else {
            throw new ParseException("Parse failed", pp.getErrorIndex());
        }
    }

    /**
     * Parses text from a string to produce a list of {@code String}s.
     * <p>
     * The method attempts to parse text starting at the index given by
     * {@code parsePos}.
     * If parsing succeeds, then the index of {@code parsePos} is updated
     * to the index after the last character used (parsing does not necessarily
     * use all characters up to the end of the string), and the parsed
     * object is returned. The updated {@code parsePos} can be used to
     * indicate the starting point for the next call to this method.
     * If an error occurs, then the index of {@code parsePos} is not
     * changed, the error index of {@code parsePos} is set to the index of
     * the character where the error occurred, and null is returned.
     * See the {@link #parse(String)} method for more information
     * on list parsing.
     *
     * @param source A {@code String}, part of which should be parsed.
     * @param parsePos A {@code ParsePosition} object with index and error
     *            index information as described above.
     * @return A {@code List} of {@code String} parsed from the string. In case of
     *         error, returns null.
     * @throws NullPointerException if {@code source} or {@code parsePos} is null.
     */
    @Override
    public Object parseObject(String source, ParsePosition parsePos) {
        Objects.requireNonNull(source);
        var sm = startPattern.matcher(source);
        var em = endPattern.matcher(source);
        Object parsed = null;
        if (sm.find(parsePos.index) && em.find(parsePos.index)) {
            // get em to the last
            var c = em.start();
            while (em.find()) {
                c = em.start();
            }
            em.find(c);
            var startEnd = sm.end();
            var endStart = em.start();
            if (startEnd <= endStart) {
                var mid = source.substring(startEnd, endStart);
                var count = mid.split(middleBetween).length + 2;
                parsed = new MessageFormat(createMessageFormatString(count), locale).parseObject(source, parsePos);
            }
        }

        if (parsed == null) {
            // now try exact number patterns
            parsed = new MessageFormat(patterns[TWO], locale).parseObject(source, parsePos);
            if (parsed == null && !patterns[THREE].isEmpty()) {
                parsed = new MessageFormat(patterns[THREE], locale).parseObject(source, parsePos);
            }
        }

        // return the entire source if still no match
        if (parsed == null) {
            parsePos.setIndex(source.length());
            parsed = new String[]{source};
        }

        if (parsed instanceof Object[] objs) {
            return Arrays.asList(objs);
        }
        throw new InternalError("MessageFormat.parseObject() should return Object[]");
    }

    @Override
    public AttributedCharacterIterator formatToCharacterIterator(Object arguments) {
        if (arguments instanceof List<?> objs) {
            var a = objs.toArray(new Object[0]);
            return generateMessageFormat(a).formatToCharacterIterator(a);
        } else {
            throw new IllegalArgumentException("The arguments should be an Object array");
        }
    }

    /**
     * {@inheritDoc}
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
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return
            """
            ListFormat [locale: "%s", start: "%s", middle: "%s",  end: "%s", two: "%s", three: "%s"]
            """.formatted(locale.getDisplayName(), patterns[START], patterns[MIDDLE], patterns[END], patterns[TWO], patterns[THREE]);
    }

    private MessageFormat generateMessageFormat(Object[] input) {
        var len = input.length;
        return switch (len) {
            case 0 -> throw new IllegalArgumentException("There should at least be one input string");
            case 1 -> new MessageFormat("{0}", locale);
            case 2, 3 -> {
                var pattern = patterns[len + 1];
                if (pattern != null && !pattern.isEmpty()) {
                    yield new MessageFormat(pattern, locale);
                } else {
                    yield new MessageFormat(createMessageFormatString(len), locale);
                }
            }
            default -> new MessageFormat(createMessageFormatString(len), locale);
        };
    }

    private String createMessageFormatString(int count) {
        var sb = new StringBuilder(patterns[START]);
        IntStream.range(2, count - 1).forEach(i -> sb.append(middleBetween).append("{").append(i).append("}"));
        sb.append(patterns[END].replaceFirst("\\{0}", "").replaceFirst("\\{1}", "\\{" + (count - 1) + "\\}"));
        return sb.toString();
    }

    @java.io.Serial
    private void readObject(ObjectInputStream stream)
            throws IOException, ClassNotFoundException {
        stream.defaultReadObject();
        init();
    }

    /**
     * A list format type - {@code standard}, {@code or}, and {@code unit}.
     * <p>
     * {@code Type} is an enum which represents the type for formatting
     * a list within a given {@code ListFormat} instance.
     */
    public enum Type {

        /**
         * The {@code STANDARD} list format style. This is the default
         * type, which concatenates elements in "and" enumeration.
         */
        STANDARD,

        /**
         * The {@code OR} list format style. This style concatenates
         * elements in "or" enumeration.
         */
        OR,

        /**
         * The {@code UNIT} list format style. This style concatenates
         * elements, useful for enumerating units.
         */
        UNIT;

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * A list format style - {@code full}, {@code short}, and {@code narrow}.
     * <p>
     * {@code Style} is an enum which represents the style for formatting
     * a list within a given {@code ListFormat} instance.
     */
    public enum Style {

        /**
         * The {@code FULL} list format style.
         */
        FULL,

        /**
         * The {@code SHORT} list format style.
         */
        SHORT,

        /**
         * The {@code NARROW} list format style.
         */
        NARROW;

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
