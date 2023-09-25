/*
 * Copyright (c) 2000, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/classLoaderDataGraph.hpp"
#include "classfile/stringTable.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/vmSymbols.hpp"
#include "code/codeCache.hpp"
#include "code/icBuffer.hpp"
#include "compiler/oopMap.hpp"
#include "gc/serial/cardTableRS.hpp"
#include "gc/serial/defNewGeneration.hpp"
#include "gc/serial/genMarkSweep.hpp"
#include "gc/serial/markSweep.hpp"
#include "gc/shared/adaptiveSizePolicy.hpp"
#include "gc/shared/cardTableBarrierSet.hpp"
#include "gc/shared/collectedHeap.inline.hpp"
#include "gc/shared/collectorCounters.hpp"
#include "gc/shared/continuationGCSupport.inline.hpp"
#include "gc/shared/gcId.hpp"
#include "gc/shared/gcInitLogger.hpp"
#include "gc/shared/gcLocker.hpp"
#include "gc/shared/gcPolicyCounters.hpp"
#include "gc/shared/gcTrace.hpp"
#include "gc/shared/gcTraceTime.inline.hpp"
#include "gc/shared/gcVMOperations.hpp"
#include "gc/shared/genArguments.hpp"
#include "gc/shared/genCollectedHeap.hpp"
#include "gc/shared/generationSpec.hpp"
#include "gc/shared/locationPrinter.inline.hpp"
#include "gc/shared/oopStorage.inline.hpp"
#include "gc/shared/oopStorageParState.inline.hpp"
#include "gc/shared/oopStorageSet.inline.hpp"
#include "gc/shared/scavengableNMethods.hpp"
#include "gc/shared/space.hpp"
#include "gc/shared/strongRootsScope.hpp"
#include "gc/shared/weakProcessor.hpp"
#include "gc/shared/workerThread.hpp"
#include "memory/iterator.hpp"
#include "memory/metaspaceCounters.hpp"
#include "memory/metaspaceUtils.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/handles.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/java.hpp"
#include "runtime/threads.hpp"
#include "runtime/vmThread.hpp"
#include "services/memoryService.hpp"
#include "utilities/autoRestore.hpp"
#include "utilities/debug.hpp"
#include "utilities/formatBuffer.hpp"
#include "utilities/macros.hpp"
#include "utilities/stack.inline.hpp"
#include "utilities/vmError.hpp"
#if INCLUDE_JVMCI
#include "jvmci/jvmci.hpp"
#endif

GenCollectedHeap::GenCollectedHeap(Generation::Name young,
                                   Generation::Name old,
                                   const char* policy_counters_name) :
  CollectedHeap(),
  _young_gen(nullptr),
  _old_gen(nullptr),
  _young_gen_spec(new GenerationSpec(young,
                                     NewSize,
                                     MaxNewSize,
                                     GenAlignment)),
  _old_gen_spec(new GenerationSpec(old,
                                   OldSize,
                                   MaxOldSize,
                                   GenAlignment)),
  _rem_set(nullptr),
  _soft_ref_policy(),
  _gc_policy_counters(new GCPolicyCounters(policy_counters_name, 2, 2)),
  _incremental_collection_failed(false),
  _full_collections_completed(0),
  _young_manager(nullptr),
  _old_manager(nullptr) {
}

jint GenCollectedHeap::initialize() {
  // Allocate space for the heap.

  ReservedHeapSpace heap_rs = allocate(HeapAlignment);

  if (!heap_rs.is_reserved()) {
    vm_shutdown_during_initialization(
      "Could not reserve enough space for object heap");
    return JNI_ENOMEM;
  }

  initialize_reserved_region(heap_rs);

  ReservedSpace young_rs = heap_rs.first_part(_young_gen_spec->max_size());
  ReservedSpace old_rs = heap_rs.last_part(_young_gen_spec->max_size());

  _rem_set = create_rem_set(heap_rs.region());
  _rem_set->initialize(young_rs.base(), old_rs.base());

  CardTableBarrierSet *bs = new CardTableBarrierSet(_rem_set);
  bs->initialize();
  BarrierSet::set_barrier_set(bs);

  _young_gen = _young_gen_spec->init(young_rs, rem_set());
  _old_gen = _old_gen_spec->init(old_rs, rem_set());

  GCInitLogger::print();

  return JNI_OK;
}

CardTableRS* GenCollectedHeap::create_rem_set(const MemRegion& reserved_region) {
  return new CardTableRS(reserved_region);
}

ReservedHeapSpace GenCollectedHeap::allocate(size_t alignment) {
  // Now figure out the total size.
  const size_t pageSize = UseLargePages ? os::large_page_size() : os::vm_page_size();
  assert(alignment % pageSize == 0, "Must be");

  // Check for overflow.
  size_t total_reserved = _young_gen_spec->max_size() + _old_gen_spec->max_size();
  if (total_reserved < _young_gen_spec->max_size()) {
    vm_exit_during_initialization("The size of the object heap + VM data exceeds "
                                  "the maximum representable size");
  }
  assert(total_reserved % alignment == 0,
         "Gen size; total_reserved=" SIZE_FORMAT ", alignment="
         SIZE_FORMAT, total_reserved, alignment);

  ReservedHeapSpace heap_rs = Universe::reserve_heap(total_reserved, alignment);
  size_t used_page_size = heap_rs.page_size();

  os::trace_page_sizes("Heap",
                       MinHeapSize,
                       total_reserved,
                       heap_rs.base(),
                       heap_rs.size(),
                       used_page_size);

  return heap_rs;
}

class GenIsScavengable : public BoolObjectClosure {
public:
  bool do_object_b(oop obj) {
    return GenCollectedHeap::heap()->is_in_young(obj);
  }
};

static GenIsScavengable _is_scavengable;

void GenCollectedHeap::post_initialize() {
  CollectedHeap::post_initialize();

  DefNewGeneration* def_new_gen = (DefNewGeneration*)_young_gen;

  def_new_gen->ref_processor_init();

  MarkSweep::initialize();

  ScavengableNMethods::initialize(&_is_scavengable);
}

PreGenGCValues GenCollectedHeap::get_pre_gc_values() const {
  const DefNewGeneration* const def_new_gen = (DefNewGeneration*) young_gen();

  return PreGenGCValues(def_new_gen->used(),
                        def_new_gen->capacity(),
                        def_new_gen->eden()->used(),
                        def_new_gen->eden()->capacity(),
                        def_new_gen->from()->used(),
                        def_new_gen->from()->capacity(),
                        old_gen()->used(),
                        old_gen()->capacity());
}

GenerationSpec* GenCollectedHeap::young_gen_spec() const {
  return _young_gen_spec;
}

GenerationSpec* GenCollectedHeap::old_gen_spec() const {
  return _old_gen_spec;
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
  assert(_full_collections_completed <= _total_full_collections,
         "Can't complete more collections than were started");
  _full_collections_completed = _total_full_collections;
  return _full_collections_completed;
}

// Return true if any of the following is true:
// . the allocation won't fit into the current young gen heap
// . gc locker is occupied (jni critical section)
// . heap memory is tight -- the most recent previous collection
//   was a full collection because a partial collection (would
//   have) failed and is likely to fail again
bool GenCollectedHeap::should_try_older_generation_allocation(size_t word_size) const {
  size_t young_capacity = _young_gen->capacity_before_gc();
  return    (word_size > heap_word_size(young_capacity))
         || GCLocker::is_active_and_needs_gc()
         || incremental_collection_failed();
}

HeapWord* GenCollectedHeap::expand_heap_and_allocate(size_t size, bool   is_tlab) {
  HeapWord* result = nullptr;
  if (_old_gen->should_allocate(size, is_tlab)) {
    result = _old_gen->expand_and_allocate(size, is_tlab);
  }
  if (result == nullptr) {
    if (_young_gen->should_allocate(size, is_tlab)) {
      result = _young_gen->expand_and_allocate(size, is_tlab);
    }
  }
  assert(result == nullptr || is_in_reserved(result), "result not in heap");
  return result;
}

HeapWord* GenCollectedHeap::mem_allocate_work(size_t size,
                                              bool is_tlab) {

  HeapWord* result = nullptr;

  // Loop until the allocation is satisfied, or unsatisfied after GC.
  for (uint try_count = 1, gclocker_stalled_count = 0; /* return or throw */; try_count += 1) {

    // First allocation attempt is lock-free.
    Generation *young = _young_gen;
    if (young->should_allocate(size, is_tlab)) {
      result = young->par_allocate(size, is_tlab);
      if (result != nullptr) {
        assert(is_in_reserved(result), "result not in heap");
        return result;
      }
    }
    uint gc_count_before;  // Read inside the Heap_lock locked region.
    {
      MutexLocker ml(Heap_lock);
      log_trace(gc, alloc)("GenCollectedHeap::mem_allocate_work: attempting locked slow path allocation");
      // Note that only large objects get a shot at being
      // allocated in later generations.
      bool first_only = !should_try_older_generation_allocation(size);

      result = attempt_allocation(size, is_tlab, first_only);
      if (result != nullptr) {
        assert(is_in_reserved(result), "result not in heap");
        return result;
      }

      if (GCLocker::is_active_and_needs_gc()) {
        if (is_tlab) {
          return nullptr;  // Caller will retry allocating individual object.
        }
        if (!is_maximal_no_gc()) {
          // Try and expand heap to satisfy request.
          result = expand_heap_and_allocate(size, is_tlab);
          // Result could be null if we are out of space.
          if (result != nullptr) {
            return result;
          }
        }

        if (gclocker_stalled_count > GCLockerRetryAllocationCount) {
          return nullptr; // We didn't get to do a GC and we didn't get any memory.
        }

        // If this thread is not in a jni critical section, we stall
        // the requestor until the critical section has cleared and
        // GC allowed. When the critical section clears, a GC is
        // initiated by the last thread exiting the critical section; so
        // we retry the allocation sequence from the beginning of the loop,
        // rather than causing more, now probably unnecessary, GC attempts.
        JavaThread* jthr = JavaThread::current();
        if (!jthr->in_critical()) {
          MutexUnlocker mul(Heap_lock);
          // Wait for JNI critical section to be exited
          GCLocker::stall_until_clear();
          gclocker_stalled_count += 1;
          continue;
        } else {
          if (CheckJNICalls) {
            fatal("Possible deadlock due to allocating while"
                  " in jni critical section");
          }
          return nullptr;
        }
      }

      // Read the gc count while the heap lock is held.
      gc_count_before = total_collections();
    }

    VM_GenCollectForAllocation op(size, is_tlab, gc_count_before);
    VMThread::execute(&op);
    if (op.prologue_succeeded()) {
      result = op.result();
      if (op.gc_locked()) {
         assert(result == nullptr, "must be null if gc_locked() is true");
         continue;  // Retry and/or stall as necessary.
      }

      assert(result == nullptr || is_in_reserved(result),
             "result not in heap");
      return result;
    }

    // Give a warning if we seem to be looping forever.
    if ((QueuedAllocationWarningCount > 0) &&
        (try_count % QueuedAllocationWarningCount == 0)) {
          log_warning(gc, ergo)("GenCollectedHeap::mem_allocate_work retries %d times,"
                                " size=" SIZE_FORMAT " %s", try_count, size, is_tlab ? "(TLAB)" : "");
    }
  }
}

