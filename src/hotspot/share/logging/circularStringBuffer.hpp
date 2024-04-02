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

#ifndef SHARE_LOGGING_CIRCULARSTRINGBUFFER_HPP
#define SHARE_LOGGING_CIRCULARSTRINGBUFFER_HPP
#include "logging/logFileStreamOutput.hpp"
#include "nmt/memTracker.hpp"
#include "runtime/mutex.hpp"
#include "runtime/os.inline.hpp"
#include "runtime/semaphore.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/resourceHash.hpp"

#include <string.h>
#ifdef LINUX
#include <sys/mman.h>
#endif

// The CircularMapping is a struct that provides
// an interface for writing and reading bytes in a circular buffer
// correctly. This indirection is necessary because there are two
// underlying implementations: Linux, and all others.
#ifdef LINUX
// Implements a circular buffer by using the virtual memory mapping facilities of the OS.
// Specifically, it reserves virtual memory with twice the size of the requested buffer.
// The latter half of this buffer is then mapped back to the start of the first buffer.
// This allows for write_bytes and read_bytes to consist of a single memcpy, as the
// wrap-around is dealt with by the virtual memory system.
struct CircularMapping {
  FILE* file;
  char* buffer;
  size_t size;

  CircularMapping()
  : file(nullptr), buffer(nullptr), size(0) {
  };

  CircularMapping(size_t size);
  ~CircularMapping() {
    ::munmap(buffer, size * 2);
    ::fclose(file);
  }

  void write_bytes(size_t at, const char* bytes, size_t size) {
    ::memcpy(&buffer[at], bytes, size);
  }

  void read_bytes(size_t at, char* out, size_t size) {
    ::memcpy(out, &buffer[at], size);
  }
};
#else
// On other platforms we resort to a double memcpy.
struct CircularMapping {
  char* buffer;
  size_t size;
  CircularMapping()
  : buffer(nullptr), size(0) {
  };
  CircularMapping(size_t size);

  void write_bytes(size_t at, const char* bytes, size_t size) {
    const size_t part1_size = MIN2(size, this->size - at);
    const size_t part2_size = size - part1_size;

    ::memcpy(&buffer[at], bytes, part1_size);
    ::memcpy(buffer, &bytes[part1_size], part2_size);
  }

  void read_bytes(size_t at, char* out, size_t size) {
    const size_t part1_size = MIN2(size, this->size - at);
    const size_t part2_size = size - part1_size;

    ::memcpy(out, &buffer[at], part1_size);
    ::memcpy(&out[part1_size], buffer, part2_size);
  }

  ~CircularMapping() {
    os::release_memory(buffer, size);
  }
};
#endif

class CircularStringBuffer {
  friend class AsyncLogTest;

public:
    // account for dropped messages
  using StatisticsMap = ResourceHashtable<LogFileStreamOutput*, uint32_t, 17, /*table_size*/
                                        AnyObj::C_HEAP, mtLogging>;
private:
  static const LogDecorations& None;
  const bool _should_stall; // Should a producer stall until a consumer has made room for its message?

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

  size_t used();
  size_t unused();
  size_t calc_mem(size_t sz);

public:
  // Messsage is the header of a log line and contains its associated decorations and output.
  // It is directly followed by the c-str of the log line. The log line is padded at the end
  // to ensure correct alignment for the Message. A Message is considered to be a flush token
  // when its output is null.
  //
  // Example layout:
  // ---------------------------------------------
  // |_output|_decorations|"a log line", |pad| <- Message aligned.
  // |_output|_decorations|"yet another",|pad|
  // ...
  // |nullptr|_decorations|"",|pad| <- flush token
  // |<- _pos
  // ---------------------------------------------
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

private:
  void enqueue_locked(const char* msg, size_t size, LogFileStreamOutput* output, const LogDecorations decorations);

public:
  NONCOPYABLE(CircularStringBuffer);
  CircularStringBuffer(StatisticsMap& stats, PlatformMonitor& stats_lock, size_t size, bool should_stall = false);

  void enqueue(const char* msg, size_t size, LogFileStreamOutput* output,
               const LogDecorations decorations);
  void enqueue(LogFileStreamOutput& output, LogMessageBuffer::Iterator msg_iterator);

  enum DequeueResult {
    NoMessage, // There was no message in the buffer
    TooSmall,  // The provided out buffer is too small
    OK         // A message was found and copied over to the out buffer and out_message.
  };
  DequeueResult dequeue(Message* out_message, char* out, size_t out_size);

  // Await flushing, blocks until signal_flush() is called by the flusher.
  void flush();
  void signal_flush();

  bool has_message();
  void await_message();
};

#endif // SHARE_LOGGING_CIRCULARSTRINGBUFFER_HPP
