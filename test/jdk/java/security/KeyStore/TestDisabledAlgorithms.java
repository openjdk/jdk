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

/**
 * @test
 * @bug 8244336
 * @summary Test JCE layer algorithm restriction
 * @run main/othervm TestDisabledAlgorithms KeyStore.JKs true
 * @run main/othervm TestDisabledAlgorithms what false
 * @run main/othervm TestDisabledAlgorithms KeyStore.jceKS false
 */
import java.io.File;
import java.util.List;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.Provider;
import java.security.Security;

public class TestDisabledAlgorithms {

    private static final String PROP_NAME = "jdk.crypto.disabledAlgorithms";

    // reuse existing JKS test keystore
    private final static String DIR = System.getProperty("test.src", ".");
    private static final char[] PASSWORD = "passphrase".toCharArray();
    private static final String KEYSTORE = DIR + "/keystore.jks";

    private static void test(List<String> algos, Provider p,
            boolean shouldThrow) throws Exception {

        for (String a : algos) {
            System.out.println("Testing " + (p != null ? p.getName() : "") +
                    ": " + a + ", shouldThrow=" + shouldThrow);
            KeyStore k;
            if (p == null) {
                try {
                    k = KeyStore.getInstance(a);
                    if (shouldThrow) {
                        throw new RuntimeException("Expected ex not thrown");
                    }
                } catch (KeyStoreException e) {
                    if (!shouldThrow) {
                        throw new RuntimeException("Unexpected ex", e);
                    }
                }
                try {
                    k = KeyStore.getInstance(new File(KEYSTORE), PASSWORD);
                    System.out.println("Got KeyStore obj w/ algo " + k.getType());
                    if (shouldThrow) {
                        throw new RuntimeException("Expected ex not thrown");
                    }
                } catch (KeyStoreException e) {
                    if (!shouldThrow) {
                        throw new RuntimeException("Unexpected ex", e);
                    }
                }
                try {
                    k = KeyStore.getInstance(new File(KEYSTORE),
                            ()-> {
                                return new KeyStore.PasswordProtection(PASSWORD);
                            });
                    System.out.println("Got KeyStore obj w/ algo " + k.getType());
                    if (shouldThrow) {
                        throw new RuntimeException("Expected ex not thrown");
                    }
                } catch (KeyStoreException e) {
                    if (!shouldThrow) {
                        throw new RuntimeException("Unexpected ex", e);
                    }
                }
            } else {
                try {
                    k = KeyStore.getInstance(a, p);
                    System.out.println("Got KeyStore obj w/ algo " + k.getType());
                    if (shouldThrow) {
                        throw new RuntimeException("Expected ex not thrown");
                    }
                } catch (KeyStoreException e) {
                    if (!shouldThrow) {
                        throw new RuntimeException("Unexpected ex", e);
                    }
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        String propValue = args[0];
        System.out.println("Setting Security Prop " + PROP_NAME + " = " +
                propValue);
        Security.setProperty(PROP_NAME, propValue);

        boolean shouldThrow = Boolean.valueOf(args[1]);

        List<String> algos = List.of("JKS", "jkS");
        // test w/o provider
        test(algos, null, shouldThrow);

        // test w/ provider
        Provider[] providers = Security.getProviders("KeyStore.JKS");
        for (Provider p : providers) {
            test(algos, p, shouldThrow);
        }
    }
}
