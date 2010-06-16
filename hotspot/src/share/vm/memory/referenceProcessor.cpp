/*
 * Copyright (c) 2001, 2009, Oracle and/or its affiliates. All rights reserved.
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

# include "incls/_precompiled.incl"
# include "incls/_referenceProcessor.cpp.incl"

ReferencePolicy* ReferenceProcessor::_always_clear_soft_ref_policy = NULL;
ReferencePolicy* ReferenceProcessor::_default_soft_ref_policy      = NULL;
oop              ReferenceProcessor::_sentinelRef = NULL;
const int        subclasses_of_ref                = REF_PHANTOM - REF_OTHER;

// List of discovered references.
class DiscoveredList {
public:
  DiscoveredList() : _len(0), _compressed_head(0), _oop_head(NULL) { }
  oop head() const     {
     return UseCompressedOops ?  oopDesc::decode_heap_oop_not_null(_compressed_head) :
                                _oop_head;
  }
  HeapWord* adr_head() {
    return UseCompressedOops ? (HeapWord*)&_compressed_head :
                               (HeapWord*)&_oop_head;
  }
  void   set_head(oop o) {
    if (UseCompressedOops) {
      // Must compress the head ptr.
      _compressed_head = oopDesc::encode_heap_oop_not_null(o);
    } else {
      _oop_head = o;
    }
  }
  bool   empty() const          { return head() == ReferenceProcessor::sentinel_ref(); }
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

void referenceProcessor_init() {
  ReferenceProcessor::init_statics();
}

void ReferenceProcessor::init_statics() {
  assert(_sentinelRef == NULL, "should be initialized precisely once");
  EXCEPTION_MARK;
  _sentinelRef = instanceKlass::cast(
                    SystemDictionary::Reference_klass())->
                      allocate_permanent_instance(THREAD);

  // Initialize the master soft ref clock.
  java_lang_ref_SoftReference::set_clock(os::javaTimeMillis());

  if (HAS_PENDING_EXCEPTION) {
      Handle ex(THREAD, PENDING_EXCEPTION);
      vm_exit_during_initialization(ex);
  }
  assert(_sentinelRef != NULL && _sentinelRef->is_oop(),
         "Just constructed it!");
  _always_clear_soft_ref_policy = new AlwaysClearPolicy();
  _default_soft_ref_policy      = new COMPILER2_PRESENT(LRUMaxHeapPolicy())
                                      NOT_COMPILER2(LRUCurrentHeapPolicy());
  if (_always_clear_soft_ref_policy == NULL || _default_soft_ref_policy == NULL) {
    vm_exit_during_initialization("Could not allocate reference policy object");
  }
  guarantee(RefDiscoveryPolicy == ReferenceBasedDiscovery ||
            RefDiscoveryPolicy == ReferentBasedDiscovery,
            "Unrecongnized RefDiscoveryPolicy");
}

ReferenceProcessor*
ReferenceProcessor::create_ref_processor(MemRegion          span,
                                         bool               atomic_discovery,
                                         bool               mt_discovery,
                                         BoolObjectClosure* is_alive_non_header,
                                         int                parallel_gc_threads,
                                         bool               mt_processing,
                                         bool               dl_needs_barrier) {
  int mt_degree = 1;
  if (parallel_gc_threads > 1) {
    mt_degree = parallel_gc_threads;
  }
  ReferenceProcessor* rp =
    new ReferenceProcessor(span, atomic_discovery,
                           mt_discovery, mt_degree,
                           mt_processing && (parallel_gc_threads > 0),
                           dl_needs_barrier);
  if (rp == NULL) {
    vm_exit_during_initialization("Could not allocate ReferenceProcessor object");
  }
  rp->set_is_alive_non_header(is_alive_non_header);
  rp->setup_policy(false /* default soft ref policy */);
  return rp;
}

ReferenceProcessor::ReferenceProcessor(MemRegion span,
                                       bool      atomic_discovery,
                                       bool      mt_discovery,
                                       int       mt_degree,
                                       bool      mt_processing,
                                       bool      discovered_list_needs_barrier)  :
  _discovering_refs(false),
  _enqueuing_is_done(false),
  _is_alive_non_header(NULL),
  _discovered_list_needs_barrier(discovered_list_needs_barrier),
  _bs(NULL),
  _processing_is_mt(mt_processing),
  _next_id(0)
{
  _span = span;
  _discovery_is_atomic = atomic_discovery;
  _discovery_is_mt     = mt_discovery;
  _num_q               = mt_degree;
  _discoveredSoftRefs  = NEW_C_HEAP_ARRAY(DiscoveredList, _num_q * subclasses_of_ref);
  if (_discoveredSoftRefs == NULL) {
    vm_exit_during_initialization("Could not allocated RefProc Array");
  }
  _discoveredWeakRefs    = &_discoveredSoftRefs[_num_q];
  _discoveredFinalRefs   = &_discoveredWeakRefs[_num_q];
  _discoveredPhantomRefs = &_discoveredFinalRefs[_num_q];
  assert(sentinel_ref() != NULL, "_sentinelRef is NULL");
  // Initialized all entries to _sentinelRef
  for (int i = 0; i < _num_q * subclasses_of_ref; i++) {
        _discoveredSoftRefs[i].set_head(sentinel_ref());
    _discoveredSoftRefs[i].set_length(0);
  }
  // If we do barreirs, cache a copy of the barrier set.
  if (discovered_list_needs_barrier) {
    _bs = Universe::heap()->barrier_set();
  }
}

#ifndef PRODUCT
void ReferenceProcessor::verify_no_references_recorded() {
  guarantee(!_discovering_refs, "Discovering refs?");
  for (int i = 0; i < _num_q * subclasses_of_ref; i++) {
    guarantee(_discoveredSoftRefs[i].empty(),
              "Found non-empty discovered list");
  }
}
#endif

void ReferenceProcessor::weak_oops_do(OopClosure* f) {
  for (int i = 0; i < _num_q * subclasses_of_ref; i++) {
    if (UseCompressedOops) {
      f->do_oop((narrowOop*)_discoveredSoftRefs[i].adr_head());
    } else {
      f->do_oop((oop*)_discoveredSoftRefs[i].adr_head());
    }
  }
}

void ReferenceProcessor::oops_do(OopClosure* f) {
  f->do_oop(adr_sentinel_ref());
}

