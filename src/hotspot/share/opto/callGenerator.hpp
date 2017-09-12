/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_OPTO_CALLGENERATOR_HPP
#define SHARE_VM_OPTO_CALLGENERATOR_HPP

#include "compiler/compileBroker.hpp"
#include "opto/callnode.hpp"
#include "opto/compile.hpp"
#include "opto/type.hpp"
#include "runtime/deoptimization.hpp"

//---------------------------CallGenerator-------------------------------------
// The subclasses of this class handle generation of ideal nodes for
// call sites and method entry points.

class CallGenerator : public ResourceObj {
 public:
  enum {
    xxxunusedxxx
  };

 private:
  ciMethod*             _method;                // The method being called.

 protected:
  CallGenerator(ciMethod* method) : _method(method) {}

 public:
  // Accessors
  ciMethod*          method() const             { return _method; }

  // is_inline: At least some code implementing the method is copied here.
  virtual bool      is_inline() const           { return false; }
  // is_intrinsic: There's a method-specific way of generating the inline code.
  virtual bool      is_intrinsic() const        { return false; }
  // is_parse: Bytecodes implementing the specific method are copied here.
  virtual bool      is_parse() const            { return false; }
  // is_virtual: The call uses the receiver type to select or check the method.
  virtual bool      is_virtual() const          { return false; }
  // is_deferred: The decision whether to inline or not is deferred.
  virtual bool      is_deferred() const         { return false; }
  // is_predicated: Uses an explicit check (predicate).
  virtual bool      is_predicated() const       { return false; }
  virtual int       predicates_count() const    { return 0; }
  // is_trap: Does not return to the caller.  (E.g., uncommon trap.)
  virtual bool      is_trap() const             { return false; }
  // does_virtual_dispatch: Should try inlining as normal method first.
  virtual bool      does_virtual_dispatch() const     { return false; }

  // is_late_inline: supports conversion of call into an inline
  virtual bool      is_late_inline() const      { return false; }
  // same but for method handle calls
  virtual bool      is_mh_late_inline() const   { return false; }
  virtual bool      is_string_late_inline() const{ return false; }

  // for method handle calls: have we tried inlinining the call already?
  virtual bool      already_attempted() const   { ShouldNotReachHere(); return false; }

  // Replace the call with an inline version of the code
  virtual void do_late_inline() { ShouldNotReachHere(); }

  virtual CallStaticJavaNode* call_node() const { ShouldNotReachHere(); return NULL; }

  virtual void set_unique_id(jlong id)          { fatal("unique id only for late inlines"); };
  virtual jlong unique_id() const               { fatal("unique id only for late inlines"); return 0; };

  // Note:  It is possible for a CG to be both inline and virtual.
  // (The hashCode intrinsic does a vtable check and an inlined fast path.)

  // Utilities:
  const TypeFunc*   tf() const;

  // The given jvms has state and arguments for a call to my method.
  // Edges after jvms->argoff() carry all (pre-popped) argument values.
  //
  // Update the map with state and return values (if any) and return it.
  // The return values (0, 1, or 2) must be pushed on the map's stack,
  // and the sp of the jvms incremented accordingly.
  //
  // The jvms is returned on success.  Alternatively, a copy of the
  // given jvms, suitably updated, may be returned, in which case the
  // caller should discard the original jvms.
  //
  // The non-Parm edges of the returned map will contain updated global state,
  // and one or two edges before jvms->sp() will carry any return values.
  // Other map edges may contain locals or monitors, and should not
  // be changed in meaning.
  //
  // If the call traps, the returned map must have a control edge of top.
  // If the call can throw, the returned map must report has_exceptions().
  //
  // If the result is NULL, it means that this CallGenerator was unable
  // to handle the given call, and another CallGenerator should be consulted.
  virtual JVMState* generate(JVMState* jvms) = 0;

  // How to generate a call site that is inlined:
  static CallGenerator* for_inline(ciMethod* m, float expected_uses = -1);
  // How to generate code for an on-stack replacement handler.
  static CallGenerator* for_osr(ciMethod* m, int osr_bci);

  // How to generate vanilla out-of-line call sites:
  static CallGenerator* for_direct_call(ciMethod* m, bool separate_io_projs = false);   // static, special
  static CallGenerator* for_virtual_call(ciMethod* m, int vtable_index);  // virtual, interface

