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
#include "logging/logDecorations.hpp"
#include "logging/logLevel.hpp"
#include "logging/logOutput.hpp"
#include "logging/logTag.hpp"
#include "logging/logTagSet.hpp"
#include "memory/allocation.inline.hpp"

LogTagSet*  LogTagSet::_list      = NULL;
size_t      LogTagSet::_ntagsets  = 0;

// This constructor is called only during static initialization.
// See the declaration in logTagSet.hpp for more information.
LogTagSet::LogTagSet(PrefixWriter prefix_writer, LogTagType t0, LogTagType t1, LogTagType t2, LogTagType t3, LogTagType t4)
    : _next(_list), _write_prefix(prefix_writer) {
  _tag[0] = t0;
  _tag[1] = t1;
  _tag[2] = t2;
  _tag[3] = t3;
  _tag[4] = t4;
  for (_ntags = 0; _ntags < LogTag::MaxTags && _tag[_ntags] != LogTag::__NO_TAG; _ntags++) {
  }
  _list = this;
  _ntagsets++;

  // Set the default output to warning and error level for all new tagsets.
  _output_list.set_output_level(LogOutput::Stderr, LogLevel::Default);
}

void LogTagSet::update_decorators(const LogDecorators& decorator) {
  LogDecorators new_decorators = decorator;
  for (LogOutputList::Iterator it = _output_list.iterator(); it != _output_list.end(); it++) {
    new_decorators.combine_with((*it)->decorators());
  }
  _decorators = new_decorators;
}

bool LogTagSet::has_output(const LogOutput* output) {
  for (LogOutputList::Iterator it = _output_list.iterator(); it != _output_list.end(); it++) {
    if (*it == output) {
      return true;
    }
  }
  return false;
}

void LogTagSet::log(LogLevelType level, const char* msg) {
  LogDecorations decorations(level, *this, _decorators);
  for (LogOutputList::Iterator it = _output_list.iterator(level); it != _output_list.end(); it++) {
    (*it)->write(decorations, msg);
  }
}

int LogTagSet::label(char* buf, size_t len, const char* separator) const {
  int tot_written = 0;
  for (size_t i = 0; i < _ntags; i++) {
    int written = jio_snprintf(buf + tot_written, len - tot_written, "%s%s",
                               (i == 0 ? "" : separator),
                               LogTag::name(_tag[i]));
    if (written < 0) {
      return -1;
    }
    tot_written += written;
  }
  return tot_written;
}

void LogTagSet::write(LogLevelType level, const char* fmt, ...) {
  va_list args;
  va_start(args, fmt);
  vwrite(level, fmt, args);
  va_end(args);
}

const size_t vwrite_buffer_size = 512;

void LogTagSet::vwrite(LogLevelType level, const char* fmt, va_list args) {
  assert(level >= LogLevel::First && level <= LogLevel::Last, "Log level:%d is incorrect", level);
  char buf[vwrite_buffer_size];
  va_list saved_args;           // For re-format on buf overflow.
  va_copy(saved_args, args);
  size_t prefix_len = _write_prefix(buf, sizeof(buf));
  // Check that string fits in buffer; resize buffer if necessary
  int ret = os::log_vsnprintf(buf + prefix_len, sizeof(buf) - prefix_len, fmt, args);
  assert(ret >= 0, "Log message buffer issue");
  if ((size_t)ret >= sizeof(buf)) {
    size_t newbuf_len = prefix_len + ret + 1;
    char* newbuf = NEW_C_HEAP_ARRAY(char, newbuf_len, mtLogging);
    memcpy(newbuf, buf, prefix_len);
    ret = os::log_vsnprintf(newbuf + prefix_len, newbuf_len - prefix_len, fmt, saved_args);
    assert(ret >= 0, "Log message buffer issue");
    log(level, newbuf);
    FREE_C_HEAP_ARRAY(char, newbuf);
  } else {
    log(level, buf);
  }
  va_end(saved_args);
}
