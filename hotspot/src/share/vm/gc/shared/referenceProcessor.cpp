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

#include "precompiled.hpp"
#include "classfile/javaClasses.hpp"
#include "classfile/systemDictionary.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/collectedHeap.inline.hpp"
#include "gc/shared/gcTimer.hpp"
#include "gc/shared/gcTraceTime.inline.hpp"
#include "gc/shared/referencePolicy.hpp"
#include "gc/shared/referenceProcessor.hpp"
#include "logging/log.hpp"
#include "memory/allocation.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/java.hpp"
#include "runtime/jniHandles.hpp"

ReferencePolicy* ReferenceProcessor::_always_clear_soft_ref_policy = NULL;
ReferencePolicy* ReferenceProcessor::_default_soft_ref_policy      = NULL;
jlong            ReferenceProcessor::_soft_ref_timestamp_clock = 0;

void referenceProcessor_init() {
  ReferenceProcessor::init_statics();
}

void ReferenceProcessor::init_statics() {
  // We need a monotonically non-decreasing time in ms but
  // os::javaTimeMillis() does not guarantee monotonicity.
  jlong now = os::javaTimeNanos() / NANOSECS_PER_MILLISEC;

  // Initialize the soft ref timestamp clock.
  _soft_ref_timestamp_clock = now;
  // Also update the soft ref clock in j.l.r.SoftReference
  java_lang_ref_SoftReference::set_clock(_soft_ref_timestamp_clock);

  _always_clear_soft_ref_policy = new AlwaysClearPolicy();
#if defined(COMPILER2) || INCLUDE_JVMCI
  _default_soft_ref_policy      = new LRUMaxHeapPolicy();
#else
  _default_soft_ref_policy      = new LRUCurrentHeapPolicy();
#endif
  if (_always_clear_soft_ref_policy == NULL || _default_soft_ref_policy == NULL) {
    vm_exit_during_initialization("Could not allocate reference policy object");
  }
  guarantee(RefDiscoveryPolicy == ReferenceBasedDiscovery ||
            RefDiscoveryPolicy == ReferentBasedDiscovery,
            "Unrecognized RefDiscoveryPolicy");
}

void ReferenceProcessor::enable_discovery(bool check_no_refs) {
#ifdef ASSERT
  // Verify that we're not currently discovering refs
  assert(!_discovering_refs, "nested call?");

  if (check_no_refs) {
    // Verify that the discovered lists are empty
    verify_no_references_recorded();
  }
#endif // ASSERT

  // Someone could have modified the value of the static
  // field in the j.l.r.SoftReference class that holds the
  // soft reference timestamp clock using reflection or
  // Unsafe between GCs. Unconditionally update the static
  // field in ReferenceProcessor here so that we use the new
  // value during reference discovery.

  _soft_ref_timestamp_clock = java_lang_ref_SoftReference::clock();
  _discovering_refs = true;
}

ReferenceProcessor::ReferenceProcessor(MemRegion span,
                                       bool      mt_processing,
                                       uint      mt_processing_degree,
                                       bool      mt_discovery,
                                       uint      mt_discovery_degree,
                                       bool      atomic_discovery,
                                       BoolObjectClosure* is_alive_non_header)  :
  _discovering_refs(false),
  _enqueuing_is_done(false),
  _is_alive_non_header(is_alive_non_header),
  _processing_is_mt(mt_processing),
  _next_id(0)
{
  _span = span;
  _discovery_is_atomic = atomic_discovery;
  _discovery_is_mt     = mt_discovery;
  _num_q               = MAX2(1U, mt_processing_degree);
  _max_num_q           = MAX2(_num_q, mt_discovery_degree);
  _discovered_refs     = NEW_C_HEAP_ARRAY(DiscoveredList,
            _max_num_q * number_of_subclasses_of_ref(), mtGC);

  if (_discovered_refs == NULL) {
    vm_exit_during_initialization("Could not allocated RefProc Array");
  }
  _discoveredSoftRefs    = &_discovered_refs[0];
  _discoveredWeakRefs    = &_discoveredSoftRefs[_max_num_q];
  _discoveredFinalRefs   = &_discoveredWeakRefs[_max_num_q];
  _discoveredPhantomRefs = &_discoveredFinalRefs[_max_num_q];
  _discoveredCleanerRefs = &_discoveredPhantomRefs[_max_num_q];

  // Initialize all entries to NULL
  for (uint i = 0; i < _max_num_q * number_of_subclasses_of_ref(); i++) {
    _discovered_refs[i].set_head(NULL);
    _discovered_refs[i].set_length(0);
  }

  setup_policy(false /* default soft ref policy */);
}

