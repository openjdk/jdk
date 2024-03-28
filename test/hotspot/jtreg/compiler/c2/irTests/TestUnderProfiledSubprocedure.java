/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

package compiler.c2.irTests;

import compiler.lib.ir_framework.*;
import java.util.ArrayList;
import java.util.Random;
import jdk.test.lib.Utils;

/*
 * @test
 * @bug 8325681
 * @summary C2 inliner rejects to inline a deeper callee because the methoddata of caller is immature.
 * @library /test/lib /
 * @run driver compiler.c2.irTests.TestUnderProfiledSubprocedure
 */
public class TestUnderProfiledSubprocedure {
    @Test
    @Arguments(setup = "setupCondition")
    @IR(failOn = {IRNode.ALLOC_OF, "ArrayList"})
    public void allocationExample(boolean cond) {
        // We expect Iterative EA of C2 marks x and its associative array non-escaped.
        var x = new ArrayList<Integer>();
        if (cond) { // The branch is only taken with possibility ODD%.
          x.add(0); // ArrayList::add(E) is a subprocedure. It calls ArrayList::add(E, Object[], int).
                    // when C2 compiles method 'allocateExample', it's possible that the methoddata of ArrayList::add(E) hasn't been mature yet.
                    // If C2 doesn't inline ArrayList.add, x is ArgEscaped.
        }
    }

    // The tipping point is ProfileMaturityPercentage.
    // When ODD < ProfileMaturityPercentage, it's likely that HotSpot brings method bar() to c2 with premature methoddata.
    private static final int ODD = 10;
    private static final Random RANDOM = Utils.getRandomInstance();
    private static int val = RANDOM.nextInt(100);

    @Setup
    Object[] setupCondition() {
        // return true with ODD% possibility.
        return new Object[]{Boolean.valueOf(RANDOM.nextInt(100) < ODD)};
    }

    @Test
    @Arguments(setup = "setupCondition")
    @IR(failOn = {IRNode.STATIC_CALL_OF_METHOD, "bar"})
    @IR(failOn = {IRNode.STATIC_CALL_OF_METHOD, "baz"})
    public void foo(boolean cond) {
        if (cond) {
            bar();
        }
    }

    public void bar() {
        baz();
    }

    // method baz must be greater than 6 bytecodes(MaxTrivialSize), or it will be inlined as a trivial
    public int baz() {
        val = (val-1) * (val+1);
        return val;
    }

    public static void main(String[] args)  {
        TestFramework.run();
    }
}
