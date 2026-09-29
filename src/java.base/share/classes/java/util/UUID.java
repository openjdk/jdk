/*
 * Copyright (c) 2003, 2025, Oracle and/or its affiliates. All rights reserved.
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

package java.util;

import java.security.*;

import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.util.ByteArrayLittleEndian;

/**
 * A class that represents an immutable Universally Unique IDentifier (UUID).
 * A UUID represents a 128-bit value.
 *
 * <p> This class is primarily designed for manipulating Leach-Salz variant UUIDs,
 * but it also supports the creation of UUIDs of other variants.
 *
 * <p> The layout of a variant 2 (Leach-Salz) UUID is as follows:
 *
 * The most significant long consists of the following unsigned fields:
 * <pre>
 * 0xFFFFFFFF00000000 time_low
 * 0x00000000FFFF0000 time_mid
 * 0x000000000000F000 version
 * 0x0000000000000FFF time_hi
 * </pre>
 * The least significant long consists of the following unsigned fields:
 * <pre>
 * 0xC000000000000000 variant
 * 0x3FFF000000000000 clock_seq
 * 0x0000FFFFFFFFFFFF node
 * </pre>
 *
 * <p> The variant field contains a value which identifies the layout of the
 * {@code UUID}.  The bit layout described above is valid only for a {@code
 * UUID} with a variant value of 2, which indicates the Leach-Salz variant.
 *
 * <p> See <a href="https://www.rfc-editor.org/rfc/rfc9562.html">
 * <i>RFC 9562: Universally Unique Identifiers (UUIDs)</i></a> for the complete specification,
 * including the UUID format, layouts, and algorithms for creating {@code UUID}s.
 *
 * <p> There are eight defined types of UUIDs, each identified by a version number:
 * time-based (version 1), DCE security (version 2), name-based with MD5 (version 3),
 * randomly generated (version 4), name-based with SHA-1 (version 5), reordered time-based (version 6),
 * Unix epoch time-based (version 7), and custom-defined layout (version 8).
 *
 * @spec https://www.rfc-editor.org/rfc/rfc9562.html
 *      RFC 9562 Universally Unique IDentifiers (UUIDs)
 * @since   1.5
 */
public final class UUID implements java.io.Serializable, Comparable<UUID> {

    /**
     * Explicit serialVersionUID for interoperability.
     */
    @java.io.Serial
    private static final long serialVersionUID = -4856846361193249489L;

    /**
     * @serial The most significant 64 bits of this UUID.
     */
    private final long mostSigBits;

    /**
     * @serial The least significant 64 bits of this UUID.
     */
    private final long leastSigBits;

    private static final JavaLangAccess jla = SharedSecrets.getJavaLangAccess();

    /*
     * The random number generator used by this class to create random
     * based UUIDs. In a holder class to defer initialization until needed.
     */
    private static class Holder {
        static final SecureRandom numberGenerator = new SecureRandom();
    }

    // Constructors and Factories

    /*
     * Private constructor which uses a byte array to construct the new UUID.
     */
    private UUID(byte[] data) {
        long msb = 0;
        long lsb = 0;
        assert data.length == 16 : "data must be 16 bytes in length";
        for (int i=0; i<8; i++)
            msb = (msb << 8) | (data[i] & 0xff);
        for (int i=8; i<16; i++)
            lsb = (lsb << 8) | (data[i] & 0xff);
        this.mostSigBits = msb;
        this.leastSigBits = lsb;
    }

    /**
     * Constructs a new {@code UUID} using the specified data.  {@code
     * mostSigBits} is used for the most significant 64 bits of the {@code
     * UUID} and {@code leastSigBits} becomes the least significant 64 bits of
     * the {@code UUID}.
     *
     * @param  mostSigBits
     *         The most significant bits of the {@code UUID}
     *
     * @param  leastSigBits
     *         The least significant bits of the {@code UUID}
     */
    public UUID(long mostSigBits, long leastSigBits) {
        this.mostSigBits = mostSigBits;
        this.leastSigBits = leastSigBits;
    }

