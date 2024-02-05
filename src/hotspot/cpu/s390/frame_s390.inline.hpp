/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2016 SAP SE. All rights reserved.
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

#ifndef CPU_S390_FRAME_S390_INLINE_HPP
#define CPU_S390_FRAME_S390_INLINE_HPP

#include "code/codeCache.hpp"
#include "code/vmreg.inline.hpp"
#include "runtime/sharedRuntime.hpp"
#include "utilities/align.hpp"

// Inline functions for z/Architecture frames:

// Initialize frame members (_sp must be given)
inline void frame::setup() {
  if (_pc == nullptr) {
    _pc = (address)own_abi()->return_pc;
    assert(_pc != nullptr, "must have PC");
  }

  if (_cb == nullptr) {
    _cb = CodeCache::find_blob(_pc);
  }

  if (_fp == nullptr) {
    _fp = (intptr_t*)own_abi()->callers_sp;
  }

  if (_unextended_sp == nullptr) {
    _unextended_sp = _sp;
  }

  // When thawing continuation frames the _unextended_sp passed to the constructor is not aligend
  assert(_on_heap || (is_aligned(_sp, alignment_in_bytes) && is_aligned(_fp, alignment_in_bytes)),
         "invalid alignment sp:" PTR_FORMAT " unextended_sp:" PTR_FORMAT " fp:" PTR_FORMAT, p2i(_sp), p2i(_unextended_sp), p2i(_fp));

  address original_pc = CompiledMethod::get_deopt_original_pc(this);
  if (original_pc != nullptr) {
    _pc = original_pc;
    _deopt_state = is_deoptimized;
    assert(_cb == nullptr || _cb->as_compiled_method()->insts_contains_inclusive(_pc),
           "original PC must be in the main code section of the compiled method (or must be immediately following it)");
  } else {
    if (_cb == SharedRuntime::deopt_blob()) {
      _deopt_state = is_deoptimized;
    } else {
      _deopt_state = not_deoptimized;
    }
  }

  // assert(_on_heap || is_aligned(_sp, frame::frame_alignment), "SP must be 8-byte aligned");
}

// Constructors

// Initialize all fields
inline frame::frame() : _sp(nullptr), _pc(nullptr), _cb(nullptr), _oop_map(nullptr), _deopt_state(unknown),
                        _on_heap(false), DEBUG_ONLY(_frame_index(-1) COMMA) _unextended_sp(nullptr), _fp(nullptr) {}

inline frame::frame(intptr_t* sp, address pc, intptr_t* unextended_sp, intptr_t* fp, CodeBlob* cb)
  : _sp(sp), _pc(pc), _cb(cb), _oop_map(nullptr),
    _on_heap(false), DEBUG_ONLY(_frame_index(-1) COMMA) _unextended_sp(unextended_sp), _fp(fp) {
  setup();
}

inline frame::frame(intptr_t* sp) : frame(sp, nullptr) {}

// Generic constructor. Used by pns() in debug.cpp only
#ifndef PRODUCT
inline frame::frame(void* sp, void* pc, void* unextended_sp)
  : _sp((intptr_t*)sp), _pc((address)pc), _cb(nullptr), _oop_map(nullptr),
    _on_heap(false), DEBUG_ONLY(_frame_index(-1) COMMA) _unextended_sp((intptr_t*)unextended_sp) {
  setup();
}
#endif

// template interpreter state
inline frame::z_ijava_state* frame::ijava_state_unchecked() const {
  z_ijava_state* state = (z_ijava_state*) ((uintptr_t)fp() - z_ijava_state_size);
  return state;
}

inline frame::z_ijava_state* frame::ijava_state() const {
  z_ijava_state* state = ijava_state_unchecked();
  assert(state->magic == (intptr_t) frame::z_istate_magic_number,
         "wrong z_ijava_state in interpreter frame (no magic found)");
  return state;
}

