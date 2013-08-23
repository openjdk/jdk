/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/vmSymbols.hpp"
#include "code/icBuffer.hpp"
#include "gc_implementation/shared/collectorCounters.hpp"
#include "gc_implementation/shared/gcTraceTime.hpp"
#include "gc_implementation/shared/vmGCOperations.hpp"
#include "gc_interface/collectedHeap.inline.hpp"
#include "memory/filemap.hpp"
#include "memory/gcLocker.inline.hpp"
#include "memory/genCollectedHeap.hpp"
#include "memory/genOopClosures.inline.hpp"
#include "memory/generation.inline.hpp"
#include "memory/generationSpec.hpp"
#include "memory/resourceArea.hpp"
#include "memory/sharedHeap.hpp"
#include "memory/space.hpp"
#include "oops/oop.inline.hpp"
#include "oops/oop.inline2.hpp"
#include "runtime/biasedLocking.hpp"
#include "runtime/fprofiler.hpp"
#include "runtime/handles.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/java.hpp"
#include "runtime/vmThread.hpp"
#include "services/memoryService.hpp"
#include "utilities/vmError.hpp"
#include "utilities/workgroup.hpp"
#include "utilities/macros.hpp"
#if INCLUDE_ALL_GCS
#include "gc_implementation/concurrentMarkSweep/concurrentMarkSweepThread.hpp"
#include "gc_implementation/concurrentMarkSweep/vmCMSOperations.hpp"
#endif // INCLUDE_ALL_GCS

GenCollectedHeap* GenCollectedHeap::_gch;
NOT_PRODUCT(size_t GenCollectedHeap::_skip_header_HeapWords = 0;)

// The set of potentially parallel tasks in strong root scanning.
enum GCH_process_strong_roots_tasks {
  // We probably want to parallelize both of these internally, but for now...
  GCH_PS_younger_gens,
  // Leave this one last.
  GCH_PS_NumElements
};

GenCollectedHeap::GenCollectedHeap(GenCollectorPolicy *policy) :
  SharedHeap(policy),
  _gen_policy(policy),
  _gen_process_strong_tasks(new SubTasksDone(GCH_PS_NumElements)),
  _full_collections_completed(0)
{
  if (_gen_process_strong_tasks == NULL ||
      !_gen_process_strong_tasks->valid()) {
    vm_exit_during_initialization("Failed necessary allocation.");
  }
  assert(policy != NULL, "Sanity check");
}

jint GenCollectedHeap::initialize() {
  CollectedHeap::pre_initialize();

  int i;
  _n_gens = gen_policy()->number_of_generations();

  // While there are no constraints in the GC code that HeapWordSize
  // be any particular value, there are multiple other areas in the
  // system which believe this to be true (e.g. oop->object_size in some
  // cases incorrectly returns the size in wordSize units rather than
  // HeapWordSize).
  guarantee(HeapWordSize == wordSize, "HeapWordSize must equal wordSize");

  // The heap must be at least as aligned as generations.
  size_t alignment = Generation::GenGrain;

  _gen_specs = gen_policy()->generations();

  // Make sure the sizes are all aligned.
  for (i = 0; i < _n_gens; i++) {
    _gen_specs[i]->align(alignment);
  }

  // Allocate space for the heap.

  char* heap_address;
  size_t total_reserved = 0;
  int n_covered_regions = 0;
  ReservedSpace heap_rs(0);

  heap_address = allocate(alignment, &total_reserved,
                          &n_covered_regions, &heap_rs);

  if (!heap_rs.is_reserved()) {
    vm_shutdown_during_initialization(
      "Could not reserve enough space for object heap");
    return JNI_ENOMEM;
  }

  _reserved = MemRegion((HeapWord*)heap_rs.base(),
                        (HeapWord*)(heap_rs.base() + heap_rs.size()));

  // It is important to do this in a way such that concurrent readers can't
  // temporarily think somethings in the heap.  (Seen this happen in asserts.)
  _reserved.set_word_size(0);
  _reserved.set_start((HeapWord*)heap_rs.base());
  size_t actual_heap_size = heap_rs.size();
  _reserved.set_end((HeapWord*)(heap_rs.base() + actual_heap_size));

  _rem_set = collector_policy()->create_rem_set(_reserved, n_covered_regions);
  set_barrier_set(rem_set()->bs());

  _gch = this;

  for (i = 0; i < _n_gens; i++) {
    ReservedSpace this_rs = heap_rs.first_part(_gen_specs[i]->max_size(), false, false);
    _gens[i] = _gen_specs[i]->init(this_rs, i, rem_set());
    heap_rs = heap_rs.last_part(_gen_specs[i]->max_size());
  }
  clear_incremental_collection_failed();

#if INCLUDE_ALL_GCS
  // If we are running CMS, create the collector responsible
  // for collecting the CMS generations.
  if (collector_policy()->is_concurrent_mark_sweep_policy()) {
    bool success = create_cms_collector();
    if (!success) return JNI_ENOMEM;
  }
#endif // INCLUDE_ALL_GCS

  return JNI_OK;
}


