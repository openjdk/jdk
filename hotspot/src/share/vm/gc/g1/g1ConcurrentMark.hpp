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

#ifndef SHARE_VM_GC_G1_G1CONCURRENTMARK_HPP
#define SHARE_VM_GC_G1_G1CONCURRENTMARK_HPP

#include "classfile/javaClasses.hpp"
#include "gc/g1/g1RegionToSpaceMapper.hpp"
#include "gc/g1/heapRegionSet.hpp"
#include "gc/shared/taskqueue.hpp"

class G1CollectedHeap;
class G1CMBitMap;
class G1CMTask;
class G1ConcurrentMark;
class ConcurrentGCTimer;
class G1OldTracer;
typedef GenericTaskQueue<oop, mtGC>              G1CMTaskQueue;
typedef GenericTaskQueueSet<G1CMTaskQueue, mtGC> G1CMTaskQueueSet;

// Closure used by CM during concurrent reference discovery
// and reference processing (during remarking) to determine
// if a particular object is alive. It is primarily used
// to determine if referents of discovered reference objects
// are alive. An instance is also embedded into the
// reference processor as the _is_alive_non_header field
class G1CMIsAliveClosure: public BoolObjectClosure {
  G1CollectedHeap* _g1;
 public:
  G1CMIsAliveClosure(G1CollectedHeap* g1) : _g1(g1) { }

  bool do_object_b(oop obj);
};

// A generic CM bit map.  This is essentially a wrapper around the BitMap
// class, with one bit per (1<<_shifter) HeapWords.

class G1CMBitMapRO VALUE_OBJ_CLASS_SPEC {
 protected:
  HeapWord* _bmStartWord;      // base address of range covered by map
  size_t    _bmWordSize;       // map size (in #HeapWords covered)
  const int _shifter;          // map to char or bit
  BitMap    _bm;               // the bit map itself

 public:
  // constructor
  G1CMBitMapRO(int shifter);

  // inquiries
  HeapWord* startWord()   const { return _bmStartWord; }
  // the following is one past the last word in space
  HeapWord* endWord()     const { return _bmStartWord + _bmWordSize; }

  // read marks

  bool isMarked(HeapWord* addr) const {
    assert(_bmStartWord <= addr && addr < (_bmStartWord + _bmWordSize),
           "outside underlying space?");
    return _bm.at(heapWordToOffset(addr));
  }

  // iteration
  inline bool iterate(BitMapClosure* cl, MemRegion mr);

  // Return the address corresponding to the next marked bit at or after
  // "addr", and before "limit", if "limit" is non-NULL.  If there is no
  // such bit, returns "limit" if that is non-NULL, or else "endWord()".
  HeapWord* getNextMarkedWordAddress(const HeapWord* addr,
                                     const HeapWord* limit = NULL) const;

  // conversion utilities
  HeapWord* offsetToHeapWord(size_t offset) const {
    return _bmStartWord + (offset << _shifter);
  }
  size_t heapWordToOffset(const HeapWord* addr) const {
    return pointer_delta(addr, _bmStartWord) >> _shifter;
  }

  // The argument addr should be the start address of a valid object
  inline HeapWord* nextObject(HeapWord* addr);

  void print_on_error(outputStream* st, const char* prefix) const;

  // debugging
  NOT_PRODUCT(bool covers(MemRegion rs) const;)
};

class G1CMBitMapMappingChangedListener : public G1MappingChangedListener {
 private:
  G1CMBitMap* _bm;
 public:
  G1CMBitMapMappingChangedListener() : _bm(NULL) {}

  void set_bitmap(G1CMBitMap* bm) { _bm = bm; }

  virtual void on_commit(uint start_idx, size_t num_regions, bool zero_filled);
};

class G1CMBitMap : public G1CMBitMapRO {
 private:
  G1CMBitMapMappingChangedListener _listener;

 public:
  static size_t compute_size(size_t heap_size);
  // Returns the amount of bytes on the heap between two marks in the bitmap.
  static size_t mark_distance();
  // Returns how many bytes (or bits) of the heap a single byte (or bit) of the
  // mark bitmap corresponds to. This is the same as the mark distance above.
  static size_t heap_map_factor() {
    return mark_distance();
  }

