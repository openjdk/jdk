/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.ByteBuffer;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Security;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;

import jtreg.SkippedException;

/*
 * @test
 * @bug 8385672
 * @summary This test validates the length checks in SunEC's NONEwithECDSA
 *         implementation
 * @library /test/lib/
 */

public class NONEwithECDSAOffsetTest {

    private static Signature s;
    private static PrivateKey pk;

    public static void main(String[] args) throws Exception {
        Provider prov = Security.getProvider("SunEC");
        if (prov == null) {
            throw new SkippedException("Skip test - no SunEC provider found");
        }
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC", prov);
        kpg.initialize(new ECGenParameterSpec("secp521r1"));
        pk = kpg.generateKeyPair().getPrivate();
        s = Signature.getInstance("NONEwithECDSA", prov);

        test(48, true);
        test(64, true);
        test(65, false);
    }

    private static void test(int dataLen, boolean shouldPass) throws Exception {
        System.out.println("Testing " + dataLen + ", shouldPass = " +
                shouldPass);
        byte[] data = new byte[dataLen];
        Arrays.fill(data, (byte) dataLen);

        int testNum = 1;
        boolean done = false;
        while (!done) {
            String testId = String.format("Test#%d", testNum);
            s.initSign(pk);
            try {
                switch (testNum++) {
                    case 1: // update(byte)
                        byte i = 0;
                        while (i++ < dataLen) {
                            s.update(i);
                        }
                        break;
                    case 2: // update(byte[])
                        s.update(data);
                        break;
                    case 3: // update(byte[], int, int)
                        int firstPart = data.length/2;
                        s.update(data, 0, firstPart);
                        s.update(data, firstPart, data.length - firstPart);
                        break;
                    case 4: // update(ByteBuffer)
                        s.update(ByteBuffer.wrap(data));
                        done = true;
                        break;
                    default:
                        throw new AssertionError("Error: Unsupported testNum"
                                + testNum);
                }
                s.sign();
                if (!shouldPass) {
                    done = true;
                    throw new AssertionError(testId +
                            " should throw SignatureException");
                }
            } catch (SignatureException se) {
                if (shouldPass) {
                    done = true;
                    throw new AssertionError(testId +
                            ": Unexpected SignatureException", se);
                }
            }
        }
    }
}
