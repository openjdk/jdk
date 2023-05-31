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
 * @bug 8196141
 * @summary Interoperability tests with GoDaddy/Starfield CA
 * @library /test/lib
 * @build jtreg.SkippedException
 * @build ValidatePathWithURL
 * @run main/othervm -Djava.security.debug=certpath GoDaddyCA OCSP
 */

/*
 * @test id=CRL
 * @bug 8196141
 * @summary Interoperability tests with GoDaddy/Starfield CA
 * @library /test/lib
 * @build jtreg.SkippedException
 * @build ValidatePathWithURL
 * @run main/othervm -Djava.security.debug=certpath GoDaddyCA CRL
 */

import java.security.cert.CertificateEncodingException;

/*
 * Obtain test artifacts for GoDaddy/Starfield CAs from:
 *
 * Go Daddy Root Certificate Authority - G2:
 *    valid:
 *    expired: https://expired.gdig2.catest.godaddy.com/
 *    revoked:
 *
 * Starfield Root Certificate Authority - G2:
 *    valid:
 *    expired: https://expired.sfig2.catest.starfieldtech.com/
 *    revoked:
 */
public class GoDaddyCA {

    public static void main(String[] args) throws Exception {

        ValidatePathWithURL pathValidator = new ValidatePathWithURL();

        if (args.length >= 1 && "CRL".equalsIgnoreCase(args[0])) {
            pathValidator.enableCRLOnly();
        } else {
            // OCSP check by default
            pathValidator.enableOCSPOnly();
        }

        new GoDaddyGdig2().runTest(pathValidator);
        new GoDaddySfig2().runTest(pathValidator);
    }
}

class GoDaddyGdig2 {
    private static final String VALID = "https://valid.gdig2.catest.godaddy.com/";
    private static final String REVOKED = "https://revoked.gdig2.catest.godaddy.com/";
    private static final String CA_FINGERPRINT =
            "45:14:0B:32:47:EB:9C:C8:C5:B4:F0:D7:B5:30:91:F7:32:92:08:9E:6E:5A:63:E2:74:9D:D3:AC:A9:19:8E:DA";

    public void runTest(ValidatePathWithURL pathValidator) throws CertificateEncodingException {
        pathValidator.validateDomain(VALID, false, CA_FINGERPRINT);
        pathValidator.validateDomain(REVOKED, true, CA_FINGERPRINT);
    }
}

class GoDaddySfig2 {
    private static final String VALID = "https://valid.sfig2.catest.starfieldtech.com/";
    private static final String REVOKED = "https://revoked.sfig2.catest.starfieldtech.com/";
    private static final String CA_FINGERPRINT =
            "2C:E1:CB:0B:F9:D2:F9:E1:02:99:3F:BE:21:51:52:C3:B2:DD:0C:AB:DE:1C:68:E5:31:9B:83:91:54:DB:B7:F5";

    public void runTest(ValidatePathWithURL pathValidator) throws CertificateEncodingException {
        pathValidator.validateDomain(VALID, false, CA_FINGERPRINT);
        pathValidator.validateDomain(REVOKED, true, CA_FINGERPRINT);
    }
}

