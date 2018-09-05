/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7088989 8014374 8167512 8173708
 * @summary Ensure the AES ciphers of OracleUcrypto provider works correctly
 * @key randomness
 * @library /test/lib
 * @build jdk.test.lib.Platform
 *        jdk.test.lib.Utils
 * @run main TestAES
 * @run main/othervm -Dpolicy=empty.policy TestAES
 */

import java.security.*;
import java.security.spec.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;

import jdk.test.lib.OSVersion;
import jdk.test.lib.Platform;
import jdk.test.lib.Utils;

public class TestAES extends UcryptoTest {

    private static final String[] PADDEDCIPHER_ALGOS = {
        "AES/ECB/PKCS5Padding",
        "AES/CBC/PKCS5Padding",
        "AES/CFB128/PKCS5Padding"
    };

    private static final String[] CIPHER_ALGOS = {
        "AES/ECB/NoPadding",
        "AES/CBC/NoPadding",
        "AES/CFB128/NoPadding",
        "AES/CTR/NoPadding",
    };

    private static final SecretKey CIPHER_KEY =
        new SecretKeySpec(new byte[16], "AES");

    private static final boolean IS_BAD_SOLARIS = isBadSolaris();

    public static void main(String[] args) throws Exception {
        // It has to determine Solaris version before enabling security manager.
        // Because that needs some permissions, like java.io.FilePermission.
        String policy = System.getProperty("policy");
        if (policy != null) {
            enableSecurityManager(policy);
        }

        main(new TestAES(), null);
    }

    // Enables security manager and uses the specified policy file to override
    // the default one.
    private static void enableSecurityManager(String policy) {
        String policyURL = "file://" + System.getProperty("test.src", ".") + "/"
                + policy;
        System.out.println("policyURL: " + policyURL);
        Security.setProperty("policy.url.1", policyURL);
        System.setSecurityManager(new SecurityManager());
    }

    public void doTest(Provider prov) throws Exception {
        // Provider for testing Interoperability
        Provider sunJCEProv = Security.getProvider("SunJCE");

        testCipherInterop(CIPHER_ALGOS, CIPHER_KEY, prov, sunJCEProv);
        testCipherInterop(PADDEDCIPHER_ALGOS, CIPHER_KEY, prov, sunJCEProv);

        testCipherOffset(CIPHER_ALGOS, CIPHER_KEY, prov);
        testCipherOffset(PADDEDCIPHER_ALGOS, CIPHER_KEY, prov);

        testCipherKeyWrapping(PADDEDCIPHER_ALGOS, CIPHER_KEY, prov, sunJCEProv);
        testCipherGCM(CIPHER_KEY, prov);
    }

    private static void testCipherInterop(String[] algos, SecretKey key,
                                          Provider p,
                                          Provider interopP) {
        boolean testPassed = true;
        byte[] in = new byte[32];
        (new SecureRandom()).nextBytes(in);

        for (String algo : algos) {
            try {
                // check ENC
                Cipher c;
                try {
                    c = Cipher.getInstance(algo, p);
                } catch (NoSuchAlgorithmException nsae) {
                    System.out.println("Skipping Unsupported CIP algo: " + algo);
                    continue;
                }
                c.init(Cipher.ENCRYPT_MODE, key, (AlgorithmParameters)null, null);
                byte[] eout = c.doFinal(in, 0, in.length);

                AlgorithmParameters params = c.getParameters();
                Cipher c2 = Cipher.getInstance(algo, interopP);
                c2.init(Cipher.ENCRYPT_MODE, key, params, null);
                byte[] eout2 = c2.doFinal(in, 0, in.length);

                if (!Arrays.equals(eout, eout2)) {
                    System.out.println(algo + ": DIFF FAILED");
                    testPassed = false;
                } else {
                    System.out.println(algo + ": ENC Passed");
                }

                // check DEC
                c.init(Cipher.DECRYPT_MODE, key, params, null);
                byte[] dout = c.doFinal(eout);
                c2.init(Cipher.DECRYPT_MODE, key, params, null);
                byte[] dout2 = c2.doFinal(eout2);

                if (!Arrays.equals(dout, dout2)) {
                    System.out.println(algo + ": DIFF FAILED");
                    testPassed = false;
                } else {
                    System.out.println(algo + ": DEC Passed");
                }
            } catch(Exception ex) {
                System.out.println("Unexpected Exception: " + algo);
                ex.printStackTrace();
                testPassed = false;
            }
        }

        if (!testPassed) {
            throw new RuntimeException("One or more CIPHER test failed!");
        } else {
            System.out.println("CIPHER Interop Tests Passed");
        }
    }

