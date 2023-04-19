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

package jdk.internal.util.concurrent.lazy;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.lazy.KeyMapper;
import java.util.function.Function;

import static java.util.stream.Collectors.toMap;

public final class LazyMapper<K, V>
        extends AbstractMapper<K, V>
        implements Function<K, Optional<V>> {

    private final Map<K, Function<? super K, ? extends V>> mappers;

    public LazyMapper(Collection<KeyMapper<K, V>> keyMappers) {
        super(keyMappers.stream(), KeyMapper::key);
        this.mappers = keyMappers.stream()
                .collect(toMap(KeyMapper::key, lms -> lms.mapper()));
    }

    @Override
    public Optional<V> apply(K key) {
        return Optional.ofNullable(keyToInt.get(key))
                .map(i -> lazyArray.computeIfEmpty(i, k2 -> mappers.get(key).apply(key)));
    }

}