char* GenCollectedHeap::allocate(size_t alignment,
                                 size_t* _total_reserved,
                                 int* _n_covered_regions,
                                 ReservedSpace* heap_rs){
  const char overflow_msg[] = "The size of the object heap + VM data exceeds "
    "the maximum representable size";

  // Now figure out the total size.
  size_t total_reserved = 0;
  int n_covered_regions = 0;
  const size_t pageSize = UseLargePages ?
      os::large_page_size() : os::vm_page_size();

  for (int i = 0; i < _n_gens; i++) {
    total_reserved += _gen_specs[i]->max_size();
    if (total_reserved < _gen_specs[i]->max_size()) {
      vm_exit_during_initialization(overflow_msg);
    }
    n_covered_regions += _gen_specs[i]->n_covered_regions();
  }
  assert(total_reserved % pageSize == 0,
         err_msg("Gen size; total_reserved=" SIZE_FORMAT ", pageSize="
                 SIZE_FORMAT, total_reserved, pageSize));

  // Needed until the cardtable is fixed to have the right number
  // of covered regions.
  n_covered_regions += 2;

  if (UseLargePages) {
    assert(total_reserved != 0, "total_reserved cannot be 0");
    total_reserved = round_to(total_reserved, os::large_page_size());
    if (total_reserved < os::large_page_size()) {
      vm_exit_during_initialization(overflow_msg);
    }
  }

      *_total_reserved = total_reserved;
      *_n_covered_regions = n_covered_regions;
  *heap_rs = Universe::reserve_heap(total_reserved, alignment);
  return heap_rs->base();
}


void GenCollectedHeap::post_initialize() {
  SharedHeap::post_initialize();
  TwoGenerationCollectorPolicy *policy =
    (TwoGenerationCollectorPolicy *)collector_policy();
  guarantee(policy->is_two_generation_policy(), "Illegal policy type");
  DefNewGeneration* def_new_gen = (DefNewGeneration*) get_gen(0);
  assert(def_new_gen->kind() == Generation::DefNew ||
         def_new_gen->kind() == Generation::ParNew ||
         def_new_gen->kind() == Generation::ASParNew,
         "Wrong generation kind");

  Generation* old_gen = get_gen(1);
  assert(old_gen->kind() == Generation::ConcurrentMarkSweep ||
         old_gen->kind() == Generation::ASConcurrentMarkSweep ||
         old_gen->kind() == Generation::MarkSweepCompact,
    "Wrong generation kind");

  policy->initialize_size_policy(def_new_gen->eden()->capacity(),
                                 old_gen->capacity(),
                                 def_new_gen->from()->capacity());
  policy->initialize_gc_policy_counters();
}

void GenCollectedHeap::ref_processing_init() {
  SharedHeap::ref_processing_init();
  for (int i = 0; i < _n_gens; i++) {
    _gens[i]->ref_processor_init();
  }
}

size_t GenCollectedHeap::capacity() const {
  size_t res = 0;
  for (int i = 0; i < _n_gens; i++) {
    res += _gens[i]->capacity();
  }
  return res;
}

size_t GenCollectedHeap::used() const {
  size_t res = 0;
  for (int i = 0; i < _n_gens; i++) {
    res += _gens[i]->used();
  }
  return res;
}

// Save the "used_region" for generations level and lower.
void GenCollectedHeap::save_used_regions(int level) {
  assert(level < _n_gens, "Illegal level parameter");
  for (int i = level; i >= 0; i--) {
    _gens[i]->save_used_region();
  }
}

size_t GenCollectedHeap::max_capacity() const {
  size_t res = 0;
  for (int i = 0; i < _n_gens; i++) {
    res += _gens[i]->max_capacity();
  }
  return res;
}

// Update the _full_collections_completed counter
// at the end of a stop-world full GC.
unsigned int GenCollectedHeap::update_full_collections_completed() {
  MonitorLockerEx ml(FullGCCount_lock, Mutex::_no_safepoint_check_flag);
  assert(_full_collections_completed <= _total_full_collections,
         "Can't complete more collections than were started");
  _full_collections_completed = _total_full_collections;
  ml.notify_all();
  return _full_collections_completed;
}

// Update the _full_collections_completed counter, as appropriate,
// at the end of a concurrent GC cycle. Note the conditional update
// below to allow this method to be called by a concurrent collector
// without synchronizing in any manner with the VM thread (which
// may already have initiated a STW full collection "concurrently").
unsigned int GenCollectedHeap::update_full_collections_completed(unsigned int count) {
  MonitorLockerEx ml(FullGCCount_lock, Mutex::_no_safepoint_check_flag);
  assert((_full_collections_completed <= _total_full_collections) &&
         (count <= _total_full_collections),
         "Can't complete more collections than were started");
  if (count > _full_collections_completed) {
    _full_collections_completed = count;
    ml.notify_all();
  }
  return _full_collections_completed;
}


#ifndef PRODUCT
// Override of memory state checking method in CollectedHeap:
// Some collectors (CMS for example) can't have badHeapWordVal written
// in the first two words of an object. (For instance , in the case of
// CMS these words hold state used to synchronize between certain
// (concurrent) GC steps and direct allocating mutators.)
// The skip_header_HeapWords() method below, allows us to skip
// over the requisite number of HeapWord's. Note that (for
// generational collectors) this means that those many words are
// skipped in each object, irrespective of the generation in which
// that object lives. The resultant loss of precision seems to be
// harmless and the pain of avoiding that imprecision appears somewhat
// higher than we are prepared to pay for such rudimentary debugging
// support.
void GenCollectedHeap::check_for_non_bad_heap_word_value(HeapWord* addr,
                                                         size_t size) {
  if (CheckMemoryInitialization && ZapUnusedHeapArea) {
    // We are asked to check a size in HeapWords,
    // but the memory is mangled in juint words.
    juint* start = (juint*) (addr + skip_header_HeapWords());
    juint* end   = (juint*) (addr + size);
    for (juint* slot = start; slot < end; slot += 1) {
      assert(*slot == badHeapWordVal,
             "Found non badHeapWordValue in pre-allocation check");
    }
  }
}
#endif

