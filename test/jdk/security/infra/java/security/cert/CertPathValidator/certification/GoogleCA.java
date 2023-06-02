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
 * @build jtreg.SkippedException ValidatePathWithURL CAInterop
 * @run main/othervm -Djava.security.debug=certpath,ocsp GoogleCA OCSP
 */

/*
 * @test id=CRL
 * @bug 8307134
 * @summary Interoperability tests with Google's GlobalSign R4 and GTS Root certificates
 * @library /test/lib
 * @build jtreg.SkippedException ValidatePathWithURL CAInterop
 * @run main/othervm -Djava.security.debug=certpath GoogleCA CRL
 */

/*
 * Obtain TLS test artifacts for Google CAs from:
 *
 * https://pki.goog/repository/
 */
public class GoogleCA {

    public static void main(String[] args) throws Exception {

        CAInterop caInterop = new CAInterop(args[0]);

        // CN=GlobalSign, O=GlobalSign, OU=GlobalSign ECC Root CA - R4
        caInterop.validate("globalsigneccrootcar4 [jdk]",
                "https://good.gsr4.demo.pki.goog",
                "https://revoked.gsr4.demo.pki.goog");

        // CN=GTS Root R1, O=Google Trust Services LLC, C=US
        caInterop.validate("gtsrootcar1 [jdk]",
                "https://good.gtsr1.demo.pki.goog",
                "https://revoked.gtsr1.demo.pki.goog");

        // CN=GTS Root R2, O=Google Trust Services LLC, C=US
        caInterop.validate("gtsrootcar2 [jdk]",
                "https://good.gtsr2.demo.pki.goog",
                "https://revoked.gtsr2.demo.pki.goog");

        // CN=GTS Root R3, O=Google Trust Services LLC, C=US
        caInterop.validate("gtsrootecccar3 [jdk]",
                "https://good.gtsr3.demo.pki.goog",
                "https://revoked.gtsr3.demo.pki.goog");

        // CN=GTS Root R4, O=Google Trust Services LLC, C=US
        caInterop.validate("gtsrootecccar4 [jdk]",
                "https://good.gtsr4.demo.pki.goog",
                "https://revoked.gtsr4.demo.pki.goog");
    }
}
