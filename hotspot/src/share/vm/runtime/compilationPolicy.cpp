/*
 * Copyright (c) 2000, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "code/compiledIC.hpp"
#include "code/nmethod.hpp"
#include "code/scopeDesc.hpp"
#include "interpreter/interpreter.hpp"
#include "oops/methodData.hpp"
#include "oops/method.hpp"
#include "oops/oop.inline.hpp"
#include "prims/nativeLookup.hpp"
#include "runtime/advancedThresholdPolicy.hpp"
#include "runtime/compilationPolicy.hpp"
#include "runtime/frame.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/rframe.hpp"
#include "runtime/simpleThresholdPolicy.hpp"
#include "runtime/stubRoutines.hpp"
#include "runtime/thread.hpp"
#include "runtime/timer.hpp"
#include "runtime/vframe.hpp"
#include "runtime/vm_operations.hpp"
#include "utilities/events.hpp"
#include "utilities/globalDefinitions.hpp"

CompilationPolicy* CompilationPolicy::_policy;
elapsedTimer       CompilationPolicy::_accumulated_time;
bool               CompilationPolicy::_in_vm_startup;

// Determine compilation policy based on command line argument
void compilationPolicy_init() {
  CompilationPolicy::set_in_vm_startup(DelayCompilationDuringStartup);

  switch(CompilationPolicyChoice) {
  case 0:
    CompilationPolicy::set_policy(new SimpleCompPolicy());
    break;

  case 1:
#ifdef COMPILER2
    CompilationPolicy::set_policy(new StackWalkCompPolicy());
#else
    Unimplemented();
#endif
    break;
  case 2:
#ifdef TIERED
    CompilationPolicy::set_policy(new SimpleThresholdPolicy());
#else
    Unimplemented();
#endif
    break;
  case 3:
#ifdef TIERED
    CompilationPolicy::set_policy(new AdvancedThresholdPolicy());
#else
    Unimplemented();
#endif
    break;
  default:
    fatal("CompilationPolicyChoice must be in the range: [0-3]");
  }
  CompilationPolicy::policy()->initialize();
}

void CompilationPolicy::completed_vm_startup() {
  if (TraceCompilationPolicy) {
    tty->print("CompilationPolicy: completed vm startup.\n");
  }
  _in_vm_startup = false;
}

// Returns true if m must be compiled before executing it
// This is intended to force compiles for methods (usually for
// debugging) that would otherwise be interpreted for some reason.
bool CompilationPolicy::must_be_compiled(methodHandle m, int comp_level) {
  // Don't allow Xcomp to cause compiles in replay mode
  if (ReplayCompiles) return false;

  if (m->has_compiled_code()) return false;       // already compiled
  if (!can_be_compiled(m, comp_level)) return false;

  return !UseInterpreter ||                                              // must compile all methods
         (UseCompiler && AlwaysCompileLoopMethods && m->has_loops() && CompileBroker::should_compile_new_jobs()); // eagerly compile loop methods
}

// Returns true if m is allowed to be compiled
bool CompilationPolicy::can_be_compiled(methodHandle m, int comp_level) {
  // allow any levels for WhiteBox
  assert(WhiteBoxAPI || comp_level == CompLevel_all || is_compile(comp_level), "illegal compilation level");

  if (m->is_abstract()) return false;
  if (DontCompileHugeMethods && m->code_size() > HugeMethodLimit) return false;

  // Math intrinsics should never be compiled as this can lead to
  // monotonicity problems because the interpreter will prefer the
  // compiled code to the intrinsic version.  This can't happen in
  // production because the invocation counter can't be incremented
  // but we shouldn't expose the system to this problem in testing
  // modes.
  if (!AbstractInterpreter::can_be_compiled(m)) {
    return false;
  }
  if (comp_level == CompLevel_all) {
    if (TieredCompilation) {
      // enough to be compilable at any level for tiered
      return !m->is_not_compilable(CompLevel_simple) || !m->is_not_compilable(CompLevel_full_optimization);
    } else {
      // must be compilable at available level for non-tiered
      return !m->is_not_compilable(CompLevel_highest_tier);
    }
  } else if (is_compile(comp_level)) {
    return !m->is_not_compilable(comp_level);
  }
  return false;
}

// Returns true if m is allowed to be osr compiled
bool CompilationPolicy::can_be_osr_compiled(methodHandle m, int comp_level) {
  bool result = false;
  if (comp_level == CompLevel_all) {
    if (TieredCompilation) {
      // enough to be osr compilable at any level for tiered
      result = !m->is_not_osr_compilable(CompLevel_simple) || !m->is_not_osr_compilable(CompLevel_full_optimization);
    } else {
      // must be osr compilable at available level for non-tiered
      result = !m->is_not_osr_compilable(CompLevel_highest_tier);
    }
  } else if (is_compile(comp_level)) {
    result = !m->is_not_osr_compilable(comp_level);
  }
  return (result && can_be_compiled(m, comp_level));
}

bool CompilationPolicy::is_compilation_enabled() {
  // NOTE: CompileBroker::should_compile_new_jobs() checks for UseCompiler
  return !delay_compilation_during_startup() && CompileBroker::should_compile_new_jobs();
}

CompileTask* CompilationPolicy::select_task_helper(CompileQueue* compile_queue) {
#if INCLUDE_JVMCI
  if (UseJVMCICompiler && !BackgroundCompilation) {
    /*
     * In blocking compilation mode, the CompileBroker will make
     * compilations submitted by a JVMCI compiler thread non-blocking. These
     * compilations should be scheduled after all blocking compilations
     * to service non-compiler related compilations sooner and reduce the
     * chance of such compilations timing out.
     */
    for (CompileTask* task = compile_queue->first(); task != NULL; task = task->next()) {
      if (task->is_blocking()) {
        return task;
      }
    }
  }