    /**
     * Static factory to retrieve a type 4 (pseudo randomly generated) UUID.
     *
     * The {@code UUID} is generated using a cryptographically strong pseudo
     * random number generator.
     *
     * @return  A randomly generated {@code UUID}
     */
    public static UUID randomUUID() {
        SecureRandom ng = Holder.numberGenerator;

        byte[] randomBytes = new byte[16];
        ng.nextBytes(randomBytes);
        randomBytes[6]  &= 0x0f;  /* clear version        */
        randomBytes[6]  |= 0x40;  /* set to version 4     */
        randomBytes[8]  &= 0x3f;  /* clear variant        */
        randomBytes[8]  |= (byte) 0x80;  /* set to IETF variant  */
        return new UUID(randomBytes);
    }

    /**
     * Static factory to retrieve a type 3 (name based) {@code UUID} based on
     * the specified byte array.
     *
     * @param  name
     *         A byte array to be used to construct a {@code UUID}
     *
     * @return  A {@code UUID} generated from the specified array
     */
    public static UUID nameUUIDFromBytes(byte[] name) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException nsae) {
            throw new InternalError("MD5 not supported", nsae);
        }
        byte[] md5Bytes = md.digest(name);
        md5Bytes[6]  &= 0x0f;  /* clear version        */
        md5Bytes[6]  |= 0x30;  /* set to version 3     */
        md5Bytes[8]  &= 0x3f;  /* clear variant        */
        md5Bytes[8]  |= (byte) 0x80;  /* set to IETF variant  */
        return new UUID(md5Bytes);
    }

    /**
     * Creates a type 7 UUID (UUIDv7) {@code UUID} from the given Unix Epoch timestamp.
     *
     * The returned {@code UUID} will have the given {@code timestamp} in
     * the first 6 bytes, followed by the version and variant bits representing {@code UUIDv7},
     * and the remaining bytes will contain random data from a cryptographically strong
     * pseudo-random number generator.
     *
     * @apiNote {@code UUIDv7} values are created by allocating a Unix timestamp in milliseconds
     * in the most significant 48 bits, allocating the required version (4 bits) and variant (2-bits)
     * and filling the remaining 74 bits with random bits. As such, this method rejects {@code timestamp}
     * values that do not fit into 48 bits.
     * <p>
     * Monotonicity (each subsequent value being greater than the last) is a primary characteristic
     * of {@code UUIDv7} values. This is due to the {@code timestamp} value being part of the {@code UUID}.
     * Callers of this method that wish to generate monotonic {@code UUIDv7} values are expected to
     * ensure that the given {@code timestamp} value is monotonic.
     *
     *
     * @param timestamp the number of milliseconds since midnight 1 Jan 1970 UTC,
     *                 leap seconds excluded.
     *
     * @return a {@code UUID} constructed using the given {@code timestamp}
     *
     * @throws IllegalArgumentException if the timestamp is negative or greater than {@code (1L << 48) - 1}
     *
     * @since 26
     */
    public static UUID ofEpochMillis(long timestamp) {
        if ((timestamp >> 48) != 0) {
            throw new IllegalArgumentException("Supplied timestamp: " + timestamp + "does not fit within 48 bits");
        }

        SecureRandom ng = Holder.numberGenerator;
        byte[] randomBytes = new byte[16];
        ng.nextBytes(randomBytes);

        // Embed the timestamp into the first 6 bytes
        randomBytes[0] = (byte)(timestamp >>> 40);
        randomBytes[1] = (byte)(timestamp >>> 32);
        randomBytes[2] = (byte)(timestamp >>> 24);
        randomBytes[3] = (byte)(timestamp >>> 16);
        randomBytes[4] = (byte)(timestamp >>> 8);
        randomBytes[5] = (byte)(timestamp);

        // Set version to 7
        randomBytes[6] &= 0x0f;
        randomBytes[6] |= 0x70;

        // Set variant to IETF
        randomBytes[8] &= 0x3f;
        randomBytes[8] |= (byte) 0x80;

        return new UUID(randomBytes);
    }

    private static final byte[] NIBBLES;
    static {
        byte[] ns = new byte[256];
        Arrays.fill(ns, (byte) -1);
        ns['0'] = 0;
        ns['1'] = 1;
        ns['2'] = 2;
        ns['3'] = 3;
        ns['4'] = 4;
        ns['5'] = 5;
        ns['6'] = 6;
        ns['7'] = 7;
        ns['8'] = 8;
        ns['9'] = 9;
        ns['A'] = 10;
        ns['B'] = 11;
        ns['C'] = 12;
        ns['D'] = 13;
        ns['E'] = 14;
        ns['F'] = 15;
        ns['a'] = 10;
        ns['b'] = 11;
        ns['c'] = 12;
        ns['d'] = 13;
        ns['e'] = 14;
        ns['f'] = 15;
        NIBBLES = ns;
    }

    private static long parse4Nibbles(String name, int pos) {
        byte[] ns = NIBBLES;
        char ch1 = name.charAt(pos);
        char ch2 = name.charAt(pos + 1);
        char ch3 = name.charAt(pos + 2);
        char ch4 = name.charAt(pos + 3);
        return (ch1 | ch2 | ch3 | ch4) > 0xff ?
                -1 : ns[ch1] << 12 | ns[ch2] << 8 | ns[ch3] << 4 | ns[ch4];
    }

    /**
     * Creates a {@code UUID} from the string standard representation as
     * described in the {@link #toString} method.
     *
     * @param  name
     *         A string that specifies a {@code UUID}
     *
     * @return  A {@code UUID} with the specified value
     *
     * @throws  IllegalArgumentException
     *          If name does not conform to the string representation as
     *          described in {@link #toString}
     *
     */
    public static UUID fromString(String name) {
        if (name.length() == 36) {
            char ch1 = name.charAt(8);
            char ch2 = name.charAt(13);
            char ch3 = name.charAt(18);
            char ch4 = name.charAt(23);
            if (ch1 == '-' && ch2 == '-' && ch3 == '-' && ch4 == '-') {
                long msb1 = parse4Nibbles(name, 0);
                long msb2 = parse4Nibbles(name, 4);
                long msb3 = parse4Nibbles(name, 9);
                long msb4 = parse4Nibbles(name, 14);
                long lsb1 = parse4Nibbles(name, 19);
                long lsb2 = parse4Nibbles(name, 24);
                long lsb3 = parse4Nibbles(name, 28);
                long lsb4 = parse4Nibbles(name, 32);
                if ((msb1 | msb2 | msb3 | msb4 | lsb1 | lsb2 | lsb3 | lsb4) >= 0) {
                    return new UUID(
                            msb1 << 48 | msb2 << 32 | msb3 << 16 | msb4,
                            lsb1 << 48 | lsb2 << 32 | lsb3 << 16 | lsb4);
                }
            }
        }
        return fromString1(name);
    }

    private static UUID fromString1(String name) {
        int len = name.length();
        if (len > 36) {
            throw new IllegalArgumentException("UUID string too large");
        }

        int dash1 = name.indexOf('-');
        int dash2 = name.indexOf('-', dash1 + 1);
        int dash3 = name.indexOf('-', dash2 + 1);
        int dash4 = name.indexOf('-', dash3 + 1);
        int dash5 = name.indexOf('-', dash4 + 1);

        // For any valid input, dash1 through dash4 will be positive and dash5
        // negative, but it's enough to check dash4 and dash5:
        // - if dash1 is -1, dash4 will be -1
        // - if dash1 is positive but dash2 is -1, dash4 will be -1
        // - if dash1 and dash2 is positive, dash3 will be -1, dash4 will be
        //   positive, but so will dash5
        if (dash4 < 0 || dash5 >= 0) {
            throw new IllegalArgumentException("Invalid UUID string: " + name);
        }

        long mostSigBits = Long.parseLong(name, 0, dash1, 16) & 0xffffffffL;
        mostSigBits <<= 16;
        mostSigBits |= Long.parseLong(name, dash1 + 1, dash2, 16) & 0xffffL;
        mostSigBits <<= 16;
        mostSigBits |= Long.parseLong(name, dash2 + 1, dash3, 16) & 0xffffL;
        long leastSigBits = Long.parseLong(name, dash3 + 1, dash4, 16) & 0xffffL;
        leastSigBits <<= 48;
        leastSigBits |= Long.parseLong(name, dash4 + 1, len, 16) & 0xffffffffffffL;

        return new UUID(mostSigBits, leastSigBits);
    }

    // Field Accessor Methods

    /**
     * Returns the least significant 64 bits of this UUID's 128 bit value.
     *
     * @return  The least significant 64 bits of this UUID's 128 bit value
     */
    public long getLeastSignificantBits() {
        return leastSigBits;
    }

    /**
     * Returns the most significant 64 bits of this UUID's 128 bit value.
     *
     * @return  The most significant 64 bits of this UUID's 128 bit value
     */
    public long getMostSignificantBits() {
        return mostSigBits;
    }

    /**
     * The version number associated with this {@code UUID}.  The version
     * number describes how this {@code UUID} was generated.
     *
     * The version number has the following meaning:
     * <ul>
     * <li>1    Time-based UUID
     * <li>2    DCE security UUID
     * <li>3    Name-based UUID
     * <li>4    Randomly generated UUID
     * <li>7    Unix Epoch time-based UUID
     * </ul>
     *
     * @return  The version number of this {@code UUID}
     */
    public int version() {
        // Version is bits masked by 0x000000000000F000 in MS long
        return (int)((mostSigBits >> 12) & 0x0f);
    }

    /**
     * The variant number associated with this {@code UUID}.  The variant
     * number describes the layout of the {@code UUID}.
     *
     * The variant number has the following meaning:
     * <ul>
     * <li>0    Reserved for NCS backward compatibility
     * <li>2    <a href="https://www.ietf.org/rfc/rfc9562.txt">IETF&nbsp;RFC&nbsp;9562</a>
     * (Leach-Salz), used by this class
     * <li>6    Reserved, Microsoft Corporation backward compatibility
     * <li>7    Reserved for future definition
     * </ul>
     *
     * @return  The variant number of this {@code UUID}
     */
    public int variant() {
        // This field is composed of a varying number of bits.
        // 0    -    -    Reserved for NCS backward compatibility
        // 1    0    -    The IETF aka Leach-Salz variant (used by this class)
        // 1    1    0    Reserved, Microsoft backward compatibility
        // 1    1    1    Reserved for future definition.
        return (int) ((leastSigBits >>> (64 - (leastSigBits >>> 62)))
                      & (leastSigBits >> 63));
    }

    /**
     * The timestamp value associated with this UUID.
     *
     * <p> The 60 bit timestamp value is constructed from the time_low,
     * time_mid, and time_hi fields of this {@code UUID}.  The resulting
     * timestamp is measured in 100-nanosecond units since midnight,
     * October 15, 1582 UTC.
     *
     * <p> The timestamp value is only meaningful in a time-based UUID, which
     * has version type 1.  If this {@code UUID} is not a time-based UUID then
     * this method throws UnsupportedOperationException.
     *
     * @throws UnsupportedOperationException
     *         If this UUID is not a version 1 UUID
     * @return The timestamp of this {@code UUID}.
     */
    public long timestamp() {
        if (version() != 1) {
            throw new UnsupportedOperationException("Not a time-based UUID");
        }

        return (mostSigBits & 0x0FFFL) << 48
             | ((mostSigBits >> 16) & 0x0FFFFL) << 32
             | mostSigBits >>> 32;
    }

    /**
     * The clock sequence value associated with this UUID.
     *
     * <p> The 14 bit clock sequence value is constructed from the clock
     * sequence field of this UUID.  The clock sequence field is used to
     * guarantee temporal uniqueness in a time-based UUID.
     *
     * <p> The {@code clockSequence} value is only meaningful in a time-based
     * UUID, which has version type 1.  If this UUID is not a time-based UUID
     * then this method throws UnsupportedOperationException.
     *
     * @return  The clock sequence of this {@code UUID}
     *
     * @throws  UnsupportedOperationException
     *          If this UUID is not a version 1 UUID
     */
    public int clockSequence() {
        if (version() != 1) {
            throw new UnsupportedOperationException("Not a time-based UUID");
        }

        return (int)((leastSigBits & 0x3FFF000000000000L) >>> 48);
    }

    /**
     * The node value associated with this UUID.
     *
     * <p> The 48 bit node value is constructed from the node field of this
     * UUID.  This field is intended to hold the IEEE 802 address of the machine
     * that generated this UUID to guarantee spatial uniqueness.
     *
     * <p> The node value is only meaningful in a time-based UUID, which has
     * version type 1.  If this UUID is not a time-based UUID then this method
     * throws UnsupportedOperationException.
     *
     * @return  The node value of this {@code UUID}
     *
     * @throws  UnsupportedOperationException
     *          If this UUID is not a version 1 UUID
     */
    public long node() {
        if (version() != 1) {
            throw new UnsupportedOperationException("Not a time-based UUID");
        }

        return leastSigBits & 0x0000FFFFFFFFFFFFL;
    }

    // Object Inherited Methods

    /**
     * Returns a {@code String} object representing this {@code UUID}.
     *
     * <p> The UUID string representation is as described by this BNF:
     * <blockquote><pre>
     * {@code
     * UUID                   = <time_low> "-" <time_mid> "-"
     *                          <time_high_and_version> "-"
     *                          <variant_and_sequence> "-"
     *                          <node>
     * time_low               = 4*<hexOctet>
     * time_mid               = 2*<hexOctet>
     * time_high_and_version  = 2*<hexOctet>
     * variant_and_sequence   = 2*<hexOctet>
     * node                   = 6*<hexOctet>
     * hexOctet               = <hexDigit><hexDigit>
     * hexDigit               =
     *       "0" | "1" | "2" | "3" | "4" | "5" | "6" | "7" | "8" | "9"
     *       | "a" | "b" | "c" | "d" | "e" | "f"
     *       | "A" | "B" | "C" | "D" | "E" | "F"
     * }</pre></blockquote>
     *
     * @return  A string representation of this {@code UUID}
     */
    @Override
    public String toString() {
        byte[] buf = new byte[36];
        buf[8] = '-';
        buf[13] = '-';
        buf[18] = '-';
        buf[23] = '-';

        // Although the UUID byte ordering is defined to be big-endian, ByteArrayLittleEndian is used here to optimize
        // for the most common architectures. hex8 reverses the order internally.
        ByteArrayLittleEndian.setLong(buf, 0, hex8(mostSigBits >>> 32));
        long x0 = hex8(mostSigBits);
        ByteArrayLittleEndian.setInt(buf, 9, (int) x0);
        ByteArrayLittleEndian.setInt(buf, 14, (int) (x0 >>> 32));

        long x1 = hex8(leastSigBits >>> 32);
        ByteArrayLittleEndian.setInt(buf, 19, (int) (x1));
        ByteArrayLittleEndian.setInt(buf, 24, (int) (x1 >>> 32));
        ByteArrayLittleEndian.setLong(buf, 28, hex8(leastSigBits));

        return jla.uncheckedNewStringWithLatin1Bytes(buf);
    }

    /**
     * Efficiently converts 8 hexadecimal digits to their ASCII representation using SIMD-style vector operations.
     * This method processes multiple digits in parallel by treating a long value as eight 8-bit lanes,
     * achieving significantly better performance compared to traditional loop-based conversion.
     *
     * <p>The conversion algorithm works as follows:
     * <pre>
     * 1. Input expansion: Each 4-bit hex digit is expanded to 8 bits
     * 2. Vector processing:
     *    - Add 6 to each digit: triggers carry flag for a-f digits
     *    - Mask with 0x10 pattern to isolate carry flags
     *    - Calculate ASCII adjustment: (carry << 1) + (carry >> 1) - (carry >> 4)
     *    - Add ASCII '0' base (0x30) and original value
     * 3. Byte order adjustment for final output
     * </pre>
     *
     * <p>Performance characteristics:
     * <ul>
     *   <li>Processes 8 digits in parallel using vector operations
     *   <li>Avoids branching and loops completely
     *   <li>Uses only integer arithmetic and bit operations
     *   <li>Constant time execution regardless of input values
     * </ul>
     *
     * <p>ASCII conversion mapping:
     * <ul>
     *   <li>Digits 0-9 → ASCII '0'-'9' (0x30-0x39)
     *   <li>Digits a-f → ASCII 'a'-'f' (0x61-0x66)
     * </ul>
     *
     * @param input A long containing 8 hex digits (each digit must be 0-15)
     * @return A long containing 8 ASCII bytes representing the hex digits
     *
     * @implNote The implementation leverages CPU vector processing capabilities through
     *           long integer operations. The algorithm is based on the observation that
     *           ASCII hex digits have a specific pattern that can be computed efficiently
     *           using carry flag manipulation.
     *
     * @example
     * <pre>
     * Input:  0xABCDEF01
     * Output: 3130666564636261 ('1','0','f','e','d','c','b','a' in ASCII)
     * </pre>
     *
     * @see Long#reverseBytes(long)
     */
    private static long hex8(long i) {
        // Expand each 4-bit group into 8 bits, spreading them out in the long value: 0xAABBCCDD -> 0xA0A0B0B0C0C0D0D
        i = Long.expand(i, 0x0F0F_0F0F_0F0F_0F0FL);

        /*
         * This method efficiently converts 8 hexadecimal digits simultaneously using vector operations
         * The algorithm works as follows:
         *
         * For input values 0-15:
         * - For digits 0-9: converts to ASCII '0'-'9' (0x30-0x39)
         * - For digits 10-15: converts to ASCII 'a'-'f' (0x61-0x66)
         *
         * The conversion process:
         * 1. Add 6 to each 4-bit group: i + 0x0606_0606_0606_0606L
         * 2. Mask to get the adjustment flags: & 0x1010_1010_1010_1010L
         * 3. Calculate the offset: (m << 1) + (m >> 1) - (m >> 4)
         *    - For 0-9: offset = 0
         *    - For a-f: offset = 39 (to bridge the gap between '9' and 'a' in ASCII)
         * 4. Add ASCII '0' base (0x30) and the original value
         * 5. Reverse byte order for correct positioning
         */
        long m = (i + 0x0606_0606_0606_0606L) & 0x1010_1010_1010_1010L;

        // Calculate final ASCII values and reverse bytes for proper ordering
        return Long.reverseBytes(
                ((m << 1) + (m >> 1) - (m >> 4))
                + 0x3030_3030_3030_3030L // Add ASCII '0' base to all digits
                + i                      // Add original values
        );
    }

    /**
     * Returns a hash code for this {@code UUID}.
     *
     * @return  A hash code value for this {@code UUID}
     */
    @Override
    public int hashCode() {
        return Long.hashCode(mostSigBits ^ leastSigBits);
    }

    /**
     * Compares this object to the specified object.  The result is {@code
     * true} if and only if the argument is not {@code null}, is a {@code UUID}
     * object, has the same variant, and contains the same value, bit for bit,
     * as this {@code UUID}.
     *
     * @param  obj
     *         The object to be compared
     *
     * @return  {@code true} if the objects are the same; {@code false}
     *          otherwise
     */
    @Override
    public boolean equals(Object obj) {
        if ((null == obj) || (obj.getClass() != UUID.class))
            return false;
        UUID id = (UUID)obj;
        return (mostSigBits == id.mostSigBits &&
                leastSigBits == id.leastSigBits);
    }

    // Comparison Operations

    /**
     * Compares this UUID with the specified UUID.
     *
     * <p> The first of two UUIDs is greater than the second if the most
     * significant field in which the UUIDs differ is greater for the first
     * UUID.
     *
     * @param  val
     *         {@code UUID} to which this {@code UUID} is to be compared
     *
     * @return  -1, 0 or 1 as this {@code UUID} is less than, equal to, or
     *          greater than {@code val}
     *
     */
    @Override
    public int compareTo(UUID val) {
        // The ordering is intentionally set up so that the UUIDs
        // can simply be numerically compared as two numbers
        int mostSigBits = Long.compare(this.mostSigBits, val.mostSigBits);
        return mostSigBits != 0 ? mostSigBits : Long.compare(this.leastSigBits, val.leastSigBits);
    }
}
