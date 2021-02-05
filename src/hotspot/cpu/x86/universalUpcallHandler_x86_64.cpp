/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
 */

#include "precompiled.hpp"
#include "asm/macroAssembler.hpp"
#include "code/codeBlob.hpp"
#include "memory/resourceArea.hpp"
#include "prims/universalUpcallHandler.hpp"

#define __ _masm->

// 1. Create buffer according to layout
// 2. Load registers & stack args into buffer
// 3. Call upcall helper with upcall handler instance & buffer pointer (C++ ABI)
// 4. Load return value from buffer into foreign ABI registers
// 5. Return
address ProgrammableUpcallHandler::generate_upcall_stub(jobject rec, jobject jabi, jobject jlayout) {
  ResourceMark rm;
  const ABIDescriptor abi = ForeignGlobals::parse_abi_descriptor(jabi);
  const BufferLayout layout = ForeignGlobals::parse_buffer_layout(jlayout);

  CodeBuffer buffer("upcall_stub", 1024, upcall_stub_size);

  MacroAssembler* _masm = new MacroAssembler(&buffer);
  int stack_alignment_C = 16; // bytes
  int register_size = sizeof(uintptr_t);
  int buffer_alignment = xmm_reg_size;

  // stub code
  __ enter();

  // save pointer to JNI receiver handle into constant segment
  Address rec_adr = __ as_Address(InternalAddress(__ address_constant((address)rec)));

  __ subptr(rsp, (int) align_up(layout.buffer_size, buffer_alignment));

  Register used[] = { c_rarg0, c_rarg1, rax, rbx, rdi, rsi, r12, r13, r14, r15 };
  GrowableArray<Register> preserved;
  // TODO need to preserve anything killed by the upcall that is non-volatile, needs XMM regs as well, probably
  for (size_t i = 0; i < sizeof(used)/sizeof(Register); i++) {
    Register reg = used[i];
    if (!abi.is_volatile_reg(reg)) {
      preserved.push(reg);
    }
  }

  int preserved_size = align_up(preserved.length() * register_size, stack_alignment_C); // includes register alignment
  int buffer_offset = preserved_size; // offset from rsp

  __ subptr(rsp, preserved_size);
  for (int i = 0; i < preserved.length(); i++) {
    __ movptr(Address(rsp, i * register_size), preserved.at(i));
  }

  for (int i = 0; i < abi._integer_argument_registers.length(); i++) {
    size_t offs = buffer_offset + layout.arguments_integer + i * sizeof(uintptr_t);
    __ movptr(Address(rsp, (int)offs), abi._integer_argument_registers.at(i));
  }

  for (int i = 0; i < abi._vector_argument_registers.length(); i++) {
    XMMRegister reg = abi._vector_argument_registers.at(i);
    size_t offs = buffer_offset + layout.arguments_vector + i * xmm_reg_size;
    __ movdqu(Address(rsp, (int)offs), reg);
  }

  // Capture prev stack pointer (stack arguments base)
#ifndef _WIN64
  __ lea(rax, Address(rbp, 16)); // skip frame+return address
#else
  __ lea(rax, Address(rbp, 16 + 32)); // also skip shadow space
#endif
  __ movptr(Address(rsp, buffer_offset + (int) layout.stack_args), rax);
#ifndef PRODUCT
  __ movptr(Address(rsp, buffer_offset + (int) layout.stack_args_bytes), -1); // unknown
#endif

  // Call upcall helper

  __ movptr(c_rarg0, rec_adr);
  __ lea(c_rarg1, Address(rsp, buffer_offset));

#ifdef _WIN64
  __ block_comment("allocate shadow space for argument register spill");
  __ subptr(rsp, 32);
#endif

  __ call(RuntimeAddress(CAST_FROM_FN_PTR(address, ProgrammableUpcallHandler::attach_thread_and_do_upcall)));

#ifdef _WIN64
  __ block_comment("pop shadow space");
  __ addptr(rsp, 32);
#endif

  for (int i = 0; i < abi._integer_return_registers.length(); i++) {
    size_t offs = buffer_offset + layout.returns_integer + i * sizeof(uintptr_t);
    __ movptr(abi._integer_return_registers.at(i), Address(rsp, (int)offs));
  }

  for (int i = 0; i < abi._vector_return_registers.length(); i++) {
    XMMRegister reg = abi._vector_return_registers.at(i);
    size_t offs = buffer_offset + layout.returns_vector + i * xmm_reg_size;
    __ movdqu(reg, Address(rsp, (int)offs));
  }

  for (size_t i = abi._X87_return_registers_noof; i > 0 ; i--) {
      ssize_t offs = buffer_offset + layout.returns_x87 + (i - 1) * (sizeof(long double));
      __ fld_x (Address(rsp, (int)offs));
  }

  // Restore preserved registers
  for (int i = 0; i < preserved.length(); i++) {
    __ movptr(preserved.at(i), Address(rsp, i * register_size));
  }

  __ leave();
  __ ret(0);

  _masm->flush();

  BufferBlob* blob = BufferBlob::create("upcall_stub", &buffer);

  return blob->code_begin();
}
