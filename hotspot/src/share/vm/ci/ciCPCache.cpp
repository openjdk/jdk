/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

#include "incls/_precompiled.incl"
#include "incls/_ciCPCache.cpp.incl"

// ciCPCache

// ------------------------------------------------------------------
// ciCPCache::get_f1_offset
size_t ciCPCache::get_f1_offset(int index) {
  // Calculate the offset from the constantPoolCacheOop to the f1
  // field.
  ByteSize f1_offset =
    constantPoolCacheOopDesc::entry_offset(index) +
    ConstantPoolCacheEntry::f1_offset();

  return in_bytes(f1_offset);
}

// ------------------------------------------------------------------
// ciCPCache::print
//
// Print debugging information about the cache.
void ciCPCache::print() {
  Unimplemented();
}
