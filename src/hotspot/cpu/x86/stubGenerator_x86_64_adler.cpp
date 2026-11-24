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

// AVX2 weight tables for the vpmaddubsw/vpmaddwd dot product that computes
// the weighted byte sum used to update b.

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

  // Choose an appropriate LIMIT for the inner loop based on the granularity
  // of intermediate results. For unsigned 32-bit values, LIMIT of 5552
  // ensures intermediate results do not overflow 2^32 before modulo operations.
  const int LIMIT = 5552;
  const int BASE = 65521;
  const int DIV_BASE_MULTIPLIER_SHIFT = 47;
  const uint32_t DIV_BASE_MULTIPLIER = 2147975281U; // ceil(2^47 / 65521)
  const int CHUNKSIZE = 16;
  const int CHUNKSIZE_M1 = CHUNKSIZE - 1;

  // Reciprocal modulo reduction used by the AVX2 path and final scalar tail.
  // The caller passes DIV_BASE_MULTIPLIER in multiplier.
  // For the bounded unsigned 32-bit input x, this is equivalent to:
  //   q = x / BASE
  //   reg = x % BASE
  auto reduce_mod_base = [&](const Register reg, const Register multiplier) {
    assert_different_registers(reg, rax, rdx, multiplier);
    __ movl(rax, reg);                              // x = reg
    __ imulq(rax, multiplier);                      // x * multiplier
    __ shrq(rax, DIV_BASE_MULTIPLIER_SHIFT);        // q = x * multiplier >> DIV_BASE_MULTIPLIER_SHIFT
    __ imull(rdx, rax, BASE);                       // q * BASE
    __ subl(reg, rdx);                              // reg = x - q * BASE = x % BASE
  };

  // The _d suffix denotes a 32-bit (doubleword) value in the generated code;
  const Register init_d = c_rarg0;                  // packed Adler value: B in high 16, A in low 16
  const Register a_d = r12;                         // Adler A; canonical between LIMIT blocks
  const Register b_d = r8;                          // Adler B; canonical between LIMIT blocks
  const Register data = r9;                         // current input pointer
  const Register size = r10;                        // bytes not yet processed
  const Register chunk_size = r11;                  // current outer-block size
  const Register end = r13;                         // AVX-512 loop end, then scalar-tail end

  assert_different_registers(c_rarg0, c_rarg1, c_rarg2, data, size);
  assert_different_registers(init_d, data, size, chunk_size, a_d, b_d, end, rax);

  // Vector-register aliases: 0/1 means first/second member of a pair, not an
  // initial value or iteration number. AVX2 pairs are 32-byte halves; AVX-512
  // ydata pairs are consecutive 16-byte slices. A sole accumulator, such as
  // yb0 in AVX-512, keeps index 0.
  //
  // Shared by both paths:
  //   ya: byte sums (4 qword lanes in AVX2, 16 dword lanes in AVX-512)
  //   yb0: weighted B sums
  //   ydata0/ydata1: input or weighted-data vectors
  const XMMRegister ya = xmm0;
  const XMMRegister yb0 = xmm1;
  const XMMRegister ydata0 = xmm13;   // AVX2: bytes 0..31; AVX-512: 16-byte slice
  const XMMRegister ydata1 = xmm14;   // AVX2: bytes 32..63; AVX-512: 16-byte slice

  // AVX2-only state:
  //   yb1: second weighted-B chain for bytes 32..63 (yb0 handles bytes 0..31)
  //   ysum_a: running A before each full 64-byte block or final 32-byte block
  //   yzero: zero operand for vpsadbw
  //   yones: all-one words for pairwise widening by vpmaddwd
  //   ytmp0/ytmp1: byte-sum and horizontal-reduction temporaries
  //   yweight0/yweight1: weight vectors for the two 32-byte halves
  const XMMRegister yb1 = xmm2;
  const XMMRegister ysum_a = xmm4;
  const XMMRegister yzero = xmm5;
  const XMMRegister yones = xmm6;
  const XMMRegister ytmp0 = xmm7;
  const XMMRegister ytmp1 = xmm15;
  const XMMRegister yweight0 = xmm11;
  const XMMRegister yweight1 = xmm12;

  // 128-bit views shared by the two horizontal reductions.
  const XMMRegister xa = ya;
  const XMMRegister xb = yb0;

  // AVX-512-only aliases. They reuse AVX2 registers after those values have
  // been consumed by the selected path.
  //   ya_scaled: per-lane A * [0..15]
  //   xa_scaled: horizontal reduction of ya_scaled, subtracted from B
  //   xtmp_*: 512-to-256-bit reduction temporaries
  const XMMRegister ya_scaled = yb1;
  const XMMRegister xa_scaled = ya_scaled;
  const XMMRegister xtmp_a = ydata0;
  const XMMRegister xtmp_b = ydata1;
  const XMMRegister xtmp_scaled = ytmp1;

  Label LIMIT_CHUNK_LOOP;
  Label AVX2_SETUP, AVX2_LOOP_64, AVX2_MERGE_FULL_BLOCKS, AVX2_REDUCE;
  Label AVX512_LOOP_32, AVX512_REDUCE, AVX512_SCALAR_TAIL;
  Label LT16, PROCESS_16_BLOCK, SCALAR_LOOP, FINAL_REDUCE, PACK_RESULT;

  __ enter(); // required for proper stackwalking of RuntimeStub frame

  __ push_ppx(r12);
  __ push_ppx(r13);
  __ push_ppx(r14);

  __ vmovdqu(yweight0, ExternalAddress((address)ADLER32_DOT2V_TABLE), r14 /*rscratch*/);
  __ vmovdqu(yweight1, ExternalAddress((address)ADLER32_DOT2V_0_TABLE), r14 /*rscratch*/);

  __ movptr(data, c_rarg1);   // data
  __ movl(size, c_rarg2);     // length

  __ movl(b_d, init_d);       // b_d = init_d (packed B:A)
  __ shrl(b_d, 16);           // b_d = B
  __ andl(init_d, 0xFFFF);    // init_d = A
  __ movl(a_d, init_d);       // a_d = A

  __ cmpl(size, 16);
  __ jcc(Assembler::below, LT16);

  __ vpxor(yzero, yzero, yzero, Assembler::AVX_256bit);
  __ vpcmpeqw(yones, yones, yones, Assembler::AVX_256bit);
  __ vpsrlw(yones, yones, 15, Assembler::AVX_256bit);

  __ cmpl(size, 32);
  __ jcc(Assembler::below, PROCESS_16_BLOCK);

  __ bind(LIMIT_CHUNK_LOOP);
  __ movl(chunk_size, LIMIT);
  __ cmpl(chunk_size, size);
  __ cmovl(Assembler::above, chunk_size, size); // chunk_size = min(size, LIMIT)

  if (VM_Version::supports_avx512vl()) {
    // Use AVX2 up to the AVX-512 dispatch threshold.
    __ cmpl(chunk_size, MAX2(128, CopyAVX3Threshold));
    __ jcc(Assembler::belowEqual, AVX2_SETUP);

    __ movdl(ya, a_d);
    __ vpxor(yb0, yb0, yb0, Assembler::AVX_512bit);
    __ lea(end, Address(chunk_size, data, Address::times_1, - (2*CHUNKSIZE - 1)));

    // Some notes on vectorized main loop algorithm.
    // Additions are performed in slices of 16 bytes in the main loop.
    // input size : 64 bytes (a0 - a63).
    // Iteration0 : ya =  [a0 - a15]
    //              yb0 =  [a0 - a15]
    // Iteration1 : ya =  [a0 - a15] + [a16 - a31]
    //              yb0 =  2 x [a0 - a15] + [a16 - a31]
    // Iteration2 : ya =  [a0 - a15] + [a16 - a31] + [a32 - a47]
    //              yb0 =  3 x [a0 - a15] + 2 x [a16 - a31] + [a32 - a47]
    // Iteration4 : ya =  [a0 - a15] + [a16 - a31] + [a32 - a47] + [a48 - a63]
    //              yb0 =  4 x [a0 - a15] + 3 x [a16 - a31] + 2 x [a32 - a47] + [a48 - a63]
    // Before performing reduction we must scale the intermediate result appropriately.
    // Since addition was performed in chunks of 16 bytes, thus to match the scalar implementation
    // Oth lane element must be repeatedly added 16 times, 1st element 15 times and so on so forth.
    // Thus we first multiply yb0 by 16 followed by subtracting appropriately scaled ya value.
    // yb0 = 16 x yb0  - [a0 - a15] x ya
    //     = 64 x [a0 - a15] + 48 x [a16 - a31] + 32 x [a32 - a47] + 16 x [a48 - a63]  -  [a0 - a15] x ya
    //     = 64 x a0 + 63 x a1 + 62 x a2 ...... + a63
    __ align32();
    __ bind(AVX512_LOOP_32);
      __ evpmovzxbd(ydata0, Address(data, 0), Assembler::AVX_512bit);
      __ evpmovzxbd(ydata1, Address(data, CHUNKSIZE), Assembler::AVX_512bit);
      __ vpaddd(ya, ya, ydata0, Assembler::AVX_512bit);
      __ vpaddd(yb0, yb0, ya, Assembler::AVX_512bit);
      __ vpaddd(ya, ya, ydata1, Assembler::AVX_512bit);
      __ vpaddd(yb0, yb0, ya, Assembler::AVX_512bit);
      __ addptr(data, 2*CHUNKSIZE);
      __ cmpptr(data, end);
      __ jcc(Assembler::below, AVX512_LOOP_32);

    __ addptr(end, CHUNKSIZE);
    __ cmpptr(data, end);
    __ jcc(Assembler::aboveEqual, AVX512_REDUCE);

    __ evpmovzxbd(ydata0, Address(data, 0), Assembler::AVX_512bit);
    __ vpaddd(ya, ya, ydata0, Assembler::AVX_512bit);
    __ vpaddd(yb0, yb0, ya, Assembler::AVX_512bit);
    __ addptr(data, CHUNKSIZE);

    __ bind(AVX512_REDUCE);
    __ vpslld(yb0, yb0, 4, Assembler::AVX_512bit); // b is scaled by 16 (AVX-512)
    __ vpmulld(ya_scaled, ya, ExternalAddress((address)ADLER32_ASCALE_TABLE), Assembler::AVX_512bit, r14 /*rscratch*/);

    // compute horizontal sums of ya, yb0, ya_scaled
    __ vextracti64x4(xtmp_a, ya, 1);
    __ vextracti64x4(xtmp_b, yb0, 1);
    __ vextracti64x4(xtmp_scaled, ya_scaled, 1);
    __ vpaddd(xtmp_a, xtmp_a, ya, Assembler::AVX_256bit);
    __ vpaddd(xtmp_b, xtmp_b, yb0, Assembler::AVX_256bit);
    __ vpaddd(xtmp_scaled, xtmp_scaled, ya_scaled, Assembler::AVX_256bit);
    __ vextracti128(xa, xtmp_a, 1);
    __ vextracti128(xb, xtmp_b, 1);
    __ vextracti128(xa_scaled, xtmp_scaled, 1);
    __ vpaddd(xa, xa, xtmp_a, Assembler::AVX_128bit);
    __ vpaddd(xb, xb, xtmp_b, Assembler::AVX_128bit);
    __ vpaddd(xa_scaled, xa_scaled, xtmp_scaled, Assembler::AVX_128bit);
    __ vphaddd(xa, xa, xa, Assembler::AVX_128bit);
    __ vphaddd(xb, xb, xb, Assembler::AVX_128bit);
    __ vphaddd(xa_scaled, xa_scaled, xa_scaled, Assembler::AVX_128bit);
    __ vphaddd(xa, xa, xa, Assembler::AVX_128bit);
    __ vphaddd(xb, xb, xb, Assembler::AVX_128bit);
    __ vphaddd(xa_scaled, xa_scaled, xa_scaled, Assembler::AVX_128bit);

    __ vpsubd(xb, xb, xa_scaled, Assembler::AVX_128bit);

    __ addptr(end, CHUNKSIZE_M1);
    __ testl(chunk_size, CHUNKSIZE_M1);
    __ jcc(Assembler::notEqual, AVX512_SCALAR_TAIL);

    __ subl(size, chunk_size);

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
    __ jcc(Assembler::zero, PACK_RESULT);
    __ cmpl(size, 16);
    __ jcc(Assembler::below, LT16);
    __ cmpl(size, 32);
    __ jcc(Assembler::below, PROCESS_16_BLOCK);
    __ jmp(LIMIT_CHUNK_LOOP);

    __ bind(AVX512_SCALAR_TAIL);
    __ movdl(a_d, xa);
    __ movdl(rax, xb);
    __ addl(b_d, rax);
    __ jmp(SCALAR_LOOP);
  }

  // AVX2 64-byte main loop.
  //
  // ydata0 and ydata1 initially contain bytes 0..31 and 32..63. vpsadbw
  // produces their byte sums for ya. vpmaddubsw/vpmaddwd replace ydata0 and
  // ydata1 with the corresponding weighted byte sums.
  //
  // Starting with ya=A, yb0=B, yb1=0 and ysum_a=0:
  // Iteration0:
  //   ysum_a = A
  //   ya     = A + sum(bytes 0..63)
  //   yb0    = B + weighted sum(bytes 0..31)
  //   yb1    = weighted sum(bytes 32..63)
  // Iteration1:
  //   ysum_a = A + (A + sum(bytes 0..63))
  //   ya     = A + sum(bytes 0..63) + sum(bytes 64..127)
  //   yb0    = B + weighted sum(bytes 0..31) + weighted sum(bytes 64..95)
  //   yb1    = weighted sum(bytes 32..63) + weighted sum(bytes 96..127)
  //
  // The equations above are lane-wise; after horizontal reduction, m full
  // blocks produce:
  //   sum(ya) = A + sum(all input bytes)
  //   sum(yb0) + sum(yb1) + 64*sum(ysum_a)
  //           = B + 64*m*A + sum((64*m-i)*a[i])
  __ bind(AVX2_SETUP);
  __ movl(rcx, DIV_BASE_MULTIPLIER);
  __ andl(chunk_size, -32);            // process 32 bytes at a time
  __ subl(size, chunk_size);

  __ movdl(ya, a_d);                                       // ya = A
  __ movdl(yb0, b_d);                                      // yb0 = B
  __ vpxor(yb1, yb1, yb1, Assembler::AVX_256bit);          // yb1 = 0
  __ vpxor(ysum_a, ysum_a, ysum_a, Assembler::AVX_256bit); // ysum_a = 0

  __ cmpl(chunk_size, 64);
  __ jcc(Assembler::below, AVX2_MERGE_FULL_BLOCKS);
  __ align32();
  __ bind(AVX2_LOOP_64);

  __ vmovdqu(ydata0, Address(data, 0));
  __ vmovdqu(ydata1, Address(data, 32));
  __ addptr(data, 64);
  __ subl(chunk_size, 64);

  // Process the current 64-byte block. ydata0 and ydata1 contain its two
  // 32-byte input halves. Preserve ya before updating A, then use vpsadbw to
  // add both halves' byte sums to ya. The following vpmaddubsw/vpmaddwd
  // instructions replace ydata0/ydata1 with their weighted sums for B; these
  // are accumulated independently into yb0/yb1 and merged after the loop.
  //
  // Per iteration:
  //   ysum_a += ya
  //   ya     += byte sums of ydata0 and ydata1
  //   yb0    += weighted sum of ydata0
  //   yb1    += weighted sum of ydata1
  __ vpsadbw(ytmp0, ydata0, yzero, Assembler::AVX_256bit);
  __ vpsadbw(ytmp1, ydata1, yzero, Assembler::AVX_256bit);
  __ vpaddd(ysum_a, ysum_a, ya, Assembler::AVX_256bit);
  __ vpaddd(ya, ya, ytmp0, Assembler::AVX_256bit);
  __ vpmaddubsw(ydata0, ydata0, yweight0, Assembler::AVX_256bit);
  __ vpmaddubsw(ydata1, ydata1, yweight1, Assembler::AVX_256bit);
  __ vpmaddwd(ydata0, ydata0, yones, Assembler::AVX_256bit);
  __ vpmaddwd(ydata1, ydata1, yones, Assembler::AVX_256bit);
  __ vpaddd(ya, ya, ytmp1, Assembler::AVX_256bit);
  __ vpaddd(yb0, yb0, ydata0, Assembler::AVX_256bit);
  __ vpaddd(yb1, yb1, ydata1, Assembler::AVX_256bit);

  __ cmpl(chunk_size, 64);
  __ jcc(Assembler::aboveEqual, AVX2_LOOP_64);

  // Merge the two B chains after the full 64-byte blocks, apply the 64x A
  // contribution, and reset ysum_a for the optional 32-byte tail.
  __ bind(AVX2_MERGE_FULL_BLOCKS);
  __ vpaddd(yb0, yb0, yb1, Assembler::AVX_256bit);
  __ vpslld(ysum_a, ysum_a, 6, Assembler::AVX_256bit);
  __ vpaddd(yb0, yb0, ysum_a, Assembler::AVX_256bit);
  __ vpxor(ysum_a, ysum_a, ysum_a, Assembler::AVX_256bit);

  __ cmpl(chunk_size, 32);
  __ jcc(Assembler::below, AVX2_REDUCE);

  __ vmovdqu(ydata0, Address(data, 0));
  __ addptr(data, 32);
  __ subl(chunk_size, 32);

  __ vpsadbw(ytmp0, ydata0, yzero, Assembler::AVX_256bit);
  __ vpaddd(ysum_a, ysum_a, ya, Assembler::AVX_256bit);
  __ vpaddd(ya, ya, ytmp0, Assembler::AVX_256bit);
  __ vpmaddubsw(ydata0, ydata0, yweight1, Assembler::AVX_256bit);
  __ vpmaddwd(ydata0, ydata0, yones, Assembler::AVX_256bit);
  __ vpaddd(yb0, yb0, ydata0, Assembler::AVX_256bit);

  __ bind(AVX2_REDUCE);
  // A final 32-byte block contributes 32 * the A value from before that block.
  __ vpslld(ysum_a, ysum_a, 5, Assembler::AVX_256bit);
  __ vpaddd(yb0, yb0, ysum_a, Assembler::AVX_256bit);

  // Horizontally reduce ya. Its byte sums occupy dwords 0, 2, 4 and 6.
  __ vextracti128(ytmp0, ya, 1);
  __ vpaddd(xa, xa, ytmp0, Assembler::AVX_128bit);
  __ vpunpckhqdq(ytmp0, xa, xa, Assembler::AVX_128bit);
  __ vpaddd(xa, xa, ytmp0, Assembler::AVX_128bit);

  // Horizontally reduce yb0. Its partial weighted sums occupy all eight dwords.
  __ vextracti128(ytmp1, yb0, 1);
  __ vpaddd(xb, xb, ytmp1, Assembler::AVX_128bit);
  __ vpunpckhqdq(ytmp1, xb, xb, Assembler::AVX_128bit);
  __ vpaddd(xb, xb, ytmp1, Assembler::AVX_128bit);
  __ vpshufd(ytmp1, xb, 1, Assembler::AVX_128bit);
  __ vpaddd(xb, xb, ytmp1, Assembler::AVX_128bit);

  __ movdl(a_d, xa);
  __ movdl(b_d, xb);

  reduce_mod_base(a_d, rcx);
  reduce_mod_base(b_d, rcx);

  // The AVX2 chunk reduction has already normalized A and B.
  __ testl(size, size);
  __ jcc(Assembler::zero, PACK_RESULT);

  __ cmpl(size, 32);
  __ jcc(Assembler::aboveEqual, LIMIT_CHUNK_LOOP);
  __ cmpl(size, 16);
  __ jcc(Assembler::below, LT16);

  __ bind(PROCESS_16_BLOCK);
  // No vector loop is active here; reuse chunk_size to save A_before.
  __ movl(chunk_size, a_d);
  __ vmovdqu(ydata0, Address(data, 0), Assembler::AVX_128bit);
  __ addptr(data, 16);
  __ subl(size, 16);

  __ vpsadbw(ytmp0, ydata0, yzero, Assembler::AVX_128bit);
  __ movdl(rax, ytmp0);
  __ vpsrldq(ytmp0, ytmp0, 8, Assembler::AVX_128bit);
  __ movdl(r14, ytmp0);
  __ addl(rax, r14);
  __ addl(a_d, rax);

  __ vextracti128(ytmp0, yweight1, 1);
  __ vpmaddubsw(ytmp1, ydata0, ytmp0, Assembler::AVX_128bit);
  __ vpmaddwd(ytmp1, ytmp1, yones, Assembler::AVX_128bit);
  __ vpunpckhqdq(ytmp0, ytmp1, ytmp1, Assembler::AVX_128bit);
  __ vpaddd(ytmp1, ytmp1, ytmp0, Assembler::AVX_128bit);
  __ vpshufd(ytmp0, ytmp1, 1, Assembler::AVX_128bit);
  __ vpaddd(ytmp1, ytmp1, ytmp0, Assembler::AVX_128bit);
  __ movdl(rax, ytmp1);
  __ addl(b_d, rax);
  __ movl(rax, chunk_size);
  __ shll(rax, 4); // Add 16 * a_before_block.
  __ addl(b_d, rax);

  __ bind(LT16);
  __ testl(size, size);
  __ jcc(Assembler::zero, FINAL_REDUCE);
  __ lea(end, Address(data, size, Address::times_1));

  __ align32();
  __ bind(SCALAR_LOOP);
  __ movzbl(rax, Address(data, 0)); //movzx   eax, byte[data]
  __ addl(a_d, rax);
  __ addptr(data, 1);
  __ addl(b_d, a_d);
  __ cmpptr(data, end);
  __ jcc(Assembler::below, SCALAR_LOOP);

  __ bind(FINAL_REDUCE);
  __ movl(rcx, DIV_BASE_MULTIPLIER);
  reduce_mod_base(a_d, rcx);
  reduce_mod_base(b_d, rcx);

  __ bind(PACK_RESULT);
  __ movl(rax, b_d);
  __ shll(rax, 16);
  __ orl(rax, a_d);

  __ pop_ppx(r14);
  __ pop_ppx(r13);
  __ pop_ppx(r12);

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
