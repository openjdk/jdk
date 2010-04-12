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

# include "incls/_precompiled.incl"
# include "incls/_compilationPolicy.cpp.incl"

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

  default:
    fatal("CompilationPolicyChoice must be in the range: [0-1]");
  }
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
bool CompilationPolicy::mustBeCompiled(methodHandle m) {
  if (m->has_compiled_code()) return false;       // already compiled
  if (!canBeCompiled(m))      return false;

  return !UseInterpreter ||                                              // must compile all methods
         (UseCompiler && AlwaysCompileLoopMethods && m->has_loops() && CompileBroker::should_compile_new_jobs()); // eagerly compile loop methods
}

// Returns true if m is allowed to be compiled
bool CompilationPolicy::canBeCompiled(methodHandle m) {
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

  return !m->is_not_compilable();
}

#ifndef PRODUCT
void CompilationPolicy::print_time() {
  tty->print_cr ("Accumulated compilationPolicy times:");
  tty->print_cr ("---------------------------");
  tty->print_cr ("  Total: %3.3f sec.", _accumulated_time.seconds());
}

static void trace_osr_completion(nmethod* osr_nm) {
  if (TraceOnStackReplacement) {
    if (osr_nm == NULL) tty->print_cr("compilation failed");
    else tty->print_cr("nmethod " INTPTR_FORMAT, osr_nm);
  }
}
#endif // !PRODUCT

void CompilationPolicy::reset_counter_for_invocation_event(methodHandle m) {
  // Make sure invocation and backedge counter doesn't overflow again right away
  // as would be the case for native methods.

  // BUT also make sure the method doesn't look like it was never executed.
  // Set carry bit and reduce counter's value to min(count, CompileThreshold/2).
  m->invocation_counter()->set_carry();
  m->backedge_counter()->set_carry();

  assert(!m->was_never_executed(), "don't reset to 0 -- could be mistaken for never-executed");
}

void CompilationPolicy::reset_counter_for_back_branch_event(methodHandle m) {
  // Delay next back-branch event but pump up invocation counter to triger
  // whole method compilation.
  InvocationCounter* i = m->invocation_counter();
  InvocationCounter* b = m->backedge_counter();

  // Don't set invocation_counter's value too low otherwise the method will
  // look like immature (ic < ~5300) which prevents the inlining based on
  // the type profiling.
  i->set(i->state(), CompileThreshold);
  // Don't reset counter too low - it is used to check if OSR method is ready.
  b->set(b->state(), CompileThreshold / 2);
}

// SimpleCompPolicy - compile current method

void SimpleCompPolicy::method_invocation_event( methodHandle m, TRAPS) {
  assert(UseCompiler || CompileTheWorld, "UseCompiler should be set by now.");

  int hot_count = m->invocation_count();
  reset_counter_for_invocation_event(m);
  const char* comment = "count";

  if (!delayCompilationDuringStartup() && canBeCompiled(m) && UseCompiler && CompileBroker::should_compile_new_jobs()) {
    nmethod* nm = m->code();
    if (nm == NULL ) {
      const char* comment = "count";
      CompileBroker::compile_method(m, InvocationEntryBci,
                                    m, hot_count, comment, CHECK);
    } else {
#ifdef TIERED

      if (nm->is_compiled_by_c1()) {
        const char* comment = "tier1 overflow";
        CompileBroker::compile_method(m, InvocationEntryBci,
                                      m, hot_count, comment, CHECK);
      }
#endif // TIERED
    }
  }
}

void SimpleCompPolicy::method_back_branch_event(methodHandle m, int branch_bci, int loop_top_bci, TRAPS) {
  assert(UseCompiler || CompileTheWorld, "UseCompiler should be set by now.");

  int hot_count = m->backedge_count();
  const char* comment = "backedge_count";

  if (!m->is_not_osr_compilable() && !delayCompilationDuringStartup() && canBeCompiled(m) && CompileBroker::should_compile_new_jobs()) {
    CompileBroker::compile_method(m, loop_top_bci, m, hot_count, comment, CHECK);

    NOT_PRODUCT(trace_osr_completion(m->lookup_osr_nmethod_for(loop_top_bci));)
  }
}

int SimpleCompPolicy::compilation_level(methodHandle m, int branch_bci)
{
#ifdef TIERED
  if (!TieredCompilation) {
    return CompLevel_highest_tier;
  }
  if (/* m()->tier1_compile_done() && */
     // QQQ HACK FIX ME set tier1_compile_done!!
      !m()->is_native()) {
    // Grab the nmethod so it doesn't go away while it's being queried
    nmethod* code = m()->code();
    if (code != NULL && code->is_compiled_by_c1()) {
      return CompLevel_highest_tier;
    }
  }
  return CompLevel_fast_compile;
#else
  return CompLevel_highest_tier;
#endif // TIERED
}

// StackWalkCompPolicy - walk up stack to find a suitable method to compile

#ifdef COMPILER2
const char* StackWalkCompPolicy::_msg = NULL;


