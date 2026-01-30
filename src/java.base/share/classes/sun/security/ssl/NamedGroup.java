/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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
package sun.security.ssl;

import java.io.IOException;
import java.security.*;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.InvalidParameterSpecException;
import java.security.spec.NamedParameterSpec;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.DHParameterSpec;
import sun.security.ssl.ECDHKeyExchange.ECDHEPossession;
import sun.security.util.CurveDB;

/**
 * An enum containing all known named groups for use in TLS.
 *
 * The enum also contains the required properties of each group and the
 * required functions (e.g. encoding/decoding).
 */
enum NamedGroup {
    // Elliptic Curves (RFC 4492)
    //
    // See sun.security.util.CurveDB for the OIDs
    // NIST K-163

    SECT163_K1(0x0001, "sect163k1",
            NamedGroupSpec.NAMED_GROUP_ECDHE,
            ProtocolVersion.PROTOCOLS_TO_12,
            CurveDB.lookup("sect163k1")),
    SECT163_R1(0x0002, "sect163r1",
            NamedGroupSpec.NAMED_GROUP_ECDHE,
            ProtocolVersion.PROTOCOLS_TO_12,
            CurveDB.lookup("sect163r1")),

    // NIST B-163
    SECT163_R2(0x0003, "sect163r2",
            NamedGroupSpec.NAMED_GROUP_ECDHE,
            ProtocolVersion.PROTOCOLS_TO_12,
            CurveDB.lookup("sect163r2")),
    SECT193_R1(0x0004, "sect193r1",
            NamedGroupSpec.NAMED_GROUP_ECDHE,
            ProtocolVersion.PROTOCOLS_TO_12,
            CurveDB.lookup("sect193r1")),
    SECT193_R2(0x0005, "sect193r2",
            NamedGroupSpec.NAMED_GROUP_ECDHE,
            ProtocolVersion.PROTOCOLS_TO_12,
            CurveDB.lookup("sect193r2")),

    // NIST K-233
    SECT233_K1(0x0006, "sect233k1",
            NamedGroupSpec.NAMED_GROUP_ECDHE,
            ProtocolVersion.PROTOCOLS_TO_12,
            CurveDB.lookup("sect233k1")),

    // NIST B-233
    SECT233_R1(0x0007, "sect233r1",
            NamedGroupSpec.NAMED_GROUP_ECDHE,
            ProtocolVersion.PROTOCOLS_TO_12,
            CurveDB.lookup("sect233r1")),
    SECT239_K1(0x0008, "sect239k1",
            NamedGroupSpec.NAMED_GROUP_ECDHE,
            ProtocolVersion.PROTOCOLS_TO_12,
            CurveDB.lookup("sect239k1")),

    // NIST K-283
    SECT283_K1(0x0009, "sect283k1",
            NamedGroupSpec.NAMED_GROUP_ECDHE,
            ProtocolVersion.PROTOCOLS_TO_12,
            CurveDB.lookup("sect283k1")),

    // NIST B-283
    SECT283_R1(0x000A, "sect283r1",
            NamedGroupSpec.NAMED_GROUP_ECDHE,
            ProtocolVersion.PROTOCOLS_TO_12,
            CurveDB.lookup("sect283r1")),

    // NIST K-409
    SECT409_K1(0x000B, "sect409k1",
            NamedGroupSpec.NAMED_GROUP_ECDHE,
            ProtocolVersion.PROTOCOLS_TO_12,
            CurveDB.lookup("sect409k1")),

    // NIST B-409
    SECT409_R1(0x000C, "sect409r1",
            NamedGroupSpec.NAMED_GROUP_ECDHE,
            ProtocolVersion.PROTOCOLS_TO_12,
            CurveDB.lookup("sect409r1")),

    // NIST K-571
    SECT571_K1(0x000D, "sect571k1",
            NamedGroupSpec.NAMED_GROUP_ECDHE,
            ProtocolVersion.PROTOCOLS_TO_12,
            CurveDB.lookup("sect571k1")),

