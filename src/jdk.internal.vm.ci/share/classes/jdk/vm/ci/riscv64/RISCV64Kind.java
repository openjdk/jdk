/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.riscv64;

import jdk.vm.ci.meta.PlatformKind;

public enum RISCV64Kind implements PlatformKind {

    // scalar
    BYTE(1),
    WORD(2),
    DWORD(4),
    QWORD(8),
    SINGLE(4),
    DOUBLE(8);

    private final int size;
    private final int vectorLength;

    private final RISCV64Kind scalar;
    private final EnumKey<RISCV64Kind> key = new EnumKey<>(this);

    RISCV64Kind(int size) {
        this.size = size;
        this.scalar = this;
        this.vectorLength = 1;
    }

    RISCV64Kind(int size, RISCV64Kind scalar) {
        this.size = size;
        this.scalar = scalar;

        assert size % scalar.size == 0;
        this.vectorLength = size / scalar.size;
    }

    public RISCV64Kind getScalar() {
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

    public boolean isFP() {
        return switch (this) {
            case SINGLE, DOUBLE -> true;
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
            default -> '-';
        };
    }

}
