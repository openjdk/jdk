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
import java.security.spec.ECParameterSpec;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.InvalidParameterSpecException;
import java.security.AlgorithmParameters;
import java.security.AlgorithmConstraints;
import java.security.CryptoPrimitive;
import java.security.AccessController;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import javax.net.ssl.SSLProtocolException;

import sun.security.action.GetPropertyAction;

final class EllipticCurvesExtension extends HelloExtension {

    /* Class and subclass dynamic debugging support */
    private static final Debug debug = Debug.getInstance("ssl");

    private static final int ARBITRARY_PRIME = 0xff01;
    private static final int ARBITRARY_CHAR2 = 0xff02;

    // speed up the searching
    private static final Map<String, Integer> oidToIdMap = new HashMap<>();
    private static final Map<Integer, String> idToOidMap = new HashMap<>();

    // speed up the parameters construction
    private static final Map<Integer,
                AlgorithmParameters> idToParams = new HashMap<>();

    // the supported elliptic curves
    private static final int[] supportedCurveIds;

    // the curves of the extension
    private final int[] curveIds;

    // See sun.security.util.CurveDB for the OIDs
    private static enum NamedEllipticCurve {
        T163_K1(1,  "sect163k1",    "1.3.132.0.1",      true),  // NIST K-163
        T163_R1(2,  "sect163r1",    "1.3.132.0.2",      false),
        T163_R2(3,  "sect163r2",    "1.3.132.0.15",     true),  // NIST B-163
        T193_R1(4,  "sect193r1",    "1.3.132.0.24",     false),
        T193_R2(5,  "sect193r2",    "1.3.132.0.25",     false),
        T233_K1(6,  "sect233k1",    "1.3.132.0.26",     true),  // NIST K-233
        T233_R1(7,  "sect233r1",    "1.3.132.0.27",     true),  // NIST B-233
        T239_K1(8,  "sect239k1",    "1.3.132.0.3",      false),
        T283_K1(9,  "sect283k1",    "1.3.132.0.16",     true),  // NIST K-283
        T283_R1(10, "sect283r1",    "1.3.132.0.17",     true),  // NIST B-283
        T409_K1(11, "sect409k1",    "1.3.132.0.36",     true),  // NIST K-409
        T409_R1(12, "sect409r1",    "1.3.132.0.37",     true),  // NIST B-409
        T571_K1(13, "sect571k1",    "1.3.132.0.38",     true),  // NIST K-571
        T571_R1(14, "sect571r1",    "1.3.132.0.39",     true),  // NIST B-571

        P160_K1(15, "secp160k1",    "1.3.132.0.9",      false),
        P160_R1(16, "secp160r1",    "1.3.132.0.8",      false),
        P160_R2(17, "secp160r2",    "1.3.132.0.30",     false),
        P192_K1(18, "secp192k1",    "1.3.132.0.31",     false),
        P192_R1(19, "secp192r1",    "1.2.840.10045.3.1.1", true), // NIST P-192
        P224_K1(20, "secp224k1",    "1.3.132.0.32",     false),
        P224_R1(21, "secp224r1",    "1.3.132.0.33",     true),  // NIST P-224
        P256_K1(22, "secp256k1",    "1.3.132.0.10",     false),
        P256_R1(23, "secp256r1",    "1.2.840.10045.3.1.7", true), // NIST P-256
        P384_R1(24, "secp384r1",    "1.3.132.0.34",     true),  // NIST P-384
        P521_R1(25, "secp521r1",    "1.3.132.0.35",     true);  // NIST P-521

        int          id;
        String       name;
        String       oid;
        boolean      isFips;

        NamedEllipticCurve(int id, String name, String oid, boolean isFips) {
            this.id = id;
            this.name = name;
            this.oid = oid;
            this.isFips = isFips;

            if (oidToIdMap.put(oid, id) != null ||
                idToOidMap.put(id, oid) != null) {

                throw new RuntimeException(
                        "Duplicate named elliptic curve definition: " + name);
            }
        }

