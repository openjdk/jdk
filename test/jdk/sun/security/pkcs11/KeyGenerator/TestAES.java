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
 * @bug 8267319
 * @modules java.base/sun.security.util
 *          jdk.crypto.cryptoki
 * @summary Check AES key generator.
 * @library /test/lib ..
 * @run main TestAES
 */
import java.security.Provider;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidParameterException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import static sun.security.util.SecurityProviderConstants.*;

public class TestAES extends PKCS11Test {

    private static final String ALGO = "AES";

    public static void main(String[] args) throws Exception {
        main(new TestAES(), args);
    }

    @Override
    public void main(Provider p) throws Exception {
        System.out.println("Testing " + p.getName());
        KeyGenerator kg;
        try {
            kg = KeyGenerator.getInstance(ALGO, p);
        } catch (NoSuchAlgorithmException nsae) {
            System.out.println("Skip; no support for " + ALGO);
            return;
        }

        // first try w/o setting a key length and check if the generated key
        // length matches
        SecretKey key = kg.generateKey();
        byte[] keyValue = key.getEncoded();
        if (key.getEncoded().length != getDefAESKeySize() >> 3) {
            throw new RuntimeException("Default AES key length should be " +
                    getDefAESKeySize());
        }

        for (int keySize : new int[] { 16, 32, 64, 128, 256, 512, 1024 }) {
            boolean isValid = (keySize == 128 || keySize == 192 ||
                    keySize == 256);
            try {
                kg.init(keySize);
                if (!isValid) {
                    throw new RuntimeException(keySize + " is invalid keysize");
                }
                key = kg.generateKey();
                if (key.getEncoded().length != keySize >> 3) {
                    throw new RuntimeException("Generated key len mismatch!");
                }
            } catch (InvalidParameterException e) {
                if (isValid) {
                    throw new RuntimeException("IPE thrown for valid keySize");
                } else {
                    System.out.println("Expected IPE thrown for " + keySize);
                }
            }
        }
    }
}
