/*
 * Copyright (c) 2023, Red Hat, Inc. All rights reserved.
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
 * bug 8312980
 * @summary C2: "malformed control flow" created during incremental inlining
 * @requires vm.compiler2.enabled
 * @run main/othervm -XX:-TieredCompilation -XX:-BackgroundCompilation -XX:+IgnoreUnrecognizedVMOptions -XX:+AlwaysIncrementalInline TestReplacedNodesAfterLateInline
 */

public class TestReplacedNodesAfterLateInline {
    private static B fieldB = new B();
    private static A fieldA = new A();
    private static volatile int volatileField;

    public static void main(String[] args) {
        for (int i = 0; i < 20_000; i++) {
            test(false, fieldA, true);
            test(false, fieldA, false);
            testHelper(fieldB);
            testHelper2(fieldB, true, false, true);
            testHelper2(fieldA, false, true, true);
            continue;
        }
    }

    private static int test(boolean flag, Object o, boolean flag2) {
        if (o == null) {
        }
        if (flag2) {
            return testHelper2(o, true, true, flag);
        }
        return ((A) o).field;
    }

    private static int testHelper2(Object o, boolean flag, boolean flag2, boolean flag3) {
        if (flag3) {
            if (flag) {
                testHelper(o);
            }
            if (flag2) {
                return ((A) o).field;
            }
        }
        volatileField = 42;
        return volatileField;
    }

    private static void testHelper(Object o) {
        B b = (B)o;
    }

    private static class A {
        public int field;
    }

    private static class B {
    }
}
