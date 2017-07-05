/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_INTERPRETER_INVOCATIONCOUNTER_HPP
#define SHARE_VM_INTERPRETER_INVOCATIONCOUNTER_HPP

#include "memory/allocation.hpp"
#include "runtime/handles.hpp"
#include "utilities/exceptions.hpp"

// InvocationCounters are used to trigger actions when a limit (threshold) is reached.
// For different states, different limits and actions can be defined in the initialization
// routine of InvocationCounters.
//
// Implementation notes: For space reasons, state & counter are both encoded in one word,
// The state is encoded using some of the least significant bits, the counter is using the
// more significant bits. The counter is incremented before a method is activated and an
// action is triggered when when count() > limit().

class InvocationCounter VALUE_OBJ_CLASS_SPEC {
  friend class VMStructs;
 private:                             // bit no: |31  3|  2  | 1 0 |
  unsigned int _counter;              // format: [count|carry|state]

  enum PrivateConstants {
    number_of_state_bits = 2,
    number_of_carry_bits = 1,
    number_of_noncount_bits = number_of_state_bits + number_of_carry_bits,
    number_of_count_bits = BitsPerInt - number_of_noncount_bits,
    state_limit          = nth_bit(number_of_state_bits),
    count_grain          = nth_bit(number_of_state_bits + number_of_carry_bits),
    carry_mask           = right_n_bits(number_of_carry_bits) << number_of_state_bits,
    state_mask           = right_n_bits(number_of_state_bits),
    status_mask          = right_n_bits(number_of_state_bits + number_of_carry_bits),
    count_mask           = ((int)(-1) ^ status_mask)
  };

 public:
  static int InterpreterInvocationLimit;        // CompileThreshold scaled for interpreter use
  static int InterpreterBackwardBranchLimit;    // A separate threshold for on stack replacement
  static int InterpreterProfileLimit;           // Profiling threshold scaled for interpreter use

  typedef address (*Action)(methodHandle method, TRAPS);

  enum PublicConstants {
    count_increment      = count_grain,          // use this value to increment the 32bit _counter word
    count_mask_value     = count_mask,           // use this value to mask the backedge counter
    count_shift          = number_of_noncount_bits,
    count_limit          = nth_bit(number_of_count_bits - 1)
  };

  enum State {
    wait_for_nothing,                            // do nothing when count() > limit()
    wait_for_compile,                            // introduce nmethod when count() > limit()
    number_of_states                             // must be <= state_limit
  };

  // Manipulation
  void reset();                                  // sets state to wait state
  void init();                                   // sets state into original state
  void set_state(State state);                   // sets state and initializes counter correspondingly
  inline void set(State state, int count);       // sets state and counter
  inline void decay();                           // decay counter (divide by two)
  void set_carry();                              // set the sticky carry bit
  void set_carry_flag()                          {  _counter |= carry_mask; }

  // Accessors
  State  state() const                           { return (State)(_counter & state_mask); }
  bool   carry() const                           { return (_counter & carry_mask) != 0; }
  int    limit() const                           { return CompileThreshold; }
  Action action() const                          { return _action[state()]; }
  int    count() const                           { return _counter >> number_of_noncount_bits; }

  int   get_InvocationLimit() const              { return InterpreterInvocationLimit >> number_of_noncount_bits; }
  int   get_BackwardBranchLimit() const          { return InterpreterBackwardBranchLimit >> number_of_noncount_bits; }
  int   get_ProfileLimit() const                 { return InterpreterProfileLimit >> number_of_noncount_bits; }

  // Test counter using scaled limits like the asm interpreter would do rather than doing
  // the shifts to normalize the counter.

  bool   reached_InvocationLimit() const         { return _counter >= (unsigned int) InterpreterInvocationLimit; }
  bool   reached_BackwardBranchLimit() const     { return _counter >= (unsigned int) InterpreterBackwardBranchLimit; }

  // Do this just like asm interpreter does for max speed
  bool   reached_ProfileLimit(InvocationCounter *back_edge_count) const {
    return (_counter && count_mask) + back_edge_count->_counter >= (unsigned int) InterpreterProfileLimit;
  }

  void increment()                               { _counter += count_increment; }


  // Printing
  void   print();
  void   print_short();

  // Miscellaneous
  static ByteSize counter_offset()               { return byte_offset_of(InvocationCounter, _counter); }
  static void reinitialize(bool delay_overflow);

 private:
  static int         _init  [number_of_states];  // the counter limits
  static Action      _action[number_of_states];  // the actions

  static void        def(State state, int init, Action action);
  static const char* state_as_string(State state);
  static const char* state_as_short_string(State state);
};

inline void InvocationCounter::set(State state, int count) {
  assert(0 <= state && state < number_of_states, "illegal state");
  int carry = (_counter & carry_mask);    // the carry bit is sticky
  _counter = (count << number_of_noncount_bits) | carry | state;
}

inline void InvocationCounter::decay() {
  int c = count();
  int new_count = c >> 1;
  // prevent from going to zero, to distinguish from never-executed methods
  if (c > 0 && new_count == 0) new_count = 1;
  set(state(), new_count);
}


#endif // SHARE_VM_INTERPRETER_INVOCATIONCOUNTER_HPP
