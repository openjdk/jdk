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

package java.lang.runtime;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.StringTemplate.Processor;
import java.lang.StringTemplate.Processor.Linkage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import jdk.internal.access.JavaTemplateAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.javac.PreviewFeature;

/**
 * Manages string template bootstrap methods. These methods may be used, for example,
 * by Java compiler implementations to create {@link StringTemplate} instances. For example,
 * the java compiler will translate the following code;
 * {@snippet :
 * int x = 10;
 * int y = 20;
 * StringTemplate st = RAW."\{x} + \{y} = \{x + y}";
 * }
 * to byte code that invokes the {@link java.lang.runtime.TemplateRuntime#newStringTemplate}
 * bootstrap method to construct a {@link CallSite} that accepts two integers and produces a new
 * {@link StringTemplate} instance.
 * {@snippet :
 * MethodHandles.Lookup lookup = MethodHandles.lookup();
 * MethodType mt = MethodType.methodType(StringTemplate.class, int.class, int.class);
 * CallSite cs = TemplateRuntime.newStringTemplate(lookup, "", mt, "", " + ", " = ", "");
 * ...
 * int x = 10;
 * int y = 20;
 * StringTemplate st = (StringTemplate)cs.getTarget().invokeExact(x, y);
 * }
 * If the string template requires more than
 * {@link java.lang.invoke.StringConcatFactory#MAX_INDY_CONCAT_ARG_SLOTS} value slots,
 * then the java compiler will use the
 * {@link java.lang.runtime.TemplateRuntime#newLargeStringTemplate} bootstrap method
 * instead. For example, the java compiler will translate the following code;
 * {@snippet :
 * int[] a = new int[1000], b = new int[1000];
 * ...
 * StringTemplate st = """
 *      \{a[0]} - \{b[0]}
 *      \{a[1]} - \{b[1]}
 *      ...
 *      \{a[999]} - \{b[999]}
 *      """;
 * }
 * to byte code that invokes the {@link java.lang.runtime.TemplateRuntime#newLargeStringTemplate}
 * bootstrap method to construct a {@link CallSite} that accepts an array of integers and produces a new
 * {@link StringTemplate} instance.
 * {@snippet :
 * MethodType mt = MethodType.methodType(StringTemplate.class, String[].class, Object[].class);
 * CallSite cs = TemplateRuntime.newStringTemplate(lookup, "", mt);
 * ...
 * int[] a = new int[1000], b = new int[1000];
 * ...
 * StringTemplate st = (StringTemplate)cs.getTarget().invokeExact(
 *         new String[] { "", " - ", "\n", " - ", "\n", ... " - ", "\n" },
 *         new Object[] { a[0], b[0], a[1], b[1], ..., a[999], b[999]}
 *         );
 * }
 *
 * @since 21
 */
@PreviewFeature(feature=PreviewFeature.Feature.STRING_TEMPLATES)
public final class TemplateRuntime {
    private static final JavaTemplateAccess JTA = SharedSecrets.getJavaTemplateAccess();

    /**
     * {@link MethodHandle} to {@link TemplateRuntime#defaultProcess}.
     */
    private static final MethodHandle DEFAULT_PROCESS_MH;

    /**
     * {@link MethodHandle} to {@link TemplateRuntime#newTrustedStringTemplate}.
     */
    private static final MethodHandle NEW_TRUSTED_STRING_TEMPLATE;

    /**
     * Initialize {@link MethodHandle MethodHandles}.
     */
    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();

            MethodType mt = MethodType.methodType(Object.class,
                    List.class, Processor.class, Object[].class);
            DEFAULT_PROCESS_MH =
                lookup.findStatic(TemplateRuntime.class, "defaultProcess", mt);