  static CallGenerator* for_method_handle_call(  JVMState* jvms, ciMethod* caller, ciMethod* callee, bool delayed_forbidden);
  static CallGenerator* for_method_handle_inline(JVMState* jvms, ciMethod* caller, ciMethod* callee, bool& input_not_const);

  // How to generate a replace a direct call with an inline version
  static CallGenerator* for_late_inline(ciMethod* m, CallGenerator* inline_cg);
  static CallGenerator* for_mh_late_inline(ciMethod* caller, ciMethod* callee, bool input_not_const);
  static CallGenerator* for_string_late_inline(ciMethod* m, CallGenerator* inline_cg);
  static CallGenerator* for_boxing_late_inline(ciMethod* m, CallGenerator* inline_cg);

  // How to make a call but defer the decision whether to inline or not.
  static CallGenerator* for_warm_call(WarmCallInfo* ci,
                                      CallGenerator* if_cold,
                                      CallGenerator* if_hot);

  // How to make a call that optimistically assumes a receiver type:
  static CallGenerator* for_predicted_call(ciKlass* predicted_receiver,
                                           CallGenerator* if_missed,
                                           CallGenerator* if_hit,
                                           float hit_prob);

  // How to make a call that optimistically assumes a MethodHandle target:
  static CallGenerator* for_predicted_dynamic_call(ciMethodHandle* predicted_method_handle,
                                                   CallGenerator* if_missed,
                                                   CallGenerator* if_hit,
                                                   float hit_prob);

  // How to make a call that gives up and goes back to the interpreter:
  static CallGenerator* for_uncommon_trap(ciMethod* m,
                                          Deoptimization::DeoptReason reason,
                                          Deoptimization::DeoptAction action);

  // Registry for intrinsics:
  static CallGenerator* for_intrinsic(ciMethod* m);
  static void register_intrinsic(ciMethod* m, CallGenerator* cg);
  static CallGenerator* for_predicated_intrinsic(CallGenerator* intrinsic,
                                                 CallGenerator* cg);
  virtual Node* generate_predicate(JVMState* jvms, int predicate) { return NULL; };

  virtual void print_inlining_late(const char* msg) { ShouldNotReachHere(); }

  static void print_inlining(Compile* C, ciMethod* callee, int inline_level, int bci, const char* msg) {
    if (C->print_inlining()) {
      C->print_inlining(callee, inline_level, bci, msg);
    }
  }

  static void print_inlining_failure(Compile* C, ciMethod* callee, int inline_level, int bci, const char* msg) {
    print_inlining(C, callee, inline_level, bci, msg);
    C->log_inline_failure(msg);
  }

  static bool is_inlined_method_handle_intrinsic(JVMState* jvms, ciMethod* m);
};


//------------------------InlineCallGenerator----------------------------------
class InlineCallGenerator : public CallGenerator {
 protected:
  InlineCallGenerator(ciMethod* method) : CallGenerator(method) {}

 public:
  virtual bool      is_inline() const           { return true; }
};


//---------------------------WarmCallInfo--------------------------------------
// A struct to collect information about a given call site.
// Helps sort call sites into "hot", "medium", and "cold".
// Participates in the queueing of "medium" call sites for possible inlining.
class WarmCallInfo : public ResourceObj {
 private:

  CallNode*     _call;   // The CallNode which may be inlined.
  CallGenerator* _hot_cg;// CG for expanding the call node

  // These are the metrics we use to evaluate call sites:

  float         _count;  // How often do we expect to reach this site?
  float         _profit; // How much time do we expect to save by inlining?
  float         _work;   // How long do we expect the average call to take?
  float         _size;   // How big do we expect the inlined code to be?

  float         _heat;   // Combined score inducing total order on call sites.
  WarmCallInfo* _next;   // Next cooler call info in pending queue.

  // Count is the number of times this call site is expected to be executed.
  // Large count is favorable for inlining, because the extra compilation
  // work will be amortized more completely.

  // Profit is a rough measure of the amount of time we expect to save
  // per execution of this site if we inline it.  (1.0 == call overhead)
  // Large profit favors inlining.  Negative profit disables inlining.

  // Work is a rough measure of the amount of time a typical out-of-line
  // call from this site is expected to take.  (1.0 == call, no-op, return)
  // Small work is somewhat favorable for inlining, since methods with
  // short "hot" traces are more likely to inline smoothly.

