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
 * @bug 8358159 8359388
 * @summary test that the Cipher.getInstance() would reject improper
 *     transformations with empty mode and/or padding.
 * @run main TestEmptyModePadding
 */


import java.security.*;
import javax.crypto.*;

public class TestEmptyModePadding {

    public static void main(String[] args) throws Exception {
        Provider provider = Security.getProvider(
                System.getProperty("test.provider.name", "SunJCE"));

        // transformations w/ only 2 components
        test("AES/", provider);
        test("AES/ ", provider);
        test("AES/CBC", provider);
        test("PBEWithHmacSHA512/224AndAES_128/", provider);
        test("PBEWithHmacSHA512/256AndAES_128/ ", provider);
        test("PBEWithHmacSHA512/224AndAES_128/CBC", provider);

        // 3-component transformations w/ empty component(s)
        test("AES//", provider);
        test("AES/ /", provider);
        test("AES// ", provider);
        test("AES/ / ", provider);
        test("AES/CBC/", provider);
        test("AES/CBC/ ", provider);
        test("AES//PKCS5Padding", provider);
        test("AES/ /NoPadding", provider);
        test("PBEWithHmacSHA512/224AndAES_128//", provider);
        test("PBEWithHmacSHA512/224AndAES_128/ /", provider);
        test("PBEWithHmacSHA512/224AndAES_128// ", provider);
        test("PBEWithHmacSHA512/224AndAES_128/ / ", provider);
        test("PBEWithHmacSHA512/256AndAES_128/CBC/", provider);
        test("PBEWithHmacSHA512/256AndAES_128/CBC/ ", provider);
        test("PBEWithHmacSHA512/256AndAES_128//PKCS5Padding", provider);
        test("PBEWithHmacSHA512/256AndAES_128/ /PKCS5Padding", provider);
    }

    private static void test(String transformation, Provider provider)
            throws Exception {
        System.out.println("Testing " + transformation);
        try {
            Cipher c = Cipher.getInstance(transformation, provider);
            throw new RuntimeException("Expected NSAE not thrown");
        } catch (NoSuchAlgorithmException nsae) {
            System.out.println("Expected NSAE thrown: " + nsae.getMessage());
        }
    }
}
