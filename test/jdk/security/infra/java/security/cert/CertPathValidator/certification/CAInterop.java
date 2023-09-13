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
 * @test id=actalisauthenticationrootca
 * @bug 8189131
 * @summary Interoperability tests with Actalis CA
 * @library /test/lib
 * @build jtreg.SkippedException ValidatePathWithURL CAInterop
 * @run main/othervm -Djava.security.debug=certpath,ocsp
 *  CAInterop actalisauthenticationrootca OCSP
 * @run main/othervm/timeout=180 -Djava.security.debug=certpath,ocsp
 *  CAInterop actalisauthenticationrootca CRL
 */

/*
 * @test id=amazonrootca1
 * @bug 8233223
 * @summary Interoperability tests with Amazon's CA1
 * @library /test/lib
 * @build jtreg.SkippedException ValidatePathWithURL CAInterop
 * @run main/othervm -Djava.security.debug=certpath,ocsp CAInterop amazonrootca1 OCSP
 * @run main/othervm -Djava.security.debug=certpath CAInterop amazonrootca1 CRL
 */

/*
 * @test id=amazonrootca2
 * @bug 8233223
 * @summary Interoperability tests with Amazon's CA2
 * @library /test/lib
 * @build jtreg.SkippedException ValidatePathWithURL CAInterop
 * @run main/othervm -Djava.security.debug=certpath,ocsp CAInterop amazonrootca2 OCSP
 * @run main/othervm -Djava.security.debug=certpath CAInterop amazonrootca2 CRL
 */

/*
 * @test id=amazonrootca3
 * @bug 8233223
 * @summary Interoperability tests with Amazon's CA3
 * @library /test/lib
 * @build jtreg.SkippedException ValidatePathWithURL CAInterop
 * @run main/othervm -Djava.security.debug=certpath,ocsp CAInterop amazonrootca3 OCSP
 * @run main/othervm -Djava.security.debug=certpath CAInterop amazonrootca3 CRL
 */

/*
 * @test id=amazonrootca4
 * @bug 8233223
 * @summary Interoperability tests with Amazon's CA4
 * @library /test/lib
 * @build jtreg.SkippedException ValidatePathWithURL CAInterop
 * @run main/othervm -Djava.security.debug=certpath,ocsp CAInterop amazonrootca4 OCSP
 * @run main/othervm -Djava.security.debug=certpath CAInterop amazonrootca4 CRL
 */

/*
 * @test id=buypassclass2ca
 * @bug 8189131
 * @summary Interoperability tests with Buypass Class 2 CA
 * @library /test/lib
 * @build jtreg.SkippedException ValidatePathWithURL CAInterop
 * @run main/othervm -Djava.security.debug=certpath,ocsp CAInterop buypassclass2ca OCSP
 * @run main/othervm -Djava.security.debug=certpath,ocsp CAInterop buypassclass2ca CRL
 */

/*
 * @test id=buypassclass3ca
 * @bug 8189131
 * @summary Interoperability tests with Buypass Class 3 CA
 * @library /test/lib
 * @build jtreg.SkippedException ValidatePathWithURL CAInterop
 * @run main/othervm -Djava.security.debug=certpath,ocsp CAInterop buypassclass3ca OCSP
 * @run main/othervm -Djava.security.debug=certpath,ocsp CAInterop buypassclass3ca CRL
 */

/*
 * @test id=letsencryptisrgx1
 * @bug 8189131
 * @summary Interoperability tests with Let's Encrypt CA
 * @library /test/lib
 * @build jtreg.SkippedException ValidatePathWithURL CAInterop
 * @run main/othervm -Djava.security.debug=certpath,ocsp CAInterop letsencryptisrgx1 DEFAULT
 */

/*
 * @test id=globalsignrootcar6
 * @bug 8216577
 * @summary Interoperability tests with GlobalSign R6 CA
 * @library /test/lib
 * @build jtreg.SkippedException ValidatePathWithURL CAInterop
 * @run main/othervm -Djava.security.debug=certpath,ocsp CAInterop globalsignrootcar6 OCSP
 * @run main/othervm -Djava.security.debug=certpath CAInterop globalsignrootcar6 CRL
 */
public class CAInterop {

