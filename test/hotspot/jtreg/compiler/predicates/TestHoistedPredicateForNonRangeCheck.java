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
 * @run main/othervm -Xcomp -XX:CompileCommand=compileonly,compiler.predicates.TestHoistedPredicateForNonRangeCheck::test
 *                   compiler.predicates.TestHoistedPredicateForNonRangeCheck
 * @run main/othervm -Xcomp -XX:CompileCommand=compileonly,compiler.predicates.TestHoistedPredicateForNonRangeCheck::test
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

    public static void main(String[] args) {
        if (args.length == 0) {
            Integer.compareUnsigned(34, 34); // Ensure Integer class is loaded and we do not emit a trap inside test() for it.

            for (int i = 0; i < 2; i++) {
                iFld = 0;
                iFld2 = 0;
                test();
                Asserts.assertEQ(iFld, 901, "wrong value");
                Asserts.assertEQ(iFld2, 100, "wrong value");
            }
        } else {
            boolean flag = false;
            for (int i = 0; i < 10000; i++) {
                testCalendar1();
                testCalendar2(flag);
            }
        }
    }

    public static void test() {
        // (Inverted) Hoisted Predicate checks lower and upper bound: -1 < 0 && 1000 >= 100 -> always true and the predicate is removed
        // while template assertion predicates are kept. When splitting this loop further, we insert an assertion predicate which
        // fails for i = 0 and we halt. When not splitting this loop (with LoopMaxUnroll=0), we have a wrong execution due
        // to never executing iFld2++ (we removed the check and the branch with the trap).
        for (int i = -1; i < 1000; i++) {
            if (Integer.compareUnsigned(i, 100) < 0) { // Loop Predication creates a Hoisted Range Check Predicate due to trap with Float.isNan().
                iFld2++;
                Float.isNaN(34); // Float class is unloaded with -Xcomp -> inserts trap
            } else {
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
