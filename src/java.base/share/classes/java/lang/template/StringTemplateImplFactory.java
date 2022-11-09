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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.StringConcatException;
import java.lang.invoke.StringConcatFactory;
import java.util.List;

import jdk.internal.access.JavaTemplateAccess;
import jdk.internal.access.JavaUtilCollectionAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.javac.PreviewFeature;

import static java.lang.invoke.MethodType.methodType;

/**
 * This class synthesizes {@link StringTemplate StringTemplates} based on
 * fragments and bootstrap method type.
 *
 * @since 20
 */
@PreviewFeature(feature=PreviewFeature.Feature.STRING_TEMPLATES)
final class StringTemplateImplFactory implements JavaTemplateAccess {

    /**
     * Private constructor.
     */
    StringTemplateImplFactory() {
    }

    /*
     * {@link StringTemplateImpl} constructor MethodHandle.
     */
    private static final MethodHandle CONSTRUCTOR;

    /**
     * List (for nullable) of MethodHandle;
     */
    private static final MethodHandle TO_LIST;

    /**
     * Access to nullible form of {@code List.of}
     */
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
    private static <E> List<E> toList(E... elements) {
        return JUCA.listFromTrustedArrayNullsAllowed(elements);
    }

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();

            MethodType mt = methodType(void.class, int.class, int.class, List.class,
                    MethodHandle.class, MethodHandle.class);
            CONSTRUCTOR = lookup.findConstructor(StringTemplateImpl.class, mt)
                    .asType(mt.changeReturnType(Carriers.CarrierObject.class));

            mt = methodType(List.class, Object[].class);
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
    public  MethodHandle createStringTemplateImplMH(List<String> fragments, MethodType type) {
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
        mt = methodType(String.class, StringTemplateImpl.class);
        interpolateMH = MethodHandles.permuteArguments(interpolateMH, mt, permute);

        mt = methodType(List.class, ptypes);
        MethodHandle valuesMH = TO_LIST.asCollector(Object[].class, components.length).asType(mt);
        valuesMH = MethodHandles.filterArguments(valuesMH, 0, components);
        mt = methodType(List.class, StringTemplateImpl.class);
        valuesMH = MethodHandles.permuteArguments(valuesMH, mt, permute);

        MethodHandle constructor = MethodHandles.insertArguments(CONSTRUCTOR,0,
                elements.primitiveCount(), elements.objectCount(),
                fragments, valuesMH, interpolateMH);
        constructor = MethodHandles.foldArguments(elements.initializer(), 0, constructor);

        mt = methodType(StringTemplate.class, ptypes);
        constructor = constructor.asType(mt);

        return constructor;
    }

}
