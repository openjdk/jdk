/*
* Copyright (c) 2019, 2023, Intel Corporation. All rights reserved.
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
#include "stubGenerator_x86_64.hpp"

#define __ _masm->

#ifdef PRODUCT
#define BLOCK_COMMENT(str) /* nothing */
#else
#define BLOCK_COMMENT(str) __ block_comment(str)
#endif // PRODUCT

#define BIND(label) bind(label); BLOCK_COMMENT(#label ":")

// Constants

const int AESBlockSize = 16;

// Shuffle mask for fixing up 128-bit words consisting of big-endian 32-bit integers.
ATTRIBUTE_ALIGNED(16) static const uint64_t KEY_SHUFFLE_MASK[] = {
    0x0405060700010203UL, 0x0C0D0E0F08090A0BUL
};
static address key_shuffle_mask_addr() {
  return (address)KEY_SHUFFLE_MASK;
}

// Shuffle mask for big-endian 128-bit integers.
ATTRIBUTE_ALIGNED(64) static const uint64_t COUNTER_SHUFFLE_MASK[] = {
    0x08090A0B0C0D0E0FUL, 0x0001020304050607UL,
    0x08090A0B0C0D0E0FUL, 0x0001020304050607UL,
    0x08090A0B0C0D0E0FUL, 0x0001020304050607UL,
    0x08090A0B0C0D0E0FUL, 0x0001020304050607UL,
};
static address counter_shuffle_mask_addr() {
  return (address)COUNTER_SHUFFLE_MASK;
}

// This mask is used for incrementing counter value
ATTRIBUTE_ALIGNED(64) static const uint64_t COUNTER_MASK_LINC0[] = {
    0x0000000000000000UL, 0x0000000000000000UL,
    0x0000000000000001UL, 0x0000000000000000UL,
    0x0000000000000002UL, 0x0000000000000000UL,
    0x0000000000000003UL, 0x0000000000000000UL,
};
static address counter_mask_linc0_addr() {
  return (address)COUNTER_MASK_LINC0;
}

ATTRIBUTE_ALIGNED(16) static const uint64_t COUNTER_MASK_LINC1[] = {
    0x0000000000000001UL, 0x0000000000000000UL,
};
static address counter_mask_linc1_addr() {
  return (address)COUNTER_MASK_LINC1;
}

ATTRIBUTE_ALIGNED(16) uint64_t COUNTER_MASK_LINC1F[] = {
    0x0000000000000000UL, 0x0100000000000000UL,
};

static address counter_mask_linc1f_addr() {
  return (address)COUNTER_MASK_LINC1F;
}

ATTRIBUTE_ALIGNED(16) uint64_t COUNTER_MASK_LINC2[] = {
    0x0000000000000002UL, 0x0000000000000000UL,
};

static address counter_mask_linc2_addr() {
  return (address)COUNTER_MASK_LINC2;
}

ATTRIBUTE_ALIGNED(16) uint64_t COUNTER_MASK_LINC2F[] = {
    0x0000000000000000UL, 0x0200000000000000UL,
};

static address counter_mask_linc2f_addr() {
  return (address)COUNTER_MASK_LINC2F;
}

ATTRIBUTE_ALIGNED(64) static const uint64_t COUNTER_MASK_LINC4[] = {
    0x0000000000000004UL, 0x0000000000000000UL,
    0x0000000000000004UL, 0x0000000000000000UL,
    0x0000000000000004UL, 0x0000000000000000UL,
    0x0000000000000004UL, 0x0000000000000000UL,
};
static address counter_mask_linc4_addr() {
  return (address)COUNTER_MASK_LINC4;
}

ATTRIBUTE_ALIGNED(64) static const uint64_t COUNTER_MASK_LINC8[] = {
    0x0000000000000008UL, 0x0000000000000000UL,
    0x0000000000000008UL, 0x0000000000000000UL,
    0x0000000000000008UL, 0x0000000000000000UL,
    0x0000000000000008UL, 0x0000000000000000UL,
};
static address counter_mask_linc8_addr() {
  return (address)COUNTER_MASK_LINC8;
}

ATTRIBUTE_ALIGNED(64) static const uint64_t COUNTER_MASK_LINC16[] = {
    0x0000000000000010UL, 0x0000000000000000UL,
    0x0000000000000010UL, 0x0000000000000000UL,
    0x0000000000000010UL, 0x0000000000000000UL,
    0x0000000000000010UL, 0x0000000000000000UL,
};
static address counter_mask_linc16_addr() {
  return (address)COUNTER_MASK_LINC16;
}

ATTRIBUTE_ALIGNED(64) static const uint64_t COUNTER_MASK_LINC32[] = {
    0x0000000000000020UL, 0x0000000000000000UL,
    0x0000000000000020UL, 0x0000000000000000UL,
    0x0000000000000020UL, 0x0000000000000000UL,
    0x0000000000000020UL, 0x0000000000000000UL,
};
static address counter_mask_linc32_addr() {
  return (address)COUNTER_MASK_LINC32;
}

ATTRIBUTE_ALIGNED(64) uint64_t COUNTER_MASK_ONES[] = {
    0x0000000000000000UL, 0x0000000000000001UL,
    0x0000000000000000UL, 0x0000000000000001UL,
    0x0000000000000000UL, 0x0000000000000001UL,
    0x0000000000000000UL, 0x0000000000000001UL,
};
static address counter_mask_ones_addr() {
  return (address)COUNTER_MASK_ONES;
}

ATTRIBUTE_ALIGNED(64) static const uint64_t GHASH_POLYNOMIAL_REDUCTION[] = {
    0x00000001C2000000UL, 0xC200000000000000UL,
    0x00000001C2000000UL, 0xC200000000000000UL,
    0x00000001C2000000UL, 0xC200000000000000UL,
    0x00000001C2000000UL, 0xC200000000000000UL,
};
static address ghash_polynomial_reduction_addr() {
  return (address)GHASH_POLYNOMIAL_REDUCTION;
}

ATTRIBUTE_ALIGNED(16) static const uint64_t GHASH_POLYNOMIAL_TWO_ONE[] = {
    0x0000000000000001UL, 0x0000000100000000UL,
};
static address ghash_polynomial_two_one_addr() {
  return (address)GHASH_POLYNOMIAL_TWO_ONE;
}


// AES intrinsic stubs

void StubGenerator::generate_aes_stubs() {
  if (UseAESIntrinsics) {
    StubRoutines::_aescrypt_encryptBlock = generate_aescrypt_encryptBlock();
    StubRoutines::_aescrypt_decryptBlock = generate_aescrypt_decryptBlock();
    StubRoutines::_cipherBlockChaining_encryptAESCrypt = generate_cipherBlockChaining_encryptAESCrypt();
    if (VM_Version::supports_avx512_vaes() &&  VM_Version::supports_avx512vl() && VM_Version::supports_avx512dq() ) {
      StubRoutines::_cipherBlockChaining_decryptAESCrypt = generate_cipherBlockChaining_decryptVectorAESCrypt();
      StubRoutines::_electronicCodeBook_encryptAESCrypt = generate_electronicCodeBook_encryptAESCrypt();
      StubRoutines::_electronicCodeBook_decryptAESCrypt = generate_electronicCodeBook_decryptAESCrypt();
      StubRoutines::_galoisCounterMode_AESCrypt = generate_galoisCounterMode_AESCrypt();
    } else {
      StubRoutines::_cipherBlockChaining_decryptAESCrypt = generate_cipherBlockChaining_decryptAESCrypt_Parallel();
      if (VM_Version::supports_avx2()) {
          StubRoutines::_galoisCounterMode_AESCrypt = generate_avx2_galoisCounterMode_AESCrypt();
      }
    }
  }

  if (UseAESCTRIntrinsics) {
    if (VM_Version::supports_avx512_vaes() && VM_Version::supports_avx512bw() && VM_Version::supports_avx512vl()) {
      StubRoutines::_counterMode_AESCrypt = generate_counterMode_VectorAESCrypt();
    } else {
      StubRoutines::_counterMode_AESCrypt = generate_counterMode_AESCrypt_Parallel();
    }
  }
}

// Vector AES Galois Counter Mode implementation.
//
// Inputs:           Windows    |   Linux
//   in         = rcx (c_rarg0) | rsi (c_rarg0)
//   len        = rdx (c_rarg1) | rdi (c_rarg1)
//   ct         = r8  (c_rarg2) | rdx (c_rarg2)
//   out        = r9  (c_rarg3) | rcx (c_rarg3)
//   key        = r10           | r8  (c_rarg4)
//   state      = r13           | r9  (c_rarg5)
//   subkeyHtbl = r14           | r11
//   counter    = rsi           | r12
//
// Output:
//   rax - number of processed bytes
address StubGenerator::generate_galoisCounterMode_AESCrypt() {
  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", "galoisCounterMode_AESCrypt");
  address start = __ pc();

  const Register in = c_rarg0;
  const Register len = c_rarg1;
  const Register ct = c_rarg2;
  const Register out = c_rarg3;
  // and updated with the incremented counter in the end
#ifndef _WIN64
  const Register key = c_rarg4;
  const Register state = c_rarg5;
  const Address subkeyH_mem(rbp, 2 * wordSize);
  const Register subkeyHtbl = r11;
  const Register avx512_subkeyHtbl = r13;
  const Address counter_mem(rbp, 3 * wordSize);
  const Register counter = r12;
#else
  const Address key_mem(rbp, 6 * wordSize);
  const Register key = r10;
  const Address state_mem(rbp, 7 * wordSize);
  const Register state = r13;
  const Address subkeyH_mem(rbp, 8 * wordSize);
  const Register subkeyHtbl = r14;
  const Register avx512_subkeyHtbl = r12;
  const Address counter_mem(rbp, 9 * wordSize);
  const Register counter = rsi;
#endif
  __ enter();
 // Save state before entering routine
  __ push(r12);
  __ push(r13);
  __ push(r14);
  __ push(r15);
  __ push(rbx);
#ifdef _WIN64
  // on win64, fill len_reg from stack position
  __ push(rsi);
  __ movptr(key, key_mem);
  __ movptr(state, state_mem);
#endif
  __ movptr(subkeyHtbl, subkeyH_mem);
  __ movptr(counter, counter_mem);
// Save rbp and rsp
  __ push(rbp);
  __ movq(rbp, rsp);
// Align stack
  __ andq(rsp, -64);
  __ subptr(rsp, 96 * longSize); // Create space on the stack for htbl entries
  __ movptr(avx512_subkeyHtbl, rsp);

  aesgcm_encrypt(in, len, ct, out, key, state, subkeyHtbl, avx512_subkeyHtbl, counter);

  __ vzeroupper();

  __ movq(rsp, rbp);
  __ pop(rbp);

  // Restore state before leaving routine
#ifdef _WIN64
  __ pop(rsi);
#endif
  __ pop(rbx);
  __ pop(r15);
  __ pop(r14);
  __ pop(r13);
  __ pop(r12);

  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ ret(0);

  return start;
}

// AVX2 Vector AES Galois Counter Mode implementation.
//
// Inputs:           Windows    |   Linux
//   in         = rcx (c_rarg0) | rsi (c_rarg0)
//   len        = rdx (c_rarg1) | rdi (c_rarg1)
//   ct         = r8  (c_rarg2) | rdx (c_rarg2)
//   out        = r9  (c_rarg3) | rcx (c_rarg3)
//   key        = rdi           | r8  (c_rarg4)
//   state      = r13           | r9  (c_rarg5)
//   subkeyHtbl = r11           | r11
//   counter    = rsi           | r12
//
// Output:
//   rax - number of processed bytes
address StubGenerator::generate_avx2_galoisCounterMode_AESCrypt() {
  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", "galoisCounterMode_AESCrypt");
  address start = __ pc();

  const Register in = c_rarg0;
  const Register len = c_rarg1;
  const Register ct = c_rarg2;
  const Register out = c_rarg3;
  // and updated with the incremented counter in the end
 #ifndef _WIN64
  const Register key = c_rarg4;
  const Register state = c_rarg5;
  const Address subkeyH_mem(rbp, 2 * wordSize);
  const Register subkeyHtbl = r11;
  const Address counter_mem(rbp, 3 * wordSize);
  const Register counter = r12;
 #else
  const Address key_mem(rbp, 6 * wordSize);
  const Register key = rdi;
  const Address state_mem(rbp, 7 * wordSize);
  const Register state = r13;
  const Address subkeyH_mem(rbp, 8 * wordSize);
  const Register subkeyHtbl = r11;
  const Address counter_mem(rbp, 9 * wordSize);
  const Register counter = rsi;
 #endif
  __ enter();
  // Save state before entering routine
  __ push(r12);
  __ push(r13);
  __ push(r14);
  __ push(r15);
  __ push(rbx);
#ifdef _WIN64
  // on win64, fill len_reg from stack position
  __ push(rsi);
  __ push(rdi);
  __ movptr(key, key_mem);
  __ movptr(state, state_mem);
#endif
  __ movptr(subkeyHtbl, subkeyH_mem);
  __ movptr(counter, counter_mem);

  // Save rsp
  __ movq(r14, rsp);
  // Align stack
  __ andq(rsp, -64);
  __ subptr(rsp, 16 * longSize); // Create space on the stack for saving AES entries

  aesgcm_avx2(in, len, ct, out, key, state, subkeyHtbl, counter);
  __ vzeroupper();
  __ movq(rsp, r14);
  // Restore state before leaving routine
 #ifdef _WIN64
  __ pop(rdi);
  __ pop(rsi);
 #endif
  __ pop(rbx);
  __ pop(r15);
  __ pop(r14);
  __ pop(r13);
  __ pop(r12);

  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ ret(0);

  return start;
}

// Vector AES Counter implementation
address StubGenerator::generate_counterMode_VectorAESCrypt()  {
  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", "counterMode_AESCrypt");
  address start = __ pc();

  const Register from = c_rarg0; // source array address
  const Register to = c_rarg1; // destination array address
  const Register key = c_rarg2; // key array address r8
  const Register counter = c_rarg3; // counter byte array initialized from counter array address
  // and updated with the incremented counter in the end
#ifndef _WIN64
  const Register len_reg = c_rarg4;
  const Register saved_encCounter_start = c_rarg5;
  const Register used_addr = r10;
  const Address  used_mem(rbp, 2 * wordSize);
  const Register used = r11;
#else
  const Address len_mem(rbp, 6 * wordSize); // length is on stack on Win64
  const Address saved_encCounter_mem(rbp, 7 * wordSize); // saved encrypted counter is on stack on Win64
  const Address used_mem(rbp, 8 * wordSize); // used length is on stack on Win64
  const Register len_reg = r10; // pick the first volatile windows register
  const Register saved_encCounter_start = r11;
  const Register used_addr = r13;
  const Register used = r14;
#endif
  __ enter();
 // Save state before entering routine
  __ push(r12);
  __ push(r13);
  __ push(r14);
  __ push(r15);
#ifdef _WIN64
  // on win64, fill len_reg from stack position
  __ movl(len_reg, len_mem);
  __ movptr(saved_encCounter_start, saved_encCounter_mem);
  __ movptr(used_addr, used_mem);
  __ movl(used, Address(used_addr, 0));
#else
  __ push(len_reg); // Save
  __ movptr(used_addr, used_mem);
  __ movl(used, Address(used_addr, 0));
#endif
  __ push(rbx);

  aesctr_encrypt(from, to, key, counter, len_reg, used, used_addr, saved_encCounter_start);

  __ vzeroupper();
  // Restore state before leaving routine
  __ pop(rbx);
#ifdef _WIN64
  __ movl(rax, len_mem); // return length
#else
  __ pop(rax); // return length
#endif
  __ pop(r15);
  __ pop(r14);
  __ pop(r13);
  __ pop(r12);

  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ ret(0);

  return start;
}

// This is a version of CTR/AES crypt which does 6 blocks in a loop at a time
// to hide instruction latency
//
// Arguments:
//
// Inputs:
//   c_rarg0   - source byte array address
//   c_rarg1   - destination byte array address
//   c_rarg2   - K (key) in little endian int array
//   c_rarg3   - counter vector byte array address
//   Linux
//     c_rarg4   -          input length
//     c_rarg5   -          saved encryptedCounter start
//     rbp + 6 * wordSize - saved used length
//   Windows
//     rbp + 6 * wordSize - input length
//     rbp + 7 * wordSize - saved encryptedCounter start
//     rbp + 8 * wordSize - saved used length
//
// Output:
//   rax       - input length
//
address StubGenerator::generate_counterMode_AESCrypt_Parallel() {
  assert(UseAES, "need AES instructions and misaligned SSE support");
  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", "counterMode_AESCrypt");
  address start = __ pc();

  const Register from = c_rarg0; // source array address
  const Register to = c_rarg1; // destination array address
  const Register key = c_rarg2; // key array address
  const Register counter = c_rarg3; // counter byte array initialized from counter array address
                                    // and updated with the incremented counter in the end
#ifndef _WIN64
  const Register len_reg = c_rarg4;
  const Register saved_encCounter_start = c_rarg5;
  const Register used_addr = r10;
  const Address  used_mem(rbp, 2 * wordSize);
  const Register used = r11;
#else
  const Address len_mem(rbp, 6 * wordSize); // length is on stack on Win64
  const Address saved_encCounter_mem(rbp, 7 * wordSize); // length is on stack on Win64
  const Address used_mem(rbp, 8 * wordSize); // length is on stack on Win64
  const Register len_reg = r10; // pick the first volatile windows register
  const Register saved_encCounter_start = r11;
  const Register used_addr = r13;
  const Register used = r14;
#endif
  const Register pos = rax;

  const int PARALLEL_FACTOR = 6;
  const XMMRegister xmm_counter_shuf_mask = xmm0;
  const XMMRegister xmm_key_shuf_mask = xmm1; // used temporarily to swap key bytes up front
  const XMMRegister xmm_curr_counter = xmm2;

  const XMMRegister xmm_key_tmp0 = xmm3;
  const XMMRegister xmm_key_tmp1 = xmm4;

  // registers holding the four results in the parallelized loop
  const XMMRegister xmm_result0 = xmm5;
  const XMMRegister xmm_result1 = xmm6;
  const XMMRegister xmm_result2 = xmm7;
  const XMMRegister xmm_result3 = xmm8;
  const XMMRegister xmm_result4 = xmm9;
  const XMMRegister xmm_result5 = xmm10;

  const XMMRegister xmm_from0 = xmm11;
  const XMMRegister xmm_from1 = xmm12;
  const XMMRegister xmm_from2 = xmm13;
  const XMMRegister xmm_from3 = xmm14; //the last one is xmm14. we have to preserve it on WIN64.
  const XMMRegister xmm_from4 = xmm3; //reuse xmm3~4. Because xmm_key_tmp0~1 are useless when loading input text
  const XMMRegister xmm_from5 = xmm4;

  //for key_128, key_192, key_256
  const int rounds[3] = {10, 12, 14};
  Label L_exit_preLoop, L_preLoop_start;
  Label L_multiBlock_loopTop[3];
  Label L_singleBlockLoopTop[3];
  Label L__incCounter[3][6]; //for 6 blocks
  Label L__incCounter_single[3]; //for single block, key128, key192, key256
  Label L_processTail_insr[3], L_processTail_4_insr[3], L_processTail_2_insr[3], L_processTail_1_insr[3], L_processTail_exit_insr[3];
  Label L_processTail_4_extr[3], L_processTail_2_extr[3], L_processTail_1_extr[3], L_processTail_exit_extr[3];

  Label L_exit;

  __ enter(); // required for proper stackwalking of RuntimeStub frame

#ifdef _WIN64
  // allocate spill slots for r13, r14
  enum {
      saved_r13_offset,
      saved_r14_offset
  };
  __ subptr(rsp, 2 * wordSize);
  __ movptr(Address(rsp, saved_r13_offset * wordSize), r13);
  __ movptr(Address(rsp, saved_r14_offset * wordSize), r14);

  // on win64, fill len_reg from stack position
  __ movl(len_reg, len_mem);
  __ movptr(saved_encCounter_start, saved_encCounter_mem);
  __ movptr(used_addr, used_mem);
  __ movl(used, Address(used_addr, 0));
#else
  __ push(len_reg); // Save
  __ movptr(used_addr, used_mem);
  __ movl(used, Address(used_addr, 0));
#endif

  __ push(rbx); // Save RBX
  __ movdqu(xmm_curr_counter, Address(counter, 0x00)); // initialize counter with initial counter
  __ movdqu(xmm_counter_shuf_mask, ExternalAddress(counter_shuffle_mask_addr()), pos /*rscratch*/);
  __ pshufb(xmm_curr_counter, xmm_counter_shuf_mask); //counter is shuffled
  __ movptr(pos, 0);

  // Use the partially used encrpyted counter from last invocation
  __ BIND(L_preLoop_start);
  __ cmpptr(used, 16);
  __ jcc(Assembler::aboveEqual, L_exit_preLoop);
    __ cmpptr(len_reg, 0);
    __ jcc(Assembler::lessEqual, L_exit_preLoop);
    __ movb(rbx, Address(saved_encCounter_start, used));
    __ xorb(rbx, Address(from, pos));
    __ movb(Address(to, pos), rbx);
    __ addptr(pos, 1);
    __ addptr(used, 1);
    __ subptr(len_reg, 1);

  __ jmp(L_preLoop_start);

  __ BIND(L_exit_preLoop);
  __ movl(Address(used_addr, 0), used);

  // key length could be only {11, 13, 15} * 4 = {44, 52, 60}
  __ movdqu(xmm_key_shuf_mask, ExternalAddress(key_shuffle_mask_addr()), rbx /*rscratch*/);
  __ movl(rbx, Address(key, arrayOopDesc::length_offset_in_bytes() - arrayOopDesc::base_offset_in_bytes(T_INT)));
  __ cmpl(rbx, 52);
  __ jcc(Assembler::equal, L_multiBlock_loopTop[1]);
  __ cmpl(rbx, 60);
  __ jcc(Assembler::equal, L_multiBlock_loopTop[2]);

#define CTR_DoSix(opc, src_reg)                \
  __ opc(xmm_result0, src_reg);              \
  __ opc(xmm_result1, src_reg);              \
  __ opc(xmm_result2, src_reg);              \
  __ opc(xmm_result3, src_reg);              \
  __ opc(xmm_result4, src_reg);              \
  __ opc(xmm_result5, src_reg);

  // k == 0 :  generate code for key_128
  // k == 1 :  generate code for key_192
  // k == 2 :  generate code for key_256
  for (int k = 0; k < 3; ++k) {
    //multi blocks starts here
    __ align(OptoLoopAlignment);
    __ BIND(L_multiBlock_loopTop[k]);
    __ cmpptr(len_reg, PARALLEL_FACTOR * AESBlockSize); // see if at least PARALLEL_FACTOR blocks left
    __ jcc(Assembler::less, L_singleBlockLoopTop[k]);
    load_key(xmm_key_tmp0, key, 0x00, xmm_key_shuf_mask);

    //load, then increase counters
    CTR_DoSix(movdqa, xmm_curr_counter);
    inc_counter(rbx, xmm_result1, 0x01, L__incCounter[k][0]);
    inc_counter(rbx, xmm_result2, 0x02, L__incCounter[k][1]);
    inc_counter(rbx, xmm_result3, 0x03, L__incCounter[k][2]);
    inc_counter(rbx, xmm_result4, 0x04, L__incCounter[k][3]);
    inc_counter(rbx, xmm_result5,  0x05, L__incCounter[k][4]);
    inc_counter(rbx, xmm_curr_counter, 0x06, L__incCounter[k][5]);
    CTR_DoSix(pshufb, xmm_counter_shuf_mask); // after increased, shuffled counters back for PXOR
    CTR_DoSix(pxor, xmm_key_tmp0);   //PXOR with Round 0 key

    //load two ROUND_KEYs at a time
    for (int i = 1; i < rounds[k]; ) {
      load_key(xmm_key_tmp1, key, (0x10 * i), xmm_key_shuf_mask);
      load_key(xmm_key_tmp0, key, (0x10 * (i+1)), xmm_key_shuf_mask);
      CTR_DoSix(aesenc, xmm_key_tmp1);
      i++;
      if (i != rounds[k]) {
        CTR_DoSix(aesenc, xmm_key_tmp0);
      } else {
        CTR_DoSix(aesenclast, xmm_key_tmp0);
      }
      i++;
    }

    // get next PARALLEL_FACTOR blocks into xmm_result registers
    __ movdqu(xmm_from0, Address(from, pos, Address::times_1, 0 * AESBlockSize));
    __ movdqu(xmm_from1, Address(from, pos, Address::times_1, 1 * AESBlockSize));
    __ movdqu(xmm_from2, Address(from, pos, Address::times_1, 2 * AESBlockSize));
    __ movdqu(xmm_from3, Address(from, pos, Address::times_1, 3 * AESBlockSize));
    __ movdqu(xmm_from4, Address(from, pos, Address::times_1, 4 * AESBlockSize));
    __ movdqu(xmm_from5, Address(from, pos, Address::times_1, 5 * AESBlockSize));

    __ pxor(xmm_result0, xmm_from0);
    __ pxor(xmm_result1, xmm_from1);
    __ pxor(xmm_result2, xmm_from2);
    __ pxor(xmm_result3, xmm_from3);
    __ pxor(xmm_result4, xmm_from4);
    __ pxor(xmm_result5, xmm_from5);

    // store 6 results into the next 64 bytes of output
    __ movdqu(Address(to, pos, Address::times_1, 0 * AESBlockSize), xmm_result0);
    __ movdqu(Address(to, pos, Address::times_1, 1 * AESBlockSize), xmm_result1);
    __ movdqu(Address(to, pos, Address::times_1, 2 * AESBlockSize), xmm_result2);
    __ movdqu(Address(to, pos, Address::times_1, 3 * AESBlockSize), xmm_result3);
    __ movdqu(Address(to, pos, Address::times_1, 4 * AESBlockSize), xmm_result4);
    __ movdqu(Address(to, pos, Address::times_1, 5 * AESBlockSize), xmm_result5);

    __ addptr(pos, PARALLEL_FACTOR * AESBlockSize); // increase the length of crypt text
    __ subptr(len_reg, PARALLEL_FACTOR * AESBlockSize); // decrease the remaining length
    __ jmp(L_multiBlock_loopTop[k]);

    // singleBlock starts here
    __ align(OptoLoopAlignment);
    __ BIND(L_singleBlockLoopTop[k]);
    __ cmpptr(len_reg, 0);
    __ jcc(Assembler::lessEqual, L_exit);
    load_key(xmm_key_tmp0, key, 0x00, xmm_key_shuf_mask);
    __ movdqa(xmm_result0, xmm_curr_counter);
    inc_counter(rbx, xmm_curr_counter, 0x01, L__incCounter_single[k]);
    __ pshufb(xmm_result0, xmm_counter_shuf_mask);
    __ pxor(xmm_result0, xmm_key_tmp0);
    for (int i = 1; i < rounds[k]; i++) {
      load_key(xmm_key_tmp0, key, (0x10 * i), xmm_key_shuf_mask);
      __ aesenc(xmm_result0, xmm_key_tmp0);
    }
    load_key(xmm_key_tmp0, key, (rounds[k] * 0x10), xmm_key_shuf_mask);
    __ aesenclast(xmm_result0, xmm_key_tmp0);
    __ cmpptr(len_reg, AESBlockSize);
    __ jcc(Assembler::less, L_processTail_insr[k]);
      __ movdqu(xmm_from0, Address(from, pos, Address::times_1, 0 * AESBlockSize));
      __ pxor(xmm_result0, xmm_from0);
      __ movdqu(Address(to, pos, Address::times_1, 0 * AESBlockSize), xmm_result0);
      __ addptr(pos, AESBlockSize);
      __ subptr(len_reg, AESBlockSize);
      __ jmp(L_singleBlockLoopTop[k]);
    __ BIND(L_processTail_insr[k]);                               // Process the tail part of the input array
      __ addptr(pos, len_reg);                                    // 1. Insert bytes from src array into xmm_from0 register
      __ testptr(len_reg, 8);
      __ jcc(Assembler::zero, L_processTail_4_insr[k]);
        __ subptr(pos,8);
        __ pinsrq(xmm_from0, Address(from, pos), 0);
      __ BIND(L_processTail_4_insr[k]);
      __ testptr(len_reg, 4);
      __ jcc(Assembler::zero, L_processTail_2_insr[k]);
        __ subptr(pos,4);
        __ pslldq(xmm_from0, 4);
        __ pinsrd(xmm_from0, Address(from, pos), 0);
      __ BIND(L_processTail_2_insr[k]);
      __ testptr(len_reg, 2);
      __ jcc(Assembler::zero, L_processTail_1_insr[k]);
        __ subptr(pos, 2);
        __ pslldq(xmm_from0, 2);
        __ pinsrw(xmm_from0, Address(from, pos), 0);
      __ BIND(L_processTail_1_insr[k]);
      __ testptr(len_reg, 1);
      __ jcc(Assembler::zero, L_processTail_exit_insr[k]);
        __ subptr(pos, 1);
        __ pslldq(xmm_from0, 1);
        __ pinsrb(xmm_from0, Address(from, pos), 0);
      __ BIND(L_processTail_exit_insr[k]);

      __ movdqu(Address(saved_encCounter_start, 0), xmm_result0);  // 2. Perform pxor of the encrypted counter and plaintext Bytes.
      __ pxor(xmm_result0, xmm_from0);                             //    Also the encrypted counter is saved for next invocation.

      __ testptr(len_reg, 8);
      __ jcc(Assembler::zero, L_processTail_4_extr[k]);            // 3. Extract bytes from xmm_result0 into the dest. array
        __ pextrq(Address(to, pos), xmm_result0, 0);
        __ psrldq(xmm_result0, 8);
        __ addptr(pos, 8);
      __ BIND(L_processTail_4_extr[k]);
      __ testptr(len_reg, 4);
      __ jcc(Assembler::zero, L_processTail_2_extr[k]);
        __ pextrd(Address(to, pos), xmm_result0, 0);
        __ psrldq(xmm_result0, 4);
        __ addptr(pos, 4);
      __ BIND(L_processTail_2_extr[k]);
      __ testptr(len_reg, 2);
      __ jcc(Assembler::zero, L_processTail_1_extr[k]);
        __ pextrw(Address(to, pos), xmm_result0, 0);
        __ psrldq(xmm_result0, 2);
        __ addptr(pos, 2);
      __ BIND(L_processTail_1_extr[k]);
      __ testptr(len_reg, 1);
      __ jcc(Assembler::zero, L_processTail_exit_extr[k]);
        __ pextrb(Address(to, pos), xmm_result0, 0);

      __ BIND(L_processTail_exit_extr[k]);
      __ movl(Address(used_addr, 0), len_reg);
      __ jmp(L_exit);
  }

  __ BIND(L_exit);
  __ pshufb(xmm_curr_counter, xmm_counter_shuf_mask); //counter is shuffled back.
  __ movdqu(Address(counter, 0), xmm_curr_counter); //save counter back
  __ pop(rbx); // pop the saved RBX.
#ifdef _WIN64
  __ movl(rax, len_mem);
  __ movptr(r13, Address(rsp, saved_r13_offset * wordSize));
  __ movptr(r14, Address(rsp, saved_r14_offset * wordSize));
  __ addptr(rsp, 2 * wordSize);
#else
  __ pop(rax); // return 'len'
#endif
  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ ret(0);

  return start;
}

