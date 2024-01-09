/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OPTO_TRACEAUTOVECTORIZATIONTAGS_HPP
#define SHARE_OPTO_TRACEAUTOVECTORIZATIONTAGS_HPP

#include "utilities/bitMap.inline.hpp"

#define COMPILER_TRACEAUTOVECTORIZATION_TAGS(flags) \
  flags(PRECONDITION,         "Trace VLoop::check_preconditions") \
  flags(LOOP_ANALYZER,        "Trace VLoopAnalyzer::analyze") \
  flags(MEMORY_SLICES,        "Trace VLoopMemorySlices::analyze") \
  flags(BODY,                 "Trace VLoopBody::construct") \
  flags(DEPENDENCE_GRAPH,     "Trace VLoopDependenceGraph::build") \
  flags(TYPES,                "Trace VLoopTypes::compute_vector_element_type") \
  flags(POINTER_ANALYSIS,     "Trace VPointer") \
  flags(SW_ADJACENT_MEMOPS,   "Trace SuperWord::find_adjacent_refs") \
  flags(SW_ALIGNMENT,         "Trace SuperWord alignment analysis") \
  flags(SW_REJECTIONS,        "Trace SuperWord rejections (non vectorizations)") \
  flags(SW_PACKSET,           "Trace SuperWord packset at different stages") \
  flags(SW_INFO,              "Trace SuperWord info") \
  flags(SW_ALL,               "Trace SuperWord all (verbose)") \
  flags(ALIGN_VECTOR,         "Trace AlignVector") \
  flags(ALL,                  "Trace everything (very verbose)")

#define table_entry(name, description) TAG_##name,
enum TraceAutovectorizationTag {
  COMPILER_TRACEAUTOVECTORIZATION_TAGS(table_entry)
  TRACEAUTOVECTORIZATION_TAGS_NUM,
  TRACEAUTOVECTORIZATION_TAGS_NONE
};
#undef table_entry

static const char* tag_descriptions[] = {
#define array_of_labels(name, description) description,
       COMPILER_TRACEAUTOVECTORIZATION_TAGS(array_of_labels)
#undef array_of_labels
};

static const char* tag_names[] = {
#define array_of_labels(name, description) #name,
       COMPILER_TRACEAUTOVECTORIZATION_TAGS(array_of_labels)
#undef array_of_labels
};

static TraceAutovectorizationTag find_tag(const char* str) {
  for (int i = 0; i < TRACEAUTOVECTORIZATION_TAGS_NUM; i++) {
    if (strcmp(tag_names[i], str) == 0) {
      return (TraceAutovectorizationTag)i;
    }
  }
  return TRACEAUTOVECTORIZATION_TAGS_NONE;
}

class TraceAutovectorizationTagNameIter {
 private:
  char* _token;
  char* _saved_ptr;
  char* _list;

 public:
  TraceAutovectorizationTagNameIter(ccstrlist option) {
    _list = (char*) canonicalize(option);
    _saved_ptr = _list;
    _token = strtok_r(_saved_ptr, ",", &_saved_ptr);
  }

  ~TraceAutovectorizationTagNameIter() {
    FREE_C_HEAP_ARRAY(char, _list);
  }

  const char* operator*() const { return _token; }

  TraceAutovectorizationTagNameIter& operator++() {
    _token = strtok_r(nullptr, ",", &_saved_ptr);
    return *this;
  }

  ccstrlist canonicalize(ccstrlist option_value) {
    char* canonicalized_list = NEW_C_HEAP_ARRAY(char, strlen(option_value) + 1, mtCompiler);
    int i = 0;
    char current;
    while ((current = option_value[i]) != '\0') {
      if (current == '\n' || current == ' ') {
        canonicalized_list[i] = ',';
      } else {
        canonicalized_list[i] = current;
      }
      i++;
    }
    canonicalized_list[i] = '\0';
    return canonicalized_list;
  }
};

class TraceAutovectorizationTagValidator {
 private:
  CHeapBitMap _tags;
  bool _valid;
  char* _bad;
  bool _is_print_usage;

 public:
  TraceAutovectorizationTagValidator(ccstrlist option, bool is_print_usage) :
    _tags(TRACEAUTOVECTORIZATION_TAGS_NUM, mtCompiler),
    _valid(true),
    _bad(nullptr),
    _is_print_usage(is_print_usage)
  {
    for (TraceAutovectorizationTagNameIter iter(option); *iter != nullptr && _valid; ++iter) {
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
      TraceAutovectorizationTag tat = find_tag(tag_name);
      if (TRACEAUTOVECTORIZATION_TAGS_NONE == tat) {
        // cap len to a value we know is enough for all tags
        const size_t len = MIN2<size_t>(strlen(*iter), 63) + 1;
        _bad = NEW_C_HEAP_ARRAY(char, len, mtCompiler);
        // strncpy always writes len characters. If the source string is
        // shorter, the function fills the remaining bytes with nulls.
        strncpy(_bad, *iter, len);
        _valid = false;
      } else if (TAG_ALL == tat) {
        _tags.set_range(0, TRACEAUTOVECTORIZATION_TAGS_NUM);
      } else if (TAG_SW_ALL == tat) {
        _tags.at_put(TAG_SW_ADJACENT_MEMOPS, set_bit);
        _tags.at_put(TAG_SW_ALIGNMENT, set_bit);
        _tags.at_put(TAG_SW_REJECTIONS, set_bit);
        _tags.at_put(TAG_SW_PACKSET, set_bit);
        _tags.at_put(TAG_SW_INFO, set_bit);
        _tags.at_put(TAG_SW_ALL, set_bit);
      } else if (TAG_SW_INFO == tat) {
        _tags.at_put(TAG_SW_ADJACENT_MEMOPS, set_bit);
        _tags.at_put(TAG_SW_REJECTIONS, set_bit);
        _tags.at_put(TAG_SW_PACKSET, set_bit);
        _tags.at_put(TAG_SW_INFO, set_bit);
      } else {
        assert(tat < TRACEAUTOVECTORIZATION_TAGS_NUM, "out of bounds");
        _tags.at_put(tat, set_bit);
      }
    }
  }

  ~TraceAutovectorizationTagValidator() {
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
    for (int i = 0; i < TRACEAUTOVECTORIZATION_TAGS_NUM; i++) {
      tty->print_cr("  %-22s %s", tag_names[i], tag_descriptions[i]);
    }
    tty->cr();
  }
};

#endif // SHARE_OPTO_TRACEAUTOVECTORIZATIONTAGS_HPP
