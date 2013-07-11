/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "interpreter/interpreter.hpp"
#include "memory/resourceArea.hpp"
#include "oops/markOop.hpp"
#include "oops/method.hpp"
#include "oops/oop.inline.hpp"
#include "prims/methodHandles.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/monitorChunk.hpp"
#include "runtime/os.hpp"
#include "runtime/signature.hpp"
#include "runtime/stubCodeGenerator.hpp"
#include "runtime/stubRoutines.hpp"
#include "vmreg_x86.inline.hpp"
#ifdef COMPILER1
#include "c1/c1_Runtime1.hpp"
#include "runtime/vframeArray.hpp"
#endif

#ifdef ASSERT
void RegisterMap::check_location_valid() {
}
#endif


// Profiling/safepoint support

bool frame::safe_for_sender(JavaThread *thread) {
  address   sp = (address)_sp;
  address   fp = (address)_fp;
  address   unextended_sp = (address)_unextended_sp;

  // consider stack guards when trying to determine "safe" stack pointers
  static size_t stack_guard_size = os::uses_stack_guard_pages() ? (StackYellowPages + StackRedPages) * os::vm_page_size() : 0;
  size_t usable_stack_size = thread->stack_size() - stack_guard_size;

  // sp must be within the usable part of the stack (not in guards)
  bool sp_safe = (sp < thread->stack_base()) &&
                 (sp >= thread->stack_base() - usable_stack_size);


  if (!sp_safe) {
    return false;
  }

  // unextended sp must be within the stack and above or equal sp
  bool unextended_sp_safe = (unextended_sp < thread->stack_base()) &&
                            (unextended_sp >= sp);

  if (!unextended_sp_safe) {
    return false;
  }

  // an fp must be within the stack and above (but not equal) sp
  // second evaluation on fp+ is added to handle situation where fp is -1
  bool fp_safe = (fp < thread->stack_base() && (fp > sp) && (((fp + (return_addr_offset * sizeof(void*))) < thread->stack_base())));

  // We know sp/unextended_sp are safe only fp is questionable here

  // If the current frame is known to the code cache then we can attempt to
  // to construct the sender and do some validation of it. This goes a long way
  // toward eliminating issues when we get in frame construction code

  if (_cb != NULL ) {

    // First check if frame is complete and tester is reliable
    // Unfortunately we can only check frame complete for runtime stubs and nmethod
    // other generic buffer blobs are more problematic so we just assume they are
    // ok. adapter blobs never have a frame complete and are never ok.

    // check for a valid frame_size, otherwise we are unlikely to get a valid sender_pc

    if (!Interpreter::contains(_pc) && _cb->frame_size() <= 0) {
      //assert(0, "Invalid frame_size");
      return false;
    }

    if (!_cb->is_frame_complete_at(_pc)) {
      if (_cb->is_nmethod() || _cb->is_adapter_blob() || _cb->is_runtime_stub()) {
        return false;
      }
    }

    // Could just be some random pointer within the codeBlob
    if (!_cb->code_contains(_pc)) {
      return false;
    }

    // Entry frame checks
    if (is_entry_frame()) {
      // an entry frame must have a valid fp.

      if (!fp_safe) return false;

      // Validate the JavaCallWrapper an entry frame must have

      address jcw = (address)entry_frame_call_wrapper();

      bool jcw_safe = (jcw < thread->stack_base()) && ( jcw > fp);

      return jcw_safe;

    }

    intptr_t* sender_sp = NULL;
    address   sender_pc = NULL;

    if (is_interpreted_frame()) {
      // fp must be safe
      if (!fp_safe) {
        return false;
      }

      sender_pc = (address) this->fp()[return_addr_offset];
      sender_sp = (intptr_t*) addr_at(sender_sp_offset);

    } else {
      // must be some sort of compiled/runtime frame
      // fp does not have to be safe (although it could be check for c1?)

      sender_sp = _unextended_sp + _cb->frame_size();
      // On Intel the return_address is always the word on the stack
      sender_pc = (address) *(sender_sp-1);
    }


    // If the potential sender is the interpreter then we can do some more checking
    if (Interpreter::contains(sender_pc)) {

      // ebp is always saved in a recognizable place in any code we generate. However
      // only if the sender is interpreted/call_stub (c1 too?) are we certain that the saved ebp
      // is really a frame pointer.

      intptr_t *saved_fp = (intptr_t*)*(sender_sp - frame::sender_sp_offset);
      bool saved_fp_safe = ((address)saved_fp < thread->stack_base()) && (saved_fp > sender_sp);

      if (!saved_fp_safe) {
        return false;
      }

      // construct the potential sender

      frame sender(sender_sp, saved_fp, sender_pc);

      return sender.is_interpreted_frame_valid(thread);

    }

    // We must always be able to find a recognizable pc
    CodeBlob* sender_blob = CodeCache::find_blob_unsafe(sender_pc);
    if (sender_pc == NULL ||  sender_blob == NULL) {
      return false;
    }

    // Could be a zombie method
    if (sender_blob->is_zombie() || sender_blob->is_unloaded()) {
      return false;
    }

    // Could just be some random pointer within the codeBlob
    if (!sender_blob->code_contains(sender_pc)) {
      return false;
    }

    // We should never be able to see an adapter if the current frame is something from code cache
    if (sender_blob->is_adapter_blob()) {
      return false;
    }

    // Could be the call_stub
    if (StubRoutines::returns_to_call_stub(sender_pc)) {
      intptr_t *saved_fp = (intptr_t*)*(sender_sp - frame::sender_sp_offset);
      bool saved_fp_safe = ((address)saved_fp < thread->stack_base()) && (saved_fp > sender_sp);

      if (!saved_fp_safe) {
        return false;
      }

      // construct the potential sender

      frame sender(sender_sp, saved_fp, sender_pc);

      // Validate the JavaCallWrapper an entry frame must have
      address jcw = (address)sender.entry_frame_call_wrapper();

      bool jcw_safe = (jcw < thread->stack_base()) && ( jcw > (address)sender.fp());

      return jcw_safe;
    }

    if (sender_blob->is_nmethod()) {
        nmethod* nm = sender_blob->as_nmethod_or_null();
        if (nm != NULL) {
            if (nm->is_deopt_mh_entry(sender_pc) || nm->is_deopt_entry(sender_pc)) {
                return false;
            }
        }
    }

    // If the frame size is 0 something (or less) is bad because every nmethod has a non-zero frame size
    // because the return address counts against the callee's frame.

    if (sender_blob->frame_size() <= 0) {
      assert(!sender_blob->is_nmethod(), "should count return address at least");
      return false;
    }

    // We should never be able to see anything here except an nmethod. If something in the
    // code cache (current frame) is called by an entity within the code cache that entity
    // should not be anything but the call stub (already covered), the interpreter (already covered)
    // or an nmethod.

    if (!sender_blob->is_nmethod()) {
        return false;
    }

    // Could put some more validation for the potential non-interpreted sender
    // frame we'd create by calling sender if I could think of any. Wait for next crash in forte...

    // One idea is seeing if the sender_pc we have is one that we'd expect to call to current cb

    // We've validated the potential sender that would be created
    return true;
  }

  // Must be native-compiled frame. Since sender will try and use fp to find
  // linkages it must be safe

  if (!fp_safe) {
    return false;
  }

  // Will the pc we fetch be non-zero (which we'll find at the oldest frame)

  if ( (address) this->fp()[return_addr_offset] == NULL) return false;


  // could try and do some more potential verification of native frame if we could think of some...

  return true;

}


