/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.ByteBuffer;
import java.security.AlgorithmConstraints;
import java.security.AlgorithmParameters;
import java.security.CryptoPrimitive;
import java.security.NoSuchAlgorithmException;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.InvalidParameterSpecException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.crypto.spec.DHParameterSpec;
import javax.net.ssl.SSLProtocolException;
import sun.security.action.GetPropertyAction;
import static sun.security.ssl.SSLExtension.CH_SUPPORTED_GROUPS;
import static sun.security.ssl.SSLExtension.EE_SUPPORTED_GROUPS;
import sun.security.ssl.SSLExtension.ExtensionConsumer;
import sun.security.ssl.SSLExtension.SSLExtensionSpec;
import sun.security.ssl.SSLHandshake.HandshakeMessage;

/**
 * Pack of the "supported_groups" extensions [RFC 4492/7919].
 */
final class SupportedGroupsExtension {
    static final HandshakeProducer chNetworkProducer =
            new CHSupportedGroupsProducer();
    static final ExtensionConsumer chOnLoadConsumer =
            new CHSupportedGroupsConsumer();
    static final SSLStringizer sgsStringizer =
            new SupportedGroupsStringizer();

    static final HandshakeProducer eeNetworkProducer =
            new EESupportedGroupsProducer();
    static final ExtensionConsumer eeOnLoadConsumer =
            new EESupportedGroupsConsumer();

    /**
     * The "supported_groups" extension.
     */
    static final class SupportedGroupsSpec implements SSLExtensionSpec {
        final int[] namedGroupsIds;

        private SupportedGroupsSpec(int[] namedGroupsIds) {
            this.namedGroupsIds = namedGroupsIds;
        }

        private SupportedGroupsSpec(List<NamedGroup> namedGroups) {
            this.namedGroupsIds = new int[namedGroups.size()];
            int i = 0;
            for (NamedGroup ng : namedGroups) {
                namedGroupsIds[i++] = ng.id;
            }
        }

        private SupportedGroupsSpec(ByteBuffer m) throws IOException  {
            if (m.remaining() < 2) {      // 2: the length of the list
                throw new SSLProtocolException(
                    "Invalid supported_groups extension: insufficient data");
            }

            byte[] ngs = Record.getBytes16(m);
            if (m.hasRemaining()) {
                throw new SSLProtocolException(
                    "Invalid supported_groups extension: unknown extra data");
            }

            if ((ngs == null) || (ngs.length == 0) || (ngs.length % 2 != 0)) {
                throw new SSLProtocolException(
                    "Invalid supported_groups extension: incomplete data");
            }

            int[] ids = new int[ngs.length / 2];
            for (int i = 0, j = 0; i < ngs.length;) {
                ids[j++] = ((ngs[i++] & 0xFF) << 8) | (ngs[i++] & 0xFF);
            }

            this.namedGroupsIds = ids;
        }

        @Override
        public String toString() {
            MessageFormat messageFormat = new MessageFormat(
                "\"versions\": '['{0}']'", Locale.ENGLISH);

            if (namedGroupsIds == null || namedGroupsIds.length == 0) {
                Object[] messageFields = {
                        "<no supported named group specified>"
                    };
                return messageFormat.format(messageFields);
            } else {
                StringBuilder builder = new StringBuilder(512);
                boolean isFirst = true;
                for (int ngid : namedGroupsIds) {
                    if (isFirst) {
                        isFirst = false;
                    } else {
                        builder.append(", ");
                    }

                    builder.append(NamedGroup.nameOf(ngid));
                }

                Object[] messageFields = {
                        builder.toString()
                    };

                return messageFormat.format(messageFields);
            }
        }
    }

    private static final
            class SupportedGroupsStringizer implements SSLStringizer {
        @Override
        public String toString(ByteBuffer buffer) {
            try {
                return (new SupportedGroupsSpec(buffer)).toString();
            } catch (IOException ioe) {
                // For debug logging only, so please swallow exceptions.
                return ioe.getMessage();
            }
        }
    }

