/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.word;

public interface Signed extends ComparableWord {

    /**
     * Returns a Signed whose value is {@code (this + val)}.
     *
     * @param val value to be added to this Signed.
     * @return {@code this + val}
     */
    Signed add(Signed val);

    /**
     * Returns a Signed whose value is {@code (this - val)}.
     *
     * @param val value to be subtracted from this Signed.
     * @return {@code this - val}
     */
    Signed subtract(Signed val);

    /**
     * Returns a Signed whose value is {@code (this * val)}.
     *
     * @param val value to be multiplied by this Signed.
     * @return {@code this * val}
     */
    Signed multiply(Signed val);

    /**
     * Returns a Signed whose value is {@code (this / val)}.
     *
     * @param val value by which this Signed is to be divided.
     * @return {@code this / val}
     */
    Signed signedDivide(Signed val);

    /**
     * Returns a Signed whose value is {@code (this % val)}.
     *
     * @param val value by which this Signed is to be divided, and the remainder computed.
     * @return {@code this % val}
     */
    Signed signedRemainder(Signed val);

    /**
     * Returns a Signed whose value is {@code (this << n)}.
     *
     * @param n shift distance, in bits.
     * @return {@code this << n}
     */
    Signed shiftLeft(Unsigned n);

    /**
     * Returns a Signed whose value is {@code (this >> n)}. Sign extension is performed.
     *
     * @param n shift distance, in bits.
     * @return {@code this >> n}
     */
    Signed signedShiftRight(Unsigned n);

    /**
     * Returns a Signed whose value is {@code (this & val)}. (This method returns a negative Signed
     * if and only if this and val are both negative.)
     *
     * @param val value to be AND'ed with this Signed.
     * @return {@code this & val}
     */
    Signed and(Signed val);

    /**
     * Returns a Signed whose value is {@code (this | val)}. (This method returns a negative Signed
     * if and only if either this or val is negative.)
     *
     * @param val value to be OR'ed with this Signed.
     * @return {@code this | val}
     */
    Signed or(Signed val);

    /**
     * Returns a Signed whose value is {@code (this ^ val)}. (This method returns a negative Signed
     * if and only if exactly one of this and val are negative.)
     *
     * @param val value to be XOR'ed with this Signed.
     * @return {@code this ^ val}
     */
    Signed xor(Signed val);

    /**
     * Returns a Signed whose value is {@code (~this)}. (This method returns a negative value if and
     * only if this Signed is non-negative.)
     *
     * @return {@code ~this}
     */
    Signed not();

    /**
     * Compares this Signed with the specified value.
     *
     * @param val value to which this Signed is to be compared.
     * @return {@code this == val}
     */
    boolean equal(Signed val);

    /**
     * Compares this Signed with the specified value.
     *
     * @param val value to which this Signed is to be compared.
     * @return {@code this != val}
     */
    boolean notEqual(Signed val);

    /**
     * Compares this Signed with the specified value.
     *
     * @param val value to which this Signed is to be compared.
     * @return {@code this < val}
     */
    boolean lessThan(Signed val);

    /**
     * Compares this Signed with the specified value.
     *
     * @param val value to which this Signed is to be compared.
     * @return {@code this <= val}
     */
    boolean lessOrEqual(Signed val);

    /**
     * Compares this Signed with the specified value.
     *
     * @param val value to which this Signed is to be compared.
     * @return {@code this > val}
     */
    boolean greaterThan(Signed val);

    /**
     * Compares this Signed with the specified value.
     *
     * @param val value to which this Signed is to be compared.
     * @return {@code this >= val}
     */
    boolean greaterOrEqual(Signed val);

    /**
     * Returns a Signed whose value is {@code (this + val)}.
     *
     * @param val value to be added to this Signed.
     * @return {@code this + val}
     */
    Signed add(int val);

    /**
     * Returns a Signed whose value is {@code (this - val)}.
     *
     * @param val value to be subtracted from this Signed.
     * @return {@code this - val}
     */
    Signed subtract(int val);

    /**
     * Returns a Signed whose value is {@code (this * val)}.
     *
     * @param val value to be multiplied by this Signed.
     * @return {@code this * val}
     */
    Signed multiply(int val);

    /**
     * Returns a Signed whose value is {@code (this / val)}.
     *
     * @param val value by which this Signed is to be divided.
     * @return {@code this / val}
     */
    Signed signedDivide(int val);

    /**
     * Returns a Signed whose value is {@code (this % val)}.
     *
     * @param val value by which this Signed is to be divided, and the remainder computed.
     * @return {@code this % val}
     */
    Signed signedRemainder(int val);

    /**
     * Returns a Signed whose value is {@code (this << n)}.
     *
     * @param n shift distance, in bits.
     * @return {@code this << n}
     */
    Signed shiftLeft(int n);

    /**
     * Returns a Signed whose value is {@code (this >> n)}. Sign extension is performed.
     *
     * @param n shift distance, in bits.
     * @return {@code this >> n}
     */
    Signed signedShiftRight(int n);

    /**
     * Returns a Signed whose value is {@code (this & val)}. (This method returns a negative Signed
     * if and only if this and val are both negative.)
     *
     * @param val value to be AND'ed with this Signed.
     * @return {@code this & val}
     */
    Signed and(int val);

    /**
     * Returns a Signed whose value is {@code (this | val)}. (This method returns a negative Signed
     * if and only if either this or val is negative.)
     *
     * @param val value to be OR'ed with this Signed.
     * @return {@code this | val}
     */
    Signed or(int val);

    /**
     * Returns a Signed whose value is {@code (this ^ val)}. (This method returns a negative Signed
     * if and only if exactly one of this and val are negative.)
     *
     * @param val value to be XOR'ed with this Signed.
     * @return {@code this ^ val}
     */
    Signed xor(int val);

    /**
     * Compares this Signed with the specified value.
     *
     * @param val value to which this Signed is to be compared.
     * @return {@code this == val}
     */
    boolean equal(int val);

    /**
     * Compares this Signed with the specified value.
     *
     * @param val value to which this Signed is to be compared.
     * @return {@code this != val}
     */
    boolean notEqual(int val);

    /**
     * Compares this Signed with the specified value.
     *
     * @param val value to which this Signed is to be compared.
     * @return {@code this < val}
     */
    boolean lessThan(int val);

    /**
     * Compares this Signed with the specified value.
     *
     * @param val value to which this Signed is to be compared.
     * @return {@code this <= val}
     */
    boolean lessOrEqual(int val);

    /**
     * Compares this Signed with the specified value.
     *
     * @param val value to which this Signed is to be compared.
     * @return {@code this > val}
     */
    boolean greaterThan(int val);

    /**
     * Compares this Signed with the specified value.
     *
     * @param val value to which this Signed is to be compared.
     * @return {@code this >= val}
     */
    boolean greaterOrEqual(int val);
}
