/*
 * Copyright (c) 2001, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "classfile/classLoaderData.hpp"
#include "classfile/classLoaderDataGraph.hpp"
#include "cppstdlib/new.hpp"
#include "gc/g1/g1BarrierSet.hpp"
#include "gc/g1/g1BatchedTask.hpp"
#include "gc/g1/g1CardSetMemory.hpp"
#include "gc/g1/g1CardTableClaimTable.inline.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1CollectionSetChooser.hpp"
#include "gc/g1/g1CollectorState.hpp"
#include "gc/g1/g1ConcurrentMark.inline.hpp"
#include "gc/g1/g1ConcurrentMarkRemarkTasks.hpp"
#include "gc/g1/g1ConcurrentMarkThread.inline.hpp"
#include "gc/g1/g1ConcurrentRebuildAndScrub.hpp"
#include "gc/g1/g1ConcurrentRefine.hpp"
#include "gc/g1/g1HeapRegion.inline.hpp"
#include "gc/g1/g1HeapRegionManager.hpp"
#include "gc/g1/g1HeapRegionPrinter.hpp"
#include "gc/g1/g1HeapRegionRemSet.inline.hpp"
#include "gc/g1/g1HeapRegionSet.inline.hpp"
#include "gc/g1/g1HeapVerifier.hpp"
#include "gc/g1/g1OopClosures.inline.hpp"
#include "gc/g1/g1Policy.hpp"
#include "gc/g1/g1RegionMarkStatsCache.inline.hpp"
#include "gc/g1/g1ThreadLocalData.hpp"
#include "gc/g1/g1Trace.hpp"
#include "gc/shared/gcId.hpp"
#include "gc/shared/gcTimer.hpp"
#include "gc/shared/gcTraceTime.inline.hpp"
#include "gc/shared/gcVMOperations.hpp"
#include "gc/shared/partialArraySplitter.inline.hpp"
#include "gc/shared/partialArrayState.hpp"
#include "gc/shared/partialArrayTaskStats.hpp"
#include "gc/shared/referencePolicy.hpp"
#include "gc/shared/suspendibleThreadSet.hpp"
#include "gc/shared/taskqueue.inline.hpp"
#include "gc/shared/taskTerminator.hpp"
#include "gc/shared/weakProcessor.inline.hpp"
#include "gc/shared/workerPolicy.hpp"
#include "jvm.h"
#include "logging/log.hpp"
#include "memory/allocation.hpp"
#include "memory/iterator.hpp"
#include "memory/metaspaceUtils.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "nmt/memTracker.hpp"
#include "oops/access.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/java.hpp"
#include "runtime/orderAccess.hpp"
#include "runtime/os.hpp"
#include "runtime/prefetch.inline.hpp"
#include "runtime/threads.hpp"
#include "utilities/align.hpp"
#include "utilities/checkedCast.hpp"
#include "utilities/formatBuffer.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/powerOfTwo.hpp"

G1CMIsAliveClosure::G1CMIsAliveClosure() : _cm(nullptr) { }

G1CMIsAliveClosure::G1CMIsAliveClosure(G1ConcurrentMark* cm) : _cm(cm) {
  assert(cm != nullptr, "must be");
}

void G1CMIsAliveClosure::initialize(G1ConcurrentMark* cm) {
  assert(cm != nullptr, "must be");
  assert(_cm == nullptr, "double initialize");
  _cm = cm;
}

bool G1CMBitMapClosure::do_addr(HeapWord* const addr) {
  assert(addr < _cm->finger(), "invariant");
  assert(addr >= _task->finger(), "invariant");

  // We move that task's local finger along.
  _task->move_finger_to(addr);

  _task->process_entry(G1TaskQueueEntry(cast_to_oop(addr)), false /* stolen */);
  // we only partially drain the local queue and global stack
  _task->drain_local_queue(true);
  _task->drain_global_stack(true);

  // if the has_aborted flag has been raised, we need to bail out of
  // the iteration
  return !_task->has_aborted();
}

G1CMMarkStack::G1CMMarkStack() :
  _chunk_allocator() {
  set_empty();
}

size_t G1CMMarkStack::capacity_alignment() {
  return (size_t)lcm(os::vm_allocation_granularity(), sizeof(TaskQueueEntryChunk)) / sizeof(G1TaskQueueEntry);
}

bool G1CMMarkStack::initialize() {
  guarantee(_chunk_allocator.capacity() == 0, "G1CMMarkStack already initialized.");

  size_t initial_capacity = MarkStackSize;
  size_t max_capacity = MarkStackSizeMax;

  size_t const TaskEntryChunkSizeInVoidStar = sizeof(TaskQueueEntryChunk) / sizeof(G1TaskQueueEntry);

  size_t max_num_chunks = align_up(max_capacity, capacity_alignment()) / TaskEntryChunkSizeInVoidStar;
  size_t initial_num_chunks = align_up(initial_capacity, capacity_alignment()) / TaskEntryChunkSizeInVoidStar;

  initial_num_chunks = round_up_power_of_2(initial_num_chunks);
  max_num_chunks = MAX2(initial_num_chunks, max_num_chunks);

  size_t limit = (INT_MAX - 1);
  max_capacity = MIN2((max_num_chunks * TaskEntryChunkSizeInVoidStar), limit);
  initial_capacity = MIN2((initial_num_chunks * TaskEntryChunkSizeInVoidStar), limit);

  FLAG_SET_ERGO(MarkStackSizeMax, max_capacity);
  FLAG_SET_ERGO(MarkStackSize, initial_capacity);

  log_trace(gc)("MarkStackSize: %uk  MarkStackSizeMax: %uk", (uint)(MarkStackSize / K), (uint)(MarkStackSizeMax / K));

  log_debug(gc)("Initialize mark stack with %zu chunks, maximum %zu",
                initial_num_chunks, max_capacity);

  return _chunk_allocator.initialize(initial_num_chunks, max_num_chunks);
}

G1CMMarkStack::TaskQueueEntryChunk* G1CMMarkStack::ChunkAllocator::allocate_new_chunk() {
  if (_size.load_relaxed() >= _max_capacity) {
    return nullptr;
  }

  size_t cur_idx = _size.fetch_then_add(1u);

  if (cur_idx >= _max_capacity) {
    return nullptr;
  }

  size_t bucket = get_bucket(cur_idx);
  if (_buckets[bucket].load_acquire() == nullptr) {
    if (!_should_grow) {
      // Prefer to restart the CM.
      return nullptr;
    }

    MutexLocker x(G1MarkStackChunkList_lock, Mutex::_no_safepoint_check_flag);
    if (_buckets[bucket].load_acquire() == nullptr) {
      size_t desired_capacity = bucket_size(bucket) * 2;
      if (!try_expand_to(desired_capacity)) {
        return nullptr;
      }
    }
  }

  size_t bucket_idx = get_bucket_index(cur_idx);
  TaskQueueEntryChunk* result = ::new (&_buckets[bucket].load_relaxed()[bucket_idx]) TaskQueueEntryChunk;
  result->next = nullptr;
  return result;
}

G1CMMarkStack::ChunkAllocator::ChunkAllocator() :
  _min_capacity(0),
  _max_capacity(0),
  _capacity(0),
  _num_buckets(0),
  _should_grow(false),
  _buckets(nullptr),
  _size(0)
{ }

bool G1CMMarkStack::ChunkAllocator::initialize(size_t initial_capacity, size_t max_capacity) {
  guarantee(is_power_of_2(initial_capacity), "Invalid initial_capacity");

  _min_capacity = initial_capacity;
  _max_capacity = max_capacity;
  _num_buckets  = get_bucket(_max_capacity) + 1;

  _buckets = NEW_C_HEAP_ARRAY(Atomic<TaskQueueEntryChunk*>, _num_buckets, mtGC);

  for (size_t i = 0; i < _num_buckets; i++) {
    _buckets[i].store_relaxed(nullptr);
  }

  size_t new_capacity = bucket_size(0);

  if (!reserve(new_capacity)) {
    log_warning(gc)("Failed to reserve memory for new overflow mark stack with %zu chunks and size %zuB.", new_capacity, new_capacity * sizeof(TaskQueueEntryChunk));
    return false;
  }
  return true;
}

bool G1CMMarkStack::ChunkAllocator::try_expand_to(size_t desired_capacity) {
  if (_capacity == _max_capacity) {
    log_debug(gc)("Can not expand overflow mark stack further, already at maximum capacity of %zu chunks.", _capacity);
    return false;
  }

  size_t old_capacity = _capacity;
  desired_capacity = MIN2(desired_capacity, _max_capacity);

  if (reserve(desired_capacity)) {
    log_debug(gc)("Expanded the mark stack capacity from %zu to %zu chunks",
                  old_capacity, desired_capacity);
    return true;
  }
  return false;
}

bool G1CMMarkStack::ChunkAllocator::try_expand() {
  size_t new_capacity = _capacity * 2;
  return try_expand_to(new_capacity);
}

G1CMMarkStack::ChunkAllocator::~ChunkAllocator() {
  if (_buckets == nullptr) {
    return;
  }

  for (size_t i = 0; i < _num_buckets; i++) {
    if (_buckets[i].load_relaxed() != nullptr) {
      MmapArrayAllocator<TaskQueueEntryChunk>::free(_buckets[i].load_relaxed(),  bucket_size(i));
      _buckets[i].store_relaxed(nullptr);
    }
  }

  FREE_C_HEAP_ARRAY(TaskQueueEntryChunk*, _buckets);
}

bool G1CMMarkStack::ChunkAllocator::reserve(size_t new_capacity) {
  assert(new_capacity <= _max_capacity, "Cannot expand overflow mark stack beyond the max_capacity of %zu chunks.", _max_capacity);

  size_t highest_bucket = get_bucket(new_capacity - 1);
  size_t i = get_bucket(_capacity);

  // Allocate all buckets associated with indexes between the current capacity (_capacity)
  // and the new capacity (new_capacity). This step ensures that there are no gaps in the
  // array and that the capacity accurately reflects the reserved memory.
  for (; i <= highest_bucket; i++) {
    if (_buckets[i].load_acquire() != nullptr) {
      continue; // Skip over already allocated buckets.
    }

    size_t bucket_capacity = bucket_size(i);

    // Trim bucket size so that we do not exceed the _max_capacity.
    bucket_capacity = (_capacity + bucket_capacity) <= _max_capacity ?
                      bucket_capacity :
                      _max_capacity - _capacity;


    TaskQueueEntryChunk* bucket_base = MmapArrayAllocator<TaskQueueEntryChunk>::allocate_or_null(bucket_capacity, mtGC);

    if (bucket_base == nullptr) {
      log_warning(gc)("Failed to reserve memory for increasing the overflow mark stack capacity with %zu chunks and size %zuB.",
                      bucket_capacity, bucket_capacity * sizeof(TaskQueueEntryChunk));
      return false;
    }
    _capacity += bucket_capacity;
    _buckets[i].release_store(bucket_base);
  }
  return true;
}

void G1CMMarkStack::expand() {
  _chunk_allocator.try_expand();
}

void G1CMMarkStack::add_chunk_to_list(Atomic<TaskQueueEntryChunk*>* list, TaskQueueEntryChunk* elem) {
  elem->next = list->load_relaxed();
  list->store_relaxed(elem);
}

void G1CMMarkStack::add_chunk_to_chunk_list(TaskQueueEntryChunk* elem) {
  MutexLocker x(G1MarkStackChunkList_lock, Mutex::_no_safepoint_check_flag);
  add_chunk_to_list(&_chunk_list, elem);
  _chunks_in_chunk_list++;
}

void G1CMMarkStack::add_chunk_to_free_list(TaskQueueEntryChunk* elem) {
  MutexLocker x(G1MarkStackFreeList_lock, Mutex::_no_safepoint_check_flag);
  add_chunk_to_list(&_free_list, elem);
}

G1CMMarkStack::TaskQueueEntryChunk* G1CMMarkStack::remove_chunk_from_list(Atomic<TaskQueueEntryChunk*>* list) {
  TaskQueueEntryChunk* result = list->load_relaxed();
  if (result != nullptr) {
    list->store_relaxed(list->load_relaxed()->next);
  }
  return result;
}

G1CMMarkStack::TaskQueueEntryChunk* G1CMMarkStack::remove_chunk_from_chunk_list() {
  MutexLocker x(G1MarkStackChunkList_lock, Mutex::_no_safepoint_check_flag);
  TaskQueueEntryChunk* result = remove_chunk_from_list(&_chunk_list);
  if (result != nullptr) {
    _chunks_in_chunk_list--;
  }
  return result;
}

G1CMMarkStack::TaskQueueEntryChunk* G1CMMarkStack::remove_chunk_from_free_list() {
  MutexLocker x(G1MarkStackFreeList_lock, Mutex::_no_safepoint_check_flag);
  return remove_chunk_from_list(&_free_list);
}

bool G1CMMarkStack::par_push_chunk(G1TaskQueueEntry* ptr_arr) {
  // Get a new chunk.
  TaskQueueEntryChunk* new_chunk = remove_chunk_from_free_list();

  if (new_chunk == nullptr) {
    // Did not get a chunk from the free list. Allocate from backing memory.
    new_chunk = _chunk_allocator.allocate_new_chunk();

    if (new_chunk == nullptr) {
      return false;
    }
  }

  Copy::conjoint_memory_atomic(ptr_arr, new_chunk->data, EntriesPerChunk * sizeof(G1TaskQueueEntry));

  add_chunk_to_chunk_list(new_chunk);

  return true;
}

bool G1CMMarkStack::par_pop_chunk(G1TaskQueueEntry* ptr_arr) {
  TaskQueueEntryChunk* cur = remove_chunk_from_chunk_list();

  if (cur == nullptr) {
    return false;
  }

  Copy::conjoint_memory_atomic(cur->data, ptr_arr, EntriesPerChunk * sizeof(G1TaskQueueEntry));

  add_chunk_to_free_list(cur);
  return true;
}

void G1CMMarkStack::set_empty() {
  _chunks_in_chunk_list = 0;
  _chunk_list.store_relaxed(nullptr);
  _free_list.store_relaxed(nullptr);
  _chunk_allocator.reset();
}

G1CMRootMemRegions::G1CMRootMemRegions(uint const max_regions) :
    _root_regions(MemRegion::create_array(max_regions, mtGC)),
    _max_regions(max_regions),
    _num_root_regions(0),
    _claimed_root_regions(0),
    _scan_in_progress(false),
    _should_abort(false) { }

G1CMRootMemRegions::~G1CMRootMemRegions() {
  MemRegion::destroy_array(_root_regions, _max_regions);
}

void G1CMRootMemRegions::reset() {
  _num_root_regions.store_relaxed(0);
}