#endif
  return compile_queue->first();
}

#ifndef PRODUCT
void CompilationPolicy::print_time() {
  tty->print_cr ("Accumulated compilationPolicy times:");
  tty->print_cr ("---------------------------");
  tty->print_cr ("  Total: %3.3f sec.", _accumulated_time.seconds());
}

void NonTieredCompPolicy::trace_osr_completion(nmethod* osr_nm) {
  if (TraceOnStackReplacement) {
    if (osr_nm == NULL) tty->print_cr("compilation failed");
    else tty->print_cr("nmethod " INTPTR_FORMAT, p2i(osr_nm));
  }
}
#endif // !PRODUCT

void NonTieredCompPolicy::initialize() {
  // Setup the compiler thread numbers
  if (CICompilerCountPerCPU) {
    // Example: if CICompilerCountPerCPU is true, then we get
    // max(log2(8)-1,1) = 2 compiler threads on an 8-way machine.
    // May help big-app startup time.
    _compiler_count = MAX2(log2_intptr(os::active_processor_count())-1,1);
    FLAG_SET_ERGO(intx, CICompilerCount, _compiler_count);
  } else {
    _compiler_count = CICompilerCount;
  }
}

// Note: this policy is used ONLY if TieredCompilation is off.
// compiler_count() behaves the following way:
// - with TIERED build (with both COMPILER1 and COMPILER2 defined) it should return
//   zero for the c1 compilation levels, hence the particular ordering of the
//   statements.
// - the same should happen when COMPILER2 is defined and COMPILER1 is not
//   (server build without TIERED defined).
// - if only COMPILER1 is defined (client build), zero should be returned for
//   the c2 level.
// - if neither is defined - always return zero.
int NonTieredCompPolicy::compiler_count(CompLevel comp_level) {
  assert(!TieredCompilation, "This policy should not be used with TieredCompilation");
#ifdef COMPILER2
  if (is_c2_compile(comp_level)) {
    return _compiler_count;
  } else {
    return 0;
  }
#endif

#ifdef COMPILER1
  if (is_c1_compile(comp_level)) {
    return _compiler_count;
  } else {
    return 0;
  }
#endif

  return 0;
}