HeapWord* GenCollectedHeap::attempt_allocation(size_t size,
                                               bool is_tlab,
                                               bool first_only) {
  HeapWord* res;
  for (int i = 0; i < _n_gens; i++) {
    if (_gens[i]->should_allocate(size, is_tlab)) {
      res = _gens[i]->allocate(size, is_tlab);
      if (res != NULL) return res;
      else if (first_only) break;
    }
  }
  // Otherwise...
  return NULL;
}

HeapWord* GenCollectedHeap::mem_allocate(size_t size,
                                         bool* gc_overhead_limit_was_exceeded) {
  return collector_policy()->mem_allocate_work(size,
                                               false /* is_tlab */,
                                               gc_overhead_limit_was_exceeded);
}

bool GenCollectedHeap::must_clear_all_soft_refs() {
  return _gc_cause == GCCause::_last_ditch_collection;
}

bool GenCollectedHeap::should_do_concurrent_full_gc(GCCause::Cause cause) {
  return UseConcMarkSweepGC &&
         ((cause == GCCause::_gc_locker && GCLockerInvokesConcurrent) ||
          (cause == GCCause::_java_lang_system_gc && ExplicitGCInvokesConcurrent));
}

void GenCollectedHeap::do_collection(bool  full,
                                     bool   clear_all_soft_refs,
                                     size_t size,
                                     bool   is_tlab,
                                     int    max_level) {
  bool prepared_for_verification = false;
  ResourceMark rm;
  DEBUG_ONLY(Thread* my_thread = Thread::current();)

  assert(SafepointSynchronize::is_at_safepoint(), "should be at safepoint");
  assert(my_thread->is_VM_thread() ||
         my_thread->is_ConcurrentGC_thread(),
         "incorrect thread type capability");
  assert(Heap_lock->is_locked(),
         "the requesting thread should have the Heap_lock");
  guarantee(!is_gc_active(), "collection is not reentrant");
  assert(max_level < n_gens(), "sanity check");

  if (GC_locker::check_active_before_gc()) {
    return; // GC is disabled (e.g. JNI GetXXXCritical operation)
  }

  const bool do_clear_all_soft_refs = clear_all_soft_refs ||
                          collector_policy()->should_clear_all_soft_refs();

  ClearedAllSoftRefs casr(do_clear_all_soft_refs, collector_policy());

  const size_t metadata_prev_used = MetaspaceAux::allocated_used_bytes();

  print_heap_before_gc();

  {
    FlagSetting fl(_is_gc_active, true);

    bool complete = full && (max_level == (n_gens()-1));
    const char* gc_cause_prefix = complete ? "Full GC" : "GC";
    gclog_or_tty->date_stamp(PrintGC && PrintGCDateStamps);
    TraceCPUTime tcpu(PrintGCDetails, true, gclog_or_tty);
    GCTraceTime t(GCCauseString(gc_cause_prefix, gc_cause()), PrintGCDetails, false, NULL);

    gc_prologue(complete);
    increment_total_collections(complete);

    size_t gch_prev_used = used();

    int starting_level = 0;
    if (full) {
      // Search for the oldest generation which will collect all younger
      // generations, and start collection loop there.
      for (int i = max_level; i >= 0; i--) {
        if (_gens[i]->full_collects_younger_generations()) {
          starting_level = i;
          break;
        }
      }
    }

    bool must_restore_marks_for_biased_locking = false;

    int max_level_collected = starting_level;
    for (int i = starting_level; i <= max_level; i++) {
      if (_gens[i]->should_collect(full, size, is_tlab)) {
        if (i == n_gens() - 1) {  // a major collection is to happen
          if (!complete) {
            // The full_collections increment was missed above.
            increment_total_full_collections();
          }
          pre_full_gc_dump(NULL);    // do any pre full gc dumps
        }
        // Timer for individual generations. Last argument is false: no CR
        // FIXME: We should try to start the timing earlier to cover more of the GC pause
        GCTraceTime t1(_gens[i]->short_name(), PrintGCDetails, false, NULL);
        TraceCollectorStats tcs(_gens[i]->counters());
        TraceMemoryManagerStats tmms(_gens[i]->kind(),gc_cause());

        size_t prev_used = _gens[i]->used();
        _gens[i]->stat_record()->invocations++;
        _gens[i]->stat_record()->accumulated_time.start();

        // Must be done anew before each collection because
        // a previous collection will do mangling and will
        // change top of some spaces.
        record_gen_tops_before_GC();

        if (PrintGC && Verbose) {
          gclog_or_tty->print("level=%d invoke=%d size=" SIZE_FORMAT,
                     i,
                     _gens[i]->stat_record()->invocations,
                     size*HeapWordSize);
        }

        if (VerifyBeforeGC && i >= VerifyGCLevel &&
            total_collections() >= VerifyGCStartAt) {
          HandleMark hm;  // Discard invalid handles created during verification
          if (!prepared_for_verification) {
            prepare_for_verify();
            prepared_for_verification = true;
          }
          Universe::verify(" VerifyBeforeGC:");
        }
        COMPILER2_PRESENT(DerivedPointerTable::clear());

        if (!must_restore_marks_for_biased_locking &&
            _gens[i]->performs_in_place_marking()) {
          // We perform this mark word preservation work lazily
          // because it's only at this point that we know whether we
          // absolutely have to do it; we want to avoid doing it for
          // scavenge-only collections where it's unnecessary
          must_restore_marks_for_biased_locking = true;
          BiasedLocking::preserve_marks();
        }

        // Do collection work
        {
          // Note on ref discovery: For what appear to be historical reasons,
          // GCH enables and disabled (by enqueing) refs discovery.
          // In the future this should be moved into the generation's
          // collect method so that ref discovery and enqueueing concerns
          // are local to a generation. The collect method could return
          // an appropriate indication in the case that notification on
          // the ref lock was needed. This will make the treatment of
          // weak refs more uniform (and indeed remove such concerns
          // from GCH). XXX

          HandleMark hm;  // Discard invalid handles created during gc
          save_marks();   // save marks for all gens
          // We want to discover references, but not process them yet.
          // This mode is disabled in process_discovered_references if the
          // generation does some collection work, or in
          // enqueue_discovered_references if the generation returns
          // without doing any work.
          ReferenceProcessor* rp = _gens[i]->ref_processor();
          // If the discovery of ("weak") refs in this generation is
          // atomic wrt other collectors in this configuration, we
          // are guaranteed to have empty discovered ref lists.
          if (rp->discovery_is_atomic()) {
            rp->enable_discovery(true /*verify_disabled*/, true /*verify_no_refs*/);
            rp->setup_policy(do_clear_all_soft_refs);
          } else {
            // collect() below will enable discovery as appropriate
          }
          _gens[i]->collect(full, do_clear_all_soft_refs, size, is_tlab);
          if (!rp->enqueuing_is_done()) {
            rp->enqueue_discovered_references();
          } else {
            rp->set_enqueuing_is_done(false);
          }
          rp->verify_no_references_recorded();
        }
        max_level_collected = i;

        // Determine if allocation request was met.
        if (size > 0) {
          if (!is_tlab || _gens[i]->supports_tlab_allocation()) {
            if (size*HeapWordSize <= _gens[i]->unsafe_max_alloc_nogc()) {
              size = 0;
            }
          }
        }

        COMPILER2_PRESENT(DerivedPointerTable::update_pointers());

        _gens[i]->stat_record()->accumulated_time.stop();

        update_gc_stats(i, full);

        if (VerifyAfterGC && i >= VerifyGCLevel &&
            total_collections() >= VerifyGCStartAt) {
          HandleMark hm;  // Discard invalid handles created during verification
          Universe::verify(" VerifyAfterGC:");
        }

        if (PrintGCDetails) {
          gclog_or_tty->print(":");
          _gens[i]->print_heap_change(prev_used);
        }
      }
    }

    // Update "complete" boolean wrt what actually transpired --
    // for instance, a promotion failure could have led to
    // a whole heap collection.
    complete = complete || (max_level_collected == n_gens() - 1);

    if (complete) { // We did a "major" collection
      // FIXME: See comment at pre_full_gc_dump call
      post_full_gc_dump(NULL);   // do any post full gc dumps
    }

    if (PrintGCDetails) {
      print_heap_change(gch_prev_used);

      // Print metaspace info for full GC with PrintGCDetails flag.
      if (complete) {
        MetaspaceAux::print_metaspace_change(metadata_prev_used);
      }
    }

    for (int j = max_level_collected; j >= 0; j -= 1) {
      // Adjust generation sizes.
      _gens[j]->compute_new_size();
    }

    if (complete) {
      // Delete metaspaces for unloaded class loaders and clean up loader_data graph
      ClassLoaderDataGraph::purge();
      MetaspaceAux::verify_metrics();
      // Resize the metaspace capacity after full collections
      MetaspaceGC::compute_new_size();
      update_full_collections_completed();
    }

    // Track memory usage and detect low memory after GC finishes
    MemoryService::track_memory_usage();

    gc_epilogue(complete);

    if (must_restore_marks_for_biased_locking) {
      BiasedLocking::restore_marks();
    }
  }

  AdaptiveSizePolicy* sp = gen_policy()->size_policy();
  AdaptiveSizePolicyOutput(sp, total_collections());

  print_heap_after_gc();

#ifdef TRACESPINNING
  ParallelTaskTerminator::print_termination_counts();
#endif
}

