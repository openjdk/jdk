/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/metaspace/metachunk.hpp"
#include "memory/metaspace.hpp"
#include "memory/metaspace/chunkManager.hpp"
#include "memory/metaspace/metaspaceCommon.hpp"
#include "memory/metaspace/occupancyMap.hpp"
#include "memory/metaspace/virtualSpaceNode.hpp"
#include "memory/virtualspace.hpp"
#include "runtime/os.hpp"
#include "services/memTracker.hpp"
#include "utilities/copy.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

namespace metaspace {

// Decide if large pages should be committed when the memory is reserved.
static bool should_commit_large_pages_when_reserving(size_t bytes) {
  if (UseLargePages && UseLargePagesInMetaspace && !os::can_commit_large_page_memory()) {
    size_t words = bytes / BytesPerWord;
    bool is_class = false; // We never reserve large pages for the class space.
    if (MetaspaceGC::can_expand(words, is_class) &&
        MetaspaceGC::allowed_expansion() >= words) {
      return true;
    }
  }

  return false;
}

// byte_size is the size of the associated virtualspace.
VirtualSpaceNode::VirtualSpaceNode(bool is_class, size_t bytes) :
    _is_class(is_class), _top(NULL), _next(NULL), _rs(), _container_count(0), _occupancy_map(NULL) {
  assert_is_aligned(bytes, Metaspace::reserve_alignment());
  bool large_pages = should_commit_large_pages_when_reserving(bytes);
  _rs = ReservedSpace(bytes, Metaspace::reserve_alignment(), large_pages);

  if (_rs.is_reserved()) {
    assert(_rs.base() != NULL, "Catch if we get a NULL address");
    assert(_rs.size() != 0, "Catch if we get a 0 size");
    assert_is_aligned(_rs.base(), Metaspace::reserve_alignment());
    assert_is_aligned(_rs.size(), Metaspace::reserve_alignment());

    MemTracker::record_virtual_memory_type((address)_rs.base(), mtClass);
  }
}

void VirtualSpaceNode::purge(ChunkManager* chunk_manager) {
  DEBUG_ONLY(this->verify();)
    Metachunk* chunk = first_chunk();
  Metachunk* invalid_chunk = (Metachunk*) top();
  while (chunk < invalid_chunk ) {
    assert(chunk->is_tagged_free(), "Should be tagged free");
    MetaWord* next = ((MetaWord*)chunk) + chunk->word_size();
    chunk_manager->remove_chunk(chunk);
    chunk->remove_sentinel();
    assert(chunk->next() == NULL &&
        chunk->prev() == NULL,
        "Was not removed from its list");
    chunk = (Metachunk*) next;
  }
}

void VirtualSpaceNode::print_map(outputStream* st, bool is_class) const {

  if (bottom() == top()) {
    return;
  }

  const size_t spec_chunk_size = is_class ? ClassSpecializedChunk : SpecializedChunk;
  const size_t small_chunk_size = is_class ? ClassSmallChunk : SmallChunk;
  const size_t med_chunk_size = is_class ? ClassMediumChunk : MediumChunk;

  int line_len = 100;
  const size_t section_len = align_up(spec_chunk_size * line_len, med_chunk_size);
  line_len = (int)(section_len / spec_chunk_size);

  static const int NUM_LINES = 4;

  char* lines[NUM_LINES];
  for (int i = 0; i < NUM_LINES; i ++) {
    lines[i] = (char*)os::malloc(line_len, mtInternal);
  }
  int pos = 0;
  const MetaWord* p = bottom();
  const Metachunk* chunk = (const Metachunk*)p;
  const MetaWord* chunk_end = p + chunk->word_size();
  while (p < top()) {
    if (pos == line_len) {
      pos = 0;
      for (int i = 0; i < NUM_LINES; i ++) {
        st->fill_to(22);
        st->print_raw(lines[i], line_len);
        st->cr();
      }
    }
    if (pos == 0) {
      st->print(PTR_FORMAT ":", p2i(p));
    }
    if (p == chunk_end) {
      chunk = (Metachunk*)p;
      chunk_end = p + chunk->word_size();
    }
    // line 1: chunk starting points (a dot if that area is a chunk start).
    lines[0][pos] = p == (const MetaWord*)chunk ? '.' : ' ';

    // Line 2: chunk type (x=spec, s=small, m=medium, h=humongous), uppercase if
    // chunk is in use.
    const bool chunk_is_free = ((Metachunk*)chunk)->is_tagged_free();
    if (chunk->word_size() == spec_chunk_size) {
      lines[1][pos] = chunk_is_free ? 'x' : 'X';
    } else if (chunk->word_size() == small_chunk_size) {
      lines[1][pos] = chunk_is_free ? 's' : 'S';
    } else if (chunk->word_size() == med_chunk_size) {
      lines[1][pos] = chunk_is_free ? 'm' : 'M';
    } else if (chunk->word_size() > med_chunk_size) {
      lines[1][pos] = chunk_is_free ? 'h' : 'H';
    } else {
      ShouldNotReachHere();
    }

    // Line 3: chunk origin
    const ChunkOrigin origin = chunk->get_origin();
    lines[2][pos] = origin == origin_normal ? ' ' : '0' + (int) origin;

    // Line 4: Virgin chunk? Virgin chunks are chunks created as a byproduct of padding or splitting,
    //         but were never used.
    lines[3][pos] = chunk->get_use_count() > 0 ? ' ' : 'v';

    p += spec_chunk_size;
    pos ++;
  }
  if (pos > 0) {
    for (int i = 0; i < NUM_LINES; i ++) {
      st->fill_to(22);
      st->print_raw(lines[i], line_len);
      st->cr();
    }
  }
  for (int i = 0; i < NUM_LINES; i ++) {
    os::free(lines[i]);
  }
}


#ifdef ASSERT
uintx VirtualSpaceNode::container_count_slow() {
  uintx count = 0;
  Metachunk* chunk = first_chunk();
  Metachunk* invalid_chunk = (Metachunk*) top();
  while (chunk < invalid_chunk ) {
    MetaWord* next = ((MetaWord*)chunk) + chunk->word_size();
    do_verify_chunk(chunk);
    // Don't count the chunks on the free lists.  Those are
    // still part of the VirtualSpaceNode but not currently
    // counted.
    if (!chunk->is_tagged_free()) {
      count++;
    }
    chunk = (Metachunk*) next;
  }
  return count;
}
#endif

#ifdef ASSERT
// Verify counters, all chunks in this list node and the occupancy map.
void VirtualSpaceNode::verify() {
  uintx num_in_use_chunks = 0;
  Metachunk* chunk = first_chunk();
  Metachunk* invalid_chunk = (Metachunk*) top();

  // Iterate the chunks in this node and verify each chunk.
  while (chunk < invalid_chunk ) {
    DEBUG_ONLY(do_verify_chunk(chunk);)
      if (!chunk->is_tagged_free()) {
        num_in_use_chunks ++;
      }
    MetaWord* next = ((MetaWord*)chunk) + chunk->word_size();
    chunk = (Metachunk*) next;
  }
  assert(_container_count == num_in_use_chunks, "Container count mismatch (real: " UINTX_FORMAT
      ", counter: " UINTX_FORMAT ".", num_in_use_chunks, _container_count);
  // Also verify the occupancy map.
  occupancy_map()->verify(this->bottom(), this->top());
}
#endif // ASSERT

#ifdef ASSERT
// Verify that all free chunks in this node are ideally merged
// (there not should be multiple small chunks where a large chunk could exist.)
void VirtualSpaceNode::verify_free_chunks_are_ideally_merged() {
  Metachunk* chunk = first_chunk();
  Metachunk* invalid_chunk = (Metachunk*) top();
  // Shorthands.
  const size_t size_med = (is_class() ? ClassMediumChunk : MediumChunk) * BytesPerWord;
  const size_t size_small = (is_class() ? ClassSmallChunk : SmallChunk) * BytesPerWord;
  int num_free_chunks_since_last_med_boundary = -1;
  int num_free_chunks_since_last_small_boundary = -1;
  while (chunk < invalid_chunk ) {
    // Test for missed chunk merge opportunities: count number of free chunks since last chunk boundary.
    // Reset the counter when encountering a non-free chunk.
    if (chunk->get_chunk_type() != HumongousIndex) {
      if (chunk->is_tagged_free()) {
        // Count successive free, non-humongous chunks.
        if (is_aligned(chunk, size_small)) {
          assert(num_free_chunks_since_last_small_boundary <= 1,
              "Missed chunk merge opportunity at " PTR_FORMAT " for chunk size " SIZE_FORMAT_HEX ".", p2i(chunk) - size_small, size_small);
          num_free_chunks_since_last_small_boundary = 0;
        } else if (num_free_chunks_since_last_small_boundary != -1) {
          num_free_chunks_since_last_small_boundary ++;
        }
        if (is_aligned(chunk, size_med)) {
          assert(num_free_chunks_since_last_med_boundary <= 1,
              "Missed chunk merge opportunity at " PTR_FORMAT " for chunk size " SIZE_FORMAT_HEX ".", p2i(chunk) - size_med, size_med);
          num_free_chunks_since_last_med_boundary = 0;
        } else if (num_free_chunks_since_last_med_boundary != -1) {
          num_free_chunks_since_last_med_boundary ++;
        }
      } else {
        // Encountering a non-free chunk, reset counters.
        num_free_chunks_since_last_med_boundary = -1;
        num_free_chunks_since_last_small_boundary = -1;
      }
    } else {
      // One cannot merge areas with a humongous chunk in the middle. Reset counters.
      num_free_chunks_since_last_med_boundary = -1;
      num_free_chunks_since_last_small_boundary = -1;
    }

    MetaWord* next = ((MetaWord*)chunk) + chunk->word_size();
    chunk = (Metachunk*) next;
  }
}
#endif // ASSERT

void VirtualSpaceNode::inc_container_count() {
  assert_lock_strong(MetaspaceExpand_lock);
  _container_count++;
}

void VirtualSpaceNode::dec_container_count() {
  assert_lock_strong(MetaspaceExpand_lock);
  _container_count--;
}

#ifdef ASSERT
void VirtualSpaceNode::verify_container_count() {
  assert(_container_count == container_count_slow(),
      "Inconsistency in container_count _container_count " UINTX_FORMAT
      " container_count_slow() " UINTX_FORMAT, _container_count, container_count_slow());
}
#endif

VirtualSpaceNode::~VirtualSpaceNode() {
  _rs.release();
  if (_occupancy_map != NULL) {
    delete _occupancy_map;
  }
#ifdef ASSERT
  size_t word_size = sizeof(*this) / BytesPerWord;
  Copy::fill_to_words((HeapWord*) this, word_size, 0xf1f1f1f1);
#endif
}

size_t VirtualSpaceNode::used_words_in_vs() const {
  return pointer_delta(top(), bottom(), sizeof(MetaWord));
}

// Space committed in the VirtualSpace
size_t VirtualSpaceNode::capacity_words_in_vs() const {
  return pointer_delta(end(), bottom(), sizeof(MetaWord));
}

size_t VirtualSpaceNode::free_words_in_vs() const {
  return pointer_delta(end(), top(), sizeof(MetaWord));
}

// Given an address larger than top(), allocate padding chunks until top is at the given address.
void VirtualSpaceNode::allocate_padding_chunks_until_top_is_at(MetaWord* target_top) {

  assert(target_top > top(), "Sanity");

  // Padding chunks are added to the freelist.
  ChunkManager* const chunk_manager = Metaspace::get_chunk_manager(this->is_class());

  // shorthands
  const size_t spec_word_size = chunk_manager->specialized_chunk_word_size();
  const size_t small_word_size = chunk_manager->small_chunk_word_size();
  const size_t med_word_size = chunk_manager->medium_chunk_word_size();

  while (top() < target_top) {

    // We could make this coding more generic, but right now we only deal with two possible chunk sizes
    // for padding chunks, so it is not worth it.
    size_t padding_chunk_word_size = small_word_size;
    if (is_aligned(top(), small_word_size * sizeof(MetaWord)) == false) {
      assert_is_aligned(top(), spec_word_size * sizeof(MetaWord)); // Should always hold true.
      padding_chunk_word_size = spec_word_size;
    }
    MetaWord* here = top();
    assert_is_aligned(here, padding_chunk_word_size * sizeof(MetaWord));
    inc_top(padding_chunk_word_size);

    // Create new padding chunk.
    ChunkIndex padding_chunk_type = get_chunk_type_by_size(padding_chunk_word_size, is_class());
    assert(padding_chunk_type == SpecializedIndex || padding_chunk_type == SmallIndex, "sanity");

    Metachunk* const padding_chunk =
        ::new (here) Metachunk(padding_chunk_type, is_class(), padding_chunk_word_size, this);
    assert(padding_chunk == (Metachunk*)here, "Sanity");
    DEBUG_ONLY(padding_chunk->set_origin(origin_pad);)
    log_trace(gc, metaspace, freelist)("Created padding chunk in %s at "
        PTR_FORMAT ", size " SIZE_FORMAT_HEX ".",
        (is_class() ? "class space " : "metaspace"),
        p2i(padding_chunk), padding_chunk->word_size() * sizeof(MetaWord));

    // Mark chunk start in occupancy map.
    occupancy_map()->set_chunk_starts_at_address((MetaWord*)padding_chunk, true);

    // Chunks are born as in-use (see MetaChunk ctor). So, before returning
    // the padding chunk to its chunk manager, mark it as in use (ChunkManager
    // will assert that).
    do_update_in_use_info_for_chunk(padding_chunk, true);

    // Return Chunk to freelist.
    inc_container_count();
    chunk_manager->return_single_chunk(padding_chunk);
    // Please note: at this point, ChunkManager::return_single_chunk()
    // may already have merged the padding chunk with neighboring chunks, so
    // it may have vanished at this point. Do not reference the padding
    // chunk beyond this point.
  }

  assert(top() == target_top, "Sanity");

} // allocate_padding_chunks_until_top_is_at()

// Allocates the chunk from the virtual space only.
// This interface is also used internally for debugging.  Not all
// chunks removed here are necessarily used for allocation.
Metachunk* VirtualSpaceNode::take_from_committed(size_t chunk_word_size) {
  // Non-humongous chunks are to be allocated aligned to their chunk
  // size. So, start addresses of medium chunks are aligned to medium
  // chunk size, those of small chunks to small chunk size and so
  // forth. This facilitates merging of free chunks and reduces
  // fragmentation. Chunk sizes are spec < small < medium, with each
  // larger chunk size being a multiple of the next smaller chunk
  // size.
  // Because of this alignment, me may need to create a number of padding
  // chunks. These chunks are created and added to the freelist.

  // The chunk manager to which we will give our padding chunks.
  ChunkManager* const chunk_manager = Metaspace::get_chunk_manager(this->is_class());

  // shorthands
  const size_t spec_word_size = chunk_manager->specialized_chunk_word_size();
  const size_t small_word_size = chunk_manager->small_chunk_word_size();
  const size_t med_word_size = chunk_manager->medium_chunk_word_size();

  assert(chunk_word_size == spec_word_size || chunk_word_size == small_word_size ||
      chunk_word_size >= med_word_size, "Invalid chunk size requested.");

  // Chunk alignment (in bytes) == chunk size unless humongous.
  // Humongous chunks are aligned to the smallest chunk size (spec).
  const size_t required_chunk_alignment = (chunk_word_size > med_word_size ?
      spec_word_size : chunk_word_size) * sizeof(MetaWord);

  // Do we have enough space to create the requested chunk plus
  // any padding chunks needed?
  MetaWord* const next_aligned =
      static_cast<MetaWord*>(align_up(top(), required_chunk_alignment));
  if (!is_available((next_aligned - top()) + chunk_word_size)) {
    return NULL;
  }

  // Before allocating the requested chunk, allocate padding chunks if necessary.
  // We only need to do this for small or medium chunks: specialized chunks are the
  // smallest size, hence always aligned. Homungous chunks are allocated unaligned
  // (implicitly, also aligned to smallest chunk size).
  if ((chunk_word_size == med_word_size || chunk_word_size == small_word_size) && next_aligned > top())  {
    log_trace(gc, metaspace, freelist)("Creating padding chunks in %s between %p and %p...",
        (is_class() ? "class space " : "metaspace"),
        top(), next_aligned);
    allocate_padding_chunks_until_top_is_at(next_aligned);
    // Now, top should be aligned correctly.
    assert_is_aligned(top(), required_chunk_alignment);
  }

  // Now, top should be aligned correctly.
  assert_is_aligned(top(), required_chunk_alignment);

  // Bottom of the new chunk
  MetaWord* chunk_limit = top();
  assert(chunk_limit != NULL, "Not safe to call this method");

  // The virtual spaces are always expanded by the
  // commit granularity to enforce the following condition.
  // Without this the is_available check will not work correctly.
  assert(_virtual_space.committed_size() == _virtual_space.actual_committed_size(),
      "The committed memory doesn't match the expanded memory.");

  if (!is_available(chunk_word_size)) {
    LogTarget(Trace, gc, metaspace, freelist) lt;
    if (lt.is_enabled()) {
      LogStream ls(lt);
      ls.print("VirtualSpaceNode::take_from_committed() not available " SIZE_FORMAT " words ", chunk_word_size);
      // Dump some information about the virtual space that is nearly full
      print_on(&ls);
    }
    return NULL;
  }

  // Take the space  (bump top on the current virtual space).
  inc_top(chunk_word_size);

  // Initialize the chunk
  ChunkIndex chunk_type = get_chunk_type_by_size(chunk_word_size, is_class());
  Metachunk* result = ::new (chunk_limit) Metachunk(chunk_type, is_class(), chunk_word_size, this);
  assert(result == (Metachunk*)chunk_limit, "Sanity");
  occupancy_map()->set_chunk_starts_at_address((MetaWord*)result, true);
  do_update_in_use_info_for_chunk(result, true);

  inc_container_count();

  if (VerifyMetaspace) {
    DEBUG_ONLY(chunk_manager->locked_verify());
    DEBUG_ONLY(this->verify());
  }

  DEBUG_ONLY(do_verify_chunk(result));

  result->inc_use_count();

  return result;
}


// Expand the virtual space (commit more of the reserved space)
bool VirtualSpaceNode::expand_by(size_t min_words, size_t preferred_words) {
  size_t min_bytes = min_words * BytesPerWord;
  size_t preferred_bytes = preferred_words * BytesPerWord;

  size_t uncommitted = virtual_space()->reserved_size() - virtual_space()->actual_committed_size();

  if (uncommitted < min_bytes) {
    return false;
  }

  size_t commit = MIN2(preferred_bytes, uncommitted);
  bool result = virtual_space()->expand_by(commit, false);

  if (result) {
    log_trace(gc, metaspace, freelist)("Expanded %s virtual space list node by " SIZE_FORMAT " words.",
        (is_class() ? "class" : "non-class"), commit);
    DEBUG_ONLY(Atomic::inc(&g_internal_statistics.num_committed_space_expanded));
  } else {
    log_trace(gc, metaspace, freelist)("Failed to expand %s virtual space list node by " SIZE_FORMAT " words.",
        (is_class() ? "class" : "non-class"), commit);
  }

  assert(result, "Failed to commit memory");

  return result;
}

Metachunk* VirtualSpaceNode::get_chunk_vs(size_t chunk_word_size) {
  assert_lock_strong(MetaspaceExpand_lock);
  Metachunk* result = take_from_committed(chunk_word_size);
  return result;
}

bool VirtualSpaceNode::initialize() {

  if (!_rs.is_reserved()) {
    return false;
  }

  // These are necessary restriction to make sure that the virtual space always
  // grows in steps of Metaspace::commit_alignment(). If both base and size are
  // aligned only the middle alignment of the VirtualSpace is used.
  assert_is_aligned(_rs.base(), Metaspace::commit_alignment());
  assert_is_aligned(_rs.size(), Metaspace::commit_alignment());

  // ReservedSpaces marked as special will have the entire memory
  // pre-committed. Setting a committed size will make sure that
  // committed_size and actual_committed_size agrees.
  size_t pre_committed_size = _rs.special() ? _rs.size() : 0;

  bool result = virtual_space()->initialize_with_granularity(_rs, pre_committed_size,
      Metaspace::commit_alignment());
  if (result) {
    assert(virtual_space()->committed_size() == virtual_space()->actual_committed_size(),
        "Checking that the pre-committed memory was registered by the VirtualSpace");

    set_top((MetaWord*)virtual_space()->low());
  }

  // Initialize Occupancy Map.
  const size_t smallest_chunk_size = is_class() ? ClassSpecializedChunk : SpecializedChunk;
  _occupancy_map = new OccupancyMap(bottom(), reserved_words(), smallest_chunk_size);

  return result;
}

void VirtualSpaceNode::print_on(outputStream* st, size_t scale) const {
  size_t used_words = used_words_in_vs();
  size_t commit_words = committed_words();
  size_t res_words = reserved_words();
  VirtualSpace* vs = virtual_space();

  st->print("node @" PTR_FORMAT ": ", p2i(this));
  st->print("reserved=");
  print_scaled_words(st, res_words, scale);
  st->print(", committed=");
  print_scaled_words_and_percentage(st, commit_words, res_words, scale);
  st->print(", used=");
  print_scaled_words_and_percentage(st, used_words, res_words, scale);
  st->cr();
  st->print("   [" PTR_FORMAT ", " PTR_FORMAT ", "
      PTR_FORMAT ", " PTR_FORMAT ")",
      p2i(bottom()), p2i(top()), p2i(end()),
      p2i(vs->high_boundary()));
}

#ifdef ASSERT
void VirtualSpaceNode::mangle() {
  size_t word_size = capacity_words_in_vs();
  Copy::fill_to_words((HeapWord*) low(), word_size, 0xf1f1f1f1);
}
#endif // ASSERT

void VirtualSpaceNode::retire(ChunkManager* chunk_manager) {
  DEBUG_ONLY(verify_container_count();)
  assert(this->is_class() == chunk_manager->is_class(), "Wrong ChunkManager?");
  for (int i = (int)MediumIndex; i >= (int)ZeroIndex; --i) {
    ChunkIndex index = (ChunkIndex)i;
    size_t chunk_size = chunk_manager->size_by_index(index);

    while (free_words_in_vs() >= chunk_size) {
      Metachunk* chunk = get_chunk_vs(chunk_size);
      // Chunk will be allocated aligned, so allocation may require
      // additional padding chunks. That may cause above allocation to
      // fail. Just ignore the failed allocation and continue with the
      // next smaller chunk size. As the VirtualSpaceNode comitted
      // size should be a multiple of the smallest chunk size, we
      // should always be able to fill the VirtualSpace completely.
      if (chunk == NULL) {
        break;
      }
      chunk_manager->return_single_chunk(chunk);
    }
    DEBUG_ONLY(verify_container_count();)
  }
  assert(free_words_in_vs() == 0, "should be empty now");
}

} // namespace metaspace