void ReferenceProcessor::update_soft_ref_master_clock() {
  // Update (advance) the soft ref master clock field. This must be done
  // after processing the soft ref list.
  jlong now = os::javaTimeMillis();
  jlong clock = java_lang_ref_SoftReference::clock();
  NOT_PRODUCT(
  if (now < clock) {
    warning("time warp: %d to %d", clock, now);
  }
  )
  // In product mode, protect ourselves from system time being adjusted
  // externally and going backward; see note in the implementation of
  // GenCollectedHeap::time_since_last_gc() for the right way to fix
  // this uniformly throughout the VM; see bug-id 4741166. XXX
  if (now > clock) {
    java_lang_ref_SoftReference::set_clock(now);
  }
  // Else leave clock stalled at its old value until time progresses
  // past clock value.
}

void ReferenceProcessor::process_discovered_references(
  BoolObjectClosure*           is_alive,
  OopClosure*                  keep_alive,
  VoidClosure*                 complete_gc,
  AbstractRefProcTaskExecutor* task_executor) {
  NOT_PRODUCT(verify_ok_to_handle_reflists());

  assert(!enqueuing_is_done(), "If here enqueuing should not be complete");
  // Stop treating discovered references specially.
  disable_discovery();

  bool trace_time = PrintGCDetails && PrintReferenceGC;
  // Soft references
  {
    TraceTime tt("SoftReference", trace_time, false, gclog_or_tty);
    process_discovered_reflist(_discoveredSoftRefs, _current_soft_ref_policy, true,
                               is_alive, keep_alive, complete_gc, task_executor);
  }

  update_soft_ref_master_clock();

  // Weak references
  {
    TraceTime tt("WeakReference", trace_time, false, gclog_or_tty);
    process_discovered_reflist(_discoveredWeakRefs, NULL, true,
                               is_alive, keep_alive, complete_gc, task_executor);
  }

  // Final references
  {
    TraceTime tt("FinalReference", trace_time, false, gclog_or_tty);
    process_discovered_reflist(_discoveredFinalRefs, NULL, false,
                               is_alive, keep_alive, complete_gc, task_executor);
  }

  // Phantom references
  {
    TraceTime tt("PhantomReference", trace_time, false, gclog_or_tty);
    process_discovered_reflist(_discoveredPhantomRefs, NULL, false,
                               is_alive, keep_alive, complete_gc, task_executor);
  }

  // Weak global JNI references. It would make more sense (semantically) to
  // traverse these simultaneously with the regular weak references above, but
  // that is not how the JDK1.2 specification is. See #4126360. Native code can
  // thus use JNI weak references to circumvent the phantom references and
  // resurrect a "post-mortem" object.
  {
    TraceTime tt("JNI Weak Reference", trace_time, false, gclog_or_tty);
    if (task_executor != NULL) {
      task_executor->set_single_threaded_mode();
    }
    process_phaseJNI(is_alive, keep_alive, complete_gc);
  }
}

#ifndef PRODUCT
// Calculate the number of jni handles.
uint ReferenceProcessor::count_jni_refs() {
  class AlwaysAliveClosure: public BoolObjectClosure {
  public:
    virtual bool do_object_b(oop obj) { return true; }
    virtual void do_object(oop obj) { assert(false, "Don't call"); }
  };

  class CountHandleClosure: public OopClosure {
  private:
    int _count;
  public:
    CountHandleClosure(): _count(0) {}
    void do_oop(oop* unused)       { _count++; }
    void do_oop(narrowOop* unused) { ShouldNotReachHere(); }
    int count() { return _count; }
  };
  CountHandleClosure global_handle_count;
  AlwaysAliveClosure always_alive;
  JNIHandles::weak_oops_do(&always_alive, &global_handle_count);
  return global_handle_count.count();
}
#endif

void ReferenceProcessor::process_phaseJNI(BoolObjectClosure* is_alive,
                                          OopClosure*        keep_alive,
                                          VoidClosure*       complete_gc) {
#ifndef PRODUCT
  if (PrintGCDetails && PrintReferenceGC) {
    unsigned int count = count_jni_refs();
    gclog_or_tty->print(", %u refs", count);
  }
#endif
  JNIHandles::weak_oops_do(is_alive, keep_alive);
  // Finally remember to keep sentinel around
  keep_alive->do_oop(adr_sentinel_ref());
  complete_gc->do_void();
}


template <class T>
bool enqueue_discovered_ref_helper(ReferenceProcessor* ref,
                                   AbstractRefProcTaskExecutor* task_executor) {

  // Remember old value of pending references list
  T* pending_list_addr = (T*)java_lang_ref_Reference::pending_list_addr();
  T old_pending_list_value = *pending_list_addr;

  // Enqueue references that are not made active again, and
  // clear the decks for the next collection (cycle).
  ref->enqueue_discovered_reflists((HeapWord*)pending_list_addr, task_executor);
  // Do the oop-check on pending_list_addr missed in
  // enqueue_discovered_reflist. We should probably
  // do a raw oop_check so that future such idempotent
  // oop_stores relying on the oop-check side-effect
  // may be elided automatically and safely without
  // affecting correctness.
  oop_store(pending_list_addr, oopDesc::load_decode_heap_oop(pending_list_addr));

  // Stop treating discovered references specially.
  ref->disable_discovery();

  // Return true if new pending references were added
  return old_pending_list_value != *pending_list_addr;
}

bool ReferenceProcessor::enqueue_discovered_references(AbstractRefProcTaskExecutor* task_executor) {
  NOT_PRODUCT(verify_ok_to_handle_reflists());
  if (UseCompressedOops) {
    return enqueue_discovered_ref_helper<narrowOop>(this, task_executor);
  } else {
    return enqueue_discovered_ref_helper<oop>(this, task_executor);
  }
}

