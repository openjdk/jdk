/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8396832
 * @summary Commit signature key state only after successful validation
 * @run main FailedInitPreservesKey
 */

import java.security.*;
import java.security.spec.*;
import java.util.Arrays;

public class FailedInitPreservesKey {

    private static class RawEncodedKeySpec extends EncodedKeySpec {
        RawEncodedKeySpec(byte[] encoded) {
            super(encoded);
        }

        @Override
        public String getFormat() {
            return "RAW";
        }
    }

    public static void main(String[] args) throws Exception {
        var pair = KeyPairGenerator.getInstance("ML-DSA-44").generateKeyPair();
        var factory = KeyFactory.getInstance("ML-DSA-44");
        byte[] raw = factory.getKeySpec(pair.getPublic(), EncodedKeySpec.class).getEncoded();
        byte[] message = {1, 2, 3};
        var signer = Signature.getInstance("ML-DSA-44");
        signer.initSign(pair.getPrivate()); signer.update(message);
        byte[] signature = signer.sign();

        for (int length : new int[] {raw.length - 1, raw.length + 1}) {
            var verifier = Signature.getInstance("ML-DSA-44");
            verifier.initVerify(pair.getPublic());
            verifier.update(message, 0, 1);
            var bad = factory.generatePublic(new RawEncodedKeySpec(Arrays.copyOf(raw, length)));

            try {
                verifier.initVerify(bad);
                throw new AssertionError("Invalid key accepted");
            } catch (InvalidKeyException expected) { }

            verifier.update(message, 1, 2);

            if (!verifier.verify(signature)) throw new AssertionError("Previous key or message was lost");
        }
    }
}
