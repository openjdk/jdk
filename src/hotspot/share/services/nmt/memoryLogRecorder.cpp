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
constexpr int _threads_max = 64;

int compare(const void* ptr_a, const void* ptr_b) {
  size_t a = * ( (size_t*) ptr_a );
  size_t b = * ( (size_t*) ptr_b );
  if ( a > b ) return 1;
  else if ( a < b ) return -1;
  else return 0;
}

static inline double percent_diff(double initial_value, double final_value) {
  return 100.0 * (final_value - initial_value) / initial_value;
}

static inline double ratio(double smaller, double bigger) {
  return 100.0 * smaller / bigger;
}

// on macOS malloc currently (macOS 13) returns the same value for same sizes
// on Linux malloc can return different values for the same sizes
static inline size_t _malloc_good_size_impl(size_t size) {
  void *ptr = malloc(size);
  assert(ptr != nullptr, "must be, size=%zu", size);
  size_t actual = 0;
#if defined(LINUX)
  actual = malloc_usable_size(ptr);
#elif defined(WINDOWS)
  actual = _msize(ptr);
#elif defined(__APPLE__)
  actual = malloc_size(ptr);
#endif
  free(ptr);
  return actual;
}

static size_t good_sizes_counts[_buckets_max] = { 0 };
static size_t good_sizes_totals[_buckets_max] = { 0 };
static size_t good_sizes_requested[_buckets_max] = { 0 };
static inline size_t _malloc_good_size_stats(size_t size) {
  for (size_t i=0; i<_buckets_max; i++) {
    if (good_sizes_requested[i] == size) {
      // return average actual size
      return good_sizes_totals[i]/good_sizes_counts[i];
    }
  }
  // don't have this size in our stats, so estimate it
  return _malloc_good_size_impl(size);
}

static inline size_t _malloc_good_size(size_t size) {
  return _malloc_good_size_stats(size);
}

void NMT_MemoryLogRecorder::calculate_good_sizes(Entry* entries, size_t count) {
  for (size_t c=0; c<count; c++) {
    Entry* e = &entries[c];
    if (NMT_MemoryLogRecorder::is_alloc(e)) {
      for (int i=0; i<_buckets_max; i++) {
        if (good_sizes_requested[i] == 0) {
          good_sizes_requested[i] = e->requested;
          break;
        }
        else if (good_sizes_requested[i] == e->requested) {
          break;
        }
      }
    }
  }
  qsort(good_sizes_requested, _buckets_max, sizeof(size_t), compare);

  for (size_t c=0; c<count; c++) {
    Entry* e = &entries[c];
    if (NMT_MemoryLogRecorder::is_alloc(e)) {
      for (int i=0; i<_buckets_max; i++) {
        if (good_sizes_requested[i] == e->requested) {
          good_sizes_counts[i] += 1;
          good_sizes_totals[i] += e->actual;
          break;
        }
      }
    }
  }

#if 0
  fprintf(stderr, "\n");
  size_t b = 0;
  for (size_t i=0; i<_buckets_max; i++) {
    if (good_sizes_requested[i] > 0) {
      fprintf(stderr, "%3ld %8ld %8ld %12ld [%12ld][%.3f]\n",
      b++, good_sizes_requested[i], good_sizes_counts[i], good_sizes_totals[i],
      good_sizes_totals[i]/good_sizes_counts[i], (double)good_sizes_totals[i]/(double)good_sizes_counts[i]);
    }
  }
  fprintf(stderr, "\n");
#endif
}

