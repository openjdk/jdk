/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2025, Red Hat, Inc. and/or its affiliates.
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

#ifndef SHARE_COMPILER_COMPILATIONMEMORYSTATISTIC_HPP
#define SHARE_COMPILER_COMPILATIONMEMORYSTATISTIC_HPP

#include "memory/allocation.hpp"
#include "memory/allStatic.hpp"
#include "utilities/globalDefinitions.hpp"

class DirectiveSet;
class outputStream;

class CompilationMemoryStatistic : public AllStatic {
  friend class CompilationMemoryStatisticMark;
  static bool _enabled; // set to true if memstat is active for any method.

  // Private, should only be called via CompilationMemoryStatisticMark
  static void on_start_compilation(const DirectiveSet* directive);

  // Private, should only be called via CompilationMemoryStatisticMark
  static void on_end_compilation();

  static void print_all_by_size(outputStream* st, bool verbose, bool legend, size_t minsize, int max_num_printed);

public:
  static void initialize();
  // true if CollectMemStat or PrintMemStat has been enabled for any method
  static bool enabled() { return _enabled; }
  // true if we are in a fatal error inited by hitting the MemLimit
  static bool in_oom_crash();

  static void on_phase_start(int phase_trc_id, const char* text);
  static void on_phase_end();
  static void on_arena_chunk_allocation(size_t size, int arenatag, uint64_t* stamp);
  static void on_arena_chunk_deallocation(size_t size, uint64_t stamp);

  static void print_final_report(outputStream* st);
  static void print_error_report(outputStream* st);
  static void print_jcmd_report(outputStream* st, bool verbose, bool legend, size_t minsize);

  // For compilers
  static const char* failure_reason_memlimit();

  DEBUG_ONLY(static void do_test_allocations();)
};

// RAII object to wrap one compilation
class CompilationMemoryStatisticMark : public StackObj {
  const bool _active;
public:
  CompilationMemoryStatisticMark(const DirectiveSet* directive);
  ~CompilationMemoryStatisticMark();
};

#endif // SHARE_COMPILER_COMPILATIONMEMORYSTATISTIC_HPP
