/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, Alibaba Group Holding Limited. All Rights Reserved.
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
 * @bug 8183543
 * @summary C2 compilation often fails with "failed spill-split-recycle sanity check"
 *
 * @library /test/lib
 *
 * @build sun.hotspot.WhiteBox
 *
 * @run driver jdk.test.lib.helpers.ClassFileInstaller sun.hotspot.WhiteBox
 *
 * @run main/othervm -Xbatch
 *                   -XX:-Inline
 *                   -XX:-TieredCompilation
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *                   compiler.c2.Test8183543
 */

package compiler.c2;

import sun.hotspot.WhiteBox;

public class Test8183543 {

  static volatile int vol_f;

  static int test(Test8183543 arg) {
    Test8183543 a = arg;
    int res = 0;
    not_inlined();
    res = a.vol_f;
    return res;
  }

  static void not_inlined() {
    for (int i = 0; i < 5000; i++) {
      vol_f += 2;
    }
  }

  public static void main(String args[]) {
    Test8183543 arg = new Test8183543();
    for (int i = 0; i < 10000; i++) {
      Test8183543.test(arg);
    }
    try {
      var method = Test8183543.class.getDeclaredMethod("test", Test8183543.class);
      if (!WhiteBox.getWhiteBox().isMethodCompiled(method)) {
        throw new Error("test method didn't get compiled");
      }
    } catch (NoSuchMethodException e) {
      throw new Error("TESTBUG : " + e, e);
    }
  }
}
