/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_RUNTIME_FRAME_INLINE_HPP
#define SHARE_RUNTIME_FRAME_INLINE_HPP

#include "runtime/frame.hpp"

#include "code/codeBlob.inline.hpp"
#include "code/nmethod.inline.hpp"
#include "interpreter/interpreter.hpp"
#include "oops/stackChunkOop.inline.hpp"
#include "oops/method.hpp"
#include "runtime/continuation.hpp"
#include "runtime/registerMap.hpp"
#include "runtime/stubRoutines.hpp"
#include "utilities/macros.hpp"
#ifdef ZERO
# include "entryFrame_zero.hpp"
# include "fakeStubFrame_zero.hpp"
# include "interpreterFrame_zero.hpp"
#endif

#include CPU_HEADER_INLINE(frame)

inline bool frame::is_entry_frame() const {
  return StubRoutines::returns_to_call_stub(pc());
}

inline bool frame::is_stub_frame() const {
  return StubRoutines::is_stub_code(pc()) || (_cb != nullptr && _cb->is_adapter_blob());
}

inline bool frame::is_first_frame() const {
  return (is_entry_frame() && entry_frame_is_first())
      // Upcall stub frames entry frames are only present on certain platforms
      || (is_upcall_stub_frame() && upcall_stub_frame_is_first());
}

inline bool frame::is_upcall_stub_frame() const {
  return _cb != nullptr && _cb->is_upcall_stub();
}

inline bool frame::is_compiled_frame() const {
  if (_cb != nullptr &&
      _cb->is_nmethod() &&
      _cb->as_nmethod()->is_java_method()) {
    return true;
  }
  return false;
}

inline address frame::get_deopt_original_pc() const {
  if (_cb == nullptr)  return nullptr;

  nmethod* nm = _cb->as_nmethod_or_null();
  if (nm != nullptr && nm->is_deopt_pc(_pc)) {
    return nm->get_original_pc(this);
  }
  return nullptr;
}

template <typename RegisterMapT>
inline address frame::oopmapreg_to_location(VMReg reg, const RegisterMapT* reg_map) const {
  if (reg->is_reg()) {
    // If it is passed in a register, it got spilled in the stub frame.
    return reg_map->location(reg, sp());
  } else {
    int sp_offset_in_bytes = reg->reg2stack() * VMRegImpl::stack_slot_size;
    if (reg_map->in_cont()) {
      return (address)((intptr_t)reg_map->as_RegisterMap()->stack_chunk()->relativize_usp_offset(*this, sp_offset_in_bytes));
    }
    address usp = (address)unextended_sp();
    assert(reg_map->thread() == nullptr || reg_map->thread()->is_in_usable_stack(usp), INTPTR_FORMAT, p2i(usp));
    return (usp + sp_offset_in_bytes);
  }
}

template <typename RegisterMapT>
inline oop* frame::oopmapreg_to_oop_location(VMReg reg, const RegisterMapT* reg_map) const {
  return (oop*)oopmapreg_to_location(reg, reg_map);
}

inline CodeBlob* frame::get_cb() const {
  // if (_cb == nullptr) _cb = CodeCache::find_blob(_pc);
  if (_cb == nullptr) {
    int slot;
    _cb = CodeCache::find_blob_and_oopmap(_pc, slot);
    if (_oop_map == nullptr && slot >= 0) {
      _oop_map = _cb->oop_map_for_slot(slot, _pc);
    }
  }
  return _cb;
}

inline const ImmutableOopMap* frame::get_oop_map() const {
  if (_cb == nullptr || _cb->oop_maps() == nullptr) return nullptr;

  NativePostCallNop* nop = nativePostCallNop_at(_pc);
  int oopmap_slot;
  int cb_offset;
  if (nop != nullptr && nop->decode(oopmap_slot, cb_offset)) {
    return _cb->oop_map_for_slot(oopmap_slot, _pc);
  }
  const ImmutableOopMap* oop_map = OopMapSet::find_map(this);
  return oop_map;
}

inline int frame::interpreter_frame_monitor_size_in_bytes() {
  // Number of bytes for a monitor.
  return frame::interpreter_frame_monitor_size() * wordSize;
}

#endif // SHARE_RUNTIME_FRAME_INLINE_HPP
