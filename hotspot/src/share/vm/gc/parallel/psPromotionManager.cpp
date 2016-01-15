/*
 * Copyright (c) 2002, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/parallel/mutableSpace.hpp"
#include "gc/parallel/parallelScavengeHeap.hpp"
#include "gc/parallel/psOldGen.hpp"
#include "gc/parallel/psPromotionManager.inline.hpp"
#include "gc/parallel/psScavenge.inline.hpp"
#include "gc/shared/gcTrace.hpp"
#include "gc/shared/taskqueue.inline.hpp"
#include "logging/log.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/memRegion.hpp"
#include "memory/padded.inline.hpp"
#include "oops/instanceKlass.inline.hpp"
#include "oops/instanceMirrorKlass.inline.hpp"
#include "oops/objArrayKlass.inline.hpp"
#include "oops/oop.inline.hpp"

PaddedEnd<PSPromotionManager>* PSPromotionManager::_manager_array = NULL;
OopStarTaskQueueSet*           PSPromotionManager::_stack_array_depth = NULL;
PSOldGen*                      PSPromotionManager::_old_gen = NULL;
MutableSpace*                  PSPromotionManager::_young_space = NULL;

void PSPromotionManager::initialize() {
  ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();

  _old_gen = heap->old_gen();
  _young_space = heap->young_gen()->to_space();

  // To prevent false sharing, we pad the PSPromotionManagers
  // and make sure that the first instance starts at a cache line.
  assert(_manager_array == NULL, "Attempt to initialize twice");
  _manager_array = PaddedArray<PSPromotionManager, mtGC>::create_unfreeable(ParallelGCThreads + 1);
  guarantee(_manager_array != NULL, "Could not initialize promotion manager");

  _stack_array_depth = new OopStarTaskQueueSet(ParallelGCThreads);
  guarantee(_stack_array_depth != NULL, "Could not initialize promotion manager");

  // Create and register the PSPromotionManager(s) for the worker threads.
  for(uint i=0; i<ParallelGCThreads; i++) {
    stack_array_depth()->register_queue(i, _manager_array[i].claimed_stack_depth());
  }
  // The VMThread gets its own PSPromotionManager, which is not available
  // for work stealing.
}

// Helper functions to get around the circular dependency between
// psScavenge.inline.hpp and psPromotionManager.inline.hpp.
bool PSPromotionManager::should_scavenge(oop* p, bool check_to_space) {
  return PSScavenge::should_scavenge(p, check_to_space);
}
bool PSPromotionManager::should_scavenge(narrowOop* p, bool check_to_space) {
  return PSScavenge::should_scavenge(p, check_to_space);
}

PSPromotionManager* PSPromotionManager::gc_thread_promotion_manager(uint index) {
  assert(index < ParallelGCThreads, "index out of range");
  assert(_manager_array != NULL, "Sanity");
  return &_manager_array[index];
}

PSPromotionManager* PSPromotionManager::vm_thread_promotion_manager() {
  assert(_manager_array != NULL, "Sanity");
  return &_manager_array[ParallelGCThreads];
}

void PSPromotionManager::pre_scavenge() {
  ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();

  _young_space = heap->young_gen()->to_space();

  for(uint i=0; i<ParallelGCThreads+1; i++) {
    manager_array(i)->reset();
  }
}

bool PSPromotionManager::post_scavenge(YoungGCTracer& gc_tracer) {
  bool promotion_failure_occurred = false;

  TASKQUEUE_STATS_ONLY(print_taskqueue_stats());
  for (uint i = 0; i < ParallelGCThreads + 1; i++) {
    PSPromotionManager* manager = manager_array(i);
    assert(manager->claimed_stack_depth()->is_empty(), "should be empty");
    if (manager->_promotion_failed_info.has_failed()) {
      gc_tracer.report_promotion_failed(manager->_promotion_failed_info);
      promotion_failure_occurred = true;
    }
    manager->flush_labs();
  }
  return promotion_failure_occurred;
}

#if TASKQUEUE_STATS
void
PSPromotionManager::print_local_stats(outputStream* const out, uint i) const {
  #define FMT " " SIZE_FORMAT_W(10)
  out->print_cr("%3u" FMT FMT FMT FMT, i, _masked_pushes, _masked_steals,
                _arrays_chunked, _array_chunks_processed);
  #undef FMT
}

static const char* const pm_stats_hdr[] = {
  "    --------masked-------     arrays      array",
  "thr       push      steal    chunked     chunks",
  "--- ---------- ---------- ---------- ----------"
};

void
PSPromotionManager::print_taskqueue_stats() {
  if (!develop_log_is_enabled(Trace, gc, task, stats)) {
    return;
  }
  LogHandle(gc, task, stats) log;
  ResourceMark rm;
  outputStream* out = log.trace_stream();
  out->print_cr("== GC Tasks Stats, GC %3d",
                ParallelScavengeHeap::heap()->total_collections());

  TaskQueueStats totals;
  out->print("thr "); TaskQueueStats::print_header(1, out); out->cr();
  out->print("--- "); TaskQueueStats::print_header(2, out); out->cr();
  for (uint i = 0; i < ParallelGCThreads + 1; ++i) {
    TaskQueueStats& next = manager_array(i)->_claimed_stack_depth.stats;
    out->print("%3d ", i); next.print(out); out->cr();
    totals += next;
  }
  out->print("tot "); totals.print(out); out->cr();

  const uint hlines = sizeof(pm_stats_hdr) / sizeof(pm_stats_hdr[0]);
  for (uint i = 0; i < hlines; ++i) out->print_cr("%s", pm_stats_hdr[i]);
  for (uint i = 0; i < ParallelGCThreads + 1; ++i) {
    manager_array(i)->print_local_stats(out, i);
  }
}

void
PSPromotionManager::reset_stats() {
  claimed_stack_depth()->stats.reset();
  _masked_pushes = _masked_steals = 0;
  _arrays_chunked = _array_chunks_processed = 0;
}
#endif // TASKQUEUE_STATS

PSPromotionManager::PSPromotionManager() {
  ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();

  // We set the old lab's start array.
  _old_lab.set_start_array(old_gen()->start_array());

  uint queue_size;
  claimed_stack_depth()->initialize();
  queue_size = claimed_stack_depth()->max_elems();

  _totally_drain = (ParallelGCThreads == 1) || (GCDrainStackTargetSize == 0);
  if (_totally_drain) {
    _target_stack_size = 0;
  } else {
    // don't let the target stack size to be more than 1/4 of the entries
    _target_stack_size = (uint) MIN2((uint) GCDrainStackTargetSize,
                                     (uint) (queue_size / 4));
  }

  _array_chunk_size = ParGCArrayScanChunk;
  // let's choose 1.5x the chunk size
  _min_array_size_for_chunking = 3 * _array_chunk_size / 2;

  reset();
}

void PSPromotionManager::reset() {
  assert(stacks_empty(), "reset of non-empty stack");

  // We need to get an assert in here to make sure the labs are always flushed.

  ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();

  // Do not prefill the LAB's, save heap wastage!
  HeapWord* lab_base = young_space()->top();
  _young_lab.initialize(MemRegion(lab_base, (size_t)0));
  _young_gen_is_full = false;

  lab_base = old_gen()->object_space()->top();
  _old_lab.initialize(MemRegion(lab_base, (size_t)0));
  _old_gen_is_full = false;

  _promotion_failed_info.reset();

  TASKQUEUE_STATS_ONLY(reset_stats());
}


void PSPromotionManager::drain_stacks_depth(bool totally_drain) {
  totally_drain = totally_drain || _totally_drain;

#ifdef ASSERT
  ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();
  MutableSpace* to_space = heap->young_gen()->to_space();
  MutableSpace* old_space = heap->old_gen()->object_space();
#endif /* ASSERT */

  OopStarTaskQueue* const tq = claimed_stack_depth();
  do {
    StarTask p;

    // Drain overflow stack first, so other threads can steal from
    // claimed stack while we work.
    while (tq->pop_overflow(p)) {
      process_popped_location_depth(p);
    }

    if (totally_drain) {
      while (tq->pop_local(p)) {
        process_popped_location_depth(p);
      }
    } else {
      while (tq->size() > _target_stack_size && tq->pop_local(p)) {
        process_popped_location_depth(p);
      }
    }
  } while (totally_drain && !tq->taskqueue_empty() || !tq->overflow_empty());

  assert(!totally_drain || tq->taskqueue_empty(), "Sanity");
  assert(totally_drain || tq->size() <= _target_stack_size, "Sanity");
  assert(tq->overflow_empty(), "Sanity");
}

