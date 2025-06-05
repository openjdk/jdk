/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZPAGEALLOCATOR_HPP
#define SHARE_GC_Z_ZPAGEALLOCATOR_HPP

#include "gc/z/zAddress.hpp"
#include "gc/z/zAllocationFlags.hpp"
#include "gc/z/zArray.hpp"
#include "gc/z/zGenerationId.hpp"
#include "gc/z/zGranuleMap.hpp"
#include "gc/z/zList.hpp"
#include "gc/z/zLock.hpp"
#include "gc/z/zMappedCache.hpp"
#include "gc/z/zPage.hpp"
#include "gc/z/zPageAge.hpp"
#include "gc/z/zPageType.hpp"
#include "gc/z/zPhysicalMemoryManager.hpp"
#include "gc/z/zSafeDelete.hpp"
#include "gc/z/zUncommitter.hpp"
#include "gc/z/zValue.hpp"
#include "gc/z/zVirtualMemoryManager.hpp"
#include "utilities/ostream.hpp"

class ThreadClosure;
class ZGeneration;
class ZMemoryAllocation;
class ZMultiPartitionAllocation;
class ZPageAllocation;
class ZPageAllocator;
class ZPageAllocatorStats;
class ZSegmentStash;
class ZSinglePartitionAllocation;
class ZVirtualMemory;
class ZWorkers;

class ZPartition {
  friend class VMStructs;
  friend class ZPageAllocator;
  friend class ZUncommitter;

private:
  ZPageAllocator* const _page_allocator;
  ZMappedCache          _cache;
  ZUncommitter          _uncommitter;
  const size_t          _min_capacity;
  const size_t          _max_capacity;
  volatile size_t       _current_max_capacity;
  volatile size_t       _capacity;
  volatile size_t       _claimed;
  size_t                _used;
  const uint32_t        _numa_id;

  const ZVirtualMemoryManager& virtual_memory_manager() const;
  ZVirtualMemoryManager& virtual_memory_manager();

  const ZPhysicalMemoryManager& physical_memory_manager() const;
  ZPhysicalMemoryManager& physical_memory_manager();

  void verify_virtual_memory_multi_partition_association(const ZVirtualMemory& vmem) const NOT_DEBUG_RETURN;
  void verify_virtual_memory_association(const ZVirtualMemory& vmem, bool check_multi_partition = false) const NOT_DEBUG_RETURN;
  void verify_virtual_memory_association(const ZArray<ZVirtualMemory>* vmems) const NOT_DEBUG_RETURN;
  void verify_memory_allocation_association(const ZMemoryAllocation* allocation) const NOT_DEBUG_RETURN;

public:
  ZPartition(uint32_t numa_id, ZPageAllocator* page_allocator);

  uint32_t numa_id() const;

  size_t available() const;

  size_t increase_capacity(size_t size);
  void decrease_capacity(size_t size, bool set_max_capacity);

  void increase_used(size_t size);
  void decrease_used(size_t size);

  void free_memory(const ZVirtualMemory& vmem);

  void claim_from_cache_or_increase_capacity(ZMemoryAllocation* allocation);
  bool claim_capacity(ZMemoryAllocation* allocation);
  bool claim_capacity_fast_medium(ZMemoryAllocation* allocation);

  void sort_segments_physical(const ZVirtualMemory& vmem);

  void claim_physical(const ZVirtualMemory& vmem);
  void free_physical(const ZVirtualMemory& vmem);
  size_t commit_physical(const ZVirtualMemory& vmem);
  size_t uncommit_physical(const ZVirtualMemory& vmem);

  void map_virtual(const ZVirtualMemory& vmem);
  void unmap_virtual(const ZVirtualMemory& vmem);

  void map_virtual_from_multi_partition(const ZVirtualMemory& vmem);
  void unmap_virtual_from_multi_partition(const ZVirtualMemory& vmem);

  ZVirtualMemory claim_virtual(size_t size);
  size_t claim_virtual(size_t size, ZArray<ZVirtualMemory>* vmems_out);
  void free_virtual(const ZVirtualMemory& vmem);

  void free_and_claim_virtual_from_low_many(const ZVirtualMemory& vmem, ZArray<ZVirtualMemory>* vmems_out);
  ZVirtualMemory free_and_claim_virtual_from_low_exact_or_many(size_t size, ZArray<ZVirtualMemory>* vmems_in_out);

  bool prime(ZWorkers* workers, size_t size);

  ZVirtualMemory prepare_harvested_and_claim_virtual(ZMemoryAllocation* allocation);

