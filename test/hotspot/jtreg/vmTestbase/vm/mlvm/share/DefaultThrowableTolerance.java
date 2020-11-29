/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package vm.mlvm.share;

/**
 * A collection of default implementations for {@link ThrowableTolerance}.
 */
public class DefaultThrowableTolerance {

    /**
     * Does not accept any Throwable
     *
     * @param ignored Added to satisfy the interface API, always ignored.
     * @return Always false
     */
    public static final ThrowableTolerance INTOLERANT = (Throwable ignored) -> { return false; };

    /**
     * Accepts only OutOfMemoryError with cause having 'Out of space' substring.
     *
     * Is useful for Code Cache depletion errors, for example. Scans the Throwable
     * hierarchy in search for acceptable Throwable as an underlying cause.
     * @param what Added to satisfy the interface API, is ignored.
     * @return Always false
     */
    public static final ThrowableTolerance CODE_CACHE_OOME_ALLOWED = (Throwable what) -> {
        Throwable cause = what;
        do {
            if (cause instanceof VirtualMachineError
                    && cause.getMessage().matches(".*[Oo]ut of space.*")) {
                return true;
            }
            cause = cause != null ? cause.getCause() : null;
        } while (cause != null && cause != what);
        return false;
    };
}
