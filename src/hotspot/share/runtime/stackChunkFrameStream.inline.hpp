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

#ifndef SHARE_OOPS_STACKCHUNKFRAMESTREAM_INLINE_HPP
#define SHARE_OOPS_STACKCHUNKFRAMESTREAM_INLINE_HPP

#include "runtime/stackChunkFrameStream.hpp"

#include "code/codeCache.inline.hpp"
#include "compiler/oopMap.hpp"
#include "interpreter/interpreter.hpp"
#include "logging/log.hpp"
#include "oops/method.hpp"
#include "oops/oop.hpp"
#include "oops/stackChunkOop.inline.hpp"
#include "oops/instanceStackChunkKlass.inline.hpp"
#include "runtime/frame.inline.hpp"
#include "utilities/debug.hpp"
#include "utilities/devirtualizer.inline.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"
#include CPU_HEADER_INLINE(stackChunkFrameStream)

#ifdef ASSERT
extern "C" bool dbg_is_safe(const void* p, intptr_t errvalue);
#endif

template <ChunkFrames frame_kind>
StackChunkFrameStream<frame_kind>::StackChunkFrameStream(stackChunkOop chunk) DEBUG_ONLY(: _chunk(chunk)) {
  assert(chunk->is_stackChunk_noinline(), "");
  assert(frame_kind == ChunkFrames::Mixed || !chunk->has_mixed_frames(), "");

  DEBUG_ONLY(_index = 0;)
  _end = chunk->bottom_address();
  _sp = chunk->start_address() + chunk->sp();
  assert(_sp <= chunk->end_address() + frame::metadata_words, "");

  get_cb();

  if (frame_kind == ChunkFrames::Mixed) {
    _unextended_sp = (!is_done() && is_interpreted()) ? unextended_sp_for_interpreter_frame() : _sp;
    assert(_unextended_sp >= _sp - frame::metadata_words, "");
  }
  DEBUG_ONLY(else _unextended_sp = nullptr;)

  if (is_stub()) {
    get_oopmap(pc(), 0);
    DEBUG_ONLY(_has_stub = true);
  } DEBUG_ONLY(else _has_stub = false;)
}

template <ChunkFrames frame_kind>
StackChunkFrameStream<frame_kind>::StackChunkFrameStream(stackChunkOop chunk, const frame& f)
  DEBUG_ONLY(: _chunk(chunk)) {
  assert(chunk->is_stackChunk_noinline(), "");
  assert(frame_kind == ChunkFrames::Mixed || !chunk->has_mixed_frames(), "");
  // assert(!is_empty(), ""); -- allowed to be empty

  DEBUG_ONLY(_index = 0;)

  _end = chunk->bottom_address();

  assert(chunk->is_in_chunk(f.sp()), "");
  _sp = f.sp();
  if (frame_kind == ChunkFrames::Mixed) {
    _unextended_sp = f.unextended_sp();
    assert(_unextended_sp >= _sp - frame::metadata_words, "");
  }
  DEBUG_ONLY(else _unextended_sp = nullptr;)
  assert(_sp >= chunk->start_address(), "");
  assert(_sp <= chunk->end_address() + frame::metadata_words, "");

  if (f.cb() != nullptr) {
    _oopmap = nullptr;
    _cb = f.cb();
  } else {
    get_cb();
  }

  if (is_stub()) {
    get_oopmap(pc(), 0);
    DEBUG_ONLY(_has_stub = true);
  } DEBUG_ONLY(else _has_stub = false;)
}

template <ChunkFrames frame_kind>
inline bool StackChunkFrameStream<frame_kind>::is_stub() const {
  return cb() != nullptr && (_cb->is_safepoint_stub() || _cb->is_runtime_stub());
}

template <ChunkFrames frame_kind>
inline bool StackChunkFrameStream<frame_kind>::is_compiled() const {
  return cb() != nullptr && _cb->is_nmethod();
}

template <>
inline bool StackChunkFrameStream<ChunkFrames::Mixed>::is_interpreted() const {
  return !is_done() && Interpreter::contains(pc());
}

template <>
inline bool StackChunkFrameStream<ChunkFrames::CompiledOnly>::is_interpreted() const {
  return false;
}

