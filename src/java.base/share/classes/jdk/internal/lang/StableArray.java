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

package jdk.internal.lang;

import jdk.internal.lang.stable.StableArrayImpl;
import jdk.internal.lang.stable.TrustedFieldType;

/**
 * An atomic, thread-safe, stable array holder for which components can be set at most once.
 * <p>
 * Stable arrays are eligible for certain optimizations by the JVM.
 * <p>
 * The total number of components ({@linkplain StableArray#length()}) in a stable array
 * can not exceed about 2<sup>31</sup>.
 *
 * @param <V> type of StableValue that this stable array holds
 * @since 23
 */
public sealed interface StableArray<V>
        extends TrustedFieldType
        permits StableArrayImpl {

    /**
     * {@return the {@code [index]} component of this stable array}
     *
     * @param index to use as a component index
     * @throws ArrayIndexOutOfBoundsException if {@code
     *         index < 0 || index >= size()}
     */
    StableValue<V> get(int index);

    /**
     * {@return the length of this stable array}
     */
    int length();

    /**
     * {@return a new StableArray with the provided length}
     *
     * @param <V> type of StableValue the stable array holds
     * @throws IllegalArgumentException if the provided {@code length} is {@code < 0}
     */
    static <V> StableArray<V> of(int length) {
        if (length < 0) {
            throw new IllegalArgumentException();
        }
        return StableArrayImpl.of(length);
    }

}
