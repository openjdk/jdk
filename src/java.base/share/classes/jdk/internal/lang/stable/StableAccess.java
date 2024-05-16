/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.access.JavaUtilCollectionAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.lang.StableValue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

public final class StableAccess {

    private StableAccess() {}

    private static final JavaUtilCollectionAccess ACCESS =
            SharedSecrets.getJavaUtilCollectionAccess();

    public static <V> List<StableValue<V>> ofList(int size) {
        if (size < 0) {
            throw new IllegalArgumentException();
        }
        if (size == 0) {
            return List.of();
        }
        return ACCESS.stableList(size);
    }

    public static <V> V computeIfUnset(List<StableValue<V>> list,
                                       int index,
                                       IntFunction<? extends V> mapper) {
        return ACCESS.computeIfUnset(list, index, mapper);
    }

    public static <K, V> Map<K, StableValue<V>> ofMap(Set<? extends K> keys) {
        if (keys.isEmpty()) {
            return Map.of();
        }
        return ACCESS.stableMap(keys);
    }

    public static <K, V> V computeIfUnset(Map<K, StableValue<V>> map,
                                          K key,
                                          Function<? super K, ? extends V> mapper) {
        return ACCESS.computeIfUnset(map, key, mapper);
    }

    public static <T> Supplier<T> memoizedSupplier(StableValue<T> stable,
                                                   Supplier<? extends T> original) {
        return new MemoizedSupplier<>(stable, original);
    }

    public static <R> IntFunction<R> memoizedIntFunction(List<StableValue<R>> stableList,
                                                         IntFunction<? extends R> original) {
        return new MemoizedIntFunction<>(stableList, original);
    }

    public static <T, R> Function<T, R> memoizedFunction(Map<T, StableValue<R>> stableMap,
                                                         Function<? super T, ? extends R> original) {
        return new MemoizedFunction<>(stableMap, original);
    }

    private record MemoizedSupplier<T>(StableValue<T> stable,
                                       Supplier<? extends T> original) implements Supplier<T> {

        @Override
        public T get() {
            return stable.computeIfUnset(original);
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }
    }

    private record MemoizedIntFunction<R>(List<StableValue<R>> stableList,
                                          IntFunction<? extends R> original) implements IntFunction<R> {

        @Override
        public R apply(int value) {
            return StableValue.computeIfUnset(stableList, value, original);
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }

    }

    private record MemoizedFunction<T, R>(Map<T, StableValue<R>> stableMap,
                                          Function<? super T, ? extends R> original) implements Function<T, R> {

        @Override
        public R apply(T t) {
            return StableValue.computeIfUnset(stableMap, t, original);
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }

    }

}
