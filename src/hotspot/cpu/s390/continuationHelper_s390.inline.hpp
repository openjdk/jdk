/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

#ifndef CPU_S390_CONTINUATIONHELPER_S390_INLINE_HPP
#define CPU_S390_CONTINUATIONHELPER_S390_INLINE_HPP

#include "runtime/continuationHelper.hpp"

static inline void patch_return_pc_with_preempt_stub(frame& f) {
  if (f.is_runtime_frame()) {
    // Patch the pc of the now old last Java frame (we already set the anchor to enterSpecial)
    // so that when target returns to Java it will actually return to the preempt cleanup stub.
    // We step over the runtime stub frame and patch the return PC in the caller's frame.
    intptr_t* caller_sp = f.sp() + f.cb()->frame_size();
    frame::z_common_abi* abi = (frame::z_common_abi*)caller_sp;
    abi->return_pc = (uint64_t)StubRoutines::cont_preempt_stub();
  } else {
    // The target will check for preemption once it returns to the interpreter
    // or the native wrapper code and will manually jump to the preempt stub.
    JavaThread *thread = JavaThread::current();
    DEBUG_ONLY(Method* m = f.is_interpreted_frame() ? f.interpreter_frame_method() : f.cb()->as_nmethod()->method();)
    assert(m->is_object_wait0() || thread->interp_at_preemptable_vmcall_cnt() > 0,
           "preemptable VM call not using call_VM_preemptable");
    thread->set_preempt_alternate_return(StubRoutines::cont_preempt_stub());
  }
}

inline int ContinuationHelper::frame_align_words(int size) {
  // S390 requires 8-byte (1-word) frame alignment, not 16-byte like other platforms.
  // Because frames are already 8-byte aligned, no additional padding words are needed.
  // Other platforms (x86, aarch64, ppc) return size & 1 to ensure 16-byte alignment,
  // but s390's 8-byte alignment requirement is already satisfied.
  return 0;
}

inline intptr_t* ContinuationHelper::frame_align_pointer(intptr_t* p) {
  return align_down(p, frame::frame_alignment);
}

template<typename FKind>
inline void ContinuationHelper::update_register_map(const frame& f, RegisterMap* map) {
  // All registers are considered volatile and saved in the caller (Java) frame if needed.
  // No register map update required for s390.
}

inline void ContinuationHelper::update_register_map_with_callee(const frame& f, RegisterMap* map) {
  // All registers are considered volatile and saved in the caller (Java) frame if needed.
  // No register map update required for s390.
}

inline void ContinuationHelper::push_pd(const frame& f) {
  f.own_abi()->callers_sp = (uint64_t)f.fp();
}

inline void ContinuationHelper::set_anchor_to_entry_pd(JavaFrameAnchor* anchor, ContinuationEntry* cont) {
  // No frame pointer update needed for s390.
  // Unlike x86/aarch64, s390 doesn't require setting last_Java_fp in the anchor.
}

inline void ContinuationHelper::set_anchor_pd(JavaFrameAnchor* anchor, intptr_t* sp) {
  // No frame pointer update needed for s390.
  // Unlike x86/aarch64, s390 doesn't require setting last_Java_fp in the anchor.
}

#ifdef ASSERT
inline bool ContinuationHelper::Frame::assert_frame_laid_out(frame f) {
  intptr_t* sp = f.sp();
  address pc = *(address*)(sp - frame::sender_sp_ret_address_offset());
  intptr_t* fp = (intptr_t*)f.own_abi()->callers_sp;
  assert(f.raw_pc() == pc, "f.ra_pc: " INTPTR_FORMAT " actual: " INTPTR_FORMAT, p2i(f.raw_pc()), p2i(pc));
  assert(f.fp() == fp, "f.fp: " INTPTR_FORMAT " actual: " INTPTR_FORMAT, p2i(f.fp()), p2i(fp));
  return f.raw_pc() == pc && f.fp() == fp;
}
#endif

inline intptr_t** ContinuationHelper::Frame::callee_link_address(const frame& f) {
  return (intptr_t**)&f.own_abi()->callers_sp;
}

inline address* ContinuationHelper::InterpretedFrame::return_pc_address(const frame& f) {
  return (address*)&f.callers_abi()->return_pc;
}

inline void ContinuationHelper::InterpretedFrame::patch_sender_sp(frame& f, const frame& caller) {
  intptr_t* sp = caller.unextended_sp();
  if (!f.is_heap_frame() && caller.is_interpreted_frame()) {
    // When the caller is an interpreted frame, we need to use the caller's top_frame_sp
    // instead of unextended_sp. This is because the interpreter resizes the caller's
    // frame before making a call
    sp = (intptr_t*)caller.at_relative(_z_ijava_idx(top_frame_sp));
  }
  assert(f.is_interpreted_frame(), "");
  assert(f.is_heap_frame() || is_aligned(sp, frame::alignment_in_bytes), "");
  intptr_t* la = f.addr_at(_z_ijava_idx(sender_sp));
  *la = f.is_heap_frame() ? (intptr_t)(sp - f.fp()) : (intptr_t)sp;
}

inline address* ContinuationHelper::Frame::return_pc_address(const frame& f) {
  return (address*)&f.callers_abi()->return_pc;
}

inline address ContinuationHelper::Frame::real_pc(const frame& f) {
  return (address)f.own_abi()->return_pc;
}

inline void ContinuationHelper::Frame::patch_pc(const frame& f, address pc) {
  f.own_abi()->return_pc = (uint64_t)pc;
}

inline intptr_t* ContinuationHelper::InterpretedFrame::frame_top(const frame& f, InterpreterOopMap* mask) { // inclusive; this will be copied with the frame
  int expression_stack_sz = expression_stack_size(f, mask);
  intptr_t* res = (intptr_t*)f.interpreter_frame_monitor_end() - expression_stack_sz;
  assert(res <= (intptr_t*)f.ijava_state() - expression_stack_sz,
      "res=" PTR_FORMAT " f.ijava_state()=" PTR_FORMAT " expression_stack_sz=%d",
      p2i(res), p2i(f.ijava_state()), expression_stack_sz);
  assert(res >= f.unextended_sp(),
      "res: " INTPTR_FORMAT " ijava_state: " INTPTR_FORMAT " esp: " INTPTR_FORMAT " unextended_sp: " INTPTR_FORMAT " expression_stack_size: %d",
      p2i(res), p2i(f.ijava_state()), f.ijava_state()->esp, p2i(f.unextended_sp()), expression_stack_sz);
  return res;
}

inline intptr_t* ContinuationHelper::InterpretedFrame::frame_bottom(const frame& f) { // exclusive; this will not be copied with the frame
  return (intptr_t*)f.at_relative(_z_ijava_idx(locals)) + 1; // exclusive; this will not be copied with the frame
}

inline intptr_t* ContinuationHelper::InterpretedFrame::frame_top(const frame& f, int callee_argsize, bool callee_interpreted) {
  intptr_t* pseudo_unextended_sp = f.interpreter_frame_esp() + 1 - frame::metadata_words_at_top;
  // callee_argsize includes metadata (frame::metadata_words_at_top).
  // When the callee is interpreted, we add callee_argsize to account for the arguments
  // that are part of the caller's frame but logically belong to the callee.
  return pseudo_unextended_sp + (callee_interpreted ? callee_argsize : 0);
}

inline intptr_t* ContinuationHelper::InterpretedFrame::callers_sp(const frame& f) {
  return f.fp();
}

#endif // CPU_S390_CONTINUATIONHELPER_S390_INLINE_HPP
