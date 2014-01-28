/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_MEMORY_METASPACECOUNTERS_HPP
#define SHARE_VM_MEMORY_METASPACECOUNTERS_HPP

#include "memory/allocation.hpp"

class MetaspacePerfCounters;

class MetaspaceCounters: public AllStatic {
  static MetaspacePerfCounters* _perf_counters;
  static size_t used();
  static size_t capacity();
  static size_t max_capacity();

 public:
  static void initialize_performance_counters();
  static void update_performance_counters();
};

class CompressedClassSpaceCounters: public AllStatic {
  static MetaspacePerfCounters* _perf_counters;
  static size_t used();
  static size_t capacity();
  static size_t max_capacity();

 public:
  static void initialize_performance_counters();
  static void update_performance_counters();
};

#endif // SHARE_VM_MEMORY_METASPACECOUNTERS_HPP
