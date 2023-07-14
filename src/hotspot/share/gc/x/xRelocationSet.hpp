/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_X_XRELOCATIONSET_HPP
#define SHARE_GC_X_XRELOCATIONSET_HPP

#include "gc/x/xArray.hpp"
#include "gc/x/xForwardingAllocator.hpp"

class XForwarding;
class XRelocationSetSelector;
class XWorkers;

class XRelocationSet {
  template <bool> friend class XRelocationSetIteratorImpl;

private:
  XWorkers*            _workers;
  XForwardingAllocator _allocator;
  XForwarding**        _forwardings;
  size_t               _nforwardings;

public:
  XRelocationSet(XWorkers* workers);

  void install(const XRelocationSetSelector* selector);
  void reset();
};

template <bool Parallel>
class XRelocationSetIteratorImpl : public XArrayIteratorImpl<XForwarding*, Parallel> {
public:
  XRelocationSetIteratorImpl(XRelocationSet* relocation_set);
};

using XRelocationSetIterator = XRelocationSetIteratorImpl<false /* Parallel */>;
using XRelocationSetParallelIterator = XRelocationSetIteratorImpl<true /* Parallel */>;

#endif // SHARE_GC_X_XRELOCATIONSET_HPP
