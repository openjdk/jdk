/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8345403
 * @summary FloatingDecimal parsing methods (use -Dseed=X to set seed,
 *      use -Dsamples=N to set the number of random samples per test)
 * @modules java.base/jdk.internal.math
 * @library /test/lib
 * @build jdk.test.lib.RandomFactory
 * @run junit TestRandomFloatingDecimal
 * @key randomness
 */

import jdk.internal.math.FloatingDecimal;
import jdk.test.lib.RandomFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Random;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestRandomFloatingDecimal {

    /*
     * This class relies on the correctness of
     *      BigInteger string parsing, both decimal and hexadecimal
     *      BigDecimal floatValue() and doubleValue() conversions
     * and on the fact that the implementation of the BigDecimal conversions is
     * independent of the implementation in FloatingDecimal.
     * Hence, the expected values are those computed by BigDecimal,
     * while the actual values are those returned by FloatingDecimal.
     */


    private static final Random RANDOM = RandomFactory.getRandom();
    private static int samples;  // random samples per test

    static Stream<Args> testRandomDecForFloat() {
        return Stream.generate(() -> randomDec(false)).limit(samples);
    }

    static Stream<Args> testRandomDecForDouble() {
        return Stream.generate(() -> randomDec(true)).limit(samples);
    }

    static Stream<Args> testRandomHexForFloat() {
        return Stream.generate(() -> randomHex(false)).limit(samples);
    }

    static Stream<Args> testRandomHexForDouble() {
        return Stream.generate(() -> randomHex(true)).limit(samples);
    }

    private static final String SAMPLES_PROP = "samples";

    @BeforeAll
    static void setCount() {
        String prop = System.getProperty(SAMPLES_PROP, "10000");  // 10_000
        try {
            samples = Integer.parseInt(prop);
            if (samples <= 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException _) {
            throw new IllegalArgumentException("-D" + SAMPLES_PROP + "=" + prop + " must specify a valid positive decimal integer.");
        }
    }

    @ParameterizedTest
    @MethodSource
    void testRandomDecForFloat(Args args) {
        float expected = args.decimal().floatValue();
        float actual = FloatingDecimal.parseFloat(args.s());
        assertEquals(expected, actual);
    }

    @ParameterizedTest
    @MethodSource
    void testRandomDecForDouble(Args args) {
        double expected = args.decimal().doubleValue();
        double actual = FloatingDecimal.parseDouble(args.s());
        assertEquals(expected, actual);
    }

    @ParameterizedTest
    @MethodSource
    void testRandomHexForFloat(Args args) {
        float expected = args.decimal().floatValue();
        float actual = FloatingDecimal.parseFloat(args.s());
        assertEquals(expected, actual);
    }

    @ParameterizedTest
    @MethodSource
    void testRandomHexForDouble(Args args) {
       double expected = args.decimal().doubleValue();
       double actual = FloatingDecimal.parseDouble(args.s());
       assertEquals(expected, actual);
    }

    private record Args(String s, BigDecimal decimal) {}

    private static Args randomDec(boolean forDouble) {
        StringBuilder sb = new StringBuilder();
        int leadingWhites = RANDOM.nextInt(4);
        appendRandomWhitespace(sb, leadingWhites);
        int signLen = appendRandomSign(sb);
        int leadingZeros = RANDOM.nextInt(4);
        appendZeros(sb, leadingZeros);
        int digits = RANDOM.nextInt(forDouble ? 24 : 12) + 1;
        appendRandomDecDigits(sb, digits);
        int trailingZeros = RANDOM.nextInt(4);
        appendZeros(sb, trailingZeros);
        BigDecimal bd = new BigDecimal(new BigInteger(
                sb.substring(
                        leadingWhites,
                        leadingWhites + signLen + leadingZeros + digits + trailingZeros),
                10));

        int p = 0;
        if (RANDOM.nextInt(8) != 0) {  // 87.5% chance of point presence
            int pointPos = RANDOM.nextInt(leadingZeros + digits + trailingZeros + 1);
            sb.insert(leadingWhites + signLen + pointPos, '.');
            p = -(leadingZeros + digits + trailingZeros - pointPos);
        }
        int e = 0;
        if (RANDOM.nextInt(4) != 0) {  // 75% chance of explicit exponent
            int emax = forDouble ? 325 : 46;
            e = RANDOM.nextInt(-emax, emax);
            appendExponent(sb, e, true);
        }
        appendRandomSuffix(sb);
        int trailingWhites = RANDOM.nextInt(4);
        appendRandomWhitespace(sb, trailingWhites);
        if (e + p >= 0) {
            bd = bd .multiply(BigDecimal.TEN.pow(e + p));
        } else {
            bd = bd .divide(BigDecimal.TEN.pow(-(e + p)));
        }
        return new Args(sb.toString(), bd);
    }

    private static Args randomHex(boolean forDouble) {
        StringBuilder sb = new StringBuilder();
        int leadingWhites = RANDOM.nextInt(4);
        appendRandomWhitespace(sb, leadingWhites);
        int signLen = appendRandomSign(sb);
        appendHexPrefix(sb);
        int leadingZeros = RANDOM.nextInt(4);
        appendZeros(sb, leadingZeros);
        int digits = RANDOM.nextInt(forDouble ? 24 : 12) + 1;
        appendRandomHexDigits(sb, digits);
        int trailingZeros = RANDOM.nextInt(4);
        appendZeros(sb, trailingZeros);
        BigDecimal bd = new BigDecimal(new BigInteger(  // don't include 0x or 0X
                sb.substring(leadingWhites, leadingWhites + signLen) +
                sb.substring(
                        leadingWhites + signLen + 2,
                        leadingWhites + signLen + 2 + leadingZeros + digits + trailingZeros),
                0x10));

        int p = 0;
        if (RANDOM.nextInt(8) != 0) {  // 87.5% chance of point presence
            int pointPos = RANDOM.nextInt(leadingZeros + digits + trailingZeros + 1);
            sb.insert(leadingWhites + signLen + 2 + pointPos, '.');
            p = -4 * (leadingZeros + digits + trailingZeros - pointPos);
        }
        int emax = forDouble ? 1075 : 150;
        int e = RANDOM.nextInt(-emax, emax);
        appendExponent(sb, e, false);
        appendRandomSuffix(sb);
        int trailingWhites = RANDOM.nextInt(4);
        appendRandomWhitespace(sb, trailingWhites);
        if (e + p >= 0) {
            bd = bd .multiply(BigDecimal.TWO.pow(e + p));
        } else {
            bd = bd .divide(BigDecimal.TWO.pow(-(e + p)));
        }
        return new Args(sb.toString(), bd);
    }

    private static int appendRandomSign(StringBuilder sb) {
        return switch (RANDOM.nextInt(4)) {  // 50% chance of tacit sign
            case 0 -> {
                sb.append('-');
                yield 1;
            }
            case 1 -> {
                sb.append('+');
                yield 1;
            }
            default -> 0;
        };
    }

    private static void appendExponent(StringBuilder sb, int e, boolean forDec) {
        if (forDec) {
            sb.append(RANDOM.nextBoolean() ? 'e' : 'E');
        } else {
            sb.append(RANDOM.nextBoolean() ? 'p' : 'P');
        }
        if (e < 0) {
            sb.append('-');
        } else if (e == 0) {
            appendRandomSign(sb);
        } else if (RANDOM.nextBoolean()) {
            sb.append('+');
        }
        appendZeros(sb, RANDOM.nextInt(2));
        sb.append(Math.abs(e));
    }

    private static void appendRandomSuffix(StringBuilder sb) {
        switch (RANDOM.nextInt(8)) {  // 50% chance of no suffix
            case 0 -> sb.append('D');
            case 1 -> sb.append('F');
            case 2 -> sb.append('d');
            case 3 -> sb.append('f');
        }
    }

    private static void appendHexPrefix(StringBuilder sb) {
        /* Randomize case of x. */
        sb.append('0').append(RANDOM.nextBoolean() ? 'x' : 'X');
    }

    private static void appendZeros(StringBuilder sb, int count) {
        sb.repeat('0', count);
    }

    private static void appendRandomDecDigits(StringBuilder sb, int count) {
        sb.append(randomDecDigit(1));
        for (; count > 1; --count) {
            sb.append(randomDecDigit(0));
        }
    }

    private static void appendRandomHexDigits(StringBuilder sb, int count) {
        sb.append(randomHexDigit(1));
        for (; count > 1; --count) {
            sb.append(randomHexDigit(0));
        }
    }

    private static char randomHexDigit(int min) {
        char c = Character.forDigit(RANDOM.nextInt(min, 0x10), 0x10);
        /* Randomize letter case as well. */
        return RANDOM.nextBoolean() ? Character.toLowerCase(c) : Character.toUpperCase(c);
    }

    private static char randomDecDigit(int min) {
        int c = Character.forDigit(RANDOM.nextInt(min, 10), 10);
        return (char) c;
    }

    private static void appendRandomWhitespace(StringBuilder sb, int count) {
        /* Randomize all whitespace chars. */
        for (; count > 0; --count) {
            sb.append(randomWhitespace());
        }
    }

    private static char randomWhitespace() {
        return (char) (RANDOM.nextInt(0x20 + 1));
    }

}
