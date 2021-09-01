/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

#ifndef SHARE_GC_SHARED_PRETOUCH_HPP
#define SHARE_GC_SHARED_PRETOUCH_HPP

#include "gc/shared/workgroup.hpp"
#include "utilities/globalDefinitions.hpp"

class BasicTouchTask : public AbstractGangTask {
  char* volatile _cur;
  void* const _end;
  size_t const _page_size;
  size_t const _chunk_size;

  NONCOPYABLE(BasicTouchTask);

protected:
  BasicTouchTask(const char* name, void* start, void* end, size_t page_size);
  ~BasicTouchTask() = default;

  virtual void do_touch(void* start, void* end, size_t page_size) = 0;

  void touch_impl(WorkGang* gang);

public:
  void work(uint worker_id) override;
};

class PretouchTask : public BasicTouchTask {
  PretouchTask(const char* name, void* start, void* end, size_t page_size);

  void do_touch(void* start, void* end, size_t page_size) override;

public:
  static void pretouch(const char* task_name,
                       void* start,
                       void* end,
                       size_t page_size,
                       WorkGang* pretouch_gang);
};

class TouchTask : public BasicTouchTask {
  TouchTask(const char* name, void* start, void* end, size_t page_size);

  void do_touch(void* start, void* end, size_t page_size) override;

public:
  static void touch(const char* task_name,
                    void* start,
                    void* end,
                    size_t page_size,
                    WorkGang* touch_gang);
};

#endif // SHARE_GC_SHARED_PRETOUCH_HPP
