/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZHEAPITERATOR_HPP
#define SHARE_GC_Z_ZHEAPITERATOR_HPP

#include "gc/z/zAddressRangeMap.hpp"
#include "gc/z/zGlobals.hpp"
#include "memory/allocation.hpp"
#include "utilities/stack.hpp"

class ZHeapIteratorBitMap;

class ZHeapIterator : public StackObj {
  friend class ZHeapIteratorRootOopClosure;
  friend class ZHeapIteratorOopClosure;

private:
  typedef ZAddressRangeMap<ZHeapIteratorBitMap*, ZPageSizeMinShift>         ZVisitMap;
  typedef ZAddressRangeMapIterator<ZHeapIteratorBitMap*, ZPageSizeMinShift> ZVisitMapIterator;
  typedef Stack<oop, mtGC>                                                  ZVisitStack;

  ZVisitStack _visit_stack;
  ZVisitMap   _visit_map;
  const bool  _visit_referents;

  ZHeapIteratorBitMap* object_map(oop obj);
  void push(oop obj);

public:
  ZHeapIterator(bool visit_referents);
  ~ZHeapIterator();

  void objects_do(ObjectClosure* cl);
};

#endif // SHARE_GC_Z_ZHEAPITERATOR_HPP
