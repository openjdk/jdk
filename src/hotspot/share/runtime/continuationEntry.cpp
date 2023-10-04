/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "code/compiledIC.hpp"
#include "code/nmethod.hpp"
#include "oops/method.inline.hpp"
#include "runtime/continuation.hpp"
#include "runtime/continuationEntry.inline.hpp"
#include "runtime/continuationHelper.inline.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/stackFrameStream.inline.hpp"
#include "runtime/stackWatermarkSet.inline.hpp"
#include "runtime/stubRoutines.hpp"

int ContinuationEntry::_return_pc_offset = 0;
address ContinuationEntry::_return_pc = nullptr;
CompiledMethod* ContinuationEntry::_enter_special = nullptr;
int ContinuationEntry::_interpreted_entry_offset = 0;

void ContinuationEntry::set_enter_code(CompiledMethod* cm, int interpreted_entry_offset) {
  assert(_return_pc_offset != 0, "");
  _return_pc = cm->code_begin() + _return_pc_offset;

  _enter_special = cm;
  _interpreted_entry_offset = interpreted_entry_offset;
  assert(_enter_special->code_contains(compiled_entry()),    "entry not in enterSpecial");
  assert(_enter_special->code_contains(interpreted_entry()), "entry not in enterSpecial");
  assert(interpreted_entry() < compiled_entry(), "unexpected code layout");
}

address ContinuationEntry::compiled_entry() {
  return _enter_special->verified_entry_point();
}

address ContinuationEntry::interpreted_entry() {
  return _enter_special->code_begin() + _interpreted_entry_offset;
}

bool ContinuationEntry::is_interpreted_call(address call_address) {
  assert(_enter_special->code_contains(call_address), "call not in enterSpecial");
  assert(call_address >= interpreted_entry(), "unexpected location");
  return call_address < compiled_entry();
}

ContinuationEntry* ContinuationEntry::from_frame(const frame& f) {
  assert(Continuation::is_continuation_enterSpecial(f), "");
  return (ContinuationEntry*)f.unextended_sp();
}

NOINLINE static void flush_stack_processing(JavaThread* thread, intptr_t* sp) {
  log_develop_trace(continuations)("flush_stack_processing");
  for (StackFrameStream fst(thread, true, true); fst.current()->sp() <= sp; fst.next()) {
    ;
  }
}

inline void maybe_flush_stack_processing(JavaThread* thread, intptr_t* sp) {
  StackWatermark* sw;
  uintptr_t watermark;
  if ((sw = StackWatermarkSet::get(thread, StackWatermarkKind::gc)) != nullptr
        && (watermark = sw->watermark()) != 0
        && watermark <= (uintptr_t)sp) {
    flush_stack_processing(thread, sp);
  }
}

void ContinuationEntry::flush_stack_processing(JavaThread* thread) const {
  maybe_flush_stack_processing(thread, (intptr_t*)((uintptr_t)entry_sp() + ContinuationEntry::size()));
}

#ifndef PRODUCT
void ContinuationEntry::describe(FrameValues& values, int frame_no) const {
  address usp = (address)this;
  values.describe(frame_no, (intptr_t*)(usp + in_bytes(ContinuationEntry::parent_offset())),    "parent");
  values.describe(frame_no, (intptr_t*)(usp + in_bytes(ContinuationEntry::cont_offset())),      "continuation");
  values.describe(frame_no, (intptr_t*)(usp + in_bytes(ContinuationEntry::flags_offset())),     "flags");
  values.describe(frame_no, (intptr_t*)(usp + in_bytes(ContinuationEntry::chunk_offset())),     "chunk");
  values.describe(frame_no, (intptr_t*)(usp + in_bytes(ContinuationEntry::argsize_offset())),   "argsize");
  values.describe(frame_no, (intptr_t*)(usp + in_bytes(ContinuationEntry::pin_count_offset())), "pin_count");
  values.describe(frame_no, (intptr_t*)(usp + in_bytes(ContinuationEntry::parent_cont_fastpath_offset())),      "parent fastpath");
  values.describe(frame_no, (intptr_t*)(usp + in_bytes(ContinuationEntry::parent_held_monitor_count_offset())), "parent held monitor count");
}
#endif

#ifdef ASSERT
bool ContinuationEntry::assert_entry_frame_laid_out(JavaThread* thread) {
  assert(thread->has_last_Java_frame(), "Wrong place to use this assertion");

  ContinuationEntry* entry = thread->last_continuation();
  assert(entry != nullptr, "");

  intptr_t* unextended_sp = entry->entry_sp();
  intptr_t* sp;
  if (entry->argsize() > 0) {
    sp = entry->bottom_sender_sp();
  } else {
    sp = unextended_sp;
    bool interpreted_bottom = false;
    RegisterMap map(thread,
                    RegisterMap::UpdateMap::skip,
                    RegisterMap::ProcessFrames::skip,
                    RegisterMap::WalkContinuation::skip);
    frame f;
    for (f = thread->last_frame();
         !f.is_first_frame() && f.sp() <= unextended_sp && !Continuation::is_continuation_enterSpecial(f);
         f = f.sender(&map)) {
      interpreted_bottom = f.is_interpreted_frame();
    }
    assert(Continuation::is_continuation_enterSpecial(f), "");
    sp = interpreted_bottom ? f.sp() : entry->bottom_sender_sp();
  }

  assert(sp != nullptr, "");
  assert(sp <= entry->entry_sp(), "");
  address pc = ContinuationHelper::return_address_at(
                 sp - frame::sender_sp_ret_address_offset());

  if (pc != StubRoutines::cont_returnBarrier()) {
    CodeBlob* cb = pc != nullptr ? CodeCache::find_blob(pc) : nullptr;
    assert(cb != nullptr, "sp: " INTPTR_FORMAT " pc: " INTPTR_FORMAT, p2i(sp), p2i(pc));
    assert(cb->as_compiled_method()->method()->is_continuation_enter_intrinsic(), "");
  }

  return true;
}
#endif // ASSERT