    // NIST B-571
    SECT571_R1(0x000E, "sect571r1",
            NamedGroupSpec.NAMED_GROUP_ECDHE,
            ProtocolVersion.PROTOCOLS_TO_12,
            CurveDB.lookup("sect571r1")),
    SECP160_K1(0x000F, "secp160k1",
            NamedGroupSpec.NAMED_GROUP_ECDHE,
            ProtocolVersion.PROTOCOLS_TO_12,
            CurveDB.lookup("secp160k1")),
    SECP160_R1(0x0010, "secp160r1",
            NamedGroupSpec.NAMED_GROUP_ECDHE,
            ProtocolVersion.PROTOCOLS_TO_12,
            CurveDB.lookup("secp160r1")),
    SECP160_R2(0x0011, "secp160r2",
            NamedGroupSpec.NAMED_GROUP_ECDHE,
            ProtocolVersion.PROTOCOLS_TO_12,
            CurveDB.lookup("secp160r2")),
    SECP192_K1(0x0012, "secp192k1",
            NamedGroupSpec.NAMED_GROUP_ECDHE,
            ProtocolVersion.PROTOCOLS_TO_12,
            CurveDB.lookup("secp192k1")),

    // NIST P-192
    SECP192_R1(0x0013, "secp192r1",
            NamedGroupSpec.NAMED_GROUP_ECDHE,
            ProtocolVersion.PROTOCOLS_TO_12,
            CurveDB.lookup("secp192r1")),
    SECP224_K1(0x0014, "secp224k1",
            NamedGroupSpec.NAMED_GROUP_ECDHE,
            ProtocolVersion.PROTOCOLS_TO_12,
            CurveDB.lookup("secp224k1")),

    // NIST P-224
    SECP224_R1(0x0015, "secp224r1",
            NamedGroupSpec.NAMED_GROUP_ECDHE,
            ProtocolVersion.PROTOCOLS_TO_12,
            CurveDB.lookup("secp224r1")),
    SECP256_K1(0x0016, "secp256k1",
            NamedGroupSpec.NAMED_GROUP_ECDHE,
            ProtocolVersion.PROTOCOLS_TO_12,
            CurveDB.lookup("secp256k1")),

    // NIST P-256
    SECP256_R1(0x0017, "secp256r1",
            NamedGroupSpec.NAMED_GROUP_ECDHE,
            ProtocolVersion.PROTOCOLS_TO_13,
            CurveDB.lookup("secp256r1")),

    // NIST P-384
    SECP384_R1(0x0018, "secp384r1",
            NamedGroupSpec.NAMED_GROUP_ECDHE,
            ProtocolVersion.PROTOCOLS_TO_13,
            CurveDB.lookup("secp384r1")),

    // NIST P-521
    SECP521_R1(0x0019, "secp521r1",
            NamedGroupSpec.NAMED_GROUP_ECDHE,
            ProtocolVersion.PROTOCOLS_TO_13,
            CurveDB.lookup("secp521r1")),

    // x25519 and x448 (RFC 8422/8446)
    X25519(0x001D, "x25519",
            NamedGroupSpec.NAMED_GROUP_XDH,
            ProtocolVersion.PROTOCOLS_TO_13,
            NamedParameterSpec.X25519),
    X448(0x001E, "x448",
            NamedGroupSpec.NAMED_GROUP_XDH,
            ProtocolVersion.PROTOCOLS_TO_13,
            NamedParameterSpec.X448),

    // Finite Field Diffie-Hellman Ephemeral Parameters (RFC 7919)
    FFDHE_2048(0x0100, "ffdhe2048",
            NamedGroupSpec.NAMED_GROUP_FFDHE,
            ProtocolVersion.PROTOCOLS_TO_13,
            PredefinedDHParameterSpecs.ffdheParams.get(2048)),

    FFDHE_3072(0x0101, "ffdhe3072",
            NamedGroupSpec.NAMED_GROUP_FFDHE,
            ProtocolVersion.PROTOCOLS_TO_13,
            PredefinedDHParameterSpecs.ffdheParams.get(3072)),
    FFDHE_4096(0x0102, "ffdhe4096",
            NamedGroupSpec.NAMED_GROUP_FFDHE,
            ProtocolVersion.PROTOCOLS_TO_13,
            PredefinedDHParameterSpecs.ffdheParams.get(4096)),
    FFDHE_6144(0x0103, "ffdhe6144",
            NamedGroupSpec.NAMED_GROUP_FFDHE,
            ProtocolVersion.PROTOCOLS_TO_13,
            PredefinedDHParameterSpecs.ffdheParams.get(6144)),
    FFDHE_8192(0x0104, "ffdhe8192",
            NamedGroupSpec.NAMED_GROUP_FFDHE,
            ProtocolVersion.PROTOCOLS_TO_13,
            PredefinedDHParameterSpecs.ffdheParams.get(8192)),