void frame::patch_pc(Thread* thread, address pc) {
  address* pc_addr = &(((address*) sp())[-1]);
  if (TracePcPatching) {
    tty->print_cr("patch_pc at address " INTPTR_FORMAT " [" INTPTR_FORMAT " -> " INTPTR_FORMAT "]",
                  pc_addr, *pc_addr, pc);
  }
  // Either the return address is the original one or we are going to
  // patch in the same address that's already there.
  assert(_pc == *pc_addr || pc == *pc_addr, "must be");
  *pc_addr = pc;
  _cb = CodeCache::find_blob(pc);
  address original_pc = nmethod::get_deopt_original_pc(this);
  if (original_pc != NULL) {
    assert(original_pc == _pc, "expected original PC to be stored before patching");
    _deopt_state = is_deoptimized;
    // leave _pc as is
  } else {
    _deopt_state = not_deoptimized;
    _pc = pc;
  }
}

bool frame::is_interpreted_frame() const  {
  return Interpreter::contains(pc());
}

int frame::frame_size(RegisterMap* map) const {
  frame sender = this->sender(map);
  return sender.sp() - sp();
}

intptr_t* frame::entry_frame_argument_at(int offset) const {
  // convert offset to index to deal with tsi
  int index = (Interpreter::expr_offset_in_bytes(offset)/wordSize);
  // Entry frame's arguments are always in relation to unextended_sp()
  return &unextended_sp()[index];
}