HeapWord* GenCollectedHeap::satisfy_failed_allocation(size_t size, bool is_tlab) {
  return collector_policy()->satisfy_failed_allocation(size, is_tlab);
}

void GenCollectedHeap::set_par_threads(uint t) {
  SharedHeap::set_par_threads(t);
  _gen_process_strong_tasks->set_n_threads(t);
}

void GenCollectedHeap::
gen_process_strong_roots(int level,
                         bool younger_gens_as_roots,
                         bool activate_scope,
                         bool is_scavenging,
                         SharedHeap::ScanningOption so,
                         OopsInGenClosure* not_older_gens,
                         bool do_code_roots,
                         OopsInGenClosure* older_gens,
                         KlassClosure* klass_closure) {
  // General strong roots.

  if (!do_code_roots) {
    SharedHeap::process_strong_roots(activate_scope, is_scavenging, so,
                                     not_older_gens, NULL, klass_closure);
  } else {
    bool do_code_marking = (activate_scope || nmethod::oops_do_marking_is_active());
    CodeBlobToOopClosure code_roots(not_older_gens, /*do_marking=*/ do_code_marking);
    SharedHeap::process_strong_roots(activate_scope, is_scavenging, so,
                                     not_older_gens, &code_roots, klass_closure);
  }

  if (younger_gens_as_roots) {
    if (!_gen_process_strong_tasks->is_task_claimed(GCH_PS_younger_gens)) {
      for (int i = 0; i < level; i++) {
        not_older_gens->set_generation(_gens[i]);
        _gens[i]->oop_iterate(not_older_gens);
      }
      not_older_gens->reset_generation();
    }
  }
  // When collection is parallel, all threads get to cooperate to do
  // older-gen scanning.
  for (int i = level+1; i < _n_gens; i++) {
    older_gens->set_generation(_gens[i]);
    rem_set()->younger_refs_iterate(_gens[i], older_gens);
    older_gens->reset_generation();
  }

  _gen_process_strong_tasks->all_tasks_completed();
}

