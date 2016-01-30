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
#ifndef SHARE_VM_LOGGING_LOG_HPP
#define SHARE_VM_LOGGING_LOG_HPP

#include "logging/logLevel.hpp"
#include "logging/logPrefix.hpp"
#include "logging/logTagSet.hpp"
#include "logging/logTag.hpp"
#include "memory/allocation.hpp"
#include "memory/allocation.inline.hpp"
#include "runtime/os.hpp"
#include "utilities/debug.hpp"
#include "utilities/ostream.hpp"

//
// Logging macros
//
// Usage:
//   log_<level>(<comma separated log tags>)(<printf-style log arguments>);
// e.g.
//   log_debug(logging)("message %d", i);
//
// Note that these macros will not evaluate the arguments unless the logging is enabled.
//
#define log_error(...)   (!log_is_enabled(Error, __VA_ARGS__))   ? (void)0 : Log<LOG_TAGS(__VA_ARGS__)>::write<LogLevel::Error>
#define log_warning(...) (!log_is_enabled(Warning, __VA_ARGS__)) ? (void)0 : Log<LOG_TAGS(__VA_ARGS__)>::write<LogLevel::Warning>
#define log_info(...)    (!log_is_enabled(Info, __VA_ARGS__))    ? (void)0 : Log<LOG_TAGS(__VA_ARGS__)>::write<LogLevel::Info>
#define log_debug(...)   (!log_is_enabled(Debug, __VA_ARGS__))   ? (void)0 : Log<LOG_TAGS(__VA_ARGS__)>::write<LogLevel::Debug>
#define log_trace(...)   (!log_is_enabled(Trace, __VA_ARGS__))   ? (void)0 : Log<LOG_TAGS(__VA_ARGS__)>::write<LogLevel::Trace>

// Macros for logging that should be excluded in product builds.
// Available for levels Info, Debug and Trace. Includes test macro that
// evaluates to false in product builds.
#ifndef PRODUCT
#define log_develop_info(...)  (!log_is_enabled(Info, __VA_ARGS__))   ? (void)0 : Log<LOG_TAGS(__VA_ARGS__)>::write<LogLevel::Info>
#define log_develop_debug(...) (!log_is_enabled(Debug, __VA_ARGS__)) ? (void)0 : Log<LOG_TAGS(__VA_ARGS__)>::write<LogLevel::Debug>
#define log_develop_trace(...) (!log_is_enabled(Trace, __VA_ARGS__))  ? (void)0 : Log<LOG_TAGS(__VA_ARGS__)>::write<LogLevel::Trace>
#define develop_log_is_enabled(level, ...)  log_is_enabled(level, __VA_ARGS__)
#else
#define DUMMY_ARGUMENT_CONSUMER(...)
#define log_develop_info(...)  DUMMY_ARGUMENT_CONSUMER
#define log_develop_debug(...) DUMMY_ARGUMENT_CONSUMER
#define log_develop_trace(...) DUMMY_ARGUMENT_CONSUMER
#define develop_log_is_enabled(...)  false
#endif

// Convenience macro to test if the logging is enabled on the specified level for given tags.
#define log_is_enabled(level, ...) (Log<LOG_TAGS(__VA_ARGS__)>::is_level(LogLevel::level))

//
// Log class for more advanced logging scenarios.
// Has printf-style member functions for each log level (trace(), debug(), etc).
//
// Also has outputStream compatible API for the different log-levels.
// The streams are resource allocated when requested and are accessed through
// calls to <level>_stream() functions (trace_stream(), debug_stream(), etc).
//
// Example usage:
//   LogHandle(logging) log;
//   if (log.is_debug()) {
//     ...
//     log.debug("result = %d", result).trace(" tracing info");
//     obj->print_on(log.debug_stream());
//   }
//
#define LogHandle(...)  Log<LOG_TAGS(__VA_ARGS__)>

template <LogTagType T0, LogTagType T1 = LogTag::__NO_TAG, LogTagType T2 = LogTag::__NO_TAG, LogTagType T3 = LogTag::__NO_TAG,
          LogTagType T4 = LogTag::__NO_TAG, LogTagType GuardTag = LogTag::__NO_TAG>
class Log VALUE_OBJ_CLASS_SPEC {
 private:
  static const size_t LogBufferSize = 512;
 public:
  // Make sure no more than the maximum number of tags have been given.
  // The GuardTag allows this to be detected if/when it happens. If the GuardTag
  // is not __NO_TAG, the number of tags given exceeds the maximum allowed.
  STATIC_ASSERT(GuardTag == LogTag::__NO_TAG); // Number of logging tags exceeds maximum supported!

  // Empty constructor to avoid warnings on MSVC about unused variables
  // when the log instance is only used for static functions.
  Log() {
  }

  static bool is_level(LogLevelType level) {
    return LogTagSetMapping<T0, T1, T2, T3, T4>::tagset().is_level(level);
  }

  template <LogLevelType Level>
  ATTRIBUTE_PRINTF(1, 2)
  static void write(const char* fmt, ...) {
    va_list args;
    va_start(args, fmt);
    vwrite<Level>(fmt, args);
    va_end(args);
  };

  template <LogLevelType Level>
  ATTRIBUTE_PRINTF(1, 0)
  static void vwrite(const char* fmt, va_list args) {
    char buf[LogBufferSize];
    va_list saved_args;         // For re-format on buf overflow.
    va_copy(saved_args, args);
    size_t prefix_len = LogPrefix<T0, T1, T2, T3, T4>::prefix(buf, sizeof(buf));
    // Check that string fits in buffer; resize buffer if necessary
    int ret = os::log_vsnprintf(buf + prefix_len, sizeof(buf) - prefix_len, fmt, args);
    assert(ret >= 0, "Log message buffer issue");
    if ((size_t)ret >= sizeof(buf)) {
      size_t newbuf_len = prefix_len + ret + 1;
      char* newbuf = NEW_C_HEAP_ARRAY(char, newbuf_len, mtLogging);
      prefix_len = LogPrefix<T0, T1, T2, T3, T4>::prefix(newbuf, newbuf_len);
      ret = os::log_vsnprintf(newbuf + prefix_len, newbuf_len - prefix_len, fmt, saved_args);
      assert(ret >= 0, "Log message buffer issue");
      puts<Level>(newbuf);
      FREE_C_HEAP_ARRAY(char, newbuf);
    } else {
      puts<Level>(buf);
    }
  }

  template <LogLevelType Level>
  static void puts(const char* string) {
    LogTagSetMapping<T0, T1, T2, T3, T4>::tagset().log(Level, string);
  }

#define LOG_LEVEL(level, name) ATTRIBUTE_PRINTF(2, 0) \
  Log& v##name(const char* fmt, va_list args) { \
    vwrite<LogLevel::level>(fmt, args); \
    return *this; \
  } \
  Log& name(const char* fmt, ...) ATTRIBUTE_PRINTF(2, 3) { \
    va_list args; \
    va_start(args, fmt); \
    vwrite<LogLevel::level>(fmt, args); \
    va_end(args); \
    return *this; \
  } \
  static bool is_##name() { \
    return is_level(LogLevel::level); \
  } \
  static outputStream* name##_stream() { \
    return new logStream(write<LogLevel::level>); \
  }
  LOG_LEVEL_LIST
#undef LOG_LEVEL
};

#endif // SHARE_VM_LOGGING_LOG_HPP
