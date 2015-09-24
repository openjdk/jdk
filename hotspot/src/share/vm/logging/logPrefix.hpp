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
#ifndef SHARE_VM_LOGGING_LOGPREFIX_HPP
#define SHARE_VM_LOGGING_LOGPREFIX_HPP

#include "gc/shared/gcId.hpp"
#include "logging/logTag.hpp"

// Prefixes prepend each log message for a specified tagset with the given prefix.
// A prefix consists of a format string and a value or callback. Prefixes are added
// after the decorations but before the log message.
//
// List of prefixes for specific tags and/or tagsets.
// Syntax: LOG_PREFIX(<printf format>, <value/callback for value>, LOG_TAGS(<chosen log tags>))
#define LOG_PREFIX_LIST // Currently unused/empty

// The empty prefix, used when there's no prefix defined.
template <LogTagType T0, LogTagType T1, LogTagType T2, LogTagType T3, LogTagType T4, LogTagType GuardTag = LogTag::__NO_TAG>
struct LogPrefix : public AllStatic {
  STATIC_ASSERT(GuardTag == LogTag::__NO_TAG);
  static size_t prefix(char* buf, size_t len) {
    return 0;
  }
};

#define LOG_PREFIX(fmt, fn, ...) \
template <> struct LogPrefix<__VA_ARGS__> { \
  static size_t prefix(char* buf, size_t len) { \
    int ret = jio_snprintf(buf, len, fmt, fn); \
    assert(ret >= 0, \
           err_msg("Failed to prefix log message using prefix ('%s', '%s'), log buffer too small?", fmt, #fn)); \
    return ret; \
  } \
};
LOG_PREFIX_LIST
#undef LOG_PREFIX

#endif // SHARE_VM_LOGGING_LOGPREFIX_HPP
