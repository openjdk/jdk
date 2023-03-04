/*
 * Copyright (c) 2023 SAP SE. All rights reserved.
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, Red Hat, Inc. and/or its affiliates.
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

#ifndef SHARE_MEMORY_METASPACE_CHUNKHEADERPOOL_HPP
#define SHARE_MEMORY_METASPACE_CHUNKHEADERPOOL_HPP

#include "memory/allocation.hpp"
#include "memory/metaspace/counters.hpp"
#include "memory/metaspace/metachunk.hpp"
#include "memory/metaspace/metachunkList.hpp"
#include "utilities/debug.hpp"
#include "utilities/fixedItemArray.hpp"
#include "utilities/globalDefinitions.hpp"

namespace metaspace {

// Chunk headers (Metachunk objects) are separate entities from their payload.
//  Since they are allocated and released frequently in the course of buddy allocation
//  (splitting, merging chunks happens often) we want allocation of them fast. Therefore
//  we keep them in a simple pool.

class ChunkHeaderPool : public CHeapObj<mtMetaspace> {
  FixedItemArray<Metachunk, 128, 0, CHeapAllocator> _pool;
  static ChunkHeaderPool* _chunkHeaderPool;
public:

  // Allocates a Metachunk structure. The structure is uninitialized.
  Metachunk* allocate_chunk_header() {
    DEBUG_ONLY(verify());
    Metachunk* c = _pool.allocate();
    // By contract, the returned structure is uninitialized.
    // Zap to make this clear.
    DEBUG_ONLY(c->zap_header(0xBB);)
    return c;
  }

  void return_chunk_header(Metachunk* c) {
    // We only ever should return free chunks, since returning chunks
    // happens only on merging and merging only works with free chunks.
    assert(c != nullptr && c->is_free(), "Sanity");
    // In debug, fill dead header with pattern.
    DEBUG_ONLY(c->zap_header(0xCC);)
    c->set_dead();
    _pool.deallocate(c);
  }

  int used() const                      { return _pool.num_allocated(); }
  int freelist_size() const             { return _pool.num_free(); }
  size_t memory_footprint_words() const { return _pool.footprint(); }

  DEBUG_ONLY(void verify() const { _pool.verify(); })

  static void initialize();

  // Returns reference to the one global chunk header pool.
  static ChunkHeaderPool* pool() { return _chunkHeaderPool; }
};

} // namespace metaspace

#endif // SHARE_MEMORY_METASPACE_CHUNKHEADERPOOL_HPP
