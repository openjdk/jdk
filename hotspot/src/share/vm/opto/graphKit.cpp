/*
 * Copyright (c) 2001, 2010, Oracle and/or its affiliates. All rights reserved.
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
#include "compiler/compileLog.hpp"
#include "gc_implementation/g1/g1SATBCardTableModRefBS.hpp"
#include "gc_implementation/g1/heapRegion.hpp"
#include "gc_interface/collectedHeap.hpp"
#include "memory/barrierSet.hpp"
#include "memory/cardTableModRefBS.hpp"
#include "opto/addnode.hpp"
#include "opto/graphKit.hpp"
#include "opto/idealKit.hpp"
#include "opto/locknode.hpp"
#include "opto/machnode.hpp"
#include "opto/parse.hpp"
#include "opto/rootnode.hpp"
#include "opto/runtime.hpp"
#include "runtime/deoptimization.hpp"
#include "runtime/sharedRuntime.hpp"

//----------------------------GraphKit-----------------------------------------
// Main utility constructor.
GraphKit::GraphKit(JVMState* jvms)
  : Phase(Phase::Parser),
    _env(C->env()),
    _gvn(*C->initial_gvn())
{
  _exceptions = jvms->map()->next_exception();
  if (_exceptions != NULL)  jvms->map()->set_next_exception(NULL);
  set_jvms(jvms);
}

// Private constructor for parser.
GraphKit::GraphKit()
  : Phase(Phase::Parser),
    _env(C->env()),
    _gvn(*C->initial_gvn())
{
  _exceptions = NULL;
  set_map(NULL);
  debug_only(_sp = -99);
  debug_only(set_bci(-99));
}



//---------------------------clean_stack---------------------------------------
// Clear away rubbish from the stack area of the JVM state.
// This destroys any arguments that may be waiting on the stack.
void GraphKit::clean_stack(int from_sp) {
  SafePointNode* map      = this->map();
  JVMState*      jvms     = this->jvms();
  int            stk_size = jvms->stk_size();
  int            stkoff   = jvms->stkoff();
  Node*          top      = this->top();
  for (int i = from_sp; i < stk_size; i++) {
    if (map->in(stkoff + i) != top) {
      map->set_req(stkoff + i, top);
    }
  }
}


//--------------------------------sync_jvms-----------------------------------
// Make sure our current jvms agrees with our parse state.
JVMState* GraphKit::sync_jvms() const {
  JVMState* jvms = this->jvms();
  jvms->set_bci(bci());       // Record the new bci in the JVMState
  jvms->set_sp(sp());         // Record the new sp in the JVMState
  assert(jvms_in_sync(), "jvms is now in sync");
  return jvms;
}

#ifdef ASSERT
bool GraphKit::jvms_in_sync() const {
  Parse* parse = is_Parse();
  if (parse == NULL) {
    if (bci() !=      jvms()->bci())          return false;
    if (sp()  != (int)jvms()->sp())           return false;
    return true;
  }
  if (jvms()->method() != parse->method())    return false;
  if (jvms()->bci()    != parse->bci())       return false;
  int jvms_sp = jvms()->sp();
  if (jvms_sp          != parse->sp())        return false;
  int jvms_depth = jvms()->depth();
  if (jvms_depth       != parse->depth())     return false;
  return true;
}

// Local helper checks for special internal merge points
// used to accumulate and merge exception states.
// They are marked by the region's in(0) edge being the map itself.
// Such merge points must never "escape" into the parser at large,
// until they have been handed to gvn.transform.
static bool is_hidden_merge(Node* reg) {
  if (reg == NULL)  return false;
  if (reg->is_Phi()) {
    reg = reg->in(0);
    if (reg == NULL)  return false;
  }
  return reg->is_Region() && reg->in(0) != NULL && reg->in(0)->is_Root();
}

void GraphKit::verify_map() const {
  if (map() == NULL)  return;  // null map is OK
  assert(map()->req() <= jvms()->endoff(), "no extra garbage on map");
  assert(!map()->has_exceptions(),    "call add_exception_states_from 1st");
  assert(!is_hidden_merge(control()), "call use_exception_state, not set_map");
}

void GraphKit::verify_exception_state(SafePointNode* ex_map) {
  assert(ex_map->next_exception() == NULL, "not already part of a chain");
  assert(has_saved_ex_oop(ex_map), "every exception state has an ex_oop");
}
#endif

//---------------------------stop_and_kill_map---------------------------------
// Set _map to NULL, signalling a stop to further bytecode execution.
// First smash the current map's control to a constant, to mark it dead.
void GraphKit::stop_and_kill_map() {
  SafePointNode* dead_map = stop();
  if (dead_map != NULL) {
    dead_map->disconnect_inputs(NULL); // Mark the map as killed.
    assert(dead_map->is_killed(), "must be so marked");
  }
}


//--------------------------------stopped--------------------------------------
// Tell if _map is NULL, or control is top.
bool GraphKit::stopped() {
  if (map() == NULL)           return true;
  else if (control() == top()) return true;
  else                         return false;
}


//-----------------------------has_ex_handler----------------------------------
// Tell if this method or any caller method has exception handlers.
bool GraphKit::has_ex_handler() {
  for (JVMState* jvmsp = jvms(); jvmsp != NULL; jvmsp = jvmsp->caller()) {
    if (jvmsp->has_method() && jvmsp->method()->has_exception_handlers()) {
      return true;
    }
  }
  return false;
}

//------------------------------save_ex_oop------------------------------------
// Save an exception without blowing stack contents or other JVM state.
void GraphKit::set_saved_ex_oop(SafePointNode* ex_map, Node* ex_oop) {
  assert(!has_saved_ex_oop(ex_map), "clear ex-oop before setting again");
  ex_map->add_req(ex_oop);
  debug_only(verify_exception_state(ex_map));
}

inline static Node* common_saved_ex_oop(SafePointNode* ex_map, bool clear_it) {
  assert(GraphKit::has_saved_ex_oop(ex_map), "ex_oop must be there");
  Node* ex_oop = ex_map->in(ex_map->req()-1);
  if (clear_it)  ex_map->del_req(ex_map->req()-1);
  return ex_oop;
}

//-----------------------------saved_ex_oop------------------------------------
// Recover a saved exception from its map.
Node* GraphKit::saved_ex_oop(SafePointNode* ex_map) {
  return common_saved_ex_oop(ex_map, false);
}

//--------------------------clear_saved_ex_oop---------------------------------
// Erase a previously saved exception from its map.
Node* GraphKit::clear_saved_ex_oop(SafePointNode* ex_map) {
  return common_saved_ex_oop(ex_map, true);
}

#ifdef ASSERT
//---------------------------has_saved_ex_oop----------------------------------
// Erase a previously saved exception from its map.
bool GraphKit::has_saved_ex_oop(SafePointNode* ex_map) {
  return ex_map->req() == ex_map->jvms()->endoff()+1;
}
#endif

//-------------------------make_exception_state--------------------------------
// Turn the current JVM state into an exception state, appending the ex_oop.
SafePointNode* GraphKit::make_exception_state(Node* ex_oop) {
  sync_jvms();
  SafePointNode* ex_map = stop();  // do not manipulate this map any more
  set_saved_ex_oop(ex_map, ex_oop);
  return ex_map;
}


//--------------------------add_exception_state--------------------------------
// Add an exception to my list of exceptions.
void GraphKit::add_exception_state(SafePointNode* ex_map) {
  if (ex_map == NULL || ex_map->control() == top()) {
    return;
  }
#ifdef ASSERT
  verify_exception_state(ex_map);
  if (has_exceptions()) {
    assert(ex_map->jvms()->same_calls_as(_exceptions->jvms()), "all collected exceptions must come from the same place");
  }
#endif

  // If there is already an exception of exactly this type, merge with it.
  // In particular, null-checks and other low-level exceptions common up here.
  Node*       ex_oop  = saved_ex_oop(ex_map);
  const Type* ex_type = _gvn.type(ex_oop);
  if (ex_oop == top()) {
    // No action needed.
    return;
  }
  assert(ex_type->isa_instptr(), "exception must be an instance");
  for (SafePointNode* e2 = _exceptions; e2 != NULL; e2 = e2->next_exception()) {
    const Type* ex_type2 = _gvn.type(saved_ex_oop(e2));
    // We check sp also because call bytecodes can generate exceptions
    // both before and after arguments are popped!
    if (ex_type2 == ex_type
        && e2->_jvms->sp() == ex_map->_jvms->sp()) {
      combine_exception_states(ex_map, e2);
      return;
    }
  }

  // No pre-existing exception of the same type.  Chain it on the list.
  push_exception_state(ex_map);
}

//-----------------------add_exception_states_from-----------------------------
void GraphKit::add_exception_states_from(JVMState* jvms) {
  SafePointNode* ex_map = jvms->map()->next_exception();
  if (ex_map != NULL) {
    jvms->map()->set_next_exception(NULL);
    for (SafePointNode* next_map; ex_map != NULL; ex_map = next_map) {
      next_map = ex_map->next_exception();
      ex_map->set_next_exception(NULL);
      add_exception_state(ex_map);
    }
  }
}

//-----------------------transfer_exceptions_into_jvms-------------------------
JVMState* GraphKit::transfer_exceptions_into_jvms() {
  if (map() == NULL) {
    // We need a JVMS to carry the exceptions, but the map has gone away.
    // Create a scratch JVMS, cloned from any of the exception states...
    if (has_exceptions()) {
      _map = _exceptions;
      _map = clone_map();
      _map->set_next_exception(NULL);
      clear_saved_ex_oop(_map);
      debug_only(verify_map());
    } else {
      // ...or created from scratch
      JVMState* jvms = new (C) JVMState(_method, NULL);
      jvms->set_bci(_bci);
      jvms->set_sp(_sp);
      jvms->set_map(new (C, TypeFunc::Parms) SafePointNode(TypeFunc::Parms, jvms));
      set_jvms(jvms);
      for (uint i = 0; i < map()->req(); i++)  map()->init_req(i, top());
      set_all_memory(top());
      while (map()->req() < jvms->endoff())  map()->add_req(top());
    }
    // (This is a kludge, in case you didn't notice.)
    set_control(top());
  }
  JVMState* jvms = sync_jvms();
  assert(!jvms->map()->has_exceptions(), "no exceptions on this map yet");
  jvms->map()->set_next_exception(_exceptions);
  _exceptions = NULL;   // done with this set of exceptions
  return jvms;
}

static inline void add_n_reqs(Node* dstphi, Node* srcphi) {
  assert(is_hidden_merge(dstphi), "must be a special merge node");
  assert(is_hidden_merge(srcphi), "must be a special merge node");
  uint limit = srcphi->req();
  for (uint i = PhiNode::Input; i < limit; i++) {
    dstphi->add_req(srcphi->in(i));
  }
}
static inline void add_one_req(Node* dstphi, Node* src) {
  assert(is_hidden_merge(dstphi), "must be a special merge node");
  assert(!is_hidden_merge(src), "must not be a special merge node");
  dstphi->add_req(src);
}

//-----------------------combine_exception_states------------------------------
// This helper function combines exception states by building phis on a
// specially marked state-merging region.  These regions and phis are
// untransformed, and can build up gradually.  The region is marked by
// having a control input of its exception map, rather than NULL.  Such
// regions do not appear except in this function, and in use_exception_state.
void GraphKit::combine_exception_states(SafePointNode* ex_map, SafePointNode* phi_map) {
  if (failing())  return;  // dying anyway...
  JVMState* ex_jvms = ex_map->_jvms;
  assert(ex_jvms->same_calls_as(phi_map->_jvms), "consistent call chains");
  assert(ex_jvms->stkoff() == phi_map->_jvms->stkoff(), "matching locals");
  assert(ex_jvms->sp() == phi_map->_jvms->sp(), "matching stack sizes");
  assert(ex_jvms->monoff() == phi_map->_jvms->monoff(), "matching JVMS");
  assert(ex_map->req() == phi_map->req(), "matching maps");
  uint tos = ex_jvms->stkoff() + ex_jvms->sp();
  Node*         hidden_merge_mark = root();
  Node*         region  = phi_map->control();
  MergeMemNode* phi_mem = phi_map->merged_memory();
  MergeMemNode* ex_mem  = ex_map->merged_memory();
  if (region->in(0) != hidden_merge_mark) {
    // The control input is not (yet) a specially-marked region in phi_map.
    // Make it so, and build some phis.
    region = new (C, 2) RegionNode(2);
    _gvn.set_type(region, Type::CONTROL);
    region->set_req(0, hidden_merge_mark);  // marks an internal ex-state
    region->init_req(1, phi_map->control());
    phi_map->set_control(region);
    Node* io_phi = PhiNode::make(region, phi_map->i_o(), Type::ABIO);
    record_for_igvn(io_phi);
    _gvn.set_type(io_phi, Type::ABIO);
    phi_map->set_i_o(io_phi);
    for (MergeMemStream mms(phi_mem); mms.next_non_empty(); ) {
      Node* m = mms.memory();
      Node* m_phi = PhiNode::make(region, m, Type::MEMORY, mms.adr_type(C));
      record_for_igvn(m_phi);
      _gvn.set_type(m_phi, Type::MEMORY);
      mms.set_memory(m_phi);
    }
  }

  // Either or both of phi_map and ex_map might already be converted into phis.
  Node* ex_control = ex_map->control();
  // if there is special marking on ex_map also, we add multiple edges from src
  bool add_multiple = (ex_control->in(0) == hidden_merge_mark);
  // how wide was the destination phi_map, originally?
  uint orig_width = region->req();

  if (add_multiple) {
    add_n_reqs(region, ex_control);
    add_n_reqs(phi_map->i_o(), ex_map->i_o());
  } else {
    // ex_map has no merges, so we just add single edges everywhere
    add_one_req(region, ex_control);
    add_one_req(phi_map->i_o(), ex_map->i_o());
  }
  for (MergeMemStream mms(phi_mem, ex_mem); mms.next_non_empty2(); ) {
    if (mms.is_empty()) {
      // get a copy of the base memory, and patch some inputs into it
      const TypePtr* adr_type = mms.adr_type(C);
      Node* phi = mms.force_memory()->as_Phi()->slice_memory(adr_type);
      assert(phi->as_Phi()->region() == mms.base_memory()->in(0), "");
      mms.set_memory(phi);
      // Prepare to append interesting stuff onto the newly sliced phi:
      while (phi->req() > orig_width)  phi->del_req(phi->req()-1);
    }
    // Append stuff from ex_map:
    if (add_multiple) {
      add_n_reqs(mms.memory(), mms.memory2());
    } else {
      add_one_req(mms.memory(), mms.memory2());
    }
  }
  uint limit = ex_map->req();
  for (uint i = TypeFunc::Parms; i < limit; i++) {
    // Skip everything in the JVMS after tos.  (The ex_oop follows.)
    if (i == tos)  i = ex_jvms->monoff();
    Node* src = ex_map->in(i);
    Node* dst = phi_map->in(i);
    if (src != dst) {
      PhiNode* phi;
      if (dst->in(0) != region) {
        dst = phi = PhiNode::make(region, dst, _gvn.type(dst));
        record_for_igvn(phi);
        _gvn.set_type(phi, phi->type());
        phi_map->set_req(i, dst);
        // Prepare to append interesting stuff onto the new phi:
        while (dst->req() > orig_width)  dst->del_req(dst->req()-1);
      } else {
        assert(dst->is_Phi(), "nobody else uses a hidden region");
        phi = (PhiNode*)dst;
      }
      if (add_multiple && src->in(0) == ex_control) {
        // Both are phis.
        add_n_reqs(dst, src);
      } else {
        while (dst->req() < region->req())  add_one_req(dst, src);
      }
      const Type* srctype = _gvn.type(src);
      if (phi->type() != srctype) {
        const Type* dsttype = phi->type()->meet(srctype);
        if (phi->type() != dsttype) {
          phi->set_type(dsttype);
          _gvn.set_type(phi, dsttype);
        }
      }
    }
  }
}

//--------------------------use_exception_state--------------------------------
Node* GraphKit::use_exception_state(SafePointNode* phi_map) {
  if (failing()) { stop(); return top(); }
  Node* region = phi_map->control();
  Node* hidden_merge_mark = root();
  assert(phi_map->jvms()->map() == phi_map, "sanity: 1-1 relation");
  Node* ex_oop = clear_saved_ex_oop(phi_map);
  if (region->in(0) == hidden_merge_mark) {
    // Special marking for internal ex-states.  Process the phis now.
    region->set_req(0, region);  // now it's an ordinary region
    set_jvms(phi_map->jvms());   // ...so now we can use it as a map
    // Note: Setting the jvms also sets the bci and sp.
    set_control(_gvn.transform(region));
    uint tos = jvms()->stkoff() + sp();
    for (uint i = 1; i < tos; i++) {
      Node* x = phi_map->in(i);
      if (x->in(0) == region) {
        assert(x->is_Phi(), "expected a special phi");
        phi_map->set_req(i, _gvn.transform(x));
      }
    }
    for (MergeMemStream mms(merged_memory()); mms.next_non_empty(); ) {
      Node* x = mms.memory();
      if (x->in(0) == region) {
        assert(x->is_Phi(), "nobody else uses a hidden region");
        mms.set_memory(_gvn.transform(x));
      }
    }
    if (ex_oop->in(0) == region) {
      assert(ex_oop->is_Phi(), "expected a special phi");
      ex_oop = _gvn.transform(ex_oop);
    }
  } else {
    set_jvms(phi_map->jvms());
  }

  assert(!is_hidden_merge(phi_map->control()), "hidden ex. states cleared");
  assert(!is_hidden_merge(phi_map->i_o()), "hidden ex. states cleared");
  return ex_oop;
}

//---------------------------------java_bc-------------------------------------
Bytecodes::Code GraphKit::java_bc() const {
  ciMethod* method = this->method();
  int       bci    = this->bci();
  if (method != NULL && bci != InvocationEntryBci)
    return method->java_code_at_bci(bci);
  else
    return Bytecodes::_illegal;
}

void GraphKit::uncommon_trap_if_should_post_on_exceptions(Deoptimization::DeoptReason reason,
                                                          bool must_throw) {
    // if the exception capability is set, then we will generate code
    // to check the JavaThread.should_post_on_exceptions flag to see
    // if we actually need to report exception events (for this
    // thread).  If we don't need to report exception events, we will
    // take the normal fast path provided by add_exception_events.  If
    // exception event reporting is enabled for this thread, we will
    // take the uncommon_trap in the BuildCutout below.

    // first must access the should_post_on_exceptions_flag in this thread's JavaThread
    Node* jthread = _gvn.transform(new (C, 1) ThreadLocalNode());
    Node* adr = basic_plus_adr(top(), jthread, in_bytes(JavaThread::should_post_on_exceptions_flag_offset()));
    Node* should_post_flag = make_load(control(), adr, TypeInt::INT, T_INT, Compile::AliasIdxRaw, false);

    // Test the should_post_on_exceptions_flag vs. 0
    Node* chk = _gvn.transform( new (C, 3) CmpINode(should_post_flag, intcon(0)) );
    Node* tst = _gvn.transform( new (C, 2) BoolNode(chk, BoolTest::eq) );

    // Branch to slow_path if should_post_on_exceptions_flag was true
    { BuildCutout unless(this, tst, PROB_MAX);
      // Do not try anything fancy if we're notifying the VM on every throw.
      // Cf. case Bytecodes::_athrow in parse2.cpp.
      uncommon_trap(reason, Deoptimization::Action_none,
                    (ciKlass*)NULL, (char*)NULL, must_throw);
    }

}

//------------------------------builtin_throw----------------------------------
void GraphKit::builtin_throw(Deoptimization::DeoptReason reason, Node* arg) {
  bool must_throw = true;

  if (env()->jvmti_can_post_on_exceptions()) {
    // check if we must post exception events, take uncommon trap if so
    uncommon_trap_if_should_post_on_exceptions(reason, must_throw);
    // here if should_post_on_exceptions is false
    // continue on with the normal codegen
  }

  // If this particular condition has not yet happened at this
  // bytecode, then use the uncommon trap mechanism, and allow for
  // a future recompilation if several traps occur here.
  // If the throw is hot, try to use a more complicated inline mechanism
  // which keeps execution inside the compiled code.
  bool treat_throw_as_hot = false;
  ciMethodData* md = method()->method_data();

  if (ProfileTraps) {
    if (too_many_traps(reason)) {
      treat_throw_as_hot = true;
    }
    // (If there is no MDO at all, assume it is early in
    // execution, and that any deopts are part of the
    // startup transient, and don't need to be remembered.)

    // Also, if there is a local exception handler, treat all throws
    // as hot if there has been at least one in this method.
    if (C->trap_count(reason) != 0
        && method()->method_data()->trap_count(reason) != 0
        && has_ex_handler()) {
        treat_throw_as_hot = true;
    }
  }

  // If this throw happens frequently, an uncommon trap might cause
  // a performance pothole.  If there is a local exception handler,
  // and if this particular bytecode appears to be deoptimizing often,
  // let us handle the throw inline, with a preconstructed instance.
  // Note:   If the deopt count has blown up, the uncommon trap
  // runtime is going to flush this nmethod, not matter what.
  if (treat_throw_as_hot
      && (!StackTraceInThrowable || OmitStackTraceInFastThrow)) {
    // If the throw is local, we use a pre-existing instance and
    // punt on the backtrace.  This would lead to a missing backtrace
    // (a repeat of 4292742) if the backtrace object is ever asked
    // for its backtrace.
    // Fixing this remaining case of 4292742 requires some flavor of
    // escape analysis.  Leave that for the future.
    ciInstance* ex_obj = NULL;
    switch (reason) {
    case Deoptimization::Reason_null_check:
      ex_obj = env()->NullPointerException_instance();
      break;
    case Deoptimization::Reason_div0_check:
      ex_obj = env()->ArithmeticException_instance();
      break;
    case Deoptimization::Reason_range_check:
      ex_obj = env()->ArrayIndexOutOfBoundsException_instance();
      break;
    case Deoptimization::Reason_class_check:
      if (java_bc() == Bytecodes::_aastore) {
        ex_obj = env()->ArrayStoreException_instance();
      } else {
        ex_obj = env()->ClassCastException_instance();
      }
      break;
    }
    if (failing()) { stop(); return; }  // exception allocation might fail
    if (ex_obj != NULL) {
      // Cheat with a preallocated exception object.
      if (C->log() != NULL)
        C->log()->elem("hot_throw preallocated='1' reason='%s'",
                       Deoptimization::trap_reason_name(reason));
      const TypeInstPtr* ex_con  = TypeInstPtr::make(ex_obj);
      Node*              ex_node = _gvn.transform( ConNode::make(C, ex_con) );

      // Clear the detail message of the preallocated exception object.
      // Weblogic sometimes mutates the detail message of exceptions
      // using reflection.
      int offset = java_lang_Throwable::get_detailMessage_offset();
      const TypePtr* adr_typ = ex_con->add_offset(offset);

      Node *adr = basic_plus_adr(ex_node, ex_node, offset);
      const TypeOopPtr* val_type = TypeOopPtr::make_from_klass(env()->String_klass());
      Node *store = store_oop_to_object(control(), ex_node, adr, adr_typ, null(), val_type, T_OBJECT);

      add_exception_state(make_exception_state(ex_node));
      return;
    }
  }

  // %%% Maybe add entry to OptoRuntime which directly throws the exc.?
  // It won't be much cheaper than bailing to the interp., since we'll
  // have to pass up all the debug-info, and the runtime will have to
  // create the stack trace.

  // Usual case:  Bail to interpreter.
  // Reserve the right to recompile if we haven't seen anything yet.

  Deoptimization::DeoptAction action = Deoptimization::Action_maybe_recompile;
  if (treat_throw_as_hot
      && (method()->method_data()->trap_recompiled_at(bci())
          || C->too_many_traps(reason))) {
    // We cannot afford to take more traps here.  Suffer in the interpreter.
    if (C->log() != NULL)
      C->log()->elem("hot_throw preallocated='0' reason='%s' mcount='%d'",
                     Deoptimization::trap_reason_name(reason),
                     C->trap_count(reason));
    action = Deoptimization::Action_none;
  }

  // "must_throw" prunes the JVM state to include only the stack, if there
  // are no local exception handlers.  This should cut down on register
  // allocation time and code size, by drastically reducing the number
  // of in-edges on the call to the uncommon trap.

  uncommon_trap(reason, action, (ciKlass*)NULL, (char*)NULL, must_throw);
}


//----------------------------PreserveJVMState---------------------------------
PreserveJVMState::PreserveJVMState(GraphKit* kit, bool clone_map) {
  debug_only(kit->verify_map());
  _kit    = kit;
  _map    = kit->map();   // preserve the map
  _sp     = kit->sp();
  kit->set_map(clone_map ? kit->clone_map() : NULL);
#ifdef ASSERT
  _bci    = kit->bci();
  Parse* parser = kit->is_Parse();
  int block = (parser == NULL || parser->block() == NULL) ? -1 : parser->block()->rpo();
  _block  = block;
#endif
}
PreserveJVMState::~PreserveJVMState() {
  GraphKit* kit = _kit;
#ifdef ASSERT
  assert(kit->bci() == _bci, "bci must not shift");
  Parse* parser = kit->is_Parse();
  int block = (parser == NULL || parser->block() == NULL) ? -1 : parser->block()->rpo();
  assert(block == _block,    "block must not shift");
#endif
  kit->set_map(_map);
  kit->set_sp(_sp);
}


//-----------------------------BuildCutout-------------------------------------
BuildCutout::BuildCutout(GraphKit* kit, Node* p, float prob, float cnt)
  : PreserveJVMState(kit)
{
  assert(p->is_Con() || p->is_Bool(), "test must be a bool");
  SafePointNode* outer_map = _map;   // preserved map is caller's
  SafePointNode* inner_map = kit->map();
  IfNode* iff = kit->create_and_map_if(outer_map->control(), p, prob, cnt);
  outer_map->set_control(kit->gvn().transform( new (kit->C, 1) IfTrueNode(iff) ));
  inner_map->set_control(kit->gvn().transform( new (kit->C, 1) IfFalseNode(iff) ));
}
BuildCutout::~BuildCutout() {
  GraphKit* kit = _kit;
  assert(kit->stopped(), "cutout code must stop, throw, return, etc.");
}

//---------------------------PreserveReexecuteState----------------------------
PreserveReexecuteState::PreserveReexecuteState(GraphKit* kit) {
  assert(!kit->stopped(), "must call stopped() before");
  _kit    =    kit;
  _sp     =    kit->sp();
  _reexecute = kit->jvms()->_reexecute;
}
PreserveReexecuteState::~PreserveReexecuteState() {
  if (_kit->stopped()) return;
  _kit->jvms()->_reexecute = _reexecute;
  _kit->set_sp(_sp);
}

//------------------------------clone_map--------------------------------------
// Implementation of PreserveJVMState
//
// Only clone_map(...) here. If this function is only used in the
// PreserveJVMState class we may want to get rid of this extra
// function eventually and do it all there.

SafePointNode* GraphKit::clone_map() {
  if (map() == NULL)  return NULL;

  // Clone the memory edge first
  Node* mem = MergeMemNode::make(C, map()->memory());
  gvn().set_type_bottom(mem);

  SafePointNode *clonemap = (SafePointNode*)map()->clone();
  JVMState* jvms = this->jvms();
  JVMState* clonejvms = jvms->clone_shallow(C);
  clonemap->set_memory(mem);
  clonemap->set_jvms(clonejvms);
  clonejvms->set_map(clonemap);
  record_for_igvn(clonemap);
  gvn().set_type_bottom(clonemap);
  return clonemap;
}


//-----------------------------set_map_clone-----------------------------------
void GraphKit::set_map_clone(SafePointNode* m) {
  _map = m;
  _map = clone_map();
  _map->set_next_exception(NULL);
  debug_only(verify_map());
}


//----------------------------kill_dead_locals---------------------------------
// Detect any locals which are known to be dead, and force them to top.
void GraphKit::kill_dead_locals() {
  // Consult the liveness information for the locals.  If any
  // of them are unused, then they can be replaced by top().  This
  // should help register allocation time and cut down on the size
  // of the deoptimization information.

  // This call is made from many of the bytecode handling
  // subroutines called from the Big Switch in do_one_bytecode.
  // Every bytecode which might include a slow path is responsible
  // for killing its dead locals.  The more consistent we
  // are about killing deads, the fewer useless phis will be
  // constructed for them at various merge points.

  // bci can be -1 (InvocationEntryBci).  We return the entry
  // liveness for the method.

  if (method() == NULL || method()->code_size() == 0) {
    // We are building a graph for a call to a native method.
    // All locals are live.
    return;
  }

  ResourceMark rm;

  // Consult the liveness information for the locals.  If any
  // of them are unused, then they can be replaced by top().  This
  // should help register allocation time and cut down on the size
  // of the deoptimization information.
  MethodLivenessResult live_locals = method()->liveness_at_bci(bci());

  int len = (int)live_locals.size();
  assert(len <= jvms()->loc_size(), "too many live locals");
  for (int local = 0; local < len; local++) {
    if (!live_locals.at(local)) {
      set_local(local, top());
    }
  }
}

#ifdef ASSERT
//-------------------------dead_locals_are_killed------------------------------
// Return true if all dead locals are set to top in the map.
// Used to assert "clean" debug info at various points.
bool GraphKit::dead_locals_are_killed() {
  if (method() == NULL || method()->code_size() == 0) {
    // No locals need to be dead, so all is as it should be.
    return true;
  }

  // Make sure somebody called kill_dead_locals upstream.
  ResourceMark rm;
  for (JVMState* jvms = this->jvms(); jvms != NULL; jvms = jvms->caller()) {
    if (jvms->loc_size() == 0)  continue;  // no locals to consult
    SafePointNode* map = jvms->map();
    ciMethod* method = jvms->method();
    int       bci    = jvms->bci();
    if (jvms == this->jvms()) {
      bci = this->bci();  // it might not yet be synched
    }
    MethodLivenessResult live_locals = method->liveness_at_bci(bci);
    int len = (int)live_locals.size();
    if (!live_locals.is_valid() || len == 0)
      // This method is trivial, or is poisoned by a breakpoint.
      return true;
    assert(len == jvms->loc_size(), "live map consistent with locals map");
    for (int local = 0; local < len; local++) {
      if (!live_locals.at(local) && map->local(jvms, local) != top()) {
        if (PrintMiscellaneous && (Verbose || WizardMode)) {
          tty->print_cr("Zombie local %d: ", local);
          jvms->dump();
        }
        return false;
      }
    }
  }
  return true;
}

#endif //ASSERT

// Helper function for enforcing certain bytecodes to reexecute if
// deoptimization happens
static bool should_reexecute_implied_by_bytecode(JVMState *jvms, bool is_anewarray) {
  ciMethod* cur_method = jvms->method();
  int       cur_bci   = jvms->bci();
  if (cur_method != NULL && cur_bci != InvocationEntryBci) {
    Bytecodes::Code code = cur_method->java_code_at_bci(cur_bci);
    return Interpreter::bytecode_should_reexecute(code) ||
           is_anewarray && code == Bytecodes::_multianewarray;
    // Reexecute _multianewarray bytecode which was replaced with
    // sequence of [a]newarray. See Parse::do_multianewarray().
    //
    // Note: interpreter should not have it set since this optimization
    // is limited by dimensions and guarded by flag so in some cases
    // multianewarray() runtime calls will be generated and
    // the bytecode should not be reexecutes (stack will not be reset).
  } else
    return false;
}

// Helper function for adding JVMState and debug information to node
void GraphKit::add_safepoint_edges(SafePointNode* call, bool must_throw) {
  // Add the safepoint edges to the call (or other safepoint).

  // Make sure dead locals are set to top.  This
  // should help register allocation time and cut down on the size
  // of the deoptimization information.
  assert(dead_locals_are_killed(), "garbage in debug info before safepoint");

  // Walk the inline list to fill in the correct set of JVMState's
  // Also fill in the associated edges for each JVMState.

  JVMState* youngest_jvms = sync_jvms();

  // If we are guaranteed to throw, we can prune everything but the
  // input to the current bytecode.
  bool can_prune_locals = false;
  uint stack_slots_not_pruned = 0;
  int inputs = 0, depth = 0;
  if (must_throw) {
    assert(method() == youngest_jvms->method(), "sanity");
    if (compute_stack_effects(inputs, depth)) {
      can_prune_locals = true;
      stack_slots_not_pruned = inputs;
    }
  }

  if (env()->jvmti_can_access_local_variables()) {
    // At any safepoint, this method can get breakpointed, which would
    // then require an immediate deoptimization.
    can_prune_locals = false;  // do not prune locals
    stack_slots_not_pruned = 0;
  }

  // do not scribble on the input jvms
  JVMState* out_jvms = youngest_jvms->clone_deep(C);
  call->set_jvms(out_jvms); // Start jvms list for call node

  // For a known set of bytecodes, the interpreter should reexecute them if
  // deoptimization happens. We set the reexecute state for them here
  if (out_jvms->is_reexecute_undefined() && //don't change if already specified
      should_reexecute_implied_by_bytecode(out_jvms, call->is_AllocateArray())) {
    out_jvms->set_should_reexecute(true); //NOTE: youngest_jvms not changed
  }

  // Presize the call:
  debug_only(uint non_debug_edges = call->req());
  call->add_req_batch(top(), youngest_jvms->debug_depth());
  assert(call->req() == non_debug_edges + youngest_jvms->debug_depth(), "");

  // Set up edges so that the call looks like this:
  //  Call [state:] ctl io mem fptr retadr
  //       [parms:] parm0 ... parmN
  //       [root:]  loc0 ... locN stk0 ... stkSP mon0 obj0 ... monN objN
  //    [...mid:]   loc0 ... locN stk0 ... stkSP mon0 obj0 ... monN objN [...]
  //       [young:] loc0 ... locN stk0 ... stkSP mon0 obj0 ... monN objN
  // Note that caller debug info precedes callee debug info.

  // Fill pointer walks backwards from "young:" to "root:" in the diagram above:
  uint debug_ptr = call->req();

  // Loop over the map input edges associated with jvms, add them
  // to the call node, & reset all offsets to match call node array.
  for (JVMState* in_jvms = youngest_jvms; in_jvms != NULL; ) {
    uint debug_end   = debug_ptr;
    uint debug_start = debug_ptr - in_jvms->debug_size();
    debug_ptr = debug_start;  // back up the ptr

    uint p = debug_start;  // walks forward in [debug_start, debug_end)
    uint j, k, l;
    SafePointNode* in_map = in_jvms->map();
    out_jvms->set_map(call);

    if (can_prune_locals) {
      assert(in_jvms->method() == out_jvms->method(), "sanity");
      // If the current throw can reach an exception handler in this JVMS,
      // then we must keep everything live that can reach that handler.
      // As a quick and dirty approximation, we look for any handlers at all.
      if (in_jvms->method()->has_exception_handlers()) {
        can_prune_locals = false;
      }
    }

    // Add the Locals
    k = in_jvms->locoff();
    l = in_jvms->loc_size();
    out_jvms->set_locoff(p);
    if (!can_prune_locals) {
      for (j = 0; j < l; j++)
        call->set_req(p++, in_map->in(k+j));
    } else {
      p += l;  // already set to top above by add_req_batch
    }

    // Add the Expression Stack
    k = in_jvms->stkoff();
    l = in_jvms->sp();
    out_jvms->set_stkoff(p);
    if (!can_prune_locals) {
      for (j = 0; j < l; j++)
        call->set_req(p++, in_map->in(k+j));
    } else if (can_prune_locals && stack_slots_not_pruned != 0) {
      // Divide stack into {S0,...,S1}, where S0 is set to top.
      uint s1 = stack_slots_not_pruned;
      stack_slots_not_pruned = 0;  // for next iteration
      if (s1 > l)  s1 = l;
      uint s0 = l - s1;
      p += s0;  // skip the tops preinstalled by add_req_batch
      for (j = s0; j < l; j++)
        call->set_req(p++, in_map->in(k+j));
    } else {
      p += l;  // already set to top above by add_req_batch
    }

    // Add the Monitors
    k = in_jvms->monoff();
    l = in_jvms->mon_size();
    out_jvms->set_monoff(p);
    for (j = 0; j < l; j++)
      call->set_req(p++, in_map->in(k+j));

    // Copy any scalar object fields.
    k = in_jvms->scloff();
    l = in_jvms->scl_size();
    out_jvms->set_scloff(p);
    for (j = 0; j < l; j++)
      call->set_req(p++, in_map->in(k+j));

    // Finish the new jvms.
    out_jvms->set_endoff(p);

    assert(out_jvms->endoff()     == debug_end,             "fill ptr must match");
    assert(out_jvms->depth()      == in_jvms->depth(),      "depth must match");
    assert(out_jvms->loc_size()   == in_jvms->loc_size(),   "size must match");
    assert(out_jvms->mon_size()   == in_jvms->mon_size(),   "size must match");
    assert(out_jvms->scl_size()   == in_jvms->scl_size(),   "size must match");
    assert(out_jvms->debug_size() == in_jvms->debug_size(), "size must match");

    // Update the two tail pointers in parallel.
    out_jvms = out_jvms->caller();
    in_jvms  = in_jvms->caller();
  }

  assert(debug_ptr == non_debug_edges, "debug info must fit exactly");

  // Test the correctness of JVMState::debug_xxx accessors:
  assert(call->jvms()->debug_start() == non_debug_edges, "");
  assert(call->jvms()->debug_end()   == call->req(), "");
  assert(call->jvms()->debug_depth() == call->req() - non_debug_edges, "");
}

bool GraphKit::compute_stack_effects(int& inputs, int& depth) {
  Bytecodes::Code code = java_bc();
  if (code == Bytecodes::_wide) {
    code = method()->java_code_at_bci(bci() + 1);
  }

  BasicType rtype = T_ILLEGAL;
  int       rsize = 0;

  if (code != Bytecodes::_illegal) {
    depth = Bytecodes::depth(code); // checkcast=0, athrow=-1
    rtype = Bytecodes::result_type(code); // checkcast=P, athrow=V
    if (rtype < T_CONFLICT)
      rsize = type2size[rtype];
  }

  switch (code) {
  case Bytecodes::_illegal:
    return false;

  case Bytecodes::_ldc:
  case Bytecodes::_ldc_w:
  case Bytecodes::_ldc2_w:
    inputs = 0;
    break;

  case Bytecodes::_dup:         inputs = 1;  break;
  case Bytecodes::_dup_x1:      inputs = 2;  break;
  case Bytecodes::_dup_x2:      inputs = 3;  break;
  case Bytecodes::_dup2:        inputs = 2;  break;
  case Bytecodes::_dup2_x1:     inputs = 3;  break;
  case Bytecodes::_dup2_x2:     inputs = 4;  break;
  case Bytecodes::_swap:        inputs = 2;  break;
  case Bytecodes::_arraylength: inputs = 1;  break;

  case Bytecodes::_getstatic:
  case Bytecodes::_putstatic:
  case Bytecodes::_getfield:
  case Bytecodes::_putfield:
    {
      bool is_get = (depth >= 0), is_static = (depth & 1);
      bool ignore;
      ciBytecodeStream iter(method());
      iter.reset_to_bci(bci());
      iter.next();
      ciField* field = iter.get_field(ignore);
      int      size  = field->type()->size();
      inputs  = (is_static ? 0 : 1);
      if (is_get) {
        depth = size - inputs;
      } else {
        inputs += size;        // putxxx pops the value from the stack
        depth = - inputs;
      }
    }
    break;

  case Bytecodes::_invokevirtual:
  case Bytecodes::_invokespecial:
  case Bytecodes::_invokestatic:
  case Bytecodes::_invokedynamic:
  case Bytecodes::_invokeinterface:
    {
      bool ignore;
      ciBytecodeStream iter(method());
      iter.reset_to_bci(bci());
      iter.next();
      ciMethod* method = iter.get_method(ignore);
      inputs = method->arg_size_no_receiver();
      // Add a receiver argument, maybe:
      if (code != Bytecodes::_invokestatic &&
          code != Bytecodes::_invokedynamic)
        inputs += 1;
      // (Do not use ciMethod::arg_size(), because
      // it might be an unloaded method, which doesn't
      // know whether it is static or not.)
      int size = method->return_type()->size();
      depth = size - inputs;
    }
    break;

  case Bytecodes::_multianewarray:
    {
      ciBytecodeStream iter(method());
      iter.reset_to_bci(bci());
      iter.next();
      inputs = iter.get_dimensions();
      assert(rsize == 1, "");
      depth = rsize - inputs;
    }
    break;

  case Bytecodes::_ireturn:
  case Bytecodes::_lreturn:
  case Bytecodes::_freturn:
  case Bytecodes::_dreturn:
  case Bytecodes::_areturn:
    assert(rsize = -depth, "");
    inputs = rsize;
    break;

  case Bytecodes::_jsr:
  case Bytecodes::_jsr_w:
    inputs = 0;
    depth  = 1;                  // S.B. depth=1, not zero
    break;

  default:
    // bytecode produces a typed result
    inputs = rsize - depth;
    assert(inputs >= 0, "");
    break;
  }

#ifdef ASSERT
  // spot check
  int outputs = depth + inputs;
  assert(outputs >= 0, "sanity");
  switch (code) {
  case Bytecodes::_checkcast: assert(inputs == 1 && outputs == 1, ""); break;
  case Bytecodes::_athrow:    assert(inputs == 1 && outputs == 0, ""); break;
  case Bytecodes::_aload_0:   assert(inputs == 0 && outputs == 1, ""); break;
  case Bytecodes::_return:    assert(inputs == 0 && outputs == 0, ""); break;
  case Bytecodes::_drem:      assert(inputs == 4 && outputs == 2, ""); break;
  }
#endif //ASSERT

  return true;
}



//------------------------------basic_plus_adr---------------------------------
Node* GraphKit::basic_plus_adr(Node* base, Node* ptr, Node* offset) {
  // short-circuit a common case
  if (offset == intcon(0))  return ptr;
  return _gvn.transform( new (C, 4) AddPNode(base, ptr, offset) );
}

Node* GraphKit::ConvI2L(Node* offset) {
  // short-circuit a common case
  jint offset_con = find_int_con(offset, Type::OffsetBot);
  if (offset_con != Type::OffsetBot) {
    return longcon((long) offset_con);
  }
  return _gvn.transform( new (C, 2) ConvI2LNode(offset));
}
Node* GraphKit::ConvL2I(Node* offset) {
  // short-circuit a common case
  jlong offset_con = find_long_con(offset, (jlong)Type::OffsetBot);
  if (offset_con != (jlong)Type::OffsetBot) {
    return intcon((int) offset_con);
  }
  return _gvn.transform( new (C, 2) ConvL2INode(offset));
}

//-------------------------load_object_klass-----------------------------------
Node* GraphKit::load_object_klass(Node* obj) {
  // Special-case a fresh allocation to avoid building nodes:
  Node* akls = AllocateNode::Ideal_klass(obj, &_gvn);
  if (akls != NULL)  return akls;
  Node* k_adr = basic_plus_adr(obj, oopDesc::klass_offset_in_bytes());
  return _gvn.transform( LoadKlassNode::make(_gvn, immutable_memory(), k_adr, TypeInstPtr::KLASS) );
}

//-------------------------load_array_length-----------------------------------
Node* GraphKit::load_array_length(Node* array) {
  // Special-case a fresh allocation to avoid building nodes:
  AllocateArrayNode* alloc = AllocateArrayNode::Ideal_array_allocation(array, &_gvn);
  Node *alen;
  if (alloc == NULL) {
    Node *r_adr = basic_plus_adr(array, arrayOopDesc::length_offset_in_bytes());
    alen = _gvn.transform( new (C, 3) LoadRangeNode(0, immutable_memory(), r_adr, TypeInt::POS));
  } else {
    alen = alloc->Ideal_length();
    Node* ccast = alloc->make_ideal_length(_gvn.type(array)->is_oopptr(), &_gvn);
    if (ccast != alen) {
      alen = _gvn.transform(ccast);
    }
  }
  return alen;
}

//------------------------------do_null_check----------------------------------
// Helper function to do a NULL pointer check.  Returned value is
// the incoming address with NULL casted away.  You are allowed to use the
// not-null value only if you are control dependent on the test.
extern int explicit_null_checks_inserted,
           explicit_null_checks_elided;
Node* GraphKit::null_check_common(Node* value, BasicType type,
                                  // optional arguments for variations:
                                  bool assert_null,
                                  Node* *null_control) {
  assert(!assert_null || null_control == NULL, "not both at once");
  if (stopped())  return top();
  if (!GenerateCompilerNullChecks && !assert_null && null_control == NULL) {
    // For some performance testing, we may wish to suppress null checking.
    value = cast_not_null(value);   // Make it appear to be non-null (4962416).
    return value;
  }
  explicit_null_checks_inserted++;

  // Construct NULL check
  Node *chk = NULL;
  switch(type) {
    case T_LONG   : chk = new (C, 3) CmpLNode(value, _gvn.zerocon(T_LONG)); break;
    case T_INT    : chk = new (C, 3) CmpINode( value, _gvn.intcon(0)); break;
    case T_ARRAY  : // fall through
      type = T_OBJECT;  // simplify further tests
    case T_OBJECT : {
      const Type *t = _gvn.type( value );

      const TypeOopPtr* tp = t->isa_oopptr();
      if (tp != NULL && tp->klass() != NULL && !tp->klass()->is_loaded()
          // Only for do_null_check, not any of its siblings:
          && !assert_null && null_control == NULL) {
        // Usually, any field access or invocation on an unloaded oop type
        // will simply fail to link, since the statically linked class is
        // likely also to be unloaded.  However, in -Xcomp mode, sometimes
        // the static class is loaded but the sharper oop type is not.
        // Rather than checking for this obscure case in lots of places,
        // we simply observe that a null check on an unloaded class
        // will always be followed by a nonsense operation, so we
        // can just issue the uncommon trap here.
        // Our access to the unloaded class will only be correct
        // after it has been loaded and initialized, which requires
        // a trip through the interpreter.
#ifndef PRODUCT
        if (WizardMode) { tty->print("Null check of unloaded "); tp->klass()->print(); tty->cr(); }
#endif
        uncommon_trap(Deoptimization::Reason_unloaded,
                      Deoptimization::Action_reinterpret,
                      tp->klass(), "!loaded");
        return top();
      }

      if (assert_null) {
        // See if the type is contained in NULL_PTR.
        // If so, then the value is already null.
        if (t->higher_equal(TypePtr::NULL_PTR)) {
          explicit_null_checks_elided++;
          return value;           // Elided null assert quickly!
        }
      } else {
        // See if mixing in the NULL pointer changes type.
        // If so, then the NULL pointer was not allowed in the original
        // type.  In other words, "value" was not-null.
        if (t->meet(TypePtr::NULL_PTR) != t) {
          // same as: if (!TypePtr::NULL_PTR->higher_equal(t)) ...
          explicit_null_checks_elided++;
          return value;           // Elided null check quickly!
        }
      }
      chk = new (C, 3) CmpPNode( value, null() );
      break;
    }

    default      : ShouldNotReachHere();
  }
  assert(chk != NULL, "sanity check");
  chk = _gvn.transform(chk);

  BoolTest::mask btest = assert_null ? BoolTest::eq : BoolTest::ne;
  BoolNode *btst = new (C, 2) BoolNode( chk, btest);
  Node   *tst = _gvn.transform( btst );

  //-----------
  // if peephole optimizations occurred, a prior test existed.
  // If a prior test existed, maybe it dominates as we can avoid this test.
  if (tst != btst && type == T_OBJECT) {
    // At this point we want to scan up the CFG to see if we can
    // find an identical test (and so avoid this test altogether).
    Node *cfg = control();
    int depth = 0;
    while( depth < 16 ) {       // Limit search depth for speed
      if( cfg->Opcode() == Op_IfTrue &&
          cfg->in(0)->in(1) == tst ) {
        // Found prior test.  Use "cast_not_null" to construct an identical
        // CastPP (and hence hash to) as already exists for the prior test.
        // Return that casted value.
        if (assert_null) {
          replace_in_map(value, null());
          return null();  // do not issue the redundant test
        }
        Node *oldcontrol = control();
        set_control(cfg);
        Node *res = cast_not_null(value);
        set_control(oldcontrol);
        explicit_null_checks_elided++;
        return res;
      }
      cfg = IfNode::up_one_dom(cfg, /*linear_only=*/ true);
      if (cfg == NULL)  break;  // Quit at region nodes
      depth++;
    }
  }

  //-----------
  // Branch to failure if null
  float ok_prob = PROB_MAX;  // a priori estimate:  nulls never happen
  Deoptimization::DeoptReason reason;
  if (assert_null)
    reason = Deoptimization::Reason_null_assert;
  else if (type == T_OBJECT)
    reason = Deoptimization::Reason_null_check;
  else
    reason = Deoptimization::Reason_div0_check;

  // %%% Since Reason_unhandled is not recorded on a per-bytecode basis,
  // ciMethodData::has_trap_at will return a conservative -1 if any
  // must-be-null assertion has failed.  This could cause performance
  // problems for a method after its first do_null_assert failure.
  // Consider using 'Reason_class_check' instead?

  // To cause an implicit null check, we set the not-null probability
  // to the maximum (PROB_MAX).  For an explicit check the probability
  // is set to a smaller value.
  if (null_control != NULL || too_many_traps(reason)) {
    // probability is less likely
    ok_prob =  PROB_LIKELY_MAG(3);
  } else if (!assert_null &&
             (ImplicitNullCheckThreshold > 0) &&
             method() != NULL &&
             (method()->method_data()->trap_count(reason)
              >= (uint)ImplicitNullCheckThreshold)) {
    ok_prob =  PROB_LIKELY_MAG(3);
  }

  if (null_control != NULL) {
    IfNode* iff = create_and_map_if(control(), tst, ok_prob, COUNT_UNKNOWN);
    Node* null_true = _gvn.transform( new (C, 1) IfFalseNode(iff));
    set_control(      _gvn.transform( new (C, 1) IfTrueNode(iff)));
    if (null_true == top())
      explicit_null_checks_elided++;
    (*null_control) = null_true;
  } else {
    BuildCutout unless(this, tst, ok_prob);
    // Check for optimizer eliding test at parse time
    if (stopped()) {
      // Failure not possible; do not bother making uncommon trap.
      explicit_null_checks_elided++;
    } else if (assert_null) {
      uncommon_trap(reason,
                    Deoptimization::Action_make_not_entrant,
                    NULL, "assert_null");
    } else {
      replace_in_map(value, zerocon(type));
      builtin_throw(reason);
    }
  }

  // Must throw exception, fall-thru not possible?
  if (stopped()) {
    return top();               // No result
  }

  if (assert_null) {
    // Cast obj to null on this path.
    replace_in_map(value, zerocon(type));
    return zerocon(type);
  }

  // Cast obj to not-null on this path, if there is no null_control.
  // (If there is a null_control, a non-null value may come back to haunt us.)
  if (type == T_OBJECT) {
    Node* cast = cast_not_null(value, false);
    if (null_control == NULL || (*null_control) == top())
      replace_in_map(value, cast);
    value = cast;
  }

  return value;
}


