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
import java.io.*;
import java.util.*;

// Please refer to "The SPARC Architecture Manual - Version 8"

public class SPARCV8Disassembler extends SPARCDisassembler {

    public SPARCV8Disassembler(long startPc, byte[] code, SPARCInstructionFactory factory) {
        super(startPc, code, factory);
    }

    public SPARCV8Disassembler(long startPc, byte[] code) {
        this(startPc, code, new SPARCInstructionFactoryImpl());
    }

    // decoders for format 2 instructions
    private static InstructionDecoder format2Decoders[] = {
        new UnimpDecoder(),
        illegalDecoder,
        new IntegerBranchDecoder(),
        illegalDecoder,
        new SethiDecoder(),
        illegalDecoder,
        new FloatBranchDecoder(),
        new CoprocessorBranchDecoder()
    };

    protected InstructionDecoder getFormat2Decoder(int op2) {
        return format2Decoders[op2];
    }

    // op3 decoder table for op=3 (FORMAT_3) instructions - (memory instructions)
    // Appendix F - Opcodes and Condition Codes - Page 229 - Table F-4

    private static final InstructionDecoder format3Decoders[][] = {
        {
          new LoadDecoder(LD, "ld", RTLDT_UNSIGNED_WORD), new AlternateSpaceLoadDecoder(LDA, "lda", RTLDT_UNSIGNED_WORD),
          new LoadDecoder(LDF,"ld", RTLDT_FL_SINGLE),     new SpecialLoadDecoder(LDC,"ld", SPARCSpecialRegisters.CREG)
        },
        {
          new LoadDecoder(LDUB, "ldub", RTLDT_UNSIGNED_BYTE),   new AlternateSpaceLoadDecoder(LDUBA, "lduba", RTLDT_UNSIGNED_BYTE),
          new SpecialLoadDecoder(LDFSR, "ld", SPARCSpecialRegisters.FSR), new SpecialLoadDecoder(LDCSR, "ld", SPARCSpecialRegisters.CSR)
        },
        {
          new LoadDecoder(LDUH, "lduh", RTLDT_UNSIGNED_HALF),  new AlternateSpaceLoadDecoder(LDUHA, "lduha", RTLDT_UNSIGNED_HALF),
          illegalDecoder, illegalDecoder
        },
        {
          new LoadDecoder(LDD, "ldd", RTLDT_UNSIGNED_DWORD), new AlternateSpaceLoadDecoder(LDDA, "ldda", RTLDT_UNSIGNED_DWORD),
          new LoadDecoder(LDDF, "ldd", RTLDT_FL_DOUBLE),  new SpecialLoadDecoder(LDDC, "ldd", SPARCSpecialRegisters.CREG)
        },
        {
          new StoreDecoder(ST, "st", RTLDT_UNSIGNED_WORD), new AlternateSpaceStoreDecoder(STA, "sta", RTLDT_UNSIGNED_WORD),
          new StoreDecoder(STF, "st", RTLDT_FL_SINGLE),   new SpecialStoreDecoder(STC, "st", SPARCSpecialRegisters.CREG)
        },
        {
          new StoreDecoder(STB, "stb", RTLDT_UNSIGNED_BYTE), new AlternateSpaceStoreDecoder(STBA, "stba", RTLDT_UNSIGNED_BYTE),
          new SpecialStoreDecoder(STFSR, "st", SPARCSpecialRegisters.FSR), new SpecialStoreDecoder(STCSR, "st", SPARCSpecialRegisters.CSR),
        },
        {
          new StoreDecoder(STH, "sth", RTLDT_UNSIGNED_HALF), new AlternateSpaceStoreDecoder(STHA, "stha", RTLDT_UNSIGNED_HALF),
          new SpecialStoreDecoder(STDFQ, "std", SPARCSpecialRegisters.FQ), new SpecialStoreDecoder(STDCQ, "std", SPARCSpecialRegisters.CQ),
        },
        {
          new StoreDecoder(STD, "std", RTLDT_UNSIGNED_DWORD),  new AlternateSpaceStoreDecoder(STDA, "stda", RTLDT_UNSIGNED_DWORD),
          new StoreDecoder(STDF, "std", RTLDT_FL_DOUBLE),  new SpecialStoreDecoder(STDC, "std", SPARCSpecialRegisters.CREG)
        },
        {
          illegalDecoder, illegalDecoder,
          illegalDecoder, illegalDecoder
        },
        {
          new LoadDecoder(LDSB, "ldsb", RTLDT_SIGNED_BYTE), new AlternateSpaceLoadDecoder(LDSBA, "ldsba", RTLDT_UNSIGNED_BYTE),
          illegalDecoder, illegalDecoder
        },
        {
          new LoadDecoder(LDSH, "ldsh", RTLDT_SIGNED_HALF), new AlternateSpaceLoadDecoder(LDSHA, "ldsha", RTLDT_UNSIGNED_HALF),
          illegalDecoder, illegalDecoder
        },
        {
          illegalDecoder, illegalDecoder,
          illegalDecoder, illegalDecoder
        },
        {
          illegalDecoder, illegalDecoder,
          illegalDecoder, illegalDecoder
        },
        {
          new LdstubDecoder(LDSTUB, "ldstub", RTLDT_UNSIGNED_BYTE), new AlternateSpaceLdstubDecoder(LDSTUBA, "ldstuba", RTLDT_UNSIGNED_BYTE),
          illegalDecoder, illegalDecoder
        },
        {
          illegalDecoder, illegalDecoder,
          illegalDecoder, illegalDecoder
        },
        {
          new SwapDecoder(SWAP, "swap", RTLDT_UNSIGNED_WORD), new AlternateSpaceSwapDecoder(SWAPA, "swapa", RTLDT_UNSIGNED_WORD),
          illegalDecoder, illegalDecoder
        },
    };

