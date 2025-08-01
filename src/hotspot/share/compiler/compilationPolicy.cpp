/*
 * Copyright (c) 2010, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "cds/aotLinkedClassBulkLoader.hpp"
#include "code/scopeDesc.hpp"
#include "compiler/compilationPolicy.hpp"
#include "compiler/compileBroker.hpp"
#include "compiler/compilerDefinitions.inline.hpp"
#include "compiler/compilerOracle.hpp"
#include "memory/resourceArea.hpp"
#include "oops/method.inline.hpp"
#include "oops/methodData.hpp"
#include "oops/oop.inline.hpp"
#include "oops/trainingData.hpp"
#include "prims/jvmtiExport.hpp"
#include "runtime/arguments.hpp"
#include "runtime/deoptimization.hpp"
#include "runtime/frame.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/safepointVerifiers.hpp"
#ifdef COMPILER1
#include "c1/c1_Compiler.hpp"
#endif
#ifdef COMPILER2
#include "opto/c2compiler.hpp"
#endif
#if INCLUDE_JVMCI
#include "jvmci/jvmci.hpp"
#endif

int64_t CompilationPolicy::_start_time = 0;
int CompilationPolicy::_c1_count = 0;
int CompilationPolicy::_c2_count = 0;
double CompilationPolicy::_increase_threshold_at_ratio = 0;

CompilationPolicy::TrainingReplayQueue CompilationPolicy::_training_replay_queue;

void compilationPolicy_init() {
  CompilationPolicy::initialize();
}

int CompilationPolicy::compiler_count(CompLevel comp_level) {
  if (is_c1_compile(comp_level)) {
    return c1_count();
  } else if (is_c2_compile(comp_level)) {
    return c2_count();
  }
  return 0;
}

// Returns true if m must be compiled before executing it
// This is intended to force compiles for methods (usually for
// debugging) that would otherwise be interpreted for some reason.
bool CompilationPolicy::must_be_compiled(const methodHandle& m, int comp_level) {
  // Don't allow Xcomp to cause compiles in replay mode
  if (ReplayCompiles) return false;

  if (m->has_compiled_code()) return false;       // already compiled
  if (!can_be_compiled(m, comp_level)) return false;

  return !UseInterpreter ||                                                                        // must compile all methods
         (AlwaysCompileLoopMethods && m->has_loops() && CompileBroker::should_compile_new_jobs()); // eagerly compile loop methods
}

void CompilationPolicy::maybe_compile_early(const methodHandle& m, TRAPS) {
  if (m->method_holder()->is_not_initialized()) {
    // 'is_not_initialized' means not only '!is_initialized', but also that
    // initialization has not been started yet ('!being_initialized')
    // Do not force compilation of methods in uninitialized classes.
    return;
  }
  if (!m->is_native() && MethodTrainingData::have_data()) {
    MethodTrainingData* mtd = MethodTrainingData::find_fast(m);
    if (mtd == nullptr) {
      return;              // there is no training data recorded for m
    }
    CompLevel cur_level = static_cast<CompLevel>(m->highest_comp_level());
    CompLevel next_level = trained_transition(m, cur_level, mtd, THREAD);
    if (next_level != cur_level && can_be_compiled(m, next_level) && !CompileBroker::compilation_is_in_queue(m)) {
      if (PrintTieredEvents) {
        print_event(FORCE_COMPILE, m(), m(), InvocationEntryBci, next_level);
      }
      CompileBroker::compile_method(m, InvocationEntryBci, next_level, 0, CompileTask::Reason_MustBeCompiled, THREAD);
      if (HAS_PENDING_EXCEPTION) {
        CLEAR_PENDING_EXCEPTION;
      }
    }
  }
}

void CompilationPolicy::compile_if_required(const methodHandle& m, TRAPS) {
  if (!THREAD->can_call_java() || THREAD->is_Compiler_thread()) {
    // don't force compilation, resolve was on behalf of compiler
    return;
  }
  if (m->method_holder()->is_not_initialized()) {
    // 'is_not_initialized' means not only '!is_initialized', but also that
    // initialization has not been started yet ('!being_initialized')
    // Do not force compilation of methods in uninitialized classes.
    // Note that doing this would throw an assert later,
    // in CompileBroker::compile_method.
    // We sometimes use the link resolver to do reflective lookups
    // even before classes are initialized.
    return;
  }

  if (must_be_compiled(m)) {
    // This path is unusual, mostly used by the '-Xcomp' stress test mode.
    CompLevel level = initial_compile_level(m);
    if (PrintTieredEvents) {
      print_event(FORCE_COMPILE, m(), m(), InvocationEntryBci, level);
    }
    CompileBroker::compile_method(m, InvocationEntryBci, level, 0, CompileTask::Reason_MustBeCompiled, THREAD);
  }
}

void CompilationPolicy::replay_training_at_init_impl(InstanceKlass* klass, TRAPS) {
  if (!klass->has_init_deps_processed()) {
    ResourceMark rm;
    log_debug(training)("Replay training: %s", klass->external_name());

    KlassTrainingData* ktd = KlassTrainingData::find(klass);
    if (ktd != nullptr) {
      guarantee(ktd->has_holder(), "");
      ktd->notice_fully_initialized(); // sets klass->has_init_deps_processed bit
      assert(klass->has_init_deps_processed(), "");
      if (AOTCompileEagerly) {
        ktd->iterate_comp_deps([&](CompileTrainingData* ctd) {
          if (ctd->init_deps_left() == 0) {
            MethodTrainingData* mtd = ctd->method();
            if (mtd->has_holder()) {
              const methodHandle mh(THREAD, const_cast<Method*>(mtd->holder()));
              CompilationPolicy::maybe_compile_early(mh, THREAD);
            }
          }
        });
      }
    }
  }
}

void CompilationPolicy::replay_training_at_init(InstanceKlass* klass, TRAPS) {
  assert(klass->is_initialized(), "");
  if (TrainingData::have_data() && klass->is_shared()) {
    _training_replay_queue.push(klass, TrainingReplayQueue_lock, THREAD);
  }
}

// For TrainingReplayQueue
template<>
void CompilationPolicyUtils::Queue<InstanceKlass>::print_on(outputStream* st) {
  int pos = 0;
  for (QueueNode* cur = _head; cur != nullptr; cur = cur->next()) {
    ResourceMark rm;
    InstanceKlass* ik = cur->value();
    st->print_cr("%3d: " INTPTR_FORMAT " %s", ++pos, p2i(ik), ik->external_name());
  }
}

void CompilationPolicy::replay_training_at_init_loop(TRAPS) {
  while (!CompileBroker::is_compilation_disabled_forever()) {
    InstanceKlass* ik = _training_replay_queue.pop(TrainingReplayQueue_lock, THREAD);
    if (ik != nullptr) {
      replay_training_at_init_impl(ik, THREAD);
    }
  }
}

static inline CompLevel adjust_level_for_compilability_query(CompLevel comp_level) {
  if (comp_level == CompLevel_any) {
     if (CompilerConfig::is_c1_only()) {
       comp_level = CompLevel_simple;
     } else if (CompilerConfig::is_c2_or_jvmci_compiler_only()) {
       comp_level = CompLevel_full_optimization;
     }
  }
  return comp_level;
}

// Returns true if m is allowed to be compiled
bool CompilationPolicy::can_be_compiled(const methodHandle& m, int comp_level) {
  // allow any levels for WhiteBox
  assert(WhiteBoxAPI || comp_level == CompLevel_any || is_compile(comp_level), "illegal compilation level %d", comp_level);

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
  comp_level = adjust_level_for_compilability_query((CompLevel) comp_level);
  if (comp_level == CompLevel_any || is_compile(comp_level)) {
    return !m->is_not_compilable(comp_level);
  }
  return false;
}

// Returns true if m is allowed to be osr compiled
bool CompilationPolicy::can_be_osr_compiled(const methodHandle& m, int comp_level) {
  bool result = false;
  comp_level = adjust_level_for_compilability_query((CompLevel) comp_level);
  if (comp_level == CompLevel_any || is_compile(comp_level)) {
    result = !m->is_not_osr_compilable(comp_level);
  }
  return (result && can_be_compiled(m, comp_level));
}

bool CompilationPolicy::is_compilation_enabled() {
  // NOTE: CompileBroker::should_compile_new_jobs() checks for UseCompiler
  return CompileBroker::should_compile_new_jobs();
}

CompileTask* CompilationPolicy::select_task_helper(CompileQueue* compile_queue) {
  // Remove unloaded methods from the queue
  for (CompileTask* task = compile_queue->first(); task != nullptr; ) {
    CompileTask* next = task->next();
    if (task->is_unloaded()) {
      compile_queue->remove_and_mark_stale(task);
    }
    task = next;
  }
#if INCLUDE_JVMCI
  if (UseJVMCICompiler && !BackgroundCompilation) {
    /*
     * In blocking compilation mode, the CompileBroker will make
     * compilations submitted by a JVMCI compiler thread non-blocking. These
     * compilations should be scheduled after all blocking compilations
     * to service non-compiler related compilations sooner and reduce the
     * chance of such compilations timing out.
     */
    for (CompileTask* task = compile_queue->first(); task != nullptr; task = task->next()) {
      if (task->is_blocking()) {
        return task;
      }
    }
  }
