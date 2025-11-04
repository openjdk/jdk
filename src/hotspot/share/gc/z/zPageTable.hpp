/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZPAGETABLE_HPP
#define SHARE_GC_Z_ZPAGETABLE_HPP

#include "gc/z/zGenerationId.hpp"
#include "gc/z/zGranuleMap.hpp"
#include "gc/z/zIndexDistributor.hpp"
#include "memory/allocation.hpp"

class ZPage;
class ZPageAllocator;
class ZPageTable;

class ZPageTable {
  friend class ZPageTableIterator;
  friend class ZPageTableParallelIterator;
  friend class ZRemsetTableIterator;
  friend class VMStructs;

private:
  ZGranuleMap<ZPage*> _map;

public:
  ZPageTable();

  int count() const;

  ZPage* get(zaddress addr) const;
  ZPage* get(volatile zpointer* p) const;

  ZPage* at(size_t index) const;

  void insert(ZPage* page);
  void remove(ZPage* page);
  void replace(ZPage* old_page, ZPage* new_page);
};

class ZPageTableIterator : public StackObj {
private:
  ZGranuleMapIterator<ZPage*, false /* Parallel */> _iter;
  ZPage*                                            _prev;

public:
  ZPageTableIterator(const ZPageTable* table);

  bool next(ZPage** page);
};

class ZPageTableParallelIterator : public StackObj {
  const ZPageTable* _table;
  ZIndexDistributor _index_distributor;

public:
  ZPageTableParallelIterator(const ZPageTable* table);

  template <typename Function>
  void do_pages(Function function);
};

class ZGenerationPagesIterator : public StackObj {
private:
  ZPageTableIterator _iterator;
  ZGenerationId      _generation_id;
  ZPageAllocator*    _page_allocator;

public:
  ZGenerationPagesIterator(const ZPageTable* page_table, ZGenerationId id, ZPageAllocator* page_allocator);
  ~ZGenerationPagesIterator();

  bool next(ZPage** page);

  template <typename Function>
  void yield(Function function);
};

class ZGenerationPagesParallelIterator : public StackObj {
private:
  ZPageTableParallelIterator _iterator;
  ZGenerationId              _generation_id;
  ZPageAllocator*            _page_allocator;

public:
  ZGenerationPagesParallelIterator(const ZPageTable* page_table, ZGenerationId id, ZPageAllocator* page_allocator);
  ~ZGenerationPagesParallelIterator();

  template <typename Function>
  void do_pages(Function function);
};

#endif // SHARE_GC_Z_ZPAGETABLE_HPP
