#ifndef SHARE_GC_ZERO_ZEROMEMORYPOOL_H
#define SHARE_GC_ZERO_ZEROMEMORYPOOL_H


#include "gc/zero/zeroHeap.hpp"
#include "services/memoryPool.hpp"
#include "services/memoryUsage.hpp"
#include "utilities/macros.hpp"

class ZeroMemoryPool : public CollectedMemoryPool {
private:
    ZeroHeap* _heap;

public:
    ZeroMemoryPool(ZeroHeap* heap);
    size_t committed_in_bytes() { return _heap->capacity();     }
    size_t used_in_bytes()      { return _heap->used();         }
    size_t max_size()     const { return _heap->max_capacity(); }
    MemoryUsage get_memory_usage();
};


#endif //SHARE_GC_ZERO_ZEROMEMORYPOOL_H
