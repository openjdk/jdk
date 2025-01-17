/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8048603 8242332
 * @summary Check if doFinal and update operation result in same Mac
 * @author Yu-Ching Valerie Peng, Bill Situ, Alexander Fomin
 * @library /test/lib ..
 * @modules jdk.crypto.cryptoki
 * @run main/othervm MacSameTest
 * @key randomness
 */

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Provider;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

public class MacSameTest extends PKCS11Test {

    private static final int MESSAGE_SIZE = 25;
    private static final int OFFSET = 5;
    private static final int KEY_SIZE = 128;

    /**
     * Initialize a message, instantiate a Mac object,
     * initialize the object with a SecretKey,
     * feed the message into the Mac object
     * all at once and get the output MAC as result1.
     * Reset the Mac object, chop the message into three pieces,
     * feed into the Mac object sequentially, and get the output MAC as result2.
     * Finally, compare result1 and result2 and see if they are the same.
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) throws Exception {
        main(new MacSameTest(), args);
    }

    @Override
    public void main(Provider p) {
        List<String> algorithms = getSupportedAlgorithms("Mac", "Hmac", p);
        boolean success = true;

        for (String alg : algorithms) {
            // first try w/ java secret key object
            SecretKey skey = generateKey(alg, KEY_SIZE);
            try {
                doTest(alg, skey, p);
            } catch (Exception e) {
                System.out.println("Unexpected exception: " + e);
                e.printStackTrace();
                success = false;
            }

            try {
                // No KeyGenerator support for PBE
                KeyGenerator kg = KeyGenerator.getInstance(alg, p);
                kg.init(KEY_SIZE);
                skey = kg.generateKey();
                doTest(alg, skey, p);
            } catch (NoSuchAlgorithmException nsae) {
                System.out.println("Skip test using native key for " + alg);
                continue;
            } catch (Exception e) {
                System.out.println("Unexpected exception: " + e);
                e.printStackTrace();
                success = false;
            }
        }

        if (!success) {
            throw new RuntimeException("Test failed");
        }
    }

    private void doTest(String algo, SecretKey key, Provider provider)
            throws NoSuchAlgorithmException, NoSuchProviderException,
            InvalidKeyException {
        System.out.println("Test " + algo);
        Mac mac = Mac.getInstance(algo, provider);

        byte[] plain = new byte[MESSAGE_SIZE];
        for (int i = 0; i < MESSAGE_SIZE; i++) {
            plain[i] = (byte) (i % 256);
        }

        byte[] tail = new byte[plain.length - OFFSET];
        System.arraycopy(plain, OFFSET, tail, 0, tail.length);

        mac.init(key);
        byte[] result1 = mac.doFinal(plain);

        mac.reset();
        mac.update(plain[0]);
        mac.update(plain, 1, OFFSET - 1);
        byte[] result2 = mac.doFinal(tail);

        if (!java.util.Arrays.equals(result1, result2)) {
            throw new RuntimeException("result1 and result2 are not the same");
        }
    }

}
