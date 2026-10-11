/*
 * Copyright (c) 2015, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/shared/gc_globals.hpp"
#include "gc/shared/gcLogPrecious.hpp"
#include "gc/shared/suspendibleThreadSet.hpp"
#include "gc/z/zAdaptiveHeap.inline.hpp"
#include "gc/z/zAddress.hpp"
#include "gc/z/zAllocationFlags.hpp"
#include "gc/z/zArray.inline.hpp"
#include "gc/z/zDriver.hpp"
#include "gc/z/zFuture.inline.hpp"
#include "gc/z/zGeneration.inline.hpp"
#include "gc/z/zGenerationId.hpp"
#include "gc/z/zGlobals.hpp"
#include "gc/z/zLargePages.inline.hpp"
#include "gc/z/zLock.inline.hpp"
#include "gc/z/zMappedCache.inline.hpp"
#include "gc/z/zMemoryWorker.hpp"
#include "gc/z/zNUMA.inline.hpp"
#include "gc/z/zPage.inline.hpp"
#include "gc/z/zPageAge.hpp"
#include "gc/z/zPageAllocator.inline.hpp"
#include "gc/z/zPageType.hpp"
#include "gc/z/zPhysicalMemoryManager.hpp"
#include "gc/z/zSafeDelete.inline.hpp"
#include "gc/z/zStat.hpp"
#include "gc/z/zTask.hpp"
#include "gc/z/zUncommitter.hpp"
#include "gc/z/zValue.inline.hpp"
#include "gc/z/zVirtualMemory.inline.hpp"
#include "gc/z/zVirtualMemoryManager.inline.hpp"
#include "gc/z/zWorkers.hpp"
#include "jfr/jfrEvents.hpp"
#include "logging/log.hpp"
#include "memory/allocation.hpp"
#include "nmt/memTag.hpp"
#include "runtime/globals.hpp"
#include "runtime/init.hpp"
#include "runtime/java.hpp"
#include "runtime/os.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/powerOfTwo.hpp"
#include "utilities/ticks.hpp"
#include "utilities/vmError.hpp"

#include <cmath>

class ZMemoryAllocation;

static const ZStatCounter       ZCounterMutatorAllocationRate("Memory", "Allocation Rate", ZStatUnitBytesPerSecond);
static const ZStatCounter       ZCounterMappedCacheHarvest("Memory", "Mapped Cache Harvest", ZStatUnitBytesPerSecond);
static const ZStatCounter       ZCounterDefragment("Memory", "Defragment", ZStatUnitOpsPerSecond);
static const ZStatCriticalPhase ZCriticalPhaseAllocationStall("Allocation Stall");

static void check_numa_mismatch(const ZVirtualMemory& vmem, uint32_t desired_id) {
  if (ZNUMA::is_enabled()) {
    // Check if memory ended up on desired NUMA node or not
    const uint32_t actual_id = ZNUMA::memory_id(untype(ZOffset::address(vmem.start())));
    if (actual_id != desired_id) {
      log_debug(gc, heap)("NUMA Mismatch: desired %d, actual %d", desired_id, actual_id);
    }
  }
}

enum class ZPageAllocationAttempt {
  initial,
  retry,
  stall,
};

class ZMemoryAllocation : public CHeapObj<mtGC> {
private:
  const size_t           _size;
  ZPartition*            _partition;
  ZVirtualMemory         _satisfied_from_cache_vmem;
  ZArray<ZVirtualMemory> _partial_vmems;
  int                    _num_harvested;
  size_t                 _harvested;
  size_t                 _increased_capacity;
  size_t                 _committed_capacity;
  bool                   _has_set_committed_capacity;

  explicit ZMemoryAllocation(const ZMemoryAllocation& other)
    : ZMemoryAllocation(other._size) {
    // Transfer the partition
    set_partition(other._partition);

    // Reserve space for the partial vmems
    _partial_vmems.reserve(other._partial_vmems.length() + (other._satisfied_from_cache_vmem.is_null() ? 1 : 0));

    // Transfer the claimed capacity
    transfer_claimed_capacity(other);
  }

  ZMemoryAllocation(const ZMemoryAllocation& a1, const ZMemoryAllocation& a2)
    : ZMemoryAllocation(a1._size + a2._size) {
    // Transfer the partition
    assert(a1._partition == a2._partition, "only merge with same partition");
    set_partition(a1._partition);

    // Reserve space for the partial vmems
    const int num_vmems_a1 = a1._partial_vmems.length() + (a1._satisfied_from_cache_vmem.is_null() ? 1 : 0);
    const int num_vmems_a2 = a2._partial_vmems.length() + (a2._satisfied_from_cache_vmem.is_null() ? 1 : 0);
    _partial_vmems.reserve(num_vmems_a1 + num_vmems_a2);

    // Transfer the claimed capacity
    transfer_claimed_capacity(a1);
    transfer_claimed_capacity(a2);
  }

  void transfer_claimed_capacity(const ZMemoryAllocation& from) {
    assert(from._committed_capacity == 0, "Unexpected value %zu", from._committed_capacity);
    assert(!_has_set_committed_capacity, "Should not have set committed capacity yet");

    // Transfer increased capacity
    _increased_capacity += from._increased_capacity;

    // Transfer satisfying vmem or partial mappings
    const ZVirtualMemory vmem = from._satisfied_from_cache_vmem;
    if (!vmem.is_null()) {
      assert(_partial_vmems.is_empty(), "Must either have result or partial vmems");
      _partial_vmems.push(vmem);
      _num_harvested += 1;
      _harvested += vmem.size();
    } else {
      _partial_vmems.appendAll(&from._partial_vmems);
      _num_harvested += from._num_harvested;
      _harvested += from._harvested;
    }
  }

public:
  explicit ZMemoryAllocation(size_t size)
    : _size(size),
      _partition(nullptr),
      _satisfied_from_cache_vmem(),
      _partial_vmems(0),
      _num_harvested(0),
      _harvested(0),
      _increased_capacity(0),
      _committed_capacity(0),
      _has_set_committed_capacity(false) {}

  void reset_for_retry() {
    assert(_satisfied_from_cache_vmem.is_null(), "Incompatible with reset");

    _partition = nullptr;
    _partial_vmems.clear();
    _num_harvested = 0;
    _harvested = 0;
    _increased_capacity = 0;
    _committed_capacity = 0;
    _has_set_committed_capacity = false;
  }

  size_t size() const {
    return _size;
  }

  ZPartition& partition() const {
    assert(_partition != nullptr, "Should have been initialized");
    return *_partition;
  }

  void set_partition(ZPartition* partition) {
    assert(_partition == nullptr, "Should be initialized only once");
    _partition = partition;
  }

  ZVirtualMemory satisfied_from_cache_vmem() const {
    return _satisfied_from_cache_vmem;
  }

  void set_satisfied_from_cache_vmem_fast_medium(ZVirtualMemory vmem) {
    precond(_satisfied_from_cache_vmem.is_null());
    precond(_partial_vmems.is_empty());
    precond(ZPageSizeMediumEnabled);
    precond(vmem.size() >= ZPageSizeMediumMin);
    precond(vmem.size() <= ZPageSizeMediumMax);
    precond(is_power_of_2(vmem.size()));

    _satisfied_from_cache_vmem = vmem;
  }

  void set_satisfied_from_cache_vmem(ZVirtualMemory vmem) {
    precond(_satisfied_from_cache_vmem.is_null());
    precond(vmem.size() == size());
    precond(_partial_vmems.is_empty());

    _satisfied_from_cache_vmem = vmem;
  }

  ZArray<ZVirtualMemory>* partial_vmems() {
    return &_partial_vmems;
  }

  const ZArray<ZVirtualMemory>* partial_vmems() const {
    return &_partial_vmems;
  }

  int num_harvested() const {
    return _num_harvested;
  }

  size_t harvested() const {
    return _harvested;
  }

  void set_harvested(int num_harvested, size_t harvested) {
    _num_harvested = num_harvested;
    _harvested = harvested;
  }

  size_t increased_capacity() const {
    return _increased_capacity;
  }

  void set_increased_capacity(size_t increased_capacity) {
    _increased_capacity = increased_capacity;
  }

  size_t committed_capacity() const {
    return _committed_capacity;
  }

  void set_committed_capacity(size_t committed_capacity) {
    precond(!_has_set_committed_capacity);
    _committed_capacity = committed_capacity;
    _has_set_committed_capacity = true;
  }

  bool commit_failed() const {
    precond(_has_set_committed_capacity);
    return _committed_capacity != _increased_capacity;
  }

  static void destroy(ZMemoryAllocation* allocation) {
    delete allocation;
  }

  static void merge(const ZMemoryAllocation& allocation, ZMemoryAllocation** merge_location) {
    ZMemoryAllocation* const other_allocation = *merge_location;
    if (other_allocation == nullptr) {
      // First allocation, allocate new partition
      *merge_location = new ZMemoryAllocation(allocation);
    } else {
      // Merge with other allocation
      *merge_location = new ZMemoryAllocation(allocation, *other_allocation);

      // Delete old allocation
      delete other_allocation;
    }
  }
};

class ZSinglePartitionAllocation {
private:
  ZMemoryAllocation _allocation;

public:
  ZSinglePartitionAllocation(size_t size)
    : _allocation(size) {}

  size_t size() const {
    return _allocation.size();
  }

  ZMemoryAllocation* allocation() {
    return &_allocation;
  }

  const ZMemoryAllocation* allocation() const {
    return &_allocation;
  }

  void reset_for_retry() {
    _allocation.reset_for_retry();
  }
};

class ZMultiPartitionAllocation : public StackObj {
private:
  const size_t               _size;
  ZArray<ZMemoryAllocation*> _allocations;

public:
  ZMultiPartitionAllocation(size_t size)
    : _size(size),
      _allocations(0) {}

  ~ZMultiPartitionAllocation() {
    for (ZMemoryAllocation* allocation : _allocations) {
      ZMemoryAllocation::destroy(allocation);
    }
  }

  void initialize() {
    precond(_allocations.is_empty());

    // The multi-partition allocation creates at most one allocation per partition.
    const int length = (int)ZNUMA::count();

    _allocations.reserve(length);
  }

  void reset_for_retry() {
    for (ZMemoryAllocation* allocation : _allocations) {
      ZMemoryAllocation::destroy(allocation);
    }
    _allocations.clear();
  }

  size_t size() const {
    return _size;
  }

  ZArray<ZMemoryAllocation*>* allocations() {
    return &_allocations;
  }

  const ZArray<ZMemoryAllocation*>* allocations() const {
    return &_allocations;
  }

  void register_allocation(const ZMemoryAllocation& allocation) {
    ZMemoryAllocation** const slot = allocation_slot(allocation.partition().numa_id());

    ZMemoryAllocation::merge(allocation, slot);
  }

  ZMemoryAllocation** allocation_slot(uint32_t numa_id) {
    // Try to find an existing allocation for numa_id
    for (int i = 0; i < _allocations.length(); ++i) {
      ZMemoryAllocation** const slot_addr = _allocations.adr_at(i);
      ZMemoryAllocation* const allocation = *slot_addr;
      if (allocation->partition().numa_id() == numa_id) {
        // Found an existing slot
        return slot_addr;
      }
    }

    // Push an empty slot for the numa_id
    _allocations.push(nullptr);

    // Return the address of the slot
    return &_allocations.last();
  }

  int sum_num_harvested_vmems() const {
    int total = 0;

    for (const ZMemoryAllocation* allocation : _allocations) {
      total += allocation->num_harvested();
    }

    return total;
  }

  size_t sum_harvested() const {
    size_t total = 0;

    for (const ZMemoryAllocation* allocation : _allocations) {
      total += allocation->harvested();
    }

    return total;
  }

  size_t sum_committed_increased_capacity() const {
    size_t total = 0;

    for (const ZMemoryAllocation* allocation : _allocations) {
      total += allocation->committed_capacity();
    }

    return total;
  }
};

struct ZPageAllocationStats {
  int    _num_harvested_vmems;
  size_t _total_harvested;
  size_t _total_committed_capacity;

  ZPageAllocationStats(int num_harvested_vmems, size_t total_harvested, size_t total_committed_capacity)
    : _num_harvested_vmems(num_harvested_vmems),
      _total_harvested(total_harvested),
      _total_committed_capacity(total_committed_capacity) {}
};

class ZPageAllocation : public StackObj {
  friend class ZList<ZPageAllocation>;

private:
  const ZPageType            _type;
  const size_t               _requested_size;
  const ZAllocationFlags     _flags;
  const ZPageAge             _age;
  const Ticks                _start_timestamp;
  const uint32_t             _young_seqnum;
  const uint32_t             _old_seqnum;
  const uint32_t             _preferred_partition;
  bool                       _is_multi_partition;
  ZSinglePartitionAllocation _single_partition_allocation;
  ZMultiPartitionAllocation  _multi_partition_allocation;
  ZListNode<ZPageAllocation> _node;
  ZFuture<bool>              _stall_result;

public:
  ZPageAllocation(ZPageType type, size_t size, ZAllocationFlags flags, ZPageAge age, uint32_t preferred_partition)
    : _type(type),
      _requested_size(size),
      _flags(flags),
      _age(age),
      _start_timestamp(Ticks::now()),
      _young_seqnum(ZGeneration::young()->seqnum()),
      _old_seqnum(ZGeneration::old()->seqnum()),
      _preferred_partition(preferred_partition),
      _is_multi_partition(false),
      _single_partition_allocation(size),
      _multi_partition_allocation(size),
      _node(),
      _stall_result() {
    assert(_preferred_partition < ZNUMA::count(), "Preferred partition out-of-bounds (0 <= %d < %d)", _preferred_partition, ZNUMA::count());
  }

  void reset_for_retry() {
    _is_multi_partition = false;
    _single_partition_allocation.reset_for_retry();
    _multi_partition_allocation.reset_for_retry();
  }

  ZPageType type() const {
    return _type;
  }

  size_t size() const {
    if (_flags.fast_medium()) {
      // A fast medium allocation may have allocated less than the _size field
      const ZVirtualMemory vmem = _single_partition_allocation.allocation()->satisfied_from_cache_vmem();
      if (!vmem.is_null()) {
        // The allocation has been satisfied, return the satisfied size.
        return vmem.size();
      }
    }

    return _requested_size;
  }

  ZAllocationFlags flags() const {
    return _flags;
  }

  ZPageAge age() const {
    return _age;
  }

  uint32_t young_seqnum() const {
    return _young_seqnum;
  }

  uint32_t old_seqnum() const {
    return _old_seqnum;
  }

  uint32_t preferred_partition() const {
    return _preferred_partition;
  }

  bool is_multi_partition() const {
    return _is_multi_partition;
  }

  void initiate_multi_partition_allocation() {
    assert(!_is_multi_partition, "Reinitialization?");
    _is_multi_partition = true;
    _multi_partition_allocation.initialize();
  }

  ZMultiPartitionAllocation* multi_partition_allocation() {
    assert(_is_multi_partition, "multi-partition allocation must be initiated");

    return &_multi_partition_allocation;
  }

  const ZMultiPartitionAllocation* multi_partition_allocation() const {
    assert(_is_multi_partition, "multi-partition allocation must be initiated");

    return &_multi_partition_allocation;
  }

  ZSinglePartitionAllocation* single_partition_allocation() {
    assert(!_is_multi_partition, "multi-partition allocation must not have been initiated");

    return &_single_partition_allocation;
  }

  const ZSinglePartitionAllocation* single_partition_allocation() const {
    assert(!_is_multi_partition, "multi-partition allocation must not have been initiated");

    return &_single_partition_allocation;
  }

  ZVirtualMemory satisfied_from_cache_vmem() const {
    precond(!_is_multi_partition);

    const ZMemoryAllocation* const allocation = _single_partition_allocation.allocation();

    return allocation->satisfied_from_cache_vmem();
  }

  bool wait() {
    return _stall_result.get();
  }

  void satisfy(bool result) {
    _stall_result.set(result);
  }

  bool gc_relocation() const {
    return _flags.gc_relocation();
  }

  ZPageAllocationStats stats() const {
    if (_is_multi_partition) {
      return ZPageAllocationStats(
          _multi_partition_allocation.sum_num_harvested_vmems(),
          _multi_partition_allocation.sum_harvested(),
          _multi_partition_allocation.sum_committed_increased_capacity());
    } else {
      return ZPageAllocationStats(
          _single_partition_allocation.allocation()->num_harvested(),
          _single_partition_allocation.allocation()->harvested(),
          _single_partition_allocation.allocation()->committed_capacity());
    }
  }

  void send_event(bool successful) {
    if (!EventZPageAllocation::is_enabled()) {
      // Event not enabled, exit early
      return;
    }

    Ticks end_timestamp = Ticks::now();
    const ZPageAllocationStats st = stats();

    EventZPageAllocation::commit(_start_timestamp,
                                 end_timestamp,
                                 (u8)_type,
                                 size(),
                                 st._total_harvested,
                                 st._total_committed_capacity,
                                 (unsigned)st._num_harvested_vmems,
                                 _is_multi_partition,
                                 successful,
                                 _flags.non_blocking());
  }
};

const ZVirtualMemoryManager& ZPartition::virtual_memory_manager() const {
  return _page_allocator->_virtual;
}

ZVirtualMemoryManager& ZPartition::virtual_memory_manager() {
  return _page_allocator->_virtual;
}

const ZPhysicalMemoryManager& ZPartition::physical_memory_manager() const {
  return _page_allocator->_physical;
}

ZPhysicalMemoryManager& ZPartition::physical_memory_manager() {
  return _page_allocator->_physical;
}

ZLock* ZPartition::lock() const {
  return &_page_allocator->_lock;
}

#ifdef ASSERT

void ZPartition::verify_virtual_memory_multi_partition_association(const ZVirtualMemory& vmem) const {
  const ZVirtualMemoryManager& manager = virtual_memory_manager();

  assert(manager.is_in_multi_partition(vmem),
         "Virtual memory must be associated with the extra space "
         "actual: %u", virtual_memory_manager().lookup_partition_id(vmem));
}

void ZPartition::verify_virtual_memory_association(const ZVirtualMemory& vmem, bool check_multi_partition) const {
  const ZVirtualMemoryManager& manager = virtual_memory_manager();

  if (check_multi_partition && manager.is_in_multi_partition(vmem)) {
    // We allow claim/free/commit physical operation in multi-partition allocations
    // to use virtual memory associated with the extra space.
    return;
  }

  const uint32_t vmem_numa_id = virtual_memory_manager().lookup_partition_id(vmem);
  assert(_numa_id == vmem_numa_id,
         "Virtual memory must be associated with the current partition "
         "expected: %u, actual: %u", _numa_id, vmem_numa_id);
}

void ZPartition::verify_virtual_memory_association(const ZArray<ZVirtualMemory>* vmems) const {
  for (const ZVirtualMemory& vmem : *vmems) {
    verify_virtual_memory_association(vmem);
  }
}

void ZPartition::verify_memory_allocation_association(const ZMemoryAllocation* allocation) const {
  assert(this == &allocation->partition(),
         "Memory allocation must be associated with the current partition "
         "expected: %u, actual: %u", _numa_id, allocation->partition().numa_id());
}

#endif // ASSERT

ZPartition::ZPartition(uint32_t numa_id,
                       ZPageAllocator* page_allocator,
                       size_t static_min_capacity,
                       size_t static_max_capacity)
  : _page_allocator(page_allocator),
    _cache(),
    _uncommitter(numa_id, this),
    _mem_worker(numa_id, this),
    _static_min_capacity(ZNUMA::calculate_share(numa_id, static_min_capacity)),
    _static_max_capacity(ZNUMA::calculate_share(numa_id, static_max_capacity)),
    _capacity(0),
    _claimed(0),
    _used(0),
    _numa_id(numa_id) {}

size_t ZPartition::dynamic_max_capacity() const {
  return ZNUMA::calculate_share(_numa_id, _page_allocator->dynamic_max_capacity());
}

size_t ZPartition::current_max_capacity() const {
  return ZNUMA::calculate_share(_numa_id, _page_allocator->current_max_capacity());
}

size_t ZPartition::static_max_capacity() const {
  return _static_max_capacity;
}

size_t ZPartition::capacity() const {
  return AtomicAccess::load(&_capacity);
}

const ZUncommitter& ZPartition::uncommitter() const {
  return _uncommitter;
}

ZUncommitter& ZPartition::uncommitter() {
  return _uncommitter;
}

const ZMemoryWorker& ZPartition::memory_worker() const {
  return _mem_worker;
}

ZMemoryWorker& ZPartition::memory_worker() {
  return _mem_worker;
}

uint32_t ZPartition::numa_id() const {
  return _numa_id;
}

size_t ZPartition::available(ZPageAllocationAttempt attempt, size_t capacity_limit) const {
  assert(_capacity == _used + _claimed + _cache.size(), "Should be consistent"
         " _capacity: %zx _used: %zx _claimed: %zx _cache.size(): %zx",
         _capacity, _used, _claimed, _cache.size());

  if (attempt == ZPageAllocationAttempt::initial) {
    return available_from_any_capacity(capacity_limit);
  }

  if (attempt == ZPageAllocationAttempt::retry || attempt == ZPageAllocationAttempt::stall) {
    return available_from_cache(capacity_limit);
  }

  ShouldNotReachHere();
}

size_t ZPartition::available_from_any_capacity(size_t capacity_limit) const {
  assert(_capacity == _used + _claimed + _cache.size(), "Should be consistent"
         " _capacity: %zx _used: %zx _claimed: %zx _cache.size(): %zx",
         _capacity, _used, _claimed, _cache.size());
  assert(capacity_limit <= _static_max_capacity, "Invalid capacity limit for partition: %zx > %zx", capacity_limit, _static_max_capacity);
  const size_t unavailable = _used + _claimed;

  if (capacity_limit < unavailable) {
    // The current max capacity may be below what is handed out
    return 0;
  }

  return capacity_limit - unavailable;
}

size_t ZPartition::available_from_increase_capacity(size_t capacity_limit) const {
  precond(capacity_limit >= _capacity);
  const size_t available = ZPartition::available_from_any_capacity(capacity_limit);
  const size_t cached = _cache.size();

  return available - cached;
}

size_t ZPartition::available_from_cache(size_t capacity_limit) const {
  const size_t available = ZPartition::available_from_any_capacity(capacity_limit);
  const size_t cached = _cache.size();

  // The current allowed available may be below what is in the cache
  return MIN2(available, cached);
}

size_t ZPartition::try_increase_capacity(size_t size, ZPageAllocationAttempt attempt, size_t capacity_limit) {
  if (attempt == ZPageAllocationAttempt::initial) {
    // We should only increase the capacity if the capacity limit is greater
    // than the already committed memory, i.e., the capacity.
    return (capacity_limit > _capacity)
        ? increase_capacity(size, capacity_limit)
        : 0;
  }

  if (attempt == ZPageAllocationAttempt::retry || attempt == ZPageAllocationAttempt::stall) {
    return 0;
  }

  ShouldNotReachHere();
}

size_t ZPartition::increase_capacity(size_t size, size_t capacity_limit) {
  assert(capacity_limit > _capacity, "Cannot increase capacity. capacity limit: %zu, capacity: %zu", capacity_limit, _capacity);

  const size_t available = available_from_increase_capacity(capacity_limit);
  const size_t increased = MIN2(size, available);

  if (increased > 0) {
    // Update atomically since we have concurrent readers
    AtomicAccess::add(&_capacity, increased);

    _uncommitter.cancel_uncommit_cycle();
  }

  return increased;
}

void ZPartition::decrease_capacity(size_t size) {
  // Update capacity atomically since we have concurrent readers
  AtomicAccess::sub(&_capacity, size);
}

void ZPartition::increase_used(size_t size) {
  // The partition usage tracking is only read and updated under the page
  // allocator lock. Usage statistics for generations and GC cycles are
  // collected on the ZPageAllocator level.
  _used += size;
}

void ZPartition::decrease_used(size_t size) {
  // The partition usage tracking is only read and updated under the page
  // allocator lock. Usage statistics for generations and GC cycles are
  // collected on the ZPageAllocator level.
  _used -= size;
}

void ZPartition::increase_claimed(size_t size) {
  // Update claimed atomically since we have concurrent readers
  AtomicAccess::add(&_claimed, size);
}

void ZPartition::decrease_claimed(size_t size) {
  // Update claimed atomically since we have concurrent readers
  AtomicAccess::sub(&_claimed, size);
}

static void pretouch_memory(zoffset start, size_t size) {
  // At this point we know that we have a valid zoffset / zaddress.
  const zaddress zaddr = ZOffset::address(start);
  const uintptr_t addr = untype(zaddr);
  const size_t page_size = ZLargePages::is_explicit() ? ZGranuleSize : os::vm_page_size();
  os::pretouch_memory((void*)addr, (void*)(addr + size), page_size);
}

void ZPartition::heat_memory(const ZVirtualMemory& vmem) const {
  verify_virtual_memory_association(vmem, true /* check_multi_partition */);

  const ZPhysicalMemoryManager& manager = physical_memory_manager();

  pretouch_memory(vmem.start(), vmem.size());
  if (ZLargePages::is_collapse()) {
    manager.collapse(vmem);
  }
}

