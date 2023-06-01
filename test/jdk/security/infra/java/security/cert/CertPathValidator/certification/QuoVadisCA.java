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
 * @summary Interoperability tests with QuoVadis Root CA1, CA2, and CA3 G3 CAs using OCSP
 * @library /test/lib
 * @build jtreg.SkippedException ValidatePathWithURL CAInterop
 * @run main/othervm -Djava.security.debug=certpath QuoVadisCA OCSP
 */

/*
 * @test id=CRL
 * @bug 8189131
 * @summary Interoperability tests with QuoVadis Root CA1, CA2, and CA3 G3 CAs using CRL
 * @library /test/lib
 * @build jtreg.SkippedException ValidatePathWithURL CAInterop
 * @run main/othervm -Djava.security.debug=certpath QuoVadisCA CRL
 */

/*
 * Obtain TLS test artifacts for QuoVadis CAs from:
 *
 * https://www.quovadisglobal.com/download-roots-crl/
 *
 */
public class QuoVadisCA {
    public static void main(String[] args) throws Exception {

        CAInterop caInterop = new CAInterop(args[0]);

        // CN=QuoVadis Root CA 1 G3, O=QuoVadis Limited, C=BM
        caInterop.validate("quovadisrootca1g3 [jdk]",
                "https://quovadis-root-ca-1-g3.chain-demos.digicert.com",
                "https://quovadis-root-ca-1-g3-revoked.chain-demos.digicert.com");

        // CN=QuoVadis Root CA 2 G3, O=QuoVadis Limited, C=BM
        caInterop.validate("quovadisrootca2g3 [jdk]",
                "https://quovadis-root-ca-2-g3.chain-demos.digicert.com",
                "https://quovadis-root-ca-2-g3-revoked.chain-demos.digicert.com");

        // CN=QuoVadis Root CA 3 G3, O=QuoVadis Limited, C=BM
        caInterop.validate("quovadisrootca3g3 [jdk]",
                "https://quovadis-root-ca-3-g3.chain-demos.digicert.com",
                "https://quovadis-root-ca-3-g3-revoked.chain-demos.digicert.com");
    }
}
