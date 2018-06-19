/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
 */

#include "precompiled.hpp"
#include "classfile/javaClasses.inline.hpp"
#include "gc/shared/referencePolicy.hpp"
#include "gc/shared/referenceProcessorStats.hpp"
#include "gc/z/zHeap.inline.hpp"
#include "gc/z/zOopClosures.inline.hpp"
#include "gc/z/zReferenceProcessor.hpp"
#include "gc/z/zStat.hpp"
#include "gc/z/zTask.hpp"
#include "gc/z/zTracer.inline.hpp"
#include "gc/z/zUtils.inline.hpp"
#include "memory/universe.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/os.hpp"

static const ZStatSubPhase ZSubPhaseConcurrentReferencesProcess("Concurrent References Process");
static const ZStatSubPhase ZSubPhaseConcurrentReferencesEnqueue("Concurrent References Enqueue");

ZReferenceProcessor::ZReferenceProcessor(ZWorkers* workers) :
    _workers(workers),
    _soft_reference_policy(NULL),
    _encountered_count(),
    _discovered_count(),
    _enqueued_count(),
    _discovered_list(NULL),
    _pending_list(NULL),
    _pending_list_tail(_pending_list.addr()) {}

void ZReferenceProcessor::set_soft_reference_policy(bool clear) {
  static AlwaysClearPolicy always_clear_policy;
  static LRUMaxHeapPolicy lru_max_heap_policy;

  if (clear) {
    log_info(gc, ref)("Clearing All Soft References");
    _soft_reference_policy = &always_clear_policy;
  } else {
    _soft_reference_policy = &lru_max_heap_policy;
  }

  _soft_reference_policy->setup();
}

void ZReferenceProcessor::update_soft_reference_clock() const {
  const jlong now = os::javaTimeNanos() / NANOSECS_PER_MILLISEC;
  java_lang_ref_SoftReference::set_clock(now);
}

bool ZReferenceProcessor::is_reference_inactive(oop obj) const {
  // A non-null next field means the reference is inactive
  return java_lang_ref_Reference::next(obj) != NULL;
}

ReferenceType ZReferenceProcessor::reference_type(oop obj) const {
  return InstanceKlass::cast(obj->klass())->reference_type();
}

const char* ZReferenceProcessor::reference_type_name(ReferenceType type) const {
  switch (type) {
  case REF_SOFT:
    return "Soft";

  case REF_WEAK:
    return "Weak";

  case REF_FINAL:
    return "Final";

  case REF_PHANTOM:
    return "Phantom";

  default:
    ShouldNotReachHere();
    return NULL;
  }
}

volatile oop* ZReferenceProcessor::reference_referent_addr(oop obj) const {
  return (volatile oop*)java_lang_ref_Reference::referent_addr_raw(obj);
}

oop ZReferenceProcessor::reference_referent(oop obj) const {
  return *reference_referent_addr(obj);
}

bool ZReferenceProcessor::is_referent_strongly_alive_or_null(oop obj, ReferenceType type) const {
  // Check if the referent is strongly alive or null, in which case we don't want to
  // discover the reference. It can only be null if the application called
  // Reference.enqueue() or Reference.clear().
  //
  // PhantomReferences with finalizable marked referents should technically not have
  // to be discovered. However, InstanceRefKlass::oop_oop_iterate_ref_processing()
  // does not know about the finalizable mark concept, and will therefore mark
  // referents in non-discovered PhantomReferences as strongly live. To prevent
  // this, we always discover PhantomReferences with finalizable marked referents.
  // They will automatically be dropped during the reference processing phase.

  volatile oop* const p = reference_referent_addr(obj);
  const oop o = ZBarrier::weak_load_barrier_on_oop_field(p);
  return o == NULL || ZHeap::heap()->is_object_strongly_live(ZOop::to_address(o));
}

bool ZReferenceProcessor::is_referent_softly_alive(oop obj, ReferenceType type) const {
  if (type != REF_SOFT) {
    // Not a soft reference
    return false;
  }

  // Ask soft reference policy
  const jlong clock = java_lang_ref_SoftReference::clock();
  assert(clock != 0, "Clock not initialized");
  assert(_soft_reference_policy != NULL, "Policy not initialized");
  return !_soft_reference_policy->should_clear_reference(obj, clock);
}

bool ZReferenceProcessor::should_drop_reference(oop obj, ReferenceType type) const {
  // This check is racing with a call to Reference.clear() from the application.
  // If the application clears the reference after this check it will still end
  // up on the pending list, and there's nothing we can do about that without
  // changing the Reference.clear() API. This check is also racing with a call
  // to Reference.enqueue() from the application, which is unproblematic, since
  // the application wants the reference to be enqueued anyway.
  const oop o = reference_referent(obj);
  if (o == NULL) {
    // Reference has been cleared, by a call to Reference.enqueue()
    // or Reference.clear() from the application, which means we
    // should drop the reference.
    return true;
  }

  // Check if the referent is still alive, in which case we should
  // drop the reference.
  if (type == REF_PHANTOM) {
    return ZBarrier::is_alive_barrier_on_phantom_oop(o);
  } else {
    return ZBarrier::is_alive_barrier_on_weak_oop(o);
  }
}

