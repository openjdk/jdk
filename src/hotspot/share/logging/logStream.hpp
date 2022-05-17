/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_LOGGING_LOGSTREAM_HPP
#define SHARE_LOGGING_LOGSTREAM_HPP

#include "logging/log.hpp"
#include "logging/logHandle.hpp"
#include "logging/logMessage.hpp"
#include "utilities/align.hpp"
#include "utilities/ostream.hpp"
#include "runtime/os.hpp"

template<typename BackingLog>
class LogStreamImpl : public outputStream {
  friend class LogStreamTest_TestLineBufferAllocation_vm_Test;
  friend class LogStreamTest_TestLineBufferAllocationCap_vm_Test;

  // No heap allocation of LogStream.
  static void *operator new   (size_t) = delete;
  static void *operator new[] (size_t) = delete;

  // Helper class, maintains the line buffer. For small line lengths,
  // we avoid malloc and use a fixed sized member char array. If LogStream
  // is allocated on the stack, this means small lines are assembled
  // directly on the stack.
  class LineBuffer {
    char _smallbuf[64];
    char* _buf;
    size_t _cap;
    size_t _pos;
    void try_ensure_cap(size_t cap);
  public:
    LineBuffer();
    ~LineBuffer();
    bool is_empty() const { return _pos == 0; }
    const char* buffer() const { return _buf; }
    void append(const char* s, size_t len);
    void reset();
  };

private:
  LineBuffer _current_line;
protected:
  BackingLog _backing_log;
public:
  explicit LogStreamImpl(BackingLog bl)
    : _backing_log(bl) {};

  virtual ~LogStreamImpl() {
    if (_current_line.is_empty() == false) {
      _backing_log.print("%s", _current_line.buffer());
      _current_line.reset();
    }
  }

  bool is_enabled() {
    return _backing_log.is_enabled();
  }

  void write(const char* s, size_t len) override {
    if (len > 0 && s[len - 1] == '\n') {
      _current_line.append(s, len - 1); // omit the newline.
      _backing_log.print("%s", _current_line.buffer());
      _current_line.reset();
    } else {
      _current_line.append(s, len);
    }
    update_position(s, len);
  }
};

template<typename T>
LogStreamImpl<T>::LineBuffer::LineBuffer()
 : _buf(_smallbuf), _cap(sizeof(_smallbuf)), _pos(0)
{
  _buf[0] = '\0';
}

template<typename T>
LogStreamImpl<T>::LineBuffer::~LineBuffer() {
  assert(_pos == 0, "still outstanding bytes in the line buffer");
  if (_buf != _smallbuf) {
    os::free(_buf);
  }
}

// try_ensure_cap tries to enlarge the capacity of the internal buffer
// to the given atleast value. May fail if either OOM happens or atleast
// is larger than a reasonable max of 1 M. Caller must not assume
// capacity without checking.
template<typename T>
void LogStreamImpl<T>::LineBuffer::try_ensure_cap(size_t atleast) {
  assert(_cap >= sizeof(_smallbuf), "sanity");
  if (_cap < atleast) {
    // Cap out at a reasonable max to prevent runaway leaks.
    const size_t reasonable_max = 1 * M;
    assert(_cap <= reasonable_max, "sanity");
    if (_cap == reasonable_max) {
      return;
    }

    const size_t additional_expansion = 256;
    size_t newcap = align_up(atleast + additional_expansion, additional_expansion);
    if (newcap > reasonable_max) {
      log_info(logging)("Suspiciously long log line: \"%.100s%s",
              _buf, (_pos >= 100 ? "..." : ""));
      newcap = reasonable_max;
    }

    char* const newbuf = (char*) os::malloc(newcap, mtLogging);
    if (newbuf == NULL) { // OOM. Leave object unchanged.
      return;
    }
    if (_pos > 0) { // preserve old content
      memcpy(newbuf, _buf, _pos + 1); // ..including trailing zero
    }
    if (_buf != _smallbuf) {
      os::free(_buf);
    }
    _buf = newbuf;
    _cap = newcap;
  }
  assert(_cap >= atleast, "sanity");
}


