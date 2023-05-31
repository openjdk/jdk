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
 * @summary Interoperability tests with Comodo RSA, ECC, userTrust RSA, and
 *          userTrust ECC CAs
 * @library /test/lib
 * @build jtreg.SkippedException
 * @build ValidatePathWithURL
 * @run main/othervm -Djava.security.debug=certpath ComodoCA OCSP
 */

/*
 * @test id=CRL
 * @bug 8189131
 * @summary Interoperability tests with Comodo RSA, ECC, userTrust RSA, and
 *          userTrust ECC CAs
 * @library /test/lib
 * @build jtreg.SkippedException
 * @build ValidatePathWithURL
 * @run main/othervm -Djava.security.debug=certpath ComodoCA CRL
 */

public class ComodoCA {

    public static void main(String[] args) throws Exception {

        if (args.length >= 1 && "CRL".equalsIgnoreCase(args[0])) {
            ValidatePathWithURL.enableCRLOnly();
        } else {
            // OCSP check by default
            ValidatePathWithURL.enableOCSPOnly();
        }

        new ComodoRSA().runTest();
        new ComodoECC().runTest();
        new ComodoUserTrustRSA().runTest();
        new ComodoUserTrustECC().runTest();
    }
}

class ComodoRSA {
    private static final String VALID = "https://comodorsacertificationauthority-ev.comodoca.com";
    private static final String REVOKED = "https://comodorsacertificationauthority-ev.comodoca.com:444";
    private static final String CA_ALIAS = "comodorsaca [jdk]";

    public void runTest() throws Exception {
        ValidatePathWithURL validatePathWithURL = new ValidatePathWithURL(CA_ALIAS);

        validatePathWithURL.validateDomain(VALID, false);
        validatePathWithURL.validateDomain(REVOKED, true);
    }
}

class ComodoECC {
    private static final String VALID = "https://comodoecccertificationauthority-ev.comodoca.com";
    private static final String REVOKED = "https://comodoecccertificationauthority-ev.comodoca.com:444";
    private static final String CA_ALIAS = "comodoeccca [jdk]";

    public void runTest() throws Exception {
        ValidatePathWithURL validatePathWithURL = new ValidatePathWithURL(CA_ALIAS);

        validatePathWithURL.validateDomain(VALID, false);
        validatePathWithURL.validateDomain(REVOKED, true);
    }
}

class ComodoUserTrustRSA {
    private static final String VALID = "https://usertrustrsacertificationauthority-ev.comodoca.com";
    private static final String REVOKED = "https://usertrustrsacertificationauthority-ev.comodoca.com:444";
    private static final String CA_ALIAS = "usertrustrsaca [jdk]";

    public void runTest() throws Exception {
        ValidatePathWithURL validatePathWithURL = new ValidatePathWithURL(CA_ALIAS);

        validatePathWithURL.validateDomain(VALID, false);
        validatePathWithURL.validateDomain(REVOKED, true);
    }
}

class ComodoUserTrustECC {
    private static final String VALID = "https://usertrustecccertificationauthority-ev.comodoca.com";
    private static final String REVOKED = "https://usertrustecccertificationauthority-ev.comodoca.com:444";
    private static final String CA_ALIAS = "usertrusteccca [jdk]";

    public void runTest() throws Exception {
        ValidatePathWithURL validatePathWithURL = new ValidatePathWithURL(CA_ALIAS);

        validatePathWithURL.validateDomain(VALID, false);
        validatePathWithURL.validateDomain(REVOKED, true);
    }
}
