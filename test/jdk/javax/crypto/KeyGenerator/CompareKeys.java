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
import java.security.Provider;
import java.security.Security;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import javax.crypto.KeyGenerator;

/*
 * @test
 * @bug 8185127
 * @summary Test key comparison for the Keys generated through KeyGenerator
 * @run main CompareKeys
 */
public class CompareKeys {

    public static void main(String[] args) throws Exception {

        for (KeygenAlgo alg : getSupportedAlgo("KeyGenerator")) {
            System.out.printf("Verifying provider %s and algorithm %s%n",
                    alg.provider().getName(), alg.algoName());
            SecretKey k = genSecretKey(alg.algoName(), alg.provider());
            checkKeyEquality(k, copy(k));
        }

        for (KeygenAlgo alg : getSupportedAlgo("KeyPairGenerator")) {
            System.out.printf("Verifying provider %s and algorithm %s%n",
                    alg.provider().getName(), alg.algoName());
            KeyPair kp = genKeyPair(alg.algoName(), alg.provider());
            checkKeyPairEquality(kp, copy(kp));
        }
        System.out.println("Done!");
    }

    @SuppressWarnings("preview")
    private record KeygenAlgo(String algoName, Provider provider) {

    }

    private static void checkKeyPairEquality(KeyPair origkp, KeyPair copykp)
            throws Exception {

        checkKeyEquality(origkp.getPrivate(), copykp.getPrivate());
        checkKeyEquality(origkp.getPublic(), copykp.getPublic());
    }

    /**
     * Compare original Key with another copy.
     */
    private static void checkKeyEquality(Key origKey, Key copyKey) {

        if (!(origKey.equals(copyKey)
                || origKey.hashCode() == copyKey.hashCode())
                && !Arrays.equals(origKey.getEncoded(), copyKey.getEncoded())
                && !origKey.getFormat().equals(copyKey.getFormat())) {
            System.out.println("Result equals/hashCode: "
                    + !(origKey.equals(copyKey)
                    || origKey.hashCode() == copyKey.hashCode()));
            System.out.println("Result encoded check: " + !Arrays.equals(
                    origKey.getEncoded(), copyKey.getEncoded()));
            System.out.println("Result format check: "
                    + !origKey.getFormat().equals(copyKey.getFormat()));
            throw new RuntimeException("Key inequality found");
        }
        System.out.printf("%s equality check Passed%n",
                origKey.getClass().getName());
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

    private static List<KeygenAlgo> getSupportedAlgo(String type)
            throws Exception {

        List<KeygenAlgo> kgs = new LinkedList<>();
        for (Provider p : Security.getProviders()) {
            for (Provider.Service s : p.getServices()) {
                // Remove the algorithms from the list which require
                // pre-initialisation to make the Test generic across algorithms
                if (s.getType().contains(type)
                        && !s.getAlgorithm().startsWith("SunTls")) {
                    kgs.add(new KeygenAlgo(s.getAlgorithm(), s.getProvider()));
                }
            }
        }
        return kgs;
    }

    public static SecretKey genSecretKey(String algoName, Provider provider)
            throws Exception {

        return KeyGenerator.getInstance(algoName, provider).generateKey();
    }

    public static KeyPair genKeyPair(String algoName, Provider provider)
            throws Exception {

        return KeyPairGenerator.getInstance(algoName, provider).generateKeyPair();
    }
}
