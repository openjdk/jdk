/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 */

#include "macroAssembler_x86.hpp"
#include "stubGenerator_x86_64.hpp"

#define __ _masm->

ATTRIBUTE_ALIGNED(64) constexpr uint64_t SHIFT1R[] = {
  0x0000000000000001ULL, 0x0000000000000002ULL,
  0x0000000000000003ULL, 0x0000000000000004ULL,
  0x0000000000000005ULL, 0x0000000000000006ULL,
  0x0000000000000007ULL, 0x0000000000000000ULL
};
static address shift_1R() {
  return (address)SHIFT1R;
}

ATTRIBUTE_ALIGNED(64) constexpr uint64_t SHIFT1L[] = {
  0x0000000000000007ULL, 0x0000000000000000ULL,
  0x0000000000000001ULL, 0x0000000000000002ULL,
  0x0000000000000003ULL, 0x0000000000000004ULL,
  0x0000000000000005ULL, 0x0000000000000006ULL
};
static address shift_1L() {
  return (address)SHIFT1L;
}

// Only elements 0 through 3 (c0..c2) need to permutated, otherwise setting
// index to 0 (zero)
ATTRIBUTE_ALIGNED(64) constexpr uint64_t PERMLOW[] = {
  0x0000000000000005ULL, 0x0000000000000006ULL,
  0x0000000000000007ULL, 0x0000000000000000ULL,
  0x0000000000000000ULL, 0x0000000000000000ULL,
  0x0000000000000000ULL, 0x0000000000000000ULL
};
static address perm_low() {
  return (address)PERMLOW;
}

// Only elements 4 and 5 (c3..c4) need to permutated, otherwise setting
// index to 0 (zero)
ATTRIBUTE_ALIGNED(64) constexpr uint64_t PERMLOWH[] = {
  0x0000000000000000ULL, 0x0000000000000000ULL,
  0x0000000000000000ULL, 0x0000000000000006ULL,
  0x0000000000000007ULL, 0x0000000000000000ULL,
  0x0000000000000000ULL, 0x0000000000000000ULL
};
static address perm_lowH() {
  return (address)PERMLOWH;
}

// Permutate element 4 to 0, otherwise setting index to 0 (zero)
ATTRIBUTE_ALIGNED(64) constexpr uint64_t LIMB0[] = {
  0x0000000000000004ULL, 0x0000000000000000ULL,
  0x0000000000000000ULL, 0x0000000000000000ULL,
  0x0000000000000000ULL, 0x0000000000000000ULL,
  0x0000000000000000ULL, 0x0000000000000000ULL
};
static address limb_0() {
  return (address)LIMB0;
}

