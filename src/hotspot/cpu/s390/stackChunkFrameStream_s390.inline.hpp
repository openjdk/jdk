/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_S390_STACKCHUNKFRAMESTREAM_S390_INLINE_HPP
#define CPU_S390_STACKCHUNKFRAMESTREAM_S390_INLINE_HPP

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
    // Compiled frames on heap don't have back links on s390. The back link is redundant
    // and gets computed as unextended_sp + frame_size. In debug builds, FreezeBase::patch_pd()
    // explicitly sets it to badAddress.
    return frame(sp(), unextended_sp(), Interpreter::contains(pc()) ? fp() : nullptr, pc(), cb(), _oopmap, true);
  }
}

template <ChunkFrames frame_kind>
inline address StackChunkFrameStream<frame_kind>::get_pc() const {
  assert(!is_done(), "");
  return (address)((frame::z_common_abi*) _sp)->return_pc;
}

template <ChunkFrames frame_kind>
inline intptr_t* StackChunkFrameStream<frame_kind>::fp() const {
  // See FreezeBase::patch_pd() and frame::setup()
  assert((frame_kind == ChunkFrames::Mixed && is_interpreted()), "");
  intptr_t* fp_addr = (intptr_t*)&((frame::z_common_abi*)_sp)->callers_sp;
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
  // Compute the unextended SP (stack pointer before any extension for arguments).
  // On s390, esp points to the next free slot above the operand stack, so we add 1
  // to get the actual top of the operand stack, then subtract metadata_words to
  // account for the frame metadata (callers_sp and return_pc) at the top of the frame.
  return derelativize(_z_ijava_idx(esp)) + 1 - frame::metadata_words;
}

template <ChunkFrames frame_kind>
inline void StackChunkFrameStream<frame_kind>::next_for_interpreter_frame() {
  assert_is_interpreted_and_frame_type_mixed();
  if (derelativize(_z_ijava_idx(locals)) + 1 >= _end) {
    _unextended_sp = _end;
    _sp = _end;
  } else {
    _unextended_sp = derelativize(_z_ijava_idx(sender_sp));
    _sp = this->fp();
  }
}

template <ChunkFrames frame_kind>
inline int StackChunkFrameStream<frame_kind>::interpreter_frame_size() const {
  assert_is_interpreted_and_frame_type_mixed();
  intptr_t* top = unextended_sp(); // later subtract argsize if callee is interpreted
  intptr_t* bottom = derelativize(_z_ijava_idx(locals)) + 1;
  return (int)(bottom - top);
}

// Size of stack args in words (P0..Pn above). Only valid if the caller is also
// interpreted. The function is also called if the caller is compiled but the
// result is not used in that case (same on x86).
// See also setting of sender_sp in ContinuationHelper::InterpretedFrame::patch_sender_sp()
template <ChunkFrames frame_kind>
inline int StackChunkFrameStream<frame_kind>::interpreter_frame_stack_argsize() const {
  assert_is_interpreted_and_frame_type_mixed();
  frame::z_ijava_state* state = (frame::z_ijava_state*)((uintptr_t)fp() - frame::z_ijava_state_size);
  int diff = (int)(state->locals - (state->sender_sp + frame::metadata_words_at_top) + 1);
  assert(diff == -frame::metadata_words_at_top || ((Method*)state->method)->size_of_parameters() == diff,
      "size_of_parameters(): %d diff: %d sp: " PTR_FORMAT " fp:" PTR_FORMAT,
      ((Method*)state->method)->size_of_parameters(), diff, p2i(sp()), p2i(fp()));
  return diff;
}

template <ChunkFrames frame_kind>
template <typename RegisterMapT>
inline int StackChunkFrameStream<frame_kind>::interpreter_frame_num_oops(RegisterMapT* map) const {
  assert_is_interpreted_and_frame_type_mixed();
  ResourceMark rm;
  frame f = to_frame();
  InterpreterOopCount closure;
  f.oops_interpreted_do(&closure, map);
  return closure.count();
}

template<>
template<>
inline void StackChunkFrameStream<ChunkFrames::Mixed>::update_reg_map_pd(RegisterMap* map) {
  // No register map update needed for s390.
  // In the Java calling convention on s390, all registers are volatile (caller-saved),
  // so there are no non-volatile (callee-saved) registers that need to be tracked.
}

template<>
template<>
inline void StackChunkFrameStream<ChunkFrames::CompiledOnly>::update_reg_map_pd(RegisterMap* map) {
  // No register map update needed for s390.
  // In the Java calling convention on s390, all registers are volatile (caller-saved),
  // so there are no non-volatile (callee-saved) registers that need to be tracked.
}

template <ChunkFrames frame_kind>
template <typename RegisterMapT>
inline void StackChunkFrameStream<frame_kind>::update_reg_map_pd(RegisterMapT* map) {}

#endif // CPU_S390_STACKCHUNKFRAMESTREAM_S390_INLINE_HPP