#ifndef PRODUCT
void ReferenceProcessor::verify_no_references_recorded() {
  guarantee(!_discovering_refs, "Discovering refs?");
  for (uint i = 0; i < _max_num_q * number_of_subclasses_of_ref(); i++) {
    guarantee(_discovered_refs[i].is_empty(),
              "Found non-empty discovered list");
  }
}
#endif

void ReferenceProcessor::weak_oops_do(OopClosure* f) {
  for (uint i = 0; i < _max_num_q * number_of_subclasses_of_ref(); i++) {
    if (UseCompressedOops) {
      f->do_oop((narrowOop*)_discovered_refs[i].adr_head());
    } else {
      f->do_oop((oop*)_discovered_refs[i].adr_head());
    }
  }
}

void ReferenceProcessor::update_soft_ref_master_clock() {
  // Update (advance) the soft ref master clock field. This must be done
  // after processing the soft ref list.

  // We need a monotonically non-decreasing time in ms but
  // os::javaTimeMillis() does not guarantee monotonicity.
  jlong now = os::javaTimeNanos() / NANOSECS_PER_MILLISEC;
  jlong soft_ref_clock = java_lang_ref_SoftReference::clock();
  assert(soft_ref_clock == _soft_ref_timestamp_clock, "soft ref clocks out of sync");

  NOT_PRODUCT(
  if (now < _soft_ref_timestamp_clock) {
    warning("time warp: " JLONG_FORMAT " to " JLONG_FORMAT,
            _soft_ref_timestamp_clock, now);
  }
  )
  // The values of now and _soft_ref_timestamp_clock are set using
  // javaTimeNanos(), which is guaranteed to be monotonically
  // non-decreasing provided the underlying platform provides such
  // a time source (and it is bug free).
  // In product mode, however, protect ourselves from non-monotonicity.
  if (now > _soft_ref_timestamp_clock) {
    _soft_ref_timestamp_clock = now;
    java_lang_ref_SoftReference::set_clock(now);
  }
  // Else leave clock stalled at its old value until time progresses
  // past clock value.
}

size_t ReferenceProcessor::total_count(DiscoveredList lists[]) {
  size_t total = 0;
  for (uint i = 0; i < _max_num_q; ++i) {
    total += lists[i].length();
  }
  return total;
}

ReferenceProcessorStats ReferenceProcessor::process_discovered_references(
  BoolObjectClosure*           is_alive,
  OopClosure*                  keep_alive,
  VoidClosure*                 complete_gc,
  AbstractRefProcTaskExecutor* task_executor,
  GCTimer*                     gc_timer) {

  assert(!enqueuing_is_done(), "If here enqueuing should not be complete");
  // Stop treating discovered references specially.
  disable_discovery();

  // If discovery was concurrent, someone could have modified
  // the value of the static field in the j.l.r.SoftReference
  // class that holds the soft reference timestamp clock using
  // reflection or Unsafe between when discovery was enabled and
  // now. Unconditionally update the static field in ReferenceProcessor
  // here so that we use the new value during processing of the
  // discovered soft refs.

  _soft_ref_timestamp_clock = java_lang_ref_SoftReference::clock();

  // Include cleaners in phantom statistics.  We expect Cleaner
  // references to be temporary, and don't want to deal with
  // possible incompatibilities arising from making it more visible.
  ReferenceProcessorStats stats(
      total_count(_discoveredSoftRefs),
      total_count(_discoveredWeakRefs),
      total_count(_discoveredFinalRefs),
      total_count(_discoveredPhantomRefs) + total_count(_discoveredCleanerRefs));

  // Soft references
  {
    GCTraceTime(Debug, gc, ref) tt("SoftReference", gc_timer);
    process_discovered_reflist(_discoveredSoftRefs, _current_soft_ref_policy, true,
                               is_alive, keep_alive, complete_gc, task_executor);
  }

  update_soft_ref_master_clock();

  // Weak references
  {
    GCTraceTime(Debug, gc, ref) tt("WeakReference", gc_timer);
    process_discovered_reflist(_discoveredWeakRefs, NULL, true,
                               is_alive, keep_alive, complete_gc, task_executor);
  }

  // Final references
  {
    GCTraceTime(Debug, gc, ref) tt("FinalReference", gc_timer);
    process_discovered_reflist(_discoveredFinalRefs, NULL, false,
                               is_alive, keep_alive, complete_gc, task_executor);
  }

  // Phantom references
  {
    GCTraceTime(Debug, gc, ref) tt("PhantomReference", gc_timer);
    process_discovered_reflist(_discoveredPhantomRefs, NULL, true,
                               is_alive, keep_alive, complete_gc, task_executor);

    // Process cleaners, but include them in phantom timing.  We expect
    // Cleaner references to be temporary, and don't want to deal with
    // possible incompatibilities arising from making it more visible.
    process_discovered_reflist(_discoveredCleanerRefs, NULL, true,
                                 is_alive, keep_alive, complete_gc, task_executor);
  }

  // Weak global JNI references. It would make more sense (semantically) to
  // traverse these simultaneously with the regular weak references above, but
  // that is not how the JDK1.2 specification is. See #4126360. Native code can
  // thus use JNI weak references to circumvent the phantom references and
  // resurrect a "post-mortem" object.
  {
    GCTraceTime(Debug, gc, ref) tt("JNI Weak Reference", gc_timer);
    if (task_executor != NULL) {
      task_executor->set_single_threaded_mode();
    }
    process_phaseJNI(is_alive, keep_alive, complete_gc);
  }

  log_debug(gc, ref)("Ref Counts: Soft: " SIZE_FORMAT " Weak: " SIZE_FORMAT " Final: " SIZE_FORMAT " Phantom: " SIZE_FORMAT,
                     stats.soft_count(), stats.weak_count(), stats.final_count(), stats.phantom_count());
  log_develop_trace(gc, ref)("JNI Weak Reference count: " SIZE_FORMAT, count_jni_refs());

  return stats;
}