            mt = MethodType.methodType(StringTemplate.class, String[].class, Object[].class);
            NEW_TRUSTED_STRING_TEMPLATE =
                lookup.findStatic(StringTemplateImplFactory.class, "newTrustedStringTemplate", mt);
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
     * String template bootstrap method for creating string templates.
     * The static arguments include the fragments list.
     * The non-static arguments are the values.
     *
     * @param lookup          method lookup from call site
     * @param name            method name - not used
     * @param type            method type
     *                        (ptypes...) -> StringTemplate
     * @param fragments       fragment array for string template
     *
     * @return {@link CallSite} to handle create string template
     *
     * @throws NullPointerException if any of the arguments is null
     * @throws Throwable            if linkage fails
     */
    public static CallSite newStringTemplate(MethodHandles.Lookup lookup,
                                             String name,
                                             MethodType type,
                                             String... fragments) throws Throwable {
        Objects.requireNonNull(lookup, "lookup is null");
        Objects.requireNonNull(name, "name is null");
        Objects.requireNonNull(type, "type is null");
        Objects.requireNonNull(fragments, "fragments is null");

        MethodHandle mh = StringTemplateImplFactory
                .createStringTemplateImplMH(List.of(fragments), type).asType(type);

        return new ConstantCallSite(mh);
    }

    /**
     * String template bootstrap method for creating large string templates,
     * i.e., when the number of value slots exceeds
     * {@link java.lang.invoke.StringConcatFactory#MAX_INDY_CONCAT_ARG_SLOTS}.
     * The non-static arguments are the fragments array and values array.
     *
     * @param lookup          method lookup from call site
     * @param name            method name - not used
     * @param type            method type
     *                        (String[], Object[]) -> StringTemplate
     *
     * @return {@link CallSite} to handle create large string template
     *
     * @throws NullPointerException if any of the arguments is null
     * @throws Throwable            if linkage fails
     */
    public static CallSite newLargeStringTemplate(MethodHandles.Lookup lookup,
                                                  String name,
                                                  MethodType type) throws Throwable {
        Objects.requireNonNull(lookup, "lookup is null");
        Objects.requireNonNull(name, "name is null");
        Objects.requireNonNull(type, "type is null");

        return new ConstantCallSite(NEW_TRUSTED_STRING_TEMPLATE.asType(type));
    }

    /**
     * String template bootstrap method for static final processors.
     * The static arguments include the fragments array  and a {@link MethodHandle}
     * to retrieve the value of the static final processor.
     * The non-static arguments are the values.
     *
     * @param lookup          method lookup from call site
     * @param name            method name - not used
     * @param type            method type
     *                        (ptypes...) -> Object
     * @param processorGetter {@link MethodHandle} to get static final processor
     * @param fragments       fragments from string template
     *
     * @return {@link CallSite} to handle string template processing
     *
     * @throws NullPointerException if any of the arguments is null
     * @throws Throwable            if linkage fails
     *
     * @implNote this method is likely to be revamped before exiting preview.
     */
    public static CallSite processStringTemplate(MethodHandles.Lookup lookup,
                                                 String name,
                                                 MethodType type,
                                                 MethodHandle processorGetter,
                                                 String... fragments) throws Throwable {
        Objects.requireNonNull(lookup, "lookup is null");
        Objects.requireNonNull(name, "name is null");
        Objects.requireNonNull(type, "type is null");
        Objects.requireNonNull(processorGetter, "processorGetter is null");
        Objects.requireNonNull(fragments, "fragments is null");

        Processor<?, ?> processor = (Processor<?, ?>)processorGetter.invoke();
        MethodHandle mh = processor instanceof Linkage linkage
                ? linkage.linkage(List.of(fragments), type)
                : defaultProcessMethodHandle(type, processor, List.of(fragments));

        return new ConstantCallSite(mh);
    }

    /**
     * Creates a simple {@link StringTemplate} and then invokes the processor's process method.
     *
     * @param fragments fragments from string template
     * @param processor {@link Processor} to process
     * @param values    array of expression values
     *
     * @return result of processing the string template
     *
     * @throws Throwable when {@link Processor#process(StringTemplate)} throws
     */
    private static Object defaultProcess(
            List<String> fragments,
            Processor<?, ?> processor,
            Object[] values
    ) throws Throwable {
        return processor.process(StringTemplate.of(fragments, Arrays.stream(values).toList()));
    }

    /**
     * Generate a {@link MethodHandle} which is effectively invokes
     * {@code processor.process(new StringTemplate(fragments, values...)}.
     *
     * @return default process {@link MethodHandle}
     */
    private static MethodHandle defaultProcessMethodHandle(
            MethodType type,
            Processor<?, ?> processor,
            List<String> fragments
    ) {
        MethodHandle mh = MethodHandles.insertArguments(DEFAULT_PROCESS_MH, 0, fragments, processor);
        return mh.asCollector(Object[].class, type.parameterCount()).asType(type);
    }
}

