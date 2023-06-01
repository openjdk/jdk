/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8307683
 * @library /test/lib /
 * @requires vm.compiler2.enabled
 * @summary Tests that IfNode is not wrongly chosen as range check by Loop Predication leading to crashes and wrong executions.
 * @run main/othervm -Xcomp -XX:CompileCommand=compileonly,compiler.predicates.TestHoistedPredicateForNonRangeCheck::test*
 *                   compiler.predicates.TestHoistedPredicateForNonRangeCheck
 * @run main/othervm -Xcomp -XX:CompileCommand=compileonly,compiler.predicates.TestHoistedPredicateForNonRangeCheck::test*
 *                   -XX:LoopMaxUnroll=0 compiler.predicates.TestHoistedPredicateForNonRangeCheck
 */

/*
 * @test
 * @bug 8307683
 * @library /test/lib /
 * @summary Tests that IfNode is not wrongly chosen as range check by Loop Predication leading to crashes and wrong executions.
 * @run main/othervm -Xbatch compiler.predicates.TestHoistedPredicateForNonRangeCheck calendar
 */

package compiler.predicates;

import jdk.test.lib.Asserts;

import java.util.Calendar;
import java.util.Date;


public class TestHoistedPredicateForNonRangeCheck {
    static int iFld, iFld2;
    static int[] iArr = new int[100];

    public static void main(String[] args) {
        if (args.length == 0) {
            Integer.compareUnsigned(34, 34); // Ensure Integer class is loaded and we do not emit a trap inside test() for it.

            for (int i = 0; i < 2; i++) {
                iFld = 0;
                iFld2 = 0;
                test();
                Asserts.assertEQ(iFld, 3604, "wrong value");
                Asserts.assertEQ(iFld2, 400, "wrong value");
            }

            for (int i = 0; i < 2000; i++) {
                iFld = -100;
                testRangeCheckNode();
            }
            iFld = -1;
            iFld2 = 0;
            testRangeCheckNode();
            Asserts.assertEQ(iFld2, 36, "wrong value");
        } else {
            boolean flag = false;
            for (int i = 0; i < 10000; i++) {
                testCalendar1();
                testCalendar2(flag);
            }
        }
    }

    public static void test() {
        for (int i = -1; i < 1000; i++) {
            // We hoist this check and insert a Hoisted Predicate for the lower and upper bound:
            // -1 >=u 100 && 1000 >= u 100 -> always true and the predicates are removed.
            // Template Assertion Predicates, however, are kept. When splitting this loop further, we insert an Assertion
            // Predicate which fails for i = 0 and we halt.
            // When not splitting this loop (with LoopMaxUnroll=0), we have a wrong execution due to never executing
            // iFld2++ (we remove the check and the branch with the trap when creating the Hoisted Predicates).
            if (Integer.compareUnsigned(i, 100) < 0) {
                iFld2++;
                Float.isNaN(34); // Float class is unloaded with -Xcomp -> inserts trap
            } else {
                iFld++;
            }

            // Same but flipped condition and moved trap to other branch - result is the same.
            if (Integer.compareUnsigned(i, 100) >= 0) { // Loop Predication creates a Hoisted Range Check Predicate due to trap with Float.isNan().
                iFld++;
            } else {
                iFld2++;
                Float.isNaN(34); // Float class is unloaded with -Xcomp -> inserts trap
            }

            // Same but with LoadRangeNode.
            if (Integer.compareUnsigned(i, iArr.length) >= 0) { // Loop Predication creates a Hoisted Range Check Predicate due to trap with Float.isNan().
                iFld++;
            } else {
                iFld2++;
                Float.isNaN(34); // Float class is unloaded with -Xcomp -> inserts trap
            }

            // Same but with LoadRangeNode and flipped condition and moved trap to other branch - result is the same.
            if (Integer.compareUnsigned(i, iArr.length) >= 0) { // Loop Predication creates a Hoisted Range Check Predicate due to trap with Float.isNan().
                iFld++;
            } else {
                iFld2++;
                Float.isNaN(34); // Float class is unloaded with -Xcomp -> inserts trap
            }
        }
    }

    static void testRangeCheckNode() {
        int array[] = new int[34];
        // Hoisted Range Check Predicate with flipped bool because trap is on success proj and no trap on false proj due
        // to catching exception:
        // iFld >=u 34 && iFld+36 >=u 34
        // This is always false for first 2000 iterations where, initially, iFld = -100
        // It is still true in the last iteration where, initially, iFld = -1. But suddenly, in the second iteration,
        // where iFld = 0, we would take the true projection for the first time - but we removed that branch when
        // creating the Hoisted Range Check Predicate. We therefore run into the same problem as with test(): We either
        // halt due to Assertion Predicates catching this case or we have a wrong execution (iFld2 never updated).
        for (int i = 0; i < 37; i++) {
            try {
                array[iFld] = 34; // Normal RangeCheckNode
                iFld2++;
                Math.ceil(34); // Never taken and unloaded -> trap
            } catch (Exception e) {
                // False Proj of RangeCheckNode
                iFld++;
            }
        }
    }

    // Reported in JDK-8307683
    static void testCalendar1() {
        Calendar c = Calendar.getInstance();
        c.setLenient(false);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.getTime();
    }

    // Reported in JDK-8307978
    static void testCalendar2(boolean flag) {
        flag = !flag;
        Calendar timespan = removeTime(new Date(), flag);
        timespan.getTime();
    }

    static Calendar removeTime(Date date, boolean flag) {
        Calendar calendar = Calendar.getInstance();
        if (flag) {
            calendar.setLenient(false);
        }
        calendar.setTime(date);
        calendar = removeTime(calendar);
        return calendar;
    }

    static Calendar removeTime(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar;
    }
}
