/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4532506 4999599
 * @library /test/lib
 * @summary Serializing KeyPair on one VM (Sun),
 *      and Deserializing on another (IBM) fails
 * @run main/othervm/java.security.policy=Serial.policy Serial
 */

import java.io.*;
import java.math.BigInteger;
import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import jdk.test.lib.security.DiffieHellmanGroup;
import jdk.test.lib.security.SecurityUtils;

public class Serial {

    // providers
    private static final String SUN = System.getProperty("test.provider.name", "SUN");
    private static final String RSA = System.getProperty("test.provider.name", "SunRsaSign");
    private static final String JCE = System.getProperty("test.provider.name", "SunJCE");

    public static void main(String[] args) throws Exception {

        // generate DSA key pair
        String kpgAlgorithmDsa = "DSA";
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(kpgAlgorithmDsa, SUN);
        kpg.initialize(SecurityUtils.getTestKeySize(kpgAlgorithmDsa));
        KeyPair dsaKp = kpg.genKeyPair();

        // serialize DSA key pair
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(dsaKp);
        oos.close();

        // deserialize DSA key pair
        ObjectInputStream ois = new ObjectInputStream
                        (new ByteArrayInputStream(baos.toByteArray()));
        KeyPair dsaKp2 = (KeyPair)ois.readObject();
        ois.close();

        if (!dsaKp2.getPublic().equals(dsaKp.getPublic()) ||
            !dsaKp2.getPrivate().equals(dsaKp.getPrivate())) {
            throw new SecurityException("DSA test failed");
        }

        // generate RSA key pair
        String kpgAlgorithmRsa = "RSA";
        kpg = KeyPairGenerator.getInstance(kpgAlgorithmRsa, RSA);
        kpg.initialize(SecurityUtils.getTestKeySize(kpgAlgorithmRsa));
        KeyPair rsaKp = kpg.genKeyPair();

        // serialize RSA key pair
        baos.reset();
        oos = new ObjectOutputStream(baos);
        oos.writeObject(rsaKp);
        oos.close();

        // deserialize RSA key pair
        ois = new ObjectInputStream
                        (new ByteArrayInputStream(baos.toByteArray()));
        KeyPair rsaKp2 = (KeyPair)ois.readObject();
        ois.close();

        if (!rsaKp2.getPublic().equals(rsaKp.getPublic()) ||
            !rsaKp2.getPrivate().equals(rsaKp.getPrivate())) {
            throw new SecurityException("RSA test failed");
        }

        // generate DH key pair
        DiffieHellmanGroup dhGroup = SecurityUtils.getTestDHGroup();
        kpg = KeyPairGenerator.getInstance("DiffieHellman", JCE);
        kpg.initialize(new DHParameterSpec(dhGroup.getPrime(), dhGroup.getBase()));
        KeyPair dhKp = kpg.genKeyPair();

        // serialize DH key pair
        baos.reset();
        oos = new ObjectOutputStream(baos);
        oos.writeObject(dhKp);
        oos.close();

        // deserialize DH key pair
        ois = new ObjectInputStream
                        (new ByteArrayInputStream(baos.toByteArray()));
        KeyPair dhKp2 = (KeyPair)ois.readObject();
        ois.close();

        if (!dhKp2.getPublic().equals(dhKp.getPublic()) ||
            !dhKp2.getPrivate().equals(dhKp.getPrivate())) {
            throw new SecurityException("DH test failed");
        }

        // generate RC5 key
        SecretKeySpec rc5Key = new SecretKeySpec(new byte[128], "RC5");

        // serialize RC5 key
        baos.reset();
        oos = new ObjectOutputStream(baos);
        oos.writeObject(rc5Key);
        oos.close();

        // deserialize RC5 key
        ois = new ObjectInputStream
                        (new ByteArrayInputStream(baos.toByteArray()));
        SecretKey rc5Key2 = (SecretKey)ois.readObject();
        ois.close();

        if (!rc5Key.equals(rc5Key2)) {
            throw new SecurityException("RC5 test failed");
        }

        // generate PBE key

        // Salt
        byte[] salt = {
                (byte)0xc7, (byte)0x73, (byte)0x21, (byte)0x8c,
                (byte)0x7e, (byte)0xc8, (byte)0xee, (byte)0x99
        };

        // Iteration count
        int count = 20;

        // Create PBE parameter set
        PBEParameterSpec pbeParamSpec = new PBEParameterSpec(salt, count);

        char[] password = new char[] {'f', 'o', 'o'};
        PBEKeySpec pbeKeySpec = new PBEKeySpec(password);
        SecretKeyFactory keyFac =
                        SecretKeyFactory.getInstance("PBEWithMD5AndDES", JCE);
        SecretKey pbeKey = keyFac.generateSecret(pbeKeySpec);

        // serialize PBE key
        baos.reset();
        oos = new ObjectOutputStream(baos);
        oos.writeObject(pbeKey);
        oos.close();

        // deserialize PBE key
        ois = new ObjectInputStream
                        (new ByteArrayInputStream(baos.toByteArray()));
        SecretKey pbeKey2 = (SecretKey)ois.readObject();
        ois.close();

        if (!pbeKey.equals(pbeKey2)) {
            throw new SecurityException("PBE test failed");
        }

        checkKey("AES", 128);
        checkKey("Blowfish", -1);
        checkKey("DES", 56);
        checkKey("DESede", 168);
        checkKey("HmacMD5", -1);
        checkKey("HmacSHA1", -1);
    }

    private static void checkKey(String algorithm, int size) throws Exception {
        // generate key
        KeyGenerator kg = KeyGenerator.getInstance(algorithm, JCE);
        if (size > 0) {
            kg.init(size);
        }
        SecretKey key = kg.generateKey();

        // serialize key
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(key);
        oos.close();

        // deserialize key
        ObjectInputStream ois = new ObjectInputStream
                                (new ByteArrayInputStream(baos.toByteArray()));
        SecretKey key2 = (SecretKey)ois.readObject();
        ois.close();

        if (!key.equals(key2)) {
            throw new SecurityException(algorithm + " test failed");
        }
    }
}
