/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CDS_AOTLOGGING_HPP
#define SHARE_CDS_AOTLOGGING_HPP

#include "cds/cds_globals.hpp"
#include "cds/cdsConfig.hpp"
#include "logging/log.hpp"

// UL Logging for AOT
// ==================
//
// The old "CDS" feature is rebranded as "AOT" in JEP 483. Therefore, UL logging
// related to the AOT features should be using the [aot] tag.
//
// However, some old scripts may be using -Xlog:cds for diagnostic purposes. To
// provide a fair amount of backwards compatibility for such scripts, some AOT
// logs that are likely to be used by such scripts are printed using the macros
// in this header file.
//
// NOTE: MOST of the AOT logs will be using the usual macros such as log_info(aot)(...).
// The information below does NOT apply to such logs.
//
// CDS Compatibility Logs & Compatibility Macros
// =============================================
//
// A subset of the original CDS logs (the "CDS Compatibility Logs") have been
// chosen in JDK 25. These logs are guarded using the aot_log_xxx compatibility
// macros. Before JDK 25, such code looked like this:
//
//      log_info(cds)("trying to map %s%s", info, _full_path);
//      log_warning(cds)("Unable to read the file header.");
//
// New code since JDK 25:
//
//      aot_log_info(aot)("trying to map %s%s", info, _full_path);
//      aot_log_warning(aot)("Unable to read the file header.");
//
// The messages printed with the log_aot_xxx() macros work as if they are
// using the [cds] tag when running with the "classic" CDS workflow (i.e.,
// new -XX:AOTxxx flags are not used).
//
//      $ java -Xlog:cds -XX:SharedArchiveFile=bad.jsa ...
//      [0.020s][info][cds] trying to map bad.jsa
//      [0.020s][warning][cds] Unable to read the file header
//
// However, when running new AOT flags such as-XX:AOTCache, these messages are
// under the [aot] tag:
//
//      $ java -Xlog:aot -XX:AOTCache=bad.aot ...
//      [0.020s][info][aot] trying to map bad.aot
//      [0.020s][warning][aot] Unable to read the file header
//
// Rules on selection and printing
//
// [1] When using AOT cache
//     - These logs are selected via the aot tag, and not the cds tag.
//       They are always printed with [aot] decoration
//
// [2] When using CDS archives
//     - These logs are selected via the cds tag, and not the aot tag.
//       They are always printed with [cds] decoration
//
// Deprecation Process
// ===================
//
// This is modeled after the deprecate/obsolete/expire process of VM options in arguments.cpp
//
// JDK 25 - When no -XX:AOTxxx flags are used, the CDS Compatibility Logs must be selected
//          with -Xlog:cds
//
// JDK 26 - Same as above, except that when -Xlog:cds is specified in the command-line, a warning
//          message is printed to indicate that -Xlog:cds is deprecated.
//
// JDK 27 - The CDS Compatibility Logs must be selected with -Xlog:aot.
//
//          When -Xlog:cds is specified in the command-line, a warning message is printed to
//          indicate that -Xlog:cds is obsolete.
//
// JDK 28 - When -Xlog:cds is specified in the command-line, the VM will exit with an error message:
//
//          [0.002s][error][logging] Invalid tag 'cds' in log selection.
//          Invalid -Xlog option '-Xlog:cds', see error log for details.
//

// The following macros are inspired by the same macros (without the aot_ prefix) in logging/log.hpp

#define aot_log_is_enabled(level, ...) (AOTLogImpl<LOG_TAGS(__VA_ARGS__)>::is_level(LogLevel::level))

#define aot_log_error(...)    (!aot_log_is_enabled(Error, __VA_ARGS__))   ? (void)0 : AOTLogImpl<LOG_TAGS(__VA_ARGS__)>::write<LogLevel::Error>
#define aot_log_warning(...)  (!aot_log_is_enabled(Warning, __VA_ARGS__)) ? (void)0 : AOTLogImpl<LOG_TAGS(__VA_ARGS__)>::write<LogLevel::Warning>
#define aot_log_info(...)     (!aot_log_is_enabled(Info, __VA_ARGS__))    ? (void)0 : AOTLogImpl<LOG_TAGS(__VA_ARGS__)>::write<LogLevel::Info>
#define aot_log_debug(...)    (!aot_log_is_enabled(Debug, __VA_ARGS__))   ? (void)0 : AOTLogImpl<LOG_TAGS(__VA_ARGS__)>::write<LogLevel::Debug>
#define aot_log_trace(...)    (!aot_log_is_enabled(Trace, __VA_ARGS__))   ? (void)0 : AOTLogImpl<LOG_TAGS(__VA_ARGS__)>::write<LogLevel::Trace>

template <LogTagType IGNORED, LogTagType T1 = LogTag::__NO_TAG, LogTagType T2 = LogTag::__NO_TAG, LogTagType T3 = LogTag::__NO_TAG,
          LogTagType T4 = LogTag::__NO_TAG, LogTagType GuardTag = LogTag::__NO_TAG>
class AOTLogImpl {
 public:
  // Make sure no more than the maximum number of tags have been given.
  // The GuardTag allows this to be detected if/when it happens. If the GuardTag
  // is not __NO_TAG, the number of tags given exceeds the maximum allowed.
  STATIC_ASSERT(GuardTag == LogTag::__NO_TAG); // Number of logging tags exceeds maximum supported!

  // Empty constructor to avoid warnings on MSVC about unused variables
  // when the log instance is only used for static functions.
  AOTLogImpl() {
  }

  static bool is_level(LogLevelType level) {
    if (CDSConfig::new_aot_flags_used()) {
      return LogTagSetMapping<LogTag::_aot, T1, T2, T3, T4>::tagset().is_level(level);
    } else {
      return LogTagSetMapping<LogTag::_cds, T1, T2, T3, T4>::tagset().is_level(level);
    }
  }

  ATTRIBUTE_PRINTF(2, 3)
  static void write(LogLevelType level, const char* fmt, ...) {
    va_list args;
    va_start(args, fmt);
    vwrite(level, fmt, args);
    va_end(args);
  }

  template <LogLevelType Level>
  ATTRIBUTE_PRINTF(1, 2)
  static void write(const char* fmt, ...) {
    va_list args;
    va_start(args, fmt);
    vwrite(Level, fmt, args);
    va_end(args);
  }

  ATTRIBUTE_PRINTF(2, 0)
  static void vwrite(LogLevelType level, const char* fmt, va_list args) {
    if (CDSConfig::new_aot_flags_used()) {
      LogTagSetMapping<LogTag::_aot, T1, T2, T3, T4>::tagset().vwrite(level, fmt, args);
    } else {
      LogTagSetMapping<LogTag::_cds, T1, T2, T3, T4>::tagset().vwrite(level, fmt, args);
    }
  }
};

#endif
