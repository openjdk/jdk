/*
 * Copyright (c) 2003, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4898428 8368984
 * @summary test that the new getInstance() implementation works correctly
 * @author Andreas Sterbenz
 * @library /test/lib
 * @run main TestGetInstance DES PBEWithMD5AndTripleDES
 * @run main TestGetInstance AES PBEWithHmacSHA1AndAES_128
 */

import java.security.*;
import java.security.spec.*;
import java.util.Locale;

import javax.crypto.*;
import jdk.test.lib.Utils;

public class TestGetInstance {

    private static void same(Provider p1, Provider p2) throws Exception {
        if (p1 != p2) {
           throw new Exception("not same object");
        }
    }

    public static void main(String[] args) throws Exception {
        String algo = args[0];
        String algoLC = algo.toLowerCase(Locale.ROOT);
        String pbeAlgo = args[1];
        String pName = System.getProperty("test.provider.name", "SunJCE");
        Provider p = Security.getProvider(pName);

        Cipher c;

        c = Cipher.getInstance(pbeAlgo);
        same(p, c.getProvider());

        c = Cipher.getInstance(algoLC,
                System.getProperty("test.provider.name", "SunJCE"));
        same(p, c.getProvider());
        c = Cipher.getInstance(algoLC + "/cbc/pkcs5padding",
                System.getProperty("test.provider.name", "SunJCE"));
        same(p, c.getProvider());

        c = Cipher.getInstance(algoLC, p);
        same(p, c.getProvider());
        c = Cipher.getInstance(algoLC + "/cbc/pkcs5padding", p);
        same(p, c.getProvider());

        // invalid transformations or transformations containing unsupported
        // modes which should lead to NSAE
        String[] nsaeTransformations = {
            (algo + "/XYZ/PKCS5Padding"),
            (algo + "/CBC/XYZWithSHA512/224Padding/"),
            (algo + "/CBC/XYZWithSHA512/256Padding/"),
            (pbeAlgo + "/CBC/XYZWithSHA512/224Padding/"),
            (pbeAlgo + "/CBC/XYZWithSHA512/256Padding/"),
            "foo",
        };

        for (String t : nsaeTransformations) {
            System.out.println("Testing NSAE on " + t);
            Utils.runAndCheckException(() -> Cipher.getInstance(t),
                    NoSuchAlgorithmException.class);
            Utils.runAndCheckException(() -> Cipher.getInstance(t, pName),
                    NoSuchAlgorithmException.class);
            Utils.runAndCheckException(() -> Cipher.getInstance(t, p),
                    NoSuchAlgorithmException.class);
        }

        // transformations containing unsupported paddings for SunJCE provider
        // which should lead to NSPE
        String[] nspeTransformations = {
            (algo + "/CBC/XYZPadding"),
            (algo + "/CBC/XYZWithSHA512/224Padding"),
            (algo + "/CBC/XYZWithSHA512/256Padding"),
            (pbeAlgo + "/CBC/XYZWithSHA512/224Padding"),
            (pbeAlgo + "/CBC/XYZWithSHA512/256Padding"),
        };

        for (String t : nspeTransformations) {
            System.out.println("Testing NSPE on " + t);
            Utils.runAndCheckException(() -> Cipher.getInstance(t, pName),
                    NoSuchPaddingException.class);
            Utils.runAndCheckException(() -> Cipher.getInstance(t, p),
                    NoSuchPaddingException.class);
        }

        // additional misc tests
        Utils.runAndCheckException(() -> Cipher.getInstance("foo",
                System.getProperty("test.provider.name", "SUN")),
                NoSuchAlgorithmException.class);
        Utils.runAndCheckException(() -> Cipher.getInstance("foo",
                Security.getProvider(System.getProperty("test.provider.name",
                "SUN"))), NoSuchAlgorithmException.class);
        Utils.runAndCheckException(() -> Cipher.getInstance("foo", "bar"),
                NoSuchProviderException.class);

        System.out.println("All Tests ok");
    }
}
