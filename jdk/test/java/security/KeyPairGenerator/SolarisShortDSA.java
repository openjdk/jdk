/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7081411
 * @summary DSA keypair generation affected by Solaris bug
 * @modules java.base/sun.security.provider
 */

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import sun.security.provider.DSAPrivateKey;

public class SolarisShortDSA {
    static byte[] data = new byte[0];
    public static void main(String args[]) throws Exception {
        for (int i=0; i<10000; i++) {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("DSA");
            KeyPair kp = kpg.generateKeyPair();
            DSAPrivateKey dpk = (DSAPrivateKey)kp.getPrivate();
            int len = dpk.getX().bitLength();
            if (len <= 152) {
                if (!use(kp)) {
                    String os = System.getProperty("os.name");
                    // Solaris bug, update the following line once it's fixed
                    if (os.equals("SunOS")) {
                        throw new IllegalStateException(
                                "Don't panic. This is a Solaris bug");
                    } else {
                        throw new RuntimeException("Real test failure");
                    }
                }
                break;
            }
        }
    }

    static boolean use(KeyPair kp) throws Exception {
        Signature sig = Signature.getInstance("SHA1withDSA");
        sig.initSign(kp.getPrivate());
        sig.update(data);
        byte[] signed = sig.sign();
        Signature sig2 = Signature.getInstance("SHA1withDSA");
        sig2.initVerify(kp.getPublic());
        sig2.update(data);
        return sig2.verify(signed);
   }
}
