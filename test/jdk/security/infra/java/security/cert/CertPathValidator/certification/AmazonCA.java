/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8233223
 * @summary Interoperability tests with Amazon's CA1, CA2, CA3, and CA4
 * @library /test/lib
 * @build jtreg.SkippedException
 * @build ValidatePathWithURL
 * @run main/othervm -Djava.security.debug=certpath AmazonCA OCSP
 */

/*
 * @test id=CRL
 * @bug 8233223
 * @summary Interoperability tests with Amazon's CA1, CA2, CA3, and CA4
 * @library /test/lib
 * @build jtreg.SkippedException
 * @build ValidatePathWithURL
 * @run main/othervm -Djava.security.debug=certpath AmazonCA CRL
 */

import java.security.cert.CertificateEncodingException;

/*
 * Obtain TLS test artifacts for Amazon CAs from:
 *
 * https://www.amazontrust.com/repository/
 */
public class AmazonCA {

    public static void main(String[] args) throws Exception {

        ValidatePathWithURL pathValidator = new ValidatePathWithURL();

        if (args.length >= 1 && "CRL".equalsIgnoreCase(args[0])) {
            pathValidator.enableCRLOnly();
        } else {
            // OCSP check by default
            pathValidator.enableOCSPOnly();
        }

        new AmazonCA_1().runTest(pathValidator);
        new AmazonCA_2().runTest(pathValidator);
        new AmazonCA_3().runTest(pathValidator);
        new AmazonCA_4().runTest(pathValidator);
    }
}

class AmazonCA_1 {
    private static final String VALID = "https://valid.rootca1.demo.amazontrust.com/";
    private static final String REVOKED = "https://revoked.rootca1.demo.amazontrust.com/";
    private static final String CA_FINGERPRINT =
            "8E:CD:E6:88:4F:3D:87:B1:12:5B:A3:1A:C3:FC:B1:3D:70:16:DE:7F:57:CC:90:4F:E1:CB:97:C6:AE:98:19:6E";

    public void runTest(ValidatePathWithURL pathValidator) throws CertificateEncodingException {
        pathValidator.validateDomain(VALID, false, CA_FINGERPRINT);
        pathValidator.validateDomain(REVOKED, true, CA_FINGERPRINT);
    }
}

class AmazonCA_2 {
    private static final String VALID = "https://valid.rootca2.demo.amazontrust.com/";
    private static final String REVOKED = "https://revoked.rootca2.demo.amazontrust.com/";
    private static final String CA_FINGERPRINT =
            "1B:A5:B2:AA:8C:65:40:1A:82:96:01:18:F8:0B:EC:4F:62:30:4D:83:CE:C4:71:3A:19:C3:9C:01:1E:A4:6D:B4";

    public void runTest(ValidatePathWithURL pathValidator) throws CertificateEncodingException {
        pathValidator.validateDomain(VALID, false, CA_FINGERPRINT);
        pathValidator.validateDomain(REVOKED, true, CA_FINGERPRINT);
    }
}

class AmazonCA_3 {
    private static final String VALID = "https://valid.rootca3.demo.amazontrust.com/";
    private static final String REVOKED = "https://revoked.rootca3.demo.amazontrust.com/";
    private static final String CA_FINGERPRINT =
            "18:CE:6C:FE:7B:F1:4E:60:B2:E3:47:B8:DF:E8:68:CB:31:D0:2E:BB:3A:DA:27:15:69:F5:03:43:B4:6D:B3:A4";

    public void runTest(ValidatePathWithURL pathValidator) throws CertificateEncodingException {
        pathValidator.validateDomain(VALID, false, CA_FINGERPRINT);
        pathValidator.validateDomain(REVOKED, true, CA_FINGERPRINT);
    }
}

class AmazonCA_4 {
    private static final String VALID = "https://valid.rootca4.demo.amazontrust.com/";
    private static final String REVOKED = "https://revoked.rootca4.demo.amazontrust.com/";
    private static final String CA_FINGERPRINT =
            "E3:5D:28:41:9E:D0:20:25:CF:A6:90:38:CD:62:39:62:45:8D:A5:C6:95:FB:DE:A3:C2:2B:0B:FB:25:89:70:92";

    public void runTest(ValidatePathWithURL pathValidator) throws CertificateEncodingException {
        pathValidator.validateDomain(VALID, false, CA_FINGERPRINT);
        pathValidator.validateDomain(REVOKED, true, CA_FINGERPRINT);
    }
}