HeapWord* GenCollectedHeap::attempt_allocation(size_t size,
                                               bool is_tlab,
                                               bool first_only) {
  HeapWord* res = nullptr;

  if (_young_gen->should_allocate(size, is_tlab)) {
    res = _young_gen->allocate(size, is_tlab);
    if (res != nullptr || first_only) {
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
  return mem_allocate_work(size,
                           false /* is_tlab */);
}

bool GenCollectedHeap::must_clear_all_soft_refs() {
  return _gc_cause == GCCause::_metadata_GC_clear_soft_refs ||
         _gc_cause == GCCause::_wb_full_gc;
}

void GenCollectedHeap::collect_generation(Generation* gen, bool full, size_t size,
                                          bool is_tlab, bool run_verification, bool clear_soft_refs) {
  FormatBuffer<> title("Collect gen: %s", gen->short_name());
  GCTraceTime(Trace, gc, phases) t1(title);
  TraceCollectorStats tcs(gen->counters());
  TraceMemoryManagerStats tmms(gen->gc_manager(), gc_cause(), heap()->is_young_gen(gen) ? "end of minor GC" : "end of major GC");

  gen->stat_record()->invocations++;
  gen->stat_record()->accumulated_time.start();

  // Must be done anew before each collection because
  // a previous collection will do mangling and will
  // change top of some spaces.
  record_gen_tops_before_GC();

  log_trace(gc)("%s invoke=%d size=" SIZE_FORMAT, heap()->is_young_gen(gen) ? "Young" : "Old", gen->stat_record()->invocations, size * HeapWordSize);

  if (run_verification && VerifyBeforeGC) {
    Universe::verify("Before GC");
  }
  COMPILER2_OR_JVMCI_PRESENT(DerivedPointerTable::clear());

  // Do collection work
  {
    save_marks();   // save marks for all gens

    gen->collect(full, clear_soft_refs, size, is_tlab);
  }

  COMPILER2_OR_JVMCI_PRESENT(DerivedPointerTable::update_pointers());

  gen->stat_record()->accumulated_time.stop();

  update_gc_stats(gen, full);

  if (run_verification && VerifyAfterGC) {
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
  assert(my_thread->is_VM_thread(), "only VM thread");
  assert(Heap_lock->is_locked(),
         "the requesting thread should have the Heap_lock");
  guarantee(!is_gc_active(), "collection is not reentrant");

  if (GCLocker::check_active_before_gc()) {
    return; // GC is disabled (e.g. JNI GetXXXCritical operation)
  }

  const bool do_clear_all_soft_refs = clear_all_soft_refs ||
                          soft_ref_policy()->should_clear_all_soft_refs();

  ClearedAllSoftRefs casr(do_clear_all_soft_refs, soft_ref_policy());

  AutoModifyRestore<bool> temporarily(_is_gc_active, true);

  bool complete = full && (max_generation == OldGen);
  bool old_collects_young = complete && !ScavengeBeforeFullGC;
  bool do_young_collection = !old_collects_young && _young_gen->should_collect(full, size, is_tlab);

  const PreGenGCValues pre_gc_values = get_pre_gc_values();

  bool run_verification = total_collections() >= VerifyGCStartAt;
  bool prepared_for_verification = false;
  bool do_full_collection = false;

  if (do_young_collection) {
    GCIdMark gc_id_mark;
    GCTraceCPUTime tcpu(((DefNewGeneration*)_young_gen)->gc_tracer());
    GCTraceTime(Info, gc) t("Pause Young", nullptr, gc_cause(), true);

    print_heap_before_gc();

    if (run_verification && VerifyGCLevel <= 0 && VerifyBeforeGC) {
      prepare_for_verify();
      prepared_for_verification = true;
    }

    gc_prologue(complete);
    increment_total_collections(complete);

    collect_generation(_young_gen,
                       full,
                       size,
                       is_tlab,
                       run_verification && VerifyGCLevel <= 0,
                       do_clear_all_soft_refs);

    if (size > 0 && (!is_tlab || _young_gen->supports_tlab_allocation()) &&
        size * HeapWordSize <= _young_gen->unsafe_max_alloc_nogc()) {
      // Allocation request was met by young GC.
      size = 0;
    }

    // Ask if young collection is enough. If so, do the final steps for young collection,
    // and fallthrough to the end.
    do_full_collection = should_do_full_collection(size, full, is_tlab, max_generation);
    if (!do_full_collection) {
      // Adjust generation sizes.
      _young_gen->compute_new_size();

      print_heap_change(pre_gc_values);

      // Track memory usage and detect low memory after GC finishes
      MemoryService::track_memory_usage();

      gc_epilogue(complete);
    }

    print_heap_after_gc();

  } else {
    // No young collection, ask if we need to perform Full collection.
    do_full_collection = should_do_full_collection(size, full, is_tlab, max_generation);
  }

  if (do_full_collection) {
    GCIdMark gc_id_mark;
    GCTraceCPUTime tcpu(GenMarkSweep::gc_tracer());
    GCTraceTime(Info, gc) t("Pause Full", nullptr, gc_cause(), true);

    print_heap_before_gc();

    if (!prepared_for_verification && run_verification &&
        VerifyGCLevel <= 1 && VerifyBeforeGC) {
      prepare_for_verify();
    }

    if (!do_young_collection) {
      gc_prologue(complete);
      increment_total_collections(complete);
    }

    // Accounting quirk: total full collections would be incremented when "complete"
    // is set, by calling increment_total_collections above. However, we also need to
    // account Full collections that had "complete" unset.
    if (!complete) {
      increment_total_full_collections();
    }

    CodeCache::on_gc_marking_cycle_start();

    collect_generation(_old_gen,
                       full,
                       size,
                       is_tlab,
                       run_verification && VerifyGCLevel <= 1,
                       do_clear_all_soft_refs);

    CodeCache::on_gc_marking_cycle_finish();
    CodeCache::arm_all_nmethods();

    // Adjust generation sizes.
    _old_gen->compute_new_size();
    _young_gen->compute_new_size();

    // Delete metaspaces for unloaded class loaders and clean up loader_data graph
    ClassLoaderDataGraph::purge(/*at_safepoint*/true);
    DEBUG_ONLY(MetaspaceUtils::verify();)

    // Need to clear claim bits for the next mark.
    ClassLoaderDataGraph::clear_claimed_marks();

    // Resize the metaspace capacity after full collections
    MetaspaceGC::compute_new_size();
    update_full_collections_completed();

    print_heap_change(pre_gc_values);

    // Track memory usage and detect low memory after GC finishes
    MemoryService::track_memory_usage();

    // Need to tell the epilogue code we are done with Full GC, regardless what was
    // the initial value for "complete" flag.
    gc_epilogue(true);

    print_heap_after_gc();
  }
}

bool GenCollectedHeap::should_do_full_collection(size_t size, bool full, bool is_tlab,
                                                 GenCollectedHeap::GenerationType max_gen) const {
  return max_gen == OldGen && _old_gen->should_collect(full, size, is_tlab);
}

void GenCollectedHeap::register_nmethod(nmethod* nm) {
  ScavengableNMethods::register_nmethod(nm);
}

void GenCollectedHeap::unregister_nmethod(nmethod* nm) {
  ScavengableNMethods::unregister_nmethod(nm);
}

void GenCollectedHeap::verify_nmethod(nmethod* nm) {
  ScavengableNMethods::verify_nmethod(nm);
}

void GenCollectedHeap::prune_scavengable_nmethods() {
  ScavengableNMethods::prune_nmethods();
}

HeapWord* GenCollectedHeap::satisfy_failed_allocation(size_t size, bool is_tlab) {
  GCCauseSetter x(this, GCCause::_allocation_failure);
  HeapWord* result = nullptr;

  assert(size != 0, "Precondition violated");
  if (GCLocker::is_active_and_needs_gc()) {
    // GC locker is active; instead of a collection we will attempt
    // to expand the heap, if there's room for expansion.
    if (!is_maximal_no_gc()) {
      result = expand_heap_and_allocate(size, is_tlab);
    }
    return result;   // Could be null if we are out of space.
  } else if (!incremental_collection_will_fail(false /* don't consult_young */)) {
    // Do an incremental collection.
    do_collection(false,                     // full
                  false,                     // clear_all_soft_refs
                  size,                      // size
                  is_tlab,                   // is_tlab
                  GenCollectedHeap::OldGen); // max_generation
  } else {
    log_trace(gc)(" :: Trying full because partial may fail :: ");
    // Try a full collection; see delta for bug id 6266275
    // for the original code and why this has been simplified
    // with from-space allocation criteria modified and
    // such allocation moved out of the safepoint path.
    do_collection(true,                      // full
                  false,                     // clear_all_soft_refs
                  size,                      // size
                  is_tlab,                   // is_tlab
                  GenCollectedHeap::OldGen); // max_generation
  }

  result = attempt_allocation(size, is_tlab, false /*first_only*/);

  if (result != nullptr) {
    assert(is_in_reserved(result), "result not in heap");
    return result;
  }

  // OK, collection failed, try expansion.
  result = expand_heap_and_allocate(size, is_tlab);
  if (result != nullptr) {
    return result;
  }

  // If we reach this point, we're really out of memory. Try every trick
  // we can to reclaim memory. Force collection of soft references. Force
  // a complete compaction of the heap. Any additional methods for finding
  // free memory should be here, especially if they are expensive. If this
  // attempt fails, an OOM exception will be thrown.
  {
    UIntFlagSetting flag_change(MarkSweepAlwaysCompactCount, 1); // Make sure the heap is fully compacted

    do_collection(true,                      // full
                  true,                      // clear_all_soft_refs
                  size,                      // size
                  is_tlab,                   // is_tlab
                  GenCollectedHeap::OldGen); // max_generation
  }

  result = attempt_allocation(size, is_tlab, false /* first_only */);
  if (result != nullptr) {
    assert(is_in_reserved(result), "result not in heap");
    return result;
  }

  assert(!soft_ref_policy()->should_clear_all_soft_refs(),
    "Flag should have been handled and cleared prior to this point");

  // What else?  We might try synchronous finalization later.  If the total
  // space available is large enough for the allocation, then a more
  // complete compaction phase than we've tried so far might be
  // appropriate.
  return nullptr;
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

void GenCollectedHeap::process_roots(ScanningOption so,
                                     OopClosure* strong_roots,
                                     CLDClosure* strong_cld_closure,
                                     CLDClosure* weak_cld_closure,
                                     CodeBlobToOopClosure* code_roots) {
  // General roots.
  assert(code_roots != nullptr, "code root closure should always be set");

  ClassLoaderDataGraph::roots_cld_do(strong_cld_closure, weak_cld_closure);

  // Only process code roots from thread stacks if we aren't visiting the entire CodeCache anyway
  CodeBlobToOopClosure* roots_from_code_p = (so & SO_AllCodeCache) ? nullptr : code_roots;

  Threads::oops_do(strong_roots, roots_from_code_p);

  OopStorageSet::strong_oops_do(strong_roots);

  if (so & SO_ScavengeCodeCache) {
    assert(code_roots != nullptr, "must supply closure for code cache");

    // We only visit parts of the CodeCache when scavenging.
    ScavengableNMethods::nmethods_do(code_roots);
  }
  if (so & SO_AllCodeCache) {
    assert(code_roots != nullptr, "must supply closure for code cache");

    // CMSCollector uses this to do intermediate-strength collections.
    // We scan the entire code cache, since CodeCache::do_unloading is not called.
    CodeCache::blobs_do(code_roots);
  }
  // Verify that the code cache contents are not subject to
  // movement by a scavenging collection.
  DEBUG_ONLY(CodeBlobToOopClosure assert_code_is_non_scavengable(&assert_is_non_scavengable_closure, !CodeBlobToOopClosure::FixRelocations));
  DEBUG_ONLY(ScavengableNMethods::asserted_non_scavengable_nmethods_do(&assert_code_is_non_scavengable));
}

void GenCollectedHeap::gen_process_weak_roots(OopClosure* root_closure) {
  WeakProcessor::oops_do(root_closure);
}

bool GenCollectedHeap::no_allocs_since_save_marks() {
  return _young_gen->no_allocs_since_save_marks() &&
         _old_gen->no_allocs_since_save_marks();
}

// public collection interfaces
void GenCollectedHeap::collect(GCCause::Cause cause) {
  // The caller doesn't have the Heap_lock
  assert(!Heap_lock->owned_by_self(), "this thread should not own the Heap_lock");

  unsigned int gc_count_before;
  unsigned int full_gc_count_before;

  {
    MutexLocker ml(Heap_lock);
    // Read the GC count while holding the Heap_lock
    gc_count_before      = total_collections();
    full_gc_count_before = total_full_collections();
  }

  if (GCLocker::should_discard(cause, gc_count_before)) {
    return;
  }

  bool should_run_young_gc =  (cause == GCCause::_wb_young_gc)
                           || (cause == GCCause::_gc_locker)
                DEBUG_ONLY(|| (cause == GCCause::_scavenge_alot));

  const GenerationType max_generation = should_run_young_gc
                                      ? YoungGen
                                      : OldGen;

  while (true) {
    VM_GenCollectFull op(gc_count_before, full_gc_count_before,
                        cause, max_generation);
    VMThread::execute(&op);

    if (!GCCause::is_explicit_full_gc(cause)) {
      return;
    }

    {
      MutexLocker ml(Heap_lock);
      // Read the GC count while holding the Heap_lock
      if (full_gc_count_before != total_full_collections()) {
        return;
      }
    }

    if (GCLocker::is_active_and_needs_gc()) {
      // If GCLocker is active, wait until clear before retrying.
      GCLocker::stall_until_clear();
    }
  }
}

void GenCollectedHeap::do_full_collection(bool clear_all_soft_refs) {
   do_full_collection(clear_all_soft_refs, OldGen);
}

void GenCollectedHeap::do_full_collection(bool clear_all_soft_refs,
                                          GenerationType last_generation) {
  do_collection(true,                   // full
                clear_all_soft_refs,    // clear_all_soft_refs
                0,                      // size
                false,                  // is_tlab
                last_generation);       // last_generation
  // Hack XXX FIX ME !!!
  // A scavenge may not have been attempted, or may have
  // been attempted and failed, because the old gen was too full
  if (gc_cause() == GCCause::_gc_locker && incremental_collection_failed()) {
    log_debug(gc, jni)("GC locker: Trying a full collection because scavenge failed");
    // This time allow the old gen to be collected as well
    do_collection(true,                // full
                  clear_all_soft_refs, // clear_all_soft_refs
                  0,                   // size
                  false,               // is_tlab
                  OldGen);             // last_generation
  }
}

bool GenCollectedHeap::is_in_young(const void* p) const {
  bool result = p < _old_gen->reserved().start();
  assert(result == _young_gen->is_in_reserved(p),
         "incorrect test - result=%d, p=" PTR_FORMAT, result, p2i(p));
  return result;
}

bool GenCollectedHeap::requires_barriers(stackChunkOop obj) const {
  return !is_in_young(obj);
}

// Returns "TRUE" iff "p" points into the committed areas of the heap.
bool GenCollectedHeap::is_in(const void* p) const {
  return _young_gen->is_in(p) || _old_gen->is_in(p);
}

#ifdef ASSERT
// Don't implement this by using is_in_young().  This method is used
// in some cases to check that is_in_young() is correct.
bool GenCollectedHeap::is_in_partial_collection(const void* p) {
  assert(is_in_reserved(p) || p == nullptr,
    "Does not work if address is non-null and outside of the heap");
  return p < _young_gen->reserved().end() && p != nullptr;
}
#endif

void GenCollectedHeap::oop_iterate(OopIterateClosure* cl) {
  _young_gen->oop_iterate(cl);
  _old_gen->oop_iterate(cl);
}

void GenCollectedHeap::object_iterate(ObjectClosure* cl) {
  _young_gen->object_iterate(cl);
  _old_gen->object_iterate(cl);
}

Space* GenCollectedHeap::space_containing(const void* addr) const {
  Space* res = _young_gen->space_containing(addr);
  if (res != nullptr) {
    return res;
  }
  res = _old_gen->space_containing(addr);
  assert(res != nullptr, "Could not find containing space");
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

bool GenCollectedHeap::block_is_obj(const HeapWord* addr) const {
  assert(is_in_reserved(addr), "block_is_obj of address outside of heap");
  assert(block_start(addr) == addr, "addr must be a block start");
  if (_young_gen->is_in_reserved(addr)) {
    return _young_gen->block_is_obj(addr);
  }

  assert(_old_gen->is_in_reserved(addr), "Some generation should contain the address");
  return _old_gen->block_is_obj(addr);
}

size_t GenCollectedHeap::tlab_capacity(Thread* thr) const {
  assert(!_old_gen->supports_tlab_allocation(), "Old gen supports TLAB allocation?!");
  assert(_young_gen->supports_tlab_allocation(), "Young gen doesn't support TLAB allocation?!");
  return _young_gen->tlab_capacity();
}

size_t GenCollectedHeap::tlab_used(Thread* thr) const {
  assert(!_old_gen->supports_tlab_allocation(), "Old gen supports TLAB allocation?!");
  assert(_young_gen->supports_tlab_allocation(), "Young gen doesn't support TLAB allocation?!");
  return _young_gen->tlab_used();
}

size_t GenCollectedHeap::unsafe_max_tlab_alloc(Thread* thr) const {
  assert(!_old_gen->supports_tlab_allocation(), "Old gen supports TLAB allocation?!");
  assert(_young_gen->supports_tlab_allocation(), "Young gen doesn't support TLAB allocation?!");
  return _young_gen->unsafe_max_tlab_alloc();
}

HeapWord* GenCollectedHeap::allocate_new_tlab(size_t min_size,
                                              size_t requested_size,
                                              size_t* actual_size) {
  HeapWord* result = mem_allocate_work(requested_size /* size */,
                                       true /* is_tlab */);
  if (result != nullptr) {
    *actual_size = requested_size;
  }

  return result;
}

void GenCollectedHeap::prepare_for_verify() {
  ensure_parsability(false);        // no need to retire TLABs
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
  // SerialHeap is the only subtype of GenCollectedHeap.
  return named_heap<GenCollectedHeap>(CollectedHeap::Serial);
}

#if INCLUDE_SERIALGC
void GenCollectedHeap::prepare_for_compaction() {
  // Start by compacting into same gen.
  CompactPoint cp(_old_gen);
  _old_gen->prepare_for_compaction(&cp);
  _young_gen->prepare_for_compaction(&cp);
}
#endif // INCLUDE_SERIALGC

void GenCollectedHeap::verify(VerifyOption option /* ignored */) {
  log_debug(gc, verify)("%s", _old_gen->name());
  _old_gen->verify();

  log_debug(gc, verify)("%s", _young_gen->name());
  _young_gen->verify();

  log_debug(gc, verify)("RemSet");
  rem_set()->verify();
}

void GenCollectedHeap::print_on(outputStream* st) const {
  if (_young_gen != nullptr) {
    _young_gen->print_on(st);
  }
  if (_old_gen != nullptr) {
    _old_gen->print_on(st);
  }
  MetaspaceUtils::print_on(st);
}

void GenCollectedHeap::gc_threads_do(ThreadClosure* tc) const {
}

bool GenCollectedHeap::print_location(outputStream* st, void* addr) const {
  return BlockLocationPrinter<GenCollectedHeap>::print_location(st, addr);
}

void GenCollectedHeap::print_tracing_info() const {
  if (log_is_enabled(Debug, gc, heap, exit)) {
    LogStreamHandle(Debug, gc, heap, exit) lsh;
    _young_gen->print_summary_info_on(&lsh);
    _old_gen->print_summary_info_on(&lsh);
  }
}

void GenCollectedHeap::print_heap_change(const PreGenGCValues& pre_gc_values) const {
  const DefNewGeneration* const def_new_gen = (DefNewGeneration*) young_gen();

  log_info(gc, heap)(HEAP_CHANGE_FORMAT" "
                     HEAP_CHANGE_FORMAT" "
                     HEAP_CHANGE_FORMAT,
                     HEAP_CHANGE_FORMAT_ARGS(def_new_gen->short_name(),
                                             pre_gc_values.young_gen_used(),
                                             pre_gc_values.young_gen_capacity(),
                                             def_new_gen->used(),
                                             def_new_gen->capacity()),
                     HEAP_CHANGE_FORMAT_ARGS("Eden",
                                             pre_gc_values.eden_used(),
                                             pre_gc_values.eden_capacity(),
                                             def_new_gen->eden()->used(),
                                             def_new_gen->eden()->capacity()),
                     HEAP_CHANGE_FORMAT_ARGS("From",
                                             pre_gc_values.from_used(),
                                             pre_gc_values.from_capacity(),
                                             def_new_gen->from()->used(),
                                             def_new_gen->from()->capacity()));
  log_info(gc, heap)(HEAP_CHANGE_FORMAT,
                     HEAP_CHANGE_FORMAT_ARGS(old_gen()->short_name(),
                                             pre_gc_values.old_gen_used(),
                                             pre_gc_values.old_gen_capacity(),
                                             old_gen()->used(),
                                             old_gen()->capacity()));
  MetaspaceUtils::print_metaspace_change(pre_gc_values.metaspace_sizes());
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

  // Fill TLAB's and such
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
#if COMPILER2_OR_JVMCI
  assert(DerivedPointerTable::is_empty(), "derived pointer present");
#endif // COMPILER2_OR_JVMCI

  resize_all_tlabs();

  GenGCEpilogueClosure blk(full);
  generation_iterate(&blk, false);  // not old-to-young.

  MetaspaceCounters::update_performance_counters();
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
