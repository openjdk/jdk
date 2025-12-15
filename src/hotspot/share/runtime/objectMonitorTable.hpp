/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_RUNTIME_OBJECTMONITORTABLE_HPP
#define SHARE_RUNTIME_OBJECTMONITORTABLE_HPP

#include "memory/allStatic.hpp"
#include "oops/oopsHierarchy.hpp"
#include "utilities/globalDefinitions.hpp"

class JavaThread;
class ObjectMonitor;
class ObjectMonitorTableConfig;
class outputStream;
class Thread;

class ObjectMonitorTable : AllStatic {
  friend class ObjectMonitorTableConfig;

 private:
  static void inc_items_count();
  static void dec_items_count();
  static double get_load_factor();
  static size_t table_size(Thread* current);
  static size_t max_log_size();
  static size_t min_log_size();

  template <typename V>
  static size_t clamp_log_size(V log_size);
  static size_t initial_log_size();
  static size_t grow_hint();

 public:
  static void create();
  static void verify_monitor_get_result(oop obj, ObjectMonitor* monitor);
  static ObjectMonitor* monitor_get(Thread* current, oop obj);
  static void try_notify_grow();
  static bool should_shrink() { return false; } // Not implemented

  static constexpr double GROW_LOAD_FACTOR = 0.75;

  static bool should_grow();
  static bool should_resize();

  template <typename Task, typename... Args>
  static bool run_task(JavaThread* current, Task& task, const char* task_name, Args&... args);
  static bool grow(JavaThread* current);
  static bool clean(JavaThread* current);
  static bool resize(JavaThread* current);
  static ObjectMonitor* monitor_put_get(Thread* current, ObjectMonitor* monitor, oop obj);
  static bool remove_monitor_entry(Thread* current, ObjectMonitor* monitor);
  static bool contains_monitor(Thread* current, ObjectMonitor* monitor);
  static void print_on(outputStream* st);
};

#endif // SHARE_RUNTIME_OBJECTMONITORTABLE_HPP
