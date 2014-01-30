/*
 * Copyright (c) 2001, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_IMPLEMENTATION_G1_CONCURRENTMARK_HPP
#define SHARE_VM_GC_IMPLEMENTATION_G1_CONCURRENTMARK_HPP

#include "gc_implementation/g1/heapRegionSets.hpp"
#include "utilities/taskqueue.hpp"

class G1CollectedHeap;
class CMTask;
typedef GenericTaskQueue<oop, mtGC>            CMTaskQueue;
typedef GenericTaskQueueSet<CMTaskQueue, mtGC> CMTaskQueueSet;

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

class CMBitMapRO VALUE_OBJ_CLASS_SPEC {
 protected:
  HeapWord* _bmStartWord;      // base address of range covered by map
  size_t    _bmWordSize;       // map size (in #HeapWords covered)
  const int _shifter;          // map to char or bit
  VirtualSpace _virtual_space; // underlying the bit map
  BitMap    _bm;               // the bit map itself

 public:
  // constructor
  CMBitMapRO(int shifter);

  enum { do_yield = true };

  // inquiries
  HeapWord* startWord()   const { return _bmStartWord; }
  size_t    sizeInWords() const { return _bmWordSize;  }
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
  inline bool iterate(BitMapClosure* cl);

  // Return the address corresponding to the next marked bit at or after
  // "addr", and before "limit", if "limit" is non-NULL.  If there is no
  // such bit, returns "limit" if that is non-NULL, or else "endWord()".
  HeapWord* getNextMarkedWordAddress(HeapWord* addr,
                                     HeapWord* limit = NULL) const;
  // Return the address corresponding to the next unmarked bit at or after
  // "addr", and before "limit", if "limit" is non-NULL.  If there is no
  // such bit, returns "limit" if that is non-NULL, or else "endWord()".
  HeapWord* getNextUnmarkedWordAddress(HeapWord* addr,
                                       HeapWord* limit = NULL) const;

  // conversion utilities
  HeapWord* offsetToHeapWord(size_t offset) const {
    return _bmStartWord + (offset << _shifter);
  }
  size_t heapWordToOffset(HeapWord* addr) const {
    return pointer_delta(addr, _bmStartWord) >> _shifter;
  }
  int heapWordDiffToOffsetDiff(size_t diff) const;

  // The argument addr should be the start address of a valid object
  HeapWord* nextObject(HeapWord* addr) {
    oop obj = (oop) addr;
    HeapWord* res =  addr + obj->size();
    assert(offsetToHeapWord(heapWordToOffset(res)) == res, "sanity");
    return res;
  }

  void print_on_error(outputStream* st, const char* prefix) const;

  // debugging
  NOT_PRODUCT(bool covers(ReservedSpace rs) const;)
};

class CMBitMap : public CMBitMapRO {

 public:
  // constructor
  CMBitMap(int shifter) :
    CMBitMapRO(shifter) {}

  // Allocates the back store for the marking bitmap
  bool allocate(ReservedSpace heap_rs);

  // write marks
  void mark(HeapWord* addr) {
    assert(_bmStartWord <= addr && addr < (_bmStartWord + _bmWordSize),
           "outside underlying space?");
    _bm.set_bit(heapWordToOffset(addr));
  }
  void clear(HeapWord* addr) {
    assert(_bmStartWord <= addr && addr < (_bmStartWord + _bmWordSize),
           "outside underlying space?");
    _bm.clear_bit(heapWordToOffset(addr));
  }
  bool parMark(HeapWord* addr) {
    assert(_bmStartWord <= addr && addr < (_bmStartWord + _bmWordSize),
           "outside underlying space?");
    return _bm.par_set_bit(heapWordToOffset(addr));
  }
  bool parClear(HeapWord* addr) {
    assert(_bmStartWord <= addr && addr < (_bmStartWord + _bmWordSize),
           "outside underlying space?");
    return _bm.par_clear_bit(heapWordToOffset(addr));
  }
  void markRange(MemRegion mr);
  void clearAll();
  void clearRange(MemRegion mr);

  // Starting at the bit corresponding to "addr" (inclusive), find the next
  // "1" bit, if any.  This bit starts some run of consecutive "1"'s; find
  // the end of this run (stopping at "end_addr").  Return the MemRegion
  // covering from the start of the region corresponding to the first bit
  // of the run to the end of the region corresponding to the last bit of
  // the run.  If there is no "1" bit at or after "addr", return an empty
  // MemRegion.
  MemRegion getAndClearMarkedRegion(HeapWord* addr, HeapWord* end_addr);
};

// Represents a marking stack used by ConcurrentMarking in the G1 collector.
class CMMarkStack VALUE_OBJ_CLASS_SPEC {
  VirtualSpace _virtual_space;   // Underlying backing store for actual stack
  ConcurrentMark* _cm;
  oop* _base;        // bottom of stack
  jint _index;       // one more than last occupied index
  jint _capacity;    // max #elements
  jint _saved_index; // value of _index saved at start of GC
  NOT_PRODUCT(jint _max_depth;)   // max depth plumbed during run

  bool  _overflow;
  bool  _should_expand;
  DEBUG_ONLY(bool _drain_in_progress;)
  DEBUG_ONLY(bool _drain_in_progress_yields;)

 public:
  CMMarkStack(ConcurrentMark* cm);
  ~CMMarkStack();

#ifndef PRODUCT
  jint max_depth() const {
    return _max_depth;
  }
#endif

  bool allocate(size_t capacity);

  oop pop() {
    if (!isEmpty()) {
      return _base[--_index] ;
    }
    return NULL;
  }

  // If overflow happens, don't do the push, and record the overflow.
  // *Requires* that "ptr" is already marked.
  void push(oop ptr) {
    if (isFull()) {
      // Record overflow.
      _overflow = true;
      return;
    } else {
      _base[_index++] = ptr;
      NOT_PRODUCT(_max_depth = MAX2(_max_depth, _index));
    }
  }
  // Non-block impl.  Note: concurrency is allowed only with other
  // "par_push" operations, not with "pop" or "drain".  We would need
  // parallel versions of them if such concurrency was desired.
  void par_push(oop ptr);

  // Pushes the first "n" elements of "ptr_arr" on the stack.
  // Non-block impl.  Note: concurrency is allowed only with other
  // "par_adjoin_arr" or "push" operations, not with "pop" or "drain".
  void par_adjoin_arr(oop* ptr_arr, int n);

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

  // Drain the mark stack, applying the given closure to all fields of
  // objects on the stack.  (That is, continue until the stack is empty,
  // even if closure applications add entries to the stack.)  The "bm"
  // argument, if non-null, may be used to verify that only marked objects
  // are on the mark stack.  If "yield_after" is "true", then the
  // concurrent marker performing the drain offers to yield after
  // processing each object.  If a yield occurs, stops the drain operation
  // and returns false.  Otherwise, returns true.
  template<class OopClosureClass>
  bool drain(OopClosureClass* cl, CMBitMap* bm, bool yield_after = false);

  bool isEmpty()    { return _index == 0; }
  bool isFull()     { return _index == _capacity; }
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

  // iterate over the oops in the mark stack, up to the bound recorded via
  // the call above.
  void oops_do(OopClosure* f);
};

class ForceOverflowSettings VALUE_OBJ_CLASS_SPEC {
private:
#ifndef PRODUCT
  uintx _num_remaining;
  bool _force;
#endif // !defined(PRODUCT)

public:
  void init() PRODUCT_RETURN;
  void update() PRODUCT_RETURN;
  bool should_force() PRODUCT_RETURN_( return false; );
};

// this will enable a variety of different statistics per GC task
#define _MARKING_STATS_       0
// this will enable the higher verbose levels
#define _MARKING_VERBOSE_     0

#if _MARKING_STATS_
#define statsOnly(statement)  \
do {                          \
  statement ;                 \
} while (0)
#else // _MARKING_STATS_
#define statsOnly(statement)  \
do {                          \
} while (0)
#endif // _MARKING_STATS_

typedef enum {
  no_verbose  = 0,   // verbose turned off
  stats_verbose,     // only prints stats at the end of marking
  low_verbose,       // low verbose, mostly per region and per major event
  medium_verbose,    // a bit more detailed than low
  high_verbose       // per object verbose
} CMVerboseLevel;

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
class CMRootRegions VALUE_OBJ_CLASS_SPEC {
private:
  YoungList*           _young_list;
  ConcurrentMark*      _cm;

  volatile bool        _scan_in_progress;
  volatile bool        _should_abort;
  HeapRegion* volatile _next_survivor;

public:
  CMRootRegions();
  // We actually do most of the initialization in this method.
  void init(G1CollectedHeap* g1h, ConcurrentMark* cm);

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

  // Flag that we're done with root region scanning and notify anyone
  // who's waiting on it. If aborted is false, assume that all regions
  // have been claimed.
  void scan_finished();

  // If CM threads are still scanning root regions, wait until they
  // are done. Return true if we had to wait, false otherwise.
  bool wait_until_scan_finished();
};

class ConcurrentMarkThread;

class ConcurrentMark: public CHeapObj<mtGC> {
  friend class CMMarkStack;
  friend class ConcurrentMarkThread;
  friend class CMTask;
  friend class CMBitMapClosure;
  friend class CMGlobalObjectClosure;
  friend class CMRemarkTask;
  friend class CMConcurrentMarkingTask;
  friend class G1ParNoteEndTask;
  friend class CalcLiveObjectsClosure;
  friend class G1CMRefProcTaskProxy;
  friend class G1CMRefProcTaskExecutor;
  friend class G1CMKeepAliveAndDrainClosure;
  friend class G1CMDrainMarkingStackClosure;

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

  // Same as the two above, but for the cleanup task
  double                _cleanup_sleep_factor;
  double                _cleanup_task_overhead;

  FreeRegionList        _cleanup_list;

  // Concurrent marking support structures
  CMBitMap                _markBitMap1;
  CMBitMap                _markBitMap2;
  CMBitMapRO*             _prevMarkBitMap; // Completed mark bitmap
  CMBitMap*               _nextMarkBitMap; // Under-construction mark bitmap

  BitMap                  _region_bm;
  BitMap                  _card_bm;

  // Heap bounds
  HeapWord*               _heap_start;
  HeapWord*               _heap_end;

  // Root region tracking and claiming
  CMRootRegions           _root_regions;

  // For gray objects
  CMMarkStack             _markStack; // Grey objects behind global finger
  HeapWord* volatile      _finger;  // The global finger, region aligned,
                                    // always points to the end of the
                                    // last claimed region

  // Marking tasks
  uint                    _max_worker_id;// Maximum worker id
  uint                    _active_tasks; // Task num currently active
  CMTask**                _tasks;        // Task queue array (max_worker_id len)
  CMTaskQueueSet*         _task_queues;  // Task queue set
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

  // Verbose level
  CMVerboseLevel          _verbose_level;

  // All of these times are in ms
  NumberSeq _init_times;
  NumberSeq _remark_times;
  NumberSeq   _remark_mark_times;
  NumberSeq   _remark_weak_ref_times;
  NumberSeq _cleanup_times;
  double    _total_counting_time;
  double    _total_rs_scrub_time;

  double*   _accum_task_vtime;   // Accumulated task vtime

  FlexibleWorkGang* _parallel_workers;

  ForceOverflowSettings _force_overflow_conc;
  ForceOverflowSettings _force_overflow_stw;

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
  double cleanup_sleep_factor()             { return _cleanup_sleep_factor; }
  double cleanup_task_overhead()            { return _cleanup_task_overhead;}

  bool use_parallel_marking_threads() const {
    assert(parallel_marking_threads() <=
           max_parallel_marking_threads(), "sanity");
    assert((_parallel_workers == NULL && parallel_marking_threads() == 0) ||
           parallel_marking_threads() > 0,
           "parallel workers not set up correctly");
    return _parallel_workers != NULL;
  }

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

  // It determines whether we've run out of regions to scan
  bool        out_of_regions() { return _finger == _heap_end; }

  // Returns the task with the given id
  CMTask* task(int id) {
    assert(0 <= id && id < (int) _active_tasks,
           "task id not within active bounds");
    return _tasks[id];
  }

  // Returns the task queue with the given id
  CMTaskQueue* task_queue(int id) {
    assert(0 <= id && id < (int) _active_tasks,
           "task queue id not within active bounds");
    return (CMTaskQueue*) _task_queues->queue(id);
  }

  // Returns the task queue set
  CMTaskQueueSet* task_queues()  { return _task_queues; }

  // Access / manipulation of the overflow flag which is set to
  // indicate that the global stack has overflown
  bool has_overflown()           { return _has_overflown; }
  void set_has_overflown()       { _has_overflown = true; }
  void clear_has_overflown()     { _has_overflown = false; }
  bool restart_for_overflow()    { return _restart_for_overflow; }

  // Methods to enter the two overflow sync barriers
  void enter_first_sync_barrier(uint worker_id);
  void enter_second_sync_barrier(uint worker_id);

  ForceOverflowSettings* force_overflow_conc() {
    return &_force_overflow_conc;
  }

  ForceOverflowSettings* force_overflow_stw() {
    return &_force_overflow_stw;
  }

  ForceOverflowSettings* force_overflow() {
    if (concurrent()) {
      return force_overflow_conc();
    } else {
      return force_overflow_stw();
    }
  }

  // Live Data Counting data structures...
  // These data structures are initialized at the start of
  // marking. They are written to while marking is active.
  // They are aggregated during remark; the aggregated values
  // are then used to populate the _region_bm, _card_bm, and
  // the total live bytes, which are then subsequently updated
  // during cleanup.

  // An array of bitmaps (one bit map per task). Each bitmap
  // is used to record the cards spanned by the live objects
  // marked by that task/worker.
  BitMap*  _count_card_bitmaps;

  // Used to record the number of marked live bytes
  // (for each region, by worker thread).
  size_t** _count_marked_bytes;

  // Card index of the bottom of the G1 heap. Used for biasing indices into
  // the card bitmaps.
  intptr_t _heap_bottom_card_num;

  // Set to true when initialization is complete
  bool _completed_initialization;

public:
  // Manipulation of the global mark stack.
  // Notice that the first mark_stack_push is CAS-based, whereas the
  // two below are Mutex-based. This is OK since the first one is only
  // called during evacuation pauses and doesn't compete with the
  // other two (which are called by the marking tasks during
  // concurrent marking or remark).
  bool mark_stack_push(oop p) {
    _markStack.par_push(p);
    if (_markStack.overflow()) {
      set_has_overflown();
      return false;
    }
    return true;
  }
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

  CMRootRegions* root_regions() { return &_root_regions; }

  bool concurrent_marking_in_progress() {
    return _concurrent_marking_in_progress;
  }
  void set_concurrent_marking_in_progress() {
    _concurrent_marking_in_progress = true;
  }
  void clear_concurrent_marking_in_progress() {
    _concurrent_marking_in_progress = false;
  }

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
  bool try_stealing(uint worker_id, int* hash_seed, oop& obj) {
    return _task_queues->steal(worker_id, hash_seed, obj);
  }

  ConcurrentMark(G1CollectedHeap* g1h, ReservedSpace heap_rs);
  ~ConcurrentMark();

  ConcurrentMarkThread* cmThread() { return _cmThread; }

  CMBitMapRO* prevMarkBitMap() const { return _prevMarkBitMap; }
  CMBitMap*   nextMarkBitMap() const { return _nextMarkBitMap; }

  // Returns the number of GC threads to be used in a concurrent
  // phase based on the number of GC threads being used in a STW
  // phase.
  uint scale_parallel_threads(uint n_par_threads);

  // Calculates the number of GC threads to be used in a concurrent phase.
  uint calc_parallel_marking_threads();

  // The following three are interaction between CM and
  // G1CollectedHeap

  // This notifies CM that a root during initial-mark needs to be
  // grayed. It is MT-safe. word_size is the size of the object in
  // words. It is passed explicitly as sometimes we cannot calculate
  // it from the given object because it might be in an inconsistent
  // state (e.g., in to-space and being copied). So the caller is
  // responsible for dealing with this issue (e.g., get the size from
  // the from-space image when the to-space image might be
  // inconsistent) and always passing the size. hr is the region that
  // contains the object and it's passed optionally from callers who
  // might already have it (no point in recalculating it).
  inline void grayRoot(oop obj, size_t word_size,
                       uint worker_id, HeapRegion* hr = NULL);

  // It iterates over the heap and for each object it comes across it
  // will dump the contents of its reference fields, as well as
  // liveness information for the object and its referents. The dump
  // will be written to a file with the following name:
  // G1PrintReachableBaseFile + "." + str.
  // vo decides whether the prev (vo == UsePrevMarking), the next
  // (vo == UseNextMarking) marking information, or the mark word
  // (vo == UseMarkWord) will be used to determine the liveness of
  // each object / referent.
  // If all is true, all objects in the heap will be dumped, otherwise
  // only the live ones. In the dump the following symbols / breviations
  // are used:
  //   M : an explicitly live object (its bitmap bit is set)
  //   > : an implicitly live object (over tams)
  //   O : an object outside the G1 heap (typically: in the perm gen)
  //   NOT : a reference field whose referent is not live
  //   AND MARKED : indicates that an object is both explicitly and
  //   implicitly live (it should be one or the other, not both)
  void print_reachable(const char* str,
                       VerifyOption vo, bool all) PRODUCT_RETURN;

  // Clear the next marking bitmap (will be called concurrently).
  void clearNextBitmap();

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
  void scanRootRegions();

  // Scan a single root region and mark everything reachable from it.
  void scanRootRegion(HeapRegion* hr, uint worker_id);

  // Do concurrent phase of marking, to a tentative transitive closure.
  void markFromRoots();

  void checkpointRootsFinal(bool clear_all_soft_refs);
  void checkpointRootsFinalWork();
  void cleanup();
  void completeCleanup();

  // Mark in the previous bitmap.  NB: this is usually read-only, so use
  // this carefully!
  inline void markPrev(oop p);

  // Clears marks for all objects in the given range, for the prev,
  // next, or both bitmaps.  NB: the previous bitmap is usually
  // read-only, so use this carefully!
  void clearRangePrevBitmap(MemRegion mr);
  void clearRangeNextBitmap(MemRegion mr);
  void clearRangeBothBitmaps(MemRegion mr);

  // Notify data structures that a GC has started.
  void note_start_of_gc() {
    _markStack.note_start_of_gc();
  }

  // Notify data structures that a GC is finished.
  void note_end_of_gc() {
    _markStack.note_end_of_gc();
  }

  // Verify that there are no CSet oops on the stacks (taskqueues /
  // global mark stack), enqueued SATB buffers, per-thread SATB
  // buffers, and fingers (global / per-task). The boolean parameters
  // decide which of the above data structures to verify. If marking
  // is not in progress, it's a no-op.
  void verify_no_cset_oops(bool verify_stacks,
                           bool verify_enqueued_buffers,
                           bool verify_thread_buffers,
                           bool verify_fingers) PRODUCT_RETURN;

  // It is called at the end of an evacuation pause during marking so
  // that CM is notified of where the new end of the heap is. It
  // doesn't do anything if concurrent_marking_in_progress() is false,
  // unless the force parameter is true.
  void update_g1_committed(bool force = false);

  bool isMarked(oop p) const {
    assert(p != NULL && p->is_oop(), "expected an oop");
    HeapWord* addr = (HeapWord*)p;
    assert(addr >= _nextMarkBitMap->startWord() ||
           addr < _nextMarkBitMap->endWord(), "in a region");

    return _nextMarkBitMap->isMarked(addr);
  }

  inline bool not_yet_marked(oop p) const;

  // XXX Debug code
  bool containing_card_is_marked(void* p);
  bool containing_cards_are_marked(void* start, void* last);

  bool isPrevMarked(oop p) const {
    assert(p != NULL && p->is_oop(), "expected an oop");
    HeapWord* addr = (HeapWord*)p;
    assert(addr >= _prevMarkBitMap->startWord() ||
           addr < _prevMarkBitMap->endWord(), "in a region");

    return _prevMarkBitMap->isMarked(addr);
  }

  inline bool do_yield_check(uint worker_i = 0);
  inline bool should_yield();

  // Called to abort the marking cycle after a Full GC takes place.
  void abort();

  bool has_aborted()      { return _has_aborted; }

  // This prints the global/local fingers. It is used for debugging.
  NOT_PRODUCT(void print_finger();)

  void print_summary_info();

  void print_worker_threads_on(outputStream* st) const;

  void print_on_error(outputStream* st) const;

  // The following indicate whether a given verbose level has been
  // set. Notice that anything above stats is conditional to
  // _MARKING_VERBOSE_ having been set to 1
  bool verbose_stats() {
    return _verbose_level >= stats_verbose;
  }
  bool verbose_low() {
    return _MARKING_VERBOSE_ && _verbose_level >= low_verbose;
  }
  bool verbose_medium() {
    return _MARKING_VERBOSE_ && _verbose_level >= medium_verbose;
  }
  bool verbose_high() {
    return _MARKING_VERBOSE_ && _verbose_level >= high_verbose;
  }

  // Liveness counting

  // Utility routine to set an exclusive range of cards on the given
  // card liveness bitmap
  inline void set_card_bitmap_range(BitMap* card_bm,
                                    BitMap::idx_t start_idx,
                                    BitMap::idx_t end_idx,
                                    bool is_par);

  // Returns the card number of the bottom of the G1 heap.
  // Used in biasing indices into accounting card bitmaps.
  intptr_t heap_bottom_card_num() const {
    return _heap_bottom_card_num;
  }

  // Returns the card bitmap for a given task or worker id.
  BitMap* count_card_bitmap_for(uint worker_id) {
    assert(0 <= worker_id && worker_id < _max_worker_id, "oob");
    assert(_count_card_bitmaps != NULL, "uninitialized");
    BitMap* task_card_bm = &_count_card_bitmaps[worker_id];
    assert(task_card_bm->size() == _card_bm.size(), "size mismatch");
    return task_card_bm;
  }

  // Returns the array containing the marked bytes for each region,
  // for the given worker or task id.
  size_t* count_marked_bytes_array_for(uint worker_id) {
    assert(0 <= worker_id && worker_id < _max_worker_id, "oob");
    assert(_count_marked_bytes != NULL, "uninitialized");
    size_t* marked_bytes_array = _count_marked_bytes[worker_id];
    assert(marked_bytes_array != NULL, "uninitialized");
    return marked_bytes_array;
  }

  // Returns the index in the liveness accounting card table bitmap
  // for the given address
  inline BitMap::idx_t card_bitmap_index_for(HeapWord* addr);

  // Counts the size of the given memory region in the the given
  // marked_bytes array slot for the given HeapRegion.
  // Sets the bits in the given card bitmap that are associated with the
  // cards that are spanned by the memory region.
  inline void count_region(MemRegion mr, HeapRegion* hr,
                           size_t* marked_bytes_array,
                           BitMap* task_card_bm);

  // Counts the given memory region in the task/worker counting
  // data structures for the given worker id.
  inline void count_region(MemRegion mr, HeapRegion* hr, uint worker_id);

  // Counts the given memory region in the task/worker counting
  // data structures for the given worker id.
  inline void count_region(MemRegion mr, uint worker_id);

  // Counts the given object in the given task/worker counting
  // data structures.
  inline void count_object(oop obj, HeapRegion* hr,
                           size_t* marked_bytes_array,
                           BitMap* task_card_bm);

  // Counts the given object in the task/worker counting data
  // structures for the given worker id.
  inline void count_object(oop obj, HeapRegion* hr, uint worker_id);

  // Attempts to mark the given object and, if successful, counts
  // the object in the given task/worker counting structures.
  inline bool par_mark_and_count(oop obj, HeapRegion* hr,
                                 size_t* marked_bytes_array,
                                 BitMap* task_card_bm);

  // Attempts to mark the given object and, if successful, counts
  // the object in the task/worker counting structures for the
  // given worker id.
  inline bool par_mark_and_count(oop obj, size_t word_size,
                                 HeapRegion* hr, uint worker_id);

  // Attempts to mark the given object and, if successful, counts
  // the object in the task/worker counting structures for the
  // given worker id.
  inline bool par_mark_and_count(oop obj, HeapRegion* hr, uint worker_id);

  // Similar to the above routine but we don't know the heap region that
  // contains the object to be marked/counted, which this routine looks up.
  inline bool par_mark_and_count(oop obj, uint worker_id);

  // Similar to the above routine but there are times when we cannot
  // safely calculate the size of obj due to races and we, therefore,
  // pass the size in as a parameter. It is the caller's responsibility
  // to ensure that the size passed in for obj is valid.
  inline bool par_mark_and_count(oop obj, size_t word_size, uint worker_id);

  // Unconditionally mark the given object, and unconditionally count
  // the object in the counting structures for worker id 0.
  // Should *not* be called from parallel code.
  inline bool mark_and_count(oop obj, HeapRegion* hr);

  // Similar to the above routine but we don't know the heap region that
  // contains the object to be marked/counted, which this routine looks up.
  // Should *not* be called from parallel code.
  inline bool mark_and_count(oop obj);

  // Returns true if initialization was successfully completed.
  bool completed_initialization() const {
    return _completed_initialization;
  }

protected:
  // Clear all the per-task bitmaps and arrays used to store the
  // counting data.
  void clear_all_count_data();

  // Aggregates the counting data for each worker/task
  // that was constructed while marking. Also sets
  // the amount of marked bytes for each region and
  // the top at concurrent mark count.
  void aggregate_count_data();

  // Verification routine
  void verify_count_data();
};

// A class representing a marking task.
class CMTask : public TerminatorTerminator {
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
  ConcurrentMark*             _cm;
  CMBitMap*                   _nextMarkBitMap;
  // the task queue of this task
  CMTaskQueue*                _task_queue;
private:
  // the task queue set---needed for stealing
  CMTaskQueueSet*             _task_queues;
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

  // Counting data structures. Embedding the task's marked_bytes_array
  // and card bitmap into the actual task saves having to go through
  // the ConcurrentMark object.
  size_t*                     _marked_bytes_array;
  BitMap*                     _card_bm;

  // LOTS of statistics related with this task
#if _MARKING_STATS_
  NumberSeq                   _all_clock_intervals_ms;
  double                      _interval_start_time_ms;

  int                         _aborted;
  int                         _aborted_overflow;
  int                         _aborted_cm_aborted;
  int                         _aborted_yield;
  int                         _aborted_timed_out;
  int                         _aborted_satb;
  int                         _aborted_termination;

  int                         _steal_attempts;
  int                         _steals;

  int                         _clock_due_to_marking;
  int                         _clock_due_to_scanning;

  int                         _local_pushes;
  int                         _local_pops;
  int                         _local_max_size;
  int                         _objs_scanned;

  int                         _global_pushes;
  int                         _global_pops;
  int                         _global_max_size;

  int                         _global_transfers_to;
  int                         _global_transfers_from;

  int                         _regions_claimed;
  int                         _objs_found_on_bitmap;

  int                         _satb_buffers_processed;
#endif // _MARKING_STATS_

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

public:
  // It resets the task; it should be called right at the beginning of
  // a marking phase.
  void reset(CMBitMap* _nextMarkBitMap);
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

  // It grays the object by marking it and, if necessary, pushing it
  // on the local queue
  inline void deal_with_reference(oop obj);

  // It scans an object and visits its children.
  void scan_object(oop obj);

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

  CMTask(uint worker_id, ConcurrentMark *cm,
         size_t* marked_bytes, BitMap* card_bm,
         CMTaskQueue* task_queue, CMTaskQueueSet* task_queues);

  // it prints statistics associated with this task
  void print_stats();

#if _MARKING_STATS_
  void increase_objs_found_on_bitmap() { ++_objs_found_on_bitmap; }
#endif // _MARKING_STATS_
};

// Class that's used to to print out per-region liveness
// information. It's currently used at the end of marking and also
// after we sort the old regions at the end of the cleanup operation.
class G1PrintRegionLivenessInfoClosure: public HeapRegionClosure {
private:
  outputStream* _out;

  // Accumulators for these values.
  size_t _total_used_bytes;
  size_t _total_capacity_bytes;
  size_t _total_prev_live_bytes;
  size_t _total_next_live_bytes;

  // These are set up when we come across a "stars humongous" region
  // (as this is where most of this information is stored, not in the
  // subsequent "continues humongous" regions). After that, for every
  // region in a given humongous region series we deduce the right
  // values for it by simply subtracting the appropriate amount from
  // these fields. All these values should reach 0 after we've visited
  // the last region in the series.
  size_t _hum_used_bytes;
  size_t _hum_capacity_bytes;
  size_t _hum_prev_live_bytes;
  size_t _hum_next_live_bytes;

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

  // See the .cpp file.
  size_t get_hum_bytes(size_t* hum_bytes);
  void get_hum_bytes(size_t* used_bytes, size_t* capacity_bytes,
                     size_t* prev_live_bytes, size_t* next_live_bytes);

public:
  // The header and footer are printed in the constructor and
  // destructor respectively.
  G1PrintRegionLivenessInfoClosure(outputStream* out, const char* phase_name);
  virtual bool doHeapRegion(HeapRegion* r);
  ~G1PrintRegionLivenessInfoClosure();
};

#endif // SHARE_VM_GC_IMPLEMENTATION_G1_CONCURRENTMARK_HPP
