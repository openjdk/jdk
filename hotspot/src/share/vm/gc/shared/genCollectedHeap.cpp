/*
 * Copyright (c) 2000, 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "code/codeCache.hpp"
#include "code/icBuffer.hpp"
#include "gc/shared/collectedHeap.inline.hpp"
#include "gc/shared/collectorCounters.hpp"
#include "gc/shared/gcId.hpp"
#include "gc/shared/gcLocker.inline.hpp"
#include "gc/shared/gcTrace.hpp"
#include "gc/shared/gcTraceTime.inline.hpp"
#include "gc/shared/genCollectedHeap.hpp"
#include "gc/shared/genOopClosures.inline.hpp"
#include "gc/shared/generationSpec.hpp"
#include "gc/shared/space.hpp"
#include "gc/shared/strongRootsScope.hpp"
#include "gc/shared/vmGCOperations.hpp"
#include "gc/shared/workgroup.hpp"
#include "memory/filemap.hpp"
#include "memory/resourceArea.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/biasedLocking.hpp"
#include "runtime/fprofiler.hpp"
#include "runtime/handles.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/java.hpp"
#include "runtime/vmThread.hpp"
#include "services/management.hpp"
#include "services/memoryService.hpp"
#include "utilities/macros.hpp"
#include "utilities/stack.inline.hpp"
#include "utilities/vmError.hpp"
#if INCLUDE_ALL_GCS
#include "gc/cms/concurrentMarkSweepThread.hpp"
#include "gc/cms/vmCMSOperations.hpp"
#endif // INCLUDE_ALL_GCS

NOT_PRODUCT(size_t GenCollectedHeap::_skip_header_HeapWords = 0;)

// The set of potentially parallel tasks in root scanning.
enum GCH_strong_roots_tasks {
  GCH_PS_Universe_oops_do,
  GCH_PS_JNIHandles_oops_do,
  GCH_PS_ObjectSynchronizer_oops_do,
  GCH_PS_FlatProfiler_oops_do,
  GCH_PS_Management_oops_do,
  GCH_PS_SystemDictionary_oops_do,
  GCH_PS_ClassLoaderDataGraph_oops_do,
  GCH_PS_jvmti_oops_do,
  GCH_PS_CodeCache_oops_do,
  GCH_PS_younger_gens,
  // Leave this one last.
  GCH_PS_NumElements
};

GenCollectedHeap::GenCollectedHeap(GenCollectorPolicy *policy) :
  CollectedHeap(),
  _rem_set(NULL),
  _gen_policy(policy),
  _process_strong_tasks(new SubTasksDone(GCH_PS_NumElements)),
  _full_collections_completed(0)
{
  assert(policy != NULL, "Sanity check");
  if (UseConcMarkSweepGC) {
    _workers = new WorkGang("GC Thread", ParallelGCThreads,
                            /* are_GC_task_threads */true,
                            /* are_ConcurrentGC_threads */false);
    _workers->initialize_workers();
  } else {
    // Serial GC does not use workers.
    _workers = NULL;
  }
}

jint GenCollectedHeap::initialize() {
  CollectedHeap::pre_initialize();

  // While there are no constraints in the GC code that HeapWordSize
  // be any particular value, there are multiple other areas in the
  // system which believe this to be true (e.g. oop->object_size in some
  // cases incorrectly returns the size in wordSize units rather than
  // HeapWordSize).
  guarantee(HeapWordSize == wordSize, "HeapWordSize must equal wordSize");

  // Allocate space for the heap.

  char* heap_address;
  ReservedSpace heap_rs;

  size_t heap_alignment = collector_policy()->heap_alignment();

  heap_address = allocate(heap_alignment, &heap_rs);

  if (!heap_rs.is_reserved()) {
    vm_shutdown_during_initialization(
      "Could not reserve enough space for object heap");
    return JNI_ENOMEM;
  }

  initialize_reserved_region((HeapWord*)heap_rs.base(), (HeapWord*)(heap_rs.base() + heap_rs.size()));

  _rem_set = collector_policy()->create_rem_set(reserved_region());
  set_barrier_set(rem_set()->bs());

  ReservedSpace young_rs = heap_rs.first_part(gen_policy()->young_gen_spec()->max_size(), false, false);
  _young_gen = gen_policy()->young_gen_spec()->init(young_rs, rem_set());
  heap_rs = heap_rs.last_part(gen_policy()->young_gen_spec()->max_size());

  ReservedSpace old_rs = heap_rs.first_part(gen_policy()->old_gen_spec()->max_size(), false, false);
  _old_gen = gen_policy()->old_gen_spec()->init(old_rs, rem_set());
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
                                 ReservedSpace* heap_rs){
  // Now figure out the total size.
  const size_t pageSize = UseLargePages ? os::large_page_size() : os::vm_page_size();
  assert(alignment % pageSize == 0, "Must be");

  GenerationSpec* young_spec = gen_policy()->young_gen_spec();
  GenerationSpec* old_spec = gen_policy()->old_gen_spec();

  // Check for overflow.
  size_t total_reserved = young_spec->max_size() + old_spec->max_size();
  if (total_reserved < young_spec->max_size()) {
    vm_exit_during_initialization("The size of the object heap + VM data exceeds "
                                  "the maximum representable size");
  }
  assert(total_reserved % alignment == 0,
         "Gen size; total_reserved=" SIZE_FORMAT ", alignment="
         SIZE_FORMAT, total_reserved, alignment);

  *heap_rs = Universe::reserve_heap(total_reserved, alignment);
  return heap_rs->base();
}