    static enum NamedGroupType {
        NAMED_GROUP_ECDHE,          // Elliptic Curve Groups (ECDHE)
        NAMED_GROUP_FFDHE,          // Finite Field Groups (DHE)
        NAMED_GROUP_XDH,            // Finite Field Groups (XDH)
        NAMED_GROUP_ARBITRARY,      // arbitrary prime and curves (ECDHE)
        NAMED_GROUP_NONE;           // Not predefined named group

        boolean isSupported(List<CipherSuite> cipherSuites) {
            for (CipherSuite cs : cipherSuites) {
                if (cs.keyExchange == null || cs.keyExchange.groupType == this) {
                    return true;
                }
            }

            return false;
        }
    }

    static enum NamedGroup {
        // Elliptic Curves (RFC 4492)
        //
        // See sun.security.util.CurveDB for the OIDs
        // NIST K-163
        SECT163_K1  (0x0001, "sect163k1", "1.3.132.0.1", true,
                            ProtocolVersion.PROTOCOLS_TO_12),
        SECT163_R1  (0x0002, "sect163r1", "1.3.132.0.2", false,
                            ProtocolVersion.PROTOCOLS_TO_12),

        // NIST B-163
        SECT163_R2  (0x0003, "sect163r2", "1.3.132.0.15", true,
                            ProtocolVersion.PROTOCOLS_TO_12),
        SECT193_R1  (0x0004, "sect193r1", "1.3.132.0.24", false,
                            ProtocolVersion.PROTOCOLS_TO_12),
        SECT193_R2  (0x0005, "sect193r2", "1.3.132.0.25", false,
                            ProtocolVersion.PROTOCOLS_TO_12),

        // NIST K-233
        SECT233_K1  (0x0006, "sect233k1", "1.3.132.0.26", true,
                            ProtocolVersion.PROTOCOLS_TO_12),

        // NIST B-233
        SECT233_R1  (0x0007, "sect233r1", "1.3.132.0.27", true,
                            ProtocolVersion.PROTOCOLS_TO_12),
        SECT239_K1  (0x0008, "sect239k1", "1.3.132.0.3", false,
                            ProtocolVersion.PROTOCOLS_TO_12),

        // NIST K-283
        SECT283_K1  (0x0009, "sect283k1", "1.3.132.0.16", true,
                            ProtocolVersion.PROTOCOLS_TO_12),

        // NIST B-283
        SECT283_R1  (0x000A, "sect283r1", "1.3.132.0.17", true,
                            ProtocolVersion.PROTOCOLS_TO_12),

        // NIST K-409
        SECT409_K1  (0x000B, "sect409k1", "1.3.132.0.36", true,
                            ProtocolVersion.PROTOCOLS_TO_12),

        // NIST B-409
        SECT409_R1  (0x000C, "sect409r1", "1.3.132.0.37", true,
                            ProtocolVersion.PROTOCOLS_TO_12),

        // NIST K-571
        SECT571_K1  (0x000D, "sect571k1", "1.3.132.0.38", true,
                            ProtocolVersion.PROTOCOLS_TO_12),

        // NIST B-571
        SECT571_R1  (0x000E, "sect571r1", "1.3.132.0.39", true,
                            ProtocolVersion.PROTOCOLS_TO_12),
        SECP160_K1  (0x000F, "secp160k1", "1.3.132.0.9", false,
                            ProtocolVersion.PROTOCOLS_TO_12),
        SECP160_R1  (0x0010, "secp160r1", "1.3.132.0.8", false,
                            ProtocolVersion.PROTOCOLS_TO_12),
        SECP160_R2  (0x0011, "secp160r2", "1.3.132.0.30", false,
                            ProtocolVersion.PROTOCOLS_TO_12),
        SECP192_K1  (0x0012, "secp192k1", "1.3.132.0.31", false,
                            ProtocolVersion.PROTOCOLS_TO_12),

