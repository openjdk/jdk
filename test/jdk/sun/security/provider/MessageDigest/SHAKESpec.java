/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4936767
 * @library /test/lib
 * @summary SHAKE spec conformance
 */
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

import java.security.InvalidAlgorithmParameterException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.IntegerParameterSpec;
import java.security.spec.NamedParameterSpec;

public class SHAKESpec {
    public static void main(String[] args) throws Exception {
        test(128);
        test(256);
    }

    static void test(int strength) throws Exception {

        String name = "SHAKE" + strength;
        String hasLen = name + "-LEN";

        MessageDigest.getInstance(name);
        MessageDigest.getInstance(name, (AlgorithmParameterSpec) null);
        MessageDigest.getInstance(hasLen, new IntegerParameterSpec(128));

        check(() -> MessageDigest.getInstance(name, new IntegerParameterSpec(128)));
        check(() -> MessageDigest.getInstance(hasLen));
        check(() -> MessageDigest.getInstance(hasLen, (AlgorithmParameterSpec) null));
        check(() -> MessageDigest.getInstance(hasLen, NamedParameterSpec.ED448));
        check(() -> MessageDigest.getInstance(hasLen, new IntegerParameterSpec(-1)));
        check(() -> MessageDigest.getInstance(hasLen, new IntegerParameterSpec(0)));
        check(() -> MessageDigest.getInstance(hasLen, new IntegerParameterSpec(1)));

        MessageDigest md1 = MessageDigest.getInstance(hasLen, new IntegerParameterSpec(88));
        Asserts.assertEQ(md1.digest().length, 11);

        MessageDigest md2 = MessageDigest.getInstance(name);
        Asserts.assertEQ(md2.digest().length, strength / 4);
    }

    static void check(Utils.ThrowingRunnable runnable) {
        Utils.runAndCheckException(runnable, t -> Asserts.assertTrue(
                t instanceof NoSuchAlgorithmException &&
                        t.getCause() instanceof InvalidAlgorithmParameterException));
    }
}