#endif
  return compile_queue->first();
}

// Simple methods are as good being compiled with C1 as C2.
// Determine if a given method is such a case.
bool CompilationPolicy::is_trivial(const methodHandle& method) {
  if (method->is_accessor() ||
      method->is_constant_getter()) {
    return true;
  }
  return false;
}

bool CompilationPolicy::force_comp_at_level_simple(const methodHandle& method) {
  if (CompilationModeFlag::quick_internal()) {
#if INCLUDE_JVMCI
    if (UseJVMCICompiler) {
      AbstractCompiler* comp = CompileBroker::compiler(CompLevel_full_optimization);
      if (comp != nullptr && comp->is_jvmci() && ((JVMCICompiler*) comp)->force_comp_at_level_simple(method)) {
        return true;
      }
    }
#endif
  }
  return false;
}

CompLevel CompilationPolicy::comp_level(Method* method) {
  nmethod *nm = method->code();
  if (nm != nullptr && nm->is_in_use()) {
    return (CompLevel)nm->comp_level();
  }
  return CompLevel_none;
}

// Call and loop predicates determine whether a transition to a higher
// compilation level should be performed (pointers to predicate functions
// are passed to common()).
// Tier?LoadFeedback is basically a coefficient that determines of
// how many methods per compiler thread can be in the queue before
// the threshold values double.
class LoopPredicate : AllStatic {
public:
  static bool apply_scaled(const methodHandle& method, CompLevel cur_level, int i, int b, double scale) {
    double threshold_scaling;
    if (CompilerOracle::has_option_value(method, CompileCommandEnum::CompileThresholdScaling, threshold_scaling)) {
      scale *= threshold_scaling;
    }
    switch(cur_level) {
    case CompLevel_none:
    case CompLevel_limited_profile:
      return b >= Tier3BackEdgeThreshold * scale;
    case CompLevel_full_profile:
      return b >= Tier4BackEdgeThreshold * scale;
    default:
      return true;
    }
  }

  static bool apply(const methodHandle& method, CompLevel cur_level, int i, int b) {
    double k = 1;
    switch(cur_level) {
    case CompLevel_none:
    // Fall through
    case CompLevel_limited_profile: {
      k = CompilationPolicy::threshold_scale(CompLevel_full_profile, Tier3LoadFeedback);
      break;
    }
    case CompLevel_full_profile: {
      k = CompilationPolicy::threshold_scale(CompLevel_full_optimization, Tier4LoadFeedback);
      break;
    }
    default:
      return true;
    }
    return apply_scaled(method, cur_level, i, b, k);
  }
};

class CallPredicate : AllStatic {
public:
  static bool apply_scaled(const methodHandle& method, CompLevel cur_level, int i, int b, double scale) {
    double threshold_scaling;
    if (CompilerOracle::has_option_value(method, CompileCommandEnum::CompileThresholdScaling, threshold_scaling)) {
      scale *= threshold_scaling;
    }
    switch(cur_level) {
    case CompLevel_none:
    case CompLevel_limited_profile:
      return (i >= Tier3InvocationThreshold * scale) ||
             (i >= Tier3MinInvocationThreshold * scale && i + b >= Tier3CompileThreshold * scale);
    case CompLevel_full_profile:
      return (i >= Tier4InvocationThreshold * scale) ||
             (i >= Tier4MinInvocationThreshold * scale && i + b >= Tier4CompileThreshold * scale);
    default:
     return true;
    }
  }

  static bool apply(const methodHandle& method, CompLevel cur_level, int i, int b) {
    double k = 1;
    switch(cur_level) {
    case CompLevel_none:
    case CompLevel_limited_profile: {
      k = CompilationPolicy::threshold_scale(CompLevel_full_profile, Tier3LoadFeedback);
      break;
    }
    case CompLevel_full_profile: {
      k = CompilationPolicy::threshold_scale(CompLevel_full_optimization, Tier4LoadFeedback);
      break;
    }
    default:
      return true;
    }
    return apply_scaled(method, cur_level, i, b, k);
  }
};

double CompilationPolicy::threshold_scale(CompLevel level, int feedback_k) {
  int comp_count = compiler_count(level);
  if (comp_count > 0 && feedback_k > 0) {
    double queue_size = CompileBroker::queue_size(level);
    double k = (double)queue_size / ((double)feedback_k * (double)comp_count) + 1;

    // Increase C1 compile threshold when the code cache is filled more
    // than specified by IncreaseFirstTierCompileThresholdAt percentage.
    // The main intention is to keep enough free space for C2 compiled code
    // to achieve peak performance if the code cache is under stress.
    if (CompilerConfig::is_tiered() && !CompilationModeFlag::disable_intermediate() && is_c1_compile(level))  {
      double current_reverse_free_ratio = CodeCache::reverse_free_ratio();
      if (current_reverse_free_ratio > _increase_threshold_at_ratio) {
        k *= exp(current_reverse_free_ratio - _increase_threshold_at_ratio);
      }
    }
    return k;
  }
  return 1;
}

void CompilationPolicy::print_counters(const char* prefix, Method* m) {
  int invocation_count = m->invocation_count();
  int backedge_count = m->backedge_count();
  MethodData* mdh = m->method_data();
  int mdo_invocations = 0, mdo_backedges = 0;
  int mdo_invocations_start = 0, mdo_backedges_start = 0;
  if (mdh != nullptr) {
    mdo_invocations = mdh->invocation_count();
    mdo_backedges = mdh->backedge_count();
    mdo_invocations_start = mdh->invocation_count_start();
    mdo_backedges_start = mdh->backedge_count_start();
  }
  tty->print(" %stotal=%d,%d %smdo=%d(%d),%d(%d)", prefix,
      invocation_count, backedge_count, prefix,
      mdo_invocations, mdo_invocations_start,
      mdo_backedges, mdo_backedges_start);
  tty->print(" %smax levels=%d,%d", prefix,
      m->highest_comp_level(), m->highest_osr_comp_level());
}

