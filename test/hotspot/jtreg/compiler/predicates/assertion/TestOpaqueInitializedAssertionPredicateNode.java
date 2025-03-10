/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/*
 * @test
 * @bug 8330386
 * @summary Test that replacing Opaque4 nodes with OpaqueInitializedAssertionPredicate for Initialized Assertion Predicates
 *          works. We test following cases explicitly:
 *          1) Cloning down CmpUNode in Split If with involved OpaqueInitializedAssertionPredicateNodes
 *          2) Special casing OpaqueInitializedAssertionPredicate in IdealLoopTree::policy_range_check()
 *          3) Special casing Opaque4 node from non-null check for intrinsics and unsafe accesses inside
 *             PhaseIdealLoop::update_main_loop_assertion_predicates().
 * @requires vm.compiler2.enabled
 * @modules java.base/jdk.internal.misc:+open
 * @run main/othervm -Xbatch -XX:LoopMaxUnroll=0
 *                   -XX:CompileCommand=compileonly,*TestOpaqueInitializedAssertionPredicateNode::test*
 *                   -XX:CompileCommand=dontinline,*TestOpaqueInitializedAssertionPredicateNode::dontInline
 *                   compiler.predicates.assertion.TestOpaqueInitializedAssertionPredicateNode
 * @run main/othervm -Xcomp -XX:LoopMaxUnroll=0 -XX:-LoopUnswitching
 *                   -XX:CompileCommand=compileonly,*TestOpaqueInitializedAssertionPredicateNode::test*
 *                   -XX:CompileCommand=dontinline,*TestOpaqueInitializedAssertionPredicateNode::dontInline
 *                   compiler.predicates.assertion.TestOpaqueInitializedAssertionPredicateNode
 * @run main/othervm -Xbatch -XX:LoopMaxUnroll=0 -XX:PerMethodTrapLimit=0
 *                   -XX:CompileCommand=compileonly,*TestOpaqueInitializedAssertionPredicateNode::test*
 *                   -XX:CompileCommand=dontinline,*TestOpaqueInitializedAssertionPredicateNode::dontInline
 *                   compiler.predicates.assertion.TestOpaqueInitializedAssertionPredicateNode
 */

/*
 * @test id=noflags
 * @bug 8330386
 * @modules java.base/jdk.internal.misc:+open
 * @run main compiler.predicates.assertion.TestOpaqueInitializedAssertionPredicateNode
 */

/*
 * @test id=clone_loop_handle_data_uses
 * @bug 8333644
 * @modules java.base/jdk.internal.misc:+open
 * @summary Test that using OpaqueInitializedAssertionPredicate for Initialized Assertion Predicates instead of Opaque4
 *          nodes also works with clone_loop_handle_data_uses() which missed a case before.
 * @run main/othervm -Xcomp -XX:CompileCommand=compileonly,*TestOpaqueInitializedAssertionPredicateNode::test*
 *                   -XX:CompileCommand=dontinline,*TestOpaqueInitializedAssertionPredicateNode::dontInline
 *                   compiler.predicates.assertion.TestOpaqueInitializedAssertionPredicateNode
 */

package compiler.predicates.assertion;

import jdk.internal.misc.Unsafe;
import java.lang.reflect.Field;

public class TestOpaqueInitializedAssertionPredicateNode {

    static boolean flag, flag2;
    static int iFld;
    static long lFld;
    static int x;
    static int y = 51;
    static int iArrLength;
    static int[] iArr = new int[100];

    static final Unsafe UNSAFE = Unsafe.getUnsafe();
    static final long OFFSET;

