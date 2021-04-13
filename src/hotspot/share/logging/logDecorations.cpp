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
#include "precompiled.hpp"
#include "jvm.h"
#include "logging/logConfiguration.hpp"
#include "logging/logDecorations.hpp"
#include "runtime/atomic.hpp"
#include "runtime/os.hpp"
#include "runtime/thread.inline.hpp"
#include "services/management.hpp"

const char* volatile LogDecorations::_host_name = NULL;

LogDecorations::LogDecorations(LogLevelType level, const LogTagSet &tagset, const LogDecorators &decorators)
    : _level(level), _tagset(tagset) {
  create_decorations(decorators);
}

const char* LogDecorations::host_name() {
  const char* host_name = Atomic::load_acquire(&_host_name);
  if (host_name == NULL) {
    char buffer[1024];
    if (os::get_host_name(buffer, sizeof(buffer))) {
      host_name = os::strdup_check_oom(buffer);
      const char* old_value = Atomic::cmpxchg(&_host_name, (const char*)NULL, host_name);
      if (old_value != NULL) {
        os::free((void *) host_name);
        host_name = old_value;
      }
    }
  }
  return host_name;
}

void LogDecorations::create_decorations(const LogDecorators &decorators) {
  char* position = _decorations_buffer;
  #define DECORATOR(full_name, abbr) \
  if (decorators.is_decorator(LogDecorators::full_name##_decorator)) { \
    _decoration_offset[LogDecorators::full_name##_decorator] = position; \
    position = create_##full_name##_decoration(position) + 1; \
  } else { \
    _decoration_offset[LogDecorators::full_name##_decorator] = NULL; \
  }
  DECORATOR_LIST
#undef DECORATOR
}

#define ASSERT_AND_RETURN(written, pos) \
    assert(written >= 0, "Decorations buffer overflow"); \
    return pos + written;

char* LogDecorations::create_time_decoration(char* pos) {
  char* buf = os::iso8601_time(pos, 29);
  int written = buf == NULL ? -1 : 29;
  ASSERT_AND_RETURN(written, pos)
}

char* LogDecorations::create_utctime_decoration(char* pos) {
  char* buf = os::iso8601_time(pos, 29, true);
  int written = buf == NULL ? -1 : 29;
  ASSERT_AND_RETURN(written, pos)
}

char * LogDecorations::create_uptime_decoration(char* pos) {
  int written = jio_snprintf(pos, DecorationsBufferSize - (pos - _decorations_buffer), "%.3fs", os::elapsedTime());
  ASSERT_AND_RETURN(written, pos)
}

char * LogDecorations::create_timemillis_decoration(char* pos) {
  int written = jio_snprintf(pos, DecorationsBufferSize - (pos - _decorations_buffer), INT64_FORMAT "ms", os::javaTimeMillis());
  ASSERT_AND_RETURN(written, pos)
}

// Small helper for uptime conversion
static jlong elapsed_time(int unit_multiplier) {
  return (jlong)(os::elapsedTime() * unit_multiplier);
}

char * LogDecorations::create_uptimemillis_decoration(char* pos) {
  int written = jio_snprintf(pos, DecorationsBufferSize - (pos - _decorations_buffer),
                             INT64_FORMAT "ms", elapsed_time(MILLIUNITS));
  ASSERT_AND_RETURN(written, pos)
}

char * LogDecorations::create_timenanos_decoration(char* pos) {
  int written = jio_snprintf(pos, DecorationsBufferSize - (pos - _decorations_buffer), INT64_FORMAT "ns", os::javaTimeNanos());
  ASSERT_AND_RETURN(written, pos)
}

char * LogDecorations::create_uptimenanos_decoration(char* pos) {
  int written = jio_snprintf(pos, DecorationsBufferSize - (pos - _decorations_buffer), INT64_FORMAT "ns", elapsed_time(NANOUNITS));
  ASSERT_AND_RETURN(written, pos)
}

char * LogDecorations::create_pid_decoration(char* pos) {
  int written = jio_snprintf(pos, DecorationsBufferSize - (pos - _decorations_buffer), "%d", os::current_process_id());
  ASSERT_AND_RETURN(written, pos)
}

char * LogDecorations::create_tid_decoration(char* pos) {
  int written = jio_snprintf(pos, DecorationsBufferSize - (pos - _decorations_buffer),
                             INTX_FORMAT, os::current_thread_id());
  ASSERT_AND_RETURN(written, pos)
}

char* LogDecorations::create_level_decoration(char* pos) {
  // Avoid generating the level decoration because it may change.
  // The decoration() method has a special case for level decorations.
  return pos;
}

char* LogDecorations::create_tags_decoration(char* pos) {
  int written = _tagset.label(pos, DecorationsBufferSize - (pos - _decorations_buffer));
  ASSERT_AND_RETURN(written, pos)
}

char* LogDecorations::create_hostname_decoration(char* pos) {
  int written = jio_snprintf(pos, DecorationsBufferSize - (pos - _decorations_buffer), "%s", host_name());
  ASSERT_AND_RETURN(written, pos)
}
