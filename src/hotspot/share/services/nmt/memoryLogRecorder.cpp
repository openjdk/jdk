/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "jvm.h"
#include "runtime/globals.hpp"
#include "services/nmtCommon.hpp"
#include "services/nmt/memoryLogRecorder.hpp"
#include "services/mallocHeader.hpp"
#include "services/memTracker.hpp"
#include "utilities/debug.hpp"
#include "utilities/nativeCallStack.hpp"

#if defined(LINUX)
#include <malloc.h>
#elif defined(__APPLE__)
#include <malloc/malloc.h>
#endif

#ifdef ASSERT

constexpr size_t _horizontal_space = 100;
constexpr int _buckets_max = 2048;
constexpr int _threads_max = 32;

int compare(const void* ptr_a, const void* ptr_b) {
  size_t a = * ( (size_t*) ptr_a );
  size_t b = * ( (size_t*) ptr_b );
  if ( a > b ) return 1;
  else if ( a < b ) return -1;
  else return 0;
}

void NMT_MemoryLogRecorder::print_histogram(Entry* entries, size_t count) {
  static size_t histogram_counts[_buckets_max] = { 0 };
  static size_t histogram_actual_sizes[_buckets_max] = { 0 };
  static size_t histogram_requested_sizes[_buckets_max] = { 0 };

  size_t total_requested_malloced = 0;
  size_t total_actual_malloced = 0;
  for (size_t c=0; c<count; c++) {
    Entry* e = &entries[c];
    if (NMT_MemoryLogRecorder::is_alloc(e)) {
      total_requested_malloced += e->requested;
      total_actual_malloced += e->actual;
    }
  }
  size_t total_overhead = (total_actual_malloced - total_requested_malloced);

  // find unique alloc requests
  for (size_t c=0; c<count; c++) {
    Entry* e = &entries[c];
    if (NMT_MemoryLogRecorder::is_alloc(e)) {
      for (int i=0; i<_buckets_max; i++) {
        if (histogram_requested_sizes[i] == e->requested) {
          break;
        } else if (histogram_requested_sizes[i] == 0) {
          histogram_requested_sizes[i] = e->requested;
          break;
        }
      }
    }
  }
  qsort(histogram_requested_sizes, _buckets_max, sizeof(size_t), compare);

  size_t buckets_used = 0;
  for (int i=0; i<_buckets_max; i++) {
    if (histogram_requested_sizes[i] > 0) {
      buckets_used++;
    }
  }
  assert(buckets_used<(_buckets_max-1), "_buckets_max is too small?");

  // find total_actual sizes for alloc requests and count how many of them there are
  for (int i=0; i<_buckets_max; i++) {
    for (size_t c=0; c<count; c++) {
      Entry* e = &entries[c];
      if (histogram_requested_sizes[i] == e->requested) {
        if (histogram_actual_sizes[i] > 0) {
          // just double checking
          assert(histogram_actual_sizes[i] = e->actual, "histogram_actual_sizes[] = e->total_actual");
        }
        histogram_actual_sizes[i] = e->actual;
        histogram_counts[i]++;
      }
    }
  }

  size_t max_bucket_count = 0;
  for (int c=0; c<_buckets_max; c++) {
    if (histogram_counts[c] > max_bucket_count) {
      max_bucket_count = histogram_counts[c];
    }
  }

  size_t r_total = 0;
  size_t a_total = 0;
  size_t o_total = 0;
  size_t c_total = 0;
  size_t malloc_buckets_count = 0;
  fprintf(stderr, "\n\n");
  fprintf(stderr, "Histogram of memory size allocations (uses nonlinear scale)\n");
  fprintf(stderr, "---------------------------------------------\n");
  fprintf(stderr, "requested:    actual: overhead: ratio: count:\n");
  for (int i=0; i<_buckets_max; i++) {
    if (histogram_requested_sizes[i] > 0) {
      malloc_buckets_count++;

      double bar = _horizontal_space * (double)histogram_counts[i] / (double)max_bucket_count;
      size_t mark = (size_t)MAX(MIN(sqrt(_horizontal_space*bar), _horizontal_space), 0.0);
      char flag = (histogram_requested_sizes[i] == histogram_actual_sizes[i]) ? '=' : ' ';
      size_t o = histogram_counts[i] * (histogram_actual_sizes[i] - histogram_requested_sizes[i]);
      double o_ratio = 100.0 * o / total_overhead;

      r_total += histogram_counts[i] * histogram_requested_sizes[i];
      a_total += histogram_counts[i] * histogram_actual_sizes[i];
      o_total += o;
      c_total += histogram_counts[i];

      fprintf(stderr, "%9zu%c %9zu %9zu   %02.2f %6zu ",
              histogram_requested_sizes[i], flag, histogram_actual_sizes[i], o, o_ratio, histogram_counts[i]);
      for (size_t j=0; j<mark; j++) {
        fprintf(stderr, "*");
      }
      fprintf(stderr, "\n");
    }
  }
  fprintf(stderr, "\nnative malloc used %zu buckets\n\n", malloc_buckets_count);

  //assert((total_mallocs+total_reallocs) == c_total, "(total_mallocs+total_reallocs):%zu == ctotal:%zu", (total_mallocs+total_reallocs), c_total);
  //assert(r_total == total_requested, "r_total:%zu == total_requested:%zu", r_total, total_requested);
  //assert(a_total == total_actual, "a_total:%zu == total_actual:%zu", a_total, total_actual);
  //assert(a_total-r_total == o_total, "a_total-r_total:%zu == o_total:%zu", a_total-r_total, o_total);
  //assert(total_overhead == o_total, "total_overhead:%zu == total_requested:%zu", total_overhead, o_total);
}

