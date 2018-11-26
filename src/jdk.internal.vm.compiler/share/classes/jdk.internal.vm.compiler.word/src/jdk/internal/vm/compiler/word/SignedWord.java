/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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


















package jdk.internal.vm.compiler.word;

/**
 * Represents a signed word-sized value.
 *
 * @since 1.0
 */
public interface SignedWord extends ComparableWord {

    /**
     * Returns a Signed whose value is {@code (this + val)}.
     *
     * @param val value to be added to this Signed.
     * @return {@code this + val}
     *
     * @since 1.0
     */
    SignedWord add(SignedWord val);

    /**
     * Returns a Signed whose value is {@code (this - val)}.
     *
     * @param val value to be subtracted from this Signed.
     * @return {@code this - val}
     *
     * @since 1.0
     */
    SignedWord subtract(SignedWord val);

    /**
     * Returns a Signed whose value is {@code (this * val)}.
     *
     * @param val value to be multiplied by this Signed.
     * @return {@code this * val}
     *
     * @since 1.0
     */
    SignedWord multiply(SignedWord val);

    /**
     * Returns a Signed whose value is {@code (this / val)}.
     *
     * @param val value by which this Signed is to be divided.
     * @return {@code this / val}
     *
     * @since 1.0
     */
    SignedWord signedDivide(SignedWord val);

    /**
     * Returns a Signed whose value is {@code (this % val)}.
     *
     * @param val value by which this Signed is to be divided, and the remainder computed.
     * @return {@code this % val}
     *
     * @since 1.0
     */
    SignedWord signedRemainder(SignedWord val);

    /**
     * Returns a Signed whose value is {@code (this << n)}.
     *
     * @param n shift distance, in bits.
     * @return {@code this << n}
     *
     * @since 1.0
     */
    SignedWord shiftLeft(UnsignedWord n);

    /**
     * Returns a Signed whose value is {@code (this >> n)}. Sign extension is performed.
     *
     * @param n shift distance, in bits.
     * @return {@code this >> n}
     *
     * @since 1.0
     */
    SignedWord signedShiftRight(UnsignedWord n);

    /**
     * Returns a Signed whose value is {@code (this & val)}. (This method returns a negative Signed
     * if and only if this and val are both negative.)
     *
     * @param val value to be AND'ed with this Signed.
     * @return {@code this & val}
     *
     * @since 1.0
     */
    SignedWord and(SignedWord val);

    /**
     * Returns a Signed whose value is {@code (this | val)}. (This method returns a negative Signed
     * if and only if either this or val is negative.)
     *
     * @param val value to be OR'ed with this Signed.
     * @return {@code this | val}
     *
     * @since 1.0
     */
    SignedWord or(SignedWord val);

    /**
     * Returns a Signed whose value is {@code (this ^ val)}. (This method returns a negative Signed
     * if and only if exactly one of this and val are negative.)
     *
     * @param val value to be XOR'ed with this Signed.
     * @return {@code this ^ val}
     *
     * @since 1.0
     */
    SignedWord xor(SignedWord val);

    /**
     * Returns a Signed whose value is {@code (~this)}. (This method returns a negative value if and
     * only if this Signed is non-negative.)
     *
     * @return {@code ~this}
     *
     * @since 1.0
     */
    SignedWord not();

    /**
     * Compares this Signed with the specified value.
     *
     * @param val value to which this Signed is to be compared.
     * @return {@code this == val}
     *
     * @since 1.0
     */
    boolean equal(SignedWord val);

    /**
     * Compares this Signed with the specified value.
     *
     * @param val value to which this Signed is to be compared.
     * @return {@code this != val}
     *
     * @since 1.0
     */
    boolean notEqual(SignedWord val);

    /**
     * Compares this Signed with the specified value.
     *
     * @param val value to which this Signed is to be compared.
     * @return {@code this < val}
     *
     * @since 1.0
     */
    boolean lessThan(SignedWord val);

    /**
     * Compares this Signed with the specified value.
     *
     * @param val value to which this Signed is to be compared.
     * @return {@code this <= val}
     *
     * @since 1.0
     */
    boolean lessOrEqual(SignedWord val);

