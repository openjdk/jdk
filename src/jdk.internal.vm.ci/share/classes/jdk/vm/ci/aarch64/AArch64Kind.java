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
package jdk.vm.ci.aarch64;

import jdk.vm.ci.meta.PlatformKind;

public enum AArch64Kind implements PlatformKind {

    // scalar
    BYTE(1),
    WORD(2),
    DWORD(4),
    QWORD(8),
    SINGLE(4),
    DOUBLE(8),

    // SIMD
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
    V128_DOUBLE(16, DOUBLE);

    private final int size;
    private final int vectorLength;

    private final AArch64Kind scalar;
    private final EnumKey<AArch64Kind> key = new EnumKey<>(this);

    AArch64Kind(int size) {
        this.size = size;
        this.scalar = this;
        this.vectorLength = 1;
    }

    AArch64Kind(int size, AArch64Kind scalar) {
        this.size = size;
        this.scalar = scalar;

        assert size % scalar.size == 0;
        this.vectorLength = size / scalar.size;
    }

    public AArch64Kind getScalar() {
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

    public boolean isSIMD() {
        return switch (this) {
            case SINGLE, DOUBLE, V32_BYTE, V32_WORD, V64_BYTE, V64_WORD, V64_DWORD, V128_BYTE, V128_WORD, V128_DWORD,
                 V128_QWORD, V128_SINGLE, V128_DOUBLE -> true;
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
            case V32_BYTE, V32_WORD, V64_BYTE, V64_WORD, V64_DWORD, V128_BYTE, V128_WORD, V128_DWORD, V128_QWORD,
                 V128_SINGLE, V128_DOUBLE -> 'v';
            default -> '-';
        };
    }
}
