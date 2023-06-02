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
 * @summary Interoperability tests with Buypass Class 2 and Class 3 CA
 * @library /test/lib
 * @build jtreg.SkippedException ValidatePathWithURL CAInterop
 * @run main/othervm/timeout=180 -Djava.security.debug=certpath,ocsp BuypassCA OCSP
 */

/*
 * @test id=CRL
 * @bug 8189131
 * @summary Interoperability tests with Buypass Class 2 and Class 3 CA
 * @library /test/lib
 * @build jtreg.SkippedException ValidatePathWithURL CAInterop
 * @run main/othervm/timeout=180 -Djava.security.debug=certpath BuypassCA CRL
 */

public class BuypassCA {

    public static void main(String[] args) throws Exception {

        CAInterop caInterop = new CAInterop(args[0]);

        // CN=Buypass Class 2 Root CA, O=Buypass AS-983163327, C=NO
        caInterop.validate("buypassclass2ca [jdk]",
                "https://valid.business.ca22.ssl.buypass.no",
                "https://revoked.business.ca22.ssl.buypass.no");

        caInterop.validate("buypassclass2ca [jdk]",
                "https://valid.domain.ca22.ssl.buypass.no",
                "https://revoked.domain.ca22.ssl.buypass.no");

        // CN=Buypass Class 3 Root CA, O=Buypass AS-983163327, C=NO
        caInterop.validate("buypassclass3ca [jdk]",
                "https://valid.qcevident.ca23.ssl.buypass.no",
                "https://revoked.qcevident.ca23.ssl.buypass.no");

        caInterop.validate("buypassclass3ca [jdk]",
                "https://valid.evident.ca23.ssl.buypass.no",
                "https://revoked.evident.ca23.ssl.buypass.no");

        caInterop.validate("buypassclass3ca [jdk]",
                "https://valid.businessplus.ca23.ssl.buypass.no",
                "https://revoked.businessplus.ca23.ssl.buypass.no");
    }
}
