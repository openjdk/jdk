/*
 * Copyright (c) 2018, Google LLC. All rights reserved.
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
 * @bug 8203679
 * @summary This is a negative regression test for an AssertionError in DeferredAttr.
 * @compile/fail/ref=MethodRefStuckParenthesized.out -XDrawDiagnostics MethodRefStuckParenthesized.java
 */

public abstract class MethodRefStuckParenthesized {

  interface I {
    String v();
  }

  interface J {
    String v();
  }

  abstract String v();

  abstract void f(I v);

  abstract <X extends J> J g(X x);

  void test() {
    f(g((this::v)));
  }
}