void NonTieredCompPolicy::reset_counter_for_invocation_event(const methodHandle& m) {
  // Make sure invocation and backedge counter doesn't overflow again right away
  // as would be the case for native methods.

  // BUT also make sure the method doesn't look like it was never executed.
  // Set carry bit and reduce counter's value to min(count, CompileThreshold/2).
  MethodCounters* mcs = m->method_counters();
  assert(mcs != NULL, "MethodCounters cannot be NULL for profiling");
  mcs->invocation_counter()->set_carry();
  mcs->backedge_counter()->set_carry();

  assert(!m->was_never_executed(), "don't reset to 0 -- could be mistaken for never-executed");
}

void NonTieredCompPolicy::reset_counter_for_back_branch_event(const methodHandle& m) {
  // Delay next back-branch event but pump up invocation counter to trigger
  // whole method compilation.
  MethodCounters* mcs = m->method_counters();
  assert(mcs != NULL, "MethodCounters cannot be NULL for profiling");
  InvocationCounter* i = mcs->invocation_counter();
  InvocationCounter* b = mcs->backedge_counter();

  // Don't set invocation_counter's value too low otherwise the method will
  // look like immature (ic < ~5300) which prevents the inlining based on
  // the type profiling.
  i->set(i->state(), CompileThreshold);
  // Don't reset counter too low - it is used to check if OSR method is ready.
  b->set(b->state(), CompileThreshold / 2);
}

//
// CounterDecay
//
// Iterates through invocation counters and decrements them. This
// is done at each safepoint.
//
class CounterDecay : public AllStatic {
  static jlong _last_timestamp;
  static void do_method(Method* m) {
    MethodCounters* mcs = m->method_counters();
    if (mcs != NULL) {
      mcs->invocation_counter()->decay();
    }
  }
public:
  static void decay();
  static bool is_decay_needed() {
    return (os::javaTimeMillis() - _last_timestamp) > CounterDecayMinIntervalLength;
  }
};

jlong CounterDecay::_last_timestamp = 0;

void CounterDecay::decay() {
  _last_timestamp = os::javaTimeMillis();

  // This operation is going to be performed only at the end of a safepoint
  // and hence GC's will not be going on, all Java mutators are suspended
  // at this point and hence SystemDictionary_lock is also not needed.
  assert(SafepointSynchronize::is_at_safepoint(), "can only be executed at a safepoint");
  int nclasses = SystemDictionary::number_of_classes();
  double classes_per_tick = nclasses * (CounterDecayMinIntervalLength * 1e-3 /
                                        CounterHalfLifeTime);
  for (int i = 0; i < classes_per_tick; i++) {
    Klass* k = SystemDictionary::try_get_next_class();
    if (k != NULL && k->is_instance_klass()) {
      InstanceKlass::cast(k)->methods_do(do_method);
    }
  }
}

// Called at the end of the safepoint
void NonTieredCompPolicy::do_safepoint_work() {
  if(UseCounterDecay && CounterDecay::is_decay_needed()) {
    CounterDecay::decay();
  }
}

void NonTieredCompPolicy::reprofile(ScopeDesc* trap_scope, bool is_osr) {
  ScopeDesc* sd = trap_scope;
  MethodCounters* mcs;
  InvocationCounter* c;
  for (; !sd->is_top(); sd = sd->sender()) {
    mcs = sd->method()->method_counters();
    if (mcs != NULL) {
      // Reset ICs of inlined methods, since they can trigger compilations also.
      mcs->invocation_counter()->reset();
    }
  }
  mcs = sd->method()->method_counters();
  if (mcs != NULL) {
    c = mcs->invocation_counter();
    if (is_osr) {
      // It was an OSR method, so bump the count higher.
      c->set(c->state(), CompileThreshold);
    } else {
      c->reset();
    }
    mcs->backedge_counter()->reset();
  }
}

