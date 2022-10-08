#include "precompiled.hpp"
#include "gc/zero/zeroHeap.hpp"
#include "gc/zero/zeroMemoryPool.hpp"

ZeroMemoryPool::ZeroMemoryPool(ZeroHeap* heap) :
        CollectedMemoryPool("Zero Heap",
                            heap->capacity(),
                            heap->max_capacity(),
                            false),
        _heap(heap) {
}

MemoryUsage ZeroMemoryPool::get_memory_usage() {
    size_t initial_size = initial_size();
    size_t max_sz     = max_size();
    size_t used       = used_in_bytes();
    size_t committed  = committed_in_bytes();

    return MemoryUsage(initial_size, used, committed, max_sz);
}