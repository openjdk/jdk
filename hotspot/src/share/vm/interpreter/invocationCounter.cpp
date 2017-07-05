/*
 * Copyright 1997-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
# include "incls/_invocationCounter.cpp.incl"


// Implementation of InvocationCounter

void InvocationCounter::init() {
  _counter = 0;  // reset all the bits, including the sticky carry
  reset();
}

void InvocationCounter::reset() {
  // Only reset the state and don't make the method look like it's never
  // been executed
  set_state(wait_for_compile);
}

void InvocationCounter::set_carry() {
  _counter |= carry_mask;

  // The carry bit now indicates that this counter had achieved a very
  // large value.  Now reduce the value, so that the method can be
  // executed many more times before re-entering the VM.
  int old_count = count();
  int new_count = MIN2(old_count, (int) (CompileThreshold / 2));
  // prevent from going to zero, to distinguish from never-executed methods
  if (new_count == 0)  new_count = 1;
  if (old_count != new_count)  set(state(), new_count);
}


void InvocationCounter::set_state(State state) {
  assert(0 <= state && state < number_of_states, "illegal state");
  int init = _init[state];
  // prevent from going to zero, to distinguish from never-executed methods
  if (init == 0 && count() > 0)  init = 1;
  int carry = (_counter & carry_mask);    // the carry bit is sticky
  _counter = (init << number_of_noncount_bits) | carry | state;
}


void InvocationCounter::print() {
  tty->print_cr("invocation count: up = %d, limit = %d, carry = %s, state = %s",
                                   count(), limit(),
                                   carry() ? "true" : "false",
                                   state_as_string(state()));
}

void InvocationCounter::print_short() {
  tty->print(" [%d%s;%s]", count(), carry()?"+carry":"", state_as_short_string(state()));
}

// Initialization

int                       InvocationCounter::_init  [InvocationCounter::number_of_states];
InvocationCounter::Action InvocationCounter::_action[InvocationCounter::number_of_states];
int                       InvocationCounter::InterpreterInvocationLimit;
int                       InvocationCounter::InterpreterBackwardBranchLimit;
int                       InvocationCounter::InterpreterProfileLimit;

// Tier1 limits
int                       InvocationCounter::Tier1InvocationLimit;
int                       InvocationCounter::Tier1BackEdgeLimit;



const char* InvocationCounter::state_as_string(State state) {
  switch (state) {
    case wait_for_nothing            : return "wait_for_nothing";
    case wait_for_compile            : return "wait_for_compile";
  }
  ShouldNotReachHere();
  return NULL;
}

const char* InvocationCounter::state_as_short_string(State state) {
  switch (state) {
    case wait_for_nothing            : return "not comp.";
    case wait_for_compile            : return "compileable";
  }
  ShouldNotReachHere();
  return NULL;
}


static address do_nothing(methodHandle method, TRAPS) {
  // dummy action for inactive invocation counters
  method->invocation_counter()->set_carry();
  method->invocation_counter()->set_state(InvocationCounter::wait_for_nothing);
  return NULL;
}


static address do_decay(methodHandle method, TRAPS) {
  // decay invocation counters so compilation gets delayed
  method->invocation_counter()->decay();
  return NULL;
}


void InvocationCounter::def(State state, int init, Action action) {
  assert(0 <= state && state < number_of_states, "illegal state");
  assert(0 <= init  && init  < count_limit, "initial value out of range");
  _init  [state] = init;
  _action[state] = action;
}

address dummy_invocation_counter_overflow(methodHandle m, TRAPS) {
  ShouldNotReachHere();
  return NULL;
}

void InvocationCounter::reinitialize(bool delay_overflow) {
  // define states
  guarantee((int)number_of_states <= (int)state_limit, "adjust number_of_state_bits");
  def(wait_for_nothing, 0, do_nothing);
  if (delay_overflow) {
    def(wait_for_compile, 0, do_decay);
  } else {
    def(wait_for_compile, 0, dummy_invocation_counter_overflow);
  }

  InterpreterInvocationLimit = CompileThreshold << number_of_noncount_bits;
  InterpreterProfileLimit = ((CompileThreshold * InterpreterProfilePercentage) / 100)<< number_of_noncount_bits;
  Tier1InvocationLimit = Tier2CompileThreshold << number_of_noncount_bits;
  Tier1BackEdgeLimit   = Tier2BackEdgeThreshold << number_of_noncount_bits;

  // When methodData is collected, the backward branch limit is compared against a
  // methodData counter, rather than an InvocationCounter.  In the former case, we
  // don't need the shift by number_of_noncount_bits, but we do need to adjust
  // the factor by which we scale the threshold.
  if (ProfileInterpreter) {
    InterpreterBackwardBranchLimit = (CompileThreshold * (OnStackReplacePercentage - InterpreterProfilePercentage)) / 100;
  } else {
    InterpreterBackwardBranchLimit = ((CompileThreshold * OnStackReplacePercentage) / 100) << number_of_noncount_bits;
  }

  assert(0 <= InterpreterBackwardBranchLimit,
         "OSR threshold should be non-negative");
  assert(0 <= InterpreterProfileLimit &&
         InterpreterProfileLimit <= InterpreterInvocationLimit,
         "profile threshold should be less than the compilation threshold "
         "and non-negative");
}

void invocationCounter_init() {
  InvocationCounter::reinitialize(DelayCompilationDuringStartup);
}
