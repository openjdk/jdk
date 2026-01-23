/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.util;

import java.security.*;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.BiFunction;

/**
 * The content of an ML-KEM or ML-DSA private key is defined as a CHOICE
 * among three different representations. For example:
 * <pre>
 *  ML-KEM-1024-PrivateKey ::= CHOICE {
 *       seed [0] OCTET STRING (SIZE (64)),
 *       expandedKey OCTET STRING (SIZE (3168)),
 *       both SEQUENCE {
 *           seed OCTET STRING (SIZE (64)),
 *           expandedKey OCTET STRING (SIZE (3168))
 *           }
 *       }
 * </pre>
 * This class supports reading, writing, and converting between them.
 * <p>
 * Current code follows draft-ietf-lamps-kyber-certificates-11 and RFC 9881.
 */
public final class KeyChoices {

    public enum Type { SEED, EXPANDED_KEY, BOTH }

    private record Choice(Type type, byte[] seed, byte[] expanded) {}

    /**
     * Gets the preferred choice type for an algorithm, defined as an
     * overridable security property "jdk.<name>.pkcs8.encoding".
     *
     * @param name "mlkem" or "mldsa".
     * @throws IllegalArgumentException if property is invalid value
     * @return the type
     */
    public static Type getPreferred(String name) {
        var prop = SecurityProperties.getOverridableProperty(
                "jdk." + name + ".pkcs8.encoding");
        if (prop == null) {
            return Type.SEED;
        }
        return switch (prop.toLowerCase(Locale.ROOT)) {
            case "seed" -> Type.SEED;
            case "expandedkey" -> Type.EXPANDED_KEY;
            case "both" -> Type.BOTH;
            default -> throw new IllegalArgumentException("Unknown format: " + prop);
        };
    }

    /**
     * Writes one of the ML-KEM or ML-DSA private key formats.
     * <p>
     * This method does not check the length of the inputs or whether
     * they match each other. The caller must make sure `seed` and/or
     * `expanded` are provided if `type` requires any of them.
     *
     * @param type     preferred output choice type
     * @param seed     the seed, could be null
     * @param expanded the expanded key, could be null
     * @return         one of the choices
     */
    public static byte[] writeToChoice(Type type, byte[] seed, byte[] expanded) {
        byte[] skOctets;
        // Ensures using one-byte len in DER
        assert seed == null || seed.length < 128;
        // Ensures using two-byte len in DER
        assert expanded == null || expanded.length > 256 && expanded.length < 60000;

        return switch (type) {
            case SEED -> {
                assert seed != null;
                skOctets = new byte[seed.length + 2];
                skOctets[0] = (byte)0x80;
                skOctets[1] = (byte) seed.length;
                System.arraycopy(seed, 0, skOctets, 2, seed.length);
                yield skOctets;
            }
            case EXPANDED_KEY -> {
                assert expanded != null;
                skOctets = new byte[expanded.length + 4];
                skOctets[0] = 0x04;
                writeShortLength(skOctets, 1, expanded.length);
                System.arraycopy(expanded, 0, skOctets, 4, expanded.length);
                yield skOctets;
            }
            case BOTH -> {
                assert seed != null;
                assert expanded != null;
                skOctets = new byte[10 + seed.length + expanded.length];
                skOctets[0] = 0x30;
                writeShortLength(skOctets, 1, 6 + seed.length + expanded.length);
                skOctets[4] = 0x04;
                skOctets[5] = (byte)seed.length;
                System.arraycopy(seed, 0, skOctets, 6, seed.length);
                skOctets[6 + seed.length] = 0x04;
                writeShortLength(skOctets, 7 + seed.length, expanded.length);
                System.arraycopy(expanded, 0, skOctets, 10 + seed.length, expanded.length);
                yield skOctets;
            }
        };
    }

    /**
     * Gets the type of input.
     *
     * @param input input bytes
     * @return the type
     * @throws InvalidKeyException if input is invalid
     */
    public static Type typeOfChoice(byte[] input) throws InvalidKeyException {
        if (input.length < 1) {
            throw new InvalidKeyException("Empty key");
        }
        return switch (input[0]) {
            case (byte) 0x80 -> Type.SEED;
            case 0x04 -> Type.EXPANDED_KEY;
            case 0x30 -> Type.BOTH;
            default -> throw new InvalidKeyException("Wrong tag: " + input[0]);
        };
    }

