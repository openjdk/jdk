/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, Red Hat, Inc. and/or its affiliates.
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

#include "memory/allStatic.hpp"
#include "utilities/globalDefinitions.hpp"

class DirectiveSet;
class outputStream;

class CompilationMemoryStatistic : public AllStatic {
  static bool _enabled;

  static void on_phase_start_0(int phasetraceid);
  static void on_phase_end_0(int phasetraceid);

public:
  static void initialize();
  // true if CollectMemStat or PrintMemStat has been enabled for any method
  static bool enabled() { return _enabled; }
  static void on_start_compilation(const DirectiveSet* directive);

  // Called at end of compilation. Records the arena usage peak. Also takes over
  // status information from ciEnv (compilation failed, oom'ed or went okay). ciEnv::_failure_reason
  // must be set at this point (so place CompilationMemoryStatisticMark correctly).
  static void on_end_compilation();

  static inline void on_phase_start(int phasetraceid) {
    if (enabled()) {
      on_phase_start_0(phasetraceid);
    }
  }
  static inline void on_phase_end(int phasetraceid) {
    if (enabled()) {
      on_phase_end_0(phasetraceid);
    }
  }

  // Account an arena allocation.
  static void on_arena_chunk_allocation(size_t size, int arenatag, uint64_t* stamp);

  // Account an arena deallocation.
  static void on_arena_chunk_deallocation(size_t size, uint64_t stamp);

  static void print_all_by_size(outputStream* st, bool human_readable, bool by_phase, size_t minsize);

  // For compilers
  static const char* failure_reason_memlimit();
};

// RAII object to wrap one compilation
class CompilationMemoryStatisticMark {
  const bool _active;
public:
  CompilationMemoryStatisticMark(const DirectiveSet* directive);
  ~CompilationMemoryStatisticMark();
};

#endif // SHARE_COMPILER_COMPILATIONMEMORYSTATISTIC_HPP
