/*
 * Copyright (c) 2003, 2020, Oracle and/or its affiliates. All rights reserved.
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

/**
 * A class that represents an immutable universally unique identifier (UUID).
 * A UUID represents a 128-bit value.
 *
 * <p> There exist different variants of these global identifiers.  The methods
 * of this class are for manipulating the Leach-Salz variant, although the
 * constructors allow the creation of any variant of UUID (described below).
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
 * <p> The version field holds a value that describes the type of this {@code
 * UUID}.  There are four different basic types of UUIDs: time-based, DCE
 * security, name-based, and randomly generated UUIDs.  These types have a
 * version value of 1, 2, 3 and 4, respectively.
 *
 * <p> For more information including algorithms used to create {@code UUID}s,
 * see <a href="http://www.ietf.org/rfc/rfc4122.txt"> <i>RFC&nbsp;4122: A
 * Universally Unique IDentifier (UUID) URN Namespace</i></a>, section 4.2
 * &quot;Algorithms for Creating a Time-Based UUID&quot;.
 *
 * @since   1.5
 */
public final class UUID implements java.io.Serializable, Comparable<UUID> {

    /**
     * Explicit serialVersionUID for interoperability.
     */
    @java.io.Serial
    private static final long serialVersionUID = -4856846361193249489L;

    /*
     * The most significant 64 bits of this UUID.
     *
     * @serial
     */
    private final long mostSigBits;

    /*
     * The least significant 64 bits of this UUID.
     *
     * @serial
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

    /**
     * Constructs a new {@code UUID} from the given {@code bytes}.
     *
     * <p>The {@link #version()} is set to the given {@code version} and the
     * {@link #variant()} is <strong>always</strong> set to RFC&nbsp;4122.
     *
     * @implNote
     * This constructor is <em>private</em> because it performs no validation
     * on {@code bytes} or {@code version} to be extensible for future needs of
     * the {@code UUID} implementation. {@code bytes} is expected to contain at
     * least 16 bytes and any additional data will silently be ignored, this is
     * for example required during the creation of version 5 UUIDs where the
     * SHA-1 digest is 20 bytes long and truncated.
     *
     * @param  bytes
     *         The bytes of this {@code UUID}
     *
     * @param  version
     *         The {@linkplain #version() version} of this {@code UUID}
     *
     * @since  16
     */
    private UUID(final byte[] bytes, final int version) {
        long msb = (long) (bytes[0]  & 0xFF) << 56;
        msb     |= (long) (bytes[1]  & 0xFF) << 48;
        msb     |= (long) (bytes[2]  & 0xFF) << 40;
        msb     |= (long) (bytes[3]  & 0xFF) << 32;
        msb     |= (long) (bytes[4]  & 0xFF) << 24;
        msb     |= (long) (bytes[5]  & 0xFF) << 16;
        msb     |= (long) (bytes[6]  & 0x0F | version) << 8;
        msb     |= (long)  bytes[7]  & 0xFF;
        this.mostSigBits = msb;

        long lsb = (long) (bytes[8]  & 0x3F | 0x80) << 56;
        lsb     |= (long) (bytes[9]  & 0xFF) << 48;
        lsb     |= (long) (bytes[10] & 0xFF) << 40;
        lsb     |= (long) (bytes[11] & 0xFF) << 32;
        lsb     |= (long) (bytes[12] & 0xFF) << 24;
        lsb     |= (long) (bytes[13] & 0xFF) << 16;
        lsb     |= (long) (bytes[14] & 0xFF) << 8;
        lsb     |= (long)  bytes[15] & 0xFF;
        this.leastSigBits = lsb;
    }

    /**
     * Constructs a new {@code UUID} from the given 64-bit big-endian integers.
     *
     * <p>{@code mostSigBits} is used for the most significant 64 bits of the
     * {@code UUID} and {@code leastSigBits} becomes the least significant 64
     * bits of the {@code UUID}.
     *
     * <p>It is impossible to verify if the given data is a <em>valid</em> UUID
     * according to RFC&nbsp;4122 or just two random numbers. Consequently no
     * attempt is made to validate the data in any way and this constructor can
     * be used to construct arbitrary {@code UUID} instances.
     *
     * @param  mostSigBits
     *         The most significant 64 bits of the {@code UUID}
     *
     * @param  leastSigBits
     *         The least significant 64 bits of the {@code UUID}
     */
    public UUID(final long mostSigBits, final long leastSigBits) {
        this.mostSigBits = mostSigBits;
        this.leastSigBits = leastSigBits;
    }

