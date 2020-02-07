/*
 * Copyright (c) 2017, 2020, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHSTRDEDUPQUEUE_INLINE_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHSTRDEDUPQUEUE_INLINE_HPP

#include "gc/shenandoah/shenandoahStrDedupQueue.hpp"
#include "oops/access.hpp"
#include "runtime/atomic.hpp"

// With concurrent string dedup cleaning up, GC worker threads
// may see oops just enqueued, so release_store and load_acquire
// relationship needs to be established between enqueuing threads
// and GC workers.
// For example, when GC sees a slot (index), there must be a valid
// (dead or live) oop.
// Note: There is no concern if GC misses newly enqueued oops,
// since LRB ensures they are in to-space.
template <uint buffer_size>
ShenandoahOopBuffer<buffer_size>::ShenandoahOopBuffer() :
  _index(0), _next(NULL) {
}

template <uint buffer_size>
bool ShenandoahOopBuffer<buffer_size>::is_full() const {
  return index_acquire() >= buffer_size;
}

template <uint buffer_size>
bool ShenandoahOopBuffer<buffer_size>::is_empty() const {
  return index_acquire() == 0;
}

template <uint buffer_size>
uint ShenandoahOopBuffer<buffer_size>::size() const {
  return index_acquire();
}

template <uint buffer_size>
void ShenandoahOopBuffer<buffer_size>::push(oop obj) {
  assert(!is_full(),  "Buffer is full");
  uint idx = index_acquire();
  RawAccess<IS_NOT_NULL>::oop_store(&_buf[idx], obj);
  set_index_release(idx + 1);
}

template <uint buffer_size>
oop ShenandoahOopBuffer<buffer_size>::pop() {
  assert(!is_empty(), "Buffer is empty");
  uint idx = index_acquire() - 1;
  oop value = NativeAccess<ON_PHANTOM_OOP_REF | AS_NO_KEEPALIVE | MO_ACQUIRE>::oop_load(&_buf[idx]);
  set_index_release(idx);
  return value;
}

template <uint buffer_size>
void ShenandoahOopBuffer<buffer_size>::set_next(ShenandoahOopBuffer<buffer_size>* next) {
  _next = next;
}

template <uint buffer_size>
ShenandoahOopBuffer<buffer_size>* ShenandoahOopBuffer<buffer_size>::next() const {
  return _next;
}

template <uint buffer_size>
void ShenandoahOopBuffer<buffer_size>::reset() {
  _index = 0;
  _next = NULL;
}

template <uint buffer_size>
uint ShenandoahOopBuffer<buffer_size>::index_acquire() const {
  return Atomic::load_acquire(&_index);
}

template <uint buffer_size>
void ShenandoahOopBuffer<buffer_size>::set_index_release(uint index) {
  return Atomic::release_store(&_index, index);
}

template <uint buffer_size>
void ShenandoahOopBuffer<buffer_size>::unlink_or_oops_do(StringDedupUnlinkOrOopsDoClosure* cl) {
  uint len = size();
  for (uint index = 0; index < len; index ++) {
    oop* obj_addr = &_buf[index];
    if (*obj_addr != NULL) {
      if (cl->is_alive(*obj_addr)) {
        cl->keep_alive(obj_addr);
      } else {
        RawAccess<MO_RELEASE>::oop_store(&_buf[index], oop());
      }
    }
  }
}

template <uint buffer_size>
void ShenandoahOopBuffer<buffer_size>::oops_do(OopClosure* cl) {
  uint len = size();
  for (uint index = 0; index < len; index ++) {
    cl->do_oop(&_buf[index]);
  }
}

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHSTRDEDUPQUEUE_INLINE_HPP
