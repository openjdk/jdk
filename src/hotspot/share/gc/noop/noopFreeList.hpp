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

#ifndef SHARE_GC_NOOP_NOOPFREELIST_H
#define SHARE_GC_NOOP_NOOPFREELIST_H

#include "memory/allocation.hpp"
#include "gc/shared/markBitMap.inline.hpp"

struct NoopNode: public CHeapObj<mtGC> {
private:
    HeapWord* _start;
    size_t _size;
    NoopNode* _next;
public:
    NoopNode(HeapWord* start, size_t size, NoopNode* next = NULL): _start(start), _size(size), _next(next) {}

    inline HeapWord* start() const { return _start; }

    inline size_t size() const { return _size; }

    inline NoopNode* next() const { return _next; }

    inline void setNext(NoopNode* next) { _next = next; }

    inline void setSize(size_t size) { _size = size; }

    inline void setStart(HeapWord* start) { _start = start; }
};

class NoopFreeList: public CHeapObj<mtGC> {
private:
    NoopNode* _head;
    NoopNode* _tail;
    MarkBitMap* _free_chunk_bitmap;
    static const size_t _chunk_size_alignment = 2;
public:
    NoopFreeList(NoopNode* head, MarkBitMap* fc);

    void mark(NoopNode* node);
    void unmark(NoopNode* node);

    static void link_next(NoopNode* cur, NoopNode* next);
    void remove_next(NoopNode* node, NoopNode* prev);

    void append(NoopNode* node);

    static size_t adjust_chunk_size(size_t size);

    //Slice the given node and return a new one
    NoopNode* slice_node(NoopNode* node, size_t size, NoopNode* prev = NULL);
    
    //Pop from list
    NoopNode* getFirstFit(size_t size);
};

#endif