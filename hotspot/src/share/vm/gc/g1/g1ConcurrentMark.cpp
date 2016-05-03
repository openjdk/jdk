/*
 * Copyright (c) 2001, 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/metadataOnStackMark.hpp"
#include "classfile/symbolTable.hpp"
#include "code/codeCache.hpp"
#include "gc/g1/concurrentMarkThread.inline.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1CollectorState.hpp"
#include "gc/g1/g1ConcurrentMark.inline.hpp"
#include "gc/g1/g1HeapVerifier.hpp"
#include "gc/g1/g1OopClosures.inline.hpp"
#include "gc/g1/g1CardLiveData.inline.hpp"
#include "gc/g1/g1Policy.hpp"
#include "gc/g1/g1StringDedup.hpp"
#include "gc/g1/heapRegion.inline.hpp"
#include "gc/g1/heapRegionRemSet.hpp"
#include "gc/g1/heapRegionSet.inline.hpp"
#include "gc/g1/suspendibleThreadSet.hpp"
#include "gc/shared/gcId.hpp"
#include "gc/shared/gcTimer.hpp"
#include "gc/shared/gcTrace.hpp"
#include "gc/shared/gcTraceTime.inline.hpp"
#include "gc/shared/genOopClosures.inline.hpp"
#include "gc/shared/referencePolicy.hpp"
#include "gc/shared/strongRootsScope.hpp"
#include "gc/shared/taskqueue.inline.hpp"
#include "gc/shared/vmGCOperations.hpp"
#include "logging/log.hpp"
#include "memory/allocation.hpp"
#include "memory/resourceArea.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/atomic.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/java.hpp"
#include "runtime/prefetch.inline.hpp"
#include "services/memTracker.hpp"
#include "utilities/growableArray.hpp"

// Concurrent marking bit map wrapper

G1CMBitMapRO::G1CMBitMapRO(int shifter) :
  _bm(),
  _shifter(shifter) {
  _bmStartWord = 0;
  _bmWordSize = 0;
}

HeapWord* G1CMBitMapRO::getNextMarkedWordAddress(const HeapWord* addr,
                                                 const HeapWord* limit) const {
  // First we must round addr *up* to a possible object boundary.
  addr = (HeapWord*)align_size_up((intptr_t)addr,
                                  HeapWordSize << _shifter);
  size_t addrOffset = heapWordToOffset(addr);
  assert(limit != NULL, "limit must not be NULL");
  size_t limitOffset = heapWordToOffset(limit);
  size_t nextOffset = _bm.get_next_one_offset(addrOffset, limitOffset);
  HeapWord* nextAddr = offsetToHeapWord(nextOffset);
  assert(nextAddr >= addr, "get_next_one postcondition");
  assert(nextAddr == limit || isMarked(nextAddr),
         "get_next_one postcondition");
  return nextAddr;
}

#ifndef PRODUCT
bool G1CMBitMapRO::covers(MemRegion heap_rs) const {
  // assert(_bm.map() == _virtual_space.low(), "map inconsistency");
  assert(((size_t)_bm.size() * ((size_t)1 << _shifter)) == _bmWordSize,
         "size inconsistency");
  return _bmStartWord == (HeapWord*)(heap_rs.start()) &&
         _bmWordSize  == heap_rs.word_size();
}
#endif

void G1CMBitMapRO::print_on_error(outputStream* st, const char* prefix) const {
  _bm.print_on_error(st, prefix);
}

size_t G1CMBitMap::compute_size(size_t heap_size) {
  return ReservedSpace::allocation_align_size_up(heap_size / mark_distance());
}

size_t G1CMBitMap::mark_distance() {
  return MinObjAlignmentInBytes * BitsPerByte;
}

void G1CMBitMap::initialize(MemRegion heap, G1RegionToSpaceMapper* storage) {
  _bmStartWord = heap.start();
  _bmWordSize = heap.word_size();

  _bm = BitMapView((BitMap::bm_word_t*) storage->reserved().start(), _bmWordSize >> _shifter);

  storage->set_mapping_changed_listener(&_listener);
}

void G1CMBitMapMappingChangedListener::on_commit(uint start_region, size_t num_regions, bool zero_filled) {
  if (zero_filled) {
    return;
  }
  // We need to clear the bitmap on commit, removing any existing information.
  MemRegion mr(G1CollectedHeap::heap()->bottom_addr_for_region(start_region), num_regions * HeapRegion::GrainWords);
  _bm->clear_range(mr);
}

void G1CMBitMap::clear_range(MemRegion mr) {
  mr.intersection(MemRegion(_bmStartWord, _bmWordSize));
  assert(!mr.is_empty(), "unexpected empty region");
  // convert address range into offset range
  _bm.at_put_range(heapWordToOffset(mr.start()),
                   heapWordToOffset(mr.end()), false);
}

G1CMMarkStack::G1CMMarkStack(G1ConcurrentMark* cm) :
  _base(NULL), _cm(cm)
{}

bool G1CMMarkStack::allocate(size_t capacity) {
  // allocate a stack of the requisite depth
  ReservedSpace rs(ReservedSpace::allocation_align_size_up(capacity * sizeof(oop)));
  if (!rs.is_reserved()) {
    log_warning(gc)("ConcurrentMark MarkStack allocation failure");
    return false;
  }
  MemTracker::record_virtual_memory_type((address)rs.base(), mtGC);
  if (!_virtual_space.initialize(rs, rs.size())) {
    log_warning(gc)("ConcurrentMark MarkStack backing store failure");
    // Release the virtual memory reserved for the marking stack
    rs.release();
    return false;
  }
  assert(_virtual_space.committed_size() == rs.size(),
         "Didn't reserve backing store for all of G1ConcurrentMark stack?");
  _base = (oop*) _virtual_space.low();
  setEmpty();
  _capacity = (jint) capacity;
  _saved_index = -1;
  _should_expand = false;
  return true;
}

void G1CMMarkStack::expand() {
  // Called, during remark, if we've overflown the marking stack during marking.
  assert(isEmpty(), "stack should been emptied while handling overflow");
  assert(_capacity <= (jint) MarkStackSizeMax, "stack bigger than permitted");
  // Clear expansion flag
  _should_expand = false;
  if (_capacity == (jint) MarkStackSizeMax) {
    log_trace(gc)("(benign) Can't expand marking stack capacity, at max size limit");
    return;
  }
  // Double capacity if possible
  jint new_capacity = MIN2(_capacity*2, (jint) MarkStackSizeMax);
  // Do not give up existing stack until we have managed to
  // get the double capacity that we desired.
  ReservedSpace rs(ReservedSpace::allocation_align_size_up(new_capacity *
                                                           sizeof(oop)));
  if (rs.is_reserved()) {
    // Release the backing store associated with old stack
    _virtual_space.release();
    // Reinitialize virtual space for new stack
    if (!_virtual_space.initialize(rs, rs.size())) {
      fatal("Not enough swap for expanded marking stack capacity");
    }
    _base = (oop*)(_virtual_space.low());
    _index = 0;
    _capacity = new_capacity;
  } else {
    // Failed to double capacity, continue;
    log_trace(gc)("(benign) Failed to expand marking stack capacity from " SIZE_FORMAT "K to " SIZE_FORMAT "K",
                  _capacity / K, new_capacity / K);
  }
}

void G1CMMarkStack::set_should_expand() {
  // If we're resetting the marking state because of an
  // marking stack overflow, record that we should, if
  // possible, expand the stack.
  _should_expand = _cm->has_overflown();
}

G1CMMarkStack::~G1CMMarkStack() {
  if (_base != NULL) {
    _base = NULL;
    _virtual_space.release();
  }
}

void G1CMMarkStack::par_push_arr(oop* ptr_arr, int n) {
  MutexLockerEx x(ParGCRareEvent_lock, Mutex::_no_safepoint_check_flag);
  jint start = _index;
  jint next_index = start + n;
  if (next_index > _capacity) {
    _overflow = true;
    return;
  }
  // Otherwise.
  _index = next_index;
  for (int i = 0; i < n; i++) {
    int ind = start + i;
    assert(ind < _capacity, "By overflow test above.");
    _base[ind] = ptr_arr[i];
  }
}

bool G1CMMarkStack::par_pop_arr(oop* ptr_arr, int max, int* n) {
  MutexLockerEx x(ParGCRareEvent_lock, Mutex::_no_safepoint_check_flag);
  jint index = _index;
  if (index == 0) {
    *n = 0;
    return false;
  } else {
    int k = MIN2(max, index);
    jint  new_ind = index - k;
    for (int j = 0; j < k; j++) {
      ptr_arr[j] = _base[new_ind + j];
    }
    _index = new_ind;
    *n = k;
    return true;
  }
}

void G1CMMarkStack::note_start_of_gc() {
  assert(_saved_index == -1,
         "note_start_of_gc()/end_of_gc() bracketed incorrectly");
  _saved_index = _index;
}

void G1CMMarkStack::note_end_of_gc() {
  // This is intentionally a guarantee, instead of an assert. If we
  // accidentally add something to the mark stack during GC, it
  // will be a correctness issue so it's better if we crash. we'll
  // only check this once per GC anyway, so it won't be a performance
  // issue in any way.
  guarantee(_saved_index == _index,
            "saved index: %d index: %d", _saved_index, _index);
  _saved_index = -1;
}

G1CMRootRegions::G1CMRootRegions() :
  _young_list(NULL), _cm(NULL), _scan_in_progress(false),
  _should_abort(false), _claimed_survivor_index(0) { }

void G1CMRootRegions::init(G1CollectedHeap* g1h, G1ConcurrentMark* cm) {
  _young_list = g1h->young_list();
  _cm = cm;
}

void G1CMRootRegions::prepare_for_scan() {
  assert(!scan_in_progress(), "pre-condition");

  // Currently, only survivors can be root regions.
  _claimed_survivor_index = 0;
  _scan_in_progress = true;
  _should_abort = false;
}

HeapRegion* G1CMRootRegions::claim_next() {
  if (_should_abort) {
    // If someone has set the should_abort flag, we return NULL to
    // force the caller to bail out of their loop.
    return NULL;
  }

  // Currently, only survivors can be root regions.
  const GrowableArray<HeapRegion*>* survivor_regions = _young_list->survivor_regions();

  int claimed_index = Atomic::add(1, &_claimed_survivor_index) - 1;
  if (claimed_index < survivor_regions->length()) {
    return survivor_regions->at(claimed_index);
  }
  return NULL;
}

void G1CMRootRegions::notify_scan_done() {
  MutexLockerEx x(RootRegionScan_lock, Mutex::_no_safepoint_check_flag);
  _scan_in_progress = false;
  RootRegionScan_lock->notify_all();
}

void G1CMRootRegions::cancel_scan() {
  notify_scan_done();
}

void G1CMRootRegions::scan_finished() {
  assert(scan_in_progress(), "pre-condition");

  // Currently, only survivors can be root regions.
  if (!_should_abort) {
    assert(_claimed_survivor_index >= _young_list->survivor_regions()->length(),
           "we should have claimed all survivors, claimed index = %d, length = %d",
           _claimed_survivor_index, _young_list->survivor_regions()->length());
  }

  notify_scan_done();
}

bool G1CMRootRegions::wait_until_scan_finished() {
  if (!scan_in_progress()) return false;

  {
    MutexLockerEx x(RootRegionScan_lock, Mutex::_no_safepoint_check_flag);
    while (scan_in_progress()) {
      RootRegionScan_lock->wait(Mutex::_no_safepoint_check_flag);
    }
  }
  return true;
}

uint G1ConcurrentMark::scale_parallel_threads(uint n_par_threads) {
  return MAX2((n_par_threads + 2) / 4, 1U);
}

G1ConcurrentMark::G1ConcurrentMark(G1CollectedHeap* g1h, G1RegionToSpaceMapper* prev_bitmap_storage, G1RegionToSpaceMapper* next_bitmap_storage) :
  _g1h(g1h),
  _markBitMap1(),
  _markBitMap2(),
  _parallel_marking_threads(0),
  _max_parallel_marking_threads(0),
  _sleep_factor(0.0),
  _marking_task_overhead(1.0),
  _cleanup_list("Cleanup List"),

  _prevMarkBitMap(&_markBitMap1),
  _nextMarkBitMap(&_markBitMap2),

  _markStack(this),
  // _finger set in set_non_marking_state

  _max_worker_id(ParallelGCThreads),
  // _active_tasks set in set_non_marking_state
  // _tasks set inside the constructor
  _task_queues(new G1CMTaskQueueSet((int) _max_worker_id)),
  _terminator(ParallelTaskTerminator((int) _max_worker_id, _task_queues)),

  _has_overflown(false),
  _concurrent(false),
  _has_aborted(false),
  _restart_for_overflow(false),
  _concurrent_marking_in_progress(false),
  _gc_timer_cm(new (ResourceObj::C_HEAP, mtGC) ConcurrentGCTimer()),
  _gc_tracer_cm(new (ResourceObj::C_HEAP, mtGC) G1OldTracer()),

  // _verbose_level set below

  _init_times(),
  _remark_times(), _remark_mark_times(), _remark_weak_ref_times(),
  _cleanup_times(),
  _total_counting_time(0.0),
  _total_rs_scrub_time(0.0),

  _parallel_workers(NULL),

  _completed_initialization(false) {

  _markBitMap1.initialize(g1h->reserved_region(), prev_bitmap_storage);
  _markBitMap2.initialize(g1h->reserved_region(), next_bitmap_storage);

  // Create & start a ConcurrentMark thread.
  _cmThread = new ConcurrentMarkThread(this);
  assert(cmThread() != NULL, "CM Thread should have been created");
  assert(cmThread()->cm() != NULL, "CM Thread should refer to this cm");
  if (_cmThread->osthread() == NULL) {
      vm_shutdown_during_initialization("Could not create ConcurrentMarkThread");
  }

  assert(CGC_lock != NULL, "Where's the CGC_lock?");
  assert(_markBitMap1.covers(g1h->reserved_region()), "_markBitMap1 inconsistency");
  assert(_markBitMap2.covers(g1h->reserved_region()), "_markBitMap2 inconsistency");

  SATBMarkQueueSet& satb_qs = JavaThread::satb_mark_queue_set();
  satb_qs.set_buffer_size(G1SATBBufferSize);

  _root_regions.init(_g1h, this);

  if (ConcGCThreads > ParallelGCThreads) {
    log_warning(gc)("Can't have more ConcGCThreads (%u) than ParallelGCThreads (%u).",
                    ConcGCThreads, ParallelGCThreads);
    return;
  }
  if (!FLAG_IS_DEFAULT(ConcGCThreads) && ConcGCThreads > 0) {
    // Note: ConcGCThreads has precedence over G1MarkingOverheadPercent
    // if both are set
    _sleep_factor             = 0.0;
    _marking_task_overhead    = 1.0;
  } else if (G1MarkingOverheadPercent > 0) {
    // We will calculate the number of parallel marking threads based
    // on a target overhead with respect to the soft real-time goal
    double marking_overhead = (double) G1MarkingOverheadPercent / 100.0;
    double overall_cm_overhead =
      (double) MaxGCPauseMillis * marking_overhead /
      (double) GCPauseIntervalMillis;
    double cpu_ratio = 1.0 / (double) os::processor_count();
    double marking_thread_num = ceil(overall_cm_overhead / cpu_ratio);
    double marking_task_overhead =
      overall_cm_overhead / marking_thread_num *
                                              (double) os::processor_count();
    double sleep_factor =
                       (1.0 - marking_task_overhead) / marking_task_overhead;

    FLAG_SET_ERGO(uint, ConcGCThreads, (uint) marking_thread_num);
    _sleep_factor             = sleep_factor;
    _marking_task_overhead    = marking_task_overhead;
  } else {
    // Calculate the number of parallel marking threads by scaling
    // the number of parallel GC threads.
    uint marking_thread_num = scale_parallel_threads(ParallelGCThreads);
    FLAG_SET_ERGO(uint, ConcGCThreads, marking_thread_num);
    _sleep_factor             = 0.0;
    _marking_task_overhead    = 1.0;
  }

  assert(ConcGCThreads > 0, "Should have been set");
  _parallel_marking_threads = ConcGCThreads;
  _max_parallel_marking_threads = _parallel_marking_threads;

  _parallel_workers = new WorkGang("G1 Marker",
       _max_parallel_marking_threads, false, true);
  if (_parallel_workers == NULL) {
    vm_exit_during_initialization("Failed necessary allocation.");
  } else {
    _parallel_workers->initialize_workers();
  }

  if (FLAG_IS_DEFAULT(MarkStackSize)) {
    size_t mark_stack_size =
      MIN2(MarkStackSizeMax,
          MAX2(MarkStackSize, (size_t) (parallel_marking_threads() * TASKQUEUE_SIZE)));
    // Verify that the calculated value for MarkStackSize is in range.
    // It would be nice to use the private utility routine from Arguments.
    if (!(mark_stack_size >= 1 && mark_stack_size <= MarkStackSizeMax)) {
      log_warning(gc)("Invalid value calculated for MarkStackSize (" SIZE_FORMAT "): "
                      "must be between 1 and " SIZE_FORMAT,
                      mark_stack_size, MarkStackSizeMax);
      return;
    }
    FLAG_SET_ERGO(size_t, MarkStackSize, mark_stack_size);
  } else {
    // Verify MarkStackSize is in range.
    if (FLAG_IS_CMDLINE(MarkStackSize)) {
      if (FLAG_IS_DEFAULT(MarkStackSizeMax)) {
        if (!(MarkStackSize >= 1 && MarkStackSize <= MarkStackSizeMax)) {
          log_warning(gc)("Invalid value specified for MarkStackSize (" SIZE_FORMAT "): "
                          "must be between 1 and " SIZE_FORMAT,
                          MarkStackSize, MarkStackSizeMax);
          return;
        }
      } else if (FLAG_IS_CMDLINE(MarkStackSizeMax)) {
        if (!(MarkStackSize >= 1 && MarkStackSize <= MarkStackSizeMax)) {
          log_warning(gc)("Invalid value specified for MarkStackSize (" SIZE_FORMAT ")"
                          " or for MarkStackSizeMax (" SIZE_FORMAT ")",
                          MarkStackSize, MarkStackSizeMax);
          return;
        }
      }
    }
  }

  if (!_markStack.allocate(MarkStackSize)) {
    log_warning(gc)("Failed to allocate CM marking stack");
    return;
  }

  _tasks = NEW_C_HEAP_ARRAY(G1CMTask*, _max_worker_id, mtGC);
  _accum_task_vtime = NEW_C_HEAP_ARRAY(double, _max_worker_id, mtGC);

  // so that the assertion in MarkingTaskQueue::task_queue doesn't fail
  _active_tasks = _max_worker_id;

  for (uint i = 0; i < _max_worker_id; ++i) {
    G1CMTaskQueue* task_queue = new G1CMTaskQueue();
    task_queue->initialize();
    _task_queues->register_queue(i, task_queue);

    _tasks[i] = new G1CMTask(i, this, task_queue, _task_queues);

    _accum_task_vtime[i] = 0.0;
  }

  // so that the call below can read a sensible value
  _heap_start = g1h->reserved_region().start();
  set_non_marking_state();
  _completed_initialization = true;
}

void G1ConcurrentMark::reset() {
  // Starting values for these two. This should be called in a STW
  // phase.
  MemRegion reserved = _g1h->g1_reserved();
  _heap_start = reserved.start();
  _heap_end   = reserved.end();

  // Separated the asserts so that we know which one fires.
  assert(_heap_start != NULL, "heap bounds should look ok");
  assert(_heap_end != NULL, "heap bounds should look ok");
  assert(_heap_start < _heap_end, "heap bounds should look ok");

  // Reset all the marking data structures and any necessary flags
  reset_marking_state();

  // We do reset all of them, since different phases will use
  // different number of active threads. So, it's easiest to have all
  // of them ready.
  for (uint i = 0; i < _max_worker_id; ++i) {
    _tasks[i]->reset(_nextMarkBitMap);
  }

  // we need this to make sure that the flag is on during the evac
  // pause with initial mark piggy-backed
  set_concurrent_marking_in_progress();
}


void G1ConcurrentMark::reset_marking_state(bool clear_overflow) {
  _markStack.set_should_expand();
  _markStack.setEmpty();        // Also clears the _markStack overflow flag
  if (clear_overflow) {
    clear_has_overflown();
  } else {
    assert(has_overflown(), "pre-condition");
  }
  _finger = _heap_start;

  for (uint i = 0; i < _max_worker_id; ++i) {
    G1CMTaskQueue* queue = _task_queues->queue(i);
    queue->set_empty();
  }
}

void G1ConcurrentMark::set_concurrency(uint active_tasks) {
  assert(active_tasks <= _max_worker_id, "we should not have more");

  _active_tasks = active_tasks;
  // Need to update the three data structures below according to the
  // number of active threads for this phase.
  _terminator   = ParallelTaskTerminator((int) active_tasks, _task_queues);
  _first_overflow_barrier_sync.set_n_workers((int) active_tasks);
  _second_overflow_barrier_sync.set_n_workers((int) active_tasks);
}

void G1ConcurrentMark::set_concurrency_and_phase(uint active_tasks, bool concurrent) {
  set_concurrency(active_tasks);

  _concurrent = concurrent;
  // We propagate this to all tasks, not just the active ones.
  for (uint i = 0; i < _max_worker_id; ++i)
    _tasks[i]->set_concurrent(concurrent);

  if (concurrent) {
    set_concurrent_marking_in_progress();
  } else {
    // We currently assume that the concurrent flag has been set to
    // false before we start remark. At this point we should also be
    // in a STW phase.
    assert(!concurrent_marking_in_progress(), "invariant");
    assert(out_of_regions(),
           "only way to get here: _finger: " PTR_FORMAT ", _heap_end: " PTR_FORMAT,
           p2i(_finger), p2i(_heap_end));
  }
}

void G1ConcurrentMark::set_non_marking_state() {
  // We set the global marking state to some default values when we're
  // not doing marking.
  reset_marking_state();
  _active_tasks = 0;
  clear_concurrent_marking_in_progress();
}

G1ConcurrentMark::~G1ConcurrentMark() {
  // The G1ConcurrentMark instance is never freed.
  ShouldNotReachHere();
}

class G1ClearBitMapTask : public AbstractGangTask {
public:
  static size_t chunk_size() { return M; }

private:
  // Heap region closure used for clearing the given mark bitmap.
  class G1ClearBitmapHRClosure : public HeapRegionClosure {
  private:
    G1CMBitMap* _bitmap;
    G1ConcurrentMark* _cm;
  public:
    G1ClearBitmapHRClosure(G1CMBitMap* bitmap, G1ConcurrentMark* cm) : HeapRegionClosure(), _cm(cm), _bitmap(bitmap) {
    }

    virtual bool doHeapRegion(HeapRegion* r) {
      size_t const chunk_size_in_words = G1ClearBitMapTask::chunk_size() / HeapWordSize;

      HeapWord* cur = r->bottom();
      HeapWord* const end = r->end();

      while (cur < end) {
        MemRegion mr(cur, MIN2(cur + chunk_size_in_words, end));
        _bitmap->clear_range(mr);

        cur += chunk_size_in_words;

        // Abort iteration if after yielding the marking has been aborted.
        if (_cm != NULL && _cm->do_yield_check() && _cm->has_aborted()) {
          return true;
        }
        // Repeat the asserts from before the start of the closure. We will do them
        // as asserts here to minimize their overhead on the product. However, we
        // will have them as guarantees at the beginning / end of the bitmap
        // clearing to get some checking in the product.
        assert(_cm == NULL || _cm->cmThread()->during_cycle(), "invariant");
        assert(_cm == NULL || !G1CollectedHeap::heap()->collector_state()->mark_in_progress(), "invariant");
      }
      assert(cur == end, "Must have completed iteration over the bitmap for region %u.", r->hrm_index());

      return false;
    }
  };

  G1ClearBitmapHRClosure _cl;
  HeapRegionClaimer _hr_claimer;
  bool _suspendible; // If the task is suspendible, workers must join the STS.

public:
  G1ClearBitMapTask(G1CMBitMap* bitmap, G1ConcurrentMark* cm, uint n_workers, bool suspendible) :
    AbstractGangTask("G1 Clear Bitmap"),
    _cl(bitmap, suspendible ? cm : NULL),
    _hr_claimer(n_workers),
    _suspendible(suspendible)
  { }

  void work(uint worker_id) {
    SuspendibleThreadSetJoiner sts_join(_suspendible);
    G1CollectedHeap::heap()->heap_region_par_iterate(&_cl, worker_id, &_hr_claimer, true);
  }

  bool is_complete() {
    return _cl.complete();
  }
};

void G1ConcurrentMark::clear_bitmap(G1CMBitMap* bitmap, WorkGang* workers, bool may_yield) {
  assert(may_yield || SafepointSynchronize::is_at_safepoint(), "Non-yielding bitmap clear only allowed at safepoint.");

  size_t const num_bytes_to_clear = (HeapRegion::GrainBytes * _g1h->num_regions()) / G1CMBitMap::heap_map_factor();
  size_t const num_chunks = align_size_up(num_bytes_to_clear, G1ClearBitMapTask::chunk_size()) / G1ClearBitMapTask::chunk_size();

  uint const num_workers = (uint)MIN2(num_chunks, (size_t)workers->active_workers());

  G1ClearBitMapTask cl(bitmap, this, num_workers, may_yield);

  log_debug(gc, ergo)("Running %s with %u workers for " SIZE_FORMAT " work units.", cl.name(), num_workers, num_chunks);
  workers->run_task(&cl, num_workers);
  guarantee(!may_yield || cl.is_complete(), "Must have completed iteration when not yielding.");
}

void G1ConcurrentMark::cleanup_for_next_mark() {
  // Make sure that the concurrent mark thread looks to still be in
  // the current cycle.
  guarantee(cmThread()->during_cycle(), "invariant");

  // We are finishing up the current cycle by clearing the next
  // marking bitmap and getting it ready for the next cycle. During
  // this time no other cycle can start. So, let's make sure that this
  // is the case.
  guarantee(!_g1h->collector_state()->mark_in_progress(), "invariant");

  clear_bitmap(_nextMarkBitMap, _parallel_workers, true);

  // Clear the live count data. If the marking has been aborted, the abort()
  // call already did that.
  if (!has_aborted()) {
    clear_live_data(_parallel_workers);
    DEBUG_ONLY(verify_live_data_clear());
  }

  // Repeat the asserts from above.
  guarantee(cmThread()->during_cycle(), "invariant");
  guarantee(!_g1h->collector_state()->mark_in_progress(), "invariant");
}

void G1ConcurrentMark::clear_prev_bitmap(WorkGang* workers) {
  assert(SafepointSynchronize::is_at_safepoint(), "Should only clear the entire prev bitmap at a safepoint.");
  clear_bitmap((G1CMBitMap*)_prevMarkBitMap, workers, false);
}

class CheckBitmapClearHRClosure : public HeapRegionClosure {
  G1CMBitMap* _bitmap;
  bool _error;
 public:
  CheckBitmapClearHRClosure(G1CMBitMap* bitmap) : _bitmap(bitmap) {
  }

  virtual bool doHeapRegion(HeapRegion* r) {
    // This closure can be called concurrently to the mutator, so we must make sure
    // that the result of the getNextMarkedWordAddress() call is compared to the
    // value passed to it as limit to detect any found bits.
    // end never changes in G1.
    HeapWord* end = r->end();
    return _bitmap->getNextMarkedWordAddress(r->bottom(), end) != end;
  }
};

bool G1ConcurrentMark::nextMarkBitmapIsClear() {
  CheckBitmapClearHRClosure cl(_nextMarkBitMap);
  _g1h->heap_region_iterate(&cl);
  return cl.complete();
}

class NoteStartOfMarkHRClosure: public HeapRegionClosure {
public:
  bool doHeapRegion(HeapRegion* r) {
    r->note_start_of_marking();
    return false;
  }
};

void G1ConcurrentMark::checkpointRootsInitialPre() {
  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  G1Policy* g1p = g1h->g1_policy();

  _has_aborted = false;

  // Initialize marking structures. This has to be done in a STW phase.
  reset();

  // For each region note start of marking.
  NoteStartOfMarkHRClosure startcl;
  g1h->heap_region_iterate(&startcl);
}


void G1ConcurrentMark::checkpointRootsInitialPost() {
  G1CollectedHeap*   g1h = G1CollectedHeap::heap();

  // Start Concurrent Marking weak-reference discovery.
  ReferenceProcessor* rp = g1h->ref_processor_cm();
  // enable ("weak") refs discovery
  rp->enable_discovery();
  rp->setup_policy(false); // snapshot the soft ref policy to be used in this cycle

  SATBMarkQueueSet& satb_mq_set = JavaThread::satb_mark_queue_set();
  // This is the start of  the marking cycle, we're expected all
  // threads to have SATB queues with active set to false.
  satb_mq_set.set_active_all_threads(true, /* new active value */
                                     false /* expected_active */);

  _root_regions.prepare_for_scan();

  // update_g1_committed() will be called at the end of an evac pause
  // when marking is on. So, it's also called at the end of the
  // initial-mark pause to update the heap end, if the heap expands
  // during it. No need to call it here.
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

  // If we're executing the concurrent phase of marking, reset the marking
  // state; otherwise the marking state is reset after reference processing,
  // during the remark pause.
  // If we reset here as a result of an overflow during the remark we will
  // see assertion failures from any subsequent set_concurrency_and_phase()
  // calls.
  if (concurrent()) {
    // let the task associated with with worker 0 do this
    if (worker_id == 0) {
      // task 0 is responsible for clearing the global data structures
      // We should be here because of an overflow. During STW we should
      // not clear the overflow flag since we rely on it being true when
      // we exit this method to abort the pause and restart concurrent
      // marking.
      reset_marking_state(true /* clear_overflow */);

      log_info(gc, marking)("Concurrent Mark reset for overflow");
    }
  }

  // after this, each task should reset its own data structures then
  // then go into the second barrier
}

