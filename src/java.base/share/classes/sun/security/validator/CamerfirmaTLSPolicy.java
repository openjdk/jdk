/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * This class checks if Camerfirma issued TLS Server certificates should be
 * restricted.
 */
final class CamerfirmaTLSPolicy {

    private static final Debug debug = Debug.getInstance("certpath");

    // SHA-256 certificate fingerprint of distrusted root for TLS
    // cacerts alias: camerfirmachambersca
    // DN: CN=Chambers of Commerce Root - 2008,
    //     O=AC Camerfirma S.A., SERIALNUMBER=A82743287,
    //     L=Madrid (see current address at www.camerfirma.com/address),
    //     C=EU
    private static final String FINGERPRINT =
            "063E4AFAC491DFD332F3089B8542E94617D893D7FE944E10A7937EE29D9693C0";

    // Any TLS Server certificate that is anchored by one of the Camerfirma
    // roots above and is issued after this date will be distrusted.
    private static final LocalDate APRIL_15_2025 =
        LocalDate.of(2025, Month.APRIL, 15);

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
        if (FINGERPRINT.equalsIgnoreCase(fp)) {
            Date notBefore = chain[0].getNotBefore();
            LocalDate ldNotBefore = LocalDate.ofInstant(notBefore.toInstant(),
                                                        ZoneOffset.UTC);
            // reject if certificate is issued after April 15, 2025
            checkNotBefore(ldNotBefore, APRIL_15_2025, anchor);
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
                 " and anchored by a distrusted legacy Camerfirma root CA: "
                 + anchor.getSubjectX500Principal(),
                 ValidatorException.T_UNTRUSTED_CERT, anchor);
        }
    }

    private CamerfirmaTLSPolicy() {}
}
