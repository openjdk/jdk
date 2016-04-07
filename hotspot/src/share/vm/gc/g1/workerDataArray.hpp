/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_G1_WORKERDATAARRAY_HPP
#define SHARE_VM_GC_G1_WORKERDATAARRAY_HPP

#include "memory/allocation.hpp"
#include "utilities/debug.hpp"

class outputStream;

template <class T>
class WorkerDataArray  : public CHeapObj<mtGC> {
  friend class WDAPrinter;
  T*          _data;
  uint        _length;
  const char* _title;

  WorkerDataArray<size_t>* _thread_work_items;

 public:
  WorkerDataArray(uint length, const char* title);
  ~WorkerDataArray();

  void link_thread_work_items(WorkerDataArray<size_t>* thread_work_items);
  void set_thread_work_item(uint worker_i, size_t value);
  WorkerDataArray<size_t>* thread_work_items() const {
    return _thread_work_items;
  }

  static T uninitialized();

  void set(uint worker_i, T value);
  T get(uint worker_i) const;

  void add(uint worker_i, T value);

  // The sum() and average() methods below consider uninitialized slots to be 0.
  double average() const;
  T sum() const;

  const char* title() const {
    return _title;
  }

  void reset();
  void set_all(T value);


 private:
  class WDAPrinter {
  public:
    static void summary(outputStream* out, double min, double avg, double max, double diff, double sum, bool print_sum);
    static void summary(outputStream* out, size_t min, double avg, size_t max, size_t diff, size_t sum, bool print_sum);

    static void details(const WorkerDataArray<double>* phase, outputStream* out);
    static void details(const WorkerDataArray<size_t>* phase, outputStream* out);
  };

 public:
  void print_summary_on(outputStream* out, bool print_sum = true) const;
  void print_details_on(outputStream* out) const;
};

#endif // SHARE_VM_GC_G1_WORKERDATAARRAY_HPP