  G1CMBitMap() : G1CMBitMapRO(LogMinObjAlignment), _listener() { _listener.set_bitmap(this); }

  // Initializes the underlying BitMap to cover the given area.
  void initialize(MemRegion heap, G1RegionToSpaceMapper* storage);

  // Write marks.
  inline void mark(HeapWord* addr);
  inline void clear(HeapWord* addr);
  inline bool parMark(HeapWord* addr);

  void clear_range(MemRegion mr);
};

// Represents a marking stack used by ConcurrentMarking in the G1 collector.
class G1CMMarkStack VALUE_OBJ_CLASS_SPEC {
  VirtualSpace _virtual_space;   // Underlying backing store for actual stack
  G1ConcurrentMark* _cm;
  oop* _base;        // bottom of stack
  jint _index;       // one more than last occupied index
  jint _capacity;    // max #elements
  jint _saved_index; // value of _index saved at start of GC

  bool  _overflow;
  bool  _should_expand;

 public:
  G1CMMarkStack(G1ConcurrentMark* cm);
  ~G1CMMarkStack();

  bool allocate(size_t capacity);

  // Pushes the first "n" elements of "ptr_arr" on the stack.
  // Locking impl: concurrency is allowed only with
  // "par_push_arr" and/or "par_pop_arr" operations, which use the same
  // locking strategy.
  void par_push_arr(oop* ptr_arr, int n);

  // If returns false, the array was empty.  Otherwise, removes up to "max"
  // elements from the stack, and transfers them to "ptr_arr" in an
  // unspecified order.  The actual number transferred is given in "n" ("n
  // == 0" is deliberately redundant with the return value.)  Locking impl:
  // concurrency is allowed only with "par_push_arr" and/or "par_pop_arr"
  // operations, which use the same locking strategy.
  bool par_pop_arr(oop* ptr_arr, int max, int* n);

  bool isEmpty()    { return _index == 0; }
  int  maxElems()   { return _capacity; }

  bool overflow() { return _overflow; }
  void clear_overflow() { _overflow = false; }

  bool should_expand() const { return _should_expand; }
  void set_should_expand();

  // Expand the stack, typically in response to an overflow condition
  void expand();

  int  size() { return _index; }

  void setEmpty()   { _index = 0; clear_overflow(); }

  // Record the current index.
  void note_start_of_gc();

  // Make sure that we have not added any entries to the stack during GC.
  void note_end_of_gc();

  // Apply fn to each oop in the mark stack, up to the bound recorded
  // via one of the above "note" functions.  The mark stack must not
  // be modified while iterating.
  template<typename Fn> void iterate(Fn fn);
};

class YoungList;

// Root Regions are regions that are not empty at the beginning of a
// marking cycle and which we might collect during an evacuation pause
// while the cycle is active. Given that, during evacuation pauses, we
// do not copy objects that are explicitly marked, what we have to do
// for the root regions is to scan them and mark all objects reachable
// from them. According to the SATB assumptions, we only need to visit
// each object once during marking. So, as long as we finish this scan
// before the next evacuation pause, we can copy the objects from the
// root regions without having to mark them or do anything else to them.
//
// Currently, we only support root region scanning once (at the start
// of the marking cycle) and the root regions are all the survivor
// regions populated during the initial-mark pause.
class G1CMRootRegions VALUE_OBJ_CLASS_SPEC {
private:
  YoungList*           _young_list;
  G1ConcurrentMark*    _cm;

  volatile bool        _scan_in_progress;
  volatile bool        _should_abort;
  HeapRegion* volatile _next_survivor;

  void notify_scan_done();

public:
  G1CMRootRegions();
  // We actually do most of the initialization in this method.
  void init(G1CollectedHeap* g1h, G1ConcurrentMark* cm);

  // Reset the claiming / scanning of the root regions.
  void prepare_for_scan();

