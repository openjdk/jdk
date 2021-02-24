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

// Create a class to load inside the loader instance, that will load
// A through a constant pool reference.
// Through 2 different constant pool references
class CP1 {
  void foo(boolean okSuper) throws Exception {
      System.out.println("CP1.foo()");
      try {
          Class<?> a = A.class;
          Object obj = a.getConstructor(boolean.class).newInstance(okSuper);
          if (!okSuper) {
              throw new RuntimeException("Should throw CCE here");
          }
      } catch (Throwable e) {
          System.out.println("Exception is caught: " + e);
          if (okSuper || !(e instanceof java.lang.ClassCircularityError)) {
              throw new RuntimeException("Unexpected exception");
          }
      }
  }
}

// This class has a constant pool reference to A also but will load
// the second version of the class if CCE is thrown while loading the
// first version of the class.  Otherwise if okSuper, will load A extends B
class CP2 {
  void foo(boolean okSuper) throws Exception {
      System.out.println("CP2.foo()");
      try {
          Class<?> a = A.class;
          Object obj = a.getConstructor(boolean.class).newInstance(okSuper);
      } catch (Throwable e) {
          System.out.println("Exception is caught: " + e);
          throw new RuntimeException("Unexpected exception");
      }
  }
}

public class ClassInLoader {
  public ClassInLoader(boolean okSuper) throws Exception {
    System.out.println("ClassInLoader");
    CP1 c1 = new CP1();
    c1.foo(okSuper);
    CP2 c2 = new CP2();
    c2.foo(okSuper);
  }
}