void CompilationPolicy::print_training_data(const char* prefix, Method* method) {
  methodHandle m(Thread::current(), method);
  tty->print(" %smtd: ", prefix);
  MethodTrainingData* mtd = MethodTrainingData::find(m);
  if (mtd == nullptr) {
    tty->print("null");
  } else {
    MethodData* md = mtd->final_profile();
    tty->print("mdo=");
    if (md == nullptr) {
      tty->print("null");
    } else {
      int mdo_invocations = md->invocation_count();
      int mdo_backedges = md->backedge_count();
      int mdo_invocations_start = md->invocation_count_start();
      int mdo_backedges_start = md->backedge_count_start();
      tty->print("%d(%d), %d(%d)", mdo_invocations, mdo_invocations_start, mdo_backedges, mdo_backedges_start);
    }
    CompileTrainingData* ctd = mtd->last_toplevel_compile(CompLevel_full_optimization);
    tty->print(", deps=");
    if (ctd == nullptr) {
      tty->print("null");
    } else {
      tty->print("%d", ctd->init_deps_left());
    }
  }
}

// Print an event.
void CompilationPolicy::print_event(EventType type, Method* m, Method* im, int bci, CompLevel level) {
  bool inlinee_event = m != im;

  ttyLocker tty_lock;
  tty->print("%lf: [", os::elapsedTime());

  switch(type) {
  case CALL:
    tty->print("call");
    break;
  case LOOP:
    tty->print("loop");
    break;
  case COMPILE:
    tty->print("compile");
    break;
  case FORCE_COMPILE:
    tty->print("force-compile");
    break;
  case REMOVE_FROM_QUEUE:
    tty->print("remove-from-queue");
    break;
  case UPDATE_IN_QUEUE:
    tty->print("update-in-queue");
    break;
  case REPROFILE:
    tty->print("reprofile");
    break;
  case MAKE_NOT_ENTRANT:
    tty->print("make-not-entrant");
    break;
  default:
    tty->print("unknown");
  }

  tty->print(" level=%d ", level);

  ResourceMark rm;
  char *method_name = m->name_and_sig_as_C_string();
  tty->print("[%s", method_name);
  if (inlinee_event) {
    char *inlinee_name = im->name_and_sig_as_C_string();
    tty->print(" [%s]] ", inlinee_name);
  }
  else tty->print("] ");
  tty->print("@%d queues=%d,%d", bci, CompileBroker::queue_size(CompLevel_full_profile),
                                      CompileBroker::queue_size(CompLevel_full_optimization));

  tty->print(" rate=");
  if (m->prev_time() == 0) tty->print("n/a");
  else tty->print("%f", m->rate());

  tty->print(" k=%.2lf,%.2lf", threshold_scale(CompLevel_full_profile, Tier3LoadFeedback),
                               threshold_scale(CompLevel_full_optimization, Tier4LoadFeedback));

  if (type != COMPILE) {
    print_counters("", m);
    if (inlinee_event) {
      print_counters("inlinee ", im);
    }
    tty->print(" compilable=");
    bool need_comma = false;
    if (!m->is_not_compilable(CompLevel_full_profile)) {
      tty->print("c1");
      need_comma = true;
    }
    if (!m->is_not_osr_compilable(CompLevel_full_profile)) {
      if (need_comma) tty->print(",");
      tty->print("c1-osr");
      need_comma = true;
    }
    if (!m->is_not_compilable(CompLevel_full_optimization)) {
      if (need_comma) tty->print(",");
      tty->print("c2");
      need_comma = true;
    }
    if (!m->is_not_osr_compilable(CompLevel_full_optimization)) {
      if (need_comma) tty->print(",");
      tty->print("c2-osr");
    }
    tty->print(" status=");
    if (m->queued_for_compilation()) {
      tty->print("in-queue");
    } else tty->print("idle");
    print_training_data("", m);
    if (inlinee_event) {
      print_training_data("inlinee ", im);
    }
  }
  tty->print_cr("]");
}

void CompilationPolicy::initialize() {
  if (!CompilerConfig::is_interpreter_only()) {
    int count = CICompilerCount;
    bool c1_only = CompilerConfig::is_c1_only();
    bool c2_only = CompilerConfig::is_c2_or_jvmci_compiler_only();
    int min_count = (c1_only || c2_only) ? 1 : 2;

#ifdef _LP64
    // Turn on ergonomic compiler count selection
    if (FLAG_IS_DEFAULT(CICompilerCountPerCPU) && FLAG_IS_DEFAULT(CICompilerCount)) {
      FLAG_SET_DEFAULT(CICompilerCountPerCPU, true);
    }
    if (CICompilerCountPerCPU) {
      // Simple log n seems to grow too slowly for tiered, try something faster: log n * log log n
      int log_cpu = log2i(os::active_processor_count());
      int loglog_cpu = log2i(MAX2(log_cpu, 1));
      count = MAX2(log_cpu * loglog_cpu * 3 / 2, min_count);
      // Make sure there is enough space in the code cache to hold all the compiler buffers
      size_t c1_size = 0;
#ifdef COMPILER1
      c1_size = Compiler::code_buffer_size();
#endif
      size_t c2_size = 0;
#ifdef COMPILER2
      c2_size = C2Compiler::initial_code_buffer_size();
#endif
      size_t buffer_size = 0;
      if (c1_only) {
        buffer_size = c1_size;
      } else if (c2_only) {
        buffer_size = c2_size;
      } else {
        buffer_size = c1_size / 3 + 2 * c2_size / 3;
      }
      size_t max_count = (NonNMethodCodeHeapSize - (CodeCacheMinimumUseSpace DEBUG_ONLY(* 3))) / buffer_size;
      if ((size_t)count > max_count) {
        // Lower the compiler count such that all buffers fit into the code cache
        count = MAX2((int)max_count, min_count);
      }
      FLAG_SET_ERGO(CICompilerCount, count);
    }
#else
    // On 32-bit systems, the number of compiler threads is limited to 3.
    // On these systems, the virtual address space available to the JVM
    // is usually limited to 2-4 GB (the exact value depends on the platform).
    // As the compilers (especially C2) can consume a large amount of
    // memory, scaling the number of compiler threads with the number of
    // available cores can result in the exhaustion of the address space
    /// available to the VM and thus cause the VM to crash.
    if (FLAG_IS_DEFAULT(CICompilerCount)) {
      count = 3;
      FLAG_SET_ERGO(CICompilerCount, count);
    }
#endif // _LP64

    if (c1_only) {
      // No C2 compiler threads are needed
      set_c1_count(count);
    } else if (c2_only) {
      // No C1 compiler threads are needed
      set_c2_count(count);
    } else {
#if INCLUDE_JVMCI
      if (UseJVMCICompiler && UseJVMCINativeLibrary) {
        int libjvmci_count = MAX2((int) (count * JVMCINativeLibraryThreadFraction), 1);
        int c1_count = MAX2(count - libjvmci_count, 1);
        set_c2_count(libjvmci_count);
        set_c1_count(c1_count);
      } else
#endif
      {
        set_c1_count(MAX2(count / 3, 1));
        set_c2_count(MAX2(count - c1_count(), 1));
      }
    }
    assert(count == c1_count() + c2_count(), "inconsistent compiler thread count");
    set_increase_threshold_at_ratio();
  } else {
    // Interpreter mode creates no compilers
    FLAG_SET_ERGO(CICompilerCount, 0);
  }
  set_start_time(nanos_to_millis(os::javaTimeNanos()));
}