    /**
     * Constructs a new {@code UUID} from the given big-endian {@code bytes}.
     *
     * <p>The first 8 bytes are used for the most significant 64 bits of the
     * {@code UUID} and the remaining 8 bytes are used for the least significant
     * 64 bits of the {@code UUID}.
     *
     * <p>It is impossible to verify if the given data is a <em>valid</em> UUID
     * according to RFC&nbsp;4122 or just 16 random bytes. Consequently no
     * attempt is made to validate the data in any way and this static factory
     * can be used to construct arbitrary {@code UUID} instances.
     *
     * @param  bytes
     *         The bytes of the {@code UUID}
     *
     * @return {@code UUID} constructed from the given {@code bytes}
     *
     * @throws IllegalArgumentException
     *         If {@code bytes} is not exactly of length 16
     *
     * @throws NullPointerException
     *         If {@code bytes} is {@code null}
     *
     * @since  16
     */
    public static UUID valueOf(final byte[] bytes) {
        if (bytes == null) {
            throw new NullPointerException("bytes must not be null");
        }

        if (bytes.length != 16) {
            throw new IllegalArgumentException("UUID bytes must be exactly of length 16, got: " + bytes.length);
        }

        int i = 0;
        long msb = 0;
        long lsb = 0;
        for (; i <  8; ++i) msb = (msb << 8) | (bytes[i] & 0xFF);
        for (; i < 16; ++i) lsb = (lsb << 8) | (bytes[i] & 0xFF);
        return new UUID(msb, lsb);
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
        final byte[] bytes = new byte[16];
        Holder.numberGenerator.nextBytes(bytes);
        return new UUID(bytes, 0x40);
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
        final MessageDigest md;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException nsae) {
            throw new InternalError("MD5 not supported", nsae);
        }
        return new UUID(md.digest(name), 0x30);
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

        int dash1 = name.indexOf('-', 0);
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
     * <li>2    <a href="http://www.ietf.org/rfc/rfc4122.txt">IETF&nbsp;RFC&nbsp;4122</a>
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

    /**
     * Gets the bytes of this {@code UUID}.
     *
     * <p>The layout of the {@code UUID} bytes is defined in
     * <a href="https://tools.ietf.org/html/rfc4122#section-4.1.2">RFC&nbsp;4122
     * Section 4.1.2</a>. The meaning of the individual bytes of this
     * {@code UUID} may or may not conform to the specification depending on how
     * it was initially generated.
     *
     * @return The bytes of this {@code UUID}.
     *
     * @since  16
     */
    public byte[] getBytes() {
        byte[] bytes = new byte[16];
        bytes[0]  = (byte) (mostSigBits >>> 56);
        bytes[1]  = (byte) (mostSigBits >>> 48);
        bytes[2]  = (byte) (mostSigBits >>> 40);
        bytes[3]  = (byte) (mostSigBits >>> 32);
        bytes[4]  = (byte) (mostSigBits >>> 24);
        bytes[5]  = (byte) (mostSigBits >>> 16);
        bytes[6]  = (byte) (mostSigBits >>> 8);
        bytes[7]  = (byte) (mostSigBits);
        bytes[8]  = (byte) (leastSigBits >>> 56);
        bytes[9]  = (byte) (leastSigBits >>> 48);
        bytes[10] = (byte) (leastSigBits >>> 40);
        bytes[11] = (byte) (leastSigBits >>> 32);
        bytes[12] = (byte) (leastSigBits >>> 24);
        bytes[13] = (byte) (leastSigBits >>> 16);
        bytes[14] = (byte) (leastSigBits >>> 8);
        bytes[15] = (byte) (leastSigBits);
        return bytes;
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
    public String toString() {
        return jla.fastUUID(leastSigBits, mostSigBits);
    }

    /**
     * Returns a hash code for this {@code UUID}.
     *
     * @return  A hash code value for this {@code UUID}
     */
    public int hashCode() {
        long hilo = mostSigBits ^ leastSigBits;
        return ((int)(hilo >> 32)) ^ (int) hilo;
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
    public int compareTo(UUID val) {
        // The ordering is intentionally set up so that the UUIDs
        // can simply be numerically compared as two numbers
        return (this.mostSigBits < val.mostSigBits ? -1 :
                (this.mostSigBits > val.mostSigBits ? 1 :
                 (this.leastSigBits < val.leastSigBits ? -1 :
                  (this.leastSigBits > val.leastSigBits ? 1 :
                   0))));
    }
}
