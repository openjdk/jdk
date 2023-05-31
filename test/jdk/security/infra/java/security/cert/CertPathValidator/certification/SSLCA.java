/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/*
 * @test id=OCSP
 * @bug 8243320 8256895
 * @summary Interoperability tests with SSL.com's RSA, EV RSA, and ECC CA
 * @library /test/lib
 * @build jtreg.SkippedException
 * @build ValidatePathWithURL
 * @run main/othervm -Djava.security.debug=certpath SSLCA OCSP
 */

/*
 * @test id=CRL
 * @bug 8243320 8256895
 * @summary Interoperability tests with SSL.com's RSA, EV RSA, and ECC CA
 * @library /test/lib
 * @build jtreg.SkippedException
 * @build ValidatePathWithURL
 * @run main/othervm -Djava.security.debug=certpath SSLCA CRL
 */

import java.security.cert.CertificateEncodingException;

public class SSLCA {

    public static void main(String[] args) throws Exception {

        System.setProperty("jdk.security.certpath.ocspNonce", "true");

        ValidatePathWithURL pathValidator = new ValidatePathWithURL();

        if (args.length >= 1 && "CRL".equalsIgnoreCase(args[0])) {
            pathValidator.enableCRLOnly();
        } else {
            // OCSP check by default
            pathValidator.enableOCSPOnly();
        }

        new SSLCA_RSA().runTest(pathValidator);
        new SSLCA_EV_RSA().runTest(pathValidator);
        new SSLCA_ECC().runTest(pathValidator);
    }
}

class SSLCA_RSA {
    private static final String VALID = "https://test-dv-rsa.ssl.com";
    private static final String REVOKED = "https://revoked-rsa-dv.ssl.com";
    private static final String CA_FINGERPRINT =
            "85:66:6A:56:2E:E0:BE:5C:E9:25:C1:D8:89:0A:6F:76:A8:7E:C1:6D:4D:7D:5F:29:EA:74:19:CF:20:12:3B:69";

    public void runTest(ValidatePathWithURL pathValidator) throws CertificateEncodingException {
        pathValidator.validateDomain(VALID, false, CA_FINGERPRINT);
        pathValidator.validateDomain(REVOKED, true, CA_FINGERPRINT);
    }
}

class SSLCA_EV_RSA {
    private static final String VALID = "https://test-ev-rsa.ssl.com";
    private static final String REVOKED = "https://revoked-rsa-ev.ssl.com";
    private static final String CA_FINGERPRINT =
            "2E:7B:F1:6C:C2:24:85:A7:BB:E2:AA:86:96:75:07:61:B0:AE:39:BE:3B:2F:E9:D0:CC:6D:4E:F7:34:91:42:5C";

    public void runTest(ValidatePathWithURL pathValidator) throws CertificateEncodingException {
        pathValidator.validateDomain(VALID, false, CA_FINGERPRINT);
        pathValidator.validateDomain(REVOKED, true, CA_FINGERPRINT);
    }
}

class SSLCA_ECC {
    private static final String VALID = "https://test-dv-ecc.ssl.com";
    private static final String REVOKED = "https://revoked-ecc-dv.ssl.com";
    private static final String CA_FINGERPRINT =
            "34:17:BB:06:CC:60:07:DA:1B:96:1C:92:0B:8A:B4:CE:3F:AD:82:0E:4A:A3:0B:9A:CB:C4:A7:4E:BD:CE:BC:65";

    public void runTest(ValidatePathWithURL pathValidator) throws CertificateEncodingException {
        pathValidator.validateDomain(VALID, false, CA_FINGERPRINT);
        pathValidator.validateDomain(REVOKED, true, CA_FINGERPRINT);
    }
}