#ifdef ASSERT
bool CompilationPolicy::verify_level(CompLevel level) {
  if (TieredCompilation && level > TieredStopAtLevel) {
    return false;
  }
  // Check if there is a compiler to process the requested level
  if (!CompilerConfig::is_c1_enabled() && is_c1_compile(level)) {
    return false;
  }
  if (!CompilerConfig::is_c2_or_jvmci_compiler_enabled() && is_c2_compile(level)) {
    return false;
  }

  // Interpreter level is always valid.
  if (level == CompLevel_none) {
    return true;
  }
  if (CompilationModeFlag::normal()) {
    return true;
  } else if (CompilationModeFlag::quick_only()) {
    return level == CompLevel_simple;
  } else if (CompilationModeFlag::high_only()) {
    return level == CompLevel_full_optimization;
  } else if (CompilationModeFlag::high_only_quick_internal()) {
    return level == CompLevel_full_optimization || level == CompLevel_simple;
  }
  return false;
}
#endif


CompLevel CompilationPolicy::highest_compile_level() {
  CompLevel level = CompLevel_none;
  // Setup the maximum level available for the current compiler configuration.
  if (!CompilerConfig::is_interpreter_only()) {
    if (CompilerConfig::is_c2_or_jvmci_compiler_enabled()) {
      level = CompLevel_full_optimization;
    } else if (CompilerConfig::is_c1_enabled()) {
      if (CompilerConfig::is_c1_simple_only()) {
        level = CompLevel_simple;
      } else {
        level = CompLevel_full_profile;
      }
    }
  }
  // Clamp the maximum level with TieredStopAtLevel.
  if (TieredCompilation) {
    level = MIN2(level, (CompLevel) TieredStopAtLevel);
  }

  // Fix it up if after the clamping it has become invalid.
  // Bring it monotonically down depending on the next available level for
  // the compilation mode.
  if (!CompilationModeFlag::normal()) {
    // a) quick_only - levels 2,3,4 are invalid; levels -1,0,1 are valid;
    // b) high_only - levels 1,2,3 are invalid; levels -1,0,4 are valid;
    // c) high_only_quick_internal - levels 2,3 are invalid; levels -1,0,1,4 are valid.
    if (CompilationModeFlag::quick_only()) {
      if (level == CompLevel_limited_profile || level == CompLevel_full_profile || level == CompLevel_full_optimization) {
        level = CompLevel_simple;
      }
    } else if (CompilationModeFlag::high_only()) {
      if (level == CompLevel_simple || level == CompLevel_limited_profile || level == CompLevel_full_profile) {
        level = CompLevel_none;
      }
    } else if (CompilationModeFlag::high_only_quick_internal()) {
      if (level == CompLevel_limited_profile || level == CompLevel_full_profile) {
        level = CompLevel_simple;
      }
    }
  }

  assert(verify_level(level), "Invalid highest compilation level: %d", level);
  return level;
}

CompLevel CompilationPolicy::limit_level(CompLevel level) {
  level = MIN2(level, highest_compile_level());
  assert(verify_level(level), "Invalid compilation level: %d", level);
  return level;
}

CompLevel CompilationPolicy::initial_compile_level(const methodHandle& method) {
  CompLevel level = CompLevel_any;
  if (CompilationModeFlag::normal()) {
    level = CompLevel_full_profile;
  } else if (CompilationModeFlag::quick_only()) {
    level = CompLevel_simple;
  } else if (CompilationModeFlag::high_only()) {
    level = CompLevel_full_optimization;
  } else if (CompilationModeFlag::high_only_quick_internal()) {
    if (force_comp_at_level_simple(method)) {
      level = CompLevel_simple;
    } else {
      level = CompLevel_full_optimization;
    }
  }
  assert(level != CompLevel_any, "Unhandled compilation mode");
  return limit_level(level);
}

// Set carry flags on the counters if necessary
void CompilationPolicy::handle_counter_overflow(const methodHandle& method) {
  MethodCounters *mcs = method->method_counters();
  if (mcs != nullptr) {
    mcs->invocation_counter()->set_carry_on_overflow();
    mcs->backedge_counter()->set_carry_on_overflow();
  }
  MethodData* mdo = method->method_data();
  if (mdo != nullptr) {
    mdo->invocation_counter()->set_carry_on_overflow();
    mdo->backedge_counter()->set_carry_on_overflow();
  }
}

// Called with the queue locked and with at least one element
CompileTask* CompilationPolicy::select_task(CompileQueue* compile_queue, JavaThread* THREAD) {
  CompileTask *max_blocking_task = nullptr;
  CompileTask *max_task = nullptr;
  Method* max_method = nullptr;

  int64_t t = nanos_to_millis(os::javaTimeNanos());
  // Iterate through the queue and find a method with a maximum rate.
  for (CompileTask* task = compile_queue->first(); task != nullptr;) {
    CompileTask* next_task = task->next();
    // If a method was unloaded or has been stale for some time, remove it from the queue.
    // Blocking tasks and tasks submitted from whitebox API don't become stale
    if (task->is_unloaded()) {
      compile_queue->remove_and_mark_stale(task);
      task = next_task;
      continue;
    }
    if (task->is_blocking() && task->compile_reason() == CompileTask::Reason_Whitebox) {
      // CTW tasks, submitted as blocking Whitebox requests, do not participate in rate
      // selection and/or any level adjustments. Just return them in order.
      return task;
    }
    Method* method = task->method();
    methodHandle mh(THREAD, method);
    if (task->can_become_stale() && is_stale(t, TieredCompileTaskTimeout, mh) && !is_old(mh)) {
      if (PrintTieredEvents) {
        print_event(REMOVE_FROM_QUEUE, method, method, task->osr_bci(), (CompLevel) task->comp_level());
      }
      method->clear_queued_for_compilation();
      compile_queue->remove_and_mark_stale(task);
      task = next_task;
      continue;
    }
    update_rate(t, mh);
    if (max_task == nullptr || compare_methods(method, max_method)) {
      // Select a method with the highest rate
      max_task = task;
      max_method = method;
    }

    if (task->is_blocking()) {
      if (max_blocking_task == nullptr || compare_methods(method, max_blocking_task->method())) {
        max_blocking_task = task;
      }
    }

    task = next_task;
  }

  if (max_blocking_task != nullptr) {
    // In blocking compilation mode, the CompileBroker will make
    // compilations submitted by a JVMCI compiler thread non-blocking. These
    // compilations should be scheduled after all blocking compilations
    // to service non-compiler related compilations sooner and reduce the
    // chance of such compilations timing out.
    max_task = max_blocking_task;
    max_method = max_task->method();
  }

  methodHandle max_method_h(THREAD, max_method);

  if (max_task != nullptr && max_task->comp_level() == CompLevel_full_profile && TieredStopAtLevel > CompLevel_full_profile &&
      max_method != nullptr && is_method_profiled(max_method_h) && !Arguments::is_compiler_only()) {
    max_task->set_comp_level(CompLevel_limited_profile);

    if (CompileBroker::compilation_is_complete(max_method_h, max_task->osr_bci(), CompLevel_limited_profile)) {
      if (PrintTieredEvents) {
        print_event(REMOVE_FROM_QUEUE, max_method, max_method, max_task->osr_bci(), (CompLevel)max_task->comp_level());
      }
      compile_queue->remove_and_mark_stale(max_task);
      max_method->clear_queued_for_compilation();
      return nullptr;
    }

    if (PrintTieredEvents) {
      print_event(UPDATE_IN_QUEUE, max_method, max_method, max_task->osr_bci(), (CompLevel)max_task->comp_level());
    }
  }
  return max_task;
}

