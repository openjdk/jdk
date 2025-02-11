/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import jdk.internal.classfile.components.ClassPrinter;

/**
 * A {@code class} file structure that can be viewed as a composition of its
 * member structures.  {@code CompoundElement} allows users to traverse these
 * member elements with {@link #forEach(Consumer)} or {@link #elementStream()},
 * or buffer the elements obtained from the traversal through {@link
 * #iterator()} or {@link #elementList()}.
 * <p>
 * Unless otherwise specified, all member elements of compatible type will be
 * presented during the traversal if they exist in this element.  Some member
 * elements specify that they may appear at most once in this element; if such
 * elements are presented multiple times, the latest occurrence is authentic and
 * all previous occurrences should be ignored.
 * <p>
 * {@code CompoundElement}s can be constructed by {@link ClassFileBuilder}s.
 * {@link ClassFileBuilder#transform(CompoundElement, ClassFileTransform)}
 * provides an easy way to create a new structure by selectively processing
 * the original member structures and directing the results to the builder.
 *
 * @param <E> the member element type
 * @see ClassFileElement##membership Membership Elements
 * @see ClassFileBuilder
 * @sealedGraph
 * @since 24
 */
public sealed interface CompoundElement<E extends ClassFileElement>
        extends ClassFileElement, Iterable<E>
        permits ClassModel, CodeModel, FieldModel, MethodModel, jdk.internal.classfile.impl.AbstractUnboundModel {
    /**
     * Invokes the provided handler with each member element in this compound
     * element.
     *
     * @param consumer the handler
     */
    @Override
    void forEach(Consumer<? super E> consumer);

    /**
     * {@return an {@link Iterator} describing all member elements in this
     * compound element}
     */
    @Override
    default Iterator<E> iterator() {
        return elementList().iterator();
    }

    /**
     * {@return a {@link Stream} containing all member elements in this compound
     * element}
     */
    default Stream<E> elementStream() {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(
                                            iterator(),
                                            Spliterator.IMMUTABLE | Spliterator.NONNULL | Spliterator.ORDERED),
                                    false);
    }

    /**
     * {@return a {@link List} containing all member elements in this compound
     * element}
     */
    default List<E> elementList() {
        List<E> list = new ArrayList<>();
        forEach(new Consumer<>() {
            @Override
            public void accept(E e) {
                list.add(e);
            }
        });
        return Collections.unmodifiableList(list);
    }

    /**
     * {@return a text representation of the compound element and its contents
     * for debugging purposes}
     *
     * The format, structure and exact contents of the returned string are not
     * specified and may change at any time in the future.
     */
    default String toDebugString() {
        StringBuilder text = new StringBuilder();
        ClassPrinter.toYaml(this, ClassPrinter.Verbosity.TRACE_ALL, text::append);
        return text.toString();
    }
}