address StubGenerator::generate_cipherBlockChaining_decryptVectorAESCrypt() {
  assert(VM_Version::supports_avx512_vaes(), "need AES instructions and misaligned SSE support");
  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", "cipherBlockChaining_decryptAESCrypt");
  address start = __ pc();

  const Register from = c_rarg0;  // source array address
  const Register to = c_rarg1;  // destination array address
  const Register key = c_rarg2;  // key array address
  const Register rvec = c_rarg3;  // r byte array initialized from initvector array address
  // and left with the results of the last encryption block
#ifndef _WIN64
  const Register len_reg = c_rarg4;  // src len (must be multiple of blocksize 16)
#else
  const Address  len_mem(rbp, 6 * wordSize);  // length is on stack on Win64
  const Register len_reg = r11;      // pick the volatile windows register
#endif

  Label Loop, Loop1, L_128, L_256, L_192, KEY_192, KEY_256, Loop2, Lcbc_dec_rem_loop,
        Lcbc_dec_rem_last, Lcbc_dec_ret, Lcbc_dec_rem, Lcbc_exit;

  __ enter();

#ifdef _WIN64
// on win64, fill len_reg from stack position
  __ movl(len_reg, len_mem);
#else
  __ push(len_reg); // Save
#endif
  __ push(rbx);
  __ vzeroupper();

  // Temporary variable declaration for swapping key bytes
  const XMMRegister xmm_key_shuf_mask = xmm1;
  __ movdqu(xmm_key_shuf_mask, ExternalAddress(key_shuffle_mask_addr()), rbx /*rscratch*/);

  // Calculate number of rounds from key size: 44 for 10-rounds, 52 for 12-rounds, 60 for 14-rounds
  const Register rounds = rbx;
  __ movl(rounds, Address(key, arrayOopDesc::length_offset_in_bytes() - arrayOopDesc::base_offset_in_bytes(T_INT)));

  const XMMRegister IV = xmm0;
  // Load IV and broadcast value to 512-bits
  __ evbroadcasti64x2(IV, Address(rvec, 0), Assembler::AVX_512bit);

  // Temporary variables for storing round keys
  const XMMRegister RK0 = xmm30;
  const XMMRegister RK1 = xmm9;
  const XMMRegister RK2 = xmm18;
  const XMMRegister RK3 = xmm19;
  const XMMRegister RK4 = xmm20;
  const XMMRegister RK5 = xmm21;
  const XMMRegister RK6 = xmm22;
  const XMMRegister RK7 = xmm23;
  const XMMRegister RK8 = xmm24;
  const XMMRegister RK9 = xmm25;
  const XMMRegister RK10 = xmm26;

  // Load and shuffle key
  // the java expanded key ordering is rotated one position from what we want
  // so we start from 1*16 here and hit 0*16 last
  ev_load_key(RK1, key, 1 * 16, xmm_key_shuf_mask);
  ev_load_key(RK2, key, 2 * 16, xmm_key_shuf_mask);
  ev_load_key(RK3, key, 3 * 16, xmm_key_shuf_mask);
  ev_load_key(RK4, key, 4 * 16, xmm_key_shuf_mask);
  ev_load_key(RK5, key, 5 * 16, xmm_key_shuf_mask);
  ev_load_key(RK6, key, 6 * 16, xmm_key_shuf_mask);
  ev_load_key(RK7, key, 7 * 16, xmm_key_shuf_mask);
  ev_load_key(RK8, key, 8 * 16, xmm_key_shuf_mask);
  ev_load_key(RK9, key, 9 * 16, xmm_key_shuf_mask);
  ev_load_key(RK10, key, 10 * 16, xmm_key_shuf_mask);
  ev_load_key(RK0, key, 0*16, xmm_key_shuf_mask);

  // Variables for storing source cipher text
  const XMMRegister S0 = xmm10;
  const XMMRegister S1 = xmm11;
  const XMMRegister S2 = xmm12;
  const XMMRegister S3 = xmm13;
  const XMMRegister S4 = xmm14;
  const XMMRegister S5 = xmm15;
  const XMMRegister S6 = xmm16;
  const XMMRegister S7 = xmm17;

  // Variables for storing decrypted text
  const XMMRegister B0 = xmm1;
  const XMMRegister B1 = xmm2;
  const XMMRegister B2 = xmm3;
  const XMMRegister B3 = xmm4;
  const XMMRegister B4 = xmm5;
  const XMMRegister B5 = xmm6;
  const XMMRegister B6 = xmm7;
  const XMMRegister B7 = xmm8;

  __ cmpl(rounds, 44);
  __ jcc(Assembler::greater, KEY_192);
  __ jmp(Loop);

  __ BIND(KEY_192);
  const XMMRegister RK11 = xmm27;
  const XMMRegister RK12 = xmm28;
  ev_load_key(RK11, key, 11*16, xmm_key_shuf_mask);
  ev_load_key(RK12, key, 12*16, xmm_key_shuf_mask);

  __ cmpl(rounds, 52);
  __ jcc(Assembler::greater, KEY_256);
  __ jmp(Loop);

  __ BIND(KEY_256);
  const XMMRegister RK13 = xmm29;
  const XMMRegister RK14 = xmm31;
  ev_load_key(RK13, key, 13*16, xmm_key_shuf_mask);
  ev_load_key(RK14, key, 14*16, xmm_key_shuf_mask);

  __ BIND(Loop);
  __ cmpl(len_reg, 512);
  __ jcc(Assembler::below, Lcbc_dec_rem);
  __ BIND(Loop1);
  __ subl(len_reg, 512);
  __ evmovdquq(S0, Address(from, 0 * 64), Assembler::AVX_512bit);
  __ evmovdquq(S1, Address(from, 1 * 64), Assembler::AVX_512bit);
  __ evmovdquq(S2, Address(from, 2 * 64), Assembler::AVX_512bit);
  __ evmovdquq(S3, Address(from, 3 * 64), Assembler::AVX_512bit);
  __ evmovdquq(S4, Address(from, 4 * 64), Assembler::AVX_512bit);
  __ evmovdquq(S5, Address(from, 5 * 64), Assembler::AVX_512bit);
  __ evmovdquq(S6, Address(from, 6 * 64), Assembler::AVX_512bit);
  __ evmovdquq(S7, Address(from, 7 * 64), Assembler::AVX_512bit);
  __ leaq(from, Address(from, 8 * 64));

  __ evpxorq(B0, S0, RK1, Assembler::AVX_512bit);
  __ evpxorq(B1, S1, RK1, Assembler::AVX_512bit);
  __ evpxorq(B2, S2, RK1, Assembler::AVX_512bit);
  __ evpxorq(B3, S3, RK1, Assembler::AVX_512bit);
  __ evpxorq(B4, S4, RK1, Assembler::AVX_512bit);
  __ evpxorq(B5, S5, RK1, Assembler::AVX_512bit);
  __ evpxorq(B6, S6, RK1, Assembler::AVX_512bit);
  __ evpxorq(B7, S7, RK1, Assembler::AVX_512bit);

  __ evalignq(IV, S0, IV, 0x06);
  __ evalignq(S0, S1, S0, 0x06);
  __ evalignq(S1, S2, S1, 0x06);
  __ evalignq(S2, S3, S2, 0x06);
  __ evalignq(S3, S4, S3, 0x06);
  __ evalignq(S4, S5, S4, 0x06);
  __ evalignq(S5, S6, S5, 0x06);
  __ evalignq(S6, S7, S6, 0x06);

  roundDec(RK2);
  roundDec(RK3);
  roundDec(RK4);
  roundDec(RK5);
  roundDec(RK6);
  roundDec(RK7);
  roundDec(RK8);
  roundDec(RK9);
  roundDec(RK10);

  __ cmpl(rounds, 44);
  __ jcc(Assembler::belowEqual, L_128);
  roundDec(RK11);
  roundDec(RK12);

  __ cmpl(rounds, 52);
  __ jcc(Assembler::belowEqual, L_192);
  roundDec(RK13);
  roundDec(RK14);

  __ BIND(L_256);
  roundDeclast(RK0);
  __ jmp(Loop2);

  __ BIND(L_128);
  roundDeclast(RK0);
  __ jmp(Loop2);

  __ BIND(L_192);
  roundDeclast(RK0);

  __ BIND(Loop2);
  __ evpxorq(B0, B0, IV, Assembler::AVX_512bit);
  __ evpxorq(B1, B1, S0, Assembler::AVX_512bit);
  __ evpxorq(B2, B2, S1, Assembler::AVX_512bit);
  __ evpxorq(B3, B3, S2, Assembler::AVX_512bit);
  __ evpxorq(B4, B4, S3, Assembler::AVX_512bit);
  __ evpxorq(B5, B5, S4, Assembler::AVX_512bit);
  __ evpxorq(B6, B6, S5, Assembler::AVX_512bit);
  __ evpxorq(B7, B7, S6, Assembler::AVX_512bit);
  __ evmovdquq(IV, S7, Assembler::AVX_512bit);

  __ evmovdquq(Address(to, 0 * 64), B0, Assembler::AVX_512bit);
  __ evmovdquq(Address(to, 1 * 64), B1, Assembler::AVX_512bit);
  __ evmovdquq(Address(to, 2 * 64), B2, Assembler::AVX_512bit);
  __ evmovdquq(Address(to, 3 * 64), B3, Assembler::AVX_512bit);
  __ evmovdquq(Address(to, 4 * 64), B4, Assembler::AVX_512bit);
  __ evmovdquq(Address(to, 5 * 64), B5, Assembler::AVX_512bit);
  __ evmovdquq(Address(to, 6 * 64), B6, Assembler::AVX_512bit);
  __ evmovdquq(Address(to, 7 * 64), B7, Assembler::AVX_512bit);
  __ leaq(to, Address(to, 8 * 64));
  __ jmp(Loop);

  __ BIND(Lcbc_dec_rem);
  __ evshufi64x2(IV, IV, IV, 0x03, Assembler::AVX_512bit);

  __ BIND(Lcbc_dec_rem_loop);
  __ subl(len_reg, 16);
  __ jcc(Assembler::carrySet, Lcbc_dec_ret);

  __ movdqu(S0, Address(from, 0));
  __ evpxorq(B0, S0, RK1, Assembler::AVX_512bit);
  __ vaesdec(B0, B0, RK2, Assembler::AVX_512bit);
  __ vaesdec(B0, B0, RK3, Assembler::AVX_512bit);
  __ vaesdec(B0, B0, RK4, Assembler::AVX_512bit);
  __ vaesdec(B0, B0, RK5, Assembler::AVX_512bit);
  __ vaesdec(B0, B0, RK6, Assembler::AVX_512bit);
  __ vaesdec(B0, B0, RK7, Assembler::AVX_512bit);
  __ vaesdec(B0, B0, RK8, Assembler::AVX_512bit);
  __ vaesdec(B0, B0, RK9, Assembler::AVX_512bit);
  __ vaesdec(B0, B0, RK10, Assembler::AVX_512bit);
  __ cmpl(rounds, 44);
  __ jcc(Assembler::belowEqual, Lcbc_dec_rem_last);

  __ vaesdec(B0, B0, RK11, Assembler::AVX_512bit);
  __ vaesdec(B0, B0, RK12, Assembler::AVX_512bit);
  __ cmpl(rounds, 52);
  __ jcc(Assembler::belowEqual, Lcbc_dec_rem_last);

  __ vaesdec(B0, B0, RK13, Assembler::AVX_512bit);
  __ vaesdec(B0, B0, RK14, Assembler::AVX_512bit);

  __ BIND(Lcbc_dec_rem_last);
  __ vaesdeclast(B0, B0, RK0, Assembler::AVX_512bit);

  __ evpxorq(B0, B0, IV, Assembler::AVX_512bit);
  __ evmovdquq(IV, S0, Assembler::AVX_512bit);
  __ movdqu(Address(to, 0), B0);
  __ leaq(from, Address(from, 16));
  __ leaq(to, Address(to, 16));
  __ jmp(Lcbc_dec_rem_loop);

  __ BIND(Lcbc_dec_ret);
  __ movdqu(Address(rvec, 0), IV);

  // Zero out the round keys
  __ evpxorq(RK0, RK0, RK0, Assembler::AVX_512bit);
  __ evpxorq(RK1, RK1, RK1, Assembler::AVX_512bit);
  __ evpxorq(RK2, RK2, RK2, Assembler::AVX_512bit);
  __ evpxorq(RK3, RK3, RK3, Assembler::AVX_512bit);
  __ evpxorq(RK4, RK4, RK4, Assembler::AVX_512bit);
  __ evpxorq(RK5, RK5, RK5, Assembler::AVX_512bit);
  __ evpxorq(RK6, RK6, RK6, Assembler::AVX_512bit);
  __ evpxorq(RK7, RK7, RK7, Assembler::AVX_512bit);
  __ evpxorq(RK8, RK8, RK8, Assembler::AVX_512bit);
  __ evpxorq(RK9, RK9, RK9, Assembler::AVX_512bit);
  __ evpxorq(RK10, RK10, RK10, Assembler::AVX_512bit);
  __ cmpl(rounds, 44);
  __ jcc(Assembler::belowEqual, Lcbc_exit);
  __ evpxorq(RK11, RK11, RK11, Assembler::AVX_512bit);
  __ evpxorq(RK12, RK12, RK12, Assembler::AVX_512bit);
  __ cmpl(rounds, 52);
  __ jcc(Assembler::belowEqual, Lcbc_exit);
  __ evpxorq(RK13, RK13, RK13, Assembler::AVX_512bit);
  __ evpxorq(RK14, RK14, RK14, Assembler::AVX_512bit);

  __ BIND(Lcbc_exit);
  __ vzeroupper();
  __ pop(rbx);
#ifdef _WIN64
  __ movl(rax, len_mem);
#else
  __ pop(rax); // return length
#endif
  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ ret(0);

  return start;
}

// Arguments:
//
// Inputs:
//   c_rarg0   - source byte array address
//   c_rarg1   - destination byte array address
//   c_rarg2   - K (key) in little endian int array
//
address StubGenerator::generate_aescrypt_encryptBlock() {
  assert(UseAES, "need AES instructions and misaligned SSE support");
  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", "aescrypt_encryptBlock");
  Label L_doLast;
  address start = __ pc();

  const Register from        = c_rarg0;  // source array address
  const Register to          = c_rarg1;  // destination array address
  const Register key         = c_rarg2;  // key array address
  const Register keylen      = rax;

  const XMMRegister xmm_result = xmm0;
  const XMMRegister xmm_key_shuf_mask = xmm1;
  // On win64 xmm6-xmm15 must be preserved so don't use them.
  const XMMRegister xmm_temp1  = xmm2;
  const XMMRegister xmm_temp2  = xmm3;
  const XMMRegister xmm_temp3  = xmm4;
  const XMMRegister xmm_temp4  = xmm5;

  __ enter(); // required for proper stackwalking of RuntimeStub frame

  // keylen could be only {11, 13, 15} * 4 = {44, 52, 60}
  __ movl(keylen, Address(key, arrayOopDesc::length_offset_in_bytes() - arrayOopDesc::base_offset_in_bytes(T_INT)));

  __ movdqu(xmm_key_shuf_mask, ExternalAddress(key_shuffle_mask_addr()), r10 /*rscratch*/);
  __ movdqu(xmm_result, Address(from, 0));  // get 16 bytes of input

  // For encryption, the java expanded key ordering is just what we need
  // we don't know if the key is aligned, hence not using load-execute form

  load_key(xmm_temp1, key, 0x00, xmm_key_shuf_mask);
  __ pxor(xmm_result, xmm_temp1);

  load_key(xmm_temp1, key, 0x10, xmm_key_shuf_mask);
  load_key(xmm_temp2, key, 0x20, xmm_key_shuf_mask);
  load_key(xmm_temp3, key, 0x30, xmm_key_shuf_mask);
  load_key(xmm_temp4, key, 0x40, xmm_key_shuf_mask);

  __ aesenc(xmm_result, xmm_temp1);
  __ aesenc(xmm_result, xmm_temp2);
  __ aesenc(xmm_result, xmm_temp3);
  __ aesenc(xmm_result, xmm_temp4);

  load_key(xmm_temp1, key, 0x50, xmm_key_shuf_mask);
  load_key(xmm_temp2, key, 0x60, xmm_key_shuf_mask);
  load_key(xmm_temp3, key, 0x70, xmm_key_shuf_mask);
  load_key(xmm_temp4, key, 0x80, xmm_key_shuf_mask);

  __ aesenc(xmm_result, xmm_temp1);
  __ aesenc(xmm_result, xmm_temp2);
  __ aesenc(xmm_result, xmm_temp3);
  __ aesenc(xmm_result, xmm_temp4);

  load_key(xmm_temp1, key, 0x90, xmm_key_shuf_mask);
  load_key(xmm_temp2, key, 0xa0, xmm_key_shuf_mask);

  __ cmpl(keylen, 44);
  __ jccb(Assembler::equal, L_doLast);

  __ aesenc(xmm_result, xmm_temp1);
  __ aesenc(xmm_result, xmm_temp2);

  load_key(xmm_temp1, key, 0xb0, xmm_key_shuf_mask);
  load_key(xmm_temp2, key, 0xc0, xmm_key_shuf_mask);

  __ cmpl(keylen, 52);
  __ jccb(Assembler::equal, L_doLast);

  __ aesenc(xmm_result, xmm_temp1);
  __ aesenc(xmm_result, xmm_temp2);

  load_key(xmm_temp1, key, 0xd0, xmm_key_shuf_mask);
  load_key(xmm_temp2, key, 0xe0, xmm_key_shuf_mask);

  __ BIND(L_doLast);
  __ aesenc(xmm_result, xmm_temp1);
  __ aesenclast(xmm_result, xmm_temp2);
  __ movdqu(Address(to, 0), xmm_result);        // store the result
  __ xorptr(rax, rax); // return 0

  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ ret(0);

  return start;
}