void CompilationPolicy::reprofile(ScopeDesc* trap_scope, bool is_osr) {
  for (ScopeDesc* sd = trap_scope;; sd = sd->sender()) {
    if (PrintTieredEvents) {
      print_event(REPROFILE, sd->method(), sd->method(), InvocationEntryBci, CompLevel_none);
    }
    MethodData* mdo = sd->method()->method_data();
    if (mdo != nullptr) {
      mdo->reset_start_counters();
    }
    if (sd->is_top()) break;
  }
}

nmethod* CompilationPolicy::event(const methodHandle& method, const methodHandle& inlinee,
                                      int branch_bci, int bci, CompLevel comp_level, nmethod* nm, TRAPS) {
  if (PrintTieredEvents) {
    print_event(bci == InvocationEntryBci ? CALL : LOOP, method(), inlinee(), bci, comp_level);
  }

#if INCLUDE_JVMCI
  if (EnableJVMCI && UseJVMCICompiler &&
      comp_level == CompLevel_full_optimization CDS_ONLY(&& !AOTLinkedClassBulkLoader::class_preloading_finished())) {
    return nullptr;
  }
#endif

  if (comp_level == CompLevel_none &&
      JvmtiExport::can_post_interpreter_events() &&
      THREAD->is_interp_only_mode()) {
    return nullptr;
  }
  if (ReplayCompiles) {
    // Don't trigger other compiles in testing mode
    return nullptr;
  }

  handle_counter_overflow(method);
  if (method() != inlinee()) {
    handle_counter_overflow(inlinee);
  }

  if (bci == InvocationEntryBci) {
    method_invocation_event(method, inlinee, comp_level, nm, THREAD);
  } else {
    // method == inlinee if the event originated in the main method
    method_back_branch_event(method, inlinee, bci, comp_level, nm, THREAD);
    // Check if event led to a higher level OSR compilation
    CompLevel expected_comp_level = MIN2(CompLevel_full_optimization, static_cast<CompLevel>(comp_level + 1));
    if (!CompilationModeFlag::disable_intermediate() && inlinee->is_not_osr_compilable(expected_comp_level)) {
      // It's not possible to reach the expected level so fall back to simple.
      expected_comp_level = CompLevel_simple;
    }
    CompLevel max_osr_level = static_cast<CompLevel>(inlinee->highest_osr_comp_level());
    if (max_osr_level >= expected_comp_level) { // fast check to avoid locking in a typical scenario
      nmethod* osr_nm = inlinee->lookup_osr_nmethod_for(bci, expected_comp_level, false);
      assert(osr_nm == nullptr || osr_nm->comp_level() >= expected_comp_level, "lookup_osr_nmethod_for is broken");
      if (osr_nm != nullptr && osr_nm->comp_level() != comp_level) {
        // Perform OSR with new nmethod
        return osr_nm;
      }
    }
  }
  return nullptr;
}

// Check if the method can be compiled, change level if necessary
void CompilationPolicy::compile(const methodHandle& mh, int bci, CompLevel level, TRAPS) {
  assert(verify_level(level), "Invalid compilation level requested: %d", level);

  if (level == CompLevel_none) {
    if (mh->has_compiled_code()) {
      // Happens when we switch to interpreter to profile.
      MutexLocker ml(Compile_lock);
      NoSafepointVerifier nsv;
      if (mh->has_compiled_code()) {
        mh->code()->make_not_used();
      }
      // Deoptimize immediately (we don't have to wait for a compile).
      JavaThread* jt = THREAD;
      RegisterMap map(jt,
                      RegisterMap::UpdateMap::skip,
                      RegisterMap::ProcessFrames::include,
                      RegisterMap::WalkContinuation::skip);
      frame fr = jt->last_frame().sender(&map);
      Deoptimization::deoptimize_frame(jt, fr.id());
    }
    return;
  }

  if (!CompilationModeFlag::disable_intermediate()) {
    // Check if the method can be compiled. If it cannot be compiled with C1, continue profiling
    // in the interpreter and then compile with C2 (the transition function will request that,
    // see common() ). If the method cannot be compiled with C2 but still can with C1, compile it with
    // pure C1.
    if ((bci == InvocationEntryBci && !can_be_compiled(mh, level))) {
      if (level == CompLevel_full_optimization && can_be_compiled(mh, CompLevel_simple)) {
        compile(mh, bci, CompLevel_simple, THREAD);
      }
      return;
    }
    if ((bci != InvocationEntryBci && !can_be_osr_compiled(mh, level))) {
      if (level == CompLevel_full_optimization && can_be_osr_compiled(mh, CompLevel_simple)) {
        nmethod* osr_nm = mh->lookup_osr_nmethod_for(bci, CompLevel_simple, false);
        if (osr_nm != nullptr && osr_nm->comp_level() > CompLevel_simple) {
          // Invalidate the existing OSR nmethod so that a compile at CompLevel_simple is permitted.
          osr_nm->make_not_entrant(nmethod::InvalidationReason::OSR_INVALIDATION_FOR_COMPILING_WITH_C1);
        }
        compile(mh, bci, CompLevel_simple, THREAD);
      }
      return;
    }
  }
  if (bci != InvocationEntryBci && mh->is_not_osr_compilable(level)) {
    return;
  }
  if (!CompileBroker::compilation_is_in_queue(mh)) {
    if (PrintTieredEvents) {
      print_event(COMPILE, mh(), mh(), bci, level);
    }
    int hot_count = (bci == InvocationEntryBci) ? mh->invocation_count() : mh->backedge_count();
    update_rate(nanos_to_millis(os::javaTimeNanos()), mh);
    CompileBroker::compile_method(mh, bci, level, hot_count, CompileTask::Reason_Tiered, THREAD);
  }
}

// update_rate() is called from select_task() while holding a compile queue lock.
void CompilationPolicy::update_rate(int64_t t, const methodHandle& method) {
  // Skip update if counters are absent.
  // Can't allocate them since we are holding compile queue lock.
  if (method->method_counters() == nullptr)  return;

  if (is_old(method)) {
    // We don't remove old methods from the queue,
    // so we can just zero the rate.
    method->set_rate(0);
    return;
  }

  // We don't update the rate if we've just came out of a safepoint.
  // delta_s is the time since last safepoint in milliseconds.
  int64_t delta_s = t - SafepointTracing::end_of_last_safepoint_ms();
  int64_t delta_t = t - (method->prev_time() != 0 ? method->prev_time() : start_time()); // milliseconds since the last measurement
  // How many events were there since the last time?
  int event_count = method->invocation_count() + method->backedge_count();
  int delta_e = event_count - method->prev_event_count();

  // We should be running for at least 1ms.
  if (delta_s >= TieredRateUpdateMinTime) {
    // And we must've taken the previous point at least 1ms before.
    if (delta_t >= TieredRateUpdateMinTime && delta_e > 0) {
      method->set_prev_time(t);
      method->set_prev_event_count(event_count);
      method->set_rate((float)delta_e / (float)delta_t); // Rate is events per millisecond
    } else {
      if (delta_t > TieredRateUpdateMaxTime && delta_e == 0) {
        // If nothing happened for 25ms, zero the rate. Don't modify prev values.
        method->set_rate(0);
      }
    }
  }
}

// Check if this method has been stale for a given number of milliseconds.
// See select_task().
bool CompilationPolicy::is_stale(int64_t t, int64_t timeout, const methodHandle& method) {
  int64_t delta_s = t - SafepointTracing::end_of_last_safepoint_ms();
  int64_t delta_t = t - method->prev_time();
  if (delta_t > timeout && delta_s > timeout) {
    int event_count = method->invocation_count() + method->backedge_count();
    int delta_e = event_count - method->prev_event_count();
    // Return true if there were no events.
    return delta_e == 0;
  }
  return false;
}

