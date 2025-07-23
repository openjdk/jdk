/*
 * Copyright (c) 2025, IBM Corporation. All rights reserved.
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "classfile/classLoaderDataGraph.inline.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "gc/g1/g1CollectedHeap.hpp"
#include "logging/log.hpp"
#include "memory/metaspace.hpp"
#include "memory/metaspaceUtils.hpp"
#include "memory/universe.hpp"
#include "nmt/mallocTracker.hpp"
#include "nmt/memTracker.hpp"
#include "runtime/globals.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/nonJavaThread.hpp"
#include "runtime/orderAccess.hpp"
#include "runtime/task.hpp"
#include "runtime/threads.hpp"
#include "services/shorthist.hpp"
#include "utilities/debug.hpp"
#include "utilities/deferredStatic.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"

#include OS_HEADER(shorthist)

// milliseconds
constexpr unsigned min_interval = 5000;

// all memory sizes in KB
#define btokb(s) ( (s) / K)

struct Data {
  unsigned _id;
  struct {
    time_t time;
    ShortHistoryData_pd pd;       // os-dependend data
    size_t heap_committed;
    size_t heap_used;
    size_t cldg_ik;               // Number of loaded InstanceKlass
    size_t cldg_ak;               // Number of loaded ArrayKlass
    size_t meta_nclass_used;      // non-class metaspace used
    size_t meta_class_used;       // class space used
    size_t meta_gc_threshold;     // metaspace gc threshold
    int    threads_java;          // number of JavaThread
    int    threads_nonjava;       // number of NonJavaThread
    size_t nmt_malloc_total;      // NMT: outstanding mallocs, total
    size_t nmt_malloc_gcdata;     // NMT: outstanding mallocs, gc structures
    size_t nmt_malloc_unsafe;     // NMT: outstanding mallocs, Unsafe::allocate
  } _d;

#define HEADER1_a "                         "
#define HEADER2_a "  id                time "
#define HEADER1_b "|---- java heap ----||-- cldg ---||--------- metaspace ---------||- threads -||--------- nmt malloc --------|"
#define HEADER2_b "      comm      used     ik    ak     nclass     class  threshld   jthr njthr      total    gcdata    unsafe "
  //               |.........|.........||.....|.....||.........|.........|.........||.....|.....||.........|.........|.........||

  void measure_heap() {
    _d.heap_committed = btokb(Universe::heap()->capacity());
    const size_t used = UseG1GC ? ((G1CollectedHeap*)Universe::heap())->used_unlocked() :  // avoid locking
                                  Universe::heap()->used();
    _d.heap_used = btokb(used);
  }

  void measure_meta() {
    _d.meta_nclass_used = btokb(MetaspaceUtils::used_bytes(Metaspace::NonClassType));
    _d.meta_class_used = btokb(UseCompressedClassPointers ? MetaspaceUtils::used_bytes(Metaspace::ClassType) : 0);
    _d.meta_gc_threshold = btokb(MetaspaceGC::capacity_until_GC());
    _d.cldg_ik = ClassLoaderDataGraph::num_instance_classes();
    _d.cldg_ak = ClassLoaderDataGraph::num_array_classes();
  }

  void measure_java_threads() {
    _d.threads_java = Threads::number_of_threads();
    _d.threads_nonjava = NonJavaThread::count();
  }

  void measure_nmt() {
    if (MemTracker::enabled()) {
      _d.nmt_malloc_total = btokb(MallocTracker::total_malloc());
      _d.nmt_malloc_gcdata = btokb(MallocTracker::malloc_size(MemTag::mtGC));
      _d.nmt_malloc_unsafe = btokb(MallocTracker::malloc_size(MemTag::mtOther));
    }
  }

  void measure() {
    memset(&_d, 0, sizeof(_d));
    time(&_d.time);
    measure_heap();
    measure_meta();
    measure_nmt();
    measure_java_threads();
    _d.pd.measure();
  }

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
    st->print(" %9zu %9zu ", _d.heap_committed, _d.heap_used);
    st->print(" %5zu %5zu ", _d.cldg_ik, _d.cldg_ak);
    st->print(" %9zu %9zu %9zu ", _d.meta_nclass_used, _d.meta_class_used, _d.meta_gc_threshold);
    st->print(" %5d %5d ", _d.threads_java, _d.threads_nonjava);
    st->print(" %9zu %9zu %9zu ", _d.nmt_malloc_total, _d.nmt_malloc_gcdata, _d.nmt_malloc_unsafe);
    st->cr();
  }
};

class DataBuffer {
  // a fixed-sized FIFO buffer of Data
  const int _max;
  int _pos;
  Data* _table;

public:

  DataBuffer(int max) :
    _max(max), _pos(0), _table(nullptr)
  {
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
    st->print_cr("max %u pos %u wrapped %d", _max, _pos, _pos >= _max);
  }
};

class ShortHistoryStore {

  // Spanning 10 minutes (with default interval time of 10 seconds)
  static constexpr int _short_term_buffer_size = 60;
  // Spanning 3 hours (with default interval time of 1 minute)
  static constexpr int _long_term_buffer_size = 180;

  DataBuffer _short_term_buffer;
  DataBuffer _long_term_buffer;

public:

  ShortHistoryStore(unsigned interval) :
    _short_term_buffer(_short_term_buffer_size),
    _long_term_buffer(_long_term_buffer_size)
  {}

  void store(const Data& data) {
    _short_term_buffer.store(data);

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
