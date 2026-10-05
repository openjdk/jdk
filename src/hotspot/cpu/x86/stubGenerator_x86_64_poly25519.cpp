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

const int32_t term = 19;
const int32_t limbs = 5;
const int32_t bpl = 51;
const int32_t rem = 64 - bpl;
const uint64_t MASK = 0x7FFFFFFFFFFFF;
const uint64_t CARRY_ADD = 0x4000000000000;

// Multiplication operation for polynomial arithmetic in Curve25519.
//
// This is the same algorithm as used in Java, except we use pseudo-Mersenne
// reduction to reduce register pressure instead of using the full 10 columns
// in Java.
void multiply_25519_scalar(const Register aLimbs, const Register bLimbs, const Register rLimbs, Register c[], Register bArg, Register d, Register b, Register mask, MacroAssembler* _masm) {

  for (int i = 0; i < limbs; i++) {
    __ xorq(c[i], c[i]);
  }
  __ mov64(mask, MASK);
  __ movq(bArg, bLimbs);

  // Perform high/low multiplication with signed 5x51 bit limbs
  for (int i = 0; i < limbs; i++) {
    __ movq(b, Address(bArg, i * 8));
    for (int j = 0; j < limbs; j++) {
      __ movq(rax, Address(aLimbs, j * 8));
      __ imulq(b);  // rdx:rax = a * b
      __ movq(d, rax);
      __ andq(d, mask);
      __ shrq(rax, bpl);
      __ shlq(rdx, rem);
      __ orq(rax, rdx);
      // Fold in pseudo-Mersenne reduction
      if ((i + j + 1) >= limbs) {
        __ imulq(rax, rax, term);
      }
      if ((i + j) >= limbs) {
        __ imulq(d, d, term);
      }
      __ addq(c[(i + j) % limbs], d);
      __ addq(c[(i + j + 1) % limbs], rax);
    }
  }

  // Carry-add with reduction from high limb
  Register carry = bArg;
  __ mov64(mask, CARRY_ADD);
  __ movq(carry, mask);

  // Limb 3
  __ addq(carry, c[3]);
  __ sarq(carry, bpl);
  __ addq(c[4], carry);
  __ shlq(carry, bpl);
  __ subq(c[3], carry);

  // Limb 4
  __ movq(carry, mask);
  __ addq(carry, c[4]);
  __ sarq(carry, bpl);

  // Reduce high order limb and fold back into low order limb
  __ mov64(rax, term);
  __ imulq(carry);
  __ addq(c[0], rax);

  __ shlq(carry, bpl);
  __ subq(c[4], carry);

  // Limbs 0 - 3
  for (int i = 0; i < (limbs - 1); i++) {
    __ movq(carry, mask);
    __ addq(carry, c[i]);
    __ sarq(carry, bpl);
    __ addq(c[i + 1], carry);
    __ shlq(carry, bpl);
    __ subq(c[i], carry);
  }

  __ pop_ppx(rdx);

  for (int i = 0; i < limbs; i++) {
    __ movq(Address(rLimbs, i * 8), c[i]);
  }
}

