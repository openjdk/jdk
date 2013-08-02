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

#include "precompiled.hpp"
#include "interpreter/interpreter.hpp"
#include "memory/resourceArea.hpp"
#include "oops/markOop.hpp"
#include "oops/method.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/monitorChunk.hpp"
#include "runtime/signature.hpp"
#include "runtime/stubCodeGenerator.hpp"
#include "runtime/stubRoutines.hpp"
#include "vmreg_ppc.inline.hpp"
#ifdef COMPILER1
#include "c1/c1_Runtime1.hpp"
#include "runtime/vframeArray.hpp"
#endif

#ifndef CC_INTERP
#error "CC_INTERP must be defined on PPC64"
#endif

#ifdef ASSERT
void RegisterMap::check_location_valid() {
}
#endif // ASSERT

bool frame::safe_for_sender(JavaThread *thread) {
  bool safe = false;
  address   cursp = (address)sp();
  address   curfp = (address)fp();
  if ((cursp != NULL && curfp != NULL &&
      (cursp <= thread->stack_base() && cursp >= thread->stack_base() - thread->stack_size())) &&
      (curfp <= thread->stack_base() && curfp >= thread->stack_base() - thread->stack_size())) {
      safe = true;
  }
  return safe;
}

bool frame::is_interpreted_frame() const  {
  return Interpreter::contains(pc());
}

frame frame::sender_for_entry_frame(RegisterMap *map) const {
  assert(map != NULL, "map must be set");
  // Java frame called from C; skip all C frames and return top C
  // frame of that chunk as the sender.
  JavaFrameAnchor* jfa = entry_frame_call_wrapper()->anchor();
  assert(!entry_frame_is_first(), "next Java fp must be non zero");
  assert(jfa->last_Java_sp() > _sp, "must be above this frame on stack");
  map->clear();
  assert(map->include_argument_oops(), "should be set by clear");

  if (jfa->last_Java_pc() != NULL) {
    frame fr(jfa->last_Java_sp(), jfa->last_Java_pc());
    return fr;
  }
  // Last_java_pc is not set, if we come here from compiled code. The
  // constructor retrieves the PC from the stack.
  frame fr(jfa->last_Java_sp());
  return fr;
}

frame frame::sender_for_interpreter_frame(RegisterMap *map) const {
  // Pass callers initial_caller_sp as unextended_sp.
  return frame(sender_sp(), sender_pc(), (intptr_t*)((parent_ijava_frame_abi *)callers_abi())->initial_caller_sp);
}

frame frame::sender_for_compiled_frame(RegisterMap *map) const {
  assert(map != NULL, "map must be set");

  // Frame owned by compiler.
  address pc = *compiled_sender_pc_addr(_cb);
  frame caller(compiled_sender_sp(_cb), pc);

  // Now adjust the map.

  // Get the rest.
  if (map->update_map()) {
    // Tell GC to use argument oopmaps for some runtime stubs that need it.
    map->set_include_argument_oops(_cb->caller_must_gc_arguments(map->thread()));
    if (_cb->oop_maps() != NULL) {
      OopMapSet::update_register_map(this, map);
    }
  }

  return caller;
}

intptr_t* frame::compiled_sender_sp(CodeBlob* cb) const {
  return sender_sp();
}

address* frame::compiled_sender_pc_addr(CodeBlob* cb) const {
  return sender_pc_addr();
}

frame frame::sender(RegisterMap* map) const {
  // Default is we do have to follow them. The sender_for_xxx will
  // update it accordingly.
  map->set_include_argument_oops(false);

  if (is_entry_frame())       return sender_for_entry_frame(map);
  if (is_interpreted_frame()) return sender_for_interpreter_frame(map);
  assert(_cb == CodeCache::find_blob(pc()),"Must be the same");

  if (_cb != NULL) {
    return sender_for_compiled_frame(map);
  }
  // Must be native-compiled frame, i.e. the marshaling code for native
  // methods that exists in the core system.
  return frame(sender_sp(), sender_pc());
}

void frame::patch_pc(Thread* thread, address pc) {
  if (TracePcPatching) {
    tty->print_cr("patch_pc at address " PTR_FORMAT " [" PTR_FORMAT " -> " PTR_FORMAT "]",
                  &((address*) _sp)[-1], ((address*) _sp)[-1], pc);
  }
  own_abi()->lr = (uint64_t)pc;
  _cb = CodeCache::find_blob(pc);
  if (_cb != NULL && _cb->is_nmethod() && ((nmethod*)_cb)->is_deopt_pc(_pc)) {
    address orig = (((nmethod*)_cb)->get_original_pc(this));
    assert(orig == _pc, "expected original to be stored before patching");
    _deopt_state = is_deoptimized;
    // Leave _pc as is.
  } else {
    _deopt_state = not_deoptimized;
    _pc = pc;
  }
}

void frame::pd_gc_epilog() {
  if (is_interpreted_frame()) {
    // Set constant pool cache entry for interpreter.
    Method* m = interpreter_frame_method();

    *interpreter_frame_cpoolcache_addr() = m->constants()->cache();
  }
}

bool frame::is_interpreted_frame_valid(JavaThread* thread) const {
  // Is there anything to do?
  assert(is_interpreted_frame(), "Not an interpreted frame");
  return true;
}

