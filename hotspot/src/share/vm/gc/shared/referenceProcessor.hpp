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

#ifndef SHARE_VM_GC_SHARED_REFERENCEPROCESSOR_HPP
#define SHARE_VM_GC_SHARED_REFERENCEPROCESSOR_HPP

#include "gc/shared/gcTrace.hpp"
#include "gc/shared/referencePolicy.hpp"
#include "gc/shared/referenceProcessorStats.hpp"
#include "memory/referenceType.hpp"
#include "oops/instanceRefKlass.hpp"

class GCTimer;

// ReferenceProcessor class encapsulates the per-"collector" processing
// of java.lang.Reference objects for GC. The interface is useful for supporting
// a generational abstraction, in particular when there are multiple
// generations that are being independently collected -- possibly
// concurrently and/or incrementally.  Note, however, that the
// ReferenceProcessor class abstracts away from a generational setting
// by using only a heap interval (called "span" below), thus allowing
// its use in a straightforward manner in a general, non-generational
// setting.
//
// The basic idea is that each ReferenceProcessor object concerns
// itself with ("weak") reference processing in a specific "span"
// of the heap of interest to a specific collector. Currently,
// the span is a convex interval of the heap, but, efficiency
// apart, there seems to be no reason it couldn't be extended
// (with appropriate modifications) to any "non-convex interval".

// forward references
class ReferencePolicy;
class AbstractRefProcTaskExecutor;

// List of discovered references.
class DiscoveredList {
public:
  DiscoveredList() : _len(0), _compressed_head(0), _oop_head(NULL) { }
  oop head() const     {
     return UseCompressedOops ?  oopDesc::decode_heap_oop(_compressed_head) :
                                _oop_head;
  }
  HeapWord* adr_head() {
    return UseCompressedOops ? (HeapWord*)&_compressed_head :
                               (HeapWord*)&_oop_head;
  }
  void set_head(oop o) {
    if (UseCompressedOops) {
      // Must compress the head ptr.
      _compressed_head = oopDesc::encode_heap_oop(o);
    } else {
      _oop_head = o;
    }
  }
  bool   is_empty() const       { return head() == NULL; }
  size_t length()               { return _len; }
  void   set_length(size_t len) { _len = len;  }
  void   inc_length(size_t inc) { _len += inc; assert(_len > 0, "Error"); }
  void   dec_length(size_t dec) { _len -= dec; }
private:
  // Set value depending on UseCompressedOops. This could be a template class
  // but then we have to fix all the instantiations and declarations that use this class.
  oop       _oop_head;
  narrowOop _compressed_head;
  size_t _len;
};

// Iterator for the list of discovered references.
class DiscoveredListIterator {
private:
  DiscoveredList&    _refs_list;
  HeapWord*          _prev_next;
  oop                _prev;
  oop                _ref;
  HeapWord*          _discovered_addr;
  oop                _next;
  HeapWord*          _referent_addr;
  oop                _referent;
  OopClosure*        _keep_alive;
  BoolObjectClosure* _is_alive;

  DEBUG_ONLY(
  oop                _first_seen; // cyclic linked list check
  )

  NOT_PRODUCT(
  size_t             _processed;
  size_t             _removed;
  )

public:
  inline DiscoveredListIterator(DiscoveredList&    refs_list,
                                OopClosure*        keep_alive,
                                BoolObjectClosure* is_alive):
    _refs_list(refs_list),
    _prev_next(refs_list.adr_head()),
    _prev(NULL),
    _ref(refs_list.head()),
#ifdef ASSERT
    _first_seen(refs_list.head()),
#endif
#ifndef PRODUCT
    _processed(0),
    _removed(0),
#endif
    _next(NULL),
    _keep_alive(keep_alive),
    _is_alive(is_alive)
{ }

  // End Of List.
  inline bool has_next() const { return _ref != NULL; }

  // Get oop to the Reference object.
  inline oop obj() const { return _ref; }

  // Get oop to the referent object.
  inline oop referent() const { return _referent; }

  // Returns true if referent is alive.
  inline bool is_referent_alive() const {
    return _is_alive->do_object_b(_referent);
  }

  // Loads data for the current reference.
  // The "allow_null_referent" argument tells us to allow for the possibility
  // of a NULL referent in the discovered Reference object. This typically
  // happens in the case of concurrent collectors that may have done the
  // discovery concurrently, or interleaved, with mutator execution.
  void load_ptrs(DEBUG_ONLY(bool allow_null_referent));

