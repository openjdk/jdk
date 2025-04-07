/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8322996 8324839 8325467
 * @summary Ensure C2 can compile deeply nested synchronize statements.
 *          Exercises C2 register masks, in particular. We incrementally
 *          increase the level of nesting (up to 100) to trigger potential edge
 *          cases.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run main/othervm -XX:CompileCommand=compileonly,Test::test*
 *                   -Xcomp
 *                   -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+AbortVMOnCompilationFailure
 *                   compiler.locks.TestNestedSynchronize
 */

package compiler.locks;

import compiler.lib.compile_framework.*;
import java.util.ArrayList;

public class TestNestedSynchronize {
    static int min = 1;
    static int max = 100;
    static String test_class_name = "Test";

    // The below method generates a program of the form:
    //
    // public class Test {
    //     public static void test1() {
    //         synchronized (Test.class) {
    //
    //         }
    //     }
    //
    //
    //     public static void test2() {
    //         synchronized (Test.class) {
    //         synchronized (Test.class) {
    //
    //         }
    //         }
    //     }
    //
    //
    //     public static void test3() {
    //         synchronized (Test.class) {
    //         synchronized (Test.class) {
    //         synchronized (Test.class) {
    //
    //         }
    //         }
    //         }
    //     }
    //
    //     ...
    //
    //     public static void test99() {
    //         synchronized (Test.class) {
    //         synchronized (Test.class) {
    //         synchronized (Test.class) {
    //         ...
    //
    //         ...
    //         }
    //         }
    //         }
    //     }
    // }
    //
    // The above is a massive program. Therefore, we do not directly inline the
    // program in TestNestedSynchronize and instead compile and run it via the
    // CompileFramework.
    public static String generate_test() {
        ArrayList<String> methods = new ArrayList<String>();
        for (int i = min; i < max; i++) {
            String inner = "";
            for (int j = 0; j < i; j++) {
                inner = String.format("""
                          synchronized (%s.class) {
                  %s
                          }""", test_class_name, inner);
            }
            methods.add(String.format("""
                  public static void test%d() {
              %s
                  }
              """, i, inner));
        }
        String methods_string = String.join("\n\n", methods);
        String test_program = String.format("""
                public class %s {
                %s
                }""", test_class_name, methods_string);
        return test_program;
    }

    public static void main(String[] args) {
        CompileFramework comp = new CompileFramework();
        comp.addJavaSourceCode("Test", generate_test());
        comp.compile();
        for (int i = min; i < max; ++i) {
            comp.invoke("Test", "test" + i, new Object[] {});
        }
    }
}