    private static void testCipherOffset(String[] algos, SecretKey key,
                                         Provider p) {
        boolean testPassed = true;
        byte[] in = new byte[16];
        (new SecureRandom()).nextBytes(in);

        for (int i = 0; i < algos.length; i++) {
            if (IS_BAD_SOLARIS
                    && algos[i].indexOf("CFB128") != -1
                    && p.getName().equals("OracleUcrypto")) {
                System.out.println("Ignore cases on CFB128 due to a pre-S11.3 bug");
                continue;
            }

            for (int j = 1; j < (in.length - 1); j++) {
                System.out.println("Input offset size: " + j);

                try {
                    // check ENC
                    Cipher c;
                    try {
                        c = Cipher.getInstance(algos[i], p);
                    } catch (NoSuchAlgorithmException nsae) {
                        System.out.println("Skip Unsupported CIP algo: " + algos[i]);
                        continue;
                    }
                    c.init(Cipher.ENCRYPT_MODE, key, (AlgorithmParameters)null, null);
                    byte[] eout = new byte[c.getOutputSize(in.length)];
                    int firstPartLen = in.length - j - 1;
                    //System.out.print("1st UPDATE: " + firstPartLen);
                    int k = c.update(in, 0, firstPartLen, eout, 0);
                    k += c.update(in, firstPartLen, 1, eout, k);
                    k += c.doFinal(in, firstPartLen+1, j, eout, k);

                    AlgorithmParameters params = c.getParameters();

                    Cipher c2 = Cipher.getInstance(algos[i], p);
                    c2.init(Cipher.ENCRYPT_MODE, key, params, null);
                    byte[] eout2 = new byte[c2.getOutputSize(in.length)];
                    int k2 = c2.update(in, 0, j, eout2, 0);
                    k2 += c2.update(in, j, 1, eout2, k2);
                    k2 += c2.doFinal(in, j+1, firstPartLen, eout2, k2);

                    if (!checkArrays(eout, k, eout2, k2)) testPassed = false;

                    // check DEC
                    c.init(Cipher.DECRYPT_MODE, key, params, null);
                    byte[] dout = new byte[c.getOutputSize(eout.length)];
                    k = c.update(eout, 0, firstPartLen, dout, 0);
                    k += c.update(eout, firstPartLen, 1, dout, k);
                    k += c.doFinal(eout, firstPartLen+1, eout.length - firstPartLen - 1, dout, k);
                    if (!checkArrays(in, in.length, dout, k)) testPassed = false;
                } catch(Exception ex) {
                    System.out.println("Unexpected Exception: " + algos[i]);
                    ex.printStackTrace();
                    testPassed = false;
                }
            }
        }
        if (!testPassed) {
            throw new RuntimeException("One or more CIPHER test failed!");
        } else {
            System.out.println("CIPHER Offset Tests Passed");
        }
    }

    private static void testCipherKeyWrapping(String[] algos, SecretKey key,
                                              Provider p, Provider interopP)
        throws NoSuchAlgorithmException {
        boolean testPassed = true;

        // Test SecretKey, PrivateKey and PublicKey
        Key[] tbwKeys = new Key[3];
        int[] tbwKeyTypes = { Cipher.SECRET_KEY, Cipher.PRIVATE_KEY, Cipher.PUBLIC_KEY };
        tbwKeys[0] = new SecretKeySpec(new byte[20], "Blowfish");
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(1024);
        KeyPair kp = kpg.generateKeyPair();
        tbwKeys[1] = kp.getPrivate();
        tbwKeys[2] = kp.getPublic();

        for (int i = 0; i < algos.length; i++) {
            try {
                System.out.println(algos[i] + " - Native WRAP/Java UNWRAP");

                Cipher c1;
                try {
                    c1 = Cipher.getInstance(algos[i], p);
                } catch (NoSuchAlgorithmException nsae) {
                    System.out.println("Skipping Unsupported CIP algo: " + algos[i]);
                    continue;
                }
                c1.init(Cipher.WRAP_MODE, key, (AlgorithmParameters)null, null);
                AlgorithmParameters params = c1.getParameters();
                Cipher c2 = Cipher.getInstance(algos[i], interopP);
                c2.init(Cipher.UNWRAP_MODE, key, params, null);

                for (int j = 0; j < tbwKeys.length ; j++) {
                    byte[] wrappedKey = c1.wrap(tbwKeys[j]);
                    Key recovered = c2.unwrap(wrappedKey,
                                              tbwKeys[j].getAlgorithm(), tbwKeyTypes[j]);
                    if (!checkKeys(tbwKeys[j], recovered)) testPassed = false;
                }

                System.out.println(algos[i] + " - Java WRAP/Native UNWRAP");
                c1 = Cipher.getInstance(algos[i], interopP);
                c1.init(Cipher.WRAP_MODE, key, (AlgorithmParameters)null, null);
                params = c1.getParameters();
                c2 = Cipher.getInstance(algos[i], p);
                c2.init(Cipher.UNWRAP_MODE, key, params, null);

                for (int j = 0; j < tbwKeys.length ; j++) {
                    byte[] wrappedKey = c1.wrap(tbwKeys[j]);
                    Key recovered = c2.unwrap(wrappedKey,
                                              tbwKeys[j].getAlgorithm(), tbwKeyTypes[j]);
                    if (!checkKeys(tbwKeys[j], recovered)) testPassed = false;
                }

            } catch(Exception ex) {
                System.out.println("Unexpected Exception: " + algos[i]);
                ex.printStackTrace();
                testPassed = false;
            }
        }
        if (!testPassed) {
            throw new RuntimeException("One or more CIPHER test failed!");
        } else {
            System.out.println("CIPHER KeyWrapping Tests Passed");
        }
    }

