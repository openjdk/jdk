/*
 *  Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.  Oracle designates this
 *  particular file as subject to the "Classpath" exception as provided
 *  by Oracle in the LICENSE file that accompanied this code.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *   Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

package jdk.internal.foreign.layout;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.util.Objects;
import java.util.function.Function;

/**
 * A layout transformer that can be used to apply functions on selected types
 * of memory layouts.
 * <p>
 * A layout transformer can be used to convert byte ordering for all sub-members
 * or remove all names, for example.
 *
 * @param <T> the type of MemoryLayout for which transformation is to be made
 */
public sealed interface LayoutTransformer<T extends MemoryLayout>
        permits LayoutTransformerImpl {

    /**
     * {@return a transformed version of the provided {@code layout}}.
     * @param layout to transform
     */
    MemoryLayout transform(T layout);

    /**
     * {@return a transformed version of the provided {@code layout} by recursively
     * applying this transformer (breadth first) on all sub-members}
     * @param layout to transform
     */
    MemoryLayout deepTransform(MemoryLayout layout);

    /**
     * {@return a layout transformer that transforms layouts of the given {@code type}
     * using the provided {@code transformation}}
     * @param type to transform
     * @param transformation to apply
     * @param <T> the type of memory layout to transform
     */
    static <T extends MemoryLayout>
    LayoutTransformer<T> of(Class<T> type,
                            Function<? super T, ? extends MemoryLayout> transformation) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(type);
        return LayoutTransformerImpl.of(type, transformation);
    }

    /**
     * {@return a transformer that will remove all member names in memory layouts}
     */
    static LayoutTransformer<MemoryLayout> stripNames() {
        return LayoutTransformerImpl.STRIP_NAMES;
    }

    /**
     * {@return a transformer that will set member byte ordering to the provided
     * {@code byteOrder}}
     */
    static LayoutTransformer<ValueLayout> setByteOrder(ByteOrder byteOrder) {
        return LayoutTransformerImpl.setByteOrder(byteOrder);
    }

}
