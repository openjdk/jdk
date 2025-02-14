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
 * @bug 8350134
 * @summary Verify that pkcs11 EC keys has public key associated
 * @library /test/lib ..
 * @modules jdk.crypto.cryptoki
 *          java.base/sun.security.util
 * @run main/othervm CalculatePublicKey p11-nss.txt
 * @run main/othervm CalculatePublicKey p11-nss-sensitive.txt
 */

import sun.security.util.InternalPrivateKey;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import java.io.IOException;
import java.nio.file.Path;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;

public class CalculatePublicKey extends PKCS11Test {

    public static void main(String[] args) throws Exception {
        main(new CalculatePublicKey(args[0]), args);
    }

    public CalculatePublicKey(String conf) throws IOException {
        copyNssCertKeyToClassesDir();
        setCommonSystemProps();
        System.setProperty("CUSTOM_P11_CONFIG",
                Path.of(System.getProperty("test.src", "."), "../nss/" + conf).toString());
        System.setProperty("TOKEN", "nss");
        System.setProperty("TEST", "basic");
    }

    @Override
    public void main(Provider p) throws Exception {

        // Newly generated
        var kp = KeyPairGenerator.getInstance("EC", p).generateKeyPair();
        check(kp.getPrivate());

        // Translate from another key
        var kp2 = KeyPairGenerator.getInstance("EC", "SunEC").generateKeyPair();
        check((PrivateKey) KeyFactory.getInstance("EC", p).translateKey(kp2.getPrivate()));

        // Generate from PKCS8
        check(KeyFactory.getInstance("EC", p).generatePrivate(
                new PKCS8EncodedKeySpec(kp2.getPrivate().getEncoded())));

        // Unwrapped: not supported yet.
        KeyGenerator kg = KeyGenerator.getInstance("AES", p);
        kg.init(256);
        var k = kg.generateKey();
        var cipher = Cipher.getInstance("AES_256/KW/PKCS5Padding", p);
        cipher.init(Cipher.WRAP_MODE, k);
        var wrapped = cipher.wrap(kp.getPrivate());
        cipher.init(Cipher.UNWRAP_MODE, k);

        // check((PrivateKey) cipher.unwrap(wrapped, "EC", Cipher.PRIVATE_KEY));
    }

    static void check(PrivateKey sk) throws Exception {
        System.out.println(sk);
        if (sk instanceof InternalPrivateKey ipk) {
            if (ipk.calculatePublicKey() == null) {
                throw new Exception("Associated public key is null");
            }
        } else {
            throw new Exception("Not an InternalPrivateKey");
        }
    }
}
