/*
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8072452 8163498
 * @library /test/lib
 * @summary Support DHE sizes up to 8192-bits and DSA sizes up to 3072-bits
 *          This test has been split based on lower/higher key sizes in order to
 *          reduce individual execution times and run in parallel
 *          (see SupportedDHParamGensLongKey.java)
 * @run main/timeout=300 SupportedDHParamGens 512
 * @run main/timeout=300 SupportedDHParamGens 768
 * @run main/timeout=300 SupportedDHParamGens 832
 * @run main/timeout=300 SupportedDHParamGens 1024
 * @run main/timeout=600 SupportedDHParamGens 2048
 * @run main/timeout=600 SupportedDHParamGens 3072
 * @run main/timeout=600 SupportedDHParamGens 4096
 */
import java.math.BigInteger;

import java.security.*;
import javax.crypto.*;
import javax.crypto.interfaces.*;
import javax.crypto.spec.*;
import jdk.test.lib.security.DiffieHellmanGroup;
import jdk.test.lib.security.SecurityUtils;

public class SupportedDHParamGens {

    static DiffieHellmanGroup dhGroup2048 = SecurityUtils.getTestDHGroup(2048);
    static DiffieHellmanGroup dhGroup3072 = SecurityUtils.getTestDHGroup(3072);
    static DiffieHellmanGroup dhGroup4096 = SecurityUtils.getTestDHGroup(4096);
    static DHParameterSpec FFDHE2048_SPEC =  new DHParameterSpec(
            dhGroup2048.getPrime(), dhGroup2048.getBase());

    static DHParameterSpec FFDHE3072_SPEC =  new DHParameterSpec(
            dhGroup3072.getPrime(), dhGroup3072.getBase());

    static DHParameterSpec FFDHE4096_SPEC =  new DHParameterSpec(
            dhGroup4096.getPrime(), dhGroup4096.getBase());


    public static void main(String[] args) throws Exception {
        int primeSize = Integer.valueOf(args[0]).intValue();

        System.out.println("Checking " + primeSize + " ...");
        DHParameterSpec spec = null;
        switch (primeSize) {
            case 2048: spec = FFDHE2048_SPEC;
                break;
            case 3072: spec = FFDHE3072_SPEC;
                break;
            case 4096: spec = FFDHE4096_SPEC;
                break;
            default:
                AlgorithmParameterGenerator apg =
                        AlgorithmParameterGenerator.getInstance("DH", "SunJCE");
                apg.init(primeSize);
                AlgorithmParameters ap = apg.generateParameters();
                spec = ap.getParameterSpec(DHParameterSpec.class);
                break;
        }

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("DH", "SunJCE");
        kpg.initialize(spec);
        KeyPair kp = kpg.generateKeyPair();
        checkKeyPair(kp, primeSize);
    }

    private static void checkKeyPair(KeyPair kp, int pSize) throws Exception {

        DHPrivateKey privateKey = (DHPrivateKey)kp.getPrivate();
        BigInteger p = privateKey.getParams().getP();
        if (p.bitLength() != pSize) {
            throw new Exception(
                "Invalid modulus size: " + p.bitLength() + "/" + pSize);
        }

        if (!p.isProbablePrime(128)) {
            throw new Exception("Good luck, the modulus is composite!");
        }

        DHPublicKey publicKey = (DHPublicKey)kp.getPublic();
        p = publicKey.getParams().getP();
        if (p.bitLength() != pSize) {
            throw new Exception(
                "Invalid modulus size: " + p.bitLength() + "/" + pSize);
        }

        BigInteger leftOpen = BigInteger.ONE;
        BigInteger rightOpen = p.subtract(BigInteger.ONE);

        BigInteger x = privateKey.getX();
        if ((x.compareTo(leftOpen) <= 0) ||
                (x.compareTo(rightOpen) >= 0)) {
            throw new Exception(
                "X outside range [2, p - 2]:  x: " + x + " p: " + p);
        }

        BigInteger y = publicKey.getY();
        if ((y.compareTo(leftOpen) <= 0) ||
                (y.compareTo(rightOpen) >= 0)) {
            throw new Exception(
                "Y outside range [2, p - 2]:  x: " + x + " p: " + p);
        }
    }
}
