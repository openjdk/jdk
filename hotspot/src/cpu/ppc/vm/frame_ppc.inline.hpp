/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2012, 2013 SAP AG. All rights reserved.
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

#ifndef CPU_PPC_VM_FRAME_PPC_INLINE_HPP
#define CPU_PPC_VM_FRAME_PPC_INLINE_HPP

#ifndef CC_INTERP
#error "CC_INTERP must be defined on PPC64"
#endif

// Inline functions for ppc64 frames:

// Find codeblob and set deopt_state.
inline void frame::find_codeblob_and_set_pc_and_deopt_state(address pc) {
  assert(pc != NULL, "precondition: must have PC");

  _cb = CodeCache::find_blob(pc);
  _pc = pc;   // Must be set for get_deopt_original_pc()

  _fp = (intptr_t*)own_abi()->callers_sp;
  // Use _fp - frame_size, needs to be done between _cb and _pc initialization
  // and get_deopt_original_pc.
  adjust_unextended_sp();

  address original_pc = nmethod::get_deopt_original_pc(this);
  if (original_pc != NULL) {
    _pc = original_pc;
    _deopt_state = is_deoptimized;
  } else {
    _deopt_state = not_deoptimized;
  }

  assert(((uint64_t)_sp & 0xf) == 0, "SP must be 16-byte aligned");
}

// Constructors

// Initialize all fields, _unextended_sp will be adjusted in find_codeblob_and_set_pc_and_deopt_state.
inline frame::frame() : _sp(NULL), _unextended_sp(NULL), _fp(NULL), _cb(NULL), _pc(NULL), _deopt_state(unknown) {}

inline frame::frame(intptr_t* sp) : _sp(sp), _unextended_sp(sp) {
  find_codeblob_and_set_pc_and_deopt_state((address)own_abi()->lr); // also sets _fp and adjusts _unextended_sp
}

inline frame::frame(intptr_t* sp, address pc) : _sp(sp), _unextended_sp(sp) {
  find_codeblob_and_set_pc_and_deopt_state(pc); // also sets _fp and adjusts _unextended_sp
}

inline frame::frame(intptr_t* sp, address pc, intptr_t* unextended_sp) : _sp(sp), _unextended_sp(unextended_sp) {
  find_codeblob_and_set_pc_and_deopt_state(pc); // also sets _fp and adjusts _unextended_sp
}

// Accessors

// Return unique id for this frame. The id must have a value where we
// can distinguish identity and younger/older relationship. NULL
// represents an invalid (incomparable) frame.
inline intptr_t* frame::id(void) const {
  // Use the _unextended_pc as the frame's ID. Because we have no
  // adapters, but resized compiled frames, some of the new code
  // (e.g. JVMTI) wouldn't work if we return the (current) SP of the
  // frame.
  return _unextended_sp;
}

// Return true if this frame is older (less recent activation) than
// the frame represented by id.
inline bool frame::is_older(intptr_t* id) const {
   assert(this->id() != NULL && id != NULL, "NULL frame id");
   // Stack grows towards smaller addresses on ppc64.
   return this->id() > id;
}

inline int frame::frame_size(RegisterMap* map) const {
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

inline intptr_t* frame::real_fp() const {
  return fp();
}

#ifdef CC_INTERP

inline interpreterState frame::get_interpreterState() const {
  return (interpreterState)(((address)callers_abi())
                            - frame::interpreter_frame_cinterpreterstate_size_in_bytes());
}

inline intptr_t** frame::interpreter_frame_locals_addr() const {
  interpreterState istate = get_interpreterState();
  return (intptr_t**)&istate->_locals;
}

inline intptr_t* frame::interpreter_frame_bcx_addr() const {
  interpreterState istate = get_interpreterState();
  return (intptr_t*)&istate->_bcp;
}

inline intptr_t* frame::interpreter_frame_mdx_addr() const {
  interpreterState istate = get_interpreterState();
  return (intptr_t*)&istate->_mdx;
}

inline intptr_t* frame::interpreter_frame_expression_stack() const {
  return (intptr_t*)interpreter_frame_monitor_end() - 1;
}

inline jint frame::interpreter_frame_expression_stack_direction() {
  return -1;
}

// top of expression stack
inline intptr_t* frame::interpreter_frame_tos_address() const {
  interpreterState istate = get_interpreterState();
  return istate->_stack + 1;
}

inline intptr_t* frame::interpreter_frame_tos_at(jint offset) const {
  return &interpreter_frame_tos_address()[offset];
}

// monitor elements

// in keeping with Intel side: end is lower in memory than begin;
// and beginning element is oldest element
// Also begin is one past last monitor.

inline BasicObjectLock* frame::interpreter_frame_monitor_begin() const {
  return get_interpreterState()->monitor_base();
}

inline BasicObjectLock* frame::interpreter_frame_monitor_end() const {
  return (BasicObjectLock*)get_interpreterState()->stack_base();
}

inline int frame::interpreter_frame_cinterpreterstate_size_in_bytes() {
  // Size of an interpreter object. Not aligned with frame size.
  return round_to(sizeof(BytecodeInterpreter), 8);
}

inline Method** frame::interpreter_frame_method_addr() const {
  interpreterState istate = get_interpreterState();
  return &istate->_method;
}

// Constant pool cache

inline ConstantPoolCache** frame::interpreter_frame_cpoolcache_addr() const {
  interpreterState istate = get_interpreterState();
  return &istate->_constants; // should really use accessor
}

inline ConstantPoolCache** frame::interpreter_frame_cache_addr() const {
  interpreterState istate = get_interpreterState();
  return &istate->_constants;
}
#endif // CC_INTERP

inline int frame::interpreter_frame_monitor_size() {
  // Number of stack slots for a monitor.
  return round_to(BasicObjectLock::size(),  // number of stack slots
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
  return *((oop*)map->location(R3->as_VMReg()));
}

inline void frame::set_saved_oop_result(RegisterMap* map, oop obj) {
  *((oop*)map->location(R3->as_VMReg())) = obj;
}

#endif // CPU_PPC_VM_FRAME_PPC_INLINE_HPP