void G1CMRootMemRegions::add(HeapWord* start, HeapWord* end) {
  assert_at_safepoint();
  size_t idx = _num_root_regions.fetch_then_add(1u);
  assert(idx < _max_regions, "Trying to add more root MemRegions than there is space %zu", _max_regions);
  assert(start != nullptr && end != nullptr && start <= end, "Start (" PTR_FORMAT ") should be less or equal to "
         "end (" PTR_FORMAT ")", p2i(start), p2i(end));
  _root_regions[idx].set_start(start);
  _root_regions[idx].set_end(end);
}

void G1CMRootMemRegions::prepare_for_scan() {
  assert(!scan_in_progress(), "pre-condition");

  _scan_in_progress.store_relaxed(num_root_regions() > 0);

  _claimed_root_regions.store_relaxed(0);
  _should_abort.store_relaxed(false);
}

const MemRegion* G1CMRootMemRegions::claim_next() {
  if (_should_abort.load_relaxed()) {
    // If someone has set the should_abort flag, we return null to
    // force the caller to bail out of their loop.
    return nullptr;
  }

  uint local_num_root_regions = num_root_regions();
  if (_claimed_root_regions.load_relaxed() >= local_num_root_regions) {
    return nullptr;
  }

  size_t claimed_index = _claimed_root_regions.fetch_then_add(1u);
  if (claimed_index < local_num_root_regions) {
    return &_root_regions[claimed_index];
  }
  return nullptr;
}

uint G1CMRootMemRegions::num_root_regions() const {
  return (uint)_num_root_regions.load_relaxed();
}

bool G1CMRootMemRegions::contains(const MemRegion mr) const {
  uint local_num_root_regions = num_root_regions();
  for (uint i = 0; i < local_num_root_regions; i++) {
    if (_root_regions[i].equals(mr)) {
      return true;
    }
  }
  return false;
}

void G1CMRootMemRegions::notify_scan_done() {
  MutexLocker x(G1RootRegionScan_lock, Mutex::_no_safepoint_check_flag);
  _scan_in_progress.store_relaxed(false);
  G1RootRegionScan_lock->notify_all();
}

void G1CMRootMemRegions::cancel_scan() {
  notify_scan_done();
}

void G1CMRootMemRegions::scan_finished() {
  assert(scan_in_progress(), "pre-condition");

  if (!_should_abort.load_relaxed()) {
    assert(_claimed_root_regions.load_relaxed() >= num_root_regions(),
           "we should have claimed all root regions, claimed %zu, length = %u",
           _claimed_root_regions.load_relaxed(), num_root_regions());
  }

  notify_scan_done();
}

bool G1CMRootMemRegions::wait_until_scan_finished() {
  if (!scan_in_progress()) {
    return false;
  }

  {
    MonitorLocker ml(G1RootRegionScan_lock, Mutex::_no_safepoint_check_flag);
    while (scan_in_progress()) {
      ml.wait();
    }
  }
  return true;
}

G1ConcurrentMark::G1ConcurrentMark(G1CollectedHeap* g1h,
                                   G1RegionToSpaceMapper* bitmap_storage) :
  _cm_thread(nullptr),
  _g1h(g1h),

  _mark_bitmap(),

  _heap(_g1h->reserved()),

  _root_regions(_g1h->max_num_regions()),

  _global_mark_stack(),

  _finger(nullptr), // _finger set in set_non_marking_state

  _worker_id_offset(G1ConcRefinementThreads), // The refinement control thread does not refine cards, so it's just the worker threads.
  _max_num_tasks(MAX2(ConcGCThreads, ParallelGCThreads)),
  _num_active_tasks(0), // _num_active_tasks set in set_non_marking_state()
  _tasks(nullptr), // _tasks set inside late_init()
  _task_queues(new G1CMTaskQueueSet(_max_num_tasks)),
  _terminator(_max_num_tasks, _task_queues),
  _partial_array_state_manager(new PartialArrayStateManager(_max_num_tasks)),

  _first_overflow_barrier_sync(),
  _second_overflow_barrier_sync(),

  _completed_mark_cycles(0),
  _has_overflown(false),
  _concurrent(false),
  _has_aborted(false),
  _restart_for_overflow(false),
  _gc_timer_cm(new ConcurrentGCTimer()),
  _gc_tracer_cm(new G1OldTracer()),

  // _verbose_level set below

  _remark_times(),
  _remark_mark_times(),
  _remark_weak_ref_times(),
  _cleanup_times(),

  _concurrent_workers(nullptr),
  _num_concurrent_workers(0),
  _max_concurrent_workers(0),

  _region_mark_stats(NEW_C_HEAP_ARRAY(G1RegionMarkStats, _g1h->max_num_regions(), mtGC)),
  _top_at_mark_starts(NEW_C_HEAP_ARRAY(Atomic<HeapWord*>, _g1h->max_num_regions(), mtGC)),
  _top_at_rebuild_starts(NEW_C_HEAP_ARRAY(Atomic<HeapWord*>, _g1h->max_num_regions(), mtGC)),
  _needs_remembered_set_rebuild(false)
{
  assert(G1CGC_lock != nullptr, "CGC_lock must be initialized");

  _mark_bitmap.initialize(g1h->reserved(), bitmap_storage);
}

void G1ConcurrentMark::fully_initialize() {
  if (is_fully_initialized()) {
    return;
  }

  // Create & start ConcurrentMark thread.
  _cm_thread = new G1ConcurrentMarkThread(this);
  if (_cm_thread->osthread() == nullptr) {
    vm_shutdown_during_initialization("Could not create ConcurrentMarkThread");
  }

  log_debug(gc)("ConcGCThreads: %u offset %u", ConcGCThreads, _worker_id_offset);
  log_debug(gc)("ParallelGCThreads: %u", ParallelGCThreads);

  _max_concurrent_workers = ConcGCThreads;

  _concurrent_workers = new WorkerThreads("G1 Conc", _max_concurrent_workers);
  _concurrent_workers->initialize_workers();
  _num_concurrent_workers = _concurrent_workers->active_workers();

  if (!_global_mark_stack.initialize()) {
    vm_exit_during_initialization("Failed to allocate initial concurrent mark overflow mark stack.");
  }

  _tasks = NEW_C_HEAP_ARRAY(G1CMTask*, _max_num_tasks, mtGC);

  // so that the assertion in MarkingTaskQueue::task_queue doesn't fail
  _num_active_tasks = _max_num_tasks;

  for (uint i = 0; i < _max_num_tasks; ++i) {
    G1CMTaskQueue* task_queue = new G1CMTaskQueue();
    _task_queues->register_queue(i, task_queue);

    _tasks[i] = new G1CMTask(i, this, task_queue, _region_mark_stats);
  }

  for (uint i = 0; i < _g1h->max_num_regions(); i++) {
    ::new (&_region_mark_stats[i]) G1RegionMarkStats{};
    ::new (&_top_at_mark_starts[i]) Atomic<HeapWord*>{};
    ::new (&_top_at_rebuild_starts[i]) Atomic<HeapWord*>{};
  }

  reset_at_marking_complete();
}

bool G1ConcurrentMark::in_progress() const {
  return is_fully_initialized() ? _cm_thread->in_progress() : false;
}

PartialArrayStateManager* G1ConcurrentMark::partial_array_state_manager() const {
  return _partial_array_state_manager;
}

void G1ConcurrentMark::reset() {
  _has_aborted.store_relaxed(false);

  reset_marking_for_restart();

  // Reset all tasks, since different phases will use different number of active
  // threads. So, it's easiest to have all of them ready.
  for (uint i = 0; i < _max_num_tasks; ++i) {
    _tasks[i]->reset(mark_bitmap());
  }

  uint max_num_regions = _g1h->max_num_regions();
  for (uint i = 0; i < max_num_regions; i++) {
    _top_at_rebuild_starts[i].store_relaxed(nullptr);
    _region_mark_stats[i].clear();
  }

  _root_regions.reset();
}

void G1ConcurrentMark::clear_statistics(G1HeapRegion* r) {
  uint region_idx = r->hrm_index();
  for (uint j = 0; j < _max_num_tasks; ++j) {
    _tasks[j]->clear_mark_stats_cache(region_idx);
  }
  _top_at_rebuild_starts[region_idx].store_relaxed(nullptr);
  _region_mark_stats[region_idx].clear();
}

void G1ConcurrentMark::humongous_object_eagerly_reclaimed(G1HeapRegion* r) {
  assert_at_safepoint();
  assert(r->is_starts_humongous(), "Got humongous continues region here");

  // Need to clear mark bit of the humongous object. Doing this unconditionally is fine.
  mark_bitmap()->clear(r->bottom());

  if (!_g1h->collector_state()->mark_or_rebuild_in_progress()) {
    return;
  }

  // Clear any statistics about the region gathered so far.
  _g1h->humongous_obj_regions_iterate(r,
                                      [&] (G1HeapRegion* r) {
                                        clear_statistics(r);
                                      });
}

void G1ConcurrentMark::reset_marking_for_restart() {
  _global_mark_stack.set_empty();

  // Expand the marking stack, if we have to and if we can.
  if (has_overflown()) {
    _global_mark_stack.expand();

    uint max_num_regions = _g1h->max_num_regions();
    for (uint i = 0; i < max_num_regions; i++) {
      _region_mark_stats[i].clear_during_overflow();
    }
  }

  clear_has_overflown();
  _finger.store_relaxed(_heap.start());

  for (uint i = 0; i < _max_num_tasks; ++i) {
    _tasks[i]->reset_for_restart();
  }
}

void G1ConcurrentMark::set_concurrency(uint active_tasks) {
  assert(active_tasks <= _max_num_tasks, "we should not have more");

  _num_active_tasks = active_tasks;
  // Need to update the three data structures below according to the
  // number of active threads for this phase.
  _terminator.reset_for_reuse(active_tasks);
  _first_overflow_barrier_sync.set_n_workers(active_tasks);
  _second_overflow_barrier_sync.set_n_workers(active_tasks);
}

void G1ConcurrentMark::set_concurrency_and_phase(uint active_tasks, bool concurrent) {
  set_concurrency(active_tasks);

  _concurrent.store_relaxed(concurrent);

  if (!concurrent) {
    // At this point we should be in a STW phase, and completed marking.
    assert_at_safepoint_on_vm_thread();
    assert(out_of_regions(),
           "only way to get here: _finger: " PTR_FORMAT ", _heap_end: " PTR_FORMAT,
           p2i(finger()), p2i(_heap.end()));
  }
}

#if TASKQUEUE_STATS
void G1ConcurrentMark::print_and_reset_taskqueue_stats() {

  _task_queues->print_and_reset_taskqueue_stats("G1ConcurrentMark Oop Queue");

  auto get_pa_stats = [&](uint i) {
    return _tasks[i]->partial_array_task_stats();
  };

  PartialArrayTaskStats::log_set(_max_num_tasks, get_pa_stats,
                                 "G1ConcurrentMark Partial Array Task Stats");

  for (uint i = 0; i < _max_num_tasks; ++i) {
    get_pa_stats(i)->reset();
  }
}
#endif

void G1ConcurrentMark::reset_at_marking_complete() {
  TASKQUEUE_STATS_ONLY(print_and_reset_taskqueue_stats());
  // We set the global marking state to some default values when we're
  // not doing marking.
  reset_marking_for_restart();
  _num_active_tasks = 0;
}

G1ConcurrentMark::~G1ConcurrentMark() {
  FREE_C_HEAP_ARRAY(Atomic<HeapWord*>, _top_at_mark_starts);
  FREE_C_HEAP_ARRAY(Atomic<HeapWord*>, _top_at_rebuild_starts);
  FREE_C_HEAP_ARRAY(G1RegionMarkStats, _region_mark_stats);
  // The G1ConcurrentMark instance is never freed.
  ShouldNotReachHere();
}

class G1ClearBitMapTask : public WorkerTask {
public:
  static size_t chunk_size() { return M; }

private:
  // Heap region closure used for clearing the _mark_bitmap.
  class G1ClearBitmapHRClosure : public G1HeapRegionClosure {
  private:
    G1ConcurrentMark* _cm;
    G1CMBitMap* _bitmap;
    bool _suspendible; // If suspendible, do yield checks.

    bool suspendible() {
      return _suspendible;
    }

    bool is_clear_concurrent_undo() {
      return suspendible() && _cm->cm_thread()->in_undo_mark();
    }

    bool has_aborted() {
      if (suspendible()) {
        _cm->do_yield_check();
        return _cm->has_aborted();
      }
      return false;
    }

    HeapWord* region_clear_limit(G1HeapRegion* r) {
      // During a Concurrent Undo Mark cycle, the per region top_at_mark_start and
      // live_words data are current wrt to the _mark_bitmap. We use this information
      // to only clear ranges of the bitmap that require clearing.
      if (is_clear_concurrent_undo()) {
        // No need to clear bitmaps for empty regions (which includes regions we
        // did not mark through).
        if (!_cm->contains_live_object(r->hrm_index())) {
          assert(_bitmap->get_next_marked_addr(r->bottom(), r->end()) == r->end(), "Should not have marked bits");
          return r->bottom();
        }
        assert(_bitmap->get_next_marked_addr(_cm->top_at_mark_start(r), r->end()) == r->end(), "Should not have marked bits above tams");
      }
      return r->end();
    }

  public:
    G1ClearBitmapHRClosure(G1ConcurrentMark* cm, bool suspendible) :
      G1HeapRegionClosure(),
      _cm(cm),
      _bitmap(cm->mark_bitmap()),
      _suspendible(suspendible)
    { }

    virtual bool do_heap_region(G1HeapRegion* r) {
      if (has_aborted()) {
        return true;
      }

      HeapWord* cur = r->bottom();
      HeapWord* const end = region_clear_limit(r);

      size_t const chunk_size_in_words = G1ClearBitMapTask::chunk_size() / HeapWordSize;

      while (cur < end) {

        MemRegion mr(cur, MIN2(cur + chunk_size_in_words, end));
        _bitmap->clear_range(mr);

        cur += chunk_size_in_words;

        // Repeat the asserts from before the start of the closure. We will do them
        // as asserts here to minimize their overhead on the product. However, we
        // will have them as guarantees at the beginning / end of the bitmap
        // clearing to get some checking in the product.
        assert(!suspendible() || _cm->in_progress(), "invariant");
        assert(!suspendible() || !G1CollectedHeap::heap()->collector_state()->mark_or_rebuild_in_progress(), "invariant");

        // Abort iteration if necessary.
        if (has_aborted()) {
          return true;
        }
      }
      assert(cur >= end, "Must have completed iteration over the bitmap for region %u.", r->hrm_index());

      _cm->reset_top_at_mark_start(r);

      return false;
    }
  };

  G1ClearBitmapHRClosure _cl;
  G1HeapRegionClaimer _hr_claimer;
  bool _suspendible; // If the task is suspendible, workers must join the STS.

public:
  G1ClearBitMapTask(G1ConcurrentMark* cm, uint n_workers, bool suspendible) :
    WorkerTask("G1 Clear Bitmap"),
    _cl(cm, suspendible),
    _hr_claimer(n_workers),
    _suspendible(suspendible)
  { }