void multiply_25519_avx512(const Register aLimbs, const Register bLimbs, const Register rLimbs, const Register tmp, MacroAssembler* _masm) {
  Register t0 = tmp;
  Register rscratch = tmp;

  // Inputs
  XMMRegister A = xmm0;
  XMMRegister B = xmm1;
  XMMRegister T = xmm2;

  // Intermediates
  XMMRegister Acc1  = xmm3;
  XMMRegister Acc2  = xmm4;
  XMMRegister Carry = xmm5;
  XMMRegister Acc1L = xmm6;

  // Constants
  XMMRegister shift1L  = xmm7;
  XMMRegister shift1R  = xmm8;
  XMMRegister permLow  = xmm9;
  XMMRegister permLowH = xmm10;
  XMMRegister CarryAdd = xmm12;
  XMMRegister CarryH   = xmm13;
  XMMRegister Limb0    = xmm14;
  KRegister allLimbs   = k1;
  KRegister permL      = k2;
  KRegister permLH     = k3;
  KRegister allColumns = k4;
  KRegister masks[]    = {permLH, allColumns, k5, k6, k7};

  __ mov64(t0, 0x1F);
  __ kmovql(allLimbs, t0);
  __ mov64(t0, 0x3F);
  __ kmovql(allColumns, t0);
  __ mov64(t0, 0x7);
  __ kmovql(permL, t0);
  __ mov64(t0, 0x18);
  __ kmovql(permLH, t0);
  __ evmovdqaq(shift1L, ExternalAddress(shift_1L()), Assembler::AVX_512bit, rscratch);
  __ evmovdqaq(shift1R, ExternalAddress(shift_1R()), Assembler::AVX_512bit, rscratch);
  __ evmovdqaq(permLow, allLimbs, ExternalAddress(perm_low()), false, Assembler::AVX_512bit, rscratch);
  __ evmovdqaq(permLowH, allLimbs, ExternalAddress(perm_lowH()), false, Assembler::AVX_512bit, rscratch);
  __ evmovdqaq(Limb0, allLimbs, ExternalAddress(limb_0()), false, Assembler::AVX_512bit, rscratch);

  // A = load(*aLimbs); masked evmovdquq() can be slow. Instead load full
  // 256bit, and combine with 64bit
  __ evmovdquq(A, Address(aLimbs, 8), Assembler::AVX_256bit);
  __ evpermq(A, allLimbs, shift1L, A, false, Assembler::AVX_512bit);
  __ movq(T, Address(aLimbs, 0));
  __ evporq(A, A, T, Assembler::AVX_512bit);

  // Acc1 = 0
  __ evporq(Acc1, Acc1, Acc1, Assembler::AVX_512bit);

  // Row 0
  __ vpbroadcastq(B, Address(bLimbs, 0), Assembler::AVX_512bit);
  __ evpmadd52luq(Acc1, allLimbs, A, B, true, Assembler::AVX_512bit);
  __ evpmadd52huq(Acc2, allLimbs, A, B, true, Assembler::AVX_512bit);
  __ vpermq(Acc2, shift1L, Acc2, Assembler::AVX_512bit);
  __ evpaddq(Acc1, allColumns, Acc1, Acc2, true, Assembler::AVX_512bit);

  // Shift for previous low order bits and high order alignment before add
  __ vpermq(Acc1, shift1R, Acc1, Assembler::AVX_512bit);

  // Acc2 = 0
  __ vpxorq(Acc2, Acc2, Acc2, Assembler::AVX_512bit);

  // Row 1
  __ vpbroadcastq(B, Address(bLimbs, 8), Assembler::AVX_512bit);
  __ evpmadd52luq(Acc1, allLimbs, A, B, true, Assembler::AVX_512bit);
  __ evpmadd52huq(Acc2, allLimbs, A, B, true, Assembler::AVX_512bit);
  __ vpermq(Acc2, shift1L, Acc2, Assembler::AVX_512bit);
  __ evpaddq(Acc1, allColumns, Acc1, Acc2, true, Assembler::AVX_512bit);
  __ vpermq(Acc1, shift1R, Acc1, Assembler::AVX_512bit);
  __ vpxorq(Acc2, Acc2, Acc2, Assembler::AVX_512bit);

  // Row 2
  __ vpbroadcastq(B, Address(bLimbs, 16), Assembler::AVX_512bit);
  __ evpmadd52luq(Acc1, allLimbs, A, B, true, Assembler::AVX_512bit);
  __ evpmadd52huq(Acc2, allLimbs, A, B, true, Assembler::AVX_512bit);
  __ vpermq(Acc2, shift1L, Acc2, Assembler::AVX_512bit);
  __ evpaddq(Acc1, allColumns, Acc1, Acc2, true, Assembler::AVX_512bit);
  __ vpermq(Acc1, shift1R, Acc1, Assembler::AVX_512bit);
  __ vpxorq(Acc2, Acc2, Acc2, Assembler::AVX_512bit);

  // At this point Acc1 is completely set at 8q, with single high order at c7.
  // We use Acc1 as the upper-limbs to complete the remaining accummulators
  // and we use Acc1L for the lower-limbs that will accumulate the reduction.
  // Move c0..c2 to Acc1L before Acc1 before zeroing respective positions
  __ evpermq(Acc1L, permL, permLow, Acc1, false, Assembler::AVX_512bit);

  // Row 3
  __ vpbroadcastq(B, Address(bLimbs, 24), Assembler::AVX_512bit);

  // Non-merge of luq of Acc1 zeros out c0..c2 positions, no need for them now.
  __ evpmadd52luq(Acc1, allLimbs, A, B, false, Assembler::AVX_512bit);
  __ evpmadd52huq(Acc2, allLimbs, A, B, true, Assembler::AVX_512bit);
  __ vpermq(Acc2, shift1L, Acc2, Assembler::AVX_512bit);
  __ evpaddq(Acc1, allColumns, Acc1, Acc2, true, Assembler::AVX_512bit);
  __ vpermq(Acc1, shift1R, Acc1, Assembler::AVX_512bit);
  __ vpxorq(Acc2, Acc2, Acc2, Assembler::AVX_512bit);

  // Row 4
  __ vpbroadcastq(B, Address(bLimbs, 32), Assembler::AVX_512bit);
  __ evpmadd52luq(Acc1, allLimbs, A, B, true, Assembler::AVX_512bit);
  __ evpmadd52huq(Acc2, allLimbs, A, B, true, Assembler::AVX_512bit);
  __ vpermq(Acc2, shift1L, Acc2, Assembler::AVX_512bit);
  __ evpaddq(Acc1, allColumns, Acc1, Acc2, true, Assembler::AVX_512bit);
  __ vpermq(Acc1, shift1R, Acc1, Assembler::AVX_512bit);

  // Move c3..c4 to Acc1L for accumulation in reduction
  __ evpermq(Acc1L, permLH, permLowH, Acc1, true, Assembler::AVX_512bit);

  // Pseudo-Mersenne reduction
  // The term is only 5 bits, the limbs 51 bits, and the elements are 64 bits,
  // therefore a scalar multiplication will not overflow the element radix here
  __ mov64(rax, 0x13ULL);
  __ evpbroadcastq(B, rax, Assembler::AVX_512bit);
  __ evpmullq(Acc1, allLimbs, Acc1, B, false, Assembler::AVX_512bit);
  __ evpaddq(Acc1L, allLimbs, Acc1L, Acc1, false, Assembler::AVX_512bit);

  // Perform carry and reduction from said carry-over. Note: masks[i] = limbs[i]
  for (int i = 0; i < 5; i++) {
    __ mov64(t0, 1ULL << i);
    __ kmovql(masks[i], t0);
  }
  __ mov64(rax, 0x0004000000000000ULL);
  __ evpbroadcast(CarryAdd, rax, Assembler::AVX_512bit);

  // Limb 3
  __ evpaddq(Carry, masks[3], Acc1L, CarryAdd, false, Assembler::AVX_512bit);
  __ evpsraq(Carry, masks[3], Carry, 51, false, Assembler::AVX_512bit);
  __ evpsllq(CarryH, masks[3], Carry, 51, false, Assembler::AVX_512bit);
  __ evpsubq(Acc1L, masks[3], Acc1L, CarryH, true, Assembler::AVX_512bit);
  __ vpermq(Carry, shift1L, Carry, Assembler::AVX_512bit);
  __ evpaddq(Acc1L, masks[4], Acc1L, Carry, true, Assembler::AVX_512bit);

  // Limb 4
  __ evpaddq(Carry, masks[4], Acc1L, CarryAdd, false, Assembler::AVX_512bit);
  __ evpsraq(Carry, masks[4], Carry, 51, false, Assembler::AVX_512bit);
  __ evpsllq(CarryH, masks[4], Carry, 51, false, Assembler::AVX_512bit);
  __ evpsubq(Acc1L, masks[4], Acc1L, CarryH, true, Assembler::AVX_512bit);

  // Reduction with c4+ (with B=19) back into c0
  __ evpmullq(B, masks[4], Carry, B, false, Assembler::AVX_512bit);
  __ evpermq(B, masks[0], Limb0, B, false, Assembler::AVX_512bit);
  __ evpaddq(Acc1L, masks[0], Acc1L, B, true, Assembler::AVX_512bit);

  // Limb 0
  __ evpaddq(Carry, masks[0], Acc1L, CarryAdd, false, Assembler::AVX_512bit);
  __ evpsraq(Carry, masks[0], Carry, 51, false, Assembler::AVX_512bit);
  __ evpsllq(CarryH, masks[0], Carry, 51, false, Assembler::AVX_512bit);
  __ evpsubq(Acc1L, masks[0], Acc1L, CarryH, true, Assembler::AVX_512bit);
  __ vpermq(Carry, shift1L, Carry, Assembler::AVX_512bit);
  __ evpaddq(Acc1L, masks[1], Acc1L, Carry, true, Assembler::AVX_512bit);

  // Limb 1
  __ evpaddq(Carry, masks[1], Acc1L, CarryAdd, false, Assembler::AVX_512bit);
  __ evpsraq(Carry, masks[1], Carry, 51, false, Assembler::AVX_512bit);
  __ evpsllq(CarryH, masks[1], Carry, 51, false, Assembler::AVX_512bit);
  __ evpsubq(Acc1L, masks[1], Acc1L, CarryH, true, Assembler::AVX_512bit);
  __ vpermq(Carry, shift1L, Carry, Assembler::AVX_512bit);
  __ evpaddq(Acc1L, masks[2], Acc1L, Carry, true, Assembler::AVX_512bit);

  // Limb 2
  __ evpaddq(Carry, masks[2], Acc1L, CarryAdd, false, Assembler::AVX_512bit);
  __ evpsraq(Carry, masks[2], Carry, 51, false, Assembler::AVX_512bit);
  __ evpsllq(CarryH, masks[2], Carry, 51, false, Assembler::AVX_512bit);
  __ evpsubq(Acc1L, masks[2], Acc1L, CarryH, true, Assembler::AVX_512bit);
  __ vpermq(Carry, shift1L, Carry, Assembler::AVX_512bit);
  __ evpaddq(Acc1L, masks[3], Acc1L, Carry, true, Assembler::AVX_512bit);

  // Limb 3
  __ evpaddq(Carry, masks[3], Acc1L, CarryAdd, false, Assembler::AVX_512bit);
  __ evpsraq(Carry, masks[3], Carry, 51, false, Assembler::AVX_512bit);
  __ evpsllq(CarryH, masks[3], Carry, 51, false, Assembler::AVX_512bit);
  __ evpsubq(Acc1L, masks[3], Acc1L, CarryH, true, Assembler::AVX_512bit);
  __ vpermq(Carry, shift1L, Carry, Assembler::AVX_512bit);
  __ evpaddq(Acc1L, masks[4], Acc1L, Carry, true, Assembler::AVX_512bit);

  __ evmovdquq(Address(rLimbs, 0), allLimbs, Accl1L, true, Assembler::AVX_512bit);

  // Cleanup
  // Zero out zmm0-zmm15, higher registers not used by intrinsics
  __ vzeroall();
}

