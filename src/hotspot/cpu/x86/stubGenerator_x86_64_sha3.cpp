/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, Intel Corporation. All rights reserved.
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
#include "runtime/stubRoutines.hpp"
#include "macroAssembler_x86.hpp"
#include "stubGenerator_x86_64.hpp"

#define __ _masm->

#ifdef PRODUCT
#define BLOCK_COMMENT(str) /* nothing */
#else
#define BLOCK_COMMENT(str) __ block_comment(str)
#endif // PRODUCT

#define BIND(label) bind(label); BLOCK_COMMENT(#label ":")

// Constant pool
ATTRIBUTE_ALIGNED(64) static const uint64_t round_consts_arr[24] = {
  0x8000000080008008L, 0x0000000080000001L, 0x8000000000008080L,
  0x8000000080008081L, 0x800000008000000AL, 0x000000000000800AL,
  0x8000000000000080L, 0x8000000000008002L, 0x8000000000008003L,
  0x8000000000008089L, 0x800000000000008BL, 0x000000008000808BL,
  0x000000008000000AL, 0x0000000080008009L, 0x0000000000000088L,
  0x000000000000008AL, 0x8000000000008009L, 0x8000000080008081L,
  0x0000000080000001L, 0x000000000000808BL, 0x8000000080008000L,
  0x800000000000808AL, 0x0000000000008082L, 0x0000000000000001L
};

ATTRIBUTE_ALIGNED(64) static const uint64_t avx2_round_consts[] = {
  0x8000000080008008L, 0x0L, 0x8000000080008008L, 0x0L,
  0x0000000080000001L, 0x0L, 0x0000000080000001L, 0x0L,
  0x8000000000008080L, 0x0L, 0x8000000000008080L, 0x0L,
  0x8000000080008081L, 0x0L, 0x8000000080008081L, 0x0L,
  0x800000008000000AL, 0x0L, 0x800000008000000AL, 0x0L,
  0x000000000000800AL, 0x0L, 0x000000000000800AL, 0x0L,
  0x8000000000000080L, 0x0L, 0x8000000000000080L, 0x0L,
  0x8000000000008002L, 0x0L, 0x8000000000008002L, 0x0L,
  0x8000000000008003L, 0x0L, 0x8000000000008003L, 0x0L,
  0x8000000000008089L, 0x0L, 0x8000000000008089L, 0x0L,
  0x800000000000008BL, 0x0L, 0x800000000000008BL, 0x0L,
  0x000000008000808BL, 0x0L, 0x000000008000808BL, 0x0L,
  0x000000008000000AL, 0x0L, 0x000000008000000AL, 0x0L,
  0x0000000080008009L, 0x0L, 0x0000000080008009L, 0x0L,
  0x0000000000000088L, 0x0L, 0x0000000000000088L, 0x0L,
  0x000000000000008AL, 0x0L, 0x000000000000008AL, 0x0L,
  0x8000000000008009L, 0x0L, 0x8000000000008009L, 0x0L,
  0x8000000080008081L, 0x0L, 0x8000000080008081L, 0x0L,
  0x0000000080000001L, 0x0L, 0x0000000080000001L, 0x0L,
  0x000000000000808BL, 0x0L, 0x000000000000808BL, 0x0L,
  0x8000000080008000L, 0x0L, 0x8000000080008000L, 0x0L,
  0x800000000000808AL, 0x0L, 0x800000000000808AL, 0x0L,
  0x0000000000008082L, 0x0L, 0x0000000000008082L, 0x0L,
  0x0000000000000001L, 0x0L, 0x0000000000000001L, 0x0L,
};

ATTRIBUTE_ALIGNED(64) static const uint64_t avx2_rotate_consts[] = {
  //  X0      X0     X1  X3  X1  X3     X2  X4   X2  X4
                     1, 28,  1, 28,    62, 27,  62, 27,   //           A1A3,   A2A4
 36, 41, 36, 41,    44, 55, 44, 55,     6, 20,   6, 20,   //  A5A15,   A6A8,   A7A9
  3, 18,  3, 18,    10, 25, 10, 25,    43, 39,  43, 39,   // A10A20, A11A13, A12A14
                    45, 21, 45, 21,    15,  8,  15,  8,   //         A16A18, A17A19
                     2, 56,  2, 56,    61, 14,  61, 14,   //         A21A23, A22A24

  //Offset = 384 = 12*4*8
  //        X0            X0         X1     X3     X1     X3         X2     X4      X2     X4
                                  64- 1, 64-28, 64- 1, 64-28,     64-62, 64-27,  64-62, 64-27,
  64-36, 64-41, 64-36, 64-41,     64-44, 64-55, 64-44, 64-55,     64- 6, 64-20,  64- 6, 64-20,
  64- 3, 64-18, 64- 3, 64-18,     64-10, 64-25, 64-10, 64-25,     64-43, 64-39,  64-43, 64-39,
                                  64-45, 64-21, 64-45, 64-21,     64-15, 64- 8,  64-15, 64- 8,
                                  64- 2, 64-56, 64- 2, 64-56,     64-61, 64-14,  64-61, 64-14,
};

static address round_constsAddr() {
  return (address) round_consts_arr;
}

static address avx2_round_constsAddr() {
  return (address) avx2_round_consts;
}

static address avx2_rotate_constsAddr() {
  return (address) avx2_rotate_consts;
}