void G1ConcurrentMark::enter_second_sync_barrier(uint worker_id) {
  SuspendibleThreadSetLeaver sts_leave(concurrent());
  _second_overflow_barrier_sync.enter();

  // at this point everything should be re-initialized and ready to go
}

class G1CMConcurrentMarkingTask: public AbstractGangTask {
private:
  G1ConcurrentMark*     _cm;
  ConcurrentMarkThread* _cmt;

public:
  void work(uint worker_id) {
    assert(Thread::current()->is_ConcurrentGC_thread(),
           "this should only be done by a conc GC thread");
    ResourceMark rm;

    double start_vtime = os::elapsedVTime();

    {
      SuspendibleThreadSetJoiner sts_join;

      assert(worker_id < _cm->active_tasks(), "invariant");
      G1CMTask* the_task = _cm->task(worker_id);
      the_task->record_start_time();
      if (!_cm->has_aborted()) {
        do {
          double start_vtime_sec = os::elapsedVTime();
          double mark_step_duration_ms = G1ConcMarkStepDurationMillis;

          the_task->do_marking_step(mark_step_duration_ms,
                                    true  /* do_termination */,
                                    false /* is_serial*/);

          double end_vtime_sec = os::elapsedVTime();
          double elapsed_vtime_sec = end_vtime_sec - start_vtime_sec;
          _cm->clear_has_overflown();

          _cm->do_yield_check();

          jlong sleep_time_ms;
          if (!_cm->has_aborted() && the_task->has_aborted()) {
            sleep_time_ms =
              (jlong) (elapsed_vtime_sec * _cm->sleep_factor() * 1000.0);
            {
              SuspendibleThreadSetLeaver sts_leave;
              os::sleep(Thread::current(), sleep_time_ms, false);
            }
          }
        } while (!_cm->has_aborted() && the_task->has_aborted());
      }
      the_task->record_end_time();
      guarantee(!the_task->has_aborted() || _cm->has_aborted(), "invariant");
    }

    double end_vtime = os::elapsedVTime();
    _cm->update_accum_task_vtime(worker_id, end_vtime - start_vtime);
  }

