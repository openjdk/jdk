/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_JFR_RECORDER_STORAGE_JFRBUFFER_HPP
#define SHARE_JFR_RECORDER_STORAGE_JFRBUFFER_HPP

#include "memory/allocation.hpp"
#include "runtime/atomicAccess.hpp"
#include "utilities/sizes.hpp"

//
// Represents a piece of committed memory.
//
// Use acquire() and/or try_acquire() for exclusive access
// to the buffer (cas identity). This is a precondition
// for attempting stores.
//
// u1* _pos <-- last committed position
// u1* _top <-- next unflushed position
//
// Stores must uphold transactional semantics. This means that _pos
// must be updated only after all intended stores have completed already.
// The relation between _pos and _top must hold atomically,
// e.g. the delta must always be fully parsable.
// _top can move concurrently by other threads but is always <= _pos.
//
// The _flags field holds generic tags applicable to all subsystems.
//
// The _context field can be used to set subsystem specific tags onto a buffer.
//
// Memory ordering:
//
//  Method                 Owner thread             Other threads
//  ---------------------------------------------------------------
//  acquire()              Acquire semantics (cas)  Acquire semantics (cas)
//  try_acquire()          Acquire semantics (cas)  Acquire semantics (cas)
//  release()              Release semantics        Release semantics
//  pos()                  Plain load               Acquire semantics needed at call sites
//  set_pos()              Release semantics        N/A
//  top()                  Acquire semantics        Acquire semantics
//  set_top()              Release semantics        Release semantics
//  acquire_crit_sec_top() Acquire semantics (cas)  Acquire semantics (cas)
//  release_crit_sec_top() Release semantics        Release semantics
//

class JfrBuffer {
 public:
  JfrBuffer* _next; // list support
 private:
  const void* _identity;
  u1* _pos;
  mutable const u1* _top;
  size_t _size;
  u2 _header_size;
  u1 _flags;
  u1 _context;
  LP64_ONLY(const u4 _pad;)

  const u1* stable_top() const;

 public:
  JfrBuffer();
  void initialize(size_t header_size, size_t size);
  void reinitialize();

  const u1* start() const {
    return ((const u1*)this) + _header_size;
  }

  u1* start() {
    return ((u1*)this) + _header_size;
  }

  const u1* end() const {
    return start() + size();
  }

  // If pos() methods are invoked by a thread that is not the owner,
  // then acquire semantics must be ensured at the call site.
  const u1* pos() const {
    return _pos;
  }

  u1* pos() {
    return _pos;
  }

  u1** pos_address() {
    return &_pos;
  }

  void set_pos(u1* new_pos) {
    assert(new_pos <= end(), "invariant");
    AtomicAccess::release_store(&_pos, new_pos);
  }

  void set_pos(size_t size) {
    set_pos(pos() + size);
  }

  const u1* top() const;
  void set_top(const u1* new_top);

  // mutual exclusion
  const u1* acquire_critical_section_top() const;
  void release_critical_section_top(const u1* new_top);

  size_t size() const {
    return _size;
  }

  size_t total_size() const {
    return _header_size + size();
  }

  size_t free_size() const {
    return end() - AtomicAccess::load_acquire(&_pos);
  }

  size_t unflushed_size() const;

  bool empty() const {
    return AtomicAccess::load_acquire(&_pos) == start();
  }

  const void* identity() const {
    return AtomicAccess::load_acquire(&_identity);
  }

  // use only if implied owner already
  void set_identity(const void* id);

  void acquire(const void* id);
  bool try_acquire(const void* id);
  bool acquired_by(const void* id) const;
  bool acquired_by_self() const;
  void release();

  size_t discard();
  void move(JfrBuffer* const to, size_t size);

  bool transient() const;
  void set_transient();
  void clear_transient();

  bool lease() const;
  void set_lease();
  void clear_lease();

  bool retired() const;
  void set_retired();
  void clear_retired();

  u1 context() const;
  void set_context(u1 context);
  void clear_context();

  // Code generation
  static ByteSize pos_offset();
  static ByteSize flags_offset();

};

#endif // SHARE_JFR_RECORDER_STORAGE_JFRBUFFER_HPP
