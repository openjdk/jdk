/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.security.*;
import java.security.interfaces.*;
import java.security.spec.*;
import java.util.stream.IntStream;
import jtreg.SkippedException;

/**
 * @test
 * @bug 8244336
 * @summary Test the NONEwithRSA signature refactoring for JCE layer
 *     algorithm restriction
 * @library /test/lib ..
 * @modules jdk.crypto.cryptoki
 */
public class TestNONEwithRSA extends PKCS11Test {

    private static final String SIGALG = "NONEwithRSA";

    private static final int[] KEYSIZES = { 2048, 3072 };
    private static final byte[] DATA = generateData(100);

    public static void main(String[] args) throws Exception {
        main(new TestNONEwithRSA(), args);
    }

    @Override
    public void main(Provider p) throws Exception {
        try {
            Signature.getInstance(SIGALG, p);
        } catch (NoSuchAlgorithmException nsae) {
            throw new SkippedException("Skip due to no support for " + SIGALG);
        }

        for (int kSize : KEYSIZES) {
            System.out.println("[KEYSIZE = " + kSize + "]");
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", p);
            kpg.initialize(kSize);
            KeyPair kp = kpg.generateKeyPair();
            PrivateKey privKey = kp.getPrivate();
            PublicKey pubKey = kp.getPublic();
            checkSignature(p, DATA, pubKey, privKey);
        }
    }

    private static void checkSignature(Provider p, byte[] data, PublicKey pub,
            PrivateKey priv)
            throws NoSuchAlgorithmException, InvalidKeyException,
            SignatureException, NoSuchProviderException,
            InvalidAlgorithmParameterException {

        Signature sig = Signature.getInstance(SIGALG, p);
        sig.initSign(priv);

        sig.update(data);
        byte[] signedData = sig.sign();

        // Make sure signature verifies with original data
        sig.initVerify(pub);
        sig.update(data);
        if (!sig.verify(signedData)) {
            throw new RuntimeException("Failed to verify signature");
        }

        // Make sure signature does NOT verify when the original data
        // has changed
        sig.initVerify(pub);
        sig.update(data);
        sig.update(data);
        if (sig.verify(signedData)) {
            throw new RuntimeException("Failed to detect bad signature");
        }
        System.out.println("    => Passed");
    }
}
