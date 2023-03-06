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

package java.lang.template;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.JavaTemplateAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.javac.PreviewFeature;

/**
 * This class provides runtime support for string templates. The methods within
 * are intended for internal use only.
 *
 * @since 21
 */
@PreviewFeature(feature=PreviewFeature.Feature.STRING_TEMPLATES)
final class TemplateSupport {

    /**
     * Private constructor.
     */
    private TemplateSupport() {
        throw new AssertionError("private constructor");
    }

    static {
        SharedSecrets.setJavaTemplateAccess(new StringTemplateImplFactory());
    }

    private static final JavaLangAccess JLA = SharedSecrets.getJavaLangAccess();
    private static final JavaTemplateAccess JTA = SharedSecrets.getJavaTemplateAccess();

    /**
     * Returns a StringTemplate composed from fragments and values.
     *
     * @implSpec The {@code fragments} list size must be one more that the
     * {@code values} list size.
     *
     * @param fragments list of string fragments
     * @param values    list of expression values
     *
     * @return StringTemplate composed from fragments and values
     *
     * @throws IllegalArgumentException if fragments list size is not one more
     *         than values list size
     * @throws NullPointerException if fragments is null or values is null or if any fragment is null.
     *
     * @implNote Contents of both lists are copied to construct immutable lists.
     */
    static StringTemplate of(List<String> fragments, List<?> values) {
        return JTA.newStringTemplate(fragments, values);
    }

    /**
     * Creates a string that interleaves the elements of values between the
     * elements of fragments.
     *
     * @param fragments  list of String fragments
     * @param values     list of expression values
     *
     * @return String interpolation of fragments and values
     */
    static String interpolate(List<String> fragments, List<?> values) {
        int fragmentsSize = fragments.size();
        int valuesSize = values.size();
        if (fragmentsSize == 1) {
            return fragments.get(0);
        }
        int size = fragmentsSize + valuesSize;
        String[] strings = new String[size];
        int i = 0, j = 0;
        for (; j < valuesSize; j++) {
            strings[i++] = fragments.get(j);
            strings[i++] = String.valueOf(values.get(j));
        }
        strings[i] = fragments.get(j);
        return JLA.join("", "", "", strings, size);
    }

    /**
     * Combine one or more {@link StringTemplate StringTemplates} to produce a combined {@link StringTemplate}.
     * {@snippet :
     * StringTemplate st = StringTemplate.combine("\{a}", "\{b}", "\{c}");
     * assert st.interpolate().equals("\{a}\{b}\{c}");
     * }
     *
     * @param sts  zero or more {@link StringTemplate}
     *
     * @return combined {@link StringTemplate}
     *
     * @throws NullPointerException if sts is null or if any element of sts is null
     */
    static StringTemplate combine(StringTemplate... sts) {
        Objects.requireNonNull(sts, "sts must not be null");
        if (sts.length == 0) {
            return StringTemplate.of("");
        } else if (sts.length == 1) {
            return Objects.requireNonNull(sts[0], "string templates should not be null");
        }
        int size = 0;
        for (StringTemplate st : sts) {
            Objects.requireNonNull(st, "string templates should not be null");
            size += st.values().size();
        }
        String[] combinedFragments = new String[size + 1];
        Object[] combinedValues = new Object[size];
        combinedFragments[0] = "";
        int fragmentIndex = 1;
        int valueIndex = 0;
        for (StringTemplate st : sts) {
            Iterator<String> iterator = st.fragments().iterator();
            combinedFragments[fragmentIndex - 1] += iterator.next();
            while (iterator.hasNext()) {
                combinedFragments[fragmentIndex++] = iterator.next();
            }
            for (Object value : st.values()) {
                combinedValues[valueIndex++] = value;
            }
        }
        return JTA.newStringTemplate(combinedFragments, combinedValues);
    }

    /**
     * Return the basic string interpolate process, and, initialize the SharedSecret.
     *
     * @return basic string interpolate process
     */
    static StringProcessor basicInterpolate() {
        SharedSecrets.setJavaTemplateAccess(new StringTemplateImplFactory());
        return StringTemplate::interpolate;
    }

}
