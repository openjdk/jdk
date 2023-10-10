/*
 * Copyright (c) 2004, 2023, Oracle and/or its affiliates. All rights reserved.
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

package sun.net.util;

import sun.security.action.GetPropertyAction;

import java.io.UncheckedIOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.nio.CharBuffer;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.security.PrivilegedActionException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class IPAddressUtil {
    private static final int INADDR4SZ = 4;
    private static final int INADDR16SZ = 16;
    private static final int INT16SZ = 2;

    /*
     * Converts IPv4 address in its textual presentation form
     * into its numeric binary form.
     *
     * @param src a String representing an IPv4 address in standard format
     * @return a byte array representing the IPv4 numeric address
     */
    @SuppressWarnings("fallthrough")
    public static byte[] textToNumericFormatV4(String src)
    {
        byte[] res = new byte[INADDR4SZ];

        long tmpValue = 0;
        int currByte = 0;
        boolean newOctet = true;

        int len = src.length();
        if (len == 0 || len > 15) {
            return null;
        }
        /*
         * When only one part is given, the value is stored directly in
         * the network address without any byte rearrangement.
         *
         * When a two part address is supplied, the last part is
         * interpreted as a 24-bit quantity and placed in the right
         * most three bytes of the network address. This makes the
         * two part address format convenient for specifying Class A
         * network addresses as net.host.
         *
         * When a three part address is specified, the last part is
         * interpreted as a 16-bit quantity and placed in the right
         * most two bytes of the network address. This makes the
         * three part address format convenient for specifying
         * Class B net- work addresses as 128.net.host.
         *
         * When four parts are specified, each is interpreted as a
         * byte of data and assigned, from left to right, to the
         * four bytes of an IPv4 address.
         *
         * We determine and parse the leading parts, if any, as single
         * byte values in one pass directly into the resulting byte[],
         * then the remainder is treated as a 8-to-32-bit entity and
         * translated into the remaining bytes in the array.
         */
        for (int i = 0; i < len; i++) {
            char c = src.charAt(i);
            if (c == '.') {
                if (newOctet || tmpValue < 0 || tmpValue > 0xff || currByte == 3) {
                    return null;
                }
                res[currByte++] = (byte) (tmpValue & 0xff);
                tmpValue = 0;
                newOctet = true;
            } else {
                int digit = digit(c, 10);
                if (digit < 0) {
                    return null;
                }
                tmpValue *= 10;
                tmpValue += digit;
                newOctet = false;
            }
        }
        if (newOctet || tmpValue < 0 || tmpValue >= (1L << ((4 - currByte) * 8))) {
            return null;
        }
        switch (currByte) {
            case 0:
                res[0] = (byte) ((tmpValue >> 24) & 0xff);
            case 1:
                res[1] = (byte) ((tmpValue >> 16) & 0xff);
            case 2:
                res[2] = (byte) ((tmpValue >>  8) & 0xff);
            case 3:
                res[3] = (byte) ((tmpValue >>  0) & 0xff);
        }
        return res;
    }

    /**
     * Validates if input string is a valid IPv4 address literal.
     * If the "jdk.net.allowAmbiguousIPAddressLiterals" system property is set
     * to {@code false}, or is not set then validation of the address string is performed as follows:
     * If string can't be parsed by following IETF IPv4 address string literals
     * formatting style rules (default one), but can be parsed by following BSD formatting
     * style rules, the IPv4 address string content is treated as ambiguous and
     * either {@code IllegalArgumentException} is thrown, or {@code null} is returned.
     *
     * @param src input string
     * @param throwIAE {@code true} - throw {@code IllegalArgumentException} when cannot be parsed
     *                            as IPv4 address string;
     *                 {@code false} - throw {@code IllegalArgumentException} only when IPv4 address
     *                            string is ambiguous.
     * @return bytes array if string is a valid IPv4 address string
     * @throws IllegalArgumentException if "jdk.net.allowAmbiguousIPAddressLiterals" SP is set to
     *                                  {@code false}, IPv4 address string {@code src} is ambiguous,
     *                                  or when address string cannot be parsed as an IPv4 address
     *                                  string and {@code throwIAE} is set to {@code true}.
     */
    public static byte[] validateNumericFormatV4(String src, boolean throwIAE) {
        byte[] parsedBytes = textToNumericFormatV4(src);
        if (!ALLOW_AMBIGUOUS_IPADDRESS_LITERALS_SP_VALUE
                && parsedBytes == null && isBsdParsableV4(src)) {
            throw invalidIpAddressLiteral(src);
        }
        if (parsedBytes == null && throwIAE) {
            throw invalidIpAddressLiteral(src);
        }
        return parsedBytes;
    }

    /**
     * Creates {@code IllegalArgumentException} with invalid IP address literal message.
     *
     * @param src address literal string to include to the exception message
     * @return an {@code IllegalArgumentException} instance
     */
    public static IllegalArgumentException invalidIpAddressLiteral(String src) {
        return new IllegalArgumentException("Invalid IP address literal: " + src);
    }

    /*
     * Convert IPv6 presentation level address to network order binary form.
     * credit:
     *  Converted from C code from Solaris 8 (inet_pton)
     *
     * Any component of the string following a per-cent % is ignored.
     *
     * @param src a String representing an IPv6 address in textual format
     * @return a byte array representing the IPv6 numeric address
     */
    public static byte[] textToNumericFormatV6(String src)
    {
        // Shortest valid string is "::", hence at least 2 chars
        if (src.length() < 2) {
            return null;
        }

        int colonp;
        char ch;
        boolean saw_xdigit;
        int val;
        char[] srcb = src.toCharArray();
        byte[] dst = new byte[INADDR16SZ];

        int srcb_length = srcb.length;
        int pc = src.indexOf ('%');
        if (pc == srcb_length -1) {
            return null;
        }

        if (pc != -1) {
            srcb_length = pc;
        }

        colonp = -1;
        int i = 0, j = 0;
        /* Leading :: requires some special handling. */
        if (srcb[i] == ':')
            if (srcb[++i] != ':')
                return null;
        int curtok = i;
        saw_xdigit = false;
        val = 0;
        while (i < srcb_length) {
            ch = srcb[i++];
            int chval = digit(ch, 16);
            if (chval != -1) {
                val <<= 4;
                val |= chval;
                if (val > 0xffff)
                    return null;
                saw_xdigit = true;
                continue;
            }
            if (ch == ':') {
                curtok = i;
                if (!saw_xdigit) {
                    if (colonp != -1)
                        return null;
                    colonp = j;
                    continue;
                } else if (i == srcb_length) {
                    return null;
                }
                if (j + INT16SZ > INADDR16SZ)
                    return null;
                dst[j++] = (byte) ((val >> 8) & 0xff);
                dst[j++] = (byte) (val & 0xff);
                saw_xdigit = false;
                val = 0;
                continue;
            }
            if (ch == '.' && ((j + INADDR4SZ) <= INADDR16SZ)) {
                String ia4 = src.substring(curtok, srcb_length);
                /* check this IPv4 address has 3 dots, i.e. A.B.C.D */
                int dot_count = 0, index=0;
                while ((index = ia4.indexOf ('.', index)) != -1) {
                    dot_count ++;
                    index ++;
                }
                if (dot_count != 3) {
                    return null;
                }
                byte[] v4addr = textToNumericFormatV4(ia4);
                if (v4addr == null) {
                    return null;
                }
                for (int k = 0; k < INADDR4SZ; k++) {
                    dst[j++] = v4addr[k];
                }
                saw_xdigit = false;
                break;  /* '\0' was seen by inet_pton4(). */
            }
            return null;
        }
        if (saw_xdigit) {
            if (j + INT16SZ > INADDR16SZ)
                return null;
            dst[j++] = (byte) ((val >> 8) & 0xff);
            dst[j++] = (byte) (val & 0xff);
        }

        if (colonp != -1) {
            int n = j - colonp;

            if (j == INADDR16SZ)
                return null;
            for (i = 1; i <= n; i++) {
                dst[INADDR16SZ - i] = dst[colonp + n - i];
                dst[colonp + n - i] = 0;
            }
            j = INADDR16SZ;
        }
        if (j != INADDR16SZ)
            return null;
        byte[] newdst = convertFromIPv4MappedAddress(dst);
        if (newdst != null) {
            return newdst;
        } else {
            return dst;
        }
    }

    /**
     * @param src a String representing an IPv4 address in textual format
     * @return a boolean indicating whether src is an IPv4 literal address
     */
    public static boolean isIPv4LiteralAddress(String src) {
        return textToNumericFormatV4(src) != null;
    }

    /**
     * @param src a String representing an IPv6 address in textual format
     * @return a boolean indicating whether src is an IPv6 literal address
     */
    public static boolean isIPv6LiteralAddress(String src) {
        return textToNumericFormatV6(src) != null;
    }

    /*
     * Convert IPv4-Mapped address to IPv4 address. Both input and
     * returned value are in network order binary form.
     *
     * @param src a String representing an IPv4-Mapped address in textual format
     * @return a byte array representing the IPv4 numeric address
     */
    public static byte[] convertFromIPv4MappedAddress(byte[] addr) {
        if (isIPv4MappedAddress(addr)) {
            byte[] newAddr = new byte[INADDR4SZ];
            System.arraycopy(addr, 12, newAddr, 0, INADDR4SZ);
            return newAddr;
        }
        return null;
    }

    /**
     * Utility routine to check if the InetAddress is an
     * IPv4 mapped IPv6 address.
     *
     * @return a <code>boolean</code> indicating if the InetAddress is
     * an IPv4 mapped IPv6 address; or false if address is IPv4 address.
     */
    private static boolean isIPv4MappedAddress(byte[] addr) {
        if (addr.length < INADDR16SZ) {
            return false;
        }
        if ((addr[0] == 0x00) && (addr[1] == 0x00) &&
            (addr[2] == 0x00) && (addr[3] == 0x00) &&
            (addr[4] == 0x00) && (addr[5] == 0x00) &&
            (addr[6] == 0x00) && (addr[7] == 0x00) &&
            (addr[8] == 0x00) && (addr[9] == 0x00) &&
            (addr[10] == (byte)0xff) &&
            (addr[11] == (byte)0xff))  {
            return true;
        }
        return false;
    }
    /**
     * Mapping from unscoped local Inet(6)Address to the same address
     * including the correct scope-id, determined from NetworkInterface.
     */
    private static final ConcurrentHashMap<InetAddress,InetAddress>
        cache = new ConcurrentHashMap<>();

    /**
     * Returns a scoped version of the supplied local, link-local ipv6 address
     * if that scope-id can be determined from local NetworkInterfaces.
     * If the address already has a scope-id or if the address is not local, ipv6
     * or link local, then the original address is returned.
     *
     * @param address
     * @exception SocketException if the given ipv6 link local address is found
     *            on more than one local interface
     * @return
     */
    public static InetAddress toScopedAddress(InetAddress address)
        throws SocketException {

        if (address instanceof Inet6Address && address.isLinkLocalAddress()
            && ((Inet6Address) address).getScopeId() == 0) {

            InetAddress cached = null;
            try {
                cached = cache.computeIfAbsent(address, k -> findScopedAddress(k));
            } catch (UncheckedIOException e) {
                throw (SocketException)e.getCause();
            }
            return cached != null ? cached : address;
        } else {
            return address;
        }
    }

    /**
     * Same as above for InetSocketAddress
     */
    public static InetSocketAddress toScopedAddress(InetSocketAddress address)
        throws SocketException {
        InetAddress addr;
        InetAddress orig = address.getAddress();
        if ((addr = toScopedAddress(orig)) == orig) {
            return address;
        } else {
            return new InetSocketAddress(addr, address.getPort());
        }
    }

    @SuppressWarnings("removal")
    private static InetAddress findScopedAddress(InetAddress address) {
        PrivilegedExceptionAction<List<InetAddress>> pa = () -> NetworkInterface.networkInterfaces()
                .flatMap(NetworkInterface::inetAddresses)
                .filter(a -> (a instanceof Inet6Address)
                        && address.equals(a)
                        && ((Inet6Address) a).getScopeId() != 0)
                .toList();
        List<InetAddress> result;
        try {
            result = AccessController.doPrivileged(pa);
            var sz = result.size();
            if (sz == 0)
                return null;
            if (sz > 1)
                throw new UncheckedIOException(new SocketException(
                    "Duplicate link local addresses: must specify scope-id"));
            return result.get(0);
        } catch (PrivilegedActionException pae) {
            return null;
        }
    }

    // See java.net.URI for more details on how to generate these
    // masks.
    //
    // square brackets
    private static final long L_IPV6_DELIMS = 0x0L; // "[]"
    private static final long H_IPV6_DELIMS = 0x28000000L; // "[]"
    // RFC 3986 gen-delims
    private static final long L_GEN_DELIMS = 0x8400800800000000L; // ":/?#[]@"
    private static final long H_GEN_DELIMS = 0x28000001L; // ":/?#[]@"
    // These gen-delims can appear in authority
    private static final long L_AUTH_DELIMS = 0x400000000000000L; // "@[]:"
    private static final long H_AUTH_DELIMS = 0x28000001L; // "@[]:"
    // colon is allowed in userinfo
    private static final long L_COLON = 0x400000000000000L; // ":"
    private static final long H_COLON = 0x0L; // ":"
    // slash should be encoded in authority
    private static final long L_SLASH = 0x800000000000L; // "/"
    private static final long H_SLASH = 0x0L; // "/"
    // backslash should always be encoded
    private static final long L_BACKSLASH = 0x0L; // "\"
    private static final long H_BACKSLASH = 0x10000000L; // "\"
    // ASCII chars 0-31 + 127 - various controls + CRLF + TAB
    private static final long L_NON_PRINTABLE = 0xffffffffL;
    private static final long H_NON_PRINTABLE = 0x8000000000000000L;
    // All of the above
    private static final long L_EXCLUDE = 0x84008008ffffffffL;
    private static final long H_EXCLUDE = 0x8000000038000001L;
    // excluded delims: "<>\" " - we don't include % and # here
    private static final long L_EXCLUDED_DELIMS = 0x5000000500000000L;
    private static final long H_EXCLUDED_DELIMS = 0x0L;
    // unwise "{}|\\^[]`";
    private static final long L_UNWISE = 0x0L;
    private static final long H_UNWISE = 0x3800000178000000L;
    private static final long L_FRAGMENT = 0x0000000800000000L;
    private static final long H_FRAGMENT = 0x0L;
    private static final long L_QUERY = 0x8000000000000000L;
    private static final long H_QUERY = 0x0L;

    private static final char[] OTHERS = {
            8263,8264,8265,8448,8449,8453,8454,10868,
            65109,65110,65119,65131,65283,65295,65306,65311,65312
    };

    // Tell whether the given character is found by the given mask pair
    public static boolean match(char c, long lowMask, long highMask) {
        if (c < 64)
            return ((1L << c) & lowMask) != 0;
        if (c < 128)
            return ((1L << (c - 64)) & highMask) != 0;
        return false; // other non ASCII characters are not filtered
    }

    // returns -1 if the string doesn't contain any characters
    // from the mask, the index of the first such character found
    // otherwise.
    public static int scan(String s, long lowMask, long highMask) {
        int i = -1, len;
        if (s == null || (len = s.length()) == 0) return -1;
        boolean match = false;
        while (++i < len && !(match = match(s.charAt(i), lowMask, highMask)));
        if (match) return i;
        return -1;
    }

    public static int scan(String s, long lowMask, long highMask, char[] others) {
        int i = -1, len;
        if (s == null || (len = s.length()) == 0) return -1;
        boolean match = false;
        char c, c0 = others[0];
        while (++i < len && !(match = match((c=s.charAt(i)), lowMask, highMask))) {
            if (c >= c0 && (Arrays.binarySearch(others, c) > -1)) {
                match = true; break;
            }
        }
        if (match) return i;

        return -1;
    }

    private static String describeChar(char c) {
        if (c < 32 || c == 127) {
            if (c == '\n') return "LF";
            if (c == '\r') return "CR";
            return "control char (code=" + (int)c + ")";
        }
        if (c == '\\') return "'\\'";
        return "'" + c + "'";
    }

    // Check user-info component.
    // This method returns an error message if a problem
    // is found. The caller is expected to use that message to
    // throw an exception.
    public static String checkUserInfo(String str) {
        // colon is permitted in user info
        int index = scan(str, MASKS.L_USERINFO_MASK,
                MASKS.H_USERINFO_MASK);
        if (index >= 0) {
            return "Illegal character found in user-info: "
                    + describeChar(str.charAt(index));
        }
        return null;
    }

    private static String checkHost(String str) {
        int index;
        if (str.startsWith("[") && str.endsWith("]")) {
            str = str.substring(1, str.length() - 1);
            if (isIPv6LiteralAddress(str)) {
                index = str.indexOf('%');
                if (index >= 0) {
                    index = scan(str = str.substring(index),
                            MASKS.L_SCOPE_MASK, MASKS.H_SCOPE_MASK);
                    if (index >= 0) {
                        return "Illegal character found in IPv6 scoped address: "
                                + describeChar(str.charAt(index));
                    }
                }
                return null;
            }
            return "Unrecognized IPv6 address format";
        } else {
            index = scan(str, L_EXCLUDE | MASKS.L_HOSTNAME_MASK,
                    H_EXCLUDE | MASKS.H_HOSTNAME_MASK, OTHERS);
            if (index >= 0) {
                return "Illegal character found in host: "
                        + describeChar(str.charAt(index));
            }
        }
        return null;
    }

    // Simple checks for the authority component.
    // Deeper checks on the various parts of a server-based
    // authority component may be performed by calling
    // #checkAuthority(URL url)
    // This method returns an error message if a problem
    // is found. The caller is expected to use that message to
    // throw an exception.
    public static String checkAuth(String str) {
        int index = scan(str,
                L_EXCLUDE & ~L_AUTH_DELIMS,
                H_EXCLUDE & ~H_AUTH_DELIMS);
        if (index >= 0) {
            return "Illegal character found in authority: "
                    + describeChar(str.charAt(index));
        }
        return null;
    }

    // check authority of hierarchical (server based) URL.
    // Appropriate for HTTP-like protocol handlers
    // This method returns an error message if a problem
    // is found. The caller is expected to use that message to
    // throw an exception.
    public static String checkAuthority(URL url) {
        String s, u, h;
        if (url == null) return null;
        if ((s = checkUserInfo(u = url.getUserInfo())) != null) {
            return s;
        }
        if ((s = checkHost(h = url.getHost())) != null) {
            return s;
        }
        if (h == null && u == null) {
            return checkAuth(url.getAuthority());
        }
        return null;
    }

    // minimal syntax checks if delayed parsing is
    // enabled - deeper check will be performed
    // later by the appropriate protocol handler
    // This method returns an error message if a problem
    // is found. The caller is expected to use that message to
    // throw an exception.
    public static String checkExternalForm(URL url) {
        String s;
        if (url == null) return null;
        boolean earlyURLParsing = earlyURLParsing();
        String userInfo = url.getUserInfo();
        if (earlyURLParsing) {
            if ((s = checkUserInfo(userInfo)) != null) return s;
        } else {
            int index = scan(s = userInfo,
                    L_NON_PRINTABLE | L_SLASH,
                    H_NON_PRINTABLE | H_SLASH);
            if (index >= 0) {
                return "Illegal character found in authority: "
                        + describeChar(s.charAt(index));
            }
        }
        String host = url.getHost();
        if ((s = checkHostString(host)) != null) {
            return s;
        }
        return null;
    }

    // Check host component.
    // This method returns an error message if a problem
    // is found. The caller is expected to use that message to
    // throw an exception.
    public static String checkHostString(String host) {
        if (host == null) return null;
        if (earlyURLParsing()) {
            // also validate IPv6 literal format if present
            return checkHost(host);
        } else {
            int index = scan(host,
                    MASKS.L_HOSTNAME_MASK,
                    MASKS.H_HOSTNAME_MASK,
                    OTHERS);
            if (index >= 0) {
                return "Illegal character found in host: "
                        + describeChar(host.charAt(index));
            }
        }
        return null;
    }

    /**
     * Returns the numeric value of the character {@code ch} in the
     * specified radix.
     *
     * @param ch    the character to be converted.
     * @param radix the radix.
     * @return the numeric value represented by the character in the
     * specified radix.
     */
    public static int digit(char ch, int radix) {
        if (ALLOW_AMBIGUOUS_IPADDRESS_LITERALS_SP_VALUE) {
            return Character.digit(ch, radix);
        } else {
            return parseAsciiDigit(ch, radix);
        }
    }

    /**
     * Try to parse String as IPv4 address literal by following
     * BSD-style formatting rules.
     *
     * @param input input string
     * @return {@code true} if input string is parsable as IPv4 address literal,
     * {@code false} otherwise.
     */
    public static boolean isBsdParsableV4(String input) {
        char firstSymbol = input.charAt(0);
        // Check if first digit is not a decimal digit
        if (parseAsciiDigit(firstSymbol, DECIMAL) == -1) {
            return false;
        }

        // Last character is dot OR is not a supported digit: [0-9,A-F,a-f]
        char lastSymbol = input.charAt(input.length() - 1);
        if (lastSymbol == '.' || parseAsciiHexDigit(lastSymbol) == -1) {
            return false;
        }

        // Parse IP address fields
        CharBuffer charBuffer = CharBuffer.wrap(input);
        int fieldNumber = 0;
        while (charBuffer.hasRemaining()) {
            long fieldValue = -1L;
            // Try to parse fields in all supported radixes
            for (int radix : SUPPORTED_RADIXES) {
                fieldValue = parseV4FieldBsd(radix, charBuffer, fieldNumber);
                if (fieldValue >= 0) {
                    fieldNumber++;
                    break;
                } else if (fieldValue == TERMINAL_PARSE_ERROR) {
                    return false;
                }
            }
            // If field can't be parsed as one of supported radixes stop
            // parsing
            if (fieldValue < 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Method tries to parse IP address field that starts from {@linkplain CharBuffer#position()
     * current position} of the provided character buffer.
     * <p>
     * This method supports three {@code "radix"} values to decode field values in
     * {@code "HEXADECIMAL (radix=16)"}, {@code "DECIMAL (radix=10)"} and
     * {@code "OCTAL (radix=8)"} radixes.
     * <p>
     * If {@code -1} value is returned the char buffer position is reset to the value
     * it was before it was called.
     * <p>
     * Method returns {@code -2} if formatting illegal for all supported {@code radix}
     * values is observed, and there is no point in checking other radix values.
     * That includes the following cases:<ul>
     * <li>Two subsequent dots are observer
     * <li>Number of dots more than 3
     * <li>Field value exceeds max allowed
     * <li>Character is not a valid digit for the requested {@code radix} value, given
     * that a field has the radix specific prefix
     * </ul>
     *
     * @param radix       digits encoding radix to use for parsing. Valid values: 8, 10, 16.
     * @param buffer      {@code CharBuffer} with position set to the field's fist character
     * @param fieldNumber parsed field number
     * @return {@code CANT_PARSE_IN_RADIX} if field can not be parsed in requested {@code radix}.
     * {@code TERMINAL_PARSE_ERROR} if field can't be parsed and the whole parse process should be terminated.
     * Parsed field value otherwise.
     */
    private static long parseV4FieldBsd(int radix, CharBuffer buffer, int fieldNumber) {
        int initialPos = buffer.position();
        long val = 0;
        int digitsCount = 0;
        if (!checkPrefix(buffer, radix)) {
            val = CANT_PARSE_IN_RADIX;
        }
        boolean dotSeen = false;
        while (buffer.hasRemaining() && val != CANT_PARSE_IN_RADIX && !dotSeen) {
            char c = buffer.get();
            if (c == '.') {
                dotSeen = true;
                // Fail if 4 dots in IP address string.
                // fieldNumber counter starts from 0, therefore 3
                if (fieldNumber == 3) {
                    // Terminal state, can stop parsing: too many fields
                    return TERMINAL_PARSE_ERROR;
                }
                // Check for literals with two dots, like '1.2..3', '1.2.3..'
                if (digitsCount == 0) {
                    // Terminal state, can stop parsing: dot with no digits
                    return TERMINAL_PARSE_ERROR;
                }
                if (val > 255) {
                    // Terminal state, can stop parsing: too big value for an octet
                    return TERMINAL_PARSE_ERROR;
                }
            } else {
                int dv = parseAsciiDigit(c, radix);
                if (dv >= 0) {
                    digitsCount++;
                    val *= radix;
                    val += dv;
                } else {
                    // Spotted digit can't be parsed in the requested 'radix'.
                    // The order in which radixes are checked - hex, octal, decimal:
                    //    - if symbol is not a valid digit in hex radix - terminal
                    //    - if symbol is not a valid digit in octal radix, and given
                    //      that octal prefix was observed before - terminal
                    //    - if symbol is not a valid digit in decimal radix - terminal
                    return TERMINAL_PARSE_ERROR;
                }
            }
        }
        if (val == CANT_PARSE_IN_RADIX) {
            buffer.position(initialPos);
        } else if (!dotSeen) {
            // It is the last field - check its value
            // This check will ensure that address strings with less
            // than 4 fields, i.e. A, A.B and A.B.C address types
            // contain value less then the allowed maximum for the last field.
            long maxValue = (1L << ((4 - fieldNumber) * 8)) - 1;
            if (val > maxValue) {
                //  Terminal state, can stop parsing: last field value exceeds its
                //  allowed value
                return TERMINAL_PARSE_ERROR;
            }
        }
        return val;
    }

    // This method moves the position of the supplied CharBuffer by analysing the digit prefix
    // symbols if any.
    // The caller should reset the position when method returns false.
    private static boolean checkPrefix(CharBuffer buffer, int radix) {
        return switch (radix) {
            case OCTAL -> isOctalFieldStart(buffer);
            case DECIMAL -> isDecimalFieldStart(buffer);
            case HEXADECIMAL -> isHexFieldStart(buffer);
            default -> throw new AssertionError("Not supported radix");
        };
    }

    // This method always moves the position of the supplied CharBuffer
    // removing the octal prefix symbols '0'.
    // The caller should reset the position when method returns false.
    private static boolean isOctalFieldStart(CharBuffer cb) {
        // .0<EOS> is not treated as octal field
        if (cb.remaining() < 2) {
            return false;
        }

        // Fetch two first characters
        int position = cb.position();
        char first = cb.get();
        char second = cb.get();

        // Return false if the first char is not octal prefix '0' or second is a
        // field separator - parseV4FieldBsd will reset position to start of the field.
        // '.0.' fields will be successfully parsed in decimal radix.
        boolean isOctalPrefix = first == '0' && second != '.';

        // If the prefix looks like octal - consume '0', otherwise 'false' is returned
        // and caller will reset the buffer position.
        if (isOctalPrefix) {
            cb.position(position + 1);
        }
        return isOctalPrefix;
    }

    // This method doesn't move the position of the supplied CharBuffer
    private static boolean isDecimalFieldStart(CharBuffer cb) {
        return cb.hasRemaining();
    }

    // This method always moves the position of the supplied CharBuffer
    // removing the hexadecimal prefix symbols '0x'.
    // The caller should reset the position when method returns false.
    private static boolean isHexFieldStart(CharBuffer cb) {
        if (cb.remaining() < 2) {
            return false;
        }
        char first = cb.get();
        char second = cb.get();
        return first == '0' && (second == 'x' || second == 'X');
    }

    // Parse ASCII digit in given radix
    public static int parseAsciiDigit(char c, int radix) {
        assert radix == OCTAL || radix == DECIMAL || radix == HEXADECIMAL;
        if (radix == HEXADECIMAL) {
            return parseAsciiHexDigit(c);
        }
        int val = c - '0';
        return (val < 0 || val >= radix) ? -1 : val;
    }

    // Parse ASCII digit in hexadecimal radix
    private static int parseAsciiHexDigit(char digit) {
        char c = Character.toLowerCase(digit);
        if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        }
        return parseAsciiDigit(c, DECIMAL);
    }

    public static boolean earlyURLParsing() {
        return !MASKS.DELAY_URL_PARSING_SP_VALUE;
    }

    public static boolean delayURLParsing() {
        return MASKS.DELAY_URL_PARSING_SP_VALUE;
    }

    // Supported radixes
    private static final int HEXADECIMAL = 16;
    private static final int DECIMAL = 10;
    private static final int OCTAL = 8;
    // Order in which field formats are exercised to parse one IP address textual field
    private static final int[] SUPPORTED_RADIXES = new int[]{HEXADECIMAL, OCTAL, DECIMAL};

    // BSD parser's return values
    private final static long CANT_PARSE_IN_RADIX = -1L;
    private final static long TERMINAL_PARSE_ERROR = -2L;

    private static final String ALLOW_AMBIGUOUS_IPADDRESS_LITERALS_SP = "jdk.net.allowAmbiguousIPAddressLiterals";
    private static final boolean ALLOW_AMBIGUOUS_IPADDRESS_LITERALS_SP_VALUE = Boolean.valueOf(
            GetPropertyAction.privilegedGetProperty(ALLOW_AMBIGUOUS_IPADDRESS_LITERALS_SP, "false"));
    private static class MASKS {
        private static final String DELAY_URL_PARSING_SP = "jdk.net.url.delayParsing";
        private static final boolean DELAY_URL_PARSING_SP_VALUE;
        static final long L_USERINFO_MASK = L_EXCLUDE & ~L_COLON;
        static final long H_USERINFO_MASK = H_EXCLUDE & ~H_COLON;
        static final long L_HOSTNAME_MASK;
        static final long H_HOSTNAME_MASK;
        static final long L_SCOPE_MASK;
        static final long H_SCOPE_MASK;
        static {
            var value = GetPropertyAction.privilegedGetProperty(
                    DELAY_URL_PARSING_SP, "false");
            DELAY_URL_PARSING_SP_VALUE = value.isEmpty()
                    || Boolean.parseBoolean(value);
            if (DELAY_URL_PARSING_SP_VALUE) {
                L_HOSTNAME_MASK = L_NON_PRINTABLE | L_SLASH;
                H_HOSTNAME_MASK = H_NON_PRINTABLE | H_SLASH;
                L_SCOPE_MASK = L_NON_PRINTABLE | L_IPV6_DELIMS;
                H_SCOPE_MASK = H_NON_PRINTABLE | H_IPV6_DELIMS;
            } else {
                // the hostname mask can also forbid [ ] brackets, because IPv6 should be
                // checked early before the mask is used when earlier parsing checks are performed
                L_HOSTNAME_MASK = L_NON_PRINTABLE | L_SLASH | L_UNWISE | L_EXCLUDED_DELIMS;
                H_HOSTNAME_MASK = H_NON_PRINTABLE | H_SLASH | H_UNWISE | H_EXCLUDED_DELIMS;
                L_SCOPE_MASK = L_NON_PRINTABLE | L_IPV6_DELIMS | L_SLASH | L_BACKSLASH | L_FRAGMENT | L_QUERY;
                H_SCOPE_MASK = H_NON_PRINTABLE | H_IPV6_DELIMS | H_SLASH | H_BACKSLASH | H_FRAGMENT | H_QUERY;
            }
        }
    }
}
