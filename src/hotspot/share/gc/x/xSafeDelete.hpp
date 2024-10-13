/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_X_XSAFEDELETE_HPP
#define SHARE_GC_X_XSAFEDELETE_HPP

#include "gc/x/xArray.hpp"
#include "gc/x/xLock.hpp"

#include <type_traits>

template <typename T>
class XSafeDeleteImpl {
private:
  using ItemT = std::remove_extent_t<T>;

  XLock*         _lock;
  uint64_t       _enabled;
  XArray<ItemT*> _deferred;

  bool deferred_delete(ItemT* item);
  void immediate_delete(ItemT* item);

public:
  XSafeDeleteImpl(XLock* lock);

  void enable_deferred_delete();
  void disable_deferred_delete();

  void operator()(ItemT* item);
};

template <typename T>
class XSafeDelete : public XSafeDeleteImpl<T> {
private:
  XLock _lock;

public:
  XSafeDelete();
};

template <typename T>
class XSafeDeleteNoLock : public XSafeDeleteImpl<T> {
public:
  XSafeDeleteNoLock();
};

#endif // SHARE_GC_X_XSAFEDELETE_HPP
