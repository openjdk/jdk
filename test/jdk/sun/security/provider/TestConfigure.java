/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8382442
 * @summary test the SUN provider no_crypto configuration option
 * @library /test/lib
 * @run main/othervm TestConfigure no_crypto
 * @run main/othervm -Djava.security.properties==${test.src}/noCrypto.txt TestConfigure
 */
import jdk.test.lib.Asserts;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.ProviderException;
import java.security.Security;
import java.security.Signature;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertPathValidator;

public class TestConfigure {

    private static Provider SUN_NO_CRYPTO;
    private static String PROV_NAME = "SUN";

    private static void test(String service, String algo)
            throws Exception {
        Object[] objs = {
            PROV_NAME, Security.getProvider(PROV_NAME), SUN_NO_CRYPTO
        };
        for (Object o : objs) {
            switch (service) {
                case "CertPathBuilder"->{
                    if (o instanceof String s) {
                        CertPathBuilder.getInstance(algo, s);
                    } else if (o instanceof Provider p) {
                        System.out.println("provider info: " + p.getInfo());
                        CertPathBuilder.getInstance(algo, p);
                    } else {
                        throw new RuntimeException
                                ("Error: unsupported type "  + o);
                    }
                }
                case "CertPathValidator"->{
                    if (o instanceof String s) {
                        CertPathValidator.getInstance(algo, s);
                    } else if (o instanceof Provider p) {
                        CertPathValidator.getInstance(algo, p);
                    } else {
                        throw new RuntimeException
                                ("Error: unsupported type "  + o);
                    }
                }
                case "MessageDigest"->{
                    if (o instanceof String s) {
                        Asserts.assertThrows(NoSuchAlgorithmException.class,
                                () -> MessageDigest.getInstance(algo, s));
                    } else if (o instanceof Provider p) {
                        Asserts.assertThrows(NoSuchAlgorithmException.class,
                                () -> MessageDigest.getInstance(algo, p));
                    } else {
                        throw new RuntimeException
                                ("Error: unsupported type "  + o);
                    }
                }
                case "Signature"->{
                    if (o instanceof String s) {
                        Asserts.assertThrows(NoSuchAlgorithmException.class,
                                () -> Signature.getInstance(algo, s));
                    } else if (o instanceof Provider p) {
                        Asserts.assertThrows(NoSuchAlgorithmException.class,
                                () -> Signature.getInstance(algo, p));
                    } else {
                        throw new RuntimeException
                                ("Error: unsupported type "  + o);
                    }
                }
                default->{
                    throw new RuntimeException("Unsupported service: " +
                            service);
                }
            }
        }
    }

    public static void main(String[] argv) throws Exception {
        if (argv.length > 0) {
            System.out.println("Testing runtime configuration " + argv[0]);
            // configure SUN provider w/ the supplied option
            Provider orig = Security.getProvider("SUN");
            Asserts.assertEQ(orig.getInfo().indexOf("no_crypto"), -1);
            SUN_NO_CRYPTO = orig.configure(argv[0]);
            Asserts.assertGTE(SUN_NO_CRYPTO.getInfo().indexOf("no_crypto"),
                    PROV_NAME.length());
            Asserts.assertThrows(ProviderException.class,
                        () -> orig.configure("xyz"));
        } else {
            System.out.println("Testing static configuration w/ " +
                     System.getProperty("java.security.properties"));
            SUN_NO_CRYPTO = Security.getProvider("SUN");
            Asserts.assertGTE(SUN_NO_CRYPTO.getInfo().indexOf("no_crypto"),
                    PROV_NAME.length());
        }

        // crypto services should now throw NSAE
        test("MessageDigest", "SHA-224");
        test("Signature", "SHA224withDSA");
        // make sure no change on non-crypto services
        test("CertPathBuilder", "PKIX");
        test("CertPathValidator", "PKIX");
    }
}
