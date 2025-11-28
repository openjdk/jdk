/*
 * Copyright (c) 2014, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2023 SAP SE. All rights reserved.

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

#ifndef SHARE_NMT_NMTCOMMON_HPP
#define SHARE_NMT_NMTCOMMON_HPP

#include "memory/allStatic.hpp"
#include "nmt/memTag.hpp"
#include "utilities/align.hpp"
#include "utilities/globalDefinitions.hpp"

// Native memory tracking level
//
// The meaning of the different states:
//
// "unknown": pre-init phase (before parsing NMT arguments)
//
// "off":     after initialization - NMT confirmed off.
//             - nothing is tracked
//             - no malloc headers are used
//
// "summary": after initialization with NativeMemoryTracking=summary - NMT in summary mode
//             - category summaries per tag are tracked
//             - thread stacks are tracked
//             - malloc headers are used
//             - malloc call site table is allocated and used
//
// "detail":  after initialization with NativeMemoryTracking=detail - NMT in detail mode
//             - category summaries per tag are tracked
//             - malloc details per call site are tracked
//             - virtual memory mapping info is tracked
//             - thread stacks are tracked
//             - malloc headers are used
//             - malloc call site table is allocated and used
//


// Please keep relation of numerical values!
// unknown < off < summary < detail
//
enum NMT_TrackingLevel {
  NMT_unknown,
  NMT_off,
  NMT_summary,
  NMT_detail
};

// Number of stack frames to capture. This is a
// build time decision.
const int NMT_TrackingStackDepth = 4;

// A few common utilities for native memory tracking
class NMTUtil : AllStatic {
 public:
  // Check if index is a valid MemTag enum value (including mtNone)
  static inline bool tag_index_is_valid(int index) {
    return index >= 0 && index < mt_number_of_tags;
  }

  // Check if tag value is a valid MemTag enum value (including mtNone)
  static inline bool tag_is_valid(MemTag mem_tag) {
    const int index = static_cast<int>(mem_tag);
    return tag_index_is_valid(index);
  }

  // Map memory tag to index
  static inline int tag_to_index(MemTag mem_tag) {
    assert(tag_is_valid(mem_tag), "Invalid tag (%u)", (unsigned)mem_tag);
    return static_cast<int>(mem_tag);
  }

  // Map memory tag to human readable name
  static const char* tag_to_name(MemTag mem_tag) {
    return _strings[tag_to_index(mem_tag)].human_readable;
  }

  // Map memory tag to literalized enum name (e.g. "mtTest")
  static const char* tag_to_enum_name(MemTag mem_tag) {
    return _strings[tag_to_index(mem_tag)].enum_s;
  }

  // Map an index to memory tag
  static MemTag index_to_tag(int index) {
    assert(tag_index_is_valid(index), "Invalid tag index (%d)", index);
    return static_cast<MemTag>(index);
  }

  // Memory size scale
  static const char* scale_name(size_t scale);
  static size_t scale_from_name(const char* scale);

  // Translate memory size in specified scale
  static size_t amount_in_scale(size_t amount, size_t scale) {
    return (amount + scale / 2) / scale;
  }

  // Parses the tracking level from a string. Returns NMT_unknown if
  // string is not a valid level.
  static NMT_TrackingLevel parse_tracking_level(const char* s);

  // Given a string, return associated mem_tag. mtNone if name is invalid.
  // String can be either the human readable name or the
  // stringified enum (with or without leading "mt". In all cases, case is ignored.
  static MemTag string_to_mem_tag(const char* name);

  // Returns textual representation of a tracking level.
  static const char* tracking_level_to_string(NMT_TrackingLevel level);

 private:
  struct S {
    const char* enum_s; // e.g. "mtNMT"
    const char* human_readable; // e.g. "Native Memory Tracking"
  };
  static S _strings[mt_number_of_tags];
};


#endif // SHARE_NMT_NMTCOMMON_HPP