// Arguments:
//
// Inputs:
//   c_rarg0   - source byte array address
//   c_rarg1   - destination byte array address
//   c_rarg2   - K (key) in little endian int array
//
address StubGenerator::generate_aescrypt_decryptBlock() {
  assert(UseAES, "need AES instructions and misaligned SSE support");
  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", "aescrypt_decryptBlock");
  Label L_doLast;
  address start = __ pc();

  const Register from        = c_rarg0;  // source array address
  const Register to          = c_rarg1;  // destination array address
  const Register key         = c_rarg2;  // key array address
  const Register keylen      = rax;

  const XMMRegister xmm_result = xmm0;
  const XMMRegister xmm_key_shuf_mask = xmm1;
  // On win64 xmm6-xmm15 must be preserved so don't use them.
  const XMMRegister xmm_temp1  = xmm2;
  const XMMRegister xmm_temp2  = xmm3;
  const XMMRegister xmm_temp3  = xmm4;
  const XMMRegister xmm_temp4  = xmm5;

  __ enter(); // required for proper stackwalking of RuntimeStub frame

  // keylen could be only {11, 13, 15} * 4 = {44, 52, 60}
  __ movl(keylen, Address(key, arrayOopDesc::length_offset_in_bytes() - arrayOopDesc::base_offset_in_bytes(T_INT)));

  __ movdqu(xmm_key_shuf_mask, ExternalAddress(key_shuffle_mask_addr()), r10 /*rscratch*/);
  __ movdqu(xmm_result, Address(from, 0));

  // for decryption java expanded key ordering is rotated one position from what we want
  // so we start from 0x10 here and hit 0x00 last
  // we don't know if the key is aligned, hence not using load-execute form
  load_key(xmm_temp1, key, 0x10, xmm_key_shuf_mask);
  load_key(xmm_temp2, key, 0x20, xmm_key_shuf_mask);
  load_key(xmm_temp3, key, 0x30, xmm_key_shuf_mask);
  load_key(xmm_temp4, key, 0x40, xmm_key_shuf_mask);

  __ pxor  (xmm_result, xmm_temp1);
  __ aesdec(xmm_result, xmm_temp2);
  __ aesdec(xmm_result, xmm_temp3);
  __ aesdec(xmm_result, xmm_temp4);

  load_key(xmm_temp1, key, 0x50, xmm_key_shuf_mask);
  load_key(xmm_temp2, key, 0x60, xmm_key_shuf_mask);
  load_key(xmm_temp3, key, 0x70, xmm_key_shuf_mask);
  load_key(xmm_temp4, key, 0x80, xmm_key_shuf_mask);

  __ aesdec(xmm_result, xmm_temp1);
  __ aesdec(xmm_result, xmm_temp2);
  __ aesdec(xmm_result, xmm_temp3);
  __ aesdec(xmm_result, xmm_temp4);

  load_key(xmm_temp1, key, 0x90, xmm_key_shuf_mask);
  load_key(xmm_temp2, key, 0xa0, xmm_key_shuf_mask);
  load_key(xmm_temp3, key, 0x00, xmm_key_shuf_mask);

  __ cmpl(keylen, 44);
  __ jccb(Assembler::equal, L_doLast);

  __ aesdec(xmm_result, xmm_temp1);
  __ aesdec(xmm_result, xmm_temp2);

  load_key(xmm_temp1, key, 0xb0, xmm_key_shuf_mask);
  load_key(xmm_temp2, key, 0xc0, xmm_key_shuf_mask);

  __ cmpl(keylen, 52);
  __ jccb(Assembler::equal, L_doLast);

  __ aesdec(xmm_result, xmm_temp1);
  __ aesdec(xmm_result, xmm_temp2);

  load_key(xmm_temp1, key, 0xd0, xmm_key_shuf_mask);
  load_key(xmm_temp2, key, 0xe0, xmm_key_shuf_mask);

  __ BIND(L_doLast);
  __ aesdec(xmm_result, xmm_temp1);
  __ aesdec(xmm_result, xmm_temp2);

  // for decryption the aesdeclast operation is always on key+0x00
  __ aesdeclast(xmm_result, xmm_temp3);
  __ movdqu(Address(to, 0), xmm_result);  // store the result
  __ xorptr(rax, rax); // return 0

  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ ret(0);

  return start;
}


// Arguments:
//
// Inputs:
//   c_rarg0   - source byte array address
//   c_rarg1   - destination byte array address
//   c_rarg2   - K (key) in little endian int array
//   c_rarg3   - r vector byte array address
//   c_rarg4   - input length
//
// Output:
//   rax       - input length
//
address StubGenerator::generate_cipherBlockChaining_encryptAESCrypt() {
  assert(UseAES, "need AES instructions and misaligned SSE support");
  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", "cipherBlockChaining_encryptAESCrypt");
  address start = __ pc();

  Label L_exit, L_key_192_256, L_key_256, L_loopTop_128, L_loopTop_192, L_loopTop_256;
  const Register from        = c_rarg0;  // source array address
  const Register to          = c_rarg1;  // destination array address
  const Register key         = c_rarg2;  // key array address
  const Register rvec        = c_rarg3;  // r byte array initialized from initvector array address
                                         // and left with the results of the last encryption block
#ifdef _WIN64
  const Address  len_mem(rbp, 6 * wordSize); // length is on stack on Win64
  const Register len_reg     = r11;      // pick the volatile windows register
#else
  const Register len_reg     = c_rarg4;  // src len (must be multiple of blocksize 16)
#endif
  const Register pos         = rax;

  // xmm register assignments for the loops below
  const XMMRegister xmm_result = xmm0;
  const XMMRegister xmm_temp   = xmm1;
  // keys 0-10 preloaded into xmm2-xmm12
  const int XMM_REG_NUM_KEY_FIRST = 2;
  const int XMM_REG_NUM_KEY_LAST  = 15;
  const XMMRegister xmm_key0   = as_XMMRegister(XMM_REG_NUM_KEY_FIRST);
  const XMMRegister xmm_key10  = as_XMMRegister(XMM_REG_NUM_KEY_FIRST+10);
  const XMMRegister xmm_key11  = as_XMMRegister(XMM_REG_NUM_KEY_FIRST+11);
  const XMMRegister xmm_key12  = as_XMMRegister(XMM_REG_NUM_KEY_FIRST+12);
  const XMMRegister xmm_key13  = as_XMMRegister(XMM_REG_NUM_KEY_FIRST+13);

  __ enter(); // required for proper stackwalking of RuntimeStub frame

#ifdef _WIN64
  // on win64, fill len_reg from stack position
  __ movl(len_reg, len_mem);
#else
  __ push(len_reg); // Save
#endif

  const XMMRegister xmm_key_shuf_mask = xmm_temp;  // used temporarily to swap key bytes up front
  __ movdqu(xmm_key_shuf_mask, ExternalAddress(key_shuffle_mask_addr()), r10 /*rscratch*/);
  // load up xmm regs xmm2 thru xmm12 with key 0x00 - 0xa0
  for (int rnum = XMM_REG_NUM_KEY_FIRST, offset = 0x00; rnum <= XMM_REG_NUM_KEY_FIRST+10; rnum++) {
    load_key(as_XMMRegister(rnum), key, offset, xmm_key_shuf_mask);
    offset += 0x10;
  }
  __ movdqu(xmm_result, Address(rvec, 0x00));   // initialize xmm_result with r vec


  // now split to different paths depending on the keylen (len in ints of AESCrypt.KLE array (52=192, or 60=256))
  __ movl(rax, Address(key, arrayOopDesc::length_offset_in_bytes() - arrayOopDesc::base_offset_in_bytes(T_INT)));
  __ cmpl(rax, 44);
  __ jcc(Assembler::notEqual, L_key_192_256);

  // 128 bit code follows here
  __ movptr(pos, 0);
  __ align(OptoLoopAlignment);

  __ BIND(L_loopTop_128);
  __ movdqu(xmm_temp, Address(from, pos, Address::times_1, 0));   // get next 16 bytes of input
  __ pxor  (xmm_result, xmm_temp);               // xor with the current r vector
  __ pxor  (xmm_result, xmm_key0);               // do the aes rounds
  for (int rnum = XMM_REG_NUM_KEY_FIRST + 1; rnum <= XMM_REG_NUM_KEY_FIRST + 9; rnum++) {
    __ aesenc(xmm_result, as_XMMRegister(rnum));
  }
  __ aesenclast(xmm_result, xmm_key10);
  __ movdqu(Address(to, pos, Address::times_1, 0), xmm_result);     // store into the next 16 bytes of output
  // no need to store r to memory until we exit
  __ addptr(pos, AESBlockSize);
  __ subptr(len_reg, AESBlockSize);
  __ jcc(Assembler::notEqual, L_loopTop_128);

  __ BIND(L_exit);
  __ movdqu(Address(rvec, 0), xmm_result);     // final value of r stored in rvec of CipherBlockChaining object

#ifdef _WIN64
  __ movl(rax, len_mem);
#else
  __ pop(rax); // return length
#endif
  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ ret(0);

  __ BIND(L_key_192_256);
  // here rax = len in ints of AESCrypt.KLE array (52=192, or 60=256)
  load_key(xmm_key11, key, 0xb0, xmm_key_shuf_mask);
  load_key(xmm_key12, key, 0xc0, xmm_key_shuf_mask);
  __ cmpl(rax, 52);
  __ jcc(Assembler::notEqual, L_key_256);

  // 192-bit code follows here (could be changed to use more xmm registers)
  __ movptr(pos, 0);
  __ align(OptoLoopAlignment);

  __ BIND(L_loopTop_192);
  __ movdqu(xmm_temp, Address(from, pos, Address::times_1, 0));   // get next 16 bytes of input
  __ pxor  (xmm_result, xmm_temp);               // xor with the current r vector
  __ pxor  (xmm_result, xmm_key0);               // do the aes rounds
  for (int rnum = XMM_REG_NUM_KEY_FIRST + 1; rnum  <= XMM_REG_NUM_KEY_FIRST + 11; rnum++) {
    __ aesenc(xmm_result, as_XMMRegister(rnum));
  }
  __ aesenclast(xmm_result, xmm_key12);
  __ movdqu(Address(to, pos, Address::times_1, 0), xmm_result);     // store into the next 16 bytes of output
  // no need to store r to memory until we exit
  __ addptr(pos, AESBlockSize);
  __ subptr(len_reg, AESBlockSize);
  __ jcc(Assembler::notEqual, L_loopTop_192);
  __ jmp(L_exit);

  __ BIND(L_key_256);
  // 256-bit code follows here (could be changed to use more xmm registers)
  load_key(xmm_key13, key, 0xd0, xmm_key_shuf_mask);
  __ movptr(pos, 0);
  __ align(OptoLoopAlignment);

  __ BIND(L_loopTop_256);
  __ movdqu(xmm_temp, Address(from, pos, Address::times_1, 0));   // get next 16 bytes of input
  __ pxor  (xmm_result, xmm_temp);               // xor with the current r vector
  __ pxor  (xmm_result, xmm_key0);               // do the aes rounds
  for (int rnum = XMM_REG_NUM_KEY_FIRST + 1; rnum  <= XMM_REG_NUM_KEY_FIRST + 13; rnum++) {
    __ aesenc(xmm_result, as_XMMRegister(rnum));
  }
  load_key(xmm_temp, key, 0xe0, r10 /*rscratch*/);
  __ aesenclast(xmm_result, xmm_temp);
  __ movdqu(Address(to, pos, Address::times_1, 0), xmm_result);     // store into the next 16 bytes of output
  // no need to store r to memory until we exit
  __ addptr(pos, AESBlockSize);
  __ subptr(len_reg, AESBlockSize);
  __ jcc(Assembler::notEqual, L_loopTop_256);
  __ jmp(L_exit);

  return start;
}

// This is a version of CBC/AES Decrypt which does 4 blocks in a loop at a time
// to hide instruction latency
//
// Arguments:
//
// Inputs:
//   c_rarg0   - source byte array address
//   c_rarg1   - destination byte array address
//   c_rarg2   - K (key) in little endian int array
//   c_rarg3   - r vector byte array address
//   c_rarg4   - input length
//
// Output:
//   rax       - input length
//
address StubGenerator::generate_cipherBlockChaining_decryptAESCrypt_Parallel() {
  assert(UseAES, "need AES instructions and misaligned SSE support");
  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", "cipherBlockChaining_decryptAESCrypt");
  address start = __ pc();

  const Register from        = c_rarg0;  // source array address
  const Register to          = c_rarg1;  // destination array address
  const Register key         = c_rarg2;  // key array address
  const Register rvec        = c_rarg3;  // r byte array initialized from initvector array address
                                         // and left with the results of the last encryption block
#ifndef _WIN64
  const Register len_reg     = c_rarg4;  // src len (must be multiple of blocksize 16)
#else
  const Address  len_mem(rbp, 6 * wordSize);  // length is on stack on Win64
  const Register len_reg     = r11;      // pick the volatile windows register
#endif
  const Register pos         = rax;

  const int PARALLEL_FACTOR = 4;
  const int ROUNDS[3] = { 10, 12, 14 }; // aes rounds for key128, key192, key256

  Label L_exit;
  Label L_singleBlock_loopTopHead[3]; // 128, 192, 256
  Label L_singleBlock_loopTopHead2[3]; // 128, 192, 256
  Label L_singleBlock_loopTop[3]; // 128, 192, 256
  Label L_multiBlock_loopTopHead[3]; // 128, 192, 256
  Label L_multiBlock_loopTop[3]; // 128, 192, 256

  // keys 0-10 preloaded into xmm5-xmm15
  const int XMM_REG_NUM_KEY_FIRST = 5;
  const int XMM_REG_NUM_KEY_LAST  = 15;
  const XMMRegister xmm_key_first = as_XMMRegister(XMM_REG_NUM_KEY_FIRST);
  const XMMRegister xmm_key_last  = as_XMMRegister(XMM_REG_NUM_KEY_LAST);

  __ enter(); // required for proper stackwalking of RuntimeStub frame

#ifdef _WIN64
  // on win64, fill len_reg from stack position
  __ movl(len_reg, len_mem);
#else
  __ push(len_reg); // Save
#endif
  __ push(rbx);
  // the java expanded key ordering is rotated one position from what we want
  // so we start from 0x10 here and hit 0x00 last
  const XMMRegister xmm_key_shuf_mask = xmm1;  // used temporarily to swap key bytes up front
  __ movdqu(xmm_key_shuf_mask, ExternalAddress(key_shuffle_mask_addr()), rbx /*rscratch*/);
  // load up xmm regs 5 thru 15 with key 0x10 - 0xa0 - 0x00
  for (int rnum = XMM_REG_NUM_KEY_FIRST, offset = 0x10; rnum < XMM_REG_NUM_KEY_LAST; rnum++) {
    load_key(as_XMMRegister(rnum), key, offset, xmm_key_shuf_mask);
    offset += 0x10;
  }
  load_key(xmm_key_last, key, 0x00, xmm_key_shuf_mask);

  const XMMRegister xmm_prev_block_cipher = xmm1;  // holds cipher of previous block

  // registers holding the four results in the parallelized loop
  const XMMRegister xmm_result0 = xmm0;
  const XMMRegister xmm_result1 = xmm2;
  const XMMRegister xmm_result2 = xmm3;
  const XMMRegister xmm_result3 = xmm4;

  __ movdqu(xmm_prev_block_cipher, Address(rvec, 0x00));   // initialize with initial rvec

  __ xorptr(pos, pos);

  // now split to different paths depending on the keylen (len in ints of AESCrypt.KLE array (52=192, or 60=256))
  __ movl(rbx, Address(key, arrayOopDesc::length_offset_in_bytes() - arrayOopDesc::base_offset_in_bytes(T_INT)));
  __ cmpl(rbx, 52);
  __ jcc(Assembler::equal, L_multiBlock_loopTopHead[1]);
  __ cmpl(rbx, 60);
  __ jcc(Assembler::equal, L_multiBlock_loopTopHead[2]);

#define DoFour(opc, src_reg)           \
__ opc(xmm_result0, src_reg);         \
__ opc(xmm_result1, src_reg);         \
__ opc(xmm_result2, src_reg);         \
__ opc(xmm_result3, src_reg);         \

  for (int k = 0; k < 3; ++k) {
    __ BIND(L_multiBlock_loopTopHead[k]);
    if (k != 0) {
      __ cmpptr(len_reg, PARALLEL_FACTOR * AESBlockSize); // see if at least 4 blocks left
      __ jcc(Assembler::less, L_singleBlock_loopTopHead2[k]);
    }
    if (k == 1) {
      __ subptr(rsp, 6 * wordSize);
      __ movdqu(Address(rsp, 0), xmm15); //save last_key from xmm15
      load_key(xmm15, key, 0xb0, rbx /*rscratch*/); // 0xb0; 192-bit key goes up to 0xc0
      __ movdqu(Address(rsp, 2 * wordSize), xmm15);
      load_key(xmm1, key, 0xc0, rbx /*rscratch*/);  // 0xc0;
      __ movdqu(Address(rsp, 4 * wordSize), xmm1);
    } else if (k == 2) {
      __ subptr(rsp, 10 * wordSize);
      __ movdqu(Address(rsp, 0), xmm15); //save last_key from xmm15
      load_key(xmm15, key, 0xd0, rbx /*rscratch*/); // 0xd0; 256-bit key goes up to 0xe0
      __ movdqu(Address(rsp, 6 * wordSize), xmm15);
      load_key(xmm1, key, 0xe0, rbx /*rscratch*/);  // 0xe0;
      __ movdqu(Address(rsp, 8 * wordSize), xmm1);
      load_key(xmm15, key, 0xb0, rbx /*rscratch*/); // 0xb0;
      __ movdqu(Address(rsp, 2 * wordSize), xmm15);
      load_key(xmm1, key, 0xc0, rbx /*rscratch*/);  // 0xc0;
      __ movdqu(Address(rsp, 4 * wordSize), xmm1);
    }
    __ align(OptoLoopAlignment);
    __ BIND(L_multiBlock_loopTop[k]);
    __ cmpptr(len_reg, PARALLEL_FACTOR * AESBlockSize); // see if at least 4 blocks left
    __ jcc(Assembler::less, L_singleBlock_loopTopHead[k]);

    if  (k != 0) {
      __ movdqu(xmm15, Address(rsp, 2 * wordSize));
      __ movdqu(xmm1, Address(rsp, 4 * wordSize));
    }

    __ movdqu(xmm_result0, Address(from, pos, Address::times_1, 0 * AESBlockSize)); // get next 4 blocks into xmmresult registers
    __ movdqu(xmm_result1, Address(from, pos, Address::times_1, 1 * AESBlockSize));
    __ movdqu(xmm_result2, Address(from, pos, Address::times_1, 2 * AESBlockSize));
    __ movdqu(xmm_result3, Address(from, pos, Address::times_1, 3 * AESBlockSize));

    DoFour(pxor, xmm_key_first);
    if (k == 0) {
      for (int rnum = 1; rnum < ROUNDS[k]; rnum++) {
        DoFour(aesdec, as_XMMRegister(rnum + XMM_REG_NUM_KEY_FIRST));
      }
      DoFour(aesdeclast, xmm_key_last);
    } else if (k == 1) {
      for (int rnum = 1; rnum <= ROUNDS[k]-2; rnum++) {
        DoFour(aesdec, as_XMMRegister(rnum + XMM_REG_NUM_KEY_FIRST));
      }
      __ movdqu(xmm_key_last, Address(rsp, 0)); // xmm15 needs to be loaded again.
      DoFour(aesdec, xmm1);  // key : 0xc0
      __ movdqu(xmm_prev_block_cipher, Address(rvec, 0x00));  // xmm1 needs to be loaded again
      DoFour(aesdeclast, xmm_key_last);
    } else if (k == 2) {
      for (int rnum = 1; rnum <= ROUNDS[k] - 4; rnum++) {
        DoFour(aesdec, as_XMMRegister(rnum + XMM_REG_NUM_KEY_FIRST));
      }
      DoFour(aesdec, xmm1);  // key : 0xc0
      __ movdqu(xmm15, Address(rsp, 6 * wordSize));
      __ movdqu(xmm1, Address(rsp, 8 * wordSize));
      DoFour(aesdec, xmm15);  // key : 0xd0
      __ movdqu(xmm_key_last, Address(rsp, 0)); // xmm15 needs to be loaded again.
      DoFour(aesdec, xmm1);  // key : 0xe0
      __ movdqu(xmm_prev_block_cipher, Address(rvec, 0x00));  // xmm1 needs to be loaded again
      DoFour(aesdeclast, xmm_key_last);
    }

    // for each result, xor with the r vector of previous cipher block
    __ pxor(xmm_result0, xmm_prev_block_cipher);
    __ movdqu(xmm_prev_block_cipher, Address(from, pos, Address::times_1, 0 * AESBlockSize));
    __ pxor(xmm_result1, xmm_prev_block_cipher);
    __ movdqu(xmm_prev_block_cipher, Address(from, pos, Address::times_1, 1 * AESBlockSize));
    __ pxor(xmm_result2, xmm_prev_block_cipher);
    __ movdqu(xmm_prev_block_cipher, Address(from, pos, Address::times_1, 2 * AESBlockSize));
    __ pxor(xmm_result3, xmm_prev_block_cipher);
    __ movdqu(xmm_prev_block_cipher, Address(from, pos, Address::times_1, 3 * AESBlockSize));   // this will carry over to next set of blocks
    if (k != 0) {
      __ movdqu(Address(rvec, 0x00), xmm_prev_block_cipher);
    }

    __ movdqu(Address(to, pos, Address::times_1, 0 * AESBlockSize), xmm_result0);     // store 4 results into the next 64 bytes of output
    __ movdqu(Address(to, pos, Address::times_1, 1 * AESBlockSize), xmm_result1);
    __ movdqu(Address(to, pos, Address::times_1, 2 * AESBlockSize), xmm_result2);
    __ movdqu(Address(to, pos, Address::times_1, 3 * AESBlockSize), xmm_result3);

    __ addptr(pos, PARALLEL_FACTOR * AESBlockSize);
    __ subptr(len_reg, PARALLEL_FACTOR * AESBlockSize);
    __ jmp(L_multiBlock_loopTop[k]);

    // registers used in the non-parallelized loops
    // xmm register assignments for the loops below
    const XMMRegister xmm_result = xmm0;
    const XMMRegister xmm_prev_block_cipher_save = xmm2;
    const XMMRegister xmm_key11 = xmm3;
    const XMMRegister xmm_key12 = xmm4;
    const XMMRegister key_tmp = xmm4;

    __ BIND(L_singleBlock_loopTopHead[k]);
    if (k == 1) {
      __ addptr(rsp, 6 * wordSize);
    } else if (k == 2) {
      __ addptr(rsp, 10 * wordSize);
    }
    __ cmpptr(len_reg, 0); // any blocks left??
    __ jcc(Assembler::equal, L_exit);
    __ BIND(L_singleBlock_loopTopHead2[k]);
    if (k == 1) {
      load_key(xmm_key11, key, 0xb0, rbx /*rscratch*/); // 0xb0; 192-bit key goes up to 0xc0
      load_key(xmm_key12, key, 0xc0, rbx /*rscratch*/); // 0xc0; 192-bit key goes up to 0xc0
    }
    if (k == 2) {
      load_key(xmm_key11, key, 0xb0, rbx /*rscratch*/); // 0xb0; 256-bit key goes up to 0xe0
    }
    __ align(OptoLoopAlignment);
    __ BIND(L_singleBlock_loopTop[k]);
    __ movdqu(xmm_result, Address(from, pos, Address::times_1, 0)); // get next 16 bytes of cipher input
    __ movdqa(xmm_prev_block_cipher_save, xmm_result); // save for next r vector
    __ pxor(xmm_result, xmm_key_first); // do the aes dec rounds
    for (int rnum = 1; rnum <= 9 ; rnum++) {
        __ aesdec(xmm_result, as_XMMRegister(rnum + XMM_REG_NUM_KEY_FIRST));
    }
    if (k == 1) {
      __ aesdec(xmm_result, xmm_key11);
      __ aesdec(xmm_result, xmm_key12);
    }
    if (k == 2) {
      __ aesdec(xmm_result, xmm_key11);
      load_key(key_tmp, key, 0xc0, rbx /*rscratch*/);
      __ aesdec(xmm_result, key_tmp);
      load_key(key_tmp, key, 0xd0, rbx /*rscratch*/);
      __ aesdec(xmm_result, key_tmp);
      load_key(key_tmp, key, 0xe0, rbx /*rscratch*/);
      __ aesdec(xmm_result, key_tmp);
    }

    __ aesdeclast(xmm_result, xmm_key_last); // xmm15 always came from key+0
    __ pxor(xmm_result, xmm_prev_block_cipher); // xor with the current r vector
    __ movdqu(Address(to, pos, Address::times_1, 0), xmm_result); // store into the next 16 bytes of output
    // no need to store r to memory until we exit
    __ movdqa(xmm_prev_block_cipher, xmm_prev_block_cipher_save); // set up next r vector with cipher input from this block
    __ addptr(pos, AESBlockSize);
    __ subptr(len_reg, AESBlockSize);
    __ jcc(Assembler::notEqual, L_singleBlock_loopTop[k]);
    if (k != 2) {
      __ jmp(L_exit);
    }
  } //for 128/192/256

  __ BIND(L_exit);
  __ movdqu(Address(rvec, 0), xmm_prev_block_cipher);     // final value of r stored in rvec of CipherBlockChaining object
  __ pop(rbx);
#ifdef _WIN64
  __ movl(rax, len_mem);
#else
  __ pop(rax); // return length
#endif
  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ ret(0);

  return start;
}

address StubGenerator::generate_electronicCodeBook_encryptAESCrypt() {
  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", "electronicCodeBook_encryptAESCrypt");
  address start = __ pc();

  const Register from = c_rarg0;  // source array address
  const Register to = c_rarg1;  // destination array address
  const Register key = c_rarg2;  // key array address
  const Register len = c_rarg3;  // src len (must be multiple of blocksize 16)
  __ enter(); // required for proper stackwalking of RuntimeStub frame

  aesecb_encrypt(from, to, key, len);

  __ vzeroupper();
  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ ret(0);

  return start;
 }

