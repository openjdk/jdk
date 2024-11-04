/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @summary DHKeyGenSpeed
 * @author Jan Luehe
 */
import java.security.*;
import java.security.spec.*;
import java.security.interfaces.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.math.*;
import jdk.test.lib.security.DiffieHellmanGroup;
import jdk.test.lib.security.SecurityUtils;

public class DHKeyGenSpeed {

    public static void main(String[] args) throws Exception {
        DHKeyGenSpeed test = new DHKeyGenSpeed();
        test.run();
        System.out.println("Test Passed");
    }

    public void run() throws Exception {
        long start, end;

        DiffieHellmanGroup dhGroup = SecurityUtils.getTestDHGroup();
        BigInteger p = dhGroup.getPrime();
        BigInteger g = new BigInteger(1, dhGroup.getBase().toByteArray());
        int l = 576;

        DHParameterSpec spec =
            new DHParameterSpec(p, g, l);

        // generate keyPairs using parameters
        KeyPairGenerator keyGen =
            KeyPairGenerator.getInstance("DH",
                    System.getProperty("test.provider.name", "SunJCE"));
        start = System.currentTimeMillis();
        keyGen.initialize(spec);
        KeyPair keys = keyGen.generateKeyPair();
        end = System.currentTimeMillis();

        System.out.println("PrimeBits\tExponentBits");
        System.out.println(dhGroup.getPrime().bitLength() + "\t\t" + l);
        System.out.println("keyGen(millisecond): " + (end - start));
        System.out.println("Test Passed!");
    }
}