// This method can be called by any component of the runtime to notify the policy
// that it's recommended to delay the compilation of this method.
void NonTieredCompPolicy::delay_compilation(Method* method) {
  MethodCounters* mcs = method->method_counters();
  if (mcs != NULL) {
    mcs->invocation_counter()->decay();
    mcs->backedge_counter()->decay();
  }
}

void NonTieredCompPolicy::disable_compilation(Method* method) {
  MethodCounters* mcs = method->method_counters();
  if (mcs != NULL) {
    mcs->invocation_counter()->set_state(InvocationCounter::wait_for_nothing);
    mcs->backedge_counter()->set_state(InvocationCounter::wait_for_nothing);
  }
}

CompileTask* NonTieredCompPolicy::select_task(CompileQueue* compile_queue) {
  return select_task_helper(compile_queue);
}

bool NonTieredCompPolicy::is_mature(Method* method) {
  MethodData* mdo = method->method_data();
  assert(mdo != NULL, "Should be");
  uint current = mdo->mileage_of(method);
  uint initial = mdo->creation_mileage();
  if (current < initial)
    return true;  // some sort of overflow
  uint target;
  if (ProfileMaturityPercentage <= 0)
    target = (uint) -ProfileMaturityPercentage;  // absolute value
  else
    target = (uint)( (ProfileMaturityPercentage * CompileThreshold) / 100 );
  return (current >= initial + target);
}

nmethod* NonTieredCompPolicy::event(const methodHandle& method, const methodHandle& inlinee, int branch_bci,
                                    int bci, CompLevel comp_level, nmethod* nm, JavaThread* thread) {
  assert(comp_level == CompLevel_none, "This should be only called from the interpreter");
  NOT_PRODUCT(trace_frequency_counter_overflow(method, branch_bci, bci));
  if (JvmtiExport::can_post_interpreter_events() && thread->is_interp_only_mode()) {
    // If certain JVMTI events (e.g. frame pop event) are requested then the
    // thread is forced to remain in interpreted code. This is
    // implemented partly by a check in the run_compiled_code
    // section of the interpreter whether we should skip running
    // compiled code, and partly by skipping OSR compiles for
    // interpreted-only threads.
    if (bci != InvocationEntryBci) {
      reset_counter_for_back_branch_event(method);
      return NULL;
    }
  }
  if (CompileTheWorld || ReplayCompiles) {
    // Don't trigger other compiles in testing mode
    if (bci == InvocationEntryBci) {
      reset_counter_for_invocation_event(method);
    } else {
      reset_counter_for_back_branch_event(method);
    }
    return NULL;
  }

  if (bci == InvocationEntryBci) {
    // when code cache is full, compilation gets switched off, UseCompiler
    // is set to false
    if (!method->has_compiled_code() && UseCompiler) {
      method_invocation_event(method, thread);
    } else {
      // Force counter overflow on method entry, even if no compilation
      // happened.  (The method_invocation_event call does this also.)
      reset_counter_for_invocation_event(method);
    }
    // compilation at an invocation overflow no longer goes and retries test for
    // compiled method. We always run the loser of the race as interpreted.
    // so return NULL
    return NULL;
  } else {
    // counter overflow in a loop => try to do on-stack-replacement
    nmethod* osr_nm = method->lookup_osr_nmethod_for(bci, CompLevel_highest_tier, true);
    NOT_PRODUCT(trace_osr_request(method, osr_nm, bci));
    // when code cache is full, we should not compile any more...
    if (osr_nm == NULL && UseCompiler) {
      method_back_branch_event(method, bci, thread);
      osr_nm = method->lookup_osr_nmethod_for(bci, CompLevel_highest_tier, true);
    }
    if (osr_nm == NULL) {
      reset_counter_for_back_branch_event(method);
      return NULL;
    }
    return osr_nm;
  }
  return NULL;
}

