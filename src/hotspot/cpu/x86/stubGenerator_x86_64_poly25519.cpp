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

void multiply_25519_scalar(const Register aLimbs, const Register bLimbs, const Register rLimbs, MacroAssembler* _masm) {
  Register c0    = r9;
  Register c1    = r10;
  Register c2    = r11;
  Register c3    = r12;
  Register c4    = r13;
  Register c[]   = {c0, c1, c2, c3, c4};
  Register bArg  = r14;
  Register d     = r15;
  Register b     = rbp;
  Register mask  = rbx;

  __ push(rbp);
  __ push(rbx);
  __ push(r12);
  __ push(r13);
  __ push(r14);
  __ push(r15);
  __ push(rdx);

  __ xorq(c0, c0);
  __ xorq(c1, c1);
  __ xorq(c2, c2);
  __ xorq(c3, c3);
  __ xorq(c4, c4);
  __ mov64(mask, 0x7FFFFFFFFFFFF);
  __ movq(bArg, bLimbs);

  // Perform high/low multiplication with signed 5x51 bit limbs
  for (int i = 0; i < 5; i++) {
    __ movq(b, Address(bArg, i * 8));
    for (int j = 0; j < 5; j++) {
      __ movq(rax, Address(aLimbs, j * 8));
      __ imulq(b);  // rdx:rax = a * b
      __ movq(d, rax);
      __ andq(d, mask);
      __ shrq(rax, 51);
      __ shlq(rdx, 13);
      __ orq(rax, rdx);
      // Fold in pseudo-Mersenne reduction
      if ((i + j + 1) > 4) {
        __ imulq(rax, rax, 19);
      }
      if ((i + j) > 4) {
        __ imulq(d, d, 19);
      }
      __ addq(c[(i + j) % 5], d);
      __ addq(c[(i + j + 1) % 5], rax);
    }
  }

  // Carry-add with reduction from high limb
  Register carry = bArg;
  // Limb 3
  __ mov64(carry, 0x4000000000000);
  __ addq(carry, c[3]);
  __ sarq(carry, 51);
  __ addq(c[4], carry);
  __ shlq(carry, 51);
  __ subq(c[3], carry);

  // Limb 4
  __ mov64(carry, 0x4000000000000);
  __ addq(carry, c[4]);
  __ sarq(carry, 51);

  // Reduce high order limb and fold back into low order limb
  __ mov64(rax, 0x13);
  __ imulq(carry);
  __ addq(c[0], rax);

  __ shlq(carry, 51);
  __ subq(c[4], carry);

  // Limbs 0 - 3
  for (int i = 0; i < 4; i++) {
    __ mov64(carry, 0x4000000000000);
    __ addq(carry, c[i]);
    __ sarq(carry, 51);
    __ addq(c[i + 1], carry);
    __ shlq(carry, 51);
    __ subq(c[i], carry);
  }

  __ pop(rdx);

  __ movq(Address(rLimbs, 0), c[0]);
  __ movq(Address(rLimbs, 8), c[1]);
  __ movq(Address(rLimbs, 16), c[2]);
  __ movq(Address(rLimbs, 24), c[3]);
  __ movq(Address(rLimbs, 32), c[4]);

  __ pop(r15);
  __ pop(r14);
  __ pop(r13);
  __ pop(r12);
  __ pop(rbx);
  __ pop(rbp);
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

  multiply_25519_scalar(aLimbs, bLimbs, rLimbs, _masm);

  __ leave();
  __ ret(0);

  // Record the stub entry and end
  store_archive_data(stub_id, start, __ pc());

  return start;
}
#undef __
