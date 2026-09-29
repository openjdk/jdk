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
 * @run main/othervm TestDisabledAlgorithms MESSAGEdigest.Sha-512 true
 * @run main/othervm TestDisabledAlgorithms messageDIGest.what false
 * @run main/othervm TestDisabledAlgorithms meSSagedIgest.sHA-512/224 false
 */
import java.util.List;
import java.security.NoSuchAlgorithmException;
import java.security.MessageDigest;
import java.security.Provider;
import java.security.Security;
import jdk.test.lib.Utils;

public class TestDisabledAlgorithms {

    private static final String PROP_NAME = "jdk.crypto.disabledAlgorithms";

    private static void test(List<String> algos, Provider p,
            boolean shouldThrow) throws Exception {

        for (String a : algos) {
            System.out.println("Testing " + (p != null ? p.getName() : "") +
                    ": " + a + ", shouldThrow=" + shouldThrow);
            if (shouldThrow) {
                if (p == null) {
                    Utils.runAndCheckException(() -> MessageDigest.getInstance(a),
                            NoSuchAlgorithmException.class);
                } else {
                    Utils.runAndCheckException(() -> MessageDigest.getInstance(a, p),
                            NoSuchAlgorithmException.class);
                    Utils.runAndCheckException(() -> MessageDigest.getInstance(a,
                            p.getName()), NoSuchAlgorithmException.class);
                }
            } else {
                MessageDigest m;
                if (p == null) {
                    m = MessageDigest.getInstance(a);
                } else {
                    m = MessageDigest.getInstance(a, p);
                    m = MessageDigest.getInstance(a, p.getName());
                }
                System.out.println("Got MessageDigest w/ algo " +
                        m.getAlgorithm());
            }
        }
    }

    public static void main(String[] args) throws Exception {
        String propValue = args[0];
        System.out.println("Setting Security Prop " + PROP_NAME + " = " +
                propValue);
        Security.setProperty(PROP_NAME, propValue);

        boolean shouldThrow = Boolean.valueOf(args[1]);

        List<String> algos = List.of("sHA-512", "shA-512",
                "2.16.840.1.101.3.4.2.3");
        // test w/o provider
        test(algos, null, shouldThrow);

        // test w/ provider
        Provider[] providers = Security.getProviders("MessageDigest.SHA-512");
        for (Provider p : providers) {
            test(algos, p, shouldThrow);
        }
    }
}
