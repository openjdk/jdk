/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_X_XPAGECACHE_HPP
#define SHARE_GC_X_XPAGECACHE_HPP

#include "gc/x/xList.hpp"
#include "gc/x/xPage.hpp"
#include "gc/x/xValue.hpp"

class XPageCacheFlushClosure;

class XPageCache {
private:
  XPerNUMA<XList<XPage> > _small;
  XList<XPage>            _medium;
  XList<XPage>            _large;
  uint64_t                _last_commit;

  XPage* alloc_small_page();
  XPage* alloc_medium_page();
  XPage* alloc_large_page(size_t size);

  XPage* alloc_oversized_medium_page(size_t size);
  XPage* alloc_oversized_large_page(size_t size);
  XPage* alloc_oversized_page(size_t size);

  bool flush_list_inner(XPageCacheFlushClosure* cl, XList<XPage>* from, XList<XPage>* to);
  void flush_list(XPageCacheFlushClosure* cl, XList<XPage>* from, XList<XPage>* to);
  void flush_per_numa_lists(XPageCacheFlushClosure* cl, XPerNUMA<XList<XPage> >* from, XList<XPage>* to);
  void flush(XPageCacheFlushClosure* cl, XList<XPage>* to);

public:
  XPageCache();

  XPage* alloc_page(uint8_t type, size_t size);
  void free_page(XPage* page);

  void flush_for_allocation(size_t requested, XList<XPage>* to);
  size_t flush_for_uncommit(size_t requested, XList<XPage>* to, uint64_t* timeout);

  void set_last_commit();

  void pages_do(XPageClosure* cl) const;
};

#endif // SHARE_GC_X_XPAGECACHE_HPP
