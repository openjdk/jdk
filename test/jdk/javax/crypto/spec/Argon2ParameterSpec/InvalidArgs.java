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
 * @summary Ensure that Argon2ParameterSpec builder constructor throw the stated
 * exception for invalid arguments.
 */
import jdk.test.lib.Utils;

import javax.crypto.spec.Argon2ParameterSpec;
import static javax.crypto.spec.Argon2ParameterSpec.Builder;
import static javax.crypto.spec.Argon2ParameterSpec.Type;

public class InvalidArgs {

    private static byte[] B0 = new byte[0];

    public static void main(String[] args) throws Exception {
        Class iaeCls = IllegalArgumentException.class;

        Utils.runAndCheckException(()->Argon2ParameterSpec.newBuilder
                ((Type)null), iaeCls);
        Utils.runAndCheckException(()->Argon2ParameterSpec.newBuilder
                ((String)null), iaeCls);

        Utils.runAndCheckException(()->Argon2ParameterSpec.newBuilder
                ("$notArgon2$anything$"), iaeCls);

        Builder b = Argon2ParameterSpec.newBuilder(Type.ARGON2ID);
        Utils.runAndCheckException(()->b.nonce(null), iaeCls);
        Utils.runAndCheckException(()->b.nonce(B0), iaeCls);
        Utils.runAndCheckException(()->b.parallelism(-1), iaeCls);
        Utils.runAndCheckException(()->b.parallelism(0), iaeCls);
        Utils.runAndCheckException(()->b.memoryKB(10).parallelism(2), iaeCls);
        Utils.runAndCheckException(()->b.tagLen(0), iaeCls);
        Utils.runAndCheckException(()->b.tagLen(2), iaeCls);
        Utils.runAndCheckException(()->b.memoryKB(-1), iaeCls);
        Utils.runAndCheckException(()->b.memoryKB(0), iaeCls);
        Utils.runAndCheckException(()->b.memoryKB(7), iaeCls);
        Utils.runAndCheckException(()->b.parallelism(2).memoryKB(10), iaeCls);
        Utils.runAndCheckException(()->b.iterations(0), iaeCls);
        Utils.runAndCheckException(()->b.secret(null), iaeCls);
        Utils.runAndCheckException(()->b.ad(null), iaeCls);

        byte[] b8 = new byte[8];
        Builder b2 = Argon2ParameterSpec.newBuilder(Type.ARGON2ID).nonce(b8)
               .parallelism(2).memoryKB(32).tagLen(8).iterations(5);
        Utils.runAndCheckException(()->b2.build(null), iaeCls);
        System.out.println(b2.build("12345678".getBytes()));

        System.out.println("Test Passed");
    }
}
