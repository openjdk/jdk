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
#include "code/codeCache.hpp"
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

  // check for a valid frame_size, otherwise we are unlikely to get a valid sender_pc
  if (check && frame_size() <= 0) {
    return false;
  }

  *sender_sp = unextended_sp + frame_size();
  // Is sender_sp safe?
  if (check && thread != NULL && !thread->is_in_full_stack_checked((address)*sender_sp)) {
    return false;
  }
  // On Intel the return_address is always the word on the stack
  *sender_pc = (address)*((*sender_sp) - frame::return_addr_offset);

  if (sender_unextended_sp) *sender_unextended_sp = *sender_sp;
  // Note: frame::sender_sp_offset is only valid for compiled frame
  if (saved_fp) *saved_fp = (intptr_t**)((*sender_sp) - frame::sender_sp_offset);

  return true;
}

bool InterpreterBlob::sender_frame(JavaThread *thread, bool check, address pc, intptr_t* sp, intptr_t* unextended_sp, intptr_t* fp, bool fp_safe,
    address* sender_pc, intptr_t** sender_sp, intptr_t** sender_unextended_sp, intptr_t*** saved_fp) {

  assert(sender_pc != NULL, "invariant");
  assert(sender_sp != NULL, "invariant");

  // fp must be safe
  if (check && !fp_safe) {
    return false;
  }

  *sender_pc = (address)*(fp + frame::return_addr_offset);
  // for interpreted frames, the value below is the sender "raw" sp,
  // which can be different from the sender unextended sp (the sp seen
  // by the sender) because of current frame local variables
  *sender_sp = fp + frame::sender_sp_offset;

  if (sender_unextended_sp) *sender_unextended_sp = (intptr_t*)*(fp + frame::interpreter_frame_sender_sp_offset);
  if (saved_fp) *saved_fp = (intptr_t**)(fp + frame::link_offset);

  return true;
}

bool VtableBlob::sender_frame(JavaThread *thread, bool check, address pc, intptr_t* sp, intptr_t* unextended_sp, intptr_t* fp, bool fp_safe,
    address* sender_pc, intptr_t** sender_sp, intptr_t** sender_unextended_sp, intptr_t*** saved_fp) {

  assert(sender_pc != NULL, "invariant");
  assert(sender_sp != NULL, "invariant");

  *sender_sp = unextended_sp;
  // Is sender_sp safe?
  if (check && thread != NULL && !thread->is_in_full_stack_checked((address)*sender_sp)) {
    return false;
  }
  // On Intel the return_address is always the word on the stack
  *sender_pc = (address)*((*sender_sp) - frame::pc_return_offset);

  if (sender_unextended_sp) *sender_unextended_sp = *sender_sp;
  // Note: frame::sender_sp_offset is only valid for compiled frame
  if (saved_fp) *saved_fp = (intptr_t**)((*sender_sp) - frame::sender_sp_offset);

  return true;
}

bool StubRoutinesBlob::sender_frame(JavaThread *thread, bool check, address pc, intptr_t* sp, intptr_t* unextended_sp, intptr_t* fp, bool fp_safe,
    address* sender_pc, intptr_t** sender_sp, intptr_t** sender_unextended_sp, intptr_t*** saved_fp) {

  assert(sender_pc != NULL, "invariant");
  assert(sender_sp != NULL, "invariant");

  // fp must be safe
  if (check && !fp_safe) {
    return false;
  }

  *sender_sp = fp + frame::sender_sp_offset;
  // Is sender_sp safe?
  if (check && thread != NULL && !thread->is_in_full_stack_checked((address)*sender_sp)) {
    return false;
  }
  *sender_unextended_sp = *sender_sp;

  // On Intel the return_address is always the word on the stack
  *sender_pc = (address)*(fp + frame::return_addr_offset);
  // Note: frame::sender_sp_offset is only valid for compiled frame
  *saved_fp = (intptr_t**)(fp + frame::link_offset);

  return true;
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

  assert(sender_pc != NULL, "invariant");
  assert(sender_sp != NULL, "invariant");

  if (is_compiled_by_c1() || is_compiled_by_c2()) {
    // We landed on the prolog which looks like:
    //  mov %eax,-0x16000(%rsp) == stack banging
    //  push %rbp
    //  sub N, %rsp
    // Let's first figure out which instruction we're on
    int offset = pc - as_nmethod()->verified_entry_point();
    // If it's stack banging or `push %rbp`, %rsp hasn't been modified by this method
    if (offset == 0 /* stack banging */ || offset == 7 /* `push %rbp` */) {
      if (check && thread != NULL && !thread->is_in_full_stack_checked((address)sp)) {
        return false;
      }

      *sender_sp = sp;
      *sender_unextended_sp = *sender_sp;
      *sender_pc = (address)*(*sender_sp);

      CodeBlob *sender_cb = CodeCache::find_blob_unsafe(*sender_pc);
      if (*sender_pc == NULL ||  sender_cb == NULL) {
        return false;
      }
      // Could be a zombie method
      if (sender_cb->is_zombie() || sender_cb->is_unloaded()) {
        return false;
      }
      // Could just be some random pointer within the codeBlob
      if (!sender_cb->code_contains(*sender_pc)) {
        return false;
      }

      *saved_fp = (intptr_t**)((*sender_sp) + sender_cb->frame_size() - frame::sender_sp_offset);
      return true;
    // If it's `sub N, %rsp`, %rsp has been incremented by `push %rbp` already
    // but the stack frame hasn't been allocated
    } else if (offset == 8 /* `sub N, %rsp` */) {
      if (check && thread != NULL && !thread->is_in_full_stack_checked((address)sp)) {
        return false;
      }

      *sender_sp = sp;
      *sender_unextended_sp = *sender_sp;
      *sender_pc = (address)*((*sender_sp) + frame::return_addr_offset);
      *saved_fp = (intptr_t**)((*sender_sp) + frame::link_offset);
      return true;
    }
  }

  return CompiledMethod::sender_frame(thread, check, pc, sp, unextended_sp, fp, fp_safe,
                                                   sender_pc, sender_sp, sender_unextended_sp, saved_fp);
}
