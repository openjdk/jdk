/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "logging/logStream.hpp"
#include "runtime/os.hpp"
#include "utilities/align.hpp"

LogStreamImplBase::LineBuffer::LineBuffer()
  : _buf(_smallbuf), _cap(sizeof(_smallbuf)), _pos(0) {
  _buf[0] = '\0';
}

LogStreamImplBase::LineBuffer::~LineBuffer() {
  assert(_pos == 0, "still outstanding bytes in the line buffer");
  if (_buf != _smallbuf) {
    os::free(_buf);
  }
}

// try_ensure_cap tries to enlarge the capacity of the internal buffer
// to the given atleast value. May fail if either OOM happens or atleast
// is larger than a reasonable max of 1 M.
// Returns whether the capacity is at least atleast bytes.
bool LogStreamImplBase::LineBuffer::try_ensure_cap(size_t atleast) {
  // Cap out at a reasonable max to prevent runaway leaks.
  const size_t reasonable_max = 1 * M;

  assert(_cap >= sizeof(_smallbuf), "sanity");
  assert(_cap <= reasonable_max, "sanity");

  if (_cap >= atleast) {
    return true;
  }
  if (_cap == reasonable_max) {
    return false;
  }

  const size_t additional_expansion = 256;
  size_t newcap = align_up(atleast + additional_expansion, additional_expansion);

  if (newcap > reasonable_max) {
    log_info(logging)("Suspiciously long log line: \"%.100s%s",
                      _buf, (_pos >= 100 ? "..." : ""));
    newcap = reasonable_max;
  }

  char* const newbuf = (char*)os::malloc(newcap, mtLogging);
  if (newbuf == nullptr) { // OOM. Leave object unchanged.
    return false;
  }
  if (_pos > 0) { // preserve old content
    memcpy(newbuf, _buf, _pos + 1); // ..including trailing zero
  }
  if (_buf != _smallbuf) {
    os::free(_buf);
  }
  _buf = newbuf;
  _cap = newcap;

  return _cap >= atleast;
}

void LogStreamImplBase::LineBuffer::append(const char* s, size_t len) {
  assert(_buf[_pos] == '\0', "sanity");
  assert(_pos < _cap, "sanity");
  const size_t minimum_capacity_needed = _pos + len + 1;
  const bool has_capacity = try_ensure_cap(minimum_capacity_needed);
  // try_ensure_cap may not have enlarged the capacity to the full requested
  // extent or may have not worked at all. In that case, just gracefully work
  // with what we have already; just truncate if necessary.
  if (!has_capacity) {
    len = _cap - _pos - 1;
    if (len == 0) {
      return;
    }
  }
  memcpy(_buf + _pos, s, len);
  _pos += len;
  _buf[_pos] = '\0';
}

void LogStreamImplBase::LineBuffer::reset() {
  _pos = 0;
  _buf[_pos] = '\0';
}

template <typename BackingLog>
LogStreamImpl<BackingLog>::~LogStreamImpl() {
  if (!_current_line.is_empty()) {
    _backing_log.print("%s", _current_line.buffer());
    _current_line.reset();
  }
}

template <typename BackingLog>
void LogStreamImpl<BackingLog>::write(const char* s, size_t len) {
  if (len > 0 && s[len - 1] == '\n') {
    _current_line.append(s, len - 1); // omit the newline.
    _backing_log.print("%s", _current_line.buffer());
    _current_line.reset();
  } else {
    _current_line.append(s, len);
  }
  update_position(s, len);
}

template class LogStreamImpl<LogTargetHandle>;
template class LogStreamImpl<LogMessageHandle>;
