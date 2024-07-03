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
#include "nmt/nmtCommon.hpp"
#include "nmt/mallocHeader.hpp"
#include "nmt/mallocHeader.inline.hpp"
#include "nmt/memoryLogRecorder.hpp"
#include "nmt/memTracker.hpp"
#include "runtime/arguments.hpp"
#include "runtime/globals.hpp"
#include "runtime/os.hpp"
#include "runtime/thread.hpp"
#include "utilities/debug.hpp"
#include "utilities/nativeCallStack.hpp"
#include "utilities/vmError.hpp"

#if defined(LINUX)
#include <malloc.h>
#elif defined(__APPLE__)
#include <malloc/malloc.h>
#endif

#if defined(LINUX) || defined(__APPLE__)
#include <pthread.h>
#include <string.h>
#include <sys/mman.h>
#endif

/*

 This code collects malloc/realloc/free os requests (-XX:NMTRecordMemoryAllocations=XXX)

 #1 Records all allocation requests that can be "played back" later to allow measuring
      the performance speed utilizing the exact same memory access pattern as the captured
      ones from the use case.
      This can be used to compare NMT off vs NMT summary vs NMT detail speed performance.

 Notes:

 Imagine that we issue os::malloc(20) call. We will get back not just the 20 bytes that we asked for,
      but instead a bigger chunk, depending on the particular OS (and its malloc implementation).
      For example, os::malloc(20) call on:

 - Linux allocates   24 bytes   BBBBBBBB BBBBBBBB BBBBDDDD
 - macOS allocates   32 bytes   BBBBBBBB BBBBBBBB BBBBDDDD DDDDDDDD
 - Windows allocates ?? bytes

 where:

 - B client chunk
 - D malloc rounding

 In this case the malloc overhead is:

 - Linux malloc overhead is   ((24 - 20) / 20) == 20.0 % increase
 - macOS malloc overhead is   ((32 - 20) / 20) == 60.0 % increase
 - Windows malloc overhead is                        ? % increase


 Now imagine that we issue os::malloc(20) call with NMT ON (either summary or detail mode). Since NMT
      needs a header and a footer, this will add additional (16 + 2 == 18) bytes, so we end up asking
      for (20 + 18 == 38) bytes, and after malloc rounding it up, we will get back:

 - Linux allocates   40 bytes   AAAAAAAA AAAAAAAA BBBBBBBB BBBBBBBB BBBBCCDD
 - macOS allocates   48 bytes   AAAAAAAA AAAAAAAA BBBBBBBB BBBBBBBB BBBBCCDD DDDDDDDD
 - Windows allocates ?? bytes

 where:

 - A NMT header
 - B client chunk
 - C NMT footer
 - D malloc rounding

 In this case the malloc overhead is:

 - Linux malloc overhead is   ((40 - 38) / 38) ==  5.3 % increase
 - macOS malloc overhead is   ((48 - 38) / 38) == 26.3 % increase
 - Windows malloc overhead is                        ? % increase


 When calculating the NMT overhead, this code will compare the allocated sizes, i.e. the actual
      acquired sizes, not requested sizes. In this case we would compare:

 - Linux NMT overhead is   ((40 - 24) / 24) == 66.7 % increase
 - macOS NMT overhead is   ((48 - 32) / 32) == 50.0 % increase
 - Windows NMT overhead is                        ? % increase

When estimating the NMT impact, from memory overhead point of view, we have a choice of either
      comparing 2 different runs, i.e. NMT off vs NMT on, or we can do a single run with NMT on
      and estimate the memory consumption without NMT, by substracting the NMT header (we can estimate
      what the malloc would return by using _malloc_good_size()) and by substracting malloc's
      flagged as NMT objects. The advantage is that the memory allocations can vary from
      run to run, so normally we would be required to run each case (NMT off, NMT on) multiple
      times, and we would have to compare the averages of those, which we can avoid here
      and do all in a single run. The (small) disadvantage is that malloc on certain
      platforms is free to return varying sizes even for the same requested size,
      but we can statistically estimate what the average allocated size is for given requested size,
      which is what we do here (see _malloc_good_size_stats())

 To estimate NMT memory overhead, just a single run with NMT on (either summary or detail)
      is needed, and the calculated NMT overhead will be compared to an estimated usage with NMT off
      in that same run. Of course we will get a better estimate with an average accross multiple runs,
      each one with NMT on.

 
  ./build/xcode/build/jdk/bin/java -XX:+UnlockDiagnosticVMOptions \
     -XX:NMTBenchmarkRecordedDir=/Volumes/Work/bugs/8317453/recordings/J2Ddemo/ \
     -XX:NMTBenchmarkRecordedPID=44103
  
*/