void GenCollectedHeap::post_initialize() {
  CollectedHeap::post_initialize();
  ref_processing_init();
  assert((_young_gen->kind() == Generation::DefNew) ||
         (_young_gen->kind() == Generation::ParNew),
    "Wrong youngest generation type");
  DefNewGeneration* def_new_gen = (DefNewGeneration*)_young_gen;

  assert(_old_gen->kind() == Generation::ConcurrentMarkSweep ||
         _old_gen->kind() == Generation::MarkSweepCompact,
    "Wrong generation kind");

  _gen_policy->initialize_size_policy(def_new_gen->eden()->capacity(),
                                      _old_gen->capacity(),
                                      def_new_gen->from()->capacity());
  _gen_policy->initialize_gc_policy_counters();
}

void GenCollectedHeap::ref_processing_init() {
  _young_gen->ref_processor_init();
  _old_gen->ref_processor_init();
}

size_t GenCollectedHeap::capacity() const {
  return _young_gen->capacity() + _old_gen->capacity();
}

size_t GenCollectedHeap::used() const {
  return _young_gen->used() + _old_gen->used();
}

void GenCollectedHeap::save_used_regions() {
  _old_gen->save_used_region();
  _young_gen->save_used_region();
}

size_t GenCollectedHeap::max_capacity() const {
  return _young_gen->max_capacity() + _old_gen->max_capacity();
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
  HeapWord* res = NULL;

  if (_young_gen->should_allocate(size, is_tlab)) {
    res = _young_gen->allocate(size, is_tlab);
    if (res != NULL || first_only) {
      return res;
    }
  }

  if (_old_gen->should_allocate(size, is_tlab)) {
    res = _old_gen->allocate(size, is_tlab);
  }

  return res;
}

HeapWord* GenCollectedHeap::mem_allocate(size_t size,
                                         bool* gc_overhead_limit_was_exceeded) {
  return gen_policy()->mem_allocate_work(size,
                                         false /* is_tlab */,
                                         gc_overhead_limit_was_exceeded);
}

bool GenCollectedHeap::must_clear_all_soft_refs() {
  return _gc_cause == GCCause::_last_ditch_collection;
}

bool GenCollectedHeap::should_do_concurrent_full_gc(GCCause::Cause cause) {
  if (!UseConcMarkSweepGC) {
    return false;
  }

  switch (cause) {
    case GCCause::_gc_locker:           return GCLockerInvokesConcurrent;
    case GCCause::_java_lang_system_gc:
    case GCCause::_dcmd_gc_run:         return ExplicitGCInvokesConcurrent;
    default:                            return false;
  }
}

