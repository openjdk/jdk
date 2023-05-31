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

public class BuypassCA {

    public static void main(String[] args) throws Exception {

        if (args.length >= 1 && "CRL".equalsIgnoreCase(args[0])) {
            ValidatePathWithURL.enableCRLOnly();
        } else {
            // OCSP check by default
            ValidatePathWithURL.enableOCSPOnly();
        }

        new BuypassClass2().runTest();
        new BuypassClass3().runTest();
    }
}

class BuypassClass2 {
    private static final String VALID_BUSINESS = "https://valid.business.ca22.ssl.buypass.no";
    private static final String REVOKED_BUSINESS = "https://revoked.business.ca22.ssl.buypass.no";
    private static final String VALID_DOMAIN = "https://valid.domain.ca22.ssl.buypass.no";
    private static final String REVOKED_DOMAIN = "https://revoked.domain.ca22.ssl.buypass.no";
    private static final String CA_ALIAS = "buypassclass2ca [jdk]";

    public void runTest() throws Exception {
        ValidatePathWithURL validatePathWithURL = new ValidatePathWithURL(CA_ALIAS);

        validatePathWithURL.validateDomain(VALID_BUSINESS, false);
        validatePathWithURL.validateDomain(REVOKED_BUSINESS, true);

        validatePathWithURL.validateDomain(VALID_DOMAIN, false);
        validatePathWithURL.validateDomain(REVOKED_DOMAIN, true);
    }
}

class BuypassClass3 {
    private static final String VALID_QC = "https://valid.qcevident.ca23.ssl.buypass.no";
    private static final String REVOKED_QC = "https://revoked.qcevident.ca23.ssl.buypass.no";
    private static final String VALID_EVIDENT = "https://valid.evident.ca23.ssl.buypass.no";
    private static final String REVOKED_EVIDENT = "https://revoked.evident.ca23.ssl.buypass.no";
    private static final String VALID_BUSINESSPLUS = "https://valid.businessplus.ca23.ssl.buypass.no";
    private static final String REVOKED_BUSINESSPLUS = "https://revoked.businessplus.ca23.ssl.buypass.no";
    private static final String CA_ALIAS = "buypassclass3ca [jdk]";

    public void runTest() throws Exception {
        ValidatePathWithURL validatePathWithURL = new ValidatePathWithURL(CA_ALIAS);

        validatePathWithURL.validateDomain(VALID_QC, false);
        validatePathWithURL.validateDomain(REVOKED_QC, true);

        validatePathWithURL.validateDomain(VALID_EVIDENT, false);
        validatePathWithURL.validateDomain(REVOKED_EVIDENT, true);

        validatePathWithURL.validateDomain(VALID_BUSINESSPLUS, false);
        validatePathWithURL.validateDomain(REVOKED_BUSINESSPLUS, true);
    }
}
