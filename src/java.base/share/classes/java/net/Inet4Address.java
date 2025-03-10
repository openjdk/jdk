/*
 * Copyright (c) 2000, 2024, Oracle and/or its affiliates. All rights reserved.
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

package java.net;

import sun.net.util.IPAddressUtil;

import java.io.ObjectStreamException;
import java.util.Objects;

/**
 * This class represents an Internet Protocol version 4 (IPv4) address.
 * Defined by <a href="http://www.ietf.org/rfc/rfc790.txt">
 * <i>RFC&nbsp;790: Assigned Numbers</i></a>,
 * <a href="http://www.ietf.org/rfc/rfc1918.txt">
 * <i>RFC&nbsp;1918: Address Allocation for Private Internets</i></a>,
 * and <a href="http://www.ietf.org/rfc/rfc2365.txt"><i>RFC&nbsp;2365:
 * Administratively Scoped IP Multicast</i></a>
 *
 * <h2> <a id="format">Textual representation of IPv4 addresses</a> </h2>
 *
 * Textual representation of IPv4 address used as input to methods
 * takes one of the following forms:
 *
 * <blockquote><ul style="list-style-type:none">
 * <li>{@code d.d.d.d}</li>
 * <li>{@code d.d.d}</li>
 * <li>{@code d.d}</li>
 * <li>{@code d}</li>
 * </ul></blockquote>
 *
 * <p> When four parts are specified, each is interpreted as a byte of
 * data and assigned, from left to right, to the four bytes of an IPv4
 * address.
 *
 * <p> When a three part address is specified, the last part is
 * interpreted as a 16-bit quantity and placed in the right most two
 * bytes of the network address. This makes the three part address
 * format convenient for specifying Class B network addresses as
 * 128.net.host.
 *
 * <p> When a two part address is supplied, the last part is
 * interpreted as a 24-bit quantity and placed in the right most three
 * bytes of the network address. This makes the two part address
 * format convenient for specifying Class A network addresses as
 * net.host.
 *
 * <p> When only one part is given, the value is stored directly in
 * the network address without any byte rearrangement.
 *
 * <p> For example, the following (decimal) forms are supported by the methods
 * {@link Inet4Address#ofLiteral(String)} and {@link InetAddress#getByName(String)}
 * which are capable of parsing textual representations of IPv4 addresses:
 * {@snippet :
 *  // Dotted-decimal 'd.d.d.d' form with four part address literal
 *  InetAddress.getByName("007.008.009.010"); // ==> /7.8.9.10
 *  InetAddress.getByName("127.0.1.1");       // ==> /127.0.1.1
 *
 *  // Dotted-decimal 'd.d.d' form with three part address literal,
 *  // the last part is placed in the right most two bytes
 *  // of the constructed address
 *  InetAddress.getByName("127.0.257"); // ==> /127.0.1.1
 *
 *  // Dotted-decimal 'd.d' form with two part address literal,
 *  // the last part is placed in the right most three bytes
 *  // of the constructed address
 *  Inet4Address.ofLiteral("127.257"); // ==> /127.0.1.1
 *
 *  // 'd' form with one decimal value that is stored directly in
 *  // the constructed address bytes without any rearrangement
 *  Inet4Address.ofLiteral("02130706689"); // ==> /127.0.1.1
 * }
 *
 * <p> The above forms adhere to "strict" decimal-only syntax.
 * Additionally, the {@link Inet4Address#ofPosixLiteral(String)}
 * method implements a POSIX {@code inet_addr} compatible "loose"
 * parsing algorithm, allowing octal and hexadecimal address segments.
 * Please refer to <a href="https://www.ietf.org/rfc/rfc6943.html#section-3.1.1">
 * <i>RFC&nbsp;6943: Issues in Identifier Comparison for Security
 * Purposes</i></a>. Aside from {@code Inet4Address.ofPosixLiteral(String)}, all methods only
 * support strict decimal parsing.
 * <p> For methods that return a textual representation as output
 * value, the first form, i.e. a dotted-quad string in strict decimal notation, is used.
 *
 * <h3> The Scope of a Multicast Address </h3>
 *
 * Historically the IPv4 TTL field in the IP header has doubled as a
 * multicast scope field: a TTL of 0 means node-local, 1 means
 * link-local, up through 32 means site-local, up through 64 means
 * region-local, up through 128 means continent-local, and up through
 * 255 are global. However, the administrative scoping is preferred.
 * Please refer to <a href="http://www.ietf.org/rfc/rfc2365.txt">
 * <i>RFC&nbsp;2365: Administratively Scoped IP Multicast</i></a>
 *
 * @spec https://www.rfc-editor.org/info/rfc1918
 *      RFC 1918: Address Allocation for Private Internets
 * @spec https://www.rfc-editor.org/info/rfc2365
 *      RFC 2365: Administratively Scoped IP Multicast
 * @spec https://www.rfc-editor.org/info/rfc790
 *      RFC 790: Assigned numbers
 * @spec https://www.rfc-editor.org/rfc/rfc6943.html#section-3.1.1
 *      RFC 6943: Issues in Identifier Comparison for Security Purposes
 * @since 1.4
 */