    /**
     * Splits one of the ML-KEM or ML-DSA private key formats into
     * seed and expandedKey, if exists.
     *
     * @param seedLen correct seed length
     * @param input input bytes
     * @return a {@code Choice} object. Byte arrays inside are newly allocated
     * @throws InvalidKeyException if input is invalid
     */
    private static Choice readFromChoice(int seedLen, byte[] input)
            throws InvalidKeyException {
        if (input.length < seedLen + 2) {
            throw new InvalidKeyException("Too short");
        }
        return switch (input[0]) {
            case (byte) 0x80 -> {
                // 80 SEED_LEN <SEED_LEN of seed>
                if (input[1] != seedLen && input.length != seedLen + 2) {
                    throw new InvalidKeyException("Invalid seed");
                }
                yield new Choice(Type.SEED,
                        Arrays.copyOfRange(input, 2, seedLen + 2), null);
            }
            case 0x04 -> {
                // 04 82 nn nn <nn of expandedKey>
                if (readShortLength(input, 1) != input.length - 4) {
                    throw new InvalidKeyException("Invalid expandedKey");
                }
                yield new Choice(Type.EXPANDED_KEY,
                        null, Arrays.copyOfRange(input, 4, input.length));
            }
            case 0x30 -> {
                // 30 82 mm mm 04 SEED_LEN <SEED_LEN of seed> 04 82 nn nn <nn of expandedKey>
                if (input.length < 6 + seedLen + 4) {
                    throw new InvalidKeyException("Too short");
                }
                if (readShortLength(input, 1) != input.length - 4
                        || input[4] != 0x04
                        || input[5] != (byte)seedLen
                        || input[seedLen + 6] != 0x04
                        || readShortLength(input, seedLen + 7)
                                != input.length - 10 - seedLen) {
                    throw new InvalidKeyException("Invalid both");
                }
                yield new Choice(Type.BOTH,
                        Arrays.copyOfRange(input, 6, 6 + seedLen),
                        Arrays.copyOfRange(input, seedLen + 10, input.length));
            }
            default -> throw new InvalidKeyException("Wrong tag: " + input[0]);
        };
    }

    /**
     * Reads from any encoding and write to the specified type.
     *
     * @param type preferred output choice type
     * @param pname parameter set name
     * @param seedLen seed length
     * @param input the input encoding
     * @param expander function to calculate expanded from seed, could be null
     *                 if there is already expanded in input
     * @return the preferred encoding
     * @throws InvalidKeyException if input is invalid or does not have enough
     *                             information to generate the output
     */
    public static byte[] choiceToChoice(Type type, String pname,
            int seedLen, byte[] input,
            BiFunction<String, byte[], byte[]> expander)
            throws InvalidKeyException {
        var choice = readFromChoice(seedLen, input);
        try {
            if (type != Type.EXPANDED_KEY && choice.type == Type.EXPANDED_KEY) {
                throw new InvalidKeyException(
                        "key contains not enough info to translate");
            }
            var expanded = (choice.expanded == null && type != Type.SEED)
                    ? expander.apply(pname, choice.seed)
                    : choice.expanded;
            return writeToChoice(type, choice.seed, expanded);
        } finally {
            if (choice.seed != null) {
                Arrays.fill(choice.seed, (byte) 0);
            }
            if (choice.expanded != null) {
                Arrays.fill(choice.expanded, (byte) 0);
            }
        }
    }

    /**
     * Reads from any choice of encoding and return the expanded format.
     *
     * @param pname parameter set name
     * @param seedLen seed length
     * @param input input encoding
     * @param expander function to calculate expanded from seed, could be null
     *                 if there is already expanded in input
     * @return the expanded key
     * @throws InvalidKeyException if input is invalid
     */
    public static byte[] choiceToExpanded(String pname,
            int seedLen, byte[] input,
            BiFunction<String, byte[], byte[]> expander)
            throws InvalidKeyException {
        var choice = readFromChoice(seedLen, input);
        if (choice.type == Type.BOTH) {
            var calculated = expander.apply(pname, choice.seed);
            if (!Arrays.equals(choice.expanded, calculated)) {
                throw new InvalidKeyException("seed and expandedKey do not match");
            }
            Arrays.fill(calculated, (byte)0);
        }
        try {
            if (choice.expanded != null) {
                return choice.expanded;
            }
            return expander.apply(pname, choice.seed);
        } finally {
            if (choice.seed != null) {
                Arrays.fill(choice.seed, (byte)0);
            }
        }
    }

    // Reads a 2 bytes length from DER encoding
    private static int readShortLength(byte[] input, int from)
            throws InvalidKeyException {
        if (input[from] != (byte)0x82) {
            throw new InvalidKeyException("Unexpected length");
        }
        return ((input[from + 1] & 0xff) << 8) + (input[from + 2] & 0xff);
    }

    // Writes a 2 bytes length to DER encoding
    private static void writeShortLength(byte[] input, int from, int value) {
        input[from] = (byte)0x82;
        input[from + 1] = (byte) (value >> 8);
        input[from + 2] = (byte) (value);
    }
}
