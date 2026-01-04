/*
* Copyright (c) 2019, 2025, Intel Corporation. All rights reserved.
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

#include "asm/assembler.inline.hpp"
#include "runtime/stubRoutines.hpp"
#include "macroAssembler_x86.hpp"
#include "stubGenerator_x86_64.hpp"

#define __ _masm->

ATTRIBUTE_ALIGNED(16) static const uint64_t GHASH_SHUFFLE_MASK[] = {
    0x0F0F0F0F0F0F0F0FUL, 0x0F0F0F0F0F0F0F0FUL,
};
static address ghash_shuffle_mask_addr() {
  return (address)GHASH_SHUFFLE_MASK;
}

// byte swap x86 long
ATTRIBUTE_ALIGNED(16) static const uint64_t GHASH_LONG_SWAP_MASK[] = {
    0x0F0E0D0C0B0A0908UL, 0x0706050403020100UL,
};
address StubGenerator::ghash_long_swap_mask_addr() {
  return (address)GHASH_LONG_SWAP_MASK;
}

// byte swap x86 byte array
ATTRIBUTE_ALIGNED(16) static const uint64_t GHASH_BYTE_SWAP_MASK[] = {
  0x08090A0B0C0D0E0FUL, 0x0001020304050607UL,
};
address StubGenerator::ghash_byte_swap_mask_addr() {
  return (address)GHASH_BYTE_SWAP_MASK;
}

// Polynomial x^128+x^127+x^126+x^121+1
ATTRIBUTE_ALIGNED(16) static const uint64_t GHASH_POLYNOMIAL[] = {
    0x0000000000000001ULL, 0xC200000000000000ULL,
    0x0000000000000001ULL, 0xC200000000000000ULL,
    0x0000000000000001ULL, 0xC200000000000000ULL,
    0x0000000000000001ULL, 0xC200000000000000ULL
};
address StubGenerator::ghash_polynomial_addr() {
  return (address)GHASH_POLYNOMIAL;
}


// GHASH intrinsic stubs

void StubGenerator::generate_ghash_stubs() {
  if (UseGHASHIntrinsics) {
    if (VM_Version::supports_avx()) {
      StubRoutines::_ghash_processBlocks = generate_avx_ghash_processBlocks();
    } else {
      StubRoutines::_ghash_processBlocks = generate_ghash_processBlocks();
    }
  }
}


// Single and multi-block ghash operations.
address StubGenerator::generate_ghash_processBlocks() {
  __ align(CodeEntryAlignment);
  Label L_ghash_loop, L_exit;
  StubId stub_id = StubId::stubgen_ghash_processBlocks_id;
  StubCodeMark mark(this, stub_id);
  address start = __ pc();

  const Register state        = c_rarg0;
  const Register subkeyH      = c_rarg1;
  const Register data         = c_rarg2;
  const Register blocks       = c_rarg3;

  const XMMRegister xmm_temp0 = xmm0;
  const XMMRegister xmm_temp1 = xmm1;
  const XMMRegister xmm_temp2 = xmm2;
  const XMMRegister xmm_temp3 = xmm3;
  const XMMRegister xmm_temp4 = xmm4;
  const XMMRegister xmm_temp5 = xmm5;
  const XMMRegister xmm_temp6 = xmm6;
  const XMMRegister xmm_temp7 = xmm7;
  const XMMRegister xmm_temp8 = xmm8;
  const XMMRegister xmm_temp9 = xmm9;
  const XMMRegister xmm_temp10 = xmm10;

  __ enter();

  __ push_ppx(rbx); // scratch

  __ movdqu(xmm_temp10, ExternalAddress(ghash_long_swap_mask_addr()), rbx /*rscratch*/);

  __ movdqu(xmm_temp0, Address(state, 0));
  __ pshufb(xmm_temp0, xmm_temp10);


  __ bind(L_ghash_loop);
  __ movdqu(xmm_temp2, Address(data, 0));
  __ pshufb(xmm_temp2, ExternalAddress(ghash_byte_swap_mask_addr()), rbx /*rscratch*/);

  __ movdqu(xmm_temp1, Address(subkeyH, 0));
  __ pshufb(xmm_temp1, xmm_temp10);

  __ pxor(xmm_temp0, xmm_temp2);

  //
  // Multiply with the hash key
  //
  __ movdqu(xmm_temp3, xmm_temp0);
  __ pclmulqdq(xmm_temp3, xmm_temp1, 0);      // xmm3 holds a0*b0
  __ movdqu(xmm_temp4, xmm_temp0);
  __ pclmulqdq(xmm_temp4, xmm_temp1, 16);     // xmm4 holds a0*b1

  __ movdqu(xmm_temp5, xmm_temp0);
  __ pclmulqdq(xmm_temp5, xmm_temp1, 1);      // xmm5 holds a1*b0
  __ movdqu(xmm_temp6, xmm_temp0);
  __ pclmulqdq(xmm_temp6, xmm_temp1, 17);     // xmm6 holds a1*b1

  __ pxor(xmm_temp4, xmm_temp5);      // xmm4 holds a0*b1 + a1*b0

  __ movdqu(xmm_temp5, xmm_temp4);    // move the contents of xmm4 to xmm5
  __ psrldq(xmm_temp4, 8);    // shift by xmm4 64 bits to the right
  __ pslldq(xmm_temp5, 8);    // shift by xmm5 64 bits to the left
  __ pxor(xmm_temp3, xmm_temp5);
  __ pxor(xmm_temp6, xmm_temp4);      // Register pair <xmm6:xmm3> holds the result
                                      // of the carry-less multiplication of
                                      // xmm0 by xmm1.

  // We shift the result of the multiplication by one bit position
  // to the left to cope for the fact that the bits are reversed.
  __ movdqu(xmm_temp7, xmm_temp3);
  __ movdqu(xmm_temp8, xmm_temp6);
  __ pslld(xmm_temp3, 1);
  __ pslld(xmm_temp6, 1);
  __ psrld(xmm_temp7, 31);
  __ psrld(xmm_temp8, 31);
  __ movdqu(xmm_temp9, xmm_temp7);
  __ pslldq(xmm_temp8, 4);
  __ pslldq(xmm_temp7, 4);
  __ psrldq(xmm_temp9, 12);
  __ por(xmm_temp3, xmm_temp7);
  __ por(xmm_temp6, xmm_temp8);
  __ por(xmm_temp6, xmm_temp9);

  //
  // First phase of the reduction
  //
  // Move xmm3 into xmm7, xmm8, xmm9 in order to perform the shifts
  // independently.
  __ movdqu(xmm_temp7, xmm_temp3);
  __ movdqu(xmm_temp8, xmm_temp3);
  __ movdqu(xmm_temp9, xmm_temp3);
  __ pslld(xmm_temp7, 31);    // packed right shift shifting << 31
  __ pslld(xmm_temp8, 30);    // packed right shift shifting << 30
  __ pslld(xmm_temp9, 25);    // packed right shift shifting << 25
  __ pxor(xmm_temp7, xmm_temp8);      // xor the shifted versions
  __ pxor(xmm_temp7, xmm_temp9);
  __ movdqu(xmm_temp8, xmm_temp7);
  __ pslldq(xmm_temp7, 12);
  __ psrldq(xmm_temp8, 4);
  __ pxor(xmm_temp3, xmm_temp7);      // first phase of the reduction complete

  //
  // Second phase of the reduction
  //
  // Make 3 copies of xmm3 in xmm2, xmm4, xmm5 for doing these
  // shift operations.
  __ movdqu(xmm_temp2, xmm_temp3);
  __ movdqu(xmm_temp4, xmm_temp3);
  __ movdqu(xmm_temp5, xmm_temp3);
  __ psrld(xmm_temp2, 1);     // packed left shifting >> 1
  __ psrld(xmm_temp4, 2);     // packed left shifting >> 2
  __ psrld(xmm_temp5, 7);     // packed left shifting >> 7
  __ pxor(xmm_temp2, xmm_temp4);      // xor the shifted versions
  __ pxor(xmm_temp2, xmm_temp5);
  __ pxor(xmm_temp2, xmm_temp8);
  __ pxor(xmm_temp3, xmm_temp2);
  __ pxor(xmm_temp6, xmm_temp3);      // the result is in xmm6

  __ decrement(blocks);
  __ jcc(Assembler::zero, L_exit);
  __ movdqu(xmm_temp0, xmm_temp6);
  __ addptr(data, 16);
  __ jmp(L_ghash_loop);

  __ bind(L_exit);
  __ pshufb(xmm_temp6, xmm_temp10);          // Byte swap 16-byte result
  __ movdqu(Address(state, 0), xmm_temp6);   // store the result

  __ pop_ppx(rbx);

  __ leave();
  __ ret(0);

  return start;
}


