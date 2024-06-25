/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_PPC_STACKCHUNKFRAMESTREAM_PPC_INLINE_HPP
#define CPU_PPC_STACKCHUNKFRAMESTREAM_PPC_INLINE_HPP

#include "interpreter/oopMapCache.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/registerMap.hpp"

#ifdef ASSERT
template <ChunkFrames frame_kind>
inline bool StackChunkFrameStream<frame_kind>::is_in_frame(void* p0) const {
  assert(!is_done(), "");
  assert(is_compiled(), "");
  intptr_t* p = (intptr_t*)p0;
  int argsize = (_cb->as_nmethod()->num_stack_arg_slots() * VMRegImpl::stack_slot_size) >> LogBytesPerWord;
  int frame_size = _cb->frame_size() + (argsize > 0 ? argsize + frame::metadata_words_at_top : 0);
  return (p - unextended_sp()) >= 0 && (p - unextended_sp()) < frame_size;
}
#endif

template <ChunkFrames frame_kind>
inline frame StackChunkFrameStream<frame_kind>::to_frame() const {
  if (is_done()) {
    return frame(_sp, _sp, nullptr, nullptr, nullptr, nullptr, true);
  } else {
    // Compiled frames on heap don't have back links. See FreezeBase::patch_pd() and frame::setup().
    return frame(sp(), unextended_sp(), Interpreter::contains(pc()) ? fp() : nullptr, pc(), cb(), _oopmap, true);
  }
}

template <ChunkFrames frame_kind>
inline address StackChunkFrameStream<frame_kind>::get_pc() const {
  assert(!is_done(), "");
  return (address)((frame::common_abi*) _sp)->lr;
}

template <ChunkFrames frame_kind>
inline intptr_t* StackChunkFrameStream<frame_kind>::fp() const {
  // See FreezeBase::patch_pd() and frame::setup()
  assert((frame_kind == ChunkFrames::Mixed && is_interpreted()), "");
  intptr_t* fp_addr = (intptr_t*)&((frame::common_abi*)_sp)->callers_sp;
  assert(*(intptr_t**)fp_addr != nullptr, "");
  // derelativize
  return fp_addr + *fp_addr;
}

template <ChunkFrames frame_kind>
inline intptr_t* StackChunkFrameStream<frame_kind>::derelativize(int offset) const {
  intptr_t* fp = this->fp();
  assert(fp != nullptr, "");
  return fp + fp[offset];
}

template <ChunkFrames frame_kind>
inline intptr_t* StackChunkFrameStream<frame_kind>::unextended_sp_for_interpreter_frame() const {
  assert_is_interpreted_and_frame_type_mixed();
  return derelativize(ijava_idx(esp)) + 1 - frame::metadata_words; // On PPC esp points to the next free slot
}

template <ChunkFrames frame_kind>
inline void StackChunkFrameStream<frame_kind>::next_for_interpreter_frame() {
  assert_is_interpreted_and_frame_type_mixed();
  if (derelativize(ijava_idx(locals)) + 1 >= _end) {
    _unextended_sp = _end;
    _sp = _end;
  } else {
    _unextended_sp = derelativize(ijava_idx(sender_sp));
    _sp = this->fp();
  }
}

