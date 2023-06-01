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

        CAInterop caInterop = new CAInterop(args[0]);

        // CN=COMODO RSA Certification Authority, O=COMODO CA Limited, L=Salford, ST=Greater Manchester, C=GB
        caInterop.validate("comodorsaca [jdk]",
                "https://comodorsacertificationauthority-ev.comodoca.com",
                "https://comodorsacertificationauthority-ev.comodoca.com:444");

        // CN=COMODO ECC Certification Authority, O=COMODO CA Limited, L=Salford, ST=Greater Manchester, C=GB
        caInterop.validate("comodoeccca [jdk]",
                "https://comodoecccertificationauthority-ev.comodoca.com",
                "https://comodoecccertificationauthority-ev.comodoca.com:444");

        // CN=USERTrust RSA Certification Authority, O=The USERTRUST Network, L=Jersey City, ST=New Jersey, C=US
        caInterop.validate("usertrustrsaca [jdk]",
                "https://usertrustrsacertificationauthority-ev.comodoca.com",
                "https://usertrustrsacertificationauthority-ev.comodoca.com:444");

        // CN=USERTrust ECC Certification Authority, O=The USERTRUST Network, L=Jersey City, ST=New Jersey, C=US
        caInterop.validate("usertrusteccca [jdk]",
                "https://usertrustecccertificationauthority-ev.comodoca.com",
                "https://usertrustecccertificationauthority-ev.comodoca.com:444");
    }
}