void GenCollectedHeap::collect_generation(Generation* gen, bool full, size_t size,
                                          bool is_tlab, bool run_verification, bool clear_soft_refs,
                                          bool restore_marks_for_biased_locking) {
  FormatBuffer<> title("Collect gen: %s", gen->short_name());
  GCTraceTime(Debug, gc) t1(title);
  TraceCollectorStats tcs(gen->counters());
  TraceMemoryManagerStats tmms(gen->kind(),gc_cause());

  gen->stat_record()->invocations++;
  gen->stat_record()->accumulated_time.start();

  // Must be done anew before each collection because
  // a previous collection will do mangling and will
  // change top of some spaces.
  record_gen_tops_before_GC();

  log_trace(gc)("%s invoke=%d size=" SIZE_FORMAT, heap()->is_young_gen(gen) ? "Young" : "Old", gen->stat_record()->invocations, size * HeapWordSize);

  if (run_verification && VerifyBeforeGC) {
    HandleMark hm;  // Discard invalid handles created during verification
    Universe::verify("Before GC");
  }
  COMPILER2_PRESENT(DerivedPointerTable::clear());

  if (restore_marks_for_biased_locking) {
    // We perform this mark word preservation work lazily
    // because it's only at this point that we know whether we
    // absolutely have to do it; we want to avoid doing it for
    // scavenge-only collections where it's unnecessary
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
    ReferenceProcessor* rp = gen->ref_processor();
    // If the discovery of ("weak") refs in this generation is
    // atomic wrt other collectors in this configuration, we
    // are guaranteed to have empty discovered ref lists.
    if (rp->discovery_is_atomic()) {
      rp->enable_discovery();
      rp->setup_policy(clear_soft_refs);
    } else {
      // collect() below will enable discovery as appropriate
    }
    gen->collect(full, clear_soft_refs, size, is_tlab);
    if (!rp->enqueuing_is_done()) {
      rp->enqueue_discovered_references();
    } else {
      rp->set_enqueuing_is_done(false);
    }
    rp->verify_no_references_recorded();
  }

  COMPILER2_PRESENT(DerivedPointerTable::update_pointers());

  gen->stat_record()->accumulated_time.stop();

  update_gc_stats(gen, full);

  if (run_verification && VerifyAfterGC) {
    HandleMark hm;  // Discard invalid handles created during verification
    Universe::verify("After GC");
  }
}

