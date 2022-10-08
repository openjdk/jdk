#include "gc/zero/zeroHeap.hpp"
#include "precompiled.hpp"
#include "memory/universe.hpp"
#include "memory/allocation.hpp"
#include "memory/allocation.inline.hpp"

ZeroHeap* ZeroHeap::heap() {
    return named_heap<ZeroHeap>(CollectedHeap::Zero);
}

jint ZeroHeap::initialize() {
    size_t align = HeapAlignment;
    size_t init_byte_size = align_up(InitialHeapSize, align);
    size_t max_byte_size  = align_up(MaxHeapSize, align);

    // Initializing heap space
    ReservedSpace heap_rs = Universe::reserve_heap(heap_size_in_bytes, align);

    _virtual_space.initialize(heap_rs, init_byte_size);
    MemRegion committed_region((HeapWord*)_virtual_space.low(), (HeapWord*)_virtual_space.high());

    initialize_reserved_region(heap_rs);

    _space = new ContiguousSpace();
    _space->initialize(committed_region, /* clear_space = */ true, /* mangle_space = */ true);

    // Compute constants
    _max_tlab_size = MIN2(CollectedHeap::max_tlab_size(), align_object_size(ZeroMaxTLABSize / HeapWordSize));
    _step_counter_update = MIN2<size_t>(max_byte_size / 16, ZeroUpdateCountersStep);
    _step_heap_print = (ZeroPrintHeapSteps == 0) ? SIZE_MAX : (max_byte_size / ZeroPrintHeapSteps);
    _decay_time_ns = (int64_t) ZeroTLABDecayTime * NANOSECS_PER_MILLISEC;

    // Install barrier set
    BarrierSet::set_barrier_set(new ZeroBarrierSet());

    // Print out the configuration
    ZeroInitLogger::print();

    return JNI_OK;
}

void ZeroHeap::initialize_serviceability() {
    _pool = new ZeroMemoryPool(this);
    _memory_manager.add_pool(_pool);
}

GrowableArray<GCMemoryManager*> ZeroHeap::memory_managers() {
    GrowableArray<GCMemoryManager*> memory_managers(1);
    memory_managers.append(&_memory_manager);
    return memory_managers;
}

GrowableArray<MemoryPool*> ZeroHeap::memory_pools() {
    GrowableArray<MemoryPool*> memory_pools(1);
    memory_pools.append(_pool);
    return memory_pools;
}

HeapWord* ZeroHeap::allocate_work(size_t size, bool verbose) {
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
            size_t want_space = MAX2(size, ZeroMinHeapExpand);

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

    // Print the occupancy line, if needed
    if (verbose) {
        size_t last = _last_heap_print;
        if ((used - last >= _step_heap_print) && Atomic::cmpxchg(&_last_heap_print, last, used) == last) {
            print_heap_info(used);
            print_metaspace_info();
        }
    }

    assert(is_object_aligned(res), "Object should be aligned: " PTR_FORMAT, p2i(res));
    return res;
}

HeapWord* ZeroHeap::allocate_new_tlab(size_t min_size,
                                         size_t requested_size,
                                         size_t* actual_size) {
    Thread* thread = Thread::current();

    // Defaults in case elastic paths are not taken
    bool fits = true;
    size_t size = requested_size;
    size_t ergo_tlab = requested_size;
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

    if (log_is_enabled(Trace, gc)) {
        ResourceMark rm;
        log_trace(gc)("TLAB size for \"%s\" (Requested: " SIZE_FORMAT "K, Min: " SIZE_FORMAT
        "K, Max: " SIZE_FORMAT "K, Ergo: " SIZE_FORMAT "K) -> " SIZE_FORMAT "K",
                thread->name(),
                requested_size * HeapWordSize / K,
                min_size * HeapWordSize / K,
                _max_tlab_size * HeapWordSize / K,
                ergo_tlab * HeapWordSize / K,
                size * HeapWordSize / K);
    }

    // All prepared, let's do it!
    HeapWord* res = allocate_work(size);

    if (res != NULL) {
        // Allocation successful
        *actual_size = size;
    }

    return res;
}

HeapWord* ZeroHeap::mem_allocate(size_t size, bool *gc_overhead_limit_was_exceeded) {
    *gc_overhead_limit_was_exceeded = false;
    return allocate_work(size);
}

size_t ZeroHeap::unsafe_max_tlab_alloc(Thread* thr) const {
    // Return max allocatable TLAB size, and let allocation path figure out
    // the actual allocation size. Note: result should be in bytes.
    return _max_tlab_size * HeapWordSize;
}

void ZeroHeap::collect(GCCause::Cause cause) {
    switch (cause) {
        case GCCause::_metadata_GC_threshold:
        case GCCause::_metadata_GC_clear_soft_refs:
            // Receiving these causes means the VM itself entered the safepoint for metadata collection.
            // While Zero does not do GC, it has to perform sizing adjustments, otherwise we would
            // re-enter the safepoint again very soon.

            assert(SafepointSynchronize::is_at_safepoint(), "Expected at safepoint");
            log_info(gc)("GC request for \"%s\" is handled", GCCause::to_string(cause));
            MetaspaceGC::compute_new_size();
            print_metaspace_info();
            break;
        default:
            log_info(gc)("GC request for \"%s\" is ignored", GCCause::to_string(cause));
    }
}

void ZeroHeap::do_full_collection(bool clear_all_soft_refs) {
    collect(gc_cause());
}