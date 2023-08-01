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

package java.util;

import java.lang.invoke.MethodHandle;

/**
 * Digits provides a fast methodology for converting integers and longs to
 * ASCII strings.
 *
 * @since 21
 */
sealed interface Digits permits DecimalDigits, HexDigits, OctalDigits {
    /**
     * Insert digits for long value in buffer from high index to low index.
     *
     * @param value      value to convert
     * @param buffer     byte buffer to copy into
     * @param index      insert point + 1
     * @param putCharMH  method to put character
     *
     * @return the last index used
     *
     * @throws Throwable if putCharMH fails (unusual).
     */
    int digits(long value, byte[] buffer, int index,
               MethodHandle putCharMH) throws Throwable;

    /**
     * Calculate the number of digits required to represent the long.
     *
     * @param value value to convert
     *
     * @return number of digits
     */
    int size(long value);

}