// Inputs (sha3_implCompress|sha3_implCompressMB):
//   c_rarg0   - byte[]  source+offset
//   c_rarg1   - long[]  SHA3.state
//   c_rarg2   - int     block_size
//   c_rarg3   - int     offset
//   c_rarg4   - int     limit
//
// Inputs (double_keccak):
//   c_rarg0   - long[]  SHA3.state1
//   c_rarg1   - long[]  SHA3.state2
//
// Inputs (quad_keccak):
//   c_rarg0   - long[]  SHA3.state1
//   c_rarg1   - long[]  SHA3.state2
//   c_rarg2   - long[]  SHA3.state3
//   c_rarg3   - long[]  SHA3.state4
//
// Design notes:
//  With 32 AVX512 registers, we can fit the entire SHA3 state into first 25 registers
//  (using just one element out of each register!). The 'interesting' part of the
//  function is just a translation of Java code. This is (surprisingly) very
//  efficient; and also makes double_keccak and quad_keccak support fairly trivial.
static address generate_sha3_implCompress_avx512(StubId stub_id,
                                          StubGenerator *stubgen,
                                          MacroAssembler *_masm) {
  switch(stub_id) {
  case StubId::stubgen_sha3_implCompress_id:
  case StubId::stubgen_sha3_implCompressMB_id:
  case StubId::stubgen_double_keccak_id:
  case StubId::stubgen_quad_keccak_id:
    break;
  default:
    ShouldNotReachHere();
  }

  int entry_count = StubInfo::entry_count(stub_id);
  assert(entry_count == 1, "sanity check");
  address start = stubgen->load_archive_data(stub_id);
  if (start != nullptr) {
    return start;
  }

  __ align(CodeEntryAlignment);
  StubCodeMark mark(stubgen, stub_id);
  start = __ pc();
  __ enter();

  Register buf, offset, block_size, limit;
  Register state1, state2, state3, state4, state5, state6, state7, state8;
  Register roundsLeft = r10;
  Register round_consts = r11;
  int vector_len = Assembler::AVX_128bit;
  bool multiBlock = stub_id == StubId::stubgen_sha3_implCompressMB_id;
  bool parallelKeccak = true;

  switch (stub_id) {
    case StubId::stubgen_quad_keccak_id:
      vector_len = Assembler::AVX_256bit;
      state1      = c_rarg0;
      state2      = c_rarg1;
      state3      = c_rarg2;
      state4      = c_rarg3;
      break;
    case StubId::stubgen_double_keccak_id:
      state1      = c_rarg0;
      state2      = c_rarg1;
      break;
    default:
      parallelKeccak = false;
      buf         = c_rarg0;
      state1      = c_rarg1;
      block_size  = c_rarg2;
      offset      = c_rarg3;
  #ifndef _WIN64
      limit       = c_rarg4;
  #else
      limit = rdi;
      __ push_ppx(rdi);
      __ movptr(limit, Address(rbp, 6 * wordSize));
  #endif
  }

  __ movl(rax, 0x1);
  __ kmovwl(k1, rax);
  __ lea(round_consts, ExternalAddress(round_constsAddr()));

  XMMRegister  A0 = xmm0;
  XMMRegister  A1 = xmm1;
  XMMRegister  A2 = xmm2;
  XMMRegister  A3 = xmm3;
  XMMRegister  A4 = xmm4;
  XMMRegister  A5 = xmm5;
  XMMRegister  A6 = xmm6;
  XMMRegister  A7 = xmm7;
  XMMRegister  A8 = xmm8;
  XMMRegister  A9 = xmm9;
  XMMRegister A10 = xmm10;
  XMMRegister A11 = xmm11;
  XMMRegister A12 = xmm12;
  XMMRegister A13 = xmm13;
  XMMRegister A14 = xmm14;
  XMMRegister A15 = xmm15;
  XMMRegister A16 = xmm16;
  XMMRegister A17 = xmm17;
  XMMRegister A18 = xmm18;
  XMMRegister A19 = xmm19;
  XMMRegister A20 = xmm20;
  XMMRegister A21 = xmm21;
  XMMRegister A22 = xmm22;
  XMMRegister A23 = xmm23;
  XMMRegister A24 = xmm24;
  XMMRegister C0 = xmm25;
  XMMRegister C1 = xmm26;
  XMMRegister C2 = xmm27;
  XMMRegister C3 = xmm28;
  XMMRegister C4 = xmm29;
  XMMRegister T0 = xmm30;
  XMMRegister T1 = xmm31;

  auto loadState = [=](XMMRegister X1, XMMRegister X2, int disp){
    if (stub_id == StubId::stubgen_quad_keccak_id) {
      __ vmovdqu(T0, Address(state1, disp), Assembler::AVX_128bit);
      __ vmovdqu(T1, Address(state2, disp), Assembler::AVX_128bit);
      __ vmovdqu(C0, Address(state3, disp), Assembler::AVX_128bit);
      __ vmovdqu(C1, Address(state4, disp), Assembler::AVX_128bit);
      __ vshufpd(X1, T0, T1, 0b00, Assembler::AVX_128bit);
      __ vshufpd(X2, T0, T1, 0b11, Assembler::AVX_128bit);
      __ vshufpd(T0, C0, C1, 0b00, Assembler::AVX_128bit);
      __ vshufpd(T1, C0, C1, 0b11, Assembler::AVX_128bit);
      __ vinserti128(X1, X1, T0, 1);
      __ vinserti128(X2, X2, T1, 1);
    } else if (stub_id == StubId::stubgen_double_keccak_id) {
      __ vmovdqu(T0, Address(state1, disp), Assembler::AVX_128bit);
      __ vmovdqu(T1, Address(state2, disp), Assembler::AVX_128bit);
      __ vshufpd(X1, T0, T1, 0b00, Assembler::AVX_128bit);
      __ vshufpd(X2, T0, T1, 0b11, Assembler::AVX_128bit);
    } else {
      // only care about values in first 64bit columns for non-parallel keccak
      __ vmovdqu(X1, Address(state1, disp), Assembler::AVX_128bit);
      __ vshufpd(X2, X1, X1, 0b1, Assembler::AVX_128bit);
    }
  };

  loadState( A0,  A1,  0 * 8);
  loadState( A2,  A3,  2 * 8);
  loadState( A4,  A5,  4 * 8);
  loadState( A6,  A7,  6 * 8);
  loadState( A8,  A9,  8 * 8);
  loadState(A10, A11, 10 * 8);
  loadState(A12, A13, 12 * 8);
  loadState(A14, A15, 14 * 8);
  loadState(A16, A17, 16 * 8);
  loadState(A18, A19, 18 * 8);
  loadState(A20, A21, 20 * 8);
  loadState(A22, A23, 22 * 8);
  __ movq(A24, Address(state1, 24 * 8));
  if (stub_id == StubId::stubgen_quad_keccak_id) {
    __ movq(T0, Address(state2, 24 * 8));
    __ vshufpd(A24, A24, T0, 0b00, Assembler::AVX_128bit);

    __ movq(T0, Address(state3, 24 * 8));
    __ movq(T1, Address(state4, 24 * 8));
    __ vshufpd(T0, T0, T1, 0b00, Assembler::AVX_128bit);
    __ vinserti128(A24, A24, T0, 1);
  } else if (stub_id == StubId::stubgen_double_keccak_id) {
    __ movq(T0, Address(state2, 24 * 8));
    __ vshufpd(A24, A24, T0, 0b00, Assembler::AVX_128bit);
  }

  Label rounds24_loop, multi_loop;
  __ align(OptoLoopAlignment);
  __ BIND(multi_loop);
  __ movl(roundsLeft, 23);

  if (!parallelKeccak) {
    __ evpxorq( A0, k1,  A0, Address(buf,  0 * 8), false, Assembler::AVX_128bit);
    __ evpxorq( A1, k1,  A1, Address(buf,  1 * 8), false, Assembler::AVX_128bit);
    __ evpxorq( A2, k1,  A2, Address(buf,  2 * 8), false, Assembler::AVX_128bit);
    __ evpxorq( A3, k1,  A3, Address(buf,  3 * 8), false, Assembler::AVX_128bit);
    __ evpxorq( A4, k1,  A4, Address(buf,  4 * 8), false, Assembler::AVX_128bit);
    __ evpxorq( A5, k1,  A5, Address(buf,  5 * 8), false, Assembler::AVX_128bit);
    __ evpxorq( A6, k1,  A6, Address(buf,  6 * 8), false, Assembler::AVX_128bit);
    __ evpxorq( A7, k1,  A7, Address(buf,  7 * 8), false, Assembler::AVX_128bit);
    __ evpxorq( A8, k1,  A8, Address(buf,  8 * 8), false, Assembler::AVX_128bit);
    __ cmpl(block_size, 72);
    __ jcc(Assembler::equal, rounds24_loop);
    __ evpxorq( A9, k1,  A9, Address(buf,  9 * 8), false, Assembler::AVX_128bit);
    __ evpxorq(A10, k1, A10, Address(buf, 10 * 8), false, Assembler::AVX_128bit);
    __ evpxorq(A11, k1, A11, Address(buf, 11 * 8), false, Assembler::AVX_128bit);
    __ evpxorq(A12, k1, A12, Address(buf, 12 * 8), false, Assembler::AVX_128bit);
    __ cmpl(block_size, 104);
    __ jcc(Assembler::equal, rounds24_loop);
    __ evpxorq(A13, k1, A13, Address(buf, 13 * 8), false, Assembler::AVX_128bit);
    __ evpxorq(A14, k1, A14, Address(buf, 14 * 8), false, Assembler::AVX_128bit);
    __ evpxorq(A15, k1, A15, Address(buf, 15 * 8), false, Assembler::AVX_128bit);
    __ evpxorq(A16, k1, A16, Address(buf, 16 * 8), false, Assembler::AVX_128bit);
    __ cmpl(block_size, 136);
    __ jcc(Assembler::equal, rounds24_loop);
    __ evpxorq(A17, k1, A17, Address(buf, 17 * 8), false, Assembler::AVX_128bit);
    __ cmpl(block_size, 144);
    __ jcc(Assembler::equal, rounds24_loop);
    __ evpxorq(A18, k1, A18, Address(buf, 18 * 8), false, Assembler::AVX_128bit);
    __ evpxorq(A19, k1, A19, Address(buf, 19 * 8), false, Assembler::AVX_128bit);
    __ evpxorq(A20, k1, A20, Address(buf, 20 * 8), false, Assembler::AVX_128bit);
  }

  __ align(OptoLoopAlignment);
  __ BIND(rounds24_loop);

  // Step mapping Theta as defined in section 3.2.1.
  // long c0 = a0^a5^a10^a15^a20;
  // long c1 = a1^a6^a11^a16^a21;
  // long c2 = a2^a7^a12^a17^a22;
  // long c3 = a3^a8^a13^a18^a23;
  // long c4 = a4^a9^a14^a19^a24;

  __ evmovdquq(C0, A0, vector_len);
  __ evmovdquq(C1, A1, vector_len);
  __ evmovdquq(C2, A2, vector_len);
  __ evmovdquq(C3, A3, vector_len);
  __ evmovdquq(C4, A4, vector_len);

  __ vpternlogq(C0, 0x96, A5, A10, vector_len);
  __ vpternlogq(C1, 0x96, A6, A11, vector_len);
  __ vpternlogq(C2, 0x96, A7, A12, vector_len);
  __ vpternlogq(C3, 0x96, A8, A13, vector_len);
  __ vpternlogq(C4, 0x96, A9, A14, vector_len);

  __ vpternlogq(C0, 0x96, A15, A20, vector_len);
  __ vpternlogq(C1, 0x96, A16, A21, vector_len);
  __ vpternlogq(C2, 0x96, A17, A22, vector_len);
  __ vpternlogq(C3, 0x96, A18, A23, vector_len);
  __ vpternlogq(C4, 0x96, A19, A24, vector_len);

  // long d0 = c4 ^ Long.rotateLeft(c1, 1);
  // long d1 = c0 ^ Long.rotateLeft(c2, 1);
  // long d2 = c1 ^ Long.rotateLeft(c3, 1);
  // long d3 = c2 ^ Long.rotateLeft(c4, 1);
  // long d4 = c3 ^ Long.rotateLeft(c0, 1);
  // a0  ^= d0; a1  ^= d1; a2  ^= d2; a3  ^= d3; a4  ^= d4;
  // a5  ^= d0; a6  ^= d1; a7  ^= d2; a8  ^= d3; a9  ^= d4;
  // a10 ^= d0; a11 ^= d1; a12 ^= d2; a13 ^= d3; a14 ^= d4;
  // a15 ^= d0; a16 ^= d1; a17 ^= d2; a18 ^= d3; a19 ^= d4;
  // a20 ^= d0; a21 ^= d1; a22 ^= d2; a23 ^= d3; a24 ^= d4;

  __ evprolq(T0, C1, 1, vector_len);
  __ vpternlogq(A0 , 0x96, T0, C4, vector_len);
  __ vpternlogq(A5 , 0x96, T0, C4, vector_len);
  __ vpternlogq(A10, 0x96, T0, C4, vector_len);
  __ vpternlogq(A15, 0x96, T0, C4, vector_len);
  __ vpternlogq(A20, 0x96, T0, C4, vector_len);

  __ evprolq(T0, C2, 1, vector_len);
  __ vpternlogq(A1 , 0x96, T0, C0, vector_len);
  __ vpternlogq(A6 , 0x96, T0, C0, vector_len);
  __ vpternlogq(A11, 0x96, T0, C0, vector_len);
  __ vpternlogq(A16, 0x96, T0, C0, vector_len);
  __ vpternlogq(A21, 0x96, T0, C0, vector_len);

  __ evprolq(T0, C3, 1, vector_len);
  __ vpternlogq(A2 , 0x96, T0, C1, vector_len);
  __ vpternlogq(A7 , 0x96, T0, C1, vector_len);
  __ vpternlogq(A12, 0x96, T0, C1, vector_len);
  __ vpternlogq(A17, 0x96, T0, C1, vector_len);
  __ vpternlogq(A22, 0x96, T0, C1, vector_len);

  __ evprolq(T0, C4, 1, vector_len);
  __ vpternlogq(A3 , 0x96, T0, C2, vector_len);
  __ vpternlogq(A8 , 0x96, T0, C2, vector_len);
  __ vpternlogq(A13, 0x96, T0, C2, vector_len);
  __ vpternlogq(A18, 0x96, T0, C2, vector_len);
  __ vpternlogq(A23, 0x96, T0, C2, vector_len);

  __ evprolq(T0, C0, 1, vector_len);
  __ vpternlogq(A4 , 0x96, T0, C3, vector_len);
  __ vpternlogq(A9 , 0x96, T0, C3, vector_len);
  __ vpternlogq(A14, 0x96, T0, C3, vector_len);
  __ vpternlogq(A19, 0x96, T0, C3, vector_len);
  __ vpternlogq(A24, 0x96, T0, C3, vector_len);

  // Merged Step mapping Rho (section 3.2.2) and Pi (section 3.2.3)
  // long ay = Long.rotateLeft(a10, 3);
  // a10 = Long.rotateLeft(a1, 1);
  // a1 = Long.rotateLeft(a6, 44);
  // a6 = Long.rotateLeft(a9, 20);
  // a9 = Long.rotateLeft(a22, 61);
  // a22 = Long.rotateLeft(a14, 39);
  // a14 = Long.rotateLeft(a20, 18);
  // a20 = Long.rotateLeft(a2, 62);
  // a2 = Long.rotateLeft(a12, 43);
  // a12 = Long.rotateLeft(a13, 25);
  // a13 = Long.rotateLeft(a19, 8);
  // a19 = Long.rotateLeft(a23, 56);
  // a23 = Long.rotateLeft(a15, 41);
  // a15 = Long.rotateLeft(a4, 27);
  // a4 = Long.rotateLeft(a24, 14);
  // a24 = Long.rotateLeft(a21, 2);
  // a21 = Long.rotateLeft(a8, 55);
  // a8 = Long.rotateLeft(a16, 45);
  // a16 = Long.rotateLeft(a5, 36);
  // a5 = Long.rotateLeft(a3, 28);
  // a3 = Long.rotateLeft(a18, 21);
  // a18 = Long.rotateLeft(a17, 15);
  // a17 = Long.rotateLeft(a11, 10);
  // a11 = Long.rotateLeft(a7, 6);
  // a7 = ay;

  __ evprolq(T0, A10, 3, vector_len);
  __ evprolq(A10, A1, 1, vector_len);
  __ evprolq(A1, A6, 44, vector_len);
  __ evprolq(A6, A9, 20, vector_len);
  __ evprolq(A9, A22, 61, vector_len);
  __ evprolq(A22, A14, 39, vector_len);
  __ evprolq(A14, A20, 18, vector_len);
  __ evprolq(A20, A2, 62, vector_len);
  __ evprolq(A2, A12, 43, vector_len);
  __ evprolq(A12, A13, 25, vector_len);
  __ evprolq(A13, A19, 8, vector_len);
  __ evprolq(A19, A23, 56, vector_len);
  __ evprolq(A23, A15, 41, vector_len);
  __ evprolq(A15, A4, 27, vector_len);
  __ evprolq(A4, A24, 14, vector_len);
  __ evprolq(A24, A21, 2, vector_len);
  __ evprolq(A21, A8, 55, vector_len);
  __ evprolq(A8, A16, 45, vector_len);
  __ evprolq(A16, A5, 36, vector_len);
  __ evprolq(A5, A3, 28, vector_len);
  __ evprolq(A3, A18, 21, vector_len);
  __ evprolq(A18, A17, 15, vector_len);
  __ evprolq(A17, A11, 10, vector_len);
  __ evprolq(A11, A7, 6, vector_len);
  __ evmovdquq(A7, T0, vector_len);

  // // Step mapping Chi as defined in section 3.2.4.
  // long tmp0 = a0;
  // long tmp1 = a1;
  // long tmp2 = a2;
  // long tmp3 = a3;
  // long tmp4 = a4;
  // a0 = tmp0 ^ ((~tmp1) & tmp2);
  // a1 = tmp1 ^ ((~tmp2) & tmp3);
  // a2 = tmp2 ^ ((~tmp3) & tmp4);
  // a3 = tmp3 ^ ((~tmp4) & tmp0);
  // a4 = tmp4 ^ ((~tmp0) & tmp1);
  __ evmovdquq(T0, A0, vector_len);
  __ evmovdquq(T1, A1, vector_len);
  __ vpternlogq(A0 , 0xD2, A1, A2, vector_len);
  __ vpternlogq(A1 , 0xD2, A2, A3, vector_len);
  __ vpternlogq(A2 , 0xD2, A3, A4, vector_len);
  __ vpternlogq(A3 , 0xD2, A4, T0, vector_len);
  __ vpternlogq(A4 , 0xD2, T0, T1, vector_len);

  // Step mapping Iota as defined in section 3.2.5.
  // a0 ^= RC_CONSTANTS[ir];
  __ vpbroadcastq(T0, Address(round_consts, roundsLeft, Address::times_8), vector_len);
  __ evpxorq(A0, A0, T0, vector_len);

  // tmp0 = a5; tmp1 = a6; tmp2 = a7; tmp3 = a8; tmp4 = a9;
  // a5 = tmp0 ^ ((~tmp1) & tmp2);
  // a6 = tmp1 ^ ((~tmp2) & tmp3);
  // a7 = tmp2 ^ ((~tmp3) & tmp4);
  // a8 = tmp3 ^ ((~tmp4) & tmp0);
  // a9 = tmp4 ^ ((~tmp0) & tmp1);
  __ evmovdquq(T0, A5, vector_len);
  __ evmovdquq(T1, A6, vector_len);
  __ vpternlogq(A5 , 0xD2, A6, A7, vector_len);
  __ vpternlogq(A6 , 0xD2, A7, A8, vector_len);
  __ vpternlogq(A7 , 0xD2, A8, A9, vector_len);
  __ vpternlogq(A8 , 0xD2, A9, T0, vector_len);
  __ vpternlogq(A9 , 0xD2, T0, T1, vector_len);

  // tmp0 = a10; tmp1 = a11; tmp2 = a12; tmp3 = a13; tmp4 = a14;
  // a10 = tmp0 ^ ((~tmp1) & tmp2);
  // a11 = tmp1 ^ ((~tmp2) & tmp3);
  // a12 = tmp2 ^ ((~tmp3) & tmp4);
  // a13 = tmp3 ^ ((~tmp4) & tmp0);
  // a14 = tmp4 ^ ((~tmp0) & tmp1);
  __ evmovdquq(T0, A10, vector_len);
  __ evmovdquq(T1, A11, vector_len);
  __ vpternlogq(A10 , 0xD2, A11, A12, vector_len);
  __ vpternlogq(A11 , 0xD2, A12, A13, vector_len);
  __ vpternlogq(A12 , 0xD2, A13, A14, vector_len);
  __ vpternlogq(A13 , 0xD2, A14,  T0, vector_len);
  __ vpternlogq(A14 , 0xD2,  T0,  T1, vector_len);

  // tmp0 = a15; tmp1 = a16; tmp2 = a17; tmp3 = a18; tmp4 = a19;
  // a15 = tmp0 ^ ((~tmp1) & tmp2);
  // a16 = tmp1 ^ ((~tmp2) & tmp3);
  // a17 = tmp2 ^ ((~tmp3) & tmp4);
  // a18 = tmp3 ^ ((~tmp4) & tmp0);
  // a19 = tmp4 ^ ((~tmp0) & tmp1);
  __ evmovdquq(T0, A15, vector_len);
  __ evmovdquq(T1, A16, vector_len);
  __ vpternlogq(A15 , 0xD2, A16, A17, vector_len);
  __ vpternlogq(A16 , 0xD2, A17, A18, vector_len);
  __ vpternlogq(A17 , 0xD2, A18, A19, vector_len);
  __ vpternlogq(A18 , 0xD2, A19, T0, vector_len);
  __ vpternlogq(A19 , 0xD2,  T0, T1, vector_len);

  // tmp0 = a20; tmp1 = a21; tmp2 = a22; tmp3 = a23; tmp4 = a24;
  // a20 = tmp0 ^ ((~tmp1) & tmp2);
  // a21 = tmp1 ^ ((~tmp2) & tmp3);
  // a22 = tmp2 ^ ((~tmp3) & tmp4);
  // a23 = tmp3 ^ ((~tmp4) & tmp0);
  // a24 = tmp4 ^ ((~tmp0) & tmp1);
  __ evmovdquq(T0, A20, vector_len);
  __ evmovdquq(T1, A21, vector_len);
  __ vpternlogq(A20 , 0xD2, A21, A22, vector_len);
  __ vpternlogq(A21 , 0xD2, A22, A23, vector_len);
  __ vpternlogq(A22 , 0xD2, A23, A24, vector_len);
  __ vpternlogq(A23 , 0xD2, A24,  T0, vector_len);
  __ vpternlogq(A24 , 0xD2,  T0,  T1, vector_len);

  __ decrementl(roundsLeft);
  __ jcc(Assembler::positive, rounds24_loop);

  if (multiBlock) {
    __ addptr(buf, block_size);
    __ addl(offset, block_size);
    __ cmpl(offset, limit);
    __ jcc(Assembler::lessEqual, multi_loop);
    __ movq(rax, offset); // return offset
  } else {
    __ xorq(rax, rax); // return 0
  }

  auto storeState = [=](int disp, XMMRegister X1, XMMRegister X2){
    if (stub_id == StubId::stubgen_quad_keccak_id) {
      __ vshufpd(T0, X1, X2, 0b0000, Assembler::AVX_256bit);
      __ vshufpd(T1, X1, X2, 0b1111, Assembler::AVX_256bit);
      __ vmovdqu(Address(state1, disp), T0, Assembler::AVX_128bit);
      __ vmovdqu(Address(state2, disp), T1, Assembler::AVX_128bit);
      __ vextracti128(Address(state3, disp), T0, 1);
      __ vextracti128(Address(state4, disp), T1, 1);
    } else if (stub_id == StubId::stubgen_double_keccak_id) {
      __ vshufpd(T0, X1, X2, 0b00, Assembler::AVX_128bit);
      __ vshufpd(T1, X1, X2, 0b11, Assembler::AVX_128bit);
      __ vmovdqu(Address(state1, disp), T0, Assembler::AVX_128bit);
      __ vmovdqu(Address(state2, disp), T1, Assembler::AVX_128bit);
    } else {
      __ pextrq(Address(state1, disp),   X1, 0);
      __ pextrq(Address(state1, disp+8), X2, 0);
    }
  };

  storeState( 0 * 8,  A0,  A1);
  storeState( 2 * 8,  A2,  A3);
  storeState( 4 * 8,  A4,  A5);
  storeState( 6 * 8,  A6,  A7);
  storeState( 8 * 8,  A8,  A9);
  storeState(10 * 8, A10, A11);
  storeState(12 * 8, A12, A13);
  storeState(14 * 8, A14, A15);
  storeState(16 * 8, A16, A17);
  storeState(18 * 8, A18, A19);
  storeState(20 * 8, A20, A21);
  storeState(22 * 8, A22, A23);
  __ pextrq(Address(state1, 24 * 8), A24, 0);
  if (stub_id == StubId::stubgen_quad_keccak_id) {
    __ pextrq(Address(state2, 24 * 8), A24, 1);
    __ vextracti32x4(A24, A24, 1);
    __ pextrq(Address(state3, 24 * 8), A24, 0);
    __ pextrq(Address(state4, 24 * 8), A24, 1);
  } else if (stub_id == StubId::stubgen_double_keccak_id) {
    __ pextrq(Address(state2, 24 * 8), A24, 1);
  }

  // Cleanup
  // Zero out zmm0-zmm31.
  __ vzeroall();
  for (XMMRegister rxmm = xmm16; rxmm->is_valid(); rxmm = rxmm->successor()) {
    __ vpxorq(rxmm, rxmm, rxmm, vector_len);
  }

  if (!parallelKeccak) {
#ifdef _WIN64
    __ pop_ppx(rdi);
#endif
  }

  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ ret(0);
  // record the stub entry and end
  stubgen->store_archive_data(stub_id, start, __ pc());

  return start;
}