void ZPartition::free_used_memory(const ZVirtualMemory& vmem) {
  const size_t size = vmem.size();

  // Cache the vmem
  _cache.insert(vmem);

  // Update accounting
  decrease_used(size);
}

void ZPartition::free_claimed_memory(const ZVirtualMemory& vmem) {
  const size_t size = vmem.size();

  // Cache the vmem
  _cache.insert(vmem);

  // Update accounting
  decrease_claimed(size);
}

void ZPartition::claim_from_cache_or_increase_capacity(ZMemoryAllocation* allocation, ZPageAllocationAttempt attempt, size_t capacity_limit) {
  const size_t size = allocation->size();
  ZArray<ZVirtualMemory>* const out = allocation->partial_vmems();

  // We are guaranteed to succeed the claiming of capacity here
  assert(available(attempt, capacity_limit) >= size, "Must be");

  // Associate the allocation with this partition.
  allocation->set_partition(this);

  // Try to allocate one contiguous vmem
  ZVirtualMemory vmem = _cache.remove_contiguous(size);
  if (!vmem.is_null()) {
    // Found a satisfying vmem in the cache
    allocation->set_satisfied_from_cache_vmem(vmem);

    // Done
    return;
  }

  // Try increase capacity
  const size_t increased_capacity = try_increase_capacity(size, attempt, capacity_limit);

  allocation->set_increased_capacity(increased_capacity);

  if (increased_capacity == size) {
    // Capacity increase covered the entire request, done.
    return;
  }

  // Could not increase capacity enough to satisfy the allocation completely.
  // Try removing multiple vmems from the mapped cache.
  const size_t remaining = size - increased_capacity;
  const size_t harvested = _cache.remove_discontiguous(remaining, out);
  const int num_harvested = out->length();

  allocation->set_harvested(num_harvested, harvested);

  assert(harvested + increased_capacity == size,
         "Mismatch harvested: %zu increased_capacity: %zu size: %zu",
         harvested / M, increased_capacity / M, size / M);

  return;
}