inline BasicObjectLock** frame::interpreter_frame_monitors_addr() const {
  return (BasicObjectLock**) &(ijava_state()->monitors);
}

// The next two functions read and write z_ijava_state.monitors.
inline BasicObjectLock* frame::interpreter_frame_monitors() const {
  return *interpreter_frame_monitors_addr();
}
inline void frame::interpreter_frame_set_monitors(BasicObjectLock* monitors) {
  *interpreter_frame_monitors_addr() = monitors;
}

// Accessors

// Return unique id for this frame. The id must have a value where we
// can distinguish identity and younger/older relationship. null
// represents an invalid (incomparable) frame.
inline intptr_t* frame::id(void) const {
  // Use _fp. _sp or _unextended_sp wouldn't be correct due to resizing.
  return _fp;
}

// Return true if this frame is older (less recent activation) than
// the frame represented by id.
inline bool frame::is_older(intptr_t* id) const {
  assert(this->id() != nullptr && id != nullptr, "null frame id");
  // Stack grows towards smaller addresses on z/Architecture.
  return this->id() > id;
}

inline int frame::frame_size() const {
  // Stack grows towards smaller addresses on z/Linux: sender is at a higher address.
  return sender_sp() - sp();
}

// Ignore c2i adapter frames.
inline intptr_t* frame::unextended_sp() const {
  return _unextended_sp;
}

inline address frame::sender_pc() const {
  return (address) callers_abi()->return_pc;
}

// Get caller pc, if caller is native from stack slot of gpr14.
inline address frame::native_sender_pc() const {
  return (address) callers_abi()->gpr14;
}

// Get caller pc from stack slot of gpr10.
inline address frame::callstub_sender_pc() const {
  return (address) callers_abi()->gpr10;
}

inline address* frame::sender_pc_addr() const {
  return (address*) &(callers_abi()->return_pc);
}

inline intptr_t* frame::sender_sp() const {
  return (intptr_t*) callers_abi();
}

inline intptr_t* frame::link() const {
  return (intptr_t*) callers_abi()->callers_sp;
}

inline intptr_t* frame::link_or_null() const {
  return link();
}

inline intptr_t* frame::interpreter_frame_locals() const {
  return (intptr_t*) (ijava_state()->locals);
}

inline intptr_t* frame::interpreter_frame_bcp_addr() const {
  return (intptr_t*) &(ijava_state()->bcp);
}

inline intptr_t* frame::interpreter_frame_mdp_addr() const {
  return (intptr_t*) &(ijava_state()->mdx);
}

// Bottom(base) of the expression stack (highest address).
inline intptr_t* frame::interpreter_frame_expression_stack() const {
  return (intptr_t*)interpreter_frame_monitor_end() - 1;
}

// monitor elements

// End is lower in memory than begin, and beginning element is oldest element.
// Also begin is one past last monitor.

inline intptr_t* frame::interpreter_frame_top_frame_sp() {
  return (intptr_t*)ijava_state()->top_frame_sp;
}

inline void frame::interpreter_frame_set_top_frame_sp(intptr_t* top_frame_sp) {
  ijava_state()->top_frame_sp = (intptr_t) top_frame_sp;
}

inline void frame::interpreter_frame_set_sender_sp(intptr_t* sender_sp) {
  ijava_state()->sender_sp = (intptr_t) sender_sp;
}

#ifdef ASSERT
inline void frame::interpreter_frame_set_magic() {
  ijava_state()->magic = (intptr_t) frame::z_istate_magic_number;
}
#endif

// Where z_ijava_state.esp is saved.
inline intptr_t** frame::interpreter_frame_esp_addr() const {
  return (intptr_t**) &(ijava_state()->esp);
}

// top of expression stack (lowest address)
inline intptr_t* frame::interpreter_frame_tos_address() const {
  return *interpreter_frame_esp_addr() + 1;
}

