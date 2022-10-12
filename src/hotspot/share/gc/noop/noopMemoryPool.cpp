#include "precompiled.hpp"
#include "gc/noop/noopHeap.hpp"
#include "gc/noop/noopMemoryPool.hpp"

NoopMemoryPool::NoopMemoryPool(NoopHeap* heap) :
        CollectedMemoryPool("Noop Heap",
                            heap->capacity(),
                            heap->max_capacity(),
                            false),
        _heap(heap) {
}

MemoryUsage NoopMemoryPool::get_memory_usage() {
    size_t initial_size = initial_size();
    size_t max_sz     = max_size();
    size_t used       = used_in_bytes();
    size_t committed  = committed_in_bytes();

    return MemoryUsage(initial_size, used, committed, max_sz);
}