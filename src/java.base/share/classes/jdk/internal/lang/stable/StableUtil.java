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

import jdk.internal.lang.StableValue;
import jdk.internal.misc.Unsafe;

import static jdk.internal.misc.Unsafe.*;

/**
 * Package private utility class for stable values & collections.
 */
final class StableUtil {

    private StableUtil() {}

    // Indicates a value is not set
    static final int UNSET = 0;
    // Indicates a value is set to a non-null value
    static final int NON_NULL = 1;
    // Indicates a value is set to a `null` value
    static final int NULL = 2;

    static final Unsafe UNSAFE = Unsafe.getUnsafe();

    static IllegalStateException alreadySet(StableValue<?> stable) {
        return new IllegalStateException("A value is already set: " + stable.orThrow());
    }

    /**
     * {@return a String representation of the provided {@code stable}}
     * @param stable to extract a string representation from
     */
    static String toString(StableValue<?> stable) {
        return "StableValue" +
                (stable.isSet()
                        ? "[" + stable.orThrow() + "]"
                        : ".unset");
    }

    /**
     * Performs a "freeze" operation, required to ensure safe publication under plain
     * memory read semantics.
     * <p>
     * This inserts a memory barrier, thereby establishing a happens-before constraint.
     * This prevents the reordering of store operations across the freeze boundary.
     */
    static void freeze() {
        // Issue a store fence, which is sufficient
        // to provide protection against store/store reordering.
        // See VarHandle::releaseFence
        UNSAFE.storeFence();
    }

    /**
     * {@return the address offset for an Object element in an Object array}
     * @param index for the object
     */
    static long objectOffset(int index) {
        return ARRAY_OBJECT_BASE_OFFSET + (long) index * ARRAY_OBJECT_INDEX_SCALE;
    }

    /**
     * {@return the address offset for an int element in an `int` array}
     * @param index for the object
     */
    static long intOffset(int index) {
        return ARRAY_INT_BASE_OFFSET + (long) index * ARRAY_INT_INDEX_SCALE;
    }

    static InternalError shouldNotReachHere() {
        return new InternalError("Should not reach here");
    }

}