// Ghash single and multi block operations using AVX instructions
address StubGenerator::generate_avx_ghash_processBlocks() {
  __ align(CodeEntryAlignment);

  StubId stub_id = StubId::stubgen_ghash_processBlocks_id;
  StubCodeMark mark(this, stub_id);
  address start = __ pc();

  // arguments
  const Register state = c_rarg0;
  const Register htbl = c_rarg1;
  const Register data = c_rarg2;
  const Register blocks = c_rarg3;
  __ enter();
  __ push_ppx(rbx);

  avx_ghash(state, htbl, data, blocks);

  __ pop_ppx(rbx);
  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ ret(0);

  return start;
}


// Multiblock and single block GHASH computation using Shift XOR reduction technique
void StubGenerator::avx_ghash(Register input_state, Register htbl,
                              Register input_data, Register blocks) {
  // temporary variables to hold input data and input state
  const XMMRegister data = xmm1;
  const XMMRegister state = xmm0;
  // temporary variables to hold intermediate results
  const XMMRegister tmp0 = xmm3;
  const XMMRegister tmp1 = xmm4;
  const XMMRegister tmp2 = xmm5;
  const XMMRegister tmp3 = xmm6;
  // temporary variables to hold byte and long swap masks
  const XMMRegister bswap_mask = xmm2;
  const XMMRegister lswap_mask = xmm14;

  Label GENERATE_HTBL_1_BLK, GENERATE_HTBL_8_BLKS, BEGIN_PROCESS, GFMUL, BLOCK8_REDUCTION,
        ONE_BLK_INIT, PROCESS_1_BLOCK, PROCESS_8_BLOCKS, SAVE_STATE, EXIT_GHASH;

  __ testptr(blocks, blocks);
  __ jcc(Assembler::zero, EXIT_GHASH);

  // Check if Hashtable (1*16) has been already generated
  // For anything less than 8 blocks, we generate only the first power of H.
  __ movdqu(tmp2, Address(htbl, 1 * 16));
  __ ptest(tmp2, tmp2);
  __ jcc(Assembler::notZero, BEGIN_PROCESS);
  __ call(GENERATE_HTBL_1_BLK, relocInfo::none);

  // Shuffle the input state
  __ bind(BEGIN_PROCESS);
  __ movdqu(lswap_mask, ExternalAddress(ghash_long_swap_mask_addr()), rbx /*rscratch*/);
  __ movdqu(state, Address(input_state, 0));
  __ vpshufb(state, state, lswap_mask, Assembler::AVX_128bit);

  __ cmpl(blocks, 8);
  __ jcc(Assembler::below, ONE_BLK_INIT);
  // If we have 8 blocks or more data, then generate remaining powers of H
  __ movdqu(tmp2, Address(htbl, 8 * 16));
  __ ptest(tmp2, tmp2);
  __ jcc(Assembler::notZero, PROCESS_8_BLOCKS);
  __ call(GENERATE_HTBL_8_BLKS, relocInfo::none);

  //Do 8 multiplies followed by a reduction processing 8 blocks of data at a time
  //Each block = 16 bytes.
  __ bind(PROCESS_8_BLOCKS);
  __ subl(blocks, 8);
  __ movdqu(bswap_mask, ExternalAddress(ghash_byte_swap_mask_addr()), rbx /*rscratch*/);
  __ movdqu(data, Address(input_data, 16 * 7));
  __ vpshufb(data, data, bswap_mask, Assembler::AVX_128bit);
  //Loading 1*16 as calculated powers of H required starts at that location.
  __ movdqu(xmm15, Address(htbl, 1 * 16));
  //Perform carryless multiplication of (H*2, data block #7)
  __ vpclmulhqlqdq(tmp2, data, xmm15);//a0 * b1
  __ vpclmulldq(tmp0, data, xmm15);//a0 * b0
  __ vpclmulhdq(tmp1, data, xmm15);//a1 * b1
  __ vpclmullqhqdq(tmp3, data, xmm15);//a1* b0
  __ vpxor(tmp2, tmp2, tmp3, Assembler::AVX_128bit);// (a0 * b1) + (a1 * b0)

  __ movdqu(data, Address(input_data, 16 * 6));
  __ vpshufb(data, data, bswap_mask, Assembler::AVX_128bit);
  // Perform carryless multiplication of (H^2 * 2, data block #6)
  schoolbookAAD(2, htbl, data, tmp0, tmp1, tmp2, tmp3);

  __ movdqu(data, Address(input_data, 16 * 5));
  __ vpshufb(data, data, bswap_mask, Assembler::AVX_128bit);
  // Perform carryless multiplication of (H^3 * 2, data block #5)
  schoolbookAAD(3, htbl, data, tmp0, tmp1, tmp2, tmp3);
  __ movdqu(data, Address(input_data, 16 * 4));
  __ vpshufb(data, data, bswap_mask, Assembler::AVX_128bit);
  // Perform carryless multiplication of (H^4 * 2, data block #4)
  schoolbookAAD(4, htbl, data, tmp0, tmp1, tmp2, tmp3);
  __ movdqu(data, Address(input_data, 16 * 3));
  __ vpshufb(data, data, bswap_mask, Assembler::AVX_128bit);
  // Perform carryless multiplication of (H^5 * 2, data block #3)
  schoolbookAAD(5, htbl, data, tmp0, tmp1, tmp2, tmp3);
  __ movdqu(data, Address(input_data, 16 * 2));
  __ vpshufb(data, data, bswap_mask, Assembler::AVX_128bit);
  // Perform carryless multiplication of (H^6 * 2, data block #2)
  schoolbookAAD(6, htbl, data, tmp0, tmp1, tmp2, tmp3);
  __ movdqu(data, Address(input_data, 16 * 1));
  __ vpshufb(data, data, bswap_mask, Assembler::AVX_128bit);
  // Perform carryless multiplication of (H^7 * 2, data block #1)
  schoolbookAAD(7, htbl, data, tmp0, tmp1, tmp2, tmp3);
  __ movdqu(data, Address(input_data, 16 * 0));
  // xor data block#0 with input state before performing carry-less multiplication
  __ vpshufb(data, data, bswap_mask, Assembler::AVX_128bit);
  __ vpxor(data, data, state, Assembler::AVX_128bit);
  // Perform carryless multiplication of (H^8 * 2, data block #0)
  schoolbookAAD(8, htbl, data, tmp0, tmp1, tmp2, tmp3);
  __ vpslldq(tmp3, tmp2, 8, Assembler::AVX_128bit);
  __ vpsrldq(tmp2, tmp2, 8, Assembler::AVX_128bit);
  __ vpxor(tmp0, tmp0, tmp3, Assembler::AVX_128bit);// tmp0, tmp1 contains aggregated results of
  __ vpxor(tmp1, tmp1, tmp2, Assembler::AVX_128bit);// the multiplication operation

  // we have the 2 128-bit partially accumulated multiplication results in tmp0:tmp1
  // with higher 128-bit in tmp1 and lower 128-bit in corresponding tmp0
  // Follows the reduction technique mentioned in
  // Shift-XOR reduction described in Gueron-Kounavis May 2010
  __ bind(BLOCK8_REDUCTION);
  // First Phase of the reduction
  __ vpslld(xmm8, tmp0, 31, Assembler::AVX_128bit); // packed right shifting << 31
  __ vpslld(xmm9, tmp0, 30, Assembler::AVX_128bit); // packed right shifting << 30
  __ vpslld(xmm10, tmp0, 25, Assembler::AVX_128bit); // packed right shifting << 25
  // xor the shifted versions
  __ vpxor(xmm8, xmm8, xmm10, Assembler::AVX_128bit);
  __ vpxor(xmm8, xmm8, xmm9, Assembler::AVX_128bit);

  __ vpslldq(xmm9, xmm8, 12, Assembler::AVX_128bit);
  __ vpsrldq(xmm8, xmm8, 4, Assembler::AVX_128bit);

  __ vpxor(tmp0, tmp0, xmm9, Assembler::AVX_128bit); // first phase of reduction is complete
  // second phase of the reduction
  __ vpsrld(xmm9, tmp0, 1, Assembler::AVX_128bit); // packed left shifting >> 1
  __ vpsrld(xmm10, tmp0, 2, Assembler::AVX_128bit); // packed left shifting >> 2
  __ vpsrld(tmp2, tmp0, 7, Assembler::AVX_128bit); // packed left shifting >> 7
  // xor the shifted versions
  __ vpxor(xmm9, xmm9, xmm10, Assembler::AVX_128bit);
  __ vpxor(xmm9, xmm9, tmp2, Assembler::AVX_128bit);
  __ vpxor(xmm9, xmm9, xmm8, Assembler::AVX_128bit);
  __ vpxor(tmp0, xmm9, tmp0, Assembler::AVX_128bit);
  // Final result is in state
  __ vpxor(state, tmp0, tmp1, Assembler::AVX_128bit);

  __ lea(input_data, Address(input_data, 16 * 8));
  __ cmpl(blocks, 8);
  __ jcc(Assembler::below, ONE_BLK_INIT);
  __ jmp(PROCESS_8_BLOCKS);

  // Since this is one block operation we will only use H * 2 i.e. the first power of H
  __ bind(ONE_BLK_INIT);
  __ movdqu(tmp0, Address(htbl, 1 * 16));
  __ movdqu(bswap_mask, ExternalAddress(ghash_byte_swap_mask_addr()), rbx /*rscratch*/);

  //Do one (128 bit x 128 bit) carry-less multiplication at a time followed by a reduction.
  __ bind(PROCESS_1_BLOCK);
  __ cmpl(blocks, 0);
  __ jcc(Assembler::equal, SAVE_STATE);
  __ subl(blocks, 1);
  __ movdqu(data, Address(input_data, 0));
  __ vpshufb(data, data, bswap_mask, Assembler::AVX_128bit);
  __ vpxor(state, state, data, Assembler::AVX_128bit);
  // gfmul(H*2, state)
  __ call(GFMUL, relocInfo::none);
  __ addptr(input_data, 16);
  __ jmp(PROCESS_1_BLOCK);

  __ bind(SAVE_STATE);
  __ vpshufb(state, state, lswap_mask, Assembler::AVX_128bit);
  __ movdqu(Address(input_state, 0), state);
  __ jmp(EXIT_GHASH);

  __ bind(GFMUL);
  gfmul(tmp0, state);

  __ bind(GENERATE_HTBL_1_BLK);
  generateHtbl_one_block(htbl, rbx /*rscratch*/);

  __ bind(GENERATE_HTBL_8_BLKS);
  generateHtbl_eight_blocks(htbl);

  __ bind(EXIT_GHASH);
  // zero out xmm registers used for Htbl storage
  __ vpxor(xmm0, xmm0, xmm0, Assembler::AVX_128bit);
  __ vpxor(xmm1, xmm1, xmm1, Assembler::AVX_128bit);
  __ vpxor(xmm3, xmm3, xmm3, Assembler::AVX_128bit);
  __ vpxor(xmm15, xmm15, xmm15, Assembler::AVX_128bit);
}


