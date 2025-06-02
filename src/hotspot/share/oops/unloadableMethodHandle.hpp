/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#ifndef SHARE_OOPS_UNLOADABLE_METHOD_HANDLE_HPP
#define SHARE_OOPS_UNLOADABLE_METHOD_HANDLE_HPP

#include "memory/padded.hpp"
#include "oops/oopHandle.hpp"
#include "oops/weakHandle.hpp"

// Unloadable method handle.
//
// This handle allows holding to Method* safely without delaying class unloading
// of its holder.
//
// This handle can be in 4 states:
//  1. Empty. There is no Method* inside. All methods are safe to call.
//     This is a convenience state to allow easy initializations.
//  2. Weak. Method* is present, but its holder is only weakly-reachable, and can
//     be unloaded. Users need to check is_safe() before calling method().
//     method() is safe to call iff we have not crossed a safepoint since construction
//     or last is_safe() check. Calling make_always_safe() after is_safe() check
//     moves handle to the strong state.
//  3. Strong. Method* holder is strongly reachable, cannot be unloaded.
//     Calling method() is always safe in this state.
//  4. Released. Method* is in unknown state, and cannot be accessed.
//     method() is unsafe to call in this state.
//
// The handle transitions are one-shot:
//    weak   --(make_always_safe) --> strong
//    weak   ------(release) -------> released
//    strong ------(release) -------> released
//
// Additionally, when handle is empty, it stays empty:
//    empty  --(make_always_safe) --> empty
//    empty  ------(release) -------> empty
//
// Common usage pattern:
//
//   UnloadableMethodHandle mh;           // Initially empty.
//   mh = UnloadableMethodHandle(method); // Now in weak state.
//   mh.method()->print_on(tty);          // method() is good until the next safepoint.
//   <safepoint>
//   if (!mh.is_safe()) {                 // Safe to use method()?
//     mh.release();                      // No! Release the handle and exit.
//     return;
//   }
//   mh.method()->print_on(tty);          // method() is good until the next safepoint.
//   mh.make_always_safe();               // Now in safe state.
//   <safepoint>
//   mh.method()->print_on(tty);          // method() is always safe now.
//   mh.release();                        // Release the handle.
//

class Method;

class UnloadableMethodHandle {
  friend class VMStructs;
private:
  enum State {
    EMPTY,
    PERMANENT,
    WEAK,
    STRONG,
    RELEASED,
  } volatile _state;

  // There are lots of writes to this field in common (WEAK) state.
  // Protect the adjacent fields from false sharing to optimize state queries.
  DEFINE_PAD_MINUS_SIZE(0, DEFAULT_PADDING_SIZE, 0);
  mutable volatile int _spin_lock;
  DEBUG_ONLY(mutable volatile Thread* _spin_lock_owner;)
  DEFINE_PAD_MINUS_SIZE(1, DEFAULT_PADDING_SIZE, 0);

  class SpinLocker : StackObj {
  private:
    const UnloadableMethodHandle* const _handle;

  public:
    SpinLocker(const UnloadableMethodHandle* handle);
    ~SpinLocker();
  };

  Method* _method;
  WeakHandle _weak_handle;
  OopHandle _strong_handle;

  inline State get_state() const;
  inline void set_state(State s);
  inline oop get_unload_blocker(Method* method);

public:
  UnloadableMethodHandle();
  UnloadableMethodHandle(Method* method);
  inline void release();

  inline Method* method() const;
  inline Method* method_unsafe() const;

  inline bool is_safe() const;
  void make_always_safe();
};

#endif // SHARE_OOPS_UNLOADABLE_METHOD_HANDLE_HPP
