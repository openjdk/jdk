/*
 * Copyright (c) 2023, Kirill Garbar, Red Hat, Inc. All rights reserved.
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

#include "gc/noop/noopFreeList.hpp"
#include "gc/noop/noopInitLogger.hpp"

//Constructor
NoopFreeList::NoopFreeList(NoopNode* head, MarkBitMap* fc): _head(head), _tail(head), _free_chunk_bitmap(fc) {
        this->mark(_head);
}

//Alignment
size_t NoopFreeList::adjust_chunk_size(size_t size) {
    return align_up(size, _chunk_size_alignment);
}

void NoopFreeList::mark(NoopNode* node) {
    _free_chunk_bitmap->mark(node->start());
    _free_chunk_bitmap->mark(node->start() + node->size() - 1);
}

void NoopFreeList::unmark(NoopNode* node) {
    _free_chunk_bitmap->clear(node->start());
    _free_chunk_bitmap->clear(node->start() + node->size() - 1);
}

//Remove and add to list methods
void NoopFreeList::remove_next(NoopNode* node, NoopNode* prev) {
    assert(_free_chunk_bitmap->is_marked(node->start()) 
        && _free_chunk_bitmap->is_marked(node->start() + node->size() - 1),
        "Node is not marked");
    this->unmark(node);

    if (!prev) {
        //Removing head
        _head = NULL;
        _tail = NULL;
    } else {
        NoopNode* newNext = node->next();
        if (!newNext) {
            //Removing tail
            _tail = prev;
        }
        prev->setNext(newNext);
    }
}

void NoopFreeList::link_next(NoopNode* cur, NoopNode* next) {
    NoopNode* old_next = cur->next();
    cur->setNext(next);
    next->setNext(old_next);
}

void NoopFreeList::append(NoopNode* node) {
    assert(is_aligned(node->size(), _chunk_size_alignment), "Chunk size is not aligned");
    if (_tail != NULL) { 
        _tail->setNext(node); 
    } else {
        //List was empty
        _head = node;
    }
    _tail = node;
    this->mark(node);
}

//Returns new node and resizes old one
NoopNode* NoopFreeList::slice_node(NoopNode* node, size_t size, NoopNode* prev) {
    assert(is_aligned(size, _chunk_size_alignment), "Chunk size is not aligned");
    size_t old_size = node->size();
    size_t remainder_size = old_size - size;

    if (remainder_size > 0) {
        node->setSize(remainder_size);
        
        NoopNode* res = new NoopNode(node->start() + remainder_size, size);

        _free_chunk_bitmap->clear(node->start() + old_size - 1);
        _free_chunk_bitmap->mark(node->start() + remainder_size - 1);

        return res;
    } else {
        //Remove given node from list and return it
        this->remove_next(node, prev);
        return node;
    }
}

//First fit
NoopNode* NoopFreeList::getFirstFit(size_t size) {
    if (_head == NULL) { return NULL; }
    NoopNode* fitting_node = _head;
    size_t desired_size = adjust_chunk_size(size);

    if (fitting_node->size() >= desired_size) {
        NoopNode* res = slice_node(fitting_node, desired_size);
        log_info(gc)("List _head size %li", _head->size());
        return res;
    }

    //Cut next node that fits, or remove it with remove_next
    while (fitting_node->next() != NULL) {
        if (fitting_node->next()->size() >= desired_size) { 
            //Node is found, get new node of needed size
            NoopNode* res = slice_node(fitting_node->next(), desired_size, fitting_node);
            ///assert(_head != NULL, "New head is null");
            return res;
        }
        fitting_node = fitting_node->next();
    }

    return NULL;
}
