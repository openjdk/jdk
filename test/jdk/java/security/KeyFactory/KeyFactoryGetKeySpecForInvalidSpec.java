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

/**
 * @test
 * @bug 8254717
 * @summary isAssignableFrom checks in KeyFactorySpi.engineGetKeySpec appear to be backwards.
 * @author Greg Rubin, Ziyi Luo
 */

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.*;

public class KeyFactoryGetKeySpecForInvalidSpec {
    public static void main(String[] args) throws Exception {
        KeyPairGenerator kg = KeyPairGenerator.getInstance("RSA");
        kg.initialize(2048);
        KeyPair pair = kg.generateKeyPair();

        KeyFactory factory = KeyFactory.getInstance("RSA");

        // Since RSAPrivateCrtKeySpec inherits from RSAPrivateKeySpec, we'd expect this next line to return an instance of RSAPrivateKeySpec
        // (because the private key has CRT parts).
        KeySpec spec = factory.getKeySpec(pair.getPrivate(), RSAPrivateKeySpec.class);
        if (!(spec instanceof RSAPrivateCrtKeySpec)) {
            throw new Exception("Spec should be an instance of RSAPrivateCrtKeySpec");
        }

        // This next line should give an InvalidKeySpec exception
        try {
            spec = factory.getKeySpec(pair.getPublic(), FakeX509Spec.class);
            throw new Exception("InvalidKeySpecException is expected but not thrown");
        } catch (final ClassCastException ex) {
            throw new Exception("InvalidKeySpecException is expected ClassCastException is thrown", ex);
        } catch (final InvalidKeySpecException ex) {
            // Pass
        }
    }

    public static class FakeX509Spec extends X509EncodedKeySpec {
        public FakeX509Spec(byte[] encodedKey) {
            super(encodedKey);
        }

        public FakeX509Spec(byte[] encodedKey, String algorithm) {
            super(encodedKey, algorithm);
        }
    }
}
