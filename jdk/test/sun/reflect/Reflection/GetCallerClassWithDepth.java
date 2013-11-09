/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8025799
 * @summary sun.reflect.Reflection.getCallerClass(int)
 * @run main GetCallerClassWithDepth
 */

public class GetCallerClassWithDepth {
    public static void main(String[] args) throws Exception {
        Class<?> c = Test.test();
        assertEquals(c, GetCallerClassWithDepth.class);
        Class<?> caller = Test.caller();
        assertEquals(caller, GetCallerClassWithDepth.class);
        Test.selfTest();

        try {
            sun.reflect.Reflection.getCallerClass(-1);
            throw new RuntimeException("getCallerClass(-1) should fail");
        } catch (Error e) {
            System.out.println("Expected: " + e.getMessage());
        }
    }

    public Class<?> getCallerClass() {
        // 0: Reflection 1: getCallerClass 2: Test.test 3: main
        return sun.reflect.Reflection.getCallerClass(3);
    }

    static void assertEquals(Class<?> c, Class<?> expected) {
        if (c != expected) {
            throw new RuntimeException("Incorrect caller: " + c);
        }
    }

    static class Test {
        // Returns the caller of this method
        public static Class<?> test() {
            return new GetCallerClassWithDepth().getCallerClass();
        }

        // Returns the caller of this method
        public static Class<?> caller() {
            // 0: Reflection 1: Test.caller 2: main
            return sun.reflect.Reflection.getCallerClass(2);
        }
        public static void selfTest() {
            // 0: Reflection 1: Test.selfTest
            Class<?> c = sun.reflect.Reflection.getCallerClass(1);
            assertEquals(c, Test.class);
            Inner1.deep();
        }

        static class Inner1 {
            static void deep() {
                 deeper();
            }
            static void deeper() {
                 Inner2.deepest();
            }
            static class Inner2 {
                static void deepest() {
                    // 0: Reflection 1: deepest 2: deeper 3: deep 4: Test.selfTest
                    Class<?> c = sun.reflect.Reflection.getCallerClass(4);
                    assertEquals(c, Test.class);
                }
            }
        }
    }
}
