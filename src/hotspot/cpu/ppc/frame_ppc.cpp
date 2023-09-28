/*
 * Copyright (c) 2000, 2023, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "compiler/oopMap.hpp"
#include "interpreter/interpreter.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/markWord.hpp"
#include "oops/method.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/jniHandles.inline.hpp"
#include "runtime/monitorChunk.hpp"
#include "runtime/os.inline.hpp"
#include "runtime/signature.hpp"
#include "runtime/stackWatermarkSet.hpp"
#include "runtime/stubCodeGenerator.hpp"
#include "runtime/stubRoutines.hpp"
#ifdef COMPILER1
#include "c1/c1_Runtime1.hpp"
#include "runtime/vframeArray.hpp"
#endif

#ifdef ASSERT
void RegisterMap::check_location_valid() {
}
#endif // ASSERT

bool frame::safe_for_sender(JavaThread *thread) {
  if (is_heap_frame()) {
    return true;
  }
  address sp = (address)_sp;
  address fp = (address)_fp;
  address unextended_sp = (address)_unextended_sp;

  // consider stack guards when trying to determine "safe" stack pointers
  // sp must be within the usable part of the stack (not in guards)
  if (!thread->is_in_usable_stack(sp)) {
    return false;
  }

  // Unextended sp must be within the stack
  if (!thread->is_in_full_stack_checked(unextended_sp)) {
    return false;
  }

  // An fp must be within the stack and above (but not equal) sp.
  bool fp_safe = thread->is_in_stack_range_excl(fp, sp);
  // An interpreter fp must be fp_safe.
  // Moreover, it must be at a distance at least the size of the ijava_state structure.
  bool fp_interp_safe = fp_safe && ((fp - sp) >= ijava_state_size);

  // We know sp/unextended_sp are safe, only fp is questionable here

  // If the current frame is known to the code cache then we can attempt to
  // construct the sender and do some validation of it. This goes a long way
  // toward eliminating issues when we get in frame construction code

  if (_cb != nullptr) {

    // First check if the frame is complete and the test is reliable.
    // Unfortunately we can only check frame completeness for runtime stubs
    // and nmethods. Other generic buffer blobs are more problematic
    // so we just assume they are OK.
    // Adapter blobs never have a complete frame and are never OK
    if (!_cb->is_frame_complete_at(_pc)) {
      if (_cb->is_compiled() || _cb->is_adapter_blob() || _cb->is_runtime_stub()) {
        return false;
      }
    }

    // Could just be some random pointer within the codeBlob.
    if (!_cb->code_contains(_pc)) {
      return false;
    }

    // Entry frame checks
    if (is_entry_frame()) {
      // An entry frame must have a valid fp.
      return fp_safe && is_entry_frame_valid(thread);
    }

    if (is_interpreted_frame() && !fp_interp_safe) {
      return false;
    }

    // At this point, there still is a chance that fp_safe is false.
    // In particular, fp might be null. So let's check and
    // bail out before we actually dereference from fp.
    if (!fp_safe) {
      return false;
    }

    common_abi* sender_abi = (common_abi*) fp;
    intptr_t* sender_sp = (intptr_t*) fp;
    address   sender_pc = (address) sender_abi->lr;;

    if (Continuation::is_return_barrier_entry(sender_pc)) {
      // If our sender_pc is the return barrier, then our "real" sender is the continuation entry
      frame s = Continuation::continuation_bottom_sender(thread, *this, sender_sp);
      sender_sp = s.sp();
      sender_pc = s.pc();
    }

    // We must always be able to find a recognizable pc.
    CodeBlob* sender_blob = CodeCache::find_blob(sender_pc);
    if (sender_blob == nullptr) {
      return false;
    }

    // It should be safe to construct the sender though it might not be valid.

    frame sender(sender_sp, sender_pc);

    // Do we have a valid fp?
    address sender_fp = (address) sender.fp();

    // sender_fp must be within the stack and above (but not
    // equal) current frame's fp.
    if (!thread->is_in_stack_range_excl(sender_fp, fp)) {
        return false;
    }

    // If the potential sender is the interpreter then we can do some more checking.
    if (Interpreter::contains(sender_pc)) {
      return sender.is_interpreted_frame_valid(thread);
    }

    // Could just be some random pointer within the codeBlob.
    if (!sender.cb()->code_contains(sender_pc)) {
      return false;
    }

    // We should never be able to see an adapter if the current frame is something from code cache.
    if (sender_blob->is_adapter_blob()) {
      return false;
    }

    if (sender.is_entry_frame()) {
      return sender.is_entry_frame_valid(thread);
    }

    // Frame size is always greater than zero. If the sender frame size is zero or less,
    // something is really weird and we better give up.
    if (sender_blob->frame_size() <= 0) {
      return false;
    }

    return true;
  }

  // Must be native-compiled frame. Since sender will try and use fp to find
  // linkages it must be safe

  if (!fp_safe) {
    return false;
  }

  return true;
}

frame frame::sender_for_entry_frame(RegisterMap *map) const {
  assert(map != nullptr, "map must be set");
  // Java frame called from C; skip all C frames and return top C
  // frame of that chunk as the sender.
  JavaFrameAnchor* jfa = entry_frame_call_wrapper()->anchor();
  assert(!entry_frame_is_first(), "next Java fp must be non zero");
  assert(jfa->last_Java_sp() > _sp, "must be above this frame on stack");
  map->clear();
  assert(map->include_argument_oops(), "should be set by clear");

  if (jfa->last_Java_pc() != nullptr) {
    frame fr(jfa->last_Java_sp(), jfa->last_Java_pc());
    return fr;
  }
  // Last_java_pc is not set, if we come here from compiled code. The
  // constructor retrieves the PC from the stack.
  frame fr(jfa->last_Java_sp());
  return fr;
}

UpcallStub::FrameData* UpcallStub::frame_data_for_frame(const frame& frame) const {
  assert(frame.is_upcall_stub_frame(), "wrong frame");
  // need unextended_sp here, since normal sp is wrong for interpreter callees
  return reinterpret_cast<UpcallStub::FrameData*>(
    reinterpret_cast<address>(frame.unextended_sp()) + in_bytes(_frame_data_offset));
}

bool frame::upcall_stub_frame_is_first() const {
  assert(is_upcall_stub_frame(), "must be optimzed entry frame");
  UpcallStub* blob = _cb->as_upcall_stub();
  JavaFrameAnchor* jfa = blob->jfa_for_frame(*this);
  return jfa->last_Java_sp() == nullptr;
}

frame frame::sender_for_upcall_stub_frame(RegisterMap* map) const {
  assert(map != nullptr, "map must be set");
  UpcallStub* blob = _cb->as_upcall_stub();
  // Java frame called from C; skip all C frames and return top C
  // frame of that chunk as the sender
  JavaFrameAnchor* jfa = blob->jfa_for_frame(*this);
  assert(!upcall_stub_frame_is_first(), "must have a frame anchor to go back to");
  assert(jfa->last_Java_sp() > sp(), "must be above this frame on stack");
  map->clear();
  assert(map->include_argument_oops(), "should be set by clear");
  frame fr(jfa->last_Java_sp(), jfa->last_Java_pc());

  return fr;
}

frame frame::sender_for_interpreter_frame(RegisterMap *map) const {
  // This is the sp before any possible extension (adapter/locals).
  intptr_t* unextended_sp = interpreter_frame_sender_sp();
  address sender_pc = this->sender_pc();
  if (Continuation::is_return_barrier_entry(sender_pc)) {
    if (map->walk_cont()) { // about to walk into an h-stack
      return Continuation::top_frame(*this, map);
    } else {
      return Continuation::continuation_bottom_sender(map->thread(), *this, sender_sp());
    }
  }

  return frame(sender_sp(), sender_pc, unextended_sp);
}

// locals

void frame::interpreter_frame_set_locals(intptr_t* locs)  {
  assert(is_interpreted_frame(), "interpreted frame expected");
  // set relativized locals
  *addr_at(ijava_idx(locals)) = (intptr_t) (locs - fp());
}

// sender_sp

intptr_t* frame::interpreter_frame_sender_sp() const {
  assert(is_interpreted_frame(), "interpreted frame expected");
  return (intptr_t*)at(ijava_idx(sender_sp));
}

void frame::patch_pc(Thread* thread, address pc) {
  assert(_cb == CodeCache::find_blob(pc), "unexpected pc");
  address* pc_addr = (address*)&(own_abi()->lr);

  if (TracePcPatching) {
    tty->print_cr("patch_pc at address " PTR_FORMAT " [" PTR_FORMAT " -> " PTR_FORMAT "]",
                  p2i(&((address*) _sp)[-1]), p2i(((address*) _sp)[-1]), p2i(pc));
  }
  assert(!Continuation::is_return_barrier_entry(*pc_addr), "return barrier");
  assert(_pc == *pc_addr || pc == *pc_addr || 0 == *pc_addr,
         "must be (pc: " INTPTR_FORMAT " _pc: " INTPTR_FORMAT " pc_addr: " INTPTR_FORMAT
         " *pc_addr: " INTPTR_FORMAT  " sp: " INTPTR_FORMAT ")",
         p2i(pc), p2i(_pc), p2i(pc_addr), p2i(*pc_addr), p2i(sp()));
  DEBUG_ONLY(address old_pc = _pc;)
  own_abi()->lr = (uint64_t)pc;
  _pc = pc; // must be set before call to get_deopt_original_pc
  address original_pc = CompiledMethod::get_deopt_original_pc(this);
  if (original_pc != nullptr) {
    assert(original_pc == old_pc, "expected original PC to be stored before patching");
    _deopt_state = is_deoptimized;
    _pc = original_pc;
  } else {
    _deopt_state = not_deoptimized;
  }
  assert(!is_compiled_frame() || !_cb->as_compiled_method()->is_deopt_entry(_pc), "must be");

#ifdef ASSERT
  {
    frame f(this->sp(), pc, this->unextended_sp());
    assert(f.is_deoptimized_frame() == this->is_deoptimized_frame() && f.pc() == this->pc() && f.raw_pc() == this->raw_pc(),
           "must be (f.is_deoptimized_frame(): %d this->is_deoptimized_frame(): %d "
           "f.pc(): " INTPTR_FORMAT " this->pc(): " INTPTR_FORMAT " f.raw_pc(): " INTPTR_FORMAT " this->raw_pc(): " INTPTR_FORMAT ")",
           f.is_deoptimized_frame(), this->is_deoptimized_frame(), p2i(f.pc()), p2i(this->pc()), p2i(f.raw_pc()), p2i(this->raw_pc()));
  }
#endif
}

bool frame::is_interpreted_frame_valid(JavaThread* thread) const {
  assert(is_interpreted_frame(), "Not an interpreted frame");
  // These are reasonable sanity checks
  if (fp() == 0 || (intptr_t(fp()) & (wordSize-1)) != 0) {
    return false;
  }
  if (sp() == 0 || (intptr_t(sp()) & (wordSize-1)) != 0) {
    return false;
  }
  int min_frame_slots = (parent_ijava_frame_abi_size + ijava_state_size) / sizeof(intptr_t);
  if (fp() - min_frame_slots < sp()) {
    return false;
  }
  // These are hacks to keep us out of trouble.
  // The problem with these is that they mask other problems
  if (fp() <= sp()) {        // this attempts to deal with unsigned comparison above
    return false;
  }

  // do some validation of frame elements

  // first the method

  Method* m = safe_interpreter_frame_method();

  // validate the method we'd find in this potential sender
  if (!Method::is_valid_method(m)) return false;

  // stack frames shouldn't be much larger than max_stack elements
  // this test requires the use of unextended_sp which is the sp as seen by
  // the current frame, and not sp which is the "raw" pc which could point
  // further because of local variables of the callee method inserted after
  // method arguments
  if (fp() - unextended_sp() > 1024 + m->max_stack()*Interpreter::stackElementSize) {
    return false;
  }

  // validate bci/bcx

  address  bcp    = interpreter_frame_bcp();
  if (m->validate_bci_from_bcp(bcp) < 0) {
    return false;
  }

  // validate constantPoolCache*
  ConstantPoolCache* cp = *interpreter_frame_cache_addr();
  if (MetaspaceObj::is_valid(cp) == false) return false;

  // validate locals

  address locals =  (address)interpreter_frame_locals();
  return thread->is_in_stack_range_incl(locals, (address)fp());
}

BasicType frame::interpreter_frame_result(oop* oop_result, jvalue* value_result) {
  assert(is_interpreted_frame(), "interpreted frame expected");
  Method* method = interpreter_frame_method();
  BasicType type = method->result_type();

  if (method->is_native()) {
    // Prior to calling into the runtime to notify the method exit the possible
    // result value is saved into the interpreter frame.
    address lresult = (address)&(get_ijava_state()->lresult);
    address fresult = (address)&(get_ijava_state()->fresult);

    switch (method->result_type()) {
      case T_OBJECT:
      case T_ARRAY: {
        *oop_result = JNIHandles::resolve(*(jobject*)lresult);
        break;
      }
      // We use std/stfd to store the values.
      case T_BOOLEAN : value_result->z = (jboolean) *(unsigned long*)lresult; break;
      case T_INT     : value_result->i = (jint)     *(long*)lresult;          break;
      case T_CHAR    : value_result->c = (jchar)    *(unsigned long*)lresult; break;
      case T_SHORT   : value_result->s = (jshort)   *(long*)lresult;          break;
      case T_BYTE    : value_result->z = (jbyte)    *(long*)lresult;          break;
      case T_LONG    : value_result->j = (jlong)    *(long*)lresult;          break;
      case T_FLOAT   : value_result->f = (jfloat)   *(double*)fresult;        break;
      case T_DOUBLE  : value_result->d = (jdouble)  *(double*)fresult;        break;
      case T_VOID    : /* Nothing to do */ break;
      default        : ShouldNotReachHere();
    }
  } else {
    intptr_t* tos_addr = interpreter_frame_tos_address();
    switch (method->result_type()) {
      case T_OBJECT:
      case T_ARRAY: {
        oop obj = *(oop*)tos_addr;
        assert(Universe::is_in_heap_or_null(obj), "sanity check");
        *oop_result = obj;
      }
      case T_BOOLEAN : value_result->z = (jboolean) *(jint*)tos_addr; break;
      case T_BYTE    : value_result->b = (jbyte) *(jint*)tos_addr; break;
      case T_CHAR    : value_result->c = (jchar) *(jint*)tos_addr; break;
      case T_SHORT   : value_result->s = (jshort) *(jint*)tos_addr; break;
      case T_INT     : value_result->i = *(jint*)tos_addr; break;
      case T_LONG    : value_result->j = *(jlong*)tos_addr; break;
      case T_FLOAT   : value_result->f = *(jfloat*)tos_addr; break;
      case T_DOUBLE  : value_result->d = *(jdouble*)tos_addr; break;
      case T_VOID    : /* Nothing to do */ break;
      default        : ShouldNotReachHere();
    }
  }
  return type;
}

