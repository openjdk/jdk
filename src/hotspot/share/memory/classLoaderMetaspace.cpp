/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "logging/log.hpp"
#include "memory/classLoaderMetaspace.hpp"
#include "memory/metaspace.hpp"
#include "memory/metaspaceUtils.hpp"
#include "memory/metaspace/chunkManager.hpp"
#include "memory/metaspace/internalStats.hpp"
#include "memory/metaspace/metablock.hpp"
#include "memory/metaspace/metaspaceArena.hpp"
#include "memory/metaspace/metaspaceArenaGrowthPolicy.hpp"
#include "memory/metaspace/metaspaceCommon.hpp"
#include "memory/metaspace/metaspaceContext.hpp"
#include "memory/metaspace/metaspaceSettings.hpp"
#include "memory/metaspace/metaspaceStatistics.hpp"
#include "memory/metaspace/runningCounters.hpp"
#include "memory/metaspaceTracer.hpp"
#include "oops/klass.hpp"
#include "runtime/mutexLocker.hpp"
#include "utilities/debug.hpp"

using metaspace::ChunkManager;
using metaspace::MetaBlock;
using metaspace::MetaspaceArena;
using metaspace::MetaspaceContext;
using metaspace::ArenaGrowthPolicy;
using metaspace::RunningCounters;
using metaspace::InternalStats;

#define LOGFMT         "CLMS @" PTR_FORMAT " "
#define LOGFMT_ARGS    p2i(this)

ClassLoaderMetaspace::ClassLoaderMetaspace(Mutex* lock, Metaspace::MetaspaceType space_type) :
    ClassLoaderMetaspace(lock, space_type,
                         MetaspaceContext::context_nonclass(),
                         MetaspaceContext::context_class(),
                         CompressedKlassPointers::klass_alignment_in_words())
{}

ClassLoaderMetaspace::ClassLoaderMetaspace(Mutex* lock, Metaspace::MetaspaceType space_type,
                                           MetaspaceContext* non_class_context,
                                           MetaspaceContext* class_context,
                                           size_t klass_alignment_words) :
  _lock(lock),
  _space_type(space_type),
  _non_class_space_arena(nullptr),
  _class_space_arena(nullptr)
{
  // Initialize non-class Arena
  _non_class_space_arena = new MetaspaceArena(
      non_class_context,
      ArenaGrowthPolicy::policy_for_space_type(space_type, false),
      Metaspace::min_allocation_alignment_words,
      "non-class arena");

  // If needed, initialize class arena
  if (class_context != nullptr) {
    _class_space_arena = new MetaspaceArena(
        class_context,
        ArenaGrowthPolicy::policy_for_space_type(space_type, true),
        klass_alignment_words,
        "class arena");
  }

  UL2(debug, "born (nonclass arena: " PTR_FORMAT ", class arena: " PTR_FORMAT ".",
      p2i(_non_class_space_arena), p2i(_class_space_arena));
}

ClassLoaderMetaspace::~ClassLoaderMetaspace() {
  UL(debug, "dies.");
  MutexLocker fcl(lock(), Mutex::_no_safepoint_check_flag);
  delete _non_class_space_arena;
  delete _class_space_arena;

}

// Allocate word_size words from Metaspace.
MetaWord* ClassLoaderMetaspace::allocate(size_t word_size, Metaspace::MetadataType mdType) {
  word_size = align_up(word_size, Metaspace::min_allocation_word_size);
  MutexLocker fcl(lock(), Mutex::_no_safepoint_check_flag);
  MetaBlock result, wastage;
  const bool is_class = have_class_space_arena() && mdType == Metaspace::ClassType;
  if (is_class) {
    assert(word_size >= (sizeof(Klass)/BytesPerWord), "weird size for klass: %zu", word_size);
    result = class_space_arena()->allocate(word_size, wastage);
  } else {
    result = non_class_space_arena()->allocate(word_size, wastage);
  }
  if (wastage.is_nonempty()) {
    non_class_space_arena()->deallocate(wastage);
  }
#ifdef ASSERT
  if (result.is_nonempty()) {
    const bool in_class_arena = class_space_arena() != nullptr ? class_space_arena()->contains(result) : false;
    const bool in_nonclass_arena = non_class_space_arena()->contains(result);
    assert((is_class && in_class_arena) || (!is_class && in_class_arena != in_nonclass_arena),
           "block from neither arena " METABLOCKFORMAT "?", METABLOCKFORMATARGS(result));
  }
#endif
  return result.base();
}