    ML_KEM_512(0x0200, "MLKEM512",
            NamedGroupSpec.NAMED_GROUP_KEM,
            ProtocolVersion.PROTOCOLS_OF_13,
            null),

    ML_KEM_768(0x0201, "MLKEM768",
            NamedGroupSpec.NAMED_GROUP_KEM,
            ProtocolVersion.PROTOCOLS_OF_13,
            null),

    ML_KEM_1024(0x0202, "MLKEM1024",
            NamedGroupSpec.NAMED_GROUP_KEM,
            ProtocolVersion.PROTOCOLS_OF_13,
            null),

    X25519MLKEM768(0x11ec, "X25519MLKEM768",
            NamedGroupSpec.NAMED_GROUP_KEM,
            ProtocolVersion.PROTOCOLS_OF_13,
            Hybrid.X25519_MLKEM768,
            HybridProvider.PROVIDER),

    SECP256R1MLKEM768(0x11eb, "SecP256r1MLKEM768",
            NamedGroupSpec.NAMED_GROUP_KEM,
            ProtocolVersion.PROTOCOLS_OF_13,
            Hybrid.SECP256R1_MLKEM768,
            HybridProvider.PROVIDER),

    SECP384R1MLKEM1024(0x11ed, "SecP384r1MLKEM1024",
            NamedGroupSpec.NAMED_GROUP_KEM,
            ProtocolVersion.PROTOCOLS_OF_13,
            Hybrid.SECP384R1_MLKEM1024,
            HybridProvider.PROVIDER),

    // Elliptic Curves (RFC 4492)
    //
    // arbitrary prime and characteristic-2 curves
    ARBITRARY_PRIME(0xFF01, "arbitrary_explicit_prime_curves",
            NamedGroupSpec.NAMED_GROUP_ARBITRARY,
            ProtocolVersion.PROTOCOLS_TO_12,
            null),
    ARBITRARY_CHAR2(0xFF02, "arbitrary_explicit_char2_curves",
            NamedGroupSpec.NAMED_GROUP_ARBITRARY,
            ProtocolVersion.PROTOCOLS_TO_12,
            null);

    final int id;               // hash + signature
    final String name;          // literal name
    final NamedGroupSpec spec;  // group type
    final ProtocolVersion[] supportedProtocols;
    final String algorithm;     // key exchange algorithm
    final AlgorithmParameterSpec keAlgParamSpec;
    final AlgorithmParameters keAlgParams;
    final boolean isAvailable;
    final Provider defaultProvider;

    // performance optimization
    private static final Set<CryptoPrimitive> KEY_AGREEMENT_PRIMITIVE_SET =
        Collections.unmodifiableSet(EnumSet.of(CryptoPrimitive.KEY_AGREEMENT));

    NamedGroup(int id, String name,
            NamedGroupSpec namedGroupSpec,
            ProtocolVersion[] supportedProtocols,
            AlgorithmParameterSpec keAlgParamSpec) {
        this(id, name, namedGroupSpec, supportedProtocols, keAlgParamSpec,
                null);
    }

