/*
 * Copyright (c) 2022, Red Hat, Inc. All rights reserved.
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

#include "gc/noop/noopHeap.hpp"
#include "gc/noop/noopMemoryPool.hpp"
#include "gc/noop/noopInitLogger.hpp"
#include "precompiled.hpp"
#include "memory/universe.hpp"
#include "memory/allocation.hpp"
#include "memory/allocation.inline.hpp"
#include "gc/shared/gcArguments.hpp"
#include "gc/shared/locationPrinter.inline.hpp"
#include "logging/log.hpp"
#include "memory/allocation.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/metaspaceUtils.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "runtime/atomic.hpp"
#include "runtime/globals.hpp"
#include "gc/shared/markBitMap.hpp"

NoopHeap* NoopHeap::heap() {
    return named_heap<NoopHeap>(CollectedHeap::Noop);
}

jint NoopHeap::initialize() {
    size_t align = HeapAlignment;
    size_t init_byte_size = align_up(InitialHeapSize, align);
    size_t max_byte_size  = align_up(MaxHeapSize, align);

    // Initialize backing storage(maximum size)
    ReservedHeapSpace heap_rs = Universe::reserve_heap(max_byte_size, align);
    _virtual_space.initialize(heap_rs, max_byte_size);

    MemRegion committed_region((HeapWord*)_virtual_space.low(),          (HeapWord*)_virtual_space.high());

    initialize_reserved_region(heap_rs);

    _space = new ContiguousSpace();
    _space->initialize(committed_region, /* clear_space = */ true, /* mangle_space = */ true);

    _max_tlab_size = MIN2(CollectedHeap::max_tlab_size(), align_object_size(NoopMaxTLABSize / HeapWordSize));

    // Mark bitmap reserve and initialization(No large page)
    size_t heap_size = heap_rs.size();
    size_t bitmap_size = MarkBitMap::compute_size(heap_size);
    ReservedSpace bitmap_space(bitmap_size);

    _bitmap_region = MemRegion((HeapWord*) (bitmap_space.base()),
			bitmap_space.size() / HeapWordSize);

    _mark_bitmap.initialize(committed_region, _bitmap_region);

    // Install barrier set
    BarrierSet::set_barrier_set(new NoopBarrierSet());

    // Print out the configuration
    NoopInitLogger::print();

    return JNI_OK;
}

void NoopHeap::initialize_serviceability() {
    _pool = new NoopMemoryPool(this);
    _memory_manager.add_pool(_pool);
}

GrowableArray<GCMemoryManager*> NoopHeap::memory_managers() {
    GrowableArray<GCMemoryManager*> memory_managers(1);
    memory_managers.append(&_memory_manager);
    return memory_managers;
}

GrowableArray<MemoryPool*> NoopHeap::memory_pools() {
    GrowableArray<MemoryPool*> memory_pools(1);
    memory_pools.append(_pool);
    return memory_pools;
}

//Main allocation method used in any other allocation method
HeapWord* NoopHeap::allocate_work(size_t size, bool verbose) {
    assert(is_object_aligned(size), "Allocation size should be aligned: " SIZE_FORMAT, size);

    HeapWord* res = NULL;
    while (true) {
        // Try to allocate, assume space is available
        res = _space->par_allocate(size);
        if (res != NULL) {
            break;
        }

        // Allocation failed, attempt expansion, and retry:
        {
            MutexLocker ml(Heap_lock);

            // Try to allocate under the lock, assume another thread was able to expand
            res = _space->par_allocate(size);
            if (res != NULL) {
                break;
            }

            // Expand and loop back if space is available
            size_t space_left = max_capacity() - capacity();
            size_t want_space = MAX2(size, NoopMinHeapExpand);

            if (want_space < space_left) {
                // Enough space to expand in bulk:
                bool expand = _virtual_space.expand_by(want_space);
                assert(expand, "Should be able to expand");
            } else if (size < space_left) {
                // No space to expand in bulk, and this allocation is still possible,
                // take all the remaining space:
                bool expand = _virtual_space.expand_by(space_left);
                assert(expand, "Should be able to expand");
            } else {
                // No space left:
                return NULL;
            }

            _space->set_end((HeapWord *) _virtual_space.high());
        }
    }

    size_t used = _space->used();

    assert(is_object_aligned(res), "Object should be aligned: " PTR_FORMAT, p2i(res));
    return res;
}

HeapWord* NoopHeap::allocate_new_tlab(size_t min_size,
                                         size_t requested_size,
                                         size_t* actual_size) {
    Thread* thread = Thread::current();

    bool fits = true;
    size_t size = requested_size;
    int64_t time = 0;

    // Always honor boundaries
    size = clamp(size, min_size, _max_tlab_size);

    // Always honor alignment
    size = align_up(size, MinObjAlignment);

    // Check that adjustments did not break local and global invariants
    assert(is_object_aligned(size),
           "Size honors object alignment: " SIZE_FORMAT, size);
    assert(min_size <= size,
           "Size honors min size: "  SIZE_FORMAT " <= " SIZE_FORMAT, min_size, size);
    assert(size <= _max_tlab_size,
           "Size honors max size: "  SIZE_FORMAT " <= " SIZE_FORMAT, size, _max_tlab_size);
    assert(size <= CollectedHeap::max_tlab_size(),
           "Size honors global max size: "  SIZE_FORMAT " <= " SIZE_FORMAT, size, CollectedHeap::max_tlab_size());

    // All prepared, let's do it!
    HeapWord* res = allocate_work(size);

    if (res != NULL) {
        // Allocation successful
        *actual_size = size;
    }

    return res;
}

HeapWord* NoopHeap::mem_allocate(size_t size, bool *gc_overhead_limit_was_exceeded) {
    *gc_overhead_limit_was_exceeded = false;
    return allocate_work(size);
}

size_t NoopHeap::unsafe_max_tlab_alloc(Thread* thr) const {
    // Return max allocatable TLAB size, and let allocation path figure out
    // the actual allocation size. Note: result should be in bytes.
    return _max_tlab_size * HeapWordSize;
}

void NoopHeap::collect(GCCause::Cause cause) {
    switch (cause) {
        case GCCause::_metadata_GC_threshold:
        case GCCause::_metadata_GC_clear_soft_refs:
            // Receiving these causes means the VM itself entered the safepoint for metadata collection.
            // While Noop does not do GC, it has to perform sizing adjustments, otherwise we would
            // re-enter the safepoint again very soon.

            assert(SafepointSynchronize::is_at_safepoint(), "Expected at safepoint");
            log_info(gc)("GC request for \"%s\" is handled", GCCause::to_string(cause));
            MetaspaceGC::compute_new_size();
            break;
        default:
            log_info(gc)("GC request for \"%s\" is ignored", GCCause::to_string(cause));
    }
}

void NoopHeap::do_full_collection(bool clear_all_soft_refs) {
    collect(gc_cause());
}
