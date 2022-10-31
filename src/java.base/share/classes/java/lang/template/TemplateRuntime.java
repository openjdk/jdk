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
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
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
     * {@link MethodHandle} to {@link TemplateRuntime#getValue(int, StringTemplate)}.
     */
    private static final MethodHandle GET_VALUE_MH;

    /**
     * Initialize {@link MethodHandle MethodHandles}.
     */
    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();

            MethodType mt = MethodType.methodType(Object.class, int.class, StringTemplate.class);
            GET_VALUE_MH = lookup.findStatic(TemplateRuntime.class, "getValue", mt);
        } catch (ReflectiveOperationException | SecurityException ex) {
            throw new AssertionError("template runtime init fail", ex);
        }
    }

    /**
     * Private constructor.
     */
    private TemplateRuntime() {
        throw new AssertionError("private constructor");
    }

    private static final JavaLangAccess JLA = SharedSecrets.getJavaLangAccess();

    private static final JavaLangInvokeAccess JLIA = SharedSecrets.getJavaLangInvokeAccess();

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
    @SuppressWarnings("unchecked")
    public static <E> List<E> toList(E... elements) {
        return JUCA.listFromTrustedArrayNullsAllowed(elements);
    }

    /**
     * Return the types of a {@link StringTemplate StringTemplate's} values.
     *
     * @param st  StringTemplate to examine
     *
     * @return list of value types
     *
     * @throws NullPointerException if st is null
     *
     * @implNote The default method determines if the {@link StringTemplate}
     * was synthesized by the compiler, then the types are precisely those of the
     * embedded expressions, otherwise this method returns the values list types.
     */
    static List<Class<?>> valueTypes(StringTemplate st) {
        Objects.requireNonNull(st, "st must not be null");
        List<Class<?>> result = new ArrayList<>();
        Class<?> tsClass = st.getClass();
        if (tsClass.isSynthetic()) {
            try {
                for (int i = 0; ; i++) {
                    Field field = tsClass.getDeclaredField("x" + i);
                    result.add(field.getType());
                }
            } catch (NoSuchFieldException ex) {
                // End of fields
            } catch (SecurityException ex) {
                throw new InternalError(ex);
            }

            return result;
        }
        for (Object value : st.values()) {
            result.add(value == null ? Object.class : value.getClass());
        }
        return result;
    }

    /**
     * Return {@link MethodHandle MethodHandles} to access a
     * {@link StringTemplate StringTemplate's} values.
     *
     * @param st  StringTemplate to examine
     *
     * @return list of {@link MethodHandle MethodHandles}
     *
     * @throws NullPointerException if st is null
     *
     * @implNote The default method determines if the {@link StringTemplate}
     * was synthesized by the compiler, then the MethodHandles are precisely those of the
     * embedded expressions fields, otherwise this method returns getters for the values list.
     */
    static List<MethodHandle> valueAccessors(StringTemplate st) {
        Objects.requireNonNull(st, "st must not be null");
        List<MethodHandle> result = new ArrayList<>();
        Class<?> tsClass = st.getClass();
        if (tsClass.isSynthetic()) {
            try {
                for (int i = 0; ; i++) {
                    Field field = tsClass.getDeclaredField("x" + i);
                    MethodHandle mh = JLIA.unreflectField(field, false);
                    mh = mh.asType(mh.type().changeParameterType(0, StringTemplate.class));
                    result.add(mh);
                }
            } catch (NoSuchFieldException ex) {
                // End of fields
            } catch (ReflectiveOperationException | SecurityException ex) {
                throw new InternalError(ex);
            }

            return result;
        }
        try {
            int size = st.values().size();
            for (int index = 0; index < size; index++) {
                result.add(MethodHandles.insertArguments(GET_VALUE_MH, 0, index));
            }
            return result;
        } catch (SecurityException ex) {
            throw new InternalError(ex);
        }
    }

    /**
     * Private method used by {@link TemplateRuntime#valueAccessors(StringTemplate)}
     * to access values.
     *
     * @param index values index
     * @param st    the {@link StringTemplate}
     *
     * @return value at index
     */
    private static Object getValue(int index, StringTemplate st) {
        return st.values().get(index);
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
