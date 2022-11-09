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

package java.lang.runtime;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.template.ProcessorLinkage;
import java.lang.template.StringTemplate;
import java.lang.template.ValidatingProcessor;
import java.util.List;
import java.util.Objects;

import jdk.internal.access.JavaTemplateAccess;
import jdk.internal.access.JavaUtilCollectionAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.javac.PreviewFeature;

/**
 * Manages string template bootstrapping and creation. These methods may be used, for example,
 * by Java compiler implementations to implement the bodies of methods for {@link StringTemplate}
 * objects.
 * <p>
 * Bootstraps in the form of {@code (Lookup, String, MethodType)(String[], Object[])} are used
 * to create {@link StringTemplate StringTemplates} that have more than
 * {@link java.lang.invoke.StringConcatFactory#MAX_INDY_CONCAT_ARG_SLOTS} values.
 * <p>
 * Bootstraps in the form of {@code (Lookup, String, MethodType, String...)(...)} are used to create
 * optimized {@link StringTemplate StringTemplates} based on StringTemplateImpl.
 * <p>
 * Bootstraps in the for of (Lookup, String, MethodType, MethodHandle, String...)
 * (VerifyingProcessor, ...) are used to implement specialized processors for
 * {@link ProcessorLinkage} implementing processors.
 *
 * @since 20
 */
@PreviewFeature(feature=PreviewFeature.Feature.STRING_TEMPLATES)
public final class TemplateRuntime {
    private static final JavaTemplateAccess JTA = SharedSecrets.getJavaTemplateAccess();
    private static final JavaUtilCollectionAccess JUCA = SharedSecrets.getJavaUtilCollectionAccess();

    /**
     * {@link MethodHandle} to {@link TemplateRuntime#defaultProcess}.
     */
    private static final MethodHandle DEFAULT_PROCESS_MH;

    /**
     * {@link MethodHandle} to {@link TemplateRuntime#fromArrays}.
     */
    private static final MethodHandle FROM_ARRAYS;

    /**
     * Initialize {@link MethodHandle MethodHandles}.
     */
    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();

            MethodType mt = MethodType.methodType(Object.class,
                    List.class, ValidatingProcessor.class, Object[].class);
            DEFAULT_PROCESS_MH = lookup.findStatic(TemplateRuntime.class, "defaultProcess", mt);

            mt = MethodType.methodType(StringTemplate.class, String[].class, Object[].class);
            FROM_ARRAYS = lookup.findStatic(TemplateRuntime.class, "fromArrays", mt);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("string bootstrap fail", ex);
        }
    }

    /**
     * Private constructor.
     */
    private TemplateRuntime() {
        throw new AssertionError("private constructor");
    }

    /**
     * String template bootstrap method for creating large string templates.
     *
     * @param lookup          method lookup
     * @param name            method name
     * @param type            method type
     *
     * @return {@link CallSite} to handle create large string template
     *
     * @throws NullPointerException if any of the arguments is null
     * @throws Throwable            if linkage fails
     */
    public static CallSite stringTemplateBSM(
            MethodHandles.Lookup lookup,
            String name,
            MethodType type
    ) throws Throwable {
        Objects.requireNonNull(lookup, "lookup is null");
        Objects.requireNonNull(name, "name is null");
        Objects.requireNonNull(type, "type is null");

        return new ConstantCallSite(FROM_ARRAYS.asType(type));
    }

    /**
     * String template bootstrap method for creating string templates.
     *
     * @param lookup          method lookup
     * @param name            method name
     * @param type            method type
     * @param fragments       fragments from string template
     *
     * @return {@link CallSite} to handle create string template
     *
     * @throws NullPointerException if any of the arguments is null
     * @throws Throwable            if linkage fails
     */
    public static CallSite stringTemplateBSM(
            MethodHandles.Lookup lookup,
            String name,
            MethodType type,
            String... fragments
    ) throws Throwable {
        Objects.requireNonNull(lookup, "lookup is null");
        Objects.requireNonNull(name, "name is null");
        Objects.requireNonNull(type, "type is null");
        Objects.requireNonNull(fragments, "fragments is null");

        MethodHandle mh = JTA
                .createStringTemplateImplMH(List.of(fragments), type).asType(type);

        return new ConstantCallSite(mh);
    }

    /**
     * String template bootstrap method for static final processors.
     *
     * @param lookup          method lookup
     * @param name            method name
     * @param type            method type
     * @param processorGetter {@link MethodHandle} to get static final processor
     * @param fragments       fragments from string template
     *
     * @return {@link CallSite} to handle string template processing
     *
     * @throws NullPointerException if any of the arguments is null
     * @throws Throwable            if linkage fails
     */
    public static CallSite stringTemplateBSM(
            MethodHandles.Lookup lookup,
            String name,
            MethodType type,
            MethodHandle processorGetter,
            String... fragments
    ) throws Throwable {
        Objects.requireNonNull(lookup, "lookup is null");
        Objects.requireNonNull(name, "name is null");
        Objects.requireNonNull(type, "type is null");
        Objects.requireNonNull(processorGetter, "processorGetter is null");
        Objects.requireNonNull(fragments, "fragments is null");

        ValidatingProcessor<?, ?> processor = (ValidatingProcessor<?, ?>)processorGetter.invoke();
        MethodHandle mh = processor instanceof ProcessorLinkage processorLinkage
                ? processorLinkage.linkage(List.of(fragments), type)
                : defaultProcessMethodHandle(type, processor, List.of(fragments));

        return new ConstantCallSite(mh);
    }

    /**
     * Creates a simple {@link StringTemplate} and then invokes the processor's process method.
     *
     * @param fragments fragments from string template
     * @param processor {@link ValidatingProcessor} to process
     * @param values    array of expression values
     *
     * @return result of processing the string template
     */
    private static Object defaultProcess(
            List<String> fragments,
            ValidatingProcessor<?, ?> processor,
            Object[] values
    ) throws Throwable {
        return processor.process(
            StringTemplate.of(fragments, JUCA.listFromTrustedArrayNullsAllowed(values)));
    }

    /**
     * Generate a {@link MethodHandle} which is effectively invokes
     * {@code processor.process(new StringTemplate(fragments, values...)}.
     *
     * @return default process {@link MethodHandle}
     */
    private static MethodHandle defaultProcessMethodHandle(
            MethodType type,
            ValidatingProcessor<?, ?> processor,
            List<String> fragments
    ) {
        MethodHandle mh = MethodHandles.insertArguments(DEFAULT_PROCESS_MH, 0, fragments, processor);
        return mh.asCollector(Object[].class, type.parameterCount()).asType(type);
    }

    /**
     * A {@link StringTemplate} where number of value slots exceeds
     * {@link java.lang.invoke.StringConcatFactory#MAX_INDY_CONCAT_ARG_SLOTS}.
     *
     * @param fragments  immutable list of string fragments from string template
     * @param values     immutable list of expression values
     */
    private record LargeStringTemplate(List<String> fragments, List<Object> values)
            implements StringTemplate {
        @Override
        public java.lang.String toString() {
            return StringTemplate.toString(this);
        }
    }

    /**
     * Used to create a {@link StringTemplate} when number of value slots exceeds
     * {@link java.lang.invoke.StringConcatFactory#MAX_INDY_CONCAT_ARG_SLOTS}.
     *
     * @param fragments  array of string fragments
     * @param values     array of values
     *
     * @return new {@link StringTemplate}
     */
    private static StringTemplate fromArrays(String[] fragments, Object[] values) {
        return new LargeStringTemplate(List.of(fragments),
                                       JUCA.listFromTrustedArrayNullsAllowed(values));
    }
}