address StubGenerator::generate_intpoly_mult_25519() {
  StubId stub_id = StubId::stubgen_intpoly_mult_25519_id;
  int entry_count = StubInfo::entry_count(stub_id);
  assert(entry_count == 1, "sanity check");
  address start = load_archive_data(stub_id);
  if (start != nullptr) {
    return start;
  }
  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, stub_id);
  start = __ pc();
  __ enter();

  if (VM_Version::supports_avx512ifma() && VM_Version::supports_avx512vlbw()) {
    // Register Map
    const Register aLimbs  = c_rarg0; // rdi | rcx
    const Register bLimbs  = c_rarg1; // rsi | rdx
    const Register rLimbs  = c_rarg2; // rdx | r8
    const Register tmp     = r9;

    multiply_25519_avx512(aLimbs, bLimbs, rLimbs, tmp, _masm);
  }

  __ leave();
  __ ret(0);

  // Record the stub entry and end
  store_archive_data(stub_id, start, __ pc());

  return start;
}

#undef __

#if INCLUDE_CDS
void StubGenerator::init_AOTAddressTable_poly_25519(GrowableArray<address>& external_addresses) {
#define ADD(addr) external_addresses.append((address)addr);
  // use accessors to retrive all correct addresses
  ADD(limb_0());
  ADD(perm_low());
  ADD(perm_lowH());
  ADD(shift_1R());
  ADD(shift_1L());
#undef ADD
}
#endif // INCLUDE_CDS