bool ZPartition::claim_capacity(ZMemoryAllocation* allocation, ZPageAllocationAttempt attempt, size_t capacity_limit) {
  const size_t size = allocation->size();

  if (available(attempt, capacity_limit) < size) {
    // Out of memory
    return false;
  }

  claim_from_cache_or_increase_capacity(allocation, attempt, capacity_limit);

  // Updated used statistics
  increase_used(size);

  // Success
  return true;
}

bool ZPartition::claim_capacity_fast_medium(ZMemoryAllocation* allocation, size_t capacity_limit) {
  precond(ZPageSizeMediumEnabled);

  // Try to allocate a medium page sized contiguous vmem
  const size_t available_from_cache_limit = available_from_cache(capacity_limit);
  const size_t power_of_2_limit = available_from_cache_limit == 0
      ? 0
      : round_down_power_of_2(available_from_cache_limit);
  const size_t min_size = ZPageSizeMediumMin;

  if (power_of_2_limit < min_size) {
    // No medium allocation size available within the capacity limit
    return false;
  }

  const size_t max_size = MIN2(ZStressFastMediumPageAllocation ? min_size : ZPageSizeMediumMax, power_of_2_limit);
  ZVirtualMemory vmem = _cache.remove_contiguous_power_of_2(min_size, max_size);

  if (vmem.is_null()) {
    // Failed to find a contiguous vmem
    return false;
  }

  // Found a satisfying vmem in the cache
  allocation->set_satisfied_from_cache_vmem_fast_medium(vmem);

  // Associate the allocation with this partition
  allocation->set_partition(this);

  // Updated used statistics
  increase_used(vmem.size());

  // Success
  return true;
}

size_t ZPartition::increase_and_commit_capacity(size_t size, size_t capacity_limit) {
  assert(Thread::current()->is_ConcurrentGC_thread(), "Should only be called by concurrent GC threads");

  size_t commit_size;
  {
    ZLocker<ZLock> locker(lock());

    // We are not increasing capacity, so exit early
    if (capacity_limit <= _capacity) {
      return 0;
    }

    commit_size = increase_capacity(size, capacity_limit);

    if (commit_size == 0) {
      return 0;
    }

    // We account for memory that is about to be committed as claimed
    increase_claimed(commit_size);
  }

  ZArray<ZVirtualMemory> vmems;

  const size_t claimed_virtual = claim_virtual(commit_size, &vmems);

  const bool depleted_virtual = claimed_virtual != commit_size;

  size_t total_committed = 0;
  bool commit_failed = depleted_virtual;
  ZArray<ZVirtualMemory> to_free(vmems.length());
  for (ZVirtualMemory vmem : vmems) {
    if (commit_failed) {
      // If a commit has failed free any remaining vmems
      free_virtual(vmem);
      continue;
    }

    // Claim physical
    claim_physical(vmem);

    // Commit memory
    const size_t committed = commit_physical(vmem);

    // Keep track of total committed
    total_committed += committed;

    if (committed != vmem.size()) {
      commit_failed = true;
      const ZVirtualMemory not_committed_vmem = vmem.shrink_from_back(vmem.size() - committed);
      free_physical(not_committed_vmem);
      free_virtual(not_committed_vmem);
    }

    if (vmem.size() != 0) {
      // Map memory. Since this function is called from the ZMemoryWorker,
      // we heat memory synchronously instead of sending a heating request.
      map_virtual(vmem, false /* heat */);

      heat_memory(vmem);

      to_free.push(vmem);
    }
  }

  if (commit_failed) {
    // A commit has failed

    ZLocker<ZLock> locker(lock());
    const size_t not_committed = commit_size - total_committed;
    decrease_capacity(not_committed);
    decrease_claimed(not_committed);
    _page_allocator->truncate_heuristic_max_after_capacity_decrease(ZPageAllocator::TruncationReason::CommitFailure);
  }

  // Free the memory, which puts it into the cache
  _page_allocator->free_claimed_memory(&to_free);

  return total_committed;
}

void ZPartition::sort_segments_physical(const ZVirtualMemory& vmem) {
  verify_virtual_memory_association(vmem, true /* check_multi_partition */);

  ZPhysicalMemoryManager& manager = physical_memory_manager();

  // Sort physical segments
  manager.sort_segments_physical(vmem);
}

void ZPartition::claim_physical(const ZVirtualMemory& vmem) {
  verify_virtual_memory_association(vmem, true /* check_multi_partition */);

  ZPhysicalMemoryManager& manager = physical_memory_manager();

  // Alloc physical memory
  manager.alloc(vmem, _numa_id);
}

void ZPartition::free_physical(const ZVirtualMemory& vmem) {
  verify_virtual_memory_association(vmem, true /* check_multi_partition */);

  ZPhysicalMemoryManager& manager = physical_memory_manager();

  // Free physical memory
  manager.free(vmem, _numa_id);
}

size_t ZPartition::commit_physical(const ZVirtualMemory& vmem) {
  verify_virtual_memory_association(vmem, true /* check_multi_partition */);

  ZPhysicalMemoryManager& manager = physical_memory_manager();

  // Commit physical memory
  const size_t committed =  manager.commit(vmem, _numa_id);

  return committed;
}

size_t ZPartition::uncommit_physical(const ZVirtualMemory& vmem) {
  assert(ZUncommit, "should not uncommit when uncommit is disabled");
  verify_virtual_memory_association(vmem);

  ZPhysicalMemoryManager& manager = physical_memory_manager();

  // Uncommit physical memory
  const size_t uncommitted = manager.uncommit(vmem);

  return uncommitted;
}

void ZPartition::map_virtual(const ZVirtualMemory& vmem, bool heat_memory) {
  map_virtual_inner(vmem, false /* multi_partition */, false /* sort_segments */, heat_memory);
}

void ZPartition::unmap_virtual(const ZVirtualMemory& vmem) {
  unmap_virtual_inner(vmem, false /* multi_partition */);
}

void ZPartition::map_virtual_from_multi_partition(const ZVirtualMemory& vmem) {
  map_virtual_inner(vmem, true /* multi_partition */, true /* sort_segments */, true /* register_heating */);
}

void ZPartition::unmap_virtual_from_multi_partition(const ZVirtualMemory& vmem) {
  unmap_virtual_inner(vmem, true /* multi_partition */);
}

void ZPartition::map_virtual_inner(const ZVirtualMemory& vmem, bool multi_partition, bool sort_segments, bool register_heating) {
  if (multi_partition) {
    verify_virtual_memory_multi_partition_association(vmem);
  } else {
    verify_virtual_memory_association(vmem);
  }

  ZPhysicalMemoryManager& manager = physical_memory_manager();

  if (sort_segments) {
    // Sort physical segments before mapping multi-partition memory.
    manager.sort_segments_physical(vmem);
  }

  // Map virtual memory to physical memory.
  manager.map(vmem, _numa_id);

  if (register_heating && ZMemoryHeating) {
    // Register a heating request for this mapping.
    _mem_worker.register_heating_request(vmem);
  }
}

void ZPartition::unmap_virtual_inner(const ZVirtualMemory& vmem, bool multi_partition) {
  if (multi_partition) {
    verify_virtual_memory_multi_partition_association(vmem);
  } else {
    verify_virtual_memory_association(vmem);
  }

  if (ZMemoryHeating) {
    // Remove any heating request before unmapping.
    _mem_worker.remove_heating_request(vmem);
  }

  ZPhysicalMemoryManager& manager = physical_memory_manager();

  // Unmap virtual memory from physical memory.
  manager.unmap(vmem);
}

ZVirtualMemory ZPartition::claim_virtual(size_t size) {
  ZVirtualMemoryManager& manager = virtual_memory_manager();

  return manager.remove_from_low(size, _numa_id);
}

size_t ZPartition::claim_virtual(size_t size, ZArray<ZVirtualMemory>* vmems_out) {
  ZVirtualMemoryManager& manager = virtual_memory_manager();

  return manager.remove_from_low_many_at_most(size, _numa_id, vmems_out);
}

void ZPartition::free_virtual(const ZVirtualMemory& vmem) {
  verify_virtual_memory_association(vmem);

  ZVirtualMemoryManager& manager = virtual_memory_manager();

  // Free virtual memory
  manager.insert(vmem, _numa_id);
}

void ZPartition::free_and_claim_virtual_from_low_many(const ZVirtualMemory& vmem, ZArray<ZVirtualMemory>* vmems_out) {
  verify_virtual_memory_association(vmem);

  ZVirtualMemoryManager& manager = virtual_memory_manager();

  // Shuffle virtual memory
  manager.insert_and_remove_from_low_many(vmem, _numa_id, vmems_out);
}

ZVirtualMemory ZPartition::free_and_claim_virtual_from_low_exact_or_many(size_t size, ZArray<ZVirtualMemory>* vmems_in_out) {
  verify_virtual_memory_association(vmems_in_out);

  ZVirtualMemoryManager& manager = virtual_memory_manager();

  // Shuffle virtual memory
  return manager.insert_and_remove_from_low_exact_or_many(size, _numa_id, vmems_in_out);
}

class ZPreHeatTask : public ZTask {
private:
  ZPartition* const  _partition;
  volatile uintptr_t _current;
  const uintptr_t    _end;

public:
  ZPreHeatTask(ZPartition* partition, zoffset start, zoffset_end end)
    : ZTask("ZPreHeatTask", false /* account_vtime */),
      _partition(partition),
      _current(untype(start)),
      _end(untype(end)) {}

  virtual void work() {
    const size_t size = ZGranuleSize;

    for (;;) {
      // Claim an offset for this thread
      const uintptr_t claimed = AtomicAccess::fetch_then_add(&_current, size);
      if (claimed >= _end) {
        // Done
        break;
      }

      // At this point we know that we have a valid zoffset / zaddress.
      const zoffset offset = to_zoffset(claimed);

      // Pre-heat the granule
      _partition->heat_memory(ZVirtualMemory(offset, size));
    }
  }
};

bool ZPartition::prime(ZWorkers* workers, size_t size, size_t capacity_limit) {
  if (size == 0) {
    return true;
  }

  ZArray<ZVirtualMemory> vmems;

  // Claim virtual memory
  const size_t claimed_size = claim_virtual(size, &vmems);

  // The partition must have size available in virtual memory when priming.
  assert(claimed_size == size, "must succeed %zx == %zx", claimed_size, size);

  // Increase capacity
  increase_capacity(claimed_size, capacity_limit);

  for (ZVirtualMemory vmem : vmems) {
    // Claim the backing physical memory
    claim_physical(vmem);

    // Commit the claimed physical memory
    const size_t committed = commit_physical(vmem);

    if (committed != vmem.size()) {
      // This is a failure state. We do not cleanup the maybe partially committed memory.
      return false;
    }

    // The primed memory should be "heated" asynchronously if it shouldn't be
    // pre-touched up front during startup.
    const bool async_heat = !AlwaysPreTouch;

    map_virtual(vmem, async_heat);

    check_numa_mismatch(vmem, _numa_id);

    if (!async_heat) {
      // Pre-heat memory synchronously
      ZPreHeatTask task(this, vmem.start(), vmem.end());
      workers->run_all(&task);
    }

    // We don't have to take a lock here as no other threads will access the cache
    // until we're finished
    _cache.insert(vmem);
  }

  return true;
}

