#ifndef SHARE_GC_NOOP_NOOPMEMORYPOOL_H
#define SHARE_GC_NOOP_NOOPMEMORYPOOL_H


#include "gc/noop/noopHeap.hpp"
#include "services/memoryPool.hpp"
#include "services/memoryUsage.hpp"
#include "utilities/macros.hpp"

class NoopMemoryPool : public CollectedMemoryPool {
private:
    NoopHeap* _heap;

public:
    NoopMemoryPool(NoopHeap* heap);
    size_t committed_in_bytes() { return _heap->capacity();     }
    size_t used_in_bytes()      { return _heap->used();         }
    size_t max_size()     const { return _heap->max_capacity(); }
    MemoryUsage get_memory_usage();
};


#endif //SHARE_GC_NOOP_NOOPMEMORYPOOL_H
