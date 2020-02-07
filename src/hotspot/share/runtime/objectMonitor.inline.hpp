/*
 * Copyright (c) 1998, 2020, Oracle and/or its affiliates. All rights reserved.
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

#include "logging/log.hpp"
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

// Clear _owner field; current value must match old_value.
inline void ObjectMonitor::release_clear_owner(void* old_value) {
  DEBUG_ONLY(void* prev = Atomic::load(&_owner);)
  assert(prev == old_value, "unexpected prev owner=" INTPTR_FORMAT
         ", expected=" INTPTR_FORMAT, p2i(prev), p2i(old_value));
  Atomic::release_store(&_owner, (void*)NULL);
  log_trace(monitorinflation, owner)("release_clear_owner(): mid="
                                     INTPTR_FORMAT ", old_value=" INTPTR_FORMAT,
                                     p2i(this), p2i(old_value));
}

// Simply set _owner field to new_value; current value must match old_value.
// (Simple means no memory sync needed.)
inline void ObjectMonitor::set_owner_from(void* old_value, void* new_value) {
  DEBUG_ONLY(void* prev = Atomic::load(&_owner);)
  assert(prev == old_value, "unexpected prev owner=" INTPTR_FORMAT
         ", expected=" INTPTR_FORMAT, p2i(prev), p2i(old_value));
  Atomic::store(&_owner, new_value);
  log_trace(monitorinflation, owner)("set_owner_from(): mid="
                                     INTPTR_FORMAT ", old_value=" INTPTR_FORMAT
                                     ", new_value=" INTPTR_FORMAT, p2i(this),
                                     p2i(old_value), p2i(new_value));
}

// Simply set _owner field to self; current value must match basic_lock_p.
inline void ObjectMonitor::set_owner_from_BasicLock(void* basic_lock_p, Thread* self) {
  DEBUG_ONLY(void* prev = Atomic::load(&_owner);)
  assert(prev == basic_lock_p, "unexpected prev owner=" INTPTR_FORMAT
         ", expected=" INTPTR_FORMAT, p2i(prev), p2i(basic_lock_p));
  // Non-null owner field to non-null owner field is safe without
  // cmpxchg() as long as all readers can tolerate either flavor.
  Atomic::store(&_owner, self);
  log_trace(monitorinflation, owner)("set_owner_from_BasicLock(): mid="
                                     INTPTR_FORMAT ", basic_lock_p="
                                     INTPTR_FORMAT ", new_value=" INTPTR_FORMAT,
                                     p2i(this), p2i(basic_lock_p), p2i(self));
}

// Try to set _owner field to new_value if the current value matches
// old_value. Otherwise, does not change the _owner field. Returns
// the prior value of the _owner field.
inline void* ObjectMonitor::try_set_owner_from(void* old_value, void* new_value) {
  void* prev = Atomic::cmpxchg(&_owner, old_value, new_value);
  if (prev == old_value) {
    log_trace(monitorinflation, owner)("try_set_owner_from(): mid="
                                       INTPTR_FORMAT ", prev=" INTPTR_FORMAT
                                       ", new=" INTPTR_FORMAT, p2i(this),
                                       p2i(prev), p2i(new_value));
  }
  return prev;
}

// The _next_om field can be concurrently read and modified so we
// use Atomic operations to disable compiler optimizations that
// might try to elide loading and/or storing this field.

inline ObjectMonitor* ObjectMonitor::next_om() const {
  return Atomic::load(&_next_om);
}

// Simply set _next_om field to new_value.
inline void ObjectMonitor::set_next_om(ObjectMonitor* new_value) {
  Atomic::store(&_next_om, new_value);
}

// Try to set _next_om field to new_value if the current value matches
// old_value. Otherwise, does not change the _next_om field. Returns
// the prior value of the _next_om field.
inline ObjectMonitor* ObjectMonitor::try_set_next_om(ObjectMonitor* old_value, ObjectMonitor* new_value) {
  return Atomic::cmpxchg(&_next_om, old_value, new_value);
}

#endif // SHARE_RUNTIME_OBJECTMONITOR_INLINE_HPP
