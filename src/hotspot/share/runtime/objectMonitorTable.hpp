/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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
#include "utilities/sizes.hpp"

template <typename T> class GrowableArray;
class JavaThread;
class ObjectMonitor;
class ObjectMonitorTableConfig;
class outputStream;
class Thread;

class ObjectMonitorTable : AllStatic {
  static constexpr double GROW_LOAD_FACTOR = 0.125;

public:
  class Table;

private:
  static Table* volatile _curr;
  static Table* grow_table(Table* curr);

  enum class Entry : uintptr_t {
    empty = 0,
    tombstone = 1,
    removed = 2,
    below_is_special = (removed + 1)
  };

public:
  typedef enum {
    // Used in the platform code. See: C2_MacroAssembler::fast_lock().
    below_is_special = (int)Entry::below_is_special
  } SpecialPointerValues;

  static void create();
  static ObjectMonitor* monitor_get(Thread* current, oop obj);
  static ObjectMonitor* monitor_put_get(Thread* current, ObjectMonitor* monitor, oop obj);
  static void rebuild(GrowableArray<Table*>* delete_list);
  static void destroy(GrowableArray<Table*>* delete_list);
  static void remove_monitor_entry(Thread* current, ObjectMonitor* monitor);

  // Compiler support
  static address current_table_address();
  static ByteSize table_capacity_mask_offset();
  static ByteSize table_buckets_offset();
};

#endif // SHARE_RUNTIME_OBJECTMONITORTABLE_HPP