// sender_sp
#ifdef CC_INTERP
intptr_t* frame::interpreter_frame_sender_sp() const {
  assert(is_interpreted_frame(), "interpreted frame expected");
  // QQQ why does this specialize method exist if frame::sender_sp() does same thing?
  // seems odd and if we always know interpreted vs. non then sender_sp() is really
  // doing too much work.
  return get_interpreterState()->sender_sp();
}

// monitor elements

BasicObjectLock* frame::interpreter_frame_monitor_begin() const {
  return get_interpreterState()->monitor_base();
}

BasicObjectLock* frame::interpreter_frame_monitor_end() const {
  return (BasicObjectLock*) get_interpreterState()->stack_base();
}

#else // CC_INTERP

intptr_t* frame::interpreter_frame_sender_sp() const {
  assert(is_interpreted_frame(), "interpreted frame expected");
  return (intptr_t*) at(interpreter_frame_sender_sp_offset);
}

void frame::set_interpreter_frame_sender_sp(intptr_t* sender_sp) {
  assert(is_interpreted_frame(), "interpreted frame expected");
  ptr_at_put(interpreter_frame_sender_sp_offset, (intptr_t) sender_sp);
}


// monitor elements

BasicObjectLock* frame::interpreter_frame_monitor_begin() const {
  return (BasicObjectLock*) addr_at(interpreter_frame_monitor_block_bottom_offset);
}

BasicObjectLock* frame::interpreter_frame_monitor_end() const {
  BasicObjectLock* result = (BasicObjectLock*) *addr_at(interpreter_frame_monitor_block_top_offset);
  // make sure the pointer points inside the frame
  assert(sp() <= (intptr_t*) result, "monitor end should be above the stack pointer");
  assert((intptr_t*) result < fp(),  "monitor end should be strictly below the frame pointer");
  return result;
}

void frame::interpreter_frame_set_monitor_end(BasicObjectLock* value) {
  *((BasicObjectLock**)addr_at(interpreter_frame_monitor_block_top_offset)) = value;
}

// Used by template based interpreter deoptimization
void frame::interpreter_frame_set_last_sp(intptr_t* sp) {
    *((intptr_t**)addr_at(interpreter_frame_last_sp_offset)) = sp;
}
#endif // CC_INTERP

frame frame::sender_for_entry_frame(RegisterMap* map) const {
  assert(map != NULL, "map must be set");
  // Java frame called from C; skip all C frames and return top C
  // frame of that chunk as the sender
  JavaFrameAnchor* jfa = entry_frame_call_wrapper()->anchor();
  assert(!entry_frame_is_first(), "next Java fp must be non zero");
  assert(jfa->last_Java_sp() > sp(), "must be above this frame on stack");
  map->clear();
  assert(map->include_argument_oops(), "should be set by clear");
  if (jfa->last_Java_pc() != NULL ) {
    frame fr(jfa->last_Java_sp(), jfa->last_Java_fp(), jfa->last_Java_pc());
    return fr;
  }
  frame fr(jfa->last_Java_sp(), jfa->last_Java_fp());
  return fr;
}

