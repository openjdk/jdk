/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8195774 8243321
 * @summary Interoperability tests with Entrust CAs
 * @library /test/lib
 * @build jtreg.SkippedException
 * @build ValidatePathWithURL
 * @run main/othervm -Djava.security.debug=certpath EntrustCA OCSP
 */

/*
 * @test id=CRL
 * @bug 8195774 8243321
 * @summary Interoperability tests with Entrust CAs
 * @library /test/lib
 * @build jtreg.SkippedException
 * @build ValidatePathWithURL
 * @run main/othervm -Djava.security.debug=certpath EntrustCA CRL
 */

import java.security.cert.CertificateEncodingException;
public class EntrustCA {

    public static void main(String[] args) throws Exception {

            ValidatePathWithURL pathValidator = new ValidatePathWithURL();

            if (args.length >= 1 && "CRL".equalsIgnoreCase(args[0])) {
                pathValidator.enableCRLOnly();
            } else {
                // OCSP check by default
                pathValidator.enableOCSPOnly();
            }

        new Entrust_ECCA().runTest(pathValidator);
        new Entrust_G4().runTest(pathValidator);
    }
}

class Entrust_ECCA {
    private static final String VALID = "https://validec.entrust.net";
    private static final String REVOKED = "https://revokedec.entrust.net";
    private static final String CA_FINGERPRINT =
            "E7:93:C9:B0:2F:D8:AA:13:E2:1C:31:22:8A:CC:B0:81:19:64:3B:74:9C:89:89:64:B1:74:6D:46:C3:D4:CB:D2";

    public void runTest(ValidatePathWithURL pathValidator) throws CertificateEncodingException {
        pathValidator.validateDomain(VALID, false, CA_FINGERPRINT);
        pathValidator.validateDomain(REVOKED, true, CA_FINGERPRINT);
    }
}

class Entrust_G4 {
    private static final String VALID = "https://validg4.entrust.net";
    private static final String REVOKED = "https://revokedg4.entrust.net";
    private static final String CA_FINGERPRINT =
            "DB:35:17:D1:F6:73:2A:2D:5A:B9:7C:53:3E:C7:07:79:EE:32:70:A6:2F:B4:AC:42:38:37:24:60:E6:F0:1E:88";

    public void runTest(ValidatePathWithURL pathValidator) throws CertificateEncodingException {
        pathValidator.validateDomain(VALID, false, CA_FINGERPRINT);
        pathValidator.validateDomain(REVOKED, true, CA_FINGERPRINT);
    }
}
