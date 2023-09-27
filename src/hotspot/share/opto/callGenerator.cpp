/*
 * Copyright (c) 2000, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "ci/bcEscapeAnalyzer.hpp"
#include "ci/ciCallSite.hpp"
#include "ci/ciObjArray.hpp"
#include "ci/ciMemberName.hpp"
#include "ci/ciMethodHandle.hpp"
#include "classfile/javaClasses.hpp"
#include "compiler/compileLog.hpp"
#include "gc/shared/barrierSet.hpp"
#include "opto/addnode.hpp"
#include "opto/callGenerator.hpp"
#include "opto/callnode.hpp"
#include "opto/castnode.hpp"
#include "opto/cfgnode.hpp"
#include "opto/intrinsicnode.hpp"
#include "opto/parse.hpp"
#include "opto/rootnode.hpp"
#include "opto/runtime.hpp"
#include "opto/subnode.hpp"
#include "runtime/os.inline.hpp"
#include "runtime/sharedRuntime.hpp"
#include "utilities/debug.hpp"

// Utility function.
const TypeFunc* CallGenerator::tf() const {
  return TypeFunc::make(method());
}

bool CallGenerator::is_inlined_method_handle_intrinsic(JVMState* jvms, ciMethod* m) {
  return is_inlined_method_handle_intrinsic(jvms->method(), jvms->bci(), m);
}

bool CallGenerator::is_inlined_method_handle_intrinsic(ciMethod* caller, int bci, ciMethod* m) {
  ciMethod* symbolic_info = caller->get_method_at_bci(bci);
  return is_inlined_method_handle_intrinsic(symbolic_info, m);
}

bool CallGenerator::is_inlined_method_handle_intrinsic(ciMethod* symbolic_info, ciMethod* m) {
  return symbolic_info->is_method_handle_intrinsic() && !m->is_method_handle_intrinsic();
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
    assert(InlineTree::check_can_parse(method) == nullptr, "parse must be possible");
  }

  virtual bool      is_parse() const           { return true; }
  virtual JVMState* generate(JVMState* jvms);
  int is_osr() { return _is_osr; }

};

JVMState* ParseGenerator::generate(JVMState* jvms) {
  Compile* C = Compile::current();
  C->print_inlining_update(this);

  if (is_osr()) {
    // The JVMS for a OSR has a single argument (see its TypeFunc).
    assert(jvms->depth() == 1, "no inline OSR");
  }

  if (C->failing()) {
    return nullptr;  // bailing out of the compile; do not try to parse
  }

  Parse parser(jvms, method(), _expected_uses);
  if (C->failing()) return nullptr;

  // Grab signature for matching/allocation
  GraphKit& exits = parser.exits();

  if (C->failing()) {
    while (exits.pop_exception_state() != nullptr) ;
    return nullptr;
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

protected:
  void set_call_node(CallStaticJavaNode* call) { _call_node = call; }

 public:
  DirectCallGenerator(ciMethod* method, bool separate_io_proj)
    : CallGenerator(method),
      _separate_io_proj(separate_io_proj)
  {
  }
  virtual JVMState* generate(JVMState* jvms);

  virtual CallNode* call_node() const { return _call_node; }
  virtual CallGenerator* with_call_node(CallNode* call) {
    DirectCallGenerator* dcg = new DirectCallGenerator(method(), _separate_io_proj);
    dcg->set_call_node(call->as_CallStaticJava());
    return dcg;
  }
};

JVMState* DirectCallGenerator::generate(JVMState* jvms) {
  GraphKit kit(jvms);
  kit.C->print_inlining_update(this);
  bool is_static = method()->is_static();
  address target = is_static ? SharedRuntime::get_resolve_static_call_stub()
                             : SharedRuntime::get_resolve_opt_virtual_call_stub();

  if (kit.C->log() != nullptr) {
    kit.C->log()->elem("direct_call bci='%d'", jvms->bci());
  }

  CallStaticJavaNode* call = new CallStaticJavaNode(kit.C, tf(), target, method());
  if (is_inlined_method_handle_intrinsic(jvms, method())) {
    // To be able to issue a direct call and skip a call to MH.linkTo*/invokeBasic adapter,
    // additional information about the method being invoked should be attached
    // to the call site to make resolution logic work
    // (see SharedRuntime::resolve_static_call_C).
    call->set_override_symbolic_info(true);
  }
  _call_node = call;  // Save the call node in case we need it later
  if (!is_static) {
    // Make an explicit receiver null_check as part of this call.
    // Since we share a map with the caller, his JVMS gets adjusted.
    kit.null_check_receiver_before_call(method());
    if (kit.stopped()) {
      // And dump it back to the caller, decorated with any exceptions:
      return kit.transfer_exceptions_into_jvms();
    }
    // Mark the call node as virtual, sort of:
    call->set_optimized_virtual(true);
    if (method()->is_method_handle_intrinsic() ||
        method()->is_compiled_lambda_form()) {
      call->set_method_handle_invoke(true);
    }
  }
  kit.set_arguments_for_java_call(call);
  kit.set_edges_for_java_call(call, false, _separate_io_proj);
  Node* ret = kit.set_results_for_java_call(call, _separate_io_proj);
  kit.push_node(method()->return_type()->basic_type(), ret);
  return kit.transfer_exceptions_into_jvms();
}

//--------------------------VirtualCallGenerator------------------------------
// Internal class which handles all out-of-line calls checking receiver type.
class VirtualCallGenerator : public CallGenerator {
private:
  int _vtable_index;
  bool _separate_io_proj;
  CallDynamicJavaNode* _call_node;

protected:
  void set_call_node(CallDynamicJavaNode* call) { _call_node = call; }

public:
  VirtualCallGenerator(ciMethod* method, int vtable_index, bool separate_io_proj)
    : CallGenerator(method), _vtable_index(vtable_index), _separate_io_proj(separate_io_proj), _call_node(nullptr)
  {
    assert(vtable_index == Method::invalid_vtable_index ||
           vtable_index >= 0, "either invalid or usable");
  }
  virtual bool      is_virtual() const          { return true; }
  virtual JVMState* generate(JVMState* jvms);

  virtual CallNode* call_node() const { return _call_node; }
  int vtable_index() const { return _vtable_index; }

  virtual CallGenerator* with_call_node(CallNode* call) {
    VirtualCallGenerator* cg = new VirtualCallGenerator(method(), _vtable_index, _separate_io_proj);
    cg->set_call_node(call->as_CallDynamicJava());
    return cg;
  }
};

JVMState* VirtualCallGenerator::generate(JVMState* jvms) {
  GraphKit kit(jvms);
  Node* receiver = kit.argument(0);

  kit.C->print_inlining_update(this);

  if (kit.C->log() != nullptr) {
    kit.C->log()->elem("virtual_call bci='%d'", jvms->bci());
  }

  // If the receiver is a constant null, do not torture the system
  // by attempting to call through it.  The compile will proceed
  // correctly, but may bail out in final_graph_reshaping, because
  // the call instruction will have a seemingly deficient out-count.
  // (The bailout says something misleading about an "infinite loop".)
  if (kit.gvn().type(receiver)->higher_equal(TypePtr::NULL_PTR)) {
    assert(Bytecodes::is_invoke(kit.java_bc()), "%d: %s", kit.java_bc(), Bytecodes::name(kit.java_bc()));
    ciMethod* declared_method = kit.method()->get_method_at_bci(kit.bci());
    int arg_size = declared_method->signature()->arg_size_for_bc(kit.java_bc());
    kit.inc_sp(arg_size);  // restore arguments
    kit.uncommon_trap(Deoptimization::Reason_null_check,
                      Deoptimization::Action_none,
                      nullptr, "null receiver");
    return kit.transfer_exceptions_into_jvms();
  }

  // Ideally we would unconditionally do a null check here and let it
  // be converted to an implicit check based on profile information.
  // However currently the conversion to implicit null checks in
  // Block::implicit_null_check() only looks for loads and stores, not calls.
  ciMethod *caller = kit.method();
  ciMethodData *caller_md = (caller == nullptr) ? nullptr : caller->method_data();
  if (!UseInlineCaches || !ImplicitNullChecks || !os::zero_page_read_protected() ||
       ((ImplicitNullCheckThreshold > 0) && caller_md &&
       (caller_md->trap_count(Deoptimization::Reason_null_check)
       >= (uint)ImplicitNullCheckThreshold))) {
    // Make an explicit receiver null_check as part of this call.
    // Since we share a map with the caller, his JVMS gets adjusted.
    receiver = kit.null_check_receiver_before_call(method());
    if (kit.stopped()) {
      // And dump it back to the caller, decorated with any exceptions:
      return kit.transfer_exceptions_into_jvms();
    }
  }

  assert(!method()->is_static(), "virtual call must not be to static");
  assert(!method()->is_final(), "virtual call should not be to final");
  assert(!method()->is_private(), "virtual call should not be to private");
  assert(_vtable_index == Method::invalid_vtable_index || !UseInlineCaches,
         "no vtable calls if +UseInlineCaches ");
  address target = SharedRuntime::get_resolve_virtual_call_stub();
  // Normal inline cache used for call
  CallDynamicJavaNode* call = new CallDynamicJavaNode(tf(), target, method(), _vtable_index);
  if (is_inlined_method_handle_intrinsic(jvms, method())) {
    // To be able to issue a direct call (optimized virtual or virtual)
    // and skip a call to MH.linkTo*/invokeBasic adapter, additional information
    // about the method being invoked should be attached to the call site to
    // make resolution logic work (see SharedRuntime::resolve_{virtual,opt_virtual}_call_C).
    call->set_override_symbolic_info(true);
  }
  _call_node = call;  // Save the call node in case we need it later

  kit.set_arguments_for_java_call(call);
  kit.set_edges_for_java_call(call, false /*must_throw*/, _separate_io_proj);
  Node* ret = kit.set_results_for_java_call(call, _separate_io_proj);
  kit.push_node(method()->return_type()->basic_type(), ret);

  // Represent the effect of an implicit receiver null_check
  // as part of this call.  Since we share a map with the caller,
  // his JVMS gets adjusted.
  kit.cast_not_null(receiver);
  return kit.transfer_exceptions_into_jvms();
}