// We don't remove old methods from the compile queue even if they have
// very low activity. See select_task().
bool CompilationPolicy::is_old(const methodHandle& method) {
  int i = method->invocation_count();
  int b = method->backedge_count();
  double k = TieredOldPercentage / 100.0;

  return CallPredicate::apply_scaled(method, CompLevel_none, i, b, k) || LoopPredicate::apply_scaled(method, CompLevel_none, i, b, k);
}

double CompilationPolicy::weight(Method* method) {
  return (double)(method->rate() + 1) * (method->invocation_count() + 1) * (method->backedge_count() + 1);
}

// Apply heuristics and return true if x should be compiled before y
bool CompilationPolicy::compare_methods(Method* x, Method* y) {
  if (x->highest_comp_level() > y->highest_comp_level()) {
    // recompilation after deopt
    return true;
  } else
    if (x->highest_comp_level() == y->highest_comp_level()) {
      if (weight(x) > weight(y)) {
        return true;
      }
    }
  return false;
}

// Is method profiled enough?
bool CompilationPolicy::is_method_profiled(const methodHandle& method) {
  MethodData* mdo = method->method_data();
  if (mdo != nullptr) {
    int i = mdo->invocation_count_delta();
    int b = mdo->backedge_count_delta();
    return CallPredicate::apply_scaled(method, CompLevel_full_profile, i, b, 1);
  }
  return false;
}


// Determine is a method is mature.
bool CompilationPolicy::is_mature(MethodData* mdo) {
  if (Arguments::is_compiler_only()) {
    // Always report profiles as immature with -Xcomp
    return false;
  }
  methodHandle mh(Thread::current(), mdo->method());
  if (mdo != nullptr) {
    int i = mdo->invocation_count();
    int b = mdo->backedge_count();
    double k = ProfileMaturityPercentage / 100.0;
    return CallPredicate::apply_scaled(mh, CompLevel_full_profile, i, b, k) || LoopPredicate::apply_scaled(mh, CompLevel_full_profile, i, b, k);
  }
  return false;
}

// If a method is old enough and is still in the interpreter we would want to
// start profiling without waiting for the compiled method to arrive.
// We also take the load on compilers into the account.
bool CompilationPolicy::should_create_mdo(const methodHandle& method, CompLevel cur_level) {
  if (cur_level != CompLevel_none || force_comp_at_level_simple(method) || CompilationModeFlag::quick_only() || !ProfileInterpreter) {
    return false;
  }

  if (TrainingData::have_data()) {
    MethodTrainingData* mtd = MethodTrainingData::find_fast(method);
    if (mtd != nullptr && mtd->saw_level(CompLevel_full_optimization)) {
      return true;
    }
  }

  if (is_old(method)) {
    return true;
  }

  int i = method->invocation_count();
  int b = method->backedge_count();
  double k = Tier0ProfilingStartPercentage / 100.0;

  // If the top level compiler is not keeping up, delay profiling.
  if (CompileBroker::queue_size(CompLevel_full_optimization) <= Tier0Delay * compiler_count(CompLevel_full_optimization)) {
    return CallPredicate::apply_scaled(method, CompLevel_none, i, b, k) || LoopPredicate::apply_scaled(method, CompLevel_none, i, b, k);
  }
  return false;
}

// Inlining control: if we're compiling a profiled method with C1 and the callee
// is known to have OSRed in a C2 version, don't inline it.
bool CompilationPolicy::should_not_inline(ciEnv* env, ciMethod* callee) {
  CompLevel comp_level = (CompLevel)env->comp_level();
  if (comp_level == CompLevel_full_profile ||
      comp_level == CompLevel_limited_profile) {
    return callee->highest_osr_comp_level() == CompLevel_full_optimization;
  }
  return false;
}

// Create MDO if necessary.
void CompilationPolicy::create_mdo(const methodHandle& mh, JavaThread* THREAD) {
  if (mh->is_native() ||
      mh->is_abstract() ||
      mh->is_accessor() ||
      mh->is_constant_getter()) {
    return;
  }
  if (mh->method_data() == nullptr) {
    Method::build_profiling_method_data(mh, CHECK_AND_CLEAR);
  }
  if (ProfileInterpreter && THREAD->has_last_Java_frame()) {
    MethodData* mdo = mh->method_data();
    if (mdo != nullptr) {
      frame last_frame = THREAD->last_frame();
      if (last_frame.is_interpreted_frame() && mh == last_frame.interpreter_frame_method()) {
        int bci = last_frame.interpreter_frame_bci();
        address dp = mdo->bci_to_dp(bci);
        last_frame.interpreter_frame_set_mdp(dp);
      }
    }
  }
}

CompLevel CompilationPolicy::trained_transition_from_none(const methodHandle& method, CompLevel cur_level, MethodTrainingData* mtd, JavaThread* THREAD) {
  precond(mtd != nullptr);
  precond(cur_level == CompLevel_none);

  if (mtd->only_inlined() && !mtd->saw_level(CompLevel_full_optimization)) {
    return CompLevel_none;
  }

  bool training_has_profile = (mtd->final_profile() != nullptr);
  if (mtd->saw_level(CompLevel_full_optimization) && !training_has_profile) {
    return CompLevel_full_profile;
  }

  CompLevel highest_training_level = static_cast<CompLevel>(mtd->highest_top_level());
  switch (highest_training_level) {
    case CompLevel_limited_profile:
    case CompLevel_full_profile:
      return CompLevel_limited_profile;
    case CompLevel_simple:
      return CompLevel_simple;
    case CompLevel_none:
      return CompLevel_none;
    default:
      break;
  }

  // Now handle the case of level 4.
  assert(highest_training_level == CompLevel_full_optimization, "Unexpected compilation level: %d", highest_training_level);
  if (!training_has_profile) {
    // The method was a part of a level 4 compile, but don't have a stored profile,
    // we need to profile it.
    return CompLevel_full_profile;
  }
  const bool deopt = (static_cast<CompLevel>(method->highest_comp_level()) == CompLevel_full_optimization);
  // If we deopted, then we reprofile
  if (deopt && !is_method_profiled(method)) {
    return CompLevel_full_profile;
  }

  CompileTrainingData* ctd = mtd->last_toplevel_compile(CompLevel_full_optimization);
  assert(ctd != nullptr, "Should have CTD for CompLevel_full_optimization");
  // With SkipTier2IfPossible and all deps satisfied, go to level 4 immediately
  if (SkipTier2IfPossible && ctd->init_deps_left() == 0) {
    if (method->method_data() == nullptr) {
      create_mdo(method, THREAD);
    }
    return CompLevel_full_optimization;
  }

  // Otherwise go to level 2
  return CompLevel_limited_profile;
}


CompLevel CompilationPolicy::trained_transition_from_limited_profile(const methodHandle& method, CompLevel cur_level, MethodTrainingData* mtd, JavaThread* THREAD) {
  precond(mtd != nullptr);
  precond(cur_level == CompLevel_limited_profile);

  // One of the main reasons that we can get here is that we're waiting for the stored C2 code to become ready.

  // But first, check if we have a saved profile
  bool training_has_profile = (mtd->final_profile() != nullptr);
  if (!training_has_profile) {
    return CompLevel_full_profile;
  }


  assert(training_has_profile, "Have to have a profile to be here");
  // Check if the method is ready
  CompileTrainingData* ctd = mtd->last_toplevel_compile(CompLevel_full_optimization);
  if (ctd != nullptr && ctd->init_deps_left() == 0) {
    if (method->method_data() == nullptr) {
      create_mdo(method, THREAD);
    }
    return CompLevel_full_optimization;
  }

  // Otherwise stay at the current level
  return CompLevel_limited_profile;
}


