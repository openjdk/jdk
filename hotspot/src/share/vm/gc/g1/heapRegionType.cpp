/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "gc/g1/g1HeapRegionTraceType.hpp"
#include "gc/g1/heapRegionType.hpp"

bool HeapRegionType::is_valid(Tag tag) {
  switch (tag) {
    case FreeTag:
    case EdenTag:
    case SurvTag:
    case StartsHumongousTag:
    case ContinuesHumongousTag:
    case OldTag:
    case ArchiveTag:
      return true;
  }
  return false;
}

const char* HeapRegionType::get_str() const {
  hrt_assert_is_valid(_tag);
  switch (_tag) {
    case FreeTag:               return "FREE";
    case EdenTag:               return "EDEN";
    case SurvTag:               return "SURV";
    case StartsHumongousTag:    return "HUMS";
    case ContinuesHumongousTag: return "HUMC";
    case OldTag:                return "OLD";
    case ArchiveTag:            return "ARC";
  }
  ShouldNotReachHere();
  // keep some compilers happy
  return NULL;
}

const char* HeapRegionType::get_short_str() const {
  hrt_assert_is_valid(_tag);
  switch (_tag) {
    case FreeTag:               return "F";
    case EdenTag:               return "E";
    case SurvTag:               return "S";
    case StartsHumongousTag:    return "HS";
    case ContinuesHumongousTag: return "HC";
    case OldTag:                return "O";
    case ArchiveTag:            return "A";
  }
  ShouldNotReachHere();
  // keep some compilers happy
  return NULL;
}

G1HeapRegionTraceType::Type HeapRegionType::get_trace_type() {
  hrt_assert_is_valid(_tag);
  switch (_tag) {
    case FreeTag:               return G1HeapRegionTraceType::Free;
    case EdenTag:               return G1HeapRegionTraceType::Eden;
    case SurvTag:               return G1HeapRegionTraceType::Survivor;
    case StartsHumongousTag:    return G1HeapRegionTraceType::StartsHumongous;
    case ContinuesHumongousTag: return G1HeapRegionTraceType::ContinuesHumongous;
    case OldTag:                return G1HeapRegionTraceType::Old;
    case ArchiveTag:            return G1HeapRegionTraceType::Archive;
  }
  ShouldNotReachHere();
  // keep some compilers happy
  return G1HeapRegionTraceType::Free;
}
