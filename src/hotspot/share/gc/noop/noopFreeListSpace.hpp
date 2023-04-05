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

#ifndef SHARE_GC_NOOP_NOOPFREELISTSPACE_H
#define SHARE_GC_NOOP_NOOPFREELISTSPACE_H

#include "gc/shared/space.hpp"
#include "gc/noop/noopFreeList.hpp"

class NoopFreeListSpace: public CompactibleSpace {
    friend class VMStructs;
private:
    MarkBitMap* _free_chunk_bitmap;
    NoopFreeList* _free_list;
public:

    NoopFreeListSpace(MarkBitMap* bitmap): _free_chunk_bitmap(bitmap), _free_list(NULL) {}

    //Useless virtual methods
    virtual MemRegion used_region() const { return MemRegion(bottom(), end()); }
    virtual void mangle_unused_area() {}
    virtual void mangle_unused_area_complete() {}
    virtual size_t used() const            { return 0; }
    virtual size_t free() const            { return 1; }
    virtual void verify() const {}
    virtual void reset_after_compaction() {}
    virtual void prepare_for_compaction(CompactPoint* cp) {}
    virtual HeapWord* block_start_const(const void* p) const { return (HeapWord*)0; }
    virtual size_t block_size(const HeapWord* addr) const { return 0; }

    //Space walking
    virtual void object_iterate(ObjectClosure* blk) {}
    virtual bool block_is_obj(const HeapWord* addr) const { return true; }
    virtual bool is_free_block(const HeapWord* p) const { return false; }
    virtual HeapWord* par_allocate(size_t word_size) { return bottom(); }

    using CompactibleSpace::initialize;

    virtual void initialize(MemRegion mr, bool clear_space, bool mangle_space);

    virtual HeapWord* allocate(size_t size);

    //Min node size

    //GC support
        //Iteration

};

#endif