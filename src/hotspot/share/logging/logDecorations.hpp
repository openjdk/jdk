/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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
#ifndef SHARE_LOGGING_LOGDECORATIONS_HPP
#define SHARE_LOGGING_LOGDECORATIONS_HPP

#include "logging/logDecorators.hpp"
#include "logging/logTagSet.hpp"

// Temporary object containing the necessary data for a log call's decorations (timestamps, etc).
class LogDecorations {
 public:
  static const int DecorationsBufferSize = 256;
 private:
  // Buffer for resolved decorations
  char _decorations_buffer[DecorationsBufferSize];
  // Lookup table, contains offsets of the decoration string start addresses by logtag
  // To keep its size small (which matters, see e.g. JDK-8229517) we use a byte index. That is
  //  fine since the max. size of the decorations buffer is 256. Note: 255 is reserved
  //  as "invalid offset" marker.
  typedef uint8_t offset_t;
  static const offset_t invalid_offset = DecorationsBufferSize - 1;
  offset_t _decoration_offset[LogDecorators::Count];
  LogLevelType _level;
  const LogTagSet& _tagset;
  static const char* volatile _host_name;

  const char* host_name();
  void create_decorations(const LogDecorators& decorators);

#define DECORATOR(name, abbr) char* create_##name##_decoration(char* pos);
  DECORATOR_LIST
#undef DECORATOR

 public:
  LogDecorations(LogLevelType level, const LogTagSet& tagset, const LogDecorators& decorators);

  void set_level(LogLevelType level) {
    _level = level;
  }

  const char* decoration(LogDecorators::Decorator decorator) const {
    if (decorator == LogDecorators::level_decorator) {
      return LogLevel::name(_level);
    }
    const offset_t offset = _decoration_offset[decorator];
    if (offset == invalid_offset) {
      return NULL;
    }
    return _decorations_buffer + offset;
  }
};

#endif // SHARE_LOGGING_LOGDECORATIONS_HPP