  void work(uint worker_id) {
    SuspendibleThreadSetJoiner sts_join(_suspendible);
    G1CollectedHeap::heap()->heap_region_par_iterate_from_worker_offset(&_cl, &_hr_claimer, worker_id);
  }

  bool is_complete() {
    return _cl.is_complete();
  }
};

void G1ConcurrentMark::clear_bitmap(WorkerThreads* workers, bool may_yield) {
  assert(may_yield || SafepointSynchronize::is_at_safepoint(), "Non-yielding bitmap clear only allowed at safepoint.");

  size_t const num_bytes_to_clear = (G1HeapRegion::GrainBytes * _g1h->num_committed_regions()) / G1CMBitMap::heap_map_factor();
  size_t const num_chunks = align_up(num_bytes_to_clear, G1ClearBitMapTask::chunk_size()) / G1ClearBitMapTask::chunk_size();

  uint const num_workers = (uint)MIN2(num_chunks, (size_t)workers->active_workers());

  G1ClearBitMapTask cl(this, num_workers, may_yield);

  log_debug(gc, ergo)("Running %s with %u workers for %zu work units.", cl.name(), num_workers, num_chunks);
  workers->run_task(&cl, num_workers);
  guarantee(may_yield || cl.is_complete(), "Must have completed iteration when not yielding.");
}

void G1ConcurrentMark::cleanup_for_next_mark() {
  // Make sure that the concurrent mark thread looks to still be in
  // the current cycle.
  guarantee(is_fully_initialized(), "should be initializd");
  guarantee(in_progress(), "invariant");

  // We are finishing up the current cycle by clearing the next
  // marking bitmap and getting it ready for the next cycle. During
  // this time no other cycle can start. So, let's make sure that this
  // is the case.
  guarantee(!_g1h->collector_state()->mark_or_rebuild_in_progress(), "invariant");

  clear_bitmap(_concurrent_workers, true);

  reset_partial_array_state_manager();

  // Repeat the asserts from above.
  guarantee(is_fully_initialized(), "should be initializd");
  guarantee(in_progress(), "invariant");
  guarantee(!_g1h->collector_state()->mark_or_rebuild_in_progress(), "invariant");
}

void G1ConcurrentMark::reset_partial_array_state_manager() {
  for (uint i = 0; i < _max_num_tasks; ++i) {
    _tasks[i]->unregister_partial_array_splitter();
  }

  partial_array_state_manager()->reset();

  for (uint i = 0; i < _max_num_tasks; ++i) {
    _tasks[i]->register_partial_array_splitter();
  }
}

void G1ConcurrentMark::clear_bitmap(WorkerThreads* workers) {
  assert_at_safepoint_on_vm_thread();
  // To avoid fragmentation the full collection requesting to clear the bitmap
  // might use fewer workers than available. To ensure the bitmap is cleared
  // as efficiently as possible the number of active workers are temporarily
  // increased to include all currently created workers.
  WithActiveWorkers update(workers, workers->created_workers());
  clear_bitmap(workers, false);
}

class G1PreConcurrentStartTask : public G1BatchedTask {
  // Reset marking state.
  class ResetMarkingStateTask;
  // For each region note start of marking.
  class NoteStartOfMarkTask;

public:
  G1PreConcurrentStartTask(GCCause::Cause cause, G1ConcurrentMark* cm);
};

class G1PreConcurrentStartTask::ResetMarkingStateTask : public G1AbstractSubTask {
  G1ConcurrentMark* _cm;
public:
  ResetMarkingStateTask(G1ConcurrentMark* cm) : G1AbstractSubTask(G1GCPhaseTimes::ResetMarkingState), _cm(cm) { }

  double worker_cost() const override { return 1.0; }
  void do_work(uint worker_id) override;
};

class G1PreConcurrentStartTask::NoteStartOfMarkTask : public G1AbstractSubTask {
  G1HeapRegionClaimer _claimer;
public:
  NoteStartOfMarkTask() : G1AbstractSubTask(G1GCPhaseTimes::NoteStartOfMark), _claimer(0) { }

  double worker_cost() const override {
    // The work done per region is very small, therefore we choose this magic number to cap the number
    // of threads used when there are few regions.
    const double regions_per_thread = 1000;
    return _claimer.n_regions() / regions_per_thread;
  }

  void set_max_workers(uint max_workers) override;
  void do_work(uint worker_id) override;
};

void G1PreConcurrentStartTask::ResetMarkingStateTask::do_work(uint worker_id) {
  // Reset marking state.
  _cm->reset();
}

class NoteStartOfMarkHRClosure : public G1HeapRegionClosure {
  G1ConcurrentMark* _cm;

public:
  NoteStartOfMarkHRClosure() : G1HeapRegionClosure(), _cm(G1CollectedHeap::heap()->concurrent_mark()) { }

  bool do_heap_region(G1HeapRegion* r) override {
    if (r->is_old_or_humongous() && !r->is_collection_set_candidate() && !r->in_collection_set()) {
      _cm->update_top_at_mark_start(r);
    } else {
      _cm->reset_top_at_mark_start(r);
    }
    return false;
  }
};

void G1PreConcurrentStartTask::NoteStartOfMarkTask::do_work(uint worker_id) {
  NoteStartOfMarkHRClosure start_cl;
  G1CollectedHeap::heap()->heap_region_par_iterate_from_worker_offset(&start_cl, &_claimer, worker_id);
}

void G1PreConcurrentStartTask::NoteStartOfMarkTask::set_max_workers(uint max_workers) {
  _claimer.set_n_workers(max_workers);
}

G1PreConcurrentStartTask::G1PreConcurrentStartTask(GCCause::Cause cause, G1ConcurrentMark* cm) :
  G1BatchedTask("Pre Concurrent Start", G1CollectedHeap::heap()->phase_times()) {
  add_serial_task(new ResetMarkingStateTask(cm));
  add_parallel_task(new NoteStartOfMarkTask());
};

void G1ConcurrentMark::pre_concurrent_start(GCCause::Cause cause) {
  assert_at_safepoint_on_vm_thread();

  G1CollectedHeap::start_codecache_marking_cycle_if_inactive(true /* concurrent_mark_start */);

  ClassLoaderDataGraph::verify_claimed_marks_cleared(ClassLoaderData::_claim_strong);

  G1PreConcurrentStartTask cl(cause, this);
  G1CollectedHeap::heap()->run_batch_task(&cl);

  _gc_tracer_cm->set_gc_cause(cause);
}


void G1ConcurrentMark::post_concurrent_mark_start() {
  // Start Concurrent Marking weak-reference discovery.
  ReferenceProcessor* rp = _g1h->ref_processor_cm();
  rp->start_discovery(false /* always_clear */);

  SATBMarkQueueSet& satb_mq_set = G1BarrierSet::satb_mark_queue_set();
  // This is the start of  the marking cycle, we're expected all
  // threads to have SATB queues with active set to false.
  satb_mq_set.set_active_all_threads(true, /* new active value */
                                     false /* expected_active */);

  _root_regions.prepare_for_scan();

  // update_g1_committed() will be called at the end of an evac pause
  // when marking is on. So, it's also called at the end of the
  // concurrent start pause to update the heap end, if the heap expands
  // during it. No need to call it here.
}

void G1ConcurrentMark::post_concurrent_undo_start() {
  root_regions()->cancel_scan();
}

/*
 * Notice that in the next two methods, we actually leave the STS
 * during the barrier sync and join it immediately afterwards. If we
 * do not do this, the following deadlock can occur: one thread could
 * be in the barrier sync code, waiting for the other thread to also
 * sync up, whereas another one could be trying to yield, while also
 * waiting for the other threads to sync up too.
 *
 * Note, however, that this code is also used during remark and in
 * this case we should not attempt to leave / enter the STS, otherwise
 * we'll either hit an assert (debug / fastdebug) or deadlock
 * (product). So we should only leave / enter the STS if we are
 * operating concurrently.
 *
 * Because the thread that does the sync barrier has left the STS, it
 * is possible to be suspended for a Full GC or an evacuation pause
 * could occur. This is actually safe, since the entering the sync
 * barrier is one of the last things do_marking_step() does, and it
 * doesn't manipulate any data structures afterwards.
 */

void G1ConcurrentMark::enter_first_sync_barrier(uint worker_id) {
  bool barrier_aborted;
  {
    SuspendibleThreadSetLeaver sts_leave(concurrent());
    barrier_aborted = !_first_overflow_barrier_sync.enter();
  }

  // at this point everyone should have synced up and not be doing any
  // more work

  if (barrier_aborted) {
    // If the barrier aborted we ignore the overflow condition and
    // just abort the whole marking phase as quickly as possible.
    return;
  }
}

void G1ConcurrentMark::enter_second_sync_barrier(uint worker_id) {
  SuspendibleThreadSetLeaver sts_leave(concurrent());
  _second_overflow_barrier_sync.enter();

  // at this point everything should be re-initialized and ready to go
}

class G1CMConcurrentMarkingTask : public WorkerTask {
  G1ConcurrentMark*     _cm;

public:
  void work(uint worker_id) {
    ResourceMark rm;

    SuspendibleThreadSetJoiner sts_join;

    assert(worker_id < _cm->active_tasks(), "invariant");

    G1CMTask* task = _cm->task(worker_id);
    task->record_start_time();
    if (!_cm->has_aborted()) {
      do {
        task->do_marking_step(G1ConcMarkStepDurationMillis,
                              true  /* do_termination */,
                              false /* is_serial*/);

        _cm->do_yield_check();
      } while (!_cm->has_aborted() && task->has_aborted());
    }
    task->record_end_time();
    guarantee(!task->has_aborted() || _cm->has_aborted(), "invariant");
  }

  G1CMConcurrentMarkingTask(G1ConcurrentMark* cm) :
      WorkerTask("Concurrent Mark"), _cm(cm) { }

  ~G1CMConcurrentMarkingTask() { }
};

uint G1ConcurrentMark::calc_active_marking_workers() {
  uint result = 0;
  if (!UseDynamicNumberOfGCThreads || !FLAG_IS_DEFAULT(ConcGCThreads)) {
    result = _max_concurrent_workers;
  } else {
    result =
      WorkerPolicy::calc_default_active_workers(_max_concurrent_workers,
                                                1, /* Minimum workers */
                                                _num_concurrent_workers,
                                                Threads::number_of_non_daemon_threads());
    // Don't scale the result down by scale_concurrent_workers() because
    // that scaling has already gone into "_max_concurrent_workers".
  }
  assert(result > 0 && result <= _max_concurrent_workers,
         "Calculated number of marking workers must be larger than zero and at most the maximum %u, but is %u",
         _max_concurrent_workers, result);
  return result;
}

void G1ConcurrentMark::scan_root_region(const MemRegion* region, uint worker_id) {
#ifdef ASSERT
  HeapWord* last = region->last();
  G1HeapRegion* hr = _g1h->heap_region_containing(last);
  assert(hr->is_old() || top_at_mark_start(hr) == hr->bottom(),
         "Root regions must be old or survivor/eden but region %u is %s", hr->hrm_index(), hr->get_type_str());
  assert(top_at_mark_start(hr) == region->start(),
         "MemRegion start should be equal to TAMS");
#endif

  G1RootRegionScanClosure cl(_g1h, this, worker_id);

  const uintx interval = PrefetchScanIntervalInBytes;
  HeapWord* curr = region->start();
  const HeapWord* end = region->end();
  while (curr < end) {
    Prefetch::read(curr, interval);
    oop obj = cast_to_oop(curr);
    size_t size = obj->oop_iterate_size(&cl);
    assert(size == obj->size(), "sanity");
    curr += size;
  }
}

class G1CMRootRegionScanTask : public WorkerTask {
  G1ConcurrentMark* _cm;
public:
  G1CMRootRegionScanTask(G1ConcurrentMark* cm) :
    WorkerTask("G1 Root Region Scan"), _cm(cm) { }

  void work(uint worker_id) {
    G1CMRootMemRegions* root_regions = _cm->root_regions();
    const MemRegion* region = root_regions->claim_next();
    while (region != nullptr) {
      _cm->scan_root_region(region, worker_id);
      region = root_regions->claim_next();
    }
  }
};

void G1ConcurrentMark::scan_root_regions() {
  // scan_in_progress() will have been set to true only if there was
  // at least one root region to scan. So, if it's false, we
  // should not attempt to do any further work.
  if (root_regions()->scan_in_progress()) {
    assert(!has_aborted(), "Aborting before root region scanning is finished not supported.");

    // Assign one worker to each root-region but subject to the max constraint.
    const uint num_workers = MIN2(root_regions()->num_root_regions(),
                                  _max_concurrent_workers);

    G1CMRootRegionScanTask task(this);
    log_debug(gc, ergo)("Running %s using %u workers for %u work units.",
                        task.name(), num_workers, root_regions()->num_root_regions());
    _concurrent_workers->run_task(&task, num_workers);

    // It's possible that has_aborted() is true here without actually
    // aborting the survivor scan earlier. This is OK as it's
    // mainly used for sanity checking.
    root_regions()->scan_finished();
  }
}

bool G1ConcurrentMark::wait_until_root_region_scan_finished() {
  return root_regions()->wait_until_scan_finished();
}

void G1ConcurrentMark::add_root_region(G1HeapRegion* r) {
  root_regions()->add(top_at_mark_start(r), r->top());
}

bool G1ConcurrentMark::is_root_region(G1HeapRegion* r) {
  return root_regions()->contains(MemRegion(top_at_mark_start(r), r->top()));
}

void G1ConcurrentMark::root_region_scan_abort_and_wait() {
  root_regions()->abort();
  root_regions()->wait_until_scan_finished();
}

void G1ConcurrentMark::concurrent_cycle_start() {
  _gc_timer_cm->register_gc_start();

  _gc_tracer_cm->report_gc_start(GCCause::_no_gc /* first parameter is not used */, _gc_timer_cm->gc_start());

  _g1h->trace_heap_before_gc(_gc_tracer_cm);
}

uint G1ConcurrentMark::completed_mark_cycles() const {
  return _completed_mark_cycles.load_relaxed();
}

void G1ConcurrentMark::concurrent_cycle_end(bool mark_cycle_completed) {
  _g1h->collector_state()->set_clear_bitmap_in_progress(false);

  _g1h->trace_heap_after_gc(_gc_tracer_cm);

  if (mark_cycle_completed) {
    _completed_mark_cycles.add_then_fetch(1u, memory_order_relaxed);
  }

  if (has_aborted()) {
    log_info(gc, marking)("Concurrent Mark Abort");
    _gc_tracer_cm->report_concurrent_mode_failure();
  }

  _gc_timer_cm->register_gc_end();

  _gc_tracer_cm->report_gc_end(_gc_timer_cm->gc_end(), _gc_timer_cm->time_partitions());
}

