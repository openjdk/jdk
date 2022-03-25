/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
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

#define COMPILER_PHASES(flags) \
  flags(BEFORE_STRINGOPTS,            "Before StringOpts") \
  flags(AFTER_STRINGOPTS,             "After StringOpts") \
  flags(BEFORE_REMOVEUSELESS,         "Before RemoveUseless") \
  flags(AFTER_PARSING,                "After Parsing") \
  flags(ITER_GVN1,                    "Iter GVN 1") \
  flags(EXPAND_VUNBOX,                "Expand VectorUnbox") \
  flags(SCALARIZE_VBOX,               "Scalarize VectorBox") \
  flags(INLINE_VECTOR_REBOX,          "Inline Vector Rebox Calls") \
  flags(EXPAND_VBOX,                  "Expand VectorBox") \
  flags(ELIMINATE_VBOX_ALLOC,         "Eliminate VectorBoxAllocate") \
  flags(PHASEIDEAL_BEFORE_EA,         "PhaseIdealLoop before EA") \
  flags(ITER_GVN_AFTER_VECTOR,        "Iter GVN after vector box elimination") \
  flags(ITER_GVN_BEFORE_EA,           "Iter GVN before EA") \
  flags(ITER_GVN_AFTER_EA,            "Iter GVN after EA") \
  flags(ITER_GVN_AFTER_ELIMINATION,   "Iter GVN after eliminating allocations and locks") \
  flags(PHASEIDEALLOOP1,              "PhaseIdealLoop 1") \
  flags(PHASEIDEALLOOP2,              "PhaseIdealLoop 2") \
  flags(PHASEIDEALLOOP3,              "PhaseIdealLoop 3") \
  flags(CCP1,                         "PhaseCCP 1") \
  flags(ITER_GVN2,                    "Iter GVN 2") \
  flags(PHASEIDEALLOOP_ITERATIONS,    "PhaseIdealLoop iterations") \
  flags(OPTIMIZE_FINISHED,            "Optimize finished") \
  flags(GLOBAL_CODE_MOTION,           "Global code motion") \
  flags(FINAL_CODE,                   "Final Code") \
  flags(AFTER_EA,                     "After Escape Analysis") \
  flags(BEFORE_CLOOPS,                "Before CountedLoop") \
  flags(AFTER_CLOOPS,                 "After CountedLoop") \
  flags(BEFORE_BEAUTIFY_LOOPS,        "Before beautify loops") \
  flags(AFTER_BEAUTIFY_LOOPS,         "After beautify loops") \
  flags(BEFORE_MATCHING,              "Before matching") \
  flags(MATCHING,                     "After matching") \
  flags(MACHANALYSIS,                 "After mach analysis") \
  flags(INCREMENTAL_INLINE,           "Incremental Inline") \
  flags(INCREMENTAL_INLINE_STEP,      "Incremental Inline Step") \
  flags(INCREMENTAL_INLINE_CLEANUP,   "Incremental Inline Cleanup") \
  flags(INCREMENTAL_BOXING_INLINE,    "Incremental Boxing Inline") \
  flags(CALL_CATCH_CLEANUP,           "Call catch cleanup") \
  flags(MACRO_EXPANSION,              "Macro expand") \
  flags(BARRIER_EXPANSION,            "Barrier expand") \
  flags(END,                          "End") \
  flags(FAILURE,                      "Failure") \
  flags(DEBUG,                        "Debug")

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
  static int to_bitmask(CompilerPhaseType cpt) {
    return (1 << cpt);
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
    _token = strtok_r(NULL, ",", &_saved_ptr);
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
  bool _valid;
  char* _bad;

 public:
  PhaseNameValidator(ccstrlist option, uint64_t& mask) : _valid(true), _bad(nullptr) {
    for (PhaseNameIter iter(option); *iter != NULL && _valid; ++iter) {

      CompilerPhaseType cpt = find_phase(*iter);
      if (PHASE_NONE == cpt) {
        const size_t len = MIN2<size_t>(strlen(*iter), 63) + 1;  // cap len to a value we know is enough for all phase descriptions
        _bad = NEW_C_HEAP_ARRAY(char, len, mtCompiler);
        // strncpy always writes len characters. If the source string is shorter, the function fills the remaining bytes with NULLs.
        strncpy(_bad, *iter, len);
        _valid = false;
      } else {
        assert(cpt < 64, "out of bounds");
        mask |= CompilerPhaseTypeHelper::to_bitmask(cpt);
      }
    }
  }

  ~PhaseNameValidator() {
    if (_bad != NULL) {
      FREE_C_HEAP_ARRAY(char, _bad);
    }
  }

  bool is_valid() const {
    return _valid;
  }

  const char* what() const {
    return _bad;
  }
};

#endif // SHARE_OPTO_PHASETYPE_HPP
