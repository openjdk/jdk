/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2018 SAP SE. All rights reserved.
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
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "memory/metaspace/metachunk.hpp"
#include "memory/metaspace/occupancyMap.hpp"
#include "runtime/os.hpp"

namespace metaspace {

OccupancyMap::OccupancyMap(const MetaWord* reference_address, size_t word_size, size_t smallest_chunk_word_size) :
            _reference_address(reference_address), _word_size(word_size),
            _smallest_chunk_word_size(smallest_chunk_word_size)
{
  assert(reference_address != NULL, "invalid reference address");
  assert(is_aligned(reference_address, smallest_chunk_word_size),
      "Reference address not aligned to smallest chunk size.");
  assert(is_aligned(word_size, smallest_chunk_word_size),
      "Word_size shall be a multiple of the smallest chunk size.");
  // Calculate bitmap size: one bit per smallest_chunk_word_size'd area.
  size_t num_bits = word_size / smallest_chunk_word_size;
  _map_size = (num_bits + 7) / 8;
  assert(_map_size * 8 >= num_bits, "sanity");
  _map[0] = (uint8_t*) os::malloc(_map_size, mtInternal);
  _map[1] = (uint8_t*) os::malloc(_map_size, mtInternal);
  assert(_map[0] != NULL && _map[1] != NULL, "Occupancy Map: allocation failed.");
  memset(_map[1], 0, _map_size);
  memset(_map[0], 0, _map_size);
  // Sanity test: the first respectively last possible chunk start address in
  // the covered range shall map to the first and last bit in the bitmap.
  assert(get_bitpos_for_address(reference_address) == 0,
      "First chunk address in range must map to fist bit in bitmap.");
  assert(get_bitpos_for_address(reference_address + word_size - smallest_chunk_word_size) == num_bits - 1,
      "Last chunk address in range must map to last bit in bitmap.");
}

OccupancyMap::~OccupancyMap() {
  os::free(_map[0]);
  os::free(_map[1]);
}

#ifdef ASSERT
// Verify occupancy map for the address range [from, to).
// We need to tell it the address range, because the memory the
// occupancy map is covering may not be fully comitted yet.
void OccupancyMap::verify(MetaWord* from, MetaWord* to) {
  Metachunk* chunk = NULL;
  int nth_bit_for_chunk = 0;
  MetaWord* chunk_end = NULL;
  for (MetaWord* p = from; p < to; p += _smallest_chunk_word_size) {
    const unsigned pos = get_bitpos_for_address(p);
    // Check the chunk-starts-info:
    if (get_bit_at_position(pos, layer_chunk_start_map)) {
      // Chunk start marked in bitmap.
      chunk = (Metachunk*) p;
      if (chunk_end != NULL) {
        assert(chunk_end == p, "Unexpected chunk start found at %p (expected "
            "the next chunk to start at %p).", p, chunk_end);
      }
      assert(chunk->is_valid_sentinel(), "Invalid chunk at address %p.", p);
      if (chunk->get_chunk_type() != HumongousIndex) {
        guarantee(is_aligned(p, chunk->word_size()), "Chunk %p not aligned.", p);
      }
      chunk_end = p + chunk->word_size();
      nth_bit_for_chunk = 0;
      assert(chunk_end <= to, "Chunk end overlaps test address range.");
    } else {
      // No chunk start marked in bitmap.
      assert(chunk != NULL, "Chunk should start at start of address range.");
      assert(p < chunk_end, "Did not find expected chunk start at %p.", p);
      nth_bit_for_chunk ++;
    }
    // Check the in-use-info:
    const bool in_use_bit = get_bit_at_position(pos, layer_in_use_map);
    if (in_use_bit) {
      assert(!chunk->is_tagged_free(), "Chunk %p: marked in-use in map but is free (bit %u).",
          chunk, nth_bit_for_chunk);
    } else {
      assert(chunk->is_tagged_free(), "Chunk %p: marked free in map but is in use (bit %u).",
          chunk, nth_bit_for_chunk);
    }
  }
}

// Verify that a given chunk is correctly accounted for in the bitmap.
void OccupancyMap::verify_for_chunk(Metachunk* chunk) {
  assert(chunk_starts_at_address((MetaWord*) chunk),
      "No chunk start marked in map for chunk %p.", chunk);
  // For chunks larger than the minimal chunk size, no other chunk
  // must start in its area.
  if (chunk->word_size() > _smallest_chunk_word_size) {
    assert(!is_any_bit_set_in_region(((MetaWord*) chunk) + _smallest_chunk_word_size,
        chunk->word_size() - _smallest_chunk_word_size, layer_chunk_start_map),
        "No chunk must start within another chunk.");
  }
  if (!chunk->is_tagged_free()) {
    assert(is_region_in_use((MetaWord*)chunk, chunk->word_size()),
        "Chunk %p is in use but marked as free in map (%d %d).",
        chunk, chunk->get_chunk_type(), chunk->get_origin());
  } else {
    assert(!is_region_in_use((MetaWord*)chunk, chunk->word_size()),
        "Chunk %p is free but marked as in-use in map (%d %d).",
        chunk, chunk->get_chunk_type(), chunk->get_origin());
  }
}

#endif // ASSERT

} // namespace metaspace


