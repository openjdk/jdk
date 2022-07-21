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
 * @bug 8267319
 * @summary Ensure that DSA/RSA/DH/EC KPG in PKCS11 provider uses the
 *     same default key length
 * @library /test/lib ..
 * @modules java.base/sun.security.util
 *          jdk.crypto.cryptoki
 * @run main TestDefaultSize
 */

import java.security.InvalidParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Provider;
import java.security.PrivateKey;
import java.security.interfaces.*;
import javax.crypto.interfaces.DHKey;

import static sun.security.util.SecurityProviderConstants.*;

public class TestDefaultSize extends PKCS11Test {

    @Override
    public void main(Provider p) throws Exception {
        System.out.println("Testing " + p.getName());

        String[] ALGOS = { "DSA", "RSA", "DH", "EC" };

        for (String algo : ALGOS) {
            if (p.getService("KeyPairGenerator", algo) == null) {
                System.out.println("Skip, no support for KPG: " + algo);
                return;
            }

            KeyPairGenerator kpg = KeyPairGenerator.getInstance(algo, p);
            KeyPair kp = kpg.generateKeyPair();
            PrivateKey priv = kp.getPrivate();
            int actualSize = -1;
            int expectedSize;
            if (algo == "DSA") {
                expectedSize = DEF_DSA_KEY_SIZE;
                if (priv instanceof DSAKey) {
                    actualSize = ((DSAKey) priv).getParams().getP().bitLength();
                }
            } else if (algo == "RSA") {
                expectedSize = DEF_RSA_KEY_SIZE;
                if (priv instanceof RSAKey) {
                    actualSize = ((RSAKey) priv).getModulus().bitLength();
                }
            } else if (algo == "DH") {
                expectedSize = DEF_DH_KEY_SIZE;
                if (priv instanceof DHKey) {
                    actualSize = ((DHKey) priv).getParams().getP().bitLength();
                }
            } else if (algo == "EC") {
                expectedSize = DEF_EC_KEY_SIZE;
                if (priv instanceof ECKey) {
                    actualSize = ((ECKey) priv).getParams().getCurve()
                            .getField().getFieldSize();
                }
            } else {
                throw new RuntimeException("Error: Unrecognized algo " +
                    algo + " or opaque private key object " + priv);
            }
            if (actualSize != expectedSize) {
                throw new RuntimeException("key size check failed, got " +
                    actualSize);
            } else {
                System.out.println(algo + ": passed, " + actualSize);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        main(new TestDefaultSize(), args);
    }
}
