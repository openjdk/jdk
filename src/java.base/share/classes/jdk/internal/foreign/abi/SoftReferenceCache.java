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
package jdk.internal.foreign.abi;

import java.lang.ref.SoftReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

final class SoftReferenceCache<K, V> {
    private final Map<K, Node> cache = new ConcurrentHashMap<>();

    public V get(K key, Function<K, V> valueFactory) {
        return cache
                .computeIfAbsent(key, k -> new Node()) // short lock (has to be according to ConcurrentHashMap)
                .get(key, valueFactory); // long lock, but just for the particular key
    }

    private final class Node {
        private volatile SoftReference<V> ref;

        V get(K key, Function<K, V> valueFactory) {
            V result;
            if (ref == null || (result = ref.get()) == null) {
                synchronized (this) { // don't let threads race on the valueFactory::apply call
                    if (ref == null || (result = ref.get()) == null) {
                        result = valueFactory.apply(key); // keep alive
                        ref = new SoftReference<>(result);
                    }
                }
            }
            return result;
        }
    }
}
