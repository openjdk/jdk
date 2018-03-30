/*
 * Copyright (c) 1997, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_SPARC_VM_FRAME_SPARC_INLINE_HPP
#define CPU_SPARC_VM_FRAME_SPARC_INLINE_HPP

#include "asm/macroAssembler.hpp"
#include "code/vmreg.inline.hpp"
#include "utilities/align.hpp"

// Inline functions for SPARC frames:

// Constructors

inline frame::frame() {
  _pc = NULL;
  _sp = NULL;
  _younger_sp = NULL;
  _cb = NULL;
  _deopt_state = unknown;
  _sp_adjustment_by_callee = 0;
}

// Accessors:

inline bool frame::equal(frame other) const {
  bool ret =  sp() == other.sp()
           && fp() == other.fp()
           && pc() == other.pc();
  assert(!ret || ret && cb() == other.cb() && _deopt_state == other._deopt_state, "inconsistent construction");
  return ret;
}

// Return unique id for this frame. The id must have a value where we can distinguish
// identity and younger/older relationship. NULL represents an invalid (incomparable)
// frame.
inline intptr_t* frame::id(void) const { return unextended_sp(); }

// Relationals on frames based
// Return true if the frame is younger (more recent activation) than the frame represented by id
inline bool frame::is_younger(intptr_t* id) const { assert(this->id() != NULL && id != NULL, "NULL frame id");
                                                    return this->id() < id ; }

// Return true if the frame is older (less recent activation) than the frame represented by id
inline bool frame::is_older(intptr_t* id) const   { assert(this->id() != NULL && id != NULL, "NULL frame id");
                                                    return this->id() > id ; }

inline int frame::frame_size(RegisterMap* map) const { return sender_sp() - sp(); }

inline intptr_t* frame::link() const { return (intptr_t *)(fp()[FP->sp_offset_in_saved_window()] + STACK_BIAS); }

inline intptr_t* frame::unextended_sp() const { return sp() + _sp_adjustment_by_callee; }

// return address:

inline address  frame::sender_pc()        const    { return *I7_addr() + pc_return_offset; }

inline address* frame::I7_addr() const  { return (address*) &sp()[ I7->sp_offset_in_saved_window()]; }
inline address* frame::I0_addr() const  { return (address*) &sp()[ I0->sp_offset_in_saved_window()]; }

inline address* frame::O7_addr() const  { return (address*) &younger_sp()[ I7->sp_offset_in_saved_window()]; }
inline address* frame::O0_addr() const  { return (address*) &younger_sp()[ I0->sp_offset_in_saved_window()]; }

inline intptr_t*    frame::sender_sp() const  { return fp(); }

inline intptr_t* frame::real_fp() const { return fp(); }

inline intptr_t** frame::interpreter_frame_locals_addr() const {
  return (intptr_t**) sp_addr_at( Llocals->sp_offset_in_saved_window());
}

inline intptr_t* frame::interpreter_frame_bcp_addr() const {
  return (intptr_t*) sp_addr_at( Lbcp->sp_offset_in_saved_window());
}

inline intptr_t* frame::interpreter_frame_mdp_addr() const {
  // %%%%% reinterpreting ImethodDataPtr as a mdx
  return (intptr_t*) sp_addr_at( ImethodDataPtr->sp_offset_in_saved_window());
}

// bottom(base) of the expression stack (highest address)
inline intptr_t* frame::interpreter_frame_expression_stack() const {
  return (intptr_t*)interpreter_frame_monitors() - 1;
}

// top of expression stack (lowest address)
inline intptr_t* frame::interpreter_frame_tos_address() const {
  return *interpreter_frame_esp_addr() + 1;
}

inline BasicObjectLock** frame::interpreter_frame_monitors_addr() const {
  return (BasicObjectLock**) sp_addr_at(Lmonitors->sp_offset_in_saved_window());
}
inline intptr_t** frame::interpreter_frame_esp_addr() const {
  return (intptr_t**)sp_addr_at(Lesp->sp_offset_in_saved_window());
}

inline void frame::interpreter_frame_set_tos_address( intptr_t* x ) {
  *interpreter_frame_esp_addr() = x - 1;
}

// monitor elements

// in keeping with Intel side: end is lower in memory than begin;
// and beginning element is oldest element
// Also begin is one past last monitor.

inline BasicObjectLock* frame::interpreter_frame_monitor_begin()       const  {
  int rounded_vm_local_words = align_up((int)frame::interpreter_frame_vm_local_words, WordsPerLong);
  return (BasicObjectLock *)fp_addr_at(-rounded_vm_local_words);
}

inline BasicObjectLock* frame::interpreter_frame_monitor_end()         const  {
  return interpreter_frame_monitors();
}


inline void frame::interpreter_frame_set_monitor_end(BasicObjectLock* value) {
  interpreter_frame_set_monitors(value);
}

inline int frame::interpreter_frame_monitor_size() {
  return align_up(BasicObjectLock::size(), WordsPerLong);
}

inline Method** frame::interpreter_frame_method_addr() const {
  return (Method**)sp_addr_at( Lmethod->sp_offset_in_saved_window());
}

inline BasicObjectLock* frame::interpreter_frame_monitors() const {
  return *interpreter_frame_monitors_addr();
}

inline void frame::interpreter_frame_set_monitors(BasicObjectLock* monitors) {
  *interpreter_frame_monitors_addr() = monitors;
}

inline oop* frame::interpreter_frame_mirror_addr() const {
  return (oop*)(fp() + interpreter_frame_mirror_offset);
}

// Constant pool cache

// where LcpoolCache is saved:
inline ConstantPoolCache** frame::interpreter_frame_cpoolcache_addr() const {
    return (ConstantPoolCache**)sp_addr_at(LcpoolCache->sp_offset_in_saved_window());
  }

inline ConstantPoolCache** frame::interpreter_frame_cache_addr() const {
  return (ConstantPoolCache**)sp_addr_at( LcpoolCache->sp_offset_in_saved_window());
}

inline oop* frame::interpreter_frame_temp_oop_addr() const {
  return (oop *)(fp() + interpreter_frame_oop_temp_offset);
}


inline JavaCallWrapper** frame::entry_frame_call_wrapper_addr() const {
  // note: adjust this code if the link argument in StubGenerator::call_stub() changes!
  const Argument link = Argument(0, false);
  return (JavaCallWrapper**)&sp()[link.as_in().as_register()->sp_offset_in_saved_window()];
}


inline oop  frame::saved_oop_result(RegisterMap* map) const      {
  return *((oop*) map->location(O0->as_VMReg()));
}

inline void frame::set_saved_oop_result(RegisterMap* map, oop obj) {
  *((oop*) map->location(O0->as_VMReg())) = obj;
}

#endif // CPU_SPARC_VM_FRAME_SPARC_INLINE_HPP