// Attempt to expand the GC threshold to be good for at least another word_size words
// and allocate. Returns null if failure. Used during Metaspace GC.
MetaWord* ClassLoaderMetaspace::expand_and_allocate(size_t word_size, Metaspace::MetadataType mdType) {
  size_t delta_bytes = MetaspaceGC::delta_capacity_until_GC(word_size * BytesPerWord);
  assert(delta_bytes > 0, "Must be");

  size_t before = 0;
  size_t after = 0;
  bool can_retry = true;
  MetaWord* res;
  bool incremented;

  // Each thread increments the HWM at most once. Even if the thread fails to increment
  // the HWM, an allocation is still attempted. This is because another thread must then
  // have incremented the HWM and therefore the allocation might still succeed.
  do {
    incremented = MetaspaceGC::inc_capacity_until_GC(delta_bytes, &after, &before, &can_retry);
    res = allocate(word_size, mdType);
  } while (!incremented && res == nullptr && can_retry);

  if (incremented) {
    Metaspace::tracer()->report_gc_threshold(before, after,
                                  MetaspaceGCThresholdUpdater::ExpandAndAllocate);
    // Keeping both for now until I am sure the old variant (gc + metaspace) is not needed anymore
    log_trace(gc, metaspace)("Increase capacity to GC from %zu to %zu", before, after);
    UL2(info, "GC threshold increased: %zu->%zu.", before, after);
  }

  return res;
}

// Prematurely returns a metaspace allocation to the _block_freelists
// because it is not needed anymore.
void ClassLoaderMetaspace::deallocate(MetaWord* ptr, size_t word_size) {
  MutexLocker fcl(lock(), Mutex::_no_safepoint_check_flag);
  NOT_LP64(word_size = align_down(word_size, Metaspace::min_allocation_word_size);)
  MetaBlock bl(ptr, word_size);
  // Add to class arena only if block is usable for encodable Klass storage.
  MetaspaceArena* receiving_arena = non_class_space_arena();
  if (Metaspace::using_class_space() && Metaspace::is_in_class_space(ptr) &&
      is_aligned(ptr, class_space_arena()->allocation_alignment_bytes())) {
    receiving_arena = class_space_arena();
  }
  receiving_arena->deallocate(bl);
  DEBUG_ONLY(InternalStats::inc_num_deallocs();)
}

// Update statistics. This walks all in-use chunks.
void ClassLoaderMetaspace::add_to_statistics(metaspace::ClmsStats* out) const {
  MutexLocker fcl(lock(), Mutex::_no_safepoint_check_flag);
  if (non_class_space_arena() != nullptr) {
    non_class_space_arena()->add_to_statistics(&out->_arena_stats_nonclass);
  }
  if (class_space_arena() != nullptr) {
    class_space_arena()->add_to_statistics(&out->_arena_stats_class);
  }
}

#ifdef ASSERT
void ClassLoaderMetaspace::verify() const {
  MutexLocker fcl(lock(), Mutex::_no_safepoint_check_flag);
  if (non_class_space_arena() != nullptr) {
    non_class_space_arena()->verify();
  }
  if (class_space_arena() != nullptr) {
    class_space_arena()->verify();
  }
}
#endif // ASSERT

// Convenience method to get the most important usage statistics.
void ClassLoaderMetaspace::usage_numbers(Metaspace::MetadataType mdType, size_t* p_used_words,
                                         size_t* p_committed_words, size_t* p_capacity_words) const {
  const MetaspaceArena* arena = (mdType == Metaspace::MetadataType::ClassType) ?
      class_space_arena() : non_class_space_arena();
  arena->usage_numbers(p_used_words, p_committed_words, p_capacity_words);
}

// Convenience method to get total usage numbers
void ClassLoaderMetaspace::usage_numbers(size_t* p_used_words, size_t* p_committed_words,
                                         size_t* p_capacity_words) const {
  size_t used_nc, comm_nc, cap_nc;
  size_t used_c = 0, comm_c = 0, cap_c = 0;
  {
    MutexLocker fcl(lock(), Mutex::_no_safepoint_check_flag);
    usage_numbers(Metaspace::MetadataType::NonClassType, &used_nc, &comm_nc, &cap_nc);
    if (have_class_space_arena()) {
      usage_numbers(Metaspace::MetadataType::ClassType, &used_c, &comm_c, &cap_c);
    }
  }
  if (p_used_words != nullptr) {
    (*p_used_words) = used_nc + used_c;
  }
  if (p_committed_words != nullptr) {
    (*p_committed_words) = comm_nc + comm_c;
  }
  if (p_capacity_words != nullptr) {
    (*p_capacity_words) = cap_nc + cap_c;
  }
}
