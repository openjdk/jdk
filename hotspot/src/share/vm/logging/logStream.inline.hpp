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
#ifndef SHARE_VM_LOGGING_LOGSTREAM_INLINE_HPP
#define SHARE_VM_LOGGING_LOGSTREAM_INLINE_HPP

#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/resourceArea.hpp"
#include "utilities/ostream.hpp"

inline void LogStreamNoResourceMark::write(const char* s, size_t len) {
  if (len > 0 && s[len - 1] == '\n') {
    _current_line.write(s, len - 1);
    _tagset->write(_level, "%s", _current_line.as_string());
    _current_line.reset();
  } else {
    _current_line.write(s, len);
  }
  update_position(s, len);
}

// An output stream that logs to the logging framework, and embeds a ResourceMark.
//
//  The class is intended to be stack allocated.
//  Care needs to be taken when nested ResourceMarks are used.
class LogStream : public outputStream {
private:
  ResourceMark            _embedded_resource_mark;
  LogStreamNoResourceMark _stream;

public:
  // Constructor to support creation from a LogTarget instance.
  //
  // LogTarget(Debug, gc) log;
  // LogStream(log) stream;
  template <LogLevelType level, LogTagType T0, LogTagType T1, LogTagType T2, LogTagType T3, LogTagType T4, LogTagType GuardTag>
  LogStream(const LogTargetImpl<level, T0, T1, T2, T3, T4, GuardTag>& type_carrier) :
      _embedded_resource_mark(),
      _stream(level, &LogTagSetMapping<T0, T1, T2, T3, T4>::tagset()) {}

  // Constructor to support creation from typed (likely NULL) pointer. Mostly used by the logging framework.
  //
  // LogStream stream(log.debug());
  // LogStream stream((LogTargetImpl<level, T0, T1, T2, T3, T4, GuardTag>*)NULL);
  template <LogLevelType level, LogTagType T0, LogTagType T1, LogTagType T2, LogTagType T3, LogTagType T4, LogTagType GuardTag>
  LogStream(const LogTargetImpl<level, T0, T1, T2, T3, T4, GuardTag>* type_carrier) :
      _embedded_resource_mark(),
      _stream(level, &LogTagSetMapping<T0, T1, T2, T3, T4>::tagset()) {}

  // Override of outputStream::write.
  void write(const char* s, size_t len) { _stream.write(s, len); }
};

// Support creation of a LogStream without having to provide a LogTarget pointer.
#define LogStreamHandle(level, ...) LogStreamTemplate<LogLevel::level, LOG_TAGS(__VA_ARGS__)>

template <LogLevelType level, LogTagType T0, LogTagType T1, LogTagType T2, LogTagType T3, LogTagType T4, LogTagType GuardTag>
class LogStreamTemplate : public LogStream {
public:
  LogStreamTemplate() : LogStream((LogTargetImpl<level, T0, T1, T2, T3, T4, GuardTag>*)NULL) {}
};

#endif // SHARE_VM_LOGGING_LOGSTREAM_INLINE_HPP
