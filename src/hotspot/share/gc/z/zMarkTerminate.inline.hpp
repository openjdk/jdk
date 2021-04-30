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

#include "runtime/atomic.hpp"

inline ZMarkTerminate::ZMarkTerminate() :
    _nworkers(0),
    _nworking(0),
    _resurrected(false) {}

inline void ZMarkTerminate::reset(uint nworkers) {
  _nworkers = _nworking = nworkers;
}

inline bool ZMarkTerminate::enter() {
  return Atomic::sub(&_nworking, 1u) == 0;
}

inline bool ZMarkTerminate::try_exit() {
  uint nworking = Atomic::load(&_nworking);

  for (;;) {
    if (nworking == 0) {
      return false;
    }

    const uint new_nworking = nworking + 1;
    const uint prev_nworking = Atomic::cmpxchg(&_nworking, nworking, new_nworking);
    if (prev_nworking == nworking) {
      // Success
      return true;
    }

    // Retry
    nworking = prev_nworking;
  }
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