  G1CMConcurrentMarkingTask(G1ConcurrentMark* cm,
                            ConcurrentMarkThread* cmt) :
      AbstractGangTask("Concurrent Mark"), _cm(cm), _cmt(cmt) { }

  ~G1CMConcurrentMarkingTask() { }
};

// Calculates the number of active workers for a concurrent
// phase.
uint G1ConcurrentMark::calc_parallel_marking_threads() {
  uint n_conc_workers = 0;
  if (!UseDynamicNumberOfGCThreads ||
      (!FLAG_IS_DEFAULT(ConcGCThreads) &&
       !ForceDynamicNumberOfGCThreads)) {
    n_conc_workers = max_parallel_marking_threads();
  } else {
    n_conc_workers =
      AdaptiveSizePolicy::calc_default_active_workers(
                                   max_parallel_marking_threads(),
                                   1, /* Minimum workers */
                                   parallel_marking_threads(),
                                   Threads::number_of_non_daemon_threads());
    // Don't scale down "n_conc_workers" by scale_parallel_threads() because
    // that scaling has already gone into "_max_parallel_marking_threads".
  }
  assert(n_conc_workers > 0, "Always need at least 1");
  return n_conc_workers;
}

void G1ConcurrentMark::scanRootRegion(HeapRegion* hr) {
  // Currently, only survivors can be root regions.
  assert(hr->next_top_at_mark_start() == hr->bottom(), "invariant");
  G1RootRegionScanClosure cl(_g1h, this);

  const uintx interval = PrefetchScanIntervalInBytes;
  HeapWord* curr = hr->bottom();
  const HeapWord* end = hr->top();
  while (curr < end) {
    Prefetch::read(curr, interval);
    oop obj = oop(curr);
    int size = obj->oop_iterate_size(&cl);
    assert(size == obj->size(), "sanity");
    curr += size;
  }
}

class G1CMRootRegionScanTask : public AbstractGangTask {
private:
  G1ConcurrentMark* _cm;

public:
  G1CMRootRegionScanTask(G1ConcurrentMark* cm) :
    AbstractGangTask("Root Region Scan"), _cm(cm) { }

  void work(uint worker_id) {
    assert(Thread::current()->is_ConcurrentGC_thread(),
           "this should only be done by a conc GC thread");

    G1CMRootRegions* root_regions = _cm->root_regions();
    HeapRegion* hr = root_regions->claim_next();
    while (hr != NULL) {
      _cm->scanRootRegion(hr);
      hr = root_regions->claim_next();
    }
  }
};

void G1ConcurrentMark::scan_root_regions() {
  // scan_in_progress() will have been set to true only if there was
  // at least one root region to scan. So, if it's false, we
  // should not attempt to do any further work.
  if (root_regions()->scan_in_progress()) {
    assert(!has_aborted(), "Aborting before root region scanning is finished not supported.");

    _parallel_marking_threads = calc_parallel_marking_threads();
    assert(parallel_marking_threads() <= max_parallel_marking_threads(),
           "Maximum number of marking threads exceeded");
    uint active_workers = MAX2(1U, parallel_marking_threads());

    G1CMRootRegionScanTask task(this);
    _parallel_workers->set_active_workers(active_workers);
    _parallel_workers->run_task(&task);

    // It's possible that has_aborted() is true here without actually
    // aborting the survivor scan earlier. This is OK as it's
    // mainly used for sanity checking.
    root_regions()->scan_finished();
  }
}

void G1ConcurrentMark::concurrent_cycle_start() {
  _gc_timer_cm->register_gc_start();

  _gc_tracer_cm->report_gc_start(GCCause::_no_gc /* first parameter is not used */, _gc_timer_cm->gc_start());

  _g1h->trace_heap_before_gc(_gc_tracer_cm);
}

void G1ConcurrentMark::concurrent_cycle_end() {
  _g1h->trace_heap_after_gc(_gc_tracer_cm);

  if (has_aborted()) {
    _gc_tracer_cm->report_concurrent_mode_failure();
  }

  _gc_timer_cm->register_gc_end();

  _gc_tracer_cm->report_gc_end(_gc_timer_cm->gc_end(), _gc_timer_cm->time_partitions());
}

void G1ConcurrentMark::mark_from_roots() {
  // we might be tempted to assert that:
  // assert(asynch == !SafepointSynchronize::is_at_safepoint(),
  //        "inconsistent argument?");
  // However that wouldn't be right, because it's possible that
  // a safepoint is indeed in progress as a younger generation
  // stop-the-world GC happens even as we mark in this generation.

  _restart_for_overflow = false;

  // _g1h has _n_par_threads
  _parallel_marking_threads = calc_parallel_marking_threads();
  assert(parallel_marking_threads() <= max_parallel_marking_threads(),
    "Maximum number of marking threads exceeded");

  uint active_workers = MAX2(1U, parallel_marking_threads());
  assert(active_workers > 0, "Should have been set");

  // Parallel task terminator is set in "set_concurrency_and_phase()"
  set_concurrency_and_phase(active_workers, true /* concurrent */);

  G1CMConcurrentMarkingTask markingTask(this, cmThread());
  _parallel_workers->set_active_workers(active_workers);
  _parallel_workers->run_task(&markingTask);
  print_stats();
}

