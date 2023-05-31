/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8189131
 * @summary Interoperability tests with QuoVadis Root CA1, CA2, and CA3 G3 CAs using OCSP
 * @library /test/lib
 * @build jtreg.SkippedException
 * @build ValidatePathWithURL
 * @run main/othervm -Djava.security.debug=certpath QuoVadisCA OCSP
 */

/*
 * @test id=CRL
 * @bug 8189131
 * @summary Interoperability tests with QuoVadis Root CA1, CA2, and CA3 G3 CAs using CRL
 * @library /test/lib
 * @build jtreg.SkippedException
 * @build ValidatePathWithURL
 * @run main/othervm -Djava.security.debug=certpath QuoVadisCA CRL
 */

import java.security.cert.CertificateEncodingException;

/*
 * Obtain TLS test artifacts for QuoVadis CAs from:
 *
 * https://www.quovadisglobal.com/download-roots-crl/
 *
 */
public class QuoVadisCA {
    public static void main(String[] args) throws Exception {

        ValidatePathWithURL pathValidator = new ValidatePathWithURL();

        if (args.length >= 1 && "CRL".equalsIgnoreCase(args[0])) {
            pathValidator.enableCRLOnly();
        } else {
            // OCSP check by default
            pathValidator.enableOCSPOnly();
        }

        new RootCA1G3().runTest(pathValidator);
        new RootCA2G3().runTest(pathValidator);
        new RootCA3G3().runTest(pathValidator);
    }
}

class RootCA1G3 {
    private static final String VALID = "https://quovadis-root-ca-1-g3.chain-demos.digicert.com/";
    private static final String REVOKED = "https://quovadis-root-ca-1-g3-revoked.chain-demos.digicert.com/";
    private static final String CA_FINGERPRINT =
            "8A:86:6F:D1:B2:76:B5:7E:57:8E:92:1C:65:82:8A:2B:ED:58:E9:F2:F2:88:05:41:34:B7:F1:F4:BF:C9:CC:74";

    public void runTest(ValidatePathWithURL pathValidator) throws CertificateEncodingException {
        pathValidator.validateDomain(VALID, false, CA_FINGERPRINT);
        pathValidator.validateDomain(REVOKED, true, CA_FINGERPRINT);
    }
}

class RootCA2G3 {
    private static final String VALID = "https://quovadis-root-ca-2-g3.chain-demos.digicert.com";
    private static final String REVOKED = "https://quovadis-root-ca-2-g3-revoked.chain-demos.digicert.com";
    private static final String CA_FINGERPRINT =
            "8F:E4:FB:0A:F9:3A:4D:0D:67:DB:0B:EB:B2:3E:37:C7:1B:F3:25:DC:BC:DD:24:0E:A0:4D:AF:58:B4:7E:18:40";

    public void runTest(ValidatePathWithURL pathValidator) throws CertificateEncodingException {
        pathValidator.validateDomain(VALID, false, CA_FINGERPRINT);
        pathValidator.validateDomain(REVOKED, true, CA_FINGERPRINT);
    }
}

class RootCA3G3 {
    private static final String VALID = "https://quovadis-root-ca-3-g3.chain-demos.digicert.com";
    private static final String REVOKED = "https://quovadis-root-ca-3-g3-revoked.chain-demos.digicert.com";
    private static final String CA_FINGERPRINT =
            "88:EF:81:DE:20:2E:B0:18:45:2E:43:F8:64:72:5C:EA:5F:BD:1F:C2:D9:D2:05:73:07:09:C5:D8:B8:69:0F:46";

    public void runTest(ValidatePathWithURL pathValidator) throws CertificateEncodingException {
        pathValidator.validateDomain(VALID, false, CA_FINGERPRINT);
        pathValidator.validateDomain(REVOKED, true, CA_FINGERPRINT);
    }
}
