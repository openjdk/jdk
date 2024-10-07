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
 */
/**
 * @test
 * @bug 8314275
 * @summary Tests a line number table attribute for switch expression
 * @library /tools/lib /tools/javac/lib ../lib
 * @enablePreview
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.util
 *          java.base/jdk.internal.classfile.impl
 * @build toolbox.ToolBox InMemoryFileManager TestBase
 * @build LineNumberTestBase TestCase
 * @run main T8314275
 */
import java.util.List;
public class T8314275 extends LineNumberTestBase {
    public static void main(String[] args) throws Exception {
        new T8314275().test();
    }

    public void test() throws Exception {
        test(List.of(TEST_CASE));
    }

    private static final TestCase[] TEST_CASE = new TestCase[] {
        new TestCase("""
             public class T8314275Expression {                     // 1
                 private static double multiply(Integer i) {       // 2
                      double cr = 15;                              // 3
                      cr = switch (i) {                            // 4
                          case 1 -> cr * 1;                        // 5
                          case 2 -> cr * 2;                        // 6
                          default -> cr * 4;                       // 7
                      };                                           // 8
                      return cr;                                   // 9
                  }                                                //10
             }                                                     //11
             """,
            List.of(1, 3, 4, 5, 6, 7, 8, 9),
            "T8314275Expression"),
        new TestCase("""
             public class T8314275Statement {                      // 1
                 private static double multiply(Integer i) {       // 2
                      double cr = 15;                              // 3
                      switch (i) {                                 // 4
                          case 1: cr *= 1; break;                  // 5
                          case 2: cr *= 2; break;                  // 6
                          default: cr *= 4;                        // 7
                      };                                           // 8
                      return cr;                                   // 9
                  }                                                //10
             }                                                     //11
             """,
                List.of(1, 3, 4, 5, 6, 7, 9),
                "T8314275Statement")
    };
}