/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#ifndef SHARE_ASM_CODEBUFFER_INLINE_HPP
#define SHARE_ASM_CODEBUFFER_INLINE_HPP

#include "asm/codeBuffer.hpp"
#include "ci/ciEnv.hpp"
#include "code/compiledIC.hpp"

#define __ masm.

template <typename MacroAssembler, int relocate_format = 0>
bool emit_shared_stubs_to_interp(CodeBuffer* cb, SharedStubToInterpRequests* shared_stub_to_interp_requests) {
  if (shared_stub_to_interp_requests == nullptr) {
    return true;
  }
  auto by_shared_method = [](SharedStubToInterpRequest* r1, SharedStubToInterpRequest* r2) {
    if (r1->shared_method() < r2->shared_method()) {
      return -1;
    } else if (r1->shared_method() > r2->shared_method()) {
      return 1;
    } else {
      return 0;
    }
  };
  shared_stub_to_interp_requests->sort(by_shared_method);
  MacroAssembler masm(cb);
  for (int i = 0; i < shared_stub_to_interp_requests->length();) {
    address stub = __ start_a_stub(CompiledDirectCall::to_interp_stub_size());
    if (stub == nullptr) {
      return false;
    }

    ciMethod* method = shared_stub_to_interp_requests->at(i).shared_method();
    do {
      address caller_pc = cb->insts_begin() + shared_stub_to_interp_requests->at(i).call_offset();
      __ relocate(static_stub_Relocation::spec(caller_pc), relocate_format);
      ++i;
    } while (i < shared_stub_to_interp_requests->length() && shared_stub_to_interp_requests->at(i).shared_method() == method);
    __ emit_static_call_stub();
    __ end_a_stub();
  }
  return true;
}

#undef __

#endif // SHARE_ASM_CODEBUFFER_INLINE_HPP
