/*
 * Copyright (c) 2001, 2012, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/symbolTable.hpp"
#include "gc_implementation/g1/concurrentMark.inline.hpp"
#include "gc_implementation/g1/concurrentMarkThread.inline.hpp"
#include "gc_implementation/g1/g1CollectedHeap.inline.hpp"
#include "gc_implementation/g1/g1CollectorPolicy.hpp"
#include "gc_implementation/g1/g1ErgoVerbose.hpp"
#include "gc_implementation/g1/g1OopClosures.inline.hpp"
#include "gc_implementation/g1/g1RemSet.hpp"
#include "gc_implementation/g1/heapRegion.inline.hpp"
#include "gc_implementation/g1/heapRegionRemSet.hpp"
#include "gc_implementation/g1/heapRegionSeq.inline.hpp"
#include "gc_implementation/shared/vmGCOperations.hpp"
#include "memory/genOopClosures.inline.hpp"
#include "memory/referencePolicy.hpp"
#include "memory/resourceArea.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/java.hpp"

//
// CMS Bit Map Wrapper

CMBitMapRO::CMBitMapRO(ReservedSpace rs, int shifter) :
  _bm((uintptr_t*)NULL,0),
  _shifter(shifter) {
  _bmStartWord = (HeapWord*)(rs.base());
  _bmWordSize  = rs.size()/HeapWordSize;    // rs.size() is in bytes
  ReservedSpace brs(ReservedSpace::allocation_align_size_up(
                     (_bmWordSize >> (_shifter + LogBitsPerByte)) + 1));

  guarantee(brs.is_reserved(), "couldn't allocate CMS bit map");
  // For now we'll just commit all of the bit map up fromt.
  // Later on we'll try to be more parsimonious with swap.
  guarantee(_virtual_space.initialize(brs, brs.size()),
            "couldn't reseve backing store for CMS bit map");
  assert(_virtual_space.committed_size() == brs.size(),
         "didn't reserve backing store for all of CMS bit map?");
  _bm.set_map((uintptr_t*)_virtual_space.low());
  assert(_virtual_space.committed_size() << (_shifter + LogBitsPerByte) >=
         _bmWordSize, "inconsistency in bit map sizing");
  _bm.set_size(_bmWordSize >> _shifter);
}

HeapWord* CMBitMapRO::getNextMarkedWordAddress(HeapWord* addr,
                                               HeapWord* limit) const {
  // First we must round addr *up* to a possible object boundary.
  addr = (HeapWord*)align_size_up((intptr_t)addr,
                                  HeapWordSize << _shifter);
  size_t addrOffset = heapWordToOffset(addr);
  if (limit == NULL) {
    limit = _bmStartWord + _bmWordSize;
  }
  size_t limitOffset = heapWordToOffset(limit);
  size_t nextOffset = _bm.get_next_one_offset(addrOffset, limitOffset);
  HeapWord* nextAddr = offsetToHeapWord(nextOffset);
  assert(nextAddr >= addr, "get_next_one postcondition");
  assert(nextAddr == limit || isMarked(nextAddr),
         "get_next_one postcondition");
  return nextAddr;
}

HeapWord* CMBitMapRO::getNextUnmarkedWordAddress(HeapWord* addr,
                                                 HeapWord* limit) const {
  size_t addrOffset = heapWordToOffset(addr);
  if (limit == NULL) {
    limit = _bmStartWord + _bmWordSize;
  }
  size_t limitOffset = heapWordToOffset(limit);
  size_t nextOffset = _bm.get_next_zero_offset(addrOffset, limitOffset);
  HeapWord* nextAddr = offsetToHeapWord(nextOffset);
  assert(nextAddr >= addr, "get_next_one postcondition");
  assert(nextAddr == limit || !isMarked(nextAddr),
         "get_next_one postcondition");
  return nextAddr;
}

int CMBitMapRO::heapWordDiffToOffsetDiff(size_t diff) const {
  assert((diff & ((1 << _shifter) - 1)) == 0, "argument check");
  return (int) (diff >> _shifter);
}

void CMBitMapRO::mostly_disjoint_range_union(BitMap*   from_bitmap,
                                             size_t    from_start_index,
                                             HeapWord* to_start_word,
                                             size_t    word_num) {
  _bm.mostly_disjoint_range_union(from_bitmap,
                                  from_start_index,
                                  heapWordToOffset(to_start_word),
                                  word_num);
}

#ifndef PRODUCT
bool CMBitMapRO::covers(ReservedSpace rs) const {
  // assert(_bm.map() == _virtual_space.low(), "map inconsistency");
  assert(((size_t)_bm.size() * (size_t)(1 << _shifter)) == _bmWordSize,
         "size inconsistency");
  return _bmStartWord == (HeapWord*)(rs.base()) &&
         _bmWordSize  == rs.size()>>LogHeapWordSize;
}
#endif

void CMBitMap::clearAll() {
  _bm.clear();
  return;
}

void CMBitMap::markRange(MemRegion mr) {
  mr.intersection(MemRegion(_bmStartWord, _bmWordSize));
  assert(!mr.is_empty(), "unexpected empty region");
  assert((offsetToHeapWord(heapWordToOffset(mr.end())) ==
          ((HeapWord *) mr.end())),
         "markRange memory region end is not card aligned");
  // convert address range into offset range
  _bm.at_put_range(heapWordToOffset(mr.start()),
                   heapWordToOffset(mr.end()), true);
}

void CMBitMap::clearRange(MemRegion mr) {
  mr.intersection(MemRegion(_bmStartWord, _bmWordSize));
  assert(!mr.is_empty(), "unexpected empty region");
  // convert address range into offset range
  _bm.at_put_range(heapWordToOffset(mr.start()),
                   heapWordToOffset(mr.end()), false);
}

MemRegion CMBitMap::getAndClearMarkedRegion(HeapWord* addr,
                                            HeapWord* end_addr) {
  HeapWord* start = getNextMarkedWordAddress(addr);
  start = MIN2(start, end_addr);
  HeapWord* end   = getNextUnmarkedWordAddress(start);
  end = MIN2(end, end_addr);
  assert(start <= end, "Consistency check");
  MemRegion mr(start, end);
  if (!mr.is_empty()) {
    clearRange(mr);
  }
  return mr;
}

CMMarkStack::CMMarkStack(ConcurrentMark* cm) :
  _base(NULL), _cm(cm)
#ifdef ASSERT
  , _drain_in_progress(false)
  , _drain_in_progress_yields(false)
#endif
{}

void CMMarkStack::allocate(size_t size) {
  _base = NEW_C_HEAP_ARRAY(oop, size);
  if (_base == NULL) {
    vm_exit_during_initialization("Failed to allocate CM region mark stack");
  }
  _index = 0;
  _capacity = (jint) size;
  _saved_index = -1;
  NOT_PRODUCT(_max_depth = 0);
}

CMMarkStack::~CMMarkStack() {
  if (_base != NULL) {
    FREE_C_HEAP_ARRAY(oop, _base);
  }
}

void CMMarkStack::par_push(oop ptr) {
  while (true) {
    if (isFull()) {
      _overflow = true;
      return;
    }
    // Otherwise...
    jint index = _index;
    jint next_index = index+1;
    jint res = Atomic::cmpxchg(next_index, &_index, index);
    if (res == index) {
      _base[index] = ptr;
      // Note that we don't maintain this atomically.  We could, but it
      // doesn't seem necessary.
      NOT_PRODUCT(_max_depth = MAX2(_max_depth, next_index));
      return;
    }
    // Otherwise, we need to try again.
  }
}

void CMMarkStack::par_adjoin_arr(oop* ptr_arr, int n) {
  while (true) {
    if (isFull()) {
      _overflow = true;
      return;
    }
    // Otherwise...
    jint index = _index;
    jint next_index = index + n;
    if (next_index > _capacity) {
      _overflow = true;
      return;
    }
    jint res = Atomic::cmpxchg(next_index, &_index, index);
    if (res == index) {
      for (int i = 0; i < n; i++) {
        int ind = index + i;
        assert(ind < _capacity, "By overflow test above.");
        _base[ind] = ptr_arr[i];
      }
      NOT_PRODUCT(_max_depth = MAX2(_max_depth, next_index));
      return;
    }
    // Otherwise, we need to try again.
  }
}


void CMMarkStack::par_push_arr(oop* ptr_arr, int n) {
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


bool CMMarkStack::par_pop_arr(oop* ptr_arr, int max, int* n) {
  MutexLockerEx x(ParGCRareEvent_lock, Mutex::_no_safepoint_check_flag);
  jint index = _index;
  if (index == 0) {
    *n = 0;
    return false;
  } else {
    int k = MIN2(max, index);
    jint new_ind = index - k;
    for (int j = 0; j < k; j++) {
      ptr_arr[j] = _base[new_ind + j];
    }
    _index = new_ind;
    *n = k;
    return true;
  }
}

CMRegionStack::CMRegionStack() : _base(NULL) {}

void CMRegionStack::allocate(size_t size) {
  _base = NEW_C_HEAP_ARRAY(MemRegion, size);
  if (_base == NULL) {
    vm_exit_during_initialization("Failed to allocate CM region mark stack");
  }
  _index = 0;
  _capacity = (jint) size;
}

CMRegionStack::~CMRegionStack() {
  if (_base != NULL) {
    FREE_C_HEAP_ARRAY(oop, _base);
  }
}

void CMRegionStack::push_lock_free(MemRegion mr) {
  guarantee(false, "push_lock_free(): don't call this any more");

  assert(mr.word_size() > 0, "Precondition");
  while (true) {
    jint index = _index;

    if (index >= _capacity) {
      _overflow = true;
      return;
    }
    // Otherwise...
    jint next_index = index+1;
    jint res = Atomic::cmpxchg(next_index, &_index, index);
    if (res == index) {
      _base[index] = mr;
      return;
    }
    // Otherwise, we need to try again.
  }
}

// Lock-free pop of the region stack. Called during the concurrent
// marking / remark phases. Should only be called in tandem with
// other lock-free pops.
MemRegion CMRegionStack::pop_lock_free() {
  guarantee(false, "pop_lock_free(): don't call this any more");

  while (true) {
    jint index = _index;

    if (index == 0) {
      return MemRegion();
    }
    // Otherwise...
    jint next_index = index-1;
    jint res = Atomic::cmpxchg(next_index, &_index, index);
    if (res == index) {
      MemRegion mr = _base[next_index];
      if (mr.start() != NULL) {
        assert(mr.end() != NULL, "invariant");
        assert(mr.word_size() > 0, "invariant");
        return mr;
      } else {
        // that entry was invalidated... let's skip it
        assert(mr.end() == NULL, "invariant");
      }
    }
    // Otherwise, we need to try again.
  }
}

#if 0
// The routines that manipulate the region stack with a lock are
// not currently used. They should be retained, however, as a
// diagnostic aid.

void CMRegionStack::push_with_lock(MemRegion mr) {
  assert(mr.word_size() > 0, "Precondition");
  MutexLockerEx x(CMRegionStack_lock, Mutex::_no_safepoint_check_flag);

  if (isFull()) {
    _overflow = true;
    return;
  }

  _base[_index] = mr;
  _index += 1;
}

MemRegion CMRegionStack::pop_with_lock() {
  MutexLockerEx x(CMRegionStack_lock, Mutex::_no_safepoint_check_flag);

  while (true) {
    if (_index == 0) {
      return MemRegion();
    }
    _index -= 1;

    MemRegion mr = _base[_index];
    if (mr.start() != NULL) {
      assert(mr.end() != NULL, "invariant");
      assert(mr.word_size() > 0, "invariant");
      return mr;
    } else {
      // that entry was invalidated... let's skip it
      assert(mr.end() == NULL, "invariant");
    }
  }
}
#endif

bool CMRegionStack::invalidate_entries_into_cset() {
  guarantee(false, "invalidate_entries_into_cset(): don't call this any more");

  bool result = false;
  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  for (int i = 0; i < _oops_do_bound; ++i) {
    MemRegion mr = _base[i];
    if (mr.start() != NULL) {
      assert(mr.end() != NULL, "invariant");
      assert(mr.word_size() > 0, "invariant");
      HeapRegion* hr = g1h->heap_region_containing(mr.start());
      assert(hr != NULL, "invariant");
      if (hr->in_collection_set()) {
        // The region points into the collection set
        _base[i] = MemRegion();
        result = true;
      }
    } else {
      // that entry was invalidated... let's skip it
      assert(mr.end() == NULL, "invariant");
    }
  }
  return result;
}

template<class OopClosureClass>
bool CMMarkStack::drain(OopClosureClass* cl, CMBitMap* bm, bool yield_after) {
  assert(!_drain_in_progress || !_drain_in_progress_yields || yield_after
         || SafepointSynchronize::is_at_safepoint(),
         "Drain recursion must be yield-safe.");
  bool res = true;
  debug_only(_drain_in_progress = true);
  debug_only(_drain_in_progress_yields = yield_after);
  while (!isEmpty()) {
    oop newOop = pop();
    assert(G1CollectedHeap::heap()->is_in_reserved(newOop), "Bad pop");
    assert(newOop->is_oop(), "Expected an oop");
    assert(bm == NULL || bm->isMarked((HeapWord*)newOop),
           "only grey objects on this stack");
    // iterate over the oops in this oop, marking and pushing
    // the ones in CMS generation.
    newOop->oop_iterate(cl);
    if (yield_after && _cm->do_yield_check()) {
      res = false;
      break;
    }
  }
  debug_only(_drain_in_progress = false);
  return res;
}

void CMMarkStack::note_start_of_gc() {
  assert(_saved_index == -1,
         "note_start_of_gc()/end_of_gc() bracketed incorrectly");
  _saved_index = _index;
}

void CMMarkStack::note_end_of_gc() {
  // This is intentionally a guarantee, instead of an assert. If we
  // accidentally add something to the mark stack during GC, it
  // will be a correctness issue so it's better if we crash. we'll
  // only check this once per GC anyway, so it won't be a performance
  // issue in any way.
  guarantee(_saved_index == _index,
            err_msg("saved index: %d index: %d", _saved_index, _index));
  _saved_index = -1;
}

void CMMarkStack::oops_do(OopClosure* f) {
  assert(_saved_index == _index,
         err_msg("saved index: %d index: %d", _saved_index, _index));
  for (int i = 0; i < _index; i += 1) {
    f->do_oop(&_base[i]);
  }
}

bool ConcurrentMark::not_yet_marked(oop obj) const {
  return (_g1h->is_obj_ill(obj)
          || (_g1h->is_in_permanent(obj)
              && !nextMarkBitMap()->isMarked((HeapWord*)obj)));
}

#ifdef _MSC_VER // the use of 'this' below gets a warning, make it go away
#pragma warning( disable:4355 ) // 'this' : used in base member initializer list
#endif // _MSC_VER

uint ConcurrentMark::scale_parallel_threads(uint n_par_threads) {
  return MAX2((n_par_threads + 2) / 4, 1U);
}

ConcurrentMark::ConcurrentMark(ReservedSpace rs,
                               int max_regions) :
  _markBitMap1(rs, MinObjAlignment - 1),
  _markBitMap2(rs, MinObjAlignment - 1),

  _parallel_marking_threads(0),
  _max_parallel_marking_threads(0),
  _sleep_factor(0.0),
  _marking_task_overhead(1.0),
  _cleanup_sleep_factor(0.0),
  _cleanup_task_overhead(1.0),
  _cleanup_list("Cleanup List"),
  _region_bm(max_regions, false /* in_resource_area*/),
  _card_bm((rs.size() + CardTableModRefBS::card_size - 1) >>
           CardTableModRefBS::card_shift,
           false /* in_resource_area*/),
  _prevMarkBitMap(&_markBitMap1),
  _nextMarkBitMap(&_markBitMap2),
  _at_least_one_mark_complete(false),

  _markStack(this),
  _regionStack(),
  // _finger set in set_non_marking_state

  _max_task_num(MAX2((uint)ParallelGCThreads, 1U)),
  // _active_tasks set in set_non_marking_state
  // _tasks set inside the constructor
  _task_queues(new CMTaskQueueSet((int) _max_task_num)),
  _terminator(ParallelTaskTerminator((int) _max_task_num, _task_queues)),

  _has_overflown(false),
  _concurrent(false),
  _has_aborted(false),
  _restart_for_overflow(false),
  _concurrent_marking_in_progress(false),
  _should_gray_objects(false),

  // _verbose_level set below

  _init_times(),
  _remark_times(), _remark_mark_times(), _remark_weak_ref_times(),
  _cleanup_times(),
  _total_counting_time(0.0),
  _total_rs_scrub_time(0.0),
  _parallel_workers(NULL) {
  CMVerboseLevel verbose_level = (CMVerboseLevel) G1MarkingVerboseLevel;
  if (verbose_level < no_verbose) {
    verbose_level = no_verbose;
  }
  if (verbose_level > high_verbose) {
    verbose_level = high_verbose;
  }
  _verbose_level = verbose_level;

  if (verbose_low()) {
    gclog_or_tty->print_cr("[global] init, heap start = "PTR_FORMAT", "
                           "heap end = "PTR_FORMAT, _heap_start, _heap_end);
  }

  _markStack.allocate(MarkStackSize);
  _regionStack.allocate(G1MarkRegionStackSize);

  // Create & start a ConcurrentMark thread.
  _cmThread = new ConcurrentMarkThread(this);
  assert(cmThread() != NULL, "CM Thread should have been created");
  assert(cmThread()->cm() != NULL, "CM Thread should refer to this cm");

  _g1h = G1CollectedHeap::heap();
  assert(CGC_lock != NULL, "Where's the CGC_lock?");
  assert(_markBitMap1.covers(rs), "_markBitMap1 inconsistency");
  assert(_markBitMap2.covers(rs), "_markBitMap2 inconsistency");

  SATBMarkQueueSet& satb_qs = JavaThread::satb_mark_queue_set();
  satb_qs.set_buffer_size(G1SATBBufferSize);

  _tasks = NEW_C_HEAP_ARRAY(CMTask*, _max_task_num);
  _accum_task_vtime = NEW_C_HEAP_ARRAY(double, _max_task_num);

  // so that the assertion in MarkingTaskQueue::task_queue doesn't fail
  _active_tasks = _max_task_num;
  for (int i = 0; i < (int) _max_task_num; ++i) {
    CMTaskQueue* task_queue = new CMTaskQueue();
    task_queue->initialize();
    _task_queues->register_queue(i, task_queue);

    _tasks[i] = new CMTask(i, this, task_queue, _task_queues);
    _accum_task_vtime[i] = 0.0;
  }

  if (ConcGCThreads > ParallelGCThreads) {
    vm_exit_during_initialization("Can't have more ConcGCThreads "
                                  "than ParallelGCThreads.");
  }
  if (ParallelGCThreads == 0) {
    // if we are not running with any parallel GC threads we will not
    // spawn any marking threads either
    _parallel_marking_threads =       0;
    _max_parallel_marking_threads =   0;
    _sleep_factor             =     0.0;
    _marking_task_overhead    =     1.0;
  } else {
    if (ConcGCThreads > 0) {
      // notice that ConcGCThreads overwrites G1MarkingOverheadPercent
      // if both are set

      _parallel_marking_threads = (uint) ConcGCThreads;
      _max_parallel_marking_threads = _parallel_marking_threads;
      _sleep_factor             = 0.0;
      _marking_task_overhead    = 1.0;
    } else if (G1MarkingOverheadPercent > 0) {
      // we will calculate the number of parallel marking threads
      // based on a target overhead with respect to the soft real-time
      // goal

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

      _parallel_marking_threads = (uint) marking_thread_num;
      _max_parallel_marking_threads = _parallel_marking_threads;
      _sleep_factor             = sleep_factor;
      _marking_task_overhead    = marking_task_overhead;
    } else {
      _parallel_marking_threads = scale_parallel_threads((uint)ParallelGCThreads);
      _max_parallel_marking_threads = _parallel_marking_threads;
      _sleep_factor             = 0.0;
      _marking_task_overhead    = 1.0;
    }

    if (parallel_marking_threads() > 1) {
      _cleanup_task_overhead = 1.0;
    } else {
      _cleanup_task_overhead = marking_task_overhead();
    }
    _cleanup_sleep_factor =
                     (1.0 - cleanup_task_overhead()) / cleanup_task_overhead();

#if 0
    gclog_or_tty->print_cr("Marking Threads          %d", parallel_marking_threads());
    gclog_or_tty->print_cr("CM Marking Task Overhead %1.4lf", marking_task_overhead());
    gclog_or_tty->print_cr("CM Sleep Factor          %1.4lf", sleep_factor());
    gclog_or_tty->print_cr("CL Marking Task Overhead %1.4lf", cleanup_task_overhead());
    gclog_or_tty->print_cr("CL Sleep Factor          %1.4lf", cleanup_sleep_factor());
#endif

    guarantee(parallel_marking_threads() > 0, "peace of mind");
    _parallel_workers = new FlexibleWorkGang("G1 Parallel Marking Threads",
         _max_parallel_marking_threads, false, true);
    if (_parallel_workers == NULL) {
      vm_exit_during_initialization("Failed necessary allocation.");
    } else {
      _parallel_workers->initialize_workers();
    }
  }

  // so that the call below can read a sensible value
  _heap_start = (HeapWord*) rs.base();
  set_non_marking_state();
}

