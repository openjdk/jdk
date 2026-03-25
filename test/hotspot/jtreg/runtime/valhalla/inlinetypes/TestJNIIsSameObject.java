/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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

package runtime.valhalla.inlinetypes;

import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.test.lib.Asserts;


/*
 * @test
 * @summary Test JNI IsSameObject semantic with inline types
 * @library /testlibrary /test/lib
 * @modules java.base/jdk.internal.vm.annotation
 * @enablePreview
 * @compile TestJNIIsSameObject.java
 * @run main/othervm/native runtime.valhalla.inlinetypes.TestJNIIsSameObject
 */
public class TestJNIIsSameObject {
  @LooselyConsistentValue
  static value class Value {
    int i;

    public Value(int i) {
      this.i = i;
    }
  }
  native static boolean isSameObject(Object o0, Object o1);

  static {
    System.loadLibrary("JNIIsSameObject");
  }

  public static void main(String[] args) {
    // Same value in different instances
    Value v0 = new Value(3);
    Value v1 = new Value(3);
    Asserts.assertTrue(isSameObject(v0, v1));

    // Different values
    Value v2 = new Value(4);
    Asserts.assertFalse(isSameObject(v0, v2));

    // Same object
    TestJNIIsSameObject t0 = new TestJNIIsSameObject();
    Object o = t0;
    Asserts.assertTrue(isSameObject(t0, o));

    // Different objects
    TestJNIIsSameObject t1 = new TestJNIIsSameObject();
    Asserts.assertFalse(isSameObject(t0, t1));

    // Comparing against null
    Asserts.assertFalse(isSameObject(v0, null));
    Asserts.assertFalse(isSameObject(null, v0));
    Asserts.assertFalse(isSameObject(t0, null));
    Asserts.assertFalse(isSameObject(null, t0));

    // Object vs inline
    Asserts.assertFalse(isSameObject(v0, t0));
    Asserts.assertFalse(isSameObject(t0, v0));

  }
}