    /**
     * Returns the test configuration for CA
     *
     * @param alias from the cacerts file without [jdk]
     * @return CATestTuple
     */
    private CATestTuple getTestURLs(String alias) {
        return switch (alias) {
            case "actalisauthenticationrootca" ->
                    new CATestTuple("https://ssltest-active.actalis.it",
                            "https://ssltest-revoked.actalis.it");

            case "amazonrootca1" ->
                    new CATestTuple("https://valid.rootca1.demo.amazontrust.com",
                    "https://revoked.rootca1.demo.amazontrust.com");
            case "amazonrootca2" ->
                    new CATestTuple("https://valid.rootca2.demo.amazontrust.com",
                    "https://revoked.rootca2.demo.amazontrust.com");
            case "amazonrootca3" ->
                    new CATestTuple("https://valid.rootca3.demo.amazontrust.com",
                    "https://revoked.rootca3.demo.amazontrust.com");
            case "amazonrootca4" ->
                    new CATestTuple("https://valid.rootca4.demo.amazontrust.com",
                    "https://revoked.rootca4.demo.amazontrust.com");

            case "buypassclass2ca" ->
                    new CATestTuple("https://valid.business.ca22.ssl.buypass.no",
                    "https://revoked.business.ca22.ssl.buypass.no");
            case "buypassclass3ca" ->
                    new CATestTuple("https://valid.qcevident.ca23.ssl.buypass.no",
                    "https://revoked.qcevident.ca23.ssl.buypass.no");

            case "letsencryptisrgx1" ->
                    new CATestTuple("https://valid-isrgrootx1.letsencrypt.org",
                            "https://revoked-isrgrootx1.letsencrypt.org");

            case "globalsignrootcar6" ->
                    new CATestTuple("https://valid.r6.roots.globalsign.com",
                            "https://revoked.r6.roots.globalsign.com");

            default -> throw new RuntimeException("No test setup found for: " + alias);
        };
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new RuntimeException("Run as: CAInterop <alias> <OCSP/CRL/DEFAULT>");
        }

        String caAlias = args[0];

        CAInterop caInterop = new CAInterop(args[1]);
        CATestTuple caTestTuple = caInterop.getTestURLs(caAlias);

        caInterop.validate(caAlias + " [jdk]",
                caTestTuple.getVALID_URL(),
                caTestTuple.getREVOKED_URL());
    }

    static class CATestTuple {
        final String VALID_URL;
        final String REVOKED_URL;

        public CATestTuple(String validURL,
                           String revokedURL) {
            VALID_URL = validURL;
            REVOKED_URL = revokedURL;
        }

        public String getVALID_URL() {
            return VALID_URL;
        }

        public String getREVOKED_URL() {
            return REVOKED_URL;
        }
    }

    /**
     * Constructor for interoperability test with third party CA.
     *
     * @param revocationMode revocation checking mode to use
     */
    public CAInterop(String revocationMode) {
        if ("CRL".equalsIgnoreCase(revocationMode)) {
            ValidatePathWithURL.enableCRLOnly();
        } else if ("OCSP".equalsIgnoreCase(revocationMode)) {
            ValidatePathWithURL.enableOCSPOnly();
        } else {
            // OCSP and CRL check by default
            ValidatePathWithURL.enableOCSPAndCRL();
        }

        ValidatePathWithURL.logRevocationSettings();
    }

    /**
     * Validates provided URLs using <code>HttpsURLConnection</code> making sure they
     * anchor to the root CA found in <code>cacerts</code> using provided alias.
     *
     * @param caAlias        CA alis from <code>cacerts</code> file
     * @param validCertURL   valid test URL
     * @param revokedCertURL revoked test URL
     * @throws Exception thrown when certificate can't be validated as valid or revoked
     */
    public void validate(String caAlias,
                         String validCertURL,
                         String revokedCertURL) throws Exception {

        ValidatePathWithURL validatePathWithURL = new ValidatePathWithURL(caAlias);

        if (validCertURL != null) {
            validatePathWithURL.validateDomain(validCertURL, false);
        }

        if (revokedCertURL != null) {
            validatePathWithURL.validateDomain(revokedCertURL, true);
        }
    }
}
