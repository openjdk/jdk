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

#include "gc/z/zLock.hpp"
#include "oops/oopsHierarchy.hpp"
#include "utilities/globalDefinitions.hpp"

#ifndef SHARE_GC_Z_ZNMETHODDATA_HPP
#define SHARE_GC_Z_ZNMETHODDATA_HPP

class nmethod;
template <typename T> class GrowableArray;

class ZNMethodDataOops {
private:
  const size_t _nimmediates;
  bool         _has_non_immediates;

  static size_t header_size();

  ZNMethodDataOops(const GrowableArray<oop*>& immediates, bool has_non_immediates);

public:
  static ZNMethodDataOops* create(const GrowableArray<oop*>& immediates, bool has_non_immediates);
  static void destroy(ZNMethodDataOops* oops);

  size_t immediates_count() const;
  oop** immediates_begin() const;
  oop** immediates_end() const;

  bool has_non_immediates() const;
};

class ZNMethodData {
private:
  ZReentrantLock             _lock;
  ZNMethodDataOops* volatile _oops;

  ZNMethodData(nmethod* nm);

public:
  static ZNMethodData* create(nmethod* nm);
  static void destroy(ZNMethodData* data);

  ZReentrantLock* lock();

  ZNMethodDataOops* oops() const;
  ZNMethodDataOops* swap_oops(ZNMethodDataOops* oops);
};

#endif // SHARE_GC_Z_ZNMETHODDATA_HPP
