/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2025 IBM Corporation. All rights reserved.
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
 * @bug 8359388
 * @summary test that the Cipher.getInstance() would reject improper
 *     transformations with empty mode and/or padding.
 */

import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import javax.crypto.Cipher;

public class TestEmptyModePadding {

    public static void main(String[] args) throws Exception {
        Provider provider = Security.getProvider(
                System.getProperty("test.provider.name", "SunJCE"));

        System.out.println("Testing against " + provider.getName());

        String[] testTransformations = {
            // transformations w/ only 1 component, i.e. algo
            " ",
            // transformations w/ only 2 components
            "AES/",
            "AES/ ",
            "AES/CBC",
            "PBEWithHmacSHA512/224AndAES_128/",
            "PBEWithHmacSHA512/256AndAES_128/ ",
            "PBEWithHmacSHA512/224AndAES_128/CBC",
            // 3-component transformations w/ empty component(s)
            "AES//",
            "AES/ /",
            "AES// ",
            "AES/ / ",
            "AES/CBC/", "AES/CBC/ ",
            "AES//PKCS5Padding", "AES/ /NoPadding",
            "PBEWithHmacSHA512/224AndAES_128//",
            "PBEWithHmacSHA512/224AndAES_128/ /",
            "PBEWithHmacSHA512/224AndAES_128// ",
            "PBEWithHmacSHA512/224AndAES_128/ / ",
            "PBEWithHmacSHA512/256AndAES_128/CBC/",
            "PBEWithHmacSHA512/256AndAES_128/CBC/ ",
            "PBEWithHmacSHA512/256AndAES_128//PKCS5Padding",
            "PBEWithHmacSHA512/256AndAES_128/ /PKCS5Padding",
        };

        for (String t : testTransformations) {
            test(t, provider);
        }
    }

    private static void test(String t, Provider p) throws Exception {
        try {
            Cipher c = Cipher.getInstance(t, p);
            throw new RuntimeException("Should throw NSAE for \'" + t + "\'");
        } catch (NoSuchAlgorithmException nsae) {
            // transformation info is already in the NSAE message
            System.out.println("Expected NSAE: " + nsae.getMessage());
        }
    }
}
