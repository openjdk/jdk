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

/*
 * @test
 * @bug 8286428
 * @library /test/lib
 * @modules java.base/sun.security.util
 *          java.base/sun.security.x509
 * @summary AlgorithmId should understand PBES2
 */
import jdk.test.lib.Asserts;
import jdk.test.lib.security.DerUtils;
import sun.security.x509.AlgorithmId;

import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.Map;
import java.util.Random;

public class PBES2 {
    public static void main(String[] args) throws Exception {

        var pass = "changeit".toCharArray();

        var ks = KeyStore.getInstance("pkcs12");
        ks.load(null, null);

        var bytes = new byte[16];
        new Random().nextBytes(bytes);
        var key = new SecretKeySpec(bytes, "AES");

        var algos = Map.of(
                "p1", "PBEWithMD5AndDES",
                "p2", "PBEWithHmacSHA384AndAES_128",
                "p3", "PBEWithHmacSHA256AndAES_256");

        // Write 3 SecretKeyEntry objects inside the keystore
        // PBES1
        ks.setEntry("p1", new KeyStore.SecretKeyEntry(key), new KeyStore.PasswordProtection(pass, algos.get("p1"), null));
        // PBES2
        ks.setEntry("p2", new KeyStore.SecretKeyEntry(key), new KeyStore.PasswordProtection(pass, algos.get("p2"), null));
        // default
        ks.setKeyEntry("p3", key, pass, null);

        var bout = new ByteArrayOutputStream();
        ks.store(bout, pass);
        var p12 = bout.toByteArray();

        var decryptKey = SecretKeyFactory.getInstance("PBE").generateSecret(new PBEKeySpec(pass));
        ks.load(new ByteArrayInputStream(p12), pass);
        for (int i = 0; i < 3; i++) {
            var name = DerUtils.innerDerValue(p12, "110c010c" + i + "2010").getAsString();

            // AlgorithmId
            var aid = AlgorithmId.parse(DerUtils.innerDerValue(p12, "110c010c" + i + "1010c0"));
            Asserts.assertEQ(aid.getName(), algos.get(name), name);

            // EncryptedPrivateKeyInfo
            var encrypted = DerUtils.innerDerValue(p12, "110c010c" + i + "1010c");
            var epi = new EncryptedPrivateKeyInfo(encrypted.toByteArray());
            Asserts.assertEQ(epi.getAlgName(), algos.get(name));
            var spec = epi.getKeySpec(decryptKey);
            var specEncoded = spec.getEncoded();
            Asserts.assertEQ(spec.getAlgorithm(), "AES", name);
            Asserts.assertTrue(Arrays.equals(bytes, 0, 16, specEncoded, specEncoded.length - 16, specEncoded.length), name);

            // KeyStore API
            var k = ks.getKey(name, pass);
            Asserts.assertEQ(k.getAlgorithm(), "AES", name);
            Asserts.assertTrue(Arrays.equals(bytes, k.getEncoded()), name);
        }
    }
}
