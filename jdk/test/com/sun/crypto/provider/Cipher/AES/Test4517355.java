/*
 * Copyright (c) 2002, 2007, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4517355
 * @summary Verify that AES cipher.doFinal method does NOT need more
 *      than necessary bytes in decrypt mode
 * @author Valerie Peng
 */
import java.io.PrintStream;
import java.security.*;
import java.security.spec.*;
import java.util.*;

import javax.crypto.*;
import javax.crypto.spec.*;
import java.security.Provider;
import com.sun.crypto.provider.*;

public class Test4517355 {

    private static final String ALGO = "AES";
    private static final String MODE = "CBC";
    private static final String PADDING = "PKCS5Padding";
    private static final int KEYSIZE = 16; // in bytes

    public boolean execute() throws Exception {
        Random rdm = new Random();
        byte[] plainText=new byte[125];
        rdm.nextBytes(plainText);

        Cipher ci = Cipher.getInstance(ALGO+"/"+MODE+"/"+PADDING, "SunJCE");
        KeyGenerator kg = KeyGenerator.getInstance(ALGO, "SunJCE");
        kg.init(KEYSIZE*8);
        SecretKey key = kg.generateKey();

        // TEST FIX 4517355
        ci.init(Cipher.ENCRYPT_MODE, key);
        byte[] cipherText = ci.doFinal(plainText);

        byte[] iv = ci.getIV();
        AlgorithmParameterSpec aps = new IvParameterSpec(iv);
        ci.init(Cipher.DECRYPT_MODE, key, aps);
        byte[] recoveredText = new byte[plainText.length];
        try {
            int len = ci.doFinal(cipherText, 0, cipherText.length,
                                 recoveredText);
        } catch (ShortBufferException ex) {
            throw new Exception("output buffer is the right size!");
        }

        // BONUS TESTS
        // 1. make sure the recoveredText is the same as the plainText
        if (!Arrays.equals(plainText, recoveredText)) {
            throw new Exception("encryption/decryption does not work!");
        }
        // 2. make sure encryption does happen
        if (Arrays.equals(plainText, cipherText)) {
            throw new Exception("encryption does not work!");
        }
        // 3. make sure padding is working
        if ((cipherText.length/16)*16 != cipherText.length) {
            throw new Exception("padding does not work!");
        }
        // passed all tests...hooray!
        return true;
    }

    public static void main (String[] args) throws Exception {
        Security.addProvider(new com.sun.crypto.provider.SunJCE());

        Test4517355 test = new Test4517355();
        String testName = test.getClass().getName() + "[" + ALGO +
            "/" + MODE + "/" + PADDING + "]";
        if (test.execute()) {
            System.out.println(testName + ": Passed!");
        }
    }
}