void G1ConcurrentMark::checkpointRootsFinal(bool clear_all_soft_refs) {
  // world is stopped at this checkpoint
  assert(SafepointSynchronize::is_at_safepoint(),
         "world should be stopped");

  G1CollectedHeap* g1h = G1CollectedHeap::heap();

  // If a full collection has happened, we shouldn't do this.
  if (has_aborted()) {
    g1h->collector_state()->set_mark_in_progress(false); // So bitmap clearing isn't confused
    return;
  }

  SvcGCMarker sgcm(SvcGCMarker::OTHER);

  if (VerifyDuringGC) {
    HandleMark hm;  // handle scope
    g1h->prepare_for_verify();
    Universe::verify(VerifyOption_G1UsePrevMarking, "During GC (before)");
  }
  g1h->verifier()->check_bitmaps("Remark Start");

  G1Policy* g1p = g1h->g1_policy();
  g1p->record_concurrent_mark_remark_start();

  double start = os::elapsedTime();

  checkpointRootsFinalWork();

  double mark_work_end = os::elapsedTime();

  weakRefsWork(clear_all_soft_refs);

  if (has_overflown()) {
    // Oops.  We overflowed.  Restart concurrent marking.
    _restart_for_overflow = true;

    // Verify the heap w.r.t. the previous marking bitmap.
    if (VerifyDuringGC) {
      HandleMark hm;  // handle scope
      g1h->prepare_for_verify();
      Universe::verify(VerifyOption_G1UsePrevMarking, "During GC (overflow)");
    }

    // Clear the marking state because we will be restarting
    // marking due to overflowing the global mark stack.
    reset_marking_state();
  } else {
    SATBMarkQueueSet& satb_mq_set = JavaThread::satb_mark_queue_set();
    // We're done with marking.
    // This is the end of  the marking cycle, we're expected all
    // threads to have SATB queues with active set to true.
    satb_mq_set.set_active_all_threads(false, /* new active value */
                                       true /* expected_active */);

    if (VerifyDuringGC) {
      HandleMark hm;  // handle scope
      g1h->prepare_for_verify();
      Universe::verify(VerifyOption_G1UseNextMarking, "During GC (after)");
    }
    g1h->verifier()->check_bitmaps("Remark End");
    assert(!restart_for_overflow(), "sanity");
    // Completely reset the marking state since marking completed
    set_non_marking_state();
  }

  // Expand the marking stack, if we have to and if we can.
  if (_markStack.should_expand()) {
    _markStack.expand();
  }

  // Statistics
  double now = os::elapsedTime();
  _remark_mark_times.add((mark_work_end - start) * 1000.0);
  _remark_weak_ref_times.add((now - mark_work_end) * 1000.0);
  _remark_times.add((now - start) * 1000.0);

  g1p->record_concurrent_mark_remark_end();

  G1CMIsAliveClosure is_alive(g1h);
  _gc_tracer_cm->report_object_count_after_gc(&is_alive);
}

class G1NoteEndOfConcMarkClosure : public HeapRegionClosure {
  G1CollectedHeap* _g1;
  size_t _freed_bytes;
  FreeRegionList* _local_cleanup_list;
  uint _old_regions_removed;
  uint _humongous_regions_removed;
  HRRSCleanupTask* _hrrs_cleanup_task;

public:
  G1NoteEndOfConcMarkClosure(G1CollectedHeap* g1,
                             FreeRegionList* local_cleanup_list,
                             HRRSCleanupTask* hrrs_cleanup_task) :
    _g1(g1),
    _freed_bytes(0),
    _local_cleanup_list(local_cleanup_list),
    _old_regions_removed(0),
    _humongous_regions_removed(0),
    _hrrs_cleanup_task(hrrs_cleanup_task) { }

  size_t freed_bytes() { return _freed_bytes; }
  const uint old_regions_removed() { return _old_regions_removed; }
  const uint humongous_regions_removed() { return _humongous_regions_removed; }

  bool doHeapRegion(HeapRegion *hr) {
    if (hr->is_archive()) {
      return false;
    }
    _g1->reset_gc_time_stamps(hr);
    hr->note_end_of_marking();

    if (hr->used() > 0 && hr->max_live_bytes() == 0 && !hr->is_young()) {
      _freed_bytes += hr->used();
      hr->set_containing_set(NULL);
      if (hr->is_humongous()) {
        _humongous_regions_removed++;
        _g1->free_humongous_region(hr, _local_cleanup_list, true);
      } else {
        _old_regions_removed++;
        _g1->free_region(hr, _local_cleanup_list, true);
      }
    } else {
      hr->rem_set()->do_cleanup_work(_hrrs_cleanup_task);
    }

    return false;
  }
};

class G1ParNoteEndTask: public AbstractGangTask {
  friend class G1NoteEndOfConcMarkClosure;

protected:
  G1CollectedHeap* _g1h;
  FreeRegionList* _cleanup_list;
  HeapRegionClaimer _hrclaimer;

public:
  G1ParNoteEndTask(G1CollectedHeap* g1h, FreeRegionList* cleanup_list, uint n_workers) :
      AbstractGangTask("G1 note end"), _g1h(g1h), _cleanup_list(cleanup_list), _hrclaimer(n_workers) {
  }

  void work(uint worker_id) {
    FreeRegionList local_cleanup_list("Local Cleanup List");
    HRRSCleanupTask hrrs_cleanup_task;
    G1NoteEndOfConcMarkClosure g1_note_end(_g1h, &local_cleanup_list,
                                           &hrrs_cleanup_task);
    _g1h->heap_region_par_iterate(&g1_note_end, worker_id, &_hrclaimer);
    assert(g1_note_end.complete(), "Shouldn't have yielded!");

    // Now update the lists
    _g1h->remove_from_old_sets(g1_note_end.old_regions_removed(), g1_note_end.humongous_regions_removed());
    {
      MutexLockerEx x(ParGCRareEvent_lock, Mutex::_no_safepoint_check_flag);
      _g1h->decrement_summary_bytes(g1_note_end.freed_bytes());

      // If we iterate over the global cleanup list at the end of
      // cleanup to do this printing we will not guarantee to only
      // generate output for the newly-reclaimed regions (the list
      // might not be empty at the beginning of cleanup; we might
      // still be working on its previous contents). So we do the
      // printing here, before we append the new regions to the global
      // cleanup list.

      G1HRPrinter* hr_printer = _g1h->hr_printer();
      if (hr_printer->is_active()) {
        FreeRegionListIterator iter(&local_cleanup_list);
        while (iter.more_available()) {
          HeapRegion* hr = iter.get_next();
          hr_printer->cleanup(hr);
        }
      }

      _cleanup_list->add_ordered(&local_cleanup_list);
      assert(local_cleanup_list.is_empty(), "post-condition");

      HeapRegionRemSet::finish_cleanup_task(&hrrs_cleanup_task);
    }
  }
};

void G1ConcurrentMark::cleanup() {
  // world is stopped at this checkpoint
  assert(SafepointSynchronize::is_at_safepoint(),
         "world should be stopped");
  G1CollectedHeap* g1h = G1CollectedHeap::heap();

  // If a full collection has happened, we shouldn't do this.
  if (has_aborted()) {
    g1h->collector_state()->set_mark_in_progress(false); // So bitmap clearing isn't confused
    return;
  }

  g1h->verifier()->verify_region_sets_optional();

  if (VerifyDuringGC) {
    HandleMark hm;  // handle scope
    g1h->prepare_for_verify();
    Universe::verify(VerifyOption_G1UsePrevMarking, "During GC (before)");
  }
  g1h->verifier()->check_bitmaps("Cleanup Start");

  G1Policy* g1p = g1h->g1_policy();
  g1p->record_concurrent_mark_cleanup_start();

  double start = os::elapsedTime();

  HeapRegionRemSet::reset_for_cleanup_tasks();

  {
    GCTraceTime(Debug, gc)("Finalize Live Data");
    finalize_live_data();
  }

  if (VerifyDuringGC) {
    GCTraceTime(Debug, gc)("Verify Live Data");
    verify_live_data();
  }

  g1h->collector_state()->set_mark_in_progress(false);

  double count_end = os::elapsedTime();
  double this_final_counting_time = (count_end - start);
  _total_counting_time += this_final_counting_time;

  if (log_is_enabled(Trace, gc, liveness)) {
    G1PrintRegionLivenessInfoClosure cl("Post-Marking");
    _g1h->heap_region_iterate(&cl);
  }

  // Install newly created mark bitMap as "prev".
  swapMarkBitMaps();

  g1h->reset_gc_time_stamp();

  uint n_workers = _g1h->workers()->active_workers();

  // Note end of marking in all heap regions.
  G1ParNoteEndTask g1_par_note_end_task(g1h, &_cleanup_list, n_workers);
  g1h->workers()->run_task(&g1_par_note_end_task);
  g1h->check_gc_time_stamps();

  if (!cleanup_list_is_empty()) {
    // The cleanup list is not empty, so we'll have to process it
    // concurrently. Notify anyone else that might be wanting free
    // regions that there will be more free regions coming soon.
    g1h->set_free_regions_coming();
  }

  // call below, since it affects the metric by which we sort the heap
  // regions.
  if (G1ScrubRemSets) {
    double rs_scrub_start = os::elapsedTime();
    g1h->scrub_rem_set();
    _total_rs_scrub_time += (os::elapsedTime() - rs_scrub_start);
  }

  // this will also free any regions totally full of garbage objects,
  // and sort the regions.
  g1h->g1_policy()->record_concurrent_mark_cleanup_end();

  // Statistics.
  double end = os::elapsedTime();
  _cleanup_times.add((end - start) * 1000.0);

  // Clean up will have freed any regions completely full of garbage.
  // Update the soft reference policy with the new heap occupancy.
  Universe::update_heap_info_at_gc();

  if (VerifyDuringGC) {
    HandleMark hm;  // handle scope
    g1h->prepare_for_verify();
    Universe::verify(VerifyOption_G1UsePrevMarking, "During GC (after)");
  }

  g1h->verifier()->check_bitmaps("Cleanup End");

  g1h->verifier()->verify_region_sets_optional();

  // We need to make this be a "collection" so any collection pause that
  // races with it goes around and waits for completeCleanup to finish.
  g1h->increment_total_collections();

  // Clean out dead classes and update Metaspace sizes.
  if (ClassUnloadingWithConcurrentMark) {
    ClassLoaderDataGraph::purge();
  }
  MetaspaceGC::compute_new_size();

  // We reclaimed old regions so we should calculate the sizes to make
  // sure we update the old gen/space data.
  g1h->g1mm()->update_sizes();
  g1h->allocation_context_stats().update_after_mark();
}

void G1ConcurrentMark::complete_cleanup() {
  if (has_aborted()) return;

  G1CollectedHeap* g1h = G1CollectedHeap::heap();

  _cleanup_list.verify_optional();
  FreeRegionList tmp_free_list("Tmp Free List");

  log_develop_trace(gc, freelist)("G1ConcRegionFreeing [complete cleanup] : "
                                  "cleanup list has %u entries",
                                  _cleanup_list.length());

  // No one else should be accessing the _cleanup_list at this point,
  // so it is not necessary to take any locks
  while (!_cleanup_list.is_empty()) {
    HeapRegion* hr = _cleanup_list.remove_region(true /* from_head */);
    assert(hr != NULL, "Got NULL from a non-empty list");
    hr->par_clear();
    tmp_free_list.add_ordered(hr);

    // Instead of adding one region at a time to the secondary_free_list,
    // we accumulate them in the local list and move them a few at a
    // time. This also cuts down on the number of notify_all() calls
    // we do during this process. We'll also append the local list when
    // _cleanup_list is empty (which means we just removed the last
    // region from the _cleanup_list).
    if ((tmp_free_list.length() % G1SecondaryFreeListAppendLength == 0) ||
        _cleanup_list.is_empty()) {
      log_develop_trace(gc, freelist)("G1ConcRegionFreeing [complete cleanup] : "
                                      "appending %u entries to the secondary_free_list, "
                                      "cleanup list still has %u entries",
                                      tmp_free_list.length(),
                                      _cleanup_list.length());

      {
        MutexLockerEx x(SecondaryFreeList_lock, Mutex::_no_safepoint_check_flag);
        g1h->secondary_free_list_add(&tmp_free_list);
        SecondaryFreeList_lock->notify_all();
      }
#ifndef PRODUCT
      if (G1StressConcRegionFreeing) {
        for (uintx i = 0; i < G1StressConcRegionFreeingDelayMillis; ++i) {
          os::sleep(Thread::current(), (jlong) 1, false);
        }
      }
#endif
    }
  }
  assert(tmp_free_list.is_empty(), "post-condition");
}

// Supporting Object and Oop closures for reference discovery
// and processing in during marking