bool ZReferenceProcessor::should_mark_referent(ReferenceType type) const {
  // Referents of final references (and its reachable sub graph) are
  // always marked finalizable during discovery. This avoids the problem
  // of later having to mark those objects if the referent is still final
  // reachable during processing.
  return type == REF_FINAL;
}

bool ZReferenceProcessor::should_clear_referent(ReferenceType type) const {
  // Referents that were not marked must be cleared
  return !should_mark_referent(type);
}

void ZReferenceProcessor::keep_referent_alive(oop obj, ReferenceType type) const {
  volatile oop* const p = reference_referent_addr(obj);
  if (type == REF_PHANTOM) {
    ZBarrier::keep_alive_barrier_on_phantom_oop_field(p);
  } else {
    ZBarrier::keep_alive_barrier_on_weak_oop_field(p);
  }
}

bool ZReferenceProcessor::discover_reference(oop obj, ReferenceType type) {
  if (!RegisterReferences) {
    // Reference processing disabled
    return false;
  }

  log_trace(gc, ref)("Encountered Reference: " PTR_FORMAT " (%s)", p2i(obj), reference_type_name(type));

  // Update statistics
  _encountered_count.get()[type]++;

  if (is_reference_inactive(obj) ||
      is_referent_strongly_alive_or_null(obj, type) ||
      is_referent_softly_alive(obj, type)) {
    // Not discovered
    return false;
  }

  discover(obj, type);

  // Discovered
  return true;
}

void ZReferenceProcessor::discover(oop obj, ReferenceType type) {
  log_trace(gc, ref)("Discovered Reference: " PTR_FORMAT " (%s)", p2i(obj), reference_type_name(type));

  // Update statistics
  _discovered_count.get()[type]++;

  // Mark referent finalizable
  if (should_mark_referent(type)) {
    oop* const referent_addr = (oop*)java_lang_ref_Reference::referent_addr_raw(obj);
    ZBarrier::mark_barrier_on_oop_field(referent_addr, true /* finalizable */);
  }

  // Add reference to discovered list
  assert(java_lang_ref_Reference::discovered(obj) == NULL, "Already discovered");
  oop* const list = _discovered_list.addr();
  java_lang_ref_Reference::set_discovered(obj, *list);
  *list = obj;
}

oop ZReferenceProcessor::drop(oop obj, ReferenceType type) {
  log_trace(gc, ref)("Dropped Reference: " PTR_FORMAT " (%s)", p2i(obj), reference_type_name(type));

  // Keep referent alive
  keep_referent_alive(obj, type);

  // Unlink and return next in list
  const oop next = java_lang_ref_Reference::discovered(obj);
  java_lang_ref_Reference::set_discovered(obj, NULL);
  return next;
}

oop* ZReferenceProcessor::keep(oop obj, ReferenceType type) {
  log_trace(gc, ref)("Enqueued Reference: " PTR_FORMAT " (%s)", p2i(obj), reference_type_name(type));

  // Update statistics
  _enqueued_count.get()[type]++;

  // Clear referent
  if (should_clear_referent(type)) {
    java_lang_ref_Reference::set_referent(obj, NULL);
  }

  // Make reference inactive by self-looping the next field. We could be racing with a
  // call to Reference.enqueue() from the application, which is why we are using a CAS
  // to make sure we change the next field only if it is NULL. A failing CAS means the
  // reference has already been enqueued. However, we don't check the result of the CAS,
  // since we still have no option other than keeping the reference on the pending list.
  // It's ok to have the reference both on the pending list and enqueued at the same
  // time (the pending list is linked through the discovered field, while the reference
  // queue is linked through the next field). When the ReferenceHandler thread later
  // calls Reference.enqueue() we detect that it has already been enqueued and drop it.
  oop* const next_addr = (oop*)java_lang_ref_Reference::next_addr_raw(obj);
  Atomic::cmpxchg(obj, next_addr, oop(NULL));

  // Return next in list
  return (oop*)java_lang_ref_Reference::discovered_addr_raw(obj);
}

void ZReferenceProcessor::work() {
  // Process discovered references
  oop* const list = _discovered_list.addr();
  oop* p = list;

  while (*p != NULL) {
    const oop obj = *p;
    const ReferenceType type = reference_type(obj);

    if (should_drop_reference(obj, type)) {
      *p = drop(obj, type);
    } else {
      p = keep(obj, type);
    }
  }

  // Prepend discovered references to internal pending list
  if (*list != NULL) {
    *p = Atomic::xchg(*list, _pending_list.addr());
    if (*p == NULL) {
      // First to prepend to list, record tail
      _pending_list_tail = p;
    }

    // Clear discovered list
    *list = NULL;
  }
}

