/*
 * Copyright (c) 2021, Red Hat, Inc.
 *
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

import java.security.AlgorithmParameterGenerator;
import java.security.AlgorithmParameters;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.DSAGenParameterSpec;
import java.security.spec.DSAParameterSpec;

/*
 * @test
 * @bug 8271566
 * @library /test/lib ..
 * @modules jdk.crypto.cryptoki
 * @run main/othervm/timeout=30 LargeDSAKey
 */

public final class LargeDSAKey extends PKCS11Test {

    private static final boolean enableDebug = false;

    private static final String knownText =
            "Known text known text known text";

    @Override
    public void main(Provider p) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("DSA", p);
        AlgorithmParameterGenerator dsaParGen =
                AlgorithmParameterGenerator.getInstance("DSA");
        DSAGenParameterSpec dsaParGenSpec =
                new DSAGenParameterSpec(2048, 256);
        dsaParGen.init(dsaParGenSpec, new SecureRandom());
        AlgorithmParameters params = dsaParGen.generateParameters();
        DSAParameterSpec dsaParams =
                params.getParameterSpec(DSAParameterSpec.class);
        kpg.initialize(dsaParams);
        KeyPair kp = kpg.generateKeyPair();
        doTestSignature(kp, p);
    }

    private static void doTestSignature(KeyPair kp, Provider p)
            throws Exception {
        byte[] knownTextSig = null;
        Signature s = Signature.getInstance("SHA1withDSA", p);
        PrivateKey privKey = kp.getPrivate();
        PublicKey pubKey = kp.getPublic();
        if (enableDebug) {
            System.out.println("Signature algorithm: " + s.getAlgorithm());
            System.out.println("Signature Provider: " + s.getProvider());
            System.out.println("Private key for signature: " + privKey);
            System.out.println("Public key for signature: " + pubKey);
        }
        s.initSign(privKey);
        s.update(knownText.getBytes());
        knownTextSig = s.sign();
        s.initVerify(pubKey);
        s.update(knownText.getBytes());
        if (s.verify(knownTextSig) == false) {
            throw new Exception("Could not verify signature");
        }
        if (enableDebug) {
            System.out.println("Signature verified");
        }
    }

    public static void main(String[] args) throws Throwable {
        main(new LargeDSAKey());
        System.out.println("TEST PASS - OK");
    }

}