bool NMT_MemoryLogRecorder::print_histogram(Entry* entries, size_t count) {
  static size_t histogram_counts[_buckets_max] = { 0 };
  static size_t histogram_actual_sizes[_buckets_max] = { 0 };
  static size_t histogram_requested_sizes[_buckets_max] = { 0 };
  
  size_t total_requested = 0;
  size_t total_actual = 0;
  for (size_t c=0; c<count; c++) {
    Entry* e = &entries[c];
    if (NMT_MemoryLogRecorder::is_alloc(e)) {
      total_requested += e->requested;
      total_actual += e->actual;
    }
  }
  size_t alloc_overhead = (total_actual - total_requested);
  
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
  fprintf(stderr, "----------------------------------------------\n");
  fprintf(stderr, "requested:    actual: overhead: ratio:  count:\n");
  for (int i=0; i<_buckets_max; i++) {
    if (histogram_requested_sizes[i] > 0) {
      malloc_buckets_count++;
      
      double bar = _horizontal_space * (double)histogram_counts[i] / (double)max_bucket_count;
      size_t mark = (size_t)MAX(MIN(sqrt(_horizontal_space*bar), _horizontal_space), 0.0);
      char flag = (histogram_requested_sizes[i] == histogram_actual_sizes[i]) ? '=' : ' ';
      size_t o = histogram_counts[i] * (histogram_actual_sizes[i] - histogram_requested_sizes[i]);
      double o_ratio = ratio(o, alloc_overhead);
      
      r_total += histogram_counts[i] * histogram_requested_sizes[i];
      a_total += histogram_counts[i] * histogram_actual_sizes[i];
      o_total += o;
      c_total += histogram_counts[i];
      
      fprintf(stderr, "%9zu%c %9zu %9zu   %02.3f  %6zu ",
              histogram_requested_sizes[i], flag, histogram_actual_sizes[i], o, o_ratio, histogram_counts[i]);
      for (size_t j=0; j<mark; j++) {
        fprintf(stderr, "*");
      }
      fprintf(stderr, "\n");
    }
  }
  fprintf(stderr, "\nnative malloc used %zu buckets\n\n", malloc_buckets_count);

//  assert((count_mallocs+count_reallocs) == c_total, "(count_mallocs+count_reallocs):%zu == ctotal:%zu", (count_mallocs+count_reallocs), c_total);
  assert(r_total == total_requested, "r_total:%zu == total_requested:%zu", r_total, total_requested);
  assert(a_total == total_actual, "a_total:%zu == total_actual:%zu", a_total, total_actual);
  assert(a_total-r_total == o_total, "a_total-r_total:%zu == o_total:%zu", a_total-r_total, o_total);
  assert(alloc_overhead == o_total, "alloc_overhead:%zu == total_requested:%zu", alloc_overhead, o_total);

  return (malloc_buckets_count >= _buckets_max-1);
}

void NMT_MemoryLogRecorder::print_entry(Entry* e) {
  fprintf(stderr, "{ %18p, %18p", e->ptr, e->old);
  for (int i=0; i<NMT_TrackingStackDepth; i++) {
    fprintf(stderr, ", %18p", e->stack[i]);
  }
  fprintf(stderr, ", %7u, %7u, %7u, \"%s\"},\n", (unsigned)e->requested, (unsigned)e->actual,
          (unsigned)e->flags, NMTUtil::flag_to_name(e->flags));
}

void NMT_MemoryLogRecorder::print_records(Entry* entries, size_t count) {
  for (size_t c=0; c<count; c++) {
    Entry* e = &entries[c];
    print_entry(e);
  }
}

bool NMT_MemoryLogRecorder::print_by_thread(Entry* entries, size_t count) {
  void* threads[_threads_max] = { nullptr };
  static size_t threads_counters_malloc_count[_threads_max] = { 0 };
  static size_t threads_counters_realloc_count[_threads_max] = { 0 };
  static size_t threads_counters_free_count[_threads_max] = { 0 };

  bool threads_limit_reached = false;
  size_t count_mallocs = 0;
  size_t count_reallocs = 0;
  size_t count_frees = 0;

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
        if (is_malloc(e)) {
          threads_counters_malloc_count[i]++;
          count_mallocs++;
        } else if (is_realloc(e)) {
          threads_counters_realloc_count[i]++;
          count_reallocs++;
        } else if (is_free(e)) {
          threads_counters_free_count[i]++;
          count_frees++;
        }
        break;
      }
    }
  }

  static size_t threads_counters_requested_alloced[_threads_max] = { 0 };
  static size_t threads_counters_actual_alloced[_threads_max] = { 0 };
  static size_t threads_counters_actual_freed[_threads_max] = { 0 };
  size_t lifetime_requested = 0;
  size_t lifetime_actual = 0;
  size_t lifetime_freed = 0;

  // count the total_actual bytes that were allocated/freed by the OS
  for (size_t c=0; c<count; c++) {
    Entry* e = &entries[c];
    for (int i=0; i<_threads_max; i++) {
      if (threads[i] == e->thread) {
        if (NMT_MemoryLogRecorder::is_alloc(e)) {
          lifetime_requested += e->requested;
          threads_counters_requested_alloced[i] += e->requested;
          lifetime_actual += e->actual;
          threads_counters_actual_alloced[i] += e->actual;
        } else { // free
          lifetime_freed += e->actual;
          threads_counters_actual_freed[i] += e->actual;
        }
        break;
      }
    }
  }

  fprintf(stderr, "\n");
  fprintf(stderr, "------------------------------------------------------------------------------------------------------\n");
  fprintf(stderr, "                      thread name:  mallocs: reallocs:     free:   requested:   allocated:      freed:\n");
  fprintf(stderr, "                                     (count)   (count)   (count)      (bytes)      (bytes)     (bytes)\n");
  fprintf(stderr, "------------------------------------------------------------------------------------------------------\n");
  for (int i=0; i<_threads_max; i++) {
    if (threads[i] != nullptr) {
      char buf[32] = { 0 };
#if defined(LINUX) || defined(__APPLE__)
      pthread_getname_np((pthread_t)threads[i], &buf[0], sizeof(buf));
#elif defined(WINDOWS)
      // ???
#endif
      if (strlen(&buf[0]) == 0) {
        if (i==0) {
          strcpy(&buf[0], "Main");
        } else {
          strcpy(&buf[0], "???");
        }
      }
      fprintf(stderr, "%33s:%9ld:%9ld:%9ld:%12ld:%12ld:%12ld\n", buf,
              threads_counters_malloc_count[i], threads_counters_realloc_count[i], threads_counters_free_count[i],
              threads_counters_requested_alloced[i], threads_counters_actual_alloced[i], threads_counters_actual_freed[i]);
    } else {
      break;
    }
  }

  fprintf(stderr, "------------------------------------------------------------------------------------------------------\n");
  fprintf(stderr, "                TOTALS (lifetime):%9ld:%9ld:%9ld:%12ld:%12ld:%12ld\n",
          count_mallocs, count_reallocs, count_frees, lifetime_requested, lifetime_actual, lifetime_freed);

  return threads_limit_reached;
}

