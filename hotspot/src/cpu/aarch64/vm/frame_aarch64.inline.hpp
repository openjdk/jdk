/*
 * Copyright (c) 1997, 2016, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, Red Hat Inc. All rights reserved.
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

#ifndef CPU_AARCH64_VM_FRAME_AARCH64_INLINE_HPP
#define CPU_AARCH64_VM_FRAME_AARCH64_INLINE_HPP

#include "code/codeCache.hpp"
#include "code/vmreg.inline.hpp"

// Inline functions for AArch64 frames:

// Constructors:

inline frame::frame() {
  _pc = NULL;
  _sp = NULL;
  _unextended_sp = NULL;
  _fp = NULL;
  _cb = NULL;
  _deopt_state = unknown;
}

static int spin;

inline void frame::init(intptr_t* sp, intptr_t* fp, address pc) {
  intptr_t a = intptr_t(sp);
  intptr_t b = intptr_t(fp);
  _sp = sp;
  _unextended_sp = sp;
  _fp = fp;
  _pc = pc;
  assert(pc != NULL, "no pc?");
  _cb = CodeCache::find_blob(pc);
  adjust_unextended_sp();

  address original_pc = CompiledMethod::get_deopt_original_pc(this);
  if (original_pc != NULL) {
    _pc = original_pc;
    _deopt_state = is_deoptimized;
  } else {
    _deopt_state = not_deoptimized;
  }
}

inline frame::frame(intptr_t* sp, intptr_t* fp, address pc) {
  init(sp, fp, pc);
}

inline frame::frame(intptr_t* sp, intptr_t* unextended_sp, intptr_t* fp, address pc) {
  intptr_t a = intptr_t(sp);
  intptr_t b = intptr_t(fp);
  _sp = sp;
  _unextended_sp = unextended_sp;
  _fp = fp;
  _pc = pc;
  assert(pc != NULL, "no pc?");
  _cb = CodeCache::find_blob(pc);
  adjust_unextended_sp();

  address original_pc = CompiledMethod::get_deopt_original_pc(this);
  if (original_pc != NULL) {
    _pc = original_pc;
    assert(((CompiledMethod*)_cb)->insts_contains(_pc), "original PC must be in CompiledMethod");
    _deopt_state = is_deoptimized;
  } else {
    _deopt_state = not_deoptimized;
  }
}

inline frame::frame(intptr_t* sp, intptr_t* fp) {
  intptr_t a = intptr_t(sp);
  intptr_t b = intptr_t(fp);
  _sp = sp;
  _unextended_sp = sp;
  _fp = fp;
  _pc = (address)(sp[-1]);

  // Here's a sticky one. This constructor can be called via AsyncGetCallTrace
  // when last_Java_sp is non-null but the pc fetched is junk. If we are truly
  // unlucky the junk value could be to a zombied method and we'll die on the
  // find_blob call. This is also why we can have no asserts on the validity
  // of the pc we find here. AsyncGetCallTrace -> pd_get_top_frame_for_signal_handler
  // -> pd_last_frame should use a specialized version of pd_last_frame which could
  // call a specilaized frame constructor instead of this one.
  // Then we could use the assert below. However this assert is of somewhat dubious
  // value.
  // assert(_pc != NULL, "no pc?");

  _cb = CodeCache::find_blob(_pc);
  adjust_unextended_sp();

  address original_pc = CompiledMethod::get_deopt_original_pc(this);
  if (original_pc != NULL) {
    _pc = original_pc;
    _deopt_state = is_deoptimized;
  } else {
    _deopt_state = not_deoptimized;
  }
}

// Accessors

inline bool frame::equal(frame other) const {
  bool ret =  sp() == other.sp()
              && unextended_sp() == other.unextended_sp()
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



inline intptr_t* frame::link() const              { return (intptr_t*) *(intptr_t **)addr_at(link_offset); }


inline intptr_t* frame::unextended_sp() const     { return _unextended_sp; }

// Return address:

inline address* frame::sender_pc_addr()      const { return (address*) addr_at( return_addr_offset); }
inline address  frame::sender_pc()           const { return *sender_pc_addr(); }

inline intptr_t*    frame::sender_sp()        const { return            addr_at(   sender_sp_offset); }

inline intptr_t** frame::interpreter_frame_locals_addr() const {
  return (intptr_t**)addr_at(interpreter_frame_locals_offset);
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

// Mirror

inline oop* frame::interpreter_frame_mirror_addr() const {
  return (oop*)addr_at(interpreter_frame_mirror_offset);
}

// top of expression stack
inline intptr_t* frame::interpreter_frame_tos_address() const {
  intptr_t* last_sp = interpreter_frame_last_sp();
  if (last_sp == NULL) {
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

inline int frame::pd_oop_map_offset_adjustment() const {
  return 0;
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


inline jint frame::interpreter_frame_expression_stack_direction() { return -1; }


// Entry frames

inline JavaCallWrapper** frame::entry_frame_call_wrapper_addr() const {
 return (JavaCallWrapper**)addr_at(entry_frame_call_wrapper_offset);
}


// Compiled frames

inline int frame::local_offset_for_compiler(int local_index, int nof_args, int max_nof_locals, int max_nof_monitors) {
  return (nof_args - local_index + (local_index < nof_args ? 1: -1));
}

inline int frame::monitor_offset_for_compiler(int local_index, int nof_args, int max_nof_locals, int max_nof_monitors) {
  return local_offset_for_compiler(local_index, nof_args, max_nof_locals, max_nof_monitors);
}

inline int frame::min_local_offset_for_compiler(int nof_args, int max_nof_locals, int max_nof_monitors) {
  return (nof_args - (max_nof_locals + max_nof_monitors*2) - 1);
}

inline bool frame::volatile_across_calls(Register reg) {
  return true;
}



inline oop frame::saved_oop_result(RegisterMap* map) const {
  oop* result_adr = (oop *)map->location(r0->as_VMReg());
  guarantee(result_adr != NULL, "bad register save location");

  return (*result_adr);
}

inline void frame::set_saved_oop_result(RegisterMap* map, oop obj) {
  oop* result_adr = (oop *)map->location(r0->as_VMReg());
  guarantee(result_adr != NULL, "bad register save location");

  *result_adr = obj;
}

#endif // CPU_AARCH64_VM_FRAME_AARCH64_INLINE_HPP