void G1ConcurrentMark::mark_from_roots() {
  _restart_for_overflow.store_relaxed(false);

  uint active_workers = calc_active_marking_workers();

  // Setting active workers is not guaranteed since fewer
  // worker threads may currently exist and more may not be
  // available.
  active_workers = _concurrent_workers->set_active_workers(active_workers);
  log_info(gc, task)("Concurrent Mark Using %u of %u Workers", active_workers, _concurrent_workers->max_workers());

  _num_concurrent_workers = active_workers;

  // Parallel task terminator is set in "set_concurrency_and_phase()"
  set_concurrency_and_phase(active_workers, true /* concurrent */);

  G1CMConcurrentMarkingTask marking_task(this);
  _concurrent_workers->run_task(&marking_task);
  print_stats();
}

const char* G1ConcurrentMark::verify_location_string(VerifyLocation location) {
  static const char* location_strings[] = { "Remark Before",
                                            "Remark After",
                                            "Remark Overflow",
                                            "Cleanup Before",
                                            "Cleanup After" };
  return location_strings[static_cast<std::underlying_type_t<VerifyLocation>>(location)];
}

void G1ConcurrentMark::verify_during_pause(G1HeapVerifier::G1VerifyType type,
                                           VerifyLocation location) {
  G1HeapVerifier* verifier = _g1h->verifier();

  verifier->verify_region_sets_optional();

  const char* caller = verify_location_string(location);

  if (VerifyDuringGC && G1HeapVerifier::should_verify(type)) {
    GCTraceTime(Debug, gc, phases) debug(caller, _gc_timer_cm);

    size_t const BufLen = 512;
    char buffer[BufLen];

    jio_snprintf(buffer, BufLen, "During GC (%s)", caller);
    verifier->verify(VerifyOption::G1UseConcMarking, buffer);

    // Only check bitmap in Remark, and not at After-Verification because the regions
    // already have their TAMS'es reset.
    if (location != VerifyLocation::RemarkAfter) {
      verifier->verify_bitmap_clear(true /* above_tams_only */);
    }
  }
}

class G1ObjectCountIsAliveClosure: public BoolObjectClosure {
  G1CollectedHeap* _g1h;
public:
  G1ObjectCountIsAliveClosure(G1CollectedHeap* g1h) : _g1h(g1h) {}

  bool do_object_b(oop obj) {
    return !_g1h->is_obj_dead(obj);
  }
};

void G1ConcurrentMark::remark() {
  assert_at_safepoint_on_vm_thread();

  // If a full collection has happened, we should not continue. However we might
  // have ended up here as the Remark VM operation has been scheduled already.
  if (has_aborted()) {
    return;
  }

  G1Policy* policy = _g1h->policy();
  policy->record_pause_start_time();

  double start = os::elapsedTime();

  verify_during_pause(G1HeapVerifier::G1VerifyRemark, VerifyLocation::RemarkBefore);

  {
    GCTraceTime(Debug, gc, phases) debug("Finalize Marking", _gc_timer_cm);
    finalize_marking();
  }

  double mark_work_end = os::elapsedTime();

  bool const mark_finished = !has_overflown();
  if (mark_finished) {
    weak_refs_work();

    // Unload Klasses, String, Code Cache, etc.
    if (ClassUnloadingWithConcurrentMark) {
      G1CMIsAliveClosure is_alive(this);
      _g1h->unload_classes_and_code("Class Unloading", &is_alive, _gc_timer_cm);
    }

    SATBMarkQueueSet& satb_mq_set = G1BarrierSet::satb_mark_queue_set();
    // We're done with marking.
    // This is the end of the marking cycle, we're expected all
    // threads to have SATB queues with active set to true.
    satb_mq_set.set_active_all_threads(false, /* new active value */
                                       true /* expected_active */);

    {
      GCTraceTime(Debug, gc, phases) debug("Flush Task Caches", _gc_timer_cm);
      flush_all_task_caches();
    }

    // All marking completed. Check bitmap now as we will start to reset TAMSes
    // in parallel below so that we can not do this in the After-Remark verification.
    _g1h->verifier()->verify_bitmap_clear(true /* above_tams_only */);

    {
      GCTraceTime(Debug, gc, phases) debug("Select For Rebuild and Reclaim Empty Regions", _gc_timer_cm);

      G1UpdateRegionLivenessAndSelectForRebuildTask cl(_g1h, this, _g1h->workers()->active_workers());
      uint const num_workers = MIN2(G1UpdateRegionLivenessAndSelectForRebuildTask::desired_num_workers(_g1h->num_committed_regions()),
                                    _g1h->workers()->active_workers());
      log_debug(gc,ergo)("Running %s using %u workers for %u regions in heap", cl.name(), num_workers, _g1h->num_committed_regions());
      _g1h->workers()->run_task(&cl, num_workers);

      log_debug(gc, remset, tracking)("Remembered Set Tracking update regions total %u, selected %u",
                                        _g1h->num_committed_regions(), cl.total_selected_for_rebuild());

      _needs_remembered_set_rebuild = (cl.total_selected_for_rebuild() > 0);

      if (_needs_remembered_set_rebuild) {
        // Prune rebuild candidates based on G1HeapWastePercent.
        // Improves rebuild time in addition to remembered set memory usage.
        G1CollectionSetChooser::build(_g1h->workers(), _g1h->num_committed_regions(), _g1h->policy()->candidates());
      }
    }

    if (log_is_enabled(Trace, gc, liveness)) {
      G1PrintRegionLivenessInfoClosure cl("Post-Marking");
      _g1h->heap_region_iterate(&cl);
    }

    // Potentially, some empty-regions have been reclaimed; make this a
    // "collection" so that pending allocation can retry before attempting a
    // GC pause.
    _g1h->increment_total_collections();

    // For Remark Pauses that may have been triggered by PeriodicGCs, we maintain
    // resizing based on MinHeapFreeRatio or MaxHeapFreeRatio. If a PeriodicGC is
    // triggered, it likely means there are very few regular GCs, making resizing
    // based on gc heuristics less effective.
    if (_g1h->last_gc_was_periodic()) {
      _g1h->resize_heap_after_full_collection(0 /* allocation_word_size */);
    }

    compute_new_sizes();

    verify_during_pause(G1HeapVerifier::G1VerifyRemark, VerifyLocation::RemarkAfter);

    assert(!restart_for_overflow(), "sanity");
    // Completely reset the marking state (except bitmaps) since marking completed.
    reset_at_marking_complete();

    G1CollectedHeap::finish_codecache_marking_cycle();

    {
      GCTraceTime(Debug, gc, phases) debug("Report Object Count", _gc_timer_cm);
      G1ObjectCountIsAliveClosure is_alive(_g1h);
      _gc_tracer_cm->report_object_count_after_gc(&is_alive, _g1h->workers());
    }
  } else {
    // We overflowed.  Restart concurrent marking.
    _restart_for_overflow.store_relaxed(true);

    verify_during_pause(G1HeapVerifier::G1VerifyRemark, VerifyLocation::RemarkOverflow);

    // Clear the marking state because we will be restarting
    // marking due to overflowing the global mark stack.
    reset_marking_for_restart();
  }

  // Statistics
  double now = os::elapsedTime();
  _remark_mark_times.add((mark_work_end - start) * 1000.0);
  _remark_weak_ref_times.add((now - mark_work_end) * 1000.0);
  _remark_times.add((now - start) * 1000.0);

  _g1h->update_perf_counter_cpu_time();

  policy->record_concurrent_mark_remark_end();
}

void G1ConcurrentMark::compute_new_sizes() {
  MetaspaceGC::compute_new_size();

  // Cleanup will have freed any regions completely full of garbage.
  // Update the soft reference policy with the new heap occupancy.
  Universe::heap()->update_capacity_and_used_at_gc();

  // We reclaimed old regions so we should calculate the sizes to make
  // sure we update the old gen/space data.
  _g1h->monitoring_support()->update_sizes();
}

class G1UpdateRegionsAfterRebuild : public G1HeapRegionClosure {
  G1CollectedHeap* _g1h;

public:
  G1UpdateRegionsAfterRebuild(G1CollectedHeap* g1h) : _g1h(g1h) { }

  bool do_heap_region(G1HeapRegion* r) override {
    // Update the remset tracking state from updating to complete
    // if remembered sets have been rebuilt.
    _g1h->policy()->remset_tracker()->update_after_rebuild(r);
    return false;
  }
};

void G1ConcurrentMark::cleanup() {
  assert_at_safepoint_on_vm_thread();

  // If a full collection has happened, we shouldn't do this.
  if (has_aborted()) {
    return;
  }

  G1Policy* policy = _g1h->policy();
  policy->record_pause_start_time();

  double start = os::elapsedTime();

  verify_during_pause(G1HeapVerifier::G1VerifyCleanup, VerifyLocation::CleanupBefore);

  if (needs_remembered_set_rebuild()) {
    // Update the remset tracking information as well as marking all regions
    // as fully parsable.
    GCTraceTime(Debug, gc, phases) debug("Update Remembered Set Tracking After Rebuild", _gc_timer_cm);
    G1UpdateRegionsAfterRebuild cl(_g1h);
    _g1h->heap_region_iterate(&cl);
  } else {
    log_debug(gc, phases)("No Remembered Sets to update after rebuild");
  }

  verify_during_pause(G1HeapVerifier::G1VerifyCleanup, VerifyLocation::CleanupAfter);

  // Local statistics
  _cleanup_times.add((os::elapsedTime() - start) * 1000.0);

  {
    GCTraceTime(Debug, gc, phases) debug("Finalize Concurrent Mark Cleanup", _gc_timer_cm);
    policy->record_concurrent_mark_cleanup_end(needs_remembered_set_rebuild());
  }
}

// 'Keep Alive' oop closure used by both serial parallel reference processing.
// Uses the G1CMTask associated with a worker thread (for serial reference
// processing the G1CMTask for worker 0 is used) to preserve (mark) and
// trace referent objects.
//
// Using the G1CMTask and embedded local queues avoids having the worker
// threads operating on the global mark stack. This reduces the risk
// of overflowing the stack - which we would rather avoid at this late
// state. Also using the tasks' local queues removes the potential
// of the workers interfering with each other that could occur if
// operating on the global stack.

class G1CMKeepAliveAndDrainClosure : public OopClosure {
  G1ConcurrentMark* _cm;
  G1CMTask*         _task;
  uint              _ref_counter_limit;
  uint              _ref_counter;
  bool              _is_serial;
public:
  G1CMKeepAliveAndDrainClosure(G1ConcurrentMark* cm, G1CMTask* task, bool is_serial) :
    _cm(cm), _task(task), _ref_counter_limit(G1RefProcDrainInterval),
    _ref_counter(_ref_counter_limit), _is_serial(is_serial) {
    assert(!_is_serial || _task->worker_id() == 0, "only task 0 for serial code");
  }

  virtual void do_oop(narrowOop* p) { do_oop_work(p); }
  virtual void do_oop(      oop* p) { do_oop_work(p); }

  template <class T> void do_oop_work(T* p) {
    if (_cm->has_overflown()) {
      return;
    }
    if (!_task->deal_with_reference(p)) {
      // We did not add anything to the mark bitmap (or mark stack), so there is
      // no point trying to drain it.
      return;
    }
    _ref_counter--;

    if (_ref_counter == 0) {
      // We have dealt with _ref_counter_limit references, pushing them
      // and objects reachable from them on to the local stack (and
      // possibly the global stack). Call G1CMTask::do_marking_step() to
      // process these entries.
      //
      // We call G1CMTask::do_marking_step() in a loop, which we'll exit if
      // there's nothing more to do (i.e. we're done with the entries that
      // were pushed as a result of the G1CMTask::deal_with_reference() calls
      // above) or we overflow.
      //
      // Note: G1CMTask::do_marking_step() can set the G1CMTask::has_aborted()
      // flag while there may still be some work to do. (See the comment at
      // the beginning of G1CMTask::do_marking_step() for those conditions -
      // one of which is reaching the specified time target.) It is only
      // when G1CMTask::do_marking_step() returns without setting the
      // has_aborted() flag that the marking step has completed.
      do {
        double mark_step_duration_ms = G1ConcMarkStepDurationMillis;
        _task->do_marking_step(mark_step_duration_ms,
                               false      /* do_termination */,
                               _is_serial);
      } while (_task->has_aborted() && !_cm->has_overflown());
      _ref_counter = _ref_counter_limit;
    }
  }
};

// 'Drain' oop closure used by both serial and parallel reference processing.
// Uses the G1CMTask associated with a given worker thread (for serial
// reference processing the G1CMtask for worker 0 is used). Calls the
// do_marking_step routine, with an unbelievably large timeout value,
// to drain the marking data structures of the remaining entries
// added by the 'keep alive' oop closure above.

class G1CMDrainMarkingStackClosure : public VoidClosure {
  G1ConcurrentMark* _cm;
  G1CMTask*         _task;
  bool              _is_serial;
 public:
  G1CMDrainMarkingStackClosure(G1ConcurrentMark* cm, G1CMTask* task, bool is_serial) :
    _cm(cm), _task(task), _is_serial(is_serial) {
    assert(!_is_serial || _task->worker_id() == 0, "only task 0 for serial code");
  }

  void do_void() {
    do {
      // We call G1CMTask::do_marking_step() to completely drain the local
      // and global marking stacks of entries pushed by the 'keep alive'
      // oop closure (an instance of G1CMKeepAliveAndDrainClosure above).
      //
      // G1CMTask::do_marking_step() is called in a loop, which we'll exit
      // if there's nothing more to do (i.e. we've completely drained the
      // entries that were pushed as a result of applying the 'keep alive'
      // closure to the entries on the discovered ref lists) or we overflow
      // the global marking stack.
      //
      // Note: G1CMTask::do_marking_step() can set the G1CMTask::has_aborted()
      // flag while there may still be some work to do. (See the comment at
      // the beginning of G1CMTask::do_marking_step() for those conditions -
      // one of which is reaching the specified time target.) It is only
      // when G1CMTask::do_marking_step() returns without setting the
      // has_aborted() flag that the marking step has completed.

      _task->do_marking_step(1000000000.0 /* something very large */,
                             true         /* do_termination */,
                             _is_serial);
    } while (_task->has_aborted() && !_cm->has_overflown());
  }
};

class G1CMRefProcProxyTask : public RefProcProxyTask {
  G1CollectedHeap& _g1h;
  G1ConcurrentMark& _cm;

public:
  G1CMRefProcProxyTask(uint max_workers, G1CollectedHeap& g1h, G1ConcurrentMark &cm)
    : RefProcProxyTask("G1CMRefProcProxyTask", max_workers),
      _g1h(g1h),
      _cm(cm) {}

  void work(uint worker_id) override {
    assert(worker_id < _max_workers, "sanity");
    G1CMIsAliveClosure is_alive(&_cm);
    uint index = (_tm == RefProcThreadModel::Single) ? 0 : worker_id;
    G1CMKeepAliveAndDrainClosure keep_alive(&_cm, _cm.task(index), _tm == RefProcThreadModel::Single);
    BarrierEnqueueDiscoveredFieldClosure enqueue;
    G1CMDrainMarkingStackClosure complete_gc(&_cm, _cm.task(index), _tm == RefProcThreadModel::Single);
    _rp_task->rp_work(worker_id, &is_alive, &keep_alive, &enqueue, &complete_gc);
  }

