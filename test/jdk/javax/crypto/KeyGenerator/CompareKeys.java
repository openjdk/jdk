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

import javax.crypto.SecretKey;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Arrays;
import javax.crypto.KeyGenerator;

/*
 * @test
 * @bug 8185127
 * @summary Test key comparison between the Keys generated through KeyGenerator
 * @run main CompareKeys
 */
public class CompareKeys {

    public static void main(String[] args) throws Exception {

        for (KeyAlgo ka : KeyAlgo.values()) {
            SecretKey k = ka.genSecretKey();
            checkKeyEquality(k, copy(k));
        }

        for (KeyPairAlgo kpa : KeyPairAlgo.values()) {
            KeyPair kp = kpa.genKeyPair();
            checkKeyPairEquality(kp, copy(kp));
        }
        System.out.println("Done!");
    }

    private static void checkKeyPairEquality(KeyPair origkp, KeyPair copykp)
            throws Exception {

        checkKeyEquality(origkp.getPrivate(), copykp.getPrivate());
        checkKeyEquality(origkp.getPublic(), copykp.getPublic());
    }

    /**
     * Compare original KeyPair with another copy.
     */
    private static void checkKeyEquality(Key origKey, Key copyKey) {

        if (!origKey.equals(copyKey)
                && !Arrays.equals(origKey.getEncoded(), copyKey.getEncoded())
                && origKey.hashCode() != copyKey.hashCode()) {
            throw new RuntimeException("Key inequality found");
        }
        System.out.printf("Equality check Passed for key Type:%s & Algo: %s%n",
                origKey.getClass(), origKey.getAlgorithm());
    }

    /**
     * Get a copy of the original object type.
     */
    private static <T extends Object> T copy(T orig)
            throws IOException, ClassNotFoundException {

        byte[] serialize;
        try ( ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(orig);
            serialize = bos.toByteArray();
        }

        T copy;
        try ( ByteArrayInputStream bis = new ByteArrayInputStream(serialize);
                ObjectInputStream ois = new ObjectInputStream(bis)) {
            copy = (T) ois.readObject();
        }
        return copy;
    }

    private enum KeyAlgo {

        AES("AES"),
        ARCFOUR("ARCFOUR"),
        Blowfish("Blowfish"),
        ChaCha20("ChaCha20"),
        DES("DES"),
        DESede("DESede"),
        HmacMD5("HmacMD5"),
        HmacSHA1("HmacSHA1"),
        HmacSHA224("HmacSHA224"),
        HmacSHA256("HmacSHA256"),
        HmacSHA384("HmacSHA384"),
        HmacSHA512("HmacSHA512"),
        RC2("RC2");

        private String algoName;

        private KeyAlgo(String name) {
            this.algoName = name;
        }

        public SecretKey genSecretKey() throws Exception {
            KeyGenerator kg = KeyGenerator.getInstance(this.algoName);
            return kg.generateKey();
        }
    }

    private enum KeyPairAlgo {

        DiffieHellman("DiffieHellman"),
        DSA("DSA"),
        RSA("RSA"),
        RSASSAPSS("RSASSA-PSS"),
        EC("EC"),
        EdDSA("EdDSA"),
        Ed25519("Ed25519"),
        Ed448("Ed448"),
        XDH("XDH"),
        X25519("X25519"),
        X448("X448");

        private final String algoName;

        private KeyPairAlgo(String name) {
            this.algoName = name;
        }

        public KeyPair genKeyPair() throws Exception {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance(this.algoName);
            return kpg.generateKeyPair();
        }
    }
}
