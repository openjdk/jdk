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
package jdk.internal.classfile.impl;

import java.lang.classfile.Attribute;
import java.lang.classfile.AttributedElement;
import java.lang.classfile.ClassFileElement;
import java.lang.classfile.CompoundElement;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

public abstract sealed class AbstractUnboundModel<E extends ClassFileElement>
        extends AbstractElement
        implements CompoundElement<E>, AttributedElement
        permits BufferedCodeBuilder.Model, BufferedFieldBuilder.Model, BufferedMethodBuilder.Model {
    final List<E> elements;
    private List<Attribute<?>> attributes;

    public AbstractUnboundModel(List<E> elements) {
        this.elements = Collections.unmodifiableList(elements);
    }

    @Override
    public void forEach(Consumer<? super E> consumer) {
        elements.forEach(consumer);
    }

    @Override
    public Stream<E> elementStream() {
        return elements.stream();
    }

    @Override
    public List<E> elementList() {
        return elements;
    }

    @Override
    public List<Attribute<?>> attributes() {
        if (attributes == null)
            attributes = elements.stream()
                                 .<Attribute<?>>mapMulti((e, sink) -> {
                                     if (e instanceof Attribute<?> attr) {
                                         sink.accept(attr);
                                     }
                                 })
                                 .toList();
        return attributes;
    }
}
