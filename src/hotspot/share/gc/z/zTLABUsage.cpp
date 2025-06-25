/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/z/zTLABUsage.hpp"
#include "logging/log.hpp"
#include "runtime/atomic.hpp"

ZTLABUsage::ZTLABUsage()
  : _used(0),
    _used_history() {}

void ZTLABUsage::increase_used(size_t size) {
  Atomic::add(&_used, size, memory_order_relaxed);
}

void ZTLABUsage::decrease_used(size_t size) {
  precond(size <= _used);

  Atomic::sub(&_used, size, memory_order_relaxed);
}

void ZTLABUsage::reset() {
  const size_t used = Atomic::xchg(&_used, (size_t) 0);

  // Avoid updates when nothing has been allocated since the last YC
  if (used == 0) {
    return;
  }

  // Save the old values for logging
  const size_t old_tlab_used = tlab_used();
  const size_t old_tlab_capacity = tlab_capacity();

  // Update the usage history with the current value
  _used_history.add(used);

  log_debug(gc, tlab)("TLAB usage update: used %zuM -> %zuM, capacity: %zuM -> %zuM",
                      old_tlab_used / M,
                      tlab_used() / M,
                      old_tlab_capacity / M,
                      tlab_capacity() / M);
  }

size_t ZTLABUsage::tlab_used() const {
  return _used_history.last();
}

size_t ZTLABUsage::tlab_capacity() const {
  return _used_history.davg();
}