//------------------------------------------------------------------------------
// frame::verify_deopt_original_pc
//
// Verifies the calculated original PC of a deoptimization PC for the
// given unextended SP.  The unextended SP might also be the saved SP
// for MethodHandle call sites.
#ifdef ASSERT
void frame::verify_deopt_original_pc(nmethod* nm, intptr_t* unextended_sp, bool is_method_handle_return) {
  frame fr;

  // This is ugly but it's better than to change {get,set}_original_pc
  // to take an SP value as argument.  And it's only a debugging
  // method anyway.
  fr._unextended_sp = unextended_sp;

  address original_pc = nm->get_original_pc(&fr);
  assert(nm->insts_contains(original_pc), "original PC must be in nmethod");
  assert(nm->is_method_handle_return(original_pc) == is_method_handle_return, "must be");
}
#endif

//------------------------------------------------------------------------------
// frame::adjust_unextended_sp
void frame::adjust_unextended_sp() {
  // If we are returning to a compiled MethodHandle call site, the
  // saved_fp will in fact be a saved value of the unextended SP.  The
  // simplest way to tell whether we are returning to such a call site
  // is as follows:

  nmethod* sender_nm = (_cb == NULL) ? NULL : _cb->as_nmethod_or_null();
  if (sender_nm != NULL) {
    // If the sender PC is a deoptimization point, get the original
    // PC.  For MethodHandle call site the unextended_sp is stored in
    // saved_fp.
    if (sender_nm->is_deopt_mh_entry(_pc)) {
      DEBUG_ONLY(verify_deopt_mh_original_pc(sender_nm, _fp));
      _unextended_sp = _fp;
    }
    else if (sender_nm->is_deopt_entry(_pc)) {
      DEBUG_ONLY(verify_deopt_original_pc(sender_nm, _unextended_sp));
    }
    else if (sender_nm->is_method_handle_return(_pc)) {
      _unextended_sp = _fp;
    }
  }
}

//------------------------------------------------------------------------------
// frame::update_map_with_saved_link
void frame::update_map_with_saved_link(RegisterMap* map, intptr_t** link_addr) {
  // The interpreter and compiler(s) always save EBP/RBP in a known
  // location on entry. We must record where that location is
  // so this if EBP/RBP was live on callout from c2 we can find
  // the saved copy no matter what it called.

  // Since the interpreter always saves EBP/RBP if we record where it is then
  // we don't have to always save EBP/RBP on entry and exit to c2 compiled
  // code, on entry will be enough.
  map->set_location(rbp->as_VMReg(), (address) link_addr);
#ifdef AMD64
  // this is weird "H" ought to be at a higher address however the
  // oopMaps seems to have the "H" regs at the same address and the
  // vanilla register.
  // XXXX make this go away
  if (true) {
    map->set_location(rbp->as_VMReg()->next(), (address) link_addr);
  }
#endif // AMD64
}


//------------------------------------------------------------------------------
// frame::sender_for_interpreter_frame
frame frame::sender_for_interpreter_frame(RegisterMap* map) const {
  // SP is the raw SP from the sender after adapter or interpreter
  // extension.
  intptr_t* sender_sp = this->sender_sp();

  // This is the sp before any possible extension (adapter/locals).
  intptr_t* unextended_sp = interpreter_frame_sender_sp();

#ifdef COMPILER2
  if (map->update_map()) {
    update_map_with_saved_link(map, (intptr_t**) addr_at(link_offset));
  }
#endif // COMPILER2

  return frame(sender_sp, unextended_sp, link(), sender_pc());
}


