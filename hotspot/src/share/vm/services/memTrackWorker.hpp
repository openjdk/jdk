/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_SERVICES_MEM_TRACK_WORKER_HPP
#define SHARE_VM_SERVICES_MEM_TRACK_WORKER_HPP

#include "memory/allocation.hpp"
#include "runtime/thread.hpp"
#include "services/memRecorder.hpp"

// Maximum MAX_GENERATIONS generation data can be tracked.
#define MAX_GENERATIONS  512


class MemTrackWorker : public NamedThread {
 private:
  // circular buffer. This buffer contains recorders to be merged into global
  // snaphsot.
  // Each slot holds a linked list of memory recorders, that contains one
  // generation of memory data.
  MemRecorder*  _gen[MAX_GENERATIONS];
  int           _head, _tail; // head and tail pointers to above circular buffer

  bool          _has_error;

 public:
  MemTrackWorker();
  ~MemTrackWorker();
  _NOINLINE_ void* operator new(size_t size);
  _NOINLINE_ void* operator new(size_t size, const std::nothrow_t& nothrow_constant);

  void start();
  void run();

  inline bool has_error() const { return _has_error; }

  // task at synchronization point
  void at_sync_point(MemRecorder* pending_recorders);

  // for debugging purpose, they are not thread safe.
  NOT_PRODUCT(static int count_recorder(const MemRecorder* head);)
  NOT_PRODUCT(int count_pending_recorders() const;)

  NOT_PRODUCT(int _sync_point_count;)
  NOT_PRODUCT(int _merge_count;)
  NOT_PRODUCT(int _last_gen_in_use;)

  inline int generations_in_use() const {
    return (_tail >= _head ? (_tail - _head + 1) : (MAX_GENERATIONS - (_head - _tail) + 1));
  }
};

#endif // SHARE_VM_SERVICES_MEM_TRACK_WORKER_HPP
