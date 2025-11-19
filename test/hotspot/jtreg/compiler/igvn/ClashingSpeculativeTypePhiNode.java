/*
 * Copyright (c) 2025, Red Hat, Inc.
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
 * @bug 8371716
 * @summary Ranges can be proven to be disjoint but not orderable (thanks to unsigned range)
 *          Comparing such values in such range with != should always be true.
 * @run main/othervm -XX:-TieredCompilation
 *                   -XX:-UseOnStackReplacement
 *                   -XX:-BackgroundCompilation
 *                   -XX:CompileOnly=compiler.igvn.ClashingSpeculativeTypePhiNode::test1
 *                   -XX:CompileCommand=quiet
 *                   -XX:TypeProfileLevel=222
 *                   -XX:+AlwaysIncrementalInline
 *                   -XX:VerifyIterativeGVN=10
 *                   -XX:CompileCommand=dontinline,compiler.igvn.ClashingSpeculativeTypePhiNode::notInlined1
 *                   compiler.igvn.ClashingSpeculativeTypePhiNode
 *
 * @run main compiler.igvn.ClashingSpeculativeTypePhiNode
 */

package compiler.igvn;

public class ClashingSpeculativeTypePhiNode {
    public static void main(String[] args) {
        for (int i = 0; i < 20_000; i++) {
            test1(false);
            inlined1(true, true);
            inlined2(false);
        }
    }

    private static Object test1(boolean flag1) {
        return inlined1(flag1, false);
    }

    private static Object inlined1(boolean flag1, boolean flag2) {
        if (flag1) {
            return inlined2(flag2); // C1
        }
        return null;
    }

    private static Object inlined2(boolean flag2) {
        if (flag2) {
            return new C1();
        }
        return notInlined1(); // C2
    }

    private static Object notInlined1() {
        return new C2();
    }

    static class C1 {

    }

    static class C2 {

    }
}