  // Move to the next discovered reference.
  inline void next() {
    _prev_next = _discovered_addr;
    _prev = _ref;
    move_to_next();
  }

  // Remove the current reference from the list
  void remove();

  // Make the referent alive.
  inline void make_referent_alive() {
    if (UseCompressedOops) {
      _keep_alive->do_oop((narrowOop*)_referent_addr);
    } else {
      _keep_alive->do_oop((oop*)_referent_addr);
    }
  }

  // NULL out referent pointer.
  void clear_referent();

  // Statistics
  NOT_PRODUCT(
  inline size_t processed() const { return _processed; }
  inline size_t removed() const   { return _removed; }
  )

  inline void move_to_next() {
    if (_ref == _next) {
      // End of the list.
      _ref = NULL;
    } else {
      _ref = _next;
    }
    assert(_ref != _first_seen, "cyclic ref_list found");
    NOT_PRODUCT(_processed++);
  }
};

class ReferenceProcessor : public CHeapObj<mtGC> {

 private:
  size_t total_count(DiscoveredList lists[]);

 protected:
  // The SoftReference master timestamp clock
  static jlong _soft_ref_timestamp_clock;

  MemRegion   _span;                    // (right-open) interval of heap
                                        // subject to wkref discovery

  bool        _discovering_refs;        // true when discovery enabled
  bool        _discovery_is_atomic;     // if discovery is atomic wrt
                                        // other collectors in configuration
  bool        _discovery_is_mt;         // true if reference discovery is MT.

  bool        _enqueuing_is_done;       // true if all weak references enqueued
  bool        _processing_is_mt;        // true during phases when
                                        // reference processing is MT.
  uint        _next_id;                 // round-robin mod _num_q counter in
                                        // support of work distribution

  // For collectors that do not keep GC liveness information
  // in the object header, this field holds a closure that
  // helps the reference processor determine the reachability
  // of an oop. It is currently initialized to NULL for all
  // collectors except for CMS and G1.
  BoolObjectClosure* _is_alive_non_header;

  // Soft ref clearing policies
  // . the default policy
  static ReferencePolicy*   _default_soft_ref_policy;
  // . the "clear all" policy
  static ReferencePolicy*   _always_clear_soft_ref_policy;
  // . the current policy below is either one of the above
  ReferencePolicy*          _current_soft_ref_policy;

  // The discovered ref lists themselves

  // The active MT'ness degree of the queues below
  uint             _num_q;
  // The maximum MT'ness degree of the queues below
  uint             _max_num_q;

  // Master array of discovered oops
  DiscoveredList* _discovered_refs;

  // Arrays of lists of oops, one per thread (pointers into master array above)
  DiscoveredList* _discoveredSoftRefs;
  DiscoveredList* _discoveredWeakRefs;
  DiscoveredList* _discoveredFinalRefs;
  DiscoveredList* _discoveredPhantomRefs;
  DiscoveredList* _discoveredCleanerRefs;

 public:
  static int number_of_subclasses_of_ref() { return (REF_CLEANER - REF_OTHER); }

  uint num_q()                             { return _num_q; }
  uint max_num_q()                         { return _max_num_q; }
  void set_active_mt_degree(uint v)        { _num_q = v; }

  DiscoveredList* discovered_refs()        { return _discovered_refs; }

  ReferencePolicy* setup_policy(bool always_clear) {
    _current_soft_ref_policy = always_clear ?
      _always_clear_soft_ref_policy : _default_soft_ref_policy;
    _current_soft_ref_policy->setup();   // snapshot the policy threshold
    return _current_soft_ref_policy;
  }

  // Process references with a certain reachability level.
  void process_discovered_reflist(DiscoveredList               refs_lists[],
                                  ReferencePolicy*             policy,
                                  bool                         clear_referent,
                                  BoolObjectClosure*           is_alive,
                                  OopClosure*                  keep_alive,
                                  VoidClosure*                 complete_gc,
                                  AbstractRefProcTaskExecutor* task_executor);

  void process_phaseJNI(BoolObjectClosure* is_alive,
                        OopClosure*        keep_alive,
                        VoidClosure*       complete_gc);

