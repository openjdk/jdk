/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2025, IBM Corporation. All rights reserved.
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
#include "utilities/macros.hpp"
#include "utilities/ostream.hpp"
#include "utilities/vmError.hpp"

#include OS_HEADER(shorthist)

#ifdef HAVE_NO_SHORTHISTORYDATA_PD
struct ShortHistoryData_pd {
  void measure() {}
  void reset() {}
  static void print_header_1(outputStream* st)  {}
  static void print_header_2(outputStream* st) {}
  void print_on(outputStream* st) const {}
};
#endif // HAVE_NO_SHORTHISTORYDATA_PD

// all memory sizes in KB
#define btokb(s) ( (s) / K)

struct Data {
  struct {
    time_t time;
    ShortHistoryData_pd pd;       // OS-dependent data, see shorthist_<OS>.hpp
    ssize_t heap_committed;
    ssize_t heap_used;
    ssize_t meta_nclass_used;     // non-class metaspace, used
    ssize_t meta_class_used;      // class space, used
    ssize_t meta_gc_threshold;    // metaspace gc threshold
    ssize_t nmt_malloc_total;     // NMT: outstanding mallocs, total
    ssize_t nmt_malloc_peak;      // NMT: outstanding mallocs, peak
    ssize_t nmt_malloc_gcdata;    // NMT: outstanding mallocs, gc structures
    ssize_t nmt_malloc_unsafe;    // NMT: outstanding mallocs, Unsafe::allocate
    int threads_java;             // number of JavaThread
    int threads_nonjava;          // number of NonJavaThread
    int cldg_loaders;             // Number of CLDs
    int cldg_ik;                  // Number of loaded InstanceKlass
    int cldg_ak;                  // Number of loaded ArrayKlass

    void reset () {
      heap_committed = heap_used = meta_nclass_used = meta_class_used =
                       meta_gc_threshold = nmt_malloc_total = nmt_malloc_peak =
                       nmt_malloc_gcdata = nmt_malloc_unsafe = -1;
      threads_java = threads_nonjava = cldg_loaders = cldg_ik = cldg_ak = -1;
      pd.reset();
    }
  } _d;
  unsigned _id;

#define HEADER1_a "                         "
#define HEADER2_a "  id                time "
#define HEADER1_b "|---- java heap ----||---- classes ----||--------- metaspace ---------||- threads -||-------------- nmt malloc -------------|"
#define HEADER2_b "      comm      used    cld    ik    ak     nclass     class  threshld   jthr njthr      total      peak    gcdata    unsafe "
  //               |.........|.........||.....|.....|.....||.........|.........|.........||.....|.....||.........|.........|.........|.........||

  void measure_heap() {
    _d.heap_committed = btokb(Universe::heap()->capacity());
    const size_t used = Universe::heap()->used_unlocked();
    _d.heap_used = btokb(used);
  }

  void measure_meta() {
    _d.meta_nclass_used = btokb(MetaspaceUtils::used_bytes(Metaspace::NonClassType));
    _d.meta_class_used = btokb(UseCompressedClassPointers ? MetaspaceUtils::used_bytes(Metaspace::ClassType) : 0);
    _d.meta_gc_threshold = btokb(MetaspaceGC::capacity_until_GC());
    _d.cldg_loaders = checked_cast<int>(ClassLoaderDataGraph::num_class_loaders());
    _d.cldg_ik = checked_cast<int>(ClassLoaderDataGraph::num_instance_classes());
    _d.cldg_ak = checked_cast<int>(ClassLoaderDataGraph::num_array_classes());
  }

  void measure_java_threads() {
    _d.threads_java = Threads::number_of_threads();
    _d.threads_nonjava = NonJavaThread::count();
  }

  void measure_nmt() {
    if (MemTracker::enabled()) {
      _d.nmt_malloc_total = btokb(MallocTracker::total_malloc());
      _d.nmt_malloc_peak = btokb(MallocTracker::total_peak_malloc());
      _d.nmt_malloc_gcdata = btokb(MallocTracker::malloc_size(MemTag::mtGC));
      _d.nmt_malloc_unsafe = btokb(MallocTracker::malloc_size(MemTag::mtOther));
    }
  }

  void measure() {
    _d.reset();
    time(&_d.time);
    measure_heap();
    measure_meta();
    measure_nmt();
    measure_java_threads();
    _d.pd.measure();
  }