void ReferenceProcessor::enqueue_discovered_reflist(DiscoveredList& refs_list,
                                                    HeapWord* pending_list_addr) {
  // Given a list of refs linked through the "discovered" field
  // (java.lang.ref.Reference.discovered) chain them through the
  // "next" field (java.lang.ref.Reference.next) and prepend
  // to the pending list.
  if (TraceReferenceGC && PrintGCDetails) {
    gclog_or_tty->print_cr("ReferenceProcessor::enqueue_discovered_reflist list "
                           INTPTR_FORMAT, (address)refs_list.head());
  }
  oop obj = refs_list.head();
  // Walk down the list, copying the discovered field into
  // the next field and clearing it (except for the last
  // non-sentinel object which is treated specially to avoid
  // confusion with an active reference).
  while (obj != sentinel_ref()) {
    assert(obj->is_instanceRef(), "should be reference object");
    oop next = java_lang_ref_Reference::discovered(obj);
    if (TraceReferenceGC && PrintGCDetails) {
      gclog_or_tty->print_cr("        obj " INTPTR_FORMAT "/next " INTPTR_FORMAT,
                             obj, next);
    }
    assert(java_lang_ref_Reference::next(obj) == NULL,
           "The reference should not be enqueued");
    if (next == sentinel_ref()) {  // obj is last
      // Swap refs_list into pendling_list_addr and
      // set obj's next to what we read from pending_list_addr.
      oop old = oopDesc::atomic_exchange_oop(refs_list.head(), pending_list_addr);
      // Need oop_check on pending_list_addr above;
      // see special oop-check code at the end of
      // enqueue_discovered_reflists() further below.
      if (old == NULL) {
        // obj should be made to point to itself, since
        // pending list was empty.
        java_lang_ref_Reference::set_next(obj, obj);
      } else {
        java_lang_ref_Reference::set_next(obj, old);
      }
    } else {
      java_lang_ref_Reference::set_next(obj, next);
    }
    java_lang_ref_Reference::set_discovered(obj, (oop) NULL);
    obj = next;
  }
}

// Parallel enqueue task
class RefProcEnqueueTask: public AbstractRefProcTaskExecutor::EnqueueTask {
public:
  RefProcEnqueueTask(ReferenceProcessor& ref_processor,
                     DiscoveredList      discovered_refs[],
                     HeapWord*           pending_list_addr,
                     oop                 sentinel_ref,
                     int                 n_queues)
    : EnqueueTask(ref_processor, discovered_refs,
                  pending_list_addr, sentinel_ref, n_queues)
  { }

  virtual void work(unsigned int work_id) {
    assert(work_id < (unsigned int)_ref_processor.num_q(), "Index out-of-bounds");
    // Simplest first cut: static partitioning.
    int index = work_id;
    for (int j = 0; j < subclasses_of_ref; j++, index += _n_queues) {
      _ref_processor.enqueue_discovered_reflist(
        _refs_lists[index], _pending_list_addr);
      _refs_lists[index].set_head(_sentinel_ref);
      _refs_lists[index].set_length(0);
    }
  }
};

// Enqueue references that are not made active again
void ReferenceProcessor::enqueue_discovered_reflists(HeapWord* pending_list_addr,
  AbstractRefProcTaskExecutor* task_executor) {
  if (_processing_is_mt && task_executor != NULL) {
    // Parallel code
    RefProcEnqueueTask tsk(*this, _discoveredSoftRefs,
                           pending_list_addr, sentinel_ref(), _num_q);
    task_executor->execute(tsk);
  } else {
    // Serial code: call the parent class's implementation
    for (int i = 0; i < _num_q * subclasses_of_ref; i++) {
      enqueue_discovered_reflist(_discoveredSoftRefs[i], pending_list_addr);
      _discoveredSoftRefs[i].set_head(sentinel_ref());
      _discoveredSoftRefs[i].set_length(0);
    }
  }
}

// Iterator for the list of discovered references.
class DiscoveredListIterator {
public:
  inline DiscoveredListIterator(DiscoveredList&    refs_list,
                                OopClosure*        keep_alive,
                                BoolObjectClosure* is_alive);

  // End Of List.
  inline bool has_next() const { return _next != ReferenceProcessor::sentinel_ref(); }

  // Get oop to the Reference object.
  inline oop obj() const { return _ref; }

  // Get oop to the referent object.
  inline oop referent() const { return _referent; }

  // Returns true if referent is alive.
  inline bool is_referent_alive() const;

  // Loads data for the current reference.
  // The "allow_null_referent" argument tells us to allow for the possibility
  // of a NULL referent in the discovered Reference object. This typically
  // happens in the case of concurrent collectors that may have done the
  // discovery concurrently, or interleaved, with mutator execution.
  inline void load_ptrs(DEBUG_ONLY(bool allow_null_referent));

  // Move to the next discovered reference.
  inline void next();

  // Remove the current reference from the list
  inline void remove();

  // Make the Reference object active again.
  inline void make_active() { java_lang_ref_Reference::set_next(_ref, NULL); }

  // Make the referent alive.
  inline void make_referent_alive() {
    if (UseCompressedOops) {
      _keep_alive->do_oop((narrowOop*)_referent_addr);
    } else {
      _keep_alive->do_oop((oop*)_referent_addr);
    }
  }

  // Update the discovered field.
  inline void update_discovered() {
    // First _prev_next ref actually points into DiscoveredList (gross).
    if (UseCompressedOops) {
      _keep_alive->do_oop((narrowOop*)_prev_next);
    } else {
      _keep_alive->do_oop((oop*)_prev_next);
    }
  }

  // NULL out referent pointer.
  inline void clear_referent() { oop_store_raw(_referent_addr, NULL); }

  // Statistics
  NOT_PRODUCT(
  inline size_t processed() const { return _processed; }
  inline size_t removed() const   { return _removed; }
  )

  inline void move_to_next();

private:
  DiscoveredList&    _refs_list;
  HeapWord*          _prev_next;
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
};

inline DiscoveredListIterator::DiscoveredListIterator(DiscoveredList&    refs_list,
                                                      OopClosure*        keep_alive,
                                                      BoolObjectClosure* is_alive)
  : _refs_list(refs_list),
    _prev_next(refs_list.adr_head()),
    _ref(refs_list.head()),
#ifdef ASSERT
    _first_seen(refs_list.head()),
#endif
#ifndef PRODUCT
    _processed(0),
    _removed(0),
#endif
    _next(refs_list.head()),
    _keep_alive(keep_alive),
    _is_alive(is_alive)
{ }

inline bool DiscoveredListIterator::is_referent_alive() const {
  return _is_alive->do_object_b(_referent);
}

inline void DiscoveredListIterator::load_ptrs(DEBUG_ONLY(bool allow_null_referent)) {
  _discovered_addr = java_lang_ref_Reference::discovered_addr(_ref);
  oop discovered = java_lang_ref_Reference::discovered(_ref);
  assert(_discovered_addr && discovered->is_oop_or_null(),
         "discovered field is bad");
  _next = discovered;
  _referent_addr = java_lang_ref_Reference::referent_addr(_ref);
  _referent = java_lang_ref_Reference::referent(_ref);
  assert(Universe::heap()->is_in_reserved_or_null(_referent),
         "Wrong oop found in java.lang.Reference object");
  assert(allow_null_referent ?
             _referent->is_oop_or_null()
           : _referent->is_oop(),
         "bad referent");
}

