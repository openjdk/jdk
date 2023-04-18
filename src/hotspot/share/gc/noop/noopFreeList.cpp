#include "gc/noop/noopFreeList.hpp"
#include "gc/noop/noopInitLogger.hpp"

//Constructor
NoopFreeList::NoopFreeList(NoopNode* head, MarkBitMap* fc): _head(head), _free_chunk_bitmap(fc) {
        fc->mark(head->start());
        fc->mark(head->start() + _head->size() - 1);
}

//Alignment
size_t NoopFreeList::adjust_chunk_size(size_t size) {
    return align_up(size, _chunk_size_alignment);
}

//Remove and add to list methods
void NoopFreeList::remove_next(NoopNode* node) {
    

    assert(_free_chunk_bitmap->is_marked(node->start()) 
        && _free_chunk_bitmap->is_marked(node->start() + node->size() - 1),
        "Node is not marked");
    _free_chunk_bitmap->clear(node->start());
    _free_chunk_bitmap->clear(node->start() + node->size() - 1);
}

void NoopFreeList::link_next(NoopNode* cur, NoopNode* next) {
    NoopNode* old_next = cur->next();
    cur->setNext(next);
    next->setNext(old_next);
}

void NoopFreeList::append(NoopNode* node) {
    assert(is_aligned(node->size(), _chunk_size_alignment), "Chunk size is not aligned");
}

//Returns new node and resizes old one
NoopNode* NoopFreeList::slice_node(NoopNode* node, size_t size) {
    assert(is_aligned(size, _chunk_size_alignment), "Chunk size is not aligned");
    size_t old_size = node->size();
    size_t remainder_size = old_size - size;

    if (remainder_size > 0) {
        node->setSize(remainder_size);
        
        NoopNode* res = new NoopNode(node->start() + remainder_size, size);

        //Linking node to old one
        //link_next(node, res);
        
        //Marking new nodes
        //_free_chunk_bitmap->mark(res->start());
        _free_chunk_bitmap->clear(node->start() + old_size - 1);
        _free_chunk_bitmap->mark(node->start() + remainder_size - 1);
        return res;
    } else {
        assert(false, "Should not reach here while some memory left");
        return node;
    }
}

//First fit
NoopNode* NoopFreeList::getFirstFit(size_t size) {
    if (_head == NULL) { return NULL; }
    NoopNode* fitting_node = _head;
    size_t desired_size = adjust_chunk_size(size);

    while (fitting_node->size() < desired_size) {
        if (fitting_node->next() == NULL) { 
            return NULL; 
        }
        fitting_node = fitting_node->next();
    }
    //Node is found, get new node of needed size
    NoopNode* res = slice_node(fitting_node, desired_size);

    assert(_head != NULL, "New head is null");

    return res;
}