    // Constructor used for all NamedGroup types
    NamedGroup(int id, String name,
            NamedGroupSpec namedGroupSpec,
            ProtocolVersion[] supportedProtocols,
            AlgorithmParameterSpec keAlgParamSpec,
            Provider defaultProvider) {
        this.id = id;
        this.name = name;
        this.spec = namedGroupSpec;
        this.algorithm = namedGroupSpec.algorithm;
        this.supportedProtocols = supportedProtocols;
        this.keAlgParamSpec = keAlgParamSpec;
        this.defaultProvider = defaultProvider;

        // Check if it is a supported named group.
        AlgorithmParameters algParams = null;
        boolean mediator = (keAlgParamSpec != null);

        // An EC provider, for example the SunEC provider, may support
        // AlgorithmParameters but not KeyPairGenerator or KeyAgreement.
        //
        // Note: Please be careful if removing this block!
        if (mediator && (namedGroupSpec == NamedGroupSpec.NAMED_GROUP_ECDHE)) {
            mediator = JsseJce.isEcAvailable();
        }

        // Check the specific algorithm parameters.
        if (mediator) {
            try {
                // Skip AlgorithmParameters for KEMs (not supported)
                // Check KEM's availability via KeyFactory
                if (namedGroupSpec == NamedGroupSpec.NAMED_GROUP_KEM) {
                    if (defaultProvider == null) {
                        KeyFactory.getInstance(name);
                    } else {
                        KeyFactory.getInstance(name, defaultProvider);
                    }
                } else {
                    // ECDHE or others: use AlgorithmParameters as before
                    algParams = AlgorithmParameters.getInstance(
                            namedGroupSpec.algorithm);
                    algParams.init(keAlgParamSpec);
                }
            } catch (InvalidParameterSpecException
                    | NoSuchAlgorithmException exp) {
                if (namedGroupSpec != NamedGroupSpec.NAMED_GROUP_XDH) {
                    mediator = false;
                    if (SSLLogger.isOn() && SSLLogger.isOn("ssl,handshake")) {
                        SSLLogger.warning(
                            "No AlgorithmParameters or KeyFactory for " + name,
                                exp);
                    }
                } else {
                    // Please remove the following code if the XDH/X25519/X448
                    // AlgorithmParameters algorithms are supported in JDK.
                    //
                    // Note: Please be careful if removing this block!
                    algParams = null;
                    try {
                        KeyAgreement.getInstance(name);

                        // The following service is also needed.  But for
                        // performance, check the KeyAgreement impl only.
                        //
                        // KeyFactory.getInstance(name);
                        // KeyPairGenerator.getInstance(name);
                        // AlgorithmParameters.getInstance(name);
                    } catch (NoSuchAlgorithmException nsae) {
                        mediator = false;
                        if (SSLLogger.isOn() && SSLLogger.isOn("ssl,handshake")) {
                            SSLLogger.warning(
                                "No AlgorithmParameters for " + name, nsae);
                        }
                    }
                }
            }
        }

        this.isAvailable = mediator;
        this.keAlgParams = mediator ? algParams : null;
    }

    Provider getProvider() {
        return defaultProvider;
    }

    //
    // The next set of methods search & retrieve NamedGroups.
    //
    static NamedGroup valueOf(int id) {
        for (NamedGroup group : NamedGroup.values()) {
            if (group.id == id) {
                return group;
            }
        }

        return null;
    }

    static NamedGroup valueOf(ECParameterSpec params) {
        for (NamedGroup ng : NamedGroup.values()) {
            if (ng.spec == NamedGroupSpec.NAMED_GROUP_ECDHE) {
                if ((params == ng.keAlgParamSpec) ||
                        (ng.keAlgParamSpec == CurveDB.lookup(params))) {
                    return ng;
                }
            }
        }

        return null;
    }

    static NamedGroup valueOf(DHParameterSpec params) {
        for (NamedGroup ng : NamedGroup.values()) {
            if (ng.spec != NamedGroupSpec.NAMED_GROUP_FFDHE) {
                continue;
            }

            DHParameterSpec ngParams = (DHParameterSpec)ng.keAlgParamSpec;
            if (ngParams.getP().equals(params.getP())
                    && ngParams.getG().equals(params.getG())) {
                return ng;
            }
        }

        return null;
    }

    static NamedGroup nameOf(String name) {
        for (NamedGroup group : NamedGroup.values()) {
            if (group.name.equalsIgnoreCase(name)) {
                return group;
            }
        }

        return null;
    }

    static String nameOf(int id) {
        for (NamedGroup group : NamedGroup.values()) {
            if (group.id == id) {
                return group.name;
            }
        }

        return "UNDEFINED-NAMED-GROUP(" + id + ")";
    }

