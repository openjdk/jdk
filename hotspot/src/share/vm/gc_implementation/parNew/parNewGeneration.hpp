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

#ifndef SHARE_VM_GC_IMPLEMENTATION_PARNEW_PARNEWGENERATION_HPP
#define SHARE_VM_GC_IMPLEMENTATION_PARNEW_PARNEWGENERATION_HPP

#include "gc_implementation/shared/gcTrace.hpp"
#include "gc_implementation/shared/parGCAllocBuffer.hpp"
#include "gc_implementation/shared/copyFailedInfo.hpp"
#include "memory/defNewGeneration.hpp"
#include "memory/padded.hpp"
#include "utilities/taskqueue.hpp"

class ChunkArray;
class ParScanWithoutBarrierClosure;
class ParScanWithBarrierClosure;
class ParRootScanWithoutBarrierClosure;
class ParRootScanWithBarrierTwoGensClosure;
class ParEvacuateFollowersClosure;

// It would be better if these types could be kept local to the .cpp file,
// but they must be here to allow ParScanClosure::do_oop_work to be defined
// in genOopClosures.inline.hpp.

typedef Padded<OopTaskQueue> ObjToScanQueue;
typedef GenericTaskQueueSet<ObjToScanQueue, mtGC> ObjToScanQueueSet;

class ParKeepAliveClosure: public DefNewGeneration::KeepAliveClosure {
 private:
  ParScanWeakRefClosure* _par_cl;
 protected:
  template <class T> void do_oop_work(T* p);
 public:
  ParKeepAliveClosure(ParScanWeakRefClosure* cl);
  virtual void do_oop(oop* p);
  virtual void do_oop(narrowOop* p);
};

// The state needed by thread performing parallel young-gen collection.
class ParScanThreadState {
  friend class ParScanThreadStateSet;
 private:
  ObjToScanQueue *_work_queue;
  Stack<oop, mtGC>* const _overflow_stack;

  ParGCAllocBuffer _to_space_alloc_buffer;

  ParScanWithoutBarrierClosure         _to_space_closure; // scan_without_gc_barrier
  ParScanWithBarrierClosure            _old_gen_closure; // scan_with_gc_barrier
  ParRootScanWithoutBarrierClosure     _to_space_root_closure; // scan_root_without_gc_barrier
  // One of these two will be passed to process_strong_roots, which will
  // set its generation.  The first is for two-gen configs where the
  // old gen collects the perm gen; the second is for arbitrary configs.
  // The second isn't used right now (it used to be used for the train, an
  // incremental collector) but the declaration has been left as a reminder.
  ParRootScanWithBarrierTwoGensClosure _older_gen_closure;
  // This closure will always be bound to the old gen; it will be used
  // in evacuate_followers.
  ParRootScanWithBarrierTwoGensClosure _old_gen_root_closure; // scan_old_root_with_gc_barrier
  ParEvacuateFollowersClosure          _evacuate_followers;
  DefNewGeneration::IsAliveClosure     _is_alive_closure;
  ParScanWeakRefClosure                _scan_weak_ref_closure;
  ParKeepAliveClosure                  _keep_alive_closure;


  Space* _to_space;
  Space* to_space() { return _to_space; }

  ParNewGeneration* _young_gen;
  ParNewGeneration* young_gen() const { return _young_gen; }

  Generation* _old_gen;
  Generation* old_gen() { return _old_gen; }

  HeapWord *_young_old_boundary;

  int _hash_seed;
  int _thread_num;
  ageTable _ageTable;

  bool _to_space_full;

#if TASKQUEUE_STATS
  size_t _term_attempts;
  size_t _overflow_refills;
  size_t _overflow_refill_objs;
#endif // TASKQUEUE_STATS

  // Stats for promotion failure
  PromotionFailedInfo _promotion_failed_info;

  // Timing numbers.
  double _start;
  double _start_strong_roots;
  double _strong_roots_time;
  double _start_term;
  double _term_time;