inline void DiscoveredListIterator::next() {
  _prev_next = _discovered_addr;
  move_to_next();
}

inline void DiscoveredListIterator::remove() {
  assert(_ref->is_oop(), "Dropping a bad reference");
  oop_store_raw(_discovered_addr, NULL);
  // First _prev_next ref actually points into DiscoveredList (gross).
  if (UseCompressedOops) {
    // Remove Reference object from list.
    oopDesc::encode_store_heap_oop_not_null((narrowOop*)_prev_next, _next);
  } else {
    // Remove Reference object from list.
    oopDesc::store_heap_oop((oop*)_prev_next, _next);
  }
  NOT_PRODUCT(_removed++);
  _refs_list.dec_length(1);
}

inline void DiscoveredListIterator::move_to_next() {
  _ref = _next;
  assert(_ref != _first_seen, "cyclic ref_list found");
  NOT_PRODUCT(_processed++);
}

// NOTE: process_phase*() are largely similar, and at a high level
// merely iterate over the extant list applying a predicate to
// each of its elements and possibly removing that element from the
// list and applying some further closures to that element.
// We should consider the possibility of replacing these
// process_phase*() methods by abstracting them into
// a single general iterator invocation that receives appropriate
// closures that accomplish this work.

// (SoftReferences only) Traverse the list and remove any SoftReferences whose
// referents are not alive, but that should be kept alive for policy reasons.
// Keep alive the transitive closure of all such referents.
void
ReferenceProcessor::process_phase1(DiscoveredList&    refs_list,
                                   ReferencePolicy*   policy,
                                   BoolObjectClosure* is_alive,
                                   OopClosure*        keep_alive,
                                   VoidClosure*       complete_gc) {
  assert(policy != NULL, "Must have a non-NULL policy");
  DiscoveredListIterator iter(refs_list, keep_alive, is_alive);
  // Decide which softly reachable refs should be kept alive.
  while (iter.has_next()) {
    iter.load_ptrs(DEBUG_ONLY(!discovery_is_atomic() /* allow_null_referent */));
    bool referent_is_dead = (iter.referent() != NULL) && !iter.is_referent_alive();
    if (referent_is_dead && !policy->should_clear_reference(iter.obj())) {
      if (TraceReferenceGC) {
        gclog_or_tty->print_cr("Dropping reference (" INTPTR_FORMAT ": %s"  ") by policy",
                               iter.obj(), iter.obj()->blueprint()->internal_name());
      }
      // Remove Reference object from list
      iter.remove();
      // Make the Reference object active again
      iter.make_active();
      // keep the referent around
      iter.make_referent_alive();
      iter.move_to_next();
    } else {
      iter.next();
    }
  }
  // Close the reachable set
  complete_gc->do_void();
  NOT_PRODUCT(
    if (PrintGCDetails && TraceReferenceGC) {
      gclog_or_tty->print(" Dropped %d dead Refs out of %d "
        "discovered Refs by policy ", iter.removed(), iter.processed());
    }
  )
}

// Traverse the list and remove any Refs that are not active, or
// whose referents are either alive or NULL.
void
ReferenceProcessor::pp2_work(DiscoveredList&    refs_list,
                             BoolObjectClosure* is_alive,
                             OopClosure*        keep_alive) {
  assert(discovery_is_atomic(), "Error");
  DiscoveredListIterator iter(refs_list, keep_alive, is_alive);
  while (iter.has_next()) {
    iter.load_ptrs(DEBUG_ONLY(false /* allow_null_referent */));
    DEBUG_ONLY(oop next = java_lang_ref_Reference::next(iter.obj());)
    assert(next == NULL, "Should not discover inactive Reference");
    if (iter.is_referent_alive()) {
      if (TraceReferenceGC) {
        gclog_or_tty->print_cr("Dropping strongly reachable reference (" INTPTR_FORMAT ": %s)",
                               iter.obj(), iter.obj()->blueprint()->internal_name());
      }
      // The referent is reachable after all.
      // Remove Reference object from list.
      iter.remove();
      // Update the referent pointer as necessary: Note that this
      // should not entail any recursive marking because the
      // referent must already have been traversed.
      iter.make_referent_alive();
      iter.move_to_next();
    } else {
      iter.next();
    }
  }
  NOT_PRODUCT(
    if (PrintGCDetails && TraceReferenceGC) {
      gclog_or_tty->print(" Dropped %d active Refs out of %d "
        "Refs in discovered list ", iter.removed(), iter.processed());
    }
  )
}

void
ReferenceProcessor::pp2_work_concurrent_discovery(DiscoveredList&    refs_list,
                                                  BoolObjectClosure* is_alive,
                                                  OopClosure*        keep_alive,
                                                  VoidClosure*       complete_gc) {
  assert(!discovery_is_atomic(), "Error");
  DiscoveredListIterator iter(refs_list, keep_alive, is_alive);
  while (iter.has_next()) {
    iter.load_ptrs(DEBUG_ONLY(true /* allow_null_referent */));
    HeapWord* next_addr = java_lang_ref_Reference::next_addr(iter.obj());
    oop next = java_lang_ref_Reference::next(iter.obj());
    if ((iter.referent() == NULL || iter.is_referent_alive() ||
         next != NULL)) {
      assert(next->is_oop_or_null(), "bad next field");
      // Remove Reference object from list
      iter.remove();
      // Trace the cohorts
      iter.make_referent_alive();
      if (UseCompressedOops) {
        keep_alive->do_oop((narrowOop*)next_addr);
      } else {
        keep_alive->do_oop((oop*)next_addr);
      }
      iter.move_to_next();
    } else {
      iter.next();
    }
  }
  // Now close the newly reachable set
  complete_gc->do_void();
  NOT_PRODUCT(
    if (PrintGCDetails && TraceReferenceGC) {
      gclog_or_tty->print(" Dropped %d active Refs out of %d "
        "Refs in discovered list ", iter.removed(), iter.processed());
    }
  )
}

