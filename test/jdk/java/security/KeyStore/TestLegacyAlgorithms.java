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
 * @bug 8376748
 * @summary Test JCE layer legacy algorithm warning
 * @library /test/lib
 * @run main/othervm TestLegacyAlgorithms KEYSTORE.JKs true
 * @run main/othervm TestLegacyAlgorithms keySTORE.what false
 * @run main/othervm TestLegacyAlgorithms kEYstoRe.jceKS false
 */

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.Provider;
import java.security.Security;
import java.util.List;

import jdk.test.lib.Asserts;

public class TestLegacyAlgorithms {

    private static final String PROP_NAME = "jdk.crypto.legacyAlgorithms";

    private static final String DIR = System.getProperty("test.src", ".");
    private static final char[] PASSWD = "passphrase".toCharArray();
    private static final String JKS_FN = "keystore.jks";

    private static final List<String> ALG_LIST =
            List.of("JKS", "jkS");

    private static String saveWarn(ThrowingRunnable action) throws Exception {
        PrintStream origErr = System.err;
        ByteArrayOutputStream bOut = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(bOut, true, StandardCharsets.UTF_8);
        try {
            System.setErr(ps);
            action.run();
        } finally {
            ps.flush();
            System.setErr(origErr);
        }
        return bOut.toString(StandardCharsets.UTF_8);
    }

    private static void test(List<String> algos, Provider p) throws Exception {
        for (String a : algos) {
            System.out.println("Testing " + (p != null ?
                    "provider " + p.getName() : "default provider") +
                    ": alg " + a);

            KeyStore k;
            if (p == null) {
                k = KeyStore.getInstance(a);
                System.out.println("  got KeyStore w/ alg " +
                        k.getType());
            } else {
                k = KeyStore.getInstance(a, p);
                System.out.println("  provider object: got KeyStore "
                        + "w/ alg " + k.getType());
                k = KeyStore.getInstance(a, p.getName());
                System.out.println("  provider name: got KeyStore w/ "
                        + "alg " + k.getType());
            }
        }
    }

    private static void runTests() throws Exception {
        test(ALG_LIST, null);

        File jksFile = new File(DIR, JKS_FN);

        System.out.println("Testing file with password: " + jksFile);
        KeyStore k = KeyStore.getInstance(jksFile, PASSWD);
        System.out.println("  file+password: got KeyStore w/ alg " +
                k.getType());

        System.out.println("Testing file with LoadStoreParameter: " +
                jksFile);
        k = KeyStore.getInstance(jksFile,
                () -> new KeyStore.PasswordProtection(PASSWD));
        System.out.println("  file+LoadStoreParameter: got KeyStore "
                + "w/ alg " + k.getType());

        Provider[] providers = Security.getProviders("KeyStore.JKS");
        for (Provider p : providers) {
            test(ALG_LIST, p);
        }
    }

    public static void main(String[] args) throws Exception {
        String propValue = args[0];
        boolean shouldWarn = Boolean.parseBoolean(args[1]);

        System.out.println("Setting Security Prop " + PROP_NAME + " = " +
                propValue);
        Security.setProperty(PROP_NAME, propValue);

        String warnS = saveWarn(TestLegacyAlgorithms::runTests);
        System.out.println("Warning emitted:\n" + warnS);

        String warn1 =
                "WARNING: An outdated KeyStore algorithm has been called by";
        String warn2 =
                " will be disabled by default in a future release";

        if (shouldWarn) {
            Asserts.assertTrue(warnS.contains(warn1),
                    "Expected legacy warning for KeyStore but not found");
            for (String a : ALG_LIST) {
                Asserts.assertTrue(warnS.contains("WARNING: " + a + warn2),
                        "Expected future-disable warning for KeyStore "
                        + a + " but not found");
            }
            Asserts.assertTrue(warnS.contains("TestLegacyAlgorithms"),
                    "Expected warning not preserve caller: "
                    + warnS);
        } else {
            Asserts.assertFalse(warnS.contains(warn1),
                    "Unexpected legacy warning for KeyStore: " + warnS);
            Asserts.assertFalse(warnS.contains(warn2),
                    "Unexpected future-disable warning for KeyStore: " + warnS);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