    public static List<NamedGroup> namesOf(String[] namedGroups) {
        if (namedGroups == null) {
            return null;
        }

        if (namedGroups.length == 0) {
            return List.of();
        }

        List<NamedGroup> ngs = new ArrayList<>(namedGroups.length);
        for (String ss : namedGroups) {
            NamedGroup ng = NamedGroup.nameOf(ss);
            if (ng == null || !ng.isAvailable) {
                if (SSLLogger.isOn() &&
                        SSLLogger.isOn("ssl,handshake,verbose")) {
                    SSLLogger.finest(
                            "Ignore the named group (" + ss
                                    + "), unsupported or unavailable");
                }

                continue;
            }

            ngs.add(ng);
        }

        return Collections.unmodifiableList(ngs);
    }

    // Is there any supported group permitted by the constraints?
    static boolean isActivatable(SSLConfiguration sslConfig,
            AlgorithmConstraints constraints, NamedGroupSpec type) {

        boolean hasFFDHEGroups = false;
        for (NamedGroup namedGroup :
                SupportedGroups.getGroupsFromConfig(sslConfig)) {
            if (namedGroup.isAvailable && namedGroup.spec == type) {
                if (namedGroup.isPermitted(constraints)) {
                    return true;
                }

                if (!hasFFDHEGroups &&
                        (type == NamedGroupSpec.NAMED_GROUP_FFDHE)) {
                    hasFFDHEGroups = true;
                }
            }
        }

        // For compatibility, if no FFDHE groups are defined, the non-FFDHE
        // compatible mode (using DHE cipher suite without FFDHE extension)
        // is allowed.
        //
        // Note that the constraints checking on DHE parameters will be
        // performed during key exchanging in a handshake.
        return !hasFFDHEGroups && type == NamedGroupSpec.NAMED_GROUP_FFDHE;
    }

    // Is the named group permitted by the constraints?
    static boolean isActivatable(
            SSLConfiguration sslConfig,
            AlgorithmConstraints constraints, NamedGroup namedGroup) {
        if (!namedGroup.isAvailable || !isEnabled(sslConfig, namedGroup)) {
            return false;
        }

        return namedGroup.isPermitted(constraints);
    }

    // Is the named group supported?
    static boolean isEnabled(SSLConfiguration sslConfig,
                             NamedGroup namedGroup) {
        for (NamedGroup ng : SupportedGroups.getGroupsFromConfig(sslConfig)) {
            if (namedGroup.equals(ng)) {
                return true;
            }
        }

        return false;
    }

    // Get preferred named group from the configured named groups for the
    // negotiated protocol and named group types.
    static NamedGroup getPreferredGroup(
            SSLConfiguration sslConfig,
            ProtocolVersion negotiatedProtocol,
            AlgorithmConstraints constraints, NamedGroupSpec[] types) {
        for (NamedGroup ng : SupportedGroups.getGroupsFromConfig(sslConfig)) {
            if (ng.isAvailable && NamedGroupSpec.arrayContains(types, ng.spec)
                    && ng.isAvailable(negotiatedProtocol)
                    && ng.isPermitted(constraints)) {
                return ng;
            }
        }

        return null;
    }

    // Get preferred named group from the requested and configured named
    // groups for the negotiated protocol and named group types.
    static NamedGroup getPreferredGroup(
            SSLConfiguration sslConfig,
            ProtocolVersion negotiatedProtocol,
            AlgorithmConstraints constraints, NamedGroupSpec[] types,
            List<NamedGroup> requestedNamedGroups) {
        for (NamedGroup namedGroup : requestedNamedGroups) {
            if ((namedGroup.isAvailable &&
                    NamedGroupSpec.arrayContains(types, namedGroup.spec)) &&
                    namedGroup.isAvailable(negotiatedProtocol) &&
                    isEnabled(sslConfig, namedGroup) &&
                    namedGroup.isPermitted(constraints)) {
                return namedGroup;
            }
        }

        return null;
    }

    // Is the NamedGroup available for the protocols desired?
    boolean isAvailable(List<ProtocolVersion> protocolVersions) {
        if (this.isAvailable) {
            for (ProtocolVersion pv : supportedProtocols) {
                if (protocolVersions.contains(pv)) {
                    return true;
                }
            }
        }

        return false;
    }

