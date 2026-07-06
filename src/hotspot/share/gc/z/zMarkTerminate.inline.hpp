/*
 * Copyright (c) 2017, 2026, Oracle and/or its affiliates. All rights reserved.
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
 */

#ifndef SHARE_GC_Z_ZMARKTERMINATE_INLINE_HPP
#define SHARE_GC_Z_ZMARKTERMINATE_INLINE_HPP

#include "gc/z/zMarkTerminate.hpp"

#include "gc/shared/suspendibleThreadSet.hpp"
#include "gc/z/zLock.inline.hpp"
#include "gc/z/zMarkStack.hpp"
#include "logging/log.hpp"
#include "runtime/osThread.hpp"
#include "runtime/thread.inline.hpp"

inline ZMarkTerminate::ZMarkTerminate()
  : _nworkers(0),
    _nworking(0),
    _nawakening(0),
    _resurrected(false),
    _lock() {}

inline void ZMarkTerminate::reset(uint nworkers) {
  _nworkers = nworkers;
  _nworking.store_relaxed(nworkers);
  _nawakening.store_relaxed(0u);
}

inline void ZMarkTerminate::leave() {
  SuspendibleThreadSetLeaver sts_leaver;
  ZLocker<ZConditionLock> locker(&_lock);

  if (_nworking.sub_then_fetch(1u, memory_order_relaxed) == 0) {
    // Last thread leaving; notify waiters
    _lock.notify_all();
  }
}

inline void ZMarkTerminate::maybe_reduce_stripes(ZMarkStripeSet* stripes, size_t used_nstripes) {
  const size_t nstripes = stripes->nstripes();
  if (used_nstripes == nstripes && nstripes > 1u) {
    stripes->try_set_nstripes(nstripes, nstripes >> 1);
  }
}

inline bool ZMarkTerminate::try_terminate(ZMarkStripeSet* stripes, size_t used_nstripes) {
  SuspendibleThreadSetLeaver sts_leaver;
  ZLocker<ZConditionLock> locker(&_lock);

  if (_nworking.sub_then_fetch(1u, memory_order_relaxed) == 0) {
    // Last thread entering termination: success
    _lock.notify_all();
    return true;
  }

  // If a worker runs out of work, it might be a sign that we have too many stripes
  // hiding work. Try to reduce the number of stripes if possible.
  maybe_reduce_stripes(stripes, used_nstripes);
  _lock.wait();

  // We either got notification about more work
  // or got a spurious wakeup; don't terminate
  if (_nawakening.load_relaxed() > 0) {
    _nawakening.sub_then_fetch(1u, memory_order_relaxed);
  }

  if (_nworking.load_relaxed() == 0) {
    // We got notified all work is done; terminate
    return true;
  }

  _nworking.add_then_fetch(1u, memory_order_relaxed);

  return false;
}

inline void ZMarkTerminate::wake_up() {
  const uint nworking = _nworking.load_relaxed();
  const uint nawakening = _nawakening.load_relaxed();
  if (nworking + nawakening == _nworkers) {
    // Everyone is working or about to
    return;
  }

  if (nworking == 0) {
    // Marking when marking task is not active
    return;
  }

  ZLocker<ZConditionLock> locker(&_lock);
  if (_nworking.load_relaxed() + _nawakening.load_relaxed() != _nworkers) {
    // Everyone is not working
    _nawakening.add_then_fetch(1u, memory_order_relaxed);
    _lock.notify();
  }
}

inline bool ZMarkTerminate::saturated() const {
  const uint nworking = _nworking.load_relaxed();
  const uint nawakening = _nawakening.load_relaxed();

  return nworking + nawakening == _nworkers;
}

inline void ZMarkTerminate::set_resurrected(bool value) {
  // Update resurrected if it changed
  if (resurrected() != value) {
    _resurrected.store_relaxed(value);
    if (value) {
      log_debug(gc, marking)("Resurrection broke termination");
    } else {
      log_debug(gc, marking)("Try terminate after resurrection");
    }
  }
}

inline bool ZMarkTerminate::resurrected() const {
  return _resurrected.load_relaxed();
}

#endif // SHARE_GC_Z_ZMARKTERMINATE_INLINE_HPP