  void copy_physical_segments_to_partition(const ZVirtualMemory& at, const ZVirtualMemory& from);
  void copy_physical_segments_from_partition(const ZVirtualMemory& at, const ZVirtualMemory& to);

  void commit_increased_capacity(ZMemoryAllocation* allocation, const ZVirtualMemory& vmem);
  void map_memory(ZMemoryAllocation* allocation, const ZVirtualMemory& vmem);

  void free_memory_alloc_failed(ZMemoryAllocation* allocation);

  void threads_do(ThreadClosure* tc) const;

  void print_on(outputStream* st) const;
  void print_cache_on(outputStream* st) const;
  void print_cache_extended_on(outputStream* st) const;
};

using ZPartitionIterator = ZPerNUMAIterator<ZPartition>;
using ZPartitionConstIterator = ZPerNUMAConstIterator<ZPartition>;

class ZPageAllocator {
  friend class VMStructs;
  friend class ZMultiPartitionTracker;
  friend class ZPartition;
  friend class ZUncommitter;

private:
  mutable ZLock               _lock;
  ZVirtualMemoryManager       _virtual;
  ZPhysicalMemoryManager      _physical;
  const size_t                _min_capacity;
  const size_t                _max_capacity;
  volatile size_t             _used;
  volatile size_t             _used_generations[2];
  struct {
    size_t _used_high;
    size_t _used_low;
  }                           _collection_stats[2];
  ZPerNUMA<ZPartition>        _partitions;
  ZList<ZPageAllocation>      _stalled;
  mutable ZSafeDelete<ZPage>  _safe_destroy;
  bool                        _initialized;

  bool alloc_page_stall(ZPageAllocation* allocation);
  ZPage* alloc_page_inner(ZPageAllocation* allocation);

  bool claim_capacity_or_stall(ZPageAllocation* allocation);
  bool claim_capacity(ZPageAllocation* allocation);
  bool claim_capacity_fast_medium(ZPageAllocation* allocation);
  bool claim_capacity_single_partition(ZSinglePartitionAllocation* single_partition_allocation, uint32_t partition_id);
  void claim_capacity_multi_partition(ZMultiPartitionAllocation* multi_partition_allocation, uint32_t start_partition);

  ZVirtualMemory satisfied_from_cache_vmem(const ZPageAllocation* allocation) const;

  ZVirtualMemory claim_virtual_memory(ZPageAllocation* allocation);
  ZVirtualMemory claim_virtual_memory_single_partition(ZSinglePartitionAllocation* single_partition_allocation);
  ZVirtualMemory claim_virtual_memory_multi_partition(ZMultiPartitionAllocation* multi_partition_allocation);

  void copy_claimed_physical_multi_partition(ZMultiPartitionAllocation* multi_partition_allocation, const ZVirtualMemory& vmem);

  void claim_physical_for_increased_capacity(ZPageAllocation* allocation, const ZVirtualMemory& vmem);
  void claim_physical_for_increased_capacity_single_partition(ZSinglePartitionAllocation* allocation, const ZVirtualMemory& vmem);
  void claim_physical_for_increased_capacity_multi_partition(const ZMultiPartitionAllocation* multi_partition_allocation, const ZVirtualMemory& vmem);
  void claim_physical_for_increased_capacity(ZMemoryAllocation* allocation, const ZVirtualMemory& vmem);

  bool commit_and_map(ZPageAllocation* allocation, const ZVirtualMemory& vmem);
  bool commit_and_map_single_partition(ZSinglePartitionAllocation* single_partition_allocation, const ZVirtualMemory& vmem);
  bool commit_and_map_multi_partition(ZMultiPartitionAllocation* multi_partition_allocation, const ZVirtualMemory& vmem);

  void commit(ZMemoryAllocation* allocation, const ZVirtualMemory& vmem);
  bool commit_single_partition(ZSinglePartitionAllocation* single_partition_allocation, const ZVirtualMemory& vmem);
  bool commit_multi_partition(ZMultiPartitionAllocation* multi_partition_allocation, const ZVirtualMemory& vmem);

  void unmap_harvested_multi_partition(ZMultiPartitionAllocation* multi_partition_allocation);

  void map_committed_single_partition(ZSinglePartitionAllocation* single_partition_allocation, const ZVirtualMemory& vmem);
  void map_committed_multi_partition(ZMultiPartitionAllocation* multi_partition_allocation, const ZVirtualMemory& vmem);

  void cleanup_failed_commit_single_partition(ZSinglePartitionAllocation* single_partition_allocation, const ZVirtualMemory& vmem);
  void cleanup_failed_commit_multi_partition(ZMultiPartitionAllocation* multi_partition_allocation, const ZVirtualMemory& vmem);

