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
 * @bug 8216577 8249176
 * @summary Interoperability tests with GlobalSign R6 CA
 * @library /test/lib
 * @build jtreg.SkippedException
 * @build ValidatePathWithURL
 * @run main/othervm -Djava.security.debug=certpath GlobalSignR6CA OCSP
 */

/*
 * @test id=CRL
 * @bug 8216577 8249176
 * @summary Interoperability tests with GlobalSign R6 CA
 * @library /test/lib
 * @build jtreg.SkippedException
 * @build ValidatePathWithURL
 * @run main/othervm -Djava.security.debug=certpath GlobalSignR6CA CRL
 */
public class GlobalSignR6CA {

     private static final String VALID = "https://valid.r6.roots.globalsign.com/";
     private static final String REVOKED = "https://revoked.r6.roots.globalsign.com/";
     private static final String CA_FINGERPRINT =
             "2C:AB:EA:FE:37:D0:6C:A2:2A:BA:73:91:C0:03:3D:25:98:29:52:C4:53:64:73:49:76:3A:3A:B5:AD:6C:CF:69";

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

