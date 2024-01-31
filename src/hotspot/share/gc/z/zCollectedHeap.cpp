/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/classLoaderData.hpp"
#include "gc/shared/gcHeapSummary.hpp"
#include "gc/shared/gcLogPrecious.hpp"
#include "gc/shared/suspendibleThreadSet.hpp"
#include "gc/z/zAbort.hpp"
#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zAllocator.inline.hpp"
#include "gc/z/zCollectedHeap.hpp"
#include "gc/z/zContinuation.inline.hpp"
#include "gc/z/zDirector.hpp"
#include "gc/z/zDriver.hpp"
#include "gc/z/zGeneration.inline.hpp"
#include "gc/z/zGlobals.hpp"
#include "gc/z/zHeap.inline.hpp"
#include "gc/z/zJNICritical.hpp"
#include "gc/z/zNMethod.hpp"
#include "gc/z/zObjArrayAllocator.hpp"
#include "gc/z/zServiceability.hpp"
#include "gc/z/zStackChunkGCData.inline.hpp"
#include "gc/z/zStat.hpp"
#include "gc/z/zUtils.inline.hpp"
#include "memory/classLoaderMetaspace.hpp"
#include "memory/iterator.hpp"
#include "memory/metaspaceCriticalAllocation.hpp"
#include "memory/universe.hpp"
#include "oops/stackChunkOop.hpp"
#include "runtime/continuationJavaClasses.hpp"
#include "runtime/jniHandles.inline.hpp"
#include "services/memoryUsage.hpp"
#include "utilities/align.hpp"

ZCollectedHeap* ZCollectedHeap::heap() {
  return named_heap<ZCollectedHeap>(CollectedHeap::Z);
}

ZCollectedHeap::ZCollectedHeap()
  : _soft_ref_policy(),
    _barrier_set(),
    _initialize(&_barrier_set),
    _heap(),
    _driver_minor(new ZDriverMinor()),
    _driver_major(new ZDriverMajor()),
    _director(new ZDirector()),
    _stat(new ZStat()),
    _runtime_workers() {}

CollectedHeap::Name ZCollectedHeap::kind() const {
  return CollectedHeap::Z;
}

const char* ZCollectedHeap::name() const {
  return ZName;
}

jint ZCollectedHeap::initialize() {
  if (!_heap.is_initialized()) {
    return JNI_ENOMEM;
  }

  Universe::set_verify_data(~(ZAddressHeapBase - 1) | 0x7, ZAddressHeapBase);

  return JNI_OK;
}

void ZCollectedHeap::initialize_serviceability() {
  _heap.serviceability_initialize();
}

class ZStopConcurrentGCThreadClosure : public ThreadClosure {
public:
  virtual void do_thread(Thread* thread) {
    if (thread->is_ConcurrentGC_thread()) {
      ConcurrentGCThread::cast(thread)->stop();
    }
  }
};

void ZCollectedHeap::stop() {
  log_info_p(gc, exit)("Stopping ZGC");
  ZAbort::abort();
  ZStopConcurrentGCThreadClosure cl;
  gc_threads_do(&cl);
}

SoftRefPolicy* ZCollectedHeap::soft_ref_policy() {
  return &_soft_ref_policy;
}

size_t ZCollectedHeap::max_capacity() const {
  return _heap.max_capacity();
}

size_t ZCollectedHeap::capacity() const {
  return _heap.capacity();
}

size_t ZCollectedHeap::used() const {
  return _heap.used();
}

size_t ZCollectedHeap::unused() const {
  return _heap.unused();
}

bool ZCollectedHeap::is_maximal_no_gc() const {
  // Not supported
  ShouldNotReachHere();
  return false;
}

bool ZCollectedHeap::is_in(const void* p) const {
  return _heap.is_in((uintptr_t)p);
}

bool ZCollectedHeap::requires_barriers(stackChunkOop obj) const {
  return ZContinuation::requires_barriers(&_heap, obj);
}

