/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

#include "memory/allocation.hpp"
#include "utilities/debug.hpp"

template <class T>
class WorkerDataArray  : public CHeapObj<mtGC> {
  friend class G1GCParPhasePrinter;
  T*          _data;
  uint        _length;
  const char* _title;
  bool        _print_sum;
  uint        _indent_level;
  bool        _enabled;

  WorkerDataArray<size_t>* _thread_work_items;

  NOT_PRODUCT(inline T uninitialized() const;)

  void set_all(T value);

 public:
  WorkerDataArray(uint length,
                  const char* title,
                  bool print_sum,
                  uint indent_level);

  ~WorkerDataArray();

  void link_thread_work_items(WorkerDataArray<size_t>* thread_work_items);
  void set_thread_work_item(uint worker_i, size_t value);
  WorkerDataArray<size_t>* thread_work_items() const {
    return _thread_work_items;
  }

  void set(uint worker_i, T value);
  T get(uint worker_i) const;

  void add(uint worker_i, T value);

  double average(uint active_threads) const;
  T sum(uint active_threads) const;
  T minimum(uint active_threads) const;
  T maximum(uint active_threads) const;
  T diff(uint active_threads) const;

  uint indentation() const {
    return _indent_level;
  }

  const char* title() const {
    return _title;
  }

  bool should_print_sum() const {
    return _print_sum;
  }

  void clear();
  void set_enabled(bool enabled) {
    _enabled = enabled;
  }

  void reset() PRODUCT_RETURN;
  void verify(uint active_threads) const PRODUCT_RETURN;
};
