/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.event;

import java.lang.reflect.Field;
import jdk.internal.misc.Unsafe;

class JfrEpochGeneration {
    private static final Unsafe U;
    private static final long EPOCH_GENERATION_OFFSET;

    /**
     * The JFR epoch generation is the range [1-32767],i.e. 15 bits.
     *
     * These constants are useful when operating on a field where
     * the most significant bits are used to store a JFR epoch value.
     */
    static final int EPOCH_SHIFT_INT = 17;
    static final int VALUE_MASK_INT = (1 << EPOCH_SHIFT_INT) - 1;
    static final int EPOCH_SHIFT_LONG = 49;
    static final long VALUE_MASK_LONG = (1L << EPOCH_SHIFT_LONG) - 1;

    static {
        U = Unsafe.getUnsafe();
        EPOCH_GENERATION_OFFSET = getJfrEpochGenerationOffset();
    }

    private static native long getJfrEpochGenerationOffset();

    /**
     * The JFR epoch generation is the range [1-32767],i.e. 15 bits.
     */
    static short getCurrentEpoch() {
        return U.getShort(EPOCH_GENERATION_OFFSET);
    }

    /**
     * Get the address of a field to be used in cas operations.
     */
    static long getFieldOffset(Class<?> clazz, String fieldName) {
        return U.objectFieldOffset(clazz, fieldName);
    }

    static long getFieldOffset(Field field) {
        return U.objectFieldOffset(field);
    }

    static long getStaticFielddOffset(Field field) {
        return U.staticFieldOffset(field);
    }

    /**
     * An epoch generation can be stored atomically into a field,
     * perhaps together with some other value (use the shift and mask constants).
     * Can be useful sometimes when committing events, to ensure only
     * the setter will send an event, so as to limit events per epoch.
     */
    static boolean compareAndExchange(Object o, long offset, int c, int v) {
        return U.compareAndExchangeInt(o, offset, c, v) == c;
    }

    static boolean compareAndExchange(Object o, long offset, long c, long v) {
        return U.compareAndExchangeLong(o, offset, c, v) == c;
    }
}