inline void frame::interpreter_frame_set_tos_address(intptr_t* x) {
  *interpreter_frame_esp_addr() = x - 1;
}

// Stack slot needed for native calls and GC.
inline oop * frame::interpreter_frame_temp_oop_addr() const {
  return (oop *) ((address) _fp + _z_ijava_state_neg(oop_tmp));
}

// In keeping with Intel side: end is lower in memory than begin.
// Beginning element is oldest element. Also begin is one past last monitor.
inline BasicObjectLock * frame::interpreter_frame_monitor_begin() const {
  return (BasicObjectLock*)ijava_state();
}

inline void frame::interpreter_frame_set_monitor_end(BasicObjectLock* monitors) {
  interpreter_frame_set_monitors((BasicObjectLock *)monitors);
}

inline int frame::interpreter_frame_monitor_size() {
  return BasicObjectLock::size();
}

inline Method** frame::interpreter_frame_method_addr() const {
  return (Method**)&(ijava_state()->method);
}

inline oop* frame::interpreter_frame_mirror_addr() const {
  return (oop*)&(ijava_state()->mirror);
}

// Constant pool cache

inline ConstantPoolCache** frame::interpreter_frame_cache_addr() const {
  return (ConstantPoolCache**)&(ijava_state()->cpoolCache);
}

// entry frames

inline intptr_t* frame::entry_frame_argument_at(int offset) const {
  // Since an entry frame always calls the interpreter first,
  // the parameters are on the stack and relative to known register in the
  // entry frame.
  intptr_t* tos = (intptr_t*) entry_frame_locals()->arguments_tos_address;
  return &tos[offset + 1]; // prepushed tos
}

inline JavaCallWrapper** frame::entry_frame_call_wrapper_addr() const {
  return (JavaCallWrapper**) &entry_frame_locals()->call_wrapper_address;
}

inline oop frame::saved_oop_result(RegisterMap* map) const {
  return *((oop*) map->location(Z_R2->as_VMReg(), nullptr));  // R2 is return register.
}

inline void frame::set_saved_oop_result(RegisterMap* map, oop obj) {
  *((oop*) map->location(Z_R2->as_VMReg(), nullptr)) = obj;  // R2 is return register.
}

inline intptr_t* frame::real_fp() const {
  return fp();
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

//------------------------------------------------------------------------------
// frame::sender

inline frame frame::sender(RegisterMap* map) const {
  // Default is we don't have to follow them. The sender_for_xxx will
  // update it accordingly.
  map->set_include_argument_oops(false);

  if (is_entry_frame())       return sender_for_entry_frame(map);
  if (is_upcall_stub_frame()) return sender_for_upcall_stub_frame(map);
  if (is_interpreted_frame()) return sender_for_interpreter_frame(map);

  assert(_cb == CodeCache::find_blob(pc()),"Must be the same");
  if (_cb != nullptr) return sender_for_compiled_frame(map);

  // Must be native-compiled frame, i.e. the marshaling code for native
  // methods that exists in the core system.
  return frame(sender_sp(), sender_pc());
}

inline frame frame::sender_for_compiled_frame(RegisterMap *map) const {
  assert(map != nullptr, "map must be set");

  intptr_t* sender_sp = this->sender_sp();
  address   sender_pc = this->sender_pc();

  // Now adjust the map.
  if (map->update_map()) {
    // Tell GC to use argument oopmaps for some runtime stubs that need it.
    map->set_include_argument_oops(_cb->caller_must_gc_arguments(map->thread()));
    if (_cb->oop_maps() != nullptr) {
      OopMapSet::update_register_map(this, map);
    }
  }

  return frame(sender_sp, sender_pc);
}

template <typename RegisterMapT>
void frame::update_map_with_saved_link(RegisterMapT* map, intptr_t** link_addr) {
  Unimplemented();
}

#endif // CPU_S390_FRAME_S390_INLINE_HPP