  // Forces get_next() to return NULL so that the iteration aborts early.
  void abort() { _should_abort = true; }

  // Return true if the CM thread are actively scanning root regions,
  // false otherwise.
  bool scan_in_progress() { return _scan_in_progress; }

  // Claim the next root region to scan atomically, or return NULL if
  // all have been claimed.
  HeapRegion* claim_next();

  void cancel_scan();

  // Flag that we're done with root region scanning and notify anyone
  // who's waiting on it. If aborted is false, assume that all regions
  // have been claimed.
  void scan_finished();

  // If CM threads are still scanning root regions, wait until they
  // are done. Return true if we had to wait, false otherwise.
  bool wait_until_scan_finished();
};

class ConcurrentMarkThread;

class G1ConcurrentMark: public CHeapObj<mtGC> {
  friend class ConcurrentMarkThread;
  friend class G1ParNoteEndTask;
  friend class G1VerifyLiveDataClosure;
  friend class G1CMRefProcTaskProxy;
  friend class G1CMRefProcTaskExecutor;
  friend class G1CMKeepAliveAndDrainClosure;
  friend class G1CMDrainMarkingStackClosure;
  friend class G1CMBitMapClosure;
  friend class G1CMConcurrentMarkingTask;
  friend class G1CMMarkStack;
  friend class G1CMRemarkTask;
  friend class G1CMTask;

protected:
  ConcurrentMarkThread* _cmThread;   // The thread doing the work
  G1CollectedHeap*      _g1h;        // The heap
  uint                  _parallel_marking_threads; // The number of marking
                                                   // threads we're using
  uint                  _max_parallel_marking_threads; // Max number of marking
                                                       // threads we'll ever use
  double                _sleep_factor; // How much we have to sleep, with
                                       // respect to the work we just did, to
                                       // meet the marking overhead goal
  double                _marking_task_overhead; // Marking target overhead for
                                                // a single task

  FreeRegionList        _cleanup_list;

  // Concurrent marking support structures
  G1CMBitMap              _markBitMap1;
  G1CMBitMap              _markBitMap2;
  G1CMBitMapRO*           _prevMarkBitMap; // Completed mark bitmap
  G1CMBitMap*             _nextMarkBitMap; // Under-construction mark bitmap

  // Heap bounds
  HeapWord*               _heap_start;
  HeapWord*               _heap_end;

  // Root region tracking and claiming
  G1CMRootRegions         _root_regions;

  // For gray objects
  G1CMMarkStack           _markStack; // Grey objects behind global finger
  HeapWord* volatile      _finger;  // The global finger, region aligned,
                                    // always points to the end of the
                                    // last claimed region

  // Marking tasks
  uint                    _max_worker_id;// Maximum worker id
  uint                    _active_tasks; // Task num currently active
  G1CMTask**              _tasks;        // Task queue array (max_worker_id len)
  G1CMTaskQueueSet*       _task_queues;  // Task queue set
  ParallelTaskTerminator  _terminator;   // For termination

  // Two sync barriers that are used to synchronize tasks when an
  // overflow occurs. The algorithm is the following. All tasks enter
  // the first one to ensure that they have all stopped manipulating
  // the global data structures. After they exit it, they re-initialize
  // their data structures and task 0 re-initializes the global data
  // structures. Then, they enter the second sync barrier. This
  // ensure, that no task starts doing work before all data
  // structures (local and global) have been re-initialized. When they
  // exit it, they are free to start working again.
  WorkGangBarrierSync     _first_overflow_barrier_sync;
  WorkGangBarrierSync     _second_overflow_barrier_sync;

  // This is set by any task, when an overflow on the global data
  // structures is detected
  volatile bool           _has_overflown;
  // True: marking is concurrent, false: we're in remark
  volatile bool           _concurrent;
  // Set at the end of a Full GC so that marking aborts
  volatile bool           _has_aborted;

  // Used when remark aborts due to an overflow to indicate that
  // another concurrent marking phase should start
  volatile bool           _restart_for_overflow;