HeapWord* ZCollectedHeap::allocate_new_tlab(size_t min_size, size_t requested_size, size_t* actual_size) {
  const size_t size_in_bytes = ZUtils::words_to_bytes(align_object_size(requested_size));
  const zaddress addr = ZAllocator::eden()->alloc_tlab(size_in_bytes);

  if (!is_null(addr)) {
    *actual_size = requested_size;
  }

  return (HeapWord*)untype(addr);
}

oop ZCollectedHeap::array_allocate(Klass* klass, size_t size, int length, bool do_zero, TRAPS) {
  const ZObjArrayAllocator allocator(klass, size, length, do_zero, THREAD);
  return allocator.allocate();
}

HeapWord* ZCollectedHeap::mem_allocate(size_t size, bool* gc_overhead_limit_was_exceeded) {
  const size_t size_in_bytes = ZUtils::words_to_bytes(align_object_size(size));
  return (HeapWord*)ZAllocator::eden()->alloc_object(size_in_bytes);
}

MetaWord* ZCollectedHeap::satisfy_failed_metadata_allocation(ClassLoaderData* loader_data,
                                                             size_t size,
                                                             Metaspace::MetadataType mdtype) {
  // Start asynchronous GC
  collect(GCCause::_metadata_GC_threshold);

  // Expand and retry allocation
  MetaWord* const result = loader_data->metaspace_non_null()->expand_and_allocate(size, mdtype);
  if (result != nullptr) {
    return result;
  }

  // As a last resort, try a critical allocation, riding on a synchronous full GC
  return MetaspaceCriticalAllocation::allocate(loader_data, size, mdtype);
}

void ZCollectedHeap::collect(GCCause::Cause cause) {
  // Handle external collection requests
  switch (cause) {
  case GCCause::_wb_young_gc:
  case GCCause::_scavenge_alot:
    // Start urgent minor GC
    _driver_minor->collect(ZDriverRequest(cause, ZYoungGCThreads, 0));
    break;

  case GCCause::_heap_dump:
  case GCCause::_heap_inspection:
  case GCCause::_wb_full_gc:
  case GCCause::_wb_breakpoint:
  case GCCause::_dcmd_gc_run:
  case GCCause::_java_lang_system_gc:
  case GCCause::_full_gc_alot:
  case GCCause::_jvmti_force_gc:
  case GCCause::_metadata_GC_clear_soft_refs:
  case GCCause::_codecache_GC_aggressive:
    // Start urgent major GC
    _driver_major->collect(ZDriverRequest(cause, ZYoungGCThreads, ZOldGCThreads));
    break;

  case GCCause::_metadata_GC_threshold:
  case GCCause::_codecache_GC_threshold:
    // Start not urgent major GC
    _driver_major->collect(ZDriverRequest(cause, 1, 1));
    break;

  default:
    fatal("Unsupported GC cause (%s)", GCCause::to_string(cause));
    break;
  }
}

void ZCollectedHeap::collect_as_vm_thread(GCCause::Cause cause) {
  // These collection requests are ignored since ZGC can't run a synchronous
  // GC cycle from within the VM thread. This is considered benign, since the
  // only GC causes coming in here should be heap dumper and heap inspector.
  // If the heap dumper or heap inspector explicitly requests a gc and the
  // caller is not the VM thread a synchronous GC cycle is performed from the
  // caller thread in the prologue.
  assert(Thread::current()->is_VM_thread(), "Should be the VM thread");
  guarantee(cause == GCCause::_heap_dump ||
            cause == GCCause::_heap_inspection, "Invalid cause");
}

void ZCollectedHeap::do_full_collection(bool clear_all_soft_refs) {
  // Not supported
  ShouldNotReachHere();
}

size_t ZCollectedHeap::tlab_capacity(Thread* ignored) const {
  return _heap.tlab_capacity();
}

size_t ZCollectedHeap::tlab_used(Thread* ignored) const {
  return _heap.tlab_used();
}

