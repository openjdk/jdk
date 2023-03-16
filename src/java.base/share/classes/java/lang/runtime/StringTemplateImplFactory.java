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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.StringConcatException;
import java.lang.invoke.StringConcatFactory;
import java.util.List;

import jdk.internal.access.JavaUtilCollectionAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.javac.PreviewFeature;

/**
 * This class synthesizes {@link StringTemplate StringTemplates} based on
 * fragments and bootstrap method type. Usage is primarily from
 * {@link java.lang.runtime.TemplateRuntime}.
 *
 * @since 21
 */
@PreviewFeature(feature=PreviewFeature.Feature.STRING_TEMPLATES)
final class StringTemplateImplFactory {

    private static final JavaUtilCollectionAccess JUCA = SharedSecrets.getJavaUtilCollectionAccess();

    /**
     * Private constructor.
     */
    StringTemplateImplFactory() {
        throw new AssertionError("private constructor");
    }

    /*
     * {@link StringTemplateImpl} constructor MethodHandle.
     */
    private static final MethodHandle CONSTRUCTOR;

    /**
     * List (for nullable) of MethodHandle;
     */
    private static final MethodHandle TO_LIST;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();

            MethodType mt = MethodType.methodType(void.class, int.class, int.class, List.class,
                    MethodHandle.class, MethodHandle.class);
            CONSTRUCTOR = lookup.findConstructor(StringTemplateImpl.class, mt)
                    .asType(mt.changeReturnType(Carriers.CarrierObject.class));

            mt = MethodType.methodType(List.class, Object[].class);
            TO_LIST = lookup.findStatic(StringTemplateImplFactory.class, "toList", mt);
        } catch(ReflectiveOperationException ex) {
            throw new AssertionError("carrier static init fail", ex);
        }
    }

    /**
     * Create a new {@link StringTemplateImpl} constructor.
     *
     * @param fragments  string template fragments
     * @param type       method type
     *
     * @return {@link MethodHandle} that can construct a {@link StringTemplateImpl} with arguments
     * used as values.
     */
    static MethodHandle createStringTemplateImplMH(List<String> fragments, MethodType type) {
        Carriers.CarrierElements elements = Carriers.CarrierFactory.of(type);
        MethodHandle[] components = elements
                .components()
                .stream()
                .map(c -> c.asType(c.type().changeParameterType(0, StringTemplateImpl.class)))
                .toArray(MethodHandle[]::new);
        Class<?>[] ptypes = elements
                .components()
                .stream()
                .map(c -> c.type().returnType())
                .toArray(Class<?>[]::new);
        int[] permute = new int[ptypes.length];

        MethodHandle interpolateMH;
        MethodType mt;
        try {
            interpolateMH = StringConcatFactory.makeConcatWithTemplate(fragments, List.of(ptypes));
        } catch (StringConcatException ex) {
            throw new RuntimeException("constructing internal string template", ex);
        }
        interpolateMH = MethodHandles.filterArguments(interpolateMH, 0, components);
        mt = MethodType.methodType(String.class, StringTemplateImpl.class);
        interpolateMH = MethodHandles.permuteArguments(interpolateMH, mt, permute);

        mt = MethodType.methodType(List.class, ptypes);
        MethodHandle valuesMH = TO_LIST.asCollector(Object[].class, components.length).asType(mt);
        valuesMH = MethodHandles.filterArguments(valuesMH, 0, components);
        mt = MethodType.methodType(List.class, StringTemplateImpl.class);
        valuesMH = MethodHandles.permuteArguments(valuesMH, mt, permute);

        MethodHandle constructor = MethodHandles.insertArguments(CONSTRUCTOR,0,
                elements.primitiveCount(), elements.objectCount(),
                fragments, valuesMH, interpolateMH);
        constructor = MethodHandles.foldArguments(elements.initializer(), 0, constructor);

        mt = MethodType.methodType(StringTemplate.class, ptypes);
        constructor = constructor.asType(mt);

        return constructor;
    }

    /**
     * Generic {@link StringTemplate}.
     *
     * @param fragments  immutable list of string fragments from string template
     * @param values     immutable list of expression values
     */
    private record SimpleStringTemplate(List<String> fragments, List<Object> values)
            implements StringTemplate {
        @Override
        public String toString() {
            return StringTemplate.toString(this);
        }
    }

    /**
     * Returns a new StringTemplate composed from fragments and values.
     *
     * @param fragments array of string fragments
     * @param values    array of expression values
     *
     * @return StringTemplate composed from fragments and values
     */
    static StringTemplate newStringTemplate(String[] fragments, Object[] values) {
        return new SimpleStringTemplate(List.of(fragments), toList(values));
    }

    /**
     * Returns a new StringTemplate composed from fragments and values.
     *
     * @param fragments list of string fragments
     * @param values    array of expression values
     *
     * @return StringTemplate composed from fragments and values
     */
    static StringTemplate newStringTemplate(List<String> fragments, Object[] values) {
        return new SimpleStringTemplate(List.copyOf(fragments), toList(values));
    }

    /**
     * Returns a new StringTemplate composed from fragments and values.
     *
     * @param fragments list of string fragments
     * @param values    list of expression values
     *
     * @return StringTemplate composed from fragments and values
     */
    static StringTemplate newStringTemplate(List<String> fragments, List<?> values) {
        return new SimpleStringTemplate(List.copyOf(fragments), toList(values.stream().toArray()));
    }

    /**
     * Collect nullable elements from an array into a unmodifiable list.
     * Elements are guaranteed to be safe.
     *
     * @param elements  elements to place in list
     *
     * @return unmodifiable list.
     */
    private static List<Object> toList(Object[] elements) {
        return JUCA.listFromTrustedArrayNullsAllowed(elements);
    }

}
