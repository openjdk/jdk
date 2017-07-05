/*
 * Copyright (c) 2005, 2007, Oracle and/or its affiliates. All rights reserved.
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
 * @deprecated Sun JDK internal use only --- WILL BE REMOVED in Dolphin (JDK 7)
 */
@Deprecated
public class TlsRsaPremasterSecretParameterSpec implements AlgorithmParameterSpec {

    private final int majorVersion;
    private final int minorVersion;

    /**
     * Constructs a new TlsRsaPremasterSecretParameterSpec.
     *
     * <p>The version numbers will be placed inside the premaster secret to
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
    public TlsRsaPremasterSecretParameterSpec(int majorVersion, int minorVersion) {
        this.majorVersion = TlsMasterSecretParameterSpec.checkVersion(majorVersion);
        this.minorVersion = TlsMasterSecretParameterSpec.checkVersion(minorVersion);
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
}