// Details for the comment on StackChunkFrameStream<frame_kind>::frame_size()
//
// Interpreted caller frames get extended even if the callee is also
// interpreted. This is done to accomodate non-parameter locals.
//
// The size of a single frame is from the unextended sp to the bottom of the
// locals array. The combined size of caller/callee is the single size with the
// overlap deducted. The overlap is the size of the call parameters plus the
// size of the metadata at the sp (frame::metadata_words_at_top).
//
//
// Case 1: no metadata between a frame                      Case 2: metadata is located between
//         and its locals                                           a frame and its locals as on ppc64
//
//       |  | L0 aka P0            |                    |  | L0 aka P0            |
//       |  | :      :             |                    |  | :      :             |
//       |  | :      Pn            |                    |  | :      Pn            |
//       |  | :                    |                    |  | :                    |
//       |  | Lm                   |                    |  | Lm                   |
//       |  ========================                    |  |----------------------|
//    S0 |  | Frame F0             |                    |  | Metadata@top         |
//       |  |                      |                 S0 |  |                      |
//       |  |                      |                    |  |                      |
//       |  |----------------------|                    |  |                      |
//       || | L0 aka P0            |                    |  ========================
// over- || | :      :             |                    |  | Frame F0             |
// lap   || | :      Pn            |<- unext. SP        |  |                      |
//        | | :                    |                    |  |                      |<- bottom_of_locals
//        | | Lm                   |<- SP               |  |----------------------|
//        | ========================                    || | L0 aka P0            |
//        | | Frame F1             |                    || | :      :             |
//     S1 | |                      |              over- || | :      Pn            |<- unext. SP
//        | |                      |              lap   || | :                    |   + metadata_words_at_top
//        | |----------------------|                    || | Lm                   |
//        | | L0 aka P0            |                    || |----------------------|
//        | | :      :             |                    || | Metadata@top         |
//        | | :      Pn            |<- unext. SP        || |                      |<- unextended SP
//          | :                    |                     | |                      |
//          | Lm                   |<- SP                | |                      |<- SP
//          ========================                     | ========================
//                                                       | | Frame F1             |
//                                                       | |                      |
//                                                       | |                      |
//                                                       | |----------------------|
//    overlap = size of stackargs                     S1 | | L0 aka P0            |
//                                                       | | :      :             |
//                                                       | | :      Pn            |<- unext. SP
//                                                       | | :                    |   + metadata_words_at_top
//                                                       | | Lm                   |
//                                                       | |----------------------|
//                                                       | | Metadata@top         |
//                                                       | |                      |<- unextended SP
//                                                         |                      |
//                                                         |                      |<- SP
//                                                         ========================
//
//                                           sizeof(Metadata@top) = frame::metadata_words_at_top
//                                           bottom_of_locals = unext. sp + sizeof(Metadata@top) + stackargs
//                                           overlap = bottom_of_locals - unext. sp
//                                                   = stackargs + sizeof(Metadata@top)
template <ChunkFrames frame_kind>
inline int StackChunkFrameStream<frame_kind>::interpreter_frame_size() const {
  assert_is_interpreted_and_frame_type_mixed();
  intptr_t* top = unextended_sp(); // later subtract argsize if callee is interpreted
  intptr_t* bottom = derelativize(ijava_idx(locals)) + 1;
  return (int)(bottom - top);
}

// Size of stack args in words (P0..Pn above). Only valid if the caller is also
// interpreted. The function is also called if the caller is compiled but the
// result is not used in that case (same on x86).
// See also setting of sender_sp in ContinuationHelper::InterpretedFrame::patch_sender_sp()
template <ChunkFrames frame_kind>
inline int StackChunkFrameStream<frame_kind>::interpreter_frame_stack_argsize() const {
  assert_is_interpreted_and_frame_type_mixed();
  frame::ijava_state* state = (frame::ijava_state*)((uintptr_t)fp() - frame::ijava_state_size);
  int diff = (int)(state->locals - (state->sender_sp + frame::metadata_words_at_top) + 1);
  assert(diff == -frame::metadata_words_at_top || ((Method*)state->method)->size_of_parameters() == diff,
         "size_of_parameters(): %d diff: %d sp: " PTR_FORMAT " fp:" PTR_FORMAT,
         ((Method*)state->method)->size_of_parameters(), diff, p2i(sp()), p2i(fp()));
  return diff;
}

template <ChunkFrames frame_kind>
inline int StackChunkFrameStream<frame_kind>::interpreter_frame_num_oops() const {
  assert_is_interpreted_and_frame_type_mixed();
  ResourceMark rm;
  InterpreterOopMap mask;
  frame f = to_frame();
  f.interpreted_frame_oop_map(&mask);
  return  mask.num_oops()
          + 1 // for the mirror oop
          + ((intptr_t*)f.interpreter_frame_monitor_begin()
             - (intptr_t*)f.interpreter_frame_monitor_end())/BasicObjectLock::size();
}

template<>
template<>
inline void StackChunkFrameStream<ChunkFrames::Mixed>::update_reg_map_pd(RegisterMap* map) {
  // Nothing to do (no non-volatile registers in java calling convention)
}

template<>
template<>
inline void StackChunkFrameStream<ChunkFrames::CompiledOnly>::update_reg_map_pd(RegisterMap* map) {
  // Nothing to do (no non-volatile registers in java calling convention)
}

template <ChunkFrames frame_kind>
template <typename RegisterMapT>
inline void StackChunkFrameStream<frame_kind>::update_reg_map_pd(RegisterMapT* map) {}

#endif // CPU_PPC_STACKCHUNKFRAMESTREAM_PPC_INLINE_HPP
