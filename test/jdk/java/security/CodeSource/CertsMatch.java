/*
 * Copyright (c) 2005, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6299163
 * @summary Test to compare java.security.CodeSource
 *          Instructions to re-create the used certs file.
 *          - Generate a self-signed certificate with basicConstraints=CA:TRUE
 *          - Copy the generated certificate 2 times into a newly created certs file.
 */
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.net.URL;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.List;

public class CertsMatch {

    public static void main(String[] args) throws Exception {

        File certsFile = new File(System.getProperty("test.src", "."), "certs");
        CertificateFactory cf = CertificateFactory.getInstance("X.509");

        try (FileInputStream fis = new FileInputStream(certsFile);
                BufferedInputStream bis = new BufferedInputStream(fis)) {

            ArrayList certs1 = new ArrayList();
            ArrayList certs2 = new ArrayList();

            // read the first cert
            Certificate cert = cf.generateCertificate(bis);
            certs1.add(cert);
            certs2.add(cert);

            // read the second cert
            cert = cf.generateCertificate(bis);
            certs2.add(cert);

            URL location = certsFile.toURI().toURL();
            CodeSource cs0 = new CodeSource(location, (Certificate[]) null);
            CodeSource cs1 = new CodeSource(location,
                    (Certificate[]) certs1.toArray(new Certificate[certs1.size()]));
            CodeSource cs2 = new CodeSource(location,
                    (Certificate[]) certs2.toArray(new Certificate[certs2.size()]));

            if (!cs0.implies(cs1) || !cs1.implies(cs2)) {
                throw new Exception("The implies method is not working correctly");
            }
            if (cs0.equals(cs1) || cs1.equals(cs0)
                    || cs2.equals(cs1) || cs1.equals(cs2)) {
                throw new Exception("The equals method is not working correctly");
            }
            if (verifySigner(cs0.getCodeSigners(), null)) {
                throw new RuntimeException("CodeSource.getCodeSigners() should be null");
            }
            if (!((verifySigner(cs1.getCodeSigners(), certs1))
                    && (verifySigner(cs2.getCodeSigners(), certs2)))) {
                throw new RuntimeException("Mismatched CodeSigners certificate");
            }
        }
    }

    private static boolean verifySigner(CodeSigner[] css, List certs) {
        if (css == null || certs == null) {
            return false;
        }
        if (css.length < 1 || certs.size() < 1) {
            return false;
        }
        boolean result = true;
        for (CodeSigner cs : css) {
            result &= cs.getSignerCertPath().getCertificates().equals(certs);
        }
        return result;
    }
}
