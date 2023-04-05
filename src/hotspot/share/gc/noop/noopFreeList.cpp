#include "gc/noop/noopFreeList.hpp"
#include "gc/noop/noopInitLogger.hpp"

//Constructor
NoopFreeList::NoopFreeList(NoopNode* head, MarkBitMap* fc): _head(head), _tail(head), _free_chunk_bitmap(fc) {
        //_free_chunk_bitmap->mark(_head->start());
        //_free_chunk_bitmap->mark(_head->start() + _head->size() - 1);
}

//Alignment
size_t NoopFreeList::adjust_chunk_size(size_t size) {
    return align_up(size, _chunk_size_alignment);
}

//Remove and add to list methods
void NoopFreeList::unlink(NoopNode* node) {
    if (node->prev() != NULL) { node->prev()->setNext(node->next()); }
    else { //HEAD
        _head = node->next();
    }
    if (node->next() != NULL) { node->next()->setPrev(node->prev()); }

    node->setNext(NULL);
    node->setPrev(NULL);
}

void NoopFreeList::link_next(NoopNode* cur, NoopNode* next) {
    NoopNode* prev_next = cur->next(); //NULL
    
    cur->setNext(next);
    next->setNext(prev_next);// rem->next = NULL
    next->setPrev(cur); //rem->prev = cur

    if (prev_next != NULL) { prev_next->setPrev(next); }
}

void NoopFreeList::append(NoopNode* node) {
    assert(is_aligned(node->size(), _chunk_size_alignment), "Chunk size is not aligned");

    _tail->setNext(node);
    node->setPrev(_tail);

    _tail = node;
}

//Node slicing
void NoopFreeList::slice_node(NoopNode* node, size_t size) {
    assert(is_aligned(size, _chunk_size_alignment), "Chunk size is not aligned");
    size_t old_size = node->size();
    size_t remainder_size = old_size - size;

    if (remainder_size > 0) {
        node->setSize(size);
        
        NoopNode* remainder = new NoopNode(node->start() + size, remainder_size);

        //Marking new nodes
        //_free_chunk_bitmap->mark(node->start() + node->size() - 1);
        //_free_chunk_bitmap->mark(remainder->start());

        link_next(node, remainder);
    }
}

//First fit
NoopNode* NoopFreeList::getFirstFit(size_t size) {
    log_info(gc)("_head is %li", (long)_head->start());
    if (_head == NULL) { return NULL; }
    NoopNode* res = _head;
    size_t desired_size = adjust_chunk_size(size);

    log_info(gc)("Desired size %li", desired_size);

    while (res->size() < desired_size) {
        if (res->next() == NULL) { 
            return NULL; 
        }
        res = res->next();
    }
    //Node is found, slice to needed size and append remainder
    slice_node(res, desired_size);

    unlink(res);

    assert(_head != NULL, "New head is null");

    return res;
}
