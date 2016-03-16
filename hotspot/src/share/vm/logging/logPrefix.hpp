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
#ifndef SHARE_VM_LOGGING_LOGPREFIX_HPP
#define SHARE_VM_LOGGING_LOGPREFIX_HPP

#include "gc/shared/gcId.hpp"
#include "logging/logTag.hpp"

// Prefixes prepend each log message for a specified tagset with a given prefix.
// These prefixes are written before the log message but after the log decorations.
//
// A prefix is defined as a function that takes a buffer (with some size) as argument.
// This function will be called for each log message, and should write the prefix
// to the given buffer. The function should return how many characters it wrote,
// which should never exceed the given size.
//
// List of prefixes for specific tags and/or tagsets.
// Syntax: LOG_PREFIX(<name of prefixer function>, LOG_TAGS(<chosen log tags>))
// Where the prefixer function matches the following signature: size_t (*)(char*, size_t)
#define LOG_PREFIX_LIST \
  LOG_PREFIX(GCId::print_prefix, LOG_TAGS(gc)) \
  LOG_PREFIX(GCId::print_prefix, LOG_TAGS(gc, age)) \
  LOG_PREFIX(GCId::print_prefix, LOG_TAGS(gc, alloc)) \
  LOG_PREFIX(GCId::print_prefix, LOG_TAGS(gc, barrier)) \
  LOG_PREFIX(GCId::print_prefix, LOG_TAGS(gc, classhisto)) \
  LOG_PREFIX(GCId::print_prefix, LOG_TAGS(gc, compaction)) \
  LOG_PREFIX(GCId::print_prefix, LOG_TAGS(gc, compaction, phases)) \
  LOG_PREFIX(GCId::print_prefix, LOG_TAGS(gc, cpu)) \
  LOG_PREFIX(GCId::print_prefix, LOG_TAGS(gc, ergo)) \
  LOG_PREFIX(GCId::print_prefix, LOG_TAGS(gc, ergo, cset)) \
  LOG_PREFIX(GCId::print_prefix, LOG_TAGS(gc, ergo, heap)) \
  LOG_PREFIX(GCId::print_prefix, LOG_TAGS(gc, ergo, ihop)) \
  LOG_PREFIX(GCId::print_prefix, LOG_TAGS(gc, heap)) \
  LOG_PREFIX(GCId::print_prefix, LOG_TAGS(gc, heap, region)) \
  LOG_PREFIX(GCId::print_prefix, LOG_TAGS(gc, freelist)) \
  LOG_PREFIX(GCId::print_prefix, LOG_TAGS(gc, ihop)) \
  LOG_PREFIX(GCId::print_prefix, LOG_TAGS(gc, liveness)) \
  LOG_PREFIX(GCId::print_prefix, LOG_TAGS(gc, marking)) \
  LOG_PREFIX(GCId::print_prefix, LOG_TAGS(gc, metaspace)) \
  LOG_PREFIX(GCId::print_prefix, LOG_TAGS(gc, phases)) \
  LOG_PREFIX(GCId::print_prefix, LOG_TAGS(gc, phases, start)) \
  LOG_PREFIX(GCId::print_prefix, LOG_TAGS(gc, phases, task)) \
  LOG_PREFIX(GCId::print_prefix, LOG_TAGS(gc, plab)) \
  LOG_PREFIX(GCId::print_prefix, LOG_TAGS(gc, region)) \
  LOG_PREFIX(GCId::print_prefix, LOG_TAGS(gc, remset)) \
  LOG_PREFIX(GCId::print_prefix, LOG_TAGS(gc, ref)) \
  LOG_PREFIX(GCId::print_prefix, LOG_TAGS(gc, ref, start)) \
  LOG_PREFIX(GCId::print_prefix, LOG_TAGS(gc, start)) \
  LOG_PREFIX(GCId::print_prefix, LOG_TAGS(gc, sweep)) \
  LOG_PREFIX(GCId::print_prefix, LOG_TAGS(gc, task)) \
  LOG_PREFIX(GCId::print_prefix, LOG_TAGS(gc, task, start)) \
  LOG_PREFIX(GCId::print_prefix, LOG_TAGS(gc, task, stats)) \
  LOG_PREFIX(GCId::print_prefix, LOG_TAGS(gc, task, time)) \
  LOG_PREFIX(GCId::print_prefix, LOG_TAGS(gc, tlab))


// The empty prefix, used when there's no prefix defined.
template <LogTagType T0, LogTagType T1, LogTagType T2, LogTagType T3, LogTagType T4, LogTagType GuardTag = LogTag::__NO_TAG>
struct LogPrefix : public AllStatic {
  STATIC_ASSERT(GuardTag == LogTag::__NO_TAG);
  static size_t prefix(char* buf, size_t len) {
    return 0;
  }
};

#define LOG_PREFIX(fn, ...) \
template <> struct LogPrefix<__VA_ARGS__> { \
  static size_t prefix(char* buf, size_t len) { \
    DEBUG_ONLY(buf[0] = '\0';) \
    size_t ret = fn(buf, len); \
    assert(ret == strlen(buf), "Length mismatch ret (" SIZE_FORMAT ") != buf length (" SIZE_FORMAT ")", ret, strlen(buf)); \
    return ret; \
  } \
};
LOG_PREFIX_LIST
#undef LOG_PREFIX

#endif // SHARE_VM_LOGGING_LOGPREFIX_HPP