// Multiply two 128 bit numbers resulting in a 256 bit value
// Result of the multiplication followed by reduction stored in state
void StubGenerator::gfmul(XMMRegister tmp0, XMMRegister state) {
  const XMMRegister tmp1 = xmm4;
  const XMMRegister tmp2 = xmm5;
  const XMMRegister tmp3 = xmm6;
  const XMMRegister tmp4 = xmm7;

  __ vpclmulldq(tmp1, state, tmp0); //0x00  (a0 * b0)
  __ vpclmulhdq(tmp4, state, tmp0);//0x11 (a1 * b1)
  __ vpclmullqhqdq(tmp2, state, tmp0);//0x10 (a1 * b0)
  __ vpclmulhqlqdq(tmp3, state, tmp0); //0x01 (a0 * b1)

  __ vpxor(tmp2, tmp2, tmp3, Assembler::AVX_128bit); // (a0 * b1) + (a1 * b0)

  __ vpslldq(tmp3, tmp2, 8, Assembler::AVX_128bit);
  __ vpsrldq(tmp2, tmp2, 8, Assembler::AVX_128bit);
  __ vpxor(tmp1, tmp1, tmp3, Assembler::AVX_128bit); // tmp1 and tmp4 hold the result
  __ vpxor(tmp4, tmp4, tmp2, Assembler::AVX_128bit); // of carryless multiplication
  // Follows the reduction technique mentioned in
  // Shift-XOR reduction described in Gueron-Kounavis May 2010
  // First phase of reduction
  //
  __ vpslld(xmm8, tmp1, 31, Assembler::AVX_128bit); // packed right shift shifting << 31
  __ vpslld(xmm9, tmp1, 30, Assembler::AVX_128bit); // packed right shift shifting << 30
  __ vpslld(xmm10, tmp1, 25, Assembler::AVX_128bit);// packed right shift shifting << 25
  // xor the shifted versions
  __ vpxor(xmm8, xmm8, xmm9, Assembler::AVX_128bit);
  __ vpxor(xmm8, xmm8, xmm10, Assembler::AVX_128bit);
  __ vpslldq(xmm9, xmm8, 12, Assembler::AVX_128bit);
  __ vpsrldq(xmm8, xmm8, 4, Assembler::AVX_128bit);
  __ vpxor(tmp1, tmp1, xmm9, Assembler::AVX_128bit);// first phase of the reduction complete
  //
  // Second phase of the reduction
  //
  __ vpsrld(xmm9, tmp1, 1, Assembler::AVX_128bit);// packed left shifting >> 1
  __ vpsrld(xmm10, tmp1, 2, Assembler::AVX_128bit);// packed left shifting >> 2
  __ vpsrld(xmm11, tmp1, 7, Assembler::AVX_128bit);// packed left shifting >> 7
  __ vpxor(xmm9, xmm9, xmm10, Assembler::AVX_128bit);// xor the shifted versions
  __ vpxor(xmm9, xmm9, xmm11, Assembler::AVX_128bit);
  __ vpxor(xmm9, xmm9, xmm8, Assembler::AVX_128bit);
  __ vpxor(tmp1, tmp1, xmm9, Assembler::AVX_128bit);
  __ vpxor(state, tmp4, tmp1, Assembler::AVX_128bit);// the result is in state
  __ ret(0);
}


