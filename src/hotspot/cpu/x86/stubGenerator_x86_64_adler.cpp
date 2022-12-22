/*
* Copyright (c) 2021, Intel Corporation. All rights reserved.
*
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

#include "precompiled.hpp"
#include "asm/assembler.hpp"
#include "asm/assembler.inline.hpp"
#include "runtime/globals.hpp"
#include "runtime/stubRoutines.hpp"
#include "macroAssembler_x86.hpp"
#include "stubGenerator_x86_64.hpp"

#define __ _masm->

ATTRIBUTE_ALIGNED(32) juint ADLER32_ASCALE_TABLE[] = {
    0x00000000UL, 0x00000001UL, 0x00000002UL, 0x00000003UL,
    0x00000004UL, 0x00000005UL, 0x00000006UL, 0x00000007UL
};

ATTRIBUTE_ALIGNED(32) juint ADLER32_SHUF0_TABLE[] = {
    0xFFFFFF00UL, 0xFFFFFF01UL, 0xFFFFFF02UL, 0xFFFFFF03UL,
    0xFFFFFF04UL, 0xFFFFFF05UL, 0xFFFFFF06UL, 0xFFFFFF07UL
};

ATTRIBUTE_ALIGNED(32) juint ADLER32_SHUF1_TABLE[] = {
    0xFFFFFF08UL, 0xFFFFFF09, 0xFFFFFF0AUL, 0xFFFFFF0BUL,
    0xFFFFFF0CUL, 0xFFFFFF0D, 0xFFFFFF0EUL, 0xFFFFFF0FUL
};


/***
 *  Arguments:
 *
 *  Inputs:
 *   c_rarg0   - int   adler
 *   c_rarg1   - byte* buff
 *   c_rarg2   - int   len
 *
 * Output:
 *   rax   - int adler result
 */
