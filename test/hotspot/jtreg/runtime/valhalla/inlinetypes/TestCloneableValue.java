/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @test TestCloneableValue
 * @library /test/lib
 * @enablePreview
 * @compile TestCloneableValue.java
 * @run main runtime.valhalla.inlinetypes.TestCloneableValue
 */

 package runtime.valhalla.inlinetypes;

 import jdk.test.lib.Asserts;

 import java.util.ArrayList;

 public class TestCloneableValue  {

    static value class SimpleValue implements Cloneable {
      int i;
      double j;

      public SimpleValue() {
        i = 42;
        j = Math.E;
      }

      @Override
      public Object clone() throws CloneNotSupportedException {
        return super.clone(); // delegate to Object's method performing a shallow copy
      }
     }

    static value class NotSoSimpleValue implements Cloneable {
      ArrayList list;

      public NotSoSimpleValue() {
        list = new ArrayList<>();
      }

      private NotSoSimpleValue(ArrayList l) {
        list = l;
      }

      @Override
      public Object clone() throws CloneNotSupportedException {
        return new NotSoSimpleValue((ArrayList)list.clone());
      }
    }

     public static void main(String[] args) {
      var sv = new SimpleValue();
      try {
        var c1 = sv.clone();
        Asserts.assertEQ(sv, c1);
        Asserts.assertEQ(sv.hashCode(), c1.hashCode());
      } catch(CloneNotSupportedException e) {
        Asserts.fail("Unexpected exception", e);
      }

      var nssv = new NotSoSimpleValue();
      try {
        var c2 = nssv.clone();
        Asserts.assertNE(nssv, c2);
        Asserts.assertNE(nssv.hashCode(), c2.hashCode());
      } catch(CloneNotSupportedException e) {
        Asserts.fail("Unexpected exception", e);
      }
     }
 }
