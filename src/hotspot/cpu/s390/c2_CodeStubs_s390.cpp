/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026 IBM Corporation. All rights reserved.
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

#include "opto/c2_MacroAssembler.hpp"
#include "opto/c2_CodeStubs.hpp"
#include "runtime/sharedRuntime.hpp"

#define __ masm.

int C2SafepointPollStub::max_size() const {
  // TODO: recheck this number during runtime
  return 30;
}

void C2SafepointPollStub::emit(C2_MacroAssembler& masm) {
  assert(SharedRuntime::polling_page_return_handler_blob() != nullptr,
         "polling page return stub not created yet");
  __ bind(entry());
  address stub = SharedRuntime::polling_page_return_handler_blob()->entry_point();

  // Determine saved exception pc using pc relative address computation.
  {
    Label next_pc;
    __ z_larl(Z_R1_scratch, next_pc);
    __ bind(next_pc);
  }

  int current_offset = __ offset();
  // Code size must not depend on offsets.
  __ z_agfi(Z_R1_scratch, _safepoint_offset - current_offset);
  __ z_stg(Z_R1_scratch, Address(Z_thread, JavaThread::saved_exception_pc_offset()));

  __ load_const_optimized(Z_R1_scratch, (intptr_t)stub);
  __ z_br(Z_R1_scratch);
}

#undef __
