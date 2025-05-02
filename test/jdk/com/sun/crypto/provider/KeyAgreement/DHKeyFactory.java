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
 * @bug 0000000
 * @library /test/lib
 * @summary DHKeyFactory
 * @author Jan Luehe
 */

import java.io.*;
import java.math.BigInteger;
import java.security.*;
import java.security.spec.*;
import java.security.interfaces.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import javax.crypto.interfaces.*;
import jdk.test.lib.security.DiffieHellmanGroup;
import jdk.test.lib.security.SecurityUtils;

/**
 * This test creates a DH keypair, retrieves the encodings of the DH public and
 * private key, and feeds those encodings to a DH key factory, which parses
 * the encodings and creates a DH public and private key from them.
 */

public class DHKeyFactory {

    private DHKeyFactory() {}

    public static void main(String argv[]) throws Exception {
            DHKeyFactory test = new DHKeyFactory();
            test.run();
            System.out.println("Test Passed");
    }

    private void run() throws Exception {

        DiffieHellmanGroup dhGroup = SecurityUtils.getTestDHGroup();
        DHParameterSpec dhParamSpec = new DHParameterSpec(dhGroup.getPrime(), dhGroup.getBase());
        System.out.println("Using " + dhGroup.name() + " Diffie-Hellman parameters");

        KeyPairGenerator kpgen = KeyPairGenerator.getInstance("DH",
                                    System.getProperty("test.provider.name", "SunJCE"));
        kpgen.initialize(dhParamSpec);
        KeyPair kp = kpgen.generateKeyPair();

        // get the public key encoding
        byte[] pubKeyEnc = kp.getPublic().getEncoded();

        // get the private key encoding
        byte[] privKeyEnc = kp.getPrivate().getEncoded();

        KeyFactory kfac = KeyFactory.getInstance("DH",
                                System.getProperty("test.provider.name", "SunJCE"));

        X509EncodedKeySpec x509KeySpec = new X509EncodedKeySpec(pubKeyEnc);
        PublicKey pubKey = kfac.generatePublic(x509KeySpec);

        PKCS8EncodedKeySpec pkcsKeySpec = new PKCS8EncodedKeySpec(privKeyEnc);
        PrivateKey privKey = kfac.generatePrivate(pkcsKeySpec);
    }
}
