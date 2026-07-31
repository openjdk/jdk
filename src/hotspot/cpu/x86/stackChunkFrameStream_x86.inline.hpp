/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_X86_STACKCHUNKFRAMESTREAM_X86_INLINE_HPP
#define CPU_X86_STACKCHUNKFRAMESTREAM_X86_INLINE_HPP

#include "interpreter/oopMapCache.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/registerMap.hpp"

#ifdef ASSERT
template <ChunkFrames frame_kind>
inline bool StackChunkFrameStream<frame_kind>::is_in_frame(void* p0) const {
  assert(!is_done(), "");
  intptr_t* p = (intptr_t*)p0;
  int frame_size = _cb->frame_size();
  if (is_compiled()) {
    nmethod* nm = _cb->as_nmethod_or_null();
    if (nm->needs_stack_repair() && nm->is_compiled_by_c2()) {
      frame f = to_frame();
      bool augmented = f.was_augmented_on_entry(frame_size);
      if (!augmented) {
        // Fix: C2 caller, so frame was not extended and thus the
        // size read from the frame does not include the arguments.
        // Ideally we have to count the arg size for the scalarized
        // convention. For now we include the size of the caller frame
        // which would at least be equal to that.
        RegisterMap map(nullptr,
                        RegisterMap::UpdateMap::skip,
                        RegisterMap::ProcessFrames::skip,
                        RegisterMap::WalkContinuation::skip);
        frame caller = to_frame().sender(&map);
        assert(caller.is_compiled_frame() && caller.cb()->as_nmethod()->is_compiled_by_c2(), "needs stack repair but was not extended with c1/interpreter caller");
        frame_size += (caller.real_fp() - caller.sp());
      }
    } else {
      frame_size += _cb->as_nmethod()->num_stack_arg_slots() * VMRegImpl::stack_slot_size >> LogBytesPerWord;
    }
  }
  return p == sp() - frame::sender_sp_offset || ((p - unextended_sp()) >= 0 && (p - unextended_sp()) < frame_size);
}
#endif

template <ChunkFrames frame_kind>
inline frame StackChunkFrameStream<frame_kind>::to_frame() const {
  if (is_done()) {
    return frame(_sp, _sp, nullptr, nullptr, nullptr, nullptr, true);
  } else {
    return frame(sp(), unextended_sp(), fp(), pc(), cb(), _oopmap, true);
  }
}

template <ChunkFrames frame_kind>
inline address StackChunkFrameStream<frame_kind>::get_pc() const {
  assert(!is_done(), "");
  return *(address*)(_sp - 1);
}

template <ChunkFrames frame_kind>
inline intptr_t* StackChunkFrameStream<frame_kind>::fp() const {
  intptr_t* fp_addr = _sp - frame::sender_sp_offset;
  return (frame_kind == ChunkFrames::Mixed && is_interpreted())
    ? fp_addr + *fp_addr // derelativize
    : *(intptr_t**)fp_addr;
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
  return derelativize(frame::interpreter_frame_last_sp_offset);
}

template <ChunkFrames frame_kind>
inline void StackChunkFrameStream<frame_kind>::next_for_interpreter_frame() {
  assert_is_interpreted_and_frame_type_mixed();
  if (derelativize(frame::interpreter_frame_locals_offset) + 1 >= _end) {
    _unextended_sp = _end;
    _sp = _end;
  } else {
    intptr_t* fp = this->fp();
    _unextended_sp = fp + fp[frame::interpreter_frame_sender_sp_offset];
    _sp = fp + frame::sender_sp_offset;
  }
}

template <ChunkFrames frame_kind>
inline int StackChunkFrameStream<frame_kind>::interpreter_frame_size() const {
  assert_is_interpreted_and_frame_type_mixed();

  intptr_t* top = unextended_sp(); // later subtract argsize if callee is interpreted
  intptr_t* bottom = derelativize(frame::interpreter_frame_locals_offset) + 1; // the sender's unextended sp: derelativize(frame::interpreter_frame_sender_sp_offset);
  return (int)(bottom - top);
}

template <ChunkFrames frame_kind>
inline int StackChunkFrameStream<frame_kind>::interpreter_frame_stack_argsize() const {
  assert_is_interpreted_and_frame_type_mixed();
  int diff = (int)(derelativize(frame::interpreter_frame_locals_offset) - derelativize(frame::interpreter_frame_sender_sp_offset) + 1);
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
  if (map->update_map()) {
    frame::update_map_with_saved_link(map, map->in_cont() ? (intptr_t**)(intptr_t)frame::sender_sp_offset
                                                          : (intptr_t**)(_sp - frame::sender_sp_offset));
  }
}

template<>
template<>
inline void StackChunkFrameStream<ChunkFrames::CompiledOnly>::update_reg_map_pd(RegisterMap* map) {
  if (map->update_map()) {
    frame::update_map_with_saved_link(map, map->in_cont() ? (intptr_t**)(intptr_t)frame::sender_sp_offset
                                                          : (intptr_t**)(_sp - frame::sender_sp_offset));
  }
}

template <ChunkFrames frame_kind>
template <typename RegisterMapT>
inline void StackChunkFrameStream<frame_kind>::update_reg_map_pd(RegisterMapT* map) {}

#endif // CPU_X86_STACKCHUNKFRAMESTREAM_X86_INLINE_HPP
