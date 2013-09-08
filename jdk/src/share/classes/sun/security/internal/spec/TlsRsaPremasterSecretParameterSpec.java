/*
 * Copyright (c) 2005, 2013, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.internal.spec;

import java.security.spec.AlgorithmParameterSpec;

/**
 * Parameters for SSL/TLS RSA Premaster secret generation.
 * This class is used by SSL/TLS client to initialize KeyGenerators of the
 * type "TlsRsaPremasterSecret".
 *
 * <p>Instances of this class are immutable.
 *
 * @since   1.6
 * @author  Andreas Sterbenz
 * @deprecated Sun JDK internal use only --- WILL BE REMOVED in a future
 * release.
 */
@Deprecated
public class TlsRsaPremasterSecretParameterSpec
        implements AlgorithmParameterSpec {

    private final int majorVersion;
    private final int minorVersion;
    private final byte[] encodedSecret;

    /**
     * Constructs a new TlsRsaPremasterSecretParameterSpec.
     * <P>
     * The version numbers will be placed inside the premaster secret to
     * detect version rollbacks attacks as described in the TLS specification.
     * Note that they do not indicate the protocol version negotiated for
     * the handshake.
     *
     * @param majorVersion the major number of the protocol version
     * @param minorVersion the minor number of the protocol version
     *
     * @throws IllegalArgumentException if minorVersion or majorVersion are
     *   negative or larger than 255
     */
    public TlsRsaPremasterSecretParameterSpec(int majorVersion,
            int minorVersion) {
        this.majorVersion =
            TlsMasterSecretParameterSpec.checkVersion(majorVersion);
        this.minorVersion =
            TlsMasterSecretParameterSpec.checkVersion(minorVersion);
        this.encodedSecret = null;
    }

    /**
     * Constructs a new TlsRsaPremasterSecretParameterSpec.
     * <P>
     * The version numbers will be placed inside the premaster secret to
     * detect version rollbacks attacks as described in the TLS specification.
     * Note that they do not indicate the protocol version negotiated for
     * the handshake.
     * <P>
     * Usually, the encoded secret key is a random number that acts as
     * dummy pre_master_secret to avoid vulnerabilities described by
     * section 7.4.7.1, RFC 5246.
     *
     * @param majorVersion the major number of the protocol version
     * @param minorVersion the minor number of the protocol version
     * @param encodedSecret the encoded secret key
     *
     * @throws IllegalArgumentException if minorVersion or majorVersion are
     *   negative or larger than 255, or encodedSecret is not exactly 48 bytes.
     */
    public TlsRsaPremasterSecretParameterSpec(int majorVersion,
            int minorVersion, byte[] encodedSecret) {
        this.majorVersion =
            TlsMasterSecretParameterSpec.checkVersion(majorVersion);
        this.minorVersion =
            TlsMasterSecretParameterSpec.checkVersion(minorVersion);

        if (encodedSecret == null || encodedSecret.length != 48) {
            throw new IllegalArgumentException(
                        "Encoded secret is not exactly 48 bytes");
        }
        this.encodedSecret = encodedSecret.clone();
    }

    /**
     * Returns the major version.
     *
     * @return the major version.
     */
    public int getMajorVersion() {
        return majorVersion;
    }

    /**
     * Returns the minor version.
     *
     * @return the minor version.
     */
    public int getMinorVersion() {
        return minorVersion;
    }

    /**
     * Returns the encoded secret.
     *
     * @return the encoded secret, may be null if no encoded secret.
     */
    public byte[] getEncodedSecret() {
        return encodedSecret == null ? null : encodedSecret.clone();
    }
}
