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
 */

#include "precompiled.hpp"
#include "nmt/memTracker.hpp"
#include "nmt/nmtCommon.hpp"
#include "nmt/nmtPreInit.hpp"
#include "memory/allocation.hpp"
#include "runtime/os.hpp"
#include "services/mallocLimit.hpp"
#include "utilities/debug.hpp"
#include "utilities/ostream.hpp"

#include "unittest.hpp"
#include "testutils.hpp"

#include "test_nmt_startup_data_mac.h"
#include "test_nmt_startup_data_linux.h"

/*
 make test TEST="gtest:NMTPerformance.test_startup_memory" GTEST="JAVA_OPTIONS=-XX:NativeMemoryTracking=off"
 make test TEST="gtest:NMTPerformance.test_startup_memory" GTEST="JAVA_OPTIONS=-XX:NativeMemoryTracking=summary"
 make test TEST="gtest:NMTPerformance.test_startup_memory" GTEST="JAVA_OPTIONS=-XX:NativeMemoryTracking=detail"
 */

#define LOOPS_WARMUPS 50
#define LOOPS_BENCHMARK 1000

static jlong benchmark(int count, int* indexes, void** pointers, DataStruct* data) {
  jlong start = os::javaTimeNanos();
  for (int i=0; i<count; i++) {
    //fprintf(stderr, "  data[%d] requested:%10ld, actual:%10ld, pointer:%12p, pointer_prev:%12p\n",
    //        i, data[i].requested, data[i].actual, (void*)data[i].pointer, (void*)data[i].pointer_prev);
    if (data[i].requested > 0) { // malloc or realloc
      address frames[4] = { (address)data[i].frame1, (address)data[i].frame2, (address)data[i].frame3, (address)data[i].frame4 };
      NativeCallStack stack = NativeCallStack(&frames[0], sizeof(frames)/sizeof(address));
      if (data[i].pointer_prev == 0L) { // malloc
        ALLOW_C_FUNCTION(::malloc, pointers[i] = os::malloc(data[i].actual, NMTUtil::index_to_flag(data[i].flags), stack);)
        assert(pointers[i]!=nullptr, "malloc pointers[i]!=nullptr");
        //fprintf(stderr, "    malloc pointers[i]:%p\n", pointers[i]);
      } else { // realloc
        int index = indexes[i];
        // the pointer this realloc refers to might have not been captured in our record session
        // i.e. before NMT was initialized, and we only capture after NMT is initialized
        if (index >= 0) {
          assert(index >= 0, "realloc must be (%d > 0)", index);
          assert(pointers[index]!=nullptr, "realloc pointers[index]!=nullptr");
          //fprintf(stderr, "    pointers[%d]:%p\n", index, pointers[index]);
          ALLOW_C_FUNCTION(::realloc, pointers[i] = os::realloc(pointers[index], data[i].actual, NMTUtil::index_to_flag(data[i].flags), stack);)
          assert(pointers[i]!=nullptr, "realloc pointers[i]!=nullptr");
          pointers[index] = nullptr;
          //fprintf(stderr, "    realloc pointers[%d]:%p\n", i, pointers[i]);
        } else {
          // substitute malloc for realloc here, so that any "free" that references this "realloc" has something to reference
          ALLOW_C_FUNCTION(::malloc, pointers[i] = os::malloc(data[i].actual, NMTUtil::index_to_flag(data[i].flags), stack);)
          assert(pointers[i]!=nullptr, "substitute malloc pointers[i]!=nullptr");
          //fprintf(stderr, "    substitute malloc pointers[%d]:%p\n", i, pointers[i]);
        }
      }
    } else { // free
      int index = indexes[i];
      // the pointer this realloc refers to might have not been captured in our record session
      // i.e. before NMT was initialized, and we only capture after NMT is initialized
      if (index >= 0) {
        assert(index >= 0, "must be (%d > 0)", index);
        assert(pointers[index]!=nullptr, "free pointers[index]!=nullptr");
        //fprintf(stderr, "    free pointers[%6d]:%p\n", index, pointers[index]);
        ALLOW_C_FUNCTION(::free, os::free(pointers[index]);)
        pointers[index] = nullptr;
      }
    }
  }
  jlong stop = os::javaTimeNanos();
  return (stop-start);
}

static void free_remaining_pointers(int count, void** pointers) {
  for (int i=0; i<count; i++) {
    if (pointers[i] != nullptr) {
      //fprintf(stderr, " free pointers[%d]:%p\n", i, (void*)pointers[i]);
      ALLOW_C_FUNCTION(::free, os::free(pointers[i]);)
      pointers[i] = nullptr;
    }
  }
}

