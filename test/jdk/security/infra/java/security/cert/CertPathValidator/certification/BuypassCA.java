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
 * @summary Interoperability tests with Buypass Class 2 and Class 3 CA
 * @library /test/lib
 * @build jtreg.SkippedException
 * @build ValidatePathWithURL
 * @run main/othervm/timeout=180 -Djava.security.debug=certpath BuypassCA OCSP
 */

/*
 * @test id=CRL
 * @bug 8189131
 * @summary Interoperability tests with Buypass Class 2 and Class 3 CA
 * @library /test/lib
 * @build jtreg.SkippedException
 * @build ValidatePathWithURL
 * @run main/othervm/timeout=180 -Djava.security.debug=certpath BuypassCA CRL
 */

import java.security.cert.CertificateEncodingException;
public class BuypassCA {

    public static void main(String[] args) throws Exception {

        ValidatePathWithURL pathValidator = new ValidatePathWithURL();

        if (args.length >= 1 && "CRL".equalsIgnoreCase(args[0])) {
            pathValidator.enableCRLOnly();
        } else {
            // OCSP check by default
            pathValidator.enableOCSPOnly();
        }

        new BuypassClass2().runTest(pathValidator);
        new BuypassClass3().runTest(pathValidator);
    }
}

class BuypassClass2 {
    private static final String VALID_BUSINESS = "https://valid.business.ca22.ssl.buypass.no";
    private static final String REVOKED_BUSINESS = "https://revoked.business.ca22.ssl.buypass.no";
    private static final String VALID_DOMAIN = "https://valid.domain.ca22.ssl.buypass.no";
    private static final String REVOKED_DOMAIN = "https://revoked.domain.ca22.ssl.buypass.no";
    private static final String CA_FINGERPRINT =
            "9A:11:40:25:19:7C:5B:B9:5D:94:E6:3D:55:CD:43:79:08:47:B6:46:B2:3C:DF:11:AD:A4:A0:0E:FF:15:FB:48";

    public void runTest(ValidatePathWithURL pathValidator) throws CertificateEncodingException {
        pathValidator.validateDomain(VALID_BUSINESS, false, CA_FINGERPRINT);
        pathValidator.validateDomain(REVOKED_BUSINESS, true, CA_FINGERPRINT);

        pathValidator.validateDomain(VALID_DOMAIN, false, CA_FINGERPRINT);
        pathValidator.validateDomain(REVOKED_DOMAIN, true, CA_FINGERPRINT);
    }
}

class BuypassClass3 {
    private static final String VALID_QC = "https://valid.qcevident.ca23.ssl.buypass.no";
    private static final String REVOKED_QC = "https://revoked.qcevident.ca23.ssl.buypass.no";
    private static final String VALID_EVIDENT = "https://valid.evident.ca23.ssl.buypass.no";
    private static final String REVOKED_EVIDENT = "https://revoked.evident.ca23.ssl.buypass.no";
    private static final String VALID_BUSINESSPLUS = "https://valid.businessplus.ca23.ssl.buypass.no";
    private static final String REVOKED_BUSINESSPLUS = "https://revoked.businessplus.ca23.ssl.buypass.no";
    private static final String CA_FINGERPRINT =
            "ED:F7:EB:BC:A2:7A:2A:38:4D:38:7B:7D:40:10:C6:66:E2:ED:B4:84:3E:4C:29:B4:AE:1D:5B:93:32:E6:B2:4D";

    public void runTest(ValidatePathWithURL pathValidator) throws CertificateEncodingException {
        pathValidator.validateDomain(VALID_QC, false, CA_FINGERPRINT);
        pathValidator.validateDomain(REVOKED_QC, true, CA_FINGERPRINT);

        pathValidator.validateDomain(VALID_EVIDENT, false, CA_FINGERPRINT);
        pathValidator.validateDomain(REVOKED_EVIDENT, true, CA_FINGERPRINT);

        pathValidator.validateDomain(VALID_BUSINESSPLUS, false, CA_FINGERPRINT);
        pathValidator.validateDomain(REVOKED_BUSINESSPLUS, true, CA_FINGERPRINT);
    }
}
