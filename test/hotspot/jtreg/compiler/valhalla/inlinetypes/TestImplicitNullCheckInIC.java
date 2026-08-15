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
 * @summary Test implicit exception handling in inline-cache check.
 * @bug 8375086
 * @library /test/lib
 * @enablePreview
 * @run main compiler.valhalla.inlinetypes.TestImplicitNullCheckInIC
 * @run main/othervm -Xbatch -XX:TieredStopAtLevel=1
 *                   -XX:CompileCommand=compileonly,*TestImplicitNullCheckInIC*::test*
 *                   -XX:CompileCommand=compileonly,*TestImplicitNullCheckInIC*::callee
 *                   -XX:CompileCommand=compileonly,java.lang.Integer::compareTo
 *                   compiler.valhalla.inlinetypes.TestImplicitNullCheckInIC
 */

package compiler.valhalla.inlinetypes;

public class TestImplicitNullCheckInIC {

    static interface MyInterface {
        int callee();
    }

    static value class MyValue implements MyInterface {
        int x;

        public MyValue(int x) {
            this.x = x;
        }

        public int callee() {
            return x;
        }
    }

    // Make sure that CHA does not report a single implementor
    // of MyInterface since that would disable the inline cache.
    static value class MyValue1 implements MyInterface {
        public int callee() {
            return 0;
        }
    }
    static MyValue1 tmp = new MyValue1();

    public int test(MyInterface obj) {
        return obj.callee(); // Call site will use an inline cache
    }

    // Test case extracted from original report
    static class OriginalTestcase<K> {
        public int test(Object obj1, Object obj2) {
            return ((Comparable<? super K>)obj1).compareTo((K)obj2);
        }
    }

    public static void main(String[] args) {
        TestImplicitNullCheckInIC t1 = new TestImplicitNullCheckInIC();
        OriginalTestcase<Integer> t2 = new OriginalTestcase<Integer>();

        // Warmup to trigger compilation and initialize inline cache
        for (int i = 0; i < 100_000; ++i) {
            t1.test(new MyValue(42));
            t2.test(42, 43);
        }

        try {
            t1.test(null);
            throw new RuntimeException("No NullPointerException thrown!");
        } catch (NullPointerException npe) {
            // Expected
        }

        try {
            t2.test(null, null);
            throw new RuntimeException("No NullPointerException thrown!");
        } catch (NullPointerException npe) {
            // Expected
        }
    }
}