NMT_MemoryLogRecorder::Entry* NMT_MemoryLogRecorder::find_free_entry(Entry* entries, size_t count) {
  Entry* e = &entries[count];
  for (size_t b=count-1; b>0; b--) {
    Entry* found = &entries[b];
    if (found->ptr == e->ptr) {
      return found;
    }
  }
  return nullptr;
}

NMT_MemoryLogRecorder::Entry* NMT_MemoryLogRecorder::find_realloc_entry(Entry* entries, size_t count) {
  Entry* e = &entries[count];
  for (size_t b=count-1; b>0; b--) {
    Entry* found = &entries[b];
    if (found->ptr == e->old) {
      return found;
    }
  }
  return nullptr;
}

void NMT_MemoryLogRecorder::print_summary(Entry* entries, size_t count) {
  calculate_good_sizes(entries, count);
  size_t overhead_per_malloc = MemTracker::overhead_per_malloc();
  long total_requested = 0;
  long total_requested_no_NMT = 0;
  long total_actual = 0;
  long total_actual_no_NMT = 0;
  long total_NMTObjects = 0;
  long count_mallocs = 0;
  long count_reallocs = 0;
  long count_frees = 0;
  long count_Objects = 0;
  long count_NMTObjects = 0;
  size_t gap = count / 100;
  for (size_t c=0; c<count; c++) {
    if (c%gap == 0) {
      fprintf(stderr, "%ld ", (100 - (c/gap)));
    }
    Entry* e = &entries[c];
    if (NMT_MemoryLogRecorder::is_alloc(e)) {
      if (NMT_MemoryLogRecorder::is_malloc(e)) {
        count_mallocs++;
        count_Objects++;
        total_requested += e->requested;
        total_actual += e->actual;
        if (is_nmt(e)) {
          count_NMTObjects++;
          total_NMTObjects += e->actual;
        } else {
          total_requested_no_NMT += (e->requested - overhead_per_malloc);
          total_actual_no_NMT += _malloc_good_size(e->requested - overhead_per_malloc); // this is an estimate
        }
      } else if (NMT_MemoryLogRecorder::is_realloc(e)) {
        count_reallocs++;
        Entry* found = find_realloc_entry(entries, c);
        if (found != nullptr) {
          assert(found->flags == e->flags, "found->flags == e->flags");
          assert(NMT_MemoryLogRecorder::is_alloc(found), "NMT_MemoryLogRecorder::is_alloc(found)");
//          fprintf(stderr, "\nREALLOC:\n");
//          print_entry(found);
//          print_entry(e);
          if (is_nmt(e)) {
            total_NMTObjects += e->actual;
            total_requested += e->requested;
            total_actual += e->actual;

            total_NMTObjects -= found->actual;
            total_requested -= found->requested;
            total_actual -= found->actual;
          } else {
            total_requested += e->requested;
            total_actual += e->actual;
            total_requested_no_NMT += (e->requested - overhead_per_malloc);
            total_actual_no_NMT += _malloc_good_size(e->requested - overhead_per_malloc); // this is an estimate

            total_requested -= found->requested;
            total_actual -= found->actual;
            total_requested_no_NMT -= (found->requested - overhead_per_malloc);
            total_actual_no_NMT -= _malloc_good_size(found->requested - overhead_per_malloc); // this is an estimate
          }
        }
      }
    } else if (NMT_MemoryLogRecorder::is_free(e)) {
      Entry* found = find_free_entry(entries, c);
      if (found != nullptr) {
        assert(found->actual == e->actual, "found->actual == e->actual");
        assert(NMT_MemoryLogRecorder::is_alloc(found), "NMT_MemoryLogRecorder::is_alloc(found)");
//        fprintf(stderr, "FREE:\n");
//        print_entry(found);
//        print_entry(e);
        count_Objects--;
        count_frees++;
        total_requested -= found->requested;
        total_actual -= found->actual;
        if (is_nmt(e)) {
          count_NMTObjects--;
          total_NMTObjects -= found->actual;
        } else {
          total_requested_no_NMT -= (found->requested - overhead_per_malloc);
          total_actual_no_NMT -= _malloc_good_size(found->requested - overhead_per_malloc); // this is an estimate
        }
      }
    }
  }
  fprintf(stderr, "\n");
  
  long alloc_overhead = (total_actual - total_requested);

  fprintf(stderr, "\n\n");
  fprintf(stderr, "                             Current requested: %12ld bytes, %4ld Mb\n",
          total_requested, total_requested/1024/1024);
  fprintf(stderr, "                                Current actual: %12ld bytes, %4ld Mb\n",
          total_actual, total_actual/1024/1024);
  double overhead_ratio_requested = ratio(alloc_overhead, total_requested);
  double overhead_ratio_actual    = ratio(alloc_overhead, total_actual);
  fprintf(stderr, "            Overhead due to malloc rounding up: %12ld bytes, %4ld Mb : %.3f%%, %.3f%% [#%zu]\n",
          alloc_overhead, alloc_overhead/1024/1024,
          overhead_ratio_requested,
          overhead_ratio_actual,
          (count_Objects));

  if (overhead_per_malloc > 0) {
    size_t total_NMTHeaders = count_Objects * overhead_per_malloc;
    double total_NMTHeaders_ratio_requested = ratio(total_NMTHeaders, total_requested);
    double total_NMTHeaders_ratio_actual    = ratio(total_NMTHeaders, total_actual);
    double total_NMTObjects_ratio_requested = ratio(total_NMTObjects, total_requested);
    double total_NMTObjects_ratio_actual    = ratio(total_NMTObjects, total_actual);

    fprintf(stderr, "                   Overhead due to NMT headers: %12ld bytes, %4ld Mb : %.3f%%, %.3f%% [#%zu]\n",
            total_NMTHeaders, total_NMTHeaders/1024/1024,
            total_NMTHeaders_ratio_requested,
            total_NMTHeaders_ratio_actual,
            count_Objects);
    fprintf(stderr, "                   Overhead due to NMT objects: %12ld bytes, %4ld Mb : %.3f%%, %.3f%% [#%zu]\n",
            total_NMTObjects, total_NMTObjects/1024/1024,
            total_NMTObjects_ratio_requested,
            total_NMTObjects_ratio_actual,
            count_NMTObjects);

    fprintf(stderr, "\n\n");
    long alloc_overhead_no_NMT = (total_actual_no_NMT - total_requested_no_NMT);
    fprintf(stderr, "                    Current requested (no NMT): %12ld bytes, %4ld Mb\n",
            total_requested_no_NMT, total_requested_no_NMT/1024/1024);
    fprintf(stderr, "                       Current actual (no NMT): %12ld bytes, %4ld Mb\n",
            total_actual_no_NMT, total_actual_no_NMT/1024/1024);
    double overhead_ratio_requested_no_NMT = ratio(alloc_overhead_no_NMT, total_requested_no_NMT);
    double overhead_ratio_actual_no_NMT    = ratio(alloc_overhead_no_NMT, total_actual_no_NMT);
    fprintf(stderr, "   Overhead due to malloc rounding up (no NMT): %12ld bytes, %4ld Mb : %.3f%%, %.3f%% [#%zu]\n",
            alloc_overhead_no_NMT, alloc_overhead_no_NMT/1024/1024,
            overhead_ratio_requested_no_NMT,
            overhead_ratio_actual_no_NMT,
            (count_Objects - count_NMTObjects));

    double diff = percent_diff(total_actual_no_NMT, total_actual);
    fprintf(stderr, "\n");
    fprintf(stderr, "NMT overhead (current actual memory allocated): %12.3f%%\n", diff);
  }

#if 1
  fprintf(stderr, "\n\n");
  fprintf(stderr, "overhead_per_malloc:     %12ld\n", overhead_per_malloc);
  fprintf(stderr, "total_requested:         %12ld\n", total_requested);
  fprintf(stderr, "total_requested_no_NMT:  %12ld\n", total_requested_no_NMT);
  fprintf(stderr, "total_actual:            %12ld\n", total_actual);
  fprintf(stderr, "total_actual_no_NMT:     %12ld\n", total_actual_no_NMT);
  fprintf(stderr, "total_NMTObjects:        %12ld\n", total_NMTObjects);
  fprintf(stderr, "count_mallocs:           %12ld\n", count_mallocs);
  fprintf(stderr, "count_reallocs:          %12ld\n", count_reallocs);
  fprintf(stderr, "count_frees:             %12ld\n", count_frees);
  fprintf(stderr, "count_Objects:           %12ld\n", count_Objects);
  fprintf(stderr, "count_NMTObjects:        %12ld\n", count_NMTObjects);
  fprintf(stderr, "#mallocs + #reallocs + #frees: %ld counts\n",
          (count_mallocs + count_reallocs + count_frees));
#endif
}
/*
 HelloWorld:
 
 // Real     Priv     Shared  Memory
 // 74.7 MB  48.6 MB  3.1 MB  55.6 MB
 
                                        Current requested:     25326747 bytes,   24 Mb
                                           Current actual:     25475952 bytes,   24 Mb
                       Overhead due to malloc rounding up:       149205 bytes,    0 Mb : 0.589%, 0.586% [#24882]

 
 // Real     Priv     Shared  Memory
 // 75.4 MB  48.6 MB  3.1 MB  56.3 MB

                                        Current requested:     25938937 bytes,   24 Mb
                                           Current actual:     27142320 bytes,   25 Mb
                       Overhead due to malloc rounding up:      1203383 bytes,    1 Mb : 4.639%, 4.434% [#27662]
                              Overhead due to NMT headers:       497916 bytes,    0 Mb : 1.920%, 1.834% [#27662]
                              Overhead due to NMT objects:       313344 bytes,    0 Mb : 1.208%, 1.154% [#2784]


                               Current requested (no NMT):     25194949 bytes,   24 Mb
                                  Current actual (no NMT):     25343632 bytes,   24 Mb
              Overhead due to malloc rounding up (no NMT):       148683 bytes,    0 Mb : 0.590%, 0.587% [#24878]

           NMT overhead (current actual memory allocated):        7.097%


J2Ddemo:
 
 // Real      Priv      Shared   Memory
 // 373.1 MB  272.8 MB  58.4 MB  293.9 MB

                                        Current requested:     45959424 bytes,   43 Mb
                                           Current actual:     46737360 bytes,   44 Mb
                       Overhead due to malloc rounding up:       777936 bytes,    0 Mb : 1.693%, 1.664% [#180316]


 
 // Real      Priv      Shared   Memory
 // 404.8 MB  292.6 MB  58.8 MB  310.6 MB
 
 diff:
    11.5      2.3       0.4      0.6
 
                                      Current requested:     53854696 bytes,   51 Mb
    Current actual:     56798576 bytes,   54 Mb
Overhead due to malloc rounding up:      2943880 bytes,    2 Mb : 5.466%, 5.183% [#185961]
Overhead due to NMT headers:      3347298 bytes,    3 Mb : 6.215%, 5.893% [#185961]
Overhead due to NMT objects:       428272 bytes,    0 Mb : 0.795%, 0.754% [#3805]


Current requested (no NMT):     50171366 bytes,   47 Mb
Current actual (no NMT):     50953408 bytes,   48 Mb
Overhead due to malloc rounding up (no NMT):       782042 bytes,    0 Mb : 1.559%, 1.535% [#182156]

NMT overhead (current actual memory allocated):       11.472%

*/

