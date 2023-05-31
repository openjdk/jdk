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

/*
 * Obtain TLS test artifacts for Google CAs from:
 *
 * https://pki.goog/repository/
 */
public class GoogleCA {

    public static void main(String[] args) throws Exception {

        if (args.length >= 1 && "CRL".equalsIgnoreCase(args[0])) {
            ValidatePathWithURL.enableCRLOnly();
        } else {
            // OCSP check by default
            ValidatePathWithURL.enableOCSPOnly();
        }

        new GoogleGSR4().runTest();
        new GoogleGTSR1().runTest();
        new GoogleGTSR2().runTest();
        new GoogleGTSR3().runTest();
        new GoogleGTSR4().runTest();
    }
}

class GoogleGSR4 {
    private static final String VALID = "https://good.gsr4.demo.pki.goog/";
    private static final String REVOKED = "https://revoked.gsr4.demo.pki.goog/";
    private static final String CA_ALIAS = "globalsigneccrootcar4 [jdk]";

    public void runTest() throws Exception {
        ValidatePathWithURL validatePathWithURL = new ValidatePathWithURL(CA_ALIAS);

        validatePathWithURL.validateDomain(VALID, false);
        validatePathWithURL.validateDomain(REVOKED, true);
    }
}

class GoogleGTSR1 {
    private static final String VALID = "https://good.gtsr1.demo.pki.goog/";
    private static final String REVOKED = "https://revoked.gtsr1.demo.pki.goog/";
    private static final String CA_ALIAS = "gtsrootcar1 [jdk]";

    public void runTest() throws Exception {
        ValidatePathWithURL validatePathWithURL = new ValidatePathWithURL(CA_ALIAS);

        validatePathWithURL.validateDomain(VALID, false);
        validatePathWithURL.validateDomain(REVOKED, true);
    }
}

class GoogleGTSR2 {
    private static final String VALID = "https://good.gtsr2.demo.pki.goog/";
    private static final String REVOKED = "https://revoked.gtsr2.demo.pki.goog/";
    private static final String CA_ALIAS = "gtsrootcar2 [jdk]";

    public void runTest() throws Exception {
        ValidatePathWithURL validatePathWithURL = new ValidatePathWithURL(CA_ALIAS);

        validatePathWithURL.validateDomain(VALID, false);
        validatePathWithURL.validateDomain(REVOKED, true);
    }
}

class GoogleGTSR3 {
    private static final String VALID = "https://good.gtsr3.demo.pki.goog/";
    private static final String REVOKED = "https://revoked.gtsr3.demo.pki.goog/";
    private static final String CA_ALIAS = "gtsrootecccar3 [jdk]";

    public void runTest() throws Exception {
        ValidatePathWithURL validatePathWithURL = new ValidatePathWithURL(CA_ALIAS);

        validatePathWithURL.validateDomain(VALID, false);
        validatePathWithURL.validateDomain(REVOKED, true);
    }
}

class GoogleGTSR4 {
    private static final String VALID = "https://good.gtsr4.demo.pki.goog/";
    private static final String REVOKED = "https://revoked.gtsr4.demo.pki.goog/";
    private static final String CA_ALIAS = "gtsrootecccar4 [jdk]";

    public void runTest() throws Exception {
        ValidatePathWithURL validatePathWithURL = new ValidatePathWithURL(CA_ALIAS);

        validatePathWithURL.validateDomain(VALID, false);
        validatePathWithURL.validateDomain(REVOKED, true);
    }
}
