/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "logging/logFileStreamOutput.hpp"
#include "logging/logOutput.hpp"
#include "logging/logTagSet.hpp"
#include "memory/allocation.inline.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/os.inline.hpp"

LogOutput::~LogOutput() {
  os::free(_config_string);
}

void LogOutput::clear_config_string() {
  os::free(_config_string);
  _config_string_buffer_size = InitialConfigBufferSize;
  _config_string = NEW_C_HEAP_ARRAY(char, _config_string_buffer_size, mtLogging);
  _config_string[0] = '\0';
}

void LogOutput::set_config_string(const char* string) {
  os::free(_config_string);
  _config_string = os::strdup(string, mtLogging);
  _config_string_buffer_size = strlen(_config_string) + 1;
}

void LogOutput::add_to_config_string(const LogTagSet* ts, LogLevelType level) {
  if (_config_string_buffer_size < InitialConfigBufferSize) {
    _config_string_buffer_size = InitialConfigBufferSize;
    _config_string = REALLOC_C_HEAP_ARRAY(char, _config_string, _config_string_buffer_size, mtLogging);
  }

  size_t offset = strlen(_config_string);
  if (offset > 0) {
    // Add commas in-between tag and level combinations in the config string
    _config_string[offset++] = ',';
  }

  for (;;) {
    int ret = ts->label(_config_string + offset, _config_string_buffer_size - offset, "+");
    if (ret == -1) {
      // Double the buffer size and retry
      _config_string_buffer_size *= 2;
      _config_string = REALLOC_C_HEAP_ARRAY(char, _config_string, _config_string_buffer_size, mtLogging);
      continue;
    }
    break;
  };

  offset = strlen(_config_string);
  for (;;) {
    int ret = jio_snprintf(_config_string + offset, _config_string_buffer_size - offset, "=%s", LogLevel::name(level));
    if (ret == -1) {
      _config_string_buffer_size *= 2;
      _config_string = REALLOC_C_HEAP_ARRAY(char, _config_string, _config_string_buffer_size, mtLogging);
      continue;
    }
    break;
  }
}

void LogOutput::describe(outputStream *out) {
  out->print("%s ", name());
  out->print_raw(config_string());
  out->print(" ");
  char delimiter[2] = {0};
  for (size_t d = 0; d < LogDecorators::Count; d++) {
    LogDecorators::Decorator decorator = static_cast<LogDecorators::Decorator>(d);
    if (decorators().is_decorator(decorator)) {
      out->print("%s%s", delimiter, LogDecorators::name(decorator));
      *delimiter = ',';
    }
  }
}

