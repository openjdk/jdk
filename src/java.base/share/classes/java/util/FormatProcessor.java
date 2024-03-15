/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, Alibaba Group Holding Limited. All Rights Reserved.
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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.StringTemplate.Processor;
import java.lang.StringTemplate.Processor.Linkage;

import jdk.internal.javac.PreviewFeature;

/**
 * This {@link Processor} constructs a {@link String} result using
 * {@link Formatter} specifications and values found in the {@link StringTemplate}.
 * Unlike {@link Formatter}, {@link FormatProcessor} uses the value from the
 * embedded expression that immediately follows, without whitespace, the
 * <a href="../util/Formatter.html#syntax">format specifier</a>.
 * For example:
 * {@snippet :
 * FormatProcessor fmt = FormatProcessor.create(Locale.ROOT);
 * int x = 10;
 * int y = 20;
 * String result = fmt."%05d\{x} + %05d\{y} = %05d\{x + y}";
 * }
 * In the above example, the value of {@code result} will be {@code "00010 + 00020 = 00030"}.
 * <p>
 * Embedded expressions without a preceeding format specifier, use {@code %s}
 * by default.
 * {@snippet :
 * FormatProcessor fmt = FormatProcessor.create(Locale.ROOT);
 * int x = 10;
 * int y = 20;
 * String result1 = fmt."\{x} + \{y} = \{x + y}";
 * String result2 = fmt."%s\{x} + %s\{y} = %s\{x + y}";
 * }
 * In the above example, the value of {@code result1} and {@code result2} will
 * both be {@code "10 + 20 = 30"}.
 * <p>
 * The {@link FormatProcessor} format specification used and exceptions thrown are the
 * same as those of {@link Formatter}.
 * <p>
 * However, there are two significant differences related to the position of arguments.
 * An explict {@code n$} and relative {@code <} index will cause an exception due to
 * a missing argument list.
 * Whitespace appearing between the specification and the embedded expression will
 * also cause an exception.
 * <p>
 * {@link FormatProcessor} allows the use of different locales. For example:
 * {@snippet :
 * Locale locale = Locale.forLanguageTag("th-TH-u-nu-thai");
 * FormatProcessor thaiFMT = FormatProcessor.create(locale);
 * int x = 10;
 * int y = 20;
 * String result = thaiFMT."%4d\{x} + %4d\{y} = %5d\{x + y}";
 * }
 * In the above example, the value of {@code result} will be
 * {@code "  \u0E51\u0E50 +   \u0E52\u0E50 =    \u0E53\u0E50"}.
 * <p>
 * For day to day use, the predefined {@link FormatProcessor#FMT} {@link FormatProcessor}
 * is available. {@link FormatProcessor#FMT} is defined using the {@link Locale#ROOT}.
 * Example: {@snippet :
 * int x = 10;
 * int y = 20;
 * String result = FMT."0x%04x\{x} + 0x%04x\{y} = 0x%04x\{x + y}"; // @highlight substring="FMT"
 * }
 * In the above example, the value of {@code result} will be {@code "0x000a + 0x0014 = 0x001E"}.
 *
 * @since 21
 *
 * @see Processor
 */
@PreviewFeature(feature=PreviewFeature.Feature.STRING_TEMPLATES)
public final class FormatProcessor implements Processor<String, RuntimeException>, Linkage {
    /**
     * {@link Locale} used to format
     */
    private final Locale locale;

    /**
     * Constructor.
     *
     * @param locale  {@link Locale} used to format
     */
    private FormatProcessor(Locale locale) {
        this.locale = locale;
    }

    /**
     * Create a new {@link FormatProcessor} using the specified locale.
     *
     * @param locale {@link Locale} used to format
     *
     * @return a new instance of {@link FormatProcessor}
     *
     * @throws java.lang.NullPointerException if locale is null
     */
    public static FormatProcessor create(Locale locale) {
        Objects.requireNonNull(locale);
        return new FormatProcessor(locale);
    }

    /**
     * Constructs a {@link String} based on the fragments, format
     * specifications found in the fragments and values in the
     * supplied {@link StringTemplate} object. This method constructs a
     * format string from the fragments, gathers up the values and
     * evaluates the expression asif evaulating
     * {@code new Formatter(locale).format(format, values).toString()}.
     * <p>
     * If an embedded expression is not immediately preceded by a
     * specifier then a {@code %s} is inserted in the format.
     *
     * @param stringTemplate  a {@link StringTemplate} instance
     *
     * @return constructed {@link String}

     * @throws  IllegalFormatException
     *          If a format specifier contains an illegal syntax, a format
     *          specifier that is incompatible with the given arguments,
     *          a specifier not followed immediately by an embedded expression or
     *          other illegal conditions. For specification of all possible
     *          formatting errors, see the
     *          <a href="../util/Formatter.html#detail">details</a>
     *          section of the formatter class specification.
     * @throws NullPointerException if stringTemplate is null
     *
     * @see java.util.Formatter
     */
    @Override
    public final String process(StringTemplate stringTemplate) {
        Objects.requireNonNull(stringTemplate);
        String format = stringTemplateFormat(stringTemplate.fragments());
        Object[] values = stringTemplate.values().toArray();

        return new Formatter(locale).format(format, values).toString();
    }

