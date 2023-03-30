#include "gc/noop/noopFreeListSpace.hpp"


void NoopFreeListSpace::initialize(MemRegion mr, bool clear_space, bool mangle_space, MarkBitMap* fc_bitmap) {
    CompactibleSpace::initialize(mr, clear_space, mangle_space);

    //Free list
    //All memory region is a node at first except for 1 word if heap size is odd
    Node* firstNode = new Node(mr.start(), NoopFreeList::adjust_chunk_size(mr.word_size()));
    _free_list = new NoopFreeList(firstNode, fc_bitmap);
}

HeapWord* NoopFreeListSpace::allocate(size_t size) {
    return _free_list->getFirstFit(size)->start();
}