// Traverse the list and process the referents, by either
// clearing them or keeping them (and their reachable
// closure) alive.
void
ReferenceProcessor::process_phase3(DiscoveredList&    refs_list,
                                   bool               clear_referent,
                                   BoolObjectClosure* is_alive,
                                   OopClosure*        keep_alive,
                                   VoidClosure*       complete_gc) {
  DiscoveredListIterator iter(refs_list, keep_alive, is_alive);
  while (iter.has_next()) {
    iter.update_discovered();
    iter.load_ptrs(DEBUG_ONLY(false /* allow_null_referent */));
    if (clear_referent) {
      // NULL out referent pointer
      iter.clear_referent();
    } else {
      // keep the referent around
      iter.make_referent_alive();
    }
    if (TraceReferenceGC) {
      gclog_or_tty->print_cr("Adding %sreference (" INTPTR_FORMAT ": %s) as pending",
                             clear_referent ? "cleared " : "",
                             iter.obj(), iter.obj()->blueprint()->internal_name());
    }
    assert(iter.obj()->is_oop(UseConcMarkSweepGC), "Adding a bad reference");
    iter.next();
  }
  // Remember to keep sentinel pointer around
  iter.update_discovered();
  // Close the reachable set
  complete_gc->do_void();
}

void
ReferenceProcessor::abandon_partial_discovered_list(DiscoveredList& refs_list) {
  oop obj = refs_list.head();
  while (obj != sentinel_ref()) {
    oop discovered = java_lang_ref_Reference::discovered(obj);
    java_lang_ref_Reference::set_discovered_raw(obj, NULL);
    obj = discovered;
  }
  refs_list.set_head(sentinel_ref());
  refs_list.set_length(0);
}

void ReferenceProcessor::abandon_partial_discovery() {
  // loop over the lists
  for (int i = 0; i < _num_q * subclasses_of_ref; i++) {
    if (TraceReferenceGC && PrintGCDetails && ((i % _num_q) == 0)) {
      gclog_or_tty->print_cr(
        "\nAbandoning %s discovered list",
        list_name(i));
    }
    abandon_partial_discovered_list(_discoveredSoftRefs[i]);
  }
}

class RefProcPhase1Task: public AbstractRefProcTaskExecutor::ProcessTask {
public:
  RefProcPhase1Task(ReferenceProcessor& ref_processor,
                    DiscoveredList      refs_lists[],
                    ReferencePolicy*    policy,
                    bool                marks_oops_alive)
    : ProcessTask(ref_processor, refs_lists, marks_oops_alive),
      _policy(policy)
  { }
  virtual void work(unsigned int i, BoolObjectClosure& is_alive,
                    OopClosure& keep_alive,
                    VoidClosure& complete_gc)
  {
    _ref_processor.process_phase1(_refs_lists[i], _policy,
                                  &is_alive, &keep_alive, &complete_gc);
  }
private:
  ReferencePolicy* _policy;
};

class RefProcPhase2Task: public AbstractRefProcTaskExecutor::ProcessTask {
public:
  RefProcPhase2Task(ReferenceProcessor& ref_processor,
                    DiscoveredList      refs_lists[],
                    bool                marks_oops_alive)
    : ProcessTask(ref_processor, refs_lists, marks_oops_alive)
  { }
  virtual void work(unsigned int i, BoolObjectClosure& is_alive,
                    OopClosure& keep_alive,
                    VoidClosure& complete_gc)
  {
    _ref_processor.process_phase2(_refs_lists[i],
                                  &is_alive, &keep_alive, &complete_gc);
  }
};

class RefProcPhase3Task: public AbstractRefProcTaskExecutor::ProcessTask {
public:
  RefProcPhase3Task(ReferenceProcessor& ref_processor,
                    DiscoveredList      refs_lists[],
                    bool                clear_referent,
                    bool                marks_oops_alive)
    : ProcessTask(ref_processor, refs_lists, marks_oops_alive),
      _clear_referent(clear_referent)
  { }
  virtual void work(unsigned int i, BoolObjectClosure& is_alive,
                    OopClosure& keep_alive,
                    VoidClosure& complete_gc)
  {
    _ref_processor.process_phase3(_refs_lists[i], _clear_referent,
                                  &is_alive, &keep_alive, &complete_gc);
  }
private:
  bool _clear_referent;
};

// Balances reference queues.
void ReferenceProcessor::balance_queues(DiscoveredList ref_lists[])
{
  // calculate total length
  size_t total_refs = 0;
  for (int i = 0; i < _num_q; ++i) {
    total_refs += ref_lists[i].length();
  }
  size_t avg_refs = total_refs / _num_q + 1;
  int to_idx = 0;
  for (int from_idx = 0; from_idx < _num_q; from_idx++) {
    while (ref_lists[from_idx].length() > avg_refs) {
      assert(to_idx < _num_q, "Sanity Check!");
      if (ref_lists[to_idx].length() < avg_refs) {
        // move superfluous refs
        size_t refs_to_move =
          MIN2(ref_lists[from_idx].length() - avg_refs,
               avg_refs - ref_lists[to_idx].length());
        oop move_head = ref_lists[from_idx].head();
        oop move_tail = move_head;
        oop new_head  = move_head;
        // find an element to split the list on
        for (size_t j = 0; j < refs_to_move; ++j) {
          move_tail = new_head;
          new_head = java_lang_ref_Reference::discovered(new_head);
        }
        java_lang_ref_Reference::set_discovered(move_tail, ref_lists[to_idx].head());
        ref_lists[to_idx].set_head(move_head);
        ref_lists[to_idx].inc_length(refs_to_move);
        ref_lists[from_idx].set_head(new_head);
        ref_lists[from_idx].dec_length(refs_to_move);
      } else {
        ++to_idx;
      }
    }
  }
}