ZVirtualMemory ZPartition::prepare_harvested_and_claim_virtual(ZMemoryAllocation* allocation) {
  verify_memory_allocation_association(allocation);

  // Unmap virtual memory
  for (const ZVirtualMemory vmem : *allocation->partial_vmems()) {
    unmap_virtual(vmem);
  }

  const size_t harvested = allocation->harvested();
  const int granule_count = (int)(harvested >> ZGranuleSizeShift);
  ZPhysicalMemoryManager& manager = physical_memory_manager();

  // Stash segments
  ZArray<zbacking_index> stash(granule_count);
  manager.stash_segments(*allocation->partial_vmems(), &stash);

  // Shuffle virtual memory. We attempt to allocate enough memory to cover the
  // entire allocation size, not just for the harvested memory.
  const ZVirtualMemory result = free_and_claim_virtual_from_low_exact_or_many(allocation->size(), allocation->partial_vmems());

  // Restore segments
  if (!result.is_null()) {
    // Got exact match. Restore stashed physical segments for the harvested part.
    manager.restore_segments(result.first_part(harvested), stash);
  } else {
    // Got many partial vmems
    manager.restore_segments(*allocation->partial_vmems(), stash);
  }

  if (result.is_null()) {
    // Before returning harvested memory to the cache it must be mapped.
    for (const ZVirtualMemory vmem : *allocation->partial_vmems()) {
      map_virtual(vmem);
    }
  }

  return result;
}

void ZPartition::copy_physical_segments_to_partition(const ZVirtualMemory& at, const ZVirtualMemory& from) {
  verify_virtual_memory_association(at);
  verify_virtual_memory_association(from, true /* check_multi_partition */);

  ZPhysicalMemoryManager& manager = physical_memory_manager();

  // Copy segments
  manager.copy_physical_segments(at, from);
}

void ZPartition::copy_physical_segments_from_partition(const ZVirtualMemory& at, const ZVirtualMemory& to) {
  verify_virtual_memory_association(at);
  verify_virtual_memory_association(to, true /* check_multi_partition */);

  ZPhysicalMemoryManager& manager = physical_memory_manager();

  // Copy segments
  manager.copy_physical_segments(to, at);
}

void ZPartition::commit_increased_capacity(ZMemoryAllocation* allocation, const ZVirtualMemory& vmem, size_t allowed_to_commit) {
  assert(allocation->increased_capacity() > 0, "Nothing to commit");
  assert(allowed_to_commit > 0, "Not allowed to commit anything");

  const size_t already_committed = allocation->harvested();
  const ZVirtualMemory to_be_committed_vmem = vmem.last_part(already_committed);
  assert(to_be_committed_vmem.size() == allocation->increased_capacity(), "must be the same size");

  const size_t should_commit = MIN2(to_be_committed_vmem.size(), allowed_to_commit);
  assert(should_commit > 0, "There must be something to commit");

  const ZVirtualMemory should_commit_vmem = to_be_committed_vmem.first_part(should_commit);

  // Try to commit the not-committed physical memory
  const size_t committed = commit_physical(should_commit_vmem);

  // Keep track of the committed amount
  allocation->set_committed_capacity(committed);
}

void ZPartition::map_memory(ZMemoryAllocation* allocation, const ZVirtualMemory& vmem) {
  sort_segments_physical(vmem);
  map_virtual(vmem);

  check_numa_mismatch(vmem, allocation->partition().numa_id());
}

void ZPartition::free_memory_alloc_failed(ZMemoryAllocation* allocation) {
  verify_memory_allocation_association(allocation);

  // Only decrease the overall used and not the generation used,
  // since the allocation failed and generation used wasn't bumped.
  decrease_used(allocation->size());

  size_t freed = 0;

  // Free mapped memory
  for (const ZVirtualMemory vmem : *allocation->partial_vmems()) {
    freed += vmem.size();
    _cache.insert(vmem);
  }
  assert(allocation->harvested() + allocation->committed_capacity() == freed, "must have freed all"
         " %zu + %zu == %zu", allocation->harvested(), allocation->committed_capacity(), freed);

  // Adjust capacity to reflect the failed capacity increase
  const size_t remaining = allocation->size() - freed;
  if (remaining > 0) {
    decrease_capacity(remaining);
  }
}

void ZPartition::threads_do(ThreadClosure* tc) const {
  if (ZUncommitter::is_enabled()) {
    tc->do_thread(const_cast<ZUncommitter*>(&_uncommitter));
  }

  if (ZMemoryWorker::is_enabled()) {
    tc->do_thread(const_cast<ZMemoryWorker*>(&_mem_worker));
  }
}

void ZPartition::print_on(outputStream* st) const {
  st->print("Partition %u ", _numa_id);
  st->fill_to(17);
  st->print_cr("used %zuM, capacity %zuM, max capacity %zuM",
               _used / M, _capacity / M, dynamic_max_capacity() / M);

  StreamIndentor si(st, 1);
  print_cache_on(st);
}

void ZPartition::print_cache_on(outputStream* st) const {
  _cache.print_on(st);
}

void ZPartition::print_cache_extended_on(outputStream* st) const {
  st->print_cr("Partition %u", _numa_id);

  StreamIndentor si(st, 1);
  _cache.print_extended_on(st);
}

class ZMultiPartitionTracker : CHeapObj<mtGC> {
private:
  struct Element {
    ZVirtualMemory _vmem;
    ZPartition*    _partition;
  };

  ZArray<Element> _map;

  ZMultiPartitionTracker(int capacity)
    : _map(capacity) {}

  const ZArray<Element>* map() const {
    return &_map;
  }

  ZArray<Element>* map() {
    return &_map;
  }

public:
  void prepare_memory_for_free(const ZVirtualMemory& vmem, ZArray<ZVirtualMemory>* vmems_out) const {
    // Remap memory back to original partition
    for (const Element partial_allocation : *map()) {
      ZVirtualMemory remaining_vmem = partial_allocation._vmem;
      ZPartition& partition = *partial_allocation._partition;

      const size_t size = remaining_vmem.size();

      // Allocate new virtual address ranges
      const int start_index = vmems_out->length();
      const size_t claimed_virtual = partition.claim_virtual(remaining_vmem.size(), vmems_out);

      // We are holding memory associated with this partition, and we do not
      // overcommit virtual memory claiming. So virtual memory must always
      // be available.
      assert(claimed_virtual == size, "must succeed");

      // Remap to the newly allocated virtual address ranges
      for (const ZVirtualMemory& to_vmem : vmems_out->slice_back(start_index)) {
        const ZVirtualMemory from_vmem = remaining_vmem.shrink_from_front(to_vmem.size());

        // Copy physical segments
        partition.copy_physical_segments_to_partition(to_vmem, from_vmem);

        // Unmap from_vmem
        partition.unmap_virtual_from_multi_partition(from_vmem);

        // Map to_vmem
        partition.map_virtual(to_vmem);
      }
      assert(remaining_vmem.size() == 0, "must have mapped all claimed virtual memory");
    }
  }

  static void destroy(const ZMultiPartitionTracker* tracker) {
    delete tracker;
  }

  static ZMultiPartitionTracker* create(const ZMultiPartitionAllocation* multi_partition_allocation, const ZVirtualMemory& vmem) {
    const ZArray<ZMemoryAllocation*>* const partial_allocations = multi_partition_allocation->allocations();

    ZMultiPartitionTracker* const tracker = new ZMultiPartitionTracker(partial_allocations->length());

    ZVirtualMemory remaining = vmem;

    // Each partial allocation is mapped to the virtual memory in order
    for (ZMemoryAllocation* partial_allocation : *partial_allocations) {
      // Track each separate vmem's partition
      const ZVirtualMemory partial_vmem = remaining.shrink_from_front(partial_allocation->size());
      ZPartition* const partition = &partial_allocation->partition();
      tracker->map()->push({partial_vmem, partition});
    }

    return tracker;
  }
};

ZPageAllocator::ZPageAllocator(size_t static_min_capacity,
                               size_t initial_capacity,
                               size_t soft_max_capacity,
                               size_t static_max_capacity)
  : _lock(),
    _virtual(static_max_capacity),
    _physical(static_min_capacity, static_max_capacity),
    _static_min_capacity(static_min_capacity),
    _static_max_capacity(static_max_capacity),
    _heuristic_max_capacity(ZAdaptiveHeapSizing ? initial_capacity : static_max_capacity),
    _used(0),
    _used_generations{0,0},
    _collection_stats{{0, 0},{0, 0}},
    _partitions(ZValueIdTagType{}, this, static_min_capacity, static_max_capacity),
    _stalled(),
    _safe_destroy(),
    _initialized(false) {

  if (!_virtual.is_initialized() || !_physical.is_initialized()) {
    return;
  }

  log_info_p(gc, init)("Min Capacity: %zuM", static_min_capacity / M);
  log_info_p(gc, init)("Initial Capacity: %zuM", initial_capacity / M);
  log_info_p(gc, init)("Max Capacity: %zuM", static_max_capacity / M);
  if (soft_max_capacity != 0) {
    log_info_p(gc, init)("Soft Max Capacity: %zuM", soft_max_capacity / M);
  }
  if (ZPageSizeMediumEnabled) {
    if (ZPageSizeMediumMin == ZPageSizeMediumMax) {
      log_info_p(gc, init)("Page Size Medium: %zuM", ZPageSizeMediumMax / M);
    } else {
      log_info_p(gc, init)("Page Size Medium: Range [%zuM, %zuM]", ZPageSizeMediumMin / M, ZPageSizeMediumMax / M);
    }
  } else {
    log_info_p(gc, init)("Medium Page Size: N/A");
  }
  log_info_p(gc, init)("Pre-touch: %s", AlwaysPreTouch ? "Enabled" : "Disabled");
  ZAdaptiveHeap::print();
  log_info_p(gc, init)("Memory Heating: %s", ZMemoryHeating ? "Enabled" : "Disabled");

  // Warn if system limits could stop us from reaching max capacity
  const size_t expected_capacity = ZAdaptiveHeap::explicit_max_capacity()
      ? static_max_capacity
      : initial_capacity;

  _physical.warn_commit_limits(expected_capacity, static_max_capacity);

  // Successfully initialized
  _initialized = true;
}

bool ZPageAllocator::is_initialized() const {
  return _initialized;
}

bool ZPageAllocator::prime_cache(ZWorkers* workers, size_t size) {
  ZPartitionIterator iter = partition_iterator();
  for (ZPartition* partition; iter.next(&partition);) {
    const uint32_t numa_id = partition->numa_id();
    const size_t to_prime = ZNUMA::calculate_share(numa_id, size);
    const size_t partition_limit = ZNUMA::calculate_share(numa_id, _static_max_capacity);

    if (!partition->prime(workers, to_prime, partition_limit)) {
      return false;
    }
  }

  return true;
}

size_t ZPageAllocator::static_min_capacity() const {
  return _static_min_capacity;
}

size_t ZPageAllocator::static_max_capacity() const {
  return _static_max_capacity;
}

size_t ZPageAllocator::dynamic_max_capacity() const {
  if (!ZAdaptiveHeapSizing) {
    return _static_max_capacity;
  }

  physical_memory_size_type result = ZAdaptiveHeap::machine_physical_memory();

  if (!os::is_containerized()) {
    return clamp(align_down(size_t(result), ZGranuleSize), _static_min_capacity, _static_max_capacity);
  }

  physical_memory_size_type hard_container_limit;
  if (os::Container::memory_limit(hard_container_limit)) {
    result = MIN2(result, hard_container_limit);
  }

  physical_memory_size_type throttle_container_limit;
  if (os::Container::memory_throttle_limit(throttle_container_limit)) {
    result = MIN2(result, throttle_container_limit);
  }

  return clamp(align_down(size_t(result), ZGranuleSize), _static_min_capacity, _static_max_capacity);
}

static size_t calculate_system_max_capacity(size_t system_used,
                                            size_t system_max,
                                            size_t capacity,
                                            size_t min_capacity,
                                            size_t max_capacity) {
  // It is a bit naive to assume all available memory can be directly turned
  // into our own heap memory. We need auxiliary GC data structures, and other
  // processes can also take the memory as we might not be alone. By scaling
  // the available memory we stay on the pessimistic size, and let the estimated
  // current max capacity grow gradually as we approach the limits instead.
  const double near_avoid = (1.0 - ZMemoryCriticalThreshold);
  const size_t scaled_system_max = size_t(double(system_max) * near_avoid);
  size_t result;

  // Calculate capacity limits based on availability
  if (system_used > scaled_system_max) {
    const size_t overcommitted = system_used - scaled_system_max;

    if (overcommitted > capacity) {
      return min_capacity;
    }

    result = capacity - overcommitted;
  } else {
    const double system_available = double(scaled_system_max - system_used);
    const size_t scaled_system_available = size_t(system_available * near_avoid);

    result = capacity + scaled_system_available;
  }

  // Clamp and align the max capacity by its legal memory bounds
  return clamp(align_down(result, ZGranuleSize), min_capacity, max_capacity);
}

