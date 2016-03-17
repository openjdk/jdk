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

#ifndef SHARE_VM_GC_G1_WORKERDATAARRAY_INLINE_HPP
#define SHARE_VM_GC_G1_WORKERDATAARRAY_INLINE_HPP

#include "gc/g1/workerDataArray.hpp"
#include "memory/allocation.inline.hpp"
#include "utilities/ostream.hpp"

template <typename T>
WorkerDataArray<T>::WorkerDataArray(uint length, const char* title) :
 _title(title),
 _length(0),
 _thread_work_items(NULL) {
  assert(length > 0, "Must have some workers to store data for");
  _length = length;
  _data = NEW_C_HEAP_ARRAY(T, _length, mtGC);
  reset();
}

template <typename T>
void WorkerDataArray<T>::set(uint worker_i, T value) {
  assert(worker_i < _length, "Worker %d is greater than max: %d", worker_i, _length);
  assert(_data[worker_i] == uninitialized(), "Overwriting data for worker %d in %s", worker_i, _title);
  _data[worker_i] = value;
}

template <typename T>
T WorkerDataArray<T>::get(uint worker_i) const {
  assert(worker_i < _length, "Worker %d is greater than max: %d", worker_i, _length);
  assert(_data[worker_i] != uninitialized(), "No data added for worker %d", worker_i);
  return _data[worker_i];
}

template <typename T>
WorkerDataArray<T>::~WorkerDataArray() {
  FREE_C_HEAP_ARRAY(T, _data);
}

template <typename T>
void WorkerDataArray<T>::link_thread_work_items(WorkerDataArray<size_t>* thread_work_items) {
  _thread_work_items = thread_work_items;
}

template <typename T>
void WorkerDataArray<T>::set_thread_work_item(uint worker_i, size_t value) {
  assert(_thread_work_items != NULL, "No sub count");
  _thread_work_items->set(worker_i, value);
}

template <typename T>
void WorkerDataArray<T>::add(uint worker_i, T value) {
  assert(worker_i < _length, "Worker %d is greater than max: %d", worker_i, _length);
  assert(_data[worker_i] != uninitialized(), "No data to add to for worker %d", worker_i);
  _data[worker_i] += value;
}

template <typename T>
double WorkerDataArray<T>::average(uint active_threads) const {
  return sum(active_threads) / (double) active_threads;
}

template <typename T>
T WorkerDataArray<T>::sum(uint active_threads) const {
  T s = get(0);
  for (uint i = 1; i < active_threads; ++i) {
    s += get(i);
  }
  return s;
}

template <typename T>
void WorkerDataArray<T>::clear() {
  set_all(0);
}

template <typename T>
void WorkerDataArray<T>::set_all(T value) {
  for (uint i = 0; i < _length; i++) {
    _data[i] = value;
  }
}

template <class T>
void WorkerDataArray<T>::print_summary_on(outputStream* out, uint active_threads, bool print_sum) const {
  T max = get(0);
  T min = max;
  T sum = 0;
  for (uint i = 1; i < active_threads; ++i) {
    T value = get(i);
    max = MAX2(max, value);
    min = MIN2(min, value);
    sum += value;
  }
  T diff = max - min;
  double avg = sum / (double) active_threads;
  WDAPrinter::summary(out, title(), min, avg, max, diff, sum, print_sum);
}

template <class T>
void WorkerDataArray<T>::print_details_on(outputStream* out, uint active_threads) const {
  WDAPrinter::details(this, out, active_threads);
}

#ifndef PRODUCT
template <typename T>
void WorkerDataArray<T>::reset() {
  set_all(uninitialized());
  if (_thread_work_items != NULL) {
    _thread_work_items->reset();
  }
}

template <typename T>
void WorkerDataArray<T>::verify(uint active_threads) const {
  assert(active_threads <= _length, "Wrong number of active threads");
  for (uint i = 0; i < active_threads; i++) {
    assert(_data[i] != uninitialized(),
           "Invalid data for worker %u in '%s'", i, _title);
  }
  if (_thread_work_items != NULL) {
    _thread_work_items->verify(active_threads);
  }
}

template <>
inline size_t WorkerDataArray<size_t>::uninitialized() const {
  return (size_t)-1;
}

template <>
inline double WorkerDataArray<double>::uninitialized() const {
  return -1.0;
}
#endif

#endif // SHARE_VM_GC_G1_WORKERDATAARRAY_INLINE_HPP
