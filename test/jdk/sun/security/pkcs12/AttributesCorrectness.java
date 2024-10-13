/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8309667
 * @library /test/lib
 * @modules java.base/sun.security.tools.keytool
 *          java.base/sun.security.x509
 * @summary ensures attributes reading is correct
 */
import jdk.test.lib.Asserts;
import sun.security.tools.keytool.CertAndKeyGen;
import sun.security.x509.X500Name;

import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.KeyStore;
import java.security.PKCS12Attribute;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Set;

public class AttributesCorrectness {

    static final char[] PASSWORD = "changeit".toCharArray();
    static final String LOCAL_KEY_ID = "1.2.840.113549.1.9.21";
    static final String TRUSTED_KEY_USAGE = "2.16.840.1.113894.746875.1.1";
    static final String FRIENDLY_NAME = "1.2.840.113549.1.9.20";

    static CertAndKeyGen cag;
    static KeyStore ks;

    public static void main(String[] args) throws Exception {

        ks = KeyStore.getInstance("pkcs12");
        ks.load(null, null);
        cag = new CertAndKeyGen("Ed25519", "Ed25519");

        cag.generate(-1);
        ks.setCertificateEntry("c", ss("c"));

        cag.generate(-1);
        ks.setKeyEntry("d", cag.getPrivateKey(), PASSWORD, chain("d"));

        cag.generate(-1);
        ks.setKeyEntry("e", new EncryptedPrivateKeyInfo(
                "PBEWithMD5AndDES", new byte[100]).getEncoded(), chain("e"));

        var f = new KeyStore.SecretKeyEntry(new SecretKeySpec(new byte[16], "AES"),
                Set.of(new PKCS12Attribute("1.2.3", "456")));
        ks.setEntry("f", f, new KeyStore.PasswordProtection(PASSWORD));

        cag.generate(-1);
        var g = new KeyStore.TrustedCertificateEntry(ss("g"),
                Set.of(new PKCS12Attribute("1.2.4", "456")));
        ks.setEntry("g", g, null);

        cag.generate(-1);
        var h = new KeyStore.PrivateKeyEntry(cag.getPrivateKey(), chain("h"),
                Set.of(new PKCS12Attribute("1.2.5", "456")));
        ks.setEntry("h", h, new KeyStore.PasswordProtection(PASSWORD));

        var i = new KeyStore.SecretKeyEntry(new SecretKeySpec(new byte[16], "AES"));
        ks.setEntry("i", i, new KeyStore.PasswordProtection(PASSWORD));

        cag.generate(-1);
        var j = new KeyStore.TrustedCertificateEntry(ss("g"));
        ks.setEntry("j", j, null);

        cag.generate(-1);
        var k = new KeyStore.PrivateKeyEntry(cag.getPrivateKey(), chain("h"));
        ks.setEntry("k", k, new KeyStore.PasswordProtection(PASSWORD));
        check();

        var bout = new ByteArrayOutputStream();
        ks.store(bout, PASSWORD);
        ks.load(new ByteArrayInputStream(bout.toByteArray()), PASSWORD);
        check();
    }

    static X509Certificate ss(String alias) throws Exception {
        return cag.getSelfCertificate(new X500Name("CN=" + alias), 100);
    }

    static Certificate[] chain(String alias) throws Exception {
        return new Certificate[] { ss(alias) };
    }

    static Void check() {
        checkAttributes("c", TRUSTED_KEY_USAGE);
        checkAttributes("d", LOCAL_KEY_ID);
        checkAttributes("e", LOCAL_KEY_ID);
        checkAttributes("f", LOCAL_KEY_ID, "1.2.3");
        checkAttributes("g", TRUSTED_KEY_USAGE, "1.2.4");
        checkAttributes("h", LOCAL_KEY_ID, "1.2.5");
        checkAttributes("i", LOCAL_KEY_ID);
        checkAttributes("j", TRUSTED_KEY_USAGE);
        checkAttributes("k", LOCAL_KEY_ID);
        return null;
    }

    static void checkAttributes(String alias, String... keys) {
        try {
            var attrs = keys[0].equals(LOCAL_KEY_ID)
                    ? ks.getAttributes(alias)
                    : ks.getEntry(alias, null).getAttributes();
            Asserts.assertEQ(attrs.size(), keys.length + 1);
            Asserts.assertTrue(
                    attrs.contains(new PKCS12Attribute(FRIENDLY_NAME, alias)));
            for (var attr : attrs) {
                var name = attr.getName();
                if (name.equals(FRIENDLY_NAME)) continue;
                var found = false;
                for (var key : keys) {
                    if (key.equals(name)) {
                        found = true;
                        break;
                    }
                }
                Asserts.assertTrue(found, name);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