    private static void testCipherGCM(SecretKey key,
                                      Provider p) {
        boolean testPassed = true;
        byte[] in = new byte[16];
        (new SecureRandom()).nextBytes(in);

        byte[] iv = new byte[16];
        (new SecureRandom()).nextBytes(iv);


        String algo = "AES/GCM/NoPadding";
        int tagLen[] = { 128, 120, 112, 104, 96, 64, 32 };

        try {
            Cipher c;
            try {
                c = Cipher.getInstance(algo, p);
            } catch (NoSuchAlgorithmException nsae) {
                System.out.println("Skipping Unsupported CIP algo: " + algo);
                return;
            }
            for (int i = 0; i < tagLen.length; i++) {
                // change iv value to pass the key+iv uniqueness cehck for
                // GCM encryption
                iv[0] += 1;
                AlgorithmParameterSpec paramSpec = new GCMParameterSpec(tagLen[i], iv);
                // check ENC
                c.init(Cipher.ENCRYPT_MODE, key, paramSpec, null);
                c.updateAAD(iv);
                byte[] eout = c.doFinal(in, 0, in.length);

                AlgorithmParameters param = c.getParameters();
                // check DEC
                c.init(Cipher.DECRYPT_MODE, key, param, null);
                c.updateAAD(iv);
                byte[] dout = c.doFinal(eout, 0, eout.length);

                if (!Arrays.equals(dout, in)) {
                    System.out.println(algo + ": PT and RT DIFF FAILED");
                    testPassed = false;
                } else {
                    System.out.println(algo + ": tagLen " + tagLen[i] + " done");
                }
            }
        } catch(Exception ex) {
            System.out.println("Unexpected Exception: " + algo);
            ex.printStackTrace();
            testPassed = false;
        }
        if (!testPassed) {
            throw new RuntimeException("One or more CIPHER test failed!");
        } else {
            System.out.println("CIPHER GCM Tests Passed");
        }
    }

    private static boolean checkArrays(byte[] a1, int a1Len, byte[] a2, int a2Len) {
        boolean equal = true;
        if (a1Len != a2Len) {
            System.out.println("DIFFERENT OUT LENGTH");
            equal = false;
        } else {
            for (int p = 0; p < a1Len; p++) {
                if (a1[p] != a2[p]) {
                    System.out.println("DIFF FAILED");
                    equal = false;
                    break;
                }
            }
        }
        return equal;
    }

    private static boolean checkKeys(Key k1, Key k2) {
        boolean equal = true;
        if (!k1.getAlgorithm().equalsIgnoreCase(k2.getAlgorithm())) {
            System.out.println("DIFFERENT Key Algorithm");
            equal = false;
        } else if (!k1.getFormat().equalsIgnoreCase(k2.getFormat())) {
            System.out.println("DIFFERENT Key Format");
            equal = false;
        } else if (!Arrays.equals(k1.getEncoded(), k2.getEncoded())) {
            System.out.println("DIFFERENT Key Encoding");
            equal = false;
        }
        return equal;
    }

    // The cases on CFB128 mode have to be skipped on pre-S11.3.
    private static boolean isBadSolaris() {
        return Platform.isSolaris() && OSVersion.current().compareTo(new OSVersion(11, 3)) < 0;
    }
}
