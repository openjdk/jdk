/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

package compiler.valhalla.inlinetypes;

import jdk.internal.misc.Unsafe;
import compiler.lib.ir_framework.*;

/*
 * @test
 * @summary Missing InlineTypeNode re-materialization during type sharpening.
 * @library /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.misc
 * @run main/timeout=300 compiler.valhalla.inlinetypes.TestValueRematDuringTypeSharpening
 */

abstract value class topValue {
}

value class dummyValue1 extends topValue {
   int field;
   public dummyValue1(int val) {
      field = val;
   }
}

value class dummyValue2 extends topValue {
   int field;
   public dummyValue2(int val) {
      field = val;
   }
}

public class TestValueRematDuringTypeSharpening {

   public static final Unsafe UNSAFE = Unsafe.getUnsafe();

   @DontInline
   public static int getUnsafeFieldValue(topValue obj, int incr) {
       return UNSAFE.getInt(obj, 12) + incr;
   }

   @Test
   @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {IRNode.INLINE_TYPE, " > 0 "})
   public static int test(topValue obj) {
       int val = 0;
       if (obj.getClass() != dummyValue1.class) {
           val += 10;
       } else if (obj.getClass() != dummyValue2.class) {
           val += 20;
       }
       return getUnsafeFieldValue(obj, val);
   }

   @Run(test = {"test"}, mode = RunMode.NORMAL)
   public static void kernel() {
       test(new dummyValue1(10));
       test(new dummyValue2(20));
   }

   public static void main(String [] args) {
       TestFramework.runWithFlags("-XX:-TieredCompilation", "--enable-preview", "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED");
       System.out.println("PASS");
   }
}