    /**
     * Constructs a {@link MethodHandle} that when supplied with the values from
     * a {@link StringTemplate} will produce a result equivalent to that provided by
     * {@link FormatProcessor#process(StringTemplate)}. This {@link MethodHandle}
     * is used by {@link FormatProcessor#FMT} and the ilk to perform a more
     * specialized composition of a result. This specialization is done by
     * prescanning the fragments and value types of a {@link StringTemplate}.
     * <p>
     * Process template expressions can be specialized  when the processor is
     * of type {@link Linkage} and fetched from a static constant as is
     * {@link FormatProcessor#FMT} ({@code static final FormatProcessor}).
     * <p>
     * Other {@link FormatProcessor FormatProcessors} can be specialized when stored in a static
     * final.
     * For example:
     * {@snippet :
     * FormatProcessor THAI_FMT = FormatProcessor.create(Locale.forLanguageTag("th-TH-u-nu-thai"));
     * }
     * {@code THAI_FMT} will now produce specialized {@link MethodHandle MethodHandles} by way
     * of {@link FormatProcessor#linkage(List, MethodType)}.
     *
     * See {@link FormatProcessor#process(StringTemplate)} for more information.
     *
     * @throws  IllegalFormatException
     *          If a format specifier contains an illegal syntax, a format
     *          specifier that is incompatible with the given arguments,
     *          a specifier not followed immediately by an embedded expression or
     *          other illegal conditions. For specification of all possible
     *          formatting errors, see the
     *          <a href="../util/Formatter.html#detail">details</a>
     *          section of the formatter class specification.
     * @throws NullPointerException if fragments or type is null
     *
     * @see java.util.Formatter
     */
    @Override
    public MethodHandle linkage(List<String> fragments, MethodType type) {
        Objects.requireNonNull(fragments);
        Objects.requireNonNull(type);
        String format = stringTemplateFormat(fragments);
        Class<?>[] ptypes = type.dropParameterTypes(0, 1).parameterArray();
        MethodHandle mh = new FormatterBuilder(format, locale, ptypes).build();
        mh = MethodHandles.dropArguments(mh, 0, type.parameterType(0));

        return mh;
    }

    /**
     * Find a format specification at the end of a fragment.
     *
     * @param fragment  fragment to check
     * @param needed    if the specification is needed
     *
     * @return true if the specification is found and needed
     *
     * @throws MissingFormatArgumentException if not at end or found and not needed
     */
    private static boolean findFormat(String fragment, boolean needed) {
        int max = fragment.length();
        for (int i = 0; i < max;) {
            int n = fragment.indexOf('%', i);
            if (n < 0) {
                return false;
            }

            i = n + 1;
            if (i >= max) {
                return false;
            }

            char c = fragment.charAt(i);
            if (c == '%' || c == 'n') {
                i++;
                continue;
            }
            int off = new Formatter.FormatSpecifierParser(null, c, i, fragment, max)
                    .parse();
            if (off == 0) {
                return false;
            }
            if (i + off == max && needed) {
                return true;
            }
            throw new MissingFormatArgumentException(
                    fragment.substring(i - 1, i + off)
                    + " is not immediately followed by an embedded expression");
        }
        return false;
    }

    /**
     * Convert {@link StringTemplate} fragments, containing format specifications,
     * to a form that can be passed on to {@link Formatter}. The method scans each fragment,
     * matching up formatter specifications with the following expression. If no
     * specification is found, the method inserts "%s".
     *
     * @param fragments  string template fragments
     *
     * @return  format string
     */
    private static String stringTemplateFormat(List<String> fragments) {
        StringBuilder sb = new StringBuilder();
        int lastIndex = fragments.size() - 1;
        List<String> formats = fragments.subList(0, lastIndex);
        String last = fragments.get(lastIndex);

        for (String format : formats) {
            if (findFormat(format, true)) {
                sb.append(format);
            } else {
                sb.append(format);
                sb.append("%s");
            }
        }

        if (!findFormat(last, false)) {
            sb.append(last);
        }

        return sb.toString();
    }

    /**
     * This predefined {@link FormatProcessor} instance constructs a {@link String} result using
     * the Locale.ROOT {@link Locale}. See {@link FormatProcessor} for more details.
     * Example: {@snippet :
     * int x = 10;
     * int y = 20;
     * String result = FMT."0x%04x\{x} + 0x%04x\{y} = 0x%04x\{x + y}"; // @highlight substring="FMT"
     * }
     * In the above example, the value of {@code result} will be {@code "0x000a + 0x0014 = 0x001E"}.
     *
     * @see java.util.FormatProcessor
     */
    public static final FormatProcessor FMT = FormatProcessor.create(Locale.ROOT);

}
