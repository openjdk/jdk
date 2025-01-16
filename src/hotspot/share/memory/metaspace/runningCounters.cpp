/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020 SAP SE. All rights reserved.
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
#include "memory/metaspace/chunkManager.hpp"
#include "memory/metaspace/metaspaceContext.hpp"
#include "memory/metaspace/runningCounters.hpp"
#include "memory/metaspace/virtualSpaceList.hpp"

namespace metaspace {

// Return reserved size, in words, for Metaspace
size_t RunningCounters::reserved_words() {
  return reserved_words_class() + reserved_words_nonclass();
}

size_t RunningCounters::reserved_words_class() {
  VirtualSpaceList* vs = VirtualSpaceList::vslist_class();
  return vs != nullptr ? vs->reserved_words() : 0;
}

size_t RunningCounters::reserved_words_nonclass() {
  assert(VirtualSpaceList::vslist_nonclass() != nullptr, "Metaspace not yet initialized");
  return VirtualSpaceList::vslist_nonclass()->reserved_words();
}

// Return total committed size, in words, for Metaspace
size_t RunningCounters::committed_words() {
  return committed_words_class() + committed_words_nonclass();
}

size_t RunningCounters::committed_words_class() {
  VirtualSpaceList* vs = VirtualSpaceList::vslist_class();
  return vs != nullptr ? vs->committed_words() : 0;
}

size_t RunningCounters::committed_words_nonclass() {
  assert(VirtualSpaceList::vslist_nonclass() != nullptr, "Metaspace not yet initialized");
  return VirtualSpaceList::vslist_nonclass()->committed_words();
}

// ---- used chunks -----

// Returns size, in words, used for metadata.
size_t RunningCounters::used_words() {
  return used_words_class() + used_words_nonclass();
}

size_t RunningCounters::used_words_class() {
  const MetaspaceContext* context = MetaspaceContext::context_class();
  return context != nullptr ? context->used_words() : 0;
}

size_t RunningCounters::used_words_nonclass() {
  return MetaspaceContext::context_nonclass()->used_words();
}

// ---- free chunks -----

// Returns size, in words, of all chunks in all freelists.
size_t RunningCounters::free_chunks_words() {
  return free_chunks_words_class() + free_chunks_words_nonclass();
}

size_t RunningCounters::free_chunks_words_class() {
  ChunkManager* cm = ChunkManager::chunkmanager_class();
  return cm != nullptr ? cm->total_word_size() : 0;
}

size_t RunningCounters::free_chunks_words_nonclass() {
  assert(ChunkManager::chunkmanager_nonclass() != nullptr, "Metaspace not yet initialized");
  return ChunkManager::chunkmanager_nonclass()->total_word_size();
}

} // namespace metaspace

