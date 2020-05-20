/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.util;

import sun.security.validator.Validator;

import java.security.AlgorithmParameters;
import java.security.Key;
import java.security.Timestamp;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECKey;
import java.security.interfaces.XECKey;
import java.security.spec.NamedParameterSpec;
import java.util.Date;

/**
 * This class contains parameters for checking against constraints that extend
 * past the publicly available parameters in java.security.AlgorithmConstraints.
 *
 * This is currently passed between PKIX, AlgorithmChecker,
 * and DisabledAlgorithmConstraints.
 */
public class ConstraintsParameters {
    /*
     * The below 3 values are used the same as the permit() methods
     * published in java.security.AlgorithmConstraints.
     */
    // Algorithm string to be checked against constraints
    private final String algorithm;
    // AlgorithmParameters to the algorithm being checked
    private final AlgorithmParameters algParams;
    // Key being checked against constraints
    private final Key key;

    /*
     * New values that are checked against constraints that the current public
     * API does not support.
     */
    // A certificate being passed to check against constraints.
    private final X509Certificate cert;
    // This is true if the trust anchor in the certificate chain matches a cert
    // in AnchorCertificates
    private final boolean trustedMatch;
    // PKIXParameter date
    private final Date pkixDate;
    // Timestamp of the signed JAR file
    private final Timestamp jarTimestamp;
    private final String variant;
    // Named Curve
    private final String[] curveStr;
    private static final String[] EMPTYLIST = new String[0];

    public ConstraintsParameters(X509Certificate c, boolean match,
            Date pkixdate, Timestamp jarTime, String variant) {
        cert = c;
        trustedMatch = match;
        pkixDate = pkixdate;
        jarTimestamp = jarTime;
        this.variant = (variant == null ? Validator.VAR_GENERIC : variant);
        algorithm = null;
        algParams = null;
        key = null;
        if (c != null) {
            curveStr = getNamedCurveFromKey(c.getPublicKey());
        } else {
            curveStr = EMPTYLIST;
        }
    }

    public ConstraintsParameters(String algorithm, AlgorithmParameters params,
            Key key, String variant) {
        this.algorithm = algorithm;
        algParams = params;
        this.key = key;
        curveStr = getNamedCurveFromKey(key);
        cert = null;
        trustedMatch = false;
        pkixDate = null;
        jarTimestamp = null;
        this.variant = (variant == null ? Validator.VAR_GENERIC : variant);
    }


    public ConstraintsParameters(X509Certificate c) {
        this(c, false, null, null,
                Validator.VAR_GENERIC);
    }

    public ConstraintsParameters(Timestamp jarTime) {
        this(null, false, null, jarTime, Validator.VAR_GENERIC);
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public AlgorithmParameters getAlgParams() {
        return algParams;
    }

    public Key getKey() {
        return key;
    }

    // Returns if the trust anchor has a match if anchor checking is enabled.
    public boolean isTrustedMatch() {
        return trustedMatch;
    }

    public X509Certificate getCertificate() {
        return cert;
    }

    public Date getPKIXParamDate() {
        return pkixDate;
    }

    public Timestamp getJARTimestamp() {
        return jarTimestamp;
    }

    public String getVariant() {
        return variant;
    }

    public String[] getNamedCurve() {
        return curveStr;
    }

    public static String[] getNamedCurveFromKey(Key key) {
        if (key instanceof ECKey) {
            NamedCurve nc = CurveDB.lookup(((ECKey)key).getParams());
            return (nc == null ? EMPTYLIST : nc.getNameAndAliases());
        } else if (key instanceof XECKey) {
            String[] s = {
                    ((NamedParameterSpec)((XECKey)key).getParams()).getName()
            };
            return s;
        } else {
            return EMPTYLIST;
        }
    }

    public String toString() {
        StringBuilder s = new StringBuilder();
        s.append("Cert:       ");
        if (cert != null) {
            s.append(cert.toString());
            s.append("\nSigAlgo:    ");
            s.append(cert.getSigAlgName());
        } else {
            s.append("None");
        }
        s.append("\nAlgParams:  ");
        if (getAlgParams() != null) {
            getAlgParams().toString();
        } else {
            s.append("None");
        }
        s.append("\nNamedCurves: ");
        for (String c : getNamedCurve()) {
            s.append(c + " ");
        }
        s.append("\nVariant:    " + getVariant());
        return s.toString();
    }

}
