/*
 * Copyright (c) 2001, 2018, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/shared/referenceDiscoverer.hpp"
#include "gc/shared/referencePolicy.hpp"
#include "gc/shared/referenceProcessorStats.hpp"
#include "memory/referenceType.hpp"
#include "oops/instanceRefKlass.hpp"

class AbstractRefProcTaskExecutor;
class GCTimer;
class ReferencePolicy;
class ReferenceProcessorPhaseTimes;

// List of discovered references.
class DiscoveredList {
public:
  DiscoveredList() : _oop_head(NULL), _compressed_head(0), _len(0) { }
  inline oop head() const;
  HeapWord* adr_head() {
    return UseCompressedOops ? (HeapWord*)&_compressed_head :
                               (HeapWord*)&_oop_head;
  }
  inline void set_head(oop o);
  inline bool is_empty() const;
  size_t length()               { return _len; }
  void   set_length(size_t len) { _len = len;  }
  void   inc_length(size_t inc) { _len += inc; assert(_len > 0, "Error"); }
  void   dec_length(size_t dec) { _len -= dec; }

  inline void clear();
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
  HeapWord*          _prev_discovered_addr;
  oop                _prev_discovered;
  oop                _current_discovered;
  HeapWord*          _current_discovered_addr;
  oop                _next_discovered;

  HeapWord*          _referent_addr;
  oop                _referent;

  OopClosure*        _keep_alive;
  BoolObjectClosure* _is_alive;

  DEBUG_ONLY(
  oop                _first_seen; // cyclic linked list check
  )

  size_t             _processed;
  size_t             _removed;

public:
  inline DiscoveredListIterator(DiscoveredList&    refs_list,
                                OopClosure*        keep_alive,
                                BoolObjectClosure* is_alive);

  // End Of List.
  inline bool has_next() const { return _current_discovered != NULL; }

  // Get oop to the Reference object.
  inline oop obj() const { return _current_discovered; }

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
    _prev_discovered_addr = _current_discovered_addr;
    _prev_discovered = _current_discovered;
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

  // Do enqueuing work, i.e. notifying the GC about the changed discovered pointers.
  void enqueue();

  // Move enqueued references to the reference pending list.
  void complete_enqueue();

  // NULL out referent pointer.
  void clear_referent();

  // Statistics
  inline size_t processed() const { return _processed; }
  inline size_t removed() const { return _removed; }

  inline void move_to_next() {
    if (oopDesc::equals_raw(_current_discovered, _next_discovered)) {
      // End of the list.
      _current_discovered = NULL;
    } else {
      _current_discovered = _next_discovered;
    }
    assert(!oopDesc::equals_raw(_current_discovered, _first_seen), "cyclic ref_list found");
    _processed++;
  }
};

// The ReferenceProcessor class encapsulates the per-"collector" processing
// of java.lang.Reference objects for GC. The interface is useful for supporting
// a generational abstraction, in particular when there are multiple
// generations that are being independently collected -- possibly
// concurrently and/or incrementally.
// ReferenceProcessor class abstracts away from a generational setting
// by using a closure that determines whether a given reference or referent are
// subject to this ReferenceProcessor's discovery, thus allowing its use in a
// straightforward manner in a general, non-generational, non-contiguous generation
// (or heap) setting.
class ReferenceProcessor : public ReferenceDiscoverer {
  friend class RefProcPhase1Task;
  friend class RefProcPhase2Task;
  friend class RefProcPhase3Task;
  friend class RefProcPhase4Task;
public:
  // Names of sub-phases of reference processing. Indicates the type of the reference
  // processed and the associated phase number at the end.
  enum RefProcSubPhases {
    SoftRefSubPhase1,
    SoftRefSubPhase2,
    WeakRefSubPhase2,
    FinalRefSubPhase2,
    FinalRefSubPhase3,
    PhantomRefSubPhase4,
    RefSubPhaseMax
  };

  // Main phases of reference processing.
  enum RefProcPhases {
    RefPhase1,
    RefPhase2,
    RefPhase3,
    RefPhase4,
    RefPhaseMax
  };

private:
  size_t total_count(DiscoveredList lists[]) const;
  void verify_total_count_zero(DiscoveredList lists[], const char* type) NOT_DEBUG_RETURN;

  // The SoftReference master timestamp clock
  static jlong _soft_ref_timestamp_clock;

  BoolObjectClosure* _is_subject_to_discovery; // determines whether a given oop is subject
                                               // to this ReferenceProcessor's discovery
                                               // (and further processing).

  bool        _discovering_refs;        // true when discovery enabled
  bool        _discovery_is_atomic;     // if discovery is atomic wrt
                                        // other collectors in configuration
  bool        _discovery_is_mt;         // true if reference discovery is MT.

  bool        _enqueuing_is_done;       // true if all weak references enqueued
  bool        _processing_is_mt;        // true during phases when
                                        // reference processing is MT.
  uint        _next_id;                 // round-robin mod _num_queues counter in
                                        // support of work distribution

  bool        _adjust_no_of_processing_threads; // allow dynamic adjustment of processing threads
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
  uint            _num_queues;
  // The maximum MT'ness degree of the queues below
  uint            _max_num_queues;

  // Master array of discovered oops
  DiscoveredList* _discovered_refs;

  // Arrays of lists of oops, one per thread (pointers into master array above)
  DiscoveredList* _discoveredSoftRefs;
  DiscoveredList* _discoveredWeakRefs;
  DiscoveredList* _discoveredFinalRefs;
  DiscoveredList* _discoveredPhantomRefs;

  // Phase 1: Re-evaluate soft ref policy.
  void process_soft_ref_reconsider(BoolObjectClosure* is_alive,
                                   OopClosure* keep_alive,
                                   VoidClosure* complete_gc,
                                   AbstractRefProcTaskExecutor*  task_executor,
                                   ReferenceProcessorPhaseTimes* phase_times);

  // Phase 2: Drop Soft/Weak/Final references with a NULL or live referent, and clear
  // and enqueue non-Final references.
  void process_soft_weak_final_refs(BoolObjectClosure* is_alive,
                                    OopClosure* keep_alive,
                                    VoidClosure* complete_gc,
                                    AbstractRefProcTaskExecutor*  task_executor,
                                    ReferenceProcessorPhaseTimes* phase_times);

  // Phase 3: Keep alive followers of Final references, and enqueue.
  void process_final_keep_alive(OopClosure* keep_alive,
                                VoidClosure* complete_gc,
                                AbstractRefProcTaskExecutor*  task_executor,
                                ReferenceProcessorPhaseTimes* phase_times);

  // Phase 4: Drop and keep alive live Phantom references, or clear and enqueue if dead.
  void process_phantom_refs(BoolObjectClosure* is_alive,
                            OopClosure* keep_alive,
                            VoidClosure* complete_gc,
                            AbstractRefProcTaskExecutor*  task_executor,
                            ReferenceProcessorPhaseTimes* phase_times);

  // Work methods used by the process_* methods. All methods return the number of
  // removed elements.

  // (SoftReferences only) Traverse the list and remove any SoftReferences whose
  // referents are not alive, but that should be kept alive for policy reasons.
  // Keep alive the transitive closure of all such referents.
  size_t process_soft_ref_reconsider_work(DiscoveredList&     refs_list,
                                          ReferencePolicy*    policy,
                                          BoolObjectClosure*  is_alive,
                                          OopClosure*         keep_alive,
                                          VoidClosure*        complete_gc);

  // Traverse the list and remove any Refs whose referents are alive,
  // or NULL if discovery is not atomic. Enqueue and clear the reference for
  // others if do_enqueue_and_clear is set.
  size_t process_soft_weak_final_refs_work(DiscoveredList&    refs_list,
                                           BoolObjectClosure* is_alive,
                                           OopClosure*        keep_alive,
                                           bool               do_enqueue_and_clear);

  // Keep alive followers of referents for FinalReferences. Must only be called for
  // those.
  size_t process_final_keep_alive_work(DiscoveredList&    refs_list,
                                       OopClosure*        keep_alive,
                                       VoidClosure*       complete_gc);

  size_t process_phantom_refs_work(DiscoveredList&    refs_list,
                                   BoolObjectClosure* is_alive,
                                   OopClosure*        keep_alive,
                                   VoidClosure*       complete_gc);

public:
  static int number_of_subclasses_of_ref() { return (REF_PHANTOM - REF_OTHER); }

  uint num_queues() const                  { return _num_queues; }
  uint max_num_queues() const              { return _max_num_queues; }
  void set_active_mt_degree(uint v);

  ReferencePolicy* setup_policy(bool always_clear) {
    _current_soft_ref_policy = always_clear ?
      _always_clear_soft_ref_policy : _default_soft_ref_policy;
    _current_soft_ref_policy->setup();   // snapshot the policy threshold
    return _current_soft_ref_policy;
  }

  // "Preclean" all the discovered reference lists by removing references that
  // are active (e.g. due to the mutator calling enqueue()) or with NULL or
  // strongly reachable referents.
  // The first argument is a predicate on an oop that indicates
  // its (strong) reachability and the fourth is a closure that
  // may be used to incrementalize or abort the precleaning process.
  // The caller is responsible for taking care of potential
  // interference with concurrent operations on these lists
  // (or predicates involved) by other threads.
  void preclean_discovered_references(BoolObjectClosure* is_alive,
                                      OopClosure*        keep_alive,
                                      VoidClosure*       complete_gc,
                                      YieldClosure*      yield,
                                      GCTimer*           gc_timer);

private:
  // Returns the name of the discovered reference list
  // occupying the i / _num_queues slot.
  const char* list_name(uint i);

  // "Preclean" the given discovered reference list by removing references with
  // the attributes mentioned in preclean_discovered_references().
  // Supports both normal and fine grain yielding.
  // Returns whether the operation should be aborted.
  bool preclean_discovered_reflist(DiscoveredList&    refs_list,
                                   BoolObjectClosure* is_alive,
                                   OopClosure*        keep_alive,
                                   VoidClosure*       complete_gc,
                                   YieldClosure*      yield);

  // round-robin mod _num_queues (not: _not_ mod _max_num_queues)
  uint next_id() {
    uint id = _next_id;
    assert(!_discovery_is_mt, "Round robin should only be used in serial discovery");
    if (++_next_id == _num_queues) {
      _next_id = 0;
    }
    assert(_next_id < _num_queues, "_next_id %u _num_queues %u _max_num_queues %u", _next_id, _num_queues, _max_num_queues);
    return id;
  }
  DiscoveredList* get_discovered_list(ReferenceType rt);
  inline void add_to_discovered_list_mt(DiscoveredList& refs_list, oop obj,
                                        HeapWord* discovered_addr);

  void clear_discovered_references(DiscoveredList& refs_list);

  void log_reflist(const char* prefix, DiscoveredList list[], uint num_active_queues);
  void log_reflist_counts(DiscoveredList ref_lists[], uint num_active_queues) PRODUCT_RETURN;

  // Balances reference queues.
  void balance_queues(DiscoveredList refs_lists[]);
  bool need_balance_queues(DiscoveredList refs_lists[]);

  // If there is need to balance the given queue, do it.
  void maybe_balance_queues(DiscoveredList refs_lists[]);

  // Update (advance) the soft ref master clock field.
  void update_soft_ref_master_clock();

  bool is_subject_to_discovery(oop const obj) const;

  bool is_mt_processing_set_up(AbstractRefProcTaskExecutor* task_executor) const;

public:
  // Default parameters give you a vanilla reference processor.
  ReferenceProcessor(BoolObjectClosure* is_subject_to_discovery,
                     bool mt_processing = false, uint mt_processing_degree = 1,
                     bool mt_discovery  = false, uint mt_discovery_degree  = 1,
                     bool atomic_discovery = true,
                     BoolObjectClosure* is_alive_non_header = NULL,
                     bool adjust_no_of_processing_threads = false);

  // RefDiscoveryPolicy values
  enum DiscoveryPolicy {
    ReferenceBasedDiscovery = 0,
    ReferentBasedDiscovery  = 1,
    DiscoveryPolicyMin      = ReferenceBasedDiscovery,
    DiscoveryPolicyMax      = ReferentBasedDiscovery
  };

  static void init_statics();

  // get and set "is_alive_non_header" field
  BoolObjectClosure* is_alive_non_header() {
    return _is_alive_non_header;
  }
  void set_is_alive_non_header(BoolObjectClosure* is_alive_non_header) {
    _is_alive_non_header = is_alive_non_header;
  }

  BoolObjectClosure* is_subject_to_discovery_closure() const { return _is_subject_to_discovery; }
  void set_is_subject_to_discovery_closure(BoolObjectClosure* cl) { _is_subject_to_discovery = cl; }

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

  void verify_list(DiscoveredList& ref_list);

  // Discover a Reference object, using appropriate discovery criteria
  virtual bool discover_reference(oop obj, ReferenceType rt);

  // Has discovered references that need handling
  bool has_discovered_references();

  // Process references found during GC (called by the garbage collector)
  ReferenceProcessorStats
  process_discovered_references(BoolObjectClosure*            is_alive,
                                OopClosure*                   keep_alive,
                                VoidClosure*                  complete_gc,
                                AbstractRefProcTaskExecutor*  task_executor,
                                ReferenceProcessorPhaseTimes* phase_times);

  // If a discovery is in process that is being superceded, abandon it: all
  // the discovered lists will be empty, and all the objects on them will
  // have NULL discovered fields.  Must be called only at a safepoint.
  void abandon_partial_discovery();

  size_t total_reference_count(ReferenceType rt) const;

  // debugging
  void verify_no_references_recorded() PRODUCT_RETURN;
  void verify_referent(oop obj)        PRODUCT_RETURN;

  bool adjust_no_of_processing_threads() const { return _adjust_no_of_processing_threads; }
};

// A subject-to-discovery closure that uses a single memory span to determine the area that
// is subject to discovery. Useful for collectors which have contiguous generations.
class SpanSubjectToDiscoveryClosure : public BoolObjectClosure {
  MemRegion _span;

public:
  SpanSubjectToDiscoveryClosure() : BoolObjectClosure(), _span() { }
  SpanSubjectToDiscoveryClosure(MemRegion span) : BoolObjectClosure(), _span(span) { }

  MemRegion span() const { return _span; }

  void set_span(MemRegion mr) {
    _span = mr;
  }

  virtual bool do_object_b(oop obj) {
    return _span.contains(obj);
  }
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

// A utility class to temporarily mutate the subject discovery closure of the
// given ReferenceProcessor in the scope that contains it.
class ReferenceProcessorSubjectToDiscoveryMutator : StackObj {
  ReferenceProcessor* _rp;
  BoolObjectClosure* _saved_cl;

public:
  ReferenceProcessorSubjectToDiscoveryMutator(ReferenceProcessor* rp, BoolObjectClosure* cl):
    _rp(rp) {
    _saved_cl = _rp->is_subject_to_discovery_closure();
    _rp->set_is_subject_to_discovery_closure(cl);
  }

  ~ReferenceProcessorSubjectToDiscoveryMutator() {
    _rp->set_is_subject_to_discovery_closure(_saved_cl);
  }
};

// A utility class to temporarily mutate the span of the
// given ReferenceProcessor in the scope that contains it.
class ReferenceProcessorSpanMutator : StackObj {
  ReferenceProcessor* _rp;
  SpanSubjectToDiscoveryClosure _discoverer;
  BoolObjectClosure* _old_discoverer;

public:
  ReferenceProcessorSpanMutator(ReferenceProcessor* rp,
                                MemRegion span):
    _rp(rp),
    _discoverer(span),
    _old_discoverer(rp->is_subject_to_discovery_closure()) {

    rp->set_is_subject_to_discovery_closure(&_discoverer);
  }

  ~ReferenceProcessorSpanMutator() {
    _rp->set_is_subject_to_discovery_closure(_old_discoverer);
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

  // Executes a task using worker threads.
  virtual void execute(ProcessTask& task, uint ergo_workers) = 0;

  // Switch to single threaded mode.
  virtual void set_single_threaded_mode() { };
};

// Abstract reference processing task to execute.
class AbstractRefProcTaskExecutor::ProcessTask {
protected:
  ReferenceProcessor&           _ref_processor;
  // Indicates whether the phase could generate work that should be balanced across
  // threads after execution.
  bool                          _marks_oops_alive;
  ReferenceProcessorPhaseTimes* _phase_times;

  ProcessTask(ReferenceProcessor& ref_processor,
              bool marks_oops_alive,
              ReferenceProcessorPhaseTimes* phase_times)
    : _ref_processor(ref_processor),
      _marks_oops_alive(marks_oops_alive),
      _phase_times(phase_times)
  { }

public:
  virtual void work(uint worker_id,
                    BoolObjectClosure& is_alive,
                    OopClosure& keep_alive,
                    VoidClosure& complete_gc) = 0;

  bool marks_oops_alive() const { return _marks_oops_alive; }
};

// Temporarily change the number of workers based on given reference count.
// This ergonomically decided worker count will be used to activate worker threads.
class RefProcMTDegreeAdjuster : public StackObj {
  typedef ReferenceProcessor::RefProcPhases RefProcPhases;

  ReferenceProcessor* _rp;
  bool                _saved_mt_processing;
  uint                _saved_num_queues;

  // Calculate based on total of references.
  uint ergo_proc_thread_count(size_t ref_count,
                              uint max_threads,
                              RefProcPhases phase) const;

  bool use_max_threads(RefProcPhases phase) const;

public:
  RefProcMTDegreeAdjuster(ReferenceProcessor* rp,
                          RefProcPhases phase,
                          size_t ref_count);
  ~RefProcMTDegreeAdjuster();
};

#endif // SHARE_VM_GC_SHARED_REFERENCEPROCESSOR_HPP