void NMT_MemoryLogRecorder::print_records(Entry* entries, size_t count) {
  for (size_t c=0; c<count; c++) {
    Entry* e = &entries[c];
    fprintf(stderr, "{ %18p, %18p", e->ptr, e->old);
    for (int i=0; i<NMT_TrackingStackDepth; i++) {
      fprintf(stderr, ", %18p", e->stack[i]);
    }
    fprintf(stderr, ", %7u, %7u, %7u, \"%s\"},\n", (unsigned)e->requested, (unsigned)e->actual,
            (unsigned)e->flags, NMTUtil::flag_to_name(e->flags));
  }
}

bool NMT_MemoryLogRecorder::print_by_thread(Entry* entries, size_t count) {
  void* threads[_threads_max] = { nullptr };
  static size_t threads_counters_malloc_count[_threads_max] = { 0 };
  static size_t threads_counters_realloc_count[_threads_max] = { 0 };
  static size_t threads_counters_free_count[_threads_max] = { 0 };
  bool threads_limit_reached = false;

  for (size_t c=0; c<count; c++) {
    Entry* e = &entries[c];
    
    // intialize the array with threads' pointers
    for (int i=0; i<_threads_max; i++) {
      if (i == (_threads_max-1)) {
        threads_limit_reached = true;
      }
      if (threads[i] == e->thread) {
        break;
      } else if (threads[i] == nullptr) {
        threads[i] = e->thread;
        break;
      }
    }

    // count the instances of malloc, realloc and free
    for (int i=0; i<_threads_max; i++) {
      if (threads[i] == e->thread) {
        if (NMT_MemoryLogRecorder::is_alloc(e)) {
          if (e->old == nullptr) {
            threads_counters_malloc_count[i]++;
          } else {
            threads_counters_realloc_count[i]++;
          }
        } else { // free
          threads_counters_free_count[i]++;
        }
        break;
      }
    }
  }
  
  static size_t threads_counters_actual_malloced[_threads_max] = { 0 };
  static size_t threads_counters_actual_freed[_threads_max] = { 0 };
  // count the total_actual bytes that were allocated/freed by the OS
  for (size_t c=0; c<count; c++) {
    Entry* e = &entries[c];
    for (int i=0; i<_threads_max; i++) {
      if (threads[i] == e->thread) {
        if (NMT_MemoryLogRecorder::is_alloc(e)) {
          threads_counters_actual_malloced[i] += e->actual;
        } else { // free
          threads_counters_actual_freed[i] += e->actual;
        }
        break;
      }
    }
  }

  fprintf(stderr, "\n");
  fprintf(stderr, "-----------------------------------------------------------------------------------------\n");
  fprintf(stderr, "                      thread name:  mallocs: reallocs:     free:   allocated:      freed:\n");
  fprintf(stderr, "                                     (count)   (count)   (count)      (bytes)     (bytes)\n");
  fprintf(stderr, "-----------------------------------------------------------------------------------------\n");
  for (int i=0; i<_threads_max; i++) {
    if (threads[i] != nullptr) {
      char buf[32] = { 0 };
#if defined(LINUX) || defined(__APPLE__)
      pthread_getname_np((pthread_t)threads[i], &buf[0], sizeof(buf));
#endif
      if (strlen(&buf[0]) == 0) {
        if (i==0) {
          strcpy(&buf[0], "Main");
        } else {
          strcpy(&buf[0], "???");
        }
      }
      fprintf(stderr, "%33s:%9ld:%9ld:%9ld:%10ld:%10ld\n", buf,
              threads_counters_malloc_count[i], threads_counters_realloc_count[i], threads_counters_free_count[i],
              threads_counters_actual_malloced[i], threads_counters_actual_freed[i]);
    } else {
      break;
    }
  }
  
  return threads_limit_reached;
}

