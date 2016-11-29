/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
#ifndef SHARE_VM_LOGGING_LOGDECORATIONS_HPP
#define SHARE_VM_LOGGING_LOGDECORATIONS_HPP

#include "logging/logDecorators.hpp"
#include "logging/logTagSet.hpp"
#include "memory/allocation.hpp"

// Temporary object containing the necessary data for a log call's decorations (timestamps, etc).
class LogDecorations VALUE_OBJ_CLASS_SPEC {
 public:
  static const int DecorationsBufferSize = 256;
 private:
  char _decorations_buffer[DecorationsBufferSize];
  char* _decoration_offset[LogDecorators::Count];
  LogLevelType _level;
  const LogTagSet& _tagset;
  jlong _millis;
  static jlong _vm_start_time_millis;
  static const char* _host_name;

  jlong java_millis();
  void create_decorations(const LogDecorators& decorators);

#define DECORATOR(name, abbr) char* create_##name##_decoration(char* pos);
  DECORATOR_LIST
#undef DECORATOR

 public:
  static void initialize(jlong vm_start_time);

  LogDecorations(LogLevelType level, const LogTagSet& tagset, const LogDecorators& decorators);

  void set_level(LogLevelType level) {
    _level = level;
  }

  const char* decoration(LogDecorators::Decorator decorator) const {
    if (decorator == LogDecorators::level_decorator) {
      return LogLevel::name(_level);
    }
    return _decoration_offset[decorator];
  }
};

#endif // SHARE_VM_LOGGING_LOGDECORATIONS_HPP
