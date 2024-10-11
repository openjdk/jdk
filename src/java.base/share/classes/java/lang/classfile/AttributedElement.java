/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
package java.lang.classfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import java.lang.classfile.attribute.RecordComponentInfo;
import jdk.internal.classfile.impl.AbstractUnboundModel;
import jdk.internal.javac.PreviewFeature;

/**
 * A {@link ClassFileElement} describing an entity that has attributes, such
 * as a class, field, method, code attribute, or record component.
 *
 * @sealedGraph
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
public sealed interface AttributedElement extends ClassFileElement
        permits ClassModel, CodeModel, FieldModel, MethodModel,
                RecordComponentInfo, AbstractUnboundModel {

    /**
     * {@return the attributes of this element}
     */
    List<Attribute<?>> attributes();

    /**
     * Finds an attribute by name.
     * @param attr the attribute mapper
     * @param <T> the type of the attribute
     * @return the attribute, or an empty {@linkplain Optional} if the attribute
     * is not present
     */
    default <T extends Attribute<T>> Optional<T> findAttribute(AttributeMapper<T> attr) {
        for (Attribute<?> la : attributes()) {
            if (la.attributeMapper() == attr) {
                @SuppressWarnings("unchecked")
                var res = Optional.of((T) la);
                return res;
            }
        }
        return Optional.empty();
    }

    /**
     * Finds one or more attributes by name.
     * @param attr the attribute mapper
     * @param <T> the type of the attribute
     * @return the attributes, or an empty {@linkplain List} if the attribute
     * is not present
     */
    default <T extends Attribute<T>> List<T> findAttributes(AttributeMapper<T> attr) {
        var list = new ArrayList<T>();
        for (var a : attributes()) {
            if (a.attributeMapper() == attr) {
                @SuppressWarnings("unchecked")
                T t = (T)a;
                list.add(t);
            }
        }
        return Collections.unmodifiableList(list);
    }
}
