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

import jdk.internal.access.JavaUtilCollectionAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.javac.PreviewFeature;

/**
 * Manages the template creation and bootstrapping.
 */
@PreviewFeature(feature=PreviewFeature.Feature.STRING_TEMPLATES)
public final class TemplateSupport {
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
    private final ValidatingProcessor<?, ?> processor;

    private static final JavaUtilCollectionAccess JUCA = SharedSecrets.getJavaUtilCollectionAccess();

    /**
     * Initialize {@link MethodHandle MethodHandles}.
     */
    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();

            MethodType mt = MethodType.methodType(Object.class,
                    List.class, ValidatingProcessor.class, Object[].class);
            DEFAULT_PROCESS_MH = lookup.findStatic(TemplateSupport.class, "defaultProcess", mt);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("string bootstrap fail", ex);
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
    TemplateSupport(MethodHandles.Lookup lookup, String name, MethodType type,
                    List<String> fragments,
                    ValidatingProcessor<?, ?> processor) {
        this.lookup = lookup;
        this.name = name;
        this.type = type;
        this.fragments = fragments;
        this.processor = processor;

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
        ValidatingProcessor<?, ?> processor =
                (ValidatingProcessor<?, ?>)processorGetter.asType(processorGetterType).invokeExact();
        TemplateSupport support = new TemplateSupport(lookup, name, type, List.of(fragments), processor);

        return support.processWithProcessor();
    }

    /**
     * Create callsite to invoke specialized processor process method.
     *
     * @return {@link CallSite} for processing templated strings.
     * @throws Throwable if linkage fails
     */
    CallSite processWithProcessor() throws Throwable {
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
        return processor.process(
            StringTemplate.of(fragments, toList(values)));
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
    @SuppressWarnings("unchecked")
    public static <E> List<E> toList(E... elements) {
        return JUCA.listFromTrustedArrayNullsAllowed(elements);
    }

}

