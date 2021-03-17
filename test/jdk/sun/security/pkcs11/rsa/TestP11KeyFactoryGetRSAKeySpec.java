/*
 * Copyright (c) 2021, Amazon.com, Inc. or its affiliates. All rights reserved.
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

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Provider;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.*;

/**
 * @test
 * @bug 8263404
 * @summary RsaPrivateKeySpec is always recognized as RSAPrivateCrtKeySpec in RSAKeyFactory.engineGetKeySpec
 * @author Greg Rubin, Ziyi Luo
 * @library /test/lib ..
 * @run main/othervm TestP11KeyFactoryGetRSAKeySpec
 * @run main/othervm TestP11KeyFactoryGetRSAKeySpec sm rsakeys.ks.policy
 * @modules jdk.crypto.cryptoki
 */

public class TestP11KeyFactoryGetRSAKeySpec extends PKCS11Test {
    public static void main(String[] args) throws Exception {
        main(new TestP11KeyFactoryGetRSAKeySpec(), args);
    }

    @Override
    public void main(Provider p) throws Exception {
        KeyPairGenerator kg = KeyPairGenerator.getInstance("RSA", p);
        kg.initialize(2048);
        KeyPair pair = kg.generateKeyPair();

        KeyFactory factory = KeyFactory.getInstance("RSA", p);

        // === Case 1: private key is RSAPrivateCrtKey, keySpec is RSAPrivateKeySpec
        // === Expected: return RSAPrivateCrtKeySpec
        // Since RSAPrivateCrtKeySpec inherits from RSAPrivateKeySpec, we'd expect this next line to return an instance of RSAPrivateKeySpec
        // (because the private key has CRT parts).
        KeySpec spec = factory.getKeySpec(pair.getPrivate(), RSAPrivateKeySpec.class);
        if (!(spec instanceof RSAPrivateCrtKeySpec)) {
            throw new Exception("Spec should be an instance of RSAPrivateCrtKeySpec");
        }

        // === Case 2: private key is RSAPrivateCrtKey, keySpec is RSAPrivateCrtKeySpec
        // === Expected: return RSAPrivateCrtKeySpec
        spec = factory.getKeySpec(pair.getPrivate(), RSAPrivateCrtKeySpec.class);
        if (!(spec instanceof RSAPrivateCrtKeySpec)) {
            throw new Exception("Spec should be an instance of RSAPrivateCrtKeySpec");
        }
    }
}