  // This is true from the very start of concurrent marking until the
  // point when all the tasks complete their work. It is really used
  // to determine the points between the end of concurrent marking and
  // time of remark.
  volatile bool           _concurrent_marking_in_progress;

  ConcurrentGCTimer*      _gc_timer_cm;

  G1OldTracer*            _gc_tracer_cm;

  // All of these times are in ms
  NumberSeq _init_times;
  NumberSeq _remark_times;
  NumberSeq _remark_mark_times;
  NumberSeq _remark_weak_ref_times;
  NumberSeq _cleanup_times;
  double    _total_counting_time;
  double    _total_rs_scrub_time;

  double*   _accum_task_vtime;   // Accumulated task vtime

  WorkGang* _parallel_workers;

  void weakRefsWorkParallelPart(BoolObjectClosure* is_alive, bool purged_classes);
  void weakRefsWork(bool clear_all_soft_refs);

  void swapMarkBitMaps();

  // It resets the global marking data structures, as well as the
  // task local ones; should be called during initial mark.
  void reset();

  // Resets all the marking data structures. Called when we have to restart
  // marking or when marking completes (via set_non_marking_state below).
  void reset_marking_state(bool clear_overflow = true);

  // We do this after we're done with marking so that the marking data
  // structures are initialized to a sensible and predictable state.
  void set_non_marking_state();

  // Called to indicate how many threads are currently active.
  void set_concurrency(uint active_tasks);

  // It should be called to indicate which phase we're in (concurrent
  // mark or remark) and how many threads are currently active.
  void set_concurrency_and_phase(uint active_tasks, bool concurrent);

  // Prints all gathered CM-related statistics
  void print_stats();

  bool cleanup_list_is_empty() {
    return _cleanup_list.is_empty();
  }

  // Accessor methods
  uint parallel_marking_threads() const     { return _parallel_marking_threads; }
  uint max_parallel_marking_threads() const { return _max_parallel_marking_threads;}
  double sleep_factor()                     { return _sleep_factor; }
  double marking_task_overhead()            { return _marking_task_overhead;}

  HeapWord*               finger()          { return _finger;   }
  bool                    concurrent()      { return _concurrent; }
  uint                    active_tasks()    { return _active_tasks; }
  ParallelTaskTerminator* terminator()      { return &_terminator; }

  // It claims the next available region to be scanned by a marking
  // task/thread. It might return NULL if the next region is empty or
  // we have run out of regions. In the latter case, out_of_regions()
  // determines whether we've really run out of regions or the task
  // should call claim_region() again. This might seem a bit
  // awkward. Originally, the code was written so that claim_region()
  // either successfully returned with a non-empty region or there
  // were no more regions to be claimed. The problem with this was
  // that, in certain circumstances, it iterated over large chunks of
  // the heap finding only empty regions and, while it was working, it
  // was preventing the calling task to call its regular clock
  // method. So, this way, each task will spend very little time in
  // claim_region() and is allowed to call the regular clock method
  // frequently.
  HeapRegion* claim_region(uint worker_id);

  // It determines whether we've run out of regions to scan. Note that
  // the finger can point past the heap end in case the heap was expanded
  // to satisfy an allocation without doing a GC. This is fine, because all
  // objects in those regions will be considered live anyway because of
  // SATB guarantees (i.e. their TAMS will be equal to bottom).
  bool        out_of_regions() { return _finger >= _heap_end; }

  // Returns the task with the given id
  G1CMTask* task(int id) {
    assert(0 <= id && id < (int) _active_tasks,
           "task id not within active bounds");
    return _tasks[id];
  }

  // Returns the task queue with the given id
  G1CMTaskQueue* task_queue(int id) {
    assert(0 <= id && id < (int) _active_tasks,
           "task queue id not within active bounds");
    return (G1CMTaskQueue*) _task_queues->queue(id);
  }

  // Returns the task queue set
  G1CMTaskQueueSet* task_queues()  { return _task_queues; }

