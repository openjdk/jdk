/*
* Copyright (c) 2021, 2026, Intel Corporation. All rights reserved.
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

#include "asm/assembler.hpp"
#include "asm/assembler.inline.hpp"
#include "utilities/globalDefinitions.hpp"
#include "runtime/globals.hpp"
#include "runtime/stubRoutines.hpp"
#include "macroAssembler_x86.hpp"
#include "stubGenerator_x86_64.hpp"

#define __ _masm->

ATTRIBUTE_ALIGNED(64) static const juint ADLER32_ASCALE_TABLE[] = {
    0x00000000UL, 0x00000001UL, 0x00000002UL, 0x00000003UL,
    0x00000004UL, 0x00000005UL, 0x00000006UL, 0x00000007UL,
    0x00000008UL, 0x00000009UL, 0x0000000AUL, 0x0000000BUL,
    0x0000000CUL, 0x0000000DUL, 0x0000000EUL, 0x0000000FUL
};

// Weight tables for the vpmaddubsw/vpmaddwd dot product that computes the
// weighted byte sum used to update b.

// ADLER32_DOT2V_TABLE covers bytes 0..31 with the weights 64..33,
ATTRIBUTE_ALIGNED(32) static const juint ADLER32_DOT2V_TABLE[] = {
    0x3D3E3F40UL, 0x393A3B3CUL, 0x35363738UL, 0x31323334UL,
    0x2D2E2F30UL, 0x292A2B2CUL, 0x25262728UL, 0x21222324UL
};

// ADLER32_DOT2V_0_TABLE covers bytes 32..63 with the weights 32..1.
ATTRIBUTE_ALIGNED(32) static const juint ADLER32_DOT2V_0_TABLE[] = {
    0x1D1E1F20UL, 0x191A1B1CUL, 0x15161718UL, 0x11121314UL,
    0x0D0E0F10UL, 0x090A0B0CUL, 0x05060708UL, 0x01020304UL
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
  StubId stub_id = StubId::stubgen_updateBytesAdler32_id;
  int entry_count = StubInfo::entry_count(stub_id);
  assert(entry_count == 1, "sanity check");
  address start = load_archive_data(stub_id);
  if (start != nullptr) {
    return start;
  }
  StubCodeMark mark(this, stub_id);
  start = __ pc();

  // Choose an appropriate LIMIT for inner loop based on the granularity
  // of intermediate results. For int, LIMIT of 5552 will ensure intermediate
  // results does not overflow Integer.MAX_VALUE before modulo operations.
  const int LIMIT = 5552;
  const int BASE = 65521;
  const int DIV_BASE_MAGIC_SHIFT = 47;
  const int DIV_BASE_MAGIC = 2147975281U; // gcc magic for unsigned divide by 65521
  const int CHUNKSIZE = 16;
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

  const XMMRegister ya = xmm0;
  const XMMRegister yb = xmm1;
  const XMMRegister yb_0 = xmm2;
  const XMMRegister ysa = xmm2;
  const XMMRegister yprev_a = xmm3;
  const XMMRegister ysum_a = xmm4;
  const XMMRegister yzero = xmm5;
  const XMMRegister yones = xmm6;
  const XMMRegister ytmp0 = xmm7;
  const XMMRegister ydot2v = xmm11;
  const XMMRegister ydot2v_0 = xmm12;
  const XMMRegister ydata0 = xmm13;
  const XMMRegister ydata1 = xmm14;
  const XMMRegister ytmp1 = xmm15;
  const XMMRegister xa = xmm0;
  const XMMRegister xb = xmm1;
  const XMMRegister xsa = xmm2;
  const XMMRegister xtmp0 = xmm13;
  const XMMRegister xtmp1 = xmm14;
  const XMMRegister xtmp2 = xmm15;
  const XMMRegister xtmp3 = xmm8;
  const XMMRegister xtmp4 = xmm9;
  const XMMRegister xtmp5 = xmm10;

  Label SLOOP1, SLOOP1A_AVX2, SLOOP1A_AVX2_SETUP, SLOOP1A_AVX2_TAIL, SLOOP1A_AVX3;
  Label AVX3_REDUCE, SKIP_LOOP_1A, SKIP_LOOP_1A_AVX3;
  Label DO_FINAL, FINISH, LT16, FINAL_LOOP, ZERO_SIZE, END, PROCESS_16_BLOCK;

  __ enter(); // required for proper stackwalking of RuntimeStub frame

  __ movq(xtmp3, r12);
  __ movq(xtmp4, r13);
  __ movq(xtmp5, r14);

  __ vmovdqu(ydot2v, ExternalAddress((address)ADLER32_DOT2V_TABLE), r14 /*rscratch*/);
  __ vmovdqu(ydot2v_0, ExternalAddress((address)ADLER32_DOT2V_0_TABLE), r14 /*rscratch*/);

  __ movptr(data, c_rarg1); //data
  __ movl(size, c_rarg2); //length

  __ movl(b_d, init_d); //adler
  __ shrl(b_d, 16);
  __ andl(init_d, 0xFFFF);
  __ movl(a_d, init_d);

  __ cmpl(size, 16);
  __ jcc(Assembler::below, LT16);

  __ vpxor(yzero, yzero, yzero, Assembler::AVX_256bit);
  __ vpcmpeqw(yones, yones, yones, Assembler::AVX_256bit);
  __ vpsrlw(yones, yones, 15, Assembler::AVX_256bit);

  __ cmpl(size, 32);
  __ jcc(Assembler::below, PROCESS_16_BLOCK);

  __ bind(SLOOP1);
  __ movl(s, LIMIT);
  __ cmpl(s, size);
  __ cmovl(Assembler::above, s, size); // s = min(size, LIMIT)

  __ align32();
  if (VM_Version::supports_avx512vl()) {
    // AVX2 performs better for smaller inputs because of leaner post loop reduction sequence..
    __ cmpl(s, MAX2(128, CopyAVX3Threshold));
    __ jcc(Assembler::belowEqual, SLOOP1A_AVX2_SETUP);

    __ movdl(ya, a_d);
    __ vpxor(yb, yb, yb, Assembler::AVX_512bit);
    __ lea(end, Address(s, data, Address::times_1, - (2*CHUNKSIZE - 1)));

    // Some notes on vectorized main loop algorithm.
    // Additions are performed in slices of 16 bytes in the main loop.
    // input size : 64 bytes (a0 - a63).
    // Iteration0 : ya =  [a0 - a15]
    //              yb =  [a0 - a15]
    // Iteration1 : ya =  [a0 - a15] + [a16 - a31]
    //              yb =  2 x [a0 - a15] + [a16 - a31]
    // Iteration2 : ya =  [a0 - a15] + [a16 - a31] + [a32 - a47]
    //              yb =  3 x [a0 - a15] + 2 x [a16 - a31] + [a32 - a47]
    // Iteration4 : ya =  [a0 - a15] + [a16 - a31] + [a32 - a47] + [a48 - a63]
    //              yb =  4 x [a0 - a15] + 3 x [a16 - a31] + 2 x [a32 - a47] + [a48 - a63]
    // Before performing reduction we must scale the intermediate result appropriately.
    // Since addition was performed in chunks of 16 bytes, thus to match the scalar implementation
    // Oth lane element must be repeatedly added 16 times, 1st element 15 times and so on so forth.
    // Thus we first multiply yb by 16 followed by subtracting appropriately scaled ya value.
    // yb = 16 x yb  - [a0 - a15] x ya
    //    = 64 x [a0 - a15] + 48 x [a16 - a31] + 32 x [a32 - a47] + 16 x [a48 - a63]  -  [a0 - a15] x ya
    //    = 64 x a0 + 63 x a1 + 62 x a2 ...... + a63
    __ bind(SLOOP1A_AVX3);
      __ evpmovzxbd(ydata0, Address(data, 0), Assembler::AVX_512bit);
      __ evpmovzxbd(ydata1, Address(data, CHUNKSIZE), Assembler::AVX_512bit);
      __ vpaddd(ya, ya, ydata0, Assembler::AVX_512bit);
      __ vpaddd(yb, yb, ya, Assembler::AVX_512bit);
      __ vpaddd(ya, ya, ydata1, Assembler::AVX_512bit);
      __ vpaddd(yb, yb, ya, Assembler::AVX_512bit);
      __ addptr(data, 2*CHUNKSIZE);
      __ cmpptr(data, end);
      __ jcc(Assembler::below, SLOOP1A_AVX3);

    __ addptr(end, CHUNKSIZE);
    __ cmpptr(data, end);
    __ jcc(Assembler::aboveEqual, AVX3_REDUCE);

    __ evpmovzxbd(ydata0, Address(data, 0), Assembler::AVX_512bit);
    __ vpaddd(ya, ya, ydata0, Assembler::AVX_512bit);
    __ vpaddd(yb, yb, ya, Assembler::AVX_512bit);
    __ addptr(data, CHUNKSIZE);

    __ bind(AVX3_REDUCE);
    __ vpslld(yb, yb, 4, Assembler::AVX_512bit); //b is scaled by 16(avx512))
    __ vpmulld(ysa, ya, ExternalAddress((address)ADLER32_ASCALE_TABLE), Assembler::AVX_512bit, r14 /*rscratch*/);

    // compute horizontal sums of ya, yb, ysa
    __ vextracti64x4(xtmp0, ya, 1);
    __ vextracti64x4(xtmp1, yb, 1);
    __ vextracti64x4(xtmp2, ysa, 1);
    __ vpaddd(xtmp0, xtmp0, ya, Assembler::AVX_256bit);
    __ vpaddd(xtmp1, xtmp1, yb, Assembler::AVX_256bit);
    __ vpaddd(xtmp2, xtmp2, ysa, Assembler::AVX_256bit);
    __ vextracti128(xa, xtmp0, 1);
    __ vextracti128(xb, xtmp1, 1);
    __ vextracti128(xsa, xtmp2, 1);
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

    __ addptr(end, CHUNKSIZE_M1);
    __ testl(s, CHUNKSIZE_M1);
    __ jcc(Assembler::notEqual, DO_FINAL);

    __ bind(SKIP_LOOP_1A_AVX3);
    __ subl(size, s);

    __ movdl(rax, xa);
    __ xorl(rdx, rdx);
    __ movl(rcx, BASE);
    __ divl(rcx); // divide edx:eax by ecx, quot->eax, rem->edx
    __ movl(a_d, rdx);

    __ movdl(rax, xb);
    __ addl(rax, b_d);
    __ xorl(rdx, rdx);
    __ movl(rcx, BASE);
    __ divl(rcx); // divide edx:eax by ecx, quot->eax, rem->edx
    __ movl(b_d, rdx);

    __ testl(size, size);
    __ jcc(Assembler::zero, FINISH);
    __ cmpl(size, 16);
    __ jcc(Assembler::below, LT16);
    __ cmpl(size, 32);
    __ jcc(Assembler::below, PROCESS_16_BLOCK);
    __ jmp(SLOOP1);

    __ bind(DO_FINAL);
    __ movdl(a_d, xa);
    __ movdl(rax, xb);
    __ addl(b_d, rax);
    __ jmp(FINAL_LOOP);
  }

  __ bind(SLOOP1A_AVX2_SETUP);
  __ movl(rcx, DIV_BASE_MAGIC);
  __ andl(s, -32);                     // process 32 bytes at a time
  __ subl(size, s);

  __ movdl(ya, a_d);
  __ movdl(yb, b_d);
  __ vmovdqu(yprev_a, ya);
  __ vpxor(yb_0, yb_0, yb_0, Assembler::AVX_256bit);
  __ vpxor(ysum_a, ysum_a, ysum_a, Assembler::AVX_256bit);

  __ align32();
  __ bind(SLOOP1A_AVX2);
  __ cmpl(s, 64);
  __ jcc(Assembler::below, SLOOP1A_AVX2_TAIL);

  __ vmovdqu(ydata0, Address(data, 0));
  __ vmovdqu(ydata1, Address(data, 32));
  __ addptr(data, 64);
  __ subl(s, 64);

  __ vpsadbw(ytmp0, ydata0, yzero, Assembler::AVX_256bit);
  __ vpsadbw(ytmp1, ydata1, yzero, Assembler::AVX_256bit);
  __ vpaddd(ya, ya, ytmp0, Assembler::AVX_256bit);
  __ vpaddd(ysum_a, ysum_a, yprev_a, Assembler::AVX_256bit);
  __ vpmaddubsw(ydata0, ydata0, ydot2v, Assembler::AVX_256bit);
  __ vpmaddubsw(ydata1, ydata1, ydot2v_0, Assembler::AVX_256bit);
  __ vpmaddwd(ydata0, ydata0, yones, Assembler::AVX_256bit);
  __ vpmaddwd(ydata1, ydata1, yones, Assembler::AVX_256bit);
  __ vpaddd(ya, ya, ytmp1, Assembler::AVX_256bit);
  __ vpaddd(yb, yb, ydata0, Assembler::AVX_256bit);
  __ vpaddd(yb_0, yb_0, ydata1, Assembler::AVX_256bit);
  __ vmovdqu(yprev_a, ya);
  __ jmp(SLOOP1A_AVX2);

  __ bind(SLOOP1A_AVX2_TAIL);
  __ vpaddd(yb, yb, yb_0, Assembler::AVX_256bit);
  __ vpslld(ysum_a, ysum_a, 6, Assembler::AVX_256bit);
  __ vpaddd(yb, yb, ysum_a, Assembler::AVX_256bit);
  __ vpxor(ysum_a, ysum_a, ysum_a, Assembler::AVX_256bit);

  __ cmpl(s, 32);
  __ jcc(Assembler::below, SKIP_LOOP_1A);

  __ vmovdqu(ydata0, Address(data, 0));
  __ addptr(data, 32);
  __ subl(s, 32);

  __ vpsadbw(ytmp0, ydata0, yzero, Assembler::AVX_256bit);
  __ vpaddd(ya, ya, ytmp0, Assembler::AVX_256bit);
  __ vpaddd(ysum_a, ysum_a, yprev_a, Assembler::AVX_256bit);
  __ vpmaddubsw(ydata0, ydata0, ydot2v_0, Assembler::AVX_256bit);
  __ vpmaddwd(ydata0, ydata0, yones, Assembler::AVX_256bit);
  __ vpaddd(yb, yb, ydata0, Assembler::AVX_256bit);
  __ vmovdqu(yprev_a, ya);

  __ bind(SKIP_LOOP_1A);
  __ vpslld(ysum_a, ysum_a, 5, Assembler::AVX_256bit);
  __ vpaddd(yb, yb, ysum_a, Assembler::AVX_256bit);

  // Reduce ya: the partial byte sums are in dwords 0, 2, 4 and 6.
  __ vextracti128(ytmp0, ya, 1);
  __ vpaddd(xa, xa, ytmp0, Assembler::AVX_128bit);
  __ vpunpckhqdq(ytmp0, xa, xa, Assembler::AVX_128bit);
  __ vpaddd(xa, xa, ytmp0, Assembler::AVX_128bit);

  // Reduce yb: the weighted sums are in all eight dwords.
  __ vextracti128(ytmp1, yb, 1);
  __ vpaddd(xb, xb, ytmp1, Assembler::AVX_128bit);
  __ vpunpckhqdq(ytmp1, xb, xb, Assembler::AVX_128bit);
  __ vpaddd(xb, xb, ytmp1, Assembler::AVX_128bit);
  __ vpshufd(ytmp1, xb, 1, Assembler::AVX_128bit);
  __ vpaddd(xb, xb, ytmp1, Assembler::AVX_128bit);

  __ movdl(a_d, xa);
  __ movdl(b_d, xb);

  __ movl(rax, a_d);
  __ imulq(rax, rcx);
  __ shrq(rax, DIV_BASE_MAGIC_SHIFT); // q = x / BASE
  __ imull(rdx, rax, BASE);
  __ subl(a_d, rdx); // r = x - q * BASE

  __ movl(rax, b_d);
  __ imulq(rax, rcx);
  __ shrq(rax, DIV_BASE_MAGIC_SHIFT); // q = x / BASE
  __ imull(rdx, rax, BASE);
  __ subl(b_d, rdx); // r = x - q * BASE

  __ cmpl(size, 32);
  __ jcc(Assembler::aboveEqual, SLOOP1);
  __ cmpl(size, 16);
  __ jcc(Assembler::below, LT16);

  __ bind(PROCESS_16_BLOCK);
  __ movl(s, a_d);
  __ vmovdqu(ydata0, Address(data, 0), Assembler::AVX_128bit);
  __ addptr(data, 16);
  __ subl(size, 16);

  __ vpsadbw(ytmp0, ydata0, yzero, Assembler::AVX_128bit);
  __ movdl(rax, ytmp0);
  __ vpsrldq(ytmp0, ytmp0, 8, Assembler::AVX_128bit);
  __ movdl(end, ytmp0);
  __ addl(rax, end);
  __ addl(a_d, rax);

  __ vextracti128(ytmp0, ydot2v_0, 1);
  __ vpmaddubsw(ytmp1, ydata0, ytmp0, Assembler::AVX_128bit);
  __ vpmaddwd(ytmp1, ytmp1, yones, Assembler::AVX_128bit);
  __ vpunpckhqdq(ytmp0, ytmp1, ytmp1, Assembler::AVX_128bit);
  __ vpaddd(ytmp1, ytmp1, ytmp0, Assembler::AVX_128bit);
  __ vpshufd(ytmp0, ytmp1, 1, Assembler::AVX_128bit);
  __ vpaddd(ytmp1, ytmp1, ytmp0, Assembler::AVX_128bit);
  __ movdl(rax, ytmp1);
  __ addl(b_d, rax);
  __ movl(rax, s);
  __ shll(rax, 4); // Add 16 * a_before_block.
  __ addl(b_d, rax);

  __ bind(LT16);
  __ testl(size, size);
  __ jcc(Assembler::zero, ZERO_SIZE);
  __ lea(end, Address(data, size, Address::times_1));

  __ align32();
  __ bind(FINAL_LOOP);
  __ movzbl(rax, Address(data, 0)); //movzx   eax, byte[data]
  __ addl(a_d, rax);
  __ addptr(data, 1);
  __ addl(b_d, a_d);
  __ cmpptr(data, end);
  __ jcc(Assembler::below, FINAL_LOOP);

  __ bind(ZERO_SIZE);
  __ movl(rcx, DIV_BASE_MAGIC);
  __ movl(rax, a_d);
  __ imulq(rax, rcx);
  __ shrq(rax, DIV_BASE_MAGIC_SHIFT); // q = x / BASE
  __ imull(rdx, rax, BASE);
  __ subl(a_d, rdx); // r = x - q * BASE

  __ movl(rax, b_d);
  __ imulq(rax, rcx);
  __ shrq(rax, DIV_BASE_MAGIC_SHIFT); // q = x / BASE
  __ imull(rdx, rax, BASE);
  __ subl(b_d, rdx); // r = x - q * BASE

  __ bind(FINISH);
  __ movl(rax, b_d);
  __ shll(rax, 16);
  __ orl(rax, a_d);

  __ bind(END);
  __ movq(r14, xtmp5);
  __ movq(r13, xtmp4);
  __ movq(r12, xtmp3);

  __ vzeroupper();
  __ leave();
  __ ret(0);

  // record the stub entry and end
  store_archive_data(stub_id, start, __ pc());
  return start;
}

#undef __

#if INCLUDE_CDS
void StubGenerator::init_AOTAddressTable_adler(GrowableArray<address>& external_addresses) {
#define ADD(addr) external_addresses.append((address)(addr))
  ADD(ADLER32_ASCALE_TABLE);
  ADD(ADLER32_DOT2V_TABLE);
  ADD(ADLER32_DOT2V_0_TABLE);
#undef ADD
}
#endif // INCLUDE_CDS
