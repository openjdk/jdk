/*
 * Copyright (c) 1997, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "asm/macroAssembler.hpp"
#include "runtime/flags/flagSetting.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/icache.hpp"

#define __ _masm->

void x86_generate_icache_fence(MacroAssembler* _masm) {
  switch (X86ICacheSync) {
    case 0:
      break;
    case 1:
      __ mfence();
      break;
    case 2:
    case 3:
      __ sfence();
      break;
    case 4:
      __ push_ppx(rax);
      __ push_ppx(rbx);
      __ push_ppx(rcx);
      __ push_ppx(rdx);
      __ xorptr(rax, rax);
      __ cpuid();
      __ pop_ppx(rdx);
      __ pop_ppx(rcx);
      __ pop_ppx(rbx);
      __ pop_ppx(rax);
      break;
    case 5:
      __ serialize();
      break;
    default:
      ShouldNotReachHere();
  }
}

void x86_generate_icache_flush_insn(MacroAssembler* _masm, Register addr) {
  switch (X86ICacheSync) {
    case 1:
      __ clflush(Address(addr, 0));
      break;
    case 2:
      __ clflushopt(Address(addr, 0));
      break;
    case 3:
      __ clwb(Address(addr, 0));
      break;
    default:
      ShouldNotReachHere();
  }
}

void ICacheStubGenerator::generate_icache_flush(ICache::flush_icache_stub_t* flush_icache_stub) {
  StubCodeMark mark(this, "ICache", _stub_name);

  address start = __ pc();

  const Register addr  = c_rarg0;
  const Register lines = c_rarg1;
  const Register magic = c_rarg2;

  Label flush_line, done;

  __ testl(lines, lines);
  __ jccb(Assembler::zero, done);

  x86_generate_icache_fence(_masm);

  if (1 <= X86ICacheSync && X86ICacheSync <= 3) {
    __ bind(flush_line);
    x86_generate_icache_flush_insn(_masm, addr);
    __ addptr(addr, ICache::line_size);
    __ decrementl(lines);
    __ jccb(Assembler::notZero, flush_line);

    x86_generate_icache_fence(_masm);
  }

  __ bind(done);

  __ movptr(rax, magic); // Handshake with caller to make sure it happened!
  __ ret(0);

  // Must be set here so StubCodeMark destructor can call the flush stub.
  *flush_icache_stub = (ICache::flush_icache_stub_t)start;
}

void ICache::initialize(int phase) {
  switch (phase) {
    case 1: {
      // Initial phase, we assume only CLFLUSH is available.
      IntFlagSetting fs(X86ICacheSync, 1);
      AbstractICache::initialize(phase);
      break;
    }
    case 2: {
      // Final phase, generate the stub again.
      AbstractICache::initialize(phase);
      break;
    }
    default:
      ShouldNotReachHere();
  }
}

#undef __
