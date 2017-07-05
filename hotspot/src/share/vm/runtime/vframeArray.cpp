/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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

# include "incls/_precompiled.incl"
# include "incls/_vframeArray.cpp.incl"


int vframeArrayElement:: bci(void) const { return (_bci == SynchronizationEntryBCI ? 0 : _bci); }

void vframeArrayElement::free_monitors(JavaThread* jt) {
  if (_monitors != NULL) {
     MonitorChunk* chunk = _monitors;
     _monitors = NULL;
     jt->remove_monitor_chunk(chunk);
     delete chunk;
  }
}

void vframeArrayElement::fill_in(compiledVFrame* vf) {

// Copy the information from the compiled vframe to the
// interpreter frame we will be creating to replace vf

  _method = vf->method();
  _bci    = vf->raw_bci();
  _reexecute = vf->should_reexecute();

  int index;

  // Get the monitors off-stack

  GrowableArray<MonitorInfo*>* list = vf->monitors();
  if (list->is_empty()) {
    _monitors = NULL;
  } else {

    // Allocate monitor chunk
    _monitors = new MonitorChunk(list->length());
    vf->thread()->add_monitor_chunk(_monitors);

    // Migrate the BasicLocks from the stack to the monitor chunk
    for (index = 0; index < list->length(); index++) {
      MonitorInfo* monitor = list->at(index);
      assert(!monitor->owner_is_scalar_replaced(), "object should be reallocated already");
      assert(monitor->owner() == NULL || (!monitor->owner()->is_unlocked() && !monitor->owner()->has_bias_pattern()), "object must be null or locked, and unbiased");
      BasicObjectLock* dest = _monitors->at(index);
      dest->set_obj(monitor->owner());
      monitor->lock()->move_to(monitor->owner(), dest->lock());
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
        assert(!value->obj_is_scalar_replaced(), "object should be reallocated already");
        // preserve object type
        _locals->add( new StackValue((intptr_t) (value->get_obj()()), T_OBJECT ));
        break;
      case T_CONFLICT:
        // A dead local.  Will be initialized to null/zero.
        _locals->add( new StackValue());
        break;
      case T_INT:
        _locals->add( new StackValue(value->get_int()));
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
        assert(!value->obj_is_scalar_replaced(), "object should be reallocated already");
        // preserve object type
        _expressions->add( new StackValue((intptr_t) (value->get_obj()()), T_OBJECT ));
        break;
      case T_CONFLICT:
        // A dead stack element.  Will be initialized to null/zero.
        // This can occur when the compiler emits a state in which stack
        // elements are known to be dead (because of an imminent exception).
        _expressions->add( new StackValue());
        break;
      case T_INT:
        _expressions->add( new StackValue(value->get_int()));
        break;
      default:
        ShouldNotReachHere();
    }
  }
}

int unpack_counter = 0;

