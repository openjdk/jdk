/*
 * Copyright (c) 2002, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.asm.sparc;

import sun.jvm.hotspot.asm.*;

public class SPARCV9InstructionFactoryImpl extends SPARCInstructionFactoryImpl
                      implements SPARCV9InstructionFactory {

    public SPARCInstruction newUnimpInstruction(int const22) {
        return new SPARCV9IlltrapInstruction(const22);
    }

    public SPARCInstruction newRettInstruction(SPARCRegisterIndirectAddress addr) {
        return new SPARCV9ReturnInstruction(addr);
    }

    public SPARCInstruction newCoprocessorInstruction(int instruction, int cpopcode, int opc,
                                                     int rs1Num, int rs2Num, int rdNum) {
        return new SPARCV9ImpdepInstruction(cpopcode == SPARCOpcodes.CPop1? "impdep1" : "impdep2");
    }

    public SPARCInstruction newV9ReadInstruction(int specialRegNum, int asrRegNum, SPARCRegister rd) {
        return new SPARCV9ReadInstruction(specialRegNum, asrRegNum, rd);
    }

    public SPARCInstruction newV9WriteInstruction(int specialRegNum, int asrRegNum, SPARCRegister rs1,
                                                  ImmediateOrRegister operand2) {
        return new SPARCV9WriteInstruction(specialRegNum, asrRegNum, rs1, operand2);
    }

    public SPARCInstruction newV9BranchInstruction(String name, PCRelativeAddress addr,
              boolean isAnnuled, int conditionCode, boolean predictTaken, int conditionFlag) {
        return new SPARCV9BranchInstruction(name, addr, isAnnuled, conditionCode,
                       predictTaken, conditionFlag);
    }

    public SPARCInstruction newV9RegisterBranchInstruction(String name, PCRelativeAddress addr,
                               boolean isAnnuled, int regConditionCode, SPARCRegister conditionRegister,
                               boolean predictTaken) {
        return new SPARCV9RegisterBranchInstruction(name, addr, isAnnuled, regConditionCode,
                               conditionRegister, predictTaken);
    }

    public SPARCInstruction newV9CasInstruction(String name, SPARCRegisterIndirectAddress addr,
                                              SPARCRegister rs2, SPARCRegister rd, int dataType) {
        return new SPARCV9CasInstruction(name, addr, rs2, rd, dataType);
    }

    public SPARCInstruction newV9PrefetchInstruction(String name, SPARCRegisterIndirectAddress addr,
                               int prefetchFcn) {
        return new SPARCV9PrefetchInstruction(name, addr, prefetchFcn);
    }

    public SPARCInstruction newV9FlushwInstruction() {
        return new SPARCV9FlushwInstruction();
    }

    public SPARCInstruction newV9MOVccInstruction(String name, int conditionCode, int conditionFlag,
                                  ImmediateOrRegister source, SPARCRegister rd) {
        return new SPARCV9MOVccInstruction(name, conditionCode, conditionFlag, source, rd);
    }

    public SPARCInstruction newV9MOVrInstruction(String name, SPARCRegister rs1,
                                   ImmediateOrRegister operand2, SPARCRegister rd,
                                   int regConditionCode) {
        return new SPARCV9MOVrInstruction(name, rs1, operand2, rd, regConditionCode);
    }

    public SPARCInstruction newV9RdprInstruction(int regNum, SPARCRegister rd) {
        return new SPARCV9RdprInstruction(regNum, rd);
    }

    public SPARCInstruction newV9WrprInstruction(SPARCRegister rs1, ImmediateOrRegister operand2, int regNum) {
        return new SPARCV9WrprInstruction(rs1, operand2, regNum);
    }

    public SPARCInstruction newV9PopcInstruction(ImmediateOrRegister source, SPARCRegister rd) {
        return new SPARCV9PopcInstruction(source, rd);
    }

    public SPARCInstruction newV9DoneInstruction() {
        return new SPARCV9DoneInstruction();
    }

    public SPARCInstruction newV9RetryInstruction() {
        return new SPARCV9RetryInstruction();
    }

    public SPARCInstruction newV9SavedInstruction() {
        return new SPARCV9SavedInstruction();
    }

    public SPARCInstruction newV9RestoredInstruction() {
        return new SPARCV9RestoredInstruction();
    }

    public SPARCInstruction newV9MembarInstruction(int mmask, int cmask) {
        return new SPARCV9MembarInstruction(mmask, cmask);
    }

    public SPARCInstruction newV9SirInstruction() {
        return new SPARCV9SirInstruction();
    }

    public SPARCInstruction newV9FMOVccInstruction(String name, int opf,
                                           int conditionCode, int conditionFlag,
                                           SPARCFloatRegister rs, SPARCFloatRegister rd) {
        return new SPARCV9FMOVccInstruction(name, opf, conditionCode, conditionFlag, rs, rd);
    }

    public SPARCInstruction newV9FMOVrInstruction(String name, int opf,
                                   SPARCRegister rs1, SPARCFloatRegister rs2,
                                   SPARCFloatRegister rd, int regConditionCode) {
        return new SPARCV9FMOVrInstruction(name, opf, rs1, rs2, rd, regConditionCode);
    }
}
