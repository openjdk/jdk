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
// This handle can be in 2 states:
//  1. Unsafe (weak). Method* is present, but its holder is only weakly-reachable, and can
//     be unloaded. Users need to check is_safe() before calling method().
//     method() is safe to call iff we have not crossed a safepoint since construction
//     or last is_safe() check. Calling make_always_safe() after is_safe() check
//     moves handle to the strong state.
//  2. Safe (strong). Method* holder is strongly reachable, cannot be unloaded.
//     Calling method() is always safe in this state.
//
// The handle transitions are one-shot:
//    unsafe (weak) --(make_always_safe) --> safe (strong)
//
// There are internal shortcuts that bypass this mechanics when handle knows
// the method holder is permanent and would not be unloaded. This is an implementation
// detail, it does not change any external contract. Using this handle for permanent
// method holders provides future safety.
//
// Common usage pattern:
//
//   UnloadableMethodHandle mh(method);   // Now in unsafe (weak) state.
//   mh.method()->print_on(tty);          // method() is good until the next safepoint.
//   <safepoint>
//   if (!mh.is_safe()) {                 // Safe to use method()?
//     return;                            // Nope!
//   }
//   mh.method()->print_on(tty);          // method() is good until the next safepoint.
//   mh.make_always_safe();               // Now in safe (strong) state.
//   <safepoint>
//   mh.method()->print_on(tty);          // method() is always safe now.
//

class Method;

class UnloadableMethodHandle {
  friend class VMStructs;
private:
  enum class State {
    PERMANENT,
    WEAK,
    STRONG,
    RELEASED,
  };

  State volatile _state;

  Method* _method;
  WeakHandle _weak_handle;
  OopHandle _strong_handle;

  inline State get_state() const;
  inline void set_state(State to);
  inline bool transit_state(State from, State to);
  inline oop get_unload_blocker(Method* method);

public:
  UnloadableMethodHandle(Method* method);
  ~UnloadableMethodHandle();

  inline Method* method() const;
  inline Method* method_unsafe() const;

  inline bool is_safe() const;
  void make_always_safe();
};

#endif // SHARE_OOPS_UNLOADABLE_METHOD_HANDLE_HPP
