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

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.Objects;

/**
 * {@link SoftReference} wrapper key for entries in the backing map.
 *
 * @param <T> key type
 *
 * @since 21
 */
final class SoftReferenceKey<T> extends SoftReference<T> implements ReferenceKey<T> {
    /**
     * Saved hashcode of the key. Used when {@link SoftReference} is
     * null.
     */
    private final int hashcode;

    /**
     * Package-Protected constructor.
     *
     * @param key   unwrapped key value
     * @param queue reference queue
     */
    SoftReferenceKey(T key, ReferenceQueue<T> queue) {
        super(key, queue);
        this.hashcode = Objects.hashCode(key);
    }

    /**
     * Cleanup unused key. No need to enqueue since the key did not make it
     * into the map.
     */
    @Override
    public void unused() {
        clear();
    }

    @Override
    public boolean equals(Object obj) {
        // Necessary when removing a null reference
        if (obj == this) {
            return true;
        }
        // Necessary when comparing an unwrapped key
        if (obj instanceof ReferenceKey<?> key) {
            obj = key.get();
        }
        // Note: refersTo is insufficient since keys require equivalence.
        // refersTo would also require a cast to type T.
        return Objects.equals(get(), obj);
    }

    @Override
    public int hashCode() {
        // Use saved hashcode
        return hashcode;
    }

    @Override
    public String toString() {
        return this.getClass().getCanonicalName() + "#" + System.identityHashCode(this);
    }
}
