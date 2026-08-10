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
 * @summary Test JCE layer legacy algorithm warning for Signature
 * @library /test/lib
 * @run main/othervm TestLegacyAlgorithms SIGNATURe.sha512withRSA true
 * @run main/othervm TestLegacyAlgorithms signaturE.what false
 * @run main/othervm TestLegacyAlgorithms SiGnAtUrE.SHa512/224withRSA false
 * @run main/othervm -Djdk.crypto.legacyAlgorithms=SIGNATURe.sha512withRSA
 *      -Djdk.crypto.disabledAlgorithms=SIGNATURe.sha512withRSA
 *      TestLegacyAlgorithms SIGNATURe.sha512withRSA false true
 */

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.security.Signature;
import java.util.List;

import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

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

    private static int countWarn(String warnS, String msg) {
        int num = 0;
        int index = 0;
        while ((index = warnS.indexOf(msg, index)) >= 0) {
            num++;
            index += msg.length();
        }
        return num;
    }

    private static void checkOneWarn(String warnS, String alg) {
        String warn1 =
                "WARNING: An outdated Signature algorithm has been called by";
        String warn2 = "WARNING: " + alg
                + " will be disabled by default in a future release";

        Asserts.assertEQ(countWarn(warnS, warn1), 1,
                "Expected one legacy warning for Signature " + alg
                        + " but got:\n" + warnS);
        Asserts.assertEQ(countWarn(warnS, warn2), 1,
                "Expected one future-disable warning for Signature "
                        + alg + " but got:\n" + warnS);
        Asserts.assertTrue(warnS.contains("TestLegacyAlgorithms"),
                "Expected warning to preserve caller: " + warnS);
    }

    private static void checkNoWarn(String warnS) {
        String warn1 =
                "WARNING: An outdated Signature algorithm has been called by";
        String warn2 =
                "will be disabled by default in a future release";
        Asserts.assertFalse(warnS.contains(warn1),
                "Unexpected legacy warning for Signature: " + warnS);
        Asserts.assertFalse(warnS.contains(warn2),
                "Unexpected future-disable warning for Signature: " + warnS);
    }

    private static void checkWarn(String label, String alg,
            boolean shouldWarn, ThrowingRunnable action) throws Exception {
        System.out.println("Testing " + label);
        String warnS = saveWarn(action);
        System.out.println("Warning emitted:\n" + warnS);
        if (shouldWarn) {
            checkOneWarn(warnS, alg);
        } else {
            checkNoWarn(warnS);
        }
    }

    // Disable the algorithm and check that a warning is not emitted.
    private static void warnDisabledTest()
            throws Exception {
        checkWarn("no warning when the algorithm is disabled",
                "sha512withRSA", false, () -> {
                    Utils.runAndCheckException(
                            () -> Signature.getInstance("sha512withRSA"),
                            NoSuchAlgorithmException.class);
                });
    }

    private static void runTests(boolean shouldWarn) throws Exception {
        for (String a : ALG_LIST) {
            checkWarn("default provider: alg " + a, a, shouldWarn,
                    () -> DefaultSig.run(a));
        }

        Provider[] providers = Security.getProviders("Signature.SHA512withRSA");
        if (providers.length > 0) {
            // First provider should warn, and later provider for the same
            // algorithm will not warn. This is because warning is determined
            // by caller class and algorithm string, not by provider.
            Provider p = providers[0];
            for (String a : ALG_LIST) {
                checkWarn("provider object " + p.getName() + ": alg " + a,
                        a, shouldWarn, () -> ProvObjSig.run(a, p));

                checkWarn("provider name " + p.getName() + ": alg " + a,
                        a, shouldWarn, () -> ProvNameSig.run(a, p));
            }
        }
    }

    public static void main(String[] args) throws Exception {
        String propValue = args[0];
        boolean shouldWarn = Boolean.parseBoolean(args[1]);
        boolean warnDisabled =
                args.length > 2 && Boolean.parseBoolean(args[2]);
        System.out.println("Setting Security Prop " + PROP_NAME + " = " +
                propValue);
        Security.setProperty(PROP_NAME, propValue);
        if (warnDisabled) {
            warnDisabledTest();
        } else {
            runTests(shouldWarn);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class DefaultSig {
        static void run(String alg) throws Exception {
            Signature s = Signature.getInstance(alg);
            System.out.println("  type lookup: got Signature w/ alg "
                    + s.getAlgorithm());

            // Call the method twice, and make sure that only get one
            // warning per caller.
            s = Signature.getInstance(alg);
            System.out.println("  type lookup again: got Signature w/ alg "
                    + s.getAlgorithm());
        }
    }

    private static final class ProvObjSig {
        static void run(String alg, Provider provider) throws Exception {
            Signature s = Signature.getInstance(alg, provider);
            System.out.println("  provider object: got Signature w/ alg "
                    + s.getAlgorithm());

            // Call the method twice, and make sure that only get one
            // warning per caller.
            s = Signature.getInstance(alg, provider);
            System.out.println("  provider object again: got Signature "
                    + "w/ alg " + s.getAlgorithm());
        }
    }

    private static final class ProvNameSig {
        static void run(String alg, Provider provider) throws Exception {
            Signature s = Signature.getInstance(alg, provider.getName());
            System.out.println("  provider name: got Signature w/ alg "
                    + s.getAlgorithm());

            // Call the method twice, and make sure that only get one
            // warning per caller.
            s = Signature.getInstance(alg, provider.getName());
            System.out.println("  provider name again: got Signature "
                    + "w/ alg " + s.getAlgorithm());
        }
    }
}
