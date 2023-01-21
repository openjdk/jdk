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
 * Three types of concatenation are provided; {@link Type#STANDARD},
 * {@link Type#OR}, and {@link Type#UNIT}, also three styles for each
 * type are provided; {@link Style#FULL}, {@link Style#SHORT}, and
 * {@link Style#NARROW}.
 * </p>
 * @implNote The default implementation utilizes {@link MessageFormat}
 * for formatting and parsing.
 *
 * @since 21
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
     * @serial
     */
    private final Locale locale;

    /**
     * @serial
     */
    private final String[] patterns;

    private String startBefore;
    private String startBetween;
    private String middleBetween;
    private String endBetween;
    private String endAfter;
    private Pattern startPattern;
    private Pattern endPattern;

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
     * {@return the list format object for the default {@code Locale}, {@link Type#STANDARD},
     * and {@link Style#FULL}}
     */
    public static ListFormat getInstance() {
        return getInstance(Locale.getDefault(Locale.Category.FORMAT), Type.STANDARD, Style.FULL);
    }

    /**
     * {@return the list format object for the specified {@code Locale}, {@link Type},
     * and {@link Style}}
     * @param locale Locale to be used, not null
     * @param type type of the list format. One of standard/or/unit
     * @param style style of the list format. One of full/short/narrow
     */
    public static ListFormat getInstance(Locale locale, Type type, Style style) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(style);
        return getInstance(locale, LocaleProviderAdapter.forType(LocaleProviderAdapter.Type.CLDR)
                .getLocaleResources(locale)
                .getListPatterns(type, style));
    }

    /**
     * {@return the list format object for the specified {@code Locale} and patterns array}
     * @param locale Locale to be used, not null
     * @param patterns array of patterns, not null
     */
    public static ListFormat getInstance(Locale locale, String[] patterns) {
        Objects.requireNonNull(locale);
        Objects.requireNonNull(patterns);
        if (patterns.length != PATTERN_ARRAY_LENGTH) {
            throw new IllegalArgumentException("Pattern array length should be " + PATTERN_ARRAY_LENGTH);
        }
        return new ListFormat(locale, patterns);
    }

    @Override
    public StringBuffer format(Object obj, StringBuffer toAppendTo, FieldPosition pos) {
        if (obj instanceof Object[] objs) {
            return generateMessageFormat(objs).format(objs, toAppendTo, pos);
        } else {
            throw new IllegalArgumentException("The object to format should be an Object array");
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
