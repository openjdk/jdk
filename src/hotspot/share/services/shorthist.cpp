/*
 * Copyright (c) 2023 SAP SE. All rights reserved.
 * Copyright (c) 2023 Red Hat Inc. All rights reserved.
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/shared/collectedHeap.hpp"
#include "logging/log.hpp"
#include "memory/metaspace.hpp"
#include "memory/metaspaceUtils.hpp"
#include "memory/universe.hpp"
#include "runtime/globals.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/orderAccess.hpp"
#include "runtime/task.hpp"
#include "services/shorthist.hpp"
#include "utilities/debug.hpp"
#include "utilities/deferredStatic.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"

#include OS_HEADER(shorthist)

// milliseconds
constexpr unsigned min_interval = 5000;

// all memory sizes in KB
#define btokb(s) (s / K)

struct Data {
  unsigned _id;
  struct {
    time_t time;
    ShortHistoryData_pd pd;
    size_t heap_committed;
    size_t heap_used;
    size_t meta_nclass_used;
    size_t meta_class_used;
    size_t meta_gc_threshold;
  } _d;

  void measure() {
    // Note: only measure things one can measure quickly and without locking
    time(&_d.time);
    _d.pd.measure();
    _d.heap_committed = btokb(Universe::heap()->capacity());
//    _d.heap_used = btokb(Universe::heap()->used());
    _d.heap_used = 0;
    _d.meta_nclass_used = btokb(MetaspaceUtils::used_bytes(Metaspace::NonClassType));
    _d.meta_class_used = btokb(UseCompressedClassPointers ? MetaspaceUtils::used_bytes(Metaspace::ClassType) : 0);
    _d.meta_gc_threshold = btokb(MetaspaceGC::capacity_until_GC());
  }
//                 012345678901234567890123456789012345678901234567890123456789
#define HEADER1_a "                         "
#define HEADER2_a "  id                time "
#define HEADER1_b "---- java heap ---- ------- metaspace used ------ "
#define HEADER2_b "     comm      used    nclass     class     gcthr "

  static void print_header_1(outputStream* st) {
    st->print_raw(HEADER1_a);
    ShortHistoryData_pd::print_header_1(st);
    st->print_raw(HEADER1_b);
    st->cr();
  }

  static void print_header_2(outputStream* st) {
    st->print_raw(HEADER2_a);
    ShortHistoryData_pd::print_header_2(st);
    st->print_raw(HEADER2_b);
    st->cr();
  }

  void print_on(outputStream* st) const {
    st->print("%4u ", _id);
    char buf[64] = "                Now";
    if (_d.time > 0) {
      const char* const timefmt = "%Y-%m-%d %H-%M-%S";
      struct tm local_time;
      os::localtime_pd(&_d.time, &local_time);
      strftime(buf, sizeof(buf), timefmt, &local_time);
    }
    st->print("%s ", buf);
    _d.pd.print_on(st);
    st->print("%9zu %9zu ", _d.heap_committed, _d.heap_used);
    st->print("%9zu %9zu %9zu ", _d.meta_nclass_used, _d.meta_class_used, _d.meta_gc_threshold);
    st->cr();
  }
};

class ShortHistoryStore {
  // a fixed-sized FIFO buffer of Data
  int _max;
  int _pos;
  Data* _table;

public:

  ShortHistoryStore(unsigned interval) :
    _max(0), _pos(0), _table(nullptr)
  {
    assert(interval != 0, "Only call if enabled");
    assert(interval >= min_interval, "Interval too short");
    constexpr unsigned ms_per_hour = 1000 * 60 * 60;
    _max = ms_per_hour / interval;
    _table = NEW_C_HEAP_ARRAY(Data, _max, mtInternal);
    memset(_table, 0, sizeof(Data) * _max);
  }

  void store(const Data& data) {
    assert(_pos >= 0, "Sanity");
    const int slot = _pos % _max;
    Data* const p = &_table[slot];
    p->_id = 0;
    OrderAccess::storestore();
    p->_d = data._d;
    OrderAccess::storestore();
    p->_id = _pos + 1;
    _pos++;
  }

  bool has_data() const {
    return _pos > 0;
  }

  void print_on(outputStream* st) const {
    const int start_pos = _pos;
    const int end_pos = MAX2(start_pos - _max, 0);
    for (int pos = start_pos - 1; pos >= end_pos; pos--) {
      const int slot = pos % _max;
      if (_table[slot]._id > 0) {
        OrderAccess::loadload();
        _table[slot].print_on(st);
      }
    }
  }

  void print_state(outputStream* st) const {
    st->print_cr("ShortHistory store: max %u pos %u wrapped %d", _max, _pos, _pos >= _max);
  }
};

DeferredStatic<ShortHistoryStore> g_store;

struct ShortHistoryTask : public PeriodicTask {

  ShortHistoryTask(unsigned interval) : PeriodicTask(interval) {}

  void task() override {
    Data data;
    data.measure();
    g_store->store(data);
  }

}; // ShortHistoryThread

DeferredStatic<ShortHistoryTask> g_task;

void ShortHistory::initialize() {
  if (enabled()) {
    FLAG_SET_ERGO(ShortHistoryInterval, MAX2(ShortHistoryInterval, min_interval));
    g_store.initialize(ShortHistoryInterval);
    g_task.initialize(ShortHistoryInterval);
    g_task->enroll();
    log_info(os)("ShortHistory task enrolled (interval: %u ms)", ShortHistoryInterval);
  }
}

void ShortHistory::cleanup() {
  if (enabled()) {
    g_task->disenroll(); // is this even necessary?
    log_info(os)("ShortHistory task disenrolled");
  }
}

void ShortHistory::print_state(outputStream* st) {
  if (g_store.is_initialized()) {
    st->print_cr("enabled");
    g_store->print_state(st);
  } else {
    st->print_cr("disabled");
  }
}

void ShortHistory::print(outputStream* st, bool measure_now) {
  st->print_cr("ShortHistory:");
  st->cr();
  if (!g_store.is_initialized()) {
    st->print_cr("(inactive)");
    return;
  }
  if (!g_store->has_data()) {
    st->print_cr("(no data)");
    return;
  }
  Data::print_header_1(st);
  Data::print_header_2(st);
  // Measure now, to have current values
  if (measure_now) {
    Data d_now;
    d_now._id = 0;
    d_now.measure();
    d_now._d.time = 0;
    d_now.print_on(st);
  }
  // Print history
  g_store->print_on(st);
}
