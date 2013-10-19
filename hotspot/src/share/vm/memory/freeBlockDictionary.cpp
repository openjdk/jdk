/*
 * Copyright (c) 2002, 2012, Oracle and/or its affiliates. All rights reserved.
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
#include "utilities/macros.hpp"
#if INCLUDE_ALL_GCS
#include "gc_implementation/concurrentMarkSweep/freeChunk.hpp"
#endif // INCLUDE_ALL_GCS
#include "memory/freeBlockDictionary.hpp"
#include "memory/metachunk.hpp"
#include "runtime/thread.inline.hpp"
#include "utilities/macros.hpp"

#ifndef PRODUCT
template <class Chunk> Mutex* FreeBlockDictionary<Chunk>::par_lock() const {
  return _lock;
}

template <class Chunk> void FreeBlockDictionary<Chunk>::set_par_lock(Mutex* lock) {
  _lock = lock;
}

template <class Chunk> void FreeBlockDictionary<Chunk>::verify_par_locked() const {
#ifdef ASSERT
  if (ParallelGCThreads > 0) {
    Thread* my_thread = Thread::current();
    if (my_thread->is_GC_task_thread()) {
      assert(par_lock() != NULL, "Should be using locking?");
      assert_lock_strong(par_lock());
    }
  }
#endif // ASSERT
}
#endif

template class FreeBlockDictionary<Metablock>;
template class FreeBlockDictionary<Metachunk>;

#if INCLUDE_ALL_GCS
// Explicitly instantiate for FreeChunk
template class FreeBlockDictionary<FreeChunk>;
#endif // INCLUDE_ALL_GCS
