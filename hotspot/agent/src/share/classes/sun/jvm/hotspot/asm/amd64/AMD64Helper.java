/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

package sun.jvm.hotspot.asm.amd64;

import sun.jvm.hotspot.asm.*;


public class AMD64Helper implements CPUHelper {
   public Disassembler createDisassembler(long startPc, byte[] code) {
      // FIXME: no disassembler yet
      return null;
   }

   public Register getIntegerRegister(int num) {
      return AMD64Registers.getRegister(num);
   }

   public Register getFloatRegister(int num) {
      return AMD64FloatRegisters.getRegister(num);
   }

   public Register getStackPointer() {
      return AMD64Registers.RSP;
   }

   public Register getFramePointer() {
      return AMD64Registers.RBP;
   }
}
