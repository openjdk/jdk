/*
 * Copyright (c) 1997, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "classfile/vmSymbols.hpp"
#include "code/scopeDesc.hpp"
#include "code/vmreg.inline.hpp"
#include "interpreter/bytecode.hpp"
#include "interpreter/bytecode.inline.hpp"
#include "interpreter/interpreter.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/resourceArea.hpp"
#include "oops/methodData.hpp"
#include "oops/oop.inline.hpp"
#include "prims/jvmtiThreadState.hpp"
#include "prims/methodHandles.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/monitorChunk.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/synchronizer.hpp"
#include "runtime/vframe.hpp"
#include "runtime/vframe_hp.hpp"
#include "runtime/vframeArray.hpp"
#include "utilities/copy.hpp"
#include "utilities/events.hpp"

int vframeArrayElement:: bci(void) const { return (_bci == SynchronizationEntryBCI ? 0 : _bci); }

void vframeArrayElement::free_monitors() {
  if (_monitors != nullptr) {
     MonitorChunk* chunk = _monitors;
     _monitors = nullptr;
     delete chunk;
  }
}

void vframeArrayElement::fill_in(compiledVFrame* vf, bool realloc_failures) {

// Copy the information from the compiled vframe to the
// interpreter frame we will be creating to replace vf

  _method = vf->method();
  _bci    = vf->raw_bci();
  _reexecute = vf->should_reexecute(); // initial value, updated in unpack_on_stack
#if INCLUDE_JVMCI
  _rethrow = vf->scope()->rethrow_exception();
#endif
#ifdef ASSERT
  _removed_monitors = false;
#endif

  int index;

  {
    JavaThread* current_thread = JavaThread::current();
    ResourceMark rm(current_thread);
    HandleMark hm(current_thread);

    // Get the monitors off-stack

    GrowableArray<MonitorInfo*>* list = vf->monitors();
    if (list->is_empty()) {
      _monitors = nullptr;
    } else {

      // Allocate monitor chunk
      _monitors = new MonitorChunk(list->length());

      // Migrate the BasicLocks from the stack to the monitor chunk
      for (index = 0; index < list->length(); index++) {
        MonitorInfo* monitor = list->at(index);
        assert(!monitor->owner_is_scalar_replaced() || realloc_failures, "object should be reallocated already");
        BasicObjectLock* dest = _monitors->at(index);
        if (monitor->owner_is_scalar_replaced()) {
          dest->set_obj(nullptr);
        } else {
          assert(monitor->owner() != nullptr, "monitor owner must not be null");
          assert(!monitor->owner()->is_unlocked(), "monitor must be locked");
          dest->set_obj(monitor->owner());
          assert(ObjectSynchronizer::current_thread_holds_lock(current_thread, Handle(current_thread, dest->obj())),
                 "should be held, before move_to");

          monitor->lock()->move_to(monitor->owner(), dest->lock());

          assert(ObjectSynchronizer::current_thread_holds_lock(current_thread, Handle(current_thread, dest->obj())),
                 "should be held, after move_to");
        }
      }
    }
  }

  // Convert the vframe locals and expressions to off stack
  // values. Because we will not gc all oops can be converted to
  // intptr_t (i.e. a stack slot) and we are fine. This is
  // good since we are inside a HandleMark and the oops in our
  // collection would go away between packing them here and
  // unpacking them in unpack_on_stack.

  // First the locals go off-stack

  // FIXME this seems silly it creates a StackValueCollection
  // in order to get the size to then copy them and
  // convert the types to intptr_t size slots. Seems like it
  // could do it in place... Still uses less memory than the
  // old way though

  StackValueCollection *locs = vf->locals();
  _locals = new StackValueCollection(locs->size());
  for(index = 0; index < locs->size(); index++) {
    StackValue* value = locs->at(index);
    switch(value->type()) {
      case T_OBJECT:
        assert(!value->obj_is_scalar_replaced() || realloc_failures, "object should be reallocated already");
        // preserve object type
        _locals->add( new StackValue(cast_from_oop<intptr_t>((value->get_obj()())), T_OBJECT ));
        break;
      case T_CONFLICT:
        // A dead local.  Will be initialized to null/zero.
        _locals->add( new StackValue());
        break;
      case T_INT:
        _locals->add( new StackValue(value->get_intptr()));
        break;
      default:
        ShouldNotReachHere();
    }
  }

  // Now the expressions off-stack
  // Same silliness as above

  StackValueCollection *exprs = vf->expressions();
  _expressions = new StackValueCollection(exprs->size());
  for(index = 0; index < exprs->size(); index++) {
    StackValue* value = exprs->at(index);
    switch(value->type()) {
      case T_OBJECT:
        assert(!value->obj_is_scalar_replaced() || realloc_failures, "object should be reallocated already");
        // preserve object type
        _expressions->add( new StackValue(cast_from_oop<intptr_t>((value->get_obj()())), T_OBJECT ));
        break;
      case T_CONFLICT:
        // A dead stack element.  Will be initialized to null/zero.
        // This can occur when the compiler emits a state in which stack
        // elements are known to be dead (because of an imminent exception).
        _expressions->add( new StackValue());
        break;
      case T_INT:
        _expressions->add( new StackValue(value->get_intptr()));
        break;
      default:
        ShouldNotReachHere();
    }
  }
}

static int unpack_counter = 0;

bool vframeArrayElement::should_reexecute(bool is_top_frame, int exec_mode) const {
  if (is_top_frame) {
    switch (exec_mode) {
    case Deoptimization::Unpack_uncommon_trap:
    case Deoptimization::Unpack_reexecute:
      return true;
    case Deoptimization::Unpack_exception:
      assert(raw_bci() >= 0, "bad bci %d for Unpack_exception", raw_bci());
    default:
      break;
    }
  }
  if (raw_bci() == SynchronizationEntryBCI) {
    return true;
  }
  bool reexec = should_reexecute();
  assert(is_top_frame || reexec == false, "unexepected should_reexecute()");
#ifdef ASSERT
  if (!reexec) {
    address bcp = method()->bcp_from(bci());
    Bytecodes::Code code = Bytecodes::code_at(method(), bcp);
    assert(!Interpreter::bytecode_should_reexecute(code), "should_reexecute mismatch");
  }
#endif
  return reexec;
}

void vframeArrayElement::unpack_on_stack(int caller_actual_parameters,
                                         int callee_parameters,
                                         int callee_locals,
                                         frame* caller,
                                         bool is_top_frame,
                                         bool is_bottom_frame,
                                         int exec_mode) {
  JavaThread* thread = JavaThread::current();

  bool realloc_failure_exception = thread->frames_to_pop_failed_realloc() > 0;

  // Look at bci and decide on bcp and continuation pc
  address bcp;
  // C++ interpreter doesn't need a pc since it will figure out what to do when it
  // begins execution
  address pc;
  bool reexecute = should_reexecute(is_top_frame, exec_mode);
  if (is_top_frame && exec_mode == Deoptimization::Unpack_exception) {
    assert(raw_bci() >= 0, "bad bci %d for Unpack_exception", raw_bci());
    bcp = method()->bcp_from(bci());
    // exception is pending
    pc = Interpreter::rethrow_exception_entry();
    // [phh] We're going to end up in some handler or other, so it doesn't
    // matter what mdp we point to.  See exception_handler_for_exception()
    // in interpreterRuntime.cpp.
  } else if (raw_bci() == SynchronizationEntryBCI) {
    // We are deoptimizing while hanging in prologue code for synchronized method
    bcp = method()->bcp_from(0); // first byte code
    pc  = Interpreter::deopt_entry(vtos, 0); // step = 0 since we don't skip current bytecode
    assert(reexecute, "must be");
  } else if (reexecute) { //reexecute this bytecode
    assert(is_top_frame, "reexecute allowed only for the top frame");
    bcp = method()->bcp_from(bci());
    switch (exec_mode) {
    case Deoptimization::Unpack_uncommon_trap:
    case Deoptimization::Unpack_reexecute:
      // Do not special-case _athrow or _return_register_finalizer
      pc = Interpreter::deopt_entry(vtos, 0);
      break;
    default:
      // Yes, special-case _athrow and _return_register_finalizer
      pc = Interpreter::deopt_reexecute_entry(method(), bcp);
    }
  } else {
    bcp = method()->bcp_from(bci());
    assert(!reexecute, "must be");
    pc  = Interpreter::deopt_continue_after_entry(method(), bcp, callee_parameters, is_top_frame);
  }
  assert(Bytecodes::is_defined(*bcp), "must be a valid bytecode");

  // Monitorenter and pending exceptions:
  //
  // For Compiler2, there should be no pending exception when deoptimizing at monitorenter
  // because there is no safepoint at the null pointer check (it is either handled explicitly
  // or prior to the monitorenter) and asynchronous exceptions are not made "pending" by the
  // runtime interface for the slow case (see JRT_ENTRY_FOR_MONITORENTER).  If an asynchronous
  // exception was processed, the bytecode pointer would have to be extended one bytecode beyond
  // the monitorenter to place it in the proper exception range.
  //
  // For Compiler1, deoptimization can occur while throwing a NullPointerException at monitorenter,
  // in which case bcp should point to the monitorenter since it is within the exception's range.
  //
  // For realloc failure exception we just pop frames, skip the guarantee.

  assert(*bcp != Bytecodes::_monitorenter || is_top_frame, "a _monitorenter must be a top frame");
  assert(thread->deopt_compiled_method() != nullptr, "compiled method should be known");
  guarantee(realloc_failure_exception || !(thread->deopt_compiled_method()->is_compiled_by_c2() &&
              *bcp == Bytecodes::_monitorenter             &&
              exec_mode == Deoptimization::Unpack_exception),
            "shouldn't get exception during monitorenter");

  int popframe_preserved_args_size_in_bytes = 0;
  int popframe_preserved_args_size_in_words = 0;
  if (is_top_frame) {
    JvmtiThreadState *state = thread->jvmti_thread_state();
    if (JvmtiExport::can_pop_frame() &&
        (thread->has_pending_popframe() || thread->popframe_forcing_deopt_reexecution())) {
      if (thread->has_pending_popframe()) {
        // Pop top frame after deoptimization
        pc = Interpreter::remove_activation_preserving_args_entry();
      } else {
        // Reexecute invoke in top frame
        pc = Interpreter::deopt_entry(vtos, 0);
#ifdef ASSERT
        Bytecodes::Code code = Bytecodes::code_at(method(), bcp);
        assert(Bytecodes::is_invoke(code), "must be");
        assert(!reexecute, "must be");
#endif
        // It would be nice if the VerifyStack logic in unpack_frames() was refactored so
        // we could check the stack before and after changing the reexecute mode, but
        // it should pass either way because an invoke uses the same stack state for both modes,
        // which is: args popped but result not yet pushed.
        reexecute = true;
        popframe_preserved_args_size_in_bytes = in_bytes(thread->popframe_preserved_args_size());
        // Note: the PopFrame-related extension of the expression stack size is done in
        // Deoptimization::fetch_unroll_info_helper
        popframe_preserved_args_size_in_words = in_words(thread->popframe_preserved_args_size_in_words());
      }
    } else if (JvmtiExport::can_force_early_return() && state != nullptr && state->is_earlyret_pending()) {
      if (!realloc_failure_exception) {
        // Force early return from top frame after deoptimization
        pc = Interpreter::remove_activation_early_entry(state->earlyret_tos());
      } else {
        state->clr_earlyret_pending();
        state->set_earlyret_oop(nullptr);
        state->clr_earlyret_value();
      }
    }
    _reexecute = reexecute;
  }

  // Setup the interpreter frame

  assert(method() != nullptr, "method must exist");
  int temps = expressions()->size();

  int locks = monitors() == nullptr ? 0 : monitors()->number_of_monitors();

  Interpreter::layout_activation(method(),
                                 temps + callee_parameters,
                                 popframe_preserved_args_size_in_words,
                                 locks,
                                 caller_actual_parameters,
                                 callee_parameters,
                                 callee_locals,
                                 caller,
                                 iframe(),
                                 is_top_frame,
                                 is_bottom_frame);

  // Update the pc in the frame object and overwrite the temporary pc
  // we placed in the skeletal frame now that we finally know the
  // exact interpreter address we should use.

  _frame.patch_pc(thread, pc);

  assert (!method()->is_synchronized() || locks > 0 || _removed_monitors || raw_bci() == SynchronizationEntryBCI, "synchronized methods must have monitors");

  BasicObjectLock* top = iframe()->interpreter_frame_monitor_begin();
  for (int index = 0; index < locks; index++) {
    top = iframe()->previous_monitor_in_interpreter_frame(top);
    BasicObjectLock* src = _monitors->at(index);
    top->set_obj(src->obj());
    assert(src->obj() != nullptr || ObjectSynchronizer::current_thread_holds_lock(thread, Handle(thread, src->obj())),
           "should be held, before move_to");
    src->lock()->move_to(src->obj(), top->lock());
    assert(src->obj() != nullptr || ObjectSynchronizer::current_thread_holds_lock(thread, Handle(thread, src->obj())),
           "should be held, after move_to");
  }
  iframe()->interpreter_frame_set_bcp(bcp);
  if (ProfileInterpreter) {
    MethodData* mdo = method()->method_data();
    if (mdo != nullptr && exec_mode != Deoptimization::Unpack_exception) {
      int bci = iframe()->interpreter_frame_bci();
      if (!reexecute) ++bci;
      address mdp = mdo->bci_to_dp(bci);
      iframe()->interpreter_frame_set_mdp(mdp);
    } else {
      iframe()->interpreter_frame_set_mdp(nullptr); // clear out the mdp.
    }
  }

#ifndef PRODUCT
  if (PrintDeoptimizationDetails) {
    tty->print_cr("Expressions size: %d", expressions()->size());
  }
#endif // !PRODUCT

  // Unpack expression stack
  // If this is an intermediate frame (i.e. not top frame) then this
  // only unpacks the part of the expression stack not used by callee
  // as parameters. The callee parameters are unpacked as part of the
  // callee locals.
  int i;
  for(i = 0; i < expressions()->size(); i++) {
    StackValue *value = expressions()->at(i);
    intptr_t*   addr  = iframe()->interpreter_frame_expression_stack_at(i);
    assert(!is_bottom_frame || !(caller->is_compiled_caller() && addr >= caller->unextended_sp()), "overwriting caller frame!");
    switch(value->type()) {
      case T_INT:
        *addr = value->get_intptr();
#ifndef PRODUCT
        if (PrintDeoptimizationDetails) {
          tty->print_cr(" - Reconstructed expression %d (INT): %d", i, (int)(*addr));
        }
#endif // !PRODUCT
        break;
      case T_OBJECT:
        *addr = value->get_intptr(T_OBJECT);
#ifndef PRODUCT
        if (PrintDeoptimizationDetails) {
          tty->print(" - Reconstructed expression %d (OBJECT): ", i);
          oop o = cast_to_oop((address)(*addr));
          if (o == nullptr) {
            tty->print_cr("null");
          } else {
            ResourceMark rm;
            tty->print_raw_cr(o->klass()->name()->as_C_string());
          }
        }
#endif // !PRODUCT
        break;
      case T_CONFLICT:
        // A dead stack slot.  Initialize to null in case it is an oop.
        *addr = NULL_WORD;
        break;
      default:
        ShouldNotReachHere();
    }
  }

#ifndef PRODUCT
  if (PrintDeoptimizationDetails) {
    tty->print_cr("Locals size: %d", locals()->size());
  }
#endif // !PRODUCT

  // Unpack the locals
  for(i = 0; i < locals()->size(); i++) {
    StackValue *value = locals()->at(i);
    intptr_t* addr  = iframe()->interpreter_frame_local_at(i);
    assert(!is_bottom_frame || !(caller->is_compiled_caller() && addr >= caller->unextended_sp()), "overwriting caller frame!");
    switch(value->type()) {
      case T_INT:
        *addr = value->get_intptr();
#ifndef PRODUCT
        if (PrintDeoptimizationDetails) {
          tty->print_cr(" - Reconstructed local %d (INT): %d", i, (int)(*addr));
        }
#endif // !PRODUCT
        break;
      case T_OBJECT:
        *addr = value->get_intptr(T_OBJECT);
#ifndef PRODUCT
        if (PrintDeoptimizationDetails) {
          tty->print(" - Reconstructed local %d (OBJECT): ", i);
          oop o = cast_to_oop((address)(*addr));
          if (o == nullptr) {
            tty->print_cr("null");
          } else {
            ResourceMark rm;
            tty->print_raw_cr(o->klass()->name()->as_C_string());
          }
        }
#endif // !PRODUCT
        break;
      case T_CONFLICT:
        // A dead location. If it is an oop then we need a null to prevent GC from following it
        *addr = NULL_WORD;
        break;
      default:
        ShouldNotReachHere();
    }
  }

  if (is_top_frame && JvmtiExport::can_pop_frame() && thread->popframe_forcing_deopt_reexecution()) {
    // An interpreted frame was popped but it returns to a deoptimized
    // frame. The incoming arguments to the interpreted activation
    // were preserved in thread-local storage by the
    // remove_activation_preserving_args_entry in the interpreter; now
    // we put them back into the just-unpacked interpreter frame.
    // Note that this assumes that the locals arena grows toward lower
    // addresses.
    if (popframe_preserved_args_size_in_words != 0) {
      void* saved_args = thread->popframe_preserved_args();
      assert(saved_args != nullptr, "must have been saved by interpreter");
#ifdef ASSERT
      assert(popframe_preserved_args_size_in_words <=
             iframe()->interpreter_frame_expression_stack_size()*Interpreter::stackElementWords,
             "expression stack size should have been extended");
#endif // ASSERT
      int top_element = iframe()->interpreter_frame_expression_stack_size()-1;
      intptr_t* base;
      if (frame::interpreter_frame_expression_stack_direction() < 0) {
        base = iframe()->interpreter_frame_expression_stack_at(top_element);
      } else {
        base = iframe()->interpreter_frame_expression_stack();
      }
      Copy::conjoint_jbytes(saved_args,
                            base,
                            popframe_preserved_args_size_in_bytes);
      thread->popframe_free_preserved_args();
    }
  }

#ifndef PRODUCT
  if (PrintDeoptimizationDetails) {
    const bool print_codes = WizardMode && Verbose;
    ResourceMark rm(thread);
    stringStream codes_ss;
    if (print_codes) {
      // print_codes_on() may acquire MDOExtraData_lock (rank nosafepoint-1).
      // To keep the lock acquisition order correct, call it before taking tty_lock.
      // Avoid double buffering: set buffered=false.
      method()->print_codes_on(&codes_ss, 0, false);
    }
    ttyLocker ttyl;
    tty->print_cr("[%d. Interpreted Frame]", ++unpack_counter);
    iframe()->print_on(tty);
    RegisterMap map(thread,
                    RegisterMap::UpdateMap::include,
                    RegisterMap::ProcessFrames::include,
                    RegisterMap::WalkContinuation::skip);
    vframe* f = vframe::new_vframe(iframe(), &map, thread);
    f->print();
    if (print_codes) {
      tty->print("%s", codes_ss.as_string());
    }
    tty->cr();
  }
#endif // !PRODUCT

  // The expression stack and locals are in the resource area don't leave
  // a dangling pointer in the vframeArray we leave around for debug
  // purposes

  _locals = _expressions = nullptr;

}

int vframeArrayElement::on_stack_size(int callee_parameters,
                                      int callee_locals,
                                      bool is_top_frame,
                                      int popframe_extra_stack_expression_els) const {
  assert(method()->max_locals() == locals()->size(), "just checking");
  int locks = monitors() == nullptr ? 0 : monitors()->number_of_monitors();
  int temps = expressions()->size();
  return Interpreter::size_activation(method()->max_stack(),
                                      temps + callee_parameters,
                                      popframe_extra_stack_expression_els,
                                      locks,
                                      callee_parameters,
                                      callee_locals,
                                      is_top_frame);
}


intptr_t* vframeArray::unextended_sp() const {
  assert(owner_thread()->is_in_usable_stack((address) _original.unextended_sp()), INTPTR_FORMAT, p2i(_original.unextended_sp()));
  return _original.unextended_sp();
}

vframeArray* vframeArray::allocate(JavaThread* thread, int frame_size, GrowableArray<compiledVFrame*>* chunk,
                                   RegisterMap *reg_map, frame sender, frame caller, frame self,
                                   bool realloc_failures) {

  // Allocate the vframeArray
  vframeArray * result = (vframeArray*) AllocateHeap(sizeof(vframeArray) + // fixed part
                                                     sizeof(vframeArrayElement) * (chunk->length() - 1), // variable part
                                                     mtCompiler);
  result->_frames = chunk->length();
  result->_owner_thread = thread;
  result->_sender = sender;
  result->_caller = caller;
  result->_original = self;
  result->set_unroll_block(nullptr); // initialize it
  result->fill_in(thread, frame_size, chunk, reg_map, realloc_failures);
  return result;
}

void vframeArray::fill_in(JavaThread* thread,
                          int frame_size,
                          GrowableArray<compiledVFrame*>* chunk,
                          const RegisterMap *reg_map,
                          bool realloc_failures) {
  // Set owner first, it is used when adding monitor chunks

  _frame_size = frame_size;
  for(int i = 0; i < chunk->length(); i++) {
    element(i)->fill_in(chunk->at(i), realloc_failures);
  }

  // Copy registers for callee-saved registers
  if (reg_map != nullptr) {
    for(int i = 0; i < RegisterMap::reg_count; i++) {
#ifdef AMD64
      // The register map has one entry for every int (32-bit value), so
      // 64-bit physical registers have two entries in the map, one for
      // each half.  Ignore the high halves of 64-bit registers, just like
      // frame::oopmapreg_to_location does.
      //
      // [phh] FIXME: this is a temporary hack!  This code *should* work
      // correctly w/o this hack, possibly by changing RegisterMap::pd_location
      // in frame_amd64.cpp and the values of the phantom high half registers
      // in amd64.ad.
      //      if (VMReg::Name(i) < SharedInfo::stack0 && is_even(i)) {
        intptr_t* src = (intptr_t*) reg_map->location(VMRegImpl::as_VMReg(i), _caller.sp());
        _callee_registers[i] = src != nullptr ? *src : NULL_WORD;
        //      } else {
        //      jint* src = (jint*) reg_map->location(VMReg::Name(i));
        //      _callee_registers[i] = src != nullptr ? *src : NULL_WORD;
        //      }
#else
      jint* src = (jint*) reg_map->location(VMRegImpl::as_VMReg(i), _caller.sp());
      _callee_registers[i] = src != nullptr ? *src : NULL_WORD;
#endif
      if (src != nullptr) {
        jint* dst = (jint*) register_location(i);
        *dst = *src;
      }
    }
  }
}

void vframeArray::unpack_to_stack(frame &unpack_frame, int exec_mode, int caller_actual_parameters) {
  // stack picture
  //   unpack_frame
  //   [new interpreter frames ] (frames are skeletal but walkable)
  //   caller_frame
  //
  //  This routine fills in the missing data for the skeletal interpreter frames
  //  in the above picture.

  // Find the skeletal interpreter frames to unpack into
  JavaThread* current = JavaThread::current();

  RegisterMap map(current,
                  RegisterMap::UpdateMap::skip,
                  RegisterMap::ProcessFrames::include,
                  RegisterMap::WalkContinuation::skip);
  // Get the youngest frame we will unpack (last to be unpacked)
  frame me = unpack_frame.sender(&map);
  int index;
  for (index = 0; index < frames(); index++ ) {
    *element(index)->iframe() = me;
    // Get the caller frame (possibly skeletal)
    me = me.sender(&map);
  }

  Events::log_deopt_message(current, "DEOPT UNPACKING pc=" INTPTR_FORMAT " sp=" INTPTR_FORMAT " mode %d",
                            p2i(unpack_frame.pc()), p2i(unpack_frame.sp()), exec_mode);

  if (TraceDeoptimization) {
    ResourceMark rm;
    stringStream st;
    st.print_cr("DEOPT UNPACKING thread=" INTPTR_FORMAT " vframeArray=" INTPTR_FORMAT " mode=%d",
                p2i(current), p2i(this), exec_mode);
    st.print_cr("   Virtual frames (outermost/oldest first):");
    tty->print_raw(st.freeze());
  }

  // Do the unpacking of interpreter frames; the frame at index 0 represents the top activation, so it has no callee
  // Unpack the frames from the oldest (frames() -1) to the youngest (0)
  frame* caller_frame = &me;
  for (index = frames() - 1; index >= 0 ; index--) {
    vframeArrayElement* elem = element(index);  // caller
    int callee_parameters, callee_locals;
    if (index == 0) {
      callee_parameters = callee_locals = 0;
    } else {
      methodHandle caller(current, elem->method());
      methodHandle callee(current, element(index - 1)->method());
      Bytecode_invoke inv(caller, elem->bci());
      const bool has_member_arg = inv.has_member_arg();
      callee_parameters = callee->size_of_parameters() + (has_member_arg ? 1 : 0);
      callee_locals     = callee->max_locals();
    }
    if (TraceDeoptimization) {
      ResourceMark rm;
      stringStream st;
      st.print("      VFrame %d (" INTPTR_FORMAT ")", index, p2i(elem));
      st.print(" - %s", elem->method()->name_and_sig_as_C_string());
      int bci = elem->raw_bci();
      const char* code_name;
      if (bci == SynchronizationEntryBCI) {
        code_name = "sync entry";
      } else {
        Bytecodes::Code code = elem->method()->code_at(bci);
        code_name = Bytecodes::name(code);
      }
      st.print(" - %s", code_name);
      st.print(" @ bci=%d ", bci);
      st.print_cr("sp=" PTR_FORMAT, p2i(elem->iframe()->sp()));
      tty->print_raw(st.freeze());
    }
    elem->unpack_on_stack(caller_actual_parameters,
                          callee_parameters,
                          callee_locals,
                          caller_frame,
                          index == 0,
                          index == frames() - 1,
                          exec_mode);
    if (index == frames() - 1) {
      Deoptimization::unwind_callee_save_values(elem->iframe(), this);
    }
    caller_frame = elem->iframe();
    caller_actual_parameters = callee_parameters;
  }
  deallocate_monitor_chunks();
  if (TraceDeoptimization) {
    tty->cr();
  }
}

void vframeArray::deallocate_monitor_chunks() {
  for (int index = 0; index < frames(); index++ ) {
     element(index)->free_monitors();
  }
}

#ifndef PRODUCT

bool vframeArray::structural_compare(JavaThread* thread, GrowableArray<compiledVFrame*>* chunk) {
  if (owner_thread() != thread) return false;
  int index = 0;
#if 0 // FIXME can't do this comparison

  // Compare only within vframe array.
  for (deoptimizedVFrame* vf = deoptimizedVFrame::cast(vframe_at(first_index())); vf; vf = vf->deoptimized_sender_or_null()) {
    if (index >= chunk->length() || !vf->structural_compare(chunk->at(index))) return false;
    index++;
  }
  if (index != chunk->length()) return false;
#endif

  return true;
}

#endif // !PRODUCT

address vframeArray::register_location(int i) const {
  assert(0 <= i && i < RegisterMap::reg_count, "index out of bounds");
  return (address) & _callee_registers[i];
}


#ifndef PRODUCT

// Printing

// Note: we cannot have print_on as const, as we allocate inside the method
void vframeArray::print_on_2(outputStream* st)  {
  st->print_cr(" - sp: " INTPTR_FORMAT, p2i(sp()));
  st->print(" - thread: ");
  Thread::current()->print();
  st->print_cr(" - frame size: %d", frame_size());
  for (int index = 0; index < frames() ; index++ ) {
    element(index)->print(st);
  }
}

void vframeArrayElement::print(outputStream* st) {
  st->print_cr(" - interpreter_frame -> sp: " INTPTR_FORMAT, p2i(iframe()->sp()));
}

void vframeArray::print_value_on(outputStream* st) const {
  st->print_cr("vframeArray [%d] ", frames());
}


#endif // !PRODUCT
