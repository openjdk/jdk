/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.lang.stable;

import jdk.internal.access.SharedSecrets;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * Todo: Remove this class once stable value finalizes and becomes a permanent feature.
 * <p>
 * This class allows stable functions and collections to be used without concern
 * for the {@code --enable-preview} flag. sTo use a StableValue without preview,
 * declare a field as {@code StableValueImpl} and use the {@link StableValueImpl#of()}
 * factory.
 */
public final class InternalStableFactories {

    private InternalStableFactories() {}

    public static <R> IntFunction<R> intFunction(int size,
                                                 IntFunction<? extends R> underlying) {
        StableUtil.assertSizeNonNegative(size);
        Objects.requireNonNull(underlying);
        return StableIntFunction.of(size, underlying);
    }

    public static <T, R> Function<T, R> function(Set<? extends T> inputs,
                                                 Function<? super T, ? extends R> underlying) {
        Objects.requireNonNull(inputs);
        // Checking that the Set of inputs does not contain a `null` value is made in the
        // implementing classes.
        Objects.requireNonNull(underlying);
        return inputs instanceof EnumSet<?> && !inputs.isEmpty()
                ? StableEnumFunction.of(inputs, underlying)
                : StableFunction.of(inputs, underlying);
    }

    public static <E> List<E> list(int size,
                                   IntFunction<? extends E> mapper) {
        StableUtil.assertSizeNonNegative(size);
        Objects.requireNonNull(mapper);
        return SharedSecrets.getJavaUtilCollectionAccess().stableList(size, mapper);
    }

    public static <K, V> Map<K, V> map(Set<K> keys,
                                       Function<? super K, ? extends V> mapper) {
        Objects.requireNonNull(keys);
        // Checking that the Set of keys does not contain a `null` value is made in the
        // implementing class.
        Objects.requireNonNull(mapper);
        return SharedSecrets.getJavaUtilCollectionAccess().stableMap(keys, mapper);
    }

}
