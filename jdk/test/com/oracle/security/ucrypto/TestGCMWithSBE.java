/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8036970
 * @summary Ensure that Cipher object is still usable after SBE.
 * @author Valerie Peng
 */

import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.math.*;
import com.sun.crypto.provider.*;

import java.util.*;

public class TestGCMWithSBE extends UcryptoTest {

    private static final byte[] PT = new byte[32];
    private static final byte[] ONE_BYTE = new byte[1];

    public static void main(String[] args) throws Exception {
        main(new TestGCMWithSBE(), null);
    }

    public void doTest(Provider p) throws Exception {
        Cipher c;
        try {
            c = Cipher.getInstance("AES/GCM/NoPadding", p);
        } catch (NoSuchAlgorithmException nsae) {
            System.out.println("Skipping Test due to No GCM support");
            return;
        }

        SecretKey key = new SecretKeySpec(new byte[16], "AES");
        c.init(Cipher.ENCRYPT_MODE, key);

        // test SBE with update calls
        byte[] ct1 = null;
        try {
            c.update(PT, 0, PT.length, ONE_BYTE);
        } catch (ShortBufferException sbe) {
            // retry should work
            ct1 = c.update(PT, 0, PT.length);
        }

        byte[] ct2PlusTag = null;
        // test SBE with doFinal calls
        try {
            c.doFinal(ONE_BYTE, 0);
        } catch (ShortBufferException sbe) {
            // retry should work
            ct2PlusTag = c.doFinal();
        }

        // Validate the retrieved parameters against the IV and tag length.
        AlgorithmParameters params = c.getParameters();
        if (params == null) {
            throw new Exception("getParameters() should not return null");
        }
        GCMParameterSpec spec = params.getParameterSpec(GCMParameterSpec.class);
        if (spec.getTLen() != (ct1.length + ct2PlusTag.length - PT.length)*8) {
            throw new Exception("Parameters contains incorrect TLen value");
        }
        if (!Arrays.equals(spec.getIV(), c.getIV())) {
            throw new Exception("Parameters contains incorrect IV value");
        }

        // Should be ok to use the same key+iv for decryption
        c.init(Cipher.DECRYPT_MODE, key, params);
        byte[] pt1 = c.update(ct1);
        if (pt1 != null && pt1.length != 0) {
            throw new Exception("Recovered text should not be returned "
                + "to caller before tag verification");
        }

        byte[] pt2 = null;
        try {
            c.doFinal(ct2PlusTag, 0, ct2PlusTag.length, ONE_BYTE);
        } catch (ShortBufferException sbe) {
            // retry should work
            pt2 = c.doFinal(ct2PlusTag);
        }
        if (!Arrays.equals(pt2, PT)) {
            throw new Exception("decryption result mismatch");
        }

        System.out.println("Test Passed!");
    }
}