void GenCollectedHeap::gen_process_weak_roots(OopClosure* root_closure,
                                              CodeBlobClosure* code_roots) {
  SharedHeap::process_weak_roots(root_closure, code_roots);
  // "Local" "weak" refs
  for (int i = 0; i < _n_gens; i++) {
    _gens[i]->ref_processor()->weak_oops_do(root_closure);
  }
}

#define GCH_SINCE_SAVE_MARKS_ITERATE_DEFN(OopClosureType, nv_suffix)    \
void GenCollectedHeap::                                                 \
oop_since_save_marks_iterate(int level,                                 \
                             OopClosureType* cur,                       \
                             OopClosureType* older) {                   \
  _gens[level]->oop_since_save_marks_iterate##nv_suffix(cur);           \
  for (int i = level+1; i < n_gens(); i++) {                            \
    _gens[i]->oop_since_save_marks_iterate##nv_suffix(older);           \
  }                                                                     \
}

ALL_SINCE_SAVE_MARKS_CLOSURES(GCH_SINCE_SAVE_MARKS_ITERATE_DEFN)

#undef GCH_SINCE_SAVE_MARKS_ITERATE_DEFN

bool GenCollectedHeap::no_allocs_since_save_marks(int level) {
  for (int i = level; i < _n_gens; i++) {
    if (!_gens[i]->no_allocs_since_save_marks()) return false;
  }
  return true;
}

bool GenCollectedHeap::supports_inline_contig_alloc() const {
  return _gens[0]->supports_inline_contig_alloc();
}

HeapWord** GenCollectedHeap::top_addr() const {
  return _gens[0]->top_addr();
}

HeapWord** GenCollectedHeap::end_addr() const {
  return _gens[0]->end_addr();
}

size_t GenCollectedHeap::unsafe_max_alloc() {
  return _gens[0]->unsafe_max_alloc_nogc();
}

// public collection interfaces

void GenCollectedHeap::collect(GCCause::Cause cause) {
  if (should_do_concurrent_full_gc(cause)) {
#if INCLUDE_ALL_GCS
    // mostly concurrent full collection
    collect_mostly_concurrent(cause);
#else  // INCLUDE_ALL_GCS
    ShouldNotReachHere();
#endif // INCLUDE_ALL_GCS
  } else {
#ifdef ASSERT
    if (cause == GCCause::_scavenge_alot) {
      // minor collection only
      collect(cause, 0);
    } else {
      // Stop-the-world full collection
      collect(cause, n_gens() - 1);
    }
#else
    // Stop-the-world full collection
    collect(cause, n_gens() - 1);
#endif
  }
}

void GenCollectedHeap::collect(GCCause::Cause cause, int max_level) {
  // The caller doesn't have the Heap_lock
  assert(!Heap_lock->owned_by_self(), "this thread should not own the Heap_lock");
  MutexLocker ml(Heap_lock);
  collect_locked(cause, max_level);
}

void GenCollectedHeap::collect_locked(GCCause::Cause cause) {
  // The caller has the Heap_lock
  assert(Heap_lock->owned_by_self(), "this thread should own the Heap_lock");
  collect_locked(cause, n_gens() - 1);
}

// this is the private collection interface
// The Heap_lock is expected to be held on entry.

void GenCollectedHeap::collect_locked(GCCause::Cause cause, int max_level) {
  // Read the GC count while holding the Heap_lock
  unsigned int gc_count_before      = total_collections();
  unsigned int full_gc_count_before = total_full_collections();
  {
    MutexUnlocker mu(Heap_lock);  // give up heap lock, execute gets it back
    VM_GenCollectFull op(gc_count_before, full_gc_count_before,
                         cause, max_level);
    VMThread::execute(&op);
  }
}

#if INCLUDE_ALL_GCS
bool GenCollectedHeap::create_cms_collector() {

  assert(((_gens[1]->kind() == Generation::ConcurrentMarkSweep) ||
         (_gens[1]->kind() == Generation::ASConcurrentMarkSweep)),
         "Unexpected generation kinds");
  // Skip two header words in the block content verification
  NOT_PRODUCT(_skip_header_HeapWords = CMSCollector::skip_header_HeapWords();)
  CMSCollector* collector = new CMSCollector(
    (ConcurrentMarkSweepGeneration*)_gens[1],
    _rem_set->as_CardTableRS(),
    (ConcurrentMarkSweepPolicy*) collector_policy());

  if (collector == NULL || !collector->completed_initialization()) {
    if (collector) {
      delete collector;  // Be nice in embedded situation
    }
    vm_shutdown_during_initialization("Could not create CMS collector");
    return false;
  }
  return true;  // success
}

void GenCollectedHeap::collect_mostly_concurrent(GCCause::Cause cause) {
  assert(!Heap_lock->owned_by_self(), "Should not own Heap_lock");

  MutexLocker ml(Heap_lock);
  // Read the GC counts while holding the Heap_lock
  unsigned int full_gc_count_before = total_full_collections();
  unsigned int gc_count_before      = total_collections();
  {
    MutexUnlocker mu(Heap_lock);
    VM_GenCollectFullConcurrent op(gc_count_before, full_gc_count_before, cause);
    VMThread::execute(&op);
  }
}
#endif // INCLUDE_ALL_GCS

void GenCollectedHeap::do_full_collection(bool clear_all_soft_refs) {
   do_full_collection(clear_all_soft_refs, _n_gens - 1);
}

