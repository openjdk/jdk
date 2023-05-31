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
 * @bug 8304760
 * @summary Interoperability tests with Microsoft TLS root CAs
 * @library /test/lib
 * @build jtreg.SkippedException
 * @build ValidatePathWithURL
 * @run main/othervm -Djava.security.debug=certpath MicrosoftTLS OCSP
 */

/*
 * @test id=CRL
 * @bug 8304760
 * @summary Interoperability tests with Microsoft TLS root CAs
 * @library /test/lib
 * @build jtreg.SkippedException
 * @build ValidatePathWithURL
 * @run main/othervm -Djava.security.debug=certpath MicrosoftTLS CRL
 */

import java.security.cert.CertificateEncodingException;
public class MicrosoftTLS {

    public static void main(String[] args) throws Exception {

        ValidatePathWithURL pathValidator = new ValidatePathWithURL();

        if (args.length >= 1 && "CRL".equalsIgnoreCase(args[0])) {
            pathValidator.enableCRLOnly();
        } else {
            // OCSP check by default
            pathValidator.enableOCSPOnly();
        }

        new MicrosoftECCTLS().runTest(pathValidator);
        new MicrosoftRSATLS().runTest(pathValidator);
    }
}

class MicrosoftECCTLS {
    private static final String VALID = "https://acteccroot2017.pki.microsoft.com/";
    private static final String REVOKED = "https://rvkeccroot2017.pki.microsoft.com/";
    private static final String CA_FINGERPRINT =
            "35:8D:F3:9D:76:4A:F9:E1:B7:66:E9:C9:72:DF:35:2E:E1:5C:FA:C2:27:AF:6A:D1:D7:0E:8E:4A:6E:DC:BA:02";

    public void runTest(ValidatePathWithURL pathValidator) throws CertificateEncodingException {
        pathValidator.validateDomain(VALID, false, CA_FINGERPRINT);
        pathValidator.validateDomain(REVOKED, true, CA_FINGERPRINT);
    }
}

class MicrosoftRSATLS {
    private static final String VALID = "https://actrsaroot2017.pki.microsoft.com/";
    private static final String REVOKED = "https://rvkrsaroot2017.pki.microsoft.com/";
    private static final String CA_FINGERPRINT =
            "C7:41:F7:0F:4B:2A:8D:88:BF:2E:71:C1:41:22:EF:53:EF:10:EB:A0:CF:A5:E6:4C:FA:20:F4:18:85:30:73:E0";

    public void runTest(ValidatePathWithURL pathValidator) throws CertificateEncodingException {
        pathValidator.validateDomain(VALID, false, CA_FINGERPRINT);
        pathValidator.validateDomain(REVOKED, true, CA_FINGERPRINT);
    }
}
