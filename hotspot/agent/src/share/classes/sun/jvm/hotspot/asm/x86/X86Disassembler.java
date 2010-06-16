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
import java.io.*;

public class X86Disassembler extends Disassembler
                              implements X86Opcodes {
   private int byteIndex;
   protected final X86InstructionFactory factory;

   public X86Disassembler(long startPc, byte[] code, X86InstructionFactory factory) {
      super(startPc, code);
      this.factory = factory;
   }

   public X86Disassembler(long startPc, byte[] code) {
      this(startPc, code, new X86InstructionFactoryImpl());
   }

   //Please refer to IA-32 Intel Architecture Software Developer's Manual Volume 2
   //APPENDIX A - Table A-2. One-byte Opcode Map
   private static final InstructionDecoder oneByteTable[] = {
      /* 00 */
      new ArithmeticDecoder("addb", ADDR_E, b_mode, ADDR_G, b_mode, RTLOP_ADD),
      new ArithmeticDecoder("addS", ADDR_E, v_mode, ADDR_G, v_mode, RTLOP_ADD),
      new ArithmeticDecoder("addb", ADDR_G, b_mode, ADDR_E, b_mode, RTLOP_ADD),
      new ArithmeticDecoder("addS", ADDR_G, v_mode, ADDR_E, v_mode, RTLOP_ADD),
      new ArithmeticDecoder("addb", ADDR_REG, AL, ADDR_I, b_mode, RTLOP_ADD),
      new ArithmeticDecoder("addS", ADDR_REG, EAX, ADDR_I, v_mode, RTLOP_ADD),
      new InstructionDecoder("pushl", ADDR_REG, ES),
      new InstructionDecoder("popl", ADDR_REG, ES),
      /* 08 */
      new LogicalDecoder("orb", ADDR_E, b_mode, ADDR_G, b_mode, RTLOP_OR),
      new LogicalDecoder("orS", ADDR_E, v_mode, ADDR_G, v_mode, RTLOP_OR),
      new LogicalDecoder("orb", ADDR_G, b_mode, ADDR_E, b_mode, RTLOP_OR),
      new LogicalDecoder("orS", ADDR_G, v_mode, ADDR_E, v_mode, RTLOP_OR),
      new LogicalDecoder("orb", ADDR_REG, AL, ADDR_I, b_mode, RTLOP_OR),
      new LogicalDecoder("orS", ADDR_REG, EAX, ADDR_I, v_mode, RTLOP_OR),
      new InstructionDecoder("pushl", ADDR_REG, CS),
      null,     /* 0x0f extended opcode escape */
      /* 10 */
      new ArithmeticDecoder("adcb", ADDR_E, b_mode, ADDR_G, b_mode, RTLOP_ADDC),
      new ArithmeticDecoder("adcS", ADDR_E, v_mode, ADDR_G, v_mode, RTLOP_ADDC),
      new ArithmeticDecoder("adcb", ADDR_G, b_mode, ADDR_E, b_mode, RTLOP_ADDC),
      new ArithmeticDecoder("adcS", ADDR_G, v_mode, ADDR_E, v_mode, RTLOP_ADDC),
      new ArithmeticDecoder("adcb", ADDR_REG, AL, ADDR_I, b_mode, RTLOP_ADDC),
      new ArithmeticDecoder("adcS", ADDR_REG, EAX, ADDR_I, v_mode, RTLOP_ADDC),
      new InstructionDecoder("pushl", ADDR_REG, SS),
      new InstructionDecoder("popl", ADDR_REG, SS),
      /* 18 */
      new ArithmeticDecoder("sbbb", ADDR_E, b_mode, ADDR_G, b_mode, RTLOP_SUBC),
      new ArithmeticDecoder("sbbS", ADDR_E, v_mode, ADDR_G, v_mode, RTLOP_SUBC),
      new ArithmeticDecoder("sbbb", ADDR_G, b_mode, ADDR_E, b_mode, RTLOP_SUBC),
      new ArithmeticDecoder("sbbS", ADDR_G, v_mode, ADDR_E, v_mode, RTLOP_SUBC),
      new ArithmeticDecoder("sbbb", ADDR_REG, AL, ADDR_I, b_mode, RTLOP_SUBC),
      new ArithmeticDecoder("sbbS", ADDR_REG, EAX, ADDR_I, v_mode, RTLOP_SUBC),
      new InstructionDecoder("pushl", ADDR_REG, DS),
      new InstructionDecoder("popl", ADDR_REG, DS),
      /* 20 */
      new LogicalDecoder("andb", ADDR_E, b_mode, ADDR_G, b_mode, RTLOP_AND),
      new LogicalDecoder("andS", ADDR_E, v_mode, ADDR_G, v_mode, RTLOP_AND),
      new LogicalDecoder("andb", ADDR_G, b_mode, ADDR_E, b_mode, RTLOP_AND),
      new LogicalDecoder("andS", ADDR_G, v_mode, ADDR_E, v_mode, RTLOP_AND),
      new LogicalDecoder("andb", ADDR_REG, AL, ADDR_I, b_mode, RTLOP_AND),
      new LogicalDecoder("andS", ADDR_REG, EAX, ADDR_I, v_mode, RTLOP_AND),
      null,                     /* SEG es prefix */
      new InstructionDecoder("daa"),
      /* 28 */
      new ArithmeticDecoder("subb", ADDR_E, b_mode, ADDR_G, b_mode, RTLOP_SUB),
      new ArithmeticDecoder("subS", ADDR_E, v_mode, ADDR_G, v_mode, RTLOP_SUB),
      new ArithmeticDecoder("subb", ADDR_G, b_mode, ADDR_E, b_mode, RTLOP_SUB),
      new ArithmeticDecoder("subS", ADDR_G, v_mode, ADDR_E, v_mode, RTLOP_SUB),
      new ArithmeticDecoder("subb", ADDR_REG, AL, ADDR_I, b_mode, RTLOP_SUB),
      new ArithmeticDecoder("subS", ADDR_REG, EAX, ADDR_I, v_mode, RTLOP_SUB),
      null,                     /* SEG CS prefix */
      new InstructionDecoder("das"),
      /* 30 */
      new LogicalDecoder("xorb", ADDR_E, b_mode, ADDR_G, b_mode, RTLOP_XOR),
      new LogicalDecoder("xorS", ADDR_E, v_mode, ADDR_G, v_mode, RTLOP_XOR),
      new LogicalDecoder("xorb", ADDR_G, b_mode, ADDR_E, b_mode, RTLOP_XOR),
      new LogicalDecoder("xorS", ADDR_G, v_mode, ADDR_E, v_mode, RTLOP_XOR),
      new LogicalDecoder("xorb", ADDR_REG, AL, ADDR_I, b_mode, RTLOP_XOR),
      new LogicalDecoder("xorS", ADDR_REG, EAX, ADDR_I, v_mode, RTLOP_XOR),
      null,     /* SEG SS prefix */
      new InstructionDecoder("aaa"),
      /* 38 */
      new InstructionDecoder("cmpb", ADDR_E, b_mode, ADDR_G, b_mode),
      new InstructionDecoder("cmpS", ADDR_E, v_mode, ADDR_G, v_mode),
      new InstructionDecoder("cmpb", ADDR_G, b_mode, ADDR_E, b_mode),
      new InstructionDecoder("cmpS", ADDR_G, v_mode, ADDR_E, v_mode),
      new InstructionDecoder("cmpb", ADDR_REG, AL, ADDR_I, b_mode),
      new InstructionDecoder("cmpS", ADDR_REG, EAX, ADDR_I, v_mode),
      null, /* SEG DS prefix */
      new InstructionDecoder("aas"),
      /* 40 */
      new ArithmeticDecoder("incS", ADDR_REG, EAX, RTLOP_ADD),
      new ArithmeticDecoder("incS", ADDR_REG, ECX, RTLOP_ADD),
      new ArithmeticDecoder("incS", ADDR_REG, EDX, RTLOP_ADD),
      new ArithmeticDecoder("incS", ADDR_REG, EBX, RTLOP_ADD),
      new ArithmeticDecoder("incS", ADDR_REG, ESP, RTLOP_ADD),
      new ArithmeticDecoder("incS", ADDR_REG, EBP, RTLOP_ADD),
      new ArithmeticDecoder("incS", ADDR_REG, ESI, RTLOP_ADD),
      new ArithmeticDecoder("incS", ADDR_REG, EDI, RTLOP_ADD),
      /* 48 */
      new ArithmeticDecoder("decS", ADDR_REG, EAX, RTLOP_SUB),
      new ArithmeticDecoder("decS", ADDR_REG, ECX, RTLOP_SUB),
      new ArithmeticDecoder("decS", ADDR_REG, EDX, RTLOP_SUB),
      new ArithmeticDecoder("decS", ADDR_REG, EBX, RTLOP_SUB),
      new ArithmeticDecoder("decS", ADDR_REG, ESP, RTLOP_SUB),
      new ArithmeticDecoder("decS", ADDR_REG, EBP, RTLOP_SUB),
      new ArithmeticDecoder("decS", ADDR_REG, ESI, RTLOP_SUB),
      new ArithmeticDecoder("decS", ADDR_REG, EDI, RTLOP_SUB),
      /* 50 */
      new InstructionDecoder("pushS", ADDR_REG, EAX),
      new InstructionDecoder("pushS", ADDR_REG, ECX),
      new InstructionDecoder("pushS", ADDR_REG, EDX),
      new InstructionDecoder("pushS", ADDR_REG, EBX),
      new InstructionDecoder("pushS", ADDR_REG, ESP),
      new InstructionDecoder("pushS", ADDR_REG, EBP),
      new InstructionDecoder("pushS", ADDR_REG, ESI),
      new InstructionDecoder("pushS", ADDR_REG, EDI),
      /* 58 */
      new InstructionDecoder("popS", ADDR_REG, EAX),
      new InstructionDecoder("popS", ADDR_REG, ECX),
      new InstructionDecoder("popS", ADDR_REG, EDX),
      new InstructionDecoder("popS", ADDR_REG, EBX),
      new InstructionDecoder("popS", ADDR_REG, ESP),
      new InstructionDecoder("popS", ADDR_REG, EBP),
      new InstructionDecoder("popS", ADDR_REG, ESI),
      new InstructionDecoder("popS", ADDR_REG, EDI),
      /* 60 */
      new InstructionDecoder("pusha"),
      new InstructionDecoder("popa"),
      new InstructionDecoder("boundS", ADDR_G, v_mode, ADDR_E, v_mode),
      new InstructionDecoder("arpl", ADDR_E, w_mode, ADDR_G, w_mode),
      null,     /* seg fs */
      null,     /* seg gs */
      null,     /* op size prefix */
      null, /* adr size prefix */
      /* 68 */
      new InstructionDecoder("pushS", ADDR_I, v_mode),  /* 386 book wrong */
      new ArithmeticDecoder("imulS", ADDR_G, v_mode, ADDR_E, v_mode, ADDR_I, v_mode, RTLOP_SMUL),
      new InstructionDecoder("pushl", ADDR_I, b_mode), /* push of byte really pushes 4 bytes */
      new ArithmeticDecoder("imulS", ADDR_G, v_mode, ADDR_E, v_mode, ADDR_I, b_mode, RTLOP_SMUL),
      new InstructionDecoder("insb", ADDR_ESDI, b_mode, INDIR_REG, DX),
      new InstructionDecoder("insS", ADDR_ESDI, v_mode, INDIR_REG, DX),
      new InstructionDecoder("outsb", INDIR_REG, DX, ADDR_DSSI, b_mode),
      new InstructionDecoder("outsS", INDIR_REG, DX, ADDR_DSSI, v_mode),
      /* 70 */
      new ConditionalJmpDecoder("jo", ADDR_J, b_mode),
      new ConditionalJmpDecoder("jno", ADDR_J, b_mode),
      new ConditionalJmpDecoder("jb", ADDR_J, b_mode),
      new ConditionalJmpDecoder("jae", ADDR_J, b_mode),
      new ConditionalJmpDecoder("je", ADDR_J, b_mode),
      new ConditionalJmpDecoder("jne", ADDR_J, b_mode),
      new ConditionalJmpDecoder("jbe", ADDR_J, b_mode),
      new ConditionalJmpDecoder("ja", ADDR_J, b_mode),
      /* 78 */
      new ConditionalJmpDecoder("js", ADDR_J, b_mode),
      new ConditionalJmpDecoder("jns", ADDR_J, b_mode),
      new ConditionalJmpDecoder("jp", ADDR_J, b_mode),
      new ConditionalJmpDecoder("jnp", ADDR_J, b_mode),
      new ConditionalJmpDecoder("jl", ADDR_J, b_mode),
      new ConditionalJmpDecoder("jnl", ADDR_J, b_mode),
      new ConditionalJmpDecoder("jle", ADDR_J, b_mode),
      new ConditionalJmpDecoder("jg", ADDR_J, b_mode),
      /* 80 */
      new GRPDecoder(null, 0),
      new GRPDecoder(null, 1),
      null,
      new GRPDecoder(null, 2),
      new InstructionDecoder("testb", ADDR_E, b_mode, ADDR_G, b_mode),
      new InstructionDecoder("testS", ADDR_E, v_mode, ADDR_G, v_mode),
      new MoveDecoder("xchgb", ADDR_E, b_mode, ADDR_G, b_mode),
      new MoveDecoder("xchgS", ADDR_E, v_mode, ADDR_G, v_mode),
      /* 88 */
      new MoveDecoder("movb", ADDR_E, b_mode, ADDR_G, b_mode),
      new MoveDecoder("movS", ADDR_E, v_mode, ADDR_G, v_mode),
      new MoveDecoder("movb", ADDR_G, b_mode, ADDR_E, b_mode),
      new MoveDecoder("movS", ADDR_G, v_mode, ADDR_E, v_mode),
      new MoveDecoder("movw", ADDR_E, w_mode, ADDR_SEG, w_mode),
      new InstructionDecoder("leaS", ADDR_G, v_mode, ADDR_E, 0),
      new MoveDecoder("movw", ADDR_SEG, w_mode, ADDR_E, w_mode),
      new InstructionDecoder("popS", ADDR_E, v_mode),
      /* 90 */
      new InstructionDecoder("nop"),
      new MoveDecoder("xchgS", ADDR_REG, ECX, ADDR_REG, EAX),
      new MoveDecoder("xchgS", ADDR_REG, EDX, ADDR_REG, EAX),
      new MoveDecoder("xchgS", ADDR_REG, EBX, ADDR_REG, EAX),
      new MoveDecoder("xchgS", ADDR_REG, ESP, ADDR_REG, EAX),
      new MoveDecoder("xchgS", ADDR_REG, EBP, ADDR_REG, EAX),
      new MoveDecoder("xchgS", ADDR_REG, ESI, ADDR_REG, EAX),
      new MoveDecoder("xchgS", ADDR_REG, EDI, ADDR_REG, EAX),
      /* 98 */
      new InstructionDecoder("cwtl"),
      new InstructionDecoder("cltd"),
      new CallDecoder("lcall", ADDR_DIR, p_mode),
      null, /* fwait */
      new InstructionDecoder("pushf"),
      new InstructionDecoder("popf"),
      new InstructionDecoder("sahf"),
      new InstructionDecoder("lahf"),
      /* a0 */
      new MoveDecoder("movb", ADDR_REG, AL, ADDR_OFF, b_mode),
      new MoveDecoder("movS", ADDR_REG, EAX, ADDR_OFF, v_mode),
      new MoveDecoder("movb", ADDR_OFF, b_mode, ADDR_REG, AL),
      new MoveDecoder("movS", ADDR_OFF, v_mode, ADDR_REG, EAX),
      new MoveDecoder("movsb", ADDR_ESDI, b_mode, ADDR_DSSI, b_mode),
      new MoveDecoder("movsS", ADDR_ESDI, v_mode, ADDR_DSSI, v_mode),
      new InstructionDecoder("cmpsb", ADDR_ESDI, b_mode, ADDR_DSSI, b_mode),
      new InstructionDecoder("cmpsS", ADDR_ESDI, v_mode, ADDR_DSSI, v_mode),
      /* a8 */
      new InstructionDecoder("testb", ADDR_REG, AL, ADDR_I, b_mode),
      new InstructionDecoder("testS", ADDR_REG, EAX, ADDR_I, v_mode),
      new InstructionDecoder("stosb", ADDR_ESDI, b_mode, ADDR_REG, AL),
      new InstructionDecoder("stosS", ADDR_ESDI, v_mode, ADDR_REG, EAX),
      new InstructionDecoder("lodsb", ADDR_REG, AL, ADDR_DSSI, b_mode),
      new InstructionDecoder("lodsS", ADDR_REG, EAX, ADDR_DSSI, v_mode),
      new InstructionDecoder("scasb", ADDR_REG, AL, ADDR_ESDI, b_mode),
      new InstructionDecoder("scasS", ADDR_REG, EAX, ADDR_ESDI, v_mode),
      /* b0 */
      new MoveDecoder("movb", ADDR_REG, AL, ADDR_I, b_mode),
      new MoveDecoder("movb", ADDR_REG, CL, ADDR_I, b_mode),
      new MoveDecoder("movb", ADDR_REG, DL, ADDR_I, b_mode),
      new MoveDecoder("movb", ADDR_REG, BL, ADDR_I, b_mode),
      new MoveDecoder("movb", ADDR_REG, AH, ADDR_I, b_mode),
      new MoveDecoder("movb", ADDR_REG, CH, ADDR_I, b_mode),
      new MoveDecoder("movb", ADDR_REG, DH, ADDR_I, b_mode),
      new MoveDecoder("movb", ADDR_REG, BH, ADDR_I, b_mode),
      /* b8 */
      new MoveDecoder("movS", ADDR_REG, EAX, ADDR_I, v_mode),
      new MoveDecoder("movS", ADDR_REG, ECX, ADDR_I, v_mode),
      new MoveDecoder("movS", ADDR_REG, EDX, ADDR_I, v_mode),
      new MoveDecoder("movS", ADDR_REG, EBX, ADDR_I, v_mode),
      new MoveDecoder("movS", ADDR_REG, ESP, ADDR_I, v_mode),
      new MoveDecoder("movS", ADDR_REG, EBP, ADDR_I, v_mode),
      new MoveDecoder("movS", ADDR_REG, ESI, ADDR_I, v_mode),
      new MoveDecoder("movS", ADDR_REG, EDI, ADDR_I, v_mode),
      /* c0 */
      new GRPDecoder(null, 3),
      new GRPDecoder(null, 4),
      new BranchDecoder("ret", ADDR_I, w_mode),
      new BranchDecoder("ret"),
      new InstructionDecoder("lesS", ADDR_G, v_mode, ADDR_E, 0),
      new InstructionDecoder("ldsS", ADDR_G, v_mode, ADDR_E, 0),
      new MoveDecoder("movb", ADDR_E, b_mode, ADDR_I, b_mode),
      new MoveDecoder("movS", ADDR_E, v_mode, ADDR_I, v_mode),
      /* c8 */
      new InstructionDecoder("enter", ADDR_I, w_mode, ADDR_I, b_mode),
      new InstructionDecoder("leave"),
      new InstructionDecoder("lret", ADDR_I, w_mode),
      new InstructionDecoder("lret"),
      new InstructionDecoder("int3"),
      new InstructionDecoder("int", ADDR_I, b_mode),
      new InstructionDecoder("into"),
      new InstructionDecoder("iret"),
      /* d0 */
      new GRPDecoder(null, 5),
      new GRPDecoder(null, 6),
      new GRPDecoder(null, 7),
      new GRPDecoder(null, 8),
      new InstructionDecoder("aam", ADDR_I, b_mode),
      new InstructionDecoder("aad", ADDR_I, b_mode),
      null,
      new InstructionDecoder("xlat"),
      /* d8 */
      new FloatDecoder(),
      new FloatDecoder(),
      new FloatDecoder(),
      new FloatDecoder(),
      new FloatDecoder(),
      new FloatDecoder(),
      new FloatDecoder(),
      new FloatDecoder(),
      /* e0 */
      new BranchDecoder("loopne", ADDR_J, b_mode),
      new BranchDecoder("loope", ADDR_J, b_mode),
      new BranchDecoder("loop", ADDR_J, b_mode),
      new ConditionalJmpDecoder("jCcxz", ADDR_J, b_mode),
      new InstructionDecoder("inb", ADDR_REG, AL, ADDR_I, b_mode),
      new InstructionDecoder("inS", ADDR_REG, EAX, ADDR_I, b_mode),
      new InstructionDecoder("outb", ADDR_I, b_mode, ADDR_REG, AL),
      new InstructionDecoder("outS", ADDR_I, b_mode, ADDR_REG, EAX),
      /* e8 */
      new CallDecoder("call", ADDR_J, v_mode),
      new JmpDecoder("jmp", ADDR_J, v_mode),
      new JmpDecoder("ljmp", ADDR_DIR, p_mode),
      new JmpDecoder("jmp", ADDR_J, b_mode),
      new InstructionDecoder("inb", ADDR_REG, AL, INDIR_REG, DX),
      new InstructionDecoder("inS", ADDR_REG, EAX, INDIR_REG, DX),
      new InstructionDecoder("outb", INDIR_REG, DX, ADDR_REG,AL),
      new InstructionDecoder("outS", INDIR_REG, DX, ADDR_REG, EAX),
      /* f0 */
      new InstructionDecoder("lock"),   /* lock prefix */
      null,
      new InstructionDecoder("repne"),  /* repne */
      new InstructionDecoder("rep"),    /* repz */
      new InstructionDecoder("hlt"),
      new InstructionDecoder("cmc"),
      new GRPDecoder(null, 9),
      new GRPDecoder(null, 10),
      /* f8 */
      new InstructionDecoder("clc"),
      new InstructionDecoder("stc"),
      new InstructionDecoder("cli"),
      new InstructionDecoder("sti"),
      new InstructionDecoder("cld"),
      new InstructionDecoder("std"),
      new GRPDecoder(null, 11),
      new GRPDecoder(null, 12)
   };

   //APPENDIX A - Table A-3. Two-byte Opcode Map
   private static final InstructionDecoder twoByteTable[] = {
      /* 00 */
      new GRPDecoder(null, 13),
      new GRPDecoder(null, 14),
      new InstructionDecoder("larS", ADDR_G, v_mode, ADDR_E, w_mode),
      new InstructionDecoder("lslS", ADDR_G, v_mode, ADDR_E, w_mode),
      null,
      null,
      new InstructionDecoder("clts"),
      null,
      /* 08 */
      new InstructionDecoder("invd"),
      new InstructionDecoder("wbinvd"),
      null,
      null,
      null,
      null,
      null,
      null,
      /* 10 */ //SSE
      new SSEMoveDecoder("movups", ADDR_V, ps_mode, ADDR_W, ps_mode),
      new SSEMoveDecoder("movups", ADDR_W, ps_mode, ADDR_V, ps_mode),
      new SSEMoveDecoder("movlps", ADDR_W, q_mode, ADDR_V, q_mode),
      new SSEMoveDecoder("movlps", ADDR_V, q_mode, ADDR_W, q_mode),
      new SSEInstructionDecoder("unpcklps", ADDR_V, ps_mode, ADDR_W, q_mode),
      new SSEInstructionDecoder("unpckhps", ADDR_V, ps_mode, ADDR_W, q_mode),
      new SSEMoveDecoder("movhps", ADDR_V, q_mode, ADDR_W, q_mode),
      new SSEMoveDecoder("movhps", ADDR_W, q_mode, ADDR_V, q_mode),
      /* 18 */
      new GRPDecoder(null, 21),
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* 20 */
      /* these are all backward in appendix A of the intel book */
      new MoveDecoder("movl", ADDR_R, d_mode, ADDR_C, d_mode),
      new MoveDecoder("movl", ADDR_R, d_mode, ADDR_D, d_mode),
      new MoveDecoder("movl", ADDR_C, d_mode, ADDR_R, d_mode),
      new MoveDecoder("movl", ADDR_D, d_mode, ADDR_R, d_mode),
      new MoveDecoder("movl", ADDR_R, d_mode, ADDR_T, d_mode),
      null,
      new MoveDecoder("movl", ADDR_T, d_mode, ADDR_R, d_mode),
      null,
      /* 28 */
      new SSEMoveDecoder("movaps", ADDR_V, ps_mode, ADDR_W, ps_mode),
      new SSEMoveDecoder("movaps", ADDR_W, ps_mode, ADDR_V, ps_mode),
      new SSEInstructionDecoder("cvtpi2ps", ADDR_V, ps_mode, ADDR_Q, q_mode),
      new SSEMoveDecoder("movntps", ADDR_W, ps_mode, ADDR_V, ps_mode),
      new SSEInstructionDecoder("cvttps2pi", ADDR_Q, q_mode, ADDR_W, ps_mode),
      new SSEInstructionDecoder("cvtps2pi", ADDR_Q, q_mode, ADDR_W, ps_mode),
      new SSEInstructionDecoder("ucomiss", ADDR_V, ss_mode, ADDR_W, ss_mode),
      new SSEInstructionDecoder("comiss", ADDR_V, ps_mode, ADDR_W, ps_mode),
      /* 30 */
      new SSEInstructionDecoder("wrmsr"),
      new SSEInstructionDecoder("rtdsc"),
      new SSEInstructionDecoder("rdmsr"),
      new SSEInstructionDecoder("rdpmc"),
      new SSEInstructionDecoder("sysenter"),
      new SSEInstructionDecoder("sysexit"),
      null,
      null,
      /* 38 */
      null,
      null,
      null,
      null,
      new SSEMoveDecoder("movnti", ADDR_G, v_mode, ADDR_E, v_mode),
      null,
      null,
      null,
      /* 40 */
      new MoveDecoder("cmovo", ADDR_G, v_mode, ADDR_E, v_mode),
      new MoveDecoder("cmovno", ADDR_G, v_mode, ADDR_E, v_mode),
      new MoveDecoder("cmovb", ADDR_G, v_mode, ADDR_E, v_mode),
      new MoveDecoder("cmovae", ADDR_G, v_mode, ADDR_E, v_mode),
      new MoveDecoder("cmove", ADDR_G, v_mode, ADDR_E, v_mode),
      new MoveDecoder("cmovne", ADDR_G, v_mode, ADDR_E, v_mode),
      new MoveDecoder("cmovbe", ADDR_G, v_mode, ADDR_E, v_mode),
      new MoveDecoder("cmova", ADDR_G, v_mode, ADDR_E, v_mode),
      /* 48 */
      new MoveDecoder("cmovs", ADDR_G, v_mode, ADDR_E, v_mode),
      new MoveDecoder("cmovns", ADDR_G, v_mode, ADDR_E, v_mode),
      new MoveDecoder("cmovp", ADDR_G, v_mode, ADDR_E, v_mode),
      new MoveDecoder("cmovnp", ADDR_G, v_mode, ADDR_E, v_mode),
      new MoveDecoder("cmovl", ADDR_G, v_mode, ADDR_E, v_mode),
      new MoveDecoder("cmovge", ADDR_G, v_mode, ADDR_E, v_mode),
      new MoveDecoder("cmovle", ADDR_G, v_mode, ADDR_E, v_mode),
      new MoveDecoder("cmovg", ADDR_G, v_mode, ADDR_E, v_mode),
      /* 50 */
      new SSEMoveDecoder("movmskps", ADDR_E, d_mode, ADDR_V, ps_mode),
      new SSEInstructionDecoder("sqrtps", ADDR_V, ps_mode, ADDR_W, ps_mode),
      new SSEInstructionDecoder("rsqrtps", ADDR_V, ps_mode, ADDR_W, ps_mode),
      new SSEInstructionDecoder("rcpps", ADDR_V, ps_mode, ADDR_W, ps_mode),
      new SSELogicalDecoder("andps", ADDR_V, ps_mode, ADDR_W, ps_mode, RTLOP_AND),
      new SSELogicalDecoder("andnps", ADDR_V, ps_mode, ADDR_W, ps_mode, RTLOP_AND),
      new SSELogicalDecoder("orps", ADDR_V, ps_mode, ADDR_W, ps_mode, RTLOP_OR),
      new SSELogicalDecoder("xorps", ADDR_V, ps_mode, ADDR_W, ps_mode, RTLOP_XOR),
      /* 58 */
      new SSEArithmeticDecoder("addps", ADDR_V, ps_mode, ADDR_W, ps_mode, RTLOP_ADD),
      new SSEArithmeticDecoder("mulps", ADDR_V, ps_mode, ADDR_W, ps_mode, RTLOP_SMUL),
      new SSEInstructionDecoder("cvtps2pd", ADDR_V, pd_mode, ADDR_W, ps_mode),
      new SSEInstructionDecoder("cvtdq2ps", ADDR_V, ps_mode, ADDR_W, dq_mode),
      new SSEArithmeticDecoder("subps", ADDR_V, ps_mode, ADDR_W, ps_mode, RTLOP_SUB),
      new SSEInstructionDecoder("minps", ADDR_V, ps_mode, ADDR_W, ps_mode),
      new SSEArithmeticDecoder("divps", ADDR_V, ps_mode, ADDR_W, ps_mode, RTLOP_SDIV),
      new SSEInstructionDecoder("maxps", ADDR_V, ps_mode, ADDR_W, ps_mode),
      /* 60 */
      new SSEInstructionDecoder("punpcklbw", ADDR_P, q_mode, ADDR_Q, d_mode),
      new SSEInstructionDecoder("punpcklwd", ADDR_P, q_mode, ADDR_Q, d_mode),
      new SSEInstructionDecoder("punpckldq", ADDR_P, q_mode, ADDR_Q, d_mode),
      new SSEInstructionDecoder("packsswb", ADDR_P, q_mode, ADDR_Q, q_mode),
      new SSEInstructionDecoder("pcmpgtb", ADDR_P, q_mode, ADDR_Q, q_mode),
      new SSEInstructionDecoder("pcmpgtw", ADDR_P, q_mode, ADDR_Q, q_mode),
      new SSEInstructionDecoder("pcmpgtd", ADDR_P, q_mode, ADDR_Q, q_mode),
      new SSEInstructionDecoder("packuswb", ADDR_P, q_mode, ADDR_Q, q_mode),
      /* 68 */
      new SSEInstructionDecoder("punpckhbw", ADDR_P, q_mode, ADDR_Q, d_mode),
      new SSEInstructionDecoder("punpckhwd", ADDR_P, q_mode, ADDR_Q, d_mode),
      new SSEInstructionDecoder("punpckhdq", ADDR_P, q_mode, ADDR_Q, d_mode),
      new SSEInstructionDecoder("packssdw", ADDR_P, q_mode, ADDR_Q, d_mode),
      null,
      null,
      new SSEMoveDecoder("movd", ADDR_P, d_mode, ADDR_E, d_mode),
      new SSEMoveDecoder("movq", ADDR_P, q_mode, ADDR_E, q_mode),
      /* 70 */
      new SSEInstructionDecoder("pshufw", ADDR_P, q_mode, ADDR_Q, q_mode, ADDR_I, b_mode),
      new GRPDecoder(null, 17),
      new GRPDecoder(null, 18),
      new GRPDecoder(null, 19),
      new SSEInstructionDecoder("pcmpeqb", ADDR_P, q_mode, ADDR_Q, q_mode),
      new SSEInstructionDecoder("pcmpeqw", ADDR_P, q_mode, ADDR_Q, q_mode),
      new SSEInstructionDecoder("pcmpeqd", ADDR_P, q_mode, ADDR_Q, q_mode),
      new SSEInstructionDecoder("emms"),
      /* 78 */
      null,
      null,
      null,
      null,
      null,
      null,
      new SSEMoveDecoder("movd", ADDR_E, d_mode, ADDR_P, d_mode),
      new SSEMoveDecoder("movq", ADDR_Q, q_mode, ADDR_P, q_mode),
      /* 80 */
      new ConditionalJmpDecoder("jo", ADDR_J, v_mode),
      new ConditionalJmpDecoder("jno", ADDR_J, v_mode),
      new ConditionalJmpDecoder("jb", ADDR_J, v_mode),
      new ConditionalJmpDecoder("jae", ADDR_J, v_mode),
      new ConditionalJmpDecoder("je", ADDR_J, v_mode),
      new ConditionalJmpDecoder("jne", ADDR_J, v_mode),
      new ConditionalJmpDecoder("jbe", ADDR_J, v_mode),
      new ConditionalJmpDecoder("ja", ADDR_J, v_mode),
      /* 88 */
      new ConditionalJmpDecoder("js", ADDR_J, v_mode),
      new ConditionalJmpDecoder("jns", ADDR_J, v_mode),
      new ConditionalJmpDecoder("jp", ADDR_J, v_mode),
      new ConditionalJmpDecoder("jnp", ADDR_J, v_mode),
      new ConditionalJmpDecoder("jl", ADDR_J, v_mode),
      new ConditionalJmpDecoder("jge", ADDR_J, v_mode),
      new ConditionalJmpDecoder("jle", ADDR_J, v_mode),
      new ConditionalJmpDecoder("jg", ADDR_J, v_mode),
      /* 90 */
      new InstructionDecoder("seto", ADDR_E, b_mode),
      new InstructionDecoder("setno", ADDR_E, b_mode),
      new InstructionDecoder("setb", ADDR_E, b_mode),
      new InstructionDecoder("setae", ADDR_E, b_mode),
      new InstructionDecoder("sete", ADDR_E, b_mode),
      new InstructionDecoder("setne", ADDR_E, b_mode),
      new InstructionDecoder("setbe", ADDR_E, b_mode),
      new InstructionDecoder("seta", ADDR_E, b_mode),
      /* 98 */
      new InstructionDecoder("sets", ADDR_E, b_mode),
      new InstructionDecoder("setns", ADDR_E, b_mode),
      new InstructionDecoder("setp", ADDR_E, b_mode),
      new InstructionDecoder("setnp", ADDR_E, b_mode),
      new InstructionDecoder("setl", ADDR_E, b_mode),
      new InstructionDecoder("setge", ADDR_E, b_mode),
      new InstructionDecoder("setle", ADDR_E, b_mode),
      new InstructionDecoder("setg", ADDR_E, b_mode),
      /* a0 */
      new InstructionDecoder("pushl", ADDR_REG, FS),
      new InstructionDecoder("popl", ADDR_REG, FS),
      null,
      new InstructionDecoder("btS", ADDR_E, v_mode, ADDR_G, v_mode),
      new InstructionDecoder("shldS", ADDR_E, v_mode, ADDR_G, v_mode, ADDR_I, b_mode),
      new InstructionDecoder("shldS", ADDR_E, v_mode, ADDR_G, v_mode, ADDR_REG, CL),
      null,
      null,
      /* a8 */
      new InstructionDecoder("pushl", ADDR_REG, GS),
      new InstructionDecoder("popl", ADDR_REG, GS),
      new SSEInstructionDecoder("rsm"),
      new InstructionDecoder("btsS", ADDR_E, v_mode, ADDR_G, v_mode),
      new InstructionDecoder("shrdS", ADDR_E, v_mode, ADDR_G, v_mode, ADDR_I, b_mode),
      new InstructionDecoder("shrdS", ADDR_E, v_mode, ADDR_G, v_mode, ADDR_REG, CL),
      new GRPDecoder(null, 20),
      new ArithmeticDecoder("imulS", ADDR_G, v_mode, ADDR_E, v_mode, RTLOP_SMUL),
      /* b0 */
      new InstructionDecoder("cmpxchgb", ADDR_E, b_mode, ADDR_G, b_mode),
      new InstructionDecoder("cmpxchgS", ADDR_E, v_mode, ADDR_G, v_mode),
      new InstructionDecoder("lssS", ADDR_G, v_mode, ADDR_M, p_mode),
      new InstructionDecoder("btrS", ADDR_E, v_mode, ADDR_G, v_mode),
      new InstructionDecoder("lfsS", ADDR_G, v_mode, ADDR_M, p_mode),
      new InstructionDecoder("lgsS", ADDR_G, v_mode, ADDR_M, p_mode),
      new MoveDecoder("movzbS", ADDR_G, v_mode, ADDR_E, b_mode),
      new MoveDecoder("movzwS", ADDR_G, v_mode, ADDR_E, w_mode),
      /* b8 */
      null,
      null,
      new GRPDecoder(null, 15),
      new InstructionDecoder("btcS", ADDR_E, v_mode, ADDR_G, v_mode),
      new InstructionDecoder("bsfS", ADDR_G, v_mode, ADDR_E, v_mode),
      new InstructionDecoder("bsrS", ADDR_G, v_mode, ADDR_E, v_mode),
      new MoveDecoder("movsbS", ADDR_G, v_mode, ADDR_E, b_mode),
      new MoveDecoder("movswS", ADDR_G, v_mode, ADDR_E, w_mode),
      /* c0 */
      new ArithmeticDecoder("xaddb", ADDR_E, b_mode, ADDR_G, b_mode, RTLOP_ADD),
      new ArithmeticDecoder("xaddS", ADDR_E, v_mode, ADDR_G, v_mode, RTLOP_ADD),
      new SSEInstructionDecoder("cmpps", ADDR_V, ps_mode, ADDR_W, ps_mode, ADDR_I, b_mode),
      new SSEMoveDecoder("movnti", ADDR_E, d_mode, ADDR_G, d_mode),
      new SSEInstructionDecoder("pinsrw", ADDR_P, q_mode, ADDR_E, d_mode, ADDR_I, b_mode),
      new SSEInstructionDecoder("pextrw", ADDR_G, d_mode, ADDR_P, q_mode, ADDR_I, b_mode),
      new SSEInstructionDecoder("shufps", ADDR_V, ps_mode, ADDR_W, ps_mode, ADDR_I, b_mode),
      new GRPDecoder(null, 16),
      /* c8 */
      new InstructionDecoder("bswap", ADDR_REG, EAX),
      new InstructionDecoder("bswap", ADDR_REG, ECX),
      new InstructionDecoder("bswap", ADDR_REG, EDX),
      new InstructionDecoder("bswap", ADDR_REG, EBX),
      new InstructionDecoder("bswap", ADDR_REG, ESP),
      new InstructionDecoder("bswap", ADDR_REG, EBP),
      new InstructionDecoder("bswap", ADDR_REG, ESI),
      new InstructionDecoder("bswap", ADDR_REG, EDI),
      /* d0 */
      null,
      new SSEShiftDecoder("psrlw", ADDR_P, q_mode, ADDR_Q, q_mode, RTLOP_SRL),
      new SSEShiftDecoder("psrld", ADDR_P, q_mode, ADDR_Q, q_mode, RTLOP_SRL),
      new SSEShiftDecoder("psrlq", ADDR_P, q_mode, ADDR_Q, q_mode, RTLOP_SRL),
      new SSEArithmeticDecoder("paddq", ADDR_P, q_mode, ADDR_Q, q_mode, RTLOP_ADD),
      new SSEArithmeticDecoder("pmullw", ADDR_P, q_mode, ADDR_Q, q_mode, RTLOP_SMUL),
      null,
      new SSEMoveDecoder("pmovmskb", ADDR_G, d_mode, ADDR_P, q_mode),
      /* d8 */
      new SSEArithmeticDecoder("psubusb", ADDR_P, q_mode, ADDR_Q, q_mode, RTLOP_SUB),
      new SSEArithmeticDecoder("psubusw", ADDR_P, q_mode, ADDR_Q, q_mode, RTLOP_SUB),
      new SSEInstructionDecoder("pminub", ADDR_P, q_mode, ADDR_Q, q_mode),
      new SSELogicalDecoder("pand", ADDR_P, q_mode, ADDR_Q, q_mode, RTLOP_AND),
      new SSEArithmeticDecoder("paddusb", ADDR_P, q_mode, ADDR_Q, q_mode, RTLOP_ADD),
      new SSEArithmeticDecoder("paddusw", ADDR_P, q_mode, ADDR_Q, q_mode, RTLOP_ADD),
      new SSEInstructionDecoder("pmaxub", ADDR_P, q_mode, ADDR_Q, q_mode),
      new SSELogicalDecoder("pandn", ADDR_P, q_mode, ADDR_Q, q_mode, RTLOP_AND),
      /* e0 */
      new SSEInstructionDecoder("pavgb", ADDR_P, q_mode, ADDR_Q, q_mode),
      new SSEInstructionDecoder("psraw", ADDR_P, q_mode, ADDR_Q, q_mode),
      new SSEInstructionDecoder("psrad", ADDR_P, q_mode, ADDR_Q, q_mode),
      new SSEInstructionDecoder("pavgw", ADDR_P, q_mode, ADDR_Q, q_mode),
      new SSEArithmeticDecoder("pmulhuw", ADDR_P, q_mode, ADDR_Q, q_mode, RTLOP_UMUL),
      new SSEArithmeticDecoder("pmulhw", ADDR_P, q_mode, ADDR_Q, q_mode, RTLOP_SMUL),
      null,
      new SSEMoveDecoder("movntq", ADDR_W, q_mode, ADDR_V, q_mode),
      /* e8 */
      new SSEArithmeticDecoder("psubsb", ADDR_P, q_mode, ADDR_Q, q_mode, RTLOP_SUB),
      new SSEArithmeticDecoder("psubsw", ADDR_P, q_mode, ADDR_Q, q_mode, RTLOP_SUB),
      new SSEInstructionDecoder("pminsw", ADDR_P, q_mode, ADDR_Q, q_mode),
      new SSELogicalDecoder("por", ADDR_P, q_mode, ADDR_Q, q_mode, RTLOP_OR),
      new SSEArithmeticDecoder("paddsb", ADDR_P, q_mode, ADDR_Q, q_mode, RTLOP_ADD),
      new SSEArithmeticDecoder("paddsw", ADDR_P, q_mode, ADDR_Q, q_mode, RTLOP_ADD),
      new SSEInstructionDecoder("pmaxsw", ADDR_P, q_mode, ADDR_Q, q_mode),
      new SSELogicalDecoder("pxor", ADDR_P, q_mode, ADDR_Q, q_mode, RTLOP_XOR),
      /* f0 */
      null,
      new SSEShiftDecoder("psllw", ADDR_P, q_mode, ADDR_Q, q_mode, RTLOP_SLL),
      new SSEShiftDecoder("pslld", ADDR_P, q_mode, ADDR_Q, q_mode, RTLOP_SLL),
      new SSEShiftDecoder("psllq", ADDR_P, q_mode, ADDR_Q, q_mode, RTLOP_SLL),
      new SSEArithmeticDecoder("pmuludq", ADDR_P, q_mode, ADDR_Q, q_mode, RTLOP_UMUL),
      new SSEArithmeticDecoder("pmaddwd", ADDR_P, q_mode, ADDR_Q, q_mode, RTLOP_ADD),
      new SSEArithmeticDecoder("psadbw", ADDR_P, q_mode, ADDR_Q, q_mode, RTLOP_ADD),
      new SSEMoveDecoder("maskmoveq", ADDR_P, pi_mode, ADDR_Q, pi_mode),
      /* f8 */
      new SSEArithmeticDecoder("psubb", ADDR_P, q_mode, ADDR_Q, q_mode, RTLOP_SUB),
      new SSEArithmeticDecoder("psubw", ADDR_P, q_mode, ADDR_Q, q_mode, RTLOP_SUB),
      new SSEArithmeticDecoder("psubd", ADDR_P, q_mode, ADDR_Q, q_mode, RTLOP_SUB),
      new SSEArithmeticDecoder("psubq", ADDR_P, q_mode, ADDR_Q, q_mode, RTLOP_SUB),
      new SSEArithmeticDecoder("paddb", ADDR_P, q_mode, ADDR_Q, q_mode, RTLOP_ADD),
      new SSEArithmeticDecoder("paddw", ADDR_P, q_mode, ADDR_Q, q_mode, RTLOP_ADD),
      new SSEArithmeticDecoder("paddd", ADDR_P, q_mode, ADDR_Q, q_mode, RTLOP_ADD),
      null
   };

   private static final InstructionDecoder twoBytePrefixF2Table[] = {
      /* 00 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* 08 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* 10 */
      new SSEMoveDecoder("movsd", ADDR_V, sd_mode, ADDR_W, sd_mode),
      new SSEMoveDecoder("movsd", ADDR_V, sd_mode, ADDR_W, sd_mode),
      null,
      null,
      null,
      null,
      null,
      null,
      /* 18 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* 20 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* 28 */
      null,
      null,
      new SSEInstructionDecoder("cvtsi2sd", ADDR_V, sd_mode, ADDR_E, d_mode),
      null,
      new SSEInstructionDecoder("cvttsd2si", ADDR_G, d_mode, ADDR_W, sd_mode),
      new SSEInstructionDecoder("cvtsd2si", ADDR_G, d_mode, ADDR_W, sd_mode),
      null,
      null,
      /* 30 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* 38 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* 40 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* 48 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* 50 */
      null,
      new SSEInstructionDecoder("sqrtsd", ADDR_V, sd_mode, ADDR_W, sd_mode),
      null,
      null,
      null,
      null,
      null,
      null,
      /* 58 */
      new SSEArithmeticDecoder("addsd", ADDR_V, sd_mode, ADDR_W, sd_mode, RTLOP_ADD),
      new SSEArithmeticDecoder("mulsd", ADDR_V, sd_mode, ADDR_W, sd_mode, RTLOP_SMUL),
      new SSEInstructionDecoder("cvtsd2ss", ADDR_V, sd_mode, ADDR_W, sd_mode),
      null,
      new SSEArithmeticDecoder("subsd", ADDR_V, sd_mode, ADDR_W, sd_mode, RTLOP_SUB),
      new SSEInstructionDecoder("minsd", ADDR_V, sd_mode, ADDR_W, sd_mode),
      new SSEArithmeticDecoder("divsd", ADDR_V, sd_mode, ADDR_W, sd_mode, RTLOP_SDIV),
      new SSEInstructionDecoder("maxsd", ADDR_V, sd_mode, ADDR_W, sd_mode),
      /* 60 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* 68 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* 70 */
      new SSEInstructionDecoder("pshuflw", ADDR_V, dq_mode, ADDR_W, dq_mode, ADDR_I, b_mode),
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* 78 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* 80 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* 88 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* 90 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* 98 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* a0 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* a8 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* b0 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* b8 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* c0 */
      null,
      null,
      new SSEInstructionDecoder("cmpsd", ADDR_V, sd_mode, ADDR_W, sd_mode, ADDR_I, b_mode),
      null,
      null,
      null,
      null,
      null,
      /* c8 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* d0 */
      null,
      null,
      null,
      null,
      null,
      null,
      new SSEMoveDecoder("movdq2q", ADDR_P, q_mode, ADDR_W, q_mode),
      null,
      /* d8 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* e0 */
      null,
      null,
      null,
      null,
      null,
      null,
      new SSEInstructionDecoder("cvtpd2dq", ADDR_V, dq_mode, ADDR_W, pd_mode),
      null,
      /* e8 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* f0 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* f8 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null
   };

   private static final InstructionDecoder twoBytePrefixF3Table[] = {
      /* 00 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* 08 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* 10 */
      new SSEMoveDecoder("movss", ADDR_V, ss_mode, ADDR_W, ss_mode),
      new SSEMoveDecoder("movss", ADDR_W, ss_mode, ADDR_V, ss_mode),
      null,
      null,
      null,
      null,
      null,
      null,
      /* 18 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* 20 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* 28 */
      null,
      null,
      new SSEInstructionDecoder("cvtsi2ss", ADDR_V, ss_mode, ADDR_E, d_mode),
      null,
      new SSEInstructionDecoder("cvttss2si", ADDR_G, d_mode, ADDR_W, ss_mode),
      new SSEInstructionDecoder("cvtss2si", ADDR_G, d_mode, ADDR_W, ss_mode),
      null,
      null,
      /* 30 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* 38 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* 40 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* 48 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* 50 */
      null,
      new SSEInstructionDecoder("sqrtss", ADDR_V, ss_mode, ADDR_W, ss_mode),
      new SSEInstructionDecoder("rsqrtss", ADDR_V, ss_mode, ADDR_W, ss_mode),
      new SSEInstructionDecoder("rcpss", ADDR_V, ss_mode, ADDR_W, ss_mode),
      null,
      null,
      null,
      null,
      /* 58 */
      new SSEArithmeticDecoder("addss", ADDR_V, ss_mode, ADDR_W, ss_mode, RTLOP_ADD),
      new SSEArithmeticDecoder("mulss", ADDR_V, ss_mode, ADDR_W, ss_mode, RTLOP_SMUL),
      new SSEInstructionDecoder("cvtss2sd", ADDR_V, ss_mode, ADDR_W, ss_mode),
      new SSEInstructionDecoder("cvttps2dq", ADDR_V, dq_mode, ADDR_W, ps_mode),
      new SSEArithmeticDecoder("subss", ADDR_V, ss_mode, ADDR_W, ss_mode, RTLOP_SUB),
      new SSEInstructionDecoder("minss", ADDR_V, ss_mode, ADDR_W, ss_mode),
      new SSEArithmeticDecoder("divss", ADDR_V, ss_mode, ADDR_W, ss_mode, RTLOP_SDIV),
      new SSEInstructionDecoder("maxss", ADDR_V, ss_mode, ADDR_W, ss_mode),
      /* 60 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* 68 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      new SSEMoveDecoder("movdqu", ADDR_V, dq_mode, ADDR_W, dq_mode),
      /* 70 */
      new SSEInstructionDecoder("pshufhw", ADDR_V, dq_mode, ADDR_W, dq_mode, ADDR_I, b_mode),
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* 78 */
      null,
      null,
      null,
      null,
      null,
      null,
      new SSEMoveDecoder("movq", ADDR_V, q_mode, ADDR_W, q_mode),
      new SSEMoveDecoder("movdqu", ADDR_W, dq_mode, ADDR_V, dq_mode),
      /* 80 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* 88 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* 90 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* 98 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* a0 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* a8 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* b0 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* b8 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* c0 */
      null,
      null,
      new SSEInstructionDecoder("cmpss", ADDR_V, ss_mode, ADDR_W, ss_mode, ADDR_I, b_mode),
      null,
      null,
      null,
      null,
      null,
      /* c8 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* d0 */
      null,
      null,
      null,
      null,
      null,
      null,
      new SSEMoveDecoder("movq2dq", ADDR_V, dq_mode, ADDR_Q, q_mode),
      null,
      /* d8 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* e0 */
      null,
      null,
      null,
      null,
      null,
      null,
      new SSEInstructionDecoder("cvtdq2pd", ADDR_V, pd_mode, ADDR_W, dq_mode),
      null,
      /* e8 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* f0 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* f8 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null
   };

   private static final InstructionDecoder twoBytePrefix66Table[] = {
      /* 00 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* 08 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* 10 */
      new SSEMoveDecoder("movupd", ADDR_V, pd_mode, ADDR_W, pd_mode),
      new SSEMoveDecoder("movupd", ADDR_W, pd_mode, ADDR_V, pd_mode),
      new SSEMoveDecoder("movlpd", ADDR_V, q_mode, ADDR_W, s_mode),
      new SSEMoveDecoder("movlpd", ADDR_V, q_mode, ADDR_W, q_mode),
      new SSEInstructionDecoder("unpcklpd", ADDR_V, pd_mode, ADDR_W, q_mode),
      new SSEInstructionDecoder("unpckhpd", ADDR_V, pd_mode, ADDR_W, q_mode),
      new SSEMoveDecoder("movhpd", ADDR_V, q_mode, ADDR_W, q_mode),
      new SSEMoveDecoder("movhpd", ADDR_W, q_mode, ADDR_V, q_mode),
      /* 18 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* 20 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* 28 */
      new SSEMoveDecoder("movapd", ADDR_V, pd_mode, ADDR_W, pd_mode),
      new SSEMoveDecoder("movapd", ADDR_W, pd_mode, ADDR_V, pd_mode),
      new SSEInstructionDecoder("cvtpi2pd", ADDR_V, pd_mode, ADDR_Q, dq_mode),
      new SSEMoveDecoder("movntpd", ADDR_W, pd_mode, ADDR_V, pd_mode),
      new SSEInstructionDecoder("cvttpd2pi", ADDR_Q, dq_mode, ADDR_W, pd_mode),
      new SSEInstructionDecoder("cvtpd2pi", ADDR_Q, dq_mode, ADDR_W, pd_mode),
      new SSEInstructionDecoder("ucomisd", ADDR_V, sd_mode, ADDR_W, sd_mode),
      new SSEInstructionDecoder("comisd", ADDR_V, sd_mode, ADDR_W, sd_mode),
      /* 30 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* 38 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* 40 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* 48 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* 50 */
      new SSEMoveDecoder("movmskpd", ADDR_E, d_mode, ADDR_V, pd_mode),
      new SSEInstructionDecoder("sqrtpd", ADDR_V, pd_mode, ADDR_W, pd_mode),
      null,
      null,
      new SSELogicalDecoder("andpd", ADDR_V, pd_mode, ADDR_W, pd_mode, RTLOP_AND),
      new SSELogicalDecoder("andnpd", ADDR_V, pd_mode, ADDR_W, pd_mode, RTLOP_AND),
      new SSELogicalDecoder("orpd", ADDR_V, pd_mode, ADDR_W, pd_mode, RTLOP_OR),
      new SSELogicalDecoder("xorpd", ADDR_V, pd_mode, ADDR_W, pd_mode, RTLOP_XOR),
      /* 58 */
      new SSEArithmeticDecoder("addpd", ADDR_V, pd_mode, ADDR_W, pd_mode, RTLOP_ADD),
      new SSEArithmeticDecoder("mulpd", ADDR_V, pd_mode, ADDR_W, pd_mode, RTLOP_SMUL),
      new SSEInstructionDecoder("cvtpd2ps", ADDR_V, ps_mode, ADDR_W, pd_mode),
      new SSEInstructionDecoder("cvtps2dq", ADDR_V, dq_mode, ADDR_W, ps_mode),
      new SSEArithmeticDecoder("subpd", ADDR_V, pd_mode, ADDR_W, pd_mode, RTLOP_SUB),
      new SSEInstructionDecoder("minpd", ADDR_V, pd_mode, ADDR_W, pd_mode),
      new SSEArithmeticDecoder("divpd", ADDR_V, pd_mode, ADDR_W, pd_mode, RTLOP_SDIV),
      new SSEInstructionDecoder("maxpd", ADDR_V, pd_mode, ADDR_W, pd_mode),
      /* 60 */
      new SSEInstructionDecoder("punpcklbw", ADDR_V, dq_mode, ADDR_W, dq_mode),
      new SSEInstructionDecoder("punpcklwd", ADDR_V, dq_mode, ADDR_W, dq_mode),
      new SSEInstructionDecoder("punpckldq", ADDR_V, dq_mode, ADDR_W, dq_mode),
      new SSEInstructionDecoder("packsswb", ADDR_V, dq_mode, ADDR_W, dq_mode),
      new SSEInstructionDecoder("pcmpgtb", ADDR_V, dq_mode, ADDR_W, dq_mode),
      new SSEInstructionDecoder("pcmpgtw", ADDR_V, dq_mode, ADDR_W, dq_mode),
      new SSEInstructionDecoder("pcmpgtd", ADDR_V, dq_mode, ADDR_W, dq_mode),
      new SSEInstructionDecoder("packuswb", ADDR_V, dq_mode, ADDR_W, dq_mode),
      /* 68 */
      new SSEInstructionDecoder("punpckhbw", ADDR_P, dq_mode, ADDR_Q, dq_mode),
      new SSEInstructionDecoder("punpckhwd", ADDR_P, dq_mode, ADDR_Q, dq_mode),
      new SSEInstructionDecoder("punpckhdq", ADDR_P, dq_mode, ADDR_Q, dq_mode),
      new SSEInstructionDecoder("packssdw", ADDR_P, dq_mode, ADDR_Q, dq_mode),
      new SSEInstructionDecoder("punpcklqdq", ADDR_V, dq_mode, ADDR_W, dq_mode),
      new SSEInstructionDecoder("punpckhqdq", ADDR_V, dq_mode, ADDR_W, dq_mode),
      new SSEMoveDecoder("movd", ADDR_V, dq_mode, ADDR_E, d_mode),
      new SSEMoveDecoder("movdqa", ADDR_V, dq_mode, ADDR_W, dq_mode),
      /* 70 */
      new SSEInstructionDecoder("pshufd", ADDR_V, dq_mode, ADDR_W, dq_mode, ADDR_I, b_mode),
      new GRPDecoder(null, 22),
      new GRPDecoder(null, 23),
      new GRPDecoder(null, 24),
      new SSEInstructionDecoder("pcmpeqb", ADDR_V, dq_mode, ADDR_W, dq_mode),
      new SSEInstructionDecoder("pcmpeqw", ADDR_V, dq_mode, ADDR_W, dq_mode),
      new SSEInstructionDecoder("pcmpeqd", ADDR_V, dq_mode, ADDR_W, dq_mode),
      null,
      /* 78 */
      null,
      null,
      null,
      null,
      null,
      null,
      new SSEMoveDecoder("movd", ADDR_E, d_mode, ADDR_V, dq_mode),
      new SSEMoveDecoder("movdqa", ADDR_W, dq_mode, ADDR_V, dq_mode),
      /* 80 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* 88 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* 90 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* 98 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* a0 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* a8 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* b0 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* b8 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* c0 */
      null,
      null,
      new SSEInstructionDecoder("cmppd", ADDR_V, pd_mode, ADDR_W, pd_mode, ADDR_I, b_mode),
      null,
      new SSEInstructionDecoder("pinsrw", ADDR_V, dq_mode, ADDR_E, d_mode, ADDR_I, b_mode),
      new SSEInstructionDecoder("pextrw", ADDR_G, d_mode, ADDR_V, dq_mode, ADDR_I, b_mode),
      new SSEInstructionDecoder("shufpd", ADDR_V, pd_mode, ADDR_W, pd_mode, ADDR_I, b_mode),
      null,
      /* c8 */
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      /* d0 */
      null,
      new SSEShiftDecoder("psrlw", ADDR_V, dq_mode, ADDR_W, dq_mode, RTLOP_SRL),
      new SSEShiftDecoder("psrld", ADDR_V, dq_mode, ADDR_W, dq_mode, RTLOP_SRL),
      new SSEShiftDecoder("psrlq", ADDR_V, dq_mode, ADDR_W, dq_mode, RTLOP_SRL),
      new SSEArithmeticDecoder("paddq", ADDR_V, dq_mode, ADDR_W, dq_mode, RTLOP_ADD),
      new SSEArithmeticDecoder("pmullw", ADDR_V, dq_mode, ADDR_W, dq_mode, RTLOP_SMUL),
      new SSEMoveDecoder("movq", ADDR_W, q_mode, ADDR_V, q_mode),
      new SSEMoveDecoder("pmovmskb", ADDR_G, d_mode, ADDR_V, dq_mode),
      /* d8 */
      new SSEArithmeticDecoder("psubusb", ADDR_V, dq_mode, ADDR_W, dq_mode, RTLOP_SUB),
      new SSEArithmeticDecoder("psubusw", ADDR_V, dq_mode, ADDR_W, dq_mode, RTLOP_SUB),
      new SSEInstructionDecoder("pminub", ADDR_V, dq_mode, ADDR_W, dq_mode),
      new SSELogicalDecoder("pand", ADDR_V, dq_mode, ADDR_W, dq_mode, RTLOP_AND),
      new SSEArithmeticDecoder("paddusb", ADDR_V, dq_mode, ADDR_W, dq_mode, RTLOP_ADD),
      new SSEArithmeticDecoder("paddusw", ADDR_V, dq_mode, ADDR_W, dq_mode, RTLOP_ADD),
      new SSEInstructionDecoder("pmaxub", ADDR_V, dq_mode, ADDR_W, dq_mode),
      new SSELogicalDecoder("pandn", ADDR_V, dq_mode, ADDR_W, dq_mode, RTLOP_AND),
      /* e0 */
      new SSEInstructionDecoder("pavgb", ADDR_V, dq_mode, ADDR_W, dq_mode),
      new SSEInstructionDecoder("psraw", ADDR_V, dq_mode, ADDR_W, dq_mode),
      new SSEInstructionDecoder("psrad", ADDR_V, dq_mode, ADDR_W, dq_mode),
      new SSEInstructionDecoder("pavgw", ADDR_V, dq_mode, ADDR_W, dq_mode),
      new SSEArithmeticDecoder("pmulhuw", ADDR_V, dq_mode, ADDR_W, dq_mode, RTLOP_UMUL),
      new SSEArithmeticDecoder("pmulhw", ADDR_V, dq_mode, ADDR_W, dq_mode, RTLOP_SMUL),
      new SSEInstructionDecoder("cvttpd2dq", ADDR_V, dq_mode, ADDR_W, pd_mode),
      new SSEMoveDecoder("movntdq", ADDR_W, dq_mode, ADDR_V, dq_mode),
      /* e8 */
      new SSEArithmeticDecoder("psubusb", ADDR_V, dq_mode, ADDR_W, dq_mode, RTLOP_SUB),
      new SSEArithmeticDecoder("psubusw", ADDR_V, dq_mode, ADDR_W, dq_mode, RTLOP_SUB),
      new SSEInstructionDecoder("pminsw", ADDR_V, dq_mode, ADDR_W, dq_mode),
      new SSELogicalDecoder("por", ADDR_V, dq_mode, ADDR_W, dq_mode, RTLOP_OR),
      new SSEArithmeticDecoder("paddsb", ADDR_V, dq_mode, ADDR_W, dq_mode, RTLOP_ADD),
      new SSEArithmeticDecoder("paddsw", ADDR_V, dq_mode, ADDR_W, dq_mode, RTLOP_ADD),
      new SSEInstructionDecoder("pmaxsw", ADDR_V, dq_mode, ADDR_W, dq_mode),
      new SSELogicalDecoder("pxor", ADDR_V, dq_mode, ADDR_W, dq_mode, RTLOP_XOR),
      /* f0 */
      null,
      new SSEShiftDecoder("psllw", ADDR_V, dq_mode, ADDR_W, dq_mode, RTLOP_SLL),
      new SSEShiftDecoder("pslld", ADDR_V, dq_mode, ADDR_W, dq_mode, RTLOP_SLL),
      new SSEShiftDecoder("psllq", ADDR_V, dq_mode, ADDR_W, dq_mode, RTLOP_SLL),
      new SSEArithmeticDecoder("pmuludq", ADDR_V, dq_mode, ADDR_W, dq_mode, RTLOP_UMUL),
      new SSEArithmeticDecoder("pmaddwd", ADDR_V, dq_mode, ADDR_W, dq_mode, RTLOP_ADD),
      new SSEArithmeticDecoder("psadbw", ADDR_V, dq_mode, ADDR_W, dq_mode, RTLOP_ADD),
      new SSEMoveDecoder("maskmovdqu", ADDR_V, dq_mode, ADDR_W, dq_mode),
      /* f8 */
      new SSEArithmeticDecoder("psubb", ADDR_V, dq_mode, ADDR_W, dq_mode, RTLOP_SUB),
      new SSEArithmeticDecoder("psubw", ADDR_V, dq_mode, ADDR_W, dq_mode, RTLOP_SUB),
      new SSEArithmeticDecoder("psubd", ADDR_V, dq_mode, ADDR_W, dq_mode, RTLOP_SUB),
      new SSEArithmeticDecoder("psubq", ADDR_V, dq_mode, ADDR_W, dq_mode, RTLOP_SUB),
      new SSEArithmeticDecoder("paddb", ADDR_V, dq_mode, ADDR_W, dq_mode, RTLOP_ADD),
      new SSEArithmeticDecoder("paddw", ADDR_V, dq_mode, ADDR_W, dq_mode, RTLOP_ADD),
      new SSEArithmeticDecoder("paddd", ADDR_V, dq_mode, ADDR_W, dq_mode, RTLOP_ADD),
      null
   };

   public void decode(InstructionVisitor visitor) {
      int enter_instruction = 0;
      Instruction instr = null;
      visitor.prologue();
      InstructionDecoder instrDecoder = null;
      try {
         byteIndex = 0;
         int len = 0;
         int instrStartIndex = 0;

         while(len < code.length) {
            int prefixes = 0;
            instrStartIndex = byteIndex;

            //check if there is any prefix
            prefixes = getPrefixes();
            int segmentOverride = 1;  //get segment override prefix

            if (code[byteIndex] == 0xc8)
               enter_instruction = 1;
            else
               enter_instruction = 0;

            //Read opcode
            int opcode = InstructionDecoder.readByte(code, byteIndex);
            byteIndex++;

            if (opcode == 0x0f) {
               opcode = InstructionDecoder.readByte(code, byteIndex);
               byteIndex++;

               //SSE: SSE instructions have reserved use of 0xF2, 0xF3, 0x66 prefixes
               if ((prefixes & PREFIX_REPNZ) != 0) {
                  instrDecoder = twoBytePrefixF2Table[opcode];
               } else if ((prefixes & PREFIX_REPZ) != 0) {
                  instrDecoder = twoBytePrefixF3Table[opcode];
               } else if ((prefixes & PREFIX_DATA) != 0) {
                  instrDecoder = twoBytePrefix66Table[opcode];
               } else {
                  instrDecoder = twoByteTable[opcode];
               }

            } else {
               instrDecoder = oneByteTable[opcode];
            }
            if (instrDecoder != null) {
               instr = instrDecoder.decode(code, byteIndex, instrStartIndex, segmentOverride, prefixes, factory);
               visitor.visit(startPc + len, instr);
               len = instrDecoder.getCurrentIndex();
            }
            else {
               len += 1;
            }
            byteIndex = len;
         }
      } catch (Exception exp) {
            visitor.epilogue();
      }
   }

   private int getPrefixes() {
      int prefixByte = 0;
      int prefixes = 0;
      boolean isPrefix = true;
      while (isPrefix) {
         prefixByte = InstructionDecoder.readByte(code, byteIndex);

         switch (prefixByte) {
            case 0xf3:
               prefixes |= PREFIX_REPZ;
               break;
            case 0xf2:
               prefixes |= PREFIX_REPNZ;
               break;
            case 0xf0:
               prefixes |= PREFIX_LOCK;
               break;
            case 0x2e:
               prefixes |= PREFIX_CS;
               break;
            case 0x36:
               prefixes |= PREFIX_SS;
               break;
            case 0x3e:
               prefixes |= PREFIX_DS;
               break;
            case 0x26:
               prefixes |= PREFIX_ES;
               break;
            case 0x64:
               prefixes |= PREFIX_FS;
               break;
            case 0x65:
               prefixes |= PREFIX_GS;
               break;
            case 0x66:
               prefixes |= PREFIX_DATA;
               break;
            case 0x67:
               prefixes |= PREFIX_ADR;
               break;
            case 0x9b:
               prefixes |= PREFIX_FWAIT;
               break;
            default:
               isPrefix = false;
               break;
         }
         if(isPrefix)
             byteIndex++;
      }
      return prefixes;
   }

}