        static NamedEllipticCurve getCurve(String name, boolean requireFips) {
            for (NamedEllipticCurve curve : NamedEllipticCurve.values()) {
                if (curve.name.equals(name) && (!requireFips || curve.isFips)) {
                    return curve;
                }
            }

            return null;
        }
    }

    static {
        boolean requireFips = SunJSSE.isFIPS();

        // hack code to initialize NamedEllipticCurve
        NamedEllipticCurve nec =
                NamedEllipticCurve.getCurve("secp256r1", false);

        // The value of the System Property defines a list of enabled named
        // curves in preference order, separated with comma.  For example:
        //
        //      jdk.tls.namedGroups="secp521r1, secp256r1, secp384r1"
        //
        // If the System Property is not defined or the value is empty, the
        // default curves and preferences will be used.
        String property = AccessController.doPrivileged(
                    new GetPropertyAction("jdk.tls.namedGroups"));
        if (property != null && property.length() != 0) {
            // remove double quote marks from beginning/end of the property
            if (property.length() > 1 && property.charAt(0) == '"' &&
                    property.charAt(property.length() - 1) == '"') {
                property = property.substring(1, property.length() - 1);
            }
        }

        ArrayList<Integer> idList;
        if (property != null && property.length() != 0) {   // customized curves
            String[] curves = property.split(",");
            idList = new ArrayList<>(curves.length);
            for (String curve : curves) {
                curve = curve.trim();
                if (!curve.isEmpty()) {
                    NamedEllipticCurve namedCurve =
                            NamedEllipticCurve.getCurve(curve, requireFips);
                    if (namedCurve != null) {
                        if (isAvailableCurve(namedCurve.id)) {
                            idList.add(namedCurve.id);
                        }
                    }   // ignore unknown curves
                }
            }
            if (idList.isEmpty() && JsseJce.isEcAvailable()) {
                throw new IllegalArgumentException(
                    "System property jdk.tls.namedGroups(" + property + ") " +
                    "contains no supported elliptic curves");
            }
        } else {        // default curves
            int[] ids;
            if (requireFips) {
                ids = new int[] {
                    // only NIST curves in FIPS mode
                    23, 24, 25, 9, 10, 11, 12, 13, 14,
                };
            } else {
                ids = new int[] {
                    // NIST curves first
                    23, 24, 25, 9, 10, 11, 12, 13, 14,
                    // non-NIST curves
                    22,
                };
            }

            idList = new ArrayList<>(ids.length);
            for (int curveId : ids) {
                if (isAvailableCurve(curveId)) {
                    idList.add(curveId);
                }
            }
        }

        if (debug != null && idList.isEmpty()) {
            Debug.log(
                "Initialized [jdk.tls.namedGroups|default] list contains " +
                "no available elliptic curves. " +
                (property != null ? "(" + property + ")" : "[Default]"));
        }

        supportedCurveIds = new int[idList.size()];
        int i = 0;
        for (Integer id : idList) {
            supportedCurveIds[i++] = id;
        }
    }

    // check whether the curve is supported by the underlying providers
    private static boolean isAvailableCurve(int curveId) {
        String oid = idToOidMap.get(curveId);
        if (oid != null) {
            AlgorithmParameters params = null;
            try {
                params = JsseJce.getAlgorithmParameters("EC");
                params.init(new ECGenParameterSpec(oid));
            } catch (Exception e) {
                return false;
            }

            // cache the parameters
            idToParams.put(curveId, params);

            return true;
        }

        return false;
    }

    private EllipticCurvesExtension(int[] curveIds) {
        super(ExtensionType.EXT_ELLIPTIC_CURVES);

        this.curveIds = curveIds;
    }

    EllipticCurvesExtension(HandshakeInStream s, int len)
            throws IOException {
        super(ExtensionType.EXT_ELLIPTIC_CURVES);

        int k = s.getInt16();
        if (((len & 1) != 0) || (k + 2 != len)) {
            throw new SSLProtocolException("Invalid " + type + " extension");
        }

        // Note: unknown curves will be ignored later.
        curveIds = new int[k >> 1];
        for (int i = 0; i < curveIds.length; i++) {
            curveIds[i] = s.getInt16();
        }
    }