void NMT_MemoryLogRecorder::print_summary(Entry* entries, size_t count) {
  size_t total_mallocs = 0;
  size_t total_reallocs = 0;
  size_t total_frees = 0;
  size_t total_requested_malloced = 0;
  size_t total_actual_malloced = 0;
  size_t total_actual_freed = 0;
  size_t count_NMTObjects = 0;
  size_t overhead_NMTObjects = 0;
  size_t overhead_NMTHeaders = 0;
  for (size_t c=0; c<count; c++) {
    Entry* e = &entries[c];
    if (NMT_MemoryLogRecorder::is_alloc(e)) {
      total_requested_malloced += e->requested;
      total_actual_malloced += e->actual;
      if (NMT_MemoryLogRecorder::is_malloc(e)) {
        total_mallocs++;
      } else if (NMT_MemoryLogRecorder::is_realloc(e)) {
        total_reallocs++;
      }
      if (e->flags == mtNMT) {
        count_NMTObjects++;
        overhead_NMTObjects += e->actual;
      }
      overhead_NMTHeaders += MemTracker::overhead_per_malloc();
    } else {
      total_frees++;
      total_actual_freed += e->actual;
    }
  }
  size_t total_overhead = (total_actual_malloced - total_requested_malloced);

  fprintf(stderr, "-----------------------------------------------------------------------------------------\n");
  fprintf(stderr, "                           TOTALS:%9ld:%9ld:%9ld:%10ld:%10ld\n",
          total_mallocs, total_reallocs, total_frees, total_actual_malloced, total_actual_freed);
  fprintf(stderr, "Total (#mallocs + #reallocs + #frees) counts: %ld\n",
          (total_mallocs + total_reallocs + total_frees));
  fprintf(stderr, "Total current allocated (total_actual_malloced - total_actual_freed) bytes: %ld\n",
          (total_actual_malloced - total_actual_freed));

  fprintf(stderr, "\n\n");
  fprintf(stderr, "                   Total lifetime total_requested: %9ld bytes, %4ld Mb\n",
          total_requested_malloced, total_requested_malloced/1024/1024);
  fprintf(stderr, "                      Total lifetime total_actual: %9ld bytes, %4ld Mb\n",
          total_actual_malloced, total_actual_malloced/1024/1024);
  double overhead_ratio_requested = (100.0 * ((double)total_overhead / (double)total_requested_malloced));
  double overhead_ratio_actual    = (100.0 * ((double)total_overhead / (double)total_actual_malloced));
  fprintf(stderr, "            Total lifetime overhead due to malloc: %9ld bytes, %4ld Mb : %.2f%c, %.2f%c [#%zu]\n",
          total_overhead, total_overhead/1024/1024,
          overhead_ratio_requested, '%',
          overhead_ratio_actual, '%',
          (total_mallocs + total_reallocs));
  
  if (count_NMTObjects > 0) {
    double overhead_NMTHeaders_ratio_requested = (100.0 * ((double)overhead_NMTHeaders / (double)total_requested_malloced));
    double overhead_NMTHeaders_ratio_actual    = (100.0 * ((double)overhead_NMTHeaders / (double)total_actual_malloced));
    double overhead_NMTObjects_ratio_requested = (100.0 * ((double)overhead_NMTObjects / (double)total_requested_malloced));
    double overhead_NMTObjects_ratio_actual    = (100.0 * ((double)overhead_NMTObjects / (double)total_actual_malloced));

    fprintf(stderr, "       Total lifetime overhead due to NMT objects: %9ld bytes, %4ld Mb : %.2f%c, %.2f%c [#%zu]\n",
            overhead_NMTObjects, overhead_NMTObjects/1024/1024,
            overhead_NMTObjects_ratio_requested, '%',
            overhead_NMTObjects_ratio_actual, '%',
            count_NMTObjects);
    fprintf(stderr, "       Total lifetime overhead due to NMT headers: %9ld bytes, %4ld Mb : %.2f%c, %.2f%c\n",
            overhead_NMTHeaders, overhead_NMTHeaders/1024/1024,
            overhead_NMTHeaders_ratio_requested, '%',
            overhead_NMTHeaders_ratio_actual, '%');
  }
  fprintf(stderr, "\n\n");
}

