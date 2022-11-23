/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.template.ProcessorLinkage;
import java.lang.template.StringProcessor;
import java.lang.template.StringTemplate;
import java.lang.template.ValidatingProcessor;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.internal.javac.PreviewFeature;

/**
 * This {@linkplain StringProcessor template processor} constructs a {@link String}
 * result using {@link Formatter}. Unlike {@link Formatter}, {@link FormatProcessor} uses
 * the value from the embedded expression that follows immediately after the
 * <a href="../util/Formatter.html#syntax">format specifier</a>.
 * StringTemplate expressions without a preceding specifier, use "%s" by
 * default. Example:
 * {@snippet :
 * int x = 10;
 * int y = 20;
 * String result = FMT."%05d\{x} + %05d\{y} = %05d\{x + y}";
 * }
 * result is: <code>00010 + 00020 = 00030</code>
 *
 * @implNote When used in conjunction with a runtime instances of {@link
 * StringTemplate} representing string templates this {@link StringProcessor}
 * will use the format specifiers in the fragments and types of the values in
 * the value list to produce a more performant formatter.
 *
 * @implSpec Since, values are found within the string template, argument indexing
 * specifiers are unsupported.
 *
 * @since 20
 */
@PreviewFeature(feature=PreviewFeature.Feature.STRING_TEMPLATES)
public final class FormatProcessor implements StringProcessor, ProcessorLinkage {
    /**
     * {@link Locale} used to format
     */
    private final Locale locale;

    /**
     * Constructor.
     *
     * @param locale  {@link Locale} used to format
     */
    public FormatProcessor(Locale locale) {
        this.locale = locale;
    }

    /**
     * {@inheritDoc}
     * @throws  IllegalFormatException
     *          If a format string contains an illegal syntax, a format
     *          specifier that is incompatible with the given arguments,
     *          insufficient arguments given the format string, or other
     *          illegal conditions.
     * @see java.util.Formatter
     */
    @Override
    public final String process(StringTemplate stringTemplate) {
        Objects.requireNonNull(stringTemplate);
        String format = stringTemplateFormat(stringTemplate.fragments());
        Object[] values = stringTemplate.values().toArray(new Object[0]);

        return new Formatter(locale).format(format, values).toString();
    }

    /**
     * {@inheritDoc}
     * @throws  IllegalFormatException
     *          If a format string contains an illegal syntax, a format
     *          specifier that is incompatible with the given arguments,
     *          insufficient arguments given the format string, or other
     *          illegal conditions.
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

    // %[argument_index$][flags][width][.precision][t]conversion
    private static final String FORMAT_SPECIFIER
            = "%(\\d+\\$)?([-#+ 0,(\\<]*)?(\\d+)?(\\.\\d+)?([tT])?([a-zA-Z%])";

    private static final Pattern FORMAT_SPECIFIER_PATTERN = Pattern.compile(FORMAT_SPECIFIER);

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
        Matcher matcher = FORMAT_SPECIFIER_PATTERN.matcher(fragment);
        String group;

        while (matcher.find()) {
            group = matcher.group();

            if (!group.equals("%%") && !group.equals("%n")) {
                if (matcher.end() == fragment.length() && needed) {
                    return true;
                }

                throw new MissingFormatArgumentException(group);
            }
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
     * This predefined FormatProcessor instance constructs a String result using {@link
     * Formatter}. Unlike {@link Formatter}, FormatProcessor uses the value from
     * the embedded expression that follows immediately after the
     * <a href="../../util/Formatter.html#syntax">format specifier</a>.
     * StringTemplate expressions without a preceeding specifier, use "%s" by
     * Example: {@snippet :
     * int x = 123;
     * int y = 987;
     * String result = FMT."%3d\{x} + %3d\{y} = %4d\{x + y}"; // @highlight substring="FMT"
     * }
     * {@link FMT} uses the Locale.ROOT {@link Locale}.
     */
    public static final FormatProcessor FMT = new FormatProcessor(Locale.ROOT);

}