void vframeArrayElement::unpack_on_stack(int callee_parameters,
                                         int callee_locals,
                                         frame* caller,
                                         bool is_top_frame,
                                         int exec_mode) {
  JavaThread* thread = (JavaThread*) Thread::current();

  // Look at bci and decide on bcp and continuation pc
  address bcp;
  // C++ interpreter doesn't need a pc since it will figure out what to do when it
  // begins execution
  address pc;
  bool use_next_mdp = false; // true if we should use the mdp associated with the next bci
                             // rather than the one associated with bcp
  if (raw_bci() == SynchronizationEntryBCI) {
    // We are deoptimizing while hanging in prologue code for synchronized method
    bcp = method()->bcp_from(0); // first byte code
    pc  = Interpreter::deopt_entry(vtos, 0); // step = 0 since we don't skip current bytecode
  } else if (should_reexecute()) { //reexecute this bytecode
    assert(is_top_frame, "reexecute allowed only for the top frame");
    bcp = method()->bcp_from(bci());
    pc  = Interpreter::deopt_reexecute_entry(method(), bcp);
  } else {
    bcp = method()->bcp_from(bci());
    pc  = Interpreter::deopt_continue_after_entry(method(), bcp, callee_parameters, is_top_frame);
    use_next_mdp = true;
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

  assert(*bcp != Bytecodes::_monitorenter || is_top_frame, "a _monitorenter must be a top frame");
  // TIERED Must know the compiler of the deoptee QQQ
  COMPILER2_PRESENT(guarantee(*bcp != Bytecodes::_monitorenter || exec_mode != Deoptimization::Unpack_exception,
                              "shouldn't get exception during monitorenter");)

  int popframe_preserved_args_size_in_bytes = 0;
  int popframe_preserved_args_size_in_words = 0;
  if (is_top_frame) {
    JvmtiThreadState *state = thread->jvmti_thread_state();
    if (JvmtiExport::can_pop_frame() &&
        (thread->has_pending_popframe() || thread->popframe_forcing_deopt_reexecution())) {
      if (thread->has_pending_popframe()) {
        // Pop top frame after deoptimization
#ifndef CC_INTERP
        pc = Interpreter::remove_activation_preserving_args_entry();
#else
        // Do an uncommon trap type entry. c++ interpreter will know
        // to pop frame and preserve the args
        pc = Interpreter::deopt_entry(vtos, 0);
        use_next_mdp = false;
#endif
      } else {
        // Reexecute invoke in top frame
        pc = Interpreter::deopt_entry(vtos, 0);
        use_next_mdp = false;
        popframe_preserved_args_size_in_bytes = in_bytes(thread->popframe_preserved_args_size());
        // Note: the PopFrame-related extension of the expression stack size is done in
        // Deoptimization::fetch_unroll_info_helper
        popframe_preserved_args_size_in_words = in_words(thread->popframe_preserved_args_size_in_words());
      }
    } else if (JvmtiExport::can_force_early_return() && state != NULL && state->is_earlyret_pending()) {
      // Force early return from top frame after deoptimization
#ifndef CC_INTERP
      pc = Interpreter::remove_activation_early_entry(state->earlyret_tos());
#else
     // TBD: Need to implement ForceEarlyReturn for CC_INTERP (ia64)
#endif
    } else {
      // Possibly override the previous pc computation of the top (youngest) frame
      switch (exec_mode) {
      case Deoptimization::Unpack_deopt:
        // use what we've got
        break;
      case Deoptimization::Unpack_exception:
        // exception is pending
        pc = SharedRuntime::raw_exception_handler_for_return_address(thread, pc);
        // [phh] We're going to end up in some handler or other, so it doesn't
        // matter what mdp we point to.  See exception_handler_for_exception()
        // in interpreterRuntime.cpp.
        break;
      case Deoptimization::Unpack_uncommon_trap:
      case Deoptimization::Unpack_reexecute:
        // redo last byte code
        pc  = Interpreter::deopt_entry(vtos, 0);
        use_next_mdp = false;
        break;
      default:
        ShouldNotReachHere();
      }
    }
  }

  // Setup the interpreter frame

  assert(method() != NULL, "method must exist");
  int temps = expressions()->size();

  int locks = monitors() == NULL ? 0 : monitors()->number_of_monitors();

  Interpreter::layout_activation(method(),
                                 temps + callee_parameters,
                                 popframe_preserved_args_size_in_words,
                                 locks,
                                 callee_parameters,
                                 callee_locals,
                                 caller,
                                 iframe(),
                                 is_top_frame);

  // Update the pc in the frame object and overwrite the temporary pc
  // we placed in the skeletal frame now that we finally know the
  // exact interpreter address we should use.

  _frame.patch_pc(thread, pc);

  assert (!method()->is_synchronized() || locks > 0, "synchronized methods must have monitors");

  BasicObjectLock* top = iframe()->interpreter_frame_monitor_begin();
  for (int index = 0; index < locks; index++) {
    top = iframe()->previous_monitor_in_interpreter_frame(top);
    BasicObjectLock* src = _monitors->at(index);
    top->set_obj(src->obj());
    src->lock()->move_to(src->obj(), top->lock());
  }
  if (ProfileInterpreter) {
    iframe()->interpreter_frame_set_mdx(0); // clear out the mdp.
  }
  iframe()->interpreter_frame_set_bcx((intptr_t)bcp); // cannot use bcp because frame is not initialized yet
  if (ProfileInterpreter) {
    methodDataOop mdo = method()->method_data();
    if (mdo != NULL) {
      int bci = iframe()->interpreter_frame_bci();
      if (use_next_mdp) ++bci;
      address mdp = mdo->bci_to_dp(bci);
      iframe()->interpreter_frame_set_mdp(mdp);
    }
  }

  // Unpack expression stack
  // If this is an intermediate frame (i.e. not top frame) then this
  // only unpacks the part of the expression stack not used by callee
  // as parameters. The callee parameters are unpacked as part of the
  // callee locals.
  int i;
  for(i = 0; i < expressions()->size(); i++) {
    StackValue *value = expressions()->at(i);
    intptr_t*   addr  = iframe()->interpreter_frame_expression_stack_at(i);
    switch(value->type()) {
      case T_INT:
        *addr = value->get_int();
        break;
      case T_OBJECT:
        *addr = value->get_int(T_OBJECT);
        break;
      case T_CONFLICT:
        // A dead stack slot.  Initialize to null in case it is an oop.
        *addr = NULL_WORD;
        break;
      default:
        ShouldNotReachHere();
    }
  }


  // Unpack the locals
  for(i = 0; i < locals()->size(); i++) {
    StackValue *value = locals()->at(i);
    intptr_t* addr  = iframe()->interpreter_frame_local_at(i);
    switch(value->type()) {
      case T_INT:
        *addr = value->get_int();
        break;
      case T_OBJECT:
        *addr = value->get_int(T_OBJECT);
        break;
      case T_CONFLICT:
        // A dead location. If it is an oop then we need a NULL to prevent GC from following it
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
      assert(saved_args != NULL, "must have been saved by interpreter");
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
      Copy::conjoint_bytes(saved_args,
                           base,
                           popframe_preserved_args_size_in_bytes);
      thread->popframe_free_preserved_args();
    }
  }

#ifndef PRODUCT
  if (TraceDeoptimization && Verbose) {
    ttyLocker ttyl;
    tty->print_cr("[%d Interpreted Frame]", ++unpack_counter);
    iframe()->print_on(tty);
    RegisterMap map(thread);
    vframe* f = vframe::new_vframe(iframe(), &map, thread);
    f->print();

    tty->print_cr("locals size     %d", locals()->size());
    tty->print_cr("expression size %d", expressions()->size());

    method()->print_value();
    tty->cr();
    // method()->print_codes();
  } else if (TraceDeoptimization) {
    tty->print("     ");
    method()->print_value();
    Bytecodes::Code code = Bytecodes::java_code_at(bcp);
    int bci = method()->bci_from(bcp);
    tty->print(" - %s", Bytecodes::name(code));
    tty->print(" @ bci %d ", bci);
    tty->print_cr("sp = " PTR_FORMAT, iframe()->sp());
  }
#endif // PRODUCT

  // The expression stack and locals are in the resource area don't leave
  // a dangling pointer in the vframeArray we leave around for debug
  // purposes

  _locals = _expressions = NULL;

}

int vframeArrayElement::on_stack_size(int callee_parameters,
                                      int callee_locals,
                                      bool is_top_frame,
                                      int popframe_extra_stack_expression_els) const {
  assert(method()->max_locals() == locals()->size(), "just checking");
  int locks = monitors() == NULL ? 0 : monitors()->number_of_monitors();
  int temps = expressions()->size();
  return Interpreter::size_activation(method(),
                                      temps + callee_parameters,
                                      popframe_extra_stack_expression_els,
                                      locks,
                                      callee_parameters,
                                      callee_locals,
                                      is_top_frame);
}



vframeArray* vframeArray::allocate(JavaThread* thread, int frame_size, GrowableArray<compiledVFrame*>* chunk,
                                   RegisterMap *reg_map, frame sender, frame caller, frame self) {

  // Allocate the vframeArray
  vframeArray * result = (vframeArray*) AllocateHeap(sizeof(vframeArray) + // fixed part
                                                     sizeof(vframeArrayElement) * (chunk->length() - 1), // variable part
                                                     "vframeArray::allocate");
  result->_frames = chunk->length();
  result->_owner_thread = thread;
  result->_sender = sender;
  result->_caller = caller;
  result->_original = self;
  result->set_unroll_block(NULL); // initialize it
  result->fill_in(thread, frame_size, chunk, reg_map);
  return result;
}

void vframeArray::fill_in(JavaThread* thread,
                          int frame_size,
                          GrowableArray<compiledVFrame*>* chunk,
                          const RegisterMap *reg_map) {
  // Set owner first, it is used when adding monitor chunks

  _frame_size = frame_size;
  for(int i = 0; i < chunk->length(); i++) {
    element(i)->fill_in(chunk->at(i));
  }

  // Copy registers for callee-saved registers
  if (reg_map != NULL) {
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
        intptr_t* src = (intptr_t*) reg_map->location(VMRegImpl::as_VMReg(i));
        _callee_registers[i] = src != NULL ? *src : NULL_WORD;
        //      } else {
        //      jint* src = (jint*) reg_map->location(VMReg::Name(i));
        //      _callee_registers[i] = src != NULL ? *src : NULL_WORD;
        //      }
#else
      jint* src = (jint*) reg_map->location(VMRegImpl::as_VMReg(i));
      _callee_registers[i] = src != NULL ? *src : NULL_WORD;
#endif
      if (src == NULL) {
        set_location_valid(i, false);
      } else {
        set_location_valid(i, true);
        jint* dst = (jint*) register_location(i);
        *dst = *src;
      }
    }
  }
}