    static {
        try {
            Field fieldIFld = A.class.getDeclaredField("iFld");
            OFFSET = UNSAFE.objectFieldOffset(fieldIFld);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        Integer.compareUnsigned(23, 34); // Make sure loaded with -Xcomp.
        A a = new A(34);
        for (int i = 0; i < 10000; i++) {
            iArrLength = i % 15 == 0 ? 30 : 100;
            flag = i % 3 == 0;
            flag2 = i % 5 == 0;
            x = (i % 15 == 0 ? 100 : 0);
            testCloneDown();
            testOnlyCloneDownCmp();
            testCloneDownInsideLoop();
            maybeNull(null); // Make sure return value is sometimes null.
            testPolicyRangeCheck(a);
            testUnsafeAccess(a);
            testOpaqueOutsideLoop();
            testOpaqueOutsideLoop8333644();
            testOpaqueInsideIfOutsideLoop();
        }
    }

    // Profiling will tell us that the return value is sometimes null and sometimes not.
    static A maybeNull(Object o) {
        return (A)o;
    }

    static void testCloneDown() {
        int a;
        int b;
        int[] iArr = new int[iArrLength];

        for (int i = 2; i < 4; i *= 2) ; // Make sure to run with loop opts.

        if (flag) {
            a = 34;
        } else {
            a = 3;
        }
        // Region to split through

        // --- BLOCK start ---

        // CMoveI(Bool(CmpU(y, iArr.length))), 34, 23)  (**)
        if (Integer.compareUnsigned(y, iArr.length) < 0) {
            b = 34;
        } else {
            b = 23;
        }
        iFld = b; // iFld = CMoveI -> make sure CMoveI is inside BLOCK

        // --- BLOCK end ---

        if (a > 23) { // If to split -> need to empty BLOCK
            iFld = 34;
        }

        if (flag2) {
            // Avoid out-of-bounds access in loop below
            return;
        }

        // When peeling the loop, we create an Initialized Assertion Predicate with the same CmpU as (**) above:
        // IAP(CmpU(y, iArr.length))
        //
        // At Split If: Need to clone CmpU down because it has two uses:
        // - Bool of Cmove used in "iFld = b"
        // - Bool for IAP
        //
        // => IAP uses OpaqueInitializedAssertionPredicate -> clone_cmp_down() therefore needs to handle that.
        for (int i = y - 1; i < 100; i++) {
            iArr[i] = 34; // Hoisted with Loop Predicate
            if (flag) { // Reason to peel.
                return;
            }
        }
    }

    // Same as test() but we only clone down the CmpU and not the Bool with the OpaqueInitializedAssertionPredicate
    static void testOnlyCloneDownCmp() {
        int a;
        int b;
        int[] iArr = new int[iArrLength];

        for (int i = 2; i < 4; i *= 2) ; // Make sure to run with loop opts.

        if (flag) {
            a = 34;
        } else {
            a = 3;
        }
        // Region to split through

        // --- BLOCK start ---

        // CMoveI(Bool(CmpU(51, iArr.length))), 34, 23)  (**)
        // Using constant 51 -> cannot common up with Bool from Initialized Assertion Predicate
        if (Integer.compareUnsigned(51, iArr.length) < 0) {
            b = 34;
        } else {
            b = 23;
        }
        iFld = b; // iFld = CMoveI -> make sure CMoveI is inside BLOCK

        // --- BLOCK end ---

        if (a > 23) { // If to split -> need to empty BLOCK
            iFld = 34;
        }

        if (flag2) {
            // Avoid out-of-bounds access in loop below
            return;
        }

        // When peeling the loop, we create an Initialized Assertion Predicate with the same CmpU as (**) above:
        // IAP(CmpU(y, iArr.length))
        //
        // At Split If: Need to clone CmpU down because it has two uses:
        // - Bool of Cmove used in "iFld = b"
        // - Bool for IAP
        //
        // => IAP uses OpaqueInitializedAssertionPredicate -> clone_cmp_down() therefore needs to handle that.
        for (int i = 50; i < 100; i++) {
            iArr[i] = 34; // Hoisted with Loop Predicate
            if (flag) { // Reason to peel.
                return;
            }
        }
    }

    // Same as test() but everything inside another loop.
    static void testCloneDownInsideLoop() {
        int a;
        int b;
        int[] iArr = new int[iArrLength];

        for (int i = 3; i < 30; i *= 2) { // Non-counted loop
            if (i < 10) {
                a = 34;
            } else {
                a = 3;
            }
            // Region to split through

            // --- BLOCK start ---

            // CMoveI(Bool(CmpU(a + i, iArr.length))), 34, 23)  (**)
            if (Integer.compareUnsigned(a + i, iArr.length) < 0) {
                b = 34;
            } else {
                b = 23;
            }
            iFld = b; // iFld = CMoveI -> make sure CMoveI is inside BLOCK

            // --- BLOCK end ---

            if (a > 23) { // If to split -> need to empty BLOCK
                iFld = 34;
            }

            if (i < x) {
                // Avoid out-of-bounds access in loop below
                return;
            }

            // When peeling the loop, we create an Initialized Assertion Predicate with the same CmpU as (**) above:
            // IAP(CmpU(a + i, iArr.length))
            //
            // At Split If: Need to clone CmpU down because it has two uses:
            // - Bool of Cmove used in "iFld = b"
            // - Bool for IAP
            //
            // => IAP uses OpaqueInitializedAssertionPredicate -> clone_cmp_down() therefore needs to handle that.
            for (int j = a + i - 1; j < 100; j++) {
                iArr[j] = 34; // Hoisted with Loop Predicate
                if (flag) { // Reason to peel.
                    return;
                }
            }
        }
    }

    static void testPolicyRangeCheck(Object o) {
        int two = 100;
        int limit = 2;
        for (; limit < 4; limit *= 2);
        for (int i = 2; i < limit; i++) {
            two = 2;
        }

        // 4) We call IdealLoopTree::policy_range_check() for this loop:
        //    - Initialized Assertion Predicate is now part of loop body.
        //    - Opaque4 node for null-check is also part of loop body.
         //   We also check the If nodes for these Opaque nodes could be eliminated with
         //   Range Check Elimination. We thus need to exclude Ifs with
         //   Opaque4 and OpaqueInitializedAssertionPredicate nodes in policy_range_check().
        for (int i = 0; i < 100; i++) {
            A a = maybeNull(o); // Profiling tells us that return value *might* be null.
            iFld = UNSAFE.getInt(a, OFFSET); // Emits If with Opaque4Node for null check.

            // 1) Apply Loop Predication: Loop Predicate + Template Assertion Predicate
            // 2) Apply Loop Peeling: Create Initialized Assertion Predicate with
            //                        OpaqueInitializedAssertionPredicate
            // 3) After CCP: C2 knows that two == 2. CountedLoopEnd found to be true
            //               (only execute loop once) -> CountedLoop removed
            for (int j = 0; j < two; j++) {
                iArr[j] = 34; // Hoisted in Loop Predication
                if (flag) {
                    return;
                }
            }
        }
    }

    static void testUnsafeAccess(Object o) {
        A a = maybeNull(o); // Profiling tells us that return value *might* be null.
        iFld = UNSAFE.getInt(a, OFFSET); // Emits If with Opaque4Node for null check.

        // We don't have any Parse Predicates with -XX:PerMethodTrapLimit=0. And therefore, If with Opaque4 will
        // directly be above CountedLoop. When maximally unrolling the counted loop, we try to update any Assertion
        // Predicate. We will find the If with the Opaque4 node for the non-null check which is not an Assertion
        // Predicate. This needs to be handled separately in PhaseIdealLoop::update_main_loop_assertion_predicates().
        for (int i = 0; i < 10; i++) {
            iFld *= 34;
        }
    }

    // [If->OpaqueInitializedAssertionPredicate]->Bool->Cmp, []: Inside Loop, other nodes outside.
    static void testOpaqueOutsideLoop() {
        int two = 100;
        int limit = 2;
        for (; limit < 4; limit *= 2);
        for (int i = 2; i < limit; i++) {
            two = 2;
        }

        // 4) After CCP, we can apply Loop Peeling since we removed enough nodes to bring the body size down below 255.
        // When cloning the Bool for the IAP, we have a use inside the loop (initializedAssertionPredicateBool) and one
        // outside for the IAP (input to the OpaqueInitializedAssertionPredicate being outside the loop)
        // As a result, we add the OpaqueInitializedAssertionPredicate to the split if set in clone_loop_handle_data_uses().
        for (short i = 3; i < 30; i*=2) { // Use short such that we do not need overflow protection for Loop Predicates
            if (two == 100) {
                // Before CCP: Uninlined method call prevents peeling.
                // After CCP: C2 knows that two == 2 and we remove this call which enables Loop Peeling for i-loop.
                dontInline();
            }

            // Same condition as used for IAP in j-loop below.
            boolean initializedAssertionPredicateBool = Integer.compareUnsigned(1 + i, iArr.length) < 0;

            if (flag) {
                // 1) Loop Predicate + Template Assertion Predicate
                // 2) Loop Peeling: Create IAP with same condition as initializedAssertionPredicateBool -> can be shared.
                //                  The IAP is on a loop-exit and therefore outside the loop.
                // 3) After CCP: C2 knows that two == 2 and loop is removed.
                for (short j = 0; j < two; j++) {
                    iArr[i + j] = 34; // Hoisted in Loop Predication
                    if (flag2) {
                        break;
                    }
                }
                break;
            }

            // Use Bool inside i-loop such that when applying Loop Peeling for i-loop, ctrl of Bool is inside loop and
            // OpaqueInitializedAssertionPredicate of IAP is outside of i-loop.
            if (initializedAssertionPredicateBool) {
                iFld = 3;
            }
        }
    }

    // Same as testOpaqueOutsideLoop() but we crash later when generating the Mach graph due to wrongly having an If
    // with a Phi input instead of: If <- Bool <- CmpU <- [x, Phi]. Found by fuzzing.
    static void testOpaqueOutsideLoop8333644() {
        int a = 3, b = 7;
        boolean bArr[] = new boolean[1];
        for (int i = 1; i < 122; i++) {
            float f = 1.729F;
            while (++a < 7) {
                iArr[a] *= lFld;
                switch (i) {
                    case 26:
                        for (; b < 1; ) {}
                    case 27:
                        iArr[1] = 9;
                    case 28:
                        break;
                    case 33:
                        iArr[1] = a;
                        break;
                    case 35:
                        lFld = b;
                        break;
                    default:
                        ;
                }
            }
        }
    }

    // Similar to testOpaqueOutside loop but Opaque is now also inside loop.
    // [If]->OpaqueInitializedAssertionPredicate->Bool->Cmp, []: Inside Loop, other nodes outside.
    static void testOpaqueInsideIfOutsideLoop() {
        int two = 100;
        int limit = 2;
        for (; limit < 4; limit *= 2);
        for (int i = 2; i < limit; i++) {
            two = 2;
        }

        for (short i = 3; i < 30; i*=2) {
            if (two == 100) {
                // Before CCP: Uninlined method call prevents peeling.
                // After CCP: C2 knows that two == 2 and we remove this call which enables Loop Peeling for i-loop.
                dontInline();
            }

            // 1) Loop Predicate + Template Assertion Predicate
            // 2) Loop Peeling: Create IAP with same condition as initializedAssertionPredicateBool -> can be shared.
            //                  The IAP is on a loop-exit and therefore outside the loop.
            // 3) After CCP: C2 knows that two == 2 and loop is removed.
            for (short j = 0; j < two; j++) {
                iArr[i + j] = 34; // Hoisted in Loop Predication
                if (flag2) {
                    break;
                }
            }

            if (flag) {
                // Same loop as above. We create the same IAP which can share the same OpaqueInitializedAssertionPredicate.
                // Therefore, the OpaqueInitializedAssertionPredicate is inside the loop while this If is outside the loop.
                // At Loop Peeling, we clone the Opaque node and create a Phi to merge both loop versions into the IAP If. In
                // clone_loop_handle_data_uses(), we add the If for the IAP to the split if set in (). Later, we
                // process its input phi with their OpaqueInitializedAssertionPredicate inputs.
                for (short j = 0; j < two; j++) {
                    iArr[i + j] = 34; // Hoisted in Loop Predication
                    if (flag2) {
                        break;
                    }
                }
                break;
            }
        }
    }

    // Not inlined
    static void dontInline() {}

    static class A {
        int iFld;
        A(int i) {
            this.iFld = i;
        }
    }
}
