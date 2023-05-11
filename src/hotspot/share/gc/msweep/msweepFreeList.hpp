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

#ifndef SHARE_GC_MSWEEP_MSWEEPFREELIST_H
#define SHARE_GC_MSWEEP_MSWEEPFREELIST_H

#include "memory/allocation.hpp"
#include "gc/shared/markBitMap.inline.hpp"

struct MSweepNode: public CHeapObj<mtGC> {
private:
    HeapWord* _start;
    size_t _size;
    MSweepNode* _next;
public:
    MSweepNode(HeapWord* start, size_t size, MSweepNode* next = NULL): _start(start), _size(size), _next(next) {}

    inline HeapWord* start() const { return _start; }

    inline size_t size() const { return _size; }

    inline MSweepNode* next() const { return _next; }

    inline void setNext(MSweepNode* next) { _next = next; }

    inline void setSize(size_t size) { _size = size; }

    inline void setStart(HeapWord* start) { _start = start; }
};

class MSweepFreeList: public CHeapObj<mtGC> {
private:
    MSweepNode* _head;
    MSweepNode* _tail;
    MarkBitMap* _free_chunk_bitmap;
    static const size_t _chunk_size_alignment = 2;
public:
    MSweepFreeList(MSweepNode* head, MarkBitMap* fc);

    void mark(MSweepNode* node);
    void unmark(MSweepNode* node);

    static void link_next(MSweepNode* cur, MSweepNode* next);
    void remove_next(MSweepNode* node, MSweepNode* prev);

    void append(MSweepNode* node);

    static size_t adjust_chunk_size(size_t size);

    //Slice the given node and return a new one
    MSweepNode* slice_node(MSweepNode* node, size_t size, MSweepNode* prev = NULL);
    
    //Pop from list
    MSweepNode* getFirstFit(size_t size);
};

#endif