CallGenerator* CallGenerator::for_inline(ciMethod* m, float expected_uses) {
  if (InlineTree::check_can_parse(m) != nullptr)  return nullptr;
  return new ParseGenerator(m, expected_uses);
}

// As a special case, the JVMS passed to this CallGenerator is
// for the method execution already in progress, not just the JVMS
// of the caller.  Thus, this CallGenerator cannot be mixed with others!
CallGenerator* CallGenerator::for_osr(ciMethod* m, int osr_bci) {
  if (InlineTree::check_can_parse(m) != nullptr)  return nullptr;
  float past_uses = m->interpreter_invocation_count();
  float expected_uses = past_uses;
  return new ParseGenerator(m, expected_uses, true);
}

CallGenerator* CallGenerator::for_direct_call(ciMethod* m, bool separate_io_proj) {
  assert(!m->is_abstract(), "for_direct_call mismatch");
  return new DirectCallGenerator(m, separate_io_proj);
}

CallGenerator* CallGenerator::for_virtual_call(ciMethod* m, int vtable_index) {
  assert(!m->is_static(), "for_virtual_call mismatch");
  assert(!m->is_method_handle_intrinsic(), "should be a direct call");
  return new VirtualCallGenerator(m, vtable_index, false /*separate_io_projs*/);
}

// Allow inlining decisions to be delayed
class LateInlineCallGenerator : public DirectCallGenerator {
 private:
  jlong _unique_id;   // unique id for log compilation
  bool _is_pure_call; // a hint that the call doesn't have important side effects to care about

 protected:
  CallGenerator* _inline_cg;
  virtual bool do_late_inline_check(Compile* C, JVMState* jvms) { return true; }
  virtual CallGenerator* inline_cg() const { return _inline_cg; }
  virtual bool is_pure_call() const { return _is_pure_call; }

 public:
  LateInlineCallGenerator(ciMethod* method, CallGenerator* inline_cg, bool is_pure_call = false) :
    DirectCallGenerator(method, true), _unique_id(0), _is_pure_call(is_pure_call), _inline_cg(inline_cg) {}

  virtual bool is_late_inline() const { return true; }

  // Convert the CallStaticJava into an inline
  virtual void do_late_inline();

  virtual JVMState* generate(JVMState* jvms) {
    Compile *C = Compile::current();

    C->log_inline_id(this);

    // Record that this call site should be revisited once the main
    // parse is finished.
    if (!is_mh_late_inline()) {
      C->add_late_inline(this);
    }

    // Emit the CallStaticJava and request separate projections so
    // that the late inlining logic can distinguish between fall
    // through and exceptional uses of the memory and io projections
    // as is done for allocations and macro expansion.
    return DirectCallGenerator::generate(jvms);
  }

  virtual void print_inlining_late(InliningResult result, const char* msg) {
    CallNode* call = call_node();
    Compile* C = Compile::current();
    C->print_inlining_assert_ready();
    C->print_inlining(method(), call->jvms()->depth()-1, call->jvms()->bci(), result, msg);
    C->print_inlining_move_to(this);
    C->print_inlining_update_delayed(this);
  }

  virtual void set_unique_id(jlong id) {
    _unique_id = id;
  }

  virtual jlong unique_id() const {
    return _unique_id;
  }

  virtual CallGenerator* with_call_node(CallNode* call) {
    LateInlineCallGenerator* cg = new LateInlineCallGenerator(method(), _inline_cg, _is_pure_call);
    cg->set_call_node(call->as_CallStaticJava());
    return cg;
  }
};

CallGenerator* CallGenerator::for_late_inline(ciMethod* method, CallGenerator* inline_cg) {
  return new LateInlineCallGenerator(method, inline_cg);
}

class LateInlineMHCallGenerator : public LateInlineCallGenerator {
  ciMethod* _caller;
  bool _input_not_const;

  virtual bool do_late_inline_check(Compile* C, JVMState* jvms);

 public:
  LateInlineMHCallGenerator(ciMethod* caller, ciMethod* callee, bool input_not_const) :
    LateInlineCallGenerator(callee, nullptr), _caller(caller), _input_not_const(input_not_const) {}

  virtual bool is_mh_late_inline() const { return true; }

  // Convert the CallStaticJava into an inline
  virtual void do_late_inline();

  virtual JVMState* generate(JVMState* jvms) {
    JVMState* new_jvms = LateInlineCallGenerator::generate(jvms);

    Compile* C = Compile::current();
    if (_input_not_const) {
      // inlining won't be possible so no need to enqueue right now.
      call_node()->set_generator(this);
    } else {
      C->add_late_inline(this);
    }
    return new_jvms;
  }

  virtual CallGenerator* with_call_node(CallNode* call) {
    LateInlineMHCallGenerator* cg = new LateInlineMHCallGenerator(_caller, method(), _input_not_const);
    cg->set_call_node(call->as_CallStaticJava());
    return cg;
  }
};

bool LateInlineMHCallGenerator::do_late_inline_check(Compile* C, JVMState* jvms) {
  // When inlining a virtual call, the null check at the call and the call itself can throw. These 2 paths have different
  // expression stacks which causes late inlining to break. The MH invoker is not expected to be called from a method with
  // exception handlers. When there is no exception handler, GraphKit::builtin_throw() pops the stack which solves the issue
  // of late inlining with exceptions.
  assert(!jvms->method()->has_exception_handlers() ||
         (method()->intrinsic_id() != vmIntrinsics::_linkToVirtual &&
          method()->intrinsic_id() != vmIntrinsics::_linkToInterface), "no exception handler expected");
  // Even if inlining is not allowed, a virtual call can be strength-reduced to a direct call.
  bool allow_inline = C->inlining_incrementally();
  bool input_not_const = true;
  CallGenerator* cg = for_method_handle_inline(jvms, _caller, method(), allow_inline, input_not_const);
  assert(!input_not_const, "sanity"); // shouldn't have been scheduled for inlining in the first place

  if (cg != nullptr) {
    assert(!cg->is_late_inline() || cg->is_mh_late_inline() || AlwaysIncrementalInline || StressIncrementalInlining, "we're doing late inlining");
    _inline_cg = cg;
    C->dec_number_of_mh_late_inlines();
    return true;
  } else {
    // Method handle call which has a constant appendix argument should be either inlined or replaced with a direct call
    // unless there's a signature mismatch between caller and callee. If the failure occurs, there's not much to be improved later,
    // so don't reinstall the generator to avoid pushing the generator between IGVN and incremental inlining indefinitely.
    return false;
  }
}

CallGenerator* CallGenerator::for_mh_late_inline(ciMethod* caller, ciMethod* callee, bool input_not_const) {
  assert(IncrementalInlineMH, "required");
  Compile::current()->inc_number_of_mh_late_inlines();
  CallGenerator* cg = new LateInlineMHCallGenerator(caller, callee, input_not_const);
  return cg;
}

// Allow inlining decisions to be delayed
class LateInlineVirtualCallGenerator : public VirtualCallGenerator {
 private:
  jlong          _unique_id;   // unique id for log compilation
  CallGenerator* _inline_cg;
  ciMethod*      _callee;
  bool           _is_pure_call;
  float          _prof_factor;

 protected:
  virtual bool do_late_inline_check(Compile* C, JVMState* jvms);
  virtual CallGenerator* inline_cg() const { return _inline_cg; }
  virtual bool is_pure_call() const { return _is_pure_call; }

 public:
  LateInlineVirtualCallGenerator(ciMethod* method, int vtable_index, float prof_factor)
  : VirtualCallGenerator(method, vtable_index, true /*separate_io_projs*/),
    _unique_id(0), _inline_cg(nullptr), _callee(nullptr), _is_pure_call(false), _prof_factor(prof_factor) {
    assert(IncrementalInlineVirtual, "required");
  }

