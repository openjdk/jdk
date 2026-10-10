/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.internal.vm.annotation.NullRestricted;

import jdk.test.lib.Asserts;

public class NullableFlatFieldApp {

  @LooselyConsistentValue
  static value class Value0 {
    long l;
    int i;
    short s;
    byte b;

    Value0() {
      l = 0;
      i = 0;
      s = 0;
      b = 0;
    }

    Value0(long l0, int i0, short s0, byte b0) {
      l = l0;
      i = i0;
      s = s0;
      b = b0;
    }
  }

  static class Container0 {
    Value0 val;
    long l;
    int i;
  }

  // Container0 had a external null marker located just between two Java fields,
  // and test0 checks that updating the null marker doesn't corrupt
  // the surrounding fields and vice-versa.
  static void test0() {
    Container0 c = new Container0();
    Asserts.assertNull(c.val);
    Asserts.assertEquals(c.l, 0L);
    Asserts.assertEquals(c.i, 0);
    c.l = -1L;
    c.i = -1;
    Asserts.assertNull(c.val);
    Value0 v = new Value0(-1L, -1, (short)-1, (byte)-1);
    c.val = v;
    Asserts.assertEquals(c.l, -1L);
    Asserts.assertEquals(c.i, -1);
    Value0 vr = c.val;
    Asserts.assertEquals(vr.l, -1L);
    Asserts.assertEquals(vr.i, -1);
    Asserts.assertEquals(vr.s, (short)-1);
    Asserts.assertEquals(vr.b, (byte)-1);
    c.val = null;
    Asserts.assertEquals(c.l, -1L);
    Asserts.assertEquals(c.i, -1);
  }

  @LooselyConsistentValue
  static value class Value1a {
    long l;
    short s;
    byte b;

    Value1a() {
      l = 0;
      s = 0;
      b = 0;
    }

    Value1a(long l0, short s0, byte b0) {
      l = l0;
      s = s0;
      b = b0;
    }
  }

  @LooselyConsistentValue
  static value class Value1b {
    @NullRestricted
    Value1a vala;
    long l;
    int i;

    Value1b() {
      vala = new Value1a();
      l = 1L;
      i = 1;
    }

    Value1b(Value1a v0, long l0, int i0) {
      vala = v0;
      l = l0;
      i = i0;
    }
  }

  static class Container1 {
    Value1b valb;
  }

  // Container1 has a nullable flat field with an internal null marker,
  // test1 checks that updating the null marker doesn't corrupt the
  // flat field's values
  static void test1() {
    Container1 c = new Container1();
    Asserts.assertNull(c.valb);
    Value1a va = new Value1a(-1L, (short)-1, (byte)-1);
    Asserts.assertEquals(va.l, -1L);
    Asserts.assertEquals(va.s, (short)-1);
    Asserts.assertEquals(va.b, (byte)-1);
    Value1b vb = new Value1b(va, -1L, -1);
    Asserts.assertNotNull(vb.vala);
    Asserts.assertEquals(vb.vala.l, -1L);
    Asserts.assertEquals(vb.vala.s, (short)-1);
    Asserts.assertEquals(vb.vala.b, (byte)-1);
    c.valb = vb;
    Asserts.assertNotNull(c.valb);
    Asserts.assertEquals(c.valb, vb);
    Asserts.assertEquals(c.valb.vala, va);
    Asserts.assertEquals(c.valb.l, -1L);
    Asserts.assertEquals(c.valb.i, -1);
    c.valb = null;
    Asserts.assertNull(c.valb);
  }

  @LooselyConsistentValue
  static value class Value2a {
    long l;
    int i;
    byte b;
    Value2a() {
      l = 0;
      i = 0;
      b = 0;
    }
    Value2a(long l0, int i0, byte b0) {
      l = l0;
      i = i0;
      b = b0;
    }
  }

  @LooselyConsistentValue
  static value class Value2b {
    long l;
    Value2b() {
      l = 0;
    }
    Value2b(long l0) {
      l = l0;
    }
  }

  static class Container2 {
    Value2a vala;
    Value2b valb0;
    Value2b valb1;
    int i;
  }

  // Container2 has 3 contiguous null markers,
  // test2 checks that updating a null marker doesn't affect the other markers
  public static void test2() {
    Container2 c = new Container2();
    Asserts.assertNull(c.vala);
    Asserts.assertNull(c.valb0);
    Asserts.assertNull(c.valb1);
    Value2a va = new Value2a(-1L, -1, (byte)-1);
    c.vala = va;
    Asserts.assertNotNull(c.vala);
    Asserts.assertNull(c.valb0);
    Asserts.assertNull(c.valb1);
    c.vala = null;
    Asserts.assertNull(c.vala);
    Asserts.assertNull(c.valb0);
    Asserts.assertNull(c.valb1);
    Value2b vb = new Value2b(-1L);
    c.valb0 = vb;
    Asserts.assertNull(c.vala);
    Asserts.assertNotNull(c.valb0);
    Asserts.assertNull(c.valb1);
    c.valb0 = null;
    Asserts.assertNull(c.vala);
    Asserts.assertNull(c.valb0);
    Asserts.assertNull(c.valb1);
    c.valb1 = vb;
    Asserts.assertNull(c.vala);
    Asserts.assertNull(c.valb0);
    Asserts.assertNotNull(c.valb1);
    c.valb1 = null;
    Asserts.assertNull(c.vala);
    Asserts.assertNull(c.valb0);
    Asserts.assertNull(c.valb1);
    c.vala = va;
    c.valb0 = vb;
    c.valb1 = vb;
    Asserts.assertNotNull(c.vala);
    Asserts.assertNotNull(c.valb0);
    Asserts.assertNotNull(c.valb1);
    c.vala = null;
    Asserts.assertNull(c.vala);
    Asserts.assertNotNull(c.valb0);
    Asserts.assertNotNull(c.valb1);
    c.vala = va;
    Asserts.assertNotNull(c.vala);
    Asserts.assertNotNull(c.valb0);
    Asserts.assertNotNull(c.valb1);
    c.valb0 = null;
    Asserts.assertNotNull(c.vala);
    Asserts.assertNull(c.valb0);
    Asserts.assertNotNull(c.valb1);
    c.valb0 = vb;
    Asserts.assertNotNull(c.vala);
    Asserts.assertNotNull(c.valb0);
    Asserts.assertNotNull(c.valb1);
    c.valb1 = null;
    Asserts.assertNotNull(c.vala);
    Asserts.assertNotNull(c.valb0);
    Asserts.assertNull(c.valb1);
    c.valb1 = vb;
    Asserts.assertNotNull(c.vala);
    Asserts.assertNotNull(c.valb0);
    Asserts.assertNotNull(c.valb1);
  }

  @LooselyConsistentValue
  static value class Value3 {
    int i = 0;
  }

  static class Container3 {
    Value3 val;
  }

  static Container3 getNullContainer3() {
    return null;
  }

  public static void test3() {
    NullPointerException npe = null;
    Container3 c = getNullContainer3();
    try {
      Value3 v = c.val;
    } catch(NullPointerException e) {
      npe = e;
    }
    Asserts.assertNotNull(npe);
  }

  public static void main(String[] args) {
    // All tests are run twice to exercise both the unresolved bytecodes and the rewritten ones
    for (int i = 0; i < 2; i++) {
      System.out.println("Pass " + i);
      test0();
      test1();
      test2();
      test3();
    }
  }
}