#ifndef PRODUCT

void frame::describe_pd(FrameValues& values, int frame_no) {
  if (is_interpreted_frame()) {
#define DESCRIBE_ADDRESS(name) \
  values.describe(frame_no, (intptr_t*)&(get_ijava_state()->name), #name);

      DESCRIBE_ADDRESS(method);
      DESCRIBE_ADDRESS(mirror);
      DESCRIBE_ADDRESS(locals);
      DESCRIBE_ADDRESS(monitors);
      DESCRIBE_ADDRESS(cpoolCache);
      DESCRIBE_ADDRESS(bcp);
      DESCRIBE_ADDRESS(esp);
      DESCRIBE_ADDRESS(mdx);
      DESCRIBE_ADDRESS(top_frame_sp);
      DESCRIBE_ADDRESS(sender_sp);
      DESCRIBE_ADDRESS(oop_tmp);
      DESCRIBE_ADDRESS(lresult);
      DESCRIBE_ADDRESS(fresult);
  }

  if (is_java_frame() || Continuation::is_continuation_enterSpecial(*this)) {
    intptr_t* ret_pc_loc = (intptr_t*)&own_abi()->lr;
    address ret_pc = *(address*)ret_pc_loc;
    values.describe(frame_no, ret_pc_loc,
      Continuation::is_return_barrier_entry(ret_pc) ? "return address (return barrier)" : "return address");
  }
}
#endif

intptr_t *frame::initial_deoptimization_info() {
  // `this` is the caller of the deoptee. We want to trim it, if compiled, to
  // unextended_sp. This is necessary if the deoptee frame is the bottom frame
  // of a continuation on stack (more frames could be in a StackChunk) as it
  // will pop its stack args. Otherwise the recursion in
  // FreezeBase::recurse_freeze_java_frame() would not stop at the bottom frame.
  return is_compiled_frame() ? unextended_sp() : sp();
}

#ifndef PRODUCT
// This is a generic constructor which is only used by pns() in debug.cpp.
// fp is dropped and gets determined by backlink.
frame::frame(void* sp, void* fp, void* pc) : frame((intptr_t*)sp, (address)pc) {}
#endif

// Pointer beyond the "oldest/deepest" BasicObjectLock on stack.
BasicObjectLock* frame::interpreter_frame_monitor_end() const {
  BasicObjectLock* result = (BasicObjectLock*) at_relative(ijava_idx(monitors));
  // make sure the pointer points inside the frame
  assert(sp() <= (intptr_t*) result, "monitor end should be above the stack pointer");
  assert((intptr_t*) result < fp(),  "monitor end should be strictly below the frame pointer: result: " INTPTR_FORMAT " fp: " INTPTR_FORMAT, p2i(result), p2i(fp()));
  return result;
}

intptr_t* frame::interpreter_frame_tos_at(jint offset) const {
  return &interpreter_frame_tos_address()[offset];
}