bool G1CMIsAliveClosure::do_object_b(oop obj) {
  HeapWord* addr = (HeapWord*)obj;
  return addr != NULL &&
         (!_g1->is_in_g1_reserved(addr) || !_g1->is_obj_ill(obj));
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

class G1CMKeepAliveAndDrainClosure: public OopClosure {
  G1ConcurrentMark* _cm;
  G1CMTask*         _task;
  int               _ref_counter_limit;
  int               _ref_counter;
  bool              _is_serial;
 public:
  G1CMKeepAliveAndDrainClosure(G1ConcurrentMark* cm, G1CMTask* task, bool is_serial) :
    _cm(cm), _task(task), _is_serial(is_serial),
    _ref_counter_limit(G1RefProcDrainInterval) {
    assert(_ref_counter_limit > 0, "sanity");
    assert(!_is_serial || _task->worker_id() == 0, "only task 0 for serial code");
    _ref_counter = _ref_counter_limit;
  }

  virtual void do_oop(narrowOop* p) { do_oop_work(p); }
  virtual void do_oop(      oop* p) { do_oop_work(p); }

  template <class T> void do_oop_work(T* p) {
    if (!_cm->has_overflown()) {
      oop obj = oopDesc::load_decode_heap_oop(p);
      _task->deal_with_reference(obj);
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
  }
};

// 'Drain' oop closure used by both serial and parallel reference processing.
// Uses the G1CMTask associated with a given worker thread (for serial
// reference processing the G1CMtask for worker 0 is used). Calls the
// do_marking_step routine, with an unbelievably large timeout value,
// to drain the marking data structures of the remaining entries
// added by the 'keep alive' oop closure above.

class G1CMDrainMarkingStackClosure: public VoidClosure {
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
      // entries that were pushed as a a result of applying the 'keep alive'
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

// Implementation of AbstractRefProcTaskExecutor for parallel
// reference processing at the end of G1 concurrent marking

class G1CMRefProcTaskExecutor: public AbstractRefProcTaskExecutor {
private:
  G1CollectedHeap*  _g1h;
  G1ConcurrentMark* _cm;
  WorkGang*         _workers;
  uint              _active_workers;

public:
  G1CMRefProcTaskExecutor(G1CollectedHeap* g1h,
                          G1ConcurrentMark* cm,
                          WorkGang* workers,
                          uint n_workers) :
    _g1h(g1h), _cm(cm),
    _workers(workers), _active_workers(n_workers) { }

  // Executes the given task using concurrent marking worker threads.
  virtual void execute(ProcessTask& task);
  virtual void execute(EnqueueTask& task);
};

class G1CMRefProcTaskProxy: public AbstractGangTask {
  typedef AbstractRefProcTaskExecutor::ProcessTask ProcessTask;
  ProcessTask&      _proc_task;
  G1CollectedHeap*  _g1h;
  G1ConcurrentMark* _cm;

public:
  G1CMRefProcTaskProxy(ProcessTask& proc_task,
                       G1CollectedHeap* g1h,
                       G1ConcurrentMark* cm) :
    AbstractGangTask("Process reference objects in parallel"),
    _proc_task(proc_task), _g1h(g1h), _cm(cm) {
    ReferenceProcessor* rp = _g1h->ref_processor_cm();
    assert(rp->processing_is_mt(), "shouldn't be here otherwise");
  }

  virtual void work(uint worker_id) {
    ResourceMark rm;
    HandleMark hm;
    G1CMTask* task = _cm->task(worker_id);
    G1CMIsAliveClosure g1_is_alive(_g1h);
    G1CMKeepAliveAndDrainClosure g1_par_keep_alive(_cm, task, false /* is_serial */);
    G1CMDrainMarkingStackClosure g1_par_drain(_cm, task, false /* is_serial */);

    _proc_task.work(worker_id, g1_is_alive, g1_par_keep_alive, g1_par_drain);
  }
};

void G1CMRefProcTaskExecutor::execute(ProcessTask& proc_task) {
  assert(_workers != NULL, "Need parallel worker threads.");
  assert(_g1h->ref_processor_cm()->processing_is_mt(), "processing is not MT");

  G1CMRefProcTaskProxy proc_task_proxy(proc_task, _g1h, _cm);

  // We need to reset the concurrency level before each
  // proxy task execution, so that the termination protocol
  // and overflow handling in G1CMTask::do_marking_step() knows
  // how many workers to wait for.
  _cm->set_concurrency(_active_workers);
  _workers->run_task(&proc_task_proxy);
}

class G1CMRefEnqueueTaskProxy: public AbstractGangTask {
  typedef AbstractRefProcTaskExecutor::EnqueueTask EnqueueTask;
  EnqueueTask& _enq_task;

public:
  G1CMRefEnqueueTaskProxy(EnqueueTask& enq_task) :
    AbstractGangTask("Enqueue reference objects in parallel"),
    _enq_task(enq_task) { }

  virtual void work(uint worker_id) {
    _enq_task.work(worker_id);
  }
};

void G1CMRefProcTaskExecutor::execute(EnqueueTask& enq_task) {
  assert(_workers != NULL, "Need parallel worker threads.");
  assert(_g1h->ref_processor_cm()->processing_is_mt(), "processing is not MT");

  G1CMRefEnqueueTaskProxy enq_task_proxy(enq_task);

  // Not strictly necessary but...
  //
  // We need to reset the concurrency level before each
  // proxy task execution, so that the termination protocol
  // and overflow handling in G1CMTask::do_marking_step() knows
  // how many workers to wait for.
  _cm->set_concurrency(_active_workers);
  _workers->run_task(&enq_task_proxy);
}

void G1ConcurrentMark::weakRefsWorkParallelPart(BoolObjectClosure* is_alive, bool purged_classes) {
  G1CollectedHeap::heap()->parallel_cleaning(is_alive, true, true, purged_classes);
}

void G1ConcurrentMark::weakRefsWork(bool clear_all_soft_refs) {
  if (has_overflown()) {
    // Skip processing the discovered references if we have
    // overflown the global marking stack. Reference objects
    // only get discovered once so it is OK to not
    // de-populate the discovered reference lists. We could have,
    // but the only benefit would be that, when marking restarts,
    // less reference objects are discovered.
    return;
  }

  ResourceMark rm;
  HandleMark   hm;

  G1CollectedHeap* g1h = G1CollectedHeap::heap();

  // Is alive closure.
  G1CMIsAliveClosure g1_is_alive(g1h);

  // Inner scope to exclude the cleaning of the string and symbol
  // tables from the displayed time.
  {
    GCTraceTime(Debug, gc, phases) trace("Reference Processing", _gc_timer_cm);

    ReferenceProcessor* rp = g1h->ref_processor_cm();

    // See the comment in G1CollectedHeap::ref_processing_init()
    // about how reference processing currently works in G1.

    // Set the soft reference policy
    rp->setup_policy(clear_all_soft_refs);
    assert(_markStack.isEmpty(), "mark stack should be empty");

    // Instances of the 'Keep Alive' and 'Complete GC' closures used
    // in serial reference processing. Note these closures are also
    // used for serially processing (by the the current thread) the
    // JNI references during parallel reference processing.
    //
    // These closures do not need to synchronize with the worker
    // threads involved in parallel reference processing as these
    // instances are executed serially by the current thread (e.g.
    // reference processing is not multi-threaded and is thus
    // performed by the current thread instead of a gang worker).
    //
    // The gang tasks involved in parallel reference processing create
    // their own instances of these closures, which do their own
    // synchronization among themselves.
    G1CMKeepAliveAndDrainClosure g1_keep_alive(this, task(0), true /* is_serial */);
    G1CMDrainMarkingStackClosure g1_drain_mark_stack(this, task(0), true /* is_serial */);

    // We need at least one active thread. If reference processing
    // is not multi-threaded we use the current (VMThread) thread,
    // otherwise we use the work gang from the G1CollectedHeap and
    // we utilize all the worker threads we can.
    bool processing_is_mt = rp->processing_is_mt();
    uint active_workers = (processing_is_mt ? g1h->workers()->active_workers() : 1U);
    active_workers = MAX2(MIN2(active_workers, _max_worker_id), 1U);

    // Parallel processing task executor.
    G1CMRefProcTaskExecutor par_task_executor(g1h, this,
                                              g1h->workers(), active_workers);
    AbstractRefProcTaskExecutor* executor = (processing_is_mt ? &par_task_executor : NULL);

    // Set the concurrency level. The phase was already set prior to
    // executing the remark task.
    set_concurrency(active_workers);

    // Set the degree of MT processing here.  If the discovery was done MT,
    // the number of threads involved during discovery could differ from
    // the number of active workers.  This is OK as long as the discovered
    // Reference lists are balanced (see balance_all_queues() and balance_queues()).
    rp->set_active_mt_degree(active_workers);

    // Process the weak references.
    const ReferenceProcessorStats& stats =
        rp->process_discovered_references(&g1_is_alive,
                                          &g1_keep_alive,
                                          &g1_drain_mark_stack,
                                          executor,
                                          _gc_timer_cm);
    _gc_tracer_cm->report_gc_reference_stats(stats);

    // The do_oop work routines of the keep_alive and drain_marking_stack
    // oop closures will set the has_overflown flag if we overflow the
    // global marking stack.

    assert(_markStack.overflow() || _markStack.isEmpty(),
            "mark stack should be empty (unless it overflowed)");

    if (_markStack.overflow()) {
      // This should have been done already when we tried to push an
      // entry on to the global mark stack. But let's do it again.
      set_has_overflown();
    }

    assert(rp->num_q() == active_workers, "why not");

    rp->enqueue_discovered_references(executor);

    rp->verify_no_references_recorded();
    assert(!rp->discovery_enabled(), "Post condition");
  }

  if (has_overflown()) {
    // We can not trust g1_is_alive if the marking stack overflowed
    return;
  }

  assert(_markStack.isEmpty(), "Marking should have completed");

  // Unload Klasses, String, Symbols, Code Cache, etc.
  if (ClassUnloadingWithConcurrentMark) {
    bool purged_classes;

    {
      GCTraceTime(Debug, gc, phases) trace("System Dictionary Unloading", _gc_timer_cm);
      purged_classes = SystemDictionary::do_unloading(&g1_is_alive, false /* Defer klass cleaning */);
    }

    {
      GCTraceTime(Debug, gc, phases) trace("Parallel Unloading", _gc_timer_cm);
      weakRefsWorkParallelPart(&g1_is_alive, purged_classes);
    }
  }

  if (G1StringDedup::is_enabled()) {
    GCTraceTime(Debug, gc, phases) trace("String Deduplication Unlink", _gc_timer_cm);
    G1StringDedup::unlink(&g1_is_alive);
  }
}

void G1ConcurrentMark::swapMarkBitMaps() {
  G1CMBitMapRO* temp = _prevMarkBitMap;
  _prevMarkBitMap    = (G1CMBitMapRO*)_nextMarkBitMap;
  _nextMarkBitMap    = (G1CMBitMap*)  temp;
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
    HeapRegion* hr = _g1h->heap_region_containing(entry);
    if (entry < hr->next_top_at_mark_start()) {
      // Until we get here, we don't know whether entry refers to a valid
      // object; it could instead have been a stale reference.
      oop obj = static_cast<oop>(entry);
      assert(obj->is_oop(true /* ignore mark word */),
             "Invalid oop in SATB buffer: " PTR_FORMAT, p2i(obj));
      _task->make_reference_grey(obj);
    }
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
  G1CMSATBBufferClosure _cm_satb_cl;
  G1CMOopClosure _cm_cl;
  MarkingCodeBlobClosure _code_cl;
  int _thread_parity;

 public:
  G1RemarkThreadsClosure(G1CollectedHeap* g1h, G1CMTask* task) :
    _cm_satb_cl(task, g1h),
    _cm_cl(g1h, g1h->concurrent_mark(), task),
    _code_cl(&_cm_cl, !CodeBlobToOopClosure::FixRelocations),
    _thread_parity(Threads::thread_claim_parity()) {}

  void do_thread(Thread* thread) {
    if (thread->is_Java_thread()) {
      if (thread->claim_oops_do(true, _thread_parity)) {
        JavaThread* jt = (JavaThread*)thread;

        // In theory it should not be neccessary to explicitly walk the nmethods to find roots for concurrent marking
        // however the liveness of oops reachable from nmethods have very complex lifecycles:
        // * Alive if on the stack of an executing method
        // * Weakly reachable otherwise
        // Some objects reachable from nmethods, such as the class loader (or klass_holder) of the receiver should be
        // live by the SATB invariant but other oops recorded in nmethods may behave differently.
        jt->nmethods_do(&_code_cl);

        jt->satb_mark_queue().apply_closure_and_empty(&_cm_satb_cl);
      }
    } else if (thread->is_VM_thread()) {
      if (thread->claim_oops_do(true, _thread_parity)) {
        JavaThread::satb_mark_queue_set().shared_satb_queue()->apply_closure_and_empty(&_cm_satb_cl);
      }
    }
  }
};

class G1CMRemarkTask: public AbstractGangTask {
private:
  G1ConcurrentMark* _cm;
public:
  void work(uint worker_id) {
    // Since all available tasks are actually started, we should
    // only proceed if we're supposed to be active.
    if (worker_id < _cm->active_tasks()) {
      G1CMTask* task = _cm->task(worker_id);
      task->record_start_time();
      {
        ResourceMark rm;
        HandleMark hm;

        G1RemarkThreadsClosure threads_f(G1CollectedHeap::heap(), task);
        Threads::threads_do(&threads_f);
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
  }

  G1CMRemarkTask(G1ConcurrentMark* cm, uint active_workers) :
    AbstractGangTask("Par Remark"), _cm(cm) {
    _cm->terminator()->reset_for_reuse(active_workers);
  }
};

void G1ConcurrentMark::checkpointRootsFinalWork() {
  ResourceMark rm;
  HandleMark   hm;
  G1CollectedHeap* g1h = G1CollectedHeap::heap();

  GCTraceTime(Debug, gc, phases) trace("Finalize Marking", _gc_timer_cm);

  g1h->ensure_parsability(false);

  // this is remark, so we'll use up all active threads
  uint active_workers = g1h->workers()->active_workers();
  set_concurrency_and_phase(active_workers, false /* concurrent */);
  // Leave _parallel_marking_threads at it's
  // value originally calculated in the G1ConcurrentMark
  // constructor and pass values of the active workers
  // through the gang in the task.

  {
    StrongRootsScope srs(active_workers);

    G1CMRemarkTask remarkTask(this, active_workers);
    // We will start all available threads, even if we decide that the
    // active_workers will be fewer. The extra ones will just bail out
    // immediately.
    g1h->workers()->run_task(&remarkTask);
  }

  SATBMarkQueueSet& satb_mq_set = JavaThread::satb_mark_queue_set();
  guarantee(has_overflown() ||
            satb_mq_set.completed_buffers_num() == 0,
            "Invariant: has_overflown = %s, num buffers = " SIZE_FORMAT,
            BOOL_TO_STR(has_overflown()),
            satb_mq_set.completed_buffers_num());

  print_stats();
}

void G1ConcurrentMark::clearRangePrevBitmap(MemRegion mr) {
  // Note we are overriding the read-only view of the prev map here, via
  // the cast.
  ((G1CMBitMap*)_prevMarkBitMap)->clear_range(mr);
}

HeapRegion*
G1ConcurrentMark::claim_region(uint worker_id) {
  // "checkpoint" the finger
  HeapWord* finger = _finger;

  // _heap_end will not change underneath our feet; it only changes at
  // yield points.
  while (finger < _heap_end) {
    assert(_g1h->is_in_g1_reserved(finger), "invariant");

    HeapRegion* curr_region = _g1h->heap_region_containing(finger);

    // Above heap_region_containing may return NULL as we always scan claim
    // until the end of the heap. In this case, just jump to the next region.
    HeapWord* end = curr_region != NULL ? curr_region->end() : finger + HeapRegion::GrainWords;

    // Is the gap between reading the finger and doing the CAS too long?
    HeapWord* res = (HeapWord*) Atomic::cmpxchg_ptr(end, &_finger, finger);
    if (res == finger && curr_region != NULL) {
      // we succeeded
      HeapWord*   bottom        = curr_region->bottom();
      HeapWord*   limit         = curr_region->next_top_at_mark_start();

      // notice that _finger == end cannot be guaranteed here since,
      // someone else might have moved the finger even further
      assert(_finger >= end, "the finger should have moved forward");

      if (limit > bottom) {
        return curr_region;
      } else {
        assert(limit == bottom,
               "the region limit should be at bottom");
        // we return NULL and the caller should try calling
        // claim_region() again.
        return NULL;
      }
    } else {
      assert(_finger > finger, "the finger should have moved forward");
      // read it again
      finger = _finger;
    }
  }

  return NULL;
}

#ifndef PRODUCT
class VerifyNoCSetOops VALUE_OBJ_CLASS_SPEC {
private:
  G1CollectedHeap* _g1h;
  const char* _phase;
  int _info;

public:
  VerifyNoCSetOops(const char* phase, int info = -1) :
    _g1h(G1CollectedHeap::heap()),
    _phase(phase),
    _info(info)
  { }

  void operator()(oop obj) const {
    guarantee(obj->is_oop(),
              "Non-oop " PTR_FORMAT ", phase: %s, info: %d",
              p2i(obj), _phase, _info);
    guarantee(!_g1h->obj_in_cs(obj),
              "obj: " PTR_FORMAT " in CSet, phase: %s, info: %d",
              p2i(obj), _phase, _info);
  }
};

void G1ConcurrentMark::verify_no_cset_oops() {
  assert(SafepointSynchronize::is_at_safepoint(), "should be at a safepoint");
  if (!G1CollectedHeap::heap()->collector_state()->mark_in_progress()) {
    return;
  }

  // Verify entries on the global mark stack
  _markStack.iterate(VerifyNoCSetOops("Stack"));

  // Verify entries on the task queues
  for (uint i = 0; i < _max_worker_id; ++i) {
    G1CMTaskQueue* queue = _task_queues->queue(i);
    queue->iterate(VerifyNoCSetOops("Queue", i));
  }

  // Verify the global finger
  HeapWord* global_finger = finger();
  if (global_finger != NULL && global_finger < _heap_end) {
    // Since we always iterate over all regions, we might get a NULL HeapRegion
    // here.
    HeapRegion* global_hr = _g1h->heap_region_containing(global_finger);
    guarantee(global_hr == NULL || global_finger == global_hr->bottom(),
              "global finger: " PTR_FORMAT " region: " HR_FORMAT,
              p2i(global_finger), HR_FORMAT_PARAMS(global_hr));
  }

  // Verify the task fingers
  assert(parallel_marking_threads() <= _max_worker_id, "sanity");
  for (uint i = 0; i < parallel_marking_threads(); ++i) {
    G1CMTask* task = _tasks[i];
    HeapWord* task_finger = task->finger();
    if (task_finger != NULL && task_finger < _heap_end) {
      // See above note on the global finger verification.
      HeapRegion* task_hr = _g1h->heap_region_containing(task_finger);
      guarantee(task_hr == NULL || task_finger == task_hr->bottom() ||
                !task_hr->in_collection_set(),
                "task finger: " PTR_FORMAT " region: " HR_FORMAT,
                p2i(task_finger), HR_FORMAT_PARAMS(task_hr));
    }
  }
}
#endif // PRODUCT
void G1ConcurrentMark::create_live_data() {
  _g1h->g1_rem_set()->create_card_live_data(_parallel_workers, _nextMarkBitMap);
}

void G1ConcurrentMark::finalize_live_data() {
  _g1h->g1_rem_set()->finalize_card_live_data(_g1h->workers(), _nextMarkBitMap);
}

void G1ConcurrentMark::verify_live_data() {
  _g1h->g1_rem_set()->verify_card_live_data(_g1h->workers(), _nextMarkBitMap);
}

void G1ConcurrentMark::clear_live_data(WorkGang* workers) {
  _g1h->g1_rem_set()->clear_card_live_data(workers);
}

#ifdef ASSERT
void G1ConcurrentMark::verify_live_data_clear() {
  _g1h->g1_rem_set()->verify_card_live_data_is_clear();
}
#endif

void G1ConcurrentMark::print_stats() {
  if (!log_is_enabled(Debug, gc, stats)) {
    return;
  }
  log_debug(gc, stats)("---------------------------------------------------------------------");
  for (size_t i = 0; i < _active_tasks; ++i) {
    _tasks[i]->print_stats();
    log_debug(gc, stats)("---------------------------------------------------------------------");
  }
}

void G1ConcurrentMark::abort() {
  if (!cmThread()->during_cycle() || _has_aborted) {
    // We haven't started a concurrent cycle or we have already aborted it. No need to do anything.
    return;
  }

  // Clear all marks in the next bitmap for the next marking cycle. This will allow us to skip the next
  // concurrent bitmap clearing.
  {
    GCTraceTime(Debug, gc)("Clear Next Bitmap");
    clear_bitmap(_nextMarkBitMap, _g1h->workers(), false);
  }
  // Note we cannot clear the previous marking bitmap here
  // since VerifyDuringGC verifies the objects marked during
  // a full GC against the previous bitmap.

  {
    GCTraceTime(Debug, gc)("Clear Live Data");
    clear_live_data(_g1h->workers());
  }
  DEBUG_ONLY({
    GCTraceTime(Debug, gc)("Verify Live Data Clear");
    verify_live_data_clear();
  })
  // Empty mark stack
  reset_marking_state();
  for (uint i = 0; i < _max_worker_id; ++i) {
    _tasks[i]->clear_region_fields();
  }
  _first_overflow_barrier_sync.abort();
  _second_overflow_barrier_sync.abort();
  _has_aborted = true;

  SATBMarkQueueSet& satb_mq_set = JavaThread::satb_mark_queue_set();
  satb_mq_set.abandon_partial_marking();
  // This can be called either during or outside marking, we'll read
  // the expected_active value from the SATB queue set.
  satb_mq_set.set_active_all_threads(
                                 false, /* new active value */
                                 satb_mq_set.is_active() /* expected_active */);
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
  print_ms_time_info("  ", "init marks", _init_times);
  print_ms_time_info("  ", "remarks", _remark_times);
  {
    print_ms_time_info("     ", "final marks", _remark_mark_times);
    print_ms_time_info("     ", "weak refs", _remark_weak_ref_times);

  }
  print_ms_time_info("  ", "cleanups", _cleanup_times);
  log.trace("    Finalize live data total time = %8.2f s (avg = %8.2f ms).",
            _total_counting_time, (_cleanup_times.num() > 0 ? _total_counting_time * 1000.0 / (double)_cleanup_times.num() : 0.0));
  if (G1ScrubRemSets) {
    log.trace("    RS scrub total time = %8.2f s (avg = %8.2f ms).",
              _total_rs_scrub_time, (_cleanup_times.num() > 0 ? _total_rs_scrub_time * 1000.0 / (double)_cleanup_times.num() : 0.0));
  }
  log.trace("  Total stop_world time = %8.2f s.",
            (_init_times.sum() + _remark_times.sum() + _cleanup_times.sum())/1000.0);
  log.trace("  Total concurrent time = %8.2f s (%8.2f s marking).",
            cmThread()->vtime_accum(), cmThread()->vtime_mark_accum());
}

void G1ConcurrentMark::print_worker_threads_on(outputStream* st) const {
  _parallel_workers->print_worker_threads_on(st);
}

void G1ConcurrentMark::threads_do(ThreadClosure* tc) const {
  _parallel_workers->threads_do(tc);
}

void G1ConcurrentMark::print_on_error(outputStream* st) const {
  st->print_cr("Marking Bits (Prev, Next): (CMBitMap*) " PTR_FORMAT ", (CMBitMap*) " PTR_FORMAT,
      p2i(_prevMarkBitMap), p2i(_nextMarkBitMap));
  _prevMarkBitMap->print_on_error(st, " Prev Bits: ");
  _nextMarkBitMap->print_on_error(st, " Next Bits: ");
}

// Closure for iteration over bitmaps
class G1CMBitMapClosure : public BitMapClosure {
private:
  // the bitmap that is being iterated over
  G1CMBitMap*                 _nextMarkBitMap;
  G1ConcurrentMark*           _cm;
  G1CMTask*                   _task;

public:
  G1CMBitMapClosure(G1CMTask *task, G1ConcurrentMark* cm, G1CMBitMap* nextMarkBitMap) :
    _task(task), _cm(cm), _nextMarkBitMap(nextMarkBitMap) { }

  bool do_bit(size_t offset) {
    HeapWord* addr = _nextMarkBitMap->offsetToHeapWord(offset);
    assert(_nextMarkBitMap->isMarked(addr), "invariant");
    assert( addr < _cm->finger(), "invariant");
    assert(addr >= _task->finger(), "invariant");

    // We move that task's local finger along.
    _task->move_finger_to(addr);

    _task->scan_object(oop(addr));
    // we only partially drain the local queue and global stack
    _task->drain_local_queue(true);
    _task->drain_global_stack(true);

    // if the has_aborted flag has been raised, we need to bail out of
    // the iteration
    return !_task->has_aborted();
  }
};

static ReferenceProcessor* get_cm_oop_closure_ref_processor(G1CollectedHeap* g1h) {
  ReferenceProcessor* result = g1h->ref_processor_cm();
  assert(result != NULL, "CM reference processor should not be NULL");
  return result;
}

G1CMOopClosure::G1CMOopClosure(G1CollectedHeap* g1h,
                               G1ConcurrentMark* cm,
                               G1CMTask* task)
  : MetadataAwareOopClosure(get_cm_oop_closure_ref_processor(g1h)),
    _g1h(g1h), _cm(cm), _task(task)
{ }

void G1CMTask::setup_for_region(HeapRegion* hr) {
  assert(hr != NULL,
        "claim_region() should have filtered out NULL regions");
  _curr_region  = hr;
  _finger       = hr->bottom();
  update_region_limit();
}

void G1CMTask::update_region_limit() {
  HeapRegion* hr            = _curr_region;
  HeapWord* bottom          = hr->bottom();
  HeapWord* limit           = hr->next_top_at_mark_start();

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
    // evacuation pause empties the region underneath our feet (NTAMS
    // at bottom). We then do some allocation in the region (NTAMS
    // stays at bottom), followed by the region being used as a GC
    // alloc region (NTAMS will move to top() and the objects
    // originally below it will be grayed). All objects now marked in
    // the region are explicitly grayed, if below the global finger,
    // and we do not need in fact to scan anything else. So, we simply
    // set _finger to be limit to ensure that the bitmap iteration
    // doesn't do anything.
    _finger = limit;
  }

  _region_limit = limit;
}

void G1CMTask::giveup_current_region() {
  assert(_curr_region != NULL, "invariant");
  clear_region_fields();
}

void G1CMTask::clear_region_fields() {
  // Values for these three fields that indicate that we're not
  // holding on to a region.
  _curr_region   = NULL;
  _finger        = NULL;
  _region_limit  = NULL;
}

void G1CMTask::set_cm_oop_closure(G1CMOopClosure* cm_oop_closure) {
  if (cm_oop_closure == NULL) {
    assert(_cm_oop_closure != NULL, "invariant");
  } else {
    assert(_cm_oop_closure == NULL, "invariant");
  }
  _cm_oop_closure = cm_oop_closure;
}

void G1CMTask::reset(G1CMBitMap* nextMarkBitMap) {
  guarantee(nextMarkBitMap != NULL, "invariant");
  _nextMarkBitMap                = nextMarkBitMap;
  clear_region_fields();

  _calls                         = 0;
  _elapsed_time_ms               = 0.0;
  _termination_time_ms           = 0.0;
  _termination_start_time_ms     = 0.0;
}

bool G1CMTask::should_exit_termination() {
  regular_clock_call();
  // This is called when we are in the termination protocol. We should
  // quit if, for some reason, this task wants to abort or the global
  // stack is not empty (this means that we can get work from it).
  return !_cm->mark_stack_empty() || has_aborted();
}

void G1CMTask::reached_limit() {
  assert(_words_scanned >= _words_scanned_limit ||
         _refs_reached >= _refs_reached_limit ,
         "shouldn't have been called otherwise");
  regular_clock_call();
}

void G1CMTask::regular_clock_call() {
  if (has_aborted()) return;

  // First, we need to recalculate the words scanned and refs reached
  // limits for the next clock call.
  recalculate_limits();

  // During the regular clock call we do the following

  // (1) If an overflow has been flagged, then we abort.
  if (_cm->has_overflown()) {
    set_has_aborted();
    return;
  }

  // If we are not concurrent (i.e. we're doing remark) we don't need
  // to check anything else. The other steps are only needed during
  // the concurrent marking phase.
  if (!concurrent()) return;

  // (2) If marking has been aborted for Full GC, then we also abort.
  if (_cm->has_aborted()) {
    set_has_aborted();
    return;
  }

  double curr_time_ms = os::elapsedVTime() * 1000.0;

  // (4) We check whether we should yield. If we have to, then we abort.
  if (SuspendibleThreadSet::should_yield()) {
    // We should yield. To do this we abort the task. The caller is
    // responsible for yielding.
    set_has_aborted();
    return;
  }

  // (5) We check whether we've reached our time quota. If we have,
  // then we abort.
  double elapsed_time_ms = curr_time_ms - _start_time_ms;
  if (elapsed_time_ms > _time_target_ms) {
    set_has_aborted();
    _has_timed_out = true;
    return;
  }

  // (6) Finally, we check whether there are enough completed STAB
  // buffers available for processing. If there are, we abort.
  SATBMarkQueueSet& satb_mq_set = JavaThread::satb_mark_queue_set();
  if (!_draining_satb_buffers && satb_mq_set.process_completed_buffers()) {
    // we do need to process SATB buffers, we'll abort and restart
    // the marking task to do so
    set_has_aborted();
    return;
  }
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

  _words_scanned_limit = _real_words_scanned_limit -
    3 * words_scanned_period / 4;
  _refs_reached_limit  = _real_refs_reached_limit -
    3 * refs_reached_period / 4;
}

void G1CMTask::move_entries_to_global_stack() {
  // local array where we'll store the entries that will be popped
  // from the local queue
  oop buffer[global_stack_transfer_size];

  int n = 0;
  oop obj;
  while (n < global_stack_transfer_size && _task_queue->pop_local(obj)) {
    buffer[n] = obj;
    ++n;
  }

  if (n > 0) {
    // we popped at least one entry from the local queue

    if (!_cm->mark_stack_push(buffer, n)) {
      set_has_aborted();
    }
  }

  // this operation was quite expensive, so decrease the limits
  decrease_limits();
}

void G1CMTask::get_entries_from_global_stack() {
  // local array where we'll store the entries that will be popped
  // from the global stack.
  oop buffer[global_stack_transfer_size];
  int n;
  _cm->mark_stack_pop(buffer, global_stack_transfer_size, &n);
  assert(n <= global_stack_transfer_size,
         "we should not pop more than the given limit");
  if (n > 0) {
    // yes, we did actually pop at least one entry
    for (int i = 0; i < n; ++i) {
      bool success = _task_queue->push(buffer[i]);
      // We only call this when the local queue is empty or under a
      // given target limit. So, we do not expect this push to fail.
      assert(success, "invariant");
    }
  }

  // this operation was quite expensive, so decrease the limits
  decrease_limits();
}

void G1CMTask::drain_local_queue(bool partially) {
  if (has_aborted()) return;

  // Decide what the target size is, depending whether we're going to
  // drain it partially (so that other tasks can steal if they run out
  // of things to do) or totally (at the very end).
  size_t target_size;
  if (partially) {
    target_size = MIN2((size_t)_task_queue->max_elems()/3, GCDrainStackTargetSize);
  } else {
    target_size = 0;
  }

  if (_task_queue->size() > target_size) {
    oop obj;
    bool ret = _task_queue->pop_local(obj);
    while (ret) {
      assert(_g1h->is_in_g1_reserved((HeapWord*) obj), "invariant" );
      assert(!_g1h->is_on_master_free_list(
                  _g1h->heap_region_containing((HeapWord*) obj)), "invariant");

      scan_object(obj);

      if (_task_queue->size() <= target_size || has_aborted()) {
        ret = false;
      } else {
        ret = _task_queue->pop_local(obj);
      }
    }
  }
}

void G1CMTask::drain_global_stack(bool partially) {
  if (has_aborted()) return;

  // We have a policy to drain the local queue before we attempt to
  // drain the global stack.
  assert(partially || _task_queue->size() == 0, "invariant");

  // Decide what the target size is, depending whether we're going to
  // drain it partially (so that other tasks can steal if they run out
  // of things to do) or totally (at the very end).  Notice that,
  // because we move entries from the global stack in chunks or
  // because another task might be doing the same, we might in fact
  // drop below the target. But, this is not a problem.
  size_t target_size;
  if (partially) {
    target_size = _cm->partial_mark_stack_size_target();
  } else {
    target_size = 0;
  }

  if (_cm->mark_stack_size() > target_size) {
    while (!has_aborted() && _cm->mark_stack_size() > target_size) {
      get_entries_from_global_stack();
      drain_local_queue(partially);
    }
  }
}

// SATB Queue has several assumptions on whether to call the par or
// non-par versions of the methods. this is why some of the code is
// replicated. We should really get rid of the single-threaded version
// of the code to simplify things.
void G1CMTask::drain_satb_buffers() {
  if (has_aborted()) return;

  // We set this so that the regular clock knows that we're in the
  // middle of draining buffers and doesn't set the abort flag when it
  // notices that SATB buffers are available for draining. It'd be
  // very counter productive if it did that. :-)
  _draining_satb_buffers = true;

  G1CMSATBBufferClosure satb_cl(this, _g1h);
  SATBMarkQueueSet& satb_mq_set = JavaThread::satb_mark_queue_set();

  // This keeps claiming and applying the closure to completed buffers
  // until we run out of buffers or we need to abort.
  while (!has_aborted() &&
         satb_mq_set.apply_closure_to_completed_buffer(&satb_cl)) {
    regular_clock_call();
  }

  _draining_satb_buffers = false;

  assert(has_aborted() ||
         concurrent() ||
         satb_mq_set.completed_buffers_num() == 0, "invariant");

  // again, this was a potentially expensive operation, decrease the
  // limits to get the regular clock call early
  decrease_limits();
}

void G1CMTask::print_stats() {
  log_debug(gc, stats)("Marking Stats, task = %u, calls = %d",
                       _worker_id, _calls);
  log_debug(gc, stats)("  Elapsed time = %1.2lfms, Termination time = %1.2lfms",
                       _elapsed_time_ms, _termination_time_ms);
  log_debug(gc, stats)("  Step Times (cum): num = %d, avg = %1.2lfms, sd = %1.2lfms",
                       _step_times_ms.num(), _step_times_ms.avg(),
                       _step_times_ms.sd());
  log_debug(gc, stats)("                    max = %1.2lfms, total = %1.2lfms",
                       _step_times_ms.maximum(), _step_times_ms.sum());
}

bool G1ConcurrentMark::try_stealing(uint worker_id, int* hash_seed, oop& obj) {
  return _task_queues->steal(worker_id, hash_seed, obj);
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

      (1) Marking Bitmap. If there are gray objects that appear only
      on the bitmap (this happens either when dealing with an overflow
      or when the initial marking phase has simply marked the roots
      and didn't push them on the stack), then tasks claim heap
      regions whose bitmap they then scan to find gray objects. A
      global finger indicates where the end of the last claimed region
      is. A local finger indicates how far into the region a task has
      scanned. The two fingers are used to determine how to gray an
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
      over the bitmap to identify gray objects. Throughout the marking
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
    vtime too regularly, as it is quite expensive. So, once it was in
    place, it was natural to piggy-back all the other conditions on it
    too and not constantly check them throughout the code.

    If do_termination is true then do_marking_step will enter its
    termination protocol.

    The value of is_serial must be true when do_marking_step is being
    called serially (i.e. by the VMThread) and do_marking_step should
    skip any synchronization in the termination and overflow code.
    Examples include the serial remark code and the serial reference
    processing closures.

    The value of is_serial must be false when do_marking_step is
    being called by any of the worker threads in a work gang.
    Examples include the concurrent marking code (CMMarkingTask),
    the MT remark code, and the MT reference processing closures.

 *****************************************************************************/

void G1CMTask::do_marking_step(double time_target_ms,
                               bool do_termination,
                               bool is_serial) {
  assert(time_target_ms >= 1.0, "minimum granularity is 1ms");
  assert(concurrent() == _cm->concurrent(), "they should be the same");

  G1Policy* g1_policy = _g1h->g1_policy();
  assert(_task_queues != NULL, "invariant");
  assert(_task_queue != NULL, "invariant");
  assert(_task_queues->queue(_worker_id) == _task_queue, "invariant");

  assert(!_claimed,
         "only one thread should claim this task at any one time");

  // OK, this doesn't safeguard again all possible scenarios, as it is
  // possible for two threads to set the _claimed flag at the same
  // time. But it is only for debugging purposes anyway and it will
  // catch most problems.
  _claimed = true;

  _start_time_ms = os::elapsedVTime() * 1000.0;

  // If do_stealing is true then do_marking_step will attempt to
  // steal work from the other G1CMTasks. It only makes sense to
  // enable stealing when the termination protocol is enabled
  // and do_marking_step() is not being called serially.
  bool do_stealing = do_termination && !is_serial;

  double diff_prediction_ms = _g1h->g1_policy()->predictor().get_new_prediction(&_marking_step_diffs_ms);
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
  G1CMBitMapClosure bitmap_closure(this, _cm, _nextMarkBitMap);
  G1CMOopClosure    cm_oop_closure(_g1h, _cm, this);
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
    if (!has_aborted() && _curr_region != NULL) {
      // This means that we're already holding on to a region.
      assert(_finger != NULL, "if region is not NULL, then the finger "
             "should not be NULL either");

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
        regular_clock_call();
      } else if (_curr_region->is_humongous() && mr.start() == _curr_region->bottom()) {
        if (_nextMarkBitMap->isMarked(mr.start())) {
          // The object is marked - apply the closure
          BitMap::idx_t offset = _nextMarkBitMap->heapWordToOffset(mr.start());
          bitmap_closure.do_bit(offset);
        }
        // Even if this task aborted while scanning the humongous object
        // we can (and should) give up the current region.
        giveup_current_region();
        regular_clock_call();
      } else if (_nextMarkBitMap->iterate(&bitmap_closure, mr)) {
        giveup_current_region();
        regular_clock_call();
      } else {
        assert(has_aborted(), "currently the only way to do so");
        // The only way to abort the bitmap iteration is to return
        // false from the do_bit() method. However, inside the
        // do_bit() method we move the _finger to point to the
        // object currently being looked at. So, if we bail out, we
        // have definitely set _finger to something non-null.
        assert(_finger != NULL, "invariant");

        // Region iteration was actually aborted. So now _finger
        // points to the address of the object we last scanned. If we
        // leave it there, when we restart this task, we will rescan
        // the object. It is easy to avoid this. We move the finger by
        // enough to point to the next possible object header (the
        // bitmap knows by how much we need to move it as it knows its
        // granularity).
        assert(_finger < _region_limit, "invariant");
        HeapWord* new_finger = _nextMarkBitMap->nextObject(_finger);
        // Check if bitmap iteration was aborted while scanning the last object
        if (new_finger >= _region_limit) {
          giveup_current_region();
        } else {
          move_finger_to(new_finger);
        }
      }
    }
    // At this point we have either completed iterating over the
    // region we were holding on to, or we have aborted.

    // We then partially drain the local queue and the global stack.
    // (Do we really need this?)
    drain_local_queue(true);
    drain_global_stack(true);

    // Read the note on the claim_region() method on why it might
    // return NULL with potentially more regions available for
    // claiming and why we have to check out_of_regions() to determine
    // whether we're done or not.
    while (!has_aborted() && _curr_region == NULL && !_cm->out_of_regions()) {
      // We are going to try to claim a new region. We should have
      // given up on the previous one.
      // Separated the asserts so that we know which one fires.
      assert(_curr_region  == NULL, "invariant");
      assert(_finger       == NULL, "invariant");
      assert(_region_limit == NULL, "invariant");
      HeapRegion* claimed_region = _cm->claim_region(_worker_id);
      if (claimed_region != NULL) {
        // Yes, we managed to claim one
        setup_for_region(claimed_region);
        assert(_curr_region == claimed_region, "invariant");
      }
      // It is important to call the regular clock here. It might take
      // a while to claim a region if, for example, we hit a large
      // block of empty regions. So we need to call the regular clock
      // method once round the loop to make sure it's called
      // frequently enough.
      regular_clock_call();
    }

    if (!has_aborted() && _curr_region == NULL) {
      assert(_cm->out_of_regions(),
             "at this point we should be out of regions");
    }
  } while ( _curr_region != NULL && !has_aborted());

  if (!has_aborted()) {
    // We cannot check whether the global stack is empty, since other
    // tasks might be pushing objects to it concurrently.
    assert(_cm->out_of_regions(),
           "at this point we should be out of regions");
    // Try to reduce the number of available SATB buffers so that
    // remark has less work to do.
    drain_satb_buffers();
  }

  // Since we've done everything else, we can now totally drain the
  // local queue and global stack.
  drain_local_queue(false);
  drain_global_stack(false);

  // Attempt at work stealing from other task's queues.
  if (do_stealing && !has_aborted()) {
    // We have not aborted. This means that we have finished all that
    // we could. Let's try to do some stealing...

    // We cannot check whether the global stack is empty, since other
    // tasks might be pushing objects to it concurrently.
    assert(_cm->out_of_regions() && _task_queue->size() == 0,
           "only way to reach here");
    while (!has_aborted()) {
      oop obj;
      if (_cm->try_stealing(_worker_id, &_hash_seed, obj)) {
        assert(_nextMarkBitMap->isMarked((HeapWord*) obj),
               "any stolen object should be marked");
        scan_object(obj);

        // And since we're towards the end, let's totally drain the
        // local queue and global stack.
        drain_local_queue(false);
        drain_global_stack(false);
      } else {
        break;
      }
    }
  }

  // We still haven't aborted. Now, let's try to get into the
  // termination protocol.
  if (do_termination && !has_aborted()) {
    // We cannot check whether the global stack is empty, since other
    // tasks might be concurrently pushing objects on it.
    // Separated the asserts so that we know which one fires.
    assert(_cm->out_of_regions(), "only way to reach here");
    assert(_task_queue->size() == 0, "only way to reach here");
    _termination_start_time_ms = os::elapsedVTime() * 1000.0;

    // The G1CMTask class also extends the TerminatorTerminator class,
    // hence its should_exit_termination() method will also decide
    // whether to exit the termination protocol or not.
    bool finished = (is_serial ||
                     _cm->terminator()->offer_termination(this));
    double termination_end_time_ms = os::elapsedVTime() * 1000.0;
    _termination_time_ms +=
      termination_end_time_ms - _termination_start_time_ms;

    if (finished) {
      // We're all done.

      if (_worker_id == 0) {
        // let's allow task 0 to do this
        if (concurrent()) {
          assert(_cm->concurrent_marking_in_progress(), "invariant");
          // we need to set this to false before the next
          // safepoint. This way we ensure that the marking phase
          // doesn't observe any more heap expansions.
          _cm->clear_concurrent_marking_in_progress();
        }
      }

      // We can now guarantee that the global stack is empty, since
      // all other tasks have finished. We separated the guarantees so
      // that, if a condition is false, we can immediately find out
      // which one.
      guarantee(_cm->out_of_regions(), "only way to reach here");
      guarantee(_cm->mark_stack_empty(), "only way to reach here");
      guarantee(_task_queue->size() == 0, "only way to reach here");
      guarantee(!_cm->has_overflown(), "only way to reach here");
      guarantee(!_cm->mark_stack_overflow(), "only way to reach here");
    } else {
      // Apparently there's more work to do. Let's abort this task. It
      // will restart it and we can hopefully find more things to do.
      set_has_aborted();
    }
  }

  // Mainly for debugging purposes to make sure that a pointer to the
  // closure which was statically allocated in this frame doesn't
  // escape it by accident.
  set_cm_oop_closure(NULL);
  double end_time_ms = os::elapsedVTime() * 1000.0;
  double elapsed_time_ms = end_time_ms - _start_time_ms;
  // Update the step history.
  _step_times_ms.add(elapsed_time_ms);

  if (has_aborted()) {
    // The task was aborted for some reason.
    if (_has_timed_out) {
      double diff_ms = elapsed_time_ms - _time_target_ms;
      // Keep statistics of how well we did with respect to hitting
      // our target only if we actually timed out (if we aborted for
      // other reasons, then the results might get skewed).
      _marking_step_diffs_ms.add(diff_ms);
    }

    if (_cm->has_overflown()) {
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
        // re-initialize our data structures. At the end of this method,
        // task 0 will clear the global data structures.
      }

      // We clear the local state of this task...
      clear_region_fields();

      if (!is_serial) {
        // ...and enter the second barrier.
        _cm->enter_second_sync_barrier(_worker_id);
      }
      // At this point, if we're during the concurrent phase of
      // marking, everything has been re-initialized and we're
      // ready to restart.
    }
  }

  _claimed = false;
}

G1CMTask::G1CMTask(uint worker_id,
                   G1ConcurrentMark* cm,
                   G1CMTaskQueue* task_queue,
                   G1CMTaskQueueSet* task_queues)
  : _g1h(G1CollectedHeap::heap()),
    _worker_id(worker_id), _cm(cm),
    _claimed(false),
    _nextMarkBitMap(NULL), _hash_seed(17),
    _task_queue(task_queue),
    _task_queues(task_queues),
    _cm_oop_closure(NULL) {
  guarantee(task_queue != NULL, "invariant");
  guarantee(task_queues != NULL, "invariant");

  _marking_step_diffs_ms.add(0.5);
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
#define G1PPRL_BYTE_FORMAT            "  " SIZE_FORMAT_W(9)
#define G1PPRL_BYTE_H_FORMAT          "  %9s"
#define G1PPRL_DOUBLE_FORMAT          "  %14.1f"
#define G1PPRL_DOUBLE_H_FORMAT        "  %14s"

// For summary info
#define G1PPRL_SUM_ADDR_FORMAT(tag)    "  " tag ":" G1PPRL_ADDR_BASE_FORMAT
#define G1PPRL_SUM_BYTE_FORMAT(tag)    "  " tag ": " SIZE_FORMAT
#define G1PPRL_SUM_MB_FORMAT(tag)      "  " tag ": %1.2f MB"
#define G1PPRL_SUM_MB_PERC_FORMAT(tag) G1PPRL_SUM_MB_FORMAT(tag) " / %1.2f %%"

G1PrintRegionLivenessInfoClosure::
G1PrintRegionLivenessInfoClosure(const char* phase_name)
  : _total_used_bytes(0), _total_capacity_bytes(0),
    _total_prev_live_bytes(0), _total_next_live_bytes(0),
    _total_remset_bytes(0), _total_strong_code_roots_bytes(0) {
  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  MemRegion g1_reserved = g1h->g1_reserved();
  double now = os::elapsedTime();

  // Print the header of the output.
  log_trace(gc, liveness)(G1PPRL_LINE_PREFIX" PHASE %s @ %1.3f", phase_name, now);
  log_trace(gc, liveness)(G1PPRL_LINE_PREFIX" HEAP"
                          G1PPRL_SUM_ADDR_FORMAT("reserved")
                          G1PPRL_SUM_BYTE_FORMAT("region-size"),
                          p2i(g1_reserved.start()), p2i(g1_reserved.end()),
                          HeapRegion::GrainBytes);
  log_trace(gc, liveness)(G1PPRL_LINE_PREFIX);
  log_trace(gc, liveness)(G1PPRL_LINE_PREFIX
                          G1PPRL_TYPE_H_FORMAT
                          G1PPRL_ADDR_BASE_H_FORMAT
                          G1PPRL_BYTE_H_FORMAT
                          G1PPRL_BYTE_H_FORMAT
                          G1PPRL_BYTE_H_FORMAT
                          G1PPRL_DOUBLE_H_FORMAT
                          G1PPRL_BYTE_H_FORMAT
                          G1PPRL_BYTE_H_FORMAT,
                          "type", "address-range",
                          "used", "prev-live", "next-live", "gc-eff",
                          "remset", "code-roots");
  log_trace(gc, liveness)(G1PPRL_LINE_PREFIX
                          G1PPRL_TYPE_H_FORMAT
                          G1PPRL_ADDR_BASE_H_FORMAT
                          G1PPRL_BYTE_H_FORMAT
                          G1PPRL_BYTE_H_FORMAT
                          G1PPRL_BYTE_H_FORMAT
                          G1PPRL_DOUBLE_H_FORMAT
                          G1PPRL_BYTE_H_FORMAT
                          G1PPRL_BYTE_H_FORMAT,
                          "", "",
                          "(bytes)", "(bytes)", "(bytes)", "(bytes/ms)",
                          "(bytes)", "(bytes)");
}

bool G1PrintRegionLivenessInfoClosure::doHeapRegion(HeapRegion* r) {
  const char* type       = r->get_type_str();
  HeapWord* bottom       = r->bottom();
  HeapWord* end          = r->end();
  size_t capacity_bytes  = r->capacity();
  size_t used_bytes      = r->used();
  size_t prev_live_bytes = r->live_bytes();
  size_t next_live_bytes = r->next_live_bytes();
  double gc_eff          = r->gc_efficiency();
  size_t remset_bytes    = r->rem_set()->mem_size();
  size_t strong_code_roots_bytes = r->rem_set()->strong_code_roots_mem_size();

  _total_used_bytes      += used_bytes;
  _total_capacity_bytes  += capacity_bytes;
  _total_prev_live_bytes += prev_live_bytes;
  _total_next_live_bytes += next_live_bytes;
  _total_remset_bytes    += remset_bytes;
  _total_strong_code_roots_bytes += strong_code_roots_bytes;

  // Print a line for this particular region.
  log_trace(gc, liveness)(G1PPRL_LINE_PREFIX
                          G1PPRL_TYPE_FORMAT
                          G1PPRL_ADDR_BASE_FORMAT
                          G1PPRL_BYTE_FORMAT
                          G1PPRL_BYTE_FORMAT
                          G1PPRL_BYTE_FORMAT
                          G1PPRL_DOUBLE_FORMAT
                          G1PPRL_BYTE_FORMAT
                          G1PPRL_BYTE_FORMAT,
                          type, p2i(bottom), p2i(end),
                          used_bytes, prev_live_bytes, next_live_bytes, gc_eff,
                          remset_bytes, strong_code_roots_bytes);

  return false;
}

G1PrintRegionLivenessInfoClosure::~G1PrintRegionLivenessInfoClosure() {
  // add static memory usages to remembered set sizes
  _total_remset_bytes += HeapRegionRemSet::fl_mem_size() + HeapRegionRemSet::static_mem_size();
  // Print the footer of the output.
  log_trace(gc, liveness)(G1PPRL_LINE_PREFIX);
  log_trace(gc, liveness)(G1PPRL_LINE_PREFIX
                         " SUMMARY"
                         G1PPRL_SUM_MB_FORMAT("capacity")
                         G1PPRL_SUM_MB_PERC_FORMAT("used")
                         G1PPRL_SUM_MB_PERC_FORMAT("prev-live")
                         G1PPRL_SUM_MB_PERC_FORMAT("next-live")
                         G1PPRL_SUM_MB_FORMAT("remset")
                         G1PPRL_SUM_MB_FORMAT("code-roots"),
                         bytes_to_mb(_total_capacity_bytes),
                         bytes_to_mb(_total_used_bytes),
                         perc(_total_used_bytes, _total_capacity_bytes),
                         bytes_to_mb(_total_prev_live_bytes),
                         perc(_total_prev_live_bytes, _total_capacity_bytes),
                         bytes_to_mb(_total_next_live_bytes),
                         perc(_total_next_live_bytes, _total_capacity_bytes),
                         bytes_to_mb(_total_remset_bytes),
                         bytes_to_mb(_total_strong_code_roots_bytes));
}