// Multiply 128 x 128 bits, using 4 pclmulqdq operations
void StubGenerator::schoolbookAAD(int i, Register htbl, XMMRegister data,
                                  XMMRegister tmp0, XMMRegister tmp1,
                                  XMMRegister tmp2, XMMRegister tmp3) {
  __ movdqu(xmm15, Address(htbl, i * 16));
  __ vpclmulhqlqdq(tmp3, data, xmm15); // 0x01
  __ vpxor(tmp2, tmp2, tmp3, Assembler::AVX_128bit);
  __ vpclmulldq(tmp3, data, xmm15); // 0x00
  __ vpxor(tmp0, tmp0, tmp3, Assembler::AVX_128bit);
  __ vpclmulhdq(tmp3, data, xmm15); // 0x11
  __ vpxor(tmp1, tmp1, tmp3, Assembler::AVX_128bit);
  __ vpclmullqhqdq(tmp3, data, xmm15); // 0x10
  __ vpxor(tmp2, tmp2, tmp3, Assembler::AVX_128bit);
}


// This method takes the subkey after expansion as input and generates 1 * 16 power of subkey H.
// The power of H is used in reduction process for one block ghash
void StubGenerator::generateHtbl_one_block(Register htbl, Register rscratch) {
  const XMMRegister t = xmm13;

  // load the original subkey hash
  __ movdqu(t, Address(htbl, 0));
  // shuffle using long swap mask
  __ movdqu(xmm10, ExternalAddress(ghash_long_swap_mask_addr()), rscratch);
  __ vpshufb(t, t, xmm10, Assembler::AVX_128bit);

  // Compute H' = GFMUL(H, 2)
  __ vpsrld(xmm3, t, 7, Assembler::AVX_128bit);
  __ movdqu(xmm4, ExternalAddress(ghash_shuffle_mask_addr()), rscratch);
  __ vpshufb(xmm3, xmm3, xmm4, Assembler::AVX_128bit);
  __ movl(rax, 0xff00);
  __ movdl(xmm4, rax);
  __ vpshufb(xmm4, xmm4, xmm3, Assembler::AVX_128bit);
  __ movdqu(xmm5, ExternalAddress(ghash_polynomial_addr()), rscratch);
  __ vpand(xmm5, xmm5, xmm4, Assembler::AVX_128bit);
  __ vpsrld(xmm3, t, 31, Assembler::AVX_128bit);
  __ vpslld(xmm4, t, 1, Assembler::AVX_128bit);
  __ vpslldq(xmm3, xmm3, 4, Assembler::AVX_128bit);
  __ vpxor(t, xmm4, xmm3, Assembler::AVX_128bit);// t holds p(x) <<1 or H * 2

  //Adding p(x)<<1 to xmm5 which holds the reduction polynomial
  __ vpxor(t, t, xmm5, Assembler::AVX_128bit);
  __ movdqu(Address(htbl, 1 * 16), t); // H * 2

  __ ret(0);
}


