/*
 * Copyright (c) 1998, 2005, Oracle and/or its affiliates. All rights reserved.
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

inline intptr_t ObjectMonitor::is_entered(TRAPS) const {
  if (THREAD == _owner || THREAD->is_lock_owned((address) _owner)) {
    return 1;
  }
  return 0;
}

inline markOop ObjectMonitor::header() const {
  return _header;
}

inline void ObjectMonitor::set_header(markOop hdr) {
  _header = hdr;
}

inline intptr_t ObjectMonitor::count() const {
  return _count;
}

inline void ObjectMonitor::set_count(intptr_t count) {
  _count= count;
}

inline intptr_t ObjectMonitor::waiters() const {
  return _waiters;
}

inline void* ObjectMonitor::owner() const {
  return _owner;
}

inline void ObjectMonitor::clear() {
  assert(_header, "Fatal logic error in ObjectMonitor header!");
  assert(_count == 0, "Fatal logic error in ObjectMonitor count!");
  assert(_waiters == 0, "Fatal logic error in ObjectMonitor waiters!");
  assert(_recursions == 0, "Fatal logic error in ObjectMonitor recursions!");
  assert(_object, "Fatal logic error in ObjectMonitor object!");
  assert(_owner == 0, "Fatal logic error in ObjectMonitor owner!");

  _header = NULL;
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

inline bool ObjectMonitor::check(TRAPS) {
  if (THREAD != _owner) {
    if (THREAD->is_lock_owned((address) _owner)) {
      _owner = THREAD;  // regain ownership of inflated monitor
      OwnerIsThread = 1 ;
      assert (_recursions == 0, "invariant") ;
    } else {
      check_slow(THREAD);
      return false;
    }
  }
  return true;
}


// return number of threads contending for this monitor
inline intptr_t ObjectMonitor::contentions() const {
  return _count;
}

inline void ObjectMonitor::set_owner(void* owner) {
  _owner = owner;
  _recursions = 0;
  _count = 0;
}

