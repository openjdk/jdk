/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.core.common.util;

public abstract class AbstractTypeReader implements TypeReader {
    @Override
    public long getSV() {
        return decodeSign(read());
    }

    @Override
    public long getUV() {
        return read();
    }

    public static long decodeSign(long value) {
        return (value >>> 1) ^ -(value & 1);
    }

    private long read() {
        int b0 = getU1();
        if (b0 < UnsafeArrayTypeWriter.NUM_LOW_CODES) {
            return b0;
        } else {
            return readPacked(b0);
        }
    }

    private long readPacked(int b0) {
        assert b0 >= UnsafeArrayTypeWriter.NUM_LOW_CODES;
        long sum = b0;
        long shift = UnsafeArrayTypeWriter.HIGH_WORD_SHIFT;
        for (int i = 2;; i++) {
            long b = getU1();
            sum += b << shift;
            if (b < UnsafeArrayTypeWriter.NUM_LOW_CODES || i == UnsafeArrayTypeWriter.MAX_BYTES) {
                return sum;
            }
            shift += UnsafeArrayTypeWriter.HIGH_WORD_SHIFT;
        }
    }
}
