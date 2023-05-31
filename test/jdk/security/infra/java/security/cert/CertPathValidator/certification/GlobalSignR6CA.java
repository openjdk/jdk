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
    private static final String CA_ALIAS = "globalsignrootcar6 [jdk]";

    public static void main(String[] args) throws Exception {

        if (args.length >= 1 && "CRL".equalsIgnoreCase(args[0])) {
            ValidatePathWithURL.enableCRLOnly();
        } else {
            // OCSP check by default
            ValidatePathWithURL.enableOCSPOnly();
        }

        ValidatePathWithURL validatePathWithURL = new ValidatePathWithURL(CA_ALIAS);

        validatePathWithURL.validateDomain(VALID, false);
        validatePathWithURL.validateDomain(REVOKED, true);
    }
}

