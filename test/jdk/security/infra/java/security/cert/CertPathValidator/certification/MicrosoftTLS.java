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

public class MicrosoftTLS {

    public static void main(String[] args) throws Exception {

        CAInterop caInterop = new CAInterop(args[0]);

        // CN=Microsoft ECC Root Certificate Authority 2017, O=Microsoft Corporation, C=US
        caInterop.validate("microsoftecc2017 [jdk]",
                "https://acteccroot2017.pki.microsoft.com",
                "https://rvkeccroot2017.pki.microsoft.com");

        // CN=Microsoft RSA Root Certificate Authority 2017, O=Microsoft Corporation, C=US
        caInterop.validate("microsoftrsa2017 [jdk]",
                "https://actrsaroot2017.pki.microsoft.com",
                "https://rvkrsaroot2017.pki.microsoft.com");
    }
}