// Consider m for compilation
void StackWalkCompPolicy::method_invocation_event(methodHandle m, TRAPS) {
  assert(UseCompiler || CompileTheWorld, "UseCompiler should be set by now.");

  int hot_count = m->invocation_count();
  reset_counter_for_invocation_event(m);
  const char* comment = "count";

  if (m->code() == NULL && !delayCompilationDuringStartup() && canBeCompiled(m) && UseCompiler && CompileBroker::should_compile_new_jobs()) {
    ResourceMark rm(THREAD);
    JavaThread *thread = (JavaThread*)THREAD;
    frame       fr     = thread->last_frame();
    assert(fr.is_interpreted_frame(), "must be interpreted");
    assert(fr.interpreter_frame_method() == m(), "bad method");

    if (TraceCompilationPolicy) {
      tty->print("method invocation trigger: ");
      m->print_short_name(tty);
      tty->print(" ( interpreted " INTPTR_FORMAT ", size=%d ) ", (address)m(), m->code_size());
    }
    RegisterMap reg_map(thread, false);
    javaVFrame* triggerVF = thread->last_java_vframe(&reg_map);
    // triggerVF is the frame that triggered its counter
    RFrame* first = new InterpretedRFrame(triggerVF->fr(), thread, m);

    if (first->top_method()->code() != NULL) {
      // called obsolete method/nmethod -- no need to recompile
      if (TraceCompilationPolicy) tty->print_cr(" --> " INTPTR_FORMAT, first->top_method()->code());
    } else if (compilation_level(m, InvocationEntryBci) == CompLevel_fast_compile) {
      // Tier1 compilation policy avaoids stack walking.
      CompileBroker::compile_method(m, InvocationEntryBci,
                                    m, hot_count, comment, CHECK);
    } else {
      if (TimeCompilationPolicy) accumulated_time()->start();
      GrowableArray<RFrame*>* stack = new GrowableArray<RFrame*>(50);
      stack->push(first);
      RFrame* top = findTopInlinableFrame(stack);
      if (TimeCompilationPolicy) accumulated_time()->stop();
      assert(top != NULL, "findTopInlinableFrame returned null");
      if (TraceCompilationPolicy) top->print();
      CompileBroker::compile_method(top->top_method(), InvocationEntryBci,
                                    m, hot_count, comment, CHECK);
    }
  }
}

void StackWalkCompPolicy::method_back_branch_event(methodHandle m, int branch_bci, int loop_top_bci, TRAPS) {
  assert(UseCompiler || CompileTheWorld, "UseCompiler should be set by now.");

  int hot_count = m->backedge_count();
  const char* comment = "backedge_count";

  if (!m->is_not_osr_compilable() && !delayCompilationDuringStartup() && canBeCompiled(m) && CompileBroker::should_compile_new_jobs()) {
    CompileBroker::compile_method(m, loop_top_bci, m, hot_count, comment, CHECK);

    NOT_PRODUCT(trace_osr_completion(m->lookup_osr_nmethod_for(loop_top_bci));)
  }
}

int StackWalkCompPolicy::compilation_level(methodHandle m, int osr_bci)
{
  int comp_level = CompLevel_full_optimization;
  if (TieredCompilation && osr_bci == InvocationEntryBci) {
    if (CompileTheWorld) {
      // Under CTW, the first compile is tier1, the second tier2
      if (m->highest_tier_compile() == CompLevel_none) {
        comp_level = CompLevel_fast_compile;
      }
    } else if (!m->has_osr_nmethod()) {
      // Before tier1 is done, use invocation_count + backedge_count to
      // compare against the threshold.  After that, the counters may/will
      // be reset, so rely on the straight interpreter_invocation_count.
      if (m->highest_tier_compile() == CompLevel_initial_compile) {
        if (m->interpreter_invocation_count() < Tier2CompileThreshold) {
          comp_level = CompLevel_fast_compile;
        }
      } else if (m->invocation_count() + m->backedge_count() <
                 Tier2CompileThreshold) {
        comp_level = CompLevel_fast_compile;
      }
    }

  }
  return comp_level;
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

    methodHandle m = current->top_method();
    methodHandle next_m = next->top_method();

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
    if (!canBeCompiled(next_m)) {
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
      tty->print(" ( interpreted " INTPTR_FORMAT ", size=%d ) ", (address)next_m(), next_m->code_size());
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


const char* StackWalkCompPolicy::shouldInline(methodHandle m, float freq, int cnt) {
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


const char* StackWalkCompPolicy::shouldNotInline(methodHandle m) {
  // negative filter: should send NOT be inlined?  returns NULL (--> inline) or rejection msg
  if (m->is_abstract()) return (_msg = "abstract method");
  // note: we allow ik->is_abstract()
  if (!instanceKlass::cast(m->method_holder())->is_initialized()) return (_msg = "method holder not initialized");
  if (m->is_native()) return (_msg = "native method");
  nmethod* m_code = m->code();
  if( m_code != NULL && m_code->instructions_size() > InlineSmallCode )
    return (_msg = "already compiled into a big method");

  // use frequency-based objections only for non-trivial methods
  if (m->code_size() <= MaxTrivialSize) return NULL;
  if (UseInterpreter) {     // don't use counts with -Xcomp
    if ((m->code() == NULL) && m->was_never_executed()) return (_msg = "never executed");
    if (!m->was_executed_more_than(MIN2(MinInliningThreshold, CompileThreshold >> 1))) return (_msg = "executed < MinInliningThreshold times");
  }
  if (methodOopDesc::has_unloaded_classes_in_signature(m, JavaThread::current())) return (_msg = "unloaded signature classes");

  return NULL;
}



#endif // COMPILER2
