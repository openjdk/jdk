/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_JFR_UTILITIES_JFRVERSIONSYSTEM_INLINE_HPP
#define SHARE_JFR_UTILITIES_JFRVERSIONSYSTEM_INLINE_HPP

#include "jfr/utilities/jfrSpinlockHelper.hpp"
#include "jfr/utilities/jfrVersionSystem.hpp"
#include "runtime/atomic.hpp"
#include "runtime/os.inline.hpp"
#include "runtime/vm_version.hpp"

inline JfrVersionSystem::Node::Node() : _next(NULL), _version(0), _live(true) {}

inline traceid JfrVersionSystem::Node::version() const {
  return _version;
}

inline void JfrVersionSystem::Node::set(traceid version) {
  Atomic::release_store_fence(&_version, version);
}

inline JfrVersionSystem::JfrVersionSystem() : _tip(), _head(NULL), _spinlock(0) {
  _tip._value = 1;
}

inline JfrVersionSystem::~JfrVersionSystem() {
  reset();
}

inline void JfrVersionSystem::reset() {
  NodePtr node = _head;
  while (node != NULL) {
    NodePtr next = node->_next;
    delete node;
    node = next;
  }
  _head = NULL;
  _tip._value = 1;
}

inline JfrVersionSystem::Type JfrVersionSystem::tip() const {
  return Atomic::load(&_tip._value);
}

inline JfrVersionSystem::Type JfrVersionSystem::increment() {
  if (!VM_Version::supports_cx8()) {
    JfrSpinlockHelper lock(&_spinlock);
    return ++_tip._value;
  }
  traceid cmp;
  traceid xchg;
  do {
    cmp = _tip._value;
    xchg = cmp + 1;
  } while (Atomic::cmpxchg(&_tip._value, cmp, xchg) != cmp);
  return xchg;
}

inline JfrVersionSystem::NodePtr JfrVersionSystem::acquire() {
  NodePtr node = _head;
  // free
  while (node != NULL) {
    if (node->_live || Atomic::cmpxchg(&node->_live, false, true)) {
      node = node->_next;
      continue;
    }
    assert(node->_version == 0, "invariant");
    return node;
  }
  // new
  node = new Node();
  NodePtr next;
  do {
    next = _head;
    node->_next = next;
  } while (Atomic::cmpxchg(&_head, next, node) != next);
  return node;
}

inline void JfrVersionSystem::release(JfrVersionSystem::NodePtr node) {
  assert(node != NULL, "invariant");
  assert(node->_live, "invariant");
  Atomic::release_store_fence(&node->_version, (traceid)0);
  node->_live = false;
}

inline JfrVersionSystem::NodePtr
JfrVersionSystem::synchronize_with(JfrVersionSystem::Type version, JfrVersionSystem::NodePtr node) const {
  assert(version <= tip(), "invariant");
  while (node != NULL) {
    const Type checkedout = Atomic::load_acquire(&node->_version);
    if (checkedout > 0 && checkedout < version) {
      return node;
    }
    node = node->_next;
  }
  return NULL;
}

inline void JfrVersionSystem::await(JfrVersionSystem::Type  version) {
  assert(version > 0, "invariant");
  static const int backoff_unit_ns = 10;
  int backoff_factor = 1;
  NodePtr last = _head;
  while (true) {
    last = synchronize_with(version, last);
    if (last == NULL) {
      return;
    }
    os::naked_short_nanosleep(backoff_unit_ns * backoff_factor++);
  }
}

inline JfrVersionSystem::Handle JfrVersionSystem::get_handle() {
  return Handle(this);
}

inline JfrVersionSystem::Handle JfrVersionSystem::checkout_handle() {
  Handle handle(this);
  handle.checkout();
  return handle;
}

inline JfrVersionSystem::Handle::Handle(JfrVersionSystem* system) : _system(system), _node(system->acquire()) {}

inline JfrVersionSystem::Handle::Handle() : _system(NULL), _node(NULL) {}

inline JfrVersionSystem::Handle::~Handle() {
  if (_node != NULL) {
    _system->release(_node);
  }
}

inline void JfrVersionSystem::Handle::checkout() {
  assert(_node != NULL, "invariant");
  _node->set(_system->tip());
}

inline JfrVersionSystem::Type JfrVersionSystem::Handle::increment() {
  assert(_node != NULL, "invariant");
  const Type version = _system->increment();
  assert(version > _node->version(), "invariant");
  return version;
}

inline void JfrVersionSystem::Handle::release() {
  assert(_node != NULL, "invariant");
  _system->release(_node);
  _node = NULL;
}

inline void JfrVersionSystem::Handle::await(JfrVersionSystem::Type  version) {
  _system->await(version);
}

#ifdef ASSERT
inline bool JfrVersionSystem::is_registered(JfrVersionSystem::Type version) const {
  NodePtr node = _head;
  while (node != NULL) {
    if (Atomic::load_acquire(&node->_version) == version) {
      return true;
    }
    node = node->_next;
  }
  return false;
}

inline bool JfrVersionSystem::Handle::is_tracked() const {
  assert(_node != NULL, "invariant");
  const Type current_version = _node->version();
  return current_version != 0 && _system->is_registered(current_version);
}
#endif // ASSERT

#endif // SHARE_JFR_UTILITIES_JFRVERSIONSYSTEM_INLINE_HPP