//------------------------------cast_not_null----------------------------------
// Cast obj to not-null on this path
Node* GraphKit::cast_not_null(Node* obj, bool do_replace_in_map) {
  const Type *t = _gvn.type(obj);
  const Type *t_not_null = t->join(TypePtr::NOTNULL);
  // Object is already not-null?
  if( t == t_not_null ) return obj;

  Node *cast = new (C, 2) CastPPNode(obj,t_not_null);
  cast->init_req(0, control());
  cast = _gvn.transform( cast );

  // Scan for instances of 'obj' in the current JVM mapping.
  // These instances are known to be not-null after the test.
  if (do_replace_in_map)
    replace_in_map(obj, cast);

  return cast;                  // Return casted value
}


//--------------------------replace_in_map-------------------------------------
void GraphKit::replace_in_map(Node* old, Node* neww) {
  this->map()->replace_edge(old, neww);

  // Note: This operation potentially replaces any edge
  // on the map.  This includes locals, stack, and monitors
  // of the current (innermost) JVM state.

  // We can consider replacing in caller maps.
  // The idea would be that an inlined function's null checks
  // can be shared with the entire inlining tree.
  // The expense of doing this is that the PreserveJVMState class
  // would have to preserve caller states too, with a deep copy.
}



//=============================================================================
//--------------------------------memory---------------------------------------
Node* GraphKit::memory(uint alias_idx) {
  MergeMemNode* mem = merged_memory();
  Node* p = mem->memory_at(alias_idx);
  _gvn.set_type(p, Type::MEMORY);  // must be mapped
  return p;
}