  virtual bool is_late_inline() const { return true; }

  virtual bool is_virtual_late_inline() const { return true; }

  // Convert the CallDynamicJava into an inline
  virtual void do_late_inline();

  virtual void set_callee_method(ciMethod* m) {
    assert(_callee == nullptr, "repeated inlining attempt");
    _callee = m;
  }

  virtual JVMState* generate(JVMState* jvms) {
    // Emit the CallDynamicJava and request separate projections so
    // that the late inlining logic can distinguish between fall
    // through and exceptional uses of the memory and io projections
    // as is done for allocations and macro expansion.
    JVMState* new_jvms = VirtualCallGenerator::generate(jvms);
    if (call_node() != nullptr) {
      call_node()->set_generator(this);
    }
    return new_jvms;
  }

  virtual void print_inlining_late(InliningResult result, const char* msg) {
    CallNode* call = call_node();
    Compile* C = Compile::current();
    C->print_inlining_assert_ready();
    C->print_inlining(method(), call->jvms()->depth()-1, call->jvms()->bci(), result, msg);
    C->print_inlining_move_to(this);
    C->print_inlining_update_delayed(this);
  }

  virtual void set_unique_id(jlong id) {
    _unique_id = id;
  }

  virtual jlong unique_id() const {
    return _unique_id;
  }

  virtual CallGenerator* with_call_node(CallNode* call) {
    LateInlineVirtualCallGenerator* cg = new LateInlineVirtualCallGenerator(method(), vtable_index(), _prof_factor);
    cg->set_call_node(call->as_CallDynamicJava());
    return cg;
  }
};

bool LateInlineVirtualCallGenerator::do_late_inline_check(Compile* C, JVMState* jvms) {
  // Method handle linker case is handled in CallDynamicJavaNode::Ideal().
  // Unless inlining is performed, _override_symbolic_info bit will be set in DirectCallGenerator::generate().

  // Implicit receiver null checks introduce problems when exception states are combined.
  Node* receiver = jvms->map()->argument(jvms, 0);
  const Type* recv_type = C->initial_gvn()->type(receiver);
  if (recv_type->maybe_null()) {
    if (C->print_inlining() || C->print_intrinsics()) {
      C->print_inlining(method(), jvms->depth()-1, call_node()->jvms()->bci(), InliningResult::FAILURE,
                        "late call devirtualization failed (receiver may be null)");
    }
    return false;
  }
  // Even if inlining is not allowed, a virtual call can be strength-reduced to a direct call.
  bool allow_inline = C->inlining_incrementally();
  if (!allow_inline && _callee->holder()->is_interface()) {
    // Don't convert the interface call to a direct call guarded by an interface subtype check.
    if (C->print_inlining() || C->print_intrinsics()) {
      C->print_inlining(method(), jvms->depth()-1, call_node()->jvms()->bci(), InliningResult::FAILURE,
                        "late call devirtualization failed (interface call)");
    }
    return false;
  }
  CallGenerator* cg = C->call_generator(_callee,
                                        vtable_index(),
                                        false /*call_does_dispatch*/,
                                        jvms,
                                        allow_inline,
                                        _prof_factor,
                                        nullptr /*speculative_receiver_type*/,
                                        true /*allow_intrinsics*/);

  if (cg != nullptr) {
    assert(!cg->is_late_inline() || cg->is_mh_late_inline() || AlwaysIncrementalInline || StressIncrementalInlining, "we're doing late inlining");
    _inline_cg = cg;
    return true;
  } else {
    // Virtual call which provably doesn't dispatch should be either inlined or replaced with a direct call.
    assert(false, "no progress");
    return false;
  }
}

CallGenerator* CallGenerator::for_late_inline_virtual(ciMethod* m, int vtable_index, float prof_factor) {
  assert(IncrementalInlineVirtual, "required");
  assert(!m->is_static(), "for_virtual_call mismatch");
  assert(!m->is_method_handle_intrinsic(), "should be a direct call");
  return new LateInlineVirtualCallGenerator(m, vtable_index, prof_factor);
}

void LateInlineCallGenerator::do_late_inline() {
  CallGenerator::do_late_inline_helper();
}

void LateInlineMHCallGenerator::do_late_inline() {
  CallGenerator::do_late_inline_helper();
}

void LateInlineVirtualCallGenerator::do_late_inline() {
  assert(_callee != nullptr, "required"); // set up in CallDynamicJavaNode::Ideal
  CallGenerator::do_late_inline_helper();
}

void CallGenerator::do_late_inline_helper() {
  assert(is_late_inline(), "only late inline allowed");

  // Can't inline it
  CallNode* call = call_node();
  if (call == nullptr || call->outcnt() == 0 ||
      call->in(0) == nullptr || call->in(0)->is_top()) {
    return;
  }

  const TypeTuple *r = call->tf()->domain();
  for (int i1 = 0; i1 < method()->arg_size(); i1++) {
    if (call->in(TypeFunc::Parms + i1)->is_top() && r->field_at(TypeFunc::Parms + i1) != Type::HALF) {
      assert(Compile::current()->inlining_incrementally(), "shouldn't happen during parsing");
      return;
    }
  }

  if (call->in(TypeFunc::Memory)->is_top()) {
    assert(Compile::current()->inlining_incrementally(), "shouldn't happen during parsing");
    return;
  }
  if (call->in(TypeFunc::Memory)->is_MergeMem()) {
    MergeMemNode* merge_mem = call->in(TypeFunc::Memory)->as_MergeMem();
    if (merge_mem->base_memory() == merge_mem->empty_memory()) {
      return; // dead path
    }
  }

  // check for unreachable loop
  CallProjections callprojs;
  call->extract_projections(&callprojs, true);
  if ((callprojs.fallthrough_catchproj == call->in(0)) ||
      (callprojs.catchall_catchproj    == call->in(0)) ||
      (callprojs.fallthrough_memproj   == call->in(TypeFunc::Memory)) ||
      (callprojs.catchall_memproj      == call->in(TypeFunc::Memory)) ||
      (callprojs.fallthrough_ioproj    == call->in(TypeFunc::I_O)) ||
      (callprojs.catchall_ioproj       == call->in(TypeFunc::I_O)) ||
      (callprojs.resproj != nullptr && call->find_edge(callprojs.resproj) != -1) ||
      (callprojs.exobj   != nullptr && call->find_edge(callprojs.exobj) != -1)) {
    return;
  }

  Compile* C = Compile::current();
  // Remove inlined methods from Compiler's lists.
  if (call->is_macro()) {
    C->remove_macro_node(call);
  }

  // The call is marked as pure (no important side effects), but result isn't used.
  // It's safe to remove the call.
  bool result_not_used = (callprojs.resproj == nullptr || callprojs.resproj->outcnt() == 0);

  if (is_pure_call() && result_not_used) {
    GraphKit kit(call->jvms());
    kit.replace_call(call, C->top(), true);
  } else {
    // Make a clone of the JVMState that appropriate to use for driving a parse
    JVMState* old_jvms = call->jvms();
    JVMState* jvms = old_jvms->clone_shallow(C);
    uint size = call->req();
    SafePointNode* map = new SafePointNode(size, jvms);
    for (uint i1 = 0; i1 < size; i1++) {
      map->init_req(i1, call->in(i1));
    }

    // Make sure the state is a MergeMem for parsing.
    if (!map->in(TypeFunc::Memory)->is_MergeMem()) {
      Node* mem = MergeMemNode::make(map->in(TypeFunc::Memory));
      C->initial_gvn()->set_type_bottom(mem);
      map->set_req(TypeFunc::Memory, mem);
    }

    uint nargs = method()->arg_size();
    // blow away old call arguments
    Node* top = C->top();
    for (uint i1 = 0; i1 < nargs; i1++) {
      map->set_req(TypeFunc::Parms + i1, top);
    }
    jvms->set_map(map);

    // Make enough space in the expression stack to transfer
    // the incoming arguments and return value.
    map->ensure_stack(jvms, jvms->method()->max_stack());
    for (uint i1 = 0; i1 < nargs; i1++) {
      map->set_argument(jvms, i1, call->in(TypeFunc::Parms + i1));
    }

    C->print_inlining_assert_ready();

    C->print_inlining_move_to(this);

    C->log_late_inline(this);

    // JVMState is ready, so time to perform some checks and prepare for inlining attempt.
    if (!do_late_inline_check(C, jvms)) {
      map->disconnect_inputs(C);
      C->print_inlining_update_delayed(this);
      return;
    }

    // Setup default node notes to be picked up by the inlining
    Node_Notes* old_nn = C->node_notes_at(call->_idx);
    if (old_nn != nullptr) {
      Node_Notes* entry_nn = old_nn->clone(C);
      entry_nn->set_jvms(jvms);
      C->set_default_node_notes(entry_nn);
    }

    // Now perform the inlining using the synthesized JVMState
    JVMState* new_jvms = inline_cg()->generate(jvms);
    if (new_jvms == nullptr)  return;  // no change
    if (C->failing())      return;

    // Capture any exceptional control flow
    GraphKit kit(new_jvms);

    process_result(kit);

    // Find the result object
    Node* result = C->top();
    int   result_size = method()->return_type()->size();
    if (result_size != 0 && !kit.stopped()) {
      result = (result_size == 1) ? kit.pop() : kit.pop_pair();
    }

    if (call->is_CallStaticJava() && call->as_CallStaticJava()->is_boxing_method()) {
      result = kit.must_be_not_null(result, false);
    }

    if (inline_cg()->is_inline()) {
      C->set_has_loops(C->has_loops() || inline_cg()->method()->has_loops());
      C->env()->notice_inlined_method(inline_cg()->method());
    }
    C->set_inlining_progress(true);
    C->set_do_cleanup(kit.stopped()); // path is dead; needs cleanup
    kit.replace_call(call, result, true);
  }
}

