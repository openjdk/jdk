/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "phasetype.hpp"

const char* const CompilerPhaseTypeHelper::_phase_descriptions[] = {
#define array_of_labels(name, description) description,
       COMPILER_PHASES(array_of_labels)
#undef array_of_labels
};

const char* const CompilerPhaseTypeHelper::_phase_names[] = {
#define array_of_labels(name, description) #name,
       COMPILER_PHASES(array_of_labels)
#undef array_of_labels
};

CompilerPhaseType CompilerPhaseTypeHelper::find_phase(const char* str) {
  for (int i = 0; i < PHASE_NUM_TYPES; i++) {
    if (strcmp(CompilerPhaseTypeHelper::_phase_names[i], str) == 0) {
      return (CompilerPhaseType)i;
    }
  }
  return PHASE_NONE;
}