//------------------------------------------------------------------------------
// frame::sender_for_compiled_frame
frame frame::sender_for_compiled_frame(RegisterMap* map) const {
  assert(map != NULL, "map must be set");

  // frame owned by optimizing compiler
  assert(_cb->frame_size() >= 0, "must have non-zero frame size");
  intptr_t* sender_sp = unextended_sp() + _cb->frame_size();
  intptr_t* unextended_sp = sender_sp;

  // On Intel the return_address is always the word on the stack
  address sender_pc = (address) *(sender_sp-1);

  // This is the saved value of EBP which may or may not really be an FP.
  // It is only an FP if the sender is an interpreter frame (or C1?).
  intptr_t** saved_fp_addr = (intptr_t**) (sender_sp - frame::sender_sp_offset);

  if (map->update_map()) {
    // Tell GC to use argument oopmaps for some runtime stubs that need it.
    // For C1, the runtime stub might not have oop maps, so set this flag
    // outside of update_register_map.
    map->set_include_argument_oops(_cb->caller_must_gc_arguments(map->thread()));
    if (_cb->oop_maps() != NULL) {
      OopMapSet::update_register_map(this, map);
    }

    // Since the prolog does the save and restore of EBP there is no oopmap
    // for it so we must fill in its location as if there was an oopmap entry
    // since if our caller was compiled code there could be live jvm state in it.
    update_map_with_saved_link(map, saved_fp_addr);
  }

  assert(sender_sp != sp(), "must have changed");
  return frame(sender_sp, unextended_sp, *saved_fp_addr, sender_pc);
}


//------------------------------------------------------------------------------
// frame::sender
frame frame::sender(RegisterMap* map) const {
  // Default is we done have to follow them. The sender_for_xxx will
  // update it accordingly
  map->set_include_argument_oops(false);

  if (is_entry_frame())       return sender_for_entry_frame(map);
  if (is_interpreted_frame()) return sender_for_interpreter_frame(map);
  assert(_cb == CodeCache::find_blob(pc()),"Must be the same");

  if (_cb != NULL) {
    return sender_for_compiled_frame(map);
  }
  // Must be native-compiled frame, i.e. the marshaling code for native
  // methods that exists in the core system.
  return frame(sender_sp(), link(), sender_pc());
}


bool frame::interpreter_frame_equals_unpacked_fp(intptr_t* fp) {
  assert(is_interpreted_frame(), "must be interpreter frame");
  Method* method = interpreter_frame_method();
  // When unpacking an optimized frame the frame pointer is
  // adjusted with:
  int diff = (method->max_locals() - method->size_of_parameters()) *
             Interpreter::stackElementWords;
  return _fp == (fp - diff);
}

void frame::pd_gc_epilog() {
  // nothing done here now
}

bool frame::is_interpreted_frame_valid(JavaThread* thread) const {
// QQQ
#ifdef CC_INTERP
#else
  assert(is_interpreted_frame(), "Not an interpreted frame");
  // These are reasonable sanity checks
  if (fp() == 0 || (intptr_t(fp()) & (wordSize-1)) != 0) {
    return false;
  }
  if (sp() == 0 || (intptr_t(sp()) & (wordSize-1)) != 0) {
    return false;
  }
  if (fp() + interpreter_frame_initial_sp_offset < sp()) {
    return false;
  }
  // These are hacks to keep us out of trouble.
  // The problem with these is that they mask other problems
  if (fp() <= sp()) {        // this attempts to deal with unsigned comparison above
    return false;
  }

  // do some validation of frame elements

  // first the method

  Method* m = *interpreter_frame_method_addr();

  // validate the method we'd find in this potential sender
  if (!m->is_valid_method()) return false;

  // stack frames shouldn't be much larger than max_stack elements

  if (fp() - sp() > 1024 + m->max_stack()*Interpreter::stackElementSize) {
    return false;
  }

  // validate bci/bcx

  intptr_t  bcx    = interpreter_frame_bcx();
  if (m->validate_bci_from_bcx(bcx) < 0) {
    return false;
  }

  // validate ConstantPoolCache*
  ConstantPoolCache* cp = *interpreter_frame_cache_addr();
  if (cp == NULL || !cp->is_metaspace_object()) return false;

  // validate locals

  address locals =  (address) *interpreter_frame_locals_addr();

  if (locals > thread->stack_base() || locals < (address) fp()) return false;

  // We'd have to be pretty unlucky to be mislead at this point

#endif // CC_INTERP
  return true;
}

