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
 */

/*
 * @test
 * @bug 8307814
 * @summary Verify correct LineNumberTable is generated for unrolled record patterns.
 * @library /tools/lib /tools/javac/lib ../lib
 * @enablePreview
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.util
 *          java.base/jdk.internal.classfile.impl
 * @build toolbox.ToolBox InMemoryFileManager TestBase
 * @build LineNumberTestBase TestCase
 * @run main MultipleRecordPatterns
 */

import java.util.List;

public class MultipleRecordPatterns extends LineNumberTestBase {
    public static void main(String[] args) throws Exception {
        new MultipleRecordPatterns().test();
    }

    public void test() throws Exception {
        test(List.of(TEST_CASE));
    }

    private static final TestCase[] TEST_CASE = new TestCase[] {
        new TestCase("""
                     public class Patterns {                     // 1
                         private void test1(Object o) {          // 2
                             if (o instanceof R(var v)) {        // 3
                                 System.err.println(v);          // 4
                             }                                   // 5
                         }                                       // 6
                         private void test2(Object o) {          // 7
                             if (o instanceof R(var v)) {        // 8
                                 System.err.println(v);          // 9
                             }                                   //10
                         }                                       //11
                         record R(int i) {}                      //12
                     }                                           //13
                     """,
                     "Patterns",
                     new TestCase.MethodData("test1", List.of(3, 4, 6), true),
                     new TestCase.MethodData("test2", List.of(8, 9, 11), true))
    };

}