  // Access / manipulation of the overflow flag which is set to
  // indicate that the global stack has overflown
  bool has_overflown()           { return _has_overflown; }
  void set_has_overflown()       { _has_overflown = true; }
  void clear_has_overflown()     { _has_overflown = false; }
  bool restart_for_overflow()    { return _restart_for_overflow; }

  // Methods to enter the two overflow sync barriers
  void enter_first_sync_barrier(uint worker_id);
  void enter_second_sync_barrier(uint worker_id);

  // Card index of the bottom of the G1 heap. Used for biasing indices into
  // the card bitmaps.
  intptr_t _heap_bottom_card_num;

  // Set to true when initialization is complete
  bool _completed_initialization;

  // end_timer, true to end gc timer after ending concurrent phase.
  void register_concurrent_phase_end_common(bool end_timer);

  // Clear the given bitmap in parallel using the given WorkGang. If may_yield is
  // true, periodically insert checks to see if this method should exit prematurely.
  void clear_bitmap(G1CMBitMap* bitmap, WorkGang* workers, bool may_yield);
public:
  // Manipulation of the global mark stack.
  // The push and pop operations are used by tasks for transfers
  // between task-local queues and the global mark stack, and use
  // locking for concurrency safety.
  bool mark_stack_push(oop* arr, int n) {
    _markStack.par_push_arr(arr, n);
    if (_markStack.overflow()) {
      set_has_overflown();
      return false;
    }
    return true;
  }
  void mark_stack_pop(oop* arr, int max, int* n) {
    _markStack.par_pop_arr(arr, max, n);
  }
  size_t mark_stack_size()                { return _markStack.size(); }
  size_t partial_mark_stack_size_target() { return _markStack.maxElems()/3; }
  bool mark_stack_overflow()              { return _markStack.overflow(); }
  bool mark_stack_empty()                 { return _markStack.isEmpty(); }

  G1CMRootRegions* root_regions() { return &_root_regions; }

  bool concurrent_marking_in_progress() {
    return _concurrent_marking_in_progress;
  }
  void set_concurrent_marking_in_progress() {
    _concurrent_marking_in_progress = true;
  }
  void clear_concurrent_marking_in_progress() {
    _concurrent_marking_in_progress = false;
  }

  void concurrent_cycle_start();
  void concurrent_cycle_end();

  void update_accum_task_vtime(int i, double vtime) {
    _accum_task_vtime[i] += vtime;
  }

  double all_task_accum_vtime() {
    double ret = 0.0;
    for (uint i = 0; i < _max_worker_id; ++i)
      ret += _accum_task_vtime[i];
    return ret;
  }

  // Attempts to steal an object from the task queues of other tasks
  bool try_stealing(uint worker_id, int* hash_seed, oop& obj);

  G1ConcurrentMark(G1CollectedHeap* g1h,
                   G1RegionToSpaceMapper* prev_bitmap_storage,
                   G1RegionToSpaceMapper* next_bitmap_storage);
  ~G1ConcurrentMark();

  ConcurrentMarkThread* cmThread() { return _cmThread; }

  G1CMBitMapRO* prevMarkBitMap() const { return _prevMarkBitMap; }
  G1CMBitMap*   nextMarkBitMap() const { return _nextMarkBitMap; }

  // Returns the number of GC threads to be used in a concurrent
  // phase based on the number of GC threads being used in a STW
  // phase.
  uint scale_parallel_threads(uint n_par_threads);

  // Calculates the number of GC threads to be used in a concurrent phase.
  uint calc_parallel_marking_threads();

  // The following three are interaction between CM and
  // G1CollectedHeap

  // This notifies CM that a root during initial-mark needs to be
  // grayed. It is MT-safe. hr is the region that
  // contains the object and it's passed optionally from callers who
  // might already have it (no point in recalculating it).
  inline void grayRoot(oop obj,
                       HeapRegion* hr = NULL);

  // Prepare internal data structures for the next mark cycle. This includes clearing
  // the next mark bitmap and some internal data structures. This method is intended
  // to be called concurrently to the mutator. It will yield to safepoint requests.
  void cleanup_for_next_mark();

  // Clear the previous marking bitmap during safepoint.
  void clear_prev_bitmap(WorkGang* workers);

