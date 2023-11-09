/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_SERVICES_NMT_MEMORYLOGRECORDER_HPP
#define SHARE_SERVICES_NMT_MEMORYLOGRECORDER_HPP

#include "memory/allocation.hpp"
#include "services/nmtCommon.hpp"

#ifdef ASSERT

class NMT_MemoryLogRecorder : public StackObj {

private:
  struct Entry {
    jlong time;
    intx thread;
    address ptr;
    address old;
    address stack[NMT_TrackingStackDepth];
    size_t requested;
    size_t actual;
    MEMFLAGS flags;
    uint8_t active;
  };

  static const char* recall_thread_name(intx tid);

  static bool is_active(Entry* e)   { return (e->active == 1); };
  static void deactivate(Entry* e)  { e->active = 0; };
  static bool is_type_nmt(Entry* e) { return (e->flags == MEMFLAGS::mtNMT); };
  static bool is_free(Entry* e)     { return (e->requested == 0) && (e->old == nullptr); };
  static bool is_realloc(Entry* e)  { return (e->requested > 0)  && (e->old != nullptr); };
  static bool is_malloc(Entry* e)   { return (e->requested > 0)  && (e->old == nullptr); };
  static bool is_alloc(Entry* e)    { return is_malloc(e) || is_realloc(e); };

  static size_t find_previous_entry(Entry* entries, size_t index, address ptr);
  static void print_entry(Entry* entries);

  static void calculate_good_sizes(Entry* entries, size_t count);
  static void find_malloc_requests_buckets_sizes(Entry* entries, size_t count);
  static Entry* access_active(Entry* entries, size_t count) {
    Entry* e = &entries[count];
    if (is_active(e))
      return e;
    else
      return nullptr;
  };

  static void print_histogram(Entry* entries, size_t count, double cutoff = 0.0);
  static void print_records(Entry* entries, size_t count);
  static void report_by_component(Entry* entries, size_t count);
  static void report_by_thread(Entry* entries, size_t count);
  static void print_summary(Entry* entries, size_t count);
  static void consolidate(Entry* entries, size_t count, size_t start = 0);
  static void dump(Entry* entries, size_t count);

public:
  static void log(MEMFLAGS flags = mtNone, size_t requested = 0, address ptr = nullptr, address old = nullptr,
                  const NativeCallStack *stack = nullptr);
  static void remember_thread_name(const char* name);
};

#endif // ASSERT

#endif // SHARE_SERVICES_NMT_MEMORYLOGRECORDER_HPP