class LateInlineStringCallGenerator : public LateInlineCallGenerator {

 public:
  LateInlineStringCallGenerator(ciMethod* method, CallGenerator* inline_cg) :
    LateInlineCallGenerator(method, inline_cg) {}

  virtual JVMState* generate(JVMState* jvms) {
    Compile *C = Compile::current();

    C->log_inline_id(this);

    C->add_string_late_inline(this);

    JVMState* new_jvms = DirectCallGenerator::generate(jvms);
    return new_jvms;
  }

  virtual bool is_string_late_inline() const { return true; }

  virtual CallGenerator* with_call_node(CallNode* call) {
    LateInlineStringCallGenerator* cg = new LateInlineStringCallGenerator(method(), _inline_cg);
    cg->set_call_node(call->as_CallStaticJava());
    return cg;
  }
};

CallGenerator* CallGenerator::for_string_late_inline(ciMethod* method, CallGenerator* inline_cg) {
  return new LateInlineStringCallGenerator(method, inline_cg);
}

class LateInlineBoxingCallGenerator : public LateInlineCallGenerator {

 public:
  LateInlineBoxingCallGenerator(ciMethod* method, CallGenerator* inline_cg) :
    LateInlineCallGenerator(method, inline_cg, /*is_pure=*/true) {}

  virtual JVMState* generate(JVMState* jvms) {
    Compile *C = Compile::current();

    C->log_inline_id(this);

    C->add_boxing_late_inline(this);

    JVMState* new_jvms = DirectCallGenerator::generate(jvms);
    return new_jvms;
  }

  virtual CallGenerator* with_call_node(CallNode* call) {
    LateInlineBoxingCallGenerator* cg = new LateInlineBoxingCallGenerator(method(), _inline_cg);
    cg->set_call_node(call->as_CallStaticJava());
    return cg;
  }
};

CallGenerator* CallGenerator::for_boxing_late_inline(ciMethod* method, CallGenerator* inline_cg) {
  return new LateInlineBoxingCallGenerator(method, inline_cg);
}

class LateInlineVectorReboxingCallGenerator : public LateInlineCallGenerator {

 public:
  LateInlineVectorReboxingCallGenerator(ciMethod* method, CallGenerator* inline_cg) :
    LateInlineCallGenerator(method, inline_cg, /*is_pure=*/true) {}

  virtual JVMState* generate(JVMState* jvms) {
    Compile *C = Compile::current();

    C->log_inline_id(this);

    C->add_vector_reboxing_late_inline(this);

    JVMState* new_jvms = DirectCallGenerator::generate(jvms);
    return new_jvms;
  }

  virtual CallGenerator* with_call_node(CallNode* call) {
    LateInlineVectorReboxingCallGenerator* cg = new LateInlineVectorReboxingCallGenerator(method(), _inline_cg);
    cg->set_call_node(call->as_CallStaticJava());
    return cg;
  }
};

//   static CallGenerator* for_vector_reboxing_late_inline(ciMethod* m, CallGenerator* inline_cg);
CallGenerator* CallGenerator::for_vector_reboxing_late_inline(ciMethod* method, CallGenerator* inline_cg) {
  return new LateInlineVectorReboxingCallGenerator(method, inline_cg);
}

class LateInlineScopedValueCallGenerator : public LateInlineCallGenerator {
  Node* _sv;
  bool _process_result;

public:
  LateInlineScopedValueCallGenerator(ciMethod* method, CallGenerator* inline_cg, bool process_result) :
          LateInlineCallGenerator(method, inline_cg), _sv(nullptr), _process_result(process_result) {}

  virtual JVMState* generate(JVMState* jvms) {
    Compile *C = Compile::current();

    C->log_inline_id(this);

    C->add_scoped_value_late_inline(this);

    JVMState* new_jvms = DirectCallGenerator::generate(jvms);
    return new_jvms;
  }

  virtual CallGenerator* with_call_node(CallNode* call) {
    LateInlineScopedValueCallGenerator* cg = new LateInlineScopedValueCallGenerator(method(), _inline_cg, false);
    cg->set_call_node(call->as_CallStaticJava());
    return cg;
  }

  void do_late_inline() {
    CallNode* call = call_node();
    _sv = call->in(TypeFunc::Parms);
    CallGenerator::do_late_inline_helper();
  }

  virtual void set_process_result(bool v) {
    _process_result = v;
  }