//-----------------------------reset_memory------------------------------------
Node* GraphKit::reset_memory() {
  Node* mem = map()->memory();
  // do not use this node for any more parsing!
  debug_only( map()->set_memory((Node*)NULL) );
  return _gvn.transform( mem );
}

//------------------------------set_all_memory---------------------------------
void GraphKit::set_all_memory(Node* newmem) {
  Node* mergemem = MergeMemNode::make(C, newmem);
  gvn().set_type_bottom(mergemem);
  map()->set_memory(mergemem);
}

//------------------------------set_all_memory_call----------------------------
void GraphKit::set_all_memory_call(Node* call, bool separate_io_proj) {
  Node* newmem = _gvn.transform( new (C, 1) ProjNode(call, TypeFunc::Memory, separate_io_proj) );
  set_all_memory(newmem);
}

//=============================================================================
//
// parser factory methods for MemNodes
//
// These are layered on top of the factory methods in LoadNode and StoreNode,
// and integrate with the parser's memory state and _gvn engine.
//

// factory methods in "int adr_idx"
Node* GraphKit::make_load(Node* ctl, Node* adr, const Type* t, BasicType bt,
                          int adr_idx,
                          bool require_atomic_access) {
  assert(adr_idx != Compile::AliasIdxTop, "use other make_load factory" );
  const TypePtr* adr_type = NULL; // debug-mode-only argument
  debug_only(adr_type = C->get_adr_type(adr_idx));
  Node* mem = memory(adr_idx);
  Node* ld;
  if (require_atomic_access && bt == T_LONG) {
    ld = LoadLNode::make_atomic(C, ctl, mem, adr, adr_type, t);
  } else {
    ld = LoadNode::make(_gvn, ctl, mem, adr, adr_type, t, bt);
  }
  return _gvn.transform(ld);
}

Node* GraphKit::store_to_memory(Node* ctl, Node* adr, Node *val, BasicType bt,
                                int adr_idx,
                                bool require_atomic_access) {
  assert(adr_idx != Compile::AliasIdxTop, "use other store_to_memory factory" );
  const TypePtr* adr_type = NULL;
  debug_only(adr_type = C->get_adr_type(adr_idx));
  Node *mem = memory(adr_idx);
  Node* st;
  if (require_atomic_access && bt == T_LONG) {
    st = StoreLNode::make_atomic(C, ctl, mem, adr, adr_type, val);
  } else {
    st = StoreNode::make(_gvn, ctl, mem, adr, adr_type, val, bt);
  }
  st = _gvn.transform(st);
  set_memory(st, adr_idx);
  // Back-to-back stores can only remove intermediate store with DU info
  // so push on worklist for optimizer.
  if (mem->req() > MemNode::Address && adr == mem->in(MemNode::Address))
    record_for_igvn(st);

  return st;
}