size_t ZCollectedHeap::max_tlab_size() const {
  return _heap.max_tlab_size();
}

size_t ZCollectedHeap::unsafe_max_tlab_alloc(Thread* ignored) const {
  return _heap.unsafe_max_tlab_alloc();
}

bool ZCollectedHeap::uses_stack_watermark_barrier() const {
  return true;
}

MemoryUsage ZCollectedHeap::memory_usage() {
  const size_t initial_size = ZHeap::heap()->initial_capacity();
  const size_t committed    = ZHeap::heap()->capacity();
  const size_t used         = MIN2(ZHeap::heap()->used(), committed);
  const size_t max_size     = ZHeap::heap()->max_capacity();

  return MemoryUsage(initial_size, used, committed, max_size);
}

GrowableArray<GCMemoryManager*> ZCollectedHeap::memory_managers() {
  GrowableArray<GCMemoryManager*> memory_managers(4);
  memory_managers.append(_heap.serviceability_cycle_memory_manager(true /* minor */));
  memory_managers.append(_heap.serviceability_cycle_memory_manager(false /* minor */));
  memory_managers.append(_heap.serviceability_pause_memory_manager(true /* minor */));
  memory_managers.append(_heap.serviceability_pause_memory_manager(false /* minor */));
  return memory_managers;
}

GrowableArray<MemoryPool*> ZCollectedHeap::memory_pools() {
  GrowableArray<MemoryPool*> memory_pools(2);
  memory_pools.append(_heap.serviceability_memory_pool(ZGenerationId::young));
  memory_pools.append(_heap.serviceability_memory_pool(ZGenerationId::old));
  return memory_pools;
}

void ZCollectedHeap::object_iterate(ObjectClosure* cl) {
  _heap.object_iterate(cl, true /* visit_weaks */);
}

ParallelObjectIteratorImpl* ZCollectedHeap::parallel_object_iterator(uint nworkers) {
  return _heap.parallel_object_iterator(nworkers, true /* visit_weaks */);
}

void ZCollectedHeap::pin_object(JavaThread* thread, oop obj) {
  ZJNICritical::enter(thread);
}

void ZCollectedHeap::unpin_object(JavaThread* thread, oop obj) {
  ZJNICritical::exit(thread);
}

void ZCollectedHeap::keep_alive(oop obj) {
  _heap.keep_alive(obj);
}

void ZCollectedHeap::register_nmethod(nmethod* nm) {
  ZNMethod::register_nmethod(nm);
}

void ZCollectedHeap::unregister_nmethod(nmethod* nm) {
  // ZGC follows the 'unlink | handshake | purge', where nmethods are unlinked
  // from the system, threads are handshaked so that no reference to the
  // unlinked nmethods exist, then the nmethods are deleted in the purge phase.
  //
  // CollectedHeap::unregister_nmethod is called during the flush phase, which
  // is too late for ZGC.

  ZNMethod::purge_nmethod(nm);
}

void ZCollectedHeap::verify_nmethod(nmethod* nm) {
  // Does nothing
}

WorkerThreads* ZCollectedHeap::safepoint_workers() {
  return _runtime_workers.workers();
}

void ZCollectedHeap::gc_threads_do(ThreadClosure* tc) const {
  tc->do_thread(_director);
  tc->do_thread(_driver_major);
  tc->do_thread(_driver_minor);
  tc->do_thread(_stat);
  _heap.threads_do(tc);
  _runtime_workers.threads_do(tc);
}

VirtualSpaceSummary ZCollectedHeap::create_heap_space_summary() {
  const uintptr_t start = ZAddressHeapBase;

  // Fake values. ZGC does not commit memory contiguously in the reserved
  // address space, and the reserved space is larger than MaxHeapSize.
  const uintptr_t committed_end = ZAddressHeapBase + capacity();
  const uintptr_t reserved_end = ZAddressHeapBase + max_capacity();

  return VirtualSpaceSummary((HeapWord*)start, (HeapWord*)committed_end, (HeapWord*)reserved_end);
}

