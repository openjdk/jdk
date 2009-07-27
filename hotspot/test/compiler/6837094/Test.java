/*
 * Copyright 2009 Google Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

/**
 * @test
 * @bug 6837094
 * @summary False positive for "meet not symmetric" failure
 *
 * @run main/othervm -Xbatch -XX:CompileOnly=Test.collectIs,Test$Factory$1.getArray,Test$Factory$2.getArray Test
 */

import java.util.Set;
import java.util.HashSet;

public class Test {

  private interface Factory<M extends Interface> {
    Factory<Child0> Zero = new Factory<Child0>() {
      public Child0[] getArray() { return new Child0[1]; }
    };

    Factory<Child1> One = new Factory<Child1>() {
      public Child1[] getArray() { return new Child1[1]; }
    };

    M[] getArray();
  }

  /**
   * C2 asserts when compiling this method. Bimorphic inlining happens at
   * getArray call site. A Phi in the catch block tries to join the meet type
   * from he inline site (Parent[]) with the type expected by CI (Interface[]).
   *
   * C2 throws an assert when it doesn't need to.
   */
  private static <I extends Interface> void collectIs(
      Factory<I> factory, Set<Interface> s) {
    for (I i : factory.getArray()) {
      try {
        s.add(i);
      } catch (Exception e) {
      }
    }
  }

  static public void main(String argv[]) {
    Set<Interface> s = new HashSet();

    for (int i = 0; i < 25000; i++) {
      collectIs(Factory.Zero, s);
      collectIs(Factory.One, s);
    }
  }
}

/**
 * Establish necessary class hierarchy
 */

interface Interface {
}

class Parent {
}

class Child0 extends Parent implements Interface {
}

class Child1 extends Parent implements Interface {
}

class Child2 extends Parent implements Interface {
}
