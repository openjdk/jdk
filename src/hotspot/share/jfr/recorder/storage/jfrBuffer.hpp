/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_JFR_RECORDER_STORAGE_JFRBUFFER_HPP
#define SHARE_VM_JFR_RECORDER_STORAGE_JFRBUFFER_HPP

#include "memory/allocation.hpp"

//
// Represents a piece of committed memory.
//
// u1* _pos <-- next store position
// u1* _top <-- next unflushed position
//
// const void* _identity <-- acquired by
//
// Must be the owner before attempting stores.
// Use acquire() and/or try_acquire() for exclusive access
// to the (entire) buffer (cas identity).
//
// Stores to the buffer should uphold transactional semantics.
// A new _pos must be updated only after all intended stores have completed.
// The relation between _pos and _top must hold atomically,
// e.g. the delta must always be fully parsable.
// _top can move concurrently by other threads but is always <= _pos.
//
class JfrBuffer {
 private:
  JfrBuffer* _next;
  JfrBuffer* _prev;
  const void* volatile _identity;
  u1* _pos;
  mutable const u1* volatile _top;
  u2 _flags;
  u2 _header_size;
  u4 _size;

  const u1* stable_top() const;
  void clear_flags();

 public:
  JfrBuffer();
  bool initialize(size_t header_size, size_t size, const void* id = NULL);
  void reinitialize();
  void concurrent_reinitialization();
  size_t discard();
  JfrBuffer* next() const {
    return _next;
  }

  JfrBuffer* prev() const {
    return _prev;
  }

  void set_next(JfrBuffer* next) {
    _next = next;
  }

  void set_prev(JfrBuffer* prev) {
    _prev = prev;
  }

  const u1* start() const {
    return ((const u1*)this) + _header_size;
  }

  u1* start() {
    return ((u1*)this) + _header_size;
  }

  const u1* end() const {
    return start() + size();
  }

  const u1* pos() const {
    return _pos;
  }

  u1* pos() {
    return _pos;
  }

  u1** pos_address() {
    return (u1**)&_pos;
  }

  void set_pos(u1* new_pos) {
    assert(new_pos <= end(), "invariant");
    _pos = new_pos;
  }

  void set_pos(size_t size) {
    assert(_pos + size <= end(), "invariant");
    _pos += size;
  }

  const u1* top() const;
  void set_top(const u1* new_top);
  const u1* concurrent_top() const;
  void set_concurrent_top(const u1* new_top);

  size_t header_size() const {
    return _header_size;
  }

  size_t size() const {
    return _size * BytesPerWord;
  }

  size_t total_size() const {
    return header_size() + size();
  }

  size_t free_size() const {
    return end() - pos();
  }

  size_t unflushed_size() const;

  bool empty() const {
    return pos() == start();
  }

  const void* identity() const {
    return _identity;
  }

  void clear_identity();

  void acquire(const void* id);
  bool try_acquire(const void* id);
  void release();

  void move(JfrBuffer* const to, size_t size);
  void concurrent_move_and_reinitialize(JfrBuffer* const to, size_t size);

  bool transient() const;
  void set_transient();
  void clear_transient();

  bool lease() const;
  void set_lease();
  void clear_lease();

  bool retired() const;
  void set_retired();
  void clear_retired();

  debug_only(bool acquired_by_self() const;)
};

class JfrAgeNode : public JfrBuffer {
 private:
  JfrBuffer* _retired;

 public:
  JfrAgeNode() : _retired(NULL) {}
  void set_retired_buffer(JfrBuffer* retired) {
    _retired = retired;
  }
  JfrBuffer* retired_buffer() const {
    return _retired;
  }
};

#endif // SHARE_VM_JFR_RECORDER_STORAGE_JFRBUFFER_HPP