CompLevel CompilationPolicy::trained_transition_from_full_profile(const methodHandle& method, CompLevel cur_level, MethodTrainingData* mtd, JavaThread* THREAD) {
  precond(mtd != nullptr);
  precond(cur_level == CompLevel_full_profile);

  CompLevel highest_training_level = static_cast<CompLevel>(mtd->highest_top_level());
  // We have method at the full profile level and we also know that it's possibly an important method.
  if (highest_training_level == CompLevel_full_optimization && !mtd->only_inlined()) {
    // Check if it is adequately profiled
    if (is_method_profiled(method)) {
      return CompLevel_full_optimization;
    }
  }

  // Otherwise stay at the current level
  return CompLevel_full_profile;
}

CompLevel CompilationPolicy::trained_transition(const methodHandle& method, CompLevel cur_level, MethodTrainingData* mtd, JavaThread* THREAD) {
  precond(MethodTrainingData::have_data());

  // If there is no training data recorded for this method, bail out.
  if (mtd == nullptr) {
    return cur_level;
  }

  CompLevel next_level = cur_level;
  switch(cur_level) {
    default: break;
    case CompLevel_none:
      next_level = trained_transition_from_none(method, cur_level, mtd, THREAD);
      break;
    case CompLevel_limited_profile:
      next_level = trained_transition_from_limited_profile(method, cur_level, mtd, THREAD);
      break;
    case CompLevel_full_profile:
      next_level = trained_transition_from_full_profile(method, cur_level, mtd, THREAD);
      break;
  }

  // We don't have any special strategies for the C2-only compilation modes, so just fix up the levels for now.
  if (CompilationModeFlag::high_only_quick_internal() && CompLevel_simple < next_level && next_level < CompLevel_full_optimization) {
    return CompLevel_none;
  }
  if (CompilationModeFlag::high_only() && next_level < CompLevel_full_optimization) {
    return CompLevel_none;
  }
  return (cur_level != next_level) ? limit_level(next_level) : cur_level;
}

/*
 * Method states:
 *   0 - interpreter (CompLevel_none)
 *   1 - pure C1 (CompLevel_simple)
 *   2 - C1 with invocation and backedge counting (CompLevel_limited_profile)
 *   3 - C1 with full profiling (CompLevel_full_profile)
 *   4 - C2 or Graal (CompLevel_full_optimization)
 *
 * Common state transition patterns:
 * a. 0 -> 3 -> 4.
 *    The most common path. But note that even in this straightforward case
 *    profiling can start at level 0 and finish at level 3.
 *
 * b. 0 -> 2 -> 3 -> 4.
 *    This case occurs when the load on C2 is deemed too high. So, instead of transitioning
 *    into state 3 directly and over-profiling while a method is in the C2 queue we transition to
 *    level 2 and wait until the load on C2 decreases. This path is disabled for OSRs.
 *
 * c. 0 -> (3->2) -> 4.
 *    In this case we enqueue a method for compilation at level 3, but the C1 queue is long enough
 *    to enable the profiling to fully occur at level 0. In this case we change the compilation level
 *    of the method to 2 while the request is still in-queue, because it'll allow it to run much faster
 *    without full profiling while c2 is compiling.
 *
 * d. 0 -> 3 -> 1 or 0 -> 2 -> 1.
 *    After a method was once compiled with C1 it can be identified as trivial and be compiled to
 *    level 1. These transition can also occur if a method can't be compiled with C2 but can with C1.
 *
 * e. 0 -> 4.
 *    This can happen if a method fails C1 compilation (it will still be profiled in the interpreter)
 *    or because of a deopt that didn't require reprofiling (compilation won't happen in this case because
 *    the compiled version already exists).
 *
 * Note that since state 0 can be reached from any other state via deoptimization different loops
 * are possible.
 *
 */

// Common transition function. Given a predicate determines if a method should transition to another level.
template<typename Predicate>
CompLevel CompilationPolicy::common(const methodHandle& method, CompLevel cur_level, JavaThread* THREAD, bool disable_feedback) {
  CompLevel next_level = cur_level;

  if (force_comp_at_level_simple(method)) {
    next_level = CompLevel_simple;
  } else if (is_trivial(method) || method->is_native()) {
    // We do not care if there is profiling data for these methods, throw them to compiler.
    next_level = CompilationModeFlag::disable_intermediate() ? CompLevel_full_optimization : CompLevel_simple;
  } else if (MethodTrainingData::have_data()) {
    MethodTrainingData* mtd = MethodTrainingData::find_fast(method);
    if (mtd == nullptr) {
      // We haven't see compilations of this method in training. It's either very cold or the behavior changed.
      // Feed it to the standard TF with no profiling delay.
      next_level = standard_transition<Predicate>(method, cur_level, false /*delay_profiling*/, disable_feedback);
    } else {
      next_level = trained_transition(method, cur_level, mtd, THREAD);
      if (cur_level == next_level) {
        // trained_transtion() is going to return the same level if no startup/warmup optimizations apply.
        // In order to catch possible pathologies due to behavior change we feed the event to the regular
        // TF but with profiling delay.
        next_level = standard_transition<Predicate>(method, cur_level, true /*delay_profiling*/, disable_feedback);
      }
    }
  } else {
    next_level = standard_transition<Predicate>(method, cur_level, false /*delay_profiling*/, disable_feedback);
  }
  return (next_level != cur_level) ? limit_level(next_level) : next_level;
}


template<typename Predicate>
CompLevel CompilationPolicy::standard_transition(const methodHandle& method, CompLevel cur_level, bool delay_profiling, bool disable_feedback) {
  CompLevel next_level = cur_level;
  switch(cur_level) {
  default: break;
  case CompLevel_none:
    next_level = transition_from_none<Predicate>(method, cur_level, delay_profiling, disable_feedback);
    break;
  case CompLevel_limited_profile:
    next_level = transition_from_limited_profile<Predicate>(method, cur_level, delay_profiling, disable_feedback);
    break;
  case CompLevel_full_profile:
    next_level = transition_from_full_profile<Predicate>(method, cur_level);
    break;
  }
  return next_level;
}

template<typename Predicate>
CompLevel CompilationPolicy::transition_from_none(const methodHandle& method, CompLevel cur_level, bool delay_profiling, bool disable_feedback) {
  precond(cur_level == CompLevel_none);
  CompLevel next_level = cur_level;
  int i = method->invocation_count();
  int b = method->backedge_count();
  double scale = delay_profiling ? Tier0ProfileDelayFactor : 1.0;
  // If we were at full profile level, would we switch to full opt?
  if (transition_from_full_profile<Predicate>(method, CompLevel_full_profile) == CompLevel_full_optimization) {
    next_level = CompLevel_full_optimization;
  } else if (!CompilationModeFlag::disable_intermediate() && Predicate::apply_scaled(method, cur_level, i, b, scale)) {
    // C1-generated fully profiled code is about 30% slower than the limited profile
    // code that has only invocation and backedge counters. The observation is that
    // if C2 queue is large enough we can spend too much time in the fully profiled code
    // while waiting for C2 to pick the method from the queue. To alleviate this problem
    // we introduce a feedback on the C2 queue size. If the C2 queue is sufficiently long
    // we choose to compile a limited profiled version and then recompile with full profiling
    // when the load on C2 goes down.
    if (delay_profiling || (!disable_feedback && CompileBroker::queue_size(CompLevel_full_optimization) > Tier3DelayOn * compiler_count(CompLevel_full_optimization))) {
      next_level = CompLevel_limited_profile;
    } else {
      next_level = CompLevel_full_profile;
    }
  }
  return next_level;
}