void GraphKit::pre_barrier(Node* ctl,
                           Node* obj,
                           Node* adr,
                           uint  adr_idx,
                           Node* val,
                           const TypeOopPtr* val_type,
                           BasicType bt) {
  BarrierSet* bs = Universe::heap()->barrier_set();
  set_control(ctl);
  switch (bs->kind()) {
    case BarrierSet::G1SATBCT:
    case BarrierSet::G1SATBCTLogging:
      g1_write_barrier_pre(obj, adr, adr_idx, val, val_type, bt);
      break;

    case BarrierSet::CardTableModRef:
    case BarrierSet::CardTableExtension:
    case BarrierSet::ModRef:
      break;

    case BarrierSet::Other:
    default      :
      ShouldNotReachHere();

  }
}

void GraphKit::post_barrier(Node* ctl,
                            Node* store,
                            Node* obj,
                            Node* adr,
                            uint  adr_idx,
                            Node* val,
                            BasicType bt,
                            bool use_precise) {
  BarrierSet* bs = Universe::heap()->barrier_set();
  set_control(ctl);
  switch (bs->kind()) {
    case BarrierSet::G1SATBCT:
    case BarrierSet::G1SATBCTLogging:
      g1_write_barrier_post(store, obj, adr, adr_idx, val, bt, use_precise);
      break;

    case BarrierSet::CardTableModRef:
    case BarrierSet::CardTableExtension:
      write_barrier_post(store, obj, adr, adr_idx, val, use_precise);
      break;

    case BarrierSet::ModRef:
      break;

    case BarrierSet::Other:
    default      :
      ShouldNotReachHere();

  }
}

Node* GraphKit::store_oop(Node* ctl,
                          Node* obj,
                          Node* adr,
                          const TypePtr* adr_type,
                          Node* val,
                          const TypeOopPtr* val_type,
                          BasicType bt,
                          bool use_precise) {

  set_control(ctl);
  if (stopped()) return top(); // Dead path ?

  assert(bt == T_OBJECT, "sanity");
  assert(val != NULL, "not dead path");
  uint adr_idx = C->get_alias_index(adr_type);
  assert(adr_idx != Compile::AliasIdxTop, "use other store_to_memory factory" );

  pre_barrier(control(), obj, adr, adr_idx, val, val_type, bt);
  Node* store = store_to_memory(control(), adr, val, bt, adr_idx);
  post_barrier(control(), store, obj, adr, adr_idx, val, bt, use_precise);
  return store;
}

// Could be an array or object we don't know at compile time (unsafe ref.)
Node* GraphKit::store_oop_to_unknown(Node* ctl,
                             Node* obj,   // containing obj
                             Node* adr,  // actual adress to store val at
                             const TypePtr* adr_type,
                             Node* val,
                             BasicType bt) {
  Compile::AliasType* at = C->alias_type(adr_type);
  const TypeOopPtr* val_type = NULL;
  if (adr_type->isa_instptr()) {
    if (at->field() != NULL) {
      // known field.  This code is a copy of the do_put_xxx logic.
      ciField* field = at->field();
      if (!field->type()->is_loaded()) {
        val_type = TypeInstPtr::BOTTOM;
      } else {
        val_type = TypeOopPtr::make_from_klass(field->type()->as_klass());
      }
    }
  } else if (adr_type->isa_aryptr()) {
    val_type = adr_type->is_aryptr()->elem()->make_oopptr();
  }
  if (val_type == NULL) {
    val_type = TypeInstPtr::BOTTOM;
  }
  return store_oop(ctl, obj, adr, adr_type, val, val_type, bt, true);
}


//-------------------------array_element_address-------------------------
Node* GraphKit::array_element_address(Node* ary, Node* idx, BasicType elembt,
                                      const TypeInt* sizetype) {
  uint shift  = exact_log2(type2aelembytes(elembt));
  uint header = arrayOopDesc::base_offset_in_bytes(elembt);

  // short-circuit a common case (saves lots of confusing waste motion)
  jint idx_con = find_int_con(idx, -1);
  if (idx_con >= 0) {
    intptr_t offset = header + ((intptr_t)idx_con << shift);
    return basic_plus_adr(ary, offset);
  }

  // must be correct type for alignment purposes
  Node* base  = basic_plus_adr(ary, header);
#ifdef _LP64
  // The scaled index operand to AddP must be a clean 64-bit value.
  // Java allows a 32-bit int to be incremented to a negative
  // value, which appears in a 64-bit register as a large
  // positive number.  Using that large positive number as an
  // operand in pointer arithmetic has bad consequences.
  // On the other hand, 32-bit overflow is rare, and the possibility
  // can often be excluded, if we annotate the ConvI2L node with
  // a type assertion that its value is known to be a small positive
  // number.  (The prior range check has ensured this.)
  // This assertion is used by ConvI2LNode::Ideal.
  int index_max = max_jint - 1;  // array size is max_jint, index is one less
  if (sizetype != NULL)  index_max = sizetype->_hi - 1;
  const TypeLong* lidxtype = TypeLong::make(CONST64(0), index_max, Type::WidenMax);
  idx = _gvn.transform( new (C, 2) ConvI2LNode(idx, lidxtype) );
#endif
  Node* scale = _gvn.transform( new (C, 3) LShiftXNode(idx, intcon(shift)) );
  return basic_plus_adr(ary, base, scale);
}

//-------------------------load_array_element-------------------------
Node* GraphKit::load_array_element(Node* ctl, Node* ary, Node* idx, const TypeAryPtr* arytype) {
  const Type* elemtype = arytype->elem();
  BasicType elembt = elemtype->array_element_basic_type();
  Node* adr = array_element_address(ary, idx, elembt, arytype->size());
  Node* ld = make_load(ctl, adr, elemtype, elembt, arytype);
  return ld;
}

//-------------------------set_arguments_for_java_call-------------------------
// Arguments (pre-popped from the stack) are taken from the JVMS.
void GraphKit::set_arguments_for_java_call(CallJavaNode* call) {
  // Add the call arguments:
  uint nargs = call->method()->arg_size();
  for (uint i = 0; i < nargs; i++) {
    Node* arg = argument(i);
    call->init_req(i + TypeFunc::Parms, arg);
  }
}

//---------------------------set_edges_for_java_call---------------------------
// Connect a newly created call into the current JVMS.
// A return value node (if any) is returned from set_edges_for_java_call.
void GraphKit::set_edges_for_java_call(CallJavaNode* call, bool must_throw, bool separate_io_proj) {

  // Add the predefined inputs:
  call->init_req( TypeFunc::Control, control() );
  call->init_req( TypeFunc::I_O    , i_o() );
  call->init_req( TypeFunc::Memory , reset_memory() );
  call->init_req( TypeFunc::FramePtr, frameptr() );
  call->init_req( TypeFunc::ReturnAdr, top() );

  add_safepoint_edges(call, must_throw);

  Node* xcall = _gvn.transform(call);

  if (xcall == top()) {
    set_control(top());
    return;
  }
  assert(xcall == call, "call identity is stable");

  // Re-use the current map to produce the result.

  set_control(_gvn.transform(new (C, 1) ProjNode(call, TypeFunc::Control)));
  set_i_o(    _gvn.transform(new (C, 1) ProjNode(call, TypeFunc::I_O    , separate_io_proj)));
  set_all_memory_call(xcall, separate_io_proj);

  //return xcall;   // no need, caller already has it
}

Node* GraphKit::set_results_for_java_call(CallJavaNode* call, bool separate_io_proj) {
  if (stopped())  return top();  // maybe the call folded up?

  // Capture the return value, if any.
  Node* ret;
  if (call->method() == NULL ||
      call->method()->return_type()->basic_type() == T_VOID)
        ret = top();
  else  ret = _gvn.transform(new (C, 1) ProjNode(call, TypeFunc::Parms));

  // Note:  Since any out-of-line call can produce an exception,
  // we always insert an I_O projection from the call into the result.

  make_slow_call_ex(call, env()->Throwable_klass(), separate_io_proj);

  if (separate_io_proj) {
    // The caller requested separate projections be used by the fall
    // through and exceptional paths, so replace the projections for
    // the fall through path.
    set_i_o(_gvn.transform( new (C, 1) ProjNode(call, TypeFunc::I_O) ));
    set_all_memory(_gvn.transform( new (C, 1) ProjNode(call, TypeFunc::Memory) ));
  }
  return ret;
}

//--------------------set_predefined_input_for_runtime_call--------------------
// Reading and setting the memory state is way conservative here.
// The real problem is that I am not doing real Type analysis on memory,
// so I cannot distinguish card mark stores from other stores.  Across a GC
// point the Store Barrier and the card mark memory has to agree.  I cannot
// have a card mark store and its barrier split across the GC point from
// either above or below.  Here I get that to happen by reading ALL of memory.
// A better answer would be to separate out card marks from other memory.
// For now, return the input memory state, so that it can be reused
// after the call, if this call has restricted memory effects.
Node* GraphKit::set_predefined_input_for_runtime_call(SafePointNode* call) {
  // Set fixed predefined input arguments
  Node* memory = reset_memory();
  call->init_req( TypeFunc::Control,   control()  );
  call->init_req( TypeFunc::I_O,       top()      ); // does no i/o
  call->init_req( TypeFunc::Memory,    memory     ); // may gc ptrs
  call->init_req( TypeFunc::FramePtr,  frameptr() );
  call->init_req( TypeFunc::ReturnAdr, top()      );
  return memory;
}

//-------------------set_predefined_output_for_runtime_call--------------------
// Set control and memory (not i_o) from the call.
// If keep_mem is not NULL, use it for the output state,
// except for the RawPtr output of the call, if hook_mem is TypeRawPtr::BOTTOM.
// If hook_mem is NULL, this call produces no memory effects at all.
// If hook_mem is a Java-visible memory slice (such as arraycopy operands),
// then only that memory slice is taken from the call.
// In the last case, we must put an appropriate memory barrier before
// the call, so as to create the correct anti-dependencies on loads
// preceding the call.
void GraphKit::set_predefined_output_for_runtime_call(Node* call,
                                                      Node* keep_mem,
                                                      const TypePtr* hook_mem) {
  // no i/o
  set_control(_gvn.transform( new (C, 1) ProjNode(call,TypeFunc::Control) ));
  if (keep_mem) {
    // First clone the existing memory state
    set_all_memory(keep_mem);
    if (hook_mem != NULL) {
      // Make memory for the call
      Node* mem = _gvn.transform( new (C, 1) ProjNode(call, TypeFunc::Memory) );
      // Set the RawPtr memory state only.  This covers all the heap top/GC stuff
      // We also use hook_mem to extract specific effects from arraycopy stubs.
      set_memory(mem, hook_mem);
    }
    // ...else the call has NO memory effects.

    // Make sure the call advertises its memory effects precisely.
    // This lets us build accurate anti-dependences in gcm.cpp.
    assert(C->alias_type(call->adr_type()) == C->alias_type(hook_mem),
           "call node must be constructed correctly");
  } else {
    assert(hook_mem == NULL, "");
    // This is not a "slow path" call; all memory comes from the call.
    set_all_memory_call(call);
  }
}


// Replace the call with the current state of the kit.
void GraphKit::replace_call(CallNode* call, Node* result) {
  JVMState* ejvms = NULL;
  if (has_exceptions()) {
    ejvms = transfer_exceptions_into_jvms();
  }

  SafePointNode* final_state = stop();

  // Find all the needed outputs of this call
  CallProjections callprojs;
  call->extract_projections(&callprojs, true);

  // Replace all the old call edges with the edges from the inlining result
  C->gvn_replace_by(callprojs.fallthrough_catchproj, final_state->in(TypeFunc::Control));
  C->gvn_replace_by(callprojs.fallthrough_memproj,   final_state->in(TypeFunc::Memory));
  C->gvn_replace_by(callprojs.fallthrough_ioproj,    final_state->in(TypeFunc::I_O));
  Node* final_mem = final_state->in(TypeFunc::Memory);

  // Replace the result with the new result if it exists and is used
  if (callprojs.resproj != NULL && result != NULL) {
    C->gvn_replace_by(callprojs.resproj, result);
  }

  if (ejvms == NULL) {
    // No exception edges to simply kill off those paths
    C->gvn_replace_by(callprojs.catchall_catchproj, C->top());
    C->gvn_replace_by(callprojs.catchall_memproj,   C->top());
    C->gvn_replace_by(callprojs.catchall_ioproj,    C->top());

    // Replace the old exception object with top
    if (callprojs.exobj != NULL) {
      C->gvn_replace_by(callprojs.exobj, C->top());
    }
  } else {
    GraphKit ekit(ejvms);

    // Load my combined exception state into the kit, with all phis transformed:
    SafePointNode* ex_map = ekit.combine_and_pop_all_exception_states();

    Node* ex_oop = ekit.use_exception_state(ex_map);

    C->gvn_replace_by(callprojs.catchall_catchproj, ekit.control());
    C->gvn_replace_by(callprojs.catchall_memproj,   ekit.reset_memory());
    C->gvn_replace_by(callprojs.catchall_ioproj,    ekit.i_o());

    // Replace the old exception object with the newly created one
    if (callprojs.exobj != NULL) {
      C->gvn_replace_by(callprojs.exobj, ex_oop);
    }
  }

  // Disconnect the call from the graph
  call->disconnect_inputs(NULL);
  C->gvn_replace_by(call, C->top());

  // Clean up any MergeMems that feed other MergeMems since the
  // optimizer doesn't like that.
  if (final_mem->is_MergeMem()) {
    Node_List wl;
    for (SimpleDUIterator i(final_mem); i.has_next(); i.next()) {
      Node* m = i.get();
      if (m->is_MergeMem() && !wl.contains(m)) {
        wl.push(m);
      }
    }
    while (wl.size()  > 0) {
      _gvn.transform(wl.pop());
    }
  }
}


//------------------------------increment_counter------------------------------
// for statistics: increment a VM counter by 1

void GraphKit::increment_counter(address counter_addr) {
  Node* adr1 = makecon(TypeRawPtr::make(counter_addr));
  increment_counter(adr1);
}

void GraphKit::increment_counter(Node* counter_addr) {
  int adr_type = Compile::AliasIdxRaw;
  Node* ctrl = control();
  Node* cnt  = make_load(ctrl, counter_addr, TypeInt::INT, T_INT, adr_type);
  Node* incr = _gvn.transform(new (C, 3) AddINode(cnt, _gvn.intcon(1)));
  store_to_memory( ctrl, counter_addr, incr, T_INT, adr_type );
}


//------------------------------uncommon_trap----------------------------------
// Bail out to the interpreter in mid-method.  Implemented by calling the
// uncommon_trap blob.  This helper function inserts a runtime call with the
// right debug info.
void GraphKit::uncommon_trap(int trap_request,
                             ciKlass* klass, const char* comment,
                             bool must_throw,
                             bool keep_exact_action) {
  if (failing())  stop();
  if (stopped())  return; // trap reachable?

  // Note:  If ProfileTraps is true, and if a deopt. actually
  // occurs here, the runtime will make sure an MDO exists.  There is
  // no need to call method()->ensure_method_data() at this point.

#ifdef ASSERT
  if (!must_throw) {
    // Make sure the stack has at least enough depth to execute
    // the current bytecode.
    int inputs, ignore;
    if (compute_stack_effects(inputs, ignore)) {
      assert(sp() >= inputs, "must have enough JVMS stack to execute");
      // It is a frequent error in library_call.cpp to issue an
      // uncommon trap with the _sp value already popped.
    }
  }
#endif

  Deoptimization::DeoptReason reason = Deoptimization::trap_request_reason(trap_request);
  Deoptimization::DeoptAction action = Deoptimization::trap_request_action(trap_request);

  switch (action) {
  case Deoptimization::Action_maybe_recompile:
  case Deoptimization::Action_reinterpret:
    // Temporary fix for 6529811 to allow virtual calls to be sure they
    // get the chance to go from mono->bi->mega
    if (!keep_exact_action &&
        Deoptimization::trap_request_index(trap_request) < 0 &&
        too_many_recompiles(reason)) {
      // This BCI is causing too many recompilations.
      action = Deoptimization::Action_none;
      trap_request = Deoptimization::make_trap_request(reason, action);
    } else {
      C->set_trap_can_recompile(true);
    }
    break;
  case Deoptimization::Action_make_not_entrant:
    C->set_trap_can_recompile(true);
    break;
#ifdef ASSERT
  case Deoptimization::Action_none:
  case Deoptimization::Action_make_not_compilable:
    break;
  default:
    assert(false, "bad action");
#endif
  }

  if (TraceOptoParse) {
    char buf[100];
    tty->print_cr("Uncommon trap %s at bci:%d",
                  Deoptimization::format_trap_request(buf, sizeof(buf),
                                                      trap_request), bci());
  }

  CompileLog* log = C->log();
  if (log != NULL) {
    int kid = (klass == NULL)? -1: log->identify(klass);
    log->begin_elem("uncommon_trap bci='%d'", bci());
    char buf[100];
    log->print(" %s", Deoptimization::format_trap_request(buf, sizeof(buf),
                                                          trap_request));
    if (kid >= 0)         log->print(" klass='%d'", kid);
    if (comment != NULL)  log->print(" comment='%s'", comment);
    log->end_elem();
  }

  // Make sure any guarding test views this path as very unlikely
  Node *i0 = control()->in(0);
  if (i0 != NULL && i0->is_If()) {        // Found a guarding if test?
    IfNode *iff = i0->as_If();
    float f = iff->_prob;   // Get prob
    if (control()->Opcode() == Op_IfTrue) {
      if (f > PROB_UNLIKELY_MAG(4))
        iff->_prob = PROB_MIN;
    } else {
      if (f < PROB_LIKELY_MAG(4))
        iff->_prob = PROB_MAX;
    }
  }

  // Clear out dead values from the debug info.
  kill_dead_locals();

  // Now insert the uncommon trap subroutine call
  address call_addr = SharedRuntime::uncommon_trap_blob()->entry_point();
  const TypePtr* no_memory_effects = NULL;
  // Pass the index of the class to be loaded
  Node* call = make_runtime_call(RC_NO_LEAF | RC_UNCOMMON |
                                 (must_throw ? RC_MUST_THROW : 0),
                                 OptoRuntime::uncommon_trap_Type(),
                                 call_addr, "uncommon_trap", no_memory_effects,
                                 intcon(trap_request));
  assert(call->as_CallStaticJava()->uncommon_trap_request() == trap_request,
         "must extract request correctly from the graph");
  assert(trap_request != 0, "zero value reserved by uncommon_trap_request");

  call->set_req(TypeFunc::ReturnAdr, returnadr());
  // The debug info is the only real input to this call.

  // Halt-and-catch fire here.  The above call should never return!
  HaltNode* halt = new(C, TypeFunc::Parms) HaltNode(control(), frameptr());
  _gvn.set_type_bottom(halt);
  root()->add_req(halt);

  stop_and_kill_map();
}


//--------------------------just_allocated_object------------------------------
// Report the object that was just allocated.
// It must be the case that there are no intervening safepoints.
// We use this to determine if an object is so "fresh" that
// it does not require card marks.
Node* GraphKit::just_allocated_object(Node* current_control) {
  if (C->recent_alloc_ctl() == current_control)
    return C->recent_alloc_obj();
  return NULL;
}


