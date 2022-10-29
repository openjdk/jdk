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

/*
 * @test
 * @bug 8225181
 * @summary KeyStore should have a getAttributes method
 * @library /test/lib
 * @modules java.base/sun.security.tools.keytool
 *          java.base/sun.security.x509
 */

import jdk.test.lib.Asserts;
import sun.security.tools.keytool.CertAndKeyGen;
import sun.security.x509.X500Name;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;

public class GetAttributes {

    static char[] pass = "changeit".toCharArray();

    public static void main(String[] args) throws Exception {

        // Create a keystore with one private key entry and one cert entry
        CertAndKeyGen cag = new CertAndKeyGen("EC", "SHA256withECDSA");
        KeyStore ks = KeyStore.getInstance("pkcs12");
        ks.load(null, null);
        cag.generate("secp256r1");
        ks.setKeyEntry("a", cag.getPrivateKey(), pass, new Certificate[] {
                cag.getSelfCertificate(new X500Name("CN=a"), 1000)} );
        cag.generate("secp256r1");
        ks.setCertificateEntry("b",
                cag.getSelfCertificate(new X500Name("CN=b"), 1000));

        // Test
        check(ks);

        // Test newly loaded
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ks.store(bos, pass);
        KeyStore ks2 = KeyStore.getInstance("pkcs12");
        ks2.load(new ByteArrayInputStream(bos.toByteArray()), pass);
        check(ks2);
    }

    static void check(KeyStore ks) throws Exception {
        var entry = ks.getEntry("a", new KeyStore.PasswordProtection(pass));
        Asserts.assertEQ(ks.getAttributes("a"), entry.getAttributes());
        entry = ks.getEntry("b", null);
        Asserts.assertEQ(ks.getAttributes("b"), entry.getAttributes());
    }
}