        // NIST P-192
        SECP192_R1  (0x0013, "secp192r1", "1.2.840.10045.3.1.1", true,
                            ProtocolVersion.PROTOCOLS_TO_12),
        SECP224_K1  (0x0014, "secp224k1", "1.3.132.0.32", false,
                            ProtocolVersion.PROTOCOLS_TO_12),
        // NIST P-224
        SECP224_R1  (0x0015, "secp224r1", "1.3.132.0.33", true,
                            ProtocolVersion.PROTOCOLS_TO_12),
        SECP256_K1  (0x0016, "secp256k1", "1.3.132.0.10", false,
                            ProtocolVersion.PROTOCOLS_TO_12),

        // NIST P-256
        SECP256_R1  (0x0017, "secp256r1", "1.2.840.10045.3.1.7", true,
                            ProtocolVersion.PROTOCOLS_TO_13),

        // NIST P-384
        SECP384_R1  (0x0018, "secp384r1", "1.3.132.0.34", true,
                            ProtocolVersion.PROTOCOLS_TO_13),

        // NIST P-521
        SECP521_R1  (0x0019, "secp521r1", "1.3.132.0.35", true,
                            ProtocolVersion.PROTOCOLS_TO_13),

        // x25519 and x448
        X25519      (0x001D, "x25519", true, "x25519",
                            ProtocolVersion.PROTOCOLS_TO_13),
        X448        (0x001E, "x448", true, "x448",
                            ProtocolVersion.PROTOCOLS_TO_13),

        // Finite Field Diffie-Hellman Ephemeral Parameters (RFC 7919)
        FFDHE_2048  (0x0100, "ffdhe2048",  true,
                            ProtocolVersion.PROTOCOLS_TO_13),
        FFDHE_3072  (0x0101, "ffdhe3072",  true,
                            ProtocolVersion.PROTOCOLS_TO_13),
        FFDHE_4096  (0x0102, "ffdhe4096",  true,
                            ProtocolVersion.PROTOCOLS_TO_13),
        FFDHE_6144  (0x0103, "ffdhe6144",  true,
                            ProtocolVersion.PROTOCOLS_TO_13),
        FFDHE_8192  (0x0104, "ffdhe8192",  true,
                            ProtocolVersion.PROTOCOLS_TO_13),

        // Elliptic Curves (RFC 4492)
        //
        // arbitrary prime and characteristic-2 curves
        ARBITRARY_PRIME  (0xFF01, "arbitrary_explicit_prime_curves",
                            ProtocolVersion.PROTOCOLS_TO_12),
        ARBITRARY_CHAR2  (0xFF02, "arbitrary_explicit_char2_curves",
                            ProtocolVersion.PROTOCOLS_TO_12);

        final int id;               // hash + signature
        final NamedGroupType type;  // group type
        final String name;          // literal name
        final String oid;           // object identifier of the named group
        final String algorithm;     // signature algorithm
        final boolean isFips;       // can be used in FIPS mode?
        final ProtocolVersion[] supportedProtocols;

        // Constructor used for Elliptic Curve Groups (ECDHE)
        private NamedGroup(int id, String name, String oid, boolean isFips,
                ProtocolVersion[] supportedProtocols) {
            this.id = id;
            this.type = NamedGroupType.NAMED_GROUP_ECDHE;
            this.name = name;
            this.oid = oid;
            this.algorithm = "EC";
            this.isFips = isFips;
            this.supportedProtocols = supportedProtocols;
        }

        // Constructor used for Elliptic Curve Groups (XDH)
        private NamedGroup(int id, String name,
                boolean isFips, String algorithm,
                ProtocolVersion[] supportedProtocols) {
            this.id = id;
            this.type = NamedGroupType.NAMED_GROUP_XDH;
            this.name = name;
            this.oid = null;
            this.algorithm = algorithm;
            this.isFips = isFips;
            this.supportedProtocols = supportedProtocols;
        }

        // Constructor used for Finite Field Diffie-Hellman Groups (FFDHE)
        private NamedGroup(int id, String name, boolean isFips,
                ProtocolVersion[] supportedProtocols) {
            this.id = id;
            this.type = NamedGroupType.NAMED_GROUP_FFDHE;
            this.name = name;
            this.oid = null;
            this.algorithm = "DiffieHellman";
            this.isFips = isFips;
            this.supportedProtocols = supportedProtocols;
        }

