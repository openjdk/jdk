/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023 Google LLC. All rights reserved.
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


#ifndef SHARE_RUNTIME_CPUTIMECOUNTERS_HPP
#define SHARE_RUNTIME_CPUTIMECOUNTERS_HPP


#include "memory/iterator.hpp"
#include "runtime/os.hpp"
#include "runtime/perfData.hpp"
#include "runtime/perfDataTypes.hpp"

class CPUTimeGroups : public AllStatic {
public:
  enum class CPUTimeType {
    gc_total,
    gc_parallel_workers,
    gc_conc_mark,
    gc_conc_refine,
    gc_service,
    vm,
    conc_dedup,
    COUNT,
  };

  static const char* to_string(CPUTimeType val);
  static bool is_gc_counter(CPUTimeType val);
};

class CPUTimeCounters: public CHeapObj<mtServiceability> {
private:
  // CPUTimeCounters is a singleton instance.
  CPUTimeCounters();
  NONCOPYABLE(CPUTimeCounters);

  static CPUTimeCounters* _instance;

  // An array of PerfCounters which correspond to the various counters we want
  // to track. Indexed by the enum value `CPUTimeType`.
  PerfCounter* _cpu_time_counters[static_cast<int>(CPUTimeGroups::CPUTimeType::COUNT)];

  // A long which atomically tracks how much CPU time has been spent doing GC
  // since the last time we called `publish_total_cpu_time()`.
  // It is incremented using Atomic::add() to prevent race conditions, and
  // is added to the `gc_total` CPUTimeType at the end of GC.
  volatile jlong _gc_total_cpu_time_diff;

  static void create_counter(CounterNS ns, CPUTimeGroups::CPUTimeType name);

  static CPUTimeCounters* get_instance() {
    assert(_instance != nullptr, "no instance found");
    return _instance;
  }

  static void inc_gc_total_cpu_time(jlong diff);

public:
  static void initialize() {
    assert(_instance == nullptr, "we can only allocate one CPUTimeCounters object");
    if (UsePerfData && os::is_thread_cpu_time_supported()) {
      _instance = new CPUTimeCounters();
      create_counter(SUN_THREADS, CPUTimeGroups::CPUTimeType::gc_total);
    }
  }

  static void create_counter(CPUTimeGroups::CPUTimeType name);
  static PerfCounter* get_counter(CPUTimeGroups::CPUTimeType name);
  static void update_counter(CPUTimeGroups::CPUTimeType name, jlong total);

  static void publish_gc_total_cpu_time();
};

// Class to compute the total CPU time for a set of threads, then update an
// hsperfdata counter.
class ThreadTotalCPUTimeClosure: public ThreadClosure {
 private:
  jlong _total;
  CPUTimeGroups::CPUTimeType _name;

 public:
  ThreadTotalCPUTimeClosure(CPUTimeGroups::CPUTimeType name)
      : _total(0), _name(name) {
    assert(os::is_thread_cpu_time_supported(), "os must support cpu time");
  }

  ~ThreadTotalCPUTimeClosure();

  virtual void do_thread(Thread* thread);
};

#endif // SHARE_RUNTIME_CPUTIMECOUNTERS_HPP