void GraphKit::round_double_arguments(ciMethod* dest_method) {
  // (Note:  TypeFunc::make has a cache that makes this fast.)
  const TypeFunc* tf    = TypeFunc::make(dest_method);
  int             nargs = tf->_domain->_cnt - TypeFunc::Parms;
  for (int j = 0; j < nargs; j++) {
    const Type *targ = tf->_domain->field_at(j + TypeFunc::Parms);
    if( targ->basic_type() == T_DOUBLE ) {
      // If any parameters are doubles, they must be rounded before
      // the call, dstore_rounding does gvn.transform
      Node *arg = argument(j);
      arg = dstore_rounding(arg);
      set_argument(j, arg);
    }
  }
}

void GraphKit::round_double_result(ciMethod* dest_method) {
  // A non-strict method may return a double value which has an extended
  // exponent, but this must not be visible in a caller which is 'strict'
  // If a strict caller invokes a non-strict callee, round a double result

  BasicType result_type = dest_method->return_type()->basic_type();
  assert( method() != NULL, "must have caller context");
  if( result_type == T_DOUBLE && method()->is_strict() && !dest_method->is_strict() ) {
    // Destination method's return value is on top of stack
    // dstore_rounding() does gvn.transform
    Node *result = pop_pair();
    result = dstore_rounding(result);
    push_pair(result);
  }
}

// rounding for strict float precision conformance
Node* GraphKit::precision_rounding(Node* n) {
  return UseStrictFP && _method->flags().is_strict()
    && UseSSE == 0 && Matcher::strict_fp_requires_explicit_rounding
    ? _gvn.transform( new (C, 2) RoundFloatNode(0, n) )
    : n;
}

// rounding for strict double precision conformance
Node* GraphKit::dprecision_rounding(Node *n) {
  return UseStrictFP && _method->flags().is_strict()
    && UseSSE <= 1 && Matcher::strict_fp_requires_explicit_rounding
    ? _gvn.transform( new (C, 2) RoundDoubleNode(0, n) )
    : n;
}

// rounding for non-strict double stores
Node* GraphKit::dstore_rounding(Node* n) {
  return Matcher::strict_fp_requires_explicit_rounding
    && UseSSE <= 1
    ? _gvn.transform( new (C, 2) RoundDoubleNode(0, n) )
    : n;
}

//=============================================================================
// Generate a fast path/slow path idiom.  Graph looks like:
// [foo] indicates that 'foo' is a parameter
//
//              [in]     NULL
//                 \    /
//                  CmpP
//                  Bool ne
//                   If
//                  /  \
//              True    False-<2>
//              / |
//             /  cast_not_null
//           Load  |    |   ^
//        [fast_test]   |   |
// gvn to   opt_test    |   |
//          /    \      |  <1>
//      True     False  |
//        |         \\  |
//   [slow_call]     \[fast_result]
//    Ctl   Val       \      \
//     |               \      \
//    Catch       <1>   \      \
//   /    \        ^     \      \
//  Ex    No_Ex    |      \      \
//  |       \   \  |       \ <2>  \
//  ...      \  [slow_res] |  |    \   [null_result]
//            \         \--+--+---  |  |
//             \           | /    \ | /
//              --------Region     Phi
//
//=============================================================================
// Code is structured as a series of driver functions all called 'do_XXX' that
// call a set of helper functions.  Helper functions first, then drivers.

//------------------------------null_check_oop---------------------------------
// Null check oop.  Set null-path control into Region in slot 3.
// Make a cast-not-nullness use the other not-null control.  Return cast.
Node* GraphKit::null_check_oop(Node* value, Node* *null_control,
                               bool never_see_null) {
  // Initial NULL check taken path
  (*null_control) = top();
  Node* cast = null_check_common(value, T_OBJECT, false, null_control);

  // Generate uncommon_trap:
  if (never_see_null && (*null_control) != top()) {
    // If we see an unexpected null at a check-cast we record it and force a
    // recompile; the offending check-cast will be compiled to handle NULLs.
    // If we see more than one offending BCI, then all checkcasts in the
    // method will be compiled to handle NULLs.
    PreserveJVMState pjvms(this);
    set_control(*null_control);
    replace_in_map(value, null());
    uncommon_trap(Deoptimization::Reason_null_check,
                  Deoptimization::Action_make_not_entrant);
    (*null_control) = top();    // NULL path is dead
  }

  // Cast away null-ness on the result
  return cast;
}

//------------------------------opt_iff----------------------------------------
// Optimize the fast-check IfNode.  Set the fast-path region slot 2.
// Return slow-path control.
Node* GraphKit::opt_iff(Node* region, Node* iff) {
  IfNode *opt_iff = _gvn.transform(iff)->as_If();

  // Fast path taken; set region slot 2
  Node *fast_taken = _gvn.transform( new (C, 1) IfFalseNode(opt_iff) );
  region->init_req(2,fast_taken); // Capture fast-control

  // Fast path not-taken, i.e. slow path
  Node *slow_taken = _gvn.transform( new (C, 1) IfTrueNode(opt_iff) );
  return slow_taken;
}

//-----------------------------make_runtime_call-------------------------------
Node* GraphKit::make_runtime_call(int flags,
                                  const TypeFunc* call_type, address call_addr,
                                  const char* call_name,
                                  const TypePtr* adr_type,
                                  // The following parms are all optional.
                                  // The first NULL ends the list.
                                  Node* parm0, Node* parm1,
                                  Node* parm2, Node* parm3,
                                  Node* parm4, Node* parm5,
                                  Node* parm6, Node* parm7) {
  // Slow-path call
  int size = call_type->domain()->cnt();
  bool is_leaf = !(flags & RC_NO_LEAF);
  bool has_io  = (!is_leaf && !(flags & RC_NO_IO));
  if (call_name == NULL) {
    assert(!is_leaf, "must supply name for leaf");
    call_name = OptoRuntime::stub_name(call_addr);
  }
  CallNode* call;
  if (!is_leaf) {
    call = new(C, size) CallStaticJavaNode(call_type, call_addr, call_name,
                                           bci(), adr_type);
  } else if (flags & RC_NO_FP) {
    call = new(C, size) CallLeafNoFPNode(call_type, call_addr, call_name, adr_type);
  } else {
    call = new(C, size) CallLeafNode(call_type, call_addr, call_name, adr_type);
  }

  // The following is similar to set_edges_for_java_call,
  // except that the memory effects of the call are restricted to AliasIdxRaw.

  // Slow path call has no side-effects, uses few values
  bool wide_in  = !(flags & RC_NARROW_MEM);
  bool wide_out = (C->get_alias_index(adr_type) == Compile::AliasIdxBot);

  Node* prev_mem = NULL;
  if (wide_in) {
    prev_mem = set_predefined_input_for_runtime_call(call);
  } else {
    assert(!wide_out, "narrow in => narrow out");
    Node* narrow_mem = memory(adr_type);
    prev_mem = reset_memory();
    map()->set_memory(narrow_mem);
    set_predefined_input_for_runtime_call(call);
  }

  // Hook each parm in order.  Stop looking at the first NULL.
  if (parm0 != NULL) { call->init_req(TypeFunc::Parms+0, parm0);
  if (parm1 != NULL) { call->init_req(TypeFunc::Parms+1, parm1);
  if (parm2 != NULL) { call->init_req(TypeFunc::Parms+2, parm2);
  if (parm3 != NULL) { call->init_req(TypeFunc::Parms+3, parm3);
  if (parm4 != NULL) { call->init_req(TypeFunc::Parms+4, parm4);
  if (parm5 != NULL) { call->init_req(TypeFunc::Parms+5, parm5);
  if (parm6 != NULL) { call->init_req(TypeFunc::Parms+6, parm6);
  if (parm7 != NULL) { call->init_req(TypeFunc::Parms+7, parm7);
    /* close each nested if ===> */  } } } } } } } }
  assert(call->in(call->req()-1) != NULL, "must initialize all parms");

  if (!is_leaf) {
    // Non-leaves can block and take safepoints:
    add_safepoint_edges(call, ((flags & RC_MUST_THROW) != 0));
  }
  // Non-leaves can throw exceptions:
  if (has_io) {
    call->set_req(TypeFunc::I_O, i_o());
  }

  if (flags & RC_UNCOMMON) {
    // Set the count to a tiny probability.  Cf. Estimate_Block_Frequency.
    // (An "if" probability corresponds roughly to an unconditional count.
    // Sort of.)
    call->set_cnt(PROB_UNLIKELY_MAG(4));
  }

  Node* c = _gvn.transform(call);
  assert(c == call, "cannot disappear");

  if (wide_out) {
    // Slow path call has full side-effects.
    set_predefined_output_for_runtime_call(call);
  } else {
    // Slow path call has few side-effects, and/or sets few values.
    set_predefined_output_for_runtime_call(call, prev_mem, adr_type);
  }

  if (has_io) {
    set_i_o(_gvn.transform(new (C, 1) ProjNode(call, TypeFunc::I_O)));
  }
  return call;

}

//------------------------------merge_memory-----------------------------------
// Merge memory from one path into the current memory state.
void GraphKit::merge_memory(Node* new_mem, Node* region, int new_path) {
  for (MergeMemStream mms(merged_memory(), new_mem->as_MergeMem()); mms.next_non_empty2(); ) {
    Node* old_slice = mms.force_memory();
    Node* new_slice = mms.memory2();
    if (old_slice != new_slice) {
      PhiNode* phi;
      if (new_slice->is_Phi() && new_slice->as_Phi()->region() == region) {
        phi = new_slice->as_Phi();
        #ifdef ASSERT
        if (old_slice->is_Phi() && old_slice->as_Phi()->region() == region)
          old_slice = old_slice->in(new_path);
        // Caller is responsible for ensuring that any pre-existing
        // phis are already aware of old memory.
        int old_path = (new_path > 1) ? 1 : 2;  // choose old_path != new_path
        assert(phi->in(old_path) == old_slice, "pre-existing phis OK");
        #endif
        mms.set_memory(phi);
      } else {
        phi = PhiNode::make(region, old_slice, Type::MEMORY, mms.adr_type(C));
        _gvn.set_type(phi, Type::MEMORY);
        phi->set_req(new_path, new_slice);
        mms.set_memory(_gvn.transform(phi));  // assume it is complete
      }
    }
  }
}

//------------------------------make_slow_call_ex------------------------------
// Make the exception handler hookups for the slow call
void GraphKit::make_slow_call_ex(Node* call, ciInstanceKlass* ex_klass, bool separate_io_proj) {
  if (stopped())  return;

  // Make a catch node with just two handlers:  fall-through and catch-all
  Node* i_o  = _gvn.transform( new (C, 1) ProjNode(call, TypeFunc::I_O, separate_io_proj) );
  Node* catc = _gvn.transform( new (C, 2) CatchNode(control(), i_o, 2) );
  Node* norm = _gvn.transform( new (C, 1) CatchProjNode(catc, CatchProjNode::fall_through_index, CatchProjNode::no_handler_bci) );
  Node* excp = _gvn.transform( new (C, 1) CatchProjNode(catc, CatchProjNode::catch_all_index,    CatchProjNode::no_handler_bci) );

  { PreserveJVMState pjvms(this);
    set_control(excp);
    set_i_o(i_o);

    if (excp != top()) {
      // Create an exception state also.
      // Use an exact type if the caller has specified a specific exception.
      const Type* ex_type = TypeOopPtr::make_from_klass_unique(ex_klass)->cast_to_ptr_type(TypePtr::NotNull);
      Node*       ex_oop  = new (C, 2) CreateExNode(ex_type, control(), i_o);
      add_exception_state(make_exception_state(_gvn.transform(ex_oop)));
    }
  }

  // Get the no-exception control from the CatchNode.
  set_control(norm);
}


//-------------------------------gen_subtype_check-----------------------------
// Generate a subtyping check.  Takes as input the subtype and supertype.
// Returns 2 values: sets the default control() to the true path and returns
// the false path.  Only reads invariant memory; sets no (visible) memory.
// The PartialSubtypeCheckNode sets the hidden 1-word cache in the encoding
// but that's not exposed to the optimizer.  This call also doesn't take in an
// Object; if you wish to check an Object you need to load the Object's class
// prior to coming here.
Node* GraphKit::gen_subtype_check(Node* subklass, Node* superklass) {
  // Fast check for identical types, perhaps identical constants.
  // The types can even be identical non-constants, in cases
  // involving Array.newInstance, Object.clone, etc.
  if (subklass == superklass)
    return top();             // false path is dead; no test needed.

  if (_gvn.type(superklass)->singleton()) {
    ciKlass* superk = _gvn.type(superklass)->is_klassptr()->klass();
    ciKlass* subk   = _gvn.type(subklass)->is_klassptr()->klass();

    // In the common case of an exact superklass, try to fold up the
    // test before generating code.  You may ask, why not just generate
    // the code and then let it fold up?  The answer is that the generated
    // code will necessarily include null checks, which do not always
    // completely fold away.  If they are also needless, then they turn
    // into a performance loss.  Example:
    //    Foo[] fa = blah(); Foo x = fa[0]; fa[1] = x;
    // Here, the type of 'fa' is often exact, so the store check
    // of fa[1]=x will fold up, without testing the nullness of x.
    switch (static_subtype_check(superk, subk)) {
    case SSC_always_false:
      {
        Node* always_fail = control();
        set_control(top());
        return always_fail;
      }
    case SSC_always_true:
      return top();
    case SSC_easy_test:
      {
        // Just do a direct pointer compare and be done.
        Node* cmp = _gvn.transform( new(C, 3) CmpPNode(subklass, superklass) );
        Node* bol = _gvn.transform( new(C, 2) BoolNode(cmp, BoolTest::eq) );
        IfNode* iff = create_and_xform_if(control(), bol, PROB_STATIC_FREQUENT, COUNT_UNKNOWN);
        set_control( _gvn.transform( new(C, 1) IfTrueNode (iff) ) );
        return       _gvn.transform( new(C, 1) IfFalseNode(iff) );
      }
    case SSC_full_test:
      break;
    default:
      ShouldNotReachHere();
    }
  }

  // %%% Possible further optimization:  Even if the superklass is not exact,
  // if the subklass is the unique subtype of the superklass, the check
  // will always succeed.  We could leave a dependency behind to ensure this.

  // First load the super-klass's check-offset
  Node *p1 = basic_plus_adr( superklass, superklass, sizeof(oopDesc) + Klass::super_check_offset_offset_in_bytes() );
  Node *chk_off = _gvn.transform( new (C, 3) LoadINode( NULL, memory(p1), p1, _gvn.type(p1)->is_ptr() ) );
  int cacheoff_con = sizeof(oopDesc) + Klass::secondary_super_cache_offset_in_bytes();
  bool might_be_cache = (find_int_con(chk_off, cacheoff_con) == cacheoff_con);

  // Load from the sub-klass's super-class display list, or a 1-word cache of
  // the secondary superclass list, or a failing value with a sentinel offset
  // if the super-klass is an interface or exceptionally deep in the Java
  // hierarchy and we have to scan the secondary superclass list the hard way.
  // Worst-case type is a little odd: NULL is allowed as a result (usually
  // klass loads can never produce a NULL).
  Node *chk_off_X = ConvI2X(chk_off);
  Node *p2 = _gvn.transform( new (C, 4) AddPNode(subklass,subklass,chk_off_X) );
  // For some types like interfaces the following loadKlass is from a 1-word
  // cache which is mutable so can't use immutable memory.  Other
  // types load from the super-class display table which is immutable.
  Node *kmem = might_be_cache ? memory(p2) : immutable_memory();
  Node *nkls = _gvn.transform( LoadKlassNode::make( _gvn, kmem, p2, _gvn.type(p2)->is_ptr(), TypeKlassPtr::OBJECT_OR_NULL ) );

  // Compile speed common case: ARE a subtype and we canNOT fail
  if( superklass == nkls )
    return top();             // false path is dead; no test needed.

  // See if we get an immediate positive hit.  Happens roughly 83% of the
  // time.  Test to see if the value loaded just previously from the subklass
  // is exactly the superklass.
  Node *cmp1 = _gvn.transform( new (C, 3) CmpPNode( superklass, nkls ) );
  Node *bol1 = _gvn.transform( new (C, 2) BoolNode( cmp1, BoolTest::eq ) );
  IfNode *iff1 = create_and_xform_if( control(), bol1, PROB_LIKELY(0.83f), COUNT_UNKNOWN );
  Node *iftrue1 = _gvn.transform( new (C, 1) IfTrueNode ( iff1 ) );
  set_control(    _gvn.transform( new (C, 1) IfFalseNode( iff1 ) ) );

  // Compile speed common case: Check for being deterministic right now.  If
  // chk_off is a constant and not equal to cacheoff then we are NOT a
  // subklass.  In this case we need exactly the 1 test above and we can
  // return those results immediately.
  if (!might_be_cache) {
    Node* not_subtype_ctrl = control();
    set_control(iftrue1); // We need exactly the 1 test above
    return not_subtype_ctrl;
  }

  // Gather the various success & failures here
  RegionNode *r_ok_subtype = new (C, 4) RegionNode(4);
  record_for_igvn(r_ok_subtype);
  RegionNode *r_not_subtype = new (C, 3) RegionNode(3);
  record_for_igvn(r_not_subtype);

  r_ok_subtype->init_req(1, iftrue1);

  // Check for immediate negative hit.  Happens roughly 11% of the time (which
  // is roughly 63% of the remaining cases).  Test to see if the loaded
  // check-offset points into the subklass display list or the 1-element
  // cache.  If it points to the display (and NOT the cache) and the display
  // missed then it's not a subtype.
  Node *cacheoff = _gvn.intcon(cacheoff_con);
  Node *cmp2 = _gvn.transform( new (C, 3) CmpINode( chk_off, cacheoff ) );
  Node *bol2 = _gvn.transform( new (C, 2) BoolNode( cmp2, BoolTest::ne ) );
  IfNode *iff2 = create_and_xform_if( control(), bol2, PROB_LIKELY(0.63f), COUNT_UNKNOWN );
  r_not_subtype->init_req(1, _gvn.transform( new (C, 1) IfTrueNode (iff2) ) );
  set_control(                _gvn.transform( new (C, 1) IfFalseNode(iff2) ) );

  // Check for self.  Very rare to get here, but it is taken 1/3 the time.
  // No performance impact (too rare) but allows sharing of secondary arrays
  // which has some footprint reduction.
  Node *cmp3 = _gvn.transform( new (C, 3) CmpPNode( subklass, superklass ) );
  Node *bol3 = _gvn.transform( new (C, 2) BoolNode( cmp3, BoolTest::eq ) );
  IfNode *iff3 = create_and_xform_if( control(), bol3, PROB_LIKELY(0.36f), COUNT_UNKNOWN );
  r_ok_subtype->init_req(2, _gvn.transform( new (C, 1) IfTrueNode ( iff3 ) ) );
  set_control(               _gvn.transform( new (C, 1) IfFalseNode( iff3 ) ) );

  // -- Roads not taken here: --
  // We could also have chosen to perform the self-check at the beginning
  // of this code sequence, as the assembler does.  This would not pay off
  // the same way, since the optimizer, unlike the assembler, can perform
  // static type analysis to fold away many successful self-checks.
  // Non-foldable self checks work better here in second position, because
  // the initial primary superclass check subsumes a self-check for most
  // types.  An exception would be a secondary type like array-of-interface,
  // which does not appear in its own primary supertype display.
  // Finally, we could have chosen to move the self-check into the
  // PartialSubtypeCheckNode, and from there out-of-line in a platform
  // dependent manner.  But it is worthwhile to have the check here,
  // where it can be perhaps be optimized.  The cost in code space is
  // small (register compare, branch).

  // Now do a linear scan of the secondary super-klass array.  Again, no real
  // performance impact (too rare) but it's gotta be done.
  // Since the code is rarely used, there is no penalty for moving it
  // out of line, and it can only improve I-cache density.
  // The decision to inline or out-of-line this final check is platform
  // dependent, and is found in the AD file definition of PartialSubtypeCheck.
  Node* psc = _gvn.transform(
    new (C, 3) PartialSubtypeCheckNode(control(), subklass, superklass) );

  Node *cmp4 = _gvn.transform( new (C, 3) CmpPNode( psc, null() ) );
  Node *bol4 = _gvn.transform( new (C, 2) BoolNode( cmp4, BoolTest::ne ) );
  IfNode *iff4 = create_and_xform_if( control(), bol4, PROB_FAIR, COUNT_UNKNOWN );
  r_not_subtype->init_req(2, _gvn.transform( new (C, 1) IfTrueNode (iff4) ) );
  r_ok_subtype ->init_req(3, _gvn.transform( new (C, 1) IfFalseNode(iff4) ) );

  // Return false path; set default control to true path.
  set_control( _gvn.transform(r_ok_subtype) );
  return _gvn.transform(r_not_subtype);
}

