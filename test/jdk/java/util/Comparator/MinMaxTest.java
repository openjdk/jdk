/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8356995
 * @summary Comparator min/max method tests
 * @run junit MinMaxTest
 */

import org.junit.jupiter.api.Test;

import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

public class MinMaxTest {
  @Test
  public void testMin() {
    Comparator<String> c = Comparator.naturalOrder();
    assertEquals("a", c.min("a", "b"));
    assertEquals("a", c.min("b", "a"));
  }

  @Test
  public void testMax() {
    Comparator<String> c = Comparator.naturalOrder();
    assertEquals("b", c.max("a", "b"));
    assertEquals("b", c.max("b", "a"));
  }

  @Test
  public void testThrowsNPE() {
    Comparator<String> c = Comparator.naturalOrder();
    assertThrows(NullPointerException.class, () -> c.min(null, "a"));
    assertThrows(NullPointerException.class, () -> c.min("a", null));
    assertThrows(NullPointerException.class, () -> c.max(null, "a"));
    assertThrows(NullPointerException.class, () -> c.max("a", null));
  }

  @Test
  public void testThrowsCCE() {
    @SuppressWarnings("unchecked")
    Comparator<Object> c = (Comparator<Object>) (Comparator<?>)Comparator.naturalOrder();
    assertThrows(ClassCastException.class, () -> c.min(1, "a"));
    assertThrows(ClassCastException.class, () -> c.min("a", 1));
    assertThrows(ClassCastException.class, () -> c.max(1, "a"));
    assertThrows(ClassCastException.class, () -> c.max("a", 1));
  }

  @Test
  public void testEqualReturnFirst() {
    Comparator<Object> allEqual = (_, _) -> 0;
    Object o1 = new Object();
    Object o2 = new Object();
    assertSame(o1, allEqual.min(o1, o2));
    assertSame(o1, allEqual.max(o1, o2));
  }
}