void vframeArray::unpack_to_stack(frame &unpack_frame, int exec_mode) {
  // stack picture
  //   unpack_frame
  //   [new interpreter frames ] (frames are skeletal but walkable)
  //   caller_frame
  //
  //  This routine fills in the missing data for the skeletal interpreter frames
  //  in the above picture.

  // Find the skeletal interpreter frames to unpack into
  RegisterMap map(JavaThread::current(), false);
  // Get the youngest frame we will unpack (last to be unpacked)
  frame me = unpack_frame.sender(&map);
  int index;
  for (index = 0; index < frames(); index++ ) {
    *element(index)->iframe() = me;
    // Get the caller frame (possibly skeletal)
    me = me.sender(&map);
  }

  frame caller_frame = me;

  // Do the unpacking of interpreter frames; the frame at index 0 represents the top activation, so it has no callee

  // Unpack the frames from the oldest (frames() -1) to the youngest (0)

  for (index = frames() - 1; index >= 0 ; index--) {
    int callee_parameters = index == 0 ? 0 : element(index-1)->method()->size_of_parameters();
    int callee_locals     = index == 0 ? 0 : element(index-1)->method()->max_locals();
    element(index)->unpack_on_stack(callee_parameters,
                                    callee_locals,
                                    &caller_frame,
                                    index == 0,
                                    exec_mode);
    if (index == frames() - 1) {
      Deoptimization::unwind_callee_save_values(element(index)->iframe(), this);
    }
    caller_frame = *element(index)->iframe();
  }


  deallocate_monitor_chunks();
}

void vframeArray::deallocate_monitor_chunks() {
  JavaThread* jt = JavaThread::current();
  for (int index = 0; index < frames(); index++ ) {
     element(index)->free_monitors(jt);
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

#endif

address vframeArray::register_location(int i) const {
  assert(0 <= i && i < RegisterMap::reg_count, "index out of bounds");
  return (address) & _callee_registers[i];
}


#ifndef PRODUCT

// Printing

// Note: we cannot have print_on as const, as we allocate inside the method
void vframeArray::print_on_2(outputStream* st)  {
  st->print_cr(" - sp: " INTPTR_FORMAT, sp());
  st->print(" - thread: ");
  Thread::current()->print();
  st->print_cr(" - frame size: %d", frame_size());
  for (int index = 0; index < frames() ; index++ ) {
    element(index)->print(st);
  }
}

void vframeArrayElement::print(outputStream* st) {
  st->print_cr(" - interpreter_frame -> sp: " INTPTR_FORMAT, iframe()->sp());
}

void vframeArray::print_value_on(outputStream* st) const {
  st->print_cr("vframeArray [%d] ", frames());
}


#endif