template<typename Predicate>
CompLevel CompilationPolicy::transition_from_full_profile(const methodHandle& method, CompLevel cur_level) {
  precond(cur_level == CompLevel_full_profile);
  CompLevel next_level = cur_level;
  MethodData* mdo = method->method_data();
  if (mdo != nullptr) {
    if (mdo->would_profile() || CompilationModeFlag::disable_intermediate()) {
      int mdo_i = mdo->invocation_count_delta();
      int mdo_b = mdo->backedge_count_delta();
      if (Predicate::apply(method, cur_level, mdo_i, mdo_b)) {
        next_level = CompLevel_full_optimization;
      }
    } else {
      next_level = CompLevel_full_optimization;
    }
  }
  return next_level;
}

template<typename Predicate>
CompLevel CompilationPolicy::transition_from_limited_profile(const methodHandle& method, CompLevel cur_level, bool delay_profiling, bool disable_feedback) {
  precond(cur_level == CompLevel_limited_profile);
  CompLevel next_level = cur_level;
  int i = method->invocation_count();
  int b = method->backedge_count();
  double scale = delay_profiling ? Tier2ProfileDelayFactor : 1.0;
  MethodData* mdo = method->method_data();
  if (mdo != nullptr) {
    if (mdo->would_profile()) {
      if (disable_feedback || (CompileBroker::queue_size(CompLevel_full_optimization) <=
                              Tier3DelayOff * compiler_count(CompLevel_full_optimization) &&
                              Predicate::apply_scaled(method, cur_level, i, b, scale))) {
        next_level = CompLevel_full_profile;
      }
    } else {
      next_level = CompLevel_full_optimization;
    }
  } else {
    // If there is no MDO we need to profile
    if (disable_feedback || (CompileBroker::queue_size(CompLevel_full_optimization) <=
                            Tier3DelayOff * compiler_count(CompLevel_full_optimization) &&
                            Predicate::apply_scaled(method, cur_level, i, b, scale))) {
      next_level = CompLevel_full_profile;
    }
  }
  if (next_level == CompLevel_full_profile && is_method_profiled(method)) {
    next_level = CompLevel_full_optimization;
  }
  return next_level;
}


// Determine if a method should be compiled with a normal entry point at a different level.
CompLevel CompilationPolicy::call_event(const methodHandle& method, CompLevel cur_level, JavaThread* THREAD) {
  CompLevel osr_level = MIN2((CompLevel) method->highest_osr_comp_level(), common<LoopPredicate>(method, cur_level, THREAD, true));
  CompLevel next_level = common<CallPredicate>(method, cur_level, THREAD, !TrainingData::have_data() && is_old(method));

  // If OSR method level is greater than the regular method level, the levels should be
  // equalized by raising the regular method level in order to avoid OSRs during each
  // invocation of the method.
  if (osr_level == CompLevel_full_optimization && cur_level == CompLevel_full_profile) {
    MethodData* mdo = method->method_data();
    guarantee(mdo != nullptr, "MDO should not be nullptr");
    if (mdo->invocation_count() >= 1) {
      next_level = CompLevel_full_optimization;
    }
  } else {
    next_level = MAX2(osr_level, next_level);
  }
#if INCLUDE_JVMCI
  if (EnableJVMCI && UseJVMCICompiler &&
      next_level == CompLevel_full_optimization CDS_ONLY(&& !AOTLinkedClassBulkLoader::class_preloading_finished())) {
    next_level = cur_level;
  }
#endif
  return next_level;
}

// Determine if we should do an OSR compilation of a given method.
CompLevel CompilationPolicy::loop_event(const methodHandle& method, CompLevel cur_level, JavaThread* THREAD) {
  CompLevel next_level = common<LoopPredicate>(method, cur_level, THREAD, true);
  if (cur_level == CompLevel_none) {
    // If there is a live OSR method that means that we deopted to the interpreter
    // for the transition.
    CompLevel osr_level = MIN2((CompLevel)method->highest_osr_comp_level(), next_level);
    if (osr_level > CompLevel_none) {
      return osr_level;
    }
  }
  return next_level;
}

// Handle the invocation event.
void CompilationPolicy::method_invocation_event(const methodHandle& mh, const methodHandle& imh,
                                                      CompLevel level, nmethod* nm, TRAPS) {
  if (should_create_mdo(mh, level)) {
    create_mdo(mh, THREAD);
  }
  CompLevel next_level = call_event(mh, level, THREAD);
  if (next_level != level) {
    if (is_compilation_enabled() && !CompileBroker::compilation_is_in_queue(mh)) {
      compile(mh, InvocationEntryBci, next_level, THREAD);
    }
  }
}

// Handle the back branch event. Notice that we can compile the method
// with a regular entry from here.
void CompilationPolicy::method_back_branch_event(const methodHandle& mh, const methodHandle& imh,
                                                     int bci, CompLevel level, nmethod* nm, TRAPS) {
  if (should_create_mdo(mh, level)) {
    create_mdo(mh, THREAD);
  }
  // Check if MDO should be created for the inlined method
  if (should_create_mdo(imh, level)) {
    create_mdo(imh, THREAD);
  }

  if (is_compilation_enabled()) {
    CompLevel next_osr_level = loop_event(imh, level, THREAD);
    CompLevel max_osr_level = (CompLevel)imh->highest_osr_comp_level();
    // At the very least compile the OSR version
    if (!CompileBroker::compilation_is_in_queue(imh) && (next_osr_level != level)) {
      compile(imh, bci, next_osr_level, CHECK);
    }

    // Use loop event as an opportunity to also check if there's been
    // enough calls.
    CompLevel cur_level, next_level;
    if (mh() != imh()) { // If there is an enclosing method
      {
        guarantee(nm != nullptr, "Should have nmethod here");
        cur_level = comp_level(mh());
        next_level = call_event(mh, cur_level, THREAD);

        if (max_osr_level == CompLevel_full_optimization) {
          // The inlinee OSRed to full opt, we need to modify the enclosing method to avoid deopts
          bool make_not_entrant = false;
          if (nm->is_osr_method()) {
            // This is an osr method, just make it not entrant and recompile later if needed
            make_not_entrant = true;
          } else {
            if (next_level != CompLevel_full_optimization) {
              // next_level is not full opt, so we need to recompile the
              // enclosing method without the inlinee
              cur_level = CompLevel_none;
              make_not_entrant = true;
            }
          }
          if (make_not_entrant) {
            if (PrintTieredEvents) {
              int osr_bci = nm->is_osr_method() ? nm->osr_entry_bci() : InvocationEntryBci;
              print_event(MAKE_NOT_ENTRANT, mh(), mh(), osr_bci, level);
            }
            nm->make_not_entrant(nmethod::InvalidationReason::OSR_INVALIDATION_BACK_BRANCH);
          }
        }
        // Fix up next_level if necessary to avoid deopts
        if (next_level == CompLevel_limited_profile && max_osr_level == CompLevel_full_profile) {
          next_level = CompLevel_full_profile;
        }
        if (cur_level != next_level) {
          if (!CompileBroker::compilation_is_in_queue(mh)) {
            compile(mh, InvocationEntryBci, next_level, THREAD);
          }
        }
      }
    } else {
      cur_level = comp_level(mh());
      next_level = call_event(mh, cur_level, THREAD);
      if (next_level != cur_level) {
        if (!CompileBroker::compilation_is_in_queue(mh)) {
          compile(mh, InvocationEntryBci, next_level, THREAD);
        }
      }
    }
  }
}

