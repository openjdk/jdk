/*
 * Copyright (c) 2008, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_ARM_FRAME_ARM_INLINE_HPP
#define CPU_ARM_FRAME_ARM_INLINE_HPP

#include "code/codeCache.hpp"
#include "code/vmreg.inline.hpp"

// Inline functions for ARM frames:

// Constructors:

inline frame::frame() {
  _pc = nullptr;
  _sp = nullptr;
  _unextended_sp = nullptr;
  _fp = nullptr;
  _cb = nullptr;
  _deopt_state = unknown;
  _on_heap = false;
  _oop_map = nullptr;
  DEBUG_ONLY(_frame_index = -1;)
}

inline frame::frame(intptr_t* sp) {
  Unimplemented();
}

inline void frame::init(intptr_t* sp, intptr_t* unextended_sp, intptr_t* fp, address pc) {
  _sp = sp;
  _unextended_sp = unextended_sp;
  _fp = fp;
  _pc = pc;
  assert(pc != nullptr, "no pc?");
  _cb = CodeCache::find_blob(pc);
  adjust_unextended_sp();
  DEBUG_ONLY(_frame_index = -1;)

  address original_pc = CompiledMethod::get_deopt_original_pc(this);
  if (original_pc != nullptr) {
    _pc = original_pc;
    assert(_cb->as_compiled_method()->insts_contains_inclusive(_pc),
           "original PC must be in the main code section of the the compiled method (or must be immediately following it)");
    _deopt_state = is_deoptimized;
  } else {
    _deopt_state = not_deoptimized;
  }
  _on_heap = false;
  _oop_map = nullptr;
}

inline frame::frame(intptr_t* sp, intptr_t* fp, address pc) {
  init(sp, sp, fp, pc);
}

inline frame::frame(intptr_t* sp, intptr_t* unextended_sp, intptr_t* fp, address pc) {
  init(sp, unextended_sp, fp, pc);
}

inline frame::frame(intptr_t* sp, intptr_t* fp) {
  assert(sp != nullptr, "null SP?");
  address pc = (address)(sp[-1]);
  init(sp, sp, fp, pc);
}


// Accessors

inline bool frame::equal(frame other) const {
  bool ret =  sp() == other.sp()
              && unextended_sp() == other.unextended_sp()
              && fp() == other.fp()
              && pc() == other.pc();
  assert(!ret || (cb() == other.cb() && _deopt_state == other._deopt_state), "inconsistent construction");
  return ret;
}

// Return unique id for this frame. The id must have a value where we can distinguish
// identity and younger/older relationship. null represents an invalid (incomparable)
// frame.
inline intptr_t* frame::id(void) const { return unextended_sp(); }

// Return true if the frame is older (less recent activation) than the frame represented by id
inline bool frame::is_older(intptr_t* id) const   { assert(this->id() != nullptr && id != nullptr, "null frame id");
                                                    return this->id() > id ; }

inline intptr_t* frame::link() const              { return (intptr_t*) *(intptr_t **)addr_at(link_offset); }

inline intptr_t* frame::link_or_null() const {
  intptr_t** ptr = (intptr_t **)addr_at(link_offset);
  return os::is_readable_pointer(ptr) ? *ptr : nullptr;
}

inline intptr_t* frame::unextended_sp() const     { return _unextended_sp; }

// Return address:

inline address* frame::sender_pc_addr()      const { return (address*) addr_at(return_addr_offset); }
inline address  frame::sender_pc()           const { return *sender_pc_addr(); }

inline intptr_t* frame::sender_sp() const { return addr_at(sender_sp_offset); }

inline intptr_t* frame::interpreter_frame_locals() const {
  intptr_t n = *addr_at(interpreter_frame_locals_offset);
  return &fp()[n]; // return relativized locals
}

inline intptr_t* frame::interpreter_frame_last_sp() const {
  return *(intptr_t**)addr_at(interpreter_frame_last_sp_offset);
}

inline intptr_t* frame::interpreter_frame_bcp_addr() const {
  return (intptr_t*)addr_at(interpreter_frame_bcp_offset);
}

inline intptr_t* frame::interpreter_frame_mdp_addr() const {
  return (intptr_t*)addr_at(interpreter_frame_mdp_offset);
}


// Constant pool cache

inline ConstantPoolCache** frame::interpreter_frame_cache_addr() const {
  return (ConstantPoolCache**)addr_at(interpreter_frame_cache_offset);
}

// Method

inline Method** frame::interpreter_frame_method_addr() const {
  return (Method**)addr_at(interpreter_frame_method_offset);
}

inline oop* frame::interpreter_frame_mirror_addr() const {
  return (oop*)addr_at(interpreter_frame_mirror_offset);
}

// top of expression stack
inline intptr_t* frame::interpreter_frame_tos_address() const {
  intptr_t* last_sp = interpreter_frame_last_sp();
  if (last_sp == nullptr ) {
    return sp();
  } else {
    // sp() may have been extended or shrunk by an adapter.  At least
    // check that we don't fall behind the legal region.
    // For top deoptimized frame last_sp == interpreter_frame_monitor_end.
    assert(last_sp <= (intptr_t*) interpreter_frame_monitor_end(), "bad tos");
    return last_sp;
  }
}

inline oop* frame::interpreter_frame_temp_oop_addr() const {
  return (oop *)(fp() + interpreter_frame_oop_temp_offset);
}

inline int frame::interpreter_frame_monitor_size() {
  return BasicObjectLock::size();
}


// expression stack
// (the max_stack arguments are used by the GC; see class FrameClosure)

inline intptr_t* frame::interpreter_frame_expression_stack() const {
  intptr_t* monitor_end = (intptr_t*) interpreter_frame_monitor_end();
  return monitor_end-1;
}


// Entry frames

inline JavaCallWrapper** frame::entry_frame_call_wrapper_addr() const {
 return (JavaCallWrapper**)addr_at(entry_frame_call_wrapper_offset);
}


// Compiled frames

// Register is a class, but it would be assigned numerical value.
// "0" is assigned for rax. Thus we need to ignore -Wnonnull.
PRAGMA_DIAG_PUSH
PRAGMA_NONNULL_IGNORED
inline oop frame::saved_oop_result(RegisterMap* map) const {
  oop* result_adr = (oop*) map->location(R0->as_VMReg(), nullptr);
  guarantee(result_adr != nullptr, "bad register save location");
  return (*result_adr);
}

inline void frame::set_saved_oop_result(RegisterMap* map, oop obj) {
  oop* result_adr = (oop*) map->location(R0->as_VMReg(), nullptr);
  guarantee(result_adr != nullptr, "bad register save location");
  *result_adr = obj;
}
PRAGMA_DIAG_POP

inline int frame::frame_size() const {
  return sender_sp() - sp();
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
  // Default is we done have to follow them. The sender_for_xxx will
  // update it accordingly
  map->set_include_argument_oops(false);

  if (is_entry_frame())       return sender_for_entry_frame(map);
  if (is_interpreted_frame()) return sender_for_interpreter_frame(map);
  assert(_cb == CodeCache::find_blob(pc()),"Must be the same");

  if (_cb != nullptr) return sender_for_compiled_frame(map);

  assert(false, "should not be called for a C frame");
  return frame();
}

inline frame frame::sender_for_compiled_frame(RegisterMap* map) const {
  assert(map != nullptr, "map must be set");

  // frame owned by optimizing compiler
  assert(_cb->frame_size() > 0, "must have non-zero frame size");
  intptr_t* sender_sp = unextended_sp() + _cb->frame_size();
  intptr_t* unextended_sp = sender_sp;

  address sender_pc = (address) *(sender_sp - sender_sp_offset + return_addr_offset);

  // This is the saved value of FP which may or may not really be an FP.
  // It is only an FP if the sender is an interpreter frame (or C1?).
  intptr_t** saved_fp_addr = (intptr_t**) (sender_sp - sender_sp_offset + link_offset);

  if (map->update_map()) {
    // Tell GC to use argument oopmaps for some runtime stubs that need it.
    // For C1, the runtime stub might not have oop maps, so set this flag
    // outside of update_register_map.
    map->set_include_argument_oops(_cb->caller_must_gc_arguments(map->thread()));
    if (_cb->oop_maps() != nullptr) {
      OopMapSet::update_register_map(this, map);
    }

    // Since the prolog does the save and restore of FP there is no oopmap
    // for it so we must fill in its location as if there was an oopmap entry
    // since if our caller was compiled code there could be live jvm state in it.
    update_map_with_saved_link(map, saved_fp_addr);
  }

  assert(sender_sp != sp(), "must have changed");
  return frame(sender_sp, unextended_sp, *saved_fp_addr, sender_pc);
}

template <typename RegisterMapT>
void frame::update_map_with_saved_link(RegisterMapT* map, intptr_t** link_addr) {
  Unimplemented();
}

#endif // CPU_ARM_FRAME_ARM_INLINE_HPP
