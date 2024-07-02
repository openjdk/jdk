/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/gcLocker.inline.hpp"
#include "gc/shared/suspendibleThreadSet.hpp"
#include "gc/x/xCollectedHeap.hpp"
#include "gc/x/xDirector.hpp"
#include "gc/x/xDriver.hpp"
#include "gc/x/xGlobals.hpp"
#include "gc/x/xHeap.inline.hpp"
#include "gc/x/xNMethod.hpp"
#include "gc/x/xObjArrayAllocator.hpp"
#include "gc/x/xOop.inline.hpp"
#include "gc/x/xServiceability.hpp"
#include "gc/x/xStat.hpp"
#include "gc/x/xUtils.inline.hpp"
#include "memory/classLoaderMetaspace.hpp"
#include "memory/iterator.hpp"
#include "memory/metaspaceCriticalAllocation.hpp"
#include "memory/universe.hpp"
#include "oops/stackChunkOop.hpp"
#include "runtime/continuationJavaClasses.hpp"
#include "runtime/stackWatermarkSet.hpp"
#include "utilities/align.hpp"

XCollectedHeap* XCollectedHeap::heap() {
  return named_heap<XCollectedHeap>(CollectedHeap::Z);
}

XCollectedHeap::XCollectedHeap() :
    _barrier_set(),
    _initialize(&_barrier_set),
    _heap(),
    _driver(new XDriver()),
    _director(new XDirector(_driver)),
    _stat(new XStat()),
    _runtime_workers() {}

CollectedHeap::Name XCollectedHeap::kind() const {
  return CollectedHeap::Z;
}

const char* XCollectedHeap::name() const {
  return XName;
}

jint XCollectedHeap::initialize() {
  if (!_heap.is_initialized()) {
    return JNI_ENOMEM;
  }

  Universe::calculate_verify_data((HeapWord*)0, (HeapWord*)UINTPTR_MAX);

  return JNI_OK;
}

void XCollectedHeap::initialize_serviceability() {
  _heap.serviceability_initialize();
}

class XStopConcurrentGCThreadClosure : public ThreadClosure {
public:
  virtual void do_thread(Thread* thread) {
    if (thread->is_ConcurrentGC_thread()) {
      ConcurrentGCThread::cast(thread)->stop();
    }
  }
};

void XCollectedHeap::stop() {
  XStopConcurrentGCThreadClosure cl;
  gc_threads_do(&cl);
}

size_t XCollectedHeap::max_capacity() const {
  return _heap.max_capacity();
}

size_t XCollectedHeap::capacity() const {
  return _heap.capacity();
}

size_t XCollectedHeap::used() const {
  return _heap.used();
}

size_t XCollectedHeap::unused() const {
  return _heap.unused();
}

bool XCollectedHeap::is_maximal_no_gc() const {
  // Not supported
  ShouldNotReachHere();
  return false;
}

bool XCollectedHeap::is_in(const void* p) const {
  return _heap.is_in((uintptr_t)p);
}

bool XCollectedHeap::requires_barriers(stackChunkOop obj) const {
  uintptr_t* cont_addr = obj->field_addr<uintptr_t>(jdk_internal_vm_StackChunk::cont_offset());

  if (!_heap.is_allocating(cast_from_oop<uintptr_t>(obj))) {
    // An object that isn't allocating, is visible from GC tracing. Such
    // stack chunks require barriers.
    return true;
  }

  if (!XAddress::is_good_or_null(*cont_addr)) {
    // If a chunk is allocated after a GC started, but before relocate start
    // we can have an allocating chunk that isn't deeply good. That means that
    // the contained oops might be bad and require GC barriers.
    return true;
  }

  // The chunk is allocating and its pointers are good. This chunk needs no
  // GC barriers
  return false;
}

HeapWord* XCollectedHeap::allocate_new_tlab(size_t min_size, size_t requested_size, size_t* actual_size) {
  const size_t size_in_bytes = XUtils::words_to_bytes(align_object_size(requested_size));
  const uintptr_t addr = _heap.alloc_tlab(size_in_bytes);

  if (addr != 0) {
    *actual_size = requested_size;
  }

  return (HeapWord*)addr;
}

oop XCollectedHeap::array_allocate(Klass* klass, size_t size, int length, bool do_zero, TRAPS) {
  XObjArrayAllocator allocator(klass, size, length, do_zero, THREAD);
  return allocator.allocate();
}

HeapWord* XCollectedHeap::mem_allocate(size_t size, bool* gc_overhead_limit_was_exceeded) {
  const size_t size_in_bytes = XUtils::words_to_bytes(align_object_size(size));
  return (HeapWord*)_heap.alloc_object(size_in_bytes);
}

