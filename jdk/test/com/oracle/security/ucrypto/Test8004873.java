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
 * @bug     8004873
 * @summary Need to include data buffered by Padding impl when calculating
 * output buffer sizes.
 */

import java.io.*;
import java.security.*;
import java.security.spec.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;

public class Test8004873 extends UcryptoTest {

    private static final String[] PADDEDCIPHER_ALGOS = {
        "AES/ECB/PKCS5Padding",
        "AES/CBC/PKCS5Padding",
        "AES/CFB128/PKCS5Padding"
    };

    private static final SecretKey AES_KEY;

    static {
        byte[] keyValue = {
            62, 124, -2, -15, 86, -25, 18, -112, 110, 31, 96, 59,
            89, 70, 60, 103};
        AES_KEY = new SecretKeySpec(keyValue, "AES");
    }

    public static void main(String[] args) throws Exception {
        main(new Test8004873(), null);
    }

    public void doTest(Provider prov) throws Exception {
        boolean result = true;
        for (String algo : PADDEDCIPHER_ALGOS) {
            if (!testOOS(algo, prov)) {
                result = false;
                System.out.println(algo + " Test Failed!");
            }
        }
        if (!result) {
            throw new Exception("One or more test failed!");
        }
    }

    private boolean testOOS(String algo, Provider prov)
        throws Exception {

        String password = "abcd1234";
        Cipher c;
        try {
            c = Cipher.getInstance(algo, prov);
        } catch(NoSuchAlgorithmException nsae) {
            System.out.println("Skipping Unsupported algo: " + algo);
            return true;
        }
        c.init(Cipher.ENCRYPT_MODE, AES_KEY);
        AlgorithmParameters params = c.getParameters();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CipherOutputStream cos = new CipherOutputStream(baos, c);
        ObjectOutputStream oos = new ObjectOutputStream(cos);
        oos.writeObject(password);
        oos.flush();
        oos.close();
        byte[] encrypted = baos.toByteArray();

        c.init(Cipher.DECRYPT_MODE, AES_KEY, params);

        ByteArrayInputStream bais = new ByteArrayInputStream(encrypted);
        CipherInputStream cis = new CipherInputStream(bais, c);
        ObjectInputStream ois = new ObjectInputStream(cis);

        String recovered = (String) ois.readObject();
        return recovered.equals(password);
    }
}