public final
class Inet4Address extends InetAddress {
    static final int INADDRSZ = 4;

    /** use serialVersionUID from InetAddress, but Inet4Address instance
     *  is always replaced by an InetAddress instance before being
     *  serialized */
    @java.io.Serial
    private static final long serialVersionUID = 3286316764910316507L;

    /*
     * Perform initializations.
     */
    static {
        init();
    }

    Inet4Address() {
        super();
        holder().hostName = null;
        holder().address = 0;
        holder().family = IPv4;
    }

    Inet4Address(String hostName, byte[] addr) {
        holder().hostName = hostName;
        holder().family = IPv4;
        if (addr != null) {
            if (addr.length == INADDRSZ) {
                int address  = addr[3] & 0xFF;
                address |= ((addr[2] << 8) & 0xFF00);
                address |= ((addr[1] << 16) & 0xFF0000);
                address |= ((addr[0] << 24) & 0xFF000000);
                holder().address = address;
            }
        }
        holder().originalHostName = hostName;
    }
    Inet4Address(String hostName, int address) {
        holder().hostName = hostName;
        holder().family = IPv4;
        holder().address = address;
        holder().originalHostName = hostName;
    }

    /**
     * Creates an {@code Inet4Address} based on the provided {@linkplain
     * Inet4Address##format textual representation} of an IPv4 address.
     * <p> If the provided IPv4 address literal cannot represent a {@linkplain
     * Inet4Address##format valid IPv4 address} an {@code IllegalArgumentException} is thrown.
     * <p> This method doesn't block, i.e. no reverse lookup is performed.
     *
     * @param ipv4AddressLiteral the textual representation of an IPv4 address.
     * @return an {@link Inet4Address} object with no hostname set, and constructed
     *         from the provided IPv4 address literal.
     * @throws IllegalArgumentException if the {@code ipv4AddressLiteral} cannot be
     *         parsed as an IPv4 address literal.
     * @throws NullPointerException if the {@code ipv4AddressLiteral} is {@code null}.
     * @since 22
     */
    public static Inet4Address ofLiteral(String ipv4AddressLiteral) {
        Objects.requireNonNull(ipv4AddressLiteral);
        return parseAddressString(ipv4AddressLiteral, true);
    }