template<typename T>
void LogStreamImpl<T>::LineBuffer::append(const char* s, size_t len) {
  assert(_buf[_pos] == '\0', "sanity");
  assert(_pos < _cap, "sanity");
  const size_t minimum_capacity_needed = _pos + len + 1;
  try_ensure_cap(minimum_capacity_needed);
  // try_ensure_cap may not have enlarged the capacity to the full requested
  // extend or may have not worked at all. In that case, just gracefully work
  // with what we have already; just truncate if necessary.
  if (_cap < minimum_capacity_needed) {
    len = _cap - _pos - 1;
    if (len == 0) {
      return;
    }
  }
  memcpy(_buf + _pos, s, len);
  _pos += len;
  _buf[_pos] = '\0';
}

template<typename T>
void LogStreamImpl<T>::LineBuffer::reset() {
  _pos = 0;
  _buf[_pos] = '\0';
}


class LogStream : public LogStreamImpl<LogTargetHandle>  {
  // see test/hotspot/gtest/logging/test_logStream.cpp
  friend class LogStreamTest_TestLineBufferAllocation_vm_Test;
  friend class LogStreamTest_TestLineBufferAllocationCap_vm_Test;

  // No heap allocation of LogStream.
  static void *operator new   (size_t) = delete;
  static void *operator new[] (size_t) = delete;
public:
  LogStream(const LogStream&) = delete;
  virtual ~LogStream() {};
  // Constructor to support creation from a LogTarget instance.
  //
  // LogTarget(Debug, gc) log;
  // LogStream(log) stream;
  template <LogLevelType level, LogTagType T0, LogTagType T1, LogTagType T2, LogTagType T3, LogTagType T4, LogTagType GuardTag>
  LogStream(const LogTargetImpl<level, T0, T1, T2, T3, T4, GuardTag>& type_carrier)
    : LogStreamImpl(LogTargetHandle(level, LogTagSetMapping<T0, T1, T2, T3, T4>::tagset())) {}

  // Constructor to support creation from typed (likely NULL) pointer. Mostly used by the logging framework.
  //
  // LogStream stream(log.debug());
  //  or
  // LogStream stream((LogTargetImpl<level, T0, T1, T2, T3, T4, GuardTag>*)NULL);
  template <LogLevelType level, LogTagType T0, LogTagType T1, LogTagType T2, LogTagType T3, LogTagType T4, LogTagType GuardTag>
  LogStream(const LogTargetImpl<level, T0, T1, T2, T3, T4, GuardTag>* type_carrier)
    : LogStreamImpl(LogTargetHandle(level, LogTagSetMapping<T0, T1, T2, T3, T4>::tagset())) {}

  // Constructor to support creation from a LogTargetHandle.
  //
  // LogTarget(Debug, gc) log;
  // LogTargetHandle(log) handle;
  // LogStream stream(handle);
  LogStream(LogTargetHandle handle)
    : LogStreamImpl(handle) {}

  // Constructor to support creation from a log level and tagset.
  //
  // LogStream(level, tageset);
  LogStream(LogLevelType level, LogTagSet& tagset)
    : LogStreamImpl(LogTargetHandle(level, tagset)) {}
};


// Support creation of a LogStream without having to provide a LogTarget pointer.
#define LogStreamHandle(level, ...) LogStreamTemplate<LogLevel::level, LOG_TAGS(__VA_ARGS__)>

template <LogLevelType level, LogTagType T0, LogTagType T1, LogTagType T2, LogTagType T3, LogTagType T4, LogTagType GuardTag>
class LogStreamTemplate : public LogStream {
public:
  LogStreamTemplate()
    : LogStream((LogTargetImpl<level, T0, T1, T2, T3, T4, GuardTag>*)NULL) {}
};

class LogMessageHandle {
  const LogLevelType _level;
  LogMessageImpl& _lm;
public:
  LogMessageHandle(const LogLevelType level, LogMessageImpl& lm)
    : _level(level), _lm(lm) {}
  bool is_enabled() {
    return _lm.is_level(_level);
  }
  void print(const char* fmt, ...) ATTRIBUTE_PRINTF(2, 3) {
    va_list args;
    va_start(args, fmt);
    if (is_enabled()) {
      _lm.vwrite(_level, fmt, args);
    }
    va_end(args);
  }
};


class NonInterleavingLogStream : public LogStreamImpl<LogMessageHandle> {
  static void *operator new   (size_t) = delete;
  static void *operator new[] (size_t) = delete;
public:
  NonInterleavingLogStream(LogLevelType level, LogMessageImpl& lm)
    : LogStreamImpl(LogMessageHandle(level, lm)) {}

  virtual ~NonInterleavingLogStream() {};
};


#endif // SHARE_LOGGING_LOGSTREAM_HPP
