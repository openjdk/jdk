/*
 * Copyright (c) 2008, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8006775
 * @summary the receiver parameter and static methods/classes
 * @author Werner Dietl
 * @compile/fail/ref=StaticThings.out -XDrawDiagnostics StaticThings.java
 */
class Test {
  // bad
  static void test1(Test this) {}

  // bad
  static Object test2(Test this) { return null; }

  class Nested1 {
    // good
    void test3a(Nested1 this) {}
    // good
    void test3b(Test.Nested1 this) {}
    // No static methods
    // static void test3c(Nested1 this) {}
  }
  static class Nested2 {
    // good
    void test4a(Nested2 this) {}
    // good
    void test4b(Test.Nested2 this) {}
    // bad
    static void test4c(Nested2 this) {}
    // bad
    static void test4d(Test.Nested2 this) {}
  }
}
