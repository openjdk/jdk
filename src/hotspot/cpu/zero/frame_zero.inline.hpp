/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2007, 2008, 2009, 2010 Red Hat, Inc.
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

#ifndef CPU_ZERO_FRAME_ZERO_INLINE_HPP
#define CPU_ZERO_FRAME_ZERO_INLINE_HPP

#include "code/codeCache.hpp"

// Constructors

inline frame::frame() {
  _zeroframe = nullptr;
  _sp = nullptr;
  _pc = nullptr;
  _cb = nullptr;
  _deopt_state = unknown;
  _on_heap = false;
  DEBUG_ONLY(_frame_index = -1;)
}

inline address  frame::sender_pc()           const { ShouldNotCallThis(); return nullptr; }

inline frame::frame(intptr_t* sp) {
  Unimplemented();
}

inline frame::frame(ZeroFrame* zf, intptr_t* sp) {
  _zeroframe = zf;
  _sp = sp;
  _on_heap = false;
  DEBUG_ONLY(_frame_index = -1;)
  switch (zeroframe()->type()) {
  case ZeroFrame::ENTRY_FRAME:
    _pc = StubRoutines::call_stub_return_pc();
    _cb = nullptr;
    _deopt_state = not_deoptimized;
    break;

  case ZeroFrame::INTERPRETER_FRAME:
    _pc = nullptr;
    _cb = nullptr;
    _deopt_state = not_deoptimized;
    break;

  case ZeroFrame::FAKE_STUB_FRAME:
    _pc = nullptr;
    _cb = nullptr;
    _deopt_state = not_deoptimized;
    break;

  default:
    ShouldNotReachHere();
  }
}

// Accessors

inline intptr_t* frame::sender_sp() const {
  return fp() + 1;
}

inline intptr_t* frame::real_fp() const {
  return fp();
}

inline intptr_t* frame::link() const {
  ShouldNotCallThis();
  return nullptr;
}

inline intptr_t* frame::link_or_null() const {
  ShouldNotCallThis();
  return nullptr;
}

inline interpreterState frame::get_interpreterState() const {
  return zero_interpreterframe()->interpreter_state();
}

inline intptr_t* frame::interpreter_frame_locals() const {
  return get_interpreterState()->_locals;
}

inline intptr_t* frame::interpreter_frame_bcp_addr() const {
  return (intptr_t*) &(get_interpreterState()->_bcp);
}

inline ConstantPoolCache** frame::interpreter_frame_cache_addr() const {
  return &(get_interpreterState()->_constants);
}

inline Method** frame::interpreter_frame_method_addr() const {
  return &(get_interpreterState()->_method);
}

inline oop* frame::interpreter_frame_mirror_addr() const {
  return &(get_interpreterState()->_mirror);
}

inline intptr_t* frame::interpreter_frame_mdp_addr() const {
  fatal("Should not call this: Zero never profiles");
  return nullptr; // silence compiler warnings
}

inline intptr_t* frame::interpreter_frame_tos_address() const {
  return get_interpreterState()->_stack + 1;
}

inline oop* frame::interpreter_frame_temp_oop_addr() const {
  interpreterState istate = get_interpreterState();
  return (oop *)&istate->_oop_temp;
}

inline int frame::interpreter_frame_monitor_size() {
  return BasicObjectLock::size();
}

inline intptr_t* frame::interpreter_frame_expression_stack() const {
  intptr_t* monitor_end = (intptr_t*) interpreter_frame_monitor_end();
  return monitor_end - 1;
}

// Return a unique id for this frame. The id must have a value where
// we can distinguish identity and younger/older relationship. null
// represents an invalid (incomparable) frame.
inline intptr_t* frame::id() const {
  return fp();
}

inline JavaCallWrapper** frame::entry_frame_call_wrapper_addr() const {
  return zero_entryframe()->call_wrapper();
}

inline void frame::set_saved_oop_result(RegisterMap* map, oop obj) {
  ShouldNotCallThis();
}

inline oop frame::saved_oop_result(RegisterMap* map) const {
  ShouldNotCallThis();
  return nullptr;
}

inline bool frame::is_older(intptr_t* id) const {
  ShouldNotCallThis();
  return false;
}

inline intptr_t* frame::entry_frame_argument_at(int offset) const {
  ShouldNotCallThis();
  return nullptr;
}

inline intptr_t* frame::unextended_sp() const {
  return (intptr_t *) -1;
}

inline int frame::compiled_frame_stack_argsize() const {
  Unimplemented();
  return 0;
}

inline void frame::interpreted_frame_oop_map(InterpreterOopMap* mask) const {
  Unimplemented();
}

inline int frame::sender_sp_ret_address_offset() {
  Unimplemented();
  return 0;
}

inline void frame::set_unextended_sp(intptr_t* value) {
  Unimplemented();
}

inline int frame::offset_unextended_sp() const {
  Unimplemented();
  return 0;
}

inline void frame::set_offset_unextended_sp(int value) {
  Unimplemented();
}

inline int frame::frame_size() const {
#ifdef PRODUCT
  ShouldNotCallThis();
#endif // PRODUCT
  return 0; // make javaVFrame::print_value work
}

inline address* frame::sender_pc_addr() const {
  ShouldNotCallThis();
  return nullptr;
}

//------------------------------------------------------------------------------
// frame::sender

inline frame frame::sender(RegisterMap* map) const {
  // Default is not to follow arguments; the various
  // sender_for_xxx methods update this accordingly.
  map->set_include_argument_oops(false);

  frame result = zeroframe()->is_entry_frame() ?
                 sender_for_entry_frame(map) :
                 sender_for_nonentry_frame(map);

  if (map->process_frames()) {
    StackWatermarkSet::on_iteration(map->thread(), result);
  }

  return result;
}

template <typename RegisterMapT>
void frame::update_map_with_saved_link(RegisterMapT* map, intptr_t** link_addr) {
  Unimplemented();
}

#endif // CPU_ZERO_FRAME_ZERO_INLINE_HPP
