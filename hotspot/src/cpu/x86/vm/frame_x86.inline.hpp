/*
 * Copyright 1997-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

// Inline functions for Intel frames:

// Constructors:

inline frame::frame() {
  _pc = NULL;
  _sp = NULL;
  _unextended_sp = NULL;
  _fp = NULL;
  _cb = NULL;
  _deopt_state = unknown;
}

inline frame:: frame(intptr_t* sp, intptr_t* fp, address pc) {
  _sp = sp;
  _unextended_sp = sp;
  _fp = fp;
  _pc = pc;
  assert(pc != NULL, "no pc?");
  _cb = CodeCache::find_blob(pc);
  _deopt_state = not_deoptimized;
  if (_cb != NULL && _cb->is_nmethod() && ((nmethod*)_cb)->is_deopt_pc(_pc)) {
    _pc = (((nmethod*)_cb)->get_original_pc(this));
    _deopt_state = is_deoptimized;
  } else {
    _deopt_state = not_deoptimized;
  }
}

inline frame:: frame(intptr_t* sp, intptr_t* unextended_sp, intptr_t* fp, address pc) {
  _sp = sp;
  _unextended_sp = unextended_sp;
  _fp = fp;
  _pc = pc;
  assert(pc != NULL, "no pc?");
  _cb = CodeCache::find_blob(pc);
  _deopt_state = not_deoptimized;
  if (_cb != NULL && _cb->is_nmethod() && ((nmethod*)_cb)->is_deopt_pc(_pc)) {
    _pc = (((nmethod*)_cb)->get_original_pc(this));
    _deopt_state = is_deoptimized;
  } else {
    _deopt_state = not_deoptimized;
  }
}

inline frame::frame(intptr_t* sp, intptr_t* fp) {
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

  _deopt_state = not_deoptimized;
  if (_cb != NULL && _cb->is_nmethod() && ((nmethod*)_cb)->is_deopt_pc(_pc)) {
    _pc = (((nmethod*)_cb)->get_original_pc(this));
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
inline void      frame::set_link(intptr_t* addr)  { *(intptr_t **)addr_at(link_offset) = addr; }


inline intptr_t* frame::unextended_sp() const     { return _unextended_sp; }

// Return address:

inline address* frame::sender_pc_addr()      const { return (address*) addr_at( return_addr_offset); }
inline address  frame::sender_pc()           const { return *sender_pc_addr(); }

// return address of param, zero origin index.
inline address* frame::native_param_addr(int idx) const { return (address*) addr_at( native_frame_initial_param_offset+idx); }

#ifdef CC_INTERP

inline interpreterState frame::get_interpreterState() const {
  return ((interpreterState)addr_at( -((int)sizeof(BytecodeInterpreter))/wordSize ));
}

inline intptr_t*    frame::sender_sp()        const {
  // Hmm this seems awfully expensive QQQ, is this really called with interpreted frames?
  if (is_interpreted_frame()) {
    assert(false, "should never happen");
    return get_interpreterState()->sender_sp();
  } else {
    return            addr_at(sender_sp_offset);
  }
}

inline intptr_t** frame::interpreter_frame_locals_addr() const {
  assert(is_interpreted_frame(), "must be interpreted");
  return &(get_interpreterState()->_locals);
}

inline intptr_t* frame::interpreter_frame_bcx_addr() const {
  assert(is_interpreted_frame(), "must be interpreted");
  return (intptr_t*) &(get_interpreterState()->_bcp);
}


// Constant pool cache

inline constantPoolCacheOop* frame::interpreter_frame_cache_addr() const {
  assert(is_interpreted_frame(), "must be interpreted");
  return &(get_interpreterState()->_constants);
}

// Method

inline methodOop* frame::interpreter_frame_method_addr() const {
  assert(is_interpreted_frame(), "must be interpreted");
  return &(get_interpreterState()->_method);
}

inline intptr_t* frame::interpreter_frame_mdx_addr() const {
  assert(is_interpreted_frame(), "must be interpreted");
  return (intptr_t*) &(get_interpreterState()->_mdx);
}

// top of expression stack
inline intptr_t* frame::interpreter_frame_tos_address() const {
  assert(is_interpreted_frame(), "wrong frame type");
  return get_interpreterState()->_stack + 1;
}

#else /* asm interpreter */
inline intptr_t*    frame::sender_sp()        const { return            addr_at(   sender_sp_offset); }

inline intptr_t** frame::interpreter_frame_locals_addr() const {
  return (intptr_t**)addr_at(interpreter_frame_locals_offset);
}

inline intptr_t* frame::interpreter_frame_last_sp() const {
  return *(intptr_t**)addr_at(interpreter_frame_last_sp_offset);
}

inline intptr_t* frame::interpreter_frame_bcx_addr() const {
  return (intptr_t*)addr_at(interpreter_frame_bcx_offset);
}


inline intptr_t* frame::interpreter_frame_mdx_addr() const {
  return (intptr_t*)addr_at(interpreter_frame_mdx_offset);
}



// Constant pool cache

inline constantPoolCacheOop* frame::interpreter_frame_cache_addr() const {
  return (constantPoolCacheOop*)addr_at(interpreter_frame_cache_offset);
}

// Method

inline methodOop* frame::interpreter_frame_method_addr() const {
  return (methodOop*)addr_at(interpreter_frame_method_offset);
}

// top of expression stack
inline intptr_t* frame::interpreter_frame_tos_address() const {
  intptr_t* last_sp = interpreter_frame_last_sp();
  if (last_sp == NULL) {
    return sp();
  } else {
    // sp() may have been extended or shrunk by an adapter.  At least
    // check that we don't fall behind the legal region.
    assert(last_sp < (intptr_t*) interpreter_frame_monitor_begin(), "bad tos");
    return last_sp;
  }
}

#endif /* CC_INTERP */

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

inline JavaCallWrapper* frame::entry_frame_call_wrapper() const {
 return (JavaCallWrapper*)at(entry_frame_call_wrapper_offset);
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



inline oop frame::saved_oop_result(RegisterMap* map) const       {
  return *((oop*) map->location(rax->as_VMReg()));
}

inline void frame::set_saved_oop_result(RegisterMap* map, oop obj) {
  *((oop*) map->location(rax->as_VMReg())) = obj;
}