  // Work methods used by the method process_discovered_reflist
  // Phase1: keep alive all those referents that are otherwise
  // dead but which must be kept alive by policy (and their closure).
  void process_phase1(DiscoveredList&     refs_list,
                      ReferencePolicy*    policy,
                      BoolObjectClosure*  is_alive,
                      OopClosure*         keep_alive,
                      VoidClosure*        complete_gc);
  // Phase2: remove all those references whose referents are
  // reachable.
  inline void process_phase2(DiscoveredList&    refs_list,
                             BoolObjectClosure* is_alive,
                             OopClosure*        keep_alive,
                             VoidClosure*       complete_gc) {
    if (discovery_is_atomic()) {
      // complete_gc is ignored in this case for this phase
      pp2_work(refs_list, is_alive, keep_alive);
    } else {
      assert(complete_gc != NULL, "Error");
      pp2_work_concurrent_discovery(refs_list, is_alive,
                                    keep_alive, complete_gc);
    }
  }
  // Work methods in support of process_phase2
  void pp2_work(DiscoveredList&    refs_list,
                BoolObjectClosure* is_alive,
                OopClosure*        keep_alive);
  void pp2_work_concurrent_discovery(
                DiscoveredList&    refs_list,
                BoolObjectClosure* is_alive,
                OopClosure*        keep_alive,
                VoidClosure*       complete_gc);
  // Phase3: process the referents by either clearing them
  // or keeping them alive (and their closure)
  void process_phase3(DiscoveredList&    refs_list,
                      bool               clear_referent,
                      BoolObjectClosure* is_alive,
                      OopClosure*        keep_alive,
                      VoidClosure*       complete_gc);

  // Enqueue references with a certain reachability level
  void enqueue_discovered_reflist(DiscoveredList& refs_list, HeapWord* pending_list_addr);

  // "Preclean" all the discovered reference lists
  // by removing references with strongly reachable referents.
  // The first argument is a predicate on an oop that indicates
  // its (strong) reachability and the second is a closure that
  // may be used to incrementalize or abort the precleaning process.
  // The caller is responsible for taking care of potential
  // interference with concurrent operations on these lists
  // (or predicates involved) by other threads. Currently
  // only used by the CMS collector.
  void preclean_discovered_references(BoolObjectClosure* is_alive,
                                      OopClosure*        keep_alive,
                                      VoidClosure*       complete_gc,
                                      YieldClosure*      yield,
                                      GCTimer*           gc_timer);

  // Returns the name of the discovered reference list
  // occupying the i / _num_q slot.
  const char* list_name(uint i);

  void enqueue_discovered_reflists(HeapWord* pending_list_addr, AbstractRefProcTaskExecutor* task_executor);

 protected:
  // "Preclean" the given discovered reference list
  // by removing references with strongly reachable referents.
  // Currently used in support of CMS only.
  void preclean_discovered_reflist(DiscoveredList&    refs_list,
                                   BoolObjectClosure* is_alive,
                                   OopClosure*        keep_alive,
                                   VoidClosure*       complete_gc,
                                   YieldClosure*      yield);

  // round-robin mod _num_q (not: _not_ mode _max_num_q)
  uint next_id() {
    uint id = _next_id;
    if (++_next_id == _num_q) {
      _next_id = 0;
    }
    return id;
  }
  DiscoveredList* get_discovered_list(ReferenceType rt);
  inline void add_to_discovered_list_mt(DiscoveredList& refs_list, oop obj,
                                        HeapWord* discovered_addr);

  void clear_discovered_references(DiscoveredList& refs_list);

  // Calculate the number of jni handles.
  unsigned int count_jni_refs();

  // Balances reference queues.
  void balance_queues(DiscoveredList ref_lists[]);

  // Update (advance) the soft ref master clock field.
  void update_soft_ref_master_clock();

 public:
  // Default parameters give you a vanilla reference processor.
  ReferenceProcessor(MemRegion span,
                     bool mt_processing = false, uint mt_processing_degree = 1,
                     bool mt_discovery  = false, uint mt_discovery_degree  = 1,
                     bool atomic_discovery = true,
                     BoolObjectClosure* is_alive_non_header = NULL);

  // RefDiscoveryPolicy values
  enum DiscoveryPolicy {
    ReferenceBasedDiscovery = 0,
    ReferentBasedDiscovery  = 1,
    DiscoveryPolicyMin      = ReferenceBasedDiscovery,
    DiscoveryPolicyMax      = ReferentBasedDiscovery
  };

