/*
* Copyright (c) 2019, Intel Corporation.
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
#include "runtime/stubRoutines.hpp"
#include "macroAssembler_x86.hpp"

#ifdef _LP64

void MacroAssembler::roundEnc(XMMRegister key, int rnum) {
    for (int xmm_reg_no = 0; xmm_reg_no <=rnum; xmm_reg_no++) {
      vaesenc(as_XMMRegister(xmm_reg_no), as_XMMRegister(xmm_reg_no), key, Assembler::AVX_512bit);
    }
}

void MacroAssembler::lastroundEnc(XMMRegister key, int rnum) {
    for (int xmm_reg_no = 0; xmm_reg_no <=rnum; xmm_reg_no++) {
      vaesenclast(as_XMMRegister(xmm_reg_no), as_XMMRegister(xmm_reg_no), key, Assembler::AVX_512bit);
    }
}

void MacroAssembler::roundDec(XMMRegister key, int rnum) {
    for (int xmm_reg_no = 0; xmm_reg_no <=rnum; xmm_reg_no++) {
      vaesdec(as_XMMRegister(xmm_reg_no), as_XMMRegister(xmm_reg_no), key, Assembler::AVX_512bit);
    }
}

void MacroAssembler::lastroundDec(XMMRegister key, int rnum) {
    for (int xmm_reg_no = 0; xmm_reg_no <=rnum; xmm_reg_no++) {
      vaesdeclast(as_XMMRegister(xmm_reg_no), as_XMMRegister(xmm_reg_no), key, Assembler::AVX_512bit);
    }
}

// Load key and shuffle operation
void MacroAssembler::ev_load_key(XMMRegister xmmdst, Register key, int offset, XMMRegister xmm_shuf_mask=NULL) {
    movdqu(xmmdst, Address(key, offset));
    if (xmm_shuf_mask != NULL) {
        pshufb(xmmdst, xmm_shuf_mask);
    } else {
       pshufb(xmmdst, ExternalAddress(StubRoutines::x86::key_shuffle_mask_addr()));
    }
   evshufi64x2(xmmdst, xmmdst, xmmdst, 0x0, Assembler::AVX_512bit);
}

// AES-ECB Encrypt Operation
void MacroAssembler::aesecb_encrypt(Register src_addr, Register dest_addr, Register key, Register len) {

    const Register pos = rax;
    const Register rounds = r12;

    Label NO_PARTS, LOOP, Loop_start, LOOP2, AES192, END_LOOP, AES256, REMAINDER, LAST2, END, KEY_192, KEY_256, EXIT;
    push(r13);
    push(r12);

    // For EVEX with VL and BW, provide a standard mask, VL = 128 will guide the merge
    // context for the registers used, where all instructions below are using 128-bit mode
    // On EVEX without VL and BW, these instructions will all be AVX.
    if (VM_Version::supports_avx512vlbw()) {
       movl(rax, 0xffff);
       kmovql(k1, rax);
    }
    push(len); // Save
    push(rbx);

    vzeroupper();

    xorptr(pos, pos);

    // Calculate number of rounds based on key length(128, 192, 256):44 for 10-rounds, 52 for 12-rounds, 60 for 14-rounds
    movl(rounds, Address(key, arrayOopDesc::length_offset_in_bytes() - arrayOopDesc::base_offset_in_bytes(T_INT)));

    // Load Key shuf mask
    const XMMRegister xmm_key_shuf_mask = xmm31;  // used temporarily to swap key bytes up front
    movdqu(xmm_key_shuf_mask, ExternalAddress(StubRoutines::x86::key_shuffle_mask_addr()));

    // Load and shuffle key based on number of rounds
    ev_load_key(xmm8, key, 0 * 16, xmm_key_shuf_mask);
    ev_load_key(xmm9, key, 1 * 16, xmm_key_shuf_mask);
    ev_load_key(xmm10, key, 2 * 16, xmm_key_shuf_mask);
    ev_load_key(xmm23, key, 3 * 16, xmm_key_shuf_mask);
    ev_load_key(xmm12, key, 4 * 16, xmm_key_shuf_mask);
    ev_load_key(xmm13, key, 5 * 16, xmm_key_shuf_mask);
    ev_load_key(xmm14, key, 6 * 16, xmm_key_shuf_mask);
    ev_load_key(xmm15, key, 7 * 16, xmm_key_shuf_mask);
    ev_load_key(xmm16, key, 8 * 16, xmm_key_shuf_mask);
    ev_load_key(xmm17, key, 9 * 16, xmm_key_shuf_mask);
    ev_load_key(xmm24, key, 10 * 16, xmm_key_shuf_mask);
    cmpl(rounds, 52);
    jcc(Assembler::greaterEqual, KEY_192);
    jmp(Loop_start);

    bind(KEY_192);
    ev_load_key(xmm19, key, 11 * 16, xmm_key_shuf_mask);
    ev_load_key(xmm20, key, 12 * 16, xmm_key_shuf_mask);
    cmpl(rounds, 60);
    jcc(Assembler::equal, KEY_256);
    jmp(Loop_start);

    bind(KEY_256);
    ev_load_key(xmm21, key, 13 * 16, xmm_key_shuf_mask);
    ev_load_key(xmm22, key, 14 * 16, xmm_key_shuf_mask);

    bind(Loop_start);
    movq(rbx, len);
    // Divide length by 16 to convert it to number of blocks
    shrq(len, 4);
    shlq(rbx, 60);
    jcc(Assembler::equal, NO_PARTS);
    addq(len, 1);
    // Check if number of blocks is greater than or equal to 32
    // If true, 512 bytes are processed at a time (code marked by label LOOP)
    // If not, 16 bytes are processed (code marked by REMAINDER label)
    bind(NO_PARTS);
    movq(rbx, len);
    shrq(len, 5);
    jcc(Assembler::equal, REMAINDER);
    movl(r13, len);
    // Compute number of blocks that will be processed 512 bytes at a time
    // Subtract this from the total number of blocks which will then be processed by REMAINDER loop
    shlq(r13, 5);
    subq(rbx, r13);
    //Begin processing 512 bytes
    bind(LOOP);
    // Move 64 bytes of PT data into a zmm register, as a result 512 bytes of PT loaded in zmm0-7
    evmovdquq(xmm0, Address(src_addr, pos, Address::times_1, 0 * 64), Assembler::AVX_512bit);
    evmovdquq(xmm1, Address(src_addr, pos, Address::times_1, 1 * 64), Assembler::AVX_512bit);
    evmovdquq(xmm2, Address(src_addr, pos, Address::times_1, 2 * 64), Assembler::AVX_512bit);
    evmovdquq(xmm3, Address(src_addr, pos, Address::times_1, 3 * 64), Assembler::AVX_512bit);
    evmovdquq(xmm4, Address(src_addr, pos, Address::times_1, 4 * 64), Assembler::AVX_512bit);
    evmovdquq(xmm5, Address(src_addr, pos, Address::times_1, 5 * 64), Assembler::AVX_512bit);
    evmovdquq(xmm6, Address(src_addr, pos, Address::times_1, 6 * 64), Assembler::AVX_512bit);
    evmovdquq(xmm7, Address(src_addr, pos, Address::times_1, 7 * 64), Assembler::AVX_512bit);
    // Xor with the first round key
    evpxorq(xmm0, xmm0, xmm8, Assembler::AVX_512bit);
    evpxorq(xmm1, xmm1, xmm8, Assembler::AVX_512bit);
    evpxorq(xmm2, xmm2, xmm8, Assembler::AVX_512bit);
    evpxorq(xmm3, xmm3, xmm8, Assembler::AVX_512bit);
    evpxorq(xmm4, xmm4, xmm8, Assembler::AVX_512bit);
    evpxorq(xmm5, xmm5, xmm8, Assembler::AVX_512bit);
    evpxorq(xmm6, xmm6, xmm8, Assembler::AVX_512bit);
    evpxorq(xmm7, xmm7, xmm8, Assembler::AVX_512bit);
    // 9 Aes encode round operations
    roundEnc(xmm9,  7);
    roundEnc(xmm10, 7);
    roundEnc(xmm23, 7);
    roundEnc(xmm12, 7);
    roundEnc(xmm13, 7);
    roundEnc(xmm14, 7);
    roundEnc(xmm15, 7);
    roundEnc(xmm16, 7);
    roundEnc(xmm17, 7);
    cmpl(rounds, 52);
    jcc(Assembler::aboveEqual, AES192);
    // Aesenclast round operation for keysize = 128
    lastroundEnc(xmm24, 7);
    jmp(END_LOOP);
    //Additional 2 rounds of Aesenc operation for keysize = 192
    bind(AES192);
    roundEnc(xmm24, 7);
    roundEnc(xmm19, 7);
    cmpl(rounds, 60);
    jcc(Assembler::aboveEqual, AES256);
    // Aesenclast round for keysize = 192
    lastroundEnc(xmm20, 7);
    jmp(END_LOOP);
    // 2 rounds of Aesenc operation and Aesenclast for keysize = 256
    bind(AES256);
    roundEnc(xmm20, 7);
    roundEnc(xmm21, 7);
    lastroundEnc(xmm22, 7);

    bind(END_LOOP);
    // Move 512 bytes of CT to destination
    evmovdquq(Address(dest_addr, pos, Address::times_1, 0 * 64), xmm0, Assembler::AVX_512bit);
    evmovdquq(Address(dest_addr, pos, Address::times_1, 1 * 64), xmm1, Assembler::AVX_512bit);
    evmovdquq(Address(dest_addr, pos, Address::times_1, 2 * 64), xmm2, Assembler::AVX_512bit);
    evmovdquq(Address(dest_addr, pos, Address::times_1, 3 * 64), xmm3, Assembler::AVX_512bit);
    evmovdquq(Address(dest_addr, pos, Address::times_1, 4 * 64), xmm4, Assembler::AVX_512bit);
    evmovdquq(Address(dest_addr, pos, Address::times_1, 5 * 64), xmm5, Assembler::AVX_512bit);
    evmovdquq(Address(dest_addr, pos, Address::times_1, 6 * 64), xmm6, Assembler::AVX_512bit);
    evmovdquq(Address(dest_addr, pos, Address::times_1, 7 * 64), xmm7, Assembler::AVX_512bit);

    addq(pos, 512);
    decq(len);
    jcc(Assembler::notEqual, LOOP);

    bind(REMAINDER);
    vzeroupper();
    cmpq(rbx, 0);
    jcc(Assembler::equal, END);
    // Process 16 bytes at a time
    bind(LOOP2);
    movdqu(xmm1, Address(src_addr, pos, Address::times_1, 0));
    vpxor(xmm1, xmm1, xmm8, Assembler::AVX_128bit);
    // xmm2 contains shuffled key for Aesenclast operation.
    vmovdqu(xmm2, xmm24);

    vaesenc(xmm1, xmm1, xmm9, Assembler::AVX_128bit);
    vaesenc(xmm1, xmm1, xmm10, Assembler::AVX_128bit);
    vaesenc(xmm1, xmm1, xmm23, Assembler::AVX_128bit);
    vaesenc(xmm1, xmm1, xmm12, Assembler::AVX_128bit);
    vaesenc(xmm1, xmm1, xmm13, Assembler::AVX_128bit);
    vaesenc(xmm1, xmm1, xmm14, Assembler::AVX_128bit);
    vaesenc(xmm1, xmm1, xmm15, Assembler::AVX_128bit);
    vaesenc(xmm1, xmm1, xmm16, Assembler::AVX_128bit);
    vaesenc(xmm1, xmm1, xmm17, Assembler::AVX_128bit);

    cmpl(rounds, 52);
    jcc(Assembler::below, LAST2);
    vmovdqu(xmm2, xmm20);
    vaesenc(xmm1, xmm1, xmm24, Assembler::AVX_128bit);
    vaesenc(xmm1, xmm1, xmm19, Assembler::AVX_128bit);
    cmpl(rounds, 60);
    jcc(Assembler::below, LAST2);
    vmovdqu(xmm2, xmm22);
    vaesenc(xmm1, xmm1, xmm20, Assembler::AVX_128bit);
    vaesenc(xmm1, xmm1, xmm21, Assembler::AVX_128bit);

    bind(LAST2);
    // Aesenclast round
    vaesenclast(xmm1, xmm1, xmm2, Assembler::AVX_128bit);
    // Write 16 bytes of CT to destination
    movdqu(Address(dest_addr, pos, Address::times_1, 0), xmm1);
    addq(pos, 16);
    decq(rbx);
    jcc(Assembler::notEqual, LOOP2);

    bind(END);
    // Zero out the round keys
    evpxorq(xmm8, xmm8, xmm8, Assembler::AVX_512bit);
    evpxorq(xmm9, xmm9, xmm9, Assembler::AVX_512bit);
    evpxorq(xmm10, xmm10, xmm10, Assembler::AVX_512bit);
    evpxorq(xmm23, xmm23, xmm23, Assembler::AVX_512bit);
    evpxorq(xmm12, xmm12, xmm12, Assembler::AVX_512bit);
    evpxorq(xmm13, xmm13, xmm13, Assembler::AVX_512bit);
    evpxorq(xmm14, xmm14, xmm14, Assembler::AVX_512bit);
    evpxorq(xmm15, xmm15, xmm15, Assembler::AVX_512bit);
    evpxorq(xmm16, xmm16, xmm16, Assembler::AVX_512bit);
    evpxorq(xmm17, xmm17, xmm17, Assembler::AVX_512bit);
    evpxorq(xmm24, xmm24, xmm24, Assembler::AVX_512bit);
    cmpl(rounds, 44);
    jcc(Assembler::belowEqual, EXIT);
    evpxorq(xmm19, xmm19, xmm19, Assembler::AVX_512bit);
    evpxorq(xmm20, xmm20, xmm20, Assembler::AVX_512bit);
    cmpl(rounds, 52);
    jcc(Assembler::belowEqual, EXIT);
    evpxorq(xmm21, xmm21, xmm21, Assembler::AVX_512bit);
    evpxorq(xmm22, xmm22, xmm22, Assembler::AVX_512bit);
    bind(EXIT);
    pop(rbx);
    pop(rax); // return length
    pop(r12);
    pop(r13);
}

// AES-ECB Decrypt Operation
void MacroAssembler::aesecb_decrypt(Register src_addr, Register dest_addr, Register key, Register len)  {

    Label NO_PARTS, LOOP, Loop_start, LOOP2, AES192, END_LOOP, AES256, REMAINDER, LAST2, END, KEY_192, KEY_256, EXIT;
    const Register pos = rax;
    const Register rounds = r12;
    push(r13);
    push(r12);

    // For EVEX with VL and BW, provide a standard mask, VL = 128 will guide the merge
    // context for the registers used, where all instructions below are using 128-bit mode
    // On EVEX without VL and BW, these instructions will all be AVX.
    if (VM_Version::supports_avx512vlbw()) {
       movl(rax, 0xffff);
       kmovql(k1, rax);
    }

    push(len); // Save
    push(rbx);

    vzeroupper();

    xorptr(pos, pos);
    // Calculate number of rounds i.e. based on key length(128, 192, 256):44 for 10-rounds, 52 for 12-rounds, 60 for 14-rounds
    movl(rounds, Address(key, arrayOopDesc::length_offset_in_bytes() - arrayOopDesc::base_offset_in_bytes(T_INT)));

    // Load Key shuf mask
    const XMMRegister xmm_key_shuf_mask = xmm31;  // used temporarily to swap key bytes up front
    movdqu(xmm_key_shuf_mask, ExternalAddress(StubRoutines::x86::key_shuffle_mask_addr()));

    // Load and shuffle round keys. The java expanded key ordering is rotated one position in decryption.
    // So the first round key is loaded from 1*16 here and last round key is loaded from 0*16
    ev_load_key(xmm9,  key, 1 * 16, xmm_key_shuf_mask);
    ev_load_key(xmm10, key, 2 * 16, xmm_key_shuf_mask);
    ev_load_key(xmm11, key, 3 * 16, xmm_key_shuf_mask);
    ev_load_key(xmm12, key, 4 * 16, xmm_key_shuf_mask);
    ev_load_key(xmm13, key, 5 * 16, xmm_key_shuf_mask);
    ev_load_key(xmm14, key, 6 * 16, xmm_key_shuf_mask);
    ev_load_key(xmm15, key, 7 * 16, xmm_key_shuf_mask);
    ev_load_key(xmm16, key, 8 * 16, xmm_key_shuf_mask);
    ev_load_key(xmm17, key, 9 * 16, xmm_key_shuf_mask);
    ev_load_key(xmm18, key, 10 * 16, xmm_key_shuf_mask);
    ev_load_key(xmm27, key, 0 * 16, xmm_key_shuf_mask);
    cmpl(rounds, 52);
    jcc(Assembler::greaterEqual, KEY_192);
    jmp(Loop_start);

    bind(KEY_192);
    ev_load_key(xmm19, key, 11 * 16, xmm_key_shuf_mask);
    ev_load_key(xmm20, key, 12 * 16, xmm_key_shuf_mask);
    cmpl(rounds, 60);
    jcc(Assembler::equal, KEY_256);
    jmp(Loop_start);

    bind(KEY_256);
    ev_load_key(xmm21, key, 13 * 16, xmm_key_shuf_mask);
    ev_load_key(xmm22, key, 14 * 16, xmm_key_shuf_mask);
    bind(Loop_start);
    movq(rbx, len);
    // Convert input length to number of blocks
    shrq(len, 4);
    shlq(rbx, 60);
    jcc(Assembler::equal, NO_PARTS);
    addq(len, 1);
    // Check if number of blocks is greater than/ equal to 32
    // If true, blocks then 512 bytes are processed at a time (code marked by label LOOP)
    // If not, 16 bytes are processed (code marked by label REMAINDER)
    bind(NO_PARTS);
    movq(rbx, len);
    shrq(len, 5);
    jcc(Assembler::equal, REMAINDER);
    movl(r13, len);
    // Compute number of blocks that will be processed as 512 bytes at a time
    // Subtract this from the total number of blocks, which will then be processed by REMAINDER loop.
    shlq(r13, 5);
    subq(rbx, r13);

    bind(LOOP);
    // Move 64 bytes of CT data into a zmm register, as a result 512 bytes of CT loaded in zmm0-7
    evmovdquq(xmm0, Address(src_addr, pos, Address::times_1, 0 * 64), Assembler::AVX_512bit);
    evmovdquq(xmm1, Address(src_addr, pos, Address::times_1, 1 * 64), Assembler::AVX_512bit);
    evmovdquq(xmm2, Address(src_addr, pos, Address::times_1, 2 * 64), Assembler::AVX_512bit);
    evmovdquq(xmm3, Address(src_addr, pos, Address::times_1, 3 * 64), Assembler::AVX_512bit);
    evmovdquq(xmm4, Address(src_addr, pos, Address::times_1, 4 * 64), Assembler::AVX_512bit);
    evmovdquq(xmm5, Address(src_addr, pos, Address::times_1, 5 * 64), Assembler::AVX_512bit);
    evmovdquq(xmm6, Address(src_addr, pos, Address::times_1, 6 * 64), Assembler::AVX_512bit);
    evmovdquq(xmm7, Address(src_addr, pos, Address::times_1, 7 * 64), Assembler::AVX_512bit);
    // Xor with the first round key
    evpxorq(xmm0, xmm0, xmm9, Assembler::AVX_512bit);
    evpxorq(xmm1, xmm1, xmm9, Assembler::AVX_512bit);
    evpxorq(xmm2, xmm2, xmm9, Assembler::AVX_512bit);
    evpxorq(xmm3, xmm3, xmm9, Assembler::AVX_512bit);
    evpxorq(xmm4, xmm4, xmm9, Assembler::AVX_512bit);
    evpxorq(xmm5, xmm5, xmm9, Assembler::AVX_512bit);
    evpxorq(xmm6, xmm6, xmm9, Assembler::AVX_512bit);
    evpxorq(xmm7, xmm7, xmm9, Assembler::AVX_512bit);
    // 9 rounds of Aesdec
    roundDec(xmm10, 7);
    roundDec(xmm11, 7);
    roundDec(xmm12, 7);
    roundDec(xmm13, 7);
    roundDec(xmm14, 7);
    roundDec(xmm15, 7);
    roundDec(xmm16, 7);
    roundDec(xmm17, 7);
    roundDec(xmm18, 7);
    cmpl(rounds, 52);
    jcc(Assembler::aboveEqual, AES192);
    // Aesdeclast round for keysize = 128
    lastroundDec(xmm27, 7);
    jmp(END_LOOP);

    bind(AES192);
    // 2 Additional rounds for keysize = 192
    roundDec(xmm19, 7);
    roundDec(xmm20, 7);
    cmpl(rounds, 60);
    jcc(Assembler::aboveEqual, AES256);
    // Aesdeclast round for keysize = 192
    lastroundDec(xmm27, 7);
    jmp(END_LOOP);
    bind(AES256);
    // 2 Additional rounds and Aesdeclast for keysize = 256
    roundDec(xmm21, 7);
    roundDec(xmm22, 7);
    lastroundDec(xmm27, 7);

    bind(END_LOOP);
    // Write 512 bytes of PT to the destination
    evmovdquq(Address(dest_addr, pos, Address::times_1, 0 * 64), xmm0, Assembler::AVX_512bit);
    evmovdquq(Address(dest_addr, pos, Address::times_1, 1 * 64), xmm1, Assembler::AVX_512bit);
    evmovdquq(Address(dest_addr, pos, Address::times_1, 2 * 64), xmm2, Assembler::AVX_512bit);
    evmovdquq(Address(dest_addr, pos, Address::times_1, 3 * 64), xmm3, Assembler::AVX_512bit);
    evmovdquq(Address(dest_addr, pos, Address::times_1, 4 * 64), xmm4, Assembler::AVX_512bit);
    evmovdquq(Address(dest_addr, pos, Address::times_1, 5 * 64), xmm5, Assembler::AVX_512bit);
    evmovdquq(Address(dest_addr, pos, Address::times_1, 6 * 64), xmm6, Assembler::AVX_512bit);
    evmovdquq(Address(dest_addr, pos, Address::times_1, 7 * 64), xmm7, Assembler::AVX_512bit);

    addq(pos, 512);
    decq(len);
    jcc(Assembler::notEqual, LOOP);

    bind(REMAINDER);
    vzeroupper();
    cmpq(rbx, 0);
    jcc(Assembler::equal, END);
    // Process 16 bytes at a time
    bind(LOOP2);
    movdqu(xmm1, Address(src_addr, pos, Address::times_1, 0));
    vpxor(xmm1, xmm1, xmm9, Assembler::AVX_128bit);
    // xmm2 contains shuffled key for Aesdeclast operation.
    vmovdqu(xmm2, xmm27);

    vaesdec(xmm1, xmm1, xmm10, Assembler::AVX_128bit);
    vaesdec(xmm1, xmm1, xmm11, Assembler::AVX_128bit);
    vaesdec(xmm1, xmm1, xmm12, Assembler::AVX_128bit);
    vaesdec(xmm1, xmm1, xmm13, Assembler::AVX_128bit);
    vaesdec(xmm1, xmm1, xmm14, Assembler::AVX_128bit);
    vaesdec(xmm1, xmm1, xmm15, Assembler::AVX_128bit);
    vaesdec(xmm1, xmm1, xmm16, Assembler::AVX_128bit);
    vaesdec(xmm1, xmm1, xmm17, Assembler::AVX_128bit);
    vaesdec(xmm1, xmm1, xmm18, Assembler::AVX_128bit);

    cmpl(rounds, 52);
    jcc(Assembler::below, LAST2);
    vaesdec(xmm1, xmm1, xmm19, Assembler::AVX_128bit);
    vaesdec(xmm1, xmm1, xmm20, Assembler::AVX_128bit);
    cmpl(rounds, 60);
    jcc(Assembler::below, LAST2);
    vaesdec(xmm1, xmm1, xmm21, Assembler::AVX_128bit);
    vaesdec(xmm1, xmm1, xmm22, Assembler::AVX_128bit);

    bind(LAST2);
    // Aesdeclast round
    vaesdeclast(xmm1, xmm1, xmm2, Assembler::AVX_128bit);
    // Write 16 bytes of PT to destination
    movdqu(Address(dest_addr, pos, Address::times_1, 0), xmm1);
    addq(pos, 16);
    decq(rbx);
    jcc(Assembler::notEqual, LOOP2);

    bind(END);
    // Zero out the round keys
    evpxorq(xmm8, xmm8, xmm8, Assembler::AVX_512bit);
    evpxorq(xmm9, xmm9, xmm9, Assembler::AVX_512bit);
    evpxorq(xmm10, xmm10, xmm10, Assembler::AVX_512bit);
    evpxorq(xmm11, xmm11, xmm11, Assembler::AVX_512bit);
    evpxorq(xmm12, xmm12, xmm12, Assembler::AVX_512bit);
    evpxorq(xmm13, xmm13, xmm13, Assembler::AVX_512bit);
    evpxorq(xmm14, xmm14, xmm14, Assembler::AVX_512bit);
    evpxorq(xmm15, xmm15, xmm15, Assembler::AVX_512bit);
    evpxorq(xmm16, xmm16, xmm16, Assembler::AVX_512bit);
    evpxorq(xmm17, xmm17, xmm17, Assembler::AVX_512bit);
    evpxorq(xmm18, xmm18, xmm18, Assembler::AVX_512bit);
    evpxorq(xmm27, xmm27, xmm27, Assembler::AVX_512bit);
    cmpl(rounds, 44);
    jcc(Assembler::belowEqual, EXIT);
    evpxorq(xmm19, xmm19, xmm19, Assembler::AVX_512bit);
    evpxorq(xmm20, xmm20, xmm20, Assembler::AVX_512bit);
    cmpl(rounds, 52);
    jcc(Assembler::belowEqual, EXIT);
    evpxorq(xmm21, xmm21, xmm21, Assembler::AVX_512bit);
    evpxorq(xmm22, xmm22, xmm22, Assembler::AVX_512bit);
    bind(EXIT);
    pop(rbx);
    pop(rax); // return length
    pop(r12);
    pop(r13);
}

// Multiply 128 x 128 bits, using 4 pclmulqdq operations
void MacroAssembler::schoolbookAAD(int i, Register htbl, XMMRegister data,
    XMMRegister tmp0, XMMRegister tmp1, XMMRegister tmp2, XMMRegister tmp3) {
    movdqu(xmm15, Address(htbl, i * 16));
    vpclmulhqlqdq(tmp3, data, xmm15); // 0x01
    vpxor(tmp2, tmp2, tmp3, Assembler::AVX_128bit);
    vpclmulldq(tmp3, data, xmm15); // 0x00
    vpxor(tmp0, tmp0, tmp3, Assembler::AVX_128bit);
    vpclmulhdq(tmp3, data, xmm15); // 0x11
    vpxor(tmp1, tmp1, tmp3, Assembler::AVX_128bit);
    vpclmullqhqdq(tmp3, data, xmm15); // 0x10
    vpxor(tmp2, tmp2, tmp3, Assembler::AVX_128bit);
}

// Multiply two 128 bit numbers resulting in a 256 bit value
// Result of the multiplication followed by reduction stored in state
void MacroAssembler::gfmul(XMMRegister tmp0, XMMRegister state) {
    const XMMRegister tmp1 = xmm4;
    const XMMRegister tmp2 = xmm5;
    const XMMRegister tmp3 = xmm6;
    const XMMRegister tmp4 = xmm7;

    vpclmulldq(tmp1, state, tmp0); //0x00  (a0 * b0)
    vpclmulhdq(tmp4, state, tmp0);//0x11 (a1 * b1)
    vpclmullqhqdq(tmp2, state, tmp0);//0x10 (a1 * b0)
    vpclmulhqlqdq(tmp3, state, tmp0); //0x01 (a0 * b1)

    vpxor(tmp2, tmp2, tmp3, Assembler::AVX_128bit); // (a0 * b1) + (a1 * b0)

    vpslldq(tmp3, tmp2, 8, Assembler::AVX_128bit);
    vpsrldq(tmp2, tmp2, 8, Assembler::AVX_128bit);
    vpxor(tmp1, tmp1, tmp3, Assembler::AVX_128bit); // tmp1 and tmp4 hold the result
    vpxor(tmp4, tmp4, tmp2, Assembler::AVX_128bit); // of carryless multiplication
    // Follows the reduction technique mentioned in
    // Shift-XOR reduction described in Gueron-Kounavis May 2010
    // First phase of reduction
    //
    vpslld(xmm8, tmp1, 31, Assembler::AVX_128bit); // packed right shift shifting << 31
    vpslld(xmm9, tmp1, 30, Assembler::AVX_128bit); // packed right shift shifting << 30
    vpslld(xmm10, tmp1, 25, Assembler::AVX_128bit);// packed right shift shifting << 25
    // xor the shifted versions
    vpxor(xmm8, xmm8, xmm9, Assembler::AVX_128bit);
    vpxor(xmm8, xmm8, xmm10, Assembler::AVX_128bit);
    vpslldq(xmm9, xmm8, 12, Assembler::AVX_128bit);
    vpsrldq(xmm8, xmm8, 4, Assembler::AVX_128bit);
    vpxor(tmp1, tmp1, xmm9, Assembler::AVX_128bit);// first phase of the reduction complete
    //
    // Second phase of the reduction
    //
    vpsrld(xmm9, tmp1, 1, Assembler::AVX_128bit);// packed left shifting >> 1
    vpsrld(xmm10, tmp1, 2, Assembler::AVX_128bit);// packed left shifting >> 2
    vpsrld(xmm11, tmp1, 7, Assembler::AVX_128bit);// packed left shifting >> 7
    vpxor(xmm9, xmm9, xmm10, Assembler::AVX_128bit);// xor the shifted versions
    vpxor(xmm9, xmm9, xmm11, Assembler::AVX_128bit);
    vpxor(xmm9, xmm9, xmm8, Assembler::AVX_128bit);
    vpxor(tmp1, tmp1, xmm9, Assembler::AVX_128bit);
    vpxor(state, tmp4, tmp1, Assembler::AVX_128bit);// the result is in state
    ret(0);
}

// This method takes the subkey after expansion as input and generates 1 * 16 power of subkey H.
// The power of H is used in reduction process for one block ghash
void MacroAssembler::generateHtbl_one_block(Register htbl) {
    const XMMRegister t = xmm13;

    // load the original subkey hash
    movdqu(t, Address(htbl, 0));
    // shuffle using long swap mask
    movdqu(xmm10, ExternalAddress(StubRoutines::x86::ghash_long_swap_mask_addr()));
    vpshufb(t, t, xmm10, Assembler::AVX_128bit);

    // Compute H' = GFMUL(H, 2)
    vpsrld(xmm3, t, 7, Assembler::AVX_128bit);
    movdqu(xmm4, ExternalAddress(StubRoutines::x86::ghash_shufflemask_addr()));
    vpshufb(xmm3, xmm3, xmm4, Assembler::AVX_128bit);
    movl(rax, 0xff00);
    movdl(xmm4, rax);
    vpshufb(xmm4, xmm4, xmm3, Assembler::AVX_128bit);
    movdqu(xmm5, ExternalAddress(StubRoutines::x86::ghash_polynomial_addr()));
    vpand(xmm5, xmm5, xmm4, Assembler::AVX_128bit);
    vpsrld(xmm3, t, 31, Assembler::AVX_128bit);
    vpslld(xmm4, t, 1, Assembler::AVX_128bit);
    vpslldq(xmm3, xmm3, 4, Assembler::AVX_128bit);
    vpxor(t, xmm4, xmm3, Assembler::AVX_128bit);// t holds p(x) <<1 or H * 2

    //Adding p(x)<<1 to xmm5 which holds the reduction polynomial
    vpxor(t, t, xmm5, Assembler::AVX_128bit);
    movdqu(Address(htbl, 1 * 16), t); // H * 2

    ret(0);
}

// This method takes the subkey after expansion as input and generates the remaining powers of subkey H.
// The power of H is used in reduction process for eight block ghash
void MacroAssembler::generateHtbl_eight_blocks(Register htbl) {
    const XMMRegister t = xmm13;
    const XMMRegister tmp0 = xmm1;
    Label GFMUL;

    movdqu(t, Address(htbl, 1 * 16));
    movdqu(tmp0, t);

    // tmp0 and t hold H. Now we compute powers of H by using GFMUL(H, H)
    call(GFMUL, relocInfo::none);
    movdqu(Address(htbl, 2 * 16), t); //H ^ 2 * 2
    call(GFMUL, relocInfo::none);
    movdqu(Address(htbl, 3 * 16), t); //H ^ 3 * 2
    call(GFMUL, relocInfo::none);
    movdqu(Address(htbl, 4 * 16), t); //H ^ 4 * 2
    call(GFMUL, relocInfo::none);
    movdqu(Address(htbl, 5 * 16), t); //H ^ 5 * 2
    call(GFMUL, relocInfo::none);
    movdqu(Address(htbl, 6 * 16), t); //H ^ 6 * 2
    call(GFMUL, relocInfo::none);
    movdqu(Address(htbl, 7 * 16), t); //H ^ 7 * 2
    call(GFMUL, relocInfo::none);
    movdqu(Address(htbl, 8 * 16), t); //H ^ 8 * 2
    ret(0);

    bind(GFMUL);
    gfmul(tmp0, t);
}

// Multiblock and single block GHASH computation using Shift XOR reduction technique
void MacroAssembler::avx_ghash(Register input_state, Register htbl,
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

    testptr(blocks, blocks);
    jcc(Assembler::zero, EXIT_GHASH);

    // Check if Hashtable (1*16) has been already generated
    // For anything less than 8 blocks, we generate only the first power of H.
    movdqu(tmp2, Address(htbl, 1 * 16));
    ptest(tmp2, tmp2);
    jcc(Assembler::notZero, BEGIN_PROCESS);
    call(GENERATE_HTBL_1_BLK, relocInfo::none);

    // Shuffle the input state
    bind(BEGIN_PROCESS);
    movdqu(lswap_mask, ExternalAddress(StubRoutines::x86::ghash_long_swap_mask_addr()));
    movdqu(state, Address(input_state, 0));
    vpshufb(state, state, lswap_mask, Assembler::AVX_128bit);

    cmpl(blocks, 8);
    jcc(Assembler::below, ONE_BLK_INIT);
    // If we have 8 blocks or more data, then generate remaining powers of H
    movdqu(tmp2, Address(htbl, 8 * 16));
    ptest(tmp2, tmp2);
    jcc(Assembler::notZero, PROCESS_8_BLOCKS);
    call(GENERATE_HTBL_8_BLKS, relocInfo::none);

    //Do 8 multiplies followed by a reduction processing 8 blocks of data at a time
    //Each block = 16 bytes.
    bind(PROCESS_8_BLOCKS);
    subl(blocks, 8);
    movdqu(bswap_mask, ExternalAddress(StubRoutines::x86::ghash_byte_swap_mask_addr()));
    movdqu(data, Address(input_data, 16 * 7));
    vpshufb(data, data, bswap_mask, Assembler::AVX_128bit);
    //Loading 1*16 as calculated powers of H required starts at that location.
    movdqu(xmm15, Address(htbl, 1 * 16));
    //Perform carryless multiplication of (H*2, data block #7)
    vpclmulhqlqdq(tmp2, data, xmm15);//a0 * b1
    vpclmulldq(tmp0, data, xmm15);//a0 * b0
    vpclmulhdq(tmp1, data, xmm15);//a1 * b1
    vpclmullqhqdq(tmp3, data, xmm15);//a1* b0
    vpxor(tmp2, tmp2, tmp3, Assembler::AVX_128bit);// (a0 * b1) + (a1 * b0)

    movdqu(data, Address(input_data, 16 * 6));
    vpshufb(data, data, bswap_mask, Assembler::AVX_128bit);
    // Perform carryless multiplication of (H^2 * 2, data block #6)
    schoolbookAAD(2, htbl, data, tmp0, tmp1, tmp2, tmp3);

    movdqu(data, Address(input_data, 16 * 5));
    vpshufb(data, data, bswap_mask, Assembler::AVX_128bit);
    // Perform carryless multiplication of (H^3 * 2, data block #5)
    schoolbookAAD(3, htbl, data, tmp0, tmp1, tmp2, tmp3);
    movdqu(data, Address(input_data, 16 * 4));
    vpshufb(data, data, bswap_mask, Assembler::AVX_128bit);
    // Perform carryless multiplication of (H^4 * 2, data block #4)
    schoolbookAAD(4, htbl, data, tmp0, tmp1, tmp2, tmp3);
    movdqu(data, Address(input_data, 16 * 3));
    vpshufb(data, data, bswap_mask, Assembler::AVX_128bit);
    // Perform carryless multiplication of (H^5 * 2, data block #3)
    schoolbookAAD(5, htbl, data, tmp0, tmp1, tmp2, tmp3);
    movdqu(data, Address(input_data, 16 * 2));
    vpshufb(data, data, bswap_mask, Assembler::AVX_128bit);
    // Perform carryless multiplication of (H^6 * 2, data block #2)
    schoolbookAAD(6, htbl, data, tmp0, tmp1, tmp2, tmp3);
    movdqu(data, Address(input_data, 16 * 1));
    vpshufb(data, data, bswap_mask, Assembler::AVX_128bit);
    // Perform carryless multiplication of (H^7 * 2, data block #1)
    schoolbookAAD(7, htbl, data, tmp0, tmp1, tmp2, tmp3);
    movdqu(data, Address(input_data, 16 * 0));
    // xor data block#0 with input state before perfoming carry-less multiplication
    vpshufb(data, data, bswap_mask, Assembler::AVX_128bit);
    vpxor(data, data, state, Assembler::AVX_128bit);
    // Perform carryless multiplication of (H^8 * 2, data block #0)
    schoolbookAAD(8, htbl, data, tmp0, tmp1, tmp2, tmp3);
    vpslldq(tmp3, tmp2, 8, Assembler::AVX_128bit);
    vpsrldq(tmp2, tmp2, 8, Assembler::AVX_128bit);
    vpxor(tmp0, tmp0, tmp3, Assembler::AVX_128bit);// tmp0, tmp1 contains aggregated results of
    vpxor(tmp1, tmp1, tmp2, Assembler::AVX_128bit);// the multiplication operation

    // we have the 2 128-bit partially accumulated multiplication results in tmp0:tmp1
    // with higher 128-bit in tmp1 and lower 128-bit in corresponding tmp0
    // Follows the reduction technique mentioned in
    // Shift-XOR reduction described in Gueron-Kounavis May 2010
    bind(BLOCK8_REDUCTION);
    // First Phase of the reduction
    vpslld(xmm8, tmp0, 31, Assembler::AVX_128bit); // packed right shifting << 31
    vpslld(xmm9, tmp0, 30, Assembler::AVX_128bit); // packed right shifting << 30
    vpslld(xmm10, tmp0, 25, Assembler::AVX_128bit); // packed right shifting << 25
    // xor the shifted versions
    vpxor(xmm8, xmm8, xmm10, Assembler::AVX_128bit);
    vpxor(xmm8, xmm8, xmm9, Assembler::AVX_128bit);

    vpslldq(xmm9, xmm8, 12, Assembler::AVX_128bit);
    vpsrldq(xmm8, xmm8, 4, Assembler::AVX_128bit);

    vpxor(tmp0, tmp0, xmm9, Assembler::AVX_128bit); // first phase of reduction is complete
    // second phase of the reduction
    vpsrld(xmm9, tmp0, 1, Assembler::AVX_128bit); // packed left shifting >> 1
    vpsrld(xmm10, tmp0, 2, Assembler::AVX_128bit); // packed left shifting >> 2
    vpsrld(tmp2, tmp0, 7, Assembler::AVX_128bit); // packed left shifting >> 7
    // xor the shifted versions
    vpxor(xmm9, xmm9, xmm10, Assembler::AVX_128bit);
    vpxor(xmm9, xmm9, tmp2, Assembler::AVX_128bit);
    vpxor(xmm9, xmm9, xmm8, Assembler::AVX_128bit);
    vpxor(tmp0, xmm9, tmp0, Assembler::AVX_128bit);
    // Final result is in state
    vpxor(state, tmp0, tmp1, Assembler::AVX_128bit);

    lea(input_data, Address(input_data, 16 * 8));
    cmpl(blocks, 8);
    jcc(Assembler::below, ONE_BLK_INIT);
    jmp(PROCESS_8_BLOCKS);

    // Since this is one block operation we will only use H * 2 i.e. the first power of H
    bind(ONE_BLK_INIT);
    movdqu(tmp0, Address(htbl, 1 * 16));
    movdqu(bswap_mask, ExternalAddress(StubRoutines::x86::ghash_byte_swap_mask_addr()));

    //Do one (128 bit x 128 bit) carry-less multiplication at a time followed by a reduction.
    bind(PROCESS_1_BLOCK);
    cmpl(blocks, 0);
    jcc(Assembler::equal, SAVE_STATE);
    subl(blocks, 1);
    movdqu(data, Address(input_data, 0));
    vpshufb(data, data, bswap_mask, Assembler::AVX_128bit);
    vpxor(state, state, data, Assembler::AVX_128bit);
    // gfmul(H*2, state)
    call(GFMUL, relocInfo::none);
    addptr(input_data, 16);
    jmp(PROCESS_1_BLOCK);

    bind(SAVE_STATE);
    vpshufb(state, state, lswap_mask, Assembler::AVX_128bit);
    movdqu(Address(input_state, 0), state);
    jmp(EXIT_GHASH);

    bind(GFMUL);
    gfmul(tmp0, state);

    bind(GENERATE_HTBL_1_BLK);
    generateHtbl_one_block(htbl);

    bind(GENERATE_HTBL_8_BLKS);
    generateHtbl_eight_blocks(htbl);

    bind(EXIT_GHASH);
    // zero out xmm registers used for Htbl storage
    vpxor(xmm0, xmm0, xmm0, Assembler::AVX_128bit);
    vpxor(xmm1, xmm1, xmm1, Assembler::AVX_128bit);
    vpxor(xmm3, xmm3, xmm3, Assembler::AVX_128bit);
    vpxor(xmm15, xmm15, xmm15, Assembler::AVX_128bit);
}

// AES Counter Mode using VAES instructions
void MacroAssembler::aesctr_encrypt(Register src_addr, Register dest_addr, Register key, Register counter,
    Register len_reg, Register used, Register used_addr, Register saved_encCounter_start) {

    const Register rounds = 0;
    const Register pos = r12;

    Label PRELOOP_START, EXIT_PRELOOP, REMAINDER, REMAINDER_16, LOOP, END, EXIT, END_LOOP,
    AES192, AES256, AES192_REMAINDER16, REMAINDER16_END_LOOP, AES256_REMAINDER16,
    REMAINDER_8, REMAINDER_4, AES192_REMAINDER8, REMAINDER_LOOP, AES256_REMINDER,
    AES192_REMAINDER, END_REMAINDER_LOOP, AES256_REMAINDER8, REMAINDER8_END_LOOP,
    AES192_REMAINDER4, AES256_REMAINDER4, AES256_REMAINDER, END_REMAINDER4, EXTRACT_TAILBYTES,
    EXTRACT_TAIL_4BYTES, EXTRACT_TAIL_2BYTES, EXTRACT_TAIL_1BYTE, STORE_CTR;

    cmpl(len_reg, 0);
    jcc(Assembler::belowEqual, EXIT);

    movl(pos, 0);
    // if the number of used encrypted counter bytes < 16,
    // XOR PT with saved encrypted counter to obtain CT
    bind(PRELOOP_START);
    cmpl(used, 16);
    jcc(Assembler::aboveEqual, EXIT_PRELOOP);
    movb(rbx, Address(saved_encCounter_start, used));
    xorb(rbx, Address(src_addr, pos));
    movb(Address(dest_addr, pos), rbx);
    addptr(pos, 1);
    addptr(used, 1);
    decrement(len_reg);
    jmp(PRELOOP_START);

    bind(EXIT_PRELOOP);
    movl(Address(used_addr, 0), used);

    // Calculate number of rounds i.e. 10, 12, 14,  based on key length(128, 192, 256).
    movl(rounds, Address(key, arrayOopDesc::length_offset_in_bytes() - arrayOopDesc::base_offset_in_bytes(T_INT)));

    vpxor(xmm0, xmm0, xmm0, Assembler::AVX_128bit);
    // Move initial counter value in xmm0
    movdqu(xmm0, Address(counter, 0));
    // broadcast counter value to zmm8
    evshufi64x2(xmm8, xmm0, xmm0, 0, Assembler::AVX_512bit);

    // load lbswap mask
    evmovdquq(xmm16, ExternalAddress(StubRoutines::x86::counter_mask_addr()), Assembler::AVX_512bit, r15);

    //shuffle counter using lbswap_mask
    vpshufb(xmm8, xmm8, xmm16, Assembler::AVX_512bit);

    // pre-increment and propagate counter values to zmm9-zmm15 registers.
    // Linc0 increments the zmm8 by 1 (initial value being 0), Linc4 increments the counters zmm9-zmm15 by 4
    // The counter is incremented after each block i.e. 16 bytes is processed;
    // each zmm register has 4 counter values as its MSB
    // the counters are incremented in parallel
    vpaddd(xmm8, xmm8, ExternalAddress(StubRoutines::x86::counter_mask_addr() + 64), Assembler::AVX_512bit, r15);//linc0
    vpaddd(xmm9, xmm8, ExternalAddress(StubRoutines::x86::counter_mask_addr() + 128), Assembler::AVX_512bit, r15);//linc4(rip)
    vpaddd(xmm10, xmm9, ExternalAddress(StubRoutines::x86::counter_mask_addr() + 128), Assembler::AVX_512bit, r15);//Linc4(rip)
    vpaddd(xmm11, xmm10, ExternalAddress(StubRoutines::x86::counter_mask_addr() + 128), Assembler::AVX_512bit, r15);//Linc4(rip)
    vpaddd(xmm12, xmm11, ExternalAddress(StubRoutines::x86::counter_mask_addr() + 128), Assembler::AVX_512bit, r15);//Linc4(rip)
    vpaddd(xmm13, xmm12, ExternalAddress(StubRoutines::x86::counter_mask_addr() + 128), Assembler::AVX_512bit, r15);//Linc4(rip)
    vpaddd(xmm14, xmm13, ExternalAddress(StubRoutines::x86::counter_mask_addr() + 128), Assembler::AVX_512bit, r15);//Linc4(rip)
    vpaddd(xmm15, xmm14, ExternalAddress(StubRoutines::x86::counter_mask_addr() + 128), Assembler::AVX_512bit, r15);//Linc4(rip)

    // load linc32 mask in zmm register.linc32 increments counter by 32
    evmovdquq(xmm19, ExternalAddress(StubRoutines::x86::counter_mask_addr() + 256), Assembler::AVX_512bit, r15);//Linc32

    // xmm31 contains the key shuffle mask.
    movdqu(xmm31, ExternalAddress(StubRoutines::x86::key_shuffle_mask_addr()));
    // Load key function loads 128 bit key and shuffles it. Then we broadcast the shuffled key to convert it into a 512 bit value.
    // For broadcasting the values to ZMM, vshufi64 is used instead of evbroadcasti64x2 as the source in this case is ZMM register
    // that holds shuffled key value.
    ev_load_key(xmm20, key, 0, xmm31);
    ev_load_key(xmm21, key, 1 * 16, xmm31);
    ev_load_key(xmm22, key, 2 * 16, xmm31);
    ev_load_key(xmm23, key, 3 * 16, xmm31);
    ev_load_key(xmm24, key, 4 * 16, xmm31);
    ev_load_key(xmm25, key, 5 * 16, xmm31);
    ev_load_key(xmm26, key, 6 * 16, xmm31);
    ev_load_key(xmm27, key, 7 * 16, xmm31);
    ev_load_key(xmm28, key, 8 * 16, xmm31);
    ev_load_key(xmm29, key, 9 * 16, xmm31);
    ev_load_key(xmm30, key, 10 * 16, xmm31);

    // Process 32 blocks or 512 bytes of data
    bind(LOOP);
    cmpl(len_reg, 512);
    jcc(Assembler::less, REMAINDER);
    subq(len_reg, 512);
    //Shuffle counter and Exor it with roundkey1. Result is stored in zmm0-7
    vpshufb(xmm0, xmm8, xmm16, Assembler::AVX_512bit);
    evpxorq(xmm0, xmm0, xmm20, Assembler::AVX_512bit);
    vpshufb(xmm1, xmm9, xmm16, Assembler::AVX_512bit);
    evpxorq(xmm1, xmm1, xmm20, Assembler::AVX_512bit);
    vpshufb(xmm2, xmm10, xmm16, Assembler::AVX_512bit);
    evpxorq(xmm2, xmm2, xmm20, Assembler::AVX_512bit);
    vpshufb(xmm3, xmm11, xmm16, Assembler::AVX_512bit);
    evpxorq(xmm3, xmm3, xmm20, Assembler::AVX_512bit);
    vpshufb(xmm4, xmm12, xmm16, Assembler::AVX_512bit);
    evpxorq(xmm4, xmm4, xmm20, Assembler::AVX_512bit);
    vpshufb(xmm5, xmm13, xmm16, Assembler::AVX_512bit);
    evpxorq(xmm5, xmm5, xmm20, Assembler::AVX_512bit);
    vpshufb(xmm6, xmm14, xmm16, Assembler::AVX_512bit);
    evpxorq(xmm6, xmm6, xmm20, Assembler::AVX_512bit);
    vpshufb(xmm7, xmm15, xmm16, Assembler::AVX_512bit);
    evpxorq(xmm7, xmm7, xmm20, Assembler::AVX_512bit);
    // Perform AES encode operations and put results in zmm0-zmm7.
    // This is followed by incrementing counter values in zmm8-zmm15.
    // Since we will be processing 32 blocks at a time, the counter is incremented by 32.
    roundEnc(xmm21, 7);
    vpaddq(xmm8, xmm8, xmm19, Assembler::AVX_512bit);
    roundEnc(xmm22, 7);
    vpaddq(xmm9, xmm9, xmm19, Assembler::AVX_512bit);
    roundEnc(xmm23, 7);
    vpaddq(xmm10, xmm10, xmm19, Assembler::AVX_512bit);
    roundEnc(xmm24, 7);
    vpaddq(xmm11, xmm11, xmm19, Assembler::AVX_512bit);
    roundEnc(xmm25, 7);
    vpaddq(xmm12, xmm12, xmm19, Assembler::AVX_512bit);
    roundEnc(xmm26, 7);
    vpaddq(xmm13, xmm13, xmm19, Assembler::AVX_512bit);
    roundEnc(xmm27, 7);
    vpaddq(xmm14, xmm14, xmm19, Assembler::AVX_512bit);
    roundEnc(xmm28, 7);
    vpaddq(xmm15, xmm15, xmm19, Assembler::AVX_512bit);
    roundEnc(xmm29, 7);

    cmpl(rounds, 52);
    jcc(Assembler::aboveEqual, AES192);
    lastroundEnc(xmm30, 7);
    jmp(END_LOOP);

    bind(AES192);
    roundEnc(xmm30, 7);
    ev_load_key(xmm18, key, 11 * 16, xmm31);
    roundEnc(xmm18, 7);
    cmpl(rounds, 60);
    jcc(Assembler::aboveEqual, AES256);
    ev_load_key(xmm18, key, 12 * 16, xmm31);
    lastroundEnc(xmm18, 7);
    jmp(END_LOOP);

    bind(AES256);
    ev_load_key(xmm18, key, 12 * 16, xmm31);
    roundEnc(xmm18, 7);
    ev_load_key(xmm18, key, 13 * 16, xmm31);
    roundEnc(xmm18, 7);
    ev_load_key(xmm18, key, 14 * 16, xmm31);
    lastroundEnc(xmm18, 7);

    // After AES encode rounds, the encrypted block cipher lies in zmm0-zmm7
    // xor encrypted block cipher and input plaintext and store resultant ciphertext
    bind(END_LOOP);
    evpxorq(xmm0, xmm0, Address(src_addr, pos, Address::times_1, 0 * 64), Assembler::AVX_512bit);
    evmovdquq(Address(dest_addr, pos, Address::times_1, 0), xmm0, Assembler::AVX_512bit);
    evpxorq(xmm1, xmm1, Address(src_addr, pos, Address::times_1, 1 * 64), Assembler::AVX_512bit);
    evmovdquq(Address(dest_addr, pos, Address::times_1, 64), xmm1, Assembler::AVX_512bit);
    evpxorq(xmm2, xmm2, Address(src_addr, pos, Address::times_1, 2 * 64), Assembler::AVX_512bit);
    evmovdquq(Address(dest_addr, pos, Address::times_1, 2 * 64), xmm2, Assembler::AVX_512bit);
    evpxorq(xmm3, xmm3, Address(src_addr, pos, Address::times_1, 3 * 64), Assembler::AVX_512bit);
    evmovdquq(Address(dest_addr, pos, Address::times_1, 3 * 64), xmm3, Assembler::AVX_512bit);
    evpxorq(xmm4, xmm4, Address(src_addr, pos, Address::times_1, 4 * 64), Assembler::AVX_512bit);
    evmovdquq(Address(dest_addr, pos, Address::times_1, 4 * 64), xmm4, Assembler::AVX_512bit);
    evpxorq(xmm5, xmm5, Address(src_addr, pos, Address::times_1, 5 * 64), Assembler::AVX_512bit);
    evmovdquq(Address(dest_addr, pos, Address::times_1, 5 * 64), xmm5, Assembler::AVX_512bit);
    evpxorq(xmm6, xmm6, Address(src_addr, pos, Address::times_1, 6 * 64), Assembler::AVX_512bit);
    evmovdquq(Address(dest_addr, pos, Address::times_1, 6 * 64), xmm6, Assembler::AVX_512bit);
    evpxorq(xmm7, xmm7, Address(src_addr, pos, Address::times_1, 7 * 64), Assembler::AVX_512bit);
    evmovdquq(Address(dest_addr, pos, Address::times_1, 7 * 64), xmm7, Assembler::AVX_512bit);
    addq(pos, 512);
    jmp(LOOP);

    // Encode 256, 128, 64 or 16 bytes at a time if length is less than 512 bytes
    bind(REMAINDER);
    cmpl(len_reg, 0);
    jcc(Assembler::equal, END);
    cmpl(len_reg, 256);
    jcc(Assembler::aboveEqual, REMAINDER_16);
    cmpl(len_reg, 128);
    jcc(Assembler::aboveEqual, REMAINDER_8);
    cmpl(len_reg, 64);
    jcc(Assembler::aboveEqual, REMAINDER_4);
    // At this point, we will process 16 bytes of data at a time.
    // So load xmm19 with counter increment value as 1
    evmovdquq(xmm19, ExternalAddress(StubRoutines::x86::counter_mask_addr() + 80), Assembler::AVX_128bit, r15);
    jmp(REMAINDER_LOOP);

    // Each ZMM register can be used to encode 64 bytes of data, so we have 4 ZMM registers to encode 256 bytes of data
    bind(REMAINDER_16);
    subq(len_reg, 256);
    // As we process 16 blocks at a time, load mask for incrementing the counter value by 16
    evmovdquq(xmm19, ExternalAddress(StubRoutines::x86::counter_mask_addr() + 320), Assembler::AVX_512bit, r15);//Linc16(rip)
    // shuffle counter and XOR counter with roundkey1
    vpshufb(xmm0, xmm8, xmm16, Assembler::AVX_512bit);
    evpxorq(xmm0, xmm0, xmm20, Assembler::AVX_512bit);
    vpshufb(xmm1, xmm9, xmm16, Assembler::AVX_512bit);
    evpxorq(xmm1, xmm1, xmm20, Assembler::AVX_512bit);
    vpshufb(xmm2, xmm10, xmm16, Assembler::AVX_512bit);
    evpxorq(xmm2, xmm2, xmm20, Assembler::AVX_512bit);
    vpshufb(xmm3, xmm11, xmm16, Assembler::AVX_512bit);
    evpxorq(xmm3, xmm3, xmm20, Assembler::AVX_512bit);
    // Increment counter values by 16
    vpaddq(xmm8, xmm8, xmm19, Assembler::AVX_512bit);
    vpaddq(xmm9, xmm9, xmm19, Assembler::AVX_512bit);
    // AES encode rounds
    roundEnc(xmm21, 3);
    roundEnc(xmm22, 3);
    roundEnc(xmm23, 3);
    roundEnc(xmm24, 3);
    roundEnc(xmm25, 3);
    roundEnc(xmm26, 3);
    roundEnc(xmm27, 3);
    roundEnc(xmm28, 3);
    roundEnc(xmm29, 3);

    cmpl(rounds, 52);
    jcc(Assembler::aboveEqual, AES192_REMAINDER16);
    lastroundEnc(xmm30, 3);
    jmp(REMAINDER16_END_LOOP);

    bind(AES192_REMAINDER16);
    roundEnc(xmm30, 3);
    ev_load_key(xmm18, key, 11 * 16, xmm31);
    roundEnc(xmm18, 3);
    ev_load_key(xmm5, key, 12 * 16, xmm31);

    cmpl(rounds, 60);
    jcc(Assembler::aboveEqual, AES256_REMAINDER16);
    lastroundEnc(xmm5, 3);
    jmp(REMAINDER16_END_LOOP);
    bind(AES256_REMAINDER16);
    roundEnc(xmm5, 3);
    ev_load_key(xmm6, key, 13 * 16, xmm31);
    roundEnc(xmm6, 3);
    ev_load_key(xmm7, key, 14 * 16, xmm31);
    lastroundEnc(xmm7, 3);

    // After AES encode rounds, the encrypted block cipher lies in zmm0-zmm3
    // xor 256 bytes of PT with the encrypted counters to produce CT.
    bind(REMAINDER16_END_LOOP);
    evpxorq(xmm0, xmm0, Address(src_addr, pos, Address::times_1, 0), Assembler::AVX_512bit);
    evmovdquq(Address(dest_addr, pos, Address::times_1, 0), xmm0, Assembler::AVX_512bit);
    evpxorq(xmm1, xmm1, Address(src_addr, pos, Address::times_1, 1 * 64), Assembler::AVX_512bit);
    evmovdquq(Address(dest_addr, pos, Address::times_1, 1 * 64), xmm1, Assembler::AVX_512bit);
    evpxorq(xmm2, xmm2, Address(src_addr, pos, Address::times_1, 2 * 64), Assembler::AVX_512bit);
    evmovdquq(Address(dest_addr, pos, Address::times_1, 2 * 64), xmm2, Assembler::AVX_512bit);
    evpxorq(xmm3, xmm3, Address(src_addr, pos, Address::times_1, 3 * 64), Assembler::AVX_512bit);
    evmovdquq(Address(dest_addr, pos, Address::times_1, 3 * 64), xmm3, Assembler::AVX_512bit);
    addq(pos, 256);

    cmpl(len_reg, 128);
    jcc(Assembler::aboveEqual, REMAINDER_8);

    cmpl(len_reg, 64);
    jcc(Assembler::aboveEqual, REMAINDER_4);
    //load mask for incrementing the counter value by 1
    evmovdquq(xmm19, ExternalAddress(StubRoutines::x86::counter_mask_addr() + 80), Assembler::AVX_128bit, r15);//Linc0 + 16(rip)
    jmp(REMAINDER_LOOP);

    // Each ZMM register can be used to encode 64 bytes of data, so we have 2 ZMM registers to encode 128 bytes of data
    bind(REMAINDER_8);
    subq(len_reg, 128);
    // As we process 8 blocks at a time, load mask for incrementing the counter value by 8
    evmovdquq(xmm19, ExternalAddress(StubRoutines::x86::counter_mask_addr() + 192), Assembler::AVX_512bit, r15);//Linc8(rip)
    // shuffle counters and xor with roundkey1
    vpshufb(xmm0, xmm8, xmm16, Assembler::AVX_512bit);
    evpxorq(xmm0, xmm0, xmm20, Assembler::AVX_512bit);
    vpshufb(xmm1, xmm9, xmm16, Assembler::AVX_512bit);
    evpxorq(xmm1, xmm1, xmm20, Assembler::AVX_512bit);
    // increment counter by 8
    vpaddq(xmm8, xmm8, xmm19, Assembler::AVX_512bit);
    // AES encode
    roundEnc(xmm21, 1);
    roundEnc(xmm22, 1);
    roundEnc(xmm23, 1);
    roundEnc(xmm24, 1);
    roundEnc(xmm25, 1);
    roundEnc(xmm26, 1);
    roundEnc(xmm27, 1);
    roundEnc(xmm28, 1);
    roundEnc(xmm29, 1);

    cmpl(rounds, 52);
    jcc(Assembler::aboveEqual, AES192_REMAINDER8);
    lastroundEnc(xmm30, 1);
    jmp(REMAINDER8_END_LOOP);

    bind(AES192_REMAINDER8);
    roundEnc(xmm30, 1);
    ev_load_key(xmm18, key, 11 * 16, xmm31);
    roundEnc(xmm18, 1);
    ev_load_key(xmm5, key, 12 * 16, xmm31);
    cmpl(rounds, 60);
    jcc(Assembler::aboveEqual, AES256_REMAINDER8);
    lastroundEnc(xmm5, 1);
    jmp(REMAINDER8_END_LOOP);

    bind(AES256_REMAINDER8);
    roundEnc(xmm5, 1);
    ev_load_key(xmm6, key, 13 * 16, xmm31);
    roundEnc(xmm6, 1);
    ev_load_key(xmm7, key, 14 * 16, xmm31);
    lastroundEnc(xmm7, 1);

    bind(REMAINDER8_END_LOOP);
    // After AES encode rounds, the encrypted block cipher lies in zmm0-zmm1
    // XOR PT with the encrypted counter and store as CT
    evpxorq(xmm0, xmm0, Address(src_addr, pos, Address::times_1, 0 * 64), Assembler::AVX_512bit);
    evmovdquq(Address(dest_addr, pos, Address::times_1, 0 * 64), xmm0, Assembler::AVX_512bit);
    evpxorq(xmm1, xmm1, Address(src_addr, pos, Address::times_1, 1 * 64), Assembler::AVX_512bit);
    evmovdquq(Address(dest_addr, pos, Address::times_1, 1 * 64), xmm1, Assembler::AVX_512bit);
    addq(pos, 128);

    cmpl(len_reg, 64);
    jcc(Assembler::aboveEqual, REMAINDER_4);
    // load mask for incrementing the counter value by 1
    evmovdquq(xmm19, ExternalAddress(StubRoutines::x86::counter_mask_addr() + 80), Assembler::AVX_128bit, r15);//Linc0 + 16(rip)
    jmp(REMAINDER_LOOP);

    // Each ZMM register can be used to encode 64 bytes of data, so we have 1 ZMM register used in this block of code
    bind(REMAINDER_4);
    subq(len_reg, 64);
    // As we process 4 blocks at a time, load mask for incrementing the counter value by 4
    evmovdquq(xmm19, ExternalAddress(StubRoutines::x86::counter_mask_addr() + 128), Assembler::AVX_512bit, r15);//Linc4(rip)
    // XOR counter with first roundkey
    vpshufb(xmm0, xmm8, xmm16, Assembler::AVX_512bit);
    evpxorq(xmm0, xmm0, xmm20, Assembler::AVX_512bit);
    // Increment counter
    vpaddq(xmm8, xmm8, xmm19, Assembler::AVX_512bit);
    vaesenc(xmm0, xmm0, xmm21, Assembler::AVX_512bit);
    vaesenc(xmm0, xmm0, xmm22, Assembler::AVX_512bit);
    vaesenc(xmm0, xmm0, xmm23, Assembler::AVX_512bit);
    vaesenc(xmm0, xmm0, xmm24, Assembler::AVX_512bit);
    vaesenc(xmm0, xmm0, xmm25, Assembler::AVX_512bit);
    vaesenc(xmm0, xmm0, xmm26, Assembler::AVX_512bit);
    vaesenc(xmm0, xmm0, xmm27, Assembler::AVX_512bit);
    vaesenc(xmm0, xmm0, xmm28, Assembler::AVX_512bit);
    vaesenc(xmm0, xmm0, xmm29, Assembler::AVX_512bit);
    cmpl(rounds, 52);
    jcc(Assembler::aboveEqual, AES192_REMAINDER4);
    vaesenclast(xmm0, xmm0, xmm30, Assembler::AVX_512bit);
    jmp(END_REMAINDER4);

    bind(AES192_REMAINDER4);
    vaesenc(xmm0, xmm0, xmm30, Assembler::AVX_512bit);
    ev_load_key(xmm18, key, 11 * 16, xmm31);
    vaesenc(xmm0, xmm0, xmm18, Assembler::AVX_512bit);
    ev_load_key(xmm5, key, 12 * 16, xmm31);

    cmpl(rounds, 60);
    jcc(Assembler::aboveEqual, AES256_REMAINDER4);
    vaesenclast(xmm0, xmm0, xmm5, Assembler::AVX_512bit);
    jmp(END_REMAINDER4);

    bind(AES256_REMAINDER4);
    vaesenc(xmm0, xmm0, xmm5, Assembler::AVX_512bit);
    ev_load_key(xmm6, key, 13 * 16, xmm31);
    vaesenc(xmm0, xmm0, xmm6, Assembler::AVX_512bit);
    ev_load_key(xmm7, key, 14 * 16, xmm31);
    vaesenclast(xmm0, xmm0, xmm7, Assembler::AVX_512bit);
    // After AES encode rounds, the encrypted block cipher lies in zmm0.
    // XOR encrypted block cipher with PT and store 64 bytes of ciphertext
    bind(END_REMAINDER4);
    evpxorq(xmm0, xmm0, Address(src_addr, pos, Address::times_1, 0 * 64), Assembler::AVX_512bit);
    evmovdquq(Address(dest_addr, pos, Address::times_1, 0), xmm0, Assembler::AVX_512bit);
    addq(pos, 64);
    // load mask for incrementing the counter value by 1
    evmovdquq(xmm19, ExternalAddress(StubRoutines::x86::counter_mask_addr() + 80), Assembler::AVX_128bit, r15);//Linc0 + 16(rip)

    // For a single block, the AES rounds start here.
    bind(REMAINDER_LOOP);
    cmpl(len_reg, 0);
    jcc(Assembler::belowEqual, END);
    // XOR counter with first roundkey
    vpshufb(xmm0, xmm8, xmm16, Assembler::AVX_128bit);
    evpxorq(xmm0, xmm0, xmm20, Assembler::AVX_128bit);
    vaesenc(xmm0, xmm0, xmm21, Assembler::AVX_128bit);
    // Increment counter by 1
    vpaddq(xmm8, xmm8, xmm19, Assembler::AVX_128bit);
    vaesenc(xmm0, xmm0, xmm22, Assembler::AVX_128bit);
    vaesenc(xmm0, xmm0, xmm23, Assembler::AVX_128bit);
    vaesenc(xmm0, xmm0, xmm24, Assembler::AVX_128bit);
    vaesenc(xmm0, xmm0, xmm25, Assembler::AVX_128bit);
    vaesenc(xmm0, xmm0, xmm26, Assembler::AVX_128bit);
    vaesenc(xmm0, xmm0, xmm27, Assembler::AVX_128bit);
    vaesenc(xmm0, xmm0, xmm28, Assembler::AVX_128bit);
    vaesenc(xmm0, xmm0, xmm29, Assembler::AVX_128bit);

    cmpl(rounds, 52);
    jcc(Assembler::aboveEqual, AES192_REMAINDER);
    vaesenclast(xmm0, xmm0, xmm30, Assembler::AVX_128bit);
    jmp(END_REMAINDER_LOOP);

    bind(AES192_REMAINDER);
    vaesenc(xmm0, xmm0, xmm30, Assembler::AVX_128bit);
    ev_load_key(xmm18, key, 11 * 16, xmm31);
    vaesenc(xmm0, xmm0, xmm18, Assembler::AVX_128bit);
    ev_load_key(xmm5, key, 12 * 16, xmm31);
    cmpl(rounds, 60);
    jcc(Assembler::aboveEqual, AES256_REMAINDER);
    vaesenclast(xmm0, xmm0, xmm5, Assembler::AVX_128bit);
    jmp(END_REMAINDER_LOOP);

    bind(AES256_REMAINDER);
    vaesenc(xmm0, xmm0, xmm5, Assembler::AVX_128bit);
    ev_load_key(xmm6, key, 13 * 16, xmm31);
    vaesenc(xmm0, xmm0, xmm6, Assembler::AVX_128bit);
    ev_load_key(xmm7, key, 14 * 16, xmm31);
    vaesenclast(xmm0, xmm0, xmm7, Assembler::AVX_128bit);

    bind(END_REMAINDER_LOOP);
    // If the length register is less than the blockSize i.e. 16
    // then we store only those bytes of the CT to the destination
    // corresponding to the length register value
    // extracting the exact number of bytes is handled by EXTRACT_TAILBYTES
    cmpl(len_reg, 16);
    jcc(Assembler::less, EXTRACT_TAILBYTES);
    subl(len_reg, 16);
    // After AES encode rounds, the encrypted block cipher lies in xmm0.
    // If the length register is equal to 16 bytes, store CT in dest after XOR operation.
    evpxorq(xmm0, xmm0, Address(src_addr, pos, Address::times_1, 0), Assembler::AVX_128bit);
    evmovdquq(Address(dest_addr, pos, Address::times_1, 0), xmm0, Assembler::AVX_128bit);
    addl(pos, 16);

    jmp(REMAINDER_LOOP);

    bind(EXTRACT_TAILBYTES);
    // Save encrypted counter value in xmm0 for next invocation, before XOR operation
    movdqu(Address(saved_encCounter_start, 0), xmm0);
    // XOR encryted block cipher in xmm0 with PT to produce CT
    evpxorq(xmm0, xmm0, Address(src_addr, pos, Address::times_1, 0), Assembler::AVX_128bit);
    // extract upto 15 bytes of CT from xmm0 as specified by length register
    testptr(len_reg, 8);
    jcc(Assembler::zero, EXTRACT_TAIL_4BYTES);
    pextrq(Address(dest_addr, pos), xmm0, 0);
    psrldq(xmm0, 8);
    addl(pos, 8);
    bind(EXTRACT_TAIL_4BYTES);
    testptr(len_reg, 4);
    jcc(Assembler::zero, EXTRACT_TAIL_2BYTES);
    pextrd(Address(dest_addr, pos), xmm0, 0);
    psrldq(xmm0, 4);
    addq(pos, 4);
    bind(EXTRACT_TAIL_2BYTES);
    testptr(len_reg, 2);
    jcc(Assembler::zero, EXTRACT_TAIL_1BYTE);
    pextrw(Address(dest_addr, pos), xmm0, 0);
    psrldq(xmm0, 2);
    addl(pos, 2);
    bind(EXTRACT_TAIL_1BYTE);
    testptr(len_reg, 1);
    jcc(Assembler::zero, END);
    pextrb(Address(dest_addr, pos), xmm0, 0);
    addl(pos, 1);

    bind(END);
    // If there are no tail bytes, store counter value and exit
    cmpl(len_reg, 0);
    jcc(Assembler::equal, STORE_CTR);
    movl(Address(used_addr, 0), len_reg);

    bind(STORE_CTR);
    //shuffle updated counter and store it
    vpshufb(xmm8, xmm8, xmm16, Assembler::AVX_128bit);
    movdqu(Address(counter, 0), xmm8);
    // Zero out counter and key registers
    evpxorq(xmm8, xmm8, xmm8, Assembler::AVX_512bit);
    evpxorq(xmm20, xmm20, xmm20, Assembler::AVX_512bit);
    evpxorq(xmm21, xmm21, xmm21, Assembler::AVX_512bit);
    evpxorq(xmm22, xmm22, xmm22, Assembler::AVX_512bit);
    evpxorq(xmm23, xmm23, xmm23, Assembler::AVX_512bit);
    evpxorq(xmm24, xmm24, xmm24, Assembler::AVX_512bit);
    evpxorq(xmm25, xmm25, xmm25, Assembler::AVX_512bit);
    evpxorq(xmm26, xmm26, xmm26, Assembler::AVX_512bit);
    evpxorq(xmm27, xmm27, xmm27, Assembler::AVX_512bit);
    evpxorq(xmm28, xmm28, xmm28, Assembler::AVX_512bit);
    evpxorq(xmm29, xmm29, xmm29, Assembler::AVX_512bit);
    evpxorq(xmm30, xmm30, xmm30, Assembler::AVX_512bit);
    cmpl(rounds, 44);
    jcc(Assembler::belowEqual, EXIT);
    evpxorq(xmm18, xmm18, xmm18, Assembler::AVX_512bit);
    evpxorq(xmm5, xmm5, xmm5, Assembler::AVX_512bit);
    cmpl(rounds, 52);
    jcc(Assembler::belowEqual, EXIT);
    evpxorq(xmm6, xmm6, xmm6, Assembler::AVX_512bit);
    evpxorq(xmm7, xmm7, xmm7, Assembler::AVX_512bit);
    bind(EXIT);
}

void MacroAssembler::gfmul_avx512(XMMRegister GH, XMMRegister HK) {
    const XMMRegister TMP1 = xmm0;
    const XMMRegister TMP2 = xmm1;
    const XMMRegister TMP3 = xmm2;

    evpclmulqdq(TMP1, GH, HK, 0x11, Assembler::AVX_512bit);
    evpclmulqdq(TMP2, GH, HK, 0x00, Assembler::AVX_512bit);
    evpclmulqdq(TMP3, GH, HK, 0x01, Assembler::AVX_512bit);
    evpclmulqdq(GH, GH, HK, 0x10, Assembler::AVX_512bit);
    evpxorq(GH, GH, TMP3, Assembler::AVX_512bit);
    vpsrldq(TMP3, GH, 8, Assembler::AVX_512bit);
    vpslldq(GH, GH, 8, Assembler::AVX_512bit);
    evpxorq(TMP1, TMP1, TMP3, Assembler::AVX_512bit);
    evpxorq(GH, GH, TMP2, Assembler::AVX_512bit);

    evmovdquq(TMP3, ExternalAddress(StubRoutines::x86::ghash_polynomial512_addr()), Assembler::AVX_512bit, r15);
    evpclmulqdq(TMP2, TMP3, GH, 0x01, Assembler::AVX_512bit);
    vpslldq(TMP2, TMP2, 8, Assembler::AVX_512bit);
    evpxorq(GH, GH, TMP2, Assembler::AVX_512bit);
    evpclmulqdq(TMP2, TMP3, GH, 0x00, Assembler::AVX_512bit);
    vpsrldq(TMP2, TMP2, 4, Assembler::AVX_512bit);
    evpclmulqdq(GH, TMP3, GH, 0x10, Assembler::AVX_512bit);
    vpslldq(GH, GH, 4, Assembler::AVX_512bit);
    vpternlogq(GH, 0x96, TMP1, TMP2, Assembler::AVX_512bit);
}

void MacroAssembler::generateHtbl_48_block_zmm(Register htbl) {
    const XMMRegister HK = xmm6;
    const XMMRegister ZT5 = xmm4;
    const XMMRegister ZT7 = xmm7;
    const XMMRegister ZT8 = xmm8;

    Label GFMUL_AVX512;

    movdqu(HK, Address(htbl, 0));
    movdqu(xmm10, ExternalAddress(StubRoutines::x86::ghash_long_swap_mask_addr()));
    vpshufb(HK, HK, xmm10, Assembler::AVX_128bit);

    movdqu(xmm11, ExternalAddress(StubRoutines::x86::ghash_polynomial512_addr() + 64)); // Poly
    movdqu(xmm12, ExternalAddress(StubRoutines::x86::ghash_polynomial512_addr() + 80)); // Twoone
    // Compute H ^ 2 from the input subkeyH
    movdqu(xmm2, xmm6);
    vpsllq(xmm6, xmm6, 1, Assembler::AVX_128bit);
    vpsrlq(xmm2, xmm2, 63, Assembler::AVX_128bit);
    movdqu(xmm1, xmm2);
    vpslldq(xmm2, xmm2, 8, Assembler::AVX_128bit);
    vpsrldq(xmm1, xmm1, 8, Assembler::AVX_128bit);
    vpor(xmm6, xmm6, xmm2, Assembler::AVX_128bit);

    vpshufd(xmm2, xmm1, 0x24, Assembler::AVX_128bit);
    vpcmpeqd(xmm2, xmm2, xmm12, AVX_128bit);
    vpand(xmm2, xmm2, xmm11, Assembler::AVX_128bit);
    vpxor(xmm6, xmm6, xmm2, Assembler::AVX_128bit);
    movdqu(Address(htbl, 16 * 56), xmm6); // H ^ 2
    // Compute the remaining three powers of H using XMM registers and all following powers using ZMM
    movdqu(ZT5, HK);
    vinserti32x4(ZT7, ZT7, HK, 3);

    gfmul_avx512(ZT5, HK);
    movdqu(Address(htbl, 16 * 55), ZT5); // H ^ 2 * 2
    vinserti32x4(ZT7, ZT7, ZT5, 2);

    gfmul_avx512(ZT5, HK);
    movdqu(Address(htbl, 16 * 54), ZT5); // H ^ 2 * 3
    vinserti32x4(ZT7, ZT7, ZT5, 1);

    gfmul_avx512(ZT5, HK);
    movdqu(Address(htbl, 16 * 53), ZT5); // H ^ 2 * 4
    vinserti32x4(ZT7, ZT7, ZT5, 0);

    evshufi64x2(ZT5, ZT5, ZT5, 0x00, Assembler::AVX_512bit);
    evmovdquq(ZT8, ZT7, Assembler::AVX_512bit);
    gfmul_avx512(ZT7, ZT5);
    evmovdquq(Address(htbl, 16 * 49), ZT7, Assembler::AVX_512bit);
    evshufi64x2(ZT5, ZT7, ZT7, 0x00, Assembler::AVX_512bit);
    gfmul_avx512(ZT8, ZT5);
    evmovdquq(Address(htbl, 16 * 45), ZT8, Assembler::AVX_512bit);
    gfmul_avx512(ZT7, ZT5);
    evmovdquq(Address(htbl, 16 * 41), ZT7, Assembler::AVX_512bit);
    gfmul_avx512(ZT8, ZT5);
    evmovdquq(Address(htbl, 16 * 37), ZT8, Assembler::AVX_512bit);
    gfmul_avx512(ZT7, ZT5);
    evmovdquq(Address(htbl, 16 * 33), ZT7, Assembler::AVX_512bit);
    gfmul_avx512(ZT8, ZT5);
    evmovdquq(Address(htbl, 16 * 29), ZT8, Assembler::AVX_512bit);
    gfmul_avx512(ZT7, ZT5);
    evmovdquq(Address(htbl, 16 * 25), ZT7, Assembler::AVX_512bit);
    gfmul_avx512(ZT8, ZT5);
    evmovdquq(Address(htbl, 16 * 21), ZT8, Assembler::AVX_512bit);
    gfmul_avx512(ZT7, ZT5);
    evmovdquq(Address(htbl, 16 * 17), ZT7, Assembler::AVX_512bit);
    gfmul_avx512(ZT8, ZT5);
    evmovdquq(Address(htbl, 16 * 13), ZT8, Assembler::AVX_512bit);
    gfmul_avx512(ZT7, ZT5);
    evmovdquq(Address(htbl, 16 * 9), ZT7, Assembler::AVX_512bit);
    ret(0);
}

#define vclmul_reduce(out, poly, hi128, lo128, tmp0, tmp1) \
evpclmulqdq(tmp0, poly, lo128, 0x01, Assembler::AVX_512bit); \
vpslldq(tmp0, tmp0, 8, Assembler::AVX_512bit); \
evpxorq(tmp0, lo128, tmp0, Assembler::AVX_512bit); \
evpclmulqdq(tmp1, poly, tmp0, 0x00, Assembler::AVX_512bit); \
vpsrldq(tmp1, tmp1, 4, Assembler::AVX_512bit); \
evpclmulqdq(out, poly, tmp0, 0x10, Assembler::AVX_512bit); \
vpslldq(out, out, 4, Assembler::AVX_512bit); \
vpternlogq(out, 0x96, tmp1, hi128, Assembler::AVX_512bit); \

#define vhpxori4x128(reg, tmp) \
vextracti64x4(tmp, reg, 1); \
evpxorq(reg, reg, tmp, Assembler::AVX_256bit); \
vextracti32x4(tmp, reg, 1); \
evpxorq(reg, reg, tmp, Assembler::AVX_128bit); \

#define roundEncode(key, dst1, dst2, dst3, dst4) \
vaesenc(dst1, dst1, key, Assembler::AVX_512bit); \
vaesenc(dst2, dst2, key, Assembler::AVX_512bit); \
vaesenc(dst3, dst3, key, Assembler::AVX_512bit); \
vaesenc(dst4, dst4, key, Assembler::AVX_512bit); \

#define lastroundEncode(key, dst1, dst2, dst3, dst4) \
vaesenclast(dst1, dst1, key, Assembler::AVX_512bit); \
vaesenclast(dst2, dst2, key, Assembler::AVX_512bit); \
vaesenclast(dst3, dst3, key, Assembler::AVX_512bit); \
vaesenclast(dst4, dst4, key, Assembler::AVX_512bit); \

#define storeData(dst, position, src1, src2, src3, src4) \
evmovdquq(Address(dst, position, Address::times_1, 0 * 64), src1, Assembler::AVX_512bit); \
evmovdquq(Address(dst, position, Address::times_1, 1 * 64), src2, Assembler::AVX_512bit); \
evmovdquq(Address(dst, position, Address::times_1, 2 * 64), src3, Assembler::AVX_512bit); \
evmovdquq(Address(dst, position, Address::times_1, 3 * 64), src4, Assembler::AVX_512bit); \

#define loadData(src, position, dst1, dst2, dst3, dst4) \
evmovdquq(dst1, Address(src, position, Address::times_1, 0 * 64), Assembler::AVX_512bit); \
evmovdquq(dst2, Address(src, position, Address::times_1, 1 * 64), Assembler::AVX_512bit); \
evmovdquq(dst3, Address(src, position, Address::times_1, 2 * 64), Assembler::AVX_512bit); \
evmovdquq(dst4, Address(src, position, Address::times_1, 3 * 64), Assembler::AVX_512bit); \

#define carrylessMultiply(dst00, dst01, dst10, dst11, ghdata, hkey) \
evpclmulqdq(dst00, ghdata, hkey, 0x00, Assembler::AVX_512bit); \
evpclmulqdq(dst01, ghdata, hkey, 0x01, Assembler::AVX_512bit); \
evpclmulqdq(dst10, ghdata, hkey, 0x10, Assembler::AVX_512bit); \
evpclmulqdq(dst11, ghdata, hkey, 0x11, Assembler::AVX_512bit); \

#define shuffleExorRnd1Key(dst0, dst1, dst2, dst3, shufmask, rndkey) \
vpshufb(dst0, dst0, shufmask, Assembler::AVX_512bit); \
evpxorq(dst0, dst0, rndkey, Assembler::AVX_512bit); \
vpshufb(dst1, dst1, shufmask, Assembler::AVX_512bit); \
evpxorq(dst1, dst1, rndkey, Assembler::AVX_512bit); \
vpshufb(dst2, dst2, shufmask, Assembler::AVX_512bit); \
evpxorq(dst2, dst2, rndkey, Assembler::AVX_512bit); \
vpshufb(dst3, dst3, shufmask, Assembler::AVX_512bit); \
evpxorq(dst3, dst3, rndkey, Assembler::AVX_512bit); \

#define xorBeforeStore(dst0, dst1, dst2, dst3, src0, src1, src2, src3) \
evpxorq(dst0, dst0, src0, Assembler::AVX_512bit); \
evpxorq(dst1, dst1, src1, Assembler::AVX_512bit); \
evpxorq(dst2, dst2, src2, Assembler::AVX_512bit); \
evpxorq(dst3, dst3, src3, Assembler::AVX_512bit); \

#define xorGHASH(dst0, dst1, dst2, dst3, src02, src03, src12, src13, src22, src23, src32, src33) \
vpternlogq(dst0, 0x96, src02, src03, Assembler::AVX_512bit); \
vpternlogq(dst1, 0x96, src12, src13, Assembler::AVX_512bit); \
vpternlogq(dst2, 0x96, src22, src23, Assembler::AVX_512bit); \
vpternlogq(dst3, 0x96, src32, src33, Assembler::AVX_512bit); \

void MacroAssembler::ghash16_encrypt16_parallel(Register key, Register subkeyHtbl, XMMRegister ctr_blockx, XMMRegister aad_hashx,
    Register in, Register out, Register data, Register pos, bool first_time_reduction, XMMRegister addmask, bool ghash_input, Register rounds,
    Register ghash_pos, bool final_reduction, int i, XMMRegister counter_inc_mask) {

    Label AES_192, AES_256, LAST_AES_RND;
    const XMMRegister ZTMP0 = xmm0;
    const XMMRegister ZTMP1 = xmm3;
    const XMMRegister ZTMP2 = xmm4;
    const XMMRegister ZTMP3 = xmm5;
    const XMMRegister ZTMP5 = xmm7;
    const XMMRegister ZTMP6 = xmm10;
    const XMMRegister ZTMP7 = xmm11;
    const XMMRegister ZTMP8 = xmm12;
    const XMMRegister ZTMP9 = xmm13;
    const XMMRegister ZTMP10 = xmm15;
    const XMMRegister ZTMP11 = xmm16;
    const XMMRegister ZTMP12 = xmm17;

    const XMMRegister ZTMP13 = xmm19;
    const XMMRegister ZTMP14 = xmm20;
    const XMMRegister ZTMP15 = xmm21;
    const XMMRegister ZTMP16 = xmm30;
    const XMMRegister ZTMP17 = xmm31;
    const XMMRegister ZTMP18 = xmm1;
    const XMMRegister ZTMP19 = xmm2;
    const XMMRegister ZTMP20 = xmm8;
    const XMMRegister ZTMP21 = xmm22;
    const XMMRegister ZTMP22 = xmm23;

    // Pre increment counters
    vpaddd(ZTMP0, ctr_blockx, counter_inc_mask, Assembler::AVX_512bit);
    vpaddd(ZTMP1, ZTMP0, counter_inc_mask, Assembler::AVX_512bit);
    vpaddd(ZTMP2, ZTMP1, counter_inc_mask, Assembler::AVX_512bit);
    vpaddd(ZTMP3, ZTMP2, counter_inc_mask, Assembler::AVX_512bit);
    // Save counter value
    evmovdquq(ctr_blockx, ZTMP3, Assembler::AVX_512bit);

    // Reuse ZTMP17 / ZTMP18 for loading AES Keys
    // Pre-load AES round keys
    ev_load_key(ZTMP17, key, 0, xmm29);
    ev_load_key(ZTMP18, key, 1 * 16, xmm29);

    // ZTMP19 & ZTMP20 used for loading hash key
    // Pre-load hash key
    evmovdquq(ZTMP19, Address(subkeyHtbl, i * 64 + 144), Assembler::AVX_512bit);
    evmovdquq(ZTMP20, Address(subkeyHtbl, ++i * 64 + 144), Assembler::AVX_512bit);
    // Load data for computing ghash
    evmovdquq(ZTMP21, Address(data, ghash_pos, Address::times_1, 0 * 64), Assembler::AVX_512bit);
    vpshufb(ZTMP21, ZTMP21, xmm24, Assembler::AVX_512bit);

    // Xor cipher block 0 with input ghash, if available
    if (ghash_input) {
        evpxorq(ZTMP21, ZTMP21, aad_hashx, Assembler::AVX_512bit);
    }
    // Load data for computing ghash
    evmovdquq(ZTMP22, Address(data, ghash_pos, Address::times_1, 1 * 64), Assembler::AVX_512bit);
    vpshufb(ZTMP22, ZTMP22, xmm24, Assembler::AVX_512bit);

    // stitch AES rounds with GHASH
    // AES round 0, xmm24 has shuffle mask
    shuffleExorRnd1Key(ZTMP0, ZTMP1, ZTMP2, ZTMP3, xmm24, ZTMP17);
    // Reuse ZTMP17 / ZTMP18 for loading remaining AES Keys
    ev_load_key(ZTMP17, key, 2 * 16, xmm29);
    // GHASH 4 blocks
    carrylessMultiply(ZTMP6, ZTMP7, ZTMP8, ZTMP5, ZTMP21, ZTMP19);
    // Load the next hkey and Ghash data
    evmovdquq(ZTMP19, Address(subkeyHtbl, ++i * 64 + 144), Assembler::AVX_512bit);
    evmovdquq(ZTMP21, Address(data, ghash_pos, Address::times_1, 2 * 64), Assembler::AVX_512bit);
    vpshufb(ZTMP21, ZTMP21, xmm24, Assembler::AVX_512bit);

    // AES round 1
    roundEncode(ZTMP18, ZTMP0, ZTMP1, ZTMP2, ZTMP3);
    ev_load_key(ZTMP18, key, 3 * 16, xmm29);

    // GHASH 4 blocks(11 to 8)
    carrylessMultiply(ZTMP10, ZTMP12, ZTMP11, ZTMP9, ZTMP22, ZTMP20);
    // Load the next hkey and GDATA
    evmovdquq(ZTMP20, Address(subkeyHtbl, ++i * 64 + 144), Assembler::AVX_512bit);
    evmovdquq(ZTMP22, Address(data, ghash_pos, Address::times_1, 3 * 64), Assembler::AVX_512bit);
    vpshufb(ZTMP22, ZTMP22, xmm24, Assembler::AVX_512bit);

    // AES round 2
    roundEncode(ZTMP17, ZTMP0, ZTMP1, ZTMP2, ZTMP3);
    ev_load_key(ZTMP17, key, 4 * 16, xmm29);

    // GHASH 4 blocks(7 to 4)
    carrylessMultiply(ZTMP14, ZTMP16, ZTMP15, ZTMP13, ZTMP21, ZTMP19);
    // AES rounds 3
    roundEncode(ZTMP18, ZTMP0, ZTMP1, ZTMP2, ZTMP3);
    ev_load_key(ZTMP18, key, 5 * 16, xmm29);

    // Gather(XOR) GHASH for 12 blocks
    xorGHASH(ZTMP5, ZTMP6, ZTMP8, ZTMP7, ZTMP9, ZTMP13, ZTMP10, ZTMP14, ZTMP12, ZTMP16, ZTMP11, ZTMP15);

    // AES rounds 4
    roundEncode(ZTMP17, ZTMP0, ZTMP1, ZTMP2, ZTMP3);
    ev_load_key(ZTMP17, key, 6 * 16, xmm29);

    // load plain / cipher text(recycle registers)
    loadData(in, pos, ZTMP13, ZTMP14, ZTMP15, ZTMP16);

    // AES rounds 5
    roundEncode(ZTMP18, ZTMP0, ZTMP1, ZTMP2, ZTMP3);
    ev_load_key(ZTMP18, key, 7 * 16, xmm29);
    // GHASH 4 blocks(3 to 0)
    carrylessMultiply(ZTMP10, ZTMP12, ZTMP11, ZTMP9, ZTMP22, ZTMP20);

    //  AES round 6
    roundEncode(ZTMP17, ZTMP0, ZTMP1, ZTMP2, ZTMP3);
    ev_load_key(ZTMP17, key, 8 * 16, xmm29);

    // gather GHASH in ZTMP6(low) and ZTMP5(high)
    if (first_time_reduction) {
        vpternlogq(ZTMP7, 0x96, ZTMP8, ZTMP12, Assembler::AVX_512bit);
        evpxorq(xmm25, ZTMP7, ZTMP11, Assembler::AVX_512bit);
        evpxorq(xmm27, ZTMP5, ZTMP9, Assembler::AVX_512bit);
        evpxorq(xmm26, ZTMP6, ZTMP10, Assembler::AVX_512bit);
    }
    else if (!first_time_reduction && !final_reduction) {
        xorGHASH(ZTMP7, xmm25, xmm27, xmm26, ZTMP8, ZTMP12, ZTMP7, ZTMP11, ZTMP5, ZTMP9, ZTMP6, ZTMP10);
    }

    if (final_reduction) {
        // Phase one: Add mid products together
        // Also load polynomial constant for reduction
        vpternlogq(ZTMP7, 0x96, ZTMP8, ZTMP12, Assembler::AVX_512bit);
        vpternlogq(ZTMP7, 0x96, xmm25, ZTMP11, Assembler::AVX_512bit);
        vpsrldq(ZTMP11, ZTMP7, 8, Assembler::AVX_512bit);
        vpslldq(ZTMP7, ZTMP7, 8, Assembler::AVX_512bit);
        evmovdquq(ZTMP12, ExternalAddress(StubRoutines::x86::ghash_polynomial512_addr()), Assembler::AVX_512bit, rbx);
    }
    // AES round 7
    roundEncode(ZTMP18, ZTMP0, ZTMP1, ZTMP2, ZTMP3);
    ev_load_key(ZTMP18, key, 9 * 16, xmm29);
    if (final_reduction) {
        vpternlogq(ZTMP5, 0x96, ZTMP9, ZTMP11, Assembler::AVX_512bit);
        evpxorq(ZTMP5, ZTMP5, xmm27, Assembler::AVX_512bit);
        vpternlogq(ZTMP6, 0x96, ZTMP10, ZTMP7, Assembler::AVX_512bit);
        evpxorq(ZTMP6, ZTMP6, xmm26, Assembler::AVX_512bit);
    }
    // AES round 8
    roundEncode(ZTMP17, ZTMP0, ZTMP1, ZTMP2, ZTMP3);
    ev_load_key(ZTMP17, key, 10 * 16, xmm29);

    // Horizontal xor of low and high 4*128
    if (final_reduction) {
        vhpxori4x128(ZTMP5, ZTMP9);
        vhpxori4x128(ZTMP6, ZTMP10);
    }
    // AES round 9
    roundEncode(ZTMP18, ZTMP0, ZTMP1, ZTMP2, ZTMP3);
    // First phase of reduction
    if (final_reduction) {
        evpclmulqdq(ZTMP10, ZTMP12, ZTMP6, 0x01, Assembler::AVX_128bit);
        vpslldq(ZTMP10, ZTMP10, 8, Assembler::AVX_128bit);
        evpxorq(ZTMP10, ZTMP6, ZTMP10, Assembler::AVX_128bit);
    }
    cmpl(rounds, 52);
    jcc(Assembler::greaterEqual, AES_192);
    jmp(LAST_AES_RND);
    // AES rounds upto 11 (AES192) or 13 (AES256)
    bind(AES_192);
    roundEncode(ZTMP17, ZTMP0, ZTMP1, ZTMP2, ZTMP3);
    ev_load_key(ZTMP18, key, 11 * 16, xmm29);
    roundEncode(ZTMP18, ZTMP0, ZTMP1, ZTMP2, ZTMP3);
    ev_load_key(ZTMP17, key, 12 * 16, xmm29);
    cmpl(rounds, 60);
    jcc(Assembler::aboveEqual, AES_256);
    jmp(LAST_AES_RND);

    bind(AES_256);
    roundEncode(ZTMP17, ZTMP0, ZTMP1, ZTMP2, ZTMP3);
    ev_load_key(ZTMP18, key, 13 * 16, xmm29);
    roundEncode(ZTMP18, ZTMP0, ZTMP1, ZTMP2, ZTMP3);
    ev_load_key(ZTMP17, key, 14 * 16, xmm29);

    bind(LAST_AES_RND);
    // Second phase of reduction
    if (final_reduction) {
        evpclmulqdq(ZTMP9, ZTMP12, ZTMP10, 0x00, Assembler::AVX_128bit);
        vpsrldq(ZTMP9, ZTMP9, 4, Assembler::AVX_128bit); // Shift-R 1-DW to obtain 2-DWs shift-R
        evpclmulqdq(ZTMP11, ZTMP12, ZTMP10, 0x10, Assembler::AVX_128bit);
        vpslldq(ZTMP11, ZTMP11, 4, Assembler::AVX_128bit); // Shift-L 1-DW for result
        // ZTMP5 = ZTMP5 X ZTMP11 X ZTMP9
        vpternlogq(ZTMP5, 0x96, ZTMP11, ZTMP9, Assembler::AVX_128bit);
    }
    // Last AES round
    lastroundEncode(ZTMP17, ZTMP0, ZTMP1, ZTMP2, ZTMP3);
    // XOR against plain / cipher text
    xorBeforeStore(ZTMP0, ZTMP1, ZTMP2, ZTMP3, ZTMP13, ZTMP14, ZTMP15, ZTMP16);
    // store cipher / plain text
    storeData(out, pos, ZTMP0, ZTMP1, ZTMP2, ZTMP3);
}

void MacroAssembler::aesgcm_encrypt(Register in, Register len, Register ct, Register out, Register key,
                                    Register state, Register subkeyHtbl, Register counter) {

    Label ENC_DEC_DONE, GENERATE_HTBL_48_BLKS, AES_192, AES_256, STORE_CT, GHASH_LAST_32,
          AES_32_BLOCKS, GHASH_AES_PARALLEL, LOOP, ACCUMULATE, GHASH_16_AES_16;
    const XMMRegister CTR_BLOCKx = xmm9;
    const XMMRegister AAD_HASHx = xmm14;
    const Register pos = rax;
    const Register rounds = r15;
    Register ghash_pos;
#ifndef _WIN64
    ghash_pos = r14;
#else
    ghash_pos = r11;
#endif // !_WIN64
    const XMMRegister ZTMP0 = xmm0;
    const XMMRegister ZTMP1 = xmm3;
    const XMMRegister ZTMP2 = xmm4;
    const XMMRegister ZTMP3 = xmm5;
    const XMMRegister ZTMP4 = xmm6;
    const XMMRegister ZTMP5 = xmm7;
    const XMMRegister ZTMP6 = xmm10;
    const XMMRegister ZTMP7 = xmm11;
    const XMMRegister ZTMP8 = xmm12;
    const XMMRegister ZTMP9 = xmm13;
    const XMMRegister ZTMP10 = xmm15;
    const XMMRegister ZTMP11 = xmm16;
    const XMMRegister ZTMP12 = xmm17;
    const XMMRegister ZTMP13 = xmm19;
    const XMMRegister ZTMP14 = xmm20;
    const XMMRegister ZTMP15 = xmm21;
    const XMMRegister ZTMP16 = xmm30;
    const XMMRegister COUNTER_INC_MASK = xmm18;

    movl(pos, 0); // Total length processed
    // Min data size processed = 768 bytes
    cmpl(len, 768);
    jcc(Assembler::less, ENC_DEC_DONE);

    // Generate 48 constants for htbl
    call(GENERATE_HTBL_48_BLKS, relocInfo::none);
    int index = 0; // Index for choosing subkeyHtbl entry
    movl(ghash_pos, 0); // Pointer for ghash read and store operations

    // Move initial counter value and STATE value into variables
    movdqu(CTR_BLOCKx, Address(counter, 0));
    movdqu(AAD_HASHx, Address(state, 0));
    // Load lswap mask for ghash
    movdqu(xmm24, ExternalAddress(StubRoutines::x86::ghash_long_swap_mask_addr()), rbx);
    // Shuffle input state using lswap mask
    vpshufb(AAD_HASHx, AAD_HASHx, xmm24, Assembler::AVX_128bit);

    // Compute #rounds for AES based on the length of the key array
    movl(rounds, Address(key, arrayOopDesc::length_offset_in_bytes() - arrayOopDesc::base_offset_in_bytes(T_INT)));

    // Broadcast counter value to 512 bit register
    evshufi64x2(CTR_BLOCKx, CTR_BLOCKx, CTR_BLOCKx, 0, Assembler::AVX_512bit);
    // Load counter shuffle mask
    evmovdquq(xmm24, ExternalAddress(StubRoutines::x86::counter_mask_addr()), Assembler::AVX_512bit, rbx);
    // Shuffle counter
    vpshufb(CTR_BLOCKx, CTR_BLOCKx, xmm24, Assembler::AVX_512bit);

    // Load mask for incrementing counter
    evmovdquq(COUNTER_INC_MASK, ExternalAddress(StubRoutines::x86::counter_mask_addr() + 128), Assembler::AVX_512bit, rbx);
    // Pre-increment counter
    vpaddd(ZTMP5, CTR_BLOCKx, ExternalAddress(StubRoutines::x86::counter_mask_addr() + 64), Assembler::AVX_512bit, rbx);
    vpaddd(ZTMP6, ZTMP5, COUNTER_INC_MASK, Assembler::AVX_512bit);
    vpaddd(ZTMP7, ZTMP6, COUNTER_INC_MASK, Assembler::AVX_512bit);
    vpaddd(ZTMP8, ZTMP7, COUNTER_INC_MASK, Assembler::AVX_512bit);

    // Begin 32 blocks of AES processing
    bind(AES_32_BLOCKS);
    // Save incremented counter before overwriting it with AES data
    evmovdquq(CTR_BLOCKx, ZTMP8, Assembler::AVX_512bit);

    // Move 256 bytes of data
    loadData(in, pos, ZTMP0, ZTMP1, ZTMP2, ZTMP3);
    // Load key shuffle mask
    movdqu(xmm29, ExternalAddress(StubRoutines::x86::key_shuffle_mask_addr()), rbx);
    // Load 0th AES round key
    ev_load_key(ZTMP4, key, 0, xmm29);
    // AES-ROUND0, xmm24 has the shuffle mask
    shuffleExorRnd1Key(ZTMP5, ZTMP6, ZTMP7, ZTMP8, xmm24, ZTMP4);

    for (int j = 1; j < 10; j++) {
        ev_load_key(ZTMP4, key, j * 16, xmm29);
        roundEncode(ZTMP4, ZTMP5, ZTMP6, ZTMP7, ZTMP8);
    }
    ev_load_key(ZTMP4, key, 10 * 16, xmm29);
    // AES rounds upto 11 (AES192) or 13 (AES256)
    cmpl(rounds, 52);
    jcc(Assembler::greaterEqual, AES_192);
    lastroundEncode(ZTMP4, ZTMP5, ZTMP6, ZTMP7, ZTMP8);
    jmp(STORE_CT);

    bind(AES_192);
    roundEncode(ZTMP4, ZTMP5, ZTMP6, ZTMP7, ZTMP8);
    ev_load_key(ZTMP4, key, 11 * 16, xmm29);
    roundEncode(ZTMP4, ZTMP5, ZTMP6, ZTMP7, ZTMP8);
    cmpl(rounds, 60);
    jcc(Assembler::aboveEqual, AES_256);
    ev_load_key(ZTMP4, key, 12 * 16, xmm29);
    lastroundEncode(ZTMP4, ZTMP5, ZTMP6, ZTMP7, ZTMP8);
    jmp(STORE_CT);

    bind(AES_256);
    ev_load_key(ZTMP4, key, 12 * 16, xmm29);
    roundEncode(ZTMP4, ZTMP5, ZTMP6, ZTMP7, ZTMP8);
    ev_load_key(ZTMP4, key, 13 * 16, xmm29);
    roundEncode(ZTMP4, ZTMP5, ZTMP6, ZTMP7, ZTMP8);
    ev_load_key(ZTMP4, key, 14 * 16, xmm29);
    // Last AES round
    lastroundEncode(ZTMP4, ZTMP5, ZTMP6, ZTMP7, ZTMP8);

    bind(STORE_CT);
    // Xor the encrypted key with PT to obtain CT
    xorBeforeStore(ZTMP5, ZTMP6, ZTMP7, ZTMP8, ZTMP0, ZTMP1, ZTMP2, ZTMP3);
    storeData(out, pos, ZTMP5, ZTMP6, ZTMP7, ZTMP8);
    // 16 blocks encryption completed
    addl(pos, 256);
    cmpl(pos, 512);
    jcc(Assembler::aboveEqual, GHASH_AES_PARALLEL);
    vpaddd(ZTMP5, CTR_BLOCKx, COUNTER_INC_MASK, Assembler::AVX_512bit);
    vpaddd(ZTMP6, ZTMP5, COUNTER_INC_MASK, Assembler::AVX_512bit);
    vpaddd(ZTMP7, ZTMP6, COUNTER_INC_MASK, Assembler::AVX_512bit);
    vpaddd(ZTMP8, ZTMP7, COUNTER_INC_MASK, Assembler::AVX_512bit);
    jmp(AES_32_BLOCKS);

    bind(GHASH_AES_PARALLEL);
    // Ghash16_encrypt16_parallel takes place in the order with three reduction values:
    // 1) First time -> cipher xor input ghash
    // 2) No reduction -> accumulate multiplication values
    // 3) Final reduction post 48 blocks -> new ghash value is computed for the next round
    // Reduction value = first time
    ghash16_encrypt16_parallel(key, subkeyHtbl, CTR_BLOCKx, AAD_HASHx, in, out, ct, pos, true, xmm24, true, rounds, ghash_pos, false, index, COUNTER_INC_MASK);
    addl(pos, 256);
    addl(ghash_pos, 256);
    index += 4;

    // At this point we have processed 768 bytes of AES and 256 bytes of GHASH.
    // If the remaining length is less than 768, process remaining 512 bytes of ghash in GHASH_LAST_32 code
    subl(len, 768);
    cmpl(len, 768);
    jcc(Assembler::less, GHASH_LAST_32);

    // AES 16 blocks and GHASH 16 blocks in parallel
    // For multiples of 48 blocks we will do ghash16_encrypt16 interleaved multiple times
    // Reduction value = no reduction means that the carryless multiplication values are accumulated for further calculations
    // Each call uses 4 subkeyHtbl values, so increment the index by 4.
    bind(GHASH_16_AES_16);
    // Reduction value = no reduction
    ghash16_encrypt16_parallel(key, subkeyHtbl, CTR_BLOCKx, AAD_HASHx, in, out, ct, pos, false, xmm24, false, rounds, ghash_pos, false, index, COUNTER_INC_MASK);
    addl(pos, 256);
    addl(ghash_pos, 256);
    index += 4;
    // Reduction value = final reduction means that the accumulated values have to be reduced as we have completed 48 blocks of ghash
    ghash16_encrypt16_parallel(key, subkeyHtbl, CTR_BLOCKx, AAD_HASHx, in, out, ct, pos, false, xmm24, false, rounds, ghash_pos, true, index, COUNTER_INC_MASK);
    addl(pos, 256);
    addl(ghash_pos, 256);
    // Calculated ghash value needs to be moved to AAD_HASHX so that we can restart the ghash16-aes16 pipeline
    movdqu(AAD_HASHx, ZTMP5);
    index = 0; // Reset subkeyHtbl index

    // Restart the pipeline
    // Reduction value = first time
    ghash16_encrypt16_parallel(key, subkeyHtbl, CTR_BLOCKx, AAD_HASHx, in, out, ct, pos, true, xmm24, true, rounds, ghash_pos, false, index, COUNTER_INC_MASK);
    addl(pos, 256);
    addl(ghash_pos, 256);
    index += 4;

    subl(len, 768);
    cmpl(len, 768);
    jcc(Assembler::greaterEqual, GHASH_16_AES_16);

    // GHASH last 32 blocks processed here
    // GHASH products accumulated in ZMM27, ZMM25 and ZMM26 during GHASH16-AES16 operation is used
    bind(GHASH_LAST_32);
    // Use rbx as a pointer to the htbl; For last 32 blocks of GHASH, use key# 4-11 entry in subkeyHtbl
    movl(rbx, 256);
    // Load cipher blocks
    evmovdquq(ZTMP13, Address(ct, ghash_pos, Address::times_1, 0 * 64), Assembler::AVX_512bit);
    evmovdquq(ZTMP14, Address(ct, ghash_pos, Address::times_1, 1 * 64), Assembler::AVX_512bit);
    vpshufb(ZTMP13, ZTMP13, xmm24, Assembler::AVX_512bit);
    vpshufb(ZTMP14, ZTMP14, xmm24, Assembler::AVX_512bit);
    // Load ghash keys
    evmovdquq(ZTMP15, Address(subkeyHtbl, rbx, Address::times_1, 0 * 64 + 144), Assembler::AVX_512bit);
    evmovdquq(ZTMP16, Address(subkeyHtbl, rbx, Address::times_1, 1 * 64 + 144), Assembler::AVX_512bit);

    // Ghash blocks 0 - 3
    carrylessMultiply(ZTMP2, ZTMP3, ZTMP4, ZTMP1, ZTMP13, ZTMP15);
    // Ghash blocks 4 - 7
    carrylessMultiply(ZTMP6, ZTMP7, ZTMP8, ZTMP5, ZTMP14, ZTMP16);

    vpternlogq(ZTMP1, 0x96, ZTMP5, xmm27, Assembler::AVX_512bit); // ZTMP1 = ZTMP1 + ZTMP5 + zmm27
    vpternlogq(ZTMP2, 0x96, ZTMP6, xmm26, Assembler::AVX_512bit); // ZTMP2 = ZTMP2 + ZTMP6 + zmm26
    vpternlogq(ZTMP3, 0x96, ZTMP7, xmm25, Assembler::AVX_512bit); // ZTMP3 = ZTMP3 + ZTMP7 + zmm25
    evpxorq(ZTMP4, ZTMP4, ZTMP8, Assembler::AVX_512bit);          // ZTMP4 = ZTMP4 + ZTMP8

    addl(ghash_pos, 128);
    addl(rbx, 128);

    // Ghash remaining blocks
    bind(LOOP);
    cmpl(ghash_pos, pos);
    jcc(Assembler::aboveEqual, ACCUMULATE);
    // Load next cipher blocks and corresponding ghash keys
    evmovdquq(ZTMP13, Address(ct, ghash_pos, Address::times_1, 0 * 64), Assembler::AVX_512bit);
    evmovdquq(ZTMP14, Address(ct, ghash_pos, Address::times_1, 1 * 64), Assembler::AVX_512bit);
    vpshufb(ZTMP13, ZTMP13, xmm24, Assembler::AVX_512bit);
    vpshufb(ZTMP14, ZTMP14, xmm24, Assembler::AVX_512bit);
    evmovdquq(ZTMP15, Address(subkeyHtbl, rbx, Address::times_1, 0 * 64 + 144), Assembler::AVX_512bit);
    evmovdquq(ZTMP16, Address(subkeyHtbl, rbx, Address::times_1, 1 * 64 + 144), Assembler::AVX_512bit);

    // ghash blocks 0 - 3
    carrylessMultiply(ZTMP6, ZTMP7, ZTMP8, ZTMP5, ZTMP13, ZTMP15);

    // ghash blocks 4 - 7
    carrylessMultiply(ZTMP10, ZTMP11, ZTMP12, ZTMP9, ZTMP14, ZTMP16);

    // update sums
    // ZTMP1 = ZTMP1 + ZTMP5 + ZTMP9
    // ZTMP2 = ZTMP2 + ZTMP6 + ZTMP10
    // ZTMP3 = ZTMP3 + ZTMP7 xor ZTMP11
    // ZTMP4 = ZTMP4 + ZTMP8 xor ZTMP12
    xorGHASH(ZTMP1, ZTMP2, ZTMP3, ZTMP4, ZTMP5, ZTMP9, ZTMP6, ZTMP10, ZTMP7, ZTMP11, ZTMP8, ZTMP12);
    addl(ghash_pos, 128);
    addl(rbx, 128);
    jmp(LOOP);

    // Integrate ZTMP3/ZTMP4 into ZTMP1 and ZTMP2
    bind(ACCUMULATE);
    evpxorq(ZTMP3, ZTMP3, ZTMP4, Assembler::AVX_512bit);
    vpsrldq(ZTMP7, ZTMP3, 8, Assembler::AVX_512bit);
    vpslldq(ZTMP8, ZTMP3, 8, Assembler::AVX_512bit);
    evpxorq(ZTMP1, ZTMP1, ZTMP7, Assembler::AVX_512bit);
    evpxorq(ZTMP2, ZTMP2, ZTMP8, Assembler::AVX_512bit);

    // Add ZTMP1 and ZTMP2 128 - bit words horizontally
    vhpxori4x128(ZTMP1, ZTMP11);
    vhpxori4x128(ZTMP2, ZTMP12);
    // Load reduction polynomial and compute final reduction
    evmovdquq(ZTMP15, ExternalAddress(StubRoutines::x86::ghash_polynomial512_addr()), Assembler::AVX_512bit, rbx);
    vclmul_reduce(AAD_HASHx, ZTMP15, ZTMP1, ZTMP2, ZTMP3, ZTMP4);

    // Pre-increment counter for next operation
    vpaddd(CTR_BLOCKx, CTR_BLOCKx, xmm18, Assembler::AVX_128bit);
    // Shuffle counter and save the updated value
    vpshufb(CTR_BLOCKx, CTR_BLOCKx, xmm24, Assembler::AVX_512bit);
    movdqu(Address(counter, 0), CTR_BLOCKx);
    // Load ghash lswap mask
    movdqu(xmm24, ExternalAddress(StubRoutines::x86::ghash_long_swap_mask_addr()));
    // Shuffle ghash using lbswap_mask and store it
    vpshufb(AAD_HASHx, AAD_HASHx, xmm24, Assembler::AVX_128bit);
    movdqu(Address(state, 0), AAD_HASHx);
    jmp(ENC_DEC_DONE);

    bind(GENERATE_HTBL_48_BLKS);
    generateHtbl_48_block_zmm(subkeyHtbl);

    bind(ENC_DEC_DONE);
    movq(rax, pos);
}

#endif // _LP64