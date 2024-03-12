/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/javaClasses.inline.hpp"
#include "jfr/jfrEvents.hpp"
#include "jfr/support/jfrThreadId.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "oops/oopsHierarchy.hpp"
#include "oops/stackChunkOop.inline.hpp"
#include "runtime/continuation.hpp"
#include "runtime/continuationEntry.inline.hpp"
#include "runtime/continuationHelper.inline.hpp"
#include "runtime/continuationWrapper.inline.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/stackChunkFrameStream.inline.hpp"

ContinuationWrapper::ContinuationWrapper(const RegisterMap* map)
  : ContinuationWrapper(map->thread(),
                        Continuation::get_continuation_entry_for_continuation(map->thread(), map->stack_chunk()->cont()),
                        map->stack_chunk()->cont()) {
  assert(_entry == nullptr || _continuation == _entry->cont_oop(map->thread()),
    "cont: " INTPTR_FORMAT " entry: " INTPTR_FORMAT " entry_sp: " INTPTR_FORMAT,
    p2i( (oopDesc*)_continuation), p2i((oopDesc*)_entry->cont_oop(map->thread())), p2i(entrySP()));
}

const frame ContinuationWrapper::last_frame() {
  stackChunkOop chunk = last_nonempty_chunk();
  if (chunk == nullptr) {
    return frame();
  }
  return StackChunkFrameStream<ChunkFrames::Mixed>(chunk).to_frame();
}

stackChunkOop ContinuationWrapper::find_chunk_by_address(void* p) const {
  for (stackChunkOop chunk = tail(); chunk != nullptr; chunk = chunk->parent()) {
    if (chunk->is_in_chunk(p)) {
      assert(chunk->is_usable_in_chunk(p), "");
      return chunk;
    }
  }
  return nullptr;
}

#ifndef PRODUCT
intptr_t ContinuationWrapper::hash() {
  return Thread::current()->is_Java_thread() ? _continuation->identity_hash() : -1;
}
#endif

#ifdef ASSERT
bool ContinuationWrapper::is_entry_frame(const frame& f) {
  return f.sp() == entrySP();
}

bool ContinuationWrapper::chunk_invariant() const {
  // only the topmost chunk can be empty
  if (_tail == nullptr) {
    return true;
  }

  int i = 1;
  for (stackChunkOop chunk = _tail->parent(); chunk != nullptr; chunk = chunk->parent()) {
    if (chunk->is_empty()) {
      assert(chunk != _tail, "");
      return false;
    }
    i++;
  }
  return true;
}
#endif // ASSERT