  // Helper for trim_queues. Scans subset of an array and makes
  // remainder available for work stealing.
  void scan_partial_array_and_push_remainder(oop obj);

  // In support of CMS' parallel rescan of survivor space.
  ChunkArray* _survivor_chunk_array;
  ChunkArray* survivor_chunk_array() { return _survivor_chunk_array; }

  void record_survivor_plab(HeapWord* plab_start, size_t plab_word_size);

  ParScanThreadState(Space* to_space_, ParNewGeneration* gen_,
                     Generation* old_gen_, int thread_num_,
                     ObjToScanQueueSet* work_queue_set_,
                     Stack<oop, mtGC>* overflow_stacks_,
                     size_t desired_plab_sz_,
                     ParallelTaskTerminator& term_);

 public:
  ageTable* age_table() {return &_ageTable;}

  ObjToScanQueue* work_queue() { return _work_queue; }

  ParGCAllocBuffer* to_space_alloc_buffer() {
    return &_to_space_alloc_buffer;
  }

  ParEvacuateFollowersClosure&      evacuate_followers_closure() { return _evacuate_followers; }
  DefNewGeneration::IsAliveClosure& is_alive_closure() { return _is_alive_closure; }
  ParScanWeakRefClosure&            scan_weak_ref_closure() { return _scan_weak_ref_closure; }
  ParKeepAliveClosure&              keep_alive_closure() { return _keep_alive_closure; }
  ParScanClosure&                   older_gen_closure() { return _older_gen_closure; }
  ParRootScanWithoutBarrierClosure& to_space_root_closure() { return _to_space_root_closure; };

  // Decrease queue size below "max_size".
  void trim_queues(int max_size);

  // Private overflow stack usage
  Stack<oop, mtGC>* overflow_stack() { return _overflow_stack; }
  bool take_from_overflow_stack();
  void push_on_overflow_stack(oop p);

  // Is new_obj a candidate for scan_partial_array_and_push_remainder method.
  inline bool should_be_partially_scanned(oop new_obj, oop old_obj) const;

  int* hash_seed()  { return &_hash_seed; }
  int  thread_num() { return _thread_num; }

  // Allocate a to-space block of size "sz", or else return NULL.
  HeapWord* alloc_in_to_space_slow(size_t word_sz);

  HeapWord* alloc_in_to_space(size_t word_sz) {
    HeapWord* obj = to_space_alloc_buffer()->allocate(word_sz);
    if (obj != NULL) return obj;
    else return alloc_in_to_space_slow(word_sz);
  }

  HeapWord* young_old_boundary() { return _young_old_boundary; }

  void set_young_old_boundary(HeapWord *boundary) {
    _young_old_boundary = boundary;
  }

  // Undo the most recent allocation ("obj", of "word_sz").
  void undo_alloc_in_to_space(HeapWord* obj, size_t word_sz);

  // Promotion failure stats
  void register_promotion_failure(size_t sz) {
    _promotion_failed_info.register_copy_failure(sz);
  }
  PromotionFailedInfo& promotion_failed_info() {
    return _promotion_failed_info;
  }
  bool promotion_failed() {
    return _promotion_failed_info.has_failed();
  }
  void print_promotion_failure_size();

#if TASKQUEUE_STATS
  TaskQueueStats & taskqueue_stats() const { return _work_queue->stats; }

  size_t term_attempts() const             { return _term_attempts; }
  size_t overflow_refills() const          { return _overflow_refills; }
  size_t overflow_refill_objs() const      { return _overflow_refill_objs; }

  void note_term_attempt()                 { ++_term_attempts; }
  void note_overflow_refill(size_t objs)   {
    ++_overflow_refills; _overflow_refill_objs += objs;
  }

  void reset_stats();
#endif // TASKQUEUE_STATS