//----------------------------static_subtype_check-----------------------------
// Shortcut important common cases when superklass is exact:
// (0) superklass is java.lang.Object (can occur in reflective code)
// (1) subklass is already limited to a subtype of superklass => always ok
// (2) subklass does not overlap with superklass => always fail
// (3) superklass has NO subtypes and we can check with a simple compare.
int GraphKit::static_subtype_check(ciKlass* superk, ciKlass* subk) {
  if (StressReflectiveCode) {
    return SSC_full_test;       // Let caller generate the general case.
  }

  if (superk == env()->Object_klass()) {
    return SSC_always_true;     // (0) this test cannot fail
  }

  ciType* superelem = superk;
  if (superelem->is_array_klass())
    superelem = superelem->as_array_klass()->base_element_type();

  if (!subk->is_interface()) {  // cannot trust static interface types yet
    if (subk->is_subtype_of(superk)) {
      return SSC_always_true;   // (1) false path dead; no dynamic test needed
    }
    if (!(superelem->is_klass() && superelem->as_klass()->is_interface()) &&
        !superk->is_subtype_of(subk)) {
      return SSC_always_false;
    }
  }

  // If casting to an instance klass, it must have no subtypes
  if (superk->is_interface()) {
    // Cannot trust interfaces yet.
    // %%% S.B. superk->nof_implementors() == 1
  } else if (superelem->is_instance_klass()) {
    ciInstanceKlass* ik = superelem->as_instance_klass();
    if (!ik->has_subklass() && !ik->is_interface()) {
      if (!ik->is_final()) {
        // Add a dependency if there is a chance of a later subclass.
        C->dependencies()->assert_leaf_type(ik);
      }
      return SSC_easy_test;     // (3) caller can do a simple ptr comparison
    }
  } else {
    // A primitive array type has no subtypes.
    return SSC_easy_test;       // (3) caller can do a simple ptr comparison
  }

  return SSC_full_test;
}

// Profile-driven exact type check:
Node* GraphKit::type_check_receiver(Node* receiver, ciKlass* klass,
                                    float prob,
                                    Node* *casted_receiver) {
  const TypeKlassPtr* tklass = TypeKlassPtr::make(klass);
  Node* recv_klass = load_object_klass(receiver);
  Node* want_klass = makecon(tklass);
  Node* cmp = _gvn.transform( new(C, 3) CmpPNode(recv_klass, want_klass) );
  Node* bol = _gvn.transform( new(C, 2) BoolNode(cmp, BoolTest::eq) );
  IfNode* iff = create_and_xform_if(control(), bol, prob, COUNT_UNKNOWN);
  set_control( _gvn.transform( new(C, 1) IfTrueNode (iff) ));
  Node* fail = _gvn.transform( new(C, 1) IfFalseNode(iff) );

  const TypeOopPtr* recv_xtype = tklass->as_instance_type();
  assert(recv_xtype->klass_is_exact(), "");

  // Subsume downstream occurrences of receiver with a cast to
  // recv_xtype, since now we know what the type will be.
  Node* cast = new(C, 2) CheckCastPPNode(control(), receiver, recv_xtype);
  (*casted_receiver) = _gvn.transform(cast);
  // (User must make the replace_in_map call.)

  return fail;
}


//------------------------------seems_never_null-------------------------------
// Use null_seen information if it is available from the profile.
// If we see an unexpected null at a type check we record it and force a
// recompile; the offending check will be recompiled to handle NULLs.
// If we see several offending BCIs, then all checks in the
// method will be recompiled.
bool GraphKit::seems_never_null(Node* obj, ciProfileData* data) {
  if (UncommonNullCast               // Cutout for this technique
      && obj != null()               // And not the -Xcomp stupid case?
      && !too_many_traps(Deoptimization::Reason_null_check)
      ) {
    if (data == NULL)
      // Edge case:  no mature data.  Be optimistic here.
      return true;
    // If the profile has not seen a null, assume it won't happen.
    assert(java_bc() == Bytecodes::_checkcast ||
           java_bc() == Bytecodes::_instanceof ||
           java_bc() == Bytecodes::_aastore, "MDO must collect null_seen bit here");
    return !data->as_BitData()->null_seen();
  }
  return false;
}

//------------------------maybe_cast_profiled_receiver-------------------------
// If the profile has seen exactly one type, narrow to exactly that type.
// Subsequent type checks will always fold up.
Node* GraphKit::maybe_cast_profiled_receiver(Node* not_null_obj,
                                             ciProfileData* data,
                                             ciKlass* require_klass) {
  if (!UseTypeProfile || !TypeProfileCasts) return NULL;
  if (data == NULL)  return NULL;

  // Make sure we haven't already deoptimized from this tactic.
  if (too_many_traps(Deoptimization::Reason_class_check))
    return NULL;

  // (No, this isn't a call, but it's enough like a virtual call
  // to use the same ciMethod accessor to get the profile info...)
  ciCallProfile profile = method()->call_profile_at_bci(bci());
  if (profile.count() >= 0 &&         // no cast failures here
      profile.has_receiver(0) &&
      profile.morphism() == 1) {
    ciKlass* exact_kls = profile.receiver(0);
    if (require_klass == NULL ||
        static_subtype_check(require_klass, exact_kls) == SSC_always_true) {
      // If we narrow the type to match what the type profile sees,
      // we can then remove the rest of the cast.
      // This is a win, even if the exact_kls is very specific,
      // because downstream operations, such as method calls,
      // will often benefit from the sharper type.
      Node* exact_obj = not_null_obj; // will get updated in place...
      Node* slow_ctl  = type_check_receiver(exact_obj, exact_kls, 1.0,
                                            &exact_obj);
      { PreserveJVMState pjvms(this);
        set_control(slow_ctl);
        uncommon_trap(Deoptimization::Reason_class_check,
                      Deoptimization::Action_maybe_recompile);
      }
      replace_in_map(not_null_obj, exact_obj);
      return exact_obj;
    }
    // assert(ssc == SSC_always_true)... except maybe the profile lied to us.
  }

  return NULL;
}


//-------------------------------gen_instanceof--------------------------------
// Generate an instance-of idiom.  Used by both the instance-of bytecode
// and the reflective instance-of call.
Node* GraphKit::gen_instanceof(Node* obj, Node* superklass) {
  kill_dead_locals();           // Benefit all the uncommon traps
  assert( !stopped(), "dead parse path should be checked in callers" );
  assert(!TypePtr::NULL_PTR->higher_equal(_gvn.type(superklass)->is_klassptr()),
         "must check for not-null not-dead klass in callers");

  // Make the merge point
  enum { _obj_path = 1, _fail_path, _null_path, PATH_LIMIT };
  RegionNode* region = new(C, PATH_LIMIT) RegionNode(PATH_LIMIT);
  Node*       phi    = new(C, PATH_LIMIT) PhiNode(region, TypeInt::BOOL);
  C->set_has_split_ifs(true); // Has chance for split-if optimization

  ciProfileData* data = NULL;
  if (java_bc() == Bytecodes::_instanceof) {  // Only for the bytecode
    data = method()->method_data()->bci_to_data(bci());
  }
  bool never_see_null = (ProfileDynamicTypes  // aggressive use of profile
                         && seems_never_null(obj, data));

  // Null check; get casted pointer; set region slot 3
  Node* null_ctl = top();
  Node* not_null_obj = null_check_oop(obj, &null_ctl, never_see_null);

  // If not_null_obj is dead, only null-path is taken
  if (stopped()) {              // Doing instance-of on a NULL?
    set_control(null_ctl);
    return intcon(0);
  }
  region->init_req(_null_path, null_ctl);
  phi   ->init_req(_null_path, intcon(0)); // Set null path value
  if (null_ctl == top()) {
    // Do this eagerly, so that pattern matches like is_diamond_phi
    // will work even during parsing.
    assert(_null_path == PATH_LIMIT-1, "delete last");
    region->del_req(_null_path);
    phi   ->del_req(_null_path);
  }

  if (ProfileDynamicTypes && data != NULL) {
    Node* cast_obj = maybe_cast_profiled_receiver(not_null_obj, data, NULL);
    if (stopped()) {            // Profile disagrees with this path.
      set_control(null_ctl);    // Null is the only remaining possibility.
      return intcon(0);
    }
    if (cast_obj != NULL)
      not_null_obj = cast_obj;
  }

  // Load the object's klass
  Node* obj_klass = load_object_klass(not_null_obj);

  // Generate the subtype check
  Node* not_subtype_ctrl = gen_subtype_check(obj_klass, superklass);

  // Plug in the success path to the general merge in slot 1.
  region->init_req(_obj_path, control());
  phi   ->init_req(_obj_path, intcon(1));

  // Plug in the failing path to the general merge in slot 2.
  region->init_req(_fail_path, not_subtype_ctrl);
  phi   ->init_req(_fail_path, intcon(0));

  // Return final merged results
  set_control( _gvn.transform(region) );
  record_for_igvn(region);
  return _gvn.transform(phi);
}

//-------------------------------gen_checkcast---------------------------------
// Generate a checkcast idiom.  Used by both the checkcast bytecode and the
// array store bytecode.  Stack must be as-if BEFORE doing the bytecode so the
// uncommon-trap paths work.  Adjust stack after this call.
// If failure_control is supplied and not null, it is filled in with
// the control edge for the cast failure.  Otherwise, an appropriate
// uncommon trap or exception is thrown.
Node* GraphKit::gen_checkcast(Node *obj, Node* superklass,
                              Node* *failure_control) {
  kill_dead_locals();           // Benefit all the uncommon traps
  const TypeKlassPtr *tk = _gvn.type(superklass)->is_klassptr();
  const Type *toop = TypeOopPtr::make_from_klass(tk->klass());

  // Fast cutout:  Check the case that the cast is vacuously true.
  // This detects the common cases where the test will short-circuit
  // away completely.  We do this before we perform the null check,
  // because if the test is going to turn into zero code, we don't
  // want a residual null check left around.  (Causes a slowdown,
  // for example, in some objArray manipulations, such as a[i]=a[j].)
  if (tk->singleton()) {
    const TypeOopPtr* objtp = _gvn.type(obj)->isa_oopptr();
    if (objtp != NULL && objtp->klass() != NULL) {
      switch (static_subtype_check(tk->klass(), objtp->klass())) {
      case SSC_always_true:
        return obj;
      case SSC_always_false:
        // It needs a null check because a null will *pass* the cast check.
        // A non-null value will always produce an exception.
        return do_null_assert(obj, T_OBJECT);
      }
    }
  }

  ciProfileData* data = NULL;
  if (failure_control == NULL) {        // use MDO in regular case only
    assert(java_bc() == Bytecodes::_aastore ||
           java_bc() == Bytecodes::_checkcast,
           "interpreter profiles type checks only for these BCs");
    data = method()->method_data()->bci_to_data(bci());
  }

  // Make the merge point
  enum { _obj_path = 1, _null_path, PATH_LIMIT };
  RegionNode* region = new (C, PATH_LIMIT) RegionNode(PATH_LIMIT);
  Node*       phi    = new (C, PATH_LIMIT) PhiNode(region, toop);
  C->set_has_split_ifs(true); // Has chance for split-if optimization

  // Use null-cast information if it is available
  bool never_see_null = ((failure_control == NULL)  // regular case only
                         && seems_never_null(obj, data));

  // Null check; get casted pointer; set region slot 3
  Node* null_ctl = top();
  Node* not_null_obj = null_check_oop(obj, &null_ctl, never_see_null);

  // If not_null_obj is dead, only null-path is taken
  if (stopped()) {              // Doing instance-of on a NULL?
    set_control(null_ctl);
    return null();
  }
  region->init_req(_null_path, null_ctl);
  phi   ->init_req(_null_path, null());  // Set null path value
  if (null_ctl == top()) {
    // Do this eagerly, so that pattern matches like is_diamond_phi
    // will work even during parsing.
    assert(_null_path == PATH_LIMIT-1, "delete last");
    region->del_req(_null_path);
    phi   ->del_req(_null_path);
  }

  Node* cast_obj = NULL;
  if (data != NULL &&
      // Counter has never been decremented (due to cast failure).
      // ...This is a reasonable thing to expect.  It is true of
      // all casts inserted by javac to implement generic types.
      data->as_CounterData()->count() >= 0) {
    cast_obj = maybe_cast_profiled_receiver(not_null_obj, data, tk->klass());
    if (cast_obj != NULL) {
      if (failure_control != NULL) // failure is now impossible
        (*failure_control) = top();
      // adjust the type of the phi to the exact klass:
      phi->raise_bottom_type(_gvn.type(cast_obj)->meet(TypePtr::NULL_PTR));
    }
  }

  if (cast_obj == NULL) {
    // Load the object's klass
    Node* obj_klass = load_object_klass(not_null_obj);

    // Generate the subtype check
    Node* not_subtype_ctrl = gen_subtype_check( obj_klass, superklass );

    // Plug in success path into the merge
    cast_obj = _gvn.transform(new (C, 2) CheckCastPPNode(control(),
                                                         not_null_obj, toop));
    // Failure path ends in uncommon trap (or may be dead - failure impossible)
    if (failure_control == NULL) {
      if (not_subtype_ctrl != top()) { // If failure is possible
        PreserveJVMState pjvms(this);
        set_control(not_subtype_ctrl);
        builtin_throw(Deoptimization::Reason_class_check, obj_klass);
      }
    } else {
      (*failure_control) = not_subtype_ctrl;
    }
  }

  region->init_req(_obj_path, control());
  phi   ->init_req(_obj_path, cast_obj);

  // A merge of NULL or Casted-NotNull obj
  Node* res = _gvn.transform(phi);

  // Note I do NOT always 'replace_in_map(obj,result)' here.
  //  if( tk->klass()->can_be_primary_super()  )
    // This means that if I successfully store an Object into an array-of-String
    // I 'forget' that the Object is really now known to be a String.  I have to
    // do this because we don't have true union types for interfaces - if I store
    // a Baz into an array-of-Interface and then tell the optimizer it's an
    // Interface, I forget that it's also a Baz and cannot do Baz-like field
    // references to it.  FIX THIS WHEN UNION TYPES APPEAR!
  //  replace_in_map( obj, res );

  // Return final merged results
  set_control( _gvn.transform(region) );
  record_for_igvn(region);
  return res;
}

//------------------------------next_monitor-----------------------------------
// What number should be given to the next monitor?
int GraphKit::next_monitor() {
  int current = jvms()->monitor_depth()* C->sync_stack_slots();
  int next = current + C->sync_stack_slots();
  // Keep the toplevel high water mark current:
  if (C->fixed_slots() < next)  C->set_fixed_slots(next);
  return current;
}

//------------------------------insert_mem_bar---------------------------------
// Memory barrier to avoid floating things around
// The membar serves as a pinch point between both control and all memory slices.
Node* GraphKit::insert_mem_bar(int opcode, Node* precedent) {
  MemBarNode* mb = MemBarNode::make(C, opcode, Compile::AliasIdxBot, precedent);
  mb->init_req(TypeFunc::Control, control());
  mb->init_req(TypeFunc::Memory,  reset_memory());
  Node* membar = _gvn.transform(mb);
  set_control(_gvn.transform(new (C, 1) ProjNode(membar,TypeFunc::Control) ));
  set_all_memory_call(membar);
  return membar;
}

//-------------------------insert_mem_bar_volatile----------------------------
// Memory barrier to avoid floating things around
// The membar serves as a pinch point between both control and memory(alias_idx).
// If you want to make a pinch point on all memory slices, do not use this
// function (even with AliasIdxBot); use insert_mem_bar() instead.
Node* GraphKit::insert_mem_bar_volatile(int opcode, int alias_idx, Node* precedent) {
  // When Parse::do_put_xxx updates a volatile field, it appends a series
  // of MemBarVolatile nodes, one for *each* volatile field alias category.
  // The first membar is on the same memory slice as the field store opcode.
  // This forces the membar to follow the store.  (Bug 6500685 broke this.)
  // All the other membars (for other volatile slices, including AliasIdxBot,
  // which stands for all unknown volatile slices) are control-dependent
  // on the first membar.  This prevents later volatile loads or stores
  // from sliding up past the just-emitted store.

  MemBarNode* mb = MemBarNode::make(C, opcode, alias_idx, precedent);
  mb->set_req(TypeFunc::Control,control());
  if (alias_idx == Compile::AliasIdxBot) {
    mb->set_req(TypeFunc::Memory, merged_memory()->base_memory());
  } else {
    assert(!(opcode == Op_Initialize && alias_idx != Compile::AliasIdxRaw), "fix caller");
    mb->set_req(TypeFunc::Memory, memory(alias_idx));
  }
  Node* membar = _gvn.transform(mb);
  set_control(_gvn.transform(new (C, 1) ProjNode(membar, TypeFunc::Control)));
  if (alias_idx == Compile::AliasIdxBot) {
    merged_memory()->set_base_memory(_gvn.transform(new (C, 1) ProjNode(membar, TypeFunc::Memory)));
  } else {
    set_memory(_gvn.transform(new (C, 1) ProjNode(membar, TypeFunc::Memory)),alias_idx);
  }
  return membar;
}

