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
 * @summary the receiver parameter has the type of the surrounding class
 * @author Werner Dietl
 * @compile/fail/ref=WrongType.out -XDrawDiagnostics WrongType.java
 */

@interface A {}

class WrongType {
  Object f;

  void good1(@A WrongType this) {}

  void good2(@A WrongType this) {
    this.f = null;
    Object o = this.f;
  }

  void bad1(@A Object this) {}

  void bad2(@A Object this) {
    this.f = null;
    Object o = this.f;
  }

  void wow(@A XYZ this) {
    this.f = null;
  }

  class Inner {
    void good1(@A Inner this) {}
    void good2(@A WrongType.Inner this) {}

    void outerOnly(@A WrongType this) {}
    void wrongInner(@A Object this) {}
    void badOuter(@A Outer.Inner this) {}
    void badInner(@A WrongType.XY this) {}
  }

  class Generics<X> {
    <Y> void m(Generics<Y> this) {}
  }
}
