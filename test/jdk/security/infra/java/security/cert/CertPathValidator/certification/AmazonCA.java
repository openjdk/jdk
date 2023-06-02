/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8233223
 * @summary Interoperability tests with Amazon's CA1, CA2, CA3, and CA4
 * @library /test/lib
 * @build jtreg.SkippedException ValidatePathWithURL CAInterop
 * @run main/othervm -Djava.security.debug=certpath,ocsp AmazonCA OCSP
 */

/*
 * @test id=CRL
 * @bug 8233223
 * @summary Interoperability tests with Amazon's CA1, CA2, CA3, and CA4
 * @library /test/lib
 * @build jtreg.SkippedException ValidatePathWithURL CAInterop
 * @run main/othervm -Djava.security.debug=certpath AmazonCA CRL
 */

/*
 * Obtain TLS test artifacts for Amazon CAs from:
 *
 * https://www.amazontrust.com/repository/
 */
public class AmazonCA {
    public static void main(String[] args) throws Exception {

        CAInterop caInterop = new CAInterop(args[0]);

        // CN=Amazon Root CA 1, O=Amazon, C=US
        caInterop.validate("amazonrootca1 [jdk]",
                "https://valid.rootca1.demo.amazontrust.com",
                "https://revoked.rootca1.demo.amazontrust.com");

        // CN=Amazon Root CA 2, O=Amazon, C=US
        caInterop.validate("amazonrootca2 [jdk]",
                "https://valid.rootca2.demo.amazontrust.com",
                "https://revoked.rootca2.demo.amazontrust.com");

        // CN=Amazon Root CA 3, O=Amazon, C=US
        caInterop.validate("amazonrootca3 [jdk]",
                "https://valid.rootca3.demo.amazontrust.com",
                "https://revoked.rootca3.demo.amazontrust.com");

        // CN=Amazon Root CA 4, O=Amazon, C=US
        caInterop.validate("amazonrootca4 [jdk]",
                "https://valid.rootca4.demo.amazontrust.com",
                "https://revoked.rootca4.demo.amazontrust.com");
    }
}
