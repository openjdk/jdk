/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2022, Huawei Technologies Co., Ltd. All rights reserved.
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
#include "opto/c2_CodeStubs.hpp"
#include "opto/c2_MacroAssembler.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"

#define __ masm.

void C2SafepointPollStub::emit(C2_MacroAssembler& masm) {
  assert(SharedRuntime::polling_page_return_handler_blob() != NULL,
         "polling page return stub not created yet");
  address stub = SharedRuntime::polling_page_return_handler_blob()->entry_point();
  RuntimeAddress callback_addr(stub);

  __ bind(entry());
  InternalAddress safepoint_pc(__ pc() - __ offset() + _safepoint_offset);
  __ relocate(safepoint_pc.rspec(), [&] {
    __ la(t0, safepoint_pc.target());
  });
  __ sd(t0, Address(xthread, JavaThread::saved_exception_pc_offset()));
  __ far_jump(callback_addr);
}

void C2EntryBarrierStub::emit(C2_MacroAssembler& masm) {
  Assembler::IncompressibleRegion ir(&masm); // Fixed length

  // make guard value 4-byte aligned so that it can be accessed by atomic instructions on riscv
  int alignment_bytes = __ align(4);

  __ bind(entry());

  int32_t offset = 0;
  __ movptr(t0, StubRoutines::riscv::method_entry_barrier(), offset);
  __ jalr(ra, t0, offset);
  __ j(continuation());

  __ bind(guard());
  __ relocate(entry_guard_Relocation::spec());
  __ assert_alignment(__ pc());
  __ emit_int32(0);  // nmethod guard value
  // make sure the stub with a fixed code size
  if (alignment_bytes == 2) {
    assert(UseRVC, "bad alignment");
    __ c_nop();
  } else {
    assert(alignment_bytes == 0, "bad alignment");
    __ nop();
  }
}

#undef __
