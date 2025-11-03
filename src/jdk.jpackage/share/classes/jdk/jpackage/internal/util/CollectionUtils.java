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
package jdk.jpackage.internal.util;

import java.util.Collection;

/**
 * This class consists exclusively of static methods that operate on or return collections.
 */
public final class CollectionUtils {

    /**
     * Casts the given collection to the requested type.
     *
     * @param <T> the type of elements in this output collection
     * @param <B> the type of elements in this input collection
     * @param <C> the output collection type
     * @param v the input collection. Null is permitted.
     * @return the input collection cast to the requested type
     */
    @SuppressWarnings("unchecked")
    public static <T extends B, B, C extends Collection<T>> C toCollection(Collection<B> v) {
        Collection<?> tmp = v;
        return (C) tmp;
    }

    /**
     * Casts the given collection to the requested upper bounded wildcard (UBW) type.
     *
     * @param <T> the type of elements in this output collection
     * @param <B> the upper bound type of elements in this input collection
     * @param <C> the output collection type
     * @param v the input collection. Null is permitted.
     * @return the input collection cast to the requested type
     */
    @SuppressWarnings("unchecked")
    public static <T extends B, B, C extends Collection<T>> C toCollectionUBW(Collection<? extends B> v) {
        Collection<?> tmp = v;
        return (C) tmp;
    }
}