void GenCollectedHeap::do_collection(bool           full,
                                     bool           clear_all_soft_refs,
                                     size_t         size,
                                     bool           is_tlab,
                                     GenerationType max_generation) {
  ResourceMark rm;
  DEBUG_ONLY(Thread* my_thread = Thread::current();)

  assert(SafepointSynchronize::is_at_safepoint(), "should be at safepoint");
  assert(my_thread->is_VM_thread() ||
         my_thread->is_ConcurrentGC_thread(),
         "incorrect thread type capability");
  assert(Heap_lock->is_locked(),
         "the requesting thread should have the Heap_lock");
  guarantee(!is_gc_active(), "collection is not reentrant");

  if (GCLocker::check_active_before_gc()) {
    return; // GC is disabled (e.g. JNI GetXXXCritical operation)
  }

  GCIdMarkAndRestore gc_id_mark;

  const bool do_clear_all_soft_refs = clear_all_soft_refs ||
                          collector_policy()->should_clear_all_soft_refs();

  ClearedAllSoftRefs casr(do_clear_all_soft_refs, collector_policy());

  const size_t metadata_prev_used = MetaspaceAux::used_bytes();

  print_heap_before_gc();

  {
    FlagSetting fl(_is_gc_active, true);

    bool complete = full && (max_generation == OldGen);
    bool old_collects_young = complete && !ScavengeBeforeFullGC;
    bool do_young_collection = !old_collects_young && _young_gen->should_collect(full, size, is_tlab);

    FormatBuffer<> gc_string("%s", "Pause ");
    if (do_young_collection) {
      gc_string.append("Young");
    } else {
      gc_string.append("Full");
    }

    GCTraceCPUTime tcpu;
    GCTraceTime(Info, gc) t(gc_string, NULL, gc_cause(), true);

    gc_prologue(complete);
    increment_total_collections(complete);

    size_t young_prev_used = _young_gen->used();
    size_t old_prev_used = _old_gen->used();

    bool run_verification = total_collections() >= VerifyGCStartAt;

    bool prepared_for_verification = false;
    bool collected_old = false;

    if (do_young_collection) {
      if (run_verification && VerifyGCLevel <= 0 && VerifyBeforeGC) {
        prepare_for_verify();
        prepared_for_verification = true;
      }

      collect_generation(_young_gen,
                         full,
                         size,
                         is_tlab,
                         run_verification && VerifyGCLevel <= 0,
                         do_clear_all_soft_refs,
                         false);

      if (size > 0 && (!is_tlab || _young_gen->supports_tlab_allocation()) &&
          size * HeapWordSize <= _young_gen->unsafe_max_alloc_nogc()) {
        // Allocation request was met by young GC.
        size = 0;
      }
    }

    bool must_restore_marks_for_biased_locking = false;

    if (max_generation == OldGen && _old_gen->should_collect(full, size, is_tlab)) {
      if (!complete) {
        // The full_collections increment was missed above.
        increment_total_full_collections();
      }

      if (!prepared_for_verification && run_verification &&
          VerifyGCLevel <= 1 && VerifyBeforeGC) {
        prepare_for_verify();
      }

      if (do_young_collection) {
        // We did a young GC. Need a new GC id for the old GC.
        GCIdMarkAndRestore gc_id_mark;
        GCTraceTime(Info, gc) t("Pause Full", NULL, gc_cause(), true);
        collect_generation(_old_gen, full, size, is_tlab, run_verification && VerifyGCLevel <= 1, do_clear_all_soft_refs, true);
      } else {
        // No young GC done. Use the same GC id as was set up earlier in this method.
        collect_generation(_old_gen, full, size, is_tlab, run_verification && VerifyGCLevel <= 1, do_clear_all_soft_refs, true);
      }

      must_restore_marks_for_biased_locking = true;
      collected_old = true;
    }

    // Update "complete" boolean wrt what actually transpired --
    // for instance, a promotion failure could have led to
    // a whole heap collection.
    complete = complete || collected_old;

    print_heap_change(young_prev_used, old_prev_used);
    MetaspaceAux::print_metaspace_change(metadata_prev_used);

    // Adjust generation sizes.
    if (collected_old) {
      _old_gen->compute_new_size();
    }
    _young_gen->compute_new_size();

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

  print_heap_after_gc();

#ifdef TRACESPINNING
  ParallelTaskTerminator::print_termination_counts();
#endif
}

HeapWord* GenCollectedHeap::satisfy_failed_allocation(size_t size, bool is_tlab) {
  return gen_policy()->satisfy_failed_allocation(size, is_tlab);
}

#ifdef ASSERT
class AssertNonScavengableClosure: public OopClosure {
public:
  virtual void do_oop(oop* p) {
    assert(!GenCollectedHeap::heap()->is_in_partial_collection(*p),
      "Referent should not be scavengable.");  }
  virtual void do_oop(narrowOop* p) { ShouldNotReachHere(); }
};
static AssertNonScavengableClosure assert_is_non_scavengable_closure;
#endif

void GenCollectedHeap::process_roots(StrongRootsScope* scope,
                                     ScanningOption so,
                                     OopClosure* strong_roots,
                                     OopClosure* weak_roots,
                                     CLDClosure* strong_cld_closure,
                                     CLDClosure* weak_cld_closure,
                                     CodeBlobToOopClosure* code_roots) {
  // General roots.
  assert(Threads::thread_claim_parity() != 0, "must have called prologue code");
  assert(code_roots != NULL, "code root closure should always be set");
  // _n_termination for _process_strong_tasks should be set up stream
  // in a method not running in a GC worker.  Otherwise the GC worker
  // could be trying to change the termination condition while the task
  // is executing in another GC worker.

  if (!_process_strong_tasks->is_task_claimed(GCH_PS_ClassLoaderDataGraph_oops_do)) {
    ClassLoaderDataGraph::roots_cld_do(strong_cld_closure, weak_cld_closure);
  }

  // Some CLDs contained in the thread frames should be considered strong.
  // Don't process them if they will be processed during the ClassLoaderDataGraph phase.
  CLDClosure* roots_from_clds_p = (strong_cld_closure != weak_cld_closure) ? strong_cld_closure : NULL;
  // Only process code roots from thread stacks if we aren't visiting the entire CodeCache anyway
  CodeBlobToOopClosure* roots_from_code_p = (so & SO_AllCodeCache) ? NULL : code_roots;

  bool is_par = scope->n_threads() > 1;
  Threads::possibly_parallel_oops_do(is_par, strong_roots, roots_from_clds_p, roots_from_code_p);

  if (!_process_strong_tasks->is_task_claimed(GCH_PS_Universe_oops_do)) {
    Universe::oops_do(strong_roots);
  }
  // Global (strong) JNI handles
  if (!_process_strong_tasks->is_task_claimed(GCH_PS_JNIHandles_oops_do)) {
    JNIHandles::oops_do(strong_roots);
  }

  if (!_process_strong_tasks->is_task_claimed(GCH_PS_ObjectSynchronizer_oops_do)) {
    ObjectSynchronizer::oops_do(strong_roots);
  }
  if (!_process_strong_tasks->is_task_claimed(GCH_PS_FlatProfiler_oops_do)) {
    FlatProfiler::oops_do(strong_roots);
  }
  if (!_process_strong_tasks->is_task_claimed(GCH_PS_Management_oops_do)) {
    Management::oops_do(strong_roots);
  }
  if (!_process_strong_tasks->is_task_claimed(GCH_PS_jvmti_oops_do)) {
    JvmtiExport::oops_do(strong_roots);
  }

  if (!_process_strong_tasks->is_task_claimed(GCH_PS_SystemDictionary_oops_do)) {
    SystemDictionary::roots_oops_do(strong_roots, weak_roots);
  }

  // All threads execute the following. A specific chunk of buckets
  // from the StringTable are the individual tasks.
  if (weak_roots != NULL) {
    if (is_par) {
      StringTable::possibly_parallel_oops_do(weak_roots);
    } else {
      StringTable::oops_do(weak_roots);
    }
  }

  if (!_process_strong_tasks->is_task_claimed(GCH_PS_CodeCache_oops_do)) {
    if (so & SO_ScavengeCodeCache) {
      assert(code_roots != NULL, "must supply closure for code cache");

      // We only visit parts of the CodeCache when scavenging.
      CodeCache::scavenge_root_nmethods_do(code_roots);
    }
    if (so & SO_AllCodeCache) {
      assert(code_roots != NULL, "must supply closure for code cache");

      // CMSCollector uses this to do intermediate-strength collections.
      // We scan the entire code cache, since CodeCache::do_unloading is not called.
      CodeCache::blobs_do(code_roots);
    }
    // Verify that the code cache contents are not subject to
    // movement by a scavenging collection.
    DEBUG_ONLY(CodeBlobToOopClosure assert_code_is_non_scavengable(&assert_is_non_scavengable_closure, !CodeBlobToOopClosure::FixRelocations));
    DEBUG_ONLY(CodeCache::asserted_non_scavengable_nmethods_do(&assert_code_is_non_scavengable));
  }
}

void GenCollectedHeap::gen_process_roots(StrongRootsScope* scope,
                                         GenerationType type,
                                         bool young_gen_as_roots,
                                         ScanningOption so,
                                         bool only_strong_roots,
                                         OopsInGenClosure* not_older_gens,
                                         OopsInGenClosure* older_gens,
                                         CLDClosure* cld_closure) {
  const bool is_adjust_phase = !only_strong_roots && !young_gen_as_roots;

  bool is_moving_collection = false;
  if (type == YoungGen || is_adjust_phase) {
    // young collections are always moving
    is_moving_collection = true;
  }

  MarkingCodeBlobClosure mark_code_closure(not_older_gens, is_moving_collection);
  OopsInGenClosure* weak_roots = only_strong_roots ? NULL : not_older_gens;
  CLDClosure* weak_cld_closure = only_strong_roots ? NULL : cld_closure;

  process_roots(scope, so,
                not_older_gens, weak_roots,
                cld_closure, weak_cld_closure,
                &mark_code_closure);

  if (young_gen_as_roots) {
    if (!_process_strong_tasks->is_task_claimed(GCH_PS_younger_gens)) {
      if (type == OldGen) {
        not_older_gens->set_generation(_young_gen);
        _young_gen->oop_iterate(not_older_gens);
      }
      not_older_gens->reset_generation();
    }
  }
  // When collection is parallel, all threads get to cooperate to do
  // old generation scanning.
  if (type == YoungGen) {
    older_gens->set_generation(_old_gen);
    rem_set()->younger_refs_iterate(_old_gen, older_gens, scope->n_threads());
    older_gens->reset_generation();
  }

  _process_strong_tasks->all_tasks_completed(scope->n_threads());
}


class AlwaysTrueClosure: public BoolObjectClosure {
public:
  bool do_object_b(oop p) { return true; }
};
static AlwaysTrueClosure always_true;

void GenCollectedHeap::gen_process_weak_roots(OopClosure* root_closure) {
  JNIHandles::weak_oops_do(&always_true, root_closure);
  _young_gen->ref_processor()->weak_oops_do(root_closure);
  _old_gen->ref_processor()->weak_oops_do(root_closure);
}

#define GCH_SINCE_SAVE_MARKS_ITERATE_DEFN(OopClosureType, nv_suffix)    \
void GenCollectedHeap::                                                 \
oop_since_save_marks_iterate(GenerationType gen,                        \
                             OopClosureType* cur,                       \
                             OopClosureType* older) {                   \
  if (gen == YoungGen) {                              \
    _young_gen->oop_since_save_marks_iterate##nv_suffix(cur);           \
    _old_gen->oop_since_save_marks_iterate##nv_suffix(older);           \
  } else {                                                              \
    _old_gen->oop_since_save_marks_iterate##nv_suffix(cur);             \
  }                                                                     \
}

ALL_SINCE_SAVE_MARKS_CLOSURES(GCH_SINCE_SAVE_MARKS_ITERATE_DEFN)

#undef GCH_SINCE_SAVE_MARKS_ITERATE_DEFN

bool GenCollectedHeap::no_allocs_since_save_marks() {
  return _young_gen->no_allocs_since_save_marks() &&
         _old_gen->no_allocs_since_save_marks();
}

bool GenCollectedHeap::supports_inline_contig_alloc() const {
  return _young_gen->supports_inline_contig_alloc();
}

HeapWord** GenCollectedHeap::top_addr() const {
  return _young_gen->top_addr();
}

HeapWord** GenCollectedHeap::end_addr() const {
  return _young_gen->end_addr();
}

// public collection interfaces

void GenCollectedHeap::collect(GCCause::Cause cause) {
  if (should_do_concurrent_full_gc(cause)) {
#if INCLUDE_ALL_GCS
    // Mostly concurrent full collection.
    collect_mostly_concurrent(cause);
#else  // INCLUDE_ALL_GCS
    ShouldNotReachHere();
#endif // INCLUDE_ALL_GCS
  } else if (cause == GCCause::_wb_young_gc) {
    // Young collection for the WhiteBox API.
    collect(cause, YoungGen);
  } else {
#ifdef ASSERT
  if (cause == GCCause::_scavenge_alot) {
    // Young collection only.
    collect(cause, YoungGen);
  } else {
    // Stop-the-world full collection.
    collect(cause, OldGen);
  }
#else
    // Stop-the-world full collection.
    collect(cause, OldGen);
#endif
  }
}

void GenCollectedHeap::collect(GCCause::Cause cause, GenerationType max_generation) {
  // The caller doesn't have the Heap_lock
  assert(!Heap_lock->owned_by_self(), "this thread should not own the Heap_lock");
  MutexLocker ml(Heap_lock);
  collect_locked(cause, max_generation);
}

void GenCollectedHeap::collect_locked(GCCause::Cause cause) {
  // The caller has the Heap_lock
  assert(Heap_lock->owned_by_self(), "this thread should own the Heap_lock");
  collect_locked(cause, OldGen);
}

// this is the private collection interface
// The Heap_lock is expected to be held on entry.

void GenCollectedHeap::collect_locked(GCCause::Cause cause, GenerationType max_generation) {
  // Read the GC count while holding the Heap_lock
  unsigned int gc_count_before      = total_collections();
  unsigned int full_gc_count_before = total_full_collections();
  {
    MutexUnlocker mu(Heap_lock);  // give up heap lock, execute gets it back
    VM_GenCollectFull op(gc_count_before, full_gc_count_before,
                         cause, max_generation);
    VMThread::execute(&op);
  }
}

#if INCLUDE_ALL_GCS
bool GenCollectedHeap::create_cms_collector() {

  assert(_old_gen->kind() == Generation::ConcurrentMarkSweep,
         "Unexpected generation kinds");
  // Skip two header words in the block content verification
  NOT_PRODUCT(_skip_header_HeapWords = CMSCollector::skip_header_HeapWords();)
  assert(_gen_policy->is_concurrent_mark_sweep_policy(), "Unexpected policy type");
  CMSCollector* collector =
    new CMSCollector((ConcurrentMarkSweepGeneration*)_old_gen,
                     _rem_set,
                     _gen_policy->as_concurrent_mark_sweep_policy());

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
   do_full_collection(clear_all_soft_refs, OldGen);
}

void GenCollectedHeap::do_full_collection(bool clear_all_soft_refs,
                                          GenerationType last_generation) {
  GenerationType local_last_generation;
  if (!incremental_collection_will_fail(false /* don't consult_young */) &&
      gc_cause() == GCCause::_gc_locker) {
    local_last_generation = YoungGen;
  } else {
    local_last_generation = last_generation;
  }

  do_collection(true,                   // full
                clear_all_soft_refs,    // clear_all_soft_refs
                0,                      // size
                false,                  // is_tlab
                local_last_generation); // last_generation
  // Hack XXX FIX ME !!!
  // A scavenge may not have been attempted, or may have
  // been attempted and failed, because the old gen was too full
  if (local_last_generation == YoungGen && gc_cause() == GCCause::_gc_locker &&
      incremental_collection_will_fail(false /* don't consult_young */)) {
    log_debug(gc, jni)("GC locker: Trying a full collection because scavenge failed");
    // This time allow the old gen to be collected as well
    do_collection(true,                // full
                  clear_all_soft_refs, // clear_all_soft_refs
                  0,                   // size
                  false,               // is_tlab
                  OldGen);             // last_generation
  }
}

bool GenCollectedHeap::is_in_young(oop p) {
  bool result = ((HeapWord*)p) < _old_gen->reserved().start();
  assert(result == _young_gen->is_in_reserved(p),
         "incorrect test - result=%d, p=" INTPTR_FORMAT, result, p2i((void*)p));
  return result;
}

// Returns "TRUE" iff "p" points into the committed areas of the heap.
bool GenCollectedHeap::is_in(const void* p) const {
  return _young_gen->is_in(p) || _old_gen->is_in(p);
}

#ifdef ASSERT
// Don't implement this by using is_in_young().  This method is used
// in some cases to check that is_in_young() is correct.
bool GenCollectedHeap::is_in_partial_collection(const void* p) {
  assert(is_in_reserved(p) || p == NULL,
    "Does not work if address is non-null and outside of the heap");
  return p < _young_gen->reserved().end() && p != NULL;
}
#endif

void GenCollectedHeap::oop_iterate_no_header(OopClosure* cl) {
  NoHeaderExtendedOopClosure no_header_cl(cl);
  oop_iterate(&no_header_cl);
}

void GenCollectedHeap::oop_iterate(ExtendedOopClosure* cl) {
  _young_gen->oop_iterate(cl);
  _old_gen->oop_iterate(cl);
}

void GenCollectedHeap::object_iterate(ObjectClosure* cl) {
  _young_gen->object_iterate(cl);
  _old_gen->object_iterate(cl);
}

void GenCollectedHeap::safe_object_iterate(ObjectClosure* cl) {
  _young_gen->safe_object_iterate(cl);
  _old_gen->safe_object_iterate(cl);
}

Space* GenCollectedHeap::space_containing(const void* addr) const {
  Space* res = _young_gen->space_containing(addr);
  if (res != NULL) {
    return res;
  }
  res = _old_gen->space_containing(addr);
  assert(res != NULL, "Could not find containing space");
  return res;
}

HeapWord* GenCollectedHeap::block_start(const void* addr) const {
  assert(is_in_reserved(addr), "block_start of address outside of heap");
  if (_young_gen->is_in_reserved(addr)) {
    assert(_young_gen->is_in(addr), "addr should be in allocated part of generation");
    return _young_gen->block_start(addr);
  }

  assert(_old_gen->is_in_reserved(addr), "Some generation should contain the address");
  assert(_old_gen->is_in(addr), "addr should be in allocated part of generation");
  return _old_gen->block_start(addr);
}

size_t GenCollectedHeap::block_size(const HeapWord* addr) const {
  assert(is_in_reserved(addr), "block_size of address outside of heap");
  if (_young_gen->is_in_reserved(addr)) {
    assert(_young_gen->is_in(addr), "addr should be in allocated part of generation");
    return _young_gen->block_size(addr);
  }

  assert(_old_gen->is_in_reserved(addr), "Some generation should contain the address");
  assert(_old_gen->is_in(addr), "addr should be in allocated part of generation");
  return _old_gen->block_size(addr);
}

bool GenCollectedHeap::block_is_obj(const HeapWord* addr) const {
  assert(is_in_reserved(addr), "block_is_obj of address outside of heap");
  assert(block_start(addr) == addr, "addr must be a block start");
  if (_young_gen->is_in_reserved(addr)) {
    return _young_gen->block_is_obj(addr);
  }

  assert(_old_gen->is_in_reserved(addr), "Some generation should contain the address");
  return _old_gen->block_is_obj(addr);
}

bool GenCollectedHeap::supports_tlab_allocation() const {
  assert(!_old_gen->supports_tlab_allocation(), "Old gen supports TLAB allocation?!");
  return _young_gen->supports_tlab_allocation();
}

size_t GenCollectedHeap::tlab_capacity(Thread* thr) const {
  assert(!_old_gen->supports_tlab_allocation(), "Old gen supports TLAB allocation?!");
  if (_young_gen->supports_tlab_allocation()) {
    return _young_gen->tlab_capacity();
  }
  return 0;
}

size_t GenCollectedHeap::tlab_used(Thread* thr) const {
  assert(!_old_gen->supports_tlab_allocation(), "Old gen supports TLAB allocation?!");
  if (_young_gen->supports_tlab_allocation()) {
    return _young_gen->tlab_used();
  }
  return 0;
}

size_t GenCollectedHeap::unsafe_max_tlab_alloc(Thread* thr) const {
  assert(!_old_gen->supports_tlab_allocation(), "Old gen supports TLAB allocation?!");
  if (_young_gen->supports_tlab_allocation()) {
    return _young_gen->unsafe_max_tlab_alloc();
  }
  return 0;
}

HeapWord* GenCollectedHeap::allocate_new_tlab(size_t size) {
  bool gc_overhead_limit_was_exceeded;
  return gen_policy()->mem_allocate_work(size /* size */,
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
  _young_gen->contribute_scratch(res, requestor, max_alloc_words);
  _old_gen->contribute_scratch(res, requestor, max_alloc_words);
  sort_scratch_list(res);
  return res;
}

void GenCollectedHeap::release_scratch() {
  _young_gen->reset_scratch();
  _old_gen->reset_scratch();
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
    cl->do_generation(_old_gen);
    cl->do_generation(_young_gen);
  } else {
    cl->do_generation(_young_gen);
    cl->do_generation(_old_gen);
  }
}

bool GenCollectedHeap::is_maximal_no_gc() const {
  return _young_gen->is_maximal_no_gc() && _old_gen->is_maximal_no_gc();
}

void GenCollectedHeap::save_marks() {
  _young_gen->save_marks();
  _old_gen->save_marks();
}

GenCollectedHeap* GenCollectedHeap::heap() {
  CollectedHeap* heap = Universe::heap();
  assert(heap != NULL, "Uninitialized access to GenCollectedHeap::heap()");
  assert(heap->kind() == CollectedHeap::GenCollectedHeap, "Not a GenCollectedHeap");
  return (GenCollectedHeap*)heap;
}

void GenCollectedHeap::prepare_for_compaction() {
  // Start by compacting into same gen.
  CompactPoint cp(_old_gen);
  _old_gen->prepare_for_compaction(&cp);
  _young_gen->prepare_for_compaction(&cp);
}

void GenCollectedHeap::verify(VerifyOption option /* ignored */) {
  log_debug(gc, verify)("%s", _old_gen->name());
  _old_gen->verify();

  log_debug(gc, verify)("%s", _old_gen->name());
  _young_gen->verify();

  log_debug(gc, verify)("RemSet");
  rem_set()->verify();
}

void GenCollectedHeap::print_on(outputStream* st) const {
  _young_gen->print_on(st);
  _old_gen->print_on(st);
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
  if (UseConcMarkSweepGC) {
    workers()->print_worker_threads_on(st);
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
  if (TraceYoungGenTime) {
    _young_gen->print_summary_info();
  }
  if (TraceOldGenTime) {
    _old_gen->print_summary_info();
  }
}

void GenCollectedHeap::print_heap_change(size_t young_prev_used, size_t old_prev_used) const {
  log_info(gc, heap)("%s: " SIZE_FORMAT "K->" SIZE_FORMAT "K("  SIZE_FORMAT "K)",
                     _young_gen->short_name(), young_prev_used / K, _young_gen->used() /K, _young_gen->capacity() /K);
  log_info(gc, heap)("%s: " SIZE_FORMAT "K->" SIZE_FORMAT "K("  SIZE_FORMAT "K)",
                     _old_gen->short_name(), old_prev_used / K, _old_gen->used() /K, _old_gen->capacity() /K);
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
#if defined(COMPILER2) || INCLUDE_JVMCI
  assert(DerivedPointerTable::is_empty(), "derived pointer present");
  size_t actual_gap = pointer_delta((HeapWord*) (max_uintx-3), *(end_addr()));
  guarantee(actual_gap > (size_t)FastAllocateSizeLimit, "inline allocation wraps");
#endif /* COMPILER2 || INCLUDE_JVMCI */

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
  guarantee(old_gen == _old_gen, "We only get here with an old generation");
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
  // We need a monotonically non-decreasing time in ms but
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
    NOT_PRODUCT(log_warning(gc)("time warp: " JLONG_FORMAT, retVal);)
    return 0;
  }
  return retVal;
}

void GenCollectedHeap::stop() {
#if INCLUDE_ALL_GCS
  if (UseConcMarkSweepGC) {
    ConcurrentMarkSweepThread::stop();
  }
#endif
}