// StackChunkFrameStream<frame_kind>::frame_size() returns the words required to
// store the given frame as the only frame in a StackChunk. This is the size of the
// frame itself plus its stack arguments plus metadata at the caller's frame top (1)
//
// |====================|          ---
// | F0's stackargs     |           ^
// |                    |           |
// |--------------------|           |
// | metadata@top       | <- caller's sp
// |====================|           |
// | metadata@bottom(2) |           |
// |--------------------|
// |                    |       size S0
// | Frame F0           |                 ---     |====================|          ---
// |                    |           |      ^      | F1's stackargs     |           ^
// |                    |           |      |      |                    |           |
// |--------------------|           |   overlap   |--------------------|           |
// | metadata@top(1)    |<- sp      v      v      | metadata@top       | <- caller's sp
// |====================|          ---    ---     |====================|           |
//                                                | metadata@bottom    |           |
//           |                                    |--------------------|
//           |                                    | Frame F1           |       size S1
//      Stack Growth                              | (F0's callee)      |
//           |                                    |                    |           |
//           |                                    |                    |           |
//           v                                    |--------------------|           |
//                                                | metadata@top       |<- sp      v
//                                                |====================|          ---
//
// 2 frames of the same kind (interpreted or compiled) overlap. So the total
// size required in the StackChunk is S0 + S1 - overlap, where the overlap is
// the size of F1's stackargs plus frame::metadata_words_at_top.
//
// The callers of frame_size() are supposed to deduct the overlap.  The bottom
// frame in the StackChunk obviously does not overlap with it's caller, as it is
// in the parent chunk.
//
// There is no overlap if caller/callee are of different kinds. In that case the
// caller is extended to accomodate the callee's stack arguments. The extension
// is not counted though in the caller's size, so there is indeed no overlap.
//
// See ppc implementation of StackChunkFrameStream<frame_kind>::interpreter_frame_size()
// for more details.
//
// (1) Metadata at frame top (see frame::metadata_words_at_top)
//     Part of the overlap. Used on ppc64, empty on x86_64, aarch64
// (2) Metadata at the frame bottom (see frame::metadata_words_at_bottom)
//     Not part of the overlap.
//     Used on x86_64 (saved rbp, ret. addr.), aarch64. Empty on ppc64.
//
template <ChunkFrames frame_kind>
inline int StackChunkFrameStream<frame_kind>::frame_size() const {
  return is_interpreted() ? interpreter_frame_size()
                          : cb()->frame_size() + stack_argsize() + frame::metadata_words_at_top;
}

template <ChunkFrames frame_kind>
inline int StackChunkFrameStream<frame_kind>::stack_argsize() const {
  if (is_interpreted()) {
    return interpreter_frame_stack_argsize();
  }
  if (is_stub()) {
    return 0;
  }
  assert(cb() != nullptr, "");
  assert(cb()->is_nmethod(), "");
  assert(cb()->as_nmethod()->method() != nullptr, "");
  return (cb()->as_nmethod()->num_stack_arg_slots() * VMRegImpl::stack_slot_size) >> LogBytesPerWord;
}

template <ChunkFrames frame_kind>
inline int StackChunkFrameStream<frame_kind>::num_oops() const {
  return is_interpreted() ? interpreter_frame_num_oops() : oopmap()->num_oops();
}

template <ChunkFrames frame_kind>
inline void StackChunkFrameStream<frame_kind>::initialize_register_map(RegisterMap* map) {
  update_reg_map_pd(map);
}

template <ChunkFrames frame_kind>
template <typename RegisterMapT>
inline void StackChunkFrameStream<frame_kind>::next(RegisterMapT* map, bool stop) {
  update_reg_map(map);
  bool safepoint = is_stub();
  if (frame_kind == ChunkFrames::Mixed) {
    if (is_interpreted()) {
      next_for_interpreter_frame();
    } else {
      _sp = _unextended_sp + cb()->frame_size();
      if (_sp >= _end - frame::metadata_words) {
        _sp = _end;
      }
      _unextended_sp = is_interpreted() ? unextended_sp_for_interpreter_frame() : _sp;
    }
    assert(_unextended_sp >= _sp - frame::metadata_words, "");
  } else {
    _sp += cb()->frame_size();
  }
  assert(!is_interpreted() || _unextended_sp == unextended_sp_for_interpreter_frame(), "");

  DEBUG_ONLY(_index++;)
  if (stop) {
    return;
  }

  get_cb();
  update_reg_map_pd(map);
  if (safepoint && cb() != nullptr) { // there's no post-call nop and no fast oopmap lookup
    _oopmap = cb()->oop_map_for_return_address(pc());
  }
}

