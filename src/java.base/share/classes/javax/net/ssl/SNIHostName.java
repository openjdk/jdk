/*
 * Copyright (c) 2012, 2026, Oracle and/or its affiliates. All rights reserved.
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

package javax.net.ssl;

import sun.security.x509.DNSName;

import java.io.IOException;
import java.net.IDN;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CharacterCodingException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static sun.net.util.IPAddressUtil.isIPv4LiteralAddress;
import static sun.net.util.IPAddressUtil.isIPv6LiteralAddress;

/**
 * Instances of this class represent a server name of type
 * {@link StandardConstants#SNI_HOST_NAME host_name} in a Server Name
 * Indication (SNI) extension.
 * <P>
 * As described in section 3, "Server Name Indication", of
 * <A HREF="http://www.ietf.org/rfc/rfc6066.txt">TLS Extensions (RFC 6066)</A>,
 * "HostName" contains the fully qualified DNS hostname of the server, as
 * understood by the client.  The encoded server name value of a hostname is
 * represented as a byte string using ASCII encoding without a trailing dot.
 * This allows the support of Internationalized Domain Names (IDN) through
 * the use of A-labels (the ASCII-Compatible Encoding (ACE) form of a valid
 * string of Internationalized Domain Names for Applications (IDNA)) defined
 * in <A HREF="http://www.ietf.org/rfc/rfc5890.txt">RFC 5890</A>.
 * <P>
 * Note that {@code SNIHostName} objects are immutable.
 *
 * @spec https://www.rfc-editor.org/info/rfc5890
 *      RFC 5890: Internationalized Domain Names for Applications (IDNA):
 *              Definitions and Document Framework
 * @spec https://www.rfc-editor.org/info/rfc6066
 *      RFC 6066: Transport Layer Security (TLS) Extensions: Extension Definitions
 * @see SNIServerName
 * @see StandardConstants#SNI_HOST_NAME
 *
 * @since 1.8
 */
public final class SNIHostName extends SNIServerName {

    // the decoded string value of the server name
    private final String hostname;

    /**
     * Creates an {@code SNIHostName} from the specified hostname.
     * <P>
     * Note that per <A HREF="http://www.ietf.org/rfc/rfc6066.txt">RFC 6066</A>,
     * the encoded server name value of a hostname is
     * {@link StandardCharsets#US_ASCII}-compliant.  In this method,
     * {@code hostname} can be a user-friendly Internationalized Domain Name
     * (IDN).  {@link IDN#toASCII(String, int)} is used to enforce the
     * restrictions on ASCII characters in hostnames (see
     * <A HREF="http://www.ietf.org/rfc/rfc3490.txt">RFC 3490</A>,
     * <A HREF="http://www.ietf.org/rfc/rfc1122.txt">RFC 1122</A>,
     * <A HREF="http://www.ietf.org/rfc/rfc1123.txt">RFC 1123</A>) and
     * translate the {@code hostname} into ASCII Compatible Encoding (ACE), as:
     * <pre>
     *     IDN.toASCII(hostname, IDN.USE_STD3_ASCII_RULES);
     * </pre>
     * <P>
     * The {@code hostname} argument is illegal if it:
     * <ul>
     * <li> {@code hostname} is empty,</li>
     * <li> {@code hostname} ends with a trailing dot,</li>
     * <li> {@code hostname} is not a valid Internationalized
     *      Domain Name (IDN) compliant with the RFC 3490 specification.</li>
     * </ul>
     * @param  hostname
     *         the hostname of this server name
     *
     * @throws NullPointerException if {@code hostname} is {@code null}
     * @throws IllegalArgumentException if {@code hostname} is illegal
     *
     * @spec https://www.rfc-editor.org/info/rfc1122
     *      RFC 1122: Requirements for Internet Hosts - Communication Layers
     * @spec https://www.rfc-editor.org/info/rfc1123
     *      RFC 1123: Requirements for Internet Hosts - Application and Support
     * @spec https://www.rfc-editor.org/info/rfc3490
     *      RFC 3490: Internationalizing Domain Names in Applications (IDNA)
     * @spec https://www.rfc-editor.org/info/rfc6066
     *      RFC 6066: Transport Layer Security (TLS) Extensions: Extension Definitions
     *
     * @deprecated This constructor is not fully aligned with RFC 6066 and does
     * not reject a hostname that is an IP literal address. Use
     * {@link #ofHostName(String) SNIHostName.ofHostName()} instead, which
     * performs stricter checks on the provided hostname.
     */
    @Deprecated(since = "27")
    public SNIHostName(String hostname) {
        this(hostname, false);
    }

