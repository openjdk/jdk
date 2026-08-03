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

/*
 * @test
 * @requires vm.gc.Z
 * @summary Ensures that large flat arrays with only primitives can be allocated
 *          and properly initialized by ZGC
 * @bug 8373858
 * @enablePreview
 * @library /test/lib
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main/othervm -Xmx1G
 *                   -XX:+UseZGC
 *                   runtime.valhalla.inlinetypes.FlatArrayLargePrimitiveOnly
 */

package runtime.valhalla.inlinetypes;

import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.LooselyConsistentValue;

import static jdk.test.lib.Asserts.*;

public class FlatArrayLargePrimitiveOnly {
  private static final int LARGE_ARRAY_SIZE = 16 * 1024;

  @LooselyConsistentValue
  static value class LooseIntegers {
    int i0 = 0;
    int i1 = 0;
    int i2 = 0;
    int i3 = 0;
  }

  public static void main(String[] args) {
    Integer[] array = (Integer[])ValueClass.newNullableAtomicArray(Integer.class, LARGE_ARRAY_SIZE);
    if (array.length != LARGE_ARRAY_SIZE) {
      throw new RuntimeException("nullable atomic array not created properly");
  }

    LooseIntegers[] looseArray = (LooseIntegers[])ValueClass.newNullRestrictedNonAtomicArray(LooseIntegers.class, LARGE_ARRAY_SIZE, new LooseIntegers());
    if (looseArray.length != LARGE_ARRAY_SIZE) {
      throw new RuntimeException("null-restricted non-atomic array not created properly");
    }
  }
}