  void prepare_run_task_hook() override {
    // We need to reset the concurrency level before each
    // proxy task execution, so that the termination protocol
    // and overflow handling in G1CMTask::do_marking_step() knows
    // how many workers to wait for.
    _cm.set_concurrency(_queue_count);
  }
};

void G1ConcurrentMark::weak_refs_work() {
  ResourceMark rm;

  {
    GCTraceTime(Debug, gc, phases) debug("Reference Processing", _gc_timer_cm);

    ReferenceProcessor* rp = _g1h->ref_processor_cm();

    // See the comment in G1CollectedHeap::ref_processing_init()
    // about how reference processing currently works in G1.

    assert(_global_mark_stack.is_empty(), "mark stack should be empty");

    // Prefer to grow the stack until the max capacity.
    _global_mark_stack.set_should_grow();

    // Parallel processing task executor.
    G1CMRefProcProxyTask task(rp->max_num_queues(), *_g1h, *this);
    ReferenceProcessorPhaseTimes pt(_gc_timer_cm, rp->max_num_queues());

    // Process the weak references.
    const ReferenceProcessorStats& stats = rp->process_discovered_references(task, _g1h->workers(), pt);
    _gc_tracer_cm->report_gc_reference_stats(stats);
    pt.print_all_references();

    // The do_oop work routines of the keep_alive and drain_marking_stack
    // oop closures will set the has_overflown flag if we overflow the
    // global marking stack.

    assert(has_overflown() || _global_mark_stack.is_empty(),
           "Mark stack should be empty (unless it has overflown)");
  }

  if (has_overflown()) {
    // We can not trust g1_is_alive and the contents of the heap if the marking stack
    // overflowed while processing references. Exit the VM.
    fatal("Overflow during reference processing, can not continue. Current mark stack depth: "
          "%zu, MarkStackSize: %zu, MarkStackSizeMax: %zu. "
          "Please increase MarkStackSize and/or MarkStackSizeMax and restart.",
          _global_mark_stack.size(), MarkStackSize, MarkStackSizeMax);
    return;
  }

  assert(_global_mark_stack.is_empty(), "Marking should have completed");

  {
    GCTraceTime(Debug, gc, phases) debug("Weak Processing", _gc_timer_cm);
    G1CMIsAliveClosure is_alive(this);
    WeakProcessor::weak_oops_do(_g1h->workers(), &is_alive, &do_nothing_cl, 1);
  }
}

class G1PrecleanYieldClosure : public YieldClosure {
  G1ConcurrentMark* _cm;

public:
  G1PrecleanYieldClosure(G1ConcurrentMark* cm) : _cm(cm) { }

  virtual bool should_return() {
    return _cm->has_aborted();
  }

  virtual bool should_return_fine_grain() {
    _cm->do_yield_check();
    return _cm->has_aborted();
  }
};

void G1ConcurrentMark::preclean() {
  assert(G1UseReferencePrecleaning, "Precleaning must be enabled.");

  SuspendibleThreadSetJoiner joiner;

  BarrierEnqueueDiscoveredFieldClosure enqueue;

  set_concurrency_and_phase(1, true);

  G1PrecleanYieldClosure yield_cl(this);

  ReferenceProcessor* rp = _g1h->ref_processor_cm();
  // Precleaning is single threaded. Temporarily disable MT discovery.
  ReferenceProcessorMTDiscoveryMutator rp_mut_discovery(rp, false);
  rp->preclean_discovered_references(rp->is_alive_non_header(),
                                     &enqueue,
                                     &yield_cl,
                                     _gc_timer_cm);
}

// Closure for marking entries in SATB buffers.
class G1CMSATBBufferClosure : public SATBBufferClosure {
private:
  G1CMTask* _task;
  G1CollectedHeap* _g1h;

  // This is very similar to G1CMTask::deal_with_reference, but with
  // more relaxed requirements for the argument, so this must be more
  // circumspect about treating the argument as an object.
  void do_entry(void* entry) const {
    _task->increment_refs_reached();
    oop const obj = cast_to_oop(entry);
    _task->make_reference_grey(obj);
  }

public:
  G1CMSATBBufferClosure(G1CMTask* task, G1CollectedHeap* g1h)
    : _task(task), _g1h(g1h) { }

  virtual void do_buffer(void** buffer, size_t size) {
    for (size_t i = 0; i < size; ++i) {
      do_entry(buffer[i]);
    }
  }
};

class G1RemarkThreadsClosure : public ThreadClosure {
  G1SATBMarkQueueSet& _qset;

 public:
  G1RemarkThreadsClosure(G1CollectedHeap* g1h, G1CMTask* task) :
    _qset(G1BarrierSet::satb_mark_queue_set()) {}

  void do_thread(Thread* thread) {
    // Transfer any partial buffer to the qset for completed buffer processing.
    _qset.flush_queue(G1ThreadLocalData::satb_mark_queue(thread));
  }
};

class G1CMRemarkTask : public WorkerTask {
  // For Threads::possibly_parallel_threads_do
  ThreadsClaimTokenScope _threads_claim_token_scope;
  G1ConcurrentMark* _cm;
public:
  void work(uint worker_id) {
    G1CMTask* task = _cm->task(worker_id);
    task->record_start_time();
    {
      ResourceMark rm;

      G1RemarkThreadsClosure threads_f(G1CollectedHeap::heap(), task);
      Threads::possibly_parallel_threads_do(true /* is_par */, &threads_f);
    }

    do {
      task->do_marking_step(1000000000.0 /* something very large */,
                            true         /* do_termination       */,
                            false        /* is_serial            */);
    } while (task->has_aborted() && !_cm->has_overflown());
    // If we overflow, then we do not want to restart. We instead
    // want to abort remark and do concurrent marking again.
    task->record_end_time();
  }

  G1CMRemarkTask(G1ConcurrentMark* cm, uint active_workers) :
    WorkerTask("Par Remark"), _threads_claim_token_scope(), _cm(cm) {
    _cm->terminator()->reset_for_reuse(active_workers);
  }
};

void G1ConcurrentMark::finalize_marking() {
  ResourceMark rm;

  _g1h->ensure_parsability(false);

  // this is remark, so we'll use up all active threads
  uint active_workers = _g1h->workers()->active_workers();
  set_concurrency_and_phase(active_workers, false /* concurrent */);
  // Leave _parallel_marking_threads at it's
  // value originally calculated in the G1ConcurrentMark
  // constructor and pass values of the active workers
  // through the task.

  {
    G1CMRemarkTask remarkTask(this, active_workers);
    // We will start all available threads, even if we decide that the
    // active_workers will be fewer. The extra ones will just bail out
    // immediately.
    _g1h->workers()->run_task(&remarkTask);
  }

  SATBMarkQueueSet& satb_mq_set = G1BarrierSet::satb_mark_queue_set();
  guarantee(has_overflown() ||
            satb_mq_set.completed_buffers_num() == 0,
            "Invariant: has_overflown = %s, num buffers = %zu",
            BOOL_TO_STR(has_overflown()),
            satb_mq_set.completed_buffers_num());

  print_stats();
}

void G1ConcurrentMark::flush_all_task_caches() {
  size_t hits = 0;
  size_t misses = 0;
  for (uint i = 0; i < _max_num_tasks; i++) {
    Pair<size_t, size_t> stats = _tasks[i]->flush_mark_stats_cache();
    hits += stats.first;
    misses += stats.second;
  }
  size_t sum = hits + misses;
  log_debug(gc, stats)("Mark stats cache hits %zu misses %zu ratio %1.3lf",
                       hits, misses, percent_of(hits, sum));
}

void G1ConcurrentMark::clear_bitmap_for_region(G1HeapRegion* hr) {
  assert_at_safepoint();
  _mark_bitmap.clear_range(MemRegion(hr->bottom(), hr->end()));
}

G1HeapRegion* G1ConcurrentMark::claim_region(uint worker_id) {
  // "Checkpoint" the finger.
  HeapWord* local_finger = finger();

  while (local_finger < _heap.end()) {
    assert(_g1h->is_in_reserved(local_finger), "invariant");

    G1HeapRegion* curr_region = _g1h->heap_region_containing_or_null(local_finger);
    // Make sure that the reads below do not float before loading curr_region.
    OrderAccess::loadload();
    // Above heap_region_containing may return null as we always scan claim
    // until the end of the heap. In this case, just jump to the next region.
    HeapWord* end = curr_region != nullptr ? curr_region->end() : local_finger + G1HeapRegion::GrainWords;

    // Is the gap between reading the finger and doing the CAS too long?
    HeapWord* res = _finger.compare_exchange(local_finger, end);
    if (res == local_finger && curr_region != nullptr) {
      // We succeeded.
      HeapWord* bottom = curr_region->bottom();
      HeapWord* limit = top_at_mark_start(curr_region);

      log_trace(gc, marking)("Claim region %u bottom " PTR_FORMAT " tams " PTR_FORMAT, curr_region->hrm_index(), p2i(curr_region->bottom()), p2i(top_at_mark_start(curr_region)));
      // Notice that _finger == end cannot be guaranteed here since,
      // someone else might have moved the finger even further.
      assert(finger() >= end, "The finger should have moved forward");

      if (limit > bottom) {
        return curr_region;
      } else {
        assert(limit == bottom,
               "The region limit should be at bottom");
        // We return null and the caller should try calling
        // claim_region() again.
        return nullptr;
      }
    } else {
      // Read the finger again.
      HeapWord* next_finger = finger();
      assert(next_finger > local_finger, "The finger should have moved forward " PTR_FORMAT " " PTR_FORMAT, p2i(local_finger), p2i(next_finger));
      local_finger = next_finger;
    }
  }

  return nullptr;
}

#ifndef PRODUCT
class VerifyNoCSetOops {
  G1CollectedHeap* _g1h;
  const char* _phase;
  int _info;

public:
  VerifyNoCSetOops(const char* phase, int info = -1) :
    _g1h(G1CollectedHeap::heap()),
    _phase(phase),
    _info(info)
  { }

  void operator()(G1TaskQueueEntry task_entry) const {
    if (task_entry.is_partial_array_state()) {
      oop obj = task_entry.to_partial_array_state()->source();
      guarantee(_g1h->is_in_reserved(obj), "Partial Array " PTR_FORMAT " must be in heap.", p2i(obj));
      return;
    }
    guarantee(oopDesc::is_oop(task_entry.to_oop()),
              "Non-oop " PTR_FORMAT ", phase: %s, info: %d",
              p2i(task_entry.to_oop()), _phase, _info);
    G1HeapRegion* r = _g1h->heap_region_containing(task_entry.to_oop());
    guarantee(!(r->in_collection_set() || r->has_index_in_opt_cset()),
              "obj " PTR_FORMAT " from %s (%d) in region %u in (optional) collection set",
              p2i(task_entry.to_oop()), _phase, _info, r->hrm_index());
  }
};

void G1ConcurrentMark::verify_no_collection_set_oops() {
  assert(SafepointSynchronize::is_at_safepoint() || !is_init_completed(),
         "should be at a safepoint or initializing");
  if (!_g1h->collector_state()->mark_or_rebuild_in_progress()) {
    return;
  }

  // Verify entries on the global mark stack
  _global_mark_stack.iterate(VerifyNoCSetOops("Stack"));

  // Verify entries on the task queues
  for (uint i = 0; i < _max_num_tasks; ++i) {
    G1CMTaskQueue* queue = _task_queues->queue(i);
    queue->iterate(VerifyNoCSetOops("Queue", i));
  }

  // Verify the global finger
  HeapWord* global_finger = finger();
  if (global_finger != nullptr && global_finger < _heap.end()) {
    // Since we always iterate over all regions, we might get a null G1HeapRegion
    // here.
    G1HeapRegion* global_hr = _g1h->heap_region_containing_or_null(global_finger);
    guarantee(global_hr == nullptr || global_finger == global_hr->bottom(),
              "global finger: " PTR_FORMAT " region: " HR_FORMAT,
              p2i(global_finger), HR_FORMAT_PARAMS(global_hr));
  }

  // Verify the task fingers
  assert(_num_concurrent_workers <= _max_num_tasks, "sanity");
  for (uint i = 0; i < _num_concurrent_workers; ++i) {
    G1CMTask* task = _tasks[i];
    HeapWord* task_finger = task->finger();
    if (task_finger != nullptr && task_finger < _heap.end()) {
      // See above note on the global finger verification.
      G1HeapRegion* r = _g1h->heap_region_containing_or_null(task_finger);
      guarantee(r == nullptr || task_finger == r->bottom() ||
                !r->in_collection_set() || !r->has_index_in_opt_cset(),
                "task finger: " PTR_FORMAT " region: " HR_FORMAT,
                p2i(task_finger), HR_FORMAT_PARAMS(r));
    }
  }
}
#endif // PRODUCT

void G1ConcurrentMark::rebuild_and_scrub() {
  if (!needs_remembered_set_rebuild()) {
    log_debug(gc, marking)("Skipping Remembered Set Rebuild. No regions selected for rebuild, will only scrub");
  }

  G1ConcurrentRebuildAndScrub::rebuild_and_scrub(this, needs_remembered_set_rebuild(), _concurrent_workers);
}

void G1ConcurrentMark::print_stats() {
  if (!log_is_enabled(Debug, gc, stats)) {
    return;
  }
  log_debug(gc, stats)("---------------------------------------------------------------------");
  for (size_t i = 0; i < _num_active_tasks; ++i) {
    _tasks[i]->print_stats();
    log_debug(gc, stats)("---------------------------------------------------------------------");
  }
}

bool G1ConcurrentMark::concurrent_cycle_abort() {
  // If we start the compaction before the CM threads finish
  // scanning the root regions we might trip them over as we'll
  // be moving objects / updating references. So let's wait until
  // they are done. By telling them to abort, they should complete
  // early.
  root_region_scan_abort_and_wait();

  // We haven't started a concurrent cycle no need to do anything; we might have
  // aborted the marking because of shutting down though. In this case the marking
  // might have already completed the abort (leading to in_progress() below to
  // return false), however this still left marking state particularly in the
  // shared marking bitmap that must be cleaned up.
  // If there are multiple full gcs during shutdown we do this work repeatedly for
  // nothing, but this situation should be extremely rare (a full gc after shutdown
  // has been signalled is already rare), and this work should be negligible compared
  // to actual full gc work.

  if (!is_fully_initialized() || (!cm_thread()->in_progress() && !_g1h->concurrent_mark_is_terminating())) {
    return false;
  }

  reset_marking_for_restart();

  abort_marking_threads();

  SATBMarkQueueSet& satb_mq_set = G1BarrierSet::satb_mark_queue_set();
  satb_mq_set.abandon_partial_marking();
  // This can be called either during or outside marking, we'll read
  // the expected_active value from the SATB queue set.
  satb_mq_set.set_active_all_threads(false, /* new active value */
                                     satb_mq_set.is_active() /* expected_active */);
  return true;
}