    private SNIHostName(String hostname, boolean strict) {
        Objects.requireNonNull(hostname, "Server name value of host_name cannot be null");
        this.hostname = hostname = asciifyHostName(hostname, strict);
        super(StandardConstants.SNI_HOST_NAME, hostname.getBytes(US_ASCII));
    }

    /**
     * Returns an {@code SNIHostName} from the specified hostname.
     * <p>
     * A valid SNI hostname is a DNS hostname (see <a
     * href="http://www.ietf.org/rfc1123.txt">RFC&nbsp;1123</a> and <a
     * href="http://www.ietf.org/rfc5280.txt">RFC&nbsp;5280</a>) that is either
     * ASCII-encoded or an {@linkplain IDN Internationalized Domain Name (IDN)}.
     * The {@code hostname} argument is considered illegal
     * if it:
     * <ul>
     * <li>is empty,
     * <li>ends with a trailing dot,
     * <li>is an {@linkplain java.net.InetAddress#ofLiteral(String) IP literal
     * address},
     * <li>or isn't a valid DNS hostname.
     * </ul>
     * <p>
     * Examples of valid SNI hostnames:
     * <ul>
     * <li>{@code example.com}
     * <li>{@code ëxample.com} &mdash; User-friendly IDN containing non-ASCII
     * Unicode code points
     * <li>{@code xn--xample-ova.com} &mdash; IDN in ASCII-Compatible Encoding
     * (ACE)
     * </ul>
     *
     * <h4>Non-ASCII Unicode code points</h4>
     *
     * Per <a href="http://www.ietf.org/rfc/rfc6066.txt">RFC&nbsp;6066</a>,
     * the server name value of a hostname is encoded in {@linkplain
     * StandardCharsets#US_ASCII ASCII}. The
     * {@link IDN#toASCII(String, int) IDN.toASCII(hostname, IDN.USE_STD3_ASCII_RULES)}
     * method is used to translate non-ASCII Unicode code points into their
     * corresponding ASCII-Compatible Encoding (ACE).
     *
     * @param hostname the hostname of this server name
     *
     * @return an {@code SNIHostName} from the specified hostname
     *
     * @throws NullPointerException if {@code hostname} is {@code null}
     * @throws IllegalArgumentException if {@code hostname} is illegal
     *
     * @spec https://www.rfc-editor.org/info/rfc1122
     *       RFC 1122: Requirements for Internet Hosts - Communication Layers
     * @spec https://www.rfc-editor.org/info/rfc1123
     *       RFC 1123: Requirements for Internet Hosts - Application and Support
     * @spec https://www.rfc-editor.org/info/rfc3490
     *       RFC 3490: Internationalizing Domain Names in Applications (IDNA)
     * @spec https://www.rfc-editor.org/info/rfc5280
     *       RFC 5280: Internet X.509 Public Key Infrastructure Certificate and
             Certificate Revocation List (CRL) Profile
     * @spec https://www.rfc-editor.org/info/rfc6066
     *       RFC 6066: Transport Layer Security (TLS) Extensions: Extension Definitions
     *
     * @since 27
     */
    public static SNIHostName ofHostName(String hostname) {
        return new SNIHostName(hostname, true);
    }

