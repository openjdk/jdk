/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Collections;

import jdk.test.lib.Asserts;
import sun.security.tools.keytool.CertAndKeyGen;
import sun.security.x509.X500Name;

/*
 * @test
 * @bug 8185844
 * @summary ensure setEntry overwrite old entry
 * @library /test/lib
 * @requires os.family == "windows"
 * @modules java.base/sun.security.tools.keytool
 *          java.base/sun.security.x509
 */
public class SetDupNameEntry {

    final KeyStore keyStore;
    final CertAndKeyGen ckg;

    static final String PREFIX = "8185844";

    public static void main(String[] args) throws Exception {
        SetDupNameEntry test = new SetDupNameEntry();
        test.cleanup();
        try {
            test.test(true);    // test key entry
            test.test(false);   // test cert entry
        } finally {
            test.cleanup();
        }
    }

    SetDupNameEntry() throws Exception {
        keyStore = KeyStore.getInstance("Windows-MY");
        ckg = new CertAndKeyGen("RSA", "SHA1withRSA");
    }

    void test(boolean testKey) throws Exception {
        keyStore.load(null, null);
        int size = keyStore.size();

        String alias = PREFIX + (testKey ? "k" : "c");
        for (int i = 0; i < 2; i++) {
            ckg.generate(1024);
            X509Certificate cert = ckg
                    .getSelfCertificate(new X500Name("CN=TEST"), 1000);
            if (testKey) {
                keyStore.setKeyEntry(
                        alias,
                        ckg.getPrivateKey(),
                        null,
                        new Certificate[] { cert });
            } else {
                keyStore.setCertificateEntry(alias, cert);
            }
        }
        Asserts.assertEQ(keyStore.size(), size + 1);

        keyStore.load(null, null);
        Asserts.assertEQ(keyStore.size(), size + 1);
    }

    void cleanup() throws Exception {
        keyStore.load(null, null);
        for (String alias : Collections.list(keyStore.aliases())) {
            if (alias.startsWith(PREFIX)) {
                keyStore.deleteEntry(alias);
            }
        }
    }
}