void G1ConcurrentMark::abort_marking_threads() {
  assert(!_root_regions.scan_in_progress(), "still doing root region scan");
  _has_aborted.store_relaxed(true);
  _first_overflow_barrier_sync.abort();
  _second_overflow_barrier_sync.abort();
}

double G1ConcurrentMark::worker_threads_cpu_time_s() {
  class CountCpuTimeThreadClosure : public ThreadClosure {
  public:
    jlong _total_cpu_time;

    CountCpuTimeThreadClosure() : ThreadClosure(), _total_cpu_time(0) { }

    void do_thread(Thread* t) {
      _total_cpu_time += os::thread_cpu_time(t);
    }
  } cl;

  threads_do(&cl);

  return (double)cl._total_cpu_time / NANOSECS_PER_SEC;
}

static void print_ms_time_info(const char* prefix, const char* name,
                               NumberSeq& ns) {
  log_trace(gc, marking)("%s%5d %12s: total time = %8.2f s (avg = %8.2f ms).",
                         prefix, ns.num(), name, ns.sum()/1000.0, ns.avg());
  if (ns.num() > 0) {
    log_trace(gc, marking)("%s         [std. dev = %8.2f ms, max = %8.2f ms]",
                           prefix, ns.sd(), ns.maximum());
  }
}

void G1ConcurrentMark::print_summary_info() {
  Log(gc, marking) log;
  if (!log.is_trace()) {
    return;
  }

  log.trace(" Concurrent marking:");
  if (!is_fully_initialized()) {
    log.trace("    has not been initialized yet");
    return;
  }
  print_ms_time_info("  ", "remarks", _remark_times);
  {
    print_ms_time_info("     ", "final marks", _remark_mark_times);
    print_ms_time_info("     ", "weak refs", _remark_weak_ref_times);

  }
  print_ms_time_info("  ", "cleanups", _cleanup_times);
  log.trace("    Finalize live data total time = %8.2f s (avg = %8.2f ms).",
            _cleanup_times.sum() / 1000.0, _cleanup_times.avg());
  log.trace("  Total stop_world time = %8.2f s.",
            (_remark_times.sum() + _cleanup_times.sum())/1000.0);
  log.trace("  Total concurrent time = %8.2f s (%8.2f s marking).",
            cm_thread()->total_mark_cpu_time_s(), cm_thread()->worker_threads_cpu_time_s());
}

void G1ConcurrentMark::threads_do(ThreadClosure* tc) const {
  if (is_fully_initialized()) { // they are initialized late
    tc->do_thread(_cm_thread);
    _concurrent_workers->threads_do(tc);
  }
}

void G1ConcurrentMark::print_on(outputStream* st) const {
  st->print_cr("Marking Bits: (CMBitMap*) " PTR_FORMAT, p2i(mark_bitmap()));
  _mark_bitmap.print_on(st, " Bits: ");
}

static ReferenceProcessor* get_cm_oop_closure_ref_processor(G1CollectedHeap* g1h) {
  ReferenceProcessor* result = g1h->ref_processor_cm();
  assert(result != nullptr, "CM reference processor should not be null");
  return result;
}

G1CMOopClosure::G1CMOopClosure(G1CollectedHeap* g1h,
                               G1CMTask* task)
  : ClaimMetadataVisitingOopIterateClosure(ClassLoaderData::_claim_strong, get_cm_oop_closure_ref_processor(g1h)),
    _g1h(g1h), _task(task)
{ }

void G1CMTask::setup_for_region(G1HeapRegion* hr) {
  assert(hr != nullptr,
        "claim_region() should have filtered out null regions");
  _curr_region  = hr;
  _finger       = hr->bottom();
  update_region_limit();
}

void G1CMTask::update_region_limit() {
  G1HeapRegion* hr = _curr_region;
  HeapWord* bottom = hr->bottom();
  HeapWord* limit = _cm->top_at_mark_start(hr);

  if (limit == bottom) {
    // The region was collected underneath our feet.
    // We set the finger to bottom to ensure that the bitmap
    // iteration that will follow this will not do anything.
    // (this is not a condition that holds when we set the region up,
    // as the region is not supposed to be empty in the first place)
    _finger = bottom;
  } else if (limit >= _region_limit) {
    assert(limit >= _finger, "peace of mind");
  } else {
    assert(limit < _region_limit, "only way to get here");
    // This can happen under some pretty unusual circumstances.  An
    // evacuation pause empties the region underneath our feet (TAMS
    // at bottom). We then do some allocation in the region (TAMS
    // stays at bottom), followed by the region being used as a GC
    // alloc region (TAMS will move to top() and the objects
    // originally below it will be greyed). All objects now marked in
    // the region are explicitly greyed, if below the global finger,
    // and we do not need in fact to scan anything else. So, we simply
    // set _finger to be limit to ensure that the bitmap iteration
    // doesn't do anything.
    _finger = limit;
  }

  _region_limit = limit;
}

void G1CMTask::giveup_current_region() {
  assert(_curr_region != nullptr, "invariant");
  clear_region_fields();
}

void G1CMTask::clear_region_fields() {
  // Values for these three fields that indicate that we're not
  // holding on to a region.
  _curr_region   = nullptr;
  _finger        = nullptr;
  _region_limit  = nullptr;
}

void G1CMTask::set_cm_oop_closure(G1CMOopClosure* cm_oop_closure) {
  if (cm_oop_closure == nullptr) {
    assert(_cm_oop_closure != nullptr, "invariant");
  } else {
    assert(_cm_oop_closure == nullptr, "invariant");
  }
  _cm_oop_closure = cm_oop_closure;
}

void G1CMTask::reset(G1CMBitMap* mark_bitmap) {
  guarantee(mark_bitmap != nullptr, "invariant");
  _mark_bitmap              = mark_bitmap;
  clear_region_fields();

  _calls                         = 0;
  _elapsed_time_ms               = 0.0;
  _termination_time_ms           = 0.0;

  _mark_stats_cache.reset();
}

void G1CMTask::reset_for_restart() {
  clear_region_fields();
  _task_queue->set_empty();
  TASKQUEUE_STATS_ONLY(_partial_array_splitter.stats()->reset());
  TASKQUEUE_STATS_ONLY(_task_queue->stats.reset());
}

void G1CMTask::register_partial_array_splitter() {

  ::new (&_partial_array_splitter) PartialArraySplitter(_cm->partial_array_state_manager(),
                                                        _cm->max_num_tasks(),
                                                        ObjArrayMarkingStride);
}

void G1CMTask::unregister_partial_array_splitter() {
  _partial_array_splitter.~PartialArraySplitter();
}

bool G1CMTask::should_exit_termination() {
  if (!regular_clock_call()) {
    return true;
  }

  // This is called when we are in the termination protocol. We should
  // quit if, for some reason, this task wants to abort or the global
  // stack is not empty (this means that we can get work from it).
  return !_cm->mark_stack_empty() || has_aborted();
}

void G1CMTask::reached_limit() {
  assert(_words_scanned >= _words_scanned_limit ||
         _refs_reached >= _refs_reached_limit ,
         "shouldn't have been called otherwise");
  abort_marking_if_regular_check_fail();
}

bool G1CMTask::regular_clock_call() {
  if (has_aborted()) {
    return false;
  }

  // First, we need to recalculate the words scanned and refs reached
  // limits for the next clock call.
  recalculate_limits();

  // During the regular clock call we do the following

  // (1) If an overflow has been flagged, then we abort.
  if (_cm->has_overflown()) {
    return false;
  }

  // If we are not concurrent (i.e. we're doing remark) we don't need
  // to check anything else. The other steps are only needed during
  // the concurrent marking phase.
  if (!_cm->concurrent()) {
    return true;
  }

  // (2) If marking has been aborted for Full GC, then we also abort.
  if (_cm->has_aborted()) {
    return false;
  }

  // (4) We check whether we should yield. If we have to, then we abort.
  if (SuspendibleThreadSet::should_yield()) {
    // We should yield. To do this we abort the task. The caller is
    // responsible for yielding.
    return false;
  }

  // (5) We check whether we've reached our time quota. If we have,
  // then we abort.
  double elapsed_time_ms = (double)(os::current_thread_cpu_time() - _start_cpu_time_ns) / NANOSECS_PER_MILLISEC;
  if (elapsed_time_ms > _time_target_ms) {
    _has_timed_out = true;
    return false;
  }

  // (6) Finally, we check whether there are enough completed STAB
  // buffers available for processing. If there are, we abort.
  SATBMarkQueueSet& satb_mq_set = G1BarrierSet::satb_mark_queue_set();
  if (!_draining_satb_buffers && satb_mq_set.process_completed_buffers()) {
    // we do need to process SATB buffers, we'll abort and restart
    // the marking task to do so
    return false;
  }
  return true;
}

void G1CMTask::recalculate_limits() {
  _real_words_scanned_limit = _words_scanned + words_scanned_period;
  _words_scanned_limit      = _real_words_scanned_limit;

  _real_refs_reached_limit  = _refs_reached  + refs_reached_period;
  _refs_reached_limit       = _real_refs_reached_limit;
}

void G1CMTask::decrease_limits() {
  // This is called when we believe that we're going to do an infrequent
  // operation which will increase the per byte scanned cost (i.e. move
  // entries to/from the global stack). It basically tries to decrease the
  // scanning limit so that the clock is called earlier.

  _words_scanned_limit = _real_words_scanned_limit - 3 * words_scanned_period / 4;
  _refs_reached_limit  = _real_refs_reached_limit - 3 * refs_reached_period / 4;
}

void G1CMTask::move_entries_to_global_stack() {
  // Local array where we'll store the entries that will be popped
  // from the local queue.
  G1TaskQueueEntry buffer[G1CMMarkStack::EntriesPerChunk];

  size_t n = 0;
  G1TaskQueueEntry task_entry;
  while (n < G1CMMarkStack::EntriesPerChunk && _task_queue->pop_local(task_entry)) {
    buffer[n] = task_entry;
    ++n;
  }
  if (n < G1CMMarkStack::EntriesPerChunk) {
    buffer[n] = G1TaskQueueEntry();
  }

  if (n > 0) {
    if (!_cm->mark_stack_push(buffer)) {
      set_has_aborted();
    }
  }

  // This operation was quite expensive, so decrease the limits.
  decrease_limits();
}

bool G1CMTask::get_entries_from_global_stack() {
  // Local array where we'll store the entries that will be popped
  // from the global stack.
  G1TaskQueueEntry buffer[G1CMMarkStack::EntriesPerChunk];

  if (!_cm->mark_stack_pop(buffer)) {
    return false;
  }

  // We did actually pop at least one entry.
  for (size_t i = 0; i < G1CMMarkStack::EntriesPerChunk; ++i) {
    G1TaskQueueEntry task_entry = buffer[i];
    if (task_entry.is_null()) {
      break;
    }
    assert(task_entry.is_partial_array_state() || oopDesc::is_oop(task_entry.to_oop()), "Element " PTR_FORMAT " must be an array slice or oop", p2i(task_entry.to_oop()));
    bool success = _task_queue->push(task_entry);
    // We only call this when the local queue is empty or under a
    // given target limit. So, we do not expect this push to fail.
    assert(success, "invariant");
  }

  // This operation was quite expensive, so decrease the limits
  decrease_limits();
  return true;
}

void G1CMTask::drain_local_queue(bool partially) {
  if (has_aborted()) {
    return;
  }

  // Decide what the target size is, depending whether we're going to
  // drain it partially (so that other tasks can steal if they run out
  // of things to do) or totally (at the very end).
  uint target_size;
  if (partially) {
    target_size = GCDrainStackTargetSize;
  } else {
    target_size = 0;
  }

  if (_task_queue->size() > target_size) {
    G1TaskQueueEntry entry;
    bool ret = _task_queue->pop_local(entry);
    while (ret) {
      process_entry(entry, false /* stolen */);
      if (_task_queue->size() <= target_size || has_aborted()) {
        ret = false;
      } else {
        ret = _task_queue->pop_local(entry);
      }
    }
  }
}

size_t G1CMTask::start_partial_array_processing(oop obj) {
  assert(should_be_sliced(obj), "Must be an array object %d and large %zu", obj->is_objArray(), obj->size());

  objArrayOop obj_array = objArrayOop(obj);
  size_t array_length = obj_array->length();

  size_t initial_chunk_size = _partial_array_splitter.start(_task_queue, obj_array, nullptr, array_length);

  // Mark objArray klass metadata
  if (_cm_oop_closure->do_metadata()) {
    _cm_oop_closure->do_klass(obj_array->klass());
  }

  process_array_chunk(obj_array, 0, initial_chunk_size);

  // Include object header size
  return objArrayOopDesc::object_size(checked_cast<int>(initial_chunk_size));
}

size_t G1CMTask::process_partial_array(const G1TaskQueueEntry& task, bool stolen) {
  PartialArrayState* state = task.to_partial_array_state();
  // Access state before release by claim().
  objArrayOop obj = objArrayOop(state->source());

  PartialArraySplitter::Claim claim =
    _partial_array_splitter.claim(state, _task_queue, stolen);

  process_array_chunk(obj, claim._start, claim._end);
  return heap_word_size((claim._end - claim._start) * heapOopSize);
}

void G1CMTask::drain_global_stack(bool partially) {
  if (has_aborted()) {
    return;
  }

  // We have a policy to drain the local queue before we attempt to
  // drain the global stack.
  assert(partially || _task_queue->size() == 0, "invariant");

  // Decide what the target size is, depending whether we're going to
  // drain it partially (so that other tasks can steal if they run out
  // of things to do) or totally (at the very end).
  // Notice that when draining the global mark stack partially, due to the racyness
  // of the mark stack size update we might in fact drop below the target. But,
  // this is not a problem.
  // In case of total draining, we simply process until the global mark stack is
  // totally empty, disregarding the size counter.
  if (partially) {
    size_t const target_size = _cm->partial_mark_stack_size_target();
    while (!has_aborted() && _cm->mark_stack_size() > target_size) {
      if (get_entries_from_global_stack()) {
        drain_local_queue(partially);
      }
    }
  } else {
    while (!has_aborted() && get_entries_from_global_stack()) {
      drain_local_queue(partially);
    }
  }
}

// SATB Queue has several assumptions on whether to call the par or
// non-par versions of the methods. this is why some of the code is
// replicated. We should really get rid of the single-threaded version
// of the code to simplify things.
void G1CMTask::drain_satb_buffers() {
  if (has_aborted()) {
    return;
  }

  // We set this so that the regular clock knows that we're in the
  // middle of draining buffers and doesn't set the abort flag when it
  // notices that SATB buffers are available for draining. It'd be
  // very counter productive if it did that. :-)
  _draining_satb_buffers = true;

  G1CMSATBBufferClosure satb_cl(this, _g1h);
  SATBMarkQueueSet& satb_mq_set = G1BarrierSet::satb_mark_queue_set();

  // This keeps claiming and applying the closure to completed buffers
  // until we run out of buffers or we need to abort.
  while (!has_aborted() &&
         satb_mq_set.apply_closure_to_completed_buffer(&satb_cl)) {
    abort_marking_if_regular_check_fail();
  }

  // Can't assert qset is empty here, even if not aborted.  If concurrent,
  // some other thread might be adding to the queue.  If not concurrent,
  // some other thread might have won the race for the last buffer, but
  // has not yet decremented the count.

  _draining_satb_buffers = false;

  // again, this was a potentially expensive operation, decrease the
  // limits to get the regular clock call early
  decrease_limits();
}