    boolean isAvailable(ProtocolVersion protocolVersion) {
        if (this.isAvailable) {
            for (ProtocolVersion pv : supportedProtocols) {
                if (protocolVersion == pv) {
                    return true;
                }
            }
        }

        return false;
    }

    // Are the NamedGroups available for the ciphersuites desired?
    boolean isSupported(List<CipherSuite> cipherSuites) {
        for (CipherSuite cs : cipherSuites) {
            boolean isMatch = isAvailable(cs.supportedProtocols);
            if (isMatch && ((cs.keyExchange == null)
                    || (NamedGroupSpec.arrayContains(
                            cs.keyExchange.groupTypes, spec)))) {
                return true;
            }
        }

        return false;
    }

    boolean isPermitted(AlgorithmConstraints constraints) {
        return constraints.permits(KEY_AGREEMENT_PRIMITIVE_SET,
                        this.name, null) &&
                constraints.permits(KEY_AGREEMENT_PRIMITIVE_SET,
                        this.algorithm, this.keAlgParams);
    }

    byte[] encodePossessionPublicKey(
            NamedGroupPossession namedGroupPossession) {
        return spec.encodePossessionPublicKey(namedGroupPossession);
    }

    SSLCredentials decodeCredentials(
            byte[] encoded) throws IOException, GeneralSecurityException {
        return spec.decodeCredentials(this, encoded);
    }

    SSLPossession createPossession(boolean isClient, SecureRandom random) {
        return spec.createPossession(this, isClient, random);
    }

    SSLPossession createPossession(SecureRandom random) {
        return spec.createPossession(this, random);
    }

    SSLKeyDerivation createKeyDerivation(
            HandshakeContext hc) throws IOException {
        return spec.createKeyDerivation(hc);
    }

    // A list of operations related to named groups.
    private interface NamedGroupScheme {
        byte[] encodePossessionPublicKey(
                NamedGroupPossession namedGroupPossession);

        SSLCredentials decodeCredentials(NamedGroup ng,
                byte[] encoded) throws IOException, GeneralSecurityException;

        SSLPossession createPossession(NamedGroup ng, SecureRandom random);

        SSLKeyDerivation createKeyDerivation(
                HandshakeContext hc) throws IOException;

        default SSLPossession createPossession(NamedGroup ng, boolean isClient,
                SecureRandom random) {
            return createPossession(ng, random);
        }
    }

    enum NamedGroupSpec implements NamedGroupScheme {
        // Elliptic Curve Groups (ECDHE)
        NAMED_GROUP_ECDHE("EC", ECDHEScheme.instance),

        // Finite Field Groups (DHE)
        NAMED_GROUP_FFDHE("DiffieHellman", FFDHEScheme.instance),

        // Finite Field Groups (XDH)
        NAMED_GROUP_XDH("XDH", XDHScheme.instance),

        // Post-Quantum Cryptography (PQC) KEM groups
        // Currently used for hybrid named groups
        NAMED_GROUP_KEM("KEM", KEMScheme.instance),

        // arbitrary prime and curves (ECDHE)
        NAMED_GROUP_ARBITRARY("EC", null),

        // Not predefined named group
        NAMED_GROUP_NONE("", null);

        private final String algorithm;     // key exchange name
        private final NamedGroupScheme scheme;  // named group operations

        NamedGroupSpec(String algorithm, NamedGroupScheme scheme) {
            this.algorithm = algorithm;
            this.scheme = scheme;
        }

        boolean isSupported(List<CipherSuite> cipherSuites) {
            for (CipherSuite cs : cipherSuites) {
                if (cs.keyExchange == null ||
                        arrayContains(cs.keyExchange.groupTypes, this)) {
                    return true;
                }
            }

            return false;
        }

        static boolean arrayContains(NamedGroupSpec[] namedGroupTypes,
                NamedGroupSpec namedGroupType) {
            for (NamedGroupSpec ng : namedGroupTypes) {
                if (ng == namedGroupType) {
                    return true;
                }
            }

            return false;
        }

        @Override
        public byte[] encodePossessionPublicKey(
                NamedGroupPossession namedGroupPossession) {
            if (scheme != null) {
                return scheme.encodePossessionPublicKey(namedGroupPossession);
            }

            return null;
        }

