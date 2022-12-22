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
 * @bug 8264849
 * @summary Verify cipher key size restriction is enforced properly with IKE
 * @library /test/lib ../..
 * @run main/othervm TestKeySizeCheck
 */
import java.util.Arrays;
import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.*;

// adapted from com/sun/crypto/provider/Cipher/KeyWrap/TestKeySizeCheck.java
public class TestKeySizeCheck extends PKCS11Test {

    private static final byte[] BYTES_32 =
            Arrays.copyOf("1234567890123456789012345678901234".getBytes(), 32);

    private static SecretKey getKey(int sizeInBytes) {
        if (sizeInBytes <= BYTES_32.length) {
            return new SecretKeySpec(BYTES_32, 0, sizeInBytes, "AES");
        } else {
            return new SecretKeySpec(new byte[sizeInBytes], "AES");
        }
    }

    private static String getModeStr(int mode) {
        return (mode == Cipher.ENCRYPT_MODE? "ENC" : "WRAP");
    }

    public static void test(Provider p, String algo, int[] invalidKeySizes)
            throws Exception {

        Cipher c = Cipher.getInstance(algo, p);
        System.out.println("Testing " + algo);

        int[] modes = { Cipher.ENCRYPT_MODE, Cipher.WRAP_MODE };
        for (int ks : invalidKeySizes) {
            System.out.println("keysize: " + ks);
            SecretKey key = getKey(ks);

            for (int m : modes) {
                try {
                    c.init(m, key);
                    throw new RuntimeException("Expected IKE not thrown for "
                            + getModeStr(m));
                } catch (InvalidKeyException ike) {
                    System.out.println(" => expected IKE thrown for "
                            + getModeStr(m));
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        main(new TestKeySizeCheck(), args);
    }

    @Override
    public void main(Provider p) throws Exception {
        String[] algos = {
            "AESWrap", "AESWrapPad",
            "AES/KW/PKCS5Padding", "AES/KW/NoPadding", "AES/KWP/NoPadding"
        };
        int[] keySizes = { 128, 192, 256 };

        for (String a : algos) {
            if (p.getService("Cipher", a) == null) {
                System.out.println("Skip, due to no support:  " + a);
                continue;
            }
            test(p, a, new int[] { 120, 264 });
            String from = (a == "AESWrap" || a == "AESWrapPad"? a : "AES");
            test(p, a.replace(from, from + "_128"), new int[] {192, 256 });
            test(p, a.replace(from, from + "_192"), new int[] {128, 256 });
            test(p, a.replace(from, from + "_256"), new int[] {128, 192 });
        }
        System.out.println("All Tests Passed");
    }
}