size_t ZPageAllocator::current_max_capacity() const {
  if (!ZAdaptiveHeapSizing) {
    // When not adapting use supplied max capacity
    return _static_max_capacity;
  }

  ZMachineMemoryInfo machine_memory_info = ZAdaptiveHeap::machine_memory_info();
  if (!machine_memory_info._is_valid) {
    return dynamic_max_capacity();
  }

  const size_t machine_used_memory = size_t(machine_memory_info._physical_memory - machine_memory_info._available_memory);
  const size_t machine_max_memory = size_t(machine_memory_info._physical_memory);

  const size_t cap = capacity();

  const size_t machine_max_capacity = calculate_system_max_capacity(machine_used_memory,
                                                                    machine_max_memory,
                                                                    cap,
                                                                    _static_min_capacity,
                                                                    _static_max_capacity);

  if (!os::is_containerized()) {
    return machine_max_capacity;
  }

  physical_memory_size_type container_max_memory;

  // Keep below the hard memory limit or the OOM killer will get us
  if (!os::Container::memory_limit(container_max_memory)) {
    container_max_memory = machine_max_memory;
  }

  // Avoid allocating past the throttle limit; the app will become useless here
  physical_memory_size_type container_high_memory;
  if (os::Container::memory_throttle_limit(container_high_memory)) {
    container_max_memory = MIN2(container_max_memory, container_high_memory);
  }

  physical_memory_size_type container_used_memory;

  if (!os::Container::used_memory(container_used_memory)) {
    // If we can't measure memory usage in the container, we also can not compare
    // it to any container limits. Effectively, the JVM is not appropriately
    // containerized, so use machine max capacity.
    return machine_max_capacity;
  }

  const size_t container_max_capacity = calculate_system_max_capacity(size_t(container_used_memory),
                                                                      container_max_memory,
                                                                      cap,
                                                                      _static_min_capacity,
                                                                      _static_max_capacity);

  return MIN2(machine_max_capacity, container_max_capacity);
}

static size_t clamp_to_soft_max_capacity(size_t value) {
  assert(is_aligned(value, ZGranuleSize), "Must be granule aligned: 0x%08zx", value);
  // Note that SoftMaxHeapSize is a manageable flag
  const size_t soft_max_capacity = AtomicAccess::load(&SoftMaxHeapSize);

  if (soft_max_capacity == 0) {
    // SoftMaxHeapSize is disabled / unspecified
    return value;
  }

  // Clamp to SoftMaxHeapSize, but should not be less than MinHeapSize
  return clamp(value, MinHeapSize, MAX2(MinHeapSize, align_down(soft_max_capacity, ZGranuleSize)));
}

size_t ZPageAllocator::heuristic_max_capacity() const {
  const size_t heuristic_max_capacity = _heuristic_max_capacity.load_relaxed();

  return clamp_to_soft_max_capacity(heuristic_max_capacity);
}

size_t ZPageAllocator::capacity() const {
  size_t capacity = 0;

  ZPartitionConstIterator iter = partition_iterator();
  for (const ZPartition* partition; iter.next(&partition);) {
    capacity += AtomicAccess::load(&partition->_capacity);
  }

  return capacity;
}

size_t ZPageAllocator::used() const {
  return AtomicAccess::load(&_used);
}

size_t ZPageAllocator::used_generation(ZGenerationId id) const {
  return AtomicAccess::load(&_used_generations[(int)id]);
}

size_t ZPageAllocator::unused() const {
  const ssize_t used = (ssize_t)ZPageAllocator::used();
  ssize_t capacity = 0;
  ssize_t claimed = 0;

  ZPartitionConstIterator iter = partition_iterator();
  for (const ZPartition* partition; iter.next(&partition);) {
    capacity += (ssize_t)AtomicAccess::load(&partition->_capacity);
    claimed += (ssize_t)AtomicAccess::load(&partition->_claimed);
  }

  const ssize_t unused = capacity - used - claimed;
  return unused > 0 ? (size_t)unused : 0;
}

void ZPageAllocator::shrink_heuristic_max(size_t shrink_amount) {
  for (;;) {
    const size_t heuristic_max = _heuristic_max_capacity.load_relaxed();
    const size_t capacity = MAX2(heuristic_max - MIN2(shrink_amount, heuristic_max), ZGranuleSize);
    if (heuristic_max > capacity) {
      if (_heuristic_max_capacity.compare_exchange(heuristic_max, capacity) == heuristic_max) {
        break;
      }
    }
    return;
  }
}

void ZPageAllocator::truncate_heuristic_max_after_capacity_decrease(TruncationReason reason) {
  // Adjust heuristic max capacity to ensure GC tries to keep below current capacity
  const size_t capacity = ZPageAllocator::capacity();

  for (;;) {
    const size_t heuristic_max = _heuristic_max_capacity.load_relaxed();
    if (heuristic_max <= capacity) {
      return;
    }
    if (_heuristic_max_capacity.compare_exchange(heuristic_max, capacity) != heuristic_max) {
      continue;
    }
    const size_t current_max = current_max_capacity();
    if (reason == TruncationReason::CommitFailure) {
      log_debug(gc)("Forced to lower heap size from "
                    "%zuM(%.0f%%) to %zuM(%.0f%%)",
                    heuristic_max / M, percent_of(heuristic_max, current_max),
                    capacity / M, percent_of(capacity, current_max));
    }

    if (ZAdaptiveHeapSizing) {
      heap_truncated(capacity, reason);
    }
    return;
  }
}

void ZPageAllocator::adapt_heuristic_max_capacity(ZGenerationId generation) {
  const size_t heuristic_max = heuristic_max_capacity();
  const size_t static_min_capacity = _static_min_capacity;
  const size_t used = ZPageAllocator::used();
  const size_t capacity = MAX2(ZPageAllocator::capacity(), used);
  const size_t curr_max_capacity = MAX2(capacity, current_max_capacity());
  const size_t highest_soft_capacity = clamp_to_soft_max_capacity(curr_max_capacity);

  const double alloc_rate = ZStatMutatorAllocRate::stats()._avg;

  const ZHeapResizeMetrics metrics = {
    highest_soft_capacity,
    curr_max_capacity,
    heuristic_max,
    static_min_capacity,
    capacity,
    used,
    alloc_rate
  };

  const size_t selected_capacity = ZAdaptiveHeap::compute_heap_size(metrics, generation);

  // Update heuristic max capacity
  _heuristic_max_capacity.store_relaxed(selected_capacity);

  heap_resized(selected_capacity);
}

void ZPageAllocator::heap_resized(size_t selected_capacity) {
  precond(ZAdaptiveHeapSizing);

  // Update per partition heuristic max capacity
  ZPerNUMAIterator<ZPartition> iter = partition_iterator();
  for (ZPartition* partition; iter.next(&partition);) {
    ZMemoryWorker& memory_worker = partition->memory_worker();
    const uint32_t numa_id = partition->numa_id();

    const size_t heuristic_max_capacity = ZNUMA::calculate_share(numa_id, selected_capacity);
    const size_t capacity = partition->capacity();

    if (capacity < heuristic_max_capacity) {
      // Grow the heap
      memory_worker.request_grow_capacity(heuristic_max_capacity);
    } else {
      // Consider shrinking the heap

      // Set up direct uncommit to shrink the heap
      const size_t surplus_capacity = capacity - heuristic_max_capacity;

      // Uncommit 5% of the surplus at a time for a smooth capacity decline
      const size_t uncommit_fraction = 20;
      const size_t uncommit_request = align_up(surplus_capacity / uncommit_fraction, ZGranuleSize);

      const size_t requested_capacity = capacity - uncommit_request;

      // If the surplus capacity isn't over 5% of the capacity, the point of
      // uncommitting heuristically seems questionable and might just cause
      // pointless fluctuation.
      if (surplus_capacity > capacity / uncommit_fraction) {
        // Update memory worker target capacity
        memory_worker.request_shrink_capacity(requested_capacity);
      } else {
        memory_worker.stop_heap_resizing();
      }
    }
  }

  // Complain about misconfigurations
  _physical.warn_commit_limits(selected_capacity, dynamic_max_capacity());
}

void ZPageAllocator::heap_truncated(size_t selected_capacity, ZPageAllocator::TruncationReason reason) {
  precond(ZAdaptiveHeapSizing);

  if (reason == TruncationReason::Uncommit) {
    // Uncommit heap truncation is part of heap resizing.
    return;
  }
  assert(reason == TruncationReason::CommitFailure, "Unexpected: %d", (int)reason);

  ZPerNUMAIterator<ZPartition> iter = partition_iterator();
  for (ZPartition* partition; iter.next(&partition);) {
    // Update memory worker target capacity
    ZMemoryWorker& mem_worker = partition->memory_worker();
    mem_worker.stop_heap_resizing();
  }

  // Complain about misconfigurations
  _physical.warn_commit_limits(selected_capacity, dynamic_max_capacity());
}

void ZPageAllocator::adjust_capacity(size_t used_soon) {
  const ZMemoryPressureMetrics mem_metrics = ZAdaptiveHeap::memory_pressure_metrics();
  ZStatSystemMemoryUsage::record(mem_metrics);

  ZPerNUMAIterator<ZPartition> iter = partition_iterator();

  for (ZPartition* partition; iter.next(&partition);) {
    ZMemoryWorker& mem_worker = partition->memory_worker();
    const size_t partition_capacity = partition->capacity();

    if (ZAdaptiveHeap::is_memory_pressure_high(mem_metrics)) {
      // When memory usage is high, request uncommitting if possible
      mem_worker.request_shrink_capacity_granule();
    } else {
      // When memory pressure is not high, stop targetless uncommit.
      mem_worker.stop_shrink_capacity_granule();

      // When memory pressure is not high, try to commit memory ahead of mutators.
      const uint32_t numa_id = partition->numa_id();
      const size_t used_soon_share = ZNUMA::calculate_share(numa_id, used_soon);
      if (used_soon_share > partition_capacity) {
        mem_worker.request_grow_capacity(used_soon_share);
      }
    }
  }
}

void ZPageAllocator::update_collection_stats(ZGenerationId id) {
  assert(SafepointSynchronize::is_at_safepoint(), "Should be at safepoint");

#ifdef ASSERT
  size_t total_used = 0;

  ZPartitionIterator iter(&_partitions);
  for (ZPartition* partition; iter.next(&partition);) {
    total_used += partition->_used;
  }

  assert(total_used == _used, "Must be consistent %zu == %zu", total_used, _used);
#endif

  _collection_stats[(int)id]._used_high = _used;
  _collection_stats[(int)id]._used_low = _used;
}

ZPageAllocatorStats ZPageAllocator::stats_inner(ZGeneration* generation) const {
  return ZPageAllocatorStats(heuristic_max_capacity(),
                             capacity(),
                             _used,
                             _collection_stats[(int)generation->id()]._used_high,
                             _collection_stats[(int)generation->id()]._used_low,
                             used_generation(generation->id()),
                             generation->freed(),
                             generation->promoted(),
                             generation->compacted(),
                             _stalled.size());
}

ZPageAllocatorStats ZPageAllocator::stats(ZGeneration* generation) const {
  ZLocker<ZLock> locker(&_lock);
  return stats_inner(generation);
}

ZPageAllocatorStats ZPageAllocator::update_and_stats(ZGeneration* generation) {
  ZLocker<ZLock> locker(&_lock);

  update_collection_stats(generation->id());
  return stats_inner(generation);
}

void ZPageAllocator::increase_used_generation(ZGenerationId id, size_t size) {
  // Update atomically since we have concurrent readers and writers
  AtomicAccess::add(&_used_generations[(int)id], size, memory_order_relaxed);
}

void ZPageAllocator::decrease_used_generation(ZGenerationId id, size_t size) {
  // Update atomically since we have concurrent readers and writers
  AtomicAccess::sub(&_used_generations[(int)id], size, memory_order_relaxed);
}

void ZPageAllocator::promote_used(const ZPage* from, const ZPage* to) {
  assert(from->start() == to->start(), "pages start at same offset");
  assert(from->size() == to->size(),   "pages are the same size");
  assert(from->age() != ZPageAge::old, "must be promotion");
  assert(to->age() == ZPageAge::old,   "must be promotion");

  decrease_used_generation(ZGenerationId::young, to->size());
  increase_used_generation(ZGenerationId::old, to->size());
}

static void check_out_of_memory_during_initialization() {
  if (!is_init_completed()) {
    vm_exit_during_initialization("java.lang.OutOfMemoryError", "Java heap too small");
  }
}

ZPage* ZPageAllocator::alloc_page(ZPageType type, size_t size, ZAllocationFlags flags, ZPageAge age, uint32_t preferred_partition) {
  EventZPageAllocation event;

  ZPageAllocation allocation(type, size, flags, age, preferred_partition);

  // Allocate the page
  ZPage* const page = alloc_page_inner(&allocation, ZPageAllocationAttempt::initial);
  if (page == nullptr) {
    // Out of memory
    return nullptr;
  }

  // Update allocation statistics. Exclude gc relocations to avoid
  // artificial inflation of the allocation rate during relocation.
  if (!flags.gc_relocation() && is_init_completed()) {
    // Note that there are two allocation rate counters, which have
    // different purposes and are sampled at different frequencies.
    ZStatInc(ZCounterMutatorAllocationRate, page->size());
    ZStatMutatorAllocRate::sample_allocation(page->size());
  }

  const ZPageAllocationStats stats = allocation.stats();
  const int num_harvested_vmems = stats._num_harvested_vmems;
  const size_t harvested = stats._total_harvested;

  if (harvested > 0) {
    ZStatInc(ZCounterMappedCacheHarvest, harvested);
    log_debug(gc, heap)("Mapped Cache Harvested: %zuM (%d)", harvested / M, num_harvested_vmems);
  }

  // Send event for successful allocation
  allocation.send_event(true /* successful */);

  return page;
}