bool ZReferenceProcessor::is_empty() const {
  ZPerWorkerConstIterator<oop> iter(&_discovered_list);
  for (const oop* list; iter.next(&list);) {
    if (*list != NULL) {
      return false;
    }
  }

  if (_pending_list.get() != NULL) {
    return false;
  }

  return true;
}

void ZReferenceProcessor::reset_statistics() {
  assert(is_empty(), "Should be empty");

  // Reset encountered
  ZPerWorkerIterator<Counters> iter_encountered(&_encountered_count);
  for (Counters* counters; iter_encountered.next(&counters);) {
    for (int i = REF_SOFT; i <= REF_PHANTOM; i++) {
      (*counters)[i] = 0;
    }
  }

  // Reset discovered
  ZPerWorkerIterator<Counters> iter_discovered(&_discovered_count);
  for (Counters* counters; iter_discovered.next(&counters);) {
    for (int i = REF_SOFT; i <= REF_PHANTOM; i++) {
      (*counters)[i] = 0;
    }
  }

  // Reset enqueued
  ZPerWorkerIterator<Counters> iter_enqueued(&_enqueued_count);
  for (Counters* counters; iter_enqueued.next(&counters);) {
    for (int i = REF_SOFT; i <= REF_PHANTOM; i++) {
      (*counters)[i] = 0;
    }
  }
}

void ZReferenceProcessor::collect_statistics() {
  Counters encountered = {};
  Counters discovered = {};
  Counters enqueued = {};

  // Sum encountered
  ZPerWorkerConstIterator<Counters> iter_encountered(&_encountered_count);
  for (const Counters* counters; iter_encountered.next(&counters);) {
    for (int i = REF_SOFT; i <= REF_PHANTOM; i++) {
      encountered[i] += (*counters)[i];
    }
  }

  // Sum discovered
  ZPerWorkerConstIterator<Counters> iter_discovered(&_discovered_count);
  for (const Counters* counters; iter_discovered.next(&counters);) {
    for (int i = REF_SOFT; i <= REF_PHANTOM; i++) {
      discovered[i] += (*counters)[i];
    }
  }

  // Sum enqueued
  ZPerWorkerConstIterator<Counters> iter_enqueued(&_enqueued_count);
  for (const Counters* counters; iter_enqueued.next(&counters);) {
    for (int i = REF_SOFT; i <= REF_PHANTOM; i++) {
      enqueued[i] += (*counters)[i];
    }
  }

  // Update statistics
  ZStatReferences::set_soft(encountered[REF_SOFT], discovered[REF_SOFT], enqueued[REF_SOFT]);
  ZStatReferences::set_weak(encountered[REF_WEAK], discovered[REF_WEAK], enqueued[REF_WEAK]);
  ZStatReferences::set_final(encountered[REF_FINAL], discovered[REF_FINAL], enqueued[REF_FINAL]);
  ZStatReferences::set_phantom(encountered[REF_PHANTOM], discovered[REF_PHANTOM], enqueued[REF_PHANTOM]);

  // Trace statistics
  const ReferenceProcessorStats stats(discovered[REF_SOFT],
                                      discovered[REF_WEAK],
                                      discovered[REF_FINAL],
                                      discovered[REF_PHANTOM]);
  ZTracer::tracer()->report_gc_reference_stats(stats);
}

class ZReferenceProcessorTask : public ZTask {
private:
  ZReferenceProcessor* const _reference_processor;

public:
  ZReferenceProcessorTask(ZReferenceProcessor* reference_processor) :
      ZTask("ZReferenceProcessorTask"),
      _reference_processor(reference_processor) {}

  virtual void work() {
    _reference_processor->work();
  }
};

void ZReferenceProcessor::process_references() {
  ZStatTimer timer(ZSubPhaseConcurrentReferencesProcess);

  // Process discovered lists
  ZReferenceProcessorTask task(this);
  _workers->run_concurrent(&task);

  // Update soft reference clock
  update_soft_reference_clock();

  // Collect, log and trace statistics
  collect_statistics();
}

void ZReferenceProcessor::enqueue_references() {
  ZStatTimer timer(ZSubPhaseConcurrentReferencesEnqueue);

  if (_pending_list.get() == NULL) {
    // Nothing to enqueue
    return;
  }

  {
    // Heap_lock protects external pending list
    MonitorLockerEx ml(Heap_lock);

    // Prepend internal pending list to external pending list
    *_pending_list_tail = Universe::swap_reference_pending_list(_pending_list.get());

    // Notify ReferenceHandler thread
    ml.notify_all();
  }

  // Reset internal pending list
  _pending_list.set(NULL);
  _pending_list_tail = _pending_list.addr();
}