#define REALLOC_MARKER       ((void *)1)
#define IS_FREE(e)           ((e->requested == 0) && (e->old == nullptr))
#define IS_REALLOC(e)        ((e->requested  > 0) && (e->old != nullptr))
#define IS_MALLOC_REALLOC(e) ((e->requested  > 0) && (e->old == REALLOC_MARKER))
#define IS_MALOC(e)          ((e->requested  > 0) && (e->old == nullptr))

#define ALLOCS_LOG_FILE "hs_nmt_pid%p_allocs_record.log"
#define THREADS_LOG_FILE "hs_nmt_pid%p_threads_record.log"
#define INFO_LOG_FILE "hs_nmt_pid%p_info_record.log"
#define BENCHMARK_LOG_FILE "hs_nmt_pid%p_benchmark.log"

volatile static bool _initiliazed = false;
volatile static bool _done = false;
volatile static intx _limit = 0;
static int _log_fd = -1;

#if defined(LINUX)
  static pthread_mutex_t _mutex = PTHREAD_RECURSIVE_MUTEX_INITIALIZER_NP;
#elif defined(__APPLE__)
  static pthread_mutex_t _mutex = PTHREAD_RECURSIVE_MUTEX_INITIALIZER;
#endif // LINUX || __APPLE__

constexpr size_t _threads_name_length = 32;
static volatile size_t _threads_names_capacity = 128;
static volatile size_t _threads_names_counter = 0;
typedef struct thread_name_info {
  char name[_threads_name_length];
  intx thread;
} thread_name_info;
static thread_name_info *_threads_names = nullptr;

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
    fprintf(stderr, "write_and_check(%d) ERROR\n", fd);
    //assert(false, "fd: %d", fd);
  }
  errno = 0;
  ssize_t written = ::write(fd, buf, count);
  if ((long)written != (long)count) {
    int e = errno;
    fprintf(stderr, "write_and_check(%d) ERROR:[%s]\n", fd, os::strerror(e));
    //assert((long)written != (long)count, "written != count [%ld,%ld]", (long)written, (long)count);
  }
}

static int _close_and_check(int fd) {
  if (!IS_VALID_FD(fd)) {
    fprintf(stderr, "close_and_check(%d) ERROR\n", fd);
    return fd;
  }
  if (fd > STDERR_FILENO) {
    errno = 0;
    int status = close(fd);
    if (status != 0) {
      int e = errno;
      fprintf(stderr, "ERROR:[%s]\n", os::strerror(e));
      assert(status != 0, "close(%d) returned %d", fd, status);
      return fd;
    } else {
      return -1;
    }
  } else {
    return fd;
  }
}

void NMT_MemoryLogRecorder::log_free(MEMFLAGS flags, void *ptr)
{
  if (_done) {
    return;
  }
  NMT_MemoryLogRecorder::log(flags, 0, (address)ptr, nullptr, nullptr);
}

void NMT_MemoryLogRecorder::log_malloc(MEMFLAGS flags, size_t requested, void* ptr, const NativeCallStack *stack)
{
  if (_done) {
    return;
  }
  NMT_MemoryLogRecorder::log(flags, requested, (address)ptr, nullptr, stack);
}