  static void init_statics();

 public:
  // get and set "is_alive_non_header" field
  BoolObjectClosure* is_alive_non_header() {
    return _is_alive_non_header;
  }
  void set_is_alive_non_header(BoolObjectClosure* is_alive_non_header) {
    _is_alive_non_header = is_alive_non_header;
  }

  // get and set span
  MemRegion span()                   { return _span; }
  void      set_span(MemRegion span) { _span = span; }

  // start and stop weak ref discovery
  void enable_discovery(bool check_no_refs = true);
  void disable_discovery()  { _discovering_refs = false; }
  bool discovery_enabled()  { return _discovering_refs;  }

  // whether discovery is atomic wrt other collectors
  bool discovery_is_atomic() const { return _discovery_is_atomic; }
  void set_atomic_discovery(bool atomic) { _discovery_is_atomic = atomic; }

  // whether discovery is done by multiple threads same-old-timeously
  bool discovery_is_mt() const { return _discovery_is_mt; }
  void set_mt_discovery(bool mt) { _discovery_is_mt = mt; }

  // Whether we are in a phase when _processing_ is MT.
  bool processing_is_mt() const { return _processing_is_mt; }
  void set_mt_processing(bool mt) { _processing_is_mt = mt; }

  // whether all enqueueing of weak references is complete
  bool enqueuing_is_done()  { return _enqueuing_is_done; }
  void set_enqueuing_is_done(bool v) { _enqueuing_is_done = v; }

  // iterate over oops
  void weak_oops_do(OopClosure* f);       // weak roots

  // Balance each of the discovered lists.
  void balance_all_queues();
  void verify_list(DiscoveredList& ref_list);

  // Discover a Reference object, using appropriate discovery criteria
  bool discover_reference(oop obj, ReferenceType rt);

  // Process references found during GC (called by the garbage collector)
  ReferenceProcessorStats
  process_discovered_references(BoolObjectClosure*           is_alive,
                                OopClosure*                  keep_alive,
                                VoidClosure*                 complete_gc,
                                AbstractRefProcTaskExecutor* task_executor,
                                GCTimer *gc_timer);

  // Enqueue references at end of GC (called by the garbage collector)
  bool enqueue_discovered_references(AbstractRefProcTaskExecutor* task_executor = NULL);

  // If a discovery is in process that is being superceded, abandon it: all
  // the discovered lists will be empty, and all the objects on them will
  // have NULL discovered fields.  Must be called only at a safepoint.
  void abandon_partial_discovery();

  // debugging
  void verify_no_references_recorded() PRODUCT_RETURN;
  void verify_referent(oop obj)        PRODUCT_RETURN;
};

// A utility class to disable reference discovery in
// the scope which contains it, for given ReferenceProcessor.
class NoRefDiscovery: StackObj {
 private:
  ReferenceProcessor* _rp;
  bool _was_discovering_refs;
 public:
  NoRefDiscovery(ReferenceProcessor* rp) : _rp(rp) {
    _was_discovering_refs = _rp->discovery_enabled();
    if (_was_discovering_refs) {
      _rp->disable_discovery();
    }
  }

  ~NoRefDiscovery() {
    if (_was_discovering_refs) {
      _rp->enable_discovery(false /*check_no_refs*/);
    }
  }
};


// A utility class to temporarily mutate the span of the
// given ReferenceProcessor in the scope that contains it.
class ReferenceProcessorSpanMutator: StackObj {
 private:
  ReferenceProcessor* _rp;
  MemRegion           _saved_span;

 public:
  ReferenceProcessorSpanMutator(ReferenceProcessor* rp,
                                MemRegion span):
    _rp(rp) {
    _saved_span = _rp->span();
    _rp->set_span(span);
  }

  ~ReferenceProcessorSpanMutator() {
    _rp->set_span(_saved_span);
  }
};

// A utility class to temporarily change the MT'ness of
// reference discovery for the given ReferenceProcessor
// in the scope that contains it.
class ReferenceProcessorMTDiscoveryMutator: StackObj {
 private:
  ReferenceProcessor* _rp;
  bool                _saved_mt;

 public:
  ReferenceProcessorMTDiscoveryMutator(ReferenceProcessor* rp,
                                       bool mt):
    _rp(rp) {
    _saved_mt = _rp->discovery_is_mt();
    _rp->set_mt_discovery(mt);
  }