void ConcurrentMark::update_g1_committed(bool force) {
  // If concurrent marking is not in progress, then we do not need to
  // update _heap_end. This has a subtle and important
  // side-effect. Imagine that two evacuation pauses happen between
  // marking completion and remark. The first one can grow the
  // heap (hence now the finger is below the heap end). Then, the
  // second one could unnecessarily push regions on the region
  // stack. This causes the invariant that the region stack is empty
  // at the beginning of remark to be false. By ensuring that we do
  // not observe heap expansions after marking is complete, then we do
  // not have this problem.
  if (!concurrent_marking_in_progress() && !force) return;

  MemRegion committed = _g1h->g1_committed();
  assert(committed.start() == _heap_start, "start shouldn't change");
  HeapWord* new_end = committed.end();
  if (new_end > _heap_end) {
    // The heap has been expanded.

    _heap_end = new_end;
  }
  // Notice that the heap can also shrink. However, this only happens
  // during a Full GC (at least currently) and the entire marking
  // phase will bail out and the task will not be restarted. So, let's
  // do nothing.
}

void ConcurrentMark::reset() {
  // Starting values for these two. This should be called in a STW
  // phase. CM will be notified of any future g1_committed expansions
  // will be at the end of evacuation pauses, when tasks are
  // inactive.
  MemRegion committed = _g1h->g1_committed();
  _heap_start = committed.start();
  _heap_end   = committed.end();

  // Separated the asserts so that we know which one fires.
  assert(_heap_start != NULL, "heap bounds should look ok");
  assert(_heap_end != NULL, "heap bounds should look ok");
  assert(_heap_start < _heap_end, "heap bounds should look ok");

  // reset all the marking data structures and any necessary flags
  clear_marking_state();

  if (verbose_low()) {
    gclog_or_tty->print_cr("[global] resetting");
  }

  // We do reset all of them, since different phases will use
  // different number of active threads. So, it's easiest to have all
  // of them ready.
  for (int i = 0; i < (int) _max_task_num; ++i) {
    _tasks[i]->reset(_nextMarkBitMap);
  }

  // we need this to make sure that the flag is on during the evac
  // pause with initial mark piggy-backed
  set_concurrent_marking_in_progress();
}

void ConcurrentMark::set_phase(uint active_tasks, bool concurrent) {
  assert(active_tasks <= _max_task_num, "we should not have more");

  _active_tasks = active_tasks;
  // Need to update the three data structures below according to the
  // number of active threads for this phase.
  _terminator   = ParallelTaskTerminator((int) active_tasks, _task_queues);
  _first_overflow_barrier_sync.set_n_workers((int) active_tasks);
  _second_overflow_barrier_sync.set_n_workers((int) active_tasks);

  _concurrent = concurrent;
  // We propagate this to all tasks, not just the active ones.
  for (int i = 0; i < (int) _max_task_num; ++i)
    _tasks[i]->set_concurrent(concurrent);

  if (concurrent) {
    set_concurrent_marking_in_progress();
  } else {
    // We currently assume that the concurrent flag has been set to
    // false before we start remark. At this point we should also be
    // in a STW phase.
    assert(!concurrent_marking_in_progress(), "invariant");
    assert(_finger == _heap_end, "only way to get here");
    update_g1_committed(true);
  }
}

void ConcurrentMark::set_non_marking_state() {
  // We set the global marking state to some default values when we're
  // not doing marking.
  clear_marking_state();
  _active_tasks = 0;
  clear_concurrent_marking_in_progress();
}

ConcurrentMark::~ConcurrentMark() {
  // The ConcurrentMark instance is never freed.
  ShouldNotReachHere();
}

// This closure is used to mark refs into the g1 generation
// from external roots in the CMS bit map.
// Called at the first checkpoint.
//

void ConcurrentMark::clearNextBitmap() {
  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  G1CollectorPolicy* g1p = g1h->g1_policy();

  // Make sure that the concurrent mark thread looks to still be in
  // the current cycle.
  guarantee(cmThread()->during_cycle(), "invariant");

  // We are finishing up the current cycle by clearing the next
  // marking bitmap and getting it ready for the next cycle. During
  // this time no other cycle can start. So, let's make sure that this
  // is the case.
  guarantee(!g1h->mark_in_progress(), "invariant");

  // clear the mark bitmap (no grey objects to start with).
  // We need to do this in chunks and offer to yield in between
  // each chunk.
  HeapWord* start  = _nextMarkBitMap->startWord();
  HeapWord* end    = _nextMarkBitMap->endWord();
  HeapWord* cur    = start;
  size_t chunkSize = M;
  while (cur < end) {
    HeapWord* next = cur + chunkSize;
    if (next > end) {
      next = end;
    }
    MemRegion mr(cur,next);
    _nextMarkBitMap->clearRange(mr);
    cur = next;
    do_yield_check();

    // Repeat the asserts from above. We'll do them as asserts here to
    // minimize their overhead on the product. However, we'll have
    // them as guarantees at the beginning / end of the bitmap
    // clearing to get some checking in the product.
    assert(cmThread()->during_cycle(), "invariant");
    assert(!g1h->mark_in_progress(), "invariant");
  }

  // Repeat the asserts from above.
  guarantee(cmThread()->during_cycle(), "invariant");
  guarantee(!g1h->mark_in_progress(), "invariant");
}

class NoteStartOfMarkHRClosure: public HeapRegionClosure {
public:
  bool doHeapRegion(HeapRegion* r) {
    if (!r->continuesHumongous()) {
      r->note_start_of_marking();
    }
    return false;
  }
};

void ConcurrentMark::checkpointRootsInitialPre() {
  G1CollectedHeap*   g1h = G1CollectedHeap::heap();
  G1CollectorPolicy* g1p = g1h->g1_policy();

  _has_aborted = false;

#ifndef PRODUCT
  if (G1PrintReachableAtInitialMark) {
    print_reachable("at-cycle-start",
                    VerifyOption_G1UsePrevMarking, true /* all */);
  }
#endif

  // Initialise marking structures. This has to be done in a STW phase.
  reset();

  // For each region note start of marking.
  NoteStartOfMarkHRClosure startcl;
  g1h->heap_region_iterate(&startcl);
}