#ifndef PRODUCT
void NonTieredCompPolicy::trace_frequency_counter_overflow(const methodHandle& m, int branch_bci, int bci) {
  if (TraceInvocationCounterOverflow) {
    MethodCounters* mcs = m->method_counters();
    assert(mcs != NULL, "MethodCounters cannot be NULL for profiling");
    InvocationCounter* ic = mcs->invocation_counter();
    InvocationCounter* bc = mcs->backedge_counter();
    ResourceMark rm;
    if (bci == InvocationEntryBci) {
      tty->print("comp-policy cntr ovfl @ %d in entry of ", bci);
    } else {
      tty->print("comp-policy cntr ovfl @ %d in loop of ", bci);
    }
    m->print_value();
    tty->cr();
    ic->print();
    bc->print();
    if (ProfileInterpreter) {
      if (bci != InvocationEntryBci) {
        MethodData* mdo = m->method_data();
        if (mdo != NULL) {
          int count = mdo->bci_to_data(branch_bci)->as_JumpData()->taken();
          tty->print_cr("back branch count = %d", count);
        }
      }
    }
  }
}

void NonTieredCompPolicy::trace_osr_request(const methodHandle& method, nmethod* osr, int bci) {
  if (TraceOnStackReplacement) {
    ResourceMark rm;
    tty->print(osr != NULL ? "Reused OSR entry for " : "Requesting OSR entry for ");
    method->print_short_name(tty);
    tty->print_cr(" at bci %d", bci);
  }
}
#endif // !PRODUCT

// SimpleCompPolicy - compile current method

void SimpleCompPolicy::method_invocation_event(const methodHandle& m, JavaThread* thread) {
  const int comp_level = CompLevel_highest_tier;
  const int hot_count = m->invocation_count();
  reset_counter_for_invocation_event(m);
  const char* comment = "count";

  if (is_compilation_enabled() && can_be_compiled(m, comp_level)) {
    nmethod* nm = m->code();
    if (nm == NULL ) {
      CompileBroker::compile_method(m, InvocationEntryBci, comp_level, m, hot_count, comment, thread);
    }
  }
}

void SimpleCompPolicy::method_back_branch_event(const methodHandle& m, int bci, JavaThread* thread) {
  const int comp_level = CompLevel_highest_tier;
  const int hot_count = m->backedge_count();
  const char* comment = "backedge_count";

  if (is_compilation_enabled() && can_be_osr_compiled(m, comp_level)) {
    CompileBroker::compile_method(m, bci, comp_level, m, hot_count, comment, thread);
    NOT_PRODUCT(trace_osr_completion(m->lookup_osr_nmethod_for(bci, comp_level, true));)
  }
}
// StackWalkCompPolicy - walk up stack to find a suitable method to compile

#ifdef COMPILER2
const char* StackWalkCompPolicy::_msg = NULL;