  // Size is the number of graph nodes we expect this method to produce,
  // not counting the inlining of any further warm calls it may include.
  // Small size favors inlining, since small methods are more likely to
  // inline smoothly.  The size is estimated by examining the native code
  // if available.  The method bytecodes are also examined, assuming
  // empirically observed node counts for each kind of bytecode.

  // Heat is the combined "goodness" of a site's inlining.  If we were
  // omniscient, it would be the difference of two sums of future execution
  // times of code emitted for this site (amortized across multiple sites if
  // sharing applies).  The two sums are for versions of this call site with
  // and without inlining.

  // We approximate this mythical quantity by playing with averages,
  // rough estimates, and assumptions that history repeats itself.
  // The basic formula count * profit is heuristically adjusted
  // by looking at the expected compilation and execution times of
  // of the inlined call.

  // Note:  Some of these metrics may not be present in the final product,
  // but exist in development builds to experiment with inline policy tuning.

  // This heuristic framework does not model well the very significant
  // effects of multiple-level inlining.  It is possible to see no immediate
  // profit from inlining X->Y, but to get great profit from a subsequent
  // inlining X->Y->Z.

  // This framework does not take well into account the problem of N**2 code
  // size in a clique of mutually inlinable methods.

  WarmCallInfo*  next() const          { return _next; }
  void       set_next(WarmCallInfo* n) { _next = n; }

  static WarmCallInfo _always_hot;
  static WarmCallInfo _always_cold;

  // Constructor intitialization of always_hot and always_cold
  WarmCallInfo(float c, float p, float w, float s) {
    _call = NULL;
    _hot_cg = NULL;
    _next = NULL;
    _count = c;
    _profit = p;
    _work = w;
    _size = s;
    _heat = 0;
  }

 public:
  // Because WarmInfo objects live over the entire lifetime of the
  // Compile object, they are allocated into the comp_arena, which
  // does not get resource marked or reset during the compile process
  void *operator new( size_t x, Compile* C ) throw() { return C->comp_arena()->Amalloc(x); }
  void operator delete( void * ) { } // fast deallocation

  static WarmCallInfo* always_hot();
  static WarmCallInfo* always_cold();

  WarmCallInfo() {
    _call = NULL;
    _hot_cg = NULL;
    _next = NULL;
    _count = _profit = _work = _size = _heat = 0;
  }

  CallNode* call() const { return _call; }
  float count()    const { return _count; }
  float size()     const { return _size; }
  float work()     const { return _work; }
  float profit()   const { return _profit; }
  float heat()     const { return _heat; }

  void set_count(float x)     { _count = x; }
  void set_size(float x)      { _size = x; }
  void set_work(float x)      { _work = x; }
  void set_profit(float x)    { _profit = x; }
  void set_heat(float x)      { _heat = x; }

  // Load initial heuristics from profiles, etc.
  // The heuristics can be tweaked further by the caller.
  void init(JVMState* call_site, ciMethod* call_method, ciCallProfile& profile, float prof_factor);

  static float MAX_VALUE() { return +1.0e10; }
  static float MIN_VALUE() { return -1.0e10; }

  float compute_heat() const;

  void set_call(CallNode* call)      { _call = call; }
  void set_hot_cg(CallGenerator* cg) { _hot_cg = cg; }

  // Do not queue very hot or very cold calls.
  // Make very cold ones out of line immediately.
  // Inline very hot ones immediately.
  // These queries apply various tunable limits
  // to the above metrics in a systematic way.
  // Test for coldness before testing for hotness.
  bool is_cold() const;
  bool is_hot() const;

  // Force a warm call to be hot.  This worklists the call node for inlining.
  void make_hot();

  // Force a warm call to be cold.  This worklists the call node for out-of-lining.
  void make_cold();

  // A reproducible total ordering, in which heat is the major key.
  bool warmer_than(WarmCallInfo* that);

  // List management.  These methods are called with the list head,
  // and return the new list head, inserting or removing the receiver.
  WarmCallInfo* insert_into(WarmCallInfo* head);
  WarmCallInfo* remove_from(WarmCallInfo* head);

#ifndef PRODUCT
  void print() const;
  void print_all() const;
  int count_all() const;
#endif
};

#endif // SHARE_VM_OPTO_CALLGENERATOR_HPP