bool ZPageAllocator::alloc_page_stall(ZPageAllocation* allocation) {
  ZStatTimer timer(ZCriticalPhaseAllocationStall);
  EventZAllocationStall event;

  // We can only block if the VM is fully initialized
  check_out_of_memory_during_initialization();

  // Start asynchronous minor GC
  const ZDriverRequest request(GCCause::_z_allocation_stall, ZYoungGCThreads, 0);
  ZDriver::minor()->collect(request);

  // Wait for allocation to complete or fail
  const bool result = allocation->wait();

  {
    // Guard deletion of underlying semaphore. This is a workaround for
    // a bug in sem_post() in glibc < 2.21, where it's not safe to destroy
    // the semaphore immediately after returning from sem_wait(). The
    // reason is that sem_post() can touch the semaphore after a waiting
    // thread have returned from sem_wait(). To avoid this race we are
    // forcing the waiting thread to acquire/release the lock held by the
    // posting thread. https://sourceware.org/bugzilla/show_bug.cgi?id=12674
    ZLocker<ZLock> locker(&_lock);
  }

  // Send event
  event.commit((u8)allocation->type(), allocation->size());

  return result;
}

ZPage* ZPageAllocator::alloc_page_inner(ZPageAllocation* allocation, ZPageAllocationAttempt attempt) {
  // Claim the capacity needed for this allocation.
  //
  // The claimed capacity comes from memory already mapped in the cache, or
  // from increasing the capacity. The increased capacity allows us to allocate
  // physical memory from the physical memory manager later on.
  //
  // Note that this call might block in a safepoint if the non-blocking flag is
  // not set.
  if (!claim_capacity_or_stall(allocation, &attempt)) {
    // Out of memory
    return nullptr;
  }

  // If the entire claimed capacity came from claiming a single vmem from the
  // mapped cache then the allocation has been satisfied and we are done.
  const ZVirtualMemory cached_vmem = satisfied_from_cache_vmem(allocation);
  if (!cached_vmem.is_null()) {
    return create_page(allocation, cached_vmem);
  }

  // We couldn't find a satisfying vmem in the cache, so we need to build one.

  // Claim virtual memory, either from remapping harvested vmems from the
  // mapped cache or by claiming it straight from the virtual memory manager.
  const ZVirtualMemory vmem = claim_virtual_memory(allocation);
  if (vmem.is_null()) {
    log_error(gc)("Out of address space");
    free_after_alloc_page_failed(allocation);

    // Crash in debug builds for more information
    DEBUG_ONLY(fatal("Out of address space");)
    return nullptr;
  }

  // Claim physical memory for the increased capacity. The previous claiming of
  // capacity guarantees that this will succeed.
  claim_physical_for_increased_capacity(allocation, vmem);

  // Commit memory for the increased capacity and map the entire vmem.
  if (!commit_and_map(allocation, vmem)) {
    free_after_alloc_page_failed(allocation);
    assert(attempt != ZPageAllocationAttempt::retry, "Should be initial or stall");
    return alloc_page_inner(allocation, ZPageAllocationAttempt::retry);
  }

  return create_page(allocation, vmem);
}

bool ZPageAllocator::claim_capacity_or_stall(ZPageAllocation* allocation, ZPageAllocationAttempt* attempt) {
  {
    ZLocker<ZLock> locker(&_lock);

    // Try to claim memory
    if (claim_capacity(allocation, *attempt)) {
      // Keep track of usage
      increase_used(allocation->size());

      return true;
    }

    // Failed to claim memory
    if (allocation->flags().non_blocking()) {
      // Don't stall
      return false;
    }

    // Enqueue allocation request
    _stalled.insert_last(allocation);
  }

  // We are stalling on this allocation
  *attempt = ZPageAllocationAttempt::stall;

  // Stall
  return alloc_page_stall(allocation);
}

bool ZPageAllocator::claim_capacity(ZPageAllocation* allocation, ZPageAllocationAttempt attempt) {
  ZSinglePartitionAllocation* const single_partition_allocation = allocation->single_partition_allocation();
  const bool is_fast_medium = allocation->flags().fast_medium();
  const uint32_t start_partition = allocation->preferred_partition();
  const uint32_t num_partitions = _partitions.count();

  // Round robin soft single-partition claiming
  const size_t soft_limit = heuristic_max_capacity();

  uint32_t highest_available_id = start_partition;
  size_t highest_availiable = 0;

  for (uint32_t i = 0; i < num_partitions; ++i) {
    const uint32_t partition_id = (start_partition + i) % num_partitions;
    const size_t soft_partition_limit = ZNUMA::calculate_share(partition_id, soft_limit);

    const bool claim_result = is_fast_medium
        ? claim_capacity_single_partition_fast_medium(single_partition_allocation, partition_id, soft_partition_limit)
        : claim_capacity_single_partition(single_partition_allocation, partition_id, attempt, soft_partition_limit);

    if (claim_result) {
      return true;
    }

    const ZPartition& partition = _partitions.get(partition_id);
    const size_t partition_availiable = partition.available(attempt, partition.static_max_capacity());
    if (partition_availiable > highest_availiable) {
      highest_available_id = partition_id;
      highest_availiable = partition_availiable;
    }
  }

  // Hard single-partition claiming - only try from the highest availible partition
  const size_t hard_partition_limit = _partitions.get(highest_available_id).static_max_capacity();
  const bool claim_result = is_fast_medium
      ? claim_capacity_single_partition_fast_medium(single_partition_allocation, highest_available_id, hard_partition_limit)
      : claim_capacity_single_partition(single_partition_allocation, highest_available_id, attempt, hard_partition_limit);

  if (claim_result) {
    return true;
  }

  if (is_fast_medium || !is_multi_partition_allowed(allocation, attempt, _static_max_capacity)) {
    // Multi-partition claiming is not allowed or not possible
    return false;
  }

  // Multi-partition claiming

  // Flip allocation to multi-partition allocation
  allocation->initiate_multi_partition_allocation();

  ZMultiPartitionAllocation* const multi_partition_allocation = allocation->multi_partition_allocation();

  claim_capacity_multi_partition(multi_partition_allocation, highest_available_id, attempt);

  return true;
}

bool ZPageAllocator::claim_capacity_single_partition_fast_medium(ZSinglePartitionAllocation* single_partition_allocation, uint32_t partition_id, size_t capacity_limit) {
  ZPartition& partition = _partitions.get(partition_id);

  return partition.claim_capacity_fast_medium(single_partition_allocation->allocation(), capacity_limit);
}

bool ZPageAllocator::claim_capacity_single_partition(ZSinglePartitionAllocation* single_partition_allocation, uint32_t partition_id, ZPageAllocationAttempt attempt, size_t capacity_limit) {
  ZPartition& partition = _partitions.get(partition_id);

  return partition.claim_capacity(single_partition_allocation->allocation(), attempt, capacity_limit);
}

void ZPageAllocator::claim_capacity_multi_partition(ZMultiPartitionAllocation* multi_partition_allocation, uint32_t start_partition, ZPageAllocationAttempt attempt) {
  const size_t size = multi_partition_allocation->size();
  const uint32_t num_partitions = _partitions.count();
  const size_t split_size = align_up(size / num_partitions, ZGranuleSize);

  size_t remaining = size;

  const auto do_claim_one_partition = [&](ZPartition& partition, bool claim_evenly) {
    if (remaining == 0) {
      // All memory claimed
      return false;
    }

    // Multi partition allocation, try to split evenly but at most static_max_capacity
    const size_t capacity_limit = partition.static_max_capacity();

    const size_t max_alloc_size = claim_evenly ? MIN2(split_size, remaining) : remaining;

    // This guarantees that claim_physical below will succeed
    const size_t alloc_size = MIN2(max_alloc_size, partition.available(attempt, capacity_limit));

    // Skip over empty allocations
    if (alloc_size == 0) {
      // Continue
      return true;
    }

    ZMemoryAllocation partial_allocation(alloc_size);

    // Claim capacity for this allocation - this should succeed
    const bool result = partition.claim_capacity(&partial_allocation, attempt, capacity_limit);
    assert(result, "Should have succeeded");

    // Register allocation
    multi_partition_allocation->register_allocation(partial_allocation);

    // Update remaining
    remaining -= alloc_size;

    // Continue
    return true;
  };

  // Loops over every partition and claims memory
  const auto do_claim_each_partition = [&](bool claim_evenly) {
    for (uint32_t i = 0; i < num_partitions; ++i) {
      const uint32_t partition_id = (start_partition + i) % num_partitions;
      ZPartition& partition = _partitions.get(partition_id);

      if (!do_claim_one_partition(partition, claim_evenly)) {
        // All memory claimed
        break;
      }
    }
  };

  // Try to claim from multiple partitions

  // Try to claim up to split_size on each partition
  do_claim_each_partition(true  /* claim_evenly */);

  // Try claim the remaining
  do_claim_each_partition(false /* claim_evenly */);

  assert(remaining == 0, "Must have claimed capacity for the whole allocation");
}

ZVirtualMemory ZPageAllocator::satisfied_from_cache_vmem(const ZPageAllocation* allocation) const {
  if (allocation->is_multi_partition()) {
    // Multi-partition allocations are always harvested and/or committed, so
    // there's never a satisfying vmem from the caches.
    return {};
  }

  return allocation->satisfied_from_cache_vmem();
}

ZVirtualMemory ZPageAllocator::claim_virtual_memory(ZPageAllocation* allocation) {
  // Note: that the single-partition performs "shuffling" of already harvested
  // vmem(s), while the multi-partition searches for available virtual memory
  // area without shuffling.

  if (allocation->is_multi_partition()) {
    return claim_virtual_memory_multi_partition(allocation->multi_partition_allocation());
  } else {
    return claim_virtual_memory_single_partition(allocation->single_partition_allocation());
  }
}

ZVirtualMemory ZPageAllocator::claim_virtual_memory_single_partition(ZSinglePartitionAllocation* single_partition_allocation) {
  ZMemoryAllocation* const allocation = single_partition_allocation->allocation();
  ZPartition& partition = allocation->partition();

  if (allocation->harvested() > 0) {
    // We claim virtual memory from the harvested vmems and perhaps also
    // allocate more to match the allocation request.
    return partition.prepare_harvested_and_claim_virtual(allocation);
  } else {
    // Just try to claim virtual memory
    return partition.claim_virtual(allocation->size());
  }
}

ZVirtualMemory ZPageAllocator::claim_virtual_memory_multi_partition(ZMultiPartitionAllocation* multi_partition_allocation) {
  const size_t size = multi_partition_allocation->size();

  const ZVirtualMemory vmem = _virtual.remove_from_low_multi_partition(size);
  if (!vmem.is_null()) {
    // Copy claimed multi-partition vmems, we leave the old vmems mapped until
    // after we have committed. In case committing fails we can simply
    // reinsert the initial vmems.
    copy_claimed_physical_multi_partition(multi_partition_allocation, vmem);
  }

  return vmem;
}

void ZPageAllocator::copy_claimed_physical_multi_partition(ZMultiPartitionAllocation* multi_partition_allocation, const ZVirtualMemory& vmem) {
  // Start at the new dest offset
  ZVirtualMemory remaining_dest_vmem = vmem;

  for (const ZMemoryAllocation* partial_allocation : *multi_partition_allocation->allocations()) {
    // Split off the partial allocation's destination vmem
    ZVirtualMemory partial_dest_vmem = remaining_dest_vmem.shrink_from_front(partial_allocation->size());

    // Get the partial allocation's partition
    ZPartition& partition = partial_allocation->partition();

    // Copy all physical segments from the partition to the destination vmem
    for (const ZVirtualMemory from_vmem : *partial_allocation->partial_vmems()) {
      // Split off destination
      const ZVirtualMemory to_vmem = partial_dest_vmem.shrink_from_front(from_vmem.size());

      // Copy physical segments
      partition.copy_physical_segments_from_partition(from_vmem, to_vmem);
    }
  }
}

void ZPageAllocator::claim_physical_for_increased_capacity(ZPageAllocation* allocation, const ZVirtualMemory& vmem) {
  assert(allocation->size() == vmem.size(), "vmem should be the final entry");

  if (allocation->is_multi_partition()) {
    claim_physical_for_increased_capacity_multi_partition(allocation->multi_partition_allocation(), vmem);
  } else {
    claim_physical_for_increased_capacity_single_partition(allocation->single_partition_allocation(), vmem);
  }
}

void ZPageAllocator::claim_physical_for_increased_capacity_single_partition(ZSinglePartitionAllocation* single_partition_allocation, const ZVirtualMemory& vmem) {
  claim_physical_for_increased_capacity(single_partition_allocation->allocation(), vmem);
}

