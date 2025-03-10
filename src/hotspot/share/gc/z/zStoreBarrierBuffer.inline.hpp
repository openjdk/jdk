/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
 */

#ifndef SHARE_GC_Z_ZSTOREBARRIERBUFFER_INLINE_HPP
#define SHARE_GC_Z_ZSTOREBARRIERBUFFER_INLINE_HPP

#include "gc/z/zStoreBarrierBuffer.hpp"

#include "gc/shared/gc_globals.hpp"
#include "gc/z/zThreadLocalData.hpp"
#include "runtime/thread.hpp"

inline size_t ZStoreBarrierBuffer::current() const {
  return _current / sizeof(ZStoreBarrierEntry);
}

inline void ZStoreBarrierBuffer::add(volatile zpointer* p, zpointer prev) {
  assert(ZBufferStoreBarriers, "Only buffer stores when it is enabled");
  if (_current == 0) {
    flush();
  }
  _current -= sizeof(ZStoreBarrierEntry);
  _buffer[current()] = {p, prev};
}

inline ZStoreBarrierBuffer* ZStoreBarrierBuffer::buffer_for_store(bool heal) {
  if (heal) {
    return nullptr;
  }

  Thread* const thread = Thread::current();
  if (!thread->is_Java_thread()) {
    return nullptr;
  }

  ZStoreBarrierBuffer* const buffer = ZThreadLocalData::store_barrier_buffer(JavaThread::cast(thread));
  return ZBufferStoreBarriers ? buffer : nullptr;
}

#endif // SHARE_GC_Z_ZSTOREBARRIERBUFFER_INLINE_HPP
