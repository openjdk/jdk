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
 * @bug 8305975
 * @summary Interoperability tests with TWCA Global Root CA from TAIWAN-CA
 * @library /test/lib
 * @build jtreg.SkippedException
 * @build ValidatePathWithURL
 * @run main/othervm -Djava.security.debug=certpath TWCAGlobalCA OCSP
 */

/*
 * @test id=CRL
 * @bug 8305975
 * @summary Interoperability tests with TWCA Global Root CA from TAIWAN-CA
 * @library /test/lib
 * @build jtreg.SkippedException
 * @build ValidatePathWithURL
 * @run main/othervm -Djava.security.debug=certpath TWCAGlobalCA CRL
 */

/*
 * Obtain TLS test artifacts for TWCA Global Root CA from:
 *
 * Valid TLS Certificates:
 * https://evssldemo6.twca.com.tw
 *
 * Revoked TLS Certificates:
 * https://evssldemo7.twca.com.tw
 */
public class TWCAGlobalCA {

    private static final String VALID = "https://evssldemo6.twca.com.tw";
    private static final String REVOKED = "https://evssldemo7.twca.com.tw";
    private static final String CA_FINGERPRINT =
            "59:76:90:07:F7:68:5D:0F:CD:50:87:2F:9F:95:D5:75:5A:5B:2B:45:7D:81:F3:69:2B:61:0A:98:67:2F:0E:1B";

    public static void main(String[] args) throws Exception {

        ValidatePathWithURL validatePathWithURL = new ValidatePathWithURL();

        if (args.length >= 1 && "CRL".equalsIgnoreCase(args[0])) {
            validatePathWithURL.enableCRLOnly();
        } else {
            // OCSP check by default
            validatePathWithURL.enableOCSPOnly();
        }

        validatePathWithURL.validateDomain(VALID, false, CA_FINGERPRINT);
        validatePathWithURL.validateDomain(REVOKED, true, CA_FINGERPRINT);
    }
}