  virtual void process_result(GraphKit& kit) {
    if (!_process_result) {
      return;
    }
    // The call for ScopedValue.get() was just inlined. The code here pattern matches the resulting subgraph. To make it
    // easier:
    // - the slow path call to slowGet() is not inlined. If heuristics decided it should be, it was enqueued for late
    // inlining which will happen later.
    // - The call to Thread.scopedValueCache() is not inlined either.
    // The pattern matching here starts from the current control (end of inlining) and looks for the call for
    // Thread.scopedValueCache() which acts as a marker for the beginning of the subgraph of interest. In the process a
    // number of checks from the java code of ScopedValue.get() are expected to be encountered. They are recorded:
    assert(method()->intrinsic_id() == vmIntrinsics::_SVget, "");
    Compile* C = Compile::current();
    CallNode* scoped_value_cache = nullptr; // call to Thread.scopedValueCache()
    IfNode* get_cache_iff = nullptr; // test that scopedValueCache() is not null
    IfNode* get_first_iff = nullptr; // test for a hit in the cache with first hash
    IfNode* get_second_iff = nullptr; // test for a hit in the cache with second hash
    Node* first_index = nullptr; // index in the cache for first hash
    Node* second_index = nullptr; // index in the cache for second hash
    CallStaticJavaNode* slow_call = nullptr; // slowGet() call if any
    {
      ResourceMark rm;
      Unique_Node_List wq;
      wq.push(kit.control());
      for (uint i = 0; i < wq.size(); ++i) {
        Node* c = wq.at(i);
        if (c->is_Region()) {
          for (uint j = 1; j < c->req(); ++j) {
            Node* in = c->in(j);
            if (in != nullptr) {
              assert(!in->is_top(), "");
              wq.push(in);
            }
          }
        } else {
          if (c->Opcode() == Op_If) {
            Node* bol = c->in(1);
            assert(bol->is_Bool(), "");
            Node* cmp = bol->in(1);
            assert(cmp->Opcode() == Op_CmpP, "");
            Node* in1 = cmp->in(1);
            Node* in2 = cmp->in(2);
            if (in1->is_Proj() && in1->in(0)->is_Call() &&
                in1->in(0)->as_CallJava()->method()->intrinsic_id() == vmIntrinsics::_scopedValueCache) {
              assert(in2->bottom_type() == TypePtr::NULL_PTR, "");
              assert(get_cache_iff == nullptr, "");
              get_cache_iff = c->as_If();
              if (scoped_value_cache == nullptr) {
                scoped_value_cache = in1->in(0)->as_Call();
              } else {
                assert(scoped_value_cache == in1->in(0), "");
              }
              continue;
            } else if (in2->is_Proj() && in2->in(0)->is_Call() &&
                       in2->in(0)->as_CallJava()->method()->intrinsic_id() == vmIntrinsics::_scopedValueCache) {
              assert(in1->bottom_type() == TypePtr::NULL_PTR, "");
              assert(get_cache_iff == nullptr, "");
              get_cache_iff = c->as_If();
              if (scoped_value_cache == nullptr) {
                scoped_value_cache = in1->in(0)->as_Call();
              } else {
                assert(scoped_value_cache == in1->in(0), "");
              }
              continue;
            } else {
              Node* in;
              if (in1 == _sv) {
                in = in2;
              } else {
                assert(in2 = _sv, "");
                in = in1;
              }
              BarrierSetC2* bs = BarrierSet::barrier_set()->barrier_set_c2();
              in = bs->step_over_gc_barrier(in);
              if (in->Opcode() == Op_DecodeN) {
                in = in->in(1);
              }
              assert(in->Opcode() == Op_LoadP || in->Opcode() == Op_LoadN, "");
              assert(C->get_alias_index(in->adr_type()) == C->get_alias_index(TypeAryPtr::OOPS), "");
              Node* addp1 = in->in(MemNode::Address);
              assert(addp1->is_AddP(), "");
              assert(addp1->in(AddPNode::Base)->uncast()->is_Proj() &&
                     addp1->in(AddPNode::Base)->uncast()->in(0)->as_CallJava()->method()->intrinsic_id() ==
                     vmIntrinsics::_scopedValueCache, "");
              if (scoped_value_cache == nullptr) {
                scoped_value_cache = addp1->in(AddPNode::Base)->uncast()->in(0)->as_Call();
              } else {
                assert(scoped_value_cache == addp1->in(AddPNode::Base)->uncast()->in(0), "");
              }
              assert(in->in(MemNode::Memory)->is_Proj() && in->in(MemNode::Memory)->in(0) == scoped_value_cache, "");
              Node* addp2 = addp1->in(AddPNode::Address);
              Node* offset1 = addp1->in(AddPNode::Offset);
              intptr_t const_offset = offset1->find_intptr_t_con(-1);
              BasicType bt = TypeAryPtr::OOPS->array_element_basic_type();
              int shift = exact_log2(type2aelembytes(bt));
              int header = arrayOopDesc::base_offset_in_bytes(bt);
              assert(const_offset >= header, "");
              const_offset -= header;

              Node* index = kit.gvn().intcon(const_offset >> shift);
              if (addp2->is_AddP()) {
                assert(!addp2->in(AddPNode::Address)->is_AddP() &&
                       addp2->in(AddPNode::Base) == addp1->in(AddPNode::Base),
                       "");
                Node* offset2 = addp2->in(AddPNode::Offset);
                assert(offset2->Opcode() == Op_LShiftX && offset2->in(2)->find_int_con(-1) == shift, "");
                offset2 = offset2->in(1);
#ifdef _LP64
                assert(offset2->Opcode() == Op_ConvI2L, "");
                offset2 = offset2->in(1);
                if (offset2->Opcode() == Op_CastII && offset2->in(0)->is_Proj() &&
                    offset2->in(0)->in(0) == get_cache_iff) {
                  ShouldNotReachHere();
                  offset2 = offset2->in(1);
                }
#endif
                index = kit.gvn().transform(new AddINode(offset2, index));
              }

              if (get_first_iff == nullptr) {
                get_first_iff = c->as_If();
                first_index = index;
              } else {
                assert(get_second_iff == nullptr, "");
                get_second_iff = c->as_If();
                second_index = index;
              }
            }
          } else if (c->is_RangeCheck()) {
            // Kill the range checks as they are known to always succeed
            kit.gvn().hash_delete(c);
            c->set_req(1, kit.gvn().intcon(1));
            C->record_for_igvn(c);
          } else if (c->is_CallStaticJava()) {
            assert(slow_call == nullptr, "");
            slow_call = c->as_CallStaticJava();
            assert(slow_call->method()->intrinsic_id() == vmIntrinsics::_SVslowGet, "");
          } else {
            assert(c->is_Proj() || c->is_Catch(), "");
          }
          wq.push(c->in(0));
        }
      }
      // get_first_iff/get_second_iff contain the first/second check we ran into during the graph traversal but they may
      // not be the first/second one in execution order. Perform another traversal to figure out which is first.
      if (get_second_iff != nullptr) {
        Node_Stack stack(0);
        stack.push(get_cache_iff, 0);
        while (stack.is_nonempty()) {
          Node* c = stack.node();
          uint i = stack.index();
          if (i < c->outcnt()) {
            stack.set_index(i + 1);
            Node* u = c->raw_out(i);
            if (wq.member(u) && u != c) {
              if (u == get_first_iff) {
                break;
              } else if (u == get_second_iff) {
                swap(get_first_iff, get_second_iff);
                swap(first_index, second_index);
                break;
              }
              stack.push(u, 0);
            }
          } else {
            stack.pop();
          }
        }
      }
    }

    assert(get_cache_iff != nullptr, "");
    assert(get_second_iff == nullptr || get_first_iff != nullptr, "");

    if (get_first_iff != nullptr && get_second_iff != nullptr) {
      ProjNode* get_first_iff_failure = get_first_iff->proj_out(
              get_first_iff->in(1)->as_Bool()->_test._test == BoolTest::ne ? 0 : 1);
      CallStaticJavaNode* get_first_iff_unc = get_first_iff_failure->is_uncommon_trap_proj(Deoptimization::Reason_none);
      if (get_first_iff_unc != nullptr) {
        // first cache check never hits, keep only the second.
        swap(get_first_iff, get_second_iff);
        swap(first_index, second_index);
        get_second_iff = nullptr;
        second_index = nullptr;
      }
    }

    // Now transform the subgraph in a way that makes is amenable to optimizations

    // The path on exit of the method from parsing ends here
    Node* current_ctrl = kit.control();
    Node* frame = kit.gvn().transform(new ParmNode(C->start(), TypeFunc::FramePtr));
    Node* halt = kit.gvn().transform(new HaltNode(current_ctrl, frame, "Dead path for ScopedValueCall::get"));
    C->root()->add_req(halt);
    current_ctrl = nullptr;

    // Now move right above the scopedValueCache() call
    Node* mem = scoped_value_cache->in(TypeFunc::Memory);
    Node* c = scoped_value_cache->in(TypeFunc::Control);
    Node* io = scoped_value_cache->in(TypeFunc::I_O);

    kit.set_control(c);
    kit.set_all_memory(mem);
    kit.set_i_o(io);

    // remove the scopedValueCache() call
    CallProjections scoped_value_cache_projs = CallProjections();
    scoped_value_cache->extract_projections(&scoped_value_cache_projs, true);

    C->gvn_replace_by(scoped_value_cache_projs.fallthrough_memproj, mem);
    C->gvn_replace_by(scoped_value_cache_projs.fallthrough_ioproj, io);

    kit.gvn().hash_delete(scoped_value_cache);
    scoped_value_cache->set_req(0, C->top());
    C->record_for_igvn(scoped_value_cache);

    // replace it with its intrinsic code:
    Node* thread = kit.gvn().transform(new ThreadLocalNode());
    Node* p = kit.basic_plus_adr(C->top()/*!oop*/, thread, in_bytes(JavaThread::scopedValueCache_offset()));
    Node* cache_obj_handle = kit.make_load(nullptr, p, p->bottom_type()->is_ptr(), T_ADDRESS, MemNode::unordered);
    ciInstanceKlass* object_klass = ciEnv::current()->Object_klass();
    const TypeOopPtr* etype = TypeOopPtr::make_from_klass(object_klass);
    const TypeAry* arr0 = TypeAry::make(etype, TypeInt::POS);
    const TypeAryPtr* objects_type = TypeAryPtr::make(TypePtr::BotPTR, arr0, nullptr, true, 0);
    Node* scoped_value_cache_load = kit.access_load(cache_obj_handle, objects_type, T_OBJECT, IN_NATIVE);

    // A single ScopedValueGetHitsInCache node represents all checks that are needed to probe the cache (cache not null,
    // prob with first hash, prob with second hash)
    ScopedValueGetHitsInCacheNode* sv_hits_in_cache = new ScopedValueGetHitsInCacheNode(C, kit.control(),
                                                                                        scoped_value_cache_load,
                                                                                        kit.gvn().makecon(
                                                                                                TypePtr::NULL_PTR),
                                                                                        kit.memory(TypeAryPtr::OOPS),
                                                                                        _sv,
                                                                                        first_index == nullptr ? C->top() : first_index,
                                                                                        second_index == nullptr ? C->top() : second_index);

    // It will later be expanded back to all the checks so record profile data
    float get_cache_prob = get_cache_iff->_prob;
    BoolNode* get_cache_bool = get_cache_iff->in(1)->as_Bool();
    if (get_cache_prob != PROB_UNKNOWN && !get_cache_bool->_test.is_canonical()) {
      get_cache_prob = 1 - get_cache_prob;
    }
    sv_hits_in_cache->set_profile_data(0, get_cache_iff->_fcnt, get_cache_prob);
    float get_first_prob = 0;
    if (get_first_iff != nullptr) {
      get_first_prob = get_first_iff->_prob;
      if (get_first_prob != PROB_UNKNOWN && !get_first_iff->in(1)->as_Bool()->_test.is_canonical()) {
        get_first_prob = 1 - get_first_prob;
      }
      sv_hits_in_cache->set_profile_data(1, get_first_iff->_fcnt, get_first_prob);
    } else {
      sv_hits_in_cache->set_profile_data(1, 0, 0);
    }
    float get_second_prob = 0;
    if (get_second_iff != nullptr) {
      get_second_prob = get_second_iff->_prob;
      if (get_second_prob != PROB_UNKNOWN && !get_second_iff->in(1)->as_Bool()->_test.is_canonical()) {
        get_second_prob = 1 - get_second_prob;
      }
      sv_hits_in_cache->set_profile_data(2, get_second_iff->_fcnt, get_second_prob);
    } else {
      sv_hits_in_cache->set_profile_data(2, 0, 0);
    }
    Node* sv_hits_in_cachex = kit.gvn().transform(sv_hits_in_cache);
    assert(sv_hits_in_cachex == sv_hits_in_cache, "");

    // And compute the probability of a miss in the cache
    float prob;
    // get_cache_prob: probability that cache array is not null
    // get_first_prob: probability of a miss
    // get_second_prob: probability of a miss
    if (get_cache_prob == PROB_UNKNOWN || get_first_prob == PROB_UNKNOWN || get_second_prob == PROB_UNKNOWN) {
      prob = PROB_UNKNOWN;
    } else {
      prob = (1 - get_cache_prob) + get_cache_prob * (get_first_prob + (1 - get_first_prob) * get_second_prob);
    }

    // Add the control flow that checks whether ScopedValueGetHitsInCache succeeds
    Node* bol = kit.gvn().transform(new BoolNode(sv_hits_in_cache, BoolTest::ne));
    IfNode* iff = new IfNode(kit.control(), bol, 1 - prob, get_cache_iff->_fcnt);
    Node* transformed_iff = kit.gvn().transform(iff);
    assert(transformed_iff == iff, "");
    Node* not_in_cache = kit.gvn().transform(new IfFalseNode(iff));
    Node* in_cache = kit.gvn().transform(new IfTrueNode(iff));

    // Merge the paths that produce the result (in case there's a slow path)
    Node* r = new RegionNode(3);
    Node* phi_cache_value = new PhiNode(r, TypeInstPtr::BOTTOM);
    Node* phi_mem = new PhiNode(r, Type::MEMORY, TypePtr::BOTTOM);
    Node* phi_io = new PhiNode(r, Type::ABIO);

    C->gvn_replace_by(scoped_value_cache_projs.fallthrough_catchproj, not_in_cache);
    C->gvn_replace_by(scoped_value_cache_projs.resproj, scoped_value_cache_load);

    if (slow_call == nullptr) {
      r->init_req(1, C->top());
      phi_cache_value->init_req(1, C->top());
      phi_mem->init_req(1, C->top());
      phi_io->init_req(1, C->top());
    } else {
      CallProjections slow_projs;
      slow_call->extract_projections(&slow_projs, false);
      Node* fallthrough = slow_projs.fallthrough_catchproj->clone();
      kit.gvn().set_type(fallthrough, fallthrough->bottom_type());
      r->init_req(1, fallthrough);
      C->gvn_replace_by(slow_projs.fallthrough_catchproj, C->top());
      phi_mem->init_req(1, slow_projs.fallthrough_memproj);
      phi_io->init_req(1, slow_projs.fallthrough_ioproj);
      phi_cache_value->init_req(1, slow_projs.resproj);
    }
    r->init_req(2, in_cache);

    // ScopedValueGetLoadFromCache is a single that represents the result of a hit in the cache
    Node* cache_value = kit.gvn().transform(new ScopedValueGetLoadFromCacheNode(C, in_cache, sv_hits_in_cache));
    phi_cache_value->init_req(2, cache_value);
    phi_mem->init_req(2, kit.reset_memory());
    phi_io->init_req(2, kit.i_o());
    kit.set_all_memory(kit.gvn().transform(phi_mem));
    kit.set_i_o(kit.gvn().transform(phi_io));
    kit.set_control(kit.gvn().transform(r));
    C->record_for_igvn(r);
    kit.pop();
    kit.push(phi_cache_value);
    // Before the transformation of the subgraph we had (some branch may not be present depending on profile data),
    // in pseudo code:
    //
    // if (cache == null) {
    //   goto slow_call;
    // }
    // if (first_entry_hits) {
    //   result = first_entry;
    // } else {
    //   if (second_entry_hits) {
    //     result = second_entry;
    //   } else {
    //     goto slow_call;
    //   }
    // }
    // continue:
    //
    // slow_call:
    // result = slowGet();
    // goto continue;
    //
    // After transformation:
    // if (hits_in_the_cache) {
    //   result = load_from_cache;
    // } else {
    //   if (cache == null) {
    //     goto slow_call;
    //   }
    //   if (first_entry_hits) {
    //     halt;
    //   } else {
    //     if (second_entry_hits) {
    //        halt;
    //      } else {
    //        goto slow_call;
    //     }
    //   }
    // }
    // continue:
    //
    // slow_call:
    // result = slowGet();
    // goto continue;
    //
    // the transformed graph includes 2 copies of the cache probing logic. One represented by the
    // ScopedValueGetHitsInCache/ScopedValueGetLoadFromCache pair that is amenable to optimizations. The other from
    // the result of the parsing of the java code where the success path ends with an Halt node. The reason for that is
    // that some paths may end with an uncommon trap and if one traps, we want the trap to be recorded for the right bci.
    // When the ScopedValueGetHitsInCache/ScopedValueGetLoadFromCache pair is expanded, split if finds the duplicate
    // logic and cleans it up.
  }
};