address StubGenerator::generate_electronicCodeBook_decryptAESCrypt() {
  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", "electronicCodeBook_decryptAESCrypt");
  address start = __ pc();

  const Register from = c_rarg0;  // source array address
  const Register to = c_rarg1;  // destination array address
  const Register key = c_rarg2;  // key array address
  const Register len = c_rarg3;  // src len (must be multiple of blocksize 16)
  __ enter(); // required for proper stackwalking of RuntimeStub frame

  aesecb_decrypt(from, to, key, len);

  __ vzeroupper();
  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ ret(0);

  return start;
}

// Utility routine for increase 128bit counter (iv in CTR mode)
void StubGenerator::inc_counter(Register reg, XMMRegister xmmdst, int inc_delta, Label& next_block) {
  __ pextrq(reg, xmmdst, 0x0);
  __ addq(reg, inc_delta);
  __ pinsrq(xmmdst, reg, 0x0);
  __ jcc(Assembler::carryClear, next_block); // jump if no carry
  __ pextrq(reg, xmmdst, 0x01); // Carry
  __ addq(reg, 0x01);
  __ pinsrq(xmmdst, reg, 0x01); //Carry end
  __ BIND(next_block);          // next instruction
}


void StubGenerator::roundEnc(XMMRegister key, int rnum) {
  for (int xmm_reg_no = 0; xmm_reg_no <=rnum; xmm_reg_no++) {
    __ vaesenc(as_XMMRegister(xmm_reg_no), as_XMMRegister(xmm_reg_no), key, Assembler::AVX_512bit);
  }
}

void StubGenerator::lastroundEnc(XMMRegister key, int rnum) {
  for (int xmm_reg_no = 0; xmm_reg_no <=rnum; xmm_reg_no++) {
    __ vaesenclast(as_XMMRegister(xmm_reg_no), as_XMMRegister(xmm_reg_no), key, Assembler::AVX_512bit);
  }
}

void StubGenerator::roundDec(XMMRegister key, int rnum) {
  for (int xmm_reg_no = 0; xmm_reg_no <=rnum; xmm_reg_no++) {
    __ vaesdec(as_XMMRegister(xmm_reg_no), as_XMMRegister(xmm_reg_no), key, Assembler::AVX_512bit);
  }
}

void StubGenerator::lastroundDec(XMMRegister key, int rnum) {
  for (int xmm_reg_no = 0; xmm_reg_no <=rnum; xmm_reg_no++) {
    __ vaesdeclast(as_XMMRegister(xmm_reg_no), as_XMMRegister(xmm_reg_no), key, Assembler::AVX_512bit);
  }
}

void StubGenerator::roundDec(XMMRegister xmm_reg) {
  __ vaesdec(xmm1, xmm1, xmm_reg, Assembler::AVX_512bit);
  __ vaesdec(xmm2, xmm2, xmm_reg, Assembler::AVX_512bit);
  __ vaesdec(xmm3, xmm3, xmm_reg, Assembler::AVX_512bit);
  __ vaesdec(xmm4, xmm4, xmm_reg, Assembler::AVX_512bit);
  __ vaesdec(xmm5, xmm5, xmm_reg, Assembler::AVX_512bit);
  __ vaesdec(xmm6, xmm6, xmm_reg, Assembler::AVX_512bit);
  __ vaesdec(xmm7, xmm7, xmm_reg, Assembler::AVX_512bit);
  __ vaesdec(xmm8, xmm8, xmm_reg, Assembler::AVX_512bit);
}

void StubGenerator::roundDeclast(XMMRegister xmm_reg) {
  __ vaesdeclast(xmm1, xmm1, xmm_reg, Assembler::AVX_512bit);
  __ vaesdeclast(xmm2, xmm2, xmm_reg, Assembler::AVX_512bit);
  __ vaesdeclast(xmm3, xmm3, xmm_reg, Assembler::AVX_512bit);
  __ vaesdeclast(xmm4, xmm4, xmm_reg, Assembler::AVX_512bit);
  __ vaesdeclast(xmm5, xmm5, xmm_reg, Assembler::AVX_512bit);
  __ vaesdeclast(xmm6, xmm6, xmm_reg, Assembler::AVX_512bit);
  __ vaesdeclast(xmm7, xmm7, xmm_reg, Assembler::AVX_512bit);
  __ vaesdeclast(xmm8, xmm8, xmm_reg, Assembler::AVX_512bit);
}


// Utility routine for loading a 128-bit key word in little endian format
void StubGenerator::load_key(XMMRegister xmmdst, Register key, int offset, XMMRegister xmm_shuf_mask) {
  __ movdqu(xmmdst, Address(key, offset));
  __ pshufb(xmmdst, xmm_shuf_mask);
}

void StubGenerator::load_key(XMMRegister xmmdst, Register key, int offset, Register rscratch) {
  __ movdqu(xmmdst, Address(key, offset));
  __ pshufb(xmmdst, ExternalAddress(key_shuffle_mask_addr()), rscratch);
}

void StubGenerator::ev_load_key(XMMRegister xmmdst, Register key, int offset, XMMRegister xmm_shuf_mask) {
  __ movdqu(xmmdst, Address(key, offset));
  __ pshufb(xmmdst, xmm_shuf_mask);
  __ evshufi64x2(xmmdst, xmmdst, xmmdst, 0x0, Assembler::AVX_512bit);
}

void StubGenerator::ev_load_key(XMMRegister xmmdst, Register key, int offset, Register rscratch) {
  __ movdqu(xmmdst, Address(key, offset));
  __ pshufb(xmmdst, ExternalAddress(key_shuffle_mask_addr()), rscratch);
  __ evshufi64x2(xmmdst, xmmdst, xmmdst, 0x0, Assembler::AVX_512bit);
}

// Add 128-bit integers in xmmsrc1 to xmmsrc2, then place the result in xmmdst.
// Clobber ktmp and rscratch.
// Used by aesctr_encrypt.
void StubGenerator::ev_add128(XMMRegister xmmdst, XMMRegister xmmsrc1, XMMRegister xmmsrc2,
                              int vector_len, KRegister ktmp, XMMRegister ones) {
  __ vpaddq(xmmdst, xmmsrc1, xmmsrc2, vector_len);
  __ evpcmpuq(ktmp, xmmdst, xmmsrc2, __ lt, vector_len); // set mask[0/1] bit if addq to dst[0/1] wraps
  __ kshiftlbl(ktmp, ktmp, 1);                        // mask[1] <- mask[0], mask[0] <- 0, etc

  __ evpaddq(xmmdst, ktmp, xmmdst, ones, /*merge*/true, vector_len); // dst[1]++ if mask[1] set
}

// AES-ECB Encrypt Operation
void StubGenerator::aesecb_encrypt(Register src_addr, Register dest_addr, Register key, Register len) {
  const Register pos = rax;
  const Register rounds = r12;

  Label NO_PARTS, LOOP, Loop_start, LOOP2, AES192, END_LOOP, AES256, REMAINDER, LAST2, END, KEY_192, KEY_256, EXIT;
  __ push(r13);
  __ push(r12);

  // For EVEX with VL and BW, provide a standard mask, VL = 128 will guide the merge
  // context for the registers used, where all instructions below are using 128-bit mode
  // On EVEX without VL and BW, these instructions will all be AVX.
  if (VM_Version::supports_avx512vlbw()) {
    __ movl(rax, 0xffff);
    __ kmovql(k1, rax);
  }
  __ push(len); // Save
  __ push(rbx);

  __ vzeroupper();

  __ xorptr(pos, pos);

  // Calculate number of rounds based on key length(128, 192, 256):44 for 10-rounds, 52 for 12-rounds, 60 for 14-rounds
  __ movl(rounds, Address(key, arrayOopDesc::length_offset_in_bytes() - arrayOopDesc::base_offset_in_bytes(T_INT)));

  // Load Key shuf mask
  const XMMRegister xmm_key_shuf_mask = xmm31;  // used temporarily to swap key bytes up front
  __ movdqu(xmm_key_shuf_mask, ExternalAddress(key_shuffle_mask_addr()), rbx /*rscratch*/);

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
  __ cmpl(rounds, 52);
  __ jcc(Assembler::greaterEqual, KEY_192);
  __ jmp(Loop_start);

  __ bind(KEY_192);
  ev_load_key(xmm19, key, 11 * 16, xmm_key_shuf_mask);
  ev_load_key(xmm20, key, 12 * 16, xmm_key_shuf_mask);
  __ cmpl(rounds, 60);
  __ jcc(Assembler::equal, KEY_256);
  __ jmp(Loop_start);

  __ bind(KEY_256);
  ev_load_key(xmm21, key, 13 * 16, xmm_key_shuf_mask);
  ev_load_key(xmm22, key, 14 * 16, xmm_key_shuf_mask);

  __ bind(Loop_start);
  __ movq(rbx, len);
  // Divide length by 16 to convert it to number of blocks
  __ shrq(len, 4);
  __ shlq(rbx, 60);
  __ jcc(Assembler::equal, NO_PARTS);
  __ addq(len, 1);
  // Check if number of blocks is greater than or equal to 32
  // If true, 512 bytes are processed at a time (code marked by label LOOP)
  // If not, 16 bytes are processed (code marked by REMAINDER label)
  __ bind(NO_PARTS);
  __ movq(rbx, len);
  __ shrq(len, 5);
  __ jcc(Assembler::equal, REMAINDER);
  __ movl(r13, len);
  // Compute number of blocks that will be processed 512 bytes at a time
  // Subtract this from the total number of blocks which will then be processed by REMAINDER loop
  __ shlq(r13, 5);
  __ subq(rbx, r13);
  //Begin processing 512 bytes
  __ bind(LOOP);
  // Move 64 bytes of PT data into a zmm register, as a result 512 bytes of PT loaded in zmm0-7
  __ evmovdquq(xmm0, Address(src_addr, pos, Address::times_1, 0 * 64), Assembler::AVX_512bit);
  __ evmovdquq(xmm1, Address(src_addr, pos, Address::times_1, 1 * 64), Assembler::AVX_512bit);
  __ evmovdquq(xmm2, Address(src_addr, pos, Address::times_1, 2 * 64), Assembler::AVX_512bit);
  __ evmovdquq(xmm3, Address(src_addr, pos, Address::times_1, 3 * 64), Assembler::AVX_512bit);
  __ evmovdquq(xmm4, Address(src_addr, pos, Address::times_1, 4 * 64), Assembler::AVX_512bit);
  __ evmovdquq(xmm5, Address(src_addr, pos, Address::times_1, 5 * 64), Assembler::AVX_512bit);
  __ evmovdquq(xmm6, Address(src_addr, pos, Address::times_1, 6 * 64), Assembler::AVX_512bit);
  __ evmovdquq(xmm7, Address(src_addr, pos, Address::times_1, 7 * 64), Assembler::AVX_512bit);
  // Xor with the first round key
  __ evpxorq(xmm0, xmm0, xmm8, Assembler::AVX_512bit);
  __ evpxorq(xmm1, xmm1, xmm8, Assembler::AVX_512bit);
  __ evpxorq(xmm2, xmm2, xmm8, Assembler::AVX_512bit);
  __ evpxorq(xmm3, xmm3, xmm8, Assembler::AVX_512bit);
  __ evpxorq(xmm4, xmm4, xmm8, Assembler::AVX_512bit);
  __ evpxorq(xmm5, xmm5, xmm8, Assembler::AVX_512bit);
  __ evpxorq(xmm6, xmm6, xmm8, Assembler::AVX_512bit);
  __ evpxorq(xmm7, xmm7, xmm8, Assembler::AVX_512bit);
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
  __ cmpl(rounds, 52);
  __ jcc(Assembler::aboveEqual, AES192);
  // Aesenclast round operation for keysize = 128
  lastroundEnc(xmm24, 7);
  __ jmp(END_LOOP);
  //Additional 2 rounds of Aesenc operation for keysize = 192
  __ bind(AES192);
  roundEnc(xmm24, 7);
  roundEnc(xmm19, 7);
  __ cmpl(rounds, 60);
  __ jcc(Assembler::aboveEqual, AES256);
  // Aesenclast round for keysize = 192
  lastroundEnc(xmm20, 7);
  __ jmp(END_LOOP);
  // 2 rounds of Aesenc operation and Aesenclast for keysize = 256
  __ bind(AES256);
  roundEnc(xmm20, 7);
  roundEnc(xmm21, 7);
  lastroundEnc(xmm22, 7);

  __ bind(END_LOOP);
  // Move 512 bytes of CT to destination
  __ evmovdquq(Address(dest_addr, pos, Address::times_1, 0 * 64), xmm0, Assembler::AVX_512bit);
  __ evmovdquq(Address(dest_addr, pos, Address::times_1, 1 * 64), xmm1, Assembler::AVX_512bit);
  __ evmovdquq(Address(dest_addr, pos, Address::times_1, 2 * 64), xmm2, Assembler::AVX_512bit);
  __ evmovdquq(Address(dest_addr, pos, Address::times_1, 3 * 64), xmm3, Assembler::AVX_512bit);
  __ evmovdquq(Address(dest_addr, pos, Address::times_1, 4 * 64), xmm4, Assembler::AVX_512bit);
  __ evmovdquq(Address(dest_addr, pos, Address::times_1, 5 * 64), xmm5, Assembler::AVX_512bit);
  __ evmovdquq(Address(dest_addr, pos, Address::times_1, 6 * 64), xmm6, Assembler::AVX_512bit);
  __ evmovdquq(Address(dest_addr, pos, Address::times_1, 7 * 64), xmm7, Assembler::AVX_512bit);

  __ addq(pos, 512);
  __ decq(len);
  __ jcc(Assembler::notEqual, LOOP);

  __ bind(REMAINDER);
  __ vzeroupper();
  __ cmpq(rbx, 0);
  __ jcc(Assembler::equal, END);
  // Process 16 bytes at a time
  __ bind(LOOP2);
  __ movdqu(xmm1, Address(src_addr, pos, Address::times_1, 0));
  __ vpxor(xmm1, xmm1, xmm8, Assembler::AVX_128bit);
  // xmm2 contains shuffled key for Aesenclast operation.
  __ vmovdqu(xmm2, xmm24);

  __ vaesenc(xmm1, xmm1, xmm9, Assembler::AVX_128bit);
  __ vaesenc(xmm1, xmm1, xmm10, Assembler::AVX_128bit);
  __ vaesenc(xmm1, xmm1, xmm23, Assembler::AVX_128bit);
  __ vaesenc(xmm1, xmm1, xmm12, Assembler::AVX_128bit);
  __ vaesenc(xmm1, xmm1, xmm13, Assembler::AVX_128bit);
  __ vaesenc(xmm1, xmm1, xmm14, Assembler::AVX_128bit);
  __ vaesenc(xmm1, xmm1, xmm15, Assembler::AVX_128bit);
  __ vaesenc(xmm1, xmm1, xmm16, Assembler::AVX_128bit);
  __ vaesenc(xmm1, xmm1, xmm17, Assembler::AVX_128bit);

  __ cmpl(rounds, 52);
  __ jcc(Assembler::below, LAST2);
  __ vmovdqu(xmm2, xmm20);
  __ vaesenc(xmm1, xmm1, xmm24, Assembler::AVX_128bit);
  __ vaesenc(xmm1, xmm1, xmm19, Assembler::AVX_128bit);
  __ cmpl(rounds, 60);
  __ jcc(Assembler::below, LAST2);
  __ vmovdqu(xmm2, xmm22);
  __ vaesenc(xmm1, xmm1, xmm20, Assembler::AVX_128bit);
  __ vaesenc(xmm1, xmm1, xmm21, Assembler::AVX_128bit);

  __ bind(LAST2);
  // Aesenclast round
  __ vaesenclast(xmm1, xmm1, xmm2, Assembler::AVX_128bit);
  // Write 16 bytes of CT to destination
  __ movdqu(Address(dest_addr, pos, Address::times_1, 0), xmm1);
  __ addq(pos, 16);
  __ decq(rbx);
  __ jcc(Assembler::notEqual, LOOP2);

  __ bind(END);
  // Zero out the round keys
  __ evpxorq(xmm8, xmm8, xmm8, Assembler::AVX_512bit);
  __ evpxorq(xmm9, xmm9, xmm9, Assembler::AVX_512bit);
  __ evpxorq(xmm10, xmm10, xmm10, Assembler::AVX_512bit);
  __ evpxorq(xmm23, xmm23, xmm23, Assembler::AVX_512bit);
  __ evpxorq(xmm12, xmm12, xmm12, Assembler::AVX_512bit);
  __ evpxorq(xmm13, xmm13, xmm13, Assembler::AVX_512bit);
  __ evpxorq(xmm14, xmm14, xmm14, Assembler::AVX_512bit);
  __ evpxorq(xmm15, xmm15, xmm15, Assembler::AVX_512bit);
  __ evpxorq(xmm16, xmm16, xmm16, Assembler::AVX_512bit);
  __ evpxorq(xmm17, xmm17, xmm17, Assembler::AVX_512bit);
  __ evpxorq(xmm24, xmm24, xmm24, Assembler::AVX_512bit);
  __ cmpl(rounds, 44);
  __ jcc(Assembler::belowEqual, EXIT);
  __ evpxorq(xmm19, xmm19, xmm19, Assembler::AVX_512bit);
  __ evpxorq(xmm20, xmm20, xmm20, Assembler::AVX_512bit);
  __ cmpl(rounds, 52);
  __ jcc(Assembler::belowEqual, EXIT);
  __ evpxorq(xmm21, xmm21, xmm21, Assembler::AVX_512bit);
  __ evpxorq(xmm22, xmm22, xmm22, Assembler::AVX_512bit);
  __ bind(EXIT);
  __ pop(rbx);
  __ pop(rax); // return length
  __ pop(r12);
  __ pop(r13);
}

// AES-ECB Decrypt Operation
void StubGenerator::aesecb_decrypt(Register src_addr, Register dest_addr, Register key, Register len)  {

  Label NO_PARTS, LOOP, Loop_start, LOOP2, AES192, END_LOOP, AES256, REMAINDER, LAST2, END, KEY_192, KEY_256, EXIT;
  const Register pos = rax;
  const Register rounds = r12;
  __ push(r13);
  __ push(r12);

  // For EVEX with VL and BW, provide a standard mask, VL = 128 will guide the merge
  // context for the registers used, where all instructions below are using 128-bit mode
  // On EVEX without VL and BW, these instructions will all be AVX.
  if (VM_Version::supports_avx512vlbw()) {
    __ movl(rax, 0xffff);
    __ kmovql(k1, rax);
  }

  __ push(len); // Save
  __ push(rbx);

  __ vzeroupper();

  __ xorptr(pos, pos);
  // Calculate number of rounds i.e. based on key length(128, 192, 256):44 for 10-rounds, 52 for 12-rounds, 60 for 14-rounds
  __ movl(rounds, Address(key, arrayOopDesc::length_offset_in_bytes() - arrayOopDesc::base_offset_in_bytes(T_INT)));

  // Load Key shuf mask
  const XMMRegister xmm_key_shuf_mask = xmm31;  // used temporarily to swap key bytes up front
  __ movdqu(xmm_key_shuf_mask, ExternalAddress(key_shuffle_mask_addr()), rbx /*rscratch*/);

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
  __ cmpl(rounds, 52);
  __ jcc(Assembler::greaterEqual, KEY_192);
  __ jmp(Loop_start);

  __ bind(KEY_192);
  ev_load_key(xmm19, key, 11 * 16, xmm_key_shuf_mask);
  ev_load_key(xmm20, key, 12 * 16, xmm_key_shuf_mask);
  __ cmpl(rounds, 60);
  __ jcc(Assembler::equal, KEY_256);
  __ jmp(Loop_start);

  __ bind(KEY_256);
  ev_load_key(xmm21, key, 13 * 16, xmm_key_shuf_mask);
  ev_load_key(xmm22, key, 14 * 16, xmm_key_shuf_mask);
  __ bind(Loop_start);
  __ movq(rbx, len);
  // Convert input length to number of blocks
  __ shrq(len, 4);
  __ shlq(rbx, 60);
  __ jcc(Assembler::equal, NO_PARTS);
  __ addq(len, 1);
  // Check if number of blocks is greater than/ equal to 32
  // If true, blocks then 512 bytes are processed at a time (code marked by label LOOP)
  // If not, 16 bytes are processed (code marked by label REMAINDER)
  __ bind(NO_PARTS);
  __ movq(rbx, len);
  __ shrq(len, 5);
  __ jcc(Assembler::equal, REMAINDER);
  __ movl(r13, len);
  // Compute number of blocks that will be processed as 512 bytes at a time
  // Subtract this from the total number of blocks, which will then be processed by REMAINDER loop.
  __ shlq(r13, 5);
  __ subq(rbx, r13);

  __ bind(LOOP);
  // Move 64 bytes of CT data into a zmm register, as a result 512 bytes of CT loaded in zmm0-7
  __ evmovdquq(xmm0, Address(src_addr, pos, Address::times_1, 0 * 64), Assembler::AVX_512bit);
  __ evmovdquq(xmm1, Address(src_addr, pos, Address::times_1, 1 * 64), Assembler::AVX_512bit);
  __ evmovdquq(xmm2, Address(src_addr, pos, Address::times_1, 2 * 64), Assembler::AVX_512bit);
  __ evmovdquq(xmm3, Address(src_addr, pos, Address::times_1, 3 * 64), Assembler::AVX_512bit);
  __ evmovdquq(xmm4, Address(src_addr, pos, Address::times_1, 4 * 64), Assembler::AVX_512bit);
  __ evmovdquq(xmm5, Address(src_addr, pos, Address::times_1, 5 * 64), Assembler::AVX_512bit);
  __ evmovdquq(xmm6, Address(src_addr, pos, Address::times_1, 6 * 64), Assembler::AVX_512bit);
  __ evmovdquq(xmm7, Address(src_addr, pos, Address::times_1, 7 * 64), Assembler::AVX_512bit);
  // Xor with the first round key
  __ evpxorq(xmm0, xmm0, xmm9, Assembler::AVX_512bit);
  __ evpxorq(xmm1, xmm1, xmm9, Assembler::AVX_512bit);
  __ evpxorq(xmm2, xmm2, xmm9, Assembler::AVX_512bit);
  __ evpxorq(xmm3, xmm3, xmm9, Assembler::AVX_512bit);
  __ evpxorq(xmm4, xmm4, xmm9, Assembler::AVX_512bit);
  __ evpxorq(xmm5, xmm5, xmm9, Assembler::AVX_512bit);
  __ evpxorq(xmm6, xmm6, xmm9, Assembler::AVX_512bit);
  __ evpxorq(xmm7, xmm7, xmm9, Assembler::AVX_512bit);
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
  __ cmpl(rounds, 52);
  __ jcc(Assembler::aboveEqual, AES192);
  // Aesdeclast round for keysize = 128
  lastroundDec(xmm27, 7);
  __ jmp(END_LOOP);

  __ bind(AES192);
  // 2 Additional rounds for keysize = 192
  roundDec(xmm19, 7);
  roundDec(xmm20, 7);
  __ cmpl(rounds, 60);
  __ jcc(Assembler::aboveEqual, AES256);
  // Aesdeclast round for keysize = 192
  lastroundDec(xmm27, 7);
  __ jmp(END_LOOP);
  __ bind(AES256);
  // 2 Additional rounds and Aesdeclast for keysize = 256
  roundDec(xmm21, 7);
  roundDec(xmm22, 7);
  lastroundDec(xmm27, 7);

  __ bind(END_LOOP);
  // Write 512 bytes of PT to the destination
  __ evmovdquq(Address(dest_addr, pos, Address::times_1, 0 * 64), xmm0, Assembler::AVX_512bit);
  __ evmovdquq(Address(dest_addr, pos, Address::times_1, 1 * 64), xmm1, Assembler::AVX_512bit);
  __ evmovdquq(Address(dest_addr, pos, Address::times_1, 2 * 64), xmm2, Assembler::AVX_512bit);
  __ evmovdquq(Address(dest_addr, pos, Address::times_1, 3 * 64), xmm3, Assembler::AVX_512bit);
  __ evmovdquq(Address(dest_addr, pos, Address::times_1, 4 * 64), xmm4, Assembler::AVX_512bit);
  __ evmovdquq(Address(dest_addr, pos, Address::times_1, 5 * 64), xmm5, Assembler::AVX_512bit);
  __ evmovdquq(Address(dest_addr, pos, Address::times_1, 6 * 64), xmm6, Assembler::AVX_512bit);
  __ evmovdquq(Address(dest_addr, pos, Address::times_1, 7 * 64), xmm7, Assembler::AVX_512bit);

  __ addq(pos, 512);
  __ decq(len);
  __ jcc(Assembler::notEqual, LOOP);

  __ bind(REMAINDER);
  __ vzeroupper();
  __ cmpq(rbx, 0);
  __ jcc(Assembler::equal, END);
  // Process 16 bytes at a time
  __ bind(LOOP2);
  __ movdqu(xmm1, Address(src_addr, pos, Address::times_1, 0));
  __ vpxor(xmm1, xmm1, xmm9, Assembler::AVX_128bit);
  // xmm2 contains shuffled key for Aesdeclast operation.
  __ vmovdqu(xmm2, xmm27);

  __ vaesdec(xmm1, xmm1, xmm10, Assembler::AVX_128bit);
  __ vaesdec(xmm1, xmm1, xmm11, Assembler::AVX_128bit);
  __ vaesdec(xmm1, xmm1, xmm12, Assembler::AVX_128bit);
  __ vaesdec(xmm1, xmm1, xmm13, Assembler::AVX_128bit);
  __ vaesdec(xmm1, xmm1, xmm14, Assembler::AVX_128bit);
  __ vaesdec(xmm1, xmm1, xmm15, Assembler::AVX_128bit);
  __ vaesdec(xmm1, xmm1, xmm16, Assembler::AVX_128bit);
  __ vaesdec(xmm1, xmm1, xmm17, Assembler::AVX_128bit);
  __ vaesdec(xmm1, xmm1, xmm18, Assembler::AVX_128bit);

  __ cmpl(rounds, 52);
  __ jcc(Assembler::below, LAST2);
  __ vaesdec(xmm1, xmm1, xmm19, Assembler::AVX_128bit);
  __ vaesdec(xmm1, xmm1, xmm20, Assembler::AVX_128bit);
  __ cmpl(rounds, 60);
  __ jcc(Assembler::below, LAST2);
  __ vaesdec(xmm1, xmm1, xmm21, Assembler::AVX_128bit);
  __ vaesdec(xmm1, xmm1, xmm22, Assembler::AVX_128bit);

  __ bind(LAST2);
  // Aesdeclast round
  __ vaesdeclast(xmm1, xmm1, xmm2, Assembler::AVX_128bit);
  // Write 16 bytes of PT to destination
  __ movdqu(Address(dest_addr, pos, Address::times_1, 0), xmm1);
  __ addq(pos, 16);
  __ decq(rbx);
  __ jcc(Assembler::notEqual, LOOP2);

  __ bind(END);
  // Zero out the round keys
  __ evpxorq(xmm8, xmm8, xmm8, Assembler::AVX_512bit);
  __ evpxorq(xmm9, xmm9, xmm9, Assembler::AVX_512bit);
  __ evpxorq(xmm10, xmm10, xmm10, Assembler::AVX_512bit);
  __ evpxorq(xmm11, xmm11, xmm11, Assembler::AVX_512bit);
  __ evpxorq(xmm12, xmm12, xmm12, Assembler::AVX_512bit);
  __ evpxorq(xmm13, xmm13, xmm13, Assembler::AVX_512bit);
  __ evpxorq(xmm14, xmm14, xmm14, Assembler::AVX_512bit);
  __ evpxorq(xmm15, xmm15, xmm15, Assembler::AVX_512bit);
  __ evpxorq(xmm16, xmm16, xmm16, Assembler::AVX_512bit);
  __ evpxorq(xmm17, xmm17, xmm17, Assembler::AVX_512bit);
  __ evpxorq(xmm18, xmm18, xmm18, Assembler::AVX_512bit);
  __ evpxorq(xmm27, xmm27, xmm27, Assembler::AVX_512bit);
  __ cmpl(rounds, 44);
  __ jcc(Assembler::belowEqual, EXIT);
  __ evpxorq(xmm19, xmm19, xmm19, Assembler::AVX_512bit);
  __ evpxorq(xmm20, xmm20, xmm20, Assembler::AVX_512bit);
  __ cmpl(rounds, 52);
  __ jcc(Assembler::belowEqual, EXIT);
  __ evpxorq(xmm21, xmm21, xmm21, Assembler::AVX_512bit);
  __ evpxorq(xmm22, xmm22, xmm22, Assembler::AVX_512bit);

  __ bind(EXIT);
  __ pop(rbx);
  __ pop(rax); // return length
  __ pop(r12);
  __ pop(r13);
}


