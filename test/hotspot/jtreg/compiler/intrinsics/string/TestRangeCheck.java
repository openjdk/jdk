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

/*
 * @test
 * @bug 8374582
 * @summary Tests handling of invalid array indices in C2 intrinsic if explicit range check in Java code is not inlined.
 * @modules java.base/jdk.internal.access
 * @run main/othervm
 *      -XX:CompileCommand=inline,java.lang.StringCoding::*
 *      -XX:CompileCommand=exclude,jdk.internal.util.Preconditions::checkFromIndexSize
 *      ${test.main.class}
 */

package compiler.intrinsics.string;

import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.SharedSecrets;

public class TestRangeCheck {

    private static final JavaLangAccess JLA = SharedSecrets.getJavaLangAccess();

    public static void main(String[] args) {
        byte[] bytes = new byte[42];
        for (int i = 0; i < 10_000; ++i) {
            test(bytes);
        }
    }

    private static int test(byte[] bytes) {
        try {
            // Calling `StringCoding::countPositives`, which is a "front door"
            // to the `StringCoding::countPositives0` intrinsic.
            // `countPositives` validates its input using
            // `Preconditions::checkFromIndexSize`, which also maps to an
            // intrinsic. When `checkFromIndexSize` is not inlined, C2 does not
            // know about the explicit range checks, and does not cut off the
            // dead code. As a result, an invalid value (e.g., `-1`) can be fed
            // as input into the `countPositives0` intrinsic, get replaced
            // by TOP, and cause a failure in the matcher.
            return JLA.countPositives(bytes, -1, 42);
        } catch (Exception e) {
            return 0;
        }
    }
}

