/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZRELOCATIONSET_HPP
#define SHARE_GC_Z_ZRELOCATIONSET_HPP

#include "gc/z/zArray.hpp"
#include "gc/z/zForwardingAllocator.hpp"
#include "gc/z/zLock.hpp"

class ZForwarding;
class ZGeneration;
class ZPage;
class ZPageAllocator;
class ZRelocationSetSelector;
class ZWorkers;

class ZRelocationSet {
  template <bool> friend class ZRelocationSetIteratorImpl;

private:
  ZGeneration*         _generation;
  ZForwardingAllocator _allocator;
  ZForwarding**        _forwardings;
  size_t               _nforwardings;
  ZLock                _promotion_lock;
  ZArray<ZPage*>       _flip_promoted_pages;
  ZArray<ZPage*>       _in_place_relocate_promoted_pages;

  ZWorkers* workers() const;

public:
  ZRelocationSet(ZGeneration* generation);

  void install(const ZRelocationSetSelector* selector);
  void reset(ZPageAllocator* page_allocator);
  ZGeneration* generation() const;
  ZArray<ZPage*>* flip_promoted_pages();

  void register_flip_promoted(const ZArray<ZPage*>& pages);
  void register_in_place_relocate_promoted(ZPage* page);
};

template <bool Parallel>
class ZRelocationSetIteratorImpl : public ZArrayIteratorImpl<ZForwarding*, Parallel> {
public:
  ZRelocationSetIteratorImpl(ZRelocationSet* relocation_set);
};

using ZRelocationSetIterator = ZRelocationSetIteratorImpl<false /* Parallel */>;
using ZRelocationSetParallelIterator = ZRelocationSetIteratorImpl<true /* Parallel */>;

#endif // SHARE_GC_Z_ZRELOCATIONSET_HPP
