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

# include "incls/_precompiled.incl"
# include "incls/_simpleThresholdPolicy.cpp.incl"

// Print an event.
void SimpleThresholdPolicy::print_event(EventType type, methodHandle mh, methodHandle imh,
                                        int bci, CompLevel level) {
  bool inlinee_event = mh() != imh();

  ttyLocker tty_lock;
  tty->print("%lf: [", os::elapsedTime());

  int invocation_count = mh->invocation_count();
  int backedge_count = mh->backedge_count();
  switch(type) {
  case CALL:
    tty->print("call");
    break;
  case LOOP:
    tty->print("loop");
    break;
  case COMPILE:
    tty->print("compile");
  }

  tty->print(" level: %d ", level);

  ResourceMark rm;
  char *method_name = mh->name_and_sig_as_C_string();
  tty->print("[%s", method_name);
  // We can have an inlinee, although currently we don't generate any notifications for the inlined methods.
  if (inlinee_event) {
    char *inlinee_name = imh->name_and_sig_as_C_string();
    tty->print(" [%s]] ", inlinee_name);
  }
  else tty->print("] ");
  tty->print("@%d queues: %d,%d", bci, CompileBroker::queue_size(CompLevel_full_profile),
                                       CompileBroker::queue_size(CompLevel_full_optimization));

  print_specific(type, mh, imh, bci, level);

  if (type != COMPILE) {
    methodDataHandle mdh = mh->method_data();
    int mdo_invocations = 0, mdo_backedges = 0;
    if (mdh() != NULL) {
      mdo_invocations = mdh->invocation_count();
      mdo_backedges = mdh->backedge_count();
    }
    tty->print(" total: %d,%d mdo: %d,%d",
               invocation_count, backedge_count,
               mdo_invocations, mdo_backedges);
    tty->print(" max levels: %d,%d",
                mh->highest_comp_level(), mh->highest_osr_comp_level());
    if (inlinee_event) {
      tty->print(" inlinee max levels: %d,%d", imh->highest_comp_level(), imh->highest_osr_comp_level());
    }
    tty->print(" compilable: ");
    bool need_comma = false;
    if (!mh->is_not_compilable(CompLevel_full_profile)) {
      tty->print("c1");
      need_comma = true;
    }
    if (!mh->is_not_compilable(CompLevel_full_optimization)) {
      if (need_comma) tty->print(", ");
      tty->print("c2");
      need_comma = true;
    }
    if (!mh->is_not_osr_compilable()) {
      if (need_comma) tty->print(", ");
      tty->print("osr");
    }
    tty->print(" status:");
    if (mh->queued_for_compilation()) {
      tty->print(" in queue");
    } else tty->print(" idle");
  }
  tty->print_cr("]");
}

void SimpleThresholdPolicy::initialize() {
  if (FLAG_IS_DEFAULT(CICompilerCount)) {
    FLAG_SET_DEFAULT(CICompilerCount, 3);
  }
  int count = CICompilerCount;
  if (CICompilerCountPerCPU) {
    count = MAX2(log2_intptr(os::active_processor_count()), 1) * 3 / 2;
  }
  set_c1_count(MAX2(count / 3, 1));
  set_c2_count(MAX2(count - count / 3, 1));
}

void SimpleThresholdPolicy::set_carry_if_necessary(InvocationCounter *counter) {
  if (!counter->carry() && counter->count() > InvocationCounter::count_limit / 2) {
    counter->set_carry_flag();
  }
}

// Set carry flags on the counters if necessary
void SimpleThresholdPolicy::handle_counter_overflow(methodOop method) {
  set_carry_if_necessary(method->invocation_counter());
  set_carry_if_necessary(method->backedge_counter());
  methodDataOop mdo = method->method_data();
  if (mdo != NULL) {
    set_carry_if_necessary(mdo->invocation_counter());
    set_carry_if_necessary(mdo->backedge_counter());
  }
}

// Called with the queue locked and with at least one element
CompileTask* SimpleThresholdPolicy::select_task(CompileQueue* compile_queue) {
  return compile_queue->first();
}