    /**
     * Creates an {@code SNIHostName} using the specified encoded value.
     * <P>
     * This method is normally used to parse the encoded name value in a
     * requested SNI extension.
     * <P>
     * Per <A HREF="http://www.ietf.org/rfc/rfc6066.txt">RFC 6066</A>,
     * the encoded name value of a hostname is
     * {@link StandardCharsets#US_ASCII}-compliant.  However, in the previous
     * version of the SNI extension (
     * <A HREF="http://www.ietf.org/rfc/rfc4366.txt">RFC 4366</A>),
     * the encoded hostname is represented as a byte string using UTF-8
     * encoding.  For the purpose of version tolerance, this method allows
     * that the charset of {@code encoded} argument can be
     * {@link StandardCharsets#UTF_8}, as well as
     * {@link StandardCharsets#US_ASCII}.  {@link IDN#toASCII(String)} is used
     * to translate the {@code encoded} argument into ASCII Compatible
     * Encoding (ACE) hostname.
     * <P>
     * It is strongly recommended that this constructor is only used to parse
     * the encoded name value in a requested SNI extension.  Otherwise, to
     * comply with <A HREF="http://www.ietf.org/rfc/rfc6066.txt">RFC 6066</A>,
     * please always use {@link StandardCharsets#US_ASCII}-compliant charset
     * and enforce the restrictions on ASCII characters in hostnames (see
     * <A HREF="http://www.ietf.org/rfc/rfc3490.txt">RFC 3490</A>,
     * <A HREF="http://www.ietf.org/rfc/rfc1122.txt">RFC 1122</A>,
     * <A HREF="http://www.ietf.org/rfc/rfc1123.txt">RFC 1123</A>)
     * for {@code encoded} argument, or use
     * {@link SNIHostName#SNIHostName(String)} instead.
     * <P>
     * The {@code encoded} argument is illegal if it:
     * <ul>
     * <li> {@code encoded} is empty,</li>
     * <li> {@code encoded} ends with a trailing dot,</li>
     * <li> {@code encoded} is not encoded in
     *      {@link StandardCharsets#US_ASCII} or
     *      {@link StandardCharsets#UTF_8}-compliant charset,</li>
     * <li> {@code encoded} is not a valid Internationalized
     *      Domain Name (IDN) compliant with the RFC 3490 specification.</li>
     * </ul>
     *
     * <P>
     * Note that the {@code encoded} byte array is cloned
     * to protect against subsequent modification.
     *
     * @param  encoded
     *         the encoded hostname of this server name
     *
     * @throws NullPointerException if {@code encoded} is {@code null}
     * @throws IllegalArgumentException if {@code encoded} is illegal
     *
     * @spec https://www.rfc-editor.org/info/rfc1122
     *      RFC 1122: Requirements for Internet Hosts - Communication Layers
     * @spec https://www.rfc-editor.org/info/rfc1123
     *      RFC 1123: Requirements for Internet Hosts - Application and Support
     * @spec https://www.rfc-editor.org/info/rfc3490
     *      RFC 3490: Internationalizing Domain Names in Applications (IDNA)
     * @spec https://www.rfc-editor.org/info/rfc4366
     *      RFC 4366: Transport Layer Security (TLS) Extensions
     * @spec https://www.rfc-editor.org/info/rfc6066
     *      RFC 6066: Transport Layer Security (TLS) Extensions: Extension Definitions
     *
     * @deprecated This constructor is not fully aligned with RFC 6066 and does
     * not reject a hostname that is an IP literal address. Use
     * {@link #ofEncoded(byte[]) SNIHostName.ofEncoded()} instead, which
     * performs stricter checks on the provided hostname.
     */
    @Deprecated(since = "27")
    public SNIHostName(byte[] encoded) {
        this(encoded, false);
    }

    private SNIHostName(byte[] encoded, boolean strict) {
        // Clone `encoded` to ensure all use-sites operate on the same content
        var encodedCopy = encoded.clone();      // Implicit null check on `encoded`
        // Note that `encoded` field gets populated using the user-provided
        // value. This is different from `new(String)`, which *first* converts
        // non-ASCII to ACE, and then uses ACE-formatted string to obtain
        // `encoded`. As a result, `getEncoded()` will return different for
        // `new("ëxample.com")` and new("ëxample.com".getBytes(UTF_8))`. This
        // behavior is implemented to tolerate the switch from UTF-8 (RFC 4366)
        // to ASCII (RFC 6066).
        super(StandardConstants.SNI_HOST_NAME, encodedCopy);
        var decoded = decodeHostName(encodedCopy);
        this.hostname = asciifyHostName(decoded, strict);
    }

