/*
 * Copyright (c) 2020, Red Hat, Inc. and/or its affiliates.
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
#include "gc/shenandoah/shenandoahOopClosures.hpp"
#include "gc/shenandoah/shenandoahReferenceProcessor.hpp"
#include "gc/shenandoah/shenandoahThreadLocalData.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"

#include "logging/log.hpp"

static ReferenceType reference_type(oop reference) {
  return InstanceKlass::cast(reference->klass())->reference_type();
}

static const char* reference_type_name(ReferenceType type) {
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

template <typename T>
static volatile T* reference_referent_addr(oop reference) {
  return (volatile T*)java_lang_ref_Reference::referent_addr_raw(reference);
}

template <typename T>
static oop reference_referent(oop reference) {
  T heap_oop = Atomic::load(reference_referent_addr<T>(reference));
  return CompressedOops::decode(heap_oop);
}

static void reference_set_referent(oop reference, oop referent) {
  java_lang_ref_Reference::set_referent_raw(reference, referent);
}

template <typename T>
static T* reference_discovered_addr(oop reference) {
  return reinterpret_cast<T*>(java_lang_ref_Reference::discovered_addr_raw(reference));
}

template <typename T>
static T reference_discovered(oop reference) {
  return *reference_discovered_addr<T>(reference);
}

template <typename T>
static void reference_set_discovered(oop reference, T discovered) {
  *reference_discovered_addr<T>(reference) = discovered;
}

template <typename T>
static T* reference_next_addr(oop reference) {
  return reinterpret_cast<T*>(java_lang_ref_Reference::next_addr_raw(reference));
}

template <typename T>
static oop reference_next(oop reference) {
  T heap_oop = RawAccess<>::oop_load(reference_next_addr<T>(reference));
  return CompressedOops::decode(heap_oop);
}

static void reference_set_next(oop reference, oop next) {
  java_lang_ref_Reference::set_next_raw(reference, next);
}

static void soft_reference_update_clock() {
  const jlong now = os::javaTimeNanos() / NANOSECS_PER_MILLISEC;
  java_lang_ref_SoftReference::set_clock(now);
}

ShenandoahRefProcThreadLocal::ShenandoahRefProcThreadLocal() :
  _discovered_list(NULL) {

}

void ShenandoahRefProcThreadLocal::clear() {
  _discovered_list = NULL;
}

template <typename T>
T* ShenandoahRefProcThreadLocal::discovered_list_addr() {
  return reinterpret_cast<T*>(&_discovered_list);
}

template <typename T>
T ShenandoahRefProcThreadLocal::discovered_list_head() const {
  return *reinterpret_cast<const T*>(&_discovered_list);
}

template <>
void ShenandoahRefProcThreadLocal::set_discovered_list_head<narrowOop>(oop head) {
  *discovered_list_addr<narrowOop>() = CompressedOops::encode(head);
}

template <>
void ShenandoahRefProcThreadLocal::set_discovered_list_head<oop>(oop head) {
  *discovered_list_addr<oop>() = head;
}

ShenandoahReferenceProcessor::ShenandoahReferenceProcessor(uint max_workers) :
  _soft_reference_policy(NULL),
  _ref_proc_thread_locals(NEW_C_HEAP_ARRAY(ShenandoahRefProcThreadLocal*, max_workers, mtGC)) {
  for (size_t i = 0; i < max_workers; i++) {
    _ref_proc_thread_locals[i] = NULL;
  }
}

void ShenandoahReferenceProcessor::init_thread_locals(uint worker_id) {
  ShenandoahRefProcThreadLocal* refproc_data = _ref_proc_thread_locals[worker_id];
  if (refproc_data == NULL) {
    _ref_proc_thread_locals[worker_id] = new ShenandoahRefProcThreadLocal();
  } else {
    refproc_data->clear();
  }
}

void ShenandoahReferenceProcessor::set_soft_reference_policy(bool clear) {
  static AlwaysClearPolicy always_clear_policy;
  static LRUMaxHeapPolicy lru_max_heap_policy;

  if (clear) {
    log_info(gc, ref)("Clearing All SoftReferences");
    _soft_reference_policy = &always_clear_policy;
  } else {
    _soft_reference_policy = &lru_max_heap_policy;
  }

  _soft_reference_policy->setup();
}

template <typename T>
bool ShenandoahReferenceProcessor::is_inactive(oop reference, oop referent, ReferenceType type) const {
  if (type == REF_FINAL) {
    // A FinalReference is inactive if its next field is non-null. An application can't
    // call enqueue() or clear() on a FinalReference.
    return reference_next<T>(reference) != NULL;
  } else {
    // A non-FinalReference is inactive if the referent is null. The referent can only
    // be null if the application called Reference.enqueue() or Reference.clear().
    return referent == NULL;
  }
}

bool ShenandoahReferenceProcessor::is_strongly_live(oop referent) const {
  return ShenandoahHeap::heap()->marking_context()->is_marked_strong(referent);
}

bool ShenandoahReferenceProcessor::is_softly_live(oop reference, ReferenceType type) const {
  if (type != REF_SOFT) {
    // Not a SoftReference
    return false;
  }

  // Ask SoftReference policy
  const jlong clock = java_lang_ref_SoftReference::clock();
  assert(clock != 0, "Clock not initialized");
  assert(_soft_reference_policy != NULL, "Policy not initialized");
  return !_soft_reference_policy->should_clear_reference(reference, clock);
}

template <typename T>
bool ShenandoahReferenceProcessor::should_discover(oop reference, ReferenceType type) const {
  if (!CompressedOops::is_null(reference_discovered<T>(reference))) {
    // Already discovered. This can happen if the reference is marked finalizable first, and then strong,
    // in which case it will be seen 2x by marking.
    return false;
  }
  T* referent_addr = (T*) java_lang_ref_Reference::referent_addr_raw(reference);
  T heap_oop = RawAccess<>::oop_load(referent_addr);
  oop referent = CompressedOops::decode_not_null(heap_oop);

  if (is_inactive<T>(reference, referent, type)) {
    return false;
  }

  if (is_strongly_live(referent)) {
    return false;
  }

  if (is_softly_live(reference, type)) {
    return false;
  }

  return true;
}

template <typename T>
bool ShenandoahReferenceProcessor::should_drop(oop reference, ReferenceType type) const {
  const oop referent = reference_referent<T>(reference);
  if (referent == NULL) {
    // Reference has been cleared, by a call to Reference.enqueue()
    // or Reference.clear() from the application, which means we
    // should drop the reference.
    return true;
  }

  // Check if the referent is still alive, in which case we should
  // drop the reference.
  if (type == REF_PHANTOM) {
    return ShenandoahHeap::heap()->complete_marking_context()->is_marked_final(referent);
  } else {
    return ShenandoahHeap::heap()->complete_marking_context()->is_marked_strong(referent);
  }
}

template <typename T>
void ShenandoahReferenceProcessor::make_inactive(oop reference, ReferenceType type) const {
  if (type == REF_FINAL) {
    // Don't clear referent. It is needed by the Finalizer thread to make the call
    // to finalize(). A FinalReference is instead made inactive by self-looping the
    // next field. An application can't call FinalReference.enqueue(), so there is
    // no race to worry about when setting the next field.
    assert(reference_next<T>(reference) == NULL, "Already inactive");
    reference_set_next(reference, reference);
  } else {
    // Clear referent
    reference_set_referent(reference, NULL);
  }
}

template <typename T>
bool ShenandoahReferenceProcessor::discover(oop reference, ReferenceType type) {
  if (!should_discover<T>(reference, type)) {
    // Not discovered
    return false;
  }

  // Update statistics
  //_discovered_count.get()[type]++;

  if (type == REF_FINAL) {
    Thread* thread = Thread::current();
    ShenandoahMarkRefsSuperClosure* cl = ShenandoahThreadLocalData::mark_closure(thread);
    bool strong = cl->is_strong();
    cl->set_strong(false);
    if (UseCompressedOops) {
      cl->do_oop(reinterpret_cast<narrowOop*>(java_lang_ref_Reference::referent_addr_raw(reference)));
    } else {
      cl->do_oop(reinterpret_cast<oop*>(java_lang_ref_Reference::referent_addr_raw(reference)));
    }
    cl->set_strong(strong);
  }

  // Add reference to discovered list
  assert(CompressedOops::is_null(reference_discovered<T>(reference)), "Already discovered: " PTR_FORMAT, p2i(reference));
  uint worker_id = ShenandoahThreadLocalData::worker_id(Thread::current());
  assert(worker_id != ShenandoahThreadLocalData::INVALID_WORKER_ID, "need valid worker ID");
  ShenandoahRefProcThreadLocal* refproc_data = _ref_proc_thread_locals[worker_id];
  assert(refproc_data != NULL, "need thread-local refproc-data");
  T discovered_head = refproc_data->discovered_list_head<T>();
  reference_set_discovered(reference, discovered_head);
  refproc_data->set_discovered_list_head<T>(reference);

  log_trace(gc, ref)("Discovered Reference: " PTR_FORMAT " (%s)", p2i(reference), reference_type_name(type));

  return true;
}

bool ShenandoahReferenceProcessor::discover_reference(oop reference, ReferenceType type) {
  if (!RegisterReferences) {
    // Reference processing disabled
    return false;
  }

  log_trace(gc, ref)("Encountered Reference: " PTR_FORMAT " (%s)", p2i(reference), reference_type_name(type));

  if (UseCompressedOops) {
    return discover<narrowOop>(reference, type);
  } else {
    return discover<oop>(reference, type);
  }
}

template <typename T>
T ShenandoahReferenceProcessor::drop(oop reference, ReferenceType type) {
  log_trace(gc, ref)("Dropped Reference: " PTR_FORMAT " (%s)", p2i(reference), reference_type_name(type));

  // Keep referent alive
  //keep_alive<T>(reference, type);

  // Unlink and return next in list
  const T next = reference_discovered<T>(reference);
  reference_set_discovered(reference, NULL);
  return next;
}

template <typename T>
T* ShenandoahReferenceProcessor::keep(oop reference, ReferenceType type) {
  log_trace(gc, ref)("Enqueued Reference: " PTR_FORMAT " (%s)", p2i(reference), reference_type_name(type));

  // Update statistics
  // TODO _enqueued_count.get()[type]++;

  // Make reference inactive
  make_inactive<T>(reference, type);

  // Return next in list
  return reference_discovered_addr<T>(reference);
}

template <typename T>
void ShenandoahReferenceProcessor::process_references(ShenandoahRefProcThreadLocal* refproc_data) {;
  T* list = refproc_data->discovered_list_addr<T>();
  T* p = list;
  while (!CompressedOops::is_null(*p)) {
    const oop reference = CompressedOops::decode(*p);
    const ReferenceType type = reference_type(reference);

    if (should_drop<T>(reference, type)) {
      *p = drop<T>(reference, type);
    } else {
      p = keep<T>(reference, type);
    }
  }

  // Prepend discovered references to internal pending list
  if (!CompressedOops::is_null(*list)) {
    oop prev = Atomic::xchg(&_pending_list, CompressedOops::decode_not_null(*list));
    RawAccess<>::oop_store(p, prev);
    if (prev == NULL) {
      // First to prepend to list, record tail
      _pending_list_tail = reinterpret_cast<void*>(p);
    }

    // Clear discovered list
    RawAccess<>::oop_store(list, oop(NULL));
  }
}

void ShenandoahReferenceProcessor::work() {
  // Process discovered references
  uint worker_id = ShenandoahThreadLocalData::worker_id(Thread::current());
  assert(worker_id != ShenandoahThreadLocalData::INVALID_WORKER_ID, "need valid worker ID");
  ShenandoahRefProcThreadLocal* refproc_data = _ref_proc_thread_locals[worker_id];
  assert(refproc_data != NULL, "need thread-local refproc-data");
  if (UseCompressedOops) {
    process_references<narrowOop>(refproc_data);
  } else {
    process_references<oop>(refproc_data);
  }
}

class ShenandoahReferenceProcessorTask : public AbstractGangTask {
private:
  ShenandoahReferenceProcessor* const _reference_processor;

public:
  ShenandoahReferenceProcessorTask(ShenandoahReferenceProcessor* reference_processor) :
    AbstractGangTask("ShenandoahReferenceProcessorTask"),
    _reference_processor(reference_processor) {
  }

  virtual void work(uint worker_id) {
    ShenandoahConcurrentWorkerSession worker_session(worker_id);
    _reference_processor->work();
  }
};

void ShenandoahReferenceProcessor::process_references(WorkGang* workers) {
  // Process discovered lists
  ShenandoahReferenceProcessorTask task(this);
  workers->run_task(&task);

  // Update SoftReference clock
  soft_reference_update_clock();

  // Collect, log and trace statistics
  // collect_statistics();

  enqueue_references();
}

void ShenandoahReferenceProcessor::enqueue_references() {
  if (_pending_list == NULL) {
    // Nothing to enqueue
    return;
  }

  {
    // Heap_lock protects external pending list
    MonitorLocker ml(Heap_lock);

    // Prepend internal pending list to external pending list
    if (UseCompressedOops) {
      *reinterpret_cast<narrowOop*>(_pending_list_tail) = CompressedOops::encode(Universe::swap_reference_pending_list(_pending_list));
    } else {
      *reinterpret_cast<oop*>(_pending_list_tail) = Universe::swap_reference_pending_list(_pending_list);
    }

    // Notify ReferenceHandler thread
    ml.notify_all();
  }

  // Reset internal pending list
  _pending_list = NULL;
  _pending_list_tail = &_pending_list;
}

