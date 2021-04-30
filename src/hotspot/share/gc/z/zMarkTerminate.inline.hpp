/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
#include "logging/log.hpp"
#include "runtime/atomic.hpp"

inline ZMarkTerminate::ZMarkTerminate() :
    _nworkers(0),
    _nworking(0),
    _resurrected(false),
    _lock() {}

inline void ZMarkTerminate::reset(uint nworkers) {
  Atomic::store(&_nworkers, nworkers);
  Atomic::store(&_nworking, nworkers);
}

inline void ZMarkTerminate::leave() {
  SuspendibleThreadSetLeaver sts_leaver;
  ZLocker<ZConditionLock> locker(&_lock);
  Atomic::store(&_nworking, _nworking - 1);
  if (_nworking == 0) {
    // Last thread leaving; notify waiters
    _lock.notify_all();
  }
}

inline bool ZMarkTerminate::try_terminate() {
  SuspendibleThreadSetLeaver sts_leaver;
  ZLocker<ZConditionLock> locker(&_lock);
  Atomic::store(&_nworking, _nworking - 1);
  if (_nworking == 0) {
    // Last thread entering termination: success
    _lock.notify_all();
    return true;
  }
  _lock.wait();
  if (_nworking == 0) {
    // We got notified all work is done; terminate
    return true;
  }
  // We either got notification about more work
  // or got a spurious wakeup; don't terminate
  Atomic::store(&_nworking, _nworking + 1);
  return false;
}

inline void ZMarkTerminate::wake_up() {
  if (Atomic::load(&_nworking) == Atomic::load(&_nworkers)) {
    // Everyone is working
    return;
  }

  ZLocker<ZConditionLock> locker(&_lock);
  _lock.notify();
}

inline void ZMarkTerminate::set_resurrected(bool value) {
  // Update resurrected if it changed
  if (resurrected() != value) {
    Atomic::store(&_resurrected, value);
    if (value) {
      log_debug(gc, marking)("Resurrection broke termination");
    } else {
      log_debug(gc, marking)("Try terminate after resurrection");
    }
  }
}

inline bool ZMarkTerminate::resurrected() {
  return Atomic::load(&_resurrected);
}

#endif // SHARE_GC_Z_ZMARKTERMINATE_INLINE_HPP
