/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

#include "logging/logFileStreamOutput.hpp"
#include "utilities/globalDefinitions.hpp"
#include "runtime/os.inline.hpp"
#include "runtime/mutex.hpp"
#include "runtime/semaphore.hpp"
#include "utilities/resourceHash.hpp"
#include <stddef.h>
#include <string.h>
#include <algorithm>
#include <sys/mman.h>
#include <unistd.h>

#ifndef SHARE_LOGGING_CIRCULARSTRINGBUFFER_HPP
#define SHARE_LOGGING_CIRCULARSTRINGBUFFER_HPP

#ifdef LINUX
struct CircularMapping {
  FILE* file;
  char* buffer;
  size_t size;

  CircularMapping()
  : file(nullptr), buffer(nullptr), size(0) {
  };

  CircularMapping(size_t size)
  : size(size) {
    assert(is_aligned(size, os::vm_page_size()), "must be");
    file = tmpfile();
    if (file == nullptr) {
      vm_exit_out_of_memory(size, OOM_MMAP_ERROR, "Failed to allocate async logging buffer");
    }
    const int fd = fileno(file);
    if (fd == -1) {
      vm_exit_out_of_memory(size, OOM_MMAP_ERROR, "Failed to allocate async logging buffer");
    }
    int ret = ftruncate(fd, size);
    if (ret != 0) {
      vm_exit_out_of_memory(size, OOM_MMAP_ERROR, "Failed to allocate async logging buffer");
    }

    buffer = (char*)mmap(nullptr, size * 2, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    if (buffer == MAP_FAILED) {
      vm_exit_out_of_memory(size, OOM_MMAP_ERROR, "Failed to allocate async logging buffer");
    }
    void* mmap_ret = mmap(buffer, size, PROT_READ | PROT_WRITE, MAP_SHARED | MAP_FIXED, fd, 0);
    if (mmap_ret == MAP_FAILED) {
      vm_exit_out_of_memory(size, OOM_MMAP_ERROR, "Failed to allocate async logging buffer");
    }
    mmap_ret = mmap(buffer + size, size, PROT_READ | PROT_WRITE, MAP_SHARED | MAP_FIXED, fd, 0);
    if (mmap_ret == MAP_FAILED) {
      vm_exit_out_of_memory(size, OOM_MMAP_ERROR, "Failed to allocate async logging buffer");
    }
  }
  ~CircularMapping() {
    munmap(buffer, size * 2);
    fclose(file);
  }

  void write_bytes(size_t at, const char* bytes, size_t size) {
    memcpy(&buffer[at], bytes, size);
  }

  void read_bytes(size_t at, char* out, size_t size) {
    memcpy(out, &buffer[at], size);
  }

  char* start() {
    return buffer;
  }
};
#else
struct CircularMapping {
  char* buffer;
  size_t size;
  CircularMapping()
  : buffer(nullptr), size(0) {
  };
  CircularMapping(size_t size) {
    buffer = os::reserve_memory(size, false, mtLogging);
    if (buffer == nullptr) {
      vm_exit_out_of_memory(size, OOM_MMAP_ERROR, "Failed to allocate async logging buffer");
    }
    bool ret = os::commit_memory(buffer, size, false);
    if (!ret) {
      vm_exit_out_of_memory(size, OOM_MMAP_ERROR, "Failed to allocate async logging buffer");
    }
  }
  void write_bytes(size_t at, const char* bytes, size_t size) {
    const size_t part1_size = MIN2(size, this->size - at);
    const size_t part2_size = this->size - size;

    ::memcpy(&buffer[at], bytes, part1_size);
    ::memcpy(buffer, &bytes[part1_size], part2_size);
  }
  void read_bytes(size_t at, char* out, size_t size) {
    const size_t part1_size = MIN2(size, this->size - at);
    const size_t part2_size = this->size - size;

    ::memcpy(out, &buffer[at], part1_size);
    ::memcpy(&out[part1_size], buffer, part2_size);
  }

  ~CircularMapping() {
    os::release_memory(buffer, size);
  }
}
#endif

class CircularStringBuffer {
  friend class AsyncLogTest;
public:
    // account for dropped messages
  using StatisticsMap = ResourceHashtable<LogFileStreamOutput*, uint32_t, 17, /*table_size*/
                                        AnyObj::C_HEAP, mtLogging>;
protected:
  static const LogDecorations& None;

  // Need to perform accounting of statistics under a separate lock.
  StatisticsMap& _stats;
  PlatformMonitor& _stats_lock;

  // Can't use a Monitor here as we need a low-level API that can be used without Thread::current().
  PlatformMonitor _read_lock;
  PlatformMonitor _write_lock;
  Semaphore _flush_sem;

  struct ReadLocker : public StackObj {
    CircularStringBuffer* buf;
    ReadLocker(CircularStringBuffer* buf) : buf(buf) {
      buf->_read_lock.lock();
    }
    ~ReadLocker() {
      buf->_read_lock.unlock();
    }
  };
  struct WriteLocker : public StackObj {
    CircularStringBuffer* buf;
    WriteLocker(CircularStringBuffer* buf) : buf(buf) {
      buf->_write_lock.lock();
    }
    ~WriteLocker() {
      buf->_write_lock.unlock();
    }
  };
  // Opaque circular mapping of our buffer.
  CircularMapping circular_mapping;

  // Shared memory:
  // Reader reads tail, writes to head.
  // Writer reads head, writes to tail.
  volatile size_t tail; // Where new writes happen
  volatile size_t head; // Where new reads happen

  size_t used_locked();
  size_t unused_locked();
  size_t calc_mem(size_t sz);

public:
  struct Message {
    size_t size; // Size of string following the Message envelope
    LogFileStreamOutput* const output;
    const LogDecorations decorations;
    Message(size_t size, LogFileStreamOutput* output, const LogDecorations decorations)
    : size(size), output(output), decorations(decorations) {

    }

    Message()
    : size(0), output(nullptr), decorations(None) {
    }

    bool is_token() {
      return output == nullptr;
    }
  };
protected:

  void enqueue_locked(const char* msg, size_t size, LogFileStreamOutput* output, const LogDecorations decorations);
  void dequeue_locked(Message* out_descriptor, char* out, size_t out_size);
public:
  NONCOPYABLE(CircularStringBuffer);
  CircularStringBuffer(StatisticsMap& stats, PlatformMonitor& stats_lock, size_t size);

  void enqueue(const char* msg, size_t size, LogFileStreamOutput* output,
               const LogDecorations decorations);
  void enqueue(LogFileStreamOutput& output, LogMessageBuffer::Iterator msg_iterator);

  enum DequeueResult {
    NoMessage, TooSmall, OK
  };
  DequeueResult dequeue(Message* out_descriptor, char* out, size_t out_size);

  void flush();
  void signal_flush();

  bool has_message();
  void await_message();
};

#endif // SHARE_LOGGING_CIRCULARSTRINGBUFFER_HPP
