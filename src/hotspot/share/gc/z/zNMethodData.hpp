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

#ifndef SHARE_GC_Z_ZNMETHODDATA_HPP
#define SHARE_GC_Z_ZNMETHODDATA_HPP

#include "gc/z/zArray.hpp"
#include "gc/z/zLock.hpp"
#include "memory/allocation.hpp"
#include "oops/oopsHierarchy.hpp"
#include "utilities/globalDefinitions.hpp"

struct ZNMethodDataBarrier {
  address _reloc_addr;
  int     _reloc_format;
};

class ZNMethodData : public CHeapObj<mtGC> {
private:
  ZReentrantLock              _lock;
  ZReentrantLock              _ic_lock;
  ZArray<ZNMethodDataBarrier> _barriers;
  ZArray<oop*>                _immediate_oops;
  bool                        _has_non_immediate_oops;

public:
  ZNMethodData();

  ZReentrantLock* lock();
  ZReentrantLock* ic_lock();

  const ZArray<ZNMethodDataBarrier>* barriers() const;
  const ZArray<oop*>* immediate_oops() const;
  bool has_non_immediate_oops() const;

  void swap(ZArray<ZNMethodDataBarrier>* barriers,
            ZArray<oop*>* immediate_oops,
            bool has_non_immediate_oops);
};

#endif // SHARE_GC_Z_ZNMETHODDATA_HPP
