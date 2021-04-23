/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
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

// LogDecorations objects are temporary variables in LogTagSet::log()
// It doesnot fit asynchronous logging well because flusher will access
// them in AsyncLog Thread. some decorators are very context sensitive, eg.
// uptime and tid, async logging has to copy LogDecorations objects to secure
// the accurarcy.
//
// LogDecorationsRef provides a relatively cheaper copy of LogDecorations,
// which consists of 256-byte buffer. The ref only copys strings what have
// materialized and use refcnt to avoid from duplicating.
class LogDecorationsRef : public CHeapObj<mtLogging>{
  friend class LogDecorations;
private:
  char* _decorations_buffer;
  char* _decoration_offset[LogDecorators::Count];
  size_t _refcnt;

  // only LogDecorations can create it.
  LogDecorationsRef() : _refcnt(0) {}
  LogDecorationsRef(const LogDecorationsRef& rhs) = delete;
public:
  LogDecorationsRef& operator++() {
    _refcnt++;
    return *this;
  }

  void operator--() {
    if (--_refcnt == 0 && this != &NoneRef) {
      FREE_C_HEAP_ARRAY(char, _decorations_buffer);
      delete this;
    }
  }

  size_t refcnt() const { return _refcnt; }

  // It is not constant value for convenience.
  // Do not need to care its refcnt as long as it does not delete itself.
  static LogDecorationsRef NoneRef;
};

// Temporary object containing the necessary data for a log call's decorations (timestamps, etc).
class LogDecorations {
 public:
  static const int DecorationsBufferSize = 256;
 private:
  char _decorations_buffer[DecorationsBufferSize];
  char* _decoration_offset[LogDecorators::Count];
  LogLevelType _level;
  const LogTagSet* _tagset;
  mutable LogDecorationsRef* _ref;
  static const char* volatile _host_name;

  const char* host_name();
  void create_decorations(const LogDecorators& decorators);

#define DECORATOR(name, abbr) char* create_##name##_decoration(char* pos);
  DECORATOR_LIST
#undef DECORATOR

 public:
  LogDecorations(LogLevelType level, const LogTagSet& tagset, const LogDecorators& decorators);
  LogDecorations(LogLevelType level, const LogTagSet& tagset, LogDecorationsRef& ref);
  LogDecorations(LogLevelType level, const LogDecorators& decorators);
  ~LogDecorations() {
    if (_ref != nullptr) {
      --(*_ref);
    }
  }
  void set_level(LogLevelType level) {
    _level = level;
  }

  LogLevelType get_level() const { return _level; }

  const LogTagSet& get_logTagSet() const {
    return *_tagset;
  }

  const char* decoration(LogDecorators::Decorator decorator) const {
    if (decorator == LogDecorators::level_decorator) {
      return LogLevel::name(_level);
    }

    if (_ref == nullptr) {
      return _decoration_offset[decorator];
    } else {
      return _ref->_decoration_offset[decorator];
    }
  }

  LogDecorationsRef& ref() const;
};
#endif // SHARE_LOGGING_LOGDECORATIONS_HPP