// Consider m for compilation
void StackWalkCompPolicy::method_invocation_event(const methodHandle& m, JavaThread* thread) {
  const int comp_level = CompLevel_highest_tier;
  const int hot_count = m->invocation_count();
  reset_counter_for_invocation_event(m);
  const char* comment = "count";

  if (is_compilation_enabled() && m->code() == NULL && can_be_compiled(m, comp_level)) {
    ResourceMark rm(thread);
    frame       fr     = thread->last_frame();
    assert(fr.is_interpreted_frame(), "must be interpreted");
    assert(fr.interpreter_frame_method() == m(), "bad method");

    if (TraceCompilationPolicy) {
      tty->print("method invocation trigger: ");
      m->print_short_name(tty);
      tty->print(" ( interpreted " INTPTR_FORMAT ", size=%d ) ", p2i((address)m()), m->code_size());
    }
    RegisterMap reg_map(thread, false);
    javaVFrame* triggerVF = thread->last_java_vframe(&reg_map);
    // triggerVF is the frame that triggered its counter
    RFrame* first = new InterpretedRFrame(triggerVF->fr(), thread, m());

    if (first->top_method()->code() != NULL) {
      // called obsolete method/nmethod -- no need to recompile
      if (TraceCompilationPolicy) tty->print_cr(" --> " INTPTR_FORMAT, p2i(first->top_method()->code()));
    } else {
      if (TimeCompilationPolicy) accumulated_time()->start();
      GrowableArray<RFrame*>* stack = new GrowableArray<RFrame*>(50);
      stack->push(first);
      RFrame* top = findTopInlinableFrame(stack);
      if (TimeCompilationPolicy) accumulated_time()->stop();
      assert(top != NULL, "findTopInlinableFrame returned null");
      if (TraceCompilationPolicy) top->print();
      CompileBroker::compile_method(top->top_method(), InvocationEntryBci, comp_level,
                                    m, hot_count, comment, thread);
    }
  }
}

void StackWalkCompPolicy::method_back_branch_event(const methodHandle& m, int bci, JavaThread* thread) {
  const int comp_level = CompLevel_highest_tier;
  const int hot_count = m->backedge_count();
  const char* comment = "backedge_count";

  if (is_compilation_enabled() && can_be_osr_compiled(m, comp_level)) {
    CompileBroker::compile_method(m, bci, comp_level, m, hot_count, comment, thread);
    NOT_PRODUCT(trace_osr_completion(m->lookup_osr_nmethod_for(bci, comp_level, true));)
  }
}

RFrame* StackWalkCompPolicy::findTopInlinableFrame(GrowableArray<RFrame*>* stack) {
  // go up the stack until finding a frame that (probably) won't be inlined
  // into its caller
  RFrame* current = stack->at(0); // current choice for stopping
  assert( current && !current->is_compiled(), "" );
  const char* msg = NULL;

  while (1) {

    // before going up the stack further, check if doing so would get us into
    // compiled code
    RFrame* next = senderOf(current, stack);
    if( !next )               // No next frame up the stack?
      break;                  // Then compile with current frame

    Method* m = current->top_method();
    Method* next_m = next->top_method();

    if (TraceCompilationPolicy && Verbose) {
      tty->print("[caller: ");
      next_m->print_short_name(tty);
      tty->print("] ");
    }

    if( !Inline ) {           // Inlining turned off
      msg = "Inlining turned off";
      break;
    }
    if (next_m->is_not_compilable()) { // Did fail to compile this before/
      msg = "caller not compilable";
      break;
    }
    if (next->num() > MaxRecompilationSearchLength) {
      // don't go up too high when searching for recompilees
      msg = "don't go up any further: > MaxRecompilationSearchLength";
      break;
    }
    if (next->distance() > MaxInterpretedSearchLength) {
      // don't go up too high when searching for recompilees
      msg = "don't go up any further: next > MaxInterpretedSearchLength";
      break;
    }
    // Compiled frame above already decided not to inline;
    // do not recompile him.
    if (next->is_compiled()) {
      msg = "not going up into optimized code";
      break;
    }

    // Interpreted frame above us was already compiled.  Do not force
    // a recompile, although if the frame above us runs long enough an
    // OSR might still happen.
    if( current->is_interpreted() && next_m->has_compiled_code() ) {
      msg = "not going up -- already compiled caller";
      break;
    }

    // Compute how frequent this call site is.  We have current method 'm'.
    // We know next method 'next_m' is interpreted.  Find the call site and
    // check the various invocation counts.
    int invcnt = 0;             // Caller counts
    if (ProfileInterpreter) {
      invcnt = next_m->interpreter_invocation_count();
    }
    int cnt = 0;                // Call site counts
    if (ProfileInterpreter && next_m->method_data() != NULL) {
      ResourceMark rm;
      int bci = next->top_vframe()->bci();
      ProfileData* data = next_m->method_data()->bci_to_data(bci);
      if (data != NULL && data->is_CounterData())
        cnt = data->as_CounterData()->count();
    }

    // Caller counts / call-site counts; i.e. is this call site
    // a hot call site for method next_m?
    int freq = (invcnt) ? cnt/invcnt : cnt;

    // Check size and frequency limits
    if ((msg = shouldInline(m, freq, cnt)) != NULL) {
      break;
    }
    // Check inlining negative tests
    if ((msg = shouldNotInline(m)) != NULL) {
      break;
    }


    // If the caller method is too big or something then we do not want to
    // compile it just to inline a method
    if (!can_be_compiled(next_m, CompLevel_any)) {
      msg = "caller cannot be compiled";
      break;
    }

    if( next_m->name() == vmSymbols::class_initializer_name() ) {
      msg = "do not compile class initializer (OSR ok)";
      break;
    }

    if (TraceCompilationPolicy && Verbose) {
      tty->print("\n\t     check caller: ");
      next_m->print_short_name(tty);
      tty->print(" ( interpreted " INTPTR_FORMAT ", size=%d ) ", p2i((address)next_m), next_m->code_size());
    }

    current = next;
  }

  assert( !current || !current->is_compiled(), "" );

  if (TraceCompilationPolicy && msg) tty->print("(%s)\n", msg);

  return current;
}

