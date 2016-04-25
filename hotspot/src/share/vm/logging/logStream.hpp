/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_LOGGING_LOGSTREAM_HPP
#define SHARE_VM_LOGGING_LOGSTREAM_HPP

#include "logging/log.hpp"
#include "logging/logHandle.hpp"
#include "memory/resourceArea.hpp"
#include "utilities/ostream.hpp"

// The base class of an output stream that logs to the logging framework.
template <class streamClass>
class LogStreamBase : public outputStream {
  streamClass     _current_line;
  LogTargetHandle _log_handle;

public:
  // Constructor to support creation from a LogTarget instance.
  //
  // LogTarget(Debug, gc) log;
  // LogStreamBase(log) stream;
  template <LogLevelType level, LogTagType T0, LogTagType T1, LogTagType T2, LogTagType T3, LogTagType T4, LogTagType GuardTag>
  LogStreamBase(const LogTargetImpl<level, T0, T1, T2, T3, T4, GuardTag>& type_carrier) :
      _log_handle(level, &LogTagSetMapping<T0, T1, T2, T3, T4>::tagset()) {}

  // Constructor to support creation from typed (likely NULL) pointer. Mostly used by the logging framework.
  //
  // LogStreamBase stream(log.debug());
  //  or
  // LogStreamBase stream((LogTargetImpl<level, T0, T1, T2, T3, T4, GuardTag>*)NULL);
  template <LogLevelType level, LogTagType T0, LogTagType T1, LogTagType T2, LogTagType T3, LogTagType T4, LogTagType GuardTag>
  LogStreamBase(const LogTargetImpl<level, T0, T1, T2, T3, T4, GuardTag>* type_carrier) :
      _log_handle(level, &LogTagSetMapping<T0, T1, T2, T3, T4>::tagset()) {}

  // Constructor to support creation from a LogTargetHandle.
  //
  // LogTarget(Debug, gc) log;
  // LogTargetHandle(log) handle;
  // LogStreamBase stream(handle);
  LogStreamBase(LogTargetHandle handle) : _log_handle(handle) {}

  // Constructor to support creation from a log level and tagset.
  //
  // LogStreamBase(level, tageset);
  LogStreamBase(LogLevelType level, LogTagSet* tagset) : _log_handle(level, tagset) {}

  ~LogStreamBase() {
    guarantee(_current_line.size() == 0, "Buffer not flushed. Missing call to print_cr()?");
  }

public:
  void write(const char* s, size_t len);
};

// A stringStream with an embedded ResourceMark.
class stringStreamWithResourceMark : outputStream {
 private:
  // The stringStream Resource allocate in the constructor,
  // so the order of the fields is important.
  ResourceMark _embedded_resource_mark;
  stringStream _stream;

 public:
  stringStreamWithResourceMark(size_t initial_bufsize = 256) :
      _embedded_resource_mark(),
      _stream(initial_bufsize) {}

  virtual void write(const char* c, size_t len) { _stream.write(c, len); }
  size_t      size()                            { return _stream.size(); }
  const char* base()                            { return _stream.base(); }
  void  reset()                                 { _stream.reset(); }
  char* as_string()                             { return _stream.as_string(); }
};

// An output stream that logs to the logging framework.
//
// The backing buffer is allocated in Resource memory.
// The caller is required to have a ResourceMark on the stack.
typedef LogStreamBase<stringStream> LogStreamNoResourceMark;

// An output stream that logs to the logging framework.
//
// The backing buffer is allocated in CHeap memory.
typedef LogStreamBase<bufferedStream> LogStreamCHeap;

// An output stream that logs to the logging framework, and embeds a ResourceMark.
//
// The backing buffer is allocated in Resource memory.
// The class is intended to be stack allocated.
// The class provides its own ResourceMark,
//  so care needs to be taken when nested ResourceMarks are used.
typedef LogStreamBase<stringStreamWithResourceMark> LogStream;

// Support creation of a LogStream without having to provide a LogTarget pointer.
#define LogStreamHandle(level, ...) LogStreamTemplate<LogLevel::level, LOG_TAGS(__VA_ARGS__)>

template <LogLevelType level, LogTagType T0, LogTagType T1, LogTagType T2, LogTagType T3, LogTagType T4, LogTagType GuardTag>
class LogStreamTemplate : public LogStream {
public:
  LogStreamTemplate() : LogStream((LogTargetImpl<level, T0, T1, T2, T3, T4, GuardTag>*)NULL) {}
};

#endif // SHARE_VM_LOGGING_LOGSTREAM_HPP
