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

#ifndef SHARE_RUNTIME_UNLOADABLE_METHOD_HANDLE_HPP
#define SHARE_RUNTIME_UNLOADABLE_METHOD_HANDLE_HPP

#include "oops/klass.hpp"
#include "oops/oopHandle.hpp"
#include "oops/weakHandle.hpp"

// Unloadable method handle.
//
// Useful when one needs to hold to Method* without delaying class unloading.
//
// This handle can be in 3 states:
//  1. Initial weak state. Relevant Method* is only weakly-reachable, can be cleared
//     by class unloading.
//  2. Accessible strong state. Relevant Method* is strongly reachable, cannot be
//     cleared by class unloading.
//  3. Final released state. Relevant Method* is in unknown state, and cannot be
//     accessed.
//
// Users should call block_unloading() to reach accessible state before unwrapping
// method().
//
class UnloadableMethodHandle {
  Method* _method;
  WeakHandle _weak_handle;   // oop that can be used to block unloading
  OopHandle  _strong_handle; // oop that *is* used to block unloading

  inline oop get_unload_blocker(Method* method);

public:
  UnloadableMethodHandle() : _method(nullptr) {}; // initialization
  UnloadableMethodHandle(Method* method);

  /*
   * Release the handle.
   */
  inline void release();

  /*
   * Check if method holder is unloaded.
   */
  inline bool is_unloaded() const;

  /*
   * Return the method. Only safe when !is_unloaded().
   */
  inline Method* method() const;

  /*
   * Block unloading, allow method() calls.
   */
  void block_unloading();
};

#endif // SHARE_RUNTIME_UNLOADABLE_METHOD_HANDLE_HPP
