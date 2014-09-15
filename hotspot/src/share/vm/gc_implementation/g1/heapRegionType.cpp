/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
#include "gc_implementation/g1/heapRegionType.hpp"

bool HeapRegionType::is_valid(Tag tag) {
  switch (tag) {
    case FreeTag:
    case EdenTag:
    case SurvTag:
    case HumStartsTag:
    case HumContTag:
    case OldTag:
      return true;
  }
  return false;
}

const char* HeapRegionType::get_str() const {
  hrt_assert_is_valid(_tag);
  switch (_tag) {
    case FreeTag:      return "FREE";
    case EdenTag:      return "EDEN";
    case SurvTag:      return "SURV";
    case HumStartsTag: return "HUMS";
    case HumContTag:   return "HUMC";
    case OldTag:       return "OLD";
  }
  ShouldNotReachHere();
  // keep some compilers happy
  return NULL;
}

const char* HeapRegionType::get_short_str() const {
  hrt_assert_is_valid(_tag);
  switch (_tag) {
    case FreeTag:      return "F";
    case EdenTag:      return "E";
    case SurvTag:      return "S";
    case HumStartsTag: return "HS";
    case HumContTag:   return "HC";
    case OldTag:       return "O";
  }
  ShouldNotReachHere();
  // keep some compilers happy
  return NULL;
}
