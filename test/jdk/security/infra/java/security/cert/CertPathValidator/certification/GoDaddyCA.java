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
 * @build jtreg.SkippedException ValidatePathWithURL CAInterop
 * @run main/othervm -Djava.security.debug=certpath GoDaddyCA OCSP
 */

/*
 * @test id=CRL
 * @bug 8196141
 * @summary Interoperability tests with GoDaddy/Starfield CA
 * @library /test/lib
 * @build jtreg.SkippedException ValidatePathWithURL CAInterop
 * @run main/othervm -Djava.security.debug=certpath GoDaddyCA CRL
 */
public class GoDaddyCA {

    public static void main(String[] args) throws Exception {

        CAInterop caInterop = new CAInterop(args[0]);

        // CN=Go Daddy Root Certificate Authority - G2, O="GoDaddy.com, Inc.",
        // L=Scottsdale, ST=Arizona, C=US
        caInterop.validate("godaddyrootg2ca [jdk]",
                "https://valid.gdig2.catest.godaddy.com",
                "https://revoked.gdig2.catest.godaddy.com");

        // CN=Starfield Root Certificate Authority - G2, O="Starfield Technologies, Inc.",
        // L=Scottsdale, ST=Arizona, C=US
        caInterop.validate("starfieldrootg2ca [jdk]",
                "https://valid.sfig2.catest.starfieldtech.com",
                "https://revoked.sfig2.catest.starfieldtech.com");
    }
}
