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
 * @bug 8389088
 * @summary Test that PushInlineTypeDown correctly handles dying branches in do_transform().
 * @library /test/lib
 * @enablePreview
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions
 *                   -Xcomp -XX:+DeoptimizeALot -XX:+AlwaysIncrementalInline -XX:CompileOnly=${test.main.class}::test
 *                   ${test.main.class}
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:+UnlockDiagnosticVMOptions
 *                   -Xcomp -XX:+StressIGVN -XX:+AlwaysIncrementalInline -XX:CompileOnly=${test.main.class}::test
 *                   ${test.main.class}
 * @run main ${test.main.class}
 */

package compiler.valhalla.inlinetypes;

import jdk.test.lib.Asserts;

public class TestPushInlineTypeDownDeadBranch {
    public static void main(String[] args) {
        for (int i = 0; i < 10000; i++) {
            Asserts.assertEQ(test(), null);
        }
    }

    static V test() {
        //                                                                                         null
        //                                                                                           |
        //                                                                                           v
        // Before Incremental Inlining: CallStaticJava   -> OpaqueParse -> CastPP -> CheckCastPP -> Phi -> InlineType -> Return
        // After  Incremental Inlining: InlineType(null) -> OpaqueParse -> CastPP -> CheckCastPP -> Phi -> InlineType -> Return
        // During IGVN:                 InlineType(null)                -> CastPP -> CheckCastPP -> Phi -> InlineType -> Return
        //                              InlineType(null)                -> CastPP -> CheckCastPP -> Phi -> InlineType -> Return
        //                              InlineType(null)                                                -> InlineType -> Return
        // Before Patch:                InlineType(TOP /* wrong! */)                                    -> InlineType -> Return
        //                              <empty>
        // After Patch:                 InlineType(null)                                                -> InlineType -> Return
        Object obj = foo(null);
        return (V)obj;
    }

    static Object foo(Object obj) {
        return (V)obj;
    }

    static value class V {
        int i = 34;
    }
}
