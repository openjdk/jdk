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
 * @library /test/lib ..
 * @run main/othervm TestDisabledAlgorithms CiPhEr.RSA/ECB/PKCS1Padding true
 * @run main/othervm TestDisabledAlgorithms cIpHeR.rsA true
 * @run main/othervm TestDisabledAlgorithms Cipher.what false
 * @run main/othervm TestDisabledAlgorithms CiPhER.RSA/ECB/PKCS1Padding2 false
 */
import java.util.List;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import jdk.test.lib.Utils;

public class TestDisabledAlgorithms extends PKCS11Test {

    boolean shouldThrow;

    TestDisabledAlgorithms(boolean shouldThrow) {
        this.shouldThrow = shouldThrow;
    }

    private static final String PROP_NAME = "jdk.crypto.disabledAlgorithms";

    private static void test(String alg, Provider p, boolean shouldThrow)
            throws Exception {
        System.out.println("Testing " + p.getName() + ": " + alg +
                ", shouldThrow=" + shouldThrow);
        if (shouldThrow) {
            Utils.runAndCheckException(() -> Cipher.getInstance(alg, p),
                    NoSuchAlgorithmException.class);
        } else {
            Cipher c = Cipher.getInstance(alg, p);
            System.out.println("Got cipher w/ algo " + c.getAlgorithm());
        }
    }

    @Override
    public void main(Provider p) throws Exception {
        for (String a : List.of("RSA/ECB/PKCS1Padding", "RSA")) {
            test(a, p, shouldThrow);
        }
        System.out.println("Done");
    }

    public static void main(String[] args) throws Exception {
        String propValue = args[0];
        System.out.println("Setting Security Prop " + PROP_NAME + " = " +
                propValue);
        Security.setProperty(PROP_NAME, propValue);
        boolean shouldThrow = Boolean.valueOf(args[1]);
        main(new TestDisabledAlgorithms(shouldThrow), args);
    }
}