void NMT_MemoryLogRecorder::log_realloc(MEMFLAGS flags, size_t requested, void* ptr, void* old, const NativeCallStack *stack)
{
  if (_done) {
    return;
  }
  if (old == nullptr)
  {
    // mark the realloc's old pointer, so that we can tell realloc(NULL) and malloc() apart
    old = REALLOC_MARKER;
  }
  NMT_MemoryLogRecorder::log(flags, requested, (address)ptr, (address)old, stack);
}

void NMT_MemoryLogRecorder::log(MEMFLAGS flags, size_t requested, address ptr, address old, const NativeCallStack *stack) {
  //fprintf(stderr, "NMT_MemoryLogRecorder::log(%16s, %6ld, %12p, %12p)\n", NMTUtil::flag_to_name(flags), requested, ptr, old);
  volatile static jlong _count = 0;
  jlong count = -1;

  if (_initiliazed && !_done) {
#if defined(LINUX) || defined(__APPLE__)
    pthread_mutex_lock(&_mutex);
#elif defined(WINDOWS)
    // ???
#endif
    if (_initiliazed && !_done) {
      count = _count++;
      if (count >= _limit) {
        NMT_MemoryLogRecorder::finish();
      }
    }
#if defined(LINUX) || defined(__APPLE__)
    pthread_mutex_unlock(&_mutex);
#elif defined(WINDOWS)
    // ???
#endif
  }

  //fprintf(stderr, " _initiliazed:%d\n", _initiliazed);
  //fprintf(stderr, " _done:%d\n", _done);
  //fprintf(stderr, " count:%ld\n", count);

  if (!_done && (count != -1)) {
    Entry entry;
    entry.time = count;
    if (MemTracker::is_initialized())
    {
      entry.time = os::javaTimeNanos();
    }
    entry.thread = os::current_thread_id();
    entry.ptr = ptr;
    entry.old = old;
    entry.requested = requested;
    entry.actual = 0;
    if (entry.requested > 0)
    {
#if defined(LINUX)
      entry.actual = malloc_usable_size(ptr);
#elif defined(WINDOWS)
      entry.actual = _msize(ptr);
#elif defined(__APPLE__)
      entry.actual = malloc_size(ptr);
      assert(entry.requested <= entry.actual, "entry.requested <= entry.actual [%zu,%zu]", entry.requested, entry.actual);
#endif
    }

    entry.flags = (jlong)flags;
    if ((MemTracker::is_initialized()) && (stack != nullptr)) {
      // the only use of frames is for benchmarking -
      // the NMT code uses a hashtable to store these values,
      // so preserving these will make sure that the hashtables
      // are used when ran with this data
      for (int i = 0; i < NMT_TrackingStackDepth; i++) {
        entry.stack[i] = stack->get_frame(i);
      }
    }

    if (_log_fd != -1) {
      _write_and_check(_log_fd, &entry, sizeof(Entry));
    }

    _count++;
  }
}

void NMT_MemoryLogRecorder::rememberThreadName(const char* name) {
 #if defined(LINUX) || defined(__APPLE__)
  pthread_mutex_lock(&_mutex);
#elif defined(WINDOWS)
  // ???
#endif
  if (_initiliazed && !_done) {
    if (_threads_names == nullptr) {
      ALLOW_C_FUNCTION(::calloc, _threads_names = (thread_name_info*)::calloc(_threads_names_capacity, sizeof(thread_name_info));)
    }
    if (_threads_names_counter < _threads_names_capacity) {
      size_t counter = _threads_names_counter++;
      if (counter < _threads_names_capacity) {
        strncpy((char*)_threads_names[counter].name, name, _threads_name_length-1);
        _threads_names[counter].thread = os::current_thread_id();
      }
    } else {
      _threads_names_capacity *= 2;
      ALLOW_C_FUNCTION(::realloc, _threads_names = (thread_name_info*)::realloc((void*)_threads_names, _threads_names_capacity*sizeof(thread_name_info));)
      rememberThreadName(name);
    }
  }
#if defined(LINUX) || defined(__APPLE__)
  pthread_mutex_unlock(&_mutex);
#elif defined(WINDOWS)
  // ???
#endif
}

