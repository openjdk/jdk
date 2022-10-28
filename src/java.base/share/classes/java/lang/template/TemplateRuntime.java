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

import java.lang.invoke.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.JavaLangInvokeAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.javac.PreviewFeature;

/**
 * This class provides runtime support for string templates. The methods within
 * are intended for internal use only.
 *
 * @since 20
 */
@PreviewFeature(feature=PreviewFeature.Feature.STRING_TEMPLATES)
public final class TemplateRuntime {
    /**
     * Private constructor.
     */
    private TemplateRuntime() {
        throw new AssertionError("private constructor");
    }

    /**
     * Templated string bootstrap method.
     *
     * @param lookup          method lookup
     * @param name            method name
     * @param type            method type
     * @param processorGetter {@link MethodHandle} to get static final processor
     * @param fragments       fragments from string template
     * @return {@link CallSite} to handle templated string processing
     * @throws NullPointerException if any of the arguments is null
     * @throws Throwable            if linkage fails
     */
    public static CallSite stringTemplateBSM(
            MethodHandles.Lookup lookup,
            String name,
            MethodType type,
            MethodHandle processorGetter,
            String... fragments) throws Throwable {
        Objects.requireNonNull(lookup, "lookup is null");
        Objects.requireNonNull(name, "name is null");
        Objects.requireNonNull(type, "type is null");
        Objects.requireNonNull(processorGetter, "processorGetter is null");
        Objects.requireNonNull(fragments, "fragments is null");

        MethodType processorGetterType = MethodType.methodType(ValidatingProcessor.class);
        ValidatingProcessor<?, ? extends Throwable> processor =
                (ValidatingProcessor<?, ? extends Throwable>)processorGetter.asType(processorGetterType).invokeExact();
        TemplateBootstrap bootstrap = new TemplateBootstrap(lookup, name, type, List.of(fragments), processor);

        return bootstrap.processWithProcessor();
    }

    /**
     * Manages the boostrapping of {@link ProcessorLinkage} callsites.
     */
    private static class TemplateBootstrap {
        /**
         * {@link MethodHandle} to {@link TemplateBootstrap#defaultProcess}.
         */
        private static final MethodHandle DEFAULT_PROCESS_MH;

        /**
         * {@link MethodHandles.Lookup} passed to the bootstrap method.
         */
        private final MethodHandles.Lookup lookup;

        /**
         * Name passed to the bootstrap method ("process").
         */
        private final String name;

        /**
         * {@link MethodType} passed to the bootstrap method.
         */
        private final MethodType type;

        /**
         * Fragments from string template.
         */
        private final List<String> fragments;

        /**
         * Static final processor.
         */
        private final ValidatingProcessor<?, ? extends Throwable> processor;

        /**
         * Initialize {@link MethodHandle MethodHandles}.
         */
        static {
            try {
                MethodHandles.Lookup lookup = MethodHandles.lookup();

                MethodType mt = MethodType.methodType(Object.class,
                        List.class, ValidatingProcessor.class, Object[].class);
                DEFAULT_PROCESS_MH = lookup.findStatic(TemplateBootstrap.class, "defaultProcess", mt);
            } catch (ReflectiveOperationException ex) {
                throw new AssertionError("templated string bootstrap fail", ex);
            }
        }

        /**
         * Constructor.
         *
         * @param lookup    method lookup
         * @param name      method name
         * @param type      method type
         * @param fragments fragments from string template
         * @param processor static final processor
         */
        private TemplateBootstrap(MethodHandles.Lookup lookup, String name, MethodType type,
                                  List<String> fragments,
                                  ValidatingProcessor<?, ? extends Throwable> processor) {
            this.lookup = lookup;
            this.name = name;
            this.type = type;
            this.fragments = fragments;
            this.processor = processor;

        }

        /**
         * Create callsite to invoke specialized processor process method.
         *
         * @return {@link CallSite} for processing templated strings.
         * @throws Throwable if linkage fails
         */
        private CallSite processWithProcessor() throws Throwable {
            MethodHandle mh = processor instanceof ProcessorLinkage processorLinkage ?
                    processorLinkage.linkage(fragments, type) : defaultProcessMethodHandle();

            return new ConstantCallSite(mh);
        }

