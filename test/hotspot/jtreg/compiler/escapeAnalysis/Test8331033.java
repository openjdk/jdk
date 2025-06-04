/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8331033
 * @summary EA fails with "EA unexpected CallLeaf unsafe_setmemory" after JDK-8329331
 * @requires vm.compMode != "Xint"
 *
 * @run main/othervm -Xbatch -XX:-TieredCompilation  Test8331033
 *
 */
import java.lang.foreign.*;

class MyClass {
  public int field1;
  public int field2;
  public int field3;
  public int field4;

  public MyClass(int field1, int field2, int field3, int field4) {
    this.field1 = field1;
    this.field2 = field2;
    this.field3 = field3;
    this.field4 = field4;
  }
}

public class Test8331033 {

  public static int micro1(int ctr) {
    // non-escaping object, EA sweepable, values are forwarded to users, additional
    // bookeeping (SafepointScalarObjectNode) for eliminated objects at SafePoints
    // With -XX:-Inline, constructor will not be inlined and hence AllocateNode are
    // considered escapable.
    MyClass obj = new MyClass(10, 20, 30, 40);
    return obj.field1 * ctr + obj.field2 + obj.field3 + obj.field4;
  }

  public static int micro2(int ctr) {
    // small known length arrays of size less than
    // -XX:+EliminateAllocationArraySizeLimit are eliminatable if non-escaping.
    int[] arr = new int[5];
    arr[0] = 10;
    arr[1] = 10;
    arr[2] = 10;
    arr[3] = 10;
    arr[4] = 10;
    // value forwarding will forward constants to expressions.
    return ctr * arr[0] + arr[1] + arr[2] + arr[3] + arr[4];
  }

  public static int micro3(int ctr) {
    int[] arr = new int[8];
    MemorySegment ms = MemorySegment.ofArray(arr);
    ms.fill((byte) 10);
    return ctr * ms.get(ValueLayout.JAVA_INT, 12) +
        ms.get(ValueLayout.JAVA_INT, 16) +
        ms.get(ValueLayout.JAVA_INT, 20) +
        ms.get(ValueLayout.JAVA_INT, 24);
  }

  public static void main(String[] args) {
    {
      int res = 0;
      for (int i = 0; i < 100000; i++) {
        res += micro1(i);
      }
      System.out.println("[res] " + res);
    }
    {
      int res = 0;
      for (int i = 0; i < 100000; i++) {
        res += micro2(i);
      }
      System.out.println("[res] " + res);
    }
    {
      int res = 0;
      for (int i = 0; i < 100000; i++) {
        res += micro3(i);
      }
      System.out.println("[res] " + res);
    }
  }
}