void GenCollectedHeap::do_full_collection(bool clear_all_soft_refs,
                                          int max_level) {
  int local_max_level;
  if (!incremental_collection_will_fail(false /* don't consult_young */) &&
      gc_cause() == GCCause::_gc_locker) {
    local_max_level = 0;
  } else {
    local_max_level = max_level;
  }

  do_collection(true                 /* full */,
                clear_all_soft_refs  /* clear_all_soft_refs */,
                0                    /* size */,
                false                /* is_tlab */,
                local_max_level      /* max_level */);
  // Hack XXX FIX ME !!!
  // A scavenge may not have been attempted, or may have
  // been attempted and failed, because the old gen was too full
  if (local_max_level == 0 && gc_cause() == GCCause::_gc_locker &&
      incremental_collection_will_fail(false /* don't consult_young */)) {
    if (PrintGCDetails) {
      gclog_or_tty->print_cr("GC locker: Trying a full collection "
                             "because scavenge failed");
    }
    // This time allow the old gen to be collected as well
    do_collection(true                 /* full */,
                  clear_all_soft_refs  /* clear_all_soft_refs */,
                  0                    /* size */,
                  false                /* is_tlab */,
                  n_gens() - 1         /* max_level */);
  }
}

bool GenCollectedHeap::is_in_young(oop p) {
  bool result = ((HeapWord*)p) < _gens[_n_gens - 1]->reserved().start();
  assert(result == _gens[0]->is_in_reserved(p),
         err_msg("incorrect test - result=%d, p=" PTR_FORMAT, result, (void*)p));
  return result;
}

// Returns "TRUE" iff "p" points into the committed areas of the heap.
bool GenCollectedHeap::is_in(const void* p) const {
  #ifndef ASSERT
  guarantee(VerifyBeforeGC      ||
            VerifyDuringGC      ||
            VerifyBeforeExit    ||
            VerifyDuringStartup ||
            PrintAssembly       ||
            tty->count() != 0   ||   // already printing
            VerifyAfterGC       ||
    VMError::fatal_error_in_progress(), "too expensive");

  #endif
  // This might be sped up with a cache of the last generation that
  // answered yes.
  for (int i = 0; i < _n_gens; i++) {
    if (_gens[i]->is_in(p)) return true;
  }
  // Otherwise...
  return false;
}

#ifdef ASSERT
// Don't implement this by using is_in_young().  This method is used
// in some cases to check that is_in_young() is correct.
bool GenCollectedHeap::is_in_partial_collection(const void* p) {
  assert(is_in_reserved(p) || p == NULL,
    "Does not work if address is non-null and outside of the heap");
  return p < _gens[_n_gens - 2]->reserved().end() && p != NULL;
}
#endif

void GenCollectedHeap::oop_iterate(ExtendedOopClosure* cl) {
  for (int i = 0; i < _n_gens; i++) {
    _gens[i]->oop_iterate(cl);
  }
}

void GenCollectedHeap::oop_iterate(MemRegion mr, ExtendedOopClosure* cl) {
  for (int i = 0; i < _n_gens; i++) {
    _gens[i]->oop_iterate(mr, cl);
  }
}

void GenCollectedHeap::object_iterate(ObjectClosure* cl) {
  for (int i = 0; i < _n_gens; i++) {
    _gens[i]->object_iterate(cl);
  }
}

void GenCollectedHeap::safe_object_iterate(ObjectClosure* cl) {
  for (int i = 0; i < _n_gens; i++) {
    _gens[i]->safe_object_iterate(cl);
  }
}

Space* GenCollectedHeap::space_containing(const void* addr) const {
  for (int i = 0; i < _n_gens; i++) {
    Space* res = _gens[i]->space_containing(addr);
    if (res != NULL) return res;
  }
  // Otherwise...
  assert(false, "Could not find containing space");
  return NULL;
}


HeapWord* GenCollectedHeap::block_start(const void* addr) const {
  assert(is_in_reserved(addr), "block_start of address outside of heap");
  for (int i = 0; i < _n_gens; i++) {
    if (_gens[i]->is_in_reserved(addr)) {
      assert(_gens[i]->is_in(addr),
             "addr should be in allocated part of generation");
      return _gens[i]->block_start(addr);
    }
  }
  assert(false, "Some generation should contain the address");
  return NULL;
}

size_t GenCollectedHeap::block_size(const HeapWord* addr) const {
  assert(is_in_reserved(addr), "block_size of address outside of heap");
  for (int i = 0; i < _n_gens; i++) {
    if (_gens[i]->is_in_reserved(addr)) {
      assert(_gens[i]->is_in(addr),
             "addr should be in allocated part of generation");
      return _gens[i]->block_size(addr);
    }
  }
  assert(false, "Some generation should contain the address");
  return 0;
}

bool GenCollectedHeap::block_is_obj(const HeapWord* addr) const {
  assert(is_in_reserved(addr), "block_is_obj of address outside of heap");
  assert(block_start(addr) == addr, "addr must be a block start");
  for (int i = 0; i < _n_gens; i++) {
    if (_gens[i]->is_in_reserved(addr)) {
      return _gens[i]->block_is_obj(addr);
    }
  }
  assert(false, "Some generation should contain the address");
  return false;
}

bool GenCollectedHeap::supports_tlab_allocation() const {
  for (int i = 0; i < _n_gens; i += 1) {
    if (_gens[i]->supports_tlab_allocation()) {
      return true;
    }
  }
  return false;
}

size_t GenCollectedHeap::tlab_capacity(Thread* thr) const {
  size_t result = 0;
  for (int i = 0; i < _n_gens; i += 1) {
    if (_gens[i]->supports_tlab_allocation()) {
      result += _gens[i]->tlab_capacity();
    }
  }
  return result;
}

size_t GenCollectedHeap::unsafe_max_tlab_alloc(Thread* thr) const {
  size_t result = 0;
  for (int i = 0; i < _n_gens; i += 1) {
    if (_gens[i]->supports_tlab_allocation()) {
      result += _gens[i]->unsafe_max_tlab_alloc();
    }
  }
  return result;
}

