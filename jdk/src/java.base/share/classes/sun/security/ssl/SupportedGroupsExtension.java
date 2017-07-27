/*
 * Copyright (c) 2006, 2017, Oracle and/or its affiliates. All rights reserved.
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
import java.security.spec.ECGenParameterSpec;
import java.security.spec.InvalidParameterSpecException;
import java.security.AlgorithmParameters;
import java.security.AlgorithmConstraints;
import java.security.CryptoPrimitive;
import java.security.AccessController;
import java.security.spec.AlgorithmParameterSpec;
import javax.crypto.spec.DHParameterSpec;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import javax.net.ssl.SSLProtocolException;

import sun.security.action.GetPropertyAction;

//
// Note: Since RFC 7919, the extension's semantics are expanded from
// "Supported Elliptic Curves" to "Supported Groups".  The enum datatype
// used in the extension has been renamed from NamedCurve to NamedGroup.
// Its semantics are likewise expanded from "named curve" to "named group".
//
final class SupportedGroupsExtension extends HelloExtension {

    /* Class and subclass dynamic debugging support */
    private static final Debug debug = Debug.getInstance("ssl");

    private static final int ARBITRARY_PRIME = 0xff01;
    private static final int ARBITRARY_CHAR2 = 0xff02;

    // cache to speed up the parameters construction
    private static final Map<NamedGroup,
                AlgorithmParameters> namedGroupParams = new HashMap<>();

    // the supported named groups
    private static final NamedGroup[] supportedNamedGroups;

    // the named group presented in the extension
    private final int[] requestedNamedGroupIds;

    static {
        boolean requireFips = SunJSSE.isFIPS();

        // The value of the System Property defines a list of enabled named
        // groups in preference order, separated with comma.  For example:
        //
        //      jdk.tls.namedGroups="secp521r1, secp256r1, ffdhe2048"
        //
        // If the System Property is not defined or the value is empty, the
        // default groups and preferences will be used.
        String property = AccessController.doPrivileged(
                    new GetPropertyAction("jdk.tls.namedGroups"));
        if (property != null && property.length() != 0) {
            // remove double quote marks from beginning/end of the property
            if (property.length() > 1 && property.charAt(0) == '"' &&
                    property.charAt(property.length() - 1) == '"') {
                property = property.substring(1, property.length() - 1);
            }
        }

        ArrayList<NamedGroup> groupList;
        if (property != null && property.length() != 0) {   // customized groups
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

            if (groupList.isEmpty() && JsseJce.isEcAvailable()) {
                throw new IllegalArgumentException(
                    "System property jdk.tls.namedGroups(" + property + ") " +
                    "contains no supported elliptic curves");
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
        }

        if (debug != null && groupList.isEmpty()) {
            Debug.log(
                "Initialized [jdk.tls.namedGroups|default] list contains " +
                "no available elliptic curves. " +
                (property != null ? "(" + property + ")" : "[Default]"));
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
        if ("EC".equals(namedGroup.algorithm)) {
            if (namedGroup.oid != null) {
                try {
                    params = JsseJce.getAlgorithmParameters("EC");
                    spec = new ECGenParameterSpec(namedGroup.oid);
                } catch (Exception e) {
                    return false;
                }
            }
        } else if ("DiffieHellman".equals(namedGroup.algorithm)) {
            try {
                params = JsseJce.getAlgorithmParameters("DiffieHellman");
                spec = getFFDHEDHParameterSpec(namedGroup);
            } catch (Exception e) {
                return false;
            }
        }

        if ((params != null) && (spec != null)) {
            try {
                params.init(spec);
            } catch (Exception e) {
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

    private SupportedGroupsExtension(int[] requestedNamedGroupIds) {
        super(ExtensionType.EXT_SUPPORTED_GROUPS);

        this.requestedNamedGroupIds = requestedNamedGroupIds;
    }

    SupportedGroupsExtension(HandshakeInStream s, int len) throws IOException {
        super(ExtensionType.EXT_SUPPORTED_GROUPS);

        int k = s.getInt16();
        if (((len & 1) != 0) || (k == 0) || (k + 2 != len)) {
            throw new SSLProtocolException("Invalid " + type + " extension");
        }

        // Note: unknown named group will be ignored later.
        requestedNamedGroupIds = new int[k >> 1];
        for (int i = 0; i < requestedNamedGroupIds.length; i++) {
            requestedNamedGroupIds[i] = s.getInt16();
        }
    }

    // Get a local preferred supported ECDHE group permitted by the constraints.
    static NamedGroup getPreferredECGroup(AlgorithmConstraints constraints) {
        for (NamedGroup namedGroup : supportedNamedGroups) {
            if ((namedGroup.type == NamedGroupType.NAMED_GROUP_ECDHE) &&
                constraints.permits(EnumSet.of(CryptoPrimitive.KEY_AGREEMENT),
                    namedGroup.algorithm, namedGroupParams.get(namedGroup))) {

                return namedGroup;
            }
        }

        return null;
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
        if (!hasFFDHEGroups && (type == NamedGroupType.NAMED_GROUP_FFDHE)) {
            return true;
        }

        return false;
    }

    // Create the default supported groups extension.
    static SupportedGroupsExtension createExtension(
            AlgorithmConstraints constraints,
            CipherSuiteList cipherSuites, boolean enableFFDHE) {

        ArrayList<Integer> groupList =
                new ArrayList<>(supportedNamedGroups.length);
        for (NamedGroup namedGroup : supportedNamedGroups) {
            if ((!enableFFDHE) &&
                (namedGroup.type == NamedGroupType.NAMED_GROUP_FFDHE)) {
                continue;
            }

            if (cipherSuites.contains(namedGroup.type) &&
                constraints.permits(EnumSet.of(CryptoPrimitive.KEY_AGREEMENT),
                    namedGroup.algorithm, namedGroupParams.get(namedGroup))) {

                groupList.add(namedGroup.id);
            }
        }

        if (!groupList.isEmpty()) {
            int[] ids = new int[groupList.size()];
            int i = 0;
            for (Integer id : groupList) {
                ids[i++] = id;
            }

            return new SupportedGroupsExtension(ids);
        }

        return null;
    }

    // get the preferred activated named group
    NamedGroup getPreferredGroup(
            AlgorithmConstraints constraints, NamedGroupType type) {

        for (int groupId : requestedNamedGroupIds) {
            NamedGroup namedGroup = NamedGroup.valueOf(groupId);
            if ((namedGroup != null) && (namedGroup.type == type) &&
                SupportedGroupsExtension.supports(namedGroup) &&
                constraints.permits(EnumSet.of(CryptoPrimitive.KEY_AGREEMENT),
                    namedGroup.algorithm, namedGroupParams.get(namedGroup))) {

                return namedGroup;
            }
        }

        return null;
    }

    boolean hasFFDHEGroup() {
        for (int groupId : requestedNamedGroupIds) {
            /*
             * [RFC 7919] Codepoints in the "Supported Groups Registry"
             * with a high byte of 0x01 (that is, between 256 and 511,
             * inclusive) are set aside for FFDHE groups.
             */
            if ((groupId >= 256) && (groupId <= 511)) {
                return true;
            }
        }

        return false;
    }

    boolean contains(int index) {
        for (int groupId : requestedNamedGroupIds) {
            if (index == groupId) {
                return true;
            }
        }
        return false;
    }

    @Override
    int length() {
        return 6 + (requestedNamedGroupIds.length << 1);
    }

    @Override
    void send(HandshakeOutStream s) throws IOException {
        s.putInt16(type.id);
        int k = requestedNamedGroupIds.length << 1;
        s.putInt16(k + 2);
        s.putInt16(k);
        for (int groupId : requestedNamedGroupIds) {
            s.putInt16(groupId);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Extension " + type + ", group names: {");
        boolean first = true;
        for (int groupId : requestedNamedGroupIds) {
            if (first) {
                first = false;
            } else {
                sb.append(", ");
            }
            // first check if it is a known named group, then try other cases.
            NamedGroup namedGroup = NamedGroup.valueOf(groupId);
            if (namedGroup != null) {
                sb.append(namedGroup.name);
            } else if (groupId == ARBITRARY_PRIME) {
                sb.append("arbitrary_explicit_prime_curves");
            } else if (groupId == ARBITRARY_CHAR2) {
                sb.append("arbitrary_explicit_char2_curves");
            } else {
                sb.append("unknown named group " + groupId);
            }
        }
        sb.append("}");
        return sb.toString();
    }

    static boolean supports(NamedGroup namedGroup) {
        for (NamedGroup group : supportedNamedGroups) {
            if (namedGroup.id == group.id) {
                return true;
            }
        }

        return false;
    }

    static ECGenParameterSpec getECGenParamSpec(NamedGroup namedGroup) {
        if (namedGroup.type != NamedGroupType.NAMED_GROUP_ECDHE) {
            throw new RuntimeException("Not a named EC group: " + namedGroup);
        }

        AlgorithmParameters params = namedGroupParams.get(namedGroup);
        try {
            return params.getParameterSpec(ECGenParameterSpec.class);
        } catch (InvalidParameterSpecException ipse) {
            // should be unlikely
            return new ECGenParameterSpec(namedGroup.oid);
        }
    }

    static DHParameterSpec getDHParameterSpec(NamedGroup namedGroup) {
        if (namedGroup.type != NamedGroupType.NAMED_GROUP_FFDHE) {
            throw new RuntimeException("Not a named DH group: " + namedGroup);
        }

        AlgorithmParameters params = namedGroupParams.get(namedGroup);
        try {
            return params.getParameterSpec(DHParameterSpec.class);
        } catch (InvalidParameterSpecException ipse) {
            // should be unlikely
            return getPredefinedDHParameterSpec(namedGroup);
        }
    }
}
