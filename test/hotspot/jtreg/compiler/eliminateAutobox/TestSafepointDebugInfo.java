/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @key randomness
 * @bug 8276112
 * @summary Verify consistency of safepoint debug info when boxes are scalar
 *          replaced during incremental inlining.
 * @library /test/lib
 * @run main/othervm -Xbatch -XX:+IgnoreUnrecognizedVMOptions -XX:+AlwaysIncrementalInline
 *                   -XX:CompileCommand=compileonly,compiler.eliminateAutobox.TestSafepointDebugInfo::test*
 *                   compiler.eliminateAutobox.TestSafepointDebugInfo
 */

package compiler.eliminateAutobox;

import java.util.Random;

import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

public class TestSafepointDebugInfo {

    private static final Random random = Utils.getRandomInstance();

    static Integer fBox;

    public static Integer helper(int i) {
        return Integer.valueOf(i);
    }

    // assert(local) failed: use _top instead of null
    public static int test1(int i) {
        Integer box = helper(i);
        fBox = Integer.valueOf(i);
        return box.intValue();
    }

    // Wrong execution, same types
    public static int test2(int i1, int i2) {
        Integer box1 = helper(i1);
        Integer box2 = Integer.valueOf(i2);
        fBox = Integer.valueOf(i1);
        return box1.intValue() + box2.intValue();
    }

    // Wrong execution, different types
    public static long test3(int i1, long i2) {
        Integer box1 = helper(i1);
        Long box2 = Long.valueOf(i2);
        fBox = Integer.valueOf(i1);
        return box1.intValue() + box2.longValue();
    }

    // assert(i < _max) failed: oob: i=16, _max=16
    public static int test4(int i1, int i2) {
        Integer box1 = helper(i1);
        Integer box2 = helper(i2);
        fBox = Integer.valueOf(i1);
        return box1.intValue() + box2.intValue();
    }

    public static Integer test5_helper(int i1, int i2) {
        Integer box1 = helper(i1);
        Integer box2 = helper(i2);
        fBox = Integer.valueOf(i1);
        return box1.intValue() + box2.intValue();
    }

    // assert(local) failed: use _top instead of null
    // Variant with deeper inlining
    public static int test5(int i1, int i2) {
        return test5_helper(i1, i2);
    }

    public static int test6_helper(int i1, int i2) {
        Integer box = helper(i1);
        fBox = Integer.valueOf(i2);
        return box.intValue();
    }

    // Wrong execution, variant with more arguments
    public static int test6(int i1, int i2, int i3, int i4) {
        Integer box1 = helper(i1);
        Integer box2 = helper(i2);
        int res = test6_helper(i3, i4);
        res += box1.intValue() + box2.intValue();
        return res;
    }

    public static void main(String[] args) {
        // Warmup
        for (int i = 0; i < 100_000; ++i) {
            int val = (i % 10);
            Asserts.assertEquals(test1(val), val);
            Asserts.assertEquals(fBox, val);
            Asserts.assertEquals(test2(val, val), 2*val);
            Asserts.assertEquals(fBox, val);
            Asserts.assertEquals(test3(val, val), 2L*val);
            Asserts.assertEquals(fBox, val);
            Asserts.assertEquals(test4(val, val), 2*val);
            Asserts.assertEquals(fBox, val);
            Asserts.assertEquals(test5(val, val), 2*val);
            Asserts.assertEquals(fBox, val);
            Asserts.assertEquals(test6(val, val, val, val), 3*val);
            Asserts.assertEquals(fBox, val);
        }

        // Trigger deoptimization by choosing a value that does not
        // fit in the Integer cache and check the result.
        int val = 4000;
        Asserts.assertEquals(test1(val), val);
        switch (random.nextInt(3)) {
            case 0:
                Asserts.assertEquals(test2(val, 1), val + 1);
                Asserts.assertEquals(fBox, val);
                Asserts.assertEquals(test3(val, 1), (long)val + 1);
                Asserts.assertEquals(fBox, val);
                Asserts.assertEquals(test4(val, 1), val + 1);
                Asserts.assertEquals(fBox, val);
                Asserts.assertEquals(test5(val, 1), val + 1);
                Asserts.assertEquals(fBox, val);
                Asserts.assertEquals(test6(val, 1, 2, 3), val + 3);
                Asserts.assertEquals(fBox, 3);
                break;
           case 1:
                Asserts.assertEquals(test2(1, val), val + 1);
                Asserts.assertEquals(fBox, 1);
                Asserts.assertEquals(test3(1, val), (long)val + 1);
                Asserts.assertEquals(fBox, 1);
                Asserts.assertEquals(test4(1, val), val + 1);
                Asserts.assertEquals(fBox, 1);
                Asserts.assertEquals(test5(1, val), val + 1);
                Asserts.assertEquals(fBox, 1);
                Asserts.assertEquals(test6(1, val, 2, 3), val + 3);
                Asserts.assertEquals(fBox, 3);
                break;
           case 2:
                Asserts.assertEquals(test2(1, 2), 3);
                Asserts.assertEquals(fBox, 1);
                Asserts.assertEquals(test3(1, 2), 3L);
                Asserts.assertEquals(fBox, 1);
                Asserts.assertEquals(test4(1, 2), 3);
                Asserts.assertEquals(fBox, 1);
                Asserts.assertEquals(test5(1, 2), 3);
                Asserts.assertEquals(fBox, 1);
                Asserts.assertEquals(test6(1, 2, 3, val), 6);
                Asserts.assertEquals(fBox, val);
                break;
        }
    }
}
