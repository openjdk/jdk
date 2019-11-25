/*
 * Copyright (c) 1998, 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_RUNTIME_OBJECTMONITOR_INLINE_HPP
#define SHARE_RUNTIME_OBJECTMONITOR_INLINE_HPP

#include "runtime/atomic.hpp"

inline intptr_t ObjectMonitor::is_entered(TRAPS) const {
  if (THREAD == _owner || THREAD->is_lock_owned((address) _owner)) {
    return 1;
  }
  return 0;
}

inline markWord ObjectMonitor::header() const {
  return Atomic::load(&_header);
}

inline volatile markWord* ObjectMonitor::header_addr() {
  assert((intptr_t)this == (intptr_t)&_header, "sync code expects this");
  return &_header;
}

inline void ObjectMonitor::set_header(markWord hdr) {
  Atomic::store(&_header, hdr);
}

inline jint ObjectMonitor::waiters() const {
  return _waiters;
}

inline void* ObjectMonitor::owner() const {
  return _owner;
}

inline void ObjectMonitor::clear() {
  assert(Atomic::load(&_header).value() != 0, "must be non-zero");
  assert(_contentions == 0, "must be 0: contentions=%d", _contentions);
  assert(_waiters == 0, "must be 0: waiters=%d", _waiters);
  assert(_recursions == 0, "must be 0: recursions=" INTX_FORMAT, _recursions);
  assert(_object != NULL, "must be non-NULL");
  assert(_owner == NULL, "must be NULL: owner=" INTPTR_FORMAT, p2i(_owner));

  Atomic::store(&_header, markWord::zero());
  _object = NULL;
}

inline void* ObjectMonitor::object() const {
  return _object;
}

inline void* ObjectMonitor::object_addr() {
  return (void *)(&_object);
}

inline void ObjectMonitor::set_object(void* obj) {
  _object = obj;
}

// return number of threads contending for this monitor
inline jint ObjectMonitor::contentions() const {
  return _contentions;
}

inline void ObjectMonitor::set_owner(void* owner) {
  _owner = owner;
}

#endif // SHARE_RUNTIME_OBJECTMONITOR_INLINE_HPP
