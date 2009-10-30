/*
 * Copyright 2001-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

class G1CollectedHeap;
class CMTask;
typedef GenericTaskQueue<oop> CMTaskQueue;
typedef GenericTaskQueueSet<oop> CMTaskQueueSet;

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
  CMBitMapRO(ReservedSpace rs, int shifter);

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
  bool iterate(BitMapClosure* cl) { return _bm.iterate(cl); }
  bool iterate(BitMapClosure* cl, MemRegion mr);

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
  // XXX Fix these so that offsets are size_t's...
  HeapWord* offsetToHeapWord(size_t offset) const {
    return _bmStartWord + (offset << _shifter);
  }
  size_t heapWordToOffset(HeapWord* addr) const {
    return pointer_delta(addr, _bmStartWord) >> _shifter;
  }
  int heapWordDiffToOffsetDiff(size_t diff) const;
  HeapWord* nextWord(HeapWord* addr) {
    return offsetToHeapWord(heapWordToOffset(addr) + 1);
  }

  void mostly_disjoint_range_union(BitMap*   from_bitmap,
                                   size_t    from_start_index,
                                   HeapWord* to_start_word,
                                   size_t    word_num);

  // debugging
  NOT_PRODUCT(bool covers(ReservedSpace rs) const;)
};

class CMBitMap : public CMBitMapRO {

 public:
  // constructor
  CMBitMap(ReservedSpace rs, int shifter) :
    CMBitMapRO(rs, shifter) {}

  // write marks
  void mark(HeapWord* addr) {
    assert(_bmStartWord <= addr && addr < (_bmStartWord + _bmWordSize),
           "outside underlying space?");
    _bm.at_put(heapWordToOffset(addr), true);
  }
  void clear(HeapWord* addr) {
    assert(_bmStartWord <= addr && addr < (_bmStartWord + _bmWordSize),
           "outside underlying space?");
    _bm.at_put(heapWordToOffset(addr), false);
  }
  bool parMark(HeapWord* addr) {
    assert(_bmStartWord <= addr && addr < (_bmStartWord + _bmWordSize),
           "outside underlying space?");
    return _bm.par_at_put(heapWordToOffset(addr), true);
  }
  bool parClear(HeapWord* addr) {
    assert(_bmStartWord <= addr && addr < (_bmStartWord + _bmWordSize),
           "outside underlying space?");
    return _bm.par_at_put(heapWordToOffset(addr), false);
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

// Represents a marking stack used by the CM collector.
// Ideally this should be GrowableArray<> just like MSC's marking stack(s).
class CMMarkStack VALUE_OBJ_CLASS_SPEC {
  ConcurrentMark* _cm;
  oop*   _base;      // bottom of stack
  jint   _index;     // one more than last occupied index
  jint   _capacity;  // max #elements
  jint   _oops_do_bound;  // Number of elements to include in next iteration.
  NOT_PRODUCT(jint _max_depth;)  // max depth plumbed during run

  bool   _overflow;
  DEBUG_ONLY(bool _drain_in_progress;)
  DEBUG_ONLY(bool _drain_in_progress_yields;)

 public:
  CMMarkStack(ConcurrentMark* cm);
  ~CMMarkStack();

  void allocate(size_t size);

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
  int maxElems()    { return _capacity; }

  bool overflow() { return _overflow; }
  void clear_overflow() { _overflow = false; }

  int  size() { return _index; }

  void setEmpty()   { _index = 0; clear_overflow(); }

  // Record the current size; a subsequent "oops_do" will iterate only over
  // indices valid at the time of this call.
  void set_oops_do_bound(jint bound = -1) {
    if (bound == -1) {
      _oops_do_bound = _index;
    } else {
      _oops_do_bound = bound;
    }
  }
  jint oops_do_bound() { return _oops_do_bound; }
  // iterate over the oops in the mark stack, up to the bound recorded via
  // the call above.
  void oops_do(OopClosure* f);
};

class CMRegionStack VALUE_OBJ_CLASS_SPEC {
  MemRegion* _base;
  jint _capacity;
  jint _index;
  jint _oops_do_bound;
  bool _overflow;
public:
  CMRegionStack();
  ~CMRegionStack();
  void allocate(size_t size);

  // This is lock-free; assumes that it will only be called in parallel
  // with other "push" operations (no pops).
  void push(MemRegion mr);

  // Lock-free; assumes that it will only be called in parallel
  // with other "pop" operations (no pushes).
  MemRegion pop();

  bool isEmpty()    { return _index == 0; }
  bool isFull()     { return _index == _capacity; }

  bool overflow() { return _overflow; }
  void clear_overflow() { _overflow = false; }

  int  size() { return _index; }

  // It iterates over the entries in the region stack and it
  // invalidates (i.e. assigns MemRegion()) the ones that point to
  // regions in the collection set.
  bool invalidate_entries_into_cset();

  // This gives an upper bound up to which the iteration in
  // invalidate_entries_into_cset() will reach. This prevents
  // newly-added entries to be unnecessarily scanned.
  void set_oops_do_bound() {
    _oops_do_bound = _index;
  }

  void setEmpty()   { _index = 0; clear_overflow(); }
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


class ConcurrentMarkThread;

class ConcurrentMark: public CHeapObj {
  friend class ConcurrentMarkThread;
  friend class CMTask;
  friend class CMBitMapClosure;
  friend class CSMarkOopClosure;
  friend class CMGlobalObjectClosure;
  friend class CMRemarkTask;
  friend class CMConcurrentMarkingTask;
  friend class G1ParNoteEndTask;
  friend class CalcLiveObjectsClosure;

protected:
  ConcurrentMarkThread* _cmThread;   // the thread doing the work
  G1CollectedHeap*      _g1h;        // the heap.
  size_t                _parallel_marking_threads; // the number of marking
                                                   // threads we'll use
  double                _sleep_factor; // how much we have to sleep, with
                                       // respect to the work we just did, to
                                       // meet the marking overhead goal
  double                _marking_task_overhead; // marking target overhead for
                                                // a single task

  // same as the two above, but for the cleanup task
  double                _cleanup_sleep_factor;
  double                _cleanup_task_overhead;

  // Stuff related to age cohort processing.
  struct ParCleanupThreadState {
    char _pre[64];
    UncleanRegionList list;
    char _post[64];
  };
  ParCleanupThreadState** _par_cleanup_thread_state;

  // CMS marking support structures
  CMBitMap                _markBitMap1;
  CMBitMap                _markBitMap2;
  CMBitMapRO*             _prevMarkBitMap; // completed mark bitmap
  CMBitMap*               _nextMarkBitMap; // under-construction mark bitmap
  bool                    _at_least_one_mark_complete;

  BitMap                  _region_bm;
  BitMap                  _card_bm;

  // Heap bounds
  HeapWord*               _heap_start;
  HeapWord*               _heap_end;

  // For gray objects
  CMMarkStack             _markStack; // Grey objects behind global finger.
  CMRegionStack           _regionStack; // Grey regions behind global finger.
  HeapWord* volatile      _finger;  // the global finger, region aligned,
                                    // always points to the end of the
                                    // last claimed region

  // marking tasks
  size_t                  _max_task_num; // maximum task number
  size_t                  _active_tasks; // task num currently active
  CMTask**                _tasks;        // task queue array (max_task_num len)
  CMTaskQueueSet*         _task_queues;  // task queue set
  ParallelTaskTerminator  _terminator;   // for termination

  // Two sync barriers that are used to synchronise tasks when an
  // overflow occurs. The algorithm is the following. All tasks enter
  // the first one to ensure that they have all stopped manipulating
  // the global data structures. After they exit it, they re-initialise
  // their data structures and task 0 re-initialises the global data
  // structures. Then, they enter the second sync barrier. This
  // ensure, that no task starts doing work before all data
  // structures (local and global) have been re-initialised. When they
  // exit it, they are free to start working again.
  WorkGangBarrierSync     _first_overflow_barrier_sync;
  WorkGangBarrierSync     _second_overflow_barrier_sync;


  // this is set by any task, when an overflow on the global data
  // structures is detected.
  volatile bool           _has_overflown;
  // true: marking is concurrent, false: we're in remark
  volatile bool           _concurrent;
  // set at the end of a Full GC so that marking aborts
  volatile bool           _has_aborted;
  // used when remark aborts due to an overflow to indicate that
  // another concurrent marking phase should start
  volatile bool           _restart_for_overflow;

  // This is true from the very start of concurrent marking until the
  // point when all the tasks complete their work. It is really used
  // to determine the points between the end of concurrent marking and
  // time of remark.
  volatile bool           _concurrent_marking_in_progress;

  // verbose level
  CMVerboseLevel          _verbose_level;

  // These two fields are used to implement the optimisation that
  // avoids pushing objects on the global/region stack if there are
  // no collection set regions above the lowest finger.

  // This is the lowest finger (among the global and local fingers),
  // which is calculated before a new collection set is chosen.
  HeapWord* _min_finger;
  // If this flag is true, objects/regions that are marked below the
  // finger should be pushed on the stack(s). If this is flag is
  // false, it is safe not to push them on the stack(s).
  bool      _should_gray_objects;

  // All of these times are in ms.
  NumberSeq _init_times;
  NumberSeq _remark_times;
  NumberSeq   _remark_mark_times;
  NumberSeq   _remark_weak_ref_times;
  NumberSeq _cleanup_times;
  double    _total_counting_time;
  double    _total_rs_scrub_time;

  double*   _accum_task_vtime;   // accumulated task vtime

  WorkGang* _parallel_workers;

  void weakRefsWork(bool clear_all_soft_refs);

  void swapMarkBitMaps();

  // It resets the global marking data structures, as well as the
  // task local ones; should be called during initial mark.
  void reset();
  // It resets all the marking data structures.
  void clear_marking_state();

  // It should be called to indicate which phase we're in (concurrent
  // mark or remark) and how many threads are currently active.
  void set_phase(size_t active_tasks, bool concurrent);
  // We do this after we're done with marking so that the marking data
  // structures are initialised to a sensible and predictable state.
  void set_non_marking_state();

  // prints all gathered CM-related statistics
  void print_stats();

  // accessor methods
  size_t parallel_marking_threads() { return _parallel_marking_threads; }
  double sleep_factor()             { return _sleep_factor; }
  double marking_task_overhead()    { return _marking_task_overhead;}
  double cleanup_sleep_factor()     { return _cleanup_sleep_factor; }
  double cleanup_task_overhead()    { return _cleanup_task_overhead;}

  HeapWord*               finger()        { return _finger;   }
  bool                    concurrent()    { return _concurrent; }
  size_t                  active_tasks()  { return _active_tasks; }
  ParallelTaskTerminator* terminator()    { return &_terminator; }

  // It claims the next available region to be scanned by a marking
  // task. It might return NULL if the next region is empty or we have
  // run out of regions. In the latter case, out_of_regions()
  // determines whether we've really run out of regions or the task
  // should call claim_region() again.  This might seem a bit
  // awkward. Originally, the code was written so that claim_region()
  // either successfully returned with a non-empty region or there
  // were no more regions to be claimed. The problem with this was
  // that, in certain circumstances, it iterated over large chunks of
  // the heap finding only empty regions and, while it was working, it
  // was preventing the calling task to call its regular clock
  // method. So, this way, each task will spend very little time in
  // claim_region() and is allowed to call the regular clock method
  // frequently.
  HeapRegion* claim_region(int task);

  // It determines whether we've run out of regions to scan.
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
  // indicate that the global stack or region stack has overflown
  bool has_overflown()           { return _has_overflown; }
  void set_has_overflown()       { _has_overflown = true; }
  void clear_has_overflown()     { _has_overflown = false; }

  bool has_aborted()             { return _has_aborted; }
  bool restart_for_overflow()    { return _restart_for_overflow; }

  // Methods to enter the two overflow sync barriers
  void enter_first_sync_barrier(int task_num);
  void enter_second_sync_barrier(int task_num);

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
  size_t mark_stack_size()              { return _markStack.size(); }
  size_t partial_mark_stack_size_target() { return _markStack.maxElems()/3; }
  bool mark_stack_overflow()            { return _markStack.overflow(); }
  bool mark_stack_empty()               { return _markStack.isEmpty(); }

  // Manipulation of the region stack
  bool region_stack_push(MemRegion mr) {
    _regionStack.push(mr);
    if (_regionStack.overflow()) {
      set_has_overflown();
      return false;
    }
    return true;
  }
  MemRegion region_stack_pop()          { return _regionStack.pop(); }
  int region_stack_size()               { return _regionStack.size(); }
  bool region_stack_overflow()          { return _regionStack.overflow(); }
  bool region_stack_empty()             { return _regionStack.isEmpty(); }

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
    for (int i = 0; i < (int)_max_task_num; ++i)
      ret += _accum_task_vtime[i];
    return ret;
  }

  // Attempts to steal an object from the task queues of other tasks
  bool try_stealing(int task_num, int* hash_seed, oop& obj) {
    return _task_queues->steal(task_num, hash_seed, obj);
  }

  // It grays an object by first marking it. Then, if it's behind the
  // global finger, it also pushes it on the global stack.
  void deal_with_reference(oop obj);

  ConcurrentMark(ReservedSpace rs, int max_regions);
  ~ConcurrentMark();
  ConcurrentMarkThread* cmThread() { return _cmThread; }

  CMBitMapRO* prevMarkBitMap() const { return _prevMarkBitMap; }
  CMBitMap*   nextMarkBitMap() const { return _nextMarkBitMap; }

  // The following three are interaction between CM and
  // G1CollectedHeap

  // This notifies CM that a root during initial-mark needs to be
  // grayed and it's MT-safe. Currently, we just mark it. But, in the
  // future, we can experiment with pushing it on the stack and we can
  // do this without changing G1CollectedHeap.
  void grayRoot(oop p);
  // It's used during evacuation pauses to gray a region, if
  // necessary, and it's MT-safe. It assumes that the caller has
  // marked any objects on that region. If _should_gray_objects is
  // true and we're still doing concurrent marking, the region is
  // pushed on the region stack, if it is located below the global
  // finger, otherwise we do nothing.
  void grayRegionIfNecessary(MemRegion mr);
  // It's used during evacuation pauses to mark and, if necessary,
  // gray a single object and it's MT-safe. It assumes the caller did
  // not mark the object. If _should_gray_objects is true and we're
  // still doing concurrent marking, the objects is pushed on the
  // global stack, if it is located below the global finger, otherwise
  // we do nothing.
  void markAndGrayObjectIfNecessary(oop p);

  // This iterates over the marking bitmap (either prev or next) and
  // prints out all objects that are marked on the bitmap and indicates
  // whether what they point to is also marked or not. It also iterates
  // the objects over TAMS (either prev or next).
  void print_reachable(bool use_prev_marking, const char* str);

  // Clear the next marking bitmap (will be called concurrently).
  void clearNextBitmap();

  // main CMS steps and related support
  void checkpointRootsInitial();

  // These two do the work that needs to be done before and after the
  // initial root checkpoint. Since this checkpoint can be done at two
  // different points (i.e. an explicit pause or piggy-backed on a
  // young collection), then it's nice to be able to easily share the
  // pre/post code. It might be the case that we can put everything in
  // the post method. TP
  void checkpointRootsInitialPre();
  void checkpointRootsInitialPost();

  // Do concurrent phase of marking, to a tentative transitive closure.
  void markFromRoots();

  // Process all unprocessed SATB buffers. It is called at the
  // beginning of an evacuation pause.
  void drainAllSATBBuffers();

  void checkpointRootsFinal(bool clear_all_soft_refs);
  void checkpointRootsFinalWork();
  void calcDesiredRegions();
  void cleanup();
  void completeCleanup();

  // Mark in the previous bitmap.  NB: this is usually read-only, so use
  // this carefully!
  void markPrev(oop p);
  void clear(oop p);
  // Clears marks for all objects in the given range, for both prev and
  // next bitmaps.  NB: the previous bitmap is usually read-only, so use
  // this carefully!
  void clearRangeBothMaps(MemRegion mr);

  // Record the current top of the mark and region stacks; a
  // subsequent oops_do() on the mark stack and
  // invalidate_entries_into_cset() on the region stack will iterate
  // only over indices valid at the time of this call.
  void set_oops_do_bound() {
    _markStack.set_oops_do_bound();
    _regionStack.set_oops_do_bound();
  }
  // Iterate over the oops in the mark stack and all local queues. It
  // also calls invalidate_entries_into_cset() on the region stack.
  void oops_do(OopClosure* f);
  // It is called at the end of an evacuation pause during marking so
  // that CM is notified of where the new end of the heap is. It
  // doesn't do anything if concurrent_marking_in_progress() is false,
  // unless the force parameter is true.
  void update_g1_committed(bool force = false);

  void complete_marking_in_collection_set();

  // It indicates that a new collection set is being chosen.
  void newCSet();
  // It registers a collection set heap region with CM. This is used
  // to determine whether any heap regions are located above the finger.
  void registerCSetRegion(HeapRegion* hr);

  // Returns "true" if at least one mark has been completed.
  bool at_least_one_mark_complete() { return _at_least_one_mark_complete; }

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

  inline bool do_yield_check(int worker_i = 0);
  inline bool should_yield();

  // Called to abort the marking cycle after a Full GC takes palce.
  void abort();

  // This prints the global/local fingers. It is used for debugging.
  NOT_PRODUCT(void print_finger();)

  void print_summary_info();

  void print_worker_threads_on(outputStream* st) const;

  // The following indicate whether a given verbose level has been
  // set. Notice that anything above stats is conditional to
  // _MARKING_VERBOSE_ having been set to 1
  bool verbose_stats()
    { return _verbose_level >= stats_verbose; }
  bool verbose_low()
    { return _MARKING_VERBOSE_ && _verbose_level >= low_verbose; }
  bool verbose_medium()
    { return _MARKING_VERBOSE_ && _verbose_level >= medium_verbose; }
  bool verbose_high()
    { return _MARKING_VERBOSE_ && _verbose_level >= high_verbose; }
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

  int                         _task_id;
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
  OopClosure*                 _oop_closure;

  // the region this task is scanning, NULL if we're not scanning any
  HeapRegion*                 _curr_region;
  // the local finger of this task, NULL if we're not scanning a region
  HeapWord*                   _finger;
  // limit of the region this task is scanning, NULL if we're not scanning one
  HeapWord*                   _region_limit;

  // This is used only when we scan regions popped from the region
  // stack. It records what the last object on such a region we
  // scanned was. It is used to ensure that, if we abort region
  // iteration, we do not rescan the first part of the region. This
  // should be NULL when we're not scanning a region from the region
  // stack.
  HeapWord*                   _region_finger;

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
  bool                        _has_aborted_timed_out;
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

  int                         _region_stack_pops;

  int                         _regions_claimed;
  int                         _objs_found_on_bitmap;

  int                         _satb_buffers_processed;
#endif // _MARKING_STATS_

  // it updates the local fields after this task has claimed
  // a new region to scan
  void setup_for_region(HeapRegion* hr);
  // it brings up-to-date the limit of the region
  void update_region_limit();
  // it resets the local fields after a task has finished scanning a
  // region
  void giveup_current_region();

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
        _refs_reached >= _refs_reached_limit)
      reached_limit();
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
  void do_marking_step(double target_ms);

  // These two calls start and stop the timer
  void record_start_time() {
    _elapsed_time_ms = os::elapsedTime() * 1000.0;
  }
  void record_end_time() {
    _elapsed_time_ms = os::elapsedTime() * 1000.0 - _elapsed_time_ms;
  }

  // returns the task ID
  int task_id() { return _task_id; }

  // From TerminatorTerminator. It determines whether this task should
  // exit the termination protocol after it's entered it.
  virtual bool should_exit_termination();

  HeapWord* finger()            { return _finger; }

  bool has_aborted()            { return _has_aborted; }
  void set_has_aborted()        { _has_aborted = true; }
  void clear_has_aborted()      { _has_aborted = false; }
  bool claimed() { return _claimed; }

  void set_oop_closure(OopClosure* oop_closure) {
    _oop_closure = oop_closure;
  }

  // It grays the object by marking it and, if necessary, pushing it
  // on the local queue
  void deal_with_reference(oop obj);

  // It scans an object and visits its children.
  void scan_object(oop obj) {
    assert(_nextMarkBitMap->isMarked((HeapWord*) obj), "invariant");

    if (_cm->verbose_high())
      gclog_or_tty->print_cr("[%d] we're scanning object "PTR_FORMAT,
                             _task_id, (void*) obj);

    size_t obj_size = obj->size();
    _words_scanned += obj_size;

    obj->oop_iterate(_oop_closure);
    statsOnly( ++_objs_scanned );
    check_limits();
  }

  // It pushes an object on the local queue.
  void push(oop obj);

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
  // It keeps popping regions from the region stack and processing
  // them until the region stack is empty.
  void drain_region_stack(BitMapClosure* closure);

  // moves the local finger to a new location
  inline void move_finger_to(HeapWord* new_finger) {
    assert(new_finger >= _finger && new_finger < _region_limit, "invariant");
    _finger = new_finger;
  }

  // moves the region finger to a new location
  inline void move_region_finger_to(HeapWord* new_finger) {
    assert(new_finger < _cm->finger(), "invariant");
    _region_finger = new_finger;
  }

  CMTask(int task_num, ConcurrentMark *cm,
         CMTaskQueue* task_queue, CMTaskQueueSet* task_queues);

  // it prints statistics associated with this task
  void print_stats();

#if _MARKING_STATS_
  void increase_objs_found_on_bitmap() { ++_objs_found_on_bitmap; }
#endif // _MARKING_STATS_
};
