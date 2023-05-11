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

#include "gc/msweep/msweepFreeListSpace.hpp"
#include "gc/msweep/msweepInitLogger.hpp"

void MSweepFreeListSpace::initialize(MemRegion mr, bool clear_space, bool mangle_space) {
    CompactibleSpace::initialize(mr, clear_space, mangle_space);

    //Free list
    //All memory region is a node at first except for 1 word if heap size is odd
    MSweepNode* firstNode = new MSweepNode(mr.start(), MSweepFreeList::adjust_chunk_size(mr.word_size()));
    _free_list = new MSweepFreeList(firstNode, _free_chunk_bitmap);
}

HeapWord* MSweepFreeListSpace::allocate(size_t size) {
    MSweepNode* resNode = _free_list->getFirstFit(size);
    if (resNode) {
        HeapWord* res = resNode->start();
        delete resNode;

        return res;
    }
    
    return NULL;
}

bool MSweepFreeListSpace::is_oop(HeapWord* addr) {
    if (_free_chunk_bitmap->is_marked(addr)) return false;

    Klass* k = cast_to_oop(addr)->klass_or_null_acquire();
    if (k != NULL) {
        assert(oopDesc::is_oop(cast_to_oop(addr), true), "Should be an oop");
        return true;
    } else {
        log_info(gc)("Not an object and not free block!");
        return false;
    }
}

void MSweepFreeListSpace::object_iterate(ObjectClosure* blk) {
    HeapWord* obj_addr = bottom();
    HeapWord* t = end();

    HeapWord* last = 0;

    while (obj_addr < t && obj_addr != last)
    {
        //log_info(gc)("Obj_addr: %li", (size_t)obj_addr);
        if (is_oop(obj_addr)) {
            oop obj = cast_to_oop(obj_addr);
            size_t size = MSweepFreeList::adjust_chunk_size(obj->size());
            blk->do_object(obj);
            last = obj_addr;
            obj_addr += size;
        } else {
            //Skip free block
            last = obj_addr;
            obj_addr = _free_chunk_bitmap->get_next_marked_addr(obj_addr + 1, t) + 1;
        }
        
    }
}
