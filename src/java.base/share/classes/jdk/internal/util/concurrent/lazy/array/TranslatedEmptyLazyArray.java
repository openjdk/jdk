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

package jdk.internal.util.concurrent.lazy.array;

import jdk.internal.util.concurrent.lazy.StandardEmptyLazyReference;

import java.util.Objects;
import java.util.concurrent.lazy.EmptyLazyArray;
import java.util.function.IntFunction;
import java.util.stream.IntStream;

public final class TranslatedEmptyLazyArray<V>
        extends AbstractBaseLazyArray<V, StandardEmptyLazyReference<V>>
        implements EmptyLazyArray<V> {

    private final int factor;

    @SuppressWarnings("unchecked")
    public TranslatedEmptyLazyArray(int length,
                                    int factor) {
        super(IntStream.range(0, length)
                .mapToObj(i -> new StandardEmptyLazyReference<>())
                .toArray(StandardEmptyLazyReference[]::new));
        this.factor = factor;
    }

    @Override
    public V computeIfEmpty(int index,
                            IntFunction<? extends V> mappper) {
        Objects.requireNonNull(mappper);
        if (index % factor == 0) {
            int translatedIndex = index / factor;
            return lazyObjects[translatedIndex]
                    .apply(() -> mappper.apply(index));
        }
        return mappper.apply(index);
    }

}
