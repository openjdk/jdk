/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8258229
 * @summary If a method is made not entrant while printing the assembly, hotspot crashes due to mismatched relocation information.
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:-TieredCompilation -XX:+DeoptimizeALot
 *                   -XX:CompileCommand=print,java/math/BitSieve.bit compiler.print.TestPrintAssemblyDeoptRace
 */

package compiler.print;

import java.util.Arrays;

import java.security.Security;
import java.security.Provider;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.MGF1ParameterSpec;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

public class TestPrintAssemblyDeoptRace {
    private static RSAPrivateKey privateKey;
    private static RSAPublicKey publicKey;
    static Provider cp;

    public static void main(String args[]) throws Exception {
        cp = Security.getProvider("SunJCE");
        System.out.println("Testing provider " + cp.getName() + "...");
        Provider kfp = Security.getProvider("SunRsaSign");
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", kfp);
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        privateKey = (RSAPrivateKey) kp.getPrivate();
        publicKey = (RSAPublicKey) kp.getPublic();
        testEncryptDecrypt(new OAEPParameterSpec("SHA-512/256", "MGF1",
                MGF1ParameterSpec.SHA512, PSource.PSpecified.DEFAULT), 190);

    }

    private static void testEncryptDecrypt(OAEPParameterSpec spec,
            int dataLength) throws Exception {

        Cipher c = Cipher.getInstance("RSA/ECB/OAEPPadding", cp);
        c.init(Cipher.ENCRYPT_MODE, publicKey, spec);

        byte[] data = new byte[dataLength];
        byte[] enc = c.doFinal(data);
        c.init(Cipher.DECRYPT_MODE, privateKey, spec);
    }
}
