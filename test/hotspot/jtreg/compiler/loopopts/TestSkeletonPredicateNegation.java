/*
 * Copyright (C) 2021 THL A29 Limited, a Tencent company. All rights reserved.
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8273277
 * @summary Skeleton predicates sometimes need to be negated
 * @run main compiler.loopopts.TestSkeletonPredicateNegation
 *
 */

package compiler.loopopts;

public class TestSkeletonPredicateNegation {
    public static int in0 = 2;

    public static void main(String[] args) {
        try {
            TestSkeletonPredicateNegation instance = new TestSkeletonPredicateNegation();
            for (int i = 0; i < 10000; ++i) {
                instance.mainTest(args);
            }
        } catch (Exception ex) {
            System.out.println(ex.getClass().getCanonicalName());
        } catch (OutOfMemoryError e) {
            System.out.println("OOM Error");
        }
    }

    public void mainTest (String[] args){
        long loa11[] = new long[19];

        for (long lo14 : loa11) {
            TestSkeletonPredicateNegation.in0 = -128;
            for (int i18 = 0; i18 < 13; i18++) {
                try {
                    loa11[TestSkeletonPredicateNegation.in0] %= 2275269548L;
                    Math.ceil(1374905370.2785515599);
                } catch (Exception a_e) {
                    TestSkeletonPredicateNegation.in0--;
                }
            }
        }
    }
}