void ConcurrentMark::checkpointRootsInitialPost() {
  G1CollectedHeap*   g1h = G1CollectedHeap::heap();

  // If we force an overflow during remark, the remark operation will
  // actually abort and we'll restart concurrent marking. If we always
  // force an oveflow during remark we'll never actually complete the
  // marking phase. So, we initilize this here, at the start of the
  // cycle, so that at the remaining overflow number will decrease at
  // every remark and we'll eventually not need to cause one.
  force_overflow_stw()->init();

  // Start Concurrent Marking weak-reference discovery.
  ReferenceProcessor* rp = g1h->ref_processor_cm();
  // enable ("weak") refs discovery
  rp->enable_discovery(true /*verify_disabled*/, true /*verify_no_refs*/);
  rp->setup_policy(false); // snapshot the soft ref policy to be used in this cycle

  SATBMarkQueueSet& satb_mq_set = JavaThread::satb_mark_queue_set();
  // This is the start of  the marking cycle, we're expected all
  // threads to have SATB queues with active set to false.
  satb_mq_set.set_active_all_threads(true, /* new active value */
                                     false /* expected_active */);

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
 * we'll either hit an asseert (debug / fastdebug) or deadlock
 * (product). So we should only leave / enter the STS if we are
 * operating concurrently.
 *
 * Because the thread that does the sync barrier has left the STS, it
 * is possible to be suspended for a Full GC or an evacuation pause
 * could occur. This is actually safe, since the entering the sync
 * barrier is one of the last things do_marking_step() does, and it
 * doesn't manipulate any data structures afterwards.
 */

void ConcurrentMark::enter_first_sync_barrier(int task_num) {
  if (verbose_low()) {
    gclog_or_tty->print_cr("[%d] entering first barrier", task_num);
  }

  if (concurrent()) {
    ConcurrentGCThread::stsLeave();
  }
  _first_overflow_barrier_sync.enter();
  if (concurrent()) {
    ConcurrentGCThread::stsJoin();
  }
  // at this point everyone should have synced up and not be doing any
  // more work

  if (verbose_low()) {
    gclog_or_tty->print_cr("[%d] leaving first barrier", task_num);
  }

  // let task 0 do this
  if (task_num == 0) {
    // task 0 is responsible for clearing the global data structures
    // We should be here because of an overflow. During STW we should
    // not clear the overflow flag since we rely on it being true when
    // we exit this method to abort the pause and restart concurent
    // marking.
    clear_marking_state(concurrent() /* clear_overflow */);
    force_overflow()->update();

    if (PrintGC) {
      gclog_or_tty->date_stamp(PrintGCDateStamps);
      gclog_or_tty->stamp(PrintGCTimeStamps);
      gclog_or_tty->print_cr("[GC concurrent-mark-reset-for-overflow]");
    }
  }

  // after this, each task should reset its own data structures then
  // then go into the second barrier
}

void ConcurrentMark::enter_second_sync_barrier(int task_num) {
  if (verbose_low()) {
    gclog_or_tty->print_cr("[%d] entering second barrier", task_num);
  }

  if (concurrent()) {
    ConcurrentGCThread::stsLeave();
  }
  _second_overflow_barrier_sync.enter();
  if (concurrent()) {
    ConcurrentGCThread::stsJoin();
  }
  // at this point everything should be re-initialised and ready to go

  if (verbose_low()) {
    gclog_or_tty->print_cr("[%d] leaving second barrier", task_num);
  }
}

#ifndef PRODUCT
void ForceOverflowSettings::init() {
  _num_remaining = G1ConcMarkForceOverflow;
  _force = false;
  update();
}

void ForceOverflowSettings::update() {
  if (_num_remaining > 0) {
    _num_remaining -= 1;
    _force = true;
  } else {
    _force = false;
  }
}

bool ForceOverflowSettings::should_force() {
  if (_force) {
    _force = false;
    return true;
  } else {
    return false;
  }
}
#endif // !PRODUCT

void ConcurrentMark::grayRegionIfNecessary(MemRegion mr) {
  guarantee(false, "grayRegionIfNecessary(): don't call this any more");

  // The objects on the region have already been marked "in bulk" by
  // the caller. We only need to decide whether to push the region on
  // the region stack or not.

  if (!concurrent_marking_in_progress() || !_should_gray_objects) {
    // We're done with marking and waiting for remark. We do not need to
    // push anything else on the region stack.
    return;
  }

  HeapWord* finger = _finger;

  if (verbose_low()) {
    gclog_or_tty->print_cr("[global] attempting to push "
                           "region ["PTR_FORMAT", "PTR_FORMAT"), finger is at "
                           PTR_FORMAT, mr.start(), mr.end(), finger);
  }

  if (mr.start() < finger) {
    // The finger is always heap region aligned and it is not possible
    // for mr to span heap regions.
    assert(mr.end() <= finger, "invariant");

    // Separated the asserts so that we know which one fires.
    assert(mr.start() <= mr.end(),
           "region boundaries should fall within the committed space");
    assert(_heap_start <= mr.start(),
           "region boundaries should fall within the committed space");
    assert(mr.end() <= _heap_end,
           "region boundaries should fall within the committed space");
    if (verbose_low()) {
      gclog_or_tty->print_cr("[global] region ["PTR_FORMAT", "PTR_FORMAT") "
                             "below the finger, pushing it",
                             mr.start(), mr.end());
    }

    if (!region_stack_push_lock_free(mr)) {
      if (verbose_low()) {
        gclog_or_tty->print_cr("[global] region stack has overflown.");
      }
    }
  }
}

void ConcurrentMark::markAndGrayObjectIfNecessary(oop p) {
  guarantee(false, "markAndGrayObjectIfNecessary(): don't call this any more");

  // The object is not marked by the caller. We need to at least mark
  // it and maybe push in on the stack.

  HeapWord* addr = (HeapWord*)p;
  if (!_nextMarkBitMap->isMarked(addr)) {
    // We definitely need to mark it, irrespective whether we bail out
    // because we're done with marking.
    if (_nextMarkBitMap->parMark(addr)) {
      if (!concurrent_marking_in_progress() || !_should_gray_objects) {
        // If we're done with concurrent marking and we're waiting for
        // remark, then we're not pushing anything on the stack.
        return;
      }

      // No OrderAccess:store_load() is needed. It is implicit in the
      // CAS done in parMark(addr) above
      HeapWord* finger = _finger;

      if (addr < finger) {
        if (!mark_stack_push(oop(addr))) {
          if (verbose_low()) {
            gclog_or_tty->print_cr("[global] global stack overflow "
                                   "during parMark");
          }
        }
      }
    }
  }
}

class CMConcurrentMarkingTask: public AbstractGangTask {
private:
  ConcurrentMark*       _cm;
  ConcurrentMarkThread* _cmt;

public:
  void work(uint worker_id) {
    assert(Thread::current()->is_ConcurrentGC_thread(),
           "this should only be done by a conc GC thread");
    ResourceMark rm;

    double start_vtime = os::elapsedVTime();

    ConcurrentGCThread::stsJoin();

    assert(worker_id < _cm->active_tasks(), "invariant");
    CMTask* the_task = _cm->task(worker_id);
    the_task->record_start_time();
    if (!_cm->has_aborted()) {
      do {
        double start_vtime_sec = os::elapsedVTime();
        double start_time_sec = os::elapsedTime();
        double mark_step_duration_ms = G1ConcMarkStepDurationMillis;

        the_task->do_marking_step(mark_step_duration_ms,
                                  true /* do_stealing    */,
                                  true /* do_termination */);

        double end_time_sec = os::elapsedTime();
        double end_vtime_sec = os::elapsedVTime();
        double elapsed_vtime_sec = end_vtime_sec - start_vtime_sec;
        double elapsed_time_sec = end_time_sec - start_time_sec;
        _cm->clear_has_overflown();

        bool ret = _cm->do_yield_check(worker_id);

        jlong sleep_time_ms;
        if (!_cm->has_aborted() && the_task->has_aborted()) {
          sleep_time_ms =
            (jlong) (elapsed_vtime_sec * _cm->sleep_factor() * 1000.0);
          ConcurrentGCThread::stsLeave();
          os::sleep(Thread::current(), sleep_time_ms, false);
          ConcurrentGCThread::stsJoin();
        }
        double end_time2_sec = os::elapsedTime();
        double elapsed_time2_sec = end_time2_sec - start_time_sec;

#if 0
          gclog_or_tty->print_cr("CM: elapsed %1.4lf ms, sleep %1.4lf ms, "
                                 "overhead %1.4lf",
                                 elapsed_vtime_sec * 1000.0, (double) sleep_time_ms,
                                 the_task->conc_overhead(os::elapsedTime()) * 8.0);
          gclog_or_tty->print_cr("elapsed time %1.4lf ms, time 2: %1.4lf ms",
                                 elapsed_time_sec * 1000.0, elapsed_time2_sec * 1000.0);
#endif
      } while (!_cm->has_aborted() && the_task->has_aborted());
    }
    the_task->record_end_time();
    guarantee(!the_task->has_aborted() || _cm->has_aborted(), "invariant");

    ConcurrentGCThread::stsLeave();

    double end_vtime = os::elapsedVTime();
    _cm->update_accum_task_vtime(worker_id, end_vtime - start_vtime);
  }

  CMConcurrentMarkingTask(ConcurrentMark* cm,
                          ConcurrentMarkThread* cmt) :
      AbstractGangTask("Concurrent Mark"), _cm(cm), _cmt(cmt) { }

  ~CMConcurrentMarkingTask() { }
};

// Calculates the number of active workers for a concurrent
// phase.
uint ConcurrentMark::calc_parallel_marking_threads() {
  if (G1CollectedHeap::use_parallel_gc_threads()) {
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
  // If we are not running with any parallel GC threads we will not
  // have spawned any marking threads either. Hence the number of
  // concurrent workers should be 0.
  return 0;
}

void ConcurrentMark::markFromRoots() {
  // we might be tempted to assert that:
  // assert(asynch == !SafepointSynchronize::is_at_safepoint(),
  //        "inconsistent argument?");
  // However that wouldn't be right, because it's possible that
  // a safepoint is indeed in progress as a younger generation
  // stop-the-world GC happens even as we mark in this generation.

  _restart_for_overflow = false;
  force_overflow_conc()->init();

  // _g1h has _n_par_threads
  _parallel_marking_threads = calc_parallel_marking_threads();
  assert(parallel_marking_threads() <= max_parallel_marking_threads(),
    "Maximum number of marking threads exceeded");

  uint active_workers = MAX2(1U, parallel_marking_threads());

  // Parallel task terminator is set in "set_phase()"
  set_phase(active_workers, true /* concurrent */);

  CMConcurrentMarkingTask markingTask(this, cmThread());
  if (parallel_marking_threads() > 0) {
    _parallel_workers->set_active_workers((int)active_workers);
    // Don't set _n_par_threads because it affects MT in proceess_strong_roots()
    // and the decisions on that MT processing is made elsewhere.
    assert(_parallel_workers->active_workers() > 0, "Should have been set");
    _parallel_workers->run_task(&markingTask);
  } else {
    markingTask.work(0);
  }
  print_stats();
}

void ConcurrentMark::checkpointRootsFinal(bool clear_all_soft_refs) {
  // world is stopped at this checkpoint
  assert(SafepointSynchronize::is_at_safepoint(),
         "world should be stopped");

  G1CollectedHeap* g1h = G1CollectedHeap::heap();

  // If a full collection has happened, we shouldn't do this.
  if (has_aborted()) {
    g1h->set_marking_complete(); // So bitmap clearing isn't confused
    return;
  }

  SvcGCMarker sgcm(SvcGCMarker::OTHER);

  if (VerifyDuringGC) {
    HandleMark hm;  // handle scope
    gclog_or_tty->print(" VerifyDuringGC:(before)");
    Universe::heap()->prepare_for_verify();
    Universe::verify(/* allow dirty */ true,
                     /* silent      */ false,
                     /* option      */ VerifyOption_G1UsePrevMarking);
  }

  G1CollectorPolicy* g1p = g1h->g1_policy();
  g1p->record_concurrent_mark_remark_start();

  double start = os::elapsedTime();

  checkpointRootsFinalWork();

  double mark_work_end = os::elapsedTime();

  weakRefsWork(clear_all_soft_refs);

  if (has_overflown()) {
    // Oops.  We overflowed.  Restart concurrent marking.
    _restart_for_overflow = true;
    // Clear the flag. We do not need it any more.
    clear_has_overflown();
    if (G1TraceMarkStackOverflow) {
      gclog_or_tty->print_cr("\nRemark led to restart for overflow.");
    }
  } else {
    SATBMarkQueueSet& satb_mq_set = JavaThread::satb_mark_queue_set();
    // We're done with marking.
    // This is the end of  the marking cycle, we're expected all
    // threads to have SATB queues with active set to true.
    satb_mq_set.set_active_all_threads(false, /* new active value */
                                       true /* expected_active */);

    if (VerifyDuringGC) {
      HandleMark hm;  // handle scope
      gclog_or_tty->print(" VerifyDuringGC:(after)");
      Universe::heap()->prepare_for_verify();
      Universe::verify(/* allow dirty */ true,
                       /* silent      */ false,
                       /* option      */ VerifyOption_G1UseNextMarking);
    }
    assert(!restart_for_overflow(), "sanity");
  }

  // Reset the marking state if marking completed
  if (!restart_for_overflow()) {
    set_non_marking_state();
  }

#if VERIFY_OBJS_PROCESSED
  _scan_obj_cl.objs_processed = 0;
  ThreadLocalObjQueue::objs_enqueued = 0;
#endif

  // Statistics
  double now = os::elapsedTime();
  _remark_mark_times.add((mark_work_end - start) * 1000.0);
  _remark_weak_ref_times.add((now - mark_work_end) * 1000.0);
  _remark_times.add((now - start) * 1000.0);

  g1p->record_concurrent_mark_remark_end();
}

#define CARD_BM_TEST_MODE 0

class CalcLiveObjectsClosure: public HeapRegionClosure {

  CMBitMapRO* _bm;
  ConcurrentMark* _cm;
  bool _changed;
  bool _yield;
  size_t _words_done;
  size_t _tot_live;
  size_t _tot_used;
  size_t _regions_done;
  double _start_vtime_sec;

  BitMap* _region_bm;
  BitMap* _card_bm;
  intptr_t _bottom_card_num;
  bool _final;

  void mark_card_num_range(intptr_t start_card_num, intptr_t last_card_num) {
    for (intptr_t i = start_card_num; i <= last_card_num; i++) {
#if CARD_BM_TEST_MODE
      guarantee(_card_bm->at(i - _bottom_card_num), "Should already be set.");
#else
      _card_bm->par_at_put(i - _bottom_card_num, 1);
#endif
    }
  }

public:
  CalcLiveObjectsClosure(bool final,
                         CMBitMapRO *bm, ConcurrentMark *cm,
                         BitMap* region_bm, BitMap* card_bm) :
    _bm(bm), _cm(cm), _changed(false), _yield(true),
    _words_done(0), _tot_live(0), _tot_used(0),
    _region_bm(region_bm), _card_bm(card_bm),_final(final),
    _regions_done(0), _start_vtime_sec(0.0)
  {
    _bottom_card_num =
      intptr_t(uintptr_t(G1CollectedHeap::heap()->reserved_region().start()) >>
               CardTableModRefBS::card_shift);
  }

  // It takes a region that's not empty (i.e., it has at least one
  // live object in it and sets its corresponding bit on the region
  // bitmap to 1. If the region is "starts humongous" it will also set
  // to 1 the bits on the region bitmap that correspond to its
  // associated "continues humongous" regions.
  void set_bit_for_region(HeapRegion* hr) {
    assert(!hr->continuesHumongous(), "should have filtered those out");

    size_t index = hr->hrs_index();
    if (!hr->startsHumongous()) {
      // Normal (non-humongous) case: just set the bit.
      _region_bm->par_at_put((BitMap::idx_t) index, true);
    } else {
      // Starts humongous case: calculate how many regions are part of
      // this humongous region and then set the bit range. It might
      // have been a bit more efficient to look at the object that
      // spans these humongous regions to calculate their number from
      // the object's size. However, it's a good idea to calculate
      // this based on the metadata itself, and not the region
      // contents, so that this code is not aware of what goes into
      // the humongous regions (in case this changes in the future).
      G1CollectedHeap* g1h = G1CollectedHeap::heap();
      size_t end_index = index + 1;
      while (end_index < g1h->n_regions()) {
        HeapRegion* chr = g1h->region_at(end_index);
        if (!chr->continuesHumongous()) break;
        end_index += 1;
      }
      _region_bm->par_at_put_range((BitMap::idx_t) index,
                                   (BitMap::idx_t) end_index, true);
    }
  }

  bool doHeapRegion(HeapRegion* hr) {
    if (!_final && _regions_done == 0) {
      _start_vtime_sec = os::elapsedVTime();
    }

    if (hr->continuesHumongous()) {
      // We will ignore these here and process them when their
      // associated "starts humongous" region is processed (see
      // set_bit_for_heap_region()). Note that we cannot rely on their
      // associated "starts humongous" region to have their bit set to
      // 1 since, due to the region chunking in the parallel region
      // iteration, a "continues humongous" region might be visited
      // before its associated "starts humongous".
      return false;
    }

    HeapWord* nextTop = hr->next_top_at_mark_start();
    HeapWord* start   = hr->top_at_conc_mark_count();
    assert(hr->bottom() <= start && start <= hr->end() &&
           hr->bottom() <= nextTop && nextTop <= hr->end() &&
           start <= nextTop,
           "Preconditions.");
    // Otherwise, record the number of word's we'll examine.
    size_t words_done = (nextTop - start);
    // Find the first marked object at or after "start".
    start = _bm->getNextMarkedWordAddress(start, nextTop);
    size_t marked_bytes = 0;

    // Below, the term "card num" means the result of shifting an address
    // by the card shift -- address 0 corresponds to card number 0.  One
    // must subtract the card num of the bottom of the heap to obtain a
    // card table index.
    // The first card num of the sequence of live cards currently being
    // constructed.  -1 ==> no sequence.
    intptr_t start_card_num = -1;
    // The last card num of the sequence of live cards currently being
    // constructed.  -1 ==> no sequence.
    intptr_t last_card_num = -1;

    while (start < nextTop) {
      if (_yield && _cm->do_yield_check()) {
        // We yielded.  It might be for a full collection, in which case
        // all bets are off; terminate the traversal.
        if (_cm->has_aborted()) {
          _changed = false;
          return true;
        } else {
          // Otherwise, it might be a collection pause, and the region
          // we're looking at might be in the collection set.  We'll
          // abandon this region.
          return false;
        }
      }
      oop obj = oop(start);
      int obj_sz = obj->size();
      // The card num of the start of the current object.
      intptr_t obj_card_num =
        intptr_t(uintptr_t(start) >> CardTableModRefBS::card_shift);

      HeapWord* obj_last = start + obj_sz - 1;
      intptr_t obj_last_card_num =
        intptr_t(uintptr_t(obj_last) >> CardTableModRefBS::card_shift);

      if (obj_card_num != last_card_num) {
        if (start_card_num == -1) {
          assert(last_card_num == -1, "Both or neither.");
          start_card_num = obj_card_num;
        } else {
          assert(last_card_num != -1, "Both or neither.");
          assert(obj_card_num >= last_card_num, "Inv");
          if ((obj_card_num - last_card_num) > 1) {
            // Mark the last run, and start a new one.
            mark_card_num_range(start_card_num, last_card_num);
            start_card_num = obj_card_num;
          }
        }
#if CARD_BM_TEST_MODE
        /*
        gclog_or_tty->print_cr("Setting bits from %d/%d.",
                               obj_card_num - _bottom_card_num,
                               obj_last_card_num - _bottom_card_num);
        */
        for (intptr_t j = obj_card_num; j <= obj_last_card_num; j++) {
          _card_bm->par_at_put(j - _bottom_card_num, 1);
        }
#endif
      }
      // In any case, we set the last card num.
      last_card_num = obj_last_card_num;

      marked_bytes += (size_t)obj_sz * HeapWordSize;
      // Find the next marked object after this one.
      start = _bm->getNextMarkedWordAddress(start + 1, nextTop);
      _changed = true;
    }
    // Handle the last range, if any.
    if (start_card_num != -1) {
      mark_card_num_range(start_card_num, last_card_num);
    }
    if (_final) {
      // Mark the allocated-since-marking portion...
      HeapWord* tp = hr->top();
      if (nextTop < tp) {
        start_card_num =
          intptr_t(uintptr_t(nextTop) >> CardTableModRefBS::card_shift);
        last_card_num =
          intptr_t(uintptr_t(tp) >> CardTableModRefBS::card_shift);
        mark_card_num_range(start_card_num, last_card_num);
        // This definitely means the region has live objects.
        set_bit_for_region(hr);
      }
    }

    hr->add_to_marked_bytes(marked_bytes);
    // Update the live region bitmap.
    if (marked_bytes > 0) {
      set_bit_for_region(hr);
    }
    hr->set_top_at_conc_mark_count(nextTop);
    _tot_live += hr->next_live_bytes();
    _tot_used += hr->used();
    _words_done = words_done;

    if (!_final) {
      ++_regions_done;
      if (_regions_done % 10 == 0) {
        double end_vtime_sec = os::elapsedVTime();
        double elapsed_vtime_sec = end_vtime_sec - _start_vtime_sec;
        if (elapsed_vtime_sec > (10.0 / 1000.0)) {
          jlong sleep_time_ms =
            (jlong) (elapsed_vtime_sec * _cm->cleanup_sleep_factor() * 1000.0);
          os::sleep(Thread::current(), sleep_time_ms, false);
          _start_vtime_sec = end_vtime_sec;
        }
      }
    }

    return false;
  }

  bool changed() { return _changed;  }
  void reset()   { _changed = false; _words_done = 0; }
  void no_yield() { _yield = false; }
  size_t words_done() { return _words_done; }
  size_t tot_live() { return _tot_live; }
  size_t tot_used() { return _tot_used; }
};


void ConcurrentMark::calcDesiredRegions() {
  _region_bm.clear();
  _card_bm.clear();
  CalcLiveObjectsClosure calccl(false /*final*/,
                                nextMarkBitMap(), this,
                                &_region_bm, &_card_bm);
  G1CollectedHeap *g1h = G1CollectedHeap::heap();
  g1h->heap_region_iterate(&calccl);

  do {
    calccl.reset();
    g1h->heap_region_iterate(&calccl);
  } while (calccl.changed());
}

class G1ParFinalCountTask: public AbstractGangTask {
protected:
  G1CollectedHeap* _g1h;
  CMBitMap* _bm;
  uint    _n_workers;
  size_t *_live_bytes;
  size_t *_used_bytes;
  BitMap* _region_bm;
  BitMap* _card_bm;
public:
  G1ParFinalCountTask(G1CollectedHeap* g1h, CMBitMap* bm,
                      BitMap* region_bm, BitMap* card_bm)
    : AbstractGangTask("G1 final counting"), _g1h(g1h),
    _bm(bm), _region_bm(region_bm), _card_bm(card_bm),
    _n_workers(0)
  {
    // Use the value already set as the number of active threads
    // in the call to run_task().  Needed for the allocation of
    // _live_bytes and _used_bytes.
    if (G1CollectedHeap::use_parallel_gc_threads()) {
      assert( _g1h->workers()->active_workers() > 0,
        "Should have been previously set");
      _n_workers = _g1h->workers()->active_workers();
    } else {
      _n_workers = 1;
    }

    _live_bytes = NEW_C_HEAP_ARRAY(size_t, _n_workers);
    _used_bytes = NEW_C_HEAP_ARRAY(size_t, _n_workers);
  }

  ~G1ParFinalCountTask() {
    FREE_C_HEAP_ARRAY(size_t, _live_bytes);
    FREE_C_HEAP_ARRAY(size_t, _used_bytes);
  }

  void work(uint worker_id) {
    CalcLiveObjectsClosure calccl(true /*final*/,
                                  _bm, _g1h->concurrent_mark(),
                                  _region_bm, _card_bm);
    calccl.no_yield();
    if (G1CollectedHeap::use_parallel_gc_threads()) {
      _g1h->heap_region_par_iterate_chunked(&calccl, worker_id,
                                            (int) _n_workers,
                                            HeapRegion::FinalCountClaimValue);
    } else {
      _g1h->heap_region_iterate(&calccl);
    }
    assert(calccl.complete(), "Shouldn't have yielded!");

    assert(worker_id < _n_workers, "invariant");
    _live_bytes[worker_id] = calccl.tot_live();
    _used_bytes[worker_id] = calccl.tot_used();
  }
  size_t live_bytes()  {
    size_t live_bytes = 0;
    for (uint i = 0; i < _n_workers; ++i)
      live_bytes += _live_bytes[i];
    return live_bytes;
  }
  size_t used_bytes()  {
    size_t used_bytes = 0;
    for (uint i = 0; i < _n_workers; ++i)
      used_bytes += _used_bytes[i];
    return used_bytes;
  }
};

class G1ParNoteEndTask;

class G1NoteEndOfConcMarkClosure : public HeapRegionClosure {
  G1CollectedHeap* _g1;
  int _worker_num;
  size_t _max_live_bytes;
  size_t _regions_claimed;
  size_t _freed_bytes;
  FreeRegionList* _local_cleanup_list;
  OldRegionSet* _old_proxy_set;
  HumongousRegionSet* _humongous_proxy_set;
  HRRSCleanupTask* _hrrs_cleanup_task;
  double _claimed_region_time;
  double _max_region_time;

public:
  G1NoteEndOfConcMarkClosure(G1CollectedHeap* g1,
                             int worker_num,
                             FreeRegionList* local_cleanup_list,
                             OldRegionSet* old_proxy_set,
                             HumongousRegionSet* humongous_proxy_set,
                             HRRSCleanupTask* hrrs_cleanup_task) :
    _g1(g1), _worker_num(worker_num),
    _max_live_bytes(0), _regions_claimed(0),
    _freed_bytes(0),
    _claimed_region_time(0.0), _max_region_time(0.0),
    _local_cleanup_list(local_cleanup_list),
    _old_proxy_set(old_proxy_set),
    _humongous_proxy_set(humongous_proxy_set),
    _hrrs_cleanup_task(hrrs_cleanup_task) { }

  size_t freed_bytes() { return _freed_bytes; }

  bool doHeapRegion(HeapRegion *hr) {
    // We use a claim value of zero here because all regions
    // were claimed with value 1 in the FinalCount task.
    hr->reset_gc_time_stamp();
    if (!hr->continuesHumongous()) {
      double start = os::elapsedTime();
      _regions_claimed++;
      hr->note_end_of_marking();
      _max_live_bytes += hr->max_live_bytes();
      _g1->free_region_if_empty(hr,
                                &_freed_bytes,
                                _local_cleanup_list,
                                _old_proxy_set,
                                _humongous_proxy_set,
                                _hrrs_cleanup_task,
                                true /* par */);
      double region_time = (os::elapsedTime() - start);
      _claimed_region_time += region_time;
      if (region_time > _max_region_time) {
        _max_region_time = region_time;
      }
    }
    return false;
  }

  size_t max_live_bytes() { return _max_live_bytes; }
  size_t regions_claimed() { return _regions_claimed; }
  double claimed_region_time_sec() { return _claimed_region_time; }
  double max_region_time_sec() { return _max_region_time; }
};

class G1ParNoteEndTask: public AbstractGangTask {
  friend class G1NoteEndOfConcMarkClosure;

protected:
  G1CollectedHeap* _g1h;
  size_t _max_live_bytes;
  size_t _freed_bytes;
  FreeRegionList* _cleanup_list;

public:
  G1ParNoteEndTask(G1CollectedHeap* g1h,
                   FreeRegionList* cleanup_list) :
    AbstractGangTask("G1 note end"), _g1h(g1h),
    _max_live_bytes(0), _freed_bytes(0), _cleanup_list(cleanup_list) { }

  void work(uint worker_id) {
    double start = os::elapsedTime();
    FreeRegionList local_cleanup_list("Local Cleanup List");
    OldRegionSet old_proxy_set("Local Cleanup Old Proxy Set");
    HumongousRegionSet humongous_proxy_set("Local Cleanup Humongous Proxy Set");
    HRRSCleanupTask hrrs_cleanup_task;
    G1NoteEndOfConcMarkClosure g1_note_end(_g1h, worker_id, &local_cleanup_list,
                                           &old_proxy_set,
                                           &humongous_proxy_set,
                                           &hrrs_cleanup_task);
    if (G1CollectedHeap::use_parallel_gc_threads()) {
      _g1h->heap_region_par_iterate_chunked(&g1_note_end, worker_id,
                                            _g1h->workers()->active_workers(),
                                            HeapRegion::NoteEndClaimValue);
    } else {
      _g1h->heap_region_iterate(&g1_note_end);
    }
    assert(g1_note_end.complete(), "Shouldn't have yielded!");

    // Now update the lists
    _g1h->update_sets_after_freeing_regions(g1_note_end.freed_bytes(),
                                            NULL /* free_list */,
                                            &old_proxy_set,
                                            &humongous_proxy_set,
                                            true /* par */);
    {
      MutexLockerEx x(ParGCRareEvent_lock, Mutex::_no_safepoint_check_flag);
      _max_live_bytes += g1_note_end.max_live_bytes();
      _freed_bytes += g1_note_end.freed_bytes();

      // If we iterate over the global cleanup list at the end of
      // cleanup to do this printing we will not guarantee to only
      // generate output for the newly-reclaimed regions (the list
      // might not be empty at the beginning of cleanup; we might
      // still be working on its previous contents). So we do the
      // printing here, before we append the new regions to the global
      // cleanup list.

      G1HRPrinter* hr_printer = _g1h->hr_printer();
      if (hr_printer->is_active()) {
        HeapRegionLinkedListIterator iter(&local_cleanup_list);
        while (iter.more_available()) {
          HeapRegion* hr = iter.get_next();
          hr_printer->cleanup(hr);
        }
      }

      _cleanup_list->add_as_tail(&local_cleanup_list);
      assert(local_cleanup_list.is_empty(), "post-condition");

      HeapRegionRemSet::finish_cleanup_task(&hrrs_cleanup_task);
    }
    double end = os::elapsedTime();
    if (G1PrintParCleanupStats) {
      gclog_or_tty->print("     Worker thread %d [%8.3f..%8.3f = %8.3f ms] "
                          "claimed %u regions (tot = %8.3f ms, max = %8.3f ms).\n",
                          worker_id, start, end, (end-start)*1000.0,
                          g1_note_end.regions_claimed(),
                          g1_note_end.claimed_region_time_sec()*1000.0,
                          g1_note_end.max_region_time_sec()*1000.0);
    }
  }
  size_t max_live_bytes() { return _max_live_bytes; }
  size_t freed_bytes() { return _freed_bytes; }
};

class G1ParScrubRemSetTask: public AbstractGangTask {
protected:
  G1RemSet* _g1rs;
  BitMap* _region_bm;
  BitMap* _card_bm;
public:
  G1ParScrubRemSetTask(G1CollectedHeap* g1h,
                       BitMap* region_bm, BitMap* card_bm) :
    AbstractGangTask("G1 ScrubRS"), _g1rs(g1h->g1_rem_set()),
    _region_bm(region_bm), _card_bm(card_bm)
  {}

  void work(uint worker_id) {
    if (G1CollectedHeap::use_parallel_gc_threads()) {
      _g1rs->scrub_par(_region_bm, _card_bm, worker_id,
                       HeapRegion::ScrubRemSetClaimValue);
    } else {
      _g1rs->scrub(_region_bm, _card_bm);
    }
  }

};

void ConcurrentMark::cleanup() {
  // world is stopped at this checkpoint
  assert(SafepointSynchronize::is_at_safepoint(),
         "world should be stopped");
  G1CollectedHeap* g1h = G1CollectedHeap::heap();

  // If a full collection has happened, we shouldn't do this.
  if (has_aborted()) {
    g1h->set_marking_complete(); // So bitmap clearing isn't confused
    return;
  }

  HRSPhaseSetter x(HRSPhaseCleanup);
  g1h->verify_region_sets_optional();

  if (VerifyDuringGC) {
    HandleMark hm;  // handle scope
    gclog_or_tty->print(" VerifyDuringGC:(before)");
    Universe::heap()->prepare_for_verify();
    Universe::verify(/* allow dirty */ true,
                     /* silent      */ false,
                     /* option      */ VerifyOption_G1UsePrevMarking);
  }

  G1CollectorPolicy* g1p = G1CollectedHeap::heap()->g1_policy();
  g1p->record_concurrent_mark_cleanup_start();

  double start = os::elapsedTime();

  HeapRegionRemSet::reset_for_cleanup_tasks();

  uint n_workers;

  // Do counting once more with the world stopped for good measure.
  G1ParFinalCountTask g1_par_count_task(g1h, nextMarkBitMap(),
                                        &_region_bm, &_card_bm);
  if (G1CollectedHeap::use_parallel_gc_threads()) {
    assert(g1h->check_heap_region_claim_values(
                                               HeapRegion::InitialClaimValue),
           "sanity check");

    g1h->set_par_threads();
    n_workers = g1h->n_par_threads();
    assert(g1h->n_par_threads() == n_workers,
           "Should not have been reset");
    g1h->workers()->run_task(&g1_par_count_task);
    // Done with the parallel phase so reset to 0.
    g1h->set_par_threads(0);

    assert(g1h->check_heap_region_claim_values(
                                             HeapRegion::FinalCountClaimValue),
           "sanity check");
  } else {
    n_workers = 1;
    g1_par_count_task.work(0);
  }

  size_t known_garbage_bytes =
    g1_par_count_task.used_bytes() - g1_par_count_task.live_bytes();
  g1p->set_known_garbage_bytes(known_garbage_bytes);

  size_t start_used_bytes = g1h->used();
  _at_least_one_mark_complete = true;
  g1h->set_marking_complete();

  ergo_verbose4(ErgoConcCycles,
           "finish cleanup",
           ergo_format_byte("occupancy")
           ergo_format_byte("capacity")
           ergo_format_byte_perc("known garbage"),
           start_used_bytes, g1h->capacity(),
           known_garbage_bytes,
           ((double) known_garbage_bytes / (double) g1h->capacity()) * 100.0);

  double count_end = os::elapsedTime();
  double this_final_counting_time = (count_end - start);
  if (G1PrintParCleanupStats) {
    gclog_or_tty->print_cr("Cleanup:");
    gclog_or_tty->print_cr("  Finalize counting: %8.3f ms",
                           this_final_counting_time*1000.0);
  }
  _total_counting_time += this_final_counting_time;

  if (G1PrintRegionLivenessInfo) {
    G1PrintRegionLivenessInfoClosure cl(gclog_or_tty, "Post-Marking");
    _g1h->heap_region_iterate(&cl);
  }

  // Install newly created mark bitMap as "prev".
  swapMarkBitMaps();

  g1h->reset_gc_time_stamp();

  // Note end of marking in all heap regions.
  double note_end_start = os::elapsedTime();
  G1ParNoteEndTask g1_par_note_end_task(g1h, &_cleanup_list);
  if (G1CollectedHeap::use_parallel_gc_threads()) {
    g1h->set_par_threads((int)n_workers);
    g1h->workers()->run_task(&g1_par_note_end_task);
    g1h->set_par_threads(0);

    assert(g1h->check_heap_region_claim_values(HeapRegion::NoteEndClaimValue),
           "sanity check");
  } else {
    g1_par_note_end_task.work(0);
  }

  if (!cleanup_list_is_empty()) {
    // The cleanup list is not empty, so we'll have to process it
    // concurrently. Notify anyone else that might be wanting free
    // regions that there will be more free regions coming soon.
    g1h->set_free_regions_coming();
  }
  double note_end_end = os::elapsedTime();
  if (G1PrintParCleanupStats) {
    gclog_or_tty->print_cr("  note end of marking: %8.3f ms.",
                           (note_end_end - note_end_start)*1000.0);
  }

  // call below, since it affects the metric by which we sort the heap
  // regions.
  if (G1ScrubRemSets) {
    double rs_scrub_start = os::elapsedTime();
    G1ParScrubRemSetTask g1_par_scrub_rs_task(g1h, &_region_bm, &_card_bm);
    if (G1CollectedHeap::use_parallel_gc_threads()) {
      g1h->set_par_threads((int)n_workers);
      g1h->workers()->run_task(&g1_par_scrub_rs_task);
      g1h->set_par_threads(0);

      assert(g1h->check_heap_region_claim_values(
                                            HeapRegion::ScrubRemSetClaimValue),
             "sanity check");
    } else {
      g1_par_scrub_rs_task.work(0);
    }

    double rs_scrub_end = os::elapsedTime();
    double this_rs_scrub_time = (rs_scrub_end - rs_scrub_start);
    _total_rs_scrub_time += this_rs_scrub_time;
  }

  // this will also free any regions totally full of garbage objects,
  // and sort the regions.
  g1h->g1_policy()->record_concurrent_mark_cleanup_end((int)n_workers);

  // Statistics.
  double end = os::elapsedTime();
  _cleanup_times.add((end - start) * 1000.0);

  if (PrintGC || PrintGCDetails) {
    g1h->print_size_transition(gclog_or_tty,
                               start_used_bytes,
                               g1h->used(),
                               g1h->capacity());
  }

  size_t cleaned_up_bytes = start_used_bytes - g1h->used();
  g1p->decrease_known_garbage_bytes(cleaned_up_bytes);

  // Clean up will have freed any regions completely full of garbage.
  // Update the soft reference policy with the new heap occupancy.
  Universe::update_heap_info_at_gc();

  // We need to make this be a "collection" so any collection pause that
  // races with it goes around and waits for completeCleanup to finish.
  g1h->increment_total_collections();

  if (VerifyDuringGC) {
    HandleMark hm;  // handle scope
    gclog_or_tty->print(" VerifyDuringGC:(after)");
    Universe::heap()->prepare_for_verify();
    Universe::verify(/* allow dirty */ true,
                     /* silent      */ false,
                     /* option      */ VerifyOption_G1UsePrevMarking);
  }

  g1h->verify_region_sets_optional();
}

void ConcurrentMark::completeCleanup() {
  if (has_aborted()) return;

  G1CollectedHeap* g1h = G1CollectedHeap::heap();

  _cleanup_list.verify_optional();
  FreeRegionList tmp_free_list("Tmp Free List");

  if (G1ConcRegionFreeingVerbose) {
    gclog_or_tty->print_cr("G1ConcRegionFreeing [complete cleanup] : "
                           "cleanup list has "SIZE_FORMAT" entries",
                           _cleanup_list.length());
  }

  // Noone else should be accessing the _cleanup_list at this point,
  // so it's not necessary to take any locks
  while (!_cleanup_list.is_empty()) {
    HeapRegion* hr = _cleanup_list.remove_head();
    assert(hr != NULL, "the list was not empty");
    hr->par_clear();
    tmp_free_list.add_as_tail(hr);

    // Instead of adding one region at a time to the secondary_free_list,
    // we accumulate them in the local list and move them a few at a
    // time. This also cuts down on the number of notify_all() calls
    // we do during this process. We'll also append the local list when
    // _cleanup_list is empty (which means we just removed the last
    // region from the _cleanup_list).
    if ((tmp_free_list.length() % G1SecondaryFreeListAppendLength == 0) ||
        _cleanup_list.is_empty()) {
      if (G1ConcRegionFreeingVerbose) {
        gclog_or_tty->print_cr("G1ConcRegionFreeing [complete cleanup] : "
                               "appending "SIZE_FORMAT" entries to the "
                               "secondary_free_list, clean list still has "
                               SIZE_FORMAT" entries",
                               tmp_free_list.length(),
                               _cleanup_list.length());
      }

      {
        MutexLockerEx x(SecondaryFreeList_lock, Mutex::_no_safepoint_check_flag);
        g1h->secondary_free_list_add_as_tail(&tmp_free_list);
        SecondaryFreeList_lock->notify_all();
      }

      if (G1StressConcRegionFreeing) {
        for (uintx i = 0; i < G1StressConcRegionFreeingDelayMillis; ++i) {
          os::sleep(Thread::current(), (jlong) 1, false);
        }
      }
    }
  }
  assert(tmp_free_list.is_empty(), "post-condition");
}

// Support closures for reference procssing in G1

bool G1CMIsAliveClosure::do_object_b(oop obj) {
  HeapWord* addr = (HeapWord*)obj;
  return addr != NULL &&
         (!_g1->is_in_g1_reserved(addr) || !_g1->is_obj_ill(obj));
}

class G1CMKeepAliveClosure: public OopClosure {
  G1CollectedHeap* _g1;
  ConcurrentMark*  _cm;
  CMBitMap*        _bitMap;
 public:
  G1CMKeepAliveClosure(G1CollectedHeap* g1, ConcurrentMark* cm,
                       CMBitMap* bitMap) :
    _g1(g1), _cm(cm),
    _bitMap(bitMap) {}

  virtual void do_oop(narrowOop* p) { do_oop_work(p); }
  virtual void do_oop(      oop* p) { do_oop_work(p); }

  template <class T> void do_oop_work(T* p) {
    oop obj = oopDesc::load_decode_heap_oop(p);
    HeapWord* addr = (HeapWord*)obj;

    if (_cm->verbose_high()) {
      gclog_or_tty->print_cr("\t[0] we're looking at location "
                             "*"PTR_FORMAT" = "PTR_FORMAT,
                             p, (void*) obj);
    }

    if (_g1->is_in_g1_reserved(addr) && _g1->is_obj_ill(obj)) {
      _bitMap->mark(addr);
      _cm->mark_stack_push(obj);
    }
  }
};

class G1CMDrainMarkingStackClosure: public VoidClosure {
  CMMarkStack*                  _markStack;
  CMBitMap*                     _bitMap;
  G1CMKeepAliveClosure*         _oopClosure;
 public:
  G1CMDrainMarkingStackClosure(CMBitMap* bitMap, CMMarkStack* markStack,
                               G1CMKeepAliveClosure* oopClosure) :
    _bitMap(bitMap),
    _markStack(markStack),
    _oopClosure(oopClosure)
  {}

  void do_void() {
    _markStack->drain((OopClosure*)_oopClosure, _bitMap, false);
  }
};

// 'Keep Alive' closure used by parallel reference processing.
// An instance of this closure is used in the parallel reference processing
// code rather than an instance of G1CMKeepAliveClosure. We could have used
// the G1CMKeepAliveClosure as it is MT-safe. Also reference objects are
// placed on to discovered ref lists once so we can mark and push with no
// need to check whether the object has already been marked. Using the
// G1CMKeepAliveClosure would mean, however, having all the worker threads
// operating on the global mark stack. This means that an individual
// worker would be doing lock-free pushes while it processes its own
// discovered ref list followed by drain call. If the discovered ref lists
// are unbalanced then this could cause interference with the other
// workers. Using a CMTask (and its embedded local data structures)
// avoids that potential interference.
class G1CMParKeepAliveAndDrainClosure: public OopClosure {
  ConcurrentMark*  _cm;
  CMTask*          _task;
  int              _ref_counter_limit;
  int              _ref_counter;
 public:
  G1CMParKeepAliveAndDrainClosure(ConcurrentMark* cm, CMTask* task) :
    _cm(cm), _task(task),
    _ref_counter_limit(G1RefProcDrainInterval) {
    assert(_ref_counter_limit > 0, "sanity");
    _ref_counter = _ref_counter_limit;
  }

  virtual void do_oop(narrowOop* p) { do_oop_work(p); }
  virtual void do_oop(      oop* p) { do_oop_work(p); }

  template <class T> void do_oop_work(T* p) {
    if (!_cm->has_overflown()) {
      oop obj = oopDesc::load_decode_heap_oop(p);
      if (_cm->verbose_high()) {
        gclog_or_tty->print_cr("\t[%d] we're looking at location "
                               "*"PTR_FORMAT" = "PTR_FORMAT,
                               _task->task_id(), p, (void*) obj);
      }

      _task->deal_with_reference(obj);
      _ref_counter--;

      if (_ref_counter == 0) {
        // We have dealt with _ref_counter_limit references, pushing them and objects
        // reachable from them on to the local stack (and possibly the global stack).
        // Call do_marking_step() to process these entries. We call the routine in a
        // loop, which we'll exit if there's nothing more to do (i.e. we're done
        // with the entries that we've pushed as a result of the deal_with_reference
        // calls above) or we overflow.
        // Note: CMTask::do_marking_step() can set the CMTask::has_aborted() flag
        // while there may still be some work to do. (See the comment at the
        // beginning of CMTask::do_marking_step() for those conditions - one of which
        // is reaching the specified time target.) It is only when
        // CMTask::do_marking_step() returns without setting the has_aborted() flag
        // that the marking has completed.
        do {
          double mark_step_duration_ms = G1ConcMarkStepDurationMillis;
          _task->do_marking_step(mark_step_duration_ms,
                                 false /* do_stealing    */,
                                 false /* do_termination */);
        } while (_task->has_aborted() && !_cm->has_overflown());
        _ref_counter = _ref_counter_limit;
      }
    } else {
      if (_cm->verbose_high()) {
         gclog_or_tty->print_cr("\t[%d] CM Overflow", _task->task_id());
      }
    }
  }
};

class G1CMParDrainMarkingStackClosure: public VoidClosure {
  ConcurrentMark* _cm;
  CMTask* _task;
 public:
  G1CMParDrainMarkingStackClosure(ConcurrentMark* cm, CMTask* task) :
    _cm(cm), _task(task)
  {}

  void do_void() {
    do {
      if (_cm->verbose_high()) {
        gclog_or_tty->print_cr("\t[%d] Drain: Calling do marking_step",
                               _task->task_id());
      }

      // We call CMTask::do_marking_step() to completely drain the local and
      // global marking stacks. The routine is called in a loop, which we'll
      // exit if there's nothing more to do (i.e. we'completely drained the
      // entries that were pushed as a result of applying the
      // G1CMParKeepAliveAndDrainClosure to the entries on the discovered ref
      // lists above) or we overflow the global marking stack.
      // Note: CMTask::do_marking_step() can set the CMTask::has_aborted() flag
      // while there may still be some work to do. (See the comment at the
      // beginning of CMTask::do_marking_step() for those conditions - one of which
      // is reaching the specified time target.) It is only when
      // CMTask::do_marking_step() returns without setting the has_aborted() flag
      // that the marking has completed.

      _task->do_marking_step(1000000000.0 /* something very large */,
                             true /* do_stealing    */,
                             true /* do_termination */);
    } while (_task->has_aborted() && !_cm->has_overflown());
  }
};

// Implementation of AbstractRefProcTaskExecutor for parallel
// reference processing at the end of G1 concurrent marking

class G1CMRefProcTaskExecutor: public AbstractRefProcTaskExecutor {
private:
  G1CollectedHeap* _g1h;
  ConcurrentMark*  _cm;
  WorkGang*        _workers;
  int              _active_workers;

public:
  G1CMRefProcTaskExecutor(G1CollectedHeap* g1h,
                        ConcurrentMark* cm,
                        WorkGang* workers,
                        int n_workers) :
    _g1h(g1h), _cm(cm),
    _workers(workers), _active_workers(n_workers) { }

  // Executes the given task using concurrent marking worker threads.
  virtual void execute(ProcessTask& task);
  virtual void execute(EnqueueTask& task);
};

class G1CMRefProcTaskProxy: public AbstractGangTask {
  typedef AbstractRefProcTaskExecutor::ProcessTask ProcessTask;
  ProcessTask&     _proc_task;
  G1CollectedHeap* _g1h;
  ConcurrentMark*  _cm;

public:
  G1CMRefProcTaskProxy(ProcessTask& proc_task,
                     G1CollectedHeap* g1h,
                     ConcurrentMark* cm) :
    AbstractGangTask("Process reference objects in parallel"),
    _proc_task(proc_task), _g1h(g1h), _cm(cm) { }

  virtual void work(uint worker_id) {
    CMTask* marking_task = _cm->task(worker_id);
    G1CMIsAliveClosure g1_is_alive(_g1h);
    G1CMParKeepAliveAndDrainClosure g1_par_keep_alive(_cm, marking_task);
    G1CMParDrainMarkingStackClosure g1_par_drain(_cm, marking_task);

    _proc_task.work(worker_id, g1_is_alive, g1_par_keep_alive, g1_par_drain);
  }
};

void G1CMRefProcTaskExecutor::execute(ProcessTask& proc_task) {
  assert(_workers != NULL, "Need parallel worker threads.");

  G1CMRefProcTaskProxy proc_task_proxy(proc_task, _g1h, _cm);

  // We need to reset the phase for each task execution so that
  // the termination protocol of CMTask::do_marking_step works.
  _cm->set_phase(_active_workers, false /* concurrent */);
  _g1h->set_par_threads(_active_workers);
  _workers->run_task(&proc_task_proxy);
  _g1h->set_par_threads(0);
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

  G1CMRefEnqueueTaskProxy enq_task_proxy(enq_task);

  _g1h->set_par_threads(_active_workers);
  _workers->run_task(&enq_task_proxy);
  _g1h->set_par_threads(0);
}

void ConcurrentMark::weakRefsWork(bool clear_all_soft_refs) {
  ResourceMark rm;
  HandleMark   hm;

  G1CollectedHeap* g1h = G1CollectedHeap::heap();

  // Is alive closure.
  G1CMIsAliveClosure g1_is_alive(g1h);

  // Inner scope to exclude the cleaning of the string and symbol
  // tables from the displayed time.
  {
    bool verbose = PrintGC && PrintGCDetails;
    if (verbose) {
      gclog_or_tty->put(' ');
    }
    TraceTime t("GC ref-proc", verbose, false, gclog_or_tty);

    ReferenceProcessor* rp = g1h->ref_processor_cm();

    // See the comment in G1CollectedHeap::ref_processing_init()
    // about how reference processing currently works in G1.

    // Process weak references.
    rp->setup_policy(clear_all_soft_refs);
    assert(_markStack.isEmpty(), "mark stack should be empty");

    G1CMKeepAliveClosure g1_keep_alive(g1h, this, nextMarkBitMap());
    G1CMDrainMarkingStackClosure
      g1_drain_mark_stack(nextMarkBitMap(), &_markStack, &g1_keep_alive);

    // We use the work gang from the G1CollectedHeap and we utilize all
    // the worker threads.
    uint active_workers = g1h->workers() ? g1h->workers()->active_workers() : 1U;
    active_workers = MAX2(MIN2(active_workers, _max_task_num), 1U);

    G1CMRefProcTaskExecutor par_task_executor(g1h, this,
                                              g1h->workers(), active_workers);

    if (rp->processing_is_mt()) {
      // Set the degree of MT here.  If the discovery is done MT, there
      // may have been a different number of threads doing the discovery
      // and a different number of discovered lists may have Ref objects.
      // That is OK as long as the Reference lists are balanced (see
      // balance_all_queues() and balance_queues()).
      rp->set_active_mt_degree(active_workers);

      rp->process_discovered_references(&g1_is_alive,
                                      &g1_keep_alive,
                                      &g1_drain_mark_stack,
                                      &par_task_executor);

      // The work routines of the parallel keep_alive and drain_marking_stack
      // will set the has_overflown flag if we overflow the global marking
      // stack.
    } else {
      rp->process_discovered_references(&g1_is_alive,
                                        &g1_keep_alive,
                                        &g1_drain_mark_stack,
                                        NULL);
    }

    assert(_markStack.overflow() || _markStack.isEmpty(),
            "mark stack should be empty (unless it overflowed)");
    if (_markStack.overflow()) {
      // Should have been done already when we tried to push an
      // entry on to the global mark stack. But let's do it again.
      set_has_overflown();
    }

    if (rp->processing_is_mt()) {
      assert(rp->num_q() == active_workers, "why not");
      rp->enqueue_discovered_references(&par_task_executor);
    } else {
      rp->enqueue_discovered_references();
    }

    rp->verify_no_references_recorded();
    assert(!rp->discovery_enabled(), "Post condition");
  }

  // Now clean up stale oops in StringTable
  StringTable::unlink(&g1_is_alive);
  // Clean up unreferenced symbols in symbol table.
  SymbolTable::unlink();
}

void ConcurrentMark::swapMarkBitMaps() {
  CMBitMapRO* temp = _prevMarkBitMap;
  _prevMarkBitMap  = (CMBitMapRO*)_nextMarkBitMap;
  _nextMarkBitMap  = (CMBitMap*)  temp;
}

class CMRemarkTask: public AbstractGangTask {
private:
  ConcurrentMark *_cm;

public:
  void work(uint worker_id) {
    // Since all available tasks are actually started, we should
    // only proceed if we're supposed to be actived.
    if (worker_id < _cm->active_tasks()) {
      CMTask* task = _cm->task(worker_id);
      task->record_start_time();
      do {
        task->do_marking_step(1000000000.0 /* something very large */,
                              true /* do_stealing    */,
                              true /* do_termination */);
      } while (task->has_aborted() && !_cm->has_overflown());
      // If we overflow, then we do not want to restart. We instead
      // want to abort remark and do concurrent marking again.
      task->record_end_time();
    }
  }

  CMRemarkTask(ConcurrentMark* cm, int active_workers) :
    AbstractGangTask("Par Remark"), _cm(cm) {
    _cm->terminator()->reset_for_reuse(active_workers);
  }
};

void ConcurrentMark::checkpointRootsFinalWork() {
  ResourceMark rm;
  HandleMark   hm;
  G1CollectedHeap* g1h = G1CollectedHeap::heap();

  g1h->ensure_parsability(false);

  if (G1CollectedHeap::use_parallel_gc_threads()) {
    G1CollectedHeap::StrongRootsScope srs(g1h);
    // this is remark, so we'll use up all active threads
    uint active_workers = g1h->workers()->active_workers();
    if (active_workers == 0) {
      assert(active_workers > 0, "Should have been set earlier");
      active_workers = (uint) ParallelGCThreads;
      g1h->workers()->set_active_workers(active_workers);
    }
    set_phase(active_workers, false /* concurrent */);
    // Leave _parallel_marking_threads at it's
    // value originally calculated in the ConcurrentMark
    // constructor and pass values of the active workers
    // through the gang in the task.

    CMRemarkTask remarkTask(this, active_workers);
    g1h->set_par_threads(active_workers);
    g1h->workers()->run_task(&remarkTask);
    g1h->set_par_threads(0);
  } else {
    G1CollectedHeap::StrongRootsScope srs(g1h);
    // this is remark, so we'll use up all available threads
    uint active_workers = 1;
    set_phase(active_workers, false /* concurrent */);

    CMRemarkTask remarkTask(this, active_workers);
    // We will start all available threads, even if we decide that the
    // active_workers will be fewer. The extra ones will just bail out
    // immediately.
    remarkTask.work(0);
  }
  SATBMarkQueueSet& satb_mq_set = JavaThread::satb_mark_queue_set();
  guarantee(satb_mq_set.completed_buffers_num() == 0, "invariant");

  print_stats();

#if VERIFY_OBJS_PROCESSED
  if (_scan_obj_cl.objs_processed != ThreadLocalObjQueue::objs_enqueued) {
    gclog_or_tty->print_cr("Processed = %d, enqueued = %d.",
                           _scan_obj_cl.objs_processed,
                           ThreadLocalObjQueue::objs_enqueued);
    guarantee(_scan_obj_cl.objs_processed ==
              ThreadLocalObjQueue::objs_enqueued,
              "Different number of objs processed and enqueued.");
  }
#endif
}

#ifndef PRODUCT

class PrintReachableOopClosure: public OopClosure {
private:
  G1CollectedHeap* _g1h;
  outputStream*    _out;
  VerifyOption     _vo;
  bool             _all;

public:
  PrintReachableOopClosure(outputStream* out,
                           VerifyOption  vo,
                           bool          all) :
    _g1h(G1CollectedHeap::heap()),
    _out(out), _vo(vo), _all(all) { }

  void do_oop(narrowOop* p) { do_oop_work(p); }
  void do_oop(      oop* p) { do_oop_work(p); }

  template <class T> void do_oop_work(T* p) {
    oop         obj = oopDesc::load_decode_heap_oop(p);
    const char* str = NULL;
    const char* str2 = "";

    if (obj == NULL) {
      str = "";
    } else if (!_g1h->is_in_g1_reserved(obj)) {
      str = " O";
    } else {
      HeapRegion* hr  = _g1h->heap_region_containing(obj);
      guarantee(hr != NULL, "invariant");
      bool over_tams = false;
      bool marked = false;

      switch (_vo) {
        case VerifyOption_G1UsePrevMarking:
          over_tams = hr->obj_allocated_since_prev_marking(obj);
          marked = _g1h->isMarkedPrev(obj);
          break;
        case VerifyOption_G1UseNextMarking:
          over_tams = hr->obj_allocated_since_next_marking(obj);
          marked = _g1h->isMarkedNext(obj);
          break;
        case VerifyOption_G1UseMarkWord:
          marked = obj->is_gc_marked();
          break;
        default:
          ShouldNotReachHere();
      }

      if (over_tams) {
        str = " >";
        if (marked) {
          str2 = " AND MARKED";
        }
      } else if (marked) {
        str = " M";
      } else {
        str = " NOT";
      }
    }

    _out->print_cr("  "PTR_FORMAT": "PTR_FORMAT"%s%s",
                   p, (void*) obj, str, str2);
  }
};

class PrintReachableObjectClosure : public ObjectClosure {
private:
  G1CollectedHeap* _g1h;
  outputStream*    _out;
  VerifyOption     _vo;
  bool             _all;
  HeapRegion*      _hr;

public:
  PrintReachableObjectClosure(outputStream* out,
                              VerifyOption  vo,
                              bool          all,
                              HeapRegion*   hr) :
    _g1h(G1CollectedHeap::heap()),
    _out(out), _vo(vo), _all(all), _hr(hr) { }

  void do_object(oop o) {
    bool over_tams = false;
    bool marked = false;

    switch (_vo) {
      case VerifyOption_G1UsePrevMarking:
        over_tams = _hr->obj_allocated_since_prev_marking(o);
        marked = _g1h->isMarkedPrev(o);
        break;
      case VerifyOption_G1UseNextMarking:
        over_tams = _hr->obj_allocated_since_next_marking(o);
        marked = _g1h->isMarkedNext(o);
        break;
      case VerifyOption_G1UseMarkWord:
        marked = o->is_gc_marked();
        break;
      default:
        ShouldNotReachHere();
    }
    bool print_it = _all || over_tams || marked;

    if (print_it) {
      _out->print_cr(" "PTR_FORMAT"%s",
                     o, (over_tams) ? " >" : (marked) ? " M" : "");
      PrintReachableOopClosure oopCl(_out, _vo, _all);
      o->oop_iterate(&oopCl);
    }
  }
};

class PrintReachableRegionClosure : public HeapRegionClosure {
private:
  outputStream* _out;
  VerifyOption  _vo;
  bool          _all;

public:
  bool doHeapRegion(HeapRegion* hr) {
    HeapWord* b = hr->bottom();
    HeapWord* e = hr->end();
    HeapWord* t = hr->top();
    HeapWord* p = NULL;

    switch (_vo) {
      case VerifyOption_G1UsePrevMarking:
        p = hr->prev_top_at_mark_start();
        break;
      case VerifyOption_G1UseNextMarking:
        p = hr->next_top_at_mark_start();
        break;
      case VerifyOption_G1UseMarkWord:
        // When we are verifying marking using the mark word
        // TAMS has no relevance.
        assert(p == NULL, "post-condition");
        break;
      default:
        ShouldNotReachHere();
    }
    _out->print_cr("** ["PTR_FORMAT", "PTR_FORMAT"] top: "PTR_FORMAT" "
                   "TAMS: "PTR_FORMAT, b, e, t, p);
    _out->cr();

    HeapWord* from = b;
    HeapWord* to   = t;

    if (to > from) {
      _out->print_cr("Objects in ["PTR_FORMAT", "PTR_FORMAT"]", from, to);
      _out->cr();
      PrintReachableObjectClosure ocl(_out, _vo, _all, hr);
      hr->object_iterate_mem_careful(MemRegion(from, to), &ocl);
      _out->cr();
    }

    return false;
  }

  PrintReachableRegionClosure(outputStream* out,
                              VerifyOption  vo,
                              bool          all) :
    _out(out), _vo(vo), _all(all) { }
};

static const char* verify_option_to_tams(VerifyOption vo) {
  switch (vo) {
    case VerifyOption_G1UsePrevMarking:
      return "PTAMS";
    case VerifyOption_G1UseNextMarking:
      return "NTAMS";
    default:
      return "NONE";
  }
}

void ConcurrentMark::print_reachable(const char* str,
                                     VerifyOption vo,
                                     bool all) {
  gclog_or_tty->cr();
  gclog_or_tty->print_cr("== Doing heap dump... ");

  if (G1PrintReachableBaseFile == NULL) {
    gclog_or_tty->print_cr("  #### error: no base file defined");
    return;
  }

  if (strlen(G1PrintReachableBaseFile) + 1 + strlen(str) >
      (JVM_MAXPATHLEN - 1)) {
    gclog_or_tty->print_cr("  #### error: file name too long");
    return;
  }

  char file_name[JVM_MAXPATHLEN];
  sprintf(file_name, "%s.%s", G1PrintReachableBaseFile, str);
  gclog_or_tty->print_cr("  dumping to file %s", file_name);

  fileStream fout(file_name);
  if (!fout.is_open()) {
    gclog_or_tty->print_cr("  #### error: could not open file");
    return;
  }

  outputStream* out = &fout;
  out->print_cr("-- USING %s", verify_option_to_tams(vo));
  out->cr();

  out->print_cr("--- ITERATING OVER REGIONS");
  out->cr();
  PrintReachableRegionClosure rcl(out, vo, all);
  _g1h->heap_region_iterate(&rcl);
  out->cr();

  gclog_or_tty->print_cr("  done");
  gclog_or_tty->flush();
}

#endif // PRODUCT

// This note is for drainAllSATBBuffers and the code in between.
// In the future we could reuse a task to do this work during an
// evacuation pause (since now tasks are not active and can be claimed
// during an evacuation pause). This was a late change to the code and
// is currently not being taken advantage of.

class CMGlobalObjectClosure : public ObjectClosure {
private:
  ConcurrentMark* _cm;

public:
  void do_object(oop obj) {
    _cm->deal_with_reference(obj);
  }

  CMGlobalObjectClosure(ConcurrentMark* cm) : _cm(cm) { }
};

void ConcurrentMark::deal_with_reference(oop obj) {
  if (verbose_high()) {
    gclog_or_tty->print_cr("[global] we're dealing with reference "PTR_FORMAT,
                           (void*) obj);
  }

  HeapWord* objAddr = (HeapWord*) obj;
  assert(obj->is_oop_or_null(true /* ignore mark word */), "Error");
  if (_g1h->is_in_g1_reserved(objAddr)) {
    assert(obj != NULL, "null check is implicit");
    if (!_nextMarkBitMap->isMarked(objAddr)) {
      // Only get the containing region if the object is not marked on the
      // bitmap (otherwise, it's a waste of time since we won't do
      // anything with it).
      HeapRegion* hr = _g1h->heap_region_containing_raw(obj);
      if (!hr->obj_allocated_since_next_marking(obj)) {
        if (verbose_high()) {
          gclog_or_tty->print_cr("[global] "PTR_FORMAT" is not considered "
                                 "marked", (void*) obj);
        }

        // we need to mark it first
        if (_nextMarkBitMap->parMark(objAddr)) {
          // No OrderAccess:store_load() is needed. It is implicit in the
          // CAS done in parMark(objAddr) above
          HeapWord* finger = _finger;
          if (objAddr < finger) {
            if (verbose_high()) {
              gclog_or_tty->print_cr("[global] below the global finger "
                                     "("PTR_FORMAT"), pushing it", finger);
            }
            if (!mark_stack_push(obj)) {
              if (verbose_low()) {
                gclog_or_tty->print_cr("[global] global stack overflow during "
                                       "deal_with_reference");
              }
            }
          }
        }
      }
    }
  }
}

void ConcurrentMark::drainAllSATBBuffers() {
  guarantee(false, "drainAllSATBBuffers(): don't call this any more");

  CMGlobalObjectClosure oc(this);
  SATBMarkQueueSet& satb_mq_set = JavaThread::satb_mark_queue_set();
  satb_mq_set.set_closure(&oc);

  while (satb_mq_set.apply_closure_to_completed_buffer()) {
    if (verbose_medium()) {
      gclog_or_tty->print_cr("[global] processed an SATB buffer");
    }
  }

  // no need to check whether we should do this, as this is only
  // called during an evacuation pause
  satb_mq_set.iterate_closure_all_threads();

  satb_mq_set.set_closure(NULL);
  assert(satb_mq_set.completed_buffers_num() == 0, "invariant");
}

void ConcurrentMark::clear(oop p) {
  assert(p != NULL && p->is_oop(), "expected an oop");
  HeapWord* addr = (HeapWord*)p;
  assert(addr >= _nextMarkBitMap->startWord() ||
         addr < _nextMarkBitMap->endWord(), "in a region");

  _nextMarkBitMap->clear(addr);
}

void ConcurrentMark::clearRangePrevBitmap(MemRegion mr) {
  // Note we are overriding the read-only view of the prev map here, via
  // the cast.
  ((CMBitMap*)_prevMarkBitMap)->clearRange(mr);
}

void ConcurrentMark::clearRangeNextBitmap(MemRegion mr) {
  _nextMarkBitMap->clearRange(mr);
}

void ConcurrentMark::clearRangeBothBitmaps(MemRegion mr) {
  clearRangePrevBitmap(mr);
  clearRangeNextBitmap(mr);
}

HeapRegion*
ConcurrentMark::claim_region(int task_num) {
  // "checkpoint" the finger
  HeapWord* finger = _finger;

  // _heap_end will not change underneath our feet; it only changes at
  // yield points.
  while (finger < _heap_end) {
    assert(_g1h->is_in_g1_reserved(finger), "invariant");

    // Note on how this code handles humongous regions. In the
    // normal case the finger will reach the start of a "starts
    // humongous" (SH) region. Its end will either be the end of the
    // last "continues humongous" (CH) region in the sequence, or the
    // standard end of the SH region (if the SH is the only region in
    // the sequence). That way claim_region() will skip over the CH
    // regions. However, there is a subtle race between a CM thread
    // executing this method and a mutator thread doing a humongous
    // object allocation. The two are not mutually exclusive as the CM
    // thread does not need to hold the Heap_lock when it gets
    // here. So there is a chance that claim_region() will come across
    // a free region that's in the progress of becoming a SH or a CH
    // region. In the former case, it will either
    //   a) Miss the update to the region's end, in which case it will
    //      visit every subsequent CH region, will find their bitmaps
    //      empty, and do nothing, or
    //   b) Will observe the update of the region's end (in which case
    //      it will skip the subsequent CH regions).
    // If it comes across a region that suddenly becomes CH, the
    // scenario will be similar to b). So, the race between
    // claim_region() and a humongous object allocation might force us
    // to do a bit of unnecessary work (due to some unnecessary bitmap
    // iterations) but it should not introduce and correctness issues.
    HeapRegion* curr_region   = _g1h->heap_region_containing_raw(finger);
    HeapWord*   bottom        = curr_region->bottom();
    HeapWord*   end           = curr_region->end();
    HeapWord*   limit         = curr_region->next_top_at_mark_start();

    if (verbose_low()) {
      gclog_or_tty->print_cr("[%d] curr_region = "PTR_FORMAT" "
                             "["PTR_FORMAT", "PTR_FORMAT"), "
                             "limit = "PTR_FORMAT,
                             task_num, curr_region, bottom, end, limit);
    }

    // Is the gap between reading the finger and doing the CAS too long?
    HeapWord* res = (HeapWord*) Atomic::cmpxchg_ptr(end, &_finger, finger);
    if (res == finger) {
      // we succeeded

      // notice that _finger == end cannot be guaranteed here since,
      // someone else might have moved the finger even further
      assert(_finger >= end, "the finger should have moved forward");

      if (verbose_low()) {
        gclog_or_tty->print_cr("[%d] we were successful with region = "
                               PTR_FORMAT, task_num, curr_region);
      }

      if (limit > bottom) {
        if (verbose_low()) {
          gclog_or_tty->print_cr("[%d] region "PTR_FORMAT" is not empty, "
                                 "returning it ", task_num, curr_region);
        }
        return curr_region;
      } else {
        assert(limit == bottom,
               "the region limit should be at bottom");
        if (verbose_low()) {
          gclog_or_tty->print_cr("[%d] region "PTR_FORMAT" is empty, "
                                 "returning NULL", task_num, curr_region);
        }
        // we return NULL and the caller should try calling
        // claim_region() again.
        return NULL;
      }
    } else {
      assert(_finger > finger, "the finger should have moved forward");
      if (verbose_low()) {
        gclog_or_tty->print_cr("[%d] somebody else moved the finger, "
                               "global finger = "PTR_FORMAT", "
                               "our finger = "PTR_FORMAT,
                               task_num, _finger, finger);
      }

      // read it again
      finger = _finger;
    }
  }

  return NULL;
}

bool ConcurrentMark::invalidate_aborted_regions_in_cset() {
  guarantee(false, "invalidate_aborted_regions_in_cset(): "
                   "don't call this any more");

  bool result = false;
  for (int i = 0; i < (int)_max_task_num; ++i) {
    CMTask* the_task = _tasks[i];
    MemRegion mr = the_task->aborted_region();
    if (mr.start() != NULL) {
      assert(mr.end() != NULL, "invariant");
      assert(mr.word_size() > 0, "invariant");
      HeapRegion* hr = _g1h->heap_region_containing(mr.start());
      assert(hr != NULL, "invariant");
      if (hr->in_collection_set()) {
        // The region points into the collection set
        the_task->set_aborted_region(MemRegion());
        result = true;
      }
    }
  }
  return result;
}

bool ConcurrentMark::has_aborted_regions() {
  for (int i = 0; i < (int)_max_task_num; ++i) {
    CMTask* the_task = _tasks[i];
    MemRegion mr = the_task->aborted_region();
    if (mr.start() != NULL) {
      assert(mr.end() != NULL, "invariant");
      assert(mr.word_size() > 0, "invariant");
      return true;
    }
  }
  return false;
}

void ConcurrentMark::oops_do(OopClosure* cl) {
  if (_markStack.size() > 0 && verbose_low()) {
    gclog_or_tty->print_cr("[global] scanning the global marking stack, "
                           "size = %d", _markStack.size());
  }
  // we first iterate over the contents of the mark stack...
  _markStack.oops_do(cl);

  for (int i = 0; i < (int)_max_task_num; ++i) {
    OopTaskQueue* queue = _task_queues->queue((int)i);

    if (queue->size() > 0 && verbose_low()) {
      gclog_or_tty->print_cr("[global] scanning task queue of task %d, "
                             "size = %d", i, queue->size());
    }

    // ...then over the contents of the all the task queues.
    queue->oops_do(cl);
  }
}

#ifndef PRODUCT
enum VerifyNoCSetOopsPhase {
  VerifyNoCSetOopsStack,
  VerifyNoCSetOopsQueues,
  VerifyNoCSetOopsSATBCompleted,
  VerifyNoCSetOopsSATBThread
};

class VerifyNoCSetOopsClosure : public OopClosure, public ObjectClosure  {
private:
  G1CollectedHeap* _g1h;
  VerifyNoCSetOopsPhase _phase;
  int _info;

  const char* phase_str() {
    switch (_phase) {
    case VerifyNoCSetOopsStack:         return "Stack";
    case VerifyNoCSetOopsQueues:        return "Queue";
    case VerifyNoCSetOopsSATBCompleted: return "Completed SATB Buffers";
    case VerifyNoCSetOopsSATBThread:    return "Thread SATB Buffers";
    default:                            ShouldNotReachHere();
    }
    return NULL;
  }

  void do_object_work(oop obj) {
    guarantee(!_g1h->obj_in_cs(obj),
              err_msg("obj: "PTR_FORMAT" in CSet, phase: %s, info: %d",
                      (void*) obj, phase_str(), _info));
  }

public:
  VerifyNoCSetOopsClosure() : _g1h(G1CollectedHeap::heap()) { }

  void set_phase(VerifyNoCSetOopsPhase phase, int info = -1) {
    _phase = phase;
    _info = info;
  }

  virtual void do_oop(oop* p) {
    oop obj = oopDesc::load_decode_heap_oop(p);
    do_object_work(obj);
  }

  virtual void do_oop(narrowOop* p) {
    // We should not come across narrow oops while scanning marking
    // stacks and SATB buffers.
    ShouldNotReachHere();
  }

  virtual void do_object(oop obj) {
    do_object_work(obj);
  }
};

void ConcurrentMark::verify_no_cset_oops(bool verify_stacks,
                                         bool verify_enqueued_buffers,
                                         bool verify_thread_buffers,
                                         bool verify_fingers) {
  assert(SafepointSynchronize::is_at_safepoint(), "should be at a safepoint");
  if (!G1CollectedHeap::heap()->mark_in_progress()) {
    return;
  }

  VerifyNoCSetOopsClosure cl;

  if (verify_stacks) {
    // Verify entries on the global mark stack
    cl.set_phase(VerifyNoCSetOopsStack);
    _markStack.oops_do(&cl);

    // Verify entries on the task queues
    for (int i = 0; i < (int) _max_task_num; i += 1) {
      cl.set_phase(VerifyNoCSetOopsQueues, i);
      OopTaskQueue* queue = _task_queues->queue(i);
      queue->oops_do(&cl);
    }
  }

  SATBMarkQueueSet& satb_qs = JavaThread::satb_mark_queue_set();

  // Verify entries on the enqueued SATB buffers
  if (verify_enqueued_buffers) {
    cl.set_phase(VerifyNoCSetOopsSATBCompleted);
    satb_qs.iterate_completed_buffers_read_only(&cl);
  }

  // Verify entries on the per-thread SATB buffers
  if (verify_thread_buffers) {
    cl.set_phase(VerifyNoCSetOopsSATBThread);
    satb_qs.iterate_thread_buffers_read_only(&cl);
  }

  if (verify_fingers) {
    // Verify the global finger
    HeapWord* global_finger = finger();
    if (global_finger != NULL && global_finger < _heap_end) {
      // The global finger always points to a heap region boundary. We
      // use heap_region_containing_raw() to get the containing region
      // given that the global finger could be pointing to a free region
      // which subsequently becomes continues humongous. If that
      // happens, heap_region_containing() will return the bottom of the
      // corresponding starts humongous region and the check below will
      // not hold any more.
      HeapRegion* global_hr = _g1h->heap_region_containing_raw(global_finger);
      guarantee(global_finger == global_hr->bottom(),
                err_msg("global finger: "PTR_FORMAT" region: "HR_FORMAT,
                        global_finger, HR_FORMAT_PARAMS(global_hr)));
    }

    // Verify the task fingers
    assert(parallel_marking_threads() <= _max_task_num, "sanity");
    for (int i = 0; i < (int) parallel_marking_threads(); i += 1) {
      CMTask* task = _tasks[i];
      HeapWord* task_finger = task->finger();
      if (task_finger != NULL && task_finger < _heap_end) {
        // See above note on the global finger verification.
        HeapRegion* task_hr = _g1h->heap_region_containing_raw(task_finger);
        guarantee(task_finger == task_hr->bottom() ||
                  !task_hr->in_collection_set(),
                  err_msg("task finger: "PTR_FORMAT" region: "HR_FORMAT,
                          task_finger, HR_FORMAT_PARAMS(task_hr)));
      }
    }
  }
}
#endif // PRODUCT

void ConcurrentMark::clear_marking_state(bool clear_overflow) {
  _markStack.setEmpty();
  _markStack.clear_overflow();
  _regionStack.setEmpty();
  _regionStack.clear_overflow();
  if (clear_overflow) {
    clear_has_overflown();
  } else {
    assert(has_overflown(), "pre-condition");
  }
  _finger = _heap_start;

  for (int i = 0; i < (int)_max_task_num; ++i) {
    OopTaskQueue* queue = _task_queues->queue(i);
    queue->set_empty();
    // Clear any partial regions from the CMTasks
    _tasks[i]->clear_aborted_region();
  }
}

void ConcurrentMark::print_stats() {
  if (verbose_stats()) {
    gclog_or_tty->print_cr("---------------------------------------------------------------------");
    for (size_t i = 0; i < _active_tasks; ++i) {
      _tasks[i]->print_stats();
      gclog_or_tty->print_cr("---------------------------------------------------------------------");
    }
  }
}

// Closures used by ConcurrentMark::complete_marking_in_collection_set().

class CSetMarkOopClosure: public OopClosure {
  friend class CSetMarkBitMapClosure;

  G1CollectedHeap* _g1h;
  CMBitMap*        _bm;
  ConcurrentMark*  _cm;
  oop*             _ms;
  jint*            _array_ind_stack;
  int              _ms_size;
  int              _ms_ind;
  int              _array_increment;
  uint             _worker_id;

  bool push(oop obj, int arr_ind = 0) {
    if (_ms_ind == _ms_size) {
      gclog_or_tty->print_cr("Mark stack is full.");
      return false;
    }
    _ms[_ms_ind] = obj;
    if (obj->is_objArray()) {
      _array_ind_stack[_ms_ind] = arr_ind;
    }
    _ms_ind++;
    return true;
  }

  oop pop() {
    if (_ms_ind == 0) {
      return NULL;
    } else {
      _ms_ind--;
      return _ms[_ms_ind];
    }
  }

  template <class T> bool drain() {
    while (_ms_ind > 0) {
      oop obj = pop();
      assert(obj != NULL, "Since index was non-zero.");
      if (obj->is_objArray()) {
        jint arr_ind = _array_ind_stack[_ms_ind];
        objArrayOop aobj = objArrayOop(obj);
        jint len = aobj->length();
        jint next_arr_ind = arr_ind + _array_increment;
        if (next_arr_ind < len) {
          push(obj, next_arr_ind);
        }
        // Now process this portion of this one.
        int lim = MIN2(next_arr_ind, len);
        for (int j = arr_ind; j < lim; j++) {
          do_oop(aobj->objArrayOopDesc::obj_at_addr<T>(j));
        }
      } else {
        obj->oop_iterate(this);
      }
      if (abort()) return false;
    }
    return true;
  }

public:
  CSetMarkOopClosure(ConcurrentMark* cm, int ms_size, uint worker_id) :
    _g1h(G1CollectedHeap::heap()),
    _cm(cm),
    _bm(cm->nextMarkBitMap()),
    _ms_size(ms_size), _ms_ind(0),
    _ms(NEW_C_HEAP_ARRAY(oop, ms_size)),
    _array_ind_stack(NEW_C_HEAP_ARRAY(jint, ms_size)),
    _array_increment(MAX2(ms_size/8, 16)),
    _worker_id(worker_id) { }

  ~CSetMarkOopClosure() {
    FREE_C_HEAP_ARRAY(oop, _ms);
    FREE_C_HEAP_ARRAY(jint, _array_ind_stack);
  }

  virtual void do_oop(narrowOop* p) { do_oop_work(p); }
  virtual void do_oop(      oop* p) { do_oop_work(p); }

  template <class T> void do_oop_work(T* p) {
    T heap_oop = oopDesc::load_heap_oop(p);
    if (oopDesc::is_null(heap_oop)) return;
    oop obj = oopDesc::decode_heap_oop_not_null(heap_oop);
    if (obj->is_forwarded()) {
      // If the object has already been forwarded, we have to make sure
      // that it's marked.  So follow the forwarding pointer.  Note that
      // this does the right thing for self-forwarding pointers in the
      // evacuation failure case.
      obj = obj->forwardee();
    }
    HeapRegion* hr = _g1h->heap_region_containing(obj);
    if (hr != NULL) {
      if (hr->in_collection_set()) {
        if (_g1h->is_obj_ill(obj)) {
          if (_bm->parMark((HeapWord*)obj)) {
            if (!push(obj)) {
              gclog_or_tty->print_cr("Setting abort in CSetMarkOopClosure because push failed.");
              set_abort();
            }
          }
        }
      } else {
        // Outside the collection set; we need to gray it
        _cm->deal_with_reference(obj);
      }
    }
  }
};

class CSetMarkBitMapClosure: public BitMapClosure {
  G1CollectedHeap*   _g1h;
  CMBitMap*          _bitMap;
  ConcurrentMark*    _cm;
  CSetMarkOopClosure _oop_cl;
  uint               _worker_id;

public:
  CSetMarkBitMapClosure(ConcurrentMark* cm, int ms_size, int worker_id) :
    _g1h(G1CollectedHeap::heap()),
    _bitMap(cm->nextMarkBitMap()),
    _oop_cl(cm, ms_size, worker_id),
    _worker_id(worker_id) { }

  bool do_bit(size_t offset) {
    // convert offset into a HeapWord*
    HeapWord* addr = _bitMap->offsetToHeapWord(offset);
    assert(_bitMap->endWord() && addr < _bitMap->endWord(),
           "address out of range");
    assert(_bitMap->isMarked(addr), "tautology");
    oop obj = oop(addr);
    if (!obj->is_forwarded()) {
      if (!_oop_cl.push(obj)) return false;
      if (UseCompressedOops) {
        if (!_oop_cl.drain<narrowOop>()) return false;
      } else {
        if (!_oop_cl.drain<oop>()) return false;
      }
    }
    // Otherwise...
    return true;
  }
};

class CompleteMarkingInCSetHRClosure: public HeapRegionClosure {
  CMBitMap*             _bm;
  CSetMarkBitMapClosure _bit_cl;
  uint                  _worker_id;

  enum SomePrivateConstants {
    MSSize = 1000
  };

public:
  CompleteMarkingInCSetHRClosure(ConcurrentMark* cm, int worker_id) :
    _bm(cm->nextMarkBitMap()),
    _bit_cl(cm, MSSize, worker_id),
    _worker_id(worker_id) { }

  bool doHeapRegion(HeapRegion* hr) {
    if (hr->claimHeapRegion(HeapRegion::CompleteMarkCSetClaimValue)) {
      // The current worker has successfully claimed the region.
      if (!hr->evacuation_failed()) {
        MemRegion mr = MemRegion(hr->bottom(), hr->next_top_at_mark_start());
        if (!mr.is_empty()) {
          bool done = false;
          while (!done) {
            done = _bm->iterate(&_bit_cl, mr);
          }
        }
      }
    }
    return false;
  }
};

class G1ParCompleteMarkInCSetTask: public AbstractGangTask {
protected:
  G1CollectedHeap* _g1h;
  ConcurrentMark*  _cm;

public:
  G1ParCompleteMarkInCSetTask(G1CollectedHeap* g1h,
                              ConcurrentMark* cm) :
    AbstractGangTask("Complete Mark in CSet"),
    _g1h(g1h), _cm(cm) { }

  void work(uint worker_id) {
    CompleteMarkingInCSetHRClosure cmplt(_cm, worker_id);
    HeapRegion* hr = _g1h->start_cset_region_for_worker(worker_id);
    _g1h->collection_set_iterate_from(hr, &cmplt);
  }
};

void ConcurrentMark::complete_marking_in_collection_set() {
  guarantee(false, "complete_marking_in_collection_set(): "
                   "don't call this any more");

  G1CollectedHeap* g1h =  G1CollectedHeap::heap();

  if (!g1h->mark_in_progress()) {
    g1h->g1_policy()->record_mark_closure_time(0.0);
    return;
  }

  double start = os::elapsedTime();
  G1ParCompleteMarkInCSetTask complete_mark_task(g1h, this);

  assert(g1h->check_cset_heap_region_claim_values(HeapRegion::InitialClaimValue), "sanity");

  if (G1CollectedHeap::use_parallel_gc_threads()) {
    int n_workers = g1h->workers()->active_workers();
    g1h->set_par_threads(n_workers);
    g1h->workers()->run_task(&complete_mark_task);
    g1h->set_par_threads(0);
  } else {
    complete_mark_task.work(0);
  }

  assert(g1h->check_cset_heap_region_claim_values(HeapRegion::CompleteMarkCSetClaimValue), "sanity");

  // Reset the claim values in the regions in the collection set.
  g1h->reset_cset_heap_region_claim_values();

  assert(g1h->check_cset_heap_region_claim_values(HeapRegion::InitialClaimValue), "sanity");

  double end_time = os::elapsedTime();
  double elapsed_time_ms = (end_time - start) * 1000.0;
  g1h->g1_policy()->record_mark_closure_time(elapsed_time_ms);
}

// The next two methods deal with the following optimisation. Some
// objects are gray by being marked and located above the finger. If
// they are copied, during an evacuation pause, below the finger then
// the need to be pushed on the stack. The observation is that, if
// there are no regions in the collection set located above the
// finger, then the above cannot happen, hence we do not need to
// explicitly gray any objects when copying them to below the
// finger. The global stack will be scanned to ensure that, if it
// points to objects being copied, it will update their
// location. There is a tricky situation with the gray objects in
// region stack that are being coped, however. See the comment in
// newCSet().

void ConcurrentMark::newCSet() {
  guarantee(false, "newCSet(): don't call this any more");

  if (!concurrent_marking_in_progress()) {
    // nothing to do if marking is not in progress
    return;
  }

  // find what the lowest finger is among the global and local fingers
  _min_finger = _finger;
  for (int i = 0; i < (int)_max_task_num; ++i) {
    CMTask* task = _tasks[i];
    HeapWord* task_finger = task->finger();
    if (task_finger != NULL && task_finger < _min_finger) {
      _min_finger = task_finger;
    }
  }

  _should_gray_objects = false;

  // This fixes a very subtle and fustrating bug. It might be the case
  // that, during en evacuation pause, heap regions that contain
  // objects that are gray (by being in regions contained in the
  // region stack) are included in the collection set. Since such gray
  // objects will be moved, and because it's not easy to redirect
  // region stack entries to point to a new location (because objects
  // in one region might be scattered to multiple regions after they
  // are copied), one option is to ensure that all marked objects
  // copied during a pause are pushed on the stack. Notice, however,
  // that this problem can only happen when the region stack is not
  // empty during an evacuation pause. So, we make the fix a bit less
  // conservative and ensure that regions are pushed on the stack,
  // irrespective whether all collection set regions are below the
  // finger, if the region stack is not empty. This is expected to be
  // a rare case, so I don't think it's necessary to be smarted about it.
  if (!region_stack_empty() || has_aborted_regions()) {
    _should_gray_objects = true;
  }
}

void ConcurrentMark::registerCSetRegion(HeapRegion* hr) {
  guarantee(false, "registerCSetRegion(): don't call this any more");

  if (!concurrent_marking_in_progress()) return;

  HeapWord* region_end = hr->end();
  if (region_end > _min_finger) {
    _should_gray_objects = true;
  }
}

// Resets the region fields of active CMTasks whose values point
// into the collection set.
void ConcurrentMark::reset_active_task_region_fields_in_cset() {
  guarantee(false, "reset_active_task_region_fields_in_cset(): "
                   "don't call this any more");

  assert(SafepointSynchronize::is_at_safepoint(), "should be in STW");
  assert(parallel_marking_threads() <= _max_task_num, "sanity");

  for (int i = 0; i < (int)parallel_marking_threads(); i += 1) {
    CMTask* task = _tasks[i];
    HeapWord* task_finger = task->finger();
    if (task_finger != NULL) {
      assert(_g1h->is_in_g1_reserved(task_finger), "not in heap");
      HeapRegion* finger_region = _g1h->heap_region_containing(task_finger);
      if (finger_region->in_collection_set()) {
        // The task's current region is in the collection set.
        // This region will be evacuated in the current GC and
        // the region fields in the task will be stale.
        task->giveup_current_region();
      }
    }
  }
}

// abandon current marking iteration due to a Full GC
void ConcurrentMark::abort() {
  // Clear all marks to force marking thread to do nothing
  _nextMarkBitMap->clearAll();
  // Empty mark stack
  clear_marking_state();
  for (int i = 0; i < (int)_max_task_num; ++i) {
    _tasks[i]->clear_region_fields();
  }
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
  gclog_or_tty->print_cr("%s%5d %12s: total time = %8.2f s (avg = %8.2f ms).",
                         prefix, ns.num(), name, ns.sum()/1000.0, ns.avg());
  if (ns.num() > 0) {
    gclog_or_tty->print_cr("%s         [std. dev = %8.2f ms, max = %8.2f ms]",
                           prefix, ns.sd(), ns.maximum());
  }
}

void ConcurrentMark::print_summary_info() {
  gclog_or_tty->print_cr(" Concurrent marking:");
  print_ms_time_info("  ", "init marks", _init_times);
  print_ms_time_info("  ", "remarks", _remark_times);
  {
    print_ms_time_info("     ", "final marks", _remark_mark_times);
    print_ms_time_info("     ", "weak refs", _remark_weak_ref_times);

  }
  print_ms_time_info("  ", "cleanups", _cleanup_times);
  gclog_or_tty->print_cr("    Final counting total time = %8.2f s (avg = %8.2f ms).",
                         _total_counting_time,
                         (_cleanup_times.num() > 0 ? _total_counting_time * 1000.0 /
                          (double)_cleanup_times.num()
                         : 0.0));
  if (G1ScrubRemSets) {
    gclog_or_tty->print_cr("    RS scrub total time = %8.2f s (avg = %8.2f ms).",
                           _total_rs_scrub_time,
                           (_cleanup_times.num() > 0 ? _total_rs_scrub_time * 1000.0 /
                            (double)_cleanup_times.num()
                           : 0.0));
  }
  gclog_or_tty->print_cr("  Total stop_world time = %8.2f s.",
                         (_init_times.sum() + _remark_times.sum() +
                          _cleanup_times.sum())/1000.0);
  gclog_or_tty->print_cr("  Total concurrent time = %8.2f s "
                "(%8.2f s marking, %8.2f s counting).",
                cmThread()->vtime_accum(),
                cmThread()->vtime_mark_accum(),
                cmThread()->vtime_count_accum());
}

void ConcurrentMark::print_worker_threads_on(outputStream* st) const {
  _parallel_workers->print_worker_threads_on(st);
}

// Closures
// XXX: there seems to be a lot of code  duplication here;
// should refactor and consolidate the shared code.

// This closure is used to mark refs into the CMS generation in
// the CMS bit map. Called at the first checkpoint.

// We take a break if someone is trying to stop the world.
bool ConcurrentMark::do_yield_check(uint worker_id) {
  if (should_yield()) {
    if (worker_id == 0) {
      _g1h->g1_policy()->record_concurrent_pause();
    }
    cmThread()->yield();
    if (worker_id == 0) {
      _g1h->g1_policy()->record_concurrent_pause_end();
    }
    return true;
  } else {
    return false;
  }
}

bool ConcurrentMark::should_yield() {
  return cmThread()->should_yield();
}

bool ConcurrentMark::containing_card_is_marked(void* p) {
  size_t offset = pointer_delta(p, _g1h->reserved_region().start(), 1);
  return _card_bm.at(offset >> CardTableModRefBS::card_shift);
}

bool ConcurrentMark::containing_cards_are_marked(void* start,
                                                 void* last) {
  return containing_card_is_marked(start) &&
         containing_card_is_marked(last);
}

#ifndef PRODUCT
// for debugging purposes
void ConcurrentMark::print_finger() {
  gclog_or_tty->print_cr("heap ["PTR_FORMAT", "PTR_FORMAT"), global finger = "PTR_FORMAT,
                         _heap_start, _heap_end, _finger);
  for (int i = 0; i < (int) _max_task_num; ++i) {
    gclog_or_tty->print("   %d: "PTR_FORMAT, i, _tasks[i]->finger());
  }
  gclog_or_tty->print_cr("");
}
#endif

void CMTask::scan_object(oop obj) {
  assert(_nextMarkBitMap->isMarked((HeapWord*) obj), "invariant");

  if (_cm->verbose_high()) {
    gclog_or_tty->print_cr("[%d] we're scanning object "PTR_FORMAT,
                           _task_id, (void*) obj);
  }

  size_t obj_size = obj->size();
  _words_scanned += obj_size;

  obj->oop_iterate(_cm_oop_closure);
  statsOnly( ++_objs_scanned );
  check_limits();
}

// Closure for iteration over bitmaps
class CMBitMapClosure : public BitMapClosure {
private:
  // the bitmap that is being iterated over
  CMBitMap*                   _nextMarkBitMap;
  ConcurrentMark*             _cm;
  CMTask*                     _task;
  // true if we're scanning a heap region claimed by the task (so that
  // we move the finger along), false if we're not, i.e. currently when
  // scanning a heap region popped from the region stack (so that we
  // do not move the task finger along; it'd be a mistake if we did so).
  bool                        _scanning_heap_region;

public:
  CMBitMapClosure(CMTask *task,
                  ConcurrentMark* cm,
                  CMBitMap* nextMarkBitMap)
    :  _task(task), _cm(cm), _nextMarkBitMap(nextMarkBitMap) { }

  void set_scanning_heap_region(bool scanning_heap_region) {
    _scanning_heap_region = scanning_heap_region;
  }

  bool do_bit(size_t offset) {
    HeapWord* addr = _nextMarkBitMap->offsetToHeapWord(offset);
    assert(_nextMarkBitMap->isMarked(addr), "invariant");
    assert( addr < _cm->finger(), "invariant");

    if (_scanning_heap_region) {
      statsOnly( _task->increase_objs_found_on_bitmap() );
      assert(addr >= _task->finger(), "invariant");
      // We move that task's local finger along.
      _task->move_finger_to(addr);
    } else {
      // We move the task's region finger along.
      _task->move_region_finger_to(addr);
    }

    _task->scan_object(oop(addr));
    // we only partially drain the local queue and global stack
    _task->drain_local_queue(true);
    _task->drain_global_stack(true);

    // if the has_aborted flag has been raised, we need to bail out of
    // the iteration
    return !_task->has_aborted();
  }
};

// Closure for iterating over objects, currently only used for
// processing SATB buffers.
class CMObjectClosure : public ObjectClosure {
private:
  CMTask* _task;

public:
  void do_object(oop obj) {
    _task->deal_with_reference(obj);
  }

  CMObjectClosure(CMTask* task) : _task(task) { }
};

G1CMOopClosure::G1CMOopClosure(G1CollectedHeap* g1h,
                               ConcurrentMark* cm,
                               CMTask* task)
  : _g1h(g1h), _cm(cm), _task(task) {
  assert(_ref_processor == NULL, "should be initialized to NULL");

  if (G1UseConcMarkReferenceProcessing) {
    _ref_processor = g1h->ref_processor_cm();
    assert(_ref_processor != NULL, "should not be NULL");
  }
}

void CMTask::setup_for_region(HeapRegion* hr) {
  // Separated the asserts so that we know which one fires.
  assert(hr != NULL,
        "claim_region() should have filtered out continues humongous regions");
  assert(!hr->continuesHumongous(),
        "claim_region() should have filtered out continues humongous regions");

  if (_cm->verbose_low()) {
    gclog_or_tty->print_cr("[%d] setting up for region "PTR_FORMAT,
                           _task_id, hr);
  }

  _curr_region  = hr;
  _finger       = hr->bottom();
  update_region_limit();
}

void CMTask::update_region_limit() {
  HeapRegion* hr            = _curr_region;
  HeapWord* bottom          = hr->bottom();
  HeapWord* limit           = hr->next_top_at_mark_start();

  if (limit == bottom) {
    if (_cm->verbose_low()) {
      gclog_or_tty->print_cr("[%d] found an empty region "
                             "["PTR_FORMAT", "PTR_FORMAT")",
                             _task_id, bottom, limit);
    }
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

void CMTask::giveup_current_region() {
  assert(_curr_region != NULL, "invariant");
  if (_cm->verbose_low()) {
    gclog_or_tty->print_cr("[%d] giving up region "PTR_FORMAT,
                           _task_id, _curr_region);
  }
  clear_region_fields();
}

void CMTask::clear_region_fields() {
  // Values for these three fields that indicate that we're not
  // holding on to a region.
  _curr_region   = NULL;
  _finger        = NULL;
  _region_limit  = NULL;

  _region_finger = NULL;
}

void CMTask::set_cm_oop_closure(G1CMOopClosure* cm_oop_closure) {
  if (cm_oop_closure == NULL) {
    assert(_cm_oop_closure != NULL, "invariant");
  } else {
    assert(_cm_oop_closure == NULL, "invariant");
  }
  _cm_oop_closure = cm_oop_closure;
}

void CMTask::reset(CMBitMap* nextMarkBitMap) {
  guarantee(nextMarkBitMap != NULL, "invariant");

  if (_cm->verbose_low()) {
    gclog_or_tty->print_cr("[%d] resetting", _task_id);
  }

  _nextMarkBitMap                = nextMarkBitMap;
  clear_region_fields();
  assert(_aborted_region.is_empty(), "should have been cleared");

  _calls                         = 0;
  _elapsed_time_ms               = 0.0;
  _termination_time_ms           = 0.0;
  _termination_start_time_ms     = 0.0;

#if _MARKING_STATS_
  _local_pushes                  = 0;
  _local_pops                    = 0;
  _local_max_size                = 0;
  _objs_scanned                  = 0;
  _global_pushes                 = 0;
  _global_pops                   = 0;
  _global_max_size               = 0;
  _global_transfers_to           = 0;
  _global_transfers_from         = 0;
  _region_stack_pops             = 0;
  _regions_claimed               = 0;
  _objs_found_on_bitmap          = 0;
  _satb_buffers_processed        = 0;
  _steal_attempts                = 0;
  _steals                        = 0;
  _aborted                       = 0;
  _aborted_overflow              = 0;
  _aborted_cm_aborted            = 0;
  _aborted_yield                 = 0;
  _aborted_timed_out             = 0;
  _aborted_satb                  = 0;
  _aborted_termination           = 0;
#endif // _MARKING_STATS_
}

bool CMTask::should_exit_termination() {
  regular_clock_call();
  // This is called when we are in the termination protocol. We should
  // quit if, for some reason, this task wants to abort or the global
  // stack is not empty (this means that we can get work from it).
  return !_cm->mark_stack_empty() || has_aborted();
}

void CMTask::reached_limit() {
  assert(_words_scanned >= _words_scanned_limit ||
         _refs_reached >= _refs_reached_limit ,
         "shouldn't have been called otherwise");
  regular_clock_call();
}

void CMTask::regular_clock_call() {
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
    statsOnly( ++_aborted_cm_aborted );
    return;
  }

  double curr_time_ms = os::elapsedVTime() * 1000.0;

  // (3) If marking stats are enabled, then we update the step history.
#if _MARKING_STATS_
  if (_words_scanned >= _words_scanned_limit) {
    ++_clock_due_to_scanning;
  }
  if (_refs_reached >= _refs_reached_limit) {
    ++_clock_due_to_marking;
  }

  double last_interval_ms = curr_time_ms - _interval_start_time_ms;
  _interval_start_time_ms = curr_time_ms;
  _all_clock_intervals_ms.add(last_interval_ms);

  if (_cm->verbose_medium()) {
      gclog_or_tty->print_cr("[%d] regular clock, interval = %1.2lfms, "
                        "scanned = %d%s, refs reached = %d%s",
                        _task_id, last_interval_ms,
                        _words_scanned,
                        (_words_scanned >= _words_scanned_limit) ? " (*)" : "",
                        _refs_reached,
                        (_refs_reached >= _refs_reached_limit) ? " (*)" : "");
  }
#endif // _MARKING_STATS_

  // (4) We check whether we should yield. If we have to, then we abort.
  if (_cm->should_yield()) {
    // We should yield. To do this we abort the task. The caller is
    // responsible for yielding.
    set_has_aborted();
    statsOnly( ++_aborted_yield );
    return;
  }

  // (5) We check whether we've reached our time quota. If we have,
  // then we abort.
  double elapsed_time_ms = curr_time_ms - _start_time_ms;
  if (elapsed_time_ms > _time_target_ms) {
    set_has_aborted();
    _has_timed_out = true;
    statsOnly( ++_aborted_timed_out );
    return;
  }

  // (6) Finally, we check whether there are enough completed STAB
  // buffers available for processing. If there are, we abort.
  SATBMarkQueueSet& satb_mq_set = JavaThread::satb_mark_queue_set();
  if (!_draining_satb_buffers && satb_mq_set.process_completed_buffers()) {
    if (_cm->verbose_low()) {
      gclog_or_tty->print_cr("[%d] aborting to deal with pending SATB buffers",
                             _task_id);
    }
    // we do need to process SATB buffers, we'll abort and restart
    // the marking task to do so
    set_has_aborted();
    statsOnly( ++_aborted_satb );
    return;
  }
}

void CMTask::recalculate_limits() {
  _real_words_scanned_limit = _words_scanned + words_scanned_period;
  _words_scanned_limit      = _real_words_scanned_limit;

  _real_refs_reached_limit  = _refs_reached  + refs_reached_period;
  _refs_reached_limit       = _real_refs_reached_limit;
}

void CMTask::decrease_limits() {
  // This is called when we believe that we're going to do an infrequent
  // operation which will increase the per byte scanned cost (i.e. move
  // entries to/from the global stack). It basically tries to decrease the
  // scanning limit so that the clock is called earlier.

  if (_cm->verbose_medium()) {
    gclog_or_tty->print_cr("[%d] decreasing limits", _task_id);
  }

  _words_scanned_limit = _real_words_scanned_limit -
    3 * words_scanned_period / 4;
  _refs_reached_limit  = _real_refs_reached_limit -
    3 * refs_reached_period / 4;
}

void CMTask::move_entries_to_global_stack() {
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

    statsOnly( ++_global_transfers_to; _local_pops += n );

    if (!_cm->mark_stack_push(buffer, n)) {
      if (_cm->verbose_low()) {
        gclog_or_tty->print_cr("[%d] aborting due to global stack overflow",
                               _task_id);
      }
      set_has_aborted();
    } else {
      // the transfer was successful

      if (_cm->verbose_medium()) {
        gclog_or_tty->print_cr("[%d] pushed %d entries to the global stack",
                               _task_id, n);
      }
      statsOnly( int tmp_size = _cm->mark_stack_size();
                 if (tmp_size > _global_max_size) {
                   _global_max_size = tmp_size;
                 }
                 _global_pushes += n );
    }
  }

  // this operation was quite expensive, so decrease the limits
  decrease_limits();
}

void CMTask::get_entries_from_global_stack() {
  // local array where we'll store the entries that will be popped
  // from the global stack.
  oop buffer[global_stack_transfer_size];
  int n;
  _cm->mark_stack_pop(buffer, global_stack_transfer_size, &n);
  assert(n <= global_stack_transfer_size,
         "we should not pop more than the given limit");
  if (n > 0) {
    // yes, we did actually pop at least one entry

    statsOnly( ++_global_transfers_from; _global_pops += n );
    if (_cm->verbose_medium()) {
      gclog_or_tty->print_cr("[%d] popped %d entries from the global stack",
                             _task_id, n);
    }
    for (int i = 0; i < n; ++i) {
      bool success = _task_queue->push(buffer[i]);
      // We only call this when the local queue is empty or under a
      // given target limit. So, we do not expect this push to fail.
      assert(success, "invariant");
    }

    statsOnly( int tmp_size = _task_queue->size();
               if (tmp_size > _local_max_size) {
                 _local_max_size = tmp_size;
               }
               _local_pushes += n );
  }

  // this operation was quite expensive, so decrease the limits
  decrease_limits();
}

void CMTask::drain_local_queue(bool partially) {
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
    if (_cm->verbose_high()) {
      gclog_or_tty->print_cr("[%d] draining local queue, target size = %d",
                             _task_id, target_size);
    }

    oop obj;
    bool ret = _task_queue->pop_local(obj);
    while (ret) {
      statsOnly( ++_local_pops );

      if (_cm->verbose_high()) {
        gclog_or_tty->print_cr("[%d] popped "PTR_FORMAT, _task_id,
                               (void*) obj);
      }

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

    if (_cm->verbose_high()) {
      gclog_or_tty->print_cr("[%d] drained local queue, size = %d",
                             _task_id, _task_queue->size());
    }
  }
}

void CMTask::drain_global_stack(bool partially) {
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
    if (_cm->verbose_low()) {
      gclog_or_tty->print_cr("[%d] draining global_stack, target size %d",
                             _task_id, target_size);
    }

    while (!has_aborted() && _cm->mark_stack_size() > target_size) {
      get_entries_from_global_stack();
      drain_local_queue(partially);
    }

    if (_cm->verbose_low()) {
      gclog_or_tty->print_cr("[%d] drained global stack, size = %d",
                             _task_id, _cm->mark_stack_size());
    }
  }
}

// SATB Queue has several assumptions on whether to call the par or
// non-par versions of the methods. this is why some of the code is
// replicated. We should really get rid of the single-threaded version
// of the code to simplify things.
void CMTask::drain_satb_buffers() {
  if (has_aborted()) return;

  // We set this so that the regular clock knows that we're in the
  // middle of draining buffers and doesn't set the abort flag when it
  // notices that SATB buffers are available for draining. It'd be
  // very counter productive if it did that. :-)
  _draining_satb_buffers = true;

  CMObjectClosure oc(this);
  SATBMarkQueueSet& satb_mq_set = JavaThread::satb_mark_queue_set();
  if (G1CollectedHeap::use_parallel_gc_threads()) {
    satb_mq_set.set_par_closure(_task_id, &oc);
  } else {
    satb_mq_set.set_closure(&oc);
  }

  // This keeps claiming and applying the closure to completed buffers
  // until we run out of buffers or we need to abort.
  if (G1CollectedHeap::use_parallel_gc_threads()) {
    while (!has_aborted() &&
           satb_mq_set.par_apply_closure_to_completed_buffer(_task_id)) {
      if (_cm->verbose_medium()) {
        gclog_or_tty->print_cr("[%d] processed an SATB buffer", _task_id);
      }
      statsOnly( ++_satb_buffers_processed );
      regular_clock_call();
    }
  } else {
    while (!has_aborted() &&
           satb_mq_set.apply_closure_to_completed_buffer()) {
      if (_cm->verbose_medium()) {
        gclog_or_tty->print_cr("[%d] processed an SATB buffer", _task_id);
      }
      statsOnly( ++_satb_buffers_processed );
      regular_clock_call();
    }
  }

  if (!concurrent() && !has_aborted()) {
    // We should only do this during remark.
    if (G1CollectedHeap::use_parallel_gc_threads()) {
      satb_mq_set.par_iterate_closure_all_threads(_task_id);
    } else {
      satb_mq_set.iterate_closure_all_threads();
    }
  }

  _draining_satb_buffers = false;

  assert(has_aborted() ||
         concurrent() ||
         satb_mq_set.completed_buffers_num() == 0, "invariant");

  if (G1CollectedHeap::use_parallel_gc_threads()) {
    satb_mq_set.set_par_closure(_task_id, NULL);
  } else {
    satb_mq_set.set_closure(NULL);
  }

  // again, this was a potentially expensive operation, decrease the
  // limits to get the regular clock call early
  decrease_limits();
}

void CMTask::drain_region_stack(BitMapClosure* bc) {
  assert(_cm->region_stack_empty(), "region stack should be empty");
  assert(_aborted_region.is_empty(), "aborted region should be empty");
  return;

  if (has_aborted()) return;

  assert(_region_finger == NULL,
         "it should be NULL when we're not scanning a region");

  if (!_cm->region_stack_empty() || !_aborted_region.is_empty()) {
    if (_cm->verbose_low()) {
      gclog_or_tty->print_cr("[%d] draining region stack, size = %d",
                             _task_id, _cm->region_stack_size());
    }

    MemRegion mr;

    if (!_aborted_region.is_empty()) {
      mr = _aborted_region;
      _aborted_region = MemRegion();

      if (_cm->verbose_low()) {
        gclog_or_tty->print_cr("[%d] scanning aborted region "
                               "[ " PTR_FORMAT ", " PTR_FORMAT " )",
                               _task_id, mr.start(), mr.end());
      }
    } else {
      mr = _cm->region_stack_pop_lock_free();
      // it returns MemRegion() if the pop fails
      statsOnly(if (mr.start() != NULL) ++_region_stack_pops );
    }

    while (mr.start() != NULL) {
      if (_cm->verbose_medium()) {
        gclog_or_tty->print_cr("[%d] we are scanning region "
                               "["PTR_FORMAT", "PTR_FORMAT")",
                               _task_id, mr.start(), mr.end());
      }

      assert(mr.end() <= _cm->finger(),
             "otherwise the region shouldn't be on the stack");
      assert(!mr.is_empty(), "Only non-empty regions live on the region stack");
      if (_nextMarkBitMap->iterate(bc, mr)) {
        assert(!has_aborted(),
               "cannot abort the task without aborting the bitmap iteration");

        // We finished iterating over the region without aborting.
        regular_clock_call();
        if (has_aborted()) {
          mr = MemRegion();
        } else {
          mr = _cm->region_stack_pop_lock_free();
          // it returns MemRegion() if the pop fails
          statsOnly(if (mr.start() != NULL) ++_region_stack_pops );
        }
      } else {
        assert(has_aborted(), "currently the only way to do so");

        // The only way to abort the bitmap iteration is to return
        // false from the do_bit() method. However, inside the
        // do_bit() method we move the _region_finger to point to the
        // object currently being looked at. So, if we bail out, we
        // have definitely set _region_finger to something non-null.
        assert(_region_finger != NULL, "invariant");

        // Make sure that any previously aborted region has been
        // cleared.
        assert(_aborted_region.is_empty(), "aborted region not cleared");

        // The iteration was actually aborted. So now _region_finger
        // points to the address of the object we last scanned. If we
        // leave it there, when we restart this task, we will rescan
        // the object. It is easy to avoid this. We move the finger by
        // enough to point to the next possible object header (the
        // bitmap knows by how much we need to move it as it knows its
        // granularity).
        MemRegion newRegion =
          MemRegion(_nextMarkBitMap->nextWord(_region_finger), mr.end());

        if (!newRegion.is_empty()) {
          if (_cm->verbose_low()) {
            gclog_or_tty->print_cr("[%d] recording unscanned region"
                                   "[" PTR_FORMAT "," PTR_FORMAT ") in CMTask",
                                   _task_id,
                                   newRegion.start(), newRegion.end());
          }
          // Now record the part of the region we didn't scan to
          // make sure this task scans it later.
          _aborted_region = newRegion;
        }
        // break from while
        mr = MemRegion();
      }
      _region_finger = NULL;
    }

    if (_cm->verbose_low()) {
      gclog_or_tty->print_cr("[%d] drained region stack, size = %d",
                             _task_id, _cm->region_stack_size());
    }
  }
}

void CMTask::print_stats() {
  gclog_or_tty->print_cr("Marking Stats, task = %d, calls = %d",
                         _task_id, _calls);
  gclog_or_tty->print_cr("  Elapsed time = %1.2lfms, Termination time = %1.2lfms",
                         _elapsed_time_ms, _termination_time_ms);
  gclog_or_tty->print_cr("  Step Times (cum): num = %d, avg = %1.2lfms, sd = %1.2lfms",
                         _step_times_ms.num(), _step_times_ms.avg(),
                         _step_times_ms.sd());
  gclog_or_tty->print_cr("                    max = %1.2lfms, total = %1.2lfms",
                         _step_times_ms.maximum(), _step_times_ms.sum());

#if _MARKING_STATS_
  gclog_or_tty->print_cr("  Clock Intervals (cum): num = %d, avg = %1.2lfms, sd = %1.2lfms",
                         _all_clock_intervals_ms.num(), _all_clock_intervals_ms.avg(),
                         _all_clock_intervals_ms.sd());
  gclog_or_tty->print_cr("                         max = %1.2lfms, total = %1.2lfms",
                         _all_clock_intervals_ms.maximum(),
                         _all_clock_intervals_ms.sum());
  gclog_or_tty->print_cr("  Clock Causes (cum): scanning = %d, marking = %d",
                         _clock_due_to_scanning, _clock_due_to_marking);
  gclog_or_tty->print_cr("  Objects: scanned = %d, found on the bitmap = %d",
                         _objs_scanned, _objs_found_on_bitmap);
  gclog_or_tty->print_cr("  Local Queue:  pushes = %d, pops = %d, max size = %d",
                         _local_pushes, _local_pops, _local_max_size);
  gclog_or_tty->print_cr("  Global Stack: pushes = %d, pops = %d, max size = %d",
                         _global_pushes, _global_pops, _global_max_size);
  gclog_or_tty->print_cr("                transfers to = %d, transfers from = %d",
                         _global_transfers_to,_global_transfers_from);
  gclog_or_tty->print_cr("  Regions: claimed = %d, Region Stack: pops = %d",
                         _regions_claimed, _region_stack_pops);
  gclog_or_tty->print_cr("  SATB buffers: processed = %d", _satb_buffers_processed);
  gclog_or_tty->print_cr("  Steals: attempts = %d, successes = %d",
                         _steal_attempts, _steals);
  gclog_or_tty->print_cr("  Aborted: %d, due to", _aborted);
  gclog_or_tty->print_cr("    overflow: %d, global abort: %d, yield: %d",
                         _aborted_overflow, _aborted_cm_aborted, _aborted_yield);
  gclog_or_tty->print_cr("    time out: %d, SATB: %d, termination: %d",
                         _aborted_timed_out, _aborted_satb, _aborted_termination);
#endif // _MARKING_STATS_
}

/*****************************************************************************

    The do_marking_step(time_target_ms) method is the building block
    of the parallel marking framework. It can be called in parallel
    with other invocations of do_marking_step() on different tasks
    (but only one per task, obviously) and concurrently with the
    mutator threads, or during remark, hence it eliminates the need
    for two versions of the code. When called during remark, it will
    pick up from where the task left off during the concurrent marking
    phase. Interestingly, tasks are also claimable during evacuation
    pauses too, since do_marking_step() ensures that it aborts before
    it needs to yield.

    The data structures that is uses to do marking work are the
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

      (4) Global Region Stack. Entries on it correspond to areas of
      the bitmap that need to be scanned since they contain gray
      objects. Pushes on the region stack only happen during
      evacuation pauses and typically correspond to areas covered by
      GC LABS. If it overflows, then the marking phase should restart
      and iterate over the bitmap to identify gray objects. Tasks will
      try to totally drain the region stack as soon as possible.

      (5) SATB Buffer Queue. This is where completed SATB buffers are
      made available. Buffers are regularly removed from this queue
      and scanned for roots, so that the queue doesn't get too
      long. During remark, all completed buffers are processed, as
      well as the filled in parts of any uncompleted buffers.

    The do_marking_step() method tries to abort when the time target
    has been reached. There are a few other cases when the
    do_marking_step() method also aborts:

      (1) When the marking phase has been aborted (after a Full GC).

      (2) When a global overflow (either on the global stack or the
      region stack) has been triggered. Before the task aborts, it
      will actually sync up with the other tasks to ensure that all
      the marking data structures (local queues, stacks, fingers etc.)
      are re-initialised so that when do_marking_step() completes,
      the marking phase can immediately restart.

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

 *****************************************************************************/

void CMTask::do_marking_step(double time_target_ms,
                             bool do_stealing,
                             bool do_termination) {
  assert(time_target_ms >= 1.0, "minimum granularity is 1ms");
  assert(concurrent() == _cm->concurrent(), "they should be the same");

  assert(concurrent() || _cm->region_stack_empty(),
         "the region stack should have been cleared before remark");
  assert(concurrent() || !_cm->has_aborted_regions(),
         "aborted regions should have been cleared before remark");
  assert(_region_finger == NULL,
         "this should be non-null only when a region is being scanned");

  G1CollectorPolicy* g1_policy = _g1h->g1_policy();
  assert(_task_queues != NULL, "invariant");
  assert(_task_queue != NULL, "invariant");
  assert(_task_queues->queue(_task_id) == _task_queue, "invariant");

  assert(!_claimed,
         "only one thread should claim this task at any one time");

  // OK, this doesn't safeguard again all possible scenarios, as it is
  // possible for two threads to set the _claimed flag at the same
  // time. But it is only for debugging purposes anyway and it will
  // catch most problems.
  _claimed = true;

  _start_time_ms = os::elapsedVTime() * 1000.0;
  statsOnly( _interval_start_time_ms = _start_time_ms );

  double diff_prediction_ms =
    g1_policy->get_new_prediction(&_marking_step_diffs_ms);
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

  if (_cm->verbose_low()) {
    gclog_or_tty->print_cr("[%d] >>>>>>>>>> START, call = %d, "
                           "target = %1.2lfms >>>>>>>>>>",
                           _task_id, _calls, _time_target_ms);
  }

  // Set up the bitmap and oop closures. Anything that uses them is
  // eventually called from this method, so it is OK to allocate these
  // statically.
  CMBitMapClosure bitmap_closure(this, _cm, _nextMarkBitMap);
  G1CMOopClosure  cm_oop_closure(_g1h, _cm, this);
  set_cm_oop_closure(&cm_oop_closure);

  if (_cm->has_overflown()) {
    // This can happen if the region stack or the mark stack overflows
    // during a GC pause and this task, after a yield point,
    // restarts. We have to abort as we need to get into the overflow
    // protocol which happens right at the end of this task.
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

  // Then totally drain the region stack.  We will not look at
  // it again before the next invocation of this method. Entries on
  // the region stack are only added during evacuation pauses, for
  // which we have to yield. When we do, we abort the task anyway so
  // it will look at the region stack again when it restarts.
  bitmap_closure.set_scanning_heap_region(false);
  drain_region_stack(&bitmap_closure);
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

      if (_cm->verbose_low()) {
        gclog_or_tty->print_cr("[%d] we're scanning part "
                               "["PTR_FORMAT", "PTR_FORMAT") "
                               "of region "PTR_FORMAT,
                               _task_id, _finger, _region_limit, _curr_region);
      }

      // Let's iterate over the bitmap of the part of the
      // region that is left.
      bitmap_closure.set_scanning_heap_region(true);
      if (mr.is_empty() ||
          _nextMarkBitMap->iterate(&bitmap_closure, mr)) {
        // We successfully completed iterating over the region. Now,
        // let's give up the region.
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
        HeapWord* new_finger = _nextMarkBitMap->nextWord(_finger);
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
      if (_cm->verbose_low()) {
        gclog_or_tty->print_cr("[%d] trying to claim a new region", _task_id);
      }
      HeapRegion* claimed_region = _cm->claim_region(_task_id);
      if (claimed_region != NULL) {
        // Yes, we managed to claim one
        statsOnly( ++_regions_claimed );

        if (_cm->verbose_low()) {
          gclog_or_tty->print_cr("[%d] we successfully claimed "
                                 "region "PTR_FORMAT,
                                 _task_id, claimed_region);
        }

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
    // tasks might be pushing objects to it concurrently. We also cannot
    // check if the region stack is empty because if a thread is aborting
    // it can push a partially done region back.
    assert(_cm->out_of_regions(),
           "at this point we should be out of regions");

    if (_cm->verbose_low()) {
      gclog_or_tty->print_cr("[%d] all regions claimed", _task_id);
    }

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
    // tasks might be pushing objects to it concurrently. We also cannot
    // check if the region stack is empty because if a thread is aborting
    // it can push a partially done region back.
    assert(_cm->out_of_regions() && _task_queue->size() == 0,
           "only way to reach here");

    if (_cm->verbose_low()) {
      gclog_or_tty->print_cr("[%d] starting to steal", _task_id);
    }

    while (!has_aborted()) {
      oop obj;
      statsOnly( ++_steal_attempts );

      if (_cm->try_stealing(_task_id, &_hash_seed, obj)) {
        if (_cm->verbose_medium()) {
          gclog_or_tty->print_cr("[%d] stolen "PTR_FORMAT" successfully",
                                 _task_id, (void*) obj);
        }

        statsOnly( ++_steals );

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

  // If we are about to wrap up and go into termination, check if we
  // should raise the overflow flag.
  if (do_termination && !has_aborted()) {
    if (_cm->force_overflow()->should_force()) {
      _cm->set_has_overflown();
      regular_clock_call();
    }
  }

  // We still haven't aborted. Now, let's try to get into the
  // termination protocol.
  if (do_termination && !has_aborted()) {
    // We cannot check whether the global stack is empty, since other
    // tasks might be concurrently pushing objects on it. We also cannot
    // check if the region stack is empty because if a thread is aborting
    // it can push a partially done region back.
    // Separated the asserts so that we know which one fires.
    assert(_cm->out_of_regions(), "only way to reach here");
    assert(_task_queue->size() == 0, "only way to reach here");

    if (_cm->verbose_low()) {
      gclog_or_tty->print_cr("[%d] starting termination protocol", _task_id);
    }

    _termination_start_time_ms = os::elapsedVTime() * 1000.0;
    // The CMTask class also extends the TerminatorTerminator class,
    // hence its should_exit_termination() method will also decide
    // whether to exit the termination protocol or not.
    bool finished = _cm->terminator()->offer_termination(this);
    double termination_end_time_ms = os::elapsedVTime() * 1000.0;
    _termination_time_ms +=
      termination_end_time_ms - _termination_start_time_ms;

    if (finished) {
      // We're all done.

      if (_task_id == 0) {
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
      guarantee(_aborted_region.is_empty(), "only way to reach here");
      guarantee(_cm->region_stack_empty(), "only way to reach here");
      guarantee(_cm->mark_stack_empty(), "only way to reach here");
      guarantee(_task_queue->size() == 0, "only way to reach here");
      guarantee(!_cm->has_overflown(), "only way to reach here");
      guarantee(!_cm->mark_stack_overflow(), "only way to reach here");
      guarantee(!_cm->region_stack_overflow(), "only way to reach here");

      if (_cm->verbose_low()) {
        gclog_or_tty->print_cr("[%d] all tasks terminated", _task_id);
      }
    } else {
      // Apparently there's more work to do. Let's abort this task. It
      // will restart it and we can hopefully find more things to do.

      if (_cm->verbose_low()) {
        gclog_or_tty->print_cr("[%d] apparently there is more work to do",
                               _task_id);
      }

      set_has_aborted();
      statsOnly( ++_aborted_termination );
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

    statsOnly( ++_aborted );

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
      // what they are doing and re-initialise in a safe manner. We
      // will achieve this with the use of two barrier sync points.

      if (_cm->verbose_low()) {
        gclog_or_tty->print_cr("[%d] detected overflow", _task_id);
      }

      _cm->enter_first_sync_barrier(_task_id);
      // When we exit this sync barrier we know that all tasks have
      // stopped doing marking work. So, it's now safe to
      // re-initialise our data structures. At the end of this method,
      // task 0 will clear the global data structures.

      statsOnly( ++_aborted_overflow );

      // We clear the local state of this task...
      clear_region_fields();

      // ...and enter the second barrier.
      _cm->enter_second_sync_barrier(_task_id);
      // At this point everything has bee re-initialised and we're
      // ready to restart.
    }

    if (_cm->verbose_low()) {
      gclog_or_tty->print_cr("[%d] <<<<<<<<<< ABORTING, target = %1.2lfms, "
                             "elapsed = %1.2lfms <<<<<<<<<<",
                             _task_id, _time_target_ms, elapsed_time_ms);
      if (_cm->has_aborted()) {
        gclog_or_tty->print_cr("[%d] ========== MARKING ABORTED ==========",
                               _task_id);
      }
    }
  } else {
    if (_cm->verbose_low()) {
      gclog_or_tty->print_cr("[%d] <<<<<<<<<< FINISHED, target = %1.2lfms, "
                             "elapsed = %1.2lfms <<<<<<<<<<",
                             _task_id, _time_target_ms, elapsed_time_ms);
    }
  }

  _claimed = false;
}

CMTask::CMTask(int task_id,
               ConcurrentMark* cm,
               CMTaskQueue* task_queue,
               CMTaskQueueSet* task_queues)
  : _g1h(G1CollectedHeap::heap()),
    _task_id(task_id), _cm(cm),
    _claimed(false),
    _nextMarkBitMap(NULL), _hash_seed(17),
    _task_queue(task_queue),
    _task_queues(task_queues),
    _cm_oop_closure(NULL),
    _aborted_region(MemRegion()) {
  guarantee(task_queue != NULL, "invariant");
  guarantee(task_queues != NULL, "invariant");

  statsOnly( _clock_due_to_scanning = 0;
             _clock_due_to_marking  = 0 );

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

#define G1PPRL_ADDR_BASE_FORMAT    " "PTR_FORMAT"-"PTR_FORMAT
#ifdef _LP64
#define G1PPRL_ADDR_BASE_H_FORMAT  " %37s"
#else // _LP64
#define G1PPRL_ADDR_BASE_H_FORMAT  " %21s"
#endif // _LP64

// For per-region info
#define G1PPRL_TYPE_FORMAT            "   %-4s"
#define G1PPRL_TYPE_H_FORMAT          "   %4s"
#define G1PPRL_BYTE_FORMAT            "  "SIZE_FORMAT_W(9)
#define G1PPRL_BYTE_H_FORMAT          "  %9s"
#define G1PPRL_DOUBLE_FORMAT          "  %14.1f"
#define G1PPRL_DOUBLE_H_FORMAT        "  %14s"

// For summary info
#define G1PPRL_SUM_ADDR_FORMAT(tag)    "  "tag":"G1PPRL_ADDR_BASE_FORMAT
#define G1PPRL_SUM_BYTE_FORMAT(tag)    "  "tag": "SIZE_FORMAT
#define G1PPRL_SUM_MB_FORMAT(tag)      "  "tag": %1.2f MB"
#define G1PPRL_SUM_MB_PERC_FORMAT(tag) G1PPRL_SUM_MB_FORMAT(tag)" / %1.2f %%"

G1PrintRegionLivenessInfoClosure::
G1PrintRegionLivenessInfoClosure(outputStream* out, const char* phase_name)
  : _out(out),
    _total_used_bytes(0), _total_capacity_bytes(0),
    _total_prev_live_bytes(0), _total_next_live_bytes(0),
    _hum_used_bytes(0), _hum_capacity_bytes(0),
    _hum_prev_live_bytes(0), _hum_next_live_bytes(0) {
  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  MemRegion g1_committed = g1h->g1_committed();
  MemRegion g1_reserved = g1h->g1_reserved();
  double now = os::elapsedTime();

  // Print the header of the output.
  _out->cr();
  _out->print_cr(G1PPRL_LINE_PREFIX" PHASE %s @ %1.3f", phase_name, now);
  _out->print_cr(G1PPRL_LINE_PREFIX" HEAP"
                 G1PPRL_SUM_ADDR_FORMAT("committed")
                 G1PPRL_SUM_ADDR_FORMAT("reserved")
                 G1PPRL_SUM_BYTE_FORMAT("region-size"),
                 g1_committed.start(), g1_committed.end(),
                 g1_reserved.start(), g1_reserved.end(),
                 HeapRegion::GrainBytes);
  _out->print_cr(G1PPRL_LINE_PREFIX);
  _out->print_cr(G1PPRL_LINE_PREFIX
                 G1PPRL_TYPE_H_FORMAT
                 G1PPRL_ADDR_BASE_H_FORMAT
                 G1PPRL_BYTE_H_FORMAT
                 G1PPRL_BYTE_H_FORMAT
                 G1PPRL_BYTE_H_FORMAT
                 G1PPRL_DOUBLE_H_FORMAT,
                 "type", "address-range",
                 "used", "prev-live", "next-live", "gc-eff");
  _out->print_cr(G1PPRL_LINE_PREFIX
                 G1PPRL_TYPE_H_FORMAT
                 G1PPRL_ADDR_BASE_H_FORMAT
                 G1PPRL_BYTE_H_FORMAT
                 G1PPRL_BYTE_H_FORMAT
                 G1PPRL_BYTE_H_FORMAT
                 G1PPRL_DOUBLE_H_FORMAT,
                 "", "",
                 "(bytes)", "(bytes)", "(bytes)", "(bytes/ms)");
}

// It takes as a parameter a reference to one of the _hum_* fields, it
// deduces the corresponding value for a region in a humongous region
// series (either the region size, or what's left if the _hum_* field
// is < the region size), and updates the _hum_* field accordingly.
size_t G1PrintRegionLivenessInfoClosure::get_hum_bytes(size_t* hum_bytes) {
  size_t bytes = 0;
  // The > 0 check is to deal with the prev and next live bytes which
  // could be 0.
  if (*hum_bytes > 0) {
    bytes = MIN2(HeapRegion::GrainBytes, *hum_bytes);
    *hum_bytes -= bytes;
  }
  return bytes;
}

// It deduces the values for a region in a humongous region series
// from the _hum_* fields and updates those accordingly. It assumes
// that that _hum_* fields have already been set up from the "starts
// humongous" region and we visit the regions in address order.
void G1PrintRegionLivenessInfoClosure::get_hum_bytes(size_t* used_bytes,
                                                     size_t* capacity_bytes,
                                                     size_t* prev_live_bytes,
                                                     size_t* next_live_bytes) {
  assert(_hum_used_bytes > 0 && _hum_capacity_bytes > 0, "pre-condition");
  *used_bytes      = get_hum_bytes(&_hum_used_bytes);
  *capacity_bytes  = get_hum_bytes(&_hum_capacity_bytes);
  *prev_live_bytes = get_hum_bytes(&_hum_prev_live_bytes);
  *next_live_bytes = get_hum_bytes(&_hum_next_live_bytes);
}

bool G1PrintRegionLivenessInfoClosure::doHeapRegion(HeapRegion* r) {
  const char* type = "";
  HeapWord* bottom       = r->bottom();
  HeapWord* end          = r->end();
  size_t capacity_bytes  = r->capacity();
  size_t used_bytes      = r->used();
  size_t prev_live_bytes = r->live_bytes();
  size_t next_live_bytes = r->next_live_bytes();
  double gc_eff          = r->gc_efficiency();
  if (r->used() == 0) {
    type = "FREE";
  } else if (r->is_survivor()) {
    type = "SURV";
  } else if (r->is_young()) {
    type = "EDEN";
  } else if (r->startsHumongous()) {
    type = "HUMS";

    assert(_hum_used_bytes == 0 && _hum_capacity_bytes == 0 &&
           _hum_prev_live_bytes == 0 && _hum_next_live_bytes == 0,
           "they should have been zeroed after the last time we used them");
    // Set up the _hum_* fields.
    _hum_capacity_bytes  = capacity_bytes;
    _hum_used_bytes      = used_bytes;
    _hum_prev_live_bytes = prev_live_bytes;
    _hum_next_live_bytes = next_live_bytes;
    get_hum_bytes(&used_bytes, &capacity_bytes,
                  &prev_live_bytes, &next_live_bytes);
    end = bottom + HeapRegion::GrainWords;
  } else if (r->continuesHumongous()) {
    type = "HUMC";
    get_hum_bytes(&used_bytes, &capacity_bytes,
                  &prev_live_bytes, &next_live_bytes);
    assert(end == bottom + HeapRegion::GrainWords, "invariant");
  } else {
    type = "OLD";
  }

  _total_used_bytes      += used_bytes;
  _total_capacity_bytes  += capacity_bytes;
  _total_prev_live_bytes += prev_live_bytes;
  _total_next_live_bytes += next_live_bytes;

  // Print a line for this particular region.
  _out->print_cr(G1PPRL_LINE_PREFIX
                 G1PPRL_TYPE_FORMAT
                 G1PPRL_ADDR_BASE_FORMAT
                 G1PPRL_BYTE_FORMAT
                 G1PPRL_BYTE_FORMAT
                 G1PPRL_BYTE_FORMAT
                 G1PPRL_DOUBLE_FORMAT,
                 type, bottom, end,
                 used_bytes, prev_live_bytes, next_live_bytes, gc_eff);

  return false;
}

G1PrintRegionLivenessInfoClosure::~G1PrintRegionLivenessInfoClosure() {
  // Print the footer of the output.
  _out->print_cr(G1PPRL_LINE_PREFIX);
  _out->print_cr(G1PPRL_LINE_PREFIX
                 " SUMMARY"
                 G1PPRL_SUM_MB_FORMAT("capacity")
                 G1PPRL_SUM_MB_PERC_FORMAT("used")
                 G1PPRL_SUM_MB_PERC_FORMAT("prev-live")
                 G1PPRL_SUM_MB_PERC_FORMAT("next-live"),
                 bytes_to_mb(_total_capacity_bytes),
                 bytes_to_mb(_total_used_bytes),
                 perc(_total_used_bytes, _total_capacity_bytes),
                 bytes_to_mb(_total_prev_live_bytes),
                 perc(_total_prev_live_bytes, _total_capacity_bytes),
                 bytes_to_mb(_total_next_live_bytes),
                 perc(_total_next_live_bytes, _total_capacity_bytes));
  _out->cr();
}
