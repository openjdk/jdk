/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.sparc;

import jdk.vm.ci.meta.PlatformKind;

public enum SPARCKind implements PlatformKind {
    BYTE(1),
    HWORD(2),
    WORD(4),
    XWORD(8),
    SINGLE(4),
    DOUBLE(8),
    QUAD(16),

    V32_BYTE(4, BYTE),
    V32_HWORD(4, HWORD),

    V64_BYTE(8, BYTE),
    V64_HWORD(8, HWORD),
    V64_WORD(8, WORD),
    V64_SINGLE(8, SINGLE);

    private final int size;
    private final int vectorLength;

    private final SPARCKind scalar;
    private final EnumKey<SPARCKind> key = new EnumKey<>(this);

    SPARCKind(int size) {
        this.size = size;
        this.scalar = this;
        this.vectorLength = 1;
    }

    SPARCKind(int size, SPARCKind scalar) {
        this.size = size;
        this.scalar = scalar;

        assert size % scalar.size == 0;
        this.vectorLength = size / scalar.size;
    }

    public SPARCKind getScalar() {
        return scalar;
    }

    public int getSizeInBytes() {
        return size;
    }

    public int getSizeInBits() {
        return getSizeInBytes() * 8;
    }

    public int getVectorLength() {
        return vectorLength;
    }

    public Key getKey() {
        return key;
    }

    public boolean isInteger() {
        switch (this) {
            case BYTE:
            case HWORD:
            case WORD:
            case XWORD:
                return true;
            default:
                return false;
        }
    }

    public boolean isFloat() {
        return !isInteger();
    }

    public char getTypeChar() {
        switch (this) {
            case BYTE:
                return 'b';
            case HWORD:
                return 'h';
            case WORD:
                return 'w';
            case XWORD:
                return 'd';
            case SINGLE:
                return 'S';
            case DOUBLE:
            case V64_BYTE:
            case V64_HWORD:
            case V64_WORD:
                return 'D';
            default:
                return '-';
        }
    }
}
