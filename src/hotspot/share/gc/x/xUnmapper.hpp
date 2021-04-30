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
 */

#ifndef SHARE_GC_X_XUNMAPPER_HPP
#define SHARE_GC_X_XUNMAPPER_HPP

#include "gc/shared/concurrentGCThread.hpp"
#include "gc/x/xList.hpp"
#include "gc/x/xLock.hpp"

class XPage;
class XPageAllocator;

class XUnmapper : public ConcurrentGCThread {
private:
  XPageAllocator* const _page_allocator;
  XConditionLock        _lock;
  XList<XPage>          _queue;
  bool                  _stop;

  XPage* dequeue();
  void do_unmap_and_destroy_page(XPage* page) const;

protected:
  virtual void run_service();
  virtual void stop_service();

public:
  XUnmapper(XPageAllocator* page_allocator);

  void unmap_and_destroy_page(XPage* page);
};

#endif // SHARE_GC_X_XUNMAPPER_HPP