nmethod* SimpleThresholdPolicy::event(methodHandle method, methodHandle inlinee,
                                      int branch_bci, int bci, CompLevel comp_level, TRAPS) {
  if (comp_level == CompLevel_none &&
      JvmtiExport::can_post_interpreter_events()) {
    assert(THREAD->is_Java_thread(), "Should be java thread");
    if (((JavaThread*)THREAD)->is_interp_only_mode()) {
      return NULL;
    }
  }
  nmethod *osr_nm = NULL;

  handle_counter_overflow(method());
  if (method() != inlinee()) {
    handle_counter_overflow(inlinee());
  }

  if (PrintTieredEvents) {
    print_event(bci == InvocationEntryBci ? CALL : LOOP, method, inlinee, bci, comp_level);
  }

  if (bci == InvocationEntryBci) {
    method_invocation_event(method, inlinee, comp_level, THREAD);
  } else {
    method_back_branch_event(method, inlinee, bci, comp_level, THREAD);
    int highest_level = method->highest_osr_comp_level();
    if (highest_level > comp_level) {
      osr_nm = method->lookup_osr_nmethod_for(bci, highest_level, false);
    }
  }
  return osr_nm;
}

// Check if the method can be compiled, change level if necessary
void SimpleThresholdPolicy::compile(methodHandle mh, int bci, CompLevel level, TRAPS) {
  // Take the given ceiling into the account.
  // NOTE: You can set it to 1 to get a pure C1 version.
  if ((CompLevel)TieredStopAtLevel < level) {
    level = (CompLevel)TieredStopAtLevel;
  }
  if (level == CompLevel_none) {
    return;
  }
  // Check if the method can be compiled. If it cannot be compiled with C1, continue profiling
  // in the interpreter and then compile with C2 (the transition function will request that,
  // see common() ). If the method cannot be compiled with C2 but still can with C1, compile it with
  // pure C1.
  if (!can_be_compiled(mh, level)) {
    if (level == CompLevel_full_optimization && can_be_compiled(mh, CompLevel_simple)) {
        compile(mh, bci, CompLevel_simple, THREAD);
    }
    return;
  }
  if (bci != InvocationEntryBci && mh->is_not_osr_compilable()) {
    return;
  }
  if (PrintTieredEvents) {
    print_event(COMPILE, mh, mh, bci, level);
  }
  if (!CompileBroker::compilation_is_in_queue(mh, bci)) {
    submit_compile(mh, bci, level, THREAD);
  }
}

// Tell the broker to compile the method
void SimpleThresholdPolicy::submit_compile(methodHandle mh, int bci, CompLevel level, TRAPS) {
  int hot_count = (bci == InvocationEntryBci) ? mh->invocation_count() : mh->backedge_count();
  CompileBroker::compile_method(mh, bci, level, mh, hot_count, "tiered", THREAD);
}

// Call and loop predicates determine whether a transition to a higher
// compilation level should be performed (pointers to predicate functions
// are passed to common() transition function).
bool SimpleThresholdPolicy::loop_predicate(int i, int b, CompLevel cur_level) {
  switch(cur_level) {
  case CompLevel_none:
  case CompLevel_limited_profile: {
    return loop_predicate_helper<CompLevel_none>(i, b, 1.0);
  }
  case CompLevel_full_profile: {
    return loop_predicate_helper<CompLevel_full_profile>(i, b, 1.0);
  }
  default:
    return true;
  }
}

bool SimpleThresholdPolicy::call_predicate(int i, int b, CompLevel cur_level) {
  switch(cur_level) {
  case CompLevel_none:
  case CompLevel_limited_profile: {
    return call_predicate_helper<CompLevel_none>(i, b, 1.0);
  }
  case CompLevel_full_profile: {
    return call_predicate_helper<CompLevel_full_profile>(i, b, 1.0);
  }
  default:
    return true;
  }
}