void G1CMTask::clear_mark_stats_cache(uint region_idx) {
  _mark_stats_cache.reset(region_idx);
}

Pair<size_t, size_t> G1CMTask::flush_mark_stats_cache() {
  return _mark_stats_cache.evict_all();
}

void G1CMTask::print_stats() {
  log_debug(gc, stats)("Marking Stats, task = %u, calls = %u", _worker_id, _calls);
  log_debug(gc, stats)("  Elapsed time = %1.2lfms, Termination time = %1.2lfms",
                       _elapsed_time_ms, _termination_time_ms);
  log_debug(gc, stats)("  Step Times (cum): num = %d, avg = %1.2lfms, sd = %1.2lfms max = %1.2lfms, total = %1.2lfms",
                       _step_times_ms.num(),
                       _step_times_ms.avg(),
                       _step_times_ms.sd(),
                       _step_times_ms.maximum(),
                       _step_times_ms.sum());
  size_t const hits = _mark_stats_cache.hits();
  size_t const misses = _mark_stats_cache.misses();
  log_debug(gc, stats)("  Mark Stats Cache: hits %zu misses %zu ratio %.3f",
                       hits, misses, percent_of(hits, hits + misses));
}

bool G1ConcurrentMark::try_stealing(uint worker_id, G1TaskQueueEntry& task_entry) {
  return _task_queues->steal(worker_id, task_entry);
}

void G1CMTask::process_current_region(G1CMBitMapClosure& bitmap_closure) {
  if (has_aborted() || _curr_region == nullptr) {
    return;
  }

  // This means that we're already holding on to a region.
  assert(_finger != nullptr, "if region is not null, then the finger "
         "should not be null either");

  // We might have restarted this task after an evacuation pause
  // which might have evacuated the region we're holding on to
  // underneath our feet. Let's read its limit again to make sure
  // that we do not iterate over a region of the heap that
  // contains garbage (update_region_limit() will also move
  // _finger to the start of the region if it is found empty).
  update_region_limit();
  // We will start from _finger not from the start of the region,
  // as we might be restarting this task after aborting half-way
  // through scanning this region. In this case, _finger points to
  // the address where we last found a marked object. If this is a
  // fresh region, _finger points to start().
  MemRegion mr = MemRegion(_finger, _region_limit);

  assert(!_curr_region->is_humongous() || mr.start() == _curr_region->bottom(),
         "humongous regions should go around loop once only");

  // Some special cases:
  // If the memory region is empty, we can just give up the region.
  // If the current region is humongous then we only need to check
  // the bitmap for the bit associated with the start of the object,
  // scan the object if it's live, and give up the region.
  // Otherwise, let's iterate over the bitmap of the part of the region
  // that is left.
  // If the iteration is successful, give up the region.
  if (mr.is_empty()) {
    giveup_current_region();
    abort_marking_if_regular_check_fail();
  } else if (_curr_region->is_humongous() && mr.start() == _curr_region->bottom()) {
    if (_mark_bitmap->is_marked(mr.start())) {
      // The object is marked - apply the closure
      bitmap_closure.do_addr(mr.start());
    }
    // Even if this task aborted while scanning the humongous object
    // we can (and should) give up the current region.
    giveup_current_region();
    abort_marking_if_regular_check_fail();
  } else if (_mark_bitmap->iterate(&bitmap_closure, mr)) {
    giveup_current_region();
    abort_marking_if_regular_check_fail();
  } else {
    assert(has_aborted(), "currently the only way to do so");
    // The only way to abort the bitmap iteration is to return
    // false from the do_bit() method. However, inside the
    // do_bit() method we move the _finger to point to the
    // object currently being looked at. So, if we bail out, we
    // have definitely set _finger to something non-null.
    assert(_finger != nullptr, "invariant");

    // Region iteration was actually aborted. So now _finger
    // points to the address of the object we last scanned. If we
    // leave it there, when we restart this task, we will rescan
    // the object. It is easy to avoid this. We move the finger by
    // enough to point to the next possible object header.
    assert(_finger < _region_limit, "invariant");
    HeapWord* const new_finger = _finger + cast_to_oop(_finger)->size();
    if (new_finger >= _region_limit) {
      giveup_current_region();
    } else {
      move_finger_to(new_finger);
    }
  }
}

void G1CMTask::claim_new_region() {
  // Read the note on the claim_region() method on why it might
  // return null with potentially more regions available for
  // claiming and why we have to check out_of_regions() to determine
  // whether we're done or not.
  while (!has_aborted() && _curr_region == nullptr && !_cm->out_of_regions()) {
    // We are going to try to claim a new region. We should have
    // given up on the previous one.
    // Separated the asserts so that we know which one fires.
    assert(_curr_region  == nullptr, "invariant");
    assert(_finger       == nullptr, "invariant");
    assert(_region_limit == nullptr, "invariant");
    G1HeapRegion* claimed_region = _cm->claim_region(_worker_id);
    if (claimed_region != nullptr) {
      // Yes, we managed to claim one
      setup_for_region(claimed_region);
      assert(_curr_region == claimed_region, "invariant");
    }
    // It is important to call the regular clock here. It might take
    // a while to claim a region if, for example, we hit a large
    // block of empty regions. So we need to call the regular clock
    // method once round the loop to make sure it's called
    // frequently enough.
    abort_marking_if_regular_check_fail();
  }
}

void G1CMTask::attempt_stealing() {
  // We cannot check whether the global stack is empty, since other
  // tasks might be pushing objects to it concurrently.
  assert(_cm->out_of_regions() && _task_queue->size() == 0,
         "only way to reach here");
  while (!has_aborted()) {
    G1TaskQueueEntry entry;
    if (_cm->try_stealing(_worker_id, entry)) {
      process_entry(entry, true /* stolen */);

      // And since we're towards the end, let's totally drain the
      // local queue and global stack.
      drain_local_queue(false);
      drain_global_stack(false);
    } else {
      break;
    }
  }
}

void G1CMTask::attempt_termination(bool is_serial) {
  // We cannot check whether the global stack is empty, since other
  // tasks might be concurrently pushing objects on it.
  // Separated the asserts so that we know which one fires.
  assert(_cm->out_of_regions(), "only way to reach here");
  assert(_task_queue->size() == 0, "only way to reach here");
  double termination_start_time_ms = os::elapsedTime() * 1000.0;

  // The G1CMTask class also extends the TerminatorTerminator class,
  // hence its should_exit_termination() method will also decide
  // whether to exit the termination protocol or not.
  bool finished = (is_serial ||
                   _cm->terminator()->offer_termination(this));
  _termination_time_ms += (os::elapsedTime() * 1000.0 - termination_start_time_ms);

  if (finished) {
    // We're all done.

    // We can now guarantee that the global stack is empty, since
    // all other tasks have finished. We separated the guarantees so
    // that, if a condition is false, we can immediately find out
    // which one.
    guarantee(_cm->out_of_regions(), "only way to reach here");
    guarantee(_cm->mark_stack_empty(), "only way to reach here");
    guarantee(_task_queue->size() == 0, "only way to reach here");
    guarantee(!_cm->has_overflown(), "only way to reach here");
    guarantee(!has_aborted(), "should never happen if termination has completed");
  } else {
    // Apparently there's more work to do. Let's abort this task. We
    // will restart it and hopefully we can find more things to do.
    set_has_aborted();
  }
}

void G1CMTask::handle_abort(bool is_serial, double elapsed_time_ms) {
  if (_has_timed_out) {
    double diff_ms = elapsed_time_ms - _time_target_ms;
    // Keep statistics of how well we did with respect to hitting
    // our target only if we actually timed out (if we aborted for
    // other reasons, then the results might get skewed).
    _marking_step_diff_ms.add(diff_ms);
  }

  if (!_cm->has_overflown()) {
    return;
  }

  // This is the interesting one. We aborted because a global
  // overflow was raised. This means we have to restart the
  // marking phase and start iterating over regions. However, in
  // order to do this we have to make sure that all tasks stop
  // what they are doing and re-initialize in a safe manner. We
  // will achieve this with the use of two barrier sync points.
  if (!is_serial) {
    // We only need to enter the sync barrier if being called
    // from a parallel context
    _cm->enter_first_sync_barrier(_worker_id);

    // When we exit this sync barrier we know that all tasks have
    // stopped doing marking work. So, it's now safe to
    // re-initialize our data structures.
  }

  clear_region_fields();
  flush_mark_stats_cache();

  if (!is_serial) {
    // If we're executing the concurrent phase of marking, reset the marking
    // state; otherwise the marking state is reset after reference processing,
    // during the remark pause.
    // If we reset here as a result of an overflow during the remark we will
    // see assertion failures from any subsequent set_concurrency_and_phase()
    // calls.
    if (_cm->concurrent() && _worker_id == 0) {
      // Worker 0 is responsible for clearing the global data structures because
      // of an overflow. During STW we should not clear the overflow flag (in
      // G1ConcurrentMark::reset_marking_state()) since we rely on it being true when we exit
      // method to abort the pause and restart concurrent marking.
      _cm->reset_marking_for_restart();

      log_info(gc, marking)("Concurrent Mark reset for overflow");
    }

    // ...and enter the second barrier.
    _cm->enter_second_sync_barrier(_worker_id);
  }
}

/*****************************************************************************

    The do_marking_step(time_target_ms, ...) method is the building
    block of the parallel marking framework. It can be called in parallel
    with other invocations of do_marking_step() on different tasks
    (but only one per task, obviously) and concurrently with the
    mutator threads, or during remark, hence it eliminates the need
    for two versions of the code. When called during remark, it will
    pick up from where the task left off during the concurrent marking
    phase. Interestingly, tasks are also claimable during evacuation
    pauses too, since do_marking_step() ensures that it aborts before
    it needs to yield.

    The data structures that it uses to do marking work are the
    following:

      (1) Marking Bitmap. If there are grey objects that appear only
      on the bitmap (this happens either when dealing with an overflow
      or when the concurrent start pause has simply marked the roots
      and didn't push them on the stack), then tasks claim heap
      regions whose bitmap they then scan to find grey objects. A
      global finger indicates where the end of the last claimed region
      is. A local finger indicates how far into the region a task has
      scanned. The two fingers are used to determine how to grey an
      object (i.e. whether simply marking it is OK, as it will be
      visited by a task in the future, or whether it needs to be also
      pushed on a stack).

      (2) Local Queue. The local queue of the task which is accessed
      reasonably efficiently by the task. Other tasks can steal from
      it when they run out of work. Throughout the marking phase, a
      task attempts to keep its local queue short but not totally
      empty, so that entries are available for stealing by other
      tasks. Only when there is no more work, a task will totally
      drain its local queue.

      (3) Global Mark Stack. This handles local queue overflow. During
      marking only sets of entries are moved between it and the local
      queues, as access to it requires a mutex and more fine-grain
      interaction with it which might cause contention. If it
      overflows, then the marking phase should restart and iterate
      over the bitmap to identify grey objects. Throughout the marking
      phase, tasks attempt to keep the global mark stack at a small
      length but not totally empty, so that entries are available for
      popping by other tasks. Only when there is no more work, tasks
      will totally drain the global mark stack.

      (4) SATB Buffer Queue. This is where completed SATB buffers are
      made available. Buffers are regularly removed from this queue
      and scanned for roots, so that the queue doesn't get too
      long. During remark, all completed buffers are processed, as
      well as the filled in parts of any uncompleted buffers.

    The do_marking_step() method tries to abort when the time target
    has been reached. There are a few other cases when the
    do_marking_step() method also aborts:

      (1) When the marking phase has been aborted (after a Full GC).

      (2) When a global overflow (on the global stack) has been
      triggered. Before the task aborts, it will actually sync up with
      the other tasks to ensure that all the marking data structures
      (local queues, stacks, fingers etc.)  are re-initialized so that
      when do_marking_step() completes, the marking phase can
      immediately restart.

      (3) When enough completed SATB buffers are available. The
      do_marking_step() method only tries to drain SATB buffers right
      at the beginning. So, if enough buffers are available, the
      marking step aborts and the SATB buffers are processed at
      the beginning of the next invocation.

      (4) To yield. when we have to yield then we abort and yield
      right at the end of do_marking_step(). This saves us from a lot
      of hassle as, by yielding we might allow a Full GC. If this
      happens then objects will be compacted underneath our feet, the
      heap might shrink, etc. We save checking for this by just
      aborting and doing the yield right at the end.

    From the above it follows that the do_marking_step() method should
    be called in a loop (or, otherwise, regularly) until it completes.

    If a marking step completes without its has_aborted() flag being
    true, it means it has completed the current marking phase (and
    also all other marking tasks have done so and have all synced up).

    A method called regular_clock_call() is invoked "regularly" (in
    sub ms intervals) throughout marking. It is this clock method that
    checks all the abort conditions which were mentioned above and
    decides when the task should abort. A work-based scheme is used to
    trigger this clock method: when the number of object words the
    marking phase has scanned or the number of references the marking
    phase has visited reach a given limit. Additional invocations to
    the method clock have been planted in a few other strategic places
    too. The initial reason for the clock method was to avoid calling
    cpu time gathering too regularly, as it is quite expensive. So,
    once it was in place, it was natural to piggy-back all the other
    conditions on it too and not constantly check them throughout the code.

    If do_termination is true then do_marking_step will enter its
    termination protocol.

    The value of is_serial must be true when do_marking_step is being
    called serially (i.e. by the VMThread) and do_marking_step should
    skip any synchronization in the termination and overflow code.
    Examples include the serial remark code and the serial reference
    processing closures.

    The value of is_serial must be false when do_marking_step is
    being called by any of the worker threads.
    Examples include the concurrent marking code (CMMarkingTask),
    the MT remark code, and the MT reference processing closures.

 *****************************************************************************/

