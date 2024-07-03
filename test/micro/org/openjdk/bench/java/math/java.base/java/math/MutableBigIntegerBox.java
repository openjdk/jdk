/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package java.math;

/**
 * A class for tests.
 */
public class MutableBigIntegerBox {

    /**
     * Constant zero
     */
    public static final MutableBigIntegerBox ZERO = new MutableBigIntegerBox(new MutableBigInteger());

    /**
     * Constant one
     */
    public static final MutableBigIntegerBox ONE = new MutableBigIntegerBox(MutableBigInteger.ONE);

    /**
     * Constant two
     */
    public static final MutableBigIntegerBox TWO = new MutableBigIntegerBox(new MutableBigInteger(2));

    private MutableBigInteger val;

    MutableBigIntegerBox(MutableBigInteger val) {
        this.val = val;
    }

    /**
     * Construct MutableBigIntegerBox from magnitude.
     * The value is normalized.
     * @param mag the magnitude
     */
    public MutableBigIntegerBox(int[] mag) {
        this(new MutableBigInteger(mag));
        val.normalize();
    }

    /**
     * Construct MutableBigIntegerBox from BigInteger val
     * @param val the value
     */
    public MutableBigIntegerBox(BigInteger val) {
        this(val.mag);
    }

    /**
     * Returns the bit length of this MutableBigInteger value
     * @return the bit length of this MutableBigInteger value
     */
    public long bitLength() {
        return val.bitLength();
    }

    /**
     * Return {@code this << n}
     * @return {@code this << n}
     * @param n the shift
     */
    public MutableBigIntegerBox shiftLeft(int n) {
        MutableBigInteger res = new MutableBigInteger(val);
        res.safeLeftShift(n);
        return new MutableBigIntegerBox(res);
    }

    /**
     * Return {@code this >> n}
     * @return {@code this >> n}
     * @param n the shift
     */
    public MutableBigIntegerBox shiftRight(int n) {
        MutableBigInteger res = new MutableBigInteger(val);
        res.safeRightShift(n);
        return new MutableBigIntegerBox(res);
    }

    /**
     * Return this + addend
     * @return this + addend
     * @param addend the addend
     */
    public MutableBigIntegerBox add(MutableBigIntegerBox addend) {
        MutableBigInteger res = new MutableBigInteger(val);
        res.add(addend.val);
        return new MutableBigIntegerBox(res);
    }

    /**
     * Return this - subtraend
     * @return this - subtraend
     * @param subtraend the subtraend
     */
    public MutableBigIntegerBox subtract(MutableBigIntegerBox subtraend) {
        MutableBigInteger res = new MutableBigInteger(val);
        res.subtract(subtraend.val);
        return new MutableBigIntegerBox(res);
    }

    /**
     * Return this * multiplier
     * @return this * multiplier
     * @param multiplier the multiplier
     */
    public MutableBigIntegerBox multiply(MutableBigIntegerBox multiplier) {
        MutableBigInteger res = new MutableBigInteger();
        if (!(val.isZero() || multiplier.val.isZero()))
            val.multiply(multiplier.val, res);

        return new MutableBigIntegerBox(res);
    }

    /**
     * Compare the magnitude of two MutableBigIntegers. Returns -1, 0 or 1
     * as this is numerically less than, equal to, or greater than {@code b}.
     * @return -1, 0 or 1 as this is numerically less than, equal to, or
     * greater than {@code b}.
     * @param b the value to compare
     */
    public int compare(MutableBigIntegerBox b) {
        return val.compare(b.val);
    }
}