  // Return whether the next mark bitmap has no marks set. To be used for assertions
  // only. Will not yield to pause requests.
  bool nextMarkBitmapIsClear();

  // These two do the work that needs to be done before and after the
  // initial root checkpoint. Since this checkpoint can be done at two
  // different points (i.e. an explicit pause or piggy-backed on a
  // young collection), then it's nice to be able to easily share the
  // pre/post code. It might be the case that we can put everything in
  // the post method. TP
  void checkpointRootsInitialPre();
  void checkpointRootsInitialPost();

  // Scan all the root regions and mark everything reachable from
  // them.
  void scan_root_regions();

  // Scan a single root region and mark everything reachable from it.
  void scanRootRegion(HeapRegion* hr);

  // Do concurrent phase of marking, to a tentative transitive closure.
  void mark_from_roots();

  void checkpointRootsFinal(bool clear_all_soft_refs);
  void checkpointRootsFinalWork();
  void cleanup();
  void complete_cleanup();

  // Mark in the previous bitmap.  NB: this is usually read-only, so use
  // this carefully!
  inline void markPrev(oop p);

  // Clears marks for all objects in the given range, for the prev or
  // next bitmaps.  NB: the previous bitmap is usually
  // read-only, so use this carefully!
  void clearRangePrevBitmap(MemRegion mr);

  // Notify data structures that a GC has started.
  void note_start_of_gc() {
    _markStack.note_start_of_gc();
  }

  // Notify data structures that a GC is finished.
  void note_end_of_gc() {
    _markStack.note_end_of_gc();
  }

  // Verify that there are no CSet oops on the stacks (taskqueues /
  // global mark stack) and fingers (global / per-task).
  // If marking is not in progress, it's a no-op.
  void verify_no_cset_oops() PRODUCT_RETURN;

  inline bool isPrevMarked(oop p) const;

  inline bool do_yield_check();

  // Abandon current marking iteration due to a Full GC.
  void abort();

  bool has_aborted()      { return _has_aborted; }

  void print_summary_info();

  void print_worker_threads_on(outputStream* st) const;
  void threads_do(ThreadClosure* tc) const;

  void print_on_error(outputStream* st) const;

  // Attempts to mark the given object on the next mark bitmap.
  inline bool par_mark(oop obj);

  // Returns true if initialization was successfully completed.
  bool completed_initialization() const {
    return _completed_initialization;
  }

  ConcurrentGCTimer* gc_timer_cm() const { return _gc_timer_cm; }
  G1OldTracer* gc_tracer_cm() const { return _gc_tracer_cm; }

private:
  // Clear (Reset) all liveness count data.
  void clear_live_data(WorkGang* workers);

#ifdef ASSERT
  // Verify all of the above data structures that they are in initial state.
  void verify_live_data_clear();
#endif

  // Aggregates the per-card liveness data based on the current marking. Also sets
  // the amount of marked bytes for each region.
  void create_live_data();

  void finalize_live_data();

  void verify_live_data();
};

// A class representing a marking task.
class G1CMTask : public TerminatorTerminator {
private:
  enum PrivateConstants {
    // the regular clock call is called once the scanned words reaches
    // this limit
    words_scanned_period          = 12*1024,
    // the regular clock call is called once the number of visited
    // references reaches this limit
    refs_reached_period           = 384,
    // initial value for the hash seed, used in the work stealing code
    init_hash_seed                = 17,
    // how many entries will be transferred between global stack and
    // local queues
    global_stack_transfer_size    = 16
  };

  uint                        _worker_id;
  G1CollectedHeap*            _g1h;
  G1ConcurrentMark*           _cm;
  G1CMBitMap*                 _nextMarkBitMap;
  // the task queue of this task
  G1CMTaskQueue*              _task_queue;
private:
  // the task queue set---needed for stealing
  G1CMTaskQueueSet*           _task_queues;
  // indicates whether the task has been claimed---this is only  for
  // debugging purposes
  bool                        _claimed;

  // number of calls to this task
  int                         _calls;

