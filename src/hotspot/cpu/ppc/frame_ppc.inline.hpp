/*
 * Copyright (c) 2000, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2012, 2015 SAP SE. All rights reserved.
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

#ifndef CPU_PPC_FRAME_PPC_INLINE_HPP
#define CPU_PPC_FRAME_PPC_INLINE_HPP

#include "code/codeCache.hpp"
#include "code/vmreg.inline.hpp"
#include "runtime/sharedRuntime.hpp"
#include "utilities/align.hpp"

// Inline functions for ppc64 frames:

// Initialize frame members (_sp must be given)
inline void frame::setup() {
  if (_pc == nullptr) {
    _pc = (address)own_abi()->lr;
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
           "original PC must be in the main code section of the the compiled method (or must be immediately following it)");
  } else {
    if (_cb == SharedRuntime::deopt_blob()) {
      _deopt_state = is_deoptimized;
    } else {
      _deopt_state = not_deoptimized;
    }
  }

  assert(_on_heap || is_aligned(_sp, frame::frame_alignment), "SP must be 16-byte aligned");
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

// Accessors

// Return unique id for this frame. The id must have a value where we
// can distinguish identity and younger/older relationship. NULL
// represents an invalid (incomparable) frame.
inline intptr_t* frame::id(void) const {
  // Use _fp. _sp or _unextended_sp wouldn't be correct due to resizing.
  return _fp;
}

// Return true if this frame is older (less recent activation) than
// the frame represented by id.
inline bool frame::is_older(intptr_t* id) const {
   assert(this->id() != NULL && id != NULL, "NULL frame id");
   // Stack grows towards smaller addresses on ppc64.
   return this->id() > id;
}

inline int frame::frame_size() const {
  // Stack grows towards smaller addresses on PPC64: sender is at a higher address.
  return sender_sp() - sp();
}

// Return the frame's stack pointer before it has been extended by a
// c2i adapter. This is needed by deoptimization for ignoring c2i adapter
// frames.
inline intptr_t* frame::unextended_sp() const {
  return _unextended_sp;
}

// All frames have this field.
inline address frame::sender_pc() const {
  return (address)callers_abi()->lr;
}
inline address* frame::sender_pc_addr() const {
  return (address*)&(callers_abi()->lr);
}

// All frames have this field.
inline intptr_t* frame::sender_sp() const {
  return (intptr_t*)callers_abi();
}

// All frames have this field.
inline intptr_t* frame::link() const {
  return (intptr_t*)callers_abi()->callers_sp;
}

inline intptr_t* frame::link_or_null() const {
  return link();
}

inline intptr_t* frame::real_fp() const {
  return fp();
}

// Template Interpreter frame value accessors.

inline frame::ijava_state* frame::get_ijava_state() const {
  return (ijava_state*) ((uintptr_t)fp() - ijava_state_size);
}

inline intptr_t** frame::interpreter_frame_locals_addr() const {
  return (intptr_t**) &(get_ijava_state()->locals);
}

inline intptr_t* frame::interpreter_frame_bcp_addr() const {
  return (intptr_t*) &(get_ijava_state()->bcp);
}

inline intptr_t* frame::interpreter_frame_mdp_addr() const {
  return (intptr_t*) &(get_ijava_state()->mdx);
}

inline BasicObjectLock* frame::interpreter_frame_monitor_begin() const {
  return (BasicObjectLock*) get_ijava_state();
}

// Return register stack slot addr at which currently interpreted method is found.
inline Method** frame::interpreter_frame_method_addr() const {
  return (Method**) &(get_ijava_state()->method);
}

inline oop* frame::interpreter_frame_mirror_addr() const {
  return (oop*) &(get_ijava_state()->mirror);
}

inline ConstantPoolCache** frame::interpreter_frame_cache_addr() const {
  return (ConstantPoolCache**) &(get_ijava_state()->cpoolCache);
}

inline oop* frame::interpreter_frame_temp_oop_addr() const {
  return (oop*) &(get_ijava_state()->oop_tmp);
}

inline intptr_t* frame::interpreter_frame_esp() const {
  return (intptr_t*) get_ijava_state()->esp;
}

// Convenient setters
inline void frame::interpreter_frame_set_monitor_end(BasicObjectLock* end)    { get_ijava_state()->monitors = (intptr_t) end;}
inline void frame::interpreter_frame_set_cpcache(ConstantPoolCache* cp)       { *interpreter_frame_cache_addr() = cp; }
inline void frame::interpreter_frame_set_esp(intptr_t* esp)                   { get_ijava_state()->esp = (intptr_t) esp; }
inline void frame::interpreter_frame_set_top_frame_sp(intptr_t* top_frame_sp) { get_ijava_state()->top_frame_sp = (intptr_t) top_frame_sp; }
inline void frame::interpreter_frame_set_sender_sp(intptr_t* sender_sp)       { get_ijava_state()->sender_sp = (intptr_t) sender_sp; }

inline intptr_t* frame::interpreter_frame_expression_stack() const {
  return (intptr_t*)interpreter_frame_monitor_end() - 1;
}

// top of expression stack
inline intptr_t* frame::interpreter_frame_tos_address() const {
  return ((intptr_t*) get_ijava_state()->esp) + Interpreter::stackElementWords;
}

inline int frame::interpreter_frame_monitor_size() {
  // Number of stack slots for a monitor.
  return align_up(BasicObjectLock::size(),  // number of stack slots
                  WordsPerLong);            // number of stack slots for a Java long
}

inline int frame::interpreter_frame_monitor_size_in_bytes() {
  return frame::interpreter_frame_monitor_size() * wordSize;
}

// entry frames

inline intptr_t* frame::entry_frame_argument_at(int offset) const {
  // Since an entry frame always calls the interpreter first, the
  // parameters are on the stack and relative to known register in the
  // entry frame.
  intptr_t* tos = (intptr_t*)get_entry_frame_locals()->arguments_tos_address;
  return &tos[offset + 1]; // prepushed tos
}

inline JavaCallWrapper** frame::entry_frame_call_wrapper_addr() const {
  return (JavaCallWrapper**)&get_entry_frame_locals()->call_wrapper_address;
}

inline oop frame::saved_oop_result(RegisterMap* map) const {
  return *((oop*)map->location(R3->as_VMReg(), nullptr));
}

inline void frame::set_saved_oop_result(RegisterMap* map, oop obj) {
  *((oop*)map->location(R3->as_VMReg(), nullptr)) = obj;
}

inline const ImmutableOopMap* frame::get_oop_map() const {
  if (_cb == NULL) return NULL;
  if (_cb->oop_maps() != NULL) {
    NativePostCallNop* nop = nativePostCallNop_at(_pc);
    if (nop != NULL && nop->displacement() != 0) {
      int slot = ((nop->displacement() >> 24) & 0xff);
      return _cb->oop_map_for_slot(slot, _pc);
    }
    const ImmutableOopMap* oop_map = OopMapSet::find_map(this);
    return oop_map;
  }
  return NULL;
}

inline int frame::compiled_frame_stack_argsize() const {
  Unimplemented();
  return 0;
}

inline void frame::interpreted_frame_oop_map(InterpreterOopMap* mask) const {
  Unimplemented();
}

inline intptr_t* frame::interpreter_frame_last_sp() const {
  Unimplemented();
  return NULL;
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

frame frame::sender(RegisterMap* map) const {
  frame result = sender_raw(map);

  if (map->process_frames()) {
    StackWatermarkSet::on_iteration(map->thread(), result);
  }

  return result;
}

inline frame frame::sender_raw(RegisterMap* map) const {
  // Default is we do have to follow them. The sender_for_xxx will
  // update it accordingly.
  map->set_include_argument_oops(false);

  if (is_entry_frame())       return sender_for_entry_frame(map);
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

#endif // CPU_PPC_FRAME_PPC_INLINE_HPP