void
ReferenceProcessor::process_discovered_reflist(
  DiscoveredList               refs_lists[],
  ReferencePolicy*             policy,
  bool                         clear_referent,
  BoolObjectClosure*           is_alive,
  OopClosure*                  keep_alive,
  VoidClosure*                 complete_gc,
  AbstractRefProcTaskExecutor* task_executor)
{
  bool mt = task_executor != NULL && _processing_is_mt;
  if (mt && ParallelRefProcBalancingEnabled) {
    balance_queues(refs_lists);
  }
  if (PrintReferenceGC && PrintGCDetails) {
    size_t total = 0;
    for (int i = 0; i < _num_q; ++i) {
      total += refs_lists[i].length();
    }
    gclog_or_tty->print(", %u refs", total);
  }

  // Phase 1 (soft refs only):
  // . Traverse the list and remove any SoftReferences whose
  //   referents are not alive, but that should be kept alive for
  //   policy reasons. Keep alive the transitive closure of all
  //   such referents.
  if (policy != NULL) {
    if (mt) {
      RefProcPhase1Task phase1(*this, refs_lists, policy, true /*marks_oops_alive*/);
      task_executor->execute(phase1);
    } else {
      for (int i = 0; i < _num_q; i++) {
        process_phase1(refs_lists[i], policy,
                       is_alive, keep_alive, complete_gc);
      }
    }
  } else { // policy == NULL
    assert(refs_lists != _discoveredSoftRefs,
           "Policy must be specified for soft references.");
  }

  // Phase 2:
  // . Traverse the list and remove any refs whose referents are alive.
  if (mt) {
    RefProcPhase2Task phase2(*this, refs_lists, !discovery_is_atomic() /*marks_oops_alive*/);
    task_executor->execute(phase2);
  } else {
    for (int i = 0; i < _num_q; i++) {
      process_phase2(refs_lists[i], is_alive, keep_alive, complete_gc);
    }
  }

  // Phase 3:
  // . Traverse the list and process referents as appropriate.
  if (mt) {
    RefProcPhase3Task phase3(*this, refs_lists, clear_referent, true /*marks_oops_alive*/);
    task_executor->execute(phase3);
  } else {
    for (int i = 0; i < _num_q; i++) {
      process_phase3(refs_lists[i], clear_referent,
                     is_alive, keep_alive, complete_gc);
    }
  }
}

void ReferenceProcessor::clean_up_discovered_references() {
  // loop over the lists
  for (int i = 0; i < _num_q * subclasses_of_ref; i++) {
    if (TraceReferenceGC && PrintGCDetails && ((i % _num_q) == 0)) {
      gclog_or_tty->print_cr(
        "\nScrubbing %s discovered list of Null referents",
        list_name(i));
    }
    clean_up_discovered_reflist(_discoveredSoftRefs[i]);
  }
}

void ReferenceProcessor::clean_up_discovered_reflist(DiscoveredList& refs_list) {
  assert(!discovery_is_atomic(), "Else why call this method?");
  DiscoveredListIterator iter(refs_list, NULL, NULL);
  while (iter.has_next()) {
    iter.load_ptrs(DEBUG_ONLY(true /* allow_null_referent */));
    oop next = java_lang_ref_Reference::next(iter.obj());
    assert(next->is_oop_or_null(), "bad next field");
    // If referent has been cleared or Reference is not active,
    // drop it.
    if (iter.referent() == NULL || next != NULL) {
      debug_only(
        if (PrintGCDetails && TraceReferenceGC) {
          gclog_or_tty->print_cr("clean_up_discovered_list: Dropping Reference: "
            INTPTR_FORMAT " with next field: " INTPTR_FORMAT
            " and referent: " INTPTR_FORMAT,
            iter.obj(), next, iter.referent());
        }
      )
      // Remove Reference object from list
      iter.remove();
      iter.move_to_next();
    } else {
      iter.next();
    }
  }
  NOT_PRODUCT(
    if (PrintGCDetails && TraceReferenceGC) {
      gclog_or_tty->print(
        " Removed %d Refs with NULL referents out of %d discovered Refs",
        iter.removed(), iter.processed());
    }
  )
}

inline DiscoveredList* ReferenceProcessor::get_discovered_list(ReferenceType rt) {
  int id = 0;
  // Determine the queue index to use for this object.
  if (_discovery_is_mt) {
    // During a multi-threaded discovery phase,
    // each thread saves to its "own" list.
    Thread* thr = Thread::current();
    assert(thr->is_GC_task_thread(),
           "Dubious cast from Thread* to WorkerThread*?");
    id = ((WorkerThread*)thr)->id();
  } else {
    // single-threaded discovery, we save in round-robin
    // fashion to each of the lists.
    if (_processing_is_mt) {
      id = next_id();
    }
  }
  assert(0 <= id && id < _num_q, "Id is out-of-bounds (call Freud?)");

  // Get the discovered queue to which we will add
  DiscoveredList* list = NULL;
  switch (rt) {
    case REF_OTHER:
      // Unknown reference type, no special treatment
      break;
    case REF_SOFT:
      list = &_discoveredSoftRefs[id];
      break;
    case REF_WEAK:
      list = &_discoveredWeakRefs[id];
      break;
    case REF_FINAL:
      list = &_discoveredFinalRefs[id];
      break;
    case REF_PHANTOM:
      list = &_discoveredPhantomRefs[id];
      break;
    case REF_NONE:
      // we should not reach here if we are an instanceRefKlass
    default:
      ShouldNotReachHere();
  }
  return list;
}

inline void
ReferenceProcessor::add_to_discovered_list_mt(DiscoveredList& refs_list,
                                              oop             obj,
                                              HeapWord*       discovered_addr) {
  assert(_discovery_is_mt, "!_discovery_is_mt should have been handled by caller");
  // First we must make sure this object is only enqueued once. CAS in a non null
  // discovered_addr.
  oop current_head = refs_list.head();

  // Note: In the case of G1, this specific pre-barrier is strictly
  // not necessary because the only case we are interested in
  // here is when *discovered_addr is NULL (see the CAS further below),
  // so this will expand to nothing. As a result, we have manually
  // elided this out for G1, but left in the test for some future
  // collector that might have need for a pre-barrier here.
  if (_discovered_list_needs_barrier && !UseG1GC) {
    if (UseCompressedOops) {
      _bs->write_ref_field_pre((narrowOop*)discovered_addr, current_head);
    } else {
      _bs->write_ref_field_pre((oop*)discovered_addr, current_head);
    }
    guarantee(false, "Need to check non-G1 collector");
  }
  oop retest = oopDesc::atomic_compare_exchange_oop(current_head, discovered_addr,
                                                    NULL);
  if (retest == NULL) {
    // This thread just won the right to enqueue the object.
    // We have separate lists for enqueueing so no synchronization
    // is necessary.
    refs_list.set_head(obj);
    refs_list.inc_length(1);
    if (_discovered_list_needs_barrier) {
      _bs->write_ref_field((void*)discovered_addr, current_head);
    }
  } else {
    // If retest was non NULL, another thread beat us to it:
    // The reference has already been discovered...
    if (TraceReferenceGC) {
      gclog_or_tty->print_cr("Already enqueued reference (" INTPTR_FORMAT ": %s)",
                             obj, obj->blueprint()->internal_name());
    }
  }
}