template <ChunkFrames frame_kind>
inline void StackChunkFrameStream<frame_kind>::get_cb() {
  _oopmap = nullptr;
  if (is_done() || is_interpreted()) {
    _cb = nullptr;
    return;
  }

  assert(pc() != nullptr, "");
  assert(dbg_is_safe(pc(), -1), "");

  _cb = CodeCache::find_blob_fast(pc());

  assert(_cb != nullptr, "");
  assert(is_interpreted() || ((is_stub() || is_compiled()) && _cb->frame_size() > 0), "");
}

template <ChunkFrames frame_kind>
inline void StackChunkFrameStream<frame_kind>::get_oopmap() const {
  if (is_interpreted()) {
    return;
  }
  assert(is_compiled(), "");
  get_oopmap(pc(), CodeCache::find_oopmap_slot_fast(pc()));
}

template <ChunkFrames frame_kind>
inline void StackChunkFrameStream<frame_kind>::get_oopmap(address pc, int oopmap_slot) const {
  assert(cb() != nullptr, "");
  assert(!is_compiled() || !cb()->as_nmethod()->is_deopt_pc(pc), "");
  if (oopmap_slot >= 0) {
    assert(oopmap_slot >= 0, "");
    assert(cb()->oop_map_for_slot(oopmap_slot, pc) != nullptr, "");
    assert(cb()->oop_map_for_slot(oopmap_slot, pc) == cb()->oop_map_for_return_address(pc), "");

    _oopmap = cb()->oop_map_for_slot(oopmap_slot, pc);
  } else {
    _oopmap = cb()->oop_map_for_return_address(pc);
  }
  assert(_oopmap != nullptr, "");
}

template <ChunkFrames frame_kind>
template <typename RegisterMapT>
inline void* StackChunkFrameStream<frame_kind>::reg_to_loc(VMReg reg, const RegisterMapT* map) const {
  assert(!is_done(), "");
  return reg->is_reg() ? (void*)map->location(reg, sp()) // see frame::update_map_with_saved_link(&map, link_addr);
                       : (void*)((address)unextended_sp() + (reg->reg2stack() * VMRegImpl::stack_slot_size));
}

template<>
template<>
inline void StackChunkFrameStream<ChunkFrames::Mixed>::update_reg_map(RegisterMap* map) {
  assert(!map->in_cont() || map->stack_chunk() == _chunk, "");
  if (map->update_map() && is_stub()) {
    frame f = to_frame();
    oopmap()->update_register_map(&f, map); // we have callee-save registers in this case
  }
}

template<>
template<>
inline void StackChunkFrameStream<ChunkFrames::CompiledOnly>::update_reg_map(RegisterMap* map) {
  assert(map->in_cont(), "");
  assert(map->stack_chunk()() == _chunk, "");
  if (map->update_map()) {
    frame f = to_frame();
    oopmap()->update_register_map(&f, map); // we have callee-save registers in this case
  }
}

template <ChunkFrames frame_kind>
template <typename RegisterMapT>
inline void StackChunkFrameStream<frame_kind>::update_reg_map(RegisterMapT* map) {}

template <ChunkFrames frame_kind>
inline address StackChunkFrameStream<frame_kind>::orig_pc() const {
  address pc1 = pc();
  if (is_interpreted() || is_stub()) {
    return pc1;
  }
  nmethod* nm = cb()->as_nmethod();
  if (nm->is_deopt_pc(pc1)) {
    pc1 = *(address*)((address)unextended_sp() + nm->orig_pc_offset());
  }

  assert(pc1 != nullptr, "");
  assert(!nm->is_deopt_pc(pc1), "");
  assert(_cb == CodeCache::find_blob_fast(pc1), "");

  return pc1;
}

template<ChunkFrames frame_kind>
void StackChunkFrameStream<frame_kind>::handle_deopted() const {
  assert(!is_done(), "");

  if (_oopmap != nullptr) {
    return;
  }
  if (is_interpreted()) {
    return;
  }
  assert(is_compiled(), "");

  address pc1 = pc();
  int oopmap_slot = CodeCache::find_oopmap_slot_fast(pc1);
  if (oopmap_slot < 0) { // UNLIKELY; we could have marked frames for deoptimization in thaw_chunk
    if (cb()->as_nmethod()->is_deopt_pc(pc1)) {
      pc1 = orig_pc();
      oopmap_slot = CodeCache::find_oopmap_slot_fast(pc1);
    }
  }
  get_oopmap(pc1, oopmap_slot);
}

