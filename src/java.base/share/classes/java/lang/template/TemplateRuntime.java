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

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.JavaLangInvokeAccess;
import jdk.internal.access.JavaUtilCollectionAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.javac.PreviewFeature;

/**
 * This class provides runtime support for string templates. The methods within
 * are intended for internal use only.
 *
 * @since 20
 */
@PreviewFeature(feature=PreviewFeature.Feature.STRING_TEMPLATES)
final class TemplateRuntime {

    /**
     * Private constructor.
     */
    private TemplateRuntime() {
        throw new AssertionError("private constructor");
    }

    private static final JavaLangAccess JLA = SharedSecrets.getJavaLangAccess();

    private static final JavaUtilCollectionAccess JUCA = SharedSecrets.getJavaUtilCollectionAccess();

    /**
     * Collect nullable elements from an array into a unmodifiable list.
     *
     * @param elements  elements to place in list
     *
     * @return unmodifiable list.
     *
     * @param <E>  type of elements
     */
    @SafeVarargs
    @SuppressWarnings({"unchecked", "varargs"})
    static <E> List<E> toList(E... elements) {
        return JUCA.listFromTrustedArrayNullsAllowed(elements);
    }

    /**
     * Generic StringTemplate.
     *
     * @param fragments  immutable list of string fragments from string template
     * @param values     immutable list of expression values
     */
    record SimpleStringTemplate(List<String> fragments,
                                 List<Object> values
    ) implements StringTemplate {}

    /**
     * Creates a string that interleaves the elements of values between the
     * elements of fragments.
     *
     * @param fragments  list of String fragments
     * @param values     list of expression values
     *
     * @return String interpolation of fragments and values
     */
    static String interpolate(List<String> fragments, List<Object> values) {
        int fragmentsSize = fragments.size();
        int valuesSize = values.size();
        if (fragmentsSize == 1) {
            return fragments.get(0);
        }
        int size = fragmentsSize + valuesSize;
        String[] strings = new String[size];
        Iterator<String> fragmentsIter = fragments.iterator();
        int i = 0;
        for (Object value : values) {
            strings[i++] = fragmentsIter.next();
            strings[i++] = String.valueOf(value);
        }
        strings[i++] = fragmentsIter.next();
        return JLA.join("", "", "", strings, size);
    }

    /**
     * Combine one or more {@link StringTemplate StringTemplates} to produce a combined {@link StringTemplate}.
     * {@snippet :
     * StringTemplate st = StringTemplate.combine("\{a}", "\{b}", "\{c}");
     * assert st.interpolate().equals("\{a}\{b}\{c}");
     * }
     *
     * @param sts  one or more {@link StringTemplate}
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
        String[] fragments = new String[size + 1];
        Object[] values = new Object[size];
        int i = 0, j = 0;
        fragments[0] = "";
        for (StringTemplate st : sts) {
            Iterator<String> fragmentIter = st.fragments().iterator();
            fragments[i++] += fragmentIter.next();
            while (fragmentIter.hasNext()) {
                fragments[i++] = fragmentIter.next();
            }
            i--;
            for (Object value : st.values()) {
                values[j++] = value;
            }
        }
        return new SimpleStringTemplate(TemplateRuntime.toList(fragments), TemplateRuntime.toList(values));
    }

}