void ZPageAllocator::claim_physical_for_increased_capacity_multi_partition(const ZMultiPartitionAllocation* multi_partition_allocation, const ZVirtualMemory& vmem) {
  ZVirtualMemory remaining = vmem;

  for (ZMemoryAllocation* allocation : *multi_partition_allocation->allocations()) {
    const ZVirtualMemory partial = remaining.shrink_from_front(allocation->size());
    claim_physical_for_increased_capacity(allocation, partial);
  }
}

void ZPageAllocator::claim_physical_for_increased_capacity(ZMemoryAllocation* allocation, const ZVirtualMemory& vmem) {
  // The previously harvested memory is memory that has already been committed
  // and mapped. The rest of the vmem gets physical memory assigned here and
  // will be committed in a subsequent function.

  const size_t already_committed = allocation->harvested();
  const size_t non_committed = allocation->size() - already_committed;
  const size_t increased_capacity = allocation->increased_capacity();

  assert(non_committed == increased_capacity,
         "Mismatch non_committed: " PTR_FORMAT " increased_capacity: " PTR_FORMAT,
         non_committed, increased_capacity);

  if (non_committed > 0) {
    ZPartition& partition = allocation->partition();
    ZVirtualMemory non_committed_vmem = vmem.last_part(already_committed);
    partition.claim_physical(non_committed_vmem);
  }
}

bool ZPageAllocator::commit_and_map(ZPageAllocation* allocation, const ZVirtualMemory& vmem) {
  assert(allocation->size() == vmem.size(), "vmem should be the final entry");

  if (allocation->is_multi_partition()) {
    return commit_and_map_multi_partition(allocation->multi_partition_allocation(), vmem);
  } else {
    return commit_and_map_single_partition(allocation->single_partition_allocation(), vmem);
  }
}

bool ZPageAllocator::commit_and_map_single_partition(ZSinglePartitionAllocation* single_partition_allocation, const ZVirtualMemory& vmem) {
  const bool commit_successful = commit_single_partition(single_partition_allocation, vmem);

  // Map the vmem
  map_committed_single_partition(single_partition_allocation, vmem);

  if (commit_successful) {
    return true;
  }

  // Commit failed
  cleanup_failed_commit_single_partition(single_partition_allocation, vmem);

  return false;
}

bool ZPageAllocator::commit_and_map_multi_partition(ZMultiPartitionAllocation* multi_partition_allocation, const ZVirtualMemory& vmem) {
  if (commit_multi_partition(multi_partition_allocation, vmem)) {
    // Commit successful

    // Unmap harvested vmems
    unmap_harvested_multi_partition(multi_partition_allocation);

    // Map the vmem
    map_committed_multi_partition(multi_partition_allocation, vmem);

    return true;
  }

  // Commit failed
  cleanup_failed_commit_multi_partition(multi_partition_allocation, vmem);

  return false;
}

size_t ZPageAllocator::allowed_to_commit(size_t about_to_commit) {
  // We only allow committing up to the current max capacity. Even if capacity
  // may have been allowed to be increased on individual partitions, we may
  // disallow that capacity to be committed if it turns out that it will put
  // too much strain on the system.
  const size_t current_max = current_max_capacity();
  const size_t capacity = ZPageAllocator::capacity();

  // Capacity already accounts for the increase as part of this allocation,
  // subtract it to get the already committed capacity.
  const size_t committed_capacity = capacity - about_to_commit;

  if (committed_capacity >= current_max) {
    return 0;
  }

  return current_max - committed_capacity;
}

void ZPageAllocator::commit(ZMemoryAllocation* allocation, const ZVirtualMemory& vmem) {
  ZPartition& partition = allocation->partition();
  const size_t allowed_to_commit = ZPageAllocator::allowed_to_commit(allocation->increased_capacity());

  // If we both have something to commit and we're allowed to commit something,
  // then try to commit.
  if (allocation->increased_capacity() > 0 && allowed_to_commit > 0) {
    // Commit memory
    partition.commit_increased_capacity(allocation, vmem, allowed_to_commit);
  } else {
    // Record nothing to commit
    allocation->set_committed_capacity(0);
  }
}

bool ZPageAllocator::commit_single_partition(ZSinglePartitionAllocation* single_partition_allocation, const ZVirtualMemory& vmem) {
  ZMemoryAllocation* const allocation = single_partition_allocation->allocation();

  commit(allocation, vmem);

  return !allocation->commit_failed();
}

bool ZPageAllocator::commit_multi_partition(ZMultiPartitionAllocation* multi_partition_allocation, const ZVirtualMemory& vmem) {
  bool commit_failed = false;
  ZVirtualMemory remaining = vmem;
  for (ZMemoryAllocation* const allocation : *multi_partition_allocation->allocations()) {
    // Split off the partial allocation's memory range
    const ZVirtualMemory partial_vmem = remaining.shrink_from_front(allocation->size());

    commit(allocation, partial_vmem);

    // Keep track if any partial allocation failed to commit
    commit_failed |= allocation->commit_failed();
  }

  assert(remaining.size() == 0, "all memory must be accounted for");

  return !commit_failed;
}

void ZPageAllocator::unmap_harvested_multi_partition(ZMultiPartitionAllocation* multi_partition_allocation) {
  for (ZMemoryAllocation* const allocation : *multi_partition_allocation->allocations()) {
    ZPartition& partition = allocation->partition();
    ZArray<ZVirtualMemory>* const partial_vmems = allocation->partial_vmems();

    // Unmap harvested vmems
    while (!partial_vmems->is_empty()) {
      const ZVirtualMemory to_unmap = partial_vmems->pop();
      partition.unmap_virtual(to_unmap);
      partition.free_virtual(to_unmap);
    }
  }
}

void ZPageAllocator::map_committed_single_partition(ZSinglePartitionAllocation* single_partition_allocation, const ZVirtualMemory& vmem) {
  ZMemoryAllocation* const allocation = single_partition_allocation->allocation();
  ZPartition& partition = allocation->partition();

  const size_t total_committed = allocation->harvested() + allocation->committed_capacity();

  if (total_committed > 0) {
    // Map all the committed memory
    const ZVirtualMemory total_committed_vmem = vmem.first_part(total_committed);
    partition.map_memory(allocation, total_committed_vmem);
  }
}

void ZPageAllocator::map_committed_multi_partition(ZMultiPartitionAllocation* multi_partition_allocation, const ZVirtualMemory& vmem) {
  ZVirtualMemory remaining = vmem;
  for (ZMemoryAllocation* const allocation : *multi_partition_allocation->allocations()) {
    assert(!allocation->commit_failed(), "Sanity check");

    ZPartition& partition = allocation->partition();

    // Split off the partial allocation's memory range
    const ZVirtualMemory to_vmem = remaining.shrink_from_front(allocation->size());

    // Map the partial_allocation to partial_vmem
    partition.map_virtual_from_multi_partition(to_vmem);
  }

  assert(remaining.size() == 0, "all memory must be accounted for");
}

void ZPageAllocator::cleanup_failed_commit_single_partition(ZSinglePartitionAllocation* single_partition_allocation, const ZVirtualMemory& vmem) {
  ZMemoryAllocation* const allocation = single_partition_allocation->allocation();

  assert(allocation->commit_failed(), "Must have failed to commit");
  assert(allocation->partial_vmems()->is_empty(), "Invariant for single partition commit failure");

  // For a single partition we have unmapped the harvested memory before we
  // started committing, and moved its physical memory association to the start
  // of the vmem. As such, the partial_vmems is empty. All the harvested and
  // partially successfully committed memory is mapped in the first part of vmem.
  const size_t harvested_and_committed_capacity = allocation->harvested() + allocation->committed_capacity();
  const ZVirtualMemory succeeded_vmem = vmem.first_part(harvested_and_committed_capacity);
  const ZVirtualMemory failed_vmem = vmem.last_part(harvested_and_committed_capacity);

  if (succeeded_vmem.size() > 0) {
    // Register the committed and mapped memory. We insert the committed
    // memory into partial_vmems so that it will be inserted into the cache
    // in a subsequent step.
    allocation->partial_vmems()->append(succeeded_vmem);
  }

  // Free the virtual and physical memory we fetched to use but failed to commit
  ZPartition& partition = allocation->partition();
  partition.free_physical(failed_vmem);
  partition.free_virtual(failed_vmem);
}

void ZPageAllocator::cleanup_failed_commit_multi_partition(ZMultiPartitionAllocation* multi_partition_allocation, const ZVirtualMemory& vmem) {
  ZVirtualMemory remaining = vmem;
  for (ZMemoryAllocation* const allocation : *multi_partition_allocation->allocations()) {
    // Split off the partial allocation's memory range
    const ZVirtualMemory partial_vmem = remaining.shrink_from_front(allocation->size());

    if (allocation->harvested() == allocation->size()) {
      // Everything is harvested, the mappings are already in the partial_vmems,
      // nothing to cleanup.
      continue;
    }

    const size_t committed = allocation->committed_capacity();
    const ZVirtualMemory non_harvested_vmem = partial_vmem.last_part(allocation->harvested());
    const ZVirtualMemory committed_vmem = non_harvested_vmem.first_part(committed);
    const ZVirtualMemory non_committed_vmem = non_harvested_vmem.last_part(committed);

    ZPartition& partition = allocation->partition();

    if (allocation->commit_failed()) {
      // Free the physical memory we failed to commit. Virtual memory is later
      // freed for the entire multi-partition allocation after all memory
      // allocations have been visited.
      partition.free_physical(non_committed_vmem);
    }

    if (committed_vmem.size() == 0) {
      // Nothing committed, nothing more to cleanup
      continue;
    }

    ZArray<ZVirtualMemory>* const partial_vmems = allocation->partial_vmems();

    // Keep track of the start index
    const int start_index = partial_vmems->length();

    // Claim virtual memory for the committed part
    const size_t claimed_virtual = partition.claim_virtual(committed, partial_vmems);

    // We are holding memory associated with this partition, and we do not
    // overcommit virtual memory claiming. So virtual memory must always be
    // available.
    assert(claimed_virtual == committed, "must succeed");

    // Associate and map the physical memory with the partial vmems

    ZVirtualMemory remaining_committed_vmem = committed_vmem;
    for (const ZVirtualMemory& to_vmem : partial_vmems->slice_back(start_index)) {
      const ZVirtualMemory from_vmem = remaining_committed_vmem.shrink_from_front(to_vmem.size());

      // Copy physical mappings
      partition.copy_physical_segments_to_partition(to_vmem, from_vmem);

      // Map memory
      partition.map_virtual(to_vmem);
    }

    assert(remaining_committed_vmem.size() == 0, "all memory must be accounted for");
  }

  assert(remaining.size() == 0, "all memory must be accounted for");

  // Free the unused virtual memory
  _virtual.insert_multi_partition(vmem);
}

void ZPageAllocator::free_after_alloc_page_failed(ZPageAllocation* allocation) {
  // Send event for failed allocation
  allocation->send_event(false /* successful */);

  ZLocker<ZLock> locker(&_lock);

  // Free memory
  free_memory_alloc_failed(allocation);

  // Try not to commit too much again
  truncate_heuristic_max_after_capacity_decrease(TruncationReason::CommitFailure);

  // Keep track of usage
  decrease_used(allocation->size());

  // Reset allocation for a potential retry
  allocation->reset_for_retry();

  // Try satisfy stalled allocations
  satisfy_stalled();
}

void ZPageAllocator::free_memory_alloc_failed(ZPageAllocation* allocation) {
  if (allocation->is_multi_partition()) {
    free_memory_alloc_failed_multi_partition(allocation->multi_partition_allocation());
  } else {
    free_memory_alloc_failed_single_partition(allocation->single_partition_allocation());
  }
}

void ZPageAllocator::free_memory_alloc_failed_single_partition(ZSinglePartitionAllocation* single_partition_allocation) {
  free_memory_alloc_failed(single_partition_allocation->allocation());
}

void ZPageAllocator::free_memory_alloc_failed_multi_partition(ZMultiPartitionAllocation* multi_partition_allocation) {
  for (ZMemoryAllocation* allocation : *multi_partition_allocation->allocations()) {
    free_memory_alloc_failed(allocation);
  }
}

void ZPageAllocator::free_memory_alloc_failed(ZMemoryAllocation* allocation) {
  ZPartition& partition = allocation->partition();

  partition.free_memory_alloc_failed(allocation);
}