// Determine is a method is mature.
bool SimpleThresholdPolicy::is_mature(methodOop method) {
  if (is_trivial(method)) return true;
  methodDataOop mdo = method->method_data();
  if (mdo != NULL) {
    int i = mdo->invocation_count();
    int b = mdo->backedge_count();
    double k = ProfileMaturityPercentage / 100.0;
    return call_predicate_helper<CompLevel_full_profile>(i, b, k) ||
           loop_predicate_helper<CompLevel_full_profile>(i, b, k);
  }
  return false;
}

// Common transition function. Given a predicate determines if a method should transition to another level.
CompLevel SimpleThresholdPolicy::common(Predicate p, methodOop method, CompLevel cur_level) {
  CompLevel next_level = cur_level;
  int i = method->invocation_count();
  int b = method->backedge_count();

  switch(cur_level) {
  case CompLevel_none:
    {
      methodDataOop mdo = method->method_data();
      if (mdo != NULL) {
        int mdo_i = mdo->invocation_count();
        int mdo_b = mdo->backedge_count();
        // If we were at full profile level, would we switch to full opt?
        if ((this->*p)(mdo_i, mdo_b, CompLevel_full_profile)) {
          next_level = CompLevel_full_optimization;
        }
      }
    }
    if (next_level == cur_level && (this->*p)(i, b, cur_level)) {
      if (is_trivial(method)) {
        next_level = CompLevel_simple;
      } else {
        next_level = CompLevel_full_profile;
      }
    }
    break;
  case CompLevel_limited_profile:
  case CompLevel_full_profile:
    if (is_trivial(method)) {
      next_level = CompLevel_simple;
    } else {
      methodDataOop mdo = method->method_data();
      guarantee(mdo != NULL, "MDO should always exist");
      if (mdo->would_profile()) {
        int mdo_i = mdo->invocation_count();
        int mdo_b = mdo->backedge_count();
        if ((this->*p)(mdo_i, mdo_b, cur_level)) {
          next_level = CompLevel_full_optimization;
        }
      } else {
        next_level = CompLevel_full_optimization;
      }
    }
    break;
  }
  return next_level;
}

// Determine if a method should be compiled with a normal entry point at a different level.
CompLevel SimpleThresholdPolicy::call_event(methodOop method,  CompLevel cur_level) {
  CompLevel highest_level = (CompLevel)method->highest_comp_level();
  if (cur_level == CompLevel_none && highest_level > cur_level) {
    // TODO: We may want to try to do more extensive reprofiling in this case.
    return highest_level;
  }

  CompLevel osr_level = (CompLevel) method->highest_osr_comp_level();
  CompLevel next_level = common(&SimpleThresholdPolicy::call_predicate, method, cur_level);

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
CompLevel SimpleThresholdPolicy::loop_event(methodOop method, CompLevel cur_level) {
  if (cur_level == CompLevel_none) {
    // If there is a live OSR method that means that we deopted to the interpreter
    // for the transition.
    CompLevel osr_level = (CompLevel)method->highest_osr_comp_level();
    if (osr_level > CompLevel_none) {
      return osr_level;
    }
  }
  return common(&SimpleThresholdPolicy::loop_predicate, method, cur_level);
}


// Handle the invocation event.
void SimpleThresholdPolicy::method_invocation_event(methodHandle mh, methodHandle imh,
                                              CompLevel level, TRAPS) {
  if (is_compilation_enabled() && !CompileBroker::compilation_is_in_queue(mh, InvocationEntryBci)) {
    CompLevel next_level = call_event(mh(), level);
    if (next_level != level) {
      compile(mh, InvocationEntryBci, next_level, THREAD);
    }
  }
}

// Handle the back branch event. Notice that we can compile the method
// with a regular entry from here.
void SimpleThresholdPolicy::method_back_branch_event(methodHandle mh, methodHandle imh,
                                               int bci, CompLevel level, TRAPS) {
  // If the method is already compiling, quickly bail out.
  if (is_compilation_enabled() && !CompileBroker::compilation_is_in_queue(mh, bci)) {
    // Use loop event as an opportinity to also check there's been
    // enough calls.
    CompLevel cur_level = comp_level(mh());
    CompLevel next_level = call_event(mh(), cur_level);
    CompLevel next_osr_level = loop_event(mh(), level);

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
