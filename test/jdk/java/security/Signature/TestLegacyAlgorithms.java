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
 * @run main/othervm TestLegacyAlgorithms SIGNATURe.sha512withRSA true
 * @run main/othervm TestLegacyAlgorithms signaturE.what false
 * @run main/othervm TestLegacyAlgorithms SiGnAtUrE.SHa512/224withRSA false
 */

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.security.Provider;
import java.security.Security;
import java.security.Signature;
import java.util.List;

import jdk.test.lib.Asserts;

public class TestLegacyAlgorithms {

    private static final String PROP_NAME = "jdk.crypto.legacyAlgorithms";
    private static final List<String> ALG_LIST =
            List.of("sha512withRsa", "1.2.840.113549.1.1.13");

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

            Signature s;
            if (p == null) {
                s = Signature.getInstance(a);
                System.out.println("  got Signature w/ alg " +
                        s.getAlgorithm());
            } else {
                s = Signature.getInstance(a, p);
                System.out.println("  provider object: got Signature "
                        + "w/ alg " + s.getAlgorithm());
                s = Signature.getInstance(a, p.getName());
                System.out.println("  provider name: got Signature w/ "
                        + "alg " + s.getAlgorithm());
            }
        }
    }

    private static void runTests() throws Exception {
        test(ALG_LIST, null);

        Provider[] providers = Security.getProviders("Signature.SHA512withRSA");
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
                "WARNING: An outdated Signature algorithm has been called by";
        String warn2 =
                " will be disabled by default in a future release";

        if (shouldWarn) {
            Asserts.assertTrue(warnS.contains(warn1),
                    "Expected legacy warning for Signature but not found");
            for (String a : ALG_LIST) {
                Asserts.assertTrue(warnS.contains("WARNING: " + a + warn2),
                        "Expected future-disable warning for Signature "
                        + a + " but not found");
            }
            Asserts.assertTrue(warnS.contains("TestLegacyAlgorithms"),
                    "Expected warning not preserve caller: "
                    + warnS);
        } else {
            Asserts.assertFalse(warnS.contains(warn1),
                    "Unexpected legacy warning for Signature: " + warnS);
            Asserts.assertFalse(warnS.contains(warn2),
                    "Unexpected future-disable warning for Signature: " + warnS);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
