/*
 * Copyright (c) 2006, 2012, Oracle and/or its affiliates. All rights reserved.
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
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.SSLProtocolException;

final class SupportedEllipticCurvesExtension extends HelloExtension {

    // the extension value to send in the ClientHello message
    static final SupportedEllipticCurvesExtension DEFAULT;

    private static final boolean fips;

    static {
        int[] ids;
        fips = SunJSSE.isFIPS();
        if (fips == false) {
            ids = new int[] {
                // NIST curves first
                // prefer NIST P-256, rest in order of increasing key length
                23, 1, 3, 19, 21, 6, 7, 9, 10, 24, 11, 12, 25, 13, 14,
                // non-NIST curves
                15, 16, 17, 2, 18, 4, 5, 20, 8, 22,
            };
        } else {
            ids = new int[] {
                // same as above, but allow only NIST curves in FIPS mode
                23, 1, 3, 19, 21, 6, 7, 9, 10, 24, 11, 12, 25, 13, 14,
            };
        }
        DEFAULT = new SupportedEllipticCurvesExtension(ids);
    }

    private final int[] curveIds;

    private SupportedEllipticCurvesExtension(int[] curveIds) {
        super(ExtensionType.EXT_ELLIPTIC_CURVES);
        this.curveIds = curveIds;
    }

    SupportedEllipticCurvesExtension(HandshakeInStream s, int len)
            throws IOException {
        super(ExtensionType.EXT_ELLIPTIC_CURVES);
        int k = s.getInt16();
        if (((len & 1) != 0) || (k + 2 != len)) {
            throw new SSLProtocolException("Invalid " + type + " extension");
        }
        curveIds = new int[k >> 1];
        for (int i = 0; i < curveIds.length; i++) {
            curveIds[i] = s.getInt16();
        }
    }

    boolean contains(int index) {
        for (int curveId : curveIds) {
            if (index == curveId) {
                return true;
            }
        }
        return false;
    }

    // Return a reference to the internal curveIds array.
    // The caller must NOT modify the contents.
    int[] curveIds() {
        return curveIds;
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
            String oid = getCurveOid(curveId);
            if (oid != null) {
                ECParameterSpec spec = JsseJce.getECParameterSpec(oid);
                // this toString() output will look nice for the current
                // implementation of the ECParameterSpec class in the Sun
                // provider, but may not look good for other implementations.
                if (spec != null) {
                    sb.append(spec.toString().split(" ")[0]);
                } else {
                    sb.append(oid);
                }
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

    // Test whether we support the curve with the given index.
    static boolean isSupported(int index) {
        if ((index <= 0) || (index >= NAMED_CURVE_OID_TABLE.length)) {
            return false;
        }
        if (fips == false) {
            // in non-FIPS mode, we support all valid indices
            return true;
        }
        return DEFAULT.contains(index);
    }

    static int getCurveIndex(ECParameterSpec params) {
        String oid = JsseJce.getNamedCurveOid(params);
        if (oid == null) {
            return -1;
        }
        Integer n = curveIndices.get(oid);
        return (n == null) ? -1 : n;
    }

    static String getCurveOid(int index) {
        if ((index > 0) && (index < NAMED_CURVE_OID_TABLE.length)) {
            return NAMED_CURVE_OID_TABLE[index];
        }
        return null;
    }

    private final static int ARBITRARY_PRIME = 0xff01;
    private final static int ARBITRARY_CHAR2 = 0xff02;

    // See sun.security.ec.NamedCurve for the OIDs
    private final static String[] NAMED_CURVE_OID_TABLE = new String[] {
        null,                   //  (0) unused
        "1.3.132.0.1",          //  (1) sect163k1, NIST K-163
        "1.3.132.0.2",          //  (2) sect163r1
        "1.3.132.0.15",         //  (3) sect163r2, NIST B-163
        "1.3.132.0.24",         //  (4) sect193r1
        "1.3.132.0.25",         //  (5) sect193r2
        "1.3.132.0.26",         //  (6) sect233k1, NIST K-233
        "1.3.132.0.27",         //  (7) sect233r1, NIST B-233
        "1.3.132.0.3",          //  (8) sect239k1
        "1.3.132.0.16",         //  (9) sect283k1, NIST K-283
        "1.3.132.0.17",         // (10) sect283r1, NIST B-283
        "1.3.132.0.36",         // (11) sect409k1, NIST K-409
        "1.3.132.0.37",         // (12) sect409r1, NIST B-409
        "1.3.132.0.38",         // (13) sect571k1, NIST K-571
        "1.3.132.0.39",         // (14) sect571r1, NIST B-571
        "1.3.132.0.9",          // (15) secp160k1
        "1.3.132.0.8",          // (16) secp160r1
        "1.3.132.0.30",         // (17) secp160r2
        "1.3.132.0.31",         // (18) secp192k1
        "1.2.840.10045.3.1.1",  // (19) secp192r1, NIST P-192
        "1.3.132.0.32",         // (20) secp224k1
        "1.3.132.0.33",         // (21) secp224r1, NIST P-224
        "1.3.132.0.10",         // (22) secp256k1
        "1.2.840.10045.3.1.7",  // (23) secp256r1, NIST P-256
        "1.3.132.0.34",         // (24) secp384r1, NIST P-384
        "1.3.132.0.35",         // (25) secp521r1, NIST P-521
    };

    private final static Map<String,Integer> curveIndices;

    static {
        curveIndices = new HashMap<String,Integer>();
        for (int i = 1; i < NAMED_CURVE_OID_TABLE.length; i++) {
            curveIndices.put(NAMED_CURVE_OID_TABLE[i], i);
        }
    }

}