        // Constructor used for arbitrary prime and curves (ECDHE)
        private NamedGroup(int id, String name,
                ProtocolVersion[] supportedProtocols) {
            this.id = id;
            this.type = NamedGroupType.NAMED_GROUP_ARBITRARY;
            this.name = name;
            this.oid = null;
            this.algorithm = "EC";
            this.isFips = false;
            this.supportedProtocols = supportedProtocols;
        }

        static NamedGroup valueOf(int id) {
            for (NamedGroup group : NamedGroup.values()) {
                if (group.id == id) {
                    return group;
                }
            }

            return null;
        }

        static NamedGroup valueOf(ECParameterSpec params) {
            String oid = JsseJce.getNamedCurveOid(params);
            if ((oid != null) && (!oid.isEmpty())) {
                for (NamedGroup group : NamedGroup.values()) {
                    if ((group.type == NamedGroupType.NAMED_GROUP_ECDHE) &&
                            oid.equals(group.oid)) {
                        return group;
                    }
                }
            }

            return null;
        }

        static NamedGroup valueOf(DHParameterSpec params) {
            for (Map.Entry<NamedGroup, AlgorithmParameters> me :
                    SupportedGroups.namedGroupParams.entrySet()) {
                NamedGroup ng = me.getKey();
                if (ng.type != NamedGroupType.NAMED_GROUP_FFDHE) {
                    continue;
                }

                DHParameterSpec ngParams = null;
                AlgorithmParameters aps = me.getValue();
                try {
                    ngParams = aps.getParameterSpec(DHParameterSpec.class);
                } catch (InvalidParameterSpecException ipse) {
                    // should be unlikely
                }

                if (ngParams == null) {
                    continue;
                }

                if (ngParams.getP().equals(params.getP()) &&
                        ngParams.getG().equals(params.getG())) {
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

        boolean isSupported(List<CipherSuite> cipherSuites) {
            for (CipherSuite cs : cipherSuites) {
                boolean isMatch = isAvailable(cs.supportedProtocols);
                if (isMatch && (cs.keyExchange == null ||
                        cs.keyExchange.groupType == type)) {
                    return true;
                }
            }
            return false;
        }

        // lazy loading of parameters
        AlgorithmParameters getParameters() {
            return SupportedGroups.namedGroupParams.get(this);
        }

        AlgorithmParameterSpec getParameterSpec() {
            if (this.type == NamedGroupType.NAMED_GROUP_ECDHE) {
                return SupportedGroups.getECGenParamSpec(this);
            } else if (this.type == NamedGroupType.NAMED_GROUP_FFDHE) {
                return SupportedGroups.getDHParameterSpec(this);
            }

            return null;
        }
    }

    static class SupportedGroups {
        // To switch off the supported_groups extension for DHE cipher suite.
        static final boolean enableFFDHE =
                Utilities.getBooleanProperty("jsse.enableFFDHE", true);

        // cache to speed up the parameters construction
        static final Map<NamedGroup,
                    AlgorithmParameters> namedGroupParams = new HashMap<>();

        // the supported named groups
        static final NamedGroup[] supportedNamedGroups;

        static {
            boolean requireFips = SunJSSE.isFIPS();

            // The value of the System Property defines a list of enabled named
            // groups in preference order, separated with comma.  For example:
            //
            //      jdk.tls.namedGroups="secp521r1, secp256r1, ffdhe2048"
            //
            // If the System Property is not defined or the value is empty, the
            // default groups and preferences will be used.
            String property = GetPropertyAction
                    .privilegedGetProperty("jdk.tls.namedGroups");
            if (property != null && !property.isEmpty()) {
                // remove double quote marks from beginning/end of the property
                if (property.length() > 1 && property.charAt(0) == '"' &&
                        property.charAt(property.length() - 1) == '"') {
                    property = property.substring(1, property.length() - 1);
                }
            }

            ArrayList<NamedGroup> groupList;
            if (property != null && !property.isEmpty()) {
                String[] groups = property.split(",");
                groupList = new ArrayList<>(groups.length);
                for (String group : groups) {
                    group = group.trim();
                    if (!group.isEmpty()) {
                        NamedGroup namedGroup = NamedGroup.nameOf(group);
                        if (namedGroup != null &&
                                (!requireFips || namedGroup.isFips)) {
                            if (isAvailableGroup(namedGroup)) {
                                groupList.add(namedGroup);
                            }
                        }   // ignore unknown groups
                    }
                }

                if (groupList.isEmpty()) {
                    throw new IllegalArgumentException(
                            "System property jdk.tls.namedGroups(" +
                            property + ") contains no supported named groups");
                }
            } else {        // default groups
                NamedGroup[] groups;
                if (requireFips) {
                    groups = new NamedGroup[] {
                        // only NIST curves in FIPS mode
                        NamedGroup.SECP256_R1,
                        NamedGroup.SECP384_R1,
                        NamedGroup.SECP521_R1,
                        NamedGroup.SECT283_K1,
                        NamedGroup.SECT283_R1,
                        NamedGroup.SECT409_K1,
                        NamedGroup.SECT409_R1,
                        NamedGroup.SECT571_K1,
                        NamedGroup.SECT571_R1,

                        // FFDHE 2048
                        NamedGroup.FFDHE_2048,
                        NamedGroup.FFDHE_3072,
                        NamedGroup.FFDHE_4096,
                        NamedGroup.FFDHE_6144,
                        NamedGroup.FFDHE_8192,
                    };
                } else {
                    groups = new NamedGroup[] {
                        // NIST curves first
                        NamedGroup.SECP256_R1,
                        NamedGroup.SECP384_R1,
                        NamedGroup.SECP521_R1,
                        NamedGroup.SECT283_K1,
                        NamedGroup.SECT283_R1,
                        NamedGroup.SECT409_K1,
                        NamedGroup.SECT409_R1,
                        NamedGroup.SECT571_K1,
                        NamedGroup.SECT571_R1,

                        // non-NIST curves
                        NamedGroup.SECP256_K1,

                        // FFDHE 2048
                        NamedGroup.FFDHE_2048,
                        NamedGroup.FFDHE_3072,
                        NamedGroup.FFDHE_4096,
                        NamedGroup.FFDHE_6144,
                        NamedGroup.FFDHE_8192,
                    };
                }

                groupList = new ArrayList<>(groups.length);
                for (NamedGroup group : groups) {
                    if (isAvailableGroup(group)) {
                        groupList.add(group);
                    }
                }

                if (groupList.isEmpty() &&
                        SSLLogger.isOn && SSLLogger.isOn("ssl")) {
                    SSLLogger.warning("No default named groups");
                }
            }

            supportedNamedGroups = new NamedGroup[groupList.size()];
            int i = 0;
            for (NamedGroup namedGroup : groupList) {
                supportedNamedGroups[i++] = namedGroup;
            }
        }

        // check whether the group is supported by the underlying providers
        private static boolean isAvailableGroup(NamedGroup namedGroup) {
            AlgorithmParameters params = null;
            AlgorithmParameterSpec spec = null;
            if (namedGroup.type == NamedGroupType.NAMED_GROUP_ECDHE) {
                if (namedGroup.oid != null) {
                    try {
                        params = JsseJce.getAlgorithmParameters("EC");
                        spec = new ECGenParameterSpec(namedGroup.oid);
                    } catch (NoSuchAlgorithmException e) {
                        return false;
                    }
                }
            } else if (namedGroup.type == NamedGroupType.NAMED_GROUP_FFDHE) {
                try {
                    params = JsseJce.getAlgorithmParameters("DiffieHellman");
                    spec = getFFDHEDHParameterSpec(namedGroup);
                } catch (NoSuchAlgorithmException e) {
                    return false;
                }
            }   // Otherwise, unsupported.

            if ((params != null) && (spec != null)) {
                try {
                    params.init(spec);
                } catch (InvalidParameterSpecException e) {
                    return false;
                }

                // cache the parameters
                namedGroupParams.put(namedGroup, params);

                return true;
            }

            return false;
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

        static ECGenParameterSpec getECGenParamSpec(NamedGroup namedGroup) {
            if (namedGroup.type != NamedGroupType.NAMED_GROUP_ECDHE) {
                throw new RuntimeException(
                        "Not a named EC group: " + namedGroup);
            }

            AlgorithmParameters params = namedGroupParams.get(namedGroup);
            if (params == null) {
                throw new RuntimeException(
                        "Not a supported EC named group: " + namedGroup);
            }

            try {
                return params.getParameterSpec(ECGenParameterSpec.class);
            } catch (InvalidParameterSpecException ipse) {
                // should be unlikely
                return new ECGenParameterSpec(namedGroup.oid);
            }
        }

        static DHParameterSpec getDHParameterSpec(NamedGroup namedGroup) {
            if (namedGroup.type != NamedGroupType.NAMED_GROUP_FFDHE) {
                throw new RuntimeException(
                        "Not a named DH group: " + namedGroup);
            }

            AlgorithmParameters params = namedGroupParams.get(namedGroup);
            if (params == null) {
                throw new RuntimeException(
                        "Not a supported DH named group: " + namedGroup);
            }

            try {
                return params.getParameterSpec(DHParameterSpec.class);
            } catch (InvalidParameterSpecException ipse) {
                // should be unlikely
                return getPredefinedDHParameterSpec(namedGroup);
            }
        }

        // Is there any supported group permitted by the constraints?
        static boolean isActivatable(
                AlgorithmConstraints constraints, NamedGroupType type) {

            boolean hasFFDHEGroups = false;
            for (NamedGroup namedGroup : supportedNamedGroups) {
                if (namedGroup.type == type) {
                    if (constraints.permits(
                            EnumSet.of(CryptoPrimitive.KEY_AGREEMENT),
                            namedGroup.algorithm,
                            namedGroupParams.get(namedGroup))) {

                        return true;
                    }

                    if (!hasFFDHEGroups &&
                            (type == NamedGroupType.NAMED_GROUP_FFDHE)) {
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
            return !hasFFDHEGroups && type == NamedGroupType.NAMED_GROUP_FFDHE;
        }

        // Is the named group permitted by the constraints?
        static boolean isActivatable(
                AlgorithmConstraints constraints, NamedGroup namedGroup) {
            if (!isSupported(namedGroup)) {
                return false;
            }

            return constraints.permits(
                            EnumSet.of(CryptoPrimitive.KEY_AGREEMENT),
                            namedGroup.algorithm,
                            namedGroupParams.get(namedGroup));
        }

        // Is the named group supported?
        static boolean isSupported(NamedGroup namedGroup) {
            for (NamedGroup group : supportedNamedGroups) {
                if (namedGroup.id == group.id) {
                    return true;
                }
            }

            return false;
        }

        static NamedGroup getPreferredGroup(
                ProtocolVersion negotiatedProtocol,
                AlgorithmConstraints constraints, NamedGroupType type,
                List<NamedGroup> requestedNamedGroups) {
            for (NamedGroup namedGroup : requestedNamedGroups) {
                if ((namedGroup.type == type) &&
                        namedGroup.isAvailable(negotiatedProtocol) &&
                        isSupported(namedGroup) &&
                        constraints.permits(
                                EnumSet.of(CryptoPrimitive.KEY_AGREEMENT),
                                namedGroup.algorithm,
                                namedGroupParams.get(namedGroup))) {
                    return namedGroup;
                }
            }

            return null;
        }

        static NamedGroup getPreferredGroup(
                ProtocolVersion negotiatedProtocol,
                AlgorithmConstraints constraints, NamedGroupType type) {
            for (NamedGroup namedGroup : supportedNamedGroups) {
                if ((namedGroup.type == type) &&
                        namedGroup.isAvailable(negotiatedProtocol) &&
                        constraints.permits(
                                EnumSet.of(CryptoPrimitive.KEY_AGREEMENT),
                                namedGroup.algorithm,
                                namedGroupParams.get(namedGroup))) {
                    return namedGroup;
                }
            }

            return null;
        }
    }

    /**
     * Network data producer of a "supported_groups" extension in
     * the ClientHello handshake message.
     */
    private static final class CHSupportedGroupsProducer
            extends SupportedGroups implements HandshakeProducer {
        // Prevent instantiation of this class.
        private CHSupportedGroupsProducer() {
            // blank
        }

        @Override
        public byte[] produce(ConnectionContext context,
                HandshakeMessage message) throws IOException {
            // The producing happens in client side only.
            ClientHandshakeContext chc = (ClientHandshakeContext)context;

            // Is it a supported and enabled extension?
            if (!chc.sslConfig.isAvailable(CH_SUPPORTED_GROUPS)) {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.fine(
                        "Ignore unavailable supported_groups extension");
                }
                return null;
            }

            // Produce the extension.
            ArrayList<NamedGroup> namedGroups =
                new ArrayList<>(SupportedGroups.supportedNamedGroups.length);
            for (NamedGroup ng : SupportedGroups.supportedNamedGroups) {
                if ((!SupportedGroups.enableFFDHE) &&
                    (ng.type == NamedGroupType.NAMED_GROUP_FFDHE)) {
                    continue;
                }

                if (ng.isAvailable(chc.activeProtocols) &&
                        ng.isSupported(chc.activeCipherSuites) &&
                        chc.algorithmConstraints.permits(
                            EnumSet.of(CryptoPrimitive.KEY_AGREEMENT),
                            ng.algorithm, namedGroupParams.get(ng))) {
                    namedGroups.add(ng);
                } else if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.fine(
                        "Ignore inactive or disabled named group: " + ng.name);
                }
            }

            if (namedGroups.isEmpty()) {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.warning("no available named group");
                }

                return null;
            }

            int vectorLen = namedGroups.size() << 1;
            byte[] extData = new byte[vectorLen + 2];
            ByteBuffer m = ByteBuffer.wrap(extData);
            Record.putInt16(m, vectorLen);
            for (NamedGroup namedGroup : namedGroups) {
                    Record.putInt16(m, namedGroup.id);
            }

            // Update the context.
            chc.clientRequestedNamedGroups =
                    Collections.<NamedGroup>unmodifiableList(namedGroups);
            chc.handshakeExtensions.put(CH_SUPPORTED_GROUPS,
                    new SupportedGroupsSpec(namedGroups));

            return extData;
        }
    }

    /**
     * Network data producer of a "supported_groups" extension in
     * the ClientHello handshake message.
     */
    private static final
            class CHSupportedGroupsConsumer implements ExtensionConsumer {
        // Prevent instantiation of this class.
        private CHSupportedGroupsConsumer() {
            // blank
        }

        @Override
        public void consume(ConnectionContext context,
            HandshakeMessage message, ByteBuffer buffer) throws IOException {
            // The consuming happens in server side only.
            ServerHandshakeContext shc = (ServerHandshakeContext)context;

            // Is it a supported and enabled extension?
            if (!shc.sslConfig.isAvailable(CH_SUPPORTED_GROUPS)) {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.fine(
                        "Ignore unavailable supported_groups extension");
                }
                return;     // ignore the extension
            }

            // Parse the extension.
            SupportedGroupsSpec spec;
            try {
                spec = new SupportedGroupsSpec(buffer);
            } catch (IOException ioe) {
                shc.conContext.fatal(Alert.UNEXPECTED_MESSAGE, ioe);
                return;     // fatal() always throws, make the compiler happy.
            }

            // Update the context.
            List<NamedGroup> knownNamedGroups = new LinkedList<>();
            for (int id : spec.namedGroupsIds) {
                NamedGroup ng = NamedGroup.valueOf(id);
                if (ng != null) {
                    knownNamedGroups.add(ng);
                }
            }

            shc.clientRequestedNamedGroups = knownNamedGroups;
            shc.handshakeExtensions.put(CH_SUPPORTED_GROUPS, spec);

            // No impact on session resumption.
        }
    }

    /**
     * Network data producer of a "supported_groups" extension in
     * the EncryptedExtensions handshake message.
     */
    private static final class EESupportedGroupsProducer
            extends SupportedGroups implements HandshakeProducer {

        // Prevent instantiation of this class.
        private EESupportedGroupsProducer() {
            // blank
        }

        @Override
        public byte[] produce(ConnectionContext context,
                HandshakeMessage message) throws IOException {
            // The producing happens in server side only.
            ServerHandshakeContext shc = (ServerHandshakeContext)context;

            // Is it a supported and enabled extension?
            if (!shc.sslConfig.isAvailable(EE_SUPPORTED_GROUPS)) {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.fine(
                        "Ignore unavailable supported_groups extension");
                }
                return null;
            }

            // Produce the extension.
            //
            // Contains all groups the server supports, regardless of whether
            // they are currently supported by the client.
            ArrayList<NamedGroup> namedGroups = new ArrayList<>(
                    SupportedGroups.supportedNamedGroups.length);
            for (NamedGroup ng : SupportedGroups.supportedNamedGroups) {
                if ((!SupportedGroups.enableFFDHE) &&
                    (ng.type == NamedGroupType.NAMED_GROUP_FFDHE)) {
                    continue;
                }

                if (ng.isAvailable(shc.activeProtocols) &&
                        ng.isSupported(shc.activeCipherSuites) &&
                        shc.algorithmConstraints.permits(
                            EnumSet.of(CryptoPrimitive.KEY_AGREEMENT),
                            ng.algorithm, namedGroupParams.get(ng))) {
                    namedGroups.add(ng);
                } else if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.fine(
                        "Ignore inactive or disabled named group: " + ng.name);
                }
            }

            if (namedGroups.isEmpty()) {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.warning("no available named group");
                }

                return null;
            }

            int vectorLen = namedGroups.size() << 1;
            byte[] extData = new byte[vectorLen + 2];
            ByteBuffer m = ByteBuffer.wrap(extData);
            Record.putInt16(m, vectorLen);
            for (NamedGroup namedGroup : namedGroups) {
                    Record.putInt16(m, namedGroup.id);
            }

            // Update the context.
            shc.conContext.serverRequestedNamedGroups =
                    Collections.<NamedGroup>unmodifiableList(namedGroups);
            SupportedGroupsSpec spec = new SupportedGroupsSpec(namedGroups);
            shc.handshakeExtensions.put(EE_SUPPORTED_GROUPS, spec);

            return extData;
        }
    }