    /**
     * Creates an {@code Inet4Address} based on the provided {@linkplain
     * Inet4Address##format-posix textual representation of an IPv4 address in
     * POSIX {@code inet_addr} compatible form}.
     * <p> <a id="format-posix"></a> The method {@code ofPosixLiteral}
     * implements <a href="https://pubs.opengroup.org/onlinepubs/9699919799/functions/inet_addr.html">
     * POSIX {@code inet_addr}</a> compatible parsing algorithm, allowing
     * octal and hexadecimal address segments. {@code "0"} is the prefix
     * for octal numbers, {@code "0x"} and {@code "0X"} are the prefixes
     * for hexadecimal numbers. Non-zero address segments that start from
     * non-zero digits are parsed as decimal numbers. The following
     * (non-decimal) forms are supported by this method:
     * {@snippet :
     *  // Dotted-quad 'x.x.x.x' form with four part address literal
     *  Inet4Address.ofPosixLiteral("0177.0.0.1"); // ==> /127.0.0.1
     *  Inet4Address.ofPosixLiteral("0x7F.0.0.1"); // ==> /127.0.0.1
     *
     *  // Dotted-triple 'x.x.x' form with three part address literal,
     *  // the last part is placed in the rightmost two bytes
     *  // of the constructed address
     *  Inet4Address.ofPosixLiteral("0177.0.0402"); // ==> /127.0.1.2
     *  Inet4Address.ofPosixLiteral("0x7F.0.0x102"); // ==> /127.0.1.2
     *
     *  // Dotted-double 'x.x' form with two part address literal,
     *  // the last part is placed in the rightmost three bytes
     *  // of the constructed address
     *  Inet4Address.ofPosixLiteral("0177.0201003"); // ==> /127.1.2.3
     *  Inet4Address.ofPosixLiteral("0x7F.0x10203"); // ==> /127.1.2.3
     *  Inet4Address.ofPosixLiteral("127.66051"); // ==> /127.1.2.3
     *
     *  // Dotless 'x' form with one value that is stored directly in
     *  // the constructed address bytes without any rearrangement
     *  Inet4Address.ofPosixLiteral("0100401404"); // ==> /1.2.3.4
     *  Inet4Address.ofPosixLiteral("0x1020304"); // ==> /1.2.3.4
     *  Inet4Address.ofPosixLiteral("16909060"); // ==> /1.2.3.4
     * }
     * <p> If the provided IPv4 address literal cannot represent a
     * valid IPv4 address in {@linkplain Inet4Address##format-posix
     * POSIX form} an {@code IllegalArgumentException} is thrown.
     * <p> This method doesn't block, i.e. no hostname lookup is performed.
     *
     * @apiNote
     * This method produces different results compared to {@linkplain Inet4Address#ofLiteral}
     * when {@code posixIPAddressLiteral} parameter contains address segments with
     * leading zeroes. An address segment with a leading zero is always parsed as an octal
     * number by this method, therefore {@code 0255} (octal) will be parsed as
     * {@code 173} (decimal). On the other hand, {@link Inet4Address#ofLiteral
     * Inet4Address.ofLiteral} ignores leading zeros, parses all numbers as decimal and produces
     * {@code 255}. Where this method would parse {@code 0256.0256.0256.0256} (octal) and
     * produce {@code 174.174.174.174} (decimal) in four dotted quad notation,
     * {@link Inet4Address#ofLiteral Inet4Address.ofLiteral} will throw
     * {@code IllegalArgumentException}.
     *
     * @param posixIPAddressLiteral a textual representation of an IPv4 address.
     * @return an {@link Inet4Address} object with no hostname set, and constructed
     *         from the provided IPv4 address literal.
     * @throws IllegalArgumentException if the {@code posixIPAddressLiteral} cannot be
     *         parsed as an IPv4 address literal.
     * @throws NullPointerException if the {@code posixIPAddressLiteral} is {@code null}.
     * @since 23
     */
    public static Inet4Address ofPosixLiteral(String posixIPAddressLiteral) {
        Objects.requireNonNull(posixIPAddressLiteral);
        return parseAddressStringPosix(posixIPAddressLiteral);
    }