HeapWord* GenCollectedHeap::allocate_new_tlab(size_t size) {
  bool gc_overhead_limit_was_exceeded;
  return collector_policy()->mem_allocate_work(size /* size */,
                                               true /* is_tlab */,
                                               &gc_overhead_limit_was_exceeded);
}

// Requires "*prev_ptr" to be non-NULL.  Deletes and a block of minimal size
// from the list headed by "*prev_ptr".
static ScratchBlock *removeSmallestScratch(ScratchBlock **prev_ptr) {
  bool first = true;
  size_t min_size = 0;   // "first" makes this conceptually infinite.
  ScratchBlock **smallest_ptr, *smallest;
  ScratchBlock  *cur = *prev_ptr;
  while (cur) {
    assert(*prev_ptr == cur, "just checking");
    if (first || cur->num_words < min_size) {
      smallest_ptr = prev_ptr;
      smallest     = cur;
      min_size     = smallest->num_words;
      first        = false;
    }
    prev_ptr = &cur->next;
    cur     =  cur->next;
  }
  smallest      = *smallest_ptr;
  *smallest_ptr = smallest->next;
  return smallest;
}

// Sort the scratch block list headed by res into decreasing size order,
// and set "res" to the result.
static void sort_scratch_list(ScratchBlock*& list) {
  ScratchBlock* sorted = NULL;
  ScratchBlock* unsorted = list;
  while (unsorted) {
    ScratchBlock *smallest = removeSmallestScratch(&unsorted);
    smallest->next  = sorted;
    sorted          = smallest;
  }
  list = sorted;
}

ScratchBlock* GenCollectedHeap::gather_scratch(Generation* requestor,
                                               size_t max_alloc_words) {
  ScratchBlock* res = NULL;
  for (int i = 0; i < _n_gens; i++) {
    _gens[i]->contribute_scratch(res, requestor, max_alloc_words);
  }
  sort_scratch_list(res);
  return res;
}

void GenCollectedHeap::release_scratch() {
  for (int i = 0; i < _n_gens; i++) {
    _gens[i]->reset_scratch();
  }
}

class GenPrepareForVerifyClosure: public GenCollectedHeap::GenClosure {
  void do_generation(Generation* gen) {
    gen->prepare_for_verify();
  }
};

void GenCollectedHeap::prepare_for_verify() {
  ensure_parsability(false);        // no need to retire TLABs
  GenPrepareForVerifyClosure blk;
  generation_iterate(&blk, false);
}


void GenCollectedHeap::generation_iterate(GenClosure* cl,
                                          bool old_to_young) {
  if (old_to_young) {
    for (int i = _n_gens-1; i >= 0; i--) {
      cl->do_generation(_gens[i]);
    }
  } else {
    for (int i = 0; i < _n_gens; i++) {
      cl->do_generation(_gens[i]);
    }
  }
}

void GenCollectedHeap::space_iterate(SpaceClosure* cl) {
  for (int i = 0; i < _n_gens; i++) {
    _gens[i]->space_iterate(cl, true);
  }
}

bool GenCollectedHeap::is_maximal_no_gc() const {
  for (int i = 0; i < _n_gens; i++) {
    if (!_gens[i]->is_maximal_no_gc()) {
      return false;
    }
  }
  return true;
}

void GenCollectedHeap::save_marks() {
  for (int i = 0; i < _n_gens; i++) {
    _gens[i]->save_marks();
  }
}

void GenCollectedHeap::compute_new_generation_sizes(int collectedGen) {
  for (int i = 0; i <= collectedGen; i++) {
    _gens[i]->compute_new_size();
  }
}

GenCollectedHeap* GenCollectedHeap::heap() {
  assert(_gch != NULL, "Uninitialized access to GenCollectedHeap::heap()");
  assert(_gch->kind() == CollectedHeap::GenCollectedHeap, "not a generational heap");
  return _gch;
}


void GenCollectedHeap::prepare_for_compaction() {
  guarantee(_n_gens = 2, "Wrong number of generations");
  Generation* old_gen = _gens[1];
  // Start by compacting into same gen.
  CompactPoint cp(old_gen, NULL, NULL);
  old_gen->prepare_for_compaction(&cp);
  Generation* young_gen = _gens[0];
  young_gen->prepare_for_compaction(&cp);
}

GCStats* GenCollectedHeap::gc_stats(int level) const {
  return _gens[level]->gc_stats();
}

void GenCollectedHeap::verify(bool silent, VerifyOption option /* ignored */) {
  for (int i = _n_gens-1; i >= 0; i--) {
    Generation* g = _gens[i];
    if (!silent) {
      gclog_or_tty->print(g->name());
      gclog_or_tty->print(" ");
    }
    g->verify();
  }
  if (!silent) {
    gclog_or_tty->print("remset ");
  }
  rem_set()->verify();
}

void GenCollectedHeap::print_on(outputStream* st) const {
  for (int i = 0; i < _n_gens; i++) {
    _gens[i]->print_on(st);
  }
  MetaspaceAux::print_on(st);
}

void GenCollectedHeap::gc_threads_do(ThreadClosure* tc) const {
  if (workers() != NULL) {
    workers()->threads_do(tc);
  }
#if INCLUDE_ALL_GCS
  if (UseConcMarkSweepGC) {
    ConcurrentMarkSweepThread::threads_do(tc);
  }
#endif // INCLUDE_ALL_GCS
}

void GenCollectedHeap::print_gc_threads_on(outputStream* st) const {
#if INCLUDE_ALL_GCS
  if (UseParNewGC) {
    workers()->print_worker_threads_on(st);
  }
  if (UseConcMarkSweepGC) {
    ConcurrentMarkSweepThread::print_all_on(st);
  }
#endif // INCLUDE_ALL_GCS
}