//------------------------------shared_lock------------------------------------
// Emit locking code.
FastLockNode* GraphKit::shared_lock(Node* obj) {
  // bci is either a monitorenter bc or InvocationEntryBci
  // %%% SynchronizationEntryBCI is redundant; use InvocationEntryBci in interfaces
  assert(SynchronizationEntryBCI == InvocationEntryBci, "");

  if( !GenerateSynchronizationCode )
    return NULL;                // Not locking things?
  if (stopped())                // Dead monitor?
    return NULL;

  assert(dead_locals_are_killed(), "should kill locals before sync. point");

  // Box the stack location
  Node* box = _gvn.transform(new (C, 1) BoxLockNode(next_monitor()));
  Node* mem = reset_memory();

  FastLockNode * flock = _gvn.transform(new (C, 3) FastLockNode(0, obj, box) )->as_FastLock();
  if (PrintPreciseBiasedLockingStatistics) {
    // Create the counters for this fast lock.
    flock->create_lock_counter(sync_jvms()); // sync_jvms used to get current bci
  }
  // Add monitor to debug info for the slow path.  If we block inside the
  // slow path and de-opt, we need the monitor hanging around
  map()->push_monitor( flock );

  const TypeFunc *tf = LockNode::lock_type();
  LockNode *lock = new (C, tf->domain()->cnt()) LockNode(C, tf);

  lock->init_req( TypeFunc::Control, control() );
  lock->init_req( TypeFunc::Memory , mem );
  lock->init_req( TypeFunc::I_O    , top() )     ;   // does no i/o
  lock->init_req( TypeFunc::FramePtr, frameptr() );
  lock->init_req( TypeFunc::ReturnAdr, top() );

  lock->init_req(TypeFunc::Parms + 0, obj);
  lock->init_req(TypeFunc::Parms + 1, box);
  lock->init_req(TypeFunc::Parms + 2, flock);
  add_safepoint_edges(lock);

  lock = _gvn.transform( lock )->as_Lock();

  // lock has no side-effects, sets few values
  set_predefined_output_for_runtime_call(lock, mem, TypeRawPtr::BOTTOM);

  insert_mem_bar(Op_MemBarAcquire);

  // Add this to the worklist so that the lock can be eliminated
  record_for_igvn(lock);

#ifndef PRODUCT
  if (PrintLockStatistics) {
    // Update the counter for this lock.  Don't bother using an atomic
    // operation since we don't require absolute accuracy.
    lock->create_lock_counter(map()->jvms());
    increment_counter(lock->counter()->addr());
  }
#endif

  return flock;
}


//------------------------------shared_unlock----------------------------------
// Emit unlocking code.
void GraphKit::shared_unlock(Node* box, Node* obj) {
  // bci is either a monitorenter bc or InvocationEntryBci
  // %%% SynchronizationEntryBCI is redundant; use InvocationEntryBci in interfaces
  assert(SynchronizationEntryBCI == InvocationEntryBci, "");

  if( !GenerateSynchronizationCode )
    return;
  if (stopped()) {               // Dead monitor?
    map()->pop_monitor();        // Kill monitor from debug info
    return;
  }

  // Memory barrier to avoid floating things down past the locked region
  insert_mem_bar(Op_MemBarRelease);

  const TypeFunc *tf = OptoRuntime::complete_monitor_exit_Type();
  UnlockNode *unlock = new (C, tf->domain()->cnt()) UnlockNode(C, tf);
  uint raw_idx = Compile::AliasIdxRaw;
  unlock->init_req( TypeFunc::Control, control() );
  unlock->init_req( TypeFunc::Memory , memory(raw_idx) );
  unlock->init_req( TypeFunc::I_O    , top() )     ;   // does no i/o
  unlock->init_req( TypeFunc::FramePtr, frameptr() );
  unlock->init_req( TypeFunc::ReturnAdr, top() );

  unlock->init_req(TypeFunc::Parms + 0, obj);
  unlock->init_req(TypeFunc::Parms + 1, box);
  unlock = _gvn.transform(unlock)->as_Unlock();

  Node* mem = reset_memory();

  // unlock has no side-effects, sets few values
  set_predefined_output_for_runtime_call(unlock, mem, TypeRawPtr::BOTTOM);

  // Kill monitor from debug info
  map()->pop_monitor( );
}

//-------------------------------get_layout_helper-----------------------------
// If the given klass is a constant or known to be an array,
// fetch the constant layout helper value into constant_value
// and return (Node*)NULL.  Otherwise, load the non-constant
// layout helper value, and return the node which represents it.
// This two-faced routine is useful because allocation sites
// almost always feature constant types.
Node* GraphKit::get_layout_helper(Node* klass_node, jint& constant_value) {
  const TypeKlassPtr* inst_klass = _gvn.type(klass_node)->isa_klassptr();
  if (!StressReflectiveCode && inst_klass != NULL) {
    ciKlass* klass = inst_klass->klass();
    bool    xklass = inst_klass->klass_is_exact();
    if (xklass || klass->is_array_klass()) {
      jint lhelper = klass->layout_helper();
      if (lhelper != Klass::_lh_neutral_value) {
        constant_value = lhelper;
        return (Node*) NULL;
      }
    }
  }
  constant_value = Klass::_lh_neutral_value;  // put in a known value
  Node* lhp = basic_plus_adr(klass_node, klass_node, Klass::layout_helper_offset_in_bytes() + sizeof(oopDesc));
  return make_load(NULL, lhp, TypeInt::INT, T_INT);
}

// We just put in an allocate/initialize with a big raw-memory effect.
// Hook selected additional alias categories on the initialization.
static void hook_memory_on_init(GraphKit& kit, int alias_idx,
                                MergeMemNode* init_in_merge,
                                Node* init_out_raw) {
  DEBUG_ONLY(Node* init_in_raw = init_in_merge->base_memory());
  assert(init_in_merge->memory_at(alias_idx) == init_in_raw, "");

  Node* prevmem = kit.memory(alias_idx);
  init_in_merge->set_memory_at(alias_idx, prevmem);
  kit.set_memory(init_out_raw, alias_idx);
}

//---------------------------set_output_for_allocation-------------------------
Node* GraphKit::set_output_for_allocation(AllocateNode* alloc,
                                          const TypeOopPtr* oop_type,
                                          bool raw_mem_only) {
  int rawidx = Compile::AliasIdxRaw;
  alloc->set_req( TypeFunc::FramePtr, frameptr() );
  add_safepoint_edges(alloc);
  Node* allocx = _gvn.transform(alloc);
  set_control( _gvn.transform(new (C, 1) ProjNode(allocx, TypeFunc::Control) ) );
  // create memory projection for i_o
  set_memory ( _gvn.transform( new (C, 1) ProjNode(allocx, TypeFunc::Memory, true) ), rawidx );
  make_slow_call_ex(allocx, env()->OutOfMemoryError_klass(), true);

  // create a memory projection as for the normal control path
  Node* malloc = _gvn.transform(new (C, 1) ProjNode(allocx, TypeFunc::Memory));
  set_memory(malloc, rawidx);

  // a normal slow-call doesn't change i_o, but an allocation does
  // we create a separate i_o projection for the normal control path
  set_i_o(_gvn.transform( new (C, 1) ProjNode(allocx, TypeFunc::I_O, false) ) );
  Node* rawoop = _gvn.transform( new (C, 1) ProjNode(allocx, TypeFunc::Parms) );

  // put in an initialization barrier
  InitializeNode* init = insert_mem_bar_volatile(Op_Initialize, rawidx,
                                                 rawoop)->as_Initialize();
  assert(alloc->initialization() == init,  "2-way macro link must work");
  assert(init ->allocation()     == alloc, "2-way macro link must work");
  if (ReduceFieldZeroing && !raw_mem_only) {
    // Extract memory strands which may participate in the new object's
    // initialization, and source them from the new InitializeNode.
    // This will allow us to observe initializations when they occur,
    // and link them properly (as a group) to the InitializeNode.
    assert(init->in(InitializeNode::Memory) == malloc, "");
    MergeMemNode* minit_in = MergeMemNode::make(C, malloc);
    init->set_req(InitializeNode::Memory, minit_in);
    record_for_igvn(minit_in); // fold it up later, if possible
    Node* minit_out = memory(rawidx);
    assert(minit_out->is_Proj() && minit_out->in(0) == init, "");
    if (oop_type->isa_aryptr()) {
      const TypePtr* telemref = oop_type->add_offset(Type::OffsetBot);
      int            elemidx  = C->get_alias_index(telemref);
      hook_memory_on_init(*this, elemidx, minit_in, minit_out);
    } else if (oop_type->isa_instptr()) {
      ciInstanceKlass* ik = oop_type->klass()->as_instance_klass();
      for (int i = 0, len = ik->nof_nonstatic_fields(); i < len; i++) {
        ciField* field = ik->nonstatic_field_at(i);
        if (field->offset() >= TrackedInitializationLimit * HeapWordSize)
          continue;  // do not bother to track really large numbers of fields
        // Find (or create) the alias category for this field:
        int fieldidx = C->alias_type(field)->index();
        hook_memory_on_init(*this, fieldidx, minit_in, minit_out);
      }
    }
  }

  // Cast raw oop to the real thing...
  Node* javaoop = new (C, 2) CheckCastPPNode(control(), rawoop, oop_type);
  javaoop = _gvn.transform(javaoop);
  C->set_recent_alloc(control(), javaoop);
  assert(just_allocated_object(control()) == javaoop, "just allocated");

#ifdef ASSERT
  { // Verify that the AllocateNode::Ideal_allocation recognizers work:
    assert(AllocateNode::Ideal_allocation(rawoop, &_gvn) == alloc,
           "Ideal_allocation works");
    assert(AllocateNode::Ideal_allocation(javaoop, &_gvn) == alloc,
           "Ideal_allocation works");
    if (alloc->is_AllocateArray()) {
      assert(AllocateArrayNode::Ideal_array_allocation(rawoop, &_gvn) == alloc->as_AllocateArray(),
             "Ideal_allocation works");
      assert(AllocateArrayNode::Ideal_array_allocation(javaoop, &_gvn) == alloc->as_AllocateArray(),
             "Ideal_allocation works");
    } else {
      assert(alloc->in(AllocateNode::ALength)->is_top(), "no length, please");
    }
  }
#endif //ASSERT

  return javaoop;
}

//---------------------------new_instance--------------------------------------
// This routine takes a klass_node which may be constant (for a static type)
// or may be non-constant (for reflective code).  It will work equally well
// for either, and the graph will fold nicely if the optimizer later reduces
// the type to a constant.
// The optional arguments are for specialized use by intrinsics:
//  - If 'extra_slow_test' if not null is an extra condition for the slow-path.
//  - If 'raw_mem_only', do not cast the result to an oop.
//  - If 'return_size_val', report the the total object size to the caller.
Node* GraphKit::new_instance(Node* klass_node,
                             Node* extra_slow_test,
                             bool raw_mem_only, // affect only raw memory
                             Node* *return_size_val) {
  // Compute size in doublewords
  // The size is always an integral number of doublewords, represented
  // as a positive bytewise size stored in the klass's layout_helper.
  // The layout_helper also encodes (in a low bit) the need for a slow path.
  jint  layout_con = Klass::_lh_neutral_value;
  Node* layout_val = get_layout_helper(klass_node, layout_con);
  int   layout_is_con = (layout_val == NULL);

  if (extra_slow_test == NULL)  extra_slow_test = intcon(0);
  // Generate the initial go-slow test.  It's either ALWAYS (return a
  // Node for 1) or NEVER (return a NULL) or perhaps (in the reflective
  // case) a computed value derived from the layout_helper.
  Node* initial_slow_test = NULL;
  if (layout_is_con) {
    assert(!StressReflectiveCode, "stress mode does not use these paths");
    bool must_go_slow = Klass::layout_helper_needs_slow_path(layout_con);
    initial_slow_test = must_go_slow? intcon(1): extra_slow_test;

  } else {   // reflective case
    // This reflective path is used by Unsafe.allocateInstance.
    // (It may be stress-tested by specifying StressReflectiveCode.)
    // Basically, we want to get into the VM is there's an illegal argument.
    Node* bit = intcon(Klass::_lh_instance_slow_path_bit);
    initial_slow_test = _gvn.transform( new (C, 3) AndINode(layout_val, bit) );
    if (extra_slow_test != intcon(0)) {
      initial_slow_test = _gvn.transform( new (C, 3) OrINode(initial_slow_test, extra_slow_test) );
    }
    // (Macro-expander will further convert this to a Bool, if necessary.)
  }

  // Find the size in bytes.  This is easy; it's the layout_helper.
  // The size value must be valid even if the slow path is taken.
  Node* size = NULL;
  if (layout_is_con) {
    size = MakeConX(Klass::layout_helper_size_in_bytes(layout_con));
  } else {   // reflective case
    // This reflective path is used by clone and Unsafe.allocateInstance.
    size = ConvI2X(layout_val);

    // Clear the low bits to extract layout_helper_size_in_bytes:
    assert((int)Klass::_lh_instance_slow_path_bit < BytesPerLong, "clear bit");
    Node* mask = MakeConX(~ (intptr_t)right_n_bits(LogBytesPerLong));
    size = _gvn.transform( new (C, 3) AndXNode(size, mask) );
  }
  if (return_size_val != NULL) {
    (*return_size_val) = size;
  }

  // This is a precise notnull oop of the klass.
  // (Actually, it need not be precise if this is a reflective allocation.)
  // It's what we cast the result to.
  const TypeKlassPtr* tklass = _gvn.type(klass_node)->isa_klassptr();
  if (!tklass)  tklass = TypeKlassPtr::OBJECT;
  const TypeOopPtr* oop_type = tklass->as_instance_type();

  // Now generate allocation code

  // The entire memory state is needed for slow path of the allocation
  // since GC and deoptimization can happened.
  Node *mem = reset_memory();
  set_all_memory(mem); // Create new memory state

  AllocateNode* alloc
    = new (C, AllocateNode::ParmLimit)
        AllocateNode(C, AllocateNode::alloc_type(),
                     control(), mem, i_o(),
                     size, klass_node,
                     initial_slow_test);

  return set_output_for_allocation(alloc, oop_type, raw_mem_only);
}

//-------------------------------new_array-------------------------------------
// helper for both newarray and anewarray
// The 'length' parameter is (obviously) the length of the array.
// See comments on new_instance for the meaning of the other arguments.
Node* GraphKit::new_array(Node* klass_node,     // array klass (maybe variable)
                          Node* length,         // number of array elements
                          int   nargs,          // number of arguments to push back for uncommon trap
                          bool raw_mem_only,    // affect only raw memory
                          Node* *return_size_val) {
  jint  layout_con = Klass::_lh_neutral_value;
  Node* layout_val = get_layout_helper(klass_node, layout_con);
  int   layout_is_con = (layout_val == NULL);

  if (!layout_is_con && !StressReflectiveCode &&
      !too_many_traps(Deoptimization::Reason_class_check)) {
    // This is a reflective array creation site.
    // Optimistically assume that it is a subtype of Object[],
    // so that we can fold up all the address arithmetic.
    layout_con = Klass::array_layout_helper(T_OBJECT);
    Node* cmp_lh = _gvn.transform( new(C, 3) CmpINode(layout_val, intcon(layout_con)) );
    Node* bol_lh = _gvn.transform( new(C, 2) BoolNode(cmp_lh, BoolTest::eq) );
    { BuildCutout unless(this, bol_lh, PROB_MAX);
      _sp += nargs;
      uncommon_trap(Deoptimization::Reason_class_check,
                    Deoptimization::Action_maybe_recompile);
    }
    layout_val = NULL;
    layout_is_con = true;
  }

  // Generate the initial go-slow test.  Make sure we do not overflow
  // if length is huge (near 2Gig) or negative!  We do not need
  // exact double-words here, just a close approximation of needed
  // double-words.  We can't add any offset or rounding bits, lest we
  // take a size -1 of bytes and make it positive.  Use an unsigned
  // compare, so negative sizes look hugely positive.
  int fast_size_limit = FastAllocateSizeLimit;
  if (layout_is_con) {
    assert(!StressReflectiveCode, "stress mode does not use these paths");
    // Increase the size limit if we have exact knowledge of array type.
    int log2_esize = Klass::layout_helper_log2_element_size(layout_con);
    fast_size_limit <<= (LogBytesPerLong - log2_esize);
  }

  Node* initial_slow_cmp  = _gvn.transform( new (C, 3) CmpUNode( length, intcon( fast_size_limit ) ) );
  Node* initial_slow_test = _gvn.transform( new (C, 2) BoolNode( initial_slow_cmp, BoolTest::gt ) );
  if (initial_slow_test->is_Bool()) {
    // Hide it behind a CMoveI, or else PhaseIdealLoop::split_up will get sick.
    initial_slow_test = initial_slow_test->as_Bool()->as_int_value(&_gvn);
  }

  // --- Size Computation ---
  // array_size = round_to_heap(array_header + (length << elem_shift));
  // where round_to_heap(x) == round_to(x, MinObjAlignmentInBytes)
  // and round_to(x, y) == ((x + y-1) & ~(y-1))
  // The rounding mask is strength-reduced, if possible.
  int round_mask = MinObjAlignmentInBytes - 1;
  Node* header_size = NULL;
  int   header_size_min  = arrayOopDesc::base_offset_in_bytes(T_BYTE);
  // (T_BYTE has the weakest alignment and size restrictions...)
  if (layout_is_con) {
    int       hsize  = Klass::layout_helper_header_size(layout_con);
    int       eshift = Klass::layout_helper_log2_element_size(layout_con);
    BasicType etype  = Klass::layout_helper_element_type(layout_con);
    if ((round_mask & ~right_n_bits(eshift)) == 0)
      round_mask = 0;  // strength-reduce it if it goes away completely
    assert((hsize & right_n_bits(eshift)) == 0, "hsize is pre-rounded");
    assert(header_size_min <= hsize, "generic minimum is smallest");
    header_size_min = hsize;
    header_size = intcon(hsize + round_mask);
  } else {
    Node* hss   = intcon(Klass::_lh_header_size_shift);
    Node* hsm   = intcon(Klass::_lh_header_size_mask);
    Node* hsize = _gvn.transform( new(C, 3) URShiftINode(layout_val, hss) );
    hsize       = _gvn.transform( new(C, 3) AndINode(hsize, hsm) );
    Node* mask  = intcon(round_mask);
    header_size = _gvn.transform( new(C, 3) AddINode(hsize, mask) );
  }

  Node* elem_shift = NULL;
  if (layout_is_con) {
    int eshift = Klass::layout_helper_log2_element_size(layout_con);
    if (eshift != 0)
      elem_shift = intcon(eshift);
  } else {
    // There is no need to mask or shift this value.
    // The semantics of LShiftINode include an implicit mask to 0x1F.
    assert(Klass::_lh_log2_element_size_shift == 0, "use shift in place");
    elem_shift = layout_val;
  }

  // Transition to native address size for all offset calculations:
  Node* lengthx = ConvI2X(length);
  Node* headerx = ConvI2X(header_size);
#ifdef _LP64
  { const TypeLong* tllen = _gvn.find_long_type(lengthx);
    if (tllen != NULL && tllen->_lo < 0) {
      // Add a manual constraint to a positive range.  Cf. array_element_address.
      jlong size_max = arrayOopDesc::max_array_length(T_BYTE);
      if (size_max > tllen->_hi)  size_max = tllen->_hi;
      const TypeLong* tlcon = TypeLong::make(CONST64(0), size_max, Type::WidenMin);
      lengthx = _gvn.transform( new (C, 2) ConvI2LNode(length, tlcon));
    }
  }
#endif

  // Combine header size (plus rounding) and body size.  Then round down.
  // This computation cannot overflow, because it is used only in two
  // places, one where the length is sharply limited, and the other
  // after a successful allocation.
  Node* abody = lengthx;
  if (elem_shift != NULL)
    abody     = _gvn.transform( new(C, 3) LShiftXNode(lengthx, elem_shift) );
  Node* size  = _gvn.transform( new(C, 3) AddXNode(headerx, abody) );
  if (round_mask != 0) {
    Node* mask = MakeConX(~round_mask);
    size       = _gvn.transform( new(C, 3) AndXNode(size, mask) );
  }
  // else if round_mask == 0, the size computation is self-rounding

  if (return_size_val != NULL) {
    // This is the size
    (*return_size_val) = size;
  }

  // Now generate allocation code

  // The entire memory state is needed for slow path of the allocation
  // since GC and deoptimization can happened.
  Node *mem = reset_memory();
  set_all_memory(mem); // Create new memory state

  // Create the AllocateArrayNode and its result projections
  AllocateArrayNode* alloc
    = new (C, AllocateArrayNode::ParmLimit)
        AllocateArrayNode(C, AllocateArrayNode::alloc_type(),
                          control(), mem, i_o(),
                          size, klass_node,
                          initial_slow_test,
                          length);

  // Cast to correct type.  Note that the klass_node may be constant or not,
  // and in the latter case the actual array type will be inexact also.
  // (This happens via a non-constant argument to inline_native_newArray.)
  // In any case, the value of klass_node provides the desired array type.
  const TypeInt* length_type = _gvn.find_int_type(length);
  const TypeOopPtr* ary_type = _gvn.type(klass_node)->is_klassptr()->as_instance_type();
  if (ary_type->isa_aryptr() && length_type != NULL) {
    // Try to get a better type than POS for the size
    ary_type = ary_type->is_aryptr()->cast_to_size(length_type);
  }

  Node* javaoop = set_output_for_allocation(alloc, ary_type, raw_mem_only);

  // Cast length on remaining path to be as narrow as possible
  if (map()->find_edge(length) >= 0) {
    Node* ccast = alloc->make_ideal_length(ary_type, &_gvn);
    if (ccast != length) {
      _gvn.set_type_bottom(ccast);
      record_for_igvn(ccast);
      replace_in_map(length, ccast);
    }
  }

  return javaoop;
}