// Inputs (sha3_implCompress|sha3_implCompressMB):
//   c_rarg0   - byte[]  source+offset
//   c_rarg1   - long[]  SHA3.state
//   c_rarg2   - int     block_size
//   c_rarg3   - int     offset
//   c_rarg4   - int     limit
//
// Inputs (double_keccak):
//   c_rarg0   - long[]  SHA3.state1
//   c_rarg1   - long[]  SHA3.state2
//
// Pseudocode:
//   loadStates
//   xor(buf, state, blocksize) IF !dualKeccak
//   shuffle(state)
//   LBL: {
//     KECCAK()
//     IF multiBlock {
//       if (buflen) break;
//       buf++, buflen--;
//       shuffle(buf)
//       xor(buf, state, blocksize)
//       goto LBL
//     }
//   }
//   storeStates
//
// KECCAK AVX2 design notes:
//  (1) - The algorithm was written to fit into 128-bit LANE
//        (i.e. hence parallelKeccak takes full 256bit register)
//  (2) - a lot of shuffles are inevitable, since there are not enough registers.
//        To save some shuffles, column1-column3 and column2-4 are placed into
//        the same 128-bit register. Column 0 is also grouped (by rows).
// This means the SHA3 state fits into 12.5 regisers, leaving 3 registers as
// temporaries. This is mostly sufficient, except for the Theta step, where we
// have to buy two slots on the stack
static address generate_sha3_implCompress_avx2(StubId stub_id,
                                          StubGenerator *stubgen,
                                          MacroAssembler *_masm) {
  switch(stub_id) {
  case StubId::stubgen_sha3_implCompress_id:
  case StubId::stubgen_sha3_implCompressMB_id:
  case StubId::stubgen_double_keccak_id:
    break;
  default:
    ShouldNotReachHere();
  }

  int entry_count = StubInfo::entry_count(stub_id);
  assert(entry_count == 1, "sanity check");
  address start = stubgen->load_archive_data(stub_id);
  if (start != nullptr) {
    return start;
  }

  __ align(CodeEntryAlignment);
  StubCodeMark mark(stubgen, stub_id);
  start = __ pc();
  __ enter();

  bool multiBlock = stub_id == StubId::stubgen_sha3_implCompressMB_id;
  bool parallelKeccak = stub_id == StubId::stubgen_double_keccak_id;
  int vector_len, reg_size;
  Register buf, offset, block_size, limit;
  Register state1, state2;
  Register roundsLeft = r10;
  Register round_consts = r11;
  Register rotate_consts;

  if (parallelKeccak) {
    vector_len = Assembler::AVX_256bit;
    reg_size = 32;
    state1      = c_rarg0;
    state2      = c_rarg1;
    rotate_consts = r9;
  } else {
    vector_len = Assembler::AVX_128bit;
    reg_size = 16;
    buf         = c_rarg0;
    state1      = c_rarg1;
    block_size  = c_rarg2;
    offset      = c_rarg3;
  #ifndef _WIN64
    limit       = c_rarg4;
  #else
    limit = rdi;
    __ push_ppx(rdi);
    __ movptr(limit, Address(rbp, 6 * wordSize));
  #endif
    rotate_consts = r12;
    __ push_ppx(r12);
  }
  __ push_ppx(rbp);
  __ movq(rbp, rsp);
  __ andq(rsp, -32);
  __ subptr(rsp, reg_size*2);

  // Registers for memory load
  // Notice the careful 'missalignment' of pairs.
  // This helps XOR for all blocksizes
  XMMRegister    a0a1,   _a2,   a3a4;
  XMMRegister   a5a6,   a7a8,    _a9;
  XMMRegister   a10a11, _a12, a13a14;
  XMMRegister   a15a16, _a17, a18a19;
  XMMRegister   _a20, a21a22, a23a24;

  // Registers for rounds24
  XMMRegister    A0_,   A1A3,   A2A4;
  XMMRegister  A5A15,   A6A8,   A7A9;
  XMMRegister A10A20, A11A13, A12A14;
  XMMRegister         A16A18, A17A19;
  XMMRegister         A21A23, A22A24;
  XMMRegister    C0_,   C1C3,   C2C4;

  XMMRegister T0, T1, T2, T3, T4, tmp1;

  // (Very Careful) Register allocation
  a0a1   = A0_         = xmm0;
  tmp1   = A1A3        = xmm1;
   _a2   = A2A4        = xmm2;
  a3a4   = A5A15       = xmm3;
  a5a6   = A6A8        = xmm4;
  a7a8   = A7A9        = xmm5;
  _a9    = A10A20      = xmm6;
  a10a11 = A11A13      = xmm7;
  _a12   = A12A14      = xmm8;
  a13a14 = A16A18      = xmm9;
  a15a16 = A17A19      = xmm10;
  _a17   = A21A23 = T3 = xmm11;
  a18a19 = A22A24 = T4 = xmm12;
  _a20   = C0_    = T0 = xmm13;
  a21a22 = C1C3   = T1 = xmm14;
  a23a24 = C2C4   = T2 = xmm15;

  __ lea(round_consts, ExternalAddress(avx2_round_constsAddr()));
  __ lea(rotate_consts, ExternalAddress(avx2_rotate_constsAddr()));

  auto loadState = [=](XMMRegister dst, int disp){
    __ vmovdqu(dst, Address(state1, disp), Assembler::AVX_128bit);
    if (parallelKeccak) {
      __ vinserti128(dst, dst, Address(state2, disp), 1);
    }
  };
  // load the state
  loadState(a0a1,    0 * 8);
  loadState(_a2,     1 * 8);
  loadState(a3a4,    3 * 8);
  loadState(a5a6,    5 * 8);
  loadState(a7a8,    7 * 8);
  loadState(_a9,     8 * 8);
  loadState(a10a11, 10 * 8);
  loadState(_a12,   11 * 8);
  loadState(a13a14, 13 * 8);
  loadState(a15a16, 15 * 8);
  loadState(_a17  , 16 * 8);
  loadState(a18a19, 18 * 8);
  loadState(_a20  , 19 * 8);
  loadState(a21a22, 21 * 8);
  loadState(a23a24, 23 * 8);

  if (!parallelKeccak) {
    Label buffer_done;
    // load input from buffer: 72, 104, 136, 144 or 168 bytes
    // i.e. 5+4, 2*5+3, 3*5+2, 3*5+3 or 4*5+1 longs
    __ vpxor(a0a1, a0a1,      Address(buf, 0 * 8), vector_len);
    __ vpxor(_a2,  _a2,       Address(buf, 1 * 8), vector_len);
    __ vpxor(a3a4, a3a4,      Address(buf, 3 * 8), vector_len);
    __ vpxor(a5a6, a5a6,      Address(buf, 5 * 8), vector_len);
    __ vpxor(a7a8, a7a8,      Address(buf, 7 * 8), vector_len);
    __ cmpl(block_size, 72);
    __ jcc(Assembler::equal, buffer_done);
    __ vpxor(_a9,    _a9,    Address(buf,  8 * 8), vector_len);
    __ vpxor(a10a11, a10a11, Address(buf, 10 * 8), vector_len);
    __ vpxor(_a12,   _a12,   Address(buf, 11 * 8), vector_len);
    __ cmpl(block_size, 104);
    __ jcc(Assembler::equal, buffer_done);
    __ vpxor(a13a14, a13a14, Address(buf, 13 * 8), vector_len);
    __ vpxor(a15a16, a15a16, Address(buf, 15 * 8), vector_len);
    __ cmpl(block_size, 136);
    __ jcc(Assembler::equal, buffer_done);
    __ vpxor(_a17, _a17,     Address(buf, 16 * 8), vector_len);
    __ cmpl(block_size, 144);
    __ jcc(Assembler::equal, buffer_done);
    __ vpxor(a18a19, a18a19, Address(buf, 18 * 8), vector_len);
    __ vpxor(_a20,   _a20,   Address(buf, 19 * 8), vector_len);
    __ BIND(buffer_done);
  }

  // Shuffle state registers for the round24 loop
  __ vshufpd(  A1A3,   a0a1,   a3a4, 0b0101, vector_len);
  __ vshufpd(  A2A4,    _a2,   a3a4, 0b1111, vector_len);
  __ vshufpd( A5A15,   a5a6, a15a16, 0b0000, vector_len);
  __ vshufpd(  A6A8,   a5a6,   a7a8, 0b1111, vector_len);
  __ vshufpd(  A7A9,   a7a8,    _a9, 0b1010, vector_len);
  __ vshufpd(A10A20, a10a11,   _a20, 0b1010, vector_len);
  __ vshufpd(A11A13, a10a11, a13a14, 0b0101, vector_len);
  __ vshufpd(A12A14,   _a12, a13a14, 0b1111, vector_len);
  __ vshufpd(A16A18, a15a16, a18a19, 0b0101, vector_len);
  __ vshufpd(A17A19,   _a17, a18a19, 0b1111, vector_len);
  __ vshufpd(A21A23, a21a22, a23a24, 0b0000, vector_len);
  __ vshufpd(A22A24, a21a22, a23a24, 0b1111, vector_len);

  // there will be 24 keccak rounds
  // also use roundsLeft as index into avx2_round_consts array
  __ movl(roundsLeft, 23*4);
  Label rounds24_loop;
  __ align(OptoLoopAlignment);
  __ BIND(rounds24_loop);

  __ vmovdqa(Address(rsp, 0),        A21A23, vector_len);
  __ vmovdqa(Address(rsp, reg_size), A22A24, vector_len);

  // Step mapping Theta as defined in section 3.2.1.
  // long c0 = a0^a5^a10^a15^a20;
  // long c1 = a1^a6^a11^a16^a21;
  // long c2 = a2^a7^a12^a17^a22;
  // long c3 = a3^a8^a13^a18^a23;
  // long c4 = a4^a9^a14^a19^a24;
  __ vpxor(   C0_,  A5A15, A10A20, vector_len);
  __ vpxor(A21A23, A21A23, A16A18, vector_len);
  __ vpxor(A22A24, A22A24, A17A19, vector_len);
  __ vshufpd(C1C3,    C0_,    C0_, 0b1111, vector_len);
  __ vpxor(   C0_,    C0_,    A0_, vector_len);
  __ vpxor(   C0_,    C0_,   C1C3, vector_len);
  __ vpxor(  C1C3,   A1A3,   A6A8, vector_len);
  __ vpxor(  C2C4,   A2A4,   A7A9, vector_len);
  __ vpxor(A21A23, A21A23, A11A13, vector_len);
  __ vpxor(A22A24, A22A24, A12A14, vector_len);
  __ vpxor(  C1C3,   C1C3, A21A23, vector_len);
  __ vpxor(  C2C4,   C2C4, A22A24, vector_len);

  // long d0 = c4 ^ Long.rotateLeft(c1, 1);
  // long d1 = c0 ^ Long.rotateLeft(c2, 1);
  // long d2 = c1 ^ Long.rotateLeft(c3, 1);
  // long d3 = c2 ^ Long.rotateLeft(c4, 1);
  // long d4 = c3 ^ Long.rotateLeft(c0, 1);
  //    C4_  |   C0C2 |   C1C3
  //    C1_  |   C2C4 |   C3C0 (rot1)
  //    -----+--------+------- (xor)
  //    C0C0 | A21A23 | A22A24

  // Even Column: A22A24 (Overloaded with T4)
  __ vshufpd(T3, C1C3,  C0_, 0b0101, vector_len); //C3C0
  __ vpsrlq(T4, T3, 63, vector_len);
  __ vpsllq(T3, T3,  1, vector_len);
  __ vpor(T3, T3, T4, vector_len);
  __ vpxor(A22A24/*T4*/,   T3, C1C3, vector_len);

  // First Column C0C0
  __ vpsllq(T3, C1C3,  1, vector_len);
  __ vpsrlq(C1C3, C1C3, 63, vector_len);
  __ vpor(C1C3, T3, C1C3, vector_len); // C1_
  __ vshufpd(T3, C2C4, C2C4, 0b1111, vector_len); //C4_
  __ vpxor(C1C3,   T3, C1C3, vector_len);

  // Odd Column: A21A23 (Overloaded with T3)
  __ vshufpd(C0_, C0_, C2C4, 0b0000, vector_len); //C0C2
  __ vpsllq(T3, C2C4,  1, vector_len);
  __ vpsrlq(C2C4, C2C4, 63, vector_len);
  __ vpor(C2C4, T3, C2C4, vector_len); // C2C4
  __ vpxor(A21A23/*T3*/, C2C4, C0_, vector_len);

  __ vshufpd(C0_, C1C3, C1C3, 0b0000, vector_len); //C0C0

  // a0  ^= d0; a1  ^= d1; a2  ^= d2; a3  ^= d3; a4  ^= d4;
  // a5  ^= d0; a6  ^= d1; a7  ^= d2; a8  ^= d3; a9  ^= d4;
  // a10 ^= d0; a11 ^= d1; a12 ^= d2; a13 ^= d3; a14 ^= d4;
  // a15 ^= d0; a16 ^= d1; a17 ^= d2; a18 ^= d3; a19 ^= d4;
  // a20 ^= d0; a21 ^= d1; a22 ^= d2; a23 ^= d3; a24 ^= d4;
  __ vpxor(  A2A4,   A2A4, A22A24, vector_len);
  __ vpxor(  A7A9,   A7A9, A22A24, vector_len);
  __ vpxor(A12A14, A12A14, A22A24, vector_len);
  __ vpxor(A17A19, A17A19, A22A24, vector_len);
  __ vpxor(A22A24, A22A24, Address(rsp, reg_size), vector_len); // Restore A22A24 from stack

  __ vpxor(  A1A3,   A1A3, A21A23, vector_len);
  __ vpxor(  A6A8,   A6A8, A21A23, vector_len);
  __ vpxor(A11A13, A11A13, A21A23, vector_len);
  __ vpxor(A16A18, A16A18, A21A23, vector_len);
  __ vpxor(A21A23, A21A23, Address(rsp, 0), vector_len); // Restore A21A23 from stack

  __ vpxor(   A0_,    A0_, C0_, vector_len);
  __ vpxor( A5A15,  A5A15, C0_, vector_len);
  __ vpxor(A10A20, A10A20, C0_, vector_len);

  // Rho and Pi steps
  //    A0_= a0,0|x         A1A3=a6,44|a18,21    A2A4=a12,43|a24,14
  //  A5A15=a3,28|a4,27     A6A8=a9,20|a16,45    A7A9= a10,3|a22,61
  // A10A20= a1,1|a2,62   A11A13= a7,6|a19,8   A12A14=a13,25|a20,18
  //                      A16A18=a5,36|a17,15  A17A19=a11,10|a23,56
  //                      A21A23=a8,55|a15,41  A22A24=a14,39|a21,2
  auto rotate = [=](XMMRegister dst, int disp){
    __ vpsllvq(T0,  dst, Address(rotate_consts,       disp*4*8), vector_len);
    __ vpsrlvq(dst, dst, Address(rotate_consts, 384 + disp*4*8), vector_len);
    __ vpor(dst, T0, dst, vector_len);
  };

  rotate(  A1A3,  0);
  rotate(  A2A4,  1);
  rotate( A5A15,  2);
  rotate(  A6A8,  3);
  rotate(  A7A9,  4);
  rotate(A10A20,  5);
  rotate(A11A13,  6);
  rotate(A12A14,  7);
  rotate(A16A18,  8);
  rotate(A17A19,  9);
  rotate(A21A23, 10);
  rotate(A22A24, 11);

  __ vmovdqu(T0, A22A24, vector_len);
  __ vmovdqu(T1, A17A19, vector_len);
  __ vmovdqu(T2, A1A3, vector_len);
  __ vshufpd(A0_,       A0_,   A1A3, 0b0000, vector_len);           // A0_ = A0A1
  __ vshufpd(A22A24, A12A14, A21A23, 0b0101, vector_len);           // A22A24 = a14a21
  __ vshufpd(A17A19, A11A13, A21A23, 0b1010, vector_len);           // A17A19 = a11a23
  __ vshufpd(A1A3,     A6A8, A16A18, 0b1010, vector_len);           // A1A3 = a6a18
  __ vshufpd(A21A23,   A6A8, A5A15 , 0b1111, vector_len);           // A21A23 = a8a15
  __ vshufpd(A6A8,     A7A9, A16A18, 0b0101, vector_len);           // A6A8 = a9a16
  __ vshufpd(A16A18,  A5A15, T1 /*A17A19*/, 0b0000, vector_len);    // A16A18 = a5a17
  __ vshufpd(A5A15,   T2 /*A1A3*/, A2A4,    0b1111, vector_len);    // A5A15 = a3a4
  __ vmovdqu(T2, A10A20, vector_len);
  __ vshufpd(A10A20,   A0_/*A0A1*/,   A2A4, 0b0101, vector_len);    // A10A20 = a1a2
  __ vshufpd(A2A4,   A12A14, T0 /*A22A24*/, 0b1010, vector_len);    // A2A4 = a12a24
  __ vshufpd(A12A14, A11A13, T2 /*A10A20*/, 0b1111, vector_len);    // A12A14 = a13a20
  __ vshufpd(A11A13,   A7A9, T1 /*A17A19*/, 0b1010, vector_len);    // A11A13 = a7a19
  __ vshufpd(A7A9, T2 /*A10A20*/, T0 /*A22A24*/, 0b0000, vector_len); // A7A9 = a10a22

  // Chi step - First row
  // ^=A0_  A1A3  A2A4
  //  ~A1_  A2A4  A3A0
  //  &A2_  A3A0  A4A1
  __ vshufpd(T0/*A3A0*/, A1A3,  A0_, 0b0101, vector_len);
  __ vshufpd(T1/*A4A1*/, A2A4, A1A3, 0b0101, vector_len);
  __ vpandn( T2,       A2A4, T0/*A3A0*/, vector_len);
  __ vpandn( T1, T0/*A3A0*/, T1/*A4A1*/, vector_len);
  __ vpandn( T0,       A1A3,       A2A4, vector_len);
  __ vpxor(A2A4, A2A4, T1, vector_len);
  __ vpxor(A1A3, A1A3, T2, vector_len);
  __ vpxor(A0_,   A0_, T0, vector_len);

  // Step mapping Iota as defined in section 3.2.5.
  // a0 ^= RC_CONSTANTS[ir];
  __ vpxor(A0_,   A0_, Address(round_consts, roundsLeft, Address::times_8), vector_len);

  // Chi step - Second&Fourth, Third&Fifth rows
  // ^= X5X15   X6X8   X7X9   X16X18  X17X19
  //  ~ X6X16   X7X9   X8X5   X17X19  X18X15
  //  & X7X17   X8X5   X9X6   X18X15  X19X16
  auto chi_row_pair = [=](XMMRegister X5X15, XMMRegister X6X8, XMMRegister X7X9,
        XMMRegister X16X18, XMMRegister X17X19){
    // X6X8 && X7X9
    __ vshufpd(T0/*X8X5*/, X6X8, X5X15, 0b0101, vector_len);
    __ vshufpd(T1/*X9X6*/, X7X9, X6X8,  0b0101, vector_len);
    __ vpandn( T2,       X7X9, T0/*X8X5*/, vector_len);
    __ vpandn( T1, T0/*X8X5*/, T1/*X9X6*/, vector_len);
    __ vshufpd(T0/*X6X16*/, X6X8, X16X18, 0b0000, vector_len);
    __ vpxor(X6X8, X6X8, T2, vector_len);
    __ vshufpd(T2/*X7X17*/, X7X9, X17X19, 0b0000, vector_len);
    __ vpxor(X7X9, X7X9, T1, vector_len);

    // X5X15
    __ vpandn( T2, T0/*X6X16*/, T2/*X7X17*/, vector_len);
    __ vshufpd(T0/*X18X15*/, X16X18, X5X15, 0b1111, vector_len);
    __ vpxor(X5X15, X5X15, T2, vector_len);

    // X16X18 && X17X19
    __ vshufpd(T1/*X19X16*/, X17X19, X16X18, 0b0101, vector_len);
    __ vpandn( T2,       X17X19, T0/*X18X15*/, vector_len);
    __ vpandn( T1, T0/*X18X15*/, T1/*X19X16*/, vector_len);
    __ vpxor(X16X18, X16X18, T2, vector_len);
    __ vpxor(X17X19, X17X19, T1, vector_len);
  };

  chi_row_pair(A5A15, A6A8, A7A9, A16A18, A17A19);
  chi_row_pair(A10A20, A11A13, A12A14, A21A23, A22A24);

  __ decrementl(roundsLeft, 4);
  __ jcc(Assembler::positive, rounds24_loop);

  if (multiBlock) {
    Label multi_done, block104, block136, block144, block168;
    __ movl(roundsLeft, 23*4);
    __ addptr(buf, block_size);
    __ addl(offset, block_size);
    __ cmpl(offset, limit);
    __ jcc(Assembler::greater, multi_done);

    auto buf_even_odd = [=](int row, XMMRegister X1X3, XMMRegister X2X4) {
      __ vmovdqu(T1, Address(buf, (row*5 + 1) * 8), vector_len); //b1b2
      __ vmovdqu(T2, Address(buf, (row*5 + 3) * 8), vector_len); //b3b4
      __ vshufpd(T0, T1, T2, 0b0000, vector_len);   // b1b3
      __ vshufpd(T1, T1, T2, 0b1111, vector_len);   // b2b4
      __ vpxor(X1X3, X1X3, T0, vector_len); // A1A3
      __ vpxor(X2X4, X2X4, T1, vector_len); // A2A4
    };
    // First Row
    __ vpxor(A0_, A0_, Address(buf, 0 * 8), vector_len);
    buf_even_odd(0, A1A3, A2A4);

    {
      __ cmpl(block_size, 72);
      __ jcc(Assembler::notEqual, block104);
      __ vmovdqu(T1, Address(buf, 5 * 8), vector_len); //b5b6  A5A15 A6A8 A7A9
      __ vmovdqu(T2, Address(buf, 7 * 8), vector_len); //b7b8
      __ vshufpd(T0, A5A15, A6A8, 0b0000, vector_len); //A5A6
      __ vpxor(T0, T0, T1, vector_len);       //A5A6
      __ vshufpd(T1, A7A9, A6A8, 0b1010, vector_len); //A7A8
      __ vpxor(T1, T1, T2, vector_len);       //A7A8
      __ vshufpd(A5A15, T0/*A5A6*/, A5A15,      0b1010, vector_len);
      __ vshufpd(A6A8,  T0/*A5A6*/, T1/*A7A8*/, 0b1111, vector_len);
      __ vshufpd(A7A9,  T1/*A7A8*/, A7A9,       0b1010, vector_len);
      __ jmp(rounds24_loop);
    }
    __ BIND(block104);
    {
      __ cmpl(block_size, 104);
      __ jcc(Assembler::notEqual, block136);
      __ movq(T0, Address(buf, 5 * 8));   //b5
      __ movq(T1, Address(buf, 10 * 8));  //b10
      __ vshufpd(T0, T0, T1, 0b0000, vector_len); //b5b10
      __ vshufpd(T1, A5A15, A10A20, 0b0000, vector_len); //A5A10
      __ vpxor(T0, T0, T1, vector_len); //A5A10
      __ vshufpd(A5A15, T0/*A5A10*/, A5A15, 0b1010, vector_len);
      __ vshufpd(A10A20, T0/*A5A10*/, A10A20, 0b1111, vector_len);

      __ vshufpd(T0, A11A13, A12A14, 0b0000, vector_len); //A11A12
      __ vpxor(T0, T0, Address(buf, 11 * 8), vector_len); //b11b12
      __ vshufpd(A11A13, T0/*A11A12*/, A11A13, 0b1010, vector_len);
      __ vshufpd(A12A14, T0/*A11A12*/, A12A14, 0b1111, vector_len);
      buf_even_odd(1, A6A8, A7A9);
      __ jmp(rounds24_loop);
    }
    __ BIND(block136);
    {
      __ cmpl(block_size, 136);
      __ jcc(Assembler::notEqual, block144);
      __ movq(T0, Address(buf, 5 * 8));   //b5
      __ movq(T1, Address(buf, 10 * 8));  //b10
      __ vmovdqu(T2, Address(buf, 15 * 8), vector_len); //b15b16
      __ vshufpd(T0, T0, T2, 0b0000, vector_len);       //b5b15
      __ vpxor(A5A15, A5A15, T0, vector_len);
      __ vshufpd(T0, T1, T2, 0b1010, vector_len); //b10b16
      __ vshufpd(T1, A10A20, A16A18, 0b0000, vector_len); //A10A16
      __ vpxor(T1, T1, T0, vector_len);
      __ vshufpd(A10A20, T1/*A10A16*/, A10A20, 0b1010, vector_len);
      __ vshufpd(A16A18, T1/*A10A16*/, A16A18, 0b1111, vector_len);
      buf_even_odd(1, A6A8, A7A9);
      buf_even_odd(2, A11A13, A12A14);
      __ jmp(rounds24_loop);
    }
    __ BIND(block144);
    {
      __ cmpl(block_size, 144);
      __ jcc(Assembler::notEqual, block168);
      __ movq(T0, Address(buf, 5 * 8));   //b5
      __ movq(T1, Address(buf, 15 * 8));  //b15
      __ vshufpd(T0, T0/*b5*/, T1/*b15*/, 0b0000, vector_len); //b5b15
      __ vpxor(A5A15, A5A15, T0, vector_len);
      buf_even_odd(1, A6A8, A7A9);
      buf_even_odd(2, A11A13, A12A14);
      __ movq(T0, Address(buf, 10 * 8));  //b10
      __ vpxor(A10A20, T0, A10A20, vector_len);

      __ vshufpd(T0, A16A18, A17A19, 0b0000, vector_len); //A16A17
      __ vpxor(T0, T0, Address(buf, 16 * 8), vector_len); //b16b17
      __ vshufpd(A16A18, T0/*A16A17*/, A16A18, 0b1010, vector_len);
      __ vshufpd(A17A19, T0/*A16A17*/, A17A19, 0b1111, vector_len);
      __ jmp(rounds24_loop);
    }
    __ BIND(block168);
    {
      __ movq(T0, Address(buf, 5 * 8));   //A5
      __ movq(T1, Address(buf, 15 * 8));  //A15
      __ vshufpd(T0, T0/*A5*/, T1/*A15*/, 0b0000, vector_len); //A5A15
      __ vpxor(A5A15, A5A15, T0, vector_len);
      buf_even_odd(1, A6A8, A7A9);
      buf_even_odd(2, A11A13, A12A14);
      buf_even_odd(3, A16A18, A17A19);
      __ movq(T0, Address(buf, 10 * 8));  //A10
      __ movq(T1, Address(buf, 20 * 8));  //A20
      __ vshufpd(T0, T0/*A10*/, T1/*A20*/, 0b0000, vector_len); //A10A20
      __ vpxor(A10A20, A10A20, T0, vector_len);
      __ jmp(rounds24_loop);
    }
    __ BIND(multi_done);
    __ movq(rax, offset); // return offset
  } else {
    __ xorq(rax, rax); // return 0
  }

  // Unshuffle
  auto extractState = [=](int disp, XMMRegister src) {
    int disp1 = disp;
    int disp2 = disp+10;
    __ pextrq(Address(state1, disp1 * 8), src, 0);
    __ pextrq(Address(state1, disp2 * 8), src, 1);
    if (parallelKeccak) {
      __ vextracti128(src, src, 1);
      __ pextrq(Address(state2, disp1 * 8), src, 0);
      __ pextrq(Address(state2, disp2 * 8), src, 1);
    }
  };
  auto storeState = [=](int disp, XMMRegister X1X3, XMMRegister X2X4){
    XMMRegister X1X2 = T0;
    XMMRegister X3X4 = T1;
    int disp1 = disp;
    int disp2 = disp+2;
    __ vpunpcklqdq(X1X2, X1X3, X2X4, Assembler::AVX_256bit);
    __ vpunpckhqdq(X3X4, X1X3, X2X4, Assembler::AVX_256bit);
    __ vmovdqu(Address(state1, disp1 * 8), X1X2, Assembler::AVX_128bit);
    __ vmovdqu(Address(state1, disp2 * 8), X3X4, Assembler::AVX_128bit);
    if (parallelKeccak) {
      __ vextracti128(Address(state2, disp1 * 8), X1X2, 1);
      __ vextracti128(Address(state2, disp2 * 8), X3X4, 1);
    }
  };

  __ pextrq(Address(state1, 0 * 8), A0_, 0);
  if (parallelKeccak) {
    __ vextracti128(A0_, A0_, 1);
    __ pextrq(Address(state2, 0 * 8), A0_, 0);
  }
  storeState(1, A1A3, A2A4);
  extractState(5, A5A15);
  storeState(6, A6A8, A7A9);
  extractState(10, A10A20);
  storeState(11, A11A13, A12A14);
  storeState(16, A16A18, A17A19);
  storeState(21, A21A23, A22A24);

  // Cleanup
  // Zero out zmm0-zmm15.
  __ vpxor(xmm0, xmm0, xmm0, vector_len);
  __ vmovdqa(Address(rsp, 0),        xmm0, vector_len);
  __ vmovdqa(Address(rsp, reg_size), xmm0, vector_len);
  __ vzeroall();

  __ movq(rsp, rbp);
  __ pop_ppx(rbp);
  if (!parallelKeccak) {
    __ pop_ppx(r12);
  #ifdef _WIN64
    __ pop_ppx(rdi);
  #endif
  }

  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ ret(0);

  // record the stub entry and end
  stubgen->store_archive_data(stub_id, start, __ pc());

  return start;
}

