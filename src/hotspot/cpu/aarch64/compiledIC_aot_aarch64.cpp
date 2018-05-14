/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2018, Red Hat Inc. All rights reserved.
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

#include "aot/compiledIC_aot.hpp"
#include "code/codeCache.hpp"
#include "memory/resourceArea.hpp"

void CompiledDirectStaticCall::set_to_far(const methodHandle& callee, address entry) {
  if (TraceICs) {
    ResourceMark rm;
    tty->print_cr("CompiledDirectStaticCall@" INTPTR_FORMAT ": set_to_far %s",
                  p2i(instruction_address()),
                  callee->name_and_sig_as_C_string());
  }

  set_destination_mt_safe(entry);
}

void CompiledPltStaticCall::set_to_interpreted(const methodHandle& callee, address entry) {
  address stub = find_stub();
  guarantee(stub != NULL, "stub not found");
  if (TraceICs) {
    ResourceMark rm;
    tty->print_cr("CompiledPltStaticCall@" INTPTR_FORMAT ": set_to_interpreted %s",
                  p2i(instruction_address()),
                  callee->name_and_sig_as_C_string());
  }

  // Creation also verifies the object.
  NativeLoadGot* method_loader = nativeLoadGot_at(stub);
  NativeGotJump* jump          = nativeGotJump_at(method_loader->next_instruction_address());

  intptr_t data = method_loader->data();
  address destination = jump->destination();
  assert(data == 0 || data == (intptr_t)callee(),
         "a) MT-unsafe modification of inline cache");
  assert(destination == (address)Universe::non_oop_word()
         || destination == entry,
         "b) MT-unsafe modification of inline cache");

  // Update stub.
  method_loader->set_data((intptr_t)callee());
  jump->set_jump_destination(entry);

  // Update jump to call.
  set_destination_mt_safe(stub);
}

#ifdef NEVER_CALLED
void CompiledPltStaticCall::set_stub_to_clean(static_stub_Relocation* static_stub) {
  assert (CompiledIC_lock->is_locked() || SafepointSynchronize::is_at_safepoint(), "mt unsafe call");
  // Reset stub.
  address stub = static_stub->addr();
  assert(stub != NULL, "stub not found");
  // Creation also verifies the object.
  NativeLoadGot* method_loader = nativeLoadGot_at(stub);
  NativeGotJump* jump          = nativeGotJump_at(method_loader->next_instruction_address());
  method_loader->set_data(0);
  jump->set_jump_destination((address)-1);
}
#endif

#ifndef PRODUCT
void CompiledPltStaticCall::verify() {
  // Verify call.
  _call->verify();

#ifdef ASSERT
  CodeBlob *cb = CodeCache::find_blob_unsafe((address) _call);
  assert(cb && cb->is_aot(), "CompiledPltStaticCall can only be used on AOTCompiledMethod");
#endif

  // Verify stub.
  address stub = find_stub();
  assert(stub != NULL, "no stub found for static call");
  // Creation also verifies the object.
  NativeLoadGot*     method_loader = nativeLoadGot_at(stub);
  NativeGotJump*     jump          = nativeGotJump_at(method_loader->next_instruction_address());
  // Verify state.
  assert(is_clean() || is_call_to_compiled() || is_call_to_interpreted(), "sanity check");
}
#endif // !PRODUCT
