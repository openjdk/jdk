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

package jdk.internal.util;

import java.util.Objects;

/**
 * Wrapper for querying the backing map. Avoids the overhead of an
 * {@link java.lang.ref.Reference} object.
 *
 * @param <T> key type
 *
 * @since 21
 */
final class StrongReferenceKey<T> implements ReferenceKey<T> {
    T key;

    /**
     * Package-Protected constructor.
     *
     * @param key unwrapped key value
     */
    StrongReferenceKey(T key) {
        this.key = key;
    }

    /**
     * {@return the unwrapped key}
     */
    @Override
    public T get() {
        return key;
    }

    @Override
    public void unused() {
        key = null;
    }

    @Override
    public boolean equals(Object obj) {
        // Necessary when comparing an unwrapped key
        if (obj instanceof ReferenceKey<?> key) {
            obj = key.get();
        }
        return Objects.equals(get(), obj);
    }

    @Override
    public int hashCode() {
        // Use unwrapped key hash code
        return get().hashCode();
    }

    @Override
    public String toString() {
        return this.getClass().getCanonicalName() + "#" + System.identityHashCode(this);
    }
}