        @Override
        public SSLCredentials decodeCredentials(NamedGroup ng,
                byte[] encoded) throws IOException, GeneralSecurityException {
            if (scheme != null) {
                return scheme.decodeCredentials(ng, encoded);
            }

            return null;
        }

        public SSLPossession createPossession(
                NamedGroup ng, boolean isClient, SecureRandom random) {
            if (scheme != null) {
                return scheme.createPossession(ng, isClient, random);
            }

            return null;
        }

        @Override
        public SSLPossession createPossession(
                NamedGroup ng, SecureRandom random) {
            if (scheme != null) {
                return scheme.createPossession(ng, random);
            }

            return null;
        }

        @Override
        public SSLKeyDerivation createKeyDerivation(
                HandshakeContext hc) throws IOException {
            if (scheme != null) {
                return scheme.createKeyDerivation(hc);
            }

            return null;
        }
    }

    private static class FFDHEScheme implements NamedGroupScheme {
        private static final FFDHEScheme instance = new FFDHEScheme();

        @Override
        public byte[] encodePossessionPublicKey(
                NamedGroupPossession namedGroupPossession) {
            return namedGroupPossession.encode();
        }

        @Override
        public SSLCredentials decodeCredentials(NamedGroup ng,
                byte[] encoded) throws IOException, GeneralSecurityException {
            return DHKeyExchange.DHECredentials.valueOf(ng, encoded);
        }

        @Override
        public SSLPossession createPossession(
                NamedGroup ng, SecureRandom random) {
            return new DHKeyExchange.DHEPossession(ng, random);
        }

        @Override
        public SSLKeyDerivation createKeyDerivation(
                HandshakeContext hc) throws IOException {

            return DHKeyExchange.kaGenerator.createKeyDerivation(hc);
        }
    }

    private static class ECDHEScheme implements NamedGroupScheme {
        private static final ECDHEScheme instance = new ECDHEScheme();

        @Override
        public byte[] encodePossessionPublicKey(
                NamedGroupPossession namedGroupPossession) {
            return ((ECDHEPossession)namedGroupPossession).encode();
        }

        @Override
        public SSLCredentials decodeCredentials(NamedGroup ng,
                byte[] encoded) throws IOException, GeneralSecurityException {
            return ECDHKeyExchange.ECDHECredentials.valueOf(ng, encoded);
        }

        @Override
        public SSLPossession createPossession(
                NamedGroup ng, SecureRandom random) {
            return new ECDHKeyExchange.ECDHEPossession(ng, random);
        }

        @Override
        public SSLKeyDerivation createKeyDerivation(
                HandshakeContext hc) throws IOException {
            return ECDHKeyExchange.ecdheKAGenerator.createKeyDerivation(hc);
        }
    }

    private static class XDHScheme implements NamedGroupScheme {
        private static final XDHScheme instance = new XDHScheme();

        @Override
        public byte[] encodePossessionPublicKey(NamedGroupPossession poss) {
            return ((XDHKeyExchange.XDHEPossession)poss).encode();
        }

        @Override
        public SSLCredentials decodeCredentials(NamedGroup ng,
                byte[] encoded) throws IOException, GeneralSecurityException {
            return XDHKeyExchange.XDHECredentials.valueOf(ng, encoded);
        }

        @Override
        public SSLPossession createPossession(
                NamedGroup ng, SecureRandom random) {
            return new XDHKeyExchange.XDHEPossession(ng, random);
        }

        @Override
        public SSLKeyDerivation createKeyDerivation(
                HandshakeContext hc) throws IOException {
            return XDHKeyExchange.xdheKAGenerator.createKeyDerivation(hc);
        }
    }

    private static class KEMScheme implements NamedGroupScheme {
        private static final KEMScheme instance = new KEMScheme();

        @Override
        public byte[] encodePossessionPublicKey(NamedGroupPossession poss) {
            return poss.encode();
        }

        @Override
        public SSLCredentials decodeCredentials(NamedGroup ng,
                byte[] encoded) throws IOException, GeneralSecurityException {
            return KEMKeyExchange.KEMCredentials.valueOf(ng, encoded);
        }