  void start_strong_roots() {
    _start_strong_roots = os::elapsedTime();
  }
  void end_strong_roots() {
    _strong_roots_time += (os::elapsedTime() - _start_strong_roots);
  }
  double strong_roots_time() const { return _strong_roots_time; }
  void start_term_time() {
    TASKQUEUE_STATS_ONLY(note_term_attempt());
    _start_term = os::elapsedTime();
  }
  void end_term_time() {
    _term_time += (os::elapsedTime() - _start_term);
  }
  double term_time() const { return _term_time; }

  double elapsed_time() const {
    return os::elapsedTime() - _start;
  }
};

class ParNewGenTask: public AbstractGangTask {
 private:
  ParNewGeneration*            _gen;
  Generation*                  _next_gen;
  HeapWord*                    _young_old_boundary;
  class ParScanThreadStateSet* _state_set;

public:
  ParNewGenTask(ParNewGeneration*      gen,
                Generation*            next_gen,
                HeapWord*              young_old_boundary,
                ParScanThreadStateSet* state_set);

  HeapWord* young_old_boundary() { return _young_old_boundary; }

  void work(uint worker_id);

  // Reset the terminator in ParScanThreadStateSet for
  // "active_workers" threads.
  virtual void set_for_termination(int active_workers);
};

class KeepAliveClosure: public DefNewGeneration::KeepAliveClosure {
 protected:
  template <class T> void do_oop_work(T* p);
 public:
  KeepAliveClosure(ScanWeakRefClosure* cl);
  virtual void do_oop(oop* p);
  virtual void do_oop(narrowOop* p);
};

class EvacuateFollowersClosureGeneral: public VoidClosure {
 private:
  GenCollectedHeap* _gch;
  int               _level;
  OopsInGenClosure* _scan_cur_or_nonheap;
  OopsInGenClosure* _scan_older;
 public:
  EvacuateFollowersClosureGeneral(GenCollectedHeap* gch, int level,
                                  OopsInGenClosure* cur,
                                  OopsInGenClosure* older);
  virtual void do_void();
};

// Closure for scanning ParNewGeneration.
// Same as ScanClosure, except does parallel GC barrier.
class ScanClosureWithParBarrier: public ScanClosure {
 protected:
  template <class T> void do_oop_work(T* p);
 public:
  ScanClosureWithParBarrier(ParNewGeneration* g, bool gc_barrier);
  virtual void do_oop(oop* p);
  virtual void do_oop(narrowOop* p);
};

// Implements AbstractRefProcTaskExecutor for ParNew.
class ParNewRefProcTaskExecutor: public AbstractRefProcTaskExecutor {
 private:
  ParNewGeneration&      _generation;
  ParScanThreadStateSet& _state_set;
 public:
  ParNewRefProcTaskExecutor(ParNewGeneration& generation,
                            ParScanThreadStateSet& state_set)
    : _generation(generation), _state_set(state_set)
  { }

  // Executes a task using worker threads.
  virtual void execute(ProcessTask& task);
  virtual void execute(EnqueueTask& task);
  // Switch to single threaded mode.
  virtual void set_single_threaded_mode();
};


// A Generation that does parallel young-gen collection.

class ParNewGeneration: public DefNewGeneration {
  friend class ParNewGenTask;
  friend class ParNewRefProcTask;
  friend class ParNewRefProcTaskExecutor;
  friend class ParScanThreadStateSet;
  friend class ParEvacuateFollowersClosure;

 private:
  // The per-worker-thread work queues
  ObjToScanQueueSet* _task_queues;

  // Per-worker-thread local overflow stacks
  Stack<oop, mtGC>* _overflow_stacks;

  // Desired size of survivor space plab's
  PLABStats _plab_stats;

  // A list of from-space images of to-be-scanned objects, threaded through
  // klass-pointers (klass information already copied to the forwarded
  // image.)  Manipulated with CAS.
  oop _overflow_list;
  NOT_PRODUCT(ssize_t _num_par_pushes;)

  // If true, older generation does not support promotion undo, so avoid.
  static bool _avoid_promotion_undo;