bool ZCollectedHeap::contains_null(const oop* p) const {
  const zpointer* const ptr = (const zpointer*)p;
  return is_null_any(*ptr);
}

void ZCollectedHeap::safepoint_synchronize_begin() {
  ZGeneration::young()->synchronize_relocation();
  ZGeneration::old()->synchronize_relocation();
  SuspendibleThreadSet::synchronize();
}

void ZCollectedHeap::safepoint_synchronize_end() {
  SuspendibleThreadSet::desynchronize();
  ZGeneration::old()->desynchronize_relocation();
  ZGeneration::young()->desynchronize_relocation();
}

void ZCollectedHeap::prepare_for_verify() {
  // Does nothing
}

void ZCollectedHeap::print_on(outputStream* st) const {
  _heap.print_on(st);
}

void ZCollectedHeap::print_on_error(outputStream* st) const {
  st->print_cr("ZGC Globals:");
  st->print_cr(" Young Collection:   %s/%u", ZGeneration::young()->phase_to_string(), ZGeneration::young()->seqnum());
  st->print_cr(" Old Collection:     %s/%u", ZGeneration::old()->phase_to_string(), ZGeneration::old()->seqnum());
  st->print_cr(" Offset Max:         " SIZE_FORMAT "%s (" PTR_FORMAT ")",
               byte_size_in_exact_unit(ZAddressOffsetMax),
               exact_unit_for_byte_size(ZAddressOffsetMax),
               ZAddressOffsetMax);
  st->print_cr(" Page Size Small:    " SIZE_FORMAT "M", ZPageSizeSmall / M);
  st->print_cr(" Page Size Medium:   " SIZE_FORMAT "M", ZPageSizeMedium / M);
  st->cr();
  st->print_cr("ZGC Metadata Bits:");
  st->print_cr(" LoadGood:           " PTR_FORMAT, ZPointerLoadGoodMask);
  st->print_cr(" LoadBad:            " PTR_FORMAT, ZPointerLoadBadMask);
  st->print_cr(" MarkGood:           " PTR_FORMAT, ZPointerMarkGoodMask);
  st->print_cr(" MarkBad:            " PTR_FORMAT, ZPointerMarkBadMask);
  st->print_cr(" StoreGood:          " PTR_FORMAT, ZPointerStoreGoodMask);
  st->print_cr(" StoreBad:           " PTR_FORMAT, ZPointerStoreBadMask);
  st->print_cr(" ------------------- ");
  st->print_cr(" Remapped:           " PTR_FORMAT, ZPointerRemapped);
  st->print_cr(" RemappedYoung:      " PTR_FORMAT, ZPointerRemappedYoungMask);
  st->print_cr(" RemappedOld:        " PTR_FORMAT, ZPointerRemappedOldMask);
  st->print_cr(" MarkedYoung:        " PTR_FORMAT, ZPointerMarkedYoung);
  st->print_cr(" MarkedOld:          " PTR_FORMAT, ZPointerMarkedOld);
  st->print_cr(" Remembered:         " PTR_FORMAT, ZPointerRemembered);
  st->cr();
  CollectedHeap::print_on_error(st);
}

void ZCollectedHeap::print_extended_on(outputStream* st) const {
  _heap.print_extended_on(st);
}

void ZCollectedHeap::print_tracing_info() const {
  // Does nothing
}

bool ZCollectedHeap::print_location(outputStream* st, void* addr) const {
  return _heap.print_location(st, (uintptr_t)addr);
}

void ZCollectedHeap::verify(VerifyOption option /* ignored */) {
  fatal("Externally triggered verification not supported");
}

bool ZCollectedHeap::is_oop(oop object) const {
  return _heap.is_oop(cast_from_oop<uintptr_t>(object));
}

bool ZCollectedHeap::supports_concurrent_gc_breakpoints() const {
  return true;
}
