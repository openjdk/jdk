/*
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

// record pattern of allocations of memory calls:
//
// NMTRecordMemoryAllocations=0x7FFFFFFF ./build/macosx-aarch64-server-release/xcode/build/jdk/bin/java -XX:+UnlockDiagnosticVMOptions -XX:NativeMemoryTracking=summary -jar build/macosx-aarch64-server-release/images/jdk/demo/jfc/J2Ddemo/J2Ddemo.jar
//
// OR record pattern of allocations of virtual memory calls:
//
// NMTRecordVirtualMemoryAllocations=0x7FFFFFFF ./build/macosx-aarch64-server-release/xcode/build/jdk/bin/java -XX:+UnlockDiagnosticVMOptions -XX:NativeMemoryTracking=summary -jar build/macosx-aarch64-server-release/images/jdk/demo/jfc/J2Ddemo/J2Ddemo.jar
//
// this will produce 3 files:
//
// #1 hs_nmt_pid22770_allocs_record.log (is the chronological record of the the desired operations)
// OR
// #1 hs_nmt_pid22770_virtual_allocs_record.log (is the chronological record of the desired operations)
// #2 hs_nmt_pid22770_info_record.log (is the record of default NMT memory overhead and the NMT state)
// #3 hs_nmt_pid22770_threads_record.log (is the record of thread names that can be retrieved later when processing)
//
// then to actually run the benchmark:
//
// NMTBenchmarkRecordedPID=22770 ./build/macosx-aarch64-server-release/xcode/build/jdk/bin/java -XX:+UnlockDiagnosticVMOptions -XX:NativeMemoryTracking=summary

#include "jvm.h"
#include "nmt/nmtCommon.hpp"
#include "nmt/mallocHeader.hpp"
#include "nmt/mallocHeader.inline.hpp"
#include "nmt/memLogRecorder.hpp"
#include "nmt/memReporter.hpp"
#include "nmt/memTracker.hpp"
#include "nmt/memTracker.hpp"
#include "nmt/virtualMemoryTracker.hpp"
#include "runtime/arguments.hpp"
#include "runtime/globals.hpp"
#include "runtime/os.hpp"
#include "runtime/thread.hpp"
#include "utilities/debug.hpp"
#include "utilities/nativeCallStack.hpp"
#include "utilities/permitForbiddenFunctions.hpp"
#include "utilities/vmError.hpp"

bool NMTRecorder_Locker::_safe_to_use = false;

#if defined(LINUX) || defined(__APPLE__)

#include <locale.h>
#include <string.h>
#include <errno.h>

#if defined(LINUX)
#include <malloc.h>
#elif defined(__APPLE__)
#include <malloc/malloc.h>
#endif

#include <pthread.h>
#include <sys/mman.h>
#include <unistd.h>

#define LD_FORMAT "%'ld"

static void* raw_realloc(void* old, size_t s) { return permit_forbidden_function::realloc(old, s); }
#if defined(LINUX)
static size_t raw_malloc_size(void* ptr)      { return ::malloc_usable_size(ptr); }
#elif defined(_WIN64)
static size_t raw_malloc_size(void* ptr)      { return ::_msize(ptr); }
#elif defined(__APPLE__)
static size_t raw_malloc_size(void* ptr)      { return ::malloc_size(ptr); }
#endif


#define NMT_HEADER_SIZE 16

NMT_MemoryLogRecorder NMT_MemoryLogRecorder::_recorder;
NMT_VirtualMemoryLogRecorder NMT_VirtualMemoryLogRecorder::_recorder;

void NMT_LogRecorder::initialize() {
  char* NMTRecordMemoryAllocations = getenv("NMTRecordMemoryAllocations");
  if (NMTRecordMemoryAllocations != nullptr) {
    long count = atol(NMTRecordMemoryAllocations);
    if (count == 0) {
      count = strtol(NMTRecordMemoryAllocations, nullptr, 16);
    }
    NMT_MemoryLogRecorder::initialize(count);
  }
  char* NMTRecordVirtualMemoryAllocations = getenv("NMTRecordVirtualMemoryAllocations");
  if (NMTRecordVirtualMemoryAllocations != nullptr) {
    long count = atol(NMTRecordVirtualMemoryAllocations);
    if (count == 0) {
      count = strtol(NMTRecordVirtualMemoryAllocations, nullptr, 16);
    }
    NMT_VirtualMemoryLogRecorder::initialize(count);
  }
}

void NMT_LogRecorder::finish() {
  if (!NMT_MemoryLogRecorder::instance()->done()) {
    NMT_MemoryLogRecorder::instance()->finish();
  }
  if (!NMT_VirtualMemoryLogRecorder::instance()->done()) {
    NMT_VirtualMemoryLogRecorder::instance()->finish();
  }
}

void NMT_LogRecorder::replay() {
  char* NMTBenchmarkRecordedPID = getenv("NMTBenchmarkRecordedPID");
  if (NMTBenchmarkRecordedPID != nullptr) {
    int pid = atoi(NMTBenchmarkRecordedPID);
    NMT_MemoryLogRecorder::instance()->replay(pid);
    NMT_VirtualMemoryLogRecorder::instance()->replay(pid);
    os::exit(0);
  }
}

void NMT_LogRecorder::init() {
  _threads_names_size = 1;
  _threads_names = (thread_name_info*)raw_realloc(_threads_names, _threads_names_size*sizeof(thread_name_info));
  _done = true;
  _count = 0;
}

void NMT_LogRecorder::get_thread_name(char* buf) {
#if defined(__APPLE__)
  if (pthread_main_np()) {
    strcpy(buf, "main");
  } else {
    fprintf(stderr, "\n\nThread: %p:%p\n", pthread_self(), Thread::current());
    int err = pthread_getname_np(pthread_self(), buf, MAXTHREADNAMESIZE);
    fprintf(stderr, "pthread_getname_np: %d:%zu:%s\n", err, strlen(buf), strlen(buf)>0 ? buf:"N/A");
    if (Thread::current() != nullptr) {
      fprintf(stderr, "Thread::current()->name(): %s\n", Thread::current()->name());
    } else {
      fprintf(stderr, "Thread::current()->name(): N/A\n");
    }
  }
#elif defined(LINUX)
  pthread_getname_np(pthread_self(), buf, MAXTHREADNAMESIZE);
#elif defined(_WIN64)
  // TODO: NMT_LogRecorder::thread_name
#endif
}

// first time we see a new thread id, we add it
// second time we see a thread we get its name
void NMT_LogRecorder::logThreadName() {
  {
    bool found = false;
    long int tid = os::current_thread_id();
    for (size_t i = 0; i < _threads_names_size; i++) {
      if (_threads_names[i].thread == tid) {
        found = true;
        if (_threads_names[i].name[0] == 0) {
          NMT_LogRecorder::get_thread_name(_threads_names[i].name);
          // tty->print(" got name for thread %6ld:%lx [%s]\n", tid, tid, _threads_names[i].name);
        }
        break;
      }
    }
    if (!found) {
      // tty->print(" added:%6ld:%lx [%6zu]\n", tid, tid, _threads_names_size);
      size_t i = _threads_names_size-1;
      _threads_names[i].thread = tid;
      _threads_names[i].name[0] = 0;
      _threads_names_size++;
      _threads_names = (thread_name_info*)raw_realloc(_threads_names, _threads_names_size*sizeof(thread_name_info));
    }
  }
}

#define IS_FREE(e)           ((e->requested == 0) && (e->old == nullptr))
#define IS_REALLOC(e)        ((e->requested  > 0) && (e->old != nullptr))
#define IS_MALLOC(e)         ((e->requested  > 0) && (e->old == nullptr))

#define ALLOCS_LOG_FILE "hs_nmt_pid%p_allocs_record.log"
#define THREADS_LOG_FILE "hs_nmt_pid%p_threads_record.log"
#define INFO_LOG_FILE "hs_nmt_pid%p_info_record.log"
#define BENCHMARK_LOG_FILE "hs_nmt_pid%p_benchmark.log"
#define VALLOCS_LOG_FILE "hs_nmt_pid%p_virtual_allocs_record.log"

static int _prepare_log_file(const char* pattern, const char* default_pattern) {
  int fd = -1;
  if (ErrorFileToStdout) {
    fd = STDOUT_FILENO;
  } else if (ErrorFileToStderr) {
    fd = STDERR_FILENO;
  } else {
    static char name_buffer[O_BUFLEN];
    fd = VMError::prepare_log_file(pattern, default_pattern, true, name_buffer, sizeof(name_buffer));
    if (fd == -1) {
      int e = errno;
      tty->print("Can't open memory [%s]. Error: ", pattern?pattern:"null");
      tty->print_raw_cr(os::strerror(e));
      tty->print_cr("NMT memory recorder report will be written to console.");
      // See notes in VMError::report_and_die about hard coding tty to 1
      fd = 1;
    }
  }
  return fd;
}

#define IS_VALID_FD(fd) (fd > STDERR_FILENO)

static void _write_and_check(int fd, const void *buf, size_t count) {
  if (!IS_VALID_FD(fd)) {
    tty->print("write_and_check(%d) ERROR\n", fd);
    //assert(false, "fd: %d", fd);
  }
  errno = 0;
  ssize_t written = (ssize_t)::write(fd, buf, count);
  if ((long)written != (long)count) {
    int e = errno;
    tty->print("write_and_check(%d) ERROR:[%s]\n", fd, os::strerror(e));
    //assert((long)written != (long)count, "written != count [%ld,%ld]", (long)written, (long)count);
  }
}

static int _close_and_check(int fd) {
  if (!IS_VALID_FD(fd)) {
    tty->print("close_and_check(%d) ERROR\n", fd);
    return fd;
  }
  if (fd > STDERR_FILENO) {
    errno = 0;
    int status = close(fd);
    if (status != 0) {
      int e = errno;
      tty->print("ERROR:[%s]\n", os::strerror(e));
      assert(status != 0, "close(%d) returned %d", fd, status);
      return fd;
    } else {
      return -1;
    }
  } else {
    return fd;
  }
}

static bool _create_file_path_with_pid(const char *path, const char *file, char* file_path, int pid) {
  char *tmp_path = NEW_C_HEAP_ARRAY(char, JVM_MAXPATHLEN, mtNMT);
  strcpy(tmp_path, path);
  strcat(tmp_path, os::file_separator());
  strcat(tmp_path, file);
  if (!Arguments::copy_expand_pid(tmp_path, strlen(tmp_path), file_path, JVM_MAXPATHLEN, pid)) {
    FREE_C_HEAP_ARRAY(char, tmp_path);
    return false;
  } else {
    FREE_C_HEAP_ARRAY(char, tmp_path);
    return true;
  }
}

typedef struct file_info {
  void*   ptr;
  size_t  size;
  int     fd;
} file_info;

static file_info _open_file_and_read(const char* pattern, const char* path, int pid) {
  file_info info = { nullptr, 0, -1 };

  char *file_path = NEW_C_HEAP_ARRAY(char, JVM_MAXPATHLEN, mtNMT);
  if (!_create_file_path_with_pid(path, pattern, file_path, pid)) {
    tty->print("Can't construct path [%s:%s:%d].", pattern, path, pid);
    return info;
  }

  info.fd = os::open(file_path, O_RDONLY, 0);
  if (info.fd == -1) {
    int e = errno;
    tty->print("Can't open file [%s].", file_path);
    tty->print_raw_cr(os::strerror(e));
    return info;
  }

  struct stat file_info;
  ::fstat(info.fd, &file_info);
  info.size = file_info.st_size;
  ::lseek(info.fd, 0, SEEK_SET);

#if !defined(_WIN64)
  info.ptr = ::mmap(nullptr, info.size, PROT_READ, MAP_PRIVATE, info.fd, 0);
  assert(info.ptr != MAP_FAILED, "info.ptr != MAP_FAILED");
#endif

  FREE_C_HEAP_ARRAY(char, file_path);

  return info;
}

void NMT_MemoryLogRecorder::initialize(long int limit) {
  // tty->print("> NMT_MemoryLogRecorder::initialize(%ld, %ld)\n", limit, sizeof(Entry));
  NMT_MemoryLogRecorder *recorder = NMT_MemoryLogRecorder::instance();
  recorder->init();
  NMTRecorder_Locker locker;
  {
    recorder->_limit = limit;
    if (recorder->_limit > 0) {
      recorder->_log_fd = _prepare_log_file(nullptr, ALLOCS_LOG_FILE);
      recorder->_done = false;
    } else {
      recorder->_done = true;
    }
  }
}

void NMT_MemoryLogRecorder::finish(void) {
  NMT_MemoryLogRecorder *recorder = NMT_MemoryLogRecorder::instance();
  // tty->print("NMT_MemoryLogRecorder::finish() %p\n", NMT_MemoryLogRecorder::instance());
  if (!recorder->done()) {
    NMTRecorder_Locker locker;

    volatile int log_fd = recorder->_log_fd;
    recorder->_log_fd = -1;
    // tty->print(" log_fd:%d\n", log_fd);
    log_fd = _close_and_check(log_fd);
    // tty->print(" log_fd:%d\n", log_fd);

    int threads_fd = _prepare_log_file(nullptr, THREADS_LOG_FILE);
    // tty->print(" threads_fd:%d\n", threads_fd);
    if (threads_fd != -1) {
      _write_and_check(threads_fd, recorder->_threads_names, (recorder->_threads_names_size-1)*sizeof(thread_name_info));
      threads_fd = _close_and_check(threads_fd);
      // tty->print(" threads_fd:%d\n", threads_fd);
    }

    int info_fd = _prepare_log_file(nullptr, INFO_LOG_FILE);
    // tty->print(" info_fd:%d\n", info_fd);
    if (info_fd != -1) {
      size_t level = NMTUtil::parse_tracking_level(NativeMemoryTracking);
      _write_and_check(info_fd, &level, sizeof(level));
      size_t overhead = MemTracker::overhead_per_malloc();
      _write_and_check(info_fd, &overhead, sizeof(overhead));
      info_fd = _close_and_check(info_fd);
      // tty->print(" info_fd:%d\n", info_fd);
    }

    recorder->_done = true;
  }
  os::exit(0);
}

void NMT_MemoryLogRecorder::replay(const int pid) {
  // tty->print("NMT_MemoryLogRecorder::replay(%d)\n", pid);
  static const char *path = ".";
  NMT_MemoryLogRecorder *recorder = NMT_MemoryLogRecorder::instance();

  file_info log_fi = _open_file_and_read(INFO_LOG_FILE, path, pid);
  if (log_fi.fd == -1) {
    return;
  }
  size_t* status_file_bytes = (size_t*)log_fi.ptr;
  NMT_TrackingLevel recorded_nmt_level = (NMT_TrackingLevel)status_file_bytes[0];
  // compare the recorded and current levels of NMT and flag if different
  bool timeOnly = NMTUtil::parse_tracking_level(NativeMemoryTracking) != recorded_nmt_level;
  if (timeOnly) {
    tty->print("\n\nNativeMemoryTracking mismatch [%s != %s].\n", NMTUtil::tracking_level_to_string(recorded_nmt_level), NMTUtil::tracking_level_to_string(NMTUtil::parse_tracking_level(NativeMemoryTracking)));
    tty->print("(Can not be used for memory usage comparison)\n");
  }

  // open records file for reading the memory allocations to "play back"
  file_info records_fi = _open_file_and_read(ALLOCS_LOG_FILE, path, pid);
  if (records_fi.fd == -1) {
    return;
  }
  Entry* records_file_entries = (Entry*)records_fi.ptr;
  long int count = (long int)(records_fi.size / sizeof(Entry));
  long int size_pointers = (long int)(count * sizeof(address));
  address *pointers = nullptr;
#if !defined(_WIN64)
  pointers = (address*)::mmap(nullptr, size_pointers, PROT_READ|PROT_WRITE, MAP_PRIVATE|MAP_NORESERVE|MAP_ANONYMOUS, -1, 0);
  assert(pointers != MAP_FAILED, "pointers != MAP_FAILED");
#endif

  // open benchmark file for writing the final results
  char *benchmark_file_path = NEW_C_HEAP_ARRAY(char, JVM_MAXPATHLEN, mtNMT);
  if (!_create_file_path_with_pid(path, BENCHMARK_LOG_FILE, benchmark_file_path, pid)) {
    tty->print("Can't construct benchmark_file_path [%s].", benchmark_file_path);
    os::exit(-1);
  }
  int benchmark_log_fd = _prepare_log_file(nullptr, BENCHMARK_LOG_FILE);
  if (benchmark_log_fd == -1) {
    tty->print("Can't open [%s].", benchmark_file_path);
    os::exit(-1);
  }

  long int countFree = 0;
  long int countMalloc = 0;
  long int countRealloc = 0;
  long int nanoseconds = 0;
  long int requestedTotal = 0;
  long int actualTotal = 0;
  long int headers = 0;
  for (off_t i = 0; i < count; i++) {
    Entry *e = &records_file_entries[i];
    MemTag mem_tag = NMTUtil::index_to_tag((int)e->mem_tag);
    int frameCount;
    for (frameCount = 0; frameCount < NMT_TrackingStackDepth; frameCount++) {
      if (e->stack[frameCount] == 0) {
        break;
      }
    }
    NativeCallStack stack = NativeCallStack::empty_stack();
    if (frameCount > 0) {
      stack = NativeCallStack(e->stack, frameCount);
    }
    long int requested = 0;
    long int actual = 0;
    pointers[i] = nullptr;
    long int start = 0;
    long int end = 0;
    {
      if (IS_REALLOC(e)) {
        // the recorded "realloc" was captured in a different process,
        // so find the corresponding "malloc" or "realloc" in this process
        for (off_t j = i-1; j >= 0; j--) {
          Entry *p = &records_file_entries[j];
          if (e->old == p->ptr) {
            countRealloc++;
            address ptr = pointers[j];
            requested -= (long int)p->requested;
            actual -= (long int)p->actual;
            start = os::javaTimeNanos();
            {
              ptr = (address)os::realloc(ptr, e->requested, mem_tag, stack);
            }
            end = os::javaTimeNanos();
            requested += (long int)e->requested;
            actual += (long int)e->actual;
            pointers[i] = ptr;
            pointers[j] = nullptr;
            break;
          }
          if (mem_tag == mtNone) {
            tty->print("REALLOC?\n");
          }
        }
      } else if (IS_MALLOC(e)) {
          countMalloc++;
          address ptr = nullptr;
          start = os::javaTimeNanos();
          {
            ptr = (address)os::malloc(e->requested, mem_tag, stack);
          }
          end = os::javaTimeNanos();
          requested = (long int)e->requested;
          actual = (long int)e->actual;
          pointers[i] = ptr;
          if (mem_tag == mtNone) {
            tty->print("MALLOC?\n");
          }
      } else if (IS_FREE(e)) {
        // the recorded "free" was captured in a different process,
        // so find the corresponding "malloc" or "realloc" in this process
        for (off_t j = i-1; j >= 0; j--) {
          Entry *p = &records_file_entries[j];
          if ((e->old == p->ptr) || (e->ptr == p->ptr)) {
            countFree++;
            mem_tag = NMTUtil::index_to_tag((int)p->mem_tag);
            void* ptr = pointers[j];
            requested -= (long int)p->requested;
            actual -= (long int)p->actual;
            start = os::javaTimeNanos();
            {
              os::free(ptr);
            }
            end = os::javaTimeNanos();
            pointers[i] = nullptr;
            pointers[j] = nullptr;
            break;
          }
        }
        if (mem_tag == mtNone) {
          tty->print("FREE?\n");
        }
      } else {
        tty->print("HUH?\n");
        os::exit(-1);
      }
      requestedTotal += requested;
      actualTotal += actual;

      if (IS_FREE(e)) {
        if (mem_tag != mtNone) {
          headers--;
        }
      } else if IS_MALLOC(e) {
        headers++;
      }
    }
    long int duration = (start > 0) ? (end - start) : 0;
    nanoseconds += duration;

    // write final results into its own log file that we can later parse it using 3rd party tool
    // where we can do histograms and go into custom details
    _write_and_check(benchmark_log_fd, &duration, sizeof(duration));
    _write_and_check(benchmark_log_fd, &requested, sizeof(requested));
    _write_and_check(benchmark_log_fd, &actual, sizeof(actual));
    char type = (IS_MALLOC(e) * 1) | (IS_REALLOC(e) * 2) | (IS_FREE(e) * 4);
    _write_and_check(benchmark_log_fd, &type, sizeof(type));
    // tty->print(" %9ld:%9ld:%9ld %d:%d:%d\n", requested, actual, duration, IS_MALLOC(e), IS_REALLOC(e), IS_FREE(e));
  }

  // present the results
  setlocale(LC_NUMERIC, "");
  setlocale(LC_ALL, "");
  long int overhead_NMT = 0;
  if (MemTracker::enabled()) {
    overhead_NMT = (long int)(headers * MemTracker::overhead_per_malloc());
  }
  long int overhead_malloc = actualTotal - requestedTotal - overhead_NMT;
  double overheadPercentage_malloc = 100.0 * (double)overhead_malloc / (double)requestedTotal;
  tty->print("\n\n\nmalloc summary [recorded NMT mode \"%s\"]:\n\n", NMTUtil::tracking_level_to_string(recorded_nmt_level));
  tty->print("time:" LD_FORMAT "[ns]\n", nanoseconds);
  if (!timeOnly) {
    double overheadPercentage_NMT = 100.0 * (double)overhead_NMT / (double)requestedTotal;
    tty->print("[samples:" LD_FORMAT "] [NMT headers:" LD_FORMAT "]\n", count, headers);
    tty->print("[malloc#:" LD_FORMAT "] [realloc#:" LD_FORMAT "] [free#:" LD_FORMAT "]\n", countMalloc, countRealloc, countFree);
    tty->print("memory requested:" LD_FORMAT " bytes, allocated:" LD_FORMAT " bytes\n", requestedTotal, actualTotal);
    tty->print("malloc overhead:" LD_FORMAT " bytes [%2.2f%%], NMT headers overhead:" LD_FORMAT " bytes [%2.2f%%]\n", overhead_malloc, overheadPercentage_malloc, overhead_NMT, overheadPercentage_NMT);
    tty->print("\n");
  }

  // clean up
  _close_and_check(log_fi.fd);
  _close_and_check(records_fi.fd);
  _close_and_check(benchmark_log_fd);
  FREE_C_HEAP_ARRAY(char, benchmark_file_path);

  for (off_t i = 0; i < count; i++) {
    if (pointers[i] != nullptr) {
      os::free(pointers[i]);
      pointers[i] = nullptr;
    }
  }
#if !defined(_WIN64)
  munmap((void*)pointers, size_pointers);
#endif

  os::exit(0);
}

void NMT_MemoryLogRecorder::_record(MemTag mem_tag, size_t requested, address ptr, address old, const NativeCallStack *stack) {
  NMT_MemoryLogRecorder *recorder = NMT_MemoryLogRecorder::instance();
  if (!recorder->done()) {
    NMTRecorder_Locker locker;
    volatile long int count = recorder->_count++;
    if (count < recorder->_limit) {
      Entry entry;
      entry.time = count;
      if (MemTracker::is_initialized()) {
        entry.time = os::javaTimeNanos();
      }
      entry.thread = os::current_thread_id();
      entry.ptr = ptr;
      entry.old = old;
      // tty->print("record %p:%zu:%p\n", ptr, requested, old);fflush(stderr);
      entry.requested = requested;
      entry.actual = 0;
      if (entry.requested > 0) {
        entry.actual = raw_malloc_size(ptr);
      }

      entry.mem_tag = (long int)mem_tag;
      if ((MemTracker::is_initialized()) && (stack != nullptr)) {
        // recording stack frames will make sure that the hashtables
        // are used, so they get benchmarked as well
        for (int i = 0; i < NMT_TrackingStackDepth; i++) {
          entry.stack[i] = stack->get_frame(i);
        }
      }

      if (recorder->_log_fd != -1) {
        _write_and_check(recorder->_log_fd, &entry, sizeof(Entry));
      }

      recorder->logThreadName();
    } else {
      recorder->finish();
    }
  }
}

void NMT_MemoryLogRecorder::record_free(void *ptr) {
  NMT_MemoryLogRecorder *recorder = NMT_MemoryLogRecorder::instance();
  if (!recorder->done()) {
    address resolved_ptr = (address)ptr;
    if (MemTracker::enabled()) {
      resolved_ptr = (address)ptr - NMT_HEADER_SIZE;
    }
    NMT_MemoryLogRecorder::_record(mtNone, 0, resolved_ptr, nullptr, nullptr);
  }
}

void NMT_MemoryLogRecorder::record_alloc(MemTag mem_tag, size_t requested, void* ptr, const NativeCallStack *stack, void* old) {
  NMT_MemoryLogRecorder *recorder = NMT_MemoryLogRecorder::instance();
  if (!recorder->done()) {
    address old_resolved_ptr = (address)old;
    if (old != nullptr) {
      if (MemTracker::enabled()) {
        old_resolved_ptr = (address)old - NMT_HEADER_SIZE;
      }
    }
    NMT_MemoryLogRecorder::_record(mem_tag, requested, (address)ptr, old_resolved_ptr, stack);
  }
}

void NMT_MemoryLogRecorder::print(Entry *e) {
  if (e == nullptr) {
    tty->print("nullptr\n");
  } else {
    if (IS_FREE(e)) {
      tty->print("           FREE: ");
    } else if (IS_REALLOC(e)) {
      tty->print("        REALLOC: ");
    } else if (IS_MALLOC(e)) {
      tty->print("         MALLOC: ");
    }
    tty->print("time:%15ld, thread:%6ld, ptr:%14p, old:%14p, requested:%8ld, actual:%8ld, mem_tag:%s\n", e->time, e->thread, e->ptr, e->old, e->requested, e->actual, NMTUtil::tag_to_name(NMTUtil::index_to_tag((int)e->mem_tag)));
  }
}

void NMT_VirtualMemoryLogRecorder::initialize(long int limit) {
  // tty->print("> NMT_VirtualMemoryLogRecorder::initialize(%ld, %ld)\n", limit, sizeof(Entry));
  NMTRecorder_Locker locker;
  NMT_VirtualMemoryLogRecorder *recorder = NMT_VirtualMemoryLogRecorder::instance();
  recorder->init();
  {
    recorder->_limit = limit;
    if (recorder->_limit > 0) {
      recorder->_log_fd = _prepare_log_file(nullptr, VALLOCS_LOG_FILE);
      recorder->_done = false;
    } else {
      recorder->_done = true;
    }
  }
}

void NMT_VirtualMemoryLogRecorder::finish(void) {
  NMTRecorder_Locker locker;
  NMT_VirtualMemoryLogRecorder *recorder = NMT_VirtualMemoryLogRecorder::instance();
  if (!recorder->done()) {
      volatile int log_fd = recorder->_log_fd;
      recorder->_log_fd = -1;
      // tty->print(" log_fd:%d\n", log_fd);
      log_fd = _close_and_check(log_fd);
  }

  int info_fd = _prepare_log_file(nullptr, INFO_LOG_FILE);
  if (info_fd != -1) {
    size_t mode = NMTUtil::parse_tracking_level(NativeMemoryTracking);
    _write_and_check(info_fd, &mode, sizeof(mode));
    size_t overhead = MemTracker::overhead_per_malloc();
    _write_and_check(info_fd, &overhead, sizeof(overhead));
    info_fd = _close_and_check(info_fd);
  }

  recorder->_done = true;
}

void NMT_VirtualMemoryLogRecorder::replay(const int pid) {
  // tty->print("NMT_VirtualMemoryLogRecorder::replay(%d)\n", pid);
  static const char *path = ".";

  // open records file for reading the virtual memory allocations to "play back"
  file_info records_fi = _open_file_and_read(VALLOCS_LOG_FILE, path, pid);
  if (records_fi.fd == -1) {
    return;
  }
  Entry* records_file_entries = (Entry*)records_fi.ptr;
  long int count = (long int)(records_fi.size / sizeof(Entry));

  long int total = 0;
  for (off_t i = 0; i < count; i++) {
    Entry *e = &records_file_entries[i];

    MemTag mem_tag = NMTUtil::index_to_tag((int)e->mem_tag);
    int frameCount;
    for (frameCount = 0; frameCount < NMT_TrackingStackDepth; frameCount++) {
      if (e->stack[frameCount] == 0) {
        break;
      }
    }
    NativeCallStack stack = NativeCallStack::empty_stack();
    if (frameCount > 0) {
      stack = NativeCallStack(e->stack, frameCount);
    }

    //VirtualMemoryTracker::initialize(NMTUtil::parse_tracking_level(NativeMemoryTracking));
    long int start = os::javaTimeNanos();
    {
      switch (e->operation) {
        case NMT_VirtualMemoryLogRecorder::MemoryOperation::RESERVE:
          // tty->print("[record_virtual_memory_reserve(%p, %ld, %p, %hhu)\n", e->ptr, e->size, &stack, mem_tag);fflush(stderr);
          MemTracker::record_virtual_memory_reserve(e->ptr, e->size, stack, mem_tag);
          // tty->print("]\n");fflush(stderr);
          break;
        case NMT_VirtualMemoryLogRecorder::MemoryOperation::RELEASE:
          // tty->print("[record_virtual_memory_release(%p, %ld)\n", e->ptr, e->size);fflush(stderr);
          MemTracker::record_virtual_memory_release(e->ptr, e->size);
          // tty->print("]\n");fflush(stderr);
          break;
        case NMT_VirtualMemoryLogRecorder::MemoryOperation::UNCOMMIT:
          // tty->print("<record_virtual_memory_uncommit(%p, %ld)\n", e->ptr, e->size);fflush(stderr);
          MemTracker::record_virtual_memory_uncommit(e->ptr, e->size);
          // tty->print(">\n");fflush(stderr);
          break;
        case NMT_VirtualMemoryLogRecorder::MemoryOperation::RESERVE_AND_COMMIT:
          // tty->print("[MemTracker::record_virtual_memory_reserve_and_commit\n");
          MemTracker::record_virtual_memory_reserve_and_commit(e->ptr, e->size, stack, mem_tag);
          // tty->print("]\n");fflush(stderr);
          break;
        case NMT_VirtualMemoryLogRecorder::MemoryOperation::COMMIT:
          // tty->print("[record_virtual_memory_commit(%p, %ld, %p)\n", e->ptr, e->size, &stack);fflush(stderr);
          MemTracker::record_virtual_memory_commit(e->ptr, e->size, stack);
          // tty->print("]\n");fflush(stderr);
          break;
        case NMT_VirtualMemoryLogRecorder::MemoryOperation::SPLIT_RESERVED:
          // tty->print("[MemTracker::record_virtual_memory_split_reserved\n");
          MemTracker::record_virtual_memory_split_reserved(e->ptr, e->size, e->size_split, mem_tag, NMTUtil::index_to_tag((int)e->mem_tag_split));
          // tty->print("]\n");fflush(stderr);
          break;
        case NMT_VirtualMemoryLogRecorder::MemoryOperation::TAG:
          // tty->print("[record_virtual_memory_type(%p, %ld, %p)\n", e->ptr, e->size, &stack);fflush(stderr);
          MemTracker::record_virtual_memory_tag(e->ptr, e->size, mem_tag);
          // tty->print("]\n");fflush(stderr);
          break;
        default:
          tty->print("HUH?\n");
          os::exit(-1);
          break;
      }
    }
    long int end = os::javaTimeNanos();
    long int duration = (start > 0) ? (end - start) : 0;
    total += duration;
  }
  tty->print("\n\n\nVirtualMemoryTracker summary:\n\n\n");
  tty->print("time:" LD_FORMAT "[ns] [samples:" LD_FORMAT "]\n", total, count);


  _close_and_check(records_fi.fd);

  os::exit(0);
}

void NMT_VirtualMemoryLogRecorder::_record(NMT_VirtualMemoryLogRecorder::MemoryOperation operation, MemTag mem_tag, MemTag mem_tag_split, size_t size, size_t size_split, address ptr, const NativeCallStack *stack) {
//  tty->print("NMT_VirtualMemoryLogRecorder::record (%d, %hhu, %hhu, %ld, %ld, %p, %p)\n",
//          operation, mem_tag, mem_tag_split, size, size_split, ptr, stack);fflush(stderr);
  NMT_VirtualMemoryLogRecorder *recorder = NMT_VirtualMemoryLogRecorder::instance();
  if (!recorder->done()) {
    NMTRecorder_Locker locker;
    volatile long int count = recorder->_count++;
    if (count < recorder->_limit) {
      Entry entry;
      entry.operation = operation;
      entry.time = count;
      if (MemTracker::is_initialized())
      {
        entry.time = os::javaTimeNanos();
      }
      entry.thread = os::current_thread_id();
      entry.ptr = ptr;
      entry.mem_tag = (long int)mem_tag;
      entry.mem_tag_split = (long int)mem_tag_split;
      entry.size = size;
      entry.size_split = size_split;
      if ((MemTracker::is_initialized()) && (stack != nullptr)) {
        // the only use of frames is for benchmarking -
        // the NMT code uses a hashtable to store these values,
        // so preserving these will make sure that the hashtables
        // are used when ran with this data
        for (int i = 0; i < NMT_TrackingStackDepth; i++) {
          entry.stack[i] = stack->get_frame(i);
        }
      }
      // tty->print("recorder->_log_fd: %d\n", recorder->_log_fd);
      if (recorder->_log_fd != -1) {
        _write_and_check(recorder->_log_fd, &entry, sizeof(Entry));
      }
    } else {
      recorder->finish();
    }
  }
}

void NMT_VirtualMemoryLogRecorder::record_virtual_memory_reserve(void* addr, size_t size, const NativeCallStack& stack, MemTag mem_tag) {
  NMT_VirtualMemoryLogRecorder::_record(MemoryOperation::RESERVE, mem_tag, mtNone, size, 0, (address)addr, &stack);
}

void NMT_VirtualMemoryLogRecorder::record_virtual_memory_release(address addr, size_t size) {
  NMT_VirtualMemoryLogRecorder::_record(MemoryOperation::RELEASE, mtNone, mtNone, size, 0, (address)addr, nullptr);
}

void NMT_VirtualMemoryLogRecorder::record_virtual_memory_uncommit(address addr, size_t size) {
  NMT_VirtualMemoryLogRecorder::_record(MemoryOperation::UNCOMMIT, mtNone, mtNone, size, 0, (address)addr, nullptr);
}

void NMT_VirtualMemoryLogRecorder::record_virtual_memory_reserve_and_commit(void* addr, size_t size, const NativeCallStack& stack, MemTag mem_tag) {
  NMT_VirtualMemoryLogRecorder::_record(MemoryOperation::RESERVE_AND_COMMIT, mem_tag, mtNone, size, 0, (address)addr, &stack);
}

void NMT_VirtualMemoryLogRecorder::record_virtual_memory_commit(void* addr, size_t size, const NativeCallStack& stack) {
  NMT_VirtualMemoryLogRecorder::_record(MemoryOperation::COMMIT, mtNone, mtNone, size, 0, (address)addr, &stack);
}

void NMT_VirtualMemoryLogRecorder::record_virtual_memory_split_reserved(void* addr, size_t size, size_t split, MemTag mem_tag, MemTag split_mem_tag) {
  NMT_VirtualMemoryLogRecorder::_record(MemoryOperation::SPLIT_RESERVED, mem_tag, split_mem_tag, size, split, (address)addr, nullptr);
}

void NMT_VirtualMemoryLogRecorder::record_virtual_memory_tag(void* addr, size_t size, MemTag mem_tag) {
  NMT_VirtualMemoryLogRecorder::_record(MemoryOperation::TAG, mem_tag, mtNone, size, 0, (address)addr, nullptr);
}

#else

// TODO: Windows impl

#endif // if defined(LINUX) || defined(__APPLE__)