MetaWord* XCollectedHeap::satisfy_failed_metadata_allocation(ClassLoaderData* loader_data,
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

void XCollectedHeap::collect(GCCause::Cause cause) {
  _driver->collect(cause);
}

void XCollectedHeap::collect_as_vm_thread(GCCause::Cause cause) {
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

void XCollectedHeap::do_full_collection(bool clear_all_soft_refs) {
  // Not supported
  ShouldNotReachHere();
}

size_t XCollectedHeap::tlab_capacity(Thread* ignored) const {
  return _heap.tlab_capacity();
}

size_t XCollectedHeap::tlab_used(Thread* ignored) const {
  return _heap.tlab_used();
}

size_t XCollectedHeap::max_tlab_size() const {
  return _heap.max_tlab_size();
}

size_t XCollectedHeap::unsafe_max_tlab_alloc(Thread* ignored) const {
  return _heap.unsafe_max_tlab_alloc();
}

MemoryUsage XCollectedHeap::memory_usage() {
  return _heap.serviceability_memory_pool()->get_memory_usage();
}

GrowableArray<GCMemoryManager*> XCollectedHeap::memory_managers() {
  GrowableArray<GCMemoryManager*> memory_managers(2);
  memory_managers.append(_heap.serviceability_cycle_memory_manager());
  memory_managers.append(_heap.serviceability_pause_memory_manager());
  return memory_managers;
}

GrowableArray<MemoryPool*> XCollectedHeap::memory_pools() {
  GrowableArray<MemoryPool*> memory_pools(1);
  memory_pools.append(_heap.serviceability_memory_pool());
  return memory_pools;
}

void XCollectedHeap::object_iterate(ObjectClosure* cl) {
  _heap.object_iterate(cl, true /* visit_weaks */);
}

ParallelObjectIteratorImpl* XCollectedHeap::parallel_object_iterator(uint nworkers) {
  return _heap.parallel_object_iterator(nworkers, true /* visit_weaks */);
}

void XCollectedHeap::keep_alive(oop obj) {
  _heap.keep_alive(obj);
}

void XCollectedHeap::register_nmethod(nmethod* nm) {
  XNMethod::register_nmethod(nm);
}

void XCollectedHeap::unregister_nmethod(nmethod* nm) {
  XNMethod::unregister_nmethod(nm);
}

void XCollectedHeap::verify_nmethod(nmethod* nm) {
  // Does nothing
}

WorkerThreads* XCollectedHeap::safepoint_workers() {
  return _runtime_workers.workers();
}

void XCollectedHeap::gc_threads_do(ThreadClosure* tc) const {
  tc->do_thread(_director);
  tc->do_thread(_driver);
  tc->do_thread(_stat);
  _heap.threads_do(tc);
  _runtime_workers.threads_do(tc);
}

VirtualSpaceSummary XCollectedHeap::create_heap_space_summary() {
  return VirtualSpaceSummary((HeapWord*)0, (HeapWord*)capacity(), (HeapWord*)max_capacity());
}

void XCollectedHeap::safepoint_synchronize_begin() {
  StackWatermarkSet::safepoint_synchronize_begin();
  SuspendibleThreadSet::synchronize();
}

void XCollectedHeap::safepoint_synchronize_end() {
  SuspendibleThreadSet::desynchronize();
}

void XCollectedHeap::pin_object(JavaThread* thread, oop obj) {
  GCLocker::lock_critical(thread);
}

void XCollectedHeap::unpin_object(JavaThread* thread, oop obj) {
  GCLocker::unlock_critical(thread);
}

void XCollectedHeap::prepare_for_verify() {
  // Does nothing
}

void XCollectedHeap::print_on(outputStream* st) const {
  _heap.print_on(st);
}

void XCollectedHeap::print_on_error(outputStream* st) const {
  st->print_cr("ZGC Globals:");
  st->print_cr(" GlobalPhase:       %u (%s)", XGlobalPhase, XGlobalPhaseToString());
  st->print_cr(" GlobalSeqNum:      %u", XGlobalSeqNum);
  st->print_cr(" Offset Max:        " SIZE_FORMAT "%s (" PTR_FORMAT ")",
               byte_size_in_exact_unit(XAddressOffsetMax),
               exact_unit_for_byte_size(XAddressOffsetMax),
               XAddressOffsetMax);
  st->print_cr(" Page Size Small:   " SIZE_FORMAT "M", XPageSizeSmall / M);
  st->print_cr(" Page Size Medium:  " SIZE_FORMAT "M", XPageSizeMedium / M);
  st->cr();
  st->print_cr("ZGC Metadata Bits:");
  st->print_cr(" Good:              " PTR_FORMAT, XAddressGoodMask);
  st->print_cr(" Bad:               " PTR_FORMAT, XAddressBadMask);
  st->print_cr(" WeakBad:           " PTR_FORMAT, XAddressWeakBadMask);
  st->print_cr(" Marked:            " PTR_FORMAT, XAddressMetadataMarked);
  st->print_cr(" Remapped:          " PTR_FORMAT, XAddressMetadataRemapped);
  st->cr();
  CollectedHeap::print_on_error(st);
}

void XCollectedHeap::print_extended_on(outputStream* st) const {
  _heap.print_extended_on(st);
}

void XCollectedHeap::print_tracing_info() const {
  // Does nothing
}

bool XCollectedHeap::print_location(outputStream* st, void* addr) const {
  return _heap.print_location(st, (uintptr_t)addr);
}

void XCollectedHeap::verify(VerifyOption option /* ignored */) {
  _heap.verify();
}

bool XCollectedHeap::is_oop(oop object) const {
  return _heap.is_oop(XOop::to_address(object));
}

bool XCollectedHeap::supports_concurrent_gc_breakpoints() const {
  return true;
}
