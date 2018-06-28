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
#include "gc/shared/gcHeapSummary.hpp"
#include "gc/z/zCollectedHeap.hpp"
#include "gc/z/zGlobals.hpp"
#include "gc/z/zHeap.inline.hpp"
#include "gc/z/zNMethodTable.hpp"
#include "gc/z/zServiceability.hpp"
#include "gc/z/zStat.hpp"
#include "gc/z/zUtils.inline.hpp"
#include "runtime/mutexLocker.hpp"

ZCollectedHeap* ZCollectedHeap::heap() {
  CollectedHeap* heap = Universe::heap();
  assert(heap != NULL, "Uninitialized access to ZCollectedHeap::heap()");
  assert(heap->kind() == CollectedHeap::Z, "Invalid name");
  return (ZCollectedHeap*)heap;
}

ZCollectedHeap::ZCollectedHeap(ZCollectorPolicy* policy) :
    _collector_policy(policy),
    _soft_ref_policy(),
    _barrier_set(),
    _initialize(&_barrier_set),
    _heap(),
    _director(new ZDirector()),
    _driver(new ZDriver()),
    _stat(new ZStat()),
    _runtime_workers() {}

CollectedHeap::Name ZCollectedHeap::kind() const {
  return CollectedHeap::Z;
}

const char* ZCollectedHeap::name() const {
  return ZGCName;
}

jint ZCollectedHeap::initialize() {
  if (!_heap.is_initialized()) {
    return JNI_ENOMEM;
  }

  initialize_reserved_region((HeapWord*)ZAddressReservedStart(),
                             (HeapWord*)ZAddressReservedEnd());

  return JNI_OK;
}

void ZCollectedHeap::initialize_serviceability() {
  _heap.serviceability_initialize();
}

void ZCollectedHeap::stop() {
  _director->stop();
  _driver->stop();
  _stat->stop();
}

