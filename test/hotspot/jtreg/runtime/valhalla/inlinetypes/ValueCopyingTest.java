/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @test ValueCopyingTest
 * @summary Verify that interpreter doesn't tear up primitive fields when copying a value
 * @library /test/lib
 * @modules java.base/jdk.internal.vm.annotation
 * @enablePreview
 * @compile ValueCopyingTest.java
 * @run main/othervm -Xint -XX:+UnlockDiagnosticVMOptions -XX:+PrintInlineLayout runtime.valhalla.inlinetypes.ValueCopyingTest
 */

package runtime.valhalla.inlinetypes;

import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.internal.vm.annotation.NullRestricted;
import jdk.test.lib.Asserts;

public class ValueCopyingTest {

  static final int NUM_WORKERS = 16;

  static ValueCopyingTest target = new ValueCopyingTest();

  public ValueCopyingTest() {
      tv = new TestValue(0);
      super();
  }

  @LooselyConsistentValue
  static value class TestValue {
    int i;
    byte b;
    TestValue(int i0) {
      i = i0;
      b = 0;
    }
  }

  @NullRestricted
  TestValue tv;

  static class Worker implements Runnable {
    int i;
    TestValue v;
    Worker(byte b) {
      i = b | (b << 8) | (b << 16) | (b << 24);
      v = new TestValue(i);
    }

    static void checkValue(int i) {
      byte b = (byte)(i & 0xFF);
      Asserts.assertTrue(((i >> 8) & 0xFF) == b, "Tearing detected");
      Asserts.assertTrue(((i >> 16) & 0xFF) == b, "Tearing detected");
      Asserts.assertTrue(((i >> 24) & 0xFF) == b, "Tearing detected");
    }

    public void run() {
      for (int n = 0; n < 10000000; n++) {
        ValueCopyingTest.target.tv = v;
        int ri = ValueCopyingTest.target.tv.i;
        checkValue(ri);
      }
    }
  }

  static public void main(String[] args) throws InterruptedException {
    Thread[] workers = new Thread[NUM_WORKERS];
    for (int i = 0; i < NUM_WORKERS; i++) {
      workers[i] = new Thread(new Worker((byte)i));
    }
    for (int i = 0; i < NUM_WORKERS; i++) {
      workers[i].start();
    }
    try {
      for (int i = 0; i < NUM_WORKERS; i++) {
        workers[i].join();
      }
    } catch(InterruptedException e) {
      e.printStackTrace();
      throw e;
    }
  }
}