    /**
     * Parses the given string as an IPv4 address literal.
     * If the given {@code addressLiteral} string cannot be parsed as an IPv4 address literal
     * and {@code throwIAE} is {@code false}, {@code null} is returned.
     * If the given {@code addressLiteral} string cannot be parsed as an IPv4 address literal
     * and {@code throwIAE} is {@code true}, an {@code IllegalArgumentException} is thrown.
     * Otherwise, if it can be considered as {@linkplain IPAddressUtil#validateNumericFormatV4(String,
     * boolean) an ambiguous literal} - {@code IllegalArgumentException} is thrown irrelevant to
     * {@code throwIAE} value.
     *
     * @apiNote
     * The given {@code addressLiteral} string is considered ambiguous if it cannot be parsed as
     * a valid IPv4 address literal using decimal notation, but could be
     * interpreted as an IPv4 address in some other representation (octal, hexadecimal, or mixed).
     * @param addressLiteral IPv4 address literal to parse
     * @param throwIAE whether to throw {@code IllegalArgumentException} if the
     *                 given {@code addressLiteral} string cannot be parsed as
     *                 an IPv4 address literal.
     * @return {@code Inet4Address} object constructed from the address literal;
     *         or {@code null} if the literal cannot be parsed as an IPv4 address
     * @throws IllegalArgumentException if the given {@code addressLiteral} string
     * cannot be parsed as an IPv4 address literal and {@code throwIAE} is {@code true},
     * or if it is considered ambiguous, regardless of the value of {@code throwIAE}.
     */
    static Inet4Address parseAddressString(String addressLiteral, boolean throwIAE) {
        byte [] addrBytes= IPAddressUtil.validateNumericFormatV4(addressLiteral, throwIAE);
        if (addrBytes == null) {
            return null;
        }
        return new Inet4Address(null, addrBytes);
    }

    /**
     * Parses the given string as an IPv4 address literal in
     * {@linkplain Inet4Address##format-posix POSIX form.}
     *
     * <p> If the given {@code addressLiteral} string cannot be parsed as an IPv4 address literal
     * in POSIX form and {@code throwIAE} is {@code false}, {@code null} is returned.
     * If the given {@code addressLiteral} string cannot be parsed as an IPv4 address literal
     * and {@code throwIAE} is {@code true}, an {@code IllegalArgumentException}
     * is thrown.
     *
     * @apiNote
     * This method produces different results compared to {@linkplain Inet4Address#parseAddressString}
     * when {@code addressLiteral} parameter contains address segments with leading
     * zeroes. An address segment with a leading zero is always parsed as an octal
     * number by this method, therefore {@code 0255} (octal) will be parsed as
     * {@code 173} (decimal). On the other hand, {@link Inet4Address#parseAddressString}
     * ignores leading zeros, parses all numbers as decimal and produces {@code 255}.
     * Where this method would parse {@code 0256.0256.0256.0256} (octal) and produce
     * {@code 174.174.174.174} (decimal) in four dotted quad notation, {@linkplain
     * Inet4Address#parseAddressString} will either throw {@code IllegalArgumentException}
     * or return {@code null}, depending on the value of {@code throwIAE}.
     *
     * @param addressLiteral IPv4 address literal to parse
     * @param throwIAE whether to throw {@code IllegalArgumentException} if the
     *                 given {@code addressLiteral} string cannot be parsed as
     *                 an IPv4 address literal.
     * @return {@code Inet4Address} object constructed from the address literal;
     *         or {@code null} if the literal cannot be parsed as an IPv4 address
     * @throws IllegalArgumentException if the given {@code addressLiteral} string
     * cannot be parsed as an IPv4 address literal and {@code throwIAE} is {@code true}.
     */
    private static Inet4Address parseAddressStringPosix(String addressLiteral) {
        byte [] parsedBytes = IPAddressUtil.parseBsdLiteralV4(addressLiteral);
        if (parsedBytes == null) {
            throw IPAddressUtil.invalidIpAddressLiteral(addressLiteral);
        }
        return new Inet4Address(null, parsedBytes);
    }

