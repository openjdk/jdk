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
 * @bug 8373359
 * @summary C2: BoolNode::Value_cmpu_and_mask() should look through cast nodes
 * @library /test/lib /
 * @run driver TestCastIIAtUnsignedCmp
 */

import compiler.lib.ir_framework.*;

public class TestCastIIAtUnsignedCmp {
    float fFld;
    boolean flag;
    int m;

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Test
    @Warmup(1)
    @IR(failOn = IRNode.STORE_F)
    int[] test() {
        int positiveInt = flag ? 0 : Integer.MAX_VALUE; // [0, max_int]
        int size = m & positiveInt;
        int[] arr = new int[size]; // CastII(size) // [0, max_int - 2]

        // size                       <=u positiveInt  (canonicalized condition)
        // CastII(size & positiveInt) <=u positiveInt
        //
        // Before JDK-8354282:
        //
        // After Loop Opts -> widen CastII in CastII::Value() -> same type as AndI input
        // -> ConstraintCastNode::Identity() removes CastII because not UnconditionalDependency
        // -> can now apply BoolNode::Value_cmpu_and_mask() case (1a):
        //     size & positiveInt <=u positiveInt
        // -> always true -> always take else path, and we remove the StoreF and fold the condition.
        //
        // After JDK-8354282:
        //
        // After Loop Opts -> widen CastII in CastII::Ideal() + set to non-narrowing dependency
        // -> same type as AndI input -> ConstraintCastNode::Identity() does NOT remove CastII
        // because non-narrowing dependency -> fail to apply Value_cmpu_and_mask (cannot look
        // through CastII) and thus cannot fold the condition
        if (Integer.compareUnsigned(size, positiveInt) > 0) {
            fFld = 34;
        }
        return arr;
    }
}
