/*
 * Copyright (c) 2000, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2012, 2023 SAP SE. All rights reserved.
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

#include "code/codeBlob.inline.hpp"
#include "code/codeCache.inline.hpp"
#include "code/vmreg.inline.hpp"
#include "runtime/sharedRuntime.hpp"
#include "utilities/align.hpp"

// Inline functions for ppc64 frames:

// Initialize frame members (_sp must be given)
inline void frame::setup(kind knd) {
  if (_pc == nullptr) {
    _pc = (address)own_abi()->lr;
    assert(_pc != nullptr, "must have PC");
  }

  if (_cb == nullptr) {
    _cb = (knd == kind::nmethod) ? CodeCache::find_blob_fast(_pc) : CodeCache::find_blob(_pc);
  }

  if (_unextended_sp == nullptr) {
    _unextended_sp = _sp;
  }

  if (_fp == nullptr) {
    // The back link for compiled frames on the heap is not valid
    if (is_heap_frame()) {
      // fp for interpreted frames should have been derelativized and passed to the constructor
      assert(is_compiled_frame(), "");
      // The back link for compiled frames on the heap is invalid.
      _fp = _unextended_sp + _cb->frame_size();
    } else {
      _fp = (intptr_t*)own_abi()->callers_sp;
    }
  }

  address original_pc = get_deopt_original_pc();
  if (original_pc != nullptr) {
    _pc = original_pc;
    _deopt_state = is_deoptimized;
    assert(_cb == nullptr || _cb->as_nmethod()->insts_contains_inclusive(_pc),
           "original PC must be in the main code section of the compiled method (or must be immediately following it)");
  } else {
    if (_cb == SharedRuntime::deopt_blob()) {
      _deopt_state = is_deoptimized;
    } else {
      _deopt_state = not_deoptimized;
    }
  }

  // Continuation frames on the java heap are not aligned.
  // When thawing interpreted frames the sp can be unaligned (see new_stack_frame()).
  assert(_on_heap ||
         ((is_aligned(_sp, alignment_in_bytes) || is_interpreted_frame()) &&
          (is_aligned(_fp, alignment_in_bytes) || !is_fully_initialized())),
         "invalid alignment sp:" PTR_FORMAT " unextended_sp:" PTR_FORMAT " fp:" PTR_FORMAT, p2i(_sp), p2i(_unextended_sp), p2i(_fp));
}

// Constructors

// Initialize all fields
inline frame::frame() : _sp(nullptr), _pc(nullptr), _cb(nullptr), _oop_map(nullptr), _deopt_state(unknown),
                        _on_heap(false), DEBUG_ONLY(_frame_index(-1) COMMA) _unextended_sp(nullptr), _fp(nullptr) {}

inline frame::frame(intptr_t* sp) : frame(sp, nullptr, kind::nmethod) {}

inline frame::frame(intptr_t* sp, intptr_t* fp, address pc) : frame(sp, pc, nullptr, fp, nullptr) {}

inline frame::frame(intptr_t* sp, address pc, kind knd)
  : _sp(sp), _pc(pc), _cb(nullptr), _oop_map(nullptr),
    _on_heap(false), DEBUG_ONLY(_frame_index(-1) COMMA) _unextended_sp(sp), _fp(nullptr) {
  setup(knd);
}

inline frame::frame(intptr_t* sp, address pc, intptr_t* unextended_sp, intptr_t* fp, CodeBlob* cb)
  : _sp(sp), _pc(pc), _cb(cb), _oop_map(nullptr),
    _on_heap(false), DEBUG_ONLY(_frame_index(-1) COMMA) _unextended_sp(unextended_sp), _fp(fp) {
  setup(kind::nmethod);
}

inline frame::frame(intptr_t* sp, intptr_t* unextended_sp, intptr_t* fp, address pc, CodeBlob* cb, const ImmutableOopMap* oop_map)
  : _sp(sp), _pc(pc), _cb(cb), _oop_map(oop_map),
    _on_heap(false), DEBUG_ONLY(_frame_index(-1) COMMA) _unextended_sp(unextended_sp), _fp(fp) {
  assert(_cb != nullptr, "pc: " INTPTR_FORMAT, p2i(pc));
  setup(kind::nmethod);
}

inline frame::frame(intptr_t* sp, intptr_t* unextended_sp, intptr_t* fp, address pc, CodeBlob* cb,
                    const ImmutableOopMap* oop_map, bool on_heap)
                    : _sp(sp), _pc(pc), _cb(cb), _oop_map(oop_map), _deopt_state(not_deoptimized),
                      _on_heap(on_heap), DEBUG_ONLY(_frame_index(-1) COMMA) _unextended_sp(unextended_sp), _fp(fp) {
  // In thaw, non-heap frames use this constructor to pass oop_map.  I don't know why.
  assert(_on_heap || _cb != nullptr, "these frames are always heap frames");
  if (cb != nullptr) {
    setup(kind::nmethod);
  }
#ifdef ASSERT
  // The following assertion has been disabled because it would sometime trap for Continuation.run,
  // which is not *in* a continuation and therefore does not clear the _cont_fastpath flag, but this
  // is benign even in fast mode (see Freeze::setup_jump)
  // We might freeze deoptimized frame in slow mode
  // assert(_pc == pc && _deopt_state == not_deoptimized, "");
#endif
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
   // Stack grows towards smaller addresses on ppc64.
   return this->id() > id;
}

inline int frame::frame_size() const {
  // Stack grows towards smaller addresses on PPC64: sender is at a higher address.
  return sender_sp() - sp();
}

// Return the frame's stack pointer before it has been extended by a
// c2i adapter.
// i2c adapters also modify the frame they are applied on but shared code
// must never use an interpreted frames unextended sp directly as the value
// is platform dependent.
inline intptr_t* frame::unextended_sp() const          { assert_absolute(); return _unextended_sp; }
inline void frame::set_unextended_sp(intptr_t* value)  { _unextended_sp = value; }
inline int  frame::offset_unextended_sp() const        { assert_offset();   return _offset_unextended_sp; }
inline void frame::set_offset_unextended_sp(int value) { assert_on_heap();  _offset_unextended_sp = value; }

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

inline intptr_t* frame::interpreter_frame_locals() const {
  intptr_t n = *addr_at(ijava_idx(locals));
  return &fp()[n]; // return relativized locals
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
  return (intptr_t*) at_relative(ijava_idx(esp));
}

// Convenient setters
inline void frame::interpreter_frame_set_monitor_end(BasicObjectLock* end) {
  assert(is_interpreted_frame(), "interpreted frame expected");
  // set relativized monitors
  get_ijava_state()->monitors = (intptr_t) ((intptr_t*)end - fp());
}

inline void frame::interpreter_frame_set_cpcache(ConstantPoolCache* cp)       { *interpreter_frame_cache_addr() = cp; }

inline void frame::interpreter_frame_set_esp(intptr_t* esp) {
  assert(is_interpreted_frame(), "interpreted frame expected");
  // set relativized esp
  get_ijava_state()->esp = (intptr_t) (esp - fp());
}

inline void frame::interpreter_frame_set_top_frame_sp(intptr_t* top_frame_sp) {
  assert(is_interpreted_frame(), "interpreted frame expected");
  // set relativized top_frame_sp
  get_ijava_state()->top_frame_sp = (intptr_t) (top_frame_sp - fp());
}

inline void frame::interpreter_frame_set_sender_sp(intptr_t* sender_sp)       { get_ijava_state()->sender_sp = (intptr_t) sender_sp; }

inline intptr_t* frame::interpreter_frame_expression_stack() const {
  intptr_t* monitor_end = (intptr_t*) interpreter_frame_monitor_end();
  return monitor_end-1;
}

// top of expression stack
inline intptr_t* frame::interpreter_frame_tos_address() const {
  return interpreter_frame_esp() + Interpreter::stackElementWords;
}

inline int frame::interpreter_frame_monitor_size() {
  return BasicObjectLock::size();
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

inline bool frame::is_interpreted_frame() const  {
  return Interpreter::contains(pc());
}

inline frame frame::sender_raw(RegisterMap* map) const {
  // Default is we do have to follow them. The sender_for_xxx will
  // update it accordingly.
  map->set_include_argument_oops(false);

  if (map->in_cont()) { // already in an h-stack
    return map->stack_chunk()->sender(*this, map);
  }

  if (is_entry_frame())       return sender_for_entry_frame(map);
  if (is_upcall_stub_frame()) return sender_for_upcall_stub_frame(map);
  if (is_interpreted_frame()) return sender_for_interpreter_frame(map);

  assert(_cb == CodeCache::find_blob(pc()), "Must be the same");
  if (_cb != nullptr) return sender_for_compiled_frame(map);

  // Must be native-compiled frame, i.e. the marshaling code for native
  // methods that exists in the core system.
  return frame(sender_sp(), sender_pc(), kind::code_blob);
}

inline frame frame::sender(RegisterMap* map) const {
  frame result = sender_raw(map);

  if (map->process_frames() && !map->in_cont()) {
    StackWatermarkSet::on_iteration(map->thread(), result);
  }

  return result;
}

inline frame frame::sender_for_compiled_frame(RegisterMap *map) const {
  assert(map != nullptr, "map must be set");

  intptr_t* sender_sp = this->sender_sp();
  address   sender_pc = this->sender_pc();

  if (map->update_map()) {
    // Tell GC to use argument oopmaps for some runtime stubs that need it.
    // For C1, the runtime stub might not have oop maps, so set this flag
    // outside of update_register_map.
    if (!_cb->is_nmethod()) { // compiled frames do not use callee-saved registers
      map->set_include_argument_oops(_cb->caller_must_gc_arguments(map->thread()));
      if (oop_map() != nullptr) {
        _oop_map->update_register_map(this, map);
      }
    } else {
      assert(!_cb->caller_must_gc_arguments(map->thread()), "");
      assert(!map->include_argument_oops(), "");
      assert(oop_map() == nullptr || !oop_map()->has_any(OopMapValue::callee_saved_value), "callee-saved value in compiled frame");
    }
  }

  assert(sender_sp != sp(), "must have changed");

  if (Continuation::is_return_barrier_entry(sender_pc)) {
    if (map->walk_cont()) { // about to walk into an h-stack
      return Continuation::top_frame(*this, map);
    } else {
      return Continuation::continuation_bottom_sender(map->thread(), *this, sender_sp);
    }
  }

  return frame(sender_sp, sender_pc);
}

inline oop frame::saved_oop_result(RegisterMap* map) const {
  oop* result_adr = (oop *)map->location(R3->as_VMReg(), sp());
  guarantee(result_adr != nullptr, "bad register save location");
  return *result_adr;
}

inline void frame::set_saved_oop_result(RegisterMap* map, oop obj) {
  oop* result_adr = (oop *)map->location(R3->as_VMReg(), sp());
  guarantee(result_adr != nullptr, "bad register save location");

  *result_adr = obj;
}

inline int frame::compiled_frame_stack_argsize() const {
  assert(cb()->is_nmethod(), "");
  return (cb()->as_nmethod()->num_stack_arg_slots() * VMRegImpl::stack_slot_size) >> LogBytesPerWord;
}

inline void frame::interpreted_frame_oop_map(InterpreterOopMap* mask) const {
  assert(mask != nullptr, "");
  Method* m = interpreter_frame_method();
  int   bci = interpreter_frame_bci();
  m->mask_for(bci, mask); // OopMapCache::compute_one_oop_map(m, bci, mask);
}

inline int frame::sender_sp_ret_address_offset() {
  return -(int)(_abi0(lr) >> LogBytesPerWord); // offset in words
}

template <typename RegisterMapT>
void frame::update_map_with_saved_link(RegisterMapT* map, intptr_t** link_addr) {
  // Nothing to do.
}

#endif // CPU_PPC_FRAME_PPC_INLINE_HPP