    /**
     * Replaces the object to be serialized with an InetAddress object.
     *
     * @return the alternate object to be serialized.
     *
     * @throws ObjectStreamException if a new object replacing this
     * object could not be created
     */
    @java.io.Serial
    private Object writeReplace() throws ObjectStreamException {
        // will replace the to be serialized 'this' object
        InetAddress inet = new InetAddress();
        inet.holder().hostName = holder().getHostName();
        inet.holder().address = holder().getAddress();

        /**
         * Prior to 1.4 an InetAddress was created with a family
         * based on the platform AF_INET value (usually 2).
         * For compatibility reasons we must therefore write
         * the InetAddress with this family.
         */
        inet.holder().family = 2;

        return inet;
    }

    /**
     * Utility routine to check if the InetAddress is an
     * IP multicast address. IP multicast address is a Class D
     * address i.e first four bits of the address are 1110.
     * @return a {@code boolean} indicating if the InetAddress is
     * an IP multicast address
     */
    public boolean isMulticastAddress() {
        return ((holder().getAddress() & 0xf0000000) == 0xe0000000);
    }

    /**
     * Utility routine to check if the InetAddress is a wildcard address.
     * @return a {@code boolean} indicating if the InetAddress is
     *         a wildcard address.
     */
    public boolean isAnyLocalAddress() {
        return holder().getAddress() == 0;
    }

    /**
     * Utility routine to check if the InetAddress is a loopback address.
     *
     * @return a {@code boolean} indicating if the InetAddress is
     * a loopback address; or false otherwise.
     */
    public boolean isLoopbackAddress() {
        /* 127.x.x.x */
        byte[] byteAddr = getAddress();
        return byteAddr[0] == 127;
    }

    /**
     * Utility routine to check if the InetAddress is a link local address.
     *
     * @return a {@code boolean} indicating if the InetAddress is
     * a link local address; or false if address is not a link local unicast address.
     */
    public boolean isLinkLocalAddress() {
        // link-local unicast in IPv4 (169.254.0.0/16)
        // defined in "Documenting Special Use IPv4 Address Blocks
        // that have been Registered with IANA" by Bill Manning
        // draft-manning-dsua-06.txt
        int address = holder().getAddress();
        return (((address >>> 24) & 0xFF) == 169)
            && (((address >>> 16) & 0xFF) == 254);
    }

    /**
     * Utility routine to check if the InetAddress is a site local address.
     *
     * @return a {@code boolean} indicating if the InetAddress is
     * a site local address; or false if address is not a site local unicast address.
     */
    public boolean isSiteLocalAddress() {
        // refer to RFC 1918
        // 10/8 prefix
        // 172.16/12 prefix
        // 192.168/16 prefix
        int address = holder().getAddress();
        return (((address >>> 24) & 0xFF) == 10)
            || ((((address >>> 24) & 0xFF) == 172)
                && (((address >>> 16) & 0xF0) == 16))
            || ((((address >>> 24) & 0xFF) == 192)
                && (((address >>> 16) & 0xFF) == 168));
    }

    /**
     * Utility routine to check if the multicast address has global scope.
     *
     * @return a {@code boolean} indicating if the address has
     *         is a multicast address of global scope, false if it is not
     *         of global scope or it is not a multicast address
     */
    public boolean isMCGlobal() {
        // 224.0.1.0 to 238.255.255.255
        byte[] byteAddr = getAddress();
        return ((byteAddr[0] & 0xff) >= 224 && (byteAddr[0] & 0xff) <= 238 ) &&
            !((byteAddr[0] & 0xff) == 224 && byteAddr[1] == 0 &&
              byteAddr[2] == 0);
    }

    /**
     * Utility routine to check if the multicast address has node scope.
     *
     * @return a {@code boolean} indicating if the address has
     *         is a multicast address of node-local scope, false if it is not
     *         of node-local scope or it is not a multicast address
     */
    public boolean isMCNodeLocal() {
        // unless ttl == 0
        return false;
    }

