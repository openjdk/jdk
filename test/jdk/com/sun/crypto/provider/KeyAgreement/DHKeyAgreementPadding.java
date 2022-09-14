/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8281628
 * @library /test/lib
 * @summary ensure padding bytes are always added when generated secret
 *      is smaller than buffer size.
 */

import javax.crypto.KeyAgreement;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Arrays;
import java.util.HexFormat;

public class DHKeyAgreementPadding {

    public static void main(String[] args) throws Exception {

        byte[] aliceSecret = new byte[80];
        byte[] bobSecret = new byte[80];

        KeyAgreement alice = KeyAgreement.getInstance("DiffieHellman");
        KeyAgreement bob = KeyAgreement.getInstance("DiffieHellman");

        // The probability of an error is 0.2% or 1/500. Try more times.
        for (int i = 0; i < 5000; i++) {
            KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("DiffieHellman");
            keyPairGen.initialize(512);
            KeyPair aliceKeyPair = keyPairGen.generateKeyPair();
            KeyPair bobKeyPair = keyPairGen.generateKeyPair();

            // Different stale data
            Arrays.fill(aliceSecret, (byte)'a');
            Arrays.fill(bobSecret, (byte)'b');

            alice.init(aliceKeyPair.getPrivate());
            alice.doPhase(bobKeyPair.getPublic(), true);
            int aliceLen = alice.generateSecret(aliceSecret, 0);

            bob.init(bobKeyPair.getPrivate());
            bob.doPhase(aliceKeyPair.getPublic(), true);
            int bobLen = bob.generateSecret(bobSecret, 0);

            if (!Arrays.equals(aliceSecret, 0, aliceLen, bobSecret, 0, bobLen)) {
                System.out.println(HexFormat.ofDelimiter(":").formatHex(aliceSecret, 0, aliceLen));
                System.out.println(HexFormat.ofDelimiter(":").formatHex(bobSecret, 0, bobLen));
                throw new RuntimeException("Different secrets observed at runs #" + i);
            }
        }
    }
}