void StubGenerator::generate_sha3_stubs() {
  bool avx512Available = VM_Version::supports_evex() && VM_Version::supports_avx512vlbw();
  if (UseSHA3Intrinsics) {
    if (avx512Available) {
      StubRoutines::_sha3_implCompress =
        generate_sha3_implCompress_avx512(StubId::stubgen_sha3_implCompress_id, this, _masm);
      StubRoutines::_sha3_implCompressMB =
        generate_sha3_implCompress_avx512(StubId::stubgen_sha3_implCompressMB_id, this, _masm);
      StubRoutines::_double_keccak =
        generate_sha3_implCompress_avx512(StubId::stubgen_double_keccak_id, this, _masm);
      StubRoutines::_quad_keccak =
        generate_sha3_implCompress_avx512(StubId::stubgen_quad_keccak_id, this, _masm);
    } else {
      StubRoutines::_sha3_implCompress =
        generate_sha3_implCompress_avx2(StubId::stubgen_sha3_implCompress_id, this, _masm);
      StubRoutines::_double_keccak =
        generate_sha3_implCompress_avx2(StubId::stubgen_double_keccak_id, this, _masm);
      StubRoutines::_sha3_implCompressMB =
        generate_sha3_implCompress_avx2(StubId::stubgen_sha3_implCompressMB_id, this, _masm);
    }
  }
}

#undef __

#if INCLUDE_CDS
void StubGenerator::init_AOTAddressTable_sha3(GrowableArray<address>& external_addresses) {
#define ADD(addr) external_addresses.append((address)(addr));
  ADD(round_constsAddr());
  ADD(avx2_round_constsAddr());
  ADD(avx2_rotate_constsAddr());
#undef ADD
}
#endif // INCLUDE_CDS
