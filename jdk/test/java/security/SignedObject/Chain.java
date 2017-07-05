/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.security.Signature;
import java.security.SignedObject;
import java.security.KeyPairGenerator;
import java.security.KeyPair;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Arrays;

/*
 * @test
 * @bug 8050374
 * @summary Verify a chain of signed objects
 */
public class Chain {

    static enum KeyAlg {
        RSA("RSA"),
        DSA("DSA"),
        EC("EC");

        final String name;

        KeyAlg(String alg) {
            this.name = alg;
        }
    }

    static enum Provider {
        Default("default"),
        SunRsaSign("SunRsaSign"),
        Sun("SUN"),
        SunEC("SunEC"),
        SunJSSE("SunJSSE"),
        SunMSCAPI("SunMSCAPI");

        final String name;

        Provider(String name) {
            this.name = name;
        }
    }

    static enum SigAlg {
        MD2withRSA("MD2withRSA"),
        MD5withRSA("md5withRSA"),

        SHA1withDSA("SHA1withDSA"),
        SHA224withDSA("SHA224withDSA"),
        SHA256withDSA("SHA256withDSA"),

        SHA1withRSA("Sha1withrSA"),
        SHA224withRSA("SHA224withRSA"),
        SHA256withRSA("SHA256withRSA"),
        SHA384withRSA("SHA384withRSA"),
        SHA512withRSA("SHA512withRSA"),

        SHA1withECDSA("SHA1withECDSA"),
        SHA256withECDSA("SHA256withECDSA"),
        SHA224withECDSA("SHA224withECDSA"),
        SHA384withECDSA("SHA384withECDSA"),
        SHA512withECDSA("SHA512withECDSA"),

        MD5andSHA1withRSA("MD5andSHA1withRSA");

        final String name;

        SigAlg(String name) {
            this.name = name;
        }
    }

    static class Test {
        final Provider provider;
        final KeyAlg keyAlg;
        final SigAlg sigAlg;

        Test(SigAlg sigAlg, KeyAlg keyAlg, Provider privider) {
            this.provider = privider;
            this.keyAlg = keyAlg;
            this.sigAlg = sigAlg;
        }
    }

    private static final Test[] tests = {
        new Test(SigAlg.SHA1withDSA, KeyAlg.DSA, Provider.Default),
        new Test(SigAlg.MD2withRSA, KeyAlg.RSA, Provider.Default),
        new Test(SigAlg.MD5withRSA, KeyAlg.RSA, Provider.Default),
        new Test(SigAlg.SHA1withRSA, KeyAlg.RSA, Provider.Default),
        new Test(SigAlg.SHA1withDSA, KeyAlg.DSA, Provider.Sun),
        new Test(SigAlg.SHA224withDSA, KeyAlg.DSA, Provider.Sun),
        new Test(SigAlg.SHA256withDSA, KeyAlg.DSA, Provider.Sun),
    };

    private static final String str = "to-be-signed";
    private static final int N = 3;

    public static void main(String argv[]) {
        boolean result = Arrays.stream(tests).allMatch((test) -> runTest(test));
        if(result) {
            System.out.println("All tests passed");
        } else {
            throw new RuntimeException("Some tests failed");
        }
    }

    static boolean runTest(Test test) {
        System.out.format("Test: provider = %s, signature algorithm = %s, "
                + "key algorithm = %s\n",
                test.provider, test.sigAlg, test.keyAlg);
        try {
            // Generate all private/public key pairs
            PrivateKey[] privKeys = new PrivateKey[N];
            PublicKey[] pubKeys = new PublicKey[N];
            PublicKey[] anotherPubKeys = new PublicKey[N];
            KeyPairGenerator kpg = KeyPairGenerator.getInstance(
                    test.keyAlg.name);
            for (int j=0; j < N; j++) {
                KeyPair kp = kpg.genKeyPair();
                KeyPair anotherKp = kpg.genKeyPair();
                privKeys[j] = kp.getPrivate();
                pubKeys[j] = kp.getPublic();
                anotherPubKeys[j] = anotherKp.getPublic();

                if (Arrays.equals(pubKeys[j].getEncoded(),
                        anotherPubKeys[j].getEncoded())) {
                    System.out.println("Failed: it should not get "
                            + "the same pair of public key");
                    return false;
                }
            }

            Signature signature;
            if (test.provider != Provider.Default) {
                signature = Signature.getInstance(test.sigAlg.name,
                        test.provider.name);
            } else {
                signature = Signature.getInstance(test.sigAlg.name);
            }

            // Create a chain of signed objects
            SignedObject[] objects = new SignedObject[N];
            objects[0] = new SignedObject(str, privKeys[0], signature);
            for (int j = 1; j < N; j++) {
                objects[j] = new SignedObject(objects[j - 1], privKeys[j],
                        signature);
            }

            // Verify the chain
            int n = objects.length - 1;
            SignedObject object = objects[n];
            do {
                if (!object.verify(pubKeys[n], signature)) {
                    System.out.println("Failed: verification failed, n = " + n);
                    return false;
                }

                if (object.verify(anotherPubKeys[n], signature)) {
                    System.out.println("Failed: verification should not "
                            + "succeed with wrong public key, n = " + n);
                    return false;
                }

                object = (SignedObject) object.getObject();
                n--;
            } while (n > 0);

            System.out.println("signed data: " + object.getObject());
            if (!str.equals(object.getObject())) {
                System.out.println("Failed: signed data is not equal to "
                        + "original one");
                return false;
            }

            System.out.println("Test passed");
            return true;
        } catch (NoSuchProviderException nspe) {
            if (test.provider == Provider.SunMSCAPI
                    && !System.getProperty("os.name").startsWith("Windows")) {
                System.out.println("SunMSCAPI is available only on Windows: "
                        + nspe);
                return true;
            }
            System.out.println("Unexpected exception: " + nspe);
            return false;
        } catch (Exception e) {
            System.out.println("Unexpected exception: " + e);
            e.printStackTrace(System.out);
            return false;
        }
    }
}