#ifndef PRODUCT
// Calculate the number of jni handles.
size_t ReferenceProcessor::count_jni_refs() {
  class AlwaysAliveClosure: public BoolObjectClosure {
  public:
    virtual bool do_object_b(oop obj) { return true; }
  };

  class CountHandleClosure: public OopClosure {
  private:
    size_t _count;
  public:
    CountHandleClosure(): _count(0) {}
    void do_oop(oop* unused)       { _count++; }
    void do_oop(narrowOop* unused) { ShouldNotReachHere(); }
    size_t count() { return _count; }
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
  JNIHandles::weak_oops_do(is_alive, keep_alive);
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
  // Do the post-barrier on pending_list_addr missed in
  // enqueue_discovered_reflist.
  oopDesc::bs()->write_ref_field(pending_list_addr, oopDesc::load_decode_heap_oop(pending_list_addr));

  // Stop treating discovered references specially.
  ref->disable_discovery();

  // Return true if new pending references were added
  return old_pending_list_value != *pending_list_addr;
}

bool ReferenceProcessor::enqueue_discovered_references(AbstractRefProcTaskExecutor* task_executor) {
  if (UseCompressedOops) {
    return enqueue_discovered_ref_helper<narrowOop>(this, task_executor);
  } else {
    return enqueue_discovered_ref_helper<oop>(this, task_executor);
  }
}

void ReferenceProcessor::enqueue_discovered_reflist(DiscoveredList& refs_list,
                                                    HeapWord* pending_list_addr) {
  // Given a list of refs linked through the "discovered" field
  // (java.lang.ref.Reference.discovered), self-loop their "next" field
  // thus distinguishing them from active References, then
  // prepend them to the pending list.
  //
  // The Java threads will see the Reference objects linked together through
  // the discovered field. Instead of trying to do the write barrier updates
  // in all places in the reference processor where we manipulate the discovered
  // field we make sure to do the barrier here where we anyway iterate through
  // all linked Reference objects. Note that it is important to not dirty any
  // cards during reference processing since this will cause card table
  // verification to fail for G1.
  log_develop_trace(gc, ref)("ReferenceProcessor::enqueue_discovered_reflist list " INTPTR_FORMAT, p2i(refs_list.head()));

  oop obj = NULL;
  oop next_d = refs_list.head();
  // Walk down the list, self-looping the next field
  // so that the References are not considered active.
  while (obj != next_d) {
    obj = next_d;
    assert(obj->is_instance(), "should be an instance object");
    assert(InstanceKlass::cast(obj->klass())->is_reference_instance_klass(), "should be reference object");
    next_d = java_lang_ref_Reference::discovered(obj);
    log_develop_trace(gc, ref)("        obj " INTPTR_FORMAT "/next_d " INTPTR_FORMAT, p2i(obj), p2i(next_d));
    assert(java_lang_ref_Reference::next(obj) == NULL,
           "Reference not active; should not be discovered");
    // Self-loop next, so as to make Ref not active.
    java_lang_ref_Reference::set_next_raw(obj, obj);
    if (next_d != obj) {
      oopDesc::bs()->write_ref_field(java_lang_ref_Reference::discovered_addr(obj), next_d);
    } else {
      // This is the last object.
      // Swap refs_list into pending_list_addr and
      // set obj's discovered to what we read from pending_list_addr.
      oop old = oopDesc::atomic_exchange_oop(refs_list.head(), pending_list_addr);
      // Need post-barrier on pending_list_addr. See enqueue_discovered_ref_helper() above.
      java_lang_ref_Reference::set_discovered_raw(obj, old); // old may be NULL
      oopDesc::bs()->write_ref_field(java_lang_ref_Reference::discovered_addr(obj), old);
    }
  }
}

// Parallel enqueue task
class RefProcEnqueueTask: public AbstractRefProcTaskExecutor::EnqueueTask {
public:
  RefProcEnqueueTask(ReferenceProcessor& ref_processor,
                     DiscoveredList      discovered_refs[],
                     HeapWord*           pending_list_addr,
                     int                 n_queues)
    : EnqueueTask(ref_processor, discovered_refs,
                  pending_list_addr, n_queues)
  { }

  virtual void work(unsigned int work_id) {
    assert(work_id < (unsigned int)_ref_processor.max_num_q(), "Index out-of-bounds");
    // Simplest first cut: static partitioning.
    int index = work_id;
    // The increment on "index" must correspond to the maximum number of queues
    // (n_queues) with which that ReferenceProcessor was created.  That
    // is because of the "clever" way the discovered references lists were
    // allocated and are indexed into.
    assert(_n_queues == (int) _ref_processor.max_num_q(), "Different number not expected");
    for (int j = 0;
         j < ReferenceProcessor::number_of_subclasses_of_ref();
         j++, index += _n_queues) {
      _ref_processor.enqueue_discovered_reflist(
        _refs_lists[index], _pending_list_addr);
      _refs_lists[index].set_head(NULL);
      _refs_lists[index].set_length(0);
    }
  }
};

// Enqueue references that are not made active again
void ReferenceProcessor::enqueue_discovered_reflists(HeapWord* pending_list_addr,
  AbstractRefProcTaskExecutor* task_executor) {
  if (_processing_is_mt && task_executor != NULL) {
    // Parallel code
    RefProcEnqueueTask tsk(*this, _discovered_refs,
                           pending_list_addr, _max_num_q);
    task_executor->execute(tsk);
  } else {
    // Serial code: call the parent class's implementation
    for (uint i = 0; i < _max_num_q * number_of_subclasses_of_ref(); i++) {
      enqueue_discovered_reflist(_discovered_refs[i], pending_list_addr);
      _discovered_refs[i].set_head(NULL);
      _discovered_refs[i].set_length(0);
    }
  }
}

void DiscoveredListIterator::load_ptrs(DEBUG_ONLY(bool allow_null_referent)) {
  _discovered_addr = java_lang_ref_Reference::discovered_addr(_ref);
  oop discovered = java_lang_ref_Reference::discovered(_ref);
  assert(_discovered_addr && discovered->is_oop_or_null(),
         "Expected an oop or NULL for discovered field at " PTR_FORMAT, p2i(discovered));
  _next = discovered;
  _referent_addr = java_lang_ref_Reference::referent_addr(_ref);
  _referent = java_lang_ref_Reference::referent(_ref);
  assert(Universe::heap()->is_in_reserved_or_null(_referent),
         "Wrong oop found in java.lang.Reference object");
  assert(allow_null_referent ?
             _referent->is_oop_or_null()
           : _referent->is_oop(),
         "Expected an oop%s for referent field at " PTR_FORMAT,
         (allow_null_referent ? " or NULL" : ""),
         p2i(_referent));
}

void DiscoveredListIterator::remove() {
  assert(_ref->is_oop(), "Dropping a bad reference");
  oop_store_raw(_discovered_addr, NULL);

  // First _prev_next ref actually points into DiscoveredList (gross).
  oop new_next;
  if (_next == _ref) {
    // At the end of the list, we should make _prev point to itself.
    // If _ref is the first ref, then _prev_next will be in the DiscoveredList,
    // and _prev will be NULL.
    new_next = _prev;
  } else {
    new_next = _next;
  }
  // Remove Reference object from discovered list. Note that G1 does not need a
  // pre-barrier here because we know the Reference has already been found/marked,
  // that's how it ended up in the discovered list in the first place.
  oop_store_raw(_prev_next, new_next);
  NOT_PRODUCT(_removed++);
  _refs_list.dec_length(1);
}

void DiscoveredListIterator::clear_referent() {
  oop_store_raw(_referent_addr, NULL);
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
    if (referent_is_dead &&
        !policy->should_clear_reference(iter.obj(), _soft_ref_timestamp_clock)) {
      log_develop_trace(gc, ref)("Dropping reference (" INTPTR_FORMAT ": %s"  ") by policy",
                                 p2i(iter.obj()), iter.obj()->klass()->internal_name());
      // Remove Reference object from list
      iter.remove();
      // keep the referent around
      iter.make_referent_alive();
      iter.move_to_next();
    } else {
      iter.next();
    }
  }
  // Close the reachable set
  complete_gc->do_void();
  log_develop_trace(gc, ref)(" Dropped " SIZE_FORMAT " dead Refs out of " SIZE_FORMAT " discovered Refs by policy, from list " INTPTR_FORMAT,
                             iter.removed(), iter.processed(), p2i(refs_list.head()));
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
      log_develop_trace(gc, ref)("Dropping strongly reachable reference (" INTPTR_FORMAT ": %s)",
                                 p2i(iter.obj()), iter.obj()->klass()->internal_name());
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
    if (iter.processed() > 0) {
      log_develop_trace(gc, ref)(" Dropped " SIZE_FORMAT " active Refs out of " SIZE_FORMAT
        " Refs in discovered list " INTPTR_FORMAT,
        iter.removed(), iter.processed(), p2i(refs_list.head()));
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
      assert(next->is_oop_or_null(), "Expected an oop or NULL for next field at " PTR_FORMAT, p2i(next));
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
    if (iter.processed() > 0) {
      log_develop_trace(gc, ref)(" Dropped " SIZE_FORMAT " active Refs out of " SIZE_FORMAT
        " Refs in discovered list " INTPTR_FORMAT,
        iter.removed(), iter.processed(), p2i(refs_list.head()));
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
  ResourceMark rm;
  DiscoveredListIterator iter(refs_list, keep_alive, is_alive);
  while (iter.has_next()) {
    iter.load_ptrs(DEBUG_ONLY(false /* allow_null_referent */));
    if (clear_referent) {
      // NULL out referent pointer
      iter.clear_referent();
    } else {
      // keep the referent around
      iter.make_referent_alive();
    }
    log_develop_trace(gc, ref)("Adding %sreference (" INTPTR_FORMAT ": %s) as pending",
                               clear_referent ? "cleared " : "", p2i(iter.obj()), iter.obj()->klass()->internal_name());
    assert(iter.obj()->is_oop(UseConcMarkSweepGC), "Adding a bad reference");
    iter.next();
  }
  // Close the reachable set
  complete_gc->do_void();
}

void
ReferenceProcessor::clear_discovered_references(DiscoveredList& refs_list) {
  oop obj = NULL;
  oop next = refs_list.head();
  while (next != obj) {
    obj = next;
    next = java_lang_ref_Reference::discovered(obj);
    java_lang_ref_Reference::set_discovered_raw(obj, NULL);
  }
  refs_list.set_head(NULL);
  refs_list.set_length(0);
}

void ReferenceProcessor::abandon_partial_discovery() {
  // loop over the lists
  for (uint i = 0; i < _max_num_q * number_of_subclasses_of_ref(); i++) {
    if ((i % _max_num_q) == 0) {
      log_develop_trace(gc, ref)("Abandoning %s discovered list", list_name(i));
    }
    clear_discovered_references(_discovered_refs[i]);
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
    Thread* thr = Thread::current();
    int refs_list_index = ((WorkerThread*)thr)->id();
    _ref_processor.process_phase1(_refs_lists[refs_list_index], _policy,
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
    // Don't use "refs_list_index" calculated in this way because
    // balance_queues() has moved the Ref's into the first n queues.
    // Thread* thr = Thread::current();
    // int refs_list_index = ((WorkerThread*)thr)->id();
    // _ref_processor.process_phase3(_refs_lists[refs_list_index], _clear_referent,
    _ref_processor.process_phase3(_refs_lists[i], _clear_referent,
                                  &is_alive, &keep_alive, &complete_gc);
  }
private:
  bool _clear_referent;
};

#ifndef PRODUCT
void ReferenceProcessor::log_reflist_counts(DiscoveredList ref_lists[], size_t total_refs) {
  if (!log_is_enabled(Trace, gc, ref)) {
    return;
  }

  stringStream st;
  for (uint i = 0; i < _max_num_q; ++i) {
    st.print(SIZE_FORMAT " ", ref_lists[i].length());
  }
  log_develop_trace(gc, ref)("%s= " SIZE_FORMAT, st.as_string(), total_refs);
}
#endif

// Balances reference queues.
// Move entries from all queues[0, 1, ..., _max_num_q-1] to
// queues[0, 1, ..., _num_q-1] because only the first _num_q
// corresponding to the active workers will be processed.
void ReferenceProcessor::balance_queues(DiscoveredList ref_lists[])
{
  // calculate total length
  size_t total_refs = 0;
  log_develop_trace(gc, ref)("Balance ref_lists ");

  for (uint i = 0; i < _max_num_q; ++i) {
    total_refs += ref_lists[i].length();
    }
  log_reflist_counts(ref_lists, total_refs);
  size_t avg_refs = total_refs / _num_q + 1;
  uint to_idx = 0;
  for (uint from_idx = 0; from_idx < _max_num_q; from_idx++) {
    bool move_all = false;
    if (from_idx >= _num_q) {
      move_all = ref_lists[from_idx].length() > 0;
    }
    while ((ref_lists[from_idx].length() > avg_refs) ||
           move_all) {
      assert(to_idx < _num_q, "Sanity Check!");
      if (ref_lists[to_idx].length() < avg_refs) {
        // move superfluous refs
        size_t refs_to_move;
        // Move all the Ref's if the from queue will not be processed.
        if (move_all) {
          refs_to_move = MIN2(ref_lists[from_idx].length(),
                              avg_refs - ref_lists[to_idx].length());
        } else {
          refs_to_move = MIN2(ref_lists[from_idx].length() - avg_refs,
                              avg_refs - ref_lists[to_idx].length());
        }

        assert(refs_to_move > 0, "otherwise the code below will fail");

        oop move_head = ref_lists[from_idx].head();
        oop move_tail = move_head;
        oop new_head  = move_head;
        // find an element to split the list on
        for (size_t j = 0; j < refs_to_move; ++j) {
          move_tail = new_head;
          new_head = java_lang_ref_Reference::discovered(new_head);
        }

        // Add the chain to the to list.
        if (ref_lists[to_idx].head() == NULL) {
          // to list is empty. Make a loop at the end.
          java_lang_ref_Reference::set_discovered_raw(move_tail, move_tail);
        } else {
          java_lang_ref_Reference::set_discovered_raw(move_tail, ref_lists[to_idx].head());
        }
        ref_lists[to_idx].set_head(move_head);
        ref_lists[to_idx].inc_length(refs_to_move);

        // Remove the chain from the from list.
        if (move_tail == new_head) {
          // We found the end of the from list.
          ref_lists[from_idx].set_head(NULL);
        } else {
          ref_lists[from_idx].set_head(new_head);
        }
        ref_lists[from_idx].dec_length(refs_to_move);
        if (ref_lists[from_idx].length() == 0) {
          break;
        }
      } else {
        to_idx = (to_idx + 1) % _num_q;
      }
    }
  }
#ifdef ASSERT
  size_t balanced_total_refs = 0;
  for (uint i = 0; i < _max_num_q; ++i) {
    balanced_total_refs += ref_lists[i].length();
    }
  log_reflist_counts(ref_lists, balanced_total_refs);
  assert(total_refs == balanced_total_refs, "Balancing was incomplete");
#endif
}

void ReferenceProcessor::balance_all_queues() {
  balance_queues(_discoveredSoftRefs);
  balance_queues(_discoveredWeakRefs);
  balance_queues(_discoveredFinalRefs);
  balance_queues(_discoveredPhantomRefs);
  balance_queues(_discoveredCleanerRefs);
}

void ReferenceProcessor::process_discovered_reflist(
  DiscoveredList               refs_lists[],
  ReferencePolicy*             policy,
  bool                         clear_referent,
  BoolObjectClosure*           is_alive,
  OopClosure*                  keep_alive,
  VoidClosure*                 complete_gc,
  AbstractRefProcTaskExecutor* task_executor)
{
  bool mt_processing = task_executor != NULL && _processing_is_mt;
  // If discovery used MT and a dynamic number of GC threads, then
  // the queues must be balanced for correctness if fewer than the
  // maximum number of queues were used.  The number of queue used
  // during discovery may be different than the number to be used
  // for processing so don't depend of _num_q < _max_num_q as part
  // of the test.
  bool must_balance = _discovery_is_mt;

  if ((mt_processing && ParallelRefProcBalancingEnabled) ||
      must_balance) {
    balance_queues(refs_lists);
  }

  // Phase 1 (soft refs only):
  // . Traverse the list and remove any SoftReferences whose
  //   referents are not alive, but that should be kept alive for
  //   policy reasons. Keep alive the transitive closure of all
  //   such referents.
  if (policy != NULL) {
    if (mt_processing) {
      RefProcPhase1Task phase1(*this, refs_lists, policy, true /*marks_oops_alive*/);
      task_executor->execute(phase1);
    } else {
      for (uint i = 0; i < _max_num_q; i++) {
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
  if (mt_processing) {
    RefProcPhase2Task phase2(*this, refs_lists, !discovery_is_atomic() /*marks_oops_alive*/);
    task_executor->execute(phase2);
  } else {
    for (uint i = 0; i < _max_num_q; i++) {
      process_phase2(refs_lists[i], is_alive, keep_alive, complete_gc);
    }
  }

  // Phase 3:
  // . Traverse the list and process referents as appropriate.
  if (mt_processing) {
    RefProcPhase3Task phase3(*this, refs_lists, clear_referent, true /*marks_oops_alive*/);
    task_executor->execute(phase3);
  } else {
    for (uint i = 0; i < _max_num_q; i++) {
      process_phase3(refs_lists[i], clear_referent,
                     is_alive, keep_alive, complete_gc);
    }
  }
}

inline DiscoveredList* ReferenceProcessor::get_discovered_list(ReferenceType rt) {
  uint id = 0;
  // Determine the queue index to use for this object.
  if (_discovery_is_mt) {
    // During a multi-threaded discovery phase,
    // each thread saves to its "own" list.
    Thread* thr = Thread::current();
    id = thr->as_Worker_thread()->id();
  } else {
    // single-threaded discovery, we save in round-robin
    // fashion to each of the lists.
    if (_processing_is_mt) {
      id = next_id();
    }
  }
  assert(id < _max_num_q, "Id is out-of-bounds (call Freud?)");

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
    case REF_CLEANER:
      list = &_discoveredCleanerRefs[id];
      break;
    case REF_NONE:
      // we should not reach here if we are an InstanceRefKlass
    default:
      ShouldNotReachHere();
  }
  log_develop_trace(gc, ref)("Thread %d gets list " INTPTR_FORMAT, id, p2i(list));
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
  // The last ref must have its discovered field pointing to itself.
  oop next_discovered = (current_head != NULL) ? current_head : obj;

  oop retest = oopDesc::atomic_compare_exchange_oop(next_discovered, discovered_addr,
                                                    NULL);
  if (retest == NULL) {
    // This thread just won the right to enqueue the object.
    // We have separate lists for enqueueing, so no synchronization
    // is necessary.
    refs_list.set_head(obj);
    refs_list.inc_length(1);

    log_develop_trace(gc, ref)("Discovered reference (mt) (" INTPTR_FORMAT ": %s)",
                               p2i(obj), obj->klass()->internal_name());
  } else {
    // If retest was non NULL, another thread beat us to it:
    // The reference has already been discovered...
    log_develop_trace(gc, ref)("Already discovered reference (" INTPTR_FORMAT ": %s)",
                               p2i(obj), obj->klass()->internal_name());
    }
  }

#ifndef PRODUCT
// Non-atomic (i.e. concurrent) discovery might allow us
// to observe j.l.References with NULL referents, being those
// cleared concurrently by mutators during (or after) discovery.
void ReferenceProcessor::verify_referent(oop obj) {
  bool da = discovery_is_atomic();
  oop referent = java_lang_ref_Reference::referent(obj);
  assert(da ? referent->is_oop() : referent->is_oop_or_null(),
         "Bad referent " INTPTR_FORMAT " found in Reference "
         INTPTR_FORMAT " during %satomic discovery ",
         p2i(referent), p2i(obj), da ? "" : "non-");
}
#endif

// We mention two of several possible choices here:
// #0: if the reference object is not in the "originating generation"
//     (or part of the heap being collected, indicated by our "span"
//     we don't treat it specially (i.e. we scan it as we would
//     a normal oop, treating its references as strong references).
//     This means that references can't be discovered unless their
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
  // Make sure we are discovering refs (rather than processing discovered refs).
  if (!_discovering_refs || !RegisterReferences) {
    return false;
  }
  // We only discover active references.
  oop next = java_lang_ref_Reference::next(obj);
  if (next != NULL) {   // Ref is no longer active
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

  // We only discover references whose referents are not (yet)
  // known to be strongly reachable.
  if (is_alive_non_header() != NULL) {
    verify_referent(obj);
    if (is_alive_non_header()->do_object_b(java_lang_ref_Reference::referent(obj))) {
      return false;  // referent is reachable
    }
  }
  if (rt == REF_SOFT) {
    // For soft refs we can decide now if these are not
    // current candidates for clearing, in which case we
    // can mark through them now, rather than delaying that
    // to the reference-processing phase. Since all current
    // time-stamp policies advance the soft-ref clock only
    // at a full collection cycle, this is always currently
    // accurate.
    if (!_current_soft_ref_policy->should_clear_reference(obj, _soft_ref_timestamp_clock)) {
      return false;
    }
  }

  ResourceMark rm;      // Needed for tracing.

  HeapWord* const discovered_addr = java_lang_ref_Reference::discovered_addr(obj);
  const oop  discovered = java_lang_ref_Reference::discovered(obj);
  assert(discovered->is_oop_or_null(), "Expected an oop or NULL for discovered field at " PTR_FORMAT, p2i(discovered));
  if (discovered != NULL) {
    // The reference has already been discovered...
    log_develop_trace(gc, ref)("Already discovered reference (" INTPTR_FORMAT ": %s)",
                               p2i(obj), obj->klass()->internal_name());
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
      assert(UseConcMarkSweepGC || UseG1GC,
             "Only possible with a concurrent marking collector");
      return true;
    }
  }

  if (RefDiscoveryPolicy == ReferentBasedDiscovery) {
    verify_referent(obj);
    // Discover if and only if EITHER:
    // .. reference is in our span, OR
    // .. we are an atomic collector and referent is in our span
    if (_span.contains(obj_addr) ||
        (discovery_is_atomic() &&
         _span.contains(java_lang_ref_Reference::referent(obj)))) {
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
    // We do a raw store here: the field will be visited later when processing
    // the discovered references.
    oop current_head = list->head();
    // The last ref must have its discovered field pointing to itself.
    oop next_discovered = (current_head != NULL) ? current_head : obj;

    assert(discovered == NULL, "control point invariant");
    oop_store_raw(discovered_addr, next_discovered);
    list->set_head(obj);
    list->inc_length(1);

    log_develop_trace(gc, ref)("Discovered reference (" INTPTR_FORMAT ": %s)", p2i(obj), obj->klass()->internal_name());
  }
  assert(obj->is_oop(), "Discovered a bad reference");
  verify_referent(obj);
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
  GCTimer* gc_timer) {

  // Soft references
  {
    GCTraceTime(Debug, gc, ref) tm("Preclean SoftReferences", gc_timer);
    for (uint i = 0; i < _max_num_q; i++) {
      if (yield->should_return()) {
        return;
      }
      preclean_discovered_reflist(_discoveredSoftRefs[i], is_alive,
                                  keep_alive, complete_gc, yield);
    }
  }

  // Weak references
  {
    GCTraceTime(Debug, gc, ref) tm("Preclean WeakReferences", gc_timer);
    for (uint i = 0; i < _max_num_q; i++) {
      if (yield->should_return()) {
        return;
      }
      preclean_discovered_reflist(_discoveredWeakRefs[i], is_alive,
                                  keep_alive, complete_gc, yield);
    }
  }

  // Final references
  {
    GCTraceTime(Debug, gc, ref) tm("Preclean FinalReferences", gc_timer);
    for (uint i = 0; i < _max_num_q; i++) {
      if (yield->should_return()) {
        return;
      }
      preclean_discovered_reflist(_discoveredFinalRefs[i], is_alive,
                                  keep_alive, complete_gc, yield);
    }
  }

  // Phantom references
  {
    GCTraceTime(Debug, gc, ref) tm("Preclean PhantomReferences", gc_timer);
    for (uint i = 0; i < _max_num_q; i++) {
      if (yield->should_return()) {
        return;
      }
      preclean_discovered_reflist(_discoveredPhantomRefs[i], is_alive,
                                  keep_alive, complete_gc, yield);
    }

    // Cleaner references.  Included in timing for phantom references.  We
    // expect Cleaner references to be temporary, and don't want to deal with
    // possible incompatibilities arising from making it more visible.
    for (uint i = 0; i < _max_num_q; i++) {
      if (yield->should_return()) {
        return;
      }
      preclean_discovered_reflist(_discoveredCleanerRefs[i], is_alive,
                                  keep_alive, complete_gc, yield);
    }
  }
}

// Walk the given discovered ref list, and remove all reference objects
// whose referents are still alive, whose referents are NULL or which
// are not active (have a non-NULL next field). NOTE: When we are
// thus precleaning the ref lists (which happens single-threaded today),
// we do not disable refs discovery to honor the correct semantics of
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
      log_develop_trace(gc, ref)("Precleaning Reference (" INTPTR_FORMAT ": %s)",
                                 p2i(iter.obj()), iter.obj()->klass()->internal_name());
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
    if (iter.processed() > 0) {
      log_develop_trace(gc, ref)(" Dropped " SIZE_FORMAT " Refs out of " SIZE_FORMAT " Refs in discovered list " INTPTR_FORMAT,
        iter.removed(), iter.processed(), p2i(refs_list.head()));
    }
  )
}

const char* ReferenceProcessor::list_name(uint i) {
   assert(i <= _max_num_q * number_of_subclasses_of_ref(),
          "Out of bounds index");

   int j = i / _max_num_q;
   switch (j) {
     case 0: return "SoftRef";
     case 1: return "WeakRef";
     case 2: return "FinalRef";
     case 3: return "PhantomRef";
     case 4: return "CleanerRef";
   }
   ShouldNotReachHere();
   return NULL;
}