    /**
     * Utility routine to check if the multicast address has link scope.
     *
     * @return a {@code boolean} indicating if the address has
     *         is a multicast address of link-local scope, false if it is not
     *         of link-local scope or it is not a multicast address
     */
    public boolean isMCLinkLocal() {
        // 224.0.0/24 prefix and ttl == 1
        int address = holder().getAddress();
        return (((address >>> 24) & 0xFF) == 224)
            && (((address >>> 16) & 0xFF) == 0)
            && (((address >>> 8) & 0xFF) == 0);
    }

    /**
     * Utility routine to check if the multicast address has site scope.
     *
     * @return a {@code boolean} indicating if the address has
     *         is a multicast address of site-local scope, false if it is not
     *         of site-local scope or it is not a multicast address
     */
    public boolean isMCSiteLocal() {
        // 239.255/16 prefix or ttl < 32
        int address = holder().getAddress();
        return (((address >>> 24) & 0xFF) == 239)
            && (((address >>> 16) & 0xFF) == 255);
    }

    /**
     * Utility routine to check if the multicast address has organization scope.
     *
     * @return a {@code boolean} indicating if the address has
     *         is a multicast address of organization-local scope,
     *         false if it is not of organization-local scope
     *         or it is not a multicast address
     */
    public boolean isMCOrgLocal() {
        // 239.192 - 239.195
        int address = holder().getAddress();
        return (((address >>> 24) & 0xFF) == 239)
            && (((address >>> 16) & 0xFF) >= 192)
            && (((address >>> 16) & 0xFF) <= 195);
    }

    /**
     * Returns the raw IP address of this {@code InetAddress}
     * object. The result is in network byte order: the highest order
     * byte of the address is in {@code getAddress()[0]}.
     *
     * @return  the raw IP address of this object.
     */
    public byte[] getAddress() {
        int address = holder().getAddress();
        byte[] addr = new byte[INADDRSZ];

        addr[0] = (byte) ((address >>> 24) & 0xFF);
        addr[1] = (byte) ((address >>> 16) & 0xFF);
        addr[2] = (byte) ((address >>> 8) & 0xFF);
        addr[3] = (byte) (address & 0xFF);
        return addr;
    }

    /**
     * Returns the 32-bit IPv4 address.
     */
    int addressValue() {
        return holder().getAddress();
    }

    /**
     * Returns the IP address string in textual presentation form.
     *
     * @return  the raw IP address in a string format.
     */
    public String getHostAddress() {
        return numericToTextFormat(getAddress());
    }

    /**
     * Returns a hashcode for this IP address.
     *
     * @return  a hash code value for this IP address.
     */
    public int hashCode() {
        return holder().getAddress();
    }

    /**
     * Compares this object against the specified object.
     * The result is {@code true} if and only if the argument is
     * not {@code null} and it represents the same IP address as
     * this object.
     * <p>
     * Two instances of {@code InetAddress} represent the same IP
     * address if the length of the byte arrays returned by
     * {@code getAddress} is the same for both, and each of the
     * array components is the same for the byte arrays.
     *
     * @param   obj   the object to compare against.
     * @return  {@code true} if the objects are the same;
     *          {@code false} otherwise.
     * @see     java.net.InetAddress#getAddress()
     */
    public boolean equals(Object obj) {
        return (obj instanceof Inet4Address inet4Address) &&
            inet4Address.holder().getAddress() == holder().getAddress();
    }

    // Utilities

    /**
     * Converts IPv4 binary address into a string suitable for presentation.
     *
     * @param src a byte array representing an IPv4 numeric address
     * @return a String representing the IPv4 address in
     *         textual representation format
     */
    static String numericToTextFormat(byte[] src)
    {
        return (src[0] & 0xff) + "." + (src[1] & 0xff) + "." + (src[2] & 0xff) + "." + (src[3] & 0xff);
    }

    /**
     * Perform class load-time initializations.
     */
    private static native void init();
}
