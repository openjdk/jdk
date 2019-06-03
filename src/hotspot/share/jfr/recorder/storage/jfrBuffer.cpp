/*
 * Copyright (c) 2011, 2019, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "jfr/recorder/storage/jfrBuffer.hpp"
#include "runtime/atomic.hpp"
#include "runtime/orderAccess.hpp"
#include "runtime/thread.inline.hpp"

static const u1* const MUTEX_CLAIM = NULL;

JfrBuffer::JfrBuffer() : _next(NULL),
                         _prev(NULL),
                         _identity(NULL),
                         _pos(NULL),
                         _top(NULL),
                         _flags(0),
                         _header_size(0),
                         _size(0) {}

bool JfrBuffer::initialize(size_t header_size, size_t size, const void* id /* NULL */) {
  _header_size = (u2)header_size;
  _size = (u4)(size / BytesPerWord);
  assert(_identity == NULL, "invariant");
  _identity = id;
  set_pos(start());
  set_top(start());
  assert(_next == NULL, "invariant");
  assert(free_size() == size, "invariant");
  assert(!transient(), "invariant");
  assert(!lease(), "invariant");
  assert(!retired(), "invariant");
  return true;
}

void JfrBuffer::reinitialize() {
  assert(!lease(), "invariant");
  assert(!transient(), "invariant");
  set_pos(start());
  clear_retired();
  set_top(start());
}

void JfrBuffer::concurrent_reinitialization() {
  concurrent_top();
  assert(!lease(), "invariant");
  assert(!transient(), "invariant");
  set_pos(start());
  set_concurrent_top(start());
  clear_retired();
}

size_t JfrBuffer::discard() {
  size_t discard_size = unflushed_size();
  set_top(pos());
  return discard_size;
}

const u1* JfrBuffer::stable_top() const {
  const u1* current_top;
  do {
    current_top = OrderAccess::load_acquire(&_top);
  } while (MUTEX_CLAIM == current_top);
  return current_top;
}

const u1* JfrBuffer::top() const {
  return _top;
}

void JfrBuffer::set_top(const u1* new_top) {
  _top = new_top;
}

const u1* JfrBuffer::concurrent_top() const {
  do {
    const u1* current_top = stable_top();
    if (Atomic::cmpxchg(MUTEX_CLAIM, &_top, current_top) == current_top) {
      return current_top;
    }
  } while (true);
}

void JfrBuffer::set_concurrent_top(const u1* new_top) {
  assert(new_top != MUTEX_CLAIM, "invariant");
  assert(new_top <= end(), "invariant");
  assert(new_top >= start(), "invariant");
  assert(top() == MUTEX_CLAIM, "invariant");
  OrderAccess::release_store(&_top, new_top);
}

size_t JfrBuffer::unflushed_size() const {
  return pos() - stable_top();
}

void JfrBuffer::acquire(const void* id) {
  assert(id != NULL, "invariant");
  const void* current_id;
  do {
    current_id = OrderAccess::load_acquire(&_identity);
  } while (current_id != NULL || Atomic::cmpxchg(id, &_identity, current_id) != current_id);
}

bool JfrBuffer::try_acquire(const void* id) {
  assert(id != NULL, "invariant");
  const void* const current_id = OrderAccess::load_acquire(&_identity);
  return current_id == NULL && Atomic::cmpxchg(id, &_identity, current_id) == current_id;
}

void JfrBuffer::release() {
  OrderAccess::release_store(&_identity, (const void*)NULL);
}

bool JfrBuffer::acquired_by(const void* id) const {
  return identity() == id;
}

bool JfrBuffer::acquired_by_self() const {
  return acquired_by(Thread::current());
}

#ifdef ASSERT
static bool validate_to(const JfrBuffer* const to, size_t size) {
  assert(to != NULL, "invariant");
  assert(to->acquired_by_self(), "invariant");
  assert(to->free_size() >= size, "invariant");
  return true;
}

static bool validate_concurrent_this(const JfrBuffer* const t, size_t size) {
  assert(t->top() == MUTEX_CLAIM, "invariant");
  return true;
}

static bool validate_this(const JfrBuffer* const t, size_t size) {
  assert(t->top() + size <= t->pos(), "invariant");
  return true;
}
#endif // ASSERT

void JfrBuffer::move(JfrBuffer* const to, size_t size) {
  assert(validate_to(to, size), "invariant");
  assert(validate_this(this, size), "invariant");
  const u1* current_top = top();
  assert(current_top != NULL, "invariant");
  memcpy(to->pos(), current_top, size);
  to->set_pos(size);
  to->release();
  set_top(current_top + size);
}

void JfrBuffer::concurrent_move_and_reinitialize(JfrBuffer* const to, size_t size) {
  assert(validate_to(to, size), "invariant");
  const u1* current_top = concurrent_top();
  assert(validate_concurrent_this(this, size), "invariant");
  const size_t actual_size = MIN2(size, (size_t)(pos() - current_top));
  assert(actual_size <= size, "invariant");
  memcpy(to->pos(), current_top, actual_size);
  to->set_pos(actual_size);
  set_pos(start());
  to->release();
  set_concurrent_top(start());
}

enum FLAG {
  RETIRED = 1,
  TRANSIENT = 2,
  LEASE = 4
};

bool JfrBuffer::transient() const {
  return (u1)TRANSIENT == (_flags & (u1)TRANSIENT);
}

void JfrBuffer::set_transient() {
  _flags |= (u1)TRANSIENT;
  assert(transient(), "invariant");
}

void JfrBuffer::clear_transient() {
  if (transient()) {
    _flags ^= (u1)TRANSIENT;
  }
  assert(!transient(), "invariant");
}

bool JfrBuffer::lease() const {
  return (u1)LEASE == (_flags & (u1)LEASE);
}

void JfrBuffer::set_lease() {
  _flags |= (u1)LEASE;
  assert(lease(), "invariant");
}

void JfrBuffer::clear_lease() {
  if (lease()) {
    _flags ^= (u1)LEASE;
  }
  assert(!lease(), "invariant");
}

static u2 load_acquire_flags(const u2* const flags) {
  return OrderAccess::load_acquire(flags);
}

static void release_store_flags(u2* const flags, u2 new_flags) {
  OrderAccess::release_store(flags, new_flags);
}

bool JfrBuffer::retired() const {
  return (u1)RETIRED == (load_acquire_flags(&_flags) & (u1)RETIRED);
}

void JfrBuffer::set_retired() {
  const u2 new_flags = load_acquire_flags(&_flags) | (u1)RETIRED;
  release_store_flags(&_flags, new_flags);
}

void JfrBuffer::clear_retired() {
  u2 new_flags = load_acquire_flags(&_flags);
  if ((u1)RETIRED == (new_flags & (u1)RETIRED)) {
    new_flags ^= (u1)RETIRED;
    release_store_flags(&_flags, new_flags);
  }
}
