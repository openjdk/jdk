/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8076359 8133151
 * @summary Test for jdk.security.provider.preferred security property
 * @requires os.name == "SunOS"
 * @run main/othervm  PreferredProviderNegativeTest preJCESet AES:OracleUcrypto false
 * @run main/othervm  PreferredProviderNegativeTest preJCESet AES:SunNegative true
 * @run main/othervm  PreferredProviderNegativeTest afterJCESet AES:SunJGSS
 * @run main/othervm  PreferredProviderNegativeTest afterJCESet AES:SunECNegative
 * @run main/othervm  PreferredProviderNegativeTest invalidAlg AESNegative:SunJCE
 */

import java.security.Security;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;

public class PreferredProviderNegativeTest {

    /*
     * Test security property could be set by valid and invalid provider
     * before JCE was loaded
     */
    public static void preJCESet(String value, boolean negativeProvider)
            throws NoSuchAlgorithmException, NoSuchPaddingException {
        Security.setProperty("jdk.security.provider.preferred", value);

        if (!Security.getProperty("jdk.security.provider.preferred")
                .equals(value)) {
            throw new RuntimeException(
                    "Test Failed:The property wasn't set");
        }

        String[] arrays = value.split(":");
        Cipher cipher = Cipher.getInstance(arrays[0]);

        if (negativeProvider) {
            if (cipher.getProvider().getName().equals(arrays[1])) {
                throw new RuntimeException(
                        "Test Failed:The provider shouldn't be set");
            }
        } else {
            if (!cipher.getProvider().getName().equals(arrays[1])) {
                throw new RuntimeException(
                        "Test Faild:The provider could be set "
                                + "by valid provider ");
            }
        }
        System.out.println("Test Pass");
    }

    /*
     * Test that the setting of the security property after Cipher.getInstance()
     * does not influence previously loaded instances
     */
    public static void afterJCESet(String value)
            throws NoSuchAlgorithmException, NoSuchPaddingException {
        String[] arrays = value.split(":");
        Cipher cipher = Cipher.getInstance(arrays[0]);

        Security.setProperty("jdk.security.provider.preferred", value);
        if (!cipher.getProvider().getName().equals("SunJCE")) {
            throw new RuntimeException(
                    "Test Failed:The security property can't be updated after JCE load.");
        }
        System.out.println("Test Pass");
    }

    /* Test the security property with negative algorithm */
    public static void invalidAlg(String value) throws NoSuchPaddingException {
        String[] arrays = value.split(":");

        try {
            Security.setProperty("jdk.security.provider.preferred", value);
            Cipher.getInstance(arrays[0]);
        } catch (NoSuchAlgorithmException e) {
            System.out.println("Test Pass:Got NoSuchAlgorithmException as expired");
            return;
        }
        throw new RuntimeException(
                "Test Failed:Expected NoSuchAlgorithmException was not thrown");
    }

    public static void main(String[] args)
            throws NoSuchAlgorithmException, NoSuchPaddingException {
        boolean negativeProvider;

        if (args.length >= 2) {
            switch (args[0]) {
                case "preJCESet":
                    negativeProvider = Boolean.valueOf(args[2]);
                    PreferredProviderNegativeTest.preJCESet(args[1], negativeProvider);
                    break;
                case "afterJCESet":
                    PreferredProviderNegativeTest.afterJCESet(args[1]);
                    break;
                case "invalidAlg":
                    PreferredProviderNegativeTest.invalidAlg(args[1]);
                    break;
            }
        } else {
            throw new RuntimeException(
                    "Test Failed:Please pass the correct args");
        }
    }
}