  ~ReferenceProcessorMTDiscoveryMutator() {
    _rp->set_mt_discovery(_saved_mt);
  }
};


// A utility class to temporarily change the disposition
// of the "is_alive_non_header" closure field of the
// given ReferenceProcessor in the scope that contains it.
class ReferenceProcessorIsAliveMutator: StackObj {
 private:
  ReferenceProcessor* _rp;
  BoolObjectClosure*  _saved_cl;

 public:
  ReferenceProcessorIsAliveMutator(ReferenceProcessor* rp,
                                   BoolObjectClosure*  cl):
    _rp(rp) {
    _saved_cl = _rp->is_alive_non_header();
    _rp->set_is_alive_non_header(cl);
  }

  ~ReferenceProcessorIsAliveMutator() {
    _rp->set_is_alive_non_header(_saved_cl);
  }
};

// A utility class to temporarily change the disposition
// of the "discovery_is_atomic" field of the
// given ReferenceProcessor in the scope that contains it.
class ReferenceProcessorAtomicMutator: StackObj {
 private:
  ReferenceProcessor* _rp;
  bool                _saved_atomic_discovery;

 public:
  ReferenceProcessorAtomicMutator(ReferenceProcessor* rp,
                                  bool atomic):
    _rp(rp) {
    _saved_atomic_discovery = _rp->discovery_is_atomic();
    _rp->set_atomic_discovery(atomic);
  }

  ~ReferenceProcessorAtomicMutator() {
    _rp->set_atomic_discovery(_saved_atomic_discovery);
  }
};


// A utility class to temporarily change the MT processing
// disposition of the given ReferenceProcessor instance
// in the scope that contains it.
class ReferenceProcessorMTProcMutator: StackObj {
 private:
  ReferenceProcessor* _rp;
  bool  _saved_mt;

 public:
  ReferenceProcessorMTProcMutator(ReferenceProcessor* rp,
                                  bool mt):
    _rp(rp) {
    _saved_mt = _rp->processing_is_mt();
    _rp->set_mt_processing(mt);
  }

  ~ReferenceProcessorMTProcMutator() {
    _rp->set_mt_processing(_saved_mt);
  }
};


// This class is an interface used to implement task execution for the
// reference processing.
class AbstractRefProcTaskExecutor {
public:

  // Abstract tasks to execute.
  class ProcessTask;
  class EnqueueTask;

  // Executes a task using worker threads.
  virtual void execute(ProcessTask& task) = 0;
  virtual void execute(EnqueueTask& task) = 0;

  // Switch to single threaded mode.
  virtual void set_single_threaded_mode() { };
};

// Abstract reference processing task to execute.
class AbstractRefProcTaskExecutor::ProcessTask {
protected:
  ProcessTask(ReferenceProcessor& ref_processor,
              DiscoveredList      refs_lists[],
              bool                marks_oops_alive)
    : _ref_processor(ref_processor),
      _refs_lists(refs_lists),
      _marks_oops_alive(marks_oops_alive)
  { }

public:
  virtual void work(unsigned int work_id, BoolObjectClosure& is_alive,
                    OopClosure& keep_alive,
                    VoidClosure& complete_gc) = 0;

  // Returns true if a task marks some oops as alive.
  bool marks_oops_alive() const
  { return _marks_oops_alive; }

protected:
  ReferenceProcessor& _ref_processor;
  DiscoveredList*     _refs_lists;
  const bool          _marks_oops_alive;
};

// Abstract reference processing task to execute.
class AbstractRefProcTaskExecutor::EnqueueTask {
protected:
  EnqueueTask(ReferenceProcessor& ref_processor,
              DiscoveredList      refs_lists[],
              HeapWord*           pending_list_addr,
              int                 n_queues)
    : _ref_processor(ref_processor),
      _refs_lists(refs_lists),
      _pending_list_addr(pending_list_addr),
      _n_queues(n_queues)
  { }

public:
  virtual void work(unsigned int work_id) = 0;

protected:
  ReferenceProcessor& _ref_processor;
  DiscoveredList*     _refs_lists;
  HeapWord*           _pending_list_addr;
  int                 _n_queues;
};

#endif // SHARE_VM_GC_SHARED_REFERENCEPROCESSOR_HPP