    /**
     * Compares this Signed with the specified value.
     *
     * @param val value to which this Signed is to be compared.
     * @return {@code this > val}
     *
     * @since 1.0
     */
    boolean greaterThan(SignedWord val);

    /**
     * Compares this Signed with the specified value.
     *
     * @param val value to which this Signed is to be compared.
     * @return {@code this >= val}
     *
     * @since 1.0
     */
    boolean greaterOrEqual(SignedWord val);

    /**
     * Returns a Signed whose value is {@code (this + val)}.
     *
     * @param val value to be added to this Signed.
     * @return {@code this + val}
     *
     * @since 1.0
     */
    SignedWord add(int val);

    /**
     * Returns a Signed whose value is {@code (this - val)}.
     *
     * @param val value to be subtracted from this Signed.
     * @return {@code this - val}
     *
     * @since 1.0
     */
    SignedWord subtract(int val);

    /**
     * Returns a Signed whose value is {@code (this * val)}.
     *
     * @param val value to be multiplied by this Signed.
     * @return {@code this * val}
     *
     * @since 1.0
     */
    SignedWord multiply(int val);

    /**
     * Returns a Signed whose value is {@code (this / val)}.
     *
     * @param val value by which this Signed is to be divided.
     * @return {@code this / val}
     *
     * @since 1.0
     */
    SignedWord signedDivide(int val);

    /**
     * Returns a Signed whose value is {@code (this % val)}.
     *
     * @param val value by which this Signed is to be divided, and the remainder computed.
     * @return {@code this % val}
     *
     * @since 1.0
     */
    SignedWord signedRemainder(int val);

    /**
     * Returns a Signed whose value is {@code (this << n)}.
     *
     * @param n shift distance, in bits.
     * @return {@code this << n}
     *
     * @since 1.0
     */
    SignedWord shiftLeft(int n);

    /**
     * Returns a Signed whose value is {@code (this >> n)}. Sign extension is performed.
     *
     * @param n shift distance, in bits.
     * @return {@code this >> n}
     *
     * @since 1.0
     */
    SignedWord signedShiftRight(int n);

    /**
     * Returns a Signed whose value is {@code (this & val)}. (This method returns a negative Signed
     * if and only if this and val are both negative.)
     *
     * @param val value to be AND'ed with this Signed.
     * @return {@code this & val}
     *
     * @since 1.0
     */
    SignedWord and(int val);

    /**
     * Returns a Signed whose value is {@code (this | val)}. (This method returns a negative Signed
     * if and only if either this or val is negative.)
     *
     * @param val value to be OR'ed with this Signed.
     * @return {@code this | val}
     *
     * @since 1.0
     */
    SignedWord or(int val);

    /**
     * Returns a Signed whose value is {@code (this ^ val)}. (This method returns a negative Signed
     * if and only if exactly one of this and val are negative.)
     *
     * @param val value to be XOR'ed with this Signed.
     * @return {@code this ^ val}
     *
     * @since 1.0
     */
    SignedWord xor(int val);

    /**
     * Compares this Signed with the specified value.
     *
     * @param val value to which this Signed is to be compared.
     * @return {@code this == val}
     *
     * @since 1.0
     */
    boolean equal(int val);

    /**
     * Compares this Signed with the specified value.
     *
     * @param val value to which this Signed is to be compared.
     * @return {@code this != val}
     *
     * @since 1.0
     */
    boolean notEqual(int val);

    /**
     * Compares this Signed with the specified value.
     *
     * @param val value to which this Signed is to be compared.
     * @return {@code this < val}
     *
     * @since 1.0
     */
    boolean lessThan(int val);

    /**
     * Compares this Signed with the specified value.
     *
     * @param val value to which this Signed is to be compared.
     * @return {@code this <= val}
     *
     * @since 1.0
     */
    boolean lessOrEqual(int val);

    /**
     * Compares this Signed with the specified value.
     *
     * @param val value to which this Signed is to be compared.
     * @return {@code this > val}
     *
     * @since 1.0
     */
    boolean greaterThan(int val);

    /**
     * Compares this Signed with the specified value.
     *
     * @param val value to which this Signed is to be compared.
     * @return {@code this >= val}
     *
     * @since 1.0
     */
    boolean greaterOrEqual(int val);
}
