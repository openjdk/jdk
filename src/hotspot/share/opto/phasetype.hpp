/*
 * Copyright (c) 2012, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OPTO_PHASETYPE_HPP
#define SHARE_OPTO_PHASETYPE_HPP

#include "utilities/bitMap.inline.hpp"

#define COMPILER_PHASES(flags) \
  flags(BEFORE_STRINGOPTS,              "Before StringOpts") \
  flags(AFTER_STRINGOPTS,               "After StringOpts") \
  flags(BEFORE_REMOVEUSELESS,           "Before RemoveUseless") \
  flags(AFTER_PARSING,                  "After Parsing") \
  flags(BEFORE_ITER_GVN,                "Before Iter GVN") \
  flags(ITER_GVN1,                      "Iter GVN 1") \
  flags(AFTER_ITER_GVN_STEP,            "After Iter GVN Step") \
  flags(AFTER_ITER_GVN,                 "After Iter GVN") \
  flags(INCREMENTAL_INLINE_STEP,        "Incremental Inline Step") \
  flags(INCREMENTAL_INLINE_CLEANUP,     "Incremental Inline Cleanup") \
  flags(INCREMENTAL_INLINE,             "Incremental Inline") \
  flags(INCREMENTAL_BOXING_INLINE,      "Incremental Boxing Inline") \
  flags(EXPAND_VUNBOX,                  "Expand VectorUnbox") \
  flags(SCALARIZE_VBOX,                 "Scalarize VectorBox") \
  flags(INLINE_VECTOR_REBOX,            "Inline Vector Rebox Calls") \
  flags(EXPAND_VBOX,                    "Expand VectorBox") \
  flags(ELIMINATE_VBOX_ALLOC,           "Eliminate VectorBoxAllocate") \
  flags(ITER_GVN_BEFORE_EA,             "Iter GVN before EA") \
  flags(ITER_GVN_AFTER_VECTOR,          "Iter GVN after vector box elimination") \
  flags(BEFORE_BEAUTIFY_LOOPS,          "Before beautify loops") \
  flags(AFTER_BEAUTIFY_LOOPS,           "After beautify loops") \
  flags(BEFORE_LOOP_UNROLLING,          "Before Loop Unrolling") \
  flags(AFTER_LOOP_UNROLLING,           "After Loop Unrolling") \
  flags(BEFORE_SPLIT_IF,                "Before Split-If") \
  flags(AFTER_SPLIT_IF,                 "After Split-If") \
  flags(BEFORE_LOOP_PREDICATION_IC,     "Before Loop Predication IC") \
  flags(AFTER_LOOP_PREDICATION_IC,      "After Loop Predication IC") \
  flags(BEFORE_LOOP_PREDICATION_RC,     "Before Loop Predication RC") \
  flags(AFTER_LOOP_PREDICATION_RC,      "After Loop Predication RC") \
  flags(BEFORE_PARTIAL_PEELING,         "Before Partial Peeling") \
  flags(AFTER_PARTIAL_PEELING,          "After Partial Peeling") \
  flags(BEFORE_LOOP_PEELING,            "Before Loop Peeling") \
  flags(AFTER_LOOP_PEELING,             "After Loop Peeling") \
  flags(BEFORE_LOOP_UNSWITCHING,        "Before Loop Unswitching") \
  flags(AFTER_LOOP_UNSWITCHING,         "After Loop Unswitching") \
  flags(BEFORE_RANGE_CHECK_ELIMINATION, "Before Range Check Elimination") \
  flags(AFTER_RANGE_CHECK_ELIMINATION,  "After Range Check Elimination") \
  flags(BEFORE_PRE_MAIN_POST,           "Before Pre/Main/Post Loops") \
  flags(AFTER_PRE_MAIN_POST,            "After Pre/Main/Post Loops") \
  flags(SUPERWORD1_BEFORE_SCHEDULE,     "Superword 1, Before Schedule") \
  flags(SUPERWORD2_BEFORE_OUTPUT,       "Superword 2, Before Output") \
  flags(SUPERWORD3_AFTER_OUTPUT,        "Superword 3, After Output") \
  flags(BEFORE_CLOOPS,                  "Before CountedLoop") \
  flags(AFTER_CLOOPS,                   "After CountedLoop") \
  flags(PHASEIDEAL_BEFORE_EA,           "PhaseIdealLoop before EA") \
  flags(AFTER_EA,                       "After Escape Analysis") \
  flags(ITER_GVN_AFTER_EA,              "Iter GVN after EA") \
  flags(ITER_GVN_AFTER_ELIMINATION,     "Iter GVN after eliminating allocations and locks") \
  flags(PHASEIDEALLOOP1,                "PhaseIdealLoop 1") \
  flags(PHASEIDEALLOOP2,                "PhaseIdealLoop 2") \
  flags(PHASEIDEALLOOP3,                "PhaseIdealLoop 3") \
  flags(BEFORE_CCP1,                    "Before PhaseCCP 1") \
  flags(CCP1,                           "PhaseCCP 1") \
  flags(ITER_GVN2,                      "Iter GVN 2") \
  flags(PHASEIDEALLOOP_ITERATIONS,      "PhaseIdealLoop iterations") \
  flags(MACRO_EXPANSION,                "Macro expand") \
  flags(BARRIER_EXPANSION,              "Barrier expand") \
  flags(OPTIMIZE_FINISHED,              "Optimize finished") \
  flags(BEFORE_MATCHING,                "Before matching") \
  flags(MATCHING,                       "After matching") \
  flags(GLOBAL_CODE_MOTION,             "Global code motion") \
  flags(REGISTER_ALLOCATION,            "Register Allocation") \
  flags(BLOCK_ORDERING,                 "Block Ordering") \
  flags(PEEPHOLE,                       "Peephole") \
  flags(POSTALLOC_EXPAND,               "Post-Allocation Expand") \
  flags(MACH_ANALYSIS,                  "After mach analysis") \
  flags(FINAL_CODE,                     "Final Code") \
  flags(END,                            "End") \
  flags(FAILURE,                        "Failure") \
  flags(ALL,                            "All") \
  flags(DEBUG,                          "Debug")

#define table_entry(name, description) PHASE_##name,
enum CompilerPhaseType {
  COMPILER_PHASES(table_entry)
  PHASE_NUM_TYPES,
  PHASE_NONE
};
#undef table_entry

static const char* phase_descriptions[] = {
#define array_of_labels(name, description) description,
       COMPILER_PHASES(array_of_labels)
#undef array_of_labels
};

static const char* phase_names[] = {
#define array_of_labels(name, description) #name,
       COMPILER_PHASES(array_of_labels)
#undef array_of_labels
};

class CompilerPhaseTypeHelper {
  public:
  static const char* to_name(CompilerPhaseType cpt) {
    return phase_names[cpt];
  }
  static const char* to_description(CompilerPhaseType cpt) {
    return phase_descriptions[cpt];
  }
};

static CompilerPhaseType find_phase(const char* str) {
  for (int i = 0; i < PHASE_NUM_TYPES; i++) {
    if (strcmp(phase_names[i], str) == 0) {
      return (CompilerPhaseType)i;
    }
  }
  return PHASE_NONE;
}

class PhaseNameIter {
 private:
  char* _token;
  char* _saved_ptr;
  char* _list;

 public:
  PhaseNameIter(ccstrlist option) {
    _list = (char*) canonicalize(option);
    _saved_ptr = _list;
    _token = strtok_r(_saved_ptr, ",", &_saved_ptr);
  }

  ~PhaseNameIter() {
    FREE_C_HEAP_ARRAY(char, _list);
  }

  const char* operator*() const { return _token; }

  PhaseNameIter& operator++() {
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

class PhaseNameValidator {
 private:
  CHeapBitMap _phase_name_set;
  bool _valid;
  char* _bad;

 public:
  PhaseNameValidator(ccstrlist option) :
    _phase_name_set(PHASE_NUM_TYPES, mtCompiler),
    _valid(true),
    _bad(nullptr)
  {
    for (PhaseNameIter iter(option); *iter != nullptr && _valid; ++iter) {

      CompilerPhaseType cpt = find_phase(*iter);
      if (PHASE_NONE == cpt) {
        const size_t len = MIN2<size_t>(strlen(*iter), 63) + 1;  // cap len to a value we know is enough for all phase descriptions
        _bad = NEW_C_HEAP_ARRAY(char, len, mtCompiler);
        // strncpy always writes len characters. If the source string is shorter, the function fills the remaining bytes with nulls.
        strncpy(_bad, *iter, len);
        _valid = false;
      } else if (PHASE_ALL == cpt) {
        _phase_name_set.set_range(0, PHASE_NUM_TYPES);
      } else {
        assert(cpt < PHASE_NUM_TYPES, "out of bounds");
        _phase_name_set.set_bit(cpt);
      }
    }
  }

  ~PhaseNameValidator() {
    if (_bad != nullptr) {
      FREE_C_HEAP_ARRAY(char, _bad);
    }
  }

  const BitMap& phase_name_set() const {
    assert(is_valid(), "Use of invalid phase name set");
    return _phase_name_set;
  }

  bool is_valid() const {
    return _valid;
  }

  const char* what() const {
    return _bad;
  }
};

#endif // SHARE_OPTO_PHASETYPE_HPP
