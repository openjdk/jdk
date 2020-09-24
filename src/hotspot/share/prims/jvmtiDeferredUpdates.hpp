/*
 * Copyright (c) 1997, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020 SAP SE. All rights reserved.
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

#ifndef SHARE_PRIMS_JVMTIDEFERREDUPDATES_HPP
#define SHARE_PRIMS_JVMTIDEFERREDUPDATES_HPP

#include "runtime/thread.inline.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/growableArray.hpp"

// Holds updates for compiled frames by JVMTI agents that cannot be performed immediately.
class jvmtiDeferredLocalVariableSet;
class JvmtiDeferredUpdates : public CHeapObj<mtCompiler> {

  // Relocking has to be deferred if the lock owning thread is currently waiting on the monitor.
  int _relock_count_after_wait;

  // Deferred updates of locals, expressions, and monitors
  GrowableArray<jvmtiDeferredLocalVariableSet*> _deferred_locals_updates;

  void inc_relock_count_after_wait() {
    _relock_count_after_wait++;
  }

  int get_and_reset_relock_count_after_wait() {
    int result = _relock_count_after_wait;
    _relock_count_after_wait = 0;
    return result;
  }

  GrowableArray<jvmtiDeferredLocalVariableSet*>* deferred_locals() { return &_deferred_locals_updates; }

  JvmtiDeferredUpdates() :
    _relock_count_after_wait(0),
    _deferred_locals_updates((ResourceObj::set_allocation_type((address) &_deferred_locals_updates,
                              ResourceObj::C_HEAP), 1), mtCompiler) { }

public:
  static void create_for(JavaThread* thread);

  static GrowableArray<jvmtiDeferredLocalVariableSet*>* deferred_locals(JavaThread* jt) {
    return jt->deferred_updates() == NULL ? NULL : jt->deferred_updates()->deferred_locals();
  }

  // Relocking has to be deferred if the lock owning thread is currently waiting on the monitor.
  static int get_and_reset_relock_count_after_wait(JavaThread* jt) {
    return jt->deferred_updates() == NULL ? 0 : jt->deferred_updates()->get_and_reset_relock_count_after_wait();
  }
  static void inc_relock_count_after_wait(JavaThread* thread);
};

#endif // SHARE_PRIMS_JVMTIDEFERREDUPDATES_HPP
