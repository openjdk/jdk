/*
 * Copyright (c) 2022, Red Hat, Inc. All rights reserved.
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
    size_t _size = 0;
    NoopNode* _next;
public:
    NoopNode(HeapWord* start, size_t size, NoopNode* next = nullptr): _start(start), _size(size), _next(next) {}

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
    MarkBitMap* _free_chunk_bitmap;
    static const size_t _chunk_size_alignment = 2;
public:
    NoopFreeList(NoopNode* head, MarkBitMap* fc);

    static void link_next(NoopNode* cur, NoopNode* next);
    void remove_next(NoopNode* NoopNode);

    void append(NoopNode* NoopNode);

    static size_t adjust_chunk_size(size_t size);

    NoopNode* slice_node(NoopNode* node, size_t size);
    
    //Pop from list
    NoopNode* getFirstFit(size_t size);
};

#endif