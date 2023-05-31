/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8307134
 * @summary Interoperability tests with Google's GlobalSign R4 and GTS Root certificates
 * @library /test/lib
 * @build jtreg.SkippedException
 * @build ValidatePathWithURL
 * @run main/othervm -Djava.security.debug=certpath GoogleCA OCSP
 */

/*
 * @test id=CRL
 * @bug 8307134
 * @summary Interoperability tests with Google's GlobalSign R4 and GTS Root certificates
 * @library /test/lib
 * @build jtreg.SkippedException
 * @build ValidatePathWithURL
 * @run main/othervm -Djava.security.debug=certpath GoogleCA CRL
 */

import java.security.cert.CertificateEncodingException;

/*
 * Obtain TLS test artifacts for Google CAs from:
 *
 * https://pki.goog/repository/
 */
public class GoogleCA {

    public static void main(String[] args) throws Exception {

        ValidatePathWithURL pathValidator = new ValidatePathWithURL();

        if (args.length >= 1 && "CRL".equalsIgnoreCase(args[0])) {
            pathValidator.enableCRLOnly();
        } else {
            // OCSP check by default
            pathValidator.enableOCSPOnly();
        }

        new GoogleGSR4().runTest(pathValidator);
        new GoogleGTSR1().runTest(pathValidator);
        new GoogleGTSR2().runTest(pathValidator);
        new GoogleGTSR3().runTest(pathValidator);
        new GoogleGTSR4().runTest(pathValidator);
    }
}

class GoogleGSR4 {
    private static final String VALID = "https://good.gsr4.demo.pki.goog/";
    private static final String REVOKED = "https://revoked.gsr4.demo.pki.goog/";
    private static final String CA_FINGERPRINT =
            "BE:C9:49:11:C2:95:56:76:DB:6C:0A:55:09:86:D7:6E:3B:A0:05:66:7C:44:2C:97:62:B4:FB:B7:73:DE:22:8C";

    public void runTest(ValidatePathWithURL pathValidator) throws CertificateEncodingException {
        pathValidator.validateDomain(VALID, false, CA_FINGERPRINT);
        pathValidator.validateDomain(REVOKED, true, CA_FINGERPRINT);
    }
}

class GoogleGTSR1 {
    private static final String VALID = "https://good.gtsr1.demo.pki.goog/";
    private static final String REVOKED = "https://revoked.gtsr1.demo.pki.goog/";
    private static final String CA_FINGERPRINT =
            "D9:47:43:2A:BD:E7:B7:FA:90:FC:2E:6B:59:10:1B:12:80:E0:E1:C7:E4:E4:0F:A3:C6:88:7F:FF:57:A7:F4:CF";

    public void runTest(ValidatePathWithURL pathValidator) throws CertificateEncodingException {
        pathValidator.validateDomain(VALID, false, CA_FINGERPRINT);
        pathValidator.validateDomain(REVOKED, true, CA_FINGERPRINT);
    }
}

class GoogleGTSR2 {
    private static final String VALID = "https://good.gtsr2.demo.pki.goog/";
    private static final String REVOKED = "https://revoked.gtsr2.demo.pki.goog/";
    private static final String CA_FINGERPRINT =
            "8D:25:CD:97:22:9D:BF:70:35:6B:DA:4E:B3:CC:73:40:31:E2:4C:F0:0F:AF:CF:D3:2D:C7:6E:B5:84:1C:7E:A8";

    public void runTest(ValidatePathWithURL pathValidator) throws CertificateEncodingException {
        pathValidator.validateDomain(VALID, false, CA_FINGERPRINT);
        pathValidator.validateDomain(REVOKED, true, CA_FINGERPRINT);
    }
}

class GoogleGTSR3 {
    private static final String VALID = "https://good.gtsr3.demo.pki.goog/";
    private static final String REVOKED = "https://revoked.gtsr3.demo.pki.goog/";
    private static final String CA_FINGERPRINT =
            "34:D8:A7:3E:E2:08:D9:BC:DB:0D:95:65:20:93:4B:4E:40:E6:94:82:59:6E:8B:6F:73:C8:42:6B:01:0A:6F:48";

    public void runTest(ValidatePathWithURL pathValidator) throws CertificateEncodingException {
        pathValidator.validateDomain(VALID, false, CA_FINGERPRINT);
        pathValidator.validateDomain(REVOKED, true, CA_FINGERPRINT);
    }
}

class GoogleGTSR4 {
    private static final String VALID = "https://good.gtsr4.demo.pki.goog/";
    private static final String REVOKED = "https://revoked.gtsr4.demo.pki.goog/";
    private static final String CA_FINGERPRINT =
            "34:9D:FA:40:58:C5:E2:63:12:3B:39:8A:E7:95:57:3C:4E:13:13:C8:3F:E6:8F:93:55:6C:D5:E8:03:1B:3C:7D";

    public void runTest(ValidatePathWithURL pathValidator) throws CertificateEncodingException {
        pathValidator.validateDomain(VALID, false, CA_FINGERPRINT);
        pathValidator.validateDomain(REVOKED, true, CA_FINGERPRINT);
    }
}