RFrame* StackWalkCompPolicy::senderOf(RFrame* rf, GrowableArray<RFrame*>* stack) {
  RFrame* sender = rf->caller();
  if (sender && sender->num() == stack->length()) stack->push(sender);
  return sender;
}


const char* StackWalkCompPolicy::shouldInline(const methodHandle& m, float freq, int cnt) {
  // Allows targeted inlining
  // positive filter: should send be inlined?  returns NULL (--> yes)
  // or rejection msg
  int max_size = MaxInlineSize;
  int cost = m->code_size();

  // Check for too many throws (and not too huge)
  if (m->interpreter_throwout_count() > InlineThrowCount && cost < InlineThrowMaxSize ) {
    return NULL;
  }

  // bump the max size if the call is frequent
  if ((freq >= InlineFrequencyRatio) || (cnt >= InlineFrequencyCount)) {
    if (TraceFrequencyInlining) {
      tty->print("(Inlined frequent method)\n");
      m->print();
    }
    max_size = FreqInlineSize;
  }
  if (cost > max_size) {
    return (_msg = "too big");
  }
  return NULL;
}


const char* StackWalkCompPolicy::shouldNotInline(const methodHandle& m) {
  // negative filter: should send NOT be inlined?  returns NULL (--> inline) or rejection msg
  if (m->is_abstract()) return (_msg = "abstract method");
  // note: we allow ik->is_abstract()
  if (!m->method_holder()->is_initialized()) return (_msg = "method holder not initialized");
  if (m->is_native()) return (_msg = "native method");
  nmethod* m_code = m->code();
  if (m_code != NULL && m_code->code_size() > InlineSmallCode)
    return (_msg = "already compiled into a big method");

  // use frequency-based objections only for non-trivial methods
  if (m->code_size() <= MaxTrivialSize) return NULL;
  if (UseInterpreter) {     // don't use counts with -Xcomp
    if ((m->code() == NULL) && m->was_never_executed()) return (_msg = "never executed");
    if (!m->was_executed_more_than(MIN2(MinInliningThreshold, CompileThreshold >> 1))) return (_msg = "executed < MinInliningThreshold times");
  }
  if (Method::has_unloaded_classes_in_signature(m, JavaThread::current())) return (_msg = "unloaded signature classes");

  return NULL;
}



#endif // COMPILER2
