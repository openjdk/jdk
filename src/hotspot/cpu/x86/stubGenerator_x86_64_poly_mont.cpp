/*
 * Copyright (c) 2022, Intel Corporation. All rights reserved.
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
#include "macroAssembler_x86.hpp"
#include "stubGenerator_x86_64.hpp"

#define __ _masm->

ATTRIBUTE_ALIGNED(64) uint64_t MODULUS_P256[] = {
  0x000fffffffffffff, 0x00000fffffffffff, 
  0x0000000000000000, 0x0000001000000000, 
  0x0000ffffffff0000, 0x0000000000000000,
  0x0000000000000000, 0x0000000000000000
};
static address modulus_p256() {
  return (address)MODULUS_P256;
}

ATTRIBUTE_ALIGNED(64) uint64_t P256_MASK52[] = {
  0x000fffffffffffff, 0x000fffffffffffff,
  0x000fffffffffffff, 0x000fffffffffffff,
  0xffffffffffffffff, 0xffffffffffffffff,
  0xffffffffffffffff, 0xffffffffffffffff,
};
static address p256_mask52() {
  return (address)P256_MASK52;
}

ATTRIBUTE_ALIGNED(64) uint64_t BROADCAST5[] = {
  0x0000000000000004, 0x0000000000000004,
  0x0000000000000004, 0x0000000000000004,
  0x0000000000000004, 0x0000000000000004,
  0x0000000000000004, 0x0000000000000004,
};
static address broadcast_5() {
  return (address)BROADCAST5;
}

ATTRIBUTE_ALIGNED(64) uint64_t SHIFT1R[] = {
  0x0000000000000001, 0x0000000000000002,
  0x0000000000000003, 0x0000000000000004,
  0x0000000000000005, 0x0000000000000006,
  0x0000000000000007, 0x0000000000000000,
};
static address shift_1R() {
  return (address)SHIFT1R;
}

ATTRIBUTE_ALIGNED(64) uint64_t SHIFT1L[] = {
  0x0000000000000007, 0x0000000000000000,
  0x0000000000000001, 0x0000000000000002,
  0x0000000000000003, 0x0000000000000004,
  0x0000000000000005, 0x0000000000000006,
};
static address shift_1L() {
  return (address)SHIFT1L;
}

void StubGenerator::montgomeryMultiply(const Register aLimbs, const Register bLimbs, const Register rLimbs) {
  /*
    M1 = load(*m)
    M2 = M1 shift one q element <<
    A1 = load(*a)
    A2 = A1 shift one q element <<
    Acc1 = 0
    ---- for i = 0 to 5
        Acc2 = 0
        B1[0] = load(b[i])
        B1 = B1[0] replicate q
        Acc1 += A1 *  B1
        Acc2 += A2 *h B1
        N = Acc1[0] replicate q
        Acc1 += M1  * N
        Acc2 += M1 *h N
        // carry
        Acc1 = Acc1 shift one q element >>
        Acc1 = Acc1 + Acc2
    ---- done
    // Acc2 = Acc1 - P
    // select = Acc2(carry)
    // R = select ? Acc1 : Acc2
  */    

  Register t0 = r13;
  Register rscratch = r13;

  // Inputs
  XMMRegister A = xmm0;
  XMMRegister B = xmm1;
  XMMRegister T = xmm2;

  // Intermediates
  XMMRegister Acc1 = xmm10;
  XMMRegister Acc2 = xmm11;
  XMMRegister N = xmm12;
  XMMRegister select = xmm13;
  XMMRegister carry = xmm14;

  // Constants
  XMMRegister modulus = xmm20;
  XMMRegister shift1L = xmm21;
  XMMRegister shift1R = xmm22;
  XMMRegister mask52 = xmm23;
  XMMRegister broadcast5 = xmm24;
  KRegister limb0 = k1;
  KRegister limb5 = k2;
  KRegister allLimbs = k3;
  
  __ mov64(t0, 0x1);
  __ kmovql(limb0, t0);
  __ mov64(t0, 0x10);
  __ kmovql(limb5, t0);
  __ mov64(t0, 0x1f);
  __ kmovql(allLimbs, t0);
  __ evmovdquq(shift1L, allLimbs, ExternalAddress(shift_1L()), false, Assembler::AVX_512bit, rscratch);
  __ evmovdquq(shift1R, allLimbs, ExternalAddress(shift_1R()), false, Assembler::AVX_512bit, rscratch);
  __ evmovdquq(broadcast5, allLimbs, ExternalAddress(broadcast_5()), false, Assembler::AVX_512bit, rscratch);
  __ evmovdquq(mask52, allLimbs, ExternalAddress(p256_mask52()), false, Assembler::AVX_512bit, rscratch);

  // M1 = load(*m)
  __ evmovdquq(modulus, allLimbs, ExternalAddress(modulus_p256()), false, Assembler::AVX_512bit, rscratch);

  // A1 = load(*a)
  __ evmovdquq(A, Address(aLimbs, 8), Assembler::AVX_256bit);                          // Acc1 = load(*a)
  __ evpermq(A, allLimbs, shift1L, A, false, Assembler::AVX_512bit);
  __ movq(T, Address(aLimbs, 0));
  __ evporq(A, A, T, Assembler::AVX_512bit);

  // Acc1 = 0
  __ vpxorq(Acc1, Acc1, Acc1, Assembler::AVX_512bit);
  for (int i = 0; i< 5; i++) {
      // Acc2 = 0
      __ vpxorq(Acc2, Acc2, Acc2, Assembler::AVX_512bit);

      // B1[0] = load(b[i])
      // B1 = B1[0] replicate q
      __ vpbroadcastq(B, Address(bLimbs, i*8), Assembler::AVX_512bit);

      // Acc1 += A1 *  B1
      __ evpmadd52luq(Acc1, A, B, Assembler::AVX_512bit);
      
      // Acc2 += A2 *h B1
      __ evpmadd52huq(Acc2, A, B, Assembler::AVX_512bit);

      // N = Acc1[0] replicate q
      __ vpbroadcastq(N, Acc1, Assembler::AVX_512bit);

      // N = N mask 52 // not needed, implied by IFMA
      // Acc1 += M1  * N
      __ evpmadd52luq(Acc1, modulus, N, Assembler::AVX_512bit);

      // Acc2 += M1 *h N
      __ evpmadd52huq(Acc2, modulus, N, Assembler::AVX_512bit);

      // Acc1[0] =>> 52
      __ evpsrlq(Acc1, limb0, Acc1, 52, true, Assembler::AVX_512bit);

      // Acc2[0] += Acc1[0]
      __ evpaddq(Acc2, limb0, Acc1, Acc2, true, Assembler::AVX_512bit);

      // Acc1 = Acc1 shift one q element >>
      __ evpermq(Acc1, allLimbs, shift1R, Acc1, false, Assembler::AVX_512bit);
      
      // Acc1 = Acc1 + Acc2
      __ vpaddq(Acc1, Acc1, Acc2, Assembler::AVX_512bit);
  }
  
  // Carry1 = Acc1>>52
  __ evpsrlq(carry, allLimbs, Acc1, 52, false, Assembler::AVX_512bit);
  // Carry1 = shift one q element <<
  __ evpermq(carry, allLimbs, shift1L, carry, false, Assembler::AVX_512bit);
  // Acc1 += Carry1
  __ evpandq(Acc1, Acc1, mask52, Assembler::AVX_512bit); // Clear top 12 bits
  __ vpaddq(Acc1, Acc1, carry, Assembler::AVX_512bit);

  // output to r
  __ movq(Address(rLimbs, 0), Acc1);
  __ evpermq(Acc1, k0, shift1R, Acc1, true, Assembler::AVX_512bit);
  __ evmovdquq(Address(rLimbs, 8), k0, Acc1, true, Assembler::AVX_256bit);
}