  static void print_header(outputStream* st) {
    st->print_raw(HEADER1_a);
    ShortHistoryData_pd::print_header_1(st);
    st->print_raw(HEADER1_b);
    st->cr();
    st->print_raw(HEADER2_a);
    ShortHistoryData_pd::print_header_2(st);
    st->print_raw(HEADER2_b);
    st->cr();
  }

  void print_on(outputStream* st) const {
    st->print("%4u ", _id);
    char buf[64] = "";
    const char* const timefmt = "%Y-%m-%d %H:%M:%S";
    struct tm local_time;
    os::localtime_pd(&_d.time, &local_time);
    strftime(buf, sizeof(buf), timefmt, &local_time);
    st->print("%s ", buf);
    _d.pd.print_on(st);
    st->print(" %9zd %9zd ", _d.heap_committed, _d.heap_used);
    st->print(" %5d %5d %5d ", _d.cldg_loaders, _d.cldg_ik, _d.cldg_ak);
    st->print(" %9zd %9zd %9zd ", _d.meta_nclass_used, _d.meta_class_used, _d.meta_gc_threshold);
    st->print(" %5d %5d ", _d.threads_java, _d.threads_nonjava);
    st->print(" %9zd %9zd %9zd %9zd ", _d.nmt_malloc_total, _d.nmt_malloc_peak, _d.nmt_malloc_gcdata, _d.nmt_malloc_unsafe);
    st->cr();
  }
};

template <int capacity>
class DataBuffer {
  // a fixed-sized FIFO buffer of Data
  int _pos;
  Data _table[capacity];

  bool has_data() const { return _pos > 0; }

public:

  DataBuffer() : _pos(0) {
    memset(_table, 0, sizeof(Data) * capacity);
  }

  void store(const Data& data) {
    assert(_pos >= 0, "Sanity");
    const int slot = _pos % capacity;
    Data* const p = &_table[slot];
    p->_id = 0;
    OrderAccess::storestore();
    p->_d = data._d;
    OrderAccess::storestore();
    p->_id = _pos + 1;
    _pos++;
  }

  void print_on(outputStream* st, const char* title) const {
    st->print_cr("%s", title);
    if (has_data()) {
      Data::print_header(st);
      const int start_pos = _pos;
      const int end_pos = MAX2(start_pos - capacity, 0);
      for (int pos = start_pos - 1; pos >= end_pos; pos--) {
        const int slot = pos % capacity;
        if (_table[slot]._id > 0) {
          OrderAccess::loadload();
          _table[slot].print_on(st);
        }
      }
    } else {
      st->print_cr("No data");
    }
  }
};

class ShortHistoryStore {
public:

  // A short-term buffer spans the last 10 minutes; a long-term buffer the last 5 hours
  // (if we run with the default interval of 10 seconds)
  static constexpr int default_interval = 10;
  static constexpr int timespan_short = 10 * 60;
  static constexpr int timespan_long = 5 * 60 * 60;
  static constexpr int interval_long = timespan_short / 2;
  static constexpr int capacity_short = timespan_short / default_interval;
  static constexpr int capacity_long = timespan_long / interval_long;
  static constexpr int ratio_long_short = interval_long / default_interval;

private:

  DataBuffer<capacity_short> _short_term_buffer;
  DataBuffer<capacity_long> _long_term_buffer;
  int _num_stored;

public:

  ShortHistoryStore() : _num_stored(0) {}

  void store(const Data& data) {
    _num_stored ++;
    _short_term_buffer.store(data);
    if ((_num_stored % ratio_long_short) == 0) {
      _long_term_buffer.store(data);
    }
  }

  void print_on(outputStream* st) const {
    _short_term_buffer.print_on(st, "short-term");
    _long_term_buffer.print_on(st, "long-term");
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
  if (UseHistory) {
    g_store.initialize();
    g_task.initialize(HistoryInterval);
    g_task->enroll();
    log_info(os)("History task enrolled (interval: %u ms)", HistoryInterval);
  }
}

void ShortHistory::cleanup() {
  if (UseHistory) {
    g_task->disenroll();
    log_info(os)("History task dis-enrolled");
  }
}

void ShortHistory::print(outputStream* st) {
  st->print_cr("History:");
  if (UseHistory) {
    // Measure current values to show in case this is called during a crash
    if (VMError::is_error_reported_in_current_thread()) {
      Data d_now;
      d_now._id = 0;
      d_now.measure();
      st->print("now:");
      Data::print_header(st);
      d_now.print_on(st);
    }
    // Print history
    g_store->print_on(st);
  } else {
    st->print_cr("(inactive)");
    return;
  }
}