// Squaring operation for polynomial arithmetic in Curve25519.
//
// This is the same algorithm as used in Java, except we use pseudo-Mersenne
// reduction to reduce register pressure instead of using the full 10 columns
// in Java.
void square_25519_scalar(const Register aLimbs, const Register rLimbs, Register c[], Register aArg, Register d, Register carry, Register mask, MacroAssembler* _masm) {

  for (int i = 0; i < limbs; i++) {
    __ xorq(c[i], c[i]);
  }
  __ mov64(mask, MASK);

  // Perform high/low multiplication with signed 5x51 bit limbs
  for (int i = 0; i < limbs; i++) {
    __ movq(aArg, Address(aLimbs, i * 8));
    __ movq(rax, aArg);
    __ imulq(aArg);   // rdx:rax = a[j] * a[i]
    __ movq(d, rax);
    __ andq(d, mask);
    __ shrq(rax, bpl);
    __ shlq(rdx, rem);
    __ orq(rax, rdx); // rax = dd
    if ((i * 2 + 1) >= limbs) {
      __ imulq(rax, rax, term);
    }
    if ((i * 2) >= limbs) {
      __ imulq(d, d, term);
    }
    __ addq(c[(i * 2) % limbs], d);
    __ addq(c[(i * 2 + 1) % limbs], rax);
    for (int j = i + 1; j < limbs; j++) {
      __ movq(rax, Address(aLimbs, j * 8));
      __ imulq(aArg);   // rdx:rax = a * a
      __ movq(d, rax);
      __ andq(d, mask);
      __ shlq(d, 1);
      __ shrq(rax, bpl);
      __ shlq(rdx, rem);
      __ orq(rax, rdx); // rax = dd
      __ shlq(rax, 1);
      if ((j + i + 1) >= limbs) {
        __ imulq(rax, rax, term);
      }
      if ((j + i) >= limbs) {
        __ imulq(d, d, term);
      }
      __ addq(c[(i + j) % limbs], d);
      __ addq(c[(i + j + 1) % limbs], rax);
    }
  }

  // Carry-add with reduction from high limb
  // Limb 3
  __ mov64(mask, CARRY_ADD);
  __ movq(carry, mask);
  __ addq(carry, c[3]);
  __ sarq(carry, bpl);
  __ addq(c[4], carry);
  __ shlq(carry, bpl);
  __ subq(c[3], carry);

  // Limb 4
  __ movq(carry, mask);
  __ addq(carry, c[4]);
  __ sarq(carry, bpl);

  // Reduce high order limb and fold back into low order limb
  __ mov64(rax, term);
  __ imulq(carry);
  __ addq(c[0], rax);

  __ shlq(carry, bpl);
  __ subq(c[4], carry);

  // Limbs 0 - 3
  for (int i = 0; i < (limbs - 1); i++) {
    __ movq(carry, mask);
    __ addq(carry, c[i]);
    __ sarq(carry, bpl);
    __ addq(c[i + 1], carry);
    __ shlq(carry, bpl);
    __ subq(c[i], carry);
  }

  __ pop_ppx(rdx);

  for (int i = 0; i < limbs; i++) {
    __ movq(Address(rLimbs, i * 8), c[i]);
  }
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

  // Register Map
  const Register aLimbs  = c_rarg0; // rdi | rcx
  const Register bLimbs  = c_rarg1; // rsi | rdx
  const Register rLimbs  = c_rarg2; // rdx | r8

  Register c[]   = {r9, r10, r11, r12, r13};
  Register bArg  = r14;
  Register d     = r15;
  Register b     = rbp;
  Register mask  = rbx;

  __ push_ppx(rbp);
  __ push_ppx(rbx);
  __ push_ppx(r12);
  __ push_ppx(r13);
  __ push_ppx(r14);
  __ push_ppx(r15);
  __ push_ppx(rdx);

  multiply_25519_scalar(aLimbs, bLimbs, rLimbs, c, bArg, d, b, mask, _masm);

  // __ pop_ppx(rdx); // restored in the helper already
  __ pop_ppx(r15);
  __ pop_ppx(r14);
  __ pop_ppx(r13);
  __ pop_ppx(r12);
  __ pop_ppx(rbx);
  __ pop_ppx(rbp);

  __ leave();
  __ ret(0);

  // Record the stub entry and end
  store_archive_data(stub_id, start, __ pc());

  return start;
}

address StubGenerator::generate_intpoly_square_25519() {
  StubId stub_id = StubId::stubgen_intpoly_square_25519_id;
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

  // Register Map
  const Register aLimbs  = c_rarg0; // rdi | rcx
  const Register rLimbs  = c_rarg1; // rsi | rdx
  Register c[]   = {r9, r10, r11, r12, r13};
  Register aArg  = r14;
  Register d     = r15;
  Register carry = rbp;
  Register mask  = rbx;

  __ push_ppx(rbp);
  __ push_ppx(rbx);
  __ push_ppx(r12);
  __ push_ppx(r13);
  __ push_ppx(r14);
  __ push_ppx(r15);
  __ push_ppx(rdx);

  square_25519_scalar(aLimbs, rLimbs, c, aArg, d, carry, mask, _masm);

  // __ pop_ppx(rdx); // restored in the helper already
  __ pop_ppx(r15);
  __ pop_ppx(r14);
  __ pop_ppx(r13);
  __ pop_ppx(r12);
  __ pop_ppx(rbx);
  __ pop_ppx(rbp);

  __ leave();
  __ ret(0);

  // Record the stub entry and end
  store_archive_data(stub_id, start, __ pc());

  return start;
}
#undef __