void NMT_MemoryLogRecorder::printActualSizesFor(const char* list) {
//fprintf(stderr, "NMT_MemoryLogRecorder::printActualSizesFor(%s)\n", list);
  char* string = os::strdup(NMTPrintMemoryAllocationsSizesFor, mtNMT);
  if (string != nullptr) {
    ALLOW_C_FUNCTION(::strtok, char* token = strtok(string, ",");)
    while (token) {
      ALLOW_C_FUNCTION(::strtol, long requested = strtol(token, nullptr, 10);)
      long actual = 0;
      ALLOW_C_FUNCTION(::malloc, void *ptr = ::malloc(requested);)
      if (ptr != nullptr) {
#if defined(LINUX)
        ALLOW_C_FUNCTION(::malloc_usable_size, actual = ::malloc_usable_size(ptr);)
#elif defined(WINDOWS)
        ALLOW_C_FUNCTION(::_msize, actual = ::_msize(ptr);)
#elif defined(__APPLE__)
        ALLOW_C_FUNCTION(::malloc_size, actual = ::malloc_size(ptr);)
#endif
        ALLOW_C_FUNCTION(::free, ::free(ptr);)
      }
      printf("%ld", actual);
      ALLOW_C_FUNCTION(::strtok, token = strtok(NULL, ",");)
      if (token) {
        printf(",");
      }
    }
    os::exit(0);
  }
}

