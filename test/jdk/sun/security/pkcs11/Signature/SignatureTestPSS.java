/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.util.ArrayList;
import java.util.List;

import jtreg.SkippedException;

/**
 * @test id=sha
 * @bug 8080462 8226651 8242332
 * @summary Generate a RSASSA-PSS signature and verify it using PKCS11 provider
 * @library /test/lib ..
 * @modules jdk.crypto.cryptoki
 * @run main SignatureTestPSS
 */

/**
 * @test id=sha3
 * @bug 8080462 8226651 8242332
 * @summary Generate a RSASSA-PSS signature and verify it using PKCS11 provider
 * @library /test/lib ..
 * @modules jdk.crypto.cryptoki
 * @run main SignatureTestPSS sha3
 */
public class SignatureTestPSS extends PKCS11Test {

    private static final String SIGALG = "RSASSA-PSS";

    private static final int[] KEYSIZES = { 2048, 3072 };

    private static String[] DIGESTS = null;

    private static final String[] SHA_DIGESTS = {
            "SHA-224", "SHA-256", "SHA-384" , "SHA-512",
    };
    private static final String[] SHA3_DIGESTS = {
            "SHA3-224", "SHA3-256", "SHA3-384" , "SHA3-512"
    };

    private static final byte[] DATA = generateData(100);

    /**
     * How much times signature updated.
     */
    private static final int UPDATE_TIMES_FIFTY = 50;

    /**
     * How much times signature initial updated.
     */
    private static final int UPDATE_TIMES_HUNDRED = 100;

    private static final List<String> skippedAlgs = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        DIGESTS = (args.length > 0 && "sha3".equals(args[0])) ?
                SHA3_DIGESTS : SHA_DIGESTS;

        main(new SignatureTestPSS(), args);
    }

    @Override
    public void main(Provider p) throws Exception {
        if (!PSSUtil.isSignatureSupported(p)) {
            throw new SkippedException("Skip due to no support for " + SIGALG);
        }

        for (int kSize : KEYSIZES) {
            System.out.println("[KEYSIZE = " + kSize + "]");
            KeyPair kp = PSSUtil.generateKeys(p, kSize);
            PrivateKey privKey = kp.getPrivate();
            PublicKey pubKey = kp.getPublic();
            for (String hash : DIGESTS) {
                for (String mgfHash : DIGESTS) {
                    System.out.println("    [Hash  = " + hash +
                            ", MGF1 Hash = " + mgfHash + "]");
                    PSSUtil.AlgoSupport s =
                            PSSUtil.isHashSupported(p, hash, mgfHash);
                    if (s == PSSUtil.AlgoSupport.NO) {
                        System.out.println("    => Skip; no support");
                        skippedAlgs.add("[Hash  = " + hash +
                                        ", MGF1 Hash = " + mgfHash + "]");
                        continue;
                    }
                    checkSignature(p, DATA, pubKey, privKey, hash, mgfHash, s);
                }
            };
        }

        if (!skippedAlgs.isEmpty()) {
            throw new SkippedException("Test Skipped :" + skippedAlgs);
        }
    }

    private static void checkSignature(Provider p, byte[] data, PublicKey pub,
            PrivateKey priv, String hash, String mgfHash, PSSUtil.AlgoSupport s)
            throws NoSuchAlgorithmException, InvalidKeyException,
            SignatureException {

        // only test RSASSA-PSS signature against the supplied hash/mgfHash
        // if they are supported; otherwise PKCS11 library will throw
        // CKR_MECHANISM_PARAM_INVALID at Signature.initXXX calls
        Signature sig = Signature.getInstance(SIGALG, p);
        AlgorithmParameterSpec params = new PSSParameterSpec(
                hash, "MGF1", new MGF1ParameterSpec(mgfHash), 0, 1);
        sig.initSign(priv);

        try {
            sig.setParameter(params);
        } catch (InvalidAlgorithmParameterException iape) {
            if (s == PSSUtil.AlgoSupport.MAYBE) {
                // confirmed to be unsupported; skip the rest of the test
                System.out.printf("    => Skip; no PSS support public key: %s, private key: %s, " +
                                  "hash: %s, mgf hash: %s, Algo Support: %s%n",
                        pub,
                        priv,
                        hash,
                        mgfHash,
                        s);
                skippedAlgs.add(String.format(
                        "[public key: %s, private key: %s, " +
                        "hash: %s, mgf hash: %s, Algo Support: %s]",
                        pub,
                        priv,
                        hash,
                        mgfHash,
                        s)
                );
                return;
            } else {
                throw new RuntimeException("Unexpected Exception", iape);
            }
        }

        for (int i = 0; i < UPDATE_TIMES_HUNDRED; i++) {
            sig.update(data);
        }
        byte[] signedData = sig.sign();

        // Make sure signature verifies with original data
        // do we need to call sig.setParameter(params) again?
        sig.initVerify(pub);
        for (int i = 0; i < UPDATE_TIMES_HUNDRED; i++) {
            sig.update(data);
        }
        if (!sig.verify(signedData)) {
            throw new RuntimeException("Failed to verify signature");
        }

        // Make sure signature does NOT verify when the original data
        // has changed
        sig.initVerify(pub);
        for (int i = 0; i < UPDATE_TIMES_FIFTY; i++) {
            sig.update(data);
        }

        if (sig.verify(signedData)) {
            throw new RuntimeException("Failed to detect bad signature");
        }
        System.out.println("    => Passed");
    }
}
