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
 * @library /test/lib
 * @bug 8253914
 * @summary Ensure that Argon2ParameterSpec builder constructor throw the
 *     expected exception for invalid arguments.
 */
import jdk.test.lib.Utils;

import java.nio.charset.StandardCharsets;
import javax.crypto.spec.Argon2ParameterSpec;
import static javax.crypto.spec.Argon2ParameterSpec.Builder;

public class InvalidArgs {

    private static byte[] B0 = new byte[0];

    public static void main(String[] args) throws Exception {
        Class iaeCls = IllegalArgumentException.class;

        final Builder b = Argon2ParameterSpec.newBuilder();
        Utils.runAndCheckException(()->b.parallelism(-1), iaeCls);
        Utils.runAndCheckException(()->b.parallelism(0), iaeCls);
        Utils.runAndCheckException(()->b.parallelism(2).memoryKiB(12), iaeCls);
        Utils.runAndCheckException(()->b.parallelism(2).memoryPowerOfTwo(3),
                iaeCls);
        Utils.runAndCheckException(()->b.tagLen(0), iaeCls);
        Utils.runAndCheckException(()->b.tagLen(2), iaeCls);
        Utils.runAndCheckException(()->b.memoryKiB(-1), iaeCls);
        Utils.runAndCheckException(()->b.memoryKiB(0), iaeCls);
        Utils.runAndCheckException(()->b.memoryKiB(7), iaeCls);
        Utils.runAndCheckException(()->b.memoryKiB(16).parallelism(3), iaeCls);
        Utils.runAndCheckException(()->b.memoryPowerOfTwo(2), iaeCls);
        Utils.runAndCheckException(()->b.memoryPowerOfTwo(4).parallelism(3),
                iaeCls);
        Utils.runAndCheckException(()->b.iterations(0), iaeCls);
        Utils.runAndCheckException(()->b.secret(null), iaeCls);
        Utils.runAndCheckException(()->b.associatedData(null), iaeCls);

        final byte[] b8 = "12345678".getBytes();
        final char[] c0 = new char[0];
        // setup the builder w/ the required parameters
        b.parallelism(2).memoryKiB(32).tagLen(8).iterations(5);

        Utils.runAndCheckException(()->b.build(null, b8), iaeCls);
        Utils.runAndCheckException(()->b.build(B0, b8), iaeCls);
        Utils.runAndCheckException(()->b.build(b8, null), iaeCls);
        Utils.runAndCheckException(()->b.build(null, c0,
                StandardCharsets.UTF_8), iaeCls);
        Utils.runAndCheckException(()->b.build(B0, c0, StandardCharsets.UTF_8),
                iaeCls);
        Utils.runAndCheckException(()->b.build(b8, null,
                StandardCharsets.UTF_8), iaeCls);
        Utils.runAndCheckException(()->b.build(b8, c0, null), iaeCls);

        System.out.println(b.build(b8, b8));
        System.out.println(b.build(b8, c0, StandardCharsets.UTF_8));

        System.out.println("Test Passed");
    }
}