CollectorPolicy* ZCollectedHeap::collector_policy() const {
  return _collector_policy;
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

bool ZCollectedHeap::is_maximal_no_gc() const {
  // Not supported
  ShouldNotReachHere();
  return false;
}

bool ZCollectedHeap::is_scavengable(oop obj) {
  return false;
}

bool ZCollectedHeap::is_in(const void* p) const {
  return is_in_reserved(p) && _heap.is_in((uintptr_t)p);
}

bool ZCollectedHeap::is_in_closed_subset(const void* p) const {
  return is_in(p);
}

HeapWord* ZCollectedHeap::allocate_new_tlab(size_t min_size, size_t requested_size, size_t* actual_size) {
  const size_t size_in_bytes = ZUtils::words_to_bytes(align_object_size(requested_size));
  const uintptr_t addr = _heap.alloc_tlab(size_in_bytes);

  if (addr != 0) {
    *actual_size = requested_size;
  }

  return (HeapWord*)addr;
}

HeapWord* ZCollectedHeap::mem_allocate(size_t size, bool* gc_overhead_limit_was_exceeded) {
  const size_t size_in_bytes = ZUtils::words_to_bytes(align_object_size(size));
  return (HeapWord*)_heap.alloc_object(size_in_bytes);
}

MetaWord* ZCollectedHeap::satisfy_failed_metadata_allocation(ClassLoaderData* loader_data,
                                                             size_t size,
                                                             Metaspace::MetadataType mdtype) {
  MetaWord* result;

  // Start asynchronous GC
  collect(GCCause::_metadata_GC_threshold);

  // Expand and retry allocation
  result = loader_data->metaspace_non_null()->expand_and_allocate(size, mdtype);
  if (result != NULL) {
    return result;
  }

  // Start synchronous GC
  collect(GCCause::_metadata_GC_clear_soft_refs);

  // Retry allocation
  result = loader_data->metaspace_non_null()->allocate(size, mdtype);
  if (result != NULL) {
    return result;
  }

  // Expand and retry allocation
  result = loader_data->metaspace_non_null()->expand_and_allocate(size, mdtype);
  if (result != NULL) {
    return result;
  }

  // Out of memory
  return NULL;
}

void ZCollectedHeap::collect(GCCause::Cause cause) {
  _driver->collect(cause);
}

void ZCollectedHeap::collect_as_vm_thread(GCCause::Cause cause) {
  // These collection requests are ignored since ZGC can't run a synchronous
  // GC cycle from within the VM thread. This is considered benign, since the
  // only GC causes coming in here should be heap dumper and heap inspector.
  // However, neither the heap dumper nor the heap inspector really need a GC
  // to happen, but the result of their heap iterations might in that case be
  // less accurate since they might include objects that would otherwise have
  // been collected by a GC.
  assert(Thread::current()->is_VM_thread(), "Should be the VM thread");
  guarantee(cause == GCCause::_heap_dump ||
            cause == GCCause::_heap_inspection, "Invalid cause");
}

void ZCollectedHeap::do_full_collection(bool clear_all_soft_refs) {
  // Not supported
  ShouldNotReachHere();
}

bool ZCollectedHeap::supports_tlab_allocation() const {
  return true;
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

bool ZCollectedHeap::can_elide_tlab_store_barriers() const {
  return false;
}

bool ZCollectedHeap::can_elide_initializing_store_barrier(oop new_obj) {
  // Not supported
  ShouldNotReachHere();
  return true;
}

bool ZCollectedHeap::card_mark_must_follow_store() const {
  // Not supported
  ShouldNotReachHere();
  return false;
}

GrowableArray<GCMemoryManager*> ZCollectedHeap::memory_managers() {
  return GrowableArray<GCMemoryManager*>(1, 1, _heap.serviceability_memory_manager());
}

GrowableArray<MemoryPool*> ZCollectedHeap::memory_pools() {
  return GrowableArray<MemoryPool*>(1, 1, _heap.serviceability_memory_pool());
}

void ZCollectedHeap::object_iterate(ObjectClosure* cl) {
  _heap.object_iterate(cl, true /* visit_referents */);
}

void ZCollectedHeap::safe_object_iterate(ObjectClosure* cl) {
  _heap.object_iterate(cl, true /* visit_referents */);
}

HeapWord* ZCollectedHeap::block_start(const void* addr) const {
  return (HeapWord*)_heap.block_start((uintptr_t)addr);
}

size_t ZCollectedHeap::block_size(const HeapWord* addr) const {
  size_t size_in_bytes = _heap.block_size((uintptr_t)addr);
  return ZUtils::bytes_to_words(size_in_bytes);
}

bool ZCollectedHeap::block_is_obj(const HeapWord* addr) const {
  return _heap.block_is_obj((uintptr_t)addr);
}

void ZCollectedHeap::register_nmethod(nmethod* nm) {
  assert_locked_or_safepoint(CodeCache_lock);
  ZNMethodTable::register_nmethod(nm);
}

void ZCollectedHeap::unregister_nmethod(nmethod* nm) {
  assert_locked_or_safepoint(CodeCache_lock);
  ZNMethodTable::unregister_nmethod(nm);
}

void ZCollectedHeap::verify_nmethod(nmethod* nm) {
  // Does nothing
}

WorkGang* ZCollectedHeap::get_safepoint_workers() {
  return _runtime_workers.workers();
}

jlong ZCollectedHeap::millis_since_last_gc() {
  return ZStatCycle::time_since_last() / MILLIUNITS;
}

void ZCollectedHeap::gc_threads_do(ThreadClosure* tc) const {
  tc->do_thread(_director);
  tc->do_thread(_driver);
  tc->do_thread(_stat);
  _heap.worker_threads_do(tc);
  _runtime_workers.threads_do(tc);
}

VirtualSpaceSummary ZCollectedHeap::create_heap_space_summary() {
  const size_t capacity_in_words = capacity() / HeapWordSize;
  const size_t max_capacity_in_words = max_capacity() / HeapWordSize;
  return VirtualSpaceSummary(reserved_region().start(),
                             reserved_region().start() + capacity_in_words,
                             reserved_region().start() + max_capacity_in_words);
}

void ZCollectedHeap::prepare_for_verify() {
  // Does nothing
}

void ZCollectedHeap::print_on(outputStream* st) const {
  _heap.print_on(st);
}

void ZCollectedHeap::print_on_error(outputStream* st) const {
  CollectedHeap::print_on_error(st);

  st->print_cr("Address Space");
  st->print_cr( "     Start:             " PTR_FORMAT, ZAddressSpaceStart);
  st->print_cr( "     End:               " PTR_FORMAT, ZAddressSpaceEnd);
  st->print_cr( "     Size:              " SIZE_FORMAT_W(-15) " (" PTR_FORMAT ")", ZAddressSpaceSize, ZAddressSpaceSize);
  st->print_cr( "Heap");
  st->print_cr( "     GlobalPhase:       %u", ZGlobalPhase);
  st->print_cr( "     GlobalSeqNum:      %u", ZGlobalSeqNum);
  st->print_cr( "     Offset Max:        " SIZE_FORMAT_W(-15) " (" PTR_FORMAT ")", ZAddressOffsetMax, ZAddressOffsetMax);
  st->print_cr( "     Page Size Small:   " SIZE_FORMAT_W(-15) " (" PTR_FORMAT ")", ZPageSizeSmall, ZPageSizeSmall);
  st->print_cr( "     Page Size Medium:  " SIZE_FORMAT_W(-15) " (" PTR_FORMAT ")", ZPageSizeMedium, ZPageSizeMedium);
  st->print_cr( "Metadata Bits");
  st->print_cr( "     Good:              " PTR_FORMAT, ZAddressGoodMask);
  st->print_cr( "     Bad:               " PTR_FORMAT, ZAddressBadMask);
  st->print_cr( "     WeakBad:           " PTR_FORMAT, ZAddressWeakBadMask);
  st->print_cr( "     Marked:            " PTR_FORMAT, ZAddressMetadataMarked);
  st->print_cr( "     Remapped:          " PTR_FORMAT, ZAddressMetadataRemapped);
}

void ZCollectedHeap::print_extended_on(outputStream* st) const {
  _heap.print_extended_on(st);
}

void ZCollectedHeap::print_gc_threads_on(outputStream* st) const {
  _director->print_on(st);
  st->cr();
  _driver->print_on(st);
  st->cr();
  _stat->print_on(st);
  st->cr();
  _heap.print_worker_threads_on(st);
  _runtime_workers.print_threads_on(st);
}

void ZCollectedHeap::print_tracing_info() const {
  // Does nothing
}

void ZCollectedHeap::verify(VerifyOption option /* ignored */) {
  _heap.verify();
}

bool ZCollectedHeap::is_oop(oop object) const {
  return CollectedHeap::is_oop(object) && _heap.is_oop(object);
}
