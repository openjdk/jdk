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
  flags(PRECONDITION,         "comment about precondition") \
  flags(LOOP_ANALYZER,        "comment about loop analyzer") \
  flags(MEMORY_SLICES,        "comment about memory slices") \
  flags(BODY,                 "comment about body") \
  flags(DEPENDENCE_GRAPH,     "comment about dependence graph") \
  flags(TYPES,                "comment about vector element type") \
  flags(POINTER_ANALYSIS,     "comment about pointer analysis") \
  flags(SW_ADJACENT_MEMOPS,   "comment about superword find_adjacent_refs") \
  flags(SW_ALIGNMENT,         "comment about superword alignment") \
  flags(SW_REJECTIONS,        "comment about superword rejections") \
  flags(SW_PACKSET,           "comment about superword packset") \
  flags(SW_INFO,              "comment about superword all") \
  flags(SW_ALL,               "comment about superword all") \
  flags(ALL,                  "Trace everything")

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

class TraceAutovectorizationTagHelper {
  public:
  static const char* to_name(TraceAutovectorizationTag tat) {
    return tag_names[tat];
  }
  static const char* to_description(TraceAutovectorizationTag tat) {
    return tag_descriptions[tat];
  }
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

 public:
  TraceAutovectorizationTagValidator(ccstrlist option) :
    _tags(TRACEAUTOVECTORIZATION_TAGS_NUM, mtCompiler),
    _valid(true),
    _bad(nullptr)
  {
    for (TraceAutovectorizationTagNameIter iter(option); *iter != nullptr && _valid; ++iter) {

      TraceAutovectorizationTag tat = find_tag(*iter);
      if (TRACEAUTOVECTORIZATION_TAGS_NONE == tat) {
        const size_t len = MIN2<size_t>(strlen(*iter), 63) + 1;  // cap len to a value we know is enough for all phase descriptions
        _bad = NEW_C_HEAP_ARRAY(char, len, mtCompiler);
        // strncpy always writes len characters. If the source string is shorter, the function fills the remaining bytes with nulls.
        strncpy(_bad, *iter, len);
        _valid = false;
      } else if (TAG_ALL == tat) {
        _tags.set_range(0, TRACEAUTOVECTORIZATION_TAGS_NUM);
      } else if (TAG_SW_ALL == tat) {
        _tags.set_bit(TAG_SW_ADJACENT_MEMOPS);
        _tags.set_bit(TAG_SW_ALIGNMENT);
        _tags.set_bit(TAG_SW_REJECTIONS);
        _tags.set_bit(TAG_SW_PACKSET);
        _tags.set_bit(TAG_SW_INFO);
        _tags.set_bit(TAG_SW_ALL);
      } else if (TAG_SW_INFO == tat) {
        _tags.set_bit(TAG_SW_ADJACENT_MEMOPS);
        _tags.set_bit(TAG_SW_REJECTIONS);
        _tags.set_bit(TAG_SW_PACKSET);
        _tags.set_bit(TAG_SW_INFO);
      } else {
        assert(tat < TRACEAUTOVECTORIZATION_TAGS_NUM, "out of bounds");
        _tags.set_bit(tat);
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
};

#endif // SHARE_OPTO_TRACEAUTOVECTORIZATIONTAGS_HPP
