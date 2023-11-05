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

import jdk.test.lib.Asserts;
import jdk.test.lib.SecurityTools;

import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import java.security.KeyStore;

/**
 * @test
 * @bug 8251134
 * @requires os.family == "windows"
 * @summary Cipher operations on CNG keys
 * @library /test/lib
 */

public class CngCipher {

    final static String PREFIX = "8251134";

    public static void main(String[] args) throws Exception {
        cleanup();
        prepare();
        try {
            test(PREFIX + "m");
            test(PREFIX + "c");
        } finally {
            cleanup();
        }
    }

    static void prepare() throws Exception {
        // This will generate a MSCAPI key
        SecurityTools.keytool("-storetype Windows-MY -genkeypair -alias "
                + PREFIX + "m -keyalg RSA -dname CN=" + PREFIX + "m");
        // This will generate a CNG key
        ProcessBuilder pb = new ProcessBuilder("powershell", "-Command",
                "New-SelfSignedCertificate",  "-DnsName", PREFIX + "c",
                // -KeyAlgorithm not supported on Windows Server 2012
                //"-KeyAlgorithm", "RSA",
                "-CertStoreLocation", "Cert:\\CurrentUser\\My");
        pb.inheritIO();
        pb.start().waitFor();
    }

    static void cleanup() throws Exception {
        KeyStore ks = KeyStore.getInstance("Windows-MY");
        ks.load(null, null);
        ks.deleteEntry(PREFIX +"c");
        ks.deleteEntry(PREFIX +"m");
        ks.store(null, null);
    }

    static void test(String alias) throws Exception {
        KeyStore ks = KeyStore.getInstance("Windows-MY");
        ks.load(null, null);
        var alg = "RSA/ECB/PKCS1Padding";

        var k1 = ks.getKey(alias, "changeit".toCharArray());
        var k2 = ks.getCertificate(alias).getPublicKey();

        Cipher c;

        var k = KeyGenerator.getInstance("AES").generateKey();
        c = Cipher.getInstance(alg, "SunMSCAPI");
        c.init(Cipher.WRAP_MODE, k2);
        var enc = c.wrap(k);
        c = Cipher.getInstance(alg, "SunMSCAPI");
        c.init(Cipher.UNWRAP_MODE, k1);
        var dec = c.unwrap(enc, "AES", Cipher.SECRET_KEY);
        Asserts.assertTrue(Arrays.equals(k.getEncoded(), dec.getEncoded()));

        c = Cipher.getInstance(alg, "SunMSCAPI");
        c.init(Cipher.ENCRYPT_MODE, k2);
        byte[] msg = "hello you fool".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        c.update(msg);
        var enc2 = c.doFinal();
        c = Cipher.getInstance(alg, "SunMSCAPI");
        c.init(Cipher.DECRYPT_MODE, k1);
        c.update(enc2);
        var dec2 = c.doFinal();
        Asserts.assertTrue(Arrays.equals(msg, dec2));
    }
}
