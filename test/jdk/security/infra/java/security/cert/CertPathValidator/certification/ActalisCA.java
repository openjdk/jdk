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
 * @summary Interoperability tests with Actalis CA
 * @library /test/lib
 * @build jtreg.SkippedException
 * @build ValidatePathWithURL
 * @run main/othervm/timeout=180 -Djava.security.debug=certpath ActalisCA OCSP
 */

/*
 * @test id=CRL
 * @bug 8189131
 * @summary Interoperability tests with Actalis CA
 * @library /test/lib
 * @build jtreg.SkippedException
 * @build ValidatePathWithURL
 * @run main/othervm/timeout=180 -Djava.security.debug=certpath ActalisCA CRL
 */

 /*
 * Obtain test artifacts for Actalis CA from:
 *
 * Test website with *active* TLS Server certificate:
 * https://ssltest-active.actalis.it/
 *
 * Test website with *revoked* TLS Server certificate:
 * https://ssltest-revoked.actalis.it/
 */
public class ActalisCA {

     private static final String VALID = "https://ssltest-active.actalis.it/";
     private static final String REVOKED = "https://ssltest-revoked.actalis.it/";
     private static final String CA_FINGERPRINT =
             "55:92:60:84:EC:96:3A:64:B9:6E:2A:BE:01:CE:0B:A8:6A:64:FB:FE:BC:C7:AA:B5:AF:C1:55:B3:7F:D7:60:66";

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