// AES Counter Mode using VAES instructions
void StubGenerator::aesctr_encrypt(Register src_addr, Register dest_addr, Register key, Register counter,
  Register len_reg, Register used, Register used_addr, Register saved_encCounter_start) {

  const Register rounds = rax;
  const Register pos = r12;

  Label PRELOOP_START, EXIT_PRELOOP, REMAINDER, REMAINDER_16, LOOP, END, EXIT, END_LOOP,
  AES192, AES256, AES192_REMAINDER16, REMAINDER16_END_LOOP, AES256_REMAINDER16,
  REMAINDER_8, REMAINDER_4, AES192_REMAINDER8, REMAINDER_LOOP, AES256_REMINDER,
  AES192_REMAINDER, END_REMAINDER_LOOP, AES256_REMAINDER8, REMAINDER8_END_LOOP,
  AES192_REMAINDER4, AES256_REMAINDER4, AES256_REMAINDER, END_REMAINDER4, EXTRACT_TAILBYTES,
  EXTRACT_TAIL_4BYTES, EXTRACT_TAIL_2BYTES, EXTRACT_TAIL_1BYTE, STORE_CTR;

  __ cmpl(len_reg, 0);
  __ jcc(Assembler::belowEqual, EXIT);

  __ movl(pos, 0);
  // if the number of used encrypted counter bytes < 16,
  // XOR PT with saved encrypted counter to obtain CT
  __ bind(PRELOOP_START);
  __ cmpl(used, 16);
  __ jcc(Assembler::aboveEqual, EXIT_PRELOOP);
  __ movb(rbx, Address(saved_encCounter_start, used));
  __ xorb(rbx, Address(src_addr, pos));
  __ movb(Address(dest_addr, pos), rbx);
  __ addptr(pos, 1);
  __ addptr(used, 1);
  __ decrement(len_reg);
  __ jcc(Assembler::notEqual, PRELOOP_START);

  __ bind(EXIT_PRELOOP);
  __ movl(Address(used_addr, 0), used);

  __ cmpl(len_reg, 0);
  __ jcc(Assembler::equal, EXIT);

  // Calculate number of rounds i.e. 10, 12, 14,  based on key length(128, 192, 256).
  __ movl(rounds, Address(key, arrayOopDesc::length_offset_in_bytes() - arrayOopDesc::base_offset_in_bytes(T_INT)));

  __ vpxor(xmm0, xmm0, xmm0, Assembler::AVX_128bit);
  // Move initial counter value in xmm0
  __ movdqu(xmm0, Address(counter, 0));
  // broadcast counter value to zmm8
  __ evshufi64x2(xmm8, xmm0, xmm0, 0, Assembler::AVX_512bit);

  // load lbswap mask
  __ evmovdquq(xmm16, ExternalAddress(counter_shuffle_mask_addr()), Assembler::AVX_512bit, r15 /*rscratch*/);

  //shuffle counter using lbswap_mask
  __ vpshufb(xmm8, xmm8, xmm16, Assembler::AVX_512bit);

  // pre-increment and propagate counter values to zmm9-zmm15 registers.
  // Linc0 increments the zmm8 by 1 (initial value being 0), Linc4 increments the counters zmm9-zmm15 by 4
  // The counter is incremented after each block i.e. 16 bytes is processed;
  // each zmm register has 4 counter values as its MSB
  // the counters are incremented in parallel

  const XMMRegister ones = xmm17;
  // Vector value to propagate carries
  __ evmovdquq(ones, ExternalAddress(counter_mask_ones_addr()), Assembler::AVX_512bit, r15);

  __ evmovdquq(xmm19, ExternalAddress(counter_mask_linc0_addr()), Assembler::AVX_512bit, r15 /*rscratch*/);
  ev_add128(xmm8, xmm8, xmm19, Assembler::AVX_512bit, /*ktmp*/k1, ones);
  __ evmovdquq(xmm19, ExternalAddress(counter_mask_linc4_addr()), Assembler::AVX_512bit, r15 /*rscratch*/);
  ev_add128(xmm9,  xmm8,  xmm19, Assembler::AVX_512bit, /*ktmp*/k1, ones);
  ev_add128(xmm10, xmm9,  xmm19, Assembler::AVX_512bit, /*ktmp*/k1, ones);
  ev_add128(xmm11, xmm10, xmm19, Assembler::AVX_512bit, /*ktmp*/k1, ones);
  ev_add128(xmm12, xmm11, xmm19, Assembler::AVX_512bit, /*ktmp*/k1, ones);
  ev_add128(xmm13, xmm12, xmm19, Assembler::AVX_512bit, /*ktmp*/k1, ones);
  ev_add128(xmm14, xmm13, xmm19, Assembler::AVX_512bit, /*ktmp*/k1, ones);
  ev_add128(xmm15, xmm14, xmm19, Assembler::AVX_512bit, /*ktmp*/k1, ones);

  // load linc32 mask in zmm register.linc32 increments counter by 32
  __ evmovdquq(xmm19, ExternalAddress(counter_mask_linc32_addr()), Assembler::AVX_512bit, r15 /*rscratch*/);

  // xmm31 contains the key shuffle mask.
  __ movdqu(xmm31, ExternalAddress(key_shuffle_mask_addr()), r15 /*rscratch*/);
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
  __ bind(LOOP);
  __ cmpl(len_reg, 512);
  __ jcc(Assembler::less, REMAINDER);
  __ subq(len_reg, 512);
  //Shuffle counter and Exor it with roundkey1. Result is stored in zmm0-7
  __ vpshufb(xmm0, xmm8, xmm16, Assembler::AVX_512bit);
  __ evpxorq(xmm0, xmm0, xmm20, Assembler::AVX_512bit);
  __ vpshufb(xmm1, xmm9, xmm16, Assembler::AVX_512bit);
  __ evpxorq(xmm1, xmm1, xmm20, Assembler::AVX_512bit);
  __ vpshufb(xmm2, xmm10, xmm16, Assembler::AVX_512bit);
  __ evpxorq(xmm2, xmm2, xmm20, Assembler::AVX_512bit);
  __ vpshufb(xmm3, xmm11, xmm16, Assembler::AVX_512bit);
  __ evpxorq(xmm3, xmm3, xmm20, Assembler::AVX_512bit);
  __ vpshufb(xmm4, xmm12, xmm16, Assembler::AVX_512bit);
  __ evpxorq(xmm4, xmm4, xmm20, Assembler::AVX_512bit);
  __ vpshufb(xmm5, xmm13, xmm16, Assembler::AVX_512bit);
  __ evpxorq(xmm5, xmm5, xmm20, Assembler::AVX_512bit);
  __ vpshufb(xmm6, xmm14, xmm16, Assembler::AVX_512bit);
  __ evpxorq(xmm6, xmm6, xmm20, Assembler::AVX_512bit);
  __ vpshufb(xmm7, xmm15, xmm16, Assembler::AVX_512bit);
  __ evpxorq(xmm7, xmm7, xmm20, Assembler::AVX_512bit);
  // Perform AES encode operations and put results in zmm0-zmm7.
  // This is followed by incrementing counter values in zmm8-zmm15.
  // Since we will be processing 32 blocks at a time, the counter is incremented by 32.
  roundEnc(xmm21, 7);
  ev_add128(xmm8,   xmm8, xmm19, Assembler::AVX_512bit, /*ktmp*/k1, ones);
  roundEnc(xmm22, 7);
  ev_add128(xmm9,   xmm9, xmm19, Assembler::AVX_512bit, /*ktmp*/k1, ones);
  roundEnc(xmm23, 7);
  ev_add128(xmm10, xmm10, xmm19, Assembler::AVX_512bit, /*ktmp*/k1, ones);
  roundEnc(xmm24, 7);
  ev_add128(xmm11, xmm11, xmm19, Assembler::AVX_512bit, /*ktmp*/k1, ones);
  roundEnc(xmm25, 7);
  ev_add128(xmm12, xmm12, xmm19, Assembler::AVX_512bit, /*ktmp*/k1, ones);
  roundEnc(xmm26, 7);
  ev_add128(xmm13, xmm13, xmm19, Assembler::AVX_512bit, /*ktmp*/k1, ones);
  roundEnc(xmm27, 7);
  ev_add128(xmm14, xmm14, xmm19, Assembler::AVX_512bit, /*ktmp*/k1, ones);
  roundEnc(xmm28, 7);
  ev_add128(xmm15, xmm15, xmm19, Assembler::AVX_512bit, /*ktmp*/k1, ones);
  roundEnc(xmm29, 7);

  __ cmpl(rounds, 52);
  __ jcc(Assembler::aboveEqual, AES192);
  lastroundEnc(xmm30, 7);
  __ jmp(END_LOOP);

  __ bind(AES192);
  roundEnc(xmm30, 7);
  ev_load_key(xmm18, key, 11 * 16, xmm31);
  roundEnc(xmm18, 7);
  __ cmpl(rounds, 60);
  __ jcc(Assembler::aboveEqual, AES256);
  ev_load_key(xmm18, key, 12 * 16, xmm31);
  lastroundEnc(xmm18, 7);
  __ jmp(END_LOOP);

  __ bind(AES256);
  ev_load_key(xmm18, key, 12 * 16, xmm31);
  roundEnc(xmm18, 7);
  ev_load_key(xmm18, key, 13 * 16, xmm31);
  roundEnc(xmm18, 7);
  ev_load_key(xmm18, key, 14 * 16, xmm31);
  lastroundEnc(xmm18, 7);

  // After AES encode rounds, the encrypted block cipher lies in zmm0-zmm7
  // xor encrypted block cipher and input plaintext and store resultant ciphertext
  __ bind(END_LOOP);
  __ evpxorq(xmm0, xmm0, Address(src_addr, pos, Address::times_1, 0 * 64), Assembler::AVX_512bit);
  __ evmovdquq(Address(dest_addr, pos, Address::times_1, 0), xmm0, Assembler::AVX_512bit);
  __ evpxorq(xmm1, xmm1, Address(src_addr, pos, Address::times_1, 1 * 64), Assembler::AVX_512bit);
  __ evmovdquq(Address(dest_addr, pos, Address::times_1, 64), xmm1, Assembler::AVX_512bit);
  __ evpxorq(xmm2, xmm2, Address(src_addr, pos, Address::times_1, 2 * 64), Assembler::AVX_512bit);
  __ evmovdquq(Address(dest_addr, pos, Address::times_1, 2 * 64), xmm2, Assembler::AVX_512bit);
  __ evpxorq(xmm3, xmm3, Address(src_addr, pos, Address::times_1, 3 * 64), Assembler::AVX_512bit);
  __ evmovdquq(Address(dest_addr, pos, Address::times_1, 3 * 64), xmm3, Assembler::AVX_512bit);
  __ evpxorq(xmm4, xmm4, Address(src_addr, pos, Address::times_1, 4 * 64), Assembler::AVX_512bit);
  __ evmovdquq(Address(dest_addr, pos, Address::times_1, 4 * 64), xmm4, Assembler::AVX_512bit);
  __ evpxorq(xmm5, xmm5, Address(src_addr, pos, Address::times_1, 5 * 64), Assembler::AVX_512bit);
  __ evmovdquq(Address(dest_addr, pos, Address::times_1, 5 * 64), xmm5, Assembler::AVX_512bit);
  __ evpxorq(xmm6, xmm6, Address(src_addr, pos, Address::times_1, 6 * 64), Assembler::AVX_512bit);
  __ evmovdquq(Address(dest_addr, pos, Address::times_1, 6 * 64), xmm6, Assembler::AVX_512bit);
  __ evpxorq(xmm7, xmm7, Address(src_addr, pos, Address::times_1, 7 * 64), Assembler::AVX_512bit);
  __ evmovdquq(Address(dest_addr, pos, Address::times_1, 7 * 64), xmm7, Assembler::AVX_512bit);
  __ addq(pos, 512);
  __ jmp(LOOP);

  // Encode 256, 128, 64 or 16 bytes at a time if length is less than 512 bytes
  __ bind(REMAINDER);
  __ cmpl(len_reg, 0);
  __ jcc(Assembler::equal, END);
  __ cmpl(len_reg, 256);
  __ jcc(Assembler::aboveEqual, REMAINDER_16);
  __ cmpl(len_reg, 128);
  __ jcc(Assembler::aboveEqual, REMAINDER_8);
  __ cmpl(len_reg, 64);
  __ jcc(Assembler::aboveEqual, REMAINDER_4);
  // At this point, we will process 16 bytes of data at a time.
  // So load xmm19 with counter increment value as 1
  __ evmovdquq(xmm19, ExternalAddress(counter_mask_linc1_addr()), Assembler::AVX_128bit, r15 /*rscratch*/);
  __ jmp(REMAINDER_LOOP);

  // Each ZMM register can be used to encode 64 bytes of data, so we have 4 ZMM registers to encode 256 bytes of data
  __ bind(REMAINDER_16);
  __ subq(len_reg, 256);
  // As we process 16 blocks at a time, load mask for incrementing the counter value by 16
  __ evmovdquq(xmm19, ExternalAddress(counter_mask_linc16_addr()), Assembler::AVX_512bit, r15 /*rscratch*/);
  // shuffle counter and XOR counter with roundkey1
  __ vpshufb(xmm0, xmm8, xmm16, Assembler::AVX_512bit);
  __ evpxorq(xmm0, xmm0, xmm20, Assembler::AVX_512bit);
  __ vpshufb(xmm1, xmm9, xmm16, Assembler::AVX_512bit);
  __ evpxorq(xmm1, xmm1, xmm20, Assembler::AVX_512bit);
  __ vpshufb(xmm2, xmm10, xmm16, Assembler::AVX_512bit);
  __ evpxorq(xmm2, xmm2, xmm20, Assembler::AVX_512bit);
  __ vpshufb(xmm3, xmm11, xmm16, Assembler::AVX_512bit);
  __ evpxorq(xmm3, xmm3, xmm20, Assembler::AVX_512bit);
  // Increment counter values by 16
  ev_add128(xmm8, xmm8, xmm19, Assembler::AVX_512bit, /*ktmp*/k1, ones);
  ev_add128(xmm9, xmm9, xmm19, Assembler::AVX_512bit, /*ktmp*/k1, ones);
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

  __ cmpl(rounds, 52);
  __ jcc(Assembler::aboveEqual, AES192_REMAINDER16);
  lastroundEnc(xmm30, 3);
  __ jmp(REMAINDER16_END_LOOP);

  __ bind(AES192_REMAINDER16);
  roundEnc(xmm30, 3);
  ev_load_key(xmm18, key, 11 * 16, xmm31);
  roundEnc(xmm18, 3);
  ev_load_key(xmm5, key, 12 * 16, xmm31);

  __ cmpl(rounds, 60);
  __ jcc(Assembler::aboveEqual, AES256_REMAINDER16);
  lastroundEnc(xmm5, 3);
  __ jmp(REMAINDER16_END_LOOP);
  __ bind(AES256_REMAINDER16);
  roundEnc(xmm5, 3);
  ev_load_key(xmm6, key, 13 * 16, xmm31);
  roundEnc(xmm6, 3);
  ev_load_key(xmm7, key, 14 * 16, xmm31);
  lastroundEnc(xmm7, 3);

  // After AES encode rounds, the encrypted block cipher lies in zmm0-zmm3
  // xor 256 bytes of PT with the encrypted counters to produce CT.
  __ bind(REMAINDER16_END_LOOP);
  __ evpxorq(xmm0, xmm0, Address(src_addr, pos, Address::times_1, 0), Assembler::AVX_512bit);
  __ evmovdquq(Address(dest_addr, pos, Address::times_1, 0), xmm0, Assembler::AVX_512bit);
  __ evpxorq(xmm1, xmm1, Address(src_addr, pos, Address::times_1, 1 * 64), Assembler::AVX_512bit);
  __ evmovdquq(Address(dest_addr, pos, Address::times_1, 1 * 64), xmm1, Assembler::AVX_512bit);
  __ evpxorq(xmm2, xmm2, Address(src_addr, pos, Address::times_1, 2 * 64), Assembler::AVX_512bit);
  __ evmovdquq(Address(dest_addr, pos, Address::times_1, 2 * 64), xmm2, Assembler::AVX_512bit);
  __ evpxorq(xmm3, xmm3, Address(src_addr, pos, Address::times_1, 3 * 64), Assembler::AVX_512bit);
  __ evmovdquq(Address(dest_addr, pos, Address::times_1, 3 * 64), xmm3, Assembler::AVX_512bit);
  __ addq(pos, 256);

  __ cmpl(len_reg, 128);
  __ jcc(Assembler::aboveEqual, REMAINDER_8);

  __ cmpl(len_reg, 64);
  __ jcc(Assembler::aboveEqual, REMAINDER_4);
  //load mask for incrementing the counter value by 1
  __ evmovdquq(xmm19, ExternalAddress(counter_mask_linc1_addr()), Assembler::AVX_128bit, r15 /*rscratch*/);
  __ jmp(REMAINDER_LOOP);

  // Each ZMM register can be used to encode 64 bytes of data, so we have 2 ZMM registers to encode 128 bytes of data
  __ bind(REMAINDER_8);
  __ subq(len_reg, 128);
  // As we process 8 blocks at a time, load mask for incrementing the counter value by 8
  __ evmovdquq(xmm19, ExternalAddress(counter_mask_linc8_addr()), Assembler::AVX_512bit, r15 /*rscratch*/);
  // shuffle counters and xor with roundkey1
  __ vpshufb(xmm0, xmm8, xmm16, Assembler::AVX_512bit);
  __ evpxorq(xmm0, xmm0, xmm20, Assembler::AVX_512bit);
  __ vpshufb(xmm1, xmm9, xmm16, Assembler::AVX_512bit);
  __ evpxorq(xmm1, xmm1, xmm20, Assembler::AVX_512bit);
  // increment counter by 8
  ev_add128(xmm8, xmm8, xmm19, Assembler::AVX_512bit, /*ktmp*/k1, ones);
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

  __ cmpl(rounds, 52);
  __ jcc(Assembler::aboveEqual, AES192_REMAINDER8);
  lastroundEnc(xmm30, 1);
  __ jmp(REMAINDER8_END_LOOP);

  __ bind(AES192_REMAINDER8);
  roundEnc(xmm30, 1);
  ev_load_key(xmm18, key, 11 * 16, xmm31);
  roundEnc(xmm18, 1);
  ev_load_key(xmm5, key, 12 * 16, xmm31);
  __ cmpl(rounds, 60);
  __ jcc(Assembler::aboveEqual, AES256_REMAINDER8);
  lastroundEnc(xmm5, 1);
  __ jmp(REMAINDER8_END_LOOP);

  __ bind(AES256_REMAINDER8);
  roundEnc(xmm5, 1);
  ev_load_key(xmm6, key, 13 * 16, xmm31);
  roundEnc(xmm6, 1);
  ev_load_key(xmm7, key, 14 * 16, xmm31);
  lastroundEnc(xmm7, 1);

  __ bind(REMAINDER8_END_LOOP);
  // After AES encode rounds, the encrypted block cipher lies in zmm0-zmm1
  // XOR PT with the encrypted counter and store as CT
  __ evpxorq(xmm0, xmm0, Address(src_addr, pos, Address::times_1, 0 * 64), Assembler::AVX_512bit);
  __ evmovdquq(Address(dest_addr, pos, Address::times_1, 0 * 64), xmm0, Assembler::AVX_512bit);
  __ evpxorq(xmm1, xmm1, Address(src_addr, pos, Address::times_1, 1 * 64), Assembler::AVX_512bit);
  __ evmovdquq(Address(dest_addr, pos, Address::times_1, 1 * 64), xmm1, Assembler::AVX_512bit);
  __ addq(pos, 128);

  __ cmpl(len_reg, 64);
  __ jcc(Assembler::aboveEqual, REMAINDER_4);
  // load mask for incrementing the counter value by 1
  __ evmovdquq(xmm19, ExternalAddress(counter_mask_linc1_addr()), Assembler::AVX_128bit, r15 /*rscratch*/);
  __ jmp(REMAINDER_LOOP);

  // Each ZMM register can be used to encode 64 bytes of data, so we have 1 ZMM register used in this block of code
  __ bind(REMAINDER_4);
  __ subq(len_reg, 64);
  // As we process 4 blocks at a time, load mask for incrementing the counter value by 4
  __ evmovdquq(xmm19, ExternalAddress(counter_mask_linc4_addr()), Assembler::AVX_512bit, r15 /*rscratch*/);
  // XOR counter with first roundkey
  __ vpshufb(xmm0, xmm8, xmm16, Assembler::AVX_512bit);
  __ evpxorq(xmm0, xmm0, xmm20, Assembler::AVX_512bit);

  // Increment counter
  ev_add128(xmm8, xmm8, xmm19, Assembler::AVX_512bit, /*ktmp*/k1, ones);
  __ vaesenc(xmm0, xmm0, xmm21, Assembler::AVX_512bit);
  __ vaesenc(xmm0, xmm0, xmm22, Assembler::AVX_512bit);
  __ vaesenc(xmm0, xmm0, xmm23, Assembler::AVX_512bit);
  __ vaesenc(xmm0, xmm0, xmm24, Assembler::AVX_512bit);
  __ vaesenc(xmm0, xmm0, xmm25, Assembler::AVX_512bit);
  __ vaesenc(xmm0, xmm0, xmm26, Assembler::AVX_512bit);
  __ vaesenc(xmm0, xmm0, xmm27, Assembler::AVX_512bit);
  __ vaesenc(xmm0, xmm0, xmm28, Assembler::AVX_512bit);
  __ vaesenc(xmm0, xmm0, xmm29, Assembler::AVX_512bit);
  __ cmpl(rounds, 52);
  __ jcc(Assembler::aboveEqual, AES192_REMAINDER4);
  __ vaesenclast(xmm0, xmm0, xmm30, Assembler::AVX_512bit);
  __ jmp(END_REMAINDER4);

  __ bind(AES192_REMAINDER4);
  __ vaesenc(xmm0, xmm0, xmm30, Assembler::AVX_512bit);
  ev_load_key(xmm18, key, 11 * 16, xmm31);
  __ vaesenc(xmm0, xmm0, xmm18, Assembler::AVX_512bit);
  ev_load_key(xmm5, key, 12 * 16, xmm31);

  __ cmpl(rounds, 60);
  __ jcc(Assembler::aboveEqual, AES256_REMAINDER4);
  __ vaesenclast(xmm0, xmm0, xmm5, Assembler::AVX_512bit);
  __ jmp(END_REMAINDER4);

  __ bind(AES256_REMAINDER4);
  __ vaesenc(xmm0, xmm0, xmm5, Assembler::AVX_512bit);
  ev_load_key(xmm6, key, 13 * 16, xmm31);
  __ vaesenc(xmm0, xmm0, xmm6, Assembler::AVX_512bit);
  ev_load_key(xmm7, key, 14 * 16, xmm31);
  __ vaesenclast(xmm0, xmm0, xmm7, Assembler::AVX_512bit);
  // After AES encode rounds, the encrypted block cipher lies in zmm0.
  // XOR encrypted block cipher with PT and store 64 bytes of ciphertext
  __ bind(END_REMAINDER4);
  __ evpxorq(xmm0, xmm0, Address(src_addr, pos, Address::times_1, 0 * 64), Assembler::AVX_512bit);
  __ evmovdquq(Address(dest_addr, pos, Address::times_1, 0), xmm0, Assembler::AVX_512bit);
  __ addq(pos, 64);
  // load mask for incrementing the counter value by 1
  __ evmovdquq(xmm19, ExternalAddress(counter_mask_linc1_addr()), Assembler::AVX_128bit, r15 /*rscratch*/);

  // For a single block, the AES rounds start here.
  __ bind(REMAINDER_LOOP);
  __ cmpl(len_reg, 0);
  __ jcc(Assembler::belowEqual, END);
  // XOR counter with first roundkey
  __ vpshufb(xmm0, xmm8, xmm16, Assembler::AVX_128bit);
  __ evpxorq(xmm0, xmm0, xmm20, Assembler::AVX_128bit);
  __ vaesenc(xmm0, xmm0, xmm21, Assembler::AVX_128bit);
  // Increment counter by 1
  ev_add128(xmm8, xmm8, xmm19, Assembler::AVX_128bit, /*ktmp*/k1, ones);
  __ vaesenc(xmm0, xmm0, xmm22, Assembler::AVX_128bit);
  __ vaesenc(xmm0, xmm0, xmm23, Assembler::AVX_128bit);
  __ vaesenc(xmm0, xmm0, xmm24, Assembler::AVX_128bit);
  __ vaesenc(xmm0, xmm0, xmm25, Assembler::AVX_128bit);
  __ vaesenc(xmm0, xmm0, xmm26, Assembler::AVX_128bit);
  __ vaesenc(xmm0, xmm0, xmm27, Assembler::AVX_128bit);
  __ vaesenc(xmm0, xmm0, xmm28, Assembler::AVX_128bit);
  __ vaesenc(xmm0, xmm0, xmm29, Assembler::AVX_128bit);

  __ cmpl(rounds, 52);
  __ jcc(Assembler::aboveEqual, AES192_REMAINDER);
  __ vaesenclast(xmm0, xmm0, xmm30, Assembler::AVX_128bit);
  __ jmp(END_REMAINDER_LOOP);

  __ bind(AES192_REMAINDER);
  __ vaesenc(xmm0, xmm0, xmm30, Assembler::AVX_128bit);
  ev_load_key(xmm18, key, 11 * 16, xmm31);
  __ vaesenc(xmm0, xmm0, xmm18, Assembler::AVX_128bit);
  ev_load_key(xmm5, key, 12 * 16, xmm31);
  __ cmpl(rounds, 60);
  __ jcc(Assembler::aboveEqual, AES256_REMAINDER);
  __ vaesenclast(xmm0, xmm0, xmm5, Assembler::AVX_128bit);
  __ jmp(END_REMAINDER_LOOP);

  __ bind(AES256_REMAINDER);
  __ vaesenc(xmm0, xmm0, xmm5, Assembler::AVX_128bit);
  ev_load_key(xmm6, key, 13 * 16, xmm31);
  __ vaesenc(xmm0, xmm0, xmm6, Assembler::AVX_128bit);
  ev_load_key(xmm7, key, 14 * 16, xmm31);
  __ vaesenclast(xmm0, xmm0, xmm7, Assembler::AVX_128bit);

  __ bind(END_REMAINDER_LOOP);
  // If the length register is less than the blockSize i.e. 16
  // then we store only those bytes of the CT to the destination
  // corresponding to the length register value
  // extracting the exact number of bytes is handled by EXTRACT_TAILBYTES
  __ cmpl(len_reg, 16);
  __ jcc(Assembler::less, EXTRACT_TAILBYTES);
  __ subl(len_reg, 16);
  // After AES encode rounds, the encrypted block cipher lies in xmm0.
  // If the length register is equal to 16 bytes, store CT in dest after XOR operation.
  __ evpxorq(xmm0, xmm0, Address(src_addr, pos, Address::times_1, 0), Assembler::AVX_128bit);
  __ evmovdquq(Address(dest_addr, pos, Address::times_1, 0), xmm0, Assembler::AVX_128bit);
  __ addl(pos, 16);

  __ jmp(REMAINDER_LOOP);

  __ bind(EXTRACT_TAILBYTES);
  // Save encrypted counter value in xmm0 for next invocation, before XOR operation
  __ movdqu(Address(saved_encCounter_start, 0), xmm0);
  // XOR encryted block cipher in xmm0 with PT to produce CT
  __ evpxorq(xmm0, xmm0, Address(src_addr, pos, Address::times_1, 0), Assembler::AVX_128bit);
  // extract up to 15 bytes of CT from xmm0 as specified by length register
  __ testptr(len_reg, 8);
  __ jcc(Assembler::zero, EXTRACT_TAIL_4BYTES);
  __ pextrq(Address(dest_addr, pos), xmm0, 0);
  __ psrldq(xmm0, 8);
  __ addl(pos, 8);
  __ bind(EXTRACT_TAIL_4BYTES);
  __ testptr(len_reg, 4);
  __ jcc(Assembler::zero, EXTRACT_TAIL_2BYTES);
  __ pextrd(Address(dest_addr, pos), xmm0, 0);
  __ psrldq(xmm0, 4);
  __ addq(pos, 4);
  __ bind(EXTRACT_TAIL_2BYTES);
  __ testptr(len_reg, 2);
  __ jcc(Assembler::zero, EXTRACT_TAIL_1BYTE);
  __ pextrw(Address(dest_addr, pos), xmm0, 0);
  __ psrldq(xmm0, 2);
  __ addl(pos, 2);
  __ bind(EXTRACT_TAIL_1BYTE);
  __ testptr(len_reg, 1);
  __ jcc(Assembler::zero, END);
  __ pextrb(Address(dest_addr, pos), xmm0, 0);
  __ addl(pos, 1);

  __ bind(END);
  // If there are no tail bytes, store counter value and exit
  __ cmpl(len_reg, 0);
  __ jcc(Assembler::equal, STORE_CTR);
  __ movl(Address(used_addr, 0), len_reg);

  __ bind(STORE_CTR);
  //shuffle updated counter and store it
  __ vpshufb(xmm8, xmm8, xmm16, Assembler::AVX_128bit);
  __ movdqu(Address(counter, 0), xmm8);
  // Zero out counter and key registers
  __ evpxorq(xmm8, xmm8, xmm8, Assembler::AVX_512bit);
  __ evpxorq(xmm20, xmm20, xmm20, Assembler::AVX_512bit);
  __ evpxorq(xmm21, xmm21, xmm21, Assembler::AVX_512bit);
  __ evpxorq(xmm22, xmm22, xmm22, Assembler::AVX_512bit);
  __ evpxorq(xmm23, xmm23, xmm23, Assembler::AVX_512bit);
  __ evpxorq(xmm24, xmm24, xmm24, Assembler::AVX_512bit);
  __ evpxorq(xmm25, xmm25, xmm25, Assembler::AVX_512bit);
  __ evpxorq(xmm26, xmm26, xmm26, Assembler::AVX_512bit);
  __ evpxorq(xmm27, xmm27, xmm27, Assembler::AVX_512bit);
  __ evpxorq(xmm28, xmm28, xmm28, Assembler::AVX_512bit);
  __ evpxorq(xmm29, xmm29, xmm29, Assembler::AVX_512bit);
  __ evpxorq(xmm30, xmm30, xmm30, Assembler::AVX_512bit);
  __ cmpl(rounds, 44);
  __ jcc(Assembler::belowEqual, EXIT);
  __ evpxorq(xmm18, xmm18, xmm18, Assembler::AVX_512bit);
  __ evpxorq(xmm5, xmm5, xmm5, Assembler::AVX_512bit);
  __ cmpl(rounds, 52);
  __ jcc(Assembler::belowEqual, EXIT);
  __ evpxorq(xmm6, xmm6, xmm6, Assembler::AVX_512bit);
  __ evpxorq(xmm7, xmm7, xmm7, Assembler::AVX_512bit);
  __ bind(EXIT);
}