  // when the virtual timer reaches this time, the marking step should
  // exit
  double                      _time_target_ms;
  // the start time of the current marking step
  double                      _start_time_ms;

  // the oop closure used for iterations over oops
  G1CMOopClosure*             _cm_oop_closure;

  // the region this task is scanning, NULL if we're not scanning any
  HeapRegion*                 _curr_region;
  // the local finger of this task, NULL if we're not scanning a region
  HeapWord*                   _finger;
  // limit of the region this task is scanning, NULL if we're not scanning one
  HeapWord*                   _region_limit;

  // the number of words this task has scanned
  size_t                      _words_scanned;
  // When _words_scanned reaches this limit, the regular clock is
  // called. Notice that this might be decreased under certain
  // circumstances (i.e. when we believe that we did an expensive
  // operation).
  size_t                      _words_scanned_limit;
  // the initial value of _words_scanned_limit (i.e. what it was
  // before it was decreased).
  size_t                      _real_words_scanned_limit;

  // the number of references this task has visited
  size_t                      _refs_reached;
  // When _refs_reached reaches this limit, the regular clock is
  // called. Notice this this might be decreased under certain
  // circumstances (i.e. when we believe that we did an expensive
  // operation).
  size_t                      _refs_reached_limit;
  // the initial value of _refs_reached_limit (i.e. what it was before
  // it was decreased).
  size_t                      _real_refs_reached_limit;

  // used by the work stealing stuff
  int                         _hash_seed;
  // if this is true, then the task has aborted for some reason
  bool                        _has_aborted;
  // set when the task aborts because it has met its time quota
  bool                        _has_timed_out;
  // true when we're draining SATB buffers; this avoids the task
  // aborting due to SATB buffers being available (as we're already
  // dealing with them)
  bool                        _draining_satb_buffers;

  // number sequence of past step times
  NumberSeq                   _step_times_ms;
  // elapsed time of this task
  double                      _elapsed_time_ms;
  // termination time of this task
  double                      _termination_time_ms;
  // when this task got into the termination protocol
  double                      _termination_start_time_ms;

  // true when the task is during a concurrent phase, false when it is
  // in the remark phase (so, in the latter case, we do not have to
  // check all the things that we have to check during the concurrent
  // phase, i.e. SATB buffer availability...)
  bool                        _concurrent;

  TruncatedSeq                _marking_step_diffs_ms;

  // it updates the local fields after this task has claimed
  // a new region to scan
  void setup_for_region(HeapRegion* hr);
  // it brings up-to-date the limit of the region
  void update_region_limit();

  // called when either the words scanned or the refs visited limit
  // has been reached
  void reached_limit();
  // recalculates the words scanned and refs visited limits
  void recalculate_limits();
  // decreases the words scanned and refs visited limits when we reach
  // an expensive operation
  void decrease_limits();
  // it checks whether the words scanned or refs visited reached their
  // respective limit and calls reached_limit() if they have
  void check_limits() {
    if (_words_scanned >= _words_scanned_limit ||
        _refs_reached >= _refs_reached_limit) {
      reached_limit();
    }
  }
  // this is supposed to be called regularly during a marking step as
  // it checks a bunch of conditions that might cause the marking step
  // to abort
  void regular_clock_call();
  bool concurrent() { return _concurrent; }

  // Test whether obj might have already been passed over by the
  // mark bitmap scan, and so needs to be pushed onto the mark stack.
  bool is_below_finger(oop obj, HeapWord* global_finger) const;

  template<bool scan> void process_grey_object(oop obj);

public:
  // It resets the task; it should be called right at the beginning of
  // a marking phase.
  void reset(G1CMBitMap* _nextMarkBitMap);
  // it clears all the fields that correspond to a claimed region.
  void clear_region_fields();

  void set_concurrent(bool concurrent) { _concurrent = concurrent; }

  // The main method of this class which performs a marking step
  // trying not to exceed the given duration. However, it might exit
  // prematurely, according to some conditions (i.e. SATB buffers are
  // available for processing).
  void do_marking_step(double target_ms,
                       bool do_termination,
                       bool is_serial);