void NMT_MemoryLogRecorder::dump(Entry* entries, size_t count) {
  
  fprintf(stderr, "\nProcessing recorded NMT entries ...\n");

#if 0
  fprintf(stderr, "\nnapping: ");
  int nap = 30;
  for (int i=0; i<nap; i++) {
    fprintf(stderr, "%d ", 30-i); fflush(stderr);
    sleep(1);
  }
  fprintf(stderr, "\n\n");
#endif

  bool buckets_limit_reached = false;
  bool threads_limit_reached = false;

  if (PrintRecordedNMTEntries) {
    print_records(entries, count);
  }
  
#if 0
  fprintf(stderr, "\nProcessing histograms ...\n");
  buckets_limit_reached = print_histogram(entries, count);
#endif

  fprintf(stderr, "\nProcessing memory usage by thread ...\n");
  threads_limit_reached = print_by_thread(entries, count);

  fprintf(stderr, "\nProcessing memory summary ...\n");
  print_summary(entries, count);

  fprintf(stderr, "\n\n");
  if (buckets_limit_reached) {
    fprintf(stderr, "WARNING: reached _buckets_max limit: %d\n", _buckets_max);
  }
  if (threads_limit_reached) {
    fprintf(stderr, "WARNING: reached _threads_max limit: %d\n", _threads_max);
  }
  if (count == (size_t)RecordNMTEntries) {
    fprintf(stderr, "WARNING: reached RecordNMTEntries limit: %ld\n", count);
  }
  fprintf(stderr, "\nDONE!\n\n");
  //fprintf(stderr, "MemTracker::overhead_per_malloc(): %zu\n", MemTracker::overhead_per_malloc());
}