// We mention two of several possible choices here:
// #0: if the reference object is not in the "originating generation"
//     (or part of the heap being collected, indicated by our "span"
//     we don't treat it specially (i.e. we scan it as we would
//     a normal oop, treating its references as strong references).
//     This means that references can't be enqueued unless their
//     referent is also in the same span. This is the simplest,
//     most "local" and most conservative approach, albeit one
//     that may cause weak references to be enqueued least promptly.
//     We call this choice the "ReferenceBasedDiscovery" policy.
// #1: the reference object may be in any generation (span), but if
//     the referent is in the generation (span) being currently collected
//     then we can discover the reference object, provided
//     the object has not already been discovered by
//     a different concurrently running collector (as may be the
//     case, for instance, if the reference object is in CMS and
//     the referent in DefNewGeneration), and provided the processing
//     of this reference object by the current collector will
//     appear atomic to every other collector in the system.
//     (Thus, for instance, a concurrent collector may not
//     discover references in other generations even if the
//     referent is in its own generation). This policy may,
//     in certain cases, enqueue references somewhat sooner than
//     might Policy #0 above, but at marginally increased cost
//     and complexity in processing these references.
//     We call this choice the "RefeferentBasedDiscovery" policy.
bool ReferenceProcessor::discover_reference(oop obj, ReferenceType rt) {
  // We enqueue references only if we are discovering refs
  // (rather than processing discovered refs).
  if (!_discovering_refs || !RegisterReferences) {
    return false;
  }
  // We only enqueue active references.
  oop next = java_lang_ref_Reference::next(obj);
  if (next != NULL) {
    return false;
  }

  HeapWord* obj_addr = (HeapWord*)obj;
  if (RefDiscoveryPolicy == ReferenceBasedDiscovery &&
      !_span.contains(obj_addr)) {
    // Reference is not in the originating generation;
    // don't treat it specially (i.e. we want to scan it as a normal
    // object with strong references).
    return false;
  }

  // We only enqueue references whose referents are not (yet) strongly
  // reachable.
  if (is_alive_non_header() != NULL) {
    oop referent = java_lang_ref_Reference::referent(obj);
    // In the case of non-concurrent discovery, the last
    // disjunct below should hold. It may not hold in the
    // case of concurrent discovery because mutators may
    // concurrently clear() a Reference.
    assert(UseConcMarkSweepGC || UseG1GC || referent != NULL,
           "Refs with null referents already filtered");
    if (is_alive_non_header()->do_object_b(referent)) {
      return false;  // referent is reachable
    }
  }
  if (rt == REF_SOFT) {
    // For soft refs we can decide now if these are not
    // current candidates for clearing, in which case we
    // can mark through them now, rather than delaying that
    // to the reference-processing phase. Since all current
    // time-stamp policies advance the soft-ref clock only
    // at a major collection cycle, this is always currently
    // accurate.
    if (!_current_soft_ref_policy->should_clear_reference(obj)) {
      return false;
    }
  }

  HeapWord* const discovered_addr = java_lang_ref_Reference::discovered_addr(obj);
  const oop  discovered = java_lang_ref_Reference::discovered(obj);
  assert(discovered->is_oop_or_null(), "bad discovered field");
  if (discovered != NULL) {
    // The reference has already been discovered...
    if (TraceReferenceGC) {
      gclog_or_tty->print_cr("Already enqueued reference (" INTPTR_FORMAT ": %s)",
                             obj, obj->blueprint()->internal_name());
    }
    if (RefDiscoveryPolicy == ReferentBasedDiscovery) {
      // assumes that an object is not processed twice;
      // if it's been already discovered it must be on another
      // generation's discovered list; so we won't discover it.
      return false;
    } else {
      assert(RefDiscoveryPolicy == ReferenceBasedDiscovery,
             "Unrecognized policy");
      // Check assumption that an object is not potentially
      // discovered twice except by concurrent collectors that potentially
      // trace the same Reference object twice.
      assert(UseConcMarkSweepGC,
             "Only possible with an incremental-update concurrent collector");
      return true;
    }
  }

  if (RefDiscoveryPolicy == ReferentBasedDiscovery) {
    oop referent = java_lang_ref_Reference::referent(obj);
    assert(referent->is_oop(), "bad referent");
    // enqueue if and only if either:
    // reference is in our span or
    // we are an atomic collector and referent is in our span
    if (_span.contains(obj_addr) ||
        (discovery_is_atomic() && _span.contains(referent))) {
      // should_enqueue = true;
    } else {
      return false;
    }
  } else {
    assert(RefDiscoveryPolicy == ReferenceBasedDiscovery &&
           _span.contains(obj_addr), "code inconsistency");
  }

  // Get the right type of discovered queue head.
  DiscoveredList* list = get_discovered_list(rt);
  if (list == NULL) {
    return false;   // nothing special needs to be done
  }

  if (_discovery_is_mt) {
    add_to_discovered_list_mt(*list, obj, discovered_addr);
  } else {
    // If "_discovered_list_needs_barrier", we do write barriers when
    // updating the discovered reference list.  Otherwise, we do a raw store
    // here: the field will be visited later when processing the discovered
    // references.
    oop current_head = list->head();
    // As in the case further above, since we are over-writing a NULL
    // pre-value, we can safely elide the pre-barrier here for the case of G1.
    assert(discovered == NULL, "control point invariant");
    if (_discovered_list_needs_barrier && !UseG1GC) { // safe to elide for G1
      if (UseCompressedOops) {
        _bs->write_ref_field_pre((narrowOop*)discovered_addr, current_head);
      } else {
        _bs->write_ref_field_pre((oop*)discovered_addr, current_head);
      }
      guarantee(false, "Need to check non-G1 collector");
    }
    oop_store_raw(discovered_addr, current_head);
    if (_discovered_list_needs_barrier) {
      _bs->write_ref_field((void*)discovered_addr, current_head);
    }
    list->set_head(obj);
    list->inc_length(1);
  }

  // In the MT discovery case, it is currently possible to see
  // the following message multiple times if several threads
  // discover a reference about the same time. Only one will
  // however have actually added it to the disocvered queue.
  // One could let add_to_discovered_list_mt() return an
  // indication for success in queueing (by 1 thread) or
  // failure (by all other threads), but I decided the extra
  // code was not worth the effort for something that is
  // only used for debugging support.
  if (TraceReferenceGC) {
    oop referent = java_lang_ref_Reference::referent(obj);
    if (PrintGCDetails) {
      gclog_or_tty->print_cr("Enqueued reference (" INTPTR_FORMAT ": %s)",
                             obj, obj->blueprint()->internal_name());
    }
    assert(referent->is_oop(), "Enqueued a bad referent");
  }
  assert(obj->is_oop(), "Enqueued a bad reference");
  return true;
}

