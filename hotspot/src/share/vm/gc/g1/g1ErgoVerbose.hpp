/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_G1_G1ERGOVERBOSE_HPP
#define SHARE_VM_GC_G1_G1ERGOVERBOSE_HPP

#include "memory/allocation.hpp"
#include "utilities/debug.hpp"

// The log of G1's heuristic decisions comprises of a series of
// records which have a similar format in order to maintain
// consistency across records and ultimately easier parsing of the
// output, if we ever choose to do that. Each record consists of:
// * A time stamp to be able to easily correlate each record with
// other events.
// * A unique string to allow us to easily identify such records.
// * The name of the heuristic the record corresponds to.
// * An action string which describes the action that G1 did or is
// about to do.
// * An optional reason string which describes the reason for the
// action.
// * An optional number of name/value pairs which contributed to the
// decision to take the action described in the record.
//
// Each record is associated with a "tag" which is the combination of
// the heuristic the record corresponds to, as well as the min level
// of verboseness at which the record should be printed. The tag is
// checked against the current settings to determine whether the record
// should be printed or not.

// The available verboseness levels.
typedef enum {
  // Determine which part of the tag is occupied by the level.
  ErgoLevelShift = 8,
  ErgoLevelMask = ~((1 << ErgoLevelShift) - 1),

  // ErgoLow is 0 so that we don't have to explicitly or a heuristic
  // id with ErgoLow to keep its use simpler.
  ErgoLow = 0,
  ErgoHigh = 1 << ErgoLevelShift
} ErgoLevel;

// The available heuristics.
typedef enum {
  // Determines which part of the tag is occupied by the heuristic id.
  ErgoHeuristicMask = ~ErgoLevelMask,

  ErgoHeapSizing = 0,
  ErgoCSetConstruction,
  ErgoConcCycles,
  ErgoMixedGCs,
  ErgoTiming,

  ErgoHeuristicNum
} ErgoHeuristic;

class G1ErgoVerbose : AllStatic {
private:
  // Determines the minimum verboseness level at which records will be
  // printed.
  static ErgoLevel _level;
  // Determines which heuristics are currently enabled.
  static bool _enabled[ErgoHeuristicNum];

  static ErgoLevel extract_level(int tag) {
    return (ErgoLevel) (tag & ErgoLevelMask);
  }

  static ErgoHeuristic extract_heuristic(int tag) {
    return (ErgoHeuristic) (tag & ErgoHeuristicMask);
  }

public:
  // Needs to be explicitly called at GC initialization.
  static void initialize();

  static void set_level(ErgoLevel level);
  static void set_enabled(ErgoHeuristic h, bool enabled);
  // It is applied to all heuristics.
  static void set_enabled(bool enabled);

  static bool enabled(int tag) {
    ErgoLevel level = extract_level(tag);
    ErgoHeuristic n = extract_heuristic(tag);
    return level <= _level && _enabled[n];
  }

  // Extract the heuristic id from the tag and return a string with
  // its name.
  static const char* to_string(int tag);
};

// The macros below generate the format string for values of different
// types and/or metrics.

// The reason for the action is optional and is handled specially: the
// reason string is concatenated here so it's not necessary to pass it
// as a parameter.
#define ergo_format_reason(_reason_) ", reason: " _reason_

// Single parameter format strings
#define ergo_format_str(_name_)      ", " _name_ ": %s"
#define ergo_format_region(_name_)   ", " _name_ ": %u regions"
#define ergo_format_byte(_name_)     ", " _name_ ": " SIZE_FORMAT " bytes"
#define ergo_format_double(_name_)   ", " _name_ ": %1.2f"
#define ergo_format_perc(_name_)     ", " _name_ ": %1.2f %%"
#define ergo_format_ms(_name_)       ", " _name_ ": %1.2f ms"
#define ergo_format_size(_name_)     ", " _name_ ": " SIZE_FORMAT

// Double parameter format strings
#define ergo_format_byte_perc(_name_)                                   \
                             ", " _name_ ": " SIZE_FORMAT " bytes (%1.2f %%)"

// Generates the format string
#define ergo_format(_extra_format_)                           \
  " %1.3f: [G1Ergonomics (%s) %s" _extra_format_ "]"

// Conditionally, prints an ergonomic decision record. _extra_format_
// is the format string for the optional items we'd like to print
// (i.e., the decision's reason and any associated values). This
// string should be built up using the ergo_*_format macros (see
// above) to ensure consistency.
//
// Since we cannot rely on the compiler supporting variable argument
// macros, this macro accepts a fixed number of arguments and passes
// them to the print method. For convenience, we have wrapper macros
// below which take a specific number of arguments and set the rest to
// a default value.
#define ergo_verbose_common(_tag_, _action_, _extra_format_,                \
                            _arg0_, _arg1_, _arg2_, _arg3_, _arg4_, _arg5_) \
  do {                                                                      \
    if (G1ErgoVerbose::enabled((_tag_))) {                                  \
      gclog_or_tty->print_cr(ergo_format(_extra_format_),                   \
                             os::elapsedTime(),                             \
                             G1ErgoVerbose::to_string((_tag_)),             \
                             (_action_),                                    \
                             (_arg0_), (_arg1_), (_arg2_),                  \
                             (_arg3_), (_arg4_), (_arg5_));                 \
    }                                                                       \
  } while (0)


#define ergo_verbose6(_tag_, _action_, _extra_format_,                  \
                      _arg0_, _arg1_, _arg2_, _arg3_, _arg4_, _arg5_)   \
  ergo_verbose_common(_tag_, _action_, _extra_format_,                  \
                      _arg0_, _arg1_, _arg2_, _arg3_, _arg4_, _arg5_)

#define ergo_verbose5(_tag_, _action_, _extra_format_,                  \
                      _arg0_, _arg1_, _arg2_, _arg3_, _arg4_)           \
  ergo_verbose6(_tag_, _action_, _extra_format_ "%s",                   \
                _arg0_, _arg1_, _arg2_, _arg3_, _arg4_, "")

#define ergo_verbose4(_tag_, _action_, _extra_format_,                  \
                      _arg0_, _arg1_, _arg2_, _arg3_)                   \
  ergo_verbose5(_tag_, _action_, _extra_format_ "%s",                   \
                _arg0_, _arg1_, _arg2_, _arg3_, "")

#define ergo_verbose3(_tag_, _action_, _extra_format_,                  \
                      _arg0_, _arg1_, _arg2_)                           \
  ergo_verbose4(_tag_, _action_, _extra_format_ "%s",                   \
                _arg0_, _arg1_, _arg2_, "")

#define ergo_verbose2(_tag_, _action_, _extra_format_,                  \
                      _arg0_, _arg1_)                                   \
  ergo_verbose3(_tag_, _action_, _extra_format_ "%s",                   \
                _arg0_, _arg1_, "")

#define ergo_verbose1(_tag_, _action_, _extra_format_,                  \
                      _arg0_)                                           \
  ergo_verbose2(_tag_, _action_, _extra_format_ "%s",                   \
                _arg0_, "")


#define ergo_verbose0(_tag_, _action_, _extra_format_)                  \
  ergo_verbose1(_tag_, _action_, _extra_format_ "%s",                   \
                "")

#define ergo_verbose(_tag_, _action_)                                   \
  ergo_verbose0(_tag_, _action_, "")


#endif // SHARE_VM_GC_G1_G1ERGOVERBOSE_HPP