void PSPromotionManager::flush_labs() {
  assert(stacks_empty(), "Attempt to flush lab with live stack");

  // If either promotion lab fills up, we can flush the
  // lab but not refill it, so check first.
  assert(!_young_lab.is_flushed() || _young_gen_is_full, "Sanity");
  if (!_young_lab.is_flushed())
    _young_lab.flush();

  assert(!_old_lab.is_flushed() || _old_gen_is_full, "Sanity");
  if (!_old_lab.is_flushed())
    _old_lab.flush();

  // Let PSScavenge know if we overflowed
  if (_young_gen_is_full) {
    PSScavenge::set_survivor_overflow(true);
  }
}

template <class T> void PSPromotionManager::process_array_chunk_work(
                                                 oop obj,
                                                 int start, int end) {
  assert(start <= end, "invariant");
  T* const base      = (T*)objArrayOop(obj)->base();
  T* p               = base + start;
  T* const chunk_end = base + end;
  while (p < chunk_end) {
    if (PSScavenge::should_scavenge(p)) {
      claim_or_forward_depth(p);
    }
    ++p;
  }
}

void PSPromotionManager::process_array_chunk(oop old) {
  assert(PSChunkLargeArrays, "invariant");
  assert(old->is_objArray(), "invariant");
  assert(old->is_forwarded(), "invariant");

  TASKQUEUE_STATS_ONLY(++_array_chunks_processed);

  oop const obj = old->forwardee();

  int start;
  int const end = arrayOop(old)->length();
  if (end > (int) _min_array_size_for_chunking) {
    // we'll chunk more
    start = end - _array_chunk_size;
    assert(start > 0, "invariant");
    arrayOop(old)->set_length(start);
    push_depth(mask_chunked_array_oop(old));
    TASKQUEUE_STATS_ONLY(++_masked_pushes);
  } else {
    // this is the final chunk for this array
    start = 0;
    int const actual_length = arrayOop(obj)->length();
    arrayOop(old)->set_length(actual_length);
  }

  if (UseCompressedOops) {
    process_array_chunk_work<narrowOop>(obj, start, end);
  } else {
    process_array_chunk_work<oop>(obj, start, end);
  }
}

