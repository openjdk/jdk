/*
 * Copyright 2000-2010 Sun Microsystems, Inc.  All Rights Reserved.
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

#include "incls/_precompiled.incl"
#include "incls/_callGenerator.cpp.incl"

CallGenerator::CallGenerator(ciMethod* method) {
  _method = method;
}

// Utility function.
const TypeFunc* CallGenerator::tf() const {
  return TypeFunc::make(method());
}

//-----------------------------ParseGenerator---------------------------------
// Internal class which handles all direct bytecode traversal.
class ParseGenerator : public InlineCallGenerator {
private:
  bool  _is_osr;
  float _expected_uses;

public:
  ParseGenerator(ciMethod* method, float expected_uses, bool is_osr = false)
    : InlineCallGenerator(method)
  {
    _is_osr        = is_osr;
    _expected_uses = expected_uses;
    assert(can_parse(method, is_osr), "parse must be possible");
  }

  // Can we build either an OSR or a regular parser for this method?
  static bool can_parse(ciMethod* method, int is_osr = false);

  virtual bool      is_parse() const           { return true; }
  virtual JVMState* generate(JVMState* jvms);
  int is_osr() { return _is_osr; }

};

JVMState* ParseGenerator::generate(JVMState* jvms) {
  Compile* C = Compile::current();

  if (is_osr()) {
    // The JVMS for a OSR has a single argument (see its TypeFunc).
    assert(jvms->depth() == 1, "no inline OSR");
  }

  if (C->failing()) {
    return NULL;  // bailing out of the compile; do not try to parse
  }

  Parse parser(jvms, method(), _expected_uses);
  // Grab signature for matching/allocation
#ifdef ASSERT
  if (parser.tf() != (parser.depth() == 1 ? C->tf() : tf())) {
    MutexLockerEx ml(Compile_lock, Mutex::_no_safepoint_check_flag);
    assert(C->env()->system_dictionary_modification_counter_changed(),
           "Must invalidate if TypeFuncs differ");
  }
#endif

  GraphKit& exits = parser.exits();

  if (C->failing()) {
    while (exits.pop_exception_state() != NULL) ;
    return NULL;
  }

  assert(exits.jvms()->same_calls_as(jvms), "sanity");

  // Simply return the exit state of the parser,
  // augmented by any exceptional states.
  return exits.transfer_exceptions_into_jvms();
}

//---------------------------DirectCallGenerator------------------------------
// Internal class which handles all out-of-line calls w/o receiver type checks.
class DirectCallGenerator : public CallGenerator {
 private:
  CallStaticJavaNode* _call_node;
  // Force separate memory and I/O projections for the exceptional
  // paths to facilitate late inlinig.
  bool                _separate_io_proj;

 public:
  DirectCallGenerator(ciMethod* method, bool separate_io_proj)
    : CallGenerator(method),
      _separate_io_proj(separate_io_proj)
  {
  }
  virtual JVMState* generate(JVMState* jvms);

  CallStaticJavaNode* call_node() const { return _call_node; }
};

JVMState* DirectCallGenerator::generate(JVMState* jvms) {
  GraphKit kit(jvms);
  bool is_static = method()->is_static();
  address target = is_static ? SharedRuntime::get_resolve_static_call_stub()
                             : SharedRuntime::get_resolve_opt_virtual_call_stub();

  if (kit.C->log() != NULL) {
    kit.C->log()->elem("direct_call bci='%d'", jvms->bci());
  }

  CallStaticJavaNode *call = new (kit.C, tf()->domain()->cnt()) CallStaticJavaNode(tf(), target, method(), kit.bci());
  if (!is_static) {
    // Make an explicit receiver null_check as part of this call.
    // Since we share a map with the caller, his JVMS gets adjusted.
    kit.null_check_receiver(method());
    if (kit.stopped()) {
      // And dump it back to the caller, decorated with any exceptions:
      return kit.transfer_exceptions_into_jvms();
    }
    // Mark the call node as virtual, sort of:
    call->set_optimized_virtual(true);
    if (method()->is_method_handle_invoke()) {
      call->set_method_handle_invoke(true);
      kit.C->set_has_method_handle_invokes(true);
    }
  }
  kit.set_arguments_for_java_call(call);
  kit.set_edges_for_java_call(call, false, _separate_io_proj);
  Node* ret = kit.set_results_for_java_call(call, _separate_io_proj);
  kit.push_node(method()->return_type()->basic_type(), ret);
  _call_node = call;  // Save the call node in case we need it later
  return kit.transfer_exceptions_into_jvms();
}

//---------------------------DynamicCallGenerator-----------------------------
// Internal class which handles all out-of-line invokedynamic calls.
class DynamicCallGenerator : public CallGenerator {
public:
  DynamicCallGenerator(ciMethod* method)
    : CallGenerator(method)
  {
  }
  virtual JVMState* generate(JVMState* jvms);
};

JVMState* DynamicCallGenerator::generate(JVMState* jvms) {
  GraphKit kit(jvms);

  if (kit.C->log() != NULL) {
    kit.C->log()->elem("dynamic_call bci='%d'", jvms->bci());
  }

  // Get the constant pool cache from the caller class.
  ciMethod* caller_method = jvms->method();
  ciBytecodeStream str(caller_method);
  str.force_bci(jvms->bci());  // Set the stream to the invokedynamic bci.
  assert(str.cur_bc() == Bytecodes::_invokedynamic, "wrong place to issue a dynamic call!");
  ciCPCache* cpcache = str.get_cpcache();

  // Get the offset of the CallSite from the constant pool cache
  // pointer.
  int index = str.get_method_index();
  size_t call_site_offset = cpcache->get_f1_offset(index);

  // Load the CallSite object from the constant pool cache.
  const TypeOopPtr* cpcache_ptr = TypeOopPtr::make_from_constant(cpcache);
  Node* cpcache_adr = kit.makecon(cpcache_ptr);
  Node* call_site_adr = kit.basic_plus_adr(cpcache_adr, cpcache_adr, call_site_offset);
  Node* call_site = kit.make_load(kit.control(), call_site_adr, TypeInstPtr::BOTTOM, T_OBJECT, Compile::AliasIdxRaw);

  // Load the target MethodHandle from the CallSite object.
  Node* target_mh_adr = kit.basic_plus_adr(call_site, call_site, java_dyn_CallSite::target_offset_in_bytes());
  Node* target_mh = kit.make_load(kit.control(), target_mh_adr, TypeInstPtr::BOTTOM, T_OBJECT);

  address resolve_stub = SharedRuntime::get_resolve_opt_virtual_call_stub();

  CallStaticJavaNode *call = new (kit.C, tf()->domain()->cnt()) CallStaticJavaNode(tf(), resolve_stub, method(), kit.bci());
  // invokedynamic is treated as an optimized invokevirtual.
  call->set_optimized_virtual(true);
  // Take extra care (in the presence of argument motion) not to trash the SP:
  call->set_method_handle_invoke(true);
  kit.C->set_has_method_handle_invokes(true);

  // Pass the target MethodHandle as first argument and shift the
  // other arguments.
  call->init_req(0 + TypeFunc::Parms, target_mh);
  uint nargs = call->method()->arg_size();
  for (uint i = 1; i < nargs; i++) {
    Node* arg = kit.argument(i - 1);
    call->init_req(i + TypeFunc::Parms, arg);
  }

  kit.set_edges_for_java_call(call);
  Node* ret = kit.set_results_for_java_call(call);
  kit.push_node(method()->return_type()->basic_type(), ret);
  return kit.transfer_exceptions_into_jvms();
}

//--------------------------VirtualCallGenerator------------------------------
// Internal class which handles all out-of-line calls checking receiver type.
class VirtualCallGenerator : public CallGenerator {
private:
  int _vtable_index;
public:
  VirtualCallGenerator(ciMethod* method, int vtable_index)
    : CallGenerator(method), _vtable_index(vtable_index)
  {
    assert(vtable_index == methodOopDesc::invalid_vtable_index ||
           vtable_index >= 0, "either invalid or usable");
  }
  virtual bool      is_virtual() const          { return true; }
  virtual JVMState* generate(JVMState* jvms);
};

JVMState* VirtualCallGenerator::generate(JVMState* jvms) {
  GraphKit kit(jvms);
  Node* receiver = kit.argument(0);

  if (kit.C->log() != NULL) {
    kit.C->log()->elem("virtual_call bci='%d'", jvms->bci());
  }

  // If the receiver is a constant null, do not torture the system
  // by attempting to call through it.  The compile will proceed
  // correctly, but may bail out in final_graph_reshaping, because
  // the call instruction will have a seemingly deficient out-count.
  // (The bailout says something misleading about an "infinite loop".)
  if (kit.gvn().type(receiver)->higher_equal(TypePtr::NULL_PTR)) {
    kit.inc_sp(method()->arg_size());  // restore arguments
    kit.uncommon_trap(Deoptimization::Reason_null_check,
                      Deoptimization::Action_none,
                      NULL, "null receiver");
    return kit.transfer_exceptions_into_jvms();
  }

  // Ideally we would unconditionally do a null check here and let it
  // be converted to an implicit check based on profile information.
  // However currently the conversion to implicit null checks in
  // Block::implicit_null_check() only looks for loads and stores, not calls.
  ciMethod *caller = kit.method();
  ciMethodData *caller_md = (caller == NULL) ? NULL : caller->method_data();
  if (!UseInlineCaches || !ImplicitNullChecks ||
       ((ImplicitNullCheckThreshold > 0) && caller_md &&
       (caller_md->trap_count(Deoptimization::Reason_null_check)
       >= (uint)ImplicitNullCheckThreshold))) {
    // Make an explicit receiver null_check as part of this call.
    // Since we share a map with the caller, his JVMS gets adjusted.
    receiver = kit.null_check_receiver(method());
    if (kit.stopped()) {
      // And dump it back to the caller, decorated with any exceptions:
      return kit.transfer_exceptions_into_jvms();
    }
  }

  assert(!method()->is_static(), "virtual call must not be to static");
  assert(!method()->is_final(), "virtual call should not be to final");
  assert(!method()->is_private(), "virtual call should not be to private");
  assert(_vtable_index == methodOopDesc::invalid_vtable_index || !UseInlineCaches,
         "no vtable calls if +UseInlineCaches ");
  address target = SharedRuntime::get_resolve_virtual_call_stub();
  // Normal inline cache used for call
  CallDynamicJavaNode *call = new (kit.C, tf()->domain()->cnt()) CallDynamicJavaNode(tf(), target, method(), _vtable_index, kit.bci());
  kit.set_arguments_for_java_call(call);
  kit.set_edges_for_java_call(call);
  Node* ret = kit.set_results_for_java_call(call);
  kit.push_node(method()->return_type()->basic_type(), ret);

  // Represent the effect of an implicit receiver null_check
  // as part of this call.  Since we share a map with the caller,
  // his JVMS gets adjusted.
  kit.cast_not_null(receiver);
  return kit.transfer_exceptions_into_jvms();
}

bool ParseGenerator::can_parse(ciMethod* m, int entry_bci) {
  // Certain methods cannot be parsed at all:
  if (!m->can_be_compiled())              return false;
  if (!m->has_balanced_monitors())        return false;
  if (m->get_flow_analysis()->failing())  return false;

  // (Methods may bail out for other reasons, after the parser is run.
  // We try to avoid this, but if forced, we must return (Node*)NULL.
  // The user of the CallGenerator must check for this condition.)
  return true;
}

CallGenerator* CallGenerator::for_inline(ciMethod* m, float expected_uses) {
  if (!ParseGenerator::can_parse(m))  return NULL;
  return new ParseGenerator(m, expected_uses);
}

// As a special case, the JVMS passed to this CallGenerator is
// for the method execution already in progress, not just the JVMS
// of the caller.  Thus, this CallGenerator cannot be mixed with others!
CallGenerator* CallGenerator::for_osr(ciMethod* m, int osr_bci) {
  if (!ParseGenerator::can_parse(m, true))  return NULL;
  float past_uses = m->interpreter_invocation_count();
  float expected_uses = past_uses;
  return new ParseGenerator(m, expected_uses, true);
}

CallGenerator* CallGenerator::for_direct_call(ciMethod* m, bool separate_io_proj) {
  assert(!m->is_abstract(), "for_direct_call mismatch");
  return new DirectCallGenerator(m, separate_io_proj);
}

CallGenerator* CallGenerator::for_dynamic_call(ciMethod* m) {
  assert(m->is_method_handle_invoke(), "for_dynamic_call mismatch");
  return new DynamicCallGenerator(m);
}

CallGenerator* CallGenerator::for_virtual_call(ciMethod* m, int vtable_index) {
  assert(!m->is_static(), "for_virtual_call mismatch");
  assert(!m->is_method_handle_invoke(), "should be a direct call");
  return new VirtualCallGenerator(m, vtable_index);
}

// Allow inlining decisions to be delayed
class LateInlineCallGenerator : public DirectCallGenerator {
  CallGenerator* _inline_cg;

 public:
  LateInlineCallGenerator(ciMethod* method, CallGenerator* inline_cg) :
    DirectCallGenerator(method, true), _inline_cg(inline_cg) {}

  virtual bool      is_late_inline() const { return true; }

  // Convert the CallStaticJava into an inline
  virtual void do_late_inline();

  JVMState* generate(JVMState* jvms) {
    // Record that this call site should be revisited once the main
    // parse is finished.
    Compile::current()->add_late_inline(this);

    // Emit the CallStaticJava and request separate projections so
    // that the late inlining logic can distinguish between fall
    // through and exceptional uses of the memory and io projections
    // as is done for allocations and macro expansion.
    return DirectCallGenerator::generate(jvms);
  }

};


void LateInlineCallGenerator::do_late_inline() {
  // Can't inline it
  if (call_node() == NULL || call_node()->outcnt() == 0 ||
      call_node()->in(0) == NULL || call_node()->in(0)->is_top())
    return;

  CallStaticJavaNode* call = call_node();

  // Make a clone of the JVMState that appropriate to use for driving a parse
  Compile* C = Compile::current();
  JVMState* jvms     = call->jvms()->clone_shallow(C);
  uint size = call->req();
  SafePointNode* map = new (C, size) SafePointNode(size, jvms);
  for (uint i1 = 0; i1 < size; i1++) {
    map->init_req(i1, call->in(i1));
  }

  // Make sure the state is a MergeMem for parsing.
  if (!map->in(TypeFunc::Memory)->is_MergeMem()) {
    map->set_req(TypeFunc::Memory, MergeMemNode::make(C, map->in(TypeFunc::Memory)));
  }

  // Make enough space for the expression stack and transfer the incoming arguments
  int nargs    = method()->arg_size();
  jvms->set_map(map);
  map->ensure_stack(jvms, jvms->method()->max_stack());
  if (nargs > 0) {
    for (int i1 = 0; i1 < nargs; i1++) {
      map->set_req(i1 + jvms->argoff(), call->in(TypeFunc::Parms + i1));
    }
  }

  CompileLog* log = C->log();
  if (log != NULL) {
    log->head("late_inline method='%d'", log->identify(method()));
    JVMState* p = jvms;
    while (p != NULL) {
      log->elem("jvms bci='%d' method='%d'", p->bci(), log->identify(p->method()));
      p = p->caller();
    }
    log->tail("late_inline");
  }

  // Setup default node notes to be picked up by the inlining
  Node_Notes* old_nn = C->default_node_notes();
  if (old_nn != NULL) {
    Node_Notes* entry_nn = old_nn->clone(C);
    entry_nn->set_jvms(jvms);
    C->set_default_node_notes(entry_nn);
  }

  // Now perform the inling using the synthesized JVMState
  JVMState* new_jvms = _inline_cg->generate(jvms);
  if (new_jvms == NULL)  return;  // no change
  if (C->failing())      return;

  // Capture any exceptional control flow
  GraphKit kit(new_jvms);

  // Find the result object
  Node* result = C->top();
  int   result_size = method()->return_type()->size();
  if (result_size != 0 && !kit.stopped()) {
    result = (result_size == 1) ? kit.pop() : kit.pop_pair();
  }

  kit.replace_call(call, result);
}


CallGenerator* CallGenerator::for_late_inline(ciMethod* method, CallGenerator* inline_cg) {
  return new LateInlineCallGenerator(method, inline_cg);
}


//---------------------------WarmCallGenerator--------------------------------
// Internal class which handles initial deferral of inlining decisions.
class WarmCallGenerator : public CallGenerator {
  WarmCallInfo*   _call_info;
  CallGenerator*  _if_cold;
  CallGenerator*  _if_hot;
  bool            _is_virtual;   // caches virtuality of if_cold
  bool            _is_inline;    // caches inline-ness of if_hot

public:
  WarmCallGenerator(WarmCallInfo* ci,
                    CallGenerator* if_cold,
                    CallGenerator* if_hot)
    : CallGenerator(if_cold->method())
  {
    assert(method() == if_hot->method(), "consistent choices");
    _call_info  = ci;
    _if_cold    = if_cold;
    _if_hot     = if_hot;
    _is_virtual = if_cold->is_virtual();
    _is_inline  = if_hot->is_inline();
  }

  virtual bool      is_inline() const           { return _is_inline; }
  virtual bool      is_virtual() const          { return _is_virtual; }
  virtual bool      is_deferred() const         { return true; }

  virtual JVMState* generate(JVMState* jvms);
};


CallGenerator* CallGenerator::for_warm_call(WarmCallInfo* ci,
                                            CallGenerator* if_cold,
                                            CallGenerator* if_hot) {
  return new WarmCallGenerator(ci, if_cold, if_hot);
}

JVMState* WarmCallGenerator::generate(JVMState* jvms) {
  Compile* C = Compile::current();
  if (C->log() != NULL) {
    C->log()->elem("warm_call bci='%d'", jvms->bci());
  }
  jvms = _if_cold->generate(jvms);
  if (jvms != NULL) {
    Node* m = jvms->map()->control();
    if (m->is_CatchProj()) m = m->in(0);  else m = C->top();
    if (m->is_Catch())     m = m->in(0);  else m = C->top();
    if (m->is_Proj())      m = m->in(0);  else m = C->top();
    if (m->is_CallJava()) {
      _call_info->set_call(m->as_Call());
      _call_info->set_hot_cg(_if_hot);
#ifndef PRODUCT
      if (PrintOpto || PrintOptoInlining) {
        tty->print_cr("Queueing for warm inlining at bci %d:", jvms->bci());
        tty->print("WCI: ");
        _call_info->print();
      }
#endif
      _call_info->set_heat(_call_info->compute_heat());
      C->set_warm_calls(_call_info->insert_into(C->warm_calls()));
    }
  }
  return jvms;
}

void WarmCallInfo::make_hot() {
  Unimplemented();
}

void WarmCallInfo::make_cold() {
  // No action:  Just dequeue.
}


//------------------------PredictedCallGenerator------------------------------
// Internal class which handles all out-of-line calls checking receiver type.
class PredictedCallGenerator : public CallGenerator {
  ciKlass*       _predicted_receiver;
  CallGenerator* _if_missed;
  CallGenerator* _if_hit;
  float          _hit_prob;

public:
  PredictedCallGenerator(ciKlass* predicted_receiver,
                         CallGenerator* if_missed,
                         CallGenerator* if_hit, float hit_prob)
    : CallGenerator(if_missed->method())
  {
    // The call profile data may predict the hit_prob as extreme as 0 or 1.
    // Remove the extremes values from the range.
    if (hit_prob > PROB_MAX)   hit_prob = PROB_MAX;
    if (hit_prob < PROB_MIN)   hit_prob = PROB_MIN;

    _predicted_receiver = predicted_receiver;
    _if_missed          = if_missed;
    _if_hit             = if_hit;
    _hit_prob           = hit_prob;
  }

  virtual bool      is_virtual()   const    { return true; }
  virtual bool      is_inline()    const    { return _if_hit->is_inline(); }
  virtual bool      is_deferred()  const    { return _if_hit->is_deferred(); }

  virtual JVMState* generate(JVMState* jvms);
};


CallGenerator* CallGenerator::for_predicted_call(ciKlass* predicted_receiver,
                                                 CallGenerator* if_missed,
                                                 CallGenerator* if_hit,
                                                 float hit_prob) {
  return new PredictedCallGenerator(predicted_receiver, if_missed, if_hit, hit_prob);
}


JVMState* PredictedCallGenerator::generate(JVMState* jvms) {
  GraphKit kit(jvms);
  PhaseGVN& gvn = kit.gvn();
  // We need an explicit receiver null_check before checking its type.
  // We share a map with the caller, so his JVMS gets adjusted.
  Node* receiver = kit.argument(0);

  CompileLog* log = kit.C->log();
  if (log != NULL) {
    log->elem("predicted_call bci='%d' klass='%d'",
              jvms->bci(), log->identify(_predicted_receiver));
  }

  receiver = kit.null_check_receiver(method());
  if (kit.stopped()) {
    return kit.transfer_exceptions_into_jvms();
  }

  Node* exact_receiver = receiver;  // will get updated in place...
  Node* slow_ctl = kit.type_check_receiver(receiver,
                                           _predicted_receiver, _hit_prob,
                                           &exact_receiver);

  SafePointNode* slow_map = NULL;
  JVMState* slow_jvms;
  { PreserveJVMState pjvms(&kit);
    kit.set_control(slow_ctl);
    if (!kit.stopped()) {
      slow_jvms = _if_missed->generate(kit.sync_jvms());
      assert(slow_jvms != NULL, "miss path must not fail to generate");
      kit.add_exception_states_from(slow_jvms);
      kit.set_map(slow_jvms->map());
      if (!kit.stopped())
        slow_map = kit.stop();
    }
  }

  if (kit.stopped()) {
    // Instance exactly does not matches the desired type.
    kit.set_jvms(slow_jvms);
    return kit.transfer_exceptions_into_jvms();
  }

  // fall through if the instance exactly matches the desired type
  kit.replace_in_map(receiver, exact_receiver);

  // Make the hot call:
  JVMState* new_jvms = _if_hit->generate(kit.sync_jvms());
  if (new_jvms == NULL) {
    // Inline failed, so make a direct call.
    assert(_if_hit->is_inline(), "must have been a failed inline");
    CallGenerator* cg = CallGenerator::for_direct_call(_if_hit->method());
    new_jvms = cg->generate(kit.sync_jvms());
  }
  kit.add_exception_states_from(new_jvms);
  kit.set_jvms(new_jvms);

  // Need to merge slow and fast?
  if (slow_map == NULL) {
    // The fast path is the only path remaining.
    return kit.transfer_exceptions_into_jvms();
  }

  if (kit.stopped()) {
    // Inlined method threw an exception, so it's just the slow path after all.
    kit.set_jvms(slow_jvms);
    return kit.transfer_exceptions_into_jvms();
  }

  // Finish the diamond.
  kit.C->set_has_split_ifs(true); // Has chance for split-if optimization
  RegionNode* region = new (kit.C, 3) RegionNode(3);
  region->init_req(1, kit.control());
  region->init_req(2, slow_map->control());
  kit.set_control(gvn.transform(region));
  Node* iophi = PhiNode::make(region, kit.i_o(), Type::ABIO);
  iophi->set_req(2, slow_map->i_o());
  kit.set_i_o(gvn.transform(iophi));
  kit.merge_memory(slow_map->merged_memory(), region, 2);
  uint tos = kit.jvms()->stkoff() + kit.sp();
  uint limit = slow_map->req();
  for (uint i = TypeFunc::Parms; i < limit; i++) {
    // Skip unused stack slots; fast forward to monoff();
    if (i == tos) {
      i = kit.jvms()->monoff();
      if( i >= limit ) break;
    }
    Node* m = kit.map()->in(i);
    Node* n = slow_map->in(i);
    if (m != n) {
      const Type* t = gvn.type(m)->meet(gvn.type(n));
      Node* phi = PhiNode::make(region, m, t);
      phi->set_req(2, n);
      kit.map()->set_req(i, gvn.transform(phi));
    }
  }
  return kit.transfer_exceptions_into_jvms();
}


//------------------------PredictedDynamicCallGenerator-----------------------
// Internal class which handles all out-of-line calls checking receiver type.
class PredictedDynamicCallGenerator : public CallGenerator {
  ciMethodHandle* _predicted_method_handle;
  CallGenerator*  _if_missed;
  CallGenerator*  _if_hit;
  float           _hit_prob;

public:
  PredictedDynamicCallGenerator(ciMethodHandle* predicted_method_handle,
                                CallGenerator* if_missed,
                                CallGenerator* if_hit,
                                float hit_prob)
    : CallGenerator(if_missed->method()),
      _predicted_method_handle(predicted_method_handle),
      _if_missed(if_missed),
      _if_hit(if_hit),
      _hit_prob(hit_prob)
  {}

  virtual bool is_inline()   const { return _if_hit->is_inline(); }
  virtual bool is_deferred() const { return _if_hit->is_deferred(); }

  virtual JVMState* generate(JVMState* jvms);
};


CallGenerator* CallGenerator::for_predicted_dynamic_call(ciMethodHandle* predicted_method_handle,
                                                         CallGenerator* if_missed,
                                                         CallGenerator* if_hit,
                                                         float hit_prob) {
  return new PredictedDynamicCallGenerator(predicted_method_handle, if_missed, if_hit, hit_prob);
}


JVMState* PredictedDynamicCallGenerator::generate(JVMState* jvms) {
  GraphKit kit(jvms);
  PhaseGVN& gvn = kit.gvn();

  CompileLog* log = kit.C->log();
  if (log != NULL) {
    log->elem("predicted_dynamic_call bci='%d'", jvms->bci());
  }

  // Get the constant pool cache from the caller class.
  ciMethod* caller_method = jvms->method();
  ciBytecodeStream str(caller_method);
  str.force_bci(jvms->bci());  // Set the stream to the invokedynamic bci.
  ciCPCache* cpcache = str.get_cpcache();

  // Get the offset of the CallSite from the constant pool cache
  // pointer.
  int index = str.get_method_index();
  size_t call_site_offset = cpcache->get_f1_offset(index);

  // Load the CallSite object from the constant pool cache.
  const TypeOopPtr* cpcache_ptr = TypeOopPtr::make_from_constant(cpcache);
  Node* cpcache_adr   = kit.makecon(cpcache_ptr);
  Node* call_site_adr = kit.basic_plus_adr(cpcache_adr, cpcache_adr, call_site_offset);
  Node* call_site     = kit.make_load(kit.control(), call_site_adr, TypeInstPtr::BOTTOM, T_OBJECT, Compile::AliasIdxRaw);

  // Load the target MethodHandle from the CallSite object.
  Node* target_adr = kit.basic_plus_adr(call_site, call_site, java_dyn_CallSite::target_offset_in_bytes());
  Node* target_mh  = kit.make_load(kit.control(), target_adr, TypeInstPtr::BOTTOM, T_OBJECT);

  // Check if the MethodHandle is still the same.
  const TypeOopPtr* predicted_mh_ptr = TypeOopPtr::make_from_constant(_predicted_method_handle, true);
  Node* predicted_mh = kit.makecon(predicted_mh_ptr);

  Node* cmp = gvn.transform(new(kit.C, 3) CmpPNode(target_mh, predicted_mh));
  Node* bol = gvn.transform(new(kit.C, 2) BoolNode(cmp, BoolTest::eq) );
  IfNode* iff = kit.create_and_xform_if(kit.control(), bol, _hit_prob, COUNT_UNKNOWN);
  kit.set_control( gvn.transform(new(kit.C, 1) IfTrueNode (iff)));
  Node* slow_ctl = gvn.transform(new(kit.C, 1) IfFalseNode(iff));

  SafePointNode* slow_map = NULL;
  JVMState* slow_jvms;
  { PreserveJVMState pjvms(&kit);
    kit.set_control(slow_ctl);
    if (!kit.stopped()) {
      slow_jvms = _if_missed->generate(kit.sync_jvms());
      assert(slow_jvms != NULL, "miss path must not fail to generate");
      kit.add_exception_states_from(slow_jvms);
      kit.set_map(slow_jvms->map());
      if (!kit.stopped())
        slow_map = kit.stop();
    }
  }

  if (kit.stopped()) {
    // Instance exactly does not matches the desired type.
    kit.set_jvms(slow_jvms);
    return kit.transfer_exceptions_into_jvms();
  }

  // Make the hot call:
  JVMState* new_jvms = _if_hit->generate(kit.sync_jvms());
  if (new_jvms == NULL) {
    // Inline failed, so make a direct call.
    assert(_if_hit->is_inline(), "must have been a failed inline");
    CallGenerator* cg = CallGenerator::for_direct_call(_if_hit->method());
    new_jvms = cg->generate(kit.sync_jvms());
  }
  kit.add_exception_states_from(new_jvms);
  kit.set_jvms(new_jvms);

  // Need to merge slow and fast?
  if (slow_map == NULL) {
    // The fast path is the only path remaining.
    return kit.transfer_exceptions_into_jvms();
  }

  if (kit.stopped()) {
    // Inlined method threw an exception, so it's just the slow path after all.
    kit.set_jvms(slow_jvms);
    return kit.transfer_exceptions_into_jvms();
  }

  // Finish the diamond.
  kit.C->set_has_split_ifs(true); // Has chance for split-if optimization
  RegionNode* region = new (kit.C, 3) RegionNode(3);
  region->init_req(1, kit.control());
  region->init_req(2, slow_map->control());
  kit.set_control(gvn.transform(region));
  Node* iophi = PhiNode::make(region, kit.i_o(), Type::ABIO);
  iophi->set_req(2, slow_map->i_o());
  kit.set_i_o(gvn.transform(iophi));
  kit.merge_memory(slow_map->merged_memory(), region, 2);
  uint tos = kit.jvms()->stkoff() + kit.sp();
  uint limit = slow_map->req();
  for (uint i = TypeFunc::Parms; i < limit; i++) {
    // Skip unused stack slots; fast forward to monoff();
    if (i == tos) {
      i = kit.jvms()->monoff();
      if( i >= limit ) break;
    }
    Node* m = kit.map()->in(i);
    Node* n = slow_map->in(i);
    if (m != n) {
      const Type* t = gvn.type(m)->meet(gvn.type(n));
      Node* phi = PhiNode::make(region, m, t);
      phi->set_req(2, n);
      kit.map()->set_req(i, gvn.transform(phi));
    }
  }
  return kit.transfer_exceptions_into_jvms();
}


//-------------------------UncommonTrapCallGenerator-----------------------------
// Internal class which handles all out-of-line calls checking receiver type.
class UncommonTrapCallGenerator : public CallGenerator {
  Deoptimization::DeoptReason _reason;
  Deoptimization::DeoptAction _action;

public:
  UncommonTrapCallGenerator(ciMethod* m,
                            Deoptimization::DeoptReason reason,
                            Deoptimization::DeoptAction action)
    : CallGenerator(m)
  {
    _reason = reason;
    _action = action;
  }

  virtual bool      is_virtual() const          { ShouldNotReachHere(); return false; }
  virtual bool      is_trap() const             { return true; }

  virtual JVMState* generate(JVMState* jvms);
};


CallGenerator*
CallGenerator::for_uncommon_trap(ciMethod* m,
                                 Deoptimization::DeoptReason reason,
                                 Deoptimization::DeoptAction action) {
  return new UncommonTrapCallGenerator(m, reason, action);
}


JVMState* UncommonTrapCallGenerator::generate(JVMState* jvms) {
  GraphKit kit(jvms);
  // Take the trap with arguments pushed on the stack.  (Cf. null_check_receiver).
  int nargs = method()->arg_size();
  kit.inc_sp(nargs);
  assert(nargs <= kit.sp() && kit.sp() <= jvms->stk_size(), "sane sp w/ args pushed");
  if (_reason == Deoptimization::Reason_class_check &&
      _action == Deoptimization::Action_maybe_recompile) {
    // Temp fix for 6529811
    // Don't allow uncommon_trap to override our decision to recompile in the event
    // of a class cast failure for a monomorphic call as it will never let us convert
    // the call to either bi-morphic or megamorphic and can lead to unc-trap loops
    bool keep_exact_action = true;
    kit.uncommon_trap(_reason, _action, NULL, "monomorphic vcall checkcast", false, keep_exact_action);
  } else {
    kit.uncommon_trap(_reason, _action);
  }
  return kit.transfer_exceptions_into_jvms();
}

// (Note:  Moved hook_up_call to GraphKit::set_edges_for_java_call.)

// (Node:  Merged hook_up_exits into ParseGenerator::generate.)

#define NODES_OVERHEAD_PER_METHOD (30.0)
#define NODES_PER_BYTECODE (9.5)

void WarmCallInfo::init(JVMState* call_site, ciMethod* call_method, ciCallProfile& profile, float prof_factor) {
  int call_count = profile.count();
  int code_size = call_method->code_size();

  // Expected execution count is based on the historical count:
  _count = call_count < 0 ? 1 : call_site->method()->scale_count(call_count, prof_factor);

  // Expected profit from inlining, in units of simple call-overheads.
  _profit = 1.0;

  // Expected work performed by the call in units of call-overheads.
  // %%% need an empirical curve fit for "work" (time in call)
  float bytecodes_per_call = 3;
  _work = 1.0 + code_size / bytecodes_per_call;

  // Expected size of compilation graph:
  // -XX:+PrintParseStatistics once reported:
  //  Methods seen: 9184  Methods parsed: 9184  Nodes created: 1582391
  //  Histogram of 144298 parsed bytecodes:
  // %%% Need an better predictor for graph size.
  _size = NODES_OVERHEAD_PER_METHOD + (NODES_PER_BYTECODE * code_size);
}

// is_cold:  Return true if the node should never be inlined.
// This is true if any of the key metrics are extreme.
bool WarmCallInfo::is_cold() const {
  if (count()  <  WarmCallMinCount)        return true;
  if (profit() <  WarmCallMinProfit)       return true;
  if (work()   >  WarmCallMaxWork)         return true;
  if (size()   >  WarmCallMaxSize)         return true;
  return false;
}

// is_hot:  Return true if the node should be inlined immediately.
// This is true if any of the key metrics are extreme.
bool WarmCallInfo::is_hot() const {
  assert(!is_cold(), "eliminate is_cold cases before testing is_hot");
  if (count()  >= HotCallCountThreshold)   return true;
  if (profit() >= HotCallProfitThreshold)  return true;
  if (work()   <= HotCallTrivialWork)      return true;
  if (size()   <= HotCallTrivialSize)      return true;
  return false;
}

// compute_heat:
float WarmCallInfo::compute_heat() const {
  assert(!is_cold(), "compute heat only on warm nodes");
  assert(!is_hot(),  "compute heat only on warm nodes");
  int min_size = MAX2(0,   (int)HotCallTrivialSize);
  int max_size = MIN2(500, (int)WarmCallMaxSize);
  float method_size = (size() - min_size) / MAX2(1, max_size - min_size);
  float size_factor;
  if      (method_size < 0.05)  size_factor = 4;   // 2 sigmas better than avg.
  else if (method_size < 0.15)  size_factor = 2;   // 1 sigma better than avg.
  else if (method_size < 0.5)   size_factor = 1;   // better than avg.
  else                          size_factor = 0.5; // worse than avg.
  return (count() * profit() * size_factor);
}

bool WarmCallInfo::warmer_than(WarmCallInfo* that) {
  assert(this != that, "compare only different WCIs");
  assert(this->heat() != 0 && that->heat() != 0, "call compute_heat 1st");
  if (this->heat() > that->heat())   return true;
  if (this->heat() < that->heat())   return false;
  assert(this->heat() == that->heat(), "no NaN heat allowed");
  // Equal heat.  Break the tie some other way.
  if (!this->call() || !that->call())  return (address)this > (address)that;
  return this->call()->_idx > that->call()->_idx;
}

//#define UNINIT_NEXT ((WarmCallInfo*)badAddress)
#define UNINIT_NEXT ((WarmCallInfo*)NULL)

WarmCallInfo* WarmCallInfo::insert_into(WarmCallInfo* head) {
  assert(next() == UNINIT_NEXT, "not yet on any list");
  WarmCallInfo* prev_p = NULL;
  WarmCallInfo* next_p = head;
  while (next_p != NULL && next_p->warmer_than(this)) {
    prev_p = next_p;
    next_p = prev_p->next();
  }
  // Install this between prev_p and next_p.
  this->set_next(next_p);
  if (prev_p == NULL)
    head = this;
  else
    prev_p->set_next(this);
  return head;
}

WarmCallInfo* WarmCallInfo::remove_from(WarmCallInfo* head) {
  WarmCallInfo* prev_p = NULL;
  WarmCallInfo* next_p = head;
  while (next_p != this) {
    assert(next_p != NULL, "this must be in the list somewhere");
    prev_p = next_p;
    next_p = prev_p->next();
  }
  next_p = this->next();
  debug_only(this->set_next(UNINIT_NEXT));
  // Remove this from between prev_p and next_p.
  if (prev_p == NULL)
    head = next_p;
  else
    prev_p->set_next(next_p);
  return head;
}

WarmCallInfo* WarmCallInfo::_always_hot  = NULL;
WarmCallInfo* WarmCallInfo::_always_cold = NULL;

WarmCallInfo* WarmCallInfo::always_hot() {
  if (_always_hot == NULL) {
    static double bits[sizeof(WarmCallInfo) / sizeof(double) + 1] = {0};
    WarmCallInfo* ci = (WarmCallInfo*) bits;
    ci->_profit = ci->_count = MAX_VALUE();
    ci->_work   = ci->_size  = MIN_VALUE();
    _always_hot = ci;
  }
  assert(_always_hot->is_hot(), "must always be hot");
  return _always_hot;
}

WarmCallInfo* WarmCallInfo::always_cold() {
  if (_always_cold == NULL) {
    static double bits[sizeof(WarmCallInfo) / sizeof(double) + 1] = {0};
    WarmCallInfo* ci = (WarmCallInfo*) bits;
    ci->_profit = ci->_count = MIN_VALUE();
    ci->_work   = ci->_size  = MAX_VALUE();
    _always_cold = ci;
  }
  assert(_always_cold->is_cold(), "must always be cold");
  return _always_cold;
}


#ifndef PRODUCT

void WarmCallInfo::print() const {
  tty->print("%s : C=%6.1f P=%6.1f W=%6.1f S=%6.1f H=%6.1f -> %p",
             is_cold() ? "cold" : is_hot() ? "hot " : "warm",
             count(), profit(), work(), size(), compute_heat(), next());
  tty->cr();
  if (call() != NULL)  call()->dump();
}

void print_wci(WarmCallInfo* ci) {
  ci->print();
}

void WarmCallInfo::print_all() const {
  for (const WarmCallInfo* p = this; p != NULL; p = p->next())
    p->print();
}

int WarmCallInfo::count_all() const {
  int cnt = 0;
  for (const WarmCallInfo* p = this; p != NULL; p = p->next())
    cnt++;
  return cnt;
}

#endif //PRODUCT
