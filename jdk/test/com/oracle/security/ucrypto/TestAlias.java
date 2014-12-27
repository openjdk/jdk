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
 * @bug 8043349
 * @summary Ensure the cipher aliases of AES and RSA works correctly
 */
import java.io.*;
import java.security.*;
import java.security.spec.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;

public class TestAlias extends UcryptoTest {

    private static final String[] CIPHER_ALGOS = {
        "AES/ECB/PKCS5Padding",
        "AES",
        "RSA/ECB/PKCS1Padding",
        "RSA",
    };

    public static void main(String[] args) throws Exception {
        main(new TestAlias(), null);
    }

    public void doTest(Provider prov) throws Exception {
        Cipher c;
        for (int i = 0; i < (CIPHER_ALGOS.length - 1); i+=2) {
            String fullTransformation = CIPHER_ALGOS[i];
            try {
                c = Cipher.getInstance(fullTransformation, prov);
            } catch (NoSuchAlgorithmException nsae) {
                System.out.println("Skip unsupported algo: " + fullTransformation);
                continue;
            }
            c = Cipher.getInstance(CIPHER_ALGOS[i+1], prov);
        }

        System.out.println("Test Passed");
    }
}
