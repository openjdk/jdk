/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_SERVICES_MEM_REPORTER_HPP
#define SHARE_VM_SERVICES_MEM_REPORTER_HPP

#include "runtime/mutexLocker.hpp"
#include "services/memBaseline.hpp"
#include "services/memTracker.hpp"
#include "utilities/ostream.hpp"
#include "utilities/macros.hpp"

#if INCLUDE_NMT

/*
 * MemBaselineReporter reports data to this outputer class,
 * ReportOutputer is responsible for format, store and redirect
 * the data to the final destination.
 */
class BaselineOutputer : public StackObj {
 public:
  // start to report memory usage in specified scale.
  // if report_diff = true, the reporter reports baseline comparison
  // information.

  virtual void start(size_t scale, bool report_diff = false) = 0;
  // Done reporting
  virtual void done() = 0;

  /* report baseline summary information */
  virtual void total_usage(size_t total_reserved,
                           size_t total_committed) = 0;
  virtual void num_of_classes(size_t classes) = 0;
  virtual void num_of_threads(size_t threads) = 0;

  virtual void thread_info(size_t stack_reserved_amt, size_t stack_committed_amt) = 0;

  /* report baseline summary comparison */
  virtual void diff_total_usage(size_t total_reserved,
                                size_t total_committed,
                                int reserved_diff,
                                int committed_diff) = 0;
  virtual void diff_num_of_classes(size_t classes, int diff) = 0;
  virtual void diff_num_of_threads(size_t threads, int diff) = 0;

  virtual void diff_thread_info(size_t stack_reserved, size_t stack_committed,
        int stack_reserved_diff, int stack_committed_diff) = 0;


  /*
   * memory summary by memory types.
   * for each memory type, following summaries are reported:
   *  - reserved amount, committed amount
   *  - malloc'd amount, malloc count
   *  - arena amount, arena count
   */

  // start reporting memory summary by memory type
  virtual void start_category_summary() = 0;

  virtual void category_summary(MEMFLAGS type, size_t reserved_amt,
                                size_t committed_amt,
                                size_t malloc_amt, size_t malloc_count,
                                size_t arena_amt, size_t arena_count) = 0;

  virtual void diff_category_summary(MEMFLAGS type, size_t cur_reserved_amt,
                                size_t cur_committed_amt,
                                size_t cur_malloc_amt, size_t cur_malloc_count,
                                size_t cur_arena_amt, size_t cur_arena_count,
                                int reserved_diff, int committed_diff, int malloc_diff,
                                int malloc_count_diff, int arena_diff,
                                int arena_count_diff) = 0;

  virtual void done_category_summary() = 0;

  virtual void start_virtual_memory_map() = 0;
  virtual void reserved_memory_region(MEMFLAGS type, address base, address end, size_t size, address pc) = 0;
  virtual void committed_memory_region(address base, address end, size_t size, address pc) = 0;
  virtual void done_virtual_memory_map() = 0;

  /*
   *  Report callsite information
   */
  virtual void start_callsite() = 0;
  virtual void malloc_callsite(address pc, size_t malloc_amt, size_t malloc_count) = 0;
  virtual void virtual_memory_callsite(address pc, size_t reserved_amt, size_t committed_amt) = 0;

  virtual void diff_malloc_callsite(address pc, size_t cur_malloc_amt, size_t cur_malloc_count,
              int malloc_diff, int malloc_count_diff) = 0;
  virtual void diff_virtual_memory_callsite(address pc, size_t cur_reserved_amt, size_t cur_committed_amt,
              int reserved_diff, int committed_diff) = 0;

  virtual void done_callsite() = 0;

  // return current scale in "KB", "MB" or "GB"
  static const char* memory_unit(size_t scale);
};

/*
 * This class reports processed data from a baseline or
 * the changes between the two baseline.
 */
class BaselineReporter : public StackObj {
 private:
  BaselineOutputer&  _outputer;
  size_t             _scale;

 public:
  // construct a reporter that reports memory usage
  // in specified scale
  BaselineReporter(BaselineOutputer& outputer, size_t scale = K):
    _outputer(outputer) {
    _scale = scale;
  }
  virtual void report_baseline(const MemBaseline& baseline, bool summary_only = false);
  virtual void diff_baselines(const MemBaseline& cur, const MemBaseline& prev,
                              bool summary_only = false);

  void set_scale(size_t scale);
  size_t scale() const { return _scale; }

 private:
  void report_summaries(const MemBaseline& baseline);
  void report_virtual_memory_map(const MemBaseline& baseline);
  void report_callsites(const MemBaseline& baseline);

