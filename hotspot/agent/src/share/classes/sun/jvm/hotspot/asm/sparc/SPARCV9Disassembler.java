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

// Please refer to "The SPARC Architecture Manual - Version 9"

public class SPARCV9Disassembler extends SPARCDisassembler
                    implements /* imports */ SPARCV9Opcodes {
    public SPARCV9Disassembler(long startPc, byte[] code, SPARCV9InstructionFactory factory) {
        super(startPc, code, factory);
    }

    public SPARCV9Disassembler(long startPc, byte[] code) {
        this(startPc, code, new SPARCV9InstructionFactoryImpl());
    }

    // decoders for format 2 instructions
    private static InstructionDecoder format2Decoders[] = {
        new UnimpDecoder(),
        new V9IntegerBranchDecoder(),
        new IntegerBranchDecoder(),
        new V9IntRegisterBranchDecoder(),
        new SethiDecoder(),
        new V9FloatBranchDecoder(),
        new FloatBranchDecoder(),
        illegalDecoder
    };

    protected InstructionDecoder getFormat2Decoder(int op2) {
        return format2Decoders[op2];
    }

    // op3 opcode table for op=3 (FORMAT_3) instructions - (memory instructions)
    // E.2 Tables - Page 275 - Table 33.
    private static final InstructionDecoder format3Decoders[][] = {
        {
          new LoadDecoder(LDUW, "ld" /* lduw */, RTLDT_UNSIGNED_WORD), new V9AlternateSpaceLoadDecoder(LDUWA, "lduwa", RTLDT_UNSIGNED_WORD),
          new LoadDecoder(LDF,"ld", RTLDT_FL_SINGLE),  new V9AlternateSpaceLoadDecoder(LDFA, "lda", RTLDT_FL_SINGLE)
        },
        {
          new LoadDecoder(LDUB, "ldub", RTLDT_UNSIGNED_BYTE),   new V9AlternateSpaceLoadDecoder(LDUBA, "lduba", RTLDT_UNSIGNED_BYTE),
          new V9SpecialLoadDecoder(LDFSR), illegalDecoder
        },
        {
          new LoadDecoder(LDUH, "lduh", RTLDT_UNSIGNED_HALF),  new V9AlternateSpaceLoadDecoder(LDUHA, "lduha", RTLDT_UNSIGNED_HALF),
          new LoadDecoder(LDQF, "ldq", RTLDT_FL_QUAD), new V9AlternateSpaceLoadDecoder(LDQFA, "ldqa", RTLDT_FL_QUAD)
        },
        {
          new LoadDecoder(LDD, "ldd", RTLDT_UNSIGNED_DWORD), new V9AlternateSpaceLoadDecoder(LDDA, "ldda", RTLDT_UNSIGNED_DWORD),
          new LoadDecoder(LDDF, "ldd", RTLDT_FL_DOUBLE), new LoadDecoder(LDDFA, "ldda", RTLDT_FL_DOUBLE)
        },
        {
          new StoreDecoder(STW, "st" /* stw, stuw, stsw */, RTLDT_UNSIGNED_WORD), new V9AlternateSpaceStoreDecoder(STWA, "stwa", RTLDT_UNSIGNED_WORD),
          new StoreDecoder(STF, "st", RTLDT_FL_SINGLE), new StoreDecoder(STFA, "st", RTLDT_FL_SINGLE),
        },
        {
          new StoreDecoder(STB, "stb", RTLDT_UNSIGNED_BYTE), new V9AlternateSpaceStoreDecoder(STBA, "stba", RTLDT_UNSIGNED_BYTE),
          new V9SpecialStoreDecoder(STFSR), illegalDecoder
        },
        {
          new StoreDecoder(STH, "sth", RTLDT_UNSIGNED_HALF), new V9AlternateSpaceStoreDecoder(STHA, "stha", RTLDT_UNSIGNED_HALF),
          new StoreDecoder(STQF, "stq", RTLDT_FL_QUAD), new V9AlternateSpaceStoreDecoder(STQFA, "stqa", RTLDT_FL_QUAD),
        },
        {
          new StoreDecoder(STD, "std", RTLDT_UNSIGNED_DWORD), new V9AlternateSpaceStoreDecoder(STDA, "stda", RTLDT_UNSIGNED_DWORD),
          new StoreDecoder(STDF, "std", RTLDT_FL_DOUBLE), new V9AlternateSpaceStoreDecoder(STDFA, "stda", RTLDT_FL_DOUBLE)
        },
        {
          new LoadDecoder(LDSW, "ldsw", RTLDT_SIGNED_WORD), new V9AlternateSpaceLoadDecoder(LDSWA, "ldswa", RTLDT_SIGNED_WORD),
          illegalDecoder, illegalDecoder
        },
        {
          new LoadDecoder(LDSB, "ldsb", RTLDT_SIGNED_BYTE), new V9AlternateSpaceLoadDecoder(LDSBA, "ldsba", RTLDT_UNSIGNED_BYTE),
          illegalDecoder, illegalDecoder
        },
        {
          new LoadDecoder(LDSH, "ldsh", RTLDT_SIGNED_HALF), new V9AlternateSpaceLoadDecoder(LDSHA, "ldsha", RTLDT_UNSIGNED_HALF),
          illegalDecoder, illegalDecoder
        },
        {
          new LoadDecoder(LDX, "ldx", RTLDT_UNSIGNED_DWORD), new V9AlternateSpaceLoadDecoder(LDXA, "ldxa", RTLDT_UNSIGNED_DWORD),
          illegalDecoder, illegalDecoder
        },
        {
          illegalDecoder, illegalDecoder,
          illegalDecoder, new V9CasDecoder(CASA, "casa", RTLDT_UNSIGNED_WORD)
        },
        {
          new LdstubDecoder(LDSTUB, "ldstub", RTLDT_UNSIGNED_BYTE), new V9AlternateSpaceLdstubDecoder(LDSTUBA, "ldstuba", RTLDT_UNSIGNED_BYTE),
          new V9PrefetchDecoder(), new V9AlternateSpacePrefetchDecoder()
        },
        {
          new StoreDecoder(STX, "stx", RTLDT_UNSIGNED_DWORD), new V9AlternateSpaceStoreDecoder(STXA, "stxa", RTLDT_UNSIGNED_DWORD),
          illegalDecoder, new V9CasDecoder(CASXA, "casxa", RTLDT_UNSIGNED_DWORD)
        },
        {
          new SwapDecoder(SWAP, "swap", RTLDT_UNSIGNED_WORD), new V9AlternateSpaceSwapDecoder(SWAPA, "swapa", RTLDT_UNSIGNED_WORD),
          illegalDecoder, illegalDecoder
        },
    };

    protected InstructionDecoder getFormat3Decoder(int row, int column) {
        return format3Decoders[row][column];
    }

    // op3 decoder table for op=2 (FORMAT_3A) instructions
    // E Opcode Maps - Page 274 - Table 32

    protected static final InstructionDecoder format3ADecoders[][] = {
        {
          new ArithmeticDecoder(ADD, "add", RTLOP_ADD), new ArithmeticDecoder(ADDcc, "addcc", RTLOP_ADD),
          new ArithmeticDecoder(TADDcc, "taddcc", RTLOP_ADD),  new V9WriteDecoder()
        },
        {
          new LogicDecoder(AND, "and", RTLOP_AND), new LogicDecoder(ANDcc, "andcc", RTLOP_AND),
          new ArithmeticDecoder(TSUBcc, "tsubcc", RTLOP_ADD),  new V9SavedRestoredDecoder()
        },
        {
          new LogicDecoder(OR, "or", RTLOP_OR), new LogicDecoder(ORcc, "orcc", RTLOP_OR),
          new ArithmeticDecoder(TADDccTV, "taddcctv", RTLOP_ADD),  new V9WrprDecoder()
        },
        {
          new LogicDecoder(XOR, "xor", RTLOP_XOR), new LogicDecoder(XORcc, "xorcc", RTLOP_XOR),
          new ArithmeticDecoder(TSUBccTV, "tsubcctv", RTLOP_SUB), illegalDecoder
        },
        {
          new ArithmeticDecoder(SUB, "sub", RTLOP_SUB),  new ArithmeticDecoder(SUBcc, "subcc", RTLOP_SUB),
          new ArithmeticDecoder(MULScc, "mulscc", RTLOP_SMUL), new V9FPop1Decoder()
        },
        {
          new LogicDecoder(ANDN, "andn", RTLOP_NAND), new LogicDecoder(ANDNcc, "andncc", RTLOP_NAND),
          new V9ShiftDecoder(SLL, "sll", RTLOP_SLL), new V9FPop2Decoder()
        },
        {
          new LogicDecoder(ORN, "orn", RTLOP_NOR), new LogicDecoder(ORNcc, "orncc", RTLOP_NOR),
          new V9ShiftDecoder(SRL, "srl", RTLOP_SRL), new CoprocessorDecoder(IMPDEP1)
        },
        {
          new LogicDecoder(XNOR, "xnor", RTLOP_XNOR), new LogicDecoder(XNORcc, "xnorcc", RTLOP_XNOR),
          new V9ShiftDecoder(SRA, "sra", RTLOP_SRA), new CoprocessorDecoder(IMPDEP2)
        },
        {
          new ArithmeticDecoder(ADDC, "addc", RTLOP_ADDC), new ArithmeticDecoder(ADDCcc, "addccc", RTLOP_ADDC),
          new V9ReadDecoder(), new JmplDecoder()
        },
        {
          new ArithmeticDecoder(MULX, "mulx", RTLOP_UMUL), illegalDecoder,
          illegalDecoder, new RettDecoder()
        },
        {
          new ArithmeticDecoder(UMUL, "umul", RTLOP_UMUL), new ArithmeticDecoder(UMULcc, "umulcc", RTLOP_UMUL),
          new V9RdprDecoder(), new TrapDecoder()
        },
        {
          new ArithmeticDecoder(SMUL, "smul", RTLOP_SMUL), new ArithmeticDecoder(SMULcc, "smulcc", RTLOP_SMUL),
          new V9FlushwDecoder(), new FlushDecoder()
        },
        {
          new ArithmeticDecoder(SUBC, "subc", RTLOP_SUBC), new ArithmeticDecoder(SUBCcc, "subccc", RTLOP_SUBC),
          new V9MOVccDecoder(), new SaveDecoder()
        },
        {
          new ArithmeticDecoder(UDIVX, "udivx", RTLOP_UDIV), illegalDecoder,
          new ArithmeticDecoder(SDIVX, "sdivx", RTLOP_SDIV), new RestoreDecoder()
        },
        {
          new ArithmeticDecoder(UDIV, "udiv", RTLOP_UDIV),  new ArithmeticDecoder(UDIVcc, "udivcc", RTLOP_UDIV),
          new V9PopcDecoder(), new V9DoneRetryDecoder()
        },
        {
          new ArithmeticDecoder(SDIV, "sdiv", RTLOP_SDIV), new ArithmeticDecoder(SDIVcc, "sdivcc", RTLOP_SDIV),
          new V9MOVrDecoder(), illegalDecoder
        }
    };

    protected InstructionDecoder getFormat3ADecoder(int row, int column) {
        return format3ADecoders[row][column];
    }
}