class PushContentsClosure : public ExtendedOopClosure {
  PSPromotionManager* _pm;
 public:
  PushContentsClosure(PSPromotionManager* pm) : _pm(pm) {}

  template <typename T> void do_oop_nv(T* p) {
    if (PSScavenge::should_scavenge(p)) {
      _pm->claim_or_forward_depth(p);
    }
  }

  virtual void do_oop(oop* p)       { do_oop_nv(p); }
  virtual void do_oop(narrowOop* p) { do_oop_nv(p); }

  // Don't use the oop verification code in the oop_oop_iterate framework.
  debug_only(virtual bool should_verify_oops() { return false; })
};

void InstanceKlass::oop_ps_push_contents(oop obj, PSPromotionManager* pm) {
  PushContentsClosure cl(pm);
  oop_oop_iterate_oop_maps_reverse<true>(obj, &cl);
}

void InstanceMirrorKlass::oop_ps_push_contents(oop obj, PSPromotionManager* pm) {
    // Note that we don't have to follow the mirror -> klass pointer, since all
    // klasses that are dirty will be scavenged when we iterate over the
    // ClassLoaderData objects.

  InstanceKlass::oop_ps_push_contents(obj, pm);

  PushContentsClosure cl(pm);
  oop_oop_iterate_statics<true>(obj, &cl);
}