    private static final
            class EESupportedGroupsConsumer implements ExtensionConsumer {
        // Prevent instantiation of this class.
        private EESupportedGroupsConsumer() {
            // blank
        }

        @Override
        public void consume(ConnectionContext context,
            HandshakeMessage message, ByteBuffer buffer) throws IOException {
            // The consuming happens in client side only.
            ClientHandshakeContext chc = (ClientHandshakeContext)context;

            // Is it a supported and enabled extension?
            if (!chc.sslConfig.isAvailable(EE_SUPPORTED_GROUPS)) {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.fine(
                        "Ignore unavailable supported_groups extension");
                }
                return;     // ignore the extension
            }

            // Parse the extension.
            SupportedGroupsSpec spec;
            try {
                spec = new SupportedGroupsSpec(buffer);
            } catch (IOException ioe) {
                chc.conContext.fatal(Alert.UNEXPECTED_MESSAGE, ioe);
                return;     // fatal() always throws, make the compiler happy.
            }

            // Update the context.
            List<NamedGroup> knownNamedGroups =
                    new ArrayList<>(spec.namedGroupsIds.length);
            for (int id : spec.namedGroupsIds) {
                NamedGroup ng = NamedGroup.valueOf(id);
                if (ng != null) {
                    knownNamedGroups.add(ng);
                }
            }

            chc.conContext.serverRequestedNamedGroups = knownNamedGroups;
            chc.handshakeExtensions.put(EE_SUPPORTED_GROUPS, spec);

            // No impact on session resumption.
        }
    }
}