void G1CMTask::do_marking_step(double time_target_ms,
                               bool do_termination,
                               bool is_serial) {
  assert(time_target_ms >= 1.0, "minimum granularity is 1ms");

  _start_cpu_time_ns = os::current_thread_cpu_time();

  // If do_stealing is true then do_marking_step will attempt to
  // steal work from the other G1CMTasks. It only makes sense to
  // enable stealing when the termination protocol is enabled
  // and do_marking_step() is not being called serially.
  bool do_stealing = do_termination && !is_serial;

  G1Predictions const& predictor = _g1h->policy()->predictor();
  double diff_prediction_ms = predictor.predict_zero_bounded(&_marking_step_diff_ms);
  _time_target_ms = time_target_ms - diff_prediction_ms;

  // set up the variables that are used in the work-based scheme to
  // call the regular clock method
  _words_scanned = 0;
  _refs_reached  = 0;
  recalculate_limits();

  // clear all flags
  clear_has_aborted();
  _has_timed_out = false;
  _draining_satb_buffers = false;

  ++_calls;

  // Set up the bitmap and oop closures. Anything that uses them is
  // eventually called from this method, so it is OK to allocate these
  // statically.
  G1CMBitMapClosure bitmap_closure(this, _cm);
  G1CMOopClosure cm_oop_closure(_g1h, this);
  set_cm_oop_closure(&cm_oop_closure);

  if (_cm->has_overflown()) {
    // This can happen if the mark stack overflows during a GC pause
    // and this task, after a yield point, restarts. We have to abort
    // as we need to get into the overflow protocol which happens
    // right at the end of this task.
    set_has_aborted();
  }

  // First drain any available SATB buffers. After this, we will not
  // look at SATB buffers before the next invocation of this method.
  // If enough completed SATB buffers are queued up, the regular clock
  // will abort this task so that it restarts.
  drain_satb_buffers();
  // ...then partially drain the local queue and the global stack
  drain_local_queue(true);
  drain_global_stack(true);

  do {
    process_current_region(bitmap_closure);
    // At this point we have either completed iterating over the
    // region we were holding on to, or we have aborted.

    // We then partially drain the local queue and the global stack.
    drain_local_queue(true);
    drain_global_stack(true);

    claim_new_region();

    assert(has_aborted() || _curr_region != nullptr || _cm->out_of_regions(),
           "at this point we should be out of regions");
  } while ( _curr_region != nullptr && !has_aborted());

  // We cannot check whether the global stack is empty, since other
  // tasks might be pushing objects to it concurrently.
  assert(has_aborted() || _cm->out_of_regions(),
         "at this point we should be out of regions");
  // Try to reduce the number of available SATB buffers so that
  // remark has less work to do.
  drain_satb_buffers();

  // Since we've done everything else, we can now totally drain the
  // local queue and global stack.
  drain_local_queue(false);
  drain_global_stack(false);

  // Attempt at work stealing from other task's queues.
  if (do_stealing && !has_aborted()) {
    // We have not aborted. This means that we have finished all that
    // we could. Let's try to do some stealing...
    attempt_stealing();
  }

  // We still haven't aborted. Now, let's try to get into the
  // termination protocol.
  if (do_termination && !has_aborted()) {
    attempt_termination(is_serial);
  }

  // Mainly for debugging purposes to make sure that a pointer to the
  // closure which was statically allocated in this frame doesn't
  // escape it by accident.
  set_cm_oop_closure(nullptr);
  jlong end_cpu_time_ns = os::current_thread_cpu_time();
  double elapsed_time_ms = (double)(end_cpu_time_ns - _start_cpu_time_ns) / NANOSECS_PER_MILLISEC;
  // Update the step history.
  _step_times_ms.add(elapsed_time_ms);

  if (has_aborted()) {
    // The task was aborted for some reason.
    handle_abort(is_serial, elapsed_time_ms);
  }
}

G1CMTask::G1CMTask(uint worker_id,
                   G1ConcurrentMark* cm,
                   G1CMTaskQueue* task_queue,
                   G1RegionMarkStats* mark_stats) :
  _worker_id(worker_id),
  _g1h(G1CollectedHeap::heap()),
  _cm(cm),
  _mark_bitmap(nullptr),
  _task_queue(task_queue),
  _partial_array_splitter(_cm->partial_array_state_manager(), _cm->max_num_tasks(), ObjArrayMarkingStride),
  _mark_stats_cache(mark_stats, G1RegionMarkStatsCache::RegionMarkStatsCacheSize),
  _calls(0),
  _time_target_ms(0.0),
  _start_cpu_time_ns(0),
  _cm_oop_closure(nullptr),
  _curr_region(nullptr),
  _finger(nullptr),
  _region_limit(nullptr),
  _words_scanned(0),
  _words_scanned_limit(0),
  _real_words_scanned_limit(0),
  _refs_reached(0),
  _refs_reached_limit(0),
  _real_refs_reached_limit(0),
  _has_aborted(false),
  _has_timed_out(false),
  _draining_satb_buffers(false),
  _step_times_ms(),
  _elapsed_time_ms(0.0),
  _termination_time_ms(0.0),
  _marking_step_diff_ms()
{
  guarantee(task_queue != nullptr, "invariant");

  _marking_step_diff_ms.add(0.5);
}

// These are formatting macros that are used below to ensure
// consistent formatting. The *_H_* versions are used to format the
// header for a particular value and they should be kept consistent
// with the corresponding macro. Also note that most of the macros add
// the necessary white space (as a prefix) which makes them a bit
// easier to compose.

// All the output lines are prefixed with this string to be able to
// identify them easily in a large log file.
#define G1PPRL_LINE_PREFIX            "###"

#define G1PPRL_ADDR_BASE_FORMAT    " " PTR_FORMAT "-" PTR_FORMAT
#ifdef _LP64
#define G1PPRL_ADDR_BASE_H_FORMAT  " %37s"
#else // _LP64
#define G1PPRL_ADDR_BASE_H_FORMAT  " %21s"
#endif // _LP64

// For per-region info
#define G1PPRL_TYPE_FORMAT            "   %-4s"
#define G1PPRL_TYPE_H_FORMAT          "   %4s"
#define G1PPRL_STATE_FORMAT           "   %-5s"
#define G1PPRL_STATE_H_FORMAT         "   %5s"
#define G1PPRL_BYTE_FORMAT            "  %9zu"
#define G1PPRL_BYTE_H_FORMAT          "  %9s"
#define G1PPRL_DOUBLE_FORMAT          "%14.1f"
#define G1PPRL_GCEFF_H_FORMAT         "  %14s"
#define G1PPRL_GID_H_FORMAT           "  %9s"
#define G1PPRL_GID_FORMAT             "  " UINT32_FORMAT_W(9)
#define G1PPRL_LEN_FORMAT             "  " UINT32_FORMAT_W(14)
#define G1PPRL_LEN_H_FORMAT           "  %14s"
#define G1PPRL_GID_GCEFF_FORMAT       "  %14.1f"
#define G1PPRL_GID_LIVENESS_FORMAT    "  %9.2f"

// For summary info
#define G1PPRL_SUM_ADDR_FORMAT(tag)    "  " tag ":" G1PPRL_ADDR_BASE_FORMAT
#define G1PPRL_SUM_BYTE_FORMAT(tag)    "  " tag ": %zu"
#define G1PPRL_SUM_MB_FORMAT(tag)      "  " tag ": %1.2f MB"
#define G1PPRL_SUM_MB_PERC_FORMAT(tag) G1PPRL_SUM_MB_FORMAT(tag) " / %1.2f %%"

G1PrintRegionLivenessInfoClosure::G1PrintRegionLivenessInfoClosure(const char* phase_name) :
  _total_used_bytes(0),
  _total_capacity_bytes(0),
  _total_live_bytes(0),
  _total_remset_bytes(0),
  _total_code_roots_bytes(0)
{
  if (!log_is_enabled(Trace, gc, liveness)) {
    return;
  }

  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  MemRegion reserved = g1h->reserved();
  double now = os::elapsedTime();

  // Print the header of the output.
  log_trace(gc, liveness)(G1PPRL_LINE_PREFIX" PHASE %s @ %1.3f", phase_name, now);
  log_trace(gc, liveness)(G1PPRL_LINE_PREFIX" HEAP"
                          G1PPRL_SUM_ADDR_FORMAT("reserved")
                          G1PPRL_SUM_BYTE_FORMAT("region-size"),
                          p2i(reserved.start()), p2i(reserved.end()),
                          G1HeapRegion::GrainBytes);
  log_trace(gc, liveness)(G1PPRL_LINE_PREFIX);
  log_trace(gc, liveness)(G1PPRL_LINE_PREFIX
                          G1PPRL_TYPE_H_FORMAT
                          G1PPRL_ADDR_BASE_H_FORMAT
                          G1PPRL_BYTE_H_FORMAT
                          G1PPRL_BYTE_H_FORMAT
                          G1PPRL_STATE_H_FORMAT
                          G1PPRL_BYTE_H_FORMAT
                          G1PPRL_GID_H_FORMAT,
                          "type", "address-range",
                          "used", "live",
                          "state", "code-roots",
                          "group-id");
  log_trace(gc, liveness)(G1PPRL_LINE_PREFIX
                          G1PPRL_TYPE_H_FORMAT
                          G1PPRL_ADDR_BASE_H_FORMAT
                          G1PPRL_BYTE_H_FORMAT
                          G1PPRL_BYTE_H_FORMAT
                          G1PPRL_STATE_H_FORMAT
                          G1PPRL_BYTE_H_FORMAT
                          G1PPRL_GID_H_FORMAT,
                          "", "",
                          "(bytes)", "(bytes)",
                          "", "(bytes)", "");
}

bool G1PrintRegionLivenessInfoClosure::do_heap_region(G1HeapRegion* r) {
  if (!log_is_enabled(Trace, gc, liveness)) {
    return false;
  }

  const char* type       = r->get_type_str();
  HeapWord* bottom       = r->bottom();
  HeapWord* end          = r->end();
  size_t capacity_bytes  = r->capacity();
  size_t used_bytes      = r->used();
  size_t live_bytes      = r->live_bytes();
  size_t remset_bytes    = r->rem_set()->mem_size();
  size_t code_roots_bytes = r->rem_set()->code_roots_mem_size();
  const char* remset_type = r->rem_set()->get_short_state_str();
  uint cset_group_id     = r->rem_set()->has_cset_group()
                         ? r->rem_set()->cset_group_id()
                         : G1CSetCandidateGroup::NoRemSetId;

  _total_used_bytes      += used_bytes;
  _total_capacity_bytes  += capacity_bytes;
  _total_live_bytes      += live_bytes;
  _total_remset_bytes    += remset_bytes;
  _total_code_roots_bytes += code_roots_bytes;

  // Print a line for this particular region.
  log_trace(gc, liveness)(G1PPRL_LINE_PREFIX
                        G1PPRL_TYPE_FORMAT
                        G1PPRL_ADDR_BASE_FORMAT
                        G1PPRL_BYTE_FORMAT
                        G1PPRL_BYTE_FORMAT
                        G1PPRL_STATE_FORMAT
                        G1PPRL_BYTE_FORMAT
                        G1PPRL_GID_FORMAT,
                        type, p2i(bottom), p2i(end),
                        used_bytes, live_bytes,
                        remset_type, code_roots_bytes,
                        cset_group_id);

  return false;
}

G1PrintRegionLivenessInfoClosure::~G1PrintRegionLivenessInfoClosure() {
  if (!log_is_enabled(Trace, gc, liveness)) {
    return;
  }

  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  _total_remset_bytes += g1h->card_set_freelist_pool()->mem_size();
  // add static memory usages to remembered set sizes
  _total_remset_bytes += G1HeapRegionRemSet::static_mem_size();

  log_cset_candidate_groups();

  // Print the footer of the output.
  log_trace(gc, liveness)(G1PPRL_LINE_PREFIX);
  log_trace(gc, liveness)(G1PPRL_LINE_PREFIX
                         " SUMMARY"
                         G1PPRL_SUM_MB_FORMAT("capacity")
                         G1PPRL_SUM_MB_PERC_FORMAT("used")
                         G1PPRL_SUM_MB_PERC_FORMAT("live")
                         G1PPRL_SUM_MB_FORMAT("remset")
                         G1PPRL_SUM_MB_FORMAT("code-roots"),
                         bytes_to_mb(_total_capacity_bytes),
                         bytes_to_mb(_total_used_bytes),
                         percent_of(_total_used_bytes, _total_capacity_bytes),
                         bytes_to_mb(_total_live_bytes),
                         percent_of(_total_live_bytes, _total_capacity_bytes),
                         bytes_to_mb(_total_remset_bytes),
                         bytes_to_mb(_total_code_roots_bytes));
}

void G1PrintRegionLivenessInfoClosure::log_cset_candidate_group_add_total(G1CSetCandidateGroup* group, const char* type) {
  log_trace(gc, liveness)(G1PPRL_LINE_PREFIX
                          G1PPRL_GID_FORMAT
                          G1PPRL_LEN_FORMAT
                          G1PPRL_GID_GCEFF_FORMAT
                          G1PPRL_GID_LIVENESS_FORMAT
                          G1PPRL_BYTE_FORMAT
                          G1PPRL_TYPE_H_FORMAT,
                          group->group_id(),
                          group->length(),
                          group->length() > 0 ? group->gc_efficiency() : 0.0,
                          group->length() > 0 ? group->liveness_percent() : 0.0,
                          group->card_set()->mem_size(),
                          type);
  _total_remset_bytes += group->card_set()->mem_size();
}

void G1PrintRegionLivenessInfoClosure::log_cset_candidate_grouplist(G1CSetCandidateGroupList& gl, const char* type) {
  for (G1CSetCandidateGroup* group : gl) {
    log_cset_candidate_group_add_total(group, type);
  }
}

void G1PrintRegionLivenessInfoClosure::log_cset_candidate_groups() {
  log_trace(gc, liveness)(G1PPRL_LINE_PREFIX);
  log_trace(gc, liveness)(G1PPRL_LINE_PREFIX" Collection Set Candidate Groups");
  log_trace(gc, liveness)(G1PPRL_LINE_PREFIX " Types: Y=Young, M=From Marking Regions, R=Retained Regions");
  log_trace(gc, liveness)(G1PPRL_LINE_PREFIX
                          G1PPRL_GID_H_FORMAT
                          G1PPRL_LEN_H_FORMAT
                          G1PPRL_GCEFF_H_FORMAT
                          G1PPRL_BYTE_H_FORMAT
                          G1PPRL_BYTE_H_FORMAT
                          G1PPRL_TYPE_H_FORMAT,
                          "groud-id", "num-regions",
                          "gc-eff", "liveness",
                          "remset", "type");

  log_trace(gc, liveness)(G1PPRL_LINE_PREFIX
                          G1PPRL_GID_H_FORMAT
                          G1PPRL_LEN_H_FORMAT
                          G1PPRL_GCEFF_H_FORMAT
                          G1PPRL_BYTE_H_FORMAT
                          G1PPRL_BYTE_H_FORMAT
                          G1PPRL_TYPE_H_FORMAT,
                          "", "",
                          "(bytes/ms)", "%",
                          "(bytes)", "");

  G1CollectedHeap* g1h = G1CollectedHeap::heap();

  log_cset_candidate_group_add_total(g1h->young_regions_cset_group(), "Y");

  G1CollectionSetCandidates* candidates = g1h->policy()->candidates();
  log_cset_candidate_grouplist(candidates->from_marking_groups(), "M");
  log_cset_candidate_grouplist(candidates->retained_groups(), "R");
}
