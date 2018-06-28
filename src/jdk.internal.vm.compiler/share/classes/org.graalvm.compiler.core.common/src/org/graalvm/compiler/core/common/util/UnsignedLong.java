/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

public final class UnsignedLong {
    private final long value;

    public UnsignedLong(long value) {
        this.value = value;
    }

    public long asLong() {
        return value;
    }

    public boolean equals(long unsignedValue) {
        return value == unsignedValue;
    }

    public boolean isLessThan(long unsignedValue) {
        return Long.compareUnsigned(value, unsignedValue) < 0;
    }

    public boolean isLessOrEqualTo(long unsignedValue) {
        return Long.compareUnsigned(value, unsignedValue) <= 0;
    }

    public UnsignedLong times(long unsignedValue) {
        if (unsignedValue != 0 && Long.compareUnsigned(value, Long.divideUnsigned(0xffff_ffff_ffff_ffffL, unsignedValue)) > 0) {
            throw new ArithmeticException();
        }
        return new UnsignedLong(value * unsignedValue);
    }

    public UnsignedLong minus(long unsignedValue) {
        if (Long.compareUnsigned(value, unsignedValue) < 0) {
            throw new ArithmeticException();
        }
        return new UnsignedLong(value - unsignedValue);
    }

    public UnsignedLong plus(long unsignedValue) {
        if (Long.compareUnsigned(0xffff_ffff_ffff_ffffL - unsignedValue, value) < 0) {
            throw new ArithmeticException();
        }
        return new UnsignedLong(value + unsignedValue);
    }

    public UnsignedLong wrappingPlus(long unsignedValue) {
        return new UnsignedLong(value + unsignedValue);
    }

    public UnsignedLong wrappingTimes(long unsignedValue) {
        return new UnsignedLong(value * unsignedValue);
    }

    @Override
    public String toString() {
        return "UnsignedLong(" + Long.toUnsignedString(value) + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        UnsignedLong that = (UnsignedLong) o;
        return value == that.value;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(value);
    }
}
