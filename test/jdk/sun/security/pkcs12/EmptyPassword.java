/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8202299 8231107
 * @modules java.base/sun.security.tools.keytool
 *          java.base/sun.security.x509
 * @library /test/lib
 * @summary Testing empty (any of null, "", "\0") password behaviors
 */

import jdk.test.lib.Asserts;
import sun.security.tools.keytool.CertAndKeyGen;
import sun.security.x509.X500Name;

import java.io.File;
import java.io.FileOutputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.Arrays;

public class EmptyPassword {

    public static void main(String[] args) throws Exception {

        // KeyStore is protected with password "\0".
        CertAndKeyGen gen = new CertAndKeyGen("RSA", "SHA256withRSA");
        gen.generate(2048);
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ks.setKeyEntry("a", gen.getPrivateKey(), new char[1],
                new Certificate[] {
                        gen.getSelfCertificate(new X500Name("CN=Me"), 100)
                });

        // 8202299: interop between new char[0] and new char[1]
        store(ks, "p12", new char[1]);

        // It can be loaded with password "".
        ks = KeyStore.getInstance(new File("p12"), new char[0]);
        Asserts.assertTrue(ks.getKey("a", new char[0]) != null);
        Asserts.assertTrue(ks.getCertificate("a") != null);

        ks = KeyStore.getInstance(new File("p12"), new char[1]);
        Asserts.assertTrue(ks.getKey("a", new char[1]) != null);
        Asserts.assertTrue(ks.getCertificate("a") != null);

        // 8231107: Store with null password makes it password-less
        store(ks, "p00", null);

        // Can read cert and key with any password
        for (char[] pass: new char[][] {
                new char[0],    // password actually used before 8202299
                new char[1],    // the interoperability before 8202299
                null,           // password-less after 8202299
                "whatever".toCharArray()
        }) {
            System.out.println("with password " + Arrays.toString(pass));
            ks = KeyStore.getInstance(new File("p00"), pass);
            Asserts.assertTrue(ks.getKey("a", new char[1]) != null);
            Asserts.assertTrue(ks.getCertificate("a") != null);
        }
    }

    static void store(KeyStore ks, String file, char[] pass) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            ks.store(fos, pass);
        }
    }
}
