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
 * @summary Test JCE layer legacy algorithm warning for MessageDigest
 * @library /test/lib
 * @run main/othervm TestLegacyAlgorithms MESSAGEdigest.Sha-512 true
 * @run main/othervm TestLegacyAlgorithms messageDIGest.what false
 * @run main/othervm TestLegacyAlgorithms meSSagedIgest.sHA-512/224 false
 * @run main/othervm -Djdk.crypto.legacyAlgorithms=MESSAGEdigest.Sha-512
 *      -Djdk.crypto.disabledAlgorithms=MESSAGEdigest.Sha-512
 *      TestLegacyAlgorithms MESSAGEdigest.Sha-512 false true
 */

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.util.List;

import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

public class TestLegacyAlgorithms {

    private static final String PROP_NAME = "jdk.crypto.legacyAlgorithms";
    private static final List<String> ALG_LIST =
            List.of("sHA-512", "shA-512", "2.16.840.1.101.3.4.2.3");

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
                "WARNING: An outdated MessageDigest algorithm has been called by";
        String warn2 = "WARNING: " + alg
                + " will be disabled by default in a future release";

        Asserts.assertEQ(countWarn(warnS, warn1), 1,
                "Expected one legacy warning for MessageDigest " + alg
                        + " but got:\n" + warnS);
        Asserts.assertEQ(countWarn(warnS, warn2), 1,
                "Expected one future-disable warning for MessageDigest "
                        + alg + " but got:\n" + warnS);
        Asserts.assertTrue(warnS.contains("TestLegacyAlgorithms"),
                "Expected warning to preserve caller: " + warnS);
    }

    private static void checkNoWarn(String warnS) {
        String warn1 =
                "WARNING: An outdated MessageDigest algorithm has been called by";
        String warn2 =
                "will be disabled by default in a future release";
        Asserts.assertFalse(warnS.contains(warn1),
                "Unexpected legacy warning for MessageDigest: " + warnS);
        Asserts.assertFalse(warnS.contains(warn2),
                "Unexpected future-disable warning for MessageDigest: " + warnS);
    }

    private static void checkwarn(String label, String alg,
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
        checkwarn("no warning when the algorithm is disabled",
                "SHA-512", false, () -> {
                    Utils.runAndCheckException(
                            () -> MessageDigest.getInstance("SHA-512"),
                            NoSuchAlgorithmException.class);
                });
    }

    private static void runTests(boolean shouldWarn) throws Exception {
        for (String a : ALG_LIST) {
            checkwarn("default provider: alg " + a, a, shouldWarn,
                    () -> DefaultMD.run(a));
        }

        Provider[] providers = Security.getProviders("MessageDigest.SHA-512");
        if (providers.length > 0) {
            // First provider should warn, and later provider for the same
            // algorithm will not warn. This is because warning is determined
            // by caller class and algorithm string, not by provider.
            Provider p = providers[0];
            for (String a : ALG_LIST) {
                checkwarn("provider object " + p.getName() + ": alg " + a,
                        a, shouldWarn, () -> ProvObjMD.run(a, p));

                checkwarn("provider name " + p.getName() + ": alg " + a,
                        a, shouldWarn, () -> ProvNameMD.run(a, p));
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

    private static final class DefaultMD {
        static void run(String alg) throws Exception {
            MessageDigest m = MessageDigest.getInstance(alg);
            System.out.println("  type lookup: got MessageDigest w/ alg "
                    + m.getAlgorithm());

            // Call the method twice, and make sure that only get one
            // warning per caller.
            m = MessageDigest.getInstance(alg);
            System.out.println("  type lookup again: got MessageDigest w/ alg "
                    + m.getAlgorithm());
        }
    }

    private static final class ProvObjMD {
        static void run(String alg, Provider provider) throws Exception {
            MessageDigest m = MessageDigest.getInstance(alg, provider);
            System.out.println("  provider object: got MessageDigest w/ alg "
                    + m.getAlgorithm());

            // Call the method twice, and make sure that only get one
            // warning per caller.
            m = MessageDigest.getInstance(alg, provider);
            System.out.println("  provider object again: got MessageDigest "
                    + "w/ alg " + m.getAlgorithm());
        }
    }

    private static final class ProvNameMD {
        static void run(String alg, Provider provider) throws Exception {
            MessageDigest m = MessageDigest.getInstance(alg, provider.getName());
            System.out.println("  provider name: got MessageDigest w/ alg "
                    + m.getAlgorithm());

            // Call the method twice, and make sure that only get one
            // warning per caller.
            m = MessageDigest.getInstance(alg, provider.getName());
            System.out.println("  provider name again: got MessageDigest "
                    + "w/ alg " + m.getAlgorithm());
        }
    }
}