        /**
         * Creates a simple {@link StringTemplate} and then invokes the processor's process method.
         *
         * @param fragments fragments from string template
         * @param processor {@link ValidatingProcessor} to process
         * @param values    array of expression values
         * @return
         */
        private static Object defaultProcess(List<String> fragments,
                                             ValidatingProcessor<Object, Throwable> processor,
                                             Object[] values) throws Throwable {
            return processor.process(new SimpleStringTemplate(fragments, List.of(values)));
        }

        /**
         * Generate a {@link MethodHandle} which is effectively invokes
         * {@code processor.process(new StringTemplate(fragments, values...)}.
         *
         * @return default process {@link MethodHandle}
         */
        private MethodHandle defaultProcessMethodHandle() {
            MethodHandle mh = MethodHandles.insertArguments(DEFAULT_PROCESS_MH, 0, fragments, processor);
            mh = mh.withVarargs(true);
            mh = mh.asType(type);

            return mh;
        }

    }

    /**
     * Collect nullable elements from an array into a unmodifiable list.
     *
     * @param elements  elements to place in list
     *
     * @return unmodifiable list.
     *
     * @param <E>  type of elements
     *
     * @implNote Intended for use by {@link StringTemplate} implementations.
     * Other usage may lead to undesired effects.
     */
    @SuppressWarnings("unchecked")
    public static <E> List<E> toList(E... elements) {
        return Collections.unmodifiableList(Arrays.asList(elements));
    }

    private static final JavaLangAccess JLA = SharedSecrets.getJavaLangAccess();

    private static final JavaLangInvokeAccess JLIA = SharedSecrets.getJavaLangInvokeAccess();

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
    static List<MethodHandle> valueGetters(StringTemplate st) {
        Objects.requireNonNull(st, "st must not be null");
        List<MethodHandle> result = new ArrayList<>();
        Class<?> tsClass = st.getClass();
        if (tsClass.isSynthetic()) {
            try {
                for (int i = 0; ; i++) {
                    Field field = tsClass.getDeclaredField("x" + i);
                    result.add(JLIA.unreflectField(field, false));
                }
            } catch (NoSuchFieldException ex) {
                // End of fields
            } catch (ReflectiveOperationException | SecurityException ex) {
                throw new InternalError(ex);
            }

            return result;
        }
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MethodHandle getter = lookup.findStatic(TemplateRuntime.class, "getValue",
                MethodType.methodType(Object.class, int.class, StringTemplate.class));
            int size = st.values().size();
            for (int index = 0; index < size; index++) {
                result.add(MethodHandles.insertArguments(getter, 0, index));
            }
            return result;
        } catch (ReflectiveOperationException | SecurityException ex) {
            throw new InternalError(ex);
        }
    }

    /**
     * Private ethod used by {@link TemplateRuntime#valueGetters(StringTemplate)}
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
     * Creates a string that interleaves the elements of values between the
     * elements of fragments.
     *
     * @param fragments  list of String fragments
     * @param values     list of expression values
     *
     * @return String interpolation of fragments and values
     *
     * @throws NullPointerException fragments or values is null or if any of the fragments is null
     */
    static String interpolate(List<String> fragments, List<Object> values) {
        Objects.requireNonNull(fragments, "fragments must not be null");
        Objects.requireNonNull(values, "values must not be null");
        int fragmentsSize = fragments.size();
        int valuesSize = values.size();
        if (fragmentsSize != valuesSize + 1) {
            throw new RuntimeException("fragments must have one more element than values");
        }
        if (fragmentsSize == 1) {
            String fragment = Objects.requireNonNull(fragments.get(0), "fragments must not have null elements");
            return fragments.get(0);
        }
        int size = fragmentsSize + valuesSize;
        String[] strings = new String[size];
        Iterator<String> fragmentsIter = fragments.iterator();
        int i = 0;
        for (Object value : values) {
            strings[i++] = Objects.requireNonNull(fragmentsIter.next(), "fragments must not have null elements");
            strings[i++] = String.valueOf(value);
        }
        strings[i++] = Objects.requireNonNull(fragmentsIter.next(), "fragments must not have null elements");
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
        return new SimpleStringTemplate(java.lang.template.TemplateRuntime.toList(fragments), java.lang.template.TemplateRuntime.toList(values));
    }

}
