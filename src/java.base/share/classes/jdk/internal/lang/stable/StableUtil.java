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

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;

public final class StableUtil {

    private StableUtil() {}

    public static <R> String renderElements(Object self,
                                            String selfName,
                                            StableValueImpl<?>[] delegates) {
        return renderElements(self, selfName, delegates, 0, delegates.length);
    }

    public static <R> String renderElements(Object self,
                                            String selfName,
                                            StableValueImpl<?>[] delegates,
                                            int offset,
                                            int length) {
        final StringJoiner sj = new StringJoiner(", ", "[", "]");
        for (int i = 0; i < length; i++) {
            final Object value = delegates[i + offset].wrappedContentsAcquire();
            if (value == self) {
                sj.add("(this " + selfName + ")");
            } else {
                sj.add(StableValueImpl.renderWrapped(value));
            }
        }
        return sj.toString();
    }

    public static <K, V> String renderMappings(Object self,
                                               String selfName,
                                               Iterable<Map.Entry<K, StableValueImpl<V>>> delegates,
                                               boolean curly) {
        final StringJoiner sj = new StringJoiner(", ", curly ? "{" : "[", curly ? "}" : "]");
        for (var e : delegates) {
            final Object value = e.getValue().wrappedContentsAcquire();
            final String valueString;
            if (value == self) {
                valueString = "(this " + selfName + ")";
            } else {
                valueString = StableValueImpl.renderWrapped(value);
            }
            sj.add(e.getKey() + "=" + valueString);
        }
        return sj.toString();
    }

    public static <T> StableValueImpl<T>[] array(int size) {
        assertSizeNonNegative(size);
        @SuppressWarnings("unchecked")
        final var stableValues = (StableValueImpl<T>[]) new StableValueImpl<?>[size];
        for (int i = 0; i < size; i++) {
            stableValues[i] = StableValueImpl.of();
        }
        return stableValues;
    }

    public static <K, T> Map<K, StableValueImpl<T>> map(Set<K> keys) {
        Objects.requireNonNull(keys);
        @SuppressWarnings("unchecked")
        final var entries = (Map.Entry<K, StableValueImpl<T>>[]) new Map.Entry<?, ?>[keys.size()];
        int i = 0;
        for (K key : keys) {
            entries[i++] = Map.entry(key, StableValueImpl.of());
        }
        return Map.ofEntries(entries);
    }

    public static void assertSizeNonNegative(int size) {
        if (size < 0) {
            throw new IllegalArgumentException("size can not be negative: " + size);
        }
    }

}