  // These two calls start and stop the timer
  void record_start_time() {
    _elapsed_time_ms = os::elapsedTime() * 1000.0;
  }
  void record_end_time() {
    _elapsed_time_ms = os::elapsedTime() * 1000.0 - _elapsed_time_ms;
  }

  // returns the worker ID associated with this task.
  uint worker_id() { return _worker_id; }

  // From TerminatorTerminator. It determines whether this task should
  // exit the termination protocol after it's entered it.
  virtual bool should_exit_termination();

  // Resets the local region fields after a task has finished scanning a
  // region; or when they have become stale as a result of the region
  // being evacuated.
  void giveup_current_region();

  HeapWord* finger()            { return _finger; }

  bool has_aborted()            { return _has_aborted; }
  void set_has_aborted()        { _has_aborted = true; }
  void clear_has_aborted()      { _has_aborted = false; }
  bool has_timed_out()          { return _has_timed_out; }
  bool claimed()                { return _claimed; }

  void set_cm_oop_closure(G1CMOopClosure* cm_oop_closure);

  // Increment the number of references this task has visited.
  void increment_refs_reached() { ++_refs_reached; }

  // Grey the object by marking it.  If not already marked, push it on
  // the local queue if below the finger.
  // obj is below its region's NTAMS.
  inline void make_reference_grey(oop obj);

  // Grey the object (by calling make_grey_reference) if required,
  // e.g. obj is below its containing region's NTAMS.
  // Precondition: obj is a valid heap object.
  inline void deal_with_reference(oop obj);

  // It scans an object and visits its children.
  inline void scan_object(oop obj);

  // It pushes an object on the local queue.
  inline void push(oop obj);

  // These two move entries to/from the global stack.
  void move_entries_to_global_stack();
  void get_entries_from_global_stack();

  // It pops and scans objects from the local queue. If partially is
  // true, then it stops when the queue size is of a given limit. If
  // partially is false, then it stops when the queue is empty.
  void drain_local_queue(bool partially);
  // It moves entries from the global stack to the local queue and
  // drains the local queue. If partially is true, then it stops when
  // both the global stack and the local queue reach a given size. If
  // partially if false, it tries to empty them totally.
  void drain_global_stack(bool partially);
  // It keeps picking SATB buffers and processing them until no SATB
  // buffers are available.
  void drain_satb_buffers();

  // moves the local finger to a new location
  inline void move_finger_to(HeapWord* new_finger) {
    assert(new_finger >= _finger && new_finger < _region_limit, "invariant");
    _finger = new_finger;
  }

  G1CMTask(uint worker_id,
           G1ConcurrentMark *cm,
           G1CMTaskQueue* task_queue,
           G1CMTaskQueueSet* task_queues);

  // it prints statistics associated with this task
  void print_stats();
};

// Class that's used to to print out per-region liveness
// information. It's currently used at the end of marking and also
// after we sort the old regions at the end of the cleanup operation.
class G1PrintRegionLivenessInfoClosure: public HeapRegionClosure {
private:
  // Accumulators for these values.
  size_t _total_used_bytes;
  size_t _total_capacity_bytes;
  size_t _total_prev_live_bytes;
  size_t _total_next_live_bytes;

  // Accumulator for the remembered set size
  size_t _total_remset_bytes;

  // Accumulator for strong code roots memory size
  size_t _total_strong_code_roots_bytes;

  static double perc(size_t val, size_t total) {
    if (total == 0) {
      return 0.0;
    } else {
      return 100.0 * ((double) val / (double) total);
    }
  }

  static double bytes_to_mb(size_t val) {
    return (double) val / (double) M;
  }

public:
  // The header and footer are printed in the constructor and
  // destructor respectively.
  G1PrintRegionLivenessInfoClosure(const char* phase_name);
  virtual bool doHeapRegion(HeapRegion* r);
  ~G1PrintRegionLivenessInfoClosure();
};

#endif // SHARE_VM_GC_G1_G1CONCURRENTMARK_HPP
