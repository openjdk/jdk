/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

#include "compiler/compilerDefinitions.hpp"
#include "memory/allocation.hpp"
#include "memory/allStatic.hpp"
#include "memory/arena.hpp"
#include "utilities/globalDefinitions.hpp"

class outputStream;
class Symbol;
class DirectiveSet;

// Helper class to wrap the array of arena tags for easier processing
class ArenaCountersByTag {
private:
  size_t _counter[Arena::tag_count()];

public:
  int element_count() const { return Arena::tag_count(); }
  const char* tag_name(int tag) const { return Arena::tag_name[tag]; }

  size_t  counter(int tag) const {
    assert(tag < element_count(), "invalid tag %d", tag);
    return _counter[tag];
  }

  void add(int tag, size_t value) {
    assert(tag < element_count(), "invalid tag %d", tag);
    _counter[tag] += value;
  }

  void clear() {
    memset(_counter, 0, sizeof(size_t) * element_count());
  }
};

// Counters for allocations from arenas during compilation
class ArenaStatCounter : public CHeapObj<mtCompiler> {
  // Current bytes, total
  size_t _current;
  // bytes at last peak, total
  size_t _peak;
  // Current bytes used by arenas per tag
  ArenaCountersByTag _current_by_tag;
  // Peak composition:
  ArenaCountersByTag _peak_by_tag;
  // MemLimit handling
  size_t _limit;
  bool _hit_limit;
  bool _limit_in_process;

  // When to start accounting
  bool _active;

  // Number of live nodes when total peaked (c2 only)
  unsigned _live_nodes_at_peak;

  void update_c2_node_count();

  void reset();

public:
  ArenaStatCounter();

  // Size of peak since last compilation
  size_t peak() const { return _peak; }

  // Peak details
  ArenaCountersByTag peak_by_tag() const { return _peak_by_tag; }
  unsigned live_nodes_at_peak() const { return _live_nodes_at_peak; }

  // Mark the start and end of a compilation.
  void start(size_t limit);
  void end();

  // Account an arena allocation or de-allocation.
  // Returns true if new peak reached
  bool account(ssize_t delta, int tag);

  void set_live_nodes_at_peak(unsigned i) { _live_nodes_at_peak = i; }

  void print_on(outputStream* st) const;

  size_t limit() const              { return _limit; }
  bool   hit_limit() const          { return _hit_limit; }
  bool   limit_in_process() const     { return _limit_in_process; }
  void   set_limit_in_process(bool v) { _limit_in_process = v; }
  bool   is_active() const          { return _active; }
};

class CompilationMemoryStatistic : public AllStatic {
  static bool _enabled;
public:
  static void initialize();
  // true if CollectMemStat or PrintMemStat has been enabled for any method
  static bool enabled() { return _enabled; }
  static void on_start_compilation(const DirectiveSet* directive);

  // Called at end of compilation. Records the arena usage peak. Also takes over
  // status information from ciEnv (compilation failed, oom'ed or went okay). ciEnv::_failure_reason
  // must be set at this point (so place CompilationMemoryStatisticMark correctly).
  static void on_end_compilation();
  static void on_arena_change(ssize_t diff, const Arena* arena);
  static void print_all_by_size(outputStream* st, bool human_readable, size_t minsize);
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
