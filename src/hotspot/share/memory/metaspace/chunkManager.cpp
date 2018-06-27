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
#include "memory/binaryTreeDictionary.inline.hpp"
#include "memory/freeList.inline.hpp"
#include "memory/metaspace/chunkManager.hpp"
#include "memory/metaspace/metachunk.hpp"
#include "memory/metaspace/metaspaceCommon.hpp"
#include "memory/metaspace/metaspaceStatistics.hpp"
#include "memory/metaspace/occupancyMap.hpp"
#include "memory/metaspace/virtualSpaceNode.hpp"
#include "runtime/mutexLocker.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"

namespace metaspace {

ChunkManager::ChunkManager(bool is_class)
      : _is_class(is_class), _free_chunks_total(0), _free_chunks_count(0) {
  _free_chunks[SpecializedIndex].set_size(get_size_for_nonhumongous_chunktype(SpecializedIndex, is_class));
  _free_chunks[SmallIndex].set_size(get_size_for_nonhumongous_chunktype(SmallIndex, is_class));
  _free_chunks[MediumIndex].set_size(get_size_for_nonhumongous_chunktype(MediumIndex, is_class));
}

void ChunkManager::remove_chunk(Metachunk* chunk) {
  size_t word_size = chunk->word_size();
  ChunkIndex index = list_index(word_size);
  if (index != HumongousIndex) {
    free_chunks(index)->remove_chunk(chunk);
  } else {
    humongous_dictionary()->remove_chunk(chunk);
  }

  // Chunk has been removed from the chunks free list, update counters.
  account_for_removed_chunk(chunk);
}

bool ChunkManager::attempt_to_coalesce_around_chunk(Metachunk* chunk, ChunkIndex target_chunk_type) {
  assert_lock_strong(MetaspaceExpand_lock);
  assert(chunk != NULL, "invalid chunk pointer");
  // Check for valid merge combinations.
  assert((chunk->get_chunk_type() == SpecializedIndex &&
          (target_chunk_type == SmallIndex || target_chunk_type == MediumIndex)) ||
         (chunk->get_chunk_type() == SmallIndex && target_chunk_type == MediumIndex),
        "Invalid chunk merge combination.");

  const size_t target_chunk_word_size =
    get_size_for_nonhumongous_chunktype(target_chunk_type, this->is_class());

  // [ prospective merge region )
  MetaWord* const p_merge_region_start =
    (MetaWord*) align_down(chunk, target_chunk_word_size * sizeof(MetaWord));
  MetaWord* const p_merge_region_end =
    p_merge_region_start + target_chunk_word_size;

  // We need the VirtualSpaceNode containing this chunk and its occupancy map.
  VirtualSpaceNode* const vsn = chunk->container();
  OccupancyMap* const ocmap = vsn->occupancy_map();

  // The prospective chunk merge range must be completely contained by the
  // committed range of the virtual space node.
  if (p_merge_region_start < vsn->bottom() || p_merge_region_end > vsn->top()) {
    return false;
  }

  // Only attempt to merge this range if at its start a chunk starts and at its end
  // a chunk ends. If a chunk (can only be humongous) straddles either start or end
  // of that range, we cannot merge.
  if (!ocmap->chunk_starts_at_address(p_merge_region_start)) {
    return false;
  }
  if (p_merge_region_end < vsn->top() &&
      !ocmap->chunk_starts_at_address(p_merge_region_end)) {
    return false;
  }

  // Now check if the prospective merge area contains live chunks. If it does we cannot merge.
  if (ocmap->is_region_in_use(p_merge_region_start, target_chunk_word_size)) {
    return false;
  }

  // Success! Remove all chunks in this region...
  log_trace(gc, metaspace, freelist)("%s: coalescing chunks in area [%p-%p)...",
    (is_class() ? "class space" : "metaspace"),
    p_merge_region_start, p_merge_region_end);

  const int num_chunks_removed =
    remove_chunks_in_area(p_merge_region_start, target_chunk_word_size);

  // ... and create a single new bigger chunk.
  Metachunk* const p_new_chunk =
      ::new (p_merge_region_start) Metachunk(target_chunk_type, is_class(), target_chunk_word_size, vsn);
  assert(p_new_chunk == (Metachunk*)p_merge_region_start, "Sanity");
  p_new_chunk->set_origin(origin_merge);

  log_trace(gc, metaspace, freelist)("%s: created coalesced chunk at %p, size " SIZE_FORMAT_HEX ".",
    (is_class() ? "class space" : "metaspace"),
    p_new_chunk, p_new_chunk->word_size() * sizeof(MetaWord));

  // Fix occupancy map: remove old start bits of the small chunks and set new start bit.
  ocmap->wipe_chunk_start_bits_in_region(p_merge_region_start, target_chunk_word_size);
  ocmap->set_chunk_starts_at_address(p_merge_region_start, true);

  // Mark chunk as free. Note: it is not necessary to update the occupancy
  // map in-use map, because the old chunks were also free, so nothing
  // should have changed.
  p_new_chunk->set_is_tagged_free(true);

  // Add new chunk to its freelist.
  ChunkList* const list = free_chunks(target_chunk_type);
  list->return_chunk_at_head(p_new_chunk);

  // And adjust ChunkManager:: _free_chunks_count (_free_chunks_total
  // should not have changed, because the size of the space should be the same)
  _free_chunks_count -= num_chunks_removed;
  _free_chunks_count ++;

  // VirtualSpaceNode::container_count does not have to be modified:
  // it means "number of active (non-free) chunks", so merging free chunks
  // should not affect that count.

  // At the end of a chunk merge, run verification tests.
  if (VerifyMetaspace) {
    DEBUG_ONLY(this->locked_verify());
    DEBUG_ONLY(vsn->verify());
  }

  return true;
}

// Remove all chunks in the given area - the chunks are supposed to be free -
// from their corresponding freelists. Mark them as invalid.
// - This does not correct the occupancy map.
// - This does not adjust the counters in ChunkManager.
// - Does not adjust container count counter in containing VirtualSpaceNode
// Returns number of chunks removed.
int ChunkManager::remove_chunks_in_area(MetaWord* p, size_t word_size) {
  assert(p != NULL && word_size > 0, "Invalid range.");
  const size_t smallest_chunk_size = get_size_for_nonhumongous_chunktype(SpecializedIndex, is_class());
  assert_is_aligned(word_size, smallest_chunk_size);

  Metachunk* const start = (Metachunk*) p;
  const Metachunk* const end = (Metachunk*)(p + word_size);
  Metachunk* cur = start;
  int num_removed = 0;
  while (cur < end) {
    Metachunk* next = (Metachunk*)(((MetaWord*)cur) + cur->word_size());
    DEBUG_ONLY(do_verify_chunk(cur));
    assert(cur->get_chunk_type() != HumongousIndex, "Unexpected humongous chunk found at %p.", cur);
    assert(cur->is_tagged_free(), "Chunk expected to be free (%p)", cur);
    log_trace(gc, metaspace, freelist)("%s: removing chunk %p, size " SIZE_FORMAT_HEX ".",
      (is_class() ? "class space" : "metaspace"),
      cur, cur->word_size() * sizeof(MetaWord));
    cur->remove_sentinel();
    // Note: cannot call ChunkManager::remove_chunk, because that
    // modifies the counters in ChunkManager, which we do not want. So
    // we call remove_chunk on the freelist directly (see also the
    // splitting function which does the same).
    ChunkList* const list = free_chunks(list_index(cur->word_size()));
    list->remove_chunk(cur);
    num_removed ++;
    cur = next;
  }
  return num_removed;
}

size_t ChunkManager::free_chunks_total_words() {
  return _free_chunks_total;
}

size_t ChunkManager::free_chunks_total_bytes() {
  return free_chunks_total_words() * BytesPerWord;
}

// Update internal accounting after a chunk was added
void ChunkManager::account_for_added_chunk(const Metachunk* c) {
  assert_lock_strong(MetaspaceExpand_lock);
  _free_chunks_count ++;
  _free_chunks_total += c->word_size();
}

// Update internal accounting after a chunk was removed
void ChunkManager::account_for_removed_chunk(const Metachunk* c) {
  assert_lock_strong(MetaspaceExpand_lock);
  assert(_free_chunks_count >= 1,
    "ChunkManager::_free_chunks_count: about to go negative (" SIZE_FORMAT ").", _free_chunks_count);
  assert(_free_chunks_total >= c->word_size(),
    "ChunkManager::_free_chunks_total: about to go negative"
     "(now: " SIZE_FORMAT ", decrement value: " SIZE_FORMAT ").", _free_chunks_total, c->word_size());
  _free_chunks_count --;
  _free_chunks_total -= c->word_size();
}

size_t ChunkManager::free_chunks_count() {
#ifdef ASSERT
  if (!UseConcMarkSweepGC && !MetaspaceExpand_lock->is_locked()) {
    MutexLockerEx cl(MetaspaceExpand_lock,
                     Mutex::_no_safepoint_check_flag);
    // This lock is only needed in debug because the verification
    // of the _free_chunks_totals walks the list of free chunks
    slow_locked_verify_free_chunks_count();
  }
#endif
  return _free_chunks_count;
}

ChunkIndex ChunkManager::list_index(size_t size) {
  return get_chunk_type_by_size(size, is_class());
}

size_t ChunkManager::size_by_index(ChunkIndex index) const {
  index_bounds_check(index);
  assert(index != HumongousIndex, "Do not call for humongous chunks.");
  return get_size_for_nonhumongous_chunktype(index, is_class());
}

void ChunkManager::locked_verify_free_chunks_total() {
  assert_lock_strong(MetaspaceExpand_lock);
  assert(sum_free_chunks() == _free_chunks_total,
         "_free_chunks_total " SIZE_FORMAT " is not the"
         " same as sum " SIZE_FORMAT, _free_chunks_total,
         sum_free_chunks());
}

void ChunkManager::locked_verify_free_chunks_count() {
  assert_lock_strong(MetaspaceExpand_lock);
  assert(sum_free_chunks_count() == _free_chunks_count,
         "_free_chunks_count " SIZE_FORMAT " is not the"
         " same as sum " SIZE_FORMAT, _free_chunks_count,
         sum_free_chunks_count());
}

void ChunkManager::verify() {
  MutexLockerEx cl(MetaspaceExpand_lock,
                     Mutex::_no_safepoint_check_flag);
  locked_verify();
}

void ChunkManager::locked_verify() {
  locked_verify_free_chunks_count();
  locked_verify_free_chunks_total();
  for (ChunkIndex i = ZeroIndex; i < NumberOfFreeLists; i = next_chunk_index(i)) {
    ChunkList* list = free_chunks(i);
    if (list != NULL) {
      Metachunk* chunk = list->head();
      while (chunk) {
        DEBUG_ONLY(do_verify_chunk(chunk);)
        assert(chunk->is_tagged_free(), "Chunk should be tagged as free.");
        chunk = chunk->next();
      }
    }
  }
}

void ChunkManager::locked_print_free_chunks(outputStream* st) {
  assert_lock_strong(MetaspaceExpand_lock);
  st->print_cr("Free chunk total " SIZE_FORMAT "  count " SIZE_FORMAT,
                _free_chunks_total, _free_chunks_count);
}

void ChunkManager::locked_print_sum_free_chunks(outputStream* st) {
  assert_lock_strong(MetaspaceExpand_lock);
  st->print_cr("Sum free chunk total " SIZE_FORMAT "  count " SIZE_FORMAT,
                sum_free_chunks(), sum_free_chunks_count());
}

ChunkList* ChunkManager::free_chunks(ChunkIndex index) {
  assert(index == SpecializedIndex || index == SmallIndex || index == MediumIndex,
         "Bad index: %d", (int)index);

  return &_free_chunks[index];
}

// These methods that sum the free chunk lists are used in printing
// methods that are used in product builds.
size_t ChunkManager::sum_free_chunks() {
  assert_lock_strong(MetaspaceExpand_lock);
  size_t result = 0;
  for (ChunkIndex i = ZeroIndex; i < NumberOfFreeLists; i = next_chunk_index(i)) {
    ChunkList* list = free_chunks(i);

    if (list == NULL) {
      continue;
    }

    result = result + list->count() * list->size();
  }
  result = result + humongous_dictionary()->total_size();
  return result;
}

size_t ChunkManager::sum_free_chunks_count() {
  assert_lock_strong(MetaspaceExpand_lock);
  size_t count = 0;
  for (ChunkIndex i = ZeroIndex; i < NumberOfFreeLists; i = next_chunk_index(i)) {
    ChunkList* list = free_chunks(i);
    if (list == NULL) {
      continue;
    }
    count = count + list->count();
  }
  count = count + humongous_dictionary()->total_free_blocks();
  return count;
}

ChunkList* ChunkManager::find_free_chunks_list(size_t word_size) {
  ChunkIndex index = list_index(word_size);
  assert(index < HumongousIndex, "No humongous list");
  return free_chunks(index);
}

// Helper for chunk splitting: given a target chunk size and a larger free chunk,
// split up the larger chunk into n smaller chunks, at least one of which should be
// the target chunk of target chunk size. The smaller chunks, including the target
// chunk, are returned to the freelist. The pointer to the target chunk is returned.
// Note that this chunk is supposed to be removed from the freelist right away.
Metachunk* ChunkManager::split_chunk(size_t target_chunk_word_size, Metachunk* larger_chunk) {
  assert(larger_chunk->word_size() > target_chunk_word_size, "Sanity");

  const ChunkIndex larger_chunk_index = larger_chunk->get_chunk_type();
  const ChunkIndex target_chunk_index = get_chunk_type_by_size(target_chunk_word_size, is_class());

  MetaWord* const region_start = (MetaWord*)larger_chunk;
  const size_t region_word_len = larger_chunk->word_size();
  MetaWord* const region_end = region_start + region_word_len;
  VirtualSpaceNode* const vsn = larger_chunk->container();
  OccupancyMap* const ocmap = vsn->occupancy_map();

  // Any larger non-humongous chunk size is a multiple of any smaller chunk size.
  // Since non-humongous chunks are aligned to their chunk size, the larger chunk should start
  // at an address suitable to place the smaller target chunk.
  assert_is_aligned(region_start, target_chunk_word_size);

  // Remove old chunk.
  free_chunks(larger_chunk_index)->remove_chunk(larger_chunk);
  larger_chunk->remove_sentinel();

  // Prevent access to the old chunk from here on.
  larger_chunk = NULL;
  // ... and wipe it.
  DEBUG_ONLY(memset(region_start, 0xfe, region_word_len * BytesPerWord));

  // In its place create first the target chunk...
  MetaWord* p = region_start;
  Metachunk* target_chunk = ::new (p) Metachunk(target_chunk_index, is_class(), target_chunk_word_size, vsn);
  assert(target_chunk == (Metachunk*)p, "Sanity");
  target_chunk->set_origin(origin_split);

  // Note: we do not need to mark its start in the occupancy map
  // because it coincides with the old chunk start.

  // Mark chunk as free and return to the freelist.
  do_update_in_use_info_for_chunk(target_chunk, false);
  free_chunks(target_chunk_index)->return_chunk_at_head(target_chunk);

  // This chunk should now be valid and can be verified.
  DEBUG_ONLY(do_verify_chunk(target_chunk));

  // In the remaining space create the remainder chunks.
  p += target_chunk->word_size();
  assert(p < region_end, "Sanity");

  while (p < region_end) {

    // Find the largest chunk size which fits the alignment requirements at address p.
    ChunkIndex this_chunk_index = prev_chunk_index(larger_chunk_index);
    size_t this_chunk_word_size = 0;
    for(;;) {
      this_chunk_word_size = get_size_for_nonhumongous_chunktype(this_chunk_index, is_class());
      if (is_aligned(p, this_chunk_word_size * BytesPerWord)) {
        break;
      } else {
        this_chunk_index = prev_chunk_index(this_chunk_index);
        assert(this_chunk_index >= target_chunk_index, "Sanity");
      }
    }

    assert(this_chunk_word_size >= target_chunk_word_size, "Sanity");
    assert(is_aligned(p, this_chunk_word_size * BytesPerWord), "Sanity");
    assert(p + this_chunk_word_size <= region_end, "Sanity");

    // Create splitting chunk.
    Metachunk* this_chunk = ::new (p) Metachunk(this_chunk_index, is_class(), this_chunk_word_size, vsn);
    assert(this_chunk == (Metachunk*)p, "Sanity");
    this_chunk->set_origin(origin_split);
    ocmap->set_chunk_starts_at_address(p, true);
    do_update_in_use_info_for_chunk(this_chunk, false);

    // This chunk should be valid and can be verified.
    DEBUG_ONLY(do_verify_chunk(this_chunk));

    // Return this chunk to freelist and correct counter.
    free_chunks(this_chunk_index)->return_chunk_at_head(this_chunk);
    _free_chunks_count ++;

    log_trace(gc, metaspace, freelist)("Created chunk at " PTR_FORMAT ", word size "
      SIZE_FORMAT_HEX " (%s), in split region [" PTR_FORMAT "..." PTR_FORMAT ").",
      p2i(this_chunk), this_chunk->word_size(), chunk_size_name(this_chunk_index),
      p2i(region_start), p2i(region_end));

    p += this_chunk_word_size;

  }

  return target_chunk;
}

Metachunk* ChunkManager::free_chunks_get(size_t word_size) {
  assert_lock_strong(MetaspaceExpand_lock);

  slow_locked_verify();

  Metachunk* chunk = NULL;
  bool we_did_split_a_chunk = false;

  if (list_index(word_size) != HumongousIndex) {

    ChunkList* free_list = find_free_chunks_list(word_size);
    assert(free_list != NULL, "Sanity check");

    chunk = free_list->head();

    if (chunk == NULL) {
      // Split large chunks into smaller chunks if there are no smaller chunks, just large chunks.
      // This is the counterpart of the coalescing-upon-chunk-return.

      ChunkIndex target_chunk_index = get_chunk_type_by_size(word_size, is_class());

      // Is there a larger chunk we could split?
      Metachunk* larger_chunk = NULL;
      ChunkIndex larger_chunk_index = next_chunk_index(target_chunk_index);
      while (larger_chunk == NULL && larger_chunk_index < NumberOfFreeLists) {
        larger_chunk = free_chunks(larger_chunk_index)->head();
        if (larger_chunk == NULL) {
          larger_chunk_index = next_chunk_index(larger_chunk_index);
        }
      }

      if (larger_chunk != NULL) {
        assert(larger_chunk->word_size() > word_size, "Sanity");
        assert(larger_chunk->get_chunk_type() == larger_chunk_index, "Sanity");

        // We found a larger chunk. Lets split it up:
        // - remove old chunk
        // - in its place, create new smaller chunks, with at least one chunk
        //   being of target size, the others sized as large as possible. This
        //   is to make sure the resulting chunks are "as coalesced as possible"
        //   (similar to VirtualSpaceNode::retire()).
        // Note: during this operation both ChunkManager and VirtualSpaceNode
        //  are temporarily invalid, so be careful with asserts.

        log_trace(gc, metaspace, freelist)("%s: splitting chunk " PTR_FORMAT
           ", word size " SIZE_FORMAT_HEX " (%s), to get a chunk of word size " SIZE_FORMAT_HEX " (%s)...",
          (is_class() ? "class space" : "metaspace"), p2i(larger_chunk), larger_chunk->word_size(),
          chunk_size_name(larger_chunk_index), word_size, chunk_size_name(target_chunk_index));

        chunk = split_chunk(word_size, larger_chunk);

        // This should have worked.
        assert(chunk != NULL, "Sanity");
        assert(chunk->word_size() == word_size, "Sanity");
        assert(chunk->is_tagged_free(), "Sanity");

        we_did_split_a_chunk = true;

      }
    }

    if (chunk == NULL) {
      return NULL;
    }

    // Remove the chunk as the head of the list.
    free_list->remove_chunk(chunk);

    log_trace(gc, metaspace, freelist)("ChunkManager::free_chunks_get: free_list: " PTR_FORMAT " chunks left: " SSIZE_FORMAT ".",
                                       p2i(free_list), free_list->count());

  } else {
    chunk = humongous_dictionary()->get_chunk(word_size);

    if (chunk == NULL) {
      return NULL;
    }

    log_trace(gc, metaspace, alloc)("Free list allocate humongous chunk size " SIZE_FORMAT " for requested size " SIZE_FORMAT " waste " SIZE_FORMAT,
                                    chunk->word_size(), word_size, chunk->word_size() - word_size);
  }

  // Chunk has been removed from the chunk manager; update counters.
  account_for_removed_chunk(chunk);
  do_update_in_use_info_for_chunk(chunk, true);
  chunk->container()->inc_container_count();
  chunk->inc_use_count();

  // Remove it from the links to this freelist
  chunk->set_next(NULL);
  chunk->set_prev(NULL);

  // Run some verifications (some more if we did a chunk split)
#ifdef ASSERT
  if (VerifyMetaspace) {
    locked_verify();
    VirtualSpaceNode* const vsn = chunk->container();
    vsn->verify();
    if (we_did_split_a_chunk) {
      vsn->verify_free_chunks_are_ideally_merged();
    }
  }
#endif

  return chunk;
}

Metachunk* ChunkManager::chunk_freelist_allocate(size_t word_size) {
  assert_lock_strong(MetaspaceExpand_lock);
  slow_locked_verify();

  // Take from the beginning of the list
  Metachunk* chunk = free_chunks_get(word_size);
  if (chunk == NULL) {
    return NULL;
  }

  assert((word_size <= chunk->word_size()) ||
         (list_index(chunk->word_size()) == HumongousIndex),
         "Non-humongous variable sized chunk");
  LogTarget(Trace, gc, metaspace, freelist) lt;
  if (lt.is_enabled()) {
    size_t list_count;
    if (list_index(word_size) < HumongousIndex) {
      ChunkList* list = find_free_chunks_list(word_size);
      list_count = list->count();
    } else {
      list_count = humongous_dictionary()->total_count();
    }
    LogStream ls(lt);
    ls.print("ChunkManager::chunk_freelist_allocate: " PTR_FORMAT " chunk " PTR_FORMAT "  size " SIZE_FORMAT " count " SIZE_FORMAT " ",
             p2i(this), p2i(chunk), chunk->word_size(), list_count);
    ResourceMark rm;
    locked_print_free_chunks(&ls);
  }

  return chunk;
}

void ChunkManager::return_single_chunk(Metachunk* chunk) {
  const ChunkIndex index = chunk->get_chunk_type();
  assert_lock_strong(MetaspaceExpand_lock);
  DEBUG_ONLY(do_verify_chunk(chunk);)
  assert(chunk != NULL, "Expected chunk.");
  assert(chunk->container() != NULL, "Container should have been set.");
  assert(chunk->is_tagged_free() == false, "Chunk should be in use.");
  index_bounds_check(index);

  // Note: mangle *before* returning the chunk to the freelist or dictionary. It does not
  // matter for the freelist (non-humongous chunks), but the humongous chunk dictionary
  // keeps tree node pointers in the chunk payload area which mangle will overwrite.
  DEBUG_ONLY(chunk->mangle(badMetaWordVal);)

  if (index != HumongousIndex) {
    // Return non-humongous chunk to freelist.
    ChunkList* list = free_chunks(index);
    assert(list->size() == chunk->word_size(), "Wrong chunk type.");
    list->return_chunk_at_head(chunk);
    log_trace(gc, metaspace, freelist)("returned one %s chunk at " PTR_FORMAT " to freelist.",
        chunk_size_name(index), p2i(chunk));
  } else {
    // Return humongous chunk to dictionary.
    assert(chunk->word_size() > free_chunks(MediumIndex)->size(), "Wrong chunk type.");
    assert(chunk->word_size() % free_chunks(SpecializedIndex)->size() == 0,
           "Humongous chunk has wrong alignment.");
    _humongous_dictionary.return_chunk(chunk);
    log_trace(gc, metaspace, freelist)("returned one %s chunk at " PTR_FORMAT " (word size " SIZE_FORMAT ") to freelist.",
        chunk_size_name(index), p2i(chunk), chunk->word_size());
  }
  chunk->container()->dec_container_count();
  do_update_in_use_info_for_chunk(chunk, false);

  // Chunk has been added; update counters.
  account_for_added_chunk(chunk);

  // Attempt coalesce returned chunks with its neighboring chunks:
  // if this chunk is small or special, attempt to coalesce to a medium chunk.
  if (index == SmallIndex || index == SpecializedIndex) {
    if (!attempt_to_coalesce_around_chunk(chunk, MediumIndex)) {
      // This did not work. But if this chunk is special, we still may form a small chunk?
      if (index == SpecializedIndex) {
        if (!attempt_to_coalesce_around_chunk(chunk, SmallIndex)) {
          // give up.
        }
      }
    }
  }

}

void ChunkManager::return_chunk_list(Metachunk* chunks) {
  if (chunks == NULL) {
    return;
  }
  LogTarget(Trace, gc, metaspace, freelist) log;
  if (log.is_enabled()) { // tracing
    log.print("returning list of chunks...");
  }
  unsigned num_chunks_returned = 0;
  size_t size_chunks_returned = 0;
  Metachunk* cur = chunks;
  while (cur != NULL) {
    // Capture the next link before it is changed
    // by the call to return_chunk_at_head();
    Metachunk* next = cur->next();
    if (log.is_enabled()) { // tracing
      num_chunks_returned ++;
      size_chunks_returned += cur->word_size();
    }
    return_single_chunk(cur);
    cur = next;
  }
  if (log.is_enabled()) { // tracing
    log.print("returned %u chunks to freelist, total word size " SIZE_FORMAT ".",
        num_chunks_returned, size_chunks_returned);
  }
}

void ChunkManager::collect_statistics(ChunkManagerStatistics* out) const {
  MutexLockerEx cl(MetaspaceExpand_lock, Mutex::_no_safepoint_check_flag);
  for (ChunkIndex i = ZeroIndex; i < NumberOfInUseLists; i = next_chunk_index(i)) {
    out->chunk_stats(i).add(num_free_chunks(i), size_free_chunks_in_bytes(i) / sizeof(MetaWord));
  }
}

} // namespace metaspace



