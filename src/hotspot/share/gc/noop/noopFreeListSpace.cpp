#include "gc/noop/noopFreeListSpace.hpp"
#include "gc/noop/noopInitLogger.hpp"

void NoopFreeListSpace::initialize(MemRegion mr, bool clear_space, bool mangle_space) {
    CompactibleSpace::initialize(mr, clear_space, mangle_space);

    //Free list
    //All memory region is a node at first except for 1 word if heap size is odd
    NoopNode* firstNode = new NoopNode(mr.start(), NoopFreeList::adjust_chunk_size(mr.word_size()));
    _free_list = new NoopFreeList(firstNode, _free_chunk_bitmap);
}

HeapWord* NoopFreeListSpace::allocate(size_t size) {
    log_info(gc)("Allocation request");
    return _free_list->getFirstFit(size)->start();
}