void InstanceClassLoaderKlass::oop_ps_push_contents(oop obj, PSPromotionManager* pm) {
  InstanceKlass::oop_ps_push_contents(obj, pm);

  // This is called by the young collector. It will already have taken care of
  // all class loader data. So, we don't have to follow the class loader ->
  // class loader data link.
}

template <class T>
static void oop_ps_push_contents_specialized(oop obj, InstanceRefKlass *klass, PSPromotionManager* pm) {
  T* referent_addr = (T*)java_lang_ref_Reference::referent_addr(obj);
  if (PSScavenge::should_scavenge(referent_addr)) {
    ReferenceProcessor* rp = PSScavenge::reference_processor();
    if (rp->discover_reference(obj, klass->reference_type())) {
      // reference already enqueued, referent and next will be traversed later
      klass->InstanceKlass::oop_ps_push_contents(obj, pm);
      return;
    } else {
      // treat referent as normal oop
      pm->claim_or_forward_depth(referent_addr);
    }
  }
  // Treat discovered as normal oop, if ref is not "active",
  // i.e. if next is non-NULL.
  T* next_addr = (T*)java_lang_ref_Reference::next_addr(obj);
  T  next_oop = oopDesc::load_heap_oop(next_addr);
  if (!oopDesc::is_null(next_oop)) { // i.e. ref is not "active"
    T* discovered_addr = (T*)java_lang_ref_Reference::discovered_addr(obj);
    log_develop_trace(gc, ref)("   Process discovered as normal " PTR_FORMAT, p2i(discovered_addr));
    if (PSScavenge::should_scavenge(discovered_addr)) {
      pm->claim_or_forward_depth(discovered_addr);
    }
  }
  // Treat next as normal oop;  next is a link in the reference queue.
  if (PSScavenge::should_scavenge(next_addr)) {
    pm->claim_or_forward_depth(next_addr);
  }
  klass->InstanceKlass::oop_ps_push_contents(obj, pm);
}

void InstanceRefKlass::oop_ps_push_contents(oop obj, PSPromotionManager* pm) {
  if (UseCompressedOops) {
    oop_ps_push_contents_specialized<narrowOop>(obj, this, pm);
  } else {
    oop_ps_push_contents_specialized<oop>(obj, this, pm);
  }
}

void ObjArrayKlass::oop_ps_push_contents(oop obj, PSPromotionManager* pm) {
  assert(obj->is_objArray(), "obj must be obj array");
  PushContentsClosure cl(pm);
  oop_oop_iterate_elements<true>(objArrayOop(obj), &cl);
}

void TypeArrayKlass::oop_ps_push_contents(oop obj, PSPromotionManager* pm) {
  assert(obj->is_typeArray(),"must be a type array");
  ShouldNotReachHere();
}

oop PSPromotionManager::oop_promotion_failed(oop obj, markOop obj_mark) {
  assert(_old_gen_is_full || PromotionFailureALot, "Sanity");

  // Attempt to CAS in the header.
  // This tests if the header is still the same as when
  // this started.  If it is the same (i.e., no forwarding
  // pointer has been installed), then this thread owns
  // it.
  if (obj->cas_forward_to(obj, obj_mark)) {
    // We won any races, we "own" this object.
    assert(obj == obj->forwardee(), "Sanity");

    _promotion_failed_info.register_copy_failure(obj->size());

    push_contents(obj);

    // Save the mark if needed
    PSScavenge::oop_promotion_failed(obj, obj_mark);
  }  else {
    // We lost, someone else "owns" this object
    guarantee(obj->is_forwarded(), "Object must be forwarded if the cas failed.");

    // No unallocation to worry about.
    obj = obj->forwardee();
  }

  log_develop_trace(gc, scavenge)("{promotion-failure %s " PTR_FORMAT " (%d)}", obj->klass()->internal_name(), p2i(obj), obj->size());

  return obj;
}
