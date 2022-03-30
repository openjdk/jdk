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

#include "asm/codeBuffer.hpp"
#include "asm/macroAssembler.hpp"
#include "ci/ciEnv.hpp"
#include "code/compiledIC.hpp"

bool CodeBuffer::emit_shared_stubs_to_interp() {
  MacroAssembler masm(this);
  LinkedListIterator<SharedStubToInterpRequest> it(_shared_stub_to_interp_requests.head());
  SharedStubToInterpRequest* request = it.next();
  while (request != NULL) {
    address stub = masm.start_a_stub(CompiledStaticCall::to_interp_stub_size());
    if (stub == NULL) {
      ciEnv::current()->record_failure("CodeCache is full");
      return false;
    }
    Method* method = request->shared_method();
    do {
      masm.relocate(static_stub_Relocation::spec(request->caller_pc()));
      request = it.next();
    } while (request != NULL && request->shared_method() == method);
    masm.emit_static_call_stub();
    masm.end_a_stub();
  }
  return true;
}