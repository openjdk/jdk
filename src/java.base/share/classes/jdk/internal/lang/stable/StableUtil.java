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
import jdk.internal.vm.annotation.DontInline;

import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * Package private utility class for stable values & collections.
 */
public final class StableUtil {

    private StableUtil() {}

    // State values

    // Indicates a value is not set
    static final byte UNSET = 0;
    // Indicates a value is set to a non-null value
    static final byte SET_NON_NULL = 1; // The middle value
    // Indicates a value is set to a `null` value
    static final byte SET_NULL = 2;
    // Indicates there was an error when computing a value
    static final byte ERROR = 3;

    // Computation values

    // Indicates a computation operation has NOT been invoked.
    static final byte NOT_INVOKED = 0;
    // Indicates a computation operation has been invoked.
    static final byte INVOKED = 1;

    static final Unsafe UNSAFE = Unsafe.getUnsafe();

    // Sentinel value used to mark that a mutex will not be used anymore
    static final Object TOMBSTONE = new Object();

    static IllegalStateException alreadySet(StableValue<?> stable) {
        return new IllegalStateException("A value is already set: " + stable.orThrow());
    }

    static NoSuchElementException notSet() {
        return new NoSuchElementException("No value set");
    }

    static NoSuchElementException error(StableValue<?> stable) {
        return new NoSuchElementException("An error occurred during computation");
    }

    static StackOverflowError stackOverflow(Object provider, Object key) {
        final String typeText = switch (provider) {
            case Supplier<?> _    -> "Supplier.get()";
            case IntFunction<?> _ -> "IntFunction.apply(" + key + ")";
            case Function<?, ?> _ -> "Function.apply(" + key + ")";
            default               -> throw shouldNotReachHere();
        };
        return new StackOverflowError(
                "Recursive invocation of " + typeText + ": " + provider);
    }

    /**
     * {@return a String representation of the provided {@code stable}}
     * @param stable to extract a string representation from
     */
    static String toString(StableValue<?> stable,
                           Function<Object, String> errorMessageMapper) {
        return "StableValue" +
                (stable.isSet()
                        ? "[" + stable.orThrow() + "]"
                        : stable.isError() ? ".error(" + errorMessageMapper.apply(stable) + ")" : ".unset");
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
        UNSAFE.storeStoreFence();
    }

    static InternalError shouldNotReachHere() {
        return new InternalError("Should not reach here");
    }

    static boolean isMutexNotNeeded(Object mutex) {
        return mutex == TOMBSTONE || mutex instanceof Throwable;
    }

    @SuppressWarnings("unchecked")
    public static <V> StableValueImpl<V>[] newStableValueArray(int length) {
        return (StableValueImpl<V>[]) new StableValueImpl<?>[length];
    }

    @SuppressWarnings("unchecked")
    @DontInline
    public static <V> StableValueImpl<V> getOrSetVolatile(StableValue<V>[] elements, int index) {
        final class Holder {
            private static final Unsafe UNSAFE = Unsafe.getUnsafe();
        }
        long offset = Unsafe.ARRAY_OBJECT_BASE_OFFSET + Unsafe.ARRAY_OBJECT_INDEX_SCALE * (long) index;
        StableValueImpl<V> stable = (StableValueImpl<V>)Holder.UNSAFE.getReferenceVolatile(elements, offset);
        if (stable == null) {
            stable = StableValueImpl.of();
            StableValueImpl<V> witness = (StableValueImpl<V>)
                    Holder.UNSAFE.compareAndExchangeReference(elements, offset, null, stable);
            return witness == null ? stable : witness;
        }
        return stable;
    }

}
