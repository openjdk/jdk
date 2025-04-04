/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8325448
 * @summary Verify that HPKE works across security providers
 * @library /test/lib ..
 * @modules jdk.crypto.cryptoki
 * @run main/othervm DHKEM p11-nss.txt
 * @run main/othervm DHKEM p11-nss-sensitive.txt
 * @enablePreview
 */

import jdk.test.lib.Asserts;

import javax.crypto.Cipher;
import javax.crypto.KDF;
import javax.crypto.KEM;
import javax.crypto.spec.HKDFParameterSpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.*;
import java.security.spec.ECGenParameterSpec;

public class DHKEM extends PKCS11Test {

    public static void main(String[] args) throws Exception {
        main(new DHKEM(args[0]), args);
    }

    public DHKEM(String conf) throws IOException {
        copyNssCertKeyToClassesDir();
        setCommonSystemProps();
        System.setProperty("CUSTOM_P11_CONFIG",
                Path.of(System.getProperty("test.src", "."), "../nss/" + conf).toString());
        System.setProperty("TOKEN", "nss");
        System.setProperty("TEST", "basic");
    }

    @Override
    public void main(Provider p) throws Exception {
        var ec = Security.getProvider("SunEC");
        test(p, p);
        test(p, ec);
        test(ec, p);
    }

    static void test(Provider p1, Provider p2) throws Exception {
        var g = KeyPairGenerator.getInstance("EC", p2);
        g.initialize(new ECGenParameterSpec("secp521r1"));
        var kp = g.generateKeyPair();
        var msg = "hello".getBytes(StandardCharsets.UTF_8);

        prefer(p1);
        var kem1 = KEM.getInstance("DHKEM");
        var e = kem1.newEncapsulator(kp.getPublic());
        var enc = e.encapsulate();
        var kdf1 = KDF.getInstance("HKDF-SHA256");
        var k1 = kdf1.deriveKey("AES", HKDFParameterSpec.ofExtract().addIKM(enc.key()).thenExpand(null, 32));
        var c1 = Cipher.getInstance("AES");
        c1.init(Cipher.ENCRYPT_MODE, k1);
        var ct = c1.doFinal(msg);

        Asserts.assertTrue(e.secretSize() >= 42);
        var enc2 = e.encapsulate(5, 37, "AES");
        c1.init(Cipher.ENCRYPT_MODE, enc2.key());
        var ct2 = c1.doFinal(msg);

        prefer(p2);
        var kem2 = KEM.getInstance("DHKEM");
        var d = kem2.newDecapsulator(kp.getPrivate());
        var k = d.decapsulate(enc.encapsulation());
        var kdf2 = KDF.getInstance("HKDF-SHA256");
        var k2 = kdf2.deriveKey("AES", HKDFParameterSpec.ofExtract().addIKM(k).thenExpand(null, 32));
        var c2 = Cipher.getInstance("AES");
        c2.init(Cipher.DECRYPT_MODE, k2);
        var pt = c2.doFinal(ct);

        Asserts.assertEqualsByteArray(msg, pt);

        var k3 = d.decapsulate(enc2.encapsulation(), 5, 37, "AES");
        c2.init(Cipher.DECRYPT_MODE, k3);
        var pt2 = c2.doFinal(ct2);

        Asserts.assertEqualsByteArray(msg, pt2);
    }

    static void prefer(Provider p) {
        Security.removeProvider(p.getName());
        Security.insertProviderAt(p, 1);
    }
}