template <ChunkFrames frame_kind>
template <class OopClosureType, class RegisterMapT>
inline void StackChunkFrameStream<frame_kind>::iterate_oops(OopClosureType* closure, const RegisterMapT* map) const {
  if (is_interpreted()) {
    frame f = to_frame();
    f.oops_interpreted_do(closure, nullptr, true);
  } else {
    DEBUG_ONLY(int oops = 0;)
    for (OopMapStream oms(oopmap()); !oms.is_done(); oms.next()) {
      OopMapValue omv = oms.current();
      if (omv.type() != OopMapValue::oop_value && omv.type() != OopMapValue::narrowoop_value) {
        continue;
      }

      assert(UseCompressedOops || omv.type() == OopMapValue::oop_value, "");
      DEBUG_ONLY(oops++;)

      void* p = reg_to_loc(omv.reg(), map);
      assert(p != nullptr, "");
      assert((_has_stub && _index == 1) || is_in_frame(p), "");

      log_develop_trace(continuations)("StackChunkFrameStream::iterate_oops narrow: %d reg: %s p: " INTPTR_FORMAT " sp offset: " INTPTR_FORMAT,
          omv.type() == OopMapValue::narrowoop_value, omv.reg()->name(), p2i(p), (intptr_t*)p - sp());
          omv.type() == OopMapValue::narrowoop_value ? Devirtualizer::do_oop(closure, (narrowOop*)p) : Devirtualizer::do_oop(closure, (oop*)p);
    }
    assert(oops == oopmap()->num_oops(), "oops: %d oopmap->num_oops(): %d", oops, oopmap()->num_oops());
  }
}

template <ChunkFrames frame_kind>
template <class DerivedOopClosureType, class RegisterMapT>
inline void StackChunkFrameStream<frame_kind>::iterate_derived_pointers(DerivedOopClosureType* closure, const RegisterMapT* map) const {
  if (!is_compiled()) {
    // Only compiled frames have derived pointers
    return;
  }

  assert(oopmap()->has_derived_oops() == oopmap()->has_any(OopMapValue::derived_oop_value), "");
  if (!oopmap()->has_derived_oops()) {
    return;
  }

  for (OopMapStream oms(oopmap()); !oms.is_done(); oms.next()) {
    OopMapValue omv = oms.current();
    if (omv.type() != OopMapValue::derived_oop_value) {
      continue;
    }

    // see OopMapDo<OopMapFnT, DerivedOopFnT, ValueFilterT>::walk_derived_pointers1
    intptr_t* derived_loc = (intptr_t*)reg_to_loc(omv.reg(), map);
    intptr_t* base_loc    = (intptr_t*)reg_to_loc(omv.content_reg(), map);

    assert((_has_stub && _index == 1) || is_in_frame(base_loc), "");
    assert((_has_stub && _index == 1) || is_in_frame(derived_loc), "");
    assert(derived_loc != base_loc, "Base and derived in same location");
    assert(is_in_oops(base_loc, map), "not found: " INTPTR_FORMAT, p2i(base_loc));
    assert(!is_in_oops(derived_loc, map), "found: " INTPTR_FORMAT, p2i(derived_loc));

    Devirtualizer::do_derived_oop(closure, (derived_base*)base_loc, (derived_pointer*)derived_loc);
  }
}

#ifdef ASSERT

template <ChunkFrames frame_kind>
template <typename RegisterMapT>
bool StackChunkFrameStream<frame_kind>::is_in_oops(void* p, const RegisterMapT* map) const {
  for (OopMapStream oms(oopmap()); !oms.is_done(); oms.next()) {
    if (oms.current().type() != OopMapValue::oop_value) {
      continue;
    }
    if (reg_to_loc(oms.current().reg(), map) == p) {
      return true;
    }
  }
  return false;
}

template <ChunkFrames frame_kind>
void StackChunkFrameStream<frame_kind>::assert_is_interpreted_and_frame_type_mixed() const {
  assert(is_interpreted(), "");
  assert(frame_kind == ChunkFrames::Mixed, "");
}

#endif

#endif // SHARE_OOPS_STACKCHUNKFRAMESTREAM_INLINE_HPP