void NMT_MemoryLogRecorder::dump(Entry* entries, size_t count) {
  if (PrintRecordedNMTEntries) {
    print_records(entries, count);
  }
  
  bool threads_limit_reached = print_by_thread(entries, count);
  print_summary(entries, count);
  print_histogram(entries, count);
  
  if (threads_limit_reached) {
    fprintf(stderr, "WARNING: reached _threads_max limit: %d\n\n", _threads_max);
  }
  if ((long)count == RecordNMTEntries) {
    fprintf(stderr, "WARNING: reached RecordNMTEntries limit: %zu\n\n", count);
  }
  fprintf(stderr, "\nDONE!\n\n");
  //fprintf(stderr, "MemTracker::overhead_per_malloc(): %zu\n", MemTracker::overhead_per_malloc());
}

void NMT_MemoryLogRecorder::log(size_t requested, address ptr, address old, MEMFLAGS flags, const NativeCallStack *stack) {
  static pthread_mutex_t _mutex = PTHREAD_MUTEX_INITIALIZER;
  static Entry* _entries = nullptr;
  static size_t _count = 0;
  static size_t _max = RecordNMTEntries;
  volatile static bool done = false;

  if (!done && RecordNMTEntries) {
    // if we reach max or hit "special" marker we are done
    if ((_count == _max) || ((requested == 0) && (ptr == nullptr))) {
      done = true;
      dump(_entries, _count);
    } else {
      Entry* _entry = nullptr;
      pthread_mutex_lock(&_mutex);
      {
        if (_entries == nullptr) {
          _entries = (Entry*)calloc(_max+1, sizeof(Entry));
          assert(_entries != nullptr, "precondition");
        }
        _entry = &_entries[_count++];
      }
      pthread_mutex_unlock(&_mutex);

      if (_entry != nullptr) {
#if defined(LINUX) || defined(__APPLE__)
        _entry->thread = (address)pthread_self();
#endif
        _entry->ptr = ptr;
        _entry->old = old;
        _entry->requested = requested;
        if (_entry->requested > 0) {
          _entry->requested += MemTracker::overhead_per_malloc();
        }
#if defined(LINUX)
        _entry->actual = malloc_usable_size(ptr);
#elif  defined(WINDOWS)
        _entry->actual = _msize(ptr);
#elif  defined(__APPLE__)
        _entry->actual = malloc_size(ptr);
#endif
        _entry->flags = flags;
        if (stack != nullptr) {
          for (int i=0; i<NMT_TrackingStackDepth; i++) {
            _entry->stack[i] = stack->get_frame(i);
          }
        }
      }
    }
  }
}

#endif // ASSERT
