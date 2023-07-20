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
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * {@code ListFormat} provides a means to produce a list of concatenated
 * objects in a language-sensitive way. Use this to construct a list
 * of objects displayed for end users. This class provides the functionality
 * defined in Unicode Consortium's LDML specification for
 * <a href="https://www.unicode.org/reports/tr35/tr35-general.html#ListPatterns">
 * List Patterns</a>.
 * <p>
 * Three types of concatenation are provided; {@link Type#STANDARD STANDARD},
 * {@link Type#OR OR}, and {@link Type#UNIT UNIT}, also two styles for each
 * type are provided; {@link Style#FULL FULL}, {@link Style#SHORT SHORT}, and
 * {@link Style#NARROW NARROW}. For example, an array of Strings
 * {@code ["Foo", "Bar", "Baz"]} may be formatted as follows in US English with
 * the following snippet ({@code STANDARD}, {@code FULL} case):
 * {@snippet lang=java :
 * ListFormat.getInstance(Locale.US, ListFormat.Type.STANDARD, ListFormat.Style.FULL)
 *     .format(new String[] {"Foo", "Bar", "Baz"})
 * }
 * Formatted strings will be:
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
 * Instead of relying on the locale, type, and style, a customized patterns can be
 * specified with an instance created by {@link #getInstance(String[])}.
 * @implNote The default implementation utilizes {@link MessageFormat}
 * for formatting and parsing.
 *
 * @since 22
 */
public class ListFormat extends Format {

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
     * The patterns array. Each element corresponds to the Unicode LDML's `listPatternsPart` type.
     * @serial
     */
    private final String[] patterns;

    private transient String startBefore;
    private transient String startBetween;
    private transient String middleBetween;
    private transient String endBetween;
    private transient String endAfter;
    private transient Pattern startPattern;
    private transient Pattern endPattern;

    private ListFormat(Locale l, String[] patterns) {
        locale = l;
        this.patterns = patterns;
        init();
    }

    private void init() {
        // get pattern strings
        var m = Pattern.compile("(?<startBefore>.*?)\\{0}(?<startBetween>.*?)\\{1}").matcher(patterns[START]);
        if (m.matches()) {
            startBefore = m.group("startBefore");
            startBetween = m.group("startBetween");
        } else {
            startBefore = "";
            startBetween = "";
        }
        m = Pattern.compile("\\{0}(?<middleBetween>.*?)\\{1}").matcher(patterns[MIDDLE]);
        if (m.matches()) {
            middleBetween = m.group("middleBetween");
        } else {
            middleBetween = "";
        }
        m = Pattern.compile("\\{0}(?<endBetween>.*?)\\{1}(?<endAfter>.*?)").matcher(patterns[END]);
        if (m.matches()) {
            endBetween = m.group("endBetween");
            endAfter = m.group("endAfter");
        } else {
            endBetween = "";
            endAfter = "";
        }

        startPattern = Pattern.compile(startBefore + "(.+?)" + startBetween);
        endPattern = Pattern.compile(endBetween + "(.+?)" + endAfter);
    }

    /**
     * {@return the list format object for the specified {@code Locale}, {@link Type},
     * and {@link Style}}
     * @param locale Locale to be used, not null
     * @param type type of the list format. One of standard/or/unit
     * @param style style of the list format. One of full/short/narrow
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
     * Patterns array consists of five Strings of patterns, each correspond to Unicode LDML's
     * {@code listPatternPart}, i.e., "start", "middle", "end", "2", and
     * "3" patterns. Each pattern contains "{0}" and "{1}" (and "{2}" for pattern "3")
     * placeholders that are substituted with the passed input strings on formatting.
     * <p>
     * Each pattern string is first parsed as follows. Patterns in parens are optional:
     * <blockquote><pre>
     * start := (start_before){0}start_between{1}
     * middle := {0}middle_between{1}
     * end := {0}end_between{1}(end_after)
     * two := (two_before){0}two_between{1}(two_after)
     * three := (three_before){0}three_between{1}three_between{2}(three_after)
     * </pre></blockquote>
     * then, the {@code n} elements in the input string array substitute these
     * placeholders:
     * <blockquote><pre>
     * (start_before){0}start_between{1}middle_between{2} ... middle_between{m}end_between{n}(end_after)
     * </pre></blockquote>
     * If the length of the input array is two or three, specific pattern for the length,
     * if exists, is used. Following table shows patterns array which is equivalent to
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
     * Here are the resulting formatted strings with the above pattern array:), it will be
     * formatted as:
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
     * </tbody>
     * </table>
     *
     * @param patterns array of patterns, not null
     */
    public static ListFormat getInstance(String[] patterns) {
        Objects.requireNonNull(patterns);
        if (patterns.length != PATTERN_ARRAY_LENGTH) {
            throw new IllegalArgumentException("Pattern array length should be " + PATTERN_ARRAY_LENGTH);
        }
        return new ListFormat(Locale.ROOT, patterns);
    }

    /**
     * {@return the string that consists of the input strings, concatenated with the
     * patterns specified, or derived from {@code Locale}, {@code Type}, and {@code Style}}
     * @param input The array of input strings to format. There should at least be two
     *              elements in the array, otherwise {@code IllegalArgumentException} is
     *              thrown
     * @throws IllegalArgumentException if the length of the input array is less than two
     */
    public String format(String[] input) {
        return format(input, new StringBuffer(),
                DontCareFieldPosition.INSTANCE).toString();
    }

    @Override
    public StringBuffer format(Object obj, StringBuffer toAppendTo, FieldPosition pos) {
        if (obj instanceof Object[] objs) {
            return generateMessageFormat(objs).format(objs, toAppendTo, pos);
        } else {
            throw new IllegalArgumentException("The object to format should be an Object array");
        }
    }

    /**
     * {@return the parsed array of Strings from the {@code source} String}
     * @param source the String to parse
     * @param position the position to parse
     * @throws ParseException if parse failed
     */
    public String[] parse(String source, ParsePosition position) throws ParseException {
        if (parseObject(source, position) instanceof Object[] orig) {
            return Arrays.copyOf(orig, orig.length, String[].class);
        } else {
            throw new ParseException("Parse failed", position.getErrorIndex());
        }
    }

    @Override
    public Object parseObject(String source, ParsePosition parsePos) {
        var sm = startPattern.matcher(source);
        var em = endPattern.matcher(source);
        Object ret;
        if (sm.find(parsePos.index) && em.find(parsePos.index)) {
            // get em to the last
            var c = em.start();
            while (em.find()) {
                c = em.start();
            }
            em.find(c);
//            System.out.println("start found: " + sm.group(0));
//            System.out.println("end found: " + em.group(0));
            var mid = source.substring(sm.end(), em.start());
//            System.out.println("middle found: " + mid);
            var count = mid.split(middleBetween).length + 2;
            ret = new MessageFormat(createMessageFormatString(count), locale).parseObject(source, parsePos);
        } else {
            // now try exact number patterns
            ret = new MessageFormat(patterns[TWO], locale).parseObject(source, parsePos);
            if (ret == null && !patterns[THREE].isEmpty()) {
                ret = new MessageFormat(patterns[THREE], locale).parseObject(source, parsePos);
            }
        }

        // return the entire source if still no match
//        if (ret == null) {
//            parsePos.setIndex(source.length());
//            var sa = new String[1];
//            sa[0] = source;
//            ret = sa;
//        }

        return ret;
    }

    @Override
    public AttributedCharacterIterator formatToCharacterIterator(Object arguments) {
        if (arguments instanceof Object[] objs) {
            return generateMessageFormat(objs).formatToCharacterIterator(objs);
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

        if (obj instanceof ListFormat lf) {
            return locale.equals(lf.locale) &&
                Arrays.equals(patterns, lf.patterns);
        }

        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(locale, patterns);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return
            """
            ListFormat -
                locale: %s
                start pattern: %s
                middle pattern: %s
                end pattern: %s
                pattern for two: %s
                pattern for three: %s
            """.formatted(locale.getDisplayName(), patterns[START], patterns[MIDDLE], patterns[END], patterns[TWO], patterns[THREE]);
    }

    private MessageFormat generateMessageFormat(Object[] objs) {
        return switch (objs.length) {
            case 0, 1 ->
                throw new IllegalArgumentException("The object array should at least contain two elements");
            case 2, 3 -> {
                var pattern = patterns[objs.length + 1];
                if (pattern != null && !pattern.isEmpty()) {
                    yield new MessageFormat(patterns[objs.length + 1], locale);
                } else {
                    yield new MessageFormat(createMessageFormatString(objs.length), locale);
                }
            }
            default -> new MessageFormat(createMessageFormatString(objs.length), locale);
        };
    }

    private String createMessageFormatString(int count) {
        var sb = new StringBuilder(patterns[START]);
        IntStream.range(2, count - 1).forEach(i -> sb.append(middleBetween).append("{" + i + "}"));
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
     * A list format type.
     * <p>
     * {@code Type} is an enum which represents the type for formatting
     * a list within a given {@code ListFormat} instance.
     */
    public enum Type {

        /**
         * The {@code STANDARD} list format style.
         */
        STANDARD,

        /**
         * The {@code OR} list format style.
         */
        OR,

        /**
         * The {@code UNIT} list format style.
         */
        UNIT;

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * A list format style.
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