void StubGenerator::gfmul_avx512(XMMRegister GH, XMMRegister HK) {
  const XMMRegister TMP1 = xmm0;
  const XMMRegister TMP2 = xmm1;
  const XMMRegister TMP3 = xmm2;

  __ evpclmulqdq(TMP1, GH, HK, 0x11, Assembler::AVX_512bit);
  __ evpclmulqdq(TMP2, GH, HK, 0x00, Assembler::AVX_512bit);
  __ evpclmulqdq(TMP3, GH, HK, 0x01, Assembler::AVX_512bit);
  __ evpclmulqdq(GH, GH, HK, 0x10, Assembler::AVX_512bit);
  __ evpxorq(GH, GH, TMP3, Assembler::AVX_512bit);
  __ vpsrldq(TMP3, GH, 8, Assembler::AVX_512bit);
  __ vpslldq(GH, GH, 8, Assembler::AVX_512bit);
  __ evpxorq(TMP1, TMP1, TMP3, Assembler::AVX_512bit);
  __ evpxorq(GH, GH, TMP2, Assembler::AVX_512bit);

  __ evmovdquq(TMP3, ExternalAddress(ghash_polynomial_reduction_addr()), Assembler::AVX_512bit, r15 /*rscratch*/);
  __ evpclmulqdq(TMP2, TMP3, GH, 0x01, Assembler::AVX_512bit);
  __ vpslldq(TMP2, TMP2, 8, Assembler::AVX_512bit);
  __ evpxorq(GH, GH, TMP2, Assembler::AVX_512bit);
  __ evpclmulqdq(TMP2, TMP3, GH, 0x00, Assembler::AVX_512bit);
  __ vpsrldq(TMP2, TMP2, 4, Assembler::AVX_512bit);
  __ evpclmulqdq(GH, TMP3, GH, 0x10, Assembler::AVX_512bit);
  __ vpslldq(GH, GH, 4, Assembler::AVX_512bit);
  __ vpternlogq(GH, 0x96, TMP1, TMP2, Assembler::AVX_512bit);
}

void StubGenerator::generateHtbl_48_block_zmm(Register htbl, Register avx512_htbl, Register rscratch) {
  const XMMRegister HK = xmm6;
  const XMMRegister ZT5 = xmm4;
  const XMMRegister ZT7 = xmm7;
  const XMMRegister ZT8 = xmm8;

  Label GFMUL_AVX512;

  __ movdqu(HK, Address(htbl, 0));
  __ movdqu(xmm10, ExternalAddress(ghash_long_swap_mask_addr()), rscratch);
  __ vpshufb(HK, HK, xmm10, Assembler::AVX_128bit);

  __ movdqu(xmm11, ExternalAddress(ghash_polynomial_addr()), rscratch);
  __ movdqu(xmm12, ExternalAddress(ghash_polynomial_two_one_addr()), rscratch);
  // Compute H ^ 2 from the input subkeyH
  __ movdqu(xmm2, xmm6);
  __ vpsllq(xmm6, xmm6, 1, Assembler::AVX_128bit);
  __ vpsrlq(xmm2, xmm2, 63, Assembler::AVX_128bit);
  __ movdqu(xmm1, xmm2);
  __ vpslldq(xmm2, xmm2, 8, Assembler::AVX_128bit);
  __ vpsrldq(xmm1, xmm1, 8, Assembler::AVX_128bit);
  __ vpor(xmm6, xmm6, xmm2, Assembler::AVX_128bit);

  __ vpshufd(xmm2, xmm1, 0x24, Assembler::AVX_128bit);
  __ vpcmpeqd(xmm2, xmm2, xmm12, Assembler::AVX_128bit);
  __ vpand(xmm2, xmm2, xmm11, Assembler::AVX_128bit);
  __ vpxor(xmm6, xmm6, xmm2, Assembler::AVX_128bit);
  __ movdqu(Address(avx512_htbl, 16 * 47), xmm6); // H ^ 2
  // Compute the remaining three powers of H using XMM registers and all following powers using ZMM
  __ movdqu(ZT5, HK);
  __ vinserti32x4(ZT7, ZT7, HK, 3);

  gfmul_avx512(ZT5, HK);
  __ movdqu(Address(avx512_htbl, 16 * 46), ZT5); // H ^ 2 * 2
  __ vinserti32x4(ZT7, ZT7, ZT5, 2);

  gfmul_avx512(ZT5, HK);
  __ movdqu(Address(avx512_htbl, 16 * 45), ZT5); // H ^ 2 * 3
  __ vinserti32x4(ZT7, ZT7, ZT5, 1);

  gfmul_avx512(ZT5, HK);
  __ movdqu(Address(avx512_htbl, 16 * 44), ZT5); // H ^ 2 * 4
  __ vinserti32x4(ZT7, ZT7, ZT5, 0);

  __ evshufi64x2(ZT5, ZT5, ZT5, 0x00, Assembler::AVX_512bit);
  __ evmovdquq(ZT8, ZT7, Assembler::AVX_512bit);
  gfmul_avx512(ZT7, ZT5);
  __ evmovdquq(Address(avx512_htbl, 16 * 40), ZT7, Assembler::AVX_512bit);
  __ evshufi64x2(ZT5, ZT7, ZT7, 0x00, Assembler::AVX_512bit);
  gfmul_avx512(ZT8, ZT5);
  __ evmovdquq(Address(avx512_htbl, 16 * 36), ZT8, Assembler::AVX_512bit);
  gfmul_avx512(ZT7, ZT5);
  __ evmovdquq(Address(avx512_htbl, 16 * 32), ZT7, Assembler::AVX_512bit);
  gfmul_avx512(ZT8, ZT5);
  __ evmovdquq(Address(avx512_htbl, 16 * 28), ZT8, Assembler::AVX_512bit);
  gfmul_avx512(ZT7, ZT5);
  __ evmovdquq(Address(avx512_htbl, 16 * 24), ZT7, Assembler::AVX_512bit);
  gfmul_avx512(ZT8, ZT5);
  __ evmovdquq(Address(avx512_htbl, 16 * 20), ZT8, Assembler::AVX_512bit);
  gfmul_avx512(ZT7, ZT5);
  __ evmovdquq(Address(avx512_htbl, 16 * 16), ZT7, Assembler::AVX_512bit);
  gfmul_avx512(ZT8, ZT5);
  __ evmovdquq(Address(avx512_htbl, 16 * 12), ZT8, Assembler::AVX_512bit);
  gfmul_avx512(ZT7, ZT5);
  __ evmovdquq(Address(avx512_htbl, 16 * 8), ZT7, Assembler::AVX_512bit);
  gfmul_avx512(ZT8, ZT5);
  __ evmovdquq(Address(avx512_htbl, 16 * 4), ZT8, Assembler::AVX_512bit);
  gfmul_avx512(ZT7, ZT5);
  __ evmovdquq(Address(avx512_htbl, 16 * 0), ZT7, Assembler::AVX_512bit);
  __ ret(0);
}

#define vclmul_reduce(out, poly, hi128, lo128, tmp0, tmp1)      \
__ evpclmulqdq(tmp0, poly, lo128, 0x01, Assembler::AVX_512bit); \
__ vpslldq(tmp0, tmp0, 8, Assembler::AVX_512bit);               \
__ evpxorq(tmp0, lo128, tmp0, Assembler::AVX_512bit);           \
__ evpclmulqdq(tmp1, poly, tmp0, 0x00, Assembler::AVX_512bit);  \
__ vpsrldq(tmp1, tmp1, 4, Assembler::AVX_512bit);               \
__ evpclmulqdq(out, poly, tmp0, 0x10, Assembler::AVX_512bit);   \
__ vpslldq(out, out, 4, Assembler::AVX_512bit);                 \
__ vpternlogq(out, 0x96, tmp1, hi128, Assembler::AVX_512bit);   \

#define vhpxori4x128(reg, tmp)                    \
__ vextracti64x4(tmp, reg, 1);                    \
__ evpxorq(reg, reg, tmp, Assembler::AVX_256bit); \
__ vextracti32x4(tmp, reg, 1);                    \
__ evpxorq(reg, reg, tmp, Assembler::AVX_128bit); \

#define roundEncode(key, dst1, dst2, dst3, dst4)    \
__ vaesenc(dst1, dst1, key, Assembler::AVX_512bit); \
__ vaesenc(dst2, dst2, key, Assembler::AVX_512bit); \
__ vaesenc(dst3, dst3, key, Assembler::AVX_512bit); \
__ vaesenc(dst4, dst4, key, Assembler::AVX_512bit); \

#define lastroundEncode(key, dst1, dst2, dst3, dst4) \
__ vaesenclast(dst1, dst1, key, Assembler::AVX_512bit); \
__ vaesenclast(dst2, dst2, key, Assembler::AVX_512bit); \
__ vaesenclast(dst3, dst3, key, Assembler::AVX_512bit); \
__ vaesenclast(dst4, dst4, key, Assembler::AVX_512bit); \

#define storeData(dst, position, src1, src2, src3, src4) \
__ evmovdquq(Address(dst, position, Address::times_1, 0 * 64), src1, Assembler::AVX_512bit); \
__ evmovdquq(Address(dst, position, Address::times_1, 1 * 64), src2, Assembler::AVX_512bit); \
__ evmovdquq(Address(dst, position, Address::times_1, 2 * 64), src3, Assembler::AVX_512bit); \
__ evmovdquq(Address(dst, position, Address::times_1, 3 * 64), src4, Assembler::AVX_512bit); \

#define loadData(src, position, dst1, dst2, dst3, dst4) \
__ evmovdquq(dst1, Address(src, position, Address::times_1, 0 * 64), Assembler::AVX_512bit); \
__ evmovdquq(dst2, Address(src, position, Address::times_1, 1 * 64), Assembler::AVX_512bit); \
__ evmovdquq(dst3, Address(src, position, Address::times_1, 2 * 64), Assembler::AVX_512bit); \
__ evmovdquq(dst4, Address(src, position, Address::times_1, 3 * 64), Assembler::AVX_512bit); \

#define carrylessMultiply(dst00, dst01, dst10, dst11, ghdata, hkey) \
__ evpclmulqdq(dst00, ghdata, hkey, 0x00, Assembler::AVX_512bit); \
__ evpclmulqdq(dst01, ghdata, hkey, 0x01, Assembler::AVX_512bit); \
__ evpclmulqdq(dst10, ghdata, hkey, 0x10, Assembler::AVX_512bit); \
__ evpclmulqdq(dst11, ghdata, hkey, 0x11, Assembler::AVX_512bit); \

#define shuffleExorRnd1Key(dst0, dst1, dst2, dst3, shufmask, rndkey) \
__ vpshufb(dst0, dst0, shufmask, Assembler::AVX_512bit); \
__ evpxorq(dst0, dst0, rndkey, Assembler::AVX_512bit); \
__ vpshufb(dst1, dst1, shufmask, Assembler::AVX_512bit); \
__ evpxorq(dst1, dst1, rndkey, Assembler::AVX_512bit); \
__ vpshufb(dst2, dst2, shufmask, Assembler::AVX_512bit); \
__ evpxorq(dst2, dst2, rndkey, Assembler::AVX_512bit); \
__ vpshufb(dst3, dst3, shufmask, Assembler::AVX_512bit); \
__ evpxorq(dst3, dst3, rndkey, Assembler::AVX_512bit); \

#define xorBeforeStore(dst0, dst1, dst2, dst3, src0, src1, src2, src3) \
__ evpxorq(dst0, dst0, src0, Assembler::AVX_512bit); \
__ evpxorq(dst1, dst1, src1, Assembler::AVX_512bit); \
__ evpxorq(dst2, dst2, src2, Assembler::AVX_512bit); \
__ evpxorq(dst3, dst3, src3, Assembler::AVX_512bit); \

#define xorGHASH(dst0, dst1, dst2, dst3, src02, src03, src12, src13, src22, src23, src32, src33) \
__ vpternlogq(dst0, 0x96, src02, src03, Assembler::AVX_512bit); \
__ vpternlogq(dst1, 0x96, src12, src13, Assembler::AVX_512bit); \
__ vpternlogq(dst2, 0x96, src22, src23, Assembler::AVX_512bit); \
__ vpternlogq(dst3, 0x96, src32, src33, Assembler::AVX_512bit); \

