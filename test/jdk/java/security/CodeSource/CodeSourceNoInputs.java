/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.PublicKey;
import java.security.cert.Certificate;

/**
 * @test
 * @bug 8366522
 * @summary Verify that getCertificates() and getCodeSigners() return correct
 *          results when CodeSource is created with empty or null Certificate[]
 *          or CodeSigner[] arguments, or there are no X509 certificates in
 *          certs. Make sure that NPE is not thrown from
 *          CodeSource.getCodeSigners()
 */
public class CodeSourceNoInputs {
    private static final Certificate NON_X509_CERT = new Certificate("") {
        @Override
        public byte[] getEncoded() {
            return new byte[0];
        }

        @Override
        public void verify(PublicKey key) {
        }

        @Override
        public void verify(PublicKey key, String sigProvider) {
        }

        @Override
        public String toString() {
            return "";
        }

        @Override
        public PublicKey getPublicKey() {
            return null;
        }
    };

    public static void main(String[] args) throws Exception {
        CodeSource cs;

        cs = new CodeSource(null, (Certificate[]) null);
        if (cs.getCodeSigners() != null || cs.getCertificates() != null) {

            throw new SecurityException("Both CodeSource.getCodeSigners() " +
                "and CodeSource.getCertificates() should return null for " +
                "new CodeSource(null, (Certificate[]) null)");
        }

        cs = new CodeSource(null, (CodeSigner[]) null);
        if (cs.getCodeSigners() != null || cs.getCertificates() != null) {

            throw new SecurityException("Both CodeSource.getCodeSigners() " +
                "and CodeSource.getCertificates() should return null for " +
                "new CodeSource(null, (CodeSigners[]) null)");
        }

        cs = new CodeSource(null, new Certificate[0]);
        if (cs.getCodeSigners().length != 0
            || cs.getCertificates().length != 0) {

            throw new SecurityException("Both CodeSource.getCodeSigners()" +
                "and CodeSource.getCertificates() should return empty arrays " +
                "for new CodeSource(null, new Certificate[0])");

        }

        cs = new CodeSource(null, new CodeSigner[0]);
        if (cs.getCodeSigners().length != 0
            || cs.getCertificates().length != 0) {

            throw new SecurityException("Both CodeSource.getCodeSigners() and" +
                " CodeSource.getCertificates() should return empty arrays for" +
                " new CodeSource(null, new CodeSigners[0])");
        }

        cs = new CodeSource(null, new Certificate[]{NON_X509_CERT});
        if (cs.getCodeSigners().length != 0 || cs.getCertificates() == null) {

            throw new SecurityException("Both CodeSource.getCodeSigners() and" +
                " CodeSource.getCertificates() should return arrays for new" +
                " CodeSource(null, new CodeSigners[1]{NON-X509-CERTIFICATE})");
        }
    }
}
