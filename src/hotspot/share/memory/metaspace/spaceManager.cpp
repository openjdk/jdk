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
#include "memory/metaspace/chunkManager.hpp"
#include "memory/metaspace/metachunk.hpp"
#include "memory/metaspace/metaDebug.hpp"
#include "memory/metaspace/metaspaceCommon.hpp"
#include "memory/metaspace/spaceManager.hpp"
#include "memory/metaspace/virtualSpaceList.hpp"
#include "runtime/atomic.hpp"
#include "runtime/init.hpp"
#include "services/memoryService.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

namespace metaspace {

#define assert_counter(expected_value, real_value, msg) \
  assert( (expected_value) == (real_value),             \
         "Counter mismatch (%s): expected " SIZE_FORMAT \
         ", but got: " SIZE_FORMAT ".", msg, expected_value, \
         real_value);

// SpaceManager methods

size_t SpaceManager::adjust_initial_chunk_size(size_t requested, bool is_class_space) {
  size_t chunk_sizes[] = {
      specialized_chunk_size(is_class_space),
      small_chunk_size(is_class_space),
      medium_chunk_size(is_class_space)
  };

  // Adjust up to one of the fixed chunk sizes ...
  for (size_t i = 0; i < ARRAY_SIZE(chunk_sizes); i++) {
    if (requested <= chunk_sizes[i]) {
      return chunk_sizes[i];
    }
  }

  // ... or return the size as a humongous chunk.
  return requested;
}

size_t SpaceManager::adjust_initial_chunk_size(size_t requested) const {
  return adjust_initial_chunk_size(requested, is_class());
}

size_t SpaceManager::get_initial_chunk_size(Metaspace::MetaspaceType type) const {
  size_t requested;

  if (is_class()) {
    switch (type) {
    case Metaspace::BootMetaspaceType:       requested = Metaspace::first_class_chunk_word_size(); break;
    case Metaspace::AnonymousMetaspaceType:  requested = ClassSpecializedChunk; break;
    case Metaspace::ReflectionMetaspaceType: requested = ClassSpecializedChunk; break;
    default:                                 requested = ClassSmallChunk; break;
    }
  } else {
    switch (type) {
    case Metaspace::BootMetaspaceType:       requested = Metaspace::first_chunk_word_size(); break;
    case Metaspace::AnonymousMetaspaceType:  requested = SpecializedChunk; break;
    case Metaspace::ReflectionMetaspaceType: requested = SpecializedChunk; break;
    default:                                 requested = SmallChunk; break;
    }
  }

  // Adjust to one of the fixed chunk sizes (unless humongous)
  const size_t adjusted = adjust_initial_chunk_size(requested);

  assert(adjusted != 0, "Incorrect initial chunk size. Requested: "
         SIZE_FORMAT " adjusted: " SIZE_FORMAT, requested, adjusted);

  return adjusted;
}

void SpaceManager::locked_print_chunks_in_use_on(outputStream* st) const {

  for (ChunkIndex i = ZeroIndex; i < NumberOfInUseLists; i = next_chunk_index(i)) {
    st->print("SpaceManager: " UINTX_FORMAT " %s chunks.",
        num_chunks_by_type(i), chunk_size_name(i));
  }

  chunk_manager()->locked_print_free_chunks(st);
}

size_t SpaceManager::calc_chunk_size(size_t word_size) {

  // Decide between a small chunk and a medium chunk.  Up to
  // _small_chunk_limit small chunks can be allocated.
  // After that a medium chunk is preferred.
  size_t chunk_word_size;

  // Special case for anonymous metadata space.
  // Anonymous metadata space is usually small, with majority within 1K - 2K range and
  // rarely about 4K (64-bits JVM).
  // Instead of jumping to SmallChunk after initial chunk exhausted, keeping allocation
  // from SpecializeChunk up to _anon_or_delegating_metadata_specialize_chunk_limit (4)
  // reduces space waste from 60+% to around 30%.
  if ((_space_type == Metaspace::AnonymousMetaspaceType || _space_type == Metaspace::ReflectionMetaspaceType) &&
      _mdtype == Metaspace::NonClassType &&
      num_chunks_by_type(SpecializedIndex) < anon_and_delegating_metadata_specialize_chunk_limit &&
      word_size + Metachunk::overhead() <= SpecializedChunk) {
    return SpecializedChunk;
  }

  if (num_chunks_by_type(MediumIndex) == 0 &&
      num_chunks_by_type(SmallIndex) < small_chunk_limit) {
    chunk_word_size = (size_t) small_chunk_size();
    if (word_size + Metachunk::overhead() > small_chunk_size()) {
      chunk_word_size = medium_chunk_size();
    }
  } else {
    chunk_word_size = medium_chunk_size();
  }

  // Might still need a humongous chunk.  Enforce
  // humongous allocations sizes to be aligned up to
  // the smallest chunk size.
  size_t if_humongous_sized_chunk =
    align_up(word_size + Metachunk::overhead(),
                  smallest_chunk_size());
  chunk_word_size =
    MAX2((size_t) chunk_word_size, if_humongous_sized_chunk);

  assert(!SpaceManager::is_humongous(word_size) ||
         chunk_word_size == if_humongous_sized_chunk,
         "Size calculation is wrong, word_size " SIZE_FORMAT
         " chunk_word_size " SIZE_FORMAT,
         word_size, chunk_word_size);
  Log(gc, metaspace, alloc) log;
  if (log.is_trace() && SpaceManager::is_humongous(word_size)) {
    log.trace("Metadata humongous allocation:");
    log.trace("  word_size " PTR_FORMAT, word_size);
    log.trace("  chunk_word_size " PTR_FORMAT, chunk_word_size);
    log.trace("    chunk overhead " PTR_FORMAT, Metachunk::overhead());
  }
  return chunk_word_size;
}

void SpaceManager::track_metaspace_memory_usage() {
  if (is_init_completed()) {
    if (is_class()) {
      MemoryService::track_compressed_class_memory_usage();
    }
    MemoryService::track_metaspace_memory_usage();
  }
}

MetaWord* SpaceManager::grow_and_allocate(size_t word_size) {
  assert_lock_strong(_lock);
  assert(vs_list()->current_virtual_space() != NULL,
         "Should have been set");
  assert(current_chunk() == NULL ||
         current_chunk()->allocate(word_size) == NULL,
         "Don't need to expand");
  MutexLockerEx cl(MetaspaceExpand_lock, Mutex::_no_safepoint_check_flag);

  if (log_is_enabled(Trace, gc, metaspace, freelist)) {
    size_t words_left = 0;
    size_t words_used = 0;
    if (current_chunk() != NULL) {
      words_left = current_chunk()->free_word_size();
      words_used = current_chunk()->used_word_size();
    }
    log_trace(gc, metaspace, freelist)("SpaceManager::grow_and_allocate for " SIZE_FORMAT " words " SIZE_FORMAT " words used " SIZE_FORMAT " words left",
                                       word_size, words_used, words_left);
  }

  // Get another chunk
  size_t chunk_word_size = calc_chunk_size(word_size);
  Metachunk* next = get_new_chunk(chunk_word_size);

  MetaWord* mem = NULL;

  // If a chunk was available, add it to the in-use chunk list
  // and do an allocation from it.
  if (next != NULL) {
    // Add to this manager's list of chunks in use.
    // If the new chunk is humongous, it was created to serve a single large allocation. In that
    // case it usually makes no sense to make it the current chunk, since the next allocation would
    // need to allocate a new chunk anyway, while we would now prematurely retire a perfectly
    // good chunk which could be used for more normal allocations.
    bool make_current = true;
    if (next->get_chunk_type() == HumongousIndex &&
        current_chunk() != NULL) {
      make_current = false;
    }
    add_chunk(next, make_current);
    mem = next->allocate(word_size);
  }

  // Track metaspace memory usage statistic.
  track_metaspace_memory_usage();

  return mem;
}

void SpaceManager::print_on(outputStream* st) const {
  SpaceManagerStatistics stat;
  add_to_statistics(&stat); // will lock _lock.
  stat.print_on(st, 1*K, false);
}

SpaceManager::SpaceManager(Metaspace::MetadataType mdtype,
                           Metaspace::MetaspaceType space_type,//
                           Mutex* lock) :
  _mdtype(mdtype),
  _space_type(space_type),
  _capacity_words(0),
  _used_words(0),
  _overhead_words(0),
  _block_freelists(NULL),
  _lock(lock),
  _chunk_list(NULL),
  _current_chunk(NULL)
{
  Metadebug::init_allocation_fail_alot_count();
  memset(_num_chunks_by_type, 0, sizeof(_num_chunks_by_type));
  log_trace(gc, metaspace, freelist)("SpaceManager(): " PTR_FORMAT, p2i(this));
}

void SpaceManager::account_for_new_chunk(const Metachunk* new_chunk) {

  assert_lock_strong(MetaspaceExpand_lock);

  _capacity_words += new_chunk->word_size();
  _overhead_words += Metachunk::overhead();
  DEBUG_ONLY(new_chunk->verify());
  _num_chunks_by_type[new_chunk->get_chunk_type()] ++;

  // Adjust global counters:
  MetaspaceUtils::inc_capacity(mdtype(), new_chunk->word_size());
  MetaspaceUtils::inc_overhead(mdtype(), Metachunk::overhead());
}

void SpaceManager::account_for_allocation(size_t words) {
  // Note: we should be locked with the ClassloaderData-specific metaspace lock.
  // We may or may not be locked with the global metaspace expansion lock.
  assert_lock_strong(lock());

  // Add to the per SpaceManager totals. This can be done non-atomically.
  _used_words += words;

  // Adjust global counters. This will be done atomically.
  MetaspaceUtils::inc_used(mdtype(), words);
}

void SpaceManager::account_for_spacemanager_death() {

  assert_lock_strong(MetaspaceExpand_lock);

  MetaspaceUtils::dec_capacity(mdtype(), _capacity_words);
  MetaspaceUtils::dec_overhead(mdtype(), _overhead_words);
  MetaspaceUtils::dec_used(mdtype(), _used_words);
}

SpaceManager::~SpaceManager() {

  // This call this->_lock which can't be done while holding MetaspaceExpand_lock
  DEBUG_ONLY(verify_metrics());

  MutexLockerEx fcl(MetaspaceExpand_lock,
                    Mutex::_no_safepoint_check_flag);

  chunk_manager()->slow_locked_verify();

  account_for_spacemanager_death();

  Log(gc, metaspace, freelist) log;
  if (log.is_trace()) {
    log.trace("~SpaceManager(): " PTR_FORMAT, p2i(this));
    ResourceMark rm;
    LogStream ls(log.trace());
    locked_print_chunks_in_use_on(&ls);
    if (block_freelists() != NULL) {
      block_freelists()->print_on(&ls);
    }
  }

  // Add all the chunks in use by this space manager
  // to the global list of free chunks.

  // Follow each list of chunks-in-use and add them to the
  // free lists.  Each list is NULL terminated.
  chunk_manager()->return_chunk_list(chunk_list());
#ifdef ASSERT
  _chunk_list = NULL;
  _current_chunk = NULL;
#endif

  chunk_manager()->slow_locked_verify();

  if (_block_freelists != NULL) {
    delete _block_freelists;
  }
}

void SpaceManager::deallocate(MetaWord* p, size_t word_size) {
  assert_lock_strong(lock());
  // Allocations and deallocations are in raw_word_size
  size_t raw_word_size = get_allocation_word_size(word_size);
  // Lazily create a block_freelist
  if (block_freelists() == NULL) {
    _block_freelists = new BlockFreelist();
  }
  block_freelists()->return_block(p, raw_word_size);
  DEBUG_ONLY(Atomic::inc(&(g_internal_statistics.num_deallocs)));
}

// Adds a chunk to the list of chunks in use.
void SpaceManager::add_chunk(Metachunk* new_chunk, bool make_current) {

  assert_lock_strong(_lock);
  assert(new_chunk != NULL, "Should not be NULL");
  assert(new_chunk->next() == NULL, "Should not be on a list");

  new_chunk->reset_empty();

  // Find the correct list and and set the current
  // chunk for that list.
  ChunkIndex index = chunk_manager()->list_index(new_chunk->word_size());

  if (make_current) {
    // If we are to make the chunk current, retire the old current chunk and replace
    // it with the new chunk.
    retire_current_chunk();
    set_current_chunk(new_chunk);
  }

  // Add the new chunk at the head of its respective chunk list.
  new_chunk->set_next(_chunk_list);
  _chunk_list = new_chunk;

  // Adjust counters.
  account_for_new_chunk(new_chunk);

  assert(new_chunk->is_empty(), "Not ready for reuse");
  Log(gc, metaspace, freelist) log;
  if (log.is_trace()) {
    log.trace("SpaceManager::added chunk: ");
    ResourceMark rm;
    LogStream ls(log.trace());
    new_chunk->print_on(&ls);
    chunk_manager()->locked_print_free_chunks(&ls);
  }
}

void SpaceManager::retire_current_chunk() {
  if (current_chunk() != NULL) {
    size_t remaining_words = current_chunk()->free_word_size();
    if (remaining_words >= SmallBlocks::small_block_min_size()) {
      MetaWord* ptr = current_chunk()->allocate(remaining_words);
      deallocate(ptr, remaining_words);
      account_for_allocation(remaining_words);
    }
  }
}

Metachunk* SpaceManager::get_new_chunk(size_t chunk_word_size) {
  // Get a chunk from the chunk freelist
  Metachunk* next = chunk_manager()->chunk_freelist_allocate(chunk_word_size);

  if (next == NULL) {
    next = vs_list()->get_new_chunk(chunk_word_size,
                                    medium_chunk_bunch());
  }

  Log(gc, metaspace, alloc) log;
  if (log.is_trace() && next != NULL &&
      SpaceManager::is_humongous(next->word_size())) {
    log.trace("  new humongous chunk word size " PTR_FORMAT, next->word_size());
  }

  return next;
}

MetaWord* SpaceManager::allocate(size_t word_size) {
  MutexLockerEx cl(lock(), Mutex::_no_safepoint_check_flag);
  size_t raw_word_size = get_allocation_word_size(word_size);
  BlockFreelist* fl =  block_freelists();
  MetaWord* p = NULL;

  DEBUG_ONLY(if (VerifyMetaspace) verify_metrics_locked());

  // Allocation from the dictionary is expensive in the sense that
  // the dictionary has to be searched for a size.  Don't allocate
  // from the dictionary until it starts to get fat.  Is this
  // a reasonable policy?  Maybe an skinny dictionary is fast enough
  // for allocations.  Do some profiling.  JJJ
  if (fl != NULL && fl->total_size() > allocation_from_dictionary_limit) {
    p = fl->get_block(raw_word_size);
    if (p != NULL) {
      DEBUG_ONLY(Atomic::inc(&g_internal_statistics.num_allocs_from_deallocated_blocks));
    }
  }
  if (p == NULL) {
    p = allocate_work(raw_word_size);
  }

  return p;
}

// Returns the address of spaced allocated for "word_size".
// This methods does not know about blocks (Metablocks)
MetaWord* SpaceManager::allocate_work(size_t word_size) {
  assert_lock_strong(lock());
#ifdef ASSERT
  if (Metadebug::test_metadata_failure()) {
    return NULL;
  }
#endif
  // Is there space in the current chunk?
  MetaWord* result = NULL;

  if (current_chunk() != NULL) {
    result = current_chunk()->allocate(word_size);
  }

  if (result == NULL) {
    result = grow_and_allocate(word_size);
  }

  if (result != NULL) {
    account_for_allocation(word_size);
  }

  return result;
}

void SpaceManager::verify() {
  Metachunk* curr = chunk_list();
  while (curr != NULL) {
    DEBUG_ONLY(do_verify_chunk(curr);)
    assert(curr->is_tagged_free() == false, "Chunk should be tagged as in use.");
    curr = curr->next();
  }
}

void SpaceManager::verify_chunk_size(Metachunk* chunk) {
  assert(is_humongous(chunk->word_size()) ||
         chunk->word_size() == medium_chunk_size() ||
         chunk->word_size() == small_chunk_size() ||
         chunk->word_size() == specialized_chunk_size(),
         "Chunk size is wrong");
  return;
}

void SpaceManager::add_to_statistics_locked(SpaceManagerStatistics* out) const {
  assert_lock_strong(lock());
  Metachunk* chunk = chunk_list();
  while (chunk != NULL) {
    UsedChunksStatistics& chunk_stat = out->chunk_stats(chunk->get_chunk_type());
    chunk_stat.add_num(1);
    chunk_stat.add_cap(chunk->word_size());
    chunk_stat.add_overhead(Metachunk::overhead());
    chunk_stat.add_used(chunk->used_word_size() - Metachunk::overhead());
    if (chunk != current_chunk()) {
      chunk_stat.add_waste(chunk->free_word_size());
    } else {
      chunk_stat.add_free(chunk->free_word_size());
    }
    chunk = chunk->next();
  }
  if (block_freelists() != NULL) {
    out->add_free_blocks_info(block_freelists()->num_blocks(), block_freelists()->total_size());
  }
}

void SpaceManager::add_to_statistics(SpaceManagerStatistics* out) const {
  MutexLockerEx cl(lock(), Mutex::_no_safepoint_check_flag);
  add_to_statistics_locked(out);
}

#ifdef ASSERT
void SpaceManager::verify_metrics_locked() const {
  assert_lock_strong(lock());

  SpaceManagerStatistics stat;
  add_to_statistics_locked(&stat);

  UsedChunksStatistics chunk_stats = stat.totals();

  DEBUG_ONLY(chunk_stats.check_sanity());

  assert_counter(_capacity_words, chunk_stats.cap(), "SpaceManager::_capacity_words");
  assert_counter(_used_words, chunk_stats.used(), "SpaceManager::_used_words");
  assert_counter(_overhead_words, chunk_stats.overhead(), "SpaceManager::_overhead_words");
}

void SpaceManager::verify_metrics() const {
  MutexLockerEx cl(lock(), Mutex::_no_safepoint_check_flag);
  verify_metrics_locked();
}
#endif // ASSERT


} // namespace metaspace

