/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import sun.security.tools.keytool.CertAndKeyGen;
import sun.security.x509.X500Name;

import java.security.KeyStore;
import java.security.cert.Certificate;
import jdk.test.lib.Asserts;

/**
 * @test
 * @bug 6522064
 * @library /test/lib
 * @requires os.family == "windows"
 * @modules java.base/sun.security.tools.keytool
 *          java.base/sun.security.x509
 * @summary Aliases from Microsoft CryptoAPI has bad character encoding
 */

public class NonAsciiAlias {
    public static void main(String[] args) throws Exception {
        KeyStore ks = KeyStore.getInstance("Windows-MY");
        String alias = "\u58c6\u94a56522064";
        try {
            ks.load(null, null);
            CertAndKeyGen cag = new CertAndKeyGen("RSA", "SHA256withRSA");
            cag.generate(2048);
            ks.setKeyEntry(alias, cag.getPrivateKey(), null, new Certificate[]{
                    cag.getSelfCertificate(new X500Name("CN=Me"), 1000)
            });
            // Confirms the alias is there
            Asserts.assertTrue(ks.containsAlias(alias));
            ks.store(null, null);
            ks.load(null, null);
            // Confirms the alias is there after reload
            Asserts.assertTrue(ks.containsAlias(alias));
            ks.deleteEntry(alias);
            // Confirms the alias is removed
            Asserts.assertFalse(ks.containsAlias(alias));
            ks.store(null, null);
            ks.load(null, null);
            // Confirms the alias is removed after reload
            Asserts.assertFalse(ks.containsAlias(alias));
        } finally {
            ks.deleteEntry(alias);
            // in case the correct alias is not found, clean up a wrong one
            ks.deleteEntry("??6522064");
        }
    }
}
