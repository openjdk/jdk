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

package java.util.concurrent.lazy;

import jdk.internal.javac.PreviewFeature;
import jdk.internal.util.concurrent.lazy.StandardKeyMapper;

import java.util.Objects;
import java.util.function.Function;

/**
 * This class represents an association between a key
 * of type K and a mapper that can compute an associated
 * value of type V at a later time.
 *
 * @param <K>    the type of the key maintained by this association
 * @param <V>    the type of mapped values
 *
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.LAZY)
public sealed interface KeyMapper<K, V>
    permits StandardKeyMapper {

    /**
     * {@return the key for this KeyMapper for
     * the associated {@linkplain #mapper() mapper}}.
     */
    K key();

    /**
     * {@return the mapper for this KeyMapper to be applied for
     * the associated {@linkplain #key key} at a later time}.
     */
    Function<? super K, ? extends V> mapper();

    /**
     * {@return a new KeyMapper for the provided {@code key}/{@code mapper} association}.
     *
     * @param <K>    the type of the key maintained by this association
     * @param <V>    the type of mapped values
     * @param key    to associate to a mapper
     * @param mapper to associate to a key and to be applied for the key at a later time
     */
    public static <K, V> KeyMapper<K, V> of(K key,
                                            Function<? super K, ? extends V> mapper) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(mapper);
        return new StandardKeyMapper<>(key, mapper);
    }

}
