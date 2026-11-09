/*
 * Copyright (c) 2016, 2019, Red Hat, Inc. All rights reserved.
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHTASKQUEUE_INLINE_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHTASKQUEUE_INLINE_HPP

#include "gc/shenandoah/shenandoahTaskqueue.hpp"

#include "gc/shared/taskqueue.inline.hpp"
#include "utilities/stack.inline.hpp"

template <class E, MemTag MT, unsigned int N>
bool BufferedOverflowTaskQueue<E, MT, N>::pop(E &t) {
  if (!_buf_empty) {
    t = _elem;
    _buf_empty = true;
    return true;
  }

  if (taskqueue_t::pop_local(t)) {
    return true;
  }

  if (taskqueue_t::pop_overflow(t)) {
    pop_more_overflow();
    return true;
  }

  return false;
}

template <class E, MemTag MT, unsigned int N>
void BufferedOverflowTaskQueue<E, MT, N>::pop_more_overflow() {
  // Local queue is empty and we have overflow. Overflow queue is invisible
  // for work stealing, so we want to transfer as much as practically possible
  // from it. Pulling too little hinders work balancing. Pulling too much
  // incurs stalls (important e.g. when we need to respond to yield/cancellation).
  // Local queues must also have some space left for local pushes.
  constexpr uint fill = MIN2<uint>(16*K, N/2);

  E tmp;
  assert(taskqueue_t::size() == 0, "Local queue is empty");
  for (uint i = 0; (i < fill) && taskqueue_t::pop_overflow(tmp); i++) {
    bool pushed = taskqueue_t::try_push_to_taskqueue(tmp);
    assert(pushed, "Should always succeed pushing");
  }
}

template <class E, MemTag MT, unsigned int N>
bool BufferedOverflowTaskQueue<E, MT, N>::push(E t) {
  if (_buf_empty) {
    _elem = t;
    _buf_empty = false;
  } else {
    bool pushed = taskqueue_t::push(_elem);
    assert(pushed, "overflow queue should always succeed pushing");
    _elem = t;
  }
  return true;
}

template <class E, MemTag MT, unsigned int N>
void BufferedOverflowTaskQueue<E, MT, N>::clear() {
    _buf_empty = true;
    taskqueue_t::set_empty();
    taskqueue_t::overflow_stack()->clear();
}

template <class E, MemTag MT, unsigned int N>
inline size_t BufferedOverflowTaskQueue<E, MT, N>::full_size() {
  return taskqueue_t::size() + taskqueue_t::overflow_stack()->size() + (_buf_empty ? 0 : 1);
}

template <class E, MemTag MT, unsigned int N>
inline size_t BufferedOverflowTaskQueue<E, MT, N>::capacity() const {
  return N;
}

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHTASKQUEUE_INLINE_HPP
