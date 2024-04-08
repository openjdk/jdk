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

package jdk.internal.lang.lazy;

import jdk.internal.misc.Unsafe;

import java.util.NoSuchElementException;

import static jdk.internal.misc.Unsafe.*;

public final class LazyUtil {

    private LazyUtil() {
    }

    public static final Unsafe UNSAFE = Unsafe.getUnsafe();

    static UnsupportedOperationException uoe() {
        return new UnsupportedOperationException();
    }

    public static NoSuchElementException noKey(Object key) {
        return new NoSuchElementException("No such key:" + key);
    }

    static String toString(Lazy<?> lazy) {
        return "Lazy" +
                (lazy.isSet()
                        ? "[" + lazy.orThrow() + "]"
                        : ".unset");
    }

    /**
     * Performs a "freeze" operation, required to ensure safe publication under plain
     * memory read semantics.
     * <p>
     * This inserts a memory barrier, thereby establishing a happens-before constraint.
     * This prevents the reordering of store operations across the freeze boundary.
     */
    public static void freeze() {
        // Issue a store fence, which is sufficient
        // to provide protection against store/store reordering.
        // See VarHandle::releaseFence
        UNSAFE.storeFence();
    }

    public static long objectOffset(int index) {
        return ARRAY_OBJECT_BASE_OFFSET + (long) index * ARRAY_OBJECT_INDEX_SCALE;
    }

    public static long byteOffset(int index) {
        return ARRAY_BYTE_BASE_OFFSET + (long) index * ARRAY_BYTE_INDEX_SCALE;
    }

}
