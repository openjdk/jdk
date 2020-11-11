/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1FULLCOLLECTOR_INLINE_HPP
#define SHARE_GC_G1_G1FULLCOLLECTOR_INLINE_HPP

#include "gc/g1/g1FullCollector.hpp"
#include "gc/g1/g1FullGCHeapRegionAttr.hpp"
#include "oops/oopsHierarchy.hpp"

bool G1FullCollector::is_in_pinned_or_closed(oop obj) const {
  return _region_attr_table.is_pinned_or_closed(cast_from_oop<HeapWord*>(obj));
}

bool G1FullCollector::is_in_pinned(oop obj) const {
  return _region_attr_table.is_pinned(cast_from_oop<HeapWord*>(obj));
}

bool G1FullCollector::is_in_closed(oop obj) const {
  return _region_attr_table.is_closed_archive(cast_from_oop<HeapWord*>(obj));
}

#endif // SHARE_GC_G1_G1FULLCOLLECTOR_INLINE_HPP

