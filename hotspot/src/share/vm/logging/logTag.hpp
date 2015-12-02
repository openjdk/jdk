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
#ifndef SHARE_VM_LOGGING_LOGTAG_HPP
#define SHARE_VM_LOGGING_LOGTAG_HPP

#include "memory/allocation.hpp"
#include "utilities/globalDefinitions.hpp"

// List of available logging tags. New tags should be added here.
// (The tags 'all', 'disable' and 'help' are special tags that can
// not be used in log calls, and should not be listed below.)
#define LOG_TAG_LIST \
  LOG_TAG(defaultmethods) \
  LOG_TAG(logging) \
  LOG_TAG(safepoint)

#define PREFIX_LOG_TAG(T) (LogTag::T)

// Expand a set of log tags to their prefixed names.
// For error detection purposes, the macro passes one more tag than what is supported.
// If too many tags are given, a static assert in the log class will fail.
#define LOG_TAGS_EXPANDED(T0, T1, T2, T3, T4, T5, ...)  PREFIX_LOG_TAG(T0), PREFIX_LOG_TAG(T1), PREFIX_LOG_TAG(T2), \
                                                        PREFIX_LOG_TAG(T3), PREFIX_LOG_TAG(T4), PREFIX_LOG_TAG(T5)
// The EXPAND_VARARGS macro is required for MSVC, or it will resolve the LOG_TAGS_EXPANDED macro incorrectly.
#define EXPAND_VARARGS(x) x
#define LOG_TAGS(...) EXPAND_VARARGS(LOG_TAGS_EXPANDED(__VA_ARGS__, __NO_TAG, __NO_TAG, __NO_TAG, __NO_TAG, __NO_TAG, __NO_TAG))

// Log tags are used to classify log messages.
// Each log message can be assigned between 1 to LogTag::MaxTags number of tags.
// Specifying multiple tags for a log message means that only outputs configured
// for those exact tags, or a subset of the tags with a wildcard, will see the logging.
// Multiple tags should be comma separated, e.g. log_error(tag1, tag2)("msg").
class LogTag : public AllStatic {
 public:
  // The maximum number of tags that a single log message can have.
  // E.g. there might be hundreds of different tags available,
  // but a specific log message can only be tagged with up to MaxTags of those.
  static const size_t MaxTags = 5;

  enum type {
    __NO_TAG,
#define LOG_TAG(name) name,
    LOG_TAG_LIST
#undef LOG_TAG
    Count
  };

  static const char* name(LogTag::type tag) {
    return _name[tag];
  }

  static LogTag::type from_string(const char *str);

 private:
  static const char* _name[];
};

typedef LogTag::type LogTagType;

#endif // SHARE_VM_LOGGING_LOGTAG_HPP
