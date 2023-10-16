/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.security.KeyPairGenerator;
import java.security.PublicKey;

/*
 * @test
 * @bug 8308808
 * @requires os.family == "windows"
 * @modules jdk.crypto.mscapi
 * @run main EncodingMutability
 */
public class EncodingMutability {

    public static void main(String[] args) throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA", "SunMSCAPI");
        PublicKey publicKey = keyGen.generateKeyPair().getPublic();
        byte initialByte = publicKey.getEncoded()[0];
        publicKey.getEncoded()[0] = 0;
        byte mutatedByte = publicKey.getEncoded()[0];

        if (initialByte != mutatedByte) {
            System.out.println("Was able to mutate first byte of pubkey from " + initialByte + " to " + mutatedByte);
            throw new RuntimeException("Pubkey was mutated via getEncoded");
        }
    }
}