    protected InstructionDecoder getFormat3Decoder(int row, int column) {
        return format3Decoders[row][column];
    }

    // op3 decoder table for op=2 (FORMAT_3A) instructions
    // Appendix F - Opcodes and Condition Codes - Page 228 - Table F-3
    protected static final InstructionDecoder format3ADecoders[][] = {
        {
          new ArithmeticDecoder(ADD, "add", RTLOP_ADD), new ArithmeticDecoder(ADDcc, "addcc", RTLOP_ADD),
          new ArithmeticDecoder(TADDcc, "taddcc", RTLOP_ADD),  new WriteDecoder(SPARCSpecialRegisters.ASR)
        },
        {
          new LogicDecoder(AND, "and", RTLOP_AND), new LogicDecoder(ANDcc, "andcc", RTLOP_AND),
          new ArithmeticDecoder(TSUBcc, "tsubcc", RTLOP_ADD),  new WriteDecoder(SPARCSpecialRegisters.PSR)
        },
        {
          new LogicDecoder(OR, "or", RTLOP_OR), new LogicDecoder(ORcc, "orcc", RTLOP_OR),
          new ArithmeticDecoder(TADDccTV, "taddcctv", RTLOP_ADD),  new WriteDecoder(SPARCSpecialRegisters.WIM)
        },
        {
          new LogicDecoder(XOR, "xor", RTLOP_XOR), new LogicDecoder(XORcc, "xorcc", RTLOP_XOR),
          new ArithmeticDecoder(TSUBccTV, "tsubcctv", RTLOP_SUB),  new WriteDecoder(SPARCSpecialRegisters.TBR)
        },
        {
          new ArithmeticDecoder(SUB, "sub", RTLOP_SUB),  new ArithmeticDecoder(SUBcc, "subcc", RTLOP_SUB),
          new ArithmeticDecoder(MULScc, "mulscc", RTLOP_SMUL), new V8FPop1Decoder()
        },
        {
          new LogicDecoder(ANDN, "andn", RTLOP_NAND), new LogicDecoder(ANDNcc, "andncc", RTLOP_NAND),
          new ShiftDecoder(SLL, "sll", RTLOP_SLL), new V8FPop2Decoder()
        },
        {
          new LogicDecoder(ORN, "orn", RTLOP_NOR), new LogicDecoder(ORNcc, "orncc", RTLOP_NOR),
          new ShiftDecoder(SRL, "srl", RTLOP_SRL), new CoprocessorDecoder(CPop1)
        },
        {
          new LogicDecoder(XNOR, "xnor", RTLOP_XNOR), new LogicDecoder(XNORcc, "xnorcc", RTLOP_XNOR),
          new ShiftDecoder(SRA, "sra", RTLOP_SRA), new CoprocessorDecoder(CPop2)
        },
        {
          new ArithmeticDecoder(ADDX, "addx", RTLOP_ADDC), new ArithmeticDecoder(ADDXcc, "addxcc", RTLOP_ADDC),
          new ReadDecoder(SPARCSpecialRegisters.ASR), new JmplDecoder()
        },
        {
          illegalDecoder, illegalDecoder,
          new ReadDecoder(SPARCSpecialRegisters.PSR), new RettDecoder()
        },
        {
          new ArithmeticDecoder(UMUL, "umul", RTLOP_UMUL), new ArithmeticDecoder(UMULcc, "umulcc", RTLOP_UMUL),
          new ReadDecoder(SPARCSpecialRegisters.WIM), new TrapDecoder()
        },
        {
          new ArithmeticDecoder(SMUL, "smul", RTLOP_SMUL), new ArithmeticDecoder(SMULcc, "smulcc", RTLOP_SMUL),
          new ReadDecoder(SPARCSpecialRegisters.TBR), new FlushDecoder()
        },
        {
          new ArithmeticDecoder(SUBX, "subx", RTLOP_SUBC), new ArithmeticDecoder(SUBXcc, "subxcc", RTLOP_SUBC),
          illegalDecoder, new SaveDecoder()
        },
        {
          illegalDecoder, illegalDecoder,
          illegalDecoder, new RestoreDecoder()
        },
        {
          new ArithmeticDecoder(UDIV, "udiv", RTLOP_UDIV),  new ArithmeticDecoder(UDIVcc, "udivcc", RTLOP_UDIV),
          illegalDecoder, illegalDecoder
        },
        {
          new ArithmeticDecoder(SDIV, "sdiv", RTLOP_SDIV), new ArithmeticDecoder(SDIVcc, "sdivcc", RTLOP_SDIV),
          illegalDecoder, illegalDecoder
        }
    };

    protected InstructionDecoder getFormat3ADecoder(int row, int column) {
        return format3ADecoders[row][column];
    }
}
