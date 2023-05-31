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
 * @test
 * @bug 8189131
 * @summary Interoperability tests with Let's Encrypt CA using OCSP and CRL
 * @library /test/lib
 * @build jtreg.SkippedException
 * @build ValidatePathWithURL
 * @run main/othervm -Djava.security.debug=certpath LetsEncryptCA DEFAULT
 */
public class LetsEncryptCA {

     private static final String VALID = "https://valid-isrgrootx1.letsencrypt.org/";
     private static final String REVOKED = "https://revoked-isrgrootx1.letsencrypt.org/";
    private static final String CA_ALIAS = "letsencryptisrgx1 [jdk]";

     public static void main(String[] args) throws Exception {

         if (args.length >= 1 && "CRL".equalsIgnoreCase(args[0])) {
             ValidatePathWithURL.enableCRLOnly();
         } if (args.length >= 1 && "OCSP".equalsIgnoreCase(args[0])){
             ValidatePathWithURL.enableOCSPOnly();
         } else {
             // EE certs don't have CRLs, intermediate cert doesn't specify OCSP responder
             ValidatePathWithURL.enableOCSPAndCRL();
         }

         ValidatePathWithURL validatePathWithURL = new ValidatePathWithURL(CA_ALIAS);

         validatePathWithURL.validateDomain(VALID, false);
         validatePathWithURL.validateDomain(REVOKED, true);
     }
}