void StubGenerator::ghash16_encrypt16_parallel(Register key, Register subkeyHtbl, XMMRegister ctr_blockx, XMMRegister aad_hashx,
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
  __ vpaddd(ZTMP0, ctr_blockx, counter_inc_mask, Assembler::AVX_512bit);
  __ vpaddd(ZTMP1, ZTMP0, counter_inc_mask, Assembler::AVX_512bit);
  __ vpaddd(ZTMP2, ZTMP1, counter_inc_mask, Assembler::AVX_512bit);
  __ vpaddd(ZTMP3, ZTMP2, counter_inc_mask, Assembler::AVX_512bit);
  // Save counter value
  __ evmovdquq(ctr_blockx, ZTMP3, Assembler::AVX_512bit);

  // Reuse ZTMP17 / ZTMP18 for loading AES Keys
  // Pre-load AES round keys
  ev_load_key(ZTMP17, key, 0, xmm29);
  ev_load_key(ZTMP18, key, 1 * 16, xmm29);

  // ZTMP19 & ZTMP20 used for loading hash key
  // Pre-load hash key
  __ evmovdquq(ZTMP19, Address(subkeyHtbl, i * 64), Assembler::AVX_512bit);
  __ evmovdquq(ZTMP20, Address(subkeyHtbl, ++i * 64), Assembler::AVX_512bit);
  // Load data for computing ghash
  __ evmovdquq(ZTMP21, Address(data, ghash_pos, Address::times_1, 0 * 64), Assembler::AVX_512bit);
  __ vpshufb(ZTMP21, ZTMP21, xmm24, Assembler::AVX_512bit);

  // Xor cipher block 0 with input ghash, if available
  if (ghash_input) {
    __ evpxorq(ZTMP21, ZTMP21, aad_hashx, Assembler::AVX_512bit);
  }
  // Load data for computing ghash
  __ evmovdquq(ZTMP22, Address(data, ghash_pos, Address::times_1, 1 * 64), Assembler::AVX_512bit);
  __ vpshufb(ZTMP22, ZTMP22, xmm24, Assembler::AVX_512bit);

  // stitch AES rounds with GHASH
  // AES round 0, xmm24 has shuffle mask
  shuffleExorRnd1Key(ZTMP0, ZTMP1, ZTMP2, ZTMP3, xmm24, ZTMP17);
  // Reuse ZTMP17 / ZTMP18 for loading remaining AES Keys
  ev_load_key(ZTMP17, key, 2 * 16, xmm29);
  // GHASH 4 blocks
  carrylessMultiply(ZTMP6, ZTMP7, ZTMP8, ZTMP5, ZTMP21, ZTMP19);
  // Load the next hkey and Ghash data
  __ evmovdquq(ZTMP19, Address(subkeyHtbl, ++i * 64), Assembler::AVX_512bit);
  __ evmovdquq(ZTMP21, Address(data, ghash_pos, Address::times_1, 2 * 64), Assembler::AVX_512bit);
  __ vpshufb(ZTMP21, ZTMP21, xmm24, Assembler::AVX_512bit);

  // AES round 1
  roundEncode(ZTMP18, ZTMP0, ZTMP1, ZTMP2, ZTMP3);
  ev_load_key(ZTMP18, key, 3 * 16, xmm29);

  // GHASH 4 blocks(11 to 8)
  carrylessMultiply(ZTMP10, ZTMP12, ZTMP11, ZTMP9, ZTMP22, ZTMP20);
  // Load the next hkey and GDATA
  __ evmovdquq(ZTMP20, Address(subkeyHtbl, ++i * 64), Assembler::AVX_512bit);
  __ evmovdquq(ZTMP22, Address(data, ghash_pos, Address::times_1, 3 * 64), Assembler::AVX_512bit);
  __ vpshufb(ZTMP22, ZTMP22, xmm24, Assembler::AVX_512bit);

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
    __ vpternlogq(ZTMP7, 0x96, ZTMP8, ZTMP12, Assembler::AVX_512bit);
    __ evpxorq(xmm25, ZTMP7, ZTMP11, Assembler::AVX_512bit);
    __ evpxorq(xmm27, ZTMP5, ZTMP9, Assembler::AVX_512bit);
    __ evpxorq(xmm26, ZTMP6, ZTMP10, Assembler::AVX_512bit);
  } else if (!first_time_reduction && !final_reduction) {
    xorGHASH(ZTMP7, xmm25, xmm27, xmm26, ZTMP8, ZTMP12, ZTMP7, ZTMP11, ZTMP5, ZTMP9, ZTMP6, ZTMP10);
  }

  if (final_reduction) {
    // Phase one: Add mid products together
    // Also load polynomial constant for reduction
    __ vpternlogq(ZTMP7, 0x96, ZTMP8, ZTMP12, Assembler::AVX_512bit);
    __ vpternlogq(ZTMP7, 0x96, xmm25, ZTMP11, Assembler::AVX_512bit);
    __ vpsrldq(ZTMP11, ZTMP7, 8, Assembler::AVX_512bit);
    __ vpslldq(ZTMP7, ZTMP7, 8, Assembler::AVX_512bit);
    __ evmovdquq(ZTMP12, ExternalAddress(ghash_polynomial_reduction_addr()), Assembler::AVX_512bit, rbx /*rscratch*/);
  }
  // AES round 7
  roundEncode(ZTMP18, ZTMP0, ZTMP1, ZTMP2, ZTMP3);
  ev_load_key(ZTMP18, key, 9 * 16, xmm29);
  if (final_reduction) {
    __ vpternlogq(ZTMP5, 0x96, ZTMP9, ZTMP11, Assembler::AVX_512bit);
    __ evpxorq(ZTMP5, ZTMP5, xmm27, Assembler::AVX_512bit);
    __ vpternlogq(ZTMP6, 0x96, ZTMP10, ZTMP7, Assembler::AVX_512bit);
    __ evpxorq(ZTMP6, ZTMP6, xmm26, Assembler::AVX_512bit);
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
      __ evpclmulqdq(ZTMP10, ZTMP12, ZTMP6, 0x01, Assembler::AVX_128bit);
      __ vpslldq(ZTMP10, ZTMP10, 8, Assembler::AVX_128bit);
      __ evpxorq(ZTMP10, ZTMP6, ZTMP10, Assembler::AVX_128bit);
  }
  __ cmpl(rounds, 52);
  __ jcc(Assembler::greaterEqual, AES_192);
  __ jmp(LAST_AES_RND);
  // AES rounds up to 11 (AES192) or 13 (AES256)
  __ bind(AES_192);
  roundEncode(ZTMP17, ZTMP0, ZTMP1, ZTMP2, ZTMP3);
  ev_load_key(ZTMP18, key, 11 * 16, xmm29);
  roundEncode(ZTMP18, ZTMP0, ZTMP1, ZTMP2, ZTMP3);
  ev_load_key(ZTMP17, key, 12 * 16, xmm29);
  __ cmpl(rounds, 60);
  __ jcc(Assembler::aboveEqual, AES_256);
  __ jmp(LAST_AES_RND);

  __ bind(AES_256);
  roundEncode(ZTMP17, ZTMP0, ZTMP1, ZTMP2, ZTMP3);
  ev_load_key(ZTMP18, key, 13 * 16, xmm29);
  roundEncode(ZTMP18, ZTMP0, ZTMP1, ZTMP2, ZTMP3);
  ev_load_key(ZTMP17, key, 14 * 16, xmm29);

  __ bind(LAST_AES_RND);
  // Second phase of reduction
  if (final_reduction) {
    __ evpclmulqdq(ZTMP9, ZTMP12, ZTMP10, 0x00, Assembler::AVX_128bit);
    __ vpsrldq(ZTMP9, ZTMP9, 4, Assembler::AVX_128bit); // Shift-R 1-DW to obtain 2-DWs shift-R
    __ evpclmulqdq(ZTMP11, ZTMP12, ZTMP10, 0x10, Assembler::AVX_128bit);
    __ vpslldq(ZTMP11, ZTMP11, 4, Assembler::AVX_128bit); // Shift-L 1-DW for result
    // ZTMP5 = ZTMP5 X ZTMP11 X ZTMP9
    __ vpternlogq(ZTMP5, 0x96, ZTMP11, ZTMP9, Assembler::AVX_128bit);
  }
  // Last AES round
  lastroundEncode(ZTMP17, ZTMP0, ZTMP1, ZTMP2, ZTMP3);
  // XOR against plain / cipher text
  xorBeforeStore(ZTMP0, ZTMP1, ZTMP2, ZTMP3, ZTMP13, ZTMP14, ZTMP15, ZTMP16);
  // store cipher / plain text
  storeData(out, pos, ZTMP0, ZTMP1, ZTMP2, ZTMP3);
}

void StubGenerator::aesgcm_encrypt(Register in, Register len, Register ct, Register out, Register key,
                                   Register state, Register subkeyHtbl, Register avx512_subkeyHtbl, Register counter) {
  Label ENC_DEC_DONE, GENERATE_HTBL_48_BLKS, AES_192, AES_256, STORE_CT, GHASH_LAST_32,
        AES_32_BLOCKS, GHASH_AES_PARALLEL, LOOP, ACCUMULATE, GHASH_16_AES_16;
  const XMMRegister CTR_BLOCKx = xmm9;
  const XMMRegister AAD_HASHx = xmm14;
  const Register pos = rax;
  const Register rounds = r15;
  const Register ghash_pos = NOT_WIN64( r14) WIN64_ONLY( r11 );
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

  __ movl(pos, 0); // Total length processed
  // Min data size processed = 768 bytes
  __ cmpl(len, 768);
  __ jcc(Assembler::less, ENC_DEC_DONE);

  // Generate 48 constants for htbl
  __ call(GENERATE_HTBL_48_BLKS, relocInfo::none);
  int index = 0; // Index for choosing subkeyHtbl entry
  __ movl(ghash_pos, 0); // Pointer for ghash read and store operations

  // Move initial counter value and STATE value into variables
  __ movdqu(CTR_BLOCKx, Address(counter, 0));
  __ movdqu(AAD_HASHx, Address(state, 0));
  // Load lswap mask for ghash
  __ movdqu(xmm24, ExternalAddress(ghash_long_swap_mask_addr()), rbx /*rscratch*/);
  // Shuffle input state using lswap mask
  __ vpshufb(AAD_HASHx, AAD_HASHx, xmm24, Assembler::AVX_128bit);

  // Compute #rounds for AES based on the length of the key array
  __ movl(rounds, Address(key, arrayOopDesc::length_offset_in_bytes() - arrayOopDesc::base_offset_in_bytes(T_INT)));

  // Broadcast counter value to 512 bit register
  __ evshufi64x2(CTR_BLOCKx, CTR_BLOCKx, CTR_BLOCKx, 0, Assembler::AVX_512bit);
  // Load counter shuffle mask
  __ evmovdquq(xmm24, ExternalAddress(counter_shuffle_mask_addr()), Assembler::AVX_512bit, rbx /*rscratch*/);
  // Shuffle counter
  __ vpshufb(CTR_BLOCKx, CTR_BLOCKx, xmm24, Assembler::AVX_512bit);

  // Load mask for incrementing counter
  __ evmovdquq(COUNTER_INC_MASK, ExternalAddress(counter_mask_linc4_addr()), Assembler::AVX_512bit, rbx /*rscratch*/);
  // Pre-increment counter
  __ vpaddd(ZTMP5, CTR_BLOCKx, ExternalAddress(counter_mask_linc0_addr()), Assembler::AVX_512bit, rbx /*rscratch*/);
  __ vpaddd(ZTMP6, ZTMP5, COUNTER_INC_MASK, Assembler::AVX_512bit);
  __ vpaddd(ZTMP7, ZTMP6, COUNTER_INC_MASK, Assembler::AVX_512bit);
  __ vpaddd(ZTMP8, ZTMP7, COUNTER_INC_MASK, Assembler::AVX_512bit);

  // Begin 32 blocks of AES processing
  __ bind(AES_32_BLOCKS);
  // Save incremented counter before overwriting it with AES data
  __ evmovdquq(CTR_BLOCKx, ZTMP8, Assembler::AVX_512bit);

  // Move 256 bytes of data
  loadData(in, pos, ZTMP0, ZTMP1, ZTMP2, ZTMP3);
  // Load key shuffle mask
  __ movdqu(xmm29, ExternalAddress(key_shuffle_mask_addr()), rbx /*rscratch*/);
  // Load 0th AES round key
  ev_load_key(ZTMP4, key, 0, xmm29);
  // AES-ROUND0, xmm24 has the shuffle mask
  shuffleExorRnd1Key(ZTMP5, ZTMP6, ZTMP7, ZTMP8, xmm24, ZTMP4);

  for (int j = 1; j < 10; j++) {
      ev_load_key(ZTMP4, key, j * 16, xmm29);
      roundEncode(ZTMP4, ZTMP5, ZTMP6, ZTMP7, ZTMP8);
  }
  ev_load_key(ZTMP4, key, 10 * 16, xmm29);
  // AES rounds up to 11 (AES192) or 13 (AES256)
  __ cmpl(rounds, 52);
  __ jcc(Assembler::greaterEqual, AES_192);
  lastroundEncode(ZTMP4, ZTMP5, ZTMP6, ZTMP7, ZTMP8);
  __ jmp(STORE_CT);

  __ bind(AES_192);
  roundEncode(ZTMP4, ZTMP5, ZTMP6, ZTMP7, ZTMP8);
  ev_load_key(ZTMP4, key, 11 * 16, xmm29);
  roundEncode(ZTMP4, ZTMP5, ZTMP6, ZTMP7, ZTMP8);
  __ cmpl(rounds, 60);
  __ jcc(Assembler::aboveEqual, AES_256);
  ev_load_key(ZTMP4, key, 12 * 16, xmm29);
  lastroundEncode(ZTMP4, ZTMP5, ZTMP6, ZTMP7, ZTMP8);
  __ jmp(STORE_CT);

  __ bind(AES_256);
  ev_load_key(ZTMP4, key, 12 * 16, xmm29);
  roundEncode(ZTMP4, ZTMP5, ZTMP6, ZTMP7, ZTMP8);
  ev_load_key(ZTMP4, key, 13 * 16, xmm29);
  roundEncode(ZTMP4, ZTMP5, ZTMP6, ZTMP7, ZTMP8);
  ev_load_key(ZTMP4, key, 14 * 16, xmm29);
  // Last AES round
  lastroundEncode(ZTMP4, ZTMP5, ZTMP6, ZTMP7, ZTMP8);

  __ bind(STORE_CT);
  // Xor the encrypted key with PT to obtain CT
  xorBeforeStore(ZTMP5, ZTMP6, ZTMP7, ZTMP8, ZTMP0, ZTMP1, ZTMP2, ZTMP3);
  storeData(out, pos, ZTMP5, ZTMP6, ZTMP7, ZTMP8);
  // 16 blocks encryption completed
  __ addl(pos, 256);
  __ cmpl(pos, 512);
  __ jcc(Assembler::aboveEqual, GHASH_AES_PARALLEL);
  __ vpaddd(ZTMP5, CTR_BLOCKx, COUNTER_INC_MASK, Assembler::AVX_512bit);
  __ vpaddd(ZTMP6, ZTMP5, COUNTER_INC_MASK, Assembler::AVX_512bit);
  __ vpaddd(ZTMP7, ZTMP6, COUNTER_INC_MASK, Assembler::AVX_512bit);
  __ vpaddd(ZTMP8, ZTMP7, COUNTER_INC_MASK, Assembler::AVX_512bit);
  __ jmp(AES_32_BLOCKS);

  __ bind(GHASH_AES_PARALLEL);
  // Ghash16_encrypt16_parallel takes place in the order with three reduction values:
  // 1) First time -> cipher xor input ghash
  // 2) No reduction -> accumulate multiplication values
  // 3) Final reduction post 48 blocks -> new ghash value is computed for the next round
  // Reduction value = first time
  ghash16_encrypt16_parallel(key, avx512_subkeyHtbl, CTR_BLOCKx, AAD_HASHx, in, out, ct, pos, true, xmm24, true, rounds, ghash_pos, false, index, COUNTER_INC_MASK);
  __ addl(pos, 256);
  __ addl(ghash_pos, 256);
  index += 4;

  // At this point we have processed 768 bytes of AES and 256 bytes of GHASH.
  // If the remaining length is less than 768, process remaining 512 bytes of ghash in GHASH_LAST_32 code
  __ subl(len, 768);
  __ cmpl(len, 768);
  __ jcc(Assembler::less, GHASH_LAST_32);

  // AES 16 blocks and GHASH 16 blocks in parallel
  // For multiples of 48 blocks we will do ghash16_encrypt16 interleaved multiple times
  // Reduction value = no reduction means that the carryless multiplication values are accumulated for further calculations
  // Each call uses 4 subkeyHtbl values, so increment the index by 4.
  __ bind(GHASH_16_AES_16);
  // Reduction value = no reduction
  ghash16_encrypt16_parallel(key, avx512_subkeyHtbl, CTR_BLOCKx, AAD_HASHx, in, out, ct, pos, false, xmm24, false, rounds, ghash_pos, false, index, COUNTER_INC_MASK);
  __ addl(pos, 256);
  __ addl(ghash_pos, 256);
  index += 4;
  // Reduction value = final reduction means that the accumulated values have to be reduced as we have completed 48 blocks of ghash
  ghash16_encrypt16_parallel(key, avx512_subkeyHtbl, CTR_BLOCKx, AAD_HASHx, in, out, ct, pos, false, xmm24, false, rounds, ghash_pos, true, index, COUNTER_INC_MASK);
  __ addl(pos, 256);
  __ addl(ghash_pos, 256);
  // Calculated ghash value needs to be __ moved to AAD_HASHX so that we can restart the ghash16-aes16 pipeline
  __ movdqu(AAD_HASHx, ZTMP5);
  index = 0; // Reset subkeyHtbl index

  // Restart the pipeline
  // Reduction value = first time
  ghash16_encrypt16_parallel(key, avx512_subkeyHtbl, CTR_BLOCKx, AAD_HASHx, in, out, ct, pos, true, xmm24, true, rounds, ghash_pos, false, index, COUNTER_INC_MASK);
  __ addl(pos, 256);
  __ addl(ghash_pos, 256);
  index += 4;

  __ subl(len, 768);
  __ cmpl(len, 768);
  __ jcc(Assembler::greaterEqual, GHASH_16_AES_16);

  // GHASH last 32 blocks processed here
  // GHASH products accumulated in ZMM27, ZMM25 and ZMM26 during GHASH16-AES16 operation is used
  __ bind(GHASH_LAST_32);
  // Use rbx as a pointer to the htbl; For last 32 blocks of GHASH, use key# 4-11 entry in subkeyHtbl
  __ movl(rbx, 256);
  // Load cipher blocks
  __ evmovdquq(ZTMP13, Address(ct, ghash_pos, Address::times_1, 0 * 64), Assembler::AVX_512bit);
  __ evmovdquq(ZTMP14, Address(ct, ghash_pos, Address::times_1, 1 * 64), Assembler::AVX_512bit);
  __ vpshufb(ZTMP13, ZTMP13, xmm24, Assembler::AVX_512bit);
  __ vpshufb(ZTMP14, ZTMP14, xmm24, Assembler::AVX_512bit);
  // Load ghash keys
  __ evmovdquq(ZTMP15, Address(avx512_subkeyHtbl, rbx, Address::times_1, 0 * 64), Assembler::AVX_512bit);
  __ evmovdquq(ZTMP16, Address(avx512_subkeyHtbl, rbx, Address::times_1, 1 * 64), Assembler::AVX_512bit);

  // Ghash blocks 0 - 3
  carrylessMultiply(ZTMP2, ZTMP3, ZTMP4, ZTMP1, ZTMP13, ZTMP15);
  // Ghash blocks 4 - 7
  carrylessMultiply(ZTMP6, ZTMP7, ZTMP8, ZTMP5, ZTMP14, ZTMP16);

  __ vpternlogq(ZTMP1, 0x96, ZTMP5, xmm27, Assembler::AVX_512bit); // ZTMP1 = ZTMP1 + ZTMP5 + zmm27
  __ vpternlogq(ZTMP2, 0x96, ZTMP6, xmm26, Assembler::AVX_512bit); // ZTMP2 = ZTMP2 + ZTMP6 + zmm26
  __ vpternlogq(ZTMP3, 0x96, ZTMP7, xmm25, Assembler::AVX_512bit); // ZTMP3 = ZTMP3 + ZTMP7 + zmm25
  __ evpxorq(ZTMP4, ZTMP4, ZTMP8, Assembler::AVX_512bit);          // ZTMP4 = ZTMP4 + ZTMP8

  __ addl(ghash_pos, 128);
  __ addl(rbx, 128);

  // Ghash remaining blocks
  __ bind(LOOP);
  __ cmpl(ghash_pos, pos);
  __ jcc(Assembler::aboveEqual, ACCUMULATE);
  // Load next cipher blocks and corresponding ghash keys
  __ evmovdquq(ZTMP13, Address(ct, ghash_pos, Address::times_1, 0 * 64), Assembler::AVX_512bit);
  __ evmovdquq(ZTMP14, Address(ct, ghash_pos, Address::times_1, 1 * 64), Assembler::AVX_512bit);
  __ vpshufb(ZTMP13, ZTMP13, xmm24, Assembler::AVX_512bit);
  __ vpshufb(ZTMP14, ZTMP14, xmm24, Assembler::AVX_512bit);
  __ evmovdquq(ZTMP15, Address(avx512_subkeyHtbl, rbx, Address::times_1, 0 * 64), Assembler::AVX_512bit);
  __ evmovdquq(ZTMP16, Address(avx512_subkeyHtbl, rbx, Address::times_1, 1 * 64), Assembler::AVX_512bit);

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
  __ addl(ghash_pos, 128);
  __ addl(rbx, 128);
  __ jmp(LOOP);

  // Integrate ZTMP3/ZTMP4 into ZTMP1 and ZTMP2
  __ bind(ACCUMULATE);
  __ evpxorq(ZTMP3, ZTMP3, ZTMP4, Assembler::AVX_512bit);
  __ vpsrldq(ZTMP7, ZTMP3, 8, Assembler::AVX_512bit);
  __ vpslldq(ZTMP8, ZTMP3, 8, Assembler::AVX_512bit);
  __ evpxorq(ZTMP1, ZTMP1, ZTMP7, Assembler::AVX_512bit);
  __ evpxorq(ZTMP2, ZTMP2, ZTMP8, Assembler::AVX_512bit);

  // Add ZTMP1 and ZTMP2 128 - bit words horizontally
  vhpxori4x128(ZTMP1, ZTMP11);
  vhpxori4x128(ZTMP2, ZTMP12);
  // Load reduction polynomial and compute final reduction
  __ evmovdquq(ZTMP15, ExternalAddress(ghash_polynomial_reduction_addr()), Assembler::AVX_512bit, rbx /*rscratch*/);
  vclmul_reduce(AAD_HASHx, ZTMP15, ZTMP1, ZTMP2, ZTMP3, ZTMP4);

  // Pre-increment counter for next operation
  __ vpaddd(CTR_BLOCKx, CTR_BLOCKx, xmm18, Assembler::AVX_128bit);
  // Shuffle counter and save the updated value
  __ vpshufb(CTR_BLOCKx, CTR_BLOCKx, xmm24, Assembler::AVX_512bit);
  __ movdqu(Address(counter, 0), CTR_BLOCKx);
  // Load ghash lswap mask
  __ movdqu(xmm24, ExternalAddress(ghash_long_swap_mask_addr()), rbx /*rscratch*/);
  // Shuffle ghash using lbswap_mask and store it
  __ vpshufb(AAD_HASHx, AAD_HASHx, xmm24, Assembler::AVX_128bit);
  __ movdqu(Address(state, 0), AAD_HASHx);
  __ jmp(ENC_DEC_DONE);

  __ bind(GENERATE_HTBL_48_BLKS);
  generateHtbl_48_block_zmm(subkeyHtbl, avx512_subkeyHtbl, rbx /*rscratch*/);

  __ bind(ENC_DEC_DONE);
  __ movq(rax, pos);
}

//Implements data * hashkey mod (128, 127, 126, 121, 0)
//Inputs:
//GH and HK - 128 bits each
//Output:
//GH = GH * Hashkey mod poly
//Temp registers: xmm1, xmm2, xmm3, r15
void StubGenerator::gfmul_avx2(XMMRegister GH, XMMRegister HK) {
  const XMMRegister T1 = xmm1;
  const XMMRegister T2 = xmm2;
  const XMMRegister T3 = xmm3;

  __ vpclmulqdq(T1, GH, HK, 0x11); // %%T1 = a1*b1
  __ vpclmulqdq(T2, GH, HK, 0x00); // %%T2 = a0*b0
  __ vpclmulqdq(T3, GH, HK, 0x01); // %%T3 = a1*b0
  __ vpclmulqdq(GH, GH, HK, 0x10); // %%GH = a0*b1
  __ vpxor(GH, GH, T3, Assembler::AVX_128bit);

  __ vpsrldq(T3, GH, 8, Assembler::AVX_128bit); // shift-R %%GH 2 DWs
  __ vpslldq(GH, GH, 8, Assembler::AVX_128bit); // shift-L %%GH 2 DWs

  __ vpxor(T1, T1, T3, Assembler::AVX_128bit);
  __ vpxor(GH, GH, T2, Assembler::AVX_128bit);

  //first phase of the reduction
  __ movdqu(T3, ExternalAddress(ghash_polynomial_reduction_addr()), r15 /*rscratch*/);
  __ vpclmulqdq(T2, T3, GH, 0x01);
  __ vpslldq(T2, T2, 8, Assembler::AVX_128bit); // shift-L %%T2 2 DWs

  __ vpxor(GH, GH, T2, Assembler::AVX_128bit); // first phase of the reduction complete
  //second phase of the reduction
  __ vpclmulqdq(T2, T3, GH, 0x00);
  __ vpsrldq(T2, T2, 4, Assembler::AVX_128bit); // shift-R %%T2 1 DW (Shift-R only 1-DW to obtain 2-DWs shift-R)

  __ vpclmulqdq(GH, T3, GH, 0x10);
  __ vpslldq(GH, GH, 4, Assembler::AVX_128bit); // shift-L %%GH 1 DW (Shift-L 1-DW to obtain result with no shifts)

  __ vpxor(GH, GH, T2, Assembler::AVX_128bit); // second phase of the reduction complete
  __ vpxor(GH, GH, T1, Assembler::AVX_128bit); // the result is in %%GH
}

//Generate 8 constants from the given subkeyH.
//Input:
//htbl - table containing the initial subkeyH
//Output:
//htbl - containing 8 H constants
//Temp registers: xmm0, xmm1, xmm2, xmm3, xmm6, xmm11, xmm12, r15, rbx
void StubGenerator::generateHtbl_8_block_avx2(Register htbl) {
  const XMMRegister HK = xmm6;

  __ movdqu(HK, Address(htbl, 0));
  __ movdqu(xmm1, ExternalAddress(ghash_long_swap_mask_addr()), rbx /*rscratch*/);
  __ vpshufb(HK, HK, xmm1, Assembler::AVX_128bit);

  __ movdqu(xmm11, ExternalAddress(ghash_polynomial_addr()), rbx /*rscratch*/);
  __ movdqu(xmm12, ExternalAddress(ghash_polynomial_two_one_addr()), rbx /*rscratch*/);
  // Compute H ^ 2 from the input subkeyH
  __ vpsrlq(xmm1, xmm6, 63, Assembler::AVX_128bit);
  __ vpsllq(xmm6, xmm6, 1, Assembler::AVX_128bit);
  __ vpslldq(xmm2, xmm1, 8, Assembler::AVX_128bit);
  __ vpsrldq(xmm1, xmm1, 8, Assembler::AVX_128bit);

  __ vpor(xmm6, xmm6, xmm2, Assembler::AVX_128bit);

  __ vpshufd(xmm2, xmm1, 0x24, Assembler::AVX_128bit);
  __ vpcmpeqd(xmm2, xmm2, xmm12, Assembler::AVX_128bit);
  __ vpand(xmm2, xmm2, xmm11, Assembler::AVX_128bit);
  __ vpxor(xmm6, xmm6, xmm2, Assembler::AVX_128bit);
  __ movdqu(Address(htbl, 1 * 16), xmm6); // H * 2
  __ movdqu(xmm0, xmm6);
  for (int i = 2; i < 9; i++) {
    gfmul_avx2(xmm6, xmm0);
    __ movdqu(Address(htbl, i * 16), xmm6);
  }
}

