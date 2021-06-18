/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

#ifndef SHARE_UTILITIES_RESIZEABLERESOURCEHASH_HPP
#define SHARE_UTILITIES_RESIZEABLERESOURCEHASH_HPP

#include "utilities/resourceHash.hpp"

// FIXME: the parameter order of ResourceHashtableBase<> should be fixed. See JDK-XXXXXXX
template<
    typename K, typename V,
    ResourceObj::allocation_type ALLOC_TYPE = ResourceObj::RESOURCE_AREA,
    MEMFLAGS MEM_TYPE = mtInternal,
    unsigned (*HASH)  (K const&)           = primitive_hash<K>,
    bool     (*EQUALS)(K const&, K const&) = primitive_equals<K>
    >
class ResizeableResourceHashtable : public ResourceHashtableBase<
    ResizeableResourceHashtable<K, V, ALLOC_TYPE, MEM_TYPE, HASH, EQUALS>,
    K, V, HASH, EQUALS, ALLOC_TYPE, MEM_TYPE> {
  unsigned _size;
public:
  ResizeableResourceHashtable(unsigned size)
  : ResourceHashtableBase<ResizeableResourceHashtable, K, V, HASH, EQUALS, ALLOC_TYPE, MEM_TYPE>(size),
    _size(size) {}
  unsigned size_impl() const { return _size; }
};

#endif // SHARE_UTILITIES_RESIZEABLERESOURCEHASH_HPP