        @Override
        public SSLPossession createPossession(NamedGroup ng,
                SecureRandom random) {
            // Must call createPossession with isClient
            throw new UnsupportedOperationException();
        }

        @Override
        public SSLPossession createPossession(
                NamedGroup ng, boolean isClient, SecureRandom random) {
            return isClient
                    ? new KEMKeyExchange.KEMReceiverPossession(ng, random)
                    : new KEMKeyExchange.KEMSenderPossession(ng, random);
        }

        @Override
        public SSLKeyDerivation createKeyDerivation(
                HandshakeContext hc) throws IOException {
            return KEMKeyExchange.kemKAGenerator.createKeyDerivation(hc);
        }
    }

    // Inner class encapsulating supported named groups.
    static final class SupportedGroups {

        // Default named groups.
        private static final NamedGroup[] defaultGroups = new NamedGroup[]{
                // Hybrid key agreement
                X25519MLKEM768,

                // Primary XDH (RFC 7748) curves
                X25519,

                // Primary NIST Suite B curves
                SECP256_R1,
                SECP384_R1,
                SECP521_R1,

                // Secondary XDH curves
                X448,

                // FFDHE (RFC 7919)
                FFDHE_2048,
                FFDHE_3072,
                FFDHE_4096,
                FFDHE_6144,
                FFDHE_8192
        };

        // Filter default groups names against default constraints.
        // Those are the values being displayed to the user with
        // "java -XshowSettings:security:tls" command.
        private static final String[] defaultNames = Arrays.stream(
                        defaultGroups)
                .filter(ng -> ng.isAvailable)
                .filter(ng -> ng.isPermitted(SSLAlgorithmConstraints.DEFAULT))
                .map(ng -> ng.name)
                .toArray(String[]::new);

        private static final NamedGroup[] customizedGroups =
                getCustomizedNamedGroups();

        // Note: user-passed groups are not being filtered against default
        // algorithm constraints here. They will be displayed as-is.
        private static final String[] customizedNames =
                customizedGroups == null ?
                        null : Arrays.stream(customizedGroups)
                        .map(ng -> ng.name)
                        .toArray(String[]::new);

        // Named group names for SSLConfiguration.
        static final String[] namedGroups;

        static {
            if (customizedNames != null) {
                namedGroups = customizedNames;
            } else {
                if (defaultNames.length == 0) {
                    SSLLogger.logWarning("ssl", "No default named groups");
                }
                namedGroups = defaultNames;
            }
        }

        // Avoid the group lookup for default and customized groups.
        static NamedGroup[] getGroupsFromConfig(SSLConfiguration sslConfig) {
            if (sslConfig.namedGroups == defaultNames) {
                return defaultGroups;
            } else if (sslConfig.namedGroups == customizedNames) {
                return customizedGroups;
            } else {
                return Arrays.stream(sslConfig.namedGroups)
                        .map(NamedGroup::nameOf)
                        .filter(Objects::nonNull)
                        .toArray(NamedGroup[]::new);
            }
        }

        // The value of the System Property defines a list of enabled named
        // groups in preference order, separated with comma.  For example:
        //
        //      jdk.tls.namedGroups="secp521r1, secp256r1, ffdhe2048"
        //
        // If the System Property is not defined or the value is empty, the
        // default groups and preferences will be used.
        private static NamedGroup[] getCustomizedNamedGroups() {
            String property = System.getProperty("jdk.tls.namedGroups");

            if (property != null && !property.isEmpty()) {
                // remove double quote marks from beginning/end of the property
                if (property.length() > 1 && property.charAt(0) == '"' &&
                        property.charAt(property.length() - 1) == '"') {
                    property = property.substring(1, property.length() - 1);
                }
            }

            if (property != null && !property.isEmpty()) {
                NamedGroup[] ret = Arrays.stream(property.split(","))
                        .map(String::trim)
                        .map(NamedGroup::nameOf)
                        .filter(Objects::nonNull)
                        .filter(ng -> ng.isAvailable)
                        .toArray(NamedGroup[]::new);

                if (ret.length == 0) {
                    throw new IllegalArgumentException(
                            "System property jdk.tls.namedGroups(" +
                                    property
                                    + ") contains no supported named groups");
                }

                return ret;
            }

            return null;
        }
    }
}
