/*
 * Copyright (c) 2002, 2003, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.asm.x86;

import sun.jvm.hotspot.asm.*;

public interface X86InstructionFactory {
   public X86Instruction newCallInstruction(String name, Address addr, int size, int prefixes);

   public X86Instruction newJmpInstruction(String name, Address addr, int size, int prefixes);

   public X86Instruction newCondJmpInstruction(String name, X86PCRelativeAddress addr, int size, int prefixes);

   public X86Instruction newMoveInstruction(String name, X86Register rd, ImmediateOrRegister oSrc, int size, int prefixes);

   public X86Instruction newMoveLoadInstruction(String name, X86Register op1, Address op2, int dataType, int size, int prefixes);

   public X86Instruction newMoveStoreInstruction(String name, Address op1, X86Register op2, int dataType, int size, int prefixes);

   public X86Instruction newArithmeticInstruction(String name, int rtlOperation, Operand op1, Operand op2, Operand op3, int size, int prefixes);

   public X86Instruction newArithmeticInstruction(String name, int rtlOperation, Operand op1, Operand op2, int size, int prefixes);

   public X86Instruction newLogicInstruction(String name, int rtlOperation, Operand op1, Operand op2, int size, int prefixes);

   public X86Instruction newBranchInstruction(String name, X86PCRelativeAddress addr, int size, int prefixes);

   public X86Instruction newShiftInstruction(String name, int rtlOperation, Operand op1, ImmediateOrRegister op2, int size, int prefixes);

   public X86Instruction newRotateInstruction(String name, Operand op1, ImmediateOrRegister op2, int size, int prefixes);

   public X86Instruction newFPLoadInstruction(String name, Operand op, int size, int prefixes);

   public X86Instruction newFPStoreInstruction(String name, Operand op, int size, int prefixes);

   public X86Instruction newFPArithmeticInstruction(String name, int rtlOperation, Operand op1, Operand op2, int size, int prefixes);

   public X86Instruction newGeneralInstruction(String name, Operand op1, Operand op2, Operand op3, int size, int prefixes);

   public X86Instruction newGeneralInstruction(String name, Operand op1, Operand op2, int size, int prefixes);

   public X86Instruction newGeneralInstruction(String name, Operand op1, int size, int prefixes);

   public X86Instruction newIllegalInstruction();

}