  // This closure is used by the reference processor to filter out
  // references to live referent.
  DefNewGeneration::IsAliveClosure _is_alive_closure;

  static oop real_forwardee_slow(oop obj);
  static void waste_some_time();

  // Preserve the mark of "obj", if necessary, in preparation for its mark
  // word being overwritten with a self-forwarding-pointer.
  void preserve_mark_if_necessary(oop obj, markOop m);

  void handle_promotion_failed(GenCollectedHeap* gch, ParScanThreadStateSet& thread_state_set, ParNewTracer& gc_tracer);

 protected:

  bool _survivor_overflow;

  bool avoid_promotion_undo() { return _avoid_promotion_undo; }
  void set_avoid_promotion_undo(bool v) { _avoid_promotion_undo = v; }

  bool survivor_overflow() { return _survivor_overflow; }
  void set_survivor_overflow(bool v) { _survivor_overflow = v; }

 public:
  ParNewGeneration(ReservedSpace rs, size_t initial_byte_size, int level);

  ~ParNewGeneration() {
    for (uint i = 0; i < ParallelGCThreads; i++)
        delete _task_queues->queue(i);

    delete _task_queues;
  }

  virtual void ref_processor_init();
  virtual Generation::Name kind()        { return Generation::ParNew; }
  virtual const char* name() const;
  virtual const char* short_name() const { return "ParNew"; }

  // override
  virtual bool refs_discovery_is_mt()     const {
    assert(UseParNewGC, "ParNewGeneration only when UseParNewGC");
    return ParallelGCThreads > 1;
  }

  // Make the collection virtual.
  virtual void collect(bool   full,
                       bool   clear_all_soft_refs,
                       size_t size,
                       bool   is_tlab);

  // This needs to be visible to the closure function.
  // "obj" is the object to be copied, "m" is a recent value of its mark
  // that must not contain a forwarding pointer (though one might be
  // inserted in "obj"s mark word by a parallel thread).
  inline oop copy_to_survivor_space(ParScanThreadState* par_scan_state,
                             oop obj, size_t obj_sz, markOop m) {
    if (_avoid_promotion_undo) {
       return copy_to_survivor_space_avoiding_promotion_undo(par_scan_state,
                                                             obj, obj_sz, m);
    }

    return copy_to_survivor_space_with_undo(par_scan_state, obj, obj_sz, m);
  }

  oop copy_to_survivor_space_avoiding_promotion_undo(ParScanThreadState* par_scan_state,
                             oop obj, size_t obj_sz, markOop m);

  oop copy_to_survivor_space_with_undo(ParScanThreadState* par_scan_state,
                             oop obj, size_t obj_sz, markOop m);

  // in support of testing overflow code
  NOT_PRODUCT(int _overflow_counter;)
  NOT_PRODUCT(bool should_simulate_overflow();)

  // Accessor for overflow list
  oop overflow_list() { return _overflow_list; }

  // Push the given (from-space) object on the global overflow list.
  void push_on_overflow_list(oop from_space_obj, ParScanThreadState* par_scan_state);

  // If the global overflow list is non-empty, move some tasks from it
  // onto "work_q" (which need not be empty).  No more than 1/4 of the
  // available space on "work_q" is used.
  bool take_from_overflow_list(ParScanThreadState* par_scan_state);
  bool take_from_overflow_list_work(ParScanThreadState* par_scan_state);

  // The task queues to be used by parallel GC threads.
  ObjToScanQueueSet* task_queues() {
    return _task_queues;
  }

  PLABStats* plab_stats() {
    return &_plab_stats;
  }

  size_t desired_plab_sz() {
    return _plab_stats.desired_plab_sz();
  }

  static oop real_forwardee(oop obj);

  DEBUG_ONLY(static bool is_legal_forward_ptr(oop p);)
};

#endif // SHARE_VM_GC_IMPLEMENTATION_PARNEW_PARNEWGENERATION_HPP