  void diff_summaries(const MemBaseline& cur, const MemBaseline& prev);
  void diff_callsites(const MemBaseline& cur, const MemBaseline& prev);

  // calculate memory size in current memory scale
  size_t amount_in_current_scale(size_t amt) const;
  // diff two unsigned values in current memory scale
  int    diff_in_current_scale(size_t value1, size_t value2) const;
  // diff two unsigned value
  int    diff(size_t value1, size_t value2) const;
};

/*
 * tty output implementation. Native memory tracking
 * DCmd uses this outputer.
 */
class BaselineTTYOutputer : public BaselineOutputer {
 private:
  size_t         _scale;

  size_t         _num_of_classes;
  size_t         _num_of_threads;
  size_t         _thread_stack_reserved;
  size_t         _thread_stack_committed;

  int            _num_of_classes_diff;
  int            _num_of_threads_diff;
  int            _thread_stack_reserved_diff;
  int            _thread_stack_committed_diff;

  outputStream*  _output;

 public:
  BaselineTTYOutputer(outputStream* st) {
    _scale = K;
    _num_of_classes = 0;
    _num_of_threads = 0;
    _thread_stack_reserved = 0;
    _thread_stack_committed = 0;
    _num_of_classes_diff = 0;
    _num_of_threads_diff = 0;
    _thread_stack_reserved_diff = 0;
    _thread_stack_committed_diff = 0;
    _output = st;
  }

  // begin reporting memory usage in specified scale
  void start(size_t scale, bool report_diff = false);
  // done reporting
  void done();

  // total memory usage
  void total_usage(size_t total_reserved,
                   size_t total_committed);
  // report total loaded classes
  void num_of_classes(size_t classes) {
    _num_of_classes = classes;
  }

  void num_of_threads(size_t threads) {
    _num_of_threads = threads;
  }

  void thread_info(size_t stack_reserved_amt, size_t stack_committed_amt) {
    _thread_stack_reserved = stack_reserved_amt;
    _thread_stack_committed = stack_committed_amt;
  }

  void diff_total_usage(size_t total_reserved,
                        size_t total_committed,
                        int reserved_diff,
                        int committed_diff);

  void diff_num_of_classes(size_t classes, int diff) {
    _num_of_classes = classes;
    _num_of_classes_diff = diff;
  }

  void diff_num_of_threads(size_t threads, int diff) {
    _num_of_threads = threads;
    _num_of_threads_diff = diff;
  }

  void diff_thread_info(size_t stack_reserved_amt, size_t stack_committed_amt,
               int stack_reserved_diff, int stack_committed_diff) {
    _thread_stack_reserved = stack_reserved_amt;
    _thread_stack_committed = stack_committed_amt;
    _thread_stack_reserved_diff = stack_reserved_diff;
    _thread_stack_committed_diff = stack_committed_diff;
  }

  /*
   * Report memory summary categoriuzed by memory types.
   * For each memory type, following summaries are reported:
   *  - reserved amount, committed amount
   *  - malloc-ed amount, malloc count
   *  - arena amount, arena count
   */
  // start reporting memory summary by memory type
  void start_category_summary();
  void category_summary(MEMFLAGS type, size_t reserved_amt, size_t committed_amt,
                               size_t malloc_amt, size_t malloc_count,
                               size_t arena_amt, size_t arena_count);

  void diff_category_summary(MEMFLAGS type, size_t cur_reserved_amt,
                          size_t cur_committed_amt,
                          size_t cur_malloc_amt, size_t cur_malloc_count,
                          size_t cur_arena_amt, size_t cur_arena_count,
                          int reserved_diff, int committed_diff, int malloc_diff,
                          int malloc_count_diff, int arena_diff,
                          int arena_count_diff);

  void done_category_summary();

  // virtual memory map
  void start_virtual_memory_map();
  void reserved_memory_region(MEMFLAGS type, address base, address end, size_t size, address pc);
  void committed_memory_region(address base, address end, size_t size, address pc);
  void done_virtual_memory_map();


  /*
   *  Report callsite information
   */
  void start_callsite();
  void malloc_callsite(address pc, size_t malloc_amt, size_t malloc_count);
  void virtual_memory_callsite(address pc, size_t reserved_amt, size_t committed_amt);

  void diff_malloc_callsite(address pc, size_t cur_malloc_amt, size_t cur_malloc_count,
              int malloc_diff, int malloc_count_diff);
  void diff_virtual_memory_callsite(address pc, size_t cur_reserved_amt, size_t cur_committed_amt,
              int reserved_diff, int committed_diff);

  void done_callsite();
};


#endif // INCLUDE_NMT

#endif // SHARE_VM_SERVICES_MEM_REPORTER_HPP
