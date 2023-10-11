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
    address thread;
    address ptr;
    address old;
    address stack[NMT_TrackingStackDepth];
    size_t requested;
    size_t actual;
    MEMFLAGS flags;
  };
  static void calculate_good_sizes(Entry* entries, size_t count);
  static bool print_histogram(Entry* entries, size_t count);
  static void print_records(Entry* entries, size_t count);
  static bool print_by_thread(Entry* entries, size_t count);
  static size_t print_summary(Entry* entries, size_t count, bool substract_nmt = false);
  static void dump(Entry* entries, size_t count);

public:
  static bool is_free(Entry* e)    { return (e->requested == 0) && (e->old == nullptr); };
  static bool is_realloc(Entry* e) { return (e->requested > 0)  && (e->old != nullptr); };
  static bool is_malloc(Entry* e)  { return (e->requested > 0)  && (e->old == nullptr); };
  static bool is_alloc(Entry* e)   { return is_malloc(e) || is_realloc(e); };
  static void log(size_t requested = 0, address ptr = nullptr, address old = nullptr,
                  MEMFLAGS flags = mtNone, const NativeCallStack *stack = nullptr);
};

#endif // ASSERT

#endif // SHARE_SERVICES_NMT_MEMORYLOGRECORDER_HPP