// The following "Ideal_foo" functions are placed here because they recognize
// the graph shapes created by the functions immediately above.

//---------------------------Ideal_allocation----------------------------------
// Given an oop pointer or raw pointer, see if it feeds from an AllocateNode.
AllocateNode* AllocateNode::Ideal_allocation(Node* ptr, PhaseTransform* phase) {
  if (ptr == NULL) {     // reduce dumb test in callers
    return NULL;
  }
  if (ptr->is_CheckCastPP()) {  // strip a raw-to-oop cast
    ptr = ptr->in(1);
    if (ptr == NULL)  return NULL;
  }
  if (ptr->is_Proj()) {
    Node* allo = ptr->in(0);
    if (allo != NULL && allo->is_Allocate()) {
      return allo->as_Allocate();
    }
  }
  // Report failure to match.
  return NULL;
}

// Fancy version which also strips off an offset (and reports it to caller).
AllocateNode* AllocateNode::Ideal_allocation(Node* ptr, PhaseTransform* phase,
                                             intptr_t& offset) {
  Node* base = AddPNode::Ideal_base_and_offset(ptr, phase, offset);
  if (base == NULL)  return NULL;
  return Ideal_allocation(base, phase);
}

// Trace Initialize <- Proj[Parm] <- Allocate
AllocateNode* InitializeNode::allocation() {
  Node* rawoop = in(InitializeNode::RawAddress);
  if (rawoop->is_Proj()) {
    Node* alloc = rawoop->in(0);
    if (alloc->is_Allocate()) {
      return alloc->as_Allocate();
    }
  }
  return NULL;
}

// Trace Allocate -> Proj[Parm] -> Initialize
InitializeNode* AllocateNode::initialization() {
  ProjNode* rawoop = proj_out(AllocateNode::RawAddress);
  if (rawoop == NULL)  return NULL;
  for (DUIterator_Fast imax, i = rawoop->fast_outs(imax); i < imax; i++) {
    Node* init = rawoop->fast_out(i);
    if (init->is_Initialize()) {
      assert(init->as_Initialize()->allocation() == this, "2-way link");
      return init->as_Initialize();
    }
  }
  return NULL;
}

//----------------------------- store barriers ----------------------------
#define __ ideal.

void GraphKit::sync_kit(IdealKit& ideal) {
  // Final sync IdealKit and graphKit.
  __ drain_delay_transform();
  set_all_memory(__ merged_memory());
  set_control(__ ctrl());
}

// vanilla/CMS post barrier
// Insert a write-barrier store.  This is to let generational GC work; we have
// to flag all oop-stores before the next GC point.
void GraphKit::write_barrier_post(Node* oop_store,
                                  Node* obj,
                                  Node* adr,
                                  uint  adr_idx,
                                  Node* val,
                                  bool use_precise) {
  // No store check needed if we're storing a NULL or an old object
  // (latter case is probably a string constant). The concurrent
  // mark sweep garbage collector, however, needs to have all nonNull
  // oop updates flagged via card-marks.
  if (val != NULL && val->is_Con()) {
    // must be either an oop or NULL
    const Type* t = val->bottom_type();
    if (t == TypePtr::NULL_PTR || t == Type::TOP)
      // stores of null never (?) need barriers
      return;
    ciObject* con = t->is_oopptr()->const_oop();
    if (con != NULL
        && con->is_perm()
        && Universe::heap()->can_elide_permanent_oop_store_barriers())
      // no store barrier needed, because no old-to-new ref created
      return;
  }

  if (use_ReduceInitialCardMarks()
      && obj == just_allocated_object(control())) {
    // We can skip marks on a freshly-allocated object in Eden.
    // Keep this code in sync with new_store_pre_barrier() in runtime.cpp.
    // That routine informs GC to take appropriate compensating steps,
    // upon a slow-path allocation, so as to make this card-mark
    // elision safe.
    return;
  }

  if (!use_precise) {
    // All card marks for a (non-array) instance are in one place:
    adr = obj;
  }
  // (Else it's an array (or unknown), and we want more precise card marks.)
  assert(adr != NULL, "");

  IdealKit ideal(gvn(), control(), merged_memory(), true);

  // Convert the pointer to an int prior to doing math on it
  Node* cast = __ CastPX(__ ctrl(), adr);

  // Divide by card size
  assert(Universe::heap()->barrier_set()->kind() == BarrierSet::CardTableModRef,
         "Only one we handle so far.");
  Node* card_offset = __ URShiftX( cast, __ ConI(CardTableModRefBS::card_shift) );

  // Combine card table base and card offset
  Node* card_adr = __ AddP(__ top(), byte_map_base_node(), card_offset );

  // Get the alias_index for raw card-mark memory
  int adr_type = Compile::AliasIdxRaw;
  // Smash zero into card
  Node*   zero = __ ConI(0);
  BasicType bt = T_BYTE;
  if( !UseConcMarkSweepGC ) {
    __ store(__ ctrl(), card_adr, zero, bt, adr_type);
  } else {
    // Specialized path for CM store barrier
    __ storeCM(__ ctrl(), card_adr, zero, oop_store, adr_idx, bt, adr_type);
  }

  // Final sync IdealKit and GraphKit.
  sync_kit(ideal);
}

// G1 pre/post barriers
void GraphKit::g1_write_barrier_pre(Node* obj,
                                    Node* adr,
                                    uint alias_idx,
                                    Node* val,
                                    const TypeOopPtr* val_type,
                                    BasicType bt) {
  IdealKit ideal(gvn(), control(), merged_memory(), true);

  Node* tls = __ thread(); // ThreadLocalStorage

  Node* no_ctrl = NULL;
  Node* no_base = __ top();
  Node* zero = __ ConI(0);

  float likely  = PROB_LIKELY(0.999);
  float unlikely  = PROB_UNLIKELY(0.999);

  BasicType active_type = in_bytes(PtrQueue::byte_width_of_active()) == 4 ? T_INT : T_BYTE;
  assert(in_bytes(PtrQueue::byte_width_of_active()) == 4 || in_bytes(PtrQueue::byte_width_of_active()) == 1, "flag width");

  // Offsets into the thread
  const int marking_offset = in_bytes(JavaThread::satb_mark_queue_offset() +  // 648
                                          PtrQueue::byte_offset_of_active());
  const int index_offset   = in_bytes(JavaThread::satb_mark_queue_offset() +  // 656
                                          PtrQueue::byte_offset_of_index());
  const int buffer_offset  = in_bytes(JavaThread::satb_mark_queue_offset() +  // 652
                                          PtrQueue::byte_offset_of_buf());
  // Now the actual pointers into the thread

  // set_control( ctl);

  Node* marking_adr = __ AddP(no_base, tls, __ ConX(marking_offset));
  Node* buffer_adr  = __ AddP(no_base, tls, __ ConX(buffer_offset));
  Node* index_adr   = __ AddP(no_base, tls, __ ConX(index_offset));

  // Now some of the values

  Node* marking = __ load(__ ctrl(), marking_adr, TypeInt::INT, active_type, Compile::AliasIdxRaw);

  // if (!marking)
  __ if_then(marking, BoolTest::ne, zero); {
    Node* index   = __ load(__ ctrl(), index_adr, TypeInt::INT, T_INT, Compile::AliasIdxRaw);

    const Type* t1 = adr->bottom_type();
    const Type* t2 = val->bottom_type();

    Node* orig = __ load(no_ctrl, adr, val_type, bt, alias_idx);
    // if (orig != NULL)
    __ if_then(orig, BoolTest::ne, null()); {
      Node* buffer  = __ load(__ ctrl(), buffer_adr, TypeRawPtr::NOTNULL, T_ADDRESS, Compile::AliasIdxRaw);

      // load original value
      // alias_idx correct??

      // is the queue for this thread full?
      __ if_then(index, BoolTest::ne, zero, likely); {

        // decrement the index
        Node* next_index = __ SubI(index,  __ ConI(sizeof(intptr_t)));
        Node* next_indexX = next_index;
#ifdef _LP64
        // We could refine the type for what it's worth
        // const TypeLong* lidxtype = TypeLong::make(CONST64(0), get_size_from_queue);
        next_indexX = _gvn.transform( new (C, 2) ConvI2LNode(next_index, TypeLong::make(0, max_jlong, Type::WidenMax)) );
#endif

        // Now get the buffer location we will log the original value into and store it
        Node *log_addr = __ AddP(no_base, buffer, next_indexX);
        __ store(__ ctrl(), log_addr, orig, T_OBJECT, Compile::AliasIdxRaw);

        // update the index
        __ store(__ ctrl(), index_adr, next_index, T_INT, Compile::AliasIdxRaw);

      } __ else_(); {

        // logging buffer is full, call the runtime
        const TypeFunc *tf = OptoRuntime::g1_wb_pre_Type();
        __ make_leaf_call(tf, CAST_FROM_FN_PTR(address, SharedRuntime::g1_wb_pre), "g1_wb_pre", orig, tls);
      } __ end_if();  // (!index)
    } __ end_if();  // (orig != NULL)
  } __ end_if();  // (!marking)

  // Final sync IdealKit and GraphKit.
  sync_kit(ideal);
}

//
// Update the card table and add card address to the queue
//
void GraphKit::g1_mark_card(IdealKit& ideal,
                            Node* card_adr,
                            Node* oop_store,
                            uint oop_alias_idx,
                            Node* index,
                            Node* index_adr,
                            Node* buffer,
                            const TypeFunc* tf) {

  Node* zero = __ ConI(0);
  Node* no_base = __ top();
  BasicType card_bt = T_BYTE;
  // Smash zero into card. MUST BE ORDERED WRT TO STORE
  __ storeCM(__ ctrl(), card_adr, zero, oop_store, oop_alias_idx, card_bt, Compile::AliasIdxRaw);

  //  Now do the queue work
  __ if_then(index, BoolTest::ne, zero); {

    Node* next_index = __ SubI(index, __ ConI(sizeof(intptr_t)));
    Node* next_indexX = next_index;
#ifdef _LP64
    // We could refine the type for what it's worth
    // const TypeLong* lidxtype = TypeLong::make(CONST64(0), get_size_from_queue);
    next_indexX = _gvn.transform( new (C, 2) ConvI2LNode(next_index, TypeLong::make(0, max_jlong, Type::WidenMax)) );
#endif // _LP64
    Node* log_addr = __ AddP(no_base, buffer, next_indexX);

    __ store(__ ctrl(), log_addr, card_adr, T_ADDRESS, Compile::AliasIdxRaw);
    __ store(__ ctrl(), index_adr, next_index, T_INT, Compile::AliasIdxRaw);

  } __ else_(); {
    __ make_leaf_call(tf, CAST_FROM_FN_PTR(address, SharedRuntime::g1_wb_post), "g1_wb_post", card_adr, __ thread());
  } __ end_if();

}

void GraphKit::g1_write_barrier_post(Node* oop_store,
                                     Node* obj,
                                     Node* adr,
                                     uint alias_idx,
                                     Node* val,
                                     BasicType bt,
                                     bool use_precise) {
  // If we are writing a NULL then we need no post barrier

  if (val != NULL && val->is_Con() && val->bottom_type() == TypePtr::NULL_PTR) {
    // Must be NULL
    const Type* t = val->bottom_type();
    assert(t == Type::TOP || t == TypePtr::NULL_PTR, "must be NULL");
    // No post barrier if writing NULLx
    return;
  }

  if (!use_precise) {
    // All card marks for a (non-array) instance are in one place:
    adr = obj;
  }
  // (Else it's an array (or unknown), and we want more precise card marks.)
  assert(adr != NULL, "");

  IdealKit ideal(gvn(), control(), merged_memory(), true);

  Node* tls = __ thread(); // ThreadLocalStorage

  Node* no_base = __ top();
  float likely  = PROB_LIKELY(0.999);
  float unlikely  = PROB_UNLIKELY(0.999);
  Node* zero = __ ConI(0);
  Node* zeroX = __ ConX(0);

  // Get the alias_index for raw card-mark memory
  const TypePtr* card_type = TypeRawPtr::BOTTOM;

  const TypeFunc *tf = OptoRuntime::g1_wb_post_Type();

  // Offsets into the thread
  const int index_offset  = in_bytes(JavaThread::dirty_card_queue_offset() +
                                     PtrQueue::byte_offset_of_index());
  const int buffer_offset = in_bytes(JavaThread::dirty_card_queue_offset() +
                                     PtrQueue::byte_offset_of_buf());

  // Pointers into the thread

  Node* buffer_adr = __ AddP(no_base, tls, __ ConX(buffer_offset));
  Node* index_adr =  __ AddP(no_base, tls, __ ConX(index_offset));

  // Now some values
  // Use ctrl to avoid hoisting these values past a safepoint, which could
  // potentially reset these fields in the JavaThread.
  Node* index  = __ load(__ ctrl(), index_adr, TypeInt::INT, T_INT, Compile::AliasIdxRaw);
  Node* buffer = __ load(__ ctrl(), buffer_adr, TypeRawPtr::NOTNULL, T_ADDRESS, Compile::AliasIdxRaw);

  // Convert the store obj pointer to an int prior to doing math on it
  // Must use ctrl to prevent "integerized oop" existing across safepoint
  Node* cast =  __ CastPX(__ ctrl(), adr);

  // Divide pointer by card size
  Node* card_offset = __ URShiftX( cast, __ ConI(CardTableModRefBS::card_shift) );

  // Combine card table base and card offset
  Node* card_adr = __ AddP(no_base, byte_map_base_node(), card_offset );

  // If we know the value being stored does it cross regions?

  if (val != NULL) {
    // Does the store cause us to cross regions?

    // Should be able to do an unsigned compare of region_size instead of
    // and extra shift. Do we have an unsigned compare??
    // Node* region_size = __ ConI(1 << HeapRegion::LogOfHRGrainBytes);
    Node* xor_res =  __ URShiftX ( __ XorX( cast,  __ CastPX(__ ctrl(), val)), __ ConI(HeapRegion::LogOfHRGrainBytes));

    // if (xor_res == 0) same region so skip
    __ if_then(xor_res, BoolTest::ne, zeroX); {

      // No barrier if we are storing a NULL
      __ if_then(val, BoolTest::ne, null(), unlikely); {

        // Ok must mark the card if not already dirty

        // load the original value of the card
        Node* card_val = __ load(__ ctrl(), card_adr, TypeInt::INT, T_BYTE, Compile::AliasIdxRaw);

        __ if_then(card_val, BoolTest::ne, zero); {
          g1_mark_card(ideal, card_adr, oop_store, alias_idx, index, index_adr, buffer, tf);
        } __ end_if();
      } __ end_if();
    } __ end_if();
  } else {
    // Object.clone() instrinsic uses this path.
    g1_mark_card(ideal, card_adr, oop_store, alias_idx, index, index_adr, buffer, tf);
  }

  // Final sync IdealKit and GraphKit.
  sync_kit(ideal);
}
#undef __
