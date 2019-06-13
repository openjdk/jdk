/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import javax.crypto.spec.DHParameterSpec;
import javax.net.ssl.SSLException;
import java.io.IOException;
import java.security.*;
import java.security.spec.*;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.*;
import sun.security.ssl.DHKeyExchange.DHEPossession;
import sun.security.ssl.ECDHKeyExchange.ECDHEPossession;

import sun.security.util.ECUtil;

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

    SECT163_K1(0x0001, "sect163k1", "1.3.132.0.1",
            NamedGroupType.NAMED_GROUP_ECDHE,
            ProtocolVersion.PROTOCOLS_TO_12),
    SECT163_R1(0x0002, "sect163r1", "1.3.132.0.2",
            NamedGroupType.NAMED_GROUP_ECDHE,
            ProtocolVersion.PROTOCOLS_TO_12),

    // NIST B-163
    SECT163_R2(0x0003, "sect163r2", "1.3.132.0.15",
            NamedGroupType.NAMED_GROUP_ECDHE,
            ProtocolVersion.PROTOCOLS_TO_12),
    SECT193_R1(0x0004, "sect193r1", "1.3.132.0.24",
            NamedGroupType.NAMED_GROUP_ECDHE,
            ProtocolVersion.PROTOCOLS_TO_12),
    SECT193_R2(0x0005, "sect193r2", "1.3.132.0.25",
            NamedGroupType.NAMED_GROUP_ECDHE,
            ProtocolVersion.PROTOCOLS_TO_12),

    // NIST K-233
    SECT233_K1(0x0006, "sect233k1", "1.3.132.0.26",
            NamedGroupType.NAMED_GROUP_ECDHE,
            ProtocolVersion.PROTOCOLS_TO_12),

    // NIST B-233
    SECT233_R1(0x0007, "sect233r1", "1.3.132.0.27",
            NamedGroupType.NAMED_GROUP_ECDHE,
            ProtocolVersion.PROTOCOLS_TO_12),
    SECT239_K1(0x0008, "sect239k1", "1.3.132.0.3",
            NamedGroupType.NAMED_GROUP_ECDHE,
            ProtocolVersion.PROTOCOLS_TO_12),

    // NIST K-283
    SECT283_K1(0x0009, "sect283k1", "1.3.132.0.16",
            NamedGroupType.NAMED_GROUP_ECDHE,
            ProtocolVersion.PROTOCOLS_TO_12),

    // NIST B-283
    SECT283_R1(0x000A, "sect283r1", "1.3.132.0.17",
            NamedGroupType.NAMED_GROUP_ECDHE,
            ProtocolVersion.PROTOCOLS_TO_12),

    // NIST K-409
    SECT409_K1(0x000B, "sect409k1", "1.3.132.0.36",
            NamedGroupType.NAMED_GROUP_ECDHE,
            ProtocolVersion.PROTOCOLS_TO_12),

    // NIST B-409
    SECT409_R1(0x000C, "sect409r1", "1.3.132.0.37",
            NamedGroupType.NAMED_GROUP_ECDHE,
            ProtocolVersion.PROTOCOLS_TO_12),

    // NIST K-571
    SECT571_K1(0x000D, "sect571k1", "1.3.132.0.38",
            NamedGroupType.NAMED_GROUP_ECDHE,
            ProtocolVersion.PROTOCOLS_TO_12),

    // NIST B-571
    SECT571_R1(0x000E, "sect571r1", "1.3.132.0.39",
            NamedGroupType.NAMED_GROUP_ECDHE,
            ProtocolVersion.PROTOCOLS_TO_12),
    SECP160_K1(0x000F, "secp160k1", "1.3.132.0.9",
            NamedGroupType.NAMED_GROUP_ECDHE,
            ProtocolVersion.PROTOCOLS_TO_12),
    SECP160_R1(0x0010, "secp160r1", "1.3.132.0.8",
            NamedGroupType.NAMED_GROUP_ECDHE,
            ProtocolVersion.PROTOCOLS_TO_12),
    SECP160_R2(0x0011, "secp160r2", "1.3.132.0.30",
            NamedGroupType.NAMED_GROUP_ECDHE,
            ProtocolVersion.PROTOCOLS_TO_12),
    SECP192_K1(0x0012, "secp192k1", "1.3.132.0.31",
            NamedGroupType.NAMED_GROUP_ECDHE,
            ProtocolVersion.PROTOCOLS_TO_12),

    // NIST P-192
    SECP192_R1(0x0013, "secp192r1", "1.2.840.10045.3.1.1",
            NamedGroupType.NAMED_GROUP_ECDHE,
            ProtocolVersion.PROTOCOLS_TO_12),
    SECP224_K1(0x0014, "secp224k1", "1.3.132.0.32",
            NamedGroupType.NAMED_GROUP_ECDHE,
            ProtocolVersion.PROTOCOLS_TO_12),

    // NIST P-224
    SECP224_R1(0x0015, "secp224r1", "1.3.132.0.33",
            NamedGroupType.NAMED_GROUP_ECDHE,
            ProtocolVersion.PROTOCOLS_TO_12),
    SECP256_K1(0x0016, "secp256k1", "1.3.132.0.10",
            NamedGroupType.NAMED_GROUP_ECDHE,
            ProtocolVersion.PROTOCOLS_TO_12),

    // NIST P-256
    SECP256_R1(0x0017, "secp256r1", "1.2.840.10045.3.1.7",
            NamedGroupType.NAMED_GROUP_ECDHE,
            ProtocolVersion.PROTOCOLS_TO_13),

    // NIST P-384
    SECP384_R1(0x0018, "secp384r1", "1.3.132.0.34",
            NamedGroupType.NAMED_GROUP_ECDHE,
            ProtocolVersion.PROTOCOLS_TO_13),

    // NIST P-521
    SECP521_R1(0x0019, "secp521r1", "1.3.132.0.35",
            NamedGroupType.NAMED_GROUP_ECDHE,
            ProtocolVersion.PROTOCOLS_TO_13),

    // x25519 and x448 (RFC 8422/8446)
    X25519(0x001D, "x25519", "1.3.101.110",
            NamedGroupType.NAMED_GROUP_XDH,
            ProtocolVersion.PROTOCOLS_TO_13),
    X448(0x001E, "x448", "1.3.101.111",
            NamedGroupType.NAMED_GROUP_XDH,
            ProtocolVersion.PROTOCOLS_TO_13),

    // Finite Field Diffie-Hellman Ephemeral Parameters (RFC 7919)
    FFDHE_2048(0x0100, "ffdhe2048", null,
            NamedGroupType.NAMED_GROUP_FFDHE,
            ProtocolVersion.PROTOCOLS_TO_13),
    FFDHE_3072(0x0101, "ffdhe3072", null,
            NamedGroupType.NAMED_GROUP_FFDHE,
            ProtocolVersion.PROTOCOLS_TO_13),
    FFDHE_4096(0x0102, "ffdhe4096", null,
            NamedGroupType.NAMED_GROUP_FFDHE,
            ProtocolVersion.PROTOCOLS_TO_13),
    FFDHE_6144(0x0103, "ffdhe6144", null,
            NamedGroupType.NAMED_GROUP_FFDHE,
            ProtocolVersion.PROTOCOLS_TO_13),
    FFDHE_8192(0x0104, "ffdhe8192", null,
            NamedGroupType.NAMED_GROUP_FFDHE,
            ProtocolVersion.PROTOCOLS_TO_13),

    // Elliptic Curves (RFC 4492)
    //
    // arbitrary prime and characteristic-2 curves
    ARBITRARY_PRIME(0xFF01, "arbitrary_explicit_prime_curves", null,
            NamedGroupType.NAMED_GROUP_ARBITRARY,
            ProtocolVersion.PROTOCOLS_TO_12),
    ARBITRARY_CHAR2(0xFF02, "arbitrary_explicit_char2_curves", null,
            NamedGroupType.NAMED_GROUP_ARBITRARY,
            ProtocolVersion.PROTOCOLS_TO_12);

    final int id;               // hash + signature
    final NamedGroupType type;  // group type
    final String name;          // literal name
    final String oid;           // object identifier of the named group
    final String algorithm;     // signature algorithm
    final ProtocolVersion[] supportedProtocols;
    private final NamedGroupFunctions functions;    // may be null

    // Constructor used for all NamedGroup types
    private NamedGroup(int id, String name, String oid,
            NamedGroupType namedGroupType,
            ProtocolVersion[] supportedProtocols) {
        this.id = id;
        this.name = name;
        this.oid = oid;
        this.type = namedGroupType;
        this.supportedProtocols = supportedProtocols;

        if (this.type == NamedGroupType.NAMED_GROUP_ECDHE) {
            this.functions = ECDHFunctions.getInstance();
            this.algorithm = "EC";
        } else if (this.type == NamedGroupType.NAMED_GROUP_FFDHE) {
            this.functions = FFDHFunctions.getInstance();
            this.algorithm = "DiffieHellman";
        } else if (this.type == NamedGroupType.NAMED_GROUP_XDH) {
            this.functions = XDHFunctions.getInstance();
            this.algorithm = "XDH";
        } else if (this.type == NamedGroupType.NAMED_GROUP_ARBITRARY) {
            this.functions = null;
            this.algorithm = "EC";
        } else {
            throw new RuntimeException("Unexpected Named Group Type");
        }
    }

    private Optional<NamedGroupFunctions> getFunctions() {
        return Optional.ofNullable(functions);
    }

    // The next set of methods search & retrieve NamedGroups.

    static NamedGroup valueOf(int id) {
        for (NamedGroup group : NamedGroup.values()) {
            if (group.id == id) {
                return group;
            }
        }

        return null;
    }

    static NamedGroup valueOf(ECParameterSpec params) {
        String oid = ECUtil.getCurveName(null, params);
        if ((oid != null) && (!oid.isEmpty())) {
            for (NamedGroup group : NamedGroup.values()) {
                if ((group.type == NamedGroupType.NAMED_GROUP_ECDHE)
                        && oid.equals(group.oid)) {
                    return group;
                }
            }
        }

        return null;
    }

    static NamedGroup valueOf(DHParameterSpec params) {
        for (NamedGroup ng : NamedGroup.values()) {
            if (ng.type != NamedGroupType.NAMED_GROUP_FFDHE) {
                continue;
            }

            DHParameterSpec ngParams = null;
            // functions is non-null for FFDHE type
            AlgorithmParameters aps = ng.functions.getParameters(ng);
            try {
                ngParams = aps.getParameterSpec(DHParameterSpec.class);
            } catch (InvalidParameterSpecException ipse) {
                // should be unlikely
            }

            if (ngParams == null) {
                continue;
            }

            if (ngParams.getP().equals(params.getP())
                    && ngParams.getG().equals(params.getG())) {
                return ng;
            }
        }

        return null;
    }

    static NamedGroup nameOf(String name) {
        for (NamedGroup group : NamedGroup.values()) {
            if (group.name.equals(name)) {
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

    // Are the NamedGroups available for the protocol desired?

    boolean isAvailable(List<ProtocolVersion> protocolVersions) {
        for (ProtocolVersion pv : supportedProtocols) {
            if (protocolVersions.contains(pv)) {
                return true;
            }
        }
        return false;
    }

    boolean isAvailable(ProtocolVersion protocolVersion) {
        for (ProtocolVersion pv : supportedProtocols) {
            if (protocolVersion == pv) {
                return true;
            }
        }
        return false;
    }

    // Are the NamedGroups available for the ciphersuites desired?

    boolean isSupported(List<CipherSuite> cipherSuites) {
        for (CipherSuite cs : cipherSuites) {
            boolean isMatch = isAvailable(cs.supportedProtocols);
            if (isMatch && ((cs.keyExchange == null)
                    || (NamedGroupType.arrayContains(
                        cs.keyExchange.groupTypes, type)))) {
                return true;
            }
        }
        return false;
    }

    // lazy loading of parameters
    AlgorithmParameters getParameters() {
        Optional<NamedGroupFunctions> ngf = getFunctions();
        if (ngf.isEmpty()) {
            return null;
        }
        return ngf.get().getParameters(this);
    }

    // The next set of methods use the NamedGroupFunctions table
    // to do various operations in a consistent way.

    AlgorithmParameterSpec getParameterSpec() {
        Optional<NamedGroupFunctions> ngf = getFunctions();
        if (ngf.isEmpty()) {
            return null;
        }
        return ngf.get().getParameterSpec(this);
    }

    byte[] encodePossessionPublicKey(
            NamedGroupPossession namedGroupPossession) {

        Optional<NamedGroupFunctions> ngf = getFunctions();
        if (ngf.isEmpty()) {
            return null;
        }
        return ngf.get().encodePossessionPublicKey(namedGroupPossession);
    }

    SSLCredentials decodeCredentials(byte[] encoded,
            AlgorithmConstraints constraints,
            ExceptionSupplier onConstraintFail)
            throws IOException, GeneralSecurityException {

        Optional<NamedGroupFunctions> ngf = getFunctions();
        if (ngf.isEmpty()) {
            return null;
        }
        return ngf.get().decodeCredentials(this, encoded, constraints,
                onConstraintFail);
    }

    SSLPossession createPossession(SecureRandom random) {

        Optional<NamedGroupFunctions> ngf = getFunctions();
        if (ngf.isEmpty()) {
            return null;
        }
        return ngf.get().createPossession(this, random);
    }

    SSLKeyDerivation createKeyDerivation(HandshakeContext hc)
            throws IOException {

        Optional<NamedGroupFunctions> ngf = getFunctions();
        if (ngf.isEmpty()) {
            return null;
        }
        return ngf.get().createKeyDerivation(hc);

    }

    boolean isAvailableGroup() {
        Optional<NamedGroupFunctions> ngfOpt = getFunctions();
        if (ngfOpt.isEmpty()) {
            return false;
        }
        NamedGroupFunctions ngf = ngfOpt.get();
        return ngf.isAvailable(this);
    }

    enum NamedGroupType {
        NAMED_GROUP_ECDHE,      // Elliptic Curve Groups (ECDHE)
        NAMED_GROUP_FFDHE,      // Finite Field Groups (DHE)
        NAMED_GROUP_XDH,        // Finite Field Groups (XDH)
        NAMED_GROUP_ARBITRARY,  // arbitrary prime and curves (ECDHE)
        NAMED_GROUP_NONE;       // Not predefined named group

        boolean isSupported(List<CipherSuite> cipherSuites) {
            for (CipherSuite cs : cipherSuites) {
                if (cs.keyExchange == null ||
                        arrayContains(cs.keyExchange.groupTypes, this)) {
                    return true;
                }
            }

            return false;
        }

        static boolean arrayContains(NamedGroupType[] namedGroupTypes,
                NamedGroupType namedGroupType) {
            for (NamedGroupType ng : namedGroupTypes) {
                if (ng == namedGroupType) {
                    return true;
                }
            }
            return false;
        }
    }

    interface ExceptionSupplier {
        void apply(String s) throws SSLException;
    }

    /*
     * A list of functions to do NamedGroup operations in a
     * algorithm-independent and consistent way.
     */
    private static abstract class NamedGroupFunctions {

        // cache to speed up the parameters construction
        protected static final Map<NamedGroup, AlgorithmParameters>
                namedGroupParams = new ConcurrentHashMap<>();

        protected void checkConstraints(PublicKey publicKey,
                AlgorithmConstraints constraints,
                ExceptionSupplier onConstraintFail)
                throws SSLException {

            if (!constraints.permits(
                    EnumSet.of(CryptoPrimitive.KEY_AGREEMENT),
                    publicKey)) {

                onConstraintFail.apply("key share entry does not "
                        + "comply with algorithm constraints");
            }
        }

        public AlgorithmParameters getParameters(NamedGroup ng) {

            AlgorithmParameters result = namedGroupParams.get(ng);
            if (result == null) {
                Optional<AlgorithmParameters> paramsOpt = getParametersImpl(ng);
                if (paramsOpt.isPresent()) {
                    result = paramsOpt.get();
                    namedGroupParams.put(ng, result);
                }
            }

            return result;
        }

        public abstract byte[] encodePossessionPublicKey(
                NamedGroupPossession namedGroupPossession);

        public abstract SSLCredentials decodeCredentials(
                NamedGroup ng, byte[] encoded,
                AlgorithmConstraints constraints,
                ExceptionSupplier onConstraintFail)
                throws IOException, GeneralSecurityException;

        public abstract SSLPossession createPossession(NamedGroup ng,
                SecureRandom random);

        public abstract SSLKeyDerivation createKeyDerivation(
                HandshakeContext hc) throws IOException;

        protected abstract Optional<AlgorithmParameters> getParametersImpl(
                NamedGroup ng);

        public abstract AlgorithmParameterSpec getParameterSpec(NamedGroup ng);

        public abstract boolean isAvailable(NamedGroup ng);
    }

    private static class FFDHFunctions extends NamedGroupFunctions {

        // lazy initialization
        private static class FunctionsHolder {
            private static final FFDHFunctions instance = new FFDHFunctions();
        }

        private static FFDHFunctions getInstance() {
            return FunctionsHolder.instance;
        }

        @Override
        public byte[] encodePossessionPublicKey(
                NamedGroupPossession namedGroupPossession) {
            return ((DHEPossession)namedGroupPossession).encode();
        }

        @Override
        public SSLCredentials decodeCredentials(NamedGroup ng, byte[] encoded,
                AlgorithmConstraints constraints,
                ExceptionSupplier onConstraintFail)
                throws IOException, GeneralSecurityException {

            DHKeyExchange.DHECredentials result
                    = DHKeyExchange.DHECredentials.valueOf(ng, encoded);

            checkConstraints(result.getPublicKey(), constraints,
                    onConstraintFail);

            return result;
        }

        @Override
        public SSLPossession createPossession(
                NamedGroup ng, SecureRandom random) {
            return new DHKeyExchange.DHEPossession(ng, random);
        }

        @Override
        public SSLKeyDerivation createKeyDerivation(HandshakeContext hc)
                throws IOException {

            return DHKeyExchange.kaGenerator.createKeyDerivation(hc);
        }

        @Override
        public AlgorithmParameterSpec getParameterSpec(NamedGroup ng) {
            return getDHParameterSpec(ng);
        }

        DHParameterSpec getDHParameterSpec(NamedGroup ng) {

            AlgorithmParameters params = getParameters(ng);
            try {
                return params.getParameterSpec(DHParameterSpec.class);
            } catch (InvalidParameterSpecException ipse) {
                // should be unlikely
                return getPredefinedDHParameterSpec(ng);
            }
        }

        private static DHParameterSpec getFFDHEDHParameterSpec(
                NamedGroup namedGroup) {

            DHParameterSpec spec = null;
            switch (namedGroup) {
                case FFDHE_2048:
                    spec = PredefinedDHParameterSpecs.ffdheParams.get(2048);
                    break;
                case FFDHE_3072:
                    spec = PredefinedDHParameterSpecs.ffdheParams.get(3072);
                    break;
                case FFDHE_4096:
                    spec = PredefinedDHParameterSpecs.ffdheParams.get(4096);
                    break;
                case FFDHE_6144:
                    spec = PredefinedDHParameterSpecs.ffdheParams.get(6144);
                    break;
                case FFDHE_8192:
                    spec = PredefinedDHParameterSpecs.ffdheParams.get(8192);
            }

            return spec;
        }

        private static DHParameterSpec getPredefinedDHParameterSpec(
                NamedGroup namedGroup) {

            DHParameterSpec spec = null;
            switch (namedGroup) {
                case FFDHE_2048:
                    spec = PredefinedDHParameterSpecs.definedParams.get(2048);
                    break;
                case FFDHE_3072:
                    spec = PredefinedDHParameterSpecs.definedParams.get(3072);
                    break;
                case FFDHE_4096:
                    spec = PredefinedDHParameterSpecs.definedParams.get(4096);
                    break;
                case FFDHE_6144:
                    spec = PredefinedDHParameterSpecs.definedParams.get(6144);
                    break;
                case FFDHE_8192:
                    spec = PredefinedDHParameterSpecs.definedParams.get(8192);
            }

            return spec;
        }

        @Override
        public boolean isAvailable(NamedGroup ng) {

            AlgorithmParameters params = getParameters(ng);
            return params != null;
        }

        @Override
        protected Optional<AlgorithmParameters> getParametersImpl(
                NamedGroup ng) {
            try {
                AlgorithmParameters params
                        = AlgorithmParameters.getInstance("DiffieHellman");
                AlgorithmParameterSpec spec
                        = getFFDHEDHParameterSpec(ng);
                params.init(spec);
                return Optional.of(params);
            } catch (InvalidParameterSpecException
                    | NoSuchAlgorithmException ex) {
                return Optional.empty();
            }
        }

    }

    private static class ECDHFunctions extends NamedGroupFunctions {

        // lazy initialization
        private static class FunctionsHolder {
            private static final ECDHFunctions instance = new ECDHFunctions();
        }

        private static ECDHFunctions getInstance() {
            return FunctionsHolder.instance;
        }

        @Override
        public byte[] encodePossessionPublicKey(
                NamedGroupPossession namedGroupPossession) {
            return ((ECDHEPossession)namedGroupPossession).encode();
        }

        @Override
        public SSLCredentials decodeCredentials(NamedGroup ng, byte[] encoded,
                AlgorithmConstraints constraints,
                ExceptionSupplier onConstraintFail)
                throws IOException, GeneralSecurityException {

            ECDHKeyExchange.ECDHECredentials result
                    = ECDHKeyExchange.ECDHECredentials.valueOf(ng, encoded);

            checkConstraints(result.getPublicKey(), constraints,
                    onConstraintFail);

            return result;
        }

        @Override
        public SSLPossession createPossession(
                NamedGroup ng, SecureRandom random) {
            return new ECDHKeyExchange.ECDHEPossession(ng, random);
        }

        @Override
        public SSLKeyDerivation createKeyDerivation(HandshakeContext hc)
                throws IOException {

            return ECDHKeyExchange.ecdheKAGenerator.createKeyDerivation(hc);
        }

        @Override
        public AlgorithmParameterSpec getParameterSpec(NamedGroup ng) {
            return SupportedGroupsExtension.SupportedGroups
                    .getECGenParamSpec(ng);
        }

        @Override
        public boolean isAvailable(NamedGroup ng) {

            AlgorithmParameters params = getParameters(ng);
            return params != null;
        }

        @Override
        protected Optional<AlgorithmParameters> getParametersImpl(
                NamedGroup ng) {
            try {
                AlgorithmParameters params
                        = AlgorithmParameters.getInstance("EC");
                AlgorithmParameterSpec spec
                        = new ECGenParameterSpec(ng.oid);
                params.init(spec);
                return Optional.of(params);
            } catch (InvalidParameterSpecException
                    | NoSuchAlgorithmException ex) {
                return Optional.empty();
            }
        }
    }

    private static class XDHFunctions extends NamedGroupFunctions {

        // lazy initialization
        private static class FunctionsHolder {
            private static final XDHFunctions instance = new XDHFunctions();
        }

        private static XDHFunctions getInstance() {
            return FunctionsHolder.instance;
        }

        @Override
        public byte[] encodePossessionPublicKey(NamedGroupPossession poss) {
            return ((XDHKeyExchange.XDHEPossession)poss).encode();
        }

        @Override
        public SSLCredentials decodeCredentials(NamedGroup ng, byte[] encoded,
                AlgorithmConstraints constraints,
                ExceptionSupplier onConstraintFail)
                throws IOException, GeneralSecurityException {

            XDHKeyExchange.XDHECredentials result
                    = XDHKeyExchange.XDHECredentials.valueOf(ng, encoded);

            checkConstraints(result.getPublicKey(), constraints,
                    onConstraintFail);

            return result;
        }

        @Override
        public SSLPossession createPossession(
                NamedGroup ng, SecureRandom random) {
            return new XDHKeyExchange.XDHEPossession(ng, random);
        }

        @Override
        public SSLKeyDerivation createKeyDerivation(HandshakeContext hc)
                throws IOException {
            return XDHKeyExchange.xdheKAGenerator.createKeyDerivation(hc);
        }

        @Override
        public AlgorithmParameterSpec getParameterSpec(NamedGroup ng) {
            return new NamedParameterSpec(ng.name);
        }

        @Override
        public boolean isAvailable(NamedGroup ng) {

            try {
                KeyAgreement.getInstance(ng.algorithm);
                return true;
            } catch (NoSuchAlgorithmException ex) {
                return false;
            }
        }

        @Override
        protected Optional<AlgorithmParameters> getParametersImpl(
                NamedGroup ng) {
            return Optional.empty();
        }
    }
}
