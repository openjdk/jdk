/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.amd64;

import jdk.vm.ci.meta.PlatformKind;

public enum AMD64Kind implements PlatformKind {

    // scalar
    BYTE(1),
    WORD(2),
    DWORD(4),
    QWORD(8),
    SINGLE(4),
    DOUBLE(8),

    // SSE2
    V32_BYTE(4, BYTE),
    V32_WORD(4, WORD),
    V64_BYTE(8, BYTE),
    V64_WORD(8, WORD),
    V64_DWORD(8, DWORD),
    V128_BYTE(16, BYTE),
    V128_WORD(16, WORD),
    V128_DWORD(16, DWORD),
    V128_QWORD(16, QWORD),
    V128_SINGLE(16, SINGLE),
    V128_DOUBLE(16, DOUBLE),

    // AVX
    V256_BYTE(32, BYTE),
    V256_WORD(32, WORD),
    V256_DWORD(32, DWORD),
    V256_QWORD(32, QWORD),
    V256_SINGLE(32, SINGLE),
    V256_DOUBLE(32, DOUBLE),

    // AVX512
    V512_BYTE(64, BYTE),
    V512_WORD(64, WORD),
    V512_DWORD(64, DWORD),
    V512_QWORD(64, QWORD),
    V512_SINGLE(64, SINGLE),
    V512_DOUBLE(64, DOUBLE),

    MASK8(1),
    MASK16(2),
    MASK32(4),
    MASK64(8);

    private final int size;
    private final int vectorLength;

    private final AMD64Kind scalar;
    private final EnumKey<AMD64Kind> key = new EnumKey<>(this);

    AMD64Kind(int size) {
        this.size = size;
        this.scalar = this;
        this.vectorLength = 1;
    }

    AMD64Kind(int size, AMD64Kind scalar) {
        this.size = size;
        this.scalar = scalar;

        assert size % scalar.size == 0;
        this.vectorLength = size / scalar.size;
    }

    public AMD64Kind getScalar() {
        return scalar;
    }

    @Override
    public int getSizeInBytes() {
        return size;
    }

    @Override
    public int getVectorLength() {
        return vectorLength;
    }

    @Override
    public Key getKey() {
        return key;
    }

    public boolean isInteger() {
        return switch (this) {
            case BYTE, WORD, DWORD, QWORD -> true;
            default -> false;
        };
    }

    public boolean isXMM() {
        return switch (this) {
            case SINGLE, DOUBLE, V32_BYTE, V32_WORD, V64_BYTE, V64_WORD, V64_DWORD, V128_BYTE, V128_WORD, V128_DWORD,
                 V128_QWORD, V128_SINGLE, V128_DOUBLE, V256_BYTE, V256_WORD, V256_DWORD, V256_QWORD, V256_SINGLE,
                 V256_DOUBLE, V512_BYTE, V512_WORD, V512_DWORD, V512_QWORD, V512_SINGLE, V512_DOUBLE -> true;
            default -> false;
        };
    }

    public boolean isMask() {
        return switch (this) {
            case MASK8, MASK16, MASK32, MASK64 -> true;
            default -> false;
        };
    }

    @Override
    public char getTypeChar() {
        return switch (this) {
            case BYTE -> 'b';
            case WORD -> 'w';
            case DWORD -> 'd';
            case QWORD -> 'q';
            case SINGLE -> 'S';
            case DOUBLE -> 'D';
            case V32_BYTE, V32_WORD, V64_BYTE, V64_WORD, V64_DWORD -> 'v';
            case V128_BYTE, V128_WORD, V128_DWORD, V128_QWORD, V128_SINGLE, V128_DOUBLE -> 'x';
            case V256_BYTE, V256_WORD, V256_DWORD, V256_QWORD, V256_SINGLE, V256_DOUBLE -> 'y';
            case V512_BYTE, V512_WORD, V512_DWORD, V512_QWORD, V512_SINGLE, V512_DOUBLE -> 'z';
            case MASK8, MASK16, MASK32, MASK64 -> 'k';
            default -> '-';
        };
    }
}
