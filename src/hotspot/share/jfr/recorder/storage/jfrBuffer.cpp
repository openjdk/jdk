/*
 * Copyright (c) 2011, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "jfr/recorder/storage/jfrBuffer.hpp"
#include "runtime/javaThread.hpp"

static const u1* const TOP_CRITICAL_SECTION = nullptr;

JfrBuffer::JfrBuffer() : _next(nullptr),
                         _identity(nullptr),
                         _pos(nullptr),
                         _top(nullptr),
                         _size(0),
                         _header_size(0),
                         _flags(0),
                         _context(0)
                         LP64_ONLY(COMMA _pad(0)) {}

void JfrBuffer::initialize(size_t header_size, size_t size) {
  assert(_next == nullptr, "invariant");
  assert(_identity == nullptr, "invariant");
  assert(header_size <= max_jushort, "invariant");
  _header_size = static_cast<u2>(header_size);
  _size = size;
  set_pos(start());
  set_top(start());
  assert(free_size() == size, "invariant");
  assert(!transient(), "invariant");
  assert(!lease(), "invariant");
  assert(!retired(), "invariant");
}

void JfrBuffer::reinitialize() {
  acquire_critical_section_top();
  set_pos(start());
  release_critical_section_top(start());
  clear_retired();
}

const u1* JfrBuffer::top() const {
  return Atomic::load_acquire(&_top);
}

const u1* JfrBuffer::stable_top() const {
  const u1* current_top;
  do {
    current_top = top();
  } while (TOP_CRITICAL_SECTION == current_top);
  return current_top;
}

void JfrBuffer::set_top(const u1* new_top) {
  assert(new_top <= end(), "invariant");
  assert(new_top >= start(), "invariant");
  Atomic::release_store(&_top, new_top);
}

const u1* JfrBuffer::acquire_critical_section_top() const {
  do {
    const u1* current_top = stable_top();
    assert(current_top != TOP_CRITICAL_SECTION, "invariant");
    if (Atomic::cmpxchg(&_top, current_top, TOP_CRITICAL_SECTION) == current_top) {
      return current_top;
    }
  } while (true);
}

void JfrBuffer::release_critical_section_top(const u1* new_top) {
  assert(new_top != TOP_CRITICAL_SECTION, "invariant");
  assert(top() == TOP_CRITICAL_SECTION, "invariant");
  set_top(new_top);
}

bool JfrBuffer::acquired_by(const void* id) const {
  return identity() == id;
}

bool JfrBuffer::acquired_by_self() const {
  return acquired_by(Thread::current());
}

void JfrBuffer::acquire(const void* id) {
  assert(id != nullptr, "invariant");
  const void* current_id;
  do {
    current_id = identity();
  } while (current_id != nullptr || Atomic::cmpxchg(&_identity, current_id, id) != current_id);
}

bool JfrBuffer::try_acquire(const void* id) {
  assert(id != nullptr, "invariant");
  const void* const current_id = identity();
  return current_id == nullptr && Atomic::cmpxchg(&_identity, current_id, id) == current_id;
}

void JfrBuffer::set_identity(const void* id) {
  assert(id != nullptr, "invariant");
  assert(_identity == nullptr, "invariant");
  OrderAccess::storestore();
  _identity = id;
}

void JfrBuffer::release() {
  assert(identity() != nullptr, "invariant");
  Atomic::release_store(&_identity, (const void*)nullptr);
}

#ifdef ASSERT
static bool validate_to(const JfrBuffer* const to, size_t size) {
  assert(to != nullptr, "invariant");
  assert(to->acquired_by_self(), "invariant");
  assert(to->free_size() >= size, "invariant");
  return true;
}

static bool validate_this(const JfrBuffer* const t, size_t size) {
  assert(t->acquired_by_self(), "invariant");
  assert(t->top() == TOP_CRITICAL_SECTION, "invariant");
  return true;
}
#endif // ASSERT

void JfrBuffer::move(JfrBuffer* const to, size_t size) {
  assert(validate_to(to, size), "invariant");
  const u1* const current_top = acquire_critical_section_top();
  assert(validate_this(this, size), "invariant");
  const size_t actual_size = pos() - current_top;
  assert(actual_size <= size, "invariant");
  if (actual_size > 0) {
    memcpy(to->pos(), current_top, actual_size);
    to->set_pos(actual_size);
  }
  to->release();
  set_pos(start());
  release_critical_section_top(start());
}

size_t JfrBuffer::discard() {
  const u1* const position = pos();
  // stable_top() provides acquire semantics for pos()
  const u1* const current_top = stable_top();
  set_top(position);
  return position - current_top;
}

size_t JfrBuffer::unflushed_size() const {
  const u1* const position = pos();
  // stable_top() provides acquire semantics for pos()
  return position - stable_top();
}

enum FLAG {
  RETIRED = 1,
  TRANSIENT = 2,
  LEASE = 4
};

inline u1 load(const volatile u1* dest) {
  assert(dest != nullptr, "invariant");
  return Atomic::load_acquire(dest);
}

inline void set(u1* dest, u1 data) {
  assert(dest != nullptr, "invariant");
  OrderAccess::storestore();
  *dest |= data;
}

inline void clear(u1* dest, u1 data) {
  assert(dest != nullptr, "invariant");
  OrderAccess::storestore();
  *dest ^= data;
}

inline bool test(const u1* dest, u1 data) {
  return data == (load(dest) & data);
}

bool JfrBuffer::transient() const {
  return test(&_flags, TRANSIENT);
}

void JfrBuffer::set_transient() {
  assert(acquired_by_self(), "invariant");
  set(&_flags, TRANSIENT);
  assert(transient(), "invariant");
}

void JfrBuffer::clear_transient() {
  if (transient()) {
    assert(acquired_by_self(), "invariant");
    clear(&_flags, TRANSIENT);
  }
  assert(!transient(), "invariant");
}

bool JfrBuffer::lease() const {
  return test(&_flags, LEASE);
}

void JfrBuffer::set_lease() {
  assert(acquired_by_self(), "invariant");
  set(&_flags, LEASE);
  assert(lease(), "invariant");
}

void JfrBuffer::clear_lease() {
  if (lease()) {
    assert(acquired_by_self(), "invariant");
    clear(&_flags, LEASE);
  }
  assert(!lease(), "invariant");
}

bool JfrBuffer::retired() const {
  return test(&_flags, RETIRED);
}

void JfrBuffer::set_retired() {
  set(&_flags, RETIRED);
}

void JfrBuffer::clear_retired() {
  if (retired()) {
    clear(&_flags, RETIRED);
  }
}

u1 JfrBuffer::context() const {
  return load(&_context);
}

void JfrBuffer::set_context(u1 context) {
  set(&_context, context);
}

void JfrBuffer::clear_context() {
  set(&_context, 0);
}

ByteSize JfrBuffer::pos_offset() {
  return byte_offset_of(JfrBuffer, _pos);
}

ByteSize JfrBuffer::flags_offset() {
  return byte_offset_of(JfrBuffer, _flags);
}