address StubGenerator::generate_updateBytesAdler32() {
  assert(UseAdler32Intrinsics, "");

  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", "updateBytesAdler32");
  address start = __ pc();

  const int LIMIT = 5552;
  const int BASE = 65521;
  const int CHUNKSIZE =  16;
  const int CHUNKSIZE_M1 = CHUNKSIZE - 1;


  const Register init_d = c_rarg0;
  const Register data = r9;
  const Register size = r10;
  const Register s = r11;
  const Register a_d = r12; //r12d
  const Register b_d = r8; //r8d
  const Register end = r13;

  assert_different_registers(c_rarg0, c_rarg1, c_rarg2, data, size);
  assert_different_registers(init_d, data, size, s, a_d, b_d, end, rax);

  const XMMRegister yshuf0 = xmm6;
  const XMMRegister yshuf1 = xmm7;
  const XMMRegister ya = xmm0;
  const XMMRegister yb = xmm1;
  const XMMRegister ydata0 = xmm2;
  const XMMRegister ydata1 = xmm3;
  const XMMRegister ysa = xmm4;
  const XMMRegister ydata = ysa;
  const XMMRegister ytmp0 = ydata0;
  const XMMRegister ytmp1 = ydata1;
  const XMMRegister ytmp2 = xmm5;
  const XMMRegister xa = xmm0;
  const XMMRegister xb = xmm1;
  const XMMRegister xtmp0 = xmm2;
  const XMMRegister xtmp1 = xmm3;
  const XMMRegister xsa = xmm4;
  const XMMRegister xtmp2 = xmm5;

  Label SLOOP1, SLOOP1A, SKIP_LOOP_1A, FINISH, LT64, DO_FINAL, FINAL_LOOP, ZERO_SIZE, END;

  __ enter(); // required for proper stackwalking of RuntimeStub frame

  __ push(r12);
  __ push(r13);
  __ push(r14);

  __ vmovdqu(yshuf0, ExternalAddress((address)ADLER32_SHUF0_TABLE), r14 /*rscratch*/);
  __ vmovdqu(yshuf1, ExternalAddress((address)ADLER32_SHUF1_TABLE), r14 /*rscratch*/);
  __ movptr(data, c_rarg1); //data
  __ movl(size, c_rarg2); //length

  __ movl(b_d, init_d); //adler
  __ shrl(b_d, 16);
  __ andl(init_d, 0xFFFF);
  __ cmpl(size, 32);
  __ jcc(Assembler::below, LT64);
  __ movdl(xa, init_d); //vmovd - 32bit
  __ vpxor(yb, yb, yb, Assembler::AVX_256bit);

  __ bind(SLOOP1);
  __ movl(s, LIMIT);
  __ cmpl(s, size);
  __ cmovl(Assembler::above, s, size); // s = min(size, LIMIT)
  __ lea(end, Address(s, data, Address::times_1, -CHUNKSIZE_M1));
  __ cmpptr(data, end);
  __ jcc(Assembler::aboveEqual, SKIP_LOOP_1A);

  __ align32();
  __ bind(SLOOP1A);
  __ vbroadcastf128(ydata, Address(data, 0), Assembler::AVX_256bit);
  __ addptr(data, CHUNKSIZE);
  __ vpshufb(ydata0, ydata, yshuf0, Assembler::AVX_256bit);
  __ vpaddd(ya, ya, ydata0, Assembler::AVX_256bit);
  __ vpaddd(yb, yb, ya, Assembler::AVX_256bit);
  __ vpshufb(ydata1, ydata, yshuf1, Assembler::AVX_256bit);
  __ vpaddd(ya, ya, ydata1, Assembler::AVX_256bit);
  __ vpaddd(yb, yb, ya, Assembler::AVX_256bit);
  __ cmpptr(data, end);
  __ jcc(Assembler::below, SLOOP1A);

  __ bind(SKIP_LOOP_1A);
  __ addptr(end, CHUNKSIZE_M1);
  __ testl(s, CHUNKSIZE_M1);
  __ jcc(Assembler::notEqual, DO_FINAL);

  // either we're done, or we just did LIMIT
  __ subl(size, s);

  // reduce
  __ vpslld(yb, yb, 3, Assembler::AVX_256bit); //b is scaled by 8
  __ vpmulld(ysa, ya, ExternalAddress((address)ADLER32_ASCALE_TABLE), Assembler::AVX_256bit, r14 /*rscratch*/);

  // compute horizontal sums of ya, yb, ysa
  __ vextracti128(xtmp0, ya, 1);
  __ vextracti128(xtmp1, yb, 1);
  __ vextracti128(xtmp2, ysa, 1);
  __ vpaddd(xa, xa, xtmp0, Assembler::AVX_128bit);
  __ vpaddd(xb, xb, xtmp1, Assembler::AVX_128bit);
  __ vpaddd(xsa, xsa, xtmp2, Assembler::AVX_128bit);
  __ vphaddd(xa, xa, xa, Assembler::AVX_128bit);
  __ vphaddd(xb, xb, xb, Assembler::AVX_128bit);
  __ vphaddd(xsa, xsa, xsa, Assembler::AVX_128bit);
  __ vphaddd(xa, xa, xa, Assembler::AVX_128bit);
  __ vphaddd(xb, xb, xb, Assembler::AVX_128bit);
  __ vphaddd(xsa, xsa, xsa, Assembler::AVX_128bit);

  __ movdl(rax, xa);
  __ xorl(rdx, rdx);
  __ movl(rcx, BASE);
  __ divl(rcx); // divide edx:eax by ecx, quot->eax, rem->edx
  __ movl(a_d, rdx);

  __ vpsubd(xb, xb, xsa, Assembler::AVX_128bit);
  __ movdl(rax, xb);
  __ addl(rax, b_d);
  __ xorl(rdx, rdx);
  __ movl(rcx, BASE);
  __ divl(rcx); // divide edx:eax by ecx, quot->eax, rem->edx
  __ movl(b_d, rdx);

  __ testl(size, size);
  __ jcc(Assembler::zero, FINISH);

  // continue loop
  __ movdl(xa, a_d);
  __ vpxor(yb, yb, yb, Assembler::AVX_256bit);
  __ jmp(SLOOP1);

  __ bind(FINISH);
  __ movl(rax, b_d);
  __ shll(rax, 16);
  __ orl(rax, a_d);
  __ jmp(END);

  __ bind(LT64);
  __ movl(a_d, init_d);
  __ lea(end, Address(data, size, Address::times_1));
  __ testl(size, size);
  __ jcc(Assembler::notZero, FINAL_LOOP);
  __ jmp(ZERO_SIZE);

  // handle remaining 1...15 bytes
  __ bind(DO_FINAL);
  // reduce
  __ vpslld(yb, yb, 3, Assembler::AVX_256bit); //b is scaled by 8
  __ vpmulld(ysa, ya, ExternalAddress((address)ADLER32_ASCALE_TABLE), Assembler::AVX_256bit, r14 /*rscratch*/); //scaled a

  __ vextracti128(xtmp0, ya, 1);
  __ vextracti128(xtmp1, yb, 1);
  __ vextracti128(xtmp2, ysa, 1);
  __ vpaddd(xa, xa, xtmp0, Assembler::AVX_128bit);
  __ vpaddd(xb, xb, xtmp1, Assembler::AVX_128bit);
  __ vpaddd(xsa, xsa, xtmp2, Assembler::AVX_128bit);
  __ vphaddd(xa, xa, xa, Assembler::AVX_128bit);
  __ vphaddd(xb, xb, xb, Assembler::AVX_128bit);
  __ vphaddd(xsa, xsa, xsa, Assembler::AVX_128bit);
  __ vphaddd(xa, xa, xa, Assembler::AVX_128bit);
  __ vphaddd(xb, xb, xb, Assembler::AVX_128bit);
  __ vphaddd(xsa, xsa, xsa, Assembler::AVX_128bit);
  __ vpsubd(xb, xb, xsa, Assembler::AVX_128bit);

  __ movdl(a_d, xa);
  __ movdl(rax, xb);
  __ addl(b_d, rax);

  __ align32();
  __ bind(FINAL_LOOP);
  __ movzbl(rax, Address(data, 0)); //movzx   eax, byte[data]
  __ addl(a_d, rax);
  __ addptr(data, 1);
  __ addl(b_d, a_d);
  __ cmpptr(data, end);
  __ jcc(Assembler::below, FINAL_LOOP);

  __ bind(ZERO_SIZE);

  __ movl(rax, a_d);
  __ xorl(rdx, rdx);
  __ movl(rcx, BASE);
  __ divl(rcx); // div ecx -- divide edx:eax by ecx, quot->eax, rem->edx
  __ movl(a_d, rdx);

  __ movl(rax, b_d);
  __ xorl(rdx, rdx);
  __ movl(rcx, BASE);
  __ divl(rcx); // divide edx:eax by ecx, quot->eax, rem->edx
  __ shll(rdx, 16);
  __ orl(rdx, a_d);
  __ movl(rax, rdx);

  __ bind(END);
  __ pop(r14);
  __ pop(r13);
  __ pop(r12);

  __ leave();
  __ ret(0);

  return start;
}

#undef __
