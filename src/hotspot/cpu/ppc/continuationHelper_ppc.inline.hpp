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

#ifndef CPU_PPC_CONTINUATIONHELPER_PPC_INLINE_HPP
#define CPU_PPC_CONTINUATIONHELPER_PPC_INLINE_HPP

#include "runtime/continuationHelper.hpp"

template<typename FKind>
static inline intptr_t** link_address(const frame& f) {
  Unimplemented();
  return nullptr;
}

inline int ContinuationHelper::frame_align_words(int size) {
  return size & 1;
}

inline intptr_t* ContinuationHelper::frame_align_pointer(intptr_t* p) {
  return align_down(p, frame::frame_alignment);
}

template<typename FKind>
inline void ContinuationHelper::update_register_map(const frame& f, RegisterMap* map) {
  // Currently all registers are considered to be volatile and saved in the caller (java) frame if needed
}

inline void ContinuationHelper::update_register_map_with_callee(const frame& f, RegisterMap* map) {
  // Currently all registers are considered to be volatile and saved in the caller (java) frame if needed
}

inline void ContinuationHelper::push_pd(const frame& f) {
  f.own_abi()->callers_sp = (uint64_t)f.fp();
}


inline void ContinuationHelper::set_anchor_to_entry_pd(JavaFrameAnchor* anchor, ContinuationEntry* cont) {
  // nothing to do
}

#ifdef ASSERT
inline void ContinuationHelper::set_anchor_pd(JavaFrameAnchor* anchor, intptr_t* sp) {
  // nothing to do
}

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
  return (address*)&f.callers_abi()->lr;
}

inline void ContinuationHelper::InterpretedFrame::patch_sender_sp(frame& f, const frame& caller) {
  intptr_t* sp = caller.unextended_sp();
  if (!f.is_heap_frame() && caller.is_interpreted_frame()) {
    // See diagram "Interpreter Calling Procedure on PPC" at the end of continuationFreezeThaw_ppc.inline.hpp
    sp = (intptr_t*)caller.at_relative(ijava_idx(top_frame_sp));
  }
  assert(f.is_interpreted_frame(), "");
  assert(f.is_heap_frame() || is_aligned(sp, frame::alignment_in_bytes), "");
  intptr_t* la = f.addr_at(ijava_idx(sender_sp));
  *la = f.is_heap_frame() ? (intptr_t)(sp - f.fp()) : (intptr_t)sp;
}

inline address* ContinuationHelper::Frame::return_pc_address(const frame& f) {
  return (address*)&f.callers_abi()->lr;
}

inline address ContinuationHelper::Frame::real_pc(const frame& f) {
  return (address)f.own_abi()->lr;
}

inline void ContinuationHelper::Frame::patch_pc(const frame& f, address pc) {
  f.own_abi()->lr = (uint64_t)pc;
}

//                     | Minimal ABI          |
//                     | (frame::java_abi)    |
//                     | 4 words              |
//                     | Caller's SP          |<- FP of f's caller
//                     |======================|
//                     |                      |                                 Frame of f's caller
//                     |                      |
// frame_bottom of f ->|                      |
//                     |----------------------|
//                     | L0 aka P0            |
//                     | :                    |
//                     | :      Pn            |
//                     | :                    |
//                     | Lm                   |
//                     |----------------------|
//                     | SP alignment (opt.)  |
//                     |----------------------|
//                     | Minimal ABI          |
//                     | (frame::java_abi)    |
//                     | 4 words              |
//                     | Caller's SP          |<- SP of f's caller / FP of f
//                     |======================|
//                     |ijava_state (metadata)|                                 Frame of f
//                     |                      |
//                     |                      |
//                     |----------------------|
//                     | Expression stack     |
//                     |                      |
//    frame_top of f ->|                      |
//   if callee interp. |......................|
//                     | L0 aka P0            |<- ijava_state.esp + callee_argsize
//                     | :                    |
//    frame_top of f ->| :      Pn            |
//  + metadata_words   | :                    |<- ijava_state.esp (1 slot below Pn)
//    if callee comp.  | Lm                   |
//                     |----------------------|
//                     | SP alignment (opt.)  |
//                     |----------------------|
//                     | Minimal ABI          |
//                     | (frame::java_abi)    |
//                     | 4 words              |
//                     | Caller's SP          |<- SP of f / FP of f's callee
//                     |======================|
//                     |ijava_state (metadata)|                                 Frame of f's callee
//                     |                      |
//
//                           |  Growth  |
//                           v          v
//
// See also diagram at the end of continuation_ppc.inline.hpp
//
inline intptr_t* ContinuationHelper::InterpretedFrame::frame_top(const frame& f, InterpreterOopMap* mask) { // inclusive; this will be copied with the frame
  int expression_stack_sz = expression_stack_size(f, mask);
  intptr_t* res = (intptr_t*)f.interpreter_frame_monitor_end() - expression_stack_sz;
  assert(res <= (intptr_t*)f.get_ijava_state() - expression_stack_sz,
         "res=" PTR_FORMAT " f.get_ijava_state()=" PTR_FORMAT " expression_stack_sz=%d",
         p2i(res), p2i(f.get_ijava_state()), expression_stack_sz);
  assert(res >= f.unextended_sp(),
         "res: " INTPTR_FORMAT " ijava_state: " INTPTR_FORMAT " esp: " INTPTR_FORMAT " unextended_sp: " INTPTR_FORMAT " expression_stack_size: %d",
         p2i(res), p2i(f.get_ijava_state()), f.get_ijava_state()->esp, p2i(f.unextended_sp()), expression_stack_sz);
  return res;
}

inline intptr_t* ContinuationHelper::InterpretedFrame::frame_bottom(const frame& f) {
  return (intptr_t*)f.at_relative(ijava_idx(locals)) + 1; // exclusive (will not be copied), so we add 1 word
}

inline intptr_t* ContinuationHelper::InterpretedFrame::frame_top(const frame& f, int callee_argsize_incl_metadata, bool callee_interpreted) {
  intptr_t* pseudo_unextended_sp = f.interpreter_frame_esp() + 1 - frame::metadata_words_at_top;
  return pseudo_unextended_sp + (callee_interpreted ? callee_argsize_incl_metadata : 0);
}

inline intptr_t* ContinuationHelper::InterpretedFrame::callers_sp(const frame& f) {
  return f.fp();
}

#endif // CPU_PPC_CONTINUATIONFRAMEHELPERS_PPC_INLINE_HPP