BasicType frame::interpreter_frame_result(oop* oop_result, jvalue* value_result) {
#ifdef CC_INTERP
  // Needed for JVMTI. The result should always be in the
  // interpreterState object
  interpreterState istate = get_interpreterState();
#endif // CC_INTERP
  assert(is_interpreted_frame(), "interpreted frame expected");
  Method* method = interpreter_frame_method();
  BasicType type = method->result_type();

  intptr_t* tos_addr;
  if (method->is_native()) {
    // Prior to calling into the runtime to report the method_exit the possible
    // return value is pushed to the native stack. If the result is a jfloat/jdouble
    // then ST0 is saved before EAX/EDX. See the note in generate_native_result
    tos_addr = (intptr_t*)sp();
    if (type == T_FLOAT || type == T_DOUBLE) {
    // QQQ seems like this code is equivalent on the two platforms
#ifdef AMD64
      // This is times two because we do a push(ltos) after pushing XMM0
      // and that takes two interpreter stack slots.
      tos_addr += 2 * Interpreter::stackElementWords;
#else
      tos_addr += 2;
#endif // AMD64
    }
  } else {
    tos_addr = (intptr_t*)interpreter_frame_tos_address();
  }

  switch (type) {
    case T_OBJECT  :
    case T_ARRAY   : {
      oop obj;
      if (method->is_native()) {
#ifdef CC_INTERP
        obj = istate->_oop_temp;
#else
        obj = (oop) at(interpreter_frame_oop_temp_offset);
#endif // CC_INTERP
      } else {
        oop* obj_p = (oop*)tos_addr;
        obj = (obj_p == NULL) ? (oop)NULL : *obj_p;
      }
      assert(obj == NULL || Universe::heap()->is_in(obj), "sanity check");
      *oop_result = obj;
      break;
    }
    case T_BOOLEAN : value_result->z = *(jboolean*)tos_addr; break;
    case T_BYTE    : value_result->b = *(jbyte*)tos_addr; break;
    case T_CHAR    : value_result->c = *(jchar*)tos_addr; break;
    case T_SHORT   : value_result->s = *(jshort*)tos_addr; break;
    case T_INT     : value_result->i = *(jint*)tos_addr; break;
    case T_LONG    : value_result->j = *(jlong*)tos_addr; break;
    case T_FLOAT   : {
#ifdef AMD64
        value_result->f = *(jfloat*)tos_addr;
#else
      if (method->is_native()) {
        jdouble d = *(jdouble*)tos_addr;  // Result was in ST0 so need to convert to jfloat
        value_result->f = (jfloat)d;
      } else {
        value_result->f = *(jfloat*)tos_addr;
      }
#endif // AMD64
      break;
    }
    case T_DOUBLE  : value_result->d = *(jdouble*)tos_addr; break;
    case T_VOID    : /* Nothing to do */ break;
    default        : ShouldNotReachHere();
  }

  return type;
}


intptr_t* frame::interpreter_frame_tos_at(jint offset) const {
  int index = (Interpreter::expr_offset_in_bytes(offset)/wordSize);
  return &interpreter_frame_tos_address()[index];
}

#ifndef PRODUCT

#define DESCRIBE_FP_OFFSET(name) \
  values.describe(frame_no, fp() + frame::name##_offset, #name)

void frame::describe_pd(FrameValues& values, int frame_no) {
  if (is_interpreted_frame()) {
    DESCRIBE_FP_OFFSET(interpreter_frame_sender_sp);
    DESCRIBE_FP_OFFSET(interpreter_frame_last_sp);
    DESCRIBE_FP_OFFSET(interpreter_frame_method);
    DESCRIBE_FP_OFFSET(interpreter_frame_mdx);
    DESCRIBE_FP_OFFSET(interpreter_frame_cache);
    DESCRIBE_FP_OFFSET(interpreter_frame_locals);
    DESCRIBE_FP_OFFSET(interpreter_frame_bcx);
    DESCRIBE_FP_OFFSET(interpreter_frame_initial_sp);
  }
}
#endif

intptr_t *frame::initial_deoptimization_info() {
  // used to reset the saved FP
  return fp();
}

intptr_t* frame::real_fp() const {
  if (_cb != NULL) {
    // use the frame size if valid
    int size = _cb->frame_size();
    if (size > 0) {
      return unextended_sp() + size;
    }
  }
  // else rely on fp()
  assert(! is_compiled_frame(), "unknown compiled frame size");
  return fp();
}
