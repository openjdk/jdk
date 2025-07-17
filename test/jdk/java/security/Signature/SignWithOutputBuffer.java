/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4114896
 * @summary Signature should support a sign() method that places the signature
 * in an already existing array.
 * @run main SignWithOutputBuffer DSS 512
 * @run main SignWithOutputBuffer SHA256withDSA 2048
 */

import java.security.*;

public class SignWithOutputBuffer {

    public static void main(String[] args) throws Exception {

        int numBytes;

        String kpgAlgorithm = "DSA";
        int keySize = Integer.parseInt(args[1]);
        KeyPairGenerator kpGen = KeyPairGenerator.getInstance(kpgAlgorithm);
        kpGen.initialize(keySize);
        KeyPair kp = kpGen.genKeyPair();

        String signAlgo = args[0];
        Signature sig = Signature.getInstance(signAlgo);
        sig.initSign(kp.getPrivate());
        sig.update((byte)0xff);

        // Allocate buffer for signature. According to BSAFE, the size of the
        // signature may be as many as 48 bytes.
        // First, let's allocate a buffer that's too short.
        byte[] out = new byte[10];
        try {
            numBytes = sig.sign(out, 0, out.length);
        } catch (SignatureException e) {
            System.out.println(e);
        }

        // Now repeat the same with a buffer that's big enough
        sig = Signature.getInstance(signAlgo);
        sig.initSign(kp.getPrivate());
        sig.update((byte)0xff);
        out = new byte[64];
        numBytes = sig.sign(out, 0, out.length);

        System.out.println("Signature len="+numBytes);
    }
}
