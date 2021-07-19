/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, Datadog, Inc. All rights reserved.
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
#include "code/codeBlob.hpp"
#include "code/compiledMethod.hpp"
#include "code/nmethod.hpp"
#include "interpreter/interpreter.hpp"
#include "runtime/frame.hpp"

bool CodeBlob::sender_frame(JavaThread *thread, bool check, address pc, intptr_t* sp, intptr_t* unextended_sp, intptr_t* fp, bool fp_safe,
    address* sender_pc, intptr_t** sender_sp, intptr_t** sender_unextended_sp, intptr_t*** saved_fp) {
  // must be some sort of compiled/runtime frame
  // fp does not have to be safe (although it could be check for c1?)

  assert(sender_pc != NULL, "invariant");
  assert(sender_sp != NULL, "invariant");


  // First check if frame is complete and tester is reliable
  if (check && !is_frame_complete_at(pc)) {
    // Adapter blobs never have a complete frame and are never ok.
    if (is_adapter_blob()) {
      return false;
    }
  }

  frame::abi_minframe* sender_abi = (frame::abi_minframe*) fp;
  *sender_sp = (intptr_t*) fp;
  *sender_pc = (address) sender_abi->lr;

  return true;
}

bool InterpreterBlob::sender_frame(JavaThread *thread, bool check, address pc, intptr_t* sp, intptr_t* unextended_sp, intptr_t* fp, bool fp_safe,
    address* sender_pc, intptr_t** sender_sp, intptr_t** sender_unextended_sp, intptr_t*** saved_fp) {
  return CodeBlob::sender_frame(thread, check, pc, sp, unextended_sp, fp, fp_safe,
                                             sender_pc, sender_sp, sender_unextended_sp, saved_fp);
}

bool VtableBlob::sender_frame(JavaThread *thread, bool check, address pc, intptr_t* sp, intptr_t* unextended_sp, intptr_t* fp, bool fp_safe,
    address* sender_pc, intptr_t** sender_sp, intptr_t** sender_unextended_sp, intptr_t*** saved_fp) {
  return CodeBlob::sender_frame(thread, check, pc, sp, unextended_sp, fp, fp_safe,
                                             sender_pc, sender_sp, sender_unextended_sp, saved_fp);
}

bool StubRoutinesBlob::sender_frame(JavaThread *thread, bool check, address pc, intptr_t* sp, intptr_t* unextended_sp, intptr_t* fp, bool fp_safe,
    address* sender_pc, intptr_t** sender_sp, intptr_t** sender_unextended_sp, intptr_t*** saved_fp) {

  assert(sender_pc != NULL, "invariant");
  assert(sender_sp != NULL, "invariant");

  // First check if frame is complete and tester is reliable
  if (check && !is_frame_complete_at(pc)) {
    return false;
  }

  return CodeBlob::sender_frame(thread, check, pc, sp, unextended_sp, fp, fp_safe,
                                             sender_pc, sender_sp, sender_unextended_sp, saved_fp);
}

bool CompiledMethod::sender_frame(JavaThread *thread, bool check, address pc, intptr_t* sp, intptr_t* unextended_sp, intptr_t* fp, bool fp_safe,
    address* sender_pc, intptr_t** sender_sp, intptr_t** sender_unextended_sp, intptr_t*** saved_fp) {

  assert(sender_pc != NULL, "invariant");
  assert(sender_sp != NULL, "invariant");

  // First check if frame is complete and tester is reliable
  if (check && !is_frame_complete_at(pc)) {
    return false;
  }

  return CodeBlob::sender_frame(thread, check, pc, sp, unextended_sp, fp, fp_safe,
                                             sender_pc, sender_sp, sender_unextended_sp, saved_fp);
}

bool nmethod::sender_frame(JavaThread *thread, bool check, address pc, intptr_t* sp, intptr_t* unextended_sp, intptr_t* fp, bool fp_safe,
    address* sender_pc, intptr_t** sender_sp, intptr_t** sender_unextended_sp, intptr_t*** saved_fp) {
  return CompiledMethod::sender_frame(thread, check, pc, sp, unextended_sp, fp, fp_safe,
                                                   sender_pc, sender_sp, sender_unextended_sp, saved_fp);
}
