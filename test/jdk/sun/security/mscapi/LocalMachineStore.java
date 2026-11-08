/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8328184
 * @summary Certificate entries must be stored in the requested Windows store
 * @requires os.family == "windows"
 * @library /test/lib
 * @modules java.base/sun.security.tools.keytool
 *          java.base/sun.security.x509
 * @comment Run with administrative privileges because this test modifies
 *          the LOCALMACHINE store.
 * @run main/manual ${test.main.class}
 */

import sun.security.tools.keytool.CertAndKeyGen;
import sun.security.x509.X500Name;

import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.UUID;

import static jdk.test.lib.Asserts.assertEquals;
import static jdk.test.lib.Asserts.assertFalse;

public class LocalMachineStore {

    public static void main(String[] args) throws Exception {
        String alias = "jdk-8328184-" + UUID.randomUUID();

        KeyStore localMachine = load("LOCALMACHINE");
        KeyStore currentUser;
        try {
            X509Certificate cert = generateCertificate();
            localMachine.setCertificateEntry(alias, cert);

            // Reload the stores to ensure the certificate is actually persisted
            localMachine = load("LOCALMACHINE");
            currentUser = load("CURRENTUSER");

            assertEquals(cert, localMachine.getCertificate(alias), "Certificate should be in LOCALMACHINE store");
            assertFalse(currentUser.containsAlias(alias), "Certificate should not be in CURRENTUSER store");

            // Add the same certificate to CURRENTUSER to verify that deleting it
            // from LOCALMACHINE leaves the CURRENTUSER copy intact.
            currentUser.setCertificateEntry(alias, cert);

            localMachine.deleteEntry(alias);

            localMachine = load("LOCALMACHINE");
            currentUser = load("CURRENTUSER");
            assertFalse(localMachine.containsAlias(alias), "Certificate should be removed from LOCALMACHINE store");
            assertEquals(cert, currentUser.getCertificate(alias), "Certificate should still be in CURRENTUSER store");
        } finally {
            // Clean up the certificate from both stores to avoid leaving test artifacts
            try {
                load("CURRENTUSER").deleteEntry(alias);
            } finally {
                load("LOCALMACHINE").deleteEntry(alias);
            }
        }
    }

    private static KeyStore load(String type) throws Exception {
        KeyStore ks = KeyStore.getInstance("Windows-MY-" + type);
        ks.load(null, null);
        return ks;
    }

    private static X509Certificate generateCertificate() throws Exception {
        CertAndKeyGen keyGen = new CertAndKeyGen("RSA", "SHA256WithRSA", null);
        keyGen.generate(2048);
        return keyGen.getSelfCertificate(
                new X500Name("CN=JDK-8328184-" + UUID.randomUUID()),
                24 * 60 * 60
        );
    }
}
