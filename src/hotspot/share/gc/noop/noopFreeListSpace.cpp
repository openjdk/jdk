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
    NoopNode* resNode = _free_list->getFirstFit(size);
    HeapWord* res = resNode->start();
    delete resNode;

    return res;
}