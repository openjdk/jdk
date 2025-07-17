/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
  Unimplemented();
  return true;
}
#endif

template <ChunkFrames frame_kind>
inline frame StackChunkFrameStream<frame_kind>::to_frame() const {
  Unimplemented();
  return frame();
}

template <ChunkFrames frame_kind>
inline address StackChunkFrameStream<frame_kind>::get_pc() const {
  Unimplemented();
  return nullptr;
}

template <ChunkFrames frame_kind>
inline intptr_t* StackChunkFrameStream<frame_kind>::fp() const {
  Unimplemented();
  return nullptr;
}

template <ChunkFrames frame_kind>
inline intptr_t* StackChunkFrameStream<frame_kind>::derelativize(int offset) const {
  Unimplemented();
  return nullptr;
}

template <ChunkFrames frame_kind>
inline intptr_t* StackChunkFrameStream<frame_kind>::unextended_sp_for_interpreter_frame() const {
  Unimplemented();
  return nullptr;
}

template <ChunkFrames frame_kind>
inline void StackChunkFrameStream<frame_kind>::next_for_interpreter_frame() {
  Unimplemented();
}

template <ChunkFrames frame_kind>
inline int StackChunkFrameStream<frame_kind>::interpreter_frame_size() const {
  Unimplemented();
  return 0;
}

template <ChunkFrames frame_kind>
inline int StackChunkFrameStream<frame_kind>::interpreter_frame_stack_argsize() const {
  Unimplemented();
  return 0;
}

template <ChunkFrames frame_kind>
inline int StackChunkFrameStream<frame_kind>::interpreter_frame_num_oops() const {
  Unimplemented();
  return 0;
}

template<>
template<>
inline void StackChunkFrameStream<ChunkFrames::Mixed>::update_reg_map_pd(RegisterMap* map) {
  Unimplemented();
}

template<>
template<>
inline void StackChunkFrameStream<ChunkFrames::CompiledOnly>::update_reg_map_pd(RegisterMap* map) {
  Unimplemented();
}

template <ChunkFrames frame_kind>
template <typename RegisterMapT>
inline void StackChunkFrameStream<frame_kind>::update_reg_map_pd(RegisterMapT* map) {}

#endif // CPU_S390_STACKCHUNKFRAMESTREAM_S390_INLINE_HPP
