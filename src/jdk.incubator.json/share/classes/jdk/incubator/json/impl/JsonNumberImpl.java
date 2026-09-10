/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.incubator.json.impl;

import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

import jdk.incubator.json.JsonNumber;

/**
 * JsonNumber implementation class. Instances of this class are immutable.
 *
 * <p>A JSON number is represented by the range {@code [startOffset, endOffset)}
 * in {@code doc}. For a parsed instance, {@code doc} is the backing input JSON text.
 * For a factory-created instance, it is a private character array backing the
 * JSON number. If the JSON number contains a decimal point and/or an exponent,
 * their offsets are stored in {@code decimalOffset} and/or {@code exponentOffset}.
 * {@code -1} offset indicates it is absent.
 *
 * <p>{@code numString} lazily instantiates the JSON representation returned by
 * {@code toString()}. It preserves the original representation for a parsed value.
 *
 * <p>{@code numInteger}, {@code numLong}, and {@code numDouble} lazily instantiate
 * the {@code OptionalInt}, {@code OptionalLong}, and {@code OptionalDouble},
 * used by the conversion methods {@code asInt()}, {@code asLong()},
 * and {@code asDouble()}, respectively.
 */
public final class JsonNumberImpl implements JsonNumber, JsonValueSupport {

    private final char[] doc;
    private final int startOffset;
    private final int endOffset;
    private final int decimalOffset;
    private final int exponentOffset;
    private final boolean fromFactory;

    private final LazyConstant<String> numString = LazyConstant.of(this::initNumString);
    private final LazyConstant<OptionalInt> numInteger = LazyConstant.of(this::initNumInteger);
    private final LazyConstant<OptionalLong> numLong = LazyConstant.of(this::initNumLong);
    private final LazyConstant<OptionalDouble> numDouble = LazyConstant.of(this::initNumDouble);

    public JsonNumberImpl(char[] doc, boolean factory, int start, int end, int dec, int exp) {
        this.doc = doc;
        fromFactory = factory;
        startOffset = start;
        endOffset = end;
        decimalOffset = dec;
        exponentOffset = exp;
    }

    @Override
    public int asInt() {
        return numInteger.get().orElseThrow(() ->
            Utils.composeError(this, this + " cannot be represented as an int."));
    }

    @Override
    public long asLong() {
        return numLong.get().orElseThrow(() ->
                Utils.composeError(this, this + " cannot be represented as a long."));
    }

    @Override
    public double asDouble() {
        return numDouble.get().orElseThrow(() ->
                Utils.composeError(this, this + " cannot be represented as a double."));
    }

    @Override
    public char[] doc() {
        return fromFactory ? null : doc;
    }

    @Override
    public int offset() {
        return fromFactory ? -1 : startOffset;
    }

    @Override
    public String toString() {
        return numString.get();
    }

    // LazyConstants initializers
    private String initNumString() {
        return new String(doc, startOffset, endOffset - startOffset);
    }

    private OptionalInt initNumInteger() {
        try {
            var value = numLong.get();
            return value.isPresent()
                ? OptionalInt.of(Math.toIntExact(value.getAsLong()))
                : OptionalInt.empty();
        } catch (ArithmeticException _) {
            return OptionalInt.empty();
        }
    }

    private OptionalLong initNumLong() {
        try {
            if (decimalOffset == -1 && exponentOffset == -1) {
                // Fast-path immediate parseable Long format
                return OptionalLong.of(Long.parseLong(numString.get()));
            } else {
                // Decimal or exponent exists, derive value from
                // following format -> sig * 10^power
                // E.g. 54.32e1
                // sE is 'e' index / fL is 2 / exp is 1 / pow is -1 / sig is 5432 / scale is 0.1
                int sigEnd = exponentOffset == -1 ? endOffset : exponentOffset;
                int fracLen = decimalOffset == -1 ? 0 : sigEnd - decimalOffset - 1;
                int strippedZeros = 0;

                // Remove trailing zeros from the significand and compensate in the power.
                // This helps us avoid overflow for a value that fits in a long but has trailing zeros.
                // For example, the JSON number 9223372036854775807.000000 that fits in a long causes
                // overflow when parsing its digits but normalizing the zeros lets us parse the
                // sig as 9223372036854775807.
                while (sigEnd > startOffset) {
                    var c = doc[sigEnd - 1];
                    if (c == '0') {
                        sigEnd--;
                        strippedZeros++;
                    } else if (c == '.') {
                        sigEnd--;
                    } else {
                        break;
                    }
                }

                // A zero significand represents zero regardless of exponent size.
                if (sigEnd == startOffset || (doc[startOffset] == '-' && sigEnd == startOffset + 1)) {
                    return OptionalLong.of(0L);
                }

                // If not zero, we will derive the final value from
                // -> sig * 10^(exp - fracLen + strippedZeros)
                // -> sig * 10^power
                // -> sig * scale
                int exp = exponentOffset == -1 ? 0 : Integer.parseInt(new String(doc,
                        exponentOffset + 1, endOffset - exponentOffset - 1));
                int power = Math.addExact(Math.subtractExact(exp, fracLen), strippedZeros);
                long sig = decimalOffset == -1 || sigEnd <= decimalOffset
                        // Decimal point does not interfere with parsing sig
                        ? Long.parseLong(new String(doc, startOffset, sigEnd - startOffset))
                        // Parse both chunks to the left and right of the decimal point
                        : Long.parseLong(new String(doc, startOffset, decimalOffset - startOffset) +
                                new String(doc, decimalOffset + 1, sigEnd - decimalOffset - 1));
                if (power >= 0) {
                    long scale = Math.powExact(10L, power);
                    return OptionalLong.of(Math.multiplyExact(sig, scale));
                } else {
                    long scale = Math.powExact(10L, Math.negateExact(power));
                    return sig % scale == 0
                            ? OptionalLong.of(Math.divideExact(sig, scale))
                            : OptionalLong.empty(); // fractional leftover, so not representable as long
                }
            }
        } catch (NumberFormatException | ArithmeticException _) {}
        return OptionalLong.empty();
    }

    private OptionalDouble initNumDouble() {
        var db = Double.parseDouble(numString.get());
        if (Double.isFinite(db)) {
            return OptionalDouble.of(db);
        }
        return OptionalDouble.empty();
    }

    // Helper which converts this JNI to one that sees itself as created from a factory
    public JsonNumber toFactoryValue() {
        return new JsonNumberImpl(doc, true, startOffset, endOffset, decimalOffset, exponentOffset);
    }
}