  void free_after_alloc_page_failed(ZPageAllocation* allocation);

  void free_memory_alloc_failed(ZPageAllocation* allocation);
  void free_memory_alloc_failed_single_partition(ZSinglePartitionAllocation* single_partition_allocation);
  void free_memory_alloc_failed_multi_partition(ZMultiPartitionAllocation* multi_partition_allocation);
  void free_memory_alloc_failed(ZMemoryAllocation* allocation);

  ZPage* create_page(ZPageAllocation* allocation, const ZVirtualMemory& vmem);

  void prepare_memory_for_free(ZPage* page, ZArray<ZVirtualMemory>* vmems);
  void remap_and_defragment(const ZVirtualMemory& vmem, ZArray<ZVirtualMemory>* vmems_out);
  void free_memory(ZArray<ZVirtualMemory>* vmems);

  void satisfy_stalled();

  bool is_multi_partition_enabled() const;

  const ZPartition& partition_from_partition_id(uint32_t partition_id) const;
  ZPartition&       partition_from_partition_id(uint32_t partition_id);
  ZPartition&       partition_from_vmem(const ZVirtualMemory& vmem);

  size_t sum_available() const;

  void increase_used(size_t size);
  void decrease_used(size_t size);

  void notify_out_of_memory();
  void restart_gc() const;

  void update_collection_stats(ZGenerationId id);
  ZPageAllocatorStats stats_inner(ZGeneration* generation) const;

public:
  ZPageAllocator(size_t min_capacity,
                 size_t initial_capacity,
                 size_t soft_max_capacity,
                 size_t max_capacity);

  bool is_initialized() const;

  bool prime_cache(ZWorkers* workers, size_t size);

  size_t min_capacity() const;
  size_t max_capacity() const;
  size_t soft_max_capacity() const;
  size_t current_max_capacity() const;
  size_t capacity() const;
  size_t used() const;
  size_t used_generation(ZGenerationId id) const;
  size_t unused() const;

  void increase_used_generation(ZGenerationId id, size_t size);
  void decrease_used_generation(ZGenerationId id, size_t size);

  void promote_used(const ZPage* from, const ZPage* to);

  ZPageAllocatorStats stats(ZGeneration* generation) const;
  ZPageAllocatorStats update_and_stats(ZGeneration* generation);

  ZPage* alloc_page(ZPageType type, size_t size, ZAllocationFlags flags, ZPageAge age);
  void safe_destroy_page(ZPage* page);
  void free_page(ZPage* page);
  void free_pages(ZGenerationId id, const ZArray<ZPage*>* pages);

  void enable_safe_destroy() const;
  void disable_safe_destroy() const;

  bool is_alloc_stalling() const;
  bool is_alloc_stalling_for_old() const;
  void handle_alloc_stalling_for_young();
  void handle_alloc_stalling_for_old(bool cleared_soft_refs);

  ZPartitionConstIterator partition_iterator() const;
  ZPartitionIterator partition_iterator();

  void threads_do(ThreadClosure* tc) const;

  void print_usage_on(outputStream* st) const;
  void print_total_usage_on(outputStream* st) const;
  void print_partition_usage_on(outputStream* st) const;
  void print_cache_extended_on(outputStream* st) const;
};

class ZPageAllocatorStats {
private:
  const size_t _min_capacity;
  const size_t _max_capacity;
  const size_t _soft_max_capacity;
  const size_t _capacity;
  const size_t _used;
  const size_t _used_high;
  const size_t _used_low;
  const size_t _used_generation;
  const size_t _freed;
  const size_t _promoted;
  const size_t _compacted;
  const size_t _allocation_stalls;

public:
  ZPageAllocatorStats(size_t min_capacity,
                      size_t max_capacity,
                      size_t soft_max_capacity,
                      size_t capacity,
                      size_t used,
                      size_t used_high,
                      size_t used_low,
                      size_t used_generation,
                      size_t freed,
                      size_t promoted,
                      size_t compacted,
                      size_t allocation_stalls);

  size_t min_capacity() const;
  size_t max_capacity() const;
  size_t soft_max_capacity() const;
  size_t capacity() const;
  size_t used() const;
  size_t used_high() const;
  size_t used_low() const;
  size_t used_generation() const;
  size_t freed() const;
  size_t promoted() const;
  size_t compacted() const;
  size_t allocation_stalls() const;
};

#endif // SHARE_GC_Z_ZPAGEALLOCATOR_HPP