// Preclean the discovered references by removing those
// whose referents are alive, and by marking from those that
// are not active. These lists can be handled here
// in any order and, indeed, concurrently.
void ReferenceProcessor::preclean_discovered_references(
  BoolObjectClosure* is_alive,
  OopClosure* keep_alive,
  VoidClosure* complete_gc,
  YieldClosure* yield,
  bool should_unload_classes) {

  NOT_PRODUCT(verify_ok_to_handle_reflists());

#ifdef ASSERT
  bool must_remember_klasses = ClassUnloading && !UseConcMarkSweepGC ||
                               CMSClassUnloadingEnabled && UseConcMarkSweepGC ||
                               ExplicitGCInvokesConcurrentAndUnloadsClasses &&
                                 UseConcMarkSweepGC && should_unload_classes;
  RememberKlassesChecker mx(must_remember_klasses);
#endif
  // Soft references
  {
    TraceTime tt("Preclean SoftReferences", PrintGCDetails && PrintReferenceGC,
              false, gclog_or_tty);
    for (int i = 0; i < _num_q; i++) {
      if (yield->should_return()) {
        return;
      }
      preclean_discovered_reflist(_discoveredSoftRefs[i], is_alive,
                                  keep_alive, complete_gc, yield);
    }
  }

  // Weak references
  {
    TraceTime tt("Preclean WeakReferences", PrintGCDetails && PrintReferenceGC,
              false, gclog_or_tty);
    for (int i = 0; i < _num_q; i++) {
      if (yield->should_return()) {
        return;
      }
      preclean_discovered_reflist(_discoveredWeakRefs[i], is_alive,
                                  keep_alive, complete_gc, yield);
    }
  }

  // Final references
  {
    TraceTime tt("Preclean FinalReferences", PrintGCDetails && PrintReferenceGC,
              false, gclog_or_tty);
    for (int i = 0; i < _num_q; i++) {
      if (yield->should_return()) {
        return;
      }
      preclean_discovered_reflist(_discoveredFinalRefs[i], is_alive,
                                  keep_alive, complete_gc, yield);
    }
  }

  // Phantom references
  {
    TraceTime tt("Preclean PhantomReferences", PrintGCDetails && PrintReferenceGC,
              false, gclog_or_tty);
    for (int i = 0; i < _num_q; i++) {
      if (yield->should_return()) {
        return;
      }
      preclean_discovered_reflist(_discoveredPhantomRefs[i], is_alive,
                                  keep_alive, complete_gc, yield);
    }
  }
}

// Walk the given discovered ref list, and remove all reference objects
// whose referents are still alive, whose referents are NULL or which
// are not active (have a non-NULL next field). NOTE: When we are
// thus precleaning the ref lists (which happens single-threaded today),
// we do not disable refs discovery to honour the correct semantics of
// java.lang.Reference. As a result, we need to be careful below
// that ref removal steps interleave safely with ref discovery steps
// (in this thread).
void
ReferenceProcessor::preclean_discovered_reflist(DiscoveredList&    refs_list,
                                                BoolObjectClosure* is_alive,
                                                OopClosure*        keep_alive,
                                                VoidClosure*       complete_gc,
                                                YieldClosure*      yield) {
  DiscoveredListIterator iter(refs_list, keep_alive, is_alive);
  while (iter.has_next()) {
    iter.load_ptrs(DEBUG_ONLY(true /* allow_null_referent */));
    oop obj = iter.obj();
    oop next = java_lang_ref_Reference::next(obj);
    if (iter.referent() == NULL || iter.is_referent_alive() ||
        next != NULL) {
      // The referent has been cleared, or is alive, or the Reference is not
      // active; we need to trace and mark its cohort.
      if (TraceReferenceGC) {
        gclog_or_tty->print_cr("Precleaning Reference (" INTPTR_FORMAT ": %s)",
                               iter.obj(), iter.obj()->blueprint()->internal_name());
      }
      // Remove Reference object from list
      iter.remove();
      // Keep alive its cohort.
      iter.make_referent_alive();
      if (UseCompressedOops) {
        narrowOop* next_addr = (narrowOop*)java_lang_ref_Reference::next_addr(obj);
        keep_alive->do_oop(next_addr);
      } else {
        oop* next_addr = (oop*)java_lang_ref_Reference::next_addr(obj);
        keep_alive->do_oop(next_addr);
      }
      iter.move_to_next();
    } else {
      iter.next();
    }
  }
  // Close the reachable set
  complete_gc->do_void();

  NOT_PRODUCT(
    if (PrintGCDetails && PrintReferenceGC) {
      gclog_or_tty->print(" Dropped %d Refs out of %d "
        "Refs in discovered list ", iter.removed(), iter.processed());
    }
  )
}

const char* ReferenceProcessor::list_name(int i) {
   assert(i >= 0 && i <= _num_q * subclasses_of_ref, "Out of bounds index");
   int j = i / _num_q;
   switch (j) {
     case 0: return "SoftRef";
     case 1: return "WeakRef";
     case 2: return "FinalRef";
     case 3: return "PhantomRef";
   }
   ShouldNotReachHere();
   return NULL;
}

#ifndef PRODUCT
void ReferenceProcessor::verify_ok_to_handle_reflists() {
  // empty for now
}
#endif

void ReferenceProcessor::verify() {
  guarantee(sentinel_ref() != NULL && sentinel_ref()->is_oop(), "Lost _sentinelRef");
}

#ifndef PRODUCT
void ReferenceProcessor::clear_discovered_references() {
  guarantee(!_discovering_refs, "Discovering refs?");
  for (int i = 0; i < _num_q * subclasses_of_ref; i++) {
    oop obj = _discoveredSoftRefs[i].head();
    while (obj != sentinel_ref()) {
      oop next = java_lang_ref_Reference::discovered(obj);
      java_lang_ref_Reference::set_discovered(obj, (oop) NULL);
      obj = next;
    }
    _discoveredSoftRefs[i].set_head(sentinel_ref());
    _discoveredSoftRefs[i].set_length(0);
  }
}
#endif // PRODUCT