CallGenerator* CallGenerator::for_scoped_value_late_inline(ciMethod* method, CallGenerator* inline_cg,
                                                           bool process_result) {
  return new LateInlineScopedValueCallGenerator(method, inline_cg, process_result);
}

//------------------------PredictedCallGenerator------------------------------
// Internal class which handles all out-of-line calls checking receiver type.
class PredictedCallGenerator : public CallGenerator {
  ciKlass*       _predicted_receiver;
  CallGenerator* _if_missed;
  CallGenerator* _if_hit;
  float          _hit_prob;
  bool           _exact_check;

public:
  PredictedCallGenerator(ciKlass* predicted_receiver,
                         CallGenerator* if_missed,
                         CallGenerator* if_hit, bool exact_check,
                         float hit_prob)
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
    _exact_check        = exact_check;
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
  return new PredictedCallGenerator(predicted_receiver, if_missed, if_hit,
                                    /*exact_check=*/true, hit_prob);
}

CallGenerator* CallGenerator::for_guarded_call(ciKlass* guarded_receiver,
                                               CallGenerator* if_missed,
                                               CallGenerator* if_hit) {
  return new PredictedCallGenerator(guarded_receiver, if_missed, if_hit,
                                    /*exact_check=*/false, PROB_ALWAYS);
}

JVMState* PredictedCallGenerator::generate(JVMState* jvms) {
  GraphKit kit(jvms);
  kit.C->print_inlining_update(this);
  PhaseGVN& gvn = kit.gvn();
  // We need an explicit receiver null_check before checking its type.
  // We share a map with the caller, so his JVMS gets adjusted.
  Node* receiver = kit.argument(0);
  CompileLog* log = kit.C->log();
  if (log != nullptr) {
    log->elem("predicted_call bci='%d' exact='%d' klass='%d'",
              jvms->bci(), (_exact_check ? 1 : 0), log->identify(_predicted_receiver));
  }

  receiver = kit.null_check_receiver_before_call(method());
  if (kit.stopped()) {
    return kit.transfer_exceptions_into_jvms();
  }

  // Make a copy of the replaced nodes in case we need to restore them
  ReplacedNodes replaced_nodes = kit.map()->replaced_nodes();
  replaced_nodes.clone();

  Node* casted_receiver = receiver;  // will get updated in place...
  Node* slow_ctl = nullptr;
  if (_exact_check) {
    slow_ctl = kit.type_check_receiver(receiver, _predicted_receiver, _hit_prob,
                                       &casted_receiver);
  } else {
    slow_ctl = kit.subtype_check_receiver(receiver, _predicted_receiver,
                                          &casted_receiver);
  }

  SafePointNode* slow_map = nullptr;
  JVMState* slow_jvms = nullptr;
  { PreserveJVMState pjvms(&kit);
    kit.set_control(slow_ctl);
    if (!kit.stopped()) {
      slow_jvms = _if_missed->generate(kit.sync_jvms());
      if (kit.failing())
        return nullptr;  // might happen because of NodeCountInliningCutoff
      assert(slow_jvms != nullptr, "must be");
      kit.add_exception_states_from(slow_jvms);
      kit.set_map(slow_jvms->map());
      if (!kit.stopped())
        slow_map = kit.stop();
    }
  }

  if (kit.stopped()) {
    // Instance does not match the predicted type.
    kit.set_jvms(slow_jvms);
    return kit.transfer_exceptions_into_jvms();
  }

  // Fall through if the instance matches the desired type.
  kit.replace_in_map(receiver, casted_receiver);

  // Make the hot call:
  JVMState* new_jvms = _if_hit->generate(kit.sync_jvms());
  if (new_jvms == nullptr) {
    // Inline failed, so make a direct call.
    assert(_if_hit->is_inline(), "must have been a failed inline");
    CallGenerator* cg = CallGenerator::for_direct_call(_if_hit->method());
    new_jvms = cg->generate(kit.sync_jvms());
  }
  kit.add_exception_states_from(new_jvms);
  kit.set_jvms(new_jvms);

  // Need to merge slow and fast?
  if (slow_map == nullptr) {
    // The fast path is the only path remaining.
    return kit.transfer_exceptions_into_jvms();
  }

  if (kit.stopped()) {
    // Inlined method threw an exception, so it's just the slow path after all.
    kit.set_jvms(slow_jvms);
    return kit.transfer_exceptions_into_jvms();
  }

  // There are 2 branches and the replaced nodes are only valid on
  // one: restore the replaced nodes to what they were before the
  // branch.
  kit.map()->set_replaced_nodes(replaced_nodes);

  // Finish the diamond.
  kit.C->set_has_split_ifs(true); // Has chance for split-if optimization
  RegionNode* region = new RegionNode(3);
  region->init_req(1, kit.control());
  region->init_req(2, slow_map->control());
  kit.set_control(gvn.transform(region));
  Node* iophi = PhiNode::make(region, kit.i_o(), Type::ABIO);
  iophi->set_req(2, slow_map->i_o());
  kit.set_i_o(gvn.transform(iophi));
  // Merge memory
  kit.merge_memory(slow_map->merged_memory(), region, 2);
  // Transform new memory Phis.
  for (MergeMemStream mms(kit.merged_memory()); mms.next_non_empty();) {
    Node* phi = mms.memory();
    if (phi->is_Phi() && phi->in(0) == region) {
      mms.set_memory(gvn.transform(phi));
    }
  }
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
      const Type* t = gvn.type(m)->meet_speculative(gvn.type(n));
      Node* phi = PhiNode::make(region, m, t);
      phi->set_req(2, n);
      kit.map()->set_req(i, gvn.transform(phi));
    }
  }
  return kit.transfer_exceptions_into_jvms();
}


