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

package compiler.valhalla.inlinetypes;

/*
 * @test
 * @key randomness
 * @summary In CmpLNode::Ideal, we optimize expressions of the form
 *          CmpL(OrL(CastP2X(..), CastP2X(..)), 0L) that are created
 *          by Parse::do_acmp. If one of the operands has a NotNull type,
 *          then it can be folded. This test ensures that this optimization
 *          is not missed.
 * @library /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main/othervm -Xcomp -XX:-TieredCompilation -XX:+IgnoreUnrecognizedVMOptions
 *                   -XX:VerifyIterativeGVN=1110
 *                   -XX:CompileCommand=compileonly,${test.main.class}::test
 *                   ${test.main.class}
 * @run main ${test.main.class}
 */

value class MyValue1MissingOptAcmp {
    int x;

    MyValue1MissingOptAcmp(int x) {
        this.x = x;
    }
}

public class TestMissingOptAcmp {
    public static void main(String[] args) {
        test(new MyValue1MissingOptAcmp(1), new MyValue1MissingOptAcmp(1));
    }

    public static Object getNotNull(Object u) {
        // results in a CastPP with NotNull type, which enables the optimization
        return (u != null) ? u : new Object();
    }

    public static boolean test(MyValue1MissingOptAcmp v, Object u) {
        return (Object)v == getNotNull(u);
    }
}