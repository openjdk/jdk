/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package sun.security.validator;

import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Map;
import java.util.Set;

import sun.security.util.Debug;
import sun.security.x509.X509CertImpl;

/**
 * This class checks if Entrust issued TLS Server certificates should be
 * restricted.
 */
final class EntrustTLSPolicy {

    private static final Debug debug = Debug.getInstance("certpath");

    // SHA-256 certificate fingerprints of distrusted roots
    private static final Set<String> FINGERPRINTS = Set.of(
        // cacerts alias: entrustevca
        // DN: CN=Entrust Root Certification Authority,
        //     OU=(c) 2006 Entrust, Inc.,
        //     OU=www.entrust.net/CPS is incorporated by reference,
        //     O=Entrust, Inc., C=US
        "73C176434F1BC6D5ADF45B0E76E727287C8DE57616C1E6E6141A2B2CBC7D8E4C",
        // cacerts alias: entrustrootcaec1
        // DN: CN=Entrust Root Certification Authority - EC1,
        //     OU=(c) 2012 Entrust, Inc. - for authorized use only,
        //     OU=See www.entrust.net/legal-terms, O=Entrust, Inc., C=US
        "02ED0EB28C14DA45165C566791700D6451D7FB56F0B2AB1D3B8EB070E56EDFF5",
        // cacerts alias: entrustrootcag2
        // DN: CN=Entrust Root Certification Authority - G2,
        //     OU=(c) 2009 Entrust, Inc. - for authorized use only,
        //     OU=See www.entrust.net/legal-terms, O=Entrust, Inc., C=US
        "43DF5774B03E7FEF5FE40D931A7BEDF1BB2E6B42738C4E6D3841103D3AA7F339",
        // cacerts alias: entrustrootcag4
        // DN: CN=Entrust Root Certification Authority - G4
        //     OU=(c) 2015 Entrust, Inc. - for authorized use only,
        //     OU=See www.entrust.net/legal-terms, O=Entrust, Inc., C=US,
        "DB3517D1F6732A2D5AB97C533EC70779EE3270A62FB4AC4238372460E6F01E88",
        // cacerts alias: entrust2048ca
        // DN: CN=Entrust.net Certification Authority (2048),
        //     OU=(c) 1999 Entrust.net Limited,
        //     OU=www.entrust.net/CPS_2048 incorp. by ref. (limits liab.),
        //     O=Entrust.net
        "6DC47172E01CBCB0BF62580D895FE2B8AC9AD4F873801E0C10B9C837D21EB177"
    );

    // Any TLS Server certificate that is anchored by one of the Entrust
    // roots above and is issued after this date will be distrusted.
    private static final LocalDate NOVEMBER_11_2024 =
        LocalDate.of(2024, Month.NOVEMBER, 11);

    /**
     * This method assumes the eeCert is a TLS Server Cert and chains back to
     * the anchor.
     *
     * @param chain the end-entity's certificate chain. The end entity cert
     *              is at index 0, the trust anchor at index n-1.
     * @throws ValidatorException if the certificate is distrusted
     */
    static void checkDistrust(X509Certificate[] chain)
                              throws ValidatorException {
        X509Certificate anchor = chain[chain.length-1];
        String fp = fingerprint(anchor);
        if (fp == null) {
            throw new ValidatorException("Cannot generate fingerprint for "
                + "trust anchor of TLS server certificate");
        }
        if (FINGERPRINTS.contains(fp)) {
            Date notBefore = chain[0].getNotBefore();
            LocalDate ldNotBefore = LocalDate.ofInstant(notBefore.toInstant(),
                                                        ZoneOffset.UTC);
            // reject if certificate is issued after November 11, 2024
            checkNotBefore(ldNotBefore, NOVEMBER_11_2024, anchor);
        }
    }

    private static String fingerprint(X509Certificate cert) {
        return X509CertImpl.getFingerprint("SHA-256", cert, debug);
    }

    private static void checkNotBefore(LocalDate notBeforeDate,
            LocalDate distrustDate, X509Certificate anchor)
            throws ValidatorException {
        if (notBeforeDate.isAfter(distrustDate)) {
            throw new ValidatorException
                ("TLS Server certificate issued after " + distrustDate +
                 " and anchored by a distrusted legacy Entrust root CA: "
                 + anchor.getSubjectX500Principal(),
                 ValidatorException.T_UNTRUSTED_CERT, anchor);
        }
    }

    private EntrustTLSPolicy() {}
}