CallGenerator* CallGenerator::for_method_handle_call(JVMState* jvms, ciMethod* caller, ciMethod* callee, bool allow_inline) {
  assert(callee->is_method_handle_intrinsic(), "for_method_handle_call mismatch");
  bool input_not_const;
  CallGenerator* cg = CallGenerator::for_method_handle_inline(jvms, caller, callee, allow_inline, input_not_const);
  Compile* C = Compile::current();
  bool should_delay = C->should_delay_inlining();
  if (cg != nullptr) {
    if (should_delay) {
      return CallGenerator::for_late_inline(callee, cg);
    } else {
      return cg;
    }
  }
  int bci = jvms->bci();
  ciCallProfile profile = caller->call_profile_at_bci(bci);
  int call_site_count = caller->scale_count(profile.count());

  if (IncrementalInlineMH && call_site_count > 0 &&
      (should_delay || input_not_const || !C->inlining_incrementally() || C->over_inlining_cutoff())) {
    return CallGenerator::for_mh_late_inline(caller, callee, input_not_const);
  } else {
    // Out-of-line call.
    return CallGenerator::for_direct_call(callee);
  }
}

CallGenerator* CallGenerator::for_method_handle_inline(JVMState* jvms, ciMethod* caller, ciMethod* callee, bool allow_inline, bool& input_not_const) {
  GraphKit kit(jvms);
  PhaseGVN& gvn = kit.gvn();
  Compile* C = kit.C;
  vmIntrinsics::ID iid = callee->intrinsic_id();
  input_not_const = true;
  if (StressMethodHandleLinkerInlining) {
    allow_inline = false;
  }
  switch (iid) {
  case vmIntrinsics::_invokeBasic:
    {
      // Get MethodHandle receiver:
      Node* receiver = kit.argument(0);
      if (receiver->Opcode() == Op_ConP) {
        input_not_const = false;
        const TypeOopPtr* recv_toop = receiver->bottom_type()->isa_oopptr();
        if (recv_toop != nullptr) {
          ciMethod* target = recv_toop->const_oop()->as_method_handle()->get_vmtarget();
          const int vtable_index = Method::invalid_vtable_index;

          if (!ciMethod::is_consistent_info(callee, target)) {
            print_inlining_failure(C, callee, jvms->depth() - 1, jvms->bci(),
                                   "signatures mismatch");
            return nullptr;
          }

          CallGenerator *cg = C->call_generator(target, vtable_index,
                                                false /* call_does_dispatch */,
                                                jvms,
                                                allow_inline,
                                                PROB_ALWAYS);
          return cg;
        } else {
          assert(receiver->bottom_type() == TypePtr::NULL_PTR, "not a null: %s",
                 Type::str(receiver->bottom_type()));
          print_inlining_failure(C, callee, jvms->depth() - 1, jvms->bci(),
                                 "receiver is always null");
        }
      } else {
        print_inlining_failure(C, callee, jvms->depth() - 1, jvms->bci(),
                               "receiver not constant");
      }
    }
    break;

  case vmIntrinsics::_linkToVirtual:
  case vmIntrinsics::_linkToStatic:
  case vmIntrinsics::_linkToSpecial:
  case vmIntrinsics::_linkToInterface:
    {
      // Get MemberName argument:
      Node* member_name = kit.argument(callee->arg_size() - 1);
      if (member_name->Opcode() == Op_ConP) {
        input_not_const = false;
        const TypeOopPtr* oop_ptr = member_name->bottom_type()->is_oopptr();
        ciMethod* target = oop_ptr->const_oop()->as_member_name()->get_vmtarget();

        if (!ciMethod::is_consistent_info(callee, target)) {
          print_inlining_failure(C, callee, jvms->depth() - 1, jvms->bci(),
                                 "signatures mismatch");
          return nullptr;
        }

        // In lambda forms we erase signature types to avoid resolving issues
        // involving class loaders.  When we optimize a method handle invoke
        // to a direct call we must cast the receiver and arguments to its
        // actual types.
        ciSignature* signature = target->signature();
        const int receiver_skip = target->is_static() ? 0 : 1;
        // Cast receiver to its type.
        if (!target->is_static()) {
          Node* arg = kit.argument(0);
          const TypeOopPtr* arg_type = arg->bottom_type()->isa_oopptr();
          const Type*       sig_type = TypeOopPtr::make_from_klass(signature->accessing_klass());
          if (arg_type != nullptr && !arg_type->higher_equal(sig_type)) {
            const Type* recv_type = arg_type->filter_speculative(sig_type); // keep speculative part
            Node* cast_obj = gvn.transform(new CheckCastPPNode(kit.control(), arg, recv_type));
            kit.set_argument(0, cast_obj);
          }
        }
        // Cast reference arguments to its type.
        for (int i = 0, j = 0; i < signature->count(); i++) {
          ciType* t = signature->type_at(i);
          if (t->is_klass()) {
            Node* arg = kit.argument(receiver_skip + j);
            const TypeOopPtr* arg_type = arg->bottom_type()->isa_oopptr();
            const Type*       sig_type = TypeOopPtr::make_from_klass(t->as_klass());
            if (arg_type != nullptr && !arg_type->higher_equal(sig_type)) {
              const Type* narrowed_arg_type = arg_type->filter_speculative(sig_type); // keep speculative part
              Node* cast_obj = gvn.transform(new CheckCastPPNode(kit.control(), arg, narrowed_arg_type));
              kit.set_argument(receiver_skip + j, cast_obj);
            }
          }
          j += t->size();  // long and double take two slots
        }

        // Try to get the most accurate receiver type
        const bool is_virtual              = (iid == vmIntrinsics::_linkToVirtual);
        const bool is_virtual_or_interface = (is_virtual || iid == vmIntrinsics::_linkToInterface);
        int  vtable_index       = Method::invalid_vtable_index;
        bool call_does_dispatch = false;

        ciKlass* speculative_receiver_type = nullptr;
        if (is_virtual_or_interface) {
          ciInstanceKlass* klass = target->holder();
          Node*             receiver_node = kit.argument(0);
          const TypeOopPtr* receiver_type = gvn.type(receiver_node)->isa_oopptr();
          // call_does_dispatch and vtable_index are out-parameters.  They might be changed.
          // optimize_virtual_call() takes 2 different holder
          // arguments for a corner case that doesn't apply here (see
          // Parse::do_call())
          target = C->optimize_virtual_call(caller, klass, klass,
                                            target, receiver_type, is_virtual,
                                            call_does_dispatch, vtable_index, // out-parameters
                                            false /* check_access */);
          // We lack profiling at this call but type speculation may
          // provide us with a type
          speculative_receiver_type = (receiver_type != nullptr) ? receiver_type->speculative_type() : nullptr;
        }
        CallGenerator* cg = C->call_generator(target, vtable_index, call_does_dispatch, jvms,
                                              allow_inline,
                                              PROB_ALWAYS,
                                              speculative_receiver_type);
        return cg;
      } else {
        print_inlining_failure(C, callee, jvms->depth() - 1, jvms->bci(),
                               "member_name not constant");
      }
    }
    break;

    case vmIntrinsics::_linkToNative:
    print_inlining_failure(C, callee, jvms->depth() - 1, jvms->bci(),
                           "native call");
    break;

  default:
    fatal("unexpected intrinsic %d: %s", vmIntrinsics::as_int(iid), vmIntrinsics::name_at(iid));
    break;
  }
  return nullptr;
}

