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
 * @bug 8396624
 * @summary Verify that SHA3 parallel intrinsics are invoked properly.
 *
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc
 *          java.management
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -Xbatch -XX:-TieredCompilation
 *                   -XX:Tier4InvocationThreshold=50000
 *                   -XX:+LogCompilation -XX:LogFile=positive_double.log
 *                   -XX:+UseSHA3Intrinsics
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:+UseSIMDForSHA3Intrinsic
 *                   --add-opens java.base/sun.security.provider=ALL-UNNAMED
 *                   -Dmethod=doubleKeccak
 *                   compiler.intrinsics.sha.sanity.TestSHA3ParallelIntrinsics
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -Xbatch -XX:-TieredCompilation
 *                   -XX:Tier4InvocationThreshold=50000
 *                   -XX:+LogCompilation -XX:LogFile=negative_double.log
 *                   -XX:-UseSHA3Intrinsics
 *                   --add-opens java.base/sun.security.provider=ALL-UNNAMED
 *                   -Dmethod=doubleKeccak
 *                   compiler.intrinsics.sha.sanity.TestSHA3ParallelIntrinsics
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -Xbatch -XX:-TieredCompilation
 *                   -XX:Tier4InvocationThreshold=50000
 *                   -XX:+LogCompilation -XX:LogFile=positive_quad.log
 *                   -XX:+UseSHA3Intrinsics
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:+UseSIMDForSHA3Intrinsic
 *                   --add-opens java.base/sun.security.provider=ALL-UNNAMED
 *                   -Dmethod=quadKeccak
 *                   compiler.intrinsics.sha.sanity.TestSHA3ParallelIntrinsics
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -Xbatch -XX:-TieredCompilation
 *                   -XX:Tier4InvocationThreshold=50000
 *                   -XX:+LogCompilation -XX:LogFile=negative_quad.log
 *                   -XX:-UseSHA3Intrinsics
 *                   --add-opens java.base/sun.security.provider=ALL-UNNAMED
 *                   -Dmethod=quadKeccak
 *                   compiler.intrinsics.sha.sanity.TestSHA3ParallelIntrinsics
 * @run main/othervm -DverificationStrategy=VERIFY_INTRINSIC_USAGE
 *                    compiler.testlibrary.intrinsics.Verifier positive_double.log positive_quad.log
 *                    negative_double.log negative_quad.log
 */

package compiler.intrinsics.sha.sanity;

import compiler.intrinsics.sha.sanity.InvokeSanityTestBase;
import compiler.testlibrary.sha.predicate.IntrinsicPredicates;

public class TestSHA3ParallelIntrinsics {
    private static final String SHA3_DOUBLE_KECCAK_METHOD_NAME
            = "doubleKeccak";
    private static final String SHA3_QUAD_KECCAK_METHOD_NAME
            = "quadKeccak";

    public static void main(String args[]) throws Exception {
        String method = System.getProperty("method", SHA3_DOUBLE_KECCAK_METHOD_NAME);

        if (method.equals(SHA3_DOUBLE_KECCAK_METHOD_NAME) == true) {
            new InvokeSanityTestBase(IntrinsicPredicates.isSHA3DoubleKeccakIntrinsicAvailable(),
                    InvokeSanityTestBase.SHA3_DOUBLE_KECCAK_INTRINSIC_ID).test();
        } else if (method.equals(SHA3_QUAD_KECCAK_METHOD_NAME) == true) {
            new InvokeSanityTestBase(IntrinsicPredicates.isSHA3QuadKeccakIntrinsicAvailable(),
                    InvokeSanityTestBase.SHA3_QUAD_KECCAK_INTRINSIC_ID).test();
        }
    }
}
