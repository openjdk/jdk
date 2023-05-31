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

public class SSLCA {

    public static void main(String[] args) throws Exception {

        if (args.length >= 1 && "CRL".equalsIgnoreCase(args[0])) {
            ValidatePathWithURL.enableCRLOnly();
        } else {
            // OCSP check by default
            ValidatePathWithURL.enableOCSPOnly();
        }

        new SSLCA_RSA().runTest();
        new SSLCA_EV_RSA().runTest();
        new SSLCA_ECC().runTest();
    }
}

class SSLCA_RSA {
    private static final String VALID = "https://test-dv-rsa.ssl.com";
    private static final String REVOKED = "https://revoked-rsa-dv.ssl.com";
    private static final String CA_ALIAS = "sslrootrsaca [jdk]";

    public void runTest() throws Exception {
        ValidatePathWithURL validatePathWithURL = new ValidatePathWithURL(CA_ALIAS);

        validatePathWithURL.validateDomain(VALID, false);
        validatePathWithURL.validateDomain(REVOKED, true);
    }
}

class SSLCA_EV_RSA {
    private static final String VALID = "https://test-ev-rsa.ssl.com";
    private static final String REVOKED = "https://revoked-rsa-ev.ssl.com";
    private static final String CA_ALIAS = "sslrootevrsaca [jdk]";

    public void runTest() throws Exception {
        ValidatePathWithURL validatePathWithURL = new ValidatePathWithURL(CA_ALIAS);

        validatePathWithURL.validateDomain(VALID, false);
        validatePathWithURL.validateDomain(REVOKED, true);
    }
}

class SSLCA_ECC {
    private static final String VALID = "https://test-dv-ecc.ssl.com";
    private static final String REVOKED = "https://revoked-ecc-dv.ssl.com";
    private static final String CA_ALIAS = "sslrooteccca [jdk]";

    public void runTest() throws Exception {
        ValidatePathWithURL validatePathWithURL = new ValidatePathWithURL(CA_ALIAS);

        validatePathWithURL.validateDomain(VALID, false);
        validatePathWithURL.validateDomain(REVOKED, true);
    }
}