    // get the preferred active curve
    static int getActiveCurves(AlgorithmConstraints constraints) {
        return getPreferredCurve(supportedCurveIds, constraints);
    }

    static boolean hasActiveCurves(AlgorithmConstraints constraints) {
        return getActiveCurves(constraints) >= 0;
    }

    static EllipticCurvesExtension createExtension(
                AlgorithmConstraints constraints) {

        ArrayList<Integer> idList = new ArrayList<>(supportedCurveIds.length);
        for (int curveId : supportedCurveIds) {
            if (constraints.permits(
                    EnumSet.of(CryptoPrimitive.KEY_AGREEMENT),
                                "EC", idToParams.get(curveId))) {
                idList.add(curveId);
            }
        }

        if (!idList.isEmpty()) {
            int[] ids = new int[idList.size()];
            int i = 0;
            for (Integer id : idList) {
                ids[i++] = id;
            }

            return new EllipticCurvesExtension(ids);
        }

        return null;
    }

    // get the preferred activated curve
    int getPreferredCurve(AlgorithmConstraints constraints) {
        return getPreferredCurve(curveIds, constraints);
    }

    // get a preferred activated curve
    private static int getPreferredCurve(int[] curves,
                AlgorithmConstraints constraints) {
        for (int curveId : curves) {
            if (isSupported(curveId) && constraints.permits(
                    EnumSet.of(CryptoPrimitive.KEY_AGREEMENT),
                                "EC", idToParams.get(curveId))) {
                return curveId;
            }
        }

        return -1;
    }

    boolean contains(int index) {
        for (int curveId : curveIds) {
            if (index == curveId) {
                return true;
            }
        }
        return false;
    }

    @Override
    int length() {
        return 6 + (curveIds.length << 1);
    }

    @Override
    void send(HandshakeOutStream s) throws IOException {
        s.putInt16(type.id);
        int k = curveIds.length << 1;
        s.putInt16(k + 2);
        s.putInt16(k);
        for (int curveId : curveIds) {
            s.putInt16(curveId);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Extension " + type + ", curve names: {");
        boolean first = true;
        for (int curveId : curveIds) {
            if (first) {
                first = false;
            } else {
                sb.append(", ");
            }
            // first check if it is a known named curve, then try other cases.
            String curveName = getCurveName(curveId);
            if (curveName != null) {
                sb.append(curveName);
            } else if (curveId == ARBITRARY_PRIME) {
                sb.append("arbitrary_explicit_prime_curves");
            } else if (curveId == ARBITRARY_CHAR2) {
                sb.append("arbitrary_explicit_char2_curves");
            } else {
                sb.append("unknown curve " + curveId);
            }
        }
        sb.append("}");
        return sb.toString();
    }

    // Test whether the given curve is supported.
    static boolean isSupported(int index) {
        for (int curveId : supportedCurveIds) {
            if (index == curveId) {
                return true;
            }
        }

        return false;
    }

    static int getCurveIndex(ECParameterSpec params) {
        String oid = JsseJce.getNamedCurveOid(params);
        if (oid == null) {
            return -1;
        }
        Integer n = oidToIdMap.get(oid);
        return (n == null) ? -1 : n;
    }

    static String getCurveOid(int index) {
        return idToOidMap.get(index);
    }

    static ECGenParameterSpec getECGenParamSpec(int index) {
        AlgorithmParameters params = idToParams.get(index);
        try {
            return params.getParameterSpec(ECGenParameterSpec.class);
        } catch (InvalidParameterSpecException ipse) {
            // should be unlikely
            String curveOid = getCurveOid(index);
            return new ECGenParameterSpec(curveOid);
        }
    }

    private static String getCurveName(int index) {
        for (NamedEllipticCurve namedCurve : NamedEllipticCurve.values()) {
            if (namedCurve.id == index) {
                return namedCurve.name;
            }
        }

        return null;
    }
}