static void run_benchmarks(int count, int* indexes, void** pointers, DataStruct* data) {
  for (int t=0; t<LOOPS_WARMUPS; t++) {
    jlong duration = benchmark(count, indexes, pointers, data);
    //fprintf(stderr, "Warmup Time: %ld us\n", duration/1000);
    fprintf(stderr, "_");fflush(stderr);
    free_remaining_pointers(count, pointers);
  }

  jlong totals[LOOPS_BENCHMARK] = { 0 };
  for (int t=0; t<LOOPS_BENCHMARK; t++) {
    jlong duration = benchmark(count, indexes, pointers, data);
    //fprintf(stderr, "Test Time: %ld us\n", duration/1000);
    fprintf(stderr, ".");fflush(stderr);
    totals[t] = duration;
    free_remaining_pointers(count, pointers);
  }

  jlong avg = 0;
  for (int t=0; t<LOOPS_BENCHMARK; t++) {
    avg += totals[t];
  }
  avg /= LOOPS_BENCHMARK;
  fprintf(stderr, "\nAvg Total Time: %ld us\n", avg/1000);

  jlong allowed = (jlong)(1.25*avg);
  int counted = 0;
  avg = 0;
  for (int t=0; t<LOOPS_BENCHMARK; t++) {
    if (totals[t] < allowed) {
      counted++;
      avg += totals[t];
    }
  }
  avg /= counted;
  fprintf(stderr, "Clean Avg Total Time: %ld us\n", avg/1000);
}

static void collect_indexes(int count, int* indexes, void** pointers, DataStruct* data) {
  for (int i=count-1; i>=0; i--) {
    pointers[i] = nullptr;
    indexes[i] = -1;
    if (data[i].requested > 0) {
     if (data[i].pointer_prev != 0L) { // realloc
       indexes[i] = -2; // assume that we will not find the original pointer this realloc references
       for (int j=i-1; j>=0; j--) {
         if (data[i].pointer_prev == data[j].pointer) {
           indexes[i] = j;
           break;
         }
       }
     }
   } else if (data[i].requested == 0) { // free
      indexes[i] = -2; // assume that we will not find the original pointer this free references
      for (int j=i-1; j>=0; j--) {
        if (data[i].pointer == data[j].pointer) {
          indexes[i] = j;
          break;
        }
      }
    }
  }
  for (int i=count-1; i>=0; i--) {
    if (indexes[i] == -2) {
      fprintf(stderr, "pointer NOT FOUND\n");
    }
  }
}

void test(int count, int* indexes, void** pointers, DataStruct* data) {
  // look for pointers which we will need to use later for realloc/free now,
  // so that we don't include this phase in the performance timing
  collect_indexes(count, indexes, pointers, data);

  // give the VM time to "settle down"
  sleep(1);

  fprintf(stderr, "\n\n");
  run_benchmarks(count, indexes, pointers, data);
  fprintf(stderr, "\n\n");
  //MemTracker::tuning_statistics(tty);
  //MemTracker::final_report(tty);
}

#if defined(__APPLE__)

TEST_VM(NMTPerformance, test_startup_memory_mac_data) {
  int count = DATA_MAC_COUNT;
  ALLOW_C_FUNCTION(::calloc, int* indexes = (int*)calloc(count, sizeof(int));)
  ALLOW_C_FUNCTION(::calloc, void** pointers = (void**)calloc(count, sizeof(void*));)
  DataStruct* data = data_mac;

  assert(indexes!=nullptr, "indexes!=nullptr");
  assert(pointers!=nullptr, "pointers!=nullptr");

  test(count, indexes, pointers, data);

  ALLOW_C_FUNCTION(::free, ::free(pointers);)
  ALLOW_C_FUNCTION(::free, ::free(indexes);)
}

#elif defined(LINUX)

TEST_VM(NMTPerformance, test_startup_memory_linux_data) {
  int count = DATA_LINUX_COUNT;
  ALLOW_C_FUNCTION(::calloc, int* indexes = (int*)calloc(count, sizeof(int));)
  ALLOW_C_FUNCTION(::calloc, void** pointers = (void**)calloc(count, sizeof(void*));)
  DataStruct* data = data_linux;

  assert(indexes!=nullptr, "indexes!=nullptr");
  assert(pointers!=nullptr, "pointers!=nullptr");

  test(count, indexes, pointers, data);

  ALLOW_C_FUNCTION(::free, ::free(pointers);)
  ALLOW_C_FUNCTION(::free, ::free(indexes);)
}

#endif
