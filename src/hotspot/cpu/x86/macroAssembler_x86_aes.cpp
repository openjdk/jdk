/*
* Copyright (c) 2018, Intel Corporation.
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
#endif // _LP64
