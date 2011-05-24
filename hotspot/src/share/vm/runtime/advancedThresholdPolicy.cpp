/*
 * Copyright (c) 2010, 2011, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/advancedThresholdPolicy.hpp"
#include "runtime/simpleThresholdPolicy.inline.hpp"

#ifdef TIERED
// Print an event.
void AdvancedThresholdPolicy::print_specific(EventType type, methodHandle mh, methodHandle imh,
                                             int bci, CompLevel level) {
  tty->print(" rate: ");
  if (mh->prev_time() == 0) tty->print("n/a");
  else tty->print("%f", mh->rate());

  tty->print(" k: %.2lf,%.2lf", threshold_scale(CompLevel_full_profile, Tier3LoadFeedback),
                                threshold_scale(CompLevel_full_optimization, Tier4LoadFeedback));

}

void AdvancedThresholdPolicy::initialize() {
  // Turn on ergonomic compiler count selection
  if (FLAG_IS_DEFAULT(CICompilerCountPerCPU) && FLAG_IS_DEFAULT(CICompilerCount)) {
    FLAG_SET_DEFAULT(CICompilerCountPerCPU, true);
  }
  int count = CICompilerCount;
  if (CICompilerCountPerCPU) {
    // Simple log n seems to grow too slowly for tiered, try something faster: log n * log log n
    int log_cpu = log2_intptr(os::active_processor_count());
    int loglog_cpu = log2_intptr(MAX2(log_cpu, 1));
    count = MAX2(log_cpu * loglog_cpu, 1) * 3 / 2;
  }

  set_c1_count(MAX2(count / 3, 1));
  set_c2_count(MAX2(count - count / 3, 1));

  // Some inlining tuning
#ifdef X86
  if (FLAG_IS_DEFAULT(InlineSmallCode)) {
    FLAG_SET_DEFAULT(InlineSmallCode, 2000);
  }
#endif

#ifdef SPARC
  if (FLAG_IS_DEFAULT(InlineSmallCode)) {
    FLAG_SET_DEFAULT(InlineSmallCode, 2500);
  }
#endif


  set_start_time(os::javaTimeMillis());
}

// update_rate() is called from select_task() while holding a compile queue lock.
void AdvancedThresholdPolicy::update_rate(jlong t, methodOop m) {
  if (is_old(m)) {
    // We don't remove old methods from the queue,
    // so we can just zero the rate.
    m->set_rate(0);
    return;
  }

  // We don't update the rate if we've just came out of a safepoint.
  // delta_s is the time since last safepoint in milliseconds.
  jlong delta_s = t - SafepointSynchronize::end_of_last_safepoint();
  jlong delta_t = t - (m->prev_time() != 0 ? m->prev_time() : start_time()); // milliseconds since the last measurement
  // How many events were there since the last time?
  int event_count = m->invocation_count() + m->backedge_count();
  int delta_e = event_count - m->prev_event_count();

  // We should be running for at least 1ms.
  if (delta_s >= TieredRateUpdateMinTime) {
    // And we must've taken the previous point at least 1ms before.
    if (delta_t >= TieredRateUpdateMinTime && delta_e > 0) {
      m->set_prev_time(t);
      m->set_prev_event_count(event_count);
      m->set_rate((float)delta_e / (float)delta_t); // Rate is events per millisecond
    } else
      if (delta_t > TieredRateUpdateMaxTime && delta_e == 0) {
        // If nothing happened for 25ms, zero the rate. Don't modify prev values.
        m->set_rate(0);
      }
  }
}

// Check if this method has been stale from a given number of milliseconds.
// See select_task().
bool AdvancedThresholdPolicy::is_stale(jlong t, jlong timeout, methodOop m) {
  jlong delta_s = t - SafepointSynchronize::end_of_last_safepoint();
  jlong delta_t = t - m->prev_time();
  if (delta_t > timeout && delta_s > timeout) {
    int event_count = m->invocation_count() + m->backedge_count();
    int delta_e = event_count - m->prev_event_count();
    // Return true if there were no events.
    return delta_e == 0;
  }
  return false;
}

// We don't remove old methods from the compile queue even if they have
// very low activity. See select_task().
bool AdvancedThresholdPolicy::is_old(methodOop method) {
  return method->invocation_count() > 50000 || method->backedge_count() > 500000;
}

double AdvancedThresholdPolicy::weight(methodOop method) {
  return (method->rate() + 1) * ((method->invocation_count() + 1) *  (method->backedge_count() + 1));
}

// Apply heuristics and return true if x should be compiled before y
bool AdvancedThresholdPolicy::compare_methods(methodOop x, methodOop y) {
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
bool AdvancedThresholdPolicy::is_method_profiled(methodOop method) {
  methodDataOop mdo = method->method_data();
  if (mdo != NULL) {
    int i = mdo->invocation_count_delta();
    int b = mdo->backedge_count_delta();
    return call_predicate_helper<CompLevel_full_profile>(i, b, 1);
  }
  return false;
}

// Called with the queue locked and with at least one element
CompileTask* AdvancedThresholdPolicy::select_task(CompileQueue* compile_queue) {
  CompileTask *max_task = NULL;
  methodOop max_method;
  jlong t = os::javaTimeMillis();
  // Iterate through the queue and find a method with a maximum rate.
  for (CompileTask* task = compile_queue->first(); task != NULL;) {
    CompileTask* next_task = task->next();
    methodOop method = (methodOop)JNIHandles::resolve(task->method_handle());
    methodDataOop mdo = method->method_data();
    update_rate(t, method);
    if (max_task == NULL) {
      max_task = task;
      max_method = method;
    } else {
      // If a method has been stale for some time, remove it from the queue.
      if (is_stale(t, TieredCompileTaskTimeout, method) && !is_old(method)) {
        if (PrintTieredEvents) {
          print_event(KILL, method, method, task->osr_bci(), (CompLevel)task->comp_level());
        }
        CompileTaskWrapper ctw(task); // Frees the task
        compile_queue->remove(task);
        method->clear_queued_for_compilation();
        task = next_task;
        continue;
      }

      // Select a method with a higher rate
      if (compare_methods(method, max_method)) {
        max_task = task;
        max_method = method;
      }
    }
    task = next_task;
  }

  if (max_task->comp_level() == CompLevel_full_profile && is_method_profiled(max_method)) {
    max_task->set_comp_level(CompLevel_limited_profile);
    if (PrintTieredEvents) {
      print_event(UPDATE, max_method, max_method, max_task->osr_bci(), (CompLevel)max_task->comp_level());
    }
  }

  return max_task;
}

double AdvancedThresholdPolicy::threshold_scale(CompLevel level, int feedback_k) {
  double queue_size = CompileBroker::queue_size(level);
  int comp_count = compiler_count(level);
  double k = queue_size / (feedback_k * comp_count) + 1;
  return k;
}

// Call and loop predicates determine whether a transition to a higher
// compilation level should be performed (pointers to predicate functions
// are passed to common()).
// Tier?LoadFeedback is basically a coefficient that determines of
// how many methods per compiler thread can be in the queue before
// the threshold values double.
bool AdvancedThresholdPolicy::loop_predicate(int i, int b, CompLevel cur_level) {
  switch(cur_level) {
  case CompLevel_none:
  case CompLevel_limited_profile: {
    double k = threshold_scale(CompLevel_full_profile, Tier3LoadFeedback);
    return loop_predicate_helper<CompLevel_none>(i, b, k);
  }
  case CompLevel_full_profile: {
    double k = threshold_scale(CompLevel_full_optimization, Tier4LoadFeedback);
    return loop_predicate_helper<CompLevel_full_profile>(i, b, k);
  }
  default:
    return true;
  }
}

bool AdvancedThresholdPolicy::call_predicate(int i, int b, CompLevel cur_level) {
  switch(cur_level) {
  case CompLevel_none:
  case CompLevel_limited_profile: {
    double k = threshold_scale(CompLevel_full_profile, Tier3LoadFeedback);
    return call_predicate_helper<CompLevel_none>(i, b, k);
  }
  case CompLevel_full_profile: {
    double k = threshold_scale(CompLevel_full_optimization, Tier4LoadFeedback);
    return call_predicate_helper<CompLevel_full_profile>(i, b, k);
  }
  default:
    return true;
  }
}

// If a method is old enough and is still in the interpreter we would want to
// start profiling without waiting for the compiled method to arrive.
// We also take the load on compilers into the account.
bool AdvancedThresholdPolicy::should_create_mdo(methodOop method, CompLevel cur_level) {
  if (cur_level == CompLevel_none &&
      CompileBroker::queue_size(CompLevel_full_optimization) <=
      Tier3DelayOn * compiler_count(CompLevel_full_optimization)) {
    int i = method->invocation_count();
    int b = method->backedge_count();
    double k = Tier0ProfilingStartPercentage / 100.0;
    return call_predicate_helper<CompLevel_none>(i, b, k) || loop_predicate_helper<CompLevel_none>(i, b, k);
  }
  return false;
}

// Create MDO if necessary.
void AdvancedThresholdPolicy::create_mdo(methodHandle mh, TRAPS) {
  if (mh->is_native() || mh->is_abstract() || mh->is_accessor()) return;
  if (mh->method_data() == NULL) {
    methodOopDesc::build_interpreter_method_data(mh, THREAD);
    if (HAS_PENDING_EXCEPTION) {
      CLEAR_PENDING_EXCEPTION;
    }
  }
}


/*
 * Method states:
 *   0 - interpreter (CompLevel_none)
 *   1 - pure C1 (CompLevel_simple)
 *   2 - C1 with invocation and backedge counting (CompLevel_limited_profile)
 *   3 - C1 with full profiling (CompLevel_full_profile)
 *   4 - C2 (CompLevel_full_optimization)
 *
 * Common state transition patterns:
 * a. 0 -> 3 -> 4.
 *    The most common path. But note that even in this straightforward case
 *    profiling can start at level 0 and finish at level 3.
 *
 * b. 0 -> 2 -> 3 -> 4.
 *    This case occures when the load on C2 is deemed too high. So, instead of transitioning
 *    into state 3 directly and over-profiling while a method is in the C2 queue we transition to
 *    level 2 and wait until the load on C2 decreases. This path is disabled for OSRs.
 *
 * c. 0 -> (3->2) -> 4.
 *    In this case we enqueue a method for compilation at level 3, but the C1 queue is long enough
 *    to enable the profiling to fully occur at level 0. In this case we change the compilation level
 *    of the method to 2, because it'll allow it to run much faster without full profiling while c2
 *    is compiling.
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
CompLevel AdvancedThresholdPolicy::common(Predicate p, methodOop method, CompLevel cur_level) {
  if (is_trivial(method)) return CompLevel_simple;

  CompLevel next_level = cur_level;
  int i = method->invocation_count();
  int b = method->backedge_count();

  switch(cur_level) {
  case CompLevel_none:
    // If we were at full profile level, would we switch to full opt?
    if (common(p, method, CompLevel_full_profile) == CompLevel_full_optimization) {
      next_level = CompLevel_full_optimization;
    } else if ((this->*p)(i, b, cur_level)) {
      // C1-generated fully profiled code is about 30% slower than the limited profile
      // code that has only invocation and backedge counters. The observation is that
      // if C2 queue is large enough we can spend too much time in the fully profiled code
      // while waiting for C2 to pick the method from the queue. To alleviate this problem
      // we introduce a feedback on the C2 queue size. If the C2 queue is sufficiently long
      // we choose to compile a limited profiled version and then recompile with full profiling
      // when the load on C2 goes down.
      if (CompileBroker::queue_size(CompLevel_full_optimization) >
          Tier3DelayOn * compiler_count(CompLevel_full_optimization)) {
        next_level = CompLevel_limited_profile;
      } else {
        next_level = CompLevel_full_profile;
      }
    }
    break;
  case CompLevel_limited_profile:
    if (is_method_profiled(method)) {
      // Special case: we got here because this method was fully profiled in the interpreter.
      next_level = CompLevel_full_optimization;
    } else {
      methodDataOop mdo = method->method_data();
      if (mdo != NULL) {
        if (mdo->would_profile()) {
          if (CompileBroker::queue_size(CompLevel_full_optimization) <=
              Tier3DelayOff * compiler_count(CompLevel_full_optimization) &&
              (this->*p)(i, b, cur_level)) {
            next_level = CompLevel_full_profile;
          }
        } else {
          next_level = CompLevel_full_optimization;
        }
      }
    }
    break;
  case CompLevel_full_profile:
    {
      methodDataOop mdo = method->method_data();
      if (mdo != NULL) {
        if (mdo->would_profile()) {
          int mdo_i = mdo->invocation_count_delta();
          int mdo_b = mdo->backedge_count_delta();
          if ((this->*p)(mdo_i, mdo_b, cur_level)) {
            next_level = CompLevel_full_optimization;
          }
        } else {
          next_level = CompLevel_full_optimization;
        }
      }
    }
    break;
  }
  return next_level;
}

// Determine if a method should be compiled with a normal entry point at a different level.
CompLevel AdvancedThresholdPolicy::call_event(methodOop method,  CompLevel cur_level) {
  CompLevel osr_level = (CompLevel) method->highest_osr_comp_level();
  CompLevel next_level = common(&AdvancedThresholdPolicy::call_predicate, method, cur_level);

  // If OSR method level is greater than the regular method level, the levels should be
  // equalized by raising the regular method level in order to avoid OSRs during each
  // invocation of the method.
  if (osr_level == CompLevel_full_optimization && cur_level == CompLevel_full_profile) {
    methodDataOop mdo = method->method_data();
    guarantee(mdo != NULL, "MDO should not be NULL");
    if (mdo->invocation_count() >= 1) {
      next_level = CompLevel_full_optimization;
    }
  } else {
    next_level = MAX2(osr_level, next_level);
  }

  return next_level;
}

// Determine if we should do an OSR compilation of a given method.
CompLevel AdvancedThresholdPolicy::loop_event(methodOop method, CompLevel cur_level) {
  if (cur_level == CompLevel_none) {
    // If there is a live OSR method that means that we deopted to the interpreter
    // for the transition.
    CompLevel osr_level = (CompLevel)method->highest_osr_comp_level();
    if (osr_level > CompLevel_none) {
      return osr_level;
    }
  }
  return common(&AdvancedThresholdPolicy::loop_predicate, method, cur_level);
}

// Update the rate and submit compile
void AdvancedThresholdPolicy::submit_compile(methodHandle mh, int bci, CompLevel level, TRAPS) {
  int hot_count = (bci == InvocationEntryBci) ? mh->invocation_count() : mh->backedge_count();
  update_rate(os::javaTimeMillis(), mh());
  CompileBroker::compile_method(mh, bci, level, mh, hot_count, "tiered", THREAD);
}


// Handle the invocation event.
void AdvancedThresholdPolicy::method_invocation_event(methodHandle mh, methodHandle imh,
                                                      CompLevel level, TRAPS) {
  if (should_create_mdo(mh(), level)) {
    create_mdo(mh, THREAD);
  }
  if (is_compilation_enabled() && !CompileBroker::compilation_is_in_queue(mh, InvocationEntryBci)) {
    CompLevel next_level = call_event(mh(), level);
    if (next_level != level) {
      compile(mh, InvocationEntryBci, next_level, THREAD);
    }
  }
}

// Handle the back branch event. Notice that we can compile the method
// with a regular entry from here.
void AdvancedThresholdPolicy::method_back_branch_event(methodHandle mh, methodHandle imh,
                                                       int bci, CompLevel level, TRAPS) {
  if (should_create_mdo(mh(), level)) {
    create_mdo(mh, THREAD);
  }

  // If the method is already compiling, quickly bail out.
  if (is_compilation_enabled() && !CompileBroker::compilation_is_in_queue(mh, bci)) {
    // Use loop event as an opportinity to also check there's been
    // enough calls.
    CompLevel cur_level = comp_level(mh());
    CompLevel next_level = call_event(mh(), cur_level);
    CompLevel next_osr_level = loop_event(mh(), level);
    if (next_osr_level  == CompLevel_limited_profile) {
      next_osr_level = CompLevel_full_profile; // OSRs are supposed to be for very hot methods.
    }
    next_level = MAX2(next_level,
                      next_osr_level < CompLevel_full_optimization ? next_osr_level : cur_level);
    bool is_compiling = false;
    if (next_level != cur_level) {
      compile(mh, InvocationEntryBci, next_level, THREAD);
      is_compiling = true;
    }

    // Do the OSR version
    if (!is_compiling && next_osr_level != level) {
      compile(mh, bci, next_osr_level, THREAD);
    }
  }
}

#endif // TIERED