address StubGenerator::generate_intpoly_montgomeryMult_P256() {
  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", "intpoly_montgomeryMult_P256");
  address start = __ pc();
  __ enter();

  // Save all 'SOE' registers
  __ push(rbx);
  #ifdef _WIN64
  __ push(rsi);
  __ push(rdi);
  #endif
  __ push(r12);
  __ push(r13);
  __ push(r14);
  __ push(r15);

  // Register Map
  const Register aLimbs  = rdi;
  const Register bLimbs  = rsi;
  const Register rLimbs  = rdx;

  // Normalize input
  // pseudo-signature: void poly1305_processBlocks(byte[] input, int length, int[5] accumulator, int[5] R)
  // a, b, r pointers point at first array element
  // java headers bypassed in LibraryCallKit::inline_poly1305_processBlocks
  #ifdef _WIN64
  // c_rarg0 - rcx
  // c_rarg1 - rdx
  // c_rarg2 - r8
  __ mov(aLimbs, c_rarg0);
  __ mov(bLimbs, c_rarg1);
  __ mov(rLimbs, c_rarg2);
  #else
  // Already in place
  // c_rarg0 - rdi
  // c_rarg1 - rsi
  // c_rarg2 - rdx
  #endif

  montgomeryMultiply(aLimbs, bLimbs, rLimbs);

  __ pop(r15);
  __ pop(r14);
  __ pop(r13);
  __ pop(r12);
  #ifdef _WIN64
  __ pop(rdi);
  __ pop(rsi);
  #endif
  __ pop(rbx);
  __ mov64(rax, 0x1);

  __ leave();
  __ ret(0);
  return start;
}
