/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OPTO_TRACEAUTOVECTORIZATIONTAG_HPP
#define SHARE_OPTO_TRACEAUTOVECTORIZATIONTAG_HPP

#include "utilities/bitMap.inline.hpp"
#include "utilities/stringUtils.hpp"

#define COMPILER_TRACE_AUTO_VECTORIZATION_TAG(flags) \
  flags(POINTER_ANALYSIS,     "Trace VPointer") \
  flags(PRECONDITIONS,        "Trace VLoop::check_preconditions") \
  flags(LOOP_ANALYZER,        "Trace VLoopAnalyzer::setup_submodules") \
  flags(MEMORY_SLICES,        "Trace VLoopMemorySlices") \
  flags(BODY,                 "Trace VLoopBody") \
  flags(TYPES,                "Trace VLoopTypes") \
  flags(SW_ALIGNMENT,         "Trace SuperWord alignment analysis") \
  flags(SW_DEPENDENCE_GRAPH,  "Trace SuperWord::dependence_graph") \
  flags(SW_ADJACENT_MEMOPS,   "Trace SuperWord::find_adjacent_refs") \
  flags(SW_REJECTIONS,        "Trace SuperWord rejections (non vectorizations)") \
  flags(SW_PACKSET,           "Trace SuperWord packset at different stages") \
  flags(SW_INFO,              "Trace SuperWord info (equivalent to TraceSuperWord)") \
  flags(SW_VERBOSE,           "Trace SuperWord verbose (all SW tags enabled)") \
  flags(ALIGN_VECTOR,         "Trace AlignVector") \
  flags(ALL,                  "Trace everything (very verbose)")

#define table_entry(name, description) name,
enum TraceAutoVectorizationTag {
  COMPILER_TRACE_AUTO_VECTORIZATION_TAG(table_entry)
  TRACE_AUTO_VECTORIZATION_TAG_NUM,
  TRACE_AUTO_VECTORIZATION_TAG_NONE
};
#undef table_entry

static const char* tag_descriptions[] = {
#define array_of_labels(name, description) description,
       COMPILER_TRACE_AUTO_VECTORIZATION_TAG(array_of_labels)
#undef array_of_labels
};

static const char* tag_names[] = {
#define array_of_labels(name, description) #name,
       COMPILER_TRACE_AUTO_VECTORIZATION_TAG(array_of_labels)
#undef array_of_labels
};

static TraceAutoVectorizationTag find_tag(const char* str) {
  for (int i = 0; i < TRACE_AUTO_VECTORIZATION_TAG_NUM; i++) {
    if (strcmp(tag_names[i], str) == 0) {
      return (TraceAutoVectorizationTag)i;
    }
  }
  return TRACE_AUTO_VECTORIZATION_TAG_NONE;
}

class TraceAutoVectorizationTagValidator {
 private:
  CHeapBitMap _tags;
  bool _valid;
  char* _bad;
  bool _is_print_usage;

 public:
  TraceAutoVectorizationTagValidator(ccstrlist option, bool is_print_usage) :
    _tags(TRACE_AUTO_VECTORIZATION_TAG_NUM, mtCompiler),
    _valid(true),
    _bad(nullptr),
    _is_print_usage(is_print_usage)
  {
    for (StringUtils::CommaSeparatedStringIterator iter(option); *iter != nullptr && _valid; ++iter) {
      char const* tag_name = *iter;
      if (strcmp("help", tag_name) == 0) {
        if (_is_print_usage) {
          print_help();
        }
        continue;
      }
      bool set_bit = true;
      // Check for "TAG" or "-TAG"
      if (strncmp("-", tag_name, strlen("-")) == 0) {
        tag_name++;
        set_bit = false;
      }
      TraceAutoVectorizationTag tag = find_tag(tag_name);
      if (TRACE_AUTO_VECTORIZATION_TAG_NONE == tag) {
        // cap len to a value we know is enough for all tags
        const size_t len = MIN2<size_t>(strlen(*iter), 63) + 1;
        _bad = NEW_C_HEAP_ARRAY(char, len, mtCompiler);
        // strncpy always writes len characters. If the source string is
        // shorter, the function fills the remaining bytes with nulls.
        strncpy(_bad, *iter, len);
        _valid = false;
      } else if (ALL == tag) {
        _tags.set_range(0, TRACE_AUTO_VECTORIZATION_TAG_NUM);
      } else if (SW_VERBOSE == tag) {
        _tags.at_put(SW_ALIGNMENT, set_bit);
        _tags.at_put(SW_DEPENDENCE_GRAPH, set_bit);
        _tags.at_put(SW_ADJACENT_MEMOPS, set_bit);
        _tags.at_put(SW_REJECTIONS, set_bit);
        _tags.at_put(SW_PACKSET, set_bit);
        _tags.at_put(SW_INFO, set_bit);
        _tags.at_put(SW_VERBOSE, set_bit);
      } else if (SW_INFO == tag) {
        _tags.at_put(SW_DEPENDENCE_GRAPH, set_bit);
        _tags.at_put(SW_ADJACENT_MEMOPS, set_bit);
        _tags.at_put(SW_REJECTIONS, set_bit);
        _tags.at_put(SW_PACKSET, set_bit);
        _tags.at_put(SW_INFO, set_bit);
      } else {
        assert(tag < TRACE_AUTO_VECTORIZATION_TAG_NUM, "out of bounds");
        _tags.at_put(tag, set_bit);
      }
    }
  }

  ~TraceAutoVectorizationTagValidator() {
    if (_bad != nullptr) {
      FREE_C_HEAP_ARRAY(char, _bad);
    }
  }

  bool is_valid() const { return _valid; }
  const char* what() const { return _bad; }
  const CHeapBitMap& tags() const {
    assert(is_valid(), "only read tags when valid");
    return _tags;
  }

  static void print_help() {
    tty->cr();
    tty->print_cr("Usage for CompileCommand TraceAutoVectorization:");
    tty->print_cr("  -XX:CompileCommand=TraceAutoVectorization,<package.class::method>,<tags>");
    tty->print_cr("  %-22s %s", "tags", "descriptions");
    for (int i = 0; i < TRACE_AUTO_VECTORIZATION_TAG_NUM; i++) {
      tty->print_cr("  %-22s %s", tag_names[i], tag_descriptions[i]);
    }
    tty->cr();
  }
};

#endif // SHARE_OPTO_TRACEAUTOVECTORIZATIONTAG_HPP
