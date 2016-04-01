/*
 * Copyright (c) 2001, 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_CMS_CONCURRENTMARKSWEEPGENERATION_HPP
#define SHARE_VM_GC_CMS_CONCURRENTMARKSWEEPGENERATION_HPP

#include "gc/cms/cmsOopClosures.hpp"
#include "gc/cms/gSpaceCounters.hpp"
#include "gc/cms/yieldingWorkgroup.hpp"
#include "gc/shared/cardGeneration.hpp"
#include "gc/shared/gcHeapSummary.hpp"
#include "gc/shared/gcStats.hpp"
#include "gc/shared/gcWhen.hpp"
#include "gc/shared/generationCounters.hpp"
#include "gc/shared/space.hpp"
#include "gc/shared/taskqueue.hpp"
#include "logging/log.hpp"
#include "memory/freeBlockDictionary.hpp"
#include "memory/iterator.hpp"
#include "memory/virtualspace.hpp"
#include "runtime/mutexLocker.hpp"
#include "services/memoryService.hpp"
#include "utilities/bitMap.hpp"
#include "utilities/stack.hpp"

// ConcurrentMarkSweepGeneration is in support of a concurrent
// mark-sweep old generation in the Detlefs-Printezis--Boehm-Demers-Schenker
// style. We assume, for now, that this generation is always the
// seniormost generation and for simplicity
// in the first implementation, that this generation is a single compactible
// space. Neither of these restrictions appears essential, and will be
// relaxed in the future when more time is available to implement the
// greater generality (and there's a need for it).
//
// Concurrent mode failures are currently handled by
// means of a sliding mark-compact.

class AdaptiveSizePolicy;
class CMSCollector;
class CMSConcMarkingTask;
class CMSGCAdaptivePolicyCounters;
class CMSTracer;
class ConcurrentGCTimer;
class ConcurrentMarkSweepGeneration;
class ConcurrentMarkSweepPolicy;
class ConcurrentMarkSweepThread;
class CompactibleFreeListSpace;
class FreeChunk;
class ParNewGeneration;
class PromotionInfo;
class ScanMarkedObjectsAgainCarefullyClosure;
class TenuredGeneration;
class SerialOldTracer;

// A generic CMS bit map. It's the basis for both the CMS marking bit map
// as well as for the mod union table (in each case only a subset of the
// methods are used). This is essentially a wrapper around the BitMap class,
// with one bit per (1<<_shifter) HeapWords. (i.e. for the marking bit map,
// we have _shifter == 0. and for the mod union table we have
// shifter == CardTableModRefBS::card_shift - LogHeapWordSize.)
// XXX 64-bit issues in BitMap?
class CMSBitMap VALUE_OBJ_CLASS_SPEC {
  friend class VMStructs;

  HeapWord* _bmStartWord;   // base address of range covered by map
  size_t    _bmWordSize;    // map size (in #HeapWords covered)
  const int _shifter;       // shifts to convert HeapWord to bit position
  VirtualSpace _virtual_space; // underlying the bit map
  BitMap    _bm;            // the bit map itself
 public:
  Mutex* const _lock;       // mutex protecting _bm;

 public:
  // constructor
  CMSBitMap(int shifter, int mutex_rank, const char* mutex_name);

  // allocates the actual storage for the map
  bool allocate(MemRegion mr);
  // field getter
  Mutex* lock() const { return _lock; }
  // locking verifier convenience function
  void assert_locked() const PRODUCT_RETURN;

  // inquiries
  HeapWord* startWord()   const { return _bmStartWord; }
  size_t    sizeInWords() const { return _bmWordSize;  }
  size_t    sizeInBits()  const { return _bm.size();   }
  // the following is one past the last word in space
  HeapWord* endWord()     const { return _bmStartWord + _bmWordSize; }

  // reading marks
  bool isMarked(HeapWord* addr) const;
  bool par_isMarked(HeapWord* addr) const; // do not lock checks
  bool isUnmarked(HeapWord* addr) const;
  bool isAllClear() const;

  // writing marks
  void mark(HeapWord* addr);
  // For marking by parallel GC threads;
  // returns true if we did, false if another thread did
  bool par_mark(HeapWord* addr);

  void mark_range(MemRegion mr);
  void par_mark_range(MemRegion mr);
  void mark_large_range(MemRegion mr);
  void par_mark_large_range(MemRegion mr);
  void par_clear(HeapWord* addr); // For unmarking by parallel GC threads.
  void clear_range(MemRegion mr);
  void par_clear_range(MemRegion mr);
  void clear_large_range(MemRegion mr);
  void par_clear_large_range(MemRegion mr);
  void clear_all();
  void clear_all_incrementally();  // Not yet implemented!!

  NOT_PRODUCT(
    // checks the memory region for validity
    void region_invariant(MemRegion mr);
  )

  // iteration
  void iterate(BitMapClosure* cl) {
    _bm.iterate(cl);
  }
  void iterate(BitMapClosure* cl, HeapWord* left, HeapWord* right);
  void dirty_range_iterate_clear(MemRegionClosure* cl);
  void dirty_range_iterate_clear(MemRegion mr, MemRegionClosure* cl);

  // auxiliary support for iteration
  HeapWord* getNextMarkedWordAddress(HeapWord* addr) const;
  HeapWord* getNextMarkedWordAddress(HeapWord* start_addr,
                                            HeapWord* end_addr) const;
  HeapWord* getNextUnmarkedWordAddress(HeapWord* addr) const;
  HeapWord* getNextUnmarkedWordAddress(HeapWord* start_addr,
                                              HeapWord* end_addr) const;
  MemRegion getAndClearMarkedRegion(HeapWord* addr);
  MemRegion getAndClearMarkedRegion(HeapWord* start_addr,
                                           HeapWord* end_addr);

  // conversion utilities
  HeapWord* offsetToHeapWord(size_t offset) const;
  size_t    heapWordToOffset(HeapWord* addr) const;
  size_t    heapWordDiffToOffsetDiff(size_t diff) const;

  void print_on_error(outputStream* st, const char* prefix) const;

  // debugging
  // is this address range covered by the bit-map?
  NOT_PRODUCT(
    bool covers(MemRegion mr) const;
    bool covers(HeapWord* start, size_t size = 0) const;
  )
  void verifyNoOneBitsInRange(HeapWord* left, HeapWord* right) PRODUCT_RETURN;
};

// Represents a marking stack used by the CMS collector.
// Ideally this should be GrowableArray<> just like MSC's marking stack(s).
class CMSMarkStack: public CHeapObj<mtGC>  {
  friend class CMSCollector;   // To get at expansion stats further below.

  VirtualSpace _virtual_space;  // Space for the stack
  oop*   _base;      // Bottom of stack
  size_t _index;     // One more than last occupied index
  size_t _capacity;  // Max #elements
  Mutex  _par_lock;  // An advisory lock used in case of parallel access
  NOT_PRODUCT(size_t _max_depth;)  // Max depth plumbed during run

 protected:
  size_t _hit_limit;      // We hit max stack size limit
  size_t _failed_double;  // We failed expansion before hitting limit

 public:
  CMSMarkStack():
    _par_lock(Mutex::event, "CMSMarkStack._par_lock", true,
              Monitor::_safepoint_check_never),
    _hit_limit(0),
    _failed_double(0) {}

  bool allocate(size_t size);

  size_t capacity() const { return _capacity; }

  oop pop() {
    if (!isEmpty()) {
      return _base[--_index] ;
    }
    return NULL;
  }

  bool push(oop ptr) {
    if (isFull()) {
      return false;
    } else {
      _base[_index++] = ptr;
      NOT_PRODUCT(_max_depth = MAX2(_max_depth, _index));
      return true;
    }
  }

  bool isEmpty() const { return _index == 0; }
  bool isFull()  const {
    assert(_index <= _capacity, "buffer overflow");
    return _index == _capacity;
  }

  size_t length() { return _index; }

  // "Parallel versions" of some of the above
  oop par_pop() {
    // lock and pop
    MutexLockerEx x(&_par_lock, Mutex::_no_safepoint_check_flag);
    return pop();
  }

  bool par_push(oop ptr) {
    // lock and push
    MutexLockerEx x(&_par_lock, Mutex::_no_safepoint_check_flag);
    return push(ptr);
  }

  // Forcibly reset the stack, losing all of its contents.
  void reset() {
    _index = 0;
  }

  // Expand the stack, typically in response to an overflow condition.
  void expand();

  // Compute the least valued stack element.
  oop least_value(HeapWord* low) {
     oop least = (oop)low;
     for (size_t i = 0; i < _index; i++) {
       least = MIN2(least, _base[i]);
     }
     return least;
  }

  // Exposed here to allow stack expansion in || case.
  Mutex* par_lock() { return &_par_lock; }
};

class CardTableRS;
class CMSParGCThreadState;

class ModUnionClosure: public MemRegionClosure {
 protected:
  CMSBitMap* _t;
 public:
  ModUnionClosure(CMSBitMap* t): _t(t) { }
  void do_MemRegion(MemRegion mr);
};

class ModUnionClosurePar: public ModUnionClosure {
 public:
  ModUnionClosurePar(CMSBitMap* t): ModUnionClosure(t) { }
  void do_MemRegion(MemRegion mr);
};

// Survivor Chunk Array in support of parallelization of
// Survivor Space rescan.
class ChunkArray: public CHeapObj<mtGC> {
  size_t _index;
  size_t _capacity;
  size_t _overflows;
  HeapWord** _array;   // storage for array

 public:
  ChunkArray() : _index(0), _capacity(0), _overflows(0), _array(NULL) {}
  ChunkArray(HeapWord** a, size_t c):
    _index(0), _capacity(c), _overflows(0), _array(a) {}

  HeapWord** array() { return _array; }
  void set_array(HeapWord** a) { _array = a; }

  size_t capacity() { return _capacity; }
  void set_capacity(size_t c) { _capacity = c; }

  size_t end() {
    assert(_index <= capacity(),
           "_index (" SIZE_FORMAT ") > _capacity (" SIZE_FORMAT "): out of bounds",
           _index, _capacity);
    return _index;
  }  // exclusive

  HeapWord* nth(size_t n) {
    assert(n < end(), "Out of bounds access");
    return _array[n];
  }

  void reset() {
    _index = 0;
    if (_overflows > 0) {
      log_trace(gc)("CMS: ChunkArray[" SIZE_FORMAT "] overflowed " SIZE_FORMAT " times", _capacity, _overflows);
    }
    _overflows = 0;
  }

  void record_sample(HeapWord* p, size_t sz) {
    // For now we do not do anything with the size
    if (_index < _capacity) {
      _array[_index++] = p;
    } else {
      ++_overflows;
      assert(_index == _capacity,
             "_index (" SIZE_FORMAT ") > _capacity (" SIZE_FORMAT
             "): out of bounds at overflow#" SIZE_FORMAT,
             _index, _capacity, _overflows);
    }
  }
};

//
// Timing, allocation and promotion statistics for gc scheduling and incremental
// mode pacing.  Most statistics are exponential averages.
//
class CMSStats VALUE_OBJ_CLASS_SPEC {
 private:
  ConcurrentMarkSweepGeneration* const _cms_gen;   // The cms (old) gen.

  // The following are exponential averages with factor alpha:
  //   avg = (100 - alpha) * avg + alpha * cur_sample
  //
  //   The durations measure:  end_time[n] - start_time[n]
  //   The periods measure:    start_time[n] - start_time[n-1]
  //
  // The cms period and duration include only concurrent collections; time spent
  // in foreground cms collections due to System.gc() or because of a failure to
  // keep up are not included.
  //
  // There are 3 alphas to "bootstrap" the statistics.  The _saved_alpha is the
  // real value, but is used only after the first period.  A value of 100 is
  // used for the first sample so it gets the entire weight.
  unsigned int _saved_alpha; // 0-100
  unsigned int _gc0_alpha;
  unsigned int _cms_alpha;

  double _gc0_duration;
  double _gc0_period;
  size_t _gc0_promoted;         // bytes promoted per gc0
  double _cms_duration;
  double _cms_duration_pre_sweep; // time from initiation to start of sweep
  double _cms_period;
  size_t _cms_allocated;        // bytes of direct allocation per gc0 period

  // Timers.
  elapsedTimer _cms_timer;
  TimeStamp    _gc0_begin_time;
  TimeStamp    _cms_begin_time;
  TimeStamp    _cms_end_time;

  // Snapshots of the amount used in the CMS generation.
  size_t _cms_used_at_gc0_begin;
  size_t _cms_used_at_gc0_end;
  size_t _cms_used_at_cms_begin;

  // Used to prevent the duty cycle from being reduced in the middle of a cms
  // cycle.
  bool _allow_duty_cycle_reduction;

  enum {
    _GC0_VALID = 0x1,
    _CMS_VALID = 0x2,
    _ALL_VALID = _GC0_VALID | _CMS_VALID
  };

  unsigned int _valid_bits;

 protected:
  // In support of adjusting of cms trigger ratios based on history
  // of concurrent mode failure.
  double cms_free_adjustment_factor(size_t free) const;
  void   adjust_cms_free_adjustment_factor(bool fail, size_t free);

 public:
  CMSStats(ConcurrentMarkSweepGeneration* cms_gen,
           unsigned int alpha = CMSExpAvgFactor);

  // Whether or not the statistics contain valid data; higher level statistics
  // cannot be called until this returns true (they require at least one young
  // gen and one cms cycle to have completed).
  bool valid() const;

  // Record statistics.
  void record_gc0_begin();
  void record_gc0_end(size_t cms_gen_bytes_used);
  void record_cms_begin();
  void record_cms_end();

  // Allow management of the cms timer, which must be stopped/started around
  // yield points.
  elapsedTimer& cms_timer()     { return _cms_timer; }
  void start_cms_timer()        { _cms_timer.start(); }
  void stop_cms_timer()         { _cms_timer.stop(); }

  // Basic statistics; units are seconds or bytes.
  double gc0_period() const     { return _gc0_period; }
  double gc0_duration() const   { return _gc0_duration; }
  size_t gc0_promoted() const   { return _gc0_promoted; }
  double cms_period() const          { return _cms_period; }
  double cms_duration() const        { return _cms_duration; }
  size_t cms_allocated() const       { return _cms_allocated; }

  size_t cms_used_at_gc0_end() const { return _cms_used_at_gc0_end;}

  // Seconds since the last background cms cycle began or ended.
  double cms_time_since_begin() const;
  double cms_time_since_end() const;

  // Higher level statistics--caller must check that valid() returns true before
  // calling.

  // Returns bytes promoted per second of wall clock time.
  double promotion_rate() const;

  // Returns bytes directly allocated per second of wall clock time.
  double cms_allocation_rate() const;

  // Rate at which space in the cms generation is being consumed (sum of the
  // above two).
  double cms_consumption_rate() const;

  // Returns an estimate of the number of seconds until the cms generation will
  // fill up, assuming no collection work is done.
  double time_until_cms_gen_full() const;

  // Returns an estimate of the number of seconds remaining until
  // the cms generation collection should start.
  double time_until_cms_start() const;

  // End of higher level statistics.

  // Debugging.
  void print_on(outputStream* st) const PRODUCT_RETURN;
  void print() const { print_on(tty); }
};

// A closure related to weak references processing which
// we embed in the CMSCollector, since we need to pass
// it to the reference processor for secondary filtering
// of references based on reachability of referent;
// see role of _is_alive_non_header closure in the
// ReferenceProcessor class.
// For objects in the CMS generation, this closure checks
// if the object is "live" (reachable). Used in weak
// reference processing.
class CMSIsAliveClosure: public BoolObjectClosure {
  const MemRegion  _span;
  const CMSBitMap* _bit_map;

  friend class CMSCollector;
 public:
  CMSIsAliveClosure(MemRegion span,
                    CMSBitMap* bit_map):
    _span(span),
    _bit_map(bit_map) {
    assert(!span.is_empty(), "Empty span could spell trouble");
  }

  bool do_object_b(oop obj);
};


// Implements AbstractRefProcTaskExecutor for CMS.
class CMSRefProcTaskExecutor: public AbstractRefProcTaskExecutor {
public:

  CMSRefProcTaskExecutor(CMSCollector& collector)
    : _collector(collector)
  { }

  // Executes a task using worker threads.
  virtual void execute(ProcessTask& task);
  virtual void execute(EnqueueTask& task);
private:
  CMSCollector& _collector;
};


class CMSCollector: public CHeapObj<mtGC> {
  friend class VMStructs;
  friend class ConcurrentMarkSweepThread;
  friend class ConcurrentMarkSweepGeneration;
  friend class CompactibleFreeListSpace;
  friend class CMSParMarkTask;
  friend class CMSParInitialMarkTask;
  friend class CMSParRemarkTask;
  friend class CMSConcMarkingTask;
  friend class CMSRefProcTaskProxy;
  friend class CMSRefProcTaskExecutor;
  friend class ScanMarkedObjectsAgainCarefullyClosure;  // for sampling eden
  friend class SurvivorSpacePrecleanClosure;            // --- ditto -------
  friend class PushOrMarkClosure;             // to access _restart_addr
  friend class ParPushOrMarkClosure;          // to access _restart_addr
  friend class MarkFromRootsClosure;          //  -- ditto --
                                              // ... and for clearing cards
  friend class ParMarkFromRootsClosure;       //  to access _restart_addr
                                              // ... and for clearing cards
  friend class ParConcMarkingClosure;         //  to access _restart_addr etc.
  friend class MarkFromRootsVerifyClosure;    // to access _restart_addr
  friend class PushAndMarkVerifyClosure;      //  -- ditto --
  friend class MarkRefsIntoAndScanClosure;    // to access _overflow_list
  friend class PushAndMarkClosure;            //  -- ditto --
  friend class ParPushAndMarkClosure;         //  -- ditto --
  friend class CMSKeepAliveClosure;           //  -- ditto --
  friend class CMSDrainMarkingStackClosure;   //  -- ditto --
  friend class CMSInnerParMarkAndPushClosure; //  -- ditto --
  NOT_PRODUCT(friend class ScanMarkedObjectsAgainClosure;) //  assertion on _overflow_list
  friend class ReleaseForegroundGC;  // to access _foregroundGCShouldWait
  friend class VM_CMS_Operation;
  friend class VM_CMS_Initial_Mark;
  friend class VM_CMS_Final_Remark;
  friend class TraceCMSMemoryManagerStats;

 private:
  jlong _time_of_last_gc;
  void update_time_of_last_gc(jlong now) {
    _time_of_last_gc = now;
  }

  OopTaskQueueSet* _task_queues;

  // Overflow list of grey objects, threaded through mark-word
  // Manipulated with CAS in the parallel/multi-threaded case.
  oop _overflow_list;
  // The following array-pair keeps track of mark words
  // displaced for accommodating overflow list above.
  // This code will likely be revisited under RFE#4922830.
  Stack<oop, mtGC>     _preserved_oop_stack;
  Stack<markOop, mtGC> _preserved_mark_stack;

  int*             _hash_seed;

  // In support of multi-threaded concurrent phases
  YieldingFlexibleWorkGang* _conc_workers;

  // Performance Counters
  CollectorCounters* _gc_counters;
  CollectorCounters* _cgc_counters;

  // Initialization Errors
  bool _completed_initialization;

  // In support of ExplicitGCInvokesConcurrent
  static bool _full_gc_requested;
  static GCCause::Cause _full_gc_cause;
  unsigned int _collection_count_start;

  // Should we unload classes this concurrent cycle?
  bool _should_unload_classes;
  unsigned int  _concurrent_cycles_since_last_unload;
  unsigned int concurrent_cycles_since_last_unload() const {
    return _concurrent_cycles_since_last_unload;
  }
  // Did we (allow) unload classes in the previous concurrent cycle?
  bool unloaded_classes_last_cycle() const {
    return concurrent_cycles_since_last_unload() == 0;
  }
  // Root scanning options for perm gen
  int _roots_scanning_options;
  int roots_scanning_options() const      { return _roots_scanning_options; }
  void add_root_scanning_option(int o)    { _roots_scanning_options |= o;   }
  void remove_root_scanning_option(int o) { _roots_scanning_options &= ~o;  }

  // Verification support
  CMSBitMap     _verification_mark_bm;
  void verify_after_remark_work_1();
  void verify_after_remark_work_2();

  // True if any verification flag is on.
  bool _verifying;
  bool verifying() const { return _verifying; }
  void set_verifying(bool v) { _verifying = v; }

  // Collector policy
  ConcurrentMarkSweepPolicy* _collector_policy;
  ConcurrentMarkSweepPolicy* collector_policy() { return _collector_policy; }

  void set_did_compact(bool v);

  // XXX Move these to CMSStats ??? FIX ME !!!
  elapsedTimer _inter_sweep_timer;   // Time between sweeps
  elapsedTimer _intra_sweep_timer;   // Time _in_ sweeps
  // Padded decaying average estimates of the above
  AdaptivePaddedAverage _inter_sweep_estimate;
  AdaptivePaddedAverage _intra_sweep_estimate;

  CMSTracer* _gc_tracer_cm;
  ConcurrentGCTimer* _gc_timer_cm;

  bool _cms_start_registered;

  GCHeapSummary _last_heap_summary;
  MetaspaceSummary _last_metaspace_summary;

  void register_gc_start(GCCause::Cause cause);
  void register_gc_end();
  void save_heap_summary();
  void report_heap_summary(GCWhen::Type when);

 protected:
  ConcurrentMarkSweepGeneration* _cmsGen;  // Old gen (CMS)
  MemRegion                      _span;    // Span covering above two
  CardTableRS*                   _ct;      // Card table

  // CMS marking support structures
  CMSBitMap     _markBitMap;
  CMSBitMap     _modUnionTable;
  CMSMarkStack  _markStack;

  HeapWord*     _restart_addr; // In support of marking stack overflow
  void          lower_restart_addr(HeapWord* low);

  // Counters in support of marking stack / work queue overflow handling:
  // a non-zero value indicates certain types of overflow events during
  // the current CMS cycle and could lead to stack resizing efforts at
  // an opportune future time.
  size_t        _ser_pmc_preclean_ovflw;
  size_t        _ser_pmc_remark_ovflw;
  size_t        _par_pmc_remark_ovflw;
  size_t        _ser_kac_preclean_ovflw;
  size_t        _ser_kac_ovflw;
  size_t        _par_kac_ovflw;
  NOT_PRODUCT(ssize_t _num_par_pushes;)

  // ("Weak") Reference processing support.
  ReferenceProcessor*            _ref_processor;
  CMSIsAliveClosure              _is_alive_closure;
  // Keep this textually after _markBitMap and _span; c'tor dependency.

  ConcurrentMarkSweepThread*     _cmsThread;   // The thread doing the work
  ModUnionClosurePar _modUnionClosurePar;

  // CMS abstract state machine
  // initial_state: Idling
  // next_state(Idling)            = {Marking}
  // next_state(Marking)           = {Precleaning, Sweeping}
  // next_state(Precleaning)       = {AbortablePreclean, FinalMarking}
  // next_state(AbortablePreclean) = {FinalMarking}
  // next_state(FinalMarking)      = {Sweeping}
  // next_state(Sweeping)          = {Resizing}
  // next_state(Resizing)          = {Resetting}
  // next_state(Resetting)         = {Idling}
  // The numeric values below are chosen so that:
  // . _collectorState <= Idling ==  post-sweep && pre-mark
  // . _collectorState in (Idling, Sweeping) == {initial,final}marking ||
  //                                            precleaning || abortablePrecleanb
 public:
  enum CollectorState {
    Resizing            = 0,
    Resetting           = 1,
    Idling              = 2,
    InitialMarking      = 3,
    Marking             = 4,
    Precleaning         = 5,
    AbortablePreclean   = 6,
    FinalMarking        = 7,
    Sweeping            = 8
  };
 protected:
  static CollectorState _collectorState;

  // State related to prologue/epilogue invocation for my generations
  bool _between_prologue_and_epilogue;

  // Signaling/State related to coordination between fore- and background GC
  // Note: When the baton has been passed from background GC to foreground GC,
  // _foregroundGCIsActive is true and _foregroundGCShouldWait is false.
  static bool _foregroundGCIsActive;    // true iff foreground collector is active or
                                 // wants to go active
  static bool _foregroundGCShouldWait;  // true iff background GC is active and has not
                                 // yet passed the baton to the foreground GC

  // Support for CMSScheduleRemark (abortable preclean)
  bool _abort_preclean;
  bool _start_sampling;

  int    _numYields;
  size_t _numDirtyCards;
  size_t _sweep_count;

  // Occupancy used for bootstrapping stats
  double _bootstrap_occupancy;

  // Timer
  elapsedTimer _timer;

  // Timing, allocation and promotion statistics, used for scheduling.
  CMSStats      _stats;

  enum CMS_op_type {
    CMS_op_checkpointRootsInitial,
    CMS_op_checkpointRootsFinal
  };

  void do_CMS_operation(CMS_op_type op, GCCause::Cause gc_cause);
  bool stop_world_and_do(CMS_op_type op);

  OopTaskQueueSet* task_queues() { return _task_queues; }
  int*             hash_seed(int i) { return &_hash_seed[i]; }
  YieldingFlexibleWorkGang* conc_workers() { return _conc_workers; }

  // Support for parallelizing Eden rescan in CMS remark phase
  void sample_eden(); // ... sample Eden space top

 private:
  // Support for parallelizing young gen rescan in CMS remark phase
  ParNewGeneration* _young_gen;

  HeapWord** _top_addr;    // ... Top of Eden
  HeapWord** _end_addr;    // ... End of Eden
  Mutex*     _eden_chunk_lock;
  HeapWord** _eden_chunk_array; // ... Eden partitioning array
  size_t     _eden_chunk_index; // ... top (exclusive) of array
  size_t     _eden_chunk_capacity;  // ... max entries in array

  // Support for parallelizing survivor space rescan
  HeapWord** _survivor_chunk_array;
  size_t     _survivor_chunk_index;
  size_t     _survivor_chunk_capacity;
  size_t*    _cursor;
  ChunkArray* _survivor_plab_array;

  // Support for marking stack overflow handling
  bool take_from_overflow_list(size_t num, CMSMarkStack* to_stack);
  bool par_take_from_overflow_list(size_t num,
                                   OopTaskQueue* to_work_q,
                                   int no_of_gc_threads);
  void push_on_overflow_list(oop p);
  void par_push_on_overflow_list(oop p);
  // The following is, obviously, not, in general, "MT-stable"
  bool overflow_list_is_empty() const;

  void preserve_mark_if_necessary(oop p);
  void par_preserve_mark_if_necessary(oop p);
  void preserve_mark_work(oop p, markOop m);
  void restore_preserved_marks_if_any();
  NOT_PRODUCT(bool no_preserved_marks() const;)
  // In support of testing overflow code
  NOT_PRODUCT(int _overflow_counter;)
  NOT_PRODUCT(bool simulate_overflow();)       // Sequential
  NOT_PRODUCT(bool par_simulate_overflow();)   // MT version

  // CMS work methods
  void checkpointRootsInitialWork(); // Initial checkpoint work

  // A return value of false indicates failure due to stack overflow
  bool markFromRootsWork();  // Concurrent marking work

 public:   // FIX ME!!! only for testing
  bool do_marking_st();      // Single-threaded marking
  bool do_marking_mt();      // Multi-threaded  marking

 private:

  // Concurrent precleaning work
  size_t preclean_mod_union_table(ConcurrentMarkSweepGeneration* old_gen,
                                  ScanMarkedObjectsAgainCarefullyClosure* cl);
  size_t preclean_card_table(ConcurrentMarkSweepGeneration* old_gen,
                             ScanMarkedObjectsAgainCarefullyClosure* cl);
  // Does precleaning work, returning a quantity indicative of
  // the amount of "useful work" done.
  size_t preclean_work(bool clean_refs, bool clean_survivors);
  void preclean_klasses(MarkRefsIntoAndScanClosure* cl, Mutex* freelistLock);
  void abortable_preclean(); // Preclean while looking for possible abort
  void initialize_sequential_subtasks_for_young_gen_rescan(int i);
  // Helper function for above; merge-sorts the per-thread plab samples
  void merge_survivor_plab_arrays(ContiguousSpace* surv, int no_of_gc_threads);
  // Resets (i.e. clears) the per-thread plab sample vectors
  void reset_survivor_plab_arrays();

  // Final (second) checkpoint work
  void checkpointRootsFinalWork();
  // Work routine for parallel version of remark
  void do_remark_parallel();
  // Work routine for non-parallel version of remark
  void do_remark_non_parallel();
  // Reference processing work routine (during second checkpoint)
  void refProcessingWork();

  // Concurrent sweeping work
  void sweepWork(ConcurrentMarkSweepGeneration* old_gen);

  // Concurrent resetting of support data structures
  void reset_concurrent();
  // Resetting of support data structures from a STW full GC
  void reset_stw();

  // Clear _expansion_cause fields of constituent generations
  void clear_expansion_cause();

  // An auxiliary method used to record the ends of
  // used regions of each generation to limit the extent of sweep
  void save_sweep_limits();

  // A work method used by the foreground collector to do
  // a mark-sweep-compact.
  void do_compaction_work(bool clear_all_soft_refs);

  // Work methods for reporting concurrent mode interruption or failure
  bool is_external_interruption();
  void report_concurrent_mode_interruption();

  // If the background GC is active, acquire control from the background
  // GC and do the collection.
  void acquire_control_and_collect(bool   full, bool clear_all_soft_refs);

  // For synchronizing passing of control from background to foreground
  // GC.  waitForForegroundGC() is called by the background
  // collector.  It if had to wait for a foreground collection,
  // it returns true and the background collection should assume
  // that the collection was finished by the foreground
  // collector.
  bool waitForForegroundGC();

  size_t block_size_using_printezis_bits(HeapWord* addr) const;
  size_t block_size_if_printezis_bits(HeapWord* addr) const;
  HeapWord* next_card_start_after_block(HeapWord* addr) const;

  void setup_cms_unloading_and_verification_state();
 public:
  CMSCollector(ConcurrentMarkSweepGeneration* cmsGen,
               CardTableRS*                   ct,
               ConcurrentMarkSweepPolicy*     cp);
  ConcurrentMarkSweepThread* cmsThread() { return _cmsThread; }

  ReferenceProcessor* ref_processor() { return _ref_processor; }
  void ref_processor_init();

  Mutex* bitMapLock()        const { return _markBitMap.lock();    }
  static CollectorState abstract_state() { return _collectorState;  }

  bool should_abort_preclean() const; // Whether preclean should be aborted.
  size_t get_eden_used() const;
  size_t get_eden_capacity() const;

  ConcurrentMarkSweepGeneration* cmsGen() { return _cmsGen; }

  // Locking checks
  NOT_PRODUCT(static bool have_cms_token();)

  bool shouldConcurrentCollect();

  void collect(bool   full,
               bool   clear_all_soft_refs,
               size_t size,
               bool   tlab);
  void collect_in_background(GCCause::Cause cause);

  // In support of ExplicitGCInvokesConcurrent
  static void request_full_gc(unsigned int full_gc_count, GCCause::Cause cause);
  // Should we unload classes in a particular concurrent cycle?
  bool should_unload_classes() const {
    return _should_unload_classes;
  }
  void update_should_unload_classes();

  void direct_allocated(HeapWord* start, size_t size);

  // Object is dead if not marked and current phase is sweeping.
  bool is_dead_obj(oop obj) const;

  // After a promotion (of "start"), do any necessary marking.
  // If "par", then it's being done by a parallel GC thread.
  // The last two args indicate if we need precise marking
  // and if so the size of the object so it can be dirtied
  // in its entirety.
  void promoted(bool par, HeapWord* start,
                bool is_obj_array, size_t obj_size);

  void getFreelistLocks() const;
  void releaseFreelistLocks() const;
  bool haveFreelistLocks() const;

  // Adjust size of underlying generation
  void compute_new_size();

  // GC prologue and epilogue
  void gc_prologue(bool full);
  void gc_epilogue(bool full);

  jlong time_of_last_gc(jlong now) {
    if (_collectorState <= Idling) {
      // gc not in progress
      return _time_of_last_gc;
    } else {
      // collection in progress
      return now;
    }
  }

  // Support for parallel remark of survivor space
  void* get_data_recorder(int thr_num);
  void sample_eden_chunk();

  CMSBitMap* markBitMap()  { return &_markBitMap; }
  void directAllocated(HeapWord* start, size_t size);

  // Main CMS steps and related support
  void checkpointRootsInitial();
  bool markFromRoots();  // a return value of false indicates failure
                         // due to stack overflow
  void preclean();
  void checkpointRootsFinal();
  void sweep();

  // Check that the currently executing thread is the expected
  // one (foreground collector or background collector).
  static void check_correct_thread_executing() PRODUCT_RETURN;

  NOT_PRODUCT(bool is_cms_reachable(HeapWord* addr);)

  // Performance Counter Support
  CollectorCounters* counters()     { return _gc_counters; }
  CollectorCounters* cgc_counters() { return _cgc_counters; }

  // Timer stuff
  void    startTimer() { assert(!_timer.is_active(), "Error"); _timer.start();   }
  void    stopTimer()  { assert( _timer.is_active(), "Error"); _timer.stop();    }
  void    resetTimer() { assert(!_timer.is_active(), "Error"); _timer.reset();   }
  jlong   timerTicks() { assert(!_timer.is_active(), "Error"); return _timer.ticks(); }

  int  yields()          { return _numYields; }
  void resetYields()     { _numYields = 0;    }
  void incrementYields() { _numYields++;      }
  void resetNumDirtyCards()               { _numDirtyCards = 0; }
  void incrementNumDirtyCards(size_t num) { _numDirtyCards += num; }
  size_t  numDirtyCards()                 { return _numDirtyCards; }

  static bool foregroundGCShouldWait() { return _foregroundGCShouldWait; }
  static void set_foregroundGCShouldWait(bool v) { _foregroundGCShouldWait = v; }
  static bool foregroundGCIsActive() { return _foregroundGCIsActive; }
  static void set_foregroundGCIsActive(bool v) { _foregroundGCIsActive = v; }
  size_t sweep_count() const             { return _sweep_count; }
  void   increment_sweep_count()         { _sweep_count++; }

  // Timers/stats for gc scheduling and incremental mode pacing.
  CMSStats& stats() { return _stats; }

  // Adaptive size policy
  AdaptiveSizePolicy* size_policy();

  static void print_on_error(outputStream* st);

  // Debugging
  void verify();
  bool verify_after_remark();
  void verify_ok_to_terminate() const PRODUCT_RETURN;
  void verify_work_stacks_empty() const PRODUCT_RETURN;
  void verify_overflow_empty() const PRODUCT_RETURN;

  // Convenience methods in support of debugging
  static const size_t skip_header_HeapWords() PRODUCT_RETURN0;
  HeapWord* block_start(const void* p) const PRODUCT_RETURN0;

  // Accessors
  CMSMarkStack* verification_mark_stack() { return &_markStack; }
  CMSBitMap*    verification_mark_bm()    { return &_verification_mark_bm; }

  // Initialization errors
  bool completed_initialization() { return _completed_initialization; }

  void print_eden_and_survivor_chunk_arrays();

  ConcurrentGCTimer* gc_timer_cm() const { return _gc_timer_cm; }
};

class CMSExpansionCause : public AllStatic  {
 public:
  enum Cause {
    _no_expansion,
    _satisfy_free_ratio,
    _satisfy_promotion,
    _satisfy_allocation,
    _allocate_par_lab,
    _allocate_par_spooling_space,
    _adaptive_size_policy
  };
  // Return a string describing the cause of the expansion.
  static const char* to_string(CMSExpansionCause::Cause cause);
};

class ConcurrentMarkSweepGeneration: public CardGeneration {
  friend class VMStructs;
  friend class ConcurrentMarkSweepThread;
  friend class ConcurrentMarkSweep;
  friend class CMSCollector;
 protected:
  static CMSCollector*       _collector; // the collector that collects us
  CompactibleFreeListSpace*  _cmsSpace;  // underlying space (only one for now)

  // Performance Counters
  GenerationCounters*      _gen_counters;
  GSpaceCounters*          _space_counters;

  // Words directly allocated, used by CMSStats.
  size_t _direct_allocated_words;

  // Non-product stat counters
  NOT_PRODUCT(
    size_t _numObjectsPromoted;
    size_t _numWordsPromoted;
    size_t _numObjectsAllocated;
    size_t _numWordsAllocated;
  )

  // Used for sizing decisions
  bool _incremental_collection_failed;
  bool incremental_collection_failed() {
    return _incremental_collection_failed;
  }
  void set_incremental_collection_failed() {
    _incremental_collection_failed = true;
  }
  void clear_incremental_collection_failed() {
    _incremental_collection_failed = false;
  }

  // accessors
  void set_expansion_cause(CMSExpansionCause::Cause v) { _expansion_cause = v;}
  CMSExpansionCause::Cause expansion_cause() const { return _expansion_cause; }

  // Accessing spaces
  CompactibleSpace* space() const { return (CompactibleSpace*)_cmsSpace; }

 private:
  // For parallel young-gen GC support.
  CMSParGCThreadState** _par_gc_thread_states;

  // Reason generation was expanded
  CMSExpansionCause::Cause _expansion_cause;

  // In support of MinChunkSize being larger than min object size
  const double _dilatation_factor;

  // True if a compacting collection was done.
  bool _did_compact;
  bool did_compact() { return _did_compact; }

  // Fraction of current occupancy at which to start a CMS collection which
  // will collect this generation (at least).
  double _initiating_occupancy;

 protected:
  // Shrink generation by specified size (returns false if unable to shrink)
  void shrink_free_list_by(size_t bytes);

  // Update statistics for GC
  virtual void update_gc_stats(Generation* current_generation, bool full);

  // Maximum available space in the generation (including uncommitted)
  // space.
  size_t max_available() const;

  // getter and initializer for _initiating_occupancy field.
  double initiating_occupancy() const { return _initiating_occupancy; }
  void   init_initiating_occupancy(intx io, uintx tr);

  void expand_for_gc_cause(size_t bytes, size_t expand_bytes, CMSExpansionCause::Cause cause);

  void assert_correct_size_change_locking();

 public:
  ConcurrentMarkSweepGeneration(ReservedSpace rs, size_t initial_byte_size, CardTableRS* ct);

  // Accessors
  CMSCollector* collector() const { return _collector; }
  static void set_collector(CMSCollector* collector) {
    assert(_collector == NULL, "already set");
    _collector = collector;
  }
  CompactibleFreeListSpace*  cmsSpace() const { return _cmsSpace;  }

  Mutex* freelistLock() const;

  virtual Generation::Name kind() { return Generation::ConcurrentMarkSweep; }

  void set_did_compact(bool v) { _did_compact = v; }

  bool refs_discovery_is_atomic() const { return false; }
  bool refs_discovery_is_mt()     const {
    // Note: CMS does MT-discovery during the parallel-remark
    // phases. Use ReferenceProcessorMTMutator to make refs
    // discovery MT-safe during such phases or other parallel
    // discovery phases in the future. This may all go away
    // if/when we decide that refs discovery is sufficiently
    // rare that the cost of the CAS's involved is in the
    // noise. That's a measurement that should be done, and
    // the code simplified if that turns out to be the case.
    return ConcGCThreads > 1;
  }

  // Override
  virtual void ref_processor_init();

  void clear_expansion_cause() { _expansion_cause = CMSExpansionCause::_no_expansion; }

  // Space enquiries
  double occupancy() const { return ((double)used())/((double)capacity()); }
  size_t contiguous_available() const;
  size_t unsafe_max_alloc_nogc() const;

  // over-rides
  MemRegion used_region_at_save_marks() const;

  // Adjust quantities in the generation affected by
  // the compaction.
  void reset_after_compaction();

  // Allocation support
  HeapWord* allocate(size_t size, bool tlab);
  HeapWord* have_lock_and_allocate(size_t size, bool tlab);
  oop       promote(oop obj, size_t obj_size);
  HeapWord* par_allocate(size_t size, bool tlab) {
    return allocate(size, tlab);
  }


  // Used by CMSStats to track direct allocation.  The value is sampled and
  // reset after each young gen collection.
  size_t direct_allocated_words() const { return _direct_allocated_words; }
  void reset_direct_allocated_words()   { _direct_allocated_words = 0; }

  // Overrides for parallel promotion.
  virtual oop par_promote(int thread_num,
                          oop obj, markOop m, size_t word_sz);
  virtual void par_promote_alloc_done(int thread_num);
  virtual void par_oop_since_save_marks_iterate_done(int thread_num);

  virtual bool promotion_attempt_is_safe(size_t promotion_in_bytes) const;

  // Inform this (old) generation that a promotion failure was
  // encountered during a collection of the young generation.
  virtual void promotion_failure_occurred();

  bool should_collect(bool full, size_t size, bool tlab);
  virtual bool should_concurrent_collect() const;
  virtual bool is_too_full() const;
  void collect(bool   full,
               bool   clear_all_soft_refs,
               size_t size,
               bool   tlab);

  HeapWord* expand_and_allocate(size_t word_size,
                                bool tlab,
                                bool parallel = false);

  // GC prologue and epilogue
  void gc_prologue(bool full);
  void gc_prologue_work(bool full, bool registerClosure,
                        ModUnionClosure* modUnionClosure);
  void gc_epilogue(bool full);
  void gc_epilogue_work(bool full);

  // Time since last GC of this generation
  jlong time_of_last_gc(jlong now) {
    return collector()->time_of_last_gc(now);
  }
  void update_time_of_last_gc(jlong now) {
    collector()-> update_time_of_last_gc(now);
  }

  // Allocation failure
  void shrink(size_t bytes);
  HeapWord* expand_and_par_lab_allocate(CMSParGCThreadState* ps, size_t word_sz);
  bool expand_and_ensure_spooling_space(PromotionInfo* promo);

  // Iteration support and related enquiries
  void save_marks();
  bool no_allocs_since_save_marks();

  // Iteration support specific to CMS generations
  void save_sweep_limit();

  // More iteration support
  virtual void oop_iterate(ExtendedOopClosure* cl);
  virtual void safe_object_iterate(ObjectClosure* cl);
  virtual void object_iterate(ObjectClosure* cl);

  // Need to declare the full complement of closures, whether we'll
  // override them or not, or get message from the compiler:
  //   oop_since_save_marks_iterate_nv hides virtual function...
  #define CMS_SINCE_SAVE_MARKS_DECL(OopClosureType, nv_suffix) \
    void oop_since_save_marks_iterate##nv_suffix(OopClosureType* cl);
  ALL_SINCE_SAVE_MARKS_CLOSURES(CMS_SINCE_SAVE_MARKS_DECL)

  // Smart allocation  XXX -- move to CFLSpace?
  void setNearLargestChunk();
  bool isNearLargestChunk(HeapWord* addr);

  // Get the chunk at the end of the space.  Delegates to
  // the space.
  FreeChunk* find_chunk_at_end();

  void post_compact();

  // Debugging
  void prepare_for_verify();
  void verify();
  void print_statistics()               PRODUCT_RETURN;

  // Performance Counters support
  virtual void update_counters();
  virtual void update_counters(size_t used);
  void initialize_performance_counters();
  CollectorCounters* counters()  { return collector()->counters(); }

  // Support for parallel remark of survivor space
  void* get_data_recorder(int thr_num) {
    //Delegate to collector
    return collector()->get_data_recorder(thr_num);
  }
  void sample_eden_chunk() {
    //Delegate to collector
    return collector()->sample_eden_chunk();
  }

  // Printing
  const char* name() const;
  virtual const char* short_name() const { return "CMS"; }
  void        print() const;

  // Resize the generation after a compacting GC.  The
  // generation can be treated as a contiguous space
  // after the compaction.
  virtual void compute_new_size();
  // Resize the generation after a non-compacting
  // collection.
  void compute_new_size_free_list();
};

//
// Closures of various sorts used by CMS to accomplish its work
//

// This closure is used to do concurrent marking from the roots
// following the first checkpoint.
class MarkFromRootsClosure: public BitMapClosure {
  CMSCollector*  _collector;
  MemRegion      _span;
  CMSBitMap*     _bitMap;
  CMSBitMap*     _mut;
  CMSMarkStack*  _markStack;
  bool           _yield;
  int            _skipBits;
  HeapWord*      _finger;
  HeapWord*      _threshold;
  DEBUG_ONLY(bool _verifying;)

 public:
  MarkFromRootsClosure(CMSCollector* collector, MemRegion span,
                       CMSBitMap* bitMap,
                       CMSMarkStack*  markStack,
                       bool should_yield, bool verifying = false);
  bool do_bit(size_t offset);
  void reset(HeapWord* addr);
  inline void do_yield_check();

 private:
  void scanOopsInOop(HeapWord* ptr);
  void do_yield_work();
};

// This closure is used to do concurrent multi-threaded
// marking from the roots following the first checkpoint.
// XXX This should really be a subclass of The serial version
// above, but i have not had the time to refactor things cleanly.
class ParMarkFromRootsClosure: public BitMapClosure {
  CMSCollector*  _collector;
  MemRegion      _whole_span;
  MemRegion      _span;
  CMSBitMap*     _bit_map;
  CMSBitMap*     _mut;
  OopTaskQueue*  _work_queue;
  CMSMarkStack*  _overflow_stack;
  int            _skip_bits;
  HeapWord*      _finger;
  HeapWord*      _threshold;
  CMSConcMarkingTask* _task;
 public:
  ParMarkFromRootsClosure(CMSConcMarkingTask* task, CMSCollector* collector,
                          MemRegion span,
                          CMSBitMap* bit_map,
                          OopTaskQueue* work_queue,
                          CMSMarkStack*  overflow_stack);
  bool do_bit(size_t offset);
  inline void do_yield_check();

 private:
  void scan_oops_in_oop(HeapWord* ptr);
  void do_yield_work();
  bool get_work_from_overflow_stack();
};

// The following closures are used to do certain kinds of verification of
// CMS marking.
class PushAndMarkVerifyClosure: public MetadataAwareOopClosure {
  CMSCollector*    _collector;
  MemRegion        _span;
  CMSBitMap*       _verification_bm;
  CMSBitMap*       _cms_bm;
  CMSMarkStack*    _mark_stack;
 protected:
  void do_oop(oop p);
  template <class T> inline void do_oop_work(T *p) {
    oop obj = oopDesc::load_decode_heap_oop(p);
    do_oop(obj);
  }
 public:
  PushAndMarkVerifyClosure(CMSCollector* cms_collector,
                           MemRegion span,
                           CMSBitMap* verification_bm,
                           CMSBitMap* cms_bm,
                           CMSMarkStack*  mark_stack);
  void do_oop(oop* p);
  void do_oop(narrowOop* p);

  // Deal with a stack overflow condition
  void handle_stack_overflow(HeapWord* lost);
};

class MarkFromRootsVerifyClosure: public BitMapClosure {
  CMSCollector*  _collector;
  MemRegion      _span;
  CMSBitMap*     _verification_bm;
  CMSBitMap*     _cms_bm;
  CMSMarkStack*  _mark_stack;
  HeapWord*      _finger;
  PushAndMarkVerifyClosure _pam_verify_closure;
 public:
  MarkFromRootsVerifyClosure(CMSCollector* collector, MemRegion span,
                             CMSBitMap* verification_bm,
                             CMSBitMap* cms_bm,
                             CMSMarkStack*  mark_stack);
  bool do_bit(size_t offset);
  void reset(HeapWord* addr);
};


// This closure is used to check that a certain set of bits is
// "empty" (i.e. the bit vector doesn't have any 1-bits).
class FalseBitMapClosure: public BitMapClosure {
 public:
  bool do_bit(size_t offset) {
    guarantee(false, "Should not have a 1 bit");
    return true;
  }
};

// A version of ObjectClosure with "memory" (see _previous_address below)
class UpwardsObjectClosure: public BoolObjectClosure {
  HeapWord* _previous_address;
 public:
  UpwardsObjectClosure() : _previous_address(NULL) { }
  void set_previous(HeapWord* addr) { _previous_address = addr; }
  HeapWord* previous()              { return _previous_address; }
  // A return value of "true" can be used by the caller to decide
  // if this object's end should *NOT* be recorded in
  // _previous_address above.
  virtual bool do_object_bm(oop obj, MemRegion mr) = 0;
};

// This closure is used during the second checkpointing phase
// to rescan the marked objects on the dirty cards in the mod
// union table and the card table proper. It's invoked via
// MarkFromDirtyCardsClosure below. It uses either
// [Par_]MarkRefsIntoAndScanClosure (Par_ in the parallel case)
// declared in genOopClosures.hpp to accomplish some of its work.
// In the parallel case the bitMap is shared, so access to
// it needs to be suitably synchronized for updates by embedded
// closures that update it; however, this closure itself only
// reads the bit_map and because it is idempotent, is immune to
// reading stale values.
class ScanMarkedObjectsAgainClosure: public UpwardsObjectClosure {
  #ifdef ASSERT
    CMSCollector*          _collector;
    MemRegion              _span;
    union {
      CMSMarkStack*        _mark_stack;
      OopTaskQueue*        _work_queue;
    };
  #endif // ASSERT
  bool                       _parallel;
  CMSBitMap*                 _bit_map;
  union {
    MarkRefsIntoAndScanClosure*    _scan_closure;
    ParMarkRefsIntoAndScanClosure* _par_scan_closure;
  };

 public:
  ScanMarkedObjectsAgainClosure(CMSCollector* collector,
                                MemRegion span,
                                ReferenceProcessor* rp,
                                CMSBitMap* bit_map,
                                CMSMarkStack*  mark_stack,
                                MarkRefsIntoAndScanClosure* cl):
    #ifdef ASSERT
      _collector(collector),
      _span(span),
      _mark_stack(mark_stack),
    #endif // ASSERT
    _parallel(false),
    _bit_map(bit_map),
    _scan_closure(cl) { }

  ScanMarkedObjectsAgainClosure(CMSCollector* collector,
                                MemRegion span,
                                ReferenceProcessor* rp,
                                CMSBitMap* bit_map,
                                OopTaskQueue* work_queue,
                                ParMarkRefsIntoAndScanClosure* cl):
    #ifdef ASSERT
      _collector(collector),
      _span(span),
      _work_queue(work_queue),
    #endif // ASSERT
    _parallel(true),
    _bit_map(bit_map),
    _par_scan_closure(cl) { }

  bool do_object_b(oop obj) {
    guarantee(false, "Call do_object_b(oop, MemRegion) form instead");
    return false;
  }
  bool do_object_bm(oop p, MemRegion mr);
};

// This closure is used during the second checkpointing phase
// to rescan the marked objects on the dirty cards in the mod
// union table and the card table proper. It invokes
// ScanMarkedObjectsAgainClosure above to accomplish much of its work.
// In the parallel case, the bit map is shared and requires
// synchronized access.
class MarkFromDirtyCardsClosure: public MemRegionClosure {
  CompactibleFreeListSpace*      _space;
  ScanMarkedObjectsAgainClosure  _scan_cl;
  size_t                         _num_dirty_cards;

 public:
  MarkFromDirtyCardsClosure(CMSCollector* collector,
                            MemRegion span,
                            CompactibleFreeListSpace* space,
                            CMSBitMap* bit_map,
                            CMSMarkStack* mark_stack,
                            MarkRefsIntoAndScanClosure* cl):
    _space(space),
    _num_dirty_cards(0),
    _scan_cl(collector, span, collector->ref_processor(), bit_map,
                 mark_stack, cl) { }

  MarkFromDirtyCardsClosure(CMSCollector* collector,
                            MemRegion span,
                            CompactibleFreeListSpace* space,
                            CMSBitMap* bit_map,
                            OopTaskQueue* work_queue,
                            ParMarkRefsIntoAndScanClosure* cl):
    _space(space),
    _num_dirty_cards(0),
    _scan_cl(collector, span, collector->ref_processor(), bit_map,
             work_queue, cl) { }

  void do_MemRegion(MemRegion mr);
  void set_space(CompactibleFreeListSpace* space) { _space = space; }
  size_t num_dirty_cards() { return _num_dirty_cards; }
};

// This closure is used in the non-product build to check
// that there are no MemRegions with a certain property.
class FalseMemRegionClosure: public MemRegionClosure {
  void do_MemRegion(MemRegion mr) {
    guarantee(!mr.is_empty(), "Shouldn't be empty");
    guarantee(false, "Should never be here");
  }
};

// This closure is used during the precleaning phase
// to "carefully" rescan marked objects on dirty cards.
// It uses MarkRefsIntoAndScanClosure declared in genOopClosures.hpp
// to accomplish some of its work.
class ScanMarkedObjectsAgainCarefullyClosure: public ObjectClosureCareful {
  CMSCollector*                  _collector;
  MemRegion                      _span;
  bool                           _yield;
  Mutex*                         _freelistLock;
  CMSBitMap*                     _bitMap;
  CMSMarkStack*                  _markStack;
  MarkRefsIntoAndScanClosure*    _scanningClosure;

 public:
  ScanMarkedObjectsAgainCarefullyClosure(CMSCollector* collector,
                                         MemRegion     span,
                                         CMSBitMap* bitMap,
                                         CMSMarkStack*  markStack,
                                         MarkRefsIntoAndScanClosure* cl,
                                         bool should_yield):
    _collector(collector),
    _span(span),
    _yield(should_yield),
    _bitMap(bitMap),
    _markStack(markStack),
    _scanningClosure(cl) {
  }

  void do_object(oop p) {
    guarantee(false, "call do_object_careful instead");
  }

  size_t      do_object_careful(oop p) {
    guarantee(false, "Unexpected caller");
    return 0;
  }

  size_t      do_object_careful_m(oop p, MemRegion mr);

  void setFreelistLock(Mutex* m) {
    _freelistLock = m;
    _scanningClosure->set_freelistLock(m);
  }

 private:
  inline bool do_yield_check();

  void do_yield_work();
};

class SurvivorSpacePrecleanClosure: public ObjectClosureCareful {
  CMSCollector*                  _collector;
  MemRegion                      _span;
  bool                           _yield;
  CMSBitMap*                     _bit_map;
  CMSMarkStack*                  _mark_stack;
  PushAndMarkClosure*            _scanning_closure;
  unsigned int                   _before_count;

 public:
  SurvivorSpacePrecleanClosure(CMSCollector* collector,
                               MemRegion     span,
                               CMSBitMap*    bit_map,
                               CMSMarkStack* mark_stack,
                               PushAndMarkClosure* cl,
                               unsigned int  before_count,
                               bool          should_yield):
    _collector(collector),
    _span(span),
    _yield(should_yield),
    _bit_map(bit_map),
    _mark_stack(mark_stack),
    _scanning_closure(cl),
    _before_count(before_count)
  { }

  void do_object(oop p) {
    guarantee(false, "call do_object_careful instead");
  }

  size_t      do_object_careful(oop p);

  size_t      do_object_careful_m(oop p, MemRegion mr) {
    guarantee(false, "Unexpected caller");
    return 0;
  }

 private:
  inline void do_yield_check();
  void do_yield_work();
};

// This closure is used to accomplish the sweeping work
// after the second checkpoint but before the concurrent reset
// phase.
//
// Terminology
//   left hand chunk (LHC) - block of one or more chunks currently being
//     coalesced.  The LHC is available for coalescing with a new chunk.
//   right hand chunk (RHC) - block that is currently being swept that is
//     free or garbage that can be coalesced with the LHC.
// _inFreeRange is true if there is currently a LHC
// _lastFreeRangeCoalesced is true if the LHC consists of more than one chunk.
// _freeRangeInFreeLists is true if the LHC is in the free lists.
// _freeFinger is the address of the current LHC
class SweepClosure: public BlkClosureCareful {
  CMSCollector*                  _collector;  // collector doing the work
  ConcurrentMarkSweepGeneration* _g;    // Generation being swept
  CompactibleFreeListSpace*      _sp;   // Space being swept
  HeapWord*                      _limit;// the address at or above which the sweep should stop
                                        // because we do not expect newly garbage blocks
                                        // eligible for sweeping past that address.
  Mutex*                         _freelistLock; // Free list lock (in space)
  CMSBitMap*                     _bitMap;       // Marking bit map (in
                                                // generation)
  bool                           _inFreeRange;  // Indicates if we are in the
                                                // midst of a free run
  bool                           _freeRangeInFreeLists;
                                        // Often, we have just found
                                        // a free chunk and started
                                        // a new free range; we do not
                                        // eagerly remove this chunk from
                                        // the free lists unless there is
                                        // a possibility of coalescing.
                                        // When true, this flag indicates
                                        // that the _freeFinger below
                                        // points to a potentially free chunk
                                        // that may still be in the free lists
  bool                           _lastFreeRangeCoalesced;
                                        // free range contains chunks
                                        // coalesced
  bool                           _yield;
                                        // Whether sweeping should be
                                        // done with yields. For instance
                                        // when done by the foreground
                                        // collector we shouldn't yield.
  HeapWord*                      _freeFinger;   // When _inFreeRange is set, the
                                                // pointer to the "left hand
                                                // chunk"
  size_t                         _freeRangeSize;
                                        // When _inFreeRange is set, this
                                        // indicates the accumulated size
                                        // of the "left hand chunk"
  NOT_PRODUCT(
    size_t                       _numObjectsFreed;
    size_t                       _numWordsFreed;
    size_t                       _numObjectsLive;
    size_t                       _numWordsLive;
    size_t                       _numObjectsAlreadyFree;
    size_t                       _numWordsAlreadyFree;
    FreeChunk*                   _last_fc;
  )
 private:
  // Code that is common to a free chunk or garbage when
  // encountered during sweeping.
  void do_post_free_or_garbage_chunk(FreeChunk *fc, size_t chunkSize);
  // Process a free chunk during sweeping.
  void do_already_free_chunk(FreeChunk *fc);
  // Work method called when processing an already free or a
  // freshly garbage chunk to do a lookahead and possibly a
  // preemptive flush if crossing over _limit.
  void lookahead_and_flush(FreeChunk* fc, size_t chunkSize);
  // Process a garbage chunk during sweeping.
  size_t do_garbage_chunk(FreeChunk *fc);
  // Process a live chunk during sweeping.
  size_t do_live_chunk(FreeChunk* fc);

  // Accessors.
  HeapWord* freeFinger() const          { return _freeFinger; }
  void set_freeFinger(HeapWord* v)      { _freeFinger = v; }
  bool inFreeRange()    const           { return _inFreeRange; }
  void set_inFreeRange(bool v)          { _inFreeRange = v; }
  bool lastFreeRangeCoalesced() const    { return _lastFreeRangeCoalesced; }
  void set_lastFreeRangeCoalesced(bool v) { _lastFreeRangeCoalesced = v; }
  bool freeRangeInFreeLists() const     { return _freeRangeInFreeLists; }
  void set_freeRangeInFreeLists(bool v) { _freeRangeInFreeLists = v; }

  // Initialize a free range.
  void initialize_free_range(HeapWord* freeFinger, bool freeRangeInFreeLists);
  // Return this chunk to the free lists.
  void flush_cur_free_chunk(HeapWord* chunk, size_t size);

  // Check if we should yield and do so when necessary.
  inline void do_yield_check(HeapWord* addr);

  // Yield
  void do_yield_work(HeapWord* addr);

  // Debugging/Printing
  void print_free_block_coalesced(FreeChunk* fc) const;

 public:
  SweepClosure(CMSCollector* collector, ConcurrentMarkSweepGeneration* g,
               CMSBitMap* bitMap, bool should_yield);
  ~SweepClosure() PRODUCT_RETURN;

  size_t       do_blk_careful(HeapWord* addr);
  void         print() const { print_on(tty); }
  void         print_on(outputStream *st) const;
};

// Closures related to weak references processing

// During CMS' weak reference processing, this is a
// work-routine/closure used to complete transitive
// marking of objects as live after a certain point
// in which an initial set has been completely accumulated.
// This closure is currently used both during the final
// remark stop-world phase, as well as during the concurrent
// precleaning of the discovered reference lists.
class CMSDrainMarkingStackClosure: public VoidClosure {
  CMSCollector*        _collector;
  MemRegion            _span;
  CMSMarkStack*        _mark_stack;
  CMSBitMap*           _bit_map;
  CMSKeepAliveClosure* _keep_alive;
  bool                 _concurrent_precleaning;
 public:
  CMSDrainMarkingStackClosure(CMSCollector* collector, MemRegion span,
                      CMSBitMap* bit_map, CMSMarkStack* mark_stack,
                      CMSKeepAliveClosure* keep_alive,
                      bool cpc):
    _collector(collector),
    _span(span),
    _bit_map(bit_map),
    _mark_stack(mark_stack),
    _keep_alive(keep_alive),
    _concurrent_precleaning(cpc) {
    assert(_concurrent_precleaning == _keep_alive->concurrent_precleaning(),
           "Mismatch");
  }

  void do_void();
};

// A parallel version of CMSDrainMarkingStackClosure above.
class CMSParDrainMarkingStackClosure: public VoidClosure {
  CMSCollector*           _collector;
  MemRegion               _span;
  OopTaskQueue*           _work_queue;
  CMSBitMap*              _bit_map;
  CMSInnerParMarkAndPushClosure _mark_and_push;

 public:
  CMSParDrainMarkingStackClosure(CMSCollector* collector,
                                 MemRegion span, CMSBitMap* bit_map,
                                 OopTaskQueue* work_queue):
    _collector(collector),
    _span(span),
    _bit_map(bit_map),
    _work_queue(work_queue),
    _mark_and_push(collector, span, bit_map, work_queue) { }

 public:
  void trim_queue(uint max);
  void do_void();
};

// Allow yielding or short-circuiting of reference list
// precleaning work.
class CMSPrecleanRefsYieldClosure: public YieldClosure {
  CMSCollector* _collector;
  void do_yield_work();
 public:
  CMSPrecleanRefsYieldClosure(CMSCollector* collector):
    _collector(collector) {}
  virtual bool should_return();
};


// Convenience class that locks free list locks for given CMS collector
class FreelistLocker: public StackObj {
 private:
  CMSCollector* _collector;
 public:
  FreelistLocker(CMSCollector* collector):
    _collector(collector) {
    _collector->getFreelistLocks();
  }

  ~FreelistLocker() {
    _collector->releaseFreelistLocks();
  }
};

// Mark all dead objects in a given space.
class MarkDeadObjectsClosure: public BlkClosure {
  const CMSCollector*             _collector;
  const CompactibleFreeListSpace* _sp;
  CMSBitMap*                      _live_bit_map;
  CMSBitMap*                      _dead_bit_map;
public:
  MarkDeadObjectsClosure(const CMSCollector* collector,
                         const CompactibleFreeListSpace* sp,
                         CMSBitMap *live_bit_map,
                         CMSBitMap *dead_bit_map) :
    _collector(collector),
    _sp(sp),
    _live_bit_map(live_bit_map),
    _dead_bit_map(dead_bit_map) {}
  size_t do_blk(HeapWord* addr);
};

class TraceCMSMemoryManagerStats : public TraceMemoryManagerStats {

 public:
  TraceCMSMemoryManagerStats(CMSCollector::CollectorState phase, GCCause::Cause cause);
};


#endif // SHARE_VM_GC_CMS_CONCURRENTMARKSWEEPGENERATION_HPP
