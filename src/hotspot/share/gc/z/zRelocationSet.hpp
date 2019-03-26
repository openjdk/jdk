/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

#include "memory/allocation.hpp"

class ZForwarding;
class ZPage;

class ZRelocationSet {
  template <bool> friend class ZRelocationSetIteratorImpl;

private:
  ZForwarding** _forwardings;
  size_t        _nforwardings;

public:
  ZRelocationSet();

  void populate(ZPage* const* group0, size_t ngroup0,
                ZPage* const* group1, size_t ngroup1);
  void reset();
};

template <bool parallel>
class ZRelocationSetIteratorImpl : public StackObj {
private:
  ZRelocationSet* const _relocation_set;
  size_t                _next;

public:
  ZRelocationSetIteratorImpl(ZRelocationSet* relocation_set);

  bool next(ZForwarding** forwarding);
};

// Iterator types
#define ZRELOCATIONSET_SERIAL      false
#define ZRELOCATIONSET_PARALLEL    true

class ZRelocationSetIterator : public ZRelocationSetIteratorImpl<ZRELOCATIONSET_SERIAL> {
public:
  ZRelocationSetIterator(ZRelocationSet* relocation_set) :
      ZRelocationSetIteratorImpl<ZRELOCATIONSET_SERIAL>(relocation_set) {}
};

class ZRelocationSetParallelIterator : public ZRelocationSetIteratorImpl<ZRELOCATIONSET_PARALLEL> {
public:
  ZRelocationSetParallelIterator(ZRelocationSet* relocation_set) :
      ZRelocationSetIteratorImpl<ZRELOCATIONSET_PARALLEL>(relocation_set) {}
};

#endif // SHARE_GC_Z_ZRELOCATIONSET_HPP
