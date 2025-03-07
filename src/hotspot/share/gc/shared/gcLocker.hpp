/*
 * Copyright (c) 1997, 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SHARED_GCLOCKER_HPP
#define SHARE_GC_SHARED_GCLOCKER_HPP

#include "gc/shared/gcCause.hpp"
#include "memory/allStatic.hpp"
#include "runtime/mutex.hpp"

// GCLocker provides synchronization between the garbage collector (GC) and
// threads using JNI critical APIs. When threads enter a critical region (CR),
// certain GC implementations may suspend garbage collection until all such
// threads have exited.
//
// Threads that need to trigger a GC should use the `block()` and `unblock()`
// APIs. `block()` will block the caller and prevent new threads from entering
// the CR.
//
// Threads entering or exiting a CR must call the `enter` and `exit` APIs to
// ensure proper synchronization with the GC.

class GCLocker: public AllStatic {
  static Monitor* _lock;
  static volatile bool _is_gc_request_pending;

#ifdef ASSERT
  // Debug-only: to track the number of java threads in critical-region.
  static uint64_t _verify_in_cr_count;
#endif
  static void enter_slow(JavaThread* current_thread);

public:
  static void initialize();

  // To query current GCLocker state. Can become outdated if called outside a safepoint.
  static bool is_active();

  // For use by Java threads requesting GC.
  static void block();
  static void unblock();

  // For use by Java threads entering/leaving critical-region.
  inline static void enter(JavaThread* current_thread);
  inline static void exit(JavaThread* current_thread);
};

#endif // SHARE_GC_SHARED_GCLOCKER_HPP
