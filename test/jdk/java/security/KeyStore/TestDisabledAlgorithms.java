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
 * @library /test/lib
 * @run main/othervm TestDisabledAlgorithms KEYSTORE.JKs true
 * @run main/othervm TestDisabledAlgorithms keySTORE.what false
 * @run main/othervm TestDisabledAlgorithms kEYstoRe.jceKS false
 * @run main/othervm -Djdk.crypto.disabledAlgorithms="keystore.jkS" TestDisabledAlgorithms keySTORE.jceKs true
 * @run main/othervm -Djdk.crypto.disabledAlgorithms="KEYstORE.what" TestDisabledAlgorithms KeYStore.JKs false
 * @run main/othervm -Djdk.crypto.disabledAlgorithms="keystOre.jceKS" TestDisabledAlgorithms KEysTORE.JKS false
 */
import java.io.File;
import java.util.List;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.Provider;
import java.security.Security;
import jdk.test.lib.Utils;

public class TestDisabledAlgorithms {

    private static final String PROP_NAME = "jdk.crypto.disabledAlgorithms";

    // reuse existing JKS test keystore
    private final static String DIR = System.getProperty("test.src", ".");
    private static final char[] PASSWD = "passphrase".toCharArray();
    private static final String JKS_FN = "keystore.jks";

    private static void test(List<String> algos, Provider p,
            boolean shouldThrow) throws Exception {

        for (String a : algos) {
            System.out.println("Testing " + (p != null ? p.getName() : "") +
                    ": " + a + ", shouldThrow=" + shouldThrow);
            if (shouldThrow) {
                if (p == null) {
                    Utils.runAndCheckException(() -> KeyStore.getInstance(a),
                            KeyStoreException.class);
                    Utils.runAndCheckException(
                            () -> KeyStore.getInstance(new File(DIR, JKS_FN),
                                PASSWD),
                            KeyStoreException.class);
                    Utils.runAndCheckException(
                            () -> KeyStore.getInstance(new File(DIR, JKS_FN),
                                () -> {
                                    return new KeyStore.PasswordProtection(PASSWD);
                                }),
                            KeyStoreException.class);
                } else {
                    // with a provider argument
                    Utils.runAndCheckException(() -> KeyStore.getInstance(a, p),
                            KeyStoreException.class);
                    Utils.runAndCheckException(() -> KeyStore.getInstance(a,
                            p.getName()), KeyStoreException.class);
                }
            } else {
                KeyStore k;
                if (p == null) {
                    k = KeyStore.getInstance(a);
                    System.out.println("Got KeyStore w/ algo " + k.getType());
                    k = KeyStore.getInstance(new File(DIR, JKS_FN), PASSWD);
                    System.out.println("Got KeyStore w/ algo " + k.getType());
                    k = KeyStore.getInstance(new File(DIR, JKS_FN),
                        () -> {
                            return new KeyStore.PasswordProtection(PASSWD);
                        });
                    System.out.println("Got KeyStore w/ algo " + k.getType());
                } else {
                    // with a provider argument
                    k = KeyStore.getInstance(a, p);
                    k = KeyStore.getInstance(a, p.getName());
                    System.out.println("Got KeyStore w/ algo " + k.getType());
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