void NMT_MemoryLogRecorder::log(MEMFLAGS flags, size_t requested, address ptr, address old, const NativeCallStack *stack) {
  static pthread_mutex_t _mutex = PTHREAD_MUTEX_INITIALIZER;
  static Entry* _entries = nullptr;
  volatile static size_t count = 0;
  volatile static bool done = (RecordNMTEntries==0);

  Entry* _entry = nullptr;
  if (!done) {
    pthread_mutex_lock(&_mutex);
    {
      if (_entries == nullptr) {
        _entries = (Entry*)calloc(RecordNMTEntries+1, sizeof(Entry));
        assert(_entries != nullptr, "_entries != nullptr");
      }
      if (!done) {
        done = ((count >= (size_t)RecordNMTEntries) || ((requested == 0) && (ptr == nullptr)));
        // if we reach max or hit "special" marker, then we are done
        if (!done) {
          _entry = &_entries[count++];
        } else {
          dump(_entries, count);
          free((void*)_entries);
          _entries = nullptr;
          exit(0);
        }
      }
    }
    pthread_mutex_unlock(&_mutex);
  }

  if (_entry != nullptr) {
#if defined(LINUX) || defined(__APPLE__)
    _entry->thread = (address)pthread_self();
#elif defined(WINDOWS)
    _entry->thread = nullptr; // ???
#endif
    _entry->ptr = ptr;
    _entry->old = old;
    _entry->requested = requested;
    if (_entry->requested > 0) {
      _entry->requested += MemTracker::overhead_per_malloc();
    }
#if defined(LINUX)
    _entry->actual = malloc_usable_size(ptr);
#elif defined(WINDOWS)
    _entry->actual = _msize(ptr);
#elif defined(__APPLE__)
    _entry->actual = malloc_size(ptr);
#endif
    _entry->flags = flags;
    if (stack != nullptr) {
      for (int i=0; i<NMT_TrackingStackDepth; i++) {
        _entry->stack[i] = stack->get_frame(i);
      }
    }
    if (_entry->requested > 0) {
//#if defined(LINUX)
//     size_t good_size = _malloc_good_size_impl(_entry->requested);
//     if (_entry->actual != good_size) {
//       fprintf(stderr, ">>> %zu != %zu:%zu\n", _entry->actual, good_size, _entry->requested);
//     }
//     // assert(_entry->actual == good_size, "%zu != _malloc_good_size_impl(%zu):%zu",
//     //        _entry->actual, _entry->requested, good_size);
//#endif
#if defined(WINDOWS)
      //???
#endif
#if defined(__APPLE__)
      size_t good_size = malloc_good_size(_entry->requested);
      assert(_entry->actual == good_size, "%zu != malloc_good_size(%zu):%zu",
             _entry->actual, _entry->requested, good_size);
#endif
    } else {
//#if 0
//      for (size_t i=0; i<_entry->actual; i++)
//      {
//        u_char *b = (u_char*)&_entry->ptr[i];
//        *b = 0x00;
//      }
//#endif
    }
  }
}

#endif // ASSERT