void GenCollectedHeap::print_on_error(outputStream* st) const {
  this->CollectedHeap::print_on_error(st);

#if INCLUDE_ALL_GCS
  if (UseConcMarkSweepGC) {
    st->cr();
    CMSCollector::print_on_error(st);
  }
#endif // INCLUDE_ALL_GCS
}

void GenCollectedHeap::print_tracing_info() const {
  if (TraceGen0Time) {
    get_gen(0)->print_summary_info();
  }
  if (TraceGen1Time) {
    get_gen(1)->print_summary_info();
  }
}

void GenCollectedHeap::print_heap_change(size_t prev_used) const {
  if (PrintGCDetails && Verbose) {
    gclog_or_tty->print(" "  SIZE_FORMAT
                        "->" SIZE_FORMAT
                        "("  SIZE_FORMAT ")",
                        prev_used, used(), capacity());
  } else {
    gclog_or_tty->print(" "  SIZE_FORMAT "K"
                        "->" SIZE_FORMAT "K"
                        "("  SIZE_FORMAT "K)",
                        prev_used / K, used() / K, capacity() / K);
  }
}

class GenGCPrologueClosure: public GenCollectedHeap::GenClosure {
 private:
  bool _full;
 public:
  void do_generation(Generation* gen) {
    gen->gc_prologue(_full);
  }
  GenGCPrologueClosure(bool full) : _full(full) {};
};

void GenCollectedHeap::gc_prologue(bool full) {
  assert(InlineCacheBuffer::is_empty(), "should have cleaned up ICBuffer");

  always_do_update_barrier = false;
  // Fill TLAB's and such
  CollectedHeap::accumulate_statistics_all_tlabs();
  ensure_parsability(true);   // retire TLABs

  // Walk generations
  GenGCPrologueClosure blk(full);
  generation_iterate(&blk, false);  // not old-to-young.
};

class GenGCEpilogueClosure: public GenCollectedHeap::GenClosure {
 private:
  bool _full;
 public:
  void do_generation(Generation* gen) {
    gen->gc_epilogue(_full);
  }
  GenGCEpilogueClosure(bool full) : _full(full) {};
};

void GenCollectedHeap::gc_epilogue(bool full) {
#ifdef COMPILER2
  assert(DerivedPointerTable::is_empty(), "derived pointer present");
  size_t actual_gap = pointer_delta((HeapWord*) (max_uintx-3), *(end_addr()));
  guarantee(actual_gap > (size_t)FastAllocateSizeLimit, "inline allocation wraps");
#endif /* COMPILER2 */

  resize_all_tlabs();

  GenGCEpilogueClosure blk(full);
  generation_iterate(&blk, false);  // not old-to-young.

  if (!CleanChunkPoolAsync) {
    Chunk::clean_chunk_pool();
  }

  MetaspaceCounters::update_performance_counters();
  CompressedClassSpaceCounters::update_performance_counters();

  always_do_update_barrier = UseConcMarkSweepGC;
};

#ifndef PRODUCT
class GenGCSaveTopsBeforeGCClosure: public GenCollectedHeap::GenClosure {
 private:
 public:
  void do_generation(Generation* gen) {
    gen->record_spaces_top();
  }
};

void GenCollectedHeap::record_gen_tops_before_GC() {
  if (ZapUnusedHeapArea) {
    GenGCSaveTopsBeforeGCClosure blk;
    generation_iterate(&blk, false);  // not old-to-young.
  }
}
#endif  // not PRODUCT

class GenEnsureParsabilityClosure: public GenCollectedHeap::GenClosure {
 public:
  void do_generation(Generation* gen) {
    gen->ensure_parsability();
  }
};

void GenCollectedHeap::ensure_parsability(bool retire_tlabs) {
  CollectedHeap::ensure_parsability(retire_tlabs);
  GenEnsureParsabilityClosure ep_cl;
  generation_iterate(&ep_cl, false);
}

oop GenCollectedHeap::handle_failed_promotion(Generation* old_gen,
                                              oop obj,
                                              size_t obj_size) {
  guarantee(old_gen->level() == 1, "We only get here with an old generation");
  assert(obj_size == (size_t)obj->size(), "bad obj_size passed in");
  HeapWord* result = NULL;

  result = old_gen->expand_and_allocate(obj_size, false);

  if (result != NULL) {
    Copy::aligned_disjoint_words((HeapWord*)obj, result, obj_size);
  }
  return oop(result);
}

class GenTimeOfLastGCClosure: public GenCollectedHeap::GenClosure {
  jlong _time;   // in ms
  jlong _now;    // in ms

 public:
  GenTimeOfLastGCClosure(jlong now) : _time(now), _now(now) { }

  jlong time() { return _time; }

  void do_generation(Generation* gen) {
    _time = MIN2(_time, gen->time_of_last_gc(_now));
  }
};

jlong GenCollectedHeap::millis_since_last_gc() {
  // We need a monotonically non-deccreasing time in ms but
  // os::javaTimeMillis() does not guarantee monotonicity.
  jlong now = os::javaTimeNanos() / NANOSECS_PER_MILLISEC;
  GenTimeOfLastGCClosure tolgc_cl(now);
  // iterate over generations getting the oldest
  // time that a generation was collected
  generation_iterate(&tolgc_cl, false);

  // javaTimeNanos() is guaranteed to be monotonically non-decreasing
  // provided the underlying platform provides such a time source
  // (and it is bug free). So we still have to guard against getting
  // back a time later than 'now'.
  jlong retVal = now - tolgc_cl.time();
  if (retVal < 0) {
    NOT_PRODUCT(warning("time warp: "INT64_FORMAT, retVal);)
    return 0;
  }
  return retVal;
}