// This method takes the subkey after expansion as input and generates the remaining powers of subkey H.
// The power of H is used in reduction process for eight block ghash
void StubGenerator::generateHtbl_eight_blocks(Register htbl) {
  const XMMRegister t = xmm13;
  const XMMRegister tmp0 = xmm1;
  Label GFMUL;

  __ movdqu(t, Address(htbl, 1 * 16));
  __ movdqu(tmp0, t);

  // tmp0 and t hold H. Now we compute powers of H by using GFMUL(H, H)
  __ call(GFMUL, relocInfo::none);
  __ movdqu(Address(htbl, 2 * 16), t); //H ^ 2 * 2
  __ call(GFMUL, relocInfo::none);
  __ movdqu(Address(htbl, 3 * 16), t); //H ^ 3 * 2
  __ call(GFMUL, relocInfo::none);
  __ movdqu(Address(htbl, 4 * 16), t); //H ^ 4 * 2
  __ call(GFMUL, relocInfo::none);
  __ movdqu(Address(htbl, 5 * 16), t); //H ^ 5 * 2
  __ call(GFMUL, relocInfo::none);
  __ movdqu(Address(htbl, 6 * 16), t); //H ^ 6 * 2
  __ call(GFMUL, relocInfo::none);
  __ movdqu(Address(htbl, 7 * 16), t); //H ^ 7 * 2
  __ call(GFMUL, relocInfo::none);
  __ movdqu(Address(htbl, 8 * 16), t); //H ^ 8 * 2
  __ ret(0);

  __ bind(GFMUL);
  gfmul(tmp0, t);
}

#undef __