ZPage* ZPageAllocator::create_page(ZPageAllocation* allocation, const ZVirtualMemory& vmem) {
  assert(allocation->size() == vmem.size(), "Must be %zu == %zu", allocation->size(), vmem.size());

  // We don't track generation usage when claiming capacity, because this page
  // could have been allocated by a thread that satisfies a stalling allocation.
  // The stalled thread can wake up and potentially realize that the page alloc
  // should be undone. If the alloc and the undo gets separated by a safepoint,
  // the generation statistics could se a decreasing used value between mark
  // start and mark end. At this point an allocation will be successful, so we
  // update the generation usage.
  const ZGenerationId id = allocation->age() == ZPageAge::old ? ZGenerationId::old : ZGenerationId::young;
  increase_used_generation(id, allocation->size());

  const ZPageType type = allocation->type();
  const ZPageAge age = allocation->age();

  if (allocation->is_multi_partition()) {
    const ZMultiPartitionAllocation* const multi_partition_allocation = allocation->multi_partition_allocation();
    ZMultiPartitionTracker* const tracker = ZMultiPartitionTracker::create(multi_partition_allocation, vmem);

    return new ZPage(type, age, vmem, tracker);
  }

  const ZSinglePartitionAllocation* const single_partition_allocation = allocation->single_partition_allocation();
  const uint32_t partition_id = single_partition_allocation->allocation()->partition().numa_id();

  return new ZPage(type, age, vmem, partition_id);
}

void ZPageAllocator::prepare_memory_for_free(ZPage* page, ZArray<ZVirtualMemory>* vmems) {
  // Extract memory and destroy the page
  const ZVirtualMemory vmem = page->virtual_memory();
  const ZPageType page_type = page->type();
  const ZMultiPartitionTracker* const tracker = page->multi_partition_tracker();

  safe_destroy_page(page);

  // Multi-partition memory is always remapped
  if (tracker != nullptr) {
    tracker->prepare_memory_for_free(vmem, vmems);

    // Free the virtual memory
    _virtual.insert_multi_partition(vmem);

    // Destroy the tracker
    ZMultiPartitionTracker::destroy(tracker);
    return;
  }

  // Try to remap and defragment if page is large
  if (page_type == ZPageType::large) {
    remap_and_defragment(vmem, vmems);
    return;
  }

  // Leave the memory untouched
  vmems->append(vmem);
}

void ZPageAllocator::remap_and_defragment(const ZVirtualMemory& vmem, ZArray<ZVirtualMemory>* vmems_out) {
  ZPartition& partition = partition_from_vmem(vmem);

  // If no lower address can be found, don't remap/defrag
  if (_virtual.lowest_available_address(partition.numa_id()) > vmem.start()) {
    vmems_out->append(vmem);
    return;
  }

  ZStatInc(ZCounterDefragment);

  // Synchronously unmap the virtual memory
  partition.unmap_virtual(vmem);

  // Stash segments
  ZArray<zbacking_index> stash(vmem.granule_count());
  _physical.stash_segments(vmem, &stash);

  // Shuffle vmem - put new vmems in vmems_out
  const int start_index = vmems_out->length();
  partition.free_and_claim_virtual_from_low_many(vmem, vmems_out);

  // The output array may contain results from other defragmentations as well,
  // so we only operate on the result(s) we just got.
  ZArraySlice<ZVirtualMemory> defragmented_vmems = vmems_out->slice_back(start_index);

  // Restore segments
  _physical.restore_segments(defragmented_vmems, stash);

  // Map and pre-touch
  for (const ZVirtualMemory& claimed_vmem : defragmented_vmems) {
    partition.map_virtual(claimed_vmem);
    pretouch_memory(claimed_vmem.start(), claimed_vmem.size());
  }
}

void ZPageAllocator::free_used_memory(ZArray<ZVirtualMemory>* vmems) {
  ZLocker<ZLock> locker(&_lock);

  // Free the vmems
  for (const ZVirtualMemory vmem : *vmems) {
    ZPartition& partition = partition_from_vmem(vmem);

    // Free the used vmem
    partition.free_used_memory(vmem);

    // Keep track of usage
    decrease_used(vmem.size());
  }

  // Try satisfy stalled allocations
  satisfy_stalled();
}

void ZPageAllocator::free_claimed_memory(ZArray<ZVirtualMemory>* vmems) {
  ZLocker<ZLock> locker(&_lock);

  // Free the vmems
  for (const ZVirtualMemory vmem : *vmems) {
    ZPartition& partition = partition_from_vmem(vmem);

    // Free the claimed vmem
    partition.free_claimed_memory(vmem);
  }

  // Try satisfy stalled allocations
  satisfy_stalled();
}

void ZPageAllocator::satisfy_stalled() {
  for (;;) {
    ZPageAllocation* const allocation = _stalled.first();
    if (allocation == nullptr) {
      // Allocation queue is empty
      return;
    }

    if (!claim_capacity(allocation, ZPageAllocationAttempt::stall)) {
      // Allocation could not be satisfied, give up
      return;
    }

    // Keep track of usage
    increase_used(allocation->size());

    // Allocation succeeded, dequeue and satisfy allocation request.
    // Note that we must dequeue the allocation request first, since
    // it will immediately be deallocated once it has been satisfied.
    _stalled.remove(allocation);
    allocation->satisfy(true);
  }
}

bool ZPageAllocator::is_multi_partition_enabled() const {
  return _virtual.is_multi_partition_enabled();
}

bool ZPageAllocator::is_multi_partition_allowed(const ZPageAllocation* allocation, ZPageAllocationAttempt attempt, size_t total_capacity_limit) const {
  return is_multi_partition_enabled() &&
         allocation->type() == ZPageType::large &&
         allocation->size() <= sum_available(attempt, total_capacity_limit);
}

const ZPartition& ZPageAllocator::partition_from_partition_id(uint32_t numa_id) const {
  return _partitions.get(numa_id);
}

ZPartition& ZPageAllocator::partition_from_partition_id(uint32_t numa_id) {
  return _partitions.get(numa_id);
}

ZPartition& ZPageAllocator::partition_from_vmem(const ZVirtualMemory& vmem) {
  return partition_from_partition_id(_virtual.lookup_partition_id(vmem));
}

size_t ZPageAllocator::sum_available(ZPageAllocationAttempt attempt, size_t total_capacity_limit) const {
  size_t total = 0;

  ZPartitionConstIterator iter = partition_iterator();
  for (const ZPartition* partition; iter.next(&partition);) {
    if (total_capacity_limit <= total) {
      // The limit smaller than the total, we will have
      return total_capacity_limit;
    }

    const size_t partition_capacity_limit = MIN2(partition->static_max_capacity(), total_capacity_limit - total);
    total += partition->available(attempt, partition_capacity_limit);
  }

  return total;
}

void ZPageAllocator::increase_used(size_t size) {
  // Update atomically since we have concurrent readers
  const size_t used = AtomicAccess::add(&_used, size);

  // Update used high
  for (auto& stats : _collection_stats) {
    if (used > stats._used_high) {
      stats._used_high = used;
    }
  }
}

void ZPageAllocator::decrease_used(size_t size) {
  // Update atomically since we have concurrent readers
  const size_t used = AtomicAccess::sub(&_used, size);

  // Update used low
  for (auto& stats : _collection_stats) {
    if (used < stats._used_low) {
      stats._used_low = used;
    }
  }
}

void ZPageAllocator::safe_destroy_page(ZPage* page) {
  // Destroy page safely
  _safe_destroy.schedule_delete(page);
}

void ZPageAllocator::free_page(ZPage* page) {
  // Extract the id from the page
  const ZGenerationId id = page->generation_id();
  const size_t size = page->size();

  // Extract vmems and destroy the page
  ZArray<ZVirtualMemory> vmems;
  prepare_memory_for_free(page, &vmems);

  // Updated used statistics
  decrease_used_generation(id, size);

  // Free the extracted vmems
  free_used_memory(&vmems);
}

void ZPageAllocator::free_pages(ZGenerationId id, const ZArray<ZPage*>* pages) {
  // Prepare memory from pages to be cached
  ZArray<ZVirtualMemory> vmems;
  for (ZPage* page : *pages) {
    assert(page->generation_id() == id, "All pages must be from the same generation");
    const size_t size = page->size();

    // Extract vmems and destroy the page
    prepare_memory_for_free(page, &vmems);

    // Updated used statistics
    decrease_used_generation(id, size);
  }

  // Free the extracted vmems
  free_used_memory(&vmems);
}

void ZPageAllocator::enable_safe_destroy() const {
  _safe_destroy.enable_deferred_delete();
}

void ZPageAllocator::disable_safe_destroy() const {
  _safe_destroy.disable_deferred_delete();
}

static bool has_alloc_seen_young(const ZPageAllocation* allocation) {
  return allocation->young_seqnum() != ZGeneration::young()->seqnum();
}

static bool has_alloc_seen_old(const ZPageAllocation* allocation) {
  return allocation->old_seqnum() != ZGeneration::old()->seqnum();
}

bool ZPageAllocator::is_alloc_stalling() const {
  ZLocker<ZLock> locker(&_lock);
  return _stalled.first() != nullptr;
}

bool ZPageAllocator::is_alloc_stalling_for_old() const {
  ZLocker<ZLock> locker(&_lock);

  ZPageAllocation* const allocation = _stalled.first();
  if (allocation == nullptr) {
    // No stalled allocations
    return false;
  }

  return has_alloc_seen_young(allocation) && !has_alloc_seen_old(allocation);
}

void ZPageAllocator::notify_out_of_memory() {
  // Fail allocation requests that were enqueued before the last major GC started
  for (ZPageAllocation* allocation = _stalled.first(); allocation != nullptr; allocation = _stalled.first()) {
    if (!has_alloc_seen_old(allocation)) {
      // Not out of memory, keep remaining allocation requests enqueued
      return;
    }

    // Out of memory, dequeue and fail allocation request
    _stalled.remove(allocation);
    allocation->satisfy(false);
  }
}

void ZPageAllocator::restart_gc() const {
  ZPageAllocation* const allocation = _stalled.first();
  if (allocation == nullptr) {
    // No stalled allocations
    return;
  }

  if (!has_alloc_seen_young(allocation)) {
    // Start asynchronous minor GC, keep allocation requests enqueued
    const ZDriverRequest request(GCCause::_z_allocation_stall, ZYoungGCThreads, 0);
    ZDriver::minor()->collect(request);
  } else {
    // Start asynchronous major GC, keep allocation requests enqueued
    const ZDriverRequest request(GCCause::_z_allocation_stall, ZYoungGCThreads, ZOldGCThreads);
    ZDriver::major()->collect(request);
  }
}

void ZPageAllocator::handle_alloc_stalling_for_young() {
  ZLocker<ZLock> locker(&_lock);
  restart_gc();
}

void ZPageAllocator::handle_alloc_stalling_for_old(bool cleared_all_soft_refs) {
  ZLocker<ZLock> locker(&_lock);
  if (cleared_all_soft_refs) {
    notify_out_of_memory();
  }
  restart_gc();
}

ZPartitionConstIterator ZPageAllocator::partition_iterator() const {
  return ZPartitionConstIterator(&_partitions);
}

ZPartitionIterator ZPageAllocator::partition_iterator() {
  return ZPartitionIterator(&_partitions);
}

void ZPageAllocator::threads_do(ThreadClosure* tc) const {
  ZPartitionConstIterator iter = partition_iterator();
  for (const ZPartition* partition; iter.next(&partition);) {
    partition->threads_do(tc);
  }
}

static bool try_lock_on_error(ZLock* lock) {
  if (VMError::is_error_reported() && VMError::is_error_reported_in_current_thread()) {
    return lock->try_lock();
  }

  lock->lock();

  return true;
}

void ZPageAllocator::print_usage_on(outputStream* st) const {
  const bool locked = try_lock_on_error(&_lock);

  if (!locked) {
    st->print_cr("<Without lock>");
  }

  // Print information even though we may not have successfully taken the lock.
  // This is thread-safe, but may produce inconsistent results.

  print_total_usage_on(st);

  StreamIndentor si(st, 1);
  print_partition_usage_on(st);

  if (locked) {
    _lock.unlock();
  }
}

void ZPageAllocator::print_total_usage_on(outputStream* st) const {
  st->print("ZHeap ");
  st->fill_to(17);
  st->print_cr("used %zuM, capacity %zuM, max capacity %zuM",
               used() / M, capacity() / M, dynamic_max_capacity() / M);
}

void ZPageAllocator::print_partition_usage_on(outputStream* st) const {
  if (_partitions.count() == 1) {
    // Partition usage is redundant if we only have one partition. Only
    // print the cache.
    _partitions.get(0).print_cache_on(st);
    return;
  }

  // Print all partitions
  ZPartitionConstIterator iter = partition_iterator();
  for (const ZPartition* partition; iter.next(&partition);) {
    partition->print_on(st);
  }
}

void ZPageAllocator::print_cache_extended_on(outputStream* st) const {
  st->print_cr("ZMappedCache:");

  StreamIndentor si(st, 1);

  if (!try_lock_on_error(&_lock)) {
    // We can't print without taking the lock since printing the contents of
    // the cache requires iterating over the nodes in the cache's tree, which
    // is not thread-safe.
    st->print_cr("<Skipped>");

    return;
  }

  // Print each partition's cache content
  ZPartitionConstIterator iter = partition_iterator();
  for (const ZPartition* partition; iter.next(&partition);) {
    partition->print_cache_extended_on(st);
  }

  _lock.unlock();
}
