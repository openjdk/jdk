/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
 * @test @summary Ensure that Murmur3 hash performs according to specification.
 * @compile -XDignore.symbol.file Hashing.java
 */
public class Hashing {

    static final byte ONE_BYTE[] = {
        (byte) 0x80};
    static final byte TWO_BYTE[] = {
        (byte) 0x80, (byte) 0x81};
    static final char ONE_CHAR[] = {
        (char) 0x8180};
    static final byte THREE_BYTE[] = {
        (byte) 0x80, (byte) 0x81, (byte) 0x82};
    static final byte FOUR_BYTE[] = {
        (byte) 0x80, (byte) 0x81, (byte) 0x82, (byte) 0x83};
    static final char TWO_CHAR[] = {
        (char) 0x8180, (char) 0x8382};
    static final int ONE_INT[] = {
        0x83828180};
    static final byte SIX_BYTE[] = {
        (byte) 0x80, (byte) 0x81, (byte) 0x82,
        (byte) 0x83, (byte) 0x84, (byte) 0x85};
    static final char THREE_CHAR[] = {
        (char) 0x8180, (char) 0x8382, (char) 0x8584};
    static final byte EIGHT_BYTE[] = {
        (byte) 0x80, (byte) 0x81, (byte) 0x82,
        (byte) 0x83, (byte) 0x84, (byte) 0x85,
        (byte) 0x86, (byte) 0x87};
    static final char FOUR_CHAR[] = {
        (char) 0x8180, (char) 0x8382,
        (char) 0x8584, (char) 0x8786};
    static final int TWO_INT[] = {
        0x83828180, 0x87868584};
    // per  http://code.google.com/p/smhasher/source/browse/trunk/main.cpp, line:72
    static final int MURMUR3_32_X86_CHECK_VALUE = 0xB0F57EE3;

    public static void testMurmur3_32_ByteArray() {
        System.out.println("testMurmur3_32_ByteArray");

        byte[] vector = new byte[256];
        byte[] hashes = new byte[4 * 256];

        for (int i = 0; i < 256; i++) {
            vector[i] = (byte) i;
        }

        // Hash subranges {}, {0}, {0,1}, {0,1,2}, ..., {0,...,255}
        for (int i = 0; i < 256; i++) {
            int hash = sun.misc.Hashing.murmur3_32(256 - i, vector, 0, i);

            hashes[i * 4] = (byte) hash;
            hashes[i * 4 + 1] = (byte) (hash >>> 8);
            hashes[i * 4 + 2] = (byte) (hash >>> 16);
            hashes[i * 4 + 3] = (byte) (hash >>> 24);
        }

        // hash to get final result.
        int final_hash = sun.misc.Hashing.murmur3_32(0, hashes);

        if (MURMUR3_32_X86_CHECK_VALUE != final_hash) {
            throw new RuntimeException(
                    String.format("Calculated hash result not as expected. Expected %08X got %08X",
                    MURMUR3_32_X86_CHECK_VALUE,
                    final_hash));
        }
    }

    public static void testEquivalentHashes() {
        int bytes, chars, ints;

        System.out.println("testEquivalentHashes");

        bytes = sun.misc.Hashing.murmur3_32(TWO_BYTE);
        chars = sun.misc.Hashing.murmur3_32(ONE_CHAR);
        if (bytes != chars) {
            throw new RuntimeException(String.format("Hashes did not match. b:%08x != c:%08x", bytes, chars));
        }

        bytes = sun.misc.Hashing.murmur3_32(FOUR_BYTE);
        chars = sun.misc.Hashing.murmur3_32(TWO_CHAR);
        ints = sun.misc.Hashing.murmur3_32(ONE_INT);
        if ((bytes != chars) || (bytes != ints)) {
            throw new RuntimeException(String.format("Hashes did not match. b:%08x != c:%08x != i:%08x", bytes, chars, ints));
        }
        bytes = sun.misc.Hashing.murmur3_32(SIX_BYTE);
        chars = sun.misc.Hashing.murmur3_32(THREE_CHAR);
        if (bytes != chars) {
            throw new RuntimeException(String.format("Hashes did not match. b:%08x != c:%08x", bytes, chars));
        }

        bytes = sun.misc.Hashing.murmur3_32(EIGHT_BYTE);
        chars = sun.misc.Hashing.murmur3_32(FOUR_CHAR);
        ints = sun.misc.Hashing.murmur3_32(TWO_INT);
        if ((bytes != chars) || (bytes != ints)) {
            throw new RuntimeException(String.format("Hashes did not match. b:%08x != c:%08x != i:%08x", bytes, chars, ints));
        }
    }

    public static void main(String[] args) {
        testMurmur3_32_ByteArray();
        testEquivalentHashes();
    }
}
