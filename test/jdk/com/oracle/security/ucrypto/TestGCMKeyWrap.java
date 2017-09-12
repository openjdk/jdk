/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8014374
 * @summary Ensure key wrap/unwrap works using AES/GCM/NoPadding
 * @key randomness
 */

import java.io.*;
import java.security.*;
import java.security.spec.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;

public class TestGCMKeyWrap extends UcryptoTest {

    public static void main(String[] args) throws Exception {
        main(new TestGCMKeyWrap(), null);
    }

    public void doTest(Provider p) throws Exception {
        // check if GCM support exists
        try {
            Cipher.getInstance("AES/GCM/NoPadding", p);
        } catch (NoSuchAlgorithmException nsae) {
            System.out.println("Skipping Test due to no GCM support");
            return;
        }

        Random rdm = new Random();

        //init Secret Key
        byte[] keyValue = new byte[16];
        rdm.nextBytes(keyValue);
        SecretKey key = new SecretKeySpec(keyValue, "AES");

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding", p);
        cipher.init(Cipher.WRAP_MODE, key);

        byte[] wrappedKey = cipher.wrap(key);

        try { // make sure ISE is thrown if re-using the same key/IV
            wrappedKey = cipher.wrap(key);
            throw new Exception("FAIL: expected ISE not thrown");
        } catch(IllegalStateException ise){
            System.out.println("Expected ISE thrown for re-wrapping");
        }

        //unwrap the key
        AlgorithmParameters params = cipher.getParameters();
        cipher.init(Cipher.UNWRAP_MODE, key, params);
        Key unwrappedKey = cipher.unwrap(wrappedKey, "AES", Cipher.SECRET_KEY);

        //check if we can unwrap second time
        unwrappedKey = cipher.unwrap(wrappedKey, "AES", Cipher.SECRET_KEY);

        // Comparison
        if (!Arrays.equals(key.getEncoded(), unwrappedKey.getEncoded())) {
            throw new Exception("FAIL: keys are not equal");
        } else {
            System.out.println("Passed key equality check");
        }
    }
}