#define aesenc_step_avx2(t_key)\
__ aesenc(xmm1, t_key);\
__ aesenc(xmm2, t_key);\
__ aesenc(xmm3, t_key);\
__ aesenc(xmm4, t_key);\
__ aesenc(xmm5, t_key);\
__ aesenc(xmm6, t_key);\
__ aesenc(xmm7, t_key);\
__ aesenc(xmm8, t_key);\

#define ghash_step_avx2(ghdata, hkey) \
__ vpclmulqdq(xmm11, ghdata, hkey, 0x11);\
__ vpxor(xmm12, xmm12, xmm11, Assembler::AVX_128bit);\
__ vpclmulqdq(xmm11, ghdata, hkey, 0x00);\
__ vpxor(xmm15, xmm15, xmm11, Assembler::AVX_128bit);\
__ vpclmulqdq(xmm11, ghdata, hkey, 0x01);\
__ vpxor(xmm14, xmm14, xmm11, Assembler::AVX_128bit);\
__ vpclmulqdq(xmm11, ghdata, hkey, 0x10);\
__ vpxor(xmm14, xmm14, xmm11, Assembler::AVX_128bit);\

//Encrypts and hashes 8 blocks in an interleaved fashion.
//Inputs:
//key - key for aes operations
//subkeyHtbl - table containing H constants
//ctr_blockx - counter for aes operations
//in - input buffer
//out - output buffer
//ct - ciphertext buffer
//pos - holds the length processed in this method
//in_order - boolean that indicates if incrementing counter without shuffling is needed
//rounds - number of aes rounds calculated based on key length
//xmm1-xmm8 - holds encrypted counter values
//Outputs:
//xmm1-xmm8 - updated encrypted counter values
//ctr_blockx - updated counter value
//out - updated output buffer
//Temp registers: xmm0, xmm10, xmm11, xmm12, xmm13, xmm14, xmm15, rbx
void StubGenerator::ghash8_encrypt8_parallel_avx2(Register key, Register subkeyHtbl, XMMRegister ctr_blockx, Register in,
                                                  Register out, Register ct, Register pos, bool in_order, Register rounds,
                                                  XMMRegister xmm1, XMMRegister xmm2, XMMRegister xmm3, XMMRegister xmm4,
                                                  XMMRegister xmm5, XMMRegister xmm6, XMMRegister xmm7, XMMRegister xmm8) {
  const XMMRegister t1 = xmm0;
  const XMMRegister t2 = xmm10;
  const XMMRegister t3 = xmm11;
  const XMMRegister t4 = xmm12;
  const XMMRegister t5 = xmm13;
  const XMMRegister t6 = xmm14;
  const XMMRegister t7 = xmm15;
  Label skip_reload, last_aes_rnd, aes_192, aes_256;

  __ movdqu(t2, xmm1);
  for (int i = 0; i <= 6; i++) {
    __ movdqu(Address(rsp, 16 * i), as_XMMRegister(i + 2));
  }

  if (in_order) {
    __ vpaddd(xmm1, ctr_blockx, ExternalAddress(counter_mask_linc1_addr()), Assembler::AVX_128bit, rbx /*rscratch*/); //Increment counter by 1
    __ movdqu(t5, ExternalAddress(counter_mask_linc2_addr()), rbx /*rscratch*/);
    __ vpaddd(xmm2, ctr_blockx, t5, Assembler::AVX_128bit);
    for (int rnum = 1; rnum <= 6; rnum++) {
      __ vpaddd(as_XMMRegister(rnum + 2), as_XMMRegister(rnum), t5, Assembler::AVX_128bit);
    }
    __ movdqu(ctr_blockx, xmm8);

    __ movdqu(t5, ExternalAddress(counter_shuffle_mask_addr()), rbx /*rscratch*/);
    for (int rnum = 1; rnum <= 8; rnum++) {
      __ vpshufb(as_XMMRegister(rnum), as_XMMRegister(rnum), t5, Assembler::AVX_128bit); //perform a 16Byte swap
    }
  } else {
    __ vpaddd(xmm1, ctr_blockx, ExternalAddress(counter_mask_linc1f_addr()), Assembler::AVX_128bit, rbx /*rscratch*/); //Increment counter by 1
    __ vmovdqu(t5, ExternalAddress(counter_mask_linc2f_addr()), Assembler::AVX_128bit, rbx /*rscratch*/);
    __ vpaddd(xmm2, ctr_blockx, t5, Assembler::AVX_128bit);
    for (int rnum = 1; rnum <= 6; rnum++) {
      __ vpaddd(as_XMMRegister(rnum + 2), as_XMMRegister(rnum), t5, Assembler::AVX_128bit);
    }
    __ movdqu(ctr_blockx, xmm8);
  }

  load_key(t1, key, 16 * 0, rbx /*rscratch*/);
  for (int rnum = 1; rnum <= 8; rnum++) {
    __ vpxor(as_XMMRegister(rnum), as_XMMRegister(rnum), t1, Assembler::AVX_128bit);
  }

  load_key(t1, key, 16 * 1, rbx /*rscratch*/);
  aesenc_step_avx2(t1);

  load_key(t1, key, 16 * 2, rbx /*rscratch*/);
  aesenc_step_avx2(t1);

  __ movdqu(t5, (Address(subkeyHtbl, 8 * 16)));
  __ vpclmulqdq(t4, t2, t5, 0x11); //t4 = a1*b1
  __ vpclmulqdq(t7, t2, t5, 0x00); //t7 = a0*b0
  __ vpclmulqdq(t6, t2, t5, 0x01); //t6 = a1*b0
  __ vpclmulqdq(t5, t2, t5, 0x10); //t5 = a0*b1
  __ vpxor(t6, t6, t5, Assembler::AVX_128bit);

  for (int i = 3, j = 0; i <= 8; i++, j++) {
    load_key(t1, key, 16 * i, rbx /*rscratch*/);
    aesenc_step_avx2(t1);
    __ movdqu(t1, Address(rsp, 16 * j));
    __ movdqu(t5, (Address(subkeyHtbl, (7 - j) * 16)));
    ghash_step_avx2(t1, t5);
  }

  load_key(t1, key, 16 * 9, rbx /*rscratch*/);
  aesenc_step_avx2(t1);

  __ movdqu(t1, Address(rsp, 16 * 6));
  __ movdqu(t5, (Address(subkeyHtbl, 1 * 16)));

  __ vpclmulqdq(t3, t1, t5, 0x00);
  __ vpxor(t7, t7, t3, Assembler::AVX_128bit);

  __ vpclmulqdq(t3, t1, t5, 0x01);
  __ vpxor(t6, t6, t3, Assembler::AVX_128bit);

  __ vpclmulqdq(t3, t1, t5, 0x10);
  __ vpxor(t6, t6, t3, Assembler::AVX_128bit);

  __ vpclmulqdq(t3, t1, t5, 0x11);
  __ vpxor(t1, t4, t3, Assembler::AVX_128bit);

  __ vpslldq(t3, t6, 8, Assembler::AVX_128bit); //shift-L t3 2 DWs
  __ vpsrldq(t6, t6, 8, Assembler::AVX_128bit); //shift-R t2 2 DWs
  __ vpxor(t7, t7, t3, Assembler::AVX_128bit);
  __ vpxor(t1, t1, t6, Assembler::AVX_128bit); // accumulate the results in t1:t7

  load_key(t5, key, 16 * 10, rbx /*rscratch*/);
  __ cmpl(rounds, 52);
  __ jcc(Assembler::less, last_aes_rnd);

  __ bind(aes_192);
  aesenc_step_avx2(t5);
  load_key(t5, key, 16 * 11, rbx /*rscratch*/);
  aesenc_step_avx2(t5);
  load_key(t5, key, 16 * 12, rbx /*rscratch*/);
  __ cmpl(rounds, 60);
  __ jcc(Assembler::less, last_aes_rnd);

  __ bind(aes_256);
  aesenc_step_avx2(t5);
  load_key(t5, key, 16 * 13, rbx /*rscratch*/);
  aesenc_step_avx2(t5);
  load_key(t5, key, 16 * 14, rbx /*rscratch*/);
  __ bind(last_aes_rnd);
  for (int rnum = 1; rnum <= 8; rnum++) {
    __ aesenclast(as_XMMRegister(rnum), t5);
  }

  for (int i = 0; i <= 7; i++) {
    __ movdqu(t2, Address(in, pos, Address::times_1, 16 * i));
    __ vpxor(as_XMMRegister(i + 1), as_XMMRegister(i + 1), t2, Assembler::AVX_128bit);
  }

  //first phase of the reduction
  __ vmovdqu(t3, ExternalAddress(ghash_polynomial_reduction_addr()), Assembler::AVX_128bit, rbx /*rscratch*/);

  __ vpclmulqdq(t2, t3, t7, 0x01);
  __ vpslldq(t2, t2, 8, Assembler::AVX_128bit); //shift-L xmm2 2 DWs

  __ vpxor(t7, t7, t2, Assembler::AVX_128bit); //first phase of the reduction complete

  //Write to the Ciphertext buffer
  for (int i = 0; i <= 7; i++) {
    __ movdqu(Address(out, pos, Address::times_1, 16 * i), as_XMMRegister(i + 1));
  }

  __ cmpptr(ct, out);
  __ jcc(Assembler::equal, skip_reload);
  for (int i = 0; i <= 7; i++) {
    __ movdqu(as_XMMRegister(i + 1), Address(in, pos, Address::times_1, 16 * i));
  }

  __ bind(skip_reload);
  //second phase of the reduction
  __ vpclmulqdq(t2, t3, t7, 0x00);
  __ vpsrldq(t2, t2, 4, Assembler::AVX_128bit); //shift-R t2 1 DW (Shift-R only 1-DW to obtain 2-DWs shift-R)

  __ vpclmulqdq(t4, t3, t7, 0x10);
  __ vpslldq(t4, t4, 4, Assembler::AVX_128bit); //shift-L t4 1 DW (Shift-L 1-DW to obtain result with no shifts)
  __ vpxor(t4, t4, t2, Assembler::AVX_128bit); //second phase of the reduction complete
  __ vpxor(t1, t1, t4, Assembler::AVX_128bit); //the result is in t1

  //perform a 16Byte swap
  __ movdqu(t7, ExternalAddress(counter_shuffle_mask_addr()), rbx /*rscratch*/);
  for (int rnum = 1; rnum <= 8; rnum++) {
    __ vpshufb(as_XMMRegister(rnum), as_XMMRegister(rnum), t7, Assembler::AVX_128bit);
  }
  __ vpxor(xmm1, xmm1, t1, Assembler::AVX_128bit);
}

//GHASH the last 8 ciphertext blocks.
//Input:
//subkeyHtbl - table containing H constants
//Output:
//xmm14 - calculated aad hash
//Temp registers: xmm0, xmm10, xmm11, xmm12, xmm13, xmm15, rbx
void StubGenerator::ghash_last_8_avx2(Register subkeyHtbl) {
  const XMMRegister t1 = xmm0;
  const XMMRegister t2 = xmm10;
  const XMMRegister t3 = xmm11;
  const XMMRegister t4 = xmm12;
  const XMMRegister t5 = xmm13;
  const XMMRegister t6 = xmm14;
  const XMMRegister t7 = xmm15;

  //Karatsuba Method
  __ movdqu(t5, Address(subkeyHtbl, 8 * 16));

  __ vpshufd(t2, xmm1, 78, Assembler::AVX_128bit);
  __ vpshufd(t3, t5, 78, Assembler::AVX_128bit);
  __ vpxor(t2, t2, xmm1, Assembler::AVX_128bit);
  __ vpxor(t3, t3, t5, Assembler::AVX_128bit);

  __ vpclmulqdq(t6, xmm1, t5, 0x11);
  __ vpclmulqdq(t7, xmm1, t5, 0x00);

  __ vpclmulqdq(xmm1, t2, t3, 0x00);

  for (int i = 7, rnum = 2; rnum <= 8; i--, rnum++) {
    __ movdqu(t5, Address(subkeyHtbl, i * 16));
    __ vpshufd(t2, as_XMMRegister(rnum), 78, Assembler::AVX_128bit);
    __ vpshufd(t3, t5, 78, Assembler::AVX_128bit);
    __ vpxor(t2, t2, as_XMMRegister(rnum), Assembler::AVX_128bit);
    __ vpxor(t3, t3, t5, Assembler::AVX_128bit);
    __ vpclmulqdq(t4, as_XMMRegister(rnum), t5, 0x11);
    __ vpxor(t6, t6, t4, Assembler::AVX_128bit);
    __ vpclmulqdq(t4, as_XMMRegister(rnum), t5, 0x00);
    __ vpxor(t7, t7, t4, Assembler::AVX_128bit);
    __ vpclmulqdq(t2, t2, t3, 0x00);
    __ vpxor(xmm1, xmm1, t2, Assembler::AVX_128bit);
  }

  __ vpxor(xmm1, xmm1, t6, Assembler::AVX_128bit);
  __ vpxor(t2, xmm1, t7, Assembler::AVX_128bit);

  __ vpslldq(t4, t2, 8, Assembler::AVX_128bit);
  __ vpsrldq(t2, t2, 8, Assembler::AVX_128bit);

  __ vpxor(t7, t7, t4, Assembler::AVX_128bit);
  __ vpxor(t6, t6, t2, Assembler::AVX_128bit); //<t6:t7> holds the result of the accumulated carry-less multiplications

  //first phase of the reduction
  __ movdqu(t3, ExternalAddress(ghash_polynomial_reduction_addr()), rbx /*rscratch*/);

  __ vpclmulqdq(t2, t3, t7, 0x01);
  __ vpslldq(t2, t2, 8, Assembler::AVX_128bit); // shift-L t2 2 DWs

  __ vpxor(t7, t7, t2, Assembler::AVX_128bit);//first phase of the reduction complete

  //second phase of the reduction
  __ vpclmulqdq(t2, t3, t7, 0x00);
  __ vpsrldq(t2, t2, 4, Assembler::AVX_128bit); //shift-R t2 1 DW (Shift-R only 1-DW to obtain 2-DWs shift-R)

  __ vpclmulqdq(t4, t3, t7, 0x10);
  __ vpslldq(t4, t4, 4, Assembler::AVX_128bit); //shift-L t4 1 DW (Shift-L 1-DW to obtain result with no shifts)
  __ vpxor(t4, t4, t2, Assembler::AVX_128bit); //second phase of the reduction complete
  __ vpxor(t6, t6, t4, Assembler::AVX_128bit); //the result is in t6
}

//Encrypt initial number of 8 blocks
//Inputs:
//ctr - counter for aes operations
//rounds - number of aes rounds calculated based on key length
//key - key for aes operations
//len - input length to be processed
//in - input buffer
//out - output buffer
//ct - ciphertext buffer
//aad_hashx - input aad hash
//pos - holds the length processed in this method
//Outputs:
//xmm1-xmm8 - holds updated encrypted counter values
//ctr - updated counter value
//pos - updated position
//len - updated length
//out - updated output buffer
//Temp registers: xmm0, xmm10, xmm11, xmm12, xmm13, xmm14, xmm15
void StubGenerator::initial_blocks_avx2(XMMRegister ctr, Register rounds, Register key, Register len, Register in,
                                        Register out, Register ct, XMMRegister aad_hashx, Register pos) {
  const XMMRegister t1 = xmm12;
  const XMMRegister t2 = xmm13;
  const XMMRegister t3 = xmm14;
  const XMMRegister t4 = xmm15;
  const XMMRegister t5 = xmm11;
  const XMMRegister t6 = xmm10;
  const XMMRegister t_key = xmm0;

  Label skip_reload, last_aes_rnd, aes_192, aes_256;
  //Move AAD_HASH to temp reg t3
  __ movdqu(t3, aad_hashx);
  //Prepare 8 counter blocks and perform rounds of AES cipher on
  //them, load plain/cipher text and store cipher/plain text.
  __ movdqu(xmm1, ctr);
  __ movdqu(t5, ExternalAddress(counter_mask_linc1_addr()), rbx /*rscratch*/);
  __ movdqu(t6, ExternalAddress(counter_mask_linc2_addr()), rbx /*rscratch*/ );
  __ vpaddd(xmm2, xmm1, t5, Assembler::AVX_128bit);
  for (int rnum = 1; rnum <= 6; rnum++) {
    __ vpaddd(as_XMMRegister(rnum + 2), as_XMMRegister(rnum), t6, Assembler::AVX_128bit);
  }
  __ movdqu(ctr, xmm8);

  __ movdqu(t5, ExternalAddress(counter_shuffle_mask_addr()), rbx /*rscratch*/);
  for (int rnum = 1; rnum <= 8; rnum++) {
    __ vpshufb(as_XMMRegister(rnum), as_XMMRegister(rnum), t5, Assembler::AVX_128bit); //perform a 16Byte swap
  }

  load_key(t_key, key, 16 * 0, rbx /*rscratch*/);
  for (int rnum = 1; rnum <= 8; rnum++) {
    __ vpxor(as_XMMRegister(rnum), as_XMMRegister(rnum), t_key, Assembler::AVX_128bit);
  }

  for (int i = 1; i <= 9; i++) {
    load_key(t_key, key, 16 * i, rbx /*rscratch*/);
    aesenc_step_avx2(t_key);
  }

  load_key(t_key, key, 16 * 10, rbx /*rscratch*/);
  __ cmpl(rounds, 52);
  __ jcc(Assembler::less, last_aes_rnd);

  __ bind(aes_192);
  aesenc_step_avx2(t_key);
  load_key(t_key, key, 16 * 11, rbx /*rscratch*/);
  aesenc_step_avx2(t_key);
  load_key(t_key, key, 16 * 12, rbx /*rscratch*/);
  __ cmpl(rounds, 60);
  __ jcc(Assembler::less, last_aes_rnd);

  __ bind(aes_256);
  aesenc_step_avx2(t_key);
  load_key(t_key, key, 16 * 13, rbx /*rscratch*/);
  aesenc_step_avx2(t_key);
  load_key(t_key, key, 16 * 14, rbx /*rscratch*/);

  __ bind(last_aes_rnd);
  for (int rnum = 1; rnum <= 8; rnum++) {
    __ aesenclast(as_XMMRegister(rnum), t_key);
  }

  //XOR and store data
  for (int i = 0; i <= 7; i++) {
    __ movdqu(t1, Address(in, pos, Address::times_1, 16 * i));
    __ vpxor(as_XMMRegister(i + 1), as_XMMRegister(i + 1), t1, Assembler::AVX_128bit);
    __ movdqu(Address(out, pos, Address::times_1, 16 * i), as_XMMRegister(i + 1));
  }

  __ cmpptr(ct, out);
  __ jcc(Assembler::equal, skip_reload);
  for (int i = 0; i <= 7; i++) {
    __ movdqu(as_XMMRegister(i + 1), Address(in, pos, Address::times_1, 16 * i));
  }

  __ bind(skip_reload);
  //Update len with the number of blocks processed
  __ subl(len, 128);
  __ addl(pos, 128);

  __ movdqu(t4, ExternalAddress(counter_shuffle_mask_addr()), rbx /*rscratch*/);
  for (int rnum = 1; rnum <= 8; rnum++) {
    __ vpshufb(as_XMMRegister(rnum), as_XMMRegister(rnum), t4, Assembler::AVX_128bit);
  }
  // Combine GHASHed value with the corresponding ciphertext
  __ vpxor(xmm1, xmm1, t3, Assembler::AVX_128bit);
}

//AES-GCM interleaved implementation
//Inputs:
//in - input buffer
//len- message length to be processed
//ct - cipher text buffer
//out - output buffer
//key - key for aes operations
//state - address of aad hash for ghash computation
//subkeyHtbl- table consisting of H constants
//counter - address of counter for aes operations
//Output:
//(counter) - updated in memory counter value
//(state) - updated in memory aad hash
//rax - length processed
//(out) - output buffer updated
//len - updated length
//Temp registers: xmm0-xmm15, r10, r15, rbx
void StubGenerator::aesgcm_avx2(Register in, Register len, Register ct, Register out, Register key,
                                Register state, Register subkeyHtbl, Register counter) {
  const Register pos = rax;
  const Register rounds = r10;
  const XMMRegister ctr_blockx = xmm9;
  const XMMRegister aad_hashx = xmm8;
  Label encrypt_done, encrypt_by_8_new, encrypt_by_8;

  //This routine should be called only for message sizes of 128 bytes or more.
  //Macro flow:
  //process 8 16 byte blocks in initial_num_blocks.
  //process 8 16 byte blocks at a time until all are done 'encrypt_by_8_new  followed by ghash_last_8'
  __ xorl(pos, pos);

  //Generate 8 constants for htbl
  generateHtbl_8_block_avx2(subkeyHtbl);

  //Compute #rounds for AES based on the length of the key array
  __ movl(rounds, Address(key, arrayOopDesc::length_offset_in_bytes() - arrayOopDesc::base_offset_in_bytes(T_INT)));

  //Load and shuffle state and counter values
  __ movdqu(ctr_blockx, Address(counter, 0));
  __ movdqu(aad_hashx, Address(state, 0));
  __ vpshufb(ctr_blockx, ctr_blockx, ExternalAddress(counter_shuffle_mask_addr()), Assembler::AVX_128bit, rbx /*rscratch*/);
  __ vpshufb(aad_hashx, aad_hashx, ExternalAddress(ghash_long_swap_mask_addr()), Assembler::AVX_128bit, rbx /*rscratch*/);

  initial_blocks_avx2(ctr_blockx, rounds, key, len, in, out, ct, aad_hashx, pos);

  //We need at least 128 bytes to proceed further.
  __ cmpl(len, 128);
  __ jcc(Assembler::less, encrypt_done);

  //in_order vs. out_order is an optimization to increment the counter without shuffling
  //it back into little endian. r15d keeps track of when we need to increment in order so
  //that the carry is handled correctly.
  __ movdl(r15, ctr_blockx);
  __ andl(r15, 255);
  __ vpshufb(ctr_blockx, ctr_blockx, ExternalAddress(counter_shuffle_mask_addr()), Assembler::AVX_128bit, rbx /*rscratch*/);

  __ bind(encrypt_by_8_new);
  __ cmpl(r15, 255 - 8);
  __ jcc(Assembler::greater, encrypt_by_8);

  __ addb(r15, 8);
  ghash8_encrypt8_parallel_avx2(key, subkeyHtbl, ctr_blockx, in, out, ct, pos, false, rounds,
                                xmm1, xmm2, xmm3, xmm4, xmm5, xmm6, xmm7, xmm8);
  __ addl(pos, 128);
  __ subl(len, 128);
  __ cmpl(len, 128);
  __ jcc(Assembler::greaterEqual, encrypt_by_8_new);

  __ vpshufb(ctr_blockx, ctr_blockx, ExternalAddress(counter_shuffle_mask_addr()), Assembler::AVX_128bit, rbx /*rscratch*/);
  __ jmp(encrypt_done);

  __ bind(encrypt_by_8);
  __ vpshufb(ctr_blockx, ctr_blockx, ExternalAddress(counter_shuffle_mask_addr()), Assembler::AVX_128bit, rbx /*rscratch*/);

  __ addb(r15, 8);
  ghash8_encrypt8_parallel_avx2(key, subkeyHtbl, ctr_blockx, in, out, ct, pos, true, rounds,
                                xmm1, xmm2, xmm3, xmm4, xmm5, xmm6, xmm7, xmm8);

  __ vpshufb(ctr_blockx, ctr_blockx, ExternalAddress(counter_shuffle_mask_addr()), Assembler::AVX_128bit, rbx /*rscratch*/);
  __ addl(pos, 128);
  __ subl(len, 128);
  __ cmpl(len, 128);
  __ jcc(Assembler::greaterEqual, encrypt_by_8_new);
  __ vpshufb(ctr_blockx, ctr_blockx, ExternalAddress(counter_shuffle_mask_addr()), Assembler::AVX_128bit, rbx /*rscratch*/);

  __ bind(encrypt_done);
  ghash_last_8_avx2(subkeyHtbl);

  __ vpaddd(ctr_blockx, ctr_blockx, ExternalAddress(counter_mask_linc1_addr()), Assembler::AVX_128bit, rbx /*rscratch*/);
  __ vpshufb(ctr_blockx, ctr_blockx, ExternalAddress(counter_shuffle_mask_addr()), Assembler::AVX_128bit, rbx /*rscratch*/);
  __ movdqu(Address(counter, 0), ctr_blockx); //current_counter = xmm9
  __ vpshufb(xmm14, xmm14, ExternalAddress(ghash_long_swap_mask_addr()), Assembler::AVX_128bit, rbx /*rscratch*/);
  __ movdqu(Address(state, 0), xmm14); //aad hash = xmm14
  //Xor out round keys
  __ vpxor(xmm0, xmm0, xmm0, Assembler::AVX_128bit);
  __ vpxor(xmm13, xmm13, xmm13, Assembler::AVX_128bit);

 }

#undef __