static bool _create_file_path_with_pid(const char *path, const char *file, char* file_path, int pid) {
  char *tmp_path = NEW_C_HEAP_ARRAY(char, JVM_MAXPATHLEN, mtNMT);
  strcpy(tmp_path, path);
  strcat(tmp_path, os::file_separator());
  strcat(tmp_path, file);
  if(!Arguments::copy_expand_pid(tmp_path, strlen(tmp_path), file_path, JVM_MAXPATHLEN, pid)) {
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

  info.ptr = ::mmap(NULL, info.size, PROT_READ, MAP_PRIVATE, info.fd, 0);
  assert(info.ptr != MAP_FAILED);

  FREE_C_HEAP_ARRAY(char, file_path);

  return info;
}

void NMT_MemoryLogRecorder::replay(const char* path, const int pid) {
  if ((path != nullptr) && (strlen(path) > 0)) {
#if defined(LINUX) || defined(__APPLE__)
    pthread_mutex_lock(&_mutex);
#elif defined(WINDOWS)
    // ???
#endif

    // compare the recorded and current levels of NMT and exit if different
    file_info log_fi = _open_file_and_read(INFO_LOG_FILE, path, pid);
    size_t* status_file_bytes = (size_t*)log_fi.ptr;
    NMT_TrackingLevel recorded_nmt_level = (NMT_TrackingLevel)status_file_bytes[0];
    if (NMTUtil::parse_tracking_level(NativeMemoryTracking) != recorded_nmt_level) {
      tty->print("NativeMemoryTracking mismatch [%u != %u].\n", recorded_nmt_level, NMTUtil::parse_tracking_level(NativeMemoryTracking));
      tty->print("Re-run with \"-XX:NativeMemoryTracking=%s\"\n", NMTUtil::tracking_level_to_string(recorded_nmt_level));
      os::exit(-1);
    }

    // open records file for reading the memory allocations to "play back"
    file_info records_fi = _open_file_and_read(ALLOCS_LOG_FILE, path, pid);
    Entry* records_file_entries = (Entry*)records_fi.ptr;
    long int count = (records_fi.size / sizeof(Entry));
    size_t size_pointers = count * sizeof(address);
    address *pointers = (address*)::mmap(NULL, size_pointers, PROT_READ|PROT_WRITE, MAP_PRIVATE|MAP_NORESERVE|MAP_ANONYMOUS, -1, 0);
    assert(pointers != MAP_FAILED);

    // open benchmark file for writing the final results
    char *benchmark_file_path = NEW_C_HEAP_ARRAY(char, JVM_MAXPATHLEN, mtNMT);
    if (!_create_file_path_with_pid(path, BENCHMARK_LOG_FILE, benchmark_file_path, pid)) {
      tty->print("Can't construct benchmark_file_path [%s].", benchmark_file_path);
      os::exit(-1);
    }
    int benchmark_fd = _prepare_log_file(benchmark_file_path, nullptr);
    if (benchmark_fd == -1) {
      tty->print("Can't open [%s].", benchmark_file_path);
      os::exit(-1);
    }

    jlong total = 0;
    jlong max_time = 0;
    for (off_t i = 0; i < count; i++) {
      Entry *e = &records_file_entries[i];
      MEMFLAGS flags = NMTUtil::index_to_flag((int)e->flags);
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
      size_t requested = 0;
      size_t actual = 0;

      pointers[i] = nullptr;
      jlong start = 0;
      jlong end = 0;
      {
        requested = e->requested;
        if (IS_MALOC(e)) {
          start = os::javaTimeNanos();
          {
            pointers[i] = (address)os::malloc(e->requested, flags, stack);
          }
          end = os::javaTimeNanos();
        } else if (IS_MALLOC_REALLOC(e)) {
          start = os::javaTimeNanos();
          {
            // the recorded "realloc" that was captured in a different process
            // is trivial one (i.e. realloc(nullptr)) which looks like "malloc",
            // but continue to treat it as "realloc"
            pointers[i] = (address)os::realloc(nullptr, e->requested, flags, stack);
          }
          end = os::javaTimeNanos();
        } else if (IS_REALLOC(e)) {
          // the recorded "realloc" was captured in a different process,
          // so find the corresponding "malloc" or "realloc" in this process
          for (off_t j = i; j >= 0; j--) {
            Entry *p = &records_file_entries[j];
            if (e->old == p->ptr) {
              start = os::javaTimeNanos();
              {
                pointers[i] = (address)os::realloc(pointers[j], e->requested, flags, stack);
              }
              end = os::javaTimeNanos();
              pointers[j] = nullptr;
              break;
            }
          }
        } else if (IS_FREE(e)) {
          // the recorded "free" was captured in a different process,
          // so find the corresponding "malloc" or "realloc" in this process
          for (off_t j = i; j >= 0; j--) {
            Entry *p = &records_file_entries[j];
            if (e->ptr == p->ptr) {
              start = os::javaTimeNanos();
              {
                os::free(pointers[j]);
              }
              end = os::javaTimeNanos();
              pointers[i] = nullptr;
              pointers[j] = nullptr;
              break;
            }
          }
        } else {
          fprintf(stderr, "HUH?\n");
          os::exit(-1);
        }

        if (!IS_FREE(e)) {
          void* outer_ptr = pointers[i];
          if ((outer_ptr != nullptr) && (MemTracker::enabled())) {
            outer_ptr = MallocHeader::resolve_checked(outer_ptr);
          }
#if defined(LINUX)
          ALLOW_C_FUNCTION(::malloc_usable_size, actual = ::malloc_usable_size(outer_ptr);)
#elif defined(WINDOWS)
          ALLOW_C_FUNCTION(::_msize, actual = ::_msize(outer_ptr);)
#elif defined(__APPLE__)
          ALLOW_C_FUNCTION(::malloc_size, actual = ::malloc_size(outer_ptr);)
#endif
        }
      }
      jlong duration = (start > 0) ? (end - start) : 0;
      max_time = MAX(max_time, duration);
      total += duration;

      _write_and_check(benchmark_fd, &duration, sizeof(duration));
      _write_and_check(benchmark_fd, &requested, sizeof(requested));
      _write_and_check(benchmark_fd, &actual, sizeof(actual));
      char type = (IS_MALOC(e) * 1) | (IS_REALLOC(e) * 2) | (IS_FREE(e) * 4);
      _write_and_check(benchmark_fd, &type, sizeof(type));
      //fprintf(stderr, " %9ld:%9ld:%9ld %d:%d:%d\n", requested, actual, duration, IS_MALOC(e), IS_REALLOC(e), IS_FREE(e));
    }
    fprintf(stderr, "count:%ld total:%ld max:%ld [%s]\n", count, total, max_time, benchmark_file_path);
    
    _close_and_check(log_fi.fd);
    _close_and_check(records_fi.fd);
    _close_and_check(benchmark_fd);
    FREE_C_HEAP_ARRAY(char, benchmark_file_path);

    for (off_t i = 0; i < count; i++) {
      if (pointers[i] != nullptr) {
        os::free(pointers[i]);
        pointers[i] = nullptr;
      }
    }
    munmap((void*)pointers, size_pointers);

#if defined(LINUX) || defined(__APPLE__)
    pthread_mutex_unlock(&_mutex);
#elif defined(WINDOWS)
    // ???
#endif

    os::exit(0);
  }
}

void NMT_MemoryLogRecorder::initialize(intx limit) {
  //fprintf(stderr, "NMT_MemoryLogRecorder::initialize()\n");
  if ((NMTPrintMemoryAllocationsSizesFor != nullptr) && (strlen(NMTPrintMemoryAllocationsSizesFor) > 0)) {
    NMT_MemoryLogRecorder::printActualSizesFor((const char*)NMTPrintMemoryAllocationsSizesFor);
    os::exit(0);
  }

  if (!_initiliazed) {
#if defined(LINUX) || defined(__APPLE__)
    pthread_mutex_lock(&_mutex);
#elif defined(WINDOWS)
    // ???
#endif
    if (!_initiliazed) {
      _initiliazed = true;
      _limit = limit;
      if (_limit > 0) {
        _log_fd = _prepare_log_file(nullptr, ALLOCS_LOG_FILE);
        //fprintf(stderr, " _log_fd:%d\n", _log_fd);
      } else {
        _done = true;
      }
    }
#if defined(LINUX) || defined(__APPLE__)
    pthread_mutex_unlock(&_mutex);
#elif defined(WINDOWS)
    // ???
#endif
  }
}

void NMT_MemoryLogRecorder::finish(void) {
  //fprintf(stderr, "NMT_MemoryLogRecorder::finish()\n");
  if (_initiliazed && !_done) {
#if defined(LINUX) || defined(__APPLE__)
    pthread_mutex_lock(&_mutex);
#elif defined(WINDOWS)
      // ???
#endif
    if (_initiliazed && !_done) {
      volatile int log_fd = _log_fd;
      _log_fd = -1;
      //fprintf(stderr, " log_fd:%d\n", log_fd);
      log_fd = _close_and_check(log_fd);

      int threads_fd = _prepare_log_file(nullptr, THREADS_LOG_FILE);
      //fprintf(stderr, " threads_fd:%d\n", threads_fd);
      if (threads_fd != -1) {
        _write_and_check(threads_fd, _threads_names, _threads_names_counter*sizeof(thread_name_info));
        threads_fd = _close_and_check(threads_fd);
      }
      
      int info_fd = _prepare_log_file(nullptr, INFO_LOG_FILE);
      //fprintf(stderr, " info_fd:%d\n", info_fd);
      if (info_fd != -1) {
        size_t level = NMTUtil::parse_tracking_level(NativeMemoryTracking);
        _write_and_check(info_fd, &level, sizeof(level));
        size_t overhead = MemTracker::overhead_per_malloc();
        _write_and_check(info_fd, &overhead, sizeof(overhead));
        info_fd = _close_and_check(info_fd);
      }
    }
    _done = true;
#if defined(LINUX) || defined(__APPLE__)
    pthread_mutex_unlock(&_mutex);
#elif defined(WINDOWS)
    // ???
#endif
  }
}