    private static String decodeHostName(byte[] encoded) {
        // RFC 4366 requires that the hostname is encoded in UTF-8. Though
        // its successor RFC 6066 requires ASCII. To tolerate both, we use
        // UTF-8 for decoding.
        var charset = StandardCharsets.UTF_8;
        // Using a custom decoder with `CodingErrorAction.REPORT` instead of
        // `String::new(byte[], Charset)`, since the latter do not throw
        // on coding failures.
        var decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(ByteBuffer.wrap(encoded)).toString();
        } catch (RuntimeException | CharacterCodingException e) {
            throw new IllegalArgumentException(
                    "The encoded server name value is invalid", e);
        }
    }

    /**
     * Returns an {@code SNIHostName} from the specified hostname encoded in
     * UTF-8.
     * <p>
     * This method decodes the specified bytes into a hostname string. The
     * hostname is a DNS hostname (see <a
     * href="http://www.ietf.org/rfc1123.txt">RFC&nbsp;1123</a> and <a
     * href="http://www.ietf.org/rfc5280.txt">RFC&nbsp;5280</a>) that is either
     * ASCII-encoded or an {@linkplain IDN Internationalized Domain Name (IDN)}.
     * A decoded hostname string is considered illegal if it:
     * <ul>
     * <li>is empty,
     * <li>ends with a trailing dot,
     * <li>is an {@linkplain java.net.InetAddress#ofLiteral(String) IP literal
     * address},
     * <li>or isn't a valid DNS hostname.
     * </ul>
     * <p>
     * Examples of valid SNI hostnames:
     * <ul>
     * <li>{@code "example.com".getBytes(US_ASCII)}
     * <li>{@code "ëxample.com".getBytes(UTF_8)} &mdash; User-friendly IDN
     * containing non-ASCII Unicode code points
     * <li>{@code "xn--xample-ova.com".getBytes(US_ASCII)} &mdash; IDN in
     * ASCII-Compatible Encoding (ACE)
     * </ul>
     *
     * <h4>Non-ASCII Unicode code points</h4>
     *
     * Per <a href="http://www.ietf.org/rfc/rfc6066.txt">RFC&nbsp;6066</a>,
     * the encoded name value of a hostname is encoded in {@linkplain
     * StandardCharsets#US_ASCII ASCII}. However, in the previous version of the
     * SNI extension (<a href="http://www.ietf.org/rfc/rfc4366.txt">RFC&nbsp;4366</a>),
     * the encoded hostname is represented as a UTF-8 byte string. For the
     * purpose of version tolerance, this method allows both ASCII and UTF-8
     * inputs.
     * <p>
     * The specified byte string gets decoded into a UTF-8 hostname string, and
     * {@link IDN#toASCII(String, int) IDN.toASCII(hostname, IDN.USE_STD3_ASCII_RULES)}
     * is used to translate non-ASCII Unicode code points into their
     * corresponding ASCII-Compatible Encoding (ACE).
     *
     * @apiNote
     *
     * This method is intended for parsing the encoded name value in a
     * requested SNI extension. If you already have the hostname in string form,
     * use {@link #ofHostName(String) SNIHostName.ofHostName(String)} instead.
     *
     * @implNote
     *
     * The {@code encoded} byte array is cloned to protect against subsequent
     * modification.
     *
     * @param encoded the encoded hostname of this server name
     *
     * @return an {@code SNIHostName} from the specified hostname encoded in
     *         UTF-8
     *
     * @throws NullPointerException if {@code encoded} is {@code null}
     * @throws IllegalArgumentException if {@code encoded} is illegal
     *
     * @spec https://www.rfc-editor.org/info/rfc1122
     *       RFC 1122: Requirements for Internet Hosts - Communication Layers
     * @spec https://www.rfc-editor.org/info/rfc1123
     *       RFC 1123: Requirements for Internet Hosts - Application and Support
     * @spec https://www.rfc-editor.org/info/rfc3490
     *       RFC 3490: Internationalizing Domain Names in Applications (IDNA)
     * @spec https://www.rfc-editor.org/info/rfc4366
     *       RFC 4366: Transport Layer Security (TLS) Extensions
     * @spec https://www.rfc-editor.org/info/rfc6066
     *       RFC 6066: Transport Layer Security (TLS) Extensions: Extension Definitions
     *
     * @since 27
     */
    public static SNIHostName ofEncoded(byte[] encoded) {
        return new SNIHostName(encoded, true);
    }

    /**
     * Returns the {@link StandardCharsets#US_ASCII}-compliant hostname of
     * this {@code SNIHostName} object.
     * <P>
     * Note that, per
     * <A HREF="http://www.ietf.org/rfc/rfc6066.txt">RFC 6066</A>, the
     * returned hostname may be an internationalized domain name that
     * contains A-labels. See
     * <A HREF="http://www.ietf.org/rfc/rfc5890.txt">RFC 5890</A>
     * for more information about the detailed A-label specification.
     *
     * @return the {@link StandardCharsets#US_ASCII}-compliant hostname
     *         of this {@code SNIHostName} object
     *
     * @spec https://www.rfc-editor.org/info/rfc5890
     *      RFC 5890: Internationalized Domain Names for Applications (IDNA): Definitions and Document Framework
     * @spec https://www.rfc-editor.org/info/rfc6066
     *      RFC 6066: Transport Layer Security (TLS) Extensions: Extension Definitions
     */
    public String getAsciiName() {
        return hostname;
    }

    /**
     * Compares this server name to the specified object.
     * <P>
     * Per <A HREF="http://www.ietf.org/rfc/rfc6066.txt">RFC 6066</A>, DNS
     * hostnames are case-insensitive.  Two server hostnames are equal if,
     * and only if, they have the same name type, and the hostnames are
     * equal in a case-independent comparison.
     *
     * @param  other
     *         the other server name object to compare with.
     * @return true if, and only if, the {@code other} is considered
     *         equal to this instance
     *
     * @spec https://www.rfc-editor.org/info/rfc6066
     *      RFC 6066: Transport Layer Security (TLS) Extensions: Extension Definitions
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other instanceof SNIHostName) {
            return hostname.equalsIgnoreCase(((SNIHostName)other).hostname);
        }

        return false;
    }

    /**
     * Returns a hash code value for this {@code SNIHostName}.
     * <P>
     * The hash code value is generated using the case-insensitive hostname
     * of this {@code SNIHostName}.
     *
     * @return a hash code value for this {@code SNIHostName}.
     */
    @Override
    public int hashCode() {
        int result = 17;        // 17/31: prime number to decrease collisions
        result = 31 * result + hostname.toUpperCase(Locale.ENGLISH).hashCode();

        return result;
    }

    /**
     * Returns a string representation of the object, including the DNS
     * hostname in this {@code SNIHostName} object.
     * <P>
     * The exact details of the representation are unspecified and subject
     * to change, but the following may be regarded as typical:
     * <pre>
     *     "type=host_name (0), value={@literal <hostname>}"
     * </pre>
     * The "{@literal <hostname>}" is an ASCII representation of the hostname,
     * which may contain A-labels.  For example, a returned value of a pseudo
     * hostname may look like:
     * <pre>
     *     "type=host_name (0), value=www.example.com"
     * </pre>
     * or
     * <pre>
     *     "type=host_name (0), value=xn--fsqu00a.xn--0zwm56d"
     * </pre>
     * <P>
     * Please NOTE that the exact details of the representation are unspecified
     * and subject to change.
     *
     * @return a string representation of the object.
     */
    @Override
    public String toString() {
        return "type=host_name (0), value=" + hostname;
    }

    /**
     * Creates an {@link SNIMatcher} object for {@code SNIHostName}s.
     * <P>
     * This method can be used by a server to verify the acceptable
     * {@code SNIHostName}s.  For example,
     * <pre>
     *     SNIMatcher matcher =
     *         SNIHostName.createSNIMatcher("www\\.example\\.com");
     * </pre>
     * will accept the hostname "www.example.com".
     * <pre>
     *     SNIMatcher matcher =
     *         SNIHostName.createSNIMatcher("www\\.example\\.(com|org)");
     * </pre>
     * will accept hostnames "www.example.com" and "www.example.org".
     *
     * @param  regex the {@linkplain Pattern##sum regular expression pattern}
     *         representing the hostname(s) to match
     * @return a {@code SNIMatcher} object for {@code SNIHostName}s
     * @throws NullPointerException if {@code regex} is
     *         {@code null}
     * @throws PatternSyntaxException if the regular expression's
     *         syntax is invalid
     */
    public static SNIMatcher createSNIMatcher(String regex) {
        if (regex == null) {
            throw new NullPointerException(
                "The regular expression cannot be null");
        }

        return new SNIHostNameMatcher(regex);
    }

    private static String asciifyHostName(String hostname, boolean strict) {
        var asciified = IDN.toASCII(hostname, IDN.USE_STD3_ASCII_RULES);
        checkHostName(asciified, strict);
        return asciified;
    }

    /**
     * Validates an SNI {@link #hostname hostname}.
     * <p>
     * A hostname is illegal if it:
     * <ul>
     * <li>is empty,
     * <li>or ends with a dot.
     * </ul>
     * Besides above conditions, when {@code strict} is {@code true}, a hostname
     * is illegal if it
     * <ul>
     * <li>is an {@linkplain java.net.InetAddress#ofLiteral(String) IP
     * literal address}, which is not permitted per <a
     * href="https://www.rfc-editor.org/rfc/rfc6066.html#page-6">RFC 6066</a>,
     * <li>or is an invalid value for the dNSName field of an X.509 certificate.
     * </ul>
     *
     * @param strict Flag to toggle strict checks
     * @throws IllegalArgumentException If the hostname is illegal
     */
    private static void checkHostName(String hostname, boolean strict) {

        // Is it empty?
        if (hostname.isEmpty()) {
            throw new IllegalArgumentException(
                "Server name value of host_name cannot be empty");
        }

        // Does it end with a dot?
        if (hostname.endsWith(".")) {
            throw new IllegalArgumentException(
                "Server name value of host_name cannot have the trailing dot");
        }

        // Stop if strict checks are not requested.
        if (!strict) {
            return;
        }

        // Is it an IP literal address?
        if (isIPv4LiteralAddress(hostname) || isIPv6LiteralAddress(hostname)) {
            throw new IllegalArgumentException(
                    "Server name value of host_name cannot be an IP literal address");
        }

        // Is it a valid dNSName?
        try {
            new DNSName(hostname);
        } catch (IOException ioe) {
            throw new IllegalArgumentException(
                    "Server name value of host_name must be a valid DNSName", ioe);
        }
    }

    private static final class SNIHostNameMatcher extends SNIMatcher {

        // the compiled representation of a regular expression.
        private final Pattern pattern;

        /**
         * Creates an SNIHostNameMatcher object.
         *
         * @param  regex the {@linkplain Pattern##sum regular expression pattern}
         *         representing the hostname(s) to match
         * @throws NullPointerException if {@code regex} is
         *         {@code null}
         * @throws PatternSyntaxException if the regular expression's syntax
         *         is invalid
         */
        SNIHostNameMatcher(String regex) {
            super(StandardConstants.SNI_HOST_NAME);
            pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        }

        /**
         * Attempts to match the given {@link SNIServerName}.
         *
         * @param  serverName
         *         the {@link SNIServerName} instance on which this matcher
         *         performs match operations
         *
         * @return {@code true} if, and only if, the matcher matches the
         *         given {@code serverName}
         *
         * @throws NullPointerException if {@code serverName} is {@code null}
         * @throws IllegalArgumentException if {@code serverName} is
         *         not of {@code StandardConstants#SNI_HOST_NAME} type
         *
         * @see SNIServerName
         */
        @Override
        public boolean matches(SNIServerName serverName) {
            if (serverName == null) {
                throw new NullPointerException(
                    "The SNIServerName argument cannot be null");
            }

            SNIHostName hostname;
            if (!(serverName instanceof SNIHostName)) {
                if (serverName.getType() != StandardConstants.SNI_HOST_NAME) {
                    throw new IllegalArgumentException(
                        "The server name type is not host_name");
                }

                try {
                    hostname = new SNIHostName(serverName.getEncoded());
                } catch (NullPointerException | IllegalArgumentException e) {
                    return false;
                }
            } else {
                hostname = (SNIHostName)serverName;
            }

            // Let's first try the ascii name matching
            String asciiName = hostname.getAsciiName();
            if (pattern.matcher(asciiName).matches()) {
                return true;
            }

            // May be an internationalized domain name, check the Unicode
            // representations.
            return pattern.matcher(IDN.toUnicode(asciiName)).matches();
        }
    }
}
