/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8161571
 * @summary Reject signatures presented for verification that contain extra
 *          bytes.
 * @run main SignatureLength
 */
public class SignatureLength {

    public static void main(String[] args) throws Exception {
        main0("EC", 256, "SHA256withECDSA", "SunEC");
        main0("RSA", 2048, "SHA256withRSA", "SunRsaSign");
        main0("DSA", 2048, "SHA256withDSA", "SUN");

        if (System.getProperty("os.name").equals("SunOS")) {
            main0("EC", 256, "SHA256withECDSA", null);
            main0("RSA", 2048, "SHA256withRSA", null);
        }
    }

    private static void main0(String keyAlgorithm, int keysize,
            String signatureAlgorithm, String provider) throws Exception {
        byte[] plaintext = "aaa".getBytes("UTF-8");

        // Generate
        KeyPairGenerator generator =
            provider == null ?
                (KeyPairGenerator) KeyPairGenerator.getInstance(keyAlgorithm) :
                (KeyPairGenerator) KeyPairGenerator.getInstance(
                                       keyAlgorithm, provider);
        generator.initialize(keysize);
        System.out.println("Generating " + keyAlgorithm + " keypair using " +
            generator.getProvider().getName() + " JCE provider");
        KeyPair keypair = generator.generateKeyPair();

        // Sign
        Signature signer =
            provider == null ?
                Signature.getInstance(signatureAlgorithm) :
                Signature.getInstance(signatureAlgorithm, provider);
        signer.initSign(keypair.getPrivate());
        signer.update(plaintext);
        System.out.println("Signing using " + signer.getProvider().getName() +
            " JCE provider");
        byte[] signature = signer.sign();

        // Invalidate
        System.out.println("Invalidating signature ...");
        byte[] badSignature = new byte[signature.length + 5];
        System.arraycopy(signature, 0, badSignature, 0, signature.length);
        badSignature[signature.length] = 0x01;
        badSignature[signature.length + 1] = 0x01;
        badSignature[signature.length + 2] = 0x01;
        badSignature[signature.length + 3] = 0x01;
        badSignature[signature.length + 4] = 0x01;

        // Verify
        Signature verifier =
            provider == null ?
                Signature.getInstance(signatureAlgorithm) :
                Signature.getInstance(signatureAlgorithm, provider);
        verifier.initVerify(keypair.getPublic());
        verifier.update(plaintext);
        System.out.println("Verifying using " +
            verifier.getProvider().getName() + " JCE provider");

        try {
            System.out.println("Valid? " + verifier.verify(badSignature));
            throw new Exception(
                "ERROR: expected a SignatureException but none was thrown");
        } catch (SignatureException e) {
            System.out.println("OK: caught expected exception: " + e);
        }
        System.out.println();
    }
}
