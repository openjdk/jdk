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

#include "logging/log.hpp"

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

//static volatile oop* reference_referent_addr(oop reference) {
//  return (volatile oop*)java_lang_ref_Reference::referent_addr_raw(reference);
//}

//static oop reference_referent(oop reference) {
//  return Atomic::load(reference_referent_addr(reference));
//}
//
//static void reference_set_referent(oop reference, oop referent) {
//  java_lang_ref_Reference::set_referent_raw(reference, referent);
//}
//
template <typename T>
static T* reference_discovered_addr(oop reference) {
  return reinterpret_cast<T*>(java_lang_ref_Reference::discovered_addr_raw(reference));
}

template <typename T>
static oop reference_discovered(oop reference) {
  T heap_oop = RawAccess<>::oop_load(reference_discovered_addr<T>(reference));
  return CompressedOops::decode(heap_oop);
}

static void reference_set_discovered(oop reference, oop discovered) {
  java_lang_ref_Reference::set_discovered_raw(reference, discovered);
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

ShenandoahRefProcThreadLocal::ShenandoahRefProcThreadLocal() :
  _discovered_list(NULL) {

}

void ShenandoahRefProcThreadLocal::clear() {
  _discovered_list = NULL;
}

oop ShenandoahRefProcThreadLocal::discovered_list_head() const {
  return _discovered_list;
}

void ShenandoahRefProcThreadLocal::set_discovered_list_head(oop head) {
  _discovered_list = head;
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
  if (reference_discovered<T>(reference) != NULL) {
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
  assert(reference_discovered<T>(reference) == NULL, "Already discovered: " PTR_FORMAT, p2i(reference));
  uint worker_id = ShenandoahThreadLocalData::worker_id(Thread::current());
  assert(worker_id >= 0, "need valid worker ID");
  ShenandoahRefProcThreadLocal* refproc_data = _ref_proc_thread_locals[worker_id];
  assert(refproc_data != NULL, "need thread-local refproc-data");
  oop discovered_head = refproc_data->discovered_list_head();
  reference_set_discovered(reference, discovered_head);
  refproc_data->set_discovered_list_head(reference);

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
    return discover<narrowOop>(reference, type);
  }
}