BasicType frame::interpreter_frame_result(oop* oop_result, jvalue* value_result) {
  assert(is_interpreted_frame(), "interpreted frame expected");
  Method* method = interpreter_frame_method();
  BasicType type = method->result_type();

#ifdef CC_INTERP
  if (method->is_native()) {
    // Prior to calling into the runtime to notify the method exit the possible
    // result value is saved into the interpreter frame.
    interpreterState istate = get_interpreterState();
    address lresult = (address)istate + in_bytes(BytecodeInterpreter::native_lresult_offset());
    address fresult = (address)istate + in_bytes(BytecodeInterpreter::native_fresult_offset());

    switch (method->result_type()) {
      case T_OBJECT:
      case T_ARRAY: {
        oop* obj_p = *(oop**)lresult;
        oop obj = (obj_p == NULL) ? NULL : *obj_p;
        assert(obj == NULL || Universe::heap()->is_in(obj), "sanity check");
        *oop_result = obj;
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
        assert(obj == NULL || Universe::heap()->is_in(obj), "sanity check");
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
#else
  Unimplemented();
#endif
  return type;
}

#ifndef PRODUCT

void frame::describe_pd(FrameValues& values, int frame_no) {
  if (is_interpreted_frame()) {
#ifdef CC_INTERP
    interpreterState istate = get_interpreterState();
    values.describe(frame_no, (intptr_t*)istate, "istate");
    values.describe(frame_no, (intptr_t*)&(istate->_thread), " thread");
    values.describe(frame_no, (intptr_t*)&(istate->_bcp), " bcp");
    values.describe(frame_no, (intptr_t*)&(istate->_locals), " locals");
    values.describe(frame_no, (intptr_t*)&(istate->_constants), " constants");
    values.describe(frame_no, (intptr_t*)&(istate->_method), err_msg(" method = %s", istate->_method->name_and_sig_as_C_string()));
    values.describe(frame_no, (intptr_t*)&(istate->_mdx), " mdx");
    values.describe(frame_no, (intptr_t*)&(istate->_stack), " stack");
    values.describe(frame_no, (intptr_t*)&(istate->_msg), err_msg(" msg = %s", BytecodeInterpreter::C_msg(istate->_msg)));
    values.describe(frame_no, (intptr_t*)&(istate->_result), " result");
    values.describe(frame_no, (intptr_t*)&(istate->_prev_link), " prev_link");
    values.describe(frame_no, (intptr_t*)&(istate->_oop_temp), " oop_temp");
    values.describe(frame_no, (intptr_t*)&(istate->_stack_base), " stack_base");
    values.describe(frame_no, (intptr_t*)&(istate->_stack_limit), " stack_limit");
    values.describe(frame_no, (intptr_t*)&(istate->_monitor_base), " monitor_base");
    values.describe(frame_no, (intptr_t*)&(istate->_frame_bottom), " frame_bottom");
    values.describe(frame_no, (intptr_t*)&(istate->_last_Java_pc), " last_Java_pc");
    values.describe(frame_no, (intptr_t*)&(istate->_last_Java_fp), " last_Java_fp");
    values.describe(frame_no, (intptr_t*)&(istate->_last_Java_sp), " last_Java_sp");
    values.describe(frame_no, (intptr_t*)&(istate->_self_link), " self_link");
    values.describe(frame_no, (intptr_t*)&(istate->_native_fresult), " native_fresult");
    values.describe(frame_no, (intptr_t*)&(istate->_native_lresult), " native_lresult");
#else
    Unimplemented();
#endif
  }
}
#endif

void frame::adjust_unextended_sp() {
  // If we are returning to a compiled MethodHandle call site, the
  // saved_fp will in fact be a saved value of the unextended SP. The
  // simplest way to tell whether we are returning to such a call site
  // is as follows:

  if (is_compiled_frame() && false /*is_at_mh_callsite()*/) {  // TODO PPC port
    // If the sender PC is a deoptimization point, get the original
    // PC. For MethodHandle call site the unextended_sp is stored in
    // saved_fp.
    _unextended_sp = _fp - _cb->frame_size();

#ifdef ASSERT
    nmethod *sender_nm = _cb->as_nmethod_or_null();
    assert(sender_nm && *_sp == *_unextended_sp, "backlink changed");

    intptr_t* sp = _unextended_sp;  // check if stack can be walked from here
    for (int x = 0; x < 5; ++x) {   // check up to a couple of backlinks
      intptr_t* prev_sp = *(intptr_t**)sp;
      if (prev_sp == 0) break;      // end of stack
      assert(prev_sp>sp, "broken stack");
      sp = prev_sp;
    }

    if (sender_nm->is_deopt_mh_entry(_pc)) { // checks for deoptimization
      address original_pc = sender_nm->get_original_pc(this);
      assert(sender_nm->insts_contains(original_pc), "original PC must be in nmethod");
      assert(sender_nm->is_method_handle_return(original_pc), "must be");
    }
#endif
  }
}

intptr_t *frame::initial_deoptimization_info() {
  // unused... but returns fp() to minimize changes introduced by 7087445
  return fp();
}