//------------------------PredicatedIntrinsicGenerator------------------------------
// Internal class which handles all predicated Intrinsic calls.
class PredicatedIntrinsicGenerator : public CallGenerator {
  CallGenerator* _intrinsic;
  CallGenerator* _cg;

public:
  PredicatedIntrinsicGenerator(CallGenerator* intrinsic,
                               CallGenerator* cg)
    : CallGenerator(cg->method())
  {
    _intrinsic = intrinsic;
    _cg        = cg;
  }

  virtual bool      is_virtual()   const    { return true; }
  virtual bool      is_inline()    const    { return true; }
  virtual bool      is_intrinsic() const    { return true; }

  virtual JVMState* generate(JVMState* jvms);
};


CallGenerator* CallGenerator::for_predicated_intrinsic(CallGenerator* intrinsic,
                                                       CallGenerator* cg) {
  return new PredicatedIntrinsicGenerator(intrinsic, cg);
}


JVMState* PredicatedIntrinsicGenerator::generate(JVMState* jvms) {
  // The code we want to generate here is:
  //    if (receiver == nullptr)
  //        uncommon_Trap
  //    if (predicate(0))
  //        do_intrinsic(0)
  //    else
  //    if (predicate(1))
  //        do_intrinsic(1)
  //    ...
  //    else
  //        do_java_comp

  GraphKit kit(jvms);
  PhaseGVN& gvn = kit.gvn();

  CompileLog* log = kit.C->log();
  if (log != nullptr) {
    log->elem("predicated_intrinsic bci='%d' method='%d'",
              jvms->bci(), log->identify(method()));
  }

  if (!method()->is_static()) {
    // We need an explicit receiver null_check before checking its type in predicate.
    // We share a map with the caller, so his JVMS gets adjusted.
    Node* receiver = kit.null_check_receiver_before_call(method());
    if (kit.stopped()) {
      return kit.transfer_exceptions_into_jvms();
    }
  }

  int n_predicates = _intrinsic->predicates_count();
  assert(n_predicates > 0, "sanity");

  JVMState** result_jvms = NEW_RESOURCE_ARRAY(JVMState*, (n_predicates+1));

  // Region for normal compilation code if intrinsic failed.
  Node* slow_region = new RegionNode(1);

  int results = 0;
  for (int predicate = 0; (predicate < n_predicates) && !kit.stopped(); predicate++) {
#ifdef ASSERT
    JVMState* old_jvms = kit.jvms();
    SafePointNode* old_map = kit.map();
    Node* old_io  = old_map->i_o();
    Node* old_mem = old_map->memory();
    Node* old_exc = old_map->next_exception();
#endif
    Node* else_ctrl = _intrinsic->generate_predicate(kit.sync_jvms(), predicate);
#ifdef ASSERT
    // Assert(no_new_memory && no_new_io && no_new_exceptions) after generate_predicate.
    assert(old_jvms == kit.jvms(), "generate_predicate should not change jvm state");
    SafePointNode* new_map = kit.map();
    assert(old_io  == new_map->i_o(), "generate_predicate should not change i_o");
    assert(old_mem == new_map->memory(), "generate_predicate should not change memory");
    assert(old_exc == new_map->next_exception(), "generate_predicate should not add exceptions");
#endif
    if (!kit.stopped()) {
      PreserveJVMState pjvms(&kit);
      // Generate intrinsic code:
      JVMState* new_jvms = _intrinsic->generate(kit.sync_jvms());
      if (new_jvms == nullptr) {
        // Intrinsic failed, use normal compilation path for this predicate.
        slow_region->add_req(kit.control());
      } else {
        kit.add_exception_states_from(new_jvms);
        kit.set_jvms(new_jvms);
        if (!kit.stopped()) {
          result_jvms[results++] = kit.jvms();
        }
      }
    }
    if (else_ctrl == nullptr) {
      else_ctrl = kit.C->top();
    }
    kit.set_control(else_ctrl);
  }
  if (!kit.stopped()) {
    // Final 'else' after predicates.
    slow_region->add_req(kit.control());
  }
  if (slow_region->req() > 1) {
    PreserveJVMState pjvms(&kit);
    // Generate normal compilation code:
    kit.set_control(gvn.transform(slow_region));
    JVMState* new_jvms = _cg->generate(kit.sync_jvms());
    if (kit.failing())
      return nullptr;  // might happen because of NodeCountInliningCutoff
    assert(new_jvms != nullptr, "must be");
    kit.add_exception_states_from(new_jvms);
    kit.set_jvms(new_jvms);
    if (!kit.stopped()) {
      result_jvms[results++] = kit.jvms();
    }
  }

  if (results == 0) {
    // All paths ended in uncommon traps.
    (void) kit.stop();
    return kit.transfer_exceptions_into_jvms();
  }

  if (results == 1) { // Only one path
    kit.set_jvms(result_jvms[0]);
    return kit.transfer_exceptions_into_jvms();
  }

  // Merge all paths.
  kit.C->set_has_split_ifs(true); // Has chance for split-if optimization
  RegionNode* region = new RegionNode(results + 1);
  Node* iophi = PhiNode::make(region, kit.i_o(), Type::ABIO);
  for (int i = 0; i < results; i++) {
    JVMState* jvms = result_jvms[i];
    int path = i + 1;
    SafePointNode* map = jvms->map();
    region->init_req(path, map->control());
    iophi->set_req(path, map->i_o());
    if (i == 0) {
      kit.set_jvms(jvms);
    } else {
      kit.merge_memory(map->merged_memory(), region, path);
    }
  }
  kit.set_control(gvn.transform(region));
  kit.set_i_o(gvn.transform(iophi));
  // Transform new memory Phis.
  for (MergeMemStream mms(kit.merged_memory()); mms.next_non_empty();) {
    Node* phi = mms.memory();
    if (phi->is_Phi() && phi->in(0) == region) {
      mms.set_memory(gvn.transform(phi));
    }
  }

  // Merge debug info.
  Node** ins = NEW_RESOURCE_ARRAY(Node*, results);
  uint tos = kit.jvms()->stkoff() + kit.sp();
  Node* map = kit.map();
  uint limit = map->req();
  for (uint i = TypeFunc::Parms; i < limit; i++) {
    // Skip unused stack slots; fast forward to monoff();
    if (i == tos) {
      i = kit.jvms()->monoff();
      if( i >= limit ) break;
    }
    Node* n = map->in(i);
    ins[0] = n;
    const Type* t = gvn.type(n);
    bool needs_phi = false;
    for (int j = 1; j < results; j++) {
      JVMState* jvms = result_jvms[j];
      Node* jmap = jvms->map();
      Node* m = nullptr;
      if (jmap->req() > i) {
        m = jmap->in(i);
        if (m != n) {
          needs_phi = true;
          t = t->meet_speculative(gvn.type(m));
        }
      }
      ins[j] = m;
    }
    if (needs_phi) {
      Node* phi = PhiNode::make(region, n, t);
      for (int j = 1; j < results; j++) {
        phi->set_req(j + 1, ins[j]);
      }
      map->set_req(i, gvn.transform(phi));
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
  kit.C->print_inlining_update(this);
  // Take the trap with arguments pushed on the stack.  (Cf. null_check_receiver).
  // Callsite signature can be different from actual method being called (i.e _linkTo* sites).
  // Use callsite signature always.
  ciMethod* declared_method = kit.method()->get_method_at_bci(kit.bci());
  int nargs = declared_method->arg_size();
  kit.inc_sp(nargs);
  assert(nargs <= kit.sp() && kit.sp() <= jvms->stk_size(), "sane sp w/ args pushed");
  if (_reason == Deoptimization::Reason_class_check &&
      _action == Deoptimization::Action_maybe_recompile) {
    // Temp fix for 6529811
    // Don't allow uncommon_trap to override our decision to recompile in the event
    // of a class cast failure for a monomorphic call as it will never let us convert
    // the call to either bi-morphic or megamorphic and can lead to unc-trap loops
    bool keep_exact_action = true;
    kit.uncommon_trap(_reason, _action, nullptr, "monomorphic vcall checkcast", false, keep_exact_action);
  } else {
    kit.uncommon_trap(_reason, _action);
  }
  return kit.transfer_exceptions_into_jvms();
}

// (Note:  Moved hook_up_call to GraphKit::set_edges_for_java_call.)

// (Node:  Merged hook_up_exits into ParseGenerator::